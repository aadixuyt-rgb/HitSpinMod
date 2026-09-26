package com.adixuyt.hitspin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
    private static int currentRotationIndex;

    // Kolejność zgodna z indeksami w Config.selected / Config.degrees / GUI.
    public enum Rotation {
        FULL_360("360°"), LEFT("Lewo"), RIGHT("Prawo"), UP("Góra"), DOWN("Dół"),
        UP_LEFT("Ukos góra-lewo"), UP_RIGHT("Ukos góra-prawo"),
        DOWN_LEFT("Ukos dół-lewo"), DOWN_RIGHT("Ukos dół-prawo");
        public final String label;
        Rotation(String label) { this.label = label; }
    }

    /** Maksymalny kąt suwaka dla danego rodzaju obrotu (360 dla pełnego obrotu, 90 dla reszty). */
    public static int maxDegrees(int index) { return index == 0 ? 360 : 90; }

    /** Wylicza deltę yaw/pitch dla danego rodzaju obrotu na podstawie ustawionych stopni. */
    private static float[] deltaFor(int index, int deg) {
        return switch (index) {
            case 0 -> new float[]{deg, 0};          // 360 - pełny obrót
            case 1 -> new float[]{-deg, 0};         // lewo
            case 2 -> new float[]{deg, 0};          // prawo
            case 3 -> new float[]{0, -deg};         // góra
            case 4 -> new float[]{0, deg};          // dół
            case 5 -> new float[]{-deg, -deg};      // ukos góra-lewo
            case 6 -> new float[]{deg, -deg};       // ukos góra-prawo
            case 7 -> new float[]{-deg, deg};       // ukos dół-lewo
            case 8 -> new float[]{deg, deg};        // ukos dół-prawo
            default -> new float[]{0, 0};
        };
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
        List<Integer> choices = new ArrayList<>();
        for (int i = 0; i < CONFIG.selected.length; i++) if (CONFIG.selected[i]) choices.add(i);
        if (choices.isEmpty()) return;
        currentRotationIndex = CONFIG.randomMode ? choices.get(RANDOM.nextInt(choices.size())) : choices.get(CONFIG.sequenceIndex++ % choices.size());
        float[] d = deltaFor(currentRotationIndex, CONFIG.degrees[currentRotationIndex]);
        startYaw = client.player.getYaw();
        startPitch = client.player.getPitch();
        origYaw = startYaw;
        origPitch = startPitch;
        targetYaw = startYaw + d[0];
        targetPitch = clampPitch(startPitch + d[1]);
        animationStart = System.nanoTime();
        phase = 0;
        spinning = true;
    }

    private static float clampPitch(float value) { return Math.max(-90f, Math.min(90f, value)); }

    private static void updateAnimation() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!spinning || client.player == null || client.world == null) return;
        double seconds = Math.max(0.01, Math.min(2.0, CONFIG.durationSeconds));
        double progress = (System.nanoTime() - animationStart) / 1_000_000_000.0 / seconds;
        progress = Math.min(1.0, Math.max(0.0, progress));
        float eased = (float) (progress < 0.5 ? 4 * progress * progress * progress : 1 - Math.pow(-2 * progress + 2, 3) / 2);

        float yaw = lerpAngle(startYaw, targetYaw, eased);
        float pitch = startPitch + (targetPitch - startPitch) * eased;
        client.player.setAngles(yaw, pitch);
        client.player.setHeadYaw(yaw);

        if (progress >= 1.0) {
            if (currentRotationIndex != 0 && phase == 0) { // 0 = FULL_360, nie wraca
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
            o.addProperty("hotbarRefillEnabled", CONFIG.hotbarRefillEnabled);
            for (int i = 0; i < CONFIG.selected.length; i++) o.addProperty("rotation_" + i, CONFIG.selected[i]);
            for (int i = 0; i < CONFIG.degrees.length; i++) o.addProperty("degrees_" + i, CONFIG.degrees[i]);
            JsonArray extra = new JsonArray();
            for (String id : CONFIG.extraRefillItems) extra.add(id);
            o.add("extraRefillItems", extra);
            Files.writeString(configPath, GSON.toJson(o));
        } catch (IOException ignored) {}
    }

    private static void loadConfig() {
        try {
            if (!Files.exists(configPath)) return;
            JsonObject o = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
            CONFIG.enabled = o.has("enabled") && o.get("enabled").getAsBoolean();
            if (o.has("duration")) CONFIG.durationSeconds = Math.max(.01, Math.min(2.0, o.get("duration").getAsDouble()));
            if (o.has("randomMode")) CONFIG.randomMode = o.get("randomMode").getAsBoolean();
            if (o.has("theme")) CONFIG.theme = Math.max(0, Math.min(5, o.get("theme").getAsInt()));
            if (o.has("hotbarRefillEnabled")) CONFIG.hotbarRefillEnabled = o.get("hotbarRefillEnabled").getAsBoolean();
            for (int i = 0; i < CONFIG.selected.length; i++) if (o.has("rotation_" + i)) CONFIG.selected[i] = o.get("rotation_" + i).getAsBoolean();
            for (int i = 0; i < CONFIG.degrees.length; i++) {
                if (o.has("degrees_" + i)) {
                    int max = maxDegrees(i);
                    CONFIG.degrees[i] = Math.max(1, Math.min(max, o.get("degrees_" + i).getAsInt()));
                }
            }
            if (o.has("extraRefillItems")) {
                CONFIG.extraRefillItems.clear();
                for (var el : o.getAsJsonArray("extraRefillItems")) CONFIG.extraRefillItems.add(el.getAsString());
            }
        } catch (Exception ignored) {}
    }

    public static class Config {
        public boolean enabled = false;
        public double durationSeconds = 1.00;
        public boolean randomMode = true;
        public int theme = 0;
        public int sequenceIndex = 0;
        public boolean[] selected = {true, true, true, false, false, false, false, false, false};
        // Kąt (w stopniach) ustawiony osobno dla każdego rodzaju obrotu; index 0 = FULL_360 (1-360), reszta 1-90.
        public int[] degrees = {360, 90, 90, 90, 90, 90, 90, 90, 90};
        // Ustawienia modułu Autostacking (dopełniania hotbara/off-handu).
        public boolean hotbarRefillEnabled = true;
        public Set<String> extraRefillItems = defaultRefillItems();

        private static Set<String> defaultRefillItems() {
            Set<String> s = new LinkedHashSet<>();
            String[] defaults = {
                "minecraft:golden_apple", "minecraft:firework_rocket", "minecraft:blaze_rod", "minecraft:cobweb",
                "minecraft:white_wool", "minecraft:orange_wool", "minecraft:magenta_wool", "minecraft:light_blue_wool",
                "minecraft:yellow_wool", "minecraft:lime_wool", "minecraft:pink_wool", "minecraft:gray_wool",
                "minecraft:light_gray_wool", "minecraft:cyan_wool", "minecraft:purple_wool", "minecraft:blue_wool",
                "minecraft:brown_wool", "minecraft:green_wool", "minecraft:red_wool", "minecraft:black_wool",
                "minecraft:white_banner", "minecraft:orange_banner", "minecraft:magenta_banner", "minecraft:light_blue_banner",
                "minecraft:yellow_banner", "minecraft:lime_banner", "minecraft:pink_banner", "minecraft:gray_banner",
                "minecraft:light_gray_banner", "minecraft:cyan_banner", "minecraft:purple_banner", "minecraft:blue_banner",
                "minecraft:brown_banner", "minecraft:green_banner", "minecraft:red_banner", "minecraft:black_banner"
            };
            for (String id : defaults) s.add(id);
            return s;
        }
    }
}
