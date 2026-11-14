package com.yompix.glukhoman.client.menu;

/** Часы заставки и меню: обычно настенные; при записи видео — ровно 1/30 с на кадр (кадр за кадром). */
public final class MenuClock {
    private MenuClock() {}

    static final long T0 = System.nanoTime();
    /** Запись: номер кадра (≥ 0) — время идёт по кадрам; -1 — обычное время. */
    public static int recFrame = -1;
    public static int recFps = 30;

    public static double now() {
        return recFrame >= 0 ? recFrame / (double) recFps : (System.nanoTime() - T0) / 1e9;
    }
}
