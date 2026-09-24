package com.adixuyt.hitspin.mixin;

import com.adixuyt.hitspin.HitSpinClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class AttackMixin {
    @Inject(method = "attackEntity", at = @At("TAIL"))
    private void hitspin$afterAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (target instanceof PlayerEntity && target != player) HitSpinClient.triggerRotation();
    }
}
