package com.yompix.shaderlite;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * «Адаптивное качество»: держит целевой FPS, на лету снижая (и потом возвращая) облака, дальность
 * прорисовки, частицы и дальность сущностей. Параметры шейдера (разрешение теней и проходов) Iris на лету менять
 * не даёт без перезагрузки шейдера (подвисание на секунды), поэтому мод их не трогает; но тени в Iris
 * ограничены дальностью прорисовки, так что при её снижении тени тоже дешевеют.
 *
 * Свои значения мод никогда не сохраняет в options.txt: перед открытием экрана настроек и при выходе из мира /
 * игры он возвращает ваши настройки.
 */
public final class AdaptiveQuality implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("ShaderLite");
    public static Config CONFIG;
    private static AdaptiveQuality INSTANCE;

    enum Step { CLOUDS, VIEW, PARTICLES1, PARTICLES2, ENTITY1, ENTITY2 }

    // Ваши настройки (база) и то, что мод выставил в последний раз
    private boolean haveBase;
    private int baseView; private CloudRenderMode baseClouds; private ParticlesMode baseParticles; private double baseEntity;
    private int appliedView = -1; private CloudRenderMode appliedClouds; private ParticlesMode appliedParticles; private double appliedEntity = -1;
    private int level;            // сколько ступеней снижения применено
    private List<Step> ladder = List.of();

    // Замер кадров
    private long lastFrameNs, windowStartNs; private int windowFrames;
    private long pauseUntilNs;    // после изменений и входа в мир: даём чанкам догрузиться
    private int badWindows, goodWindows, goodNeeded = 6;
    private long lastUpNs;

    @Override public void onInitializeClient() {
        INSTANCE = this;
        CONFIG = new Config(FabricLoader.getInstance().getConfigDir());
        CONFIG.load();
        WorldRenderEvents.END.register(ctx -> onFrame());
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> restore(c, "выход из игры"));
        LOG.info("[ShaderLite] Адаптивное качество: {} (цель {} FPS)", CONFIG.enabled ? "включено" : "выключено", CONFIG.targetFps);
    }

    /** Вызывается из экрана настроек мода. */
    public static void onConfigChanged() {
        if (INSTANCE == null) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (!CONFIG.enabled) INSTANCE.restore(c, "мод выключен");
        INSTANCE.goodNeeded = 6;
        INSTANCE.pauseUntilNs = System.nanoTime() + 2_000_000_000L;
    }

    private void onFrame() {
        long now = System.nanoTime();
        lastFrameNs = now;
        windowFrames++;
    }

    private void onTick(MinecraftClient c) {
        long now = System.nanoTime();
        if (c.world == null || c.player == null) { // не в мире
            if (haveBase) restore(c, "выход из мира");
            haveBase = false; resetWindow(now, 5_000_000_000L);
            return;
        }
        if (!CONFIG.enabled) { resetWindow(now, 0); return; }
        Screen s = c.currentScreen;
        if (isOptionsScreen(s)) { // показать и сохранить именно ваши настройки, а не урезанные
            if (level > 0 || appliedView >= 0) restore(c, "открыты настройки");
            haveBase = false; resetWindow(now, 3_000_000_000L);
            return;
        }
        if (!haveBase) { captureBase(c.options); resetWindow(now, 6_000_000_000L); return; }
        if (userChangedSomething(c.options)) { // игрок сам поменял (например, F3+F) — принимаем как новую базу
            captureBase(c.options); resetWindow(now, 3_000_000_000L); return;
        }
        // Меню, пауза, свернутое окно (Dynamic FPS там специально снижает FPS) — не считаем
        if (s != null || c.isPaused() || !c.isWindowFocused()) { resetWindow(now, 1_000_000_000L); return; }
        if (now < pauseUntilNs) { windowStartNs = now; windowFrames = 0; return; }
        long el = now - windowStartNs;
        if (el < 1_000_000_000L) return;
        double fps = windowFrames * 1e9 / el;
        windowStartNs = now; windowFrames = 0;
        evaluate(c, fps, now);
    }

    private void evaluate(MinecraftClient c, double fps, long now) {
        int target = CONFIG.targetFps;
        if (fps < target * 0.92) { badWindows++; goodWindows = 0; }
        else if (fps > target * 1.30) { goodWindows++; badWindows = 0; }
        else { badWindows = 0; goodWindows = 0; }

        if (badWindows >= 2 && level < ladder.size()) {
            // если только что поднимали качество и снова не тянет — дольше ждём перед следующим подъёмом
            if (now - lastUpNs < 15_000_000_000L) goodNeeded = Math.min(goodNeeded * 2, 60);
            level++; badWindows = 0;
            apply(c, "FPS " + Math.round(fps) + " < " + target);
            pauseUntilNs = now + (ladder.get(level - 1) == Step.VIEW ? 3_000_000_000L : 1_500_000_000L);
        } else if (goodWindows >= goodNeeded && level > 0) {
            level--; goodWindows = 0; lastUpNs = now;
            apply(c, "FPS " + Math.round(fps) + " > " + target);
            pauseUntilNs = now + 3_000_000_000L;
        } else if (goodWindows >= 30) {
            goodNeeded = Math.max(6, goodNeeded / 2); goodWindows = 0;
        }
    }

    private void captureBase(GameOptions o) {
        baseView = o.getViewDistance().getValue();
        baseClouds = o.getCloudRenderMode().getValue();
        baseParticles = o.getParticles().getValue();
        baseEntity = o.getEntityDistanceScaling().getValue();
        haveBase = true; level = 0; goodNeeded = 6;
        appliedView = baseView; appliedClouds = baseClouds; appliedParticles = baseParticles; appliedEntity = baseEntity;
        ladder = buildLadder();
    }

    /** Порядок снижения: сначала то, что меньше всего заметно глазу. */
    private List<Step> buildLadder() {
        List<Step> l = new ArrayList<>();
        int views = Math.max(0, (baseView - CONFIG.minViewDistance + 1) / 2); // шаги по 2 чанка
        boolean clouds = CONFIG.adjustClouds && baseClouds != CloudRenderMode.OFF;
        boolean p1 = CONFIG.adjustParticles && baseParticles == ParticlesMode.ALL;
        boolean p2 = CONFIG.adjustParticles && baseParticles != ParticlesMode.MINIMAL;
        boolean e1 = CONFIG.adjustEntityDistance && baseEntity > 0.76;
        boolean e2 = CONFIG.adjustEntityDistance && baseEntity > 0.51;
        if (clouds) l.add(Step.CLOUDS);
        if (views > 0) { l.add(Step.VIEW); views--; }
        if (p1) l.add(Step.PARTICLES1);
        if (views > 0) { l.add(Step.VIEW); views--; }
        if (e1) l.add(Step.ENTITY1);
        if (p2) l.add(Step.PARTICLES2);
        if (views > 0) { l.add(Step.VIEW); views--; }
        if (e2) l.add(Step.ENTITY2);
        while (views-- > 0) l.add(Step.VIEW);
        return l;
    }

    private void apply(MinecraftClient c, String why) {
        int view = baseView; CloudRenderMode clouds = baseClouds; ParticlesMode part = baseParticles; double ent = baseEntity;
        for (int i = 0; i < level; i++) switch (ladder.get(i)) {
            case CLOUDS -> clouds = CloudRenderMode.OFF;
            case VIEW -> view = Math.max(CONFIG.minViewDistance, view - 2);
            case PARTICLES1 -> part = ParticlesMode.DECREASED;
            case PARTICLES2 -> part = ParticlesMode.MINIMAL;
            case ENTITY1 -> ent = Math.min(ent, 0.75);
            case ENTITY2 -> ent = Math.min(ent, 0.5);
        }
        set(c.options, view, clouds, part, ent);
        LOG.info("[ShaderLite] {} -> ступень {}/{}: дальность {}, облака {}, частицы {}, сущности {}%",
                why, level, ladder.size(), view, clouds, part, Math.round(ent * 100));
        if (CONFIG.showMessages && c.inGameHud != null)
            c.inGameHud.setOverlayMessage(Text.translatable("shaderlite_adaptive.msg", view, level, ladder.size()), false);
    }

    private void set(GameOptions o, int view, CloudRenderMode clouds, ParticlesMode part, double ent) {
        if (o.getViewDistance().getValue() != view) o.getViewDistance().setValue(view);
        if (o.getCloudRenderMode().getValue() != clouds) o.getCloudRenderMode().setValue(clouds);
        if (o.getParticles().getValue() != part) o.getParticles().setValue(part);
        if (o.getEntityDistanceScaling().getValue() != ent) o.getEntityDistanceScaling().setValue(ent);
        appliedView = view; appliedClouds = clouds; appliedParticles = part; appliedEntity = ent;
    }

    private boolean userChangedSomething(GameOptions o) {
        return o.getViewDistance().getValue() != appliedView || o.getCloudRenderMode().getValue() != appliedClouds
                || o.getParticles().getValue() != appliedParticles || Math.abs(o.getEntityDistanceScaling().getValue() - appliedEntity) > 1e-6;
    }

    void restore(MinecraftClient c, String why) {
        if (!haveBase || c == null || c.options == null) return;
        if (level > 0) LOG.info("[ShaderLite] {}: возвращаю ваши настройки (дальность {})", why, baseView);
        level = 0;
        set(c.options, baseView, baseClouds, baseParticles, baseEntity);
    }

    private void resetWindow(long now, long pauseNs) {
        windowStartNs = now; windowFrames = 0; badWindows = 0; goodWindows = 0;
        pauseUntilNs = Math.max(pauseUntilNs, now + pauseNs);
    }

    private static boolean isOptionsScreen(Screen s) {
        if (s == null) return false;
        if (s instanceof GameOptionsScreen) return true;
        String n = s.getClass().getName().toLowerCase();
        return n.contains("sodium") || n.contains("iris") || n.contains("option") || n.contains("settings");
    }
}
