package com.yompix.novograd.block;

import com.yompix.novograd.entity.SeatEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class SeatBlockHelper {
    private SeatBlockHelper() {}

    /** Сажает игрока на блок (парта, скамейка). */
    public static void sit(ServerWorld world, BlockPos pos, ServerPlayerEntity player, double height) {
        if (player.hasVehicle()) return;
        if (!world.getEntitiesByClass(SeatEntity.class, new Box(pos), e -> true).isEmpty()) return;
        SeatEntity seat = new SeatEntity(world, pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
        world.spawnEntity(seat);
        player.startRiding(seat, true);
    }
}
