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
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Рука с фонарём от первого лица: держит перед собой, дрожит от страха, качается при ходьбе. */
@Mixin(HeldItemRenderer.class)
public class HandMixin {
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
