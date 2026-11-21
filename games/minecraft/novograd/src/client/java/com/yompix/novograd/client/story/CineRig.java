package com.yompix.novograd.client.story;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.8: «свободная» кинокамера — чистая математика без мира.
 *
 * <p>План камеры — заранее посчитанная кривая, которая вычисляется в каждом кадре по времени сцены (доля тика
 * включена): позиция — центростремительный сплайн Катмулла — Рома с перепараметризацией по длине дуги (ровная скорость),
 * разгон и торможение — трапеция скорости со сглаженными углами (ускорение непрерывно, рывок ограничен); направление
 * взгляда — сплайн по единичным векторам (без «перескока» курса через ±180°, как у углов Эйлера); FOV — тот же сплайн.
 * Никаких пружин, догонялок, проверок стен и поправок «на ходу»: камера может спокойно пролететь сквозь блоки.
 *
 * <p>Единственное, что «живое», — планы за машиной/героем (погоня): опорная точка — сглаженная траектория цели
 * (локальная взвешенная регрессия по клиентским тикам с ядром (1−x²)³, новое значение входит с нулевым весом —
 * кривая гладкая во времени), смещение и курс — от сглаженного курса цели.
 */
public final class CineRig {
    private CineRig() {}

    /** Доля разгона/торможения в плане. */
    public static final double RAMP = 0.22;

    /**
     * Профиль движения 0…1 → 0…1: разгон (скорость растёт по smoothstep), ровная середина, торможение.
     * Позиция C², скорость C¹ — без скачков ускорения.
     */
    public static double ease(double u, double a) {
        u = MathHelper.clamp(u, 0, 1);
        a = MathHelper.clamp(a, 1e-3, 0.5);
        double total = 1 - a;
        double v;
        if (u < a) {
            double x = u / a;
            v = a * (x * x * x - x * x * x * x / 2);
        } else if (u <= 1 - a) {
            v = a / 2 + (u - a);
        } else {
            double x = (1 - u) / a;
            v = total - a * (x * x * x - x * x * x * x / 2);
        }
        return v / total;
    }

    // ===================================================================== кривая по точкам (позиция)
    /** Центростремительный Катмулл — Ром через точки, с таблицей длины дуги. */
    public static final class Curve {
        final Vec3d[] p;
        final double[] knot;   // параметр сплайна в каждой точке
        final double[] arcS;   // длина дуги в образцах
        final double[] arcT;   // глобальный параметр (индекс сегмента + доля) в образцах
        public final double length;

        public Curve(List<Vec3d> pts) {
            List<Vec3d> q = new ArrayList<>();
            for (Vec3d v : pts) if (q.isEmpty() || q.get(q.size() - 1).squaredDistanceTo(v) > 1e-8) q.add(v);
            if (q.isEmpty()) q.add(Vec3d.ZERO);
            if (q.size() == 1) q.add(q.get(0));
            p = q.toArray(new Vec3d[0]);
            knot = new double[p.length];
            for (int i = 1; i < p.length; i++) knot[i] = knot[i - 1] + Math.max(1e-4, Math.sqrt(p[i].distanceTo(p[i - 1])));
            int per = 24, n = (p.length - 1) * per;
            arcS = new double[n + 1];
            arcT = new double[n + 1];
            Vec3d prev = p[0];
            for (int i = 1; i <= n; i++) {
                double t = i / (double) per;
                Vec3d v = atParam(t);
                arcS[i] = arcS[i - 1] + v.distanceTo(prev);
                arcT[i] = t;
                prev = v;
            }
            length = arcS[n];
        }

        /** Точка при глобальном параметре t (0 … точек−1). */
        public Vec3d atParam(double t) {
            int segs = p.length - 1;
            t = MathHelper.clamp(t, 0, segs);
            int i = Math.min(segs - 1, (int) Math.floor(t));
            double f = t - i;
            Vec3d p1 = p[i], p2 = p[i + 1];
            // фантомные концы — отражение, чтобы касательная на концах была вдоль первого/последнего отрезка
            Vec3d p0 = i > 0 ? p[i - 1] : p1.multiply(2).subtract(p2);
            Vec3d p3 = i + 2 < p.length ? p[i + 2] : p2.multiply(2).subtract(p1);
            double t1 = knotAt(i), t2 = knotAt(i + 1);
            double t0 = i > 0 ? knotAt(i - 1) : t1 - (t2 - t1), t3 = i + 2 < p.length ? knotAt(i + 2) : t2 + (t2 - t1);
            double tt = MathHelper.lerp(f, t1, t2);
            Vec3d a1 = lerp(p0, p1, t0, t1, tt), a2 = lerp(p1, p2, t1, t2, tt), a3 = lerp(p2, p3, t2, t3, tt);
            Vec3d b1 = lerp(a1, a2, t0, t2, tt), b2 = lerp(a2, a3, t1, t3, tt);
            return lerp(b1, b2, t1, t2, tt);
        }

        private double knotAt(int i) {
            return knot[i];
        }

        private static Vec3d lerp(Vec3d a, Vec3d b, double ta, double tb, double t) {
            double d = tb - ta;
            if (Math.abs(d) < 1e-9) return a;
            double k = (t - ta) / d;
            return a.multiply(1 - k).add(b.multiply(k));
        }

        /** Глобальный параметр при доле длины дуги s (0…1). */
        public double paramAtArc(double s) {
            if (length < 1e-6) return MathHelper.clamp(s, 0, 1) * (p.length - 1);
            double want = MathHelper.clamp(s, 0, 1) * length;
            int lo = 0, hi = arcS.length - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (arcS[mid] < want) lo = mid;
                else hi = mid;
            }
            double seg = arcS[hi] - arcS[lo];
            double k = seg < 1e-9 ? 0 : (want - arcS[lo]) / seg;
            return MathHelper.lerp(k, arcT[lo], arcT[hi]);
        }

        public int points() {
            return p.length;
        }
    }

    // ===================================================================== скалярный сплайн (FOV, крен, курс как вектор)
    /** Катмулл — Ром по значениям в узлах (равномерный параметр). */
    public static double spline(double[] v, double t) {
        int n = v.length;
        if (n == 1) return v[0];
        t = MathHelper.clamp(t, 0, n - 1);
        int i = Math.min(n - 2, (int) Math.floor(t));
        double f = t - i;
        double p1 = v[i], p2 = v[i + 1], p0 = i > 0 ? v[i - 1] : 2 * p1 - p2, p3 = i + 2 < n ? v[i + 2] : 2 * p2 - p1;
        double f2 = f * f, f3 = f2 * f;
        return 0.5 * (2 * p1 + (p2 - p0) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f2 + (3 * p1 - p0 - 3 * p2 + p3) * f3);
    }

    /** Направление взгляда по курсу/наклону Minecraft. */
    public static Vec3d dir(double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3d(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }

    public static float yawOf(Vec3d d) {
        return (float) Math.toDegrees(Math.atan2(-d.x, d.z));
    }

    public static float pitchOf(Vec3d d) {
        return (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
    }

    /** Сплайн по единичным векторам (покомпонентно + нормировка): гладкий поворот без перескоков углов. */
    public static Vec3d dirSpline(Vec3d[] d, double t) {
        double[] x = new double[d.length], y = new double[d.length], z = new double[d.length];
        for (int i = 0; i < d.length; i++) {
            x[i] = d[i].x;
            y[i] = d[i].y;
            z[i] = d[i].z;
        }
        Vec3d r = new Vec3d(spline(x, t), spline(y, t), spline(z, t));
        double l = r.length();
        return l < 1e-6 ? d[Math.min(d.length - 1, (int) Math.round(MathHelper.clamp(t, 0, d.length - 1)))] : r.multiply(1 / l);
    }

    /** Сферическая интерполяция направлений. */
    public static Vec3d slerp(Vec3d a, Vec3d b, double k) {
        double dot = MathHelper.clamp(a.dotProduct(b), -1, 1);
        double th = Math.acos(dot);
        if (th < 1e-5) return a;
        double s = Math.sin(th);
        if (s < 1e-5) return k < 0.5 ? a : b;
        return a.multiply(Math.sin((1 - k) * th) / s).add(b.multiply(Math.sin(k * th) / s)).normalize();
    }

    // ===================================================================== сглаженная траектория цели
    /** Отсчёты цели по клиентским тикам: время экрана (с) → позиция и курс. */
    public static final class Track {
        private final double[] t = new double[64];
        private final double[][] v = new double[64][5];
        private int n, head;

        void add(double time, Entity e) {
            if (n > 0 && time <= t[(head + 63) & 63] + 1e-6) return;
            // скачок цели (актёр пересоздан на клиенте, пассажир ещё не «сел» в машину): не тянем сглаживание через
            // выброс — начинаем траекторию заново; камера тем временем идёт по инерции и потом мягко сходится
            if (n >= 2) {
                int a1 = (head + 63) & 63, a0 = (head + 62) & 63;
                double dtp = Math.max(1e-3, t[a1] - t[a0]), dt = time - t[a1];
                double px = v[a1][0] + (v[a1][0] - v[a0][0]) / dtp * dt, pz = v[a1][2] + (v[a1][2] - v[a0][2]) / dtp * dt;
                if (Math.hypot(e.getX() - px, e.getZ() - pz) > 4 + 1.5 * dt * 20 || Math.abs(e.getY() - v[a1][1]) > 6) n = 0;
            }
            double yaw = Math.toRadians(e instanceof net.minecraft.entity.LivingEntity le ? le.bodyYaw : e.getYaw());
            t[head] = time;
            v[head][0] = e.getX();
            v[head][1] = e.getY();
            v[head][2] = e.getZ();
            v[head][3] = -Math.sin(yaw);
            v[head][4] = Math.cos(yaw);
            head = (head + 1) & 63;
            n = Math.min(64, n + 1);
        }

        boolean ready() {
            return n >= 2;
        }

        /** Отсчётов в окне сглаживания; 0, если окно ещё не заполнено хотя бы наполовину (после пропажи цели). */
        int inWindow(double now, double h) {
            int c = 0;
            double oldest = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double t0 = t[(head - 1 - i) & 63];
                if (t0 < now - 2 * h) break;
                if (t0 <= now) {
                    c++;
                    oldest = t0;
                }
            }
            return oldest <= now - h ? c : 0;
        }

        /**
         * Сглаженное значение «сейчас» (now): взвешенная линейная регрессия по отсчётам в окне [now−2h, now] с центром
         * now−h и ядром (1−x²)³, затем экстраполяция к now. Новый отсчёт входит в окно с нулевым весом — кривая гладкая.
         * Возвращает [x, y, z, fx, fz, vx, vz] (fx, fz — сглаженный курс, нормированный; vx, vz — скорость, блоков/с).
         */
        double[] at(double now, double h) {
            double c = now - h;
            double sw = 0, st = 0, stt = 0;
            double[] sv = new double[5], stv = new double[5];
            for (int i = 0; i < n; i++) {
                int k = (head - 1 - i) & 63;
                double x = (t[k] - c) / h;
                if (x > 1) continue;
                if (x < -1) break;
                double w = 1 - x * x;
                w = w * w * w;
                double dt = t[k] - c;
                sw += w;
                st += w * dt;
                stt += w * dt * dt;
                for (int j = 0; j < 5; j++) {
                    sv[j] += w * v[k][j];
                    stv[j] += w * dt * v[k][j];
                }
            }
            double[] r = new double[7];
            if (sw < 1e-9) { // отсчётов в окне нет — последний известный
                int k = (head - 1) & 63;
                System.arraycopy(v[k], 0, r, 0, 5);
            } else {
                double den = sw * stt - st * st;
                double tm = st / sw;
                for (int j = 0; j < 5; j++) {
                    double mean = sv[j] / sw;
                    double slope = Math.abs(den) < 1e-12 ? 0 : (sw * stv[j] - st * sv[j]) / den;
                    // позиция — значение регрессии «сейчас» (среднее + наклон × (now − центр)); курс — без экстраполяции
                    r[j] = j < 3 ? mean + slope * (h - tm) : mean;
                    if (j == 0) r[5] = slope;
                    if (j == 2) r[6] = slope;
                }
            }
            double l = Math.hypot(r[3], r[4]);
            if (l > 1e-6) {
                r[3] /= l;
                r[4] /= l;
            } else {
                r[3] = 0;
                r[4] = 1;
            }
            // идёт/едет — курс по направлению движения (сглаженная скорость), стоит — по телу; переход плавный
            double sp = Math.hypot(r[5], r[6]);
            double w = MathHelper.clamp((sp - 0.6) / 1.4, 0, 1);
            w = w * w * (3 - 2 * w);
            if (w > 0) { // по углу, а не смешением векторов (иначе при развороте на ~180° курс переворачивался за кадр)
                double a0 = Math.atan2(r[4], r[3]), a1 = Math.atan2(r[6], r[5]);
                double d = MathHelper.wrapDegrees(Math.toDegrees(a1 - a0));
                double g = MathHelper.clamp((150 - Math.abs(d)) / 60, 0, 1);
                g = g * g * (3 - 2 * g);
                double ang = a0 + Math.toRadians(d) * w * g;
                r[3] = Math.cos(ang);
                r[4] = Math.sin(ang);
            }
            return r;
        }
    }

    /** По имени актёра в сцене (если район перезагрузился и у актёра новый id — траектория та же). */
    private static final Map<String, Track> TRACKS = new HashMap<>();

    /** Каждый клиентский тик: записать отсчёт цели. */
    public static void sample(double time, String name, Entity e) {
        TRACKS.computeIfAbsent(name, k -> new Track()).add(time, e);
    }

    /** Сглаженное значение или null, если в окне сглаживания меньше двух отсчётов (цель пропала с клиента). */
    @org.jetbrains.annotations.Nullable
    public static double[] smooth(String name, double now, double h) {
        Track tr = TRACKS.get(name);
        return tr == null || !tr.ready() || tr.inWindow(now, h) < 4 ? null : tr.at(now, h);
    }

    public static void clear() {
        TRACKS.clear();
    }
}
