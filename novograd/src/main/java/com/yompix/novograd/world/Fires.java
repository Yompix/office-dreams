package com.yompix.novograd.world;

import com.yompix.novograd.block.PropBlock;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Пожары на улицах: от взрывов и «коктейлей» загораются урны, скамейки, заборы, киоски и машины.
 * Огонь понемногу перекидывается на соседние горючие предметы и машины, над ним — столб дыма, люди разбегаются.
 * Догоревший предмет исчезает и потом возвращается (Destruction). Тушат дождь, гидрант и брандспойт пожарной машины.
 * Не больше 20 очагов на весь мир, всё считается только для них — без обхода мира.
 */
public final class Fires {
    private Fires() {}

    static final int MAX = 20;

    static final class Spot {
        final ServerWorld w;
        Vec3d at;
        @Nullable final BlockPos block;
        int ttl, age;

        Spot(ServerWorld w, Vec3d at, @Nullable BlockPos block, int ttl) {
            this.w = w;
            this.at = at;
            this.block = block;
            this.ttl = ttl;
        }
    }

    static final List<Spot> SPOTS = new ArrayList<>();

    public static int count() {
        return SPOTS.size();
    }

    /** Горючий уличный предмет? */
    public static boolean flammable(ServerWorld w, BlockPos p, BlockState st) {
        if (st.getBlock() instanceof PropBlock pb)
            return pb.kind == PropBlock.Kind.NEWS_STAND || pb.kind == PropBlock.Kind.HOTDOG || pb.kind == PropBlock.Kind.ICECREAM || pb.kind == PropBlock.Kind.PROPANE;
        Destruction.Hit h = Destruction.classify(w, p, st);
        return h == Destruction.Hit.FENCE || h == Destruction.Hit.TRASH || h == Destruction.Hit.BENCH || h == Destruction.Hit.HEDGE || st.isOf(Blocks.HAY_BLOCK);
    }

    private static boolean burning(BlockPos p) {
        for (Spot s : SPOTS) if (p.equals(s.block)) return true;
        return false;
    }

    /** Очаг огня в точке at на sec секунд (block — горящий предмет или null, если горит земля/лужа бензина). */
    public static void ignite(ServerWorld w, Vec3d at, int sec, @Nullable BlockPos block) {
        if (SPOTS.size() >= MAX || (block != null && burning(block))) return;
        if (block != null && w.getBlockState(block).getBlock() instanceof PropBlock pb && pb.kind == PropBlock.Kind.PROPANE) {
            Destruction.breakBlock(w, block, Blocks.AIR.getDefaultState(), 300);
            Destruction.later(w, Vec3d.ofCenter(block), 3.2f, 30 + w.random.nextInt(30), null); // баллон в огне — через пару секунд взрыв
            return;
        }
        SPOTS.add(new Spot(w, at, block == null ? null : block.toImmutable(), sec * 20 + w.random.nextInt(200)));
    }

    /** Взрыв или «коктейль»: поджечь до max горючих предметов в радиусе r, а если их нет — землю под точкой. */
    public static void igniteAround(ServerWorld w, Vec3d at, double r, int max) {
        int n = 0;
        int ri = (int) Math.ceil(r);
        BlockPos o = BlockPos.ofFloored(at);
        for (int dx = -ri; dx <= ri && n < max; dx++)
            for (int dz = -ri; dz <= ri && n < max; dz++)
                for (int dy = -1; dy <= 2 && n < max; dy++) {
                    BlockPos p = o.add(dx, dy, dz);
                    BlockState st = w.getBlockState(p);
                    if (st.isAir() || !flammable(w, p, st) || burning(p)) continue;
                    if (w.random.nextInt(3) == 0) continue;
                    ignite(w, Vec3d.ofBottomCenter(p), 25 + w.random.nextInt(20), p);
                    n++;
                }
        if (n == 0) {
            Vec3d g = new Vec3d(at.x, groundY(w, at), at.z);
            ignite(w, g, 18 + w.random.nextInt(10), null);
        }
    }

    private static double groundY(ServerWorld w, Vec3d at) {
        BlockPos p = BlockPos.ofFloored(at);
        for (int i = 0; i < 6 && w.getBlockState(p.down()).getCollisionShape(w, p.down()).isEmpty(); i++) p = p.down();
        return p.getY();
    }

    /** Гидрант или вода: гасит огонь вокруг (amount — сколько тиков горения снять). */
    public static void douseAt(ServerWorld w, Vec3d at, double r, int amount) {
        for (Spot s : SPOTS) if (s.w == w && s.at.squaredDistanceTo(at) < r * r) s.ttl -= amount;
    }

    /**
     * Брандспойт (VehicleJobs.hoseTick): струя из from по dir гасит уличные пожары.
     * @return сколько очагов потушено за этот тик
     */
    public static int douse(ServerWorld w, Vec3d from, Vec3d dir, @Nullable ServerPlayerEntity p) {
        int out = 0;
        // копия: взрыв машины/обрушение внутри цикла может поджечь новое место (ignite → SPOTS.add)
        for (Spot s : new ArrayList<>(SPOTS)) {
            if (!SPOTS.contains(s)) continue;
            if (s.w != w) continue;
            Vec3d to = s.at.add(0, 0.6, 0).subtract(from);
            double along = to.dotProduct(dir);
            if (along < 0 || along > 24 || to.subtract(dir.multiply(along)).length() > 3.2) continue;
            s.ttl -= 14;
            w.spawnParticles(ParticleTypes.CLOUD, s.at.x, s.at.y + 0.8, s.at.z, 2, 0.4, 0.3, 0.4, 0.02);
            if (s.ttl > 0) continue;
            SPOTS.remove(s);
            putOut(w, s);
            out++;
            if (p != null) {
                Life.earn(p, 15, "потушен пожар на улице");
                p.sendMessage(Text.literal("Огонь потушен").formatted(Formatting.AQUA), true);
            }
        }
        return out;
    }

    private static void putOut(ServerWorld w, Spot s) {
        w.spawnParticles(ParticleTypes.POOF, s.at.x, s.at.y + 0.5, s.at.z, 16, 0.5, 0.4, 0.5, 0.04);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, s.at.x, s.at.y + 0.8, s.at.z, 6, 0.4, 0.4, 0.4, 0.03);
        w.playSound(null, BlockPos.ofFloored(s.at), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1f, 1f);
    }

    public static void tick(MinecraftServer server) {
        if (SPOTS.isEmpty()) return;
        long t = server.getTicks();
        if (t % 2 != 0) return;
        List<Spot> spread = null;
        // копия: взрыв машины/обрушение внутри цикла может поджечь новое место (ignite → SPOTS.add)
        for (Spot s : new ArrayList<>(SPOTS)) {
            if (!SPOTS.contains(s)) continue;
            ServerWorld w = s.w;
            s.age += 2;
            s.ttl -= 2;
            if (!w.isChunkLoaded(BlockPos.ofFloored(s.at))) s.ttl = 0;
            if (s.block != null && w.getBlockState(s.block).isAir()) { // предмет сбили/он сгорел — дотлевает на земле
                s.ttl = Math.min(s.ttl, 80);
            }
            if (w.hasRain(BlockPos.ofFloored(s.at).up())) s.ttl -= 4;
            if (s.ttl <= 0) {
                SPOTS.remove(s);
                if (s.block != null && !w.getBlockState(s.block).isAir() && flammable(w, s.block, w.getBlockState(s.block))) {
                    // сгорело: обугленный предмет исчезает, позже город его заменит
                    Vec3d c = Vec3d.ofCenter(s.block);
                    w.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.03);
                    w.spawnParticles(ParticleTypes.ASH, c.x, c.y, c.z, 20, 0.4, 0.3, 0.4, 0.02);
                    Destruction.breakBlock(w, s.block, Blocks.AIR.getDefaultState(), 300);
                    w.playSound(null, s.block, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 0.7f);
                } else putOut(w, s);
                continue;
            }
            double x = s.at.x, y = s.at.y, z = s.at.z;
            boolean big = s.block == null;
            float k = Math.min(1f, s.ttl / 100f) * Math.min(1f, s.age / 30f + 0.3f); // разгорается и затухает
            w.spawnParticles(ParticleTypes.FLAME, x, y + 0.4, z, 1 + (int) (3 * k), big ? 0.7 : 0.3, 0.3, big ? 0.7 : 0.3, 0.02);
            if (t % 4 == 0) w.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.2, z, 1, 0.3, 0.2, 0.3, 0.03);
            if (t % 8 == 0) w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 1.5, z, 1, 0.2, 0.1, 0.2, 0.01); // столб дыма
            if (t % 12 == 0 && w.random.nextInt(3) == 0) w.spawnParticles(ParticleTypes.LAVA, x, y + 0.3, z, 1, 0.3, 0.1, 0.3, 0);
            if (t % 30 == 0) w.playSound(null, BlockPos.ofFloored(s.at), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 1.3f, 0.9f);
            if (t % 20 == 0) {
                Box hot = new Box(s.at, s.at).expand(big ? 1.4 : 0.9, 1.2, big ? 1.4 : 0.9).offset(0, 0.6, 0);
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, hot, e -> e.isAlive() && !e.isFireImmune())) e.setOnFireFor(3);
                for (CarEntity car : w.getEntitiesByClass(CarEntity.class, hot.expand(1), c -> !c.wrecked())) car.hitByBullet(3f, null); // машина рядом с огнём дымит и рано или поздно взорвётся
            }
            if (t % 40 == 0) for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, new Box(s.at, s.at).expand(10), c -> c.isAlive() && !c.hasVehicle())) c.panic(s.at);
            // огонь перекидывается на соседние горючие предметы
            if (t % 40 == 20 && s.age > 120 && w.random.nextInt(3) == 0 && SPOTS.size() < MAX) {
                BlockPos o = BlockPos.ofFloored(s.at);
                for (int i = 0; i < 6; i++) {
                    BlockPos p = o.add(w.random.nextInt(5) - 2, w.random.nextInt(3) - 1, w.random.nextInt(5) - 2);
                    BlockState st = w.getBlockState(p);
                    if (!st.isAir() && flammable(w, p, st) && !burning(p)) {
                        if (spread == null) spread = new ArrayList<>();
                        spread.add(new Spot(w, Vec3d.ofBottomCenter(p), p, 25 * 20 + w.random.nextInt(300)));
                        break;
                    }
                }
            }
        }
        if (spread != null) for (Spot s : spread) {
            if (SPOTS.size() >= MAX || burning(s.block)) break;
            ignite(s.w, s.at, s.ttl / 20, s.block);
        }
    }
}
