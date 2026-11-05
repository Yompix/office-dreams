package com.yompix.lifecraft.client;

import com.yompix.lifecraft.client.screen.PhoneScreen;
import com.yompix.lifecraft.client.screen.RouletteScreen;
import com.yompix.lifecraft.client.screen.ShopScreen;
import com.yompix.lifecraft.client.screen.SlotsScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: запускается с -Dlifecraft.autotest=true, сам ходит по городу,
 * открывает экраны и делает скриншоты, чтобы проверить мод без человека за клавиатурой.
 */
public final class DevAutotest {
    private DevAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int index = -1, timer;

    public static void init() {
        if (System.getProperty("lifecraft.autotest") == null) return;
        String p = "Dev";
        cmd(200, "gamerule doDaylightCycle false");
        cmd(5, "time set 3000");
        cmd(5, "weather clear");
        cmd(5, "gamemode spectator " + p);
        cmd(5, "tp " + p + " 0 -22 105 180 28");
        shot(120, "01_city_overview");
        cmd(5, "tp " + p + " -30 -55 30 -60 8");
        cmd(2, "gamemode creative " + p);
        shot(80, "02_street");
        cmd(5, "tp " + p + " 0 -56 16 180 5");
        shot(80, "03_school_front");
        cmd(5, "tp " + p + " -10 -56 -6 180 10");
        shot(80, "04_classroom");
        run(5, c -> c.setScreen(new PhoneScreen(null)));
        shot(40, "05_phone_home");
        run(5, c -> c.setScreen(new PhoneScreen("map")));
        shot(40, "06_phone_map");
        run(5, c -> c.setScreen(new PhoneScreen("jobs")));
        shot(30, "07_phone_jobs");
        run(5, c -> c.setScreen(null));
        cmd(5, "tp " + p + " -10 -56 -10 180 10");
        server(5, s -> com.yompix.lifecraft.life.School.start(s.getPlayerManager().getPlayer(p)));
        shot(60, "08_lesson");
        for (int i = 0; i < 5; i++) {
            run(25, c -> {
                if (c.currentScreen != null) c.currentScreen.keyPressed(49, 0, 0);
            });
        }
        shot(60, "08b_lesson_result");
        run(5, c -> c.setScreen(new ShopScreen(false)));
        shot(30, "09_shop");
        cmd(5, "lifecraft edu " + p + " 12");
        cmd(5, "lifecraft money " + p + " 5000");
        run(20, c -> c.setScreen(new SlotsScreen()));
        shot(30, "10_slots");
        run(2, c -> {
            if (c.currentScreen instanceof SlotsScreen) c.currentScreen.keyPressed(32, 0, 0);
        });
        shot(12, "11_slots_spin");
        shot(60, "12_slots_result");
        run(5, c -> c.setScreen(new RouletteScreen()));
        shot(30, "13_roulette");
        run(5, c -> c.setScreen(null));
        cmd(5, "tp " + p + " 5 -56 34 0 5");
        shot(80, "14_casino");
        cmd(5, "tp " + p + " -52 -56 -53 200 10");
        shot(80, "15_house");
        cmd(5, "time set 15000");
        cmd(5, "tp " + p + " -38 -55 19 -100 15");
        shot(100, "16_night_street");
        server(5, s -> {
            var pl = s.getPlayerManager().getPlayer(p);
            var cars = pl.getServerWorld().getEntitiesByClass(com.yompix.lifecraft.entity.CarEntity.class, pl.getBoundingBox().expand(40), e -> true);
            if (!cars.isEmpty()) {
                var car = cars.get(0);
                pl.teleport(pl.getServerWorld(), car.getX() + Math.sin(Math.toRadians(car.getYaw())) * 6, car.getY() + 1.5, car.getZ() - Math.cos(Math.toRadians(car.getYaw())) * 6, car.getYaw() + 180, 15);
            }
        });
        shot(60, "17_car_night");
        server(5, s -> {
            var pl = s.getPlayerManager().getPlayer(p);
            var cars = pl.getServerWorld().getEntitiesByClass(com.yompix.lifecraft.entity.CarEntity.class, pl.getBoundingBox().expand(40), e -> true);
            if (!cars.isEmpty()) pl.startRiding(cars.get(0), true);
        });
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
        shot(60, "18_driving_night");
        cmd(5, "time set 6000");
        shot(40, "19_driving_day");
        run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
        run(20, c -> c.scheduleStop());
    }

    private static void cmd(int wait, String command) {
        server(wait, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), command));
    }

    private static void server(int wait, Consumer<IntegratedServer> action) {
        STEPS.add(new Step(wait, c -> {
            IntegratedServer s = c.getServer();
            if (s != null) s.execute(() -> action.accept(s));
        }));
    }

    private static void run(int wait, Consumer<MinecraftClient> action) {
        STEPS.add(new Step(wait, action));
    }

    private static void shot(int wait, String name) {
        STEPS.add(new Step(wait, c -> ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), t -> {})));
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
