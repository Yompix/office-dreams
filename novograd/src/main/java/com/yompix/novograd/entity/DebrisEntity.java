package com.yompix.novograd.entity;

import com.yompix.novograd.world.Destruction;
import com.yompix.novograd.world.Props;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Обломок уличного предмета: урна, конус, скамейка, кусок забора летят и кувыркаются после удара машиной или взрыва;
 * сбитый фонарный столб медленно заваливается и лежит на дороге. Живёт недолго и не сохраняется в мир
 * (сам предмет вернётся на место через {@link Destruction}).
 */
public class DebrisEntity extends Entity {
    private static final TrackedData<BlockState> STATE = DataTracker.registerData(DebrisEntity.class, TrackedDataHandlerRegistry.BLOCK_STATE);
    private static final TrackedData<BlockState> TOP = DataTracker.registerData(DebrisEntity.class, TrackedDataHandlerRegistry.BLOCK_STATE);
    private static final TrackedData<Integer> LEN = DataTracker.registerData(DebrisEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LIFE = DataTracker.registerData(DebrisEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> SCALE = DataTracker.registerData(DebrisEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** Падение столба: тиков до удара о землю. */
    public static final int FALL = 16;

    public float rot, prevRot, spin;

    public DebrisEntity(EntityType<? extends DebrisEntity> type, World world) {
        super(type, world);
        spin = 18 + (getId() % 7) * 4;
    }

    /** Кувыркающийся обломок. */
    public static DebrisEntity tumble(ServerWorld w, BlockState state, Vec3d pos, Vec3d vel, float scale, int life) {
        DebrisEntity d = new DebrisEntity(Props.DEBRIS, w);
        d.dataTracker.set(STATE, state);
        d.dataTracker.set(TOP, state);
        d.dataTracker.set(LEN, 0);
        d.dataTracker.set(SCALE, scale);
        d.dataTracker.set(LIFE, life);
        d.refreshPositionAndAngles(pos.x, pos.y, pos.z, w.random.nextFloat() * 360, 0);
        d.setVelocity(vel);
        w.spawnEntity(d);
        return d;
    }

    /** Падающий столб высотой len блоков (верхний — top) с основанием в base, падает в сторону yaw. */
    public static DebrisEntity pole(ServerWorld w, BlockState pole, BlockState top, int len, Vec3d base, float yaw, int life) {
        DebrisEntity d = new DebrisEntity(Props.DEBRIS, w);
        d.dataTracker.set(STATE, pole);
        d.dataTracker.set(TOP, top);
        d.dataTracker.set(LEN, len);
        d.dataTracker.set(SCALE, 1f);
        d.dataTracker.set(LIFE, life);
        d.refreshPositionAndAngles(base.x, base.y, base.z, yaw, 0);
        d.setNoGravity(true);
        w.spawnEntity(d);
        return d;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        b.add(STATE, Blocks.COMPOSTER.getDefaultState());
        b.add(TOP, Blocks.COMPOSTER.getDefaultState());
        b.add(LEN, 0);
        b.add(LIFE, 300);
        b.add(SCALE, 1f);
    }

    public BlockState state() {
        return dataTracker.get(STATE);
    }

    public BlockState top() {
        return dataTracker.get(TOP);
    }

    /** 0 — кувыркающийся обломок, иначе высота столба. */
    public int poleLength() {
        return dataTracker.get(LEN);
    }

    public float scale() {
        return dataTracker.get(SCALE);
    }

    public int life() {
        return dataTracker.get(LIFE);
    }

    /** Угол завала столба (0 — стоит, 90 — лежит) с подпрыгиванием после удара. */
    public float poleAngle(float tickDelta) {
        float t = age + tickDelta;
        if (t < FALL) {
            float k = t / FALL;
            return 90 * k * k;
        }
        float b = t - FALL;
        return b < 8 ? 90 - 7 * MathHelper.sin(b / 8 * MathHelper.PI) : 90;
    }

    @Override
    public void tick() {
        super.tick();
        prevRot = rot;
        if (poleLength() > 0) {
            if (age == FALL && getWorld() instanceof ServerWorld w) Destruction.poleLanded(w, this);
        } else {
            Vec3d v = getVelocity();
            v = v.add(0, -0.06, 0);
            setVelocity(v);
            move(MovementType.SELF, getVelocity());
            v = getVelocity();
            if (isOnGround()) {
                double vy = v.y;
                setVelocity(v.x * 0.55, Math.abs(vy) > 0.12 ? -vy * 0.3 : 0, v.z * 0.55);
                spin *= 0.6f;
            } else setVelocity(v.multiply(0.98));
            if (horizontalCollision) setVelocity(-v.x * 0.4, v.y, -v.z * 0.4);
            rot += spin;
        }
        if (!getWorld().isClient && (age > life() || getY() < getWorld().getBottomY())) discard();
    }

    @Override
    public boolean shouldSave() {
        return false; // предмет вернёт на место Destruction, обломки в мире не копятся
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}
}
