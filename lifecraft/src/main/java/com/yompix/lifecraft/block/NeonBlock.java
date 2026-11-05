package com.yompix.lifecraft.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/** Неоновая трубка: сама загорается вечером и гаснет утром. На стене или на земле, восемь цветов. */
public class NeonBlock extends FacingBlock {
    public enum Color implements StringIdentifiable {
        PINK, PURPLE, CYAN, BLUE, YELLOW, GREEN, RED, ORANGE;

        @Override
        public String asString() {
            return name().toLowerCase();
        }
    }

    public static final EnumProperty<Color> COLOR = EnumProperty.of("color", Color.class);
    public static final BooleanProperty LIT = BooleanProperty.of("lit");

    public NeonBlock(Settings settings, VoxelShape north) {
        super(settings, north);
        setDefaultState(getDefaultState().with(COLOR, Color.PINK).with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(COLOR, LIT);
    }

    public static boolean night(World w) {
        long t = w.getTimeOfDay() % 24000;
        return t > 11800 && t < 23400;
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!oldState.isOf(this)) world.scheduleBlockTick(pos, this, 20 + world.getRandom().nextInt(40));
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        boolean lit = night(world);
        if (lit != state.get(LIT)) world.setBlockState(pos, state.with(LIT, lit), Block.NOTIFY_LISTENERS);
        world.scheduleBlockTick(pos, this, 80 + random.nextInt(60));
    }
}
