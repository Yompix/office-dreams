package com.yompix.glukhoman.world;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.block.DecorBlock;
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
 * «Порожняк» (0.4): строит мир один раз при первом запуске — мёртвую узкоколейку «Ветка 41» на насыпи (рельсы до 62-го метра,
 * дальше голые шпалы и босые следы в лес), станцию Еловый (перрон, вокзальчик с дежуркой и табло рельсовых цепей, ящик для кассет,
 * громкоговоритель, колокол), переезд со звонком и красными огнями, две стрелки и обгонный путь с пустым вагоном,
 * и посёлок (дома бабы Поли, Петровича и Зины, Вики). Регистрирует точки и интерактивные вещи в StoryState.
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

    public static final int LZ = 16;   // ось главного пути
    public static final int LOOPZ = 10; // ось обгонного пути
    public static final int RAIL_END = 62;

    public static void build(ServerWorld world, StoryState st) {
        long t0 = System.currentTimeMillis();
        for (int[] s : Terrain.SITES)
            for (int x = s[0] - s[2] - 16; x <= s[0] + s[2] + 16; x += 16)
                for (int z = s[1] - s[3] - 16; z <= s[1] + s[3] + 16; z += 16) world.getChunk(x >> 4, z >> 4);
        for (int x = -96; x <= 430; x += 16) for (int z = 0; z <= 32; z += 16) world.getChunk(x >> 4, z >> 4);
        B b = new B(world, st);
        line(b);
        station(b);
        crossing(b);
        village(b);
        cemetery(b);
        houses(b);
        st.set("v2");
        st.built = true;
        st.markDirty();
        Glukhoman.LOG.info("Глухомань: карта построена за {} мс, точек {}, вещей {}", System.currentTimeMillis() - t0, st.points.size(), st.interact.size());
    }

    // ================= линия: насыпь, шпалы, рельсы, обгонный путь, тупик =================
    static void line(B b) {
        for (int x = -70; x <= 420; x++) {
            for (int z = LZ - 2; z <= LZ + 2; z++) {
                int top = Terrain.surface(x, z);
                for (int y = top + 1; y <= G; y++) b.set(x, y, z, Blocks.GRAVEL);       // насыпь не проваливается в низинах
                boolean bed = Math.abs(z - LZ) <= 1;
                b.set(x, G + 1, z, bed ? Blocks.GRAVEL.getDefaultState() : (b.chance(0.5) ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.GRAVEL.getDefaultState()));
                for (int y = G + 2; y <= G + 5; y++) if (!b.get(x, y, z).isAir() && x > 60) b.set(x, y, z, Blocks.AIR);
                // голые шпалы там, где рельсы срезаны: тонкие доски поперёк пути через блок, часть сгнила или пропала
                if (bed && x > RAIL_END && (x & 1) == 0 && !b.chance(0.08))
                    b.set(x, G + 2, z, (b.chance(0.25) ? Blocks.DARK_OAK_TRAPDOOR : Blocks.SPRUCE_TRAPDOOR).getDefaultState()
                            .with(TrapdoorBlock.HALF, BlockHalf.BOTTOM).with(TrapdoorBlock.FACING, Direction.NORTH));
            }
            if (x >= -66 && x <= RAIL_END && !(x > RAIL_END - 3 && b.chance(0.5)))
                b.set(x, G + 2, LZ, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
        }
        // обрезки рельсов у обрыва и босые следы на шпалах — много, все в лес
        b.set(RAIL_END + 2, G + 2, LZ + 2, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
        for (int x = RAIL_END + 1; x <= 118; x++) for (int z = LZ - 1; z <= LZ + 1; z++)
            if ((x & 1) == 1 && b.chance(0.45)) b.set(x, G + 2, z, GContent.FOOTPRINTS.getDefaultState().with(DecorBlock.VARIANT, b.rnd.nextInt(3))
                    .with(net.minecraft.block.HorizontalFacingBlock.FACING, Direction.EAST));
        // пикеты: столбики с цифрами (дальше в тумане — «58»)
        for (int[] pk : new int[][]{{96, 57}, {124, 58}, {152, 59}}) {
            int x = pk[0];
            b.set(x, G + 1, LZ + 3, Blocks.GRAVEL);
            b.set(x, G + 2, LZ + 3, Blocks.STRIPPED_SPRUCE_LOG);
            b.set(x, G + 3, LZ + 3, Blocks.SPRUCE_SIGN.getDefaultState().with(net.minecraft.block.SignBlock.ROTATION, 4));
            if (b.w.getBlockEntity(new net.minecraft.util.math.BlockPos(x, G + 3, LZ + 3)) instanceof net.minecraft.block.entity.SignBlockEntity sb) {
                net.minecraft.text.Text n = net.minecraft.text.Text.literal(Integer.toString(pk[1]));
                sb.setText(new net.minecraft.block.entity.SignText().withMessage(1, n), true);
                sb.setText(new net.minecraft.block.entity.SignText().withMessage(1, n), false);
            }
        }
        // тупик: упор
        b.set(-67, G + 2, LZ, Blocks.RED_TERRACOTTA);
        b.set(-67, G + 3, LZ, Blocks.SPRUCE_FENCE);
        b.set(-67, G + 2, LZ - 1, Blocks.SPRUCE_FENCE);
        b.set(-67, G + 2, LZ + 1, Blocks.SPRUCE_FENCE);
        // обгонный путь: x -24…62, у восточного края — пустой вагон в тупичке
        for (int x = -26; x <= 64; x++) for (int z = LOOPZ - 1; z <= LOOPZ + 1; z++) {
            int top = Terrain.surface(x, z);
            for (int y = top + 1; y <= G; y++) b.set(x, y, z, Blocks.GRAVEL);
            b.set(x, G + 1, z, Blocks.GRAVEL.getDefaultState());
            if (z == LOOPZ && x >= -20 && x <= 62) b.set(x, G + 2, z, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
        }
        for (int i = 0; i < 5; i++) {   // съезды к главному пути (без рельсов — гравий)
            b.set(-21 - i, G + 1, LOOPZ + 1 + i, Blocks.GRAVEL);
            b.set(47 + i, G + 1, LOOPZ + 1 + i, Blocks.GRAVEL);
        }
        wagon(b, 52, LOOPZ);
        b.zone("wagon", 52, G + 1, LOOPZ - 1, 60, G + 6, LOOPZ + 1);
    }

    /** Пустая теплушка на тупичке: щели в досках, сдвинутая дверь. Прятаться в ней — нарушить правило. */
    static void wagon(B b, int x0, int z) {
        for (int x = x0; x <= x0 + 8; x++) for (int dz = -1; dz <= 1; dz++) {
            b.set(x, G + 3, z + dz, Blocks.DARK_OAK_PLANKS);
            b.set(x, G + 7, z + dz, Blocks.DARK_OAK_SLAB);
            for (int y = G + 4; y <= G + 6; y++) {
                boolean wall = Math.abs(dz) == 1 || x == x0 || x == x0 + 8;
                boolean door = dz == 1 && x >= x0 + 3 && x <= x0 + 5 && y <= G + 5;
                boolean gap = wall && b.chance(0.08);
                b.set(x, y, z + dz, wall && !door && !gap ? Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState() : AIR);
            }
        }
        for (int x : new int[]{x0 + 1, x0 + 7}) for (int dz : new int[]{-1, 1}) b.set(x, G + 2, z + dz, Blocks.POLISHED_BLACKSTONE);
        b.set(x0 + 4, G + 3, z + 2, Blocks.DARK_OAK_STAIRS.getDefaultState());
    }

    // ================= станция Еловый =================
    static void station(B b) {
        // перрон: x -12…22, z 18…20, на уровне насыпи
        for (int x = -12; x <= 22; x++) for (int z = 18; z <= 20; z++) {
            int top = Terrain.surface(x, z);
            for (int y = top + 1; y <= G; y++) b.set(x, y, z, Blocks.STONE);
            b.set(x, G + 1, z, z == 18 ? Blocks.STONE_BRICK_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.TOP)
                    : b.chance(0.25) ? Blocks.CRACKED_STONE_BRICKS.getDefaultState() : Blocks.STONE_BRICKS.getDefaultState());
            for (int y = G + 2; y <= G + 6; y++) b.set(x, y, z, AIR);
        }
        for (int z = 18; z <= 20; z++) {   // спуски с перрона
            b.stairs(-13, G + 1, z, Blocks.STONE_BRICK_STAIRS, Direction.EAST, false);
            b.stairs(23, G + 1, z, Blocks.STONE_BRICK_STAIRS, Direction.WEST, false);
        }
        b.zone("platform", -12, G + 1, 17, 22, G + 6, 21);
        b.point("start", 0, G + 2, 19);
        b.point("cp_platform", 0, G + 2, 19);
        b.point("t_platform", 4, G + 2, 19);
        b.point("board_pt", 6, G + 2, 18);
        b.point("w_post", -11, G + 2, 19);
        // вокзальчик: x -10…6, z 21…29; дежурка на западе, зал на востоке
        int x0 = -10, z0 = 21, x1 = 6, z1 = 29;
        b.ground(x0 - 1, z0, x1 + 1, z1 + 1, G, Blocks.COARSE_DIRT.getDefaultState(), 8);
        b.fill(x0, G + 1, z0, x1, G + 1, z1, PLANK);
        b.walls(x0, G + 2, z0, x1, G + 4, z1, WALL);
        for (int y = G + 2; y <= G + 4; y++) for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) b.set(c[0], y, c[1], LOG);
        b.fill(x0 - 1, G + 5, z0 - 1, x1 + 1, G + 5, z1 + 1, Blocks.DARK_OAK_SLAB);
        b.fill(x0, G + 5, z0, x1, G + 5, z1, DPLANK);
        b.fill(x0 + 2, G + 6, z0 + 2, x1 - 2, G + 6, z1 - 2, Blocks.DARK_OAK_SLAB);
        b.fill(-3, G + 2, z0 + 1, -3, G + 4, z1 - 1, WALL);                    // перегородка
        b.door(-3, G + 2, 25, Direction.EAST, Blocks.SPRUCE_DOOR, false);
        b.door(-6, G + 2, z0, Direction.SOUTH, Blocks.SPRUCE_DOOR, false);   // дежурка → перрон
        b.door(2, G + 2, z0, Direction.SOUTH, Blocks.SPRUCE_DOOR, false);    // зал → перрон
        b.door(2, G + 2, z1, Direction.NORTH, Blocks.SPRUCE_DOOR, false);    // зал → посёлок
        for (int x : new int[]{-8, 4}) b.set(x, G + 3, z0, PANE);
        b.set(-10, G + 3, 26, PANE);
        b.set(6, G + 3, 25, PANE);
        // дежурка: табло на западной стене, рация, стул, лампа
        int i = 0;
        for (int row = 1; row >= 0; row--) for (int z = 23; z <= 27; z++) {
            b.set(-9, G + 3 + row, z, GContent.BOARD_LAMP.getDefaultState().with(LampBlock.LIT, false));
            b.st.lamp("board", new net.minecraft.util.math.BlockPos(-9, G + 3 + row, z));
            b.st.bind(new net.minecraft.util.math.BlockPos(-9, G + 3 + row, z), "board");
            i++;
        }
        b.set(-9, G + 2, 23, Blocks.DARK_OAK_PLANKS);
        b.set(-9, G + 2, 27, Blocks.DARK_OAK_PLANKS);
        b.fill(-9, G + 2, 24, -9, G + 2, 26, Blocks.DARK_OAK_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.TOP));
        b.set(-8, G + 2, 28, Blocks.SPRUCE_PLANKS);
        b.use(-8, G + 3, 28, GContent.RADIO, Direction.NORTH, "radio");
        b.stairs(-6, G + 2, 26, Blocks.SPRUCE_STAIRS, Direction.SOUTH, false);
        b.st.bind(new net.minecraft.util.math.BlockPos(-6, G + 2, 26), "wait");
        b.lamp(-6, G + 4, 25, "dispatch", true);
        b.zone("dispatch", -9, G + 1, 22, -4, G + 5, 28);
        b.point("cp_dispatch", -6, G + 2, 24);
        b.point("zakhar_day", -5, G + 2, 23);
        // зал: лавки
        for (int x = 0; x <= 4; x += 2) b.stairs(x, G + 2, 28, Blocks.SPRUCE_STAIRS, Direction.NORTH, false);
        b.lamp(2, G + 4, 25, "station", false);
        // ящик для кассет у стены на перроне, громкоговоритель на столбе, колокол
        b.use(4, G + 2, 20, GContent.BOX, Direction.NORTH, "box");
        for (int y = G + 2; y <= G + 5; y++) b.set(14, y, 20, Blocks.SPRUCE_FENCE);
        b.prop(14, G + 6, 20, GContent.LOUDSPEAKER, Direction.NORTH);
        b.point("loudspeaker", 14, G + 6, 20);
        b.set(8, G + 2, 20, Blocks.BELL.getDefaultState().with(net.minecraft.block.BellBlock.ATTACHMENT, net.minecraft.block.enums.Attachment.FLOOR)
                .with(net.minecraft.block.BellBlock.FACING, Direction.EAST));
        b.st.bind(new net.minecraft.util.math.BlockPos(8, G + 2, 20), "bell");
        b.point("bell", 8, G + 2, 20);
        b.stairs(-1, G + 2, 20, Blocks.SPRUCE_STAIRS, Direction.SOUTH, false);  // лавка
        b.stairs(0, G + 2, 20, Blocks.SPRUCE_STAIRS, Direction.SOUTH, false);
        b.point("vika_day", -1, G + 2, 19);
        // фонари перрона (тусклые) и вывеска
        for (int x : new int[]{-8, 18}) {
            for (int y = G + 2; y <= G + 4; y++) b.set(x, y, 20, Blocks.SPRUCE_FENCE);
            b.set(x, G + 5, 20, GContent.STREET_LAMP.getDefaultState().with(LampBlock.LIT, false));
            b.st.lamp("station", new net.minecraft.util.math.BlockPos(x, G + 5, 20));
        }
        // стрелки: рычаги у пути
        b.set(-20, G + 1, 18, Blocks.COBBLESTONE);
        b.set(-20, G + 2, 18, Blocks.LEVER.getDefaultState().with(net.minecraft.block.LeverBlock.FACE, net.minecraft.block.enums.BlockFace.FLOOR)
                .with(net.minecraft.block.LeverBlock.FACING, Direction.EAST));
        b.st.bind(new net.minecraft.util.math.BlockPos(-20, G + 2, 18), "switch_w");
        b.point("t_switch", -20, G + 2, 17);
        b.set(46, G + 1, 18, Blocks.COBBLESTONE);
        b.set(46, G + 2, 18, Blocks.LEVER.getDefaultState().with(net.minecraft.block.LeverBlock.FACE, net.minecraft.block.enums.BlockFace.FLOOR)
                .with(net.minecraft.block.LeverBlock.FACING, Direction.EAST));
        b.st.bind(new net.minecraft.util.math.BlockPos(46, G + 2, 18), "switch_e");
        b.point("switch_e", 46, G + 2, 18);
        // водокачка у тупика
        waterTower(b, -34, 24);
    }

    // ================= переезд: дорога, огни, звонок, лавка =================
    static void crossing(B b) {
        for (int z = 12; z <= 20; z++) for (int x = 30; x <= 32; x++) b.set(x, G + 1, z, z >= 15 && z <= 17 ? Blocks.SPRUCE_PLANKS.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState());
        for (int[] p : new int[][]{{28, 19}, {34, 13}}) {
            for (int y = G + 1; y <= G + 3; y++) b.set(p[0], y, p[1], Blocks.IRON_BARS);
            b.set(p[0], G + 4, p[1], GContent.SIGNAL.getDefaultState().with(LampBlock.LIT, false));
            b.st.lamp("signal", new net.minecraft.util.math.BlockPos(p[0], G + 4, p[1]));
        }
        b.point("crossing", 29, G + 4, 19);
        b.point("t_crossing", 31, G + 2, 16);
        b.stairs(35, G + 1, 21, Blocks.SPRUCE_STAIRS, Direction.SOUTH, false);
        b.stairs(36, G + 1, 21, Blocks.SPRUCE_STAIRS, Direction.SOUTH, false);
        b.point("petrovich_day", 35, G + 1, 21);
        b.zone("east", RAIL_END - 4, G - 10, -20, 460, G + 30, 50);
    }

    // ================= посёлок (старые улицы 0.3 без сюжетных вещей) =================
    static void village(B b) {
        for (int z = 40; z <= 160; z += 16) streetLamp(b, 3, z);
        for (int z = 34; z <= 165; z++) {
            if (b.chance(0.82) && Math.abs(z - 72) > 2) b.set(-28, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
            if (b.chance(0.82)) b.set(28, G + 1, z, Blocks.SPRUCE_FENCE.getDefaultState());
        }
        b.zone("village", -30, G - 5, 30, 30, G + 20, 168);
        izba(b, -17, 124, 9, 9, Direction.EAST, 0);   // Вика с бабушкой
        b.point("vika_home", -13, G + 1, 128);
        b.point("vika_door", -7, G + 1, 128);
        brickShop(b, 9, 138);
        izba(b, -17, 110, 9, 9, Direction.EAST, 1);
        izba(b, 9, 110, 9, 9, Direction.WEST, 2);
        izba(b, -17, 96, 9, 9, Direction.EAST, 2);
        izba(b, -17, 140, 9, 9, Direction.EAST, 1);
        izba(b, 9, 124, 9, 9, Direction.WEST, 1);
        council(b, 9, 92);
        school(b, -24, 72);
        garage(b, 9, 62);
        substation(b, -20, 52);
        waterTower(b, 22, 52);
    }

    /** Дома у станции: баба Поля (окно на перрон), Петрович с Зиной. */
    static void houses(B b) {
        izba(b, 14, 36, 9, 9, Direction.WEST, 0);
        b.point("polya_window", 18, G + 1, 37);
        b.point("polya_door", 14, G + 1, 40);
        b.zone("polya_yard", 6, G - 2, 30, 26, G + 8, 47);
        b.point("zina_day", 10, G + 1, 34);
        izba(b, -22, 36, 9, 9, Direction.EAST, 0);
        b.point("petrovich_home", -18, G + 1, 40);
        b.point("zina_home", -19, G + 1, 42);
        b.point("petrovich_door", -12, G + 1, 40);
        // фото бабы Поли и расписание её рукой — на стене (видно в окно)
        b.prop(21, G + 3, 39, GContent.PHOTO, Direction.WEST);
        b.st.lamp("photo", new net.minecraft.util.math.BlockPos(21, G + 3, 39));
    }

    /** Утро после пролога: дом бабы Поли заколочен «лет пять как» — серые доски, паутина, свет не горит. */
    public static void boardUp(ServerWorld world, StoryState st) {
        B b = new B(world, st);
        BlockState board = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Z);
        b.set(14, G + 1, 40, board);
        b.set(14, G + 2, 40, board);
        b.set(14, G + 3, 39, Blocks.SPRUCE_SLAB);
        for (int[] w : new int[][]{{18, 36}, {14, 38}, {14, 42}, {22, 38}, {22, 42}, {18, 44}})
            b.set(w[0], G + 2, w[1], Blocks.DARK_OAK_PLANKS);
        b.set(18, G + 3, 40, Blocks.COBWEB);
        b.set(16, G + 3, 42, Blocks.COBWEB);
        b.set(21, G + 3, 39, AIR);
        for (var e : st.lamps.entrySet()) e.getValue().removeIf(l -> {
            var q = net.minecraft.util.math.BlockPos.fromLong(l);
            if (q.getX() >= 14 && q.getX() <= 22 && q.getZ() >= 36 && q.getZ() <= 44) {
                world.setBlockState(q, AIR, 3);
                return true;
            }
            return false;
        });
        st.markDirty();
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
        b.prop(x0 + 4, G + 2, z0 + 5, GContent.CRATE, Direction.WEST);
        b.prop(x1 - 2, G + 1, z1 - 1, GContent.CRATE, Direction.WEST);
        b.lamp(x0 + 5, G + 4, z0 + 5, "houses", false);
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
        b.prop(x0 + 8, G + 2, z0 + 2, GContent.RADIO, Direction.SOUTH);
        b.set(x0 + 8, G + 1, z0 + 2, Blocks.SPRUCE_PLANKS);
        b.prop(x0 + 3, G + 2, z0 + 2, GContent.NOTE, Direction.SOUTH);
        b.set(x0 + 3, G + 1, z0 + 2, Blocks.SPRUCE_PLANKS);
        b.prop(x1 - 1, G + 1, z1 - 1, GContent.CRATE, Direction.WEST);
        b.prop(x1 - 1, G + 1, z0 + 1, Blocks.IRON_BLOCK, Direction.WEST);
        b.set(x1 - 1, G + 2, z0 + 1, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.getDefaultState());
        b.lamp(x0 + 3, G + 4, z0 + 5, "houses", false);
        b.lamp(x0 + 9, G + 4, z0 + 5, "houses", false);
        // флаг и вывеска
        for (int y = G + 1; y <= G + 8; y++) b.set(x0 - 2, y, z0 + 2, Blocks.IRON_BARS.getDefaultState());
        b.set(x0 - 2, G + 8, z0 + 3, Blocks.RED_WOOL);
    }

    static void school(B b, int x0, int z0) {
        int x1 = x0 + 15, z1 = z0 + 18;
        b.ground(x0 - 1, z0 - 1, x1 + 1, z1 + 1, G, Blocks.GRAVEL.getDefaultState(), 10);
        b.fill(x0, G, z0, x1, G, z1, Blocks.OAK_PLANKS);
        b.walls(x0, G + 1, z0, x1, G + 4, z1, Blocks.YELLOW_TERRACOTTA.getDefaultState());
        b.fill(x0, G + 5, z0, x1, G + 5, z1, Blocks.SPRUCE_SLAB.getDefaultState());
        for (int z = z0 + 2; z <= z1 - 2; z += 3) b.fill(x0, G + 2, z, x0, G + 3, z, PANE);
        b.door(x1, G + 1, z0 + 9, Direction.WEST, Blocks.SPRUCE_DOOR, false);
        b.prop(x1 + 1, G + 2, z0 + 11, GContent.NOTE, Direction.EAST);
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
        b.prop(x0 + 1, G + 1, z1 - 1, GContent.CRATE, Direction.EAST);
        b.prop(x0 + 2, G + 1, z0 + 1, GContent.CRATE, Direction.EAST);
        for (int i = 0; i < 10; i++) b.set(x0 + 1 + b.rnd.nextInt(14), G + 4, z0 + 1 + b.rnd.nextInt(17), Blocks.COBWEB);
        b.lamp(x0 + 12, G + 4, z0 + 5, "school", false);
        b.lamp(x0 + 12, G + 4, z0 + 13, "school", false);
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
        b.prop(x1 - 1, G + 2, z1 - 2, GContent.CRATE, Direction.WEST);
        b.prop(x1 - 1, G + 2, z0 + 2, GContent.CRATE, Direction.WEST);
        b.lamp(x0 + 6, G + 5, z0 + 6, "houses", false);
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
        b.prop(x1 + 1, G + 2, z0 + 3, GContent.FUSE_BOX, Direction.EAST);
        b.set(x1 + 1, G + 1, z0 + 3, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(x1 + 1, G + 3, z0 + 3, Blocks.SPRUCE_SLAB.getDefaultState());
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

}
