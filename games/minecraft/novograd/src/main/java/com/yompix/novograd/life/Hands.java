package com.yompix.novograd.life;

import com.yompix.novograd.Novograd;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 0.9.8 «Руки»: взаимодействие с предметами — подошёл, встал лицом, руки делают дело (от первого и третьего лица,
 * с предметом в руке), потом результат. Сервер — главный: проверяет расстояние, держит время, сам применяет результат
 * в нужный тик и рассылает начало/конец игрокам рядом (у всех анимация идёт по тикам мира — синхронно).
 * Прервать: сдвинуться с места (клиент шлёт «hand_cancel»), получить урон, сесть в машину. Прерванное — без результата
 * (или с частичным: бензин — сколько налилось).
 *
 * <p>Виды и звуки — {@link HandKind}; руки и предметы рисует клиент (client/anim/HandsClient, HandsPose, HandsProps).
 */
public final class Hands {
    private Hands() {}

    /** Как далеко можно начать (до точки, где встанет игрок). */
    public static final double REACH = 3.6;
    /** Кому рассылать (блоки). */
    private static final double SEND = 96;

    public static final class Session {
        public final UUID who;
        public final HandKind kind;
        public final String variant;
        public final long t0;
        public final Vec3d anchor, target;
        public final float yaw;
        final TreeMap<Integer, Runnable> at = new TreeMap<>();
        @Nullable Runnable done, cancel;
        float hp;
        int ran = -1;
        /** Для частичного результата (бензин): сколько тиков прошло, когда прервали. */
        public int stoppedAt = -1;

        Session(UUID who, HandKind kind, String variant, long t0, Vec3d anchor, float yaw, Vec3d target) {
            this.who = who;
            this.kind = kind;
            this.variant = variant;
            this.t0 = t0;
            this.anchor = anchor;
            this.yaw = yaw;
            this.target = target;
        }

        /** Что сделать в тик t (после начала). */
        public Session at(int t, Runnable r) {
            at.merge(t, r, (a, b) -> () -> {
                a.run();
                b.run();
            });
            return this;
        }

        public Session onDone(Runnable r) {
            done = r;
            return this;
        }

        public Session onCancel(Runnable r) {
            cancel = r;
            return this;
        }
    }

    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    /** Для автотеста: сколько завершено/прервано. */
    public static int doneCount, cancelCount;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Hands::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> s.execute(() -> ACTIVE.remove(h.player.getUuid())));
    }

    @Nullable
    public static Session current(ServerPlayerEntity p) {
        return ACTIVE.get(p.getUuid());
    }

    /** Игрок занят «держащим» действием (банкомат, колонка…). */
    public static boolean busy(ServerPlayerEntity p) {
        Session s = ACTIVE.get(p.getUuid());
        return s != null && s.kind.blocking;
    }

    // ------------------------------------------------------------------ начало
    /** Встать перед блоком: точка на dist от его центра по направлению facing, лицом к блоку. */
    public static Vec3d front(BlockPos pos, Direction facing, double dist) {
        return new Vec3d(pos.getX() + 0.5 + facing.getOffsetX() * dist, pos.getY(), pos.getZ() + 0.5 + facing.getOffsetZ() * dist);
    }

    public static float yawTo(Vec3d from, Vec3d to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    /**
     * Начать действие. anchor — где встанет игрок (null — где стоит), target — на что смотрит (центр предмета).
     * null — нельзя (уже занят, далеко).
     */
    @Nullable
    public static Session start(ServerPlayerEntity p, HandKind k, String variant, @Nullable Vec3d anchor, @Nullable Vec3d target) {
        Session old = ACTIVE.get(p.getUuid());
        if (old != null) {
            if (old.kind.blocking && !k.blocking) return null; // у банкомата дверь не толкают
            if (old.kind.blocking) {
                long t = p.getServerWorld().getTime() - old.t0;
                if (t < old.kind.len) return null; // ещё не кончилось
            }
            ACTIVE.remove(p.getUuid());
        }
        if (k.blocking && (p.hasVehicle() || !p.isAlive())) return null;
        Vec3d a = anchor == null ? p.getPos() : new Vec3d(anchor.x, p.getY(), anchor.z);
        if (k.blocking && a.squaredDistanceTo(p.getPos()) > REACH * REACH) {
            p.sendMessage(Text.literal("Подойди ближе").formatted(Formatting.GRAY), true);
            return null;
        }
        Vec3d tg = target == null ? a.add(p.getRotationVector().multiply(1, 0, 1)) : target;
        float yaw = target == null ? p.getYaw() : yawTo(a, tg);
        Session s = new Session(p.getUuid(), k, variant == null ? "" : variant, p.getServerWorld().getTime(), a, yaw, tg);
        s.hp = p.getHealth();
        ACTIVE.put(p.getUuid(), s);
        broadcast(p, s, false);
        return s;
    }

    /** Короткий жест без места (дверь, сесть в машину, ремень). */
    public static void gesture(ServerPlayerEntity p, HandKind k, String variant) {
        Session old = ACTIVE.get(p.getUuid());
        if (old != null && old.kind.blocking) return;
        start(p, k, variant, null, null);
    }

    // ------------------------------------------------------------------ сеть
    private static void broadcast(ServerPlayerEntity p, Session s, boolean end) {
        NbtCompound n = new NbtCompound();
        n.putUuid("who", p.getUuid());
        n.putString("k", s.kind.id());
        if (end) {
            n.putBoolean("x", s.stoppedAt >= 0);
        } else {
            n.putString("v", s.variant);
            n.putLong("t0", s.t0);
            n.putDouble("ax", s.anchor.x);
            n.putDouble("ay", s.anchor.y);
            n.putDouble("az", s.anchor.z);
            n.putFloat("yaw", s.yaw);
            n.putDouble("tx", s.target.x);
            n.putDouble("ty", s.target.y);
            n.putDouble("tz", s.target.z);
        }
        String type = end ? "hand_end" : "hand_start";
        for (ServerPlayerEntity q : p.getServerWorld().getPlayers())
            if (q == p || q.squaredDistanceTo(p) < SEND * SEND) LifeNet.send(q, type, n.copy());
    }

    /** Пакеты от клиента: «hand_cancel», «hand_emote» (закурить, выпить — из колеса жестов). */
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "hand_cancel" -> {
                Session s = ACTIVE.get(p.getUuid());
                if (s != null) cancel(p, s);
            }
            case "hand_emote" -> emote(p, d.getString("k"));
            case "hand_belt" -> {
                if (p.hasVehicle()) gesture(p, HandKind.BELT, "");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void emote(ServerPlayerEntity p, String k) {
        if (p.hasVehicle() || p.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem) return;
        if (k.equals("smoke")) {
            if (!AgeGate.alcohol(p)) return; // курение — как алкоголь: только 18+
            start(p, HandKind.SMOKE, "", null, null);
        } else if (k.equals("drink")) {
            start(p, HandKind.DRINK, AgeGate.alcohol(p) ? "beer" : "soda", null, null);
        }
    }

    // ------------------------------------------------------------------ тик
    private static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        for (var e : new ArrayList<>(ACTIVE.entrySet())) {
            Session s = e.getValue();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || p.isRemoved()) {
                ACTIVE.remove(e.getKey());
                continue;
            }
            int t = (int) (p.getServerWorld().getTime() - s.t0);
            if (t < 0) continue;
            // прервать: сдвинулся, ранен, в машине (у «на ходу» — только машина/вода/оружие)
            boolean stop;
            if (s.kind.blocking) {
                double dx = p.getX() - s.anchor.x, dz = p.getZ() - s.anchor.z;
                stop = t > s.kind.align + 6 && dx * dx + dz * dz > 1.1 * 1.1 || p.getHealth() < s.hp - 0.5f || p.hasVehicle();
            } else {
                boolean car = s.kind == HandKind.CAR_IN || s.kind == HandKind.BELT;
                stop = (s.kind == HandKind.SMOKE || s.kind == HandKind.DRINK)
                        && (p.hasVehicle() || p.isSubmergedInWater() || p.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem)
                        || car && !p.hasVehicle();
            }
            if (stop) {
                cancel(p, s);
                continue;
            }
            s.hp = Math.min(s.hp, p.getHealth());
            // метки результата
            while (!s.at.isEmpty() && s.at.firstKey() <= t) {
                var r = s.at.pollFirstEntry();
                try {
                    r.getValue().run();
                } catch (RuntimeException ex) {
                    Novograd.LOG.warn("[руки] {} t={}", s.kind, r.getKey(), ex);
                }
            }
            if (t >= s.kind.len) {
                ACTIVE.remove(e.getKey());
                doneCount++;
                if (s.done != null) try {
                    s.done.run();
                } catch (RuntimeException ex) {
                    Novograd.LOG.warn("[руки] итог {}", s.kind, ex);
                }
                broadcast(p, s, true);
            }
        }
    }

    public static void cancel(ServerPlayerEntity p, Session s) {
        if (ACTIVE.get(p.getUuid()) != s) return;
        ACTIVE.remove(p.getUuid());
        s.stoppedAt = (int) Math.max(0, p.getServerWorld().getTime() - s.t0);
        cancelCount++;
        if (s.cancel != null) try {
            s.cancel.run();
        } catch (RuntimeException ex) {
            Novograd.LOG.warn("[руки] отмена {}", s.kind, ex);
        }
        broadcast(p, s, true);
    }

    /** Прервать то, что идёт (полиция, кат-сцена, смерть). */
    public static void cancel(ServerPlayerEntity p) {
        Session s = ACTIVE.get(p.getUuid());
        if (s != null) cancel(p, s);
    }
}
