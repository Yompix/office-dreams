package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.CarRadio;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** В машине колесо мыши переключает радиостанции (вверх — следующая, вниз — предыдущая), а не слоты. */
@Mixin(Mouse.class)
public class RadioScrollMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void novograd$radioWheel(long window, double h, double v, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (window != mc.getWindow().getHandle() || mc.currentScreen != null || v == 0) return;
        if (CarRadio.scroll(mc, v > 0 ? 1 : -1)) ci.cancel();
    }
}
