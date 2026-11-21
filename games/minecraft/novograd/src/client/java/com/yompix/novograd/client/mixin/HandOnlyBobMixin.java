package com.yompix.novograd.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.8: от первого лица при ходьбе качается только предмет в руке (ванильное «покачивание» руки остаётся), а камера —
 * нет (Настройки → «Качание камеры»: включить — как в Minecraft). Опция Minecraft «Покачивание» по-прежнему главная.
 */
@Mixin(GameRenderer.class)
public class HandOnlyBobMixin {
    @Unique
    private static boolean novograd$inHand;

    @Inject(method = "renderHand", at = @At("HEAD"))
    private void novograd$handStart(Camera camera, float tickDelta, Matrix4f matrix, CallbackInfo ci) {
        novograd$inHand = true;
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void novograd$handEnd(Camera camera, float tickDelta, Matrix4f matrix, CallbackInfo ci) {
        novograd$inHand = false;
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void novograd$noCameraBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (!novograd$inHand && !com.yompix.novograd.client.NovogradConfig.get().camBob) ci.cancel();
    }
}
