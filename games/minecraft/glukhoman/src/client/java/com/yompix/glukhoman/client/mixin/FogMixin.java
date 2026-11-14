package com.yompix.glukhoman.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yompix.glukhoman.client.GlukhomanClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Туман и его цвет по главам: сумерки, ночь, серый туман просеки, чёрная шахта, лиловый бункер. */
@Mixin(BackgroundRenderer.class)
public class FogMixin {
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void glukhoman$fog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (!GlukhomanClient.inWorld || camera.getSubmersionType() != net.minecraft.block.enums.CameraSubmersionType.NONE) return;
        float s = GlukhomanClient.fogStart, e = GlukhomanClient.fogEnd;
        if (fogType == BackgroundRenderer.FogType.FOG_SKY) {
            RenderSystem.setShaderFogStart(0);
            RenderSystem.setShaderFogEnd(Math.min(RenderSystem.getShaderFogEnd(), e));
            return;
        }
        RenderSystem.setShaderFogStart(Math.min(RenderSystem.getShaderFogStart(), s));
        RenderSystem.setShaderFogEnd(Math.min(RenderSystem.getShaderFogEnd(), e));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private static void glukhoman$color(Camera camera, float tickDelta, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfo ci) {
        if (!GlukhomanClient.inWorld || camera.getSubmersionType() != net.minecraft.block.enums.CameraSubmersionType.NONE) return;
        red = GlukhomanClient.fogR;
        green = GlukhomanClient.fogG;
        blue = GlukhomanClient.fogB;
        RenderSystem.clearColor(red, green, blue, 0f);
    }
}
