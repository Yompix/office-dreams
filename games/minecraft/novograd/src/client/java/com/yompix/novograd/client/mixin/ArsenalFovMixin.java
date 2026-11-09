package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.ArsenalClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Прицел снайперки: сужаем поле зрения мира (руку не трогаем). */
@Mixin(GameRenderer.class)
public class ArsenalFovMixin {
    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)D", at = @At("RETURN"), cancellable = true)
    private void novograd$scope(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (!changingFov) return;
        double k = ArsenalClient.fovScale(tickDelta);
        if (k < 0.999) cir.setReturnValue(cir.getReturnValue() * k);
    }
}
