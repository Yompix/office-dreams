package com.yompix.novograd.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к трекерам сущностей — чтобы актёр кат-сцены точно ушёл на клиент (Director.retrack). */
@Mixin(ServerChunkLoadingManager.class)
public interface ChunkLoadingAccessor {
    @Accessor("entityTrackers")
    Int2ObjectMap<?> novograd$trackers();
}
