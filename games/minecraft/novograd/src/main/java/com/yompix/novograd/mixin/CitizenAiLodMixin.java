package com.yompix.novograd.mixin;

import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Уровень детализации ИИ: обычные прохожие далеко от всех игроков думают реже (цели, путь, взгляд), а физика и
 * анимация идут каждый тик. Герои сюжета, актёры кат-сцен, полиция и все, кто занят игроком, — всегда каждый тик.
 */
@Mixin(MobEntity.class)
public class CitizenAiLodMixin {
    @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
    private void novograd$lod(CallbackInfo ci) {
        if ((Object) this instanceof CitizenEntity c && c.aiLodSkip()) ci.cancel();
    }
}
