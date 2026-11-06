package com.yompix.novograd.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;

/**
 * Пол кабины лифта (он же потолок кабины этажом ниже). FACING — куда выходить из кабины (к дверям),
 * ROOF — остановка «Крыша». Все такие полы в одном столбце — это этажи одного лифта.
 */
public class ElevatorFloorBlock extends FacingBlock {
    public static final MapCodec<ElevatorFloorBlock> CODEC = createCodec(ElevatorFloorBlock::new);
    public static final BooleanProperty ROOF = BooleanProperty.of("roof");

    public ElevatorFloorBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(ROOF, false));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(ROOF);
    }
}
