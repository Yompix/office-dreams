package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.style.StyleClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Телосложение и рост из внешности (0.9.6) и сила из зала: модель шире/уже и чуть выше/ниже — только картинка,
 * хитбокс прежний. Голову обратно сужает PlayerModelScaleMixin.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerStyleScaleMixin {
    @Inject(method = "scale(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
    private void novograd$muscles(AbstractClientPlayerEntity player, MatrixStack m, float amount, CallbackInfo ci) {
        float w = StyleClient.width(player), h = StyleClient.height(player);
        if (w != 1f || h != 1f) m.scale(w, h, w);
        // 0.9.6: жим лёжа — на спине на скамье, отжимания — лицом вниз (здесь ось y смотрит вниз, начало — у ног)
        String e = com.yompix.novograd.client.anim.PlayerAnim.emoteOf(player);
        if (e.equals("gym_bench")) {
            m.translate(0, -0.55, 0.9);
            m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-90));
            m.translate(0, -0.9, 0);
        } else if (e.equals("gym_pushup")) {
            float k = 0.5f + 0.5f * net.minecraft.util.math.MathHelper.sin(player.age * 0.2f);
            m.translate(0, -0.18 - 0.22 * k, -0.9);
            m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(90));
            m.translate(0, -0.9, 0);
        }
    }
}
