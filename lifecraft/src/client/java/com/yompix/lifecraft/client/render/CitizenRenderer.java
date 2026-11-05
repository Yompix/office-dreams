package com.yompix.lifecraft.client.render;

import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.entity.CitizenEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Жители — модель игрока со своими скинами (у девушек тонкие руки) и живыми движениями:
 * машут рукой, жестикулируют в разговоре, переписываются в телефоне, думают, танцуют, радуются.
 */
public class CitizenRenderer extends BipedEntityRenderer<CitizenEntity, PlayerEntityModel<CitizenEntity>> {
    private static final Identifier[] SKINS = new Identifier[CitizenEntity.SKINS.length];

    static {
        for (int i = 0; i < SKINS.length; i++) SKINS[i] = LifeCraft.id("textures/entity/citizen/" + CitizenEntity.SKINS[i] + ".png");
    }

    private final PlayerEntityModel<CitizenEntity> wide, slim;

    public CitizenRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
        wide = model;
        slim = new Model(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
        addFeature(new HairFeature(this));
    }

    @Override
    public void render(CitizenEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        model = CitizenEntity.isSlim(e.getSkin()) ? slim : wide;
        m.push();
        float t = e.age + tickDelta;
        switch (e.getActivity()) {
            case DANCE -> m.translate(0, Math.abs(MathHelper.sin(t * 0.25f)) * 0.12f * e.getScale(), 0);
            case CHEER -> m.translate(0, Math.max(0, MathHelper.sin(t * 0.3f)) * 0.25f * e.getScale(), 0);
            case SIT -> m.translate(0, -0.45f * e.getScale(), 0);
            default -> {}
        }
        super.render(e, yaw, tickDelta, m, buffers, light);
        m.pop();
        if (e.hasQuest()) {
            // жёлтый «!» над головой — житель просит о помощи
            m.push();
            float bob = MathHelper.sin((e.age + tickDelta) * 0.12f) * 0.08f;
            m.translate(0, e.getHeight() + 0.75f + bob, 0);
            m.multiply(dispatcher.getRotation());
            m.scale(0.06f, -0.06f, 0.06f);
            var tr = getTextRenderer();
            tr.draw("!", -tr.getWidth("!") / 2f, 0, 0xFFFFD000, true, m.peek().getPositionMatrix(), buffers,
                    net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
            m.pop();
        }
    }

    @Override
    public Identifier getTexture(CitizenEntity e) {
        return SKINS[e.getSkin()];
    }

    /** Модель с дополнительными позами поверх обычной ходьбы. */
    static class Model extends PlayerEntityModel<CitizenEntity> {
        Model(ModelPart root, boolean thinArms) {
            super(root, thinArms);
        }

        @Override
        public void setAngles(CitizenEntity e, float limbAngle, float limbDistance, float age, float headYaw, float headPitch) {
            CitizenEntity.Activity a = e.getActivity();
            riding = e.hasVehicle() || a == CitizenEntity.Activity.SIT;
            super.setAngles(e, limbAngle, limbDistance, age, headYaw, headPitch);
            float t = age;
            switch (a) {
                case TALK -> {
                    rightArm.pitch = -0.55f + MathHelper.sin(t * 0.28f) * 0.35f;
                    rightArm.yaw = -0.35f + MathHelper.cos(t * 0.2f) * 0.15f;
                    leftArm.pitch = -0.35f + MathHelper.cos(t * 0.23f) * 0.25f;
                    leftArm.yaw = 0.3f;
                    head.pitch += MathHelper.sin(t * 0.45f) * 0.07f;
                }
                case WAVE -> {
                    rightArm.pitch = -2.75f;
                    rightArm.roll = MathHelper.sin(t * 0.55f) * 0.45f;
                    rightArm.yaw = 0;
                }
                case PHONE -> {
                    rightArm.pitch = -1.05f;
                    rightArm.yaw = -0.3f;
                    leftArm.pitch = -0.95f;
                    leftArm.yaw = 0.35f;
                    head.pitch = 0.5f;
                    head.yaw *= 0.2f;
                }
                case THINK -> {
                    rightArm.pitch = -2.0f;
                    rightArm.yaw = -0.65f;
                    leftArm.pitch = -0.7f;
                    leftArm.yaw = 0.75f;
                    head.pitch = -0.15f + MathHelper.sin(t * 0.05f) * 0.05f;
                    head.roll = 0.12f;
                }
                case DANCE -> {
                    rightArm.pitch = -2.6f + MathHelper.sin(t * 0.25f) * 0.6f;
                    leftArm.pitch = -2.6f + MathHelper.cos(t * 0.25f) * 0.6f;
                    rightArm.roll = 0.3f;
                    leftArm.roll = -0.3f;
                    body.yaw = MathHelper.sin(t * 0.125f) * 0.35f;
                    head.roll = MathHelper.sin(t * 0.25f) * 0.18f;
                    rightLeg.pitch = MathHelper.sin(t * 0.25f) * 0.35f;
                    leftLeg.pitch = -MathHelper.sin(t * 0.25f) * 0.35f;
                }
                case CHEER -> {
                    rightArm.pitch = -2.9f;
                    leftArm.pitch = -2.9f;
                    rightArm.roll = 0.35f + MathHelper.sin(t * 0.6f) * 0.2f;
                    leftArm.roll = -0.35f - MathHelper.sin(t * 0.6f) * 0.2f;
                }
                default -> {
                    // дыхание и лёгкое покачивание, когда стоит
                    if (limbDistance < 0.05f) {
                        rightArm.roll += MathHelper.sin(t * 0.07f) * 0.04f;
                        leftArm.roll -= MathHelper.sin(t * 0.07f) * 0.04f;
                    }
                }
            }
            leftSleeve.copyTransform(leftArm);
            rightSleeve.copyTransform(rightArm);
            leftPants.copyTransform(leftLeg);
            rightPants.copyTransform(rightLeg);
            jacket.copyTransform(body);
            hat.copyTransform(head);
        }
    }
}
