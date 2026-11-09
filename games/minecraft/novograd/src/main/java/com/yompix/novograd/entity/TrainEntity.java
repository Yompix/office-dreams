package com.yompix.novograd.entity;

import com.yompix.novograd.world.Metro;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Поезд метро «Новоград — Ривьера»: четыре вагона, едет по эстакаде (world/Metro). Сам ничего не решает:
 * сервер (Metro) двигает «виртуальный» поезд всегда, а сущность появляется только рядом с игроками и повторяет его.
 * На клиенте положение предсказывается по скорости и плавно подтягивается к серверному — без рывков.
 * Пассажиры сидят в вагоне, угонщик — в кабине головного вагона.
 */
public class TrainEntity extends Entity {
    private static final TrackedData<Float> PROGRESS = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> SPEED = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> DOORS = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> TRAIN = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> DRIVER = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Клиент: предсказанное положение на линии (и прошлое — для плавной отрисовки), открытость дверей 0..1. */
    public double cp = -1, prevCp;
    public float doorOpen, prevDoorOpen;

    public TrainEntity(EntityType<? extends TrainEntity> type, World world) {
        super(type, world);
        noClip = true;
        ignoreCameraFrustum = true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        b.add(PROGRESS, 0f);
        b.add(SPEED, 0f);
        b.add(DOORS, false);
        b.add(TRAIN, 0);
        b.add(DRIVER, -1);
    }

    /** Сервер: поезд с номером id, положение s, скорость (со знаком — вдоль линии), двери, кто ведёт. */
    public void sync(int id, double s, double v, boolean doors, int driver) {
        dataTracker.set(TRAIN, id);
        dataTracker.set(PROGRESS, (float) s);
        dataTracker.set(SPEED, (float) v);
        dataTracker.set(DOORS, doors);
        dataTracker.set(DRIVER, driver);
        place(s);
    }

    public int trainId() {
        return dataTracker.get(TRAIN);
    }

    public double progress() {
        return getWorld().isClient && cp >= 0 ? cp : dataTracker.get(PROGRESS);
    }

    public float speed() {
        return dataTracker.get(SPEED);
    }

    public boolean doorsOpen() {
        return dataTracker.get(DOORS);
    }

    public int driverId() {
        return dataTracker.get(DRIVER);
    }

    private void place(double s) {
        Vec3d c = Metro.center(trainId(), s);
        prevX = getX();
        prevY = getY();
        prevZ = getZ();
        setPosition(c.x, Metro.D + 1, c.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            double target = dataTracker.get(PROGRESS);
            if (cp < 0 || Math.abs(target - cp) > 12) cp = target;
            prevCp = cp;
            cp += speed();
            cp += (target - cp) * 0.18;
            cp = Math.max(0, Math.min(Metro.LENGTH, cp));
            prevDoorOpen = doorOpen;
            doorOpen = Math.max(0, Math.min(1, doorOpen + (doorsOpen() ? 0.08f : -0.08f)));
            place(cp);
        } else if (age > 40 && !Metro.owns(this)) discard(); // поезд больше не нужен (игроки далеко или это старая копия)
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps) {
        // положение считаем сами из прогресса по линии
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        if (!hasPassenger(passenger)) return;
        Vec3d at = Metro.seat(this, passenger);
        updater.accept(passenger, at.x, at.y, at.z);
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        return Metro.dismount(this, passenger);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().size() < 12;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return null; // сервер ведёт поезд сам (угонщик — через Metro, по его W/S)
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 260 * 260;
    }

    @Override
    public Box getVisibilityBoundingBox() {
        return getBoundingBox().expand(20, 2, 20);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public boolean shouldSave() {
        return false;
    }
}
