package com.yompix.glukhoman.story;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.entity.ResidentEntity;
import com.yompix.glukhoman.entity.Seen;
import com.yompix.glukhoman.entity.TrainEntity;
import com.yompix.glukhoman.net.Net;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Камера на сервере: кто держит её у глаза, ИК-режим, батарея (прочность предмета), что попадает в кадр.
 * Пока камера поднята — идёт запись: сервер копит, кто сколько тиков был в кадре; опустил — сюжет ложится на кассету.
 */
public final class Cam {
    private Cam() {}

    static final class State {
        boolean up, ir;
        int ticks;
        final Map<String, Integer> subj = new LinkedHashMap<>();
    }

    static final Map<UUID, State> S = new HashMap<>();
    /** Угол кадра (видоискатель уже глаза): cos половины угла. */
    static final double FRAME_COS = Math.cos(Math.toRadians(24));

    static State s(ServerPlayerEntity p) {
        return S.computeIfAbsent(p.getUuid(), k -> new State());
    }

    public static boolean up(ServerPlayerEntity p) {
        State s = S.get(p.getUuid());
        return s != null && s.up;
    }

    public static Map<String, Integer> session(ServerPlayerEntity p) {
        return s(p).subj;
    }

    static ItemStack camera(ServerPlayerEntity p) {
        ItemStack m = p.getMainHandStack();
        return m.isOf(GContent.CAMCORDER) ? m : null;
    }

    public static void toggle(ServerPlayerEntity p) {
        if (up(p)) lower(p, true);
        else raise(p);
    }

    public static boolean raise(ServerPlayerEntity p) {
        ItemStack c = camera(p);
        if (c == null) return false;
        if (c.getDamage() >= c.getMaxDamage() - 1 && !swapBattery(p, c)) {
            Story.msg(p, "Батарея села. Нужна новая.");
            Story.sound(p.getBlockPos(), "battery_dead", 0.8f, 1f);
            return false;
        }
        State s = s(p);
        s.up = true;
        s.ticks = 0;
        s.subj.clear();
        p.getServerWorld().playSound(null, p.getBlockPos(), GContent.sound("tape_click"), SoundCategory.PLAYERS, 0.5f, 1.2f);
        send(p);
        Story.camRaised(p);
        return true;
    }

    static boolean swapBattery(ServerPlayerEntity p, ItemStack c) {
        if (Story.take(p, GContent.BATTERY, 1) == 0) return false;
        c.setDamage(0);
        Story.msg(p, "Поменял батарею.");
        return true;
    }

    /** Опустить камеру. commit — записать сюжет на кассету. */
    public static void lower(ServerPlayerEntity p, boolean commit) {
        State s = S.get(p.getUuid());
        if (s == null || !s.up) return;
        s.up = false;
        s.ir = false;
        send(p);
        Night.heat(p, 2);
        if (commit) commit(p, s);
        s.subj.clear();
    }

    static void commit(ServerPlayerEntity p, State s) {
        boolean special = !Story.recKind().equals("footage");
        if (s.ticks < 30 || (!special && s.subj.isEmpty())) return;
        StoryState st = StoryState.get(p.getServerWorld());
        String msg = Tapes.commit(st, Story.recKind(), Story.recCtx(), Story.recTitle(), s.subj);
        if (msg != null) Story.msg(p, msg);
    }

    public static void setIr(ServerPlayerEntity p, boolean on) {
        State s = s(p);
        if (!s.up) return;
        s.ir = on;
        send(p);
    }

    static void send(ServerPlayerEntity p) {
        State s = s(p);
        NbtCompound m = Net.msg("cam");
        m.putBoolean("up", s.up);
        m.putBoolean("ir", s.ir);
        Net.send(p, m);
    }

    /** Каждый тик: батарея, что в кадре. */
    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : Story.players()) {
            State s = S.get(p.getUuid());
            if (s == null || !s.up) continue;
            ItemStack c = camera(p);
            if (c == null || !p.isAlive()) {
                lower(p, true);
                continue;
            }
            s.ticks++;
            if (!(p instanceof net.fabricmc.fabric.api.entity.FakePlayer)) {
                c.setDamage(Math.min(c.getMaxDamage() - 1, c.getDamage() + (s.ir ? 3 : 1)));
                if (c.getDamage() >= c.getMaxDamage() - 1 && !swapBattery(p, c)) {
                    Story.msg(p, "Батарея села. Камера погасла.");
                    lower(p, true);
                    continue;
                }
            }
            if (s.ticks % 5 == 0) scan(p, s);
        }
    }

    static void scan(ServerPlayerEntity p, State s) {
        ServerWorld w = p.getServerWorld();
        for (Entity e : w.getOtherEntities(p, new Box(p.getBlockPos()).expand(48), e -> e instanceof ResidentEntity || e instanceof ServerPlayerEntity || e instanceof TrainEntity)) {
            if (e instanceof Seen sn && sn.seen() == Seen.EYE_ONLY) continue;
            if (!inFrame(p, e.getPos().add(0, e.getHeight() * 0.6, 0), e instanceof TrainEntity ? 120 : 40, e instanceof TrainEntity)) continue;
            String k = e instanceof ResidentEntity r ? r.who() : e instanceof TrainEntity ? "train" : "player:" + e.getName().getString();
            s.subj.merge(k, 5, Integer::sum);
        }
        StoryState st = StoryState.get(w);
        for (String t : new String[]{"t_platform", "t_switch", "t_crossing"}) {
            BlockPos q = st.point(t);
            if (q != null && inFrame(p, Vec3d.ofCenter(q), 45, true)) s.subj.merge(t, 5, Integer::sum);
        }
    }

    /** Точка в кадре: в пределах дальности, в угле видоискателя, без стены между (сквозь стекло и листву — видно). */
    public static boolean inFrame(ServerPlayerEntity p, Vec3d at, double range, boolean ignoreWalls) {
        Vec3d eye = p.getEyePos();
        Vec3d to = at.subtract(eye);
        double d = to.length();
        if (d > range) return false;
        if (d > 1.2 && to.normalize().dotProduct(p.getRotationVec(1f)) < FRAME_COS) return false;
        if (ignoreWalls) return true;
        var hit = p.getWorld().raycast(new RaycastContext(eye, at, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, p));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS || hit.getPos().squaredDistanceTo(at) < 1.5;
    }

    /** Кто снимает это существо (камера поднята и смотрит на него). */
    public static ServerPlayerEntity filming(ServerWorld w, Entity e) {
        for (ServerPlayerEntity p : Story.players()) {
            if (!up(p)) continue;
            if (inFrame(p, e.getPos().add(0, e.getHeight() * 0.6, 0), 40, false)) return p;
        }
        return null;
    }

    public static void forget(ServerPlayerEntity p) {
        S.remove(p.getUuid());
    }

    public static void clear() {
        S.clear();
    }
}
