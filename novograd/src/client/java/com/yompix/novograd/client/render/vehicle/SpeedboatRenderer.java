package com.yompix.novograd.client.render.vehicle;

import com.yompix.novograd.vehicle.SpeedboatEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Катер: острый нос, белая палуба, цветной борт с полосой, лобовое стекло, кресла, диван на корме, подвесной мотор с винтом.
 * На скорости задирает нос, в поворотах кренится, на волне покачивается.
 */
public class SpeedboatRenderer extends EntityRenderer<SpeedboatEntity> {
    private final ModelPart root;

    public SpeedboatRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 0;
        Cubes c = new Cubes();
        c.boxes("bottom", -11, 0, -32, 11, 4.5f, 20, -9, 1, 20, 9, 4.5f, 28, -6, 2.5f, 28, 6, 4.5f, 34, -2.5f, 3.5f, 34, 2.5f, 4.5f, 38);
        c.boxes("hull", -14, 4.5f, -34, -12, 12, 22, 12, 4.5f, -34, 14, 12, 22, -14, 4.5f, -34, 14, 12, -32,
                -12.5f, 4.5f, 22, -9.5f, 12, 28, 9.5f, 4.5f, 22, 12.5f, 12, 28,
                -9.5f, 5, 28, -6, 12, 33, 6, 5, 28, 9.5f, 12, 33,
                -6, 6, 33, -2.5f, 12, 37, 2.5f, 6, 33, 6, 12, 37, -2.5f, 7, 37, 2.5f, 12, 39.5f);
        c.boxes("stripe", -14.2f, 8, -34, -14, 9.4f, 22, 14, 8, -34, 14.2f, 9.4f, 22, -12.7f, 8, 22, -12.5f, 9.4f, 28, 12.5f, 8, 22, 12.7f, 9.4f, 28);
        c.boxes("deck", -12, 11.5f, 7, 12, 12.6f, 22, -9.5f, 11.5f, 22, 9.5f, 12.6f, 33, -6, 11.5f, 33, 6, 12.6f, 37, -2.5f, 11.5f, 37, 2.5f, 12.6f, 39.5f,
                -12, 11.5f, -34, 12, 12.6f, -27);
        c.box("floor", -12, 4.5f, -27, 12, 5.5f, 7);
        c.boxes("gunwale", -14, 12, -27, -12, 13, 7, 12, 12, -27, 14, 13, 7);
        c.boxes("rail", -12.6f, 12.6f, 10, -11.8f, 14.4f, 30, 11.8f, 12.6f, 10, 12.6f, 14.4f, 30, -12.6f, 13.6f, 30, 12.6f, 14.4f, 30.8f);
        c.box("screen", -12, 12.6f, 6, 12, 19, 7.2f);
        c.box("screen_frame", -12.2f, 18.8f, 5.8f, 12.2f, 19.6f, 7.4f);
        c.box("dash", -11, 11, 2.5f, 11, 15, 6.2f);
        c.box("gauges", -9, 13.2f, 2.3f, -3, 14.6f, 2.6f);
        c.box("wheel", -7.2f, 13, 1, -3.8f, 16.4f, 1.8f);
        c.boxes("seats", -10.5f, 5.5f, -4, -1.5f, 9, 4.5f, 1.5f, 5.5f, -4, 10.5f, 9, 4.5f,
                -10.5f, 9, -5.5f, -1.5f, 17, -3.5f, 1.5f, 9, -5.5f, 10.5f, 17, -3.5f,
                -12, 5.5f, -24, 12, 8.6f, -14, -12, 8.6f, -27, 12, 15, -24);
        c.box("motor_cover", -4, 9, -42, 4, 19, -34.5f);
        c.box("motor_leg", -1.6f, -4, -40.5f, 1.6f, 9, -37.5f);
        c.box("motor_fin", -0.6f, -5, -42, 0.6f, -2, -36);
        c.pivot("prop", 0, -2.5f, -41, -3.5f, -0.7f, -0.4f, 3.5f, 0.7f, 0.4f, -0.7f, -3.5f, -0.4f, 0.7f, 3.5f, 0.4f);
        c.boxes("nav_l", -13, 12.6f, 24, -11, 13.6f, 26);
        c.boxes("nav_r", 11, 12.6f, 24, 13, 13.6f, 26);
        c.box("cleats", -1, 12.6f, 34, 1, 13.4f, 36);
        root = c.build();
    }

    @Override
    public void render(SpeedboatEntity b, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, b.prevYaw, b.getYaw())));
        float speed = MathHelper.lerp(tickDelta, b.prevTilt, b.tilt);
        float bank = MathHelper.lerp(tickDelta, b.prevBank, b.bank);
        float t = b.age + tickDelta + b.getId() * 7;
        float bob = MathHelper.sin(t * 0.11f) * 1.6f * (1 - Math.min(Math.abs(speed), 1) * 0.6f);
        m.translate(0, 0.4, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-MathHelper.clamp(speed, -0.3f, 1.3f) * 7f + MathHelper.sin(t * 0.07f) * 1.2f));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(bank * 2.2f + bob));
        m.translate(0, -0.4, 0);
        int color = 0xFF000000 | b.hullColor();
        if (b.wrecked()) color = Cubes.shade(color, 0.25f);
        boolean white = (color & 0xFFFFFF) == 0xF2F4F7;
        root.getChild("prop").roll = (b.age + tickDelta) * (b.engineRunning() ? 1.2f + Math.abs(speed) * 2 : 0);
        Cubes.Paint p = new Cubes.Paint(root, m, buffers, light);
        p.p("bottom", 0xFF2A2F36);
        p.p("hull", color);
        p.p("stripe", white ? 0xFF1E5AA8 : 0xFFF2F4F7);
        p.p("deck", 0xFFF0EEE8);
        p.p("floor", 0xFFB89A6A);
        p.p("gunwale", 0xFFE4E2DC);
        p.p("rail", 0xFFC9CDD3);
        p.glass("screen", 0x60708CA0);
        p.p("screen_frame", 0xFF2A2C30);
        p.p("dash", 0xFF26282D);
        p.glow("gauges", 0xFF6FE3FF);
        p.p("wheel", 0xFF141414);
        p.p("seats", 0xFFE9E4D8);
        p.p("motor_cover", 0xFF15171A);
        p.p("motor_leg", 0xFF2F3237);
        p.p("motor_fin", 0xFF2F3237);
        p.p("prop", 0xFFB0B5BC);
        p.p("cleats", 0xFFC9CDD3);
        boolean on = b.engineRunning();
        if (on) {
            p.glow("nav_l", 0xFFFF2020);
            p.glow("nav_r", 0xFF20FF40);
        } else {
            p.p("nav_l", 0xFF7A1414);
            p.p("nav_r", 0xFF147A20);
        }
        m.pop();
        super.render(b, yaw, tickDelta, m, buffers, light);
    }

    @Override
    public Identifier getTexture(SpeedboatEntity entity) {
        return Cubes.BASE;
    }
}
