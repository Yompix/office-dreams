package com.yompix.glukhoman.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Дрезина (1.0): ручная, с коромыслом. Едет её Part2 (сервер двигает по линии), сама только везёт и качает рычаг —
 * темп рычага = скорость (PUMP синхронизируется всем). Сторож стоит на настиле, не сидит (руки — на рычаге, PoseMixin).
 */
public class HandcarEntity extends Entity implements GeoEntity {
    private static final TrackedData<Float> PUMP = DataTracker.registerData(HandcarEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private final AnimatableInstanceCache geo = GeckoLibUtil.createInstanceCache(this);
    public double animClock = -1;
    double phase;

    public HandcarEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public float pump() {
        return dataTracker.get(PUMP);
    }

    public void pump(float v) {
        dataTracker.set(PUMP, v);
    }

    /** Фаза рычага (рад) — на клиенте, по скорости; её же берут руки сторожа. */
    public double phase() {
        return phase;
    }

    @Override
    public void tick() {
        super.tick();
        phase += 0.08 + pump() * 0.09;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        b.add(PUMP, 0f);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (!getWorld().isClient && !hasPassengers()) player.startRiding(this);
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public boolean canHit() {
        return true;
    }

    /** Стоит на настиле (игрок «сидящий» опущен на 0.6 — компенсируем). */
    @Override
    protected net.minecraft.util.math.Vec3d getPassengerAttachmentPos(Entity passenger, net.minecraft.entity.EntityDimensions dimensions, float scaleFactor) {
        return new net.minecraft.util.math.Vec3d(0, 0.95, 0.25);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().isEmpty();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 4, s -> s.setAndContinue(RawAnimation.begin().thenLoop("animation.handcar.pump"))));
    }

    @Override
    public double getTick(Object o) {
        return animClock >= 0 ? animClock : phase / (2 * Math.PI) * 20;   // один качок рычага = одна «секунда» клипа
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geo;
    }
}
