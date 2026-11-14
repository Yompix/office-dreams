package com.yompix.glukhoman.client;

import com.yompix.glukhoman.Glukhoman;
import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.story.Story;
import com.yompix.glukhoman.story.StoryState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Автотест клиента для снимков атмосферы (-Dglukhoman.autotest=shots): создаёт мир «Глухомань», проходит по местам глав
 * и сохраняет скриншоты в run/screenshots/glk_*.png, потом закрывает игру.
 */
public final class Autotest {
    private Autotest() {}

    record Step(int delay, Consumer<MinecraftClient> act) {}

    static final List<Step> STEPS = new ArrayList<>();
    static int idx, timer;

    public static void init() {
        if ("watch".equals(System.getProperty("glukhoman.autotest"))) {
            watch();
            return;
        }
        if ("join".equals(System.getProperty("glukhoman.autotest"))) {
            join();
            return;
        }
        if ("trailer".equals(System.getProperty("glukhoman.autotest"))) {
            trailer();
            return;
        }
        if ("models".equals(System.getProperty("glukhoman.autotest"))) {
            models();
            return;
        }
        if ("shots2".equals(System.getProperty("glukhoman.autotest"))) {
            shots2();
            return;
        }
        if (!"shots".equals(System.getProperty("glukhoman.autotest"))) return;
        STEPS.add(new Step(100, c -> {
            var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                    net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
            var info = new net.minecraft.world.level.LevelInfo("GlkShots" + System.currentTimeMillis() % 100000, net.minecraft.world.GameMode.ADVENTURE, false,
                    net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.GameRules(), data);
            var key = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD_PRESET, Glukhoman.id("taiga"));
            c.createIntegratedServerLoader().createAndStart(info.getLevelName(), info, net.minecraft.world.gen.GeneratorOptions.createRandom(),
                    drm -> drm.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(key).createDimensionsRegistryHolder(),
                    new net.minecraft.client.gui.screen.TitleScreen());
        }));
        wait(c -> c.player != null && c.currentScreen != null, 1200);
        STEPS.add(new Step(60, c -> shot(c, "00_difficulty")));
        STEPS.add(new Step(5, c -> {
            c.setScreen(null);
            server(c, s -> Story.chooseDifficulty(pl(s), 1));
        }));
        STEPS.add(new Step(40, c -> shot(c, "01_title_card")));
        // старт: УАЗ на закате, взгляд на север
        STEPS.add(new Step(160, c -> look(c, 180, 4)));
        STEPS.add(new Step(40, c -> shot(c, "02_start_dusk")));
        // посёлок, ночь, глава 2: улица и школа
        STEPS.add(new Step(5, c -> server(c, s -> Story.jump(2))));
        STEPS.add(new Step(100, c -> server(c, s -> tp(s, 0, 66, 112, 0, 0))));
        STEPS.add(new Step(140, c -> shot(c, "03_village_night")));
        STEPS.add(new Step(5, c -> server(c, s -> { tp(s, -12, 65, 88, 180, 8); spawnAt(s, GContent.LISTENER, -12, 65, 79); })));
        STEPS.add(new Step(120, c -> lightOn(c)));
        STEPS.add(new Step(40, c -> shot(c, "04_school_listener")));
        // свет в посёлке
        STEPS.add(new Step(5, c -> server(c, s -> { var st = StoryState.get(s.getOverworld()); for (String k : new String[]{"crate_council_fuse", "crate_school_fuse", "crate_garage_fuse"}) Story.interact(pl(s), st.find(k), k); Story.interact(pl(s), st.find("fusebox"), "fusebox"); tp(s, 2, 66, 60, 0, 6); })));
        STEPS.add(new Step(160, c -> shot(c, "05_village_power")));
        // просека в тумане, Жердь
        STEPS.add(new Step(5, c -> server(c, s -> Story.jump(3))));
        STEPS.add(new Step(60, c -> server(c, s -> { clearPoles(s); tp(s, 3, 66, -60, 180, -2); spawnAt(s, GContent.POLE, 1, 0, -69); })));
        STEPS.add(new Step(160, c -> shot(c, "06_forest_pole")));
        STEPS.add(new Step(5, c -> server(c, s -> { clearPoles(s); tp(s, -4, 65, -226, 160, -8); })));
        STEPS.add(new Step(160, c -> shot(c, "07_station")));
        // шахта
        STEPS.add(new Step(5, c -> server(c, s -> { Story.jump(4); })));
        STEPS.add(new Step(40, c -> server(c, s -> { var st = StoryState.get(s.getOverworld()); Story.interact(pl(s), st.find("lift_top"), "lift_top"); })));
        STEPS.add(new Step(140, c -> server(c, s -> { tp(s, -122, 25, -173, -90, 0); spawnAt(s, GContent.LISTENER, -112, 25, -173); })));
        STEPS.add(new Step(60, c -> lightOn(c)));
        STEPS.add(new Step(100, c -> shot(c, "08_mine_listener")));
        // бункер и Хор
        STEPS.add(new Step(5, c -> server(c, s -> Story.jump(5))));
        STEPS.add(new Step(60, c -> server(c, s -> { tp(s, 14, 42, -252, 180, 0); var st = StoryState.get(s.getOverworld()); })));
        STEPS.add(new Step(60, c -> server(c, s -> spawnAt(s, GContent.CHOIR, 14, 42, -262))));
        STEPS.add(new Step(120, c -> shot(c, "09_bunker_choir")));
        STEPS.add(new Step(40, c -> c.scheduleStop()));
        ClientTickEvents.END_CLIENT_TICK.register(Autotest::tick);
    }

    /** Клиент заходит на выделенный сервер (localhost): выбор сложности, начало главы, рация Анны — проверка сети и синхронизации. */
    static void join() {
        STEPS.add(new Step(100, c -> net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(new net.minecraft.client.gui.screen.TitleScreen(), c,
                net.minecraft.client.network.ServerAddress.parse("localhost:25565"),
                new net.minecraft.client.network.ServerInfo("glk", "localhost:25565", net.minecraft.client.network.ServerInfo.ServerType.OTHER), false, null)));
        wait(c -> c.player != null && c.currentScreen instanceof com.yompix.glukhoman.client.screen.DifficultyScreen, 1500);
        STEPS.add(new Step(40, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: экран сложности пришёл с сервера = {}", c.currentScreen instanceof com.yompix.glukhoman.client.screen.DifficultyScreen);
            shot(c, "10_mp_difficulty");
            var m = com.yompix.glukhoman.net.Net.msg("difficulty");
            m.putInt("d", 1);
            GlukhomanClient.send(m);
            c.setScreen(null);
        }));
        STEPS.add(new Step(60, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: кат-сцена идёт = {}, хозяин пропускает", Cine.active);
            GlukhomanClient.send(com.yompix.glukhoman.net.Net.msg("skip"));
        }));
        STEPS.add(new Step(20, c -> Glukhoman.LOG.info("AUTOTEST сеть: после пропуска кат-сцена идёт = {}", Cine.active)));
        STEPS.add(new Step(220, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: глава {} «{}», цель «{}»", GlukhomanClient.chapter, GlukhomanClient.title, GlukhomanClient.objective);
            shot(c, "11_mp_start");
            c.player.networkHandler.sendChatCommand("tp @s 0 65 118");
        }));
        STEPS.add(new Step(40, c -> c.player.networkHandler.sendChatCommand("tp @s -12 65 128 90 10")));
        STEPS.add(new Step(40, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: цель после входа в дом «{}»", GlukhomanClient.objective);
            c.interactionManager.interactBlock(c.player, net.minecraft.util.Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(new net.minecraft.util.math.Vec3d(-14.5, 66.3, 126.5), net.minecraft.util.math.Direction.EAST, new BlockPos(-15, 66, 126), false));
        }));
        STEPS.add(new Step(100, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: субтитры {}", GlukhomanClient.SUBS.isEmpty() ? "нет" : GlukhomanClient.SUBS.get(GlukhomanClient.SUBS.size() - 1)[1]);
            shot(c, "12_mp_anna_radio");
        }));
        STEPS.add(new Step(500, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: цель после рации «{}», рассудок {}", GlukhomanClient.objective, GlukhomanClient.sanity);
            c.player.networkHandler.sendChatCommand("glukhoman chapter 3");
        }));
        STEPS.add(new Step(200, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: глава {} «{}»", GlukhomanClient.chapter, GlukhomanClient.title);
            c.player.networkHandler.sendChatCommand("glukhoman scare");
        }));
        STEPS.add(new Step(60, c -> shot(c, "13_mp_forest")));
        STEPS.add(new Step(20, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: готово");
            c.world.disconnect();
            c.scheduleStop();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(Autotest::tick);
    }

    /** Второй игрок: просто заходит и смотрит, синхронизируется ли сюжет, который двигает первый. */
    static void watch() {
        STEPS.add(new Step(260, c -> net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(new net.minecraft.client.gui.screen.TitleScreen(), c,
                net.minecraft.client.network.ServerAddress.parse("localhost:25565"),
                new net.minecraft.client.network.ServerInfo("glk", "localhost:25565", net.minecraft.client.network.ServerInfo.ServerType.OTHER), false, null)));
        wait(c -> c.player != null && GlukhomanClient.chapter > 0, 2000);
        STEPS.add(new Step(60, c -> {
            if (c.currentScreen != null) c.setScreen(null);
            Glukhoman.LOG.info("AUTOTEST друг: глава {} «{}», цель «{}», кат-сцена идёт = {}", GlukhomanClient.chapter, GlukhomanClient.title, GlukhomanClient.objective, Cine.active);
            shot(c, "20_friend_start");
        }));
        wait(c -> GlukhomanClient.objective.contains("батарейки"), 1500);
        STEPS.add(new Step(20, c -> {
            Glukhoman.LOG.info("AUTOTEST друг: цель обновилась «{}»", GlukhomanClient.objective);
            shot(c, "21_friend_sync");
        }));
        wait(c -> GlukhomanClient.chapter == 3, 1500);
        STEPS.add(new Step(80, c -> {
            Glukhoman.LOG.info("AUTOTEST друг: глава {} «{}», цель «{}»", GlukhomanClient.chapter, GlukhomanClient.title, GlukhomanClient.objective);
            shot(c, "22_friend_ch3");
            c.world.disconnect();
            c.scheduleStop();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(Autotest::tick);
    }

    // ======================= 0.2: кат-сцены, крупные планы, видео =======================
    static int rec = -1, recLeft;
    static String recName;

    static void record(String name, int frames) {
        recName = name;
        rec = 0;
        recLeft = frames;
    }

    static void scene(String id, double x, double y, double z, long time, int shotTick, String shot, int recFrames) {
        STEPS.add(new Step(5, c -> server(c, s -> { tp(s, x, y, z, 0, 0); s.getOverworld().setTimeOfDay(time); })));
        STEPS.add(new Step(80, c -> {
            Cine.play(id);
            if (recFrames > 0) record(id, recFrames);
        }));
        STEPS.add(new Step(shotTick, c -> shot2(c, shot)));
        STEPS.add(new Step(Math.max(10, com.yompix.glukhoman.story.Scenes.ticks(id) - shotTick + 10), c -> Cine.stop()));
    }

    static void shot2(MinecraftClient c, String name) {
        ScreenshotRecorder.saveScreenshot(c.runDirectory, "g2_" + name + ".png", c.getFramebuffer(), t -> Glukhoman.LOG.info("AUTOTEST снимок {}", name));
    }

    static void shots2() {
        STEPS.add(new Step(100, c -> {
            var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                    net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
            var info = new net.minecraft.world.level.LevelInfo("GlkShots2", net.minecraft.world.GameMode.ADVENTURE, false,
                    net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.GameRules(), data);
            var key = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD_PRESET, Glukhoman.id("taiga"));
            c.createIntegratedServerLoader().createAndStart(info.getLevelName(), info, net.minecraft.world.gen.GeneratorOptions.createRandom(),
                    drm -> drm.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(key).createDimensionsRegistryHolder(),
                    new net.minecraft.client.gui.screen.TitleScreen());
        }));
        wait(c -> c.player != null && c.currentScreen != null, 1500);
        STEPS.add(new Step(40, c -> shot2(c, "00_start_screen")));
        STEPS.add(new Step(5, c -> {
            c.setScreen(null);
            server(c, s -> Story.chooseDifficulty(pl(s), 1));
        }));
        STEPS.add(new Step(110, c -> shot2(c, "01_intro_cutscene")));
        STEPS.add(new Step(200, c -> Cine.stop()));
        scene("knock", -12, 65, 129, 18000, 175, "02_knock_headturn", 200);
        scene("village", 0, 65, 120, 18000, 150, "03_village_onetake", 0);
        scene("corridor", -4, 65, 246 * -1, 18000, 105, "04_corridor_run", 160);
        scene("anna", -110, 25, -219, 18000, 110, "06_anna", 0);
        scene("bunker", 14, 42, -252, 18000, 90, "07_bunker_choir", 0);
        scene("endA", 0, 65, -218, 1000, 80, "08_end_silence", 0);
        scene("endB", -12, 65, 130, 6000, 190, "09_end_echo", 0);
        // скример отдельно
        STEPS.add(new Step(10, c -> server(c, s -> { tp(s, -12, 25, -173, -90, 0); s.getOverworld().setTimeOfDay(18000); })));
        STEPS.add(new Step(60, c -> Jumpscare.start("listener")));
        STEPS.add(new Step(5, c -> shot2(c, "05_jumpscare_listener")));
        // крупные планы в игре
        STEPS.add(new Step(40, c -> server(c, s -> { Story.jump(3); })));
        STEPS.add(new Step(60, c -> server(c, s -> { clearPoles(s); tp(s, 3, 0, -60, 180, -12); var p = (com.yompix.glukhoman.entity.PoleEntity) spawnRet(s, GContent.POLE, 3, 0, -65); })));
        STEPS.add(new Step(190, c -> { GConfig.get().brightness = 1; GlukhomanClient.sanity = 25; shot2(c, "10_pole_closeup"); }));
        STEPS.add(new Step(5, c -> server(c, s -> { clearPoles(s); tp(s, -12, 65, 88, 180, 15); spawnAt(s, GContent.LISTENER, -12, 65, 84); })));
        STEPS.add(new Step(80, c -> shot2(c, "11_listener_closeup")));
        STEPS.add(new Step(5, c -> server(c, s -> { tp(s, 6, 0, 108, 180, 0); var d = (com.yompix.glukhoman.entity.DoppelEntity) spawnRet(s, GContent.DOPPEL, 2, 0, 96); if (d != null) d.setMimic("@anna"); })));
        STEPS.add(new Step(60, c -> { GlukhomanClient.sanity = 60; shot2(c, "12_doppel_anna"); }));
        STEPS.add(new Step(5, c -> server(c, s -> { tp(s, -11, 65, 128, 90, 12); })));
        STEPS.add(new Step(60, c -> shot2(c, "13_photo_doll")));
        STEPS.add(new Step(5, c -> server(c, s -> { Story.jump(5); })));
        STEPS.add(new Step(60, c -> server(c, s -> { tp(s, 14, 42, -254, 180, -8); spawnAt(s, GContent.CHOIR, 14, 42, -266); })));
        STEPS.add(new Step(55, c -> { GlukhomanClient.sanity = 20; shot2(c, "14_choir_closeup"); }));
        STEPS.add(new Step(40, c -> c.scheduleStop()));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (rec >= 0 && recLeft-- > 0) {
                ScreenshotRecorder.saveScreenshot(c.runDirectory, String.format("rec_%s_%04d.png", recName, rec++), c.getFramebuffer(), t -> {});
                if (recLeft == 0) rec = -1;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(Autotest::tick);
    }

    /** Проверка моделей: облёт и крупный план каждого существа днём. */
    static void models() {
        newWorld();
        wait(c -> c.player != null && c.currentScreen != null, 1500);
        STEPS.add(new Step(10, c -> {
            c.setScreen(null);
            GlukhomanClient.askedChoose = false;   // сюжет не запускаем — только смотрим модели
        }));
        String only = System.getProperty("glukhoman.models", "pole,listener,choir");
        for (String n : only.split(",")) {
            STEPS.add(new Step(10, c -> server(c, s -> { tp(s, 0, 65, -218, 180, 0); s.getOverworld().setTimeOfDay(6000); })));
            STEPS.add(new Step(80, c -> Cine.play("tt_" + n)));
            for (int[] k : new int[][]{{20, 0}, {40, 1}, {40, 2}, {50, 3}, {70, 4}, {40, 5}, {10, 6}}) {
                final int idx = k[1];
                STEPS.add(new Step(k[0], c -> shot2(c, n + "_" + idx)));
            }
            STEPS.add(new Step(30, c -> Cine.stop()));
        }
        STEPS.add(new Step(20, c -> c.scheduleStop()));
        ClientTickEvents.END_CLIENT_TICK.register(Autotest::tick);
    }

    /** Съёмка трейлера: каждая сцена — отдельный клип в /tmp/claude-0/glukhoman-trailer/clips/. */
    static void trailer() {
        boolean vert = Boolean.getBoolean("glukhoman.vertical");
        String dir = "/tmp/claude-0/glukhoman-trailer/clips/" + (vert ? "v_" : "h_");
        newWorld();
        wait(c -> c.player != null && c.currentScreen != null, 1500);
        STEPS.add(new Step(10, c -> {
            c.setScreen(null);
            GlukhomanClient.askedChoose = false;
            GConfig.get().fewerScares = true;   // скримеры в кадре — своими сценами, без экрана-вставки
            Cine.fovScale = vert ? 1.45f : 1f;
            net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(ctx -> Recorder.frame());
        }));
        Object[][] shots = vert ? new Object[][]{
                {"tr_scare", 2, 65, -241, 18000L, 4.0}, {"knock", -3, 65, 125, 18000L, 10.0}, {"village", 0, 65, 120, 18000L, 8.0},
                {"corridor", 2, 65, -241, 18000L, 6.7}, {"tr_listener", -120, 25, -173, 18000L, 4.6}, {"tr_doppel", 1, 65, 100, 18000L, 5.0},
                {"bunker", 14, 42, -252, 18000L, 7.0}}
                : new Object[][]{
                {"intro", 0, 65, 238, 12800L, 7.0}, {"village", 0, 65, 120, 18000L, 11.0}, {"tr_radio", -12, 65, 128, 18000L, 5.0},
                {"knock", -3, 65, 125, 18000L, 10.0}, {"tr_path", 1, 0, -60, 15000L, 5.0}, {"corridor", 2, 65, -241, 18000L, 6.7},
                {"tr_listener", -120, 25, -173, 18000L, 4.6}, {"anna", -110, 25, -219, 18000L, 7.0}, {"tr_doppel", 1, 65, 100, 18000L, 5.0},
                {"bunker", 14, 42, -252, 18000L, 7.0}, {"tr_scare", 2, 65, -241, 18000L, 4.0},
                {"tr_gore", -9, 65, -93, 18000L, 6.0}, {"death_pole", 1, 0, -60, 15000L, 3.6}};
        String only = System.getProperty("glukhoman.only", "");
        for (Object[] sh : shots) {
            String id = (String) sh[0];
            if (!only.isEmpty() && !java.util.Arrays.asList(only.split(",")).contains(id)) continue;
            double limit = (Double) sh[5];
            STEPS.add(new Step(10, c -> server(c, s -> { tp(s, (Integer) sh[1], (Integer) sh[2], (Integer) sh[3], 0, 0); s.getOverworld().setTimeOfDay((Long) sh[4]); })));
            STEPS.add(new Step(140, c -> {}));
            wait(Autotest::ready, 6000);   // ждём, пока чанки вокруг придут и построятся
            STEPS.add(new Step(40, c -> {
                Recorder.start(dir + id + ".mp4", 30);
                Cine.play(id);
            }));
            wait(c -> !Cine.active || Cine.time() >= limit, 200000);
            STEPS.add(new Step(2, c -> {
                Recorder.stop();
                Cine.stop();
            }));
        }
        STEPS.add(new Step(20, c -> c.scheduleStop()));
        ClientTickEvents.END_CLIENT_TICK.register(Autotest::tick);
    }

    static boolean ready(MinecraftClient c) {
        if (c.player == null || c.world == null) return false;
        int cx = c.player.getBlockX() >> 4, cz = c.player.getBlockZ() >> 4;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (!c.world.getChunkManager().isChunkLoaded(cx + dx, cz + dz)) return false;
            if (!c.worldRenderer.isRenderingReady(new BlockPos((cx + dx) * 16 + 8, c.player.getBlockY(), (cz + dz) * 16 + 8))) return false;
        }
        return c.worldRenderer.isTerrainRenderComplete();
    }

    static void newWorld() {
        STEPS.add(new Step(100, c -> {
            var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                    net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
            var info = new net.minecraft.world.level.LevelInfo("GlkT" + System.currentTimeMillis() % 100000, net.minecraft.world.GameMode.ADVENTURE, false,
                    net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.GameRules(), data);
            var key = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD_PRESET, Glukhoman.id("taiga"));
            c.createIntegratedServerLoader().createAndStart(info.getLevelName(), info, net.minecraft.world.gen.GeneratorOptions.createRandom(),
                    drm -> drm.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(key).createDimensionsRegistryHolder(),
                    new net.minecraft.client.gui.screen.TitleScreen());
        }));
    }

    static Entity spawnRet(MinecraftServer s, net.minecraft.entity.EntityType<? extends Entity> t, int x, int y, int z) {
        Entity e = t.create(s.getOverworld());
        if (e == null) return null;
        if (y == 0) y = s.getOverworld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        e.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0, 0);
        s.getOverworld().spawnEntity(e);
        return e;
    }

    static void wait(java.util.function.Predicate<MinecraftClient> cond, int max) {
        STEPS.add(new Step(-max, c -> {}));
        CONDS.put(STEPS.size() - 1, cond);
    }

    static final java.util.Map<Integer, java.util.function.Predicate<MinecraftClient>> CONDS = new java.util.HashMap<>();

    static void tick(MinecraftClient c) {
        if (idx >= STEPS.size()) return;
        Step s = STEPS.get(idx);
        timer++;
        var cond = CONDS.get(idx);
        if (cond != null) {
            if (!cond.test(c) && timer < -s.delay()) return;
        } else if (timer < s.delay()) return;
        timer = 0;
        try {
            s.act().accept(c);
        } catch (Exception e) {
            Glukhoman.LOG.error("AUTOTEST шаг " + idx, e);
        }
        idx++;
    }

    static void server(MinecraftClient c, Consumer<MinecraftServer> r) {
        MinecraftServer s = c.getServer();
        if (s != null) s.execute(() -> r.accept(s));
    }

    static ServerPlayerEntity pl(MinecraftServer s) {
        return s.getPlayerManager().getPlayerList().get(0);
    }

    static void tp(MinecraftServer s, double x, double y, double z, float yaw, float pitch) {
        ServerPlayerEntity p = pl(s);
        if (y == 0) y = s.getOverworld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
        p.teleport(s.getOverworld(), x + 0.5, y, z + 0.5, yaw, pitch);
    }

    static void spawnAt(MinecraftServer s, net.minecraft.entity.EntityType<? extends Entity> t, int x, int y, int z) {
        Entity e = t.create(s.getOverworld());
        if (e == null) return;
        if (y == 0) y = s.getOverworld().getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        e.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0, 0);
        if (e instanceof com.yompix.glukhoman.entity.PoleEntity pe) pe.lookAtPlayer(pl(s));
        s.getOverworld().spawnEntity(e);
    }

    static void clearPoles(MinecraftServer s) {
        for (var e : s.getOverworld().getEntitiesByClass(com.yompix.glukhoman.entity.PoleEntity.class, new net.minecraft.util.math.Box(BlockPos.ORIGIN).expand(600), x -> true)) e.discard();
    }

    static void look(MinecraftClient c, float yaw, float pitch) {
        c.player.setYaw(yaw);
        c.player.setPitch(pitch);
    }

    static void lightOn(MinecraftClient c) {
        server(c, s -> {
            ServerPlayerEntity p = pl(s);
            var fl = new net.minecraft.item.ItemStack(GContent.FLASHLIGHT);
            p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, fl);
            p.getInventory().insertStack(new net.minecraft.item.ItemStack(GContent.BATTERY, 4));
            if (!com.yompix.glukhoman.horror.Light.isOn(p)) com.yompix.glukhoman.horror.Light.toggle(p);
        });
    }

    static void shot(MinecraftClient c, String name) {
        ScreenshotRecorder.saveScreenshot(c.runDirectory, "glk_" + name + ".png", c.getFramebuffer(), t -> Glukhoman.LOG.info("AUTOTEST снимок {}", name));
    }
}
