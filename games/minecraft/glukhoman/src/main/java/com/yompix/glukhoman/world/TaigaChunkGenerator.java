package com.yompix.glukhoman.world;

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

/** Генератор Глухомани: каждый столбец строится функцией {@link Terrain#column}, быстро и без загрузки соседей. */
public class TaigaChunkGenerator extends ChunkGenerator {
    public static final MapCodec<TaigaChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, TaigaChunkGenerator::new));

    public TaigaChunkGenerator(BiomeSource biomeSource) {
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
                Terrain.column(x, z, (y, s) -> {
                    if (y < chunk.getBottomY() || y >= chunk.getTopY() || s.isAir()) return;
                    m.set(fx, y, fz);
                    chunk.setBlockState(m, s, false);
                    ocean.trackUpdate(fx, y, fz, s);
                    surface.trackUpdate(fx, y, fz, s);
                    // заборы, стёкла, перила и ступени соединятся с соседями, когда чанк достроится
                    net.minecraft.block.Block b = s.getBlock();
                    if (b instanceof net.minecraft.block.PaneBlock || b instanceof net.minecraft.block.FenceBlock
                            || b instanceof net.minecraft.block.WallBlock || b instanceof net.minecraft.block.StairsBlock)
                        chunk.markBlockForPostProcessing(m);
                    // кровати, сундуки и т.п.: блок-сущность создастся при загрузке чанка (как у ванильных построек)
                    if (s.hasBlockEntity()) {
                        net.minecraft.nbt.NbtCompound be = new net.minecraft.nbt.NbtCompound();
                        be.putInt("x", x);
                        be.putInt("y", y);
                        be.putInt("z", z);
                        be.putString("id", "DUMMY");
                        chunk.addPendingBlockEntityNbt(be);
                    }
                });
            }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getSeaLevel() {
        return Terrain.G;
    }

    @Override
    public int getMinimumY() {
        return -64;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type type, HeightLimitView world, NoiseConfig noise) {
        return Terrain.surface(x, z) + 1;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noise) {
        BlockState[] col = new BlockState[world.getHeight()];
        java.util.Arrays.fill(col, Blocks.AIR.getDefaultState());
        int bottom = world.getBottomY();
        Terrain.column(x, z, (y, s) -> {
            int i = y - bottom;
            if (i >= 0 && i < col.length) col[i] = s;
        });
        return new VerticalBlockSample(bottom, col);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noise, BlockPos pos) {
        text.add("Глухомань: " + Terrain.zone(pos.getX(), pos.getZ()));
    }
}
