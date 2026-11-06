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

    public static float roll(float delta) {
        if (Studio.active()) return Studio.roll(delta);
        return cutscene ? roll : 0;
    }

    public static double fov(double vanilla, float delta) {
        if (Studio.active()) return Studio.fov(vanilla, delta);
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
