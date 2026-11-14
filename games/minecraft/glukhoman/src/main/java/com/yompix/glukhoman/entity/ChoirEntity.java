package com.yompix.glukhoman.entity;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.horror.Noise;
import com.yompix.glukhoman.horror.Sanity;
import com.yompix.glukhoman.story.StoryState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Хор. Говорит голосами забранных. Не убивается. Идёт на шум и на близких. Луч фонаря оглушает на пару секунд.
 * chase — режим погони в шахте: всегда идёт за ближайшим.
 */
public class ChoirEntity extends HorrorMob {
    private static final TrackedData<Integer> STUN = DataTracker.registerData(ChoirEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private final ServerBossBar bar = new ServerBossBar(Text.literal("Хор"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);
    public boolean chase;
    private Vec3d goal;
    private BlockPos home;
    private int stunCd, attackCd, wanderCd;

    public ChoirEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        bar.setDarkenSky(true);
        bar.setThickenFog(true);
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 300).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 9).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1);
    }

    @Override
    public String geoName() {
        return "choir";
    }

    @Override
    protected String baseAnim() {
        if (stunTicks() > 0) return "stunned";
        double v = getVelocity().horizontalLengthSquared();
        if (v > 0.02) return "run";
        if (v > 0.002) return "walk";
        return "idle";
    }

    @Override
    protected int spawnActTicks() {
        return 40;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        super.initDataTracker(b);
        b.add(STUN, 0);
    }

    public int stunTicks() {
        return dataTracker.get(STUN);
    }

    public void stun(ServerPlayerEntity by) {
        if (stunTicks() > 0 || stunCd > 0) return;
        dataTracker.set(STUN, 50);
        stunCd = 130;
        getNavigation().stop();
        getWorld().playSound(null, getBlockPos(), GContent.sound("choir_hurt"), SoundCategory.HOSTILE, 1.2f, 1f);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) return super.damage(source, amount);
        if (!getWorld().isClient) getWorld().playSound(null, getBlockPos(), GContent.sound("choir_hurt"), SoundCategory.HOSTILE, 0.8f, 1.3f);
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w) || vanishing() || isRemoved()) return;
        if (home == null) home = getBlockPos();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.squaredDistanceTo(this) < 48 * 48) bar.addPlayer(p);
            else bar.removePlayer(p);
        }
        if (stunCd > 0) stunCd--;
        if (attackCd > 0) attackCd--;
        int s = stunTicks();
        if (age < 40) {
            getNavigation().stop();
            return;
        }
        if (s > 0) {
            dataTracker.set(STUN, s - 1);
            setVelocity(0, getVelocity().y, 0);
            if (s % 5 == 0) w.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 2, getZ(), 6, 0.6, 0.8, 0.6, 0.02);
            return;
        }
        int diff = StoryState.get(w).difficulty;
        double sp = (chase ? 1.55 : 1.25) * (diff == 0 ? 0.85 : diff == 2 ? 1.15 : 1.0);
        ServerPlayerEntity near = null;
        double nd = chase ? 80 * 80 : 7 * 7;
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (!Watch.playable(p)) continue;
            double d = p.squaredDistanceTo(this);
            if (d < nd) {
                nd = d;
                near = p;
            }
        }
        if (near != null) goal = near.getPos();
        else if (age % 10 == 0) {
            Noise.Event e = Noise.hear(w, getPos(), 1.0);
            if (e != null) goal = e.pos();
        }
        if (goal != null) {
            getNavigation().startMovingTo(goal.x, goal.y, goal.z, sp);
            if (goal.squaredDistanceTo(getPos()) < 3) goal = null;
        } else if (--wanderCd <= 0) {
            wanderCd = 60 + random.nextInt(80);
            BlockPos t = home.add(random.nextInt(21) - 10, 0, random.nextInt(21) - 10);
            getNavigation().startMovingTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, 0.8);
        }
        if (near != null && nd < 2.6 * 2.6 && attackCd == 0) {
            tryAttack(near);
            act("attack", 16);
            Sanity.add(near, -20);
            attackCd = 30;
        }
        if (age < 40) return; // поднимается из пола
        if (age % 100 == 0) w.playSound(null, getBlockPos(), GContent.sound(random.nextInt(3) == 0 ? "choir_roar" : "choir_hum"), SoundCategory.HOSTILE, 1.6f, 0.9f + random.nextFloat() * 0.2f);
    }

    @Override
    public float getSoundPitch() {
        return 1f;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        bar.clearPlayers();
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity p) {
        super.onStoppedTrackingBy(p);
        bar.removePlayer(p);
    }



    @Override
    public boolean isPushable() {
        return false;
    }
}
