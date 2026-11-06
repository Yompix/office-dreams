package com.yompix.novograd.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.gta.Weapons;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Режиссёр кат-сцены: пока камера летит, герои по таймлайну выходят, идут, бегут, садятся в машины,
 * едут по маршруту, дерутся и стреляют. Таймлайн — список «[секунда, действие, …]» в поле actors.
 *
 *   spawn  id place [роль|скин] [имя]   — герой сюжета (id из characters) или массовка (gang/police/citizen/скин)
 *   walk / run  id place                — дойти / добежать
 *   act    id АКТИВНОСТЬ [на кого]      — жест (TALK, ARGUE, LAUGH, AIM, UPSET, …; none — снять)
 *   face   id кто                       — повернуться
 *   car    id place вариант             — поставить машину;  ride npc car — сесть за руль;  exit npc — выйти
 *   drive  car [места] скорость         — поехать по маршруту (0.5 — спокойно, 0.9 — погоня)
 *   shoot  id цель выстрелов [урон]     — очередь по цели (герой/массовка/место)
 *   die    id                           — упасть замертво;  fight a b тиков — драка;  despawn id
 *   boom   place                        — взрыв (без разрушений);  smoke place — дым
 * Место: имя точки, «@x,z», можно со сдвигом «gang_yard+3,-2».
 * Всё остальное уходит в обычные действия шага (time, wanted, msg, …).
 */
public final class Director {
    private Director() {}

    private static final class Shot {
        double[] from, to;
        int ticks;
        String track = "", follow = "";
        double[] off = {7, 3, 0};
    }

    private static final class Scene {
        final List<JsonArray> cues = new ArrayList<>();
        final List<Shot> shots = new ArrayList<>();
        Map<String, UUID> actors = new HashMap<>();
        Set<UUID> extras = new HashSet<>();
        /** Зритель: сцену ставит напарник, а этот игрок её только смотрит (актёры общие). */
        boolean viewer;
        JsonObject step;
        final List<int[]> bursts = new ArrayList<>(); // [индекс очереди, осталось, кд]
        final List<Object[]> burstInfo = new ArrayList<>();
        int t, next;
    }

    private static final Map<UUID, Scene> SCENES = new HashMap<>();

    /** Собрать кадры и таймлайн; вернуть кадры для клиента. */
    static void start(ServerPlayerEntity p, JsonObject step, net.minecraft.nbt.NbtList clientShots) {
        stop(p);
        Scene sc = new Scene();
        ServerWorld w = p.getServerWorld();
        for (JsonElement e : step.getAsJsonArray("shots")) {
            JsonObject j = e.getAsJsonObject();
            Shot s = new Shot();
            s.from = j.has("from") ? camera(w, j.get("from").getAsString()) : null;
            s.to = j.has("to") ? camera(w, j.get("to").getAsString()) : s.from;
            s.ticks = Math.round(j.get("sec").getAsFloat() * 20);
            s.track = Story.str(j, "track", "");
            s.follow = Story.str(j, "follow", "");
            if (j.has("off")) {
                JsonArray o = j.getAsJsonArray("off");
                for (int i = 0; i < 3 && i < o.size(); i++) s.off[i] = o.get(i).getAsDouble();
            }
            sc.shots.add(s);
        }
        sc.step = step;
        // игра с друзьями: эту же сцену уже ставит напарник — смотрим вместе, второй раз актёров не зовём
        for (Map.Entry<UUID, Scene> en : SCENES.entrySet()) {
            Scene other = en.getValue();
            if (other.step == step && !other.viewer && !en.getKey().equals(p.getUuid())) {
                sc.viewer = true;
                sc.actors = other.actors;
                sc.extras = other.extras;
                sc.t = other.t;
                SCENES.put(p.getUuid(), sc);
                for (Map.Entry<String, UUID> a : other.actors.entrySet()) {
                    Entity e = w.getEntity(a.getValue());
                    if (e != null) announceTo(p, a.getKey(), e);
                }
                return;
            }
        }
        if (step.has("actors")) for (JsonElement e : step.getAsJsonArray("actors")) sc.cues.add(e.getAsJsonArray());
        sc.cues.sort(Comparator.comparingDouble(a -> a.get(0).getAsDouble()));
        SCENES.put(p.getUuid(), sc);
        // герои, которые уже стоят в мире, — сразу сообщить клиенту (камера может следить за ними с первого кадра)
        for (Shot s : sc.shots) for (String n : new String[]{s.track, s.follow})
            if (!n.isEmpty()) {
                Entity a = actor(w, sc, n);
                if (a != null) announce(p, n, a);
            }
        // нулевая секунда — до первого кадра
        run(p, sc, 0);
    }

    /** Точка камеры: [x, y, z, yaw, pitch]. */
    @Nullable
    public static double[] camera(ServerWorld w, String name) {
        Vec3d v = pos(w, name);
        if (v == null) return null;
        double[] r = com.yompix.novograd.world.Island.place(base(name));
        double[] t = r != null ? null : com.yompix.novograd.world.TownState.get(w).place(base(name));
        double yaw = t != null ? t[3] : r != null ? r[3] : 0, pitch = t == null && r != null ? r[4] : 0;
        double y = v.y + (t == null && r == null ? 1.6 : 0);
        // камера не уходит в землю: над рельефом острова минимум на 1.7 блока
        if (t == null) y = Math.max(y, com.yompix.novograd.world.Island.surface((int) Math.floor(v.x), (int) Math.floor(v.z)) + 2.7);
        return new double[]{v.x, y, v.z, yaw, pitch};
    }

    private static String base(String spec) {
        int k = spec.indexOf('+', 1);
        return k > 0 ? spec.substring(0, k) : spec;
    }

    /** Место со сдвигом: «gang_yard+3,-2». */
    @Nullable
    static Vec3d pos(ServerWorld w, String spec) {
        String b = base(spec);
        Vec3d v = Story.place(w, b);
        if (v == null || b.length() == spec.length()) return v;
        String[] d = spec.substring(b.length() + 1).split(",");
        double dx = Double.parseDouble(d[0]), dz = d.length > 1 ? Double.parseDouble(d[1]) : 0, up = d.length > 2 ? Double.parseDouble(d[2]) : 0;
        int x = (int) Math.floor(v.x + dx), z = (int) Math.floor(v.z + dz);
        return new Vec3d(v.x + dx, Math.max(v.y, com.yompix.novograd.world.Island.surface(x, z) + 1) + up, v.z + dz);
    }

    public static boolean running(ServerPlayerEntity p) {
        return SCENES.containsKey(p.getUuid());
    }

    public static void tick(ServerPlayerEntity p) {
        Scene sc = SCENES.get(p.getUuid());
        if (sc == null) return;
        ServerWorld w = p.getServerWorld();
        sc.t++;
        if (!sc.viewer) run(p, sc, sc.t);
        // очереди выстрелов
        for (int i = sc.bursts.size() - 1; i >= 0; i--) {
            int[] b = sc.bursts.get(i);
            if (--b[2] > 0) continue;
            b[2] = 4 + w.getRandom().nextInt(4);
            Object[] info = sc.burstInfo.get(i);
            Entity sh = w.getEntity((UUID) info[0]);
            Vec3d target = info[1] instanceof UUID u && w.getEntity(u) instanceof Entity te ? te.getPos().add(0, te.getHeight() * 0.7, 0) : (Vec3d) info[2];
            if (sh instanceof LivingEntity le && target != null) {
                if (le instanceof CitizenEntity c) c.getLookControl().lookAt(target.x, target.y, target.z);
                Vec3d eye = le.getEyePos();
                Weapons.shoot(w, le, eye, target.subtract(eye), (float) info[3], 0.04, 50, false);
            }
            if (--b[1] <= 0) {
                sc.bursts.remove(i);
                sc.burstInfo.remove(i);
            }
        }
        // держим «зрителя» рядом с камерой, чтобы герои в кадре были загружены и видны
        if (sc.t % 10 == 0) {
            Vec3d at = camAt(w, sc);
            if (at != null && p.squaredDistanceTo(at) > 24 * 24) p.teleport(w, at.x, at.y, at.z, p.getYaw(), p.getPitch());
        }
        int total = 0;
        for (Shot s : sc.shots) total += s.ticks;
        if (sc.t > total + 20 * 60) stop(p); // страховка, если клиент так и не ответил
    }

    @Nullable
    private static Vec3d camAt(ServerWorld w, Scene sc) {
        int t = sc.t;
        for (Shot s : sc.shots) {
            if (t <= s.ticks || s == sc.shots.get(sc.shots.size() - 1)) {
                String who = !s.follow.isEmpty() ? s.follow : s.track;
                if (!who.isEmpty() && s.from == null) {
                    Entity e = actor(w, sc, who);
                    return e == null ? null : e.getPos().add(0, 4, 0);
                }
                if (s.from == null) return null;
                double k = Math.min(1, t / (double) Math.max(1, s.ticks));
                return new Vec3d(s.from[0] + (s.to[0] - s.from[0]) * k, s.from[1] + (s.to[1] - s.from[1]) * k, s.from[2] + (s.to[2] - s.from[2]) * k);
            }
            t -= s.ticks;
        }
        return null;
    }

    private static void run(ServerPlayerEntity p, Scene sc, int now) {
        while (sc.next < sc.cues.size() && Math.round(sc.cues.get(sc.next).get(0).getAsDouble() * 20) <= now) {
            JsonArray cue = sc.cues.get(sc.next++);
            JsonArray a = new JsonArray();
            for (int i = 1; i < cue.size(); i++) a.add(cue.get(i));
            try {
                cue(p, sc, a);
            } catch (Exception e) {
                com.yompix.novograd.Novograd.LOG.warn("Кат-сцена: не вышло {}: {}", cue, e.toString());
            }
        }
    }

    @Nullable
    private static Entity actor(ServerWorld w, Scene sc, String id) {
        UUID u = sc.actors.get(id);
        if (u != null) {
            Entity e = w.getEntity(u);
            if (e != null) return e;
        }
        return Npcs.find(w, id);
    }

    @Nullable
    private static CitizenEntity npc(ServerWorld w, Scene sc, String id) {
        return actor(w, sc, id) instanceof CitizenEntity c ? c : null;
    }

    /** Сообщить о новом актёре всем, кто смотрит эту сцену (постановщику и зрителям). */
    private static void announce(ServerPlayerEntity p, String name, Entity e) {
        Scene mine = SCENES.get(p.getUuid());
        announceTo(p, name, e);
        if (mine == null) return;
        for (Map.Entry<UUID, Scene> en : SCENES.entrySet()) {
            if (en.getKey().equals(p.getUuid()) || en.getValue().actors != mine.actors) continue;
            ServerPlayerEntity q = p.getServer().getPlayerManager().getPlayer(en.getKey());
            if (q != null) announceTo(q, name, e);
        }
    }

    private static void announceTo(ServerPlayerEntity p, String name, Entity e) {
        NbtCompound n = new NbtCompound();
        n.putString("name", name);
        n.putInt("id", e.getId());
        LifeNet.send(p, "cut_actor", n);
    }

    private static boolean isCharacter(String id) {
        var ch = Story.root().getAsJsonObject("characters");
        return ch != null && ch.has(id);
    }

    private static void cue(ServerPlayerEntity p, Scene sc, JsonArray a) {
        ServerWorld w = p.getServerWorld();
        String kind = a.get(0).getAsString();
        switch (kind) {
            case "spawn" -> {
                String id = a.get(1).getAsString(), place = a.get(2).getAsString();
                Vec3d at = pos(w, place);
                if (at == null) return;
                CitizenEntity c;
                if (isCharacter(id)) {
                    c = Npcs.spawn(p, id, base(place));
                    c.refreshPositionAndAngles(at.x, Story.ground(w, at).getY(), at.z, c.getYaw(), 0);
                    c.setStationary(true);
                } else {
                    String look = a.size() > 3 ? a.get(3).getAsString() : "citizen";
                    CitizenEntity.Role role = switch (look) {
                        case "gang" -> CitizenEntity.Role.GANG;
                        case "police" -> CitizenEntity.Role.POLICE;
                        default -> CitizenEntity.Role.CITIZEN;
                    };
                    int skin = switch (look) {
                        case "gang" -> CitizenEntity.RANDOM_FROM + 39 - w.getRandom().nextInt(4) * 2;
                        case "police" -> CitizenEntity.skinIndex("police");
                        case "citizen" -> CitizenEntity.randomSkin(w.getRandom());
                        default -> CitizenEntity.skinIndex(look);
                    };
                    c = new CitizenEntity(ModContent.CITIZEN, w);
                    c.setup(skin, role, true, a.size() > 4 ? a.get(4).getAsString() : role == CitizenEntity.Role.POLICE ? "Полицейский" : role == CitizenEntity.Role.GANG ? "Бандит" : "Прохожий");
                    c.setCustomNameVisible(false);
                    c.refreshPositionAndAngles(at.x, Story.ground(w, at).getY(), at.z, Story.placeYaw(w, base(place)), 0);
                    w.spawnEntity(c);
                    c.setStationary(true);
                    sc.extras.add(c.getUuid());
                }
                sc.actors.put(id, c.getUuid());
                announce(p, id, c);
            }
            case "walk", "run" -> {
                CitizenEntity c = npc(w, sc, a.get(1).getAsString());
                Vec3d to = pos(w, a.get(2).getAsString());
                if (c != null && to != null) c.direct(to, kind.equals("run") ? 1.35 : 0.62);
            }
            case "act" -> {
                CitizenEntity c = npc(w, sc, a.get(1).getAsString());
                if (c == null) return;
                String act = a.get(2).getAsString();
                c.lockActivity(act.equals("none") ? null : CitizenEntity.Activity.valueOf(act));
                if (act.equals("AIM")) c.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModContent.PISTOL));
                if (a.size() > 3) face(p, sc, c, a.get(3).getAsString());
            }
            case "face" -> {
                CitizenEntity c = npc(w, sc, a.get(1).getAsString());
                if (c != null) face(p, sc, c, a.get(2).getAsString());
            }
            case "car" -> {
                String id = a.get(1).getAsString(), place = a.get(2).getAsString();
                Vec3d at = pos(w, place);
                if (at == null) return;
                CarEntity car = new CarEntity(ModContent.CAR, w);
                float yaw = a.size() > 4 ? a.get(4).getAsFloat() : Story.placeYaw(w, base(place));
                car.refreshPositionAndAngles(at.x, Story.ground(w, at).getY(), at.z, yaw, 0);
                car.setVariant(a.size() > 3 ? a.get(3).getAsInt() : 0);
                w.spawnEntity(car);
                sc.extras.add(car.getUuid());
                sc.actors.put(id, car.getUuid());
                announce(p, id, car);
            }
            case "ride" -> {
                CitizenEntity c = npc(w, sc, a.get(1).getAsString());
                Entity car = actor(w, sc, a.get(2).getAsString());
                if (c != null && car != null) {
                    c.directTo = null;
                    c.startRiding(car, true);
                }
            }
            case "exit" -> {
                CitizenEntity c = npc(w, sc, a.get(1).getAsString());
                if (c != null) {
                    c.stopRiding();
                    c.setStationary(true);
                }
            }
            case "drive" -> {
                if (!(actor(w, sc, a.get(1).getAsString()) instanceof CarEntity car)) return;
                if (!car.hasNpcDriver()) { // без водителя машина не поедет — сажаем массовку
                    CitizenEntity d = new CitizenEntity(ModContent.CITIZEN, w);
                    d.setup(CitizenEntity.randomSkin(w.getRandom()), CitizenEntity.Role.CITIZEN, true, "Водитель");
                    d.refreshPositionAndAngles(car.getX(), car.getY(), car.getZ(), 0, 0);
                    w.spawnEntity(d);
                    sc.extras.add(d.getUuid());
                    d.startRiding(car, true);
                }
                List<Vec3d> pts = new ArrayList<>();
                for (JsonElement e : a.get(2).getAsJsonArray()) {
                    Vec3d v = pos(w, e.getAsString());
                    if (v != null) pts.add(v);
                }
                car.setRoute(pts, a.size() > 3 ? a.get(3).getAsFloat() : 0.6f, null);
            }
            case "shoot" -> {
                Entity sh = actor(w, sc, a.get(1).getAsString());
                if (!(sh instanceof LivingEntity)) return;
                String tgt = a.get(2).getAsString();
                Entity te = actor(w, sc, tgt);
                Vec3d tp = te == null ? pos(w, tgt) : null;
                if (te == null && tp != null) tp = tp.add(0, 1.2, 0);
                if (sh instanceof CitizenEntity c) {
                    c.lockActivity(CitizenEntity.Activity.AIM);
                    c.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModContent.PISTOL));
                    if (te != null) c.face(te);
                }
                sc.bursts.add(new int[]{0, a.size() > 3 ? a.get(3).getAsInt() : 3, 1});
                sc.burstInfo.add(new Object[]{sh.getUuid(), te == null ? null : te.getUuid(), tp, a.size() > 4 ? a.get(4).getAsFloat() : 0f});
            }
            case "die" -> {
                if (actor(w, sc, a.get(1).getAsString()) instanceof LivingEntity le) {
                    Weapons.blood(w, le, 4, true);
                    if (le instanceof CitizenEntity c && !c.getStoryId().isEmpty()) {
                        c.lockActivity(CitizenEntity.Activity.SLEEP); // герой сюжета не умирает навсегда — «лежит»
                    } else le.kill();
                }
            }
            case "fight" -> {
                CitizenEntity x = npc(w, sc, a.get(1).getAsString()), y = npc(w, sc, a.get(2).getAsString());
                if (x != null && y != null) {
                    int t = a.size() > 3 ? a.get(3).getAsInt() : 120;
                    x.sceneWith(y, CitizenEntity.Activity.FIGHT, t);
                    y.sceneWith(x, CitizenEntity.Activity.FIGHT, t);
                }
            }
            case "despawn" -> {
                Entity e = actor(w, sc, a.get(1).getAsString());
                if (e != null && sc.extras.contains(e.getUuid())) e.discard();
                else if (e != null) Npcs.despawn(p, a.get(1).getAsString());
            }
            case "boom" -> {
                Vec3d v = pos(w, a.get(1).getAsString());
                if (v == null) return;
                w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, v.x, v.y + 1, v.z, 1, 0, 0, 0, 0);
                w.spawnParticles(ParticleTypes.FLAME, v.x, v.y + 1, v.z, 40, 1.2, 0.8, 1.2, 0.05);
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, v.x, v.y + 2, v.z, 60, 1.5, 1.5, 1.5, 0.04);
                w.playSound(null, v.x, v.y, v.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 3f, 0.9f);
            }
            case "smoke" -> {
                Vec3d v = pos(w, a.get(1).getAsString());
                if (v != null) w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, v.x, v.y + 1, v.z, 25, 0.8, 0.5, 0.8, 0.02);
            }
            default -> Actions.run(p, Life.get(p), a);
        }
    }

    private static void face(ServerPlayerEntity p, Scene sc, CitizenEntity c, String who) {
        if (who.equals("player")) {
            c.face(p);
            return;
        }
        Entity o = actor(p.getServerWorld(), sc, who);
        if (o != null) c.face(o);
    }

    /** Конец сцены: массовку и машины убрать, героев отпустить. */
    public static void stop(ServerPlayerEntity p) {
        Scene sc = SCENES.remove(p.getUuid());
        if (sc == null || sc.viewer) return;
        // напарник ещё смотрит — он доиграет и уберёт массовку сам
        for (Map.Entry<UUID, Scene> en : SCENES.entrySet()) {
            if (en.getValue().actors == sc.actors) {
                en.getValue().viewer = false;
                en.getValue().cues.clear();
                return;
            }
        }
        ServerWorld w = p.getServerWorld();
        for (UUID u : sc.extras) {
            Entity e = w.getEntity(u);
            if (e != null) e.discard();
        }
        for (Map.Entry<String, UUID> en : sc.actors.entrySet()) {
            if (sc.extras.contains(en.getValue())) continue;
            if (w.getEntity(en.getValue()) instanceof CitizenEntity c) {
                c.directTo = null;
                if (c.hasVehicle()) c.stopRiding();
                c.lockActivity(null);
                c.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                c.setStationary(true);
            }
        }
    }
}
