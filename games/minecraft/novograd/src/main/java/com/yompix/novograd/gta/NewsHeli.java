package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.vehicle.HeliEntity;
import com.yompix.novograd.vehicle.Vehicles;
import com.yompix.novograd.world.Island;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.*;

/**
 * Вертолёт телеканала «Новоград-24»: при большой погоне (розыск ★★★ и выше) прилетает красно-белый вертолёт,
 * кружит выше полиции и снимает тебя (пилот и оператор, без оружия). Пока он рядом — в углу экрана
 * окно «В ЭФИРЕ» (client/LiveHud), а новости рассказывают о погоне. Ушёл от полиции — вертолёт улетает.
 */
public final class NewsHeli {
    private NewsHeli() {}

    public static final int PAINT = 0xD81E2A;
    private static final String TAG = "news_heli";

    private static final class Cam {
        UUID heli;
        float orbit;
        int lost, hot;
        long started;
    }

    private static final Map<UUID, Cam> CAMS = new HashMap<>();
    private static final Random R = new Random();

    public static void tick(MinecraftServer s) {
        long t = s.getTicks();
        if (t % 20 != 9) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            int stars = p.isSpectator() ? 0 : Wanted.stars(p);
            Cam c = CAMS.get(p.getUuid());
            ServerWorld w = p.getServerWorld();
            if (c == null) {
                if (stars < 3 || FightClub.inside(p)) continue;
                c = new Cam();
                CAMS.put(p.getUuid(), c);
            }
            HeliEntity h = c.heli == null ? null : w.getEntity(c.heli) instanceof HeliEntity e ? e : null;
            if (stars >= 3) {
                c.lost = 0;
                if (h == null || h.wrecked()) {
                    if (++c.hot >= 6) { // через ~6 с после третьей звезды
                        h = spawn(w, p, c);
                        c.hot = 0;
                    }
                }
            } else if (++c.lost > 25 || h == null) { // ушёл от полиции — эфир закончен
                if (h != null) h.ai = leave(p.getUuid());
                CAMS.remove(p.getUuid());
                send(p, null);
                continue;
            }
            send(p, h);
        }
        if (t % 200 == 9) cleanup(s);
    }

    /** Для проверки (/pulse heli). */
    public static void spawnFor(ServerPlayerEntity p) {
        Cam c = CAMS.computeIfAbsent(p.getUuid(), u -> new Cam());
        spawn(p.getServerWorld(), p, c);
    }

    private static HeliEntity spawn(ServerWorld w, ServerPlayerEntity p, Cam c) {
        double a = R.nextDouble() * Math.PI * 2;
        double x = p.getX() + Math.cos(a) * 70, z = p.getZ() + Math.sin(a) * 70;
        int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        double y = Math.max(p.getY() + 30, top + 16);
        HeliEntity h = new HeliEntity(Vehicles.HELI, w);
        h.setVariant(2);
        h.paint(PAINT);
        h.refreshPositionAndAngles(x, y, z, (float) Math.toDegrees(Math.atan2(-(p.getX() - x), p.getZ() - z)), 0);
        h.rotor = 1;
        h.addCommandTag(TAG);
        h.setCustomName(net.minecraft.text.Text.literal("Новоград-24"));
        w.spawnEntity(h);
        for (String name : new String[]{"Пилот «Новоград-24»", "Оператор «Новоград-24»"}) {
            CitizenEntity n = new CitizenEntity(ModContent.CITIZEN, w);
            n.setup(CitizenEntity.randomSkin(w.getRandom()), CitizenEntity.Role.CITIZEN, false, name);
            n.refreshPositionAndAngles(x, y, z, 0, 0);
            n.addCommandTag(TAG);
            w.spawnEntity(n);
            n.startRiding(h, true);
        }
        c.heli = h.getUuid();
        c.orbit = (float) a;
        c.started = w.getTime();
        h.ai = orbit(p.getUuid(), c);
        String where = NewsTv.zoneName(Island.zone(p.getBlockX(), p.getBlockZ()));
        News.post(w.getServer(), "Прямой эфир с вертолёта: полиция преследует опасного преступника — " + where, "ЭКСТРЕННО");
        return h;
    }

    /** Кружим над целью выше полицейского вертолёта, нос (камера) — на цель. */
    private static java.util.function.Consumer<HeliEntity> orbit(UUID target, Cam c) {
        return h -> {
            ServerWorld w = (ServerWorld) h.getWorld();
            ServerPlayerEntity t = w.getServer().getPlayerManager().getPlayer(target);
            if (t == null || t.getWorld() != w || !CAMS.containsKey(target)) {
                h.ai = leave(target);
                return;
            }
            c.orbit += 0.008f;
            double r = 34;
            Vec3d want = t.getPos().add(Math.cos(c.orbit) * r, 0, Math.sin(c.orbit) * r);
            int topHere = w.getTopY(Heightmap.Type.MOTION_BLOCKING, h.getBlockX(), h.getBlockZ());
            int topThere = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(want.x), (int) Math.floor(want.z));
            double wantY = Math.max(t.getY() + 26, Math.max(topHere, topThere) + 12);
            fly(h, want, wantY, t.getPos());
        };
    }

    private static java.util.function.Consumer<HeliEntity> leave(UUID who) {
        int[] ticks = {0};
        return h -> {
            if (++ticks[0] > 240) {
                for (Entity e : new ArrayList<>(h.getPassengerList())) if (!(e instanceof ServerPlayerEntity)) e.discard();
                h.discard();
                return;
            }
            Vec3d f = Vec3d.fromPolar(0, h.getYaw());
            fly(h, h.getPos().add(f.multiply(30)), h.getY() + 8, null);
        };
    }

    private static void fly(HeliEntity h, Vec3d want, double wantY, Vec3d look) {
        h.rotor = Math.max(h.rotor, 0.9f);
        Vec3d v = h.getVelocity();
        double dx = want.x - h.getX(), dz = want.z - h.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double sp = Math.min(0.9, dist * 0.06);
        double tx = dist > 0.01 ? dx / dist * sp : 0, tz = dist > 0.01 ? dz / dist * sp : 0;
        double vx = MathHelper.lerp(0.06, v.x, tx), vz = MathHelper.lerp(0.06, v.z, tz);
        double vy = MathHelper.clamp((wantY - h.getY()) * 0.05, -0.3, 0.35);
        double lx = look != null ? look.x - h.getX() : vx, lz = look != null ? look.z - h.getZ() : vz;
        float yawWant = (float) Math.toDegrees(Math.atan2(-lx, lz));
        h.setYaw(h.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yawWant - h.getYaw()), -3, 3));
        h.setVelocity(vx, vy, vz);
        h.move(MovementType.SELF, h.getVelocity());
        if (h.horizontalCollision) h.setVelocity(h.getVelocity().x, 0.4, h.getVelocity().z);
    }

    private static void send(ServerPlayerEntity p, HeliEntity h) {
        NbtCompound n = new NbtCompound();
        n.putBoolean("on", h != null && !h.wrecked() && h.squaredDistanceTo(p) < 160 * 160);
        n.putInt("id", h == null ? -1 : h.getId());
        n.putInt("stars", Wanted.stars(p));
        n.putString("zone", NewsTv.zoneName(Island.zone(p.getBlockX(), p.getBlockZ())));
        Pulse.send(p, "live", n);
    }

    /** После перезапуска у вертолёта нет автопилота — убираем «сирот». */
    private static void cleanup(MinecraftServer s) {
        Set<UUID> mine = new HashSet<>();
        for (Cam c : CAMS.values()) if (c.heli != null) mine.add(c.heli);
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            ServerWorld w = p.getServerWorld();
            for (HeliEntity h : w.getEntitiesByClass(HeliEntity.class, p.getBoundingBox().expand(256), e -> e.getCommandTags().contains(TAG))) {
                if (mine.contains(h.getUuid()) || h.ai != null) continue;
                if (h.getPassengerList().stream().anyMatch(e -> e instanceof ServerPlayerEntity)) { h.removeCommandTag(TAG); continue; } // угнали — теперь твой
                for (Entity e : new ArrayList<>(h.getPassengerList())) e.discard();
                h.discard();
            }
        }
    }

    public static void onLeave(ServerPlayerEntity p) {
        Cam c = CAMS.remove(p.getUuid());
        if (c != null && c.heli != null && p.getServerWorld().getEntity(c.heli) instanceof HeliEntity h) h.ai = leave(p.getUuid());
    }
}
