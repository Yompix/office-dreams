package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.world.Island;
import net.minecraft.block.Blocks;
import net.minecraft.block.ObserverBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Камеры скорости и штрафы.
 *  • У трасс стоят камеры (столб с камерой ставится сам, когда игрок рядом — и в старых мирах). Ограничение 60 км/ч на трассах,
 *    50 — на магистралях в городе.
 *  • Превысил — вспышка и щелчок затвора, штраф приходит СМС от ГИБДД. Платить — телефон → «Авто» → «Штрафы».
 *  • Не заплатил за 2 игровых дня — первый же полицейский, который тебя увидит, объявит розыск (★).
 *  • Служебные машины (полиция, скорая, пожарные, спецназ) камеры не штрафуют.
 */
public final class Fines {
    private Fines() {}

    /** Камера: x, z точки на трассе, ось трассы (0 — вдоль z, 1 — вдоль x), сторона столба, лимит км/ч, название. */
    record Cam(int x, int z, int axis, int side, int limit, String name) {}

    static final Cam[] CAMS = {
            new Cam(300, -744, 1, -1, 60, "северное кольцо"),
            new Cam(744, -330, 0, 1, 60, "восточное кольцо"),
            new Cam(-552, -330, 0, -1, 60, "западное кольцо"),
            new Cam(330, 744, 1, 1, 60, "южное кольцо"),
            new Cam(-1250, -744, 1, -1, 60, "Ривьера, северная трасса"),
            new Cam(-920, 330, 0, 1, 60, "Ривьера, набережная"),
            new Cam(24, 1200, 0, 1, 60, "дорога в Пустошь"),
            new Cam(24, -1150, 0, 1, 60, "дорога в горы"),
            new Cam(1700, -400, 0, -1, 60, "восточная трасса"),
            new Cam(24, -330, 0, 1, 50, "проспект (город)"),
            new Cam(330, 24, 1, 1, 50, "магистраль Промзона"),
    };

    /** Км/ч из блоков за тик. */
    public static int kmh(double bpt) {
        return (int) Math.round(bpt * 20 * 3.6);
    }

    private static final Map<UUID, long[]> FLASHED = new HashMap<>();

    static void tick(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        for (int i = 0; i < CAMS.length; i++) {
            Cam c = CAMS[i];
            double dx = p.getX() - c.x - 0.5, dz = p.getZ() - c.z - 0.5;
            double d2 = dx * dx + dz * dz;
            if (d2 < 70 * 70) build(w, i);
            if (d2 > 14 * 14) continue;
            if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p || car.isScooter()) continue;
            if (car instanceof com.yompix.novograd.vehicle.MotorVehicle mv && !(car.isBike())) continue; // катер, вертолёт, самолёт
            if (car.body().service() && !car.isTaxi() || p.isCreative()) continue;
            int speed = kmh(car.hitSpeed());
            if (speed <= c.limit + 4) continue;
            long[] last = FLASHED.computeIfAbsent(p.getUuid(), u -> new long[CAMS.length]);
            if (w.getTime() - last[i] < 200) continue;
            last[i] = w.getTime();
            flash(p, i, speed);
        }
        if (p.age % 40 < 5) overdue(p);
    }

    private static void flash(ServerPlayerEntity p, int i, int speed) {
        Cam c = CAMS[i];
        ServerWorld w = p.getServerWorld();
        int over = speed - c.limit;
        int amt = 50 + over * 6 + (over > 30 ? 150 : 0);
        NbtCompound n = new NbtCompound();
        n.putInt("speed", speed);
        n.putInt("limit", c.limit);
        LifeNet.send(p, "drv_flash", n);
        BlockPos top = pole(i).up(4);
        w.playSound(null, top, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 1.5f, 1.8f);
        w.spawnParticles(net.minecraft.particle.ParticleTypes.FLASH, top.getX() + 0.5, top.getY() + 0.5, top.getZ() + 0.5, 1, 0, 0, 0, 0);
        DriveState st = DriveState.get(p.getServer());
        NbtList l = DriveState.list(st.of(p), "fines");
        NbtCompound f = new NbtCompound();
        f.putInt("amt", amt);
        f.putLong("day", Life.day(p));
        f.putString("where", c.name + ": " + speed + " при " + c.limit);
        l.add(f);
        st.markDirty();
        Life.message(p, "ГИБДД Новограда", "Камера «" + c.name + "»: " + speed + " км/ч при ограничении " + c.limit + ". Штраф $" + amt
                + ". Оплата: телефон → «Авто» → «Штрафы». Не оплатишь за 2 дня — объявим в розыск.");
    }

    public static int total(ServerPlayerEntity p) {
        int t = 0;
        for (var e : DriveState.list(DriveState.get(p.getServer()).of(p), "fines")) t += ((NbtCompound) e).getInt("amt");
        return t;
    }

    public static NbtList list(ServerPlayerEntity p) {
        return DriveState.list(DriveState.get(p.getServer()).of(p), "fines");
    }

    public static void payAll(ServerPlayerEntity p) {
        int t = total(p);
        if (t <= 0) {
            p.sendMessage(Text.literal("Штрафов нет — чистая совесть!").formatted(Formatting.GREEN), true);
            return;
        }
        if (!Life.pay(p, t, "штрафы ГИБДД")) return;
        DriveState st = DriveState.get(p.getServer());
        st.of(p).remove("fines");
        st.markDirty();
        Life.message(p, "ГИБДД Новограда", "Штрафы на $" + t + " оплачены. Спасибо, что соблюдаете!");
    }

    /** Просроченные штрафы: полиция видит тебя — ★. Раз в игровой день за одни и те же штрафы. */
    private static void overdue(ServerPlayerEntity p) {
        DriveState st = DriveState.get(p.getServer());
        NbtCompound me = st.of(p);
        if (!me.contains("fines")) return;
        long today = Life.day(p);
        boolean late = false;
        for (var e : DriveState.list(me, "fines")) if (today - ((NbtCompound) e).getLong("day") >= 2) late = true;
        if (!late || me.getLong("finesRep") >= today || Wanted.stars(p) > 0) return;
        if (!Wanted.policeSees(p.getServerWorld(), p, 36)) return;
        me.putLong("finesRep", today);
        st.markDirty();
        Wanted.add(p, 1, "неоплаченные штрафы ($" + total(p) + ")", 1);
        Life.message(p, "ГИБДД Новограда", "Вы не оплатили штрафы — патруль вас узнал. Оплатите в телефоне («Авто» → «Штрафы»), пока не поздно.");
    }

    // ------------------------------------------------------------------ столбы с камерами
    static BlockPos pole(int i) {
        Cam c = CAMS[i];
        int x = c.axis == 0 ? c.x + 10 * c.side : c.x, z = c.axis == 0 ? c.z : c.z + 10 * c.side;
        return new BlockPos(x, Island.surface(x, z) + 1, z);
    }

    private static void build(ServerWorld w, int i) {
        DriveState st = DriveState.get(w.getServer());
        if (st.global.getBoolean("cam" + i)) return;
        BlockPos base = pole(i);
        if (!w.isChunkLoaded(base)) return;
        Cam c = CAMS[i];
        // камера смотрит на трассу
        Direction face = c.axis == 0 ? (c.side > 0 ? Direction.WEST : Direction.EAST) : (c.side > 0 ? Direction.NORTH : Direction.SOUTH);
        for (int y = 0; y < 4; y++) w.setBlockState(base.up(y), Blocks.POLISHED_DEEPSLATE_WALL.getDefaultState());
        w.setBlockState(base.up(4), Blocks.OBSERVER.getDefaultState().with(ObserverBlock.FACING, face.getOpposite()));
        w.setBlockState(base.up(5), Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        w.setBlockState(base.up(4).offset(face), Blocks.LIGHT_GRAY_STAINED_GLASS_PANE.getDefaultState());
        w.setBlockState(base.up(3).offset(face.getOpposite()), Blocks.YELLOW_CONCRETE.getDefaultState());
        st.global.putBoolean("cam" + i, true);
        st.markDirty();
    }
}
