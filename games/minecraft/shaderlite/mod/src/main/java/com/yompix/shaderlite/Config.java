package com.yompix.shaderlite;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Настройки мода: config/shaderlite-adaptive.properties (можно править руками). */
public final class Config {
    public boolean enabled = true;
    public int targetFps = 40;
    public int minViewDistance = 5;
    public boolean adjustClouds = true;
    public boolean adjustParticles = true;
    public boolean adjustEntityDistance = true;
    public boolean showMessages = false;

    private final Path file;

    public Config(Path configDir) { this.file = configDir.resolve("shaderlite-adaptive.properties"); }

    public void load() {
        if (!Files.exists(file)) { save(); return; }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        catch (IOException e) { AdaptiveQuality.LOG.warn("[ShaderLite] не прочитал настройки: {}", e.toString()); return; }
        enabled = bool(p, "enabled", enabled);
        targetFps = clamp(integer(p, "targetFps", targetFps), 20, 240);
        minViewDistance = clamp(integer(p, "minViewDistance", minViewDistance), 2, 32);
        adjustClouds = bool(p, "adjustClouds", adjustClouds);
        adjustParticles = bool(p, "adjustParticles", adjustParticles);
        adjustEntityDistance = bool(p, "adjustEntityDistance", adjustEntityDistance);
        showMessages = bool(p, "showMessages", showMessages);
    }

    public void save() {
        String s = "# ShaderLite: Adaptive Quality / Адаптивное качество\n"
                + "# enabled - включить (true) / выключить (false)\n"
                + "# targetFps - какой FPS держать (20..240)\n"
                + "# minViewDistance - ниже этой дальности прорисовки (в чанках) мод не опускается\n"
                + "# adjustClouds / adjustParticles / adjustEntityDistance - что ещё можно временно снижать\n"
                + "# showMessages - писать над хотбаром, когда мод меняет качество\n"
                + "enabled=" + enabled + "\n"
                + "targetFps=" + targetFps + "\n"
                + "minViewDistance=" + minViewDistance + "\n"
                + "adjustClouds=" + adjustClouds + "\n"
                + "adjustParticles=" + adjustParticles + "\n"
                + "adjustEntityDistance=" + adjustEntityDistance + "\n"
                + "showMessages=" + showMessages + "\n";
        try { Files.createDirectories(file.getParent()); Files.writeString(file, s, StandardCharsets.UTF_8); }
        catch (IOException e) { AdaptiveQuality.LOG.warn("[ShaderLite] не сохранил настройки: {}", e.toString()); }
    }

    private static boolean bool(Properties p, String k, boolean d) {
        String v = p.getProperty(k); return v == null ? d : v.trim().equalsIgnoreCase("true");
    }
    private static int integer(Properties p, String k, int d) {
        try { return Integer.parseInt(p.getProperty(k, "" + d).trim()); } catch (NumberFormatException e) { return d; }
    }
    private static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }
}
