package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.world.Island;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * «Город живёт сам» — точка входа: светофоры (TrafficLights), автобусы (Buses), скорая/пожарные/патрули (Emergency),
 * полицейская рация (Dispatch) и утренние служебные машины (мусоровоз, доставка, подметальная).
 * Пакеты от клиента — с префиксом «city_». Отладка: /city lights|bus|bus tp 1 0|bus call|amb|fire|police|radio|morning.
 */
public final class CityServices {
    private CityServices() {}

    public static final SoundEvent RADIO_STATIC = sound("radio_static");
    public static final SoundEvent RADIO_CHIRP = sound("radio_chirp");

    private static SoundEvent sound(String name) {
        return Registry.register(Registries.SOUND_EVENT, Novograd.id(name), SoundEvent.of(Novograd.id(name)));
    }

    public static void init() {
        Emergency.init();
        ServerTickEvents.END_SERVER_TICK.register(CityServices::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register(Emergency::onDeath);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            Buses.onStop(s);
            Emergency.onStop(s);
            morningStop(s);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            Dispatch.onLeave(h.player);
            Buses.onLeave(h.player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(literal("city")
                .requires(s -> s.getServer().isSingleplayer() || s.hasPermissionLevel(2))
                .then(argument("what", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    String r = debug(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what"));
                    ctx.getSource().sendFeedback(() -> Text.literal(r), false);
                    return 1;
                }))));
    }

    private static void tick(MinecraftServer s) {
        try {
            TrafficLights.tick(s);
            Buses.tick(s);
            Emergency.tick(s);
            Dispatch.tick(s);
            morning(s);
        } catch (RuntimeException e) {
            if (s.getTicks() % 200 == 0) Novograd.LOG.warn("city services", e);
        }
    }

    /** Пакеты «city_*» от клиента. */
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "city_bus_skip" -> Buses.skip(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    public static String debug(ServerPlayerEntity p, String what) {
        ServerWorld w = p.getServerWorld();
        String[] a = what.trim().split(" ", 2);
        switch (a[0]) {
            case "lights" -> {
                int gx = TrafficLights.line(p.getX()), gz = TrafficLights.line(p.getZ());
                return "Перекрёсток " + gx + "," + gz + " светофор: " + TrafficLights.signalled(w, gx, gz) + "; вдоль Z: " + TrafficLights.state(w.getTime(), true)
                        + ", вдоль X: " + TrafficLights.state(w.getTime(), false) + " (0 красный, 1 жёлтый, 2 зелёный)";
            }
            case "bus" -> {
                return Buses.debug(p, a.length > 1 ? a[1] : "");
            }
            case "amb", "fire", "police", "units" -> {
                return Emergency.debug(p, a[0]);
            }
            case "radio" -> {
                Dispatch.tell(p, "Диспетчер: проверка связи " + Dispatch.where(p.getPos()) + ". Как слышно?");
                return "Сообщение в рацию";
            }
            case "morning" -> {
                return spawnService(w, p, w.getRandom().nextInt(3)) ? "Служебная машина выехала" : "Нет улицы рядом";
            }
            case "where" -> {
                return Dispatch.where(p.getPos());
            }
            default -> {
                return "lights | bus [tp N i | call] | amb | fire | police | units | radio | morning | where";
            }
        }
    }

    // ------------------------------------------------------------------ утро: мусоровоз, доставка, подметальная машина
    private static final class Service {
        final UUID car;
        final int kind;
        int legs, pause;

        Service(UUID car, int kind) {
            this.car = car;
            this.kind = kind;
        }
    }

    private static final List<Service> SERVICE = new ArrayList<>();
    private static long nextService;
    private static final String[] SERVICE_NAMES = {"Мусоровоз", "Доставка", "Подметальная машина"};

    private static void morning(MinecraftServer s) {
        long t = s.getTicks();
        if (t % 20 != 9) return;
        ServerWorld w = s.getOverworld();
        // поведение: каждые пару кварталов — остановка с аварийкой (забрать мусор, выгрузить коробки)
        for (Iterator<Service> it = SERVICE.iterator(); it.hasNext(); ) {
            Service sv = it.next();
            if (!(w.getEntity(sv.car) instanceof CarEntity car) || !car.hasNpcDriver() || car.wrecked()) {
                it.remove();
                continue;
            }
            if (sv.pause > 0) {
                if (--sv.pause == 0) {
                    car.toggleLight(CarEntity.HAZARD);
                    car.setRoute(FunUtil.wander(w, car.getX(), car.getZ(), 2, w.random), sv.kind == 2 ? 0.22f : 0.38f, null);
                } else if (sv.kind == 0 && sv.pause % 2 == 0) {
                    w.playSound(null, car.getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.NEUTRAL, 0.35f, 0.6f + w.random.nextFloat() * 0.3f);
                    w.playSound(null, car.getBlockPos(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.NEUTRAL, 0.5f, 0.6f);
                } else if (sv.kind == 1 && sv.pause == 4) w.playSound(null, car.getBlockPos(), SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.NEUTRAL, 0.8f, 0.9f);
                continue;
            }
            if (sv.kind == 2 && car.onRoute()) { // щётки метут: пыль у колёс
                double rad = Math.toRadians(car.getYaw());
                w.spawnParticles(net.minecraft.particle.ParticleTypes.WHITE_ASH, car.getX() + Math.sin(rad) * 1.5, car.getY() + 0.2, car.getZ() - Math.cos(rad) * 1.5, 6, 0.8, 0.1, 0.8, 0.01);
            }
            if (!car.onRoute()) {
                if (sv.kind != 2 && ++sv.legs % 2 == 0) {
                    sv.pause = 7;
                    car.toggleLight(CarEntity.HAZARD);
                } else car.setRoute(FunUtil.wander(w, car.getX(), car.getZ(), 2, w.random), sv.kind == 2 ? 0.22f : 0.38f, null);
            }
        }
        if (t % 400 != 9) return;
        long tod = w.getTimeOfDay() % 24000;
        if (tod > 4500 && tod < 23000) return; // с 5 до 10:30 утра
        if (t < nextService || SERVICE.size() >= 2) return;
        nextService = t + 20 * 90;
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (!Island.urban(Island.zone(p.getBlockX(), p.getBlockZ()))) continue;
            spawnService(w, p, w.random.nextInt(3));
            return;
        }
    }

    private static boolean spawnService(ServerWorld w, ServerPlayerEntity p, int kind) {
        var r = w.random;
        for (int tries = 0; tries < 10; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = 45 + r.nextInt(25);
            int gx = TrafficLights.line(p.getX() + Math.cos(a) * d), gz = TrafficLights.line(p.getZ() + Math.sin(a) * d);
            if (!Island.urban(Island.zone(gx, gz)) || !w.isChunkLoaded(new BlockPos(gx, 0, gz))) continue;
            List<Vec3d> route = FunUtil.wander(w, gx, gz, 2, r);
            if (route.isEmpty()) continue;
            Vec3d first = route.get(0);
            Vec3d start = new Vec3d(gx + 0.5 + (first.x - gx) * 0.15, com.yompix.novograd.story.Story.topY(w, gx, gz), gz + 0.5 + (first.z - gz) * 0.15);
            CarEntity car = new CarEntity(ModContent.CAR, w);
            car.refreshPositionAndAngles(start.x, start.y, start.z, FunUtil.yawTo(start, first), 0);
            switch (kind) {
                case 0 -> { car.setVariant(11); car.paint(0x3A7A3C); }   // мусоровоз: зелёный грузовик
                case 1 -> { car.setVariant(4); car.paint(r.nextBoolean() ? 0xE0662A : 0x7A3C9A); } // доставка: фургон
                default -> { car.setVariant(CarEntity.COMPACT); car.paint(0xF07A12); } // подметальная: оранжевая малолитражка
            }
            car.setAmbient(true);
            w.spawnEntity(car);
            CitizenEntity drv = new CitizenEntity(ModContent.CITIZEN, w);
            drv.setup(CitizenEntity.skinIndex("worker"), CitizenEntity.Role.CITIZEN, true, SERVICE_NAMES[kind] + ": " + Names.random(r, false));
            drv.setCustomNameVisible(false);
            drv.refreshPositionAndAngles(start.x, start.y, start.z, 0, 0);
            drv.setAmbient(true);
            w.spawnEntity(drv);
            drv.startRiding(car, true);
            car.setRoute(route, kind == 2 ? 0.22f : 0.38f, null);
            SERVICE.add(new Service(car.getUuid(), kind));
            return true;
        }
        return false;
    }

    private static void morningStop(MinecraftServer s) {
        SERVICE.clear();
    }
}
