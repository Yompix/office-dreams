package com.yompix.novograd.client.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

/**
 * 1.0, производительность: бюджет частиц. Поиск провалов FPS (автотест fpshunt) показал, что после взрывов машин,
 * пожаров и в дождь на экране 500–1300 частиц, и кадр на слабой графике растёт в 3–4 раза. Новые частицы сверх
 * бюджета («Производительность»: низкая 350, средняя 900, высокая 2500) не создаются — старые доживают как обычно.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleBudgetMixin {
    @Shadow
    @org.spongepowered.asm.mixin.Final
    private Map<ParticleTextureSheet, Queue<Particle>> particles;

    @Unique
    private int novograd$count;

    @Inject(method = "tick", at = @At("TAIL"))
    private void novograd$count(CallbackInfo ci) {
        int n = 0;
        for (Queue<Particle> q : particles.values()) n += q.size();
        novograd$count = n;
    }

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void novograd$budget(Particle p, CallbackInfo ci) {
        if (!com.yompix.novograd.client.NovogradConfig.PERF10) return;
        if (novograd$count >= com.yompix.novograd.client.NovogradConfig.particleBudget()) {
            ci.cancel();
            return;
        }
        novograd$count++;
    }
}
