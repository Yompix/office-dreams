package com.yompix.novograd.entity;

import com.yompix.novograd.life.LifeActions;
import com.yompix.novograd.story.Story;
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

    public enum Role { CITIZEN, TEACHER, SHOPKEEPER, CROUPIER, DEALER, BANKER, POLICE, GANG, GUNSMITH, MECHANIC, TAILOR, BOUTIQUE, BARBER, COACH }

    public enum Activity {
        IDLE, TALK, WAVE, PHONE, SIT, DANCE, CHEER, THINK, SLEEP, ARGUE, LAUGH, FIGHT, HUG, HANDSHAKE, UPSET, AIM,
        /** Уличные музыканты и танцоры (gta/StreetPerformers). */
        GUITAR, SAX, BREAKDANCE,
        /** 0.4.3: повседневная жизнь (world/LifeAi, world/Leisure). Новые — только в конец (номер уходит клиенту). */
        EAT, SIT_EAT, DRINK, CARRY, BAGS, DOGWALK, PHONEWALK, UMBRELLA, JOG, TYPE, SWEEP, HAMMER, SMOKE, WATCH, STRETCH, LEAN, SITCROSS,
        SUNBATHE, SWIM, HAIL, CLAP, POINT, SHRUG, RUMMAGE, DANCE2, DANCE3;

        /** Занятие «на ходу»: при ходьбе играет своя походка (коробка, пакеты, поводок, телефон у уха, зонтик, пробежка). */
        public boolean walking() {
            return this == CARRY || this == BAGS || this == DOGWALK || this == PHONEWALK || this == UMBRELLA || this == JOG;
        }

        /** Поза, которая держится и при движении (сидит, лежит, плывёт, танцует). */
        public boolean pose() {
            return this == DANCE || this == SIT || this == FIGHT || this == SLEEP || this == SIT_EAT || this == TYPE || this == SITCROSS
                    || this == SUNBATHE || this == SWIM || this == DANCE2 || this == DANCE3;
        }

        /** Сценка на время разговора: снимается, когда диалог закончился. */
        public boolean scene() {
            return this == TALK || this == ARGUE || this == LAUGH || this == FIGHT || this == HUG || this == HANDSHAKE || this == UPSET;
        }
    }

    private static final TrackedData<Integer> SKIN = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ACTIVITY = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> QUEST = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Сбит машиной: 0 — на ногах, 1 — упал и лежит, 2 — встаёт, 3 — погиб (падение), 4 — погиб уже лёжа. */
    private static final TrackedData<Integer> KNOCK = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Реплика над головой (короткий разговор прохожих, world/LifeAi); пусто — молчит. */
    private static final TrackedData<String> SAY = DataTracker.registerData(CitizenEntity.class, TrackedDataHandlerRegistry.STRING);
    private int sayTicks;
    public static final int KO_NONE = 0, KO_DOWN = 1, KO_GETUP = 2, KO_DEAD = 3, KO_DEAD_LYING = 4;
    private Role role = Role.CITIZEN;
    private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache geoCache = software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    // ---- анимации GeckoLib: ходьба/бег по скорости, занятие — отдельной анимацией, удар по мячу — разово
    private static final software.bernie.geckolib.animation.RawAnimation IDLE = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen.idle");
    private static final software.bernie.geckolib.animation.RawAnimation WALK = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen.walk");
    private static final software.bernie.geckolib.animation.RawAnimation RUN = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen.run");
    private static final software.bernie.geckolib.animation.RawAnimation RIDE = software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen.ride");
    private static final java.util.Map<Activity, software.bernie.geckolib.animation.RawAnimation> ACTS = new java.util.EnumMap<>(Activity.class);
    private static final java.util.Map<Activity, software.bernie.geckolib.animation.RawAnimation> WALK_ACTS = new java.util.EnumMap<>(Activity.class);
    /** Падение после удара машиной: полёт → лежит (дышит) / встаёт / погиб и замер. Индекс — фаза KNOCK. */
    private static final software.bernie.geckolib.animation.RawAnimation[] KO_ANIMS = {null,
            software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.citizen.knockdown").thenLoop("animation.citizen.down"),
            software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.citizen.getup"),
            software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.citizen.knockdown").thenPlayAndHold("animation.citizen.dead"),
            software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.citizen.dead")};

    static {
        for (Activity a : Activity.values())
            if (a != Activity.IDLE) ACTS.put(a, software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen." + a.name().toLowerCase()));
        for (Activity a : Activity.values())
            if (a.walking()) WALK_ACTS.put(a, software.bernie.geckolib.animation.RawAnimation.begin().thenLoop("animation.citizen." + a.name().toLowerCase() + (a == Activity.JOG ? "" : "_walk")));
    }

    @Override
    public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "body", 5, state -> {
            int ko = knockPhase();
            if (ko != KO_NONE && !hasVehicle()) {
                // сбили: почти без плавного перехода — удар резкий
                state.getController().transitionLength(ko == KO_GETUP || ko == KO_DEAD_LYING ? 4 : 1);
                return state.setAndContinue(KO_ANIMS[Math.min(ko, KO_ANIMS.length - 1)]);
            }
            state.getController().transitionLength(5);
            Activity a = getActivity();
            if (hasVehicle()) return state.setAndContinue(RIDE); // в машине — сидит на сиденье
            double v = getVelocity().horizontalLengthSquared();
            boolean moving = state.isMoving() && v > 1e-4;
            if (moving && a.walking()) return state.setAndContinue(WALK_ACTS.get(a));
            if (a != Activity.IDLE && (!moving || a.pose())) return state.setAndContinue(ACTS.get(a));
            if (moving) return state.setAndContinue(v > 0.012 ? RUN : WALK);
            return state.setAndContinue(IDLE);
        }).setAnimationSpeedHandler(e -> {
            // у каждого свой темп жестов — двое за одним делом не двигаются в такт (шаг и бег — без изменений, под скорость ног)
            Activity a = e.getActivity();
            if (a == Activity.IDLE && e.getVelocity().horizontalLengthSquared() > 1e-4 || e.knockPhase() != KO_NONE) return 1.0;
            return 0.86 + Math.floorMod(e.getUuid().hashCode() >>> 3, 29) / 100.0;
        }));
        controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "action", 2, state -> software.bernie.geckolib.animation.PlayState.STOP)
                .triggerableAnim("kick", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.citizen.kick"))
                .triggerableAnim("hit", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.citizen.hit"))
                .triggerableAnim("highfive", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.citizen.highfive"))
                .triggerableAnim("drag", software.bernie.geckolib.animation.RawAnimation.begin().thenPlay("animation.citizen.drag")));
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
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.5);
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
        kid = false; // в Новограде детей на улицах нет — взрослый город
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
        builder.add(KNOCK, KO_NONE);
        builder.add(SAY, "");
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        // деревянные двери жители открывают сами (и закрывают за собой)
        if (getNavigation() instanceof net.minecraft.entity.ai.pathing.MobNavigation nav) nav.setCanPathThroughDoors(true);
        goalSelector.add(1, new net.minecraft.entity.ai.goal.LongDoorInteractGoal(this, true));
        goalSelector.add(0, new CombatGoal(this));
        goalSelector.add(0, new JackGoal(this));
        goalSelector.add(0, new DirectedGoal(this));
        goalSelector.add(1, new HomeGoal(this));
        goalSelector.add(2, new PlayBallGoal(this));
        goalSelector.add(3, new BusyGoal(this));
        goalSelector.add(4, new ScheduleGoal(this));
        goalSelector.add(4, new StoryLifeGoal(this));
        goalSelector.add(5, new WanderAroundFarGoal(this, 0.6) {
            @Override
            public boolean canStart() {
                return !stationary && !lifeManaged && !routeWalk && lifeHold == 0 && getActivity() != Activity.SIT && getActivity() != Activity.DANCE && super.canStart();
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
     * заданное («Продавщица Сьюзи», «Мистер Стив») — оставляем, а внешность подбираем под имя.
     */
    private String matchGender(String name) {
        boolean female = isSlim(getSkin());
        String fixed = com.yompix.novograd.life.Names.fixGender(name, female, getUuid().getLeastSignificantBits());
        if (!fixed.equals(name)) return fixed;
        Boolean g = com.yompix.novograd.life.Names.genderOf(name);
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

    /** Режиссёр кат-сцены ведёт героя в точку (идёт или бежит), там он и остаётся. */
    @org.jetbrains.annotations.Nullable public net.minecraft.util.math.Vec3d directTo;
    public double directSpeed = 0.6;

    /** Путь по тротуару (world/Sidewalks): точки после directTo. Пусто — directTo последняя. */
    public final java.util.ArrayDeque<net.minecraft.util.math.Vec3d> route = new java.util.ArrayDeque<>();
    /** Что сделать, когда дошёл до конца пути (войти в дверь, сесть на скамейку…). */
    @org.jetbrains.annotations.Nullable public Runnable onArrive;
    /** Идёт по делам «повседневной жизни» (не сюжет): занятие и вещь в руке на ходу держатся. */
    public boolean routeWalk;
    @org.jetbrains.annotations.Nullable private Activity walkAct;

    /** Идти по точкам пути: занятие на ходу (пакеты, телефон, зонтик…), по приходу — onArrive. */
    public void walkRoute(java.util.List<net.minecraft.util.math.Vec3d> pts, double speed, @org.jetbrains.annotations.Nullable Activity act,
                          net.minecraft.item.ItemStack held, @org.jetbrains.annotations.Nullable Runnable arrive) {
        route.clear();
        if (pts.isEmpty()) {
            if (arrive != null) arrive.run();
            return;
        }
        route.addAll(pts);
        direct(route.poll(), speed);
        routeWalk = true;
        walkAct = act;
        onArrive = arrive;
        if (act != null) dataTracker.set(ACTIVITY, act.ordinal());
        equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, held);
    }

    /** Бросить путь (испугался, драка, сбили). */
    public void stopRoute() {
        if (!routeWalk) return;
        routeWalk = false;
        route.clear();
        onArrive = null;
        walkAct = null;
        directTo = null;
        getNavigation().stop();
    }

    public boolean walkingRoute() {
        return routeWalk && directTo != null;
    }

    /** Реплика над головой на ticks тиков. */
    public void say(String line, int ticks) {
        dataTracker.set(SAY, line);
        sayTicks = ticks;
    }

    public String saying() {
        return dataTracker.get(SAY);
    }

    public void direct(net.minecraft.util.math.Vec3d to, double speed) {
        routeWalk = false;
        route.clear();
        onArrive = null;
        walkAct = null;
        directTo = to;
        directSpeed = speed;
        lockedActivity = false;
        busyTicks = 0;
        foe = null;
        dataTracker.set(ACTIVITY, Activity.IDLE.ordinal());
        getNavigation().startMovingTo(to.x, to.y, to.z, speed);
    }

    static class DirectedGoal extends net.minecraft.entity.ai.goal.Goal {
        private final CitizenEntity c;
        private int stuck, kerbWait;
        private boolean carNear;

        DirectedGoal(CitizenEntity c) {
            this.c = c;
            setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return c.directTo != null && !c.hasVehicle();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            var to = c.directTo;
            if (to == null) return;
            double d = c.squaredDistanceTo(to.x, c.getY(), to.z);
            boolean more = c.routeWalk && !c.route.isEmpty();
            if (d < (more ? 3.0 : 1.2)) {
                if (more && to instanceof com.yompix.novograd.world.Sidewalks.Kerb && kerbWait < 440
                        && c.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                    // у «зебры»: красный для пешехода (gta/TrafficLights) или едет машина — ждём (не дольше ~22 с)
                    if (kerbWait % 5 == 0) {
                        var next = c.route.peek();
                        double dx = next.x - to.x, dz = next.z - to.z;
                        int dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 0 : 2) : (dz > 0 ? 1 : 3);
                        carNear = !com.yompix.novograd.gta.TrafficLights.canCross(sw, c.getBlockPos(), dir) || kerbWait < 90
                                && !sw.getEntitiesByClass(CarEntity.class, c.getBoundingBox().expand(11, 2, 11), car -> car.getVelocity().horizontalLengthSquared() > 0.004).isEmpty();
                    }
                    kerbWait++;
                    if (carNear) {
                        c.getNavigation().stop();
                        if (kerbWait % 40 == 1) c.getLookControl().lookAt(c.route.peek().x, c.getEyeY(), c.route.peek().z);
                        return;
                    }
                }
                kerbWait = 0;
                if (more) { // следующая точка пути по тротуару
                    c.directTo = c.route.poll();
                    c.getNavigation().startMovingTo(c.directTo.x, c.directTo.y, c.directTo.z, c.directSpeed);
                    stuck = 0;
                    return;
                }
                c.getNavigation().stop();
                c.directTo = null;
                if (c.stationary) c.anchor = c.getPos();
                if (c.routeWalk) {
                    c.routeWalk = false;
                    c.walkAct = null;
                    Runnable r = c.onArrive;
                    c.onArrive = null;
                    if (r != null) r.run();
                }
                return;
            }
            if (c.routeWalk && c.walkAct != null && c.getActivity() != c.walkAct && c.busyTicks == 0) c.dataTracker.set(ACTIVITY, c.walkAct.ordinal());
            if (c.getNavigation().isIdle()) {
                if (++stuck > 30) {
                    stuck = 0;
                    if (c.routeWalk) {
                        // прохожий застрял: на глазах у игрока не телепортируем — идём к следующей точке или сдаёмся
                        if (com.yompix.novograd.world.Sidewalks.unseen(c)) c.refreshPositionAndAngles(to.x, to.y, to.z, c.getYaw(), 0);
                        else if (!c.route.isEmpty()) c.directTo = c.route.poll();
                        else c.directTo = null;
                        if (c.directTo == null) {
                            c.routeWalk = false;
                            Runnable r = c.onArrive;
                            c.onArrive = null;
                            if (r != null) r.run();
                        }
                    } else c.refreshPositionAndAngles(to.x, to.y, to.z, c.getYaw(), 0); // путь не находится — просто «дошёл» (в кадре важнее не застрять)
                } else c.getNavigation().startMovingTo(to.x, to.y, to.z, c.directSpeed);
            } else stuck = 0;
        }
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
        // двойник героя (старая копия из выгруженного района) — убрать, главный тот, кто записан в TownState
        if (!getWorld().isClient && !storyId.isEmpty() && age % 40 == 7 && getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            java.util.UUID main = com.yompix.novograd.world.TownState.get(sw).storyNpcs.get(storyId);
            if (main != null && !main.equals(getUuid())) {
                discard();
                return;
            }
        }
        if (knockPhase() != KO_NONE) { bodyYaw = prevBodyYaw = headYaw = prevHeadYaw = getYaw(); }
        if (getWorld().isClient) {
            if (asleep()) { bodyYaw = prevBodyYaw = headYaw = prevHeadYaw = getYaw(); }
            return;
        }
        if (removeIn > 0 && --removeIn == 0) discard();
        if (knockTick()) return;
        revengeTick();
        // бандиты: подошёл к их району с оружием в руках (или ты их враг по сюжету) — нападают
        if (role == Role.GANG && foe == null && age % 20 == 0) {
            var pl = getWorld().getClosestPlayer(this, 12);
            if (pl instanceof net.minecraft.server.network.ServerPlayerEntity sp && !sp.isCreative() && !sp.isSpectator()
                    && (com.yompix.novograd.gta.Weapons.isGun(sp.getMainHandStack()) || com.yompix.novograd.life.Life.get(sp).flags.contains("gang_enemy")) && canSee(sp))
                attack(sp);
        }
        // подкрепление полиции, оставшееся далеко от всех, — убираем
        if (tempCop && foe == null && age % 100 == 0 && getWorld().getClosestPlayer(this, 100) == null) discard();
        // полицейские выходят из машины, когда нарушитель рядом и пешком
        if (role == Role.POLICE && foe != null && hasVehicle() && !foe.hasVehicle() && distanceTo(foe) < 10 && getVehicle() instanceof CarEntity car && car.slow()) stopRiding();
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
        if (sayTicks > 0 && --sayTicks == 0) dataTracker.set(SAY, "");
        if (lockedActivity || busyTicks > 0 || homeActive || foe != null || fleeTicks > 0 || routeWalk || lifeHold > 0 && --lifeHold > 0) return;
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
                    ? new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.PHONE) : net.minecraft.item.ItemStack.EMPTY);
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
        if (com.yompix.novograd.world.Island.zone(getBlockX(), getBlockZ()) != com.yompix.novograd.world.Island.Zone.CORE || r.nextInt(2) == 0)
            com.yompix.novograd.world.LifeAi.chat(this, o, me); // реплики над головами
        o.activityTicks = ticks + 40;
        if (me == Activity.FIGHT && getWorld() instanceof net.minecraft.server.world.ServerWorld w) {
            PlayerEntity p = w.getClosestPlayer(this, 16);
            if (p != null) p.sendMessage(net.minecraft.text.Text.literal("⚠ На улице драка!").formatted(net.minecraft.util.Formatting.RED), true);
        }
    }

    /** Повернуться к точке. */
    public void faceTo(net.minecraft.util.math.Vec3d o) {
        float yaw = (float) (Math.toDegrees(Math.atan2(o.z - getZ(), o.x - getX())) - 90);
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
    }

    /** Занят делом (busy): стоит у полки, сидит, болтает. */
    public boolean isBusy() {
        return busyTicks > 0;
    }

    /** Есть ли собеседник (сценка встречи прохожих). */
    public boolean hasPartner() {
        return partner != null;
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
            // упёрся в закрытую дверь (в том числе железную в магазине) — открыть
            for (net.minecraft.util.math.BlockPos d : new net.minecraft.util.math.BlockPos[]{getBlockPos().offset(getHorizontalFacing()), getBlockPos()}) {
                var st = getWorld().getBlockState(d);
                if (st.getBlock() instanceof net.minecraft.block.DoorBlock db && !st.get(net.minecraft.block.DoorBlock.OPEN)) {
                    db.setOpen(this, getWorld(), st, d, true);
                    getNavigation().recalculatePath();
                    return;
                }
            }
            if (Boolean.getBoolean("novograd.debugstuck")) {
                var bp = getBlockPos();
                var path = getNavigation().getCurrentPath();
                com.yompix.novograd.Novograd.LOG.info("[stuck] {} at {} feet={} front={} target={} times={}", getName().getString(), bp,
                        getWorld().getBlockState(bp).getBlock().getTranslationKey(),
                        getWorld().getBlockState(bp.offset(getHorizontalFacing())).getBlock().getTranslationKey(),
                        path == null ? null : path.getTarget(), stuckTimes);
            }
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
        return role == Role.CITIZEN && storyId.isEmpty() && !stationary && !lockedActivity && !hasVehicle() && removeIn < 0
                && !com.yompix.novograd.world.Residents.is(this); // у жильцов острова свой распорядок (world/Residents)
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

    /** Спокоен: не дерётся, не убегает, не лежит после удара (жильцы острова слушаются распорядка только тогда). */
    public boolean calm() {
        return foe == null && fleeTicks <= 0 && knockPhase() == KO_NONE && !hasVehicle();
    }

    public boolean asleep() {
        return getActivity() == Activity.SLEEP;
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
            if (com.yompix.novograd.world.Residents.is(c)) return false; // жилец: распорядок ведёт world/Residents
            if (c.lifeManaged || c.routeWalk) return false; // прохожий острова: дела выбирает world/LifeAi
            if (com.yompix.novograd.world.Island.zone(c.getBlockX(), c.getBlockZ()) != com.yompix.novograd.world.Island.Zone.CORE) {
                // остров: дела по соседству (магазин, бар, пляж, площадь) — места острова, а не дворы старого города
                chosen = null;
                target = com.yompix.novograd.world.Residents.islandErrand(c, t);
                return target != null && c.squaredDistanceTo(target) > 64;
            }
            String[] where;
            chosen = null;
            if (c.isKid()) where = t < 1500 || t > 15000 ? HOMES : t < 9000 ? new String[]{"school_yard", "school_front"} : new String[]{"park_center", "shop_door", "school_yard"};
            else where = t < 1500 || t > 16500 ? HOMES : t < 10500 ? new String[]{"office_door", "shop_door", "bank_door", "dealer_door", "gym_door", "pizza_door"}
                    : t < 12500 ? new String[]{"park_center", "casino_door", "shop_door", "pizza_door", "gym_door"}
                    : new String[]{"club_door", "pizza_door", "casino_door", "park_center"};
            chosen = where[c.getRandom().nextInt(where.length)];
            double[] p = com.yompix.novograd.world.TownState.get(w).place(chosen);
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
        double[] p = com.yompix.novograd.world.TownState.get(w).place(place);
        if (p == null) return;
        // место у двери смотрит в здание (yaw = p[3]) — «внутрь» на 8 блоков вперёд по взгляду
        double rad = Math.toRadians(p[3]);
        net.minecraft.util.math.Vec3d in = new net.minecraft.util.math.Vec3d(p[0] - Math.sin(rad) * 8, p[1], p[2] + Math.cos(rad) * 8);
        switch (place) {
            case "shop_door" -> {
                net.minecraft.item.Item[] goods = {net.minecraft.item.Items.BREAD, net.minecraft.item.Items.APPLE, com.yompix.novograd.ModContent.COLA,
                        net.minecraft.item.Items.MILK_BUCKET, net.minecraft.item.Items.CAKE, com.yompix.novograd.ModContent.BURGER};
                busy(r.nextBoolean() ? Activity.BAGS : Activity.THINK, 300 + r.nextInt(200), in.add(r.nextInt(9) - 4, 0, r.nextInt(5) - 2),
                        new net.minecraft.item.ItemStack(r.nextBoolean() ? com.yompix.novograd.ModContent.SHOPPING_BAG : goods[r.nextInt(goods.length)]));
            }
            case "casino_door" -> busy(r.nextBoolean() ? Activity.CHEER : Activity.THINK, 400, in.add(r.nextInt(9) - 4, 0, r.nextInt(5) - 2), net.minecraft.item.ItemStack.EMPTY);
            case "pizza_door", "burger_door" -> { // пиццерия/бургерная: за стол (стул — ступеньки внутри) и есть сидя
                net.minecraft.util.math.BlockPos inB = net.minecraft.util.math.BlockPos.ofFloored(in);
                net.minecraft.util.math.BlockPos seat = net.minecraft.util.math.BlockPos.findClosest(inB, 8, 2, b -> w.getBlockState(b).getBlock() instanceof net.minecraft.block.StairsBlock
                        && w.getBlockState(b.up()).isAir() && w.getEntitiesByClass(CitizenEntity.class, new net.minecraft.util.math.Box(b).expand(0.2, 1, 0.2), e -> e != this).isEmpty()).orElse(null);
                net.minecraft.item.ItemStack food = new net.minecraft.item.ItemStack(r.nextInt(3) == 0 ? com.yompix.novograd.ModContent.COLA : com.yompix.novograd.ModContent.BURGER);
                if (seat != null) busy(Activity.SIT_EAT, 500 + r.nextInt(300), net.minecraft.util.math.Vec3d.ofBottomCenter(seat), food);
                else busy(Activity.EAT, 300, in.add(r.nextInt(7) - 3, 0, r.nextInt(5) - 2), food);
            }
            case "office_door", "bank_door" -> busy(Activity.PHONE, 400, in.add(r.nextInt(7) - 3, 0, 0), new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.PHONE));
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
                && !com.yompix.novograd.life.SideQuests.talk((ServerPlayerEntity) player, this)) LifeActions.talk(this, (ServerPlayerEntity) player);
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // сюжетные герои и работники (учитель, продавцы, крупье, банкир, оружейник, механик) — неуязвимы, иначе сломается сюжет и город
        if (!storyId.isEmpty()) return true;
        if (role != Role.CITIZEN && role != Role.POLICE && role != Role.GANG) return true;
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hurt = super.damage(source, amount);
        if (hurt && !getWorld().isClient && isDead() && knockPhase() < KO_DEAD)
            dataTracker.set(KNOCK, knockPhase() == KO_NONE ? KO_DEAD : KO_DEAD_LYING); // падает и остаётся лежать, а не ванильный красный «завал»
        if (hurt && !getWorld().isClient && !runOverHit) {
            if (source.getAttacker() instanceof net.minecraft.server.network.ServerPlayerEntity p) {
                com.yompix.novograd.gta.Crime.hurt(p, this, isDead());
                if (!isDead()) {
                    // полиция и бандиты дают отпор; крепкие парни — тоже, остальные бегут
                    if (role == Role.POLICE || role == Role.GANG || (!isKid() && !isSlim(getSkin()) && getRandom().nextInt(3) == 0)) attack(p);
                    else panic(p.getPos());
                }
            } else if (source.getAttacker() instanceof CitizenEntity o && !isDead() && (role == Role.GANG || role == Role.POLICE)) attack(o);
        }
        return hurt;
    }

    // ------------------------------------------------------------------ сбили машиной (см. CarEntity.runOver)
    /** Пока идёт удар машиной: damage() не зовёт Crime.hurt — розыск решает Wanted.runOver (видела ли полиция). */
    public static boolean runOverHit;
    private int downTicks, getupTicks;
    private boolean airborne;
    @org.jetbrains.annotations.Nullable private net.minecraft.util.math.Vec3d knockFrom;
    @org.jetbrains.annotations.Nullable private net.minecraft.entity.LivingEntity knockedBy;
    /** Сколько тиков лежит труп (ванилла — 20). */
    private static final int CORPSE_TICKS = 140;

    public int knockPhase() {
        return dataTracker.get(KNOCK);
    }

    public boolean knockedDown() {
        int k = knockPhase();
        return k == KO_DOWN || k == KO_GETUP;
    }

    /** Сюжетные и «работающие» жители (герои, продавцы, актёры сцен, квестовые): машина их только отталкивает. */
    public boolean storyProtected() {
        return !storyId.isEmpty() || hasQuest() || stationary || lockedActivity || directTo != null || removeIn >= 0
                || (role != Role.CITIZEN && role != Role.POLICE && role != Role.GANG);
    }

    /** Сбит: летит, падает на спину и лежит ticks тиков, потом встаёт. from — откуда удар (машина). */
    public void knockDown(net.minecraft.util.math.Vec3d from, @org.jetbrains.annotations.Nullable net.minecraft.entity.LivingEntity by, int ticks) {
        if (isDead() || hasVehicle()) return;
        stopRoute();
        lifeHold = 0;
        if (hasNoGravity() && !stationary) setNoGravity(false);
        knockFrom = from;
        knockedBy = by;
        if (knockPhase() == KO_DOWN) {
            downTicks = Math.max(downTicks, ticks);
            return;
        }
        // лицом к машине — падает назад, по ходу удара
        float yaw = (float) Math.toDegrees(Math.atan2(-(from.x - getX()), from.z - getZ()));
        setYaw(yaw);
        setBodyYaw(yaw);
        setHeadYaw(yaw);
        getNavigation().stop();
        busyTicks = 0;
        busyAt = null;
        partner = null;
        foe = null;
        fleeTicks = 0;
        jackCar = null;
        jacker = null;
        dataTracker.set(ACTIVITY, Activity.IDLE.ordinal());
        equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, net.minecraft.item.ItemStack.EMPTY);
        dataTracker.set(KNOCK, KO_DOWN);
        downTicks = ticks;
        getupTicks = 0;
        airborne = true;
    }

    /** Сервер: лежит, встаёт, после — убегает (бандит или полицейский — в бой). true — сегодня больше ничего не делаем. */
    private boolean knockTick() {
        int k = knockPhase();
        if (k == KO_NONE || k >= KO_DEAD) return k >= KO_DEAD;
        getNavigation().stop();
        if (!isOnGround()) airborne = true;
        else if (airborne) {
            airborne = false;
            if (getVelocity().y < -0.05 || age % 2 == 0)
                getWorld().playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_BIG_FALL, net.minecraft.sound.SoundCategory.NEUTRAL, 0.9f, 0.8f);
        }
        if (k == KO_DOWN) {
            if (isOnGround() && --downTicks <= 0) {
                dataTracker.set(KNOCK, KO_GETUP);
                getupTicks = 30;
            }
            return true;
        }
        if (--getupTicks > 0) return true;
        dataTracker.set(KNOCK, KO_NONE);
        var by = knockedBy;
        knockedBy = null;
        if ((role == Role.GANG || role == Role.POLICE && by instanceof net.minecraft.server.network.ServerPlayerEntity sp && com.yompix.novograd.gta.Wanted.stars(sp) > 0)
                && by != null && by.isAlive() && distanceTo(by) < 40) attack(by);
        else if (knockFrom != null) panic(by != null ? by.getPos() : knockFrom);
        return false;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || knockedDown();
    }

    @Override
    public boolean isPushable() {
        return !asleep() && !isInvisible() && knockPhase() == KO_NONE && super.isPushable();
    }

    @Override
    protected void updatePostDeath() {
        if (knockPhase() < KO_DEAD) {
            super.updatePostDeath();
            return;
        }
        // упал и лежит, как в ГТА; потом тело исчезает
        deathTime++;
        if (deathTime >= CORPSE_TICKS && !getWorld().isClient() && !isRemoved()) {
            getWorld().sendEntityStatus(this, net.minecraft.entity.EntityStatuses.ADD_DEATH_PARTICLES);
            remove(RemovalReason.KILLED);
        }
    }

    // ------------------------------------------------------------------ угон: хозяин машины возвращается и выкидывает угонщика
    @org.jetbrains.annotations.Nullable private CarEntity jackCar;
    @org.jetbrains.annotations.Nullable private net.minecraft.server.network.ServerPlayerEntity jacker;
    private int jackTicks, revengeTicks;
    /** Драка за машину — кулаками, даже у бандита. */
    private boolean brawl;
    /** Мстит без угонщика: вернуть машину и уехать. */
    private boolean reclaim;

    /** Игрок выкинул этого водителя из машины. Кто-то убегает, а кто-то — бежит назад и вытаскивает угонщика (как в GTA 5). */
    public void carjacked(net.minecraft.server.network.ServerPlayerEntity p, CarEntity car) {
        var r = getRandom();
        if (role == Role.POLICE) {
            com.yompix.novograd.gta.Wanted.add(p, 1, "угон у полицейского", 2);
            attack(p);
            return;
        }
        boolean angry = !storyProtected() && !p.isCreative() && (role == Role.GANG || r.nextInt(100) < (isSlim(getSkin()) ? 30 : 50));
        if (!angry) {
            shout(p, r.nextBoolean() ? "«Помогите! Машину угнали!»" : "«Эй! Это моя машина!»");
            panic(p.getPos());
            return;
        }
        jackCar = car;
        jacker = p;
        jackTicks = 220;
        reclaim = false;
        busyTicks = 0;
        lockedActivity = false;
        fleeTicks = 0;
        shout(p, role == Role.GANG ? "«Ты чё, совсем страх потерял?!»" : "«А ну вылезай из моей машины!»");
    }

    private void jackLog(String what) {
        if (Boolean.getBoolean("novograd.debugcar")) com.yompix.novograd.Novograd.LOG.info("[jack] {} {}: {} foe={} car={} reclaim={}", getName().getString(), getId(), what, foe, jackCar, reclaim);
    }

    private void shout(net.minecraft.server.network.ServerPlayerEntity p, String line) {
        String who = getCustomName() != null ? getCustomName().getString() : "Водитель";
        if (p.squaredDistanceTo(this) < 32 * 32) p.sendMessage(net.minecraft.text.Text.literal(who + ": " + line).formatted(net.minecraft.util.Formatting.GOLD), false);
        getWorld().playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_NO, net.minecraft.sound.SoundCategory.NEUTRAL, 1.2f,
                isSlim(getSkin()) ? 1.05f : 0.7f);
    }

    /** Угонщик уехал — ругается и уходит прочь. */
    private void giveUp() {
        jackLog("give up");
        var p = jacker;
        var car = jackCar;
        jackCar = null;
        jacker = null;
        reclaim = false;
        if (p != null) shout(p, getRandom().nextBoolean() ? "«Вернись, ворюга! Я в полицию позвоню!»" : "«Да чтоб ты разбился!»");
        if (role != Role.GANG) panic(car != null ? car.getPos() : getPos());
    }

    /** Вытащить угонщика из машины: рывок, толчок, удар — и драка. */
    private void pullOut(net.minecraft.server.network.ServerPlayerEntity p, CarEntity car) {
        jackLog("pull out");
        getNavigation().stop();
        face(p);
        triggerAnim("action", "drag");
        p.stopRiding();
        net.minecraft.util.math.Vec3d away = p.getPos().subtract(car.getPos()).multiply(1, 0, 1);
        if (away.lengthSquared() < 0.01) away = p.getPos().subtract(getPos()).multiply(1, 0, 1);
        away = away.lengthSquared() < 0.01 ? new net.minecraft.util.math.Vec3d(1, 0, 0) : away.normalize();
        p.setVelocity(away.x * 0.55, 0.32, away.z * 0.55);
        p.velocityModified = true;
        getWorld().playSound(null, car.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_IRON_DOOR_OPEN, net.minecraft.sound.SoundCategory.NEUTRAL, 0.9f, 1.1f);
        getWorld().playSound(null, p.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, net.minecraft.sound.SoundCategory.NEUTRAL, 1f, 0.8f);
        p.damage(getDamageSources().mobAttack(this), 2f);
        shout(p, role == Role.GANG ? "«Вылезай, сказал!»" : "«Вылезай! Это моя машина!»");
        jackCar = car;
        jacker = null;
        reclaim = true;
        foe = p;
        brawl = true;
        fleeTicks = 0;
        // немного побьёт (бандит — подольше) и заберёт машину; дал сдачи — драка продолжается
        revengeTicks = (role == Role.GANG ? 160 : 80) + getRandom().nextInt(60);
    }

    /** Отмщение кулаками закончилось: если угонщик не отвечает — сесть в свою машину и уехать. */
    private void revengeTick() {
        if (revengeTicks <= 0 || --revengeTicks > 0) return;
        if (foe instanceof net.minecraft.server.network.ServerPlayerEntity p && getAttacker() == p && age - getLastAttackedTime() < 60) {
            revengeTicks = 60; // игрок даёт сдачи — дерёмся дальше
            return;
        }
        foe = null;
        brawl = false;
        dataTracker.set(ACTIVITY, Activity.IDLE.ordinal());
        if (jackCar == null || !reclaim) return;
        jackTicks = 200; // JackGoal: дойти до машины и сесть
    }

    static class JackGoal extends net.minecraft.entity.ai.goal.Goal {
        private final CitizenEntity c;

        JackGoal(CitizenEntity c) {
            this.c = c;
            setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return c.jackCar != null && c.foe == null && c.fleeTicks == 0 && !c.hasVehicle() && c.knockPhase() == KO_NONE;
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            c.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (c.foe != null) return; // сейчас дерёмся — CombatGoal
            CarEntity car = c.jackCar;
            if (car == null || car.isRemoved() || car.wrecked() || --c.jackTicks <= 0) {
                if (c.jacker != null) c.giveUp();
                else c.jackCar = null;
                return;
            }
            double d = Math.sqrt(c.squaredDistanceTo(car.getX(), c.getY(), car.getZ()));
            if (c.reclaim) {
                // своя машина свободна — сесть за руль и уехать; угонщик снова в ней — вытаскивать заново
                net.minecraft.entity.Entity drv = car.getFirstPassenger();
                if (drv instanceof net.minecraft.server.network.ServerPlayerEntity p && car.getControllingPassenger() == p) {
                    c.reclaim = false;
                    c.jacker = p;
                    c.jackTicks = 200;
                    return;
                }
                if (d > 40) {
                    c.jackCar = null;
                    return;
                }
                if (d > 2.6) {
                    if (c.age % 5 == 0) c.getNavigation().startMovingTo(car.getX(), car.getY(), car.getZ(), 1.2);
                    return;
                }
                c.jackCar = null;
                c.reclaim = false;
                if (!car.getPassengerList().isEmpty() || !(c.getWorld() instanceof net.minecraft.server.world.ServerWorld w)) return;
                c.jackLog("reclaim car");
                if (c.startRiding(car, true)) {
                    c.getWorld().playSound(null, car.getBlockPos(), com.yompix.novograd.ModContent.CAR_START, net.minecraft.sound.SoundCategory.NEUTRAL, 0.8f, 1f);
                    var route = com.yompix.novograd.gta.FunUtil.wander(w, car.getX(), car.getZ(), 6, w.getRandom());
                    if (!route.isEmpty()) car.setRoute(route, 0.55f, null);
                }
                return;
            }
            var p = c.jacker;
            if (p == null || p.isRemoved() || p.isDead()) {
                c.jackCar = null;
                c.jacker = null;
                return;
            }
            c.getLookControl().lookAt(p, 40, 40);
            if (p.getVehicle() != car) {
                // угонщик сам вышел — бьём его тут же
                c.jackCar = null;
                c.jacker = null;
                if (c.distanceTo(p) < 16) {
                    c.jackCar = car;
                    c.reclaim = true;
                    c.foe = p;
                    c.brawl = true;
                    c.revengeTicks = c.role == Role.GANG ? 160 : 100;
                } else c.giveUp();
                return;
            }
            double sp = car.hitSpeed();
            if (sp > 0.3 && d > 5 || d > 30) {
                c.giveUp();
                return;
            }
            if (d > 2.7 || sp > 0.2) {
                if (c.age % 4 == 0) c.getNavigation().startMovingTo(car.getX(), car.getY(), car.getZ(), 1.45);
                return;
            }
            c.pullOut(p, car);
        }
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        // карманные деньги, у бандитов — патроны, иногда пистолет
        var r = getRandom();
        int notes = 1 + r.nextInt(3);
        dropStack(new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.BANKNOTE, notes));
        if (role == Role.GANG) {
            dropStack(new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.AMMO, 6 + r.nextInt(10)));
            if (r.nextInt(4) == 0) dropStack(new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.PISTOL));
        }
    }

    // ------------------------------------------------------------------ бой и паника
    @org.jetbrains.annotations.Nullable private net.minecraft.entity.LivingEntity foe;
    private int fleeTicks, shootCooldown;
    private boolean tempCop, ambient;
    /** Сколько ещё тиков житель занят делом «повседневной жизни» без busy (LifeAi): сам занятие не меняет. */
    public int lifeHold;
    /** Дела выбирает world/LifeAi (прохожий острова, сцена пляжа/парка): свой распорядок и блуждание выключены. */
    public boolean lifeManaged;

    /** Прохожий «фоновой» жизни острова: исчезает, когда игрок далеко. */
    public void setAmbient(boolean a) {
        ambient = a;
    }

    public boolean isAmbient() {
        return ambient;
    }

    /** Полиция подкрепления исчезает, когда розыск снят. */
    public void removeWhenIdle() {
        tempCop = true;
    }

    public boolean chasing(net.minecraft.entity.Entity e) {
        return foe == e;
    }

    public void attack(net.minecraft.entity.LivingEntity target) {
        if (!storyId.isEmpty() && role != Role.GANG && role != Role.POLICE) return;
        stopRoute();
        lifeHold = 0;
        foe = target;
        fleeTicks = 0;
        if (busyTicks > 0) busyTicks = 1;
        lockedActivity = false;
    }

    /** Отбой: разойтись (и исчезнуть, если это подкрепление). */
    public void standDown() {
        jackLog("stand down");
        foe = null;
        brawl = false;
        if (hasVehicle() && getVehicle() instanceof CarEntity car) car.chase(null);
        dataTracker.set(ACTIVITY, Activity.IDLE.ordinal());
        if (tempCop) removeLater(200);
    }

    /** Испуг: убежать подальше от опасности. */
    public void panic(net.minecraft.util.math.Vec3d from) {
        if (role == Role.POLICE || role == Role.GANG || stationary || !storyId.isEmpty() || hasVehicle() || asleep() || foe != null) return;
        stopRoute(); // бросил дела — бежит
        lifeHold = 0;
        if (hasNoGravity() && !stationary) setNoGravity(false); // загорал/лежал — вскочил
        fleeTicks = 140 + getRandom().nextInt(80);
        net.minecraft.util.math.Vec3d away = getPos().subtract(from).multiply(1, 0, 1);
        if (away.lengthSquared() < 0.01) away = new net.minecraft.util.math.Vec3d(1, 0, 0);
        net.minecraft.util.math.Vec3d to = getPos().add(away.normalize().multiply(18));
        busyTicks = 0;
        lockedActivity = false;
        dataTracker.set(ACTIVITY, (isKid() || isSlim(getSkin())) && getRandom().nextBoolean() ? Activity.UPSET.ordinal() : Activity.IDLE.ordinal());
        getNavigation().startMovingTo(to.x, to.y, to.z, 1.45);
    }

    /** Бой: полиция и бандиты стреляют с дистанции, остальные дерутся кулаками. */
    static class CombatGoal extends net.minecraft.entity.ai.goal.Goal {
        private final CitizenEntity c;

        CombatGoal(CitizenEntity c) {
            this.c = c;
            setControls(java.util.EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return (c.foe != null || c.fleeTicks > 0) && !c.hasVehicle();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (c.fleeTicks > 0) {
                if (--c.fleeTicks == 0 || c.getNavigation().isIdle()) {
                    c.fleeTicks = 0;
                    c.dataTracker.set(ACTIVITY, Activity.IDLE.ordinal());
                }
                return;
            }
            var t = c.foe;
            if (t == null || !t.isAlive() || t.isRemoved() || c.squaredDistanceTo(t) > 64 * 64
                    || t instanceof net.minecraft.server.network.ServerPlayerEntity p && (p.isCreative() || p.isSpectator()
                    || c.role == Role.POLICE && com.yompix.novograd.gta.Wanted.stars(p) == 0)) {
                c.standDown();
                return;
            }
            c.getLookControl().lookAt(t, 40, 40);
            double d = c.distanceTo(t);
            boolean gunner = !c.brawl && c.role == Role.GANG || c.role == Role.POLICE && (!(t instanceof net.minecraft.server.network.ServerPlayerEntity p) || com.yompix.novograd.gta.Wanted.stars(p) >= 3);
            if (gunner) {
                if (!(c.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem))
                    c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.PISTOL));
                c.dataTracker.set(ACTIVITY, Activity.AIM.ordinal());
                // держим дистанцию 7–14 блоков и стреляем, когда видим цель
                if (d > 14 || !c.canSee(t)) c.getNavigation().startMovingTo(t, 1.25);
                else if (d < 6) {
                    net.minecraft.util.math.Vec3d back = c.getPos().add(c.getPos().subtract(t.getPos()).normalize().multiply(4));
                    c.getNavigation().startMovingTo(back.x, back.y, back.z, 1.1);
                } else c.getNavigation().stop();
                if (--c.shootCooldown <= 0 && d < 28 && c.canSee(t)) {
                    c.shootCooldown = 16 + c.getRandom().nextInt(18);
                    net.minecraft.util.math.Vec3d eye = c.getEyePos();
                    com.yompix.novograd.gta.Weapons.shoot((net.minecraft.server.world.ServerWorld) c.getWorld(), c, eye, t.getEyePos().subtract(eye),
                            c.role == Role.POLICE ? 3f : 2.5f, 0.07 + d * 0.004, 40, false);
                }
            } else {
                // полиция без стрельбы — бежит задерживать; горожанин — кулаками
                c.dataTracker.set(ACTIVITY, c.role == Role.POLICE ? Activity.IDLE.ordinal() : Activity.FIGHT.ordinal());
                if (d > 1.6) c.getNavigation().startMovingTo(t, c.role == Role.POLICE ? 1.35 : 1.15);
                else {
                    c.getNavigation().stop();
                    if (c.role != Role.POLICE && --c.shootCooldown <= 0) {
                        c.shootCooldown = 18;
                        c.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                        c.tryAttack(t);
                    }
                }
                // драка кулаками — недолго
                if (c.role == Role.CITIZEN && c.getRandom().nextInt(400) == 0) c.standDown();
            }
        }
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
        nbt.putBoolean("Ambient", ambient);
        nbt.putBoolean("Hidden", hidden);
        nbt.putBoolean("Asleep", asleep());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        dataTracker.set(SKIN, nbt.getInt("Skin"));
        // старые сохранения: «Эмили Смита» → «Эмили Смит», учительницы и старые герои → обычные горожане
        if (getCustomName() != null) {
            String n = getCustomName().getString();
            String fixed = n.replaceAll("(Смит|Джонсон|Браун|Миллер|Дэвис|Уилсон|Тейлор|Андерсон|Мур|Кларк)а$", "$1")
                    .replace("Миссис Нэнси", "Миссис Нэнси Мур").replace("Лейтенант Петров", "Лейтенант Питерсон");
            if (n.startsWith("Миссис Анна") || n.startsWith("Мистер Стив") && !n.contains("Механик")) fixed = com.yompix.novograd.life.Names.full(getRandom());
            if (!fixed.equals(n)) setCustomName(net.minecraft.text.Text.literal(fixed));
        }
        try {
            role = Role.valueOf(nbt.getString("Role"));
        } catch (IllegalArgumentException e) {
            role = Role.CITIZEN;
        }
        stationary = nbt.getBoolean("Stationary");
        storyId = nbt.getString("StoryId");
        home = nbt.getString("Home");
        ambient = nbt.getBoolean("Ambient");
        hidden = nbt.getBoolean("Hidden");
        if (nbt.getBoolean("Asleep")) dataTracker.set(ACTIVITY, Activity.SLEEP.ordinal());
        // жители из старых версий: имя и внешность по полу
        if (storyId.isEmpty() && hasCustomName()) {
            String n = getCustomName().getString(), f = matchGender(n);
            if (!f.equals(n)) setCustomName(net.minecraft.text.Text.literal(f));
        }
    }
}
