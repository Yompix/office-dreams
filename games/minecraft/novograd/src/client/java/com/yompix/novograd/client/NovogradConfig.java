package com.yompix.novograd.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки Новограда (config/novograd.json): звук, радио, графика, интерфейс. */
public final class NovogradConfig {
    /** Громкость радио в машине, 0..1. */
    public float radioVolume = 0.6f;
    /** Последняя станция (индекс в CarRadio.STATIONS), -1 — радио выключено. */
    public int radioStation = 0;
    /** Громкость голосов героев, 0..1. */
    public float voiceVolume = 1f;
    /** Графика: 0 — без фильтра, 1 — лёгкая (слабые ноутбуки), 2 — красивая. */
    public int graphics = 2;
    /** Деревья и трава колышутся на ветру. */
    public boolean windyFoliage = true;
    /** Красивая вода (блики, волны, цвет). */
    public boolean prettyWater = true;
    /** Субтитры в кат-сценах и разговорах. */
    public boolean subtitles = true;
    /** Радар (мини-карта) слева внизу. */
    public boolean minimap = true;
    /**
     * Производительность: 0 — низкая (слабый ноутбук), 1 — средняя, 2 — высокая. Меняет только детализацию того,
     * что далеко или не видно: упрощённые дальние машины, реже думающие дальние жители, фоновая догенерация острова.
     */
    public int perf = 1;
    /** Масштаб HUD (0.75..1.5). */
    public float hudScale = 0.85f;
    /** 1.0: размер субтитров (0.6..1.4) — меньше по умолчанию, как в GTA. */
    public float subtitleScale = 0.85f;
    /** 1.0: подсказки слева сверху (каждая — один раз за мир). */
    public boolean hints = true;
    /** Какие подсказки уже показаны («мир|ключ»). */
    public java.util.Set<String> seenHints = new java.util.HashSet<>();
    /** 1.0: «Производительность: АВТО» — уровень по железу (встроенная графика / мало памяти → низкая). */
    public boolean perfAuto = true;
    /** Для какого уровня последний раз выставлены настройки Minecraft (−1 — ещё ни разу). */
    public int vanillaFor = -1;
    /** 1.0: настройки чата один раз сделаны читаемыми (ChatStyle). */
    public boolean chatStyled;
    /** 0.8: живые анимации — походка, дыхание, позы и жесты игроков (и у друзей), паузы и реакции героев сюжета. */
    public boolean liveAnims = true;
    /** 0.8: от первого лица качается только предмет в руке; камера при ходьбе не качается (true — как в Minecraft). */
    public boolean camBob = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static NovogradConfig cfg;

    public static NovogradConfig get() {
        if (cfg == null) load();
        return cfg;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("novograd.json");
    }

    public static void load() {
        try {
            Path f = file();
            cfg = Files.exists(f) ? GSON.fromJson(Files.readString(f), NovogradConfig.class) : null;
        } catch (Exception e) {
            cfg = null;
        }
        if (cfg == null) cfg = new NovogradConfig();
        apply();
    }

    public static void save() {
        apply();
        applyPacks();
        try {
            Files.writeString(file(), GSON.toJson(get()));
        } catch (Exception ignored) {
        }
    }

    /** Встроенные паки шейдеров: ветер в листве и красивая вода. Включаются/выключаются по настройкам. */
    public static void registerPacks() {
        var mod = FabricLoader.getInstance().getModContainer("novograd").orElseThrow();
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(com.yompix.novograd.Novograd.id("wind"), mod,
                net.minecraft.text.Text.literal("Новоград: деревья на ветру"), net.fabricmc.fabric.api.resource.ResourcePackActivationType.DEFAULT_ENABLED);
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(com.yompix.novograd.Novograd.id("water"), mod,
                net.minecraft.text.Text.literal("Новоград: красивая вода"), net.fabricmc.fabric.api.resource.ResourcePackActivationType.DEFAULT_ENABLED);
    }

    /** Привести включённые паки к настройкам (перезагрузит ресурсы, только если что-то поменялось). */
    public static void applyPacks() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null || mc.getResourcePackManager() == null) return;
        var m = mc.getResourcePackManager();
        boolean changed = false;
        String[][] packs = {{"novograd:wind", String.valueOf(get().windyFoliage)}, {"novograd:water", String.valueOf(get().prettyWater)}};
        for (String[] pk : packs) {
            if (m.getProfile(pk[0]) == null) continue;
            boolean want = Boolean.parseBoolean(pk[1]), on = m.getEnabledIds().contains(pk[0]);
            if (want && !on) changed |= m.enable(pk[0]);
            else if (!want && on) changed |= m.disable(pk[0]);
        }
        if (changed) mc.options.refreshResourcePacks(m);
    }

    /** Итоговый размер HUD: настройка игрока × поправка на маленький экран / крупный масштаб интерфейса. */
    public static float hud() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        float auto = mc.getWindow() == null ? 1 : Math.max(0.55f, Math.min(1f, mc.getWindow().getScaledHeight() / 420f));
        return Math.max(0.4f, Math.min(1.6f, get().hudScale * auto));
    }

    /** Применить к системам игры (вызывать после изменения). */
    public static void apply() {
        var c = cfg;
        if (c == null) return;
        com.yompix.novograd.client.render.ColorGrade.enabled = c.graphics > 0;
        com.yompix.novograd.client.render.ColorGrade.quality = c.graphics >= 2 ? 1f : 0f;
        sendPerf();
    }

    /** 1.0: новые меры производительности (АВТО, отсечение дальних жителей, настройки Minecraft); false — как в 0.6.6. */
    public static final boolean PERF10 = !"false".equals(System.getProperty("novograd.perf10"));

    /** Что нашли при автоопределении (для настроек и лога). */
    public static String detected = "";

    /**
     * 1.0 «Производительность: АВТО»: встроенная графика (Intel UHD/Iris, AMD Vega/Radeon Graphics, программная) или
     * ≤ 8.5 ГБ памяти компьютера, или Java < 3 ГБ → низкая; дискретная видеокарта и ≥ 16 ГБ → высокая; иначе средняя.
     */
    public static int autoLevel() {
        String gpu = "";
        try {
            gpu = String.valueOf(com.mojang.blaze3d.platform.GlDebugInfo.getRenderer()).toLowerCase(java.util.Locale.ROOT);
        } catch (Throwable ignored) {
        }
        long ram = 0;
        try {
            ram = ((com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
        } catch (Throwable ignored) {
        }
        long heap = Runtime.getRuntime().maxMemory();
        boolean igpu = gpu.matches(".*(intel|uhd|iris|vega|radeon\\(tm\\) graphics|radeon graphics|llvmpipe|softpipe|mesa offscreen|mali|adreno|apple m).*");
        boolean dgpu = gpu.matches(".*(rtx|gtx|radeon rx|rx [5-9]|arc a|quadro).*");
        int lv = igpu || ram > 0 && ram < 8_700_000_000L || heap < 3_000_000_000L ? 0 : dgpu && ram >= 15_000_000_000L ? 2 : 1;
        detected = gpu + ", ОЗУ " + ram / 1_000_000_000 + " ГБ, Java " + heap / 1_000_000_000 + " ГБ";
        return lv;
    }

    /** Старт игры: «АВТО» — определить уровень; при смене уровня — подстроить настройки Minecraft (один раз на уровень). */
    public static void startup(net.minecraft.client.MinecraftClient mc) {
        var c = get();
        if (!PERF10) return;
        if (c.perfAuto) c.perf = autoLevel();
        com.yompix.novograd.Novograd.LOG.info("[perf] производительность: {} ({}), {}", c.perf, c.perfAuto ? "авто" : "вручную", detected);
        if (c.vanillaFor != c.perf) vanilla(mc, c.perf);
        save();
    }

    /** Настройки Minecraft под уровень: на низкой — то, что сильнее всего ест кадр на встроенной графике. */
    public static void vanilla(net.minecraft.client.MinecraftClient mc, int lv) {
        var o = mc.options;
        if (o == null) return;
        get().vanillaFor = lv;
        if (lv <= 0) {
            o.getViewDistance().setValue(Math.min(o.getViewDistance().getValue(), 8));
            o.getSimulationDistance().setValue(Math.min(o.getSimulationDistance().getValue(), 5));
            o.getEntityDistanceScaling().setValue(0.75);
            o.getParticles().setValue(net.minecraft.client.option.ParticlesMode.DECREASED);
            o.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
            o.getBiomeBlendRadius().setValue(0);
            o.getGraphicsMode().setValue(net.minecraft.client.option.GraphicsMode.FAST);
            o.getEntityShadows().setValue(false);
            o.getMipmapLevels().setValue(Math.min(o.getMipmapLevels().getValue(), 2));
        } else if (lv == 1) {
            o.getViewDistance().setValue(Math.min(o.getViewDistance().getValue(), 12));
            o.getEntityDistanceScaling().setValue(1.0);
            o.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.FAST);
            o.getBiomeBlendRadius().setValue(Math.min(o.getBiomeBlendRadius().getValue(), 2));
        }
        o.write();
    }

    /** 1.0: бюджет частиц на экране (ParticleBudgetMixin). */
    public static int particleBudget() {
        int p = get().perf;
        return p <= 0 ? 350 : p == 1 ? 900 : 2500;
    }

    /** 1.0: дальше этого (блоков) машины не рисуются (кроме актёров кат-сцены). */
    public static float carDist() {
        if (!PERF10) return 1e6f;
        int p = get().perf;
        return p <= 0 ? 72 : p == 1 ? 110 : 180;
    }

    /** 1.0: дальше этого (блоков) жители не рисуются вовсе (кроме актёров кат-сцены). */
    public static float citizenDist() {
        if (!PERF10) return 1e6f;
        int p = get().perf;
        return p <= 0 ? 48 : p == 1 ? 80 : 128;
    }

    /** Дальность (блоков) упрощения машин: дальше near — без салона и мелочей, дальше far — только кузов и колёса. */
    public static float lodNear() {
        int p = get().perf;
        return p <= 0 ? 20 : p == 1 ? 32 : 48;
    }

    public static float lodFar() {
        int p = get().perf;
        return p <= 0 ? 48 : p == 1 ? 80 : 128;
    }

    /** Сообщить серверу (в одиночной игре — своему) уровень производительности. */
    public static void sendPerf() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null || mc.getNetworkHandler() == null || cfg == null) return;
        net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
        n.putInt("level", Math.max(0, Math.min(2, cfg.perf)));
        NovogradClient.send("perf_level", n);
    }
}
