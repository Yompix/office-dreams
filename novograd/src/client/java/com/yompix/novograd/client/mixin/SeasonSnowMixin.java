package com.yompix.novograd.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yompix.novograd.client.OutdoorClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Зима (world/Seasons): на экране вместо дождя идёт снег. Только в потоке отрисовки — сервер (и в одиночной игре тоже)
 * по-прежнему считает дождь, лёд и снежный покров в мире не появляются.
 */
@Mixin(Biome.class)
public class SeasonSnowMixin {
    @Inject(method = "getPrecipitation", at = @At("HEAD"), cancellable = true)
    private void novograd$snow(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (OutdoorClient.SEASON == 2 && RenderSystem.isOnRenderThread() && ((Biome) (Object) this).hasPrecipitation()) cir.setReturnValue(Biome.Precipitation.SNOW);
    }
}
