package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.block.TrafficLightBlock;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.world.Island;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Светофоры, которые работают. Перекрёстки сетки улиц (≡ 24 mod 48) со светофорами на углах (их ставят Island / TownBuilder)
 * живут по общим городским часам: цикл 30 с — 12 с зелёный, 3 с жёлтый, 15 с красный, поперечная улица — в противофазе
 * (как в {@link TrafficLightBlock}). Здесь: лампы у игроков переключаются (в мире острова они ставятся генератором и сами
 * не тикают), машины-NPC встают у стоп-линии на красный, игрока за проезд на красный на глазах у полиции ловят на звезду.
 * Для пешеходов (ИИ жителей): {@link #canCross}, {@link #isRed}.
 */
public final class TrafficLights {
    private TrafficLights() {}

    public static final int RED = 0, YELLOW = 1, GREEN = 2;
    /** Стоп-линия: от центра перекрёстка до переднего бампера (за «зеброй» 7–8). */
    private static final double STOP = 9.6;

    /** Свет для тех, кто едет вдоль оси Z (alongZ) или X — то же, что у блока светофора. */
    public static int state(long time, boolean alongZ) {
        int s = (int) (time / 20 % 30);
        if (!alongZ) s = (s + 15) % 30;
        return s < 12 ? GREEN : s < 15 ? YELLOW : RED;
    }

    /** Сколько секунд до смены сигнала для оси. */
    public static int secondsLeft(long time, boolean alongZ) {
        int s = (int) (time / 20 % 30);
        if (!alongZ) s = (s + 15) % 30;
        return s < 12 ? 12 - s : s < 15 ? 15 - s : 30 - s;
    }

    static int line(double t) {
        return (int) (Math.round((t - 24) / 48.0) * 48 + 24);
    }

    // ------------------------------------------------------------------ где стоят светофоры
    /** Перекрёсток → найденные блоки светофоров (пусто — перекрёсток без светофора). Только из загруженных чанков. */
    private static final Map<Long, BlockPos[]> LIGHTS = new HashMap<>();
    private static final BlockPos[] NONE = new BlockPos[0];

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Есть ли на перекрёстке (gx, gz) светофор. null — ещё не знаем (чанк не загружен). */
    private static BlockPos[] lights(ServerWorld w, int gx, int gz) {
        long k = key(gx, gz);
        BlockPos[] b = LIGHTS.get(k);
        if (b != null) return b;
        Island.Zone zn = Island.zone(gx, gz);
        if (!Island.urban(zn) && zn != Island.Zone.CORE) {
            LIGHTS.put(k, NONE);
            return NONE;
        }
        for (int dx = -6; dx <= 6; dx += 12)
            for (int dz = -6; dz <= 6; dz += 12)
                if (!w.isChunkLoaded((gx + dx) >> 4, (gz + dz) >> 4)) return null;
        List<BlockPos> found = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int r : new int[]{5, 6})
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sz = -1; sz <= 1; sz += 2)
                    for (int y = Island.G + 1; y <= Island.G + 9; y++) {
                        m.set(gx + sx * r, y, gz + sz * r);
                        if (w.getBlockState(m).isOf(ModContent.TRAFFIC_LIGHT)) found.add(m.toImmutable());
                    }
        b = found.isEmpty() ? NONE : found.toArray(new BlockPos[0]);
        LIGHTS.put(k, b);
        return b;
    }

    /** Перекрёсток регулируется светофором (известно и есть хоть один). */
    public static boolean signalled(ServerWorld w, int gx, int gz) {
        BlockPos[] b = lights(w, gx, gz);
        return b != null && b.length > 0;
    }

    // ------------------------------------------------------------------ API для пешеходов (ИИ жителей)
    /**
     * Можно ли пешеходу у pos перейти улицу, идя по направлению dir (0 +x, 1 +z, 2 −x, 3 −z):
     * идёт поперёк дороги вдоль Z (то есть по X) — ждёт, пока у машин вдоль Z красный. Нерегулируемый перекрёсток — всегда «можно».
     */
    public static boolean canCross(ServerWorld w, BlockPos pos, int dir) {
        int gx = line(pos.getX()), gz = line(pos.getZ());
        if (Math.abs(pos.getX() - gx) > 14 || Math.abs(pos.getZ() - gz) > 14 || !signalled(w, gx, gz)) return true;
        boolean crossingZRoad = dir == 0 || dir == 2;
        return state(w.getTime(), crossingZRoad) == RED && secondsLeft(w.getTime(), crossingZRoad) > 2;
    }

    /** Красный (или жёлтый) для машины у pos, едущей по направлению dir. Нерегулируемый — false. */
    public static boolean isRed(ServerWorld w, BlockPos pos, int dir) {
        int gx = line(pos.getX()), gz = line(pos.getZ());
        if (!signalled(w, gx, gz)) return false;
        return state(w.getTime(), dir == 1 || dir == 3) != GREEN;
    }

    // ------------------------------------------------------------------ подход машины к перекрёстку
    /** Машина едет к перекрёстку (gx, gz) вдоль оси alongZ, до стоп-линии осталось d блоков (минус — уже за ней). */
    record Approach(int gx, int gz, boolean alongZ, double d) {}

    /** По позиции и направлению (единичный вектор) — ближайший перекрёсток впереди, или null (поворачиваем, не на улице). */
    static Approach approach(Vec3d pos, Vec3d fwd, double front) {
        boolean alongZ;
        int sgn;
        if (Math.abs(fwd.x) > 0.88) { alongZ = false; sgn = fwd.x > 0 ? 1 : -1; }
        else if (Math.abs(fwd.z) > 0.88) { alongZ = true; sgn = fwd.z > 0 ? 1 : -1; }
        else return null;
        double a = alongZ ? pos.z : pos.x, c = alongZ ? pos.x : pos.z;
        int street = line(c);
        double off = (c - street) * (alongZ ? -sgn : sgn); // правая полоса: +
        if (Math.abs(c - street) > 4.5) return null;
        // центр ближайшего перекрёстка впереди (включая тот, в который въезжаем)
        int ahead = line(a + sgn * 23.99);
        double k = (ahead - a) * sgn;                // до центра перекрёстка
        double d = k - STOP - front;                  // до стоп-линии передним бампером
        if (off < -0.6) return null;                  // встречка (обгон) — не наш светофор
        return alongZ ? new Approach(street, ahead, true, d) : new Approach(ahead, street, false, d);
    }

    // ------------------------------------------------------------------ машины-NPC: стоп на красный
    /** Кто подчиняется светофорам: трафик, автобусы, служебные фургоны (не скорая/полиция с мигалкой). */
    private static final Set<CarEntity> OBEY = Collections.newSetFromMap(new WeakHashMap<>());

    public static void obey(CarEntity car) {
        OBEY.add(car);
    }

    /** Предел скорости автопилота: у стоп-линии на красный — 0. Float.MAX_VALUE — светофор не мешает. */
    public static float limit(CarEntity car, Vec3d fwd, float autoMax) {
        if (!(car.getWorld() instanceof ServerWorld w)) return Float.MAX_VALUE;
        if (!car.isAmbient() && !OBEY.contains(car)) return Float.MAX_VALUE;
        if (car.isBike() || car.isScooter() || car.body().beacon() && car.hasNpcDriver() && !OBEY.contains(car)) return Float.MAX_VALUE;
        Approach ap = approach(car.getPos(), fwd, 2.1);
        if (ap == null || ap.d < -1.2 || ap.d > 40) return Float.MAX_VALUE;
        if (!signalled(w, ap.gx, ap.gz)) return Float.MAX_VALUE;
        int st = state(w.getTime(), ap.alongZ);
        if (st == GREEN) return Float.MAX_VALUE;
        if (st == YELLOW) {
            double brake = car.speed * car.speed * 10 + 1.5; // не успеваем остановиться — проезжаем на жёлтый
            if (ap.d < brake) return Float.MAX_VALUE;
        }
        if (ap.d < 18) car.lightWait = Math.max(car.lightWait, 10);
        return ap.d < 0.4 ? 0 : (float) Math.min(1, ap.d / 7.0) * autoMax;
    }

    // ------------------------------------------------------------------ тик: лампы и нарушители
    private static final Map<UUID, Double> LAST_D = new HashMap<>();
    private static final Map<UUID, Long> LAST_FINE = new HashMap<>();

    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        ServerWorld w = server.getOverworld();
        if (t % 20 == 0) {
            if (LIGHTS.size() > 20000) LIGHTS.clear();
            long time = w.getTime();
            for (ServerPlayerEntity p : w.getPlayers()) {
                int px = line(p.getX()), pz = line(p.getZ());
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) drive(w, px + dx * 48, pz + dz * 48, time);
            }
        }
        if (t % 4 == 0) for (ServerPlayerEntity p : w.getPlayers()) redLight(w, p);
    }

    /** Переключить лампы перекрёстка (меняем блок только при смене сигнала). */
    private static void drive(ServerWorld w, int gx, int gz, long time) {
        BlockPos[] ls = lights(w, gx, gz);
        if (ls == null || ls.length == 0) return;
        boolean removed = false;
        for (BlockPos b : ls) {
            if (!w.isChunkLoaded(b)) continue;
            BlockState s = w.getBlockState(b);
            if (!s.isOf(ModContent.TRAFFIC_LIGHT)) { removed = true; continue; } // сломали — пересчитаем
            int l = state(time, s.get(HorizontalFacingBlock.FACING).getAxis() == Direction.Axis.Z);
            if (s.get(TrafficLightBlock.LIGHT) != l) w.setBlockState(b, s.with(TrafficLightBlock.LIGHT, l), net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
        if (removed) LIGHTS.remove(key(gx, gz));
    }

    /** Игрок за рулём пересёк стоп-линию на красный: полиция видела — звезда, по рации — сводка. */
    private static void redLight(ServerWorld w, ServerPlayerEntity p) {
        if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p || car instanceof com.yompix.novograd.vehicle.MotorVehicle) {
            LAST_D.remove(p.getUuid());
            return;
        }
        double rad = Math.toRadians(car.getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        Approach ap = approach(car.getPos(), fwd, 2.1);
        Double before = LAST_D.get(p.getUuid());
        LAST_D.put(p.getUuid(), ap == null ? null : ap.d);
        if (ap == null || before == null || !(before > 0 && ap.d <= 0 && ap.d > -6)) return;
        if (car.hitSpeed() < 0.12 || !signalled(w, ap.gx, ap.gz) || state(w.getTime(), ap.alongZ) != RED) return;
        long now = w.getTime();
        if (now - LAST_FINE.getOrDefault(p.getUuid(), -10000L) < 20 * 20) return;
        LAST_FINE.put(p.getUuid(), now);
        if (!p.isCreative() && Wanted.policeSees(w, car, 45)) {
            Wanted.add(p, 1, "проезд на красный на глазах у полиции", 1);
            Dispatch.report(w, car.getPos(), "проезд на красный", Dispatch.describe(car), p);
        } else {
            p.sendMessage(net.minecraft.text.Text.literal("Проехал на красный! Повезло — полиции рядом нет").formatted(net.minecraft.util.Formatting.GOLD), true);
        }
    }
}
