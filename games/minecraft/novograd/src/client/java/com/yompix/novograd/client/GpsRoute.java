package com.yompix.novograd.client;

import com.yompix.novograd.world.Island;

import java.util.*;

/**
 * Навигатор: путь по дорогам, а не напрямик. Узлы — перекрёстки сетки (координаты ≡ 24 по модулю 48),
 * ребро между соседними узлами есть, если на всём отрезке дорога (улица, трасса, мост). Поиск A*.
 * Результат кэшируется, пока не сменились узел старта или цели.
 */
public final class GpsRoute {
    private GpsRoute() {}

    private static final int STEP = 48, N = 4096 / STEP + 2, OFF = 2048;
    private static long cacheKey = Long.MIN_VALUE;
    private static List<int[]> cache = List.of();
    private static final Map<Long, Boolean> EDGE = new HashMap<>();

    private static int snap(double v) {
        return (int) (Math.round((v - 24) / (double) STEP) * STEP + 24);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** Отрезок между соседними узлами — дорога? */
    private static boolean edge(int x1, int z1, int x2, int z2) {
        long k = key(x1 + x2, z1 + z2); // середина ребра однозначно задаёт ребро
        Boolean b = EDGE.get(k);
        if (b != null) return b;
        boolean ok = true;
        for (int i = 0; i <= 8 && ok; i++) {
            int x = x1 + (x2 - x1) * i / 8, z = z1 + (z2 - z1) * i / 8;
            ok = Island.road(x, z);
        }
        EDGE.put(k, ok);
        return ok;
    }

    /** Ближайший к точке узел, от которого есть хоть одна дорога. */
    private static int[] nearestNode(double x, double z) {
        int bx = snap(x), bz = snap(z);
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++) {
                int nx = bx + dx * STEP, nz = bz + dz * STEP;
                if (!Island.road(nx, nz) || !(edge(nx, nz, nx + STEP, nz) || edge(nx, nz, nx - STEP, nz) || edge(nx, nz, nx, nz + STEP) || edge(nx, nz, nx, nz - STEP))) continue;
                double d = Math.hypot(nx - x, nz - z);
                if (d < bd) { bd = d; best = new int[]{nx, nz}; }
            }
        return best;
    }

    /** Точки пути по дорогам от (px,pz) до (tx,tz); пусто — дороги не нашлось (тогда рисуем напрямую). */
    public static List<int[]> route(double px, double pz, double tx, double tz) {
        int[] a = nearestNode(px, pz), b = nearestNode(tx, tz);
        if (a == null || b == null) return List.of();
        long ck = key(a[0], a[1]) * 31 + key(b[0], b[1]);
        if (ck == cacheKey) return cache;
        cacheKey = ck;
        cache = astar(a, b);
        return cache;
    }

    private static List<int[]> astar(int[] a, int[] b) {
        record Node(int x, int z, double f) {}
        Map<Long, Double> g = new HashMap<>();
        Map<Long, Long> from = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        long start = key(a[0], a[1]), goal = key(b[0], b[1]);
        g.put(start, 0.0);
        open.add(new Node(a[0], a[1], 0));
        int[][] dirs = {{STEP, 0}, {-STEP, 0}, {0, STEP}, {0, -STEP}};
        int guard = 0;
        while (!open.isEmpty() && guard++ < 20000) {
            Node n = open.poll();
            long nk = key(n.x, n.z);
            if (nk == goal) break;
            double gn = g.get(nk);
            for (int[] d : dirs) {
                int x = n.x + d[0], z = n.z + d[1];
                if (Math.abs(x) > OFF || Math.abs(z) > OFF || !edge(n.x, n.z, x, z)) continue;
                long k = key(x, z);
                double cost = gn + STEP * (Island.zone(x, z) == Island.Zone.COUNTRY || Island.zone(x, z) == Island.Zone.SEA ? 0.8 : 1.0); // трассы чуть выгоднее
                if (cost < g.getOrDefault(k, Double.MAX_VALUE)) {
                    g.put(k, cost);
                    from.put(k, nk);
                    open.add(new Node(x, z, cost + Math.abs(x - b[0]) + Math.abs(z - b[1])));
                }
            }
        }
        if (!from.containsKey(goal) && start != goal) return List.of();
        LinkedList<int[]> path = new LinkedList<>();
        long k = goal;
        while (true) {
            path.addFirst(new int[]{(int) (k >> 32), (int) k});
            if (k == start) break;
            Long p = from.get(k);
            if (p == null) break;
            k = p;
        }
        return path;
    }
}
