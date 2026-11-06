package com.yompix.novograd.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yompix.novograd.client.DriveClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Туман Новограда (рассвет, дождь, горы): ближе обычного; с включёнными фарами в машине видно дальше (DriveClient.fogRange). */
@Mixin(BackgroundRenderer.class)
public class DriveFogMixin {
    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void novograd$fog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (fogType != BackgroundRenderer.FogType.FOG_TERRAIN || thickFog || camera.getSubmersionType() != net.minecraft.block.enums.CameraSubmersionType.NONE) return;
        float[] r = DriveClient.fogRange(viewDistance);
        if (r == null) return;
        RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), r[0]));
        RenderSystem.setShaderFogEnd(Math.min(RenderSystem.getShaderFogEnd(), r[1]));
    }
}
