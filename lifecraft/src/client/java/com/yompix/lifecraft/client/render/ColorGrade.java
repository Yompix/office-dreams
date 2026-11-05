package com.yompix.lifecraft.client.render;

import com.yompix.lifecraft.LifeCraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.math.MathHelper;

/** Кинематографичная цветокоррекция поверх мира (до интерфейса). Клавиша K — вкл/выкл. */
public final class ColorGrade {
    private ColorGrade() {}

    public static boolean enabled = true;
    private static PostEffectProcessor fx;
    private static boolean failed;
    private static int w, h;

    public static void render(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || failed || mc.world == null || irisShaders()) return;
        try {
            if (fx == null) {
                fx = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), LifeCraft.id("shaders/post/grade.json"));
                w = h = -1;
            }
            int fw = mc.getWindow().getFramebufferWidth(), fh = mc.getWindow().getFramebufferHeight();
            if (fw != w || fh != h) {
                fx.setupDimensions(fw, fh);
                w = fw;
                h = fh;
            }
            long t = mc.world.getTimeOfDay() % 24000;
            // 0 днём, 1 ночью, плавные закат и рассвет
            float night = t < 12000 ? (t > 22500 ? 1 - (t - 22500) / 1500f : t < 500 ? 0 : 0) : MathHelper.clamp((t - 12000) / 1500f, 0, 1);
            if (t >= 22500) night = MathHelper.clamp(1 - (t - 22500) / 1500f, 0, 1);
            fx.setUniforms("Night", night);
            fx.setUniforms("Strength", 1f);
            fx.render(tickDelta);
            mc.getFramebuffer().beginWrite(false);
        } catch (Exception e) {
            failed = true;
            LifeCraft.LOG.warn("Цветокоррекция LifeCraft выключена (несовместима с шейдерами?)", e);
        }
    }

    /** С шейдерами Iris наша коррекция не нужна — они сами рисуют картинку. */
    private static boolean irisShaders() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris")) return false;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isShaderPackInUse").invoke(inst);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static void reset() {
        if (fx != null) fx.close();
        fx = null;
    }
}
