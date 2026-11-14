package com.yompix.glukhoman.world;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.block.LampBlock;
import com.yompix.glukhoman.story.StoryState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.RailShape;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Direction;

import static com.yompix.glukhoman.world.Terrain.G;

/**
 * Строит все сюжетные места один раз, при первом запуске мира «Глухомань»:
 * старт у моста, посёлок (дом Анны, магазин, сельсовет, школа, гараж, подстанция), кладбище, избушку охотника,
 * метеостанцию с мачтой и бункером, шахту с копром, лифтом и штольнями. Регистрирует точки и интерактивные вещи в StoryState.
 */
public final class MapBuilder {
    private MapBuilder() {}

    static final BlockState LOG = Blocks.SPRUCE_LOG.getDefaultState();
    static final BlockState DLOG = Blocks.DARK_OAK_LOG.getDefaultState();
    static final BlockState PLANK = Blocks.SPRUCE_PLANKS.getDefaultState();
    static final BlockState DPLANK = Blocks.DARK_OAK_PLANKS.getDefaultState();
    static final BlockState WALL = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Y);
    static final BlockState PANE = Blocks.GLASS_PANE.getDefaultState();
    static final BlockState AIR = Blocks.AIR.getDefaultState();

    public static void build(ServerWorld world, StoryState st) {
        long t0 = System.currentTimeMillis();
        // прогружаем чанки площадок, чтобы ставить блоки
        for (int[] s : Terrain.SITES)
            for (int x = s[0] - s[2] - 16; x <= s[0] + s[2] + 16; x += 16)
                for (int z = s[1] - s[3] - 16; z <= s[1] + s[3] + 16; z += 16) world.getChunk(x >> 4, z >> 4);
        for (int x = -150; x <= -70; x += 16) for (int z = -240; z <= -130; z += 16) world.getChunk(x >> 4, z >> 4);
        B b = new B(world, st);
        start(b);
        village(b);
        cemetery(b);
        hunter(b);
        station(b);
        bunker(b);
        mine(b);
        st.built = true;
        st.markDirty();
        Glukhoman.LOG.info("Глухомань: карта построена за {} мс, точек {}, вещей {}", System.currentTimeMillis() - t0, st.points.size(), st.interact.size());
    }

    // ================= старт: УАЗ у закрытого моста =================
    static void start(B b) {
        // мост через реку: доски, середина обрушена
        for (int z = 249; z <= 268; z++) {
            boolean broken = z >= 255 && z <= 261;
            for (int x = -2; x <= 2; x++) {
                if (!broken || (x == -2 && z == 255) || (x == 2 && z == 261)) b.set(x, G, z, PLANK);
                if (!broken && (x == -2 || x == 2)) b.set(x, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
            }
        }
        for (int x = -2; x <= 2; x += 4) for (int y = G - 6; y < G; y++) {
            b.set(x, y, 252, DLOG);
            b.set(x, y, 264, DLOG);
        }
        // шлагбаум «Мост закрыт»
        for (int x = -3; x <= 3; x++) b.set(x, G + 1, 247, (x & 1) == 0 ? Blocks.RED_WOOL.getDefaultState() : Blocks.WHITE_WOOL.getDefaultState());
        b.set(-4, G + 1, 247, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(4, G + 1, 247, Blocks.SPRUCE_FENCE.getDefaultState());
        // УАЗ-«буханка»: зелёный кузов, окна, колёса
        int x0 = 4, z0 = 236;
        b.fill(x0, G + 1, z0, x0 + 2, G + 3, z0 + 5, Blocks.GREEN_TERRACOTTA);
        b.fill(x0, G + 2, z0, x0 + 2, G + 2, z0, Blocks.GLASS_PANE);
        b.fill(x0, G + 2, z0 + 1, x0, G + 2, z0 + 4, Blocks.GLASS_PANE);
        b.fill(x0 + 2, G + 2, z0 + 1, x0 + 2, G + 2, z0 + 4, Blocks.GLASS_PANE);
        b.fill(x0 + 1, G + 1, z0 + 1, x0 + 1, G + 2, z0 + 4, Blocks.AIR);
        for (int[] wh : new int[][]{{x0 - 1, z0 + 1}, {x0 + 3, z0 + 1}, {x0 - 1, z0 + 4}, {x0 + 3, z0 + 4}})
            b.set(wh[0], G + 1, wh[1], Blocks.BLACK_CONCRETE);
        b.set(x0 + 1, G + 1, z0 + 5, Blocks.AIR);
        b.set(x0 + 1, G + 2, z0 + 5, Blocks.AIR);
        b.use(x0 + 1, G + 1, z0 + 1, GContent.RADIO, Direction.SOUTH, "radio_uaz");
        b.point("start", 0, G + 1, 238);
        b.point("cp1", 0, G + 1, 238);
        b.point("uaz", x0 + 1, G + 1, z0 + 1);
        // кривой столб и несколько ящиков рядом
        b.use(-5, G + 1, 238, GContent.CRATE, Direction.EAST, "crate_start");
    }

    // ================= посёлок =================
    static void village(B b) {
        // улица: плиты-фонари, забор по краю
        for (int z = 40; z <= 160; z += 16) streetLamp(b, 3, z);
        for (int z = 34; z <= 165; z++) {
            if (b.chance(0.82) && Math.abs(z - 72) > 2) b.set(-28, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
            if (b.chance(0.82)) b.set(28, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
        }
        b.zone("village", -30, G - 5, 30, 30, G + 20, 168);
        b.zone("village_in", -30, G - 5, 30, 30, G + 20, 156);

        // дом Анны (Лесная, 7) — запад
        izba(b, -17, 124, 9, 9, Direction.EAST, 0);
        b.zone("anna_house", -17, G, 124, -9, G + 6, 132);
        b.point("safe_anna", -13, G + 1, 128);
        b.point("cp2", -12, G + 1, 128);
        b.point("anna_door", -9, G + 1, 128);
        b.use(-15, G + 2, 126, GContent.RADIO, Direction.EAST, "radio_anna");
        b.use(-16, G + 2, 126, GContent.NOTE, Direction.EAST, "note:anna_note");
        b.use(-10, G + 1, 131, GContent.CRATE, Direction.WEST, "crate_anna");
        b.use(-16, G + 1, 129, Blocks.FURNACE, Direction.EAST, "stove_anna");
        b.set(-16, G + 2, 129, Blocks.BRICKS);
        b.set(-16, G + 3, 129, Blocks.BRICKS);

        // магазин «Продукты» — восток
        brickShop(b, 9, 138);
        // дома
        izba(b, -17, 110, 9, 9, Direction.EAST, 1);
        izba(b, 9, 110, 9, 9, Direction.WEST, 2);
        izba(b, -17, 96, 9, 9, Direction.EAST, 2);
        izba(b, -17, 140, 9, 9, Direction.EAST, 1);
        izba(b, 9, 124, 9, 9, Direction.WEST, 1);
        council(b, 9, 92);
        school(b, -24, 72);
        garage(b, 9, 62);
        substation(b, -20, 48);
        waterTower(b, 20, 50);
        // ворота на просеку: забор поперёк и решётка с замком
        for (int x = -28; x <= 28; x++) {
            if (Math.abs(x) <= 1) continue;
            b.set(x, G + 1, 34, LOG);
            b.set(x, G + 2, 34, Blocks.SPRUCE_FENCE.getDefaultState());
        }
        b.fill(-1, G + 1, 34, 1, G + 3, 34, Blocks.IRON_BARS);
        b.set(-2, G + 3, 34, LOG);
        b.set(2, G + 3, 34, LOG);
        b.use(2, G + 2, 35, GContent.LOCK, Direction.SOUTH, "lock_gate");
        b.point("gate", 0, G + 1, 35);
        b.point("cp3", 0, G + 1, 28);
    }

    static void streetLamp(B b, int x, int z) {
        for (int y = G + 1; y <= G + 4; y++) b.set(x, y, z, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(x, G + 5, z, GContent.STREET_LAMP.getDefaultState().with(LampBlock.LIT, false));
        b.st.lamp("village", new net.minecraft.util.math.BlockPos(x, G + 5, z));
    }

    /** Изба: сруб, двускатная крыша, окна, печь, кровать, стол. variant 1 — брошенная, 2 — полуразрушенная. */
    static void izba(B b, int x0, int z0, int w, int d, Direction door, int variant) {
        int x1 = x0 + w - 1, z1 = z0 + d - 1;
        b.ground(x0 - 1, z0 - 1, x1 + 1, z1 + 1, G, Blocks.COARSE_DIRT.getDefaultState(), 12);
        b.fill(x0, G, z0, x1, G, z1, PLANK);
        b.walls(x0, G + 1, z0, x1, G + 3, z1, WALL);
        for (int y = G + 1; y <= G + 3; y++) for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) b.set(c[0], y, c[1], LOG);
        // окна
        int mz = (z0 + z1) / 2, mx = (x0 + x1) / 2;
        for (int z : new int[]{z0 + 2, z1 - 2}) {
            b.set(x0, G + 2, z, variant == 2 && b.chance(0.5) ? AIR : PANE);
            b.set(x1, G + 2, z, variant == 2 && b.chance(0.5) ? AIR : PANE);
        }
        b.set(mx, G + 2, z0, PANE);
        b.set(mx, G + 2, z1, PANE);
        // крыша вдоль Z
        int k = 0;
        for (; x0 - 1 + k <= x1 + 1 - k; k++) {
            int y = G + 4 + k;
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (variant == 2 && z > z1 - 3 && k < 3 && b.chance(0.7)) continue;  // провал крыши
                if (x0 - 1 + k == x1 + 1 - k) b.set(x0 - 1 + k, y, z, Blocks.SPRUCE_SLAB.getDefaultState());
                else {
                    b.stairs(x0 - 1 + k, y, z, Blocks.DARK_OAK_STAIRS, Direction.EAST, false);
                    b.stairs(x1 + 1 - k, y, z, Blocks.DARK_OAK_STAIRS, Direction.WEST, false);
                }
            }
            if (k > 0) for (int x = x0 + k; x <= x1 - k; x++) {
                b.set(x, y, z0, PLANK);
                b.set(x, y, z1, PLANK);
            }
        }
        // дверь
        int dx = door == Direction.EAST ? x1 : x0;
        b.door(dx, G + 1, mz, door == Direction.EAST ? Direction.WEST : Direction.EAST, Blocks.SPRUCE_DOOR, variant == 2);
        b.set(door == Direction.EAST ? x1 + 1 : x0 - 1, G, mz, Blocks.SPRUCE_PLANKS);
        // обстановка
        int inner = door == Direction.EAST ? x0 + 1 : x1 - 1;
        b.bed(inner, G + 1, z1 - 1, door == Direction.EAST ? Direction.EAST : Direction.WEST, variant == 0 ? Blocks.RED_BED : Blocks.BROWN_BED);
        b.set(inner + (door == Direction.EAST ? 1 : -1), G + 1, z0 + 2, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(inner + (door == Direction.EAST ? 1 : -1), G + 2, z0 + 2, Blocks.SPRUCE_PRESSURE_PLATE.getDefaultState());
        b.set(inner, G + 1, z0 + 2, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(inner, G + 2, z0 + 2, Blocks.SPRUCE_PRESSURE_PLATE.getDefaultState());
        b.stairs(inner + (door == Direction.EAST ? 2 : -2), G + 1, z0 + 2, Blocks.SPRUCE_STAIRS, door == Direction.EAST ? Direction.EAST : Direction.WEST, false);
        b.set(mx, G + 1, z0 + 1, Blocks.BARREL.getDefaultState());
        b.set(mx, G + 1, mz, variant == 0 ? Blocks.RED_CARPET.getDefaultState() : Blocks.BROWN_CARPET.getDefaultState());
        b.lamp(mx, G + 3, mz, variant == 0 ? "anna" : "houses", variant == 0);
        if (variant > 0) for (int i = 0; i < 4; i++) b.set(x0 + 1 + b.rnd.nextInt(w - 2), G + 3, z0 + 1 + b.rnd.nextInt(d - 2), Blocks.COBWEB);
        if (variant == 0) b.set(mx + (door == Direction.EAST ? -2 : 2), G + 1, z1 - 3, Blocks.WHITE_CARPET);
    }

    static void brickShop(B b, int x0, int z0) {
        int x1 = x0 + 9, z1 = z0 + 10;
        b.ground(x0 - 1, z0 - 1, x1 + 1, z1 + 1, G, Blocks.GRAVEL.getDefaultState(), 10);
        b.fill(x0, G, z0, x1, G, z1, Blocks.POLISHED_ANDESITE);
        b.walls(x0, G + 1, z0, x1, G + 4, z1, Blocks.BRICKS.getDefaultState());
        b.fill(x0, G + 5, z0, x1, G + 5, z1, Blocks.STONE_BRICK_SLAB.getDefaultState());
        b.fill(x0, G + 2, z0 + 2, x0, G + 3, z0 + 3, PANE);
        b.fill(x0, G + 2, z1 - 3, x0, G + 3, z1 - 2, Blocks.AIR); // разбитая витрина
        b.door(x0, G + 1, z0 + 5, Direction.EAST, Blocks.IRON_DOOR, true);
        // прилавок и полки
        for (int z = z0 + 2; z <= z1 - 2; z++) b.set(x0 + 4, G + 1, z, Blocks.STRIPPED_OAK_LOG.getDefaultState());
        for (int z = z0 + 1; z <= z1 - 1; z++) for (int y = G + 1; y <= G + 3; y++) b.set(x1 - 1, y, z, (z + y) % 3 == 0 ? Blocks.BARREL.getDefaultState() : Blocks.SPRUCE_SLAB.getDefaultState());
        b.use(x0 + 4, G + 2, z0 + 5, GContent.CRATE, Direction.WEST, "crate_shop");
        b.use(x1 - 2, G + 1, z1 - 1, GContent.CRATE, Direction.WEST, "crate_shop2");
        b.lamp(x0 + 5, G + 4, z0 + 5, "houses", false);
        b.point("shop", x0 + 3, G + 1, z0 + 5);
    }

    static void council(B b, int x0, int z0) {
        int x1 = x0 + 11, z1 = z0 + 11;
        b.ground(x0 - 1, z0 - 1, x1 + 1, z1 + 1, G, Blocks.GRAVEL.getDefaultState(), 10);
        b.fill(x0, G, z0, x1, G, z1, PLANK);
        b.walls(x0, G + 1, z0, x1, G + 4, z1, Blocks.WHITE_TERRACOTTA.getDefaultState());
        b.fill(x0, G + 5, z0, x1, G + 5, z1, Blocks.DARK_OAK_SLAB.getDefaultState());
        for (int z = z0 + 2; z <= z1 - 2; z += 3) b.set(x0, G + 2, z, PANE);
        b.door(x0, G + 1, z0 + 5, Direction.EAST, Blocks.SPRUCE_DOOR, false);
        // перегородка: приёмная и кабинет
        b.fill(x0 + 6, G + 1, z0 + 1, x0 + 6, G + 4, z1 - 1, PLANK);
        b.door(x0 + 6, G + 1, z0 + 5, Direction.EAST, Blocks.SPRUCE_DOOR, false);
        b.use(x0 + 8, G + 2, z0 + 2, GContent.RADIO, Direction.SOUTH, "radio_council");
        b.set(x0 + 8, G + 1, z0 + 2, Blocks.SPRUCE_PLANKS);
        b.use(x0 + 3, G + 2, z0 + 2, GContent.NOTE, Direction.SOUTH, "note:council_note");
        b.set(x0 + 3, G + 1, z0 + 2, Blocks.SPRUCE_PLANKS);
        b.use(x1 - 1, G + 1, z1 - 1, GContent.CRATE, Direction.WEST, "crate_council_fuse");
        b.use(x1 - 1, G + 1, z0 + 1, Blocks.IRON_BLOCK, Direction.WEST, "crate_council_safe");
        b.set(x1 - 1, G + 2, z0 + 1, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.getDefaultState());
        b.lamp(x0 + 3, G + 4, z0 + 5, "houses", false);
        b.lamp(x0 + 9, G + 4, z0 + 5, "houses", false);
        // флаг и вывеска
        for (int y = G + 1; y <= G + 8; y++) b.set(x0 - 2, y, z0 + 2, Blocks.IRON_BARS.getDefaultState());
        b.set(x0 - 2, G + 8, z0 + 3, Blocks.RED_WOOL);
        b.point("council", x0 + 2, G + 1, z0 + 5);
    }

    static void school(B b, int x0, int z0) {
        int x1 = x0 + 15, z1 = z0 + 18;
        b.ground(x0 - 1, z0 - 1, x1 + 1, z1 + 1, G, Blocks.GRAVEL.getDefaultState(), 10);
        b.fill(x0, G, z0, x1, G, z1, Blocks.OAK_PLANKS);
        b.walls(x0, G + 1, z0, x1, G + 4, z1, Blocks.YELLOW_TERRACOTTA.getDefaultState());
        b.fill(x0, G + 5, z0, x1, G + 5, z1, Blocks.SPRUCE_SLAB.getDefaultState());
        for (int z = z0 + 2; z <= z1 - 2; z += 3) b.fill(x0, G + 2, z, x0, G + 3, z, PANE);
        b.door(x1, G + 1, z0 + 9, Direction.WEST, Blocks.SPRUCE_DOOR, false);
        b.use(x1 + 1, G + 2, z0 + 11, GContent.NOTE, Direction.EAST, "note:school_note");
        b.set(x1 + 1, G + 1, z0 + 11, Blocks.SPRUCE_FENCE.getDefaultState());
        // коридор вдоль восточной стены, два класса
        int cw = x0 + 9;
        b.fill(cw, G + 1, z0 + 1, cw, G + 4, z1 - 1, Blocks.WHITE_TERRACOTTA.getDefaultState());
        b.fill(x0 + 1, G + 1, z0 + 9, cw - 1, G + 4, z0 + 9, Blocks.WHITE_TERRACOTTA.getDefaultState());
        b.door(cw, G + 1, z0 + 4, Direction.WEST, Blocks.SPRUCE_DOOR, true);
        b.door(cw, G + 1, z0 + 14, Direction.WEST, Blocks.SPRUCE_DOOR, false);
        // парты
        for (int r = 0; r < 3; r++) for (int c = 0; c < 2; c++) {
            b.set(x0 + 2 + c * 3, G + 1, z0 + 2 + r * 2, Blocks.SPRUCE_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.TOP));
            b.set(x0 + 2 + c * 3, G + 1, z0 + 11 + r * 2, Blocks.SPRUCE_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.TOP));
        }
        b.fill(x0 + 1, G + 2, z0 + 1, x0 + 1, G + 3, z0 + 3, Blocks.BLACK_CONCRETE); // доска
        b.use(x0 + 1, G + 1, z1 - 1, GContent.CRATE, Direction.EAST, "crate_school_fuse");
        b.use(x0 + 2, G + 1, z0 + 1, GContent.CRATE, Direction.EAST, "crate_school");
        for (int i = 0; i < 10; i++) b.set(x0 + 1 + b.rnd.nextInt(14), G + 4, z0 + 1 + b.rnd.nextInt(17), Blocks.COBWEB);
        b.lamp(x0 + 12, G + 4, z0 + 5, "school", false);
        b.lamp(x0 + 12, G + 4, z0 + 13, "school", false);
        b.point("school_listener", x0 + 12, G + 1, z0 + 9);
        b.point("school", x1, G + 1, z0 + 9);
    }

    static void garage(B b, int x0, int z0) {
        int x1 = x0 + 12, z1 = z0 + 12;
        b.ground(x0 - 1, z0 - 1, x1 + 1, z1 + 1, G, Blocks.GRAVEL.getDefaultState(), 10);
        b.fill(x0, G, z0, x1, G, z1, Blocks.STONE);
        b.walls(x0, G + 1, z0, x1, G + 5, z1, Blocks.GRAY_TERRACOTTA.getDefaultState());
        b.fill(x0, G + 6, z0, x1, G + 6, z1, Blocks.IRON_BLOCK.getDefaultState());
        b.fill(x0, G + 1, z0 + 4, x0, G + 4, z0 + 8, Blocks.AIR);  // ворота открыты
        // грузовик
        b.fill(x0 + 4, G + 1, z0 + 3, x0 + 6, G + 2, z0 + 9, Blocks.BLUE_TERRACOTTA);
        b.fill(x0 + 4, G + 3, z0 + 3, x0 + 6, G + 3, z0 + 5, Blocks.BLUE_TERRACOTTA);
        b.fill(x0 + 4, G + 3, z0 + 3, x0 + 6, G + 3, z0 + 3, Blocks.GLASS_PANE);
        for (int z : new int[]{z0 + 4, z0 + 8}) {
            b.set(x0 + 3, G + 1, z, Blocks.BLACK_CONCRETE);
            b.set(x0 + 7, G + 1, z, Blocks.BLACK_CONCRETE);
        }
        b.fill(x1 - 1, G + 1, z0 + 1, x1 - 1, G + 1, z1 - 1, Blocks.SMITHING_TABLE);
        b.use(x1 - 1, G + 2, z1 - 2, GContent.CRATE, Direction.WEST, "crate_garage_fuse");
        b.use(x1 - 1, G + 2, z0 + 2, GContent.CRATE, Direction.WEST, "crate_garage");
        b.lamp(x0 + 6, G + 5, z0 + 6, "houses", false);
        b.point("garage", x0 - 1, G + 1, z0 + 6);
    }

    static void substation(B b, int x0, int z0) {
        int x1 = x0 + 10, z1 = z0 + 10;
        b.ground(x0 - 1, z0 - 1, x1 + 1, z1 + 1, G, Blocks.GRAVEL.getDefaultState(), 10);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
            if (x == x0 || x == x1 || z == z0 || z == z1) {
                b.set(x, G + 1, z, Blocks.IRON_BARS.getDefaultState());
                b.set(x, G + 2, z, Blocks.IRON_BARS.getDefaultState());
            }
        for (int[] t : new int[][]{{x0 + 3, z0 + 3}, {x0 + 7, z0 + 3}, {x0 + 5, z0 + 7}}) {
            b.fill(t[0], G + 1, t[1], t[0] + 1, G + 2, t[1] + 1, Blocks.IRON_BLOCK);
            b.set(t[0], G + 3, t[1], Blocks.LIGHTNING_ROD.getDefaultState());
            b.set(t[0] + 1, G + 3, t[1] + 1, Blocks.LIGHTNING_ROD.getDefaultState());
        }
        b.fill(x1, G + 1, z0 + 5, x1, G + 2, z0 + 5, Blocks.AIR);
        b.use(x1 + 1, G + 2, z0 + 3, GContent.FUSE_BOX, Direction.EAST, "fusebox");
        b.set(x1 + 1, G + 1, z0 + 3, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(x1 + 1, G + 3, z0 + 3, Blocks.SPRUCE_SLAB.getDefaultState());
        b.point("fusebox", x1 + 2, G + 1, z0 + 3);
        // провода на столбах к улице
        for (int y = G + 1; y <= G + 7; y++) b.set(x1 + 1, y, z0 + 8, LOG);
    }

    static void waterTower(B b, int x, int z) {
        for (int[] l : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) for (int y = G + 1; y <= G + 12; y++) b.set(x + l[0], y, z + l[1], DLOG);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz > 10) continue;
            b.set(x + dx, G + 13, z + dz, DPLANK);
            for (int y = G + 14; y <= G + 18; y++) if (dx * dx + dz * dz >= 6) b.set(x + dx, y, z + dz, Blocks.SPRUCE_PLANKS.getDefaultState());
        }
        for (int y = G + 1; y <= G + 13; y++) b.set(x, y, z + 3, Blocks.LADDER.getDefaultState().with(net.minecraft.block.LadderBlock.FACING, Direction.SOUTH));
    }

    // ================= кладбище =================
    static void cemetery(B b) {
        int x0 = -60, z0 = 58, x1 = -36, z1 = 86;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            if (x == x0 || x == x1 || z == z0 || z == z1) {
                if (z == 72 && x == x1) continue;
                if (b.chance(0.85)) b.set(x, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
            }
        }
        for (int x = x0 + 3; x < x1 - 1; x += 4) for (int z = z0 + 3; z < z1 - 1; z += 4) {
            if (b.chance(0.2)) continue;
            if (b.chance(0.5)) {  // крест
                b.set(x, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
                b.set(x, G + 2, z, Blocks.SPRUCE_FENCE.getDefaultState());
                b.set(x, G + 3, z, Blocks.SPRUCE_FENCE.getDefaultState());
            } else {
                b.set(x, G + 1, z, Blocks.MOSSY_COBBLESTONE_WALL.getDefaultState());
            }
            b.set(x, G, z + 1, Blocks.COARSE_DIRT);
            b.set(x, G, z + 2, Blocks.COARSE_DIRT);
        }
        // свежая яма — нехороший знак
        b.fill(-44, G - 1, 80, -43, G, 81, Blocks.AIR);
        b.point("cemetery", -40, G + 1, 72);
    }

    // ================= избушка охотника =================
    static void hunter(B b) {
        int x0 = -14, z0 = -96, x1 = -8, z1 = -90;
        b.ground(x0 - 1, z0 - 1, x1 + 1, z1 + 1, G, Blocks.PODZOL.getDefaultState(), 9);
        b.fill(x0, G, z0, x1, G, z1, PLANK);
        b.walls(x0, G + 1, z0, x1, G + 3, z1, LOG);
        b.fill(x0, G + 4, z0, x1, G + 4, z1, Blocks.SPRUCE_SLAB.getDefaultState());
        b.set(x0, G + 2, z0 + 3, PANE);
        b.door(x1, G + 1, z0 + 3, Direction.WEST, Blocks.SPRUCE_DOOR, false);
        b.use(x0 + 1, G + 1, z0 + 1, GContent.CRATE, Direction.EAST, "crate_hunter_kit");
        b.use(x0 + 1, G + 1, z1 - 1, GContent.CRATE, Direction.EAST, "crate_hunter_can");
        b.use(x0 + 3, G + 2, z1 - 1, GContent.NOTE, Direction.NORTH, "note:hunter_note");
        b.set(x0 + 3, G + 1, z1 - 1, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(x0 + 4, G + 1, z0 + 1, Blocks.FURNACE.getDefaultState());
        b.lantern(x0 + 3, G + 3, z0 + 3, true);
        b.bed(x0 + 1, G + 1, z0 + 3, Direction.SOUTH, Blocks.GREEN_BED);
        b.point("safe_hunter", x0 + 3, G + 1, z0 + 3);
        b.point("hunter", x1 + 1, G + 1, z0 + 3);
    }

    // ================= метеостанция «Полынь-17» =================
    static void station(B b) {
        int fx0 = -30, fz0 = -262, fx1 = 30, fz1 = -214;
        b.ground(fx0, fz0, fx1, fz1, G, Blocks.GRAVEL.getDefaultState(), 12);
        for (int x = fx0; x <= fx1; x++) for (int z = fz0; z <= fz1; z++) {
            boolean edge = x == fx0 || x == fx1 || z == fz0 || z == fz1;
            if (!edge) continue;
            if (z == fz1 && Math.abs(x) <= 2) continue;          // южные ворота (просека)
            if (x == fx0 && z >= -240 && z <= -236) continue;    // западные ворота (узкоколейка)
            b.set(x, G + 1, z, Blocks.IRON_BARS.getDefaultState());
            b.set(x, G + 2, z, Blocks.IRON_BARS.getDefaultState());
            if ((x + z) % 6 == 0) b.set(x, G + 3, z, Blocks.IRON_BARS.getDefaultState());
        }
        b.zone("station", fx0, G - 3, fz0, fx1, G + 30, fz1);
        b.point("cp5", 0, G + 1, -218);
        b.point("station_gate", 0, G + 1, -214);
        // главное здание
        int x0 = -16, z0 = -250, x1 = 4, z1 = -238;
        b.fill(x0, G, z0, x1, G, z1, Blocks.POLISHED_ANDESITE);
        b.walls(x0, G + 1, z0, x1, G + 5, z1, Blocks.WHITE_TERRACOTTA.getDefaultState());
        b.fill(x0, G + 1, z0, x1, G + 1, z0, Blocks.STONE_BRICKS);
        b.fill(x0, G + 1, z1, x1, G + 1, z1, Blocks.STONE_BRICKS);
        b.fill(x0, G + 6, z0, x1, G + 6, z1, Blocks.GRAY_CONCRETE);
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            b.fill(x, G + 3, z0, x, G + 4, z0, PANE);
            b.fill(x, G + 3, z1, x, G + 4, z1, PANE);
        }
        b.door(-6, G + 1, z1, Direction.NORTH, Blocks.IRON_DOOR, true);
        // перегородки: радиорубка (запад), кабинет Шумова (восток), коридор
        b.fill(x0 + 1, G + 1, -244, x1 - 1, G + 5, -244, Blocks.WHITE_CONCRETE);
        b.fill(-8, G + 1, z0 + 1, -8, G + 5, -245, Blocks.WHITE_CONCRETE);
        b.door(-11, G + 1, -244, Direction.NORTH, Blocks.SPRUCE_DOOR, true);
        b.door(-1, G + 1, -244, Direction.NORTH, Blocks.SPRUCE_DOOR, false);
        // радиорубка
        b.fill(-15, G + 1, -249, -9, G + 1, -249, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X));
        b.use(-14, G + 2, -249, GContent.RADIO, Direction.SOUTH, "radio_station");
        b.use(-12, G + 2, -249, GContent.NOTE, Direction.SOUTH, "note:anna_log");
        b.use(-10, G + 2, -249, GContent.TAPE_PLAYER, Direction.SOUTH, "tape_1");
        b.use(-15, G + 1, -246, GContent.TAPE_PLAYER, Direction.EAST, "tape_2");
        b.use(-9, G + 1, -246, GContent.CRATE, Direction.WEST, "crate_station");
        b.lamp(-12, G + 5, -247, "station", false);
        b.zone("radio_room", -15, G, -249, -9, G + 5, -245);
        b.point("safe_station", -12, G + 1, -246);
        b.point("anna_station", -13, G + 1, -246);
        // кабинет Шумова
        b.fill(-6, G + 1, -249, -2, G + 1, -249, Blocks.DARK_OAK_PLANKS);
        b.use(-4, G + 2, -249, GContent.NOTE, Direction.SOUTH, "note:shumov_note");
        b.use(-2, G + 2, -249, GContent.TAPE_PLAYER, Direction.SOUTH, "tape_3");
        b.use(2, G + 1, -249, Blocks.IRON_BLOCK, Direction.SOUTH, "crate_shumov");
        b.fill(3, G + 1, -248, 3, G + 3, -246, Blocks.BOOKSHELF);
        b.lamp(-3, G + 5, -247, "station", false);
        b.point("shumov", -4, G + 1, -246);
        // коридор
        b.lamp(-10, G + 5, -241, "station", false);
        b.lamp(0, G + 5, -241, "station", false);
        b.point("station_hall", -6, G + 1, -241);
        b.point("station_pole", -14, G + 1, -241);
        // генераторная
        b.fill(10, G, -232, 16, G, -226, Blocks.STONE);
        b.walls(10, G + 1, -232, 16, G + 4, -226, Blocks.GRAY_TERRACOTTA.getDefaultState());
        b.fill(10, G + 5, -232, 16, G + 5, -226, Blocks.IRON_BLOCK);
        b.fill(10, G + 1, -230, 10, G + 2, -228, Blocks.AIR);
        b.use(14, G + 1, -229, GContent.GENERATOR, Direction.WEST, "generator");
        b.lamp(13, G + 4, -229, "station", false);
        b.point("generator", 9, G + 1, -229);
        // будка-психрометр
        for (int[] l : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) b.set(-22 + l[0], G + 1, -228 + l[1], Blocks.BIRCH_FENCE.getDefaultState());
        b.fill(-22, G + 2, -228, -21, G + 3, -227, Blocks.WHITE_CONCRETE);
        // мачта
        int mx = 18, mz = -250;
        for (int y = G + 1; y <= G + 36; y++) for (int[] l : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.set(mx + l[0], y, mz + l[1], Blocks.IRON_BARS.getDefaultState());
        for (int y = G + 4; y <= G + 36; y += 4) b.fill(mx - 1, y, mz - 1, mx + 1, y, mz + 1, Blocks.IRON_BARS);
        b.set(mx, G + 37, mz, Blocks.LIGHTNING_ROD.getDefaultState());
        b.lamp(mx, G + 36, mz, "mast", false);
        // люк в бункер у подножия мачты
        b.fill(13, G - 1, -247, 15, G, -245, Blocks.STONE_BRICKS);
        b.set(14, G, -246, Blocks.IRON_TRAPDOOR.getDefaultState().with(TrapdoorBlock.OPEN, true).with(TrapdoorBlock.HALF, BlockHalf.TOP));
        b.point("hatch", 14, G + 1, -244);
        // ящики во дворе
        b.set(-24, G + 1, -220, Blocks.BARREL.getDefaultState());
        b.set(-23, G + 1, -220, Blocks.BARREL.getDefaultState());
        b.point("station_l1", -20, G + 1, -224);
        b.point("station_l2", 20, G + 1, -222);
        // рельсы узкоколейки от западных ворот к шахте
        int[] r = Terrain.ROADS[2];
        for (int i = 0; i < (r.length - 1) / 2 - 1; i++) {
            int ax = r[i * 2], az = r[i * 2 + 1], bx = r[i * 2 + 2], bz = r[i * 2 + 3];
            int n = Math.max(Math.abs(bx - ax), Math.abs(bz - az));
            boolean alongX = Math.abs(bx - ax) >= Math.abs(bz - az);
            for (int k = 0; k <= n; k++) {
                int x = ax + (bx - ax) * k / n, z = az + (bz - az) * k / n;
                if (b.chance(0.12)) continue;
                int y = Terrain.surface(x, z) + 1;
                b.set(x, y, z, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, alongX ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH));
            }
        }
    }

    // ================= бункер под мачтой =================
    static void bunker(B b) {
        int fy = G - 23; // пол
        // коридор на север к залу, потом шахта люка с лестницей
        b.fill(12, fy, -252, 16, fy + 4, -246, Blocks.STONE_BRICKS);
        b.air(13, fy + 1, -252, 15, fy + 3, -246);
        b.walls(13, fy + 4, -247, 15, G - 1, -245, Blocks.STONE_BRICKS.getDefaultState());
        b.air(14, fy + 1, -246, 14, G - 1, -246);
        for (int y = fy + 1; y < G; y++) b.set(14, y, -246, Blocks.LADDER.getDefaultState().with(net.minecraft.block.LadderBlock.FACING, Direction.NORTH));
        b.door(14, fy + 1, -252, Direction.NORTH, Blocks.IRON_DOOR, true);
        b.lamp(14, fy + 3, -249, "bunker", true);
        // зал
        int x0 = 0, z0 = -278, x1 = 28, z1 = -252;
        b.fill(x0 - 1, fy - 1, z0 - 1, x1 + 1, fy + 8, z1, Blocks.DEEPSLATE_TILES);
        b.air(x0, fy + 1, z0, x1, fy + 7, z1 - 1);
        b.fill(x0, fy, z0, x1, fy, z1 - 1, Blocks.POLISHED_DEEPSLATE);
        b.air(13, fy + 1, z1, 15, fy + 3, z1);
        b.door(14, fy + 1, z1 - 0, Direction.NORTH, Blocks.IRON_DOOR, true);
        for (int x = x0 + 5; x <= x1 - 5; x += 6) for (int z = z0 + 5; z <= z1 - 5; z += 6) {
            if (Math.abs(x - 14) < 3 && z < z0 + 8) continue;
            b.fill(x, fy + 1, z, x + 1, fy + 7, z + 1, Blocks.DEEPSLATE_BRICKS);
        }
        // трубы и кабели под потолком
        for (int x = x0; x <= x1; x += 2) b.set(x, fy + 7, z0 + 2, Blocks.IRON_BARS.getDefaultState());
        for (int z = z0; z < z1; z += 3) b.set(x0, fy + 6, z, Blocks.CHAIN.getDefaultState());
        // усилители и передатчик
        b.use(x0 + 2, fy + 1, z0 + 2, GContent.AMPLIFIER, Direction.SOUTH, "amp_1");
        b.use(x1 - 2, fy + 1, z0 + 2, GContent.AMPLIFIER, Direction.SOUTH, "amp_2");
        b.use(x0 + 2, fy + 1, z1 - 3, GContent.AMPLIFIER, Direction.EAST, "amp_3");
        b.fill(12, fy + 1, z0, 16, fy + 1, z0 + 3, Blocks.POLISHED_DEEPSLATE);
        b.use(14, fy + 2, z0 + 1, GContent.TRANSMITTER, Direction.SOUTH, "transmitter");
        b.set(13, fy + 2, z0 + 1, Blocks.IRON_BLOCK);
        b.set(15, fy + 2, z0 + 1, Blocks.IRON_BLOCK);
        for (int x = x0 + 4; x <= x1 - 4; x += 8) for (int z = z0 + 4; z <= z1 - 4; z += 8) b.lamp(x, fy + 7, z, "bunker", b.chance(0.4));
        b.use(x1 - 2, fy + 1, z1 - 3, GContent.CRATE, Direction.WEST, "crate_bunker");
        b.zone("bunker", x0 - 2, fy - 2, z0 - 2, x1 + 2, fy + 9, -244);
        b.point("bunker_in", 14, fy + 1, -250);
        b.point("choir_bunker", 14, fy + 1, -268);
        b.point("amp_1", x0 + 2, fy + 1, z0 + 3);
        b.point("amp_2", x1 - 2, fy + 1, z0 + 3);
        b.point("amp_3", x0 + 3, fy + 1, z1 - 3);
        b.point("transmitter", 14, fy + 1, z0 + 4);
    }

    // ================= шахта «Глубокая» =================
    static final int MY = 24; // пол штолен

    static void mine(B b) {
        int x0 = -142, z0 = -182, x1 = -120, z1 = -164;
        b.ground(x0 - 2, z0 - 2, x1 + 2, z1 + 2, G, Blocks.GRAVEL.getDefaultState(), 30);
        // здание над стволом
        b.fill(x0, G, z0, x1, G, z1, Blocks.STONE);
        b.walls(x0, G + 1, z0, x1, G + 6, z1, Blocks.DARK_OAK_PLANKS.getDefaultState());
        for (int y = G + 1; y <= G + 6; y++) for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) b.set(c[0], y, c[1], DLOG);
        b.fill(x0, G + 7, z0, x1, G + 7, z1, Blocks.DARK_OAK_SLAB.getDefaultState());
        b.fill(x1, G + 1, -175, x1, G + 4, -171, Blocks.AIR);  // ворота с востока
        for (int z = z0 + 3; z < z1; z += 4) b.set(x0, G + 3, z, PANE);
        // копёр
        int sx = -134, sz = -173;
        for (int y = G + 8; y <= G + 26; y++) {
            b.set(sx - 2, y, sz - 2, DLOG);
            b.set(sx + 2, y, sz - 2, DLOG);
            b.set(sx - 2, y, sz + 2, DLOG);
            b.set(sx + 2, y, sz + 2, DLOG);
        }
        b.fill(sx - 2, G + 26, sz - 2, sx + 2, G + 26, sz + 2, DPLANK);
        for (int d = -2; d <= 2; d++) {
            b.set(sx + d, G + 27, sz, Blocks.IRON_BARS.getDefaultState());
            b.set(sx, G + 27 + Math.abs(d) * 0 + (2 - Math.abs(d)), sz, Blocks.IRON_BARS.getDefaultState());
        }
        // ствол и клеть
        b.walls(sx - 2, MY, sz - 2, sx + 1, G - 1, sz + 1, Blocks.STONE_BRICKS.getDefaultState());
        b.air(sx - 1, MY + 1, sz - 1, sx, G - 1, sz);
        b.fill(sx - 1, G, sz - 1, sx, G, sz, Blocks.IRON_TRAPDOOR.getDefaultState());
        b.fill(sx - 1, MY, sz - 1, sx, MY, sz, Blocks.SPRUCE_PLANKS);
        b.set(sx + 1, G + 1, sz + 2, Blocks.STONE_BRICKS);
        b.use(sx + 1, G + 2, sz + 2, GContent.FUSE_BOX, Direction.SOUTH, "lift_top");
        b.lamp(sx + 4, G + 6, sz, "mine_top", true);
        b.zone("mine_top", x0 - 4, G - 3, z0 - 4, x1 + 8, G + 20, z1 + 4);
        b.point("lift_top", sx + 2, G + 1, sz + 1);
        b.point("cp4top", sx + 4, G + 1, sz + 1);
        b.use(-126, G + 1, -166, GContent.CRATE, Direction.NORTH, "crate_minetop");

        // штольни
        tunnel(b, sx + 1, sz - 1, -80, sz + 1, true);             // главная на восток
        tunnel(b, -111, -215, -109, sz - 2, false);              // ветка А на север
        tunnel(b, -96, sz + 2, -94, -140, false);                // ветка Б на юг
        tunnel(b, -140, -202, -112, -200, true);                 // тупик на запад
        b.use(sx + 1, MY + 2, sz + 2, GContent.FUSE_BOX, Direction.SOUTH, "lift_bottom");
        b.set(sx + 1, MY + 1, sz + 2, Blocks.STONE_BRICKS);
        b.point("lift_bottom", sx + 2, MY + 1, sz);
        b.point("cp4", sx + 3, MY + 1, sz);
        b.zone("mine", -145, MY - 3, -232, -75, MY + 8, -135);
        // метки Анны
        b.use(-118, MY + 2, sz - 2, GContent.CHALK_MARK, Direction.SOUTH, "mark_1");
        b.use(-108, MY + 2, -192, GContent.CHALK_MARK, Direction.WEST, "mark_2");
        b.use(-108, MY + 2, -208, GContent.CHALK_MARK, Direction.WEST, "mark_3");
        b.point("mark_1", -118, MY + 1, sz);
        b.point("mark_2", -110, MY + 1, -192);
        b.point("mark_3", -110, MY + 1, -208);
        // убежище № 3
        b.fill(-117, MY, -228, -103, MY + 5, -215, Blocks.STONE_BRICKS);
        b.air(-116, MY + 1, -227, -104, MY + 4, -216);
        b.fill(-116, MY, -227, -104, MY, -216, Blocks.SPRUCE_PLANKS);
        b.door(-110, MY + 1, -215, Direction.SOUTH, Blocks.IRON_DOOR, false);
        b.point("shelter_door", -110, MY + 1, -215);
        b.bed(-115, MY + 1, -226, Direction.SOUTH, Blocks.WHITE_BED);
        for (int x = -114; x <= -106; x += 4) b.set(x, MY + 1, -226, Blocks.CANDLE.getDefaultState().with(net.minecraft.block.CandleBlock.LIT, true));
        b.lantern(-110, MY + 4, -221, true);
        b.use(-105, MY + 1, -226, GContent.CRATE, Direction.WEST, "crate_shelter");
        b.point("anna_mine", -110, MY + 1, -222);
        b.point("safe_shelter", -110, MY + 1, -221);
        b.zone("shelter", -117, MY, -228, -103, MY + 5, -215);
        // существа и находки
        b.point("mine_l1", -100, MY + 1, sz);
        b.point("mine_l2", -110, MY + 1, -200);
        b.point("mine_l3", -95, MY + 1, -150);
        b.point("choir_mine", -84, MY + 1, sz);
        b.use(-95, MY + 1, -141, GContent.CRATE, Direction.NORTH, "crate_mine");
        b.use(-139, MY + 1, -201, GContent.CRATE, Direction.EAST, "crate_mine2");
    }

    /** Штольня шириной 3, высотой 3, с крепью через 5 блоков. alongX — ось. */
    static void tunnel(B b, int xa, int za, int xb, int zb, boolean alongX) {
        int x0 = Math.min(xa, xb), x1 = Math.max(xa, xb), z0 = Math.min(za, zb), z1 = Math.max(za, zb);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            b.set(x, MY, z, b.chance(0.15) ? Blocks.GRAVEL.getDefaultState() : Blocks.STONE.getDefaultState());
            for (int y = MY + 1; y <= MY + 3; y++) b.set(x, y, z, AIR);
            b.set(x, MY + 4, z, Blocks.STONE.getDefaultState());
        }
        int len = alongX ? x1 - x0 : z1 - z0;
        for (int i = 0; i <= len; i += 5) {
            int x = alongX ? x0 + i : x0, z = alongX ? z0 : z0 + i;
            if (alongX) {
                for (int y = MY + 1; y <= MY + 2; y++) {
                    b.set(x, y, z0, LOG);
                    b.set(x, y, z1, LOG);
                }
                b.fill(x, MY + 3, z0, x, MY + 3, z1, PLANK);
            } else {
                for (int y = MY + 1; y <= MY + 2; y++) {
                    b.set(x0, y, z, LOG);
                    b.set(x1, y, z, LOG);
                }
                b.fill(x0, MY + 3, z, x1, MY + 3, z, PLANK);
            }
            if (b.chance(0.25)) b.set(alongX ? x : (x0 + x1) / 2, MY + 3, alongX ? (z0 + z1) / 2 : z,
                    Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true));
            if (b.chance(0.3)) b.set(alongX ? x + 2 : x0 + 1, MY + 3, alongX ? z0 + 1 : z + 2, Blocks.COBWEB);
        }
        if (alongX) for (int x = x0; x <= x1; x++) if (b.chance(0.85))
            b.set(x, MY + 1, (z0 + z1) / 2, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
    }
}
