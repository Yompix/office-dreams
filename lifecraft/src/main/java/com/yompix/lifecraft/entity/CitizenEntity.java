package com.yompix.lifecraft.entity;

import com.yompix.lifecraft.life.LifeActions;
import com.yompix.lifecraft.story.Story;
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
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Житель города. Роль решает, что он делает: учитель ведёт урок, продавец продаёт, крупье зовёт в казино.
 * У каждого свой скин, рост, походка и «занятие» (машет, болтает, сидит в телефоне, танцует, думает) —
 * занятие видит клиент и анимирует руки и голову.
 */
public class CitizenEntity extends PathAwareEntity {
    /** 0–7 горожане, 8–15 герои сюжета, дальше 40 случайных горожан (чётные — девушки). */
    public static final String[] SKINS;
    public static final int RANDOM_FROM = 16, RANDOM_COUNT = 40;
    private static final String[] BASE = {"teacher", "shopkeeper", "student", "student2", "worker", "office", "police", "grandma",
            "mom", "dad", "tema", "lisa", "gleb", "instructor", "baron", "guard"};
    private static final boolean[] BASE_SLIM = {true, false, false, true, false, false, false, true,
            true, false, false, true, false, false, false, false};

    static {
        SKINS = new String[BASE.length + RANDOM_COUNT];
        System.arraycopy(BASE, 0, SKINS, 0, BASE.length);
        for (int i = 0; i < RANDOM_COUNT; i++) SKINS[RANDOM_FROM + i] = String.format("c%02d", i);
    }

    public enum Role { CITIZEN, TEACHER, SHOPKEEPER, CROUPIER, DEALER, BANKER }

    public enum Activity { IDLE, TALK, WAVE, PHONE, SIT, DANCE, CHEER, THINK }

    private static final TrackedData<Integer> SKIN = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ACTIVITY = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> QUEST = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private Role role = Role.CITIZEN;
    private boolean stationary;
    private int removeIn = -1;
    private String storyId = "";
    private int activityTicks;
    private boolean lockedActivity;

    public CitizenEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48);
    }

    public static boolean isSlim(int skin) {
        return skin < BASE.length ? BASE_SLIM[skin] : (skin - RANDOM_FROM) % 2 == 0;
    }

    public static int skinIndex(String name) {
        for (int i = 0; i < SKINS.length; i++) if (SKINS[i].equals(name)) return i;
        return 2;
    }

    /** Случайный прохожий: скин, рост (ребёнок/подросток/взрослый) и скорость шага. */
    public static int randomSkin(Random r) {
        return RANDOM_FROM + r.nextInt(RANDOM_COUNT);
    }

    public void randomizeBody(Random r, boolean kid) {
        float scale = kid ? 0.62f + r.nextFloat() * 0.2f : 0.92f + r.nextFloat() * 0.14f;
        setScale(scale);
        var speed = getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue((kid ? 0.28 : 0.22) + r.nextFloat() * 0.06);
    }

    public void setScale(float scale) {
        var s = getAttributeInstance(EntityAttributes.GENERIC_SCALE);
        if (s != null) s.setBaseValue(scale);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SKIN, 2);
        builder.add(ACTIVITY, 0);
        builder.add(QUEST, false);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(4, new ScheduleGoal(this));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.6) {
            @Override
            public boolean canStart() {
                return !stationary && getActivity() != Activity.SIT && getActivity() != Activity.DANCE && super.canStart();
            }
        });
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8f));
        goalSelector.add(7, new LookAroundGoal(this));
    }

    public int getSkin() {
        return MathHelper.clamp(dataTracker.get(SKIN), 0, SKINS.length - 1);
    }

    public Activity getActivity() {
        int a = dataTracker.get(ACTIVITY);
        return a >= 0 && a < Activity.values().length ? Activity.values()[a] : Activity.IDLE;
    }

    /** Занятие, которое сам житель не сменит (танец на празднике, разговор в сюжете). null — снять. */
    public void lockActivity(Activity a) {
        lockedActivity = a != null;
        dataTracker.set(ACTIVITY, (a == null ? Activity.IDLE : a).ordinal());
    }

    public void setup(int skin, Role role, boolean stationary, String name) {
        dataTracker.set(SKIN, skin);
        this.role = role;
        this.stationary = stationary;
        setCustomName(net.minecraft.text.Text.literal(name));
        setCustomNameVisible(role != Role.CITIZEN);
    }

    public void setStoryId(String id) {
        storyId = id;
        setCustomNameVisible(true);
    }

    public String getStoryId() {
        return storyId;
    }

    public void setStationary(boolean s) {
        stationary = s;
    }

    /** Исчезнуть через несколько тиков (клиент такси вышел и ушёл по делам). */
    public void removeLater(int ticks) {
        removeIn = ticks;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (removeIn > 0 && --removeIn == 0) discard();
        if (lockedActivity) return;
        // жители сами меняют занятия: машут проходящим, болтают по телефону, задумываются
        if (--activityTicks <= 0) {
            Random r = getRandom();
            PlayerEntity near = getWorld().getClosestPlayer(this, 5);
            Activity next;
            CitizenEntity buddy = r.nextInt(4) == 0 ? getWorld().getEntitiesByClass(CitizenEntity.class, getBoundingBox().expand(3), e -> e != this && !e.lockedActivity && e.storyId.isEmpty()).stream().findFirst().orElse(null) : null;
            if (buddy != null) {
                // два жителя остановились поболтать
                getNavigation().stop();
                buddy.getNavigation().stop();
                getLookControl().lookAt(buddy, 30, 30);
                buddy.getLookControl().lookAt(this, 30, 30);
                buddy.dataTracker.set(ACTIVITY, Activity.TALK.ordinal());
                buddy.activityTicks = 120;
                next = Activity.TALK;
            } else if (near != null && getActivity() != Activity.WAVE && r.nextInt(3) == 0) next = Activity.WAVE;
            else {
                int x = r.nextInt(100);
                next = x < 50 ? Activity.IDLE : x < 65 ? Activity.PHONE : x < 78 ? Activity.TALK : x < 88 ? Activity.THINK : x < 94 ? Activity.CHEER : Activity.IDLE;
            }
            dataTracker.set(ACTIVITY, next.ordinal());
            equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, next == Activity.PHONE
                    ? new net.minecraft.item.ItemStack(com.yompix.lifecraft.ModContent.PHONE) : net.minecraft.item.ItemStack.EMPTY);
            activityTicks = next == Activity.WAVE ? 40 : 80 + r.nextInt(160);
        }
    }

    public boolean hasQuest() {
        return dataTracker.get(QUEST);
    }

    public void setQuest(boolean q) {
        dataTracker.set(QUEST, q);
        if (q) setCustomNameVisible(true);
    }

    public boolean isStationary() {
        return stationary;
    }

    public boolean isKid() {
        return getScale() < 0.85f;
    }

    /** Распорядок дня: утром дети в школу, взрослые на работу; вечером парк и казино; ночью по домам. */
    static class ScheduleGoal extends net.minecraft.entity.ai.goal.Goal {
        private final CitizenEntity c;
        private net.minecraft.util.math.Vec3d target;
        private int cooldown;

        ScheduleGoal(CitizenEntity c) {
            this.c = c;
            setControls(java.util.EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (c.stationary || c.lockedActivity || !c.storyId.isEmpty() || c.hasVehicle()) return false;
            if (--cooldown > 0) return false;
            cooldown = 200 + c.getRandom().nextInt(200);
            if (!(c.getWorld() instanceof net.minecraft.server.world.ServerWorld w)) return false;
            long t = w.getTimeOfDay() % 24000;
            String[] where;
            if (c.isKid()) where = t < 1500 || t > 15000 ? HOMES : t < 9000 ? new String[]{"school_yard", "school_front"} : new String[]{"park_center", "shop_door", "school_yard"};
            else where = t < 1500 || t > 16500 ? HOMES : t < 10500 ? new String[]{"office_door", "shop_door", "bank_door", "dealer_door"}
                    : new String[]{"park_center", "casino_door", "shop_door", "park_center"};
            double[] p = com.yompix.lifecraft.world.TownState.get(w).place(where[c.getRandom().nextInt(where.length)]);
            if (p == null) return false;
            target = new net.minecraft.util.math.Vec3d(p[0] - 5 + c.getRandom().nextInt(11), p[1], p[2] - 5 + c.getRandom().nextInt(11));
            return c.squaredDistanceTo(target) > 64;
        }

        private static final String[] HOMES = {"home_yard", "lisa_yard", "tema_yard", "gleb_yard", "house4_yard", "house6_yard"};

        @Override
        public void start() {
            step();
        }

        private void step() {
            // далёкую цель проходим кусками по 30 блоков — так навигация не сдаётся
            net.minecraft.util.math.Vec3d d = target.subtract(c.getPos());
            net.minecraft.util.math.Vec3d go = d.length() > 30 ? c.getPos().add(d.normalize().multiply(30)) : target;
            c.getNavigation().startMovingTo(go.x, target.y, go.z, 0.7);
        }

        @Override
        public boolean shouldContinue() {
            return target != null && c.squaredDistanceTo(target) > 9 && !c.lockedActivity;
        }

        @Override
        public void tick() {
            if (c.getNavigation().isIdle()) step();
        }
    }

    public Role getRole() {
        return role;
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (!getWorld().isClient && !Story.onTalk((ServerPlayerEntity) player, this)
                && !com.yompix.lifecraft.life.SideQuests.talk((ServerPlayerEntity) player, this)) LifeActions.talk(this, (ServerPlayerEntity) player);
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
        nbt.putString("StoryId", storyId);
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
        storyId = nbt.getString("StoryId");
    }
}
