package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.GunFx;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Сервер прислал длинный кулдаун ствола — это перезарядка: GunFx играет анимацию нужной длины. */
@Mixin(ItemCooldownManager.class)
public class GunCooldownMixin {
    @Inject(method = "set", at = @At("HEAD"))
    private void novograd$reload(Item item, int duration, CallbackInfo ci) {
        GunFx.onCooldown((ItemCooldownManager) (Object) this, item, duration);
    }
}
