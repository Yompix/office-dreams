package com.yompix.novograd.client.mixin;

import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 0.8: в кат-сцене камера «в воздухе» всегда: пролетая сквозь воду, лаву или снег, она не включает их туман
 * (иначе небо и туман мигают на пролёте).
 */
@Mixin(Camera.class)
public class FreeCamSubmersionMixin {
    @Inject(method = "getSubmersionType", at = @At("HEAD"), cancellable = true)
    private void novograd$air(CallbackInfoReturnable<CameraSubmersionType> cir) {
        if (com.yompix.novograd.client.studio.CineCam.cutscene) cir.setReturnValue(CameraSubmersionType.NONE);
    }
}
