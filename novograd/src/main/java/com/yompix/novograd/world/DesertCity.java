package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.block.NeonBlock;
import com.yompix.novograd.entity.CitizenEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * «Мираж» — неоновый город казино в Пустоши (юго-восток острова, как Лас-Вентурас): Стрип вдоль улицы x = 1224 с шестью
 * казино-отелями (вывески-пилоны, неон, у «Пирамиды» — чёрная стеклянная пирамида), мотели с бассейнами, свадебная
 * часовня «Навеки», ломбард «Фортуна», дайнер и супермаркет (world/Venues), центр из невысоких домов с вывесками,
 * пригород, парки с бассейнами. Пустынная трасса z = 1368 от дороги в Пустошь, заправка у въезда, знаменитая вывеска
 * «Добро пожаловать», аэропорт с полосой к югу от города (рейсы — gta/Airport).
 * Казино настоящие: автоматы и рулетки из {@link Buildings#casinoHall}, крупье и касса появляются, когда подходишь.
 * Новые чанки строит генератор; старые сохранения дописывает {@link Retrofit}.
 */
public final class DesertCity {
    private DesertCity() {}

    public static final int X0 = 976, X1 = 1424, Z0 = 1168, Z1 = 1520;
    /** Полоса и терминал аэропорта (ровная площадка). */
    static final int RX0 = 1040, RX1 = 1360, RZ0 = 1540, RZ1 = 1556;
    /** Улица-Стрип. */
    public static final int STRIP_X = 1224;
    /** Трасса через Пустошь к городу. */
    public static final int HIGHWAY_Z = 1368;

    public static boolean inside(int x, int z) {
        return x >= X0 && x <= X1 && z >= Z0 && z <= Z1;
    }

    /** Здесь пустыня ровная (аэропорт, место вывески). */
    public static boolean flat(int x, int z) {
        return x >= 1020 && x <= 1410 && z > Z1 && z <= 1572 || x >= 940 && x <= 966 && z >= 1342 && z <= 1360;
    }

    public static List<Retrofit.Region> regions() {
        int G = Island.G;
        return List.of(new Retrofit.Region(X0, Z0, X1, Z1, G - 6, G + 66),
                new Retrofit.Region(1020, Z1 + 1, 1410, 1572, G - 4, G + 40),
                new Retrofit.Region(15, HIGHWAY_Z - 32, X0 + 8, HIGHWAY_Z + 36, G - 4, G + 40),
                new Retrofit.Region(X1 - 8, HIGHWAY_Z - 32, 1712, HIGHWAY_Z + 36, G - 4, G + 40));
    }

    // ------------------------------------------------------------------ участки
    static final int HOUSE = 5, LOWRISE = 9, CASINO = 3, MOTEL = 10, CHAPEL = 11, PAWN = 12, PARK = 4, VENUE = 13;

    record Casino(String name, Block accent, Block glass, Block gold, NeonBlock.Color neon, int floors) {}

    static final Casino[] CASINOS = {
            new Casino("Мираж", Blocks.GOLD_BLOCK, Blocks.YELLOW_STAINED_GLASS, Blocks.GOLD_BLOCK, NeonBlock.Color.YELLOW, 10),
            new Casino("Фламинго", Blocks.PINK_CONCRETE, Blocks.PINK_STAINED_GLASS, Blocks.WHITE_CONCRETE, NeonBlock.Color.PINK, 8),
            new Casino("Цезарь", Blocks.QUARTZ_PILLAR, Blocks.WHITE_STAINED_GLASS, Blocks.GOLD_BLOCK, NeonBlock.Color.BLUE, 7),
            new Casino("Звёздная пыль", Blocks.PURPLE_CONCRETE, Blocks.BLUE_STAINED_GLASS, Blocks.LAPIS_BLOCK, NeonBlock.Color.PURPLE, 9),
            new Casino("Пирамида", Blocks.BLACK_CONCRETE, Blocks.BLACK_STAINED_GLASS, Blocks.GOLD_BLOCK, NeonBlock.Color.CYAN, 0),
            new Casino("Золотой самородок", Blocks.ORANGE_CONCRETE, Blocks.ORANGE_STAINED_GLASS, Blocks.GOLD_BLOCK, NeonBlock.Color.ORANGE, 6),
    };

    /** Номер казино на участке или −1. */
    static int casino(long px, long pz) {
        if (px == 25 && pz == 26) return 0;
        if (px == 26 && pz == 26) return 1;
        if (px == 25 && pz == 27) return 2;
        if (px == 25 && pz == 28) return 3;
        if (px == 26 && pz == 29) return 4;
        if (px == 25 && pz == 30) return 5;
        return -1;
    }

    static int type(long px, long pz) {
        if (casino(px, pz) >= 0) return CASINO;
        if (Venues.lot(px, pz)) return VENUE;
        if (px == 25 && pz == 25 || px == 26 && pz == 30) return MOTEL;
        if (px == 26 && pz == 25) return CHAPEL;
        if (px == 26 && pz == 27) return PAWN;
        if ((px == 25 || px == 26) && pz == 31) return PARK;
        if (px >= 22 && px <= 24 && pz >= 26 && pz <= 30) return LOWRISE;
        return HOUSE;
    }

    /** Для Island.lotInfo: {вид, полуширина, этажи}. Казино — как на Ривьере (3), дома — пригород (5). */
    static int[] info(long px, long pz) {
        int t = type(px, pz);
        if (t == CASINO) return new int[]{3, 15, 2};
        return new int[]{t, 14, 2};
    }

    static double hash(long x, long z, long salt) {
        return Island.hash(x, z, salt);
    }

    private static BlockState st(Block b) {
        return b.getDefaultState();
    }

    private static BlockState neon(Direction f, NeonBlock.Color c) {
        return ModContent.NEON.getDefaultState().with(HorizontalFacingBlock.FACING, f).with(NeonBlock.COLOR, c).with(NeonBlock.LIT, true); // «Мираж» не гаснет и днём
    }

    static void lot(int lx, int lz, long px, long pz, int s, Island.Column c) {
        switch (type(px, pz)) {
            case CASINO -> casinoLot(lx, lz, px, pz, s, c, casino(px, pz));
            case MOTEL -> motel(lx, lz, px, pz, s, c);
            case CHAPEL -> chapel(lx, lz, s, c);
            case PAWN -> pawn(lx, lz, s, c);
            case PARK -> Buildings.resortPark(lx, lz, px, pz, s, c);
            case LOWRISE -> lowrise(lx, lz, px, pz, s, c);
            default -> Buildings.suburb(lx, lz, px, pz, s, c);
        }
    }

    // ------------------------------------------------------------------ казино-отели
    static void casinoLot(int lx, int lz, long px, long pz, int s, Island.Column c, int idx) {
        Casino k = CASINOS[idx];
        int ax = Math.abs(lx), az = Math.abs(lz), m = Math.max(ax, az), half = 15;
        int sx = px <= 25 ? 1 : -1; // в какую сторону Стрип
        Direction toStrip = sx > 0 ? Direction.EAST : Direction.WEST;
        if (m > half) { // площадь: песчаник, пальмы, красная дорожка, пилон с вывеской
            c.set(s, (ax + az) % 4 == 0 ? st(Blocks.SMOOTH_SANDSTONE) : st(Blocks.CUT_SANDSTONE));
            if (lz > half && ax <= 2) c.set(s + 1, st(Blocks.RED_CARPET));
            if (ax == 17 && az == 17) {
                for (int y = 1; y <= 6; y++) c.set(s + y, st(Blocks.JUNGLE_LOG));
                c.set(s + 7, Blocks.JUNGLE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
            }
            if (lx == 16 * sx && lz >= 4 && lz <= 14) {
                if (lz == 9) for (int y = 1; y <= 14; y++) c.set(s + y, st(k.gold()));
                for (int y = 15; y <= 22; y++) {
                    boolean frame = y == 15 || y == 22 || lz == 4 || lz == 14;
                    c.set(s + y, frame ? st(k.gold()) : neon(toStrip, (y + lz) % 3 == 0 ? NeonBlock.Color.YELLOW : k.neon()));
                }
            }
            return;
        }
        if (idx == 4) { // «Пирамида»: зал внизу, сверху — ступенчатая пирамида из чёрного стекла
            podium(lx, lz, s, c, k, m, half, ax);
            int y = s + 11 + (half - m) * 2;
            c.set(y, st(m % 3 == 0 ? Blocks.BLACK_CONCRETE : Blocks.BLACK_STAINED_GLASS));
            c.set(y + 1, st(Blocks.BLACK_STAINED_GLASS));
            if (m == 0) {
                c.set(y + 2, st(Blocks.SEA_LANTERN));
                c.set(y + 3, st(Blocks.END_ROD));
            }
            if (m == half && (ax == half && az == half)) for (int q = s + 11; q <= s + 13; q++) c.set(q, neon(lx > 0 ? Direction.EAST : Direction.WEST, k.neon()));
            return;
        }
        podium(lx, lz, s, c, k, m, half, ax);
        // башня отеля над залом
        int th = 8, n = k.floors();
        if (m > th) return;
        int top = s + 10 + 4 * n;
        for (int y = s + 11; y <= top; y++) {
            int f = (y - s - 10) % 4;
            if (m == th) {
                boolean corner = ax == th && az == th;
                if (corner) c.set(y, neon(lx > 0 ? Direction.EAST : Direction.WEST, k.neon()));
                else if (f == 0) c.set(y, st(k.accent() == Blocks.QUARTZ_PILLAR ? Blocks.QUARTZ_BLOCK : k.accent()));
                else {
                    double h = hash(lx * 31L + lz, y, idx);
                    c.set(y, f == 2 && h < 0.14 ? st(Blocks.OCHRE_FROGLIGHT) : f == 2 && h < 0.26 ? st(Blocks.YELLOW_STAINED_GLASS) : st(k.glass())); // окна номеров: часть светится
                }
            } else if (f == 0) c.set(y, st(Blocks.SMOOTH_STONE));
        }
        // корона: неоновое кольцо, у «Миража» — шпиль с огнём
        c.set(top + 1, st(k.gold()));
        if (m == th) {
            c.set(top + 2, neon(lx > 0 ? Direction.EAST : Direction.WEST, k.neon()));
            c.set(top + 3, neon(lz > 0 ? Direction.SOUTH : Direction.NORTH, NeonBlock.Color.YELLOW));
        }
        if (idx == 0 && ax <= 1 && az <= 1) {
            int h = ax == 0 && az == 0 ? 14 : 6;
            for (int y = top + 2; y <= top + h; y++) c.set(y, st(Blocks.GOLD_BLOCK));
            if (ax == 0 && az == 0) c.set(top + h + 1, st(Blocks.SEA_LANTERN));
        }
        if (idx == 2 && m == th && (ax + az) % 4 == 0) c.set(top + 2, st(Blocks.QUARTZ_PILLAR)); // «Цезарь»: колонны на крыше
    }

    /** Подиум 31×31 в два этажа: стеклянный низ, золото, неон по верху; внутри — игровой зал. */
    private static void podium(int lx, int lz, int s, Island.Column c, Casino k, int m, int half, int ax) {
        if (m == half) {
            for (int y = s + 1; y <= s + 9; y++) {
                if (y <= s + 3 && ax <= 2 && lz == half) continue; // вход
                Direction f = lz == half ? Direction.SOUTH : lz == -half ? Direction.NORTH : lx > 0 ? Direction.EAST : Direction.WEST;
                if (y >= s + 7) c.set(y, neon(f, (y + ax) % 4 == 0 ? NeonBlock.Color.YELLOW : k.neon()));
                else if (y == s + 4) c.set(y, st(k.accent()));
                else if (y >= s + 5) c.set(y, st(k.gold()));
                else c.set(y, ax % 4 == 0 && Math.abs(lz) % 4 == 0 ? st(k.accent()) : st(Blocks.GLASS));
            }
            if (lz == half && ax <= 3) c.set(s + 4, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState()); // козырёк
        } else {
            c.set(s + 8, st(Blocks.BLACK_CONCRETE));
            Buildings.casinoHall(lx, lz, s, c, half - 1);
        }
        c.set(s + 10, st(Blocks.WHITE_CONCRETE));
    }

    // ------------------------------------------------------------------ мотель
    private static void door(Island.Column c, int y, Block b, Direction inward, DoorHinge hinge) {
        BlockState d = b.getDefaultState().with(DoorBlock.FACING, inward).with(DoorBlock.HINGE, hinge).with(DoorBlock.OPEN, false);
        c.set(y, d.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(y + 1, d.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Мотель: двухэтажный корпус с галереей и номерами, бассейн во дворе, парковка, вывеска на столбе. */
    static void motel(int lx, int lz, long px, long pz, int s, Island.Column c) {
        boolean cactus = px == 25;
        Block wall = cactus ? Blocks.LIME_TERRACOTTA : Blocks.PINK_TERRACOTTA, trim = cactus ? Blocks.GREEN_CONCRETE : Blocks.MAGENTA_CONCRETE;
        c.set(s, st(Blocks.SMOOTH_SANDSTONE));
        if (lx >= -15 && lx <= -8 && lz >= -14 && lz <= 14) { // корпус
            boolean edge = lx == -15 || lx == -8 || Math.abs(lz) == 14;
            c.set(s + 4, st(trim));
            c.set(s + 8, st(trim));
            c.set(s + 9, edge ? Blocks.SMOOTH_SANDSTONE_SLAB.getDefaultState() : st(Blocks.AIR));
            boolean partition = Math.floorMod(lz, 4) == 0 && lx < -8 && lx > -15;
            for (int fl = 0; fl < 2; fl++) {
                int b = s + 4 * fl;
                if (edge || partition) {
                    for (int y = b + 1; y <= b + 3; y++) c.set(y, st(wall));
                    if (lx == -8 && Math.floorMod(lz, 4) == 2) door(c, b + 1, Blocks.SPRUCE_DOOR, Direction.WEST, DoorHinge.LEFT);
                    else if (lx == -8 && Math.floorMod(lz, 4) == 3 && Math.abs(lz) < 14) c.set(b + 2, st(Blocks.GLASS));
                } else if (lx == -14 && Math.floorMod(lz, 4) == 2) { // кровать в номере
                    c.set(b + 1, Blocks.RED_BED.getDefaultState().with(net.minecraft.block.BedBlock.FACING, Direction.WEST)
                            .with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.HEAD));
                } else if (lx == -13 && Math.floorMod(lz, 4) == 2) {
                    c.set(b + 1, Blocks.RED_BED.getDefaultState().with(net.minecraft.block.BedBlock.FACING, Direction.WEST)
                            .with(net.minecraft.block.BedBlock.PART, net.minecraft.block.enums.BedPart.FOOT));
                } else if (lx == -14 && Math.floorMod(lz, 4) == 1) c.set(b + 1, st(Blocks.LANTERN));
            }
            return;
        }
        if (lx == -7 && lz >= -14 && lz <= 14) { // галерея второго этажа с перилами
            c.set(s + 4, Blocks.SMOOTH_SANDSTONE_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.TOP));
            if (lz > -10) c.set(s + 5, st(Blocks.OAK_FENCE));
            if (Math.floorMod(lz, 6) == 0) for (int y = s + 1; y <= s + 3; y++) c.set(y, st(Blocks.OAK_FENCE));
            return;
        }
        if (lx == -6 && lz >= -13 && lz <= -10) { // наружная лестница на галерею
            int k = lz + 13;
            for (int y = s + 1; y < s + 1 + k; y++) c.set(y, st(Blocks.SMOOTH_SANDSTONE));
            c.set(s + 1 + k, Blocks.SANDSTONE_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
            return;
        }
        if (lx >= 0 && lx <= 10 && lz >= -5 && lz <= 7) { // бассейн
            boolean rim = lx == 0 || lx == 10 || lz == -5 || lz == 7;
            if (rim) c.set(s, st(Blocks.SMOOTH_QUARTZ));
            else {
                c.set(s - 2, st(Blocks.LIGHT_BLUE_TERRACOTTA));
                c.set(s - 1, st(Blocks.WATER));
                c.set(s, st(Blocks.WATER));
            }
            return;
        }
        if (lx == 12 && lz >= -4 && lz <= 6 && lz % 2 == 0) { // шезлонги
            c.set(s + 1, Blocks.BIRCH_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST));
            return;
        }
        if (lz <= -9 && lx >= -5) { // парковка
            c.set(s, ModContent.ASPHALT.getDefaultState());
            if (lz >= -15 && lz <= -10 && Math.floorMod(lx, 4) == 0) c.set(s, st(Blocks.WHITE_CONCRETE));
        }
        if (lz == -16 && lx >= 9 && lx <= 15) { // вывеска «МОТЕЛЬ» на столбе
            if (lx == 12) for (int y = 1; y <= 9; y++) c.set(s + y, st(Blocks.IRON_BLOCK));
            for (int y = 10; y <= 14; y++) {
                boolean frame = y == 10 || y == 14 || lx == 9 || lx == 15;
                c.set(s + y, frame ? st(trim) : neon(Direction.NORTH, (lx + y) % 2 == 0 ? NeonBlock.Color.RED : NeonBlock.Color.YELLOW));
            }
            return;
        }
        if (Math.abs(lx) == 16 && Math.floorMod(lz, 9) == 0) { // пальмы
            for (int y = 1; y <= 5; y++) c.set(s + y, st(Blocks.JUNGLE_LOG));
            c.set(s + 6, Blocks.JUNGLE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
        }
    }

    // ------------------------------------------------------------------ свадебная часовня
    static void chapel(int lx, int lz, int s, Island.Column c) {
        int ax = Math.abs(lx), az = Math.abs(lz);
        c.set(s, st(Blocks.GRASS_BLOCK));
        if (ax <= 1 && lz > 9) c.set(s, st(Blocks.SMOOTH_SANDSTONE));
        if (lz == 13 && ax == 2) for (int y = 1; y <= 3; y++) c.set(s + y, Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
        if (lz == 13 && ax <= 2) c.set(s + 4, Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
        if ((ax == 16 || az == 16) && !(lz == 16 && ax <= 1)) c.set(s + 1, st(Blocks.BIRCH_FENCE));
        if (ax > 6 || az > 9) {
            if (ax <= 12 && az <= 12 && (ax + az) % 5 == 0) c.set(s + 1, st(Blocks.ROSE_BUSH));
            if (lz == 10 && ax <= 3) { // неоновое сердце на фасаде
                int[][] heart = {{0}, {-1, 0, 1}, {-2, -1, 0, 1, 2}, {-3, -2, -1, 0, 1, 2, 3}, {-3, -2, -1, 0, 1, 2, 3}, {-2, -1, 1, 2}};
                for (int r = 0; r < heart.length; r++) for (int hx : heart[r]) if (hx == lx) c.set(s + 8 + r, neon(Direction.SOUTH, NeonBlock.Color.PINK));
            }
            return;
        }
        boolean wall = ax == 6 || az == 9;
        c.set(s, st(Blocks.SMOOTH_QUARTZ));
        if (wall) {
            for (int y = s + 1; y <= s + 6; y++) c.set(y, st(Blocks.WHITE_CONCRETE));
            if (ax == 6 && lz % 3 == 0 && az < 9) for (int y = s + 2; y <= s + 4; y++) c.set(y, st(Blocks.PINK_STAINED_GLASS));
            if (lz == 9 && (lx == 0 || lx == -1)) {
                door(c, s + 1, Blocks.BIRCH_DOOR, Direction.NORTH, lx == 0 ? DoorHinge.LEFT : DoorHinge.RIGHT);
                c.set(s + 3, st(Blocks.PINK_STAINED_GLASS));
            }
        } else {
            if (ax == 0) c.set(s + 1, st(Blocks.WHITE_CARPET));
            else if (lz >= -4 && lz <= 6 && lz % 2 == 0 && ax >= 2) c.set(s + 1, Blocks.BIRCH_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
            if (lz == -8 && ax <= 1) {
                c.set(s + 1, st(Blocks.QUARTZ_BLOCK));
                c.set(s + 2, ax == 0 ? st(Blocks.POTTED_POPPY) : Blocks.CANDLE.getDefaultState().with(net.minecraft.block.CandleBlock.LIT, true));
            }
            if (lz == -7 && ax == 2) for (int y = 1; y <= 3; y++) c.set(s + y, Blocks.FLOWERING_AZALEA_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
            if (lz % 4 == 0 && ax == 3) c.set(s + 6, Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
        }
        // двускатная крыша и колокольня над входом
        int roof = s + 7 + (6 - ax);
        for (int y = s + 7; y <= roof; y++) c.set(y, st(y == roof ? Blocks.QUARTZ_BRICKS : Blocks.WHITE_CONCRETE));
        if (ax <= 1 && lz >= 7) {
            for (int y = roof + 1; y <= s + 19; y++) c.set(y, ax == 0 && lz == 8 && y >= s + 15 && y <= s + 17 ? st(Blocks.AIR) : st(Blocks.WHITE_CONCRETE));
            if (ax == 0 && lz == 8) {
                c.set(s + 16, st(Blocks.BELL));
                c.set(s + 20, st(Blocks.GOLD_BLOCK));
                c.set(s + 21, st(Blocks.END_ROD));
            }
        }
    }

    // ------------------------------------------------------------------ ломбард
    static void pawn(int lx, int lz, int s, Island.Column c) {
        int ax = Math.abs(lx);
        c.set(s, st(Blocks.SMOOTH_STONE));
        if (ax > 9 || lz < -13 || lz > 0) {
            if (lz > 0 && ax <= 14) { // за ломбардом — склад
                boolean e = ax == 14 || lz == 1 || lz == 15;
                if (lz <= 15) for (int y = s + 1; y <= s + 5; y++) if (e) c.set(y, st(Blocks.BROWN_TERRACOTTA));
                if (lz <= 15) c.set(s + 6, st(Blocks.GRAY_CONCRETE));
            }
            return;
        }
        boolean wall = ax == 9 || lz == -13 || lz == 0;
        c.set(s, st(Blocks.DARK_OAK_PLANKS));
        c.set(s + 5, st(Blocks.GRAY_CONCRETE));
        if (wall) {
            for (int y = s + 1; y <= s + 4; y++) c.set(y, st(Blocks.YELLOW_TERRACOTTA));
            if (lz == -13) {
                if (lx == 0) door(c, s + 1, Blocks.IRON_DOOR, Direction.SOUTH, DoorHinge.LEFT);
                else if (ax >= 2 && ax <= 7) {
                    c.set(s + 2, st(Blocks.IRON_BARS));
                    c.set(s + 3, st(Blocks.IRON_BARS));
                }
                c.set(s + 5, neon(Direction.NORTH, ax % 2 == 0 ? NeonBlock.Color.YELLOW : NeonBlock.Color.ORANGE));
            }
            return;
        }
        if (lz == -6 && ax <= 6) {
            c.set(s + 1, ModContent.SHOP_COUNTER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
            if (lx == 3) c.set(s + 2, st(Blocks.GOLD_BLOCK));
            if (lx == -3) c.set(s + 2, st(Blocks.JUKEBOX));
            return;
        }
        if (lz == -1 && ax <= 8) { // полки с «товаром»
            c.set(s + 1, ax % 3 == 0 ? st(Blocks.ANVIL) : st(Blocks.BOOKSHELF));
            c.set(s + 2, ax % 2 == 0 ? st(Blocks.BELL) : st(Blocks.BOOKSHELF));
            c.set(s + 3, st(Blocks.BOOKSHELF));
        }
        if ((ax == 8) && lz >= -11 && lz <= -8) c.set(s + 1, lz % 2 == 0 ? st(Blocks.LODESTONE) : st(Blocks.CHEST));
    }

    // ------------------------------------------------------------------ центр: невысокие дома с вывесками
    static void lowrise(int lx, int lz, long px, long pz, int s, Island.Column c) {
        int ax = Math.abs(lx), az = Math.abs(lz), m = Math.max(ax, az);
        int floors = 2 + (int) (hash(px, pz, 301) * 3), half = 12 + (int) (hash(px, pz, 302) * 3);
        Block[] walls = {Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA};
        Block wall = walls[(int) (hash(px, pz, 303) * walls.length)];
        NeonBlock.Color[] cols = NeonBlock.Color.values();
        NeonBlock.Color nc = cols[(int) (hash(px, pz, 304) * cols.length)];
        c.set(s, st(Blocks.SMOOTH_STONE));
        if (m > half) {
            if (m == half + 2 && (ax + az) % 7 == 0) c.set(s + 1, st(Blocks.POTTED_CACTUS));
            return;
        }
        int top = s + 4 * floors;
        boolean edge = m == half;
        for (int y = s + 1; y <= top; y++) {
            int f = (y - s) % 4;
            if (edge) {
                Direction face = ax == half && ax >= az ? (lx > 0 ? Direction.EAST : Direction.WEST) : (lz > 0 ? Direction.SOUTH : Direction.NORTH);
                if (y <= s + 3) { // витрины первого этажа, вход с юга
                    if (lz == half && ax <= 1) continue;
                    c.set(y, ax == half && az == half ? st(wall) : y == s + 3 ? st(wall) : st(Blocks.GLASS));
                } else if (y == s + 4) c.set(y, neon(face, nc));
                else c.set(y, f == 2 && (lx + lz) % 3 != 0 ? st(hash(lx, y + lz, px * 7 + pz) < 0.15 ? Blocks.OCHRE_FROGLIGHT : hash(lx, y + lz, px * 7 + pz) < 0.3 ? Blocks.YELLOW_STAINED_GLASS : Blocks.GRAY_STAINED_GLASS) : st(wall));
            } else if (f == 0) c.set(y, st(Blocks.SMOOTH_STONE));
            else if (y <= s + 3 && ((ax == 4 && az <= 8) || (az == 4 && ax <= 8)) && f == 1) c.set(y, st(Blocks.BARREL));
        }
        c.set(top + 1, edge ? Blocks.SMOOTH_SANDSTONE_SLAB.getDefaultState() : st(Blocks.AIR));
        if (!edge && ax <= 2 && az <= 2 && (px + pz) % 2 == 0) c.set(top + 1, st(Blocks.IRON_BLOCK)); // кондиционер
        if (lz == 0 && ax <= 4 && hash(px, pz, 305) < 0.5) { // щит на крыше
            int col = lx + 4, ad = (int) (hash(px, pz, 306) * 5);
            if (ax <= 3) for (int row = 0; row < 3; row++) c.set(top + 4 - row, CityBlocks.billboard(ad, Direction.SOUTH, Math.min(5, col), row));
            if (ax == 4) for (int y = top + 1; y <= top + 2; y++) c.set(y, st(Blocks.POLISHED_DEEPSLATE_WALL));
        }
    }

    // ------------------------------------------------------------------ пустыня у города: вывеска, аэропорт
    /** Столбец пустыни: аэропорт к югу от города и вывеска у трассы. true — построено. */
    static boolean desert(int x, int z, int s, Island.Column c) {
        if (x >= RX0 - 20 && x <= 1410 && z > Z1 && z <= 1572) {
            c.set(s, st(Blocks.RED_SAND));
            if (x >= RX0 && x <= RX1 && z >= RZ0 && z <= RZ1) {
                c.set(s, ModContent.ASPHALT.getDefaultState());
                if (z == (RZ0 + RZ1) / 2 && Math.floorMod(x, 20) < 12) c.set(s, st(Blocks.WHITE_CONCRETE));
                if ((x < RX0 + 16 || x > RX1 - 16) && Math.floorMod(z - RZ0, 3) == 0 && z > RZ0 && z < RZ1) c.set(s, st(Blocks.WHITE_CONCRETE));
                if ((z == RZ0 || z == RZ1) && Math.floorMod(x, 10) == 0) c.set(s, Blocks.REDSTONE_LAMP.getDefaultState().with(net.minecraft.block.RedstoneLampBlock.LIT, true));
                return true;
            }
            if (x >= 1366 && x <= 1400 && z >= 1526 && z <= 1538) { // терминал
                boolean e = x == 1366 || x == 1400 || z == 1526 || z == 1538;
                c.set(s, st(Blocks.SMOOTH_QUARTZ));
                if (e) for (int y = s + 1; y <= s + 4; y++) c.set(y, z == 1526 && y <= s + 3 && Math.abs(x - 1386) > 1 ? st(Blocks.LIGHT_BLUE_STAINED_GLASS)
                        : z == 1526 && Math.abs(x - 1386) <= 1 && y <= s + 2 ? st(Blocks.AIR) : st(Blocks.WHITE_CONCRETE));
                c.set(s + 5, Math.floorMod(x, 4) == 0 && !e ? st(Blocks.SEA_LANTERN) : st(Blocks.WHITE_CONCRETE));
                if (x == 1394 && z == 1532) for (int y = s + 6; y <= s + 14; y++) c.set(y, y >= s + 12 ? st(Blocks.LIGHT_BLUE_STAINED_GLASS) : st(Blocks.WHITE_CONCRETE));
                if (z == 1534 && x >= 1372 && x <= 1380 && x % 2 == 0) c.set(s + 1, Blocks.BIRCH_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH));
                return true;
            }
            if (x >= RX0 && x <= RX1 + 40 && z > RZ1 && z <= RZ1 + 6) c.set(s, st(Blocks.SMOOTH_SANDSTONE)); // рулёжка
            return true;
        }
        if (x >= 944 && x <= 962 && z >= 1346 && z <= 1356) { // «Добро пожаловать в сказочный Мираж»
            c.set(s, st(Blocks.RED_SAND));
            int dx = x - 953;
            if (z == 1352) {
                if (dx == -2 || dx == 2) for (int y = 1; y <= 3; y++) c.set(s + y, st(Blocks.WHITE_CONCRETE));
                for (int r = 0; r <= 6; r++) {
                    int w = 6 - Math.abs(r - 3);
                    if (Math.abs(dx) > w + 2) continue;
                    boolean frame = Math.abs(dx) == w + 2 || r == 0 || r == 6;
                    c.set(s + 4 + r, frame ? neon(Direction.SOUTH, NeonBlock.Color.YELLOW) : (r == 3 ? neon(Direction.SOUTH, NeonBlock.Color.RED) : st(Blocks.WHITE_CONCRETE)));
                }
            }
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ люди и надписи (когда игрок рядом)
    private static final String TAG = "nv_mirage";

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s.getTicks() % 60 != 47) return;
            try {
                tick(s);
            } catch (RuntimeException e) {
                Novograd.LOG.warn("Мираж: ошибка", e);
            }
        });
    }

    private record Spot(String key, Vec3d at, float yaw, CitizenEntity.Role role, String name, int skin) {}

    private record Sign(String key, Vec3d at, String text, Formatting color, float scale) {}

    private static List<Spot> spots;
    private static List<Sign> signs;

    private static void build() {
        if (spots != null) return;
        spots = new ArrayList<>();
        signs = new ArrayList<>();
        int y = Island.G + 1;
        for (int px = 25; px <= 26; px++)
            for (int pz = 25; pz <= 31; pz++) {
                int idx = casino(px, pz);
                if (idx < 0) continue;
                int cx = px * 48, cz = pz * 48, sx = px <= 25 ? 1 : -1;
                String key = "casino" + idx;
                spots.add(new Spot(key, new Vec3d(cx - 2.5, y, cz + 11.5), 180, CitizenEntity.Role.CROUPIER, "Администратор «" + CASINOS[idx].name() + "»", 6));
                spots.add(new Spot(key, new Vec3d(cx + 6.5, y, cz - 3.5), 0, CitizenEntity.Role.DEALER, "Крупье", 5));
                spots.add(new Spot(key, new Vec3d(cx + 11.5, y, cz + 12.5), 90, CitizenEntity.Role.BANKER, "Касса казино", 8));
                signs.add(new Sign(key, new Vec3d(cx + 16.5 * sx + 0.5 + sx * 1.2, Island.G + 19.5, cz + 9.5),
                        "♣ " + CASINOS[idx].name().toUpperCase() + " ♣", Formatting.GOLD, 4f));
                signs.add(new Sign(key, new Vec3d(cx + 0.5, Island.G + 5.6, cz + 17.2), "КАЗИНО-ОТЕЛЬ «" + CASINOS[idx].name().toUpperCase() + "»", Formatting.YELLOW, 1.6f));
            }
        spots.add(new Spot("chapel", new Vec3d(26 * 48 + 0.5, y, 25 * 48 - 6.5), 0, CitizenEntity.Role.CITIZEN, "Священник Элвис", 9));
        signs.add(new Sign("chapel", new Vec3d(26 * 48 + 0.5, Island.G + 15.5, 25 * 48 + 10.8), "ЧАСОВНЯ «НАВЕКИ» · свадьбы 24/7", Formatting.LIGHT_PURPLE, 1.6f));
        spots.add(new Spot("pawn", new Vec3d(26 * 48 + 0.5, y, 27 * 48 - 4.5), 180, CitizenEntity.Role.SHOPKEEPER, "Ломбард «Фортуна»: Сэм", 7));
        signs.add(new Sign("pawn", new Vec3d(26 * 48 + 0.5, Island.G + 7.4, 27 * 48 - 13.8), "ЛОМБАРД «ФОРТУНА»", Formatting.YELLOW, 1.6f));
        signs.add(new Sign("motel_c", new Vec3d(25 * 48 + 12.5, Island.G + 16.6, 25 * 48 - 16.8), "МОТЕЛЬ «КАКТУС»", Formatting.GREEN, 2.2f));
        signs.add(new Sign("motel_p", new Vec3d(26 * 48 + 12.5, Island.G + 16.6, 30 * 48 - 16.8), "МОТЕЛЬ «ПАЛЬМА»", Formatting.LIGHT_PURPLE, 2.2f));
        signs.add(new Sign("welcome", new Vec3d(953.5, Island.G + 13.2, 1352.5), "ДОБРО ПОЖАЛОВАТЬ\nВ СКАЗОЧНЫЙ «МИРАЖ»", Formatting.RED, 2.6f));
        signs.add(new Sign("air", new Vec3d(1386.5, Island.G + 6.6, 1525.5), "✈ АЭРОПОРТ «МИРАЖ»", Formatting.AQUA, 1.8f));
    }

    private static void tick(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        if (!(w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) return;
        ServerPlayerEntity any = null;
        for (ServerPlayerEntity p : w.getPlayers()) if (p.getX() > 860 && p.getZ() > 1240 - 300) any = p;
        if (any == null) return;
        build();
        for (Spot sp : spots) {
            if (!nearPlayer(w, sp.at(), 64) || !ready(w, sp.at())) continue;
            Box b = new Box(sp.at(), sp.at()).expand(1.5, 2, 1.5);
            if (!w.getEntitiesByClass(CitizenEntity.class, b, e -> e.getCommandTags().contains(TAG)).isEmpty()) continue;
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            c.refreshPositionAndAngles(sp.at().x, sp.at().y, sp.at().z, sp.yaw(), 0);
            c.setHeadYaw(sp.yaw());
            c.setBodyYaw(sp.yaw());
            c.setup(sp.skin(), sp.role(), true, sp.name());
            c.addCommandTag(TAG);
            w.spawnEntity(c);
        }
        for (Sign sg : signs) {
            if (!nearPlayer(w, sg.at(), 96) || !ready(w, sg.at())) continue;
            Box b = new Box(sg.at(), sg.at()).expand(1, 1.5, 1);
            if (!w.getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, b, e -> e.getCommandTags().contains(TAG)).isEmpty()) continue;
            DisplayEntity.TextDisplayEntity d = EntityType.TEXT_DISPLAY.create(w);
            if (d == null) continue;
            NbtCompound n = new NbtCompound();
            n.putString("billboard", "center");
            n.putString("alignment", "center");
            n.putInt("background", 0x99000000);
            n.putInt("line_width", 400);
            NbtCompound br = new NbtCompound();
            br.putInt("block", 15);
            br.putInt("sky", 15);
            n.put("brightness", br);
            NbtCompound tr = new NbtCompound();
            tr.put("translation", Metro.floats(0, 0, 0));
            tr.put("left_rotation", Metro.floats(0, 0, 0, 1));
            tr.put("scale", Metro.floats(sg.scale(), sg.scale(), sg.scale()));
            tr.put("right_rotation", Metro.floats(0, 0, 0, 1));
            n.put("transformation", tr);
            d.readNbt(n);
            d.setText(Text.literal(sg.text()).formatted(sg.color(), Formatting.BOLD));
            d.refreshPositionAndAngles(sg.at().x, sg.at().y, sg.at().z, 0, 0);
            d.addCommandTag(TAG);
            w.spawnEntity(d);
        }
    }

    private static boolean nearPlayer(ServerWorld w, Vec3d at, double r) {
        for (ServerPlayerEntity p : w.getPlayers()) if (p.squaredDistanceTo(at.x, p.getY(), at.z) < r * r) return true;
        return false;
    }

    /** Чанк загружен и уже дописан (отметка или новый мир). */
    private static boolean ready(ServerWorld w, Vec3d at) {
        BlockPos p = BlockPos.ofFloored(at);
        if (!w.isChunkLoaded(p)) return false;
        return w.getBlockState(new BlockPos(p.getX() & ~15, Retrofit.MARK_Y, p.getZ() & ~15)).isOf(Blocks.DEEPSLATE);
    }
}
