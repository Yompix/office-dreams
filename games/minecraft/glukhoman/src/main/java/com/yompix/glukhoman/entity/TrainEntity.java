package com.yompix.glukhoman.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Порожняк: три теплушки и пассажирский, без локомотива. Виден только в видоискателе. Без звука колёс.
 * Едет по точкам (path), скорость плавно меняется; сервер решает, куда (Night).
 */
public class TrainEntity extends Entity implements GeoEntity, Seen {
    private final AnimatableInstanceCache geo = GeckoLibUtil.createInstanceCache(this);
    /** Высота езды: колёса на головке рельса (рельс в блоке y=66). */
    public static final double Y = 66.0625;
    public final List<Vec3d> path = new ArrayList<>();
    public double speed, maxSpeed = 0.35;
    /** Сколько тиков стоит у цели. */
    public int wait;
    public boolean vanishAtEnd;
    public double animClock = -1;

    public TrainEntity(EntityType<?> type, World world) {
        super(type, world);
        noClip = true;
        ignoreCameraFrustum = true;
        setNoGravity(true);
    }

    @Override
    public int seen() {
        return CAMERA_ONLY;
    }

    public boolean moving() {
        return !path.isEmpty() && wait <= 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (wait > 0) {
            wait--;
            setVelocity(Vec3d.ZERO);
            return;
        }
        if (path.isEmpty()) {
            setVelocity(Vec3d.ZERO);
            if (vanishAtEnd) discard();
            return;
        }
        Vec3d t = path.get(0);
        Vec3d to = t.subtract(getPos());
        double d = to.length();
        double remain = d;
        for (int i = 0; i + 1 < path.size(); i++) remain += path.get(i).distanceTo(path.get(i + 1));
        double want = Math.min(maxSpeed, Math.max(0.03, remain * 0.03));
        speed += Math.signum(want - speed) * Math.min(Math.abs(want - speed), 0.006);
        if (d <= speed + 0.01) {
            setPosition(t);
            path.remove(0);
            if (path.isEmpty()) speed = 0;
            return;
        }
        Vec3d step = to.multiply(speed / d);
        setVelocity(step);
        setPosition(getPos().add(step));
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        setYaw(yaw);
        velocityDirty = true;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 300 * 300;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(HorrorMob.raw("animation.train.idle"))));
    }

    @Override
    public double getTick(Object o) {
        return animClock >= 0 ? animClock : age;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geo;
    }
}
