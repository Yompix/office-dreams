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

    /** Из GameRenderer.render (TAIL): уже с рукой, HUD и рамкой видоискателя. */
    public static void frameFree() {
        if (!on || !free) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null) return;
        double gt = (c.world.getTime() + c.getRenderTickCounter().getTickDelta(false)) / 20.0;
        if (nextT < 0) nextT = gt;
        while (gt >= nextT) {
            grab();
            nextT += 1.0 / Cine.recFps;
        }
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
                        "-r", Integer.toString(Cine.recFps), "-i", "-", "-vf", "vflip", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
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
