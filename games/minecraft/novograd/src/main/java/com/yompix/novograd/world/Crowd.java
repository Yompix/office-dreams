package com.yompix.novograd.world;

import com.yompix.novograd.entity.CitizenEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.9.7: плотность людей по районам. Раньше несколько систем (старый город, жильцы, досуг, улицы) ставили людей
 * независимо — у точек появления и популярных мест набирались толпы. Теперь:
 * <ul>
 *   <li>{@link #room} — перед появлением прохожего: в «клетке» 16×16 не больше {@link #cap} людей (центр — больше,
 *       окраины — меньше) и в радиусе 5 блоков — не больше половины этого (без кучек у точки появления);</li>
 *   <li>{@link #tick} — раз в 10 с: где людей больше нормы, лишних обычных прохожих (не героев, не жильцов, не
 *       персонал, не участников событий) убираем там, где игрок не видит; дальних (дальше 128 от игроков) — тоже.</li>
 * </ul>
 * Выключить для сравнения: -Dnovograd.crowdcap=false.
 */
public final class Crowd {
    private Crowd() {}

    public static final boolean ON = !"false".equals(System.getProperty("novograd.crowdcap"));
    /** Для автотеста: сколько появлений отклонено и сколько лишних убрано. */
    public static int denied, thinned, farRemoved;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Crowd::tick);
    }

    /** Норма людей в клетке 16×16 по району. */
    public static int cap(Island.Zone z) {
        return switch (z) {
            case CORE, DOWNTOWN -> 8;
            case MIRAGE, RIVIERA, BEACH -> 6;
            case SUBURB, PORT, INDUSTRIAL -> 4;
            case VILLAGE, COUNTRY, AIRFIELD -> 3;
            default -> 0;
        };
    }

    public static int capAt(double x, double z) {
        return cap(Island.zone((int) Math.floor(x), (int) Math.floor(z)));
    }

    /** Обычный прохожий (его можно не ставить / убрать без вреда для сюжета, работы и событий). */
    public static boolean casual(CitizenEntity c) {
        return c.getRole() == CitizenEntity.Role.CITIZEN && c.getStoryId().isEmpty() && c.getStoryOwner().isEmpty() && !c.isStationary()
                && !c.hasVehicle() && !c.getCommandTags().contains(Residents.TAG);
    }

    private static boolean person(CitizenEntity c) {
        return c.getRole() == CitizenEntity.Role.CITIZEN && c.getStoryId().isEmpty() && c.isAlive();
    }

    /** Есть ли место для ещё одного прохожего у точки. */
    public static boolean room(ServerWorld w, Vec3d at) {
        if (!ON) return true;
        int cap = capAt(at.x, at.z);
        if (cap <= 0) {
            denied++;
            return false;
        }
        int cx = (int) Math.floor(at.x) >> 4, cz = (int) Math.floor(at.z) >> 4;
        Box cell = new Box(cx << 4, at.y - 12, cz << 4, (cx << 4) + 16, at.y + 12, (cz << 4) + 16);
        int inCell = w.getEntitiesByClass(CitizenEntity.class, cell, Crowd::person).size();
        int near = w.getEntitiesByClass(CitizenEntity.class, new Box(at, at).expand(5, 4, 5), Crowd::person).size();
        if (inCell >= cap || near >= Math.max(2, cap / 2)) {
            denied++;
            return false;
        }
        return true;
    }

    private static void tick(MinecraftServer s) {
        if (!ON || s.getTicks() % 200 != 37) return;
        ServerWorld w = s.getOverworld();
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity p : w.getPlayers()) if (!p.isSpectator()) players.add(p);
        if (players.isEmpty()) return;
        Map<Long, List<CitizenEntity>> cells = new HashMap<>();
        java.util.Set<CitizenEntity> seen = new java.util.HashSet<>();
        for (ServerPlayerEntity p : players)
            for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(160, 48, 160), Crowd::person)) {
                if (!seen.add(c)) continue;
                cells.computeIfAbsent(((long) ((int) Math.floor(c.getX()) >> 4) << 32) ^ ((int) Math.floor(c.getZ()) >> 4 & 0xFFFFFFFFL), k -> new ArrayList<>()).add(c);
            }
        for (List<CitizenEntity> list : cells.values()) {
            CitizenEntity first = list.get(0);
            int cap = capAt(first.getX(), first.getZ());
            int extra = list.size() - Math.max(cap, 1);
            for (CitizenEntity c : list) {
                double near = Double.MAX_VALUE;
                for (ServerPlayerEntity p : players) near = Math.min(near, p.squaredDistanceTo(c));
                if (casual(c) && c.isAmbient() && near > 128 * 128) {
                    c.discard();
                    farRemoved++;
                    continue;
                }
                if (extra <= 0 || !casual(c)) continue;
                if (near < 20 * 20 || !Sidewalks.unseen(c)) continue; // на глазах не исчезаем
                c.discard();
                thinned++;
                extra--;
            }
        }
    }

    /** Для автотеста/отладки: сколько людей в каждой клетке вокруг точки (макс. клетка и всего). */
    public static int[] stats(ServerWorld w, Vec3d at, int radius) {
        Map<Long, Integer> cells = new HashMap<>();
        int total = 0;
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, new Box(at, at).expand(radius, 64, radius), Crowd::person)) {
            cells.merge(((long) ((int) Math.floor(c.getX()) >> 4) << 32) ^ ((int) Math.floor(c.getZ()) >> 4 & 0xFFFFFFFFL), 1, Integer::sum);
            total++;
        }
        int max = 0;
        for (int v : cells.values()) max = Math.max(max, v);
        return new int[]{max, total, cells.size()};
    }
}
