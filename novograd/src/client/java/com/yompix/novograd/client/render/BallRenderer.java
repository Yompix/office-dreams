package com.yompix.novograd.client.render;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.BallEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Мяч: кубик с футбольной текстурой, крутится, когда катится. */
public class BallRenderer extends EntityRenderer<BallEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(Novograd.id("ball"), "main");
    private static final Identifier TEX = Novograd.id("textures/entity/ball.png");
    private final ModelPart ball;

    public BallRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        ball = ctx.getPart(LAYER).getChild("ball");
        shadowRadius = 0.2f;
    }

    public static TexturedModelData modelData() {
        ModelData data = new ModelData();
        data.getRoot().addChild("ball", ModelPartBuilder.create().uv(0, 0).cuboid(-3.5f, -3.5f, -3.5f, 7, 7, 7), ModelTransform.NONE);
        return TexturedModelData.of(data, 28, 14);
    }

    @Override
    public void render(BallEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        m.push();
        m.translate(0, 3.5 / 16, 0);
        float spin = MathHelper.lerp(tickDelta, e.prevSpin, e.spin);
        var v = e.getVelocity();
        m.multiply(RotationAxis.POSITIVE_Y.rotation((float) Math.atan2(v.x, v.z)));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin));
        ball.render(m, buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX)), light, OverlayTexture.DEFAULT_UV);
        m.pop();
        super.render(e, yaw, tickDelta, m, buffers, light);
    }

    @Override
    public Identifier getTexture(BallEntity entity) {
        return TEX;
    }
}
