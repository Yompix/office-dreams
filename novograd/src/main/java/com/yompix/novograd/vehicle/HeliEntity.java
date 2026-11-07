package com.yompix.novograd.vehicle;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Вертолёт. W/S — вперёд/назад, A/D — разворот, Пробел — вверх, Shift — вниз (в воздухе Shift не высаживает).
 * Винт раскручивается пару секунд, без винта вертолёт падает; при нуле здоровья взрывается и падает горящим.
 * Вариант 6 (CarEntity.POLICE) — полицейский: пилот и стрелок-NPC, прожектор, кружит над целью.
 */
public class HeliEntity extends MotorVehicle {
    public static final int[] COLORS = {0x1F2A44, 0xD7262C, 0xF2F4F7, 0x2A2C31, 0xF2C032, 0x2C6ECB, 0x101215};
    private static final double[][] SEATS = {{-0.45, 0.55, 0.78}, {0.45, 0.55, 0.78}, {-0.45, -0.45, 0.78}, {0.45, -0.45, 0.78}};
    private static final DustParticleEffect BEAM = new DustParticleEffect(new Vector3f(1f, 0.97f, 0.8f), 1.6f);

    /** Обороты винта 0..1 (считаются на каждой стороне по тому, есть ли пилот). */
    public float rotor, prevRotorAngle, rotorAngle;
    private double vx, vz;

    /** Автопилот, заданный снаружи (вертолёт «Новоград-24»); только сервер, не сохраняется. */
    @Nullable public java.util.function.Consumer<HeliEntity> ai;

    // полиция
    @Nullable private UUID targetId;
    private int leaveTicks, shootCd = 40;
    private float orbit;

    public HeliEntity(EntityType<? extends HeliEntity> type, World world) {
        super(type, world);
    }

    public int bodyColor() {
        int p = color();
        return p != CarEntity.COLORS[getVariant()] ? p : COLORS[getVariant()];
    }

    @Override
    protected double[][] seats() {
        return SEATS;
    }

    @Override
    protected boolean flying() {
        return true;
    }

    @Override
    public float getStepHeight() {
        return 0.6f;
    }

    @Override
    protected double dismountSide() {
        return 2.2;
    }

    @Override
    public boolean engineRunning() {
        return !wrecked() && (getControllingPassenger() != null || hasNpcDriver());
    }

    @Override
    public void tick() {
        rotor = MathHelper.clamp(rotor + (engineRunning() && !isSubmergedInWater() ? 0.012f : -0.008f), 0, 1);
        prevRotorAngle = rotorAngle;
        rotorAngle += rotor * 62f;
        if (rotorAngle > 3600) {
            rotorAngle -= 3600;
            prevRotorAngle -= 3600;
        }
        super.tick();
    }

    @Override
    protected void physics(float fwd, float side, boolean up, boolean down) {
        if (!getWorld().isClient && ai != null && hasNpcDriver()) { // вертолёт телеканала (gta/NewsHeli)
            ai.accept(this);
            return;
        }
        if (!getWorld().isClient && isPolice() && hasNpcDriver()) {
            policeFlight();
            return;
        }
        boolean lift = rotor > 0.85f && !wrecked() && !isSubmergedInWater();
        double vy = getVelocity().y;
        Vec3d dir = forward();
        boolean ground = isOnGround();
        if (lift) {
            double acc = 0.042 * (1 + engine() * 0.1);
            if (getControllingPassenger() instanceof net.minecraft.entity.player.PlayerEntity pl) acc *= 0.85 + 0.15 * com.yompix.novograd.gta.Skills.flyMul(pl); // опытный пилот разгоняется резвее
            double drag = ground && !up ? 0.6 : 0.965;
            vx = vx * drag + dir.x * fwd * acc * (fwd < 0 ? 0.5 : 1);
            vz = vz * drag + dir.z * fwd * acc * (fwd < 0 ? 0.5 : 1);
            double want = up ? 0.34 : down ? -0.3 : 0;
            vy += (want - vy) * 0.12;
            steer = MathHelper.lerp(0.18f, steer, side);
            setYaw(getYaw() - steer * 3.4f * (getControllingPassenger() instanceof net.minecraft.entity.player.PlayerEntity pl ? com.yompix.novograd.gta.Skills.flyMul(pl) : 1)); // навык полёта (SA)
        } else {
            // винт не держит: падаем, разбитый — ещё и крутимся
            vx *= ground ? 0.5 : 0.99;
            vz *= ground ? 0.5 : 0.99;
            double fall = rotor * 0.06; // подкрученный винт чуть тормозит падение
            vy = ground ? -0.04 : Math.max(vy - 0.07 + fall, -1.8);
            if (wrecked() && !ground) setYaw(getYaw() + 14);
        }
        setVelocity(vx, vy, vz);
        move(MovementType.SELF, getVelocity());
        if (horizontalCollision) {
            vx *= 0.2;
            vz *= 0.2;
        }
        speed = (float) (vx * dir.x + vz * dir.z);
    }

    // ---------------------------------------------------------------- полиция
    /**
     * Полицейский вертолёт для погони (4+ звезды): появляется в стороне от точки near на высоте, кружит над целью,
     * светит прожектором, стрелок бьёт очередями (при 3+ звёздах цели). Улетает и исчезает, когда звёзд нет.
     */
    public static HeliEntity spawnPolice(ServerWorld w, Vec3d near, ServerPlayerEntity target) {
        double a = w.random.nextDouble() * Math.PI * 2;
        double x = near.x + Math.cos(a) * 45, z = near.z + Math.sin(a) * 45;
        int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
        double y = Math.max(near.y + 22, top + 12);
        HeliEntity h = new HeliEntity(Vehicles.HELI, w);
        h.setVariant(CarEntity.POLICE);
        h.refreshPositionAndAngles(x, y, z, (float) Math.toDegrees(Math.atan2(-(near.x - x), near.z - z)), 0);
        h.rotor = 1;
        h.targetId = target.getUuid();
        h.orbit = (float) a;
        w.spawnEntity(h);
        CitizenEntity pilot = cop(w, h, false);
        CitizenEntity gunner = cop(w, h, true);
        pilot.startRiding(h, true);
        gunner.startRiding(h, true);
        w.playSound(null, h.getBlockPos(), ModContent.SIREN, SoundCategory.NEUTRAL, 1.6f, 0.9f);
        return h;
    }

    /** Сколько полицейских вертолётов в радиусе r. */
    public static int countPolice(ServerWorld w, Vec3d at, double r) {
        return w.getEntitiesByClass(HeliEntity.class, new net.minecraft.util.math.Box(at, at).expand(r), h -> h.isPolice() && !h.wrecked()).size();
    }

    /** Сменить цель (или null — улететь). */
    public void setTarget(@Nullable ServerPlayerEntity p) {
        targetId = p == null ? null : p.getUuid();
    }

    private static CitizenEntity cop(ServerWorld w, HeliEntity h, boolean gunner) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        c.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.POLICE, false, gunner ? "Стрелок вертолёта" : "Пилот");
        c.refreshPositionAndAngles(h.getX(), h.getY(), h.getZ(), 0, 0);
        if (gunner) c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(ModContent.SMG));
        w.spawnEntity(c);
        return c;
    }

    @Nullable
    private ServerPlayerEntity target() {
        if (targetId == null || !(getWorld() instanceof ServerWorld w)) return null;
        var p = w.getServer().getPlayerManager().getPlayer(targetId);
        if (p == null || !p.isAlive() || p.isSpectator() || p.getWorld() != getWorld() || p.squaredDistanceTo(this) > 220 * 220
                || com.yompix.novograd.gta.Wanted.stars(p) == 0) return null;
        return p;
    }

    @Nullable
    private CitizenEntity gunner() {
        for (Entity e : getPassengerList()) if (e instanceof CitizenEntity c && seatOf(c) > 0) return c;
        return null;
    }

    private void policeFlight() {
        ServerWorld w = (ServerWorld) getWorld();
        ServerPlayerEntity t = target();
        Vec3d want;
        double wantY;
        if (t == null) {
            // цели нет — уходим вверх и в сторону, потом исчезаем
            if (++leaveTicks > 200) {
                for (Entity e : getPassengerList()) e.discard();
                discard();
                return;
            }
            Vec3d d = forward();
            want = getPos().add(d.multiply(20));
            wantY = getY() + 6;
        } else {
            leaveTicks = 0;
            orbit += 0.012f;
            double r = 15;
            Vec3d focus = com.yompix.novograd.gta.Pursuit.heliFocus(t); // потеряли из виду — кружим над зоной поиска
            want = focus.add(Math.cos(orbit) * r, 0, Math.sin(orbit) * r);
            int topHere = w.getTopY(Heightmap.Type.MOTION_BLOCKING, getBlockX(), getBlockZ());
            int topThere = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(want.x), (int) Math.floor(want.z));
            wantY = Math.max(t.getY() + 15, Math.max(topHere, topThere) + 7);
        }
        double dx = want.x - getX(), dz = want.z - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double sp = Math.min(1.0, dist * 0.08);
        double tx = dist > 0.01 ? dx / dist * sp : 0, tz = dist > 0.01 ? dz / dist * sp : 0;
        vx = MathHelper.lerp(0.08, vx, tx);
        vz = MathHelper.lerp(0.08, vz, tz);
        double vy = MathHelper.clamp((wantY - getY()) * 0.06, -0.35, 0.35);
        // нос — на цель (или по ходу, если улетаем)
        double lx = t != null ? t.getX() - getX() : vx, lz = t != null ? t.getZ() - getZ() : vz;
        float yawWant = (float) Math.toDegrees(Math.atan2(-lx, lz));
        setYaw(getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(yawWant - getYaw()), -4, 4));
        setVelocity(vx, vy, vz);
        move(MovementType.SELF, getVelocity());
        if (horizontalCollision) setVelocity(getVelocity().x, 0.4, getVelocity().z);
        speed = (float) (vx * forward().x + vz * forward().z);
        if (t != null) {
            if (com.yompix.novograd.gta.Pursuit.lost(t)) com.yompix.novograd.gta.Pursuit.sweep(w, this, age); // прожектор шарит по району
            else searchlight(w, t);
        }
    }

    private void searchlight(ServerWorld w, ServerPlayerEntity t) {
        Vec3d from = new Vec3d(getX(), getY() - 0.2, getZ());
        Vec3d to = t.getPos();
        if (age % 2 == 0) {
            Vec3d d = to.subtract(from);
            double len = d.length();
            Vec3d n = d.normalize();
            for (double s = 1.5; s < len; s += 1.3) {
                Vec3d p = from.add(n.multiply(s));
                double spread = s / len * 0.6;
                w.spawnParticles(BEAM, p.x, p.y, p.z, 1, spread, spread, spread, 0);
            }
            for (int i = 0; i < 12; i++) {
                double a = i / 12.0 * Math.PI * 2 + age * 0.05;
                w.spawnParticles(BEAM, to.x + Math.cos(a) * 2.2, to.y + 0.1, to.z + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
            }
            w.spawnParticles(ParticleTypes.END_ROD, to.x, to.y + 0.2, to.z, 1, 0.8, 0, 0.8, 0.0);
        }
        if (age % 48 == 0) w.playSound(null, getBlockPos(), ModContent.SIREN, SoundCategory.NEUTRAL, 1.6f, 0.9f);
        // стрелок: очередь по цели, если видит её
        CitizenEntity g = gunner();
        if (g == null || --shootCd > 0 || com.yompix.novograd.gta.Wanted.stars(t) < 3 || t.isCreative()) return;
        Vec3d eye = g.getEyePos();
        double d = eye.distanceTo(t.getEyePos());
        if (d > 70) return;
        var hit = w.raycast(new RaycastContext(eye, t.getEyePos(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, g));
        if (hit.getType() != HitResult.Type.MISS && hit.getPos().squaredDistanceTo(t.getEyePos()) > 2) {
            shootCd = 10;
            return;
        }
        burst = 4;
        shootCd = 45 + random.nextInt(30);
    }

    private int burst;

    @Override
    protected void serverTick() {
        if (burst > 0 && age % 3 == 0 && getWorld() instanceof ServerWorld w) {
            burst--;
            ServerPlayerEntity t = target();
            CitizenEntity g = gunner();
            if (t != null && g != null) {
                Vec3d eye = g.getEyePos();
                double d = eye.distanceTo(t.getEyePos());
                com.yompix.novograd.gta.Weapons.shoot(w, g, eye, t.getEyePos().subtract(eye), 3f, 0.05 + d * 0.0025, 80, true);
            }
        }
        // полицейский без экипажа (сбили стрелков) — просто падает
    }

    // ---------------------------------------------------------------- эффекты и звук
    @Override
    protected void clientEffects() {
        damageEffects(1.6);
        if (rotor < 0.35f) return;
        var w = getWorld();
        // ветер от винта: пыль, листья, брызги с земли под вертолётом
        BlockPos at = getBlockPos();
        for (int dy = 0; dy < 12; dy++) {
            BlockPos b = at.down(dy);
            var st = w.getBlockState(b);
            boolean water = !w.getFluidState(b).isEmpty();
            if (st.isAir() && !water) continue;
            double y = b.getY() + (water ? 0.9 : 1.05);
            float power = rotor * (1 - dy / 12f);
            for (int i = 0; i < (int) (power * 4) + 1; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 1.5 + random.nextDouble() * 3;
                double px = getX() + Math.cos(a) * r, pz = getZ() + Math.sin(a) * r;
                double ox = Math.cos(a) * 0.35 * power, oz = Math.sin(a) * 0.35 * power;
                if (water) w.addParticle(ParticleTypes.SPLASH, px, y, pz, ox, 0.2, oz);
                else if (random.nextInt(3) == 0) w.addParticle(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), px, y, pz, ox, 0.1, oz);
                else w.addParticle(ParticleTypes.POOF, px, y, pz, ox * 0.6, 0.02, oz * 0.6);
            }
            break;
        }
    }

    @Override
    public SoundEvent engineSound() {
        return Vehicles.HELI_ROTOR;
    }

    @Override
    public float soundPitch() {
        return 0.55f + rotor * 0.45f + Math.abs(clientSpeed) * 0.1f;
    }

    @Override
    public float soundVolume() {
        return rotor * 2.6f; // громче 1 — слышно дальше (радиус ~40 блоков)
    }

    @Override
    protected ItemStack pickStack() {
        return new ItemStack(Vehicles.HELI_ITEM);
    }

    @Override
    protected String nameKey() {
        return isPolice() ? "entity.novograd.police_helicopter" : "entity.novograd.helicopter";
    }

    @Override
    protected String controlsHint() {
        return "Вертолёт: Пробел — вверх, Shift — вниз, W/S — вперёд/назад, A/D — разворот";
    }

    @Override
    protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        rotor = nbt.getFloat("Rotor");
    }

    @Override
    protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putFloat("Rotor", rotor);
    }
}
