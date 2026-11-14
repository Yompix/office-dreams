package com.yompix.glukhoman.horror;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.block.LampBlock;
import com.yompix.glukhoman.entity.PoleEntity;
import com.yompix.glukhoman.net.Net;
import com.yompix.glukhoman.story.Story;
import com.yompix.glukhoman.story.StoryState;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Режиссёр страха. Чередует затишье и тревогу; по главе и рассудку выбирает событие для каждого игрока:
 * шаги и треск веток вдали, шёпот, стук, двери за спиной, гаснущий свет, фигура у кромки леса, голос из леса.
 * Скримеры редкие и только если игрок их не отключил.
 */
public final class Director {
    private Director() {}

    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Long> LAST_BIG = new HashMap<>();
    public static final Set<UUID> NO_SCARES = new HashSet<>();
    private static long calmUntil;
    private static final Random R = Random.create();

    /** Затишье для всех (после погони, после сцены). */
    public static void calm(MinecraftServer s, int ticks) {
        calmUntil = s.getTicks() + ticks;
    }

    public static void tick(MinecraftServer server) {
        long now = server.getTicks();
        if (now % 20 != 0 || now < calmUntil) return;
        StoryState st = StoryState.get(server.getOverworld());
        if (st.chapter == 0 || !st.ending.isEmpty() || Story.sceneRunning()) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            long next = NEXT.computeIfAbsent(p.getUuid(), u -> now + 400);
            if (now < next) continue;
            float san = Sanity.get(p);
            boolean safe = Story.inSafe(p);
            // чем ниже рассудок и выше сложность — тем чаще
            int base = st.difficulty == 0 ? 900 : st.difficulty == 2 ? 420 : 600;
            base = (int) (base * (0.5 + san / 200.0));
            NEXT.put(p.getUuid(), now + base + R.nextInt(base / 2 + 1));
            if (safe && R.nextFloat() < 0.7f) continue;
            fire(p, st, san);
        }
    }

    static void fire(ServerPlayerEntity p, StoryState st, float san) {
        int ch = st.chapter;
        boolean underground = ch == 4 || Story.inZone(p, "bunker");
        float r = R.nextFloat();
        boolean big = p.getServer().getTicks() - LAST_BIG.getOrDefault(p.getUuid(), -99999L) > 2400;
        if (underground) {
            if (r < 0.3f) far(p, "branch_snap", 0.5f, 14);
            else if (r < 0.55f) far(p, "choir_hum", 0.7f, 30);
            else if (r < 0.75f) steps(p);
            else if (r < 0.85f && unseenDecor(p)) return;
            else if (san < 60) whisper(p);
            else far(p, "steps_far", 0.8f, 20);
            return;
        }
        if (ch == 1) {
            if (r < 0.35f) far(p, "branch_snap", 1f, 24);
            else if (r < 0.6f) far(p, "steps_far", 1f, 28);
            else if (r < 0.75f) far(p, "wind_gust", 1f, 10);
            else if (big && apparition(p, false)) LAST_BIG.put(p.getUuid(), (long) p.getServer().getTicks());
            else far(p, "distant_scream", 0.6f, 60);
            return;
        }
        // главы 2, 3, 5 на поверхности
        if (r < 0.1f && unseenDecor(p)) return;
        if (r < 0.2f && big && ch != 3 && doppel(p)) {
            LAST_BIG.put(p.getUuid(), (long) p.getServer().getTicks());
            return;
        }
        if (r < 0.28f && friendCall(p)) return;
        if (r < 0.15f) far(p, "branch_snap", 1f, 20);
        else if (r < 0.27f) steps(p);
        else if (r < 0.39f && doorBehind(p)) return;
        else if (r < 0.51f && lightsOut(p)) return;
        else if (r < 0.6f) knock(p);
        else if (r < 0.7f) mimic(p);
        else if (r < 0.8f && san < 55) whisper(p);
        else if (big && ch != 3 && san < 35 && !NO_SCARES.contains(p.getUuid()) && st.difficulty > 0) {
            LAST_BIG.put(p.getUuid(), (long) p.getServer().getTicks());
            hush(p, 60);  // тишина… и только потом
            Sched.after(60, () -> apparition(p, true));
        } else if (big && ch != 3 && apparition(p, false)) LAST_BIG.put(p.getUuid(), (long) p.getServer().getTicks());
        else far(p, "distant_scream", 0.5f, 70);
    }

    /** Звук только этому игроку, в точке позади-сбоку на расстоянии dist. */
    public static void far(ServerPlayerEntity p, String sound, float vol, double dist) {
        Vec3d look = p.getRotationVec(1f).multiply(1, 0, 1).normalize();
        double ang = Math.PI * (0.6 + R.nextDouble() * 0.8) * (R.nextBoolean() ? 1 : -1);
        Vec3d dir = new Vec3d(look.x * Math.cos(ang) - look.z * Math.sin(ang), 0, look.x * Math.sin(ang) + look.z * Math.cos(ang));
        Vec3d at = p.getPos().add(dir.multiply(dist)).add(0, 1, 0);
        snd(p, sound, at, vol, 0.85f + R.nextFloat() * 0.3f);
    }

    public static void snd(ServerPlayerEntity p, String sound, Vec3d at, float vol, float pitch) {
        NbtCompound m = Net.msg("snd");
        m.putString("id", sound);
        m.putDouble("x", at.x);
        m.putDouble("y", at.y);
        m.putDouble("z", at.z);
        m.putFloat("v", vol);
        m.putFloat("p", pitch);
        Net.send(p, m);
    }

    static void steps(ServerPlayerEntity p) {
        Net.send(p, Net.msg("steps"));
    }

    static void whisper(ServerPlayerEntity p) {
        Vec3d back = p.getPos().subtract(p.getRotationVec(1f).multiply(1.5, 0, 1.5)).add(0, 1.6, 0);
        snd(p, "voice.whisper_" + (1 + R.nextInt(4)), back, 0.6f, 1f);
    }

    static void mimic(ServerPlayerEntity p) {
        far(p, R.nextBoolean() ? "voice.mimic_1" : "voice.mimic_2", 0.9f, 30);
    }

    static void knock(ServerPlayerEntity p) {
        BlockPos d = findDoor(p, 12, false);
        if (d == null) {
            far(p, "knock", 0.8f, 10);
            return;
        }
        p.getServerWorld().playSound(null, d, GContent.sound("knock"), SoundCategory.AMBIENT, 1f, 0.9f + R.nextFloat() * 0.2f);
    }

    /** Дверь за спиной открывается или закрывается — если её никто не видит. */
    static boolean doorBehind(ServerPlayerEntity p) {
        BlockPos d = findDoor(p, 14, true);
        if (d == null) return false;
        ServerWorld w = p.getServerWorld();
        BlockState s = w.getBlockState(d);
        boolean open = !s.get(DoorBlock.OPEN);
        w.setBlockState(d, s.with(DoorBlock.OPEN, open), 10);
        BlockState up = w.getBlockState(d.up());
        if (up.getBlock() instanceof DoorBlock) w.setBlockState(d.up(), up.with(DoorBlock.OPEN, open), 10);
        w.playSound(null, d, GContent.sound("door_creak"), SoundCategory.BLOCKS, 0.6f, 0.9f);
        return true;
    }

    static BlockPos findDoor(ServerPlayerEntity p, int r, boolean behind) {
        ServerWorld w = p.getServerWorld();
        BlockPos c = p.getBlockPos();
        Vec3d look = p.getRotationVec(1f);
        for (BlockPos q : BlockPos.iterateOutwards(c, r, 3, r)) {
            BlockState s = w.getBlockState(q);
            if (!(s.getBlock() instanceof DoorBlock) || s.get(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            if (s.getBlock() == net.minecraft.block.Blocks.IRON_DOOR) continue;
            Vec3d to = Vec3d.ofCenter(q).subtract(p.getEyePos());
            if (to.length() < 4) continue;
            if (behind && to.normalize().dotProduct(look) > -0.2) continue;
            if (behind && seenByOthers(p, Vec3d.ofCenter(q))) continue;
            if (q.equals(StoryState.get(w).point("anna_door"))) continue;
            return q.toImmutable();
        }
        return null;
    }

    static boolean seenByOthers(ServerPlayerEntity p, Vec3d pos) {
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList()) {
            if (o == p) continue;
            Vec3d to = pos.subtract(o.getEyePos());
            if (to.length() < 24 && to.normalize().dotProduct(o.getRotationVec(1f)) > 0.5) return true;
        }
        return false;
    }

    /** Ближние лампы мигают и гаснут на несколько секунд. */
    static boolean lightsOut(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        StoryState st = StoryState.get(w);
        var near = new java.util.ArrayList<BlockPos>();
        for (var e : st.lamps.entrySet()) {
            if (e.getKey().equals("mast")) continue;
            for (long l : e.getValue()) {
                BlockPos q = BlockPos.fromLong(l);
                if (q.getSquaredDistance(p.getPos()) < 400 && w.getBlockState(q).getBlock() instanceof LampBlock && w.getBlockState(q).get(LampBlock.LIT)) near.add(q);
            }
        }
        if (near.isEmpty()) return false;
        flicker(w, near, 40 + R.nextInt(80));
        return true;
    }

    public static void flicker(ServerWorld w, java.util.List<BlockPos> lamps, int darkTicks) {
        int[] pattern = {0, 3, 5, 9, 12, 16};
        for (int i = 0; i < pattern.length; i++) {
            boolean lit = i % 2 == 1;
            Sched.after(pattern[i] + 1, () -> setLamps(w, lamps, lit));
        }
        for (BlockPos q : lamps) w.playSound(null, q, GContent.sound("lamp_buzz"), SoundCategory.BLOCKS, 0.7f, 1f);
        Sched.after(18 + darkTicks, () -> setLamps(w, lamps, true));
    }

    public static void setLamps(ServerWorld w, java.util.List<BlockPos> lamps, boolean lit) {
        for (BlockPos q : lamps) {
            BlockState s = w.getBlockState(q);
            if (s.getBlock() instanceof LampBlock) w.setBlockState(q, s.with(LampBlock.LIT, lit), 3);
        }
    }

    /** Тишина перед скримером: фон глохнет. */
    public static void hush(ServerPlayerEntity p, int ticks) {
        NbtCompound m = Net.msg("hush");
        m.putInt("t", ticks);
        Net.send(p, m);
    }

    /** Полноэкранный скример — модель крупным планом (если игрок их не отключил). */
    public static void jumpscare(ServerPlayerEntity p, String kind) {
        NbtCompound m = Net.msg("jumpscare");
        m.putString("k", kind);
        Net.send(p, m);
    }

    /** Голос друга: из леса зовёт голосом и с ником другого игрока, который сейчас далеко. */
    static boolean friendCall(ServerPlayerEntity p) {
        var others = p.getServer().getPlayerManager().getPlayerList().stream().filter(o -> o != p && !o.isSpectator() && o.squaredDistanceTo(p) > 25 * 25).toList();
        if (others.isEmpty()) return false;
        ServerPlayerEntity f = others.get(R.nextInt(others.size()));
        Vec3d look = p.getRotationVec(1f).multiply(1, 0, 1).normalize();
        Vec3d at = p.getPos().subtract(look.multiply(26)).add(0, 1, 0);
        snd(p, R.nextBoolean() ? "voice.mimic_1" : "voice.mimic_2", at, 1f, 0.8f);
        NbtCompound m = Net.msg("voice");
        m.putString("id", "");
        m.putString("who", f.getName().getString());
        m.putString("text", R.nextBoolean() ? "Эй! Сюда! Я нашёл…" : "Помоги… я здесь, за деревьями…");
        m.putInt("dur", 60);
        m.putBoolean("global", true);
        Net.send(p, m);
        return true;
    }

    /** Двойник: облик друга (в кооперативе) или Анны стоит вдали и машет. */
    static boolean doppel(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        if (!w.getEntitiesByClass(com.yompix.glukhoman.entity.DoppelEntity.class, new Box(p.getBlockPos()).expand(90), e -> true).isEmpty()) return false;
        var others = p.getServer().getPlayerManager().getPlayerList().stream().filter(o -> o != p && !o.isSpectator()).toList();
        Vec3d look = p.getRotationVec(1f).multiply(1, 0, 1).normalize();
        for (int i = 0; i < 10; i++) {
            double ang = Math.toRadians(R.nextInt(50) - 25);
            Vec3d dir = new Vec3d(look.x * Math.cos(ang) - look.z * Math.sin(ang), 0, look.x * Math.sin(ang) + look.z * Math.cos(ang));
            Vec3d at = p.getPos().add(dir.multiply(22 + R.nextInt(10)));
            int x = MathHelper.floor(at.x), z = MathHelper.floor(at.z);
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - p.getBlockY()) > 5) continue;
            var d = GContent.DOPPEL.create(w);
            if (d == null) return false;
            d.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0, 0);
            if (!p.canSee(d)) continue;
            d.setMimic(others.isEmpty() ? "@anna" : others.get(R.nextInt(others.size())).getName().getString());
            w.spawnEntity(d);
            return true;
        }
        return false;
    }

    /** Кукла поворачивается к тебе, фото меняется — пока никто не смотрит. */
    static boolean unseenDecor(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        StoryState st = StoryState.get(w);
        for (String g : new String[]{"doll", "photo"}) {
            for (long l : st.lamps.getOrDefault(g, java.util.List.of())) {
                BlockPos q = BlockPos.fromLong(l);
                if (q.getSquaredDistance(p.getPos()) > 16 * 16) continue;
                Vec3d to = Vec3d.ofCenter(q).subtract(p.getEyePos());
                if (to.normalize().dotProduct(p.getRotationVec(1f)) > 0.3 || seenByOthers(p, Vec3d.ofCenter(q))) continue;
                BlockState s = w.getBlockState(q);
                if (!(s.getBlock() instanceof com.yompix.glukhoman.block.DecorBlock)) continue;
                if (g.equals("doll")) {
                    net.minecraft.util.math.Direction face = net.minecraft.util.math.Direction.getFacing(-to.x, 0, -to.z);
                    w.setBlockState(q, s.with(net.minecraft.block.HorizontalFacingBlock.FACING, face), 3);
                } else {
                    int v = s.get(com.yompix.glukhoman.block.DecorBlock.VARIANT);
                    if (v >= 2) continue;
                    w.setBlockState(q, s.with(com.yompix.glukhoman.block.DecorBlock.VARIANT, v + 1), 3);
                }
                return true;
            }
        }
        return false;
    }

    /** Жердь-видение у кромки леса: исчезает, когда на неё смотрят. jump — скример вплотную за спиной. */
    public static boolean apparition(ServerPlayerEntity p, boolean jump) {
        ServerWorld w = p.getServerWorld();
        if (!w.getEntitiesByClass(PoleEntity.class, new Box(p.getBlockPos()).expand(80), e -> e.isApparition()).isEmpty()) return false;
        Vec3d look = p.getRotationVec(1f).multiply(1, 0, 1).normalize();
        for (int tries = 0; tries < 12; tries++) {
            double ang, dist;
            if (jump) {
                ang = Math.PI + (R.nextDouble() - 0.5) * 0.6;
                dist = 2.5;
            } else {
                ang = Math.toRadians(45 + R.nextInt(30)) * (R.nextBoolean() ? 1 : -1);
                dist = 24 + R.nextInt(18);
            }
            Vec3d dir = new Vec3d(look.x * Math.cos(ang) - look.z * Math.sin(ang), 0, look.x * Math.sin(ang) + look.z * Math.cos(ang));
            Vec3d at = p.getPos().add(dir.multiply(dist));
            int x = MathHelper.floor(at.x), z = MathHelper.floor(at.z);
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - p.getBlockY()) > 6) continue;
            PoleEntity e = GContent.POLE.create(w);
            if (e == null) return false;
            e.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0, 0);
            if (!jump && !p.canSee(e)) continue;
            if (!jump) {   // ищем ствол рядом: встанет за ним и выглянет
                BlockPos log = null;
                for (BlockPos q : BlockPos.iterateOutwards(new BlockPos(x, y + 1, z), 4, 1, 4))
                    if (w.getBlockState(q).isIn(net.minecraft.registry.tag.BlockTags.LOGS)) { log = q.toImmutable(); break; }
                if (log != null) {
                    Vec3d away = Vec3d.ofCenter(log).subtract(p.getPos()).multiply(1, 0, 1).normalize();
                    Vec3d side = new Vec3d(-away.z, 0, away.x).multiply(R.nextBoolean() ? 0.9 : -0.9);
                    Vec3d spot = Vec3d.ofCenter(log).add(away.multiply(1.1)).add(side);
                    e.refreshPositionAndAngles(spot.x, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(spot.x), MathHelper.floor(spot.z)), spot.z, 0, 0);
                    e.setPeek(true);
                }
            }
            e.makeApparition(jump ? p.getUuid() : null);
            e.lookAtPlayer(p);
            w.spawnEntity(e);
            return true;
        }
        return false;
    }
}
