package com.yompix.glukhoman.client.menu;

import com.yompix.glukhoman.Glukhoman;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;

import java.util.List;

/** «Новая смена» — сразу новый мир «Глухомань»; «Продолжить» — последнее сохранение. */
public final class WorldStart {
    private WorldStart() {}

    public static void newShift() {
        MinecraftClient c = MinecraftClient.getInstance();
        var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
        String name = "Смена " + new java.text.SimpleDateFormat("dd.MM HH-mm").format(new java.util.Date());
        var info = new net.minecraft.world.level.LevelInfo(name, net.minecraft.world.GameMode.ADVENTURE, false,
                net.minecraft.world.Difficulty.NORMAL, false, new net.minecraft.world.GameRules(), data);
        var key = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD_PRESET, Glukhoman.id("taiga"));
        c.createIntegratedServerLoader().createAndStart(name.replace(':', '-'), info, net.minecraft.world.gen.GeneratorOptions.createRandom(),
                drm -> drm.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(key).createDimensionsRegistryHolder(),
                new TitleMenuScreen());
    }

    /** Есть ли хоть одно сохранение (для «Продолжить» и подтверждения «Новой смены»). */
    public static boolean hasSave(MinecraftClient c) {
        try {
            return !c.getLevelStorage().getLevelList().levels().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public static void newShift(MinecraftClient c, net.minecraft.client.gui.screen.Screen back) {
        newShift();
    }

    public static void continueShift(MinecraftClient c, net.minecraft.client.gui.screen.Screen back) {
        continueLatest();
    }

    public static void continueLatest() {
        MinecraftClient c = MinecraftClient.getInstance();
        try {
            var storage = c.getLevelStorage();
            storage.loadSummaries(storage.getLevelList()).thenAcceptAsync(list -> {
                var latest = list.stream().filter(s -> !s.isLocked() && !s.requiresConversion())
                        .max(java.util.Comparator.comparingLong(net.minecraft.world.level.storage.LevelSummary::getLastPlayed)).orElse(null);
                if (latest == null) {
                    newShift();
                    return;
                }
                try {
                    c.createIntegratedServerLoader().start(latest.getName(), () -> c.setScreen(new TitleMenuScreen()));
                } catch (Exception e) {
                    Glukhoman.LOG.warn("Продолжить: не открылся мир", e);
                    c.setScreen(new SelectWorldScreen(new TitleMenuScreen()));
                }
            }, c);
        } catch (Exception e) {
            c.setScreen(new SelectWorldScreen(new TitleMenuScreen()));
        }
    }
}
