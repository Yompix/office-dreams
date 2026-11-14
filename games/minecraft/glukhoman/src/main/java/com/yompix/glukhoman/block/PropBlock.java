package com.yompix.glukhoman.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

import java.util.EnumMap;
import java.util.Map;

/** Предмет обстановки с направлением: рация, щит, генератор, записка… Форма задаётся для «лицом на юг» и поворачивается. */
public class PropBlock extends HorizontalFacingBlock {
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public PropBlock(Settings settings, VoxelShape southShape) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.SOUTH));
        for (Direction d : Direction.Type.HORIZONTAL) shapes.put(d, rotate(southShape, d));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return null;
    }

    private static VoxelShape rotate(VoxelShape s, Direction to) {
        VoxelShape[] out = {VoxelShapes.empty()};
        s.forEachBox((x1, y1, z1, x2, y2, z2) -> {
            VoxelShape b = switch (to) {
                case NORTH -> VoxelShapes.cuboid(1 - x2, y1, 1 - z2, 1 - x1, y2, 1 - z1);
                case EAST -> VoxelShapes.cuboid(z1, y1, 1 - x2, z2, y2, 1 - x1);
                case WEST -> VoxelShapes.cuboid(1 - z2, y1, x1, 1 - z1, y2, x2);
                default -> VoxelShapes.cuboid(x1, y1, z1, x2, y2, z2);
            };
            out[0] = VoxelShapes.union(out[0], b);
        });
        return out[0];
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        return shapes.get(state.get(FACING));
    }
}
