package com.yompix.novograd.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.yompix.novograd.client.OutdoorClient;
import net.minecraft.world.biome.FoliageColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Береза осенью — жёлтая, зимой хвоя и берёза в инее (у них цвет не из биома, а постоянный). */
@Mixin(FoliageColors.class)
public class SeasonFoliageMixin {
    @Inject(method = "getBirchColor", at = @At("RETURN"), cancellable = true)
    private static void novograd$birch(CallbackInfoReturnable<Integer> cir) {
        if (!RenderSystem.isOnRenderThread()) return;
        int s = OutdoorClient.SEASON;
        if (s == 1) cir.setReturnValue(0xE8B43A);
        else if (s == 2) cir.setReturnValue(0xC6D2D0);
    }

    @Inject(method = "getSpruceColor", at = @At("RETURN"), cancellable = true)
    private static void novograd$spruce(CallbackInfoReturnable<Integer> cir) {
        if (RenderSystem.isOnRenderThread() && OutdoorClient.SEASON == 2) cir.setReturnValue(0x8FA59A);
    }
}
