package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.block.ElevatorFloorBlock;
import com.yompix.novograd.block.NeonBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * Здания острова изнутри: небоскрёбы и отели с лифтами, лестницами, лобби, офисами, квартирами и номерами,
 * казино Ривьеры, домики пригорода с дверями и обстановкой, склады со стеллажами.
 * Всё описано по столбцам: каждый (lx, lz) участка сам решает, что стоит на каждом этаже — без соседей и без мира.
 * <pre>
 * Ядро любой высотки (координаты от центра участка, вход с юга, +z):
 *   лестница  lx −4..3, lz −9..−5: два марша (lz −8 вверх на восток, lz −6 вверх на запад), стенка lz −7, двери в lz −5
 *   лифты     lx 6..8,  lz −2..2:  две кабины (7, ±1), двери на запад (lx 6), панель на задней стенке (8, ±1)
 *   этаж      4 блока: перекрытие на s+4k, жить на s+4k+1..+3; крыша — s+4n, туда тоже ведут лифт и лестница
 * </pre>
 */
final class Buildings {
    private Buildings() {}

    static final int OFFICE = 0, HOMES = 1, HOTEL = 2;
    static final int HELI = 0, ANTENNA = 1, POOL = 2;

    private static double hash(long x, long z, long salt) {
        return Island.hash(x, z, salt);
    }

    // ------------------------------------------------------------------ состояния блоков
    private static BlockState st(Block b) {
        return b.getDefaultState();
    }

    private static BlockState stairs(Block b, Direction f) {
        return b.getDefaultState().with(StairsBlock.FACING, f);
    }

    private static BlockState slabTop(Block b) {
        return b.getDefaultState().with(SlabBlock.TYPE, SlabType.TOP);
    }

    private static BlockState trapTop(Block b) {
        return b.getDefaultState().with(TrapdoorBlock.HALF, BlockHalf.TOP);
    }

    private static BlockState facing(Block b, Direction d) {
        return b.getDefaultState().with(Properties.HORIZONTAL_FACING, d);
    }

    private static BlockState hanging() {
        return Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
    }

    private static BlockState water3() {
        return Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3);
    }

    private static BlockState leaves(Block b) {
        return b.getDefaultState().with(LeavesBlock.PERSISTENT, true);
    }

    private static void door(Island.Column c, int y, Block b, Direction inward, DoorHinge hinge) {
        BlockState d = b.getDefaultState().with(DoorBlock.FACING, inward).with(DoorBlock.HINGE, hinge).with(DoorBlock.OPEN, false);
        c.set(y, d.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(y + 1, d.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void bed(Island.Column c, int y, Block b, Direction toHead, boolean head) {
        c.set(y, b.getDefaultState().with(BedBlock.FACING, toHead).with(BedBlock.PART, head ? BedPart.HEAD : BedPart.FOOT));
    }

    private static final Block[] CARPETS = {Blocks.GRAY_CARPET, Blocks.BLUE_CARPET, Blocks.LIGHT_GRAY_CARPET, Blocks.CYAN_CARPET, Blocks.GREEN_CARPET, Blocks.BROWN_CARPET};
    private static final Block[] RUGS = {Blocks.RED_CARPET, Blocks.ORANGE_CARPET, Blocks.YELLOW_CARPET, Blocks.LIGHT_BLUE_CARPET, Blocks.PURPLE_CARPET, Blocks.WHITE_CARPET, Blocks.LIME_CARPET};
    private static final Block[] BEDS = {Blocks.RED_BED, Blocks.BLUE_BED, Blocks.WHITE_BED, Blocks.GREEN_BED, Blocks.YELLOW_BED, Blocks.LIGHT_BLUE_BED, Blocks.PINK_BED};
    private static final Block[] PLANKS = {Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.JUNGLE_PLANKS};
    private static final Block[] POTS = {Blocks.POTTED_FERN, Blocks.POTTED_BAMBOO, Blocks.POTTED_FLOWERING_AZALEA_BUSH, Blocks.POTTED_AZALEA_BUSH, Blocks.POTTED_CACTUS};

    // ================================================================== высотки
    /** Параметры одной высотки (всё — от номера участка). */
    record Spec(long px, long pz, int half, int n, BlockState frame, BlockState glass, BlockState lit, int use, int roof, Block carpet, Block planks) {}

    private static final BlockState STAIR_WALL = st(Blocks.SMOOTH_STONE), STAIR_FLOOR = st(Blocks.POLISHED_ANDESITE),
            LIFT_WALL = st(Blocks.POLISHED_ANDESITE), LIFT_FRONT = st(Blocks.SMOOTH_QUARTZ), LIGHT = st(Blocks.SEA_LANTERN);
    private static final Block STEP = Blocks.POLISHED_ANDESITE_STAIRS;

    static boolean inStair(int lx, int lz) {
        return lx >= -4 && lx <= 3 && lz >= -9 && lz <= -5;
    }

    static boolean inLift(int lx, int lz) {
        return lx >= 6 && lx <= 8 && lz >= -2 && lz <= 2;
    }

    /** Проходы к лестнице и лифтам — мебель сюда не ставим. */
    static boolean coreZone(int lx, int lz) {
        return (lx >= -5 && lx <= 4 && lz >= -10 && lz <= -3) || (lx >= 4 && lx <= 9 && lz >= -4 && lz <= 3);
    }

    /** Небоскрёб делового центра. */
    static void tower(int lx, int lz, long px, long pz, int s, Island.Column c) {
        double r = hash(px, pz, 21);
        int half = 13 + (int) (hash(px, pz, 22) * 4);              // 13..16
        double centerBoost = 1 - Math.min(1, Math.hypot(px * 48 - 430, pz * 48 + 430) / 380.0);
        int n = 6 + (int) (hash(px, pz, 23) * 10 + centerBoost * 12); // 6..27 этажей
        int style = (int) (r * 4);
        BlockState glass = st(switch (style) {
            case 0 -> Blocks.LIGHT_BLUE_STAINED_GLASS;
            case 1 -> Blocks.GRAY_STAINED_GLASS;
            case 2 -> Blocks.CYAN_STAINED_GLASS;
            default -> Blocks.BLACK_STAINED_GLASS;
        });
        BlockState frame = st(switch (style) {
            case 0 -> Blocks.WHITE_CONCRETE;
            case 1 -> Blocks.LIGHT_GRAY_CONCRETE;
            case 2 -> Blocks.QUARTZ_BLOCK;
            default -> Blocks.GRAY_CONCRETE;
        });
        int use = hash(px, pz, 25) < 0.3 ? HOMES : OFFICE;
        int roof = hash(px, pz, 24) < 0.5 ? HELI : ANTENNA;
        Spec sp = new Spec(px, pz, half, n, frame, glass, st(Blocks.YELLOW_STAINED_GLASS), use, roof,
                CARPETS[(int) (hash(px, pz, 26) * CARPETS.length)], PLANKS[(int) (hash(px, pz, 27) * PLANKS.length)]);
        int ax = Math.abs(lx), az = Math.abs(lz);
        if (ax > half || az > half) {
            plaza(lx, lz, s, c, sp);
            return;
        }
        highrise(lx, lz, s, c, sp);
    }

    /** Площадь перед башней: плитка, клумбы, козырёк над входом. */
    private static void plaza(int lx, int lz, int s, Island.Column c, Spec sp) {
        int ax = Math.abs(lx), az = Math.abs(lz), half = sp.half();
        boolean hotel = sp.use() == HOTEL;
        if (hotel) c.set(s, (ax + az) % 4 == 0 ? st(Blocks.SMOOTH_SANDSTONE) : st(Blocks.CUT_SANDSTONE));
        else c.set(s, (ax + az) % 7 == 0 ? st(Blocks.POLISHED_ANDESITE) : st(Blocks.SMOOTH_STONE));
        boolean entrance = lz > half && lx >= -1 && lx <= 2;
        if (entrance) {
            if (lz <= half + 2) c.set(s + 4, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState()); // козырёк над дверями
            if (lx == 0 || lx == 1) c.set(s + 1, st(hotel ? Blocks.RED_CARPET : Blocks.GRAY_CARPET));
            return;
        }
        if (hotel) {
            if (ax == half + 2 && az % 8 == 0 && az <= half) { // пальмы вдоль фасада
                for (int y = 1; y <= 5; y++) c.set(s + y, st(Blocks.JUNGLE_LOG));
                c.set(s + 6, leaves(Blocks.JUNGLE_LEAVES));
            }
            return;
        }
        if (ax == half + 3 && az % 6 == 0 && az <= half) c.set(s + 1, leaves(Blocks.AZALEA_LEAVES));
        if (lz == half + 1 && (lx == -3 || lx == 4)) c.set(s + 1, st(Blocks.POTTED_BAMBOO));
    }

    /** Роль столбца на этаже k: 0 — пусто (терраса/выше крыши), 1 — фасад, 2 — внутри. */
    private static int role(int m, int k, Spec sp) {
        if (k < 0 || k >= sp.n()) return 0;
        int wall = k >= sp.n() - 2 ? sp.half() - 3 : sp.half();
        if (m > wall) return 0;
        return m == wall ? 1 : 2;
    }

    /** Каркас высотки: перекрытия, фасад, ядро, начинка этажей и крыша. */
    static void highrise(int lx, int lz, int s, Island.Column c, Spec sp) {
        int n = sp.n(), inner = sp.half() - 3;
        int ax = Math.abs(lx), az = Math.abs(lz), m = Math.max(ax, az);
        boolean outer = m > inner;
        int maxK = outer ? n - 2 : n;
        for (int k = 0; k <= maxK; k++) {
            int L = s + 4 * k;
            int role = role(m, k, sp), below = role(m, k - 1, sp);
            BlockState slab = slab(lx, lz, k, role, below, sp);
            if (slab != null) c.set(L, slab);
            if (k == maxK) {
                if (outer) terrace(lx, lz, L, c, sp);
                else roof(lx, lz, L, c, sp);
                break;
            }
            if (role == 1) facade(lx, lz, k, L, c, sp);
            else if (role == 2) interior(lx, lz, k, L, c, sp, (k >= n - 2 ? inner : sp.half()) - 1);
        }
    }

    private static BlockState slab(int lx, int lz, int k, int role, int below, Spec sp) {
        int n = sp.n();
        if (inLift(lx, lz) && (role == 2 || below == 2)) return liftSlab(lx, lz, k, k == n);
        if (inStair(lx, lz) && (role == 2 || below == 2)) return stairSlab(lx, lz, k);
        if (role == 1 || below == 1) return sp.frame();
        if (k == 0) return lobbyFloor(lx, lz, sp);
        if (role == 0) return k == n ? (sp.roof() == POOL ? st(Blocks.SMOOTH_SANDSTONE) : sp.frame()) : st(Blocks.SMOOTH_STONE);
        // перекрытие между этажами: снизу — потолок со светильниками, сверху — пол
        int W = (k - 1 >= n - 2 ? sp.half() - 3 : sp.half()) - 1;
        if (below == 2 && light(lx, lz, k - 1, sp, W)) return LIGHT;
        return floorMat(lx, lz, k, sp, (k >= n - 2 ? sp.half() - 3 : sp.half()) - 1);
    }

    /** Светильник в потолке этажа k (в перекрытии над ним). */
    private static boolean light(int lx, int lz, int k, Spec sp, int W) {
        if (coreZone(lx, lz) && !(lx >= 4 && lx <= 5)) return false;
        if (inLift(lx, lz) || inStair(lx, lz)) return false;
        if (k == 0) return Math.floorMod(lx, 4) == 2 && Math.floorMod(lz, 4) == 2;
        if (sp.use() == OFFICE) return Math.floorMod(lx + 1, 4) == 3 && Math.floorMod(lz + 1, 5) == 3;
        int u = unit(lx, lz, W);
        if (u == -1) return (lz == -3 && Math.floorMod(lx, 4) == 0) || (lx >= 1 && lz == 1 && Math.floorMod(lx, 4) == 2);
        return false; // в квартирах — подвесные фонари
    }

    private static BlockState floorMat(int lx, int lz, int k, Spec sp, int W) {
        if (sp.use() == OFFICE) return st(Blocks.LIGHT_GRAY_CONCRETE);
        int u = unit(lx, lz, W);
        if (u >= 0 && !unitWall(lx, lz)) return st(sp.use() == HOTEL ? Blocks.SPRUCE_PLANKS : PLANKS[(int) (hash(sp.px() * 7 + u, sp.pz() + k, 28) * PLANKS.length)]);
        return st(Blocks.SMOOTH_STONE);
    }

    private static BlockState lobbyFloor(int lx, int lz, Spec sp) {
        if (sp.use() == HOTEL) return (Math.floorMod(lx, 4) == 0 || Math.floorMod(lz, 4) == 0) ? st(Blocks.CUT_SANDSTONE) : st(Blocks.SMOOTH_QUARTZ);
        return (Math.floorMod(lx, 5) == 0 || Math.floorMod(lz, 5) == 0) ? st(Blocks.POLISHED_ANDESITE) : st(Blocks.POLISHED_DIORITE);
    }

    // ------------------------------------------------------------------ фасад
    private static void facade(int lx, int lz, int k, int L, Island.Column c, Spec sp) {
        int n = sp.n(), wall = k >= n - 2 ? sp.half() - 3 : sp.half();
        int ax = Math.abs(lx), az = Math.abs(lz);
        boolean corner = ax == wall && az == wall;
        // главный вход: двойная стеклянная дверь
        if (k == 0 && lz == sp.half() && (lx == 0 || lx == 1)) {
            door(c, L + 1, Blocks.WAXED_COPPER_DOOR, Direction.NORTH, lx == 0 ? DoorHinge.LEFT : DoorHinge.RIGHT);
            c.set(L + 3, sp.glass());
            return;
        }
        // выход на террасу с верхних этажей офисов
        if (k == n - 2 && sp.use() == OFFICE && lz == wall && (lx == 0 || lx == 1)) {
            door(c, L + 1, Blocks.WAXED_COPPER_DOOR, Direction.SOUTH, lx == 0 ? DoorHinge.RIGHT : DoorHinge.LEFT);
            c.set(L + 3, sp.glass());
            return;
        }
        boolean lit = hash(sp.px() * 31 + lx * 7L + lz * 3L, k, sp.pz()) < 0.2;
        for (int y = L + 1; y <= L + 3; y++) c.set(y, corner ? sp.frame() : (lit && y == L + 2) ? sp.lit() : sp.glass());
    }

    // ------------------------------------------------------------------ ядро: лестница и лифты
    private static BlockState stairSlab(int lx, int lz, int k) {
        boolean wallX = lx == -4 || lx == 3, outerZ = lz == -9, innerZ = lz == -5, mid = lz == -7 && lx >= -2 && lx <= 1;
        if (wallX || outerZ || innerZ || mid) return STAIR_WALL;
        if (k == 0) return STAIR_FLOOR;
        int prev = k - 1, laneP = prev % 2 == 0 ? -8 : -6;
        if (lz == laneP) {
            int j = prev % 2 == 0 ? lx + 2 : 1 - lx;
            if (j >= 0 && j <= 2) return null; // проём над маршем
            if (j == 3) return stairs(STEP, prev % 2 == 0 ? Direction.EAST : Direction.WEST); // верхняя ступень марша
        }
        return STAIR_FLOOR;
    }

    private static void stairContent(int lx, int lz, int k, int L, Island.Column c, boolean roof) {
        boolean wallX = lx == -4 || lx == 3, outerZ = lz == -9, innerZ = lz == -5, mid = lz == -7 && lx >= -2 && lx <= 1;
        boolean doorway = innerZ && (lx == -3 || lx == 2);
        if (wallX || outerZ || mid || (innerZ && !doorway)) for (int y = L + 1; y <= L + 3; y++) c.set(y, STAIR_WALL);
        if (doorway) c.set(L + 3, LIGHT); // свет над дверью на лестницу
        if (outerZ && (lx == -3 || lx == 2)) c.set(L + 3, LIGHT);
        if (roof) {
            c.set(L + 4, STAIR_WALL); // будка выхода на крышу
            return;
        }
        if (wallX || outerZ || mid || innerZ) return;
        int lane = k % 2 == 0 ? -8 : -6;
        if (lz != lane) return;
        int j = k % 2 == 0 ? lx + 2 : 1 - lx;
        if (j >= 0 && j <= 2) c.set(L + 1 + j, stairs(STEP, k % 2 == 0 ? Direction.EAST : Direction.WEST));
    }

    private static BlockState liftSlab(int lx, int lz, int k, boolean roof) {
        if (lx == 7 && (lz == -1 || lz == 1))
            return CityBlocks.ELEVATOR_FLOOR.getDefaultState().with(ElevatorFloorBlock.FACING, Direction.WEST).with(ElevatorFloorBlock.ROOF, roof);
        return LIFT_WALL;
    }

    private static void liftContent(int lx, int lz, int L, Island.Column c, boolean roof) {
        boolean cabin = lx == 7 && (lz == -1 || lz == 1), doorway = lx == 6 && (lz == -1 || lz == 1);
        if (roof) c.set(L + 4, LIFT_WALL);
        if (cabin) return;
        if (doorway) {
            c.set(L + 3, st(Blocks.POLISHED_BLACKSTONE)); // табло над дверью
            return;
        }
        for (int y = L + 1; y <= L + 3; y++) c.set(y, lx == 6 ? LIFT_FRONT : LIFT_WALL);
        if (lx == 8 && (lz == -1 || lz == 1)) c.set(L + 2, facing(CityBlocks.ELEVATOR_PANEL, Direction.WEST));
    }

    // ------------------------------------------------------------------ начинка этажа
    private static void interior(int lx, int lz, int k, int L, Island.Column c, Spec sp, int W) {
        if (inStair(lx, lz)) {
            stairContent(lx, lz, k, L, c, false);
            return;
        }
        if (inLift(lx, lz)) {
            liftContent(lx, lz, L, c, false);
            return;
        }
        if (k == 0) lobby(lx, lz, L, c, sp, W);
        else if (sp.use() == OFFICE) office(lx, lz, k, L, c, sp, W);
        else units(lx, lz, k, L, c, sp, W);
    }

    private static void lobby(int lx, int lz, int L, Island.Column c, Spec sp, int W) {
        int ax = Math.abs(lx), az = Math.abs(lz), e = W - Math.max(ax, az);
        boolean hotel = sp.use() == HOTEL;
        if ((lx == 0 || lx == 1) && lz >= -2) {
            if (hotel) c.set(L + 1, st(Blocks.RED_CARPET));
            return;
        }
        if (e == 0 && ax == az) {
            c.set(L + 1, st(Blocks.FLOWERING_AZALEA));
            return;
        }
        if (lz == W && (lx == -1 || lx == 2)) {
            c.set(L + 1, st(Blocks.POTTED_BAMBOO));
            return;
        }
        // стойка ресепшена с компьютером и креслом
        if (lz == 4 && lx >= -7 && lx <= -3) {
            c.set(L + 1, st(hotel ? Blocks.DARK_OAK_PLANKS : Blocks.QUARTZ_BRICKS));
            if (lx == -5) c.set(L + 2, facing(ModContent.COMPUTER, Direction.NORTH));
            else if (lx == -3) c.set(L + 2, st(Blocks.POTTED_RED_TULIP));
            else if (lx == -7 && hotel) c.set(L + 2, st(Blocks.BELL));
            return;
        }
        if (lz == 3 && lx == -5) {
            c.set(L + 1, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.NORTH));
            return;
        }
        if (lz == 1 && lx >= -7 && lx <= -3) { // стена за ресепшеном с вывеской
            for (int y = L + 1; y <= L + 3; y++) c.set(y, st(hotel ? Blocks.STRIPPED_DARK_OAK_WOOD : Blocks.SMOOTH_QUARTZ));
            if (lx == -5) c.set(L + 2, ModContent.NEON.getDefaultState().with(net.minecraft.block.HorizontalFacingBlock.FACING, Direction.SOUTH)
                    .with(NeonBlock.COLOR, NeonBlock.Color.values()[(int) (hash(sp.px(), sp.pz(), 29) * NeonBlock.Color.values().length)]));
            return;
        }
        // зона ожидания: два дивана и столик
        if (lx >= 4 && lx <= 7 && lz >= 6 && lz <= 8) {
            Block sofa = hotel ? Blocks.DARK_OAK_STAIRS : Blocks.POLISHED_BLACKSTONE_STAIRS;
            if (lz == 6) c.set(L + 1, stairs(sofa, Direction.NORTH));
            else if (lz == 8) c.set(L + 1, stairs(sofa, Direction.SOUTH));
            else c.set(L + 1, (lx == 5 || lx == 6) ? trapTop(Blocks.SPRUCE_TRAPDOOR) : st(Blocks.GRAY_CARPET));
            if (lz == 7 && lx == 5) c.set(L + 2, st(Blocks.POTTED_WHITE_TULIP));
            return;
        }
        if (lx >= 3 && lx <= 8 && lz >= 5 && lz <= 9) {
            c.set(L + 1, st(hotel ? Blocks.RED_CARPET : Blocks.GRAY_CARPET));
            return;
        }
        if (lx == -W && lz == 0) c.set(L + 1, facing(ModContent.ATM, Direction.EAST));
    }

    private static void office(int lx, int lz, int k, int L, Island.Column c, Spec sp, int W) {
        int ax = Math.abs(lx), az = Math.abs(lz), e = W - Math.max(ax, az);
        BlockState carpet = st(sp.carpet());
        if (coreZone(lx, lz)) {
            c.set(L + 1, carpet);
            return;
        }
        if (e == 0) {
            c.set(L + 1, ax == az ? st(POTS[(int) (hash(sp.px() + k, sp.pz() + lx * 3L + lz, 30) * POTS.length)]) : carpet);
            return;
        }
        // кулер и принтер у лифтового холла
        if (lx == 3 && lz == -2) {
            c.set(L + 1, st(Blocks.SMOOTH_QUARTZ));
            c.set(L + 2, st(Blocks.LIGHT_BLUE_STAINED_GLASS));
            return;
        }
        if (lx == 3 && lz == 2) {
            c.set(L + 1, st(Blocks.DROPPER));
            return;
        }
        // переговорная со стеклянными стенами (юго-восточный угол)
        if (W >= 11 && lx >= 5 && lz >= 5) {
            if (lx == 5 || lz == 5) {
                boolean doorGap = lx == 5 && lz == 7;
                c.set(L + 1, doorGap ? carpet : st(Blocks.GLASS_PANE));
                if (!doorGap) c.set(L + 2, st(Blocks.GLASS_PANE));
                c.set(L + 3, st(Blocks.GLASS_PANE));
                return;
            }
            int a = 8, b = W - 2;
            boolean tableX = lx >= a && lx <= b, tableZ = lz >= a && lz <= b;
            if (tableX && tableZ) c.set(L + 1, slabTop(Blocks.DARK_OAK_SLAB));
            else if (tableZ && lx == a - 1) c.set(L + 1, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.WEST));
            else if (tableZ && lx == b + 1) c.set(L + 1, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.EAST));
            else if (tableX && lz == a - 1) c.set(L + 1, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.NORTH));
            else if (tableX && lz == b + 1) c.set(L + 1, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.SOUTH));
            else if (lx == 6 && lz == (a + b) / 2) {
                c.set(L + 1, st(Blocks.BARREL).with(Properties.FACING, Direction.UP));
                c.set(L + 2, facing(ModContent.TV, Direction.EAST));
            } else c.set(L + 1, st(Blocks.LIGHT_GRAY_CARPET));
            return;
        }
        if (e == 1) {
            c.set(L + 1, carpet);
            return;
        }
        // рабочие места: перегородка, стол с компьютером, кресло, проходы
        int u = Math.floorMod(lx + 1, 4), v = Math.floorMod(lz + 1, 5);
        if (u < 3 && v == 0) {
            c.set(L + 1, st(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE));
            c.set(L + 2, st(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE));
        } else if (u < 3 && v == 1) {
            c.set(L + 1, slabTop(Blocks.BIRCH_SLAB));
            if (u == 1) c.set(L + 2, facing(ModContent.COMPUTER, Direction.SOUTH));
            else if (u == 0 && hash(lx, lz * 31L + k, sp.px() + sp.pz()) < 0.4) c.set(L + 2, st(Blocks.POTTED_CACTUS));
            else if (u == 2 && hash(lx, lz * 17L + k, sp.px() - sp.pz()) < 0.5) c.set(L + 2, st(Blocks.WHITE_CARPET)); // бумаги
        } else if (u == 1 && v == 2) {
            c.set(L + 1, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.SOUTH));
        } else if (u == 3 && v == 1 && hash(lx + k, lz, sp.px() * 3 + sp.pz()) < 0.5) {
            c.set(L + 1, st(Blocks.FLOWERING_AZALEA));
        } else c.set(L + 1, carpet);
    }

    // ------------------------------------------------------------------ квартиры и номера отеля
    /**
     * Номер квартиры/номера на этаже (0..3) или −1 (коридор/холл), −2 — глухая шахта.
     * A: северо-запад, B: северо-восток, C: юго-запад, D: юго-восток.
     */
    private static int unit(int lx, int lz, int W) {
        if (lz <= -5 && lx <= -5) return 0;
        if (lz <= -5 && lx >= 4) return 1;
        if (lz <= -5) return -2;
        if (lz >= -2 && lx <= 0) return 2;
        if (lz >= 4 && lx >= 1) return 3;
        return -1;
    }

    private static boolean unitWall(int lx, int lz) {
        return (lx == -5 && lz <= -5) || (lz == -5 && lx <= -5) || (lx == 4 && lz <= -5) || (lz == -5 && lx >= 4)
                || (lx == 0 && lz >= -2) || (lz == -2 && lx <= 0) || (lx == 1 && lz >= 4) || (lz == 4 && lx >= 1);
    }

    /** Локальные координаты комнаты: i вдоль стены с дверью, j — вглубь от неё. */
    private record Room(int i, int j, int w, int d, int doorI, Direction in, Direction along) {}

    private static Room room(int u, int lx, int lz, int W) {
        return switch (u) {
            case 0 -> new Room(lx + W, -6 - lz, W - 5, W - 5, -7 + W, Direction.NORTH, Direction.EAST);
            case 1 -> new Room(lx - 5, -6 - lz, W - 4, W - 5, 1, Direction.NORTH, Direction.EAST);
            case 2 -> new Room(lx + W, lz + 1, W, W + 2, -3 + W, Direction.SOUTH, Direction.EAST);
            default -> new Room(lx - 2, lz - 5, W - 1, W - 4, 2, Direction.SOUTH, Direction.EAST);
        };
    }

    private static int[] doorOf(int u) {
        return switch (u) {
            case 0 -> new int[]{-7, -5};
            case 1 -> new int[]{6, -5};
            case 2 -> new int[]{-3, -2};
            default -> new int[]{4, 4};
        };
    }

    private static void units(int lx, int lz, int k, int L, Island.Column c, Spec sp, int W) {
        boolean hotel = sp.use() == HOTEL;
        BlockState wall = st(hotel ? Blocks.SMOOTH_SANDSTONE : Blocks.WHITE_CONCRETE);
        if (unitWall(lx, lz)) {
            for (int u = 0; u < 4; u++) {
                int[] d = doorOf(u);
                if (d[0] == lx && d[1] == lz) {
                    door(c, L + 1, hotel ? Blocks.DARK_OAK_DOOR : Blocks.SPRUCE_DOOR, room(u, lx, lz, W).in(), DoorHinge.LEFT);
                    c.set(L + 3, wall);
                    return;
                }
            }
            for (int y = L + 1; y <= L + 3; y++) c.set(y, wall);
            return;
        }
        int u = unit(lx, lz, W);
        if (u == -2) return;
        if (u == -1) { // коридор: дорожка, цветы по углам
            if (coreZone(lx, lz) && !(lz == -4 || lz == -3)) return;
            if ((lx == -W || lx == W) && (lz == -4 || lz == -3)) c.set(L + 1, st(Blocks.POTTED_FERN));
            else if (lx >= 9 && lz >= -2 && lz <= 3 && lx == W && lz == 0) c.set(L + 1, st(Blocks.FLOWERING_AZALEA));
            else c.set(L + 1, st(hotel ? Blocks.RED_CARPET : Blocks.GRAY_CARPET));
            return;
        }
        Room r = room(u, lx, lz, W);
        furnish(r, L, c, hotel, hash(sp.px() * 13 + u, sp.pz() * 7 + k, 44), k);
    }

    /** Обстановка комнаты: кровать у дальней стены, кухня (или мини-бар и стол в отеле), телевизор с диваном, ванна. */
    private static void furnish(Room r, int L, Island.Column c, boolean hotel, double seed, int k) {
        int i = r.i(), j = r.j(), w = r.w(), d = r.d();
        Direction in = r.in(), along = r.along();
        if (i == w / 2 && j == d / 2) c.set(L + 3, hanging()); // светильник под потолком
        if (i == r.doorI() && j <= 1) return; // проход от двери
        // кровать (двуспальная — из двух), изголовьем к дальней стене
        if (j >= d - 2 && (i == 0 || (i == 1 && w >= 5))) {
            Block b = hotel ? Blocks.WHITE_BED : BEDS[(int) (seed * BEDS.length)];
            bed(c, L + 1, b, in, j == d - 1);
            return;
        }
        int ns = w >= 5 ? 2 : 1;
        if (j == d - 1 && i == ns && ns < w - 1) { // тумбочка с лампой
            c.set(L + 1, st(Blocks.BARREL).with(Properties.FACING, Direction.UP));
            c.set(L + 2, st(Blocks.LANTERN));
            return;
        }
        // у правой стены: кухня или мини-бар со столом
        if (i == w - 1 && j >= 1) {
            int t = d - 1 - j;
            Direction toRoom = along.getOpposite();
            if (!hotel) {
                if (t == 0) {
                    c.set(L + 1, st(Blocks.SMOOTH_QUARTZ)); // холодильник
                    c.set(L + 2, st(Blocks.SMOOTH_QUARTZ));
                    return;
                }
                if (t == 1) {
                    c.set(L + 1, facing(Blocks.SMOKER, toRoom)); // плита
                    return;
                }
                if (t == 2) {
                    c.set(L + 1, water3()); // мойка
                    return;
                }
                if (t == 3) {
                    c.set(L + 1, st(Blocks.BARREL).with(Properties.FACING, Direction.UP)); // столешница
                    if (seed > 0.5) c.set(L + 2, st(Blocks.POTTED_BAMBOO));
                    return;
                }
            } else {
                if (t == 0) {
                    c.set(L + 1, st(Blocks.BARREL).with(Properties.FACING, Direction.UP)); // мини-бар
                    c.set(L + 2, st(Blocks.BREWING_STAND));
                    return;
                }
                if (t == 2) {
                    c.set(L + 1, slabTop(Blocks.DARK_OAK_SLAB)); // письменный стол
                    c.set(L + 2, st(Blocks.LANTERN));
                    return;
                }
            }
        }
        // ванна в углу у двери
        if (i == w - 1 && j == 0 && r.doorI() < w - 1) {
            c.set(L + 1, water3());
            return;
        }
        // телевизор на тумбе и диван напротив
        int jt = Math.max(1, d - 4);
        int sofaI = w >= 6 ? 3 : 2;
        if (i == 0 && j == jt) {
            c.set(L + 1, st(Blocks.BARREL).with(Properties.FACING, Direction.UP));
            c.set(L + 2, facing(ModContent.TV, along));
            return;
        }
        if (j < d - 2 && j >= 1 && i == sofaI && Math.abs(j - jt) <= 1 && sofaI < w - 1) {
            c.set(L + 1, stairs(hotel ? Blocks.DARK_OAK_STAIRS : (seed > 0.5 ? Blocks.SPRUCE_STAIRS : Blocks.POLISHED_BLACKSTONE_STAIRS), along));
            return;
        }
        if (i == sofaI - 1 && j == jt && sofaI - 1 > 0) {
            c.set(L + 1, trapTop(Blocks.SPRUCE_TRAPDOOR));
            return;
        }
        if (i >= 1 && i < sofaI && Math.abs(j - jt) <= 1 && j < d - 2) {
            c.set(L + 1, st(hotel ? Blocks.WHITE_CARPET : RUGS[(int) (seed * 977 % 1 * RUGS.length)]));
            return;
        }
        if (j == 0 && i == 0 && w >= 5) c.set(L + 1, st(POTS[(int) (seed * 31 % 1 * POTS.length)]));
    }

    // ------------------------------------------------------------------ крыша и терраса
    private static void terrace(int lx, int lz, int L, Island.Column c, Spec sp) {
        int ax = Math.abs(lx), az = Math.abs(lz), half = sp.half();
        if (ax == half || az == half) {
            c.set(L + 1, sp.frame()); // парапет
            return;
        }
        if (ax == half - 1 && az == half - 1) c.set(L + 1, st(Blocks.FLOWERING_AZALEA));
        else if (sp.use() == OFFICE && Math.max(ax, az) == half - 1 && Math.floorMod(lx + lz, 6) == 0 && lz > 0 && lx != 0 && lx != 1)
            c.set(L + 1, stairs(Blocks.SPRUCE_STAIRS, lz == half - 1 ? Direction.SOUTH : lx > 0 ? Direction.EAST : Direction.WEST)); // скамейки
    }

    private static void roof(int lx, int lz, int L, Island.Column c, Spec sp) {
        int ax = Math.abs(lx), az = Math.abs(lz), inner = sp.half() - 3;
        if (inStair(lx, lz)) {
            stairContent(lx, lz, sp.n(), L, c, true);
            return;
        }
        if (inLift(lx, lz)) {
            liftContent(lx, lz, L, c, true);
            return;
        }
        if (ax == inner || az == inner) {
            c.set(L + 1, sp.frame());
            return;
        }
        switch (sp.roof()) {
            case HELI -> {
                if (ax <= 4 && az <= 4) {
                    c.set(L, st(Blocks.GRAY_CONCRETE));
                    if ((ax == 2 && az <= 2) || (lz == 0 && ax <= 2)) c.set(L, st(Blocks.YELLOW_CONCRETE));
                    if (ax == 4 && az == 4) c.set(L, Blocks.REDSTONE_LAMP.getDefaultState().with(Properties.LIT, true));
                    return;
                }
            }
            case ANTENNA -> {
                if (lz == inner - 1 && lx >= -3 && lx <= 2) { // рекламный щит над городом
                    int ad = (int) (hash(sp.px(), sp.pz(), 35) * 5);
                    for (int row = 0; row < 3; row++) c.set(L + 4 - row, CityBlocks.billboard(ad, Direction.SOUTH, lx + 3, row));
                    if (lx == -2 || lx == 1) c.set(L + 1, st(Blocks.POLISHED_DEEPSLATE_WALL));
                    return;
                }
                if (lx == 0 && lz == 0) {
                    for (int y = 1; y <= 12; y++) c.set(L + y, st(Blocks.IRON_BARS));
                    c.set(L + 13, Blocks.REDSTONE_LAMP.getDefaultState().with(Properties.LIT, true));
                    return;
                }
                if ((lx == 1 || lx == -1) && lz == 0) c.set(L + 1, st(Blocks.IRON_BARS));
                if (lx >= 4 && lx <= 5 && lz >= 4 && lz <= 5) { // второй кондиционер
                    c.set(L + 1, st(Blocks.LIGHT_GRAY_CONCRETE));
                    c.set(L + 2, Blocks.IRON_TRAPDOOR.getDefaultState());
                    return;
                }
            }
            default -> { // бассейн с бортиком, шезлонги, зонтики и бар
                if (ax <= 4 && az <= 3) {
                    c.set(L, st(Blocks.LIGHT_BLUE_TERRACOTTA));
                    c.set(L + 1, ax == 4 || az == 3 ? st(Blocks.SMOOTH_QUARTZ) : st(Blocks.WATER));
                    return;
                }
                if (lz == 5 && (lx == -3 || lx == -1 || lx == 1 || lx == 3)) {
                    c.set(L + 1, stairs(Blocks.BIRCH_STAIRS, Direction.SOUTH));
                    return;
                }
                if (lz == 6 && (lx == -3 || lx == -1 || lx == 1 || lx == 3)) {
                    c.set(L + 1, st(Blocks.WHITE_CARPET));
                    return;
                }
                if (lz == 7 && (lx == -2 || lx == 2)) {
                    c.set(L + 1, st(Blocks.OAK_FENCE));
                    c.set(L + 2, st(Blocks.OAK_FENCE));
                }
                if (Math.abs(lz - 7) <= 1 && (Math.abs(lx - 2) <= 1 || Math.abs(lx + 2) <= 1) && lz <= inner - 1)
                    c.set(L + 3, st(lx > 0 ? Blocks.ORANGE_WOOL : Blocks.WHITE_WOOL));
                if (lx >= -8 && lx <= -6 && lz >= 0 && lz <= 4) { // бар у бассейна
                    if (lx == -6 && lz >= 1 && lz <= 3) {
                        c.set(L + 1, st(Blocks.SMOOTH_QUARTZ));
                        if (lz == 2) c.set(L + 2, st(Blocks.POTTED_FLOWERING_AZALEA_BUSH));
                    } else if (lx == -8 && lz >= 1 && lz <= 3) {
                        c.set(L + 1, st(Blocks.BARREL).with(Properties.FACING, Direction.UP));
                        c.set(L + 2, st(Blocks.BREWING_STAND));
                    } else if ((lz == 0 || lz == 4) && (lx == -8 || lx == -6)) {
                        for (int y = 1; y <= 3; y++) c.set(L + y, st(Blocks.DARK_OAK_FENCE));
                    }
                    c.set(L + 4, st(Blocks.ORANGE_WOOL));
                    return;
                }
                if (lx == -5 && lz >= 1 && lz <= 3) c.set(L + 1, st(Blocks.DARK_OAK_FENCE)); // табуреты
                return;
            }
        }
        // кондиционеры на любой крыше
        if (lx >= -8 && lx <= -7 && lz >= 4 && lz <= 6 && inner - 1 >= 8) {
            c.set(L + 1, st(Blocks.LIGHT_GRAY_CONCRETE));
            c.set(L + 2, lz == 5 ? Blocks.IRON_TRAPDOOR.getDefaultState() : st(Blocks.LIGHT_GRAY_CONCRETE));
            if (lz == 5) c.set(L + 2, Blocks.IRON_TRAPDOOR.getDefaultState());
        }
        // бак для воды у жилых домов
        if (sp.use() == HOMES && lx >= 4 && lx <= 5 && lz >= -6 && lz <= -5) {
            for (int y = 1; y <= 2; y++) c.set(L + y, st(Blocks.SPRUCE_FENCE));
            c.set(L + 3, st(Blocks.SPRUCE_PLANKS));
            c.set(L + 4, st(Blocks.SPRUCE_PLANKS));
            c.set(L + 5, st(Blocks.SPRUCE_SLAB));
        }
    }

    /** Для автотестов: {тип (0 офисы, 1 жилая башня, 2 отель, 3 казино, 4 парк, 5 дома, 6 склад, 7 контейнеры), полуширина, этажей}. */
    static int[] info(Island.Zone zn, long px, long pz) {
        switch (zn) {
            case DOWNTOWN -> {
                double centerBoost = 1 - Math.min(1, Math.hypot(px * 48 - 430, pz * 48 + 430) / 380.0);
                return new int[]{hash(px, pz, 25) < 0.3 ? 1 : 0, 13 + (int) (hash(px, pz, 22) * 4), 6 + (int) (hash(px, pz, 23) * 10 + centerBoost * 12)};
            }
            case RIVIERA -> {
                double r = hash(px, pz, 81);
                boolean casino = (px == -27 && pz == -1) || r < 0.3;
                if (!casino && r > 0.86) return new int[]{4, 0, 0};
                return new int[]{casino ? 3 : 2, 13 + (int) (hash(px, pz, 82) * 3), casino ? 2 : 6 + (int) (hash(px, pz, 84) * 8)};
            }
            case INDUSTRIAL -> {
                return new int[]{(int) (hash(px, pz, 31) * 3) >= 2 ? 7 : 6, 13, 1};
            }
            default -> {
                return new int[]{5, 4, 1};
            }
        }
    }

    // ================================================================== Ривьера
    /** Курорт: отели с бассейнами на крышах, казино, парк с бассейном. */
    static void resort(int lx, int lz, long px, long pz, int s, Island.Column c) {
        double r = hash(px, pz, 81);
        boolean goldenCrown = px == -27 && pz == -1; // здесь CasinoBuilder строит зал «Золотая корона»
        boolean casino = goldenCrown || r < 0.3;
        if (!casino && r > 0.86) {
            resortPark(lx, lz, px, pz, s, c);
            return;
        }
        int half = 13 + (int) (hash(px, pz, 82) * 3);
        Block[] accents = {Blocks.PINK_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.LIME_CONCRETE, Blocks.MAGENTA_CONCRETE};
        BlockState accent = st(accents[(int) (hash(px, pz, 85) * accents.length)]);
        int ax = Math.abs(lx), az = Math.abs(lz);
        if (casino) {
            casino(lx, lz, px, pz, s, c, half, accent, goldenCrown);
            return;
        }
        int n = 6 + (int) (hash(px, pz, 84) * 8);
        Spec sp = new Spec(px, pz, half, n, accent, st(Blocks.WHITE_STAINED_GLASS), st(Blocks.YELLOW_STAINED_GLASS), HOTEL, POOL, Blocks.RED_CARPET, Blocks.SPRUCE_PLANKS);
        if (ax > half || az > half) {
            plaza(lx, lz, s, c, sp);
            return;
        }
        highrise(lx, lz, s, c, sp);
    }

    private static void casino(int lx, int lz, long px, long pz, int s, Island.Column c, int half, BlockState accent, boolean plain) {
        int ax = Math.abs(lx), az = Math.abs(lz), m = Math.max(ax, az);
        int h = 14 + (int) (hash(px, pz, 83) * 8);
        if (m > half) {
            c.set(s, (ax + az) % 4 == 0 ? st(Blocks.SMOOTH_SANDSTONE) : st(Blocks.CUT_SANDSTONE));
            if (ax == half + 2 && az % 8 == 0 && az <= half) {
                for (int y = 1; y <= 5; y++) c.set(s + y, st(Blocks.JUNGLE_LOG));
                c.set(s + 6, leaves(Blocks.JUNGLE_LEAVES));
            }
            if (lz > half && ax <= 2) c.set(s + 1, st(Blocks.RED_CARPET)); // красная дорожка
            return;
        }
        boolean wall = m == half;
        for (int y = s + 1; y <= s + h; y++) {
            int floor = (y - s) % 4;
            if (wall) {
                if (y <= s + 3 && ax <= 2 && lz == half) continue; // вход
                if (y >= s + h - 3) c.set(y, ModContent.NEON.getDefaultState().with(net.minecraft.block.HorizontalFacingBlock.FACING,
                        lz == half ? Direction.SOUTH : lz == -half ? Direction.NORTH : lx > 0 ? Direction.EAST : Direction.WEST)
                        .with(NeonBlock.COLOR, NeonBlock.Color.values()[Math.floorMod((int) (px * 7 + pz + y), NeonBlock.Color.values().length)]));
                else if (floor == 0) c.set(y, accent);
                else c.set(y, st(Blocks.GOLD_BLOCK));
            } else if (plain) { // коробка как раньше: зал внутри обставит CasinoBuilder
                if (floor == 0) c.set(y, st(Blocks.WHITE_CONCRETE));
                else if (floor == 2 && ax % 5 == 0 && az % 5 == 0) c.set(y, LIGHT);
            } else if (y >= s + 8 && floor == 0) c.set(y, st(Blocks.BLACK_CONCRETE));
        }
        c.set(s + h + 1, st(Blocks.WHITE_CONCRETE));
        if (wall || plain) return;
        casinoHall(lx, lz, s, c, half - 1);
    }

    /** Игровой зал в два этажа: автоматы, рулетки, бар, люстры. Автоматы и рулетки — настоящие (играть можно). */
    private static void casinoHall(int lx, int lz, int s, Island.Column c, int I) {
        int ax = Math.abs(lx), az = Math.abs(lz), m = Math.max(ax, az);
        boolean aisle = ax <= 1 && lz > -I + 3;
        c.set(s, m == I ? st(Blocks.BLACK_CONCRETE) : aisle ? st(Blocks.YELLOW_WOOL)
                : (Math.floorMod(lx, 4) == 0 && Math.floorMod(lz, 4) == 0) ? st(Blocks.ORANGE_WOOL) : st(Blocks.RED_WOOL));
        if (Math.floorMod(lx, 4) == 0 && Math.floorMod(lz, 4) == 2) c.set(s + 8, LIGHT);
        if (Math.floorMod(lx, 6) == 3 && Math.floorMod(lz, 6) == 3) { // люстры
            c.set(s + 7, st(Blocks.CHAIN));
            c.set(s + 6, hanging());
        }
        if (aisle) return;
        // бар у северной стены
        if (ax <= 4 && lz == -I) {
            c.set(s + 1, st(Blocks.BARREL).with(Properties.FACING, Direction.UP));
            c.set(s + 2, st(Blocks.BREWING_STAND));
            return;
        }
        if (ax <= 4 && lz == -I + 2) {
            c.set(s + 1, st(Blocks.DARK_OAK_PLANKS));
            if (lx == 0) c.set(s + 2, st(Blocks.POTTED_RED_TULIP));
            return;
        }
        if (ax <= 4 && lz == -I + 3) {
            c.set(s + 1, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
            return;
        }
        if (lz <= -I + 3) return;
        // автоматы — на западе
        if (lx <= -3 && lx >= -I + 1 && lz <= I - 2) {
            int v = Math.floorMod(lz, 4);
            if (v == 0) c.set(s + 1, facing(ModContent.SLOT_MACHINE, Direction.SOUTH));
            else if (v == 1) c.set(s + 1, stairs(Blocks.CRIMSON_STAIRS, Direction.SOUTH));
            return;
        }
        // рулетки — на востоке, вокруг табуреты
        if (lx >= 3 && lx <= I - 1 && lz <= I - 2) {
            int u = Math.floorMod(lx - 5, 5), v = Math.floorMod(lz, 5);
            if (u == 0 && v == 0) c.set(s + 1, ModContent.ROULETTE.getDefaultState());
            else if (u == 0 && (v == 1 || v == 4)) c.set(s + 1, stairs(Blocks.CRIMSON_STAIRS, v == 1 ? Direction.SOUTH : Direction.NORTH));
            else if ((u == 1 || u == 4) && v == 0) c.set(s + 1, stairs(Blocks.CRIMSON_STAIRS, u == 1 ? Direction.EAST : Direction.WEST));
        }
    }

    /** Парк Ривьеры: бассейн, шезлонги, зонтики, пальмы, бар и живая изгородь. */
    private static void resortPark(int lx, int lz, long px, long pz, int s, Island.Column c) {
        int ax = Math.abs(lx), az = Math.abs(lz);
        c.set(s, (ax + az) % 3 == 0 ? st(Blocks.SMOOTH_SANDSTONE) : st(Blocks.CUT_SANDSTONE));
        if (ax == 17 || az == 17) { // изгородь с проходами
            if (ax > 1 && az > 1) c.set(s + 1, leaves(Blocks.OAK_LEAVES));
            return;
        }
        if (ax <= 7 && az <= 4) { // бассейн в два блока глубиной
            if (ax == 7 || az == 4) {
                c.set(s, st(Blocks.SMOOTH_QUARTZ));
                return;
            }
            c.set(s - 2, st(Blocks.LIGHT_BLUE_TERRACOTTA));
            c.set(s - 1, st(Blocks.WATER));
            c.set(s, st(Blocks.WATER));
            return;
        }
        if ((ax == 13 && az == 13) || (ax == 13 && lz == 0) || (lx == 0 && az == 13)) {
            if (lx == 0 && lz == 13) return; // вход с юга
            for (int y = 1; y <= 6; y++) c.set(s + y, st(Blocks.JUNGLE_LOG));
            c.set(s + 7, leaves(Blocks.JUNGLE_LEAVES));
            return;
        }
        if ((Math.abs(ax - 13) + Math.abs(az - 13) == 1) || (lz == 0 && Math.abs(ax - 13) == 1) || (lx == 0 && Math.abs(az - 13) == 1 && lz < 0)
                || (Math.abs(lx) == 1 && az == 13 && lz < 0) || (ax == 13 && Math.abs(lz) == 1)) {
            c.set(s + 6, leaves(Blocks.JUNGLE_LEAVES));
            c.set(s + 7, leaves(Blocks.JUNGLE_LEAVES));
        }
        if (az == 6 && ax <= 7 && lx % 2 != 0) { // шезлонги вдоль бассейна
            c.set(s + 1, stairs(Blocks.BIRCH_STAIRS, lz > 0 ? Direction.SOUTH : Direction.NORTH));
            return;
        }
        if (az == 7 && ax <= 7 && lx % 2 != 0) {
            c.set(s + 1, st(Blocks.WHITE_CARPET));
            return;
        }
        if (az == 8 && (ax == 4)) {
            for (int y = 1; y <= 2; y++) c.set(s + y, st(Blocks.OAK_FENCE));
        }
        if (Math.abs(az - 8) <= 1 && Math.abs(ax - 4) <= 1) c.set(s + 3, st(lz > 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL));
        // бар-киоск
        if (lx >= 9 && lx <= 12 && lz >= -12 && lz <= -9) {
            boolean edge = lx == 9 || lx == 12 || lz == -12 || lz == -9;
            if (edge && (lx == 9 || lx == 12) && (lz == -12 || lz == -9)) for (int y = 1; y <= 3; y++) c.set(s + y, st(Blocks.DARK_OAK_FENCE));
            else if (lx == 9) c.set(s + 1, st(Blocks.SMOOTH_QUARTZ));
            else if (lx == 12) {
                c.set(s + 1, st(Blocks.BARREL).with(Properties.FACING, Direction.UP));
                c.set(s + 2, st(Blocks.BREWING_STAND));
            }
            c.set(s + 4, st((lx + lz) % 2 == 0 ? Blocks.CYAN_WOOL : Blocks.WHITE_WOOL));
            return;
        }
        if (lx == 8 && lz >= -11 && lz <= -10) c.set(s + 1, st(Blocks.DARK_OAK_FENCE));
        // фонари и скамейки
        if (ax == 10 && az == 10 && !(lx > 0 && lz < 0)) {
            for (int y = 1; y <= 3; y++) c.set(s + y, ModContent.LAMP_POST.getDefaultState());
            c.set(s + 4, ModContent.STREET_LAMP.getDefaultState().with(net.minecraft.block.HorizontalFacingBlock.FACING, Direction.NORTH));
        }
        if (az == 15 && ax >= 4 && ax <= 6) c.set(s + 1, stairs(Blocks.SPRUCE_STAIRS, lz > 0 ? Direction.SOUTH : Direction.NORTH));
        if ((ax == 15 && az <= 10 && az % 5 == 0)) c.set(s + 1, st(Blocks.FLOWERING_AZALEA));
    }

    // ================================================================== пригород
    /** Участки у магазинов стиля: южную половину не застраиваем домиками — там встанет магазин. */
    private static boolean shopLot(long px, long pz) {
        return pz == 2 && (px == -5 || px == -7 || px == -9 || px == -35);
    }

    /** Четыре домика на участке лицом к улицам: двери, окна, кухня, гостиная, спальня; калитки и дорожки. */
    static void suburb(int lx, int lz, long px, long pz, int s, Island.Column c) {
        int qx = lx < 0 ? -1 : 1, qz = lz < 0 ? -1 : 1;
        int hx = lx - qx * 9, hz = lz - qz * 9;      // координаты внутри четверти (центр дома в ±9)
        long id = px * 4 + (qx + 1) + (pz * 4 + (qz + 1)) * 131;
        int ax = Math.abs(hx), az = Math.abs(hz);
        boolean empty = shopLot(px, pz) && lz > 0;
        if (Math.abs(lx) == 17 || Math.abs(lz) == 17) {
            if (Math.abs(lz) == 17 && hx == 0 && !empty)
                c.set(s + 1, Blocks.OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, Direction.NORTH));
            else {
                c.set(s + 1, st(Blocks.OAK_FENCE));
                if (Math.abs(lz) == 17 && ax == 1 && !empty) c.set(s + 2, st(Blocks.LANTERN));
            }
            return;
        }
        if (empty) {
            if (ax == 6 && az == 6 && hash(id, 1, 53) < 0.6) tree(s, c, 4 + (int) (hash(id, 2, 54) * 2));
            return;
        }
        net.minecraft.block.Block[] walls = {Blocks.WHITE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.BRICKS, Blocks.OAK_PLANKS};
        net.minecraft.block.Block[] roofs = {Blocks.RED_NETHER_BRICKS, Blocks.DARK_OAK_PLANKS, Blocks.DEEPSLATE_TILES, Blocks.SPRUCE_PLANKS};
        net.minecraft.block.Block[] doors = {Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.DARK_OAK_DOOR, Blocks.BIRCH_DOOR};
        if (ax <= 4 && az <= 4) {
            BlockState wall = walls[(int) (hash(id, 0, 51) * walls.length)].getDefaultState();
            BlockState roof = roofs[(int) (hash(id, 0, 52) * roofs.length)].getDefaultState();
            int f = hz * qz, i = hx * qx;               // f: +4 — фасад с дверью (к улице), −4 — задняя стена
            Direction inward = qz > 0 ? Direction.NORTH : Direction.SOUTH, toFront = inward.getOpposite();
            Direction plusI = qx > 0 ? Direction.EAST : Direction.WEST;
            boolean edge = ax == 4 || az == 4;
            // двускатная крыша
            int roofH = 4 - ax;
            for (int k = 0; k <= Math.max(0, roofH); k++) c.set(s + 5 + k, roof);
            if (i == 2 && f == -2) for (int y = s + 5; y <= s + 5 + roofH + 2; y++) c.set(y, st(Blocks.BRICKS)); // труба
            if (edge) {
                for (int y = s + 1; y <= s + 4; y++) {
                    boolean window = (y == s + 2 || y == s + 3) && (ax == 2 || az == 2) && !(ax == 4 && az == 4);
                    c.set(y, window ? st(Blocks.GLASS_PANE) : wall);
                }
                if (f == 4 && hx == 0) { // входная дверь
                    door(c, s + 1, doors[(int) (hash(id, 3, 55) * doors.length)], inward, DoorHinge.LEFT);
                    c.set(s + 3, wall);
                }
                return;
            }
            c.set(s, st(PLANKS[(int) (hash(id, 4, 56) * PLANKS.length)]));
            c.set(s + 4, st(Blocks.OAK_PLANKS)); // потолок
            if (i == 0 && (f == 2 || f == -2)) c.set(s + 3, hanging());
            // перегородка между гостиной и спальней, проход посередине
            if (f == 0) {
                if (i != 0) for (int y = s + 1; y <= s + 3; y++) c.set(y, wall);
                else c.set(s + 3, wall);
                return;
            }
            if (f > 0) { // гостиная и кухня
                if (i == 3) {
                    if (f == 3) {
                        c.set(s + 1, st(Blocks.SMOOTH_QUARTZ)); // холодильник
                        c.set(s + 2, st(Blocks.SMOOTH_QUARTZ));
                    } else if (f == 2) c.set(s + 1, facing(Blocks.SMOKER, plusI.getOpposite()));
                    else c.set(s + 1, water3());
                } else if (i == -3 && f == 2) {
                    c.set(s + 1, st(Blocks.BARREL).with(Properties.FACING, Direction.UP));
                    c.set(s + 2, facing(ModContent.TV, plusI));
                } else if (i == -3) c.set(s + 1, st(f == 1 ? Blocks.POTTED_FERN : Blocks.BOOKSHELF));
                else if (i == -1) c.set(s + 1, stairs(hash(id, 5, 57) < 0.5 ? Blocks.SPRUCE_STAIRS : Blocks.DARK_OAK_STAIRS, plusI));
                else if (i == -2) c.set(s + 1, f == 2 ? trapTop(Blocks.SPRUCE_TRAPDOOR) : st(RUGS[(int) (hash(id, 6, 58) * RUGS.length)]));
                else if (i == 2 && f == 2) {
                    c.set(s + 1, st(Blocks.OAK_FENCE)); // обеденный столик
                    c.set(s + 2, st(Blocks.OAK_PRESSURE_PLATE));
                }
                return;
            }
            // спальня
            if ((i == -3 || i == -2) && f <= -2) {
                bed(c, s + 1, BEDS[(int) (hash(id, 7, 59) * BEDS.length)], inward, f == -3);
                return;
            }
            if (i == -1 && f == -3) {
                c.set(s + 1, st(Blocks.BARREL).with(Properties.FACING, Direction.UP));
                c.set(s + 2, st(Blocks.LANTERN));
                return;
            }
            if (i == 3 && f <= -2) {
                c.set(s + 1, st(Blocks.BOOKSHELF)); // шкаф
                c.set(s + 2, st(Blocks.BOOKSHELF));
                return;
            }
            if (i == 3 && f == -1) {
                c.set(s + 1, slabTop(Blocks.BIRCH_SLAB));
                c.set(s + 2, facing(ModContent.COMPUTER, plusI.getOpposite()));
                return;
            }
            if (i == 2 && f == -1) {
                c.set(s + 1, stairs(Blocks.SPRUCE_STAIRS, plusI.getOpposite()));
                return;
            }
            if (i >= -1 && i <= 1 && f >= -2) c.set(s + 1, st(RUGS[(int) (hash(id, 8, 60) * RUGS.length)]));
            return;
        }
        // двор: газон, дорожка от двери к калитке, дерево, клумбы под окнами
        if (hx == 0 && hz * qz > 4) c.set(s, st(Blocks.DIRT_PATH));
        else if (hz * qz == 5 && ax <= 3 && ax >= 2) c.set(s + 1, st((id & 1) == 0 ? Blocks.ROSE_BUSH : Blocks.PEONY).with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        if (hz * qz == 5 && ax <= 3 && ax >= 2) c.set(s + 2, st((id & 1) == 0 ? Blocks.ROSE_BUSH : Blocks.PEONY).with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        if (ax == 6 && az == 6 && hash(id, 1, 53) < 0.6) tree(s, c, 4 + (int) (hash(id, 2, 54) * 2));
        else if (ax <= 7 && az <= 7 && (Math.abs(ax - 6) <= 1 && Math.abs(az - 6) <= 1) && hash(id, 1, 53) < 0.6) {
            int h = 4 + (int) (hash(id, 2, 54) * 2);
            for (int y = h - 1; y <= h + ((ax == 6 || az == 6) ? 1 : 0); y++) c.set(s + y, leaves(Blocks.OAK_LEAVES));
        }
    }

    private static void tree(int s, Island.Column c, int h) {
        for (int y = 1; y <= h; y++) c.set(s + y, st(Blocks.OAK_LOG));
        c.set(s + h + 1, leaves(Blocks.OAK_LEAVES));
    }

    // ================================================================== промзона
    /** Склад: ворота, бетонный пол, стеллажи с товаром, погрузчик, поддоны, фонари под крышей. */
    static void industry(int lx, int lz, long px, long pz, int s, Island.Column c) {
        int kind = (int) (hash(px, pz, 31) * 3);
        int ax = Math.abs(lx), az = Math.abs(lz);
        c.set(s, (lx * 3 + lz) % 5 == 0 ? st(Blocks.GRAVEL) : st(Blocks.COARSE_DIRT));
        if (ax == 17 || az == 17) { // забор участка
            if (!(lz == 17 && ax <= 3)) for (int y = 1; y <= 2; y++) c.set(s + y, st(Blocks.IRON_BARS));
            return;
        }
        if (kind >= 2) {
            Island.container(lx, lz, px, pz, s, c);
            return;
        }
        int hx = 13, hz = 9 + (int) (hash(px, pz, 32) * 5);
        if (ax > hx || az > hz) {
            if (lz > hz && ax <= 3) c.set(s, st(Blocks.GRAY_CONCRETE)); // площадка у ворот
            return;
        }
        int h = 8 + (int) (hash(px, pz, 33) * 5);
        BlockState wallB = hash(px, pz, 34) < 0.5 ? st(Blocks.LIGHT_GRAY_TERRACOTTA) : st(Blocks.BROWN_TERRACOTTA);
        boolean wall = ax == hx || az == hz;
        if (wall) {
            for (int y = s + 1; y <= s + h; y++) {
                boolean gate = lz == hz && ax <= 3 && y <= s + 5;
                if (gate) continue;
                boolean graffiti = az == hz && y <= s + 3 && hash(lx, y, px * 7 + pz) < 0.12;
                c.set(y, graffiti ? (hash(lx, y, 5) < 0.5 ? Blocks.LIME_CONCRETE : Blocks.MAGENTA_CONCRETE).getDefaultState()
                        : (y - s) % 3 == 0 ? st(Blocks.GRAY_CONCRETE) : wallB);
            }
            if (lz == hz && ax == 4 && kind == 0) c.set(s + 3, ModContent.STREET_LAMP.getDefaultState().with(net.minecraft.block.HorizontalFacingBlock.FACING, Direction.SOUTH));
            c.set(s + h + 1, st(Blocks.GRAY_CONCRETE));
        } else {
            c.set(s, ax <= 3 && Math.floorMod(lz, 4) == 0 ? st(Blocks.YELLOW_CONCRETE) : st(Blocks.SMOOTH_STONE));
            c.set(s + h + 1, ax % 6 == 3 && az < hz - 1 ? st(Blocks.GLASS) : st(Blocks.GRAY_CONCRETE)); // световые полосы на крыше
            if (Math.floorMod(lx, 6) == 0 && Math.floorMod(lz, 5) == 0) c.set(s + h, hanging());
            warehouse(lx, lz, px, pz, s, c, hx, hz);
            if (kind == 1 && lz == 0 && lx >= -3 && lx <= 2) { // щит на крыше склада
                int ad = (int) (hash(px, pz, 36) * 5);
                for (int row = 0; row < 3; row++) c.set(s + h + 5 - row, CityBlocks.billboard(ad, Direction.SOUTH, lx + 3, row));
                if (lx == -2 || lx == 1) c.set(s + h + 2, st(Blocks.POLISHED_DEEPSLATE_WALL));
            }
        }
        // труба с дымом
        if (kind == 0 && lx == hx - 3 && lz == -hz + 3) {
            for (int y = s + h + 2; y <= s + h + 16; y++) c.set(y, st(Blocks.BRICKS));
            c.set(s + h + 17, st(Blocks.CAMPFIRE));
        }
    }

    private static void warehouse(int lx, int lz, long px, long pz, int s, Island.Column c, int hx, int hz) {
        int ax = Math.abs(lx);
        // погрузчик у центрального проезда
        if (lx == 2 && lz >= -1 && lz <= 2) {
            switch (lz) {
                case -1 -> { c.set(s + 1, st(Blocks.YELLOW_CONCRETE)); c.set(s + 2, st(Blocks.BLACK_CONCRETE)); }
                case 0 -> { c.set(s + 1, st(Blocks.YELLOW_CONCRETE)); c.set(s + 2, stairs(Blocks.POLISHED_BLACKSTONE_STAIRS, Direction.NORTH)); c.set(s + 3, trapTop(Blocks.IRON_TRAPDOOR)); }
                case 1 -> { for (int y = 1; y <= 3; y++) c.set(s + y, st(Blocks.IRON_BARS)); }
                default -> { c.set(s + 1, Blocks.IRON_TRAPDOOR.getDefaultState()); c.set(s + 2, st(Blocks.PACKED_MUD)); }
            }
            return;
        }
        if (ax <= 4) {
            if (lx == -3 && lz == hz - 3) { // поддоны у ворот
                c.set(s + 1, Blocks.OAK_SLAB.getDefaultState());
                c.set(s + 2, st(Blocks.PACKED_MUD));
            }
            return;
        }
        if (ax > hx - 2 || lz < -hz + 2 || lz > hz - 3) return;
        int row = Math.floorMod(lz + hz, 4);
        if (row != 1 && row != 2) return;
        // стеллаж: стойки, полки, коробки, бочки, сено, мешки
        if (Math.floorMod(lx, 4) == 2) {
            for (int y = 1; y <= 5; y++) c.set(s + y, st(Blocks.POLISHED_DEEPSLATE_WALL));
            return;
        }
        Block[] goods = {Blocks.BARREL, Blocks.PACKED_MUD, Blocks.HAY_BLOCK, Blocks.WHITE_WOOL, Blocks.PACKED_MUD, Blocks.BROWN_MUSHROOM_BLOCK};
        for (int lvl = 0; lvl < 3; lvl++) {
            double g = hash(lx * 11L + lvl, lz, px * 17 + pz);
            if (g < 0.8) c.set(s + 1 + lvl * 2, st(goods[(int) (g / 0.8 * goods.length)]));
            if (lvl < 2) c.set(s + 2 + lvl * 2, slabTop(Blocks.SMOOTH_STONE_SLAB));
        }
    }
}
