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
        if (mode == null || !(mode.startsWith("cine") || mode.equals("camaudit") || mode.startsWith("loadaudit") || mode.startsWith("shotexport") || mode.startsWith("fpshunt") || mode.startsWith("uishots"))) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        com.yompix.novograd.client.story.CineRec.armed = false;
        PRE.add(new Step(60, CineAutotest::openWorld));
        run(100, c -> { if (c.player != null && c.player.isDead()) c.player.requestRespawn(); });
        run(5, c -> { if (c.currentScreen != null) c.setScreen(null); });
        if (mode.equals("camaudit")) {
            server(20, CineAutotest::audit);
            run(40, MinecraftClient::scheduleStop);
        } else if (mode.startsWith("loadaudit")) {
            cmd(5, "gamemode creative " + P);
            cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
            cmd(5, "time set 6000");
            cmd(5, "weather clear");
            cmd(5, "gamerule doDaylightCycle false");
            run(1, c -> LoadAudit.begin(mode));
            ClientTickEvents.END_CLIENT_TICK.register(LoadAudit::tick);
        } else if (mode.startsWith("uishots")) {
            // экран «как у игрока»: цель миссии, подсказка, награда, строка над хотбаром, чат (мод и игрок), субтитры сцены
            String tag = System.getProperty("novograd.uitag", "ui");
            cmd(5, "gamemode survival " + P);
            cmd(5, "effect give " + P + " minecraft:resistance 99999 4 true");
            cmd(5, "time set 6000");
            cmd(5, "weather clear");
            server(5, s -> {
                var d = Life.get(pl(s));
                d.storyStarted = true;
                d.storyDone = false;
                d.chapter = 0;
                d.mission = 2;
                d.step = 1;
                d.flags.add("started:0:2");
                Story.reenter(pl(s));
                Life.sync(pl(s));
            });
            run(60, c -> {
                if (c.currentScreen != null) c.setScreen(null);
                c.player.networkHandler.sendChatMessage("Где встречаемся?");
            });
            server(5, s -> {
                var p = pl(s);
                p.sendMessage(net.minecraft.text.Text.literal("Новоград: в «Неоне» сегодня вечеринка — вход свободный до полуночи, приходи с друзьями и не забудь маску, будет весело и громко!").formatted(net.minecraft.util.Formatting.AQUA), false);
                p.sendMessage(net.minecraft.text.Text.literal("+$120").formatted(net.minecraft.util.Formatting.GREEN), true);
            });
            run(5, c -> {
                com.yompix.novograd.client.story.ClientStory.achievement("Первые колёса", "Угнать первую машину");
                com.yompix.novograd.client.ui.Quiet.hint("", "Телефон — G. Карта — M.");
                if (!com.yompix.novograd.story.Stage.ON) com.yompix.novograd.client.OutdoorClient.toast("Навык: вождение", "Уровень 2", 0xFF66BB6A, 40);
            });
            server(4, s -> pl(s).sendMessage(net.minecraft.text.Text.literal("+$80").formatted(net.minecraft.util.Formatting.GREEN), true));
            run(30, c -> shot(c, tag + "_hud"));
            run(5, c -> c.options.getChatVisibility()); // чат виден (последние строки ещё не погасли)
            run(5, c -> c.setScreen(new net.minecraft.client.gui.screen.ChatScreen("")));
            run(10, c -> shot(c, tag + "_chat"));
            run(5, c -> c.setScreen(null));
            server(5, s -> {
                var d = Life.get(pl(s));
                d.chapter = 3;
                d.mission = 1;
                d.step = 0;
                d.flags.add("started:3:1");
                Story.reenter(pl(s));
            });
            run(200, c -> shot(c, tag + "_cutscene"));
            run(40, MinecraftClient::scheduleStop);
        } else if (mode.startsWith("fpshunt")) {
            // мир открыт — дальше сценарии FpsHunt
        } else if (mode.startsWith("shotexport")) {
            cmd(5, "gamemode spectator " + P);
            run(1, c -> ShotExport.begin(mode));
            ClientTickEvents.END_CLIENT_TICK.register(ShotExport::tick);
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
                d.flags.add("started:" + ch + ":" + mi); // сцена на метке миссии: миссия уже «начата», иначе ждёт игрока
                Life.sync(pl(s));
            });
            run(1, c -> com.yompix.novograd.client.story.CineRec.armed = true);
            server(5, s -> Story.reenter(pl(s)));
            run(1, c -> watching = true);
        }
        ClientTickEvents.END_CLIENT_TICK.register(CineAutotest::tick);
        ClientTickEvents.END_CLIENT_TICK.register(CineAutotest::watch);
    }

    /** Мир для теста: -Dnovograd.cineworld (по умолчанию Island); нет такого — создаём новый остров с этим именем. */
    private static void openWorld(MinecraftClient c) {
        String name = System.getProperty("novograd.cineworld", "Island");
        boolean exists;
        try {
            exists = c.getLevelStorage().levelExists(name);
        } catch (RuntimeException e) {
            exists = false;
        }
        if (exists) {
            c.createIntegratedServerLoader().start(name, () -> c.setScreen(new com.yompix.novograd.client.menu.LifeMenuScreen()));
            return;
        }
        var data = new net.minecraft.resource.DataConfiguration(new net.minecraft.resource.DataPackSettings(List.of("vanilla", "fabric"), List.of()),
                net.minecraft.resource.featuretoggle.FeatureFlags.DEFAULT_ENABLED_FEATURES);
        var info = new net.minecraft.world.level.LevelInfo(name, net.minecraft.world.GameMode.CREATIVE, false,
                net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.GameRules(), data);
        var city = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD_PRESET, Novograd.id("city"));
        c.createIntegratedServerLoader().createAndStart(name, info, new net.minecraft.world.gen.GeneratorOptions(20251120L, false, false),
                drm -> drm.get(net.minecraft.registry.RegistryKeys.WORLD_PRESET).getOrThrow(city).createDimensionsRegistryHolder(), new com.yompix.novograd.client.menu.LifeMenuScreen());
    }

    private static void shot(MinecraftClient c, String name) {
        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), t -> Novograd.LOG.info("[ui] {}", t.getString()));
    }

    static ServerPlayerEntity player(IntegratedServer s) {
        return pl(s);
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
