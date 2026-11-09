package com.yompix.novograd.block;

import com.mojang.serialization.MapCodec;
import com.yompix.novograd.world.Elevators;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Панель лифта в кабине: нажал — меню этажей. Лицо панели смотрит в кабину. */
public class ElevatorPanelBlock extends FacingBlock {
    public static final MapCodec<ElevatorPanelBlock> CODEC = createCodec(ElevatorPanelBlock::new);

    public ElevatorPanelBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        Elevators.openMenu((ServerPlayerEntity) player, pos, state.get(FACING));
        return ActionResult.CONSUME;
    }
}
