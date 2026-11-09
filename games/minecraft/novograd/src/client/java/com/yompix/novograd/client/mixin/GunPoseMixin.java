package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.GunFx;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** С пистолетом/автоматом в руке игрок (и любой гуманоид) держит оружие в прицеле: двумя руками, пистолет — одной. */
@Mixin(BipedEntityModel.class)
public class GunPoseMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void novograd$aim(LivingEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, CallbackInfo ci) {
        GunFx.pose((BipedEntityModel<?>) (Object) this, e);
    }
}
