package com.yompix.glukhoman.client;

import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.entity.HorrorMob;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.math.Box;

/**
 * Пост-обработка страха: зерно плёнки, виньетка, хроматическая аберрация и обесцвечивание от страха,
 * волны искажения рядом с существом, а в кат-сценах — размытие фона, лучи света, цветокоррекция, разрыв неба.
 */
public final class FearFx {
    private FearFx() {}

    static PostEffectProcessor fx;
    static boolean failed;
    static int w, h;
    static float near;

    public static void render(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int level = GConfig.get().screenFx;
        if (!GlukhomanClient.inWorld || level == 0 || failed || mc.world == null || mc.player == null) return;
        if (Cine.active && Cine.noFx) return;   // съёмка панорамы меню: чистые грани куба
        try {
            if (fx == null) {
                fx = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), Glukhoman.id("shaders/post/fear.json"));
                w = h = -1;
            }
            int fw = mc.getWindow().getFramebufferWidth(), fh = mc.getWindow().getFramebufferHeight();
            if (fw != w || fh != h) {
                fx.setupDimensions(fw, fh);
                w = fw;
                h = fh;
            }
            if (mc.world.getTime() % 5 == 0) {
                double best = 99;
                for (var e : mc.world.getEntitiesByClass(HorrorMob.class, new Box(mc.player.getBlockPos()).expand(18), x -> true))
                    best = Math.min(best, e.distanceTo(mc.player));
                float target = best > 18 ? 0 : (float) (1 - best / 18);
                near += (target - near) * 0.5f;
            }
            float str = level == 1 ? 0.55f : 1f;
            boolean calm = GConfig.get().noFlashing;
            float fear = Cine.active ? 0.25f : 1 - GlukhomanClient.shownSanity / 100f;
            fx.setUniforms("Time", (mc.world.getTime() % 2000 + tickDelta) / 20f);
            fx.setUniforms("Fear", fear);
            fx.setUniforms("Near", Math.max(near, Cine.active ? Cine.warp : 0) * (calm ? 0.4f : 1f));
            fx.setUniforms("Strength", str);
            fx.setUniforms("Flash", calm ? 0 : Cine.flash);
            fx.setUniforms("Blur", Cine.active && GConfig.get().fxBlur ? Cine.blur : 0);
            fx.setUniforms("Rays", Cine.active && GConfig.get().fxBlur ? Cine.rays : 0);
            fx.setUniforms("RayX", Cine.rayX);
            fx.setUniforms("RayY", Cine.rayY);
            fx.setUniforms("Tear", Cine.active && GConfig.get().fxTear ? Cine.tear : 0);
            boolean ir = !Cine.active && Viewfinder.up && Viewfinder.ir;   // ИК: зелёный монохром
            fx.setUniforms("TintR", Cine.active ? Cine.tintR : ir ? 0.55f : 1);
            fx.setUniforms("TintG", Cine.active ? Cine.tintG : ir ? 1.2f : 1);
            fx.setUniforms("TintB", Cine.active ? Cine.tintB : ir ? 0.6f : 1);
            fx.setUniforms("Cone", Cine.active ? Cine.cone : 0);
            // видоискатель — полная плёнка; глазами — лёгкий плёночный фильтр (настройка) или чисто
            float vhs = Viewfinder.view() ? 1f : GConfig.get().vhs ? 0.3f : 0f;
            fx.setUniforms("Vhs", vhs * GConfig.get().vhsStrength / 100f);
            float g = Cine.active ? Cine.glitch : Math.max(near, (float) Math.pow(Math.max(0, 1 - GlukhomanClient.shownSanity / 60f), 2));
            fx.setUniforms("Glitch", (GConfig.get().fxGlitch ? 1f : 0f) * (GConfig.get().noFlashing ? 0.4f : 1f) * Math.min(1f, g));
            fx.setUniforms("Crush", Cine.active ? Cine.crush : Viewfinder.up ? 0.3f : 0.45f);
            fx.render(tickDelta);
            mc.getFramebuffer().beginWrite(false);
        } catch (Exception e) {
            failed = true;
            Glukhoman.LOG.warn("Пост-обработка Глухомани выключена", e);
        }
    }

    public static void reset() {
        if (fx != null) fx.close();
        fx = null;
    }
}
