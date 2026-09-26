package com.adixuyt.hitspin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

// Funkcja obrotu kamery po trafieniu gracza została CAŁKOWICIE usunięta
// (niedozwolona na serwerze, na którym gra autor). Zostaje tylko GUI,
// wybór motywu kolorystycznego i ustawienia modułu Autostacking.
public class HitSpinClient implements ClientModInitializer {
    public static final String MOD_ID = "hitspin";
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Config CONFIG = new Config();
    public static KeyBinding OPEN_GUI;
    private static Path configPath;

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
    }

    public static void saveConfig() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("theme", CONFIG.theme);
            o.addProperty("hotbarRefillEnabled", CONFIG.hotbarRefillEnabled);
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
            if (o.has("theme")) CONFIG.theme = Math.max(0, Math.min(5, o.get("theme").getAsInt()));
            if (o.has("hotbarRefillEnabled")) CONFIG.hotbarRefillEnabled = o.get("hotbarRefillEnabled").getAsBoolean();
            if (o.has("extraRefillItems")) {
                CONFIG.extraRefillItems.clear();
                for (var el : o.getAsJsonArray("extraRefillItems")) CONFIG.extraRefillItems.add(el.getAsString());
            }
        } catch (Exception ignored) {}
    }

    public static class Config {
        public int theme = 0;
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
