package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
import com.yompix.novograd.world.IslandRoads;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Город сам реагирует на происшествия — без игрока:
 *  • сбили или подстрелили прохожего — едет скорая с сиреной, фельдшеры встают на колено у пострадавшего,
 *    живого поднимают на ноги, потом уезжают; раненый (меньше 40% здоровья) лежит и ждёт врачей;
 *  • горят машины и уличные предметы (world/Fires, взорванные машины) — пожарная машина, расчёт тушит из рукава;
 *  • грабёж, стрельба бандитов, кража сумки — патруль едет на место даже без розыска игрока, осматривается,
 *    а грабителя-NPC ловит («копы против грабителей»). Иногда на улице сам случается грабёж прохожего.
 * Дёшево: не больше 2 машин служб одновременно и только рядом с игроком (≤110 блоков).
 */
public final class Emergency {
    private Emergency() {}

    enum Kind { AMBULANCE, FIRE, POLICE }

    private static final int DRIVING = 0, WALK_IN = 1, WORK = 2, WALK_BACK = 3, LEAVING = 4;

    static final class Unit {
        final Kind kind;
        Vec3d target;
        @Nullable UUID car, patient, suspect;
        final List<UUID> crew = new ArrayList<>();
        int stage, timer, work;
        long started, stageAt;
        String what = "";
        boolean saved;

        Unit(Kind kind, Vec3d target) {
            this.kind = kind;
            this.target = target;
        }
    }

    private record Call(Kind kind, Vec3d at, @Nullable UUID patient, @Nullable UUID suspect, String what, long when) {}

    private static final List<Unit> UNITS = new ArrayList<>();
    private static final List<Call> PENDING = new ArrayList<>();
    private static final Map<Kind, Long> LAST = new EnumMap<>(Kind.class);
    private static final Set<UUID> PATIENTS = new HashSet<>();
    private static long nextMugging = 20 * 120;

    public static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register(Emergency::afterDamage);
    }

    // ------------------------------------------------------------------ вызовы
    private static void afterDamage(LivingEntity e, DamageSource src, float base, float taken, boolean blocked) {
        if (!(e instanceof CitizenEntity c) || !(e.getWorld() instanceof ServerWorld w) || taken <= 0) return;
        if (!c.getStoryId().isEmpty()) return;
        if (c.getRole() != CitizenEntity.Role.CITIZEN && c.getRole() != CitizenEntity.Role.GANG && c.getRole() != CitizenEntity.Role.POLICE) return;
        if (c.hasVehicle()) return;
        boolean dead = c.isDead();
        if (!dead && c.getHealth() > c.getMaxHealth() * 0.5f) return;
        // раненый горожанин падает и ждёт скорую (бандиты и полиция дерутся дальше)
        if (!dead && c.getRole() == CitizenEntity.Role.CITIZEN && !c.storyProtected() && !CitizenEntity.runOverHit && !c.knockedDown() && c.getHealth() < c.getMaxHealth() * 0.4f) {
            Entity by = src.getAttacker();
            c.knockDown(by != null ? by.getPos() : c.getPos().add(1, 0, 0), null, 120);
        }
        boolean runover = CitizenEntity.runOverHit || src.getSource() instanceof CarEntity;
        String what = runover ? (dead ? "ДТП, сбит пешеход, погиб" : "ДТП, сбит пешеход") : dead ? "огнестрел, пострадавший без признаков жизни" : "ранен прохожий";
        call(w, Kind.AMBULANCE, c.getPos(), dead ? null : c.getUuid(), null, what, 60 + w.random.nextInt(60));
    }

    /** Где пожарные уже были: туда второй раз не едут (пожар потушен, остов догорает сам). */
    private static final List<Vec3d> FIRE_DONE = new ArrayList<>();
    private static final List<Long> FIRE_DONE_AT = new ArrayList<>();

    /** Публично: сообщить о пожаре (для world/Fires, взрывов). */
    public static void reportFire(ServerWorld w, Vec3d at) {
        long now = w.getTime();
        for (int i = FIRE_DONE.size() - 1; i >= 0; i--) {
            if (now - FIRE_DONE_AT.get(i) > 20 * 120) {
                FIRE_DONE.remove(i);
                FIRE_DONE_AT.remove(i);
            } else if (FIRE_DONE.get(i).squaredDistanceTo(at) < 30 * 30) return;
        }
        call(w, Kind.FIRE, at, null, null, "возгорание", 80);
    }

    /** Преступление NPC (StreetEvents: кража сумки, ограбление магазина; стрельба бандитов): патруль едет на место. */
    public static void npcCrime(ServerWorld w, @Nullable LivingEntity suspect, Vec3d at, String what, int delayTicks) {
        Dispatch.report(w, at, what, suspect != null && !suspect.hasVehicle() ? "подозреваемый пешком" : null, null);
        call(w, Kind.POLICE, at, null, suspect == null ? null : suspect.getUuid(), what, delayTicks);
    }

    /** Выстрел (Crime.gunshot): стреляли NPC — патруль на место. Стреляет игрок — это уже розыск (Wanted). */
    public static void gunshot(ServerWorld w, LivingEntity shooter, Vec3d at) {
        if (shooter instanceof ServerPlayerEntity) return;
        long now = w.getTime();
        if (now - LAST.getOrDefault(Kind.POLICE, -100000L) < 20 * 60) return;
        npcCrime(w, shooter instanceof CitizenEntity c && c.getRole() == CitizenEntity.Role.GANG ? shooter : null, at, "стрельба", 200);
    }

    private static void call(ServerWorld w, Kind k, Vec3d at, @Nullable UUID patient, @Nullable UUID suspect, String what, int delay) {
        if (!(w.getChunkManager().getChunkGenerator() instanceof com.yompix.novograd.world.IslandChunkGenerator)) return;
        if (!nearPlayer(w, at, 110)) return;
        for (Unit u : UNITS) if (u.kind == k && u.target.squaredDistanceTo(at) < 40 * 40) return; // уже едут
        for (Call c : PENDING) if (c.kind == k && c.at.squaredDistanceTo(at) < 40 * 40) return;
        if (PENDING.size() > 4) return;
        PENDING.add(new Call(k, at, patient, suspect, what, w.getTime() + delay));
        if (patient != null) PATIENTS.add(patient);
    }

    private static boolean nearPlayer(ServerWorld w, Vec3d at, double r) {
        for (ServerPlayerEntity p : w.getPlayers()) if (p.squaredDistanceTo(at.x, p.getY(), at.z) < r * r) return true;
        return false;
    }

    // ------------------------------------------------------------------ тик
    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        ServerWorld w = server.getOverworld();
        if (t % 10 != 4) return;
        long now = w.getTime();
        // вызовы, которым пора ехать
        for (Iterator<Call> it = PENDING.iterator(); it.hasNext(); ) {
            Call c = it.next();
            if (now < c.when) continue;
            it.remove();
            if (UNITS.size() >= 2 || UNITS.stream().anyMatch(u -> u.kind == c.kind)) {
                if (c.patient != null) PATIENTS.remove(c.patient);
                continue;
            }
            if (now - LAST.getOrDefault(c.kind, -100000L) < 20 * 25) {
                if (c.patient != null) PATIENTS.remove(c.patient);
                continue;
            }
            dispatch(w, c);
        }
        for (Iterator<Unit> it = UNITS.iterator(); it.hasNext(); ) {
            Unit u = it.next();
            boolean done;
            try {
                done = step(w, u, now);
            } catch (RuntimeException ex) {
                com.yompix.novograd.Novograd.LOG.warn("emergency unit", ex);
                done = true;
            }
            if (done) {
                cleanup(w, u);
                it.remove();
            }
        }
        // раненые ждут врачей лёжа
        if (t % 20 == 4) for (Iterator<UUID> it = PATIENTS.iterator(); it.hasNext(); ) {
            if (w.getEntity(it.next()) instanceof CitizenEntity c && c.isAlive() && c.knockedDown()) c.knockDown(c.getPos().add(1, 0, 0), null, 50);
            else it.remove();
        }
        if (t % 100 == 4) watchFires(w);
        if (t % 20 == 14) muggings(w, now);
    }

    /** Взорванные машины и уличные пожары рядом с игроком → пожарные. */
    private static void watchFires(ServerWorld w) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            for (CarEntity car : w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(80, 20, 80), CarEntity::wrecked)) {
                reportFire(w, car.getPos());
                return;
            }
            for (Vec3d f : com.yompix.novograd.world.FireWatch.spots(w))
                if (f.squaredDistanceTo(p.getPos()) < 80 * 80) {
                    reportFire(w, f);
                    return;
                }
        }
    }

    // ------------------------------------------------------------------ выезд
    private static int lineOf(double v) {
        return TrafficLights.line(v);
    }

    /** Точка на полосе ближайшей улицы у места происшествия. */
    private static Vec3d roadNear(ServerWorld w, Vec3d at) {
        int lx = lineOf(at.x), lz = lineOf(at.z);
        double px, pz;
        if (Math.abs(at.x - lx) <= Math.abs(at.z - lz)) { px = lx + 2.5; pz = at.z; }
        else { px = at.x; pz = lz + 2.5; }
        if (!Island.road((int) Math.floor(px), (int) Math.floor(pz))) {
            int[] n = IslandRoads.nearestNode(at.x, at.z);
            if (n != null) { px = n[0] + 0.5; pz = n[1] + 0.5; }
        }
        return new Vec3d(px, Story.topY(w, (int) Math.floor(px), (int) Math.floor(pz)), pz);
    }

    /** Путь по узлам дорог → точки по правой полосе; на поворотах — подход, угол и выход, чтобы не срезать на тротуар. */
    private static List<Vec3d> lanes(List<int[]> nodes, double y) {
        List<Vec3d> out = new ArrayList<>();
        int n = nodes.size();
        final double L = 2.5;
        for (int i = 0; i < n; i++) {
            int[] c = nodes.get(i);
            int[] dout = i < n - 1 ? dir(c, nodes.get(i + 1)) : null, din = i > 0 ? dir(nodes.get(i - 1), c) : null;
            if (din == null) din = dout;
            if (dout == null) dout = din;
            if (din == null) break;
            double rinX = -din[1], rinZ = din[0], routX = -dout[1], routZ = dout[0];
            if (din[0] == dout[0] && din[1] == dout[1] || din[0] == -dout[0] && din[1] == -dout[1]) {
                if (i > 0) out.add(new Vec3d(c[0] + 0.5 + rinX * L, y, c[1] + 0.5 + rinZ * L));
                if (din[0] == -dout[0] && din[1] == -dout[1]) out.add(new Vec3d(c[0] + 0.5 + routX * L, y, c[1] + 0.5 + routZ * L));
            } else {
                if (i > 0) out.add(new Vec3d(c[0] + 0.5 - din[0] * 9 + rinX * L, y, c[1] + 0.5 - din[1] * 9 + rinZ * L));
                out.add(new Vec3d(c[0] + 0.5 + rinX * L + routX * L, y, c[1] + 0.5 + rinZ * L + routZ * L));
                out.add(new Vec3d(c[0] + 0.5 + dout[0] * 9 + routX * L, y, c[1] + 0.5 + dout[1] * 9 + routZ * L));
            }
        }
        return out;
    }

    /** Уехать с места: по дорогам к узлу в ~100 блоках (по ходу машины, если можно). */
    private static List<Vec3d> away(ServerWorld w, CarEntity car) {
        double rad = Math.toRadians(car.getYaw());
        for (int i = 0; i < 6; i++) {
            double a = rad + (i == 0 ? 0 : (w.random.nextDouble() - 0.5) * Math.PI * (i < 3 ? 1 : 2));
            double tx = car.getX() - Math.sin(a) * 100, tz = car.getZ() + Math.cos(a) * 100;
            List<int[]> nodes = IslandRoads.route(car.getX() - Math.sin(rad) * 20, car.getZ() + Math.cos(rad) * 20, tx, tz);
            if (nodes.size() >= 2) return lanes(nodes, car.getY());
        }
        return FunUtil.wander(w, car.getX(), car.getZ(), 6, w.random);
    }

    private static int[] dir(int[] a, int[] b) {
        return new int[]{Integer.signum(b[0] - a[0]), Integer.signum(b[1] - a[1])};
    }

    private static void dispatch(ServerWorld w, Call c) {
        var r = w.getRandom();
        Vec3d goal = roadNear(w, c.at);
        // откуда выезжают: узел дорог в 55–85 блоках, не на глазах у игрока
        Vec3d from = null;
        for (int i = 0; i < 16 && from == null; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 55 + r.nextInt(30);
            int[] n = IslandRoads.nearestNode(goal.x + Math.cos(a) * d, goal.z + Math.sin(a) * d);
            if (n == null) continue;
            Vec3d cand = new Vec3d(n[0] + 0.5, 0, n[1] + 0.5);
            if (cand.squaredDistanceTo(goal.x, 0, goal.z) < 35 * 35 || !w.isChunkLoaded(BlockPos.ofFloored(cand)) || nearPlayer(w, cand, 30)) continue;
            from = new Vec3d(cand.x, Story.topY(w, n[0], n[1]), cand.z);
        }
        if (from == null) {
            if (c.patient != null) PATIENTS.remove(c.patient);
            return;
        }
        List<Vec3d> route = lanes(IslandRoads.route(from.x, from.z, goal.x, goal.z), from.y);
        route.add(goal);
        Unit u = new Unit(c.kind, goal);
        u.patient = c.patient;
        u.suspect = c.suspect;
        u.what = c.what;
        u.started = w.getTime();
        int variant = switch (c.kind) {
            case AMBULANCE -> CarEntity.AMBULANCE;
            case FIRE -> CarEntity.FIRETRUCK;
            case POLICE -> CarEntity.POLICE;
        };
        Vec3d first = route.get(0);
        CarEntity car = new CarEntity(ModContent.CAR, w);
        car.refreshPositionAndAngles(from.x, from.y, from.z, FunUtil.yawTo(from, first.squaredDistanceTo(from) < 4 && route.size() > 1 ? route.get(1) : first), 0);
        car.setVariant(variant);
        w.spawnEntity(car);
        u.car = car.getUuid();
        for (int i = 0; i < 2; i++) {
            CitizenEntity m = crew(w, c.kind, from, i);
            m.startRiding(car, true);
            u.crew.add(m.getUuid());
        }
        car.setRoute(route, c.kind == Kind.FIRE ? 0.5f : 0.56f, () -> arrived(w, u));
        UNITS.add(u);
        LAST.put(c.kind, w.getTime());
        String where = Dispatch.where(c.at);
        switch (c.kind) {
            case AMBULANCE -> Dispatch.say(w.getServer(), "Скорая, бригада 3, на выезд: " + c.what + " " + where + ".");
            case FIRE -> Dispatch.say(w.getServer(), "Пожарный расчёт 2: " + c.what + " " + where + ". Выезжайте.");
            case POLICE -> Dispatch.say(w.getServer(), "Экипаж 14, проверьте: " + c.what + " " + where + ". — Четырнадцатый принял, еду.");
        }
    }

    private static CitizenEntity crew(ServerWorld w, Kind k, Vec3d at, int i) {
        CitizenEntity m = new CitizenEntity(ModContent.CITIZEN, w);
        var r = w.getRandom();
        switch (k) {
            case POLICE -> {
                m.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.POLICE, false, i == 0 ? "Сержант " + last(Names.random(r, false)) : "Патрульный");
                m.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(ModContent.PISTOL));
            }
            case FIRE -> {
                m.setup(CitizenEntity.skinIndex("worker"), CitizenEntity.Role.CITIZEN, true, i == 0 ? "Пожарный" : "Пожарный " + last(Names.random(r, false)));
                m.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.item.Items.GOLDEN_HELMET));
            }
            default -> m.setup(CitizenEntity.skinIndex(i == 0 ? "office" : "student2"), CitizenEntity.Role.CITIZEN, true, i == 0 ? "Врач скорой" : "Фельдшер");
        }
        m.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        w.spawnEntity(m);
        return m;
    }

    private static String last(String name) {
        int i = name.lastIndexOf(' ');
        return i >= 0 ? name.substring(i + 1) : name;
    }

    private static void arrived(ServerWorld w, Unit u) {
        if (u.stage != DRIVING) return;
        u.stage = WALK_IN;
        u.stageAt = w.getTime();
        // цель уточняем: пострадавший / огонь / подозреваемый
        Vec3d spot = workSpot(w, u);
        int i = 0;
        for (UUID id : u.crew) {
            if (!(w.getEntity(id) instanceof CitizenEntity m)) continue;
            m.stopRiding();
            m.setStationary(false);
            Vec3d to = spot.add(i == 0 ? 1.2 : -1.2, 0, i == 0 ? 0.6 : -0.6);
            if (u.kind == Kind.POLICE && u.suspect != null && w.getEntity(u.suspect) instanceof LivingEntity s && s.isAlive() && s.distanceTo(m) < 48) {
                m.attack(s);
                m.say(i == 0 ? "Стоять! Полиция!" : "Лицом к стене!", 60);
            } else m.walkRoute(List.of(to), 1.0, null, m.getMainHandStack().copy(), null);
            i++;
        }
        w.playSound(null, BlockPos.ofFloored(spot), SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.NEUTRAL, 0.8f, 1.1f);
    }

    private static Vec3d workSpot(ServerWorld w, Unit u) {
        if (u.patient != null && w.getEntity(u.patient) instanceof CitizenEntity p && p.isAlive()) return p.getPos();
        if (u.kind == Kind.FIRE) {
            Vec3d best = null;
            double bd = 30 * 30;
            for (Vec3d f : com.yompix.novograd.world.FireWatch.spots(w)) {
                double d = f.squaredDistanceTo(u.target);
                if (d < bd) { bd = d; best = f; }
            }
            if (best != null) return best;
        }
        return u.target;
    }

    /** true — выезд окончен (машину убрать). */
    private static boolean step(ServerWorld w, Unit u, long now) {
        CarEntity car = u.car != null && w.getEntity(u.car) instanceof CarEntity c ? c : null;
        if (car == null || car.wrecked()) return true;
        if (now - u.started > 20 * 240) return !nearPlayer(w, car.getPos(), 48) || now - u.started > 20 * 400; // «вечный» выезд — закрываем
        u.timer++;
        List<CitizenEntity> crew = new ArrayList<>();
        for (UUID id : u.crew) if (w.getEntity(id) instanceof CitizenEntity m && m.isAlive()) crew.add(m);
        if (crew.isEmpty()) return !nearPlayer(w, car.getPos(), 40);
        switch (u.stage) {
            case DRIVING -> {
                if (now % 50 < 10) w.playSound(null, car.getBlockPos(), ModContent.SIREN, SoundCategory.NEUTRAL, 1.4f, u.kind == Kind.AMBULANCE ? 1.18f : u.kind == Kind.FIRE ? 0.82f : 1f);
                // угнали машину службы
                if (car.getControllingPassenger() instanceof ServerPlayerEntity) return true;
                // застряли надолго и никто не видит — подвозим к месту
                if (car.slow()) u.work++;
                else u.work = 0;
                if ((now - u.started > 20 * 50 || u.work > 12) && !nearPlayer(w, car.getPos(), 26) && car.slow()) {
                    car.refreshPositionAndAngles(u.target.x, u.target.y, u.target.z, car.getYaw(), 0);
                    car.setRoute(List.of(), 0.5f, null);
                    arrived(w, u);
                }
                if (!car.onRoute() && u.stage == DRIVING) arrived(w, u);
            }
            case WALK_IN -> {
                Vec3d spot = workSpot(w, u);
                boolean there = true;
                for (CitizenEntity m : crew) if (m.squaredDistanceTo(spot) > 3.2 * 3.2 && !(u.kind == Kind.FIRE && m.squaredDistanceTo(spot) < 6 * 6) && !(u.kind == Kind.POLICE && u.suspect != null)) there = false;
                if (there || now - u.stageAt > 20 * 15) {
                    u.stage = WORK;
                    u.stageAt = now;
                    u.work = 0;
                    for (CitizenEntity m : crew) {
                        if (m.squaredDistanceTo(spot) > 5 * 5 && u.kind != Kind.POLICE) m.refreshPositionAndAngles(spot.x + w.random.nextDouble() * 2 - 1, spot.y, spot.z + w.random.nextDouble() * 2 - 1, m.getYaw(), 0);
                        m.getLookControl().lookAt(spot.x, spot.y, spot.z);
                        switch (u.kind) {
                            case AMBULANCE -> m.lockActivity(CitizenEntity.Activity.RUMMAGE); // на колене у пострадавшего
                            case FIRE -> m.lockActivity(CitizenEntity.Activity.AIM);
                            case POLICE -> {
                                if (u.suspect == null) m.lockActivity(w.random.nextBoolean() ? CitizenEntity.Activity.WATCH : CitizenEntity.Activity.POINT);
                            }
                        }
                    }
                    String[] lines = switch (u.kind) {
                        case AMBULANCE -> new String[]{"Пульс есть!", "Держись, дружище", "Носилки сюда!"};
                        case FIRE -> new String[]{"Давай воду!", "Рукав сюда!", "Отойдите все!"};
                        case POLICE -> new String[]{"Что тут случилось?", "Кто-нибудь видел?", "Проходим, не толпимся"};
                    };
                    crew.get(0).say(lines[w.random.nextInt(lines.length)], 60);
                }
            }
            case WORK -> {
                u.work++;
                Vec3d spot = workSpot(w, u);
                if (u.kind == Kind.FIRE) hose(w, u, crew, spot);
                if (u.kind == Kind.AMBULANCE && u.work == 14) treat(w, u, crew);
                if (u.kind == Kind.POLICE) {
                    LivingEntity s = u.suspect != null && w.getEntity(u.suspect) instanceof LivingEntity le && le.isAlive() ? le : null;
                    if (s != null) {
                        // погоня за грабителем: пешком; далеко убежал — на машине
                        for (CitizenEntity m : crew) if (!m.chasing(s)) m.attack(s);
                        if (s.distanceTo(car) > 40 && crew.stream().allMatch(m -> m.distanceTo(s) > 30)) u.work = 0;
                        if (u.work > 90) u.suspect = null; // ушёл
                    } else if (u.suspect != null) {
                        u.suspect = null;
                        u.work = 20;
                        crew.get(0).say("Готов. Задержан.", 60);
                        Dispatch.say(w.getServer(), "Экипаж 14: подозреваемый задержан " + Dispatch.where(spot) + ". Отбой.");
                    } else if (u.work % 12 == 6) {
                        // осматриваются: шаг-другой по месту
                        CitizenEntity m = crew.get(w.random.nextInt(crew.size()));
                        m.lockActivity(null);
                        m.walkRoute(List.of(spot.add(w.random.nextInt(9) - 4, 0, w.random.nextInt(9) - 4)), 0.7, w.random.nextBoolean() ? CitizenEntity.Activity.PHONEWALK : null,
                                new ItemStack(ModContent.PISTOL), null);
                    }
                }
                int need = switch (u.kind) {
                    case AMBULANCE -> 20;  // 10 с
                    case FIRE -> 18;
                    case POLICE -> 40;
                };
                if (u.work >= need && (u.kind != Kind.POLICE || u.suspect == null)) {
                    u.stage = WALK_BACK;
                    u.stageAt = now;
                    for (CitizenEntity m : crew) {
                        m.lockActivity(null);
                        if (m.getRole() == CitizenEntity.Role.POLICE) m.standDown();
                        m.walkRoute(List.of(car.getPos()), 1.0, null, m.getMainHandStack().copy(), null);
                    }
                    if (u.kind == Kind.FIRE) {
                        Dispatch.say(w.getServer(), "Расчёт 2: пожар " + Dispatch.where(spot) + " потушен, возвращаемся.");
                        FIRE_DONE.add(u.target);
                        FIRE_DONE_AT.add(now);
                    }
                    if (u.kind == Kind.POLICE && u.suspect == null && u.what.equals("стрельба")) crew.get(0).say("Никого. Гильзы есть…", 60);
                }
            }
            case WALK_BACK -> {
                boolean all = true;
                for (CitizenEntity m : crew) if (m.squaredDistanceTo(car) > 3.5 * 3.5) all = false;
                if (all || now - u.stageAt > 20 * 12) {
                    for (CitizenEntity m : crew) {
                        m.stopRoute();
                        m.startRiding(car, true);
                    }
                    u.stage = LEAVING;
                    u.stageAt = now;
                    car.setRoute(away(w, car), 0.5f, null);
                }
            }
            case LEAVING -> {
                if (!nearPlayer(w, car.getPos(), 70) || now - u.stageAt > 20 * 45 && !nearPlayer(w, car.getPos(), 30) || !car.onRoute() && !nearPlayer(w, car.getPos(), 30)) return true;
                if (!car.onRoute()) car.setRoute(away(w, car), 0.5f, null);
            }
        }
        return false;
    }

    private static void hose(ServerWorld w, Unit u, List<CitizenEntity> crew, Vec3d spot) {
        for (CitizenEntity m : crew) {
            Vec3d from = m.getEyePos().add(0, -0.3, 0);
            Vec3d dir = spot.add(0, 0.6, 0).subtract(from);
            double len = dir.length();
            if (len < 0.5) continue;
            dir = dir.normalize();
            m.getLookControl().lookAt(spot.x, spot.y + 0.5, spot.z);
            for (double i = 1; i < Math.min(len + 1, 14); i += 1.5) {
                Vec3d pt = from.add(dir.multiply(i)).add(0, -0.01 * i * i, 0);
                w.spawnParticles(ParticleTypes.SPLASH, pt.x, pt.y, pt.z, 3, 0.12, 0.12, 0.12, 0.08);
            }
            w.spawnParticles(ParticleTypes.CLOUD, spot.x, spot.y + 1, spot.z, 2, 0.6, 0.3, 0.6, 0.02);
            com.yompix.novograd.world.Fires.douse(w, from, dir, null);
        }
        com.yompix.novograd.world.Fires.douseAt(w, spot, 5, 60);
        if (u.work % 2 == 0) w.playSound(null, BlockPos.ofFloored(spot), SoundEvents.WEATHER_RAIN, SoundCategory.NEUTRAL, 0.7f, 1.6f);
        if (u.work == 16) {
            // догоревшие машины: остаётся обгоревший остов (через минуту его увезут сами)
            for (CarEntity c : w.getEntitiesByClass(CarEntity.class, new net.minecraft.util.math.Box(spot, spot).expand(8), CarEntity::wrecked)) {
                c.paint(0x2A2522);
                w.spawnParticles(ParticleTypes.POOF, c.getX(), c.getY() + 1, c.getZ(), 30, 1, 0.6, 1, 0.05);
            }
            w.playSound(null, BlockPos.ofFloored(spot), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.2f, 1f);
        }
    }

    private static void treat(ServerWorld w, Unit u, List<CitizenEntity> crew) {
        CitizenEntity p = u.patient != null && w.getEntity(u.patient) instanceof CitizenEntity c ? c : null;
        if (p != null && p.isAlive()) {
            p.setHealth(p.getMaxHealth());
            PATIENTS.remove(p.getUuid());
            u.saved = true;
            p.say(w.random.nextBoolean() ? "Спасибо, доктор…" : "Где я?..", 70);
            crew.get(0).say("Жить будет!", 60);
            w.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1, p.getZ(), 5, 0.4, 0.3, 0.4, 0);
            Dispatch.say(w.getServer(), "Скорая, бригада 3: пострадавший стабилен, помощь оказана.");
        } else {
            crew.get(0).say("Опоздали…", 60);
            Dispatch.say(w.getServer(), "Скорая, бригада 3: на месте, пострадавший погиб. Вызовите коронера.");
        }
    }

    private static void cleanup(ServerWorld w, Unit u) {
        if (u.car != null && w.getEntity(u.car) instanceof CarEntity car) {
            boolean player = false;
            for (Entity e : car.getPassengerList()) if (e instanceof ServerPlayerEntity) player = true;
            if (!player && !car.wrecked() && car.getControllingPassenger() == null) {
                for (Entity e : new ArrayList<>(car.getPassengerList())) e.discard();
                car.discard();
            } else car.setAmbient(true);
        }
        for (UUID id : u.crew) if (w.getEntity(id) instanceof CitizenEntity m && !m.hasVehicle()) m.removeLater(200);
        if (u.patient != null) PATIENTS.remove(u.patient);
    }

    /** Перед сохранением: машины служб и экипажи не должны остаться в мире. */
    public static void onStop(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        for (Unit u : UNITS) {
            if (u.car != null && w.getEntity(u.car) instanceof CarEntity car) {
                for (Entity e : new ArrayList<>(car.getPassengerList())) {
                    e.stopRiding();
                    if (!(e instanceof ServerPlayerEntity)) e.discard();
                }
                car.discard();
            }
            for (UUID id : u.crew) if (w.getEntity(id) instanceof CitizenEntity m) m.discard();
        }
        UNITS.clear();
        PENDING.clear();
        PATIENTS.clear();
    }

    // ------------------------------------------------------------------ копы против грабителей
    /** Изредка рядом с игроком (без розыска) грабитель отнимает у прохожего сумку и бежит — патруль выезжает и ловит. */
    private static void muggings(ServerWorld w, long now) {
        if (now < nextMugging) return;
        nextMugging = now + 20L * (150 + w.random.nextInt(150));
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (Wanted.stars(p) > 0 || StreetEvents.busy(p) || !FunUtil.freeRoam(p) || p.isCreative() && !Boolean.getBoolean("novograd.autotest.city")) continue;
            if (!Island.urban(Island.zone(p.getBlockX(), p.getBlockZ()))) continue;
            if (mugging(w, p)) return;
        }
    }

    /** Сценка: грабитель и жертва на тротуаре в 25–45 блоках. */
    public static boolean mugging(ServerWorld w, ServerPlayerEntity p) {
        Vec3d at = FunUtil.curbAround(p, 25, 45);
        if (at == null) return false;
        CitizenEntity victim = FunUtil.citizen(w, at, CitizenEntity.Role.CITIZEN, null);
        victim.lockActivity(CitizenEntity.Activity.UPSET);
        victim.say("Помогите! Грабят!", 80);
        victim.removeLater(20 * 50);
        victim.setAmbient(true);
        CitizenEntity thief = FunUtil.citizen(w, at.add(1.2, 0, 0.4), CitizenEntity.Role.GANG, "Грабитель");
        thief.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(net.minecraft.item.Items.BUNDLE));
        thief.setCustomNameVisible(true);
        thief.removeWhenIdle();
        thief.removeLater(20 * 90);
        // бежит прочь от игрока по улице
        Vec3d away = at.subtract(p.getPos()).multiply(1, 0, 1);
        if (away.lengthSquared() < 1) away = new Vec3d(1, 0, 0);
        away = away.normalize();
        List<Vec3d> run = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Vec3d q = at.add(away.multiply(16 * i));
            run.add(new Vec3d(q.x, Story.topY(w, (int) Math.floor(q.x), (int) Math.floor(q.z)), q.z));
        }
        thief.walkRoute(run, 1.35, null, ItemStack.EMPTY, null);
        THIEVES.put(thief.getUuid(), victim.getUuid());
        w.playSound(null, victim.getBlockPos(), SoundEvents.ENTITY_VILLAGER_HURT, SoundCategory.NEUTRAL, 1.2f, 1.4f);
        npcCrime(w, thief, at, "уличный грабёж", 20 * 6);
        return true;
    }

    /** Грабитель → жертва: если игрок сам остановит грабителя, жертва благодарит. */
    private static final Map<UUID, UUID> THIEVES = new HashMap<>();

    /** Из CityServices (AFTER_DEATH): грабителя остановил игрок. */
    static void onDeath(LivingEntity e, DamageSource src) {
        UUID victim = THIEVES.remove(e.getUuid());
        if (victim == null || !(e.getWorld() instanceof ServerWorld w)) return;
        if (src.getAttacker() instanceof ServerPlayerEntity p) {
            Life.earn(p, 60, "остановил грабителя");
            if (w.getEntity(victim) instanceof CitizenEntity v) v.say("Спасибо! Вы мой герой!", 80);
        }
    }

    // ------------------------------------------------------------------ отладка
    public static String debug(ServerPlayerEntity p, String what) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = p.getPos().add(p.getRotationVector().multiply(1, 0, 1).normalize().multiply(12));
        switch (what) {
            case "amb" -> {
                CitizenEntity c = FunUtil.citizen(w, new Vec3d(at.x, Story.topY(w, (int) at.x, (int) at.z), at.z), CitizenEntity.Role.CITIZEN, null);
                c.setAmbient(true);
                c.knockDown(p.getPos(), null, 100);
                c.setHealth(4);
                LAST.remove(Kind.AMBULANCE);
                call(w, Kind.AMBULANCE, c.getPos(), c.getUuid(), null, "сбит пешеход", 20);
                return "Скорая вызвана к " + c.getBlockPos().toShortString();
            }
            case "fire" -> {
                com.yompix.novograd.world.Fires.ignite(w, new Vec3d(at.x, Story.topY(w, (int) at.x, (int) at.z), at.z), 90, null);
                LAST.remove(Kind.FIRE);
                call(w, Kind.FIRE, at, null, null, "возгорание", 20);
                return "Пожарные вызваны";
            }
            case "police" -> {
                LAST.remove(Kind.POLICE);
                return mugging(w, p) ? "Грабёж — патруль в пути" : "Нет тротуара рядом";
            }
            default -> {
                StringBuilder sb = new StringBuilder("Выезды: ");
                for (Unit u : UNITS) {
                    sb.append(u.kind).append(" stage ").append(u.stage).append(" work ").append(u.work);
                    if (u.car != null && w.getEntity(u.car) instanceof CarEntity c)
                        sb.append(String.format(" car %.0f,%.0f v=%.2f route=%s → %.0f,%.0f", c.getX(), c.getZ(), c.speed, c.onRoute(), u.target.x, u.target.z));
                    sb.append("; ");
                }
                sb.append(" ждут: ").append(PENDING.size());
                return sb.toString();
            }
        }
    }
}
