package com.yompix.novograd.world;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/** Только чтение: где сейчас горят уличные пожары ({@link Fires}) — для вызова пожарных (gta/Emergency). */
public final class FireWatch {
    private FireWatch() {}

    public static List<Vec3d> spots(ServerWorld w) {
        List<Vec3d> out = new ArrayList<>();
        for (Fires.Spot s : Fires.SPOTS) if (s.w == w && s.ttl > 0) out.add(s.at);
        return out;
    }
}
