package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.GlukhomanClient;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** В Глухомани нет спокойной музыки Minecraft — только свой звуковой фон. */
@Mixin(MusicTracker.class)
public abstract class NoMusicMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void glukhoman$noMusic(CallbackInfo ci) {
        if (!GlukhomanClient.inWorld) return;
        ((MusicTracker) (Object) this).stop();
        ci.cancel();
    }
}
