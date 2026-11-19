package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.story.CineRec;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Покадровая запись видео (только автотест): кадр рисуется ровно на нужной доле тика — ровный шаг времени. */
@Mixin(RenderTickCounter.Dynamic.class)
public class RecTickDeltaMixin {
    @Inject(method = "getTickDelta", at = @At("HEAD"), cancellable = true)
    private void novograd$fixed(boolean ignoreFreeze, CallbackInfoReturnable<Float> cir) {
        if (CineRec.forceDelta >= 0) cir.setReturnValue(CineRec.forceDelta);
    }
}
