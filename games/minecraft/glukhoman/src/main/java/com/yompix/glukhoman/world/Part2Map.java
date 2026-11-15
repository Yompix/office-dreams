package com.yompix.glukhoman.world;

import com.yompix.glukhoman.story.StoryState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static com.yompix.glukhoman.world.Terrain.G;
import static com.yompix.glukhoman.world.MapBuilder.LZ;

/**
 * Часть вторая (0.6): то, чего не было в 0.5 — достраивается в любом мире, в том числе в старых сохранениях (флаг «v3»).
 * <ul>
 *   <li>надписи на шпалах восточнее переезда — «вырезаны ножом», видны только в видоискателе в ИК (TextDisplay с меткой ‡);</li>
 *   <li>перегон до 41-го км: пикеты 60–67, куски уцелевших рельсов (стук на стыках), 3-я стрелка с ответвлением к яме;</li>
 *   <li>41-й км: развалины лагпункта — барак (спрятаться: присесть), вышка, нарядная (стол, гроссбух, полки с кассетами).</li>
 * </ul>
 */
public final class Part2Map {
    private Part2Map() {}

    public static final int CAMP_X = 396, END_X = 392, SWITCH_X = 300;
    /** Уцелевшие куски рельсов на перегоне: здесь стучат стыки. */
    public static final int[][] RAILS = {{140, 176}, {226, 268}, {330, 360}};

    /** Имена на шпалах: {x, текст, id}. Четвёртое — ложное: живой человек (Петрович) с номером из списка. */
    public static final Object[][] CARVINGS = {{76, "ГРИНЮК С. П. · 38", "carve_1"}, {84, "ГЛОТОВ В. П. · 37", "carve_false"},
            {93, "КРАВЕЦ И. · 39", "carve_2"}, {104, "ЛЕЩЕНКО М. А. · 40", "carve_3"}};

    public static void build(ServerWorld w, StoryState st) {
        B b = new B(w, st);
        for (int x = 60; x <= 460; x += 16) for (int z = -16; z <= 48; z += 16) w.getChunk(x >> 4, z >> 4);
        // надписи на шпалах
        for (Object[] c : CARVINGS) {
            int x = (Integer) c[0];
            DisplayEntity.TextDisplayEntity t = EntityType.TEXT_DISPLAY.create(w);
            if (t == null) continue;
            t.refreshPositionAndAngles(x + 0.5, G + 2.22, LZ + 0.5, 0, -90);
            var nbt = new net.minecraft.nbt.NbtCompound();
            t.writeNbt(nbt);
            nbt.putString("text", Text.Serialization.toJsonString(Text.literal("‡ " + c[1]), w.getRegistryManager()));
            nbt.putString("billboard", "fixed");
            nbt.putInt("background", 0);
            nbt.putFloat("view_range", 0.6f);
            nbt.putBoolean("shadow", false);
            t.readNbt(nbt);
            t.setYaw(0);
            t.setPitch(-90);
            t.addCommandTag("glk_carving");
            w.spawnEntity(t);
            b.point((String) c[2], x, G + 2, LZ);
        }
        // пикеты 60–67 и уцелевшие рельсы
        int n = 60;
        for (int x = 180; x <= 376; x += 28) {
            b.set(x, G + 1, LZ + 3, Blocks.GRAVEL);
            b.set(x, G + 2, LZ + 3, Blocks.STRIPPED_SPRUCE_LOG);
            b.set(x, G + 3, LZ + 3, Blocks.SPRUCE_SIGN.getDefaultState().with(net.minecraft.block.SignBlock.ROTATION, 4));
            if (w.getBlockEntity(new BlockPos(x, G + 3, LZ + 3)) instanceof net.minecraft.block.entity.SignBlockEntity sb) {
                Text t = Text.literal(Integer.toString(n));
                sb.setText(new net.minecraft.block.entity.SignText().withMessage(1, t), true);
                sb.setText(new net.minecraft.block.entity.SignText().withMessage(1, t), false);
            }
            n++;
        }
        for (int[] r : RAILS) for (int x = r[0]; x <= r[1]; x++)
            b.set(x, G + 2, LZ, Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST));
        // 3-я стрелка: ответвление вправо (на юг) к яме — шпалы и обрыв
        for (int i = 1; i <= 14; i++) {
            int x = SWITCH_X + i, z = LZ + 1 + i / 2;
            b.set(x, G + 1, z, Blocks.GRAVEL);
            if ((i & 1) == 0) b.set(x, G + 2, z, Blocks.SPRUCE_TRAPDOOR);
        }
        b.fill(SWITCH_X + 12, G - 6, LZ + 6, SWITCH_X + 18, G + 1, LZ + 12, Blocks.AIR);
        b.point("c3_switch", SWITCH_X, G + 2, LZ);
        b.point("c2_schedule", 21, G + 3, 39);   // расписание бабы Поли на стене (видно сквозь доски)
        b.point("c3_cart", 70, G + 2, LZ);
        b.point("c3_end", END_X, G + 2, LZ);
        // 41-й км: площадка
        b.ground(CAMP_X, -6, CAMP_X + 48, 38, G + 1, Blocks.COARSE_DIRT.getDefaultState(), 8);
        b.zone("camp", CAMP_X - 6, G - 4, -8, CAMP_X + 50, G + 20, 40);
        // барак: длинный, бревенчатый, с нарами (присесть на нарах — спрятаться)
        int bx = CAMP_X + 4, bz = 22;
        b.fill(bx, G + 1, bz, bx + 16, G + 1, bz + 7, Blocks.SPRUCE_PLANKS);
        b.walls(bx, G + 2, bz, bx + 16, G + 4, bz + 7, Blocks.DARK_OAK_LOG.getDefaultState());
        b.fill(bx, G + 5, bz, bx + 16, G + 5, bz + 7, Blocks.DARK_OAK_SLAB.getDefaultState());
        b.air(bx + 7, G + 2, bz, bx + 8, G + 3, bz);   // проём без двери
        for (int x = bx + 2; x <= bx + 14; x += 3) {
            b.set(x, G + 2, bz + 6, Blocks.SPRUCE_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.TOP));
            b.set(x, G + 3, bz + 6, Blocks.SPRUCE_SLAB);
        }
        b.zone("barrack", bx + 1, G + 1, bz + 1, bx + 15, G + 5, bz + 6);
        // вышка
        int tx = CAMP_X + 30, tz = 32;
        for (int y = G + 2; y <= G + 10; y++) for (int[] c : new int[][]{{0, 0}, {3, 0}, {0, 3}, {3, 3}}) b.set(tx + c[0], y, tz + c[1], Blocks.STRIPPED_DARK_OAK_LOG);
        b.fill(tx, G + 10, tz, tx + 3, G + 10, tz + 3, Blocks.DARK_OAK_PLANKS);
        b.lantern(tx + 1, G + 9, tz + 1, true);
        // нарядная: стол с гроссбухом, полки с кассетами, таблички с именами
        int nx = CAMP_X + 26, nz = 4;
        b.fill(nx, G + 1, nz, nx + 9, G + 1, nz + 7, Blocks.SPRUCE_PLANKS);
        b.walls(nx, G + 2, nz, nx + 9, G + 4, nz + 7, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState());
        b.fill(nx - 1, G + 5, nz - 1, nx + 10, G + 5, nz + 8, Blocks.SPRUCE_SLAB.getDefaultState());
        b.door(nx + 4, G + 2, nz + 7, Direction.SOUTH, Blocks.SPRUCE_DOOR, false);
        b.set(nx + 4, G + 2, nz + 3, Blocks.LECTERN);   // гроссбух 1953 года
        b.set(nx + 3, G + 2, nz + 3, Blocks.SPRUCE_PLANKS);
        b.set(nx + 5, G + 2, nz + 3, Blocks.SPRUCE_PLANKS);
        for (int x = nx + 1; x <= nx + 8; x++) for (int y = G + 2; y <= G + 4; y++) b.set(x, y, nz + 1, Blocks.CHISELED_BOOKSHELF);
        b.zone("naryad", nx + 1, G + 1, nz + 1, nx + 8, G + 5, nz + 6);
        b.point("naryad_in", nx + 4, G + 2, nz + 5);
        b.point("naryad_table", nx + 4, G + 2, nz + 3);
        String[][] labels = {{"ПЕЛАГЕЯ Т.", "38"}, {"ГЛОТОВ В.", "37"}, {"ЗИНАИДА Г.", "36"}, {"ВИКТОРИЯ С.", "35"}, {"", "40"}};
        for (int i = 0; i < labels.length; i++) {
            int x = nx + 1 + i * 2;
            b.set(x, G + 4, nz + 2, Blocks.SPRUCE_WALL_SIGN.getDefaultState().with(net.minecraft.block.WallSignBlock.FACING, Direction.SOUTH));
            if (w.getBlockEntity(new BlockPos(x, G + 4, nz + 2)) instanceof net.minecraft.block.entity.SignBlockEntity sb) {
                var t = new net.minecraft.block.entity.SignText().withMessage(1, Text.literal(labels[i][0])).withMessage(2, Text.literal("— " + labels[i][1]));
                sb.setText(t, true);
                sb.setText(t, false);
            }
        }
        b.point("naryad_mine", nx + 6, G + 3, nz + 2);   // сюда — свежая табличка с именем игрока (при входе)
        b.point("camp_w", CAMP_X - 2, G + 2, LZ);
        b.point("camp_e", CAMP_X + 46, G + 2, LZ);
        st.set("v3");
        st.markDirty();
    }

    /** Свежая табличка: имя игрока, место 1 (ставится при входе в нарядную). */
    public static void nameSign(ServerWorld w, BlockPos at, String name) {
        w.setBlockState(at, Blocks.SPRUCE_WALL_SIGN.getDefaultState().with(net.minecraft.block.WallSignBlock.FACING, Direction.SOUTH));
        if (w.getBlockEntity(at) instanceof net.minecraft.block.entity.SignBlockEntity sb) {
            var t = new net.minecraft.block.entity.SignText().withMessage(1, Text.literal(name.toUpperCase())).withMessage(2, Text.literal("— 1"));
            sb.setText(t, true);
            sb.setText(t, false);
        }
    }
}
