package com.yompix.glukhoman.entity;

import com.yompix.glukhoman.story.Story;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Житель Елового (баба Поля, Захар Ильич, Вика, Петрович, Зина). WHO — кто это (скин и реплики), SLEEP — лунатик:
 * ночью идёт по точкам к перрону, ни на кого не смотрит. Двигает его сюжет (Night), сам он только идёт по маршруту.
 */
public class ResidentEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<String> WHO = DataTracker.registerData(ResidentEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> ACT = DataTracker.registerData(ResidentEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Boolean> SLEEP = DataTracker.registerData(ResidentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private final AnimatableInstanceCache geo = GeckoLibUtil.createInstanceCache(this);
    /** Маршрут (сервер): идём к первой точке, дошли — убираем. */
    public final List<Vec3d> route = new ArrayList<>();
    public double speed = 0.6;
    /** Пауза движения (звонок колокола), в тиках. */
    public int hold;
    public boolean talking;
    public double animClock = -1;
    /** Клиент: выбор базового клипа новой системой анимации (client/anim/Animator); на сервере не используется. */
    public static java.util.function.BiFunction<ResidentEntity, String, String> ANIM;

    public ResidentEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
        setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 20).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 96)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.05);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        super.initDataTracker(b);
        b.add(WHO, "zina");
        b.add(ACT, "");
        b.add(SLEEP, false);
    }

    public String who() {
        return dataTracker.get(WHO);
    }

    public void who(String w) {
        dataTracker.set(WHO, w);
    }

    public boolean sleepwalking() {
        return dataTracker.get(SLEEP);
    }

    public void sleepwalking(boolean s) {
        dataTracker.set(SLEEP, s);
    }

    public String act() {
        return dataTracker.get(ACT);
    }

    public void act(String a) {
        dataTracker.set(ACT, a);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 8f) {
            @Override
            public boolean canStart() {
                return !sleepwalking() && route.isEmpty() && super.canStart();
            }
        });
        goalSelector.add(2, new LookAroundGoal(this) {
            @Override
            public boolean canStart() {
                return !sleepwalking() && super.canStart();
            }
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (hold > 0) {
            hold--;
            getNavigation().stop();
            return;
        }
        if (route.isEmpty()) return;
        Vec3d t = route.get(0);
        if (t.squaredDistanceTo(getPos().x, t.y, getPos().z) < (route.size() == 1 ? 0.25 : 1.6)) {
            route.remove(0);
            if (route.isEmpty()) getNavigation().stop();
            return;
        }
        // лунатик идёт прямо по точкам, без поиска пути (ступеньки — шагом, высота шага 1 блок)
        getNavigation().stop();
        getMoveControl().moveTo(t.x, t.y, t.z, speed);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (player instanceof ServerPlayerEntity sp && hand == Hand.MAIN_HAND) Story.resident(sp, this);
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Who", who());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Who")) who(nbt.getString("Who"));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double d) {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 7, state -> {
            String a = act();
            if (a.isEmpty()) {
                boolean moving = getVelocity().horizontalLengthSquared() > 0.0015;
                if (sleepwalking()) a = "sleepwalk";
                else if (moving) a = "walk";
                else {   // разнообразие на месте: дышит, переминается, осматривается, чешет затылок
                    String[] v = {"idle", "idle_shift", "idle", "idle_look", "idle_shift", "idle_scratch"};
                    a = v[(int) Math.floorMod((long) (getTick(this) / 120) + getId(), (long) v.length)];   // у кукол кат-сцен id отрицательные
                }
            }
            if (ANIM != null) a = ANIM.apply(this, a);
            return state.setAndContinue(HorrorMob.raw("animation.human." + a));
        }));
    }

    @Override
    public double getTick(Object o) {
        return animClock >= 0 ? animClock : age;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geo;
    }
}
