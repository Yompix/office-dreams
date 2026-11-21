package com.yompix.novograd.client.city;

import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.9: блэкаут на клиенте — в районе без света блочный свет почти гаснет (фонари, вывески, окна), плавно за 2 с,
 * и так же плавно возвращается (миксин {@code BlackoutLightMixin} на lightmap). Плюс текущее событие города для карты.
 */
public final class CityFx {
    private CityFx() {}

    /** {x, z, r, до (время мира)}. */
    private static final List<double[]> DARK = new ArrayList<>();
    private static float dim = 1;
    public static NbtCompound event = new NbtCompound();

    public static void blackout(MinecraftClient c, NbtCompound n) {
        if (c.world == null) return;
        DARK.add(new double[]{n.getDouble("x"), n.getDouble("z"), n.getDouble("r"), c.world.getTime() + n.getInt("ticks")});
    }

    public static void tick(MinecraftClient c) {
        if (c.world == null || c.player == null) return;
        long now = c.world.getTime();
        DARK.removeIf(d -> now >= d[3]);
        boolean in = false;
        for (double[] d : DARK) {
            double dx = c.player.getX() - d[0], dz = c.player.getZ() - d[1];
            if (dx * dx + dz * dz < d[2] * d[2]) in = true;
        }
        float want = in ? 0.12f : 1f;
        dim += (want - dim) * 0.05f; // lightmap пересчитывается каждый тик сам
        if (Math.abs(dim - want) < 0.005f) dim = want;
    }

    /** Множитель блочного света (1 — как обычно). */
    public static float blockLight() {
        return dim;
    }
}
