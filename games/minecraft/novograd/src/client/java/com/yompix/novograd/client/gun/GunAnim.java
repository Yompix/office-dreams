package com.yompix.novograd.client.gun;

import java.util.HashMap;
import java.util.Map;

/**
 * Анимации ствола «по кадрам», но процедурно: кости модели (затвор, магазин, помпа, затвор снайперки, спуск, патрон),
 * наклон всего оружия и точки, где держат руки. Тайминги — доли перезарядки, которую назначил сервер,
 * поэтому анимация всегда совпадает с настоящей перезарядкой. Звуки — «метки» (cues), их играет GunFx.
 *
 * Единицы: кости — пиксели предмета и градусы (правило правой руки), оружие целиком — блоки и градусы вокруг рукояти.
 */
public final class GunAnim {
    private GunAnim() {}

    /** Поза ствола, который сейчас в руках у игрока (её читает GunGeoRenderer). */
    public static final Pose POSE = new Pose();

    public static final class Pose {
        public String gun;
        /** В прицеле прячем приклад (ставит GunFx). */
        public boolean hideStock;
        /** Всё оружие: сдвиг (блоки) и поворот (градусы) вокруг рукояти. */
        public float tx, ty, tz, rx, ry, rz;
        /** Точки рук в пикселях предмета. */
        public final float[] main = new float[3], support = new float[3];
        /** Где сейчас магазин (центр хвата), пиксели предмета; magVisible — для выпавшего в мир. */
        public final float[] magPos = new float[3];
        final Map<String, float[]> bones = new HashMap<>();

        public float[] bone(String n) {
            return bones.get(n);
        }

        float[] b(String n) {
            return bones.computeIfAbsent(n, k -> new float[7]);
        }

        void reset(GunMeta m) {
            tx = ty = tz = rx = ry = rz = 0;
            for (float[] v : bones.values()) java.util.Arrays.fill(v, 0);
            b("shell")[6] = 1;
            System.arraycopy(m.grip, 0, main, 0, 3);
            System.arraycopy(m.support, 0, support, 0, 3);
            System.arraycopy(m.magHand, 0, magPos, 0, 3);
        }
    }

    /** Что происходит с оружием (заполняет GunFx). */
    public static final class In {
        public String gun = "pistol";
        public float shotT = 99;          // с момента выстрела, с
        public boolean locked;            // пистолет: последний патрон — затвор встал на задержку
        public float reloadP = -1;        // 0..1 или -1
        public float reloadSec = 1;
        public boolean reloadEmpty;
        public int shells;
        public float inspectP = -1;
        public float trigger;             // 0..1 — палец на спуске
        public boolean empty;             // магазин пуст (у гранатомёта не видно выстрела в трубе)
    }

    /** Новые стволы двигаются как похожие старые (см. GunItem.base); у револьвера и гранатомёта — свои анимации. */
    static String base(String gun) {
        return com.yompix.novograd.item.GunItem.base(gun);
    }

    public static final float INSPECT_SEC = 2.8f;
    public static final float PUMP_AT = 0.28f, SNIPER_BOLT_AT = 0.42f;

    // ------------------------------------------------------------------ помощники
    static float s(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static float win(float x, float a, float b, float c, float d) {
        return s(a, b, x) * (1 - s(c, d, x));
    }

    static float bump(float x, float a, float b) {
        if (x <= a || x >= b) return 0;
        return (float) Math.sin((x - a) / (b - a) * Math.PI);
    }

    static float pulse(float t, float up, float down) {
        if (t < 0) return 0;
        if (t < up) return t / up;
        float k = 1 - (t - up) / down;
        return k <= 0 ? 0 : k * k;
    }

    static void lerp3(float[] out, float[] a, float[] b, float k) {
        for (int i = 0; i < 3; i++) out[i] = a[i] + (b[i] - a[i]) * k;
    }

    static float[] add(float[] a, float x, float y, float z) {
        return new float[]{a[0] + x, a[1] + y, a[2] + z};
    }

    // ------------------------------------------------------------------ главное
    public static void evaluate(GunMeta m, In in) {
        Pose p = POSE;
        p.gun = in.gun;
        p.reset(m);
        float trig = Math.max(in.trigger, pulse(in.shotT, 0.02f, 0.1f));
        p.b("trigger")[3] = -22 * trig;
        float k = pulse(in.shotT, 0.022f, 0.075f);
        String bg = base(in.gun);
        switch (bg) {
            case "revolver" -> p.b("hammer")[3] = 38 * k;
            case "rpg" -> p.b("mag")[6] = in.empty && in.reloadP < 0 ? 1 : 0;
            case "pistol" -> {
                p.b("slide")[2] = in.locked ? m.slideKick : m.slideKick * k;
                p.b("hammer")[3] = 38 * Math.max(k, in.locked ? 1 : 0);
            }
            case "smg", "rifle" -> p.b("bolt")[2] = m.slideKick * k;
            case "shotgun" -> {
                float t = in.shotT - PUMP_AT;
                float pump = s(0f, 0.08f, t) - s(0.12f, 0.22f, t);
                p.b("pump")[2] = m.slideKick * pump;
                p.tz += 0.012f * pump;
                p.rz += -3 * pump;
            }
            case "sniper" -> boltCycle(m, p, (in.shotT - SNIPER_BOLT_AT) / 0.62f, 1);
            default -> {}
        }
        if (in.reloadP >= 0) {
            if (bg.equals("shotgun")) shotgunReload(m, p, in);
            else if (bg.equals("revolver")) revolverReload(m, p, in);
            else if (bg.equals("rpg")) rpgReload(m, p, in);
            else magReload(m, p, in);
        } else if (in.inspectP >= 0) inspect(m, p, in);
        // рука на цевье помпы ходит вместе с ней
        if (in.gun.equals("shotgun")) {
            float pz = p.b("pump")[2];
            if (in.reloadP < 0) p.support[2] = m.support[2] + pz;
        }
    }

    /** Затвор снайперки: x 0..1 — вверх, назад (гильза), вперёд, вниз; правая рука на рукоятке. */
    static void boltCycle(GunMeta m, Pose p, float x, float handWeight) {
        if (x < -0.25f || x > 1.3f) return;
        float up = s(0.0f, 0.13f, x) - s(0.77f, 0.9f, x);
        float back = s(0.16f, 0.32f, x) - s(0.45f, 0.62f, x);
        float[] b = p.b("bolt");
        b[5] = 62 * up;
        b[2] = 2.6f * back;
        float w = win(x, -0.2f, -0.02f, 0.92f, 1.15f) * handWeight;
        if (w > 0 && m.boltHand != null) {
            // рукоятка поворачивается вокруг оси затвора и уезжает назад
            float[] piv = m.pivots.getOrDefault("bolt", new float[]{8, 9.6f, 11});
            float hx = m.boltHand[0] - piv[0], hy = m.boltHand[1] - piv[1];
            double a = Math.toRadians(62 * up);
            float[] hand = {piv[0] + (float) (hx * Math.cos(a) - hy * Math.sin(a)), piv[1] + (float) (hx * Math.sin(a) + hy * Math.cos(a)),
                    m.boltHand[2] + 2.6f * back};
            lerp3(p.main, p.main, hand, w);
            p.rz += -7 * w;
            p.ty += -0.012f * w;
            p.rx += 3 * w;
        }
    }

    /** Магазинная перезарядка: тактическая (магазин вынимают рукой) и с пустого (магазин падает, затвор). */
    static void magReload(GunMeta m, Pose p, In in) {
        float t = in.reloadP;
        boolean empty = in.reloadEmpty;
        float tilt = s(0, 0.14f, t) * (1 - s(0.86f, 1, t));
        p.rz += -20 * tilt;
        p.rx += 9 * tilt;
        p.tx += -0.035f * tilt;
        p.ty += 0.025f * tilt;
        float[] d = m.magDir;
        float[] mag = p.b("mag");
        float[] off = new float[3];
        float[] hand = m.support.clone();
        float handW;
        if (!empty) {
            float rel = s(0.14f, 0.24f, t) * 2;
            float pull = s(0.24f, 0.42f, t);
            float in1 = s(0.48f, 0.66f, t), seat = s(0.66f, 0.72f, t);
            if (t < 0.45f) {
                float dist = rel + pull * 14;
                off = new float[]{d[0] * dist - 5 * pull, d[1] * dist, d[2] * dist + 2 * pull};
            } else if (t < 0.48f) {
                mag[6] = 1;
                off = new float[]{-5, d[1] * 16, d[2] * 16 + 2};
            } else {
                float dist = (16 + (2 - 16) * in1) * (1 - seat);
                float side = 1 - in1;
                off = new float[]{d[0] * dist - 5 * side, d[1] * dist, d[2] * dist + 2 * side};
            }
            handW = win(t, 0.06f, 0.16f, 0.74f, 0.86f);
            hand = add(m.magHand, off[0], off[1], off[2]);
        } else {
            float drop = t - 0.12f;
            if (drop > 0) {
                float sec = drop * in.reloadSec;
                float dist = 1.5f + 0.5f * 200 * sec * sec;
                if (t < 0.5f) {
                    off = new float[]{d[0] * dist + 2 * sec, d[1] * dist, d[2] * dist};
                    mag[3] = 140 * sec;
                    if (dist > 40) mag[6] = 1;
                } else mag[6] = 1;
            }
            // рука: к подсумку, обратно с новым магазином, вставить
            float[] pouch = add(m.grip, -5, -10, -1);
            float[] start = add(m.magHand, d[0] * 6, d[1] * 6, d[2] * 6);
            float toPouch = s(0.10f, 0.30f, t), back = s(0.30f, 0.50f, t), ins = s(0.50f, 0.60f, t);
            float[] h = new float[3];
            lerp3(h, m.support, pouch, toPouch);
            lerp3(h, h, start, back);
            lerp3(h, h, m.magHand, ins);
            hand = h;
            if (t >= 0.30f) {
                mag[3] = 0;
                mag[6] = 0;
                off = new float[]{h[0] - m.magHand[0], h[1] - m.magHand[1], h[2] - m.magHand[2]};
            }
            handW = win(t, 0.04f, 0.10f, 0.62f, 0.66f);
            // затвор / рукоятка взвода
            float[] rackHand = null;
            float rackW = win(t, 0.64f, 0.70f, 0.82f, 0.92f);
            float pullB = s(0.70f, 0.77f, t) * (1 - s(0.78f, 0.80f, t));
            switch (base(in.gun)) {
                case "pistol" -> {
                    float[] sl = p.b("slide");
                    sl[2] = t < 0.785f ? m.slideKick + 0.35f * pullB : m.slideKick * (1 - s(0.785f, 0.80f, t));
                    p.b("hammer")[3] = 38;
                    rackHand = new float[]{m.sight[0] - 1.0f, m.sight[1] - 0.8f, m.sight[2] - 2.6f + sl[2]};
                    p.tz += -0.05f * win(t, 0.64f, 0.70f, 0.8f, 0.9f);
                    p.tx += 0.02f * win(t, 0.64f, 0.70f, 0.8f, 0.9f);
                }
                case "smg" -> {
                    p.b("handle")[2] = 2.4f * pullB;
                    p.b("bolt")[2] = 1.3f * pullB;
                    float[] hp = m.pivots.getOrDefault("handle", new float[]{6.7f, 9.8f, 1.8f});
                    rackHand = new float[]{hp[0] - 0.6f, hp[1], hp[2] + 2.4f * pullB};
                }
                case "rifle" -> {
                    p.b("bolt")[2] = 2.4f * pullB;
                    float[] bp = m.pivots.getOrDefault("bolt", new float[]{9, 9.7f, 11});
                    rackHand = new float[]{bp[0] + 1.0f, bp[1], bp[2] + 0.6f + 2.4f * pullB};
                }
                case "sniper" -> boltCycle(m, p, (t - 0.66f) / 0.3f, 1);
                default -> {}
            }
            if (rackHand != null && rackW > 0) {
                lerp3(p.support, p.support, rackHand, rackW);
                p.rz += -6 * rackW;
            }
            float slapT = bump(t, 0.59f, 0.66f);
            p.ty += 0.02f * slapT;
            p.rx += 4 * slapT;
        }
        if (!empty) {
            float slapT = bump(t, 0.68f, 0.76f);
            p.ty += 0.02f * slapT;
            p.rx += 4 * slapT;
        }
        mag[0] = off[0];
        mag[1] = off[1];
        mag[2] = off[2];
        p.magPos[0] = m.magHand[0] + off[0];
        p.magPos[1] = m.magHand[1] + off[1];
        p.magPos[2] = m.magHand[2] + off[2];
        if (handW > 0) lerp3(p.support, p.support, hand, handW);
    }

    /** Дробовик: патроны по одному в окно снизу, с пустого — помпа в конце. */
    static void shotgunReload(GunMeta m, Pose p, In in) {
        float T = Math.max(0.5f, in.reloadSec);
        float t = in.reloadP * T;
        float tilt = s(0, 0.3f, t) * (1 - s(T - 0.3f, T, t));
        p.rz += -40 * tilt;
        p.rx += 8 * tilt;
        p.tx += -0.07f * tilt;
        p.ty += 0.06f * tilt;
        float[] port = m.magHand; // центр патрона в окне
        float[] entry = add(port, 0, -1.4f, 0.6f);
        float[] pouch = add(m.grip, -3.5f, -6.5f, -2);
        float[] shell = p.b("shell");
        shell[6] = 1;
        float start = 0.4f, per = 0.35f;
        int n = Math.max(0, in.shells);
        float[] hand = null;
        if (t >= start && t < start + per * n) {
            int i = (int) ((t - start) / per);
            float q = (t - start - i * per) / per;
            float[] h = new float[3];
            float[] prev = i == 0 ? m.support : port;
            lerp3(h, prev, pouch, s(0, 0.3f, q));
            lerp3(h, h, entry, s(0.3f, 0.7f, q));
            float push = s(0.72f, 1f, q);
            float[] in2 = add(port, 0, 0.3f, -1.4f);
            lerp3(h, h, in2, push);
            hand = h;
            if (q > 0.22f && q < 0.97f) {
                shell[6] = 0;
                shell[0] = h[0] - port[0];
                shell[1] = h[1] - port[1];
                shell[2] = h[2] - port[2];
            }
        } else if (n > 0 && t >= start + per * n && t < start + per * n + 0.25f) {
            float[] h = new float[3];
            lerp3(h, port, m.support, s(start + per * n, start + per * n + 0.25f, t));
            hand = h;
        }
        if (in.reloadEmpty) {
            float u = (t - (T - 0.55f)) / 0.5f;
            float pump = s(0, 0.4f, u) - s(0.5f, 0.9f, u);
            p.b("pump")[2] = m.slideKick * pump;
            p.tz += 0.012f * pump;
        }
        if (hand != null) System.arraycopy(hand, 0, p.support, 0, 3);
        else p.support[2] = m.support[2] + p.b("pump")[2];
    }

    /** Револьвер: барабан откидывается влево, рука ускорителем заряжания вставляет шесть патронов, барабан на место. */
    static void revolverReload(GunMeta m, Pose p, In in) {
        float t = in.reloadP;
        float tilt = s(0, 0.12f, t) * (1 - s(0.86f, 1, t));
        p.rx += 28 * tilt;   // стволом вверх — гильзы высыпаются
        p.rz += -14 * tilt;
        p.ty += 0.04f * tilt;
        float open = s(0.06f, 0.14f, t) * (1 - s(0.8f, 0.86f, t));
        p.b("mag")[5] = 70 * open;
        // рука: к барабану (выбросить гильзы), к поясу за ускорителем, к барабану, закрыть
        float[] cyl = {m.magHand[0] - 1.0f * open, m.magHand[1] - 0.6f * open, m.magHand[2]};
        float[] pouch = add(m.grip, -4, -8, -1);
        float[] h = new float[3];
        lerp3(h, m.support, cyl, s(0.04f, 0.14f, t));
        lerp3(h, h, pouch, s(0.16f, 0.26f, t));
        lerp3(h, h, add(cyl, 0, 0, -1.5f), s(0.26f, 0.34f, t));
        lerp3(h, h, cyl, s(0.74f, 0.82f, t));
        lerp3(h, h, m.support, s(0.86f, 0.96f, t));
        System.arraycopy(h, 0, p.support, 0, 3);
        float slap = bump(t, 0.8f, 0.88f);
        p.rz += 6 * slap;
    }

    /** Гранатомёт: новый выстрел ПГ из-за спины — в трубу спереди. */
    static void rpgReload(GunMeta m, Pose p, In in) {
        float t = in.reloadP;
        float tilt = s(0, 0.15f, t) * (1 - s(0.85f, 1, t));
        p.rx += -16 * tilt;  // дулом вниз, к руке
        p.ty += -0.05f * tilt;
        p.tx += 0.04f * tilt;
        float[] mag = p.b("mag");
        float[] pouch = add(m.grip, -6, -12, 4);
        float[] front = add(m.magHand, 0, -1.5f, -6);
        float[] h = new float[3];
        lerp3(h, m.support, pouch, s(0.05f, 0.25f, t));
        lerp3(h, h, front, s(0.3f, 0.55f, t));
        lerp3(h, h, m.magHand, s(0.55f, 0.7f, t));
        lerp3(h, h, m.support, s(0.78f, 0.92f, t));
        System.arraycopy(h, 0, p.support, 0, 3);
        if (t < 0.28f) mag[6] = 1;
        else if (t < 0.7f) {
            mag[0] = h[0] - m.magHand[0];
            mag[1] = h[1] - m.magHand[1];
            mag[2] = h[2] - m.magHand[2];
        }
        p.magPos[0] = m.magHand[0] + mag[0];
        p.magPos[1] = m.magHand[1] + mag[1];
        p.magPos[2] = m.magHand[2] + mag[2];
    }

    /** Осмотр (Y): повернуть одной стороной, другой, проверить патрон / магазин / затвор. */
    static void inspect(GunMeta m, Pose p, In in) {
        float t = in.inspectP;
        float a = s(0, 0.18f, t) * (1 - s(0.84f, 1, t));
        float flip = s(0.40f, 0.58f, t);
        float wob = (float) Math.sin(t * 13) * 1.2f * a;
        p.ry += (38 + (-22 - 38) * flip) * a + wob;
        p.rz += (-24 + (22 + 24) * flip) * a;
        p.rx += 10 * a + wob * 0.5f;
        p.tx += -0.11f * a;
        p.ty += 0.05f * a;
        p.tz += 0.07f * a;
        float det = win(t, 0.62f, 0.68f, 0.74f, 0.8f);
        float handW = win(t, 0.56f, 0.62f, 0.8f, 0.86f);
        switch (base(in.gun)) {
            case "revolver" -> {
                p.b("mag")[5] = 70 * det;
                lerp3(p.support, p.support, m.magHand, handW);
            }
            case "pistol" -> {
                p.b("slide")[2] = 0.9f * det;
                lerp3(p.support, p.support, new float[]{m.sight[0] - 0.9f, m.sight[1] - 0.8f, m.muzzle[2] + 1.6f + 0.9f * det}, handW);
            }
            case "smg", "rifle" -> {
                float[] d = m.magDir;
                float[] mag = p.b("mag");
                mag[0] = d[0] * 2.5f * det;
                mag[1] = d[1] * 2.5f * det;
                mag[2] = d[2] * 2.5f * det;
                lerp3(p.support, p.support, add(m.magHand, mag[0], mag[1], mag[2]), handW);
            }
            case "shotgun" -> {
                p.b("pump")[2] = 1.2f * det;
                p.support[2] += 1.2f * det;
            }
            case "sniper" -> {
                float[] b = p.b("bolt");
                b[5] = 62 * det;
                if (m.boltHand != null) {
                    float[] piv = m.pivots.getOrDefault("bolt", new float[]{8, 9.6f, 11});
                    float hx = m.boltHand[0] - piv[0], hy = m.boltHand[1] - piv[1];
                    double ang = Math.toRadians(62 * det);
                    float[] hand = {piv[0] + (float) (hx * Math.cos(ang) - hy * Math.sin(ang)), piv[1] + (float) (hx * Math.sin(ang) + hy * Math.cos(ang)), m.boltHand[2]};
                    lerp3(p.main, p.main, hand, handW);
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ звуковые метки
    /** Метка: момент (доля) и звук. */
    public record Cue(float at, String sound) {}

    public static Cue[] reloadCues(String gun, boolean empty, int shells, float sec) {
        gun = base(gun);
        if (gun.equals("revolver")) {
            java.util.List<Cue> c = new java.util.ArrayList<>(java.util.List.of(new Cue(0.02f, "handling"), new Cue(0.1f, "mag_out")));
            for (int i = 0; i < 6; i++) c.add(new Cue(0.3f + 0.07f * i, "shell_in"));
            c.add(new Cue(0.84f, "mag_in"));
            return c.toArray(new Cue[0]);
        }
        if (gun.equals("rpg")) return new Cue[]{new Cue(0.02f, "handling"), new Cue(0.3f, "handling"), new Cue(0.68f, "mag_in"), new Cue(0.76f, "bolt_fwd")};
        if (gun.equals("shotgun")) {
            java.util.List<Cue> c = new java.util.ArrayList<>();
            c.add(new Cue(0.02f, "handling"));
            for (int i = 0; i < shells; i++) c.add(new Cue((0.4f + 0.35f * i + 0.35f * 0.85f) / sec, "shell_in"));
            if (empty) c.add(new Cue((sec - 0.55f) / sec, "pump"));
            return c.toArray(new Cue[0]);
        }
        if (!empty) return new Cue[]{new Cue(0.02f, "handling"), new Cue(0.2f, "mag_out"), new Cue(0.68f, "mag_in")};
        String rack = switch (gun) {
            case "pistol" -> "slide_release";
            default -> "bolt_fwd";
        };
        if (gun.equals("sniper"))
            return new Cue[]{new Cue(0.02f, "handling"), new Cue(0.12f, "mag_out"), new Cue(0.6f, "mag_in"), new Cue(0.70f, "bolt_back"), new Cue(0.85f, "bolt_fwd"), new Cue(0.13f, "drop")};
        return new Cue[]{new Cue(0.02f, "handling"), new Cue(0.12f, "mag_out"), new Cue(0.6f, "mag_in"), new Cue(0.71f, gun.equals("pistol") ? "slide_back" : "bolt_back"),
                new Cue(0.785f, rack), new Cue(0.13f, "drop")};
    }

    public static Cue[] inspectCues(String gun) {
        return switch (base(gun)) {
            case "revolver" -> new Cue[]{new Cue(0.02f, "handling"), new Cue(0.64f, "mag_out"), new Cue(0.77f, "mag_in")};
            case "pistol" -> new Cue[]{new Cue(0.02f, "handling"), new Cue(0.64f, "slide_back"), new Cue(0.77f, "slide_release")};
            case "shotgun" -> new Cue[]{new Cue(0.02f, "handling"), new Cue(0.64f, "pump")};
            case "sniper" -> new Cue[]{new Cue(0.02f, "handling"), new Cue(0.64f, "bolt_back"), new Cue(0.77f, "bolt_fwd")};
            default -> new Cue[]{new Cue(0.02f, "handling"), new Cue(0.64f, "mag_out"), new Cue(0.77f, "mag_in")};
        };
    }
}
