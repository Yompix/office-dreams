package com.yompix.novograd.client.style;

import com.yompix.novograd.client.NovogradConfig;
import com.yompix.novograd.life.Appearance;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 0.9.7: форма груди — отдельная деталь на передней стороне корпуса (у игрока и у жителей). Всегда «под одеждой»:
 * деталь берёт пиксели того же собранного скина ({@link SkinComposer}), что и корпус (нижний слой — верх, купальник,
 * платье; верхний — куртка, в т.ч. нараспашку), поэтому цвет и узор вещи продолжаются на ней.
 *
 * <p>Женский персонаж — клин (скат сверху, перед, низ, бока) пяти размеров: от небольшого до умеренно большого,
 * без крайностей. Мужской — две плоские пластины рельефа при спортивном телосложении. Верхний слой — на 0,25 px
 * больше со всех сторон, кроме задней кромки (как слои скина Minecraft): плоскости нигде не совпадают — нет мерцания.
 *
 * <p>«Физика тела»: на женскую форму — пружина с затуханием (чуть меньше критического) от ускорения тела по
 * вертикали и вперёд (прыжок, приземление, старт/остановка) и от шага; смещение сдвигом «петли» у верхней кромки
 * (верх прикреплён к корпусу, щелей нет), ограничено {@link #CAP} px × сила 50–100 %. Считается на клиенте при
 * отрисовке, по сущности, без пакетов. Пространство — как у корпуса ванильной модели: шея y = 0, пояс y = 12, перед −z.
 */
public final class BodyShape {
    private BodyShape() {}

    // ---------------------------------------------------------------- размеры (px модели)
    /** Женская форма по размеру 0…4: глубина, низ, полуширина. Верх — {@link #F_TOP}. */
    static final float[] F_DEPTH = {0.55f, 0.8f, 1.05f, 1.3f, 1.55f};
    static final float[] F_BOTTOM = {4.2f, 4.35f, 4.5f, 4.65f, 4.8f};
    static final float[] F_HALF = {2.75f, 2.9f, 3.05f, 3.2f, 3.35f};
    static final float F_TOP = 1.7f;
    /** Мужской рельеф: 0 — нет, 1, 2. */
    static final float[] M_DEPTH = {0f, 0.45f, 0.65f};
    static final float M_TOP = 1.1f, M_BOTTOM = 3.5f, M_X0 = 0.12f, M_X1 = 3.7f;
    /** Предел смещения физики, px (при силе 100 %). */
    public static final float CAP = 0.4f;
    static final float LAYER = 0.25f;

    /** Размер, который видно на этом клиенте: без «взрослого контента» (или с выключенным «Размером груди») — средний. */
    public static int chestShown(Appearance a) {
        return NovogradConfig.chestSizes() ? MathHelper.clamp(a.chest, 0, Appearance.CHEST_STEPS - 1) : Appearance.MID_CHEST;
    }

    /** Глубина формы (px) — насколько вынести вперёд висящее на груди. */
    public static float depth(Appearance a) {
        if (a.slim) return F_DEPTH[chestShown(a)] + LAYER;
        return a.build == 2 && a.pecs > 0 ? M_DEPTH[MathHelper.clamp(a.pecs, 0, 2)] + LAYER : 0;
    }

    // ---------------------------------------------------------------- физика
    static final class Spring {
        float y, v;
        double last;
        long seen;
        int age = Integer.MIN_VALUE;
        double vy, vf;
    }

    private static final Int2ObjectOpenHashMap<Spring> SPRINGS = new Int2ObjectOpenHashMap<>();
    private static long sweep;
    /** Частота ~3 Гц, затухание 0,6 от критического (чуть меньше — одно мягкое покачивание и покой). */
    static final float W = 2 * MathHelper.PI * 3.0f, ZETA = 0.6f;
    /** Замер: сколько раз считали и сколько ушло (нс) — физика и отрисовка детали. */
    public static long physNanos, physCount, drawNanos, drawCount, bufNanos;

    /** Ключ пружины для витрины редактора (своя, не та же, что у героя в мире). */
    public static final int PREVIEW_KEY = Integer.MIN_VALUE + 7;

    /**
     * Шаг пружины для сущности (раз за кадр отрисовки). Возвращает смещение, px (уже ограничено и умножено на силу);
     * 0 — физика выключена.
     */
    public static float physics(int key, Entity e, float limbAngle, float limbDistance, int size, double nowSec) {
        if (!NovogradConfig.bodyPhysics()) return 0;
        long t0 = System.nanoTime();
        Spring s = SPRINGS.get(key);
        if (s == null) {
            s = new Spring();
            s.last = nowSec;
            SPRINGS.put(key, s);
        }
        // время — игровое (тики + доля тика): в замедленной записи ролика и на паузе пружина идёт с игрой
        float dt = (float) Math.min(0.05, Math.max(0, nowSec - s.last));
        s.last = nowSec;
        s.seen = t0;
        // раз в тик: ускорение тела (блоков/тик²) → толчок скорости (px/с). Вверх ускоряется тело — форма отстаёт вниз.
        if (e != null && e.age != s.age) {
            double vy = e.getY() - e.prevY, dx = e.getX() - e.prevX, dz = e.getZ() - e.prevZ;
            double vf = Math.sqrt(dx * dx + dz * dz);
            if (s.age != Integer.MIN_VALUE && e.age - s.age == 1) {
                double ay = MathHelper.clamp(vy - s.vy, -0.8, 0.8), af = MathHelper.clamp(vf - s.vf, -0.4, 0.4);
                s.v += (float) (ay * 24 + Math.abs(af) * 7);
            }
            s.vy = vy;
            s.vf = vf;
            s.age = e.age;
        }
        // шаг: два толчка за цикл ходьбы (только на земле)
        boolean ground = e == null || e.isOnGround();
        float f = ground ? MathHelper.sin(limbAngle * 0.6662f * 2) * Math.min(1f, limbDistance) * 55f : 0;
        float k = 0.75f + size * 0.1f; // меньше размер — меньше движение
        for (float left = dt; left > 1e-5f; ) {
            float h = Math.min(left, 1 / 120f);
            float acc = -W * W * s.y - 2 * ZETA * W * s.v + f * k;
            s.v = MathHelper.clamp(s.v + acc * h, -14f, 14f);
            s.y = MathHelper.clamp(s.y + s.v * h, -1.2f, 1.2f);
            left -= h;
        }
        float cap = CAP * NovogradConfig.bodyPhysicsK();
        float out = MathHelper.clamp(s.y * NovogradConfig.bodyPhysicsK(), -cap, cap);
        // старые пружины (сущность ушла из вида) — убрать
        if (t0 - sweep > 2_000_000_000L) {
            sweep = t0;
            long now = t0;
            SPRINGS.int2ObjectEntrySet().removeIf(en -> now - en.getValue().seen > 3_000_000_000L);
        }
        physNanos += System.nanoTime() - t0;
        physCount++;
        return out;
    }

    public static int springs() {
        return SPRINGS.size();
    }

    // ---------------------------------------------------------------- отрисовка
    /**
     * Нарисовать форму в пространстве корпуса (m уже повернута как корпус). tex — собранный скин этого человека.
     * key — ключ пружины (id сущности или {@link #PREVIEW_KEY}).
     */
    public static void render(MatrixStack m, VertexConsumerProvider buffers, Identifier tex, int light, int overlay, Appearance a, int key, Entity e,
                              float limbAngle, float limbDistance, float tickDelta) {
        if (a == null || tex == null) return;
        boolean female = a.slim;
        int pecs = MathHelper.clamp(a.pecs, 0, 2);
        if (!female && (a.build != 2 || pecs == 0)) return;
        long t0 = System.nanoTime();
        // тот же слой, что у самой модели (игрок и житель — entityTranslucent): без лишней смены пачки отрисовки
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityTranslucent(tex));
        bufNanos += System.nanoTime() - t0;
        Matrix4f pos = m.peek().getPositionMatrix();
        Matrix3f nrm = m.peek().getNormalMatrix();
        if (female) {
            int size = chestShown(a);
            double now = key == PREVIEW_KEY || e == null ? System.nanoTime() / 1e9 : (e.age + tickDelta) / 20.0;
            float dy = physics(key, e, limbAngle, limbDistance, size, now);
            float d = F_DEPTH[size], b = F_BOTTOM[size], hw = F_HALF[size];
            for (int layer = 0; layer < 2; layer++) {
                float g = layer * LAYER;
                wedge(vc, pos, nrm, light, overlay, -hw - g, hw + g, F_TOP - g, b + g, d + g, d * 0.8f, dy, layer == 0 ? 20 : 36);
            }
        } else {
            float d = M_DEPTH[pecs];
            for (int layer = 0; layer < 2; layer++) {
                float g = layer * LAYER;
                wedge(vc, pos, nrm, light, overlay, -M_X1 - g, -M_X0 + g * 0.4f, M_TOP - g, M_BOTTOM + g, d + g, d, 0, layer == 0 ? 20 : 36);
                wedge(vc, pos, nrm, light, overlay, M_X0 - g * 0.4f, M_X1 + g, M_TOP - g, M_BOTTOM + g, d + g, d, 0, layer == 0 ? 20 : 36);
            }
        }
        drawNanos += System.nanoTime() - t0;
        drawCount++;
    }

    private static final Vector3f N = new Vector3f();

    /**
     * Клин на переднем крае корпуса (z = −2): x0..x1, сверху y0 (кромка прилегает к корпусу), скат до y0 + slope,
     * перед на глубине depth, низ y1. Текстура — перед корпуса (u 20..28, v = vRow + y). dy — сдвиг всего, кроме
     * верхней кромки (физика).
     */
    private static void wedge(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, int light, int ov, float x0, float x1, float y0, float y1, float depth, float slope,
                              float dy, int vRow) {
        float zb = -2f, zf = -2f - depth, ys = Math.min(y1 - 0.4f, y0 + Math.max(slope, 0.3f));
        // текстура: скат — верхняя часть области, перед — остальное (рисунок вещи идёт непрерывно)
        float vTop = vRow + Math.max(0, y0), vMid = vRow + Math.max(0, y0) + (y1 - y0) * 0.3f, vBot = vRow + Math.min(12, y1);
        float u0 = 24 + x0, u1 = 24 + x1;
        // нормаль ската: вверх-вперёд
        float sy = ys - y0, sl = (float) Math.sqrt(sy * sy + depth * depth);
        // скат
        quad(vc, pos, nrm, light, ov, 0, -depth / sl, -sy / sl,
                x0, y0, zb, 0, x1, y0, zb, 0, x1, ys, zf, dy, x0, ys, zf, dy,
                u0, vTop, u1, vTop, u1, vMid, u0, vMid, 1f);
        // перед
        quad(vc, pos, nrm, light, ov, 0, 0, -1,
                x0, ys, zf, dy, x1, ys, zf, dy, x1, y1, zf, dy, x0, y1, zf, dy,
                u0, vMid, u1, vMid, u1, vBot, u0, vBot, 1f);
        // низ (в тени)
        quad(vc, pos, nrm, light, ov, 0, 1, 0,
                x0, y1, zf, dy, x1, y1, zf, dy, x1, y1, zb, dy, x0, y1, zb, dy,
                u0, vBot - 0.5f, u1, vBot - 0.5f, u1, vBot, u0, vBot, 0.78f);
        // бока: крайние столбцы ткани
        float ue0 = u0, ue1 = u0 + 0.5f, uf0 = u1 - 0.5f, uf1 = u1;
        quad(vc, pos, nrm, light, ov, -1, 0, 0,
                x0, y0, zb, 0, x0, ys, zf, dy, x0, y1, zf, dy, x0, y1, zb, dy,
                ue0, vTop, ue1, vMid, ue1, vBot, ue0, vBot, 0.9f);
        quad(vc, pos, nrm, light, ov, 1, 0, 0,
                x1, y0, zb, 0, x1, y1, zb, dy, x1, y1, zf, dy, x1, ys, zf, dy,
                uf1, vTop, uf1, vBot, uf0, vBot, uf0, vMid, 0.9f);
    }

    private static void quad(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, int light, int ov, float nx, float ny, float nz,
                             float ax, float ay, float az, float ad, float bx, float by, float bz, float bd,
                             float cx, float cy, float cz, float cd, float dx, float dy, float dz, float dd,
                             float au, float av, float bu, float bv, float cu, float cv, float du, float dv, float shade) {
        N.set(nx, ny, nz);
        nrm.transform(N);
        N.normalize();
        int c = 0xFF000000 | ((int) (255 * shade) * 0x010101);
        v(vc, pos, ax, ay + ad, az, au, av, c, ov, light);
        v(vc, pos, bx, by + bd, bz, bu, bv, c, ov, light);
        v(vc, pos, cx, cy + cd, cz, cu, cv, c, ov, light);
        v(vc, pos, dx, dy + dd, dz, du, dv, c, ov, light);
    }

    private static void v(VertexConsumer vc, Matrix4f pos, float x, float y, float z, float u, float v, int color, int ov, int light) {
        Vector3f p = pos.transformPosition(x / 16f, y / 16f, z / 16f, new Vector3f());
        vc.vertex(p.x, p.y, p.z, color, u / 64f, v / 64f, ov, light, N.x, N.y, N.z);
    }

    public static int defaultOverlay() {
        return OverlayTexture.DEFAULT_UV;
    }
}
