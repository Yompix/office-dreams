package com.yompix.novograd.vehicle;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.world.Island;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Транспорт кроме машин: мотоцикл, катер, вертолёт, самолёт.
 * Регистрация, предметы для творческого режима и расстановка по острову (порт, пляж, крыши, аэродром).
 */
public final class Vehicles {
    private Vehicles() {}

    // ---------- звуки ----------
    public static final SoundEvent BIKE_ENGINE = sound("bike_engine");
    public static final SoundEvent BOAT_ENGINE = sound("boat_engine");
    public static final SoundEvent HELI_ROTOR = sound("heli_rotor");
    public static final SoundEvent PLANE_ENGINE = sound("plane_engine");

    // ---------- существа ----------
    /** Мотоцикл — та же машина (CarEntity), только другой тип: физика, угон, тюнинг, пули — общие. */
    public static final EntityType<CarEntity> BIKE = Registry.register(Registries.ENTITY_TYPE, Novograd.id("bike"),
            EntityType.Builder.<CarEntity>create(CarEntity::new, SpawnGroup.MISC).dimensions(1.0f, 1.4f).maxTrackingRange(10).trackingTickInterval(1).alwaysUpdateVelocity(true).build("bike"));
    public static final EntityType<SpeedboatEntity> SPEEDBOAT = Registry.register(Registries.ENTITY_TYPE, Novograd.id("speedboat"),
            EntityType.Builder.<SpeedboatEntity>create(SpeedboatEntity::new, SpawnGroup.MISC).dimensions(1.9f, 1.0f).maxTrackingRange(10).trackingTickInterval(1).alwaysUpdateVelocity(true).build("speedboat"));
    public static final EntityType<HeliEntity> HELI = Registry.register(Registries.ENTITY_TYPE, Novograd.id("helicopter"),
            EntityType.Builder.<HeliEntity>create(HeliEntity::new, SpawnGroup.MISC).dimensions(2.8f, 2.4f).maxTrackingRange(16).trackingTickInterval(1).alwaysUpdateVelocity(true).build("helicopter"));
    public static final EntityType<PlaneEntity> PLANE = Registry.register(Registries.ENTITY_TYPE, Novograd.id("plane"),
            EntityType.Builder.<PlaneEntity>create(PlaneEntity::new, SpawnGroup.MISC).dimensions(3.0f, 2.0f).maxTrackingRange(16).trackingTickInterval(1).alwaysUpdateVelocity(true).build("plane"));

    // ---------- предметы ----------
    public static final Item BIKE_ITEM = item("bike", new VehicleItem(new Item.Settings().maxCount(1), VehicleItem.Kind.BIKE));
    public static final Item SPEEDBOAT_ITEM = item("speedboat", new VehicleItem(new Item.Settings().maxCount(1), VehicleItem.Kind.BOAT));
    public static final Item HELI_ITEM = item("helicopter", new VehicleItem(new Item.Settings().maxCount(1), VehicleItem.Kind.HELI));
    public static final Item PLANE_ITEM = item("plane", new VehicleItem(new Item.Settings().maxCount(1), VehicleItem.Kind.PLANE));

    // ---------- ввод (только клиент водителя: физика считается у него) ----------
    /** Пробел и Shift водителя — их выставляет VehiclesClient каждый тик. */
    public static volatile boolean inUp, inDown;
    /** Клиентский хук: звук мотора для транспорта (выставляет VehiclesClient). */
    public static Consumer<MotorVehicle> clientTick = v -> {};

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(RegistryKey.of(RegistryKeys.ITEM_GROUP, Novograd.id("main"))).register(e -> {
            e.add(BIKE_ITEM);
            e.add(SPEEDBOAT_ITEM);
            e.add(HELI_ITEM);
            e.add(PLANE_ITEM);
        });
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            if (w.getTime() % 100 != 7 || w.getRegistryKey() != net.minecraft.world.World.OVERWORLD) return;
            if (!(w.getChunkManager().getChunkGenerator() instanceof com.yompix.novograd.world.IslandChunkGenerator)) return;
            for (ServerPlayerEntity p : w.getPlayers()) {
                try {
                    populate(w, p);
                } catch (Exception ex) {
                    Novograd.LOG.warn("vehicles populate", ex);
                }
            }
        });
    }

    private static SoundEvent sound(String name) {
        return Registry.register(Registries.SOUND_EVENT, Novograd.id(name), SoundEvent.of(Novograd.id(name)));
    }

    private static <I extends Item> I item(String name, I item) {
        return Registry.register(Registries.ITEM, Novograd.id(name), item);
    }

    // ------------------------------------------------------------------ расстановка по острову
    /** Где и когда уже ставили транспорт: ключ точки → время (чтобы угнанный/взорванный появился снова не сразу). */
    private static final Map<String, Long> SPAWNED = new HashMap<>();
    private static final long RESPAWN = 20L * 60 * 8;

    /**
     * Ставит транспорт рядом с игроком, если он подошёл к порту, пляжу, крыше с вертолётной площадкой или аэродрому,
     * а транспорта там ещё нет. Можно звать сколько угодно часто — лишнего не наставит.
     */
    public static void populate(ServerWorld w, ServerPlayerEntity p) {
        if (p.isSpectator()) return;
        long now = w.getTime();
        // катера у причалов порта и у пляжа
        boats(w, p, "port_dock", 3, now);
        boats(w, p, "beach_lot", 2, now);
        boats(w, p, "beach_bar", 1, now);
        // аэродром: вертолёт у ангара и самолёт на полосе
        double[] hangar = Island.place("airfield_hangar");
        if (hangar != null && p.squaredDistanceTo(hangar[0], hangar[1], hangar[2]) < 90 * 90) {
            BlockPos pad = new BlockPos((int) Math.floor(hangar[0]), 0, (int) Math.floor(hangar[2]) - 3);
            spawnOnGround(w, "air_heli", pad, now, () -> new HeliEntity(HELI, w), 180);
            BlockPos strip = new BlockPos(372, 0, -905);
            spawnOnGround(w, "air_plane", strip, now, () -> new PlaneEntity(PLANE, w), 270);
            spawnOnGround(w, "air_plane2", new BlockPos(520, 0, -880), now, () -> new PlaneEntity(PLANE, w), 270);
        }
        // крыши небоскрёбов делового центра: жёлтая «H» по центру участка
        if (Island.zone((int) p.getX(), (int) p.getZ()) == Island.Zone.DOWNTOWN) {
            int pcx = Math.floorDiv((int) p.getX() + 24, 48), pcz = Math.floorDiv((int) p.getZ() + 24, 48);
            int placed = 0;
            for (int dx = -2; dx <= 2 && placed < 2; dx++)
                for (int dz = -2; dz <= 2 && placed < 2; dz++) {
                    int x = (pcx + dx) * 48, z = (pcz + dz) * 48;
                    if (Island.zone(x, z) != Island.Zone.DOWNTOWN) continue;
                    BlockPos probe = new BlockPos(x, p.getBlockY(), z);
                    if (!w.shouldTickEntity(probe)) continue;
                    int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                    if (!w.getBlockState(new BlockPos(x, top - 1, z)).isOf(Blocks.YELLOW_CONCRETE)
                            || !w.getBlockState(new BlockPos(x + 3, top - 1, z)).isOf(Blocks.GRAY_CONCRETE)) continue;
                    String key = "roof_" + x + "_" + z;
                    Long t = SPAWNED.get(key);
                    if (t != null && now - t < RESPAWN) continue;
                    Vec3d at = new Vec3d(x + 0.5, top, z + 0.5);
                    if (!w.getEntitiesByClass(HeliEntity.class, new Box(at, at).expand(10), e -> true).isEmpty()) {
                        SPAWNED.put(key, now);
                        continue;
                    }
                    HeliEntity h = new HeliEntity(HELI, w);
                    h.refreshPositionAndAngles(at.x, at.y, at.z, w.random.nextInt(4) * 90, 0);
                    h.setVariant(w.random.nextInt(3));
                    if (!w.isSpaceEmpty(h, h.getBoundingBox())) continue;
                    w.spawnEntity(h);
                    SPAWNED.put(key, now);
                    placed++;
                }
        }
    }

    private static void spawnOnGround(ServerWorld w, String key, BlockPos xz, long now, java.util.function.Supplier<MotorVehicle> make, float yaw) {
        Long t = SPAWNED.get(key);
        if (t != null && now - t < RESPAWN) return;
        if (!w.shouldTickEntity(xz.withY(Island.G))) return;
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, xz.getX(), xz.getZ());
        Vec3d at = new Vec3d(xz.getX() + 0.5, y, xz.getZ() + 0.5);
        MotorVehicle v = make.get();
        if (!w.getEntitiesByClass(v.getClass(), new Box(at, at).expand(12), e -> true).isEmpty()) {
            SPAWNED.put(key, now);
            return;
        }
        v.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
        v.setVariant(w.random.nextInt(3));
        if (!w.isSpaceEmpty(v, v.getBoundingBox())) return;
        w.spawnEntity(v);
        SPAWNED.put(key, now);
    }

    /** Несколько катеров на воде рядом с точкой (свободная вода 3×3, между катерами не меньше 6 блоков). */
    private static void boats(ServerWorld w, ServerPlayerEntity p, String place, int count, long now) {
        double[] c = Island.place(place);
        if (c == null || p.squaredDistanceTo(c[0], p.getY(), c[2]) > 110 * 110) return;
        Long t = SPAWNED.get(place);
        if (t != null && now - t < RESPAWN) return;
        BlockPos center = BlockPos.ofFloored(c[0], Island.G, c[2]);
        if (!w.shouldTickEntity(center)) return;
        Box area = new Box(center).expand(60, 20, 60);
        int have = w.getEntitiesByClass(SpeedboatEntity.class, area, e -> true).size();
        SPAWNED.put(place, now);
        if (have >= count) return;
        java.util.List<Vec3d> spots = new java.util.ArrayList<>();
        for (int r = 4; r <= 56 && spots.size() < count - have; r += 3)
            for (int i = 0; i < 24 && spots.size() < count - have; i++) {
                double a = i / 24.0 * Math.PI * 2;
                int x = center.getX() + (int) Math.round(Math.cos(a) * r), z = center.getZ() + (int) Math.round(Math.sin(a) * r);
                BlockPos at = new BlockPos(x, Island.G - 1, z);
                if (!w.shouldTickEntity(at) || !openWater(w, at)) continue;
                Vec3d v = new Vec3d(x + 0.5, 0, z + 0.5);
                if (spots.stream().anyMatch(s -> s.squaredDistanceTo(v) < 36)) continue;
                spots.add(v);
            }
        for (Vec3d s : spots) {
            SpeedboatEntity b = new SpeedboatEntity(SPEEDBOAT, w);
            BlockPos wp = BlockPos.ofFloored(s.x, Island.G - 1, s.z);
            double surf = wp.getY() + w.getFluidState(wp).getHeight(w, wp);
            b.refreshPositionAndAngles(s.x, surf - SpeedboatEntity.DRAFT, s.z, w.random.nextInt(360), 0);
            b.setVariant(w.random.nextInt(SpeedboatEntity.COLORS.length));
            w.spawnEntity(b);
        }
    }

    private static boolean openWater(ServerWorld w, BlockPos at) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos q = at.add(dx, 0, dz);
                if (!w.getFluidState(q).isIn(FluidTags.WATER) || !w.isAir(q.up()) || !w.isAir(q.up(2))) return false;
            }
        return true;
    }
}
