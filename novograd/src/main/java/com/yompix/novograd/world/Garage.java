package com.yompix.novograd.world;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Life;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Гаражи при своём жилье (world/Housing): заехал на въезд и остановился — машина встаёт в гараж со всем,
 * что на ней было (кузов, цвет, тюнинг, нитро, повреждения и всё, что запишет в NBT машины любой другой модуль).
 * Забрать — телефон → «Дом» → «Выкатить»: машина появится у въезда. Машины хранятся за игроком (PersistentState).
 * Угнанная машина, поставленная в свой гараж, становится твоей (хозяин — ты, полиция её больше не ищет).
 * <p>
 * API для других модулей (эвакуатор, штрафстоянка, аукцион):
 * {@link #isOwned(CarEntity)}, {@link #ownerOf(CarEntity)}, {@link #impound(CarEntity, String)}, {@link #impounded(MinecraftServer, UUID)},
 * {@link #release(ServerPlayerEntity, int, Vec3d, float)}, {@link #give(ServerPlayerEntity, CarEntity)}, {@link #IMPOUND_RELEASE}.
 */
public final class Garage {
    private Garage() {}

    /** Сколько стоит забрать машину со штрафстоянки через телефон (если у стоянки нет своего обработчика). */
    public static final int RELEASE_FEE = 250;
    /**
     * Обработчик «забрать со штрафстоянки» для чужих стоянок (агент эвакуатора может назначить свой):
     * (игрок, запись машины) → true, если обработано (машину выдали или объяснили, куда ехать). null — выдаём сами за {@link #RELEASE_FEE}.
     */
    @Nullable public static BiFunction<ServerPlayerEntity, NbtCompound, Boolean> IMPOUND_RELEASE;

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Garage::tick);
    }

    // ------------------------------------------------------------------ API
    /** Машина чья-то личная (купленная, из гаража, бывшая угнанная после гаража): у неё есть хозяин-игрок. */
    public static boolean isOwned(CarEntity car) {
        return car.getOwner() != null;
    }

    @Nullable
    public static UUID ownerOf(CarEntity car) {
        return car.getOwner();
    }

    /**
     * Эвакуировать личную машину на штрафстоянку lot: машина исчезает с улицы и записывается хозяину как «на стоянке lot».
     * false — у машины нет хозяина (ничья/уличная), ничего не сделано.
     */
    public static boolean impound(CarEntity car, String lot) {
        UUID owner = car.getOwner();
        if (owner == null || !(car.getWorld() instanceof ServerWorld w)) return false;
        Housing.State st = Housing.state(w.getServer());
        Housing.Owner o = st.owner(owner);
        o.cars.add(snapshot(car, o, "impound:" + lot));
        car.removeAllPassengers();
        car.discard();
        st.markDirty();
        ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(owner);
        if (p != null) p.sendMessage(Text.literal("Твою машину увезли на штрафстоянку («" + lot + "»)").formatted(Formatting.RED), false);
        return true;
    }

    /** Машины игрока на штрафстоянках (копии записей: id, type, name, color, health, where, data). */
    public static List<NbtCompound> impounded(MinecraftServer server, UUID owner) {
        List<NbtCompound> out = new ArrayList<>();
        for (NbtCompound c : Housing.state(server).owner(owner).cars) if (c.getString("where").startsWith("impound:")) out.add(c.copy());
        return out;
    }

    /** Все машины игрока (в гаражах и на стоянках), копии. */
    public static List<NbtCompound> cars(MinecraftServer server, UUID owner) {
        List<NbtCompound> out = new ArrayList<>();
        for (NbtCompound c : Housing.state(server).owner(owner).cars) out.add(c.copy());
        return out;
    }

    /** Выдать машину игрока (по id записи) в точке at — со стоянки или из гаража. null — нет такой. */
    @Nullable
    public static CarEntity release(ServerPlayerEntity p, int carId, Vec3d at, float yaw) {
        Housing.State st = Housing.state(p.getServer());
        Housing.Owner o = st.owner(p.getUuid());
        NbtCompound rec = find(o, carId);
        if (rec == null) return null;
        CarEntity car = spawn(p.getServerWorld(), rec, at, yaw, p.getUuid());
        if (car == null) return null;
        o.cars.remove(rec);
        st.markDirty();
        return car;
    }

    /** Подарить игроку машину (выигрыш на аукционе, награда): ставится в первый свободный гараж, иначе — на стоянку «delivery». */
    public static void give(ServerPlayerEntity p, CarEntity car) {
        Housing.State st = Housing.state(p.getServer());
        Housing.Owner o = st.owner(p.getUuid());
        car.setOwner(p.getUuid());
        String where = "impound:delivery";
        for (Housing.Listing l : Housing.homes(p)) if (parked(o, l.id) < l.cars) {
            where = "home:" + l.id;
            break;
        }
        o.cars.add(snapshot(car, o, where));
        car.discard();
        st.markDirty();
    }

    // ------------------------------------------------------------------ записи
    private static NbtCompound snapshot(CarEntity car, Housing.Owner o, String where) {
        for (Entity e : new ArrayList<>(car.getPassengerList())) e.stopRiding();
        NbtCompound data = car.writeNbt(new NbtCompound());
        for (String k : new String[]{"UUID", "Pos", "Motion", "Passengers", "Rotation", "FallDistance", "Fire", "OnGround"}) data.remove(k);
        data.putBoolean("Ambient", false);
        NbtCompound rec = new NbtCompound();
        rec.putInt("id", o.nextCar++);
        rec.putString("type", Registries.ENTITY_TYPE.getId(car.getType()).toString());
        rec.putString("name", nameKey(car));
        rec.putInt("color", car.color());
        rec.putInt("health", car.health());
        rec.putBoolean("nitro", car.hasNitro());
        rec.putInt("engine", car.engine());
        rec.putInt("armor", car.armor());
        rec.putString("where", where);
        rec.put("data", data);
        return rec;
    }

    private static String nameKey(CarEntity car) {
        Text t = car.getName();
        return t.getContent() instanceof TranslatableTextContent tr ? tr.getKey() : t.getString();
    }

    @Nullable
    private static NbtCompound find(Housing.Owner o, int id) {
        for (NbtCompound c : o.cars) if (c.getInt("id") == id) return c;
        return null;
    }

    @Nullable
    private static CarEntity spawn(ServerWorld w, NbtCompound rec, Vec3d at, float yaw, UUID owner) {
        EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.tryParse(rec.getString("type")));
        Entity e = type == null ? null : type.create(w);
        if (!(e instanceof CarEntity car)) return null;
        car.readNbt(rec.getCompound("data"));
        car.setOwner(owner);
        car.setAmbient(false);
        car.setVelocity(Vec3d.ZERO);
        w.getChunk(BlockPos.ofFloored(at));
        car.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
        w.spawnEntity(car);
        return car;
    }

    /** Сколько машин стоит в гараже этого жилья. */
    static int parked(Housing.Owner o, String listing) {
        int n = 0;
        for (NbtCompound c : o.cars) if (c.getString("where").equals("home:" + listing)) n++;
        return n;
    }

    static NbtList carsNbt(Housing.Owner o) {
        NbtList l = new NbtList();
        for (NbtCompound c : o.cars) {
            NbtCompound n = c.copy();
            n.remove("data");
            l.add(n);
        }
        return l;
    }

    /** Жильё потеряно (выселение, продажа): машины из его гаража — на штрафстоянку «evict». */
    static void evict(MinecraftServer server, UUID who, String listing, String why) {
        Housing.Owner o = Housing.state(server).owner(who);
        for (NbtCompound c : o.cars) if (c.getString("where").equals("home:" + listing)) c.putString("where", "impound:evict");
    }

    // ------------------------------------------------------------------ въезд в гараж
    private static final Map<UUID, Integer> PARKING = new HashMap<>();

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 5 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p) {
                PARKING.remove(p.getUuid());
                continue;
            }
            Housing.Listing at = null;
            for (Housing.Listing l : Housing.homes(p)) {
                if (l.cars <= 0) continue;
                double dx = car.getX() - l.garage.x, dz = car.getZ() - l.garage.z;
                if (dx * dx + dz * dz < 4.5 * 4.5 && Math.abs(car.getY() - l.garage.y) < 4) at = l;
            }
            if (at == null) {
                PARKING.remove(p.getUuid());
                continue;
            }
            ServerWorld w = p.getServerWorld();
            if (server.getTicks() % 10 == 0)
                w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, at.garage.x, at.garage.y + 0.3, at.garage.z, 6, 2, 0.1, 2, 0);
            if (!car.slow()) {
                PARKING.put(p.getUuid(), 0);
                p.sendMessage(Text.literal("Гараж «" + at.name + "» — остановись, чтобы поставить машину").formatted(Formatting.AQUA), true);
                continue;
            }
            int n = PARKING.merge(p.getUuid(), 1, Integer::sum);
            if (n < 6) {
                p.sendMessage(Text.literal("Ставлю в гараж" + ".".repeat(n % 4)).formatted(Formatting.AQUA), true);
                continue;
            }
            PARKING.remove(p.getUuid());
            store(p, car, at);
        }
    }

    private static void store(ServerPlayerEntity p, CarEntity car, Housing.Listing l) {
        Housing.State st = Housing.state(p.getServer());
        Housing.Owner o = st.owner(p.getUuid());
        int used = parked(o, l.id);
        if (used >= l.cars) {
            p.sendMessage(Text.literal("Гараж полон (" + used + "/" + l.cars + "). Выкати машину через телефон → «Дом»").formatted(Formatting.RED), true);
            PARKING.put(p.getUuid(), -200);
            return;
        }
        boolean stolen = car.getOwner() == null || !car.getOwner().equals(p.getUuid());
        if (car.isPolice() && com.yompix.novograd.gta.Wanted.stars(p) > 0) {
            p.sendMessage(Text.literal("Полицейскую машину с хвостом в гараж не спрячешь — сначала оторвись").formatted(Formatting.RED), true);
            PARKING.put(p.getUuid(), -200);
            return;
        }
        car.setOwner(p.getUuid());
        o.cars.add(snapshot(car, o, "home:" + l.id));
        ServerWorld w = p.getServerWorld();
        car.removeAllPassengers();
        car.discard();
        st.markDirty();
        Vec3d s = l.street;
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(s.x), (int) Math.floor(s.z));
        p.teleport(w, s.x, Math.max(y, s.y), s.z, p.getYaw(), p.getPitch());
        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.7f, 0.7f);
        com.yompix.novograd.gta.Activities.title(p, "МАШИНА В ГАРАЖЕ", l.name + " · " + (used + 1) + "/" + l.cars
                + (stolen ? " · теперь она твоя" : ""));
    }

    /** Телефон → «Дом» → «Выкатить»: машина появляется у въезда в гараж (игрок должен быть рядом или дома). */
    static void takeOut(ServerPlayerEntity p, int carId) {
        Housing.Owner o = Housing.state(p.getServer()).owner(p.getUuid());
        NbtCompound rec = find(o, carId);
        if (rec == null || !rec.getString("where").startsWith("home:")) return;
        Housing.Listing l = Housing.get(rec.getString("where").substring(5));
        if (l == null) return;
        boolean near = l.garage.squaredDistanceTo(p.getPos()) < 40 * 40 || Housing.here(p) == l;
        if (!near) {
            p.sendMessage(Text.literal("Машина в гараже «" + l.name + "» — приезжай туда").formatted(Formatting.YELLOW), true);
            return;
        }
        ServerWorld w = p.getServerWorld();
        Vec3d at = free(w, l.garage, true);
        CarEntity car = release(p, carId, at, l.garageYaw);
        if (car == null) return;
        w.playSound(null, car.getBlockPos(), com.yompix.novograd.ModContent.CAR_START, SoundCategory.NEUTRAL, 0.8f, 1f);
        p.sendMessage(Text.literal("Машина ждёт у въезда в гараж «" + l.name + "»").formatted(Formatting.GREEN), true);
    }

    /** Телефон: забрать со штрафстоянки — своя стоянка у другого модуля (IMPOUND_RELEASE) или выдача рядом за $250. */
    static void releaseFromApp(ServerPlayerEntity p, int carId) {
        Housing.Owner o = Housing.state(p.getServer()).owner(p.getUuid());
        NbtCompound rec = find(o, carId);
        if (rec == null || !rec.getString("where").startsWith("impound:")) return;
        String lot = rec.getString("where").substring(8);
        if (IMPOUND_RELEASE != null && !lot.equals("evict") && !lot.equals("delivery") && IMPOUND_RELEASE.apply(p, rec.copy())) return;
        if (p.hasVehicle()) {
            p.sendMessage(Text.literal("Выйди из машины — её подгонят к тебе").formatted(Formatting.YELLOW), true);
            return;
        }
        int fee = lot.equals("delivery") ? 0 : RELEASE_FEE;
        if (fee > 0 && !Life.pay(p, fee, "штрафстоянка")) return;
        ServerWorld w = p.getServerWorld();
        Vec3d look = p.getRotationVec(1).multiply(1, 0, 1).normalize().multiply(4);
        CarEntity car = release(p, carId, free(w, p.getPos().add(look), false), p.getYaw());
        if (car != null) p.sendMessage(Text.literal("Машину подогнали — она рядом").formatted(Formatting.GREEN), true);
    }

    /** Свободное место у точки: если там уже стоит машина — чуть дальше. */
    private static Vec3d free(ServerWorld w, Vec3d at, boolean ground) {
        for (int i = 0; i < 6; i++) {
            Vec3d c = at.add(i * 3.5, 0, 0);
            int y = ground ? w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(c.x), (int) Math.floor(c.z)) : (int) Math.floor(c.y);
            Vec3d p = new Vec3d(c.x, y, c.z);
            if (w.getEntitiesByClass(CarEntity.class, new Box(p, p).expand(2, 2, 2), e -> true).isEmpty()) return p;
        }
        return at;
    }
}
