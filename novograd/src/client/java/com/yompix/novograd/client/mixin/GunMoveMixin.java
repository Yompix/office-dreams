package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.GunFx;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** В прицеле (ПКМ со стволом) идём медленнее. */
@Mixin(KeyboardInput.class)
public class GunMoveMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void novograd$adsSlow(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        float k = GunFx.adsMoveFactor();
        if (k < 1f) {
            Input in = (Input) (Object) this;
            in.movementForward *= k;
            in.movementSideways *= k;
        }
    }
}
