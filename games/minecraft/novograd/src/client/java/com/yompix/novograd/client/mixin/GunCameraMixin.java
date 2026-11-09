package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.GunFx;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Короткая тряска камеры от выстрела и покадровое обновление «ощущения» оружия. */
@Mixin(GameRenderer.class)
public class GunCameraMixin {
    /** Раз за кадр до камеры: пружины оружия, подброс камеры от отдачи, анимации. */
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void novograd$gunFrame(net.minecraft.client.render.RenderTickCounter counter, CallbackInfo ci) {
        GunFx.frame(counter.getTickDelta(true));
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("TAIL"))
    private void novograd$shake(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        GunFx.shake(matrices, tickDelta);
    }
}
