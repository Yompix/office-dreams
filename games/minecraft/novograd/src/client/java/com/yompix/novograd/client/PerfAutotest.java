package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.perf.Perf;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только разработка: -Dnovograd.autotest=perf — замер в людном центре: 40 с стоим, 30 с едем по улице.
 * Строки лога «[perf]» (сервер, Perf) и «[perfc]» (кадры клиента). Для сравнения «до/после».
 * perf:walk — вместо проезда идём пешком. Запись видео: -Dnovograd.rec=путь.mp4 (Recorder, кадр за кадром).
 */
public final class PerfAutotest {
    private PerfAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";
    static String phase = "";
    /** Маршрут «проезда»: из центра на восток по улице. */
    static double moveX0 = 120.5, moveZ0 = -213.5, moveSpeed;
    static int moveTicks;

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("perf")) return;
        boolean walk = mode.contains("walk");
        com.yompix.novograd.client.story.YompixSplash.markShown();
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(System.getProperty("novograd.perfworld", "Island"), () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        cmd(5, "gamemode creative " + P);
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        server(5, s -> {
            var d = Life.get(pl(s));
            d.storyStarted = true;
            d.storyDone = true;
            Life.sync(pl(s));
            var p = pl(s);
            var w = p.getServerWorld();
            p.getAbilities().flying = true;
            p.sendAbilitiesUpdate();
            w.getChunk(((int) moveX0) >> 4, ((int) moveZ0) >> 4); // без загрузки участка высота земли — дно мира (игрок оказывался под землёй)
            int top = Story.topY(w, (int) moveX0, (int) moveZ0);
            p.teleport(w, moveX0, top + 1, moveZ0, -90, 5);
            Novograd.LOG.info("[perf] старт: игрок на {} {} {} (земля {})", moveX0, top + 1, moveZ0, top);
        });
        if (System.getProperty("novograd.perflevel") != null) {
            int lv = Integer.getInteger("novograd.perflevel", 1);
            run(1, c -> { NovogradConfig.get().perf = lv; NovogradConfig.apply(); });
        }
        if (System.getProperty("novograd.rec") != null) {
            // запись проезда: игра медленнее, кадры видео ровно через 1/30 с игрового времени
            cmd(5, "tick rate " + System.getProperty("novograd.rectps", "5"));
        }
        run(5, c -> phase = "warmup");
        run(600, c -> phase = "stand");                  // 30 с — город наполняется
        run(800, c -> {                                  // 40 с стоим
            phase = "move";
            if (System.getProperty("novograd.rec") != null) com.yompix.novograd.client.story.CineRec.start(System.getProperty("novograd.rec"), 30);
            moveSpeed = walk ? 0.22 : 0.7;               // блоков за тик: шаг ~4.3 м/с, машина ~50 км/ч
            moveTicks = 0;
        });
        run(600, c -> {
            phase = "end";
            if (com.yompix.novograd.client.story.CineRec.on) com.yompix.novograd.client.story.CineRec.stop();
        });
        run(40, c -> Novograd.LOG.info("[perf] done"));
        run(20, MinecraftClient::scheduleStop);
        ClientTickEvents.END_CLIENT_TICK.register(PerfAutotest::tick);
        ClientTickEvents.END_CLIENT_TICK.register(PerfAutotest::move);
        HudRenderCallback.EVENT.register((ctx, t) -> frame());
    }

    private static double moveY = Double.NaN;

    /** Движение по улице — на клиенте (как будто игрок едет сам): позиция между тиками сглаживается, видео ровное. */
    private static void move(MinecraftClient c) {
        if (!phase.equals("move") || c.player == null || c.world == null) return;
        moveTicks++;
        double x = moveX0 + moveTicks * moveSpeed;
        if (Double.isNaN(moveY)) moveY = c.player.getY() + (moveSpeed > 0.5 ? 1.2 : 0.0); // сервер уже поставил игрока над улицей
        var p = c.player;
        p.getAbilities().flying = true;
        p.setVelocity(0, 0, 0);
        p.setPosition(x, moveY, moveZ0);
        p.setYaw(-90);
        p.setPitch(5);
    }

    // ---------------- кадры клиента
    private static long last, winStart;
    private static final long[] FR = new long[4096];
    private static int fn;

    private static void frame() {
        long now = System.nanoTime();
        if (last != 0 && fn < FR.length) FR[fn++] = now - last;
        last = now;
        if (winStart == 0) winStart = now;
        if (now - winStart < 5_000_000_000L || fn == 0) return;
        long[] a = java.util.Arrays.copyOf(FR, fn);
        java.util.Arrays.sort(a);
        double sum = 0;
        for (long v : a) sum += v;
        double avg = sum / fn / 1e6;
        double p95 = a[(int) (fn * 0.95)] / 1e6, max = a[fn - 1] / 1e6;
        var mc = MinecraftClient.getInstance();
        int ents = mc.world == null ? 0 : mc.world.getRegularEntityCount();
        Novograd.LOG.info(String.format(java.util.Locale.ROOT, "[perfc] phase=%s fps=%.1f frame avg=%.1f p95=%.1f max=%.1f ms clientEntities=%d | server avg=%.2f p95=%.2f citizens=%d cars=%d",
                phase, fn / ((now - winStart) / 1e9), avg, p95, max, ents, Perf.lastAvg, Perf.lastP95, Perf.lastCitizens, Perf.lastCars));
        fn = 0;
        winStart = now;
    }

    private static ServerPlayerEntity pl(IntegratedServer s) {
        return s.getPlayerManager().getPlayer(P);
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
            Novograd.LOG.warn("[perf] step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
