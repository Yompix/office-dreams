package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.style.StyleClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 0.9.6: редактор внешности — герой на витрине идёт шагом (подменяем фазу и размах шага только на время отрисовки витрины). */
@Mixin(PlayerEntityModel.class)
public class PreviewWalkMixin {
    @ModifyVariable(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float novograd$phase(float limbAngle) {
        return StyleClient.walkPreview ? StyleClient.walkPhase : limbAngle;
    }

    @ModifyVariable(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float novograd$amount(float limbDistance) {
        return StyleClient.walkPreview ? 0.75f : limbDistance;
    }
}
