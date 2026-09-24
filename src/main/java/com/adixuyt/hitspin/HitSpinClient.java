package com.adixuyt.hitspin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HitSpinClient implements ClientModInitializer {
    public static final String MOD_ID = "hitspin";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Random RANDOM = new Random();
    public static final Config CONFIG = new Config();
    public static KeyBinding OPEN_GUI;
    private static Path configPath;

    private static boolean spinning;
    private static long animationStart;
    private static int phase; // 0 = outbound, 1 = return
    private static float startYaw, startPitch, targetYaw, targetPitch;
    private static float origYaw, origPitch; // pozycja kamery sprzed obrotu
    private static Rotation currentRotation;

    public enum Rotation {
        FULL_360(360, 0, "360°"), LEFT(-90, 0, "90° lewo"), RIGHT(90, 0, "90° prawo"),
        UP(0, -90, "90° góra"), DOWN(0, 90, "90° dół"),
        UP_LEFT(-90, -90, "45° góra-lewo"), UP_RIGHT(90, -90, "45° góra-prawo"),
        DOWN_LEFT(-90, 90, "45° dół-lewo"), DOWN_RIGHT(90, 90, "45° dół-prawo");
        public final float yaw, pitch; public final String label;
        Rotation(float yaw, float pitch, String label) { this.yaw = yaw; this.pitch = pitch; this.label = label; }
    }

    @Override
    public void onInitializeClient() {
        configPath = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/hitspin.json");
        loadConfig();
        OPEN_GUI = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.hitspin.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.category.hitspin.controls"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_GUI.wasPressed()) {
                if (client.player != null) client.setScreen(new HitSpinScreen(null));
            }
        });

        WorldRenderEvents.END.register(context -> updateAnimation());
    }

    public static void triggerRotation() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!CONFIG.enabled || client.player == null || spinning) return;
        List<Rotation> choices = new ArrayList<>();
        for (int i = 0; i < CONFIG.selected.length; i++) if (CONFIG.selected[i]) choices.add(Rotation.values()[i]);
        if (choices.isEmpty()) return;
        currentRotation = CONFIG.randomMode ? choices.get(RANDOM.nextInt(choices.size())) : choices.get(CONFIG.sequenceIndex++ % choices.size());
        startYaw = client.player.getYaw();
        startPitch = client.player.getPitch();
        origYaw = startYaw;
        origPitch = startPitch;
        targetYaw = startYaw + currentRotation.yaw;
        targetPitch = clampPitch(startPitch + currentRotation.pitch);
        animationStart = System.nanoTime();
        phase = 0;
        spinning = true;
    }

    private static float clampPitch(float value) { return Math.max(-90f, Math.min(90f, value)); }

    private static void updateAnimation() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!spinning || client.player == null || client.world == null) return;
        double seconds = Math.max(0.01, Math.min(4.0, CONFIG.durationSeconds));
        double progress = (System.nanoTime() - animationStart) / 1_000_000_000.0 / seconds;
        progress = Math.min(1.0, Math.max(0.0, progress));
        float eased = (float)(progress < 0.5 ? 4 * progress * progress * progress : 1 - Math.pow(-2 * progress + 2, 3) / 2);

        float yaw = lerpAngle(startYaw, targetYaw, eased);
        float pitch = startPitch + (targetPitch - startPitch) * eased;
        client.player.setAngles(yaw, pitch);
        client.player.setHeadYaw(yaw);

        if (progress >= 1.0) {
            if (currentRotation != Rotation.FULL_360 && phase == 0) {
                phase = 1;
                animationStart = System.nanoTime();
                float oldStartYaw = startYaw, oldStartPitch = startPitch;
                startYaw = targetYaw; startPitch = targetPitch;
                targetYaw = oldStartYaw; targetPitch = oldStartPitch;
            } else {
                client.player.setAngles(origYaw, origPitch);
                client.player.setHeadYaw(origYaw);
                spinning = false;
            }
        }
    }

    private static float lerpAngle(float a, float b, float t) { return a + (b - a) * t; }

    public static boolean isSpinning() { return spinning; }

    public static void saveConfig() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("enabled", CONFIG.enabled);
            o.addProperty("duration", CONFIG.durationSeconds);
            o.addProperty("randomMode", CONFIG.randomMode);
            o.addProperty("theme", CONFIG.theme);
            for (int i = 0; i < CONFIG.selected.length; i++) o.addProperty("rotation_" + i, CONFIG.selected[i]);
            Files.writeString(configPath, GSON.toJson(o));
        } catch (IOException ignored) {}
    }

    private static void loadConfig() {
        try {
            if (!Files.exists(configPath)) return;
            JsonObject o = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            CONFIG.enabled = o.has("enabled") && o.get("enabled").getAsBoolean();
            if (o.has("duration")) CONFIG.durationSeconds = Math.max(.01, Math.min(4, o.get("duration").getAsDouble()));
            if (o.has("randomMode")) CONFIG.randomMode = o.get("randomMode").getAsBoolean();
            if (o.has("theme")) CONFIG.theme = Math.max(0, Math.min(5, o.get("theme").getAsInt()));
            for (int i = 0; i < CONFIG.selected.length; i++) if (o.has("rotation_" + i)) CONFIG.selected[i] = o.get("rotation_" + i).getAsBoolean();
        } catch (Exception ignored) {}
    }

    public static class Config {
        public boolean enabled = false;
        public double durationSeconds = 1.00;
        public boolean randomMode = true;
        public int theme = 0;
        public int sequenceIndex = 0;
        public boolean[] selected = {true, true, true, false, false, false, false, false, false};
    }
}
