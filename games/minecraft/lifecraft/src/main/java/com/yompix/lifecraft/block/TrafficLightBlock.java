package com.yompix.lifecraft.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Светофор: 0 красный, 1 жёлтый, 2 зелёный. Все светофоры города идут по общим часам, поперечные — в противофазе. */
public class TrafficLightBlock extends FacingBlock {
    public static final IntProperty LIGHT = IntProperty.of("light", 0, 2);

    public TrafficLightBlock(Settings settings) {
        super(settings, box(4, 0, 5, 12, 16, 11));
        setDefaultState(getDefaultState().with(LIGHT, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(LIGHT);
    }

    static int lightAt(long time, Direction facing) {
        int s = (int) (time / 20 % 30);
        if (facing.getAxis() == Direction.Axis.X) s = (s + 15) % 30;
        if (s < 12) return 2;
        if (s < 15) return 1;
        return 0;
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.isOf(this)) world.scheduleBlockTick(pos, this, 20);
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        int l = lightAt(world.getTime(), state.get(FACING));
        if (l != state.get(LIGHT)) world.setBlockState(pos, state.with(LIGHT, l), Block.NOTIFY_LISTENERS);
        world.scheduleBlockTick(pos, this, 20 - (int) (world.getTime() % 20));
    }
}
