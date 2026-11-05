package com.yompix.lifecraft.block;

import com.mojang.serialization.MapCodec;
import com.yompix.lifecraft.story.Story;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Улика (или потерянная вещь) на земле: блестит искрами, ПКМ — подобрать. */
public class ClueBlock extends Block {
    public static final MapCodec<ClueBlock> CODEC = createCodec(ClueBlock::new);
    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 3, 13);

    public ClueBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) Story.onClue((ServerPlayerEntity) player, pos);
        return ActionResult.success(world.isClient);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        for (int i = 0; i < 2; i++)
            world.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + random.nextGaussian() * 0.2, pos.getY() + 0.4, pos.getZ() + 0.5 + random.nextGaussian() * 0.2, 0, 0.04, 0);
    }
}
