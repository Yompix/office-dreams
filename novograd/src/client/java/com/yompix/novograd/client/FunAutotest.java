package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.gta.FunHooks;
import com.yompix.novograd.gta.VehicleJobs;
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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Только для разработки: -Dnovograd.autotest=fun — уличные события, подработки, война банд, парашют, автомат и дартс.
 * Мир «FunTest» (копия Fresh2), снимки в run/screenshots/fun_*.png.
 */
public final class FunAutotest {
    private FunAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("fun")) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        String world = System.getProperty("novograd.funworld", "FunTest");
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(world, () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode creative " + P);
        cmd(5, "time set 6000");
        cmd(5, "gamerule doDaylightCycle false");
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        server(5, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
            d.money = 20000;
            d.dirty = 0;
            Life.sync(pl(s));
        });
        boolean all = mode.equals("fun");
        if (all || mode.equals("fun_events")) events();
        if (all || mode.equals("fun_jobs")) jobs();
        if (all || mode.equals("fun_turf")) turf();
        if (all || mode.equals("fun_jump")) jump();
        if (all || mode.equals("fun_bar")) bar();
        run(20, c -> Novograd.LOG.info("[fun] done"));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(FunAutotest::tick);
    }

    // ------------------------------------------------------------------ сценарии
    private static void events() {
        // вор с сумкой: тротуар делового центра
        tp(456, -403, 180, 10);
        run(40, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        fun(5, "event snatch");
        shot(25, "fun_snatch");
        server(5, s -> { // догоняем вора
            ServerPlayerEntity p = pl(s);
            CitizenEntity t = nearest(p, "Вор", 60);
            if (t != null) p.teleport(p.getServerWorld(), t.getX() + 1, t.getY(), t.getZ() + 1, p.getYaw(), 10);
        });
        shot(30, "fun_snatch_bag");
        // авария
        fun(10, "event crash");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            CitizenEntity v = nearest(p, "Пострадавший", 120);
            if (v != null) look(p, v.getPos().add(7, 3, 7), v.getPos());
        });
        shot(40, "fun_crash");
        // ограбление магазина у пиццерии старого центра
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = Story.place(p.getServerWorld(), "pizza_door");
            if (at != null) p.teleport(p.getServerWorld(), at.x + 20, at.y, at.z + 4, 90, 10);
        });
        fun(40, "event robbery");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            CitizenEntity g = nearest(p, "Грабитель", 90);
            if (g != null) look(p, g.getPos().add(4, 2.5, 4), g.getPos());
        });
        shot(40, "fun_robbery");
        // гонка на светофоре: машина на улице z=-408, едем на восток
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            w.getChunk(300 >> 4, -406 >> 4);
            CarEntity car = new CarEntity(com.yompix.novograd.ModContent.CAR, w);
            car.refreshPositionAndAngles(300.5, Story.topY(w, 300, -406), -405.5, -90, 0);
            car.setVariant(9);
            car.setTune(true, 2, 0);
            w.spawnEntity(car);
            p.teleport(w, 300.5, car.getY() + 1, -405.5, -90, 10);
            p.startRiding(car, true);
        });
        fun(40, "event race");
        shot(20, "fun_race_offer");
        run(5, c -> {
            var n = new net.minecraft.nbt.NbtCompound();
            n.putInt("bit", CarEntity.HEADLIGHTS);
            NovogradClient.send("car_light", n);
        });
        shot(30, "fun_race_go");
        hold(40, c -> c.options.forwardKey, 120);
        shot(5, "fun_race");
        server(5, s -> pl(s).stopRiding());
    }

    private static void jobs() {
        // скорая
        server(10, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            w.getChunk(408 >> 4, -310 >> 4);
            CarEntity van = new CarEntity(com.yompix.novograd.ModContent.CAR, w);
            van.refreshPositionAndAngles(410.5, Story.topY(w, 410, -300), -300.5, 180, 0);
            van.setVariant(4);
            van.paint(VehicleJobs.AMB_COLOR);
            van.addCommandTag("nv_ambulance");
            w.spawnEntity(van);
            p.teleport(w, 410.5, van.getY() + 1, -300.5, 180, 10);
            p.startRiding(van, true);
        });
        run(30, c -> NovogradClient.send("activity"));
        shot(40, "fun_ambulance");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            CitizenEntity v = nearest(p, "✚ Раненый", 300);
            Novograd.LOG.info("[fun] patient {}", v == null ? "нет" : v.getBlockPos());
        });
        run(5, c -> NovogradClient.send("activity"));
        // пожарные
        server(10, s -> {
            ServerPlayerEntity p = pl(s);
            p.stopRiding();
            ServerWorld w = p.getServerWorld();
            CarEntity truck = new CarEntity(com.yompix.novograd.ModContent.CAR, w);
            truck.refreshPositionAndAngles(410.5, Story.topY(w, 410, -320), -320.5, 180, 0);
            truck.setVariant(11);
            truck.paint(VehicleJobs.FIRE_COLOR);
            truck.addCommandTag("nv_firetruck");
            w.spawnEntity(truck);
            p.teleport(w, 410.5, truck.getY() + 1, -320.5, 180, 10);
            p.startRiding(truck, true);
        });
        run(30, c -> NovogradClient.send("activity"));
        server(30, s -> { // подвозим машину к пожару
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            CarEntity fire = null;
            for (CarEntity c : w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(300, 40, 300), c -> c.getCommandTags().contains("nv_burning")))
                if (fire == null || c.squaredDistanceTo(p) < fire.squaredDistanceTo(p)) fire = c;
            if (fire == null || !(p.getVehicle() instanceof CarEntity truck)) return;
            p.stopRiding();
            Vec3d at = fire.getPos().add(10, 0, 0);
            w.getChunk((int) at.x >> 4, (int) at.z >> 4);
            truck.refreshPositionAndAngles(at.x, Story.topY(w, (int) at.x, (int) at.z), at.z, 90, 0);
            p.teleport(w, at.x, truck.getY() + 1, at.z, 90, 8);
            p.startRiding(truck, true);
            Novograd.LOG.info("[fun] fire at {}", fire.getBlockPos());
        });
        run(40, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        hold(5, c -> c.options.useKey, 50);
        shot(1, "fun_fire_hose");
        run(30, c -> {});
        shot(1, "fun_fire_after");
        run(5, c -> NovogradClient.send("activity"));
        server(5, s -> pl(s).stopRiding());
    }

    private static void turf() {
        tp(430, 300, 180, 15);
        run(40, c -> c.setScreen(new com.yompix.novograd.client.screen.MapScreen()));
        shot(60, "fun_map");
        run(5, c -> c.setScreen(null));
        fun(10, "war");
        run(40, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        shot(20, "fun_war");
        server(5, s -> FunHooks.debug(pl(s), "event ambush"));
        shot(60, "fun_ambush");
    }

    private static void jump() {
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        fun(10, "jump");
        shot(50, "fun_freefall");
        hold(5, c -> c.options.jumpKey, 3);
        shot(40, "fun_chute");
        hold(5, c -> c.options.forwardKey, 60);
        shot(1, "fun_chute2");
        run(20, c -> c.options.setPerspective(Perspective.FIRST_PERSON));
        run(5, c -> c.player.setPitch(-60));
        shot(10, "fun_chute_fp");
        run(5, c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
        run(400, c -> {}); // приземление
        shot(1, "fun_landed");
    }

    private static void bar() {
        fun(10, "arcade");
        run(50, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.screen.ArcadeScreen a) a.steer(-1); });
        run(30, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.screen.ArcadeScreen a) a.steer(1); });
        shot(30, "fun_arcade");
        run(5, c -> c.setScreen(null));
        fun(10, "darts");
        shot(30, "fun_darts");
        for (int i = 0; i < 3; i++) run(20, c -> { if (c.currentScreen instanceof com.yompix.novograd.client.screen.DartsScreen d) d.throwDart(); });
        shot(10, "fun_darts2");
        run(5, c -> c.setScreen(null));
    }

    // ------------------------------------------------------------------ помощники
    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
    }

    private static CitizenEntity nearest(ServerPlayerEntity p, String name, double r) {
        CitizenEntity best = null;
        for (CitizenEntity c : p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(r), c -> c.hasCustomName() && c.getCustomName().getString().startsWith(name)))
            if (best == null || c.squaredDistanceTo(p) < best.squaredDistanceTo(p)) best = c;
        return best;
    }

    private static void look(ServerPlayerEntity p, Vec3d from, Vec3d at) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(at.x - from.x), at.z - from.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(at.y - from.y, Math.hypot(at.x - from.x, at.z - from.z)));
        p.teleport(p.getServerWorld(), from.x, from.y, from.z, yaw, pitch);
    }

    private static void tp(int x, int z, float yaw, float pitch) {
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            ServerWorld w = p.getServerWorld();
            p.stopRiding();
            w.getChunk(x >> 4, z >> 4);
            p.teleport(w, x + 0.5, Story.topY(w, x, z) + 0.1, z + 0.5, yaw, pitch);
        });
    }

    private static void fun(int delay, String what) {
        server(delay, s -> Novograd.LOG.info("[fun] {} → {}", what, FunHooks.debug(pl(s), what)));
    }

    private static void hold(int delay, Function<MinecraftClient, KeyBinding> kb, int ticks) {
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
                    Novograd.LOG.warn("[fun] step failed", e);
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
            Novograd.LOG.warn("[fun] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
