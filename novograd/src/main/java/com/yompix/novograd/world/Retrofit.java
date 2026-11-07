package com.yompix.novograd.world;

import com.yompix.novograd.Novograd;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Новые места в старых сохранениях. Генератор острова строит их сам в новых чанках (и ставит отметку — блок глубинного
 * сланца на дне чанка); если отметки нет, чанк был создан раньше — тогда при его загрузке нужный кусок острова
 * пересобирается заново из {@link Island#column} (кафе, супермаркеты, «Мираж», трасса в пустыне), а эстакада метро
 * дописывается поверх ({@link Metro#column}). По одному чанку за тик и не дольше 3 мс — без рывков.
 */
public final class Retrofit {
    private Retrofit() {}

    /** Прямоугольник, который пересобирается целиком, и высоты (y0..y1). */
    public record Region(int x0, int z0, int x1, int z1, int y0, int y1) {
        boolean has(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        boolean touches(int cx, int cz) {
            int bx = cx << 4, bz = cz << 4;
            return bx + 15 >= x0 && bx <= x1 && bz + 15 >= z0 && bz <= z1;
        }
    }

    private static List<Region> regions;

    static List<Region> regions() {
        if (regions == null) {
            List<Region> r = new ArrayList<>();
            r.addAll(Venues.regions());
            r.addAll(DesertCity.regions());
            regions = r;
        }
        return regions;
    }

    /** Отметки: (0, −63, 0) — пересобрано, (1, −63, 0) — эстакада. */
    static final BlockState MARK = Blocks.DEEPSLATE.getDefaultState();
    static final int MARK_Y = -63;

    public static boolean fullChunk(int cx, int cz) {
        for (Region r : regions()) if (r.touches(cx, cz)) return true;
        return false;
    }

    /** Генератор: отметки в новых чанках (их не нужно пересобирать). */
    static void mark(int x, int z, Island.Column c) {
        if ((z & 15) != 0 || (x & 15) > 1) return;
        int cx = x >> 4, cz = z >> 4;
        if ((x & 15) == 0 && fullChunk(cx, cz)) c.set(MARK_Y, MARK);
        if ((x & 15) == 1 && Metro.chunk(cx, cz)) c.set(MARK_Y, MARK);
    }

    private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();
    private static int done;

    public static void init() {
        ServerChunkEvents.CHUNK_LOAD.register((w, chunk) -> {
            if (w.getRegistryKey() != World.OVERWORLD) return;
            ChunkPos cp = chunk.getPos();
            if (fullChunk(cp.x, cp.z) || Metro.chunk(cp.x, cp.z)) QUEUE.add(cp.toLong());
        });
        ServerTickEvents.END_SERVER_TICK.register(Retrofit::tick);
    }

    private static void tick(MinecraftServer s) {
        if (QUEUE.isEmpty()) return;
        ServerWorld w = s.getOverworld();
        if (!(w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) {
            QUEUE.clear();
            return;
        }
        long start = System.nanoTime();
        int n = 0;
        while (!QUEUE.isEmpty() && n < 2 && System.nanoTime() - start < 3_000_000) {
            long key = QUEUE.removeFirstLong();
            ChunkPos cp = new ChunkPos(key);
            if (!w.isChunkLoaded(cp.x, cp.z)) continue; // выгрузился — вернётся при следующей загрузке
            WorldChunk ch = w.getChunk(cp.x, cp.z);
            try {
                if (fullChunk(cp.x, cp.z) && !ch.getBlockState(new BlockPos(cp.getStartX(), MARK_Y, cp.getStartZ())).isOf(Blocks.DEEPSLATE)) {
                    rebuild(w, cp);
                    w.setBlockState(new BlockPos(cp.getStartX(), MARK_Y, cp.getStartZ()), MARK, Block.NOTIFY_LISTENERS);
                    n++;
                    done++;
                }
                if (Metro.chunk(cp.x, cp.z) && !ch.getBlockState(new BlockPos(cp.getStartX() + 1, MARK_Y, cp.getStartZ())).isOf(Blocks.DEEPSLATE)) {
                    overlay(w, cp);
                    w.setBlockState(new BlockPos(cp.getStartX() + 1, MARK_Y, cp.getStartZ()), MARK, Block.NOTIFY_LISTENERS);
                    n++;
                    done++;
                }
            } catch (RuntimeException e) {
                Novograd.LOG.warn("Retrofit {}: {}", cp, e.toString());
            }
        }
        if (QUEUE.size() > 30000) QUEUE.clear();
    }

    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    /** Пересобрать части чанка, попавшие в регионы, — так же, как их построил бы генератор. */
    static void rebuild(ServerWorld w, ChunkPos cp) {
        BlockState[] col = new BlockState[400];
        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos.Mutable m = new BlockPos.Mutable();
        WorldChunk ch = w.getChunk(cp.x, cp.z);
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getStartX() + lx, z = cp.getStartZ() + lz;
                int y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
                for (Region r : regions())
                    if (r.has(x, z)) {
                        y0 = Math.min(y0, r.y0());
                        y1 = Math.max(y1, r.y1());
                    }
                if (y0 > y1) continue;
                java.util.Arrays.fill(col, null);
                Island.column(x, z, (y, st) -> {
                    if (y >= -64 && y < 320 && !st.isAir()) col[y + 64] = st; // как генератор: «воздух» ничего не стирает
                });
                for (int y = y0; y <= y1; y++) {
                    BlockState want = col[y + 64] == null ? air : col[y + 64];
                    m.set(x, y, z);
                    if (ch.getBlockState(m) != want) w.setBlockState(m, want, FLAGS);
                }
            }
    }

    /** Дописать эстакаду и станции метро поверх того, что есть. */
    static void overlay(ServerWorld w, ChunkPos cp) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        WorldChunk ch = w.getChunk(cp.x, cp.z);
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getStartX() + lx, z = cp.getStartZ() + lz;
                Metro.column(x, z, (y, st) -> {
                    if (st.isAir()) return;
                    m.set(x, y, z);
                    if (ch.getBlockState(m) != st) w.setBlockState(m, st, FLAGS);
                });
            }
    }

    /** Автотест: сколько чанков ждут и сколько уже дописано. */
    public static String debug() {
        return "очередь " + QUEUE.size() + ", дописано " + done;
    }
}
