package com.yompix.glukhoman.story;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.block.LampBlock;
import com.yompix.glukhoman.entity.ResidentEntity;
import com.yompix.glukhoman.entity.TrainEntity;
import com.yompix.glukhoman.entity.WatcherEntity;
import com.yompix.glukhoman.net.Net;
import com.yompix.glukhoman.world.MapBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ночь на перроне. 03:14 звонит переезд на мёртвой линии; Порожняк (виден только в камере) подходит без звука и стоит у перрона.
 * Из домов выходят лунатики — те, кого «внесли в список» (самые заметные на сданных кассетах и не заякоренные своими).
 * Остановить без оружия: разбудить (держаться рядом 3 с, не бежать), снять на ходу (3 с в кадре), колокол (все замирают),
 * стрелка (поезд уходит на обгонный путь, +90 с; каждую — один раз). Табло рельсовых цепей в дежурке показывает, где что стоит.
 * Обходчик ходит по перрону: глазами виден, в камере нет; учится там, где ты опускаешь камеру и прячешься.
 */
public final class Night {
    private Night() {}

    public static boolean running;
    static int t, phase, stopLeft, diverts;
    static boolean bellUsed, loopWaited;
    static final List<String> SWITCHES = new ArrayList<>();
    static TrainEntity train;
    static WatcherEntity watcher;
    static final List<ResidentEntity> WALKERS = new ArrayList<>();
    static final Map<ResidentEntity, int[]> WAKING = new HashMap<>();   // [тики, индекс игрока]
    static final Map<ResidentEntity, ServerPlayerEntity> WAKER = new HashMap<>();
    static final Map<ResidentEntity, Integer> FILMED = new HashMap<>();
    static final Map<UUID, Integer> GRABS = new HashMap<>();
    static final boolean[] BOARD = new boolean[10];
    static int bellLoop;
    public static Vec3d wakeAt;
    public static String wakeWho = "";

    static final int[][] SECTORS = {{-90, -24}, {-24, -12}, {-12, 22}, {22, 40}, {40, 62}, {62, 150}, {150, 260}, {260, 460}};
    public static final String[] SECTOR_NAMES = {"тупик", "стрелка 1", "перрон", "переезд", "стрелка 2", "1-й км", "2-й км", "3-й км", "обгонный", "посёлок"};

    static ServerWorld w() {
        return Story.w();
    }

    static StoryState st() {
        return Story.st();
    }

    static int need() {
        return Math.max(1, Math.min(3, Story.players().size()));
    }

    /** Кого позовут: сначала самые заметные на сданных кассетах, потом те, кого меньше всех снимали. Заякоренных — нет. */
    static List<String> choose() {
        StoryState st = st();
        List<String> out = new ArrayList<>();
        Map<String, Integer> listed = Tapes.weight(st, true);
        Map<String, Integer> own = Tapes.weight(st, false);
        List<String> pool = new ArrayList<>(List.of("petrovich", "zina", "vika"));
        pool.removeIf(x -> st.flag("taken:" + x));
        List<String> byList = new ArrayList<>(pool);
        byList.removeIf(x -> listed.getOrDefault(x, 0) <= 0 || Tapes.anchored(st, x));
        byList.sort((a, b) -> listed.getOrDefault(b, 0) - listed.getOrDefault(a, 0));
        for (String x : byList) if (out.size() < need()) out.add(x);
        List<String> rest = new ArrayList<>(pool);
        rest.removeAll(out);
        rest.sort((a, b) -> {
            int an = Tapes.anchored(st, a) ? 1 : 0, bn = Tapes.anchored(st, b) ? 1 : 0;
            return an != bn ? an - bn : own.getOrDefault(a, 0) - own.getOrDefault(b, 0);
        });
        for (String x : rest) if (out.size() < need() && !Tapes.anchored(st, x)) out.add(x);
        if (out.isEmpty() && !rest.isEmpty()) out.add(rest.get(0));   // «пропущенный»: линия всегда кого-то зовёт
        return out;
    }

    public static void start() {
        StoryState st = st();
        running = true;
        t = 0;
        phase = 0;
        diverts = 0;
        bellUsed = false;
        SWITCHES.clear();
        WALKERS.clear();
        WAKING.clear();
        WAKER.clear();
        FILMED.clear();
        GRABS.clear();
        wakeAt = null;
        wakeWho = "";
        w().setTimeOfDay(21170);   // 03:10
        Story.clearResidents();
        List<String> who = choose();
        st.data.putString("n2_called", String.join(",", who));
        for (String x : who) {
            BlockPos home = st.point(x + "_home");
            openDoorNear(home);   // дверь настежь: лунатик выходит сам
            ResidentEntity r = Story.spawnResident(x, home);
            if (r == null) continue;
            r.sleepwalking(true);
            r.speed = 0.55;
            WALKERS.add(r);
        }
        Glukhoman.LOG.info("Глухомань: ночь 2, лунатики {}", who);
        watcher = Story.spawn(GContent.WATCHER, st.point("w_post"));
        if (watcher != null) {
            watcher.posted = true;
            watcher.setYaw(-90);
        }
        Story.lamps("station", true);
        Story.setStep("n2_wait");
        Story.voice("n2_zakhar_1", null, true);
        roles();
    }

    static void openDoorNear(BlockPos at) {
        if (at == null) return;
        for (BlockPos q : BlockPos.iterate(at.add(-7, -1, -7), at.add(7, 1, 7))) {
            var s = w().getBlockState(q);
            if (s.getBlock() instanceof net.minecraft.block.DoorBlock && s.get(net.minecraft.block.DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER
                    && !s.get(net.minecraft.block.DoorBlock.OPEN)) {
                ((net.minecraft.block.DoorBlock) s.getBlock()).setOpen(null, w(), s, q.toImmutable(), true);
            }
        }
    }

    /** Кооператив: роли бригады (инструменты можно передавать). В соло дежурит Захар у табло. */
    static void roles() {
        List<ServerPlayerEntity> ps = Story.players();
        if (ps.size() < 2) return;
        String[] r = {"Оператор: камера. Ты видишь поезд — рассказывай остальным.", "Дежурный: табло в дежурке. Веди остальных по рации, но не сиди один.",
                "Стрелочник: стрелки у перрона и за переездом. Каждую — один раз.", "Фонарщик: колокол и фонарь. Ты видишь Обходчика — кричи оператору."};
        for (int i = 0; i < ps.size(); i++) Story.msg(ps.get(i), r[Math.min(3, i)]);
    }

    public static void tick() {
        if (!running) return;
        t++;
        StoryState st = st();
        if (t == 200) {   // 03:14
            Story.setStep("n2_night");
            Story.voice("n2_zakhar_2", null, true);
            bellLoop = 0;
            for (ResidentEntity r : WALKERS) route(r);
        }
        if (t >= 200 && phase < 3 && --bellLoop <= 0) {
            bellLoop = 80;
            Story.sound(st.point("crossing"), "crossing_bell", 2.2f, 1f);
        }
        if (t >= 200 && phase < 3 && t % 10 == 0) for (BlockPos q : Story.lampsOf("signal")) Story.lampSet(q, (t / 10) % 2 == 0);
        if (t == 300) {
            train = Story.spawn(GContent.TRAIN, new BlockPos(330, 66, MapBuilder.LZ));
            if (train != null) {
                train.setYaw(90);
                train.path.add(new Vec3d(8, 66, MapBuilder.LZ + 0.5));
                train.maxSpeed = 0.42;
            }
            phase = 1;
        }
        if (train != null && train.isRemoved()) train = null;
        if (phase == 1 && train != null && train.path.isEmpty()) {
            phase = 2;
            stopLeft = Story.difficulty() == 0 ? 1800 : 2400;
            Story.voice("n2_zakhar_3", null, true);
        }
        if (phase == 2 && train != null && train.wait <= 0 && train.path.isEmpty()) {
            boolean anyWalking = false;
            for (ResidentEntity r : WALKERS) anyWalking |= r.sleepwalking();
            if (!anyWalking) stopLeft = Math.min(stopLeft, 300);   // звать больше некого — постоит и уйдёт
            if (--stopLeft <= 0) {
                phase = 3;
                train.path.add(new Vec3d(-70, 66, MapBuilder.LZ + 0.5));
                train.vanishAtEnd = true;
                train.maxSpeed = 0.3;
                Story.voice("n2_zakhar_4", null, true);
                for (BlockPos q : Story.lampsOf("signal")) Story.lampSet(q, false);
            }
        }
        if (phase == 4 && train != null && train.path.isEmpty() && !loopWaited) {
            loopWaited = true;
            train.wait = 1800;
        } else if (phase == 4 && train != null && train.path.isEmpty() && train.wait <= 0) {   // с обгонного — обратно к перрону
            train.path.add(new Vec3d(-16, 66, MapBuilder.LOOPZ + 0.5));
            train.path.add(new Vec3d(-22, 66, MapBuilder.LZ + 0.5));
            train.path.add(new Vec3d(8, 66, MapBuilder.LZ + 0.5));
            phase = 1;
        }
        if (t == 900 && watcher != null) watcher.posted = false;   // постоял на краю перрона — пошёл
        walkers();
        if (t % 400 == 0) for (ResidentEntity r : WALKERS)
            Glukhoman.LOG.info("Глухомань: ночь t={} фаза {}: {} в {} (маршрут {}, спит {})", t, phase, r.who(), r.getBlockPos().toShortString(), r.route.size(), r.sleepwalking());
        if (t % 20 == 0) board();
        if (phase == 3 && train == null) finish();
        if (t > 20 * 60 * 9) finish();   // страховка
    }

    static void route(ResidentEntity r) {
        StoryState st = st();
        r.route.clear();
        BlockPos door = st.point(r.who().equals("vika") ? "vika_door" : "petrovich_door");
        if (door != null) r.route.add(Vec3d.ofBottomCenter(door));
        r.route.add(new Vec3d(0.5, 65, door != null ? door.getZ() + 0.5 : 40));
        r.route.add(new Vec3d(0.5, 65, 31.5));
        r.route.add(new Vec3d(10.5, 65, 31.5));     // мимо вокзала, не через двери
        r.route.add(new Vec3d(10.5 + WALKERS.indexOf(r) * 3, 65, 22.0));
        r.route.add(new Vec3d(10.5 + WALKERS.indexOf(r) * 3, 66, 18.6));
    }

    static void walkers() {
        StoryState st = st();
        for (ResidentEntity r : new ArrayList<>(WALKERS)) {
            if (r.isRemoved()) {
                WALKERS.remove(r);
                continue;
            }
            if (!r.sleepwalking()) continue;
            // будят: рядом, не бежать
            int[] wk = WAKING.get(r);
            ServerPlayerEntity by = WAKER.get(r);
            if (wk != null && by != null) {
                if (by.squaredDistanceTo(r) > 4.5 * 4.5) {
                    WAKING.remove(r);
                    Story.msg(by, "Отошёл — и он снова пошёл.");
                } else if (by.isSprinting()) {
                    WAKING.remove(r);
                    Story.msg(by, "Не беги рядом с ним. Он вздрогнул и пошёл дальше.");
                } else {
                    r.hold = 5;
                    if (++wk[0] >= 60) woken(r, by, "wake");
                }
            }
            // снимают на ходу: 3 секунды в кадре
            if (t % 5 == 0 && r.sleepwalking()) for (ServerPlayerEntity p : Story.players())
                if (Cam.up(p) && Cam.inFrame(p, r.getPos().add(0, 1.2, 0), 32, false)) {
                    int f = FILMED.merge(r, 5, Integer::sum);
                    if (f >= 60) {
                        woken(r, p, "film");
                        break;
                    }
                }
            // посадка: поезд стоит у перрона, лунатик на краю
            if (r.sleepwalking() && phase == 2 && train != null && train.path.isEmpty() && r.getZ() < 20.4 && r.getX() > -10 && r.getX() < 22 && r.getY() > 65.5)
                board(r);
        }
    }

    public static void wake(ServerPlayerEntity p, ResidentEntity r) {
        if (!r.sleepwalking()) return;
        if (WAKING.containsKey(r)) return;
        WAKING.put(r, new int[]{0});
        WAKER.put(r, p);
        wakeAt = p.getPos();
        wakeWho = r.who();
        Story.msg(p, "Берёшь за плечо. Не отходи и не беги…");
        if (Cam.up(p)) {   // руки нужны обе: камеру — на перрон, она пишет дальше
            Cam.lower(p, true);
            Story.msg(p, "Положил камеру на перрон. Она пишет.");
        }
        Tapes.commit(st(), "night", "wake", Story.titleOf("night"), Map.of(r.who(), 200, "player:" + p.getName().getString(), 200));
    }

    static void woken(ResidentEntity r, ServerPlayerEntity by, String how) {
        r.sleepwalking(false);
        WAKING.remove(r);
        r.route.clear();
        BlockPos home = st().point(r.who() + "_home");
        if (home != null) {
            r.route.add(new Vec3d(r.getX(), 65, 22.0));
            r.route.add(new Vec3d(10.5, 65, 31.5));
            r.route.add(new Vec3d(0.5, 65, 31.5));
            r.route.add(Vec3d.ofBottomCenter(home));
        }
        r.speed = 0.5;
        st().set("saved:" + r.who());
        Story.voice("woke_" + r.who() + (how.equals("film") ? "_f" : ""), r.getBlockPos(), false);
        if (how.equals("film")) {
            wakeWho = r.who();
            Story.msg(by, "Он остановился в кадре. Обернулся. Пошёл домой.");
        }
        Night.heat(by, 1);
    }

    static void board(ResidentEntity r) {
        StoryState st = st();
        st.set("taken:" + r.who());
        st.inc("taken");
        Glukhoman.LOG.info("Глухомань: {} сел(а) в Порожняк", r.who());
        Story.sound(r.getBlockPos(), "stinger_soft", 0.6f, 0.6f);
        for (ServerPlayerEntity p : Story.players()) {
            if (Cam.up(p) && Cam.inFrame(p, r.getPos().add(0, 1, 0), 40, true)) Story.msg(p, "В кадре: шагнул в вагон. Обернулся. Смотрит в объектив.");
            else if (p.squaredDistanceTo(r) < 30 * 30) Story.msg(p, "Шаг… и между двумя шагами его нет.");
        }
        r.discard();
        WALKERS.remove(r);
    }

    public static void bell(ServerPlayerEntity p) {
        w().playSound(null, st().point("bell"), SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 2f, 0.8f);
        if (!running || t < 200) return;
        if (bellUsed) {
            Story.msg(p, "Колокол звенит глухо. Второй раз они не слышат.");
            return;
        }
        bellUsed = true;
        for (ResidentEntity r : WALKERS) if (r.sleepwalking()) r.hold = 260;
        Story.msg(p, "Они замерли на звон. Ненадолго.");
        st().set("bell_used");
    }

    public static void lever(ServerPlayerEntity p, String which) {
        if (!running || t < 200) {
            Story.msg(p, "Стрелка тугая. Сейчас незачем.");
            return;
        }
        if (SWITCHES.contains(which)) {
            Story.msg(p, "Стрелку заело. Второй раз не переведёшь.");
            return;
        }
        if (train == null || phase == 3) {
            Story.msg(p, "Поезда нет. Или уже уходит.");
            return;
        }
        SWITCHES.add(which);
        diverts++;
        w().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.8f, 0.6f);
        // поезд уходит на обгонный путь и стоит там 90 с, потом возвращается
        train.path.clear();
        double x = train.getX();
        train.path.add(new Vec3d(Math.max(x, 12), 66, MapBuilder.LZ + 0.5));
        train.path.add(new Vec3d(Math.max(x, 12) + 6, 66, MapBuilder.LOOPZ + 0.5));
        train.path.add(new Vec3d(20, 66, MapBuilder.LOOPZ + 0.5));
        train.wait = 0;
        train.maxSpeed = 0.25;
        loopWaited = false;
        phase = 4;
        Story.msg(p, "Стрелка ушла с лязгом. Поезд — на обгонный. Это минута-полторы.");
        Story.voice("n2_zakhar_switch", null, true);
    }

    /** Табло: какие участки заняты (поезд, Обходчик, лунатики). Реле щёлкает, Захар диктует по рации. */
    static void board() {
        boolean[] now = new boolean[10];
        List<Entity> things = new ArrayList<>();
        if (train != null) things.add(train);
        if (watcher != null && !watcher.isRemoved()) things.add(watcher);
        things.addAll(WALKERS);
        for (Entity e : things) {
            if (e instanceof ResidentEntity r && (r.getZ() > 24 || !r.sleepwalking())) {
                if (r.sleepwalking()) now[9] = true;
                continue;
            }
            if (e.getZ() < 13 && e.getX() > -26 && e.getX() < 64) {
                now[8] = true;
                continue;
            }
            for (int i = 0; i < SECTORS.length; i++) if (e.getX() >= SECTORS[i][0] && e.getX() < SECTORS[i][1]) now[i] = true;
        }
        List<BlockPos> lamps = Story.lampsOf("board");
        for (int i = 0; i < Math.min(lamps.size(), 10); i++) {
            if (now[i] != BOARD[i]) {
                Story.lampSet(lamps.get(i), now[i]);
                Story.sound(lamps.get(i), "relay", 0.9f, now[i] ? 1.1f : 0.9f);
                if (now[i] && train != null && (i == 3 || i == 2 || i == 8) && Story.players().size() < 2) Story.voice("board_" + i, null, true);
            }
            BOARD[i] = now[i];
        }
        // кто в дежурке — видит табло строкой
        for (ServerPlayerEntity p : Story.players()) if (Story.inZone(p, "dispatch")) Story.msg(p, readout());
    }

    public static String readout() {
        StringBuilder b = new StringBuilder("Табло: ");
        boolean any = false;
        for (int i = 0; i < SECTOR_NAMES.length; i++) if (BOARD[i]) {
            b.append(any ? " · " : "").append(SECTOR_NAMES[i]).append(" — ЗАНЯТ");
            any = true;
        }
        if (!any) b.append("все участки свободны");
        return b.toString();
    }

    /** Обходчик поймал одного вне кадра: первый раз — вырвал плёнку, потом (на «Страшно» и выше) — забрал. */
    public static void grab(ServerPlayerEntity p, WatcherEntity wch) {
        int n = GRABS.merge(p.getUuid(), 1, Integer::sum);
        Story.fx(p, "black", 50);
        Story.sound(p.getBlockPos(), "breath_close", 1f, 0.8f);
        if (n == 1 || Story.difficulty() == 0) {
            String lost = Tapes.ripLast(st());
            Story.msg(p, lost == null ? "Холодные пальцы на камере. Отпустил." : "Он вырвал плёнку из кассеты. Пропало: «" + lost + "».");
            Vec3d back = p.getPos().add(p.getRotationVec(1f).multiply(-5, 0, -5));
            p.teleport(p.getServerWorld(), back.x, p.getY(), back.z, p.getYaw(), p.getPitch());
        } else {
            // след — только кровь на шпалах и волочение к лесу; сама смерть — на плёнке погибшего
            BlockPos q = p.getBlockPos();
            ServerWorld w = p.getServerWorld();
            for (int i = 0; i < 6; i++) {
                BlockPos t2 = q.add(i, 0, (i % 2));
                int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, t2.getX(), t2.getZ());
                BlockPos at = new BlockPos(t2.getX(), y, t2.getZ());
                if (w.getBlockState(at).isAir()) w.setBlockState(at, GContent.BLOOD.getDefaultState().with(com.yompix.glukhoman.block.DecorBlock.VARIANT, i == 0 ? 1 : 2)
                        .with(net.minecraft.block.HorizontalFacingBlock.FACING, net.minecraft.util.math.Direction.EAST), 3);
            }
            Tapes.commit(st(), "death", "death:" + p.getName().getString(), "Последняя запись: " + p.getName().getString(), Map.of("player:" + p.getName().getString(), 100));
            p.damage(w.getDamageSources().outOfWorld(), 1000f);
        }
        Vec3d s = learnedSpot(w(), p);
        if (s != null) wch.refreshPositionAndAngles(s.x, s.y, s.z, wch.getYaw(), 0);
    }

    /** Тепловая карта привычек: где опускали камеру (k=2), где стояли и прятались (k=1). */
    public static void heat(ServerPlayerEntity p, int k) {
        StoryState st = st();
        String key = "heat:" + (p.getBlockX() >> 3) + ":" + (p.getBlockZ() >> 3);
        for (int i = 0; i < k; i++) st.inc(key);
    }

    /** Куда прийти: самая «горячая» клетка рядом с игроком, не на виду; иначе — за спиной, метрах в двадцати. */
    public static Vec3d learnedSpot(ServerWorld w, ServerPlayerEntity p) {
        StoryState st = st();
        String best = null;
        int bv = 0;
        for (var e : st.counters.entrySet()) {
            if (!e.getKey().startsWith("heat:")) continue;
            String[] a = e.getKey().split(":");
            int cx = Integer.parseInt(a[1]) * 8 + 4, cz = Integer.parseInt(a[2]) * 8 + 4;
            Vec3d c = new Vec3d(cx, p.getY(), cz);
            if (c.distanceTo(p.getPos()) > 48 || c.distanceTo(p.getPos()) < 10) continue;
            if (c.subtract(p.getPos()).normalize().dotProduct(p.getRotationVec(1f)) > 0.3) continue;
            if (e.getValue() > bv) {
                bv = e.getValue();
                best = e.getKey();
            }
        }
        Vec3d at;
        if (best != null) {
            String[] a = best.split(":");
            at = new Vec3d(Integer.parseInt(a[1]) * 8 + 4 + w.random.nextInt(3) - 1, 0, Integer.parseInt(a[2]) * 8 + 4 + w.random.nextInt(3) - 1);
        } else at = p.getPos().subtract(p.getRotationVec(1f).multiply(20, 0, 20));
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
        return new Vec3d(at.x, y, at.z);
    }

    public static void finish() {
        if (!running) return;
        running = false;
        StoryState st = st();
        for (ServerPlayerEntity p : Story.players()) Cam.lower(p, true);
        if (!Tapes.has(st, "night", null)) Tapes.commit(st, "night", "bench", Story.titleOf("night"), Map.of("platform", 300));
        if (train != null) train.discard();
        if (watcher != null) watcher.discard();
        for (ResidentEntity r : WALKERS) r.discard();
        WALKERS.clear();
        for (BlockPos q : Story.lampsOf("board")) Story.lampSet(q, false);
        java.util.Arrays.fill(BOARD, false);
        Story.nightOver();
    }

    public static void reset() {
        running = false;
        train = null;
        watcher = null;
        WALKERS.clear();
        WAKING.clear();
    }

    /** Для автотеста и отладки: ускорить ночь. */
    public static void skipTo(int tick) {
        t = Math.max(t, tick);
    }

    public static int phase() {
        return phase;
    }

    public static List<ResidentEntity> walkersList() {
        return WALKERS;
    }
}
