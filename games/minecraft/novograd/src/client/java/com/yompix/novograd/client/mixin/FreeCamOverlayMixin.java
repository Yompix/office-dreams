package com.yompix.novograd.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 0.8: камера кат-сцены свободно пролетает сквозь блоки — без «экрана в стене», огня и воды поверх кадра. */
@Mixin(InGameOverlayRenderer.class)
public class FreeCamOverlayMixin {
    @Inject(method = "renderOverlays", at = @At("HEAD"), cancellable = true)
    private static void novograd$noWallOverlay(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        if (com.yompix.novograd.client.studio.CineCam.cutscene) ci.cancel();
    }
}
