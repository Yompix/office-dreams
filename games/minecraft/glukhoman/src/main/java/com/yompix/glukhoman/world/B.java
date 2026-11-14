package com.yompix.glukhoman.world;

import com.yompix.glukhoman.block.LampBlock;
import com.yompix.glukhoman.block.PropBlock;
import com.yompix.glukhoman.story.StoryState;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/** Кисть для строительства карты: короткие команды поверх world.setBlockState без лишних обновлений. */
public class B {
    public final ServerWorld w;
    public final StoryState st;
    public final Random rnd = Random.create(1996);
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private final BlockPos.Mutable m = new BlockPos.Mutable();

    public B(ServerWorld w, StoryState st) {
        this.w = w;
        this.st = st;
    }

    public void set(int x, int y, int z, BlockState s) {
        w.setBlockState(m.set(x, y, z), s, FLAGS);
    }

    public void set(int x, int y, int z, Block b) {
        set(x, y, z, b.getDefaultState());
    }

    public BlockState get(int x, int y, int z) {
        return w.getBlockState(m.set(x, y, z));
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        fill(x1, y1, z1, x2, y2, z2, b.getDefaultState());
    }

    public void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Blocks.AIR);
    }

    /** Стены коробки (без пола и потолка). */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        fill(x1, y1, z1, x2, y2, z1, s);
        fill(x1, y1, z2, x2, y2, z2, s);
        fill(x1, y1, z1, x1, y2, z2, s);
        fill(x2, y1, z1, x2, y2, z2, s);
    }

    /** Площадка: выровнять землю на уровне y (под ней земля до y-4), выше — воздух. */
    public void ground(int x1, int z1, int x2, int z2, int y, BlockState top, int clear) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int yy = y - 4; yy < y; yy++) set(x, yy, z, Blocks.DIRT);
                set(x, y, z, top);
                for (int yy = y + 1; yy <= y + clear; yy++) set(x, yy, z, Blocks.AIR);
            }
    }

    public void door(int x, int y, int z, Direction facing, Block door, boolean open) {
        BlockState s = door.getDefaultState().with(DoorBlock.FACING, facing).with(DoorBlock.HINGE, DoorHinge.LEFT).with(DoorBlock.OPEN, open);
        set(x, y, z, s.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(x, y + 1, z, s.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    public void bed(int x, int y, int z, Direction facing, Block bed) {
        BlockState s = bed.getDefaultState().with(BedBlock.FACING, facing);
        set(x, y, z, s.with(BedBlock.PART, BedPart.FOOT));
        Direction d = facing;
        set(x + d.getOffsetX(), y, z + d.getOffsetZ(), s.with(BedBlock.PART, BedPart.HEAD));
    }

    public void stairs(int x, int y, int z, Block b, Direction facing, boolean top) {
        set(x, y, z, b.getDefaultState().with(StairsBlock.FACING, facing).with(StairsBlock.HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM));
    }

    public void prop(int x, int y, int z, Block b, Direction facing) {
        BlockState s = b.getDefaultState();
        if (s.contains(HorizontalFacingBlock.FACING)) s = s.with(HorizontalFacingBlock.FACING, facing);
        set(x, y, z, s);
    }

    /** Интерактивная вещь: блок + ключ действия в сюжете. */
    public void use(int x, int y, int z, Block b, Direction facing, String key) {
        prop(x, y, z, b, facing);
        st.bind(new BlockPos(x, y, z), key);
    }

    public void lamp(int x, int y, int z, String group, boolean lit) {
        set(x, y, z, com.yompix.glukhoman.GContent.LAMP.getDefaultState().with(LampBlock.LIT, lit));
        st.lamp(group, new BlockPos(x, y, z));
    }

    public void lantern(int x, int y, int z, boolean hanging) {
        set(x, y, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, hanging));
    }

    public void point(String name, int x, int y, int z) {
        st.point(name, new BlockPos(x, y, z));
    }

    /** Зона (для триггеров): две противоположные точки. */
    public void zone(String name, int x1, int y1, int z1, int x2, int y2, int z2) {
        st.point(name + "_a", new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)));
        st.point(name + "_b", new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)));
    }

    public boolean chance(double p) {
        return rnd.nextDouble() < p;
    }

    public static Direction facingOf(PropBlock b) {
        return Direction.SOUTH;
    }
}
