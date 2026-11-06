package com.yompix.novograd.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Новоград: без сердечек, голода, брони и опыта Майнкрафта — здоровье и броня на радаре, как в GTA. */
@Mixin(InGameHud.class)
public class HudBarsMixin {
    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void novograd$noBars(DrawContext ctx, CallbackInfo ci) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player != null && !mc.player.isCreative() && !mc.player.isSpectator()) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void novograd$noXp(DrawContext ctx, int x, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true, require = 0)
    private void novograd$noXpLevel(DrawContext ctx, net.minecraft.client.render.RenderTickCounter t, CallbackInfo ci) {
        ci.cancel();
    }
}
