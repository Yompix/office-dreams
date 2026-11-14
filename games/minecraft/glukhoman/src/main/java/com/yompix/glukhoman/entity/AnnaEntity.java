package com.yompix.glukhoman.entity;

import com.yompix.glukhoman.story.Story;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Анна Ветрова. Не слышит (воск в ушах), общается записками. ПКМ — прочитать её записку. */
public class AnnaEntity extends PathAwareEntity implements software.bernie.geckolib.animatable.GeoEntity {
    private static final net.minecraft.entity.data.TrackedData<String> ACT = net.minecraft.entity.data.DataTracker.registerData(AnnaEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.STRING);
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geo = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    /** Клиент: говорит (лицо с открытым ртом) и куда смотреть головой — для кат-сцен. */
    public boolean talking;

    @Override
    protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder b) {
        super.initDataTracker(b);
        b.add(ACT, "");
    }

    public String act() {
        return dataTracker.get(ACT);
    }

    public void act(String a) {
        dataTracker.set(ACT, a);
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar c) {
        c.add(new software.bernie.geckolib.animation.AnimationController<>(this, "main", 4, state -> {
            String a = act();
            if (a.isEmpty()) a = getVelocity().horizontalLengthSquared() > 0.002 ? "walk" : "idle";
            return state.setAndContinue(HorrorMob.raw("animation.human." + a));
        }));
    }

    /** Клиентские «куклы» кат-сцен: своё время анимации (в тиках) — для замедленной съёмки. */
    public double animClock = -1;

    @Override
    public double getTick(Object o) {
        return animClock >= 0 ? animClock : age;
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geo;
    }

    public AnnaEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
        setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 20).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 10f));
        goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity sp && hand == Hand.MAIN_HAND) Story.interact(sp, getBlockPos(), "anna");
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double d) {
        return false;
    }
}
