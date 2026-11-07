package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.world.Island;
import com.yompix.novograd.world.IslandRoads;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Городские автобусы: три маршрута (кольцо Делового центра, «Центр — Ривьера» через Северный мост, Пригород).
 * Автобусы ездят «по расписанию» всегда (виртуально — точка на маршруте), а настоящая машина с водителем появляется
 * только рядом с игроком — дёшево для слабого ноутбука. На остановках (навес, скамейка и табличка с номером
 * маршрута ставятся прямо в мире у тротуара, так что работают и в старых сохранениях) табличка показывает «через N мин».
 * Автобус встаёт на остановках: жители выходят и садятся. Игрок: ПКМ по автобусу на остановке — сесть за $5,
 * Пробел — сразу к следующей остановке (как пропуск поездки в такси), Shift — выйти. На ходу ПКМ — угнать автобус.
 */
public final class Buses {
    private Buses() {}

    private static final double LANE = 2.5, V_VIRTUAL = 7.0, SPAWN_R = 92, DESPAWN_R = 112;
    private static final int DWELL = 8, FARE = 5;

    // ------------------------------------------------------------------ маршруты
    public static final class Stop {
        final String name;
        final int idx;            // вершина маршрута, где стоит автобус
        double s;
        final Vec3d lane;
        final int mx, mz;         // середина квартала на осевой
        final int fx, fz, rx, rz; // вперёд по ходу и вправо (к тротуару)
        boolean built, bad;
        String lastText = "";

        Stop(String name, int idx, Vec3d lane, int mx, int mz, int fx, int fz) {
            this.name = name;
            this.idx = idx;
            this.lane = lane;
            this.mx = mx;
            this.mz = mz;
            this.fx = fx;
            this.fz = fz;
            this.rx = -fz;
            this.rz = fx;
        }

        BlockPos at(int t, int c, int y) {
            return new BlockPos(mx + fx * t + rx * c, Island.G + y, mz + fz * t + rz * c);
        }

        /** Скамейка под навесом (куда садятся ждущие). */
        public Vec3d bench() {
            BlockPos b = at(0, 6, 1);
            return new Vec3d(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
        }

        /** Точка на тротуаре у передней двери автобуса. */
        Vec3d door() {
            return new Vec3d(mx + fx * 1.5 + rx * 5 + 0.5, Island.G + 1, mz + fz * 1.5 + rz * 5 + 0.5);
        }
    }

    public static final class Route {
        public final int num;
        public final String title;
        final int color;
        final Block post;
        final int[][] waypoints;
        final List<Vec3d> pts = new ArrayList<>();
        double[] cum;
        double len;
        final List<Stop> stops = new ArrayList<>();
        final int buses;

        Route(int num, String title, int color, Block post, int buses, int[][] waypoints) {
            this.num = num;
            this.title = title;
            this.color = color;
            this.post = post;
            this.buses = buses;
            this.waypoints = waypoints;
        }

        Vec3d at(double s) {
            s = mod(s);
            int i = seg(s);
            Vec3d a = pts.get(i), b = pts.get((i + 1) % pts.size());
            double l = cum[i + 1] - cum[i];
            return l < 1e-6 ? a : a.lerp(b, (s - cum[i]) / l);
        }

        float yawAt(double s) {
            int i = seg(mod(s));
            Vec3d a = pts.get(i), b = pts.get((i + 1) % pts.size());
            return (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z));
        }

        double mod(double s) {
            return ((s % len) + len) % len;
        }

        int seg(double s) {
            int lo = 0, hi = pts.size() - 1;
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (cum[mid] <= s) lo = mid;
                else hi = mid - 1;
            }
            return Math.min(lo, pts.size() - 1);
        }

        /** Ближайшая точка маршрута к позиции (возле прежнего s). */
        double project(Vec3d p, double near) {
            double best = near, bd = Double.MAX_VALUE;
            int n = pts.size();
            for (int i = 0; i < n; i++) {
                Vec3d a = pts.get(i), b = pts.get((i + 1) % n);
                Vec3d ab = b.subtract(a);
                double l2 = ab.x * ab.x + ab.z * ab.z;
                double t = l2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * ab.x + (p.z - a.z) * ab.z) / l2));
                double qx = a.x + ab.x * t - p.x, qz = a.z + ab.z * t - p.z;
                double s = cum[i] + Math.sqrt(l2) * t;
                double ds = Math.abs(mod(s - near + len / 2) - len / 2);
                double d = qx * qx + qz * qz + (ds > 120 ? 10000 : 0);
                if (d < bd) { bd = d; best = s; }
            }
            return mod(best);
        }

        /** Следующая остановка после s (строго дальше чем на 1 блок). */
        Stop nextStop(double s) {
            Stop best = stops.get(0);
            double bd = Double.MAX_VALUE;
            for (Stop st : stops) {
                double d = mod(st.s - s - 1);
                if (d < bd) { bd = d; best = st; }
            }
            return best;
        }

        double ahead(double from, double to) {
            return mod(to - from);
        }
    }

    static final class Bus {
        final Route r;
        double s;
        int dwell;
        @Nullable Stop atStop;
        @Nullable UUID car, driver;
        final List<UUID> riders = new ArrayList<>();
        long deadUntil, lastMove;
        double lastS;
        final List<UUID> boarding = new ArrayList<>();
        int boardTimer;

        Bus(Route r, double s) {
            this.r = r;
            this.s = s;
        }
    }

    private static final List<Route> ROUTES = new ArrayList<>();
    private static final List<Bus> BUSES = new ArrayList<>();
    private static boolean ready, tried;

    private static final String[][] NAMES = {
            {"DOWNTOWN", "Сити-Банк", "Площадь Башен", "Биржа", "БЦ «Кристалл»", "Неоновый бульвар", "Отель «Метрополь»", "Мэрия", "Галерея", "Медиа-холдинг", "Арт-квартал"},
            {"SUBURB", "Школа", "Тихий сквер", "Почта", "Поликлиника", "Кленовая роща", "Рынок", "Библиотека", "Стадион", "Детский сад", "Парк Дружбы", "Пекарня"},
            {"RIVIERA", "Казино «Корона»", "Пристань", "Отель «Лазурь»", "Набережная", "Пальмовая аллея", "Яхт-клуб", "Аквапарк", "Стрип"},
            {"INDUSTRIAL", "Завод", "Склады", "Депо", "Элеватор"}};
    private static final Map<String, Integer> NAME_USED = new HashMap<>();

    private static String stopName(Island.Zone zn, int mx, int mz) {
        for (String[] pool : NAMES) {
            if (!pool[0].equals(zn.name())) continue;
            int k = NAME_USED.merge(pool[0], 1, Integer::sum) - 1;
            if (k < pool.length - 1) return pool[1 + k];
        }
        String[] st = Dispatch.street(mx, mz);
        return st != null ? st[0] : "Остановка";
    }

    private static void defineRoutes() {
        ROUTES.clear();
        BUSES.clear();
        NAME_USED.clear();
        ROUTES.add(new Route(1, "Деловой центр", 0xC8322B, Blocks.RED_CONCRETE, 2,
                new int[][]{{216, -600}, {648, -600}, {648, -264}, {216, -264}}));
        ROUTES.add(new Route(2, "Центр — Ривьера", 0x2C79C8, Blocks.BLUE_CONCRETE, 2,
                new int[][]{{456, -504}, {264, -504}, {-1128, -312}, {-1368, -312}, {-1368, -120}, {-1128, -120}}));
        ROUTES.add(new Route(3, "Пригород", 0x3C9A4A, Blocks.LIME_CONCRETE, 1,
                new int[][]{{-504, -216}, {-216, -216}, {-216, 216}, {-504, 216}}));
        for (Route r : ROUTES) {
            build(r);
            if (r.stops.isEmpty() || r.len < 100) continue;
            for (int i = 0; i < r.buses; i++) BUSES.add(new Bus(r, r.len * i / r.buses));
            com.yompix.novograd.Novograd.LOG.info("[bus] маршрут №{} «{}»: {} блоков, {} точек, {} остановок", r.num, r.title, (int) r.len, r.pts.size(), r.stops.size());
        }
    }

    private static int[] dir(int[] a, int[] b) {
        return new int[]{Integer.signum(b[0] - a[0]), Integer.signum(b[1] - a[1])};
    }

    /** Узлы по дорогам острова (A* как у навигатора), потом точки по правой полосе с плавными поворотами и остановки. */
    private static void build(Route r) {
        List<int[]> nodes = new ArrayList<>();
        int n = r.waypoints.length;
        for (int i = 0; i < n; i++) {
            int[] a = r.waypoints[i], b = r.waypoints[(i + 1) % n];
            List<int[]> path = IslandRoads.route(a[0], a[1], b[0], b[1]);
            if (path.isEmpty()) path = List.of(a, b);
            for (int[] q : path) {
                if (!nodes.isEmpty() && nodes.get(nodes.size() - 1)[0] == q[0] && nodes.get(nodes.size() - 1)[1] == q[1]) continue;
                nodes.add(q);
            }
        }
        if (nodes.size() > 1 && Arrays.equals(nodes.get(0), nodes.get(nodes.size() - 1))) nodes.remove(nodes.size() - 1);
        // разбить длинные рёбра (A* шагает по 48, но на всякий случай) — нужны рёбра ровно по осям
        int m = nodes.size();
        if (m < 3) return;
        double y = Island.G + 1;
        double lastStopS = -1e9;
        for (int i = 0; i < m; i++) {
            int[] p = nodes.get((i + m - 1) % m), c = nodes.get(i), nx = nodes.get((i + 1) % m);
            int[] din = dir(p, c), dout = dir(c, nx);
            double rinX = -din[1], rinZ = din[0], routX = -dout[1], routZ = dout[0];
            if (din[0] == dout[0] && din[1] == dout[1]) {
                r.pts.add(new Vec3d(c[0] + 0.5 + rinX * LANE, y, c[1] + 0.5 + rinZ * LANE));
            } else if (din[0] == -dout[0] && din[1] == -dout[1]) {
                // разворот на перекрёстке
                r.pts.add(new Vec3d(c[0] + 0.5 + rinX * LANE + din[0] * 2, y, c[1] + 0.5 + rinZ * LANE + din[1] * 2));
                r.pts.add(new Vec3d(c[0] + 0.5 + din[0] * 4, y, c[1] + 0.5 + din[1] * 4));
                r.pts.add(new Vec3d(c[0] + 0.5 + routX * LANE + din[0] * 2, y, c[1] + 0.5 + routZ * LANE + din[1] * 2));
            } else {
                r.pts.add(new Vec3d(c[0] + 0.5 - din[0] * 9 + rinX * LANE, y, c[1] + 0.5 - din[1] * 9 + rinZ * LANE));
                r.pts.add(new Vec3d(c[0] + 0.5 + rinX * LANE + routX * LANE, y, c[1] + 0.5 + rinZ * LANE + routZ * LANE));
                r.pts.add(new Vec3d(c[0] + 0.5 + dout[0] * 9 + routX * LANE, y, c[1] + 0.5 + dout[1] * 9 + routZ * LANE));
            }
            // середина квартала до следующего узла: точка полосы (и, может быть, остановка)
            int len = Math.abs(nx[0] - c[0]) + Math.abs(nx[1] - c[1]);
            if (len < 40) continue;
            int mx = (c[0] + nx[0]) / 2, mz = (c[1] + nx[1]) / 2;
            Vec3d stopPt = new Vec3d(mx + 0.5 + dout[0] * 1.0 + routX * 3.0, y, mz + 0.5 + dout[1] * 1.0 + routZ * 3.0);
            r.pts.add(stopPt);
            // остановка: настоящая городская улица с тротуаром, не трасса; не чаще чем через ~200 блоков
            Island.Zone zn = Island.zone(mx, mz);
            int sx = (int) Math.floor(mx + routX * 5.5), sz = (int) Math.floor(mz + routZ * 5.5);
            boolean street = Island.urban(zn) && Island.urban(Island.zone(sx, sz)) && Island.road(mx, mz) && !Island.road(sx, sz)
                    && Island.urban(Island.zone((int) (mx + routX * 10), (int) (mz + routZ * 10)));
            if (street) {
                double sNow = 0; // считаем ниже, когда будет cum; пока — индекс
                r.stops.add(new Stop("", r.pts.size() - 1, stopPt, mx, mz, dout[0], dout[1]));
            }
        }
        // длины
        int k = r.pts.size();
        r.cum = new double[k + 1];
        for (int i = 0; i < k; i++) r.cum[i + 1] = r.cum[i] + r.pts.get(i).distanceTo(r.pts.get((i + 1) % k));
        r.len = r.cum[k];
        for (Stop st : r.stops) st.s = r.cum[st.idx];
        // прореживаем: остановки не ближе ~210 блоков друг к другу
        List<Stop> keep = new ArrayList<>();
        double gap = r.num == 2 ? 260 : 200;
        for (Stop st : r.stops) {
            if (!keep.isEmpty() && st.s - keep.get(keep.size() - 1).s < gap) continue;
            keep.add(st);
        }
        if (keep.size() > 1 && r.len - keep.get(keep.size() - 1).s + keep.get(0).s < gap * 0.7) keep.remove(keep.size() - 1);
        r.stops.clear();
        for (Stop st : keep) {
            Stop named = new Stop(stopName(Island.zone(st.mx, st.mz), st.mx, st.mz), st.idx, st.lane, st.mx, st.mz, st.fx, st.fz);
            named.s = st.s;
            r.stops.add(named);
        }
        lastStopS = 0;
    }

    private static boolean ensure(ServerWorld w) {
        if (ready) return true;
        if (tried) return false;
        tried = true;
        if (!(w.getChunkManager().getChunkGenerator() instanceof com.yompix.novograd.world.IslandChunkGenerator)) return false;
        long t0 = System.currentTimeMillis();
        defineRoutes();
        ready = !BUSES.isEmpty();
        com.yompix.novograd.Novograd.LOG.info("[bus] маршруты построены за {} мс", System.currentTimeMillis() - t0);
        return ready;
    }

    /** Остановки (скамейки под навесами) для ИИ жителей: можно ждать автобус. */
    public static List<Vec3d> stopsNear(Vec3d at, double r) {
        List<Vec3d> out = new ArrayList<>();
        for (Route rt : ROUTES) for (Stop st : rt.stops) if (st.built && st.bench().squaredDistanceTo(at) < r * r) out.add(st.bench());
        return out;
    }

    // ------------------------------------------------------------------ тик
    public static void tick(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        long t = server.getTicks();
        if (t % 20 == 7 && !ensure(w)) return;
        if (!ready) return;
        for (Bus b : BUSES) {
            CarEntity car = b.car == null ? null : w.getEntity(b.car) instanceof CarEntity c ? c : null;
            if (b.car != null && (car == null || car.isRemoved())) {
                release(w, b, null);
                continue;
            }
            if (car != null) {
                // угнали (водителя нет) или разбили
                Entity drv = b.driver == null ? null : w.getEntity(b.driver);
                if (car.wrecked() || drv == null || drv.getVehicle() != car) {
                    hijacked(w, b, car);
                    continue;
                }
            }
            if (t % 20 != 3) continue;
            if (car == null) virtualStep(w, b);
            else realStep(w, b, car);
        }
        if (t % 40 == 11) signs(w);
        if (t % 20 == 5) hud(w);
        if (t % 200 == 13) strays(w);
    }

    private static boolean playerNear(ServerWorld w, Vec3d at, double r) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            double dx = p.getX() - at.x, dz = p.getZ() - at.z;
            if (dx * dx + dz * dz < r * r) return true;
        }
        return false;
    }

    /** Автобус «за кадром»: едет по маршруту со средней скоростью и стоит на остановках. */
    private static void virtualStep(ServerWorld w, Bus b) {
        long now = w.getTime();
        if (now < b.deadUntil) return;
        if (b.dwell > 0) {
            b.dwell--;
            if (b.dwell == 0) b.atStop = null;
        } else {
            Stop next = b.r.nextStop(b.s);
            double d = b.r.ahead(b.s, next.s);
            if (d <= V_VIRTUAL) {
                b.s = next.s;
                b.dwell = DWELL;
                b.atStop = next;
            } else b.s = b.r.mod(b.s + V_VIRTUAL);
        }
        Vec3d at = b.r.at(b.s);
        if (playerNear(w, at, SPAWN_R) && w.isChunkLoaded(BlockPos.ofFloored(at)) && !playerNear(w, at, 18)) materialize(w, b);
    }

    private static void materialize(ServerWorld w, Bus b) {
        Vec3d at = b.r.at(b.s);
        CarEntity car = new CarEntity(ModContent.CAR, w);
        car.refreshPositionAndAngles(at.x, at.y, at.z, b.r.yawAt(b.s), 0);
        car.setVariant(CarEntity.BUS);
        car.paint(b.r.color);
        car.setFuel(CarEntity.FUEL_MAX);
        TrafficLights.obey(car);
        w.spawnEntity(car);
        var rnd = w.getRandom();
        CitizenEntity drv = new CitizenEntity(ModContent.CITIZEN, w);
        int skin = CitizenEntity.randomSkin(rnd);
        drv.setup(skin, CitizenEntity.Role.CITIZEN, true, Names.random(rnd, CitizenEntity.isSlim(skin)));
        drv.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        w.spawnEntity(drv);
        drv.startRiding(car, true);
        b.car = car.getUuid();
        b.driver = drv.getUuid();
        b.riders.clear();
        int pax = rnd.nextInt(3);
        for (int i = 0; i < pax; i++) {
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            int sk = CitizenEntity.randomSkin(rnd);
            c.setup(sk, CitizenEntity.Role.CITIZEN, true, Names.random(rnd, CitizenEntity.isSlim(sk)));
            c.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
            w.spawnEntity(c);
            c.startRiding(car, true);
            b.riders.add(c.getUuid());
        }
        b.lastMove = w.getTime();
        b.lastS = b.s;
        if (b.atStop != null) {
            car.setVelocity(Vec3d.ZERO);
            car.toggleLight(CarEntity.RIGHT);
        } else depart(w, b, car);
    }

    /** Убрать машину (игрок далеко): автобус дальше едет «виртуально». */
    private static void dematerialize(ServerWorld w, Bus b, CarEntity car) {
        for (Entity e : new ArrayList<>(car.getPassengerList())) {
            if (e instanceof ServerPlayerEntity) continue;
            e.stopRiding();
            e.discard();
        }
        car.discard();
        b.car = null;
        b.driver = null;
        b.riders.clear();
    }

    private static void release(ServerWorld w, Bus b, @Nullable CarEntity car) {
        b.car = null;
        b.driver = null;
        b.riders.clear();
        b.atStop = null;
        b.dwell = 0;
        b.deadUntil = w.getTime() + 20 * 120; // новый автобус выйдет из парка через 2 минуты
        b.s = b.r.mod(b.s + b.r.len / 2);
    }

    /** Водителя выкинули (угон) или автобус разбит: машина больше не наша, «новый» автобус выйдет позже. */
    private static void hijacked(ServerWorld w, Bus b, CarEntity car) {
        for (UUID u : b.riders) if (w.getEntity(u) instanceof CitizenEntity c && c.getVehicle() == car) {
            c.stopRiding();
            c.panic(car.getPos());
            c.removeLater(400);
        }
        ServerPlayerEntity thief = car.getControllingPassenger() instanceof ServerPlayerEntity sp ? sp : null;
        if (thief != null && !car.wrecked()) {
            Dispatch.report(w, car.getPos(), "угон автобуса маршрута №" + b.r.num, null, thief);
            thief.sendMessage(Text.literal("🚌 Угнал автобус №" + b.r.num + "!").formatted(Formatting.GOLD), true);
        }
        car.setAmbient(false);
        release(w, b, car);
    }

    private static void depart(ServerWorld w, Bus b, CarEntity car) {
        b.atStop = null;
        b.dwell = 0;
        if ((car.lights() & CarEntity.RIGHT) != 0) car.toggleLight(CarEntity.RIGHT);
        Stop next = b.r.nextStop(b.s);
        List<Vec3d> pts = pointsTo(b.r, b.s, next);
        w.playSound(null, car.getBlockPos(), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.NEUTRAL, 0.7f, 0.55f);
        car.setRoute(pts, 0.45f, () -> arrive(w, b, car, next));
    }

    private static List<Vec3d> pointsTo(Route r, double s, Stop st) {
        List<Vec3d> out = new ArrayList<>();
        int n = r.pts.size();
        int i = (r.seg(r.mod(s)) + 1) % n;
        for (int guard = 0; guard < n + 1; guard++) {
            out.add(r.pts.get(i));
            if (i == st.idx) break;
            i = (i + 1) % n;
        }
        return out;
    }

    private static void arrive(ServerWorld w, Bus b, CarEntity car, Stop st) {
        if (car.isRemoved() || b.car == null || !b.car.equals(car.getUuid())) return;
        b.s = st.s;
        b.atStop = st;
        b.dwell = DWELL;
        car.speed = 0;
        if ((car.lights() & CarEntity.RIGHT) == 0) car.toggleLight(CarEntity.RIGHT);
        w.playSound(null, car.getBlockPos(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.NEUTRAL, 0.8f, 0.5f);
        exchange(w, b, st, car);
    }

    /** На остановке: ждущие жители идут к двери и «садятся», кто-то выходит. */
    private static void exchange(ServerWorld w, Bus b, Stop st, CarEntity car) {
        var r = w.getRandom();
        Vec3d door = st.door();
        b.boarding.clear();
        int n = 0;
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, new net.minecraft.util.math.Box(door, door).expand(9, 3, 9),
                c -> c.isAlive() && !c.hasVehicle() && c.getStoryId().isEmpty() && c.getRole() == CitizenEntity.Role.CITIZEN && !c.knockedDown() && c.isAmbient())) {
            if (n++ >= 2) break;
            c.walkRoute(List.of(door), 0.9, null, net.minecraft.item.ItemStack.EMPTY, null);
            b.boarding.add(c.getUuid());
        }
        b.boardTimer = 6;
        // выходят 0–2 пассажира: появляются у двери
        int out = r.nextInt(3);
        for (int i = 0; i < out; i++) {
            CitizenEntity c = com.yompix.novograd.gta.FunUtil.citizen(w, door.add(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5), CitizenEntity.Role.CITIZEN, null);
            c.setAmbient(true);
        }
        // кто сидел в салоне — один может выйти
        if (!b.riders.isEmpty() && r.nextBoolean()) {
            UUID u = b.riders.remove(0);
            if (w.getEntity(u) instanceof CitizenEntity c) {
                c.stopRiding();
                c.refreshPositionAndAngles(door.x, door.y, door.z, c.getYaw(), 0);
                c.setStationary(false);
                c.setAmbient(true);
            }
        }
    }

    /** Настоящий автобус: позиция на маршруте, стоянка на остановке, застрял — исчезает за кадром. */
    private static void realStep(ServerWorld w, Bus b, CarEntity car) {
        b.s = b.r.project(car.getPos(), b.s);
        long now = w.getTime();
        if (Math.abs(b.r.mod(b.s - b.lastS + b.r.len / 2) - b.r.len / 2) > 2 || b.atStop != null) {
            b.lastS = b.s;
            b.lastMove = now;
        }
        if (b.atStop != null) {
            if (b.boardTimer > 0 && --b.boardTimer == 0 || b.dwell <= 1) {
                for (UUID u : b.boarding) if (w.getEntity(u) instanceof CitizenEntity c && !c.hasVehicle() && c.squaredDistanceTo(car) < 14 * 14) c.discard(); // сели
                b.boarding.clear();
            }
            boolean playerBoarding = false;
            for (ServerPlayerEntity p : w.getPlayers()) if (p.getVehicle() == null && p.squaredDistanceTo(b.atStop.door()) < 6 * 6) playerBoarding = true;
            if (--b.dwell <= 0 && !(playerBoarding && b.dwell > -6)) depart(w, b, car);
        } else if (!car.onRoute()) {
            depart(w, b, car);
        }
        boolean passenger = false;
        for (Entity e : car.getPassengerList()) if (e instanceof ServerPlayerEntity) passenger = true;
        if (!passenger && !playerNear(w, car.getPos(), DESPAWN_R)) {
            dematerialize(w, b, car);
            return;
        }
        // застрял больше 40 с и никто не смотрит — убираем, автобус «проедет» виртуально
        if (!passenger && now - b.lastMove > 20 * 40 && !playerNear(w, car.getPos(), 40)) dematerialize(w, b, car);
    }

    /** Брошенные автобусы с водителем без маршрута (после перезахода) — убрать. */
    private static void strays(ServerWorld w) {
        Set<UUID> ours = new HashSet<>();
        for (Bus b : BUSES) if (b.car != null) ours.add(b.car);
        for (ServerPlayerEntity p : w.getPlayers())
            for (CarEntity c : w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(128, 30, 128),
                    c -> c.getVariant() == CarEntity.BUS && c.hasNpcDriver() && !c.onRoute() && !ours.contains(c.getUuid()))) {
                boolean player = false;
                for (Entity e : c.getPassengerList()) if (e instanceof ServerPlayerEntity) player = true;
                if (player) continue;
                for (Entity e : new ArrayList<>(c.getPassengerList())) e.discard();
                c.discard();
            }
    }

    /** Перед сохранением мира: наши машины и водители не должны остаться в чанках. */
    public static void onStop(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        for (Bus b : BUSES) {
            if (b.car != null && w.getEntity(b.car) instanceof CarEntity car) {
                for (Entity e : new ArrayList<>(car.getPassengerList())) {
                    e.stopRiding();
                    if (!(e instanceof ServerPlayerEntity)) e.discard();
                }
                car.discard();
            }
            b.car = null;
        }
        ready = false;
        tried = false;
    }

    // ------------------------------------------------------------------ игрок: посадка, пропуск, подсказка
    @Nullable
    private static Bus busOf(CarEntity car) {
        for (Bus b : BUSES) if (b.car != null && b.car.equals(car.getUuid())) return b;
        return null;
    }

    /** ПКМ по автобусу (из CarEntity.interact). null — обычная логика машины (угон). */
    @Nullable
    public static ActionResult interact(CarEntity car, ServerPlayerEntity p) {
        if (car.getVariant() != CarEntity.BUS || !car.hasNpcDriver()) return null;
        Bus b = busOf(car);
        if (b == null) return null;
        if (b.atStop == null || p.isSneaking()) {
            // на ходу — угон: сначала выходят пассажиры, потом водителя выкинет CarEntity
            ServerWorld w = p.getServerWorld();
            for (UUID u : b.riders) if (w.getEntity(u) instanceof CitizenEntity c && c.getVehicle() == car) {
                c.stopRiding();
                c.panic(car.getPos());
                c.removeLater(400);
            }
            b.riders.clear();
            return null;
        }
        if (car.hasPassenger(p)) return ActionResult.CONSUME;
        if (car.getPassengerList().size() >= 4) {
            // мест нет — один из пассажиров-жителей выходит
            for (UUID u : b.riders) if (p.getServerWorld().getEntity(u) instanceof CitizenEntity c) {
                c.stopRiding();
                c.discard();
                b.riders.remove(u);
                break;
            }
        }
        if (!p.isCreative() && !Life.pay(p, FARE, "проезд в автобусе №" + b.r.num)) return ActionResult.CONSUME;
        if (p.startRiding(car)) {
            b.dwell = Math.max(b.dwell, 3);
            p.sendMessage(Text.literal("🚌 Автобус №" + b.r.num + " «" + b.r.title + "». Пробел — до следующей остановки, Shift — выйти").formatted(Formatting.YELLOW), false);
        }
        return ActionResult.CONSUME;
    }

    @Nullable
    private static Bus ridden(ServerPlayerEntity p) {
        return p.getVehicle() instanceof CarEntity car && car.getVariant() == CarEntity.BUS && car.getControllingPassenger() != p ? busOf(car) : null;
    }

    /** Пробел в автобусе: затемнение — и ты уже на следующей остановке (в автобусе). */
    public static void skip(ServerPlayerEntity p) {
        Bus b = ridden(p);
        if (b == null) return;
        ServerWorld w = p.getServerWorld();
        if (!(w.getEntity(b.car) instanceof CarEntity car)) return;
        Stop next = b.r.nextStop(b.atStop != null ? b.atStop.s : b.s);
        NbtCompound n = new NbtCompound();
        n.putString("label", "АВТОБУС №" + b.r.num);
        LifeNet.send(p, "city_fade", n);
        p.stopRiding();
        dematerialize(w, b, car);
        b.s = next.s;
        b.atStop = next;
        b.dwell = DWELL;
        Vec3d door = next.door();
        w.getChunk(BlockPos.ofFloored(door));
        w.getChunk(BlockPos.ofFloored(next.lane));
        p.teleport(w, door.x, door.y + 0.1, door.z, b.r.yawAt(next.s), p.getPitch());
        materialize(w, b);
        if (b.car != null && w.getEntity(b.car) instanceof CarEntity nb) {
            p.startRiding(nb, true);
            arrive(w, b, nb, next);
        }
        p.sendMessage(Text.literal("🚌 Остановка «" + next.name + "»").formatted(Formatting.YELLOW), true);
    }

    private static final Set<UUID> HUD_ON = new HashSet<>();

    private static void hud(ServerWorld w) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            Bus b = ridden(p);
            if (b == null) {
                if (HUD_ON.remove(p.getUuid())) LifeNet.send(p, "city_bus", new NbtCompound());
                continue;
            }
            HUD_ON.add(p.getUuid());
            Stop next = b.atStop != null ? b.atStop : b.r.nextStop(b.s);
            NbtCompound n = new NbtCompound();
            n.putInt("num", b.r.num);
            n.putString("title", b.r.title);
            n.putInt("color", b.r.color);
            n.putString("stop", next.name);
            n.putBoolean("at", b.atStop != null);
            n.putInt("eta", (int) (b.r.ahead(b.s, next.s) / 8.0));
            LifeNet.send(p, "city_bus", n);
        }
    }

    // ------------------------------------------------------------------ остановки: навес, табличка «через N мин»
    /** Через сколько секунд у остановки будет ближайший автобус маршрута. */
    private static int eta(Route r, Stop st) {
        int best = Integer.MAX_VALUE;
        for (Bus b : BUSES) {
            if (b.r != r) continue;
            if (b.atStop == st) return 0;
            long dead = b.deadUntil - 0;
            double d = r.ahead(b.s, st.s);
            int stopsBetween = 0;
            for (Stop o : r.stops) if (o != st && r.ahead(b.s, o.s) < d) stopsBetween++;
            int sec = (int) (d / V_VIRTUAL) + stopsBetween * DWELL + Math.max(0, b.dwell);
            best = Math.min(best, sec);
        }
        return best;
    }

    private static void signs(ServerWorld w) {
        for (Route r : ROUTES)
            for (Stop st : r.stops) {
                if (st.bad || !playerNear(w, new Vec3d(st.mx, 0, st.mz), 72)) continue;
                BlockPos sign = st.at(-3, 5, 2);
                if (!w.isChunkLoaded(sign) || !w.isChunkLoaded(st.at(3, 6, 1)) || !w.isChunkLoaded(st.at(-3, 6, 1))) continue;
                if (!st.built) {
                    if (!(w.getBlockState(sign).getBlock() instanceof WallSignBlock) && !buildShelter(w, r, st)) {
                        st.bad = true;
                        continue;
                    }
                    st.built = true;
                    st.lastText = "";
                }
                if (!(w.getBlockEntity(sign) instanceof SignBlockEntity be)) continue;
                int sec = eta(r, st);
                String when = sec <= 0 ? "на остановке" : sec < 50 ? "прибывает" : sec > 3600 ? "нет рейсов" : "через " + Math.max(1, Math.round(sec / 60f)) + " мин";
                String text = r.num + "|" + st.name + "|" + when;
                if (text.equals(st.lastText)) continue;
                st.lastText = text;
                String nm = st.name.length() > 15 ? st.name.substring(0, 15) : st.name;
                SignText s = new SignText().withGlowing(true).withColor(DyeColor.WHITE)
                        .withMessage(0, Text.literal("🚌 №" + r.num).formatted(Formatting.BOLD))
                        .withMessage(1, Text.literal(nm))
                        .withMessage(2, Text.literal(when).formatted(Formatting.YELLOW))
                        .withMessage(3, Text.literal("$" + FARE + " · " + (r.title.length() > 11 ? r.title.substring(0, 11) : r.title)));
                be.setText(s, true);
                be.setText(s, false);
                be.setWaxed(true);
            }
    }

    /** Навес у тротуара: стойки цвета маршрута, крыша, стекло, скамейка, табличка. Проход по тротуару (полоса 5) остаётся свободным. */
    private static boolean buildShelter(ServerWorld w, Route r, Stop st) {
        for (int t = -3; t <= 3; t++)
            for (int c = 5; c <= 6; c++) {
                BlockState g = w.getBlockState(st.at(t, c, 0));
                if (!g.isOf(ModContent.SIDEWALK)) return false;
            }
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        BlockState air = Blocks.AIR.getDefaultState();
        for (int t = -3; t <= 3; t++)
            for (int c = 5; c <= 6; c++)
                for (int y = 1; y <= 4; y++) if (!w.getBlockState(st.at(t, c, y)).isAir()) w.setBlockState(st.at(t, c, y), air, flags);
        Direction right = Direction.fromVector(st.rx, 0, st.rz);
        if (right == null) right = Direction.NORTH;
        BlockState slab = Blocks.SMOOTH_STONE_SLAB.getDefaultState().with(SlabBlock.TYPE, net.minecraft.block.enums.SlabType.BOTTOM);
        for (int t = -3; t <= 3; t++) for (int c = 5; c <= 6; c++) w.setBlockState(st.at(t, c, 4), slab, flags);
        for (int y = 1; y <= 3; y++) {
            w.setBlockState(st.at(-3, 6, y), r.post.getDefaultState(), flags);
            w.setBlockState(st.at(3, 6, y), Blocks.IRON_BARS.getDefaultState(), flags);
        }
        for (int t = -2; t <= 2; t++)
            for (int y = 2; y <= 3; y++) w.setBlockState(st.at(t, 6, y), Blocks.GLASS_PANE.getDefaultState(), Block.NOTIFY_ALL);
        for (int t = -1; t <= 1; t++)
            w.setBlockState(st.at(t, 6, 1), Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, right), flags);
        w.setBlockState(st.at(2, 6, 1), Blocks.COMPOSTER.getDefaultState(), flags); // урна
        w.setBlockState(st.at(-3, 5, 2), Blocks.DARK_OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, right.getOpposite()), Block.NOTIFY_ALL);
        // «жёлтая» отметка на краю тротуара, где встаёт автобус
        for (int t = -2; t <= 3; t++) {
            BlockPos edge = st.at(t, 4, 0);
            if (w.getBlockState(edge).isOf(ModContent.ASPHALT)) w.setBlockState(edge, Blocks.YELLOW_CONCRETE.getDefaultState(), flags);
        }
        return true;
    }

    public static void onLeave(ServerPlayerEntity p) {
        HUD_ON.remove(p.getUuid());
    }

    /** Для отладки и автотеста: /city bus … */
    public static String debug(ServerPlayerEntity p, String what) {
        ServerWorld w = p.getServerWorld();
        if (!ensure(w) && !ready) return "Автобусы только в мире острова";
        if (what.startsWith("tp")) {
            String[] a = what.split(" ");
            int ri = a.length > 1 ? Integer.parseInt(a[1]) - 1 : 0, si = a.length > 2 ? Integer.parseInt(a[2]) : 0;
            Route r = ROUTES.get(Math.max(0, Math.min(ri, ROUTES.size() - 1)));
            if (r.stops.isEmpty()) return "Нет остановок";
            Stop st = r.stops.get(Math.floorMod(si, r.stops.size()));
            Vec3d d = st.door();
            w.getChunk(BlockPos.ofFloored(d));
            p.teleport(w, d.x + st.rx * 1.5, d.y + 0.1, d.z + st.rz * 1.5, b2yaw(st), 10);
            return "Остановка «" + st.name + "» маршрута №" + r.num;
        }
        if (what.startsWith("call")) {
            // ближайший автобус маршрута — к ближайшей остановке (для теста)
            Stop best = null;
            Route br = null;
            double bd = Double.MAX_VALUE;
            for (Route r : ROUTES) for (Stop st : r.stops) {
                double d = st.door().squaredDistanceTo(p.getPos());
                if (d < bd) { bd = d; best = st; br = r; }
            }
            if (best == null) return "Нет остановок";
            for (Bus b : BUSES) if (b.r == br) {
                if (b.car != null && w.getEntity(b.car) instanceof CarEntity c) dematerialize(w, b, c);
                b.deadUntil = 0;
                b.s = br.mod(best.s - 70);
                b.dwell = 0;
                b.atStop = null;
                return "Автобус №" + br.num + " едет к «" + best.name + "»";
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Route r : ROUTES) {
            sb.append("№").append(r.num).append(" ").append((int) r.len).append(" бл., остановок ").append(r.stops.size()).append(": ");
            for (Stop st : r.stops) sb.append(st.name).append(" (").append(st.mx).append(",").append(st.mz).append(") ").append(eta(r, st)).append("с; ");
            sb.append("\n");
        }
        for (Bus b : BUSES) sb.append("bus №").append(b.r.num).append(" s=").append((int) b.s).append(b.car != null ? " REAL" : "").append(b.atStop != null ? " stop" : "").append("; ");
        return sb.toString();
    }

    private static float b2yaw(Stop st) {
        return (float) Math.toDegrees(Math.atan2(-(-st.rx), -st.rz)); // лицом к дороге
    }
}
