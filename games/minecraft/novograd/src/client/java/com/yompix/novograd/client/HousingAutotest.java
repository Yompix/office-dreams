package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Garage;
import com.yompix.novograd.world.Housing;
import com.yompix.novograd.world.Residents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только для разработки: -Dnovograd.autotest=housing — недвижимость, гараж, жильцы, уличные музыканты.
 * Мир «HousingTest» (копия Fresh2, -Dnovograd.housingworld=…), снимки в run/screenshots/home_*.png.
 */
public final class HousingAutotest {
    private HousingAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("housing")) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        String world = System.getProperty("novograd.housingworld", "HousingTest");
        if (Boolean.getBoolean("novograd.newworld")) PRE.add(new Step(60, c -> { // свежий мир: интерьеры квартир и офисов новой генерации
            var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                    net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
            var info = new net.minecraft.world.level.LevelInfo("HousingNew" + System.currentTimeMillis() % 100000, net.minecraft.world.GameMode.CREATIVE, false,
                    net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.GameRules(), data);
            var city = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD_PRESET, Novograd.id("city"));
            c.createIntegratedServerLoader().createAndStart(info.getLevelName(), info, net.minecraft.world.gen.GeneratorOptions.createRandom(),
                    drm -> drm.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(city).createDimensionsRegistryHolder(),
                    new com.yompix.novograd.client.menu.LifeMenuScreen());
        }));
        else PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(world, () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        server(1, s -> { // без вступления: сюжет пройден
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
        });
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(100, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode creative " + P);
        cmd(5, "time set 6000");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "weather clear");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        server(5, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
            d.money = 200000;
            d.dirty = 5000;
            Life.sync(pl(s));
            Novograd.LOG.info("[home] listings {}", Housing.listings().size());
        });
        run(200, c -> {}); // новый мир: пусть догенерируется
        boolean all = mode.equals("housing");
        if (all || mode.equals("housing_busk")) buskers();
        if (all || mode.equals("housing_res")) residents();
        if (all || mode.equals("housing_buy")) buy();
        if (all || mode.equals("housing_pent")) penthouse();
        run(20, c -> Novograd.LOG.info("[home] done"));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(HousingAutotest::tick);
    }

    // ------------------------------------------------------------------ сценарии
    private static final String HOUSE = "H:-4:8:1:1", PENT = "T:8:-10:20:2", APT = "T:6:-6:5:0";

    private static void buy() {
        // табличка у дома в пригороде
        server(5, s -> {
            Housing.Listing l = Housing.get(HOUSE);
            if (l == null) { Novograd.LOG.warn("[home] нет {}", HOUSE); return; }
            look(pl(s), l.sign.add(0, 0.6, 7), l.sign);
        });
        run(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        shot(160, "home_sign");
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.RealEstateScreen()));
        shot(40, "home_catalog");
        run(5, c -> send("buy", HOUSE));
        shot(30, "home_bought");
        run(5, c -> c.setScreen(null));
        server(10, s -> {
            Housing.Listing l = Housing.get(HOUSE);
            look(pl(s), l.inside, l.inside.add(0, 0.5, -3));
        });
        // сейф: грязные деньги в сейф
        run(20, c -> send("safe_in", null));
        run(10, c -> c.setScreen(new com.yompix.novograd.client.screen.RealEstateScreen(true)));
        shot(40, "home_mine");
        run(5, c -> c.setScreen(null));
        server(5, s -> Novograd.LOG.info("[home] safe {} dirty {} here {}", Housing.safeCash(pl(s)), Life.get(pl(s)).dirty,
                Housing.here(pl(s)) == null ? "-" : Housing.here(pl(s)).id));
        // гараж: машина на въезде, игрок за рулём — через пару секунд она в гараже
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Housing.Listing l = Housing.get(HOUSE);
            ServerWorld w = p.getServerWorld();
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
            CarEntity car = new CarEntity(ModContent.CAR, w);
            car.setVariant(9);
            car.paint(0x2EAD4B);
            car.setTune(true, 2, 1);
            car.refreshPositionAndAngles(l.garage.x, l.garage.y + 0.5, l.garage.z, 90, 0);
            w.spawnEntity(car);
            p.teleport(w, l.garage.x, l.garage.y + 1, l.garage.z, 90, 20);
            p.startRiding(car, true);
        });
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        shot(20, "home_garage_in");
        server(80, s -> Novograd.LOG.info("[home] cars after park: {}", Garage.cars(s, pl(s).getUuid()).size()));
        shot(5, "home_garage_parked");
        run(5, c -> c.setScreen(new com.yompix.novograd.client.screen.RealEstateScreen(true)));
        shot(30, "home_garage_list");
        server(5, s -> {
            var cars = Garage.cars(s, pl(s).getUuid());
            if (!cars.isEmpty()) {
                NbtCompound n = new NbtCompound();
                n.putInt("car", cars.get(0).getInt("id"));
                MinecraftClient.getInstance().execute(() -> HousingClient.send("car_out", n));
            }
        });
        run(10, c -> c.setScreen(null));
        server(20, s -> {
            ServerPlayerEntity p = pl(s);
            Housing.Listing l = Housing.get(HOUSE);
            look(p, l.garage.add(-6, 3, -6), l.garage);
            var near = p.getServerWorld().getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(20), Garage::isOwned);
            Novograd.LOG.info("[home] car out: {} owned cars near, garage now {}", near.size(), Garage.cars(s, p.getUuid()).size());
        });
        run(5, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        shot(20, "home_garage_out");
    }

    private static void penthouse() {
        run(5, c -> send("buy", PENT));
        server(20, s -> {
            Housing.Listing l = Housing.get(PENT);
            if (l == null) { Novograd.LOG.warn("[home] нет пентхауса"); return; }
            Novograd.LOG.info("[home] pent owned {} heli {}", Housing.owns(pl(s), l), l.heli);
            look(pl(s), l.heli.add(9, 4, 9), l.heli);
        });
        shot(140, "home_pent_roof");
        server(5, s -> {
            Housing.Listing l = Housing.get(PENT);
            look(pl(s), l.inside, l.inside.add(0, 0.6, 4));
        });
        shot(60, "home_pent_inside");
        server(5, s -> {
            Housing.Listing l = Housing.get(PENT);
            look(pl(s), l.street.add(0, 2, 6), l.garage);
        });
        shot(60, "home_pent_street");
    }

    private static void residents() {
        // ночь: прежний жилец квартиры спит в своей кровати
        cmd(5, "time set 18500");
        server(5, s -> {
            Housing.Listing l = Housing.get(APT);
            if (l == null) return;
            int idx = Residents.residentOf(APT);
            Novograd.LOG.info("[home] resident of {}: {} pool {}", APT, idx, Residents.poolSize());
            look(pl(s), l.inside, l.inside.add(0, 0, -4));
        });
        server(120, s -> {
            Residents.debug(pl(s).getServerWorld()).forEach(x -> Novograd.LOG.info("[home] res {}", x));
            Housing.Listing l = Housing.get(APT);
            int beds = 0;
            for (var b : net.minecraft.util.math.BlockPos.iterate(net.minecraft.util.math.BlockPos.ofFloored(l.inside).add(-8, -1, -8), net.minecraft.util.math.BlockPos.ofFloored(l.inside).add(8, 1, 8)))
                if (pl(s).getServerWorld().getBlockState(b).getBlock() instanceof net.minecraft.block.BedBlock) beds++;
            Novograd.LOG.info("[home] beds near {}: {}", APT, beds);
        });
        shot(5, "home_res_night");
        // утро: из подъезда жилой башни выходят на работу
        cmd(5, "time set 1700");
        server(5, s -> {
            Housing.Listing l = Housing.get(APT);
            if (l == null) return;
            look(pl(s), l.street.add(-2, 2, 8), l.street.add(-3, 1, -4));
        });
        server(120, s -> Residents.debug(pl(s).getServerWorld()).forEach(x -> Novograd.LOG.info("[home] res {}", x)));
        shot(5, "home_res_morning");
        // день: офис — люди за столами
        cmd(5, "time set 4000");
        server(5, s -> {
            // ближайший офис: этаж, где работает больше всего жильцов
            Vec3d best = null;
            for (int i = 0; i < Residents.poolSize(); i++) {
                Vec3d[] p = Residents.places(i);
                if (p != null && p[1] != null && p[1].y > com.yompix.novograd.world.Island.G + 5) {
                    best = p[1];
                    break;
                }
            }
            if (best != null) look(pl(s), best.add(3, 1, 3), best);
        });
        server(120, s -> Residents.debug(pl(s).getServerWorld()).forEach(x -> Novograd.LOG.info("[home] res {}", x)));
        shot(5, "home_res_office");
        cmd(5, "time set 6000");
    }

    private static void buskers() {
        cmd(5, "time set 7000");
        for (String[] sp : new String[][]{{"downtown_plaza", "6", "4"}, {"downtown_plaza", "-8", "6"}, {"riv_strip", "4", "6"}, {"riv_beach", "6", "2"}}) {
            server(5, s -> {
                Vec3d at = Story.place(pl(s).getServerWorld(), sp[0]);
                if (at == null) return;
                var near = pl(s).getServerWorld().getEntitiesByClass(CitizenEntity.class, new net.minecraft.util.math.Box(at, at).expand(20),
                        e -> e.getCommandTags().contains(com.yompix.novograd.gta.StreetPerformers.TAG));
                Vec3d m = near.isEmpty() ? at.add(Double.parseDouble(sp[1]), 1.2, Double.parseDouble(sp[2]))
                        : near.stream().min(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(at.add(Double.parseDouble(sp[1]), 0, Double.parseDouble(sp[2]))))).get().getPos().add(0, 1.2, 0);
                look(pl(s), m.add(1.5, 0.3, -3.5), m);
            });
            shot(140, "home_busk_" + sp[0] + sp[1]);
        }
        // монетка гитаристу
        server(5, s -> {
            Vec3d at = Story.place(pl(s).getServerWorld(), "downtown_plaza");
            Vec3d m = at.add(6, 1.2, 4);
            look(pl(s), m.add(0, 0.5, -3), m);
        });
        run(60, c -> {
            CitizenEntity best = null;
            for (var e : c.world.getEntities())
                if (e instanceof CitizenEntity ce && ce.getCustomName() != null && ce.getCustomName().getString().contains("монетка")
                        && (best == null || ce.squaredDistanceTo(c.player) < best.squaredDistanceTo(c.player))) best = ce;
            Novograd.LOG.info("[home] busker near: {}", best == null ? "-" : best.getCustomName().getString());
            if (best != null) c.interactionManager.interactEntity(c.player, best, Hand.MAIN_HAND);
        });
        shot(10, "home_busk_tip");
    }

    // ------------------------------------------------------------------ помощники
    private static void send(String type, String id) {
        NbtCompound n = new NbtCompound();
        if (id != null) n.putString("id", id);
        HousingClient.send(type, n);
    }

    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    /** Встать в from и смотреть на to. */
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
                    Novograd.LOG.warn("[home] server step failed", e);
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
            Novograd.LOG.warn("[home] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
