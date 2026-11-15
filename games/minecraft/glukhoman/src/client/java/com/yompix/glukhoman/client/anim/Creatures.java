package com.yompix.glukhoman.client.anim;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.entity.HorrorMob;
import com.yompix.glukhoman.entity.WatcherEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Поведение Обходчика на клиенте (0.5.1) — только анимация, механику решает сервер (FROZEN, скорость, телепорт):
 * <ul>
 *   <li>появился (новый на клиенте или «прыгнул» на новое место) — раскрывается из присяда (w_unfold);</li>
 *   <li>на старом месте остаётся «след» — копия, которая кадрами отступает в темноту (w_back) и пропадает;</li>
 *   <li>в объективе: если снимают вплотную (≤ 4.5 блока) — сначала медленно наклоняется к камере (w_lean), потом замирает;
 *       иначе замирает сразу — время аниматора и GeckoLib стоит, ни дыхания, ни покачивания;</li>
 *   <li>идёт: неровная походка с запинкой (w_walk); рядом с игроком — подкрадывается (w_creep);
 *       быстро — погоня рывками (w_chase); стоит — наблюдает (w_observe), голова кренится.</li>
 * </ul>
 */
public final class Creatures {
    private Creatures() {}

    record Ghost(Entity e, double t0) {}

    static final List<Ghost> GHOSTS = new ArrayList<>();
    static int nextId = -770000;

    public static String watcher(Animator a) {
        if (a.e.age < 3 && !a.overriding() && a.e.getId() >= 0) a.base("w_unfold", 2.4);
        if (a.onJump == null) a.onJump = () -> jumped(a);
        if (a.overriding()) return a.ovr;
        boolean frozen = a.e instanceof WatcherEntity w && w.frozen();
        if (frozen && !a.frozen) {
            var mc = MinecraftClient.getInstance();
            boolean close = mc.player != null && com.yompix.glukhoman.client.Viewfinder.up && mc.player.squaredDistanceTo(a.e) < 4.5 * 4.5 && !a.leaned;
            if (close && a.leanAt < 0) {
                a.leanAt = a.now();
                a.base("w_lean", 3.6);
                return "w_lean";
            }
            if (a.leanAt < 0 || a.now() - a.leanAt > 3.4) freeze(a, true);
        } else if (!frozen && a.frozen) {
            freeze(a, false);
            a.leanAt = -1;
        }
        if (a.frozen) return a.leanAt >= 0 ? "w_lean" : a.lastBase;
        double v = a.speed();
        String b;
        if (v > 3.0) b = "w_chase";
        else if (a.walking) {
            var p = a.e.getWorld().getClosestPlayer(a.e, 7);
            b = p != null ? "w_creep" : "w_walk";
        } else b = "w_observe";
        a.lastBase = b;
        return b;
    }

    /** Замереть намертво: и аниматор, и часы GeckoLib (поза держится ровно та же). */
    static void freeze(Animator a, boolean on) {
        a.frozen = on;
        if (a.e instanceof HorrorMob m && a.e.getId() >= 0) m.animClock = on ? a.e.age + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false) : -1;
        if (on) a.leaned = a.leanAt >= 0;
    }

    /** Прыгнул на новое место: тут раскрывается, там — «след» отступает в темноту. */
    static void jumped(Animator a) {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null || a.e.getId() < 0) return;
        a.base("w_unfold", 2.4);
        WatcherEntity g = GContent.WATCHER.create(mc.world);
        if (g == null) return;
        g.setId(nextId--);
        g.setNoGravity(true);
        g.noClip = true;
        g.refreshPositionAndAngles(a.jumpX, a.jumpY, a.jumpZ, a.e.getYaw(), 0);
        g.bodyYaw = g.prevBodyYaw = g.headYaw = g.prevHeadYaw = a.e.bodyYaw;
        mc.world.addEntity(g);
        Animator ga = Animator.of(g, Gestures.WATCHER);
        ga.base("w_back", 2.5);
        ga.autoLook = false;
        GHOSTS.add(new Ghost(g, mc.world.getTime()));
    }

    static final List<Object[]> LATER = new ArrayList<>();

    /** Отложенное действие на клиенте (через n тиков). */
    public static void later(int ticks, Runnable r) {
        LATER.add(new Object[]{ticks, r});
    }

    /** Из клиентского тика: «след» отступает назад и через 2.5 с исчезает. */
    public static void tick() {
        var mc = MinecraftClient.getInstance();
        LATER.removeIf(o -> {
            int n = (Integer) o[0] - 1;
            o[0] = n;
            if (n > 0) return false;
            ((Runnable) o[1]).run();
            return true;
        });
        if (mc.world == null) {
            GHOSTS.clear();
            return;
        }
        GHOSTS.removeIf(g -> {
            double t = (mc.world.getTime() - g.t0) / 20.0;
            if (t > 2.5 || g.e.isRemoved()) {
                mc.world.removeEntity(g.e.getId(), Entity.RemovalReason.DISCARDED);
                return true;
            }
            if ((int) (t * 4) != (int) ((t - 0.05) * 4)) {   // шаг назад рывком, 4 раза в секунду
                var back = net.minecraft.util.math.Vec3d.fromPolar(0, g.e.getYaw()).multiply(-0.22);
                g.e.setPosition(g.e.getX() + back.x, g.e.getY(), g.e.getZ() + back.z);
            }
            return false;
        });
    }
}
