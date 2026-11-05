package com.yompix.lifecraft.entity;

import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.ModContent;
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
    public static final String[] VARIANTS = {"red", "blue", "yellow", "black", "white", "taxi"};
    public static final int[] COLORS = {0xD7262C, 0x2C6ECB, 0xF2C032, 0x2A2C31, 0xE8E8EA, 0xF7C600};
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
        return getPassengerList().size() < (isScooter() ? 1 : SEATS.length);
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
        boolean driver = getPassengerList().isEmpty();
        if (driver && !isScooter() && !player.isCreative() && player instanceof ServerPlayerEntity sp) {
            var d = com.yompix.lifecraft.life.Life.get(sp);
            boolean young = d.age > 0 ? d.age < 18 : d.edu <= com.yompix.lifecraft.life.LifeData.SCHOOL_END;
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
        // поворот машины передаём пассажирам в updatePassengerPosition — после их тика,
        // тогда камера поворачивается плавно между кадрами, а не ступеньками раз в тик
        tickYawDelta = MathHelper.wrapDegrees(getYaw() - yawBefore);
        if (!getWorld().isClient && route == null && (light(LEFT) || light(RIGHT)) && Math.abs(MathHelper.wrapDegrees(getYaw() - signalYaw)) > 70
                && Math.abs(tickYawDelta) < 0.5f) dataTracker.set(LIGHTS, lights() & ~(LEFT | RIGHT));
        yawTick = age;
        if (getWorld().isClient) {
            clientAnimations();
            LifeCraft.CLIENT.carTick(this);
        }
        checkBlockCollision();
        pushOthers();
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
            if (Boolean.getBoolean("lifecraft.debugcar") && age % 40 == 0)
                LifeCraft.LOG.info("car {} pos {} target#{} {} speed {} hc {} ground {}", getId(), getPos(), routeIndex, t, speed, horizontalCollision, isOnGround());
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
                if (Boolean.getBoolean("lifecraft.debugcar") && age % 20 == 0)
                    LifeCraft.LOG.info("car {} ob {} blocked {} dodge {} rev {} speed {}", getId(), ob, blocked, dodge, reverse, speed);
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
        float max = isTouchingWater() ? 0.15f : isScooter() ? 0.42f : route != null ? autoMax + 0.05f : 0.95f;
        if (fwd > 0) {
            speed += speed < 0 ? 0.05f : 0.018f * (1 - speed / max * 0.6f);
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
        steer = MathHelper.lerp(0.25f, steer, side);
        float grip = MathHelper.clamp(Math.abs(speed) * 6, 0, 1);
        float turn = -steer * 4.2f * grip * Math.signum(speed) * (1 - Math.min(Math.abs(speed), 0.9f) * 0.45f);
        setYaw(getYaw() + turn);

        double rad = Math.toRadians(getYaw());
        Vec3d dir = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        Vec3d v = getVelocity();
        double vy = isOnGround() ? -0.04 : (v.y - 0.08) * 0.98;
        setVelocity(dir.x * speed, vy, dir.z * speed);
        move(MovementType.SELF, getVelocity());
        if (horizontalCollision && Math.abs(speed) > 0.1f) {
            if (Math.abs(speed) > 0.45f && !getWorld().isClient)
                getWorld().playSound(null, getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.NEUTRAL, 0.7f, 1.4f);
            speed *= 0.25f;
        }
    }

    private void clientAnimations() {
        prevWheelRot = wheelRot;
        prevVisualSteer = visualSteer;
        prevRoll = roll;
        prevPitchLean = pitchLean;
        double rad = Math.toRadians(getYaw());
        double dx = getX() - prevX, dz = getZ() - prevZ;
        double along = dx * -Math.sin(rad) + dz * Math.cos(rad);
        float lastSpeed = clientSpeed;
        clientSpeed = (float) along;
        // тормозим — стопы ярко; едем назад — белые огни заднего хода
        boolean slowing = Math.abs(clientSpeed) < Math.abs(lastSpeed) - 0.004f && Math.abs(lastSpeed) > 0.03f;
        brakeGlow = slowing || braking ? 1 : Math.max(0, brakeGlow - 0.1f);
        reverseGlow = clientSpeed < -0.01f ? 1 : Math.max(0, reverseGlow - 0.2f);
        if ((light(LEFT) || light(RIGHT) || light(HAZARD)) && getWorld().getTime() % 8 == 0 && com.yompix.lifecraft.LifeCraft.CLIENT.player() != null && hasPassenger(com.yompix.lifecraft.LifeCraft.CLIENT.player()))
            getWorld().playSound(getX(), getY(), getZ(), net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), net.minecraft.sound.SoundCategory.NEUTRAL, 0.15f, (getWorld().getTime() / 8) % 2 == 0 ? 1.9f : 1.6f, false);
        wheelRot += (float) (along / (5.0 / 16.0));
        float yawRate = MathHelper.wrapDegrees(getYaw() - lastYaw);
        lastYaw = getYaw();
        float targetSteer = isLogicalSideForUpdatingMovement() ? steer : MathHelper.clamp(-yawRate / 4f, -1, 1);
        visualSteer = MathHelper.lerp(0.3f, visualSteer, targetSteer);
        roll = MathHelper.lerp(0.12f, roll, MathHelper.clamp(yawRate * Math.abs(clientSpeed) * 0.8f, -2.5f, 2.5f));
        pitchLean = MathHelper.lerp(0.1f, pitchLean, MathHelper.clamp((clientSpeed - lastSpeed) * -30f, -1.5f, 1.5f));
        // выхлоп
        if (getControllingPassenger() != null && age % (Math.abs(clientSpeed) > 0.2f ? 2 : 6) == 0) {
            double bx = getX() + Math.sin(rad) * 1.75 - Math.cos(rad) * 0.5;
            double bz = getZ() - Math.cos(rad) * 1.75 - Math.sin(rad) * 0.5;
            getWorld().addParticle(net.minecraft.particle.ParticleTypes.SMOKE, bx, getY() + 0.3, bz, Math.sin(rad) * 0.03, 0.02, -Math.cos(rad) * 0.03);
        }
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
        double[] seat = isScooter() ? new double[]{0, -0.15} : SEATS[Math.max(0, Math.min(i, SEATS.length - 1))];
        double rad = Math.toRadians(getYaw());
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -Math.cos(rad), rz = -Math.sin(rad);
        double x = getX() + fx * seat[1] + rx * seat[0];
        double z = getZ() + fz * seat[1] + rz * seat[0];
        float sc = passenger instanceof LivingEntity le ? le.getScale() : 1f;
        // бёдра ровно на подушке сиденья (с учётом роста); ребёнок сидит на бустере, чтобы видеть дорогу
        double y = getY() + (isScooter() ? 0.7 - 0.75 * sc : 0.645 - 0.75 * sc + (1 - sc) * 0.35);
        updater.accept(passenger, x, y, z);
        if (yawTick == age && YAW_DONE.getOrDefault(passenger, -1) != age) {
            YAW_DONE.put(passenger, age);
            passenger.setYaw(passenger.getYaw() + tickYawDelta);
            passenger.setHeadYaw(passenger.getHeadYaw() + tickYawDelta);
        }
        if (passenger instanceof LivingEntity le) le.setBodyYaw(getYaw());
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
        return new ItemStack(isScooter() ? ModContent.SCOOTER_ITEM : ModContent.CARS[getVariant()]);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        setVariant(nbt.getInt("Variant"));
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Variant", getVariant());
        if (owner != null) nbt.putUuid("Owner", owner);
    }

    @Override
    protected Text getDefaultName() {
        return Text.translatable(isScooter() ? "item.lifecraft.scooter" : "item.lifecraft.car_" + VARIANTS[getVariant()]);
    }

    public boolean isScooter() {
        return getType() == ModContent.SCOOTER;
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
