package com.yompix.lifecraft.client;

import com.yompix.lifecraft.client.screen.OnlineCasinoScreen;
import com.yompix.lifecraft.client.screen.PhoneScreen;
import com.yompix.lifecraft.client.story.YompixSplash;
import com.yompix.lifecraft.entity.CitizenEntity;
import com.yompix.lifecraft.life.Life;
import com.yompix.lifecraft.story.Npcs;
import com.yompix.lifecraft.story.Story;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: запускается с -Dlifecraft.autotest=true, сам проходит начало сюжета,
 * открывает экраны и делает скриншоты, чтобы проверить мод без человека за клавиатурой.
 */
public final class DevAutotest {
    private DevAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, timer;
    private static final String P = "Dev";

    public static void init() {
        if (System.getProperty("lifecraft.autotest") == null) return;
        String mode = System.getProperty("lifecraft.autotest");
        if (mode.startsWith("mp")) {
            // игра по сети: A проходит разговоры пробелом, B просто смотрит; оба делают снимки
            YompixSplash.markShown();
            boolean a = mode.equals("mpA");
            for (int i = 0; i < 14; i++) {
                if (a) for (int k = 0; k < 6; k++) key(25, GLFW.GLFW_KEY_SPACE);
                else run(150, c -> {});
                shot(10, mode + "_" + i);
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (System.getProperty("lifecraft.autotest").equals("taxi")) {
            taxiOnly();
            return;
        }
        YompixSplash.markShown();
        run(80, c -> c.setScreen(new YompixSplash(null)));
        shot(30, "00_splash_logo");
        shot(70, "00b_splash_title");
        run(2, c -> c.setScreen(null));
        // сюжет стартует сам: кат-сцена облёта
        shot(100, "01_intro_cutscene");
        shot(120, "02_intro_cutscene2");
        key(5, GLFW.GLFW_KEY_SPACE);            // пропустить кат-сцену
        shot(40, "03_chapter_title");
        shot(140, "04_mom_dialog");
        spaces(12);
        run(5, c -> pressKey(c, GLFW.GLFW_KEY_1));
        spaces(4);
        shot(30, "05_objective_dad");
        talk(5, "dad");
        shot(30, "06_dad_dialog");
        spaces(10);
        run(10, c -> c.setScreen(new PhoneScreen("story")));
        shot(30, "07_phone_story");
        run(5, c -> c.setScreen(null));
        shot(20, "08_hud_go_school");
        // дальше по сюжету — к школе, знакомство с Тёмой
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var v = Story.place(p.getServerWorld(), "school_front");
            p.teleport(p.getServerWorld(), v.x, v.y, v.z + 3, 180, 0);
        });
        shot(80, "09_tema_dialog");
        spaces(8);
        run(5, c -> pressKey(c, GLFW.GLFW_KEY_2));
        spaces(4);
        shot(40, "10_gleb_target");
        // ряд жителей — проверить 3D-причёски, бороды, рост и движения
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var w = p.getServerWorld();
            double bx = 60, bz = 60;
            int y = Story.topY(w, (int) bx, (int) bz);
            for (int i = 0; i < 18; i++) {
                CitizenEntity c = new CitizenEntity(com.yompix.lifecraft.ModContent.CITIZEN, w);
                int skin = i < 8 ? 8 + i : CitizenEntity.RANDOM_FROM + (i - 8) * 2;
                c.setup(skin, CitizenEntity.Role.CITIZEN, true, CitizenEntity.SKINS[skin]);
                c.refreshPositionAndAngles(bx - 13 + i * 1.5, y, bz, 0, 0);
                c.setHeadYaw(0);
                c.lockActivity(CitizenEntity.Activity.values()[i % 8]);
                if (i % 5 == 3) c.setScale(0.7f);
                w.spawnEntity(c);
            }
            p.teleport(w, bx, y + 1, bz + 6, 180, 8);
        });
        shot(80, "11_lineup");
        server(5, s -> s.getPlayerManager().getPlayer(P).teleport(s.getOverworld(), 60, Story.topY(s.getOverworld(), 60, 60) + 1, 63, 180, 15));
        shot(40, "12_lineup_close");
        // взрослый — онлайн-казино и награды
        server(5, s -> {
            var d = Life.get(s.getPlayerManager().getPlayer(P));
            d.edu = 12;
            d.money = 5000;
            Life.sync(s.getPlayerManager().getPlayer(P));
        });
        run(10, c -> c.setScreen(new OnlineCasinoScreen()));
        run(10, c -> LifeCraftClient.send("bj", nbt("action", "deal", 10)));
        shot(30, "13_blackjack");
        run(5, c -> click(c, "crash"));
        run(5, c -> LifeCraftClient.send("crash_start", nbt(null, null, 10)));
        shot(70, "14_crash");
        run(30, c -> click(c, "coin"));
        run(5, c -> LifeCraftClient.send("coin", nbt(null, null, 10)));
        shot(15, "15_coin_spin");
        run(5, c -> c.setScreen(new PhoneScreen("trophy")));
        shot(30, "16_trophies");
        run(5, c -> c.setScreen(new PhoneScreen(null)));
        shot(30, "17_phone_home");
        // 0.3: карта, такси с автопилотом, вид из салона, «!» над жителем
        run(5, c -> c.setScreen(null));
        server(5, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), "time set 4000"));
        run(5, c -> c.setScreen(new com.yompix.lifecraft.client.screen.MapScreen()));
        shot(40, "20_map");
        run(5, c -> c.setScreen(null));
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var v = Story.place(p.getServerWorld(), "school_front");
            p.teleport(p.getServerWorld(), v.x, v.y, v.z + 4, 180, 5);
            com.yompix.lifecraft.life.Taxi.call(p, "shop_door");
        });
        shot(200, "21_taxi_coming");
        shot(300, "22_taxi_arrived");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var cars = p.getServerWorld().getEntitiesByClass(com.yompix.lifecraft.entity.CarEntity.class, p.getBoundingBox().expand(30), c -> c.isTaxi() && c.hasPassengers());
            if (!cars.isEmpty()) p.startRiding(cars.get(0), true);
        });
        shot(120, "23_inside_taxi");
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
        shot(120, "24_taxi_driving");
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            p.stopRiding();
            var w = p.getServerWorld();
            var list = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(60), c -> c.getStoryId().isEmpty() && c.getRole() == CitizenEntity.Role.CITIZEN);
            if (!list.isEmpty()) {
                var c = list.get(0);
                c.setQuest(true);
                p.teleport(w, c.getX(), c.getY(), c.getZ() + 4, 180, 0);
            }
        });
        shot(60, "25_quest_marker");
        // кадры для фона главного меню: ночной неон, казино, город сверху, школа днём
        run(5, c -> c.setScreen(null));
        cmd(5, "gamemode spectator " + P);
        cmd(5, "time set 14500");
        run(5, c -> c.options.hudHidden = true);
        cmd(5, "tp " + P + " -38 -53 20 -110 10");
        shot(260, "bg1");
        cmd(5, "tp " + P + " 4 -50 30 0 12");
        shot(80, "bg2");
        cmd(5, "tp " + P + " 30 -25 70 135 30");
        shot(100, "bg3");
        cmd(5, "time set 3000");
        cmd(5, "tp " + P + " 6 -52 24 160 6");
        shot(80, "bg4");
        run(5, c -> c.options.hudHidden = false);
        run(5, c -> c.setScreen(new com.yompix.lifecraft.client.menu.LifeMenuScreen()));
        shot(60, "18_main_menu");
        run(5, c -> c.setScreen(new com.yompix.lifecraft.client.menu.LifePauseScreen()));
        shot(30, "19_pause");
        run(20, MinecraftClient::scheduleStop);
    }

    private static void taxiOnly() {
        YompixSplash.markShown();
        cmd(200, "time set 4000");
        cmd(5, "lifecraft money " + P + " 5000");
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var v = Story.place(p.getServerWorld(), "school_front");
            p.teleport(p.getServerWorld(), v.x, v.y, v.z + 6, 180, 5);
            com.yompix.lifecraft.life.Taxi.call(p, "shop_door");
        });
        for (int i = 0; i < 6; i++) shot(100, "t" + i);
        server(5, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            var cars = p.getServerWorld().getEntitiesByClass(com.yompix.lifecraft.entity.CarEntity.class, p.getBoundingBox().expand(60), c -> c.isTaxi() && c.hasPassengers());
            if (!cars.isEmpty()) p.startRiding(cars.get(0), true);
        });
        for (int i = 6; i < 12; i++) shot(100, "t" + i);
        run(20, MinecraftClient::scheduleStop);
    }

    private static net.minecraft.nbt.NbtCompound nbt(String k, String v, int bet) {
        var n = new net.minecraft.nbt.NbtCompound();
        if (k != null) n.putString(k, v);
        n.putInt("bet", bet);
        return n;
    }

    private static void click(MinecraftClient c, String tab) {
        if (c.currentScreen instanceof OnlineCasinoScreen) {
            try {
                var f = OnlineCasinoScreen.class.getDeclaredField("tab");
                f.setAccessible(true);
                f.set(c.currentScreen, tab);
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static void pressKey(MinecraftClient c, int key) {
        if (c.currentScreen != null) c.currentScreen.keyPressed(key, 0, 0);
    }

    private static void key(int delay, int key) {
        run(delay, c -> pressKey(c, key));
    }

    private static void spaces(int n) {
        for (int i = 0; i < n; i++) key(14, GLFW.GLFW_KEY_SPACE);
    }

    private static void talk(int delay, String npc) {
        server(delay, s -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
            CitizenEntity c = Npcs.find(p.getServerWorld(), npc);
            if (c != null) Story.onTalk(p, c);
        });
    }

    private static void cmd(int delay, String command) {
        server(delay, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), command));
    }

    private static void server(int delay, Consumer<IntegratedServer> action) {
        STEPS.add(new Step(delay, c -> {
            IntegratedServer s = c.getServer();
            if (s != null) s.execute(() -> action.accept(s));
        }));
    }

    private static void run(int delay, Consumer<MinecraftClient> action) {
        STEPS.add(new Step(delay, action));
    }

    private static void shot(int delay, String name) {
        STEPS.add(new Step(delay, c -> ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), t -> {})));
    }

    static {
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (STEPS.isEmpty() || c.player == null || c.world == null) return;
            if (index < 0) {
                index = 0;
                timer = STEPS.get(0).delay();
            }
            if (index >= STEPS.size()) return;
            if (--timer > 0) return;
            STEPS.get(index).action().accept(c);
            index++;
            if (index < STEPS.size()) timer = STEPS.get(index).delay();
        });
    }
}
