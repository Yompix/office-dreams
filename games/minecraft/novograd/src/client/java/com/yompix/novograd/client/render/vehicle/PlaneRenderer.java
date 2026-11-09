package com.yompix.novograd.client.render.vehicle;

import com.yompix.novograd.vehicle.PlaneEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Лёгкий самолёт-высокоплан: фюзеляж с капотом и винтом, кабина-фонарь, крыло на подкосах (9 блоков),
 * хвостовое оперение, шасси с колёсами. Нос поднимается/опускается по тангажу, в повороте — крен.
 */
public class PlaneRenderer extends EntityRenderer<PlaneEntity> {
    private final ModelPart root;

    public PlaneRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 1.8f;
        Cubes c = new Cubes();
        c.boxes("fuselage", -7, 6, -40, 7, 19, 20, -5.5f, 8, -64, 5.5f, 18, -40, -3.5f, 10, -86, 3.5f, 17, -64, -2, 12, -100, 2, 16, -86);
        c.box("cowl", -6.5f, 7, 20, 6.5f, 18, 32);
        c.box("spinner", -2.2f, 10.3f, 32, 2.2f, 14.7f, 36);
        c.pivot("prop", 0, 12.5f, 36.5f, -1.2f, -24, -0.5f, 1.2f, 24, 0.5f);
        c.boxes("stripe", -7.2f, 12, -40, -7, 14, 32, 7, 12, -40, 7.2f, 14, 32, -5.7f, 12.5f, -64, -5.5f, 14, -40, 5.5f, 12.5f, -64, 5.7f, 14, -40);
        c.box("canopy", -6, 19, -14, 6, 30, 16);
        c.boxes("canopy_frame", -6.3f, 19, 15.4f, -5.2f, 30, 16.4f, 5.2f, 19, 15.4f, 6.3f, 30, 16.4f, -6.3f, 19, -14.5f, 6.3f, 30, -13.5f, -6.3f, 19, 0, -5.6f, 30, 1, 5.6f, 19, 0, 6.3f, 30, 1);
        c.box("wing", -72, 30, -6, 72, 32.5f, 14);
        c.boxes("flaps", -60, 30.2f, -9, -10, 32, -6, 10, 30.2f, -9, 60, 32, -6);
        c.boxes("struts", -32, 12, 2, -30, 30, 4, 30, 12, 2, 32, 30, 4, -30, 12, 2, -7, 13, 4, 7, 12, 2, 30, 13, 4);
        c.box("stab", -26, 14, -100, 26, 15.5f, -88);
        c.box("fin", -0.9f, 16, -104, 0.9f, 38, -88);
        c.box("rudder", -1, 18, -106, 1, 36, -104);
        c.boxes("gear", -16, 2, -2, -14, 12, 1, 14, 2, -2, 16, 12, 1, -1, 3, 23, 1, 8, 25);
        c.boxes("wheels", -18, 0, -4, -14, 5, 3, 14, 0, -4, 18, 5, 3, -1.5f, 0, 21.5f, 1.5f, 4, 26.5f);
        c.boxes("seats", -4.5f, 6, 0, 4.5f, 12.8f, 8, -4.5f, 12.8f, -1.5f, 4.5f, 22, 0, -4.5f, 6, -14, 4.5f, 12.8f, -6, -4.5f, 12.8f, -15.5f, 4.5f, 22, -14);
        c.box("panel", -6, 17, 15, 6, 21, 18);
        c.box("gauges", -5, 18.5f, 14.8f, 5, 20.5f, 15.1f);
        c.box("nav_l", -73, 30.4f, 0, -72, 32.2f, 6);
        c.box("nav_r", 72, 30.4f, 0, 73, 32.2f, 6);
        c.box("strobe", -1, 38, -98, 1, 39.5f, -95);
        c.box("landing_light", -1.5f, 7.5f, 31.8f, 1.5f, 9, 32.4f);
        root = c.build();
    }

    @Override
    public void render(PlaneEntity pl, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, pl.prevYaw, pl.getYaw())));
        float pitch = MathHelper.lerp(tickDelta, pl.prevPitch, pl.getPitch());
        float bank = MathHelper.lerp(tickDelta, pl.prevBank, pl.bank);
        m.translate(0, 1.0, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        if (!pl.isOnGround()) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(bank * 6f));
        m.translate(0, -1.0, 0);
        root.getChild("prop").roll = MathHelper.lerp(tickDelta, pl.prevProp, pl.prop) * MathHelper.RADIANS_PER_DEGREE;
        int color = 0xFF000000 | pl.bodyColor();
        if (pl.wrecked()) color = Cubes.shade(color, 0.25f);
        boolean white = (color & 0xFFFFFF) == 0xF2F4F7;
        int accent = white ? 0xFFD7262C : 0xFFF2F4F7;
        Cubes.Paint p = new Cubes.Paint(root, m, buffers, light);
        p.p("fuselage", color);
        p.p("cowl", Cubes.shade(color, 0.85f));
        p.p("spinner", accent);
        p.p("prop", 0xFF1C1D20);
        p.p("stripe", accent);
        p.p("canopy_frame", Cubes.shade(color, 0.8f));
        p.p("wing", color);
        p.p("flaps", Cubes.shade(color, 0.85f));
        p.p("struts", 0xFF9EA3AA);
        p.p("stab", color);
        p.p("fin", color);
        p.p("rudder", accent);
        p.p("gear", 0xFF3A3D42);
        p.p("wheels", 0xFF141414);
        p.p("seats", 0xFF4B3A2A);
        p.p("panel", 0xFF1E2024);
        p.glass("canopy", 0x506A8AA8);
        boolean on = pl.engineRunning();
        long t = pl.getWorld().getTime();
        if (on) p.glow("gauges", 0xFF6FE3FF);
        else p.p("gauges", 0xFF203038);
        p.glow("nav_l", on ? 0xFFFF2020 : 0xFF7A1414);
        p.glow("nav_r", on ? 0xFF20FF40 : 0xFF147A20);
        if (on && t % 24 < 3) p.glow("strobe", 0xFFFFFFFF);
        else p.p("strobe", 0xFFB0B0B0);
        if (on) p.glow("landing_light", 0xFFFFF6C8);
        else p.p("landing_light", 0xFFD8D4C0);
        m.pop();
        super.render(pl, yaw, tickDelta, m, buffers, light);
    }

    @Override
    public Identifier getTexture(PlaneEntity entity) {
        return Cubes.BASE;
    }

    @Override
    public boolean shouldRender(PlaneEntity entity, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z) && frustum.isVisible(entity.getBoundingBox().expand(5, 1, 5));
    }
}
