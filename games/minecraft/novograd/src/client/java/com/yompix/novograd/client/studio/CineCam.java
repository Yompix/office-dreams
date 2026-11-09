package com.yompix.novograd.client.studio;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.mixin.GameRendererAccessor;
import net.minecraft.client.MinecraftClient;

/**
 * Общая «операторская» часть для кат-сцен сюжета: наклон камеры, зум и кинофильтр.
 * Киностудия (F7) управляет тем же самым сама — тут то, что задаёт режиссёр кат-сцены.
 */
public final class CineCam {
    private CineCam() {}

    /** Кат-сцена идёт и управляет камерой. */
    public static boolean cutscene;
    public static float roll, fov = Float.NaN;
    private static String applied = null;

    /** «Потрачено» как в GTA: чёрно-белый кадр, медленный наезд и крен камеры на несколько секунд. */
    private static long fxStart, fxUntil;
    private static boolean fxFilter;

    public static void wasted(MinecraftClient mc) {
        fxStart = System.currentTimeMillis();
        fxUntil = fxStart + 4800;
        if (!cutscene) {
            filter(mc, "film_noir");
            fxFilter = true;
        }
        if (mc.player != null) {
            mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f); // глухой «провал» звука
            mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.8f);
        }
    }

    private static float fx() {
        long now = System.currentTimeMillis();
        if (now >= fxUntil) return -1;
        float k = Math.min(1f, (now - fxStart) / 2500f);
        return k * k * (3 - 2 * k);
    }

    /** Каждый кадр: эффект кончился — вернуть обычную картинку. */
    public static void tickFx(MinecraftClient mc) {
        if (fxFilter && System.currentTimeMillis() >= fxUntil) {
            fxFilter = false;
            if (!cutscene) filter(mc, null);
        }
    }

    public static float roll(float delta) {
        if (Studio.active()) return Studio.roll(delta);
        float f = fx();
        if (f >= 0 && !cutscene) return 3.5f * f;
        return cutscene ? roll : 0;
    }

    public static double fov(double vanilla, float delta) {
        if (Studio.active()) return Studio.fov(vanilla, delta);
        float f = fx();
        if (f >= 0 && !cutscene) return vanilla * (1 - 0.22 * f);
        return cutscene && !Float.isNaN(fov) ? fov : vanilla;
    }

    /** Кинофильтр кат-сцены (имя файла shaders/post/<name>.json) или null — выключить. */
    public static void filter(MinecraftClient mc, String name) {
        var acc = (GameRendererAccessor) mc.gameRenderer;
        boolean lost = name != null && mc.gameRenderer.getPostProcessor() == null;
        if (java.util.Objects.equals(name, applied) && !lost) return;
        applied = name;
        if (name == null || name.isEmpty()) acc.novograd$disablePost();
        else acc.novograd$loadPost(Novograd.id("shaders/post/" + name + ".json"));
    }

    public static void reset(MinecraftClient mc) {
        cutscene = false;
        roll = 0;
        fov = Float.NaN;
        if (applied != null) filter(mc, null);
    }
}
