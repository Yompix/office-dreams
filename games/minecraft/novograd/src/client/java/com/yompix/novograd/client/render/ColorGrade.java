package com.yompix.novograd.client.render;

import com.yompix.novograd.Novograd;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.util.math.MathHelper;

/** Кинематографичная цветокоррекция поверх мира (до интерфейса). Клавиша K — вкл/выкл. */
public final class ColorGrade {
    private ColorGrade() {}

    public static boolean enabled = true;
    /** 1 — «Красивая» (со свечением), 0 — «Лёгкая» для слабых ноутбуков. */
    public static float quality = 1f;

    /** Клавиша K: Красивая → Лёгкая → Выключена → Красивая. Возвращает название режима. */
    public static String cycle() {
        if (enabled && quality > 0.5f) quality = 0f;
        else if (enabled) enabled = false;
        else {
            enabled = true;
            quality = 1f;
        }
        return !enabled ? "выключена" : quality > 0.5f ? "красивая (свечение огней)" : "лёгкая (для слабых ноутбуков)";
    }
    private static PostEffectProcessor fx;
    private static boolean failed;
    private static int w, h;

    public static void render(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || failed || mc.world == null || irisShaders()) return;
        try {
            if (fx == null) {
                fx = new PostEffectProcessor(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), Novograd.id("shaders/post/grade.json"));
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
            // закат и рассвет: золотой час
            long dawn = Math.min(Math.abs(t - 23300), Math.abs(t + 24000 - 23300));
            float sunset = Math.max(1 - Math.abs(t - 12500) / 1300f, 1 - dawn / 1000f);
            fx.setUniforms("Sunset", MathHelper.clamp(sunset, 0, 1));
            fx.setUniforms("Rain", mc.world.getRainGradient(tickDelta));
            fx.setUniforms("Quality", quality);
            fx.setUniforms("Time", (mc.world.getTime() % 1000 + tickDelta) / 20f);
            fx.render(tickDelta);
            mc.getFramebuffer().beginWrite(false);
        } catch (Exception e) {
            failed = true;
            Novograd.LOG.warn("Цветокоррекция Новограда выключена (несовместима с шейдерами?)", e);
        }
    }

    /** С шейдерами Iris наша коррекция не нужна — они сами рисуют картинку. */
    private static Object irisApi;
    private static java.lang.reflect.Method irisInUse;
    private static Boolean irisLoaded;

    /** Проверка каждый кадр — поэтому без поиска класса и метода каждый раз (раньше рефлексия шла каждый кадр). */
    private static boolean irisShaders() {
        if (irisLoaded == null) irisLoaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iris");
        if (!irisLoaded) return false;
        try {
            if (irisInUse == null) {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            }
            return (boolean) irisInUse.invoke(irisApi);
        } catch (ReflectiveOperationException | RuntimeException e) {
            irisLoaded = false;
            return false;
        }
    }

    public static void reset() {
        if (fx != null) fx.close();
        fx = null;
    }
}
