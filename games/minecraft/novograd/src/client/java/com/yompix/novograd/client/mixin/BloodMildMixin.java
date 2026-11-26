package com.yompix.novograd.client.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 0.9.7: «Кровь: МЯГКО» (и всегда — без взрослого контента): из кровавых частиц ранения (gta/Weapons.blood — тёмно-красная
 * пыль и капли «из блока редстоуна») остаётся одна из шести пылинок, капель нет.
 */
@Mixin(ParticleManager.class)
public abstract class BloodMildMixin {
    @Unique
    private static int novograd$n;

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void novograd$mild(ParticleEffect fx, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        if (com.yompix.novograd.client.NovogradConfig.goreFull()) return;
        if (fx instanceof BlockStateParticleEffect b && b.getBlockState().isOf(net.minecraft.block.Blocks.REDSTONE_BLOCK)) {
            cir.setReturnValue(null);
        } else if (fx instanceof DustParticleEffect d) {
            var c = d.getColor();
            if (c.x() > 0.4f && c.y() < 0.1f && c.z() < 0.1f && novograd$n++ % 6 != 0) cir.setReturnValue(null);
        }
    }
}
