package com.yompix.novograd.client.story;

import com.mojang.blaze3d.platform.GlStateManager;
import com.yompix.novograd.Novograd;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Locale;

/**
 * Запись видео (только автотест, -Dnovograd.rec): каждый кадр — сразу в ffmpeg, без картинок на диске.
 * Кадры берутся ровно через 1/fps игрового времени (доля тика подменяется, TickDeltaMixin), поэтому видео ровное,
 * даже если машина рисует медленно (игру при записи замедляют командой /tick rate).
 * Рядом пишется журнал событий (.events.tsv): реплики и музыка — для сведения звука после записи.
 */
public final class CineRec {
    private CineRec() {}

    public static boolean on;
    /** Запись по -Dnovograd.rec уже была (вторая кат-сцена подряд не перезаписывает первую). */
    public static boolean used;
    /** Автотест: записывать только ту сцену, которую он сам запустил (а не ту, что продолжилась из сохранения). */
    public static boolean armed = true;
    public static int fps = 30;
    /** Подменённая доля тика (-1 — нет). */
    public static float forceDelta = -1;
    private static boolean capture;
    private static double nextT = -1, t0;
    private static Process proc;
    private static WritableByteChannel ch;
    private static ByteBuffer buf;
    private static String path;
    private static int frames;
    private static PrintWriter events;

    private static long clientTicks;

    static {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(c -> {
            if (c.world != null && !c.isPaused()) clientTicks++;
        });
    }

    public static void start(String out, int f) {
        stop();
        used = true;
        path = out;
        fps = f;
        frames = 0;
        nextT = -1;
        on = true;
        try {
            events = new PrintWriter(new FileWriter(out.replace(".mp4", ".events.tsv")), true);
            // музыка сцены включилась ещё до записи (во время загрузки) — тоже в журнал, с нуля
            if (CutsceneMusic.playingId != null) events.println("0.000\tMUSIC\t" + CutsceneMusic.playingId + "\t" + CutsceneMusic.playingVol);
        } catch (Exception e) {
            events = null;
        }
    }

    /** Этот кадр пойдёт в видео (камера логируется только по таким кадрам). */
    public static boolean capturing() {
        return capture;
    }

    /** Время видео в секундах (для журнала звука). */
    public static double videoTime() {
        return frames / (double) fps;
    }

    public static void event(String kind, String rest) {
        if (on && events != null) events.println(String.format(Locale.ROOT, "%.3f\t%s\t%s", videoTime(), kind, rest));
    }

    /** GameRenderer.render HEAD: если следующий кадр видео попадает в текущий тик — рисуем ровно его. */
    public static void beginFrame() {
        forceDelta = -1;
        capture = false;
        if (!on) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        // часы — собственные тики клиента (как у времени кат-сцены), а не время мира: оно приходит с сервера рывками
        double now = clientTicks;
        if (nextT < 0) nextT = now / 20.0;
        double target = nextT * 20 - now;
        if (target < 1.0) {
            forceDelta = (float) Math.max(0, target);
            capture = true;
        } else forceDelta = 0f;
    }

    /** GameRenderer.render TAIL: кадр целиком (мир, HUD, кинополосы, субтитры). */
    public static void endFrame() {
        if (!on || !capture) return;
        grab();
        nextT += 1.0 / fps;
        forceDelta = -1;
        capture = false;
    }

    private static void grab() {
        try {
            Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
            int w = fb.textureWidth, h = fb.textureHeight;
            if (proc == null) {
                proc = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", w + "x" + h,
                        "-r", Integer.toString(fps), "-i", "-", "-vf", "vflip", "-c:v", "libx264", "-preset", "veryfast", "-crf", "22",
                        "-pix_fmt", "yuv420p", path).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start();
                ch = Channels.newChannel(proc.getOutputStream());
                buf = BufferUtils.createByteBuffer(w * h * 4);
            }
            buf.clear();
            GlStateManager._bindTexture(fb.getColorAttachment());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            buf.rewind();
            while (buf.hasRemaining()) ch.write(buf);
            frames++;
        } catch (Exception e) {
            Novograd.LOG.error("Запись видео", e);
            on = false;
        }
    }

    public static void stop() {
        if (!on && proc == null) return;
        on = false;
        forceDelta = -1;
        try {
            if (ch != null) ch.close();
            if (proc != null) proc.waitFor();
        } catch (Exception e) {
            Novograd.LOG.error("Запись видео: стоп", e);
        }
        if (events != null) events.close();
        events = null;
        Novograd.LOG.info("[rec] {} — {} кадров", path, frames);
        proc = null;
        ch = null;
    }
}
