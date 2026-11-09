package com.yompix.novograd.entity;

import com.yompix.novograd.gta.Armory;
import com.yompix.novograd.gta.Arsenal;
import com.yompix.novograd.gta.Crime;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * Выстрел ПГ из «Гранатомёта»: летит быстро и почти прямо, дымный след, взрыв от касания или через 5 с.
 * Блоки не ломает (город цел), машины и людей — да.
 */
public class RocketEntity extends ThrownItemEntity {
    public RocketEntity(EntityType<? extends RocketEntity> type, World world) {
        super(type, world);
    }

    public RocketEntity(EntityType<? extends RocketEntity> type, LivingEntity owner, World world) {
        super(type, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return Armory.ROCKET;
    }

    @Override
    protected double getGravity() {
        return 0.006;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            for (int i = 0; i < 3; i++) {
                double k = i / 3.0;
                getWorld().addParticle(ParticleTypes.SMOKE, prevX + (getX() - prevX) * k, prevY + (getY() - prevY) * k, prevZ + (getZ() - prevZ) * k, 0, 0.01, 0);
            }
            getWorld().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
        } else if (age > 100 && getWorld() instanceof ServerWorld w) explode(w);
    }

    @Override
    protected boolean canHit(net.minecraft.entity.Entity e) {
        return super.canHit(e) && (getOwner() == null || !getOwner().isConnectedThroughVehicle(e));
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (getWorld() instanceof ServerWorld w && !isRemoved()) {
            if (hit instanceof EntityHitResult eh) setPosition(eh.getPos());
            else setPosition(hit.getPos());
            explode(w);
        }
    }

    private void explode(ServerWorld w) {
        double x = getX(), y = getY(), z = getZ();
        w.createExplosion(this, null, null, x, y, z, 3.8f, false, World.ExplosionSourceType.NONE,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, Arsenal.EXPLOSION);
        w.spawnParticles(ParticleTypes.FLAME, x, y, z, 40, 0.8, 0.5, 0.8, 0.15);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 30, 1.2, 0.8, 1.2, 0.05);
        // машины рядом — сильный урон (взрыв мода не задевает кузов полностью)
        for (CarEntity car : w.getEntitiesByClass(CarEntity.class, getBoundingBox().expand(4), c -> true))
            car.hitByBullet(60f * (float) Math.max(0.2, 1 - car.distanceTo(this) / 5), getOwner() instanceof LivingEntity o ? o : null);
        if (getOwner() instanceof LivingEntity o) Crime.gunshot(w, o, getPos());
        com.yompix.novograd.world.Destruction.explosion(w, getPos(), 3.8f, getOwner() instanceof LivingEntity o ? o : null); // стёкла, предметы, огонь
        discard();
    }
}
