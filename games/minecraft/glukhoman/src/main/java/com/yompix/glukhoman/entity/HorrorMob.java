package com.yompix.glukhoman.entity;

import com.yompix.glukhoman.block.LampBlock;
import com.yompix.glukhoman.horror.Director;
import com.yompix.glukhoman.story.StoryState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Общее для существ: анимированная модель GeckoLib, «действие» (act) — особая анимация на N тиков
 * (появление, исчезание, атака, поворот головы на 180°, выглядывание, скример), гашение ламп рядом.
 */
public abstract class HorrorMob extends HostileEntity implements GeoEntity {
    private static final TrackedData<String> ACT = DataTracker.registerData(HorrorMob.class, TrackedDataHandlerRegistry.STRING);
    private final AnimatableInstanceCache geo = GeckoLibUtil.createInstanceCache(this);
    private static final Map<String, RawAnimation> RAW = new HashMap<>();
    private int actTicks;
    private boolean started;

    protected HorrorMob(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        setPersistent();
    }

    /** geo/<имя>.geo.json и animation.<имя>.* */
    public abstract String geoName();

    /** Обычная анимация по состоянию (вызывается на клиенте). */
    protected abstract String baseAnim();

    /** Анимация появления и её длина в тиках (0 — без). */
    protected String spawnAct() {
        return "spawn";
    }

    protected int spawnActTicks() {
        return 26;
    }

    /** Гасит ли лампы рядом. */
    protected boolean cutsLights() {
        return true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder b) {
        super.initDataTracker(b);
        b.add(ACT, "");
    }

    public String act() {
        return dataTracker.get(ACT);
    }

    /** Включить особую анимацию на ticks тиков (0 — пока не сменят). «vanish» по окончании убирает существо. */
    public void act(String name, int ticks) {
        dataTracker.set(ACT, name);
        actTicks = ticks;
    }

    public boolean vanishing() {
        return act().equals("vanish");
    }

    /** Плавно исчезнуть (анимация, потом удаление). */
    public void fade() {
        if (!vanishing()) act("vanish", 15);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld w)) return;
        if (!started) {
            started = true;
            if (spawnActTicks() > 0 && act().isEmpty()) act(spawnAct(), spawnActTicks());
        }
        if (actTicks > 0 && --actTicks == 0) {
            if (vanishing()) {
                discard();
                return;
            }
            dataTracker.set(ACT, "");
        }
        if (vanishing()) {
            getNavigation().stop();
            setVelocity(0, getVelocity().y, 0);
        }
        if (cutsLights() && age % 30 == 0) {
            StoryState st = StoryState.get(w);
            List<BlockPos> near = new java.util.ArrayList<>();
            for (var e : st.lamps.entrySet()) {
                if (e.getKey().equals("mast") || e.getKey().equals("photo") || e.getKey().equals("doll")) continue;
                for (long l : e.getValue()) {
                    BlockPos q = BlockPos.fromLong(l);
                    if (q.getSquaredDistance(getPos()) < 100 && w.getBlockState(q).getBlock() instanceof LampBlock && w.getBlockState(q).get(LampBlock.LIT)) near.add(q);
                }
            }
            if (!near.isEmpty()) Director.flicker(w, near, 70);
        }
    }

    // ---------- GeckoLib ----------
    static RawAnimation raw(String name) {
        return RAW.computeIfAbsent(name, n -> RawAnimation.begin().thenPlay(n));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            String a = act();
            if (a.isEmpty()) a = baseAnim();
            return state.setAndContinue(raw("animation." + animSet() + "." + a));
        }));
    }

    /** Набор анимаций (у людей — общий «human»). */
    protected String animSet() {
        return geoName();
    }

    /** Клиентские «куклы» кат-сцен: своё время анимации (в тиках) — для замедленной съёмки. */
    public double animClock = -1;

    @Override
    public double getTick(Object o) {
        return animClock >= 0 ? animClock : age;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geo;
    }

    @Override
    public boolean canImmediatelyDespawn(double d) {
        return false;
    }

    @Override
    protected boolean isDisallowedInPeaceful() {
        return false;
    }
}
