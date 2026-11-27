package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.10: профессии со званиями (телефон → «Карьера»). Смена: берёшь задание — едешь/летишь/ловишь — результат и
 * оплата на сервере; опыт → звание (5 ступеней), выше звание — больше платят и задания дальше.
 * <ul>
 *   <li><b>Дальнобойщик</b>: фура у портового склада → груз в деревню, «Мираж», аэродром, ферму; побил фуру — меньше.</li>
 *   <li><b>Пилот</b>: самолёт на аэродроме → посадка у «Миража» (и обратно); долетел и сел — оплата.</li>
 *   <li><b>Рыбак</b>: на причале (порт или озеро) поймай 5 рыб — сдай у причала.</li>
 *   <li><b>Механик</b>: сломанная машина у дороги (аварийка, хозяин ждёт) — подойди пешком, чинишь руками (присел, трещотка).</li>
 *   <li><b>Курьер</b>: скутер, три адреса подряд, на время.</li>
 *   <li><b>Охранник</b>: пост у банка или казино 3 минуты; воришка — догони (кошелёк вернёшь — премия).</li>
 *   <li><b>Журналист</b>: снимок события (концерт, пожар, погоня, городская история) с 30 блоков — телефон → «Камера».</li>
 * </ul>
 * Опыт и звания — {@code LifeData.city.car_<id>}.
 */
public final class Careers {
    private Careers() {}

    public static final String[] IDS = {"trucker", "pilot", "fisher", "mechanic", "courier", "guard", "journalist"};
    public static final String[] NAMES = {"Дальнобойщик", "Пилот", "Рыбак", "Механик", "Курьер", "Охранник", "Журналист"};
    static final String[][] RANKS = {
            {"стажёр", "водитель", "профи", "ас трассы", "король дорог"},
            {"курсант", "второй пилот", "командир", "капитан", "легенда неба"},
            {"новичок", "рыбак", "добытчик", "капитан артели", "морской волк"},
            {"подмастерье", "механик", "мастер", "главный механик", "золотые руки"},
            {"новичок", "курьер", "быстрый", "экспресс", "молния"},
            {"стажёр", "охранник", "старший", "начальник смены", "шеф безопасности"},
            {"стажёр", "репортёр", "корреспондент", "обозреватель", "звезда эфира"}};
    public static final int[] XP = {0, 100, 300, 700, 1500};

    public static int rank(int xp) {
        int r = 0;
        for (int i = 0; i < XP.length; i++) if (xp >= XP[i]) r = i;
        return r;
    }

    static float mul(int rank) {
        return 1f + rank * 0.2f;
    }

    static int idx(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    // ------------------------------------------------------------------ смена и задание
    static final class Shift {
        int career;
        String step = "";
        Vec3d target, from;
        long deadline, started;
        @Nullable UUID vehicle, npc;
        int count, stops, base;
        float startHealth = 100;
    }

    private static final Map<UUID, Shift> SHIFTS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Careers::tick);
        for (int i = 0; i < IDS.length; i++) Achievements.ALL.putIfAbsent("career_" + IDS[i], new String[]{NAMES[i] + ": вершина", "Высшее звание — " + NAMES[i].toLowerCase()});
    }

    /** Для автотеста: куда сейчас задание (или где транспорт). */
    @Nullable
    public static Vec3d target(ServerPlayerEntity p) {
        Shift s = SHIFTS.get(p.getUuid());
        if (s == null) return null;
        if ((s.step.equals("fura") || s.step.equals("fly")) && s.vehicle != null && p.getServerWorld().getEntity(s.vehicle) != null)
            return s.step.equals("fura") ? p.getServerWorld().getEntity(s.vehicle).getPos() : s.target;
        return s.target;
    }

    public static boolean onShift(ServerPlayerEntity p) {
        return SHIFTS.containsKey(p.getUuid());
    }

    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        switch (type) {
            case "career_state" -> send(p);
            case "career_start" -> start(p, n.getInt("i"));
            case "career_stop" -> stop(p, "Смена окончена.");
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void start(ServerPlayerEntity p, int i) {
        if (i < 0 || i >= IDS.length || SHIFTS.containsKey(p.getUuid())) return;
        if (Wanted.stars(p) > 0) {
            FunUtil.hint(p, "С розыском на смену не берут", Formatting.RED);
            return;
        }
        if (Duty.on(p)) Duty.off(p, "");
        Shift s = new Shift();
        s.career = i;
        s.started = p.getServerWorld().getTime();
        SHIFTS.put(p.getUuid(), s);
        FunUtil.title(p, NAMES[i].toUpperCase(), "смена началась · " + RANKS[i][rank(xp(p, i))]);
        next(p, s);
        send(p);
    }

    static int xp(ServerPlayerEntity p, int i) {
        return Life.get(p).city.getInt("car_" + IDS[i]);
    }

    public static void stop(ServerPlayerEntity p, String why) {
        Shift s = SHIFTS.remove(p.getUuid());
        if (s == null) return;
        cleanup(p, s);
        FunUtil.target(p, null, "");
        if (!why.isEmpty()) FunUtil.hint(p, why, Formatting.GRAY);
        send(p);
    }

    private static void cleanup(ServerPlayerEntity p, Shift s) {
        ServerWorld w = p.getServerWorld();
        for (UUID u : new UUID[]{s.vehicle, s.npc})
            if (u != null) {
                Entity e = w.getEntity(u);
                if (e != null) {
                    if (e.hasPassengers() && e.getFirstPassenger() == p) continue; // сидит — пусть доедет
                    e.discard();
                }
            }
        s.vehicle = null;
        s.npc = null;
    }

    private static Vec3d place(ServerPlayerEntity p, String name) {
        return Story.place(p.getServerWorld(), name);
    }

    /** Новое задание по профессии. */
    private static void next(ServerPlayerEntity p, Shift s) {
        ServerWorld w = p.getServerWorld();
        cleanup(p, s);
        long now = w.getTime();
        int r = rank(xp(p, s.career));
        switch (IDS[s.career]) {
            case "trucker" -> {
                s.from = place(p, "port_warehouse");
                String[] dests = r >= 2 ? new String[]{"village", "mirage_strip", "airfield", "farm", "desert_road", "outer_east"} : new String[]{"airfield", "farm", "industry_street"};
                String d = dests[p.getRandom().nextInt(dests.length)];
                s.target = place(p, d);
                CarEntity truck = FunUtil.car(w, FunUtil.curbAround(p, 4, 10) != null && p.squaredDistanceTo(s.from) < 60 * 60 ? FunUtil.curbAround(p, 4, 10) : s.from, 0, 11);
                s.vehicle = truck.getUuid();
                s.step = "fura";
                s.base = 120 + (int) (s.from.distanceTo(s.target) / 8);
                s.deadline = now + 20L * (120 + (long) (s.from.distanceTo(s.target) / 7));
                FunUtil.target(p, truck.getPos(), "ФУРА");
                FunUtil.hint(p, "🚚 Фура ждёт. Груз — " + d.replace('_', ' '), Formatting.GOLD);
            }
            case "pilot" -> {
                Vec3d air = place(p, "airfield"), mirage = place(p, "mirage_air");
                boolean there = mirage != null && p.squaredDistanceTo(mirage) < p.squaredDistanceTo(air);
                s.from = there ? mirage : air;
                s.target = there ? air : mirage;
                var plane = com.yompix.novograd.vehicle.Vehicles.PLANE.create(w);
                if (plane != null) {
                    plane.refreshPositionAndAngles(s.from.x, s.from.y + 0.5, s.from.z, 0, 0);
                    w.spawnEntity(plane);
                    s.vehicle = plane.getUuid();
                }
                s.step = "fly";
                s.base = 260;
                s.deadline = now + 20L * 300;
                FunUtil.target(p, s.from, "САМОЛЁТ");
                FunUtil.hint(p, "✈ Рейс: " + (there ? "«Мираж» → аэродром" : "аэродром → «Мираж»") + ". Взлёт, полёт, посадка", Formatting.GOLD);
            }
            case "fisher" -> {
                Vec3d port = place(p, "port_dock"), lake = place(p, "lake_dock");
                s.target = lake != null && p.squaredDistanceTo(lake) < p.squaredDistanceTo(port) ? lake : port;
                s.step = "fish";
                s.count = fish(p);
                s.base = 30;
                s.deadline = now + 20L * 900;
                FunUtil.target(p, s.target, "ПРИЧАЛ");
                FunUtil.hint(p, "🎣 Поймай 5 рыб у причала (удочка — у рыбака в порту) и сдай здесь", Formatting.GOLD);
            }
            case "mechanic" -> {
                // поломка — в загруженной части города (иначе машина не появится)
                Vec3d at = null;
                for (int tries = 0; tries < 6 && at == null; tries++) {
                    Vec3d c = FunUtil.curbAround(p, tries < 3 ? 70 : 30, tries < 3 ? 140 : 60);
                    if (c != null && w.isChunkLoaded(net.minecraft.util.math.BlockPos.ofFloored(c))) at = c;
                }
                if (at == null) at = p.getPos().add(8, 0, 8);
                w.getChunk((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4); // загрузить, иначе высота земли неизвестна
                at = new Vec3d(at.x, Story.topY(w, (int) Math.floor(at.x), (int) Math.floor(at.z)), at.z); // на земле, а не на высоте игрока
                int[] variants = {0, 1, 2, 3, 4, 7, 8, 13, 16};
                CarEntity car = FunUtil.car(w, at, p.getRandom().nextFloat() * 360, variants[p.getRandom().nextInt(variants.length)]);
                car.setHealthPct(25 + p.getRandom().nextInt(30));
                car.toggleLight(CarEntity.HAZARD);
                s.vehicle = car.getUuid();
                CitizenEntity owner = FunUtil.citizen(w, at.add(1.8, 0, 1.2), CitizenEntity.Role.CITIZEN, null);
                owner.setStationary(true);
                owner.busy(CitizenEntity.Activity.PHONE, 20 * 600, null, ItemStack.EMPTY);
                s.npc = owner.getUuid();
                s.target = at;
                s.step = "fix";
                s.base = 90;
                s.deadline = now + 20L * 360;
                FunUtil.target(p, at, "ПОЛОМКА");
                FunUtil.hint(p, "🔧 Сломалась машина. Подойди к ней пешком — починишь руками", Formatting.GOLD);
            }
            case "courier" -> {
                if (s.vehicle == null && p.getVehicle() == null) {
                    CarEntity scooter = new CarEntity(ModContent.SCOOTER, w);
                    scooter.refreshPositionAndAngles(p.getX() + 1.5, p.getY(), p.getZ(), p.getYaw(), 0);
                    w.spawnEntity(scooter);
                    s.vehicle = scooter.getUuid();
                }
                s.stops = 3;
                s.count = 0;
                s.target = FunUtil.curbAround(p, 120, 260);
                if (s.target == null) s.target = p.getPos().add(100, 0, 0);
                s.step = "parcel";
                s.base = 45;
                s.deadline = now + 20L * 150;
                FunUtil.target(p, s.target, "АДРЕС 1/3");
                FunUtil.hint(p, "📦 Три посылки. Первый адрес — на радаре", Formatting.GOLD);
            }
            case "guard" -> {
                String post = p.getRandom().nextBoolean() ? "bank_tower" : "casino_dt";
                s.target = place(p, post);
                s.step = "post";
                s.count = 0;
                s.base = 160;
                s.deadline = now + 20L * 180;
                FunUtil.target(p, s.target, "ПОСТ");
                FunUtil.hint(p, "🛡 Пост: " + (post.equals("bank_tower") ? "Сити-Банк" : "казино центра") + ". 3 минуты на месте", Formatting.GOLD);
            }
            default -> { // журналист
                s.step = "story";
                s.target = newsTarget(p);
                s.base = 70;
                s.deadline = now + 20L * 420;
                FunUtil.target(p, s.target, "СЮЖЕТ");
                FunUtil.hint(p, "📷 Сними сюжет: подойди ближе 30 блоков и сфотографируй (телефон → «Камера»)", Formatting.GOLD);
            }
        }
        sendTask(p, s);
    }

    /** Куда ехать журналисту: пожар, погоня, концерт — или городская история. */
    private static Vec3d newsTarget(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        var fires = com.yompix.novograd.world.Fires.spots();
        if (!fires.isEmpty()) return fires.get(0);
        for (ServerPlayerEntity o : w.getPlayers()) if (o != p && Wanted.stars(o) >= 2) return o.getPos();
        if (Nightlife.concertNow(w)) return Story.place(w, Nightlife.todayVenue(w));
        String[] stories = {"downtown_plaza", "port_crane", "riv_marina", "village", "hill_top", "beach_lot", "mirage_chapel", "farm"};
        return Story.place(w, stories[p.getRandom().nextInt(stories.length)]);
    }

    static int fish(ServerPlayerEntity p) {
        int n = 0;
        Item[] fish = {Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH};
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) for (Item f : fish) if (inv.getStack(i).isOf(f)) n += inv.getStack(i).getCount();
        return n;
    }

    // ------------------------------------------------------------------ такт
    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 10 != 3 || SHIFTS.isEmpty()) return;
        for (var e : new HashMap<>(SHIFTS).entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null) {
                SHIFTS.remove(e.getKey());
                continue;
            }
            Shift s = e.getValue();
            try {
                step(p, s);
            } catch (RuntimeException ex) {
                com.yompix.novograd.Novograd.LOG.warn("[карьера]", ex);
                stop(p, "Смена прервана");
            }
        }
    }

    private static void step(ServerPlayerEntity p, Shift s) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        if (Wanted.stars(p) >= 2) {
            stop(p, "Розыск — смена сорвана");
            return;
        }
        if (now > s.deadline && !s.step.equals("post")) {
            FunUtil.title(p, "НЕ УСПЕЛ", NAMES[s.career] + " · задание провалено");
            next(p, s);
            return;
        }
        Entity veh = s.vehicle == null ? null : w.getEntity(s.vehicle);
        switch (s.step) {
            case "fura" -> {
                if (veh instanceof CarEntity car && p.getVehicle() == car) {
                    s.step = "haul";
                    s.startHealth = car.health();
                    FunUtil.target(p, s.target, "РАЗГРУЗКА");
                }
            }
            case "haul" -> {
                if (veh == null && p.getVehicle() == null && s.vehicle != null) return; // фура в невыгруженном чанке — ждём
                if (!(veh instanceof CarEntity car) || car.wrecked()) {
                    FunUtil.title(p, "ГРУЗ ПОТЕРЯН", "фура разбита");
                    next(p, s);
                    return;
                }
                if (p.getVehicle() == car && car.squaredDistanceTo(s.target) < 20 * 20 && car.slow()) {
                    float cond = Math.max(0.3f, car.health() / Math.max(1f, s.startHealth));
                    pay(p, s, (int) (s.base * cond), cond < 0.9f ? "груз помят: " + Math.round(cond * 100) + "%" : "груз цел");
                    p.stopRiding();
                    car.discard();
                    s.vehicle = null;
                    next(p, s);
                }
            }
            case "fly" -> {
                if (veh != null && p.getVehicle() == veh) {
                    if (veh.squaredDistanceTo(s.target) < 40 * 40 && veh.isOnGround() && veh.getVelocity().horizontalLength() < 0.25) {
                        pay(p, s, s.base, "посадка");
                        p.stopRiding();
                        veh.discard();
                        s.vehicle = null;
                        next(p, s);
                    } else if (now % 40 == 3) FunUtil.target(p, s.target, "ПОСАДКА");
                }
            }
            case "fish" -> {
                int caught = Math.max(0, fish(p) - s.count);
                if (caught >= 5 && p.squaredDistanceTo(s.target) < 25 * 25) {
                    // сдал улов
                    int left = 5;
                    var inv = p.getInventory();
                    for (int i = 0; i < inv.size() && left > 0; i++) {
                        ItemStack st = inv.getStack(i);
                        if (st.isOf(Items.COD) || st.isOf(Items.SALMON) || st.isOf(Items.TROPICAL_FISH) || st.isOf(Items.PUFFERFISH)) {
                            int k = Math.min(left, st.getCount());
                            st.decrement(k);
                            left -= k;
                        }
                    }
                    pay(p, s, s.base * 5, "улов сдан");
                    next(p, s);
                } else if (now % 200 == 3 && caught > 0) FunUtil.hint(p, "🎣 Улов: " + caught + "/5", Formatting.AQUA);
            }
            case "fix" -> {
                if (!(veh instanceof CarEntity car)) {
                    if (s.target != null && p.squaredDistanceTo(s.target) < 48 * 48) next(p, s); // рядом, а машины нет — увезли
                    return; // далеко — машина в невыгруженном чанке
                }
                if (!p.hasVehicle() && p.squaredDistanceTo(car) < 3.5 * 3.5 && com.yompix.novograd.life.Hands.current(p) == null) {
                    var hs = com.yompix.novograd.life.Hands.start(p, com.yompix.novograd.life.HandKind.REPAIR, "", null, car.getPos().add(0, 0.5, 0));
                    if (Boolean.getBoolean("novograd.workfast")) com.yompix.novograd.Novograd.LOG.info("[карьера] {} чинит машину: {}", p.getName().getString(), hs != null);
                    if (hs != null) hs.onDone(() -> {
                        car.repair();
                        car.toggleLight(CarEntity.HAZARD);
                        if (s.npc != null && w.getEntity(s.npc) instanceof CitizenEntity c) c.busy(CitizenEntity.Activity.HANDSHAKE, 40, null, ItemStack.EMPTY);
                        pay(p, s, s.base, "машина на ходу");
                        if (s.npc != null && w.getEntity(s.npc) instanceof CitizenEntity c) c.removeLater(200);
                        s.npc = null;
                        if (w.getEntity(s.vehicle) instanceof CarEntity c2) c2.removeAllPassengers();
                        s.vehicle = null;
                        next(p, s);
                    });
                }
            }
            case "parcel" -> {
                if (p.squaredDistanceTo(s.target.x, p.getY(), s.target.z) < 6 * 6) {
                    s.count++;
                    p.playSoundToPlayer(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), net.minecraft.sound.SoundCategory.MASTER, 0.8f, 1.6f);
                    if (s.count >= s.stops) {
                        pay(p, s, s.base * s.stops, "все посылки");
                        next(p, s);
                    } else {
                        s.target = FunUtil.curbAround(p, 90, 200);
                        if (s.target == null) s.target = p.getPos().add(-80, 0, 60);
                        s.deadline = now + 20L * 120;
                        FunUtil.target(p, s.target, "АДРЕС " + (s.count + 1) + "/" + s.stops);
                    }
                }
            }
            case "post" -> {
                boolean at = p.squaredDistanceTo(s.target) < 18 * 18;
                if (at) s.count += 10;
                // воришка раз в ~минуту
                if (at && s.npc == null && p.getRandom().nextInt(120) == 0) {
                    CitizenEntity thief = FunUtil.citizen(w, s.target.add(3, 0, 3), CitizenEntity.Role.CITIZEN, "Воришка");
                    thief.removeLater(20 * 60);
                    Vec3d away = s.target.add(p.getRandom().nextBoolean() ? 40 : -40, 0, p.getRandom().nextBoolean() ? 40 : -40);
                    thief.getNavigation().startMovingTo(away.x, away.y, away.z, 1.4);
                    s.npc = thief.getUuid();
                    FunUtil.hint(p, "🛡 Держи вора! (догони)", Formatting.RED);
                }
                if (s.npc != null && w.getEntity(s.npc) instanceof CitizenEntity th) {
                    if (p.squaredDistanceTo(th) < 2.2 * 2.2) {
                        th.busy(CitizenEntity.Activity.UPSET, 60, null, ItemStack.EMPTY);
                        th.removeLater(60);
                        s.npc = null;
                        pay(p, s, 60, "вор пойман");
                    }
                } else s.npc = null;
                if (s.count >= (Boolean.getBoolean("novograd.workfast") ? 200 : 20 * 180)) {
                    pay(p, s, s.base, "смена на посту");
                    s.count = 0;
                    next(p, s);
                } else if (!at && now % 100 == 3) FunUtil.hint(p, "🛡 Вернись на пост", Formatting.YELLOW);
            }
            default -> {
            } // журналист: снимок — onPhoto
        }
    }

    /** Снимок с телефона (журналист). */
    public static void onPhoto(ServerPlayerEntity p) {
        Shift s = SHIFTS.get(p.getUuid());
        if (s == null || !s.step.equals("story") || s.target == null) return;
        if (p.squaredDistanceTo(s.target) > 30 * 30) {
            FunUtil.hint(p, "📷 Далеко — подойди ближе к сюжету", Formatting.YELLOW);
            return;
        }
        boolean hot = !com.yompix.novograd.world.Fires.spots().isEmpty() || Nightlife.concertNow(p.getServerWorld());
        pay(p, s, s.base * (hot ? 2 : 1), hot ? "горячий сюжет" : "сюжет в номер");
        News.post(p.getServer(), "Репортаж " + p.getName().getString() + " для «Новоград-24»: " + (hot ? "с места событий" : "город сегодня"), "ГОРОД");
        next(p, s);
    }

    private static void pay(ServerPlayerEntity p, Shift s, int base, String why) {
        com.yompix.novograd.Novograd.LOG.info("[карьера] {} · {}: {} (база ${})", p.getName().getString(), NAMES[s.career], why, base);
        LifeData d = Life.get(p);
        String key = "car_" + IDS[s.career];
        int before = d.city.getInt(key);
        int r = rank(before);
        int sum = Math.round(base * mul(r));
        Life.earn(p, sum, NAMES[s.career].toLowerCase() + ": " + why);
        int gain = 15 + base / 20;
        d.city.putInt(key, before + gain);
        int nr = rank(before + gain);
        if (nr > r) {
            FunUtil.title(p, "НОВОЕ ЗВАНИЕ", NAMES[s.career] + " · " + RANKS[s.career][nr] + " · оплата ×" + String.format("%.1f", mul(nr)));
            if (nr == 4) Achievements.give(p, "career_" + IDS[s.career]);
            Social.post(p.getServer(), "@novograd_rabota", p.getName().getString() + " теперь " + RANKS[s.career][nr] + " (" + NAMES[s.career].toLowerCase() + ")", p, 30, 4);
        }
        Life.sync(p);
        send(p);
    }

    // ------------------------------------------------------------------ телефон
    private static void sendTask(ServerPlayerEntity p, Shift s) {
        send(p);
    }

    public static void send(ServerPlayerEntity p) {
        NbtCompound out = new NbtCompound();
        NbtList l = new NbtList();
        for (int i = 0; i < IDS.length; i++) {
            NbtCompound c = new NbtCompound();
            int xp = xp(p, i), r = rank(xp);
            c.putString("name", NAMES[i]);
            c.putString("rank", RANKS[i][r]);
            c.putInt("r", r);
            c.putInt("xp", xp);
            c.putInt("next", r < 4 ? XP[r + 1] : xp);
            c.putInt("from", XP[r]);
            c.putFloat("mul", mul(r));
            l.add(c);
        }
        out.put("list", l);
        Shift s = SHIFTS.get(p.getUuid());
        out.putInt("on", s == null ? -1 : s.career);
        if (s != null) {
            out.putString("step", s.step);
            out.putLong("left", Math.max(0, s.deadline - p.getServerWorld().getTime()) / 20);
        }
        LifeNet.send(p, "career", out);
    }

    public static void onLeave(ServerPlayerEntity p) {
        Shift s = SHIFTS.remove(p.getUuid());
        if (s != null) cleanup(p, s);
    }
}
