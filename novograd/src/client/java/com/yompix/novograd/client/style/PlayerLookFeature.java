package com.yompix.novograd.client.style;

import com.yompix.novograd.client.render.HairFeature;
import com.yompix.novograd.life.Appearance;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;

/** Объёмная причёска, борода, головной убор и очки игрока — те же модели, что у жителей ({@link HairFeature}). */
public class PlayerLookFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final HairFeature hair = new HairFeature(null);

    public PlayerLookFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
        super(ctx);
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity p, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        Appearance a = StyleClient.look(p);
        if (a == null || !a.custom || p.isInvisible() || !p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) return;
        long t = p.age + p.getId() * 37L;
        boolean blink = t % 90 < 3 || (t % 230 > 100 && t % 230 < 103);
        m.push();
        getContextModel().head.rotate(m);
        hair.drawLook(m, buffers, light, StyleClient.headJson(a), blink, p.isSleeping(), false, p.getId());
        m.pop();
    }
}
