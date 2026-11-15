package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.block.LampBlock;
import com.yompix.glukhoman.client.screen.CineScreen;
import com.yompix.glukhoman.entity.ResidentEntity;
import com.yompix.glukhoman.entity.TrainEntity;
import net.minecraft.nbt.NbtCompound;
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
    public static float cone, crush, glitch;
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

        /** После сцены (пропуск, конец): вернуть мир как было. */
        default void end() {
        }
    }

    // ======================= управление =======================
    /** Сцена «в видоискателе»: REC на экране, видно то, что видно только камерой. replay — это пересмотр кассеты (PLAY ▶). */
    public static boolean vf, replay, noFx;
    /** Сцена запущена только на этом клиенте (просмотр, автотест): Esc останавливает её сразу, без сервера. */
    public static boolean local;
    /** Съёмка панорамы: грань куба 0–5. */
    public static int panoFace;
    /** Параметры сцены от сервера (пересмотр: где лежала камера, кого будили). */
    public static NbtCompound args = new NbtCompound();

    public static void play(String sid) {
        play(sid, new NbtCompound());
    }

    public static void play(String sid, NbtCompound a) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        stop();
        args = a;
        vf = replay = false;
        scene = make(sid);
        if (scene == null) return;
        id = sid;
        len = Scenes.LEN.getOrDefault(sid, 5f);
        startNs = System.nanoTime();
        clockTicks = 0;
        active = true;
        FIRED.clear();
        blur = rays = tear = warp = flash = 0;
        cone = crush = glitch = 0;
        fogS = 0;
        recFrame = 0;
        tintR = tintG = tintB = 1;
        fov = 70;
        roll = 0;
        caption = "";
        savedHud = c.options.hudHidden;
        c.options.hudHidden = true;
        c.chunkCullingEnabled = false;   // камера сцены не там, где игрок: без отсечения пещер, иначе внутри домов — пустое небо
        if (a.contains("t")) {   // 0.5.1: зашёл посреди сцены — догоняем с того же места (совместная игра)
            double off = Math.min(a.getDouble("t"), len - 0.5);
            startNs -= (long) (off * 1e9);
            clockTicks += (int) (off * 20);
        }
        scene.start(c.world);
        scene.frame(time());
        c.setScreen(new CineScreen());
        Glukhoman.LOG.info("Кат-сцена {}", sid);
    }

    public static void stop() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (!active) return;
        Glukhoman.LOG.info("Кат-сцена {} закончилась на {} с", id, String.format("%.1f", time()));
        if (FIXED) Glukhoman.LOG.info("стек остановки", new Throwable());
        active = false;
        if (scene != null) scene.end();
        local = false;
        for (Entity e : PUPPETS) if (c.world != null) c.world.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
        PUPPETS.clear();
        if (c.world != null) for (BlockPos p : KEYS) if (c.world.getBlockState(p).isOf(net.minecraft.block.Blocks.LIGHT)) c.world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
        KEYS.clear();
        if (c.world != null) for (BlockPos p : BEAM) if (c.world.getBlockState(p).isOf(net.minecraft.block.Blocks.LIGHT)) c.world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
        BEAM.clear();
        c.options.hudHidden = savedHud;
        c.chunkCullingEnabled = true;
        blur = rays = tear = warp = flash = 0;
        tintR = tintG = tintB = 1;
        vf = replay = noFx = false;
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
        if (MinecraftClient.getInstance().world != null) Viewfinder.flicker(MinecraftClient.getInstance().world, t);
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
        } else if (e instanceof ResidentEntity r) {
            if (!r.act().equals(a)) r.act(a);
            r.animClock = clockSec * 20;
        } else if (e instanceof TrainEntity tr) tr.animClock = clockSec * 20;
    }

    /** Ключевой свет кадра: невидимый источник только на этом клиенте. */
    static void key(ClientWorld w, int x, int y, int z, int level) {
        BlockPos p = new BlockPos(x, y, z);
        if (!w.getBlockState(p).isAir()) return;
        w.setBlockState(p, net.minecraft.block.Blocks.LIGHT.getDefaultState().with(net.minecraft.block.LightBlock.LEVEL_15, level), 3);
        KEYS.add(p);
    }

    /** Земля под точкой: сверху вниз мимо листвы, стволов и травы (на клиенте нет карты высот «без листвы»). */
    static int ground(ClientWorld w, double x, double z) {
        int bx = MathHelper.floor(x), bz = MathHelper.floor(z);
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
        BlockPos.Mutable m = new BlockPos.Mutable(bx, y - 1, bz);
        while (m.getY() > w.getBottomY()) {
            var s = w.getBlockState(m);
            if (!(s.isAir() || s.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) || s.isIn(net.minecraft.registry.tag.BlockTags.LOGS) || s.isReplaceable())) break;
            m.move(0, -1, 0);
        }
        return m.getY() + 1;
    }

    static void lamp(ClientWorld w, BlockPos p, boolean lit) {
        BlockState s = w.getBlockState(p);
        if (s.getBlock() instanceof LampBlock && s.get(LampBlock.LIT) != lit) w.setBlockState(p, s.with(LampBlock.LIT, lit), 3);
    }

    // ======================= сцены =======================
    static Scene make(String sid) {
        return switch (sid) {
            case "pr_train" -> StoryScenes.prTrain();      // 0.5.1: на режиссёре Timeline
            case "pr_board" -> StoryScenes.prBoard();
            case "rp_night" -> StoryScenes.rpNight();
            case "c1_handover" -> StoryScenes.handover();
            case "c2_gena" -> Part2Scenes.gena(false);      // 0.6: часть вторая
            case "c2_gena_ok" -> Part2Scenes.gena(true);
            case "c2_pass" -> Part2Scenes.pass();
            case "c3_naryad" -> Part2Scenes.naryad();
            case "c3_savva" -> Part2Scenes.savva();
            case "demo_ch2" -> Part2Scenes.demoCh2();
            case "demo_ch3" -> Part2Scenes.demoCh3();
            case "demo_watcher" -> StoryScenes.demoWatcher();
            case "demo_residents" -> StoryScenes.demoResidents();
            case "demo_moments" -> StoryScenes.demoMoments();
            case "pano" -> new Pano();
            case "in_ir" -> new InIr();
            case "proto_npc" -> ProtoScenes.npc();
            case "proto_cut" -> ProtoScenes.handover();
            case "tt_pole" -> new Turntable(GContent.POLE, 4.6, 2.2, 3.6);
            case "tt_watcher" -> new Turntable(GContent.WATCHER, 4.4, 1.8, 3.0);
            case "tt_listener" -> new Turntable(GContent.LISTENER, 3.0, 0.7, 0.85);
            case "tt_choir" -> new Turntable(GContent.CHOIR, 6.5, 2.0, 2.9);
            default -> Trailer3.make(sid);
        };
    }

    // ======================= «живая» камера и фонарь =======================
    static final List<BlockPos> BEAM = new ArrayList<>();

    /** Ручная камера: дыхание, микродрожь, шаги. */
    static Vec3d hand(double t, double walk) {
        double k = Timeline.shakeMul();
        return k == 0 ? Vec3d.ZERO : v(Math.sin(t * 0.9) * 0.025 + Math.sin(t * 7.3) * 0.004, Math.sin(t * 1.3) * 0.03 + Math.abs(Math.sin(t * Math.PI * 1.8)) * 0.05 * walk,
                Math.cos(t * 0.7) * 0.02).multiply(k);
    }

    /** Луч фонаря: свет там, куда он бьёт, и рядом с камерой; всё остальное — темнота. */
    static void beam(ClientWorld w, Vec3d from, Vec3d dir, boolean on) {
        List<BlockPos> want = new ArrayList<>();
        if (on) {
            var hit = w.raycast(new net.minecraft.world.RaycastContext(from, from.add(dir.multiply(24)), net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE, MinecraftClient.getInstance().player));
            Vec3d end = hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? from.add(dir.multiply(24)) : hit.getPos().subtract(dir.multiply(0.6));
            want.add(BlockPos.ofFloored(end));
            want.add(BlockPos.ofFloored(from.add(dir.multiply(Math.min(5, end.distanceTo(from) * 0.4)))));
        }
        for (BlockPos p : new ArrayList<>(BEAM)) if (!want.contains(p)) {
            if (w.getBlockState(p).isOf(net.minecraft.block.Blocks.LIGHT)) w.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
            BEAM.remove(p);
        }
        for (int i = 0; i < want.size(); i++) {
            BlockPos p = want.get(i);
            if (BEAM.contains(p) || !w.getBlockState(p).isAir()) continue;
            w.setBlockState(p, net.minecraft.block.Blocks.LIGHT.getDefaultState().with(net.minecraft.block.LightBlock.LEVEL_15, i == 0 ? 13 : 8), 3);
            BEAM.add(p);
        }
    }

    static void night() {
        fog(0.6f, 20f, 0.075f, 0.095f, 0.105f);
        cone = 0.5f;
        crush = 0.45f;
        tintR = 0.9f; tintG = 1.0f; tintB = 1.05f;
    }

    /** Куда повернуться, чтобы смотреть из from на to (градусы, как yaw сущности). */
    static float yawTo(Vec3d from, Vec3d to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    static Vec3d dirOf(float yaw, float pitch) {
        return Vec3d.fromPolar(pitch, yaw);
    }

    static void fog(float s, float e, float r, float g, float b) {
        fogS = s; fogE = e; fogR = r; fogG = g; fogB = b;
    }

    static class Turntable implements Scene {
        final EntityType<? extends Entity> type;
        final double dist, mid, head;
        Entity e;
        static final double X = 40.5, Z = 30.5;
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
            key(w, 40, (int) y + 3, 28, 13);
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


    static final BlockPos CROSS = new BlockPos(29, 68, 19);
    static final BlockPos[] SIGNALS = {new BlockPos(28, 68, 19), new BlockPos(34, 68, 13)};

    /** Переезд звонит, красные огни мигают (на этом клиенте, по времени сцены). */
    static void crossing(ClientWorld w, double t, double from) {
        if (t < from) return;
        int k = (int) ((t - from) * 2);
        for (int i = 0; i < SIGNALS.length; i++) lamp(w, SIGNALS[i], (k + i) % 2 == 0);
        if (once("bell" + (int) ((t - from) / 4), t, from + (int) ((t - from) / 4) * 4)) sound("crossing_bell", Vec3d.ofCenter(CROSS), 2.4f, 1f);
    }

    static void motor(double t) {
        if (once("motor" + (int) (t / 3), t, (int) (t / 3) * 3)) sound("cam_motor", null, 0.35f, 1f);
    }

    static ResidentEntity resident(ClientWorld w, String who, double x, double y, double z, float yaw) {
        ResidentEntity r = puppet(w, GContent.RESIDENT, x, y, z, yaw);
        if (r != null) r.who(who);
        return r;
    }

    /** Грани панорамы главного меню: ночная станция с перрона, угол 90°, без пост-эффекта (стыки должны совпасть). */
    static class Pano implements Scene {
        public void start(ClientWorld w) {
            noFx = true;
            key(w, 4, 68, 19, 7);
        }

        public void frame(double t) {
            fog(3f, 46f, 0.06f, 0.068f, 0.08f);
            float y0 = 60;
            float[][] f = {{y0, 0}, {y0 + 90, 0}, {y0 + 180, 0}, {y0 - 90, 0}, {y0, -90}, {y0, 90}};
            cx = 13.5;
            cy = 67.4;
            cz = 19.5;
            yaw = f[panoFace][0];
            pitch = f[panoFace][1];
            roll = 0;
            fov = 90;
            cone = crush = glitch = 0;
        }
    }

    /** Заставка: перрон ночью в ИК-режиме камеры (зелёный, зернистый), камера в руках. */
    static class InIr implements Scene {
        public void start(ClientWorld w) {
            vf = true;
        }

        public void frame(double t) {
            Vec3d p = v(-6 + t * 1.3, 67.6, 19.6).add(hand(t, 0.8));
            cam(p, p.add(dirOf(-75 + (float) Math.sin(t * 0.6) * 10, 8)));
            fov = 60;
            fog(6f, 50f, 0.12f, 0.2f, 0.12f);
            cone = 0;
            crush = 0;
            glitch = 0.15f;
            tintR = 0.5f;
            tintG = 1.35f;
            tintB = 0.5f;
        }
    }
}
