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
        String mode = System.getProperty("glukhoman.autotest", "");
        if (mode.equals("shots") || mode.equals("trailer") || mode.equals("models") || mode.equals("walk")) System.setProperty("glukhoman.freeze", "true");
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
        if ("walk".equals(System.getProperty("glukhoman.autotest"))) {
            walk();
            return;
        }
        if ("models".equals(System.getProperty("glukhoman.autotest"))) {
            models();
            return;
        }
        if (!"shots".equals(System.getProperty("glukhoman.autotest"))) return;
        shots();
    }

    static void cam(MinecraftClient c, boolean up) {
        server(c, s -> {
            ServerPlayerEntity p = pl(s);
            if (!p.getMainHandStack().isOf(GContent.CAMCORDER)) {
                p.getInventory().selectedSlot = 0;
                p.getInventory().setStack(0, new net.minecraft.item.ItemStack(GContent.CAMCORDER));
            }
            if (up && !com.yompix.glukhoman.story.Cam.up(p)) com.yompix.glukhoman.story.Cam.raise(p);
            if (!up) com.yompix.glukhoman.story.Cam.lower(p, false);
        });
    }

    /** 0.4 «Порожняк»: снимки ключевых мест и механик (видоискатель / глаза). */
    static void shots() {
        newWorld();
        wait(c -> c.player != null && c.currentScreen != null, 1500);
        STEPS.add(new Step(60, c -> shot(c, "00_difficulty")));
        STEPS.add(new Step(5, c -> {
            c.setScreen(null);
            server(c, s -> Story.chooseDifficulty(pl(s), 1));
        }));
        wait(Autotest::ready, 4000);
        STEPS.add(new Step(160, c -> shot(c, "01_prologue_viewfinder")));
        STEPS.add(new Step(5, c -> server(c, s -> tp(s, 10, 66, 19, 160, -4))));
        STEPS.add(new Step(80, c -> shot(c, "02_polya_window")));
        STEPS.add(new Step(5, c -> { cam(c, false); server(c, s -> tp(s, 61, 66, 16, -90, 12)); }));
        wait(Autotest::ready, 4000);
        STEPS.add(new Step(100, c -> shot(c, "03_rails_end_eye")));
        STEPS.add(new Step(5, c -> cam(c, true)));
        STEPS.add(new Step(60, c -> shot(c, "04_rails_end_camera")));
        // поезд у перрона: в камере есть, глазами — пусто
        STEPS.add(new Step(5, c -> server(c, s -> {
            var tr = spawnRet(s, GContent.TRAIN, 8, 66, 16);
            if (tr != null) tr.setYaw(90);
            tp(s, 24, 66, 21, 112, 4);
        })));
        wait(Autotest::ready, 4000);
        STEPS.add(new Step(80, c -> shot(c, "05_train_viewfinder")));
        STEPS.add(new Step(5, c -> cam(c, false)));
        STEPS.add(new Step(30, c -> shot(c, "06_train_eye_empty")));
        // Обходчик на краю перрона: глазами есть, в камере нет
        STEPS.add(new Step(5, c -> server(c, s -> {
            for (var e : s.getOverworld().getEntitiesByClass(com.yompix.glukhoman.entity.TrainEntity.class, new net.minecraft.util.math.Box(BlockPos.ORIGIN).expand(600), x -> true)) e.discard();
            var w = (com.yompix.glukhoman.entity.WatcherEntity) spawnRet(s, GContent.WATCHER, -11, 66, 19);
            if (w != null) { w.posted = true; w.setYaw(-90); w.setHeadYaw(-90); }
            tp(s, -2, 66, 19, 90, -6);
        })));
        STEPS.add(new Step(100, c -> shot(c, "07_watcher_eye")));
        STEPS.add(new Step(5, c -> cam(c, true)));
        STEPS.add(new Step(40, c -> shot(c, "08_watcher_not_in_frame")));
        STEPS.add(new Step(5, c -> { cam(c, false); server(c, s -> tp(s, -7, 66, 19.3, 90, -18)); }));
        STEPS.add(new Step(60, c -> shot(c, "09_watcher_close")));
        // табло в дежурке
        STEPS.add(new Step(5, c -> server(c, s -> {
            var st = StoryState.get(s.getOverworld());
            int i = 0;
            for (long l : st.lamps.get("board")) {
                var q = BlockPos.fromLong(l);
                var bs = s.getOverworld().getBlockState(q);
                s.getOverworld().setBlockState(q, bs.with(com.yompix.glukhoman.block.LampBlock.LIT, i == 2 || i == 3 || i == 7), 3);
                i++;
            }
            tp(s, -5, 66, 25, 90, 8);
        })));
        STEPS.add(new Step(80, c -> shot(c, "10_board")));
        // утро: дом бабы Поли заколочен, жители
        STEPS.add(new Step(5, c -> server(c, s -> Story.jump(2))));
        STEPS.add(new Step(80, c -> server(c, s -> tp(s, 8, 65, 40, -90, 0))));
        wait(Autotest::ready, 4000);
        STEPS.add(new Step(60, c -> shot(c, "11_morning_boarded")));
        STEPS.add(new Step(5, c -> server(c, s -> tp(s, 4, 66, 19, 120, 6))));
        STEPS.add(new Step(60, c -> shot(c, "12_platform_day_residents")));
        STEPS.add(new Step(5, c -> server(c, s -> com.yompix.glukhoman.story.Tapes.open(pl(s)))));
        STEPS.add(new Step(30, c -> shot(c, "13_tapes")));
        STEPS.add(new Step(5, c -> { c.setScreen(null); server(c, s -> Story.interact(pl(s), StoryState.get(s.getOverworld()).find("box"), "box")); }));
        STEPS.add(new Step(30, c -> shot(c, "14_box_choice")));
        STEPS.add(new Step(5, c -> c.setScreen(null)));
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
        STEPS.add(new Step(200, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: глава {} «{}», цель «{}», камера у глаза = {}", GlukhomanClient.chapter, GlukhomanClient.title, GlukhomanClient.objective, Viewfinder.up);
            shot(c, "11_mp_prologue");
            c.player.networkHandler.sendChatCommand("glukhoman chapter 2");
        }));
        STEPS.add(new Step(200, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: глава {} «{}», цель «{}»", GlukhomanClient.chapter, GlukhomanClient.title, GlukhomanClient.objective);
            c.player.networkHandler.sendChatCommand("tp @s 4 66 21 0 30");
        }));
        STEPS.add(new Step(40, c -> c.interactionManager.interactBlock(c.player, net.minecraft.util.Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(new net.minecraft.util.math.Vec3d(4.5, 66.5, 20.5), net.minecraft.util.math.Direction.NORTH, new BlockPos(4, 66, 20), false))));
        STEPS.add(new Step(40, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: ящик → экран выбора = {}", c.currentScreen instanceof com.yompix.glukhoman.client.screen.AskScreen);
            shot(c, "12_mp_box");
            var m = com.yompix.glukhoman.net.Net.msg("answer");
            m.putString("id", "box");
            m.putInt("i", 1);
            GlukhomanClient.send(m);
            c.setScreen(null);
        }));
        STEPS.add(new Step(200, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: после ящика цель «{}», субтитры {}", GlukhomanClient.objective, GlukhomanClient.SUBS.isEmpty() ? "нет" : GlukhomanClient.SUBS.get(GlukhomanClient.SUBS.size() - 1)[1]);
            c.player.networkHandler.sendChatCommand("glukhoman night");
        }));
        STEPS.add(new Step(300, c -> {
            Glukhoman.LOG.info("AUTOTEST сеть: ночь — шаг {}, цель «{}»", GlukhomanClient.step, GlukhomanClient.objective);
            shot(c, "13_mp_night");
        }));
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
        wait(c -> GlukhomanClient.chapter == 2, 2500);
        STEPS.add(new Step(20, c -> {
            Glukhoman.LOG.info("AUTOTEST друг: цель обновилась «{}»", GlukhomanClient.objective);
            shot(c, "21_friend_sync");
        }));
        wait(c -> GlukhomanClient.step.startsWith("n2"), 2500);
        STEPS.add(new Step(80, c -> {
            Glukhoman.LOG.info("AUTOTEST друг: глава {} «{}», цель «{}»", GlukhomanClient.chapter, GlukhomanClient.title, GlukhomanClient.objective);
            shot(c, "22_friend_night");
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

    /** walk_demo: 10 с от первого лица с камерой — 5 с камера опущена (руки), 5 с у глаза; шаг, поворот, бег, спуск с перрона. */
    static double walkT0 = -1;

    static void walk() {
        newWorld();
        wait(c -> c.player != null && c.currentScreen != null, 1500);
        STEPS.add(new Step(5, c -> {
            c.setScreen(null);
            server(c, s -> Story.chooseDifficulty(pl(s), 1));
        }));
        wait(Autotest::ready, 3000);
        STEPS.add(new Step(80, c -> {
            server(c, s -> {
                cam0(s, false);
                tp(s, -7, 66, 19, -90, 6);
                s.getTickManager().setTickRate(6f);   // медленнее мир — больше кадров на игровую секунду
            });
            c.options.getPerspective();
        }));
        STEPS.add(new Step(40, c -> {
            Recorder.startFree("/tmp/claude-0/glukhoman-trailer3/walk_raw.mp4", 30);
            walkT0 = (c.world.getTime()) / 20.0;
            net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.START.register(ctx -> {
                if (walkT0 < 0 || c.player == null) return;
                double t = (c.world.getTime() + c.getRenderTickCounter().getTickDelta(false)) / 20.0 - walkT0;
                // взгляд: вдоль перрона, на 3-й секунде резко вправо (к дому бабы Поли), потом обратно, на 7-й — влево к линии
                float yaw = -90 + (float) (-60 * smooth(t, 2.4, 2.9) + 60 * smooth(t, 3.8, 4.7) + 40 * smooth(t, 7.0, 7.4) - 40 * smooth(t, 8.4, 9.2));
                c.player.setYaw(yaw);
                c.player.prevYaw = yaw;
                c.player.setPitch(6 + (float) (Math.sin(t * 0.7) * 2));
                c.player.prevPitch = c.player.getPitch();
                c.options.forwardKey.setPressed(t < 9.6 && !(t > 4.2 && t < 5.3));   // постоял, поднял камеру
                c.options.sprintKey.setPressed(t > 6.0 && t < 7.8);
                if (t > 4.6 && !Viewfinder.up) server(c, s -> cam0(s, true));
            });
        }));
        STEPS.add(new Step(2, c -> {}));
        wait(c -> walkT0 >= 0 && c.world.getTime() / 20.0 - walkT0 > 10.2, 20000);
        STEPS.add(new Step(2, c -> {
            Recorder.stop();
            walkT0 = -1;
            c.options.forwardKey.setPressed(false);
            c.options.sprintKey.setPressed(false);
            server(c, s -> s.getTickManager().setTickRate(20f));
        }));
        STEPS.add(new Step(20, c -> c.scheduleStop()));
        ClientTickEvents.END_CLIENT_TICK.register(Autotest::tick);
    }

    static double smooth(double t, double a, double b) {
        double u = Math.max(0, Math.min(1, (t - a) / (b - a)));
        return u * u * (3 - 2 * u);
    }

    static void cam0(MinecraftServer s, boolean up) {
        ServerPlayerEntity p = pl(s);
        if (!p.getMainHandStack().isOf(GContent.CAMCORDER)) {
            p.getInventory().selectedSlot = 0;
            p.getInventory().setStack(0, new net.minecraft.item.ItemStack(GContent.CAMCORDER));
        }
        if (up && !com.yompix.glukhoman.story.Cam.up(p)) com.yompix.glukhoman.story.Cam.raise(p);
        if (!up) com.yompix.glukhoman.story.Cam.lower(p, false);
    }

    /** Проверка моделей: облёт и крупный план каждого существа днём. */
    static void models() {
        newWorld();
        wait(c -> c.player != null && c.currentScreen != null, 1500);
        STEPS.add(new Step(10, c -> {
            c.setScreen(null);
            GlukhomanClient.askedChoose = false;   // сюжет не запускаем — только смотрим модели
        }));
        String only = System.getProperty("glukhoman.models", "watcher");
        for (String n : only.split(",")) {
            STEPS.add(new Step(10, c -> server(c, s -> { tp(s, 40, 0, 26, 180, 0); s.getOverworld().setTimeOfDay(6000); })));
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
        String dir = "/tmp/claude-0/glukhoman-trailer3/clips/";
        newWorld();
        wait(c -> c.player != null && c.currentScreen != null, 1500);
        STEPS.add(new Step(10, c -> {
            c.setScreen(null);
            GlukhomanClient.askedChoose = false;
            GConfig.get().fewerScares = true;   // скримеры в кадре — своими сценами, без экрана-вставки
            Cine.fovScale = vert ? 1.45f : 1f;
            net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END.register(ctx -> Recorder.frame());
        }));
        Object[][] shots = {
                {"t3_rails", 0, 66, 19, 18000L, 6.5}, {"t3_cut", 58, 66, 16, 18000L, 5.0}, {"t3_bell", 24, 66, 22, 18000L, 6.0},
                {"t3_train", 40, 66, 20, 18000L, 8.0}, {"t3_updown", 14, 66, 20, 18000L, 5.5}, {"t3_vanish", 8, 66, 20, 18000L, 4.5},
                {"t3_board", -5, 66, 25, 18000L, 5.0}, {"t3_rewind", 2, 66, 20, 18000L, 6.2}, {"t3_rollcall", 2, 66, 20, 18000L, 5.0},
                {"t3_draisine", 112, 66, 16, 18000L, 4.5}, {"t3_look", 20, 66, 21, 18000L, 4.5}, {"t3_stop", 4, 66, 19, 18000L, 3.2},
                {"t3_bogie", 10, 66, 19, 18000L, 4.0}, {"t3_house", 8, 65, 40, 1000L, 5.0}};
        String only = System.getProperty("glukhoman.only", "");
        for (Object[] sh : shots) {
            String id = (String) sh[0];
            if (!only.isEmpty() && !java.util.Arrays.asList(only.split(",")).contains(id)) continue;
            double limit = (Double) sh[5];
            STEPS.add(new Step(10, c -> server(c, s -> {
                tp(s, (Integer) sh[1], (Integer) sh[2], (Integer) sh[3], 0, 0);
                s.getOverworld().setTimeOfDay((Long) sh[4]);
                var st = StoryState.get(s.getOverworld());
                Story.clearResidents();
                for (String g : new String[]{"station", "dispatch"}) {
                    var l = st.lamps.get(g);
                    if (l != null) for (long q : l) {
                        var bp = BlockPos.fromLong(q);
                        var bs = s.getOverworld().getBlockState(bp);
                        if (bs.getBlock() instanceof com.yompix.glukhoman.block.LampBlock)
                            s.getOverworld().setBlockState(bp, bs.with(com.yompix.glukhoman.block.LampBlock.LIT, (Long) sh[4] > 12000), 3);
                    }
                }
                if (id.equals("t3_house") && !st.flag("boarded")) {
                    st.set("boarded");
                    com.yompix.glukhoman.world.MapBuilder.boardUp(s.getOverworld(), st);
                }
            })));
            STEPS.add(new Step(140, c -> {}));
            wait(Autotest::ready, 900);   // ждём, пока чанки вокруг придут и построятся (не дольше 45 с)
            STEPS.add(new Step(200, c -> {}));
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
