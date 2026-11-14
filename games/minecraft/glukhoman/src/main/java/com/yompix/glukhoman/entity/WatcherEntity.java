package com.yompix.glukhoman.entity;

import com.yompix.glukhoman.story.Cam;
import com.yompix.glukhoman.story.Night;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Обходчик. Савва Тихонов, путевой обходчик 1991-го: высокий, худой, в тужурке, с фонарём и камерой без кассеты.
 * Глазами виден и ходит. В видоискателе его нет — но пока на него смотрит поднятая камера, он не может сдвинуться
 * («плёнка держит»). Учится: приходит туда, где ты чаще опускаешь камеру и где прячешься (Night.learnedSpot).
 * Не убивает сразу: вне кадра и в одиночку сначала вырывает плёнку.
 */
public class WatcherEntity extends HorrorMob implements Seen {
    private static final TrackedData<Boolean> FROZEN = DataTracker.registerData(WatcherEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private int close, relocate;
    /** Сценарный режим: стоит в точке и никуда не идёт. */
    public boolean posted;

    public WatcherEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        experiencePoints = 0;
        setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return HostileEntity.createHostileAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 200).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64).add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 0).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.1);
    }

    @Override
    public int seen() {
        return EYE_ONLY;
    }

    @Override
    public String geoName() {
        return "watcher";
    }

    @Override
    protected int spawnActTicks() {
        return 0;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        super.initDataTracker(b);
        b.add(FROZEN, false);
    }

    public boolean frozen() {
        return dataTracker.get(FROZEN);
    }

    @Override
    protected String baseAnim() {
        if (frozen()) return "still";
        return getVelocity().horizontalLengthSquared() > 0.002 ? "walk" : "idle";
    }

    @Override
    protected void initGoals() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        boolean f = Cam.filming(w, this) != null;
        if (f != frozen()) dataTracker.set(FROZEN, f);
        if (f || posted || vanishing()) {
            getNavigation().stop();
            setVelocity(0, getVelocity().y, 0);
            return;
        }
        ServerPlayerEntity p = null;
        double best = 70 * 70;
        for (ServerPlayerEntity q : w.getPlayers()) {
            if (!Watch.playable(q)) continue;
            double d = q.squaredDistanceTo(this);
            if (d < best) {
                best = d;
                p = q;
            }
        }
        if (p == null) return;
        double d = Math.sqrt(best);
        if (d > 34 && ++relocate > 200) {
            relocate = 0;
            Vec3d s = Night.learnedSpot(w, p);
            if (s != null) refreshPositionAndAngles(s.x, s.y, s.z, getYaw(), 0);
            return;
        }
        // встаёт вплотную за спиной: туда, куда ты не смотришь
        Vec3d back = p.getPos().subtract(p.getRotationVec(1f).multiply(1.3, 0, 1.3));
        if (age % 8 == 0) getNavigation().startMovingTo(back.x, back.y, back.z, d > 10 ? 1.25 : 0.8);
        getLookControl().lookAt(p, 30, 30);
        boolean alone = true;
        for (ServerPlayerEntity q : w.getPlayers()) if (q != p && q.squaredDistanceTo(p) < 10 * 10) alone = false;
        if (d < 1.8 && alone && !Cam.up(p)) {
            if (++close > 30) {
                close = 0;
                Night.grab(p, this);
            }
        } else close = Math.max(0, close - 1);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL) && super.damage(source, amount);
    }

    @Override
    public boolean tryAttack(net.minecraft.entity.Entity target) {
        return false;
    }
}
