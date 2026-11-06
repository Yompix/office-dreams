package com.yompix.novograd.client.mixin;

import com.yompix.novograd.client.ArsenalClient;
import com.yompix.novograd.item.GunItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Со стволом в руке мышь — как в шутере: ЛКМ стреляет (не бьёт и не ломает блоки, в машине тоже),
 * ПКМ — прицел. Одиночное нажатие ПКМ по двери/машине ещё открывает/сажает, по людям — только прицел.
 */
@Mixin(MinecraftClient.class)
public class GunInputMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void novograd$fire(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient c = (MinecraftClient) (Object) this;
        if (c.player != null && c.player.getMainHandStack().getItem() instanceof GunItem) {
            ArsenalClient.click();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void novograd$noBreak(boolean breaking, CallbackInfo ci) {
        MinecraftClient c = (MinecraftClient) (Object) this;
        if (c.player != null && c.player.getMainHandStack().getItem() instanceof GunItem) {
            if (c.interactionManager != null) c.interactionManager.cancelBlockBreaking();
            ci.cancel();
        }
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void novograd$aim(CallbackInfo ci) {
        MinecraftClient c = (MinecraftClient) (Object) this;
        if (c.player == null || !(c.player.getMainHandStack().getItem() instanceof GunItem)) return;
        if (ArsenalClient.aimHeldTicks() > 1 || c.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity) ci.cancel();
    }
}
