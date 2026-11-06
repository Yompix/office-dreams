package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.block.NeonBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.util.math.Direction;

/**
 * Остров Новоград 2000×2000: вся карта описана функциями от координат (x, z), без загрузки мира.
 * <pre>
 *   север (z < 0)        холмы, лес, дачи, аэродром
 *   северо-восток        ДЕЛОВОЙ ЦЕНТР — небоскрёбы
 *   центр                старый город (строит TownBuilder)
 *   юго-восток           ПРОМЗОНА и район банд
 *   запад                ПЛЯЖ и ПОРТ, дальше море
 *   вокруг центра        пригород с домами
 * </pre>
 * Улицы города — по сетке старого центра: дороги на координатах ≡ 24 (mod 48), участки 48×48 с центром на кратных 48.
 * Районы связаны кольцевой трассой (x = −552 и 744, z = ±744).
 */
public final class Island {
    private Island() {}

    /** Высота травы (на ней стоят), как в плоском мире старого города. */
    public static final int G = -57;
    public static final int HALF = 2000;

    public enum Zone { SEA, BEACH, PORT, CORE, DOWNTOWN, INDUSTRIAL, SUBURB, COUNTRY, AIRFIELD, RIVIERA, VILLAGE }

    // ------------------------------------------------------------------ шум
    private static double hash(long x, long z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        return (h >>> 11) / (double) (1L << 53);
    }

    /** Плавный шум 0..1 с ячейкой size. */
    private static double noise(double x, double z, double size, long salt) {
        double fx = x / size, fz = z / size;
        long x0 = (long) Math.floor(fx), z0 = (long) Math.floor(fz);
        double tx = fx - x0, tz = fz - z0;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = hash(x0, z0, salt), b = hash(x0 + 1, z0, salt), c = hash(x0, z0 + 1, salt), d = hash(x0 + 1, z0 + 1, salt);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    // ------------------------------------------------------------------ районы
    /** Где кончается суша на западе (неровный берег). */
    private static double coastX(int z) {
        return -620 + (noise(0, z, 90, 7) - 0.5) * 40;
    }

    /** Западный берег острова Ривьера. */
    private static double westCoast(int z) {
        return -1880 + (noise(0, z, 110, 8) - 0.5) * 50;
    }

    /** Озеро на востоке: глубина (0 — суши нет). */
    private static double lake(int x, int z) {
        double d = Math.hypot((x - 1320) / 210.0, (z - 760) / 150.0) + (noise(x, z, 60, 9) - 0.5) * 0.25;
        return d < 1 ? 1 - d : 0;
    }

    public static Zone zone(int x, int z) {
        double edge = Math.max(Math.abs(x), Math.abs(z)) + (noise(x, z, 120, 3) - 0.5) * 60;
        if (edge > HALF - 30) return Zone.SEA;
        // за проливом — остров с курортным городом Ривьера
        if (x < -840) {
            double wx = westCoast(z);
            if (x < wx) return Zone.SEA;
            if (x < wx + 45 || x > -900) return Zone.BEACH;
            if (x >= -1560 && x <= -1080 && z >= -420 && z <= 420) return Zone.RIVIERA;
            if (x >= -1790 && x <= -940 && z >= -900 && z <= 900) return Zone.SUBURB;
            return Zone.COUNTRY;
        }
        if (lake(x, z) > 0.08) return Zone.SEA;
        double cx = coastX(z);
        boolean port = z > -420 && z < 420;
        if (port && x < cx + 40 && x > -800) return Zone.PORT;
        if (x < cx) return Zone.SEA;
        if (x < cx + 45) return Zone.BEACH;
        if (x >= -84 && x <= 132 && z >= -84 && z <= 132) return Zone.CORE;
        if (x >= 300 && x <= 900 && z >= -990 && z <= -820) return Zone.AIRFIELD;
        if (x >= 156 && x <= 700 && z >= -700 && z <= -156) return Zone.DOWNTOWN;
        if (x >= 156 && x <= 700 && z >= 156 && z <= 700) return Zone.INDUSTRIAL;
        if (Math.abs(x) <= 560 && Math.abs(z) <= 560) return Zone.SUBURB;
        if (x >= 1250 && x <= 1650 && z >= -250 && z <= 250) return Zone.VILLAGE;
        return Zone.COUNTRY;
    }

    public static boolean urban(Zone z) {
        return z == Zone.DOWNTOWN || z == Zone.INDUSTRIAL || z == Zone.SUBURB || z == Zone.RIVIERA || z == Zone.VILLAGE;
    }

    /** Расстояние до ближайшей линии сетки улиц (24 mod 48). */
    public static int gridDist(int t) {
        int m = Math.floorMod(t - 24, 48);
        return Math.min(m, 48 - m);
    }

    /** Трассы острова: {0 — вдоль z на x=c | 1 — вдоль x на z=c, c, от, до}. */
    private static final int[][] ROADS = {
            // кольцо Новограда
            {0, -552, -744, 744}, {0, 744, -744, 744}, {1, -744, -552, 744}, {1, 744, -552, 744},
            // к аэродрому
            {0, 600, -830, -744},
            // мосты через пролив и кольцо Ривьеры
            {1, -744, -1800, -552}, {1, 744, -1800, -552}, {0, -1800, -744, 744}, {0, -920, -1700, 1700},
            // большое кольцо острова и дороги в горы, пустошь и деревню
            {0, 1700, -1700, 1700}, {1, -1700, -920, 1700}, {1, 1700, -920, 1700},
            {1, 24, 744, 1700}, {0, 24, -1700, -744}, {0, 24, 744, 1700},
    };

    /** Трасса: расстояние до осевой ближайшей трассы (или большое число). */
    private static int highwayDist(int x, int z) {
        int best = 99;
        for (int[] r : ROADS) {
            int along = r[0] == 0 ? z : x, across = r[0] == 0 ? x : z;
            if (along >= r[2] - 7 && along <= r[3] + 7) best = Math.min(best, Math.abs(across - r[1]));
        }
        // магистрали по осям внутри кольца (x = 24 и z = 24), кроме старого центра
        if (Math.abs(x) > 140 || Math.abs(z) > 140) {
            if (z >= -744 && z <= 744) best = Math.min(best, Math.abs(x - 24));
            if (x >= -552 && x <= 744) best = Math.min(best, Math.abs(z - 24));
        }
        return best;
    }

    /** Высота поверхности (y верхнего блока земли). */
    public static int surface(int x, int z) {
        Zone zn = zone(x, z);
        if (zn == Zone.SEA) return lake(x, z) > 0 ? G - 2 - (int) (lake(x, z) * 10) : G - 6;
        if (zn != Zone.COUNTRY) return G;
        // холмы за городом: выше к северу, на крайнем севере — горы, у дорог — ровно
        double hills = noise(x, z, 140, 11) * 0.7 + noise(x, z, 45, 12) * 0.3;
        double north = Math.max(0, Math.min(1, (-z - 560) / 300.0));
        double fromCity = x < -840 ? Math.max(0, Math.min(1, (Math.abs(z) - 900) / 120.0))
                : Math.max(0, Math.min(1, (Math.max(Math.abs(x), Math.abs(z)) - 560) / 120.0));
        double mountains = Math.max(0, Math.min(1, (-z - 1050) / 250.0));
        double ridge = 1 - Math.abs(noise(x, z, 260, 13) * 2 - 1); // хребты
        int h = (int) ((hills * (6 + north * 34) + mountains * ridge * 70) * fromCity);
        // пустошь на юге: плоско, но с плато-столовыми горами
        if (z > 1050 && x > -840) {
            double mesa = noise(x, z, 90, 14);
            h = (int) ((mesa > 0.68 ? 18 + (mesa - 0.68) * 40 : hills * 4) * fromCity);
        }
        int hw = highwayDist(x, z);
        if (hw < 14) h = (int) (h * Math.max(0, (hw - 8) / 6.0));
        return G + h;
    }

    // ------------------------------------------------------------------ генерация столбца
    public interface Column {
        void set(int y, BlockState s);
    }

    private static final BlockState AIR = Blocks.AIR.getDefaultState(), STONE = Blocks.STONE.getDefaultState(), DIRT = Blocks.DIRT.getDefaultState(),
            GRASS = Blocks.GRASS_BLOCK.getDefaultState(), SAND = Blocks.SAND.getDefaultState(), WATER = Blocks.WATER.getDefaultState(),
            BEDROCK = Blocks.BEDROCK.getDefaultState();

    /** Строит весь столбец (x, z): земля, вода, дороги, здания, деревья. */
    public static void column(int x, int z, Column c) {
        Zone zn = zone(x, z);
        int s = surface(x, z);
        c.set(-64, BEDROCK);
        for (int y = -63; y < s - 3; y++) c.set(y, STONE);
        if (zn == Zone.SEA) {
            for (int y = s - 3; y <= s; y++) c.set(y, SAND);
            for (int y = s + 1; y <= G - 1; y++) c.set(y, WATER);
            // мост: настил трассы над водой на опорах
            int hw = highwayDist(x, z);
            if (hw <= 7) {
                highway(x, z, G, hw, c);
                boolean alongZ = isAlongZ(x, z);
                int t = alongZ ? z : x;
                if (Math.floorMod(t, 32) < 3 && (hw == 6 || hw == 2)) for (int y = s + 1; y < G; y++) c.set(y, Blocks.STONE_BRICKS.getDefaultState());
                if (hw == 7) {
                    c.set(G + 1, Blocks.STONE_BRICK_WALL.getDefaultState());
                    if (Math.floorMod(t, 16) == 0) { c.set(G + 2, ModContent.LAMP_POST.getDefaultState()); c.set(G + 3, ModContent.LAMP_POST.getDefaultState());
                        c.set(G + 4, ModContent.STREET_LAMP.getDefaultState().with(HorizontalFacingBlock.FACING, alongZ ? Direction.EAST : Direction.NORTH)); }
                }
            }
            return;
        }
        boolean desert = zn == Zone.COUNTRY && z > 1050 && x > -840;
        for (int y = Math.max(-63, s - 3); y < s; y++) c.set(y, DIRT);
        c.set(s, zn == Zone.BEACH ? SAND : desert ? (s > G + 15 ? Blocks.TERRACOTTA.getDefaultState() : Blocks.RED_SAND.getDefaultState())
                : s > G + 62 ? Blocks.SNOW_BLOCK.getDefaultState() : s > G + 48 ? Blocks.STONE.getDefaultState() : GRASS);
        if (desert && s > G + 15) for (int y = Math.max(-63, s - 20); y < s; y++) c.set(y, (y / 3) % 2 == 0 ? Blocks.ORANGE_TERRACOTTA.getDefaultState() : Blocks.TERRACOTTA.getDefaultState());
        if (zn == Zone.BEACH) {
            c.set(s - 1, SAND);
            c.set(s - 2, SAND);
        }
        if (zn == Zone.CORE) return; // старый центр строит TownBuilder
        // трасса
        int hw = highwayDist(x, z);
        if (hw <= 9) {
            highway(x, z, s, hw, c);
            return;
        }
        switch (zn) {
            case DOWNTOWN, INDUSTRIAL, SUBURB, RIVIERA, VILLAGE -> city(x, z, s, zn, c);
            case PORT -> port(x, z, s, c);
            case BEACH -> beach(x, z, s, c);
            case AIRFIELD -> airfield(x, z, s, c);
            case COUNTRY -> {
                if (desert) desert(x, z, s, c);
                else if (s > G + 48) { if (s <= G + 62 && hash(x, z, 91) < 0.02) c.set(s + 1, Blocks.SPRUCE_SAPLING.getDefaultState()); }
                else country(x, z, s, c);
            }
            default -> {}
        }
    }

    private static void highway(int x, int z, int s, int d, Column c) {
        BlockState asphalt = ModContent.ASPHALT.getDefaultState();
        if (d <= 7) {
            c.set(s, asphalt);
            // двойная сплошная по центру и пунктир между полосами
            boolean alongZ = isAlongZ(x, z);
            int t = alongZ ? z : x;
            if (d == 0) c.set(s, Blocks.YELLOW_CONCRETE.getDefaultState());
            else if (d == 4 && Math.floorMod(t, 6) < 3) c.set(s, Blocks.WHITE_CONCRETE.getDefaultState());
            if (d == 7) c.set(s + 1, Blocks.POLISHED_ANDESITE_SLAB.getDefaultState()); // отбойник-бордюр
        } else {
            c.set(s, Blocks.COARSE_DIRT.getDefaultState());
            boolean alongZ = isAlongZ(x, z);
            int t = alongZ ? z : x;
            if (d == 9 && Math.floorMod(t, 24) == 0) lampPost(s, c, alongZ ? Direction.EAST : Direction.NORTH);
        }
    }

    private static boolean isAlongZ(int x, int z) {
        int dx = Math.abs(x - 24), dz = Math.abs(z - 24);
        for (int[] r : ROADS) {
            int along = r[0] == 0 ? z : x;
            if (along < r[2] - 7 || along > r[3] + 7) continue;
            if (r[0] == 0) dx = Math.min(dx, Math.abs(x - r[1]));
            else dz = Math.min(dz, Math.abs(z - r[1]));
        }
        return dx <= dz;
    }

    private static void lampPost(int s, Column c, Direction face) {
        for (int y = 1; y <= 3; y++) c.set(s + y, ModContent.LAMP_POST.getDefaultState());
        c.set(s + 4, ModContent.STREET_LAMP.getDefaultState().with(HorizontalFacingBlock.FACING, face));
    }

    // ------------------------------------------------------------------ город: улицы и участки
    private static void city(int x, int z, int s, Zone zn, Column c) {
        int dx = gridDist(x), dz = gridDist(z);
        int road = Math.min(dx, dz);
        BlockState asphalt = ModContent.ASPHALT.getDefaultState(), side = ModContent.SIDEWALK.getDefaultState();
        if (road <= 4) {
            c.set(s, asphalt);
            boolean cross = dx <= 6 && dz <= 6;
            // разметка: пунктир по осевой, «зебра» перед перекрёстком
            if (!cross && road == 0 && Math.floorMod(dx == 0 ? z : x, 4) < 2) c.set(s, ModContent.ASPHALT_LINE.getDefaultState()
                    .with(HorizontalFacingBlock.FACING, dx == 0 ? Direction.NORTH : Direction.EAST));
            int other = dx <= 4 ? dz : dx;
            if (other == 7 || other == 8) c.set(s, ModContent.CROSSWALK.getDefaultState().with(HorizontalFacingBlock.FACING, dx <= 4 ? Direction.EAST : Direction.NORTH));
            return;
        }
        if (road <= 6) {
            c.set(s, side);
            // светофор на углу перекрёстка
            if (dx == 5 && dz == 5) {
                for (int y = 1; y <= 3; y++) c.set(s + y, ModContent.LAMP_POST.getDefaultState());
                Direction f = Math.floorMod(x - 24, 48) < 24 ? (Math.floorMod(z - 24, 48) < 24 ? Direction.SOUTH : Direction.NORTH) : (Math.floorMod(z - 24, 48) < 24 ? Direction.EAST : Direction.WEST);
                c.set(s + 4, ModContent.TRAFFIC_LIGHT.getDefaultState().with(HorizontalFacingBlock.FACING, f));
                return;
            }
            if (road == 6 && dx >= 7 && dz >= 7) {
                int t = dx == 6 ? z : x;
                int m = Math.floorMod(t, 16);
                if (m == 8) lampPost(s, c, dx == 6 ? (Math.floorMod(x - 24, 48) < 24 ? Direction.WEST : Direction.EAST) : Direction.NORTH);
                else if (zn == Zone.DOWNTOWN && m == 0) c.set(s + 1, NeonBlockState(t));
                else if (m == 4) c.set(s + 1, Blocks.COMPOSTER.getDefaultState()); // урна
                else if (m == 12 || m == 13) { // скамейка
                    Direction back = dx == 6 ? (Math.floorMod(x - 24, 48) < 24 ? Direction.EAST : Direction.WEST) : (Math.floorMod(z - 24, 48) < 24 ? Direction.SOUTH : Direction.NORTH);
                    c.set(s + 1, Blocks.SPRUCE_STAIRS.getDefaultState().with(net.minecraft.block.StairsBlock.FACING, back.getOpposite()));
                } else if (zn == Zone.SUBURB && (m == 0 || m == 1 || m == 15)) {
                    c.set(s + 1, Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true)); // живая изгородь
                } else if (m == 2 && Math.floorMod(t, 64) == 2) c.set(s + 1, Blocks.RED_NETHER_BRICK_WALL.getDefaultState()); // гидрант
            }
            // автобусная остановка: навес со стеклом посреди квартала
            if (road == 5 && dx >= 7 && dz >= 7) {
                int t = dx == 5 ? z : x;
                int m = Math.floorMod(t - 24, 96);
                if (m >= 44 && m <= 50) {
                    if (m == 44 || m == 50) for (int y = 1; y <= 3; y++) c.set(s + y, Blocks.IRON_BARS.getDefaultState());
                    c.set(s + 4, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
                    if (m == 47) c.set(s + 3, Blocks.YELLOW_CONCRETE.getDefaultState());
                }
            }
            if (road == 6 && dx >= 7 && dz >= 7) {
                int t = dx == 6 ? z : x;
                int m = Math.floorMod(t - 24, 96);
                if (m >= 44 && m <= 50) {
                    c.set(s + 1, m == 47 ? Blocks.YELLOW_CONCRETE.getDefaultState() : Blocks.GLASS_PANE.getDefaultState());
                    c.set(s + 2, Blocks.GLASS_PANE.getDefaultState());
                    c.set(s + 3, Blocks.GLASS_PANE.getDefaultState());
                    c.set(s + 4, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
                }
            }
            return;
        }
        // участок: координаты внутри (-17..17) и «номер» участка для случайностей
        int lx = Math.floorMod(x + 24, 48) - 24, lz = Math.floorMod(z + 24, 48) - 24;
        long px = Math.floorDiv(x + 24, 48), pz = Math.floorDiv(z + 24, 48);
        switch (zn) {
            case DOWNTOWN -> tower(lx, lz, px, pz, s, c);
            case RIVIERA -> resort(lx, lz, px, pz, s, c);
            case INDUSTRIAL -> industry(lx, lz, px, pz, s, c);
            default -> suburb(lx, lz, px, pz, s, c);
        }
    }

    private static BlockState NeonBlockState(int t) {
        NeonBlock.Color[] cs = NeonBlock.Color.values();
        return ModContent.NEON_FLOOR.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH).with(NeonBlock.COLOR, cs[Math.floorMod(t / 16, cs.length)]);
    }

    /** Небоскрёб: стеклянные стены с рамой, этажи, светящиеся окна, крыша с вертолётной площадкой или антенной. */
    private static void tower(int lx, int lz, long px, long pz, int s, Column c) {
        double r = hash(px, pz, 21);
        int half = 11 + (int) (hash(px, pz, 22) * 6);              // 11..16
        double centerBoost = 1 - Math.min(1, Math.hypot(px * 48 - 430, pz * 48 + 430) / 380.0);
        int h = 24 + (int) (hash(px, pz, 23) * 40 + centerBoost * 50); // до ~110
        int style = (int) (r * 4);
        BlockState glass = switch (style) {
            case 0 -> Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState();
            case 1 -> Blocks.GRAY_STAINED_GLASS.getDefaultState();
            case 2 -> Blocks.CYAN_STAINED_GLASS.getDefaultState();
            default -> Blocks.BLACK_STAINED_GLASS.getDefaultState();
        };
        BlockState frame = switch (style) {
            case 0 -> Blocks.WHITE_CONCRETE.getDefaultState();
            case 1 -> Blocks.LIGHT_GRAY_CONCRETE.getDefaultState();
            case 2 -> Blocks.QUARTZ_BLOCK.getDefaultState();
            default -> Blocks.GRAY_CONCRETE.getDefaultState();
        };
        int ax = Math.abs(lx), az = Math.abs(lz);
        if (ax > half || az > half) {
            // площадь перед башней: плитка и клумбы
            c.set(s, (ax + az) % 7 == 0 ? Blocks.POLISHED_ANDESITE.getDefaultState() : Blocks.SMOOTH_STONE.getDefaultState());
            if (ax == half + 3 && az % 6 == 0 && az <= half) c.set(s + 1, Blocks.AZALEA_LEAVES.getDefaultState());
            return;
        }
        boolean wall = ax == half || az == half, corner = ax == half && az == half;
        // ступенчатый верх: верхние этажи уже
        int top = h;
        if (ax > half - 3 || az > half - 3) top = h - 6;
        for (int y = s + 1; y <= s + top; y++) {
            int floor = (y - s) % 4;
            if (wall) {
                boolean door = y <= s + 3 && (lx == 0 || lx == 1) && lz == half;
                if (door) { if (y == s + 1 || y == s + 2) continue; }
                if (corner || floor == 0) c.set(y, frame);
                else {
                    // светящиеся окна вразнобой
                    boolean lit = hash(px * 31 + lx + lz, y, pz) < 0.18;
                    c.set(y, lit && floor == 2 ? Blocks.YELLOW_STAINED_GLASS.getDefaultState() : glass);
                }
            } else if (floor == 0) c.set(y, frame);
            else if (floor == 2 && ax % 6 == 0 && az % 6 == 0) c.set(y, Blocks.SEA_LANTERN.getDefaultState());
        }
        c.set(s + top + 1, frame);
        // крыша
        if (hash(px, pz, 24) < 0.5) {
            if (Math.abs(lx) <= 4 && Math.abs(lz) <= 4) c.set(s + top + 1, Blocks.GRAY_CONCRETE.getDefaultState());
            if ((Math.abs(lx) == 2 && Math.abs(lz) <= 2) || (lz == 0 && Math.abs(lx) <= 2)) c.set(s + top + 1, Blocks.YELLOW_CONCRETE.getDefaultState());
        } else if (lx == 0 && lz == 0) {
            for (int y = 1; y <= 12; y++) c.set(s + top + 1 + y, Blocks.IRON_BARS.getDefaultState());
            c.set(s + top + 14, Blocks.REDSTONE_LAMP.getDefaultState().with(net.minecraft.block.RedstoneLampBlock.LIT, true));
        }
    }

    /** Промзона: склады, трубы с дымом, контейнеры, заборы, граффити. */
    private static void industry(int lx, int lz, long px, long pz, int s, Column c) {
        int kind = (int) (hash(px, pz, 31) * 3);
        int ax = Math.abs(lx), az = Math.abs(lz);
        c.set(s, (lx * 3 + lz) % 5 == 0 ? Blocks.GRAVEL.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState());
        if (ax == 17 || az == 17) { // забор участка
            if (!(lz == 17 && ax <= 3)) for (int y = 1; y <= 2; y++) c.set(s + y, Blocks.IRON_BARS.getDefaultState());
            return;
        }
        if (kind < 2) {
            int hx = 13, hz = 9 + (int) (hash(px, pz, 32) * 5);
            if (ax <= hx && az <= hz) {
                int h = 8 + (int) (hash(px, pz, 33) * 5);
                BlockState wallB = hash(px, pz, 34) < 0.5 ? Blocks.LIGHT_GRAY_TERRACOTTA.getDefaultState() : Blocks.BROWN_TERRACOTTA.getDefaultState();
                boolean wall = ax == hx || az == hz;
                for (int y = s + 1; y <= s + h; y++) {
                    if (wall) {
                        boolean gate = lz == hz && ax <= 3 && y <= s + 5;
                        if (gate) continue;
                        boolean graffiti = az == hz && y <= s + 3 && hash(lx, y, px * 7 + pz) < 0.12;
                        c.set(y, graffiti ? (hash(lx, y, 5) < 0.5 ? Blocks.LIME_CONCRETE : Blocks.MAGENTA_CONCRETE).getDefaultState()
                                : (y - s) % 3 == 0 ? Blocks.GRAY_CONCRETE.getDefaultState() : wallB);
                    }
                }
                c.set(s + h + 1, Blocks.GRAY_CONCRETE.getDefaultState());
                if (ax % 6 == 3 && az < hz - 1) c.set(s + h + 1, Blocks.GLASS.getDefaultState()); // световые полосы на крыше
                // труба с дымом
                if (kind == 0 && lx == hx - 3 && lz == -hz + 3) {
                    for (int y = s + h + 2; y <= s + h + 16; y++) c.set(y, Blocks.BRICKS.getDefaultState());
                    c.set(s + h + 17, Blocks.CAMPFIRE.getDefaultState());
                }
            }
        } else {
            // площадка с контейнерами
            container(lx, lz, px, pz, s, c);
        }
    }

    /** Контейнеры 6×3, в 1–3 яруса, разных цветов. */
    private static void container(int lx, int lz, long px, long pz, int s, Column c) {
        int cx = Math.floorMod(lx + 17, 8), cz = Math.floorMod(lz + 17, 4);
        if (cx >= 7 || cz >= 3) return;
        long id = (long) Math.floorDiv(lx + 17, 8) * 13 + Math.floorDiv(lz + 17, 4) + px * 101 + pz * 977;
        int high = 1 + (int) (hash(id, 1, 41) * 3);
        net.minecraft.block.Block[] colors = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.BROWN_CONCRETE};
        for (int k = 0; k < high; k++) {
            BlockState col = colors[(int) (hash(id, k, 42) * colors.length)].getDefaultState();
            for (int y = 1; y <= 3; y++) c.set(s + k * 3 + y, (cx == 0 || cx == 6) && y != 2 ? Blocks.GRAY_CONCRETE.getDefaultState() : col);
        }
    }

    /** Пригород: четыре домика на участке, газоны, заборчики, деревья во дворах. */
    private static void suburb(int lx, int lz, long px, long pz, int s, Column c) {
        int qx = lx < 0 ? -1 : 1, qz = lz < 0 ? -1 : 1;
        int hx = lx - qx * 9, hz = lz - qz * 9;      // координаты внутри четверти (центр дома в ±9)
        long id = px * 4 + (qx + 1) + (pz * 4 + (qz + 1)) * 131;
        int ax = Math.abs(hx), az = Math.abs(hz);
        if (Math.abs(lx) == 17 || Math.abs(lz) == 17) {
            c.set(s + 1, Blocks.OAK_FENCE.getDefaultState());
            return;
        }
        if (ax <= 4 && az <= 4) {
            net.minecraft.block.Block[] walls = {Blocks.WHITE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.BRICKS, Blocks.OAK_PLANKS};
            net.minecraft.block.Block[] roofs = {Blocks.RED_NETHER_BRICKS, Blocks.DARK_OAK_PLANKS, Blocks.DEEPSLATE_TILES, Blocks.SPRUCE_PLANKS};
            BlockState wall = walls[(int) (hash(id, 0, 51) * walls.length)].getDefaultState();
            BlockState roof = roofs[(int) (hash(id, 0, 52) * roofs.length)].getDefaultState();
            boolean edge = ax == 4 || az == 4;
            for (int y = s + 1; y <= s + 4; y++) {
                if (!edge) { if (y == s + 4) c.set(y, Blocks.OAK_PLANKS.getDefaultState()); continue; }
                boolean window = (y == s + 2 || y == s + 3) && (ax == 2 || az == 2) && !(ax == 4 && az == 4);
                boolean door = hz * -qz == 4 && hx == 0 && y <= s + 2;
                if (door) continue;
                c.set(y, window ? Blocks.GLASS_PANE.getDefaultState() : wall);
            }
            // двускатная крыша
            int roofH = 4 - ax;
            for (int k = 0; k <= Math.max(0, roofH); k++) c.set(s + 5 + k, roof);
            return;
        }
        // двор: газон, дорожка к двери, дерево
        if (hx == 0 && hz * -qz > 4) c.set(s, Blocks.DIRT_PATH.getDefaultState());
        if (ax == 6 && az == 6 && hash(id, 1, 53) < 0.6) tree(s, c, 4 + (int) (hash(id, 2, 54) * 2), false);
        else if (ax <= 7 && az <= 7 && (Math.abs(ax - 6) <= 1 && Math.abs(az - 6) <= 1) && hash(id, 1, 53) < 0.6) leaves(s, c, 4 + (int) (hash(id, 2, 54) * 2), ax == 6 || az == 6);
    }

    private static void tree(int s, Column c, int h, boolean birch) {
        for (int y = 1; y <= h; y++) c.set(s + y, (birch ? Blocks.BIRCH_LOG : Blocks.OAK_LOG).getDefaultState());
        for (int y = h - 1; y <= h + 1; y++) if (y > h) c.set(s + y, Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
    }

    private static void leaves(int s, Column c, int h, boolean near) {
        for (int y = h - 1; y <= h + (near ? 1 : 0); y++) c.set(s + y, Blocks.OAK_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
    }

    // ------------------------------------------------------------------ порт, пляж, аэродром, загород
    private static void port(int x, int z, int s, Column c) {
        double cx = coastX(z);
        // причалы-пирсы в море: бетон на сваях, между ними вода
        boolean pier = x >= cx || Math.floorMod(z, 60) < 26;
        if (!pier) {
            for (int y = G - 6; y <= G - 1; y++) c.set(y, y <= G - 4 ? SAND : WATER);
            c.set(G, AIR);
            return;
        }
        c.set(s, Blocks.SMOOTH_STONE.getDefaultState());
        if (x < cx) for (int y = G - 6; y < s; y++) c.set(y, Math.floorMod(x, 6) == 0 && Math.floorMod(z, 6) == 0 ? Blocks.STRIPPED_SPRUCE_LOG.getDefaultState() : WATER);
        int lx = Math.floorMod(x, 48), lz = Math.floorMod(z, 60);
        // край причала — жёлтая полоса и швартовые тумбы
        if (x < cx && (lz == 0 || lz == 25)) {
            c.set(s, Blocks.YELLOW_CONCRETE.getDefaultState());
            if (Math.floorMod(x, 8) == 0) c.set(s + 1, Blocks.POLISHED_BLACKSTONE_WALL.getDefaultState());
            return;
        }
        // портальный кран
        if (lx == 10 && (lz == 3 || lz == 22) && x < cx) {
            for (int y = 1; y <= 22; y++) c.set(s + y, Blocks.YELLOW_CONCRETE.getDefaultState());
            return;
        }
        if (lx == 10 && lz > 3 && lz < 22 && x < cx) {
            c.set(s + 22, Blocks.YELLOW_CONCRETE.getDefaultState());
            if (lz == 12) for (int y = 14; y <= 21; y++) c.set(s + y, Blocks.CHAIN.getDefaultState());
            return;
        }
        // штабеля контейнеров на берегу
        if (x >= cx && x < cx + 36) container(Math.floorMod(x, 34) - 17, Math.floorMod(z, 34) - 17, Math.floorDiv(x, 34), Math.floorDiv(z, 34), s, c);
    }

    private static void beach(int x, int z, int s, Column c) {
        double cx = coastX(z);
        // пальмы, зонтики и вышка спасателей
        long cellX = Math.floorDiv(x, 14), cellZ = Math.floorDiv(z, 14);
        int lx = Math.floorMod(x, 14), lz = Math.floorMod(z, 14);
        double r = hash(cellX, cellZ, 61);
        if (r < 0.45 && lx == 7 && lz == 7) {
            for (int y = 1; y <= 6; y++) c.set(s + y, Blocks.JUNGLE_LOG.getDefaultState());
            c.set(s + 7, Blocks.JUNGLE_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
        } else if (r < 0.45 && (Math.abs(lx - 7) + Math.abs(lz - 7) == 1)) {
            c.set(s + 7, Blocks.JUNGLE_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
            c.set(s + 6, Blocks.JUNGLE_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
        } else if (r < 0.45 && (Math.abs(lx - 7) == 2 && lz == 7 || Math.abs(lz - 7) == 2 && lx == 7 || Math.abs(lx - 7) == 1 && Math.abs(lz - 7) == 1)) {
            c.set(s + 6, Blocks.JUNGLE_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true)); // свисающие листья
        } else if (r > 0.6 && r < 0.75 && lz == 3 && (lx == 3 || lx == 4)) {
            c.set(s + 1, (lx == 3 ? Blocks.WHITE_CARPET : Blocks.LIGHT_BLUE_CARPET).getDefaultState()); // шезлонг/полотенце
        } else if (r > 0.75 && lx == 4 && lz == 4) {
            c.set(s + 1, Blocks.OAK_FENCE.getDefaultState());
            c.set(s + 2, Blocks.OAK_FENCE.getDefaultState());
            c.set(s + 3, (r > 0.88 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL).getDefaultState());
        } else if (r > 0.75 && Math.abs(lx - 4) <= 1 && Math.abs(lz - 4) <= 1) {
            c.set(s + 3, (r > 0.88 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL).getDefaultState());
        }
        // мокрый песок у воды
        if (x < cx + 6) c.set(s, Blocks.SANDSTONE.getDefaultState());
    }

    private static void airfield(int x, int z, int s, Column c) {
        int rz = Math.abs(z + 905);
        if (rz <= 15 && x > 320 && x < 880) {
            c.set(s, ModContent.ASPHALT.getDefaultState());
            if (rz == 0 && Math.floorMod(x, 20) < 12) c.set(s, Blocks.WHITE_CONCRETE.getDefaultState());
            if (rz == 14 && Math.floorMod(x, 10) == 0) c.set(s + 1, Blocks.REDSTONE_LAMP.getDefaultState().with(net.minecraft.block.RedstoneLampBlock.LIT, true));
            if ((x < 340 || x > 860) && rz < 12 && Math.floorMod(rz, 3) == 0) c.set(s, Blocks.WHITE_CONCRETE.getDefaultState());
            return;
        }
        // ангары и диспетчерская вышка
        int hx = Math.floorMod(x - 400, 90), hz = z + 860;
        if (hz >= -20 && hz <= 0 && hx < 30 && x < 820) {
            boolean edge = hx == 0 || hx == 29 || hz == -20;
            for (int y = 1; y <= 10; y++) if (edge && !(hz == 0) || y == 10) c.set(s + y, Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
            return;
        }
        if (x >= 850 && x <= 856 && z >= -870 && z <= -864) {
            boolean edge = x == 850 || x == 856 || z == -870 || z == -864;
            for (int y = 1; y <= 24; y++) if (edge || y == 24) c.set(s + y, y > 18 ? Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState());
            c.set(s + 25, Blocks.WHITE_CONCRETE.getDefaultState());
            return;
        }
        c.set(s, Blocks.GRASS_BLOCK.getDefaultState());
    }

    /** Ривьера: курортные отели и казино — ниже небоскрёбов, белые с цветными козырьками, неон и пальмы. */
    private static void resort(int lx, int lz, long px, long pz, int s, Column c) {
        double r = hash(px, pz, 81);
        int half = 13 + (int) (hash(px, pz, 82) * 3);
        boolean casino = r < 0.35;
        int h = casino ? 14 + (int) (hash(px, pz, 83) * 8) : 22 + (int) (hash(px, pz, 84) * 30);
        int ax = Math.abs(lx), az = Math.abs(lz);
        net.minecraft.block.Block[] accents = {Blocks.PINK_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.LIME_CONCRETE, Blocks.MAGENTA_CONCRETE};
        BlockState accent = accents[(int) (hash(px, pz, 85) * accents.length)].getDefaultState();
        if (ax > half || az > half) {
            c.set(s, (ax + az) % 4 == 0 ? Blocks.SMOOTH_SANDSTONE.getDefaultState() : Blocks.CUT_SANDSTONE.getDefaultState());
            if (ax == half + 2 && az % 8 == 0 && az <= half) { // пальмы вдоль фасада
                for (int y = 1; y <= 5; y++) c.set(s + y, Blocks.JUNGLE_LOG.getDefaultState());
                c.set(s + 6, Blocks.JUNGLE_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
            }
            if (casino && lz == half + 2 && ax <= half) c.set(s + 1, Blocks.RED_CARPET.getDefaultState()); // красная дорожка к казино
            return;
        }
        boolean wall = ax == half || az == half;
        for (int y = s + 1; y <= s + h; y++) {
            int floor = (y - s) % 4;
            if (wall) {
                boolean door = y <= s + 3 && ax <= 2 && lz == half;
                if (door) { if (y <= s + 3) continue; }
                if (casino && y >= s + h - 3) c.set(y, NeonBlockState((int) (px * 7 + pz + y)).getBlock() == ModContent.NEON_FLOOR ? ModContent.NEON.getDefaultState() : accent);
                else if (floor == 0) c.set(y, accent);
                else c.set(y, casino ? Blocks.GOLD_BLOCK.getDefaultState() : (hash(px * 17 + lx + lz, y, pz) < 0.22 ? Blocks.YELLOW_STAINED_GLASS : Blocks.WHITE_STAINED_GLASS).getDefaultState());
            } else if (floor == 0) c.set(y, Blocks.WHITE_CONCRETE.getDefaultState());
            else if (floor == 2 && ax % 5 == 0 && az % 5 == 0) c.set(y, Blocks.SEA_LANTERN.getDefaultState());
        }
        c.set(s + h + 1, Blocks.WHITE_CONCRETE.getDefaultState());
        // бассейн на крыше отеля
        if (!casino && ax <= 5 && az <= 4) c.set(s + h + 1, ax == 5 || az == 4 ? Blocks.WHITE_CONCRETE.getDefaultState() : WATER);
    }

    /** Пустошь: кактусы, сухие кусты, редкие валуны. */
    private static void desert(int x, int z, int s, Column c) {
        double r = hash(x, z, 92);
        if (r < 0.004) for (int y = 1; y <= 2 + (int) (hash(x, z, 93) * 2); y++) c.set(s + y, Blocks.CACTUS.getDefaultState());
        else if (r < 0.02) c.set(s + 1, Blocks.DEAD_BUSH.getDefaultState());
        else if (r < 0.022) c.set(s + 1, Blocks.COBBLESTONE.getDefaultState());
    }

    private static void country(int x, int z, int s, Column c) {
        // лес гуще на севере, дачи у дорог
        double forest = noise(x, z, 70, 71) + Math.max(0, Math.min(0.25, (-z - 600) / 800.0));
        long cellX = Math.floorDiv(x, 7), cellZ = Math.floorDiv(z, 7);
        int lx = Math.floorMod(x, 7), lz = Math.floorMod(z, 7);
        double r = hash(cellX, cellZ, 72);
        int tx = 1 + (int) (hash(cellX, cellZ, 73) * 5), tz = 1 + (int) (hash(cellX, cellZ, 74) * 5);
        boolean birch = hash(cellX, cellZ, 75) < 0.3;
        if (forest > 0.45 && r < forest) {
            int h = 5 + (int) (hash(cellX, cellZ, 76) * 3);
            int d = Math.max(Math.abs(lx - tx), Math.abs(lz - tz));
            if (d == 0) {
                for (int y = 1; y <= h; y++) c.set(s + y, (birch ? Blocks.BIRCH_LOG : Blocks.SPRUCE_LOG).getDefaultState());
                c.set(s + h + 1, (birch ? Blocks.BIRCH_LEAVES : Blocks.SPRUCE_LEAVES).getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
                return;
            }
            if (d == 1) for (int y = h - 2; y <= h; y++) c.set(s + y, (birch ? Blocks.BIRCH_LEAVES : Blocks.SPRUCE_LEAVES).getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true));
            return;
        }
        if (forest < 0.3 && r < 0.04 && lx == 3 && lz == 3) c.set(s + 1, Blocks.HAY_BLOCK.getDefaultState());
        if (forest < 0.4 && r < 0.3) c.set(s + 1, (hash(x, z, 77) < 0.5 ? Blocks.SHORT_GRASS : Blocks.DANDELION).getDefaultState());
    }

    // ------------------------------------------------------------------ карта (M)
    /** Цвет точки для карты острова. */
    public static int mapColor(int x, int z) {
        Zone zn = zone(x, z);
        if (zn == Zone.SEA) return highwayDist(x, z) <= 7 ? 0xFF606670 : 0xFF1E5A8C;
        if (highwayDist(x, z) <= 7) return 0xFF505560;
        switch (zn) {
            case BEACH: return 0xFFE8D69A;
            case PORT: return x < coastX(z) && !(Math.floorMod(z, 60) < 26) ? 0xFF1E5A8C : 0xFF9A9A9A;
            case AIRFIELD: return Math.abs(z + 905) <= 15 && x > 320 && x < 880 ? 0xFF3A3D44 : 0xFF6E9E4A;
            case COUNTRY: {
                int h = surface(x, z) - G;
                if (z > 1050 && x > -840) return h > 15 ? 0xFFB5653A : 0xFFD9A066;
                if (h > 62) return 0xFFF2F4F7;
                if (h > 48) return 0xFF8A8A8A;
                int g = 0x5A + Math.min(60, h * 2);
                return noise(x, z, 70, 71) > 0.45 ? 0xFF2F6A30 : 0xFF000000 | (g / 2) << 16 | g << 8 | 0x40;
            }
            default: {
                if (zn == Zone.CORE) return 0xFF7A8A78;
                int road = Math.min(gridDist(x), gridDist(z));
                if (road <= 4) return 0xFF3E4148;
                if (road <= 6) return 0xFFB8B8B0;
                return zn == Zone.DOWNTOWN ? 0xFF7FA3C8 : zn == Zone.RIVIERA ? 0xFFE8B4C8 : zn == Zone.INDUSTRIAL ? 0xFF8C7A62 : 0xFF8DB86A;
            }
        }
    }

    /** Точки сюжета на острове: x, z, yaw (y — по земле). Улицы на линиях ≡ 24 (mod 48), тротуары — ±5 от них. */
    private static final java.util.Map<String, double[]> PLACES = new java.util.HashMap<>();

    static {
        // гонки: старт на северной трассе, круг по кольцу
        put("race_start", 120, -744, 270);
        put("race_1", 420, -744, 270);
        put("race_2", 744, -600, 0);
        put("race_3", 744, -200, 0);
        put("race_4", 744, 24, 0);
        put("race_5", 400, 24, 90);
        put("race_finish", 168, 24, 90);
        // второй заезд: по деловому центру
        put("dt_1", 216, -168, 270);
        put("dt_2", 504, -168, 270);
        put("dt_3", 504, -504, 180);
        put("dt_4", 216, -504, 90);
        put("dt_5", 216, -264, 0);
        // места
        put("downtown_plaza", 432, -413, 180);
        put("bank_tower", 480, -317, 180);
        put("gang_hq", 432, 451, 180);
        put("gang_yard", 432, 432, 180);
        put("port_dock", -640, 10, 90);
        put("port_gate", -560, 24, 90);
        put("port_warehouse", -585, -60, 90);
        put("beach_bar", -603, -560, 90);
        put("hill_top", -200, -870, 0);
        put("airfield", 600, -905, 270);
        put("airfield_hangar", 460, -870, 0);
        put("suburb_safehouse", -312, 269, 180);
        put("industry_street", 408, 300, 180);
        put("hw_north", 24, -700, 0);
        put("hw_west", -552, -300, 0);
        // новые места сюжета 0.2
        put("suburb_street", -264, 216, 0);
        put("suburb_house", -259, 232, 180);
        put("club_neon", 365, -269, 180);
        put("dt_alley", 317, -216, 90);
        put("chop_shop", 509, 552, 180);
        put("port_crane", -600, 70, 90);
        put("beach_lot", -598, -520, 90);
        put("hw_nw", -552, -720, 0);
        put("hw_south", 200, 744, 90);
        put("hw_east", 744, 400, 0);
        put("hw_sw", -552, 600, 0);
        put("casino_dt", 600, -600, 180);
        put("farm", -400, -820, 0);
        // камеры кат-сцен
        cam("cam_club", 360, 25, -230, 180, 20);
        cam("cam_suburb", -240, 18, 250, 145, 20);
        cam("cam_chop", 540, 22, 590, 141, 22);
        cam("cam_beach", -560, 18, -495, 122, 15);
        cam("cam_casino", 640, 40, -560, 135, 20);
        cam("cam_hw_south", 230, 15, 760, 60, 10);
        // 0.3: Ривьера, мосты, горы, пустошь, озеро, деревня
        put("riv_strip", -1320, 24, 270);
        put("riv_casino", -1315, -40, 180);
        put("riv_hotel", -1171, 200, 90);
        put("riv_marina", -1850, 300, 270);
        put("riv_beach", -1850, -150, 270);
        put("riv_villa", -1699, -600, 90);
        put("riv_villa2", -1699, 560, 90);
        put("riv_docks", -905, 0, 90);
        put("bridge_n", -700, -744, 270);
        put("bridge_s", -700, 744, 270);
        put("riv_ring_w", -1800, 0, 0);
        put("riv_ring_e", -920, -300, 0);
        put("village", 1464, 24, 90);
        put("village_farm", 1500, 230, 0);
        put("lake_dock", 1320, 600, 180);
        put("mtn_road", 24, -1500, 0);
        put("mtn_top", 312, -1500, 0);
        put("desert_road", 24, 1400, 180);
        put("desert_mesa", 520, 1400, 90);
        put("outer_east", 1700, 0, 0);
        put("outer_ne", 1700, -1700, 270);
        put("outer_se", 1700, 1700, 90);
        put("outer_nw", -920, -1700, 90);
        // магазины стиля (строит ShopPlacer при первом приближении игрока)
        put("shop_clothes", -240, 125, 0);
        put("shop_barber", -336, 125, 0);
        put("shop_gym", -432, 125, 0);
        put("shop_boutique", -1680, 125, 0);
        cam("cam_riv_far", -1100, 90, 300, 144, 25);
        cam("cam_riv_strip", -1320, 6, 120, 180, 0);
        cam("cam_bridge", -640, 20, -700, 126, 10);
        cam("cam_mtn", 200, 40, -1350, 200, 15);
        cam("cam_desert", 450, 25, 1300, 30, 10);
        cam("cam_island_far", 260, 160, 260, 135, 38);
        cam("cam_dt_a", 300, 70, -120, 210, 18);
        cam("cam_dt_b", 456, 4, -330, 180, -6);
        cam("cam_port_a", -480, 34, 70, 100, 18);
        cam("cam_port_b", -590, 8, 30, 80, 4);
        cam("cam_ind_a", 360, 42, 320, 210, 28);
        cam("cam_ind_b", 432, 5, 470, 180, 2);
        cam("cam_air_a", 520, 30, -840, 110, 14);
    }

    private static void put(String name, double x, double z, float yaw) {
        PLACES.put(name, new double[]{x + 0.5, 0, z + 0.5, yaw, 0});
    }

    /** Камера для кат-сцен: высота над землёй и наклон. */
    private static void cam(String name, double x, double up, double z, float yaw, float pitch) {
        PLACES.put(name, new double[]{x + 0.5, up, z + 0.5, yaw, pitch});
    }

    /** Точка сюжета (или null). y — по поверхности острова. */
    public static double[] place(String name) {
        double[] v = PLACES.get(name);
        if (v == null) return null;
        return new double[]{v[0], surface((int) Math.floor(v[0]), (int) Math.floor(v[2])) + 1 + v[1], v[2], v[3], v[4]};
    }

    /** Подписи районов на карте и точки для сюжета. */
    public static final String[][] LABELS = {
            {"Деловой центр", "430", "-430"}, {"Промзона «Чёрных Ящеров»", "430", "430"}, {"Порт", "-640", "0"},
            {"Пляж", "-610", "-520"}, {"Аэродром", "600", "-905"}, {"Холмы и лес", "-200", "-800"}, {"Пригород", "-300", "300"},
            {"РИВЬЕРА", "-1320", "0"}, {"Пляж Ривьеры", "-1860", "-200"}, {"Северный мост", "-700", "-744"}, {"Южный мост", "-700", "744"},
            {"Горы Седые", "300", "-1450"}, {"Пустошь", "500", "1400"}, {"Озеро Ясное", "1320", "760"}, {"Деревня Сосновка", "1450", "0"},
            {"Шмот-Базар", "-240", "135"}, {"Цирюльня", "-336", "135"}, {"Зал «Железо»", "-432", "135"}, {"Бутик «Бархат»", "-1680", "135"}};
}
