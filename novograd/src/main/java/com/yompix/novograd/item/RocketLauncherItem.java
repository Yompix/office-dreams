package com.yompix.novograd.item;

import com.yompix.novograd.entity.RocketEntity;
import com.yompix.novograd.gta.Armory;
import com.yompix.novograd.gta.Arsenal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;

/**
 * «Гранатомёт»: один выстрел ПГ в трубе, ракета летит почти прямо и взрывается от касания (RocketEntity).
 * Остальное (прицел, отдача, перезарядка, колесо) — как у любого ствола.
 */
public class RocketLauncherItem extends GunItem {
    public RocketLauncherItem(Settings settings, Spec spec) {
        super(settings, spec);
    }

    @Override
    protected void discharge(ServerWorld w, PlayerEntity p, ItemStack stack, double spread) {
        var r = w.getRandom();
        Vec3d dir = p.getRotationVec(1f).add((r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread).normalize();
        RocketEntity rocket = new RocketEntity(Armory.ROCKET_ENTITY, p, w);
        Vec3d at = p.getEyePos().add(dir.multiply(1.2)).add(0, -0.15, 0);
        rocket.setPosition(at);
        rocket.setVelocity(dir.x, dir.y, dir.z, 2.2f, 0f);
        w.spawnEntity(rocket);
        w.playSound(null, p.getBlockPos(), spec().sound(), SoundCategory.PLAYERS, spec().volume(), 0.95f + r.nextFloat() * 0.1f);
        // вспышка у трубы для всех (без луча)
        Arsenal.sendShotFx(w, p, p.getEyePos(), kindIndex(), new float[]{(float) at.x, (float) at.y, (float) at.z}, new byte[]{0}, (byte) 0);
        // реактивная струя назад
        Vec3d back = p.getEyePos().subtract(dir.multiply(1.4));
        w.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, back.x, back.y, back.z, 12, 0.3, 0.3, 0.3, 0.05);
        w.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, back.x, back.y, back.z, 6, 0.15, 0.15, 0.15, 0.04);
        com.yompix.novograd.gta.Crime.gunshot(w, p, p.getPos());
    }
}
