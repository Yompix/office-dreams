package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.gta.CityServices;
import com.yompix.novograd.gta.TrafficLights;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: -Dnovograd.autotest=city (или city_lights, city_bus, city_ems, city_radio) —
 * светофоры и очередь машин, автобус и остановка, скорая/пожарные/патруль, полицейская рация.
 * Мир «Fresh2», снимки в run/screenshots/city_*.png, лог — строки «[city]».
 */
public final class CityAutotest {
    private CityAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";
    private static final List<CarEntity> CARS = new ArrayList<>();

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("city")) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(System.getProperty("novograd.cityworld", "Fresh2"), () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode creative " + P);
        cmd(5, "time set 3000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "gamerule doWeatherCycle false");
        server(5, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
            d.money = 5000;
            Life.sync(pl(s));
        });
        boolean all = mode.equals("city");
        if (all || mode.contains("lights")) lights();
        if (all || mode.contains("bus")) bus();
        if (all || mode.contains("ems")) ems();
        if (all || mode.contains("radio")) radio();
        run(20, c -> Novograd.LOG.info("[city] done"));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(CityAutotest::tick);
    }

    // ------------------------------------------------------------------ светофоры: машины встают на красный и стоят в очереди
    private static void lights() {
        final int GX = 456, GZ = -408;
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            for (int x = GX - 96; x <= GX + 96; x += 16) w.getChunk(x >> 4, GZ >> 4);
            p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            look(p, GX - 62, Story.topY(w, GX, GZ) + 7, GZ + 3.5, GX - 10, Story.topY(w, GX, GZ), GZ + 2.5);
        });
        run(60, c -> {});
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            Novograd.LOG.info("[city] {}", CityServices.debug(p, "lights"));
            // три машины трафика с запада на восток по правой полосе, к перекрёстку
            CARS.clear();
            for (int i = 0; i < 3; i++) {
                double x = GX - 44 - i * 9 + 0.5, z = GZ + 2.5;
                int y = Story.topY(w, (int) x, (int) z);
                CarEntity car = new CarEntity(ModContent.CAR, w);
                car.refreshPositionAndAngles(x, y, z, -90, 0);
                car.setVariant(i == 1 ? 5 : i == 2 ? 7 : 0);
                car.setAmbient(true);
                w.spawnEntity(car);
                CitizenEntity drv = new CitizenEntity(ModContent.CITIZEN, w);
                drv.setup(CitizenEntity.randomSkin(w.random), CitizenEntity.Role.CITIZEN, true, "Водитель");
                drv.refreshPositionAndAngles(x, y, z, 0, 0);
                drv.setAmbient(true);
                w.spawnEntity(drv);
                drv.startRiding(car, true);
                car.setRoute(List.of(new Vec3d(GX + 70.5, y, z)), 0.45f, null);
                CARS.add(car);
            }
        });
        for (int i = 0; i < 24; i++) {
            final int n = i;
            server(40, s -> {
                ServerWorld w = pl(s).getServerWorld();
                StringBuilder sb = new StringBuilder();
                for (CarEntity c : CARS) sb.append(String.format("x=%.1f v=%.2f wait=%d; ", c.getX(), c.speed, c.lightWait));
                var b = w.getBlockState(new net.minecraft.util.math.BlockPos(GX + 5, com.yompix.novograd.world.Island.G + 4, GZ + 5));
                Novograd.LOG.info("[city] lights t={} X={} Z={} lamp={} | {}", n * 2, TrafficLights.state(w.getTime(), false), TrafficLights.state(w.getTime(), true), b, sb);
                if (n == 12) for (CarEntity c : CARS) if (!c.onRoute()) c.setRoute(List.of(new Vec3d(GX + 70.5, c.getY(), GZ + 2.5)), 0.45f, null);
            });
            if (i % 3 == 1) shot(1, "city_lights_" + i);
        }
        server(5, s -> {
            for (CarEntity c : CARS) {
                c.getPassengerList().forEach(e -> e.discard());
                c.discard();
            }
            CARS.clear();
        });
    }

    // ------------------------------------------------------------------ автобус: остановка, приезд, посадка, пропуск
    private static void bus() {
        server(5, s -> Novograd.LOG.info("[city] bus routes:\n{}", CityServices.debug(pl(s), "bus")));
        server(5, s -> {
            pl(s).changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            Novograd.LOG.info("[city] {}", CityServices.debug(pl(s), "bus tp 1 1"));
        });
        run(80, c -> {});
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Novograd.LOG.info("[city] at stop {}", p.getBlockPos().toShortString());
        });
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            // с дороги на навес: табличка «через N мин»
            double rad = Math.toRadians(p.getYaw());
            Vec3d f = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
            Vec3d eye = p.getPos().add(f.multiply(9)).add(0, 2.2, 0);
            look(p, eye.x - f.z * 3, eye.y, eye.z + f.x * 3, p.getX(), p.getY() + 1.6, p.getZ());
        });
        shot(30, "city_bus_stop");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            Novograd.LOG.info("[city] {}", CityServices.debug(p, "bus tp 1 1"));
        });
        server(5, s -> Novograd.LOG.info("[city] {}", CityServices.debug(pl(s), "bus call")));
        for (int i = 0; i < 10; i++) {
            final int n = i;
            server(40, s -> {
                ServerPlayerEntity p = pl(s);
                var w = p.getServerWorld();
                var buses = w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(120), c -> c.getVariant() == CarEntity.BUS);
                for (CarEntity b : buses) Novograd.LOG.info("[city] bus t={} at {} dist {} route {} v={}", n * 2, b.getBlockPos().toShortString(), (int) b.distanceTo(p), b.onRoute(), String.format("%.2f", b.speed));
                if (buses.isEmpty()) Novograd.LOG.info("[city] bus t={} none near; {}", n * 2, CityServices.debug(p, "bus").lines().reduce((a, b) -> b).orElse(""));
            });
            if (i % 2 == 1) shot(1, "city_bus_come_" + i);
        }
        // сесть: ПКМ по автобусу, когда он встал на остановке (ждём до ~30 с — может стоять на красном)
        for (int i = 0; i < 15; i++) {
            final int n = i;
            server(i == 0 ? 5 : 40, s -> {
                ServerPlayerEntity p = pl(s);
                if (p.getVehicle() != null) return;
                var w = p.getServerWorld();
                var buses = w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(40), c -> c.getVariant() == CarEntity.BUS && !c.onRoute() && c.slow());
                if (buses.isEmpty()) return;
                p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                CarEntity b = buses.get(0);
                var r = com.yompix.novograd.gta.Buses.interact(b, p);
                Novograd.LOG.info("[city] board try {} result {} riding {} money {}", n, r, p.getVehicle() == b, Life.get(p).money);
            });
        }
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        shot(40, "city_bus_inside");
        shot(160, "city_bus_ride");
        run(5, c -> NovogradClient.send("city_bus_skip"));
        shot(14, "city_bus_fade");
        shot(80, "city_bus_next_stop");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Novograd.LOG.info("[city] after skip pos {} riding {}", p.getBlockPos().toShortString(), p.getVehicle() != null);
            p.stopRiding();
            p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        });
        run(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
    }

    // ------------------------------------------------------------------ скорая, пожарные, патруль
    private static void ems() {
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            int x = 552, z = -312;
            for (int dx = -64; dx <= 64; dx += 16) for (int dz = -64; dz <= 64; dz += 16) w.getChunk((x + dx) >> 4, (z + dz) >> 4);
            p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            look(p, x + 2.5, Story.topY(w, x, z) + 8, z + 34.5, x + 1, Story.topY(w, x, z), z + 14);
        });
        run(40, c -> {});
        server(5, s -> Novograd.LOG.info("[city] {}", CityServices.debug(pl(s), "amb")));
        for (int i = 0; i < 14; i++) {
            final int n = i;
            server(50, s -> Novograd.LOG.info("[city] ems t={} {}", n * 2.5, CityServices.debug(pl(s), "units")));
            if (i >= 4 && i % 2 == 0) shot(1, "city_amb_" + i);
        }
        server(5, s -> Novograd.LOG.info("[city] {}", CityServices.debug(pl(s), "fire")));
        for (int i = 0; i < 14; i++) {
            final int n = i;
            server(50, s -> Novograd.LOG.info("[city] fire t={} {}", n * 2.5, CityServices.debug(pl(s), "units")));
            if (i >= 4 && i % 2 == 0) shot(1, "city_fire_" + i);
        }
        server(5, s -> Novograd.LOG.info("[city] {}", CityServices.debug(pl(s), "police")));
        for (int i = 0; i < 14; i++) {
            final int n = i;
            server(50, s -> Novograd.LOG.info("[city] police t={} {}", n * 2.5, CityServices.debug(pl(s), "units")));
            if (i >= 3 && i % 2 == 1) shot(1, "city_police_" + i);
        }
    }

    // ------------------------------------------------------------------ рация: в полицейской машине и в розыске
    private static void radio() {
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            double x = 360.5, z = -408 + 2.5;
            CarEntity car = new CarEntity(ModContent.CAR, w);
            car.refreshPositionAndAngles(x, Story.topY(w, (int) x, (int) z), z, -90, 0);
            car.setVariant(CarEntity.POLICE);
            w.spawnEntity(car);
            p.teleport(w, x, car.getY(), z, -90, 10);
            p.startRiding(car, true);
            com.yompix.novograd.gta.Wanted.add(p, 2, "угон полицейской машины", 2);
        });
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        shot(30, "city_radio_1");
        server(5, s -> CityServices.debug(pl(s), "radio"));
        shot(150, "city_radio_2");
        run(1, c -> Novograd.LOG.info("[city] radio: {}", CityClient.lastRadio()));
        server(5, s -> com.yompix.novograd.gta.Wanted.clear(pl(s)));
    }

    // ------------------------------------------------------------------ помощники
    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    private static void look(ServerPlayerEntity p, double cx, double cy, double cz, double tx, double ty, double tz) {
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleport(p.getServerWorld(), cx, cy, cz, yaw, pitch);
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
                    Novograd.LOG.warn("[city] step failed", e);
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
            Novograd.LOG.warn("[city] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
