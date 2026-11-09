package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.block.NeonBlock;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.StyleCatalog;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.EulerAngle;

import java.util.List;

/**
 * Здания «стиля SA»: магазин одежды «Шмот-Базар» (дёшево), бутик «Бархат» (дорого), цирюльня «Острая бритва»
 * и зал «Железо». Каждое — комната 21×17 с витриной, продавцом и обстановкой.
 *
 * <p>Координаты: origin — блок пола посередине входа (там, где стоит игрок в дверях, на уровне пола),
 * facing — куда смотрит фасад (на улицу). Здание уходит от origin назад на 16 блоков и на 10 в стороны;
 * перед входом — тротуар 3 блока. Строить можно повторно на том же месте: старые продавцы и манекены убираются.
 */
public final class ShopBuilder {
    private ShopBuilder() {}

    public enum Kind { CLOTHES_CHEAP, CLOTHES_LUX, BARBER, GYM }

    /** Одно здание для {@link #placeAll}: вид, вход (уровень пола) и куда смотрит фасад. */
    public record Spot(Kind kind, BlockPos origin, Direction facing) {}

    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final String TAG = "novograd_style";

    /** Поставить все здания из списка (вызывать на сервере; чанки догрузятся сами). */
    public static void placeAll(ServerWorld world, List<Spot> spots) {
        for (Spot s : spots) place(world, s.kind(), s.origin(), s.facing());
    }

    public static void place(ServerWorld world, Kind kind, BlockPos origin, Direction facing) {
        switch (kind) {
            case CLOTHES_CHEAP -> clothesCheap(world, origin, facing);
            case CLOTHES_LUX -> clothesLux(world, origin, facing);
            case BARBER -> barber(world, origin, facing);
            case GYM -> gym(world, origin, facing);
        }
    }

    // ------------------------------------------------------------------ «Шмот-Базар»
    public static void clothesCheap(ServerWorld world, BlockPos origin, Direction facing) {
        R b = new R(world, origin, facing);
        b.shell(Blocks.BRICKS.getDefaultState(), Blocks.DARK_OAK_PLANKS.getDefaultState(), Blocks.SPRUCE_PLANKS.getDefaultState(),
                Blocks.GRAY_CONCRETE.getDefaultState(), Blocks.LIGHT_GRAY_CONCRETE.getDefaultState());
        // граффити на фасаде
        Block[] paint = {Blocks.LIME_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.CYAN_TERRACOTTA};
        for (int x = -10; x <= 10; x++) if (Math.abs(x) > 1 && (x * 7 & 3) == 0) b.set(x, 4, 0, paint[Math.floorMod(x, 4)].getDefaultState());
        b.awning(-4, 4, Blocks.RED_WOOL, Blocks.WHITE_WOOL);
        // полки с одеждой вдоль стен: стопки разноцветных вещей
        Block[] wool = {Blocks.RED_WOOL, Blocks.BLUE_WOOL, Blocks.BLACK_WOOL, Blocks.WHITE_WOOL, Blocks.GREEN_WOOL, Blocks.GRAY_WOOL, Blocks.YELLOW_WOOL, Blocks.PURPLE_WOOL};
        for (int z = -14; z <= -3; z++) {
            for (int y = 0; y <= 2; y++) {
                b.set(-9, y, z, (z + y) % 3 == 0 ? Blocks.SPRUCE_PLANKS.getDefaultState() : wool[Math.floorMod(z * 3 + y, wool.length)].getDefaultState());
                if (z > -10) b.set(9, y, z, (z + y) % 3 == 0 ? Blocks.SPRUCE_PLANKS.getDefaultState() : wool[Math.floorMod(z * 5 + y + 2, wool.length)].getDefaultState());
            }
            b.set(-9, 3, z, Blocks.SPRUCE_SLAB.getDefaultState());
            if (z > -10) b.set(9, 3, z, Blocks.SPRUCE_SLAB.getDefaultState());
        }
        // столы-бочки со сложенными футболками
        Block[] carpet = {Blocks.RED_CARPET, Blocks.BLUE_CARPET, Blocks.WHITE_CARPET, Blocks.BLACK_CARPET, Blocks.LIME_CARPET, Blocks.YELLOW_CARPET};
        int k = 0;
        for (int[] t : new int[][]{{-5, -4}, {-4, -4}, {-5, -8}, {-4, -8}, {0, -6}, {1, -6}, {-1, -6}}) {
            b.set(t[0], 0, t[1], Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
            b.set(t[0], 1, t[1], carpet[k++ % carpet.length].getDefaultState());
        }
        // вешалки: перекладина на столбиках, манекены в куртках
        b.rack(-6, 6, -11, Blocks.DARK_OAK_FENCE);
        b.mannequin(-5, -11, 0x1E50B4, 0x707070, Items.LEATHER_BOOTS, 0xF0F0F0, null, 0);
        b.mannequin(-2, -11, 0xC62828, 0x2F4A7A, Items.LEATHER_BOOTS, 0x1A1A1A, Items.LEATHER_HELMET, 0xC62828);
        b.mannequin(2, -11, 0x3A5F94, 0x1C1C22, Items.LEATHER_BOOTS, 0xF2F2F2, null, 0);
        b.mannequin(5, -11, 0x5E2B8A, 0x4E5A34, Items.LEATHER_BOOTS, 0x1A1A1A, Items.LEATHER_HELMET, 0x1B1B1B);
        // примерочные с шторками
        for (int x : new int[]{-8, -5}) {
            b.fill(x, 0, -15, x, 2, -13, Blocks.SPRUCE_PLANKS.getDefaultState());
            b.fill(x + 1, 3, -15, x + 2, 3, -13, Blocks.SPRUCE_SLAB.getDefaultState());
            b.set(x + 1, 2, -13, Blocks.RED_WOOL.getDefaultState());
            b.set(x + 2, 2, -13, Blocks.RED_WOOL.getDefaultState());
        }
        b.fill(-2, 0, -15, -2, 2, -13, Blocks.SPRUCE_PLANKS.getDefaultState());
        b.mirror(-7, -15, Blocks.SPRUCE_PLANKS);
        b.mirror(-4, -15, Blocks.SPRUCE_PLANKS);
        // касса
        b.counter(3, 8, -13, Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState(), Blocks.DARK_OAK_SLAB.getDefaultState());
        b.set(6, 1, -13, Blocks.LANTERN.getDefaultState());
        b.mirror(9, -12, Blocks.DARK_OAK_PLANKS);
        b.lights(Blocks.GLOWSTONE.getDefaultState());
        b.neonLine(NeonBlock.Color.GREEN);
        b.label(0, 8, 0, StyleCatalog.CHEAP_NAME.toUpperCase() + " · одежда недорого", Formatting.GREEN);
        b.npc(5, -14, CitizenEntity.RANDOM_FROM + 19, CitizenEntity.Role.TAILOR, "Продавец Гоша");
    }

    // ------------------------------------------------------------------ бутик «Бархат»
    public static void clothesLux(ServerWorld world, BlockPos origin, Direction facing) {
        R b = new R(world, origin, facing);
        b.shell(Blocks.SMOOTH_QUARTZ.getDefaultState(), Blocks.BLACK_CONCRETE.getDefaultState(), Blocks.QUARTZ_BLOCK.getDefaultState(),
                Blocks.POLISHED_BLACKSTONE.getDefaultState(), Blocks.WHITE_CONCRETE.getDefaultState());
        // золотые полосы на фасаде и бархатный навес
        for (int x = -10; x <= 10; x++) b.set(x, 4, 0, Blocks.GOLD_BLOCK.getDefaultState());
        b.awning(-3, 3, Blocks.PURPLE_WOOL, Blocks.PURPLE_WOOL);
        // подиум-дорожка из бархата и шахматный пол
        for (int x = -9; x <= 9; x++)
            for (int z = -15; z <= -1; z++)
                b.set(x, -1, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState());
        for (int z = -14; z <= -1; z++) for (int x = -1; x <= 1; x++) b.set(x, 0, z, Blocks.PURPLE_CARPET.getDefaultState());
        // манекены на кварцевых тумбах: костюмы, пальто, кожа
        int[][] stands = {{-6, -4}, {-6, -8}, {-6, -12}, {6, -4}, {6, -8}};
        int[][] looks = {{0x161618, 0x161618, 0x111111}, {0xB08A5A, 0x3A3C42, 0x5A3420}, {0xF2F0EA, 0xF0EEE8, 0xF4F4F4},
                {0x181412, 0x141212, 0x1A1614}, {0x8EC8EC, 0xF0EEE8, 0xF4F4F4}};
        for (int i = 0; i < stands.length; i++) {
            b.set(stands[i][0], 0, stands[i][1], Blocks.QUARTZ_PILLAR.getDefaultState());
            b.mannequinAt(stands[i][0], 1, stands[i][1], looks[i][0], looks[i][1], Items.LEATHER_BOOTS, looks[i][2],
                    i == 1 ? Items.LEATHER_HELMET : null, 0x161616, i < 3 ? 90 : 270);
        }
        // зеркала в золотых рамах
        b.mirror(-9, -6, Blocks.GOLD_BLOCK);
        b.mirror(-9, -10, Blocks.GOLD_BLOCK);
        b.mirror(9, -12, Blocks.GOLD_BLOCK);
        // диван для ожидания
        for (int z = -6; z >= -8; z--) b.stairs(8, 0, z, Direction.WEST, Blocks.DARK_OAK_STAIRS);
        b.set(7, 0, -10, Blocks.POTTED_BAMBOO.getDefaultState());
        b.set(-8, 0, -14, Blocks.POTTED_BAMBOO.getDefaultState());
        // касса из чёрного камня с золотом
        b.counter(2, 7, -13, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
        b.set(2, 0, -12, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(7, 0, -12, Blocks.GOLD_BLOCK.getDefaultState());
        // люстры
        for (int[] l : new int[][]{{-4, -5}, {4, -5}, {0, -10}}) {
            b.set(l[0], 5, l[1], Blocks.CHAIN.getDefaultState());
            b.set(l[0], 4, l[1], Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        }
        b.lights(Blocks.SEA_LANTERN.getDefaultState());
        b.neonLine(NeonBlock.Color.PURPLE);
        b.label(0, 8, 0, StyleCatalog.LUX_NAME.toUpperCase() + " · одежда класса люкс", Formatting.LIGHT_PURPLE);
        b.npc(4, -14, CitizenEntity.RANDOM_FROM + 14, CitizenEntity.Role.BOUTIQUE, "Стилист Виолетта");
    }

    // ------------------------------------------------------------------ цирюльня
    public static void barber(ServerWorld world, BlockPos origin, Direction facing) {
        R b = new R(world, origin, facing);
        b.shell(Blocks.CYAN_TERRACOTTA.getDefaultState(), Blocks.WHITE_CONCRETE.getDefaultState(), Blocks.WHITE_TERRACOTTA.getDefaultState(),
                Blocks.WHITE_CONCRETE.getDefaultState(), Blocks.BLACK_CONCRETE.getDefaultState());
        // шахматный пол
        for (int x = -9; x <= 9; x++)
            for (int z = -15; z <= -1; z++)
                b.set(x, -1, z, ((x + z) & 1) == 0 ? Blocks.BLACK_CONCRETE.getDefaultState() : Blocks.WHITE_CONCRETE.getDefaultState());
        // полосатый столб цирюльника у входа
        for (int y = 0; y <= 3; y++) {
            b.set(-3, y, 1, (y % 3 == 0 ? Blocks.RED_WOOL : y % 3 == 1 ? Blocks.WHITE_WOOL : Blocks.BLUE_WOOL).getDefaultState());
            b.set(3, y, 1, (y % 3 == 1 ? Blocks.RED_WOOL : y % 3 == 2 ? Blocks.WHITE_WOOL : Blocks.BLUE_WOOL).getDefaultState());
        }
        b.awning(-2, 2, Blocks.RED_WOOL, Blocks.WHITE_WOOL);
        // три кресла перед зеркалами у левой стены, раковины
        for (int z : new int[]{-4, -8, -12}) {
            b.set(-7, 0, z, Blocks.IRON_BLOCK.getDefaultState());
            b.stairs(-7, 1, z, Direction.WEST, Blocks.RED_NETHER_BRICK_STAIRS);
            b.mirror(-9, z, Blocks.WHITE_CONCRETE);
            b.set(-9, 0, z + 1, Blocks.CAULDRON.getDefaultState());
            b.set(-9, 3, z, Blocks.SEA_LANTERN.getDefaultState());
        }
        // стойка с бритвами и банками, диван ожидания
        b.counter(3, 7, -14, Blocks.WHITE_CONCRETE.getDefaultState(), Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
        b.set(4, 1, -14, Blocks.BREWING_STAND.getDefaultState());
        b.set(6, 1, -14, Blocks.FLOWER_POT.getDefaultState());
        for (int z = -3; z >= -7; z--) b.stairs(8, 0, z, Direction.WEST, Blocks.DARK_OAK_STAIRS);
        b.set(8, 0, -9, Blocks.BOOKSHELF.getDefaultState());
        b.set(8, 1, -9, Blocks.JUKEBOX.getDefaultState());
        b.lights(Blocks.SEA_LANTERN.getDefaultState());
        b.neonLine(NeonBlock.Color.CYAN);
        b.label(0, 8, 0, "✂ " + StyleCatalog.BARBER_NAME.toUpperCase() + " · стрижки и бороды", Formatting.AQUA);
        b.npc(-6, -6, CitizenEntity.RANDOM_FROM + 21, CitizenEntity.Role.BARBER, "Цирюльник Арсен", 270);
    }

    // ------------------------------------------------------------------ зал «Железо»
    public static void gym(ServerWorld world, BlockPos origin, Direction facing) {
        R b = new R(world, origin, facing);
        b.shell(Blocks.GRAY_CONCRETE.getDefaultState(), Blocks.BLACK_CONCRETE.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState(),
                Blocks.GRAY_CONCRETE.getDefaultState(), Blocks.YELLOW_CONCRETE.getDefaultState());
        for (int x = -10; x <= 10; x++) b.set(x, 4, 0, (x & 1) == 0 ? Blocks.YELLOW_CONCRETE.getDefaultState() : Blocks.BLACK_CONCRETE.getDefaultState());
        // резиновые маты
        for (int x = -8; x <= -2; x++) for (int z = -14; z <= -3; z++) b.set(x, -1, z, Blocks.BLACK_CONCRETE.getDefaultState());
        // штанги и беговые дорожки (нажми — откроется тренировка)
        for (int z : new int[]{-4, -7, -10, -13}) {
            b.facing(-7, 0, z, ModContent.BARBELL, Direction.EAST);
            b.stairs(-5, 0, z, Direction.WEST, Blocks.STONE_BRICK_STAIRS);
        }
        for (int x : new int[]{3, 5, 7}) b.facing(x, 0, -3, ModContent.TREADMILL, Direction.SOUTH);
        // ринг с канатами
        for (int x = 2; x <= 8; x++) for (int z = -14; z <= -8; z++) b.set(x, 0, z, Blocks.BLUE_CARPET.getDefaultState());
        for (int[] c : new int[][]{{2, -14}, {8, -14}, {2, -8}, {8, -8}}) b.fill(c[0], 0, c[1], c[0], 1, c[1], Blocks.RED_CONCRETE.getDefaultState());
        for (int x = 3; x <= 7; x++) {
            b.set(x, 1, -14, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
            if (x != 5) b.set(x, 1, -8, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
        }
        for (int z = -13; z <= -9; z++) {
            b.set(2, 1, z, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.Z));
            b.set(8, 1, z, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.Z));
        }
        // груши на цепях
        for (int x : new int[]{-1, 1}) {
            b.fill(x, 4, -15 + 2, x, 5, -15 + 2, Blocks.CHAIN.getDefaultState());
            b.fill(x, 2, -13, x, 3, -13, Blocks.BLACK_WOOL.getDefaultState());
        }
        // стойка с гантелями
        b.fill(9, 0, -12, 9, 0, -4, Blocks.IRON_BLOCK.getDefaultState());
        for (int z = -12; z <= -4; z += 2) b.set(9, 1, z, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.getDefaultState());
        b.mirror(9, -6, Blocks.BLACK_CONCRETE);
        b.mirror(9, -10, Blocks.BLACK_CONCRETE);
        b.lights(Blocks.SEA_LANTERN.getDefaultState());
        b.neonLine(NeonBlock.Color.YELLOW);
        b.label(0, 8, 0, StyleCatalog.GYM_NAME.toUpperCase() + " · сила и выносливость", Formatting.YELLOW);
        b.npc(0, -6, CitizenEntity.RANDOM_FROM + 27, CitizenEntity.Role.COACH, "Тренер Борис");
    }

    // ------------------------------------------------------------------ строитель в локальных координатах
    /** Фасад к +z (на улицу), вход — в (0, 0, 0); поворот по facing применяется ко всему. */
    private static final class R {
        final ServerWorld world;
        final BlockPos o;
        final BlockRotation rot;

        R(ServerWorld world, BlockPos origin, Direction facing) {
            this.world = world;
            this.o = origin;
            this.rot = switch (facing) {
                case WEST -> BlockRotation.CLOCKWISE_90;
                case NORTH -> BlockRotation.CLOCKWISE_180;
                case EAST -> BlockRotation.COUNTERCLOCKWISE_90;
                default -> BlockRotation.NONE;
            };
        }

        BlockPos pos(int x, int y, int z) {
            int[] p = switch (rot) {
                case CLOCKWISE_90 -> new int[]{-z, x};
                case CLOCKWISE_180 -> new int[]{-x, -z};
                case COUNTERCLOCKWISE_90 -> new int[]{z, -x};
                default -> new int[]{x, z};
            };
            return o.add(p[0], y, p[1]);
        }

        float yaw(float local) {
            return local + switch (rot) {
                case CLOCKWISE_90 -> 90;
                case CLOCKWISE_180 -> 180;
                case COUNTERCLOCKWISE_90 -> 270;
                default -> 0;
            };
        }

        void set(int x, int y, int z, BlockState s) {
            world.setBlockState(pos(x, y, z), s.rotate(rot), FLAGS);
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
        }

        void stairs(int x, int y, int z, Direction facing, Block stairs) {
            set(x, y, z, stairs.getDefaultState().with(StairsBlock.FACING, facing).with(StairsBlock.HALF, BlockHalf.BOTTOM));
        }

        void facing(int x, int y, int z, Block block, Direction d) {
            BlockState s = block.getDefaultState();
            if (s.contains(Properties.HORIZONTAL_FACING)) s = s.with(Properties.HORIZONTAL_FACING, d);
            else if (s.contains(Properties.FACING)) s = s.with(Properties.FACING, d);
            set(x, y, z, s);
        }

        /** Коробка здания: стены, углы, крыша с парапетом, витрины, вход, тротуар; старые жители и манекены убираются. */
        void shell(BlockState wall, BlockState trim, BlockState inner, BlockState floor, BlockState accent) {
            Box area = new Box(pos(-11, -1, -17).toCenterPos(), pos(11, 10, 3).toCenterPos()).expand(1);
            for (Entity e : world.getEntitiesByClass(Entity.class, area, e -> e.getCommandTags().contains(TAG)
                    || e instanceof CitizenEntity c && c.getRole().ordinal() >= CitizenEntity.Role.TAILOR.ordinal())) e.discard();
            fill(-11, 0, -17, 11, 10, 4, Blocks.AIR.getDefaultState());
            fill(-10, -3, -16, 10, -2, 0, Blocks.STONE_BRICKS.getDefaultState());
            fill(-10, -1, -16, 10, -1, 0, floor);
            fill(-11, -1, 1, 11, -1, 3, Blocks.SMOOTH_STONE.getDefaultState());
            fill(-11, -2, 1, 11, -2, 3, Blocks.STONE.getDefaultState());
            fill(-10, 0, -16, 10, 5, -16, wall);
            fill(-10, 0, 0, 10, 5, 0, wall);
            fill(-10, 0, -16, -10, 5, 0, wall);
            fill(10, 0, -16, 10, 5, 0, wall);
            // внутренняя отделка
            fill(-9, 0, -15, 9, 4, -15, inner);
            fill(-9, 0, -15, -9, 4, -1, inner);
            fill(9, 0, -15, 9, 4, -1, inner);
            fill(-9, 0, -1, 9, 4, -1, Blocks.AIR.getDefaultState());
            for (int y = 0; y <= 5; y++) {
                set(-10, y, 0, trim);
                set(10, y, 0, trim);
                set(-10, y, -16, trim);
                set(10, y, -16, trim);
            }
            fill(-10, 6, -16, 10, 6, 0, trim);
            fill(-9, 5, -15, 9, 5, -1, inner);
            for (int x = -10; x <= 10; x++) {
                set(x, 7, 0, trim);
                set(x, 7, -16, trim);
            }
            for (int z = -16; z <= 0; z++) {
                set(-10, 7, z, trim);
                set(10, 7, z, trim);
            }
            // витрины и вход
            for (int x = -8; x <= 8; x++) if (Math.abs(x) > 2) fill(x, 1, 0, x, 3, 0, Blocks.GLASS.getDefaultState());
            fill(-8, 0, 0, 8, 0, 0, accent);
            fill(-1, 0, 0, 1, 2, 0, Blocks.AIR.getDefaultState());
            fill(-1, -1, 0, 1, -1, 0, floor);
        }

        void awning(int x1, int x2, Block a, Block c) {
            for (int x = x1; x <= x2; x++) {
                set(x, 4, 1, ((x & 1) == 0 ? a : c).getDefaultState());
                set(x, 4, 2, ((x & 1) == 0 ? a : c).getDefaultState());
            }
        }

        void lights(BlockState light) {
            for (int x = -6; x <= 6; x += 6)
                for (int z = -13; z <= -3; z += 5) set(x, 5, z, light);
        }

        void neonLine(NeonBlock.Color color) {
            for (int x = -10; x <= 10; x++)
                set(x, 5, 1, ModContent.NEON.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH).with(NeonBlock.COLOR, color));
        }

        /** Зеркало у стены: голубое стекло в раме. x = ±9 — у боковой стены, иначе — у задней. */
        void mirror(int x, int z, Block frame) {
            boolean side = Math.abs(x) == 9;
            for (int y = 0; y <= 3; y++)
                for (int d = -1; d <= 1; d++) {
                    int xx = side ? x : x + d, zz = side ? z + d : z;
                    boolean edge = y == 0 || y == 3 || d != 0;
                    set(xx, y, zz, edge ? frame.getDefaultState() : Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState());
                }
        }

        void counter(int x1, int x2, int z, BlockState body, BlockState top) {
            fill(x1, 0, z, x2, 0, z, body);
            fill(x1, 1, z, x2, 1, z, top);
        }

        void rack(int x1, int x2, int z, Block fence) {
            set(x1, 0, z, fence.getDefaultState());
            set(x1, 1, z, fence.getDefaultState());
            set(x2, 0, z, fence.getDefaultState());
            set(x2, 1, z, fence.getDefaultState());
            for (int x = x1; x <= x2; x++) set(x, 2, z, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
        }

        void mannequin(int x, int z, int shirt, int pants, Item boots, int bootsColor, Item hat, int hatColor, float yaw) {
            mannequinAt(x, 0, z, shirt, pants, boots, bootsColor, hat, hatColor, yaw);
        }

        void mannequin(int x, int z, int shirt, int pants, Item boots, int bootsColor, Item hat, int hatColor) {
            mannequinAt(x, 0, z, shirt, pants, boots, bootsColor, hat, hatColor, 0);
        }

        /** Манекен: стойка для брони в крашеной коже (рубашка, брюки, обувь, шапка). */
        void mannequinAt(int x, int y, int z, int shirt, int pants, Item boots, int bootsColor, Item hat, int hatColor, float yaw) {
            ArmorStandEntity a = EntityType.ARMOR_STAND.create(world);
            if (a == null) return;
            BlockPos p = pos(x, y, z);
            a.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw(yaw), 0);
            a.setShowArms(true);
            a.setHideBasePlate(true);
            a.setInvulnerable(true);
            a.setLeftArmRotation(new EulerAngle(-10, 0, -8));
            a.setRightArmRotation(new EulerAngle(-10, 0, 8));
            a.equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, shirt));
            a.equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, pants));
            a.equipStack(EquipmentSlot.FEET, dyed(boots, bootsColor));
            if (hat != null) a.equipStack(EquipmentSlot.HEAD, dyed(hat, hatColor));
            a.addCommandTag(TAG);
            world.spawnEntity(a);
        }

        static ItemStack dyed(Item item, int rgb) {
            ItemStack s = new ItemStack(item);
            s.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(rgb, false));
            return s;
        }

        void npc(int x, int z, int skin, CitizenEntity.Role role, String name) {
            npc(x, z, skin, role, name, 0);
        }

        /** Продавец стоит на месте и смотрит на вход (yaw 0 — к фасаду). */
        void npc(int x, int z, int skin, CitizenEntity.Role role, String name, float localYaw) {
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, world);
            BlockPos p = pos(x, 0, z);
            float yaw = yaw(localYaw);
            c.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0);
            c.setHeadYaw(yaw);
            c.setBodyYaw(yaw);
            c.setup(skin, role, true, name);
            world.spawnEntity(c);
        }

        /** Большая светящаяся надпись над входом. */
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
            tr.put("scale", floats(2.2f, 2.2f, 2.2f));
            tr.put("right_rotation", floats(0, 0, 0, 1));
            n.put("transformation", tr);
            NbtCompound br = new NbtCompound();
            br.putInt("block", 15);
            br.putInt("sky", 15);
            n.put("brightness", br);
            d.readNbt(n);
            BlockPos p = pos(x, y, z);
            d.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            d.addCommandTag(TAG);
            world.spawnEntity(d);
        }

        private static NbtList floats(float... v) {
            NbtList l = new NbtList();
            for (float f : v) l.add(NbtFloat.of(f));
            return l;
        }
    }
}
