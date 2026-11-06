package com.yompix.novograd.client.mixin;

import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** В Новограде нет фоновой музыки Minecraft: музыка — только своё радио (в машине, в меню, в наушниках). */
@Mixin(MusicTracker.class)
public abstract class NoVanillaMusicMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void novograd$noMusic(CallbackInfo ci) {
        ((MusicTracker) (Object) this).stop();
        ci.cancel();
    }
}
