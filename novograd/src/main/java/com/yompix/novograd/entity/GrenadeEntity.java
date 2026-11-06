package com.yompix.novograd.entity;

import com.yompix.novograd.gta.Arsenal;
import com.yompix.novograd.gta.Crime;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Брошенная граната или коктейль Молотова.
 * Граната отскакивает и взрывается через 2,5 с: урон и отбрасывание, блоки не ломаются.
 * Молотов разбивается при касании и 5 секунд горит лужей: поджигает всех рядом, но не дома.
 */
public class GrenadeEntity extends ThrownItemEntity {
    private int fuse = 50;
    private int burn = -1;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, World world) {
        super(type, world);
    }

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, LivingEntity owner, World world) {
        super(type, owner, world);
    }

    private boolean molotov() {
        return getType() == Arsenal.MOLOTOV_ENTITY;
    }

    @Override
    protected Item getDefaultItem() {
        return molotov() ? Arsenal.MOLOTOV : Arsenal.GRENADE;
    }

    @Override
    protected double getGravity() {
        return burn >= 0 ? 0 : 0.045;
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        super.onBlockHit(hit);
        if (molotov()) {
            ignite(hit.getPos());
            return;
        }
        // отскок от стены/пола
        Direction side = hit.getSide();
        Vec3d v = getVelocity();
        Vec3d n = Vec3d.of(side.getVector());
        Vec3d refl = v.subtract(n.multiply(2 * v.dotProduct(n))).multiply(0.4);
        if (side == Direction.UP && Math.abs(refl.y) < 0.1) refl = new Vec3d(refl.x * 0.6, 0, refl.z * 0.6);
        setPosition(hit.getPos().add(n.multiply(0.06)));
        setVelocity(refl);
        if (v.lengthSquared() > 0.04 && !getWorld().isClient)
            getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 0.5f, 1.6f);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        if (molotov()) {
            ignite(hit.getPos());
            return;
        }
        setVelocity(getVelocity().multiply(-0.25));
    }

    private void ignite(Vec3d at) {
        if (burn >= 0) return;
        burn = 100;
        setPosition(at);
        setVelocity(Vec3d.ZERO);
        setNoGravity(true);
        if (getWorld() instanceof ServerWorld w) {
            setItem(ItemStack.EMPTY);
            w.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1f, 0.9f);
            w.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.2f, 0.8f);
            w.spawnParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, 40, 1.2, 0.3, 1.2, 0.05);
            if (getOwner() instanceof LivingEntity o) Crime.gunshot(w, o, at);
        }
    }

    @Override
    public void tick() {
        if (burn >= 0) {
            // лужа огня: не двигается, только горит
            baseTick();
            if (getWorld() instanceof ServerWorld w) burnTick(w);
            return;
        }
        super.tick();
        if (getWorld() instanceof ServerWorld w && !molotov()) {
            if (fuse % 10 == 0) w.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 0.15, getZ(), 1, 0, 0, 0, 0.01);
            if (--fuse <= 0) explode(w);
        }
        if (getY() < getWorld().getBottomY() - 10 || age > 400) discard();
    }

    private void burnTick(ServerWorld w) {
        if (burn % 2 == 0) {
            w.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 0.1, getZ(), 6, 1.3, 0.15, 1.3, 0.02);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.6, getZ(), 1, 0.8, 0.2, 0.8, 0.02);
        }
        if (burn % 20 == 0) w.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 1.4f, 1f);
        if (burn % 10 == 0) {
            var src = w.getDamageSources().create(DamageTypes.IN_FIRE, this, getOwner());
            for (Entity e : w.getOtherEntities(this, getBoundingBox().expand(2.6, 1.2, 2.6), e -> e instanceof LivingEntity && e.isAlive())) {
                e.setOnFireFor(4);
                e.damage(src, 2.5f);
            }
        }
        if (--burn <= 0) discard();
    }

    private void explode(ServerWorld w) {
        double x = getX(), y = getY() + 0.1, z = getZ();
        w.createExplosion(this, null, null, x, y, z, 2.6f, false, World.ExplosionSourceType.NONE,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, Arsenal.EXPLOSION);
        w.spawnParticles(ParticleTypes.FLAME, x, y, z, 30, 0.6, 0.4, 0.6, 0.12);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 20, 1, 0.6, 1, 0.04);
        w.spawnParticles(ParticleTypes.LAVA, x, y, z, 8, 0.4, 0.2, 0.4, 0);
        if (getOwner() instanceof LivingEntity o) Crime.gunshot(w, o, getPos());
        discard();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("fuse", fuse);
        nbt.putInt("burn", burn);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        fuse = nbt.getInt("fuse");
        burn = nbt.contains("burn") ? nbt.getInt("burn") : -1;
    }
}
