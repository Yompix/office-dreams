package com.yompix.novograd.client;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.perf.Perf;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Только разработка: -Dnovograd.autotest=fpshunt — «где падает FPS»: сценарии по очереди (быстрая езда по центру,
 * Ривьере, порту; ночной неон; дождь; погоня 5★; взрывы машин; толпа; телефон/карта/инвентарь; старт кат-сцены;
 * езда 200+ км/ч в незагруженные районы; сохранение мира). На каждый сценарий — строка «[fps]»: кадр avg/p99/max,
 * рывков > 50 мс, сборок мусора, тик сервера avg/max, сущностей и частиц; на каждый рывок — строка «[hitch]» с тем,
 * что было в этот кадр (GC, очередь стройки участков, экран, частицы). Итог — «[fps] итого».
 */
public final class FpsHunt {
    private FpsHunt() {}

    private record Seg(String name, int ticks, Consumer<MinecraftClient> start) {}

    private static final List<Seg> SEGS = new ArrayList<>();
    private static int seg = -1, segTicks, warm = 300;
    private static boolean active, started;
    private static final String P = "Dev";
    // движение «на машине»: клиент двигает игрока по прямой
    private static double mx, mz, my = Double.NaN, mdx, mdz, speed;
    private static float myaw;

    public static void init() {
        String mode = System.getProperty("novograd.autotest");
        if (mode == null || !mode.startsWith("fpshunt")) return;
        com.yompix.novograd.client.story.YompixSplash.markShown();
        add("стоим_центр", 200, c -> place(c, "dt_2", 0, 0));
        add("езда_центр", 300, c -> drive(c, "dt_1", 0.9, -90));
        add("езда_ривьера", 300, c -> drive(c, "riv_strip", 0.9, 90));
        add("езда_порт", 300, c -> drive(c, "port_warehouse", 0.9, 0));
        add("ночь_неон", 260, c -> { cmd(c, "time set 18000"); drive(c, "club_neon", 0.4, -90); });
        add("дождь", 260, c -> { cmd(c, "time set 6000"); cmd(c, "weather rain"); drive(c, "dt_2", 0.7, 180); });
        add("погоня_5звёзд", 400, c -> { cmd(c, "weather clear"); place(c, "dt_3", 0, 0); server(c, p -> com.yompix.novograd.gta.Cheats.apply(p, "LJSPQK")); });
        add("взрывы", 200, c -> server(c, p -> { com.yompix.novograd.gta.Cheats.apply(p, "ASNAEB"); com.yompix.novograd.gta.Cheats.apply(p, "CPKTNWT"); }));
        add("телефон_карта_инвентарь", 200, c -> { server(c, p -> com.yompix.novograd.gta.Cheats.apply(p, "ASNAEB")); ui = 0; });
        add("старт_катсцены", 260, c -> server(c, p -> {
            var d = Life.get(p);
            d.chapter = 0;
            d.mission = 7;
            d.step = 0;
            d.flags.add("started:0:7");
            Story.reenter(p);
        }));
        add("быстро_новые_районы", 300, c -> { closeScreens(c); drive(c, "outer_east", 3.0, 0); });
        add("сохранение", 120, c -> { closeScreens(c); place(c, "dt_2", 0, 0); cmd(c, "save-all flush"); });
        // 0.9.5: тяжёлые места города — стоим по 30 с, сервер: MSPT и сколько сущностей вокруг (не копятся ли)
        for (String lm : new String[]{"hospital", "dt_2", "riv_strip", "port_warehouse", "police_station", "shop_clothes", "hospital"})
            add("место_" + lm, 600, c -> { closeScreens(c); cmd(c, "time set 6000"); cmd(c, "weather clear"); place(c, lm, 0, 0); });
        active = true;
        ClientTickEvents.END_CLIENT_TICK.register(FpsHunt::tick);
        HudRenderCallback.EVENT.register((ctx, t) -> frame());
    }

    private static int ui = -1, added;

    private static void add(String n, int t, Consumer<MinecraftClient> s) {
        String only = System.getProperty("novograd.fpsonly"); // номера сценариев через запятую (с 0)
        int idx = added++;
        if (only != null && !("," + only + ",").contains("," + idx + ",")) return;
        SEGS.add(new Seg(n, t, s));
    }

    private static void cmd(MinecraftClient c, String command) {
        var s = c.getServer();
        if (s != null) s.execute(() -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), command));
    }

    private static void server(MinecraftClient c, Consumer<ServerPlayerEntity> f) {
        var s = c.getServer();
        if (s != null) s.execute(() -> {
            var p = s.getPlayerManager().getPlayer(P);
            if (p != null) f.accept(p);
        });
    }

    private static void closeScreens(MinecraftClient c) {
        if (c.currentScreen instanceof com.yompix.novograd.client.story.CutsceneScreen cs) cs.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE, 0, 0);
        else if (c.currentScreen != null) c.setScreen(null);
    }

    private static void place(MinecraftClient c, String where, double dx, double dz) {
        speed = 0;
        server(c, p -> {
            Vec3d v = Story.place(p.getServerWorld(), where);
            if (v == null) return;
            p.getServerWorld().getChunk(((int) v.x) >> 4, ((int) v.z) >> 4);
            p.teleport(p.getServerWorld(), v.x + dx, Story.topY(p.getServerWorld(), (int) v.x, (int) v.z) + 1.5, v.z + dz, 0, 5);
        });
        my = Double.NaN;
    }

    private static void drive(MinecraftClient c, String where, double sp, float yaw) {
        place(c, where, 0, 0);
        speed = -sp; // старт через 20 тиков (пока сервер перенесёт игрока)
        myaw = yaw;
    }

    private static void tick(MinecraftClient c) {
        if (!active || c.player == null || c.world == null) return;
        if (!started) {
            if (--warm > 0) {
                if (warm == 290) {
                    cmd(c, "gamemode creative " + P);
                    cmd(c, "effect give " + P + " minecraft:resistance 99999 4 true");
                    cmd(c, "gamerule doDaylightCycle false");
                    cmd(c, "time set 6000");
                    server(c, p -> {
                        var d = Life.get(p);
                        d.storyStarted = true;
                        d.storyDone = true;
                        Life.sync(p);
                        p.getAbilities().flying = true;
                        p.sendAbilitiesUpdate();
                    });
                }
                if (c.currentScreen != null && !(c.currentScreen instanceof com.yompix.novograd.client.story.CutsceneScreen)) c.setScreen(null);
                return;
            }
            started = true;
            nextSeg(c);
            return;
        }
        segTicks++;
        if (speed < 0 && segTicks > 20) {
            speed = -speed;
            mx = c.player.getX();
            mz = c.player.getZ();
            my = c.player.getY() + 1.5;
            double r = Math.toRadians(myaw);
            mdx = -Math.sin(r);
            mdz = Math.cos(r);
        }
        if (speed > 0) {
            mx += mdx * speed;
            mz += mdz * speed;
            var p = c.player;
            p.getAbilities().flying = true;
            p.setVelocity(0, 0, 0);
            p.setPosition(mx, my, mz);
            p.setYaw(myaw);
            p.setPitch(8);
        }
        if (ui >= 0) { // телефон → карта → инвентарь, по 60 тиков
            if (segTicks == 10) c.setScreen(new com.yompix.novograd.client.screen.PhoneScreen("home"));
            if (segTicks == 70) c.setScreen(new com.yompix.novograd.client.screen.MapScreen());
            if (segTicks == 130) c.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(c.player));
            if (segTicks == 190) {
                c.setScreen(null);
                ui = -1;
            }
        }
        if (segTicks >= SEGS.get(seg).ticks()) {
            report(SEGS.get(seg).name());
            nextSeg(c);
        }
    }

    private static void nextSeg(MinecraftClient c) {
        seg++;
        segTicks = 0;
        if (seg >= SEGS.size()) {
            active = false;
            summary();
            c.scheduleStop();
            return;
        }
        speed = 0;
        resetStats();
        SEGS.get(seg).start().accept(c);
        Novograd.LOG.info("[fps] → {}", SEGS.get(seg).name());
    }

    // ---------------- кадры
    private static long last;
    private static final long[] FR = new long[20000];
    private static int fn, hitches;
    private static long gcCount0, gcTime0, lastGc;
    private static final List<String> SUMMARY = new ArrayList<>();

    private static long gcCount() {
        long n = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) n += Math.max(0, b.getCollectionCount());
        return n;
    }

    private static long gcTime() {
        long n = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) n += Math.max(0, b.getCollectionTime());
        return n;
    }

    private static void resetStats() {
        fn = 0;
        hitches = 0;
        gcCount0 = gcCount();
        gcTime0 = gcTime();
        lastGc = gcCount0;
        Perf.tickMaxNs = 0;
        Perf.tickSumNs = 0;
        Perf.tickCount = 0;
    }

    private static void frame() {
        long now = System.nanoTime();
        long dt = last == 0 ? 0 : now - last;
        last = now;
        if (!active || !started || seg < 0 || dt == 0) return;
        if (fn < FR.length) FR[fn++] = dt;
        long g = gcCount();
        if (dt > 50_000_000L) {
            hitches++;
            var mc = MinecraftClient.getInstance();
            if (hitches <= 6)
                Novograd.LOG.info(String.format(Locale.ROOT, "[hitch] %s +%dт кадр %.0f мс: GC=%s экран=%s участки=[%s] частиц=%s сущностей=%d",
                        SEGS.get(seg).name(), segTicks, dt / 1e6, g != lastGc ? "да" : "нет",
                        mc.currentScreen == null ? "-" : mc.currentScreen.getClass().getSimpleName(),
                        mc.worldRenderer.getChunksDebugString(), mc.particleManager.getDebugString(),
                        mc.world == null ? 0 : mc.world.getRegularEntityCount()));
        }
        lastGc = g;
    }

    private static void report(String name) {
        var srv = MinecraftClient.getInstance().getServer();
        if (srv != null) srv.execute(() -> { // 0.9.5: сервер в этом месте — тик и сущности по видам
            var p = srv.getPlayerManager().getPlayer(P);
            if (p == null) return;
            java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
            for (var e : p.getServerWorld().getOtherEntities(p, p.getBoundingBox().expand(64)))
                kinds.merge(e.getType().getUntranslatedName(), 1, Integer::sum);
            Novograd.LOG.info("[fps] сервер {}: mspt {} сущностей {}", name, String.format("%.2f", srv.getAverageNanosPerTick() / 1e6),
                    kinds);
            java.util.Map<String, Integer> cars = new java.util.TreeMap<>();
            for (var car : p.getServerWorld().getEntitiesByClass(com.yompix.novograd.entity.CarEntity.class, p.getBoundingBox().expand(64), x -> true))
                cars.merge(com.yompix.novograd.entity.CarEntity.VARIANTS[car.getVariant()] + (car.hasPassengers() ? "+люди" : "") + (car.onRoute() ? "+маршрут" : "")
                        + (car.getCommandTags().isEmpty() ? "" : car.getCommandTags().toString()), 1, Integer::sum);
            Novograd.LOG.info("[fps] машины {}: {}", name, cars);
        });
        if (fn == 0) return;
        long[] a = java.util.Arrays.copyOf(FR, fn);
        java.util.Arrays.sort(a);
        double sum = 0;
        for (long v : a) sum += v;
        var mc = MinecraftClient.getInstance();
        double avg = sum / fn / 1e6, p99 = a[Math.min(fn - 1, (int) (fn * 0.99))] / 1e6, max = a[fn - 1] / 1e6;
        double savg = Perf.tickCount == 0 ? 0 : Perf.tickSumNs / (double) Perf.tickCount / 1e6, smax = Perf.tickMaxNs / 1e6;
        String line = String.format(Locale.ROOT, "%s: кадр avg=%.1f p99=%.1f max=%.1f мс, рывков>50мс=%d, GC=%d (%d мс), тик сервера avg=%.1f max=%.1f мс, сущностей=%d, частиц=%s",
                name, avg, p99, max, hitches, gcCount() - gcCount0, gcTime() - gcTime0, savg, smax,
                mc.world == null ? 0 : mc.world.getRegularEntityCount(), mc.particleManager.getDebugString());
        Novograd.LOG.info("[fps] " + line);
        SUMMARY.add(line);
    }

    private static void summary() {
        Novograd.LOG.info("[fps] итого ({} сценариев):", SUMMARY.size());
        for (String s : SUMMARY) Novograd.LOG.info("[fps]   " + s);
    }
}
