package com.yompix.novograd.client.render.vehicle;

import com.yompix.novograd.entity.CarEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Спортивный мотоцикл: обтекатель с фарой, бак цвета байка, сиденье, вилка с рулём, маятник, глушитель.
 * Колёса крутятся от пути, переднее — с рулём; байк ложится в поворотах, на резком разгоне встаёт на заднее колесо.
 */
public class BikeRenderer extends EntityRenderer<CarEntity> {
    private final ModelPart root;

    public BikeRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 0.5f;
        Cubes c = new Cubes();
        float[] wheel = {-1.6f, -5, -3.4f, 1.6f, 5, 3.4f, -1.6f, -3.4f, -5, 1.6f, 3.4f, 5, -1.6f, -4.3f, -4.3f, 1.6f, 4.3f, 4.3f};
        float[] rim = {-1.7f, -2.6f, -2.6f, 1.7f, 2.6f, 2.6f};
        c.pivot("wheel_f", 0, 5, 13, wheel).pivot("rim_f", 0, 5, 13, rim);
        c.pivot("wheel_r", 0, 5, -12, wheel).pivot("rim_r", 0, 5, -12, rim);
        // вилка и руль поворачиваются вокруг рулевой колонки
        c.pivot("fork", 0, 16, 10.5f,
                -2.6f, -11, -0.8f, -1.4f, 2, 0.8f, 1.4f, -11, -0.8f, 2.6f, 2, 0.8f,     // перья вилки
                -7, 3.2f, -1.6f, 7, 4.2f, -0.6f,                                      // руль
                -8, 2.8f, -1.9f, -6, 4.6f, -0.3f, 6, 2.8f, -1.9f, 8, 4.6f, -0.3f);   // ручки
        c.boxes("frame", -1.5f, 7, -9, 1.5f, 10, 9, -1.2f, 10, 6, 1.2f, 16, 9);
        c.box("swingarm", -2.2f, 4.2f, -12.5f, 2.2f, 6, -3);
        c.boxes("engine", -3.2f, 4, -4, 3.2f, 10.5f, 5, -3.6f, 5, 0, 3.6f, 8, 3);
        c.box("tank", -3.6f, 11, 0, 3.6f, 15.5f, 8.5f);
        c.box("tank_top", -2.8f, 15.5f, 1, 2.8f, 16.3f, 7.5f);
        c.box("seat", -3, 12.5f, -10, 3, 14.6f, 0.5f);
        c.box("seat_p", -2.6f, 14.6f, -14.5f, 2.6f, 15.8f, -9.5f);
        c.boxes("tail", -2.6f, 11.5f, -16, 2.6f, 14.6f, -9, -3.4f, 8, -6, 3.4f, 12.5f, 0);
        c.box("fairing", -4.2f, 13, 9.5f, 4.2f, 19.5f, 13.5f);
        c.box("fairing_side", -4.4f, 8, 5, 4.4f, 13.5f, 11);
        c.box("screen", -3.4f, 19.5f, 10, 3.4f, 23.5f, 11.2f);
        c.box("headlight", -2.2f, 14.5f, 13.4f, 2.2f, 17.5f, 14.4f);
        c.box("taillight", -1.6f, 12, -16.4f, 1.6f, 13.6f, -15.8f);
        c.boxes("signals", -3.6f, 15.8f, 12.6f, -2.6f, 16.8f, 13.8f, 2.6f, 15.8f, 12.6f, 3.6f, 16.8f, 13.8f);
        c.boxes("exhaust", 2.6f, 5, -15, 4.6f, 7.2f, -3, 3, 7.2f, -15.4f, 4.2f, 9, -12);
        c.boxes("fenders", -2, 10.5f, 9.5f, 2, 11.3f, 16.5f, -2, 10.2f, -18, 2, 11, -14);
        c.boxes("pegs", -5, 6, -2, -3, 6.8f, 0, 3, 6, -2, 5, 6.8f, 0, -5, 7, -9, -3, 7.8f, -7, 3, 7, -9, 5, 7.8f, -7);
        c.box("plate", -1.8f, 9.5f, -18.4f, 1.8f, 11.5f, -18);
        root = c.build();
    }

    @Override
    public void render(CarEntity bike, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, bike.prevYaw, bike.getYaw())));
        float roll = MathHelper.lerp(tickDelta, bike.prevRoll, bike.roll);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll * 9f));
        // вилли: разгон с места поднимает переднее колесо
        float accel = -MathHelper.lerp(tickDelta, bike.prevPitchLean, bike.pitchLean);
        float wheelie = Math.abs(bike.clientSpeed) < 0.75f ? MathHelper.clamp((accel - 0.35f) * 30f, 0, 22) : 0;
        if (wheelie > 0) {
            m.translate(0, 5 / 16f, -12 / 16f);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-wheelie));
            m.translate(0, -5 / 16f, 12 / 16f);
        }
        float spin = MathHelper.lerp(tickDelta, bike.prevWheelRot, bike.wheelRot) * 2;
        float steer = MathHelper.lerp(tickDelta, bike.prevVisualSteer, bike.visualSteer) * 0.45f;
        for (String w : new String[]{"wheel_f", "rim_f", "wheel_r", "rim_r"}) root.getChild(w).pitch = spin;
        root.getChild("wheel_f").yaw = steer;
        root.getChild("rim_f").yaw = steer;
        root.getChild("fork").yaw = steer;
        int color = 0xFF000000 | bike.color();
        if (bike.wrecked()) color = Cubes.shade(color, 0.25f);
        Cubes.Paint p = new Cubes.Paint(root, m, buffers, light);
        p.p("wheel_f", 0xFF141414);
        p.p("wheel_r", 0xFF141414);
        p.p("rim_f", 0xFFB9BEC6);
        p.p("rim_r", 0xFFB9BEC6);
        p.p("fork", 0xFF2A2C30);
        p.p("frame", 0xFF33363C);
        p.p("swingarm", 0xFF505560);
        p.p("engine", 0xFF6B7078);
        p.p("tank", color);
        p.p("tank_top", Cubes.shade(color, 0.85f));
        p.p("fairing", color);
        p.p("fairing_side", Cubes.shade(color, 0.9f));
        p.p("tail", color);
        p.p("seat", 0xFF1A1A1C);
        p.p("seat_p", 0xFF222225);
        p.p("exhaust", 0xFFC9CDD3);
        p.p("fenders", 0xFF1E1F22);
        p.p("pegs", 0xFF2A2A2A);
        p.p("plate", 0xFFF2F2F2);
        p.glass("screen", 0x705A7088);
        boolean driven = bike.getControllingPassenger() != null;
        boolean head = driven || bike.light(CarEntity.HEADLIGHTS);
        if (head) p.glow("headlight", 0xFFFFF6C8);
        else p.p("headlight", 0xFFD8D4C0);
        if (bike.brakeGlow > 0.1f) p.glow("taillight", 0xFFFF1A1A);
        else p.p("taillight", driven ? 0xFFC42020 : 0xFF6A1414);
        boolean blink = (bike.getWorld().getTime() / 8) % 2 == 0;
        if (blink && (bike.light(CarEntity.LEFT) || bike.light(CarEntity.RIGHT) || bike.light(CarEntity.HAZARD))) p.glow("signals", 0xFFFFA000);
        else p.p("signals", 0xFFB86A00);
        m.pop();
        super.render(bike, yaw, tickDelta, m, buffers, light);
    }

    @Override
    public Identifier getTexture(CarEntity entity) {
        return Cubes.BASE;
    }
}
