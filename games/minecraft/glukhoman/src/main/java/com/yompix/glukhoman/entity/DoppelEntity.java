package com.yompix.glukhoman.entity;

import com.yompix.glukhoman.GContent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Двойник: Хор надевает чужой облик — Анны или друга по кооперативу (его скин и ник над головой).
 * Стоит слишком неподвижно и машет. Подойдёшь или засмотришься — голова поворачивается на 180°, и он убегает в лес.
 */
public class DoppelEntity extends HorrorMob {
    private static final TrackedData<String> MIMIC = DataTracker.registerData(DoppelEntity.class, TrackedDataHandlerRegistry.STRING);
    private int life, seen, phase, phaseT;

    public DoppelEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 40).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        super.initDataTracker(b);
        b.add(MIMIC, "");
    }

    /** "@anna" — облик Анны; иначе ник игрока, чей скин он носит. */
    public String mimic() {
        return dataTracker.get(MIMIC);
    }

    public void setMimic(String who) {
        dataTracker.set(MIMIC, who);
        setCustomName(Text.literal(who.equals("@anna") ? "Анна" : who));
        setCustomNameVisible(!who.isEmpty());
    }

    @Override
    public String geoName() {
        return "human";
    }

    @Override
    protected String baseAnim() {
        double v = getVelocity().horizontalLengthSquared();
        if (v > 0.02) return "run";
        if (v > 0.002) return "walk";
        return (age / 120) % 2 == 0 ? "slowwave" : "still";
    }

    @Override
    protected int spawnActTicks() {
        return 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w) || vanishing() || isRemoved()) return;
        life++;
        ServerPlayerEntity t = w.getClosestPlayer(this, 64) instanceof ServerPlayerEntity sp ? sp : null;
        if (t == null || life > 1400) {
            fade();
            return;
        }
        if (phase == 0) {
            double dx = t.getX() - getX(), dz = t.getZ() - getZ();
            float yaw = (float) (MathHelper.atan2(dz, dx) * 57.2957795) - 90f;
            setYaw(yaw);
            setBodyYaw(yaw);
            setHeadYaw(yaw);
            if (Watch.observer(w, this, 64, 0.7) != null) seen++;
            if (distanceTo(t) < 12 || seen > 70) {
                // отворачивается телом, а голова остаётся на тебе — поворот на 180°
                setYaw(yaw + 180);
                setBodyYaw(yaw + 180);
                act("turn180", 26);
                w.playSound(null, getBlockPos(), GContent.sound("pole_step"), SoundCategory.HOSTILE, 1f, 0.6f);
                phase = 1;
            }
        } else if (phase == 1 && ++phaseT > 26) {
            phase = 2;
            phaseT = 0;
            Vec3d away = getPos().subtract(t.getPos()).normalize().multiply(30).add(getPos());
            getNavigation().startMovingTo(away.x, away.y, away.z, 2.0);
        } else if (phase == 2 && ++phaseT > 70) fade();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) return super.damage(source, amount);
        if (!getWorld().isClient) fade();
        return false;
    }
}
