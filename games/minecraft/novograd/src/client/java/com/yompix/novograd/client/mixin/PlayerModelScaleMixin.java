package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.style.StyleClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 0.9.6: при широком/узком телосложении голова остаётся обычной (модель общая — ставим каждый кадр). */
@Mixin(PlayerEntityModel.class)
public class PlayerModelScaleMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void novograd$head(LivingEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, CallbackInfo ci) {
        PlayerEntityModel<?> m = (PlayerEntityModel<?>) (Object) this;
        float inv = e instanceof PlayerEntity p ? 1f / StyleClient.width(p) : 1f;
        m.head.xScale = m.head.zScale = inv;
        m.hat.xScale = m.hat.zScale = inv;
    }
}
