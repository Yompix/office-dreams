package com.yompix.novograd.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 1.0 (проверка интерфейса на 1280×720, 1366×768, 2560×1440): масштаб интерфейса «Авто» берёт самый крупный, при
 * котором экран ≥ 320×240, — на 720p это 3, и окна мода (финансы, коллекции, навыки, карта) упираются в край: подписи
 * налезают друг на друга, низ списка уходит под подсказки. Для «Авто» оставляем экран не меньше 480×270 (на 720p —
 * масштаб 2, на 1080p — 4 как было). Выбранный вручную масштаб (и его предел в настройках) не трогаем.
 */
@Mixin(MinecraftClient.class)
public abstract class AutoGuiScaleMixin {
    @Shadow
    @Final
    private Window window;

    @Shadow
    public GameOptions options;

    @ModifyArg(method = "onResolutionChanged", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;setScaleFactor(D)V"))
    private double novograd$roomyAuto(double scale) {
        if (options == null || options.getGuiScale().getValue() != 0) return scale;
        int r = (int) scale, w = window.getFramebufferWidth(), h = window.getFramebufferHeight();
        while (r > 1 && (w / r < 480 || h / r < 270)) r--;
        return r;
    }
}
