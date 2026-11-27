package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.10: служба игроков, как на RP-серверах (телефон → «Служба», по желанию — «На смену»).
 * <ul>
 *   <li><b>Полиция</b>: на радаре — ближайший игрок в розыске (звёзды). Задержание: стой рядом с ним 3 секунды
 *       (он пешком или его машина стоит) — колония по его звёздам, тебе — награда за каждую звезду.</li>
 *   <li><b>Пожарные</b>: на радаре — ближайший пожар; брандспойт работает и без подработки (в пожарной машине,
 *       ПКМ). Каждый потушенный очаг — премия.</li>
 *   <li><b>Скорая</b>: на радаре — раненый игрок (меньше 40 % здоровья); стой рядом 3 секунды — вылечил, премия
 *       (ему — бесплатно).</li>
 * </ul>
 * Правила против гриферства: на смену — только без розыска и не чаще раза в 5 минут; совершил преступление —
 * смена снимается; полицейский на смене не может ранить игрока без розыска; задержать можно только того, у кого
 * звёзды, и одного и того же — не чаще раза в 10 минут; в «Полицейских и ворах» служба не работает.
 */
public final class Duty {
    private Duty() {}

    public static final String[] ROLES = {"police", "fire", "medic"};
    public static final String[] NAMES = {"Полиция", "Пожарные", "Скорая"};

    static final class St {
        int role;
        long since;
        UUID target;
        int hold;
        int hose;
        int done;
    }

    private static final Map<UUID, St> ON = new HashMap<>();
    private static final Map<UUID, Long> TOGGLED = new HashMap<>(), ARRESTED = new HashMap<>();

    public static boolean on(ServerPlayerEntity p) {
        return ON.containsKey(p.getUuid());
    }

    public static boolean isFire(ServerPlayerEntity p) {
        St s = ON.get(p.getUuid());
        return s != null && s.role == 1;
    }

    public static boolean isPolice(ServerPlayerEntity p) {
        St s = ON.get(p.getUuid());
        return s != null && s.role == 0;
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Duty::tick);
        // полицейский на смене не ранит игрока без розыска
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amt) -> !(e instanceof ServerPlayerEntity victim && src.getAttacker() instanceof ServerPlayerEntity a
                && a != victim && isPolice(a) && Wanted.stars(victim) == 0));
        Achievements.ALL.putIfAbsent("duty_arrest", new String[]{"Служу Новограду", "Задержать игрока в розыске"});
        Achievements.ALL.putIfAbsent("duty_save", new String[]{"Ангел в белом", "Вылечить игрока на смене"});
        Achievements.ALL.putIfAbsent("duty_fire", new String[]{"Огнеборец", "Потушить 10 очагов на смене"});
    }

    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        switch (type) {
            case "duty_state" -> send(p);
            case "duty_on" -> start(p, n.getInt("role"));
            case "duty_off" -> off(p, "Смена сдана.");
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void start(ServerPlayerEntity p, int role) {
        if (role < 0 || role >= ROLES.length || on(p)) return;
        long now = p.getServerWorld().getTime();
        if (Wanted.stars(p) > 0) {
            FunUtil.hint(p, "С розыском на службу не берут", Formatting.RED);
            return;
        }
        if (CopsAndRobbers.immune(p)) return;
        if (now - TOGGLED.getOrDefault(p.getUuid(), -99999L) < 20 * 300) {
            FunUtil.hint(p, "На смену — не чаще раза в 5 минут", Formatting.GRAY);
            return;
        }
        if (Careers.onShift(p)) Careers.stop(p, "");
        TOGGLED.put(p.getUuid(), now);
        St s = new St();
        s.role = role;
        s.since = now;
        ON.put(p.getUuid(), s);
        FunUtil.title(p, NAMES[role].toUpperCase(), "на смене · цели на радаре");
        send(p);
    }

    public static void off(ServerPlayerEntity p, String why) {
        if (ON.remove(p.getUuid()) == null) return;
        TOGGLED.put(p.getUuid(), p.getServerWorld().getTime());
        FunUtil.target(p, null, "");
        if (!why.isEmpty()) FunUtil.hint(p, why, Formatting.GRAY);
        send(p);
    }

    /** Брандспойт на смене (без подработки VehicleJobs). */
    public static void hose(ServerPlayerEntity p, boolean on) {
        St s = ON.get(p.getUuid());
        if (s == null || s.role != 1 || !(p.getVehicle() instanceof CarEntity car) || car.getVariant() != 19) return;
        s.hose = on ? 8 : 0;
    }

    /** Fires.douse: очаг потушен игроком. */
    public static void onDouse(ServerPlayerEntity p) {
        St s = ON.get(p.getUuid());
        if (s == null || s.role != 1) return;
        s.done++;
        Life.earn(p, 60, "пожарный на смене");
        if (s.done >= 10) Achievements.give(p, "duty_fire");
    }

    private static void tick(MinecraftServer server) {
        if (ON.isEmpty()) return;
        int t = server.getTicks();
        for (var e : new HashMap<>(ON).entrySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null) {
                ON.remove(e.getKey());
                continue;
            }
            St s = e.getValue();
            if (Wanted.stars(p) > 0) { // нарушил сам — смена снята
                off(p, "Смена снята: ты сам в розыске");
                continue;
            }
            if (s.role == 1 && s.hose > 0) hoseTick(p, s);
            if (t % 10 != 1) continue;
            switch (s.role) {
                case 0 -> police(p, s);
                case 1 -> fire(p, s);
                default -> medic(p, s);
            }
        }
    }

    private static void police(ServerPlayerEntity p, St s) {
        ServerWorld w = p.getServerWorld();
        ServerPlayerEntity best = null;
        for (ServerPlayerEntity o : w.getPlayers())
            if (o != p && Wanted.stars(o) > 0 && !Prison.inside(o) && !on(o) && (best == null || o.squaredDistanceTo(p) < best.squaredDistanceTo(p))) best = o;
        if (best == null) {
            if (w.getTime() % 100 == 1) FunUtil.target(p, null, "");
            s.hold = 0;
            return;
        }
        FunUtil.target(p, best.getPos(), "★".repeat(Wanted.stars(best)) + " " + best.getName().getString());
        long now = w.getTime();
        boolean still = !best.hasVehicle() || best.getVehicle().getVelocity().horizontalLength() < 0.05 && best.getVehicle().getPos().squaredDistanceTo(best.getVehicle().prevX, best.getVehicle().prevY, best.getVehicle().prevZ) < 0.01;
        if (best.squaredDistanceTo(p) < 3 * 3 && still && now - ARRESTED.getOrDefault(best.getUuid(), -99999L) > 20 * 600) {
            s.hold += 10;
            if (s.hold == 10) {
                FunUtil.hint(best, "🚔 " + p.getName().getString() + ": «Стоять! Полиция!»", Formatting.RED);
                FunUtil.hint(p, "Задержание… не отходи", Formatting.AQUA);
            }
            if (s.hold >= 60) {
                int stars = Wanted.stars(best);
                ARRESTED.put(best.getUuid(), now);
                s.hold = 0;
                Prison.jail(best, stars, 0);
                Life.earn(p, 300 * stars, "задержание: " + best.getName().getString());
                Achievements.give(p, "duty_arrest");
                Social.post(p.getServer(), "@novograd_police", p.getName().getString() + " задержал(а) " + best.getName().getString() + " (" + "★".repeat(stars) + ")", p, 40 + stars * 20, 8);
                News.post(p.getServer(), "Патрульный " + p.getName().getString() + " задержал разыскиваемого " + best.getName().getString(), "КРИМИНАЛ");
            }
        } else s.hold = 0;
    }

    private static void fire(ServerPlayerEntity p, St s) {
        Vec3d best = null;
        for (Vec3d f : com.yompix.novograd.world.Fires.spots()) if (best == null || f.squaredDistanceTo(p.getPos()) < best.squaredDistanceTo(p.getPos())) best = f;
        if (best != null) FunUtil.target(p, best, "ПОЖАР");
        else if (p.getServerWorld().getTime() % 100 == 1) FunUtil.target(p, null, "");
    }

    private static void hoseTick(ServerPlayerEntity p, St s) {
        s.hose--;
        if (!(p.getVehicle() instanceof CarEntity car)) return;
        ServerWorld w = p.getServerWorld();
        Vec3d from = car.getPos().add(0, 2.6, 0), dir = p.getRotationVec(1);
        if (w.getTime() % 2 == 0)
            for (int i = 2; i < 22; i += 3) {
                Vec3d pt = from.add(dir.multiply(i)).add(0, -0.004 * i * i, 0);
                w.spawnParticles(ParticleTypes.SPLASH, pt.x, pt.y, pt.z, 3, 0.15, 0.15, 0.15, 0.1);
            }
        com.yompix.novograd.world.Fires.douse(w, from, dir, p);
    }

    private static void medic(ServerPlayerEntity p, St s) {
        ServerWorld w = p.getServerWorld();
        ServerPlayerEntity best = null;
        for (ServerPlayerEntity o : w.getPlayers())
            if (o != p && o.isAlive() && o.getHealth() < o.getMaxHealth() * 0.4f && (best == null || o.squaredDistanceTo(p) < best.squaredDistanceTo(p))) best = o;
        if (best == null) {
            if (w.getTime() % 100 == 1) FunUtil.target(p, null, "");
            s.hold = 0;
            return;
        }
        FunUtil.target(p, best.getPos(), "✚ " + best.getName().getString());
        if (best.squaredDistanceTo(p) < 2.5 * 2.5 && !p.hasVehicle()) {
            s.hold += 10;
            if (s.hold >= 60) {
                s.hold = 0;
                best.setHealth(best.getMaxHealth());
                FunUtil.title(best, "ВЫЛЕЧИЛИ", "скорая: " + p.getName().getString());
                Life.earn(p, 150, "скорая на смене");
                Achievements.give(p, "duty_save");
            }
        } else s.hold = 0;
    }

    public static void send(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        St s = ON.get(p.getUuid());
        n.putInt("role", s == null ? -1 : s.role);
        long left = 20 * 300 - (p.getServerWorld().getTime() - TOGGLED.getOrDefault(p.getUuid(), -99999L));
        n.putLong("cooldown", Math.max(0, left / 20));
        n.putInt("stars", Wanted.stars(p));
        int wanted = 0, injured = 0;
        for (ServerPlayerEntity o : p.getServerWorld().getPlayers()) {
            if (o == p) continue;
            if (Wanted.stars(o) > 0) wanted++;
            if (o.getHealth() < o.getMaxHealth() * 0.4f) injured++;
        }
        n.putInt("wanted", wanted);
        n.putInt("injured", injured);
        n.putInt("fires", com.yompix.novograd.world.Fires.count());
        LifeNet.send(p, "duty", n);
    }

    public static void onLeave(ServerPlayerEntity p) {
        ON.remove(p.getUuid());
    }
}
