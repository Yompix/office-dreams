package com.yompix.lifecraft.client.render;

import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.entity.CarEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Электроскутер: дека, руль с фарой, сиденье, два маленьких колеса, наклон в поворотах. */
public class ScooterRenderer extends EntityRenderer<CarEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(LifeCraft.id("scooter"), "main");
    private static final Identifier BASE = LifeCraft.id("textures/entity/car/base.png");
    private final ModelPart root;

    public ScooterRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        root = ctx.getPart(LAYER);
        shadowRadius = 0.4f;
    }

    private static ModelPartBuilder box(float x1, float y1, float z1, float x2, float y2, float z2) {
        return ModelPartBuilder.create().uv(0, 0).cuboid(x1, y1, z1, x2 - x1, y2 - y1, z2 - z1);
    }

    public static TexturedModelData modelData() {
        ModelData data = new ModelData();
        ModelPartData r = data.getRoot();
        r.addChild("deck", box(-2.5f, 2, -8, 2.5f, 3.5f, 8), ModelTransform.NONE);
        r.addChild("stem", box(-0.8f, 3, 7, 0.8f, 18, 8.6f), ModelTransform.NONE);
        r.addChild("bar", box(-6, 17.5f, 7, 6, 18.8f, 8.4f), ModelTransform.NONE);
        r.addChild("grips", ModelPartBuilder.create().uv(0, 0).cuboid(-6.5f, 17.3f, 6.8f, 2, 1.8f, 1.8f).cuboid(4.5f, 17.3f, 6.8f, 2, 1.8f, 1.8f), ModelTransform.NONE);
        r.addChild("lamp", box(-1.2f, 15, 8.5f, 1.2f, 16.6f, 9.2f), ModelTransform.NONE);
        r.addChild("seat_post", box(-0.6f, 3.5f, -5, 0.6f, 9, -3.8f), ModelTransform.NONE);
        r.addChild("seat", box(-2.4f, 9, -7, 2.4f, 10.4f, -2), ModelTransform.NONE);
        r.addChild("fender", box(-1.8f, 4.5f, -10, 1.8f, 5.2f, -6), ModelTransform.NONE);
        r.addChild("wheel_f", box(-1, -2.2f, -2.2f, 1, 2.2f, 2.2f), ModelTransform.pivot(0, 2.2f, 8));
        r.addChild("wheel_r", box(-1, -2.2f, -2.2f, 1, 2.2f, 2.2f), ModelTransform.pivot(0, 2.2f, -8));
        return TexturedModelData.of(data, 64, 64);
    }

    @Override
    public void render(CarEntity car, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, car.prevYaw, car.getYaw())));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.lerp(tickDelta, car.prevRoll, car.roll) * 2.5f));
        float spin = MathHelper.lerp(tickDelta, car.prevWheelRot, car.wheelRot) * 2.2f;
        root.getChild("wheel_f").pitch = spin;
        root.getChild("wheel_r").pitch = spin;
        root.getChild("wheel_f").yaw = MathHelper.lerp(tickDelta, car.prevVisualSteer, car.visualSteer) * 0.5f;
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(BASE));
        int ov = OverlayTexture.DEFAULT_UV;
        root.getChild("deck").render(m, vc, light, ov, 0xFF1E88E5);
        root.getChild("fender").render(m, vc, light, ov, 0xFF1E88E5);
        root.getChild("stem").render(m, vc, light, ov, 0xFFB0BEC5);
        root.getChild("bar").render(m, vc, light, ov, 0xFF263238);
        root.getChild("grips").render(m, vc, light, ov, 0xFF111111);
        root.getChild("seat_post").render(m, vc, light, ov, 0xFF90A4AE);
        root.getChild("seat").render(m, vc, light, ov, 0xFF212121);
        root.getChild("lamp").render(m, vc, LightmapTextureManager.MAX_LIGHT_COORDINATE, ov, 0xFFFFF6C8);
        root.getChild("wheel_f").render(m, vc, light, ov, 0xFF151515);
        root.getChild("wheel_r").render(m, vc, light, ov, 0xFF151515);
        m.pop();
        super.render(car, yaw, tickDelta, m, buffers, light);
    }

    @Override
    public Identifier getTexture(CarEntity entity) {
        return BASE;
    }
}
