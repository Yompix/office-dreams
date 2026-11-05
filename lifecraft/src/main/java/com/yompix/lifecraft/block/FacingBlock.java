package com.yompix.lifecraft.block;

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

/** Блок, который смотрит на игрока при установке. Форма задаётся для севера и сама поворачивается. */
public class FacingBlock extends HorizontalFacingBlock {
    /** Столбы, банкоматы, ящики, парты — непроходимы для поиска пути, иначе жители упираются в тонкий столб. */
    @Override
    protected boolean canPathfindThrough(net.minecraft.block.BlockState state, net.minecraft.entity.ai.pathing.NavigationType type) {
        return false;
    }

    public static final MapCodec<FacingBlock> CODEC = createCodec(FacingBlock::new);
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public FacingBlock(Settings settings) {
        this(settings, VoxelShapes.fullCube());
    }

    public FacingBlock(Settings settings, VoxelShape north) {
        super(settings);
        for (Direction d : Direction.Type.HORIZONTAL) shapes.put(d, rotate(north, d));
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shapes.get(state.get(FACING));
    }

    /** Коробки в пикселях (0..16), как в json-моделях; лицо блока смотрит на север. */
    public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Block.createCuboidShape(x1, y1, z1, x2, y2, z2);
    }

    static VoxelShape rotate(VoxelShape shape, Direction to) {
        int turns = switch (to) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        VoxelShape out = shape;
        for (int i = 0; i < turns; i++) {
            VoxelShape[] acc = {VoxelShapes.empty()};
            out.forEachBox((x1, y1, z1, x2, y2, z2) ->
                    acc[0] = VoxelShapes.union(acc[0], VoxelShapes.cuboid(1 - z2, y1, x1, 1 - z1, y2, x2)));
            out = acc[0];
        }
        return out;
    }
}
