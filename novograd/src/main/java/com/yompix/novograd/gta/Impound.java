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

    /** Название стоянки в записях гаража (world/Garage: «impound:police»). */
    public static final String LOT_ID = "police";

    static void tow(ServerWorld w, CarEntity car) {
        UUID owner = ownerOf(car);
        SEEN.remove(car.getUuid());
        DRIVER.remove(car.getUuid());
        if (owner != null) {
            if (car.getOwner() == null) car.setOwner(owner); // хозяин по тегу nv_owned
            String nm = name(car.getVariant());
            // машины игроков хранит world/Garage (агент жилья): там же цвет, тюнинг, бензин — всё NBT машины
            if (com.yompix.novograd.world.Garage.impound(car, LOT_ID)) {
                ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(owner);
                if (p != null) Life.message(p, "Штрафстоянка", "Твою машину (" + nm + ") эвакуировали: бросил посреди дороги. Забрать — штрафстоянка у полицейского участка, $" + FEE + ".");
                return;
            }
        }
        car.discard();
    }

    /** Названия машин для сервера (переводы item.novograd.car_* есть только у клиента). */
    static final String[] NAMES = {"Седан", "Хэтчбек", "Спорткупе", "Люкс-седан", "Фургон", "Такси", "Полицейская машина", "Внедорожник",
            "Пикап", "Маслкар", "Лимузин", "Грузовик", "Суперкар «Молния»", "Классика 70-х", "Лоурайдер", "Багги", "Малолитражка «Кроха»",
            "Бронефургон спецназа", "Скорая помощь", "Пожарная машина"};

    public static String name(int v) {
        return NAMES[Math.max(0, Math.min(v, NAMES.length - 1))];
    }

    /** Машины игрока на этой стоянке: {id, name, day}. */
    public static NbtList list(ServerPlayerEntity p) {
        NbtList out = new NbtList();
        for (NbtCompound rec : com.yompix.novograd.world.Garage.impounded(p.getServer(), p.getUuid())) {
            if (!rec.getString("where").equals("impound:" + LOT_ID)) continue;
            NbtCompound c = new NbtCompound();
            c.putInt("id", rec.getInt("id"));
            NbtCompound data = rec.getCompound("data");
            c.putString("name", rec.getString("type").endsWith(":car") ? name(data.getInt("Variant")) : rec.getString("name"));
            out.add(c);
        }
        return out;
    }

    /** Телефон → «Дом» → забрать со стоянки: наша стоянка выдаёт только на месте (world/Garage.IMPOUND_RELEASE). */
    static boolean phoneRelease(ServerPlayerEntity p, NbtCompound rec) {
        if (!rec.getString("where").equals("impound:" + LOT_ID)) return false;
        Vec3d at = Story.place(p.getServerWorld(), LOT);
        if (at != null && p.squaredDistanceTo(at) < 14 * 14) {
            retrieveId(p, rec.getInt("id"));
            return true;
        }
        if (at != null) FunUtil.target(p, at, "Штрафстоянка");
        p.sendMessage(Text.literal("⚠ Машина на штрафстоянке у полицейского участка — приезжай, $" + FEE + " (метка на радаре)").formatted(Formatting.GOLD), false);
        return true;
    }

    // ------------------------------------------------------------------ сама стоянка: метка у участка
    static void tickLot(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, LOT);
        if (at == null || p.squaredDistanceTo(at) > 40 * 40) return;
        Vec3d m = at;
        if (p.age % 40 < 10)
            FunUtil.marker(w, m, "nv_impound", new ItemStack(Items.TRIPWIRE_HOOK), Text.literal("⚠ Штрафстоянка").formatted(Formatting.GOLD), true);
        DriveHooks.walkIn(p, "impound", m, () -> {
            NbtCompound n = DriveHooks.state(p);
            n.putString("open", "impound");
            LifeNet.send(p, "drv_open", n);
        });
    }

    /** Забрать машину i (номер в списке) со штрафстоянки — только у самой стоянки. */
    public static void retrieve(ServerPlayerEntity p, int i) {
        NbtList l = list(p);
        if (i < 0 || i >= l.size()) return;
        retrieveId(p, l.getCompound(i).getInt("id"));
    }

    private static void retrieveId(ServerPlayerEntity p, int id) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, LOT);
        if (at == null || p.squaredDistanceTo(at) > 14 * 14) {
            p.sendMessage(Text.literal("Забрать машину можно только на штрафстоянке у полицейского участка").formatted(Formatting.RED), true);
            return;
        }
        if (!Life.pay(p, FEE, "штрафстоянка")) return;
        Vec3d spot = at.add(3, 0, 3);
        CarEntity car = com.yompix.novograd.world.Garage.release(p, id, new Vec3d(spot.x, at.y, spot.z), p.getYaw());
        if (car == null) {
            Life.earn(p, FEE, "возврат — машины нет");
            return;
        }
        w.playSound(null, car.getBlockPos(), ModContent.CAR_START, net.minecraft.sound.SoundCategory.NEUTRAL, 0.8f, 1f);
        Activities.title(p, "МАШИНА ВОЗВРАЩЕНА", name(car.getVariant()) + " ждёт у ворот");
    }
}
