package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.style.StyleClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Скин из внешности Новограда (одежда, кожа, причёска) вместо скина Minecraft — у себя и у друзей. */
@Mixin(AbstractClientPlayerEntity.class)
public class PlayerSkinMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void novograd$styleSkin(CallbackInfoReturnable<SkinTextures> cir) {
        SkinTextures s = StyleClient.skin((AbstractClientPlayerEntity) (Object) this, cir.getReturnValue());
        if (s != null) cir.setReturnValue(s);
    }
}
