package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.OutdoorClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Времена года (world/Seasons): осенью трава жухнет, а листва желтеет и краснеет; зимой — в инее; весной — свежая. */
@Mixin(BiomeColors.class)
public class SeasonColorsMixin {
    @Inject(method = "getGrassColor", at = @At("RETURN"), cancellable = true)
    private static void novograd$grass(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int s = OutdoorClient.SEASON;
        if (s == 0) return;
        int base = cir.getReturnValueI();
        cir.setReturnValue(switch (s) {
            case 1 -> mix(base, 0xB9A24A, 0.62f);
            case 2 -> mix(base, 0xE6EDF0, 0.82f);
            default -> mix(base, 0x7FDA4E, 0.45f);
        });
    }

    @Inject(method = "getFoliageColor", at = @At("RETURN"), cancellable = true)
    private static void novograd$foliage(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int s = OutdoorClient.SEASON;
        if (s == 0) return;
        int base = cir.getReturnValueI();
        int h = pos == null ? 0 : Math.floorMod((pos.getX() >> 2) * 73856093 ^ (pos.getZ() >> 2) * 19349663 ^ (pos.getY() >> 2) * 83492791, 7);
        cir.setReturnValue(switch (s) {
            case 1 -> mix(base, new int[]{0xD9822B, 0xC2412A, 0xE3B53A, 0xD9822B, 0xB8582A, 0xE8C547, 0x9A6A2A}[h], 0.8f);
            case 2 -> mix(base, 0xCAD6D4, 0.7f);
            default -> mix(base, 0x8DDB5A, 0.4f);
        });
    }

    private static int mix(int a, int b, float k) {
        int r = (int) ((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k);
        int g = (int) ((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k);
        int bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return r << 16 | g << 8 | bl;
    }
}
