package com.yompix.novograd.client.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 0.8, производительность: ваниль 1334 раза за тик выбирает случайный блок вокруг игрока и для каждого не-полного
 * блока (а в городе это почти всё: воздух, стёкла, заборы) ищет биом — «размытым» поиском по 8 соседним ячейкам
 * шума. Это 15% времени главного потока клиента в замере (JFR, perf). Биом нужен только для фоновых частиц биома
 * (пепел, споры); берём ячейку биома напрямую — одна выборка вместо восьми, частицы те же (у границы биомов —
 * с точностью до ячейки 4×4×4). -Dnovograd.perf08=false — как в ванили.
 */
@Mixin(ClientWorld.class)
public class DisplayTickBiomeMixin {
    private static final boolean ON = !"false".equals(System.getProperty("novograd.perf08"));

    @Redirect(method = "randomBlockDisplayTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/world/ClientWorld;getBiome(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/registry/entry/RegistryEntry;"))
    private RegistryEntry<Biome> novograd$cellBiome(ClientWorld w, BlockPos pos) {
        if (!ON) return w.getBiome(pos);
        return w.getBiomeForNoiseGen(BiomeCoords.fromBlock(pos.getX()), BiomeCoords.fromBlock(pos.getY()), BiomeCoords.fromBlock(pos.getZ()));
    }
}
