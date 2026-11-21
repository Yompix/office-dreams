package com.yompix.novograd.client.mixin;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 0.8: живые анимации игроков — слоями поверх ванильной позы (client/anim/PlayerAnim). */
@Mixin(PlayerEntityModel.class)
public class PlayerAnimMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void novograd$live(LivingEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, CallbackInfo ci) {
        com.yompix.novograd.client.anim.PlayerAnim.apply((PlayerEntityModel<?>) (Object) this, e, limbAngle, limbDistance, age, headYaw, headPitch);
    }
}
