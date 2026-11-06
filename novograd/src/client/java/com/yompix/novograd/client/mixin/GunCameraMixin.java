package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.GunFx;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Короткая тряска камеры от выстрела. */
@Mixin(GameRenderer.class)
public class GunCameraMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("TAIL"))
    private void novograd$shake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        GunFx.shake(matrices, tickDelta);
    }
}
