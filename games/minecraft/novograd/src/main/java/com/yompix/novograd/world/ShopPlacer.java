package com.yompix.novograd.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Магазины одежды, цирюльня и спортзал: строятся на своих участках, когда игрок впервые подъезжает. */
public final class ShopPlacer {
    private ShopPlacer() {}

    private record Spot(String place, ShopBuilder.Kind kind) {}

    private static final Spot[] SPOTS = {
            new Spot("shop_clothes", ShopBuilder.Kind.CLOTHES_CHEAP), new Spot("shop_barber", ShopBuilder.Kind.BARBER),
            new Spot("shop_gym", ShopBuilder.Kind.GYM), new Spot("shop_boutique", ShopBuilder.Kind.CLOTHES_LUX),
    };

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 11) return;
        ServerWorld w = server.getOverworld();
        if (!(w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) return;
        TownState t = TownState.get(w);
        for (Spot s : SPOTS) {
            String key = "built:" + s.place();
            if (t.storyPlaces.containsKey(key)) continue;
            double[] v = Island.place(s.place());
            if (v == null) continue;
            boolean near = false;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
                if (p.getServerWorld() == w && p.squaredDistanceTo(v[0], v[1], v[2]) < 96 * 96) near = true;
            if (!near) continue;
            int x = (int) Math.floor(v[0]), z = (int) Math.floor(v[2]);
            for (int cx = -1; cx <= 1; cx++) for (int cz = 0; cz <= 2; cz++) w.getChunk((x >> 4) + cx, (z >> 4) + cz); // участок целиком загружен
            ShopBuilder.place(w, s.kind(), new BlockPos(x, Island.surface(x, z), z), Direction.NORTH);
            t.storyPlaces.put(key, "1");
            t.markDirty();
        }
    }
}
