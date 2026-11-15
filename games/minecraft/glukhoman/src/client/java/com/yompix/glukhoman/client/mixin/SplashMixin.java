package com.yompix.glukhoman.client.mixin;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.resource.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Экран загрузки: вместо логотипа Mojang Studios — наш (тёмный фон включается настройкой «монохромный логотип»). */
@Mixin(targets = "net.minecraft.client.gui.screen.SplashOverlay$LogoTexture")
public abstract class SplashMixin extends ResourceTexture {
    SplashMixin(net.minecraft.util.Identifier id) {
        super(id);
    }

    @Inject(method = "loadTextureData", at = @At("HEAD"), cancellable = true)
    private void glukhoman$logo(ResourceManager rm, CallbackInfoReturnable<ResourceTexture.TextureData> cir) {
        try (var in = SplashMixin.class.getResourceAsStream("/assets/glukhoman/textures/gui/splash_logo.png")) {
            if (in == null) return;
            cir.setReturnValue(new ResourceTexture.TextureData(new net.minecraft.client.resource.metadata.TextureResourceMetadata(true, true), NativeImage.read(in)));
        } catch (Exception ignored) {
        }
    }
}
