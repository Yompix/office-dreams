package com.yompix.novograd.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 0.9: блэкаут района — блочный свет в lightmap гаснет (client/city/CityFx). Небо и солнце не трогаем. */
@Mixin(LightmapTextureManager.class)
public class BlackoutLightMixin {
    @Redirect(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/LightmapTextureManager;getBrightness(Lnet/minecraft/world/dimension/DimensionType;I)F", ordinal = 1))
    private float novograd$blackout(DimensionType type, int level) {
        return LightmapTextureManager.getBrightness(type, level) * com.yompix.novograd.client.city.CityFx.blockLight();
    }
}
