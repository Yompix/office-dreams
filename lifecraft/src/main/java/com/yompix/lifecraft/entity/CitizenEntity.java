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
public class CitizenEntity extends PathAwareEntity implements software.bernie.geckolib.animatable.GeoEntity {
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

    public enum Activity {
        IDLE, TALK, WAVE, PHONE, SIT, DANCE, CHEER, THINK, SLEEP, ARGUE, LAUGH, FIGHT, HUG, HANDSHAKE, UPSET;

        /** Сценка на время разговора: снимается, когда диалог закончился. */
        public boolean scene() {
            return this == TALK || this == ARGUE || this == LAUGH || this == FIGHT || this == HUG || this == HANDSHAKE || this == UPSET;
        }
    }

    private static final TrackedData<Integer> SKIN = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ACTIVITY = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> QUEST = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private Role role = Role.CITIZEN;
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geoCache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    // ---- анимации GeckoLib: ходьба/бег по скорости, занятие — отдельной анимацией, удар по мячу — разово
    private static final software.bernie.geckolib.animation.RawAnimation IDLE = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen.idle");
    private static final software.bernie.geckolib.animation.RawAnimation WALK = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen.walk");
    private static final software.bernie.geckolib.animation.RawAnimation RUN = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen.run");
    private static final software.bernie.geckolib.animation.RawAnimation RIDE = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen.ride");
    private static final java.util.Map<Activity, software.bernie.geckolib.animation.RawAnimation> ACTS = new java.util.EnumMap<>(Activity.class);

    static {
        for (Activity a : Activity.values())
            if (a != Activity.IDLE) ACTS.put(a, software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen." + a.name().toLowerCase()));
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "body", 5, state -> {
            Activity a = getActivity();
            if (hasVehicle()) return state.setAndContinue(RIDE); // в машине — сидит на сиденье
            double v = getVelocity().horizontalLengthSquared();
            boolean moving = state.isMoving() && v > 1e-4;
            if (a != Activity.IDLE && (!moving || a == Activity.DANCE || a == Activity.SIT || a == Activity.FIGHT || a == Activity.SLEEP)) return state.setAndContinue(ACTS.get(a));
            if (moving) return state.setAndContinue(v > 0.012 ? RUN : WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "action", 2, state -> software.bernie.geckolib.animation.PlayState.STOP)
                .triggerableAnim("kick", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.citizen.kick"))
                .triggerableAnim("hit", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.citizen.hit"))
                .triggerableAnim("highfive", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.citizen.highfive")));
    }

    @Override
    public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
    private boolean stationary;
    private int removeIn = -1;
    private String storyId = "";
    private int activityTicks;
    /** Занятие на месте (покупки, лавочка, игра): сколько ещё тиков. */
    private int busyTicks;
    @org.jetbrains.annotations.Nullable private net.minecraft.util.math.Vec3d busyAt;
    private boolean lockedActivity;
    /** Свой дом (см. {@link HomeGoal}); hidden — ушёл в свою комнату, не видно; homeActive — сейчас живёт «дома». */
    String home = "";
    boolean hidden, homeActive;
    /** С кем сейчас общаемся/дерёмся на улице. */
    @org.jetbrains.annotations.Nullable private CitizenEntity partner;

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
        goalSelector.add(1, new HomeGoal(this));
        goalSelector.add(2, new PlayBallGoal(this));
        goalSelector.add(3, new BusyGoal(this));
        goalSelector.add(4, new ScheduleGoal(this));
        goalSelector.add(4, new StoryLifeGoal(this));
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
        if (a != null) equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, net.minecraft.item.ItemStack.EMPTY);
    }

    public void setup(int skin, Role role, boolean stationary, String name) {
        dataTracker.set(SKIN, skin);
        this.role = role;
        this.stationary = stationary;
        setCustomName(net.minecraft.text.Text.literal(matchGender(name)));
        setCustomNameVisible(role != Role.CITIZEN);
    }

    /**
     * Имя и пол совпадают. Случайное имя («Егор Козлов») меняем под внешность;
     * заданное («Продавец Зина», «Сергей Олегович») — оставляем, а внешность подбираем под имя.
     */
    private String matchGender(String name) {
        boolean female = isSlim(getSkin());
        String fixed = com.yompix.lifecraft.life.Names.fixGender(name, female, getUuid().getLeastSignificantBits());
        if (!fixed.equals(name)) return fixed;
        Boolean g = com.yompix.lifecraft.life.Names.genderOf(name);
        if (g != null && g != female && getSkin() < RANDOM_FROM || (g != null && g != female)) {
            int k = (int) Math.floorMod(getUuid().getMostSignificantBits(), RANDOM_COUNT / 2);
            dataTracker.set(SKIN, RANDOM_FROM + k * 2 + (g ? 0 : 1)); // чётные — девушки
        }
        return name;
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
        anchor = s ? getPos() : null;
    }

    /** Где сюжетный герой «живёт» сейчас: гуляет рядом, но далеко не уходит. */
    @org.jetbrains.annotations.Nullable private net.minecraft.util.math.Vec3d anchor;

    /** Сюжетные герои между сценами: прогуливаются рядом, болтают друг с другом, замирают, когда подходит игрок. */
    static class StoryLifeGoal extends net.minecraft.entity.ai.goal.Goal {
        private final CitizenEntity c;
        private int left;

        StoryLifeGoal(CitizenEntity c) {
            this.c = c;
            setControls(java.util.EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            if (c.storyId.isEmpty() || c.anchor == null || c.lockedActivity || c.busyTicks > 0 || c.hasVehicle()) return false;
            if (c.getRandom().nextInt(160) != 0) return false;
            return c.getWorld().getClosestPlayer(c, 4) == null;
        }

        @Override
        public void start() {
            var r = c.getRandom();
            left = 200;
            // иногда — подойти поболтать к другому герою рядом
            CitizenEntity mate = c.getWorld().getEntitiesByClass(CitizenEntity.class, c.getBoundingBox().expand(7),
                    o -> o != c && !o.storyId.isEmpty() && !o.lockedActivity).stream().findAny().orElse(null);
            if (mate != null && r.nextInt(3) == 0) {
                c.getNavigation().startMovingTo(mate, 0.5);
                return;
            }
            net.minecraft.util.math.Vec3d to = c.anchor.add(r.nextInt(7) - 3, 0, r.nextInt(7) - 3);
            c.getNavigation().startMovingTo(to.x, to.y, to.z, 0.45);
        }

        @Override
        public boolean shouldContinue() {
            return --left > 0 && !c.getNavigation().isIdle() && !c.lockedActivity && c.getWorld().getClosestPlayer(c, 3) == null;
        }

        @Override
        public void stop() {
            c.getNavigation().stop();
            CitizenEntity mate = c.getWorld().getEntitiesByClass(CitizenEntity.class, c.getBoundingBox().expand(2.2),
                    o -> o != c && !o.storyId.isEmpty() && !o.lockedActivity && o.busyTicks == 0).stream().findAny().orElse(null);
            if (mate != null && c.busyTicks == 0 && !c.lockedActivity) {
                c.face(mate);
                mate.face(c);
                c.busy(Activity.TALK, 120, null, net.minecraft.item.ItemStack.EMPTY);
                mate.busy(c.getRandom().nextBoolean() ? Activity.LAUGH : Activity.TALK, 120, null, net.minecraft.item.ItemStack.EMPTY);
            }
            // далеко ушёл (толкнули, сбился) — вернуться к своему месту
            if (c.anchor != null && c.squaredDistanceTo(c.anchor) > 49) c.getNavigation().startMovingTo(c.anchor.x, c.anchor.y, c.anchor.z, 0.5);
        }
    }

    /** Исчезнуть через несколько тиков (клиент такси вышел и ушёл по делам). */
    public void removeLater(int ticks) {
        removeIn = ticks;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            if (asleep()) { bodyYaw = prevBodyYaw = headYaw = prevHeadYaw = getYaw(); }
            return;
        }
        if (removeIn > 0 && --removeIn == 0) discard();
        unstick();
        if (busyTicks > 0 && --busyTicks == 0) {
            dataTracker.set(ACTIVITY, Activity.IDLE.ordinal());
            equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, net.minecraft.item.ItemStack.EMPTY);
            busyAt = null;
        }
        if (busyTicks > 0 && partner != null) {
            if (getActivity() == Activity.FIGHT) fightTick();
            else if (!partner.isRemoved() && age % 10 == 0) face(partner);
        }
        if (busyTicks == 0) partner = null;
        if (lockedActivity || busyTicks > 0 || homeActive) return;
        // жители сами меняют занятия: машут проходящим, болтают по телефону, задумываются
        if (--activityTicks <= 0) {
            Random r = getRandom();
            PlayerEntity near = getWorld().getClosestPlayer(this, 5);
            Activity next;
            CitizenEntity buddy = r.nextInt(4) == 0 ? getWorld().getEntitiesByClass(CitizenEntity.class, getBoundingBox().expand(3), e -> e != this && !e.lockedActivity && e.storyId.isEmpty()).stream().findFirst().orElse(null) : null;
            if (buddy != null && !buddy.homeActive && buddy.busyTicks == 0) {
                meet(buddy, r);
                return;
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

    /** Встреча двух жителей: поболтать, пожать руки, обняться, посмеяться, поспорить — а иногда и подраться. */
    private void meet(CitizenEntity o, Random r) {
        getNavigation().stop();
        o.getNavigation().stop();
        face(o);
        o.face(this);
        boolean girl = isSlim(getSkin()) || isSlim(o.getSkin());
        boolean old = getSkin() == 7 || o.getSkin() == 7;
        int x = r.nextInt(100);
        Activity me, them;
        int ticks;
        if (x < 30) { me = Activity.TALK; them = Activity.TALK; ticks = 140; }
        else if (x < 45) { me = Activity.HANDSHAKE; them = Activity.HANDSHAKE; ticks = 50; }
        else if (x < 55 && girl) { me = Activity.HUG; them = Activity.HUG; ticks = 60; }
        else if (x < 70) { me = Activity.TALK; them = Activity.LAUGH; ticks = 100; }
        else if (x < 78) { me = Activity.WAVE; them = Activity.WAVE; ticks = 40; triggerAnim("action", "highfive"); o.triggerAnim("action", "highfive"); }
        else if (x < 92 || old) { me = Activity.ARGUE; them = r.nextBoolean() ? Activity.ARGUE : Activity.UPSET; ticks = 120; }
        else { me = Activity.FIGHT; them = Activity.FIGHT; ticks = 110; }
        if (me == Activity.HUG || me == Activity.HANDSHAKE) {
            // подойти вплотную: обниматься и жать руки на расстоянии руки
            net.minecraft.util.math.Vec3d mid = getPos().add(o.getPos()).multiply(0.5), dir = o.getPos().subtract(getPos()).normalize().multiply(me == Activity.HUG ? 0.32 : 0.45);
            refreshPositionAndAngles(mid.x - dir.x, getY(), mid.z - dir.z, getYaw(), 0);
            o.refreshPositionAndAngles(mid.x + dir.x, o.getY(), mid.z + dir.z, o.getYaw(), 0);
            face(o);
            o.face(this);
        }
        partner = o;
        o.partner = this;
        busy(me, ticks, null, net.minecraft.item.ItemStack.EMPTY);
        o.busy(them, ticks, null, net.minecraft.item.ItemStack.EMPTY);
        activityTicks = ticks + 40;
        o.activityTicks = ticks + 40;
        if (me == Activity.FIGHT && getWorld() instanceof net.minecraft.server.world.ServerWorld w) {
            PlayerEntity p = w.getClosestPlayer(this, 16);
            if (p != null) p.sendMessage(net.minecraft.text.Text.literal("⚠ На улице драка!").formatted(net.minecraft.util.Formatting.RED), true);
        }
    }

    public void face(net.minecraft.entity.Entity o) {
        float yaw = (float) (Math.toDegrees(Math.atan2(o.getZ() - getZ(), o.getX() - getX())) - 90);
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
    }

    /** Сюжетная драка/сценка с другим жителем (см. story Actions «fight»). */
    public void sceneWith(CitizenEntity o, Activity a, int ticks) {
        partner = o;
        face(o);
        busy(a, ticks, null, net.minecraft.item.ItemStack.EMPTY);
        lockedActivity = false;
    }

    /** Удары в драке: каждый пропущенный — откидывает голову назад, слышен шлепок. */
    private void fightTick() {
        if (partner == null || getActivity() != Activity.FIGHT) return;
        if (partner.isRemoved() || squaredDistanceTo(partner) > 9) { partner = null; return; }
        face(partner);
        if (squaredDistanceTo(partner) > 1.8) getNavigation().startMovingTo(partner, 0.6);
        else getNavigation().stop();
        int phase = (age + getId() * 7) % 24;
        if (phase == 5 || phase == 16) {
            partner.triggerAnim("action", "hit");
            getWorld().playSound(null, partner.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, net.minecraft.sound.SoundCategory.NEUTRAL, 0.7f, 0.9f + getRandom().nextFloat() * 0.3f);
        }
    }

    // ------------------------------------------------------------------ не застревать
    private net.minecraft.util.math.Vec3d stuckFrom;
    private int stuckTimes;

    /**
     * Каждые 2 секунды: если житель куда-то идёт, а сам стоит на месте (упёрся в машину, фонарь, забор),
     * сначала подпрыгивает и ищет путь заново, а если не помогло — делает шаг на ближайшее свободное место.
     */
    private void unstick() {
        if (stationary || hasVehicle() || hidden || asleep() || age % 40 != 0) return;
        boolean walking = !getNavigation().isIdle();
        if (isInsideWall()) {
            escape();
            return;
        }
        if (!walking) {
            stuckFrom = null;
            stuckTimes = 0;
            return;
        }
        net.minecraft.util.math.Vec3d now = getPos();
        if (stuckFrom != null && now.squaredDistanceTo(stuckFrom) < 0.16) {
            stuckTimes++;
            if (stuckTimes == 1) {
                getJumpControl().setActive();
                getNavigation().recalculatePath();
            } else if (stuckTimes >= 2) {
                escape();
                getNavigation().stop();
                stuckTimes = 0;
            }
        } else stuckTimes = 0;
        stuckFrom = now;
    }

    /** Шаг на ближайшее свободное место с твёрдым полом. */
    private void escape() {
        net.minecraft.util.math.BlockPos at = getBlockPos();
        for (net.minecraft.util.math.BlockPos p : net.minecraft.util.math.BlockPos.iterateOutwards(at, 3, 2, 3)) {
            if (p.equals(at)) continue;
            var box = getDimensions(getPose()).getBoxAt(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            if (getWorld().isSpaceEmpty(this, box) && getWorld().getBlockState(p.down()).isSolidBlock(getWorld(), p.down())
                    && getWorld().getOtherEntities(this, box, e -> e instanceof CarEntity).isEmpty()) {
                refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYaw(), getPitch());
                return;
            }
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

    /** Обычный горожанин, который живёт своей жизнью (не сюжетный, не продавец, не стоит на месте). */
    boolean livesInTown() {
        return role == Role.CITIZEN && storyId.isEmpty() && !stationary && !lockedActivity && !hasVehicle() && removeIn < 0;
    }

    public void setHome(String h) {
        home = h;
    }

    String home() {
        if (home.isEmpty()) home = HomeGoal.HOUSES[(getUuid().hashCode() & 0x7fffffff) % HomeGoal.HOUSES.length];
        return home;
    }

    /** Своя «привычка» 0..1: кто раньше встаёт, кто позже ложится. */
    float personal() {
        return (float) ((getUuid().getLeastSignificantBits() >>> 11) % 1000) / 1000f;
    }

    void setActivity(Activity a, net.minecraft.item.ItemStack held) {
        dataTracker.set(ACTIVITY, a.ordinal());
        equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, held);
    }

    public boolean asleep() {
        return getActivity() == Activity.SLEEP;
    }

    @Override
    public boolean isPushable() {
        return !asleep() && !isInvisible() && super.isPushable();
    }

    @Override
    public boolean isCollidable() {
        return false;
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
            chosen = null;
            if (c.isKid()) where = t < 1500 || t > 15000 ? HOMES : t < 9000 ? new String[]{"school_yard", "school_front"} : new String[]{"park_center", "shop_door", "school_yard"};
            else where = t < 1500 || t > 16500 ? HOMES : t < 10500 ? new String[]{"office_door", "shop_door", "bank_door", "dealer_door", "gym_door", "pizza_door"}
                    : t < 12500 ? new String[]{"park_center", "casino_door", "shop_door", "pizza_door", "gym_door"}
                    : new String[]{"club_door", "pizza_door", "casino_door", "park_center"};
            chosen = where[c.getRandom().nextInt(where.length)];
            double[] p = com.yompix.lifecraft.world.TownState.get(w).place(chosen);
            if (p == null) return false;
            target = new net.minecraft.util.math.Vec3d(p[0] - 5 + c.getRandom().nextInt(11), p[1], p[2] - 5 + c.getRandom().nextInt(11));
            return c.squaredDistanceTo(target) > 64;
        }

        private String chosen;

        @Override
        public void stop() {
            if (chosen != null && target != null && c.squaredDistanceTo(target) <= 16) c.arrived(chosen);
            target = null;
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

    /** Пришёл на место — занимается делом: покупки, лавочка, казино, работа, мяч. */
    void arrived(String place) {
        var r = getRandom();
        var w = (net.minecraft.server.world.ServerWorld) getWorld();
        double[] p = com.yompix.lifecraft.world.TownState.get(w).place(place);
        if (p == null) return;
        // место у двери смотрит в здание (yaw = p[3]) — «внутрь» на 8 блоков вперёд по взгляду
        double rad = Math.toRadians(p[3]);
        net.minecraft.util.math.Vec3d in = new net.minecraft.util.math.Vec3d(p[0] - Math.sin(rad) * 8, p[1], p[2] + Math.cos(rad) * 8);
        switch (place) {
            case "shop_door" -> {
                net.minecraft.item.Item[] goods = {net.minecraft.item.Items.BREAD, net.minecraft.item.Items.APPLE, com.yompix.lifecraft.ModContent.COLA,
                        net.minecraft.item.Items.MILK_BUCKET, net.minecraft.item.Items.CAKE, com.yompix.lifecraft.ModContent.BURGER};
                busy(Activity.THINK, 300 + r.nextInt(200), in.add(r.nextInt(9) - 4, 0, r.nextInt(5) - 2), new net.minecraft.item.ItemStack(goods[r.nextInt(goods.length)]));
            }
            case "casino_door" -> busy(r.nextBoolean() ? Activity.CHEER : Activity.THINK, 400, in.add(r.nextInt(9) - 4, 0, r.nextInt(5) - 2), net.minecraft.item.ItemStack.EMPTY);
            case "office_door", "bank_door" -> busy(Activity.PHONE, 400, in.add(r.nextInt(7) - 3, 0, 0), new net.minecraft.item.ItemStack(com.yompix.lifecraft.ModContent.PHONE));
            case "park_center" -> {
                if (!isKid()) {
                    // лавочка: ближайшие ступеньки в парке
                    net.minecraft.util.math.BlockPos bench = net.minecraft.util.math.BlockPos.findClosest(getBlockPos(), 10, 2,
                            b -> w.getBlockState(b).getBlock() instanceof net.minecraft.block.StairsBlock).orElse(null);
                    if (bench != null && r.nextBoolean()) busy(Activity.SIT, 500, net.minecraft.util.math.Vec3d.ofBottomCenter(bench), net.minecraft.item.ItemStack.EMPTY);
                    else busy(r.nextBoolean() ? Activity.TALK : Activity.PHONE, 300, null, net.minecraft.item.ItemStack.EMPTY);
                }
            }
            default -> {
                if (place.endsWith("_yard")) busy(Activity.TALK, 200 + r.nextInt(200), null, net.minecraft.item.ItemStack.EMPTY);
            }
        }
    }

    public void busy(Activity a, int ticks, @org.jetbrains.annotations.Nullable net.minecraft.util.math.Vec3d at, net.minecraft.item.ItemStack held) {
        busyTicks = ticks;
        busyAt = at;
        dataTracker.set(ACTIVITY, a.ordinal());
        equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, held);
        if (at != null) getNavigation().startMovingTo(at.x, at.y, at.z, 0.6);
    }

    /** Держит занятие: стоит у полки, сидит на лавочке. */
    static class BusyGoal extends net.minecraft.entity.ai.goal.Goal {
        private final CitizenEntity c;

        BusyGoal(CitizenEntity c) {
            this.c = c;
            setControls(java.util.EnumSet.of(Control.MOVE));
        }

        @Override
        public boolean canStart() {
            return c.busyTicks > 0;
        }

        @Override
        public void tick() {
            if (c.busyAt != null && c.getNavigation().isIdle() && c.squaredDistanceTo(c.busyAt) > 2)
                c.getNavigation().startMovingTo(c.busyAt.x, c.busyAt.y, c.busyAt.z, 0.6);
            if (c.getActivity() == Activity.SIT && c.busyAt != null && c.squaredDistanceTo(c.busyAt) <= 2) c.getNavigation().stop();
        }
    }

    /** Дети гоняют мяч: бегут к нему и пинают в сторону другого ребёнка или «ворот». */
    static class PlayBallGoal extends net.minecraft.entity.ai.goal.Goal {
        private final CitizenEntity c;
        private BallEntity ball;
        private int left;

        PlayBallGoal(CitizenEntity c) {
            this.c = c;
            setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (!c.isKid() || c.stationary || c.lockedActivity || !c.storyId.isEmpty() || c.busyTicks > 0) return false;
            long t = c.getWorld().getTimeOfDay() % 24000;
            if (t > 12500 || c.getRandom().nextInt(40) != 0) return false;
            ball = c.getWorld().getEntitiesByClass(BallEntity.class, c.getBoundingBox().expand(20), b -> true).stream().findFirst().orElse(null);
            return ball != null;
        }

        @Override
        public void start() {
            left = 400 + c.getRandom().nextInt(400);
            c.dataTracker.set(ACTIVITY, Activity.IDLE.ordinal());
        }

        @Override
        public boolean shouldContinue() {
            return ball != null && ball.isAlive() && --left > 0 && c.squaredDistanceTo(ball) < 30 * 30;
        }

        @Override
        public void tick() {
            c.getLookControl().lookAt(ball, 30, 30);
            if (c.age % 5 == 0) c.getNavigation().startMovingTo(ball, 1.15);
            if (c.squaredDistanceTo(ball) < 1.6) {
                // пас другому ребёнку рядом или удар в случайную сторону
                CitizenEntity mate = c.getWorld().getEntitiesByClass(CitizenEntity.class, c.getBoundingBox().expand(12), o -> o != c && o.isKid()).stream().findAny().orElse(null);
                net.minecraft.util.math.Vec3d dir = mate != null ? mate.getPos().subtract(ball.getPos())
                        : new net.minecraft.util.math.Vec3d(c.getRandom().nextGaussian(), 0, c.getRandom().nextGaussian());
                ball.kick(dir, 0.45 + c.getRandom().nextDouble() * 0.35, 0.08 + c.getRandom().nextDouble() * 0.2);
                c.triggerAnim("action", "kick");
                if (c.getRandom().nextInt(6) == 0) c.dataTracker.set(ACTIVITY, Activity.CHEER.ordinal());
                else c.dataTracker.set(ACTIVITY, Activity.IDLE.ordinal());
            }
        }

        @Override
        public void stop() {
            c.getNavigation().stop();
            ball = null;
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
        nbt.putString("Home", home);
        nbt.putBoolean("Hidden", hidden);
        nbt.putBoolean("Asleep", asleep());
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
        home = nbt.getString("Home");
        hidden = nbt.getBoolean("Hidden");
        if (nbt.getBoolean("Asleep")) dataTracker.set(ACTIVITY, Activity.SLEEP.ordinal());
        // жители из старых версий: имя и внешность по полу
        if (storyId.isEmpty() && hasCustomName()) {
            String n = getCustomName().getString(), f = matchGender(n);
            if (!f.equals(n)) setCustomName(net.minecraft.text.Text.literal(f));
        }
    }
}
