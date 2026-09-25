package pl.hotbarrefill;

import com.adixuyt.hitspin.HitSpinClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

// Moduł "Autostacking" - uruchamiany jako drugi entrypoint tego samego moda (Hit Spin).
// Włącznik i lista dodatkowych przedmiotów są w GUI Hit Spina i trzymane we wspólnym configu.
public class HotbarRefillClient implements ClientModInitializer {
    private int cooldown;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!HitSpinClient.CONFIG.hotbarRefillEnabled) return;
            if (client.player == null || client.interactionManager == null) return;
            if (client.currentScreen != null) return; // never opens or controls a GUI
            if (cooldown > 0) { cooldown--; return; }
            if (!client.player.currentScreenHandler.getCursorStack().isEmpty()) return;

            for (int i = 0; i < 9; i++) refill(client, 36 + i); // player screen hotbar slots
            refill(client, 45); // player screen off-hand slot
            cooldown = 2;
        });
    }

    private static void refill(MinecraftClient client, int handlerSlot) {
        ItemStack target = client.player.currentScreenHandler.getSlot(handlerSlot).getStack();
        if (target.isEmpty() || target.getCount() >= target.getMaxCount() || !isAllowed(target)) return;

        int need = target.getMaxCount() - target.getCount();
        boolean started = false;

        for (int invSlot = 9; invSlot <= 35 && need > 0; invSlot++) {
            ItemStack source = client.player.currentScreenHandler.getSlot(invSlot).getStack();
            if (source.isEmpty() || !ItemStack.areItemsAndComponentsEqual(target, source)) continue;

            if (!started) {
                click(client, handlerSlot, 0); // pick up the incomplete stack
                started = true;
            }

            click(client, invSlot, 0);       // merge inventory stack into cursor
            click(client, handlerSlot, 0);   // put the filled/remaining stack back

            target = client.player.currentScreenHandler.getSlot(handlerSlot).getStack();
            need = target.isEmpty() ? 0 : target.getMaxCount() - target.getCount();
            if (need <= 0) break;
            if (!client.player.currentScreenHandler.getCursorStack().isEmpty()) {
                // A server/client desync: stop rather than touching unrelated items.
                break;
            }
        }
    }

    private static void click(MinecraftClient client, int slot, int button) {
        client.interactionManager.clickSlot(
            client.player.currentScreenHandler.syncId,
            slot,
            button,
            SlotActionType.PICKUP,
            client.player
        );
    }

    private static boolean isAllowed(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.GOLDEN_APPLE || item == Items.FIREWORK_ROCKET ||
            item == Items.BLAZE_ROD || item == Items.COBWEB) return true;

        // All wool colours.
        if (item == Items.WHITE_WOOL || item == Items.ORANGE_WOOL || item == Items.MAGENTA_WOOL ||
            item == Items.LIGHT_BLUE_WOOL || item == Items.YELLOW_WOOL || item == Items.LIME_WOOL ||
            item == Items.PINK_WOOL || item == Items.GRAY_WOOL || item == Items.LIGHT_GRAY_WOOL ||
            item == Items.CYAN_WOOL || item == Items.PURPLE_WOOL || item == Items.BLUE_WOOL ||
            item == Items.BROWN_WOOL || item == Items.GREEN_WOOL || item == Items.RED_WOOL ||
            item == Items.BLACK_WOOL) return true;

        // All banner colours.
        if (item == Items.WHITE_BANNER || item == Items.ORANGE_BANNER || item == Items.MAGENTA_BANNER ||
            item == Items.LIGHT_BLUE_BANNER || item == Items.YELLOW_BANNER || item == Items.LIME_BANNER ||
            item == Items.PINK_BANNER || item == Items.GRAY_BANNER || item == Items.LIGHT_GRAY_BANNER ||
            item == Items.CYAN_BANNER || item == Items.PURPLE_BANNER || item == Items.BLUE_BANNER ||
            item == Items.BROWN_BANNER || item == Items.GREEN_BANNER || item == Items.RED_BANNER ||
            item == Items.BLACK_BANNER) return true;

        // Dodatkowe bloki/przedmioty wybrane ręcznie w GUI (zakładka "Autostacking").
        Identifier id = Registries.ITEM.getId(item);
        return id != null && HitSpinClient.CONFIG.extraRefillItems.contains(id.toString());
    }
}
