package com.yompix.glukhoman.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.shape.VoxelShape;

/** Обстановка с вариантами: плакат «Пропал человек», царапины, кукла, фотография (меняется, пока не смотришь). */
public class DecorBlock extends PropBlock {
    public static final IntProperty VARIANT = IntProperty.of("variant", 0, 2);

    public DecorBlock(Settings settings, VoxelShape southShape) {
        super(settings, southShape);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
        super.appendProperties(b);
        b.add(VARIANT);
    }
}
