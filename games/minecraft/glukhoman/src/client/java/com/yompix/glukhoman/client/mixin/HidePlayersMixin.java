package com.yompix.glukhoman.client.mixin;

import com.yompix.glukhoman.client.Cine;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** В кат-сцене игроки скрыты — в кадре только актёры. */
@Mixin(EntityRenderDispatcher.class)
public class HidePlayersMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void glukhoman$hide(E entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (Cine.active && entity instanceof PlayerEntity) cir.setReturnValue(false);
    }
}
