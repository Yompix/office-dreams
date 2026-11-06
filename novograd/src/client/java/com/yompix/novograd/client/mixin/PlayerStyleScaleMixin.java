package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.style.StyleClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Накачанный в зале герой чуть шире в плечах. */
@Mixin(PlayerEntityRenderer.class)
public class PlayerStyleScaleMixin {
    @Inject(method = "scale(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
    private void novograd$muscles(AbstractClientPlayerEntity player, MatrixStack m, float amount, CallbackInfo ci) {
        float w = StyleClient.width(player);
        if (w != 1f) m.scale(w, 1f, w);
    }
}
