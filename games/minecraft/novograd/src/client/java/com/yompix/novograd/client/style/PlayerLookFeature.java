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
        if (p.isInvisible()) return;
        String emote = com.yompix.novograd.client.anim.PlayerAnim.emoteOf(p);
        if (emote.startsWith("gym_")) GymProps.render(m, buffers, light, emote, getContextModel()); // 0.9.6: снаряды спортзала
        Appearance a = StyleClient.look(p);
        if (a == null || !a.custom) return;
        var json = StyleClient.headJson(a);
        if (p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
            m.push();
            getContextModel().body.rotate(m);
            hair.drawBody(m, buffers, light, json);
            m.pop();
        }
        if (!p.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) return;
        boolean blink = HairFeature.blink(p.getId(), p.age);
        m.push();
        getContextModel().head.rotate(m);
        hair.drawLook(m, buffers, light, json, blink, p.isSleeping(), false, p.getId());
        m.pop();
    }
}
