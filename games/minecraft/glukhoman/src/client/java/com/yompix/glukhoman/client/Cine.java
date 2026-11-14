package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.block.LampBlock;
import com.yompix.glukhoman.client.screen.CineScreen;
import com.yompix.glukhoman.entity.AnnaEntity;
import com.yompix.glukhoman.entity.DoppelEntity;
import com.yompix.glukhoman.entity.HorrorMob;
import com.yompix.glukhoman.story.Scenes;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Кат-сцены уровня машинимы, целиком на клиенте: камера по сплайну с плавными кривыми (поза считается каждый кадр
 * по реальному времени, без рывков по тикам), наклон горизонта, FOV и «долли-зум», замедленная съёмка, размытие фона,
 * лучи света, цветокоррекция, актёры-куклы (GeckoLib) только на клиенте — у всех игроков одинаково.
 * Сервер лишь говорит «играй сцену X» и ждёт её длину (или пропуска).
 */
public final class Cine {
    private Cine() {}

    public static boolean active;
    static String id = "";
    static long startNs;
    static float len;
    static Scene scene;
    // поза камеры (читает CameraMixin / FovMixin / ShakeMixin)
    public static double cx, cy, cz;
    public static float yaw, pitch, roll, fov = 70;
    // пост-эффекты (читает FearFx)
    public static float blur, rays, rayX = 0.5f, rayY = 0.25f, tear, warp, tintR = 1, tintG = 1, tintB = 1, flash, letter;
    public static String caption = "";
    static final List<Entity> PUPPETS = new ArrayList<>();
    static final List<BlockPos> KEYS = new ArrayList<>();
    static final Set<String> FIRED = new HashSet<>();
    static boolean savedHud;
    static int nextId = -880000;
    static final Random RND = Random.create();

    interface Scene {
        void start(ClientWorld w);

        void frame(double t);
    }

    // ======================= управление =======================
    public static void play(String sid) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        stop();
        scene = make(sid);
        if (scene == null) return;
        id = sid;
        len = Scenes.LEN.getOrDefault(sid, 5f);
        startNs = System.nanoTime();
        clockTicks = 0;
        active = true;
        FIRED.clear();
        blur = rays = tear = warp = flash = 0;
        fogS = 0;
        recFrame = 0;
        tintR = tintG = tintB = 1;
        fov = 70;
        roll = 0;
        caption = "";
        savedHud = c.options.hudHidden;
        c.options.hudHidden = true;
        scene.start(c.world);
        scene.frame(0);
        c.setScreen(new CineScreen());
        Glukhoman.LOG.info("Кат-сцена {}", sid);
    }

    public static void stop() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (!active) return;
        Glukhoman.LOG.info("Кат-сцена {} закончилась на {} с", id, String.format("%.1f", time()));
        if (FIXED) Glukhoman.LOG.info("стек остановки", new Throwable());
        active = false;
        for (Entity e : PUPPETS) if (c.world != null) c.world.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
        PUPPETS.clear();
        if (c.world != null) for (BlockPos p : KEYS) if (c.world.getBlockState(p).isOf(net.minecraft.block.Blocks.LIGHT)) c.world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
        KEYS.clear();
        c.options.hudHidden = savedHud;
        blur = rays = tear = warp = flash = 0;
        tintR = tintG = tintB = 1;
        if (c.currentScreen instanceof CineScreen) c.setScreen(null);
    }

    /** Для записи видео автотестом: время сцены идёт по тикам (кадр на тик), а не по часам. */
    public static final boolean FIXED = System.getProperty("glukhoman.autotest") != null;
    public static int clockTicks;

    /** Запись трейлера: кадр за кадром с шагом 1/recFps (FearFx/рекордер двигают recFrame). */
    public static int recFps, recFrame;
    public static float fovScale = 1f;
    /** Свой туман сцены (если start > 0). */
    public static float fogS, fogE, fogR, fogG, fogB;

    public static double time() {
        if (recFps > 0) return recFrame / (double) recFps;
        if (FIXED) return (clockTicks + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false)) / 20.0;
        return (System.nanoTime() - startNs) / 1e9;
    }

    /** Каждый кадр (из CameraMixin): поза камеры, актёры, эффекты. */
    public static void frame() {
        if (!active || scene == null) return;
        double t = time();
        if (t > len + 0.3) {
            stop();
            return;
        }
        double lb = Math.min(1, Math.min(t / 0.8, (len - t) / 0.8));
        letter = (float) MathHelper.clamp(lb, 0, 1);
        scene.frame(t);
    }

    // ======================= инструменты =======================
    static double ease(double u) {          // плавный старт и остановка (smootherstep)
        u = MathHelper.clamp(u, 0, 1);
        return u * u * u * (u * (u * 6 - 15) + 10);
    }

    static double seg(double t, double a, double b) {
        return MathHelper.clamp((t - a) / (b - a), 0, 1);
    }

    /** Сплайн Катмулла — Рома по точкам. */
    static Vec3d rail(double u, Vec3d... p) {
        u = MathHelper.clamp(u, 0, 1) * (p.length - 1);
        int i = Math.min(p.length - 2, (int) Math.floor(u));
        double f = u - i;
        Vec3d p0 = p[Math.max(0, i - 1)], p1 = p[i], p2 = p[i + 1], p3 = p[Math.min(p.length - 1, i + 2)];
        double f2 = f * f, f3 = f2 * f;
        return p1.multiply(2).add(p2.subtract(p0).multiply(f)).add(p0.multiply(2).subtract(p1.multiply(5)).add(p2.multiply(4)).subtract(p3).multiply(f2))
                .add(p3.subtract(p0).add(p1.multiply(3)).subtract(p2.multiply(3)).multiply(f3)).multiply(0.5);
    }

    static void cam(Vec3d pos, Vec3d look) {
        cx = pos.x;
        cy = pos.y;
        cz = pos.z;
        Vec3d d = look.subtract(pos);
        yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
    }

    static Vec3d v(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    static boolean once(String key, double t, double at) {
        return t >= at && FIRED.add(key);
    }

    static void sound(String name, Vec3d at, float vol, float pitch) {
        SoundEvent e = Registries.SOUND_EVENT.get(Glukhoman.id(name));
        if (e == null) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (at == null) c.getSoundManager().play(PositionedSoundInstance.master(e, pitch, vol));
        else c.getSoundManager().play(new PositionedSoundInstance(e, SoundCategory.AMBIENT, vol, pitch, RND, at.x, at.y, at.z));
    }

    @SuppressWarnings("unchecked")
    static <T extends Entity> T puppet(ClientWorld w, EntityType<T> type, double x, double y, double z, float yaw) {
        T e = type.create(w);
        if (e == null) return null;
        e.setId(nextId--);
        e.setNoGravity(true);
        e.noClip = true;
        place(e, x, y, z, yaw);
        w.addEntity(e);
        PUPPETS.add(e);
        return e;
    }

    /** Поставить актёра точно, без межтиковой интерполяции (позу считаем каждый кадр сами). */
    static void place(Entity e, double x, double y, double z, float yaw) {
        e.setPosition(x, y, z);
        e.prevX = e.lastRenderX = x;
        e.prevY = e.lastRenderY = y;
        e.prevZ = e.lastRenderZ = z;
        e.setYaw(yaw);
        e.prevYaw = yaw;
        if (e instanceof LivingEntity l) {
            l.bodyYaw = l.prevBodyYaw = yaw;
            l.headYaw = l.prevHeadYaw = yaw;
        }
    }

    static void act(Entity e, String a, double clockSec) {
        if (e instanceof HorrorMob m) {
            if (!m.act().equals(a)) m.act(a, 0);
            m.animClock = clockSec * 20;
        } else if (e instanceof AnnaEntity an) {
            if (!an.act().equals(a)) an.act(a);
            an.animClock = clockSec * 20;
        }
    }

    /** Ключевой свет кадра: невидимый источник только на этом клиенте. */
    static void key(ClientWorld w, int x, int y, int z, int level) {
        BlockPos p = new BlockPos(x, y, z);
        if (!w.getBlockState(p).isAir()) return;
        w.setBlockState(p, net.minecraft.block.Blocks.LIGHT.getDefaultState().with(net.minecraft.block.LightBlock.LEVEL_15, level), 3);
        KEYS.add(p);
    }

    static int ground(ClientWorld w, double x, double z) {
        return w.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(x), MathHelper.floor(z));   // на клиенте есть только этот
    }

    static void lamp(ClientWorld w, BlockPos p, boolean lit) {
        BlockState s = w.getBlockState(p);
        if (s.getBlock() instanceof LampBlock && s.get(LampBlock.LIT) != lit) w.setBlockState(p, s.with(LampBlock.LIT, lit), 3);
    }

    // ======================= сцены =======================
    static Scene make(String sid) {
        return switch (sid) {
            case "intro" -> new Intro();
            case "knock" -> new Knock();
            case "village" -> new Village();
            case "corridor" -> new Corridor();
            case "anna" -> new Anna();
            case "bunker" -> new Bunker();
            case "endA" -> new EndA();
            case "endB" -> new EndB();
            case "tr_radio" -> new TrRadio();
            case "tr_gore" -> new TrGore();
            case "death_pole" -> new Death(GContent.POLE, 3.6, "stinger");
            case "death_listener" -> new Death(GContent.LISTENER, 0.9, "listener_scream");
            case "death_choir" -> new Death(GContent.CHOIR, 2.9, "choir_roar");
            case "death_human" -> new Death(GContent.DOPPEL, 1.6, "stinger");
            case "tr_path" -> new TrPath();
            case "tr_listener" -> new TrListener();
            case "tr_doppel" -> new TrDoppel();
            case "tr_scare" -> new TrScare();
            case "tt_pole" -> new Turntable(GContent.POLE, 4.6, 2.2, 3.6);
            case "tt_listener" -> new Turntable(GContent.LISTENER, 3.0, 0.7, 0.85);
            case "tt_choir" -> new Turntable(GContent.CHOIR, 6.5, 2.0, 2.9);
            default -> null;
        };
    }

    // ======================= сцены трейлера =======================
    static void fog(float s, float e, float r, float g, float b) {
        fogS = s; fogE = e; fogR = r; fogG = g; fogB = b;
    }

    /** 18+: изба охотника — то, что от него осталось. */
    static class TrGore implements Scene {
        public void start(ClientWorld w) {
            key(w, -9, 67, -93, 11);
        }

        public void frame(double t) {
            double u = ease(t / 6);
            cam(v(-7.2, 66.7, -93.2).lerp(v(-8.6, 66.3, -92.4), u), v(-10.8, 65.2, -93.0).lerp(v(-9.6, 65.1, -93.8), u));
            fov = (float) MathHelper.lerp(u, 58, 44);
            tintR = 1.12f; tintG = 0.9f; tintB = 0.85f;
            blur = 0.25f;
        }
    }

    /** Смерть от существа: падаешь, оно над тобой — бросок, тьма. */
    static class Death implements Scene {
        final EntityType<? extends Entity> type;
        final double head;
        final String snd;
        Entity k;
        Vec3d at;
        float y0;

        Death(EntityType<? extends Entity> type, double head, String snd) {
            this.type = type;
            this.head = head;
            this.snd = snd;
        }

        public void start(ClientWorld w) {
            var p = MinecraftClient.getInstance().player;
            at = p.getPos();
            y0 = p.getYaw();
            Vec3d f = Vec3d.fromPolar(0, y0).multiply(1.5);
            k = puppet(w, type, at.x + f.x, at.y, at.z + f.z, y0 + 180);
            if (k instanceof DoppelEntity d) d.setMimic("@anna");
        }

        public void frame(double t) {
            double fall = ease(seg(t, 0, 0.9));
            Vec3d f = Vec3d.fromPolar(0, y0);
            Vec3d eye = at.add(0, MathHelper.lerp(fall, 1.62, 0.25), 0).subtract(f.multiply(0.3 * fall));
            cam(eye, at.add(f.multiply(1.5)).add(0, head * (0.6 + 0.4 * fall), 0));
            roll = (float) (fall * 62);
            fov = (float) MathHelper.lerp(seg(t, 1.2, 2.6), 70, 52);
            act(k, t < 1.2 ? "attack" : "jumpscare", t < 1.2 ? t : t - 1.2);
            place(k, at.x + f.x * 1.5, at.y, at.z + f.z * 1.5, y0 + 180);
            if (once("s", t, 0.1)) sound(snd, null, 1.2f, 0.9f);
            if (once("b", t, 2.3)) Jumpscare.splatter();
            flash = 0;
            tintR = 1.2f; tintG = (float) (1 - 0.5 * seg(t, 2, 3.5)); tintB = tintG;
            GlukhomanClient.shake = (float) Math.max(GlukhomanClient.shake, t > 2.2 ? 1.6 : 0.4);
        }
    }

    /** Наезд на рацию Анны в тёмной избе. */
    static class TrRadio implements Scene {
        public void start(ClientWorld w) {
            key(w, -12, 67, 127, 9);
        }

        public void frame(double t) {
            double u = ease(t / 6);
            cam(v(-11.2, 67.1, 129.0).lerp(v(-13.9, 66.7, 127.0), u), v(-15, 66.35, 126.5));
            fov = (float) MathHelper.lerp(u, 52, 34);
            blur = (float) (0.2 + 0.3 * u);
            tintR = 1.1f; tintG = 0.95f; tintB = 0.85f;
        }
    }

    /** Просека в тумане: камера идёт по тропе, из-за ели выглядывает Жердь. */
    static class TrPath implements Scene {
        Entity pole;
        double gy;

        public void start(ClientWorld w) {
            gy = ground(w, -3.0, -71.5);
            pole = puppet(w, GContent.POLE, -3.0, gy, -71.5, -160);
        }

        public void frame(double t) {
            double u = ease(t / 6);
            Vec3d p = v(1.5, ground(MinecraftClient.getInstance().world, 1, -58) + 1.7, -57.5).lerp(v(0.8, ground(MinecraftClient.getInstance().world, 1, -64) + 1.7, -63.5), u);
            cam(p.add(Math.sin(t * 2.1) * 0.05, Math.abs(Math.sin(t * 4.2)) * 0.05, 0), v(-2.6 + Math.sin(t * 0.5) * 0.3, gy + 2.6, -71.5));
            roll = (float) (Math.sin(t * 1.3) * 1.0);
            fov = 58;
            fog(1.5f, 22f, 0.16f, 0.17f, 0.19f);
            act(pole, "peek", t);
            place(pole, -3.0, gy, -71.5, -160);
        }
    }

    /** Штольня: Слухач несётся на камеру по рельсам. */
    static class TrListener implements Scene {
        Entity l;

        public void start(ClientWorld w) {
            l = puppet(w, GContent.LISTENER, -100.5, 25, -172.5, 90);
            key(w, -116, 27, -173, 11);
            key(w, -108, 27, -173, 7);
        }

        public void frame(double t) {
            cam(v(-120.5, 25.9, -172.6), v(-110, 25.8, -172.6));
            fov = 62;
            double u = seg(t, 1.2, 4.6);
            act(l, t < 1.2 ? "listen" : "sprint", t);
            place(l, MathHelper.lerp(u * u, -100.5, -117.5), 25, -172.5, 90);
            warp = (float) (0.5 * u);
            tintR = 1.05f; tintG = 0.95f; tintB = 0.9f;
        }
    }

    /** Ночная улица: «Анна» стоит спиной; голова поворачивается к нам. */
    static class TrDoppel implements Scene {
        DoppelEntity d;

        public void start(ClientWorld w) {
            d = puppet(w, GContent.DOPPEL, 0.5, 65, 92.5, 180);
            if (d != null) {
                d.setMimic("@anna");
                d.setCustomNameVisible(false);
            }
            key(w, 1, 67, 95, 10);
        }

        public void frame(double t) {
            double u = ease(t / 5);
            cam(v(1.6, 66.8, 104).lerp(v(1.0, 66.7, 97.5), u), v(0.5, 66.4, 92.5));
            fov = (float) MathHelper.lerp(u, 50, 36);
            act(d, t < 2.2 ? "still" : "turn180", t < 2.2 ? t : (t - 2.2) * 0.5);
            place(d, 0.5, 65, 92.5, 180);
            tintR = 0.85f; tintG = 0.92f; tintB = 1.15f;
        }
    }

    /** Финальный скример: тьма, тишина… и лицо Жерди вплотную. */
    static class TrScare implements Scene {
        Entity p;
        ClientWorld w;

        public void start(ClientWorld w) {
            this.w = w;
            p = puppet(w, GContent.POLE, -40, 65, -241.5, -90);
        }

        public void frame(double t) {
            cam(v(2.0, 66.6, -241.5), v(-6, 66.4, -241.5));
            fov = 60;
            boolean show = t > 2.4;
            lamp(w, new BlockPos(-10, 69, -241), t > 0.4 && t < 0.55 || show);
            if (show && once("k", t, 2.4)) key(w, 0, 67, -241, 14);
            act(p, "jumpscare", Math.max(0, t - 2.4));
            place(p, show ? 0.7 - Math.min(0.5, (t - 2.4) * 2) * 0 : -40, 65 - (show ? 1.35 : 0), -241.5, -90);
            if (show) GlukhomanClient.shake = 2f;
            warp = show ? 0.8f : 0;
        }
    }

    /** Смотровая сцена для проверки моделей: облёт на 360°, ходьба, крупный план лица со скримером. */
    static class Turntable implements Scene {
        final EntityType<? extends Entity> type;
        final double dist, mid, head;
        Entity e;
        static final double X = 0.5, Z = -224.5;
        double y;

        Turntable(EntityType<? extends Entity> type, double dist, double mid, double head) {
            this.type = type;
            this.dist = dist;
            this.mid = mid;
            this.head = head;
        }

        public void start(ClientWorld w) {
            y = ground(w, X, Z);
            e = puppet(w, type, X, y, Z, 0);
            key(w, 0, (int) y + 3, -227, 13);
        }

        public void frame(double t) {
            if (t < 6) {
                double a = t / 6 * Math.PI * 2;
                cam(v(X + Math.sin(a) * dist, y + mid + 1.4, Z - Math.cos(a) * dist), v(X, y + mid, Z));
                act(e, "idle", t);
            } else if (t < 9) {
                cam(v(X + dist * 0.7, y + mid, Z - dist * 0.7), v(X, y + mid, Z));
                act(e, e.getType() == GContent.LISTENER ? "crawl" : "walk", t - 6);
            } else {
                cam(v(X, y + head + 0.1, Z + 1.9 + head * 0.25), v(X, y + head, Z));
                act(e, t < 10 ? "still" : "jumpscare", t < 10 ? t - 9 : t - 10);
            }
            fov = t < 9 ? 55 : 60;
            place(e, X, y, Z, 0);
        }
    }

    /** Глава I. Кран над рекой, мимо УАЗа, вверх по дороге; у кромки леса стоит фигура и исчезает. */
    static class Intro implements Scene {
        Entity pole;
        double ground;

        public void start(ClientWorld w) {
            ground = ground(w, -7, 196);
            pole = puppet(w, GContent.POLE, -7.5, ground, 196.5, 0);
        }

        public void frame(double t) {
            double u = ease(t / 13.0);
            cam(rail(u, v(2, 88, 290), v(9, 76, 266), v(9, 68.5, 247), v(3, 67.4, 233), v(1, 67.2, 222)),
                    rail(u, v(0, 64, 252), v(1, 65, 238), v(5, 66, 236), v(0, 67, 190), v(-3, 67.5, 196)));
            roll = (float) (Math.sin(t * 0.4) * 1.2 * (1 - u));
            fov = (float) MathHelper.lerp(ease(seg(t, 9, 13)), 62, 48);
            tintR = 0.95f; tintG = 0.98f; tintB = 1.1f;
            rays = (float) (0.35 * (1 - u));
            rayX = 0.15f; rayY = 0.2f;
            caption = t > 2.5 && t < 8 ? "ГЛУХОМАНЬ" : "";
            if (once("wind", t, 0)) sound("wind_gust", null, 0.8f, 1f);
            if (once("hit", t, 2.5)) sound("title_hit", null, 1f, 1f);
            if (pole != null) {
                act(pole, t < 11 ? "still" : "vanish", t);
                place(pole, -7.5, ground, 196.5, 0);
                if (once("pole", t, 11)) sound("pole_vanish", pole.getPos(), 1f, 0.8f);
            }
        }
    }

    /** Стук. Снаружи у двери стоит «Анна» и стучит. Замедление: голова поворачивается на 180° прямо в камеру, «долли-зум». */
    static class Knock implements Scene {
        DoppelEntity d;

        public void start(ClientWorld w) {
            d = puppet(w, GContent.DOPPEL, -7.45, 65, 128.5, 90);
            key(w, -5, 67, 126, 11);
            if (d != null) {
                d.setMimic("@anna");
                d.setCustomNameVisible(false);
            }
        }

        public void frame(double t) {
            // до 5.5 с — медленный наезд; потом долли-зум: камера отъезжает, угол сужается — фон «наваливается»
            double push = ease(seg(t, 0, 5.5));
            double dz = ease(seg(t, 5.5, 9));
            Vec3d look = v(-7.5, 66.9, 128.5);
            Vec3d p0 = v(0.5, 67.6, 121.5), p1 = v(-3.4, 67.1, 125.4), p2 = v(-0.8, 67.3, 123.2);
            Vec3d pos = t < 5.5 ? p0.lerp(p1, push) : p1.lerp(p2, dz);
            cam(pos, look);
            fov = (float) (t < 5.5 ? 60 : MathHelper.lerp(dz, 60, 30));
            roll = (float) (t > 5.5 ? Math.sin(t * 1.3) * 2.5 * dz : 0);
            blur = (float) (0.1 + 0.3 * dz);
            tintR = 0.85f; tintG = 0.92f; tintB = 1.15f;
            // замедленная съёмка: время анимации идёт втрое медленнее
            double clock = t < 5.5 ? t : 5.5 + (t - 5.5) * 0.33;
            if (d != null) {
                act(d, t < 5.5 ? "knock" : "turn180", t < 5.5 ? clock : (t - 5.5) * 0.33 * 1.0 + 0.0001);
                place(d, -7.45, 65, 128.5, 90);
            }
            for (double k : new double[]{0.6, 2.0, 3.4}) if (once("k" + k, t, k)) sound("knock", v(-9, 66, 128), 1.2f, 1f);
            if (once("slow", t, 5.5)) sound("slowmo", null, 0.9f, 1f);
            if (once("sting", t, 8.3)) sound("stinger_soft", null, 1f, 0.8f);
            flash = (float) Math.max(0, 1 - Math.abs(t - 9.2) * 3) * 0.0f;
        }
    }

    /** Глава II. Один непрерывный кадр вдоль улицы: фонари гаснут дом за домом прямо перед камерой. */
    static class Village implements Scene {
        final List<BlockPos> lamps = new ArrayList<>();
        Entity crawler;
        ClientWorld w;

        public void start(ClientWorld w) {
            this.w = w;
            for (BlockPos p : BlockPos.iterate(-26, 64, 36, 26, 70, 168)) if (w.getBlockState(p).getBlock() instanceof LampBlock) lamps.add(p.toImmutable());
            for (BlockPos p : lamps) lamp(w, p, true);
            crawler = puppet(w, GContent.LISTENER, -12, 65, 62, -90);
        }

        public void frame(double t) {
            double u = ease(t / 13.5);
            Vec3d pos = rail(u, v(0.5, 68.5, 162), v(1.5, 67.2, 140), v(-0.5, 66.9, 115), v(1.0, 66.8, 92), v(0, 66.9, 74));
            cam(pos.add(Math.sin(t * 0.9) * 0.15, Math.sin(t * 1.7) * 0.06, 0), pos.add(Math.sin(t * 0.35) * 2.5, -0.4, -12));
            roll = (float) (Math.sin(t * 0.6) * 1.5);
            fov = 58;
            tintR = 0.8f; tintG = 0.9f; tintB = 1.2f;
            for (BlockPos p : lamps) {
                double ahead = pos.z - p.getZ();
                if (ahead < 7 && once("off" + p.asLong(), t, 0)) {
                    lamp(w, p, false);
                    sound("lamp_buzz", Vec3d.ofCenter(p), 0.8f, 0.8f);
                }
            }
            if (crawler != null) {
                double cu = ease(seg(t, 11, 14.5));
                act(crawler, cu > 0 && cu < 1 ? "sprint" : "listen", t);
                place(crawler, MathHelper.lerp(cu, -12, 12), 65, 62, -90);
                if (once("scr", t, 11)) sound("listener_scream", crawler.getPos(), 1f, 0.9f);
            }
            if (once("wind", t, 0.2)) sound("wind_gust", null, 0.7f, 0.9f);
        }
    }

    /** Погоня: в тёмном коридоре станции мигает лампа, Жердь разворачивается из угла и несётся на камеру (рапид). */
    static class Corridor implements Scene {
        Entity pole;
        ClientWorld w;
        final BlockPos lampPos = new BlockPos(-10, 69, -241);

        public void start(ClientWorld w) {
            this.w = w;
            pole = puppet(w, GContent.POLE, -14.5, 65, -241.5, -90);
            key(w, -12, 68, -242, 9);
            key(w, -3, 68, -241, 7);
        }

        public void frame(double t) {
            Vec3d look = v(-10, 66.4, -241.5);
            cam(v(MathHelper.lerp(ease(seg(t, 0, 4)), 3.2, 1.8), 66.7, -241.5), look);
            fov = 64;
            boolean lit = (int) (t * 7) % 5 != 0 && t > 0.6 && (t < 4.4 || (int) (t * 13) % 2 == 0);
            lamp(w, lampPos, lit);
            tintR = 1.05f; tintG = 0.95f; tintB = 0.95f;
            double run = seg(t, 4.5, 7.0);
            double slow = t > 5.8 && t < 6.5 ? 0.3 : 1;   // рапид в середине рывка
            double clock = t < 5.8 ? t : t < 6.5 ? 5.8 + (t - 5.8) * 0.3 : 6.01 + (t - 6.5);
            double x = MathHelper.lerp(run < 0.52 ? run : 0.52 + (run - 0.52) * slow, -14.5, 0.5);
            if (pole != null) {
                act(pole, t < 1.4 ? "spawn" : t < 4.5 ? "tilt" : "run", t < 1.4 ? clock : clock - (t < 4.5 ? 1.4 : 4.5));
                place(pole, x, 65, -241.5, -90);
            }
            warp = (float) (0.4 * run);
            if (once("step", t, 1)) sound("pole_appear", v(-14, 66, -241), 1f, 1f);
            if (once("run", t, 4.5)) sound("listener_scream", v(-12, 66, -241), 1f, 0.5f);
            if (once("jump", t, 6.9)) {
                Jumpscare.start("pole");
                lamp(w, lampPos, false);
            }
        }
    }

    /** Анна в убежище: пишет, поднимает глаза, показывает на выход; пыль с потолка — Хор идёт. */
    static class Anna implements Scene {
        AnnaEntity a;
        ClientWorld w;

        public void start(ClientWorld w) {
            this.w = w;
            a = puppet(w, GContent.ANNA, -110.5, 25, -222.5, 200);
            key(w, -108, 27, -220, 12);
        }

        public void frame(double t) {
            double u = ease(t / 12);
            Vec3d look = rail(u, v(-110.5, 26.4, -222.5), v(-110.5, 26.6, -222.5), v(-110, 26.4, -219), v(-110, 26.5, -215));
            cam(rail(u, v(-106.8, 26.7, -218.6), v(-107.6, 26.6, -219.8), v(-108.2, 26.5, -219.0), v(-106.5, 26.6, -219.5)), look);
            fov = (float) MathHelper.lerp(ease(seg(t, 0, 4)), 50, 42);
            blur = (float) (t < 4 ? 0.5 : 0.15);   // перевод фокуса: сначала только её руки
            tintR = 1.12f; tintG = 1.0f; tintB = 0.85f;
            if (a != null) {
                a.talking = t > 4 && t < 7;
                act(a, t < 4 ? "write" : t < 7.3 ? "point" : "upset", t < 4 ? t : t < 7.3 ? t - 4 : t - 7.3);
                place(a, -110.5, 25, -222.5, t < 4 ? 200 : 180 + (float) (ease(seg(t, 4, 5)) * 20));
            }
            caption = t > 4 && t < 9 ? "Анна пишет мелом на стене: «ОНО ИДЁТ. БЕГИТЕ К ЛИФТУ»" : "";
            if (t > 7.5) {
                GlukhomanClient.shake = Math.max(GlukhomanClient.shake, (float) (0.6 * (1 - seg(t, 7.5, 11))));
                if (RND.nextInt(3) == 0) w.addParticle(ParticleTypes.ASH, -110 + RND.nextGaussian() * 2, 28.8, -221 + RND.nextGaussian() * 2, 0, -0.05, 0);
            }
            if (once("rum", t, 7.5)) sound("choir_roar", v(-100, 26, -200), 1.4f, 0.6f);
        }
    }

    /** Бункер: Хор поднимается из пола под лучами ламп, лица открывают рты. */
    static class Bunker implements Scene {
        Entity choir;

        public void start(ClientWorld w) {
            choir = puppet(w, GContent.CHOIR, 14.5, 42, -268.5, 0);
            key(w, 14, 47, -263, 13);
            key(w, 10, 44, -266, 8);
            key(w, 19, 44, -266, 8);
        }

        public void frame(double t) {
            double u = ease(t / 9);
            cam(rail(u, v(14.5, 44.2, -253.2), v(13.8, 43.6, -256.5), v(15.2, 43.2, -259.5)), v(14.5, 45.2 + u, -268.5));
            fov = (float) MathHelper.lerp(u, 66, 52);
            rays = 0.55f;
            rayX = 0.5f;
            rayY = 0.08f;
            tintR = 0.9f; tintG = 0.8f; tintB = 1.15f;
            if (choir != null) {
                act(choir, t < 2.4 ? "spawn" : "roar", t < 2.4 ? t : t - 2.4);
                place(choir, 14.5, 42, -268.5, 0);
            }
            if (once("roar", t, 2.6)) sound("choir_roar", v(14.5, 44, -268.5), 2f, 0.9f);
            if (t > 2.6 && t < 6) GlukhomanClient.shake = Math.max(GlukhomanClient.shake, 0.7f);
            warp = (float) (t > 2.6 && t < 6 ? 0.5 : 0);
        }
    }

    /** «Тишина»: рассвет, кран от мачты над станцией; разрыв в небе затягивается. */
    static class EndA implements Scene {
        AnnaEntity a;

        public void start(ClientWorld w) {
            a = puppet(w, GContent.ANNA, 2.5, 65, -216.5, 160);
        }

        public void frame(double t) {
            double u = ease(t / 14);
            cam(rail(u, v(16, 67, -235), v(22, 78, -228), v(36, 94, -210), v(44, 101, -196)), rail(u, v(18, 84, -250), v(14, 76, -246), v(4, 68, -236), v(2, 66, -230)));
            fov = 60;
            tear = (float) (1 - ease(seg(t, 1.5, 10)));
            rays = 0.6f;
            rayX = 0.8f;
            rayY = 0.15f;
            tintR = 1.12f; tintG = 1.04f; tintB = 0.92f;
            if (a != null) {
                act(a, t < 6 ? "think" : "wave", t < 6 ? t : t - 6);
                place(a, 2.5, 65, -216.5, 160);
            }
            if (once("relief", t, 0.5)) sound("title_hit", null, 0.7f, 1.3f);
        }
    }

    /** «Эхо»: год спустя, дом Анны, наезд на рацию; в дверях стоит кто-то в твоём облике. */
    static class EndB implements Scene {
        DoppelEntity d;

        public void start(ClientWorld w) {
            d = puppet(w, GContent.DOPPEL, -9.5, 65, 128.5, 90);
            MinecraftClient c = MinecraftClient.getInstance();
            if (d != null && c.player != null) d.setMimic(c.player.getName().getString());
        }

        public void frame(double t) {
            double push = ease(seg(t, 0, 7));
            double pan = ease(seg(t, 7.5, 9.5));
            Vec3d pos = v(-11.5, 67.1, 129.6).lerp(v(-13.6, 66.7, 127.4), push);
            Vec3d radio = v(-15, 66.3, 126.5), door = v(-9.5, 66.6, 128.5);
            cam(pos, radio.lerp(door, pan));
            fov = (float) MathHelper.lerp(push, 55, 40);
            blur = (float) (pan > 0 ? 0.1 : 0.55 * push);
            tintR = 1.0f; tintG = 0.96f; tintB = 0.9f;
            if (d != null) {
                act(d, t < 10 ? "still" : "slowwave", t < 10 ? t : t - 10);
                place(d, -9.5, 65, 128.5, 90);
            }
            if (once("static", t, 1)) sound("radio_static", radio, 1f, 1f);
            if (once("box", t, 8)) sound("musicbox", null, 0.8f, 0.9f);
        }
    }
}
