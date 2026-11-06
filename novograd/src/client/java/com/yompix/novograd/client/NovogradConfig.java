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
    /** Масштаб HUD (0.75..1.5). */
    public float hudScale = 1f;

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

    /** Применить к системам игры (вызывать после изменения). */
    public static void apply() {
        var c = cfg;
        if (c == null) return;
        com.yompix.novograd.client.render.ColorGrade.enabled = c.graphics > 0;
        com.yompix.novograd.client.render.ColorGrade.quality = c.graphics >= 2 ? 1f : 0f;
    }
}
