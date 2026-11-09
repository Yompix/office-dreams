package com.yompix.novograd.world;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.gta.Activities;
import com.yompix.novograd.gta.Economy;
import com.yompix.novograd.gta.Wanted;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.CityLife;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Недвижимость Новограда: квартиры в жилых башнях делового центра, люксы отелей Ривьеры, дома в пригороде,
 * виллы на Ривьере, пентхаус с вертолётной площадкой и гаражом на 10 машин, а также старые убежища (gta/Activities)
 * и дома старого города (life/CityLife) — всё в одном каталоге риелтора (телефон → «Дом»).
 * <p>
 * Купить — один раз, снять — посуточно (списывается с карты каждое утро; три дня без оплаты — выселение).
 * Своё жильё даёт: возрождение дома после больницы, гардероб, сейф для грязных денег (не пропадают при гибели и аресте),
 * сон (перемотка до утра) и гараж (world/Garage). У двери — табличка «Продаётся / Сдаётся», на радаре — значок дома.
 * <p>
 * Всё на маркерах и данных (PersistentState «novograd_housing»): работает и в старых мирах, генерацию не меняет.
 */
public final class Housing {
    private Housing() {}

    // ------------------------------------------------------------------ каталог
    public enum Kind {
        APT("Квартира"), HOTEL("Люкс в отеле"), HOUSE("Дом"), VILLA("Вилла"), PENTHOUSE("Пентхаус"), SAFEHOUSE("Убежище");
        public final String title;

        Kind(String t) {
            title = t;
        }
    }

    /** Одно жильё. id: «T:px:pz:этаж:номер» (башня), «H:px:pz:qx:qz» (дом), «P:i» (убежище), «C:id» (старый город). */
    public static final class Listing {
        public final String id, name, area;
        public final Kind kind;
        public final int price, rent, cars, act, prop;
        /** Где стоять дома (точка возрождения), дверь, «внутрь» от двери, табличка, въезд в гараж и куда встать у гаража. */
        public final Vec3d inside, sign, garage, street;
        @Nullable public final BlockPos door;
        public final float garageYaw;
        @Nullable public final Vec3d heli;
        public final int floor;
        /** Флаг старого владения (prop:i, house:id) — в старых сохранениях. */
        @Nullable public final String flag;

        Listing(String id, String name, String area, Kind kind, int price, int rent, int cars, int act, int prop, Vec3d inside, Vec3d sign,
                @Nullable BlockPos door, Vec3d garage, float garageYaw, Vec3d street, @Nullable Vec3d heli, int floor, @Nullable String flag) {
            this.id = id;
            this.name = name;
            this.area = area;
            this.kind = kind;
            this.price = price;
            this.rent = rent;
            this.cars = cars;
            this.act = act;
            this.prop = prop;
            this.inside = inside;
            this.sign = sign;
            this.door = door;
            this.garage = garage;
            this.garageYaw = garageYaw;
            this.street = street;
            this.heli = heli;
            this.floor = floor;
            this.flag = flag;
        }

        public boolean buyable() {
            return price > 0;
        }

        public boolean rentable() {
            return rent > 0;
        }

        /** Насколько «дома» считается игрок: радиус по горизонтали. */
        double radius() {
            return kind == Kind.PENTHOUSE ? 15 : kind == Kind.SAFEHOUSE ? 6 : 8;
        }
    }

    private static final List<Listing> LISTINGS = new ArrayList<>();
    private static final Map<String, Listing> BY_ID = new HashMap<>();
    /** Пентхаус: башня делового центра с площадкой для вертолёта на крыше (участок 8, −10: 21 этаж). */
    public static final int PENT_PX = 8, PENT_PZ = -10;
    private static final int[][] UNIT_DOOR = {{-7, -5}, {6, -5}, {-3, -2}, {4, 4}};

    public static List<Listing> listings() {
        return LISTINGS;
    }

    @Nullable
    public static Listing get(String id) {
        return BY_ID.get(id);
    }

    private static void add(@Nullable Listing l) {
        if (l == null || BY_ID.containsKey(l.id)) return;
        LISTINGS.add(l);
        BY_ID.put(l.id, l);
    }

    /** Квартира (или люкс отеля) в башне: участок, этаж k (0 — лобби), номер 0..3 (северо-запад, северо-восток, юго-запад, юго-восток). */
    @Nullable
    static Listing tower(String name, Kind kind, long px, long pz, int k, int u, int price, int rent, int cars, int act, int prop, @Nullable String flag) {
        int cx = (int) px * 48, cz = (int) pz * 48;
        int[] info = Island.lotInfo(cx, cz);
        int want = kind == Kind.HOTEL ? 2 : 1;
        if (info == null || info[0] != want || k < 1 || k >= info[2]) {
            Novograd.LOG.warn("Недвижимость: на участке {},{} нет нужной башни ({}), «{}» пропущено", px, pz, info == null ? "-" : info[0], name);
            return null;
        }
        int s = Island.surface(cx, cz), half = info[1], n = info[2];
        int L = s + 4 * k;
        int[] d = UNIT_DOOR[u];
        Direction in = u < 2 ? Direction.NORTH : Direction.SOUTH;
        BlockPos door = new BlockPos(cx + d[0], L + 1, cz + d[1]);
        Vec3d inside = Vec3d.ofBottomCenter(door).add(in.getOffsetX() * 2, 0, in.getOffsetZ() * 2);
        Vec3d sign = Vec3d.ofBottomCenter(door).add(-in.getOffsetX() * 1.1, 0.9, -in.getOffsetZ() * 1.1);
        Vec3d garage = new Vec3d(cx + 8.5, s + 1, cz + 21.5), street = new Vec3d(cx + 4.5, s + 1, cz + 19.5);
        Vec3d heli = kind == Kind.PENTHOUSE ? new Vec3d(cx + 0.5, s + 4 * n + 1, cz + 0.5) : null;
        String id = "T:" + px + ":" + pz + ":" + k + ":" + u;
        String area = Island.zone(cx, cz) == Island.Zone.RIVIERA ? "Ривьера" : "Деловой центр";
        return new Listing(id, name, area + " · " + (k + 1) + " этаж", kind, price, rent, cars, act, prop, inside, sign, door, garage, 90, street, heli, k, flag);
    }

    /** Дом в пригороде (или вилла на Ривьере): участок и четверть (qx, qz = ±1), дверь смотрит на улицу. */
    @Nullable
    static Listing house(String name, Kind kind, long px, long pz, int qx, int qz, int price, int rent, int cars, int act) {
        int cx = (int) px * 48, cz = (int) pz * 48;
        Island.Zone zn = Island.zone(cx, cz);
        if (zn != Island.Zone.SUBURB || (pz == 2 && qz > 0 && (px == -5 || px == -7 || px == -9 || px == -35))) {
            Novograd.LOG.warn("Недвижимость: участок {},{} не пригород, «{}» пропущено", px, pz, name);
            return null;
        }
        int s = Island.surface(cx, cz);
        int hx = cx + qx * 9;
        BlockPos door = new BlockPos(hx, s + 1, cz + qz * 13);
        Vec3d inside = new Vec3d(hx + 0.5, s + 1, cz + qz * 11 + 0.5);
        Vec3d sign = new Vec3d(hx + 1.5, s + 1.9, cz + qz * 15 + 0.5);
        Vec3d garage = new Vec3d(hx + 0.5, s + 1, cz + qz * 21 + 0.5), street = new Vec3d(hx + 2.5, s + 1, cz + qz * 19 + 0.5);
        String id = "H:" + px + ":" + pz + ":" + qx + ":" + qz;
        String area = cx < -840 ? "Ривьера" : "Пригород";
        return new Listing(id, name, area, kind, price, rent, cars, act, -1, inside, sign, door, garage, 90, street, null, 0, null);
    }

    private static boolean built;

    /** Каталог собирается при старте сервера (нужны места старого города из TownState). */
    static void build(ServerWorld w) {
        if (built) return;
        built = true;
        Object[][] props = Activities.props();
        // пентхаус — это убежище «Пентхаус в центре» (флаг prop:3 в старых сохранениях), только теперь настоящий: верхний этаж башни
        int[] pinfo = Island.lotInfo(PENT_PX * 48, PENT_PZ * 48);
        int top = pinfo == null ? 6 : pinfo[2] - 1;
        add(tower("Пентхаус «Небо»", Kind.PENTHOUSE, PENT_PX, PENT_PZ, top, 2, (int) props[3][3], 0, 10, 0, 3, "prop:3"));
        // квартиры в жилых башнях делового центра
        add(tower("Квартира «Звёздная»", Kind.APT, 7, -8, 12, 2, 18000, 120, 2, 0, -1, null));
        add(tower("Квартира «Лофт-Сити»", Kind.APT, 6, -6, 5, 0, 12000, 80, 1, 0, -1, null));
        add(tower("Квартира «Парковая»", Kind.APT, 11, -5, 9, 3, 15000, 100, 2, 0, -1, null));
        add(tower("Студия «Старт»", Kind.APT, 5, -4, 3, 1, 8000, 50, 1, 0, -1, null));
        add(tower("Квартира «Высота»", Kind.APT, 6, -11, 17, 2, 24000, 150, 2, 1, -1, null));
        add(tower("Квартира «Панорама»", Kind.APT, 11, -8, 15, 0, 22000, 140, 2, 1, -1, null));
        // люксы отелей Ривьеры — только аренда
        add(tower("Люкс «Лазурный берег»", Kind.HOTEL, -29, 0, 8, 3, 0, 220, 1, 2, -1, null));
        add(tower("Люкс «Стрип»", Kind.HOTEL, -28, 2, 9, 2, 0, 180, 1, 2, -1, null));
        // дома в пригороде
        add(house("Дом на Сиреневой", Kind.HOUSE, -4, 8, 1, 1, 14000, 90, 3, 0));
        add(house("Дом с садом", Kind.HOUSE, -8, 4, -1, -1, 12000, 80, 3, 0));
        add(house("Дом у холмов", Kind.HOUSE, -3, -6, 1, -1, 13000, 85, 3, 0));
        add(house("Дом на Каштановой", Kind.HOUSE, -9, -3, -1, 1, 11000, 75, 2, 0));
        add(house("Дом у магистрали", Kind.HOUSE, 2, 8, 1, 1, 15000, 95, 3, 0));
        add(house("Дом на Лесной", Kind.HOUSE, -6, -9, -1, -1, 10000, 70, 2, 0));
        // виллы Ривьеры
        add(house("Вилла «Белый парус»", Kind.VILLA, -35, -15, -1, -1, 45000, 300, 4, 2));
        add(house("Вилла «Кипарис»", Kind.VILLA, -33, 13, 1, 1, 40000, 280, 4, 2));
        add(house("Вилла «Закат»", Kind.VILLA, -36, 6, -1, 1, 42000, 290, 4, 2));
        // старые убежища (кроме пентхауса — он выше): дверь на улице, без аренды
        int[] cars = {2, 2, 3, 10, 4, 6};
        for (int i = 0; i < props.length; i++) {
            if (i == 3) continue;
            int x = (int) props[i][1], z = (int) props[i][2];
            int y = Island.surface(x, z) + 1;
            Vec3d at = new Vec3d(x + 0.5, y, z + 0.5);
            add(new Listing("P:" + i, (String) props[i][0], "Убежище", Kind.SAFEHOUSE, (int) props[i][3], 0, cars[Math.min(i, cars.length - 1)], 0, i,
                    at, at.add(0, 1.4, 0), null, at.add(4, 0, 4), 0, at.add(2, 0, 0), null, 0, "prop:" + i));
        }
        // дома старого города (если он построен в этом мире)
        TownState ts = TownState.get(w);
        for (String[] h : CityLife.HOUSES) {
            double[] bed = ts.place(h[0] + "_bed"), door = ts.place(h[0] + "_door");
            if (bed == null || door == null) continue;
            Vec3d in = new Vec3d(bed[0], bed[1], bed[2]), out = new Vec3d(door[0], door[1], door[2]);
            add(new Listing("C:" + h[0], h[1], "Старый город", Kind.HOUSE, Integer.parseInt(h[2]), 0, 2, 0, -1, in, out.add(0, 1.2, 0),
                    BlockPos.ofFloored(out), out.add(3, 0, 3), 0, out.add(1, 0, 1), null, 0, "house:" + h[0]));
        }
        Novograd.LOG.info("Недвижимость: {} предложений в каталоге", LISTINGS.size());
    }

    // ------------------------------------------------------------------ данные
    /** Жильё игрока: куплено или снято, по какой день оплачено, сколько дней долг. */
    public static final class Holding {
        public boolean rent;
        public long paidDay;
        public int unpaid;
    }

    public static final class Owner {
        public final Map<String, Holding> homes = new LinkedHashMap<>();
        /** Грязные наличные в домашнем сейфе. */
        public int safe;
        /** Машины игрока (в гаражах и на штрафстоянке) — см. world/Garage. */
        public final List<NbtCompound> cars = new ArrayList<>();
        public int nextCar = 1;
        @Nullable public UUID heli;
        /** Монетки уличным музыкантам (gta/StreetPerformers). */
        public int tips;
    }

    public static final class State extends PersistentState {
        static final Type<State> TYPE = new Type<>(State::new, State::read, DataFixTypes.LEVEL);
        public final Map<UUID, Owner> owners = new HashMap<>();
        /** Кому принадлежит жильё (квартиры и дома — у одного хозяина; убежища у каждого свои). */
        public final Map<String, UUID> taken = new HashMap<>();
        /** Жильцы острова (world/Residents): кто куда переехал и кто лежит в больнице до какого дня. */
        public final Map<Integer, String> moved = new HashMap<>();
        public final Map<Integer, Long> hospital = new HashMap<>();

        public Owner owner(UUID u) {
            return owners.computeIfAbsent(u, k -> new Owner());
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            NbtCompound os = new NbtCompound();
            owners.forEach((u, o) -> {
                NbtCompound n = new NbtCompound();
                NbtCompound hs = new NbtCompound();
                o.homes.forEach((id, h) -> {
                    NbtCompound x = new NbtCompound();
                    x.putBoolean("rent", h.rent);
                    x.putLong("paid", h.paidDay);
                    x.putInt("unpaid", h.unpaid);
                    hs.put(id, x);
                });
                n.put("homes", hs);
                n.putInt("safe", o.safe);
                NbtList cl = new NbtList();
                cl.addAll(o.cars);
                n.put("cars", cl);
                n.putInt("nextCar", o.nextCar);
                if (o.heli != null) n.putUuid("heli", o.heli);
                n.putInt("tips", o.tips);
                os.put(u.toString(), n);
            });
            nbt.put("owners", os);
            NbtCompound tk = new NbtCompound();
            taken.forEach((id, u) -> tk.putUuid(id, u));
            nbt.put("taken", tk);
            NbtCompound mv = new NbtCompound();
            moved.forEach((i, h) -> mv.putString(String.valueOf(i), h));
            nbt.put("moved", mv);
            NbtCompound hp = new NbtCompound();
            hospital.forEach((i, d) -> hp.putLong(String.valueOf(i), d));
            nbt.put("hospital", hp);
            return nbt;
        }

        static State read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            State s = new State();
            NbtCompound os = nbt.getCompound("owners");
            for (String k : os.getKeys()) {
                UUID u;
                try {
                    u = UUID.fromString(k);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                NbtCompound n = os.getCompound(k);
                Owner o = s.owner(u);
                NbtCompound hs = n.getCompound("homes");
                for (String id : hs.getKeys()) {
                    NbtCompound x = hs.getCompound(id);
                    Holding h = new Holding();
                    h.rent = x.getBoolean("rent");
                    h.paidDay = x.getLong("paid");
                    h.unpaid = x.getInt("unpaid");
                    o.homes.put(id, h);
                }
                o.safe = n.getInt("safe");
                for (NbtElement e : n.getList("cars", NbtElement.COMPOUND_TYPE)) o.cars.add((NbtCompound) e);
                o.nextCar = Math.max(1, n.getInt("nextCar"));
                if (n.containsUuid("heli")) o.heli = n.getUuid("heli");
                o.tips = n.getInt("tips");
            }
            NbtCompound tk = nbt.getCompound("taken");
            for (String id : tk.getKeys()) if (tk.containsUuid(id)) s.taken.put(id, tk.getUuid(id));
            NbtCompound mv = nbt.getCompound("moved");
            for (String k : mv.getKeys()) s.moved.put(Integer.parseInt(k), mv.getString(k));
            NbtCompound hp = nbt.getCompound("hospital");
            for (String k : hp.getKeys()) s.hospital.put(Integer.parseInt(k), hp.getLong(k));
            return s;
        }
    }

    public static State state(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(State.TYPE, "novograd_housing");
    }

    // ------------------------------------------------------------------ сеть
    public record S2C(NbtCompound data) implements CustomPayload {
        public static final Id<S2C> ID = new Id<>(Novograd.id("home_s2c"));
        public static final PacketCodec<RegistryByteBuf, S2C> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, S2C::data, S2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S(NbtCompound data) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(Novograd.id("home_c2s"));
        public static final PacketCodec<RegistryByteBuf, C2S> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, C2S::data, C2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void send(ServerPlayerEntity p, String type, NbtCompound n) {
        n.putString("type", type);
        if (ServerPlayNetworking.canSend(p, S2C.ID)) ServerPlayNetworking.send(p, new S2C(n));
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(S2C.ID, S2C.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S.ID, C2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(C2S.ID, (payload, ctx) -> {
            try {
                handle(ctx.player(), payload.data());
            } catch (RuntimeException e) {
                Novograd.LOG.warn("Недвижимость: ошибка", e);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Housing::tick);
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> {
            build(server.getOverworld());
            pins(h.player);
        }));
        // после больницы — домой (как сохранение в убежище); задержание — как раньше, участок
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            if (!alive) newP.getServer().execute(() -> wakeAtHome(newP));
        });
        Garage.init();
        Residents.init();
        LifeAi.init(); // мозг прохожих: нужды, характер, реакции, разговоры
        Leisure.init(); // пляжи, ларьки с очередью, собачники, стройки, порт, патруль, клуб
        com.yompix.novograd.gta.StreetPerformers.init();
    }

    private static void handle(ServerPlayerEntity p, NbtCompound d) {
        build(p.getServerWorld());
        String id = d.getString("id");
        switch (d.getString("type")) {
            case "list" -> {}
            case "buy" -> buy(p, id, false);
            case "rent" -> buy(p, id, true);
            case "leave" -> leave(p, id);
            case "safe_in" -> safe(p, true, d.getInt("amount"));
            case "safe_out" -> safe(p, false, d.getInt("amount"));
            case "sleep" -> sleep(p);
            case "wardrobe" -> {
                if (here(p) == null) p.sendMessage(Text.literal("Гардероб — дома. Приезжай к себе").formatted(Formatting.YELLOW), true);
                else com.yompix.novograd.life.Style.open(p, "wardrobe");
                return;
            }
            case "car_out" -> Garage.takeOut(p, d.getInt("car"));
            case "car_release" -> Garage.releaseFromApp(p, d.getInt("car"));
            default -> {}
        }
        sendList(p);
    }

    // ------------------------------------------------------------------ владение
    public static boolean owns(ServerPlayerEntity p, Listing l) {
        if (l.flag != null && Life.get(p).flags.contains(l.flag)) return true;
        return state(p.getServer()).owner(p.getUuid()).homes.containsKey(l.id);
    }

    /** Всё жильё игрока (купленное и снятое, вместе со старыми убежищами). */
    public static List<Listing> homes(ServerPlayerEntity p) {
        build(p.getServerWorld());
        List<Listing> out = new ArrayList<>();
        for (Listing l : LISTINGS) if (owns(p, l)) out.add(l);
        return out;
    }

    @Nullable
    private static Listing atHome(ServerPlayerEntity p, Listing l) {
        Vec3d at = l.inside;
        double dx = p.getX() - at.x, dz = p.getZ() - at.z, dy = p.getY() - at.y;
        double r = l.radius();
        boolean roof = l.heli != null && Math.abs(p.getY() - l.heli.y) < 3 && dx * dx + dz * dz < 16 * 16;
        return (dx * dx + dz * dz <= r * r && dy > -1.5 && dy < 3) || roof ? l : null;
    }

    /** Своё жильё, в котором игрок сейчас стоит (или null). */
    @Nullable
    public static Listing here(ServerPlayerEntity p) {
        for (Listing l : homes(p)) if (atHome(p, l) != null) return l;
        return null;
    }

    /** Для гардероба (life/Style через Activities.nearOwnedSafehouse): игрок у себя дома. */
    public static boolean atAnyHome(ServerPlayerEntity p) {
        return here(p) != null;
    }

    private static long day(ServerWorld w) {
        return w.getTimeOfDay() / 24000L + 1;
    }

    private static void buy(ServerPlayerEntity p, String id, boolean rent) {
        Listing l = BY_ID.get(id);
        if (l == null) return;
        LifeData d = Life.get(p);
        State st = state(p.getServer());
        if (owns(p, l)) {
            p.sendMessage(Text.literal("Это уже твоё жильё").formatted(Formatting.YELLOW), true);
            return;
        }
        UUID other = st.taken.get(l.id);
        if (other != null && !other.equals(p.getUuid()) && l.prop < 0) {
            p.sendMessage(Text.literal("Здесь уже живёт другой игрок").formatted(Formatting.RED), true);
            return;
        }
        if (Economy.act(d) < l.act) {
            p.sendMessage(Text.literal("Риелтор: «Этот объект покажем позже — сначала заработай имя в городе» (сюжет, акт " + (l.act + 1) + ")").formatted(Formatting.YELLOW), false);
            return;
        }
        if (rent ? !l.rentable() : !l.buyable()) return;
        int cost = rent ? l.rent : l.price;
        if (!Life.pay(p, cost, rent ? "аренда: " + l.name + " (первые сутки)" : "покупка: " + l.name)) return;
        ServerWorld w = p.getServerWorld();
        if (l.flag != null && !rent) d.flags.add(l.flag);
        else {
            Holding h = new Holding();
            h.rent = rent;
            h.paidDay = day(w);
            st.owner(p.getUuid()).homes.put(l.id, h);
        }
        if (l.prop < 0) st.taken.put(l.id, p.getUuid());
        st.markDirty();
        p.setSpawnPoint(w.getRegistryKey(), BlockPos.ofFloored(l.inside), 0, true, false);
        String tenant = Residents.moveOut(w, l.id);
        Activities.title(p, rent ? "ЖИЛЬЁ СНЯТО" : "НЕДВИЖИМОСТЬ КУПЛЕНА", l.name + (rent ? " · $" + l.rent + " в день" : " · −$" + l.price));
        Life.message(p, "Риелтор", (rent ? "Ключи ваши! Аренда $" + l.rent + " в сутки спишется с карты каждое утро. " : "Поздравляем с покупкой! ")
                + (tenant != null ? "Прежний жилец, " + tenant + ", уже съезжает. " : "")
                + "Дома: сейф, гардероб и сон, гараж на " + l.cars + (l.heli != null ? ", на крыше ждёт вертолёт." : "."));
        w.playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1f);
        Achievements.give(p, "house");
        Life.sync(p);
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) pins(o);
    }

    /** Съехать со съёмной квартиры или продать купленное за полцены. */
    private static void leave(ServerPlayerEntity p, String id) {
        Listing l = BY_ID.get(id);
        if (l == null || !owns(p, l)) return;
        State st = state(p.getServer());
        Holding h = st.owner(p.getUuid()).homes.get(id);
        boolean rented = h != null && h.rent;
        int back = rented ? 0 : l.price / 2;
        if (l.flag != null) Life.get(p).flags.remove(l.flag);
        st.owner(p.getUuid()).homes.remove(id);
        if (p.getUuid().equals(st.taken.get(id))) st.taken.remove(id);
        Garage.evict(p.getServer(), p.getUuid(), id, "свой переезд");
        st.markDirty();
        if (back > 0) Life.earn(p, back, "продажа: " + l.name);
        Life.message(p, "Риелтор", rented ? "Вы съехали: " + l.name + ". Машины из гаража — на штрафстоянке (телефон → «Дом»)."
                : "Продано: " + l.name + " за $" + back + ". Машины из гаража — на штрафстоянке.");
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) pins(o);
    }

    private static void evict(MinecraftServer server, UUID who, Listing l) {
        State st = state(server);
        st.owner(who).homes.remove(l.id);
        if (who.equals(st.taken.get(l.id))) st.taken.remove(l.id);
        Garage.evict(server, who, l.id, "выселение");
        st.markDirty();
    }

    // ------------------------------------------------------------------ сейф, сон, гардероб
    private static void safe(ServerPlayerEntity p, boolean in, int amount) {
        if (here(p) == null) {
            p.sendMessage(Text.literal("Сейф — дома. Приезжай к себе").formatted(Formatting.YELLOW), true);
            return;
        }
        LifeData d = Life.get(p);
        State st = state(p.getServer());
        Owner o = st.owner(p.getUuid());
        int have = in ? d.dirty : o.safe;
        int take = amount <= 0 ? have : Math.min(amount, have);
        if (take <= 0) return;
        if (in) {
            d.dirty -= take;
            o.safe += take;
        } else {
            o.safe -= take;
            d.dirty += take;
        }
        st.markDirty();
        p.playSoundToPlayer(SoundEvents.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 0.6f, 1.4f);
        p.sendMessage(Text.literal(in ? "В сейф: $" + take + " грязными (в сейфе $" + o.safe + ") — не пропадут при гибели и аресте"
                : "Из сейфа: $" + take + " (осталось $" + o.safe + ")").formatted(Formatting.GOLD), true);
        Life.sync(p);
    }

    /** Грязные деньги в сейфе игрока (для финансов и автотестов). */
    public static int safeCash(ServerPlayerEntity p) {
        return state(p.getServer()).owner(p.getUuid()).safe;
    }

    private static void sleep(ServerPlayerEntity p) {
        Listing l = here(p);
        if (l == null) {
            p.sendMessage(Text.literal("Спать — дома. Приезжай к себе").formatted(Formatting.YELLOW), true);
            return;
        }
        if (Wanted.stars(p) > 0) {
            p.sendMessage(Text.literal("Не уснуть, пока тебя ищет полиция").formatted(Formatting.RED), true);
            return;
        }
        ServerWorld w = p.getServerWorld();
        p.setHealth(p.getMaxHealth());
        p.setSpawnPoint(w.getRegistryKey(), BlockPos.ofFloored(l.inside), 0, true, false);
        if (p.getServer().getPlayerManager().getPlayerList().size() > 1) {
            Activities.title(p, "ОТДОХНУЛ", "Друзья в городе не спят — время не перемотать");
            return;
        }
        long t = w.getTimeOfDay(), tod = t % 24000;
        // ночью и вечером — до 7 утра, днём — на 6 часов вперёд
        long next = tod >= 12000 || tod < 1000 ? (t / 24000 + (tod >= 1000 ? 1 : 0)) * 24000 + 1000 : t + 6000;
        NbtCompound f = new NbtCompound();
        f.putString("type", "fade");
        f.putInt("ticks", 40);
        long h = (next % 24000 / 1000 + 6) % 24;
        f.putString("label", "Сон… " + String.format("%02d:00", h));
        ServerPlayNetworking.send(p, new Elevators.S2C(f));
        p.getServer().execute(() -> {
            w.setTimeOfDay(next);
            Activities.title(p, "ДОБРОЕ УТРО", l.name + " · " + String.format("%02d:00", h) + " · здоровье восстановлено");
        });
    }

    private static void wakeAtHome(ServerPlayerEntity p) {
        if (p.isDead()) return;
        Listing best = null;
        double bd = Double.MAX_VALUE;
        for (Listing l : homes(p)) {
            if (l.kind == Kind.SAFEHOUSE) continue; // убежища как раньше — точка возрождения игры
            double dd = l.inside.squaredDistanceTo(p.getPos());
            if (dd < bd) {
                bd = dd;
                best = l;
            }
        }
        if (best == null) return;
        ServerWorld w = p.getServerWorld();
        w.getChunk(BlockPos.ofFloored(best.inside));
        p.teleport(w, best.inside.x, best.inside.y, best.inside.z, p.getYaw(), 0);
        p.sendMessage(Text.literal("Тебя выписали из больницы — отлежался дома: " + best.name).formatted(Formatting.GREEN), false);
    }

    // ------------------------------------------------------------------ тик
    private static final Map<UUID, String> INSIDE = new HashMap<>();
    private static final Map<UUID, Long> RENT_DAY = new HashMap<>();

    private static void tick(MinecraftServer server) {
        long t = server.getTicks();
        if (t % 20 != 3) return;
        ServerWorld w = server.getOverworld();
        build(w);
        long day = day(w);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.getServerWorld() != w) continue;
            if (!Long.valueOf(day).equals(RENT_DAY.get(p.getUuid()))) {
                RENT_DAY.put(p.getUuid(), day);
                rent(p, day);
            }
            Listing h = here(p);
            String was = INSIDE.put(p.getUuid(), h == null ? "" : h.id);
            if (h != null && !h.id.equals(was)) {
                p.setSpawnPoint(w.getRegistryKey(), BlockPos.ofFloored(h.inside), 0, true, false);
                p.sendMessage(Text.literal(h.name + " · телефон → «Дом»: сейф, гардероб, сон, гараж").formatted(Formatting.GREEN), true);
            }
            signs(p);
            if (t % 100 == 3) heli(p);
        }
        if (t % 200 == 3) cleanOldPentSign(w);
    }

    /** Посуточная аренда: утром списывается с карты. Три дня долга — выселение, машины — на штрафстоянку. */
    private static void rent(ServerPlayerEntity p, long day) {
        State st = state(p.getServer());
        Owner o = st.owner(p.getUuid());
        LifeData d = Life.get(p);
        for (var e : new ArrayList<>(o.homes.entrySet())) {
            Holding h = e.getValue();
            Listing l = BY_ID.get(e.getKey());
            if (!h.rent || l == null) continue;
            int guard = 0;
            while (h.paidDay < day && guard++ < 5) {
                h.paidDay++;
                if (d.money >= l.rent) {
                    d.money -= l.rent;
                    d.spent += l.rent;
                    h.unpaid = 0;
                    p.sendMessage(Text.literal("Аренда «" + l.name + "»: −$" + l.rent).formatted(Formatting.GOLD), false);
                } else if (++h.unpaid >= 3) {
                    evict(p.getServer(), p.getUuid(), l);
                    Life.message(p, "Арендодатель", "Три дня без оплаты — вы выселены из «" + l.name + "». Машины из гаража на штрафстоянке.");
                    break;
                } else {
                    Life.message(p, "Арендодатель", "Не хватает денег на аренду «" + l.name + "» ($" + l.rent + "). Долг: " + h.unpaid
                            + " дн. На третий день — выселение!");
                }
            }
            if (h.paidDay < day) h.paidDay = day;
        }
        st.markDirty();
        Life.sync(p);
    }

    /** Таблички «Продаётся / Сдаётся» у дверей рядом с игроком. */
    private static void signs(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        State st = state(p.getServer());
        for (int i = 0; i < LISTINGS.size(); i++) {
            Listing l = LISTINGS.get(i);
            if (l.kind == Kind.SAFEHOUSE || l.id.startsWith("C:")) continue; // у убежищ своя дверь (gta/Activities)
            Vec3d s = l.sign;
            if (Math.abs(s.y - p.getY()) > 5 || s.squaredDistanceTo(p.getPos()) > 22 * 22) continue;
            if (!w.isChunkLoaded(BlockPos.ofFloored(s))) continue;
            String tag = "realty_" + i;
            List<ItemEntity> have = w.getEntitiesByClass(ItemEntity.class, new Box(s, s).expand(2), e -> e.getCommandTags().contains(tag));
            ItemEntity it;
            if (have.isEmpty()) {
                it = new ItemEntity(w, s.x, s.y, s.z, new ItemStack(Items.OAK_SIGN));
                it.setVelocity(0, 0, 0);
                it.setNoGravity(true);
                it.setPickupDelayInfinite();
                it.setNeverDespawn();
                it.addCommandTag(tag);
                it.setCustomNameVisible(true);
                w.spawnEntity(it);
            } else {
                it = have.get(0);
                for (int k = 1; k < have.size(); k++) have.get(k).discard();
            }
            UUID who = st.taken.get(l.id);
            String name;
            Formatting col;
            if (owns(p, l)) {
                name = l.name + " — твой дом";
                col = Formatting.GREEN;
            } else if (who != null) {
                ServerPlayerEntity o = p.getServer().getPlayerManager().getPlayer(who);
                name = l.name + " — живёт " + (o != null ? o.getName().getString() : "другой игрок");
                col = Formatting.GRAY;
            } else {
                name = "" + (l.buyable() ? "Продаётся $" + l.price : "") + (l.buyable() && l.rentable() ? " · " : "")
                        + (l.rentable() ? "Сдаётся $" + l.rent + "/сутки" : "") + " — телефон → «Дом»";
                col = Formatting.GOLD;
            }
            Text txt = Text.literal(name).formatted(col);
            if (it.getCustomName() == null || !it.getCustomName().getString().equals(name)) it.setCustomName(txt);
        }
    }

    /** Пентхаус: у хозяина на крыше всегда стоит вертолёт. */
    private static void heli(ServerPlayerEntity p) {
        for (Listing l : homes(p)) {
            if (l.heli == null || l.heli.squaredDistanceTo(p.getPos()) > 96 * 96) continue;
            ServerWorld w = p.getServerWorld();
            if (!w.isChunkLoaded(BlockPos.ofFloored(l.heli))) continue;
            State st = state(p.getServer());
            Owner o = st.owner(p.getUuid());
            String tag = "nvg_heli_" + p.getUuidAsString();
            var helis = w.getEntitiesByClass(com.yompix.novograd.vehicle.HeliEntity.class, new Box(-Island.HALF, -64, -Island.HALF, Island.HALF, 320, Island.HALF),
                    e -> e.getCommandTags().contains(tag));
            for (var e : helis) if (o.heli != null && !o.heli.equals(e.getUuid())) e.discard(); // старая копия из выгруженного чанка
            boolean have = helis.stream().anyMatch(e -> !e.isRemoved());
            if (o.heli != null && !have && w.getEntity(o.heli) != null) have = true;
            if (have) continue;
            var heli = new com.yompix.novograd.vehicle.HeliEntity(com.yompix.novograd.vehicle.Vehicles.HELI, w);
            heli.refreshPositionAndAngles(l.heli.x, l.heli.y, l.heli.z, 180, 0);
            heli.addCommandTag(tag);
            w.spawnEntity(heli);
            o.heli = heli.getUuid();
            st.markDirty();
        }
    }

    /** Убежище «Пентхаус в центре» переехало к настоящей башне — старая табличка на тротуаре больше не нужна. */
    private static void cleanOldPentSign(ServerWorld w) {
        BlockPos old = new BlockPos(461, Island.surface(461, -400) + 1, -400);
        if (!w.isChunkLoaded(old)) return;
        for (ItemEntity e : w.getEntitiesByClass(ItemEntity.class, new Box(old).expand(4, 6, 4), e -> e.getCommandTags().contains("prop_3"))) e.discard();
    }

    // ------------------------------------------------------------------ каталог для телефона и значки на карте
    public static void sendList(ServerPlayerEntity p) {
        build(p.getServerWorld());
        State st = state(p.getServer());
        Owner o = st.owner(p.getUuid());
        LifeData d = Life.get(p);
        NbtList list = new NbtList();
        Listing at = here(p);
        for (Listing l : LISTINGS) {
            NbtCompound n = new NbtCompound();
            n.putString("id", l.id);
            n.putString("name", l.name);
            n.putString("area", l.area);
            n.putString("kind", l.kind.title);
            n.putInt("price", l.price);
            n.putInt("rent", l.rent);
            n.putInt("cars", l.cars);
            n.putBoolean("heli", l.heli != null);
            n.putInt("x", (int) Math.floor(l.inside.x));
            n.putInt("z", (int) Math.floor(l.inside.z));
            boolean mine = owns(p, l);
            UUID who = st.taken.get(l.id);
            n.putString("state", mine ? (o.homes.containsKey(l.id) && o.homes.get(l.id).rent ? "rent" : "own")
                    : who != null && l.prop < 0 ? "taken" : Economy.act(d) < l.act ? "locked" : "free");
            n.putInt("act", l.act + 1);
            if (mine) n.putInt("parked", Garage.parked(o, l.id));
            list.add(n);
        }
        NbtCompound n = new NbtCompound();
        n.put("list", list);
        n.putInt("money", d.money);
        n.putInt("dirty", d.dirty);
        n.putInt("safe", o.safe);
        n.putString("here", at == null ? "" : at.id);
        n.put("cars", Garage.carsNbt(o));
        send(p, "list", n);
    }

    /** Значки на радаре и карте: свободное жильё и своё. */
    public static void pins(ServerPlayerEntity p) {
        build(p.getServerWorld());
        State st = state(p.getServer());
        NbtList list = new NbtList();
        for (int i = 0; i < LISTINGS.size(); i++) {
            Listing l = LISTINGS.get(i);
            boolean mine = owns(p, l);
            if (!mine && (l.kind == Kind.SAFEHOUSE || l.id.startsWith("C:"))) continue; // убежища и так видны на улице
            if (!mine && st.taken.containsKey(l.id)) continue;
            NbtCompound n = new NbtCompound();
            n.putString("id", (mine ? "realty_own_" : "realty_") + i);
            n.putString("name", (mine ? "Мой дом: " : l.kind.title + ": ") + l.name);
            n.putInt("x", (int) Math.floor(l.inside.x));
            n.putInt("z", (int) Math.floor(l.inside.z));
            list.add(n);
            if (mine && l.cars > 0) {
                NbtCompound g = new NbtCompound();
                g.putString("id", "realty_garage_" + i);
                g.putString("name", "Гараж: " + l.name);
                g.putInt("x", (int) Math.floor(l.garage.x));
                g.putInt("z", (int) Math.floor(l.garage.z));
                list.add(g);
            }
        }
        NbtCompound n = new NbtCompound();
        n.put("pins", list);
        send(p, "pins", n);
    }
}
