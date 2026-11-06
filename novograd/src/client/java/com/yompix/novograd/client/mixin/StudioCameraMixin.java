package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.studio.CineCam;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Киностудия и кат-сцены: наклон камеры (голландский угол) и зум. */
@Mixin(GameRenderer.class)
public class StudioCameraMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("TAIL"))
    private void novograd$roll(MatrixStack m, float delta, CallbackInfo ci) {
        float r = CineCam.roll(delta);
        if (r != 0) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(r));
    }

    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
    private void novograd$zoom(Camera camera, float delta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (!changingFov) return;
        double v = cir.getReturnValue(), f = CineCam.fov(v, delta);
        if (f != v) cir.setReturnValue(f);
    }
}
