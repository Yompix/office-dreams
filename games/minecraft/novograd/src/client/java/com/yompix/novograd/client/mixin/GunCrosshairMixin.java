package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.gun.GunHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Со стволом — свой прицел (расходится при движении и стрельбе, прячется в ADS) вместо ванильного плюсика. */
@Mixin(InGameHud.class)
public class GunCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void novograd$gunCrosshair(DrawContext ctx, RenderTickCounter counter, CallbackInfo ci) {
        if (GunHud.replacesCrosshair()) ci.cancel();
    }
}
