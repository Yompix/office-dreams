package com.yompix.novograd.block;

import com.yompix.novograd.world.Props;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/** Уличные предметы, которыми можно пользоваться и которые можно сломать: автомат с газировкой, таксофон, киоск, тележки, баллон, конус. */
public class PropBlock extends FacingBlock {
    public enum Kind { VENDING, PAYPHONE, NEWS_STAND, HOTDOG, ICECREAM, PROPANE, CONE }

    public final Kind kind;

    public PropBlock(Settings settings, Kind kind, VoxelShape north) {
        super(settings, north);
        this.kind = kind;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        Props.use(kind, state, (ServerWorld) world, pos, (ServerPlayerEntity) player);
        return ActionResult.CONSUME;
    }
}
