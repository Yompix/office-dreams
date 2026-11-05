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

/**
 * Машина из кубиков: кузов цвета машины, тонированные стёкла, светящиеся фары и стопы,
 * колёса крутятся от пройденного пути, передние поворачиваются вместе с рулём,
 * кузов кренится в поворотах и приседает при разгоне/торможении.
 */
public class CarRenderer extends EntityRenderer<CarEntity> {
    public static final EntityModelLayer LAYER = new EntityModelLayer(LifeCraft.id("car"), "main");
    private static final Identifier BASE = LifeCraft.id("textures/entity/car/base.png");
    private static final String[] WHEELS = {"wheel_fl", "wheel_fr", "wheel_rl", "wheel_rr"};

    private final ModelPart root;

    public CarRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.root = ctx.getPart(LAYER);
        this.shadowRadius = 1.3f;
    }

    private static ModelPartBuilder box(float x1, float y1, float z1, float x2, float y2, float z2) {
        return ModelPartBuilder.create().uv(0, 0).cuboid(x1, y1, z1, x2 - x1, y2 - y1, z2 - z1);
    }

    /** Размеры в пикселях (16 = блок): длина 54, ширина 30, высота 24. Перед машины смотрит в +z. */
    public static TexturedModelData modelData() {
        ModelData data = new ModelData();
        ModelPartData r = data.getRoot();
        // кузов полый: пол, борта, моторный отсек и багажник — внутри салон с сиденьями
        r.addChild("chassis", box(-14, 3, -26, 14, 7, 26), ModelTransform.NONE);
        r.addChild("body", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-15, 6, -25, 2, 7, 50).cuboid(13, 6, -25, 2, 7, 50)     // борта (двери)
                .cuboid(-13, 6, 9, 26, 7, 16)                                   // моторный отсек
                .cuboid(-13, 6, -25, 26, 7, 6), ModelTransform.NONE);           // багажник
        r.addChild("hood", box(-14, 13, 10, 14, 14, 24), ModelTransform.NONE);
        r.addChild("trunk", box(-14, 13, -24, 14, 14, -19), ModelTransform.NONE);
        r.addChild("pillars", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-13, 13, 8, 2, 16, 2).cuboid(11, 13, 8, 2, 16, 2)       // передние стойки
                .cuboid(-13, 13, -19, 2, 16, 2).cuboid(11, 13, -19, 2, 16, 2)   // задние
                .cuboid(-13, 13, -7, 1, 16, 1.5f).cuboid(12, 13, -7, 1, 16, 1.5f), ModelTransform.NONE);
        r.addChild("roof", box(-13, 29, -19, 13, 31, 10), ModelTransform.NONE);
        r.addChild("headliner", box(-12.5f, 28.6f, -18.5f, 12.5f, 29, 9.5f), ModelTransform.NONE);
        r.addChild("glass", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-12.5f, 13, 9, 25, 16, 0.4f)          // лобовое
                .cuboid(-12.5f, 13, -18.4f, 25, 16, 0.4f)     // заднее
                .cuboid(-13, 14, -17, 0.4f, 15, 25)           // левые окна
                .cuboid(12.6f, 14, -17, 0.4f, 15, 25), ModelTransform.NONE);
        r.addChild("floor", box(-13, 7, -19, 13, 7.6f, 9), ModelTransform.NONE);
        // двери изнутри: обивка, подлокотник, ручка
        r.addChild("trim", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-13, 7.6f, -18, 0.8f, 5.4f, 26).cuboid(12.2f, 7.6f, -18, 0.8f, 5.4f, 26)
                .cuboid(-13, 12, -16, 1.6f, 1, 6).cuboid(11.4f, 12, -16, 1.6f, 1, 6)
                .cuboid(-13, 12, -4, 1.6f, 1, 7).cuboid(11.4f, 12, -4, 1.6f, 1, 7), ModelTransform.NONE);
        r.addChild("handles", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-12.3f, 11, 3.5f, 0.5f, 0.6f, 2).cuboid(11.8f, 11, 3.5f, 0.5f, 0.6f, 2)
                .cuboid(-12.3f, 11, -9, 0.5f, 0.6f, 2).cuboid(11.8f, 11, -9, 0.5f, 0.6f, 2)
                .cuboid(-15.3f, 11.5f, 1, 0.4f, 0.6f, 3).cuboid(14.9f, 11.5f, 1, 0.4f, 0.6f, 3)
                .cuboid(-15.3f, 11.5f, -12, 0.4f, 0.6f, 3).cuboid(14.9f, 11.5f, -12, 0.4f, 0.6f, 3), ModelTransform.NONE);
        r.addChild("dashboard", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-12.5f, 11, 5.5f, 25, 6, 3.5f)        // торпедо
                .cuboid(-12.5f, 16, 3.5f, 25, 1.2f, 2)        // козырёк над приборами
                .cuboid(-2.5f, 7.6f, 3, 5, 5, 3), ModelTransform.NONE);  // центр под магнитолой
        r.addChild("gauges", ModelPartBuilder.create().uv(0, 0)
                .cuboid(7.8f, 13.5f, 5.3f, 2.2f, 2.2f, 0.2f).cuboid(4.8f, 13.5f, 5.3f, 2.2f, 2.2f, 0.2f)   // спидометр и тахометр
                .cuboid(-1.8f, 12.5f, 5.3f, 3.6f, 2.6f, 0.2f), ModelTransform.NONE);                     // экран магнитолы
        // передние ковши: подушка, спинка, подголовник на стойках; сзади — диван на троих
        ModelPartBuilder seats = ModelPartBuilder.create().uv(0, 0);
        for (float x : new float[]{-11, 3}) {
            seats.cuboid(x, 7.6f, -4, 8, 2.6f, 8)              // подушка
                    .cuboid(x - 0.2f, 8, -4, 1, 3.2f, 8).cuboid(x + 7.2f, 8, -4, 1, 3.2f, 8)  // боковая поддержка
                    .cuboid(x, 10.2f, -6, 8, 13, 2)             // спинка
                    .cuboid(x + 1.5f, 24.2f, -5.8f, 5, 3.5f, 1.6f)  // подголовник
                    .cuboid(x + 2.3f, 23.2f, -5.4f, 0.6f, 1, 0.6f).cuboid(x + 5.1f, 23.2f, -5.4f, 0.6f, 1, 0.6f);
        }
        seats.cuboid(-12, 7.6f, -17, 24, 2.6f, 8)                   // задний диван
                .cuboid(-12, 10.2f, -19, 24, 12, 2)
                .cuboid(-10, 22.5f, -18.8f, 5, 3, 1.4f).cuboid(5, 22.5f, -18.8f, 5, 3, 1.4f)
                .cuboid(-2, 7.6f, -3, 4, 3.4f, 6);                    // тоннель с подлокотником
        r.addChild("seats", seats, ModelTransform.NONE);
        r.addChild("seam", ModelPartBuilder.create().uv(0, 0)        // строчка/вставки сидений
                .cuboid(-9, 10.25f, -3.5f, 4, 0.1f, 7).cuboid(5, 10.25f, -3.5f, 4, 0.1f, 7)
                .cuboid(-9, 12, -3.95f, 4, 10, 0.1f).cuboid(5, 12, -3.95f, 4, 10, 0.1f)
                .cuboid(-11, 10.25f, -16.5f, 22, 0.1f, 7), ModelTransform.NONE);
        r.addChild("gear", ModelPartBuilder.create().uv(0, 0).cuboid(-0.3f, 11, 3.2f, 0.6f, 2.4f, 0.6f).cuboid(-0.7f, 13.2f, 2.9f, 1.4f, 1.2f, 1.2f), ModelTransform.NONE);
        r.addChild("mirror_in", box(-2, 26.5f, 7.5f, 2, 28, 8.2f), ModelTransform.NONE);
        ModelPartData wheel = r.addChild("steering", ModelPartBuilder.create().uv(0, 0)
                .cuboid(-3, -0.4f, -0.3f, 6, 0.8f, 0.6f).cuboid(-0.4f, -3, -0.3f, 0.8f, 3, 0.6f)
                .cuboid(-3, -3, -0.3f, 6, 0.8f, 0.6f).cuboid(-3, 2.2f, -0.3f, 6, 0.8f, 0.6f)
                .cuboid(-3, -3, -0.3f, 0.8f, 6, 0.6f).cuboid(2.2f, -3, -0.3f, 0.8f, 6, 0.6f)
                .cuboid(-1, -1, -0.5f, 2, 2, 0.6f), ModelTransform.of(7, 16.5f, 4.2f, -0.5f, 0, 0));
        wheel.addChild("column", box(-0.5f, -0.5f, 0.2f, 0.5f, 0.5f, 3), ModelTransform.NONE);
        r.addChild("bumpers", ModelPartBuilder.create().uv(0, 0).cuboid(-15, 5, 25, 30, 4, 2).cuboid(-15, 5, -27, 30, 4, 2), ModelTransform.NONE);
        r.addChild("grille", box(-6, 8, 25, 6, 12, 25.5f), ModelTransform.NONE);
        r.addChild("plates", ModelPartBuilder.create().uv(0, 0).cuboid(-4, 6, 27, 8, 2, 0.3f).cuboid(-4, 6, -27.3f, 8, 2, 0.3f), ModelTransform.NONE);
        r.addChild("headlights", ModelPartBuilder.create().uv(0, 0).cuboid(-13, 9.5f, 25, 5, 3, 0.6f).cuboid(8, 9.5f, 25, 5, 3, 0.6f), ModelTransform.NONE);
        r.addChild("taillights", ModelPartBuilder.create().uv(0, 0).cuboid(-13, 9.5f, -25.6f, 4, 3, 0.6f).cuboid(9, 9.5f, -25.6f, 4, 3, 0.6f), ModelTransform.NONE);
        r.addChild("mirrors", ModelPartBuilder.create().uv(0, 0).cuboid(-17, 14, 7, 2, 2, 2).cuboid(15, 14, 7, 2, 2, 2), ModelTransform.NONE);
        r.addChild("taxi_sign", box(-5, 31, -6, 5, 34, -1), ModelTransform.NONE);
        float[][] wheels = {{13, 16}, {-13, 16}, {13, -16}, {-13, -16}};
        for (int i = 0; i < 4; i++) {
            ModelPartData w = r.addChild(WHEELS[i], box(-2.5f, -5, -5, 2.5f, 5, 5), ModelTransform.pivot(wheels[i][0], 5, wheels[i][1]));
            w.addChild("rim", box(-2.8f, -2.5f, -2.5f, 2.8f, 2.5f, 2.5f), ModelTransform.NONE);
            w.addChild("spoke", box(-2.9f, -0.6f, -4, 2.9f, 0.6f, 4), ModelTransform.NONE);
        }
        return TexturedModelData.of(data, 256, 256);
    }

    @Override
    public void render(CarEntity car, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        m.push();
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, car.prevYaw, car.getYaw());
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        float roll = MathHelper.lerp(tickDelta, car.prevRoll, car.roll);
        float pitch = MathHelper.lerp(tickDelta, car.prevPitchLean, car.pitchLean);
        float wobble = car.getWobble() > 0 ? MathHelper.sin((car.getWobble() - tickDelta) * 1.3f) * car.getWobble() * 0.4f : 0;
        m.translate(0, 0.02, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll + wobble));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

        float wheelRot = MathHelper.lerp(tickDelta, car.prevWheelRot, car.wheelRot);
        float steer = MathHelper.lerp(tickDelta, car.prevVisualSteer, car.visualSteer) * 0.55f;
        for (int i = 0; i < 4; i++) {
            ModelPart w = root.getChild(WHEELS[i]);
            w.pitch = wheelRot;
            w.yaw = i < 2 ? steer : 0;
        }

        int color = 0xFF000000 | CarEntity.COLORS[car.getVariant()];
        long time = car.getWorld().getTimeOfDay() % 24000;
        boolean night = time > 12300 && time < 23700;
        boolean driven = car.getControllingPassenger() != null || car.hasNpcDriver();
        VertexConsumer solid = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(BASE));
        int ov = OverlayTexture.DEFAULT_UV;
        int full = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        part("chassis", m, solid, light, 0xFF2B2D31);
        part("body", m, solid, light, color);
        part("hood", m, solid, light, LifeShade.shade(color, 0.93f));
        part("trunk", m, solid, light, LifeShade.shade(color, 0.93f));
        part("pillars", m, solid, light, LifeShade.shade(color, 0.85f));
        part("roof", m, solid, light, color);
        part("mirrors", m, solid, light, color);
        part("bumpers", m, solid, light, 0xFF9EA3AA);
        part("grille", m, solid, light, 0xFF141518);
        part("plates", m, solid, light, 0xFFF4F4F4);
        part("dashboard", m, solid, light, 0xFF26282D);
        part("gauges", m, solid, LightmapTextureManager.MAX_LIGHT_COORDINATE, 0xFF6FE3FF);
        int seat = car.isTaxi() ? 0xFF4B3A2A : 0xFF33363D;
        part("seats", m, solid, light, seat);
        part("seam", m, solid, light, LifeShade.shade(seat, 0.75f));
        part("floor", m, solid, light, 0xFF1C1D20);
        part("trim", m, solid, light, car.isTaxi() ? 0xFF5A4A3A : 0xFF3E424A);
        part("handles", m, solid, light, 0xFFB8BDC4);
        part("headliner", m, solid, light, 0xFFD4CFC4);
        part("gear", m, solid, light, 0xFF141414);
        part("mirror_in", m, solid, light, 0xFF202226);
        root.getChild("steering").roll = -steer * 2.2f;
        part("steering", m, solid, light, 0xFF141414);
        for (String w : WHEELS) {
            ModelPart wheel = root.getChild(w);
            m.push();
            wheel.rotate(m);
            wheel.getChild("rim").render(m, solid, light, ov, 0xFFC9CDD3);
            wheel.getChild("spoke").render(m, solid, light, ov, 0xFF8A8F96);
            m.pop();
            wheel.render(m, solid, light, ov, 0xFF1A1A1C);
        }
        // фары светятся всегда, ночью ярче; стопы горят у машины с водителем
        part("headlights", m, solid, night || driven ? full : light, night ? 0xFFFFF6C8 : 0xFFE8E4C8);
        part("taillights", m, solid, driven ? full : light, driven ? 0xFFFF2A2A : 0xFF8A1A1A);
        if (car.isTaxi()) part("taxi_sign", m, solid, full, 0xFFFFE14D);
        VertexConsumer glass = buffers.getBuffer(RenderLayer.getEntityTranslucent(BASE));
        root.getChild("glass").render(m, glass, light, ov, 0x5A6A8AA8);
        if (night && driven) beams(m, buffers);
        m.pop();
        super.render(car, yaw, tickDelta, m, buffers, light);
    }

    private void part(String name, MatrixStack m, VertexConsumer vc, int light, int color) {
        root.getChild(name).render(m, vc, light, OverlayTexture.DEFAULT_UV, color);
    }

    /** Ночью от фар вперёд расходятся полупрозрачные лучи. */
    private static void beams(MatrixStack m, VertexConsumerProvider buffers) {
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getLightning());
        var mat = m.peek().getPositionMatrix();
        for (float side : new float[]{-0.66f, 0.66f}) {
            float y = 11f / 16f, z = 25.6f / 16f;
            float len = 7f, spread = 1.4f;
            vc.vertex(mat, side - 0.12f, y + 0.08f, z).color(255, 250, 210, 70);
            vc.vertex(mat, side + 0.12f, y + 0.08f, z).color(255, 250, 210, 70);
            vc.vertex(mat, side + spread, y - 0.6f, z + len).color(255, 250, 210, 0);
            vc.vertex(mat, side - spread, y - 0.6f, z + len).color(255, 250, 210, 0);
        }
    }

    @Override
    public Identifier getTexture(CarEntity entity) {
        return BASE;
    }
}
