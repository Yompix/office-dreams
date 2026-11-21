package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.world.Sidewalks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.5: чем занят полицейский без вызова — обход по тротуару, штраф за неправильную парковку, разговор с жителем,
 * кофе, связь по рации. Только рядом с игроком (до 64 блоков), раз в 2 с; бой и задержания — CitizenEntity/Witness.
 */
public final class PoliceLife {
    private PoliceLife() {}

    private static final class S {
        String doing = "патруль";
        long until;
        @Nullable UUID car;
    }

    private static final Map<UUID, S> ST = new HashMap<>();
    public static int tickets, chats, coffees, walks;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(PoliceLife::tick);
    }

    public static String doing(CitizenEntity c) {
        S s = ST.get(c.getUuid());
        return s == null ? "патруль" : s.doing;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 17) return;
        ServerWorld w = server.getOverworld();
        long now = w.getTime();
        if (now % 2400 == 17) ST.keySet().removeIf(u -> w.getEntity(u) == null);
        for (ServerPlayerEntity p : w.getPlayers()) {
            for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(64, 16, 64),
                    c -> c.getRole() == CitizenEntity.Role.POLICE && c.isAlive() && c.getFoe() == null && !c.hasVehicle() && c.calm() && c.getStoryId().isEmpty())) {
                S s = ST.computeIfAbsent(c.getUuid(), k -> new S());
                if (s.car != null) { // идёт выписывать штраф
                    if (w.getEntity(s.car) instanceof CarEntity car && !car.hasPassengers()) {
                        if (c.squaredDistanceTo(car) < 3.2 * 3.2) {
                            c.getNavigation().stop();
                            c.directTo = null;
                            c.face(car);
                            c.busy(CitizenEntity.Activity.THINK, 160, null, new ItemStack(Items.PAPER));
                            s.doing = "выписывает штраф";
                            s.car = null;
                            s.until = now + 200;
                            tickets++;
                        } else if (c.directTo == null || now > s.until) {
                            s.car = null;
                            s.until = now;
                        }
                        continue;
                    }
                    s.car = null;
                }
                if (now < s.until || c.isBusy() || c.directTo != null) continue;
                var r = w.getRandom();
                int k = r.nextInt(10);
                if (k < 3) {
                    CarEntity car = w.getEntitiesByClass(CarEntity.class, c.getBoundingBox().expand(20, 4, 20),
                            x -> !x.hasPassengers() && x.slow() && !x.wrecked() && !x.isPolice() && !x.onRoute()).stream().findFirst().orElse(null);
                    if (car != null) {
                        s.car = car.getUuid();
                        s.doing = "идёт к машине — штраф за парковку";
                        s.until = now + 400;
                        c.direct(car.getPos().add(1.6, 0, 0), 0.6);
                        continue;
                    }
                }
                if (k < 5) {
                    CitizenEntity o = w.getEntitiesByClass(CitizenEntity.class, c.getBoundingBox().expand(12, 3, 12),
                            x -> x != c && x.getRole() == CitizenEntity.Role.CITIZEN && x.calm() && !x.isBusy() && !x.hasVehicle() && x.getStoryId().isEmpty()).stream().findFirst().orElse(null);
                    if (o != null) {
                        c.sceneWith(o, CitizenEntity.Activity.TALK, 200);
                        o.sceneWith(c, CitizenEntity.Activity.TALK, 200);
                        s.doing = "разговаривает с жителем";
                        s.until = now + 260;
                        chats++;
                        continue;
                    }
                }
                if (k == 5) {
                    c.busy(CitizenEntity.Activity.DRINK, 240, null, new ItemStack(ModContent.COLA));
                    s.doing = "перерыв на кофе";
                    s.until = now + 300;
                    coffees++;
                    continue;
                }
                if (k == 6) {
                    c.busy(CitizenEntity.Activity.PHONE, 120, null, new ItemStack(ModContent.PHONE));
                    s.doing = "на связи по рации";
                    s.until = now + 160;
                    continue;
                }
                double a = r.nextDouble() * Math.PI * 2, d = 22 + r.nextInt(14);
                Vec3d to = Sidewalks.nearestSidewalk(c.getPos().add(Math.cos(a) * d, 0, Math.sin(a) * d));
                if (to == null) continue;
                c.walkRoute(Sidewalks.route(c.getPos(), to), 0.55, null, c.getMainHandStack().copy(), null);
                s.doing = "обход района";
                s.until = now + 600;
                walks++;
            }
        }
    }
}
