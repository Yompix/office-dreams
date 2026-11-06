package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.world.Island;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Живые улицы по всему острову: вокруг каждого игрока ходят прохожие и ездят машины с водителями
 * (по своей полосе, с поворотами на перекрёстках). Кто далеко от всех игроков — исчезает.
 */
public final class Streets {
    private Streets() {}

    private static final int PEOPLE = 14, CARS = 6, FAR = 110;

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0) return;
        ServerWorld w = server.getOverworld();
        // уборка дальних
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, everywhere(w), CitizenEntity::isAmbient))
            if (w.getClosestPlayer(c, FAR) == null) c.discard();
        for (CarEntity car : w.getEntitiesByClass(CarEntity.class, everywhere(w), CarEntity::isAmbient))
            if (w.getClosestPlayer(car, FAR) == null) {
                car.getPassengerList().forEach(e -> e.discard());
                car.discard();
            }
        for (ServerPlayerEntity p : w.getPlayers()) {
            Island.Zone here = Island.zone(p.getBlockX(), p.getBlockZ());
            if (here == Island.Zone.CORE || here == Island.Zone.SEA) continue;
            long people = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(64, 20, 64), CitizenEntity::isAmbient).size();
            if (people < PEOPLE) pedestrian(w, p);
            long cars = w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(80, 20, 80), CarEntity::isAmbient).size();
            if (cars < CARS) traffic(w, p);
        }
    }

    private static net.minecraft.util.math.Box everywhere(ServerWorld w) {
        return new net.minecraft.util.math.Box(-Island.HALF, -64, -Island.HALF, Island.HALF, 320, Island.HALF);
    }

    private static boolean walkable(Island.Zone z) {
        return Island.urban(z) || z == Island.Zone.BEACH || z == Island.Zone.PORT;
    }

    private static void pedestrian(ServerWorld w, ServerPlayerEntity p) {
        var r = w.getRandom();
        for (int tries = 0; tries < 8; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = 30 + r.nextInt(26);
            int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
            Island.Zone zn = Island.zone(x, z);
            if (!walkable(zn)) continue;
            int road = Math.min(Island.gridDist(x), Island.gridDist(z));
            if (Island.urban(zn) && road != 5) continue; // по тротуарам
            int y = Island.surface(x, z) + 1;
            if (!w.isAir(new net.minecraft.util.math.BlockPos(x, y, z))) continue;
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            int skin = CitizenEntity.randomSkin(r);
            c.setup(skin, CitizenEntity.Role.CITIZEN, false, Names.random(r, CitizenEntity.isSlim(skin)));
            c.refreshPositionAndAngles(x + 0.5, y, z + 0.5, r.nextFloat() * 360, 0);
            c.randomizeBody(r, r.nextInt(6) == 0);
            c.setAmbient(true);
            w.spawnEntity(c);
            return;
        }
    }

    /** Машина с водителем на ближайшей улице: едет по правой полосе от перекрёстка к перекрёстку. */
    private static void traffic(ServerWorld w, ServerPlayerEntity p) {
        var r = w.getRandom();
        for (int tries = 0; tries < 8; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = 40 + r.nextInt(30);
            int gx = Math.round((float) ((p.getX() + Math.cos(a) * d - 24) / 48)) * 48 + 24;
            int gz = Math.round((float) ((p.getZ() + Math.sin(a) * d - 24) / 48)) * 48 + 24;
            if (!Island.urban(Island.zone(gx, gz)) && Island.zone(gx, gz) != Island.Zone.CORE) continue;
            int dir = r.nextInt(4);
            List<Vec3d> route = route(gx, gz, dir, 8, r);
            if (route.size() < 2) continue;
            Vec3d start = route.get(0);
            int y = Island.surface((int) start.x, (int) start.z) + 1;
            if (!w.isAir(net.minecraft.util.math.BlockPos.ofFloored(start.x, y, start.z))) continue;
            CarEntity car = new CarEntity(ModContent.CAR, w);
            Vec3d next = route.get(1);
            car.refreshPositionAndAngles(start.x, y, start.z, (float) Math.toDegrees(Math.atan2(-(next.x - start.x), next.z - start.z)), 0);
            car.setVariant(r.nextInt(6));
            car.setAmbient(true);
            w.spawnEntity(car);
            CitizenEntity drv = new CitizenEntity(ModContent.CITIZEN, w);
            int skin = CitizenEntity.randomSkin(r);
            drv.setup(skin, CitizenEntity.Role.CITIZEN, true, Names.random(r, CitizenEntity.isSlim(skin)));
            drv.refreshPositionAndAngles(start.x, y, start.z, 0, 0);
            drv.setAmbient(true);
            w.spawnEntity(drv);
            drv.startRiding(car, true);
            drive(car, route, r);
            return;
        }
    }

    private static void drive(CarEntity car, List<Vec3d> route, net.minecraft.util.math.random.Random r) {
        car.setRoute(route.subList(1, route.size()), 0.42f + r.nextFloat() * 0.1f, () -> {
            if (car.isRemoved() || !car.hasNpcDriver()) return;
            Vec3d end = route.get(route.size() - 1), prev = route.get(route.size() - 2);
            int dir = Math.abs(end.x - prev.x) > Math.abs(end.z - prev.z) ? (end.x > prev.x ? 0 : 2) : (end.z > prev.z ? 1 : 3);
            int gx = Math.round((float) ((end.x - 24) / 48)) * 48 + 24, gz = Math.round((float) ((end.z - 24) / 48)) * 48 + 24;
            List<Vec3d> more = route(gx, gz, dir, 8, r);
            if (more.size() >= 2) drive(car, more, r);
        });
    }

    /** dir: 0 +x, 1 +z, 2 −x, 3 −z. Правая полоса: смещение на 2 блока вправо от осевой. */
    private static List<Vec3d> route(int gx, int gz, int dir, int steps, net.minecraft.util.math.random.Random r) {
        List<Vec3d> pts = new ArrayList<>();
        int[][] d = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        int x = gx, z = gz;
        pts.add(lane(x, z, dir));
        for (int i = 0; i < steps; i++) {
            int nd = dir;
            int k = r.nextInt(10);
            if (k < 2) nd = (dir + 1) % 4;
            else if (k < 4) nd = (dir + 3) % 4;
            int nx = x + d[nd][0] * 48, nz = z + d[nd][1] * 48;
            Island.Zone zn = Island.zone(nx, nz);
            if (!Island.urban(zn) && zn != Island.Zone.CORE) {
                nd = dir;
                nx = x + d[nd][0] * 48;
                nz = z + d[nd][1] * 48;
                zn = Island.zone(nx, nz);
                if (!Island.urban(zn) && zn != Island.Zone.CORE) break;
            }
            // поворот: точка в центре перекрёстка на новой полосе
            if (nd != dir) pts.add(lane(x, z, nd));
            dir = nd;
            x = nx;
            z = nz;
            pts.add(lane(x, z, dir));
        }
        return pts;
    }

    private static Vec3d lane(int x, int z, int dir) {
        double ox = 0, oz = 0;
        switch (dir) {
            case 0 -> oz = 2;     // едем на восток — правая полоса южнее
            case 2 -> oz = -2;
            case 1 -> ox = -2;    // едем на юг — правая полоса западнее
            default -> ox = 2;
        }
        return new Vec3d(x + 0.5 + ox, Island.G + 1, z + 0.5 + oz);
    }
}
