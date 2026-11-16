package com.yompix.shaderlite.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.*;

/**
 * Сценарий проверки: -Dshaderlite.autotest=/путь/script.txt. Команды по строке:
 *   wait S | cmd КОМАНДА | look YAW PITCH | shot ИМЯ | bench ИМЯ S | view N | hud on|off | quit
 * Пишет в лог строки "SLBENCH ..." и "SLSHOT ...".
 */
public final class Autotest implements ClientModInitializer {
    static final Logger LOG = LoggerFactory.getLogger("SLTEST");
    private final List<String> script = new ArrayList<>();
    private int pc; private long waitUntil; private boolean started;
    private String benchName; private long benchEnd; private long lastFrame; private final List<Long> frames = new ArrayList<>();
    private long benchStart;

    @Override public void onInitializeClient() {
        String p = System.getProperty("shaderlite.autotest");
        if (p == null) return;
        try { for (String l : Files.readAllLines(Path.of(p))) { l = l.strip(); if (!l.isEmpty() && !l.startsWith("#")) script.add(l); } }
        catch (Exception e) { LOG.error("SLTEST no script", e); return; }
        WorldRenderEvents.END.register(ctx -> {
            long now = System.nanoTime();
            if (benchName != null && lastFrame != 0) frames.add(now - lastFrame);
            lastFrame = now;
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient c) {
        long now = System.nanoTime();
        if (!started) {
            if (c.world == null || c.player == null) return;
            started = true; waitUntil = now; LOG.info("SLTEST world loaded");
        }
        if (benchName != null) {
            if (now < benchEnd) return;
            report(now);
        }
        if (now < waitUntil || pc >= script.size()) return;
        String line = script.get(pc++);
        String[] a = line.split("\\s+", 2);
        String arg = a.length > 1 ? a[1] : "";
        try {
            switch (a[0]) {
                case "wait" -> waitUntil = now + (long) (Double.parseDouble(arg) * 1e9);
                case "cmd" -> c.player.networkHandler.sendChatCommand(arg);
                case "look" -> { String[] v = arg.split(" "); c.player.setYaw(Float.parseFloat(v[0])); c.player.setPitch(Float.parseFloat(v[1])); }
                case "view" -> c.options.getViewDistance().setValue(Integer.parseInt(arg));
                case "hud" -> c.options.hudHidden = arg.equals("off");
                case "shot" -> {
                    ScreenshotRecorder.saveScreenshot(c.runDirectory, arg + ".png", c.getFramebuffer(), t -> {});
                    LOG.info("SLSHOT {}", arg);
                }
                case "bench" -> {
                    String[] v = arg.split(" ");
                    benchName = v[0]; frames.clear(); lastFrame = 0; benchStart = now;
                    benchEnd = now + (long) (Double.parseDouble(v[1]) * 1e9);
                }
                case "cave" -> { String[] v = arg.split(" "); findCave(c, Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2]), Integer.parseInt(v[3]), v.length > 4 ? Integer.parseInt(v[4]) : 0); }
                case "screen" -> {   // открыть экран по имени класса (конструктор (Screen parent)), "none" — закрыть
                    if (arg.equals("none")) c.setScreen(null);
                    else c.setScreen((net.minecraft.client.gui.screen.Screen) Class.forName(arg)
                            .getConstructor(net.minecraft.client.gui.screen.Screen.class).newInstance((Object) null));
                }
                case "log" -> LOG.info("SLTEST view={} clouds={} particles={} entity={}", c.options.getViewDistance().getValue(),
                        c.options.getCloudRenderMode().getValue(), c.options.getParticles().getValue(), c.options.getEntityDistanceScaling().getValue());
                case "quit" -> { LOG.info("SLTEST done"); c.scheduleStop(); }
                default -> LOG.warn("SLTEST unknown {}", line);
            }
        } catch (Exception e) { LOG.error("SLTEST failed: " + line, e); }
    }

    /** Ищет в радиусе r самую просторную тёмную пещеру и ставит туда игрока (смотрит в сторону простора). */
    private void findCave(MinecraftClient c, int cx, int cy, int cz, int r, int torches) {
        var w = c.world; int best = -1; BlockPos bp = null; int bestYaw = 0;
        int[][] dirs = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        for (int x = cx - r; x <= cx + r; x += 2) for (int z = cz - r; z <= cz + r; z += 2) for (int y = cy - 20; y <= cy + 20; y++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!w.getBlockState(p).isAir() || !w.getBlockState(p.up()).isAir() || w.getBlockState(p.down()).isAir()) continue;
            if (w.getLightLevel(LightType.SKY, p) > 0) continue;
            for (int d = 0; d < 4; d++) {
                int open = 0;
                for (int k = 1; k <= 12; k++) for (int s = -3; s <= 3; s++) for (int h = 0; h <= 4; h++) {
                    BlockPos q = p.add(dirs[d][0] * k + dirs[d][1] * s, h, dirs[d][1] * k + dirs[d][0] * s);
                    if (w.getBlockState(q).isAir()) open++;
                }
                if (open > best) { best = open; bp = p; bestYaw = d * 90; }
            }
        }
        if (bp == null) { LOG.info("SLTEST cave not found"); return; }
        LOG.info("SLTEST cave at {} yaw {} open {}", bp, bestYaw, best);
        c.player.networkHandler.sendChatCommand("tp @s " + bp.getX() + ".5 " + bp.getY() + " " + bp.getZ() + ".5 " + bestYaw + " 10");
        int d = bestYaw / 90;
        for (int i = 0; i < torches; i++) {
            int k = 3 + i * 4, s = (i % 2 == 0) ? -2 : 2;
            for (int h = 0; h < 6; h++) {
                BlockPos q = bp.add(dirs[d][0] * k + dirs[d][1] * s, h - 2, dirs[d][1] * k + dirs[d][0] * s);
                if (w.getBlockState(q).isAir() && !w.getBlockState(q.down()).isAir()) {
                    c.player.networkHandler.sendChatCommand("setblock " + q.getX() + " " + q.getY() + " " + q.getZ() + " minecraft:torch");
                    break;
                }
            }
        }
    }

    private void report(long now) {
        List<Long> f = new ArrayList<>(frames);
        benchName = benchName == null ? "?" : benchName;
        if (f.isEmpty()) { LOG.info("SLBENCH {} frames=0", benchName); benchName = null; return; }
        Collections.sort(f);
        double sum = 0; for (long x : f) sum += x;
        double avgMs = sum / f.size() / 1e6;
        double p95 = f.get(Math.min(f.size() - 1, (int) (f.size() * 0.95))) / 1e6;
        int n1 = Math.max(1, f.size() / 100); double worst = 0;
        for (int i = f.size() - n1; i < f.size(); i++) worst += f.get(i);
        double low1 = 1000.0 / (worst / n1 / 1e6);
        LOG.info(String.format(Locale.ROOT, "SLBENCH %s frames=%d avgFps=%.2f avgMs=%.1f p95Ms=%.1f low1Fps=%.2f view=%d",
                benchName, f.size(), 1000.0 / avgMs, avgMs, p95, low1, MinecraftClient.getInstance().options.getViewDistance().getValue()));
        benchName = null;
    }
}
