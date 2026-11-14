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
            Glukhoman.LOG.info("AUTOTEST друг: глава {} «{}», цель «{}»", GlukhomanClient.chapter, GlukhomanClient.title, GlukhomanClient.objective);
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
