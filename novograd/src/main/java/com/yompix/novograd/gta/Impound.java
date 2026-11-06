package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
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
 * Эвакуатор и штрафстоянка.
 *  • Машина, брошенная посреди дороги (не на обочине и не на парковке) больше 3 минут, пока рядом никого нет, — эвакуируется.
 *  • Чужая/угнанная — просто увозят. Своя (хозяин в CarEntity.owner или тег «nv_owned:&lt;uuid&gt;») — на «Штрафстоянку»
 *    у полицейского участка: СМС хозяину, забрать там же за $200 (всё как было: цвет, тюнинг, бензин).
 */
public final class Impound {
    private Impound() {}

    public static final int FEE = 200;
    public static final String LOT = "police_station+16,10";
    private static final int IDLE_TICKS = 20 * 180;

    /** Машина → {кто последний вёл, когда, где стояла (x*31+z), с какого тика стоит}. */
    private static final Map<UUID, long[]> SEEN = new HashMap<>();
    private static final Map<UUID, UUID> DRIVER = new HashMap<>();

    static void driven(CarEntity car, ServerPlayerEntity p) {
        DRIVER.put(car.getUuid(), p.getUuid());
        SEEN.remove(car.getUuid());
    }

    @Nullable
    public static UUID ownerOf(CarEntity car) {
        if (car.getOwner() != null) return car.getOwner();
        for (String t : car.getCommandTags())
            if (t.startsWith("nv_owned:")) try {
                return UUID.fromString(t.substring(9));
            } catch (IllegalArgumentException ignored) {
            }
        return null;
    }

    /** Посреди проезжей части: трасса или полоса городской улицы (не тротуар и не двор). */
    static boolean midRoad(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        if (!Island.road(ix, iz)) return false;
        Island.Zone zn = Island.zone(ix, iz);
        if (zn == Island.Zone.CORE) return false; // старый центр — дворы и парковки
        if (Island.urban(zn)) return Math.min(Island.gridDist(ix), Island.gridDist(iz)) <= 3;
        return true;
    }

    static void tick(MinecraftServer s) {
        if (s.getTicks() % 100 != 41) return;
        ServerWorld w = s.getOverworld();
        long now = w.getTime();
        for (UUID id : new java.util.ArrayList<>(DRIVER.keySet())) {
            if (!(w.getEntity(id) instanceof CarEntity car) || car.isRemoved()) {
                // выгружена или пропала: своя проверится, когда снова загрузится
                if (w.getEntity(id) == null && !SEEN.containsKey(id)) continue;
                DRIVER.remove(id);
                SEEN.remove(id);
                continue;
            }
            check(w, car, now);
        }
        // свои машины игроков: и после перезахода (хозяин записан в машине)
        for (ServerPlayerEntity p : w.getPlayers())
            for (CarEntity car : w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(160, 40, 160), c -> ownerOf(c) != null && !DRIVER.containsKey(c.getUuid())))
                check(w, car, now);
    }

    private static void check(ServerWorld w, CarEntity car, long now) {
        if (car.hasPassengers() || car.wrecked() || car.isScooter() || car instanceof com.yompix.novograd.vehicle.MotorVehicle || car.isAmbient() && ownerOf(car) == null && !DRIVER.containsKey(car.getUuid())) {
            SEEN.remove(car.getUuid());
            return;
        }
        long key = (long) Math.floor(car.getX()) * 100003L + (long) Math.floor(car.getZ());
        long[] s = SEEN.get(car.getUuid());
        if (s == null || s[0] != key) {
            SEEN.put(car.getUuid(), new long[]{key, now});
            return;
        }
        if (now - s[1] < IDLE_TICKS || !midRoad(car.getX(), car.getZ())) return;
        if (w.getClosestPlayer(car, 24) != null) return; // на глазах не увозим
        tow(w, car);
    }

    static void tow(ServerWorld w, CarEntity car) {
        UUID owner = ownerOf(car);
        SEEN.remove(car.getUuid());
        DRIVER.remove(car.getUuid());
        if (owner != null) {
            DriveState st = DriveState.get(w.getServer());
            NbtCompound rec = new NbtCompound();
            rec.putInt("v", car.getVariant());
            rec.putInt("paint", car.color() == CarEntity.COLORS[car.getVariant()] ? 0 : car.color());
            rec.putInt("tune", car.tune());
            rec.putInt("hp", car.health());
            rec.putInt("fuel", car.fuel());
            rec.putLong("day", w.getTimeOfDay() / 24000L + 1);
            rec.putString("tags", String.join(",", car.getCommandTags()));
            DriveState.list(st.of(owner), "impound").add(rec);
            st.markDirty();
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(owner);
            if (p != null) Life.message(p, "Штрафстоянка", "Твою машину (" + name(car.getVariant()) + ") эвакуировали: бросил посреди дороги. Забрать — штрафстоянка у полицейского участка, $" + FEE + ".");
        }
        car.discard();
    }

    public static String name(int v) {
        return Text.translatable("item.novograd.car_" + CarEntity.VARIANTS[Math.max(0, Math.min(v, CarEntity.VARIANTS.length - 1))]).getString();
    }

    public static NbtList list(ServerPlayerEntity p) {
        return DriveState.list(DriveState.get(p.getServer()).of(p), "impound");
    }

    // ------------------------------------------------------------------ сама стоянка: метка у участка
    static void tickLot(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, LOT);
        if (at == null || p.squaredDistanceTo(at) > 40 * 40) return;
        int y = Story.topY(w, (int) Math.floor(at.x), (int) Math.floor(at.z));
        Vec3d m = new Vec3d(at.x, y, at.z);
        if (p.age % 40 < 10)
            FunUtil.marker(w, m, "nv_impound", new ItemStack(Items.TRIPWIRE_HOOK), Text.literal("⚠ Штрафстоянка").formatted(Formatting.GOLD), true);
        DriveHooks.walkIn(p, "impound", m, () -> {
            NbtCompound n = DriveHooks.state(p);
            n.putString("open", "impound");
            LifeNet.send(p, "drv_open", n);
        });
    }

    /** Забрать машину i со штрафстоянки (только у самой стоянки). */
    public static void retrieve(ServerPlayerEntity p, int i) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, LOT);
        if (at == null || p.squaredDistanceTo(at) > 14 * 14) {
            p.sendMessage(Text.literal("Забрать машину можно только на штрафстоянке у полицейского участка").formatted(Formatting.RED), true);
            return;
        }
        NbtList l = list(p);
        if (i < 0 || i >= l.size()) return;
        if (!Life.pay(p, FEE, "штрафстоянка")) return;
        NbtCompound rec = l.getCompound(i);
        l.remove(i);
        DriveState.get(p.getServer()).markDirty();
        Vec3d spot = at.add(3, 0, 3);
        int y = Story.topY(w, (int) Math.floor(spot.x), (int) Math.floor(spot.z));
        CarEntity car = FunUtil.car(w, new Vec3d(spot.x, y, spot.z), p.getYaw(), rec.getInt("v"));
        if (rec.getInt("paint") != 0) car.paint(rec.getInt("paint"));
        car.setTune((rec.getInt("tune") & 1) != 0, (rec.getInt("tune") >> 1) & 3, (rec.getInt("tune") >> 3) & 3);
        car.setFuel(rec.getInt("fuel"));
        car.setOwner(p.getUuid());
        for (String t : rec.getString("tags").split(",")) if (!t.isBlank()) car.addCommandTag(t);
        w.playSound(null, car.getBlockPos(), ModContent.CAR_START, net.minecraft.sound.SoundCategory.NEUTRAL, 0.8f, 1f);
        Activities.title(p, "МАШИНА ВОЗВРАЩЕНА", name(rec.getInt("v")) + " ждёт у ворот");
    }
}
