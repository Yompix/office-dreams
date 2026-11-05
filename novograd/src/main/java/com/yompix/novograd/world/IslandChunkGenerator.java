package com.yompix.novograd.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Генератор острова Новоград: каждый столбец строится функцией {@link Island#column}, быстро и без загрузки соседей. */
public class IslandChunkGenerator extends ChunkGenerator {
    public static final MapCodec<IslandChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, IslandChunkGenerator::new));

    public IslandChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public void carve(ChunkRegion region, long seed, NoiseConfig noise, BiomeAccess biomes, StructureAccessor structures, Chunk chunk, net.minecraft.world.gen.GenerationStep.Carver step) {
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noise, Chunk chunk) {
    }

    @Override
    public void populateEntities(ChunkRegion region) {
    }

    @Override
    public int getWorldHeight() {
        return 384;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noise, StructureAccessor structures, Chunk chunk) {
        int bx = chunk.getPos().getStartX(), bz = chunk.getPos().getStartZ();
        BlockPos.Mutable m = new BlockPos.Mutable();
        Heightmap ocean = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG), surface = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = bx + lx, z = bz + lz;
                final int fx = lx, fz = lz;
                Island.column(x, z, (y, s) -> {
                    if (y < chunk.getBottomY() || y >= chunk.getTopY() || s.isAir()) return;
                    m.set(fx, y, fz);
                    chunk.setBlockState(m, s, false);
                    ocean.trackUpdate(fx, y, fz, s);
                    surface.trackUpdate(fx, y, fz, s);
                });
            }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel() {
        return Island.G;
    }

    @Override
    public int getMinimumY() {
        return -64;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView world, NoiseConfig noise) {
        return Island.surface(x, z) + 1;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noise) {
        BlockState[] col = new BlockState[world.getHeight()];
        java.util.Arrays.fill(col, Blocks.AIR.getDefaultState());
        int bottom = world.getBottomY();
        Island.column(x, z, (y, s) -> {
            int i = y - bottom;
            if (i >= 0 && i < col.length) col[i] = s;
        });
        return new VerticalBlockSample(bottom, col);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noise, BlockPos pos) {
        text.add("Новоград: " + Island.zone(pos.getX(), pos.getZ()));
    }
}
