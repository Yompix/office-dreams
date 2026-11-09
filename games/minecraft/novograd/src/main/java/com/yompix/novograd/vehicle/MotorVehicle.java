package com.yompix.novograd.vehicle;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Общая основа катера, вертолёта и самолёта. Наследуется от машины, чтобы работали пули (Weapons), здоровье,
 * взрыв, хозяин, «забрать в инвентарь» — но физику и тик пишет сам: машинная езда (drive) не вызывается.
 * Физику считает клиент водителя (как у машины), без водителя — сервер.
 */
public abstract class MotorVehicle extends CarEntity {
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYaw, lerpPitch;
    private float tickYawDelta;
    private int yawTick = -1;
    private static final Map<Entity, Integer> YAW_DONE = new WeakHashMap<>();

    /** Сервер: прошлый шаг (для ударов о стены и жёстких посадок — сервер не считает столкновения машины игрока). */
    private Vec3d lastPos;
    private double lastH, lastVy;
    protected int groundTicks, wreckAge;
    private long warnAt;

    /** Клиент: анимации. */
    public float prevTilt, tilt, prevBank, bank;

    protected MotorVehicle(EntityType<? extends MotorVehicle> type, World world) {
        super(type, world);
        setNoGravity(true); // гравитацию считаем сами; иначе сервер кикает за «парящий транспорт»
    }

    // ---------------------------------------------------------------- что задают наследники
    /** Посадочные места: {вправо, вперёд, высота подушки над низом}. */
    protected abstract double[][] seats();

    protected abstract void physics(float fwd, float side, boolean up, boolean down);

    public abstract SoundEvent engineSound();

    public abstract float soundPitch();

    public abstract float soundVolume();

    public abstract boolean engineRunning();

    protected abstract ItemStack pickStack();

    protected abstract String nameKey();

    /** Летающий транспорт: выходить можно только на земле. */
    protected boolean flying() {
        return false;
    }

    protected double dismountSide() {
        return 1.8;
    }

    protected void clientEffects() {}

    protected void serverTick() {}

    // ---------------------------------------------------------------- тик
    @Override
    public void tick() {
        baseTick();
        float yawBefore = getYaw();
        if (!getWorld().isClient) serverCommon();
        if (isLogicalSideForUpdatingMovement()) {
            lerpSteps = 0;
            updateTrackedPosition(getX(), getY(), getZ());
            float fwd = 0, side = 0;
            boolean up = false, down = false;
            if (getControllingPassenger() instanceof PlayerEntity p && !wrecked()) {
                fwd = p.forwardSpeed;
                side = p.sidewaysSpeed;
                if (getWorld().isClient) {
                    up = Vehicles.inUp;
                    down = Vehicles.inDown;
                }
            }
            physics(fwd, side, up, down);
        } else if (lerpSteps > 0) {
            lerpPosAndRotation(lerpSteps, lerpX, lerpY, lerpZ, lerpYaw, lerpPitch);
            lerpSteps--;
        }
        tickYawDelta = MathHelper.wrapDegrees(getYaw() - yawBefore);
        yawTick = age;
        if (getWorld().isClient) {
            animate();
            clientEffects();
            Vehicles.clientTick.accept(this);
        }
        checkBlockCollision();
    }

    private void serverCommon() {
        // Shift в воздухе — не выход, а снижение: гасим «присед» до того, как игра высадит игрока
        boolean landed = landed();
        groundTicks = landed ? groundTicks + 1 : 0;
        if (flying() && getControllingPassenger() instanceof ServerPlayerEntity sp && sp.isSneaking() && (groundTicks < 15 || lastH > 0.25)) {
            sp.setSneaking(false);
            if (getWorld().getTime() > warnAt) {
                warnAt = getWorld().getTime() + 60;
                sp.sendMessage(Text.literal(landed ? "Дай машине остановиться — потом выход" : "Shift — снижение. Выйти можно после посадки"), true);
            }
        }
        // удары: резко остановились на скорости или жёстко сели
        Vec3d pos = getPos();
        if (lastPos != null && !wrecked()) {
            Vec3d d = pos.subtract(lastPos);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            // удар засчитываем, только если и на следующем тике стоим (пропущенный пакет — не авария)
            if (d.lengthSquared() < 400) {
                if (pendingCrash > 0) {
                    if (h < 0.3 && d.y > -0.15) crash(pendingCrash);
                    pendingCrash = 0;
                } else if (lastH > crashSpeed() && h < lastH * 0.35) pendingCrash = (float) (lastH - h) * crashDamage();
                else if (lastVy < -0.55 && d.y > -0.08 && landed) pendingCrash = (float) -lastVy * crashDamage();
            }
            lastH = h;
            lastVy = d.y;
        }
        lastPos = pos;
        if (wrecked()) {
            if (++wreckAge > 600) {
                for (Entity e : getPassengerList()) if (e instanceof CitizenEntity) e.discard();
                discard();
            }
        }
        serverTick();
    }

    protected double crashSpeed() {
        return 0.7;
    }

    /** Урон за удар на скорости 1 блок/тик (до множителя брони машины ×2.2). */
    protected float crashDamage() {
        return 28;
    }

    private float pendingCrash;

    protected void crash(float dmg) {
        getWorld().playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.NEUTRAL, 1.2f, 0.7f);
        for (Entity e : getPassengerList()) e.damage(getWorld().getDamageSources().flyIntoWall(), Math.max(0, dmg * 0.15f - 1));
        hitByBullet(dmg, null);
    }

    /** Стоит на чём-то (или на воде). Сервер не знает onGround машины игрока — проверяем сами. */
    public boolean landed() {
        return !getWorld().isSpaceEmpty(this, getBoundingBox().offset(0, -0.25, 0)) || isTouchingWater();
    }

    private void animate() {
        prevTilt = tilt;
        prevBank = bank;
        prevRoll = roll;
        prevPitchLean = pitchLean;
        double rad = Math.toRadians(getYaw());
        double dx = getX() - prevX, dz = getZ() - prevZ;
        float lastSpeed = clientSpeed;
        clientSpeed = (float) (dx * -Math.sin(rad) + dz * Math.cos(rad));
        float yawRate = MathHelper.wrapDegrees(getYaw() - prevYaw);
        roll = MathHelper.lerp(0.12f, roll, MathHelper.clamp(yawRate * Math.abs(clientSpeed) * 0.8f, -2.5f, 2.5f));
        pitchLean = MathHelper.lerp(0.1f, pitchLean, MathHelper.clamp((clientSpeed - lastSpeed) * -30f, -1.5f, 1.5f));
        bank = MathHelper.lerp(0.15f, bank, MathHelper.clamp(yawRate, -6, 6));
        tilt = MathHelper.lerp(0.12f, tilt, clientSpeed);
    }

    // ---------------------------------------------------------------- посадка
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().size() < seats().length;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldDismountUnderwater() {
        return false;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.shouldCancelInteraction()) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (wrecked()) {
            player.sendMessage(Text.literal("Разбит — уже не починить"), true);
            return ActionResult.CONSUME;
        }
        if (hasNpcDriver()) {
            player.sendMessage(Text.literal("Сюда не сесть"), true);
            return ActionResult.CONSUME;
        }
        boolean driver = getPassengerList().isEmpty();
        if (driver && getOwner() != null && !getOwner().equals(player.getUuid()) && !player.isCreative() && !stealable()) {
            player.sendMessage(Text.literal("Чужой транспорт: управляет только хозяин"), true);
            return ActionResult.CONSUME;
        }
        if (player.startRiding(this) && driver) {
            if (player instanceof ServerPlayerEntity sp && getOwner() != null && !getOwner().equals(sp.getUuid()) && !sp.isCreative())
                com.yompix.novograd.gta.Crime.carjack(sp);
            getWorld().playSound(null, getBlockPos(), com.yompix.novograd.ModContent.CAR_START, SoundCategory.NEUTRAL, 0.8f, 0.8f);
            player.sendMessage(Text.literal(controlsHint()), true);
        }
        return ActionResult.CONSUME;
    }

    /** Транспорт на острове без хозяина (поставлен миром) можно просто взять. */
    protected boolean stealable() {
        return false;
    }

    protected String controlsHint() {
        return "W/S — газ/тормоз, A/D — поворот";
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        if (!hasPassenger(passenger)) return;
        double[][] s = seats();
        double[] seat = s[Math.max(0, Math.min(seatOf(passenger), s.length - 1))];
        double rad = Math.toRadians(getYaw());
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -Math.cos(rad), rz = -Math.sin(rad);
        float sc = passenger instanceof LivingEntity le ? le.getScale() : 1f;
        updater.accept(passenger, getX() + fx * seat[1] + rx * seat[0], getY() + seat[2] - 0.75 * sc, getZ() + fz * seat[1] + rz * seat[0]);
        if (yawTick == age && YAW_DONE.getOrDefault(passenger, -1) != age) {
            YAW_DONE.put(passenger, age);
            passenger.setYaw(passenger.getYaw() + tickYawDelta);
            passenger.setHeadYaw(passenger.getHeadYaw() + tickYawDelta);
        }
        if (passenger instanceof LivingEntity le) le.setBodyYaw(getYaw());
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        double rad = Math.toRadians(getYaw());
        double side = seats()[Math.min(seatOf(passenger), seats().length - 1)][0] < 0 ? -dismountSide() : dismountSide();
        for (double up : new double[]{0, 1, 2}) {
            Vec3d out = new Vec3d(getX() - Math.cos(rad) * side, getY() + up, getZ() - Math.sin(rad) * side);
            if (getWorld().isSpaceEmpty(passenger, passenger.getDimensions(passenger.getPose()).getBoxAt(out))) return out;
        }
        return new Vec3d(getX(), getBoundingBox().maxY, getZ());
    }

    // ---------------------------------------------------------------- плавность для тех, кто не за рулём
    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        lerpPitch = pitch;
        lerpSteps = 3;
    }

    @Override
    public double getLerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double getLerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double getLerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float getLerpTargetYaw() {
        return lerpSteps > 0 ? (float) lerpYaw : getYaw();
    }

    @Override
    public float getLerpTargetPitch() {
        return lerpSteps > 0 ? (float) lerpPitch : getPitch();
    }

    // ---------------------------------------------------------------- прочее
    @Override
    public ItemStack getPickBlockStack() {
        return pickStack();
    }

    @Override
    protected Text getDefaultName() {
        return Text.translatable(nameKey());
    }

    /** Огонь и дым у разбитого/подбитого (машинный тик у нас не вызывается). */
    protected void damageEffects(double up) {
        var w = getWorld();
        if (health() < 35 && age % 3 == 0) w.addParticle(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, getX(), getY() + up, getZ(), 0, 0.06, 0);
        if (wrecked() && age % 2 == 0)
            w.addParticle(net.minecraft.particle.ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5) * 1.5, getY() + up * 0.6, getZ() + (random.nextDouble() - 0.5) * 1.5, 0, 0.05, 0);
    }

    /** Направление «вперёд» по повороту. */
    protected Vec3d forward() {
        double rad = Math.toRadians(getYaw());
        return new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
    }
}
