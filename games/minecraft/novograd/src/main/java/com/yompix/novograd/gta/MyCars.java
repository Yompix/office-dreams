package com.yompix.novograd.gta;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.6: «Мои машины». Каждая личная машина на улице записана за хозяином (по UUID, в мире): где стояла последний раз,
 * копия машины (NBT) и «поколение». Хозяин видит свои машины на радаре и карте всегда — даже если район не загружен,
 * после тюрьмы и смерти; на штрафстоянке/в гараже — из world/Garage. Телефон → «Авто» → «Мои машины»: метка,
 * «подогнать» (за деньги, через 20 с машину пригоняют к тебе), страховка, «передать права» другому игроку (с ценой:
 * деньги и ключи меняются разом, после подтверждения покупателя; в журнал сервера).
 *
 * <p>Подогнали машину, которая стояла в незагруженном районе: создаётся копия под новым UUID; старый UUID записан
 * в «списанные», и старая копия, когда её район загрузится, исчезает (двойников нет).
 */
public final class MyCars {
    private MyCars() {}

    public static final int CALL_BASE = 80, INSURE_DAY = 40, DELIVERY_TICKS = 20 * 20;

    // ------------------------------------------------------------------ записи
    public static final class State extends PersistentState {
        /** хозяин → машина → запись (name, x, y, z, gen, insured, data). */
        public final Map<UUID, Map<UUID, NbtCompound>> cars = new HashMap<>();
        /** Старые копии после «подогнать»: когда загрузятся — исчезнут. */
        public final java.util.Set<UUID> retired = new java.util.HashSet<>();

        Map<UUID, NbtCompound> of(UUID owner) {
            return cars.computeIfAbsent(owner, k -> new LinkedHashMap<>());
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
            NbtList l = new NbtList();
            for (var e : cars.entrySet())
                for (var c : e.getValue().entrySet()) {
                    NbtCompound n = c.getValue().copy();
                    n.putUuid("owner", e.getKey());
                    n.putUuid("car", c.getKey());
                    l.add(n);
                }
            nbt.put("cars", l);
            NbtList r = new NbtList();
            for (UUID u : retired) r.add(net.minecraft.nbt.NbtHelper.fromUuid(u));
            nbt.put("retired", r);
            return nbt;
        }

        static State read(NbtCompound nbt, RegistryWrapper.WrapperLookup reg) {
            State s = new State();
            for (var e : nbt.getList("cars", 10)) {
                NbtCompound n = (NbtCompound) e;
                if (!n.containsUuid("owner") || !n.containsUuid("car")) continue;
                UUID o = n.getUuid("owner"), c = n.getUuid("car");
                n.remove("owner");
                n.remove("car");
                s.of(o).put(c, n);
            }
            for (var e : nbt.getList("retired", 11)) s.retired.add(net.minecraft.nbt.NbtHelper.toUuid(e));
            return s;
        }
    }

    private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::read, null);

    public static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_mycars");
    }

    /** Отложенные «подогнать»: игрок → (машина, тик готовности). */
    private record Call(UUID car, long at) {}

    private static final Map<UUID, Call> CALLS = new HashMap<>();
    /** Предложения передачи: покупатель → предложение. */
    public record Offer(UUID seller, UUID car, int price, long at, String carName) {}

    private static final Map<UUID, Offer> OFFERS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(MyCars::tick);
    }

    // ------------------------------------------------------------------ тик
    private static void tick(MinecraftServer s) {
        int t = s.getTicks();
        if (t % 40 == 17) {
            State st = state(s);
            boolean snap = t % 400 == 17;
            for (ServerWorld w : s.getWorlds())
                for (CarEntity car : w.getEntitiesByType(TypeFilter.instanceOf(CarEntity.class), c -> c.getOwner() != null && c.isAlive())) {
                    if (st.retired.remove(car.getUuid())) {
                        car.discard(); // старая копия после «подогнать»
                        st.markDirty();
                        continue;
                    }
                    Map<UUID, NbtCompound> mine = st.of(car.getOwner());
                    NbtCompound rec = mine.get(car.getUuid());
                    if (rec == null) {
                        // новая личная машина (купил, из гаража, угнал и поставил в гараж)
                        rec = new NbtCompound();
                        mine.put(car.getUuid(), rec);
                        snap = true;
                    }
                    rec.putString("name", Impound.name(car.getVariant()));
                    rec.putString("dim", w.getRegistryKey().getValue().toString());
                    rec.putDouble("x", car.getX());
                    rec.putDouble("y", car.getY());
                    rec.putDouble("z", car.getZ());
                    rec.putBoolean("wrecked", car.wrecked());
                    if (snap || !rec.contains("data")) rec.put("data", data(car));
                    st.markDirty();
                }
        }
        if (t % 100 == 23) for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) send(p);
        // доставка
        if (!CALLS.isEmpty()) {
            long now = s.getOverworld().getTime();
            for (var it = CALLS.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                if (now < e.getValue().at()) continue;
                it.remove();
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(e.getKey());
                if (p != null) deliver(p, e.getValue().car());
            }
        }
        if (t % 200 == 5) OFFERS.values().removeIf(o -> s.getOverworld().getTime() - o.at() > 20 * 60);
    }

    private static NbtCompound data(CarEntity car) {
        NbtCompound d = car.writeNbt(new NbtCompound());
        for (String k : new String[]{"UUID", "Pos", "Motion", "Passengers", "Rotation", "FallDistance", "Fire", "OnGround"}) d.remove(k);
        d.putString("nv_type", Registries.ENTITY_TYPE.getId(car.getType()).toString());
        return d;
    }

    /** Список машин игроку: на улице (отсюда) + в гаражах и на стоянках (world/Garage). */
    public static void send(ServerPlayerEntity p) {
        NbtList l = new NbtList();
        for (var e : state(p.getServer()).of(p.getUuid()).entrySet()) {
            NbtCompound r = e.getValue(), n = new NbtCompound();
            n.putUuid("car", e.getKey());
            n.putString("name", r.getString("name"));
            n.putDouble("x", r.getDouble("x"));
            n.putDouble("z", r.getDouble("z"));
            n.putString("where", r.getBoolean("wrecked") ? "wrecked" : "street");
            n.putBoolean("insured", r.getBoolean("insured"));
            l.add(n);
        }
        for (NbtCompound c : com.yompix.novograd.world.Garage.cars(p.getServer(), p.getUuid())) {
            NbtCompound n = new NbtCompound();
            n.putInt("rec", c.getInt("id"));
            n.putString("name", c.getString("name"));
            String where = c.getString("where");
            n.putString("where", where.startsWith("impound:") ? "impound" : "garage");
            if (where.startsWith("impound:")) {
                Vec3d lot = com.yompix.novograd.story.Story.place(p.getServerWorld(), Impound.LOT);
                if (lot != null) {
                    n.putDouble("x", lot.x);
                    n.putDouble("z", lot.z);
                }
                n.putInt("fee", Impound.FEE);
            }
            l.add(n);
        }
        NbtCompound n = new NbtCompound();
        n.put("list", l);
        n.putInt("callBase", CALL_BASE);
        n.putInt("insure", INSURE_DAY);
        LifeNet.send(p, "mycars", n);
    }

    // ------------------------------------------------------------------ действия из телефона
    public static void handle(ServerPlayerEntity p, String act, NbtCompound d) {
        MinecraftServer s = p.getServer();
        State st = state(s);
        switch (act) {
            case "call" -> {
                UUID car = d.getUuid("car");
                NbtCompound rec = st.of(p.getUuid()).get(car);
                if (rec == null || rec.getBoolean("wrecked") || CALLS.containsKey(p.getUuid())) return;
                if (com.yompix.novograd.gta.Wanted.stars(p) > 0) {
                    Life.message(p, "Парковка", "С розыском машину не подгоняют.");
                    return;
                }
                int price = callPrice(p, rec);
                if (!Life.pay(p, price, "подогнать машину")) return;
                CALLS.put(p.getUuid(), new Call(car, p.getServerWorld().getTime() + DELIVERY_TICKS));
            }
            case "insure" -> {
                NbtCompound rec = st.of(p.getUuid()).get(d.getUuid("car"));
                if (rec == null) return;
                rec.putBoolean("insured", !rec.getBoolean("insured"));
                st.markDirty();
            }
            case "give" -> offer(p, d.getUuid("car"), d.getString("to"), Math.max(0, Math.min(10_000_000, d.getInt("price"))));
            case "accept" -> accept(p, true);
            case "decline" -> accept(p, false);
            default -> {}
        }
        send(p);
    }

    public static int callPrice(ServerPlayerEntity p, NbtCompound rec) {
        double dist = Math.hypot(rec.getDouble("x") - p.getX(), rec.getDouble("z") - p.getZ());
        return Economy.price(CALL_BASE + (int) Math.min(400, dist / 10));
    }

    @Nullable
    private static CarEntity loaded(MinecraftServer s, UUID car) {
        for (ServerWorld w : s.getWorlds()) if (w.getEntity(car) instanceof CarEntity c && c.isAlive()) return c;
        return null;
    }

    /** Машину пригоняют на дорогу рядом с игроком. */
    private static void deliver(ServerPlayerEntity p, UUID carId) {
        State st = state(p.getServer());
        Map<UUID, NbtCompound> mine = st.of(p.getUuid());
        NbtCompound rec = mine.get(carId);
        if (rec == null) return;
        ServerWorld w = p.getServerWorld();
        Vec3d dir = Vec3d.fromPolar(0, p.getYaw()).multiply(-5);
        Vec3d at = p.getPos().add(dir.x, 0, dir.z);
        BlockPos g = com.yompix.novograd.story.Story.ground(w, at);
        CarEntity car = loaded(p.getServer(), carId);
        if (car != null) {
            car.removeAllPassengers();
            if (car.getWorld() != w) return;
            car.refreshPositionAndAngles(at.x, g.getY(), at.z, p.getYaw(), 0);
            car.setVelocity(Vec3d.ZERO);
        } else {
            NbtCompound data = rec.getCompound("data");
            EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.tryParse(data.getString("nv_type")));
            Entity e = type == null ? null : type.create(w);
            if (!(e instanceof CarEntity c)) return;
            c.readNbt(data);
            c.setUuid(UUID.randomUUID());
            c.setOwner(p.getUuid());
            c.setAmbient(false);
            c.refreshPositionAndAngles(at.x, g.getY(), at.z, p.getYaw(), 0);
            w.spawnEntity(c);
            // старая запись — устаревшая копия: если её район загрузится, она исчезнет
            st.retired.add(carId);
            mine.remove(carId);
            NbtCompound fresh = rec.copy();
            fresh.put("data", data(c));
            mine.put(c.getUuid(), fresh);
            car = c;
        }
        st.markDirty();
        Life.message(p, "Парковка", "Машина подъехала: " + rec.getString("name") + ".");
        Novograd.LOG.info("[мои машины] {} подогнал {}", p.getName().getString(), car.getUuid());
    }

    // ------------------------------------------------------------------ передача прав
    private static void offer(ServerPlayerEntity seller, UUID carId, String to, int price) {
        MinecraftServer s = seller.getServer();
        ServerPlayerEntity buyer = s.getPlayerManager().getPlayer(to);
        if (buyer == null || buyer == seller) {
            Life.message(seller, "Авто", "Игрок не в сети.");
            return;
        }
        CarEntity car = loaded(s, carId);
        NbtCompound rec = state(s).of(seller.getUuid()).get(carId);
        if (car == null || rec == null || !seller.getUuid().equals(car.getOwner())) {
            Life.message(seller, "Авто", "Машина далеко — подгони её, потом передавай.");
            return;
        }
        OFFERS.put(buyer.getUuid(), new Offer(seller.getUuid(), carId, price, s.getOverworld().getTime(), rec.getString("name")));
        NbtCompound n = new NbtCompound();
        n.putString("from", seller.getName().getString());
        n.putString("car", rec.getString("name"));
        n.putInt("price", price);
        LifeNet.send(buyer, "car_offer", n);
        Life.message(seller, "Авто", "Предложение отправлено: " + buyer.getName().getString() + (price > 0 ? ", $" + price : ", бесплатно") + ".");
    }

    /** Покупатель ответил. Деньги и ключи — разом: всё проверяется в одном тике сервера. */
    private static void accept(ServerPlayerEntity buyer, boolean yes) {
        Offer o = OFFERS.remove(buyer.getUuid());
        if (o == null) return;
        MinecraftServer s = buyer.getServer();
        ServerPlayerEntity seller = s.getPlayerManager().getPlayer(o.seller());
        if (!yes) {
            if (seller != null) Life.message(seller, "Авто", buyer.getName().getString() + " отказался.");
            return;
        }
        CarEntity car = loaded(s, o.car());
        State st = state(s);
        NbtCompound rec = st.of(o.seller()).get(o.car());
        if (seller == null || car == null || rec == null || !o.seller().equals(car.getOwner())) {
            Life.message(buyer, "Авто", "Сделка не состоялась.");
            return;
        }
        LifeData bd = Life.get(buyer), sd = Life.get(seller);
        if (bd.money < o.price()) {
            Life.message(buyer, "Авто", "Не хватает денег на карте: $" + o.price() + ".");
            return;
        }
        bd.money -= o.price();
        sd.money += o.price();
        car.removeAllPassengers();
        car.setOwner(buyer.getUuid());
        st.of(o.seller()).remove(o.car());
        st.of(buyer.getUuid()).put(o.car(), rec);
        st.markDirty();
        Life.sync(buyer);
        Life.sync(seller);
        send(buyer);
        send(seller);
        Life.message(buyer, "Авто", "Теперь твоя: " + o.carName() + ".");
        Life.message(seller, "Авто", "Права переданы: " + buyer.getName().getString() + (o.price() > 0 ? ", +$" + o.price() : "") + ".");
        Novograd.LOG.info("[передача] {} → {}: {} {} за ${}", seller.getName().getString(), buyer.getName().getString(), o.carName(), o.car(), o.price());
    }

    /** Ежедневная страховка (Economy.bills): сколько застрахованных машин у игрока. */
    public static int insured(MinecraftServer s, UUID owner) {
        int n = 0;
        for (NbtCompound r : state(s).of(owner).values()) if (r.getBoolean("insured")) n++;
        return n;
    }

    public static boolean isInsured(CarEntity car) {
        if (car.getOwner() == null || !(car.getWorld() instanceof ServerWorld w)) return false;
        NbtCompound r = state(w.getServer()).of(car.getOwner()).get(car.getUuid());
        return r != null && r.getBoolean("insured");
    }
}
