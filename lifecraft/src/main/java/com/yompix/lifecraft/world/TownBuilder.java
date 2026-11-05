package com.yompix.lifecraft.world;

import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.entity.CarEntity;
import com.yompix.lifecraft.entity.CitizenEntity;
import com.yompix.lifecraft.life.Names;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

/**
 * Строит город 156×156: сетка дорог 4×4 со светофорами и фонарями, а между ними 9 кварталов:
 * дома, парк, магазин, школа (в центре), офис, автосалон, казино с банком.
 */
public final class TownBuilder {
    private TownBuilder() {}

    public static final int[] ROADS = {-72, -24, 24, 72};
    public static final RegistryKey<Biome> CITY_BIOME = RegistryKey.of(RegistryKeys.BIOME, LifeCraft.id("city"));
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final int EDGE = 78;

    /** Новый мир с типом «LifeCraft: город» — строим город один раз при первом запуске. */
    public static void autoBuildIfCityWorld(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        TownState state = TownState.get(world);
        if (state.built) return;
        BlockPos spawn = world.getSpawnPos();
        if (!world.getBiome(spawn).matchesKey(CITY_BIOME)) return;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        LifeCraft.LOG.info("Мир LifeCraft: строю город на y={}", y);
        build(world, new BlockPos(0, y, 0), false);
    }

    public static void build(ServerWorld world, BlockPos origin, boolean flatten) {
        long t0 = System.currentTimeMillis();
        TownState state = TownState.get(world);
        state.origin = origin;
        state.mailboxes.clear();
        B b = new B(world, origin.getX(), origin.getY(), origin.getZ(), BlockRotation.NONE);
        if (flatten) {
            b.fill(-EDGE - 2, -4, -EDGE - 2, EDGE + 2, -2, EDGE + 2, Blocks.DIRT.getDefaultState());
            b.fill(-EDGE - 2, -1, -EDGE - 2, EDGE + 2, -1, EDGE + 2, Blocks.GRASS_BLOCK.getDefaultState());
            b.fill(-EDGE - 2, 0, -EDGE - 2, EDGE + 2, 30, EDGE + 2, Blocks.AIR.getDefaultState());
        }
        roads(b);
        // кварталы: центр квартала и поворот (фасад к ближайшей дороге)
        int[] centers = {-48, 0, 48};
        plot(world, origin, centers[0], centers[0], BlockRotation.NONE, TownBuilder::houses);
        plot(world, origin, centers[1], centers[0], BlockRotation.NONE, TownBuilder::park);
        plot(world, origin, centers[2], centers[0], BlockRotation.NONE, TownBuilder::houses);
        plot(world, origin, centers[0], centers[1], BlockRotation.NONE, TownBuilder::shop);
        plot(world, origin, centers[1], centers[1], BlockRotation.NONE, TownBuilder::school);
        plot(world, origin, centers[2], centers[1], BlockRotation.NONE, TownBuilder::office);
        plot(world, origin, centers[0], centers[2], BlockRotation.CLOCKWISE_180, TownBuilder::dealership);
        plot(world, origin, centers[1], centers[2], BlockRotation.CLOCKWISE_180, TownBuilder::casino);
        plot(world, origin, centers[2], centers[2], BlockRotation.CLOCKWISE_180, TownBuilder::houses);
        streetLife(b);

        state.school = origin.add(0, 0, 5);
        state.built = true;
        state.markDirty();
        world.setSpawnPos(origin.add(0, 0, 9), 180);
        GameRules rules = world.getGameRules();
        rules.get(GameRules.SPAWN_RADIUS).set(0, world.getServer());
        rules.get(GameRules.DO_INSOMNIA).set(false, world.getServer());
        rules.get(GameRules.DO_PATROL_SPAWNING).set(false, world.getServer());
        rules.get(GameRules.DO_TRADER_SPAWNING).set(false, world.getServer());
        world.setTimeOfDay(0);
        LifeCraft.LOG.info("Город построен за {} мс, почтовых ящиков: {}", System.currentTimeMillis() - t0, state.mailboxes.size());
    }

    interface PlotBuilder {
        void build(B b);
    }

    private static void plot(ServerWorld w, BlockPos o, int cx, int cz, BlockRotation rot, PlotBuilder pb) {
        pb.build(new B(w, o.getX() + cx, o.getY(), o.getZ() + cz, rot));
    }

    // ---------------------------------------------------------------- дороги
    private static void roads(B b) {
        BlockState side = ModContent.SIDEWALK.getDefaultState(), asphalt = ModContent.ASPHALT.getDefaultState();
        for (int c : ROADS) {
            b.fill(-EDGE, -1, c - 6, EDGE, -1, c + 6, side);
            b.fill(c - 6, -1, -EDGE, c + 6, -1, EDGE, side);
        }
        for (int c : ROADS) {
            b.fill(-EDGE, -1, c - 4, EDGE, -1, c + 4, asphalt);
            b.fill(c - 4, -1, -EDGE, c + 4, -1, EDGE, asphalt);
        }
        BlockState lineX = ModContent.ASPHALT_LINE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST);
        BlockState lineZ = ModContent.ASPHALT_LINE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        BlockState crossX = ModContent.CROSSWALK.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST);
        BlockState crossZ = ModContent.CROSSWALK.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH);
        for (int c : ROADS) {
            for (int t = -EDGE; t <= EDGE; t++) {
                int near = nearestRoadDist(t);
                if (near > 8 && Math.floorMod(t, 4) < 2) {
                    b.set(t, -1, c, lineX);
                    b.set(c, -1, t, lineZ);
                }
                if (near == 6 || near == 7) {
                    for (int d = -4; d <= 4; d++) {
                        b.set(t, -1, c + d, crossX);
                        b.set(c + d, -1, t, crossZ);
                    }
                }
            }
        }
        // светофоры на углах перекрёстков
        for (int cx : ROADS) {
            for (int cz : ROADS) {
                trafficLight(b, cx + 6, cz - 6, Direction.NORTH);
                trafficLight(b, cx + 6, cz + 6, Direction.EAST);
                trafficLight(b, cx - 6, cz + 6, Direction.SOUTH);
                trafficLight(b, cx - 6, cz - 6, Direction.WEST);
            }
        }
        // фонари вдоль тротуаров
        for (int c : ROADS) {
            for (int t = -EDGE + 4; t <= EDGE - 4; t += 12) {
                if (nearestRoadDist(t) < 9) continue;
                lamp(b, t, c + 6, Direction.NORTH);
                lamp(b, t + 6, c - 6, Direction.SOUTH);
                lamp(b, c + 6, t, Direction.WEST);
                lamp(b, c - 6, t + 6, Direction.EAST);
            }
        }
    }

    private static int nearestRoadDist(int t) {
        int best = Integer.MAX_VALUE;
        for (int c : ROADS) best = Math.min(best, Math.abs(t - c));
        return best;
    }

    private static void trafficLight(B b, int x, int z, Direction face) {
        b.set(x, 0, z, ModContent.LAMP_POST.getDefaultState());
        b.set(x, 1, z, ModContent.LAMP_POST.getDefaultState());
        b.set(x, 2, z, ModContent.TRAFFIC_LIGHT.getDefaultState().with(HorizontalFacingBlock.FACING, face));
        b.world.scheduleBlockTick(b.pos(x, 2, z), ModContent.TRAFFIC_LIGHT, 1);
    }

    private static void lamp(B b, int x, int z, Direction face) {
        if (Math.abs(x) > EDGE - 2 || Math.abs(z) > EDGE - 2) return;
        for (int y = 0; y < 3; y++) b.set(x, y, z, ModContent.LAMP_POST.getDefaultState());
        b.set(x, 3, z, ModContent.STREET_LAMP.getDefaultState().with(HorizontalFacingBlock.FACING, face));
    }

    private static void streetLife(B b) {
        Random r = b.world.getRandom();
        // припаркованные машины, на которых может прокатиться любой
        int[][] cars = {{-40, 26, 90, 1}, {-52, 26, 90, 4}, {40, 22, -90, 0}, {58, -22, -90, 3}, {26, -40, 0, 2}, {-26, 46, 180, 5}};
        for (int[] c : cars) b.car(c[0], c[1], c[2], c[3]);
        for (int i = 0; i < 10; i++) {
            int c = ROADS[r.nextInt(ROADS.length)] + (r.nextBoolean() ? 5 : -5);
            int t = -60 + r.nextInt(120);
            int skin = 2 + r.nextInt(6);
            if (r.nextBoolean()) b.citizen(t, 0, c, skin, CitizenEntity.Role.CITIZEN, false, Names.random(r), 0);
            else b.citizen(c, 0, t, skin, CitizenEntity.Role.CITIZEN, false, Names.random(r), 0);
        }
    }

    // ---------------------------------------------------------------- школа (центр)
    private static void school(B b) {
        BlockState wall = Blocks.BRICKS.getDefaultState(), floor = Blocks.OAK_PLANKS.getDefaultState(), white = Blocks.WHITE_CONCRETE.getDefaultState();
        int x1 = -15, x2 = 15, z1 = -16, z2 = 2, h = 6;
        b.fill(x1, -1, z1, x2, -1, z2, floor);
        b.walls(x1, 0, z1, x2, h - 1, z2, wall);
        b.fill(x1, h, z1, x2, h, z2, Blocks.SMOOTH_STONE.getDefaultState());
        b.walls(x1, h + 1, z1, x2, h + 1, z2, Blocks.STONE_BRICK_WALL.getDefaultState());
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, h - 1, z2 - 1, Blocks.AIR.getDefaultState());
        b.fill(x1 + 1, h - 1, z1 + 1, x2 - 1, h - 1, z2 - 1, white);
        // окна по фасаду и бокам
        for (int x = x1 + 2; x <= x2 - 2; x += 3) if (Math.abs(x) > 2) b.fill(x, 1, z2, x + 1, 3, z2, Blocks.GLASS.getDefaultState());
        for (int z = z1 + 2; z <= z2 - 2; z += 3) {
            b.fill(x1, 1, z, x1, 3, z + 1, Blocks.GLASS.getDefaultState());
            b.fill(x2, 1, z, x2, 3, z + 1, Blocks.GLASS.getDefaultState());
        }
        // вход: широкие двери и крыльцо
        b.door(-1, 0, z2, Direction.SOUTH, Blocks.BIRCH_DOOR, false);
        b.door(0, 0, z2, Direction.SOUTH, Blocks.BIRCH_DOOR, true);
        b.fill(-3, -1, z2 + 1, 2, -1, z2 + 2, Blocks.POLISHED_ANDESITE.getDefaultState());
        b.fill(-3, 4, z2 + 1, 2, 4, z2 + 1, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        // перегородки: три класса у северной стены и коридор
        int[][] rooms = {{-14, -6}, {-4, 4}, {6, 14}};
        b.fill(x1 + 1, 0, -5, x2 - 1, h - 2, -5, white);
        for (int x : new int[]{-5, 5}) b.fill(x, 0, z1 + 1, x, h - 2, -5, white);
        String[] teachers = {"Марья Ивановна", "Анна Петровна", "Сергей Олегович"};
        for (int i = 0; i < rooms.length; i++) {
            int a = rooms[i][0], c = rooms[i][1], mid = (a + c) / 2;
            b.fill(mid, 0, -5, mid, 1, -5, Blocks.AIR.getDefaultState());
            b.door(mid, 0, -5, Direction.SOUTH, Blocks.OAK_DOOR, false);
            b.fill(a, -1, z1 + 1, c, -1, -6, Blocks.SPRUCE_PLANKS.getDefaultState());
            for (int x = mid - 2; x <= mid + 2; x++)
                for (int y = 1; y <= 2; y++) b.set(x, y, z1 + 1, ModContent.CHALKBOARD.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            b.set(mid - 1, 0, z1 + 3, Blocks.SPRUCE_PLANKS.getDefaultState());
            b.set(mid, 0, z1 + 3, Blocks.SPRUCE_PLANKS.getDefaultState());
            b.set(mid - 1, 1, z1 + 3, Blocks.FLOWER_POT.getDefaultState());
            b.set(mid, 1, z1 + 3, Blocks.POTTED_RED_TULIP.getDefaultState());
            b.citizen(mid + 1, 0, z1 + 3, 0, CitizenEntity.Role.TEACHER, true, teachers[i], 0);
            for (int row = 0; row < 3; row++)
                for (int col = a + 1; col <= c - 1; col += 2)
                    b.set(col, 0, z1 + 6 + row * 2, ModContent.SCHOOL_DESK.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
            b.set(mid, h - 1, -10, Blocks.SEA_LANTERN.getDefaultState());
            b.set(a + 1, h - 1, -13, Blocks.SEA_LANTERN.getDefaultState());
            b.set(c - 1, h - 1, -13, Blocks.SEA_LANTERN.getDefaultState());
            b.set(a + 1, h - 1, -7, Blocks.SEA_LANTERN.getDefaultState());
            b.set(c - 1, h - 1, -7, Blocks.SEA_LANTERN.getDefaultState());
            b.set(a + 1, 0, -6, Blocks.BOOKSHELF.getDefaultState());
            b.set(c - 1, 0, -6, Blocks.BOOKSHELF.getDefaultState());
        }
        // коридор: шкафчики, кубки, лампы, ученики
        for (int x = x1 + 2; x <= x2 - 2; x += 5) b.set(x, h - 1, -2, Blocks.SEA_LANTERN.getDefaultState());
        for (int x = x1 + 1; x <= x1 + 6; x++) {
            b.set(x, 0, z2 - 1, Blocks.IRON_BLOCK.getDefaultState());
            b.set(x, 1, z2 - 1, Blocks.IRON_BLOCK.getDefaultState());
        }
        b.set(x2 - 2, 0, z2 - 1, Blocks.GLASS.getDefaultState());
        b.set(x2 - 2, 1, z2 - 1, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(x2 - 3, 0, z2 - 1, ModContent.TV.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH).with(com.yompix.lifecraft.block.TvBlock.CHANNEL, 1));
        Random r = b.world.getRandom();
        for (int i = 0; i < 3; i++) b.citizen(-8 + i * 7, 0, -2, 2 + (i % 2), CitizenEntity.Role.CITIZEN, false, Names.random(r), 0);
        // школьный двор: мини-футбол, флагшток, скамейки
        b.fill(-14, -1, 5, 14, -1, 16, Blocks.GRASS_BLOCK.getDefaultState());
        for (int x = -12; x <= 12; x++) {
            b.set(x, -1, 7, Blocks.WHITE_CONCRETE.getDefaultState());
            b.set(x, -1, 15, Blocks.WHITE_CONCRETE.getDefaultState());
        }
        for (int z = 7; z <= 15; z++) {
            b.set(-12, -1, z, Blocks.WHITE_CONCRETE.getDefaultState());
            b.set(12, -1, z, Blocks.WHITE_CONCRETE.getDefaultState());
            b.set(0, -1, z, Blocks.WHITE_CONCRETE.getDefaultState());
        }
        for (int z = 10; z <= 12; z++) {
            b.set(-13, 0, z, Blocks.AIR.getDefaultState());
            b.set(-13, 1, z, Blocks.AIR.getDefaultState());
        }
        for (int x : new int[]{-13, 13}) {
            b.set(x, 0, 10, Blocks.WHITE_STAINED_GLASS_PANE.getDefaultState());
            b.set(x, 0, 12, Blocks.WHITE_STAINED_GLASS_PANE.getDefaultState());
            b.set(x, 1, 10, Blocks.WHITE_WOOL.getDefaultState());
            b.set(x, 1, 11, Blocks.WHITE_WOOL.getDefaultState());
            b.set(x, 1, 12, Blocks.WHITE_WOOL.getDefaultState());
        }
        for (int y = 0; y < 7; y++) b.set(-15, y, 4, Blocks.IRON_BARS.getDefaultState());
        b.set(-14, 6, 4, Blocks.WHITE_WOOL.getDefaultState());
        b.set(-14, 5, 4, Blocks.BLUE_WOOL.getDefaultState());
        b.set(-13, 6, 4, Blocks.WHITE_WOOL.getDefaultState());
        b.set(-13, 5, 4, Blocks.RED_WOOL.getDefaultState());
        b.stairs(6, 0, 4, Direction.NORTH, Blocks.OAK_STAIRS);
        b.stairs(7, 0, 4, Direction.NORTH, Blocks.OAK_STAIRS);
        b.mailbox(4, 0, 4);
        b.citizen(-5, 0, 11, 2, CitizenEntity.Role.CITIZEN, false, Names.random(r), 0);
        b.citizen(5, 0, 10, 3, CitizenEntity.Role.CITIZEN, false, Names.random(r), 0);
        b.label(0, 8, 2, "🏫 ШКОЛА №1", Formatting.GOLD);
    }

    // ---------------------------------------------------------------- магазин
    private static void shop(B b) {
        int x1 = -14, x2 = 14, z1 = -12, z2 = 4, h = 5;
        building(b, x1, z1, x2, z2, h, Blocks.WHITE_CONCRETE.getDefaultState(), Blocks.LIGHT_GRAY_CONCRETE.getDefaultState(), Blocks.POLISHED_ANDESITE.getDefaultState());
        b.fill(x1, h - 1, z2, x2, h - 1, z2, Blocks.RED_CONCRETE.getDefaultState());
        for (int x = x1 + 2; x <= x2 - 2; x++) if (Math.abs(x) > 1) b.fill(x, 1, z2, x, 2, z2, Blocks.GLASS.getDefaultState());
        b.door(0, 0, z2, Direction.SOUTH, Blocks.IRON_DOOR, false);
        b.set(1, 1, z2 + 1, Blocks.STONE_BUTTON.getDefaultState().with(net.minecraft.block.ButtonBlock.FACING, Direction.SOUTH));
        // кассы
        for (int x : new int[]{-6, -5, 5, 6}) b.set(x, 0, 1, ModContent.SHOP_COUNTER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        b.citizen(-5, 0, 0, 1, CitizenEntity.Role.SHOPKEEPER, true, "Продавец Зина", 180);
        b.citizen(5, 0, 0, 1, CitizenEntity.Role.SHOPKEEPER, true, "Продавец Олег", 180);
        // стеллажи и холодильники
        for (int z = -10; z <= -3; z += 3) {
            for (int x = x1 + 3; x <= x2 - 3; x++) {
                if (Math.abs(x) <= 1) continue;
                b.set(x, 0, z, Blocks.BARREL.getDefaultState());
                b.set(x, 1, z, (x + z) % 3 == 0 ? Blocks.BOOKSHELF.getDefaultState() : Blocks.CHISELED_BOOKSHELF.getDefaultState());
            }
        }
        for (int z = z1 + 1; z <= z2 - 3; z++) {
            b.set(x1 + 1, 0, z, Blocks.IRON_BLOCK.getDefaultState());
            b.set(x1 + 1, 1, z, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
            b.set(x2 - 1, 0, z, Blocks.IRON_BLOCK.getDefaultState());
            b.set(x2 - 1, 1, z, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
        }
        lights(b, x1, z1, x2, z2, h);
        b.set(9, 0, z2 + 1, ModContent.ATM.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        b.set(10, 0, z2 + 1, ModContent.ATM.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        b.mailbox(-9, 0, z2 + 2);
        b.fill(x1, -1, z2 + 1, x2, -1, 15, ModContent.ASPHALT.getDefaultState());
        for (int x = x1; x <= x2; x += 4) b.fill(x, -1, 9, x, -1, 15, Blocks.WHITE_CONCRETE.getDefaultState());
        b.car(-6, 12, 0, 2);
        b.car(6, 12, 0, 4);
        b.label(0, h + 2, z2, "🛒 СУПЕРМАРКЕТ", Formatting.RED);
    }

    // ---------------------------------------------------------------- офис и центр занятости
    private static void office(B b) {
        int x1 = -13, x2 = 13, z1 = -14, z2 = 4, h = 9;
        building(b, x1, z1, x2, z2, h, Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState(), Blocks.GRAY_CONCRETE.getDefaultState(), Blocks.SMOOTH_QUARTZ.getDefaultState());
        for (int y = 0; y < h; y += 3) b.walls(x1, y, z1, x2, y, z2, Blocks.GRAY_CONCRETE.getDefaultState());
        b.fill(x1 + 1, 4, z1 + 1, x2 - 1, 4, z2 - 1, Blocks.SMOOTH_QUARTZ.getDefaultState());
        b.door(0, 0, z2, Direction.SOUTH, Blocks.IRON_DOOR, false);
        b.set(1, 1, z2 + 1, Blocks.STONE_BUTTON.getDefaultState().with(net.minecraft.block.ButtonBlock.FACING, Direction.SOUTH));
        b.set(-1, 1, z2 - 1, Blocks.STONE_BUTTON.getDefaultState().with(net.minecraft.block.ButtonBlock.FACING, Direction.NORTH));
        for (int x = -2; x <= 2; x++) b.set(x, 1, z1 + 1, ModContent.JOB_BOARD.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        b.citizen(0, 0, z1 + 3, 5, CitizenEntity.Role.CITIZEN, true, "Менеджер Ирина", 0);
        Random r = b.world.getRandom();
        int n = 0;
        for (int z = -8; z <= -2; z += 3) {
            for (int x : new int[]{-10, -7, 7, 10}) {
                b.set(x, 0, z, Blocks.SPRUCE_PLANKS.getDefaultState());
                b.set(x, 1, z, ModContent.COMPUTER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
                b.stairs(x, 0, z + 1, Direction.NORTH, Blocks.DARK_OAK_STAIRS);
                if (n++ % 3 == 0) b.citizen(x, 0, z + 1, 5, CitizenEntity.Role.CITIZEN, true, Names.full(r), 180);
            }
        }
        b.set(x2 - 1, 0, z2 - 1, Blocks.POTTED_FERN.getDefaultState());
        b.set(x1 + 1, 0, z2 - 1, Blocks.CAULDRON.getDefaultState());
        lights(b, x1, z1, x2, z2, 4);
        // второй этаж: лестница и переговорка
        for (int i = 0; i < 4; i++) b.stairs(x2 - 2, i, z1 + 2 + i, Direction.SOUTH, Blocks.QUARTZ_STAIRS);
        b.fill(x2 - 2, 4, z1 + 2, x2 - 2, 4, z1 + 6, Blocks.AIR.getDefaultState());
        b.fill(-4, 5, -10, 4, 5, -6, Blocks.DARK_OAK_PLANKS.getDefaultState());
        b.set(0, 5, -11, ModContent.TV.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH).with(com.yompix.lifecraft.block.TvBlock.CHANNEL, 1));
        lights(b, x1, z1, x2, z2, h - 1);
        b.mailbox(-5, 0, z2 + 2);
        b.label(0, h + 2, z2, "💼 ОФИС · ЦЕНТР ЗАНЯТОСТИ", Formatting.AQUA);
    }

    // ---------------------------------------------------------------- парк
    private static void park(B b) {
        Random r = b.world.getRandom();
        b.fill(-16, -1, -16, 16, -1, 16, Blocks.GRASS_BLOCK.getDefaultState());
        b.fill(-1, -1, -16, 1, -1, 16, Blocks.DIRT_PATH.getDefaultState());
        b.fill(-16, -1, -1, 16, -1, 1, Blocks.DIRT_PATH.getDefaultState());
        // фонтан
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 5.5) b.set(x, -1, z, Blocks.STONE_BRICKS.getDefaultState());
                if (d > 4.5 && d <= 5.5) b.set(x, 0, z, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
                else if (d <= 4.5) b.set(x, 0, z, Blocks.WATER.getDefaultState());
            }
        for (int y = 0; y <= 2; y++) b.set(0, y, 0, Blocks.STONE_BRICK_WALL.getDefaultState());
        b.set(0, 3, 0, Blocks.WATER.getDefaultState());
        for (int[] t : new int[][]{{-11, -11}, {11, -11}, {-11, 11}, {11, 11}, {-13, 4}, {13, -4}, {5, -13}, {-5, 13}}) tree(b, t[0], t[1], r);
        for (int x : new int[]{-8, -6}) {
            b.stairs(x, 0, 3, Direction.NORTH, Blocks.OAK_STAIRS);
            b.stairs(x, 0, -3, Direction.SOUTH, Blocks.OAK_STAIRS);
        }
        for (int x : new int[]{6, 8}) {
            b.stairs(x, 0, 3, Direction.NORTH, Blocks.OAK_STAIRS);
            b.stairs(x, 0, -3, Direction.SOUTH, Blocks.OAK_STAIRS);
        }
        BlockState[] flowers = {Blocks.POPPY.getDefaultState(), Blocks.DANDELION.getDefaultState(), Blocks.CORNFLOWER.getDefaultState(), Blocks.OXEYE_DAISY.getDefaultState(), Blocks.ALLIUM.getDefaultState()};
        for (int i = 0; i < 60; i++) {
            int x = -15 + r.nextInt(31), z = -15 + r.nextInt(31);
            if (Math.abs(x) > 2 && Math.abs(z) > 2 && x * x + z * z > 40 && b.get(x, 0, z).isAir()) b.set(x, 0, z, flowers[r.nextInt(flowers.length)]);
        }
        b.citizen(-7, 0, 4, 7, CitizenEntity.Role.CITIZEN, false, "Баба Валя", 0);
        b.citizen(7, 0, -4, 6, CitizenEntity.Role.CITIZEN, false, "Полицейский Петров", 0);
        b.citizen(9, 0, 9, 4, CitizenEntity.Role.CITIZEN, false, Names.random(r), 0);
        b.label(0, 6, 0, "🌳 ПАРК", Formatting.GREEN);
    }

    private static void tree(B b, int x, int z, Random r) {
        int h = 4 + r.nextInt(2);
        BlockState leaves = (r.nextBoolean() ? Blocks.OAK_LEAVES : Blocks.BIRCH_LEAVES).getDefaultState().with(LeavesBlock.PERSISTENT, true);
        BlockState log = leaves.isOf(Blocks.OAK_LEAVES) ? Blocks.OAK_LOG.getDefaultState() : Blocks.BIRCH_LOG.getDefaultState();
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = h - 2; dy <= h + 1; dy++) {
                    int rad = dy > h ? 1 : 2;
                    if (Math.abs(dx) <= rad && Math.abs(dz) <= rad && !(Math.abs(dx) == 2 && Math.abs(dz) == 2)) b.set(x + dx, dy, z + dz, leaves);
                }
        for (int y = 0; y < h; y++) b.set(x, y, z, log);
    }

    // ---------------------------------------------------------------- дома
    private static void houses(B b) {
        BlockState[][] styles = {
                {Blocks.OAK_PLANKS.getDefaultState(), Blocks.SPRUCE_PLANKS.getDefaultState(), Blocks.DARK_OAK_PLANKS.getDefaultState()},
                {Blocks.WHITE_TERRACOTTA.getDefaultState(), Blocks.BRICKS.getDefaultState(), Blocks.RED_NETHER_BRICKS.getDefaultState()},
                {Blocks.LIGHT_BLUE_TERRACOTTA.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState(), Blocks.DEEPSLATE_TILES.getDefaultState()},
                {Blocks.YELLOW_TERRACOTTA.getDefaultState(), Blocks.BIRCH_PLANKS.getDefaultState(), Blocks.MANGROVE_PLANKS.getDefaultState()}};
        Random r = b.world.getRandom();
        house(b.sub(-8), styles[r.nextInt(styles.length)], r);
        house(b.sub(8), styles[r.nextInt(styles.length)], r);
    }

    private static void house(B b, BlockState[] style, Random r) {
        int x1 = -6, x2 = 6, z1 = -10, z2 = 2, h = 5;
        b.fill(x1, -1, z1, x2, -1, z2, style[1]);
        b.walls(x1, 0, z1, x2, h - 1, z2, style[0]);
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, h - 1, z2 - 1, Blocks.AIR.getDefaultState());
        for (int y = 0; y < h; y++) {
            b.set(x1, y, z1, style[1]);
            b.set(x2, y, z1, style[1]);
            b.set(x1, y, z2, style[1]);
            b.set(x2, y, z2, style[1]);
        }
        // двускатная крыша: скаты по x, фронтоны по z
        b.fill(x1, h, z1, x2, h, z2, style[2]);
        b.fill(x1 + 1, h, z1 + 1, x2 - 1, h, z2 - 1, Blocks.OAK_PLANKS.getDefaultState());
        for (int k = 0; k <= 7; k++) {
            int left = x1 - 1 + k, right = x2 + 1 - k;
            if (left > right) break;
            b.fill(left, h + k, z1 - 1, left, h + k, z2 + 1, style[2]);
            b.fill(right, h + k, z1 - 1, right, h + k, z2 + 1, style[2]);
            if (left + 1 <= right - 1) {
                b.fill(left + 1, h + k, z1, right - 1, h + k, z1, style[0]);
                b.fill(left + 1, h + k, z2, right - 1, h + k, z2, style[0]);
            }
        }
        // окна
        for (int x : new int[]{-4, -3, 3, 4}) b.fill(x, 1, z2, x, 2, z2, Blocks.GLASS.getDefaultState());
        for (int z : new int[]{-7, -6, -3, -2}) {
            b.fill(x1, 1, z, x1, 2, z, Blocks.GLASS.getDefaultState());
            b.fill(x2, 1, z, x2, 2, z, Blocks.GLASS.getDefaultState());
        }
        b.door(0, 0, z2, Direction.SOUTH, Blocks.SPRUCE_DOOR, false);
        // спальня, гостиная с телевизором и диваном, кухня
        b.bed(-4, 0, -8, Direction.NORTH, Blocks.LIGHT_BLUE_BED);
        b.set(-5, 0, -9, Blocks.CHEST.getDefaultState().with(net.minecraft.block.ChestBlock.FACING, Direction.EAST));
        b.set(-2, 0, -9, Blocks.BOOKSHELF.getDefaultState());
        b.set(-5, 0, -4, Blocks.SPRUCE_PLANKS.getDefaultState());
        b.set(-5, 1, -4, ModContent.COMPUTER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST));
        b.set(4, 0, -9, Blocks.SMOKER.getDefaultState().with(net.minecraft.block.AbstractFurnaceBlock.FACING, Direction.SOUTH));
        b.set(3, 0, -9, Blocks.CAULDRON.getDefaultState());
        b.set(2, 0, -9, Blocks.CRAFTING_TABLE.getDefaultState());
        b.set(5, 0, -9, Blocks.IRON_BLOCK.getDefaultState());
        b.set(5, 1, -9, Blocks.IRON_BLOCK.getDefaultState());
        b.set(3, 0, -6, Blocks.OAK_FENCE.getDefaultState());
        b.set(3, 1, -6, Blocks.OAK_PRESSURE_PLATE.getDefaultState());
        b.stairs(4, 0, -6, Direction.WEST, Blocks.OAK_STAIRS);
        b.set(5, 0, -2, ModContent.TV.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST).with(com.yompix.lifecraft.block.TvBlock.CHANNEL, 1 + r.nextInt(5)));
        b.stairs(2, 0, -3, Direction.EAST, Blocks.CRIMSON_STAIRS);
        b.stairs(2, 0, -2, Direction.EAST, Blocks.CRIMSON_STAIRS);
        b.stairs(2, 0, -1, Direction.EAST, Blocks.CRIMSON_STAIRS);
        b.fill(-1, -1, -3, 1, -1, -1, Blocks.RED_WOOL.getDefaultState());
        b.set(0, h - 1, -4, Blocks.GLOWSTONE.getDefaultState());
        b.set(-4, h - 1, -7, Blocks.GLOWSTONE.getDefaultState());
        b.set(4, h - 1, -7, Blocks.GLOWSTONE.getDefaultState());
        // палисадник и почтовый ящик
        for (int x = x1; x <= x2; x++) if (Math.abs(x) > 1) b.set(x, 0, 9, Blocks.BIRCH_FENCE.getDefaultState());
        b.fill(-1, -1, z2 + 1, 1, -1, 12, Blocks.STONE_BRICKS.getDefaultState());
        for (int x = x1 + 1; x <= x2 - 1; x++)
            if (Math.abs(x) > 2 && r.nextInt(3) == 0) b.set(x, 0, z2 + 2 + r.nextInt(5), (r.nextBoolean() ? Blocks.POPPY : Blocks.AZURE_BLUET).getDefaultState());
        b.mailbox(2, 0, 10);
    }

    // ---------------------------------------------------------------- автосалон
    private static void dealership(B b) {
        int x1 = -15, x2 = -1, z1 = -12, z2 = 2, h = 6;
        building(b, x1, z1, x2, z2, h, Blocks.GLASS.getDefaultState(), Blocks.WHITE_CONCRETE.getDefaultState(), Blocks.POLISHED_DIORITE.getDefaultState());
        b.fill(x1 + 1, 0, z2, x2 - 1, 3, z2, Blocks.GLASS.getDefaultState());
        b.door(-8, 0, z2, Direction.SOUTH, Blocks.IRON_DOOR, false);
        b.set(-7, 1, z2 + 1, Blocks.STONE_BUTTON.getDefaultState().with(net.minecraft.block.ButtonBlock.FACING, Direction.SOUTH));
        b.set(-9, 1, z2 - 1, Blocks.STONE_BUTTON.getDefaultState().with(net.minecraft.block.ButtonBlock.FACING, Direction.NORTH));
        lights(b, x1, z1, x2, z2, h - 1);
        b.car(-10, -6, 30, 3);
        b.car(-5, -7, -30, 4);
        b.set(-13, 0, -2, ModContent.SHOP_COUNTER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST));
        b.citizen(-14, 0, -2, 5, CitizenEntity.Role.SHOPKEEPER, true, "Автодилер Гена", -90);
        // стоянка и стоянка такси
        b.fill(1, -1, -15, 16, -1, 15, ModContent.ASPHALT.getDefaultState());
        for (int z = -15; z <= 15; z += 5) b.fill(1, -1, z, 6, -1, z, Blocks.WHITE_CONCRETE.getDefaultState());
        for (int z = -15; z <= 15; z += 5) b.fill(11, -1, z, 16, -1, z, Blocks.YELLOW_CONCRETE.getDefaultState());
        b.car(4, -12, 90, 0);
        b.car(4, -7, 90, 1);
        b.car(4, 3, 90, 2);
        b.car(14, -2, -90, 5);
        b.car(14, 8, -90, 5);
        b.mailbox(-3, 0, z2 + 2);
        b.label(-8, h + 2, z2, "🚗 АВТОСАЛОН", Formatting.YELLOW);
        b.label(13, 3, 12, "🚕 ТАКСИ", Formatting.YELLOW);
    }

    // ---------------------------------------------------------------- казино и банк
    private static void casino(B b) {
        int x1 = -16, x2 = 6, z1 = -14, z2 = 4, h = 7;
        building(b, x1, z1, x2, z2, h, Blocks.BLACK_CONCRETE.getDefaultState(), Blocks.GOLD_BLOCK.getDefaultState(), Blocks.RED_CARPET.getDefaultState());
        b.fill(x1 + 1, -1, z1 + 1, x2 - 1, -1, z2 - 1, Blocks.BLACK_CONCRETE.getDefaultState());
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, Blocks.RED_CARPET.getDefaultState());
        b.fill(-6, 0, z2, -4, 2, z2, Blocks.AIR.getDefaultState());
        b.fill(-7, 3, z2 + 1, -3, 3, z2 + 1, Blocks.GOLD_BLOCK.getDefaultState());
        for (int x = x1 + 1; x <= x2 - 1; x++) {
            b.set(x, h - 1, z2, Blocks.SHROOMLIGHT.getDefaultState());
            b.set(x, h, z2 + 1, (x % 2 == 0 ? Blocks.MAGENTA_STAINED_GLASS : Blocks.PURPLE_STAINED_GLASS).getDefaultState());
            if (Math.abs(x + 5) > 2 && x % 3 != 0) b.fill(x, 1, z2, x, 3, z2, Blocks.PURPLE_STAINED_GLASS.getDefaultState());
        }
        for (int x : new int[]{-8, -2}) for (int y = 0; y < 4; y++) b.set(x, y, z2 + 1, Blocks.GOLD_BLOCK.getDefaultState());
        for (int z = z2 + 1; z <= z2 + 8; z++) b.fill(-6, -1, z, -4, -1, z, Blocks.RED_CONCRETE.getDefaultState());
        for (int z = z2 + 2; z <= z2 + 8; z += 3) {
            b.set(-7, 0, z, Blocks.GOLD_BLOCK.getDefaultState());
            b.set(-7, 1, z, Blocks.SEA_LANTERN.getDefaultState());
            b.set(-3, 0, z, Blocks.GOLD_BLOCK.getDefaultState());
            b.set(-3, 1, z, Blocks.SEA_LANTERN.getDefaultState());
        }
        // автоматы вдоль стен
        for (int x = x1 + 2; x <= x2 - 2; x += 2) b.set(x, 0, z1 + 1, ModContent.SLOT_MACHINE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        for (int z = z1 + 3; z <= z2 - 3; z += 2) b.set(x1 + 1, 0, z, ModContent.SLOT_MACHINE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST));
        // рулетки
        for (int x : new int[]{-10, -2}) {
            b.set(x, 0, -5, ModContent.ROULETTE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            b.set(x - 1, 0, -5, Blocks.GREEN_CARPET.getDefaultState());
            b.set(x + 1, 0, -5, Blocks.GREEN_CARPET.getDefaultState());
            b.citizen(x, 0, -6, 5, CitizenEntity.Role.DEALER, true, "Крупье", 0);
        }
        b.citizen(-5, 0, z1 + 3, 6, CitizenEntity.Role.CROUPIER, true, "Администратор казино", 0);
        b.citizen(-8, 0, z2 - 1, 6, CitizenEntity.Role.CITIZEN, true, "Охрана", 180);
        for (int x = x1 + 3; x <= x2 - 3; x += 5)
            for (int z = z1 + 3; z <= z2 - 2; z += 5) b.set(x, h - 1, z, Blocks.SEA_LANTERN.getDefaultState());
        b.label(-5, h + 2, z2, "🎰 КАЗИНО «ФОРТУНА» 18+", Formatting.LIGHT_PURPLE);

        // банк
        int bx1 = 8, bx2 = 16;
        building(b, bx1, z1 + 2, bx2, z2, 6, Blocks.QUARTZ_PILLAR.getDefaultState(), Blocks.QUARTZ_BLOCK.getDefaultState(), Blocks.POLISHED_DIORITE.getDefaultState());
        b.door(12, 0, z2, Direction.SOUTH, Blocks.IRON_DOOR, false);
        b.set(13, 1, z2 + 1, Blocks.STONE_BUTTON.getDefaultState().with(net.minecraft.block.ButtonBlock.FACING, Direction.SOUTH));
        b.set(11, 1, z2 - 1, Blocks.STONE_BUTTON.getDefaultState().with(net.minecraft.block.ButtonBlock.FACING, Direction.NORTH));
        for (int x = 10; x <= 14; x += 2) b.set(x, 0, z1 + 3, ModContent.ATM.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        b.set(15, 0, -4, ModContent.SHOP_COUNTER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
        b.citizen(15, 0, -6, 5, CitizenEntity.Role.BANKER, true, "Банкир Аркадий", -90);
        lights(b, bx1, z1 + 2, bx2, z2, 5);
        b.mailbox(16, 0, z2 + 2);
        b.label(12, 8, z2, "🏦 БАНК", Formatting.GREEN);
    }

    // ---------------------------------------------------------------- общие помощники
    private static void building(B b, int x1, int z1, int x2, int z2, int h, BlockState wall, BlockState frame, BlockState floor) {
        b.fill(x1, -1, z1, x2, -1, z2, floor.isOf(Blocks.RED_CARPET) ? Blocks.BLACK_CONCRETE.getDefaultState() : floor);
        b.walls(x1, 0, z1, x2, h - 1, z2, wall);
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, h - 1, z2 - 1, Blocks.AIR.getDefaultState());
        for (int y = 0; y < h; y++) {
            b.set(x1, y, z1, frame);
            b.set(x2, y, z1, frame);
            b.set(x1, y, z2, frame);
            b.set(x2, y, z2, frame);
        }
        b.fill(x1, h, z1, x2, h, z2, frame);
    }

    private static void lights(B b, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1 + 3; x <= x2 - 3; x += 5)
            for (int z = z1 + 3; z <= z2 - 2; z += 5) b.set(x, y, z, Blocks.SEA_LANTERN.getDefaultState());
    }

    /** Строитель в локальных координатах квартала: фасад к +z, поворот применяется ко всему. */
    static final class B {
        final ServerWorld world;
        final int ox, oy, oz;
        final BlockRotation rot;

        B(ServerWorld world, int ox, int oy, int oz, BlockRotation rot) {
            this.world = world;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
            this.rot = rot;
        }

        B sub(int dx) {
            int[] p = rotate(dx, 0);
            return new B(world, ox + p[0], oy, oz + p[1], rot);
        }

        int[] rotate(int x, int z) {
            return switch (rot) {
                case CLOCKWISE_90 -> new int[]{-z, x};
                case CLOCKWISE_180 -> new int[]{-x, -z};
                case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
                default -> new int[]{x, z};
            };
        }

        BlockPos pos(int x, int y, int z) {
            int[] p = rotate(x, z);
            return new BlockPos(ox + p[0], oy + y, oz + p[1]);
        }

        Direction dir(Direction d) {
            return rot.rotate(d);
        }

        BlockState get(int x, int y, int z) {
            return world.getBlockState(pos(x, y, z));
        }

        void set(int x, int y, int z, BlockState s) {
            world.setBlockState(pos(x, y, z), s.rotate(rot), FLAGS);
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
        }

        void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            fill(x1, y1, z1, x2, y2, z1, s);
            fill(x1, y1, z2, x2, y2, z2, s);
            fill(x1, y1, z1, x1, y2, z2, s);
            fill(x2, y1, z1, x2, y2, z2, s);
        }

        void door(int x, int y, int z, Direction facing, Block door, boolean rightHinge) {
            BlockState s = door.getDefaultState().with(DoorBlock.FACING, facing)
                    .with(DoorBlock.HINGE, rightHinge ? net.minecraft.block.enums.DoorHinge.RIGHT : net.minecraft.block.enums.DoorHinge.LEFT);
            set(x, y, z, s.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(x, y + 1, z, s.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }

        void stairs(int x, int y, int z, Direction facing, Block stairs) {
            set(x, y, z, stairs.getDefaultState().with(StairsBlock.FACING, facing));
        }

        void bed(int x, int y, int z, Direction toHead, Block bed) {
            BlockState s = bed.getDefaultState().with(BedBlock.FACING, toHead);
            set(x, y, z, s.with(BedBlock.PART, BedPart.FOOT));
            set(x + toHead.getOffsetX(), y, z + toHead.getOffsetZ(), s.with(BedBlock.PART, BedPart.HEAD));
        }

        void mailbox(int x, int y, int z) {
            set(x, y, z, ModContent.MAILBOX.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        }

        void citizen(int x, int y, int z, int skin, CitizenEntity.Role role, boolean stationary, String name, float yaw) {
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, world);
            BlockPos p = pos(x, y, z);
            float worldYaw = yaw + rotYaw();
            c.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, worldYaw, 0);
            c.setHeadYaw(worldYaw);
            c.setBodyYaw(worldYaw);
            c.setup(skin, role, stationary, name);
            world.spawnEntity(c);
        }

        void car(int x, int z, float yaw, int variant) {
            CarEntity car = new CarEntity(ModContent.CAR, world);
            BlockPos p = pos(x, 0, z);
            car.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw + rotYaw(), 0);
            car.setVariant(variant);
            world.spawnEntity(car);
        }

        float rotYaw() {
            return switch (rot) {
                case CLOCKWISE_90 -> 90;
                case CLOCKWISE_180 -> 180;
                case COUNTERCLOCKWISE_90 -> 270;
                default -> 0;
            };
        }

        /** Большая светящаяся надпись над зданием. */
        void label(int x, int y, int z, String text, Formatting color) {
            DisplayEntity.TextDisplayEntity d = EntityType.TEXT_DISPLAY.create(world);
            if (d == null) return;
            NbtCompound n = new NbtCompound();
            n.putString("text", Text.Serialization.toJsonString(Text.literal(text).formatted(color, Formatting.BOLD), world.getRegistryManager()));
            n.putString("billboard", "center");
            n.putString("alignment", "center");
            n.putInt("background", 0x99000000);
            n.putByte("shadow", (byte) 1);
            NbtCompound tr = new NbtCompound();
            tr.put("translation", floats(0, 0, 0));
            tr.put("left_rotation", floats(0, 0, 0, 1));
            tr.put("scale", floats(2.5f, 2.5f, 2.5f));
            tr.put("right_rotation", floats(0, 0, 0, 1));
            n.put("transformation", tr);
            NbtCompound br = new NbtCompound();
            br.putInt("block", 15);
            br.putInt("sky", 15);
            n.put("brightness", br);
            d.readNbt(n);
            BlockPos p = pos(x, y, z);
            d.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            world.spawnEntity(d);
        }

        private static NbtList floats(float... v) {
            NbtList l = new NbtList();
            for (float f : v) l.add(NbtFloat.of(f));
            return l;
        }
    }
}
