package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.GlukhomanClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Тряска камеры: удар, лифт, погоня, паника. */
@Mixin(GameRenderer.class)
public class ShakeMixin {
    /** С камерой в руках ванильное покачивание выключено — его заменяет HandCam (шаг в шаг, без двойной качки). */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void glukhoman$noVanillaBob(MatrixStack m, float tickDelta, CallbackInfo ci) {
        if (com.yompix.glukhoman.client.HandCam.active) ci.cancel();
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("TAIL"))
    private void glukhoman$shake(MatrixStack m, float tickDelta, CallbackInfo ci) {
        if (com.yompix.glukhoman.client.Cine.active && com.yompix.glukhoman.client.Cine.roll != 0)
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(com.yompix.glukhoman.client.Cine.roll));
        if (com.yompix.glukhoman.client.HandCam.active && com.yompix.glukhoman.client.HandCam.roll != 0)
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(com.yompix.glukhoman.client.HandCam.roll));
        float s = GlukhomanClient.shake;
        if (s < 0.02f) return;
        MinecraftClient c = MinecraftClient.getInstance();
        float t = (c.world == null ? 0 : c.world.getTime()) + tickDelta;
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(t * 1.9) * s * 1.6f));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.cos(t * 2.3) * s * 1.1f));
    }
}
