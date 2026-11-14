package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.Recorder;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Покадровая запись (только автотест): кадр рисуется ровно на нужной доле тика — ровный шаг времени в видео. */
@Mixin(RenderTickCounter.Dynamic.class)
public class TickDeltaMixin {
    @Inject(method = "getTickDelta", at = @At("HEAD"), cancellable = true)
    private void glukhoman$fixed(boolean ignoreFreeze, CallbackInfoReturnable<Float> cir) {
        if (Recorder.forceDelta >= 0) cir.setReturnValue(Recorder.forceDelta);
    }
}
