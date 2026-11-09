package com.yompix.novograd.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;

/**
 * Рекламный щит 6×3 блока: AD — какая реклама, TILE — кусок картинки (ряд × 6 + столбец, слева направо для зрителя, сверху вниз).
 * FACING — куда смотрит лицо щита.
 */
public class BillboardBlock extends FacingBlock {
    public static final MapCodec<BillboardBlock> CODEC = createCodec(BillboardBlock::new);
    public static final int ADS = 6, W = 6, H = 3;
    public static final IntProperty AD = IntProperty.of("ad", 0, ADS - 1);
    public static final IntProperty TILE = IntProperty.of("tile", 0, W * H - 1);

    public BillboardBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(AD, 0).with(TILE, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(AD, TILE);
    }
}
