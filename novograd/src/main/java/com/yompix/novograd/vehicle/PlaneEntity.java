package com.yompix.novograd.vehicle;

import com.yompix.novograd.entity.CarEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Лёгкий самолёт (винтовой). W/S — газ больше/меньше (на земле S — тормоз), A/D — крен и поворот,
 * Пробел — нос вверх, Shift — нос вниз. Взлёт: разгон по полосе до ~70 км/ч и Пробел.
 * Меньше ~55 км/ч в воздухе — сваливание: нос падает. Выйти можно после посадки и остановки.
 */
public class PlaneEntity extends MotorVehicle {
    public static final int[] COLORS = {0xF2F4F7, 0xD7262C, 0xF2C032, 0x2C6ECB, 0x2A2C31};
    private static final double[][] SEATS = {{0, 0.3, 0.8}, {0, -0.6, 0.8}};
    private static final float TAKEOFF = 0.95f, MAX = 2.1f;
    public float throttle;
    public float prevProp, prop;

    public PlaneEntity(EntityType<? extends PlaneEntity> type, World world) {
        super(type, world);
    }

    public int bodyColor() {
        int p = color();
        return p != CarEntity.COLORS[getVariant()] ? p : COLORS[Math.min(getVariant(), COLORS.length - 1)];
    }

    @Override
    protected double[][] seats() {
        return SEATS;
    }

    @Override
    protected boolean flying() {
        return true;
    }

    @Override
    public float getStepHeight() {
        return 0.6f;
    }

    @Override
    protected double dismountSide() {
        return 2.0;
    }

    @Override
    public boolean engineRunning() {
        return getControllingPassenger() != null && !wrecked();
    }

    @Override
    public void tick() {
        prevProp = prop;
        float spin = engineRunning() ? 25 + Math.abs(clientSpeed) * 30 + throttle * 30 : 0;
        prop += spin;
        if (prop > 3600) {
            prop -= 3600;
            prevProp -= 3600;
        }
        super.tick();
    }

    @Override
    protected double crashSpeed() {
        return 0.8;
    }

    @Override
    protected void physics(float fwd, float side, boolean up, boolean down) {
        boolean ground = isOnGround();
        if (wrecked() || !engineRunning()) throttle = Math.max(0, throttle - 0.02f);
        else if (fwd > 0) throttle = Math.min(1, throttle + 0.015f);
        else if (fwd < 0) throttle = Math.max(0, throttle - 0.02f);
        float max = MAX * (1 + engine() * 0.08f) * (nitroOn() ? 1.3f : 1);
        float target = throttle * max;
        // разгон/торможение к скорости, которую даёт газ; на земле S тормозит колёсами
        if (speed < target) speed += 0.012f + (ground ? 0.006f : 0);
        else speed -= ground ? (fwd < 0 ? 0.04f : 0.01f) : 0.006f;
        speed = MathHelper.clamp(speed, 0, max);
        float pitch = getPitch(); // минус — нос вверх (как у игрока)
        boolean stall = !ground && speed < TAKEOFF * 0.8f;
        if (up && (speed > TAKEOFF * 0.85f || !ground)) pitch -= 1.6f;
        else if (down && !ground) pitch += 1.6f;
        else pitch = MathHelper.lerp(0.04f, pitch, 0);
        if (stall) pitch = Math.min(40, pitch + 1.2f);
        if (ground && pitch > 0) pitch = 0;
        pitch = MathHelper.clamp(pitch, -35, 45);
        setPitch(pitch);
        // поворот: в воздухе через крен, на земле — рулёжка
        steer = MathHelper.lerp(0.12f, steer, side);
        float turn = ground ? -steer * 2.2f * MathHelper.clamp(speed * 3, 0, 1) * (1 - Math.min(speed, 1) * 0.5f) : -steer * 2.0f;
        setYaw(getYaw() + turn);
        double rad = Math.toRadians(pitch);
        Vec3d dir = forward();
        double hs = speed * Math.cos(rad);
        double vy = -speed * Math.sin(rad);
        if (ground) {
            if (vy < 0 || speed < TAKEOFF) vy = -0.04;
        } else {
            double sink = speed < TAKEOFF ? (TAKEOFF - speed) * 0.5 : 0;
            vy -= sink + 0.02;
        }
        if (wrecked() && !ground) vy = Math.min(vy, getVelocity().y - 0.06);
        setVelocity(dir.x * hs, Math.max(vy, -2), dir.z * hs);
        move(MovementType.SELF, getVelocity());
        if (horizontalCollision) speed *= 0.2f;
    }

    @Override
    protected void clientEffects() {
        damageEffects(1.1);
        if (!engineRunning()) return;
        Vec3d f = forward();
        if (age % 2 == 0)
            getWorld().addParticle(ParticleTypes.SMOKE, getX() + f.x * 2.6 - f.z * 0.4, getY() + 1.0, getZ() + f.z * 2.6 + f.x * 0.4, -f.x * 0.1, 0.01, -f.z * 0.1);
        // след с концов крыльев на скорости
        if (Math.abs(clientSpeed) > 1.2f && !isOnGround()) {
            double rx = -f.z, rz = f.x;
            for (int s = -1; s <= 1; s += 2)
                getWorld().addParticle(ParticleTypes.CLOUD, getX() + rx * s * 4.4, getY() + 1.1, getZ() + rz * s * 4.4, 0, 0, 0);
        }
    }

    @Override
    public SoundEvent engineSound() {
        return Vehicles.PLANE_ENGINE;
    }

    @Override
    public float soundPitch() {
        return 0.6f + throttle * 0.5f + Math.abs(clientSpeed) * 0.15f;
    }

    @Override
    public float soundVolume() {
        return 0.6f + throttle * 1.8f;
    }

    @Override
    protected ItemStack pickStack() {
        return new ItemStack(Vehicles.PLANE_ITEM);
    }

    @Override
    protected String nameKey() {
        return "entity.novograd.plane";
    }

    @Override
    protected String controlsHint() {
        return "Самолёт: W/S — газ, A/D — поворот, Пробел — нос вверх, Shift — нос вниз";
    }
}
