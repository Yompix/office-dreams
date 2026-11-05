package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.PlayerWeight;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Полный герой выглядит шире, стройный — уже. */
@Mixin(PlayerEntityRenderer.class)
public class PlayerWidthMixin {
    @Inject(method = "scale(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
    private void novograd$width(AbstractClientPlayerEntity player, MatrixStack m, float amount, CallbackInfo ci) {
        float w = PlayerWeight.width(player);
        if (w != 1f) m.scale(w, 1f, w);
        if (Boolean.getBoolean("novograd.autotest.log") && player.age % 40 == 0) com.yompix.novograd.Novograd.LOG.info("[width] {} {}", player.getName().getString(), w);
    }
}
