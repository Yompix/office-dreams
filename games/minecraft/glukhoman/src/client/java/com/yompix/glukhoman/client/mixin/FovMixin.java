package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.Cine;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Угол обзора кат-сцены (наезды, долли-зум). */
@Mixin(GameRenderer.class)
public class FovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void glukhoman$fov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (Cine.active && changingFov) cir.setReturnValue((double) (Cine.fov * Cine.fovScale));
        else if (!Cine.active && com.yompix.glukhoman.client.Viewfinder.up) cir.setReturnValue(cir.getReturnValueD() * 0.72);   // объектив: узкий кадр
    }
}
