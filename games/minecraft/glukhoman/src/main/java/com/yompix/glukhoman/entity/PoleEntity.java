package com.yompix.glukhoman.entity;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.horror.Director;
import com.yompix.glukhoman.horror.Sanity;
import com.yompix.glukhoman.net.Net;
import com.yompix.glukhoman.story.Story;
import com.yompix.glukhoman.story.StoryState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Жердь. Высокая фигура в коре с пустым лицом. Двигается, только когда на неё никто не смотрит.
 * Видение (главы 1–2): стоит у кромки леса и исчезает от взгляда. Охотник (глава 3): подбирается, пока ты отвернулся.
 */
public class PoleEntity extends HorrorMob {
    private static final TrackedData<Boolean> APPARITION = DataTracker.registerData(PoleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> PEEK = DataTracker.registerData(PoleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> FROZEN = DataTracker.registerData(PoleEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private boolean apparition;
    private UUID jumpFor;
    private int life, seenTicks, unseenTicks, cooldown, stepTick, stareTicks, followTicks, fleeCd;
    private boolean hunting;

    public PoleEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        experiencePoints = 0;
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 200).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1);
    }

    @Override
    public String geoName() {
        return "pole";
    }

    @Override
    protected String baseAnim() {
        if (apparitionClient()) return peeking() ? "peek" : "still";
        double v = getVelocity().horizontalLengthSquared();
        if (v > 0.03) return "run";
        if (v > 0.002) return "walk";
        return isFrozen() ? "still" : "idle";
    }

    @Override
    protected int spawnActTicks() {
        return 0;
    }

    private boolean apparitionClient() {
        return dataTracker.get(APPARITION);
    }

    public boolean peeking() {
        return dataTracker.get(PEEK);
    }

    public void setPeek(boolean p) {
        dataTracker.set(PEEK, p);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        b.add(APPARITION, false);
        b.add(PEEK, false);
        super.initDataTracker(b);
        b.add(FROZEN, true);
    }

    public boolean isFrozen() {
        return dataTracker.get(FROZEN);
    }

    public boolean isApparition() {
        return apparition || apparitionClient();
    }

    public void makeApparition(UUID jump) {
        apparition = true;
        dataTracker.set(APPARITION, true);
        jumpFor = jump;
    }

    public void lookAtPlayer(ServerPlayerEntity p) {
        double dx = p.getX() - getX(), dz = p.getZ() - getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * 57.2957795) - 90f;
        setYaw(yaw);
        setHeadYaw(yaw);
        setBodyYaw(yaw);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) return super.damage(source, amount);
        if (!getWorld().isClient && source.getAttacker() instanceof ServerPlayerEntity p) vanish();
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w) || vanishing() || isRemoved()) return;
        life++;
        ServerPlayerEntity seer = Watch.observer(w, this, 72, 0.62);
        if (apparition) {
            ServerPlayerEntity near = w.getClosestPlayer(this, 80) instanceof ServerPlayerEntity sp ? sp : null;
            if (near != null) lookAtPlayer(near);
            setVelocity(0, getVelocity().y, 0);
            if (seer != null) {
                seenTicks++;
                if (jumpFor != null && seer.getUuid().equals(jumpFor)) {
                    jumpScare(seer);
                    vanish();
                } else if (seenTicks > 8 + random.nextInt(10)) vanish();
            }
            if (life > 600) vanish();
            return;
        }
        // охотник
        if (cooldown > 0) cooldown--;
        ServerPlayerEntity target = nearestTarget(w);
        boolean frozen = seer != null || target == null;
        dataTracker.set(FROZEN, frozen);
        if (fleeCd > 0) fleeCd--;
        if (seer != null && seer.distanceTo(this) < 12) {
            if (++stareTicks == 50 && act().isEmpty()) act("tilt", 44);
        } else stareTicks = 0;
        if (frozen) {
            getNavigation().stop();
            setVelocity(0, getVelocity().y, 0);
            unseenTicks = 0;
            if (target != null) lookAtPlayer(target);
            return;
        }
        unseenTicks++;
        int diff = StoryState.get(w).difficulty;
        double speed = diff == 0 ? 1.0 : diff == 2 ? 1.75 : 1.35;
        double dist = distanceTo(target);
        if (Story.inSafe(target) && dist < 14) {
            getNavigation().stop();
            return;
        }
        if (dist > 30 && unseenTicks > 40 && cooldown == 0) {
            teleportNear(w, target, 15 + random.nextInt(5));
            cooldown = 60;
            return;
        }
        // сначала только следует на расстоянии; охотится, когда игрок один или долго не оборачивается
        if (!hunting) {
            boolean alone = w.getPlayers().stream().noneMatch(o -> o != target && Watch.playable(o) && o.squaredDistanceTo(target) < 14 * 14);
            if (++followTicks > (alone ? 500 : 900) / (diff + 1)) hunting = true;
            if (dist < 16) {
                getNavigation().stop();
                lookAtPlayer(target);
                if (random.nextInt(60) == 0 && act().isEmpty()) act("twitch", 40);
                return;
            }
            if (dist < 26) {
                getNavigation().stop();
                return;
            }
            speed *= 0.8;
        }
        getNavigation().startMovingTo(target, speed);
        if (++stepTick % 12 == 0) w.playSound(null, getBlockPos(), GContent.sound("pole_step"), SoundCategory.HOSTILE, 0.7f, 0.8f + random.nextFloat() * 0.2f);
        if (dist < 1.9 && cooldown == 0) {
            float dmg = (float) getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) * (diff == 0 ? 0.6f : diff == 2 ? 1.6f : 1f);
            target.damage(getDamageSources().mobAttack(this), dmg);
            Sanity.add(target, -30);
            jumpScare(target);
            act("attack", 16);
            teleportNear(w, target, 34);
            cooldown = 200;
            hunting = false;
            followTicks = 0;
        }
    }

    /** Луч фонаря: видение исчезает, охотник отступает в лес. */
    public void lit(ServerPlayerEntity by) {
        if (!(getWorld() instanceof ServerWorld w) || vanishing()) return;
        if (isApparition()) {
            vanish();
            return;
        }
        if (fleeCd > 0) return;
        fleeCd = 220;
        hunting = false;
        followTicks = 0;
        w.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 1.8, getZ(), 20, 0.3, 1.2, 0.3, 0.01);
        w.playSound(null, getBlockPos(), GContent.sound("pole_vanish"), SoundCategory.HOSTILE, 0.9f, 0.8f);
        teleportNear(w, by, 32);
    }

    ServerPlayerEntity nearestTarget(ServerWorld w) {
        ServerPlayerEntity best = null;
        double bd = 96 * 96;
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (!Watch.playable(p)) continue;
            double d = p.squaredDistanceTo(this);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    void jumpScare(ServerPlayerEntity p) {
        boolean soft = Director.NO_SCARES.contains(p.getUuid());
        getWorld().playSound(null, p.getBlockPos(), GContent.sound(soft ? "stinger_soft" : "stinger"), SoundCategory.HOSTILE, soft ? 0.6f : 1.2f, 1f);
        if (!soft) Director.jumpscare(p, "pole");
        else {
            NbtCompound fx = Net.msg("fx");
            fx.putString("k", "shake");
            Net.send(p, fx);
        }
        Sanity.add(p, -12);
    }

    /** Переместиться за спину цели, в место, которое никто не видит. */
    void teleportNear(ServerWorld w, ServerPlayerEntity t, double dist) {
        Vec3d look = t.getRotationVec(1f).multiply(1, 0, 1).normalize();
        for (int i = 0; i < 16; i++) {
            double ang = Math.PI + (random.nextDouble() - 0.5) * 2.2;
            Vec3d dir = new Vec3d(look.x * Math.cos(ang) - look.z * Math.sin(ang), 0, look.x * Math.sin(ang) + look.z * Math.cos(ang));
            Vec3d at = t.getPos().add(dir.multiply(dist));
            int x = MathHelper.floor(at.x), z = MathHelper.floor(at.z);
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - t.getBlockY()) > 8) continue;
            Vec3d old = getPos();
            refreshPositionAndAngles(x + 0.5, y, z + 0.5, getYaw(), 0);
            if (Watch.observer(w, this, 72, 0.5) == null) return;
            refreshPositionAndAngles(old.x, old.y, old.z, getYaw(), 0);
        }
    }

    public void vanish() {
        if (getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 1.8, getZ(), 30, 0.3, 1.2, 0.3, 0.01);
            w.playSound(null, getBlockPos(), GContent.sound("pole_vanish"), SoundCategory.HOSTILE, 0.8f, 1f);
        }
        fade();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("apparition", apparition);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        apparition = nbt.getBoolean("apparition");
        if (apparition) discard(); // видения не переживают перезапуск
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
