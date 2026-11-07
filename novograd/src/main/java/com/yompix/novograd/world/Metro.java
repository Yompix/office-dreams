package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.entity.TrainEntity;
import com.yompix.novograd.gta.Activities;
import com.yompix.novograd.gta.Wanted;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Метро «Новоград — Ривьера»: надземная линия на эстакаде (настил на высоте 7 блоков над улицей) от Делового центра
 * по улице z = −408 на запад, по кольцевой x = −552 на юг, через Южный мост и по набережной Ривьеры к Стрипу.
 * Шесть станций: платформы с навесом, лестницы с тротуара, автомат билетов ($5), табло с живым расписанием.
 * <p>
 * Два поезда по одному пути: встречаются только на станции «Пригород» (там разъезд — два пути), блок-участки
 * как на настоящей однопутке. Поезда «виртуальные»: их положение считается всегда (пара сложений за тик),
 * а сущность {@link TrainEntity} появляется, только когда рядом игрок.
 * <p>
 * Эстакада и станции строятся при генерации (новые чанки) и дописываются в старые сохранения при загрузке
 * чанка ({@link Retrofit}). Пассажир: ПКМ у открытых дверей (нужен билет), Пробел — пропустить поездку,
 * Shift — выйти. Угон: Shift+ПКМ у поезда — W/S газ и тормоз, две звезды розыска.
 */
public final class Metro {
    private Metro() {}

    /** Уровень настила (верх блока — уровень пола платформы и колёс поезда). */
    public static final int D = Island.G + 7;
    /** Вершины линии (центры путей по сетке улиц). */
    static final int[][] P = {{450, -408}, {-552, -408}, {-552, 744}, {-920, 744}, {-920, 24}, {-1314, 24}};
    /** Отрезок идёт по трассе (широкие обочины, лестницы дальше от оси) или по городской улице. */
    static final boolean[] HW = {false, true, true, true, false};

    public record Station(String id, String name, int seg, int t, double s, int x, int z) {}

    static final double[] START = new double[P.length];
    public static final double LENGTH;
    public static final Station[] ST;
    /** Станция-разъезд: здесь поезда встречаются. */
    static final int MEET = 3;
    static final double CAR = 7, GAP = 0.5, TRAIN_LEN = CAR * 4 + GAP * 3;
    static final double CRUISE = 1.0, ACC = 0.016;
    static final int DWELL = 200;

    static {
        double s = 0;
        for (int i = 0; i < P.length - 1; i++) {
            START[i] = s;
            s += Math.abs(P[i + 1][0] - P[i][0]) + Math.abs(P[i + 1][1] - P[i][1]);
        }
        START[P.length - 1] = s;
        LENGTH = s;
        Object[][] st = {
                {"metro_dt", "Деловой центр", 432, -408}, {"metro_north", "Пригород-Север", -240, -408}, {"metro_port", "Порт", -552, -48},
                {"metro_suburb", "Пригород", -552, 384}, {"metro_riv", "Ривьера-Восток", -920, 384}, {"metro_strip", "Стрип", -1296, 24}};
        ST = new Station[st.length];
        for (int k = 0; k < st.length; k++) {
            int x = (int) st[k][2], z = (int) st[k][3];
            for (int i = 0; i < P.length - 1; i++) {
                boolean ax = alongX(i);
                int t = ax ? x : z, b = ax ? z - P[i][1] : x - P[i][0];
                if (b != 0 || t < Math.min(t0(i), t1(i)) || t > Math.max(t0(i), t1(i))) continue;
                ST[k] = new Station((String) st[k][0], (String) st[k][1], i, t, START[i] + Math.abs(t - (ax ? P[i][0] : P[i][1])), x, z);
                break;
            }
        }
    }

    static boolean alongX(int i) {
        return P[i][1] == P[i + 1][1];
    }

    private static int t0(int i) {
        return alongX(i) ? P[i][0] : P[i][1];
    }

    private static int t1(int i) {
        return alongX(i) ? P[i + 1][0] : P[i + 1][1];
    }

    // ------------------------------------------------------------------ геометрия линии
    /** Точка оси линии на расстоянии s от начала: {x, z, dx, dz} (центр блока). */
    public static double[] at(double s) {
        s = Math.max(0, Math.min(LENGTH, s));
        int i = 0;
        while (i < P.length - 2 && s > START[i + 1]) i++;
        double f = s - START[i];
        int dx = Integer.signum(P[i + 1][0] - P[i][0]), dz = Integer.signum(P[i + 1][1] - P[i][1]);
        return new double[]{P[i][0] + dx * f + 0.5, P[i][1] + dz * f + 0.5, dx, dz};
    }

    /** Сдвиг поезда вбок на разъезде (+2 — первый поезд, −2 — второй), плавно на подходах. */
    static double lat(int train, double s) {
        double a = Math.abs(s - ST[MEET].s);
        double k = a <= 16 ? 1 : a >= 26 ? 0 : (26 - a) / 10;
        return k * 2 * (train == 0 ? 1 : -1);
    }

    /** Точка пути с учётом сдвига на разъезде: {x, z}. Сдвиг — по оси «b» отрезка (z у отрезков вдоль x, x — у вдоль z). */
    static double[] track(int train, double s) {
        double[] p = at(s);
        double l = lat(train, s);
        if (p[2] != 0) p[1] += l;
        else p[0] += l;
        return p;
    }

    public static Vec3d center(int train, double s) {
        double[] p = track(train, s);
        return new Vec3d(p[0], D + 1, p[1]);
    }

    /** Вагон k (0..3, от меньших s): центр и рыскание (как у сущностей). */
    public static double[] carPose(int train, double s, int k) {
        double c = s + (k - 1.5) * (CAR + GAP);
        double[] a = track(train, c - 2.6), b = track(train, c + 2.6);
        double x = (a[0] + b[0]) / 2, z = (a[1] + b[1]) / 2;
        float yaw = (float) Math.toDegrees(Math.atan2(-(b[0] - a[0]), b[1] - a[1]));
        return new double[]{x, z, yaw};
    }

    // ------------------------------------------------------------------ эстакада и станции (по столбцам)
    /** Чанк задевает линию (для отметки и дописывания старых сохранений). */
    public static boolean chunk(int cx, int cz) {
        int x0 = cx << 4, z0 = cz << 4;
        for (int i = 0; i < P.length - 1; i++) {
            int minX = Math.min(P[i][0], P[i + 1][0]) - 12, maxX = Math.max(P[i][0], P[i + 1][0]) + 12;
            int minZ = Math.min(P[i][1], P[i + 1][1]) - 12, maxZ = Math.max(P[i][1], P[i + 1][1]) + 12;
            if (x0 + 15 >= minX && x0 <= maxX && z0 + 15 >= minZ && z0 <= maxZ) return true;
        }
        return false;
    }

    /** Точка под путями (для карты). */
    public static boolean onLine(int x, int z) {
        for (int i = 0; i < P.length - 1; i++) {
            boolean ax = alongX(i);
            int t = ax ? x : z, b = ax ? z - P[i][1] : x - P[i][0];
            if (Math.abs(b) <= 2 && t >= Math.min(t0(i), t1(i)) && t <= Math.max(t0(i), t1(i))) return true;
        }
        return false;
    }

    private static final BlockState DECK = Blocks.POLISHED_ANDESITE.getDefaultState(), CURB = Blocks.SMOOTH_STONE_SLAB.getDefaultState(),
            PILLAR = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(), BEAM = Blocks.GRAY_CONCRETE.getDefaultState(),
            PLAT = Blocks.SMOOTH_STONE.getDefaultState(), PLAT2 = Blocks.POLISHED_DIORITE.getDefaultState(), EDGE = Blocks.YELLOW_CONCRETE.getDefaultState(),
            ROOF = Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(), LAMP = Blocks.SEA_LANTERN.getDefaultState(), POST = Blocks.QUARTZ_PILLAR.getDefaultState(),
            GLASS = Blocks.LIGHT_GRAY_STAINED_GLASS.getDefaultState(), STEP_FILL = Blocks.SMOOTH_STONE.getDefaultState();

    /** Станция на отрезке i рядом с t (|a| ≤ 26) или −1. */
    private static int stationNear(int i, int t, int r) {
        for (int k = 0; k < ST.length; k++) if (ST[k].seg() == i && Math.abs(t - ST[k].t()) <= r) return k;
        return -1;
    }

    /** Эстакада, платформы, лестницы и опоры в столбце (x, z) — поверх того, что уже построил остров. */
    public static void column(int x, int z, Island.Column c) {
        for (int i = 0; i < P.length - 1; i++) {
            boolean ax = alongX(i);
            int t = ax ? x : z, b = ax ? z - P[i][1] : x - P[i][0];
            int lo = Math.min(t0(i), t1(i)), hi = Math.max(t0(i), t1(i));
            if (Math.abs(b) > 9 || t < lo - 3 || t > hi + 3) continue;
            seg(i, ax, t, b, lo, hi, x, z, c);
        }
    }

    private static void seg(int i, boolean ax, int t, int b, int lo, int hi, int x, int z, Island.Column c) {
        int pb = HW[i] ? 8 : 6, ab = Math.abs(b), sg = b > 0 ? 1 : -1;
        boolean ext = t < lo || t > hi;
        // концы линии (первый и последний отрезок) — тупик с упором; остальные концы — углы поворота
        boolean lineEndLo = (i == 0 && t0(i) == lo) || (i == P.length - 2 && t1(i) == lo);
        boolean lineEndHi = (i == 0 && t0(i) == hi) || (i == P.length - 2 && t1(i) == hi);
        boolean nearCorner = (!lineEndLo && t - lo < 4) || (!lineEndHi && hi - t < 4);
        int meet = ST[MEET].seg() == i && Math.abs(t - ST[MEET].t()) <= 26 ? t - ST[MEET].t() : 999;
        int dw = meet != 999 ? 3 : 2;
        int k = stationNear(i, t, 16);
        int a = k >= 0 ? t - ST[k].t() : 999;
        int pin = k == MEET ? 4 : 3;
        // настил и пути
        if (ab <= dw) {
            c.set(D, DECK);
            if (!ext || lineEndLo || lineEndHi) {
                boolean rail = meet != 999 ? (Math.abs(meet) <= 16 && ab == 2) || (Math.abs(meet) >= 26 && b == 0) : b == 0;
                if (rail && !nearCorner) c.set(D + 1, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, ax ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH));
                if (ab == dw && k < 0 && !nearCorner) c.set(D + 1, CURB);
            }
            if ((lineEndLo && t == lo - 3) || (lineEndHi && t == hi + 3)) { // упор в тупике
                c.set(D + 1, ab <= 1 ? Blocks.RED_CONCRETE.getDefaultState() : CURB);
                if (ab <= 1) c.set(D + 2, Blocks.YELLOW_CONCRETE.getDefaultState());
            }
        }
        if (ext) return;
        int surf = Island.surface(x, z);
        // станция
        if (k >= 0 && Math.abs(a) <= 16) {
            if (ab >= pin && ab <= pb - 1) {
                c.set(D, ab == pin ? EDGE : ((a + b) & 1) == 0 ? PLAT : PLAT2);
                if (ab == pb - 1) {
                    int sa = a * sg;
                    if (Math.floorMod(a, 8) == 0 && Math.abs(a) < 16) for (int y = D + 1; y <= D + 4; y++) c.set(y, POST);
                    else if (sa == -6) c.set(D + 1, ModContent.ATM.getDefaultState().with(HorizontalFacingBlock.FACING, toTrack(ax, sg)));
                    else if (sa == 12 || sa == 13) c.set(D + 1, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, toTrack(ax, sg).getOpposite()));
                    else if (sa < 6 || sa > 9) c.set(D + 1, GLASS);
                }
            }
            if (ab <= pb - 1) { // навес над платформами и путями
                boolean lamp = Math.floorMod(a, 4) == 0 && (ab == pin + 1 || b == 0);
                c.set(D + 5, lamp ? LAMP : ROOF);
                if (Math.abs(a) == 16 || ab == pb - 1) c.set(D + 6, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
            }
            if (ab == pb) { // лестница с тротуара: ступени вдоль края, к своему концу платформы
                int sa = a * sg;
                if (sa >= 1 && sa <= 7) {
                    int y = Island.G + sa;
                    for (int f = surf + 1; f < y; f++) c.set(f, STEP_FILL);
                    c.set(y, Blocks.STONE_STAIRS.getDefaultState().with(StairsBlock.FACING, stepDir(ax, sg)).with(StairsBlock.HALF, BlockHalf.BOTTOM));
                    return;
                }
                if (sa == 8) {
                    for (int f = surf + 1; f <= D; f++) c.set(f, STEP_FILL);
                    return;
                }
            }
        }
        // опоры: П-образные рамы через 32 блока (стойки на тротуаре/обочине, ригель под настилом)
        if (Math.floorMod(t, 32) == 6 && !(k >= 0 && Math.abs(a) <= 16 && ab == pb)) {
            if (ab == pb && !Island.road(x, z)) for (int y = surf + 1; y < D; y++) c.set(y, PILLAR);
            if (ab < pb) c.set(D - 1, BEAM);
        }
    }

    /** Куда «вверх» идёт лестница: к своему концу платформы (на стороне b > 0 — по ходу оси, на b < 0 — против). */
    private static Direction stepDir(boolean ax, int sg) {
        return ax ? (sg > 0 ? Direction.EAST : Direction.WEST) : (sg > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /** С платформы — к путям. */
    private static Direction toTrack(boolean ax, int sg) {
        return ax ? (sg > 0 ? Direction.NORTH : Direction.SOUTH) : (sg > 0 ? Direction.WEST : Direction.EAST);
    }

    /** Мировая точка станции k: вдоль a, поперёк b (центр блока). */
    static Vec3d stationPoint(int k, double a, double b, double y) {
        Station st = ST[k];
        boolean ax = alongX(st.seg());
        double x = ax ? st.x() + a : st.x() + b, z = ax ? st.z() + b : st.z() + a;
        return new Vec3d(x + 0.5, y, z + 0.5);
    }

    static int pb(int k) {
        return HW[ST[k].seg()] ? 8 : 6;
    }

    static int pin(int k) {
        return k == MEET ? 4 : 3;
    }

    /** Выход с лестницы на тротуар: сторона sg. */
    public static Vec3d streetEntrance(int k, int sg) {
        return stationPoint(k, 0, sg * pb(k), Island.G + 1);
    }

    // ------------------------------------------------------------------ поезда (виртуальные)
    static final class Train {
        final int id;
        double pos, speed;
        int dir, dwell, stop = -1;
        boolean doors;
        @Nullable UUID entity, driver;

        Train(int id, int station, int dir) {
            this.id = id;
            this.pos = ST[station].s();
            this.dir = dir;
            this.stop = station;
            this.dwell = 120;
        }

        /** Следующая станция по ходу (или та, где стоит). */
        int next() {
            if (stop >= 0) return stop;
            for (int k = dir > 0 ? 0 : ST.length - 1; k >= 0 && k < ST.length; k += dir)
                if ((ST[k].s() - pos) * dir > 0.01) return k;
            return dir > 0 ? ST.length - 1 : 0;
        }
    }

    static final Train[] TRAINS = {new Train(0, 0, 1), new Train(1, ST.length - 1, -1)};

    /** Можно ли поезду t уйти со станции-разъезда: на участке впереди нет второго поезда. */
    private static boolean blocked(Train t) {
        Train o = TRAINS[1 - t.id];
        if (o.stop == MEET) return false;
        double m = ST[MEET].s();
        return t.dir > 0 ? o.pos > m + 0.5 : o.pos < m - 0.5;
    }

    private static void step(ServerWorld w, Train t) {
        if (t.driver != null) {
            drive(w, t);
            return;
        }
        if (t.stop >= 0) {
            t.speed = 0;
            if (t.stop == 0) t.dir = 1;
            if (t.stop == ST.length - 1) t.dir = -1;
            if (--t.dwell > 0) {
                t.doors = t.dwell > 30 && t.dwell < DWELL - 20;
                return;
            }
            if (t.stop == MEET && blocked(t)) {
                t.dwell = 2;
                t.doors = true;
                return;
            }
            t.stop = -1;
            t.doors = false;
        }
        int nx = t.next();
        double dist = (ST[nx].s() - t.pos) * t.dir;
        double vmax = Math.min(CRUISE, Math.sqrt(2 * ACC * Math.max(0, dist)) + 0.03);
        t.speed = t.speed < vmax ? Math.min(vmax, t.speed + ACC) : Math.max(vmax, t.speed - ACC * 2);
        if (dist <= t.speed + 0.001) {
            t.pos = ST[nx].s();
            t.speed = 0;
            t.stop = nx;
            t.dwell = DWELL;
            arrive(w, t, nx);
        } else t.pos += t.speed * t.dir;
    }

    /** Угнанный поезд: W — газ, S — тормоз и задний ход, отпустил — катится. */
    private static void drive(ServerWorld w, Train t) {
        ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(t.driver);
        TrainEntity e = t.entity == null ? null : w.getEntity(t.entity) instanceof TrainEntity te ? te : null;
        if (p == null || e == null || p.getVehicle() != e) {
            release(t);
            return;
        }
        t.stop = -1;
        t.doors = false;
        float in = p.forwardSpeed;
        if (in > 0.1f) t.speed = Math.min(1.9, t.speed + 0.02);
        else if (in < -0.1f) t.speed = Math.max(-0.6, t.speed - 0.035);
        else t.speed *= 0.996;
        double np = t.pos + t.speed * t.dir;
        if (np <= 0 || np >= LENGTH) { // упор в тупике
            if (Math.abs(t.speed) > 0.7) {
                w.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.2f, 1.1f);
                w.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION, e.getX(), e.getY() + 1.5, e.getZ(), 4, 1.5, 0.6, 1.5, 0);
                p.damage(w.getDamageSources().generic(), 4f);
            }
            np = Math.max(0, Math.min(LENGTH, np));
            t.speed = 0;
        }
        t.pos = np;
        if (w.getTime() % 200 == 0) Wanted.add(p, 0, "угон поезда метро", 2);
    }

    private static void release(Train t) {
        t.driver = null;
        if (t.speed < 0) {
            t.dir = -t.dir;
            t.speed = -t.speed;
        }
        t.speed = Math.min(t.speed, CRUISE);
    }

    // ------------------------------------------------------------------ сущности рядом с игроками
    public static EntityType<TrainEntity> TRAIN;

    /** Сущность ещё нужна (её ведёт один из поездов). */
    public static boolean owns(TrainEntity e) {
        for (Train t : TRAINS) if (e.getUuid().equals(t.entity)) return true;
        return false;
    }

    private static void entities(ServerWorld w, Train t) {
        Vec3d c = center(t.id, t.pos);
        TrainEntity e = t.entity == null ? null : w.getEntity(t.entity) instanceof TrainEntity te && te.isAlive() ? te : null;
        if (e == null) t.entity = null; // выгрузилась вместе с чанком
        boolean near = false;
        for (ServerPlayerEntity p : w.getPlayers())
            if (!p.isSpectator() && Math.abs(p.getX() - c.x) < 120 && Math.abs(p.getZ() - c.z) < 120) near = true;
        boolean loaded = w.isChunkLoaded(ChunkPos.toLong((int) Math.floor(c.x) >> 4, (int) Math.floor(c.z) >> 4))
                && w.shouldTickEntity(BlockPos.ofFloored(c));
        if (e != null && (!loaded || !near && !e.hasPassengers())) {
            e.removeAllPassengers();
            e.discard();
            e = null;
            t.entity = null;
        }
        if (e == null && near && loaded) {
            e = TRAIN.create(w);
            if (e == null) return;
            e.sync(t.id, t.pos, t.speed * t.dir, t.doors, -1);
            if (w.spawnEntity(e)) t.entity = e.getUuid();
        }
    }

    // ------------------------------------------------------------------ такт
    public static void init() {
        TRAIN = Registry.register(Registries.ENTITY_TYPE, Novograd.id("train"),
                EntityType.Builder.<TrainEntity>create(TrainEntity::new, SpawnGroup.MISC).dimensions(2.6f, 3.4f).maxTrackingRange(16)
                        .trackingTickInterval(1).disableSummon().build("train"));
        CityNet.init();
        com.yompix.novograd.life.Achievements.ALL.putIfAbsent("metro_ride", new String[]{"Осторожно, двери закрываются", "Прокатиться на метро"});
        com.yompix.novograd.life.Achievements.ALL.putIfAbsent("train_hijack", new String[]{"Машинист поневоле", "Угнать поезд метро"});
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            try {
                tick(s);
            } catch (RuntimeException e) {
                Novograd.LOG.warn("Метро: ошибка такта", e);
            }
        });
        net.minecraft.util.Identifier early = Novograd.id("metro_early");
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, world, hand, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            if (!world.getBlockState(pos).isOf(ModContent.ATM) || pos.getY() != D + 1) return ActionResult.PASS;
            int k = stationAt(pos.getX(), pos.getZ(), 20);
            if (k < 0) return ActionResult.PASS;
            ticketMenu(p, k);
            return ActionResult.SUCCESS;
        });
    }

    private static boolean island(ServerWorld w) {
        return w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator;
    }

    private static void tick(MinecraftServer s) {
        ServerWorld w = s.getOverworld();
        if (!island(w)) return;
        long now = w.getTime();
        for (Train t : TRAINS) {
            step(w, t);
            if (now % 10 == 0) entities(w, t);
            TrainEntity e = t.entity == null ? null : w.getEntity(t.entity) instanceof TrainEntity te ? te : null;
            if (e != null) {
                ServerPlayerEntity d = t.driver == null ? null : s.getPlayerManager().getPlayer(t.driver);
                e.sync(t.id, t.pos, t.speed * t.dir, t.doors, d == null ? -1 : d.getId());
                if (now % 10 == 0) hud(w, t, e);
                if (t.speed > 0.3 && now % 30 == 0)
                    w.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.NEUTRAL, 0.5f, 0.55f + (float) t.speed * 0.3f);
            }
        }
        if (now % 20 == 7) stations(w);
    }

    // ------------------------------------------------------------------ станции: табло, надписи, люди на платформах
    private static final String TAG = "nv_metro", WAIT = "nv_metro_wait";

    /** Станция в радиусе r от точки (по оси) или −1. */
    public static int stationAt(double x, double z, double r) {
        for (int k = 0; k < ST.length; k++) if (Math.abs(x - ST[k].x() - 0.5) <= r && Math.abs(z - ST[k].z() - 0.5) <= r) return k;
        return -1;
    }

    private static boolean built(ServerWorld w, int k) {
        BlockPos p = BlockPos.ofFloored(stationPoint(k, 0, 0, D));
        return w.isChunkLoaded(p) && w.getBlockState(p).isOf(Blocks.POLISHED_ANDESITE);
    }

    private static void stations(ServerWorld w) {
        long now = w.getTime();
        for (int k = 0; k < ST.length; k++) {
            Vec3d c = stationPoint(k, 0, 0, D + 1);
            ServerPlayerEntity near = null;
            for (ServerPlayerEntity p : w.getPlayers()) if (p.squaredDistanceTo(c.x, p.getY(), c.z) < 72 * 72) near = p;
            if (near == null || !built(w, k)) continue;
            Box box = new Box(c.x - 20, Island.G - 2, c.z - 20, c.x + 20, D + 10, c.z + 20);
            List<DisplayEntity.TextDisplayEntity> labels = w.getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, box, e -> e.getCommandTags().contains(TAG));
            if (labels.isEmpty()) labels(w, k);
            else for (DisplayEntity.TextDisplayEntity d : labels) if (d.getCommandTags().contains(TAG + "_board")) d.setText(board(k));
            if (now % 100 == 7) waiting(w, k, box);
        }
    }

    private static void labels(ServerWorld w, int k) {
        int pb = pb(k);
        Station st = ST[k];
        label(w, stationPoint(k, 0, 0, D + 6.6), Text.literal("Ⓜ " + st.name().toUpperCase()).formatted(Formatting.WHITE, Formatting.BOLD), 2.4f, 0xCC1E5AA8, null);
        for (int sg = -1; sg <= 1; sg += 2) {
            label(w, stationPoint(k, sg * 3, sg * (pb - 1) - sg * 0.2, D + 2.9), board(k), 0.9f, 0xCC101418, "_board");
            label(w, stationPoint(k, -sg * 6, sg * (pb - 1), D + 2.3), Text.literal("🎫 Билет $5\nПКМ по автомату").formatted(Formatting.YELLOW), 0.7f, 0x99000000, null);
            Vec3d e = streetEntrance(k, sg);
            label(w, e.add(sg * 0.0, 2.4, 0), Text.literal("Ⓜ Метро «" + st.name() + "»").formatted(Formatting.WHITE, Formatting.BOLD), 1.0f, 0xCC1E5AA8, null);
        }
    }

    private static void label(ServerWorld w, Vec3d at, Text text, float scale, int bg, @Nullable String tag2) {
        DisplayEntity.TextDisplayEntity d = EntityType.TEXT_DISPLAY.create(w);
        if (d == null) return;
        NbtCompound n = new NbtCompound();
        n.putString("billboard", "center");
        n.putString("alignment", "center");
        n.putInt("background", bg);
        n.putInt("line_width", 260);
        NbtCompound br = new NbtCompound();
        br.putInt("block", 15);
        br.putInt("sky", 15);
        n.put("brightness", br);
        NbtCompound tr = new NbtCompound();
        tr.put("translation", floats(0, 0, 0));
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("scale", floats(scale, scale, scale));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        n.put("transformation", tr);
        d.readNbt(n);
        d.setText(text);
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        d.addCommandTag(TAG);
        if (tag2 != null) d.addCommandTag(TAG + tag2);
        w.spawnEntity(d);
    }

    static net.minecraft.nbt.NbtList floats(float... v) {
        net.minecraft.nbt.NbtList l = new net.minecraft.nbt.NbtList();
        for (float f : v) l.add(net.minecraft.nbt.NbtFloat.of(f));
        return l;
    }

    /** Табло: через сколько поезд в каждую сторону. */
    static Text board(int k) {
        var t = Text.literal("ЛИНИЯ 1 · «" + ST[k].name() + "»\n").formatted(Formatting.AQUA);
        if (k > 0) t.append(Text.literal("← " + ST[0].name() + ": " + eta(k, -1) + "\n").formatted(Formatting.YELLOW));
        if (k < ST.length - 1) t.append(Text.literal("→ " + ST[ST.length - 1].name() + ": " + eta(k, 1)).formatted(Formatting.YELLOW));
        return t;
    }

    /** Когда ближайший поезд будет на станции k, идя в сторону dir: «стоит», «1:20». */
    static String eta(int k, int dir) {
        int best = Integer.MAX_VALUE;
        for (Train t : TRAINS) {
            int sec = secondsTo(t, k, dir);
            if (sec == 0) return "посадка";
            best = Math.min(best, sec);
        }
        return best == Integer.MAX_VALUE ? "—" : String.format("%d:%02d", best / 60, best % 60);
    }

    /** Грубая оценка: путь / крейсерская скорость + остановки по пути. */
    private static int secondsTo(Train t, int k, int dir) {
        if (t.driver != null) return Integer.MAX_VALUE;
        if (t.stop == k && t.dir == dir && t.doors) return 0;
        double pos = t.pos, ticks = t.stop >= 0 ? t.dwell : 0;
        int d = t.dir;
        if (t.stop == 0) d = 1;
        if (t.stop == ST.length - 1) d = -1;
        for (int guard = 0; guard < 4; guard++) {
            double end = d > 0 ? ST[ST.length - 1].s() : ST[0].s();
            // станции между pos и концом по ходу d
            for (int j = d > 0 ? 0 : ST.length - 1; j >= 0 && j < ST.length; j += d) {
                double sd = (ST[j].s() - pos) * d;
                if (sd <= 0.01) continue;
                ticks += sd / CRUISE + 60;
                pos = ST[j].s();
                if (j == k && d == dir) return (int) (ticks / 20);
                if (j == k && (j == 0 || j == ST.length - 1)) return (int) (ticks / 20); // конечная: в обе стороны
                ticks += DWELL;
            }
            pos = end;
            d = -d;
        }
        return Integer.MAX_VALUE;
    }

    /** Ждущие на платформе: 1–3 человека с телефонами и часами (днём больше). */
    private static void waiting(ServerWorld w, int k, Box box) {
        List<CitizenEntity> list = w.getEntitiesByClass(CitizenEntity.class, box, e -> e.getCommandTags().contains(WAIT));
        double h = LifeAi.hour(w);
        int want = h >= 6 && h < 23 ? 2 + (int) (Leisure.scale() * 2) : 1;
        Random r = w.getRandom();
        for (int i = list.size(); i < want; i++) {
            int sg = r.nextBoolean() ? 1 : -1;
            Vec3d at = stationPoint(k, r.nextInt(25) - 12, sg * (pin(k) + 1), D + 1);
            BlockPos bp = BlockPos.ofFloored(at);
            if (!w.getBlockState(bp).isAir() || !w.getBlockState(bp.down()).isSolidBlock(w, bp.down())) continue;
            CitizenEntity c = Leisure.citizen(w, at, r);
            c.setStationary(true);
            c.addCommandTag(WAIT);
            c.addCommandTag(TAG);
            if (!w.spawnEntity(c)) continue;
            c.faceTo(stationPoint(k, 0, 0, D + 1.5));
            c.busy(r.nextBoolean() ? CitizenEntity.Activity.PHONE : CitizenEntity.Activity.WATCH, 20 * 60 * 5, null,
                    r.nextBoolean() ? new ItemStack(ModContent.PHONE) : ItemStack.EMPTY);
        }
    }

    /** Поезд пришёл: двери открываются, выходят 1–3 пассажира, ждущие заходят. */
    private static void arrive(ServerWorld w, Train t, int k) {
        if (t.entity == null || !(w.getEntity(t.entity) instanceof TrainEntity e)) return;
        w.playSound(null, e.getX(), e.getY() + 2, e.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.NEUTRAL, 1.2f, 1.2f);
        w.playSound(null, e.getX(), e.getY() + 2, e.getZ(), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.NEUTRAL, 0.5f, 1.4f);
        if (w.getClosestPlayer(e.getX(), e.getY(), e.getZ(), 64, false) == null) return;
        Random r = w.getRandom();
        Vec3d c = stationPoint(k, 0, 0, D + 1);
        // ждущие — к ближайшей двери и внутрь
        for (CitizenEntity p : w.getEntitiesByClass(CitizenEntity.class, new Box(c.x - 20, D - 1, c.z - 20, c.x + 20, D + 4, c.z + 20), q -> q.getCommandTags().contains(WAIT))) {
            Vec3d door = nearestDoor(t, p.getPos());
            p.setStationary(false);
            p.walkRoute(List.of(door), 0.5, null, ItemStack.EMPTY, () -> p.removeLater(4));
        }
        // выходящие — из дверей к лестнице и на улицу (дальше ими займётся «мозг» прохожих)
        int out = 1 + r.nextInt(3);
        for (int i = 0; i < out; i++) {
            int car = r.nextInt(4), sg = r.nextBoolean() ? 1 : -1;
            Vec3d door = doorPoint(t, car, r.nextBoolean() ? -1 : 1, sg);
            CitizenEntity p = Leisure.citizen(w, door, r);
            p.addCommandTag(TAG);
            if (!w.spawnEntity(p)) continue;
            int pb = pb(k);
            boolean ax = alongX(ST[k].seg());
            double da = ax ? door.x - c.x : door.z - c.z;
            List<Vec3d> route = List.of(stationPoint(k, da, sg * (pin(k) + 1), D + 1), stationPoint(k, sg * 9, sg * (pb - 1), D + 1),
                    stationPoint(k, sg * 8, sg * pb, D + 1), stationPoint(k, 0, sg * pb, Island.G + 1), stationPoint(k, -sg * 4, sg * pb, Island.G + 1));
            p.walkRoute(route, 0.5, null, ItemStack.EMPTY, () -> p.lifeManaged = true);
        }
    }

    /** Точка у двери вагона car (дверь d = ±1 — передняя/задняя) на стороне sg (по оси «b» отрезка). */
    static Vec3d doorPoint(Train t, int car, int d, int sg) {
        double[] cp = carPose(t.id, t.pos, car);
        double yaw = Math.toRadians(cp[2]);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double[] p = at(t.pos);
        double bx = p[2] != 0 ? 0 : 1, bz = p[2] != 0 ? 1 : 0;
        return new Vec3d(cp[0] + fx * d * 1.75 + bx * sg * 1.9, D + 1, cp[1] + fz * d * 1.75 + bz * sg * 1.9);
    }

    private static Vec3d nearestDoor(Train t, Vec3d from) {
        Vec3d best = null;
        double bd = 1e9;
        for (int car = 0; car < 4; car++)
            for (int d = -1; d <= 1; d += 2)
                for (int sg = -1; sg <= 1; sg += 2) {
                    Vec3d v = doorPoint(t, car, d, sg);
                    double dd = v.squaredDistanceTo(from);
                    if (dd < bd) {
                        bd = dd;
                        best = v;
                    }
                }
        return best;
    }

    // ------------------------------------------------------------------ игрок: билет, посадка, поездка, угон
    static final String TICKET = "metro_ticket";

    static int tickets(LifeData d) {
        return (int) d.flags.stream().filter(f -> f.startsWith(TICKET + "=")).mapToLong(f -> Long.parseLong(f.substring(TICKET.length() + 1))).max().orElse(0);
    }

    static void setTickets(LifeData d, int n) {
        d.flags.removeIf(f -> f.startsWith(TICKET + "="));
        if (n > 0) d.flags.add(TICKET + "=" + n);
    }

    private static void ticketMenu(ServerPlayerEntity p, int k) {
        LifeData d = Life.get(p);
        NbtCompound m = CityNet.menu("metro_ticket:" + k, "Ⓜ МЕТРО · «" + ST[k].name() + "»",
                "Билетов у тебя: " + tickets(d) + " · ПКМ у открытых дверей — сесть, Пробел в вагоне — пропустить поездку", 0xFF1E5AA8);
        CityNet.item(m, "Разовый билет", "Одна поездка в любую сторону", 5, d.money >= 5);
        CityNet.item(m, "10 поездок", "Проездной: выгоднее на $10", 40, d.money >= 40);
        CityNet.item(m, "Схема линии", lineText(), 0, true);
        CityNet.send(p, "menu", m);
        p.playSoundToPlayer(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.BLOCKS, 0.5f, 1.4f);
    }

    static String lineText() {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < ST.length; k++) sb.append(k == 0 ? "" : " — ").append(ST[k].name());
        return sb.toString();
    }

    /** Выбор в меню метро (билеты, пропуск поездки). */
    static void pick(ServerPlayerEntity p, String ctx, int i) {
        LifeData d = Life.get(p);
        if (ctx.startsWith("metro_ticket:")) {
            int k = Integer.parseInt(ctx.substring(13));
            if (k < 0 || k >= ST.length || p.squaredDistanceTo(stationPoint(k, 0, 0, D + 1)) > 24 * 24) return;
            if (i == 0 && Life.pay(p, 5, "билет на метро")) setTickets(d, tickets(d) + 1);
            else if (i == 1 && Life.pay(p, 40, "проездной на 10 поездок")) setTickets(d, tickets(d) + 10);
            else if (i == 2) p.sendMessage(Text.literal("Ⓜ Линия 1: " + lineText()).formatted(Formatting.AQUA), false);
            if (i <= 1) {
                p.sendMessage(Text.literal("🎫 Билетов: " + tickets(d) + ". Жди поезд — ПКМ у открытых дверей").formatted(Formatting.GREEN), true);
                p.playSoundToPlayer(ModContent.CASH, SoundCategory.PLAYERS, 0.7f, 1.2f);
            }
            return;
        }
        if (ctx.equals("metro_skip")) {
            TrainEntity e = p.getVehicle() instanceof TrainEntity te ? te : null;
            if (e == null || i < 0 || i >= ST.length) return;
            Train t = TRAINS[e.trainId()];
            if (t.driver != null) return;
            ServerWorld w = p.getServerWorld();
            NbtCompound n = new NbtCompound();
            n.putString("label", ST[i].name());
            CityNet.send(p, "fade", n);
            p.stopRiding();
            Vec3d to = stationPoint(i, 0, (pin(i) + 1) * (i % 2 == 0 ? 1 : -1), D + 1);
            w.getChunk(BlockPos.ofFloored(to));
            p.teleport(w, to.x, to.y + 0.05, to.z, p.getYaw(), p.getPitch());
            p.sendMessage(Text.literal("Ⓜ Станция «" + ST[i].name() + "». Осторожно, двери закрываются!").formatted(Formatting.AQUA), false);
        }
    }

    /** Пробел в вагоне — выбор станции, куда «перемотать» поездку. */
    static void skipMenu(ServerPlayerEntity p) {
        if (!(p.getVehicle() instanceof TrainEntity e) || TRAINS[e.trainId()].driver != null) return;
        NbtCompound m = CityNet.menu("metro_skip", "Ⓜ КУДА ЕДЕМ?", "Затемнение — и ты на станции", 0xFF1E5AA8);
        int near = stationAt(p.getX(), p.getZ(), 30);
        for (int k = 0; k < ST.length; k++) CityNet.item(m, ST[k].name(), k == near ? "ты здесь" : "", 0, k != near);
        CityNet.send(p, "menu", m);
    }

    @Nullable
    private static TrainEntity nearTrain(ServerPlayerEntity p, double r) {
        TrainEntity best = null;
        double bd = r * r;
        for (TrainEntity e : p.getServerWorld().getEntitiesByClass(TrainEntity.class, p.getBoundingBox().expand(r + 16), Entity::isAlive)) {
            double d = trackDist(e, p.getPos());
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** Квадрат расстояния от точки до ближайшего вагона. */
    static double trackDist(TrainEntity e, Vec3d p) {
        double best = 1e9;
        for (int k = 0; k < 4; k++) {
            double[] c = carPose(e.trainId(), e.progress(), k);
            double dx = p.x - c[0], dz = p.z - c[1];
            // вагон длинный: расстояние до отрезка длиной CAR
            double yaw = Math.toRadians(c[2]);
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            double along = MathHelper.clamp(dx * fx + dz * fz, -CAR / 2, CAR / 2);
            double ex = dx - fx * along, ez = dz - fz * along;
            best = Math.min(best, ex * ex + ez * ez + Math.pow(p.y - (D + 1), 2) * 0.3);
        }
        return best;
    }

    /** ПКМ у открытых дверей. */
    static void board(ServerPlayerEntity p) {
        if (p.hasVehicle()) return;
        TrainEntity e = nearTrain(p, 5.5);
        if (e == null) return;
        Train t = TRAINS[e.trainId()];
        if (!t.doors || t.driver != null) {
            p.sendMessage(Text.literal("Двери закрыты — жди остановки").formatted(Formatting.GRAY), true);
            return;
        }
        LifeData d = Life.get(p);
        int n = tickets(d);
        if (n <= 0) {
            p.sendMessage(Text.literal("🎫 Нужен билет: автомат на платформе, $5").formatted(Formatting.YELLOW), true);
            return;
        }
        setTickets(d, n - 1);
        Life.sync(p);
        p.startRiding(e, true);
        p.sendMessage(Text.literal("Ⓜ Следующая станция — «" + ST[nextFor(t)].name() + "». Пробел — пропустить поездку, Shift — выйти").formatted(Formatting.AQUA), true);
        com.yompix.novograd.life.Achievements.give(p, "metro_ride");
    }

    static int nextFor(Train t) {
        if (t.stop >= 0) {
            int d = t.stop == 0 ? 1 : t.stop == ST.length - 1 ? -1 : t.dir;
            return Math.max(0, Math.min(ST.length - 1, t.stop + d));
        }
        return t.next();
    }

    /** Shift+ПКМ у поезда (или в вагоне): угнать. */
    static void hijack(ServerPlayerEntity p) {
        TrainEntity e = p.getVehicle() instanceof TrainEntity te ? te : nearTrain(p, 6);
        if (e == null) return;
        Train t = TRAINS[e.trainId()];
        if (t.driver != null) return;
        if (!com.yompix.novograd.gta.FunUtil.freeRoam(p)) {
            p.sendMessage(Text.literal("Сейчас не до угона поездов").formatted(Formatting.GRAY), true);
            return;
        }
        if (p.getVehicle() != e) {
            p.stopRiding();
            p.startRiding(e, true);
        }
        t.driver = p.getUuid();
        t.stop = -1;
        t.doors = false;
        Wanted.add(p, 0, "угон поезда метро", 2);
        com.yompix.novograd.life.Achievements.give(p, "train_hijack");
        Activities.title(p, "ПОЕЗД УГНАН", "W — газ · S — тормоз и назад · Shift — бросить состав");
        p.getServerWorld().playSound(null, e.getBlockPos(), ModContent.CAR_HORN, SoundCategory.NEUTRAL, 1.6f, 0.5f);
        // пассажиры в панике выбегают
        for (Entity q : new ArrayList<>(e.getPassengerList())) if (q != p) q.stopRiding();
    }

    /** Подсказки в вагоне: следующая станция, скорость. */
    private static void hud(ServerWorld w, Train t, TrainEntity e) {
        for (Entity q : e.getPassengerList()) {
            if (!(q instanceof ServerPlayerEntity p)) continue;
            NbtCompound n = new NbtCompound();
            boolean drv = p.getUuid().equals(t.driver);
            n.putBoolean("driver", drv);
            n.putInt("kmh", (int) Math.round(Math.abs(t.speed) * 72));
            n.putString("next", ST[nextFor(t)].name());
            n.putBoolean("doors", t.doors);
            n.putInt("stop", t.stop);
            n.putString("here", t.stop >= 0 ? ST[t.stop].name() : "");
            CityNet.send(p, "metro_hud", n);
        }
    }

    /** Где сидит пассажир: угонщик — в кабине головного вагона, остальные — по вагонам у окна. */
    public static Vec3d seat(TrainEntity e, Entity who) {
        int idx = e.getPassengerList().indexOf(who);
        double s = e.progress();
        int id = e.trainId();
        if (who.getId() == e.driverId()) {
            int car = e.speed() < -0.01 ? 0 : 3; // кабина — в голове по ходу
            double[] cp = carPose(id, s, car);
            double yaw = Math.toRadians(cp[2]);
            double f = car == 3 ? 2.6 : -2.6;
            return new Vec3d(cp[0] - Math.sin(yaw) * f, D + 1.2, cp[1] + Math.cos(yaw) * f);
        }
        int car = 1 + idx % 2;
        double[] cp = carPose(id, s, car);
        double yaw = Math.toRadians(cp[2]);
        double along = -1.2 + (idx / 2 % 3) * 1.2, side = idx % 4 < 2 ? 0.75 : -0.75;
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        return new Vec3d(cp[0] + fx * along - fz * side, D + 1.15, cp[1] + fz * along + fx * side);
    }

    /** Вышел: на остановке — на платформу, в пути — на настил рядом с вагоном. */
    public static Vec3d dismount(TrainEntity e, LivingEntity who) {
        Train t = TRAINS[e.trainId()];
        if (who instanceof ServerPlayerEntity p && p.getUuid().equals(t.driver)) release(t);
        int k = stationAt(e.getX(), e.getZ(), 18);
        if (k >= 0 && Math.abs(e.progress() - ST[k].s()) < 3) {
            Vec3d c = stationPoint(k, 0, 0, D + 1);
            double sg = who.getPos().subtract(c).dotProduct(new Vec3d(alongX(ST[k].seg()) ? 0 : 1, 0, alongX(ST[k].seg()) ? 1 : 0)) >= 0 ? 1 : -1;
            double[] cp = carPose(e.trainId(), e.progress(), 1);
            Vec3d at = stationPoint(k, 0, sg * (pin(k) + 1), D + 1);
            boolean ax = alongX(ST[k].seg());
            return new Vec3d(ax ? cp[0] : at.x, D + 1.05, ax ? at.z : cp[1]);
        }
        return who.getPos().add(0, 0.2, 0);
    }

    /** Для автотеста и отладки: где поезда. */
    public static String debug() {
        StringBuilder sb = new StringBuilder();
        for (Train t : TRAINS)
            sb.append(String.format("[поезд %d s=%.0f v=%.2f dir=%d стоит=%d двери=%s сущн=%s] ", t.id, t.pos, t.speed, t.dir, t.stop, t.doors, t.entity != null));
        return sb.toString();
    }

    /** Для автотеста: поставить поезд к станции k и открыть двери. */
    public static void debugPlace(int train, int k) {
        Train t = TRAINS[train];
        t.pos = ST[k].s();
        t.stop = k;
        t.dwell = DWELL;
        t.speed = 0;
        t.doors = true;
    }

    /** Для автотеста: поезд идёт к станции k, сейчас в dist блоках до неё. */
    public static void debugApproach(int train, int k, double dist) {
        Train t = TRAINS[train];
        t.dir = k == 0 ? -1 : k == ST.length - 1 ? 1 : t.dir;
        t.pos = Math.max(0, Math.min(LENGTH, ST[k].s() - t.dir * dist));
        t.stop = -1;
        t.speed = CRUISE;
    }

    /** Для автотеста: билеты и угон без клиента. */
    public static void debugTicket(ServerPlayerEntity p, int n) {
        setTickets(Life.get(p), n);
    }

    public static void hijackForTest(ServerPlayerEntity p) {
        hijack(p);
    }

    public static boolean driving(ServerPlayerEntity p) {
        for (Train t : TRAINS) if (p.getUuid().equals(t.driver)) return true;
        return false;
    }
}
