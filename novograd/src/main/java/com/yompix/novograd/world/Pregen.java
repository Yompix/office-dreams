package com.yompix.novograd.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * Без «прогрузок»: остров генерируется заранее, в фоне, пока ты играешь.
 * 1) Срочная очередь — места ближайших кат-сцен и миссии (заказывает Story при смене шага): к началу сцены
 *    всё уже построено, камера сразу видит город.
 * 2) Фоновая генерация всего острова спиралью от центра города — только когда серверу легко (свободное время тика),
 *    по несколько участков за тик на рабочих потоках. Прогресс сохраняется в мире.
 */
public final class Pregen {
    private Pregen() {}

    /** Участок держится загруженным ~10 с — за это время он генерируется и сохраняется, потом выгружается сам. */
    private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("novograd_pregen", Comparator.comparingLong(ChunkPos::toLong), 200);
    private static final ArrayDeque<ChunkPos> URGENT = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static final int HALF = 2048 / 16; // остров ±2 км в участках
    private static final int URGENT_MAX = 4000;

    static final class Progress extends PersistentState {
        int index;
        static final Type<Progress> TYPE = new Type<>(Progress::new, (n, r) -> {
            Progress p = new Progress();
            p.index = n.getInt("i");
            return p;
        }, null);

        @Override
        public NbtCompound writeNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup r) {
            n.putInt("i", index);
            return n;
        }
    }

    private static boolean island(ServerWorld w) {
        return w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator;
    }

    /** Заказать участки вокруг точки (r участков во все стороны) — в срочную очередь. */
    public static void around(double x, double z, int r) {
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                ChunkPos c = new ChunkPos(cx + dx, cz + dz);
                if (QUEUED.size() > URGENT_MAX) QUEUED.clear();
                if (QUEUED.add(c.toLong())) URGENT.add(c);
            }
    }

    /** Story: шаг миссии сменился — готовим места всех кат-сцен этой миссии (камера и актёры). */
    public static void forMission(ServerPlayerEntity p, JsonObject mission) {
        if (mission == null || !island(p.getServerWorld())) return;
        ServerWorld w = p.getServerWorld();
        JsonArray steps = mission.getAsJsonArray("steps");
        if (steps == null) return;
        for (JsonElement e : steps) {
            JsonObject st = e.getAsJsonObject();
            if (!"cutscene".equals(st.has("type") ? st.get("type").getAsString() : "")) continue;
            if (st.has("shots")) for (JsonElement s : st.getAsJsonArray("shots")) {
                JsonObject sh = s.getAsJsonObject();
                for (String k : new String[]{"from", "to"}) {
                    if (!sh.has(k)) continue;
                    double[] c = com.yompix.novograd.story.Director.camera(w, sh.get(k).getAsString());
                    if (c != null) around(c[0], c[2], 3);
                }
            }
            if (st.has("actors")) for (JsonElement a : st.getAsJsonArray("actors")) {
                JsonArray cue = a.getAsJsonArray();
                for (int i = 2; i < cue.size(); i++) {
                    if (!cue.get(i).isJsonPrimitive() || !cue.get(i).getAsJsonPrimitive().isString()) continue;
                    var v = com.yompix.novograd.story.Director.pos(w, cue.get(i).getAsString());
                    if (v != null) around(v.x, v.z, 2);
                }
            }
        }
    }

    /** Спираль участков от центра города: i → участок (кольца растут наружу). */
    private static ChunkPos spiral(int i) {
        // быстрый расчёт по кольцам
        int ring = (int) Math.ceil((Math.sqrt(i + 1) - 1) / 2);
        int side = 2 * ring, maxIdx = (2 * ring + 1) * (2 * ring + 1) - 1;
        int t = maxIdx - i;
        if (t < side) return new ChunkPos(ring - t, ring);
        t -= side;
        if (t < side) return new ChunkPos(-ring, ring - t);
        t -= side;
        if (t < side) return new ChunkPos(-ring + t, -ring);
        t -= side;
        return new ChunkPos(ring, -ring + t);
    }

    private static int inFlight;

    public static void tick(MinecraftServer s) {
        ServerWorld w = s.getOverworld();
        if (!island(w)) return;
        // нагрузка: средний тик сервера; тяжело — ждём
        float mspt = s.getAverageTickTime();
        int budget = URGENT.isEmpty() ? (mspt < 25 ? 2 : mspt < 40 ? 1 : 0) : (mspt < 45 ? 6 : 2);
        if (s.getTicks() % 2 != 0) return;
        var cm = w.getChunkManager();
        for (int n = 0; n < budget; n++) {
            ChunkPos c = URGENT.pollFirst();
            if (c == null) {
                if (s.getPlayerManager().getPlayerList().isEmpty()) return;
                Progress pr = w.getPersistentStateManager().getOrCreate(Progress.TYPE, "novograd_pregen");
                int total = (2 * HALF + 1) * (2 * HALF + 1);
                if (pr.index >= total) return; // весь остров уже готов
                ChunkPos sp = spiral(pr.index++);
                pr.markDirty();
                c = new ChunkPos(sp.x + (432 >> 4), sp.z + (-430 >> 4)); // от делового центра
                if (Math.abs(c.x) > HALF || Math.abs(c.z) > HALF) continue;
            }
            cm.addTicket(TICKET, c, 0, c); // генерация на рабочих потоках, не в тике
        }
    }
}
