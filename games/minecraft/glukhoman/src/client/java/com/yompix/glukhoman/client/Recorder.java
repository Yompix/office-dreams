package com.yompix.glukhoman.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.yompix.glukhoman.Glukhoman;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

/**
 * Запись трейлера (только автотест): каждый отрисованный кадр — сразу в ffmpeg (без картинок на диске).
 * Время кат-сцены идёт ровно на 1/fps за кадр, поэтому видео плавное даже если машина рисует медленно.
 */
public final class Recorder {
    private Recorder() {}

    static Process proc;
    static WritableByteChannel ch;
    static ByteBuffer buf;
    static String path;
    public static boolean on;
    static int frames;

    /** Свободная запись (не кат-сцена): кадры выбираются по игровому времени, шаг 1/fps — ровно даже при медленной отрисовке. */
    public static boolean free;
    static double nextT;

    public static void startFree(String out, int fps) {
        start(out, fps);
        free = true;
        nextT = -1;
    }

    /** Подменённая доля тика (-1 — нет): кадр рисуется ровно в момент nextT, без дрожи выборки. */
    public static float forceDelta = -1;
    static boolean capture;

    /** Из GameRenderer.render (HEAD): если следующий кадр видео попадает в текущий тик — рисуем ровно его. */
    public static void beginFrame() {
        forceDelta = -1;
        capture = false;
        if (!on || !free) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        double now = c.world.getTime();
        if (nextT < 0) nextT = (now + 0.0) / 20.0;
        double target = nextT * 20 - now;     // в тиках от начала текущего тика
        if (target < 1.0) {
            forceDelta = (float) Math.max(0, target);
            capture = true;
        } else forceDelta = 0f;   // ждём следующего тика: время = начало тика (между прошлым и следующим кадром видео, не забегая вперёд)
        if (preFrame != null) preFrame.run();
    }

    /** Автотест: сценарий «мыши» — до обновления камеры, по тому же времени кадра. */
    public static Runnable preFrame;

    /** Запись экранов (заставка, меню): каждый кадр отрисовки — кадр видео, часы меню идут по кадрам. */
    public static boolean screenRec;

    public static void startScreen(String out, int fps) {
        start(out, fps);
        screenRec = true;
        com.yompix.glukhoman.client.menu.MenuClock.recFps = fps;
        com.yompix.glukhoman.client.menu.MenuClock.recFrame = 0;
    }

    /** Из GameRenderer.render (TAIL): уже с рукой, HUD и рамкой видоискателя. */
    public static void frameFree() {
        if (on && screenRec) {
            grab();
            com.yompix.glukhoman.client.menu.MenuClock.recFrame++;
            return;
        }
        if (!on || !free || !capture) return;
        grab();
        HandCam.logFrame();
        nextT += 1.0 / Cine.recFps;
        forceDelta = -1;
        capture = false;
    }

    public static void start(String out, int fps) {
        path = out;
        Cine.recFps = fps;
        Cine.recFrame = 0;
        frames = 0;
        on = true;
    }

    /** Из WorldRenderEvents.END, после пост-эффекта. */
    public static void frame() {
        if (!on || free || !Cine.active) return;
        grab();
        Cine.recFrame++;
    }

    static void grab() {
        try {
            Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
            int w = fb.textureWidth, h = fb.textureHeight;
            if (proc == null) {
                proc = new ProcessBuilder("ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgba", "-s", w + "x" + h,
                        "-r", Integer.toString(Cine.recFps), "-i", "-", "-vf", "vflip", "-c:v", "libx264", "-preset", "veryfast", "-crf", System.getProperty("glukhoman.crf", "21"),
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
            Glukhoman.LOG.error("Запись трейлера", e);
            on = false;
        }
    }

    public static void stop() {
        on = false;
        free = false;
        if (screenRec) com.yompix.glukhoman.client.menu.MenuClock.recFrame = -1;
        screenRec = false;
        Cine.recFps = 0;
        try {
            if (ch != null) ch.close();
            if (proc != null) proc.waitFor();
        } catch (Exception e) {
            Glukhoman.LOG.error("Запись трейлера: стоп", e);
        }
        Glukhoman.LOG.info("AUTOTEST клип {} — {} кадров", path, frames);
        proc = null;
        ch = null;
    }
}
