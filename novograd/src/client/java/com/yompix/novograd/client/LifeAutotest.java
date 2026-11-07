package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.world.Island;
import com.yompix.novograd.world.Leisure;
import com.yompix.novograd.world.LifeAi;
import com.yompix.novograd.world.Residents;
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
 * Только для разработки: -Dnovograd.autotest=life (или life:beach, life:city, life:night, life:rain, life:shop) —
 * повседневная жизнь прохожих: улица утром, пляжи днём, магазины, вечер у клуба и на «Стрипе», дождь.
 * Новый мир (-Dnovograd.newworld=true) или копия Fresh2; снимки run/screenshots/life_*.png, в логе — [life] счётчики и mspt.
 */
public final class LifeAutotest {
    private LifeAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("life")) return;
        String only = mode.contains(":") ? mode.split(":")[1] : "";
        com.yompix.novograd.client.story.YompixSplash.markShown();
        if (Boolean.getBoolean("novograd.newworld")) PRE.add(new Step(60, c -> {
            var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                    net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
            var info = new net.minecraft.world.level.LevelInfo("LifeTest" + System.currentTimeMillis() % 100000, net.minecraft.world.GameMode.CREATIVE, false,
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
        cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
        cmd(5, "effect give " + P + " minecraft:invisibility 99999 0 true");
        server(5, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
            Novograd.LOG.info("[life] память Java {} МБ, масштаб людей {}", Runtime.getRuntime().maxMemory() / 1048576, Leisure.scale());
        });
        int G = Island.G;
        if (only.isEmpty() || only.contains("city")) // утро в деловом центре: час пик
            scene("city_morning", 2000, false, new Vec3d(398, G + 6, -397), new Vec3d(445, G + 1, -404), 700);
        if (only.isEmpty() || only.contains("beach")) {
            scene("beach_noon", 6500, false, new Vec3d(-568, G + 8, -548), new Vec3d(-608, G + 1, -556), 900);
            closeups("beach_noon");
            scene("rivbeach_noon", 7000, false, new Vec3d(-1822, G + 8, -140), new Vec3d(-1858, G + 1, -150), 900);
            closeups("rivbeach_noon");
        }
        if (only.isEmpty() || only.contains("shop")) // магазины пригорода днём
            scene("shops_day", 8000, false, new Vec3d(-262, G + 6, 108), new Vec3d(-240, G + 1, 124), 700);
        if (only.isEmpty() || only.contains("night")) {
            scene("club_evening", 15200, false, new Vec3d(352, G + 5, -250), new Vec3d(365, G + 1, -266), 700);
            scene("strip_evening", 15500, false, new Vec3d(-1300, G + 6, 42), new Vec3d(-1320, G + 1, 24), 700);
        }
        if (only.isEmpty() || only.contains("rain")) {
            cmd(5, "weather rain 99999");
            scene("city_rain", 4000, true, new Vec3d(398, G + 6, -397), new Vec3d(445, G + 1, -404), 600);
            cmd(5, "weather clear 99999");
        }
        run(20, c -> Novograd.LOG.info("[life] done"));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(LifeAutotest::tick);
    }

    /** Время, точка обзора; ждём, пока город «наполнится», три снимка с интервалом и отчёт. */
    private static void scene(String name, int tod, boolean rain, Vec3d from, Vec3d to, int wait) {
        cmd(5, "time set " + tod);
        server(5, s -> look(pl(s), from, to));
        server(wait / 2, s -> report(s, name + "_mid"));
        shot(wait / 2, "life_" + name + "_1");
        shot(160, "life_" + name + "_2");
        server(5, s -> report(s, name));
        shot(200, "life_" + name + "_3");
    }

    /** Подойти поближе к людям на месте: к трём разным занятиям (чтобы видеть позы). */
    private static void closeups(String name) {
        for (int i = 0; i < 3; i++) {
            final int k = i;
            server(10, s -> {
                ServerPlayerEntity p = pl(s);
                ServerWorld w = p.getServerWorld();
                List<CitizenEntity> list = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(50, 20, 50), c -> c.getActivity() != CitizenEntity.Activity.IDLE);
                // разные позы: сначала лежащие/плавающие/сидящие, потом остальные
                list.sort(java.util.Comparator.comparingInt(c -> switch (c.getActivity()) {
                    case SUNBATHE -> 0;
                    case SWIM -> 1;
                    case SIT, SITCROSS, SIT_EAT -> 2;
                    default -> 3;
                }));
                if (list.isEmpty()) return;
                CitizenEntity c = list.get(Math.min(k, list.size() - 1));
                Vec3d at = c.getPos();
                look(p, at.add(3.2, 2.2, 3.2), at.add(0, 0.6, 0));
                Novograd.LOG.info("[life] крупно {}: {} {} {}", name, c.getName().getString(), c.getActivity(), LifeAi.doing(c));
            });
            shot(60, "life_" + name + "_close" + i);
        }
    }

    private static void report(IntegratedServer s, String name) {
        ServerPlayerEntity p = pl(s);
        ServerWorld w = p.getServerWorld();
        var st = LifeAi.stats(w, p.getPos(), 64);
        int total = st.values().stream().mapToInt(Integer::intValue).sum();
        Novograd.LOG.info("[life] {} t={} людей {} (жильцов {}), {} | {} | mspt {}", name, w.getTimeOfDay() % 24000, total, Residents.activeCount(), st,
                Leisure.debug(), String.format("%.2f", s.getAverageTickTime()));
    }

    // ------------------------------------------------------------------ помощники
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
                    Novograd.LOG.warn("[life] server step failed", e);
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
            Novograd.LOG.warn("[life] client step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
