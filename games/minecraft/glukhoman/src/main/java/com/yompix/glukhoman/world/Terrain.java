package com.yompix.glukhoman.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;

/**
 * Рельеф и лес Глухомани. Каждый столбец — чистая функция от (x, z): быстро и одинаково на клиенте и сервере.
 * Площадки под постройки и дороги выравниваются к уровню G, вокруг — холмистая тёмная тайга с елями и сухостоем.
 * Север — минус Z. Старт на юге (0, 240), посёлок (0, 90), станция (0, -235), шахта (-130, -170).
 */
public final class Terrain {
    private Terrain() {}

    public static final int G = 64;
    public static final int RIVER_Z = 258;

    /** Площадки: центр x, z, полуширина, полудлина. На них ровно и нет деревьев. */
    public static final int[][] SITES = {
            {10, 18, 80, 14},      // станция Еловый, перрон, переезд, обгонный путь
            {0, 95, 34, 62},       // посёлок
            {-48, 72, 12, 14},     // кладбище
    };

    /** Дороги: ломаные (x, z…) и ширина. Насыпь узкоколейки — тоже «дорога» (ровно, без деревьев). */
    public static final int[][] ROADS = {
            {0, 165, 0, 22, 4},                    // улица от посёлка к станции
            {31, 40, 31, 0, 3},                    // дорога через переезд
            {80, 16, 430, 16, 6},                  // насыпь в лес, к 41-му километру
            {-20, 72, -36, 72, 2},
    };

    // ---------- шум ----------
    private static double hash(int x, int z, int s) {
        long h = x * 374761393L + z * 668265263L + s * 2147483647L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static double vnoise(double x, double z, int s) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(xi, zi, s), b = hash(xi + 1, zi, s), c = hash(xi, zi + 1, s), d = hash(xi + 1, zi + 1, s);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    private static double fbm(double x, double z) {
        return vnoise(x / 64, z / 64, 1) * 0.6 + vnoise(x / 24, z / 24, 2) * 0.3 + vnoise(x / 9, z / 9, 3) * 0.1;
    }

    // ---------- маски ----------
    /** 1 — внутри площадки, плавно до 0 за 14 блоков. */
    public static double siteWeight(int x, int z) {
        double w = 0;
        for (int[] s : SITES) {
            double dx = Math.max(0, Math.abs(x - s[0]) - s[2]), dz = Math.max(0, Math.abs(z - s[1]) - s[3]);
            double d = Math.sqrt(dx * dx + dz * dz);
            w = Math.max(w, 1 - Math.min(1, d / 14));
        }
        return w;
    }

    /** Расстояние до ближайшей дороги (по оси ломаной) минус полуширина. */
    public static double roadDist(int x, int z) {
        double best = 1e9;
        for (int[] r : ROADS) {
            int n = (r.length - 1) / 2;
            double half = r[r.length - 1] / 2.0;
            for (int i = 0; i < n - 1; i++) {
                double ax = r[i * 2], az = r[i * 2 + 1], bx = r[i * 2 + 2], bz = r[i * 2 + 3];
                double vx = bx - ax, vz = bz - az, len2 = vx * vx + vz * vz;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / len2));
                double px = ax + vx * t - x, pz = az + vz * t - z;
                best = Math.min(best, Math.sqrt(px * px + pz * pz) - half);
            }
        }
        return best;
    }

    public static boolean inRiver(int x, int z) {
        return Math.abs(z - RIVER_Z - (int) (Math.sin(x / 23.0) * 3)) <= 4;
    }

    public static int surface(int x, int z) {
        double natural = G - 2 + fbm(x, z) * 14;
        // дальние сопки за границей сюжета
        double r = Math.sqrt(x * x + (z + 20.0) * (z + 20.0));
        if (r > 380) natural += Math.min(30, (r - 380) * 0.25) * vnoise(x / 40.0, z / 40.0, 9);
        double w = Math.max(siteWeight(x, z), 1 - Math.min(1, Math.max(0, roadDist(x, z)) / 8));
        int h = (int) Math.round(natural + (G - natural) * w);
        if (inRiver(x, z)) h = Math.min(h, G - 6);
        else if (Math.abs(z - RIVER_Z) <= 7) h = Math.min(h, G - 1);
        return h;
    }

    /** Есть ли дерево с корнем в (x, z): сетка 5×5 со смещением, гуще вдали от построек. */
    private static int treeAt(int cx, int cz) {
        int x = cx * 5 + (int) (hash(cx, cz, 11) * 4), z = cz * 5 + (int) (hash(cx, cz, 12) * 4);
        double clear = Math.max(siteWeight(x, z) * 1.6, 0);
        double road = roadDist(x, z);
        if (clear > 0.35 || road < 2.5 || inRiver(x, z) || Math.abs(z - RIVER_Z) < 8) return Integer.MIN_VALUE;
        double dens = 0.78 - clear + (road < 6 ? -0.4 : 0);
        if (hash(cx, cz, 13) > dens) return Integer.MIN_VALUE;
        return 0;
    }

    public interface Sink {
        void put(int y, BlockState s);
    }

    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState LOG = Blocks.SPRUCE_LOG.getDefaultState();
    private static final BlockState DEAD = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState();
    private static final BlockState BIRCH = Blocks.BIRCH_LOG.getDefaultState();
    private static final BlockState LEAF = Blocks.SPRUCE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true);

    /** Строит столбец снизу вверх. */
    public static void column(int x, int z, Sink out) {
        int h = surface(x, z);
        out.put(-64, Blocks.BEDROCK.getDefaultState());
        for (int y = -63; y < 0; y++) out.put(y, Blocks.DEEPSLATE.getDefaultState());
        for (int y = 0; y < h - 3; y++) out.put(y, Blocks.STONE.getDefaultState());
        boolean river = inRiver(x, z);
        double road = roadDist(x, z);
        double site = siteWeight(x, z);
        for (int y = h - 3; y < h; y++) out.put(y, river ? Blocks.GRAVEL.getDefaultState() : Blocks.DIRT.getDefaultState());
        double n = hash(x, z, 5);
        BlockState top;
        if (river) top = Blocks.MUD.getDefaultState();
        else if (road < 0) top = n < 0.55 ? Blocks.DIRT_PATH.getDefaultState() : n < 0.85 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.GRAVEL.getDefaultState();
        else if (Math.abs(z - RIVER_Z) <= 7) top = n < 0.5 ? Blocks.GRAVEL.getDefaultState() : Blocks.MUD.getDefaultState();
        else {
            double p = vnoise(x / 7.0, z / 7.0, 21);
            top = p < 0.3 ? Blocks.PODZOL.getDefaultState() : p < 0.42 ? Blocks.COARSE_DIRT.getDefaultState() : p > 0.8 ? Blocks.MOSS_BLOCK.getDefaultState()
                    : Blocks.GRASS_BLOCK.getDefaultState();
        }
        out.put(h, top);
        if (river) {
            for (int y = h + 1; y <= G - 2; y++) out.put(y, Blocks.WATER.getDefaultState());
            return;
        }
        // мелочь на земле
        if (road >= 0.5 && Math.abs(z - RIVER_Z) > 7) {
            double g = hash(x, z, 7);
            boolean grassy = top.isOf(Blocks.GRASS_BLOCK) || top.isOf(Blocks.MOSS_BLOCK) || top.isOf(Blocks.PODZOL);
            if (grassy && g < 0.16 * (1 - site * 0.6)) out.put(h + 1, Blocks.FERN.getDefaultState());
            else if (grassy && g < 0.3) out.put(h + 1, Blocks.SHORT_GRASS.getDefaultState());
            else if (grassy && g < 0.315 && site < 0.2) {
                out.put(h + 1, Blocks.LARGE_FERN.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.LOWER));
                out.put(h + 2, Blocks.LARGE_FERN.getDefaultState().with(TallPlantBlock.HALF, DoubleBlockHalf.UPPER));
            } else if (g > 0.996 && site < 0.1) out.put(h + 1, Blocks.MOSSY_COBBLESTONE.getDefaultState());
            else if (g > 0.99 && site < 0.1) out.put(h + 1, Blocks.BROWN_MUSHROOM.getDefaultState());
            else if (g > 0.985 && site < 0.1) out.put(h + 1, Blocks.DEAD_BUSH.getDefaultState());
        }
        // деревья: проверяем соседние клетки сетки
        int cx0 = Math.floorDiv(x, 5), cz0 = Math.floorDiv(z, 5);
        for (int cx = cx0 - 1; cx <= cx0 + 1; cx++)
            for (int cz = cz0 - 1; cz <= cz0 + 1; cz++) {
                if (treeAt(cx, cz) == Integer.MIN_VALUE) continue;
                int tx = cx * 5 + (int) (hash(cx, cz, 11) * 4), tz = cz * 5 + (int) (hash(cx, cz, 12) * 4);
                int dx = Math.abs(x - tx), dz = Math.abs(z - tz);
                if (dx > 3 || dz > 3) continue;
                int base = surface(tx, tz) + 1;
                double kind = hash(cx, cz, 14);
                int height = 9 + (int) (hash(cx, cz, 15) * 9);
                if (kind < 0.12) { // сухостой: голый ствол
                    if (dx == 0 && dz == 0) for (int y = base; y < base + height - 3; y++) out.put(y, kind < 0.05 ? BIRCH : DEAD);
                    continue;
                }
                int crown = base + height;
                for (int y = base + 3; y <= crown; y++) {
                    int fromTop = crown - y;
                    int r = fromTop <= 1 ? 0 : Math.min(3, (fromTop + 1) / 3 + ((fromTop % 3 == 0) ? 1 : 0));
                    if (fromTop > height - 5) r = Math.min(r, 1);
                    boolean edge = dx + dz > r + (r >= 2 ? 1 : 0);
                    if (dx == 0 && dz == 0) continue;
                    if (dx <= r && dz <= r && !edge) out.put(y, LEAF);
                }
                if (dx == 0 && dz == 0) {
                    for (int y = base; y < crown; y++) out.put(y, LOG);
                    out.put(crown, LEAF);
                    out.put(crown + 1, LEAF);
                }
            }
    }

    public static String zone(int x, int z) {
        if (z < 34 && x > -80 && x < 90) return "Станция";
        if (Math.abs(x) < 40 && z >= 34 && z < 165) return "Посёлок";
        if (z > 8 && z < 24 && x >= 90) return "Линия";
        return "Тайга";
    }
}
