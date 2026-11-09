package com.yompix.novograd.client.render;

import com.yompix.novograd.client.render.vehicle.Cubes;
import com.yompix.novograd.entity.TrainEntity;
import com.yompix.novograd.world.Metro;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Поезд метро: четыре вагона по 7 блоков. Белый кузов с синей полосой и красной линией, окна во всю длину
 * (полупрозрачные — изнутри видно город), по две двустворчатые двери с каждой стороны (разъезжаются на станциях),
 * внутри — пол, синие диваны вдоль стен, жёлтые поручни, светящаяся полоса на потолке; на концах — кабины с фарами
 * и табло маршрута. Каждый вагон ставится на путь отдельно (по двум тележкам), поэтому на поворотах состав изгибается.
 */
public class TrainRenderer extends EntityRenderer<TrainEntity> {
    private final ModelPart root;

    public TrainRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        shadowRadius = 0;
        Cubes c = new Cubes();
        // ходовая часть и рама
        c.boxes("bogies", -16, 0, -48, 16, 6, -32, -16, 0, 32, 16, 6, 48);
        c.boxes("wheels", -18, 0.5f, -46, -16, 5.5f, -42, 16, 0.5f, -46, 18, 5.5f, -42, -18, 0.5f, -38, -16, 5.5f, -34, 16, 0.5f, -38, 18, 5.5f, -34,
                -18, 0.5f, 34, -16, 5.5f, 38, 16, 0.5f, 34, 18, 5.5f, 38, -18, 0.5f, 42, -16, 5.5f, 46, 16, 0.5f, 42, 18, 5.5f, 46);
        c.box("frame", -20, 4, -56, 20, 7, 56);
        c.box("floor", -20, 7, -56, 20, 8, 56);
        // борта: низ, полосы, простенки между окнами, верх (двери — отдельно)
        float[] seg = {-56, -36, -20, 20, 36, 56};
        float[] lower = new float[0], upper = new float[0], piers = new float[0], glass = new float[0], blue = new float[0], red = new float[0];
        for (int side = -1; side <= 1; side += 2) {
            float x1 = side < 0 ? -21 : 20, x2 = side < 0 ? -20 : 21, gx1 = side < 0 ? -20.8f : 20.3f, gx2 = side < 0 ? -20.3f : 20.8f;
            for (int i = 0; i < seg.length; i += 2) {
                lower = cat(lower, x1, 8, seg[i], x2, 20, seg[i + 1]);
                blue = cat(blue, side < 0 ? -21.2f : 20.9f, 20, seg[i], side < 0 ? -20.9f : 21.2f, 25, seg[i + 1]);
                red = cat(red, side < 0 ? -21.2f : 20.9f, 25, seg[i], side < 0 ? -20.9f : 21.2f, 26.5f, seg[i + 1]);
                upper = cat(upper, x1, 42, seg[i], x2, 50, seg[i + 1]);
                glass = cat(glass, gx1, 26, seg[i] + 1, gx2, 42, seg[i + 1] - 1);
                piers = cat(piers, x1, 26, seg[i], x2, 42, seg[i] + 1.5f, x1, 26, seg[i + 1] - 1.5f, x2, 42, seg[i + 1]);
                if (seg[i + 1] - seg[i] > 30) piers = cat(piers, x1, 26, -0.75f, x2, 42, 0.75f);
            }
            upper = cat(upper, x1, 44, -36, x2, 50, -20, x1, 44, 20, x2, 50, 36); // над дверями
        }
        c.boxes("lower", lower);
        c.boxes("blue", blue);
        c.boxes("red", red);
        c.boxes("upper", upper);
        c.boxes("piers", piers);
        c.boxes("glass", glass);
        // двери: по две створки, 4 проёма (2 с каждой стороны); каждая створка — своя деталь (двигаем вдоль z)
        for (int side = -1; side <= 1; side += 2) {
            float x1 = side < 0 ? -20.9f : 20.1f, x2 = side < 0 ? -20.1f : 20.9f;
            for (int d = 0; d < 2; d++) {
                float z0 = d == 0 ? -36 : 20;
                String n = "door" + (side < 0 ? "L" : "R") + d;
                c.boxes(n + "a", x1, 8, z0, x2, 44, z0 + 8);
                c.boxes(n + "b", x1, 8, z0 + 8, x2, 44, z0 + 16);
                c.boxes(n + "ga", side < 0 ? -21.1f : 20.6f, 28, z0 + 2, side < 0 ? -20.6f : 21.1f, 40, z0 + 6.5f);
                c.boxes(n + "gb", side < 0 ? -21.1f : 20.6f, 28, z0 + 9.5f, side < 0 ? -20.6f : 21.1f, 40, z0 + 14);
            }
        }
        c.box("roof", -21, 50, -56, 21, 53, 56);
        c.box("roofcap", -18, 53, -54, 18, 54.5f, 54);
        c.boxes("ac", -9, 54.5f, -20, 9, 58, 0, -9, 54.5f, 10, 9, 58, 26);
        // торцы: внутренние (переходы) и кабины на концах состава
        c.box("endA", -21, 8, -56.5f, 21, 50, -56);
        c.box("endB", -21, 8, 56, 21, 50, 56.5f);
        c.box("gangA", -12, 8, -60, 12, 46, -56.5f);
        c.box("gangB", -12, 8, 56.5f, 12, 46, 60);
        c.box("cabA", -21, 8, -60, 21, 50, -56.5f);
        c.box("cabB", -21, 8, 56.5f, 21, 50, 60);
        c.box("windA", -17, 28, -60.6f, 17, 44, -60);
        c.box("windB", -17, 28, 60, 17, 44, 60.6f);
        c.boxes("lampsA", -17, 13, -60.8f, -12, 17, -60, 12, 13, -60.8f, 17, 17, -60);
        c.boxes("lampsB", -17, 13, 60, -12, 17, 60.8f, 12, 13, 60, 17, 17, 60.8f);
        c.box("boardA", -10, 45, -60.8f, 10, 49, -60);
        c.box("boardB", -10, 45, 60, 10, 49, 60.8f);
        // салон
        c.boxes("seats", -20, 8, -18, -14, 13, 18, 14, 8, -18, 20, 13, 18, -20, 8, -54, -14, 13, -38, 14, 8, -54, 20, 13, -38,
                -20, 8, 38, -14, 13, 54, 14, 8, 38, 20, 13, 54);
        c.boxes("backs", -20, 13, -18, -19, 26, 18, 19, 13, -18, 20, 26, 18, -20, 13, -54, -19, 26, -38, 19, 13, -54, 20, 26, -38,
                -20, 13, 38, -19, 26, 54, 19, 13, 38, 20, 26, 54);
        c.boxes("poles", -0.6f, 8, -28.6f, 0.6f, 50, -27.4f, -0.6f, 8, 27.4f, 0.6f, 50, 28.6f, -8.6f, 8, -0.6f, -7.4f, 50, 0.6f, 7.4f, 8, -0.6f, 8.6f, 50, 0.6f,
                -14, 46, -50, -13, 47, 50, 13, 46, -50, 14, 47, 50);
        c.box("ceiling", -20, 49.5f, -56, 20, 50, 56);
        c.box("lightstrip", -5, 49, -52, 5, 49.5f, 52);
        root = c.build();
    }

    private static float[] cat(float[] a, float... b) {
        float[] r = new float[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    @Override
    public void render(TrainEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        double s = e.cp < 0 ? e.progress() : MathHelper.lerp(tickDelta, e.prevCp, e.cp);
        double ex = MathHelper.lerp(tickDelta, e.prevX, e.getX()), ez = MathHelper.lerp(tickDelta, e.prevZ, e.getZ());
        float door = MathHelper.lerp(tickDelta, e.prevDoorOpen, e.doorOpen);
        boolean night = e.getWorld().getTimeOfDay() % 24000 > 12500 && e.getWorld().getTimeOfDay() % 24000 < 23500;
        int id = e.trainId();
        for (int k = 0; k < 4; k++) {
            double[] p = Metro.carPose(id, s, k);
            m.push();
            m.translate(p[0] - ex, 0, p[1] - ez);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) -p[2]));
            int lt = WorldRenderer.getLightmapCoordinates(e.getWorld(), BlockPos.ofFloored(p[0], Metro.D + 2.5, p[1]));
            Cubes.Paint c = new Cubes.Paint(root, m, buffers, lt);
            int white = 0xFFF2F4F7;
            c.p("bogies", 0xFF2A2C31);
            c.p("wheels", 0xFF54575E);
            c.p("frame", 0xFF3A3D44);
            c.p("floor", 0xFF5A5F66);
            c.p("lower", white);
            c.p("blue", 0xFF1E5AA8);
            c.p("red", 0xFFD32F2F);
            c.p("upper", white);
            c.p("piers", white);
            c.p("roof", 0xFFC4C9D0);
            c.p("roofcap", 0xFFB0B5BC);
            c.p("ac", 0xFF8A9099);
            c.p("seats", 0xFF1E5AA8);
            c.p("backs", 0xFF234E8A);
            c.p("poles", 0xFFF2C032);
            c.p("ceiling", 0xFFE6E8EC);
            c.glow("lightstrip", 0xFFFFFBEA);
            // двери: створки разъезжаются
            for (String sd : new String[]{"L", "R"})
                for (int d = 0; d < 2; d++) {
                    String n = "door" + sd + d;
                    float off = door * 7.5f;
                    for (String part : new String[]{"a", "ga"}) root.getChild(n + part).pivotZ = -off;
                    for (String part : new String[]{"b", "gb"}) root.getChild(n + part).pivotZ = off;
                    c.p(n + "a", 0xFF8A96A6);
                    c.p(n + "b", 0xFF8A96A6);
                }
            // торцы: кабины в голове и хвосте состава, между вагонами — переходы
            if (k == 0) {
                c.p("cabA", white);
                c.glass("windA", 0x7A20303C);
                c.glow("lampsA", e.speed() < -0.01 ? 0xFFFFF4D0 : 0xFFE02020);
                c.glow("boardA", 0xFFFF9A1E);
            } else c.p("gangA", 0xFF26282C);
            if (k == 3) {
                c.p("cabB", white);
                c.glass("windB", 0x7A20303C);
                c.glow("lampsB", e.speed() >= -0.01 ? 0xFFFFF4D0 : 0xFFE02020);
                c.glow("boardB", 0xFFFF9A1E);
            } else c.p("gangB", 0xFF26282C);
            c.p("endA", 0xFFE0E3E8);
            c.p("endB", 0xFFE0E3E8);
            c.glass("glass", night ? 0x66FFF2C0 : 0x5A7A9AB8);
            for (String sd : new String[]{"L", "R"})
                for (int d = 0; d < 2; d++) {
                    c.glass("door" + sd + d + "ga", 0x5A7A9AB8);
                    c.glass("door" + sd + d + "gb", 0x5A7A9AB8);
                }
            m.pop();
        }
        super.render(e, yaw, tickDelta, m, buffers, light);
    }

    @Override
    public boolean shouldRender(TrainEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRender(x, y, z);
    }

    @Override
    public Identifier getTexture(TrainEntity entity) {
        return Cubes.BASE;
    }
}
