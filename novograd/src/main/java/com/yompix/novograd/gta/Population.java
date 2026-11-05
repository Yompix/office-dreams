package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.TownState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Город не пустеет: вместо погибших появляются новые жители (там, где игрок не видит); на юге — банда «Чёрные Ящеры». */
public final class Population {
    private Population() {}

    private static final int TARGET = 42, GANG = 6;

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 200 != 0) return;
        ServerWorld w = server.getOverworld();
        TownState t = TownState.get(w);
        if (t.origin == null || server.getPlayerManager().getPlayerList().isEmpty()) return;
        Box city = new Box(t.origin).expand(130, 30, 130);
        var all = w.getEntitiesByClass(CitizenEntity.class, city, c -> c.getStoryId().isEmpty());
        long people = all.stream().filter(c -> c.getRole() == CitizenEntity.Role.CITIZEN).count();
        long gang = all.stream().filter(c -> c.getRole() == CitizenEntity.Role.GANG).count();
        var r = w.getRandom();
        if (people < TARGET) spawn(w, t, CitizenEntity.Role.CITIZEN, null);
        double[] turf = t.place("gang_turf");
        if (turf != null && gang < GANG) spawn(w, t, CitizenEntity.Role.GANG, turf);
    }

    private static void spawn(ServerWorld w, TownState t, CitizenEntity.Role role, double[] near) {
        var r = w.getRandom();
        for (int tries = 0; tries < 10; tries++) {
            int x = near != null ? (int) near[0] + r.nextInt(17) - 8 : t.origin.getX() + r.nextInt(220) - 110;
            int z = near != null ? (int) near[2] + r.nextInt(17) - 8 : t.origin.getZ() + r.nextInt(220) - 110;
            int y = Story.topY(w, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!w.isAir(at) || !w.isAir(at.up())) continue;
            boolean seen = false;
            for (ServerPlayerEntity p : w.getPlayers()) if (p.squaredDistanceTo(x, y, z) < 30 * 30) seen = true;
            if (seen) continue;
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            int skin = role == CitizenEntity.Role.GANG ? CitizenEntity.RANDOM_FROM + 1 + 2 * r.nextInt(CitizenEntity.RANDOM_COUNT / 2) : CitizenEntity.randomSkin(r);
            c.setup(skin, role, false, role == CitizenEntity.Role.GANG ? "Ящер " + Names.random(r, false) : Names.random(r, CitizenEntity.isSlim(skin)));
            c.refreshPositionAndAngles(x + 0.5, y, z + 0.5, r.nextFloat() * 360, 0);
            c.randomizeBody(r, role == CitizenEntity.Role.CITIZEN && r.nextInt(4) == 0);
            if (role == CitizenEntity.Role.GANG) {
                c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(r.nextBoolean() ? ModContent.BAT : ModContent.PISTOL));
                c.setCustomNameVisible(true);
            }
            w.spawnEntity(c);
            return;
        }
    }
}
