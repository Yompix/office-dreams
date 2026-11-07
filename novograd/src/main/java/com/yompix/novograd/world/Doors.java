package com.yompix.novograd.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Входы в здания острова для прохожих (world/LifeAi): подъезды жилых башен и отелей, входы офисов
 * (двойная стеклянная дверь посередине южного фасада, см. Buildings.facade), двери домиков пригорода
 * (Residents.homeOf), магазины стиля (ShopBuilder: вход на тротуар, зал — к югу).
 */
public final class Doors {
    private Doors() {}

    private static final String[] SHOPS = {"shop_clothes", "shop_barber", "shop_gym", "shop_boutique"};

    @Nullable
    public static LifeAi.Door shop(ServerWorld w, Vec3d p, double r) {
        LifeAi.Door best = null;
        double bd = r * r;
        TownState t = TownState.get(w);
        for (String s : SHOPS) {
            double[] v = Island.place(s);
            if (v == null || !t.storyPlaces.containsKey("built:" + s)) continue;
            Vec3d door = new Vec3d(v[0], v[1], v[2]);
            double d = door.squaredDistanceTo(p);
            if (d >= bd || !w.isChunkLoaded(BlockPos.ofFloored(door))) continue;
            bd = d;
            best = new LifeAi.Door(door.add(0, 0, -0.6), door.add(0, 0, 3), 1, "shop");
        }
        return best;
    }

    @Nullable
    public static LifeAi.Door home(ServerWorld w, Vec3d p, double r, Random rnd) {
        return pick(w, p, r, rnd, true);
    }

    @Nullable
    public static LifeAi.Door office(ServerWorld w, Vec3d p, double r, Random rnd) {
        return pick(w, p, r, rnd, false);
    }

    /** Случайный вход из ближайших (не самый близкий — чтобы люди расходились в разные стороны). */
    @Nullable
    private static LifeAi.Door pick(ServerWorld w, Vec3d p, double r, Random rnd, boolean home) {
        List<LifeAi.Door> all = all(w, p, r, home);
        if (all.isEmpty()) return null;
        all.sort((a, b) -> Double.compare(a.outside().squaredDistanceTo(p), b.outside().squaredDistanceTo(p)));
        return all.get(rnd.nextInt(Math.min(4, all.size())));
    }

    /** Все входы нужного вида в радиусе r (загруженные чанки). */
    public static List<LifeAi.Door> all(ServerWorld w, Vec3d p, double r, boolean home) {
        List<LifeAi.Door> out = new ArrayList<>();
        int n = (int) Math.ceil(r / 48);
        long px0 = Math.floorDiv((int) p.x + 24, 48), pz0 = Math.floorDiv((int) p.z + 24, 48);
        for (long px = px0 - n; px <= px0 + n; px++)
            for (long pz = pz0 - n; pz <= pz0 + n; pz++) {
                int cx = (int) px * 48, cz = (int) pz * 48;
                int[] in = Island.lotInfo(cx, cz);
                if (in == null) continue;
                Island.Zone zn = Island.zone(cx, cz);
                int s = Island.surface(cx, cz);
                if (zn == Island.Zone.SUBURB) {
                    if (!home) continue;
                    for (int qx = -1; qx <= 1; qx += 2)
                        for (int qz = -1; qz <= 1; qz += 2) {
                            if (pz == 2 && qz > 0 && (px == -5 || px == -7 || px == -9 || px == -35)) continue; // там магазины стиля
                            int hx = cx + qx * 9;
                            add(w, out, p, r, new LifeAi.Door(new Vec3d(hx + 0.5, s + 1, cz + qz * 15 + 0.5), new Vec3d(hx + 0.5, s + 1, cz + qz * 11 + 0.5), -qz, "house"));
                        }
                    continue;
                }
                // башни: 0 офисы, 1 жилая, 2 отель
                boolean ok = home ? in[0] == 1 || in[0] == 2 : in[0] == 0;
                if (!ok || zn != Island.Zone.DOWNTOWN && zn != Island.Zone.RIVIERA) continue;
                int half = in[1];
                add(w, out, p, r, new LifeAi.Door(new Vec3d(cx + 1.0, s + 1, cz + half + 2.5), new Vec3d(cx + 1.0, s + 1, cz + half - 2.5), -1, home ? "tower" : "office"));
            }
        return out;
    }

    private static void add(ServerWorld w, List<LifeAi.Door> out, Vec3d p, double r, LifeAi.Door d) {
        if (d.outside().squaredDistanceTo(p) < r * r && w.isChunkLoaded(BlockPos.ofFloored(d.outside()))) out.add(d);
    }
}
