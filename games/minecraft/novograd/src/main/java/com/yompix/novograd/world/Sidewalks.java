package com.yompix.novograd.world;

import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Пешеходные пути по тротуарам острова. Город — сетка улиц (линии ≡ 24 mod 48), вокруг каждого квартала —
 * «кольцо» тротуара в 5 блоках от осевой (у магистралей — по обочине, в 8). Прохожий идёт по кольцу своего
 * квартала, на углу переходит улицу по «зебре» (7–8 блоков от перекрёстка) и дальше — по кольцу соседнего.
 * Точки пути отдаются горожанину ({@link CitizenEntity#walkRoute}), между точками — обычная навигация (дёшево:
 * короткие отрезки). Вне города (пляж, порт, старый центр) — прямо, кусками по ~24 блока.
 */
public final class Sidewalks {
    private Sidewalks() {}

    /** Край тротуара перед «зеброй»: тут прохожий смотрит, не едет ли машина. */
    public static final class Kerb extends Vec3d {
        public Kerb(double x, double y, double z) {
            super(x, y, z);
        }
    }

    private static int line(double t) {
        return (int) (Math.round((t - 24) / 48.0) * 48 + 24);
    }

    private static int cell(double t) {
        return Math.floorDiv((int) Math.floor(t) - 24, 48);
    }

    /** Отступ полосы тротуара от осевой линии улицы: 5 — обычная улица, 8 — магистраль (обочина за отбойником). */
    private static int off(boolean alongZ, int lineC, double mid) {
        int m = (int) Math.floor(mid);
        boolean highway = alongZ ? Island.road(lineC + 6, m) && Island.road(lineC - 6, m) : Island.road(m, lineC + 6) && Island.road(m, lineC - 6);
        return highway ? 8 : 5;
    }

    /** Кольцо тротуара квартала (i, j): xW, xE, zN, zS (середины блоков полосы). */
    private static double[] ring(int i, int j) {
        int x0 = 24 + 48 * i, x1 = x0 + 48, z0 = 24 + 48 * j, z1 = z0 + 48;
        double mz = z0 + 24, mx = x0 + 24;
        return new double[]{x0 + off(true, x0, mz) + 0.5, x1 - off(true, x1, mz) + 0.5, z0 + off(false, z0, mx) + 0.5, z1 - off(false, z1, mx) + 0.5};
    }

    /** Ближайшая точка кольца. */
    private static double[] project(double[] r, double x, double z) {
        double cx = Math.max(r[0], Math.min(r[1], x)), cz = Math.max(r[2], Math.min(r[3], z));
        double dw = cx - r[0], de = r[1] - cx, dn = cz - r[2], ds = r[3] - cz;
        double m = Math.min(Math.min(dw, de), Math.min(dn, ds));
        if (m == dw) cx = r[0];
        else if (m == de) cx = r[1];
        else if (m == dn) cz = r[2];
        else cz = r[3];
        return new double[]{cx, cz};
    }

    /** Положение точки на периметре кольца (0..P), обход: север (W→E), восток (N→S), юг (E→W), запад (S→N). */
    private static double param(double[] r, double x, double z) {
        double w = r[1] - r[0], h = r[3] - r[2];
        if (Math.abs(z - r[2]) < 0.01) return x - r[0];
        if (Math.abs(x - r[1]) < 0.01) return w + (z - r[2]);
        if (Math.abs(z - r[3]) < 0.01) return w + h + (r[1] - x);
        return 2 * w + h + (r[3] - z);
    }

    /** Углы кольца (срезаны на блок внутрь — на самом углу стоит светофор). */
    private static double[][] corners(double[] r) {
        return new double[][]{{r[0] + 1, r[2] + 1}, {r[1] - 1, r[2] + 1}, {r[1] - 1, r[3] - 1}, {r[0] + 1, r[3] - 1}};
    }

    /** По кольцу от a до b — короче из двух направлений; добавляет углы и саму b. */
    private static void alongRing(List<Vec3d> out, double[] r, double ax, double az, double bx, double bz) {
        double w = r[1] - r[0], h = r[3] - r[2], P = 2 * (w + h);
        double[] cornerP = {0, w, w + h, 2 * w + h};
        double pa = param(r, ax, az), pb = param(r, bx, bz);
        double fwd = ((pb - pa) % P + P) % P;
        boolean cw = fwd <= P - fwd;
        double[][] cs = corners(r);
        double dist = cw ? fwd : P - fwd;
        // углы, которые проходим по пути
        List<double[]> passed = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            double d = cw ? ((cornerP[k] - pa) % P + P) % P : ((pa - cornerP[k]) % P + P) % P;
            if (d > 0.5 && d < dist - 0.5) passed.add(new double[]{d, k});
        }
        passed.sort((u, v) -> Double.compare(u[0], v[0]));
        double px = ax, pz = az;
        for (double[] c : passed) {
            double[] cc = cs[(int) c[1]];
            split(out, px, pz, cc[0], cc[1]);
            px = cc[0];
            pz = cc[1];
        }
        split(out, px, pz, bx, bz);
    }

    /** Добавить точку b, а длинный отрезок порезать кусками по ~20 блоков. */
    private static void split(List<Vec3d> out, double ax, double az, double bx, double bz) {
        double d = Math.hypot(bx - ax, bz - az);
        int n = (int) Math.ceil(d / 20.0);
        for (int k = 1; k <= n; k++) {
            double x = ax + (bx - ax) * k / n, z = az + (bz - az) * k / n;
            out.add(at(x, z));
        }
    }

    private static Vec3d at(double x, double z) {
        return new Vec3d(x, Island.surface((int) Math.floor(x), (int) Math.floor(z)) + 1, z);
    }

    /**
     * Путь пешехода от from до to. Последняя точка — сама цель (дверь, скамейка). Пусто — уже на месте.
     * Длинные пути (> 220 блоков) не строим — ведущий код сам решает «уехать за кадром».
     */
    public static List<Vec3d> route(Vec3d from, Vec3d to) {
        List<Vec3d> out = new ArrayList<>();
        if (from.squaredDistanceTo(to) < 4) return out;
        Island.Zone za = Island.zone((int) from.x, (int) from.z), zb = Island.zone((int) to.x, (int) to.z);
        if (!Island.urban(za) || !Island.urban(zb) || Math.abs(from.x - to.x) + Math.abs(from.z - to.z) > 300) {
            split(out, from.x, from.z, to.x, to.z);
            out.set(out.size() - 1, to);
            return out;
        }
        int ci = cell(from.x), cj = cell(from.z), ti = cell(to.x), tj = cell(to.z);
        double[] r = ring(ci, cj);
        double[] cur = project(r, from.x, from.z);
        out.add(at(cur[0], cur[1]));
        // сначала поперёк улиц по x, потом по z; переход — по «зебре» у того угла, что ближе к цели
        while (ci != ti || cj != tj) {
            boolean stepX = ci != ti;
            int step = stepX ? Integer.signum(ti - ci) : Integer.signum(tj - cj);
            double[] a, b;
            int ni = ci + (stepX ? step : 0), nj = cj + (stepX ? 0 : step);
            double[] r2 = ring(ni, nj);
            if (stepX) {
                int X = 24 + 48 * (ci + (step > 0 ? 1 : 0));
                int z0 = 24 + 48 * cj, z1 = z0 + 48;
                boolean north = Math.abs(to.z - z0) < Math.abs(to.z - z1);
                double zc = north ? z0 + 8.0 : z1 - 7.0;
                if (Island.zone(X, (int) zc) == Island.Zone.SEA) zc = north ? z1 - 7.0 : z0 + 8.0;
                zc = Math.max(Math.max(r[2], r2[2]), Math.min(Math.min(r[3], r2[3]), zc));
                a = new double[]{step > 0 ? r[1] : r[0], zc};
                b = new double[]{step > 0 ? r2[0] : r2[1], zc};
            } else {
                int Z = 24 + 48 * (cj + (step > 0 ? 1 : 0));
                int x0 = 24 + 48 * ci, x1 = x0 + 48;
                boolean west = Math.abs(to.x - x0) < Math.abs(to.x - x1);
                double xc = west ? x0 + 8.0 : x1 - 7.0;
                xc = Math.max(Math.max(r[0], r2[0]), Math.min(Math.min(r[1], r2[1]), xc));
                a = new double[]{xc, step > 0 ? r[3] : r[2]};
                b = new double[]{xc, step > 0 ? r2[2] : r2[3]};
            }
            alongRing(out, r, cur[0], cur[1], a[0], a[1]);
            Vec3d last = out.get(out.size() - 1);
            out.set(out.size() - 1, new Kerb(last.x, last.y, last.z)); // перед переходом — посмотреть на дорогу
            out.add(at(b[0], b[1]));
            cur = b;
            r = r2;
            ci = ni;
            cj = nj;
        }
        double[] end = project(r, to.x, to.z);
        alongRing(out, r, cur[0], cur[1], end[0], end[1]);
        if (Math.hypot(end[0] - to.x, end[1] - to.z) > 1) split(out, end[0], end[1], to.x, to.z);
        out.set(out.size() - 1, to);
        return out;
    }

    /** Ближайшая точка тротуара (полоса у бордюра) к точке; вне города — сама точка. */
    public static Vec3d nearestSidewalk(Vec3d p) {
        if (!Island.urban(Island.zone((int) p.x, (int) p.z))) return p;
        double[] r = ring(cell(p.x), cell(p.z));
        double[] c = project(r, p.x, p.z);
        return at(c[0], c[1]);
    }

    // ------------------------------------------------------------------ «за кадром»
    /** Никто из игроков не видит точку: дальше 48 блоков или (дальше 10 и) за спиной. */
    public static boolean unseen(ServerWorld w, Vec3d at) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator()) continue;
            double d2 = p.squaredDistanceTo(at);
            if (d2 > 48 * 48) continue;
            if (d2 < 10 * 10) return false;
            Vec3d look = p.getRotationVec(1f), to = at.subtract(p.getEyePos()).normalize();
            if (look.dotProduct(to) > 0.25) return false; // в поле зрения (≈ 150°)
        }
        return true;
    }

    public static boolean unseen(Entity e) {
        return e.getWorld() instanceof ServerWorld w && unseen(w, e.getPos().add(0, 1, 0));
    }

    /** Игрок рядом (ближе r) — для «думает только рядом с игроком». */
    public static boolean playerNear(ServerWorld w, Vec3d at, double r) {
        for (ServerPlayerEntity p : w.getPlayers()) if (!p.isSpectator() && p.squaredDistanceTo(at) < r * r) return true;
        return false;
    }
}
