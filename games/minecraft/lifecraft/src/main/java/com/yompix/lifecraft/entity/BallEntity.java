package com.yompix.lifecraft.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Футбольный мяч: катится, отскакивает от стен, его пинают дети и игроки (удар рукой или просто набегая). */
public class BallEntity extends Entity {
    public float spin, prevSpin;

    public BallEntity(EntityType<? extends BallEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    public void kick(Vec3d dir, double power, double lift) {
        Vec3d d = new Vec3d(dir.x, 0, dir.z);
        if (d.lengthSquared() < 1e-4) return;
        d = d.normalize().multiply(power);
        setVelocity(d.x, lift, d.z);
        velocityDirty = true;
        getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_WOOL_HIT, SoundCategory.NEUTRAL, 0.8f, 1.4f);
    }

    @Override
    public void tick() {
        super.tick();
        prevSpin = spin;
        Vec3d v = getVelocity();
        double vy = isOnGround() ? Math.max(v.y, -0.02) : v.y - 0.04;
        double fr = isOnGround() ? 0.94 : 0.99;
        setVelocity(v.x * fr, vy, v.z * fr);
        Vec3d before = getVelocity();
        move(MovementType.SELF, before);
        // отскок от стен
        if (horizontalCollision) setVelocity(-before.x * 0.6, getVelocity().y, -before.z * 0.6);
        if (verticalCollision && before.y < -0.15) setVelocity(getVelocity().x, -before.y * 0.5, getVelocity().z);
        spin += (float) (Math.sqrt(before.x * before.x + before.z * before.z) * 40);
        if (!getWorld().isClient) {
            // набежал игрок или житель — мяч отлетает
            for (Entity e : getWorld().getOtherEntities(this, getBoundingBox().expand(0.25), e -> e instanceof PlayerEntity || e instanceof CitizenEntity)) {
                Vec3d push = getPos().subtract(e.getPos());
                Vec3d run = e.getVelocity();
                double p = Math.max(0.35, Math.sqrt(run.x * run.x + run.z * run.z) * 2.2);
                kick(push, Math.min(p, 0.9), 0.12);
            }
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient) return true;
        if (source.getAttacker() != null) kick(getPos().subtract(source.getAttacker().getPos()), 1.1, 0.35);
        return true;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient) kick(player.getRotationVec(1), 0.9, 0.45);
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean canHit() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}
}
