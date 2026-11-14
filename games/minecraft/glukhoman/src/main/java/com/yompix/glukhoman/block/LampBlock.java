package com.yompix.glukhoman.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** Лампочка под потолком / уличный фонарь. LIT переключает режиссёр страха и подстанция (мерцание, отключения). */
public class LampBlock extends Block {
    public static final BooleanProperty LIT = BooleanProperty.of("lit");
    private static final VoxelShape SHAPE = com.yompix.glukhoman.GContent.box(5, 6, 5, 11, 16, 11);

    public LampBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return null;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
        b.add(LIT);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return SHAPE;
    }
}
