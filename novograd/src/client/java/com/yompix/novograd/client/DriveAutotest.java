package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.gta.DriveHooks;
import com.yompix.novograd.gta.Fuel;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Только для разработки: -Dnovograd.autotest=drv (или drv_cars, drv_fuel, drv_fines, drv_air, drv_boat, drv_bets, drv_auction, drv_fog) —
 * новые машины, бензин и заправка, камера скорости и штрафы, штрафстоянка, авиарейс, яхт-клуб, тотализатор, аукцион, туман.
 * Мир «Fresh2», снимки в run/screenshots/drv_*.png.
 */
public final class DriveAutotest {
    private DriveAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";
    private static final Map<Integer, CarEntity> CARS = new HashMap<>();

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("drv")) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        String world = System.getProperty("novograd.drvworld", "Fresh2");
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(world, () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode survival " + P);
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "gamerule doWeatherCycle false");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        cmd(5, "effect give " + P + " minecraft:saturation 99999 4 true");
        server(5, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
            d.money = 30000;
            d.dirty = 0;
            Life.sync(pl(s));
        });
        boolean all = mode.equals("drv");
        if (all || mode.contains("cars")) cars();
        if (all || mode.contains("fuel")) fuel();
        if (all || mode.contains("fines")) fines();
        if (all || mode.contains("air")) air();
        if (all || mode.contains("boat")) boat();
        if (all || mode.contains("bets")) bets();
        if (all || mode.contains("auction")) auction();
        if (all || mode.contains("fog")) fog();
        run(20, c -> Novograd.LOG.info("[drv] done"));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(DriveAutotest::tick);
    }

    // ------------------------------------------------------------------ сценарии
    private static void cars() {
        final int X0 = 420, Z = -905;
        int[] vs = {12, 13, 14, 15, 16, 17, 18, 19};
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            for (int x = X0 - 32; x < X0 + vs.length * 9 + 32; x += 16) w.getChunk(x >> 4, Z >> 4);
            p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            look(p, X0 + vs.length * 4.5, Story.topY(w, X0, Z) + 12, Z + 24, X0 + vs.length * 4.5, Story.topY(w, X0, Z), Z);
        });
        run(60, c -> {});
        server(5, s -> {
            ServerWorld w = pl(s).getServerWorld();
            for (int i = 0; i < vs.length; i++) {
                double x = X0 + i * 9 + 0.5;
                CarEntity car = new CarEntity(ModContent.CAR, w);
                car.refreshPositionAndAngles(x, Story.topY(w, (int) x, Z), Z + 0.5, 0, 0);
                car.setVariant(vs[i]);
                w.spawnEntity(car);
                CARS.put(vs[i], car);
            }
        });
        shot(50, "drv_cars_all");
        for (int v : vs) {
            server(5, s -> {
                CarEntity car = CARS.get(v);
                look(pl(s), car.getX() + 4.6, car.getY() + 2.1, car.getZ() + 6.4, car.getX(), car.getY() + 0.9, car.getZ() - 0.4);
            });
            shot(16, "drv_car_" + CarEntity.VARIANTS[v] + "_front");
            server(5, s -> {
                CarEntity car = CARS.get(v);
                look(pl(s), car.getX() - 4.8, car.getY() + 2.3, car.getZ() - 7.6, car.getX(), car.getY() + 0.9, car.getZ() - 1);
            });
            shot(16, "drv_car_" + CarEntity.VARIANTS[v] + "_rear");
        }
        for (int v : new int[]{12, 13, 14, 15}) {
            server(5, s -> {
                CarEntity car = CARS.get(v);
                look(pl(s), car.getX() + 8.5, car.getY() + 1.3, car.getZ() - 0.8, car.getX(), car.getY() + 0.9, car.getZ() - 0.8);
            });
            shot(16, "drv_car_" + CarEntity.VARIANTS[v] + "_side");
        }
        // сидим в суперкаре и багги: голова не торчит сквозь крышу
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            p.startRiding(CARS.get(12), true);
        });
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        shot(30, "drv_ride_super");
        // ночь: мигалки скорой, пожарной, спецназа
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.stopRiding();
            p.getServerWorld().setTimeOfDay(18000);
            for (int v : new int[]{17, 18, 19}) {
                CARS.get(v).toggleLight(CarEntity.HAZARD);
                CARS.get(v).toggleLight(CarEntity.HEADLIGHTS);
            }
            p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            CarEntity car = CARS.get(18);
            look(p, car.getX() + 2, car.getY() + 5, car.getZ() + 14, car.getX() + 2, car.getY() + 1, car.getZ());
        });
        run(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        shot(30, "drv_cars_night");
        server(5, s -> {
            pl(s).getServerWorld().setTimeOfDay(6000);
            pl(s).changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            for (CarEntity c : CARS.values()) c.discard();
        });
    }

    private static void fuel() {
        // заправка 0: машина с 8% бензина у колонок
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            Vec3d at = Fuel.pumps(0).add(0, 0, 0);
            w.getChunk((int) at.x >> 4, (int) at.z >> 4);
            int y = com.yompix.novograd.world.Island.surface((int) Math.floor(at.x), (int) Math.floor(at.z)) + 1;
            p.teleport(w, at.x - 6, y, at.z - 4, 0, 0);
        });
        run(60, c -> {});
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            Vec3d at = Fuel.pumps(0);
            int y = com.yompix.novograd.world.Island.surface((int) Math.floor(at.x), (int) Math.floor(at.z)) + 1;
            CarEntity car = com.yompix.novograd.gta.FunUtil.car(w, new Vec3d(at.x - 1.5, y, at.z), 90, 0);
            car.setFuel(80);
            car.setOwner(p.getUuid());
            p.startRiding(car, true);
            CARS.put(100, car);
        });
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        shot(8, "drv_fuel_low");
        shot(60, "drv_fuel_refuel");
        server(5, s -> Novograd.LOG.info("[drv] fuel after refuel: {}", CARS.get(100).fuel()));
        // пустой бак: газ не работает
        server(5, s -> {
            CarEntity car = CARS.get(100);
            pl(s).stopRiding();
            car.refreshPositionAndAngles(car.getX() + 30, car.getY(), car.getZ(), 90, 0);
            car.setFuel(0);
        });
        server(5, s -> pl(s).startRiding(CARS.get(100), true));
        hold(5, c -> c.options.forwardKey, 40);
        server(5, s -> Novograd.LOG.info("[drv] empty tank moved speed={} fuel={}", CARS.get(100).speed, CARS.get(100).fuel()));
        shot(5, "drv_fuel_empty");
        // кассир заправки
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.stopRiding();
            Vec3d c = Fuel.at(0, -5, 23.2);
            Vec3d pump = Fuel.pumps(0);
            Vec3d eye = c.add(pump.subtract(c).normalize().multiply(3.5));
            look(p, eye.x, com.yompix.novograd.world.Island.G + 2.6, eye.z, c.x, com.yompix.novograd.world.Island.G + 2.4, c.z);
            var clerks = p.getServerWorld().getEntitiesByClass(com.yompix.novograd.entity.CitizenEntity.class, p.getBoundingBox().expand(30), e -> e.getCommandTags().stream().anyMatch(t -> t.startsWith("nv_fuel_clerk")));
            Novograd.LOG.info("[drv] fuel clerks: {}", clerks.size());
        });
        run(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        shot(30, "drv_fuel_clerk");
        server(5, s -> CARS.remove(100).discard());
    }

    private static void fines() {
        // разгон на суперкаре мимо камеры на северном кольце
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            for (int x = 220; x < 360; x += 16) w.getChunk(x >> 4, -744 >> 4);
            int y = Story.topY(w, 262, -742);
            p.teleport(w, 262.5, y, -741.5, -90, 0);
        });
        run(40, c -> {});
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            CarEntity car = com.yompix.novograd.gta.FunUtil.car(w, new Vec3d(262.5, Story.topY(w, 262, -742), -741.5), -90, CarEntity.SUPERCAR);
            car.setTune(true, 2, 0);
            p.startRiding(car, true);
            CARS.put(101, car);
        });
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        hold(10, c -> c.options.forwardKey, 40);
        server(1, s -> Novograd.LOG.info("[drv] car at {} speed {}", CARS.get(101).getPos(), CARS.get(101).hitSpeed()));
        hold(1, c -> c.options.forwardKey, 6);
        shot(1, "drv_fines_flash");
        server(1, s -> Novograd.LOG.info("[drv] car at {} speed {}", CARS.get(101).getPos(), CARS.get(101).hitSpeed()));
        run(10, c -> {});
        shot(10, "drv_fines_after");
        server(5, s -> Novograd.LOG.info("[drv] fines total {}", com.yompix.novograd.gta.Fines.total(pl(s))));
        server(5, s -> pl(s).stopRiding());
        server(5, s -> DriveHooks.debug(pl(s), "fine 3"));
        run(10, c -> c.setScreen(new com.yompix.novograd.client.screen.DriveScreen()));
        shot(30, "drv_phone_fines");
        run(5, c -> { if (c.currentScreen != null) c.currentScreen.close(); });
        // штрафстоянка: эвакуируем свою машину и забираем
        server(10, s -> {
            CarEntity car = CARS.remove(101);
            car.setOwner(pl(s).getUuid());
            com.yompix.novograd.gta.Impound.class.getName();
            DriveHooks.debug(pl(s), "tow");
            Novograd.LOG.info("[drv] impound list {}", com.yompix.novograd.gta.Impound.list(pl(s)).size());
        });
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = Story.place(p.getServerWorld(), com.yompix.novograd.gta.Impound.LOT);
            Novograd.LOG.info("[drv] lot at {}", at);
            if (at != null) {
                p.getServerWorld().getChunk((int) at.x >> 4, (int) at.z >> 4);
                p.teleport(p.getServerWorld(), at.x + 6, Story.topY(p.getServerWorld(), (int) at.x + 6, (int) at.z) + 0.1, at.z, 90, 15);
            }
        });
        shot(50, "drv_impound_lot");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = Story.place(p.getServerWorld(), com.yompix.novograd.gta.Impound.LOT);
            if (at != null) p.teleport(p.getServerWorld(), at.x, Story.topY(p.getServerWorld(), (int) at.x, (int) at.z) + 0.1, at.z, 90, 15);
        });
        shot(30, "drv_impound_screen");
        server(5, s -> com.yompix.novograd.gta.Impound.retrieve(pl(s), 0));
        run(5, c -> { if (c.currentScreen != null) c.currentScreen.close(); });
        shot(20, "drv_impound_back");
    }

    private static void air() {
        place(5, "airfield_hangar+14,18", 5, 0, 90, 10);
        shot(50, "drv_air_counter");
        place(5, "airfield_hangar+14,18", 0, 0, 90, 10);
        shot(30, "drv_air_screen");
        server(5, s -> DriveHooks.debug(pl(s), "fly riv"));
        run(5, c -> { if (c.currentScreen != null) c.currentScreen.close(); });
        shot(25, "drv_air_flight1");
        shot(50, "drv_air_flight2");
        shot(70, "drv_air_landed");
        server(5, s -> Novograd.LOG.info("[drv] after flight at {}", pl(s).getPos()));
    }

    private static void boat() {
        place(5, "riv_marina", 5, 3, 90, 10);
        shot(60, "drv_boat_club");
        place(5, "riv_marina", 0, 0, 90, 10);
        shot(30, "drv_boat_screen");
        server(5, s -> com.yompix.novograd.gta.BoatClub.action(pl(s), "race"));
        run(5, c -> { if (c.currentScreen != null) c.currentScreen.close(); });
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        shot(40, "drv_boat_start");
        hold(5, c -> c.options.forwardKey, 120);
        shot(1, "drv_boat_race");
        server(5, s -> Novograd.LOG.info("[drv] boat at {} vehicle {}", pl(s).getPos(), pl(s).getVehicle()));
        server(5, s -> pl(s).stopRiding());
    }

    private static void bets() {
        place(5, com.yompix.novograd.gta.RaceBets.DESK, 0, 0, 180, 10);
        shot(60, "drv_bets_screen");
        server(5, s -> com.yompix.novograd.gta.RaceBets.bet(pl(s), 0, 500));
        run(5, c -> { if (c.currentScreen != null) c.currentScreen.close(); });
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d st = Story.place(p.getServerWorld(), "race_start");
            if (st != null) look(p, st.x - 14, st.y + 5, st.z + 12, st.x, st.y + 1, st.z);
        });
        shot(330, "drv_bets_grid");
        shot(120, "drv_bets_race");
    }

    private static void auction() {
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            Vec3d at = Story.place(w, com.yompix.novograd.gta.Auction.GATE);
            Novograd.LOG.info("[drv] auction gate {}", at);
            if (at == null) return;
            w.getChunk((int) at.x >> 4, (int) at.z >> 4);
            CarEntity car = com.yompix.novograd.gta.FunUtil.car(w, new Vec3d(at.x + 10, Story.topY(w, (int) at.x + 10, (int) at.z), at.z), 90, CarEntity.CLASSIC);
            p.teleport(w, car.getX(), car.getY(), car.getZ(), 90, 0);
            p.startRiding(car, true);
            CARS.put(102, car);
        });
        run(40, c -> {});
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = Story.place(p.getServerWorld(), com.yompix.novograd.gta.Auction.GATE);
            if (at != null) CARS.get(102).refreshPositionAndAngles(at.x + 1, CARS.get(102).getY(), at.z, 90, 0);
        });
        shot(30, "drv_auction_screen");
        server(5, s -> com.yompix.novograd.gta.Auction.sell(pl(s)));
        shot(70, "drv_auction_bids");
        shot(140, "drv_auction_sold");
    }

    private static void fog() {
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            w.setWeather(0, 6000, true, true);
            int y = Story.topY(w, 456, -330);
            p.teleport(w, 456.5, y, -330.5, 0, 0);
            CarEntity car = com.yompix.novograd.gta.FunUtil.car(w, new Vec3d(456.5, y, -330.5), 180, 2);
            p.startRiding(car, true);
            CARS.put(103, car);
        });
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        shot(200, "drv_fog_rain");
        server(5, s -> CARS.get(103).toggleLight(CarEntity.HEADLIGHTS));
        shot(30, "drv_fog_headlights");
        server(5, s -> {
            pl(s).getServerWorld().setWeather(6000, 0, false, false);
            pl(s).stopRiding();
            CARS.remove(103).discard();
        });
    }

    // ------------------------------------------------------------------ помощники
    private static void place(int delay, String where, double dx, double dz, float yaw, float pitch) {
        server(delay, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            Vec3d at = Story.place(w, where);
            Novograd.LOG.info("[drv] place {} = {}", where, at);
            if (at == null) return;
            p.stopRiding();
            int x = (int) Math.floor(at.x + dx), z = (int) Math.floor(at.z + dz);
            w.getChunk(x >> 4, z >> 4);
            p.teleport(w, at.x + dx, Story.topY(w, x, z) + 0.1, at.z + dz, yaw, pitch);
        });
    }

    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    private static void look(ServerPlayerEntity p, double cx, double cy, double cz, double tx, double ty, double tz) {
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleport(p.getServerWorld(), cx, cy, cz, yaw, pitch);
    }

    private static void hold(int delay, java.util.function.Function<MinecraftClient, KeyBinding> kb, int ticks) {
        run(delay, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(kb.apply(c).getBoundKeyTranslationKey()), true));
        run(ticks, c -> KeyBinding.setKeyPressed(InputUtil.fromTranslationKey(kb.apply(c).getBoundKeyTranslationKey()), false));
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
                    Novograd.LOG.warn("[drv] step failed", e);
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
            Novograd.LOG.warn("[drv] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
