package com.yompix.lifecraft.entity;

import com.yompix.lifecraft.life.LifeActions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** Житель города. Роль решает, что он делает: учитель ведёт урок, продавец продаёт, крупье зовёт в казино. */
public class CitizenEntity extends PathAwareEntity {
    public static final String[] SKINS = {"teacher", "shopkeeper", "student", "student2", "worker", "office", "police", "grandma"};
    public enum Role { CITIZEN, TEACHER, SHOPKEEPER, CROUPIER, DEALER, BANKER }

    private static final TrackedData<Integer> SKIN = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private Role role = Role.CITIZEN;
    private boolean stationary;
    private int removeIn = -1;

    public CitizenEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, 2);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.6) {
            @Override
            public boolean canStart() {
                return !stationary && super.canStart();
            }
        });
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    public int getSkin() {
        return MathHelper.clamp(dataTracker.get(SKIN), 0, SKINS.length - 1);
    }

    public void setup(int skin, Role role, boolean stationary, String name) {
        dataTracker.set(SKIN, skin);
        this.role = role;
        this.stationary = stationary;
        setCustomName(net.minecraft.text.Text.literal(name));
        setCustomNameVisible(role != Role.CITIZEN);
    }

    /** Исчезнуть через несколько тиков (клиент такси вышел и ушёл по делам). */
    public void removeLater(int ticks) {
        removeIn = ticks;
    }

    @Override
    public void tick() {
        super.tick();
        if (removeIn > 0 && --removeIn == 0 && !getWorld().isClient) discard();
    }

    public Role getRole() {
        return role;
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!getWorld().isClient) LifeActions.talk(this, (ServerPlayerEntity) player);
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !(source.getAttacker() instanceof PlayerEntity p && p.isCreative()) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return !stationary;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Skin", getSkin());
        nbt.putString("Role", role.name());
        nbt.putBoolean("Stationary", stationary);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(SKIN, nbt.getInt("Skin"));
        try {
            role = Role.valueOf(nbt.getString("Role"));
        } catch (IllegalArgumentException e) {
            role = Role.CITIZEN;
        }
        stationary = nbt.getBoolean("Stationary");
    }
}
