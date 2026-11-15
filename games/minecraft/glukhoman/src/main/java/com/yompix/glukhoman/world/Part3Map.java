package com.yompix.glukhoman.world;

import com.yompix.glukhoman.story.Names;
import com.yompix.glukhoman.story.StoryState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import static com.yompix.glukhoman.world.MapBuilder.LZ;
import static com.yompix.glukhoman.world.Terrain.G;

/**
 * Часть третья (0.7): улики «Имён сорока» (10 надписей в ИК на шпалах и столбах, 10 бирок глазами — по 4 имени, раскладка своя
 * в каждом мире) и «внутри поезда» — четыре вагона на отдельной площадке (теплушка, вагон с кассетами, пассажирский, «место 40»).
 * Достраивается и в старых мирах (флаг «v4»).
 */
public final class Part3Map {
    private Part3Map() {}

    /** Надписи (ИК): x на шпалах; последняя — у барака на 41-м км. */
    public static final int[][] CARV = {{112, 66, LZ}, {140, 66, LZ}, {168, 66, LZ}, {196, 66, LZ}, {224, 66, LZ}, {252, 66, LZ},
            {280, 66, LZ}, {316, 66, LZ}, {372, 66, LZ}, {Part2Map.CAMP_X + 12, 66, 20}};
    /** Бирки (глазами): стены вокзала, переезд, дома, кладбище, барак. */
    public static final double[][] TAGS = {{-8.5, 67.6, 20.6}, {-1.5, 67.6, 20.6}, {4.5, 67.6, 20.6}, {28.5, 67.8, 19.6}, {10.5, 67.2, 33.6},
            {-17.5, 67.2, 38.6}, {14.5, 67.2, 39.4}, {-39.5, 67.2, 71.5}, {Part2Map.CAMP_X + 8.5, 67.6, 21.6}, {Part2Map.CAMP_X + 30.5, 67.6, 12.6}};

    /** Вагоны поезда изнутри: x0 каждого вагона, общий z0 и высота пола. */
    public static final int CX = 150, CZ = -40, CY = G + 24;

    public static void build(ServerWorld w, StoryState st) {
        B b = new B(w, st);
        int[][] lc = Names.layout(st, true), lt = Names.layout(st, false);
        for (int i = 0; i < Names.SPOTS; i++) {
            int[] c = CARV[i];
            w.getChunk(c[0] >> 4, c[2] >> 4);
            StringBuilder s = new StringBuilder("‡ ");
            for (int n : lc[i]) s.append(Names.surname(n)).append("  ");
            text(w, s.toString().trim(), c[0] + 0.5, c[1] + 0.22, c[2] + 0.5, -90, 0.6f, "fixed");
            b.point("nc_" + i, c[0], c[1], c[2]);
            double[] t = TAGS[i];
            w.getChunk((int) Math.floor(t[0]) >> 4, (int) Math.floor(t[2]) >> 4);
            StringBuilder q = new StringBuilder("бирка: ");
            for (int n : lt[i]) q.append("№").append(n).append(' ');
            text(w, q.toString().trim(), t[0], t[1], t[2], 0, 0.45f, "center");
            b.point("nt_" + i, (int) Math.floor(t[0]), (int) Math.floor(t[1]), (int) Math.floor(t[2]));
        }
        // вагоны: 4 коробки 10×4, между ними тамбуры
        for (int k = 0; k < 4; k++) {
            int x0 = CX + k * 12;
            for (int x = x0; x <= x0 + 10; x += 16) w.getChunk(x >> 4, CZ >> 4);
            b.fill(x0, CY, CZ, x0 + 10, CY + 5, CZ + 5, Blocks.DARK_OAK_PLANKS.getDefaultState());
            b.air(x0 + 1, CY + 1, CZ + 1, x0 + 9, CY + 4, CZ + 4);
            for (int x = x0 + 2; x <= x0 + 8; x += 3) b.set(x, CY + 3, CZ, Blocks.BLACK_STAINED_GLASS_PANE.getDefaultState());
            b.lantern(x0 + 5, CY + 4, CZ + 2, true);
            b.zone("car" + (k + 1), x0 + 1, CY, CZ + 1, x0 + 9, CY + 5, CZ + 4);
            b.point("car" + (k + 1), x0 + 2, CY + 1, CZ + 2);
            b.zone("car" + (k + 1) + "_exit", x0 + 8, CY, CZ + 1, x0 + 9, CY + 5, CZ + 4);
            if (k == 0) for (int x = x0 + 2; x <= x0 + 8; x += 2) b.set(x, CY + 1, CZ + 4, Blocks.SPRUCE_SLAB.getDefaultState());   // нары
            if (k == 1) for (int x = x0 + 1; x <= x0 + 9; x++) b.set(x, CY + 2, CZ + 1, Blocks.JUKEBOX.getDefaultState());       // «магнитофоны»
            if (k == 2) b.set(x0 + 5, CY + 1, CZ + 1, Blocks.SPRUCE_STAIRS.getDefaultState());
            if (k == 3) {
                b.set(x0 + 5, CY + 1, CZ + 1, Blocks.SPRUCE_STAIRS.getDefaultState());
                b.set(x0 + 5, CY + 1, CZ + 4, Blocks.SPRUCE_STAIRS.getDefaultState().with(net.minecraft.block.StairsBlock.FACING, net.minecraft.util.math.Direction.SOUTH));
                b.point("car4_seat", x0 + 5, CY + 1, CZ + 4);
            }
        }
        st.set("v4");
        st.markDirty();
    }

    /** 1.0: доска объявлений с фотографиями у колонки; детали вагонов (магнитофоны, багаж, лампы). Флаг «v5». */
    public static void build5(ServerWorld w, StoryState st) {
        B b = new B(w, st);
        w.getChunk(0, 1);
        // доска: два столба, щит, четыре «фотокарточки» (таблички — их текст меняет сюжет)
        int bx = 6, bz = 30;
        for (int y = 66; y <= 68; y++) {
            b.set(bx - 1, y, bz, Blocks.SPRUCE_FENCE.getDefaultState());
            b.set(bx + 4, y, bz, Blocks.SPRUCE_FENCE.getDefaultState());
        }
        b.fill(bx - 1, 69, bz, bx + 4, 69, bz, Blocks.SPRUCE_SLAB.getDefaultState());
        b.fill(bx, 67, bz, bx + 3, 68, bz, Blocks.SPRUCE_PLANKS.getDefaultState());
        for (int i = 0; i < 4; i++) {
            b.set(bx + i, 67, bz - 1, Blocks.SPRUCE_WALL_SIGN.getDefaultState().with(net.minecraft.block.WallSignBlock.FACING, net.minecraft.util.math.Direction.NORTH));
            b.point("photo_" + i, bx + i, 67, bz - 1);
        }
        // вагоны: убрать «музыкальные автоматы», поставить магнитофоны рядами, багаж, лампы
        for (int k = 0; k < 4; k++) {
            int x0 = CX + k * 12;
            for (int x = x0 + 1; x <= x0 + 9; x++) if (w.getBlockState(new net.minecraft.util.math.BlockPos(x, CY + 2, CZ + 1)).isOf(Blocks.JUKEBOX)) b.set(x, CY + 2, CZ + 1, Blocks.AIR.getDefaultState());
            b.lantern(x0 + 2, CY + 4, CZ + 3, true);
            b.lantern(x0 + 8, CY + 4, CZ + 3, true);
            if (k == 0 || k == 2) {   // багаж: мешки, чемоданы
                b.set(x0 + 9, CY + 1, CZ + 1, Blocks.BARREL.getDefaultState());
                b.set(x0 + 9, CY + 1, CZ + 2, Blocks.CHEST.getDefaultState());
                b.set(x0 + 1, CY + 1, CZ + 1, Blocks.BROWN_WOOL.getDefaultState());
            }
            if (k == 1) for (int x = x0 + 1; x <= x0 + 9; x++) {   // магнитофоны на полках в два ряда
                b.set(x, CY + 1, CZ + 1, Blocks.SPRUCE_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.TOP));
                b.prop(x, CY + 2, CZ + 1, com.yompix.glukhoman.GContent.RADIO, net.minecraft.util.math.Direction.SOUTH);
                b.set(x, CY + 3, CZ + 1, Blocks.SPRUCE_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.TOP));
                if (x % 2 == 0) b.prop(x, CY + 4, CZ + 1, com.yompix.glukhoman.GContent.RADIO, net.minecraft.util.math.Direction.SOUTH);
            }
        }
        st.set("v5");
        st.markDirty();
    }

    static void text(ServerWorld w, String s, double x, double y, double z, float pitch, float scale, String billboard) {
        DisplayEntity.TextDisplayEntity t = EntityType.TEXT_DISPLAY.create(w);
        if (t == null) return;
        t.refreshPositionAndAngles(x, y, z, 0, pitch);
        var nbt = new net.minecraft.nbt.NbtCompound();
        t.writeNbt(nbt);
        nbt.putString("text", Text.Serialization.toJsonString(Text.literal(s), w.getRegistryManager()));
        nbt.putString("billboard", billboard);
        nbt.putInt("background", billboard.equals("center") ? 0x90303830 : 0);
        nbt.putBoolean("shadow", false);
        var tr = new net.minecraft.nbt.NbtCompound();
        var f = (java.util.function.Function<float[], net.minecraft.nbt.NbtList>) a -> {
            var l = new net.minecraft.nbt.NbtList();
            for (float v : a) l.add(net.minecraft.nbt.NbtFloat.of(v));
            return l;
        };
        tr.put("left_rotation", f.apply(new float[]{0, 0, 0, 1}));
        tr.put("right_rotation", f.apply(new float[]{0, 0, 0, 1}));
        tr.put("translation", f.apply(new float[]{0, 0, 0}));
        tr.put("scale", f.apply(new float[]{scale, scale, scale}));
        nbt.put("transformation", tr);
        t.readNbt(nbt);
        t.setYaw(0);
        t.setPitch(pitch);
        t.addCommandTag("glk_evidence");
        w.spawnEntity(t);
    }
}
