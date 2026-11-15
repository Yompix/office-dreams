package com.yompix.glukhoman.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Экран загрузки на тёмном фоне (а не красном): включаем «монохромный логотип» до первой отрисовки. */
@Mixin(SplashOverlay.class)
public class SplashBgMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void glukhoman$dark(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        var o = MinecraftClient.getInstance().options;
        if (o != null && !o.getMonochromeLogo().getValue()) o.getMonochromeLogo().setValue(true);
    }
}
