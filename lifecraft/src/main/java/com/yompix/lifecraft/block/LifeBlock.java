package com.yompix.lifecraft.block;

import com.yompix.lifecraft.life.LifeActions;
import com.yompix.lifecraft.world.TownState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/** Блоки города, на которые можно нажать: доска, парта, банкомат, вакансии, почта, касса, компьютер. */
public class LifeBlock extends FacingBlock {
    public enum Kind { CHALKBOARD, DESK, ATM, JOB_BOARD, MAILBOX, SHOP_COUNTER, COMPUTER, SLOT_MACHINE, ROULETTE }

    public final Kind kind;

    public LifeBlock(Settings settings, Kind kind, VoxelShape north) {
        super(settings, north);
        this.kind = kind;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        LifeActions.useBlock(kind, state, (ServerWorld) world, pos, (ServerPlayerEntity) player, ItemStack.EMPTY);
        return ActionResult.CONSUME;
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (kind != Kind.MAILBOX && kind != Kind.ATM) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;
        LifeActions.useBlock(kind, state, (ServerWorld) world, pos, (ServerPlayerEntity) player, stack);
        return ItemActionResult.CONSUME;
    }

    @Override
    protected void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (kind == Kind.MAILBOX && world instanceof ServerWorld sw && !oldState.isOf(this)) TownState.get(sw).addMailbox(pos);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (kind == Kind.MAILBOX && world instanceof ServerWorld sw && !newState.isOf(this)) TownState.get(sw).removeMailbox(pos);
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
