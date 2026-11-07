package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Жильцы острова: у каждого постоянные имя, лицо, квартира (или дом), работа и распорядок дня.
 * Ночью спят в своей кровати, утром выходят из подъезда и едут на работу (в офис за стол, в магазин, порт,
 * полицию, больницу, казино…), в обед перекусывают, вечером — бар, клуб, пляж или площадь, к ночи — домой.
 * <p>
 * Производительность: жильцы — это записи (~260), а живыми горожанами появляются только рядом с игроком
 * (не больше {@link #MAX_ACTIVE}); вдали жизнь идёт «виртуально» — по часам. Дорога между районами — «за кадром»
 * (уехали на машине/такси), в высотках — на лифте. Купил игрок жильё — прежний жилец переезжает.
 * Свидетели, паника и драки — обычная логика CitizenEntity: пока житель не спокоен, распорядок ему не мешает.
 */
public final class Residents {
    private Residents() {}

    public static final String TAG = "nvg_res";
    private static final int POOL = 260, MAX_ACTIVE = 36, PER_PLAYER = 12, SPAWN_R = 38, DESPAWN_R = 60;

    enum Mode { SLEEP, HOME, LEAVE_HOME, TRANSIT, ARRIVE_WORK, WORK, LUNCH, LEAVE_WORK, LEISURE, ARRIVE_HOME }

    /** Постоянная личность жильца. */
    static final class Res {
        int idx;
        long seed;
        String name = "";
        int skin;
        String homeKey = "", homeKind = "";
        Vec3d homePos, homeDoor, homeGround, homeStreet, homeBed;
        boolean homeUp;
        String work = "", workLabel = "";
        Vec3d workPos, workGround, workStreet, lunch;
        boolean workUp;
        String leisure = "";
        Vec3d leisurePos;
        float shift;
        boolean night;
    }

    private static final List<Res> POOL_LIST = new ArrayList<>();
    private static final List<String> SPARE = new ArrayList<>();
    private static boolean ready;

    /** Тег жильца на горожанине (CitizenEntity: распорядок и «дом» старого города им не управляют). */
    public static boolean is(CitizenEntity c) {
        return c.getCommandTags().contains(TAG);
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Residents::tick);
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
            if (e instanceof CitizenEntity c && is(c) && c.getWorld() instanceof ServerWorld w) {
                int idx = idxOf(c);
                if (idx < 0) return;
                Housing.State st = Housing.state(w.getServer());
                st.hospital.put(idx, w.getTimeOfDay() / 24000L + 3); // два дня в больнице, потом снова живёт как жил
                st.markDirty();
                ACTIVE.remove(idx);
            }
        });
        // ПКМ по жильцу — кто он такой (разговор при этом — обычный, как с любым горожанином)
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && hand == Hand.MAIN_HAND && entity instanceof CitizenEntity c && is(c) && player instanceof ServerPlayerEntity p) {
                int idx = idxOf(c);
                if (idx >= 0 && idx < POOL_LIST.size()) {
                    Res r = POOL_LIST.get(idx);
                    p.sendMessage(Text.literal("👤 " + r.name + " · живёт: " + r.homeKind + " · работа: " + r.workLabel).formatted(Formatting.GRAY), true);
                }
            }
            return ActionResult.PASS;
        });
    }

    private static int idxOf(CitizenEntity c) {
        for (String t : c.getCommandTags()) if (t.startsWith(TAG + ":")) {
            try {
                return Integer.parseInt(t.substring(TAG.length() + 1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ генерация города жильцов
    private record Home(String key, String kind, Vec3d pos, Vec3d door, Vec3d ground, Vec3d street, boolean up, Vec3d bed) {}

    private record Spot(String place, String kind, String label, Vec3d pos, Vec3d ground, Vec3d street, boolean up, boolean night) {}

    private static final int[][] UNIT_DOOR = {{-7, -5}, {6, -5}, {-3, -2}, {4, 4}};

    @Nullable
    static Home homeOf(String key) {
        String[] a = key.split(":");
        try {
            if (a[0].equals("T")) {
                long px = Long.parseLong(a[1]), pz = Long.parseLong(a[2]);
                int k = Integer.parseInt(a[3]), u = Integer.parseInt(a[4]);
                int cx = (int) px * 48, cz = (int) pz * 48;
                int[] info = Island.lotInfo(cx, cz);
                if (info == null || (info[0] != 1 && info[0] != 2)) return null;
                int s = Island.surface(cx, cz), half = info[1], n = info[2];
                int[] d = UNIT_DOOR[u];
                // кровать — как её ставит Buildings.furnish: у дальней стены комнаты, в углу (i = 0, изножье j = d − 2)
                int W = (k >= n - 2 ? half - 3 : half) - 1;
                int[] bed = switch (u) {
                    case 0 -> new int[]{-W, -6 - (W - 5 - 2)};
                    case 1 -> new int[]{5, -6 - (W - 5 - 2)};
                    case 2 -> new int[]{-W, W + 2 - 2 - 1};
                    default -> new int[]{2, W - 4 - 2 + 5};
                };
                Direction in = u < 2 ? Direction.NORTH : Direction.SOUTH;
                Vec3d door = new Vec3d(cx + d[0] + 0.5, s + 4 * k + 1, cz + d[1] + 0.5);
                Vec3d pos = door.add(in.getOffsetX() * 2.5, 0, in.getOffsetZ() * 2.5);
                Vec3d corridor = door.add(-in.getOffsetX() * 1.5, 0, -in.getOffsetZ() * 1.5);
                return new Home(key, "башня, " + (k + 1) + " этаж", pos, corridor, new Vec3d(cx + 0.5, s + 1, cz + half - 2.5),
                        new Vec3d(cx + 3.5, s + 1, cz + half + 4.5), true, new Vec3d(cx + bed[0] + 0.5, s + 4 * k + 1, cz + bed[1] + 0.5));
            }
            if (a[0].equals("H")) {
                long px = Long.parseLong(a[1]), pz = Long.parseLong(a[2]);
                int qx = Integer.parseInt(a[3]), qz = Integer.parseInt(a[4]);
                int cx = (int) px * 48, cz = (int) pz * 48, hx = cx + qx * 9;
                int s = Island.surface(cx, cz);
                Vec3d door = new Vec3d(hx + 0.5, s + 1, cz + qz * 14 + 0.5);
                return new Home(key, cx < -840 ? "вилла на Ривьере" : "дом в пригороде", new Vec3d(hx + 0.5, s + 1, cz + qz * 10 + 0.5), door,
                        door, new Vec3d(hx + 0.5, s + 1, cz + qz * 19 + 0.5), false, new Vec3d(cx + qx * 6 + 0.5, s + 1, cz + qz * 7 + 0.5));
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private static final String[][] WORKS = {
            // место, вид, подпись, ночная смена
            {"shop_clothes", "shop", "продавец в «Шмот-Базаре»", ""}, {"shop_barber", "shop", "парикмахер", ""}, {"shop_gym", "gym", "тренер в зале", ""},
            {"shop_boutique", "shop", "продавец бутика", ""}, {"pizza_door", "food", "пиццерия «Мама Мия»", ""}, {"burger_door", "food", "«Жир-Бургер»", ""},
            {"bar_door", "bar", "бармен", "n"}, {"beach_bar", "bar", "пляжный бар", ""}, {"club_neon", "bar", "клуб «Неон»", "n"},
            {"bank_tower", "bank", "Сити-Банк", ""}, {"police_station", "police", "полиция", ""}, {"hospital", "hospital", "больница", "n"},
            {"port_warehouse", "port", "грузчик в порту", ""}, {"port_dock", "port", "докер", ""}, {"port_crane", "port", "крановщик", ""},
            {"casino_dt", "casino", "казино «Корона»", "n"}, {"riv_casino", "casino", "крупье «Золотой короны»", "n"}, {"riv_hotel", "hotel", "отель «Лазурь»", ""},
            {"airfield_hangar", "air", "механик аэродрома", ""}, {"garage_door", "garage", "автомастерская", ""}, {"chop_shop", "garage", "разборка", ""},
            {"riv_marina", "marina", "пристань Ривьеры", ""}, {"farm", "farm", "ферма", ""}, {"village_farm", "farm", "ферма в Сосновке", ""}};
    private static final String[][] FUN = {
            {"club_neon", "club"}, {"bar_door", "bar"}, {"beach_bar", "bar"}, {"riv_strip", "walk"}, {"riv_beach", "beach"}, {"downtown_plaza", "walk"},
            {"casino_dt", "casino"}, {"riv_casino", "casino"}, {"park_center", "park"}, {"lake_dock", "walk"}, {"beach_lot", "beach"}};

    private static final Map<String, Vec3d> PLACE_CACHE = new HashMap<>();

    @Nullable
    private static Vec3d place(ServerWorld w, String name) {
        if (PLACE_CACHE.containsKey(name)) return PLACE_CACHE.get(name);
        Vec3d v = null;
        try {
            v = Story.place(w, name);
            if (v != null && name.endsWith("_door")) { // дверь смотрит в здание — работают внутри
                double rad = Math.toRadians(Story.placeYaw(w, name));
                v = v.add(-Math.sin(rad) * 5, 0, Math.cos(rad) * 5);
            }
        } catch (RuntimeException ignored) {
        }
        PLACE_CACHE.put(name, v);
        return v;
    }

    private static void build(ServerWorld w) {
        if (ready) return;
        ready = true;
        Random r = new Random(20261106L);
        // жильё: квартиры в жилых башнях и дома пригорода/Ривьеры
        List<String> towers = new ArrayList<>(), houses = new ArrayList<>();
        List<int[]> offices = new ArrayList<>();
        for (int px = 3; px <= 14; px++)
            for (int pz = -14; pz <= -4; pz++) {
                int[] in = Island.lotInfo(px * 48, pz * 48);
                if (in == null || Island.zone(px * 48, pz * 48) != Island.Zone.DOWNTOWN) continue;
                if (in[0] == 1) for (int k = 1; k < in[2] - 1; k++) for (int u = 0; u < 4; u++) towers.add("T:" + px + ":" + pz + ":" + k + ":" + u);
                if (in[0] == 0) offices.add(new int[]{px * 48, pz * 48, in[1], in[2]});
            }
        for (int[] range : new int[][]{{-11, 2, -11, 11}, {-37, -20, -18, 18}})
            for (int px = range[0]; px <= range[1]; px++)
                for (int pz = range[2]; pz <= range[3]; pz++) {
                    if (Island.zone(px * 48, pz * 48) != Island.Zone.SUBURB) continue;
                    for (int qx = -1; qx <= 1; qx += 2)
                        for (int qz = -1; qz <= 1; qz += 2) {
                            if (pz == 2 && qz > 0 && (px == -5 || px == -7 || px == -9 || px == -35)) continue; // там магазины стиля
                            houses.add("H:" + px + ":" + pz + ":" + qx + ":" + qz);
                        }
                }
        java.util.Collections.shuffle(towers, r);
        java.util.Collections.shuffle(houses, r);
        // жилье из каталога риелтора — у каждого свой прежний жилец
        List<String> keys = new ArrayList<>();
        for (Housing.Listing l : Housing.listings()) if (l.id.startsWith("T:") && l.kind == Housing.Kind.APT || l.id.startsWith("H:")) keys.add(l.id);
        Set<String> used = new HashSet<>(keys);
        int ti = 0, hi = 0;
        while (keys.size() < POOL && (ti < towers.size() || hi < houses.size())) {
            boolean tower = keys.size() % 5 < 2 ? ti < towers.size() : hi >= houses.size();
            String k = tower ? towers.get(ti++) : houses.get(hi++);
            if (used.add(k)) keys.add(k);
        }
        for (int i = 0; i < 40 && ti < towers.size(); i++) if (!used.contains(towers.get(ti))) SPARE.add(towers.get(ti++)); else ti++;
        for (int i = 0; i < 40 && hi < houses.size(); i++) if (!used.contains(houses.get(hi))) SPARE.add(houses.get(hi++)); else hi++;
        // рабочие места
        List<Spot> works = new ArrayList<>();
        for (String[] wk : WORKS) {
            Vec3d at = place(w, wk[0]);
            if (at == null) continue;
            works.add(new Spot(wk[0], wk[1], wk[2], at, at, at, false, !wk[3].isEmpty()));
        }
        for (int i = 0; i < POOL && i < keys.size(); i++) {
            Res res = new Res();
            res.idx = i;
            res.seed = r.nextLong();
            Random pr = new Random(res.seed);
            res.skin = CitizenEntity.RANDOM_FROM + pr.nextInt(CitizenEntity.RANDOM_COUNT);
            res.name = Names.full(net.minecraft.util.math.random.Random.create(res.seed), CitizenEntity.isSlim(res.skin));
            res.homeKey = keys.get(i);
            res.shift = (pr.nextFloat() - 0.5f) * 1.4f;
            // работа: треть — офисы делового центра, остальные — магазины, порт, полиция, больница, казино…
            if (!offices.isEmpty() && (pr.nextFloat() < 0.38f || works.isEmpty())) {
                int[] o = offices.get(pr.nextInt(offices.size()));
                int s = Island.surface(o[0], o[1]);
                int k = 1 + pr.nextInt(Math.max(1, Math.min(o[3] - 3, 12)));
                int W = o[2] - 1;
                Vec3d desk = new Vec3d(o[0] + 0.5 - W + 2 + pr.nextInt(2 * W - 3), s + 4 * k + 1, o[1] + 4.5 + pr.nextInt(Math.max(1, W - 5)));
                res.work = "office";
                res.workLabel = "офис, " + (k + 1) + " этаж";
                res.workPos = desk;
                res.workGround = new Vec3d(o[0] + 0.5 + jitter(res.seed, 24), s + 1, o[1] + o[2] - 2.5);
                res.workStreet = new Vec3d(o[0] - 2.5 + jitter(res.seed, 32) * 2, s + 1, o[1] + o[2] + 4.5 + jitter(res.seed, 40) * 0.6);
                res.workUp = true;
                res.lunch = res.workStreet.add(pr.nextInt(9) - 4, 0, 1 + pr.nextInt(2));
            } else {
                Spot sp = works.get(pr.nextInt(works.size()));
                res.work = sp.kind();
                res.workLabel = sp.label();
                res.night = sp.night() && pr.nextBoolean();
                res.workPos = sp.pos().add(pr.nextInt(5) - 2, 0, pr.nextInt(5) - 2);
                res.workGround = res.workPos;
                res.workStreet = res.workPos;
                res.lunch = res.workPos.add(pr.nextInt(9) - 4, 0, pr.nextInt(9) - 4);
            }
            // досуг — что поближе к дому
            Home h = homeOf(res.homeKey);
            if (h == null) continue;
            applyHome(res, h);
            String best = null;
            double bd = Double.MAX_VALUE;
            for (int tries = 0; tries < 4; tries++) {
                String[] f = FUN[pr.nextInt(FUN.length)];
                Vec3d at = place(w, f[0]);
                if (at == null) continue;
                double d = at.squaredDistanceTo(h.pos()) * (0.6 + pr.nextDouble());
                if (d < bd) {
                    bd = d;
                    best = f[0] + "|" + f[1];
                }
            }
            if (best != null) {
                String[] f = best.split("\\|");
                res.leisure = f[1];
                res.leisurePos = place(w, f[0]).add(pr.nextInt(9) - 4, 0, pr.nextInt(9) - 4);
            }
            POOL_LIST.add(res);
        }
        // переехавшие (игрок купил их жильё)
        Housing.State st = Housing.state(w.getServer());
        st.moved.forEach((idx, key) -> {
            if (idx < POOL_LIST.size()) {
                Home h = homeOf(key);
                if (h != null) applyHome(POOL_LIST.get(idx), h);
            }
        });
        for (int i = 0; i < POOL_LIST.size(); i++) POOL_LIST.get(i).idx = i;
        Novograd.LOG.info("Жильцы острова: {} человек, {} офисов", POOL_LIST.size(), offices.size());
    }

    private static void applyHome(Res res, Home h) {
        res.homeKey = h.key();
        res.homeKind = h.kind();
        res.homePos = h.pos();
        res.homeDoor = h.door();
        res.homeBed = h.bed();
        // соседи по подъезду выходят не в одну точку
        double jx = jitter(res.seed, 8), jz = jitter(res.seed, 16);
        res.homeGround = h.up() ? h.ground().add(jx, 0, jz * 0.5) : h.ground();
        res.homeStreet = h.street().add(jx * 1.5, 0, jz);
        res.homeUp = h.up();
    }

    /** −1.5..1.5 блока из личного «семени» жильца. */
    private static double jitter(long seed, int shift) {
        return ((seed >>> shift) % 7 - 3) * 0.5;
    }

    /** Игрок купил/снял жильё: прежний жилец переезжает. Возвращает его имя (или null). */
    @Nullable
    public static String moveOut(ServerWorld w, String key) {
        build(w);
        Housing.State st = Housing.state(w.getServer());
        for (Res r : POOL_LIST) {
            if (!r.homeKey.equals(key)) continue;
            String to = null;
            for (String s : SPARE) {
                boolean busy = st.taken.containsKey(s);
                for (Res o : POOL_LIST) if (o.homeKey.equals(s)) busy = true;
                if (!busy) {
                    to = s;
                    break;
                }
            }
            Home h = to == null ? null : homeOf(to);
            // жилец рядом — выходит с вещами и уходит на улицу
            Active a = ACTIVE.get(r.idx);
            if (a != null && w.getEntity(a.uuid) instanceof CitizenEntity c) {
                c.lockActivity(null);
                c.setStationary(false);
                a.leaving = true;
                a.goal = r.homeUp ? r.homeDoor : r.homeStreet;
                go(c, a.goal, 0.6);
                c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.CHEST)); // с вещами
            }
            if (h != null) {
                applyHome(r, h);
                st.moved.put(r.idx, to);
            } else {
                r.homeKey = "";
                st.moved.put(r.idx, "");
            }
            st.markDirty();
            return r.name;
        }
        return null;
    }

    // ------------------------------------------------------------------ расписание
    /** Час суток жильца (0..24) с его сдвигом и ночной сменой. */
    private static double hour(ServerWorld w, Res r) {
        double h = ((w.getTimeOfDay() % 24000) / 1000.0 + 6) % 24;
        return ((h - r.shift - (r.night ? 12 : 0)) % 24 + 24) % 24;
    }

    private static Mode mode(double h) {
        if (h < 6.5) return Mode.SLEEP;
        if (h < 7.5) return Mode.HOME;
        if (h < 8.0) return Mode.LEAVE_HOME;
        if (h < 8.4) return Mode.TRANSIT;
        if (h < 8.8) return Mode.ARRIVE_WORK;
        if (h < 12.5) return Mode.WORK;
        if (h < 13.5) return Mode.LUNCH;
        if (h < 17.5) return Mode.WORK;
        if (h < 17.8) return Mode.LEAVE_WORK;
        if (h < 18.2) return Mode.TRANSIT;
        if (h < 21.3) return Mode.LEISURE;
        if (h < 21.7) return Mode.TRANSIT;
        if (h < 22.2) return Mode.ARRIVE_HOME;
        if (h < 23.2) return Mode.HOME;
        return Mode.SLEEP;
    }

    /** Доля пройденного пути на переходе (0..1). */
    private static double legFrac(double h, Mode m) {
        double a, b;
        switch (m) {
            case LEAVE_HOME -> { a = 7.5; b = 8.0; }
            case ARRIVE_WORK -> { a = 8.4; b = 8.8; }
            case LEAVE_WORK -> { a = 17.5; b = 17.8; }
            case ARRIVE_HOME -> { a = 21.7; b = 22.2; }
            default -> { return 1; }
        }
        return Math.max(0, Math.min(1, (h - a) / (b - a)));
    }

    /** Где жилец «сейчас» и куда идёт. null — в дороге между районами. */
    @Nullable
    private static Vec3d[] where(Res r, Mode m, double h) {
        if (r.homeKey.isEmpty() || r.homePos == null) return null;
        double f = legFrac(h, m);
        return switch (m) {
            case SLEEP, HOME -> new Vec3d[]{r.homePos, r.homePos};
            case LEAVE_HOME -> new Vec3d[]{r.homeGround.lerp(r.homeStreet, f), r.homeStreet};
            case ARRIVE_WORK -> new Vec3d[]{r.workStreet.lerp(r.workGround, f), r.workGround};
            case WORK -> new Vec3d[]{r.workPos, r.workPos};
            case LUNCH -> new Vec3d[]{r.lunch, r.lunch};
            case LEAVE_WORK -> new Vec3d[]{r.workGround.lerp(r.workStreet, f), r.workStreet};
            case LEISURE -> r.leisurePos == null ? null : new Vec3d[]{r.leisurePos, r.leisurePos};
            case ARRIVE_HOME -> new Vec3d[]{r.homeStreet.lerp(r.homeGround, f), r.homeGround};
            case TRANSIT -> null;
        };
    }

    // ------------------------------------------------------------------ живые жильцы рядом с игроками
    private static final class Active {
        UUID uuid;
        Mode mode;
        long until;
        boolean lying, leaving;
        @Nullable Vec3d goal;
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static final Map<String, Long> GREETED = new HashMap<>();

    private static void tick(MinecraftServer server) {
        long t = server.getTicks();
        if (t % 20 != 11) return;
        ServerWorld w = server.getOverworld();
        if (w.getPlayers().isEmpty()) return;
        Housing.build(w);
        build(w);
        Housing.State st = Housing.state(server);
        long day = w.getTimeOfDay() / 24000L + 1;
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity p : w.getPlayers()) if (!p.isSpectator()) players.add(p);
        // 1) живые: убрать дальних и ведомых не туда, вести остальных по распорядку
        for (var e : new ArrayList<>(ACTIVE.entrySet())) {
            Res r = POOL_LIST.get(e.getKey());
            Active a = e.getValue();
            if (!(w.getEntity(a.uuid) instanceof CitizenEntity c) || c.isRemoved()) {
                ACTIVE.remove(e.getKey());
                continue;
            }
            double near = nearest(players, c.getPos(), r.homeUp || r.workUp);
            if (near > DESPAWN_R) {
                c.discard();
                ACTIVE.remove(e.getKey());
                continue;
            }
            drive(w, r, a, c, players);
            if (c.isRemoved()) ACTIVE.remove(e.getKey());
        }
        // 2) кого пора «показать» рядом с игроками
        if (ACTIVE.size() < MAX_ACTIVE) {
            List<Object[]> want = new ArrayList<>();
            for (Res r : POOL_LIST) {
                if (ACTIVE.containsKey(r.idx)) continue;
                Long hos = st.hospital.get(r.idx);
                if (hos != null && hos >= day) continue;
                double h = hour(w, r);
                Mode m = mode(h);
                Vec3d[] at = where(r, m, h);
                if (at == null) continue;
                double d = nearest(players, at[0], true);
                if (d < SPAWN_R) want.add(new Object[]{d, r, m, at});
            }
            want.sort((x, y) -> Double.compare((double) x[0], (double) y[0]));
            Map<UUID, Integer> per = new HashMap<>();
            for (Active a : ACTIVE.values()) {
                Object ent = w.getEntity(a.uuid);
                if (ent instanceof CitizenEntity c) {
                    ServerPlayerEntity p = w.getClosestPlayer(c.getX(), c.getY(), c.getZ(), 80, false) instanceof ServerPlayerEntity sp ? sp : null;
                    if (p != null) per.merge(p.getUuid(), 1, Integer::sum);
                }
            }
            for (Object[] o : want) {
                if (ACTIVE.size() >= MAX_ACTIVE) break;
                Res r = (Res) o[1];
                Vec3d[] at = (Vec3d[]) o[3];
                ServerPlayerEntity p = closest(players, at[0]);
                if (p == null || per.getOrDefault(p.getUuid(), 0) >= PER_PLAYER) continue;
                if (spawn(w, r, (Mode) o[2], at)) per.merge(p.getUuid(), 1, Integer::sum);
            }
        }
        // 3) двойники из выгруженных чанков и прошлых запусков
        if (t % 100 == 11) for (ServerPlayerEntity p : players)
            for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(96, 40, 96), Residents::is)) {
                int idx = idxOf(c);
                Active a = ACTIVE.get(idx);
                if (a == null || !a.uuid.equals(c.getUuid())) c.discard();
            }
        // 4) соседи здороваются
        for (ServerPlayerEntity p : players) greet(w, p, day);
    }

    /** Расстояние до ближайшего игрока; для этажей высоток учитываем высоту (на другом этаже — «далеко»). */
    private static double nearest(List<ServerPlayerEntity> players, Vec3d at, boolean floors) {
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : players) {
            double dx = p.getX() - at.x, dz = p.getZ() - at.z, dy = Math.abs(p.getY() - at.y);
            double d = Math.sqrt(dx * dx + dz * dz) + (dy > 5 ? (floors ? 1000 : dy) : 0);
            best = Math.min(best, d);
        }
        return best;
    }

    @Nullable
    private static ServerPlayerEntity closest(List<ServerPlayerEntity> players, Vec3d at) {
        ServerPlayerEntity best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayerEntity p : players) {
            double d = p.squaredDistanceTo(at);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private static boolean spawn(ServerWorld w, Res r, Mode m, Vec3d[] at) {
        BlockPos bp = BlockPos.ofFloored(at[0]);
        if (!w.isChunkLoaded(bp)) return false;
        Vec3d pos = freeSpot(w, at[0]);
        if (pos == null) return false;
        if (w.isSkyVisible(BlockPos.ofFloored(pos).up()) && !Sidewalks.unseen(w, pos.add(0, 1, 0))) return false; // на улице — не из воздуха на глазах
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        c.setup(r.skin, CitizenEntity.Role.CITIZEN, false, r.name);
        c.randomizeBody(net.minecraft.util.math.random.Random.create(r.seed), false);
        c.addCommandTag(TAG);
        c.addCommandTag(TAG + ":" + r.idx);
        c.refreshPositionAndAngles(pos.x, pos.y, pos.z, (float) (r.seed % 360), 0);
        if (!w.spawnEntity(c)) return false;
        Active a = new Active();
        a.uuid = c.getUuid();
        a.mode = null;
        ACTIVE.put(r.idx, a);
        drive(w, r, a, c, w.getPlayers());
        return true;
    }

    /** Свободное место (ноги и голова не в блоке) рядом с точкой. */
    @Nullable
    private static Vec3d freeSpot(ServerWorld w, Vec3d at) {
        BlockPos b = BlockPos.ofFloored(at);
        for (int r = 0; r <= 3; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos p = b.add(dx, dy, dz);
                        if (w.getBlockState(p).getCollisionShape(w, p).isEmpty() && w.getBlockState(p.up()).getCollisionShape(w, p.up()).isEmpty()
                                && !w.getBlockState(p.down()).getCollisionShape(w, p.down()).isEmpty())
                            return new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                    }
                }
        return null;
    }

    /** Вести живого жильца по распорядку. */
    private static void drive(ServerWorld w, Res r, Active a, CitizenEntity c, List<ServerPlayerEntity> players) {
        if (!c.calm()) { // испугался, дерётся, сбит — своя логика горожанина
            c.directTo = null;
            if (a.lying) wake(c, a);
            a.mode = null;
            return;
        }
        if (a.leaving) { // переезжает: дошёл до улицы (или лифта) — исчез (но не на глазах у игрока)
            if ((a.goal == null || c.squaredDistanceTo(a.goal) < 4 || c.directTo == null) && Sidewalks.unseen(c)) c.discard();
            return;
        }
        double h = hour(w, r);
        Mode m = mode(h);
        Vec3d[] at = where(r, m, h);
        long now = w.getTime();
        if (at == null) { // пора в дорогу между районами: дошёл до тротуара — «сел в машину»
            if (a.lying) wake(c, a);
            Vec3d exit = c.getY() - (r.homeUp ? r.homeGround.y : c.getY()) > 3 && r.homeUp ? r.homeDoor : null;
            Vec3d to = exit != null ? exit : a.goal != null ? a.goal : c.getPos();
            if (c.squaredDistanceTo(to) < 4 || c.directTo == null && now > a.until) gone(c, exit == null);
            else if (c.directTo == null) {
                go(c, to, 0.6);
                a.until = now + 400;
            }
            return;
        }
        Vec3d target = at[1];
        // в высотке на другом этаже: жилец идёт к лифту (двери квартиры / из офиса к лобби) и уезжает
        boolean upstairs = Math.abs(c.getY() - target.y) > 3;
        if (upstairs) {
            if (a.lying) wake(c, a);
            Vec3d lift = r.homeUp && Math.abs(c.getY() - r.homeDoor.y) < 3 ? r.homeDoor : new Vec3d(c.getX(), c.getY(), c.getZ());
            if (c.squaredDistanceTo(lift) < 4 || now > a.until + 600) gone(c, false);
            else if (c.directTo == null) {
                go(c, lift, 0.6);
                if (a.mode != m) a.until = now;
            }
            a.mode = m;
            return;
        }
        boolean changed = a.mode != m;
        a.mode = m;
        switch (m) {
            case LEAVE_HOME, ARRIVE_WORK, LEAVE_WORK, ARRIVE_HOME -> {
                if (a.lying) wake(c, a);
                if (changed || c.directTo == null && c.squaredDistanceTo(target) > 4) {
                    a.goal = target;
                    go(c, target, 0.6);
                }
                if (c.directTo == null && c.squaredDistanceTo(target) < 4 && (m == Mode.LEAVE_HOME || m == Mode.LEAVE_WORK)) gone(c, true); // уехал
            }
            case SLEEP -> {
                if (a.lying) return;
                if (c.squaredDistanceTo(target) > 9) {
                    if (c.directTo == null) go(c, target, 0.5);
                    return;
                }
                lieDown(w, c, a, target, r.homeBed);
            }
            default -> {
                if (a.lying) wake(c, a);
                if (c.squaredDistanceTo(target) > 36 && (!c.isBusy() || changed)) { // занят делом (плывёт, носит ящики) — не дёргать
                    if (c.directTo == null || changed) go(c, target, 0.6);
                    return;
                }
                if (c.directTo != null || now < a.until && !changed) return;
                a.until = now + 300 + w.getRandom().nextInt(300);
                doThing(w, r, m, c, target);
            }
        }
    }

    /**
     * Уйти «за кадр» (сел в машину/такси, уехал на лифте): только когда никто из игроков не смотрит.
     * На глазах — ловит такси у бордюра (на улице) или ждёт лифт, посматривая на часы.
     */
    private static void gone(CitizenEntity c, boolean street) {
        if (Sidewalks.unseen(c)) {
            c.discard();
            return;
        }
        if (!c.isBusy()) c.busy(street ? CitizenEntity.Activity.HAIL : CitizenEntity.Activity.WATCH, 60 + c.getRandom().nextInt(60), null, ItemStack.EMPTY);
    }

    /** Идти по делам по тротуарам (world/Sidewalks): не отвлекаясь на болтовню; в дождь — под зонтиком, кто-то говорит по телефону. */
    private static void go(CitizenEntity c, Vec3d to, double speed) {
        CitizenEntity.Activity act = null;
        ItemStack held = ItemStack.EMPTY;
        if (c.getWorld().isRaining() && c.getWorld().isSkyVisible(c.getBlockPos().up())) {
            act = CitizenEntity.Activity.UMBRELLA;
            held = new ItemStack(ModContent.UMBRELLA);
            speed += 0.15;
        } else if (c.getRandom().nextInt(5) == 0) {
            act = CitizenEntity.Activity.PHONEWALK;
            held = new ItemStack(ModContent.PHONE);
        }
        boolean indoors = !c.getWorld().isSkyVisible(c.getBlockPos().up()) || Math.abs(c.getY() - to.y) > 3;
        c.walkRoute(indoors ? List.of(to) : Sidewalks.route(c.getPos(), to), speed, act, held, null);
        if (c.directTo == null) c.direct(to, speed);
    }

    /** Чем занят на месте: работа, обед, досуг, дома. */
    private static void doThing(ServerWorld w, Res r, Mode m, CitizenEntity c, Vec3d at) {
        var rnd = w.getRandom();
        int ticks = 600;
        switch (m) {
            case WORK -> {
                if (r.work.equals("office")) { // за столом печатает; иногда — звонок, кофе, часы, потянуться
                    BlockPos chair = chair(w, BlockPos.ofFloored(at), 5);
                    int k = rnd.nextInt(10);
                    if (chair != null && k < 7) c.busy(k < 5 ? CitizenEntity.Activity.TYPE : CitizenEntity.Activity.SIT, ticks, Vec3d.ofBottomCenter(chair), ItemStack.EMPTY);
                    else if (k < 8) c.busy(CitizenEntity.Activity.DRINK, 200, at, new ItemStack(ModContent.COLA));
                    else if (k < 9) c.busy(CitizenEntity.Activity.STRETCH, 120, at, ItemStack.EMPTY);
                    else c.busy(CitizenEntity.Activity.PHONE, ticks, at, new ItemStack(ModContent.PHONE));
                    return;
                }
                CitizenEntity.Activity act = switch (r.work) {
                    case "bar" -> rnd.nextBoolean() ? CitizenEntity.Activity.TALK : rnd.nextBoolean() ? CitizenEntity.Activity.SWEEP : CitizenEntity.Activity.LAUGH;
                    case "food", "shop", "bank" -> rnd.nextInt(3) == 0 ? CitizenEntity.Activity.SWEEP : rnd.nextBoolean() ? CitizenEntity.Activity.TALK : CitizenEntity.Activity.THINK;
                    case "hotel" -> rnd.nextBoolean() ? CitizenEntity.Activity.SWEEP : CitizenEntity.Activity.TALK;
                    case "casino" -> rnd.nextBoolean() ? CitizenEntity.Activity.CHEER : CitizenEntity.Activity.TALK;
                    case "police" -> rnd.nextBoolean() ? CitizenEntity.Activity.PHONE : CitizenEntity.Activity.WATCH;
                    case "hospital" -> rnd.nextBoolean() ? CitizenEntity.Activity.PHONE : CitizenEntity.Activity.THINK;
                    case "port", "marina" -> CitizenEntity.Activity.CARRY;
                    case "garage", "air" -> rnd.nextInt(3) == 0 ? CitizenEntity.Activity.THINK : CitizenEntity.Activity.HAMMER;
                    case "farm" -> CitizenEntity.Activity.SWEEP;
                    case "gym" -> rnd.nextBoolean() ? CitizenEntity.Activity.STRETCH : CitizenEntity.Activity.CHEER;
                    default -> CitizenEntity.Activity.THINK;
                };
                Item held = switch (r.work) {
                    case "port", "marina" -> rnd.nextBoolean() ? Items.CHEST : ModContent.PARCEL;
                    case "garage", "air" -> act == CitizenEntity.Activity.HAMMER ? Items.IRON_AXE : Items.IRON_INGOT;
                    case "farm" -> Items.IRON_HOE;
                    case "food" -> act == CitizenEntity.Activity.SWEEP ? Items.BRUSH : ModContent.BURGER;
                    case "police", "hospital" -> ModContent.PHONE;
                    default -> act == CitizenEntity.Activity.SWEEP ? Items.BRUSH : Items.AIR;
                };
                if (act == CitizenEntity.Activity.CARRY) at = at.add(rnd.nextInt(13) - 6, 0, rnd.nextInt(13) - 6); // носит ящики туда-сюда
                c.busy(act, ticks, at.add(rnd.nextInt(3) - 1, 0, rnd.nextInt(3) - 1), new ItemStack(held));
            }
            case LUNCH -> { // обед: на скамейке/стуле — сидя, иначе стоя с бургером или колой
                BlockPos seat = chair(w, BlockPos.ofFloored(at), 6);
                if (seat != null && rnd.nextBoolean()) c.busy(CitizenEntity.Activity.SIT_EAT, ticks, Vec3d.ofBottomCenter(seat), new ItemStack(ModContent.BURGER));
                else if (rnd.nextBoolean()) c.busy(CitizenEntity.Activity.EAT, ticks, at, new ItemStack(ModContent.BURGER));
                else c.busy(CitizenEntity.Activity.DRINK, ticks, at, new ItemStack(ModContent.COLA));
            }
            case LEISURE -> {
                CitizenEntity.Activity[] dances = {CitizenEntity.Activity.DANCE, CitizenEntity.Activity.DANCE2, CitizenEntity.Activity.DANCE3};
                CitizenEntity.Activity act = switch (r.leisure) {
                    case "club" -> dances[rnd.nextInt(3)];
                    case "bar" -> rnd.nextInt(3) == 0 ? CitizenEntity.Activity.DRINK : rnd.nextBoolean() ? CitizenEntity.Activity.LAUGH : CitizenEntity.Activity.TALK;
                    case "casino" -> rnd.nextBoolean() ? CitizenEntity.Activity.CHEER : CitizenEntity.Activity.THINK;
                    case "beach" -> {
                        if (Island.zone(c.getBlockX(), c.getBlockZ()) == Island.Zone.BEACH && !w.isRaining()) { // на пляже — как все пляжники
                            Leisure.beachActivity(w, c);
                            yield null;
                        }
                        yield CitizenEntity.Activity.SMOKE;
                    }
                    case "park" -> {
                        BlockPos bench = chair(w, BlockPos.ofFloored(at), 6);
                        if (bench != null) {
                            c.busy(rnd.nextBoolean() ? CitizenEntity.Activity.SITCROSS : CitizenEntity.Activity.SIT, ticks, Vec3d.ofBottomCenter(bench), ItemStack.EMPTY);
                            yield null;
                        }
                        yield CitizenEntity.Activity.PHONE;
                    }
                    default -> { // прогулка: телефон, болтовня, покурить, часы
                        CitizenEntity.Activity[] pool = {CitizenEntity.Activity.PHONE, CitizenEntity.Activity.TALK, CitizenEntity.Activity.SMOKE, CitizenEntity.Activity.WATCH, CitizenEntity.Activity.SHRUG};
                        yield pool[rnd.nextInt(pool.length)];
                    }
                };
                if (act != null) c.busy(act, ticks, at.add(rnd.nextInt(5) - 2, 0, rnd.nextInt(5) - 2),
                        act == CitizenEntity.Activity.PHONE ? new ItemStack(ModContent.PHONE) : act == CitizenEntity.Activity.DRINK ? new ItemStack(ModContent.COLA) : ItemStack.EMPTY);
            }
            default -> { // дома: телефон, диван, кухня
                BlockPos sofa = chair(w, BlockPos.ofFloored(at), 5);
                int k = rnd.nextInt(6);
                if (sofa != null && k < 3) c.busy(k == 0 ? CitizenEntity.Activity.SITCROSS : k == 1 ? CitizenEntity.Activity.SIT_EAT : CitizenEntity.Activity.SIT, ticks,
                        Vec3d.ofBottomCenter(sofa), k == 1 ? new ItemStack(ModContent.BURGER) : ItemStack.EMPTY);
                else if (k == 3) c.busy(CitizenEntity.Activity.STRETCH, 160, at, ItemStack.EMPTY);
                else if (k == 4) c.busy(CitizenEntity.Activity.DRINK, ticks, at, new ItemStack(ModContent.COLA));
                else c.busy(CitizenEntity.Activity.PHONE, ticks, at, new ItemStack(ModContent.PHONE));
            }
        }
    }

    /** Стул, кресло, диван, скамейка: нижняя половина ступенек, над ними пусто, не лестничный марш. */
    @Nullable
    private static BlockPos chair(ServerWorld w, BlockPos at, int r) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos b : BlockPos.iterate(at.add(-r, -1, -r), at.add(r, 1, r))) {
            BlockState s = w.getBlockState(b);
            if (!(s.getBlock() instanceof StairsBlock) || s.get(StairsBlock.HALF) != BlockHalf.BOTTOM) continue;
            if (!w.getBlockState(b.up()).getCollisionShape(w, b.up()).isEmpty()) continue;
            if (w.getBlockState(b.down()).getBlock() instanceof StairsBlock || w.getBlockState(b.up()).getBlock() instanceof StairsBlock) continue;
            double d = b.getSquaredDistance(at) + w.getRandom().nextDouble() * 6;
            if (d < bd) {
                bd = d;
                best = b.toImmutable();
            }
        }
        return best;
    }

    /** Лечь в свою кровать (как в HomeGoal: ноги у изножья, голова на подушке). */
    private static void lieDown(ServerWorld w, CitizenEntity c, Active a, Vec3d home, @Nullable Vec3d bedHint) {
        BlockPos at = BlockPos.ofFloored(bedHint != null ? bedHint : home);
        int r = bedHint != null ? 2 : 7;
        for (BlockPos b : BlockPos.iterate(at.add(-r, -1, -r), at.add(r, 1, r))) {
            BlockState s = w.getBlockState(b);
            if (!(s.getBlock() instanceof BedBlock) || s.get(BedBlock.PART) != BedPart.FOOT) continue;
            Direction d = s.get(BedBlock.FACING);
            Vec3d foot = Vec3d.ofBottomCenter(b);
            Vec3d lie = foot.add(-d.getOffsetX() * 0.45, 0.5625, -d.getOffsetZ() * 0.45);
            float yaw = (float) Math.toDegrees(Math.atan2(d.getOffsetX(), -d.getOffsetZ()));
            c.getNavigation().stop();
            c.directTo = null;
            c.refreshPositionAndAngles(lie.x, lie.y, lie.z, yaw, 0);
            c.setBodyYaw(yaw);
            c.setHeadYaw(yaw);
            c.setStationary(true);
            c.lockActivity(CitizenEntity.Activity.SLEEP);
            c.setNoGravity(true);
            a.lying = true;
            return;
        }
        c.busy(CitizenEntity.Activity.THINK, 400, home, ItemStack.EMPTY); // кровати не нашлось — просто дома
    }

    private static void wake(CitizenEntity c, Active a) {
        a.lying = false;
        c.setNoGravity(false);
        c.lockActivity(null);
        c.setStationary(false);
        c.refreshPositionAndAngles(c.getX(), Math.floor(c.getY()) + 0.01, c.getZ(), c.getYaw(), 0);
    }

    /** Соседи по дому/улице здороваются с новым жильцом раз в день. */
    private static void greet(ServerWorld w, ServerPlayerEntity p, long day) {
        List<Housing.Listing> mine = Housing.homes(p);
        if (mine.isEmpty()) return;
        boolean nearHome = false;
        for (Housing.Listing l : mine) if (l.inside.squaredDistanceTo(p.getPos()) < 40 * 40 || l.street.squaredDistanceTo(p.getPos()) < 30 * 30) nearHome = true;
        if (!nearHome) return;
        for (var e : ACTIVE.entrySet()) {
            Res r = POOL_LIST.get(e.getKey());
            if (!(w.getEntity(e.getValue().uuid) instanceof CitizenEntity c) || c.squaredDistanceTo(p) > 5 * 5 || !c.calm() || c.asleep()) continue;
            if (r.homePos == null || r.homePos.squaredDistanceTo(p.getPos()) > 60 * 60) continue; // соседи — те, кто живёт рядом
            String key = e.getKey() + "|" + p.getUuidAsString();
            if (Long.valueOf(day).equals(GREETED.get(key))) continue;
            GREETED.put(key, day);
            c.face(p);
            c.busy(CitizenEntity.Activity.WAVE, 50, null, ItemStack.EMPTY);
            double hh = ((w.getTimeOfDay() % 24000) / 1000.0 + 6) % 24;
            String hi = hh < 11 ? "Доброе утро, сосед!" : hh < 18 ? "Привет, сосед! Как обживаешься?" : "Добрый вечер, сосед!";
            boolean she = CitizenEntity.isSlim(c.getSkin());
            p.sendMessage(Text.literal((she ? "Соседка " : "Сосед ") + r.name + ": «" + hi + "»").formatted(Formatting.AQUA), true);
            return;
        }
    }

    // ------------------------------------------------------------------ дела прохожих на острове (CitizenEntity.ScheduleGoal)
    private static final String[][] ERRANDS_DAY = {{"shop_clothes"}, {"shop_barber"}, {"shop_gym"}, {"shop_boutique"}, {"bank_tower"}, {"downtown_plaza"},
            {"riv_strip"}, {"beach_bar"}, {"riv_beach"}, {"port_gate"}, {"riv_marina"}, {"village"}, {"airfield_hangar"}};
    private static final String[][] ERRANDS_EVENING = {{"club_neon"}, {"casino_dt"}, {"riv_casino"}, {"riv_strip"}, {"beach_bar"}, {"downtown_plaza"}, {"riv_hotel"}};

    /** Куда сходить обычному прохожему острова: место по времени суток поблизости или дальше по тротуару. */
    @Nullable
    public static Vec3d islandErrand(CitizenEntity c, long tod) {
        if (!(c.getWorld() instanceof ServerWorld w)) return null;
        var rnd = c.getRandom();
        String[][] list = tod > 11500 && tod < 18000 ? ERRANDS_EVENING : ERRANDS_DAY;
        List<Vec3d> near = new ArrayList<>();
        for (String[] e : list) {
            Vec3d at = place(w, e[0]);
            if (at != null && at.squaredDistanceTo(c.getPos()) < 90 * 90) near.add(at);
        }
        if (!near.isEmpty() && rnd.nextInt(3) != 0) return near.get(rnd.nextInt(near.size())).add(rnd.nextInt(9) - 4, 0, rnd.nextInt(9) - 4);
        // прогулка по тротуару: вдоль своей улицы на 20–40 блоков
        int x = c.getBlockX(), z = c.getBlockZ();
        boolean alongZ = Island.gridDist(x) <= Island.gridDist(z);
        int step = (20 + rnd.nextInt(21)) * (rnd.nextBoolean() ? 1 : -1);
        if (alongZ) {
            int line = (int) (Math.round((x - 24) / 48.0) * 48 + 24);
            x = line + (x >= line ? 5 : -5);
            z += step;
        } else {
            int line = (int) (Math.round((z - 24) / 48.0) * 48 + 24);
            z = line + (z >= line ? 5 : -5);
            x += step;
        }
        Island.Zone zn = Island.zone(x, z);
        if (!Island.urban(zn) && zn != Island.Zone.BEACH && zn != Island.Zone.PORT) return null;
        return new Vec3d(x + 0.5, Island.surface(x, z) + 1, z + 0.5);
    }

    // ------------------------------------------------------------------ для автотестов
    public static int activeCount() {
        return ACTIVE.size();
    }

    public static int poolSize() {
        return POOL_LIST.size();
    }

    /** Отладка: «имя — режим — где» для живых жильцов. */
    public static List<String> debug(ServerWorld w) {
        List<String> out = new ArrayList<>();
        for (var e : ACTIVE.entrySet()) {
            Res r = POOL_LIST.get(e.getKey());
            var ent = w.getEntity(e.getValue().uuid);
            out.add(r.name + " [" + r.homeKey + " → " + r.workLabel + "] " + e.getValue().mode
                    + (ent == null ? "" : String.format(" @%.0f,%.0f,%.0f %s", ent.getX(), ent.getY(), ent.getZ(), ((CitizenEntity) ent).getActivity())));
        }
        return out;
    }

    /** Отладка: точка дома/работы жильца с индексом idx. */
    @Nullable
    public static Vec3d[] places(int idx) {
        if (idx < 0 || idx >= POOL_LIST.size()) return null;
        Res r = POOL_LIST.get(idx);
        return new Vec3d[]{r.homePos, r.workPos, r.leisurePos};
    }

    /** Отладка: индекс жильца, живущего в жилье key. */
    public static int residentOf(String key) {
        for (Res r : POOL_LIST) if (r.homeKey.equals(key)) return r.idx;
        return -1;
    }
}
