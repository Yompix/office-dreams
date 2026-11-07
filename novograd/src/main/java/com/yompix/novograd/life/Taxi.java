package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.world.TownBuilder;
import com.yompix.novograd.world.TownState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Вызов такси из телефона или с карты: если в сети есть игрок-таксист — заказ уходит ему,
 * иначе приезжает жёлтая машина с водителем и сама везёт по дорогам куда скажешь.
 */
public final class Taxi {
    private Taxi() {}

    /** Адреса для такси: id места в городе → название. */
    public static final Map<String, String> PLACES = new LinkedHashMap<>();

    static {
        // Новоград
        PLACES.put("garage_door", "Нитро-Гараж");
        PLACES.put("downtown_plaza", "Деловой центр");
        PLACES.put("bank_tower", "Сити-Банк");
        PLACES.put("club_neon", "Клуб «Неон»");
        PLACES.put("casino_dt", "Казино «Корона»");
        PLACES.put("bar_door", "Бар «Ржавый якорь»");
        PLACES.put("pizza_door", "Пиццерия «Мама Мия»");
        PLACES.put("burger_door", "Жир-Бургер");
        PLACES.put("gunshop_door", "Оружие «Калибр»");
        PLACES.put("police_station", "Полиция");
        PLACES.put("hospital", "Больница");
        PLACES.put("chop_shop", "Разборка у промзоны");
        PLACES.put("port_gate", "Порт");
        PLACES.put("beach_bar", "Пляжный бар");
        PLACES.put("suburb_safehouse", "Убежище в пригороде");
        PLACES.put("shop_clothes", "Шмот-Базар");
        PLACES.put("shop_barber", "Цирюльня «Острая бритва»");
        PLACES.put("shop_gym", "Зал «Железо»");
        PLACES.put("airfield_hangar", "Аэродром");
        // Ривьера
        PLACES.put("riv_strip", "Стрип (Ривьера)");
        PLACES.put("riv_casino", "Казино «Золотая корона»");
        PLACES.put("riv_hotel", "Отель «Лазурь»");
        PLACES.put("riv_marina", "Пристань Ривьеры");
        PLACES.put("riv_beach", "Пляж Ривьеры");
        PLACES.put("shop_boutique", "Бутик «Бархат»");
        // за городом
        PLACES.put("village", "Деревня Сосновка");
        PLACES.put("lake_dock", "Озеро Ясное");
        PLACES.put("mtn_top", "Седые горы");
        PLACES.put("desert_mesa", "Пустошь");
        // кафе, супермаркеты, метро (world/Venues, world/Metro)
        PLACES.put("cafe_dt", "Кофейня «Бодрость»");
        PLACES.put("cafe_suburb", "Кафе «Пончик»");
        PLACES.put("cafe_riviera", "Кафе «Лазурь» (Ривьера)");
        PLACES.put("cafe_beach", "Пляжное кафе «Чайка»");
        PLACES.put("market_suburb", "Супермаркет «Корзинка»");
        PLACES.put("metro_dt", "Метро «Деловой центр»");
        PLACES.put("metro_north", "Метро «Пригород-Север»");
        PLACES.put("metro_port", "Метро «Порт»");
        PLACES.put("metro_suburb", "Метро «Пригород»");
        PLACES.put("metro_riv", "Метро «Ривьера-Восток»");
        PLACES.put("metro_strip", "Метро «Стрип»");
        // «Мираж» — город казино в Пустоши (world/DesertCity)
        PLACES.put("mirage_strip", "Мираж — Стрип");
        PLACES.put("mirage_casino", "Казино «Мираж»");
        PLACES.put("mirage_chapel", "Часовня «Навеки» (Мираж)");
        PLACES.put("mirage_motel", "Мотель «Кактус» (Мираж)");
        PLACES.put("mirage_pawn", "Ломбард «Фортуна» (Мираж)");
        PLACES.put("cafe_mirage", "Дайнер «У Дасти» (Мираж)");
        PLACES.put("market_mirage", "Мираж-Маркет");
        PLACES.put("mirage_air", "Аэропорт «Мираж»");
    }

    record Order(UUID client, Vec3d dest, String destName, int price, long at) {}

    private static final Map<UUID, Order> WAITING = new HashMap<>();   // клиент → заказ, пока едет машина
    private static final Map<UUID, UUID> CARS = new HashMap<>();       // клиент → машина-такси

    /** Для автотеста: машина-такси игрока. */
    @Nullable
    public static UUID carOf(ServerPlayerEntity p) {
        return CARS.get(p.getUuid());
    }

    /** Эта машина — такси, вызванное этим игроком? (тогда он садится пассажиром, а не угоняет) */
    public static boolean isClientOf(ServerPlayerEntity p, CarEntity car) {
        return car.getUuid().equals(CARS.get(p.getUuid()));
    }

    public static int price(Vec3d from, Vec3d to) {
        return 8 + (int) (Math.sqrt(from.squaredDistanceTo(to)) / 5);
    }

    /** dest: id места или "x,z" (метка на карте). */
    public static void call(ServerPlayerEntity p, String dest) {
        ServerWorld w = p.getServerWorld();
        Vec3d to = null;
        String name = PLACES.get(dest);
        if (name != null) to = com.yompix.novograd.story.Story.place(w, dest);
        else if (dest.contains(",")) {
            String[] xz = dest.split(",");
            try {
                int x = Integer.parseInt(xz[0].trim()), z = Integer.parseInt(xz[1].trim());
                to = new Vec3d(x + 0.5, com.yompix.novograd.story.Story.topY(w, x, z), z + 0.5);
                name = "метка на карте";
            } catch (NumberFormatException ignored) {
            }
        }
        if (to == null) return;
        if (CARS.containsKey(p.getUuid()) || WAITING.containsKey(p.getUuid())) {
            p.sendMessage(Text.literal("Такси уже едет к тебе").formatted(Formatting.YELLOW), true);
            return;
        }
        int price = price(p.getPos(), to);
        if (!Life.pay(p, price, "такси: " + name)) return;
        Order o = new Order(p.getUuid(), to, name, price, w.getTime());
        // сначала — живые таксисты
        boolean offered = false;
        for (ServerPlayerEntity d : p.getServer().getPlayerManager().getPlayerList()) {
            if (d == p || Jobs.job(d) != Jobs.Job.TAXI || !Life.get(d).taskKind.isEmpty()) continue;
            Life.message(d, "Такси", p.getName().getString() + " вызывает такси → " + name + ". Оплата $" + price + ". Открой «Работа» → «Принять вызов».");
            offered = true;
        }
        WAITING.put(p.getUuid(), o);
        if (offered) {
            Life.message(p, "Такси", "Ищем водителя… Если никто из игроков не возьмёт заказ за 20 секунд, приедет машина службы.");
        } else {
            dispatchNpc(p, o);
        }
    }

    /** Таксист-игрок принимает ближайший вызов. */
    public static void accept(ServerPlayerEntity driver) {
        if (Jobs.job(driver) != Jobs.Job.TAXI) return;
        for (Order o : WAITING.values()) {
            if (CARS.containsKey(o.client())) continue;
            ServerPlayerEntity c = driver.getServer().getPlayerManager().getPlayer(o.client());
            if (c == null) continue;
            WAITING.remove(o.client());
            LifeData d = Life.get(driver);
            d.taskKind = "pickup:" + o.client();
            d.taskPos = c.getBlockPos();
            d.taskReward = o.price();
            PENDING_DEST.put(o.client(), o);
            Life.message(driver, "Такси", "Заказ принят. Забери " + c.getName().getString() + " (стрелка в углу экрана).");
            Life.message(c, "Такси", "Водитель " + driver.getName().getString() + " едет за тобой. Садись к нему в машину.");
            Life.sync(driver);
            return;
        }
        driver.sendMessage(Text.literal("Свободных вызовов нет").formatted(Formatting.GRAY), true);
    }

    private static final Map<UUID, Order> PENDING_DEST = new HashMap<>();

    /** Машина службы с водителем выезжает к клиенту. */
    private static void dispatchNpc(ServerPlayerEntity p, Order o) {
        ServerWorld w = p.getServerWorld();
        island = w.getChunkManager().getChunkGenerator() instanceof com.yompix.novograd.world.IslandChunkGenerator;
        TownState t = TownState.get(w);
        if (t.origin == null) {
            p.sendMessage(Text.literal("В этом мире нет дорог Новограда — такси не приедет").formatted(Formatting.RED), false);
            Life.get(p).money += o.price();
            WAITING.remove(p.getUuid());
            return;
        }
        // игрок далеко от дорог (лес, горы, пустошь) — такси ждёт на ближайшей дороге, туда и подъезжает
        Vec3d road = nearestRoad(t, p.getPos());
        boolean offRoad = road.distanceTo(p.getPos()) > 40;
        Vec3d start = offRoad ? roadPointAround(t, road, 30) : roadPointAround(t, p.getPos(), 36);
        // держим участок с такси загруженным, пока игрок не сел (иначе далёкая машина «пропадает»)
        release(p);
        net.minecraft.util.math.ChunkPos cp = new net.minecraft.util.math.ChunkPos(net.minecraft.util.math.BlockPos.ofFloored(start));
        w.setChunkForced(cp.x, cp.z, true);
        FORCED.put(p.getUuid(), cp);
        CarEntity car = new CarEntity(ModContent.CAR, w);
        double sy = island ? com.yompix.novograd.story.Story.topY(w, (int) Math.floor(start.x), (int) Math.floor(start.z)) + 0.2 : t.origin.getY();
        car.refreshPositionAndAngles(start.x, sy, start.z, 0, 0);
        car.setVariant(5);
        w.spawnEntity(car);
        CitizenEntity driver = new CitizenEntity(ModContent.CITIZEN, w);
        driver.setup(CitizenEntity.randomSkin(w.getRandom()), CitizenEntity.Role.CITIZEN, true, "Таксист " + Names.random(w.getRandom()));
        driver.refreshPositionAndAngles(start.x, sy, start.z, 0, 0);
        w.spawnEntity(driver);
        driver.startRiding(car, true);
        // переднее пассажирское место занято сумкой водителя — клиент садится назад, обзор не закрывает водитель
        net.minecraft.entity.decoration.ArmorStandEntity bag = new net.minecraft.entity.decoration.ArmorStandEntity(w, start.x, sy, start.z);
        bag.setInvisible(true);
        bag.setNoGravity(true);
        bag.setInvulnerable(true);
        bag.addCommandTag("novograd_taxi_seat");
        w.spawnEntity(bag);
        bag.startRiding(car, true);
        CARS.put(p.getUuid(), car.getUuid());
        Vec3d pickup = road;
        PICKUP.put(p.getUuid(), pickup);
        WAIT_TICKS.remove(p.getUuid());
        List<Vec3d> r = route(t, start, pickup);
        com.yompix.novograd.Novograd.LOG.info("taxi route from {} to {}: {}", start, pickup, r);
        car.setRoute(r, 0.55f, () -> arrivedForClient(p.getUuid(), car));
        int secs = (int) (Math.sqrt(start.squaredDistanceTo(pickup)) / 9) + 3;
        if (offRoad) Life.message(p, "Такси", "Здесь нет дорог — такси ждёт на ближайшей дороге, в " + (int) road.distanceTo(p.getPos()) + " м. Метка на радаре.");
        else Life.message(p, "Такси", "Жёлтая машина едет к тебе, примерно " + secs + " с. Выйди к дороге — золотой столб покажет место.");
        Life.get(p).taskKind = "taxi_wait";
        Life.get(p).taskPos = BlockPos.ofFloored(pickup);
        Life.sync(p);
    }

    /** Как в GTA: в такси можно пропустить дорогу — экран темнеет, и ты уже на месте. */
    private static final Map<UUID, Integer> STUCK = new HashMap<>();
    private static final Map<UUID, Integer> WAIT_TICKS = new HashMap<>();
    private static final Map<UUID, Vec3d> PICKUP = new HashMap<>();
    private static final Map<UUID, net.minecraft.util.math.ChunkPos> FORCED = new HashMap<>();

    private static void release(ServerPlayerEntity p) {
        var cp = FORCED.remove(p.getUuid());
        if (cp != null) p.getServerWorld().setChunkForced(cp.x, cp.z, false);
    }

    public static void skip(ServerPlayerEntity p) {
        UUID cid = CARS.get(p.getUuid());
        LifeData d = Life.get(p);
        ServerWorld w = p.getServerWorld();
        if (cid == null || !(w.getEntity(cid) instanceof CarEntity car) || p.getVehicle() != car || !"taxi_ride".equals(d.taskKind) || d.taskPos == null) return;
        TownState t = TownState.get(w);
        Vec3d drop = island ? Vec3d.ofCenter(d.taskPos) : nearestRoad(t, Vec3d.ofCenter(d.taskPos));
        STUCK.remove(p.getUuid());
        net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
        n.putString("label", "Такси");
        LifeNet.send(p, "taxi_fade", n);
        p.stopRiding();
        for (net.minecraft.entity.Entity e : new java.util.ArrayList<>(car.getPassengerList())) e.discard();
        car.discard();
        CARS.remove(p.getUuid());
        w.getChunk(net.minecraft.util.math.BlockPos.ofFloored(drop));
        int y = com.yompix.novograd.story.Story.topY(w, (int) Math.floor(drop.x), (int) Math.floor(drop.z));
        p.teleport(w, drop.x, y + 0.1, drop.z, p.getYaw(), p.getPitch());
        d.taskKind = "";
        d.taskPos = null;
        Life.sync(p);
        p.sendMessage(Text.literal("Водитель: «Приехали! Хорошего дня»").formatted(Formatting.YELLOW), false);
        Achievements.give(p, "taxi_ride");
    }

    private static void arrivedForClient(UUID client, CarEntity car) {
        if (!(car.getWorld() instanceof ServerWorld w)) return;
        w.playSound(null, car.getBlockPos(), ModContent.CAR_HORN, SoundCategory.NEUTRAL, 1.2f, 1.1f);
        ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(client);
        if (p != null) p.sendMessage(Text.literal("Такси на месте! Садись (ПКМ по машине)").formatted(Formatting.GOLD), true);
    }

    /** Раз в секунду: сел ли клиент, доехал ли, не пора ли такси уехать. */
    public static void tick(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Order o = WAITING.get(p.getUuid());
        if (o != null && !CARS.containsKey(p.getUuid()) && w.getTime() - o.at() > 400) dispatchNpc(p, o);
        // такси едет к игроку, но застряло — через 45 с просто ставим его на место посадки
        if (o != null && CARS.get(p.getUuid()) != null && w.getEntity(CARS.get(p.getUuid())) instanceof CarEntity coming && p.getVehicle() != coming) {
            Vec3d pick = PICKUP.get(p.getUuid());
            int wt = WAIT_TICKS.merge(p.getUuid(), 1, Integer::sum);
            if (pick != null && wt > 45 && coming.squaredDistanceTo(pick) > 12 * 12) {
                int y = com.yompix.novograd.story.Story.topY(w, (int) Math.floor(pick.x), (int) Math.floor(pick.z));
                w.getChunk(net.minecraft.util.math.BlockPos.ofFloored(pick));
                coming.refreshPositionAndAngles(pick.x, y + 0.2, pick.z, coming.getYaw(), 0);
                coming.setRoute(List.of(), 0.5f, null);
                arrivedForClient(p.getUuid(), coming);
                WAIT_TICKS.put(p.getUuid(), -100000);
            }
        }
        UUID cid = CARS.get(p.getUuid());
        if (cid != null && w.getEntity(cid) instanceof CarEntity stuckCar && p.getVehicle() == stuckCar && "taxi_ride".equals(Life.get(p).taskKind)) {
            // застряли в пробке/на объезде дольше 20 секунд — довозим «за кадром», как пропуск поездки
            int st = stuckCar.getVelocity().horizontalLengthSquared() < 0.0004 ? STUCK.merge(p.getUuid(), 1, Integer::sum) : 0;
            if (st == 0) STUCK.remove(p.getUuid());
            if (st > 20) {
                skip(p);
                return;
            }
        }
        if (cid != null && w.getEntity(cid) instanceof CarEntity car) {
            Order ord = WAITING.get(p.getUuid());
            if (ord != null && p.getVehicle() == car && !car.onRoute()) {
                WAITING.remove(p.getUuid());
                release(p);
                TownState t = TownState.get(w);
                Vec3d drop = island ? ord.dest() : nearestRoad(t, ord.dest());
                Life.get(p).taskKind = "taxi_ride";
                Life.get(p).taskPos = BlockPos.ofFloored(ord.dest());
                Life.sync(p);
                p.sendMessage(Text.literal("Водитель: «Едем: " + ord.destName() + ". Пристегнитесь!»").formatted(Formatting.YELLOW), false);
                car.setRoute(route(t, car.getPos(), drop), 0.6f, () -> {
                    p.stopRiding();
                    p.sendMessage(Text.literal("Водитель: «Приехали! Хорошего дня»").formatted(Formatting.YELLOW), false);
                    Life.get(p).taskKind = "";
                    Life.get(p).taskPos = null;
                    Life.sync(p);
                    Achievements.give(p, "taxi_ride");
                    leave(car, t);
                    CARS.remove(p.getUuid());
                });
            }
        } else if (cid != null && !WAITING.containsKey(p.getUuid())) {
            CARS.remove(p.getUuid()); // поездка закончилась; пока ждём — машина может быть в незагруженном месте, не теряем её
        }
        // игрок-таксист подобрал клиента
        LifeData d = Life.get(p);
        if (d.taskKind.startsWith("pickup:") && p.getVehicle() instanceof CarEntity car) {
            UUID client = UUID.fromString(d.taskKind.substring(7));
            ServerPlayerEntity c = w.getServer().getPlayerManager().getPlayer(client);
            Order ord = PENDING_DEST.get(client);
            if (c == null || ord == null) {
                d.taskKind = "";
                d.taskPos = null;
            } else if (c.getVehicle() == car) {
                PENDING_DEST.remove(client);
                d.taskKind = "taxi";
                d.taskPos = BlockPos.ofFloored(ord.dest());
                d.taskReward = ord.price();
                Life.message(p, "Такси", "Клиент в машине. Вези: " + ord.destName());
                Life.sync(p);
            } else if (d.taskPos == null || d.taskPos.getSquaredDistance(c.getBlockPos()) > 9) {
                d.taskPos = c.getBlockPos();
                Life.sync(p);
            }
        }
    }

    /** Отъехать и исчезнуть. */
    private static void leave(CarEntity car, TownState t) {
        Vec3d away = roadPointAround(t, car.getPos(), 40);
        car.setRoute(route(t, car.getPos(), away), 0.6f, () -> {
            for (Entity e : car.getPassengerList()) if (e instanceof CitizenEntity || e.getCommandTags().contains("novograd_taxi_seat")) e.discard();
            car.discard();
        });
    }

    // ---------------------------------------------------------------- маршруты по сетке дорог
    static double gx(TownState t, int i) {
        return t.origin.getX() + TownBuilder.ROADS[i] + 0.5;
    }

    static double gz(TownState t, int i) {
        return t.origin.getZ() + TownBuilder.ROADS[i] + 0.5;
    }

    /** Ближайшая точка на дорогах (на осевой). */
    /** Мир «Новоград: Город» (остров) — дороги берём с острова, а не из сетки старого центра. */
    static boolean island() {
        return island;
    }

    private static boolean island;

    static Vec3d nearestRoad(TownState t, Vec3d p) {
        if (island) {
            int[] n = com.yompix.novograd.world.IslandRoads.nearestNode(p.x, p.z);
            if (n != null) return new Vec3d(n[0] + 0.5, p.y, n[1] + 0.5);
        }
        double bestD = Double.MAX_VALUE;
        Vec3d best = p;
        double lo = gx(t, 0), hi = gx(t, TownBuilder.ROADS.length - 1);
        for (int i = 0; i < TownBuilder.ROADS.length; i++) {
            Vec3d a = new Vec3d(gx(t, i), p.y, clamp(p.z, gz(t, 0), gz(t, TownBuilder.ROADS.length - 1)));
            Vec3d b = new Vec3d(clamp(p.x, lo, hi), p.y, gz(t, i));
            for (Vec3d c : new Vec3d[]{a, b}) {
                double d = c.squaredDistanceTo(p);
                if (d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
        }
        return best;
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    static Vec3d roadPointAround(TownState t, Vec3d p, double dist) {
        Vec3d best = nearestRoad(t, p);
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            Vec3d c = nearestRoad(t, p.add(Math.cos(a) * dist, 0, Math.sin(a) * dist));
            if (Math.abs(c.distanceTo(p) - dist) < Math.abs(best.distanceTo(p) - dist)) best = c;
        }
        return best;
    }

    /** Маршрут: по дорогам до перекрёстков (поиск в ширину по сетке), машина едет по правой полосе. */
    public static List<Vec3d> route(TownState t, Vec3d from, Vec3d to) {
        if (island) {
            // по настоящим дорогам острова (тот же путь, что рисует навигатор)
            List<int[]> path = com.yompix.novograd.world.IslandRoads.route(from.x, from.z, to.x, to.z);
            List<Vec3d> center = new ArrayList<>();
            center.add(new Vec3d(from.x, from.y, from.z));
            for (int[] q : path) center.add(new Vec3d(q[0] + 0.5, from.y, q[1] + 0.5));
            Vec3d last = center.get(center.size() - 1);
            // до самой двери: последний короткий отрезок от перекрёстка к цели
            if (path.isEmpty() || Math.hypot(last.x - to.x, last.z - to.z) < 40) center.add(new Vec3d(to.x, from.y, to.z));
            return lane(center, from.y);
        }
        int n = TownBuilder.ROADS.length;
        Vec3d a = nearestRoad(t, from), b = nearestRoad(t, to);
        // соседние перекрёстки для точки на дороге
        List<int[]> sa = ends(t, a), sb = ends(t, b);
        double best = Double.MAX_VALUE;
        List<int[]> bestPath = List.of();
        for (int[] s : sa)
            for (int[] e : sb) {
                List<int[]> path = grid(s, e, n);
                double len = a.distanceTo(node(t, s)) + path.size() * 48 + node(t, e).distanceTo(b);
                if (len < best) {
                    best = len;
                    bestPath = path;
                }
            }
        List<Vec3d> center = new ArrayList<>();
        center.add(a);
        for (int[] q : bestPath) center.add(node(t, q));
        center.add(b);
        // если обе точки на одной дороге — едем напрямую
        if (sameRoad(t, a, b)) {
            center.clear();
            center.add(a);
            center.add(b);
        }
        return lane(center, from.y);
    }

    private static boolean sameRoad(TownState t, Vec3d a, Vec3d b) {
        return Math.abs(a.x - b.x) < 1 && isRoadX(t, a.x) || Math.abs(a.z - b.z) < 1 && isRoadZ(t, a.z);
    }

    private static boolean isRoadX(TownState t, double x) {
        for (int i = 0; i < TownBuilder.ROADS.length; i++) if (Math.abs(x - gx(t, i)) < 1) return true;
        return false;
    }

    private static boolean isRoadZ(TownState t, double z) {
        for (int i = 0; i < TownBuilder.ROADS.length; i++) if (Math.abs(z - gz(t, i)) < 1) return true;
        return false;
    }

    private static Vec3d node(TownState t, int[] q) {
        return new Vec3d(gx(t, q[0]), 0, gz(t, q[1]));
    }

    private static List<int[]> ends(TownState t, Vec3d p) {
        int n = TownBuilder.ROADS.length;
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (Math.abs(p.x - gx(t, i)) < 1) { // дорога вдоль z
                for (int j = 0; j < n - 1; j++)
                    if (p.z >= gz(t, j) - 1 && p.z <= gz(t, j + 1) + 1) {
                        out.add(new int[]{i, j});
                        out.add(new int[]{i, j + 1});
                    }
            }
            if (Math.abs(p.z - gz(t, i)) < 1) {
                for (int j = 0; j < n - 1; j++)
                    if (p.x >= gx(t, j) - 1 && p.x <= gx(t, j + 1) + 1) {
                        out.add(new int[]{j, i});
                        out.add(new int[]{j + 1, i});
                    }
            }
        }
        if (out.isEmpty()) out.add(new int[]{0, 0});
        return out;
    }

    private static List<int[]> grid(int[] s, int[] e, int n) {
        List<int[]> path = new ArrayList<>();
        int x = s[0], z = s[1];
        path.add(new int[]{x, z});
        while (x != e[0]) {
            x += Integer.signum(e[0] - x);
            path.add(new int[]{x, z});
        }
        while (z != e[1]) {
            z += Integer.signum(e[1] - z);
            path.add(new int[]{x, z});
        }
        return path;
    }

    /** Сдвиг на правую полосу (+2 блока вправо по ходу движения). */
    private static List<Vec3d> lane(List<Vec3d> c, double y) {
        List<Vec3d> out = new ArrayList<>();
        for (int i = 0; i < c.size(); i++) {
            Vec3d prev = c.get(Math.max(0, i - 1)), next = c.get(Math.min(c.size() - 1, i + 1));
            Vec3d dir = next.subtract(prev);
            if (dir.lengthSquared() < 0.01) continue;
            dir = dir.normalize();
            Vec3d right = new Vec3d(-dir.z, 0, dir.x);
            Vec3d p = c.get(i).add(right.multiply(2.2));
            if (out.isEmpty() || out.get(out.size() - 1).distanceTo(p) > 1.5) out.add(new Vec3d(p.x, y, p.z));
        }
        return out;
    }

    @Nullable
    public static NbtCompound placesNbt(ServerWorld w) {
        NbtCompound n = new NbtCompound();
        PLACES.forEach((id, name) -> {
            Vec3d v = com.yompix.novograd.story.Story.place(w, id);
            if (v == null) return;
            NbtCompound e = new NbtCompound();
            e.putString("name", name);
            e.putInt("x", (int) Math.floor(v.x));
            e.putInt("z", (int) Math.floor(v.z));
            n.put(id, e);
        });
        TownState t = TownState.get(w);
        if (t.origin != null) {
            n.putInt("ox", t.origin.getX());
            n.putInt("oz", t.origin.getZ());
        }
        return n;
    }
}
