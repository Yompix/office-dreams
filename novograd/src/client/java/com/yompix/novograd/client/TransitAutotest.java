package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.TrainEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.world.Island;
import com.yompix.novograd.world.Metro;
import com.yompix.novograd.world.Retrofit;
import com.yompix.novograd.world.Venues;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: -Dnovograd.autotest=transit (или transit:cafe,market,metro,ride,mirage) — кафе и супермаркеты,
 * метро (станции, поезд, посадка, пропуск поездки, угон), город «Мираж». С -Dnovograd.newworld=true — новый мир
 * (генератор), без него — копия Fresh2 (проверка дописывания в старое сохранение). Снимки run/screenshots/tr_*.png.
 */
public final class TransitAutotest {
    private TransitAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";
    private static final int G = Island.G, D = Metro.D;

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("transit")) return;
        String only = mode.contains(":") ? mode.split(":")[1] : "";
        com.yompix.novograd.client.story.YompixSplash.markShown();
        if (Boolean.getBoolean("novograd.newworld")) PRE.add(new Step(60, c -> {
            var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                    net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
            var info = new net.minecraft.world.level.LevelInfo("TrTest" + System.currentTimeMillis() % 100000, net.minecraft.world.GameMode.CREATIVE, false,
                    net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.GameRules(), data);
            var city = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD_PRESET, Novograd.id("city"));
            c.createIntegratedServerLoader().createAndStart(info.getLevelName(), info, net.minecraft.world.gen.GeneratorOptions.createRandom(),
                    drm -> drm.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(city).createDimensionsRegistryHolder(),
                    new com.yompix.novograd.client.menu.LifeMenuScreen());
        }));
        else PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(System.getProperty("novograd.lifeworld", "Fresh2"),
                () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        server(1, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
        });
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(100, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode creative " + P);
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "weather clear 99999");
        cmd(5, "time set 6000");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        server(5, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
            d.money = 5000;
            Life.sync(pl(s));
        });
        boolean all = only.isEmpty();
        if (all || only.contains("cafe")) {
            view("cafe_suburb_front", new Vec3d(180, G + 7, -34), new Vec3d(192, G + 2, -12), 260);
            view("cafe_suburb_in", new Vec3d(201.5, G + 2.6, -9.5), new Vec3d(192, G + 1.5, 6), 80);
            server(5, s -> Novograd.LOG.info("[transit] посадил в кафе: {}", Venues.debugFeed(pl(s).getServerWorld(), pl(s).getPos())));
            server(500, s -> Novograd.LOG.info("[transit] {}", Venues.debug()));
            shot(5, "tr_cafe_suburb_npc");
            server(5, s -> {
                ServerPlayerEntity p = pl(s);
                var site = Venues.site("cafe_suburb");
                if (site != null) com.yompix.novograd.world.Venues.debugMenu(p, site);
            });
            shot(30, "tr_cafe_menu");
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
            view("cafe_dt", new Vec3d(424, G + 3, -400), new Vec3d(433, G + 2, -418), 260);
            view("cafe_dt_in", new Vec3d(434.5, G + 2.6, -419.5), new Vec3d(424, G + 1.5, -430), 60);
            view("cafe_riviera", new Vec3d(-1262, G + 5, 16), new Vec3d(-1248, G + 2, 36), 260);
            int bx = (int) Math.floor(Island.place("cafe_beach")[0]);
            view("cafe_beach", new Vec3d(bx + 10, G + 6, -486), new Vec3d(bx - 10, G + 1, -472), 260);
        }
        if (all || only.contains("market")) {
            view("market_front", new Vec3d(270, G + 8, -40), new Vec3d(288, G + 3, -10), 260);
            view("market_in", new Vec3d(288.5, G + 3.5, -7.5), new Vec3d(288, G + 1, 10), 80);
            server(5, s -> {
                var site = Venues.site("market_suburb");
                if (site != null) Venues.debugMenu(pl(s), site);
            });
            shot(30, "tr_market_menu");
            run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        }
        if (all || only.contains("metro")) {
            view("metro_dt_street", new Vec3d(400, G + 4, -392), new Vec3d(432, D, -408), 300);
            view("metro_line_air", new Vec3d(110, G + 30, -380), new Vec3d(40, D, -408), 200);
            view("metro_bridge", new Vec3d(-640, G + 20, 700), new Vec3d(-760, D, 744), 300);
            view("metro_meet", new Vec3d(-520, G + 14, 360), new Vec3d(-552, D, 384), 300);
            view("metro_strip", new Vec3d(-1270, G + 12, 50), new Vec3d(-1296, D, 24), 300);
        }
        if (all || only.contains("ride")) {
            server(5, s -> {
                Metro.debugPlace(0, 0);
                ServerPlayerEntity p = pl(s);
                look(p, new Vec3d(436.5, D + 1, -411.5), new Vec3d(430, D + 2, -408));
                p.getAbilities().flying = false;
                p.sendAbilitiesUpdate();
                Metro.debugTicket(p, 2);
            });
            server(60, s -> Novograd.LOG.info("[transit] поезда: {}", Metro.debug()));
            shot(5, "tr_metro_train");
            run(5, c -> TransitClient.send("board", new net.minecraft.nbt.NbtCompound()));
            server(20, s -> Novograd.LOG.info("[transit] сел: {} {}", pl(s).getVehicle(), Metro.debug()));
            shot(5, "tr_metro_inside");
            server(240, s -> Novograd.LOG.info("[transit] едем: {} {}", pl(s).getVehicle(), Metro.debug()));
            shot(5, "tr_metro_moving_in");
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            shot(20, "tr_metro_moving");
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
            run(5, c -> TransitClient.send("skip", new net.minecraft.nbt.NbtCompound()));
            shot(20, "tr_metro_skipmenu");
            run(5, c -> {
                var n = new net.minecraft.nbt.NbtCompound();
                n.putString("ctx", "metro_skip");
                n.putInt("i", 5);
                if (c.currentScreen != null) c.setScreen(null);
                TransitClient.send("pick", n);
            });
            shot(15, "tr_metro_fade");
            server(80, s -> Novograd.LOG.info("[transit] после пропуска: {} {}", pl(s).getPos(), pl(s).getVehicle()));
            shot(5, "tr_metro_arrived");
            // угон: поезд №2 на «Стрипе», игрок рядом на платформе
            server(5, s -> {
                Metro.debugPlace(1, 5);
                ServerPlayerEntity p = pl(s);
                look(p, new Vec3d(-1290.5, D + 1, 28.5), new Vec3d(-1296, D + 2, 24));
                p.getAbilities().flying = false;
                p.sendAbilitiesUpdate();
                p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            });
            server(60, s -> Metro.hijackForTest(pl(s)));
            run(5, c -> c.options.forwardKey.setPressed(true));
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK));
            server(160, s -> Novograd.LOG.info("[transit] угон: {} {} звёзд {}", pl(s).getVehicle(), Metro.debug(), com.yompix.novograd.gta.Wanted.stars(pl(s))));
            shot(5, "tr_metro_hijack");
            run(5, c -> c.options.forwardKey.setPressed(false));
            run(5, c -> c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON));
            server(5, s -> {
                ServerPlayerEntity p = pl(s);
                p.stopRiding();
                p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
                com.yompix.novograd.gta.Wanted.reduce(p, 9);
            });
        }
        if (all || only.contains("mirage")) {
            view("mirage_strip_day", new Vec3d(1224, G + 30, 1140), new Vec3d(1224, G + 10, 1300), 500);
            view("mirage_casino_front", new Vec3d(1212, G + 4, 1275), new Vec3d(1200, G + 8, 1252), 260);
            view("mirage_casino_in", new Vec3d(1200.5, G + 3, 1258.5), new Vec3d(1192, G + 2, 1240), 120);
            view("mirage_chapel", new Vec3d(1238, G + 4, 1226), new Vec3d(1248, G + 8, 1206), 200);
            view("mirage_welcome", new Vec3d(953, G + 6, 1380), new Vec3d(953, G + 8, 1352), 260);
            view("mirage_air", new Vec3d(1340, G + 14, 1500), new Vec3d(1300, G, 1548), 260);
            cmd(5, "time set 14500");
            view("mirage_strip_night", new Vec3d(1240, G + 26, 1150), new Vec3d(1224, G + 8, 1300), 300);
            view("mirage_diner", new Vec3d(1188, G + 5, 1362), new Vec3d(1200, G + 2, 1380), 160);
            cmd(5, "time set 6000");
        }
        run(20, c -> Novograd.LOG.info("[transit] done; {}", Retrofit.debug()));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(TransitAutotest::tick);
    }

    /** Встать в точку, посмотреть на цель, подождать (чанки, дописывание) — снимок и отчёт. */
    private static void view(String name, Vec3d from, Vec3d to, int wait) {
        server(5, s -> look(pl(s), from, to));
        server(wait, s -> Novograd.LOG.info("[transit] {}: mspt {}, {}, {}", name, String.format("%.2f", s.getAverageTickTime()), Retrofit.debug(), Metro.debug()));
        shot(10, "tr_" + name);
    }

    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    private static void look(ServerPlayerEntity p, Vec3d from, Vec3d to) {
        ServerWorld w = p.getServerWorld();
        w.getChunk((int) Math.floor(from.x) >> 4, (int) Math.floor(from.z) >> 4);
        double dx = to.x - from.x, dy = to.y - (from.y + 1.62), dz = to.z - from.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.stopRiding();
        p.teleport(w, from.x, from.y, from.z, yaw, pitch);
        p.getAbilities().flying = true;
        p.sendAbilitiesUpdate();
    }

    private static void cmd(int delay, String command) {
        server(delay, s -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), command));
    }

    private static void server(int delay, Consumer<IntegratedServer> action) {
        STEPS.add(new Step(delay, c -> {
            IntegratedServer s = c.getServer();
            if (s != null) s.execute(() -> {
                try {
                    action.accept(s);
                } catch (RuntimeException e) {
                    Novograd.LOG.warn("[transit] server step failed", e);
                }
            });
        }));
    }

    private static void run(int delay, Consumer<MinecraftClient> action) {
        STEPS.add(new Step(delay, action));
    }

    private static void shot(int delay, String name) {
        STEPS.add(new Step(delay, c -> ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), t -> {})));
    }

    private static void tick(MinecraftClient c) {
        if (c.player == null && !PRE.isEmpty() && c.getOverlay() == null && preIndex < PRE.size()) {
            if (preIndex < 0) {
                if (!(c.currentScreen instanceof com.yompix.novograd.client.story.YompixSplash) && !(c.currentScreen instanceof com.yompix.novograd.client.menu.LifeMenuScreen)) return;
                preIndex = 0;
                preTimer = PRE.get(0).delay();
            }
            if (--preTimer > 0) return;
            PRE.get(preIndex).action().accept(c);
            preIndex++;
            if (preIndex < PRE.size()) preTimer = PRE.get(preIndex).delay();
            return;
        }
        if (STEPS.isEmpty() || c.player == null || c.world == null) return;
        if (index < 0) {
            index = 0;
            timer = STEPS.get(0).delay();
        }
        if (index >= STEPS.size()) return;
        if (--timer > 0) return;
        try {
            STEPS.get(index).action().accept(c);
        } catch (RuntimeException e) {
            Novograd.LOG.warn("[transit] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
