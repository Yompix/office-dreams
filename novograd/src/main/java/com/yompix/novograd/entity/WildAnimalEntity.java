package com.yompix.novograd.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Дичь за городом (gta/Hunting): олень и кабан на GeckoLib. Олень пуглив — убегает от игрока (тихо подкрадёшься — ближе),
 * кабан бродит, раненый — бросается на обидчика.
 */
public class WildAnimalEntity extends PathAwareEntity implements GeoEntity {
    public enum Kind { DEER, BOAR }

    private static final TrackedData<Integer> KIND = DataTracker.registerData(WildAnimalEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.wild.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.wild.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.wild.run");
    private static final RawAnimation GRAZE = RawAnimation.begin().thenLoop("animation.wild.graze");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public WildAnimalEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setKind(type == com.yompix.novograd.gta.Outdoors.BOAR ? Kind.BOAR : Kind.DEER);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 16).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        super.initDataTracker(b);
        b.add(KIND, 0);
    }

    public Kind kind() {
        return Kind.values()[Math.floorMod(dataTracker.get(KIND), Kind.values().length)];
    }

    public void setKind(Kind k) {
        dataTracker.set(KIND, k.ordinal());
        var hp = getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        var sp = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (hp != null) hp.setBaseValue(k == Kind.DEER ? 14 : 20);
        if (sp != null) sp.setBaseValue(k == Kind.DEER ? 0.3 : 0.26);
        setHealth(getMaxHealth());
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new EscapeDangerGoal(this, 2.1) {
            @Override
            public boolean canStart() {
                return kind() == Kind.DEER && super.canStart();
            }
        });
        // олень убегает от игрока; подкрался пригнувшись и со скрытностью — подпустит ближе
        goalSelector.add(2, new FleeEntityGoal<>(this, PlayerEntity.class, 14, 1.4, 2.0,
                e -> kind() == Kind.DEER && e instanceof PlayerEntity p && !p.isCreative() && !p.isSpectator() && spotted(p)));
        goalSelector.add(3, new MeleeAttackGoal(this, 1.5, false) {
            @Override
            public boolean canStart() {
                return kind() == Kind.BOAR && super.canStart();
            }
        });
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.8, 0.02f));
        goalSelector.add(6, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this) {
            @Override
            public boolean canStart() {
                return kind() == Kind.BOAR && super.canStart();
            }
        });
    }

    /** Заметил ли олень игрока: на корточках — только вблизи, а мастер скрытности почти не слышен. */
    private boolean spotted(PlayerEntity p) {
        double d = distanceTo(p);
        if (!p.isSneaking()) return d < 14;
        double r = 7 * (1 - 0.55 * com.yompix.novograd.gta.Skills.k(p, com.yompix.novograd.gta.Skills.Skill.STEALTH));
        return d < r;
    }

    @Override
    public boolean tryAttack(net.minecraft.entity.Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) playSound(SoundEvents.ENTITY_HOGLIN_ATTACK, 0.8f, 1.2f);
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return kind() == Kind.BOAR ? SoundEvents.ENTITY_PIG_AMBIENT : null;
    }

    @Override
    public float getSoundPitch() {
        return kind() == Kind.BOAR ? 0.6f : 1.3f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return kind() == Kind.BOAR ? SoundEvents.ENTITY_HOGLIN_HURT : SoundEvents.ENTITY_GOAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return kind() == Kind.BOAR ? SoundEvents.ENTITY_HOGLIN_DEATH : SoundEvents.ENTITY_GOAT_DEATH;
    }

    @Override
    public boolean canImmediatelyDespawn(double d) {
        return false; // убирает gta/Hunting, когда рядом никого
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound n) {
        super.writeCustomDataToNbt(n);
        n.putInt("Kind", dataTracker.get(KIND));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound n) {
        super.readCustomDataFromNbt(n);
        dataTracker.set(KIND, n.getInt("Kind"));
    }

    @Override
    public net.minecraft.text.Text getDefaultName() {
        return net.minecraft.text.Text.literal(kind() == Kind.DEER ? "Олень" : "Кабан");
    }

    // ---------- GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 4, state -> {
            double v = getVelocity().horizontalLengthSquared();
            if (state.isMoving() && v > 1e-4) return state.setAndContinue(v > 0.02 ? RUN : WALK);
            return state.setAndContinue((getId() + age / 160) % 3 == 0 ? GRAZE : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
