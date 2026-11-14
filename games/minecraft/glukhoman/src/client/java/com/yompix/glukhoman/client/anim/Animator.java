package com.yompix.glukhoman.client.anim;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Аниматор персонажа (новая система, этап 1 — прототип на Захаре Ильиче). Один на сущность, только на клиенте.
 *
 * <p>Порядок за кадр: база GeckoLib (ходьба/стойка из JSON, плавный переход между ними — сам GeckoLib) → слои-жесты по
 * приоритету (свои кости ведут целиком, на остальные — добавка) → дыхание → взгляд (глаза → голова → шея → грудь,
 * пружиной без рывков) → челюсть по речи. Лицо — сменой текстуры: моргание, рот (открыт/приоткрыт), глаза влево/вправо.
 *
 * <p>Состояния: стоит ↔ идёт (с гистерезисом; при остановке — «досадка» веса), во время стойки — случайные жесты из
 * набора персонажа, речь сама добавляет жесты руками и кивки. Всё мягко входит и выходит (веса слоёв).
 */
public final class Animator {
    static final Map<Integer, Animator> ALL = new HashMap<>();

    /** Звук от жеста (кашель и т. п.) — для журнала сцены при записи видео. */
    public static SoundHook onSound;

    public interface SoundHook {
        void play(String name, Vec3d at, float vol);
    }

    public static Animator of(LivingEntity e, AnimSet set) {
        Animator a = ALL.get(e.getId());
        if (a == null || a.e != e) {
            if (ALL.size() > 64) ALL.values().removeIf(x -> x.e.isRemoved());
            a = new Animator(e, set);
            ALL.put(e.getId(), a);
        }
        return a;
    }

    public static Animator get(LivingEntity e) {
        Animator a = ALL.get(e.getId());
        return a != null && a.e == e ? a : null;
    }

    public final LivingEntity e;
    public final AnimSet set;
    final Random rnd;

    // ---- время ----
    double now, last = -1;
    /** Своё время (сек) для кукол кат-сцены; иначе — время мира. */
    public Supplier<Double> clock;

    // ---- взгляд ----
    /** Куда смотреть (точка в мире). null — авто: ближайший игрок рядом, иначе прямо. */
    public Vec3d lookAt;
    public boolean autoLook = true;
    /** Скорость поворота головы (рад/с пружины); вздрогнул — быстрее. */
    public double lookSpeed = 7;
    double hy, hp, hyv, hpv, lookW = 1;
    int eyes;           // -1 влево, 0 прямо, 1 вправо (для самого персонажа)
    double eyesUntil;

    // ---- моргание ----
    double nextBlink, blinkEnd, blink2;

    // ---- речь ----
    String line = "";
    double sayT0, sayEnd;
    final List<double[]> mouth = new ArrayList<>();   // {время от начала, открытие 0/0.5/1}
    double jaw, stress;

    // ---- слои ----
    final List<Layer> layers = new ArrayList<>();
    final Pose tmp = new Pose();
    final Set<GeoBone> touched = new HashSet<>();

    // ---- движение ----
    /** Куклы сцены: скорость задаёт режиссёр (блоков/с); иначе считаем по смещению. */
    public double forcedSpeed = -1;
    double speed, lastX = Double.NaN, lastZ, idleSince, nextIdle;
    /** Усталость 0…1: растёт при ходьбе — дыхание чаще и глубже, после остановки — «усталый» жест. */
    public double fatigue;
    public boolean walking, autoIdle = true;
    String lastIdle = "";

    // ---- в руке ----
    public ItemStack held = ItemStack.EMPTY;
    public String heldBone = "right_hand";
    /** Цель для жеста «показать». */
    public Vec3d pointAt;

    Animator(LivingEntity e, AnimSet set) {
        this.e = e;
        this.set = set;
        this.rnd = new Random(e.getId() * 31L + 7);
        nextBlink = 1 + rnd.nextDouble() * 2;
        nextIdle = 3 + rnd.nextDouble() * 3;
    }

    static final class Layer {
        final Clip clip;
        final double t0;
        double stopAt = Double.NaN;

        Layer(Clip c, double t0) {
            clip = c;
            this.t0 = t0;
        }

        double weight(double now) {
            double t = now - t0;
            double w = clip.fadeIn() <= 0 ? 1 : MathHelper.clamp(t / clip.fadeIn(), 0, 1);
            double end = clip.len() > 0 ? t0 + clip.len() : stopAt;
            if (!Double.isNaN(end) && clip.fadeOut() > 0) w = Math.min(w, MathHelper.clamp((end - now) / clip.fadeOut(), 0, 1));
            else if (!Double.isNaN(end) && now >= end) w = 0;
            return smooth(w);
        }

        boolean done(double now) {
            double end = clip.len() > 0 ? t0 + clip.len() : stopAt;
            return !Double.isNaN(end) && now >= end;
        }
    }

    // ======================= команды (режиссёр сцены / сюжет) =======================
    public void play(Clip c) {
        for (Layer l : layers) if (l.clip.name().equals(c.name()) && !l.done(now) && Double.isNaN(l.stopAt)) return;
        layers.add(new Layer(c, now));
        idleSince = now;
    }

    public void stop(String name) {
        for (Layer l : layers) if (l.clip.name().equals(name) && Double.isNaN(l.stopAt)) l.stopAt = now + l.clip.fadeOut();
    }

    /** Сколько слоёв ещё звучит (автотест «нет застрявших анимаций»). */
    public int activeLayers() {
        return (int) layers.stream().filter(l -> !l.done(now)).count();
    }

    public boolean playing(String name) {
        for (Layer l : layers) if (l.clip.name().equals(name) && !l.done(now)) return true;
        return false;
    }

    /** Сказать реплику: рот по слогам, кивки и жесты руками на ударениях. Возвращает длительность, с. */
    public double say(String text) {
        line = text;
        mouth.clear();
        double t = 0.05;
        for (int i = 0; i < text.length(); i++) {
            char ch = Character.toLowerCase(text.charAt(i));
            if ("аеёиоуыэюяaeiouy".indexOf(ch) >= 0) {
                mouth.add(new double[]{t, 1});
                t += 0.085 + rnd.nextDouble() * 0.04;
                mouth.add(new double[]{t, 0.5});
                t += 0.02;
            } else if (Character.isLetter(ch)) {
                mouth.add(new double[]{t, 0.5});
                t += 0.045;
            } else if (ch == ' ') {
                mouth.add(new double[]{t, 0});
                t += 0.035;
            } else if (",.!?…—:;".indexOf(ch) >= 0) {
                mouth.add(new double[]{t, 0});
                t += ch == ',' ? 0.18 : 0.32;
            }
        }
        mouth.add(new double[]{t, 0});
        sayT0 = now;
        sayEnd = now + t;
        if (set.talk() != null) play(set.talk());
        return t;
    }

    public boolean speaking() {
        return now < sayEnd;
    }

    /** Вздрогнуть и резко обернуться к точке (звук за кадром). */
    public void startle(Vec3d at) {
        lookAt = at;
        lookSpeed = 16;
        if (Gestures.FLINCH != null) play(Gestures.FLINCH);
        blinkEnd = now + 0.12;
    }

    // ======================= кадр =======================
    double time(float pt) {
        if (clock != null) return clock.get();
        return (e.getWorld().getTime() + pt) / 20.0;
    }

    /** Из GeoModel.setCustomAnimations: база уже наложена GeckoLib, добавляем слои. */
    public void apply(AnimationProcessor<?> proc, float pt) {
        now = time(pt);
        double dt = last < 0 ? 0 : MathHelper.clamp(now - last, 0, 0.1);
        boolean fresh = now != last;
        last = now;
        if (fresh) update(dt, pt);
        touched.clear();

        // --- слои по возрастанию приоритета: старший ложится поверх ---
        layers.sort((a, b) -> Integer.compare(a.clip.priority(), b.clip.priority()));
        double look = 1;
        for (Layer l : layers) {
            double w = l.weight(now);
            if (w <= 0.001) continue;
            look = Math.min(look, 1 - w * (1 - l.clip.look()));
            tmp.clear();
            l.clip.body().at(now - l.t0, tmp, this);
            for (var en : tmp.m.entrySet()) {
                GeoBone b = proc.getBone(en.getKey());
                if (b == null) continue;
                float[] v = en.getValue();
                if (l.clip.own().contains(en.getKey())) {
                    var i = b.getInitialSnapshot();
                    b.setRotX(lerp(w, b.getRotX(), i.getRotX() + rad(v[0])));
                    b.setRotY(lerp(w, b.getRotY(), i.getRotY() + rad(v[1])));
                    b.setRotZ(lerp(w, b.getRotZ(), i.getRotZ() + rad(v[2])));
                    b.setPosX(lerp(w, b.getPosX(), v[3]));
                    b.setPosY(lerp(w, b.getPosY(), v[4]));
                    b.setPosZ(lerp(w, b.getPosZ(), v[5]));
                } else {
                    b.setRotX(b.getRotX() + (float) (rad(v[0]) * w));
                    b.setRotY(b.getRotY() + (float) (rad(v[1]) * w));
                    b.setRotZ(b.getRotZ() + (float) (rad(v[2]) * w));
                    if (v[3] != 0 || v[4] != 0 || v[5] != 0) {
                        b.setPosX(b.getPosX() + (float) (v[3] * w));
                        b.setPosY(b.getPosY() + (float) (v[4] * w));
                        b.setPosZ(b.getPosZ() + (float) (v[5] * w));
                    }
                }
                touched.add(b);
            }
        }
        lookW += (look - lookW) * Math.min(1, dt * 6);

        // --- дыхание: грудь, плечи; после ходьбы — чаще и глубже ---
        double rate = set.breath() * (1 + fatigue * 0.9);
        double br = Math.sin(now * rate * 2 * Math.PI);
        double depth = 1 + fatigue;
        add(proc, "chest", -1.1 * br * depth, 0, 0);
        addPos(proc, "right_arm", 0, 0.12 * br * depth, 0);
        addPos(proc, "left_arm", 0, 0.12 * br * depth, 0);
        add(proc, "head", 0.6 * br, 0, 0);

        // --- взгляд: голова 60 %, шея 25 %, грудь 15 %; наклон — голова 70 %, шея 30 % ---
        double ly = hy * lookW, lp = hp * lookW;
        add(proc, "chest", 0, ly * 0.15, 0);
        add(proc, "neck", lp * 0.3, ly * 0.25, 0);
        add(proc, "head", lp * 0.7 - jaw * 1.2 + stress * 2.5, ly * 0.6, 0);   // челюсть чуть поднимает подбородок, ударение — кивок

        for (GeoBone b : touched) b.resetStateChanges();   // иначе GeckoLib не вернёт кость в позу клипа на следующем кадре
    }

    void add(AnimationProcessor<?> proc, String bone, double x, double y, double z) {
        GeoBone b = proc.getBone(bone);
        if (b == null) return;
        b.setRotX(b.getRotX() + rad(x));
        b.setRotY(b.getRotY() + rad(y));
        b.setRotZ(b.getRotZ() + rad(z));
        touched.add(b);
    }

    void addPos(AnimationProcessor<?> proc, String bone, double x, double y, double z) {
        GeoBone b = proc.getBone(bone);
        if (b == null) return;
        b.setPosX(b.getPosX() + (float) x);
        b.setPosY(b.getPosY() + (float) y);
        b.setPosZ(b.getPosZ() + (float) z);
        touched.add(b);
    }

    void update(double dt, float pt) {
        // --- движение и состояние ---
        double x = MathHelper.lerp(pt, e.prevX, e.getX()), z = MathHelper.lerp(pt, e.prevZ, e.getZ());
        if (forcedSpeed >= 0) speed = forcedSpeed;
        else if (!Double.isNaN(lastX) && dt > 0) {
            double v = Math.hypot(x - lastX, z - lastZ) / dt;
            speed += (v - speed) * Math.min(1, dt * 8);
        }
        lastX = x;
        lastZ = z;
        boolean was = walking;
        if (!walking && speed > 0.35) walking = true;
        else if (walking && speed < 0.12) walking = false;
        if (was && !walking && set.settle() != null) play(set.settle());
        fatigue = MathHelper.clamp(fatigue + (walking ? 0.07 : -0.035) * dt, 0, 1);
        if (walking) idleSince = now;

        // --- случайные жесты на месте ---
        if (autoIdle && !walking && !speaking() && now - idleSince > nextIdle && layers.stream().noneMatch(l -> !l.done(now)) && !set.idles().isEmpty()) {
            Clip c;
            if (fatigue > 0.25 && set.tired() != null) c = set.tired();
            else {
                do c = set.idles().get(rnd.nextInt(set.idles().size())); while (set.idles().size() > 1 && c.name().equals(lastIdle));
            }
            lastIdle = c.name();
            play(c);
            nextIdle = c.len() + 3 + rnd.nextDouble() * 5;
        }

        // --- слои: звуки и уборка ---
        for (Layer l : layers) {
            if (l.clip.sounds() == null) continue;
            for (Object[] s : l.clip.sounds()) {
                double at = l.t0 + ((Number) s[0]).doubleValue();
                if (now - dt < at && now >= at) sound((String) s[1], s.length > 2 ? ((Number) s[2]).floatValue() : 0.8f);
            }
        }
        layers.removeIf(l -> l.done(now));

        // --- речь ---
        double open = 0;
        if (speaking()) {
            double t = now - sayT0;
            for (double[] k : mouth) if (k[0] <= t) open = k[1];
        }
        jaw += (open - jaw) * Math.min(1, dt * 25);
        stress += ((open >= 1 ? 1 : 0) - stress) * Math.min(1, dt * 3);
        if (!speaking() && set.talk() != null) stop(set.talk().name());

        // --- взгляд ---
        float body = MathHelper.lerpAngleDegrees(pt, e.prevBodyYaw, e.bodyYaw);
        Vec3d eye = new Vec3d(x, MathHelper.lerp(pt, e.prevY, e.getY()) + e.getStandingEyeHeight(), z);
        Vec3d tgt = lookAt;
        if (tgt == null && autoLook) {
            PlayerEntity best = null;
            double bd = 64;
            for (PlayerEntity p : e.getWorld().getPlayers()) {
                double d = p.squaredDistanceTo(e);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
            if (best != null) tgt = best.getLerpedPos(pt).add(0, best.getStandingEyeHeight(), 0);
        }
        double wy = 0, wp = 0;
        if (tgt != null) {
            Vec3d d = tgt.subtract(eye);
            double yawTo = Math.toDegrees(Math.atan2(-d.x, d.z));
            wy = MathHelper.wrapDegrees(yawTo - body);
            if (Math.abs(wy) > 115) wy = 0;          // за спиной — не выворачиваем шею
            wy = MathHelper.clamp(wy, -80, 80);
            wp = MathHelper.clamp(Math.toDegrees(Math.atan2(-d.y, Math.hypot(d.x, d.z))), -35, 40);
        }
        // глаза — сразу, голова догоняет пружиной (без перелёта); большой поворот — моргнуть
        double gap = wy - hy;
        if (Math.abs(gap) > 40 && now > blinkEnd + 0.4) blinkEnd = now + 0.11;
        int want = gap > 7 ? 1 : gap < -7 ? -1 : (Math.abs(gap) < 3 ? 0 : eyes);
        if (want != eyes && now > eyesUntil) {
            eyes = want;
            eyesUntil = now + 0.15;
        }
        int n = Math.max(1, (int) Math.ceil(dt * 240));
        double h = dt / n, w = lookSpeed;
        for (int i = 0; i < n; i++) {
            hyv += (w * w * (wy - hy) - 2 * w * hyv) * h;
            hy += hyv * h;
            hpv += (w * w * (wp - hp) - 2 * w * hpv) * h;
            hp += hpv * h;
        }
        lookSpeed += (7 - lookSpeed) * Math.min(1, dt * 1.5);

        // --- моргание: 2–6 с, иногда двойное ---
        if (now >= nextBlink) {
            blinkEnd = now + 0.12;
            blink2 = rnd.nextDouble() < 0.18 ? now + 0.3 : 0;
            nextBlink = now + 1.8 + rnd.nextDouble() * 4.2;
        }
        if (blink2 > 0 && now >= blink2) {
            blinkEnd = now + 0.1;
            blink2 = 0;
        }
    }

    void sound(String name, float vol) {
        SoundEvent ev = Registries.SOUND_EVENT.get(com.yompix.glukhoman.Glukhoman.id(name));
        Vec3d at = e.getPos().add(0, 1.5, 0);
        if (ev != null && MinecraftClient.getInstance().world != null)
            MinecraftClient.getInstance().world.playSound(at.x, at.y, at.z, ev, SoundCategory.NEUTRAL, vol, 0.95f + rnd.nextFloat() * 0.1f, false);
        if (onSound != null) onSound.play(name, at, vol);
    }

    /** Суффикс текстуры лица: моргание важнее рта, рот важнее глаз. */
    public String face() {
        if (now < blinkEnd) return "_blink";
        if (speaking()) {
            if (jaw > 0.7) return "_talk";
            if (jaw > 0.3) return "_half";
        }
        return eyes > 0 ? "_eyes_r" : eyes < 0 ? "_eyes_l" : "";
    }

    /** Базовый клип GeckoLib (ходьба/стойка) — плавный переход между ними делает контроллер. */
    public String base() {
        return walking ? set.walk() : set.idle();
    }

    public double speech() {
        return jaw;
    }

    static float rad(double d) {
        return (float) (d * MathHelper.RADIANS_PER_DEGREE);
    }

    static float lerp(double w, float a, double b) {
        return (float) (a + (b - a) * w);
    }

    static double smooth(double u) {
        return u * u * (3 - 2 * u);
    }
}
