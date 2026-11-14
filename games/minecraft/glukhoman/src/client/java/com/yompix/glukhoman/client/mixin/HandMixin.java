package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.GlukhomanClient;
import com.yompix.glukhoman.item.FlashlightItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Рука с фонарём от первого лица: держит перед собой, дрожит от страха, качается при ходьбе. */
@Mixin(HeldItemRenderer.class)
public class HandMixin {
    @org.spongepowered.asm.mixin.Shadow
    private void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, float equipProgress, float swingProgress, net.minecraft.util.Arm arm) {
    }

    /** Камера у глаза — рук и камеры не видно (смотрим через неё). Опущена — рука держит камеру, качается в такт шагам. */
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void glukhoman$camcorder(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swing, ItemStack item, float equip,
                                     MatrixStack m, VertexConsumerProvider v, int light, CallbackInfo ci) {
        if (!item.isOf(com.yompix.glukhoman.GContent.CAMCORDER)) return;
        if (com.yompix.glukhoman.client.Viewfinder.up) {
            ci.cancel();
            return;
        }
        boolean right = (hand == Hand.MAIN_HAND) == (player.getMainArm() == net.minecraft.util.Arm.RIGHT);
        float k = com.yompix.glukhoman.client.GConfig.get().handSway / 100f;
        double ph = MathHelper.lerp(tickDelta, player.prevHorizontalSpeed, player.horizontalSpeed);
        double sp = Math.min(1.5, player.getVelocity().horizontalLength() / 0.13);
        m.push();
        m.translate(Math.sin(Math.PI * ph) * 0.03 * sp * k, -Math.abs(Math.cos(Math.PI * ph)) * 0.035 * sp * k, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (Math.sin(Math.PI * ph) * 2.5 * sp * k)));
        m.push();
        m.translate(right ? 0.05 : -0.05, -0.08, 0.05);
        renderArmHoldingItem(m, v, light, equip, swing, right ? net.minecraft.util.Arm.RIGHT : net.minecraft.util.Arm.LEFT);
        m.pop();
        m.translate(0, -0.06, 0);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void glukhoman$camcorderEnd(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swing, ItemStack item, float equip,
                                        MatrixStack m, VertexConsumerProvider v, int light, CallbackInfo ci) {
        if (item.isOf(com.yompix.glukhoman.GContent.CAMCORDER) && !com.yompix.glukhoman.client.Viewfinder.up) m.pop();
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void glukhoman$tremble(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swing, ItemStack item, float equip,
                                   MatrixStack m, VertexConsumerProvider v, int light, CallbackInfo ci) {
        if (!GlukhomanClient.inWorld || item.isEmpty()) return;
        float fear = 1 - GlukhomanClient.shownSanity / 100f;
        float t = (MinecraftClient.getInstance().world == null ? 0 : MinecraftClient.getInstance().world.getTime()) + tickDelta;
        float k = 0.004f + fear * fear * 0.02f;
        m.translate(Math.sin(t * 2.7) * k + Math.sin(t * 7.1) * k * 0.4, Math.cos(t * 3.3) * k, 0);
        if (item.getItem() instanceof FlashlightItem) {
            m.translate(hand == Hand.MAIN_HAND ? -0.08 : 0.08, 0.06, -0.08);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-6 + (float) Math.sin(t * 0.21) * 1.5f));
        }
    }
}
