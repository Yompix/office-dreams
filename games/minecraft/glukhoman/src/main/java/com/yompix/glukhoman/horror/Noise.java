package com.yompix.glukhoman.horror;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Шум для Слухачей и Хора: бег, прыжки, двери, ломание блоков, камешки, усилители.
 * Шум живёт 3 секунды; существо слышит его, если стоит ближе радиуса.
 */
public final class Noise {
    private Noise() {}

    public record Event(Vec3d pos, double radius, long time, ServerPlayerEntity who) {}

    private static final Deque<Event> EVENTS = new ArrayDeque<>();

    public static void emit(ServerWorld w, Vec3d pos, double radius, ServerPlayerEntity who) {
        long now = w.getTime();
        while (!EVENTS.isEmpty() && now - EVENTS.peekFirst().time() > 60) EVENTS.pollFirst();
        EVENTS.addLast(new Event(pos, radius, now, who));
        if (EVENTS.size() > 200) EVENTS.pollFirst();
    }

    /** Самый громкий (с учётом расстояния) свежий шум, который слышно из pos. */
    public static Event hear(ServerWorld w, Vec3d pos, double mult) {
        long now = w.getTime();
        Event best = null;
        double bestScore = 0;
        for (Event e : EVENTS) {
            if (now - e.time() > 60) continue;
            double d = e.pos().distanceTo(pos);
            double r = e.radius() * mult;
            if (d > r) continue;
            double score = (r - d) / r + (60 - (now - e.time())) / 120.0;
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    /** Шаги игроков: зовётся раз в 5 тиков. */
    public static void playerStep(ServerPlayerEntity p) {
        if (p.isSpectator() || p.isCreative() || !p.isOnGround()) return;
        double v = p.getVelocity().horizontalLength();
        if (v < 0.03) return;
        double r = p.isSneaking() ? 1.5 : p.isSprinting() ? 22 : 8;
        emit(p.getServerWorld(), p.getPos(), r, p);
    }

    public static void clear() {
        EVENTS.clear();
    }
}
