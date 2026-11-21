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
 *   gest   id g_жест [сек] [на кого]     — 1.0: слой-жест поверх тела (g_handshake, g_phone, g_point, g_push, g_punch,
 *                                          g_laugh, g_wave, g_fidget, g_walktalk);  look id кто — взгляд (none — снять)
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
        /** 0.8: план постановщика — где должен стоять герой кадра (глаза) в начале плана. */
        String subject = "";
        @Nullable double[] aim;
    }

    private static final class Scene {
        final List<JsonArray> cues = new ArrayList<>();
        final List<Shot> shots = new ArrayList<>();
        Map<String, UUID> actors = new HashMap<>();
        Set<UUID> extras = new HashSet<>();
        /** Зритель: сцену ставит напарник, а этот игрок её только смотрит (актёры общие). */
        boolean viewer;
        /**
         * Клиент ждёт загрузку местности и актёров — сцена стоит. С 0.6.5 сцена начинается «на паузе», пока клиент
         * не скажет «готов» (cut_hold off); не ответил за 12 с — идём сами, чтобы сцена не зависла.
         */
        boolean hold = SYNC;
        int holdTicks;
        /** План, которого ждёт клиент (-1 — неизвестно): туда и ставим «зрителя», чтобы участки и актёры пришли. */
        int waitShot = -1;
        /** Какой сетевой id актёра уже сообщили клиенту: если район выгружался и актёр загрузился заново, id новый. */
        final Map<String, Integer> announced = new HashMap<>();
        JsonObject step;
        final List<int[]> bursts = new ArrayList<>(); // [индекс очереди, осталось, кд]
        final List<Object[]> burstInfo = new ArrayList<>();
        int t, next;
        int markedShot = -1;
    }

    private static final Map<UUID, Scene> SCENES = new HashMap<>();
    /** Старт сцены по готовности клиента (0.6.5). -Dnovograd.cutsync=false — как в 0.6.4 (для сравнения в автотестах). */
    static final boolean SYNC = !"false".equals(System.getProperty("novograd.cutsync"));
    /** На сколько «зритель» может отстать от камеры, прежде чем его перенесут (1.0: реже — без лишней перезагрузки участков). */
    static final double VIEWER_GAP = Stage.ON ? 48 : 24;

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
            if (j.has("keys")) { // план из постановщика (tools/shot_planner.py): ключи камеры [t, x, y, z, yaw, pitch, fov, roll]
                JsonArray ks = j.getAsJsonArray("keys");
                s.from = key(ks.get(0).getAsJsonArray());
                s.to = key(ks.get(ks.size() - 1).getAsJsonArray());
            }
            s.ticks = Math.round(j.get("sec").getAsFloat() * 20);
            s.track = Story.str(j, "track", "");
            s.follow = Story.str(j, "follow", "");
            s.subject = Story.str(j, "subject", "");
            if (j.has("aim") && j.getAsJsonArray("aim").size() >= 3) {
                JsonArray a = j.getAsJsonArray("aim");
                s.aim = new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()};
            }
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
        toMark(w, sc, 0); // пока экран чёрный (вход сцены)
        Stage.started(p); // заготовка сцены больше не нужна (лишнюю массовку — убрать)
    }

    static double[] key(JsonArray k) {
        return new double[]{k.get(1).getAsDouble(), k.get(2).getAsDouble(), k.get(3).getAsDouble(), k.get(4).getAsDouble(), k.get(5).getAsDouble()};
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
        // камера не внутри здания: поднимаем до воздуха (крыша, балкон)
        net.minecraft.util.math.BlockPos.Mutable bp = new net.minecraft.util.math.BlockPos.Mutable(v.x, y, v.z);
        w.getChunk(bp);
        for (int i = 0; i < 80 && (!w.getBlockState(bp).isAir() || !w.getBlockState(bp.up()).isAir()); i++) {
            y += 1;
            bp.setY((int) Math.floor(y));
        }
        return new double[]{v.x, y, v.z, yaw, pitch};
    }

    private static String base(String spec) {
        int k = spec.indexOf('+', 1);
        return k > 0 ? spec.substring(0, k) : spec;
    }

    /** Место со сдвигом: «gang_yard+3,-2». */
    @Nullable
    public static Vec3d pos(ServerWorld w, String spec) {
        String b = base(spec);
        Vec3d v = Story.place(w, b);
        if (v == null || b.length() == spec.length()) return v;
        String[] d = spec.substring(b.length() + 1).split(",");
        double dx = Double.parseDouble(d[0]), dz = d.length > 1 ? Double.parseDouble(d[1]) : 0, up = d.length > 2 ? Double.parseDouble(d[2]) : 0;
        int x = (int) Math.floor(v.x + dx), z = (int) Math.floor(v.z + dz);
        return new Vec3d(v.x + dx, Math.max(v.y, com.yompix.novograd.world.Island.surface(x, z) + 1) + up, v.z + dz);
    }

    /** Клиент: «жду загрузку» / «показываю». */
    public static void hold(ServerPlayerEntity p, boolean on, int shot) {
        Scene sc = SCENES.get(p.getUuid());
        if (sc != null) {
            if (sc.hold != on) sc.holdTicks = 0;
            sc.hold = on;
            sc.waitShot = on ? shot : -1;
        }
    }

    /** Актёры, которые уже стоят в кадре на старте (клиент ждёт, пока они к нему придут). */
    public static List<String> cast(ServerPlayerEntity p, @Nullable Vec3d firstCam) {
        Scene sc = SCENES.get(p.getUuid());
        List<String> r = new ArrayList<>();
        if (sc == null) return r;
        for (Map.Entry<String, UUID> e : sc.actors.entrySet()) {
            Entity en = p.getServerWorld().getEntity(e.getValue());
            // только те, кто рядом с первым кадром: дальних клиент и не получит, пока камера туда не долетит
            if (en != null && (firstCam == null || en.squaredDistanceTo(firstCam) < 72 * 72)) r.add(e.getKey());
        }
        return r;
    }

    /** Движение (идти, ехать, стрелять, драться, взрыв) не начинаем, пока клиент грузит сцену — иначе актёры «убегают». */
    private static final Set<String> MOTION = Set.of("walk", "run", "drive", "shoot", "fight", "boom", "die", "ride", "exit", "gest");

    /**
     * Реплика в кат-сцене: говорящий жестикулирует, остальные герои поворачиваются к нему и замолкают
     * (раньше все, у кого есть реплики, «болтали» всю сцену разом).
     */
    public static void speak(ServerPlayerEntity p, String who) {
        Scene sc = SCENES.get(p.getUuid());
        if (sc == null || sc.viewer) return;
        ServerWorld w = p.getServerWorld();
        Entity speaker = who.equals("player") ? p : actor(w, sc, who);
        for (Map.Entry<String, UUID> en : sc.actors.entrySet()) {
            if (!(w.getEntity(en.getValue()) instanceof CitizenEntity c)) continue;
            var act = c.getActivity();
            boolean free = act == CitizenEntity.Activity.IDLE || act == CitizenEntity.Activity.TALK;
            if (!free) continue; // у героя своя сцена (целится, спорит, обнимает) — не трогаем
            boolean still = c.getVelocity().horizontalLengthSquared() < 0.003;
            if (en.getKey().equals(who)) {
                c.lockActivity(CitizenEntity.Activity.TALK);
                // говорящий смотрит на ближайшего собеседника (как в GTA: разговор лицом к лицу)
                if (still) {
                    Entity near = null;
                    for (UUID u : sc.actors.values()) {
                        Entity o = w.getEntity(u);
                        if (o instanceof CitizenEntity && o != c && o.squaredDistanceTo(c) < 8 * 8 && (near == null || o.squaredDistanceTo(c) < near.squaredDistanceTo(c))) near = o;
                    }
                    if (near != null) c.face(near);
                }
            } else {
                c.lockActivity(null);
                if (speaker != null && still && speaker != c && speaker.squaredDistanceTo(c) < 24 * 24) c.face(speaker);
            }
        }
    }

    public static boolean running(ServerPlayerEntity p) {
        return SCENES.containsKey(p.getUuid());
    }

    public static void tick(ServerPlayerEntity p) {
        Scene sc = SCENES.get(p.getUuid());
        if (sc == null) return;
        ServerWorld w = p.getServerWorld();
        // актёры живут, даже если «зритель» далеко от них
        if (w.getTime() % 20 == 0) for (UUID u : sc.actors.values()) {
            Entity e = w.getEntity(u);
            if (e != null) w.getChunkManager().addTicket(net.minecraft.server.world.ChunkTicketType.PORTAL, e.getChunkPos(), 2, e.getBlockPos());
        }
        // актёр перезагрузился вместе с районом (новый сетевой id) — сообщить клиенту заново, иначе камера его «не видит»;
        // и пересчитать, видит ли его зритель (ванильный трекер делает это только при смене секции)
        if (w.getTime() % 10 == 5) for (Map.Entry<String, UUID> en : sc.actors.entrySet()) {
            Entity e = w.getEntity(en.getValue());
            if (e == null) continue;
            if (!sc.viewer && !Integer.valueOf(e.getId()).equals(sc.announced.get(en.getKey()))) announce(p, en.getKey(), e);
            retrack(w, e, p);
        }
        if (sc.hold && ++sc.holdTicks > 240) {
            com.yompix.novograd.Novograd.LOG.warn("Кат-сцена: клиент {} не ответил «готов» за 12 с — играем дальше", p.getName().getString());
            sc.hold = false;
            sc.holdTicks = 0;
        }
        if (sc.hold && sc.holdTicks == 60 && sc.waitShot >= 0 && sc.waitShot < sc.shots.size()) {
            // клиент ждёт план уже 3 с — в лог, где актёр плана и где «зритель» (для разбора «не догружается»)
            Shot ws = sc.shots.get(sc.waitShot);
            for (String n : new String[]{ws.track, ws.follow}) {
                if (n.isEmpty()) continue;
                UUID u = sc.actors.get(n);
                Entity e = u == null ? null : w.getEntity(u);
                com.yompix.novograd.Novograd.LOG.info("[cut] сервер: план {} ждёт «{}»: {} (id {}), до зрителя {} блоков, объявлен id {}", sc.waitShot, n,
                        e == null ? (u == null ? "не в сцене" : "нет в мире") : e.getBlockPos().toShortString(), e == null ? -1 : e.getId(),
                        e == null ? -1 : (int) Math.sqrt(e.squaredDistanceTo(p)), sc.announced.get(n));
            }
        }
        if (sc.hold) {
            Vec3d at = sc.waitShot >= 0 ? shotAt(w, sc, sc.waitShot) : camAt(w, sc, 0);
            // герой плана далеко от камеры (дальше, чем клиенту присылают сущности) — «зритель» встаёт между ними,
            // чтобы пришли и участки у камеры, и сам герой (иначе клиент ждал его все 4 с впустую)
            if (at != null && sc.waitShot >= 0 && sc.waitShot < sc.shots.size()) {
                Shot ws = sc.shots.get(sc.waitShot);
                String who = !ws.track.isEmpty() ? ws.track : ws.follow;
                UUID u = who.isEmpty() ? null : sc.actors.get(who);
                Entity e = u == null ? null : w.getEntity(u);
                if (e != null && e.squaredDistanceTo(at) > 48 * 48) at = at.add(e.getPos()).multiply(0.5);
            }
            if (at != null && w.getTime() % 10 == 0 && p.squaredDistanceTo(at) > VIEWER_GAP * VIEWER_GAP) p.teleport(w, at.x, at.y, at.z, p.getYaw(), p.getPitch());
            return;
        }
        sc.t++;
        if (!sc.viewer) run(p, sc, sc.t);
        if (!sc.viewer) {
            int si = shotIndex(sc, sc.t);
            if (si != sc.markedShot) toMark(w, sc, si); // склейка — герой встаёт на метку плана
        }
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
            // заранее (за 1.5 с) переносим «зрителя» к следующему кадру, чтобы местность успела загрузиться
            Vec3d at = camAt(w, sc, 30);
            if (at == null) at = camAt(w, sc, 0);
            if (at != null && p.squaredDistanceTo(at) > VIEWER_GAP * VIEWER_GAP) p.teleport(w, at.x, at.y, at.z, p.getYaw(), p.getPitch());
        }
        int total = 0;
        for (Shot s : sc.shots) total += s.ticks;
        if (sc.t > total + 20 * 60) stop(p); // страховка, если клиент так и не ответил
    }

    private static int shotIndex(Scene sc, int t) {
        for (int i = 0; i < sc.shots.size(); i++) {
            if (t <= sc.shots.get(i).ticks) return i;
            t -= sc.shots.get(i).ticks;
        }
        return sc.shots.size() - 1;
    }

    /**
     * 0.8: камера больше не подстраивается под героя — если герой плана стоит не на своей метке (дальше 1.2 блока
     * от точки, которую ждал постановщик), на склейке (или в затемнении входа) переставляем ГЕРОЯ на метку.
     * Идущего героя (ему задан путь) не трогаем: его ведёт таймлайн.
     */
    private static void toMark(ServerWorld w, Scene sc, int si) {
        sc.markedShot = si;
        if (si < 0 || si >= sc.shots.size()) return;
        Shot s = sc.shots.get(si);
        if (s.aim == null || s.subject.isEmpty()) return;
        if (!(actor(w, sc, s.subject) instanceof CitizenEntity c) || c.hasVehicle() || c.directTo != null || !c.route.isEmpty()) return;
        Vec3d eye = c.getPos().add(0, c.getHeight() * 0.9, 0);
        double d = eye.distanceTo(new Vec3d(s.aim[0], s.aim[1], s.aim[2]));
        if (d < 1.2 || d > 12) return;
        Vec3d feet = new Vec3d(s.aim[0], s.aim[1] - c.getHeight() * 0.9, s.aim[2]);
        c.getNavigation().stop();
        c.refreshPositionAndAngles(feet.x, feet.y, feet.z, c.getYaw(), c.getPitch());
        c.setVelocity(Vec3d.ZERO);
        if (c.isStationary()) c.setStationary(true); // якорь стоящего героя — на новой метке
        com.yompix.novograd.Novograd.LOG.info("[cut] герой {} переставлен на метку плана {} ({} блока)", s.subject, si, String.format(java.util.Locale.ROOT, "%.1f", d));
    }

    /** Пересчитать, отправлена ли сущность игроку (см. mixin/EntityTrackerInvoker). */
    private static void retrack(ServerWorld w, Entity e, ServerPlayerEntity p) {
        try {
            Object t = ((com.yompix.novograd.mixin.ChunkLoadingAccessor) w.getChunkManager().chunkLoadingManager).novograd$trackers().get(e.getId());
            if (t instanceof com.yompix.novograd.mixin.EntityTrackerInvoker inv) inv.novograd$update(p);
        } catch (RuntimeException ignored) {
        }
    }

    /** Начало плана n: где будет камера (или над героем плана). */
    @Nullable
    private static Vec3d shotAt(ServerWorld w, Scene sc, int n) {
        if (n >= sc.shots.size()) return null;
        int t = 0;
        for (int i = 0; i < n; i++) t += sc.shots.get(i).ticks;
        int save = sc.t;
        sc.t = t + 1;
        Vec3d v = camAt(w, sc, 0);
        sc.t = save;
        return v;
    }

    @Nullable
    private static Vec3d camAt(ServerWorld w, Scene sc, int ahead) {
        int t = sc.t + ahead;
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
            if (sc.hold && sc.cues.get(sc.next).size() > 1 && MOTION.contains(sc.cues.get(sc.next).get(1).getAsString())) break;
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
        if (mine != null) mine.announced.put(name, e.getId());
        // район актёра держим загруженным с первой секунды (раньше — только со следующей проверки, и он успевал выгрузиться)
        if (p.getServerWorld() == e.getWorld())
            p.getServerWorld().getChunkManager().addTicket(net.minecraft.server.world.ChunkTicketType.PORTAL, e.getChunkPos(), 2, e.getBlockPos());
        announceTo(p, name, e);
        if (mine == null) return;
        for (Map.Entry<UUID, Scene> en : SCENES.entrySet()) {
            if (en.getKey().equals(p.getUuid()) || en.getValue().actors != mine.actors) continue;
            ServerPlayerEntity q = p.getServer().getPlayerManager().getPlayer(en.getKey());
            if (q != null) announceTo(q, name, e);
        }
    }

    /** Пакет всем, кто смотрит эту сцену (постановщику и зрителям-напарникам). */
    private static void toScene(ServerPlayerEntity p, String type, NbtCompound n) {
        Scene mine = SCENES.get(p.getUuid());
        LifeNet.send(p, type, n);
        if (mine == null) return;
        for (Map.Entry<UUID, Scene> en : SCENES.entrySet()) {
            if (en.getKey().equals(p.getUuid()) || en.getValue().actors != mine.actors) continue;
            ServerPlayerEntity q = p.getServer().getPlayerManager().getPlayer(en.getKey());
            if (q != null) LifeNet.send(q, type, n.copy());
        }
    }

    private static void announceTo(ServerPlayerEntity p, String name, Entity e) {
        NbtCompound n = new NbtCompound();
        n.putString("name", name);
        n.putInt("id", e.getId());
        LifeNet.send(p, "cut_actor", n);
    }

    /** Массовка сцены по описанию (gang / police / citizen / имя скина). */
    static CitizenEntity extra(ServerWorld w, String look, @Nullable String name) {
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
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        // 0.8: массовка «в роли героя» (вступление: Джейк у гаража — старый скин c25 + имя героя) — в облике героя
        String as = heroByOldSkin(look, name);
        if (as != null) {
            c.setupAsHero(as, role, name);
            c.setCustomNameVisible(false);
            return c;
        }
        c.setup(skin, role, true, name != null ? name : role == CitizenEntity.Role.POLICE ? "Полицейский" : role == CitizenEntity.Role.GANG ? "Бандит" : "Прохожий");
        c.setCustomNameVisible(false);
        return c;
    }

    /** Герой по имени («Брик», «Виктор Кроу», «Шеф Харрис») или null. */
    @Nullable
    public static String heroByName(@Nullable String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            for (var en : Story.root().getAsJsonObject("characters").entrySet()) {
                var ch = en.getValue().getAsJsonObject();
                if (!ch.has("name") || CitizenEntity.storySkin(en.getKey()) < 0) continue;
                String full = ch.get("name").getAsString();
                if (full.equals(name) || full.split(" ")[0].equals(name) || full.endsWith(" " + name)) return en.getKey();
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /** Герой, которого изображает массовка: её облик — бывший скин героя, а имя — его имя. */
    @Nullable
    private static String heroByOldSkin(String look, @Nullable String name) {
        if (name == null) return null;
        try {
            for (var en : Story.root().getAsJsonObject("characters").entrySet()) {
                var ch = en.getValue().getAsJsonObject();
                if (ch.has("skin") && !ch.get("skin").isJsonNull() && look.equals(ch.get("skin").getAsString()) && ch.has("name")
                        && ch.get("name").getAsString().startsWith(name.split(" ")[0]) && CitizenEntity.storySkin(en.getKey()) >= 0) return en.getKey();
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /** Для Stage: массовка нулевой секунды по реплике таймлайна [0, "spawn", id, place, look, name]. */
    @Nullable
    static CitizenEntity makeExtra(ServerWorld w, JsonArray cue, Vec3d at) {
        String place = cue.get(3).getAsString();
        CitizenEntity c = extra(w, cue.size() > 4 ? cue.get(4).getAsString() : "citizen", cue.size() > 5 ? cue.get(5).getAsString() : null);
        c.refreshPositionAndAngles(at.x, Story.ground(w, at).getY(), at.z, Story.placeYaw(w, base(place)), 0);
        return c;
    }

    static boolean isCharacterId(String id) {
        return isCharacter(id);
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
                w.getChunk(net.minecraft.util.math.BlockPos.ofFloored(at)); // район актёра загружен, иначе он потеряется
                CitizenEntity c;
                if (isCharacter(id)) {
                    c = Npcs.spawn(p, id, base(place));
                    c.refreshPositionAndAngles(at.x, Story.ground(w, at).getY(), at.z, c.getYaw(), 0);
                    c.setStationary(true);
                } else {
                    // 1.0: массовку могли поставить заранее (Stage, пока игрок подходил) — принимаем её, а не создаём
                    c = Stage.take(p, sc.step, id);
                    if (c == null) {
                        c = extra(w, a.size() > 3 ? a.get(3).getAsString() : "citizen", a.size() > 4 ? a.get(4).getAsString() : null);
                        c.refreshPositionAndAngles(at.x, Story.ground(w, at).getY(), at.z, Story.placeYaw(w, base(place)), 0);
                        w.spawnEntity(c);
                        c.setStationary(true);
                    }
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
            case "gest" -> { // 1.0: слой-жест поверх тела: gest id g_handshake [сек] [на кого смотреть]
                Entity e = actor(w, sc, a.get(1).getAsString());
                if (e == null) return;
                NbtCompound n = new NbtCompound();
                n.putInt("id", e.getId());
                n.putString("g", a.get(2).getAsString());
                n.putInt("ticks", Math.round((a.size() > 3 ? a.get(3).getAsFloat() : 2f) * 20));
                if (a.size() > 4) {
                    String who = a.get(4).getAsString();
                    Entity t = who.equals("player") ? p : who.equals("none") ? null : actor(w, sc, who);
                    n.putInt("look", t == null ? -1 : t.getId());
                    if (t != null && e instanceof CitizenEntity c && !who.equals("player")) c.face(t);
                }
                toScene(p, "cut_gest", n);
            }
            case "look" -> { // 1.0: look id кто — взгляд (шея/голова) на героя, «none» — снять
                Entity e = actor(w, sc, a.get(1).getAsString());
                if (e == null) return;
                String who = a.get(2).getAsString();
                Entity t = who.equals("player") ? p : who.equals("none") ? null : actor(w, sc, who);
                NbtCompound n = new NbtCompound();
                n.putInt("id", e.getId());
                n.putString("g", "");
                n.putInt("ticks", 0);
                n.putInt("look", t == null ? -1 : t.getId());
                toScene(p, "cut_gest", n);
            }
            case "face" -> {
                CitizenEntity c = npc(w, sc, a.get(1).getAsString());
                if (c != null) face(p, sc, c, a.get(2).getAsString());
            }
            case "car" -> {
                String id = a.get(1).getAsString(), place = a.get(2).getAsString();
                Vec3d at = pos(w, place);
                if (at == null) return;
                w.getChunk(net.minecraft.util.math.BlockPos.ofFloored(at));
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
            default -> { if (!DirectorCues.handle(p, a, id -> id.equals("player") ? p : actor(w, sc, id))) Actions.run(p, Life.get(p), a); } // sfx, music, give, door… (DirectorCues)
        }
    }

    private static void face(ServerPlayerEntity p, Scene sc, CitizenEntity c, String who) {
        if (who.equals("cam")) { // 0.9: лицом к камере следующего плана (крупный план не со спины)
            Vec3d at = camAt(p.getServerWorld(), sc, 2);
            if (at == null) return;
            float yaw = (float) Math.toDegrees(Math.atan2(-(at.x - c.getX()), at.z - c.getZ()));
            c.getNavigation().stop();
            c.setYaw(yaw);
            c.setBodyYaw(yaw);
            c.setHeadYaw(yaw);
            return;
        }
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
