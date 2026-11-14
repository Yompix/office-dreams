package com.yompix.glukhoman.entity;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.horror.Noise;
import com.yompix.glukhoman.horror.Sanity;
import com.yompix.glukhoman.story.StoryState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Слухач. Слепой, ползает, живёт звуком. Идёт на шум (бег, прыжки, двери, камешки), прислушивается и щёлкает.
 * Крадущегося игрока замечает только вплотную. Иногда повторяет голоса, которые слышал.
 */
public class ListenerEntity extends HostileEntity {
    private static final TrackedData<Integer> MODE = DataTracker.registerData(ListenerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final int IDLE = 0, HUNT = 1, LISTEN = 2;
    private Vec3d goal;
    private BlockPos home;
    private int listenTicks, attackCd, wanderCd;

    public ListenerEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 40).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.24)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5).add(EntityAttributes.GENERIC_ARMOR, 6);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        super.initDataTracker(b);
        b.add(MODE, IDLE);
    }

    public int mode() {
        return dataTracker.get(MODE);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        if (home == null) home = getBlockPos();
        if (attackCd > 0) attackCd--;
        int diff = StoryState.get(w).difficulty;
        double hearing = diff == 0 ? 0.7 : diff == 2 ? 1.3 : 1.0;
        if (age % 5 == 0) {
            Noise.Event e = Noise.hear(w, getPos(), hearing);
            if (e != null && (goal == null || e.pos().squaredDistanceTo(goal) > 4)) {
                if (mode() != HUNT) w.playSound(null, getBlockPos(), GContent.sound("listener_scream"), SoundCategory.HOSTILE, 0.9f, 1.1f);
                goal = e.pos();
                dataTracker.set(MODE, HUNT);
            }
        }
        // вплотную замечает любого, крадущегося — с шансом
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (!Watch.playable(p)) continue;
            double d = p.distanceTo(this);
            if (d < 1.6 || (d < 3 && !p.isSneaking())) {
                goal = p.getPos();
                dataTracker.set(MODE, HUNT);
                if (d < 1.7 && attackCd == 0) {
                    tryAttack(p);
                    Sanity.add(p, -15);
                    attackCd = 24;
                }
            }
        }
        switch (mode()) {
            case HUNT -> {
                double sp = diff == 0 ? 1.3 : diff == 2 ? 1.9 : 1.6;
                getNavigation().startMovingTo(goal.x, goal.y, goal.z, sp);
                if (goal.squaredDistanceTo(getPos()) < 2.5 || getNavigation().isIdle() && age % 20 == 0) {
                    dataTracker.set(MODE, LISTEN);
                    listenTicks = 60 + random.nextInt(40);
                    getNavigation().stop();
                }
            }
            case LISTEN -> {
                if (age % 15 == 0) w.playSound(null, getBlockPos(), GContent.sound("listener_click"), SoundCategory.HOSTILE, 0.8f, 0.9f + random.nextFloat() * 0.3f);
                if (--listenTicks <= 0) {
                    dataTracker.set(MODE, IDLE);
                    goal = null;
                }
            }
            default -> {
                if (--wanderCd <= 0) {
                    wanderCd = 80 + random.nextInt(120);
                    BlockPos t = home.add(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
                    getNavigation().startMovingTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, 0.7);
                }
                if (random.nextInt(900) == 0) w.playSound(null, getBlockPos(), GContent.sound("voice.mimic_2"), SoundCategory.HOSTILE, 0.8f, 0.85f);
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return GContent.sound("listener_click");
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 70;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return GContent.sound("listener_hurt");
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GContent.sound("listener_scream");
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean r = super.damage(source, amount);
        if (r && source.getAttacker() != null) {
            goal = source.getAttacker().getPos();
            dataTracker.set(MODE, HUNT);
        }
        return r;
    }

    @Override
    public boolean canImmediatelyDespawn(double d) {
        return false;
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return false;
    }
}
