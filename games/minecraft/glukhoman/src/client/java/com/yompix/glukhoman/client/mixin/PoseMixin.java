package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.client.Viewfinder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Поза игрока с камерой в третьем лице (друзья в кооперативе видят): снимает — обе руки подняты к глазу, голова в окуляре,
 * шаг осторожный и короткий; не снимает — камера опущена в одной руке, вторая машет при ходьбе как обычно.
 */
@Mixin(BipedEntityModel.class)
public class PoseMixin<T extends LivingEntity> {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void glukhoman$camPose(T e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch, CallbackInfo ci) {
        if (!(e instanceof PlayerEntity p)) return;
        boolean main = p.getMainHandStack().isOf(GContent.CAMCORDER), off = p.getOffHandStack().isOf(GContent.CAMCORDER);
        if (!main && !off) return;
        BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;
        MinecraftClient c = MinecraftClient.getInstance();
        boolean filming = p == c.player ? Viewfinder.up : Viewfinder.FILMERS.contains(p.getUuid());
        boolean rightHolds = main == (p.getMainArm() == Arm.RIGHT);
        float hy = headYaw * MathHelper.RADIANS_PER_DEGREE, hp = headPitch * MathHelper.RADIANS_PER_DEGREE;
        if (filming) {
            // обе руки к лицу: локти разведены, камера у правого глаза
            m.rightArm.pitch = -1.45f + hp;
            m.rightArm.yaw = -0.32f + hy;
            m.rightArm.roll = 0;
            m.leftArm.pitch = -1.5f + hp;
            m.leftArm.yaw = 0.62f + hy;
            m.leftArm.roll = 0;
            // осторожный шаг: короче и мягче, корпус чуть вперёд
            m.rightLeg.pitch *= 0.55f;
            m.leftLeg.pitch *= 0.55f;
            m.body.pitch = 0.08f;
        } else {
            // камера опущена в руке: рука почти неподвижна, чуть вперёд, лёгкое покачивание в такт шагу
            var arm = rightHolds ? m.rightArm : m.leftArm;
            arm.pitch = -0.32f + MathHelper.cos(limbAngle * 0.6662f) * 0.18f * limbDistance;
            arm.yaw = rightHolds ? -0.1f : 0.1f;
        }
    }
}
