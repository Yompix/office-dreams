package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Дороги и транспорт 0.4 — точка входа: бензин и заправки (Fuel), камеры и штрафы (Fines), эвакуатор и штрафстоянка (Impound),
 * авиарейсы (Airport), яхт-клуб и гонки катеров (BoatClub), тотализатор (RaceBets), аукцион угнанных машин (Auction),
 * редкие машины на природе (багги в Пустоши и горах). Пакеты от клиента — с префиксом «drv_».
 */
public final class DriveHooks {
    private DriveHooks() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DriveHooks::tick);
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> sendPlaces(h.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            BoatClub.onLeave(h.player);
            Auction.onLeave(h.player);
            INSIDE.keySet().removeIf(k -> k.startsWith(h.player.getUuidAsString()));
        });
        // для проверки: /drv fuel 5|fine|tow|fly riv|boat|bets|auction|spawn super|places
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(literal("drv")
                .requires(s -> s.getServer().isSingleplayer() || s.hasPermissionLevel(2))
                .then(argument("what", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    String r = debug(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what"));
                    ctx.getSource().sendFeedback(() -> Text.literal(r), false);
                    return 1;
                }))));
    }

    private static void tick(MinecraftServer s) {
        long t = s.getTicks();
        Airport.tick(s);
        RaceBets.tick(s);
        BoatClub.tick(s);
        Impound.tick(s);
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.isDead() || p.getWorld() != s.getOverworld()) continue;
            try {
                if (t % 5 == 1) Fines.tick(p);
                if (t % 10 == 2) {
                    Fuel.tick(p);
                    Impound.tickLot(p);
                    Airport.tickPlayer(p);
                    BoatClub.tickPlayer(p);
                    RaceBets.tickPlayer(p);
                    Auction.tickPlayer(p);
                }
                if (t % 200 == 77) parked(p);
                if (t % 200 == 131) tips(p);
            } catch (RuntimeException e) {
                com.yompix.novograd.Novograd.LOG.warn("drive tick", e);
            }
        }
    }

    // ------------------------------------------------------------------ пакеты клиента
    /** true — пакет наш. */
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "drv_state" -> {
                NbtCompound n = state(p);
                n.putString("open", d.getString("tab"));
                if (d.getString("tab").equals("auction")) Auction.putLot(p, n);
                if (d.getString("tab").equals("boat")) BoatClub.putBoard(p, n);
                LifeNet.send(p, "drv_state", n);
            }
            case "drv_payfines" -> {
                Fines.payAll(p);
                LifeNet.send(p, "drv_state", state(p));
            }
            case "drv_impound" -> {
                Impound.retrieve(p, d.getInt("i"));
                LifeNet.send(p, "drv_state", state(p));
            }
            case "drv_fly" -> Airport.fly(p, d.getString("to"));
            case "drv_boat" -> BoatClub.action(p, d.getString("what"));
            case "drv_bet" -> {
                RaceBets.bet(p, d.getInt("driver"), d.getInt("amount"));
                NbtCompound n = state(p);
                RaceBets.putRace(n, p);
                LifeNet.send(p, "drv_state", n);
            }
            case "drv_sell" -> Auction.sell(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Всё для экрана «Авто»: деньги, штрафы, штрафстоянка, бензин машины. */
    public static NbtCompound state(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        LifeData d = Life.get(p);
        n.putInt("money", d.money);
        n.putInt("dirty", d.dirty);
        n.put("fines", Fines.list(p).copy());
        n.putInt("finesTotal", Fines.total(p));
        NbtList imp = new NbtList();
        for (var e : Impound.list(p)) {
            NbtCompound c = new NbtCompound();
            c.putString("name", Impound.name(((NbtCompound) e).getInt("v")));
            c.putLong("day", ((NbtCompound) e).getLong("day"));
            imp.add(c);
        }
        n.put("impound", imp);
        n.putInt("impoundFee", Impound.FEE);
        n.putLong("day", Life.day(p));
        if (p.getVehicle() instanceof CarEntity car && car.usesFuel()) n.putInt("fuel", car.fuel() * 100 / CarEntity.FUEL_MAX);
        n.putString("station", Fuel.name(Fuel.nearest(p.getPos())));
        NbtList ports = new NbtList();
        for (String[] pt : Airport.PORTS) {
            NbtCompound c = new NbtCompound();
            c.putString("id", pt[0]);
            c.putString("name", pt[2]);
            c.putInt("price", Integer.parseInt(pt[3]));
            ports.add(c);
        }
        n.put("ports", ports);
        n.putInt("stars", Wanted.stars(p));
        n.putInt("act", Economy.act(d));
        BoatClub.putBoard(p, n);
        n.putString("hot", Impound.name(Auction.hot(p.getServerWorld())));
        return n;
    }

    // ------------------------------------------------------------------ метки «зайди — откроется»
    private static final Map<String, Boolean> INSIDE = new HashMap<>();

    public static void walkIn(ServerPlayerEntity p, String key, Vec3d at, Runnable open) {
        walkIn(p, key, at, 1.7, open);
    }

    /** Вошёл в круг радиуса r у метки — открыть экран (один раз, пока не отойдёшь на 5 блоков). */
    public static void walkIn(ServerPlayerEntity p, String key, Vec3d at, double r, Runnable open) {
        String k = p.getUuidAsString() + key;
        double dx = p.getX() - at.x, dz = p.getZ() - at.z, d = Math.sqrt(dx * dx + dz * dz);
        boolean in = d < r && Math.abs(p.getY() - at.y) < 3.5;
        if (in && !INSIDE.getOrDefault(k, false)) {
            INSIDE.put(k, true);
            if (!Airport.flying(p)) open.run();
        } else if (d > r + 3.5) INSIDE.remove(k);
    }

    /** Не открывать метку, пока игрок не отойдёт (например, сразу после прилёта). */
    public static void arm(ServerPlayerEntity p, String key) {
        INSIDE.put(p.getUuidAsString() + key, true);
    }

    static void give(ServerPlayerEntity p, ItemStack s) {
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    // ------------------------------------------------------------------ значки на карте и радаре
    public static void sendPlaces(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        NbtList l = new NbtList();
        for (int i = 0; i < Fuel.STATIONS.length; i++) l.add(place("drv_fuel" + i, "⛽ " + Fuel.name(i), Fuel.pumps(i)));
        for (int i = 0; i < Fines.CAMS.length; i++) {
            Fines.Cam c = Fines.CAMS[i];
            l.add(place("drv_cam" + i, "◉ Камера " + c.limit() + " км/ч", new Vec3d(c.x(), 0, c.z())));
        }
        for (String[] pt : Airport.PORTS) {
            Vec3d at = Story.place(w, pt[1]);
            if (at != null) l.add(place("drv_air_" + pt[0], "✈ " + pt[2], at));
        }
        add(w, l, "drv_impound", "⚠ Штрафстоянка", Impound.LOT);
        add(w, l, "drv_boatclub", "⚓ Яхт-клуб, гонки катеров", BoatClub.CLUB);
        add(w, l, "drv_bets", "⚑ Тотализатор", RaceBets.DESK);
        add(w, l, "drv_auction", "⚒ Аукцион угнанных машин", Auction.GATE);
        NbtCompound n = new NbtCompound();
        n.put("list", l);
        LifeNet.send(p, "drv_places", n);
    }

    private static void add(ServerWorld w, NbtList l, String id, String name, String where) {
        Vec3d at = Story.place(w, where);
        if (at != null) l.add(place(id, name, at));
    }

    private static NbtCompound place(String id, String name, Vec3d at) {
        NbtCompound c = new NbtCompound();
        c.putString("id", id);
        c.putString("name", name);
        c.putInt("x", (int) Math.floor(at.x));
        c.putInt("z", (int) Math.floor(at.z));
        return c;
    }

    // ------------------------------------------------------------------ редкие машины на природе
    /** В Пустоши и горах у дорог иногда стоит пустой багги (или внедорожник) — бери и катайся по бездорожью. */
    private static void parked(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Island.Zone zn = Island.zone(p.getBlockX(), p.getBlockZ());
        boolean wild = zn == Island.Zone.COUNTRY && (Math.abs(p.getZ()) > 1000 || p.getX() > 900);
        if (!wild) return;
        var box = p.getBoundingBox().expand(140, 40, 140);
        if (w.getEntitiesByClass(CarEntity.class, box, c -> c.getCommandTags().contains("nv_parked")).size() >= 2) return;
        var r = w.getRandom();
        for (int tries = 0; tries < 20; tries++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 45 + r.nextInt(40);
            int x = (int) (p.getX() + Math.cos(a) * dist), z = (int) (p.getZ() + Math.sin(a) * dist);
            if (Island.zone(x, z) != Island.Zone.COUNTRY || Island.road(x, z)) continue;
            boolean nearRoad = false;
            for (int k = -5; k <= 5 && !nearRoad; k += 5) nearRoad = Island.road(x + k, z) || Island.road(x, z + k);
            if (!nearRoad) continue;
            if (!w.isChunkLoaded(new net.minecraft.util.math.BlockPos(x, 0, z))) continue;
            int y = Story.topY(w, x, z);
            if (!w.isAir(new net.minecraft.util.math.BlockPos(x, y, z)) || !w.isAir(new net.minecraft.util.math.BlockPos(x, y + 1, z))) continue;
            int v = r.nextInt(4) == 0 ? (p.getZ() < -1000 ? 7 : 8) : CarEntity.BUGGY;
            CarEntity car = FunUtil.car(w, new Vec3d(x + 0.5, y, z + 0.5), r.nextFloat() * 360, v);
            car.setAmbient(true);
            car.addCommandTag("nv_parked");
            car.setFuel(300 + r.nextInt(600));
            int[] pal = {0xE07A1F, 0xF2C032, 0x7BC639, 0xD7262C, 0x2C6ECB, 0x3E4B3C};
            car.paint(pal[r.nextInt(pal.length)]);
            return;
        }
    }

    private static void tips(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (p.age > 20 * 60 * 9 && d.flags.add("tips_drive04"))
            Life.message(p, "Новоград-гид", "Новое на дорогах: машины жгут бензин (⛽ «Нефтяник» на трассах — заедь к колонкам и встань), "
                    + "на трассах камеры скорости (60 км/ч, штрафы — телефон → «Авто»), брошенные посреди дороги машины увозит эвакуатор. "
                    + "Авиакассы на аэродроме, на пляже Ривьеры и в Пустоши; яхт-клуб на пристани Ривьеры; тотализатор у старта кольца; "
                    + "угнанные машины берут на аукционе в разборке.");
    }

    // ------------------------------------------------------------------ отладка
    public static String debug(ServerPlayerEntity p, String what) {
        String[] a = what.trim().toLowerCase(Locale.ROOT).split("\\s+");
        ServerWorld w = p.getServerWorld();
        switch (a[0]) {
            case "fuel" -> {
                if (!(p.getVehicle() instanceof CarEntity car)) return "Сядь в машину";
                car.setFuel(a.length > 1 ? Integer.parseInt(a[1]) * CarEntity.FUEL_MAX / 100 : 0);
                return "Бензин " + car.fuel();
            }
            case "fine" -> {
                DriveState st = DriveState.get(p.getServer());
                NbtCompound f = new NbtCompound();
                f.putInt("amt", 120);
                f.putLong("day", Life.day(p) - (a.length > 1 ? Integer.parseInt(a[1]) : 0));
                f.putString("where", "тест: 95 при 60");
                DriveState.list(st.of(p), "fines").add(f);
                st.of(p).remove("finesRep");
                st.markDirty();
                return "Штраф добавлен, всего $" + Fines.total(p);
            }
            case "tow" -> {
                CarEntity car = w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(12), c -> !c.hasPassengers()).stream()
                        .min(Comparator.comparingDouble(c -> c.squaredDistanceTo(p))).orElse(null);
                if (car == null) return "Рядом нет пустой машины";
                Impound.tow(w, car);
                return "Эвакуирована";
            }
            case "fly" -> {
                Airport.fly(p, a.length > 1 ? a[1] : "riv");
                return "Рейс";
            }
            case "bets" -> {
                RaceBets.open(p);
                return "Тотализатор";
            }
            case "spawn" -> {
                int v = Arrays.asList(CarEntity.VARIANTS).indexOf(a.length > 1 ? a[1] : "super");
                if (v < 0) return "Нет такой: " + String.join(",", CarEntity.VARIANTS);
                Vec3d at = p.getPos().add(p.getRotationVector().multiply(4, 0, 4));
                FunUtil.car(w, new Vec3d(at.x, p.getY(), at.z), p.getYaw(), v);
                return "Машина " + CarEntity.VARIANTS[v];
            }
            case "places" -> {
                sendPlaces(p);
                return "Значки отправлены";
            }
            case "state" -> {
                return state(p).toString();
            }
            default -> {
                return "fuel N|fine [дней]|tow|fly nov|riv|des|bets|spawn <вариант>|places|state";
            }
        }
    }

}
