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
    @org.spongepowered.asm.mixin.Unique
    private static final java.util.Map<java.util.UUID, float[]> GLUKHOMAN$RAISE = new java.util.HashMap<>();

    @org.spongepowered.asm.mixin.Unique
    private static float glukhoman$raise(java.util.UUID id, boolean up) {
        float[] s = GLUKHOMAN$RAISE.computeIfAbsent(id, k -> new float[]{up ? 1 : 0, 0});
        long now = System.nanoTime();
        float t = now / 1e9f % 100000;
        float dt = s[1] == 0 ? 0 : Math.max(0, Math.min(0.1f, t - s[1]));
        s[1] = t;
        s[0] = MathHelper.clamp(s[0] + (up ? dt : -dt) / 0.5f, 0, 1);
        return s[0];
    }

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
        // 0.5.1: камера поднимается к глазу плавно (~0.5 с), а не прыжком — тот же темп, что у первого лица
        float u = glukhoman$raise(p.getUuid(), filming);
        float ew = u * u * (3 - 2 * u);
        var arm = rightHolds ? m.rightArm : m.leftArm;
        float downP = -0.32f + MathHelper.cos(limbAngle * 0.6662f) * 0.18f * limbDistance, downY = rightHolds ? -0.1f : 0.1f;
        if (ew > 0.001f) {
            // обе руки к лицу: локти разведены, камера у правого глаза
            m.rightArm.pitch = MathHelper.lerp(ew, rightHolds ? downP : m.rightArm.pitch, -1.45f + hp);
            m.rightArm.yaw = MathHelper.lerp(ew, rightHolds ? downY : m.rightArm.yaw, -0.32f + hy);
            m.rightArm.roll = MathHelper.lerp(ew, m.rightArm.roll, 0);
            m.leftArm.pitch = MathHelper.lerp(ew, !rightHolds ? downP : m.leftArm.pitch, -1.5f + hp);
            m.leftArm.yaw = MathHelper.lerp(ew, !rightHolds ? downY : m.leftArm.yaw, 0.62f + hy);
            m.leftArm.roll = MathHelper.lerp(ew, m.leftArm.roll, 0);
            // осторожный шаг: короче и мягче, корпус чуть вперёд
            m.rightLeg.pitch *= 1 - 0.45f * ew;
            m.leftLeg.pitch *= 1 - 0.45f * ew;
            m.body.pitch = 0.08f * ew;
        } else {
            // камера опущена в руке: рука почти неподвижна, чуть вперёд, лёгкое покачивание в такт шагу
            arm.pitch = downP;
            arm.yaw = downY;
        }
    }
}
