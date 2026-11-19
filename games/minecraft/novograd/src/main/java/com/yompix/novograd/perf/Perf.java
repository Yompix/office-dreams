package com.yompix.novograd.perf;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

/**
 * Замер производительности сервера (только разработка: -Dnovograd.perf=true или автотест perf/cine).
 * Каждые 5 с в лог строка «[perf] mspt avg/p95/max, жители, машины, все сущности, загруженные чанки».
 */
public final class Perf {
    private Perf() {}

    public static final boolean ON = System.getProperty("novograd.perf") != null
            || String.valueOf(System.getProperty("novograd.autotest")).startsWith("perf")
            || String.valueOf(System.getProperty("novograd.autotest")).startsWith("cine");
    private static long start;
    private static final long[] WIN = new long[100];
    private static int n;
    /** Последние значения — для клиента-автотеста (общий процесс в одиночной игре). */
    public static volatile double lastAvg, lastP95, lastMax;
    public static volatile int lastCitizens, lastCars, lastEntities, lastChunks;

    public static void init() {
        if (!ON) return;
        ServerTickEvents.START_SERVER_TICK.register(s -> start = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(Perf::end);
    }

    private static void end(MinecraftServer s) {
        WIN[n++] = System.nanoTime() - start;
        if (n < WIN.length) return;
        n = 0;
        long[] a = WIN.clone();
        java.util.Arrays.sort(a);
        double sum = 0;
        for (long v : a) sum += v;
        lastAvg = sum / a.length / 1e6;
        lastP95 = a[94] / 1e6;
        lastMax = a[99] / 1e6;
        int cit = 0, cars = 0, all = 0;
        ServerWorld w = s.getOverworld();
        for (var e : w.iterateEntities()) {
            all++;
            if (e instanceof CitizenEntity) cit++;
            else if (e instanceof CarEntity) cars++;
        }
        lastCitizens = cit;
        lastCars = cars;
        lastEntities = all;
        lastChunks = w.getChunkManager().getLoadedChunkCount();
        Novograd.LOG.info(String.format(java.util.Locale.ROOT, "[perf] mspt avg=%.2f p95=%.2f max=%.2f citizens=%d cars=%d entities=%d chunks=%d aiSkipped=%d aiRan=%d level=%d",
                lastAvg, lastP95, lastMax, cit, cars, all, lastChunks, CitizenEntity.lodSkipped, CitizenEntity.lodRan, PerfLevel.level));
        CitizenEntity.lodSkipped = CitizenEntity.lodRan = 0;
    }
}
