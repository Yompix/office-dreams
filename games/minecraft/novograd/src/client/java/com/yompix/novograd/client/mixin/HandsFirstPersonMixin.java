package com.yompix.novograd.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.9.8 «Руки» от первого лица: пока игрок что-то делает (банкомат, автомат, колонка…), вместо ванильной руки —
 * свои кисти и предметы (HandsClient). Бита и нож — свои удары: замах и дуга, выпад и полоснуть.
 */
@Mixin(HeldItemRenderer.class)
public class HandsFirstPersonMixin {
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD"), cancellable = true)
    private void novograd$hands(float tickDelta, MatrixStack m, VertexConsumerProvider.Immediate buffers, ClientPlayerEntity player, int light, CallbackInfo ci) {
        if (com.yompix.novograd.client.anim.HandsClient.renderFirst(m, buffers, player, tickDelta, light)) {
            buffers.draw();
            ci.cancel();
        }
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void novograd$melee(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swing, ItemStack item,
                                float equip, MatrixStack m, VertexConsumerProvider buffers, int light, CallbackInfo ci) {
        if (hand != Hand.MAIN_HAND || swing <= 0 || item.isEmpty()) return;
        String id = Registries.ITEM.getId(item.getItem()).getPath();
        boolean bat = id.equals("bat"), knife = id.equals("knife") || id.equals("machete");
        if (!bat && !knife) return;
        ci.cancel();
        boolean right = player.getMainArm() == Arm.RIGHT;
        float s = right ? 1 : -1;
        m.push();
        m.translate(s * 0.56f, -0.52f + equip * -0.6f, -0.72f);
        if (bat) {
            // замах вправо-назад → широкая дуга через экран → доводка
            float wind = MathHelper.clamp(swing / 0.25f, 0, 1), hit = MathHelper.clamp((swing - 0.25f) / 0.4f, 0, 1);
            hit = hit * hit * (3 - 2 * hit);
            float yaw = MathHelper.lerp(hit, 55f * wind, -75f);
            m.translate(s * (0.15f * wind - 0.55f * hit), 0.12f * wind - 0.05f * hit, 0.1f * wind - 0.2f * hit);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s * yaw));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(s * (-60f * hit + 20f * wind)));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30f * wind + 10f * hit));
        } else {
            // выпад вперёд, затем полоснуть по диагонали
            float stab = swing < 0.3f ? swing / 0.3f : Math.max(0, 1 - (swing - 0.3f) / 0.3f), slash = MathHelper.clamp((swing - 0.35f) / 0.45f, 0, 1);
            m.translate(s * (-0.08f * stab - 0.4f * slash), 0.06f * stab + 0.1f * slash, -0.35f * stab);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-70f * stab));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(s * 55f * slash));
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s * -35f * slash));
        }
        ((HeldItemRenderer) (Object) this).renderItem(player, item, right ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND,
                !right, m, buffers, light);
        m.pop();
    }
}
