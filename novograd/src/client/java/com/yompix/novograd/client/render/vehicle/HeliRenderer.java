package com.yompix.novograd.client.render.vehicle;

import com.yompix.novograd.vehicle.HeliEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Вертолёт: кабина со стеклянным носом, четыре кресла и приборная панель, двигатель и мачта сверху,
 * несущий винт (10 блоков) и рулевой винт крутятся по оборотам, хвостовая балка с килем, полозья.
 * На ходу клюёт носом, в развороте кренится. Полиция: бело-синяя, мигалки и прожектор.
 */
public class HeliRenderer extends EntityRenderer<HeliEntity> {
    private final ModelPart root;

    public HeliRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 1.6f;
        Cubes c = new Cubes();
        c.box("floor", -11, 4, -15, 11, 6, 17);
        c.boxes("body", -12, 6, -15, -11, 15, 13, 11, 6, -15, 12, 15, 13,       // нижние борта
                -12, 6, -17, 12, 31, -15,                                     // задняя стенка
                -10, 4, 17, 10, 14, 25,                                       // нос
                -12, 15, -15, -11, 31, -13, 11, 15, -15, 12, 31, -13);        // задние стойки
        c.box("roof", -12, 31, -17, 12, 34, 13);
        c.boxes("pillars", -11.8f, 15, 11, -10.6f, 31, 13, 10.6f, 15, 11, 11.8f, 31, 13, -11.8f, 15, -1, -11, 31, 0.5f, 11, 15, -1, 11.8f, 31, 0.5f);
        c.boxes("glass", -11.8f, 15, -13, -11.4f, 31, 11, 11.4f, 15, -13, 11.8f, 31, 11,   // боковые окна
                -10, 14, 13, 10, 30, 14, -9.5f, 14, 14, 9.5f, 24, 22, -9, 24, 14, 9, 29, 18); // нос: лоб и скулы
        c.boxes("stripe", -12.2f, 10, -15, -12, 12, 25, 12, 10, -15, 12.2f, 12, 25);
        c.box("engine", -7, 34, -18, 7, 39, 6);
        c.boxes("intakes", -7.4f, 35, 0, -7, 38, 4, 7, 35, 0, 7.4f, 38, 4);
        c.box("exhaust", -3, 35, -20, 3, 38, -18);
        c.box("mast", -1.5f, 39, -2.5f, 1.5f, 43, 0.5f);
        c.pivot("rotor", 0, 43, -1,
                -80, 0, -2.4f, -2, 0.8f, 2.4f, 2, 0, -2.4f, 80, 0.8f, 2.4f,
                -2.4f, 0.1f, -80, 2.4f, 0.9f, -2, -2.4f, 0.1f, 2, 2.4f, 0.9f, 80,
                -3, -0.5f, -3, 3, 1.5f, 3);
        c.boxes("boom", -3.5f, 18, -62, 3.5f, 25, -17, -2.2f, 19.5f, -80, 2.2f, 24, -62);
        c.box("fin", -0.8f, 22, -84, 0.8f, 40, -74);
        c.box("stab", -13, 20, -70, 13, 21, -64);
        c.pivot("tail_rotor", 1.8f, 33, -79, 0, -10, -1.6f, 0.6f, 10, 1.6f, 0, -1.6f, -10, 0.6f, 1.6f, 10);
        c.boxes("skids", -14, 0, -18, -12, 1.6f, 20, 12, 0, -18, 14, 1.6f, 20, -14, 0.6f, 20, -12, 2.6f, 22, 12, 0.6f, 20, 14, 2.6f, 22,
                -13.5f, 1.6f, -10, -12, 4.5f, -8, 12, 1.6f, -10, 13.5f, 4.5f, -8, -13.5f, 1.6f, 9, -12, 4.5f, 11, 12, 1.6f, 9, 13.5f, 4.5f, 11);
        c.boxes("seats", -10, 6, 4, -2, 12.5f, 13, 2, 6, 4, 10, 12.5f, 13, -10, 12.5f, 2, -2, 23, 4, 2, 12.5f, 2, 10, 23, 4,
                -10, 6, -12, -2, 12.5f, -3, 2, 6, -12, 10, 12.5f, -3, -10, 12.5f, -14, -2, 23, -12, 2, 12.5f, -14, 10, 23, -12);
        c.box("panel", -9.5f, 12, 14, 9.5f, 17, 17);
        c.box("gauges", -8, 14, 13.8f, 8, 16, 14.1f);
        c.boxes("sticks", -6.5f, 6, 15, -5.5f, 13, 16, 5.5f, 6, 15, 6.5f, 13, 16);
        c.box("beacon", -1, 40, -83, 1, 41.5f, -81);
        c.box("nav_l", -13.5f, 20, -69, -12.5f, 21.5f, -67);
        c.box("nav_r", 12.5f, 20, -69, 13.5f, 21.5f, -67);
        c.box("lightbar_r", -6, 2.6f, 4, -1, 4, 8);
        c.box("lightbar_b", 1, 2.6f, 4, 6, 4, 8);
        c.box("spot", -2, 2.4f, 18, 2, 4.2f, 22);
        root = c.build();
    }

    @Override
    public void render(HeliEntity h, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, h.prevYaw, h.getYaw())));
        float speed = MathHelper.lerp(tickDelta, h.prevTilt, h.tilt);
        float bank = MathHelper.lerp(tickDelta, h.prevBank, h.bank);
        m.translate(0, 1.2, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.clamp(speed * 14f, -10, 18)));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(bank * 3f));
        m.translate(0, -1.2, 0);
        float rot = MathHelper.lerp(tickDelta, h.prevRotorAngle, h.rotorAngle);
        root.getChild("rotor").yaw = rot * MathHelper.RADIANS_PER_DEGREE;
        root.getChild("tail_rotor").pitch = rot * 1.7f * MathHelper.RADIANS_PER_DEGREE;
        boolean police = h.isPolice();
        int color = police ? 0xFFF2F4F7 : 0xFF000000 | h.bodyColor();
        if (h.wrecked()) color = Cubes.shade(color, 0.25f);
        int accent = police ? 0xFF1E4FB8 : Cubes.shade(color, 0.7f);
        Cubes.Paint p = new Cubes.Paint(root, m, buffers, light);
        p.p("floor", 0xFF2A2C31);
        p.p("body", color);
        p.p("roof", color);
        p.p("pillars", Cubes.shade(color, 0.85f));
        p.p("stripe", accent);
        p.p("engine", Cubes.shade(color, 0.9f));
        p.p("intakes", 0xFF141518);
        p.p("exhaust", 0xFF3A3D42);
        p.p("mast", 0xFF5A5F66);
        p.p("rotor", 0xFF1C1D20);
        p.p("boom", color);
        p.p("fin", police ? accent : color);
        p.p("stab", Cubes.shade(color, 0.85f));
        p.p("tail_rotor", 0xFF1C1D20);
        p.p("skids", 0xFF3A3D42);
        p.p("seats", 0xFF2E3138);
        p.p("panel", 0xFF1E2024);
        p.p("sticks", 0xFF141414);
        p.glass("glass", 0x5A6A8AA8);
        boolean on = h.engineRunning();
        long t = h.getWorld().getTime();
        if (on) p.glow("gauges", 0xFF6FE3FF);
        else p.p("gauges", 0xFF203038);
        if (on && t % 20 < 4) p.glow("beacon", 0xFFFF2020);
        else p.p("beacon", 0xFF7A1414);
        p.glow("nav_l", on ? 0xFFFF2020 : 0xFF7A1414);
        p.glow("nav_r", on ? 0xFF20FF40 : 0xFF147A20);
        if (police) {
            boolean a = (t / 4) % 2 == 0;
            if (a) p.glow("lightbar_r", 0xFFFF2020);
            else p.p("lightbar_r", 0xFF5A1010);
            if (!a) p.glow("lightbar_b", 0xFF2060FF);
            else p.p("lightbar_b", 0xFF10205A);
            p.glow("spot", 0xFFFFF6C8);
        }
        m.pop();
        super.render(h, yaw, tickDelta, m, buffers, light);
    }

    @Override
    public Identifier getTexture(HeliEntity entity) {
        return Cubes.BASE;
    }

    @Override
    public boolean shouldRender(HeliEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
        // винт шире хитбокса — не прячем вертолёт, когда сам корпус чуть за краем экрана
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().expand(5, 1, 5));
    }
}
