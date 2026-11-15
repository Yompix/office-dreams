package com.yompix.glukhoman.client.anim;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Жесты новой системы. Знаки — как в {@link Pose}. Ключи по времени ({@link #k}) — плавные (smoothstep между ключами),
 * поэтому жест входит и выходит без рывков даже без весов слоя.
 */
public final class Gestures {
    private Gestures() {}

    static final Set<String> RARM = Set.of("right_arm", "right_forearm");
    static final Set<String> LARM = Set.of("left_arm", "left_forearm");
    static final Set<String> ARMS = Set.of("right_arm", "right_forearm", "left_arm", "left_forearm");

    /** Ключи: k(t, t0, v0, t1, v1, …) — плавно между ними, до первого и после последнего — крайние значения. */
    public static double k(double t, double... kv) {
        if (t <= kv[0]) return kv[1];
        for (int i = 2; i < kv.length; i += 2) {
            if (t <= kv[i]) {
                double u = (t - kv[i - 2]) / (kv[i] - kv[i - 2]);
                u = u * u * (3 - 2 * u);
                return kv[i - 1] + (kv[i + 1] - kv[i - 1]) * u;
            }
        }
        return kv[kv.length - 1];
    }

    /** Толчок: быстрый рост за a, затухание с темпом d. */
    static double punch(double t, double at, double a, double d) {
        double x = t - at;
        if (x < 0) return 0;
        return x < a ? x / a : Math.exp(-(x - a) * d);
    }

    // ======================= общие =======================
    /** Вздрогнул: плечи вверх, корпус назад, присел — и отпустило. */
    public static final Clip FLINCH = Clip.of("flinch", 1.4, 0.03, 0.5, 5, 1, Set.of(), (t, p, a) -> {
        double f = punch(t, 0, 0.09, 3.2);
        p.pos("right_arm", 0, 0.7 * f, 0).pos("left_arm", 0, 0.7 * f, 0);
        p.rot("chest", -5 * f, 0, 0).rot("waist", -2 * f, 0, 0).pos("root", 0, -0.5 * f, 0.3 * f);
        p.rot("right_arm", 10 * f, 0, 0).rot("left_arm", 10 * f, 0, 0).rot("right_forearm", 20 * f, 0, 0).rot("left_forearm", 20 * f, 0, 0);
    });

    /** Досадка при остановке: вес по инерции вперёд и назад, лёгкое приседание. */
    public static final Clip SETTLE = Clip.of("settle", 1.0, 0.05, 0.3, 1, 1, Set.of(), (t, p, a) -> {
        double f = Math.sin(Math.min(1, t / 0.9) * Math.PI) * Math.exp(-t * 1.5);
        p.rot("chest", 4 * f, 0, 0).rot("waist", 2 * f, 0, 0).pos("root", 0, -0.35 * f, 0);
    });

    /** Показать рукой на точку {@link Animator#pointAt}: рука поднимается, корпус доворачивается, держит, опускает. */
    public static Clip point(double hold) {
        return Clip.of("point", hold + 1.6, 0.05, 0.1, 6, 1, RARM, (t, p, a) -> {
            double up = k(t, 0, 0, 0.55, 1, hold + 0.7, 1, hold + 1.5, 0);
            double[] d = rel(a, a.pointAt);
            double twist = MathHelper.clamp(d[0] * 0.35, -25, 25);
            p.rot("chest", 0, twist * up, 0);
            p.rot("right_arm", (88 + d[1]) * up, MathHelper.clamp(d[0] - twist, -35, 95) * up, 0);
            p.rot("right_forearm", 6 * up, 0, 0);
            // пока держит — рука чуть «подаёт» направление дважды
            double tap = t > 0.6 && t < hold + 0.6 ? Math.max(0, Math.sin((t - 0.6) * 5.5)) * Math.exp(-(t - 0.6) * 1.2) : 0;
            p.rot("right_arm", -5 * tap, 0, 0).rot("right_forearm", 8 * tap, 0, 0);
        });
    }

    /** Направление на точку от груди персонажа: {поворот вправо, подъём вверх}, градусы. */
    static double[] rel(Animator a, Vec3d at) {
        if (at == null) return new double[]{0, 0};
        Vec3d o = a.e.getPos().add(0, 1.35, 0);
        Vec3d d = at.subtract(o);
        double y = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(-d.x, d.z)) - a.e.bodyYaw);
        double el = Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        return new double[]{y, el};
    }

    /** Протянуть вещь правой рукой (держит, пока не снимут). */
    public static final Clip GIVE = Clip.of("give", 0, 0.05, 0.6, 6, 1, RARM, (t, p, a) -> {
        double u = k(t, 0, 0, 0.7, 1);
        p.rot("right_arm", 62 * u, -10 * u, 0).rot("right_forearm", 22 * u, 0, 0).rot("chest", 6 * u, 0, 0).rot("waist", 2 * u, 0, 0);
    });

    /** Принять обеими руками, поднести к груди и посмотреть на вещь. */
    public static final Clip TAKE = Clip.of("take", 0, 0.05, 0.6, 6, 0.4, ARMS, (t, p, a) -> {
        double reach = k(t, 0, 0, 0.55, 1, 1.0, 1, 1.6, 0.35);
        double hold = k(t, 0.9, 0, 1.6, 1);
        p.rot("right_arm", 60 * reach + 25 * hold, -6 * reach - 18 * hold, 0).rot("right_forearm", 18 * reach + 70 * hold, 0, 0);
        p.rot("left_arm", 55 * reach + 22 * hold, 10 * reach + 22 * hold, 0).rot("left_forearm", 20 * reach + 72 * hold, 0, 0);
        p.rot("head", 22 * hold, 0, 0).rot("chest", 4 * reach, 0, 0);
    });

    /** Поднять камеру к глазу (медленно — на «вдохе»). */
    public static final Clip RAISE = Clip.of("raise", 0, 0.1, 0.6, 7, 1, ARMS, (t, p, a) -> {
        double u = k(t, 0, 0, 1.4, 1);
        p.rot("right_arm", 82 * u, -14 * u, 0).rot("right_forearm", 96 * u, 0, 0);
        p.rot("left_arm", 78 * u, 26 * u, 0).rot("left_forearm", 100 * u, 0, 0);
    });

    /** Наклон к окну/к камере: корпус вперёд, голова тянется. */
    public static final Clip LEAN = Clip.of("lean", 0, 0.8, 0.9, 2, 1, Set.of(), (t, p, a) -> {
        p.rot("chest", 13, 0, 0).rot("waist", 6, 0, 0).rot("neck", -4, 0, 0).pos("root", 0, -0.3, 0);
        p.rot("right_arm", 18, 0, 0).rot("right_forearm", 30, 0, 0);
    });

    /** Шаг назад от двери: вес назад, руки чуть вперёд, плечи вверх. */
    public static final Clip RECOIL = Clip.of("recoil", 2.0, 0.08, 0.8, 4, 1, Set.of(), (t, p, a) -> {
        double f = k(t, 0, 0, 0.25, 1, 1.2, 0.7, 2.0, 0);
        p.rot("chest", -8 * f, 0, 0).rot("waist", -4 * f, 0, 0).pos("root", 0, -0.3 * f, 1.4 * f);
        p.rot("right_arm", 25 * f, 0, 0).rot("left_arm", 22 * f, 0, 0).rot("right_forearm", 30 * f, 0, 0).rot("left_forearm", 30 * f, 0, 0);
        p.pos("right_arm", 0, 0.4 * f, 0).pos("left_arm", 0, 0.4 * f, 0);
    });

    // ======================= Захар Ильич =======================
    /** Кашель: три толчка грудью, правая рука ко рту, голова вниз; звук — по первому толчку. */
    public static final Clip COUGH = Clip.of("cough", 2.3, 0.15, 0.45, 3, 0.15, RARM, (t, p, a) -> {
        double b = punch(t, 0.05, 0.06, 7) + 0.8 * punch(t, 0.48, 0.06, 7) + 0.65 * punch(t, 0.92, 0.06, 7);
        double up = k(t, 0, 0, 0.22, 1, 1.6, 1, 2.2, 0);
        p.rot("chest", 13 * b + 3 * up, 0, 0).rot("waist", 4 * b, 0, 0).rot("neck", 4 * b, 0, 0).rot("head", 9 * b + 6 * up, 0, 0);
        p.rot("right_arm", (74 + 4 * b) * up, -32 * up, 0).rot("right_forearm", 108 * up, 0, 0);
        p.rot("left_arm", 6 * b, 0, 0).pos("root", 0, -0.25 * b, 0);
    }, new Object[]{0.02, "cough", 0.9});

    /** Поправить форму: одёрнуть китель двумя руками, распрямиться, коснуться козырька. */
    public static final Clip UNIFORM = Clip.of("uniform", 3.2, 0.1, 0.1, 2, 0.7, ARMS, (t, p, a) -> {
        double hem = k(t, 0, 0, 0.4, 1, 1.15, 1, 1.45, 0);
        double tug = punch(t, 0.55, 0.07, 9) + punch(t, 0.92, 0.07, 9);
        double cap = k(t, 1.2, 0, 1.6, 1, 2.3, 1, 2.9, 0);
        double tall = k(t, 0.3, 0, 1.0, 1, 2.6, 1, 3.2, 0);
        p.rot("chest", -7 * tall - 3 * tug, 0, 0).rot("head", -4 * tall + 5 * k(t, 1.75, 0, 1.95, 1, 2.2, 0), 0, 0);
        p.rot("right_arm", 20 * hem + 150 * cap, -12 * hem - 22 * cap, 0).rot("right_forearm", 42 * hem + 112 * cap, 0, 0);
        p.rot("left_arm", 20 * hem, 12 * hem, 0).rot("left_forearm", 42 * hem, 0, 0);
        p.pos("right_arm", 0, -0.6 * tug * hem, 0).pos("left_arm", 0, -0.6 * tug, 0);
    });

    /** Устал: руки на поясницу, прогнуться назад, голова вверх, выдох. */
    public static final Clip BACK = Clip.of("back", 3.6, 0.1, 0.1, 2, 0.3, ARMS, (t, p, a) -> {
        double u = k(t, 0, 0, 0.7, 1, 2.6, 1, 3.5, 0);
        double arch = k(t, 0.6, 0, 1.3, 1, 2.3, 1, 3.0, 0);
        p.rot("right_arm", -32 * u, 18 * u, 0).rot("right_forearm", 68 * u, 0, 0);
        p.rot("left_arm", -32 * u, -18 * u, 0).rot("left_forearm", 68 * u, 0, 0);
        p.rot("chest", -11 * arch, 0, 0).rot("waist", -4 * arch, 0, 0).rot("head", -14 * arch, 0, 0);
    });

    /** Осмотреться: глянуть через плечо (голова и грудь), задержаться, вернуться. */
    public static final Clip GLANCE = Clip.of("glance", 3.4, 0.2, 0.3, 1, 0.1, Set.of(), (t, p, a) -> {
        double y = k(t, 0, 0, 0.6, -55, 1.6, -55, 2.2, 20, 2.8, 20, 3.4, 0);
        p.rot("head", 3, y * 0.6, 0).rot("neck", 0, y * 0.25, 0).rot("chest", 0, y * 0.15, 0);
    });

    /** Руки во время речи: левая ладонь «объясняет», на ударениях — взмах; плечи живые. */
    public static final Clip TALK = Clip.of("talk", 0, 0.35, 0.6, 1, 1, Set.of(), (t, p, a) -> {
        double s = a.stress;
        double slow = Math.sin(t * 1.7);
        p.rot("left_arm", 7 + 6 * s + 2 * slow, 6, 0).rot("left_forearm", 16 + 16 * s, 0, 0);
        p.rot("right_arm", 2 + 2 * s, 0, 0).rot("right_forearm", 5 * s, 0, 0);
        p.rot("chest", 2 * s, 2 * slow, 0);
    });

    /** Сделать шаг назад (от двери), протянуть руку к плечу (схватить за плечо), отпрянуть — жесты для «моментов» сюжета. */
    public static final Clip REACH = Clip.of("reach", 1.8, 0.1, 0.5, 6, 1, RARM, (t, p, a) -> {
        double u = k(t, 0, 0, 0.25, 1, 1.1, 1, 1.7, 0);
        p.rot("right_arm", 72 * u, -16 * u, 0).rot("right_forearm", 28 * u, 0, 0).rot("chest", 7 * u, -6 * u, 0).pos("root", 0, 0, -0.6 * u);
    });

    /** Нервный жест (Гена): быстро глянуть на часы и одёрнуть рукав. */
    public static final Clip WATCH = Clip.of("watch", 1.6, 0.08, 0.25, 2, 0.2, LARM, (t, p, a) -> {
        double u = k(t, 0, 0, 0.18, 1, 1.15, 1, 1.5, 0);
        p.rot("left_arm", 58 * u, 34 * u, 0).rot("left_forearm", 96 * u, 0, 0).rot("head", 26 * u, 14 * u, 0);
    });

    /** Петрович: потереть шею, махнуть рукой. */
    public static final Clip WAVE_OFF = Clip.of("waveoff", 2.2, 0.1, 0.3, 2, 0.8, RARM, (t, p, a) -> {
        double up = k(t, 0, 0, 0.4, 1, 1.4, 1, 2.1, 0);
        double flick = Math.max(0, Math.sin((t - 0.6) * 6)) * (t > 0.6 && t < 1.4 ? 1 : 0);
        p.rot("right_arm", 40 * up, -10 * up, 0).rot("right_forearm", 50 * up + 25 * flick, 0, 0).rot("head", -3 * flick, -8 * flick, 0);
    });

    /** Голова лунатика: рывок на звук и снова клонится. */
    public static final Clip SLEEP_JERK = Clip.of("sleepjerk", 2.0, 0.02, 0.8, 3, 1, Set.of(), (t, p, a) -> {
        double f = punch(t, 0, 0.08, 2.2);
        p.rot("head", -24 * f, 0, 0).rot("neck", -8 * f, 0, 0).rot("chest", -3 * f, 0, 0);
    });

    public static final AnimSet ZAKHAR = new AnimSet("z_walk", "z_idle", List.of(COUGH, UNIFORM, GLANCE, UNIFORM, BACK), BACK, TALK, SETTLE, 0.22);
    /** «Я» в кат-сцене (скин игрока): обычная ходьба и стойка, без случайных жестов. */
    public static final AnimSet PLAYER = new AnimSet("walk", "idle", List.of(), null, null, SETTLE, 0.3);
    public static final AnimSet GENERIC = new AnimSet("walk", "idle", List.of(GLANCE), null, TALK, SETTLE, 0.27);
    public static final AnimSet VIKA = new AnimSet("walk", "v_idle", List.of(GLANCE), null, TALK, SETTLE, 0.3);
    /** Гена: уверенно, но нервно — быстрый взгляд, резкие реакции. */
    public static final AnimSet GENA = new AnimSet("g_walk", "g_idle", List.of(WATCH, GLANCE, WATCH), null, TALK, SETTLE, 0.34).withLook(85, 0.85, 12, 0);
    /** Петрович: расслабленный, шатается, взгляд ленивый. */
    public static final AnimSet PETROVICH = new AnimSet("p_walk", "p_idle", List.of(WAVE_OFF, GLANCE), BACK, TALK, SETTLE, 0.2).withLook(70, 1, 4, 0);
    public static final AnimSet POLYA = new AnimSet("o_walk", "o_idle", List.of(GLANCE), null, TALK, SETTLE, 0.2).withLook(60, 1, 4.5, 0);
    public static final AnimSet ZINA = new AnimSet("walk", "zi_idle", List.of(GLANCE), null, TALK, SETTLE, 0.3).withLook(80, 1, 8, 0);
    /** Лунатики: идут во сне, стоят — качаются; на звук голова дёргается; глаза закрыты. */
    public static final AnimSet SLEEP = new AnimSet("sleepwalk", "s_idle", List.of(), null, null, null, 0.12).withLook(25, 1, 1.5, 0)
            .pick(a -> a.walking ? "sleepwalk" : "s_idle");
    /** Двойник: смотрит с опозданием и перелётом, не моргает, голова набок. */
    public static final AnimSet DOPPEL = new AnimSet("walk", "idle", List.of(), null, null, null, 0.0).withLook(100, 0.45, 3.2, 14).noBlink();
    /** Обходчик: см. {@link Creatures#watcher}. Не дышит, не моргает, взгляд перелетает за 90° и медленно возвращается, голова кренится. */
    public static final AnimSet WATCHER = new AnimSet("w_walk", "w_observe", List.of(), null, null, null, 0.0).withLook(150, 0.32, 4.2, 22).noBlink()
            .pick(Creatures::watcher);

    public static final Map<String, AnimSet> SETS = Map.ofEntries(Map.entry("zakhar", ZAKHAR), Map.entry("@me", PLAYER), Map.entry("vika", VIKA),
            Map.entry("gena", GENA), Map.entry("petrovich", PETROVICH), Map.entry("polya", POLYA), Map.entry("zina", ZINA),
            Map.entry("nyura", POLYA), Map.entry("kolya", PETROVICH), Map.entry("lyuba", ZINA), Map.entry("mitya", VIKA));

    /** Жесты по имени — для синхронизации с сервера (S2C «gest»). */
    public static Clip byName(String n, double hold) {
        return switch (n) {
            case "cough" -> COUGH;
            case "uniform" -> UNIFORM;
            case "back" -> BACK;
            case "glance" -> GLANCE;
            case "point" -> point(hold > 0 ? hold : 1.6);
            case "give" -> GIVE;
            case "take" -> TAKE;
            case "raise" -> RAISE;
            case "lean" -> LEAN;
            case "recoil" -> RECOIL;
            case "flinch" -> FLINCH;
            case "reach" -> REACH;
            case "watch" -> WATCH;
            case "waveoff" -> WAVE_OFF;
            case "sleepjerk" -> SLEEP_JERK;
            default -> null;
        };
    }
}
