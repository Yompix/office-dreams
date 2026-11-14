package com.yompix.glukhoman.horror;

import java.util.ArrayList;
import java.util.List;

/** Отложенные действия сцен (в тиках сервера). */
public final class Sched {
    private Sched() {}

    private record Task(long at, Runnable r) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    public static void after(int ticks, Runnable r) {
        TASKS.add(new Task(now + Math.max(1, ticks), r));
    }

    public static void tick(long serverTick) {
        now = serverTick;
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.at <= now) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            try {
                t.r.run();
            } catch (Exception e) {
                com.yompix.glukhoman.Glukhoman.LOG.error("Глухомань: ошибка в сцене", e);
            }
        }
    }

    public static void clear() {
        TASKS.clear();
    }
}
