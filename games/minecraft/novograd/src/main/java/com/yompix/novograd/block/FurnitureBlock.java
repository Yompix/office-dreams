package com.yompix.novograd.block;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * 0.9.10: мебель квартиры — объёмная модель (tools/gen_furniture.py), поворачивается по сторонам света
 * (при установке — к игроку плюс поворот Z/X из предпросмотра), диван/кресло/стул/кровать — сесть.
 */
public class FurnitureBlock extends FacingBlock {
    /** Высота сиденья в блоках или -1, если сесть нельзя. */
    public final double seat;

    public FurnitureBlock(Settings settings, VoxelShape north, double seat) {
        super(settings, north);
        this.seat = seat;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (seat < 0 || player.isSneaking()) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        SeatBlockHelper.sit((ServerWorld) world, pos, (ServerPlayerEntity) player, seat);
        return ActionResult.CONSUME;
    }

    @Override
    protected boolean isTransparent(BlockState state, net.minecraft.world.BlockView world, BlockPos pos) {
        return true;
    }
}
