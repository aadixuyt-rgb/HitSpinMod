package pl.hotbarrefill;

import com.adixuyt.hitspin.HitSpinClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
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
            cooldown = 10; // ~0.5 s - dać serwerowi czas potwierdzić zmianę, zanim wyślemy kolejną
        });
    }

    private static void refill(MinecraftClient client, int handlerSlot) {
        var handler = client.player.currentScreenHandler;
        ItemStack target = handler.getSlot(handlerSlot).getStack();
        if (target.isEmpty() || target.getCount() >= target.getMaxCount() || !isAllowed(target)) return;

        for (int invSlot = 9; invSlot <= 35; invSlot++) {
            target = handler.getSlot(handlerSlot).getStack();
            if (target.isEmpty() || target.getCount() >= target.getMaxCount()) break;

            ItemStack source = handler.getSlot(invSlot).getStack();
            if (source.isEmpty() || !ItemStack.areItemsAndComponentsEqual(target, source)) continue;

            // Bezpieczna kolejność: kursor zawsze kończy pusty.
            click(client, invSlot, 0);     // 1) weź stos z plecaka na kursor (slot w plecaku pusty)
            click(client, handlerSlot, 0); // 2) dolej kursor do celu, do jego max; nadmiar zostaje na kursorze
            click(client, invSlot, 0);     // 3) odłóż ewentualną resztę z powrotem do (pustego) slotu w plecaku
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
        // Jedyne źródło prawdy to lista w GUI (zakładka "Autostacking" -> "Dodatkowe bloki").
        // Domyślnie zawiera złote jabłko, fajerwerki, kość blaze, pajęczynę, wełnę i sztandary,
        // ale każdy z nich można tam odznaczyć - to naprawdę je wyłącza.
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id != null && HitSpinClient.CONFIG.extraRefillItems.contains(id.toString());
    }
}
