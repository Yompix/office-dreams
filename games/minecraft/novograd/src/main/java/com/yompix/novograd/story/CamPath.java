package com.yompix.novograd.story;

import net.minecraft.block.ShapeContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;

/**
 * Путь камеры кат-сцены без проходов сквозь дома (общий код: клиент ставит камеру, сервер-автотест проверяет все сцены).
 *
 * <p>Пролёт «из точки А в точку Б» идёт дугой, как кран или дрон. Перед планом путь проверяется по блокам: 48 точек и
 * отрезки между ними. Если дуга задевает дом, пробуем другую сторону дуги, прямую и подъём над крышами — берём первый
 * вариант, где камера ни разу не в стене и не пересекает стену. Если чистого нет — тот, где задето меньше всего.
 */
public final class CamPath {
    private CamPath() {}

    public static final int SAMPLES = 48;
    /** Варианты: {знак дуги (0 — по умолчанию, 1 — обратная, 2 — прямая), подъём в блоках}. */
    private static final double[][] VARIANTS = {{0, 0}, {1, 0}, {2, 0}, {0, 2}, {1, 2}, {2, 2}, {2, 4}, {0, 4}, {2, 7}, {2, 10}, {2, 14}, {2, 20},
            {3, 0}, {5, 0.5}, {5, 0.75}, {5, 0.88}, {4, 0}};
    /** Вариант «кран»: подъём над крышами у А, пролёт над городом, спуск у Б (высота — по карте высот на линии). */
    public static final int CRANE = 12;
    /** Последний вариант: камера стоит у Б и только поворачивается (Б всегда в воздухе) — путь сквозь дома невозможен. */
    public static final int HOLD = 16;
    /** Высота «крана» для текущего плана (ставит plan, читает at). */
    private static final ThreadLocal<Double> CRANE_Y = ThreadLocal.withInitial(() -> 0.0);

    /** Плавный разгон и торможение (smootherstep), как у операторского крана. */
    public static double ease(double k) {
        k = MathHelper.clamp(k, 0, 1);
        return k * k * k * (k * (k * 6 - 15) + 10);
    }

    /**
     * Точка пролёта при доле k (уже сглаженной): прямая от from к to + дуга в сторону + горб вверх + подъём lift
     * (подъём плавно нарастает и спадает к концам, чтобы стартовая и конечная точки не менялись, если они чистые).
     */
    public static Vec3d at(double[] f, double[] to, double k, int shotIdx, int variant) {
        return at(f, to, k, shotIdx, variant, CRANE_Y.get());
    }

    public static Vec3d at(double[] f, double[] to, double k, int shotIdx, int variant, double craneY) {
        double[] v = VARIANTS[MathHelper.clamp(variant, 0, VARIANTS.length - 1)];
        if (v[0] == 4) return new Vec3d(to[0], to[1], to[2]);
        if (v[0] == 5) { // «короткий наезд»: только конец пути (от доли v[1] до Б) по прямой — первая часть шла сквозь дом
            double kk = MathHelper.lerp(k, v[1], 1);
            return new Vec3d(MathHelper.lerp(kk, f[0], to[0]), MathHelper.lerp(kk, f[1], to[1]), MathHelper.lerp(kk, f[2], to[2]));
        }
        if (v[0] == 3) {
            double hy = Math.max(craneY, Math.max(f[1], to[1]));
            Vec3d a = new Vec3d(f[0], f[1], f[2]), b = new Vec3d(to[0], to[1], to[2]);
            return rail(k, a, new Vec3d(f[0], hy, f[2]), new Vec3d(to[0], hy, to[2]), b);
        }
        double x = MathHelper.lerp(k, f[0], to[0]), y = MathHelper.lerp(k, f[1], to[1]), z = MathHelper.lerp(k, f[2], to[2]);
        double mx = to[0] - f[0], mz = to[2] - f[2], dist = Math.sqrt(mx * mx + mz * mz);
        if (dist > 2) {
            double sign = shotIdx % 2 == 0 ? 1 : -1;
            if (v[0] == 1) sign = -sign;
            if (v[0] == 2) sign = 0;
            double arc = Math.min(6, dist * 0.09) * Math.sin(Math.PI * k) * sign;
            x += -mz / dist * arc;
            z += mx / dist * arc;
            y += Math.min(3, dist * 0.03) * Math.sin(Math.PI * k);
        }
        // подъём: концы пути поднимаем, только если они сами в стене (endLift), середину — по варианту
        y += v[1] * Math.sin(Math.PI * k);
        return new Vec3d(x, y, z);
    }

    /** Сплайн Катмулла — Рома по точкам (k 0…1 по всему пути). */
    public static Vec3d rail(double u, Vec3d... p) {
        u = MathHelper.clamp(u, 0, 1) * (p.length - 1);
        int i = Math.min(p.length - 2, (int) Math.floor(u));
        double f = u - i;
        Vec3d p0 = p[Math.max(0, i - 1)], p1 = p[i], p2 = p[i + 1], p3 = p[Math.min(p.length - 1, i + 2)];
        double f2 = f * f, f3 = f2 * f;
        return p1.multiply(2).add(p2.subtract(p0).multiply(f)).add(p0.multiply(2).subtract(p1.multiply(5)).add(p2.multiply(4)).subtract(p3).multiply(f2))
                .add(p3.subtract(p0).add(p1.multiply(3)).subtract(p2.multiply(3)).multiply(f3)).multiply(0.5);
    }

    /** Высота над крышами на линии А→Б (карта высот + 4 блока). */
    public static double craneHeight(net.minecraft.world.World w, double[] f, double[] to) {
        double top = Math.max(f[1], to[1]);
        for (int i = 0; i <= 32; i++) {
            double k = i / 32.0;
            int x = MathHelper.floor(MathHelper.lerp(k, f[0], to[0])), z = MathHelper.floor(MathHelper.lerp(k, f[2], to[2]));
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    top = Math.max(top, w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, x + dx, z + dz) + 4);
        }
        return top;
    }

    /** Камера (её «голова» 0.2 блока) не задевает твёрдый блок. */
    public static boolean clear(BlockView w, Vec3d p) {
        return clear(w, p, 0.2);
    }

    public static boolean clear(BlockView w, Vec3d p, double r) {
        Box b = new Box(p.x - r, p.y - r, p.z - r, p.x + r, p.y + r, p.z + r);
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = MathHelper.floor(b.minX); x <= MathHelper.floor(b.maxX); x++)
            for (int y = MathHelper.floor(b.minY); y <= MathHelper.floor(b.maxY); y++)
                for (int z = MathHelper.floor(b.minZ); z <= MathHelper.floor(b.maxZ); z++) {
                    m.set(x, y, z);
                    var st = w.getBlockState(m);
                    if (st.isAir()) continue;
                    var sh = st.getCollisionShape(w, m);
                    if (sh.isEmpty()) continue;
                    for (Box sb : sh.getBoundingBoxes()) if (sb.offset(x, y, z).intersects(b)) return false;
                }
        return true;
    }

    /** Отрезок a→b не пересекает твёрдые блоки. */
    public static boolean free(BlockView w, Vec3d a, Vec3d b) {
        if (a.squaredDistanceTo(b) < 1e-6) return true;
        return w.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent())).getType() == HitResult.Type.MISS;
    }

    /** Луч a→b: где упёрся (или null). */
    public static Vec3d hit(BlockView w, Vec3d a, Vec3d b) {
        if (a.squaredDistanceTo(b) < 1e-6) return null;
        var h = w.raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
        return h.getType() == HitResult.Type.MISS ? null : h.getPos();
    }

    /** Сколько точек/отрезков пути задевают стены (0 — путь чистый). */
    public static int blocked(BlockView w, double[] f, double[] to, int shotIdx, int variant) {
        return blocked(w, f, to, shotIdx, variant, CRANE_Y.get());
    }

    public static int blocked(BlockView w, double[] f, double[] to, int shotIdx, int variant, double craneY) {
        int bad = 0;
        Vec3d prev = null;
        for (int i = 0; i <= SAMPLES; i++) {
            Vec3d p = at(f, to, i / (double) SAMPLES, shotIdx, variant, craneY);
            if (!clear(w, p)) bad++;
            else if (prev != null && !free(w, prev, p)) bad++;
            prev = p;
        }
        return bad;
    }

    /** Выбрать вариант пути: {вариант, сколько задето, высота крана}. */
    public static int[] plan(net.minecraft.world.World w, double[] f, double[] to, int shotIdx) {
        int best = 0, bestBad = Integer.MAX_VALUE;
        if (to == null) to = f;
        boolean still = Math.abs(f[0] - to[0]) + Math.abs(f[1] - to[1]) + Math.abs(f[2] - to[2]) < 0.05;
        double cy = craneHeight(w, f, to);
        for (int v = 0; v < VARIANTS.length; v++) {
            int bad = blocked(w, f, to, shotIdx, v, cy);
            if (bad < bestBad) {
                best = v;
                bestBad = bad;
            }
            if (bad == 0 || still) break;
        }
        return new int[]{best, bestBad, (int) Math.ceil(cy)};
    }

    public static int variants() {
        return VARIANTS.length;
    }

    /** Поднять точку из стены в воздух (до 24 блоков) — для концов пути, которые сами оказались в доме. */
    public static double[] lift(BlockView w, double[] p) {
        double[] r = p.clone();
        for (int i = 0; i < 24 && !clear(w, new Vec3d(r[0], r[1], r[2]), 0.3); i++) r[1] += 1;
        return r;
    }
}
