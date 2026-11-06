package com.yompix.novograd.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.gta.Wanted;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Шаги сюжета в стиле GTA:
 * carrace — гонка на машине по отметкам против соперника; tune — поставить тюнинг (нитро);
 * escape — уйти от погони (n звёзд); steal — угнать машину и пригнать куда сказано;
 * clear — перестрелка: перебить банду (с боссом); chase — разбить машину убегающего.
 */
final class GtaSteps {
    private GtaSteps() {}

    static final java.util.Map<UUID, List<UUID>> SPAWNED = new java.util.HashMap<>();

    static boolean handles(String type) {
        return switch (type) {
            case "carrace", "tune", "escape", "steal", "clear", "chase" -> true;
            default -> false;
        };
    }

    private static void track(ServerPlayerEntity p, net.minecraft.entity.Entity e) {
        SPAWNED.computeIfAbsent(p.getUuid(), u -> new ArrayList<>()).add(e.getUuid());
    }

    /** Убрать всё, что шаг наспавнил (машины соперников, банду). */
    static void cleanup(ServerPlayerEntity p) {
        List<UUID> l = SPAWNED.remove(p.getUuid());
        if (l == null) return;
        for (UUID u : l) {
            var e = p.getServerWorld().getEntity(u);
            if (e instanceof CarEntity car) car.getPassengerList().forEach(x -> { if (!(x instanceof ServerPlayerEntity)) x.discard(); });
            if (e != null && !(e instanceof ServerPlayerEntity)) e.discard();
        }
    }

    private static CarEntity car(ServerWorld w, Vec3d at, float yaw, int variant) {
        CarEntity c = new CarEntity(ModContent.CAR, w);
        c.refreshPositionAndAngles(at.x, Story.ground(w, at).getY(), at.z, yaw, 0);
        c.setVariant(variant);
        w.spawnEntity(c);
        return c;
    }

    private static CitizenEntity npc(ServerWorld w, Vec3d at, CitizenEntity.Role role, String name, int skin) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        c.setup(skin, role, false, name);
        c.refreshPositionAndAngles(at.x, Story.ground(w, at).getY(), at.z, 0, 0);
        c.setCustomNameVisible(true);
        w.spawnEntity(c);
        return c;
    }

    private static List<Vec3d> points(ServerWorld w, JsonArray a) {
        List<Vec3d> l = new ArrayList<>();
        for (var e : a) {
            Vec3d v = Story.place(w, e.getAsString());
            if (v != null) l.add(v);
        }
        return l;
    }

    // ------------------------------------------------------------------ вход в шаг
    static void enter(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s) {
        ServerWorld w = p.getServerWorld();
        cleanup(p);
        d.stepCount = 0;
        r.countdown = -1;
        r.rivalIndex = 0;
        switch (Story.str(s, "type", "")) {
            case "escape" -> Wanted.add(p, 0, Story.str(s, "why", "погоня"), Story.num(s, "stars", 2));
            case "steal" -> {
                Vec3d at = Story.place(w, Story.str(s, "place", ""));
                if (at == null) return;
                CarEntity c = car(w, at, Story.placeYaw(w, Story.str(s, "place", "")), Story.num(s, "variant", 3));
                c.paint(0xD4AF37); // золотая — сразу видно, какую угонять
                track(p, c);
                r.target = c.getUuid();
            }
            case "clear" -> {
                Vec3d at = Story.place(w, Story.str(s, "place", ""));
                if (at == null) return;
                int n = Story.num(s, "count", 4);
                var rnd = w.getRandom();
                for (int i = 0; i < n; i++) {
                    Vec3d v = at.add(rnd.nextInt(11) - 5, 0, rnd.nextInt(11) - 5);
                    CitizenEntity g = npc(w, v, CitizenEntity.Role.GANG, "Ящер " + com.yompix.novograd.life.Names.random(rnd, false), CitizenEntity.RANDOM_FROM + 1 + 2 * rnd.nextInt(20));
                    g.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(rnd.nextInt(3) == 0 ? ModContent.SMG : ModContent.PISTOL));
                    track(p, g);
                }
                String boss = Story.str(s, "boss", "");
                if (!boss.isEmpty()) {
                    CitizenEntity b = npc(w, at, CitizenEntity.Role.GANG, boss, CitizenEntity.RANDOM_FROM + 39);
                    var hp = b.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                    if (hp != null) hp.setBaseValue(80);
                    b.setHealth(80);
                    b.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(ModContent.SMG));
                    track(p, b);
                }
            }
            case "chase" -> {
                List<Vec3d> pts = points(w, s.getAsJsonArray("points"));
                if (pts.size() < 2) return;
                Vec3d a = pts.get(0), b = pts.get(1);
                CarEntity c = car(w, a, (float) Math.toDegrees(Math.atan2(-(b.x - a.x), b.z - a.z)), Story.num(s, "variant", 3));
                CitizenEntity drv = npc(w, a, CitizenEntity.Role.GANG, Story.str(s, "driver", "Беглец"), CitizenEntity.RANDOM_FROM + 39);
                drv.startRiding(c, true);
                c.setTune(true, 1, 2);
                track(p, c);
                track(p, drv);
                r.target = c.getUuid();
                r.countdown = 60; // фора: 3 секунды, потом газ
            }
            case "carrace" -> {
                List<Vec3d> pts = points(w, s.getAsJsonArray("points"));
                Vec3d start = Story.place(w, Story.str(s, "start", ""));
                if (start == null || pts.isEmpty()) return;
                Vec3d first = pts.get(0);
                float yaw = (float) Math.toDegrees(Math.atan2(-(first.x - start.x), first.z - start.z));
                Vec3d side = new Vec3d(Math.cos(Math.toRadians(yaw)), 0, Math.sin(Math.toRadians(yaw))).multiply(3.5);
                CarEntity c = car(w, start.add(side), yaw, Story.num(s, "variant", 0));
                c.setTune(true, Story.num(s, "rival_engine", 1), 0);
                CitizenEntity drv = npc(w, start.add(side), CitizenEntity.Role.CITIZEN, Story.str(s, "rival", "Соперник"), CitizenEntity.RANDOM_FROM + 13);
                drv.startRiding(c, true);
                track(p, c);
                track(p, drv);
                r.target = c.getUuid();
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ каждый тик
    static void tick(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s) {
        ServerWorld w = p.getServerWorld();
        switch (Story.str(s, "type", "")) {
            case "tune" -> {
                if (p.getVehicle() instanceof CarEntity c && c.hasNitro()) Story.advance(p);
            }
            case "escape" -> {
                if (r.ticks > 60 && Wanted.stars(p) == 0) {
                    Story.say(p, "Ушёл от полиции!", Formatting.GREEN);
                    Story.advance(p);
                }
            }
            case "steal" -> {
                var e = r.target == null ? null : w.getEntity(r.target);
                if (e != null && r.ticks % 20 == 0) w.getChunkManager().addTicket(net.minecraft.server.world.ChunkTicketType.PORTAL, e.getChunkPos(), 2, e.getBlockPos());
                Vec3d to = Story.place(w, Story.str(s, "to", ""));
                Vec3d from = Story.place(w, Story.str(s, "place", ""));
                if (e == null && from != null && p.squaredDistanceTo(from) > 90 * 90) return; // машина стоит в незагруженном районе — ждём, пока игрок доедет
                if (e == null && r.ticks > 60) {
                    Story.say(p, "Машину потеряли — попробуй ещё раз", Formatting.RED);
                    Story.reenter(p);
                    return;
                }
                if (e == null) return;
                if (to != null && p.getVehicle() == e && e.squaredDistanceTo(to) < 64 && ((CarEntity) e).slow()) {
                    p.stopRiding();
                    Story.advance(p);
                }
            }
            case "clear" -> {
                Vec3d where = Story.place(w, Story.str(s, "place", ""));
                if (where != null && p.squaredDistanceTo(where) > 70 * 70) return; // враги в незагруженном районе — ждём игрока
                List<UUID> l = SPAWNED.getOrDefault(p.getUuid(), List.of());
                int alive = 0;
                for (UUID u : l) if (w.getEntity(u) instanceof CitizenEntity c && c.isAlive()) {
                    alive++;
                    if (!c.chasing(p) && c.squaredDistanceTo(p) < 30 * 30) c.attack(p);
                }
                d.stepCount = l.size() - alive;
                if (r.ticks > 40 && alive == 0) {
                    Story.say(p, "Чисто!", Formatting.GREEN);
                    Story.advance(p);
                }
            }
            case "chase" -> {
                var e = r.target == null ? null : w.getEntity(r.target);
                if (!(e instanceof CarEntity car)) {
                    // машину-цель потеряли (выгрузилась) — погоня заново, а не «засчитано»
                    if (r.ticks > 40) {
                        Story.say(p, "Цель потеряна — ещё раз!", Formatting.RED);
                        Story.reenter(p);
                    }
                    return;
                }
                if (r.ticks % 20 == 0) w.getChunkManager().addTicket(net.minecraft.server.world.ChunkTicketType.PORTAL, car.getChunkPos(), 2, car.getBlockPos());
                if (r.countdown > 0 && --r.countdown == 0) {
                    List<Vec3d> pts = points(w, s.getAsJsonArray("points"));
                    car.setRoute(pts.subList(1, pts.size()), 0.85f, () -> r.rivalIndex = 999);
                }
                if (car.wrecked() || !car.hasNpcDriver()) {
                    Story.say(p, "Попался!", Formatting.GREEN);
                    Story.advance(p);
                } else if (r.rivalIndex == 999 || car.squaredDistanceTo(p) > 160 * 160) {
                    Story.say(p, "Он ушёл! Ещё раз", Formatting.RED);
                    Story.reenter(p);
                }
            }
            case "carrace" -> tickRace(p, d, r, s, w);
            default -> {}
        }
    }

    private static void tickRace(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s, ServerWorld w) {
        List<Vec3d> pts = points(w, s.getAsJsonArray("points"));
        Vec3d start = Story.place(w, Story.str(s, "start", ""));
        var rival = r.target == null ? null : w.getEntity(r.target);
        if (start == null) return;
        CarEntity rc = rival instanceof CarEntity c0 ? c0 : null;
        if (rc == null && r.countdown != 0) {
            // соперника выгрузило/сломало до старта — начинаем заезд заново
            if (r.ticks % 40 == 0) Story.reenter(p);
            return;
        }
        // машина соперника всегда «живёт», даже если уехала далеко от игрока
        if (rc != null && r.ticks % 20 == 0) w.getChunkManager().addTicket(net.minecraft.server.world.ChunkTicketType.PORTAL, rc.getChunkPos(), 2, rc.getBlockPos());
        if (r.countdown < 0) {
            // ждём: игрок за рулём у старта
            boolean ready = p.getVehicle() instanceof CarEntity mine && mine.getControllingPassenger() == p && p.squaredDistanceTo(start) < 12 * 12;
            if (ready) r.countdown = 80;
            else if (r.ticks % 100 == 0) Story.say(p, "Садись за руль и подъезжай к старту", Formatting.YELLOW);
            return;
        }
        if (r.countdown > 0) {
            if (r.countdown % 20 == 0) {
                int n = r.countdown / 20 - 1;
                LifeNet.send(p, "countdown", Story.text("text", n == 0 ? "ГАЗ!" : String.valueOf(n)));
                p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.MASTER, 1f, n == 0 ? 2f : 1f);
            }
            if (--r.countdown == 0 && rc != null) rc.setRoute(pts, Story.num(s, "rival_speed", 75) / 100f, () -> r.rivalIndex = 999);
            return;
        }
        if (d.stepCount < pts.size() && horiz(p.getPos(), pts.get(d.stepCount)) < 10 * 10 && Math.abs(p.getY() - pts.get(d.stepCount).y) < 8) {
            d.stepCount++;
            p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 1f, 1.2f);
        }
        if (d.stepCount >= pts.size()) {
            Story.say(p, "ПОБЕДА!", Formatting.GOLD);
            cleanup(p);
            Story.advance(p);
        } else if (r.rivalIndex == 999) {
            Story.say(p, "Соперник финишировал первым! Возвращайся на старт", Formatting.RED);
            Story.reenter(p);
        }
    }

    private static double horiz(Vec3d a, Vec3d b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    // ------------------------------------------------------------------ метка цели и прогресс
    static Vec3d target(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s) {
        ServerWorld w = p.getServerWorld();
        switch (Story.str(s, "type", "")) {
            case "tune":
                return Story.place(w, "garage_door");
            case "escape":
                return null;
            case "steal": {
                var e = r.target == null ? null : w.getEntity(r.target);
                if (e != null && p.getVehicle() != e) return e.getPos().add(0, 2, 0);
                if (e == null) return Story.place(w, Story.str(s, "place", ""));
                return Story.place(w, Story.str(s, "to", ""));
            }
            case "clear":
                return Story.place(w, Story.str(s, "place", ""));
            case "chase": {
                var e = r.target == null ? null : w.getEntity(r.target);
                return e == null ? null : e.getPos().add(0, 2, 0);
            }
            case "carrace": {
                if (r.countdown < 0) return Story.place(w, Story.str(s, "start", ""));
                List<Vec3d> pts = points(w, s.getAsJsonArray("points"));
                return d.stepCount < pts.size() ? pts.get(d.stepCount) : null;
            }
            default:
                return null;
        }
    }

    static String extra(ServerPlayerEntity p, LifeData d, Story.Run r, JsonObject s) {
        return switch (Story.str(s, "type", "")) {
            case "escape" -> "Розыск: " + "★".repeat(Wanted.stars(p)) + " — скройся из виду или перекрасься в Нитро-Гараже";
            case "clear" -> "Повержено: " + d.stepCount + "/" + GtaSteps.SPAWNED.getOrDefault(p.getUuid(), List.of()).size();
            case "carrace" -> r.countdown < 0 ? "Подъезжай к старту на машине" : "Отметка " + Math.min(d.stepCount + 1, s.getAsJsonArray("points").size()) + "/" + s.getAsJsonArray("points").size();
            case "chase" -> {
                var e = r.target == null ? null : p.getServerWorld().getEntity(r.target);
                yield e instanceof CarEntity c ? "Машина беглеца: " + c.health() + "%" : "";
            }
            default -> "";
        };
    }
}
