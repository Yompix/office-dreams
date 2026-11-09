package com.yompix.novograd.vehicle;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Катер: ходит только по воде, качается на волнах, задирает нос на скорости, заносит в поворотах,
 * за кормой — пенный след и брызги. На берегу садится на мель.
 */
public class SpeedboatEntity extends MotorVehicle {
    public static final int[] COLORS = {0xF2F4F7, 0xD7262C, 0x1E5AA8, 0x111316, 0xF2C032};
    /** Насколько корпус сидит в воде. */
    public static final double DRAFT = 0.3;
    private static final double[][] SEATS = {{-0.35, 0.15, 0.56}, {0.35, 0.15, 0.56}, {-0.35, -0.95, 0.5}, {0.35, -0.95, 0.5}};
    private double hvx, hvz;

    public SpeedboatEntity(EntityType<? extends SpeedboatEntity> type, World world) {
        super(type, world);
    }

    public int hullColor() {
        int p = color();
        return p != com.yompix.novograd.entity.CarEntity.COLORS[getVariant()] ? p : COLORS[Math.min(getVariant(), COLORS.length - 1)];
    }

    @Override
    protected double[][] seats() {
        return SEATS;
    }

    @Override
    public float getStepHeight() {
        return 0.0f;
    }

    /** Уровень воды под катером (или NaN, если воды нет). */
    public double waterSurface() {
        BlockPos base = BlockPos.ofFloored(getX(), getY() + 0.6, getZ());
        for (int dy = 0; dy >= -2; dy--) {
            BlockPos p = base.up(dy);
            FluidState f = getWorld().getFluidState(p);
            if (f.isIn(FluidTags.WATER)) {
                // вода выше — значит мы под водой: всплываем к верхнему блоку
                BlockPos up = p.up();
                while (getWorld().getFluidState(up).isIn(FluidTags.WATER) && up.getY() < p.getY() + 4) up = up.up();
                BlockPos top = up.down();
                return top.getY() + getWorld().getFluidState(top).getHeight(getWorld(), top);
            }
        }
        return Double.NaN;
    }

    @Override
    protected void physics(float fwd, float side, boolean up, boolean down) {
        double surf = waterSurface();
        boolean water = !Double.isNaN(surf);
        float max = 1.15f * (1 + engine() * 0.1f);
        if (nitroOn()) max *= 1.4f;
        double vy = getVelocity().y;
        float turn = 0;
        if (water) {
            if (fwd > 0) speed += (nitroOn() ? 0.04f : 0.022f) * (1 - speed / max * 0.7f);
            else if (fwd < 0) speed = speed > 0.02f ? speed - 0.03f : Math.max(speed - 0.01f, -0.22f);
            else {
                speed *= 0.985f;
                if (Math.abs(speed) < 0.004f) speed = 0;
            }
            speed = MathHelper.clamp(speed, -0.22f, max);
            steer = MathHelper.lerp(0.2f, steer, side);
            float grip = MathHelper.clamp(Math.abs(speed) * 5, 0.25f, 1);
            turn = -steer * 3.4f * grip * (speed < 0 ? -1 : 1) * (1 - Math.min(Math.abs(speed), 1f) * 0.3f);
            // держимся на поверхности: мягкая пружина + лёгкая волна
            double wave = getControllingPassenger() != null ? Math.sin((age + getId() * 7) * 0.12) * 0.04 : 0; // пустой стоит ровно (качку рисует модель)
            double target = surf - DRAFT + wave - (wrecked() ? 1.6 : 0) + Math.min(Math.abs(speed), 1) * 0.08;
            vy = (target - getY()) * 0.25 + vy * 0.5;
        } else {
            // на мели или в воздухе (слетели с волны)
            speed *= isOnGround() ? 0.55f : 0.99f;
            vy = isOnGround() ? -0.04 : Math.max((vy - 0.08) * 0.98, -1.5);
            steer = MathHelper.lerp(0.2f, steer, 0);
        }
        setYaw(getYaw() + turn);
        Vec3d dir = forward();
        // занос: скорость догоняет направление носа не сразу
        double slip = water ? 0.14 : 0.5;
        hvx = MathHelper.lerp(slip, hvx, dir.x * speed);
        hvz = MathHelper.lerp(slip, hvz, dir.z * speed);
        setVelocity(hvx, vy, hvz);
        move(MovementType.SELF, getVelocity());
        if (horizontalCollision && Math.abs(speed) > 0.1f) {
            speed *= 0.3f;
            hvx *= 0.3;
            hvz *= 0.3;
        }
    }

    @Override
    protected double crashSpeed() {
        return 0.9;
    }

    @Override
    protected float crashDamage() {
        return 12;
    }

    @Override
    protected void clientEffects() {
        damageEffects(0.9);
        float s = Math.abs(clientSpeed);
        if (Double.isNaN(waterSurface())) return;
        double rad = Math.toRadians(getYaw());
        double fx = -Math.sin(rad), fz = Math.cos(rad), rx = -Math.cos(rad), rz = -Math.sin(rad);
        double y = waterSurface() + 0.05;
        var w = getWorld();
        if (s > 0.08) {
            // пенный след за кормой
            for (int i = 0; i < 1 + (int) (s * 4); i++) {
                double back = -2.0 - random.nextDouble() * 0.6, lat = (random.nextDouble() - 0.5) * 1.2;
                w.addParticle(ParticleTypes.FISHING, getX() + fx * back + rx * lat, y, getZ() + fz * back + rz * lat, 0, 0, 0);
            }
            // брызги из-под винта и от носа
            for (int i = 0; i < (int) (s * 6); i++) {
                double back = -2.1, lat = (random.nextDouble() - 0.5) * 0.8;
                w.addParticle(ParticleTypes.SPLASH, getX() + fx * back + rx * lat, y + 0.1, getZ() + fz * back + rz * lat, -fx * 0.2, 0.15, -fz * 0.2);
            }
            if (s > 0.4)
                for (int side = -1; side <= 1; side += 2) {
                    double lat = side * 0.95;
                    w.addParticle(ParticleTypes.SPLASH, getX() + fx * 1.2 + rx * lat, y + 0.15, getZ() + fz * 1.2 + rz * lat, rx * side * 0.25, 0.2, rz * side * 0.25);
                }
            if (age % 3 == 0) w.addParticle(ParticleTypes.BUBBLE, getX() - fx * 2, y - 0.4, getZ() - fz * 2, 0, 0.05, 0);
        } else if (engineRunning() && age % 6 == 0) {
            w.addParticle(ParticleTypes.BUBBLE, getX() - fx * 2.1, y - 0.3, getZ() - fz * 2.1, 0, 0.05, 0);
        }
    }

    @Override
    public SoundEvent engineSound() {
        return Vehicles.BOAT_ENGINE;
    }

    @Override
    public float soundPitch() {
        return 0.7f + Math.abs(clientSpeed) * 1.0f;
    }

    @Override
    public float soundVolume() {
        return 0.5f + Math.abs(clientSpeed) * 0.6f;
    }

    @Override
    public boolean engineRunning() {
        return getControllingPassenger() != null && !wrecked();
    }

    @Override
    protected ItemStack pickStack() {
        return new ItemStack(Vehicles.SPEEDBOAT_ITEM);
    }

    @Override
    protected String nameKey() {
        return "entity.novograd.speedboat";
    }

    @Override
    protected String controlsHint() {
        return "Катер: W/S — газ/реверс, A/D — руль";
    }

    @Override
    protected double dismountSide() {
        return 1.6;
    }
}
