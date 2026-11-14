package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.Recorder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Автотест-запись «как видит игрок»: после всего кадра (мир, рука, HUD). */
@Mixin(GameRenderer.class)
public class RecordMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void glukhoman$rec(RenderTickCounter counter, boolean tick, CallbackInfo ci) {
        Recorder.frameFree();
    }
}
