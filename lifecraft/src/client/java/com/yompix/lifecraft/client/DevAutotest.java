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
        if (mode.equals("pause")) {
            YompixSplash.markShown();
            for (int i = 0; i < 4; i++) key(40, GLFW.GLFW_KEY_SPACE);
            run(10, c -> c.setScreen(new com.yompix.lifecraft.client.menu.LifePauseScreen()));
            shot(20, "pause");
            run(5, c -> c.setScreen(new net.minecraft.client.gui.screen.OpenToLanScreen(c.currentScreen)));
            shot(20, "lan");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("gfx")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); c.options.hudHidden = true; });
            cmd(10, "gamemode spectator " + P);
            cmd(5, "tp " + P + " 30 -45 30 135 12");
            String[][] shots = {{"12600", "1", "g_sunset"}, {"12600", "0", "g_sunset_lite"}, {"17000", "1", "g_night"}, {"17000", "0", "g_night_lite"}, {"6000", "1", "g_day"}};
            for (String[] v : shots) {
                cmd(5, "time set " + v[0]);
                run(2, c -> { com.yompix.lifecraft.client.render.ColorGrade.enabled = true; com.yompix.lifecraft.client.render.ColorGrade.quality = Float.parseFloat(v[1]); });
                shot(40, v[2]);
            }
            run(2, c -> com.yompix.lifecraft.client.render.ColorGrade.enabled = false);
            shot(20, "g_off");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("atm")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
                d.money = 800;
                Life.sync(s.getPlayerManager().getPlayer(P));
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> c.setScreen(new com.yompix.lifecraft.client.screen.AtmScreen(800, 0)));
            shot(8, "atm_card");
            shot(30, "atm_menu");
            run(2, c -> { if (c.currentScreen instanceof com.yompix.lifecraft.client.screen.AtmScreen a) a.pressWithdraw(250); });
            shot(10, "atm_proc");
            shot(22, "atm_cash1");
            shot(10, "atm_cash2");
            run(30, c -> c.setScreen(null));
            server(5, s -> {
                var inv = s.getPlayerManager().getPlayer(P).getInventory();
                for (int i = 0; i < inv.size(); i++) if (!inv.getStack(i).isEmpty())
                    com.yompix.lifecraft.LifeCraft.LOG.info("[atm] {} x{}", inv.getStack(i).getName().getString(), inv.getStack(i).getCount());
                com.yompix.lifecraft.LifeCraft.LOG.info("[atm] balance {}", Life.get(s.getPlayerManager().getPlayer(P)).money);
            });
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("wheel")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
                d.age = 20;
                Story.applyAge(s.getPlayerManager().getPlayer(P), 20);
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(10, "gamemode creative " + P);
            cmd(5, "time set 6000");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, -40, 24);
                var car = new com.yompix.lifecraft.entity.CarEntity(com.yompix.lifecraft.ModContent.CAR, w);
                car.refreshPositionAndAngles(-45.5, y, 24.5, -90, 0);
                car.setVariant(1);
                w.spawnEntity(car);
                p.startRiding(car, true);
            });
            cmd(5, "time set 15000");
            server(5, s -> {
                if (s.getPlayerManager().getPlayer(P).getVehicle() instanceof com.yompix.lifecraft.entity.CarEntity car) {
                    car.toggleLight(com.yompix.lifecraft.entity.CarEntity.HEADLIGHTS);
                    car.toggleLight(com.yompix.lifecraft.entity.CarEntity.LEFT);
                }
            });
            run(10, c -> { c.player.setYaw(-90); c.player.setPitch(30); });
            shot(20, "wh1");
            shot(4, "wh1b");
            run(5, c -> { c.player.setPitch(10); c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT); });
            shot(20, "wh2");
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            shot(20, "wh3");
            shot(4, "wh3b");
            shot(4, "wh3c");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("stuck")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(10, "gamemode spectator " + P);
            cmd(5, "gamerule doDaylightCycle true");
            cmd(5, "time set 0");
            // держим все чанки города загруженными: игрок летает по кругу над городом
            for (int k = 0; k < 40; k++) {
                int kk = k;
                server(60, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    double a = kk * 0.6;
                    p.teleport(p.getServerWorld(), Math.cos(a) * 40 + 20, -30, Math.sin(a) * 40, 0, 90);
                    if (kk % 8 == 0) s.getCommandManager().executeWithPrefix(s.getCommandSource(), "time add 2500");
                });
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("traffic")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(10, "gamemode spectator " + P);
            cmd(5, "time set 6000");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, -40, 24);
                var taxi = new com.yompix.lifecraft.entity.CarEntity(com.yompix.lifecraft.ModContent.CAR, w);
                taxi.refreshPositionAndAngles(-45.5, y, 24.5, -90, 0);
                taxi.setVariant(5);
                w.spawnEntity(taxi);
                CitizenEntity drv = new CitizenEntity(com.yompix.lifecraft.ModContent.CITIZEN, w);
                drv.setup(20, CitizenEntity.Role.CITIZEN, true, "Таксист");
                drv.refreshPositionAndAngles(-45.5, y, 24.5, -90, 0);
                w.spawnEntity(drv);
                drv.startRiding(taxi, true);
                var parked = new com.yompix.lifecraft.entity.CarEntity(com.yompix.lifecraft.ModContent.CAR, w);
                parked.refreshPositionAndAngles(-30.5, y, 24.5, -90, 0);
                w.spawnEntity(parked);
                CitizenEntity walker = new CitizenEntity(com.yompix.lifecraft.ModContent.CITIZEN, w);
                walker.setup(CitizenEntity.RANDOM_FROM + 3, CitizenEntity.Role.CITIZEN, true, "Зевака");
                walker.refreshPositionAndAngles(-12.5, y, 24.5, 90, 0);
                w.spawnEntity(walker);
                taxi.setRoute(java.util.List.of(new net.minecraft.util.math.Vec3d(12.5, y, 24.5)), 0.5f,
                        () -> com.yompix.lifecraft.LifeCraft.LOG.info("[traffic] arrived"));
                if (Boolean.getBoolean("lifecraft.ride")) p.startRiding(taxi, true);
                else p.teleport(w, -28.5, y + 22, 24.5, -90, 70);
            });
            server(5, s -> {
                var w = s.getPlayerManager().getPlayer(P).getServerWorld();
                for (var c : w.getEntitiesByClass(CitizenEntity.class, s.getPlayerManager().getPlayer(P).getBoundingBox().expand(200), c -> c.getStoryId().isEmpty()))
                    com.yompix.lifecraft.LifeCraft.LOG.info("[names] {} {}", CitizenEntity.isSlim(c.getSkin()) ? "Ж" : "М", c.getName().getString());
            });
            for (int i = 0; i < 12; i++) {
                shot(40, "tr" + i);
                server(1, s -> {
                    var w = s.getPlayerManager().getPlayer(P).getServerWorld();
                    for (var car : w.getEntitiesByClass(com.yompix.lifecraft.entity.CarEntity.class, s.getPlayerManager().getPlayer(P).getBoundingBox().expand(80), c -> c.isTaxi()))
                        com.yompix.lifecraft.LifeCraft.LOG.info("[traffic] taxi at {} {} route {}", String.format("%.1f", car.getX()), String.format("%.1f", car.getZ()), car.onRoute());
                });
            }
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("scooter")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            cmd(10, "gamemode survival " + P);
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, -40, 24);
                var sc = new com.yompix.lifecraft.entity.CarEntity(com.yompix.lifecraft.ModContent.SCOOTER, w);
                sc.refreshPositionAndAngles(-50.5, y, 22.5, -90, 0);
                w.spawnEntity(sc);
                p.teleport(w, -50.5, y, 22.5, -90, 0);
                p.startRiding(sc, true);
            });
            run(20, c -> c.options.forwardKey.setPressed(true));
            for (int i = 0; i < 6; i++) shot(20, "sc" + i);
            run(5, c -> c.options.forwardKey.setPressed(false));
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("c6")) {
            YompixSplash.markShown();
            server(150, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var d = Life.get(p);
                d.storyStarted = true;
                d.storyDone = true;
                d.age = 18;
                d.chapter = 5;
                d.mission = 0;
                d.step = 0;
                d.prevMode = -1;
                Story.onJoin(p);
                Story.onClientReady(p);
            });
            for (int i = 0; i < 4; i++) shot(80, "c6_" + i);
            for (int i = 0; i < 8; i++) { key(60, GLFW.GLFW_KEY_SPACE); shot(20, "c6_k" + i); }
            server(5, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                com.yompix.lifecraft.LifeCraft.LOG.info("[c6] chapter {} mission {} step {} done {}", d.chapter, d.mission, d.step, d.storyDone);
            });
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("east")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
                d.chapter = 99;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); c.options.hudHidden = false; });
            cmd(10, "gamemode spectator " + P);
            cmd(5, "time set 6000");
            boolean quick = Boolean.getBoolean("lifecraft.quick");
            String[][] views = quick ? new String[0][] : new String[][]{{"cam_east_a", "e_aerial"}, {"cam_east_b", "e_aerial2"}, {"cam_pizza_a", "e_pizza"}, {"cam_club_a", "e_club_out"}};
            for (String[] v : views) {
                server(5, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    double[] c = com.yompix.lifecraft.world.TownState.get(p.getServerWorld()).place(v[0]);
                    if (c != null) p.teleport(p.getServerWorld(), c[0], c[1], c[2], (float) c[3], (float) c[4]);
                });
                shot(60, v[1]);
            }
            String[][] inside = quick ? new String[0][] : new String[][]{{"pizza_counter", "0", "e_pizza_in", "20"}, {"gym_center", "180", "e_gym_in", "15"}, {"club_floor", "180", "e_club_in", "10"}};
            for (String[] v : inside) {
                if (v[2].equals("e_club_in")) cmd(5, "time set 16000");
                server(5, s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                    double[] c = com.yompix.lifecraft.world.TownState.get(p.getServerWorld()).place(v[0]);
                    if (c != null) p.teleport(p.getServerWorld(), c[0], c[1] + 1.2, c[2], (float) c[3] + Float.parseFloat(v[1]), Float.parseFloat(v[3]));
                });
                shot(60, v[2]);
            }
            cmd(5, "gamemode creative " + P);
            run(5, c -> c.setScreen(new com.yompix.lifecraft.client.screen.RhythmScreen("run")));
            shot(60, "e_rhythm");
            run(5, c -> c.setScreen(null));
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                Life.get(p).weight = 105;
                Life.sync(p);
                com.yompix.lifecraft.life.Fitness.apply(p, Life.get(p));
                double[] c = com.yompix.lifecraft.world.TownState.get(p.getServerWorld()).place("gym_door");
                p.teleport(p.getServerWorld(), c[0], c[1], c[2] + 3, 0, 10);
            });
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            shot(30, "e_fat");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                Life.get(p).weight = 55;
                Life.sync(p);
            });
            shot(20, "e_slim");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("home")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); c.options.hudHidden = false; });
            cmd(10, "gamemode creative " + P);
            cmd(5, "gamerule doDaylightCycle true");
            cmd(5, "time set 12950");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                double[] door = com.yompix.lifecraft.world.TownState.get(w).place("gleb_door");
                double rad = Math.toRadians(door[3]);
                for (int i = 0; i < 4; i++) {
                    CitizenEntity c = new CitizenEntity(com.yompix.lifecraft.ModContent.CITIZEN, w);
                    c.setup(CitizenEntity.RANDOM_FROM + i * 3, CitizenEntity.Role.CITIZEN, false, "Сосед " + i);
                    c.setHome("gleb");
                    c.refreshPositionAndAngles(door[0] - Math.sin(rad) * -6 + (i - 1.5) * 2, door[1], door[2] + Math.cos(rad) * -6, 0, 0);
                    w.spawnEntity(c);
                }
                // камера на крыльце, смотрит на дверь
                p.teleport(w, door[0] + Math.sin(rad) * 5 + Math.cos(rad) * 3, door[1] + 1.5, door[2] - Math.cos(rad) * 5 + Math.sin(rad) * 3, (float) door[3] - 30, 10);
            });
            shot(60, "h_walk");
            shot(100, "h_door");
            shot(100, "h_door2");
            cmd(5, "time set 18500");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                double[] bed = com.yompix.lifecraft.world.TownState.get(w).place("gleb_bed");
                double[] liv = com.yompix.lifecraft.world.TownState.get(w).place("gleb_living");
                p.teleport(w, liv[0], liv[1] + 0.2, liv[2], 0, 20);
                p.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, new net.minecraft.util.math.Vec3d(bed[0], bed[1], bed[2]));
                for (var c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(20), c -> true))
                    com.yompix.lifecraft.LifeCraft.LOG.info("[home] {} act {} inv {} at {}", c.getName().getString(), c.getActivity(), c.isInvisible(), c.getPos());
            });
            shot(500, "h_night");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                double[] liv = com.yompix.lifecraft.world.TownState.get(p.getServerWorld()).place("gleb_living");
                p.teleport(p.getServerWorld(), liv[0] + 3, liv[1] + 0.2, liv[2] + 1, 0, 15);
                p.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, new net.minecraft.util.math.Vec3d(liv[0] - 2, liv[1], liv[2] - 1));
            });
            shot(20, "h_night2");
            cmd(5, "time set 23700");
            shot(200, "h_morning");
            shot(200, "h_morning2");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                for (var c : p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(30), c -> c.getName().getString().startsWith("Сосед")))
                    com.yompix.lifecraft.LifeCraft.LOG.info("[home-am] {} act {} inv {} at {}", c.getName().getString(), c.getActivity(), c.isInvisible(), c.getPos());
            });
            // встречи на улице: ставим пары лицом к лицу
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, 0, 10);
                CitizenEntity.Activity[] acts = {CitizenEntity.Activity.HANDSHAKE, CitizenEntity.Activity.HUG, CitizenEntity.Activity.ARGUE, CitizenEntity.Activity.FIGHT, CitizenEntity.Activity.LAUGH, CitizenEntity.Activity.UPSET};
                for (int i = 0; i < acts.length; i++) {
                    CitizenEntity a1 = new CitizenEntity(com.yompix.lifecraft.ModContent.CITIZEN, w), a2 = new CitizenEntity(com.yompix.lifecraft.ModContent.CITIZEN, w);
                    a1.setup(CitizenEntity.RANDOM_FROM + 2 * i, CitizenEntity.Role.CITIZEN, true, "A" + i);
                    a2.setup(CitizenEntity.RANDOM_FROM + 2 * i + 5, CitizenEntity.Role.CITIZEN, true, "B" + i);
                    double x = -12.5 + i * 5;
                    double gap = acts[i] == CitizenEntity.Activity.HUG ? 0.65 : acts[i] == CitizenEntity.Activity.HANDSHAKE ? 0.9 : 1.4;
                    a1.refreshPositionAndAngles(x - gap / 2, y, 10.5, -90, 0);
                    a2.refreshPositionAndAngles(x + gap / 2, y, 10.5, 90, 0);
                    w.spawnEntity(a1);
                    w.spawnEntity(a2);
                    a1.sceneWith(a2, acts[i], 2000);
                    a2.sceneWith(a1, acts[i] == CitizenEntity.Activity.LAUGH || acts[i] == CitizenEntity.Activity.UPSET ? CitizenEntity.Activity.TALK : acts[i], 2000);
                }
                p.teleport(w, 0.5, y + 1.2, 18.5, 180, 10);
            });
            cmd(5, "time set 5000");
            shot(40, "i1");
            shot(9, "i2");
            shot(9, "i3");
            run(20, MinecraftClient::scheduleStop);
            return;
        }
        if (mode.equals("geo")) {
            YompixSplash.markShown();
            server(150, s -> {
                var d = Life.get(s.getPlayerManager().getPlayer(P));
                d.storyStarted = true;
                d.storyDone = true;
            });
            for (int i = 0; i < 4; i++) key(10, GLFW.GLFW_KEY_SPACE);
            run(5, c -> {
                if (c.currentScreen != null) c.setScreen(null);
                c.setCameraEntity(c.player);
                c.options.hudHidden = false;
            });
            cmd(20, "time set 5000");
            cmd(5, "gamemode creative " + P);
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                double bx = 0.5, bz = 10.5;
                int y = Story.topY(w, 0, 10);
                CitizenEntity.Activity[] acts = {CitizenEntity.Activity.IDLE, CitizenEntity.Activity.TALK, CitizenEntity.Activity.WAVE, CitizenEntity.Activity.PHONE,
                        CitizenEntity.Activity.THINK, CitizenEntity.Activity.DANCE, CitizenEntity.Activity.CHEER, CitizenEntity.Activity.SIT};
                int[] skins = {8, 11, 9, 10, 14, 17, 12, 16};
                for (int i = 0; i < acts.length; i++) {
                    CitizenEntity c = new CitizenEntity(com.yompix.lifecraft.ModContent.CITIZEN, w);
                    c.setup(skins[i], CitizenEntity.Role.CITIZEN, true, acts[i].name());
                    c.refreshPositionAndAngles(bx - 10.5 + i * 3, y, bz, 0, 0);
                    c.setHeadYaw(0);
                    c.setBodyYaw(0);
                    w.spawnEntity(c);
                    c.lockActivity(acts[i]);
                }
                // двое идут и бегут
                for (int i = 0; i < 2; i++) {
                    CitizenEntity c = new CitizenEntity(com.yompix.lifecraft.ModContent.CITIZEN, w);
                    c.setup(20 + i * 5, CitizenEntity.Role.CITIZEN, false, i == 0 ? "WALK" : "RUN");
                    c.refreshPositionAndAngles(bx - 12, y, bz + 5 + i * 2, -90, 0);
                    w.spawnEntity(c);
                    c.getNavigation().startMovingTo(bx + 14, y, bz + 5 + i * 2, i == 0 ? 0.6 : 1.3);
                }
                p.teleport(w, bx, y + 1.2, bz + 10, 180, 8);
            });
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                int y = Story.topY(w, 0, 18);
                // такси с водителем-жителем и игроком на пассажирском — проверить посадку
                var car = new com.yompix.lifecraft.entity.CarEntity(com.yompix.lifecraft.ModContent.CAR, w);
                car.refreshPositionAndAngles(-20.5, y, 18.5, 90, 0);
                car.setVariant(5);
                w.spawnEntity(car);
                CitizenEntity drv = new CitizenEntity(com.yompix.lifecraft.ModContent.CITIZEN, w);
                drv.setup(20, CitizenEntity.Role.CITIZEN, true, "Таксист");
                drv.refreshPositionAndAngles(-20.5, y, 18.5, 90, 0);
                w.spawnEntity(drv);
                drv.startRiding(car, true);
            });
            shot(30, "g1");
            for (int i = 0; i < 8; i++) shot(3, "w" + i);
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var w = p.getServerWorld();
                for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(30), c -> c.getVehicle() == null && c.getNavigation().isIdle())) {
                    c.setYaw(90); c.setBodyYaw(90); c.setHeadYaw(90);
                }
                p.teleport(w, -4.5, Story.topY(w, -4, 15) + 0.1, 15.5, 180, 5);
            });
            run(3, c -> {
                for (var e : c.world.getEntities()) if (e instanceof CitizenEntity ce && ce.getVehicle() == null) { ce.bodyYaw = 90; ce.prevBodyYaw = 90; ce.headYaw = 90; ce.prevHeadYaw = 90; }
            });
            shot(10, "side1");
            server(3, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.teleport(p.getServerWorld(), 4.5, Story.topY(p.getServerWorld(), 4, 15) + 0.1, 15.5, 180, 5);
            });
            shot(10, "side2");
            shot(7, "g2");
            shot(7, "g3");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.teleport(p.getServerWorld(), -7.5, p.getY(), 13.2, 180, 12);
            });
            shot(30, "g4");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.teleport(p.getServerWorld(), 5.5, p.getY(), 13.2, 180, 12);
            });
            shot(30, "g5");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.teleport(p.getServerWorld(), -14, p.getY(), 12, -100, 5);
            });
            shot(30, "g6");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.teleport(p.getServerWorld(), -20.8, Story.topY(p.getServerWorld(), -20, 23) + 0.4, 21.0, 180, 18);
            });
            shot(30, "t_side");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                p.getAbilities().flying = true; p.sendAbilitiesUpdate();
                p.teleport(p.getServerWorld(), -20.5, Story.topY(p.getServerWorld(), -20, 18) + 3.3, 20.3, 180, 60);
            });
            shot(30, "t_top");
            server(5, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                var cars = p.getServerWorld().getEntitiesByClass(com.yompix.lifecraft.entity.CarEntity.class, p.getBoundingBox().expand(10), c -> true);
                if (!cars.isEmpty()) p.startRiding(cars.get(0), true);
            });
            run(5, c -> { c.player.setYaw(90); c.player.setPitch(12); });
            shot(20, "t_inside");
            run(5, c -> { c.player.setYaw(-60); c.player.setPitch(25); });
            shot(20, "t_back");
            run(5, c -> { c.player.setYaw(90); c.player.setPitch(20); c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK); });
            shot(20, "t_3rd");
            // такси само везёт: игрок пассажир, руль у таксиста
            server(2, s -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(P);
                if (p.getVehicle() instanceof com.yompix.lifecraft.entity.CarEntity car) {
                    car.setRoute(java.util.List.of(car.getPos().add(-25, 0, 0)), 0.5f, () -> com.yompix.lifecraft.LifeCraft.LOG.info("[ride] arrived, controller={}", car.getControllingPassenger()));
                    com.yompix.lifecraft.LifeCraft.LOG.info("[ride] start at {} controller={}", car.getPos(), car.getControllingPassenger());
                }
            });
            shot(40, "t_ride1");
            server(2, s -> com.yompix.lifecraft.LifeCraft.LOG.info("[ride] player now at {} vehicle={}", s.getPlayerManager().getPlayer(P).getPos(), s.getPlayerManager().getPlayer(P).getVehicle()));
            shot(60, "t_ride2");
            server(2, s -> {
                for (var car : s.getPlayerManager().getPlayer(P).getServerWorld().getEntitiesByClass(com.yompix.lifecraft.entity.CarEntity.class, s.getPlayerManager().getPlayer(P).getBoundingBox().expand(10), c -> true))
                    for (var e : car.getPassengerList())
                        com.yompix.lifecraft.LifeCraft.LOG.info("[seat] car {} {} {} yaw {} -> {} seat {} at {} {} {}", car.getX(), car.getY(), car.getZ(), car.getYaw(), e.getType(), car.seatOf(e), e.getX(), e.getY(), e.getZ());
            });
            run(2, c -> {
                for (var e : c.world.getEntities()) if (e.getVehicle() != null)
                    com.yompix.lifecraft.LifeCraft.LOG.info("[seat-client] {} at {} {} {} pose {} vis {}", e.getType(), e.getX(), e.getY(), e.getZ(), e.getPose(), !e.isInvisible());
            });
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT));
            shot(30, "t_front");
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
