package com.yompix.glukhoman.client;

import com.yompix.glukhoman.client.anim.AnimSet;
import com.yompix.glukhoman.client.anim.Animator;
import com.yompix.glukhoman.client.anim.Clip;
import com.yompix.glukhoman.client.anim.Gestures;
import com.yompix.glukhoman.entity.ResidentEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * Режиссёр кат-сцены (новая система, этап 3 — прототип): одна шкала времени для камеры, актёров, реплик, света и звука.
 *
 * <ul>
 *   <li>Камера — планы ({@link Shot}): ключи позиции/цели/FOV со сглаживанием по сплайну, «ручная» дрожь, склейки
 *       между планами; планы «из-за плеча» и «крупно» считаются от актёров на лету; камера не проходит сквозь стены
 *       (луч от цели к камере, при попадании — ближе к цели).</li>
 *   <li>Актёры ({@link Actor}) — куклы GeckoLib с {@link Animator}: ходят по пути с разгоном и торможением, плавно
 *       разворачиваются корпусом, смотрят глазами/головой на цель, говорят (рот по слогам), играют жесты-слои.</li>
 *   <li>События — по времени, ровно один раз; при записи видео пишутся в журнал (реплики и звуки — для сведения).</li>
 * </ul>
 * Время сцены одно у всех игроков (сервер говорит «играй X»), пропуск — Esc (в сюжете — через сервер, как раньше).
 */
final class Timeline implements Cine.Scene {
    final double len;
    final List<Actor> actors = new ArrayList<>();
    final List<Shot> shots = new ArrayList<>();
    final List<Object[]> events = new ArrayList<>();   // {t, Runnable}
    final List<DoubleConsumer> each = new ArrayList<>();
    final List<Runnable> cleanup = new ArrayList<>();
    Consumer<Timeline> setup;
    ClientWorld w;
    double t, prevT = -1;
    PrintWriter log;

    Timeline(double len, Consumer<Timeline> setup) {
        this.len = len;
        this.setup = setup;
    }

    // ======================= актёры =======================
    final class Actor {
        final String who, name;
        ResidentEntity e;
        Animator an;
        final List<double[]> path = new ArrayList<>();   // {t0, t1, индекс точек} — отрезки ходьбы
        final List<Vec3d[]> legs = new ArrayList<>();
        Vec3d pos;
        float yaw, yawV;
        Supplier<Vec3d> face, look;
        double lastT = -1;
        Vec3d lastP;

        Actor(String who, String name, Vec3d at, float yaw) {
            this.who = who;
            this.name = name;
            this.pos = at;
            this.yaw = yaw;
        }

        /** Пройти по точкам за [t0, t1]: разгон и торможение, корпус — по направлению шага. */
        Actor walk(double t0, double t1, Vec3d... pts) {
            path.add(new double[]{t0, t1, legs.size()});
            Vec3d[] p = new Vec3d[pts.length + 1];
            p[0] = null;
            System.arraycopy(pts, 0, p, 1, pts.length);
            legs.add(p);
            return this;
        }

        Vec3d head() {
            return pos.add(0, e != null ? e.getStandingEyeHeight() * (who.equals("vika") ? 0.86 : 1) : 1.6, 0);
        }

        void frame(double t) {
            // позиция: последний начавшийся отрезок пути
            Vec3d p = pos;
            boolean moving = false;
            Vec3d prevEnd = null;
            for (double[] seg : path) {
                Vec3d[] pts = legs.get((int) seg[2]);
                if (pts[0] == null) pts[0] = prevEnd != null ? prevEnd : pos0;
                if (t < seg[0]) break;
                double u = MathHelper.clamp((t - seg[0]) / (seg[1] - seg[0]), 0, 1);
                p = Cine.rail(walkEase(u, (seg[1] - seg[0])), pts);
                moving = u > 0 && u < 1;
                prevEnd = pts[pts.length - 1];
            }
            double dt = lastT < 0 ? 0 : t - lastT;
            double speed = lastP == null || dt <= 0 ? 0 : Math.hypot(p.x - lastP.x, p.z - lastP.z) / dt;
            // корпус: при ходьбе — по шагу, стоя — к цели «лицом»; пружиной (разворот на месте не мгновенный)
            float want = yaw;
            if (moving && speed > 0.15) want = (float) Math.toDegrees(Math.atan2(-(p.x - lastP.x), p.z - lastP.z));
            else if (face != null) want = Cine.yawTo(p, face.get());
            if (dt > 0) {
                double wn = moving ? 9 : 5;
                double d = MathHelper.wrapDegrees(want - yaw);
                yawV += (float) ((wn * wn * d - 2 * wn * yawV) * dt);
                yaw += (float) (yawV * dt);
            }
            pos = p;
            lastP = p;
            lastT = t;
            e.prevBodyYaw = e.bodyYaw;
            Cine.place(e, p.x, p.y, p.z, yaw);
            an.forcedSpeed = moving ? speed : 0;
            an.lookAt = look != null ? look.get() : null;
            e.animClock = t * 20;
        }

        Vec3d pos0;
    }

    static double walkEase(double u, double dur) {
        // разгон/торможение ~0.45 с, середина — ровным шагом
        double a = Math.min(0.45 / dur, 0.4);
        double v = 1 / (1 - a);   // скорость в середине
        if (u < a) return v * u * u / (2 * a);
        if (u > 1 - a) return 1 - v * (1 - u) * (1 - u) / (2 * a);
        return v * (u - a / 2);
    }

    Actor actor(String who, String name, Vec3d at, float yaw) {
        Actor a = new Actor(who, name, at, yaw);
        a.pos0 = at;
        actors.add(a);
        return a;
    }

    // ======================= камера =======================
    interface CamFn {
        /** u — 0…1 внутри плана; вернуть {позиция, цель}. */
        Vec3d[] at(double u, double t);
    }

    static final class Shot {
        final double t0, t1;
        final CamFn fn;
        float fov0 = 60, fov1 = 60, roll;
        double shake = 0.6;
        boolean avoid;
        String name = "";

        Shot(double t0, double t1, CamFn fn) {
            this.t0 = t0;
            this.t1 = t1;
            this.fn = fn;
        }

        Shot fov(float a, float b) {
            fov0 = a;
            fov1 = b;
            return this;
        }

        Shot shake(double s) {
            shake = s;
            return this;
        }

        Shot avoid() {
            avoid = true;
            return this;
        }

        Shot name(String n) {
            name = n;
            return this;
        }
    }

    Shot shot(double t0, double t1, CamFn fn) {
        Shot s = new Shot(t0, t1, fn);
        shots.add(s);
        return s;
    }

    /** Проезд: позиция по сплайну через точки, цель — своя функция; плавный старт и остановка. */
    Shot dolly(double t0, double t1, Supplier<Vec3d> look, Vec3d... pts) {
        return shot(t0, t1, (u, t) -> new Vec3d[]{Cine.rail(Cine.ease(u), pts), look.get()});
    }

    /** Из-за плеча: камера за плечом {@code over} (side: +1 — правое), смотрит на {@code at}. */
    Shot ots(double t0, double t1, Actor over, Actor at, double side, double back, double drift) {
        return shot(t0, t1, (u, t) -> {
            Vec3d h = over.head(), tg = at.head();
            Vec3d d = tg.subtract(h).multiply(1, 0, 1).normalize();
            Vec3d right = new Vec3d(-d.z, 0, d.x);
            Vec3d p = h.subtract(d.multiply(back + drift * u)).add(right.multiply(side * 0.42)).add(0, 0.12, 0);
            return new Vec3d[]{p, tg.add(0, -0.05, 0)};
        }).avoid();
    }

    /** Крупно лицо: спереди под углом {@code ang} к взгляду актёра, медленный наезд с dist0 до dist1. */
    Shot close(double t0, double t1, Actor a, double ang, double dist0, double dist1, double dy) {
        return shot(t0, t1, (u, t) -> {
            Vec3d h = a.head();
            float y = a.yaw + (float) ang;
            Vec3d fw = Vec3d.fromPolar(0, y);
            double d = MathHelper.lerp(Cine.ease(u), dist0, dist1);
            return new Vec3d[]{h.add(fw.multiply(d)).add(0, dy, 0), h.add(0, -0.05, 0)};
        }).avoid();
    }

    // ======================= события =======================
    Timeline at(double time, Runnable r) {
        events.add(new Object[]{time, r});
        return this;
    }

    /** Реплика: id — для перевода (assets/glukhoman/story/&lt;язык&gt;.json), текст — русский оригинал. */
    Timeline line(double time, Actor a, String id, String text) {
        return at(time, () -> {
            double d = a.an.say(text);
            String key = a.who.equals("@me") ? "me" : a.who;
            com.yompix.glukhoman.client.ui.Subtitles.push(key, com.yompix.glukhoman.client.ui.StoryLang.who(key, a.name),
                    com.yompix.glukhoman.client.ui.StoryLang.text(id, text), d + 0.6);
            log("LINE", String.format(Locale.ROOT, "%.3f\t%s\t%s\t%s", com.yompix.glukhoman.client.ui.Subtitles.readTime(text, d + 0.6), key, a.name, text));
        });
    }

    Timeline play(double time, Actor a, Clip c) {
        return at(time, () -> a.an.play(c));
    }

    Timeline stop(double time, Actor a, String clip) {
        return at(time, () -> a.an.stop(clip));
    }

    Timeline sound(double time, String name, Vec3d at, float vol) {
        return at(time, () -> snd(name, at, vol));
    }

    void snd(String name, Vec3d at, float vol) {
        Cine.sound(name, at, vol, 1f);
        double dist = at == null ? 0 : at.distanceTo(new Vec3d(Cine.cx, Cine.cy, Cine.cz));
        log("SND", String.format(Locale.ROOT, "%s\t%.2f\t%.1f", name, vol, dist));
    }

    void log(String kind, String rest) {
        if (log != null) log.println(String.format(Locale.ROOT, "%.3f\t%s\t%s", t, kind, rest));
    }

    // ======================= Cine.Scene =======================
    @Override
    public void start(ClientWorld w) {
        this.w = w;
        if (Recorder.on && Recorder.path != null) {
            try {
                log = new PrintWriter(new FileWriter(Recorder.path.replace(".mp4", ".events.tsv")), true);
            } catch (Exception ignored) {
            }
        }
        Animator.onSound = (n, at, v) -> log("SND", String.format(Locale.ROOT, "%s\t%.2f\t%.1f", n, v, at.distanceTo(new Vec3d(Cine.cx, Cine.cy, Cine.cz))));
        setup.accept(this);
        for (Actor a : actors) {
            a.e = Cine.resident(w, a.who, a.pos.x, a.pos.y, a.pos.z, a.yaw);
            AnimSet set = Gestures.SETS.getOrDefault(a.who, Gestures.GENERIC);
            a.an = Animator.of(a.e, set);
            a.an.clock = Cine::time;
            a.an.autoIdle = false;
            a.an.autoLook = false;
        }
        events.sort((x, y) -> Double.compare((Double) x[0], (Double) y[0]));
    }

    @Override
    public void frame(double now) {
        t = now;
        for (Object[] ev : events) {
            double at = (Double) ev[0];
            if (at <= now && at > prevT) ((Runnable) ev[1]).run();
        }
        prevT = now;
        for (Actor a : actors) a.frame(now);
        for (DoubleConsumer c : each) c.accept(now);
        Shot s = null;
        for (Shot x : shots) if (now >= x.t0 && now < x.t1) s = x;
        if (s == null && !shots.isEmpty()) s = shots.get(shots.size() - 1);
        if (s == null) return;
        double u = MathHelper.clamp((now - s.t0) / (s.t1 - s.t0), 0, 1);
        Vec3d[] pl = s.fn.at(u, now);
        Vec3d p = pl[0], look = pl[1];
        // «ручная» камера: несколько несоизмеримых синусов — плавно, без повторов на глаз
        double k = s.shake * 0.02 * shakeMul();
        p = p.add(k * (Math.sin(now * 0.83) + 0.5 * Math.sin(now * 2.07 + 1)), k * (Math.sin(now * 1.11 + 2) + 0.4 * Math.sin(now * 2.9)), k * Math.sin(now * 0.67 + 4));
        if (s.avoid) p = avoid(look, p);
        Cine.cam(p, look.add(k * 0.6 * Math.sin(now * 0.51), k * 0.6 * Math.sin(now * 0.73 + 1), 0));
        Cine.fov = (float) MathHelper.lerp(Cine.ease(u), s.fov0, s.fov1) * Cine.fovScale;
        Cine.roll = s.roll + (float) (s.shake * shakeMul() * 0.25 * Math.sin(now * 0.47));
    }

    /** Настройки: «Дрожь камеры в кат-сценах» и «Без тряски». */
    static double shakeMul() {
        var c = GConfig.get();
        return c.noShake ? 0 : c.cineShake / 100.0;
    }

    /** Камера не за стеной: луч от цели к камере; упёрся — встаём чуть перед препятствием. */
    Vec3d avoid(Vec3d look, Vec3d cam) {
        var hit = w.raycast(new RaycastContext(look, cam, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, MinecraftClient.getInstance().player));
        if (hit.getType() == HitResult.Type.MISS) return cam;
        Vec3d d = cam.subtract(look);
        double len = d.length();
        double keep = Math.max(0.3, hit.getPos().distanceTo(look) - 0.18);
        return look.add(d.multiply(keep / len));
    }

    @Override
    public void end() {
        for (Runnable r : cleanup) r.run();
        cleanup.clear();
        if (log != null) log.close();
        log = null;
        Animator.onSound = null;
    }

    // ======================= мир на время сцены =======================
    /** Дверь открыть/закрыть (только у себя, после сцены — как было). */
    void door(double time, BlockPos p, boolean open, boolean sound) {
        at(time, () -> {
            var st = w.getBlockState(p);
            if (!(st.getBlock() instanceof net.minecraft.block.DoorBlock)) return;
            boolean was = st.get(net.minecraft.block.DoorBlock.OPEN);
            ((net.minecraft.block.DoorBlock) st.getBlock()).setOpen(null, w, st, p, open);
            if (cleanup.stream().noneMatch(r -> r instanceof DoorReset dr && dr.p.equals(p))) cleanup.add(new DoorReset(w, p, was));
            if (sound) snd("door_creak", Vec3d.ofCenter(p), 0.8f);
        });
    }

    record DoorReset(ClientWorld w, BlockPos p, boolean open) implements Runnable {
        public void run() {
            var st = w.getBlockState(p);
            if (st.getBlock() instanceof net.minecraft.block.DoorBlock d) d.setOpen(null, w, st, p, open);
        }
    }

    /** Лампа (LampBlock) — включить/выключить в момент времени; после сцены вернуть. */
    void lamp(double time, BlockPos p, boolean lit) {
        at(time, () -> {
            if (GConfig.get().noFlashing && t > 0.5 && !lit) return;   // «Без вспышек»: свет в сцене не мигает
            var st = w.getBlockState(p);
            if (st.getBlock() instanceof com.yompix.glukhoman.block.LampBlock) {
                boolean was = st.get(com.yompix.glukhoman.block.LampBlock.LIT);
                Cine.lamp(w, p, lit);
                cleanup.add(() -> Cine.lamp(w, p, was));
            }
        });
    }

    static Vec3d v(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    static Clip point(double hold) {
        return Gestures.point(hold);
    }
}
