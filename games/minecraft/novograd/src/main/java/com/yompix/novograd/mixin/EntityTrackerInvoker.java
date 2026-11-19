package com.yompix.novograd.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Ванильный трекер пересчитывает «кто видит сущность» только когда она или игрок сменили секцию. Актёр появился,
 * пока участок ещё не дошёл до клиента, и стоит на месте — клиент его так и не получал (кат-сцена ждала впустую).
 */
@Mixin(targets = "net.minecraft.server.world.ServerChunkLoadingManager$EntityTracker")
public interface EntityTrackerInvoker {
    @Invoker("updateTrackedStatus")
    void novograd$update(ServerPlayerEntity player);
}
