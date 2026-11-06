package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.GunFx;
import com.yompix.novograd.item.GunItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Стволы от первого лица рисует GunFx: прицеливание, отдача, перезарядка, вспышка. */
@Mixin(HeldItemRenderer.class)
public class GunHeldItemMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void novograd$gun(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress, ItemStack item,
                              float equipProgress, MatrixStack matrices, VertexConsumerProvider buffers, int light, CallbackInfo ci) {
        if (!(item.getItem() instanceof GunItem) || player.isUsingSpyglass()) return;
        ci.cancel();
        GunFx.renderFirstPerson((HeldItemRenderer) (Object) this, player, tickDelta, hand, item, equipProgress, matrices, buffers, light);
    }
}
