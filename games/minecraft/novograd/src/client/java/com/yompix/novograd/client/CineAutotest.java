package com.yompix.novograd.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.story.CamPath;
import com.yompix.novograd.story.Director;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Только разработка.
 * <ul>
 *   <li>-Dnovograd.autotest=cine:глава:миссия:шаг — сыграть кат-сцену сюжета и (с -Dnovograd.rec=путь.mp4) записать её
 *       покадрово; камера проверяется по кадрам (строка «[cam]» в логе, рядом .cam.tsv). Игра на время записи замедлена
 *       (/tick rate), видео всё равно 30 к/с реального времени сцены.</li>
 *   <li>-Dnovograd.autotest=camaudit — проверить пути камеры ВСЕХ кат-сцен сюжета по карте (сервер): строки
 *       «[camaudit]» — сколько точек пролёта задевает дома по старому пути и по выбранному CamPath.</li>
 * </ul>
 */
public final class CineAutotest {
    private CineAutotest() {}

    private record Step(int delay, Consumer<MinecraftClient> action) {}

    private static final List<Step> PRE = new ArrayList<>(), STEPS = new ArrayList<>();
    private static int preIndex = -1, preTimer, index = -1, timer;
    private static final String P = "Dev";
    private static boolean watching, seen;
    private static int watchTicks, after;

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !(mode.startsWith("cine") || mode.equals("camaudit"))) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        com.yompix.novograd.client.story.CineRec.armed = false;
        PRE.add(new Step(60, c -> c.createIntegratedServerLoader().start(System.getProperty("novograd.cineworld", "Island"), () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()))));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        if (mode.equals("camaudit")) {
            server(20, CineAutotest::audit);
            run(40, MinecraftClient::scheduleStop);
        } else {
            String[] a = mode.split(":");
            int ch = Integer.parseInt(a[1]), mi = Integer.parseInt(a[2]), st = Integer.parseInt(a[3]);
            boolean rec = System.getProperty("novograd.rec") != null;
            server(40, s -> {
                ServerPlayerEntity p = pl(s);
                var d = Life.get(p);
                d.storyStarted = true;
                d.storyDone = false;
                if (ch >= 5) d.flags.add("end_cop");
                d.chapter = ch;
                d.mission = mi;
                d.step = st;
                d.age = 22;
                Story.applyAge(p, 22);
                Life.sync(p);
            });
            cmd(5, "time set " + System.getProperty("novograd.cinetime", "13500"));
            cmd(5, "weather clear");
            // запись: игра медленнее (машина рисует медленно), кадры видео всё равно ровно через 1/30 с времени сцены
            if (rec) cmd(5, "tick rate " + System.getProperty("novograd.rectps", "4"));
            // сцена, что продолжилась из сохранения при входе, — пропустить (Пробел), записываем только нашу
            run(10, c -> {
                if (c.currentScreen instanceof com.yompix.novograd.client.story.CutsceneScreen cs) cs.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
            });
            server(30, s -> {
                var d = Life.get(pl(s));
                d.chapter = ch;
                d.mission = mi;
                d.step = st;
                Life.sync(pl(s));
            });
            run(1, c -> com.yompix.novograd.client.story.CineRec.armed = true);
            server(5, s -> Story.reenter(pl(s)));
            run(1, c -> watching = true);
        }
        ClientTickEvents.END_CLIENT_TICK.register(CineAutotest::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CineAutotest::watch);
    }

    /** Ждём, пока кат-сцена откроется и закроется, потом выходим. */
    private static void watch(MinecraftClient c) {
        if (!watching) return;
        watchTicks++;
        boolean open = c.currentScreen instanceof com.yompix.novograd.client.story.CutsceneScreen;
        if (open) seen = true;
        if (seen && !open) after++;
        if (after == 2 || watchTicks > 20 * 60 * 8) {
            watching = false;
            if (!seen) Novograd.LOG.warn("[cine] кат-сцена так и не открылась");
            Novograd.LOG.info("[cine] done seen={} ticks={}", seen, watchTicks);
            if (com.yompix.novograd.client.story.CineRec.on) com.yompix.novograd.client.story.CineRec.stop();
            var s = c.getServer();
            if (s != null) s.execute(() -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), "tick rate 20"));
            STEPS.add(new Step(20, MinecraftClient::scheduleStop));
            index = STEPS.size() - 1;
            timer = 20;
        }
    }

    /** Все кат-сцены сюжета: путь камеры по карте — старый (дуга по умолчанию) и выбранный CamPath. */
    private static void audit(IntegratedServer s) {
        ServerPlayerEntity p = pl(s);
        var w = p.getServerWorld();
        int scenes = 0, shotsN = 0, badOld = 0, badNew = 0, endsInside = 0, holds = 0, cranes = 0, shorts = 0;
        int ci = 0;
        for (JsonElement ce : Story.root().getAsJsonArray("chapters")) {
            int mi = 0;
            for (JsonElement me : ce.getAsJsonObject().getAsJsonArray("missions")) {
                int si = 0;
                for (JsonElement se : me.getAsJsonObject().getAsJsonArray("steps")) {
                    JsonObject st = se.getAsJsonObject();
                    if ("cutscene".equals(st.has("type") ? st.get("type").getAsString() : "") && st.has("shots")) {
                        scenes++;
                        int k = 0, idx = 0;
                        for (JsonElement sh : st.getAsJsonArray("shots")) {
                            JsonObject o = sh.getAsJsonObject();
                            double[] f = o.has("from") ? Director.camera(w, o.get("from").getAsString()) : null;
                            double[] t = o.has("to") ? Director.camera(w, o.get("to").getAsString()) : f;
                            if (f == null && (o.has("track") || o.has("follow"))) {
                                idx++;
                                continue;
                            }
                            if (f == null) continue;
                            for (double[] pt : new double[][]{f, t}) {
                                w.getChunk(((int) Math.floor(pt[0])) >> 4, ((int) Math.floor(pt[2])) >> 4);
                            }
                            // середина пути тоже загружена
                            w.getChunk(((int) Math.floor((f[0] + t[0]) / 2)) >> 4, ((int) Math.floor((f[2] + t[2]) / 2)) >> 4);
                            shotsN++;
                            int old = CamPath.blocked(w, f, t, idx, 0);
                            double[] fl = CamPath.lift(w, f), tl = CamPath.lift(w, t);
                            if (fl[1] != f[1] || tl[1] != t[1]) endsInside++;
                            int[] plan = CamPath.plan(w, fl, tl, idx);
                            if (old > 0) badOld++;
                            if (plan[1] > 0) badNew++;
                            if (plan[0] == CamPath.HOLD) holds++;
                            if (plan[0] == CamPath.CRANE) cranes++;
                            if (plan[0] > CamPath.CRANE && plan[0] < CamPath.HOLD) shorts++;
                            if (old > 0 || plan[1] > 0)
                                Novograd.LOG.info("[camaudit] сцена {}:{}:{} «{}» план {}: старый путь задевает {} из {}, новый (вариант {}) — {}",
                                        ci, mi, si, st.has("id") ? st.get("id").getAsString() : "", idx, old, CamPath.SAMPLES + 1, plan[0], plan[1]);
                            idx++;
                            k++;
                        }
                    }
                    si++;
                }
                mi++;
            }
            ci++;
        }
        Novograd.LOG.info("[camaudit] итого: кат-сцен {}, пролётов {}, старый путь сквозь дома: {}, новый: {}, концы в стене (подняты): {}, «кран» над крышами: {}, короткий наезд: {}, неподвижный кадр у Б: {}",
                scenes, shotsN, badOld, badNew, endsInside, cranes, shorts, holds);
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
            Novograd.LOG.warn("[cine] step failed", e);
        }
        index++;
        if (index < STEPS.size()) timer = STEPS.get(index).delay();
    }
}
