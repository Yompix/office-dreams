package com.yompix.novograd.entity;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.ModContent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Машина. Едет у того, кто за рулём (как лодка): клиент водителя считает физику и шлёт позицию серверу.
 * Анимации (колёса, руль, крен, фары, выхлоп) рисует клиент по движению машины.
 */
public class CarEntity extends Entity {
    /** Кузова: 0 седан, 1 хэтчбек, 2 спорткупе, 3 люкс-седан, 4 фургон, 5 такси, 6 полиция, 7 внедорожник,
     *  8 пикап, 9 маслкар, 10 лимузин, 11 грузовик (см. CarBody). */
    public static final String[] VARIANTS = {"red", "blue", "yellow", "black", "white", "taxi", "police", "suv", "pickup", "muscle", "limo", "truck"};
    public static final int[] COLORS = {0xD7262C, 0x2C6ECB, 0xF2C032, 0x2A2C31, 0xE8E8EA, 0xF7C600, 0xF2F4F7, 0x3E4B3C, 0xB5462A, 0xF07A12, 0x15161A, 0xE4E4DE};
    public static final int POLICE = 6;
    /** Тюнинг: цвет (0 — заводской), нитро, двигатель 0–2, броня 0–2; здоровье машины 0–100. */
    private static final TrackedData<Integer> PAINT = DataTracker.registerData(CarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TUNE = DataTracker.registerData(CarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> HEALTH = DataTracker.registerData(CarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> NITRO = DataTracker.registerData(CarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> VARIANT = DataTracker.registerData(CarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> WOBBLE = DataTracker.registerData(CarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Свет: 1 — фары, 2 — левый поворотник, 4 — правый, 8 — аварийка. */
    private static final TrackedData<Integer> LIGHTS = DataTracker.registerData(CarEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final int HEADLIGHTS = 1, LEFT = 2, RIGHT = 4, HAZARD = 8;

    /** Посадочные места: x — вправо, z — вперёд (в блоках). */
    private static final double[][] SEATS = {{-0.44, -0.12}, {0.44, -0.12}, {-0.44, -0.94}, {0.44, -0.94}};

    @Nullable private UUID owner;
    public float speed;          // блоков за тик, минус — назад
    public float steer;          // −1..1
    public boolean braking;
    private int hits;

    // автопилот (такси с водителем): маршрут по дорогам и что сделать по приезду
    @Nullable private java.util.List<Vec3d> route;
    private int routeIndex;
    private float autoMax = 0.5f;
    @Nullable private Runnable onArrive;

    public void setRoute(java.util.List<Vec3d> points, float maxSpeed, @Nullable Runnable arrive) {
        route = points.isEmpty() ? null : points;
        routeIndex = 0;
        legFrom = getPos();
        autoMax = maxSpeed;
        onArrive = arrive;
    }

    public int lights() {
        return dataTracker.get(LIGHTS);
    }

    public boolean light(int bit) {
        return (lights() & bit) != 0;
    }

    /** Переключатели водителя: фары, поворотники (взаимоисключающие), аварийка. */
    public void toggleLight(int bit) {
        int l = lights();
        if (bit == LEFT || bit == RIGHT) l = (l & ~(LEFT | RIGHT)) | ((l & bit) != 0 ? 0 : bit);
        else l ^= bit;
        dataTracker.set(LIGHTS, l);
        signalYaw = getYaw();
    }

    private float signalYaw;
    /** Яркость стоп-сигналов и заднего хода считается на каждом клиенте по движению машины. */
    public float brakeGlow, reverseGlow;

    public boolean onRoute() {
        return route != null;
    }

    // плавное движение для тех, кто не за рулём
    private int lerpTicks, coast, yawTick = -1;
    private float tickYawDelta;
    private static final java.util.Map<Entity, Integer> YAW_DONE = new java.util.WeakHashMap<>();
    private double lerpX, lerpY, lerpZ, lerpYaw;

    // только клиент: для анимаций
    public float wheelRot, prevWheelRot, visualSteer, prevVisualSteer, roll, prevRoll, pitchLean, prevPitchLean;
    public float clientSpeed;
    private float lastYaw;

    public CarEntity(EntityType<? extends CarEntity> type, World world) {
        super(type, world);
        this.intersectionChecked = true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(VARIANT, 0);
        builder.add(WOBBLE, 0);
        builder.add(LIGHTS, 0);
        builder.add(PAINT, 0);
        builder.add(TUNE, 0);
        builder.add(HEALTH, 100);
        builder.add(NITRO, 0);
    }

    public int getVariant() {
        return MathHelper.clamp(dataTracker.get(VARIANT), 0, VARIANTS.length - 1);
    }

    public void setVariant(int v) {
        dataTracker.set(VARIANT, v);
    }

    public int getWobble() {
        return dataTracker.get(WOBBLE);
    }

    public boolean isTaxi() {
        return getVariant() == 5;
    }

    private boolean ambient;

    public void setAmbient(boolean a) {
        ambient = a;
    }

    public boolean isAmbient() {
        return ambient;
    }

    public boolean isPolice() {
        return getVariant() == POLICE;
    }

    /** Тип кузова (форма и характер езды). */
    public CarBody body() {
        return CarBody.of(getVariant());
    }

    /** Ручник (Пробел) у водителя на этом клиенте; ставит клиентский тик (CarHud). Сервер его не читает. */
    public static boolean handbrakeInput;
    /** Только у водителя: ручник сейчас затянут. */
    public boolean handbrake;
    /** Направление движения кузова (отстаёт от носа машины в заносе) и угол заноса в градусах. */
    private float moveYaw = Float.NaN;
    public float slip;

    /** Предел скорости игрока на этой машине (без маршрута): кузов, тюнинг двигателя, нитро. */
    public float topSpeed() {
        float max = isScooter() ? 0.42f : isBike() ? 1.25f * (1 + engine() * 0.12f) : 0.95f * body().top * (1 + engine() * 0.12f);
        return nitroOn() ? max * 1.55f : max;
    }

    /** Цвет кузова с учётом покраски в гараже. */
    public int color() {
        int p = dataTracker.get(PAINT);
        return p != 0 ? p : COLORS[getVariant()];
    }

    public void paint(int rgb) {
        dataTracker.set(PAINT, rgb);
    }

    public int tune() {
        return dataTracker.get(TUNE);
    }

    public boolean hasNitro() {
        return (tune() & 1) != 0;
    }

    public int engine() {
        return (tune() >> 1) & 3;
    }

    public int armor() {
        return (tune() >> 3) & 3;
    }

    public void setTune(boolean nitro, int engine, int armor) {
        dataTracker.set(TUNE, (nitro ? 1 : 0) | (engine & 3) << 1 | (armor & 3) << 3);
    }

    public int health() {
        return dataTracker.get(HEALTH);
    }

    public void repair() {
        dataTracker.set(HEALTH, 100);
    }

    public boolean wrecked() {
        return health() <= 0;
    }

    public boolean nitroOn() {
        return dataTracker.get(NITRO) > 0;
    }

    private long nitroReady;

    /** Нитро: 3 секунды ускорения, перезарядка 10 секунд. */
    public boolean fireNitro() {
        if (!hasNitro() || wrecked() || getWorld().getTime() < nitroReady) return false;
        dataTracker.set(NITRO, 60);
        nitroReady = getWorld().getTime() + 260;
        getWorld().playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.NEUTRAL, 0.9f, 0.6f);
        return true;
    }

    public boolean slow() {
        return Math.abs(speed) < 0.08f && getVelocity().horizontalLengthSquared() < 0.01;
    }

    /** Попадание пули: машина дымит, а при нуле — взрывается и горит. */
    public void hitByBullet(float dmg, net.minecraft.entity.LivingEntity shooter) {
        if (wrecked() || getWorld().isClient) return;
        int h = health() - Math.round(dmg * (2.2f - armor() * 0.6f));
        dataTracker.set(HEALTH, Math.max(0, h));
        dataTracker.set(WOBBLE, 6);
        if (h <= 0) wreck();
    }

    private int wreckTicks;

    private void wreck() {
        var w = (net.minecraft.server.world.ServerWorld) getWorld();
        w.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        w.playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.NEUTRAL, 2f, 0.9f);
        for (Entity e : getPassengerList()) {
            e.stopRiding();
            e.damage(w.getDamageSources().explosion(this, null), 6);
        }
        for (Entity e : w.getOtherEntities(this, getBoundingBox().expand(3), e -> e instanceof net.minecraft.entity.LivingEntity))
            e.damage(w.getDamageSources().explosion(this, null), 5);
        speed = 0;
        route = null;
        chaseTarget = null;
        wreckTicks = 1200;
    }

    private void gtaTick() {
        var w = getWorld();
        // дым от повреждений, огонь у разбитой
        if (w.isClient) {
            if (health() < 35 && age % 3 == 0) w.addParticle(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.1, getZ(), 0, 0.05, 0);
            if (wrecked() && age % 2 == 0) w.addParticle(net.minecraft.particle.ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5), getY() + 1, getZ() + (random.nextDouble() - 0.5), 0, 0.04, 0);
            if (nitroOn()) {
                double rad = Math.toRadians(getYaw());
                for (int i = 0; i < 2; i++)
                    w.addParticle(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.sin(rad) * 1.7 + (random.nextDouble() - 0.5) * 0.3, getY() + 0.4,
                            getZ() - Math.cos(rad) * 1.7 + (random.nextDouble() - 0.5) * 0.3, Math.sin(rad) * 0.2, 0.01, -Math.cos(rad) * 0.2);
            }
            return;
        }
        if (wrecked()) {
            if (--wreckTicks <= 0) discard();
            return;
        }
        // полицейская машина: сирена и погоня за нарушителем
        if (isPolice() && chaseTarget != null) {
            if (!chaseTarget.isAlive() || chaseTarget.squaredDistanceTo(this) > 120 * 120) chase(null);
            else {
                if (age % 48 == 0) w.playSound(null, getBlockPos(), ModContent.SIREN, SoundCategory.NEUTRAL, 1.6f, 1f);
                if (age % 15 == 0 && getControllingPassenger() == null) {
                    Vec3d tp = chaseTarget.getPos();
                    setRoute(java.util.List.of(tp), chaseTarget.hasVehicle() ? 0.9f : 0.6f, null);
                }
                // догнали пешего — полицейские выходят
                if (!chaseTarget.hasVehicle() && squaredDistanceTo(chaseTarget) < 100 && slow())
                    for (Entity e : new java.util.ArrayList<>(getPassengerList())) if (e instanceof CitizenEntity) e.stopRiding();
            }
        }
    }

    // ------------------------------------------------------------------ погоня полиции
    @Nullable private Entity chaseTarget;

    public void chase(@Nullable Entity target) {
        chaseTarget = target;
        if (target == null) route = null;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Nullable public UUID getOwner() {
        return owner;
    }

    @Override
    public float getStepHeight() {
        return 1.05f;
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canHit() {
        return !isRemoved();
    }

    @Override
    public boolean collidesWith(Entity other) {
        return (other.isCollidable() || other.isPushable()) && !isConnectedThroughVehicle(other);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().size() < (isScooter() ? 1 : isBike() ? 2 : SEATS.length);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (hasNpcDriver()) return null; // за рулём таксист — едет автопилот, игрок пассажир
        return getFirstPassenger() instanceof PlayerEntity p ? p : null;
    }

    /** В машине житель-водитель (такси): он всегда за рулём, даже если игрок сел позже. */
    public boolean hasNpcDriver() {
        for (Entity e : getPassengerList()) if (e instanceof CitizenEntity) return true;
        return false;
    }

    /**
     * Какое место занимает пассажир. Ванилла ставит игрока первым в список (то есть за руль),
     * поэтому места раздаём сами: житель-водитель — руль, «сумка» такси — рядом, остальные — сзади.
     */
    public int seatOf(Entity passenger) {
        java.util.List<Entity> order = new java.util.ArrayList<>(getPassengerList());
        order.sort(java.util.Comparator.comparingInt(e -> e instanceof CitizenEntity ? 0 : e instanceof net.minecraft.entity.decoration.ArmorStandEntity ? 1 : 2));
        return Math.max(0, order.indexOf(passenger));
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.shouldCancelInteraction()) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (wrecked()) {
            player.sendMessage(Text.literal("🔥 Машина разбита — её починят только в «Нитро-Гараже»"), true);
            return ActionResult.CONSUME;
        }
        if (hasNpcDriver() && player instanceof ServerPlayerEntity sp0 && !com.yompix.novograd.life.Taxi.isClientOf(sp0, this) && !player.isSneaking()) {
            // угон: выкидываем водителя
            for (Entity e : new java.util.ArrayList<>(getPassengerList()))
                if (e instanceof CitizenEntity c && seatOf(c) == 0) {
                    c.stopRiding();
                    getWorld().playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.NEUTRAL, 0.8f, 1.2f);
                    c.carjacked(sp0, this); // убегает — или возвращается и вытаскивает угонщика (как в GTA 5)
                }
            com.yompix.novograd.gta.Crime.carjack(sp0);
            if (isPolice()) com.yompix.novograd.gta.Wanted.add(sp0, 2, "угон полицейской машины", 2);
        }
        boolean driver = getPassengerList().isEmpty();
        if (driver && !isScooter() && !player.isCreative() && player instanceof ServerPlayerEntity sp) {
            var d = com.yompix.novograd.life.Life.get(sp);
            boolean young = false; // в Новограде все взрослые
            if (young) {
                player.sendMessage(Text.literal("Водить машину можно с 18 лет и с правами. Пока — электроскутер (есть в магазине)!"), true);
                return ActionResult.CONSUME;
            }
        }
        if (driver && owner != null && !owner.equals(player.getUuid()) && !player.isCreative()) {
            // за руль чужой машины нельзя; пассажиром — можно, когда хозяин уже сел
            player.sendMessage(Text.literal("🔒 Это чужая машина: за руль садится только хозяин"), true);
            return ActionResult.CONSUME;
        }
        if (player.startRiding(this) && driver) {
            getWorld().playSound(null, getBlockPos(), ModContent.CAR_START, SoundCategory.NEUTRAL, 0.8f, 1f);
        }
        return ActionResult.CONSUME;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (getWorld().isClient || isRemoved()) return true;
        if (isInvulnerableTo(source)) return false;
        dataTracker.set(WOBBLE, 10);
        if (source.getAttacker() instanceof ServerPlayerEntity p) {
            boolean mine = owner != null && owner.equals(p.getUuid());
            if (p.isCreative() && p.isSneaking()) {
                discard();
            } else if (mine && p.isSneaking()) {
                if (++hits >= 3) {
                    ItemStack stack = getPickBlockStack();
                    if (!p.getInventory().insertStack(stack)) dropStack(stack);
                    removeAllPassengers();
                    discard();
                } else {
                    p.sendMessage(Text.literal("🔧 Ещё " + (3 - hits) + " удар(а) — машина уйдёт в инвентарь"), true);
                }
            } else if (!mine && !p.isCreative()) {
                p.sendMessage(Text.literal("🚨 Это не твоя машина!"), true);
            }
        }
        return true;
    }

    @Override
    public void tick() {
        if (dataTracker.get(WOBBLE) > 0) dataTracker.set(WOBBLE, dataTracker.get(WOBBLE) - 1);
        if (!getWorld().isClient && dataTracker.get(NITRO) > 0) dataTracker.set(NITRO, dataTracker.get(NITRO) - 1);
        gtaTick();
        super.tick();
        float yawBefore = getYaw();
        if (isLogicalSideForUpdatingMovement()) {
            lerpTicks = 0;
            updateTrackedPosition(getX(), getY(), getZ());
            drive();
        } else {
            if (lerpTicks > 0) {
                lerpPosAndRotation(lerpTicks, lerpX, lerpY, lerpZ, lerpYaw, 0);
                lerpTicks--;
            }
            if (!getWorld().isClient && getControllingPassenger() == null) drive();
        }
        if (!getWorld().isClient && !isScooter() && !isBike()) crashCheck();
        if (!(this instanceof com.yompix.novograd.vehicle.MotorVehicle)) {
            if (!getWorld().isClient) runOver();
            else if (isLogicalSideForUpdatingMovement()) runOverClient();
        }
        // поворот машины передаём пассажирам в updatePassengerPosition — после их тика,
        // тогда камера поворачивается плавно между кадрами, а не ступеньками раз в тик
        tickYawDelta = MathHelper.wrapDegrees(getYaw() - yawBefore);
        if (!getWorld().isClient && route == null && (light(LEFT) || light(RIGHT)) && Math.abs(MathHelper.wrapDegrees(getYaw() - signalYaw)) > 70
                && Math.abs(tickYawDelta) < 0.5f) dataTracker.set(LIGHTS, lights() & ~(LEFT | RIGHT));
        yawTick = age;
        if (getWorld().isClient) {
            clientAnimations();
            Novograd.CLIENT.carTick(this);
        }
        checkBlockCollision();
        pushOthers();
    }

    // ------------------------------------------------------------------ удары: вмятины от аварий
    private final double[] stepLen = new double[8];
    private double lastCX = Double.NaN, lastCZ;

    /**
     * Сервер: по пути за последние тики ловим резкую остановку (удар о стену или машину) — кузов мнётся, машину трясёт.
     * Аварии сами по себе машину не взрывают: ниже 12 «здоровья» от ударов не опускается.
     */
    private void crashCheck() {
        if (Double.isNaN(lastCX)) {
            lastCX = getX();
            lastCZ = getZ();
            return;
        }
        double d = Math.hypot(getX() - lastCX, getZ() - lastCZ);
        lastCX = getX();
        lastCZ = getZ();
        System.arraycopy(stepLen, 1, stepLen, 0, stepLen.length - 1);
        stepLen[stepLen.length - 1] = d > 6 ? 0 : d; // телепорт — не удар
        double before = 0, after = 0;
        for (int i = 0; i < 4; i++) before += stepLen[i];
        for (int i = 4; i < 8; i++) after += stepLen[i];
        if (before > 2.2 && after < before * 0.3 && age > 40 && !wrecked()) {
            int dmg = (int) Math.round((before / 4 - 0.45) * 40) + 3;
            dmg = Math.round(dmg * (1.2f - armor() * 0.35f));
            int h = Math.max(Math.min(health(), 12), health() - dmg);
            if (h < health()) dataTracker.set(HEALTH, h);
            dataTracker.set(WOBBLE, 10);
            getWorld().playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.NEUTRAL, 1f, 0.7f);
            java.util.Arrays.fill(stepLen, 0);
        }
    }

    // ------------------------------------------------------------------ сбить пешехода (как в ГТА)
    /** Сервер: как машина реально двигалась за тик (у игрока физику считает клиент — берём по позиции). */
    private Vec3d hitMotion = Vec3d.ZERO;
    /** Позиции за последние тики: пакеты движения от клиента приходят пачками, скорость берём по окну. */
    private final Vec3d[] hitTrail = new Vec3d[6];
    private int hitTrailN;
    private final java.util.Map<Entity, Long> hitCooldown = new java.util.WeakHashMap<>();
    /** Только у водителя на клиенте: кого уже «почувствовали» (машина теряет скорость один раз за удар). */
    private final java.util.Map<Entity, Integer> clientHits = new java.util.WeakHashMap<>();

    /** Скорость машины в блоках за тик (сервер, по факту движения). */
    public double hitSpeed() {
        return hitMotion.horizontalLength();
    }

    /** Половина длины и ширины кузова (бокс сущности квадратный, а машина длинная). */
    private double halfLen() {
        return isScooter() ? 0.8 : isBike() ? 1.1 : exhaustBack() + 0.35;
    }

    private double halfWid() {
        return isScooter() || isBike() ? 0.45 : body() == CarBody.TRUCK || body() == CarBody.VAN ? 1.15 : 1.0;
    }

    /** Кого задевает машина, едущая по dir: точка в кузове (с запасом) и впереди по ходу. */
    private java.util.List<LivingEntity> inPath(Vec3d dir) {
        double rad = Math.toRadians(getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(rad), 0, Math.cos(rad)), side = new Vec3d(-Math.cos(rad), 0, -Math.sin(rad));
        double hl = halfLen(), hw = halfWid();
        java.util.List<LivingEntity> out = new java.util.ArrayList<>();
        for (Entity e : getWorld().getOtherEntities(this, getBoundingBox().expand(hl + 0.6, 0.2, hl + 0.6),
                e -> e instanceof LivingEntity le && le.isAlive() && !e.hasVehicle() && !isConnectedThroughVehicle(e) && !e.isSpectator()
                        && !(e instanceof net.minecraft.entity.decoration.ArmorStandEntity) && !e.isInvisible()
                        && !(e instanceof PlayerEntity pl && (pl.isCreative() || pl.isSpectator())))) {
            Vec3d rel = e.getPos().subtract(getPos());
            if (e.getY() > getY() + 1.6 || e.getY() + e.getHeight() < getY() + 0.1) continue;
            double r = e.getWidth() / 2 + 0.15;
            double along = rel.dotProduct(fwd), lat = rel.dotProduct(side);
            if (Math.abs(along) > hl + r || Math.abs(lat) > hw + r) continue;
            if (rel.multiply(1, 0, 1).dotProduct(dir) < -0.2) continue; // сзади по ходу — не наш удар
            out.add((LivingEntity) e);
        }
        return out;
    }

    /** Кто за рулём: игрок или житель-водитель (такси, полиция, трафик). */
    @Nullable
    private LivingEntity driver() {
        LivingEntity p = getControllingPassenger();
        if (p != null) return p;
        for (Entity e : getPassengerList()) if (e instanceof CitizenEntity c && seatOf(c) == 0) return c;
        return null;
    }

    /**
     * Сервер: наехали на человека. Медленно — толчок и синяк, быстро — полёт через капот и смерть.
     * Сюжетных героев и работников только отталкиваем — миссии не ломаются.
     */
    private void runOver() {
        Vec3d now = getPos();
        Vec3d prev = hitTrail[hitTrailN % hitTrail.length];
        Vec3d last = hitTrail[(hitTrailN + hitTrail.length - 1) % hitTrail.length];
        if (last != null && now.squaredDistanceTo(last) > 36) java.util.Arrays.fill(hitTrail, null); // телепорт
        prev = hitTrail[hitTrailN % hitTrail.length];
        hitTrail[hitTrailN % hitTrail.length] = now;
        hitTrailN++;
        if (prev == null) {
            hitMotion = Vec3d.ZERO;
            return;
        }
        // средняя скорость за окно, не больше того, что машина вообще может (защита от рывков синхронизации)
        Vec3d m = now.subtract(prev).multiply(1.0 / hitTrail.length);
        double cap = topSpeed() * 1.25 + 0.05;
        if (m.horizontalLength() > cap) m = m.multiply(cap / m.horizontalLength());
        hitMotion = m;
        double s = hitSpeed();
        if (s < 0.12 || wrecked() || !(getWorld() instanceof net.minecraft.server.world.ServerWorld w)) return;
        Vec3d dir = new Vec3d(hitMotion.x, 0, hitMotion.z).normalize();
        long t = w.getTime();
        int victims = 0, kills = 0;
        LivingEntity drv = driver();
        for (LivingEntity v : inPath(dir)) {
            if (t - hitCooldown.getOrDefault(v, -100L) < 14) continue;
            hitCooldown.put(v, t);
            boolean safe = v instanceof CitizenEntity c && c.storyProtected();
            Vec3d side = new Vec3d(-dir.z, 0, dir.x);
            if (v.getPos().subtract(getPos()).dotProduct(side) < 0) side = side.multiply(-1);
            if (safe) {
                // герой сюжета / продавец / актёр: отшагивает в сторону, без урона
                v.setVelocity(side.x * 0.45 + dir.x * s * 0.4, 0.2, side.z * 0.45 + dir.z * s * 0.4);
                v.velocityModified = true;
                w.playSound(null, v.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, SoundCategory.NEUTRAL, 0.6f, 0.8f);
                continue;
            }
            float dmg = (float) Math.max(1, (s - 0.1) * 40);
            boolean knock = s >= 0.22;
            var src = w.getDamageSources().create(net.minecraft.entity.damage.DamageTypes.FLY_INTO_WALL, this, drv);
            CitizenEntity.runOverHit = true;
            try {
                v.damage(src, dmg);
            } finally {
                CitizenEntity.runOverHit = false;
            }
            boolean dead = !v.isAlive();
            if (Boolean.getBoolean("novograd.debugcar"))
                Novograd.LOG.info("[runover] hit {} speed {} dmg {} hp {} dead {}", v.getName().getString(), String.format("%.2f", s), dmg, v.getHealth(), dead);
            // полёт: по ходу машины и вверх, чуть в сторону; медленно — просто отпихнуло с дороги
            double fwdK = knock ? s * 1.05 + 0.12 : s * 0.6 + 0.1;
            double up = knock ? Math.min(0.62, 0.18 + s * 0.45) : 0.18;
            double sideK = knock ? (random.nextDouble() - 0.3) * 0.25 : 0.35;
            v.setVelocity(dir.x * fwdK + side.x * sideK, up, dir.z * fwdK + side.z * sideK);
            v.velocityModified = true;
            v.fallDistance = 0;
            if (v instanceof CitizenEntity c && (knock || dead)) c.knockDown(getPos(), drv, 50 + random.nextInt(50));
            // звук: глухой удар, на скорости — ещё и металл
            w.playSound(null, v.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.NEUTRAL, 1.2f, 0.55f);
            if (knock) w.playSound(null, v.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_BIG_FALL, SoundCategory.NEUTRAL, 1.0f, 0.7f);
            if (s > 0.45) w.playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.NEUTRAL, 0.55f, 1.3f);
            // машина теряет скорость (у автопилота — здесь, у игрока — на его клиенте), на скорости мнётся
            if (getControllingPassenger() == null) speed *= isBike() || isScooter() ? 0.55f : body() == CarBody.TRUCK ? 0.9f : 0.75f;
            if (s > 0.45 && health() > 12) {
                dataTracker.set(HEALTH, Math.max(12, health() - (int) Math.round((s - 0.4) * 12)));
            }
            dataTracker.set(WOBBLE, knock ? 8 : 4);
            victims++;
            if (dead) kills++;
            if (drv instanceof ServerPlayerEntity sp) com.yompix.novograd.gta.Wanted.runOver(sp, this, v, dead);
        }
        if (victims > 0 && drv instanceof ServerPlayerEntity sp) com.yompix.novograd.gta.Wanted.runOverStreak(sp, victims, kills);
    }

    /** Клиент водителя: удар чувствуется сразу — машина теряет скорость (сервер шлёт только урон и полёт жертвы). */
    private void runOverClient() {
        if (Math.abs(speed) < 0.12f) return;
        double rad = Math.toRadians(Float.isNaN(moveYaw) ? getYaw() : moveYaw);
        Vec3d dir = new Vec3d(-Math.sin(rad), 0, Math.cos(rad)).multiply(Math.signum(speed));
        for (LivingEntity v : inPath(dir)) {
            if (age - clientHits.getOrDefault(v, -100) < 14) continue;
            clientHits.put(v, age);
            if (v instanceof CitizenEntity c && c.storyProtected()) {
                speed *= 0.5f; // в героя «упёрлись»
                continue;
            }
            speed *= isBike() || isScooter() ? 0.55f : body() == CarBody.TRUCK ? 0.9f : 0.78f;
        }
    }

    /** Физика: газ, тормоз, задний ход, руль зависит от скорости, гравитация, ступеньки до 1 блока. */
    /** Кто впереди на полосе движения. */
    private record Obstacle(double dist, double lat, boolean still, boolean living) {}

    private int blocked, dodgeTicks, reverse;
    private Vec3d legFrom;
    private float dodge;

    @org.jetbrains.annotations.Nullable
    private Obstacle scanAhead(Vec3d fwd, Vec3d side) {
        Obstacle best = null;
        for (Entity e : getWorld().getOtherEntities(this, getBoundingBox().expand(11, 2, 11),
                e -> !hasPassenger(e) && !e.hasVehicle() && (e instanceof CarEntity || e instanceof LivingEntity) && !(e instanceof net.minecraft.entity.decoration.ArmorStandEntity) && !e.isSpectator())) {
            Vec3d rel = e.getPos().subtract(getPos());
            double along = rel.dotProduct(fwd), lat = rel.dotProduct(side);
            double halfW = e instanceof CarEntity ? 2.0 : 1.4;
            if (along < 1.0 || along > 11 || Math.abs(lat - dodge) > halfW) continue;
            if (best == null || along < best.dist) {
                boolean still = e.getVelocity().horizontalLengthSquared() < 0.003 && !(e instanceof CarEntity c && Math.abs(c.speed) > 0.05f);
                best = new Obstacle(along, lat, still, e instanceof LivingEntity);
            }
        }
        return best;
    }

    private void drive() {
        float fwd = 0, side = 0;
        if (getControllingPassenger() instanceof PlayerEntity p) {
            fwd = p.forwardSpeed;
            side = p.sidewaysSpeed;
        } else if (route != null) {
            Vec3d t = route.get(routeIndex);
            if (Boolean.getBoolean("novograd.debugcar") && age % 40 == 0)
                Novograd.LOG.info("car {} pos {} target#{} {} speed {} hc {} ground {}", getId(), getPos(), routeIndex, t, speed, horizontalCollision, isOnGround());
            double dx = t.x - getX(), dz = t.z - getZ(), dist = Math.sqrt(dx * dx + dz * dz);
            boolean last = routeIndex == route.size() - 1;
            if (dist < (last ? 2.5 : 3.5)) {
                if (last) {
                    route = null;
                    speed *= 0.3f;
                    Runnable r = onArrive;
                    onArrive = null;
                    if (r != null) r.run();
                } else {
                    legFrom = t;
                    routeIndex++;
                }
            } else {
                // смотрим вперёд: кто на нашей полосе — машина, человек, игрок
                double rad0 = Math.toRadians(getYaw());
                Vec3d fwdV = new Vec3d(-Math.sin(rad0), 0, Math.cos(rad0)), sideV = new Vec3d(-Math.cos(rad0), 0, -Math.sin(rad0));
                Obstacle ob = scanAhead(fwdV, sideV);
                if (Boolean.getBoolean("novograd.debugcar") && age % 20 == 0)
                    Novograd.LOG.info("car {} ob {} blocked {} dodge {} rev {} speed {}", getId(), ob, blocked, dodge, reverse, speed);
                if (ob != null && ob.still && Math.abs(speed) < 0.06f) blocked++;
                else if (ob == null) blocked = Math.max(0, blocked - 2);
                if (blocked > 35 && dodge == 0 && ob != null) {
                    // стоит намертво (припаркованная машина, зевака) — сдаём чуть назад и объезжаем с другой стороны
                    dodge = ob.lat > 0 ? -3.4f : 3.4f;
                    dodgeTicks = 120;
                    blocked = 0;
                    if (ob.dist < 6) reverse = 22;
                }
                // линия маршрута от прошлой точки до цели: по ней считаем «свою полосу» и соседнюю
                if (legFrom == null) legFrom = getPos();
                Vec3d line = new Vec3d(t.x - legFrom.x, 0, t.z - legFrom.z);
                if (line.lengthSquared() < 0.01) line = fwdV;
                line = line.normalize();
                Vec3d perp = new Vec3d(-line.z, 0, line.x);
                if (dodge != 0) {
                    // пока помеха рядом с нашей полосой — держимся соседней
                    for (Entity e : getWorld().getOtherEntities(this, getBoundingBox().expand(9, 2, 9), e -> e instanceof CarEntity || (e instanceof LivingEntity && !e.hasVehicle()))) {
                        if (hasPassenger(e)) continue;
                        Vec3d rel = e.getPos().subtract(getPos());
                        double al = rel.dotProduct(line), la = e.getPos().subtract(legFrom).dotProduct(perp) - getPos().subtract(legFrom).dotProduct(perp) + getPos().subtract(legFrom).dotProduct(perp);
                        if (al > -4.5 && al < 9 && Math.abs(e.getPos().subtract(legFrom).dotProduct(perp)) < 1.9) dodgeTicks = Math.max(dodgeTicks, 30);
                    }
                    if (--dodgeTicks <= 0) dodge = 0;
                }
                if (ob != null && ob.living && ob.dist < 7 && age % 50 == 0 && !getWorld().isClient)
                    getWorld().playSound(null, getBlockPos(), ModContent.CAR_HORN, SoundCategory.NEUTRAL, 0.6f, 1.1f);
                double tx = t.x, tz = t.z;
                if (dodge != 0) {
                    // перестраиваемся: цель — точка в 7 блоках впереди на соседней полосе
                    double along = getPos().subtract(legFrom).dotProduct(line);
                    Vec3d aim = legFrom.add(line.multiply(along + 7)).add(perp.multiply(dodge));
                    tx = aim.x;
                    tz = aim.z;
                }
                float want = (float) Math.toDegrees(Math.atan2(-(tx - getX()), tz - getZ()));
                float diff = MathHelper.wrapDegrees(want - getYaw());
                if (!getWorld().isClient && age % 10 == 0) {
                    long tod = getWorld().getTimeOfDay() % 24000;
                    int l = (tod > 12300 && tod < 23700 ? HEADLIGHTS : 0);
                    // поворачиваем к следующей точке маршрута — заранее мигаем поворотником
                    Vec3d next = routeIndex + 1 < route.size() ? route.get(routeIndex + 1) : null;
                    float turn = Math.abs(diff) > 25 ? diff : 0;
                    if (turn == 0 && next != null && dist < 14) turn = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-(next.x - t.x), next.z - t.z)) - getYaw());
                    if (Math.abs(turn) > 30) l |= turn > 0 ? RIGHT : LEFT;
                    if (dodge != 0) l |= dodge > 0 ? LEFT : RIGHT;
                    dataTracker.set(LIGHTS, l);
                }
                side = MathHelper.clamp(-diff / 18f, -1, 1);
                // тормозим перед поворотами, у цели и перед тем, кто впереди
                float limit = Math.abs(diff) > 50 ? 0.18f : last && dist < 10 ? 0.22f : autoMax;
                if (ob != null) limit = Math.min(limit, (float) Math.max(0, (ob.dist - (ob.living ? 3.2 : 4.6)) / 6.0) * autoMax);
                if (reverse > 0) {
                    // задний ход, колёса прямо
                    reverse--;
                    side = 0;
                    limit = -1;
                }
                fwd = limit < 0 ? -1 : speed < limit ? 1 : speed > limit + 0.05f ? -1 : 0;
                if (horizontalCollision && speed < 0.05f) setYaw(getYaw() + MathHelper.clamp(diff, -20, 20));
            }
        }
        braking = false;
        boolean player = getControllingPassenger() instanceof PlayerEntity;
        boolean fourWheels = !isBike() && !isScooter();
        CarBody b = fourWheels ? body() : CarBody.SEDAN;
        float max = isTouchingWater() ? 0.15f : route != null && !player ? autoMax + 0.05f : topSpeed();
        if (route != null && !player && nitroOn()) max *= 1.55f;
        // ручник — только у водителя на его клиенте (физику машины игрока считает его клиент)
        handbrake = player && fourWheels && getWorld().isClient && handbrakeInput && !wrecked();
        if (wrecked()) {
            fwd = 0;
            max = 0;
        }
        float sp = Math.abs(speed);
        if (!player) {
            // автопилот (такси, трафик, полиция): прежняя простая модель, под неё настроены маршруты
            if (fwd > 0) {
                speed += speed < 0 ? 0.05f : (nitroOn() ? 0.04f : isBike() ? 0.028f : 0.018f) * (1 - speed / Math.max(max, 0.01f) * 0.6f);
            } else if (fwd < 0) {
                if (speed > 0.02f) {
                    speed -= 0.05f;
                    braking = true;
                } else {
                    speed = Math.max(speed - 0.012f, -0.25f);
                }
            } else {
                speed *= isOnGround() ? 0.975f : 0.995f;
                if (Math.abs(speed) < 0.004f) speed = 0;
            }
            speed = MathHelper.clamp(speed, -0.25f, max);
        } else {
            // игрок: мягкий старт, тяга падает к максималке, настоящий тормоз, задний ход, торможение мотором
            if (fwd > 0) {
                if (speed < -0.01f) {
                    speed = Math.min(0, speed + 0.045f * b.brake);
                    braking = true;
                } else {
                    float f = MathHelper.clamp(speed / Math.max(max, 0.01f), 0, 1.2f);
                    float launch = 0.6f + 0.4f * MathHelper.clamp(f * 5, 0, 1);       // сцепление: с места чуть мягче
                    float torque = launch * Math.max(0.04f, 1 - f * f * 0.92f);        // к максималке тяга уходит плавно
                    float base = nitroOn() ? 0.042f : isScooter() ? 0.016f : isBike() ? 0.03f : 0.021f * b.accel * (1 + engine() * 0.1f);
                    speed += base * torque;
                }
            } else if (fwd < 0) {
                if (speed > 0.01f) {
                    speed = Math.max(0, speed - (0.03f + sp * 0.045f) * b.brake);
                    braking = true;
                } else {
                    speed = Math.max(speed - 0.011f * (1 - Math.min(1, -speed / 0.28f) * 0.7f), -0.28f);
                }
            } else {
                speed *= isOnGround() ? 0.984f : 0.996f;
                speed -= Math.signum(speed) * Math.min(Math.abs(speed), 0.0016f);
            }
            if (handbrake) {
                speed -= Math.signum(speed) * Math.min(Math.abs(speed), 0.009f + sp * 0.012f);
                braking = true;
            }
            // после нитро скорость спадает плавно, а не обрывается
            if (speed > max) speed = Math.max(max, speed * 0.97f);
            speed = Math.max(speed, -0.28f);
        }
        // руль: на скорости поворот меньше и плавнее (без «дёрганья» на прямой)
        sp = Math.abs(speed);
        steer = MathHelper.lerp(player ? 0.22f + 0.12f * (1 - Math.min(sp, 1)) : 0.25f, steer, side);
        float grip = MathHelper.clamp(sp * 6, 0, 1);
        float turnK = player ? 4.5f * b.turn * (1 - Math.min(sp, 1.2f) * 0.42f) : 4.2f * (1 - Math.min(sp, 0.9f) * 0.45f);
        if (handbrake && sp > 0.15f) turnK *= 1.75f;
        float turn = -steer * turnK * grip * Math.signum(speed);
        setYaw(getYaw() + turn);

        // занос: направление движения догоняет нос машины; ручник и слабое сцепление — дольше
        if (!player || !fourWheels || Float.isNaN(moveYaw) || sp < 0.06f) moveYaw = getYaw();
        else {
            float diff = MathHelper.wrapDegrees(getYaw() - moveYaw);
            float traction = handbrake ? 0.045f : MathHelper.clamp(0.55f * b.grip - sp * 0.12f, 0.2f, 0.75f);
            moveYaw += diff * traction;
            diff = MathHelper.wrapDegrees(getYaw() - moveYaw);
            if (Math.abs(diff) > 65) moveYaw = getYaw() - Math.signum(diff) * 65;
            speed -= speed * Math.min(Math.abs(diff), 60) / 60f * 0.018f; // боком машина теряет скорость
        }
        slip = MathHelper.wrapDegrees(getYaw() - moveYaw);

        double rad = Math.toRadians(moveYaw);
        Vec3d dir = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        Vec3d v = getVelocity();
        double vy = isOnGround() ? -0.04 : (v.y - 0.08) * 0.98;
        setVelocity(dir.x * speed, vy, dir.z * speed);
        move(MovementType.SELF, getVelocity());
        if (horizontalCollision && Math.abs(speed) > 0.1f) {
            if (Math.abs(speed) > 0.45f && !getWorld().isClient)
                getWorld().playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.NEUTRAL, 0.7f, 1.4f);
            speed *= 0.25f;
            moveYaw = getYaw();
        }
    }

    // только клиент: подвеска, коробка передач, занос (для модели, звука, спидометра и следов шин)
    public float suspY, prevSuspY;
    private float suspV, pitchV, rollV;
    /** Передача: −1 задний ход, 0 нейтраль, 1..N. Обороты 0..1. */
    public int gear;
    public float rpm, throttle;
    /** Визуальный занос (угол между носом и движением) и «шины визжат» — для следов и дыма. */
    public float slipVis;
    public boolean skidding;
    private double lastY = Double.NaN;

    private void clientAnimations() {
        prevWheelRot = wheelRot;
        prevVisualSteer = visualSteer;
        prevRoll = roll;
        prevPitchLean = pitchLean;
        prevSuspY = suspY;
        double rad = Math.toRadians(getYaw());
        double dx = getX() - prevX, dz = getZ() - prevZ;
        double along = dx * -Math.sin(rad) + dz * Math.cos(rad);
        double lateral = dx * -Math.cos(rad) + dz * -Math.sin(rad);
        float lastSpeed = clientSpeed;
        clientSpeed = (float) along;
        float accel = clientSpeed - lastSpeed;
        // тормозим — стопы ярко; едем назад — белые огни заднего хода
        boolean slowing = Math.abs(clientSpeed) < Math.abs(lastSpeed) - 0.004f && Math.abs(lastSpeed) > 0.03f;
        brakeGlow = slowing || braking ? 1 : Math.max(0, brakeGlow - 0.1f);
        reverseGlow = clientSpeed < -0.01f ? 1 : Math.max(0, reverseGlow - 0.2f);
        if ((light(LEFT) || light(RIGHT) || light(HAZARD)) && getWorld().getTime() % 8 == 0 && com.yompix.novograd.Novograd.CLIENT.player() != null && hasPassenger(com.yompix.novograd.Novograd.CLIENT.player()))
            getWorld().playSound(getX(), getY(), getZ(), net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sound.SoundCategory.NEUTRAL, 0.15f, (getWorld().getTime() / 8) % 2 == 0 ? 1.9f : 1.6f, false);
        float spd = Math.abs(clientSpeed);
        // занос со стороны: нос смотрит не туда, куда едет машина
        float slideDeg = Math.hypot(dx, dz) > 0.12 ? (float) Math.toDegrees(Math.atan2(lateral, Math.abs(along))) : 0;
        slipVis = MathHelper.lerp(0.5f, slipVis, slideDeg);
        wheelRot += (float) (along / (5.0 / 16.0));
        float yawRate = MathHelper.wrapDegrees(getYaw() - lastYaw);
        lastYaw = getYaw();
        float targetSteer = isLogicalSideForUpdatingMovement() ? steer : MathHelper.clamp(-yawRate / 4f, -1, 1);
        visualSteer = MathHelper.lerp(0.3f, visualSteer, targetSteer);

        // подвеска: пружина с затуханием — крен в повороте, клевок при торможении, приседание при разгоне, кочки
        if (isBike() || isScooter()) {
            // мотоцикл и скутер — прежние наклоны (BikeRenderer умножает крен сам)
            roll = MathHelper.lerp(0.12f, roll, MathHelper.clamp(yawRate * Math.abs(clientSpeed) * 0.8f, -2.5f, 2.5f));
            pitchLean = MathHelper.lerp(0.1f, pitchLean, MathHelper.clamp((clientSpeed - lastSpeed) * -30f, -1.5f, 1.5f));
        } else {
        boolean heavy = !isBike() && !isScooter() && (body() == CarBody.VAN || body() == CarBody.TRUCK || body() == CarBody.SUV || body() == CarBody.LIMO);
        float soft = heavy ? 1.35f : body() == CarBody.SPORTS ? 0.6f : 1f;
        float rollTarget = MathHelper.clamp(yawRate * spd * 1.1f * soft, -4.5f, 4.5f);
        rollV += (rollTarget - roll) * 0.22f;
        rollV *= 0.68f;
        roll += rollV;
        float pitchTarget = MathHelper.clamp(-accel * 55f * soft, -3.2f, 3.2f);
        pitchV += (pitchTarget - pitchLean) * 0.2f;
        pitchV *= 0.7f;
        pitchLean += pitchV;
        }
        if (!Double.isNaN(lastY)) {
            double dy = getY() - lastY;
            if (Math.abs(dy) > 0.04 && Math.abs(dy) < 1.3) suspV -= (float) dy * 2.6f; // въехали на бордюр / спрыгнули
        }
        lastY = getY();
        suspV += -suspY * 0.3f;
        suspV *= 0.72f;
        suspY = MathHelper.clamp(suspY + suspV, -3.5f, 3.5f);
        if (getWobble() > 0) suspV += (random.nextFloat() - 0.5f) * 0.4f;

        // коробка передач: передача и обороты по скорости относительно максималки
        float top = Math.max(0.2f, isScooter() ? 0.42f : 0.95f * (isBike() ? 1.3f : body().top) * (1 + engine() * 0.12f));
        int n = isScooter() ? 1 : isBike() ? 5 : body().gears;
        boolean driven = getControllingPassenger() != null || hasNpcDriver();
        throttle = MathHelper.lerp(0.25f, throttle, accel > 0.002f && clientSpeed > 0 ? 1 : 0);
        if (!driven) {
            gear = 0;
            rpm = MathHelper.lerp(0.1f, rpm, 0);
        } else if (clientSpeed < -0.01f) {
            gear = -1;
            rpm = MathHelper.lerp(0.3f, rpm, 0.2f + Math.min(1, spd / 0.28f) * 0.5f);
        } else if (spd < 0.012f) {
            gear = 0;
            rpm = MathHelper.lerp(0.2f, rpm, 0.12f + throttle * 0.3f);
        } else {
            float f = MathHelper.clamp(spd / top, 0, 1.2f);
            // короткие первые передачи, длинные последние
            float pos = (float) Math.pow(Math.min(f, 1f), 0.8f) * n;
            int g = MathHelper.clamp((int) pos + 1, 1, n);
            // чуть держим передачу при сбросе газа — без дребезга между передачами
            if (gear >= 1 && g == gear - 1 && pos > gear - 1 - 0.12f) g = gear;
            gear = g;
            float inGear = MathHelper.clamp(pos - (gear - 1), 0, 1.15f);
            float target = 0.22f + inGear * 0.72f + throttle * 0.06f;
            rpm = MathHelper.lerp(Math.abs(target - rpm) > 0.3f ? 0.25f : 0.4f, rpm, target);
        }

        // визг шин: занос, резкий тормоз на скорости, пробуксовка на старте у мощных машин
        boolean onGround = isOnGround() || getVelocity().y > -0.1;
        skidding = onGround && !isBike() && !isScooter() && !wrecked() && (
                (Math.abs(slipVis) > 11 && spd > 0.18f)
                        || (handbrake && spd > 0.15f)
                        || (accel < -0.035f && spd > 0.22f)
                        || (accel > 0.022f && spd < 0.35f && (body() == CarBody.MUSCLE || body() == CarBody.SPORTS || nitroOn())));
        if (skidding && (age % 3 == 0 || handbrake)) {
            for (int side = -1; side <= 1; side += 2) {
                double bx = getX() + Math.sin(rad) * 1.0 - Math.cos(rad) * 0.8 * side;
                double bz = getZ() - Math.cos(rad) * 1.0 - Math.sin(rad) * 0.8 * side;
                getWorld().addParticle(net.minecraft.particle.ParticleTypes.WHITE_SMOKE, bx, getY() + 0.1, bz, (random.nextDouble() - 0.5) * 0.03, 0.015, (random.nextDouble() - 0.5) * 0.03);
            }
        }
        // выхлоп
        if (getControllingPassenger() != null && age % (Math.abs(clientSpeed) > 0.2f ? 2 : 6) == 0) {
            double back = isBike() || isScooter() ? 1.0 : exhaustBack();
            double bx = getX() + Math.sin(rad) * back - Math.cos(rad) * 0.5;
            double bz = getZ() - Math.cos(rad) * back - Math.sin(rad) * 0.5;
            getWorld().addParticle(net.minecraft.particle.ParticleTypes.SMOKE, bx, getY() + 0.3, bz, Math.sin(rad) * 0.03, 0.02, -Math.cos(rad) * 0.03);
        }
    }

    /** Где выхлопная труба (блоков назад от центра) — у длинных кузовов дальше. */
    private double exhaustBack() {
        return switch (body()) {
            case LIMO -> 2.45;
            case TRUCK -> 2.2;
            case PICKUP -> 2.05;
            case VAN -> 1.85;
            case HATCH -> 1.5;
            default -> 1.75;
        };
    }

    private void pushOthers() {
        if (getWorld().isClient) return;
        for (Entity e : getWorld().getOtherEntities(this, getBoundingBox().expand(0.15, -0.01, 0.15), e -> e.isPushable() && !isConnectedThroughVehicle(e))) {
            pushAwayFrom(e);
        }
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
        if (!hasPassenger(passenger)) return;
        int i = seatOf(passenger);
        double[] seat = isScooter() ? new double[]{0, -0.15} : isBike() ? new double[]{0, i == 0 ? -0.1 : -0.62} : SEATS[Math.max(0, Math.min(i, SEATS.length - 1))];
        double rad = Math.toRadians(getYaw());
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -Math.cos(rad), rz = -Math.sin(rad);
        double x = getX() + fx * seat[1] + rx * seat[0];
        double z = getZ() + fz * seat[1] + rz * seat[0];
        float sc = passenger instanceof LivingEntity le ? le.getScale() : 1f;
        // бёдра ровно на подушке сиденья (с учётом роста); ребёнок сидит на бустере, чтобы видеть дорогу
        double y = getY() + (isScooter() ? 0.7 - 0.75 * sc : isBike() ? (i == 0 ? 0.86 : 0.95) - 0.75 * sc : 0.645 - 0.75 * sc + (1 - sc) * 0.35 - seatDrop());
        updater.accept(passenger, x, y, z);
        if (yawTick == age && YAW_DONE.getOrDefault(passenger, -1) != age) {
            YAW_DONE.put(passenger, age);
            passenger.setYaw(passenger.getYaw() + tickYawDelta);
            passenger.setHeadYaw(passenger.getHeadYaw() + tickYawDelta);
        }
        if (passenger instanceof LivingEntity le) le.setBodyYaw(getYaw());
    }

    /** Низкие кузова: сиденья ниже (спорткупе, маслкар), чтобы голова не торчала сквозь крышу. */
    private double seatDrop() {
        return switch (body()) {
            case SPORTS -> 2 / 16.0;
            case MUSCLE -> 1 / 16.0;
            default -> 0;
        };
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        double rad = Math.toRadians(getYaw());
        double side = seatOf(passenger) % 2 == 0 ? -1.8 : 1.8;
        Vec3d out = new Vec3d(getX() - Math.cos(rad) * side, getY(), getZ() - Math.sin(rad) * side);
        return getWorld().isSpaceEmpty(passenger, passenger.getDimensions(passenger.getPose()).getBoxAt(out)) ? out : super.updatePassengerForDismount(passenger);
    }

    @Override
    public void onPassengerLookAround(Entity passenger) {
        passenger.setBodyYaw(getYaw());
        float f = MathHelper.wrapDegrees(passenger.getYaw() - getYaw());
        float g = MathHelper.clamp(f, -120, 120);
        passenger.prevYaw += g - f;
        passenger.setYaw(passenger.getYaw() + g - f);
        passenger.setHeadYaw(passenger.getYaw());
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYaw = yaw;
        // сервер шлёт позицию каждый тик — догоняем ровно за 3 тика: постоянная маленькая задержка вместо рывков
        lerpTicks = 3;
    }

    @Override
    public double getLerpTargetX() {
        return lerpTicks > 0 ? lerpX : getX();
    }

    @Override
    public double getLerpTargetY() {
        return lerpTicks > 0 ? lerpY : getY();
    }

    @Override
    public double getLerpTargetZ() {
        return lerpTicks > 0 ? lerpZ : getZ();
    }

    @Override
    public float getLerpTargetYaw() {
        return lerpTicks > 0 ? (float) lerpYaw : getYaw();
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(isBike() ? com.yompix.novograd.vehicle.Vehicles.BIKE_ITEM : isScooter() ? ModContent.SCOOTER_ITEM : ModContent.CARS[Math.min(getVariant(), ModContent.CARS.length - 1)]);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        setVariant(nbt.getInt("Variant"));
        paint(nbt.getInt("Paint"));
        dataTracker.set(TUNE, nbt.getInt("Tune"));
        if (nbt.contains("Health")) dataTracker.set(HEALTH, nbt.getInt("Health"));
        ambient = nbt.getBoolean("Ambient");
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Variant", getVariant());
        nbt.putInt("Paint", dataTracker.get(PAINT));
        nbt.putInt("Tune", tune());
        nbt.putInt("Health", health());
        nbt.putBoolean("Ambient", ambient);
        if (owner != null) nbt.putUuid("Owner", owner);
    }

    @Override
    protected Text getDefaultName() {
        return Text.translatable(isBike() ? "entity.novograd.bike" : isScooter() ? "item.novograd.scooter" : "item.novograd.car_" + VARIANTS[getVariant()]);
    }

    public boolean isScooter() {
        return getType() == ModContent.SCOOTER;
    }

    /** Мотоцикл (vehicle/Vehicles): та же машина, двое седоков, быстрее разгон и выше скорость. */
    public boolean isBike() {
        return getType() == com.yompix.novograd.vehicle.Vehicles.BIKE;
    }

    /** Сервер не пересчитывает столкновения машины игрока — иначе на бордюрах её дёргает назад («moved wrongly»). */
    @Override
    public void move(MovementType type, Vec3d movement) {
        if (type == MovementType.PLAYER && !getWorld().isClient) {
            setPosition(getX() + movement.x, getY() + movement.y, getZ() + movement.z);
            return;
        }
        super.move(type, movement);
    }
}
