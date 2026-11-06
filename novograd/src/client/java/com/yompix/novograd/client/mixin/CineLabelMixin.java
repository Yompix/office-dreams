package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.studio.CineCam;
import com.yompix.novograd.client.studio.Studio;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** В кат-сценах и при съёмке в киностудии — без надписей над головами и метками: чистый кадр. */
@Mixin(EntityRenderer.class)
public class CineLabelMixin {
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void novograd$noLabels(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (CineCam.cutscene || Studio.playing()) cir.setReturnValue(false);
    }
}
