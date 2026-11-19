package com.yompix.novograd.mixin;

import com.yompix.novograd.world.IslandChunkGenerator;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * На острове единственный биом «Город» без природных мобов (spawners пустые) — ванильный перебор мест для
 * спавна в каждом чанке каждый тик ничего не находит, но съедал ~17% тика сервера (замер JFR, 0.6.4).
 * Пропускаем его только в мире-острове: результат тот же (никто не появляется), а тик дешевле.
 * Жители, звери и полиция появляются своим кодом (gta/Streets, Population, Hunting) — их это не касается.
 */
@Mixin(SpawnHelper.class)
public class IslandSpawnMixin {
    @Inject(method = "spawnEntitiesInChunk(Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/chunk/WorldChunk;Lnet/minecraft/world/SpawnHelper$Checker;Lnet/minecraft/world/SpawnHelper$Runner;)V",
            at = @At("HEAD"), cancellable = true)
    private static void novograd$skipOnIsland(SpawnGroup group, ServerWorld world, WorldChunk chunk, SpawnHelper.Checker checker, SpawnHelper.Runner runner, CallbackInfo ci) {
        if (world.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator) ci.cancel();
    }
}
