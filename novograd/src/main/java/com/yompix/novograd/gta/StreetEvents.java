package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.vehicle.MotorVehicle;
import com.yompix.novograd.world.Island;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Случайные события на улицах в свободной игре (не во время миссий), как в GTA:
 *  • вор вырвал сумку — догони, верни хозяйке (чистые деньги) или оставь себе (грязные, а рядом полиция — звезда);
 *  • авария — окажи помощь (вызовешь скорую) или обчисти салон;
 *  • гонщик на светофоре — мигни фарами (L), ставка; выигрыш наличными, акции «Нитро-Гаража» растут;
 *  • ограбление магазина — останови грабителей (награда хозяина) или наведи ствол на продавца и войди в долю;
 *  • пьяный водитель — останови машину, город заплатит;
 *  • автостопщик за городом — довези (иногда за ним «хвост» — звёзды и двойная оплата наличными);
 *  • засада банды, когда у тебя много грязных денег (меньше, если держишь районы — см. Territories).
 */
public final class StreetEvents {
    private StreetEvents() {}

    public enum Kind { SNATCH, CRASH, RACE, ROBBERY, DRUNK, HITCH, AMBUSH }

    private static final class Ev {
        Kind kind;
        int stage, timer, count, bet, pay;
        @Nullable UUID victim, perp, car, car2, bag;
        final List<UUID> crew = new ArrayList<>();
        @Nullable Vec3d at, dest;
        String name = "";
        boolean flag;
        int lights;
        List<Vec3d> route = List.of();
    }

    private static final Map<UUID, Ev> EV = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final Map<UUID, Long> CLERK_CD = new HashMap<>();

    public static boolean busy(ServerPlayerEntity p) {
        return EV.containsKey(p.getUuid());
    }

    // ------------------------------------------------------------------ планировщик
    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        if (t % 5 != 2) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.isDead()) continue;
            Ev e = EV.get(p.getUuid());
            if (e != null) {
                try {
                    tickEvent(p, e);
                } catch (RuntimeException ex) {
                    com.yompix.novograd.Novograd.LOG.warn("street event", ex);
                    end(p, e, 60);
                }
                continue;
            }
            if (t % 20 != 2) continue;
            long now = p.getServerWorld().getTime();
            long next = NEXT.computeIfAbsent(p.getUuid(), u -> now + 20 * 120);
            if (now < next) continue;
            if (!FunUtil.freeRoam(p) || FunUtil.busy(p) || Wanted.stars(p) > 0 || p.isCreative()) {
                NEXT.put(p.getUuid(), now + 20 * 15);
                continue;
            }
            Kind k = roll(p);
            if (k == null || !start(p, k)) NEXT.put(p.getUuid(), now + 20 * 20);
        }
    }

    /** Какое событие подходит сейчас (по месту, машине, деньгам и сюжету). */
    @Nullable
    private static Kind roll(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        Island.Zone zn = Island.zone(p.getBlockX(), p.getBlockZ());
        boolean urban = Island.urban(zn);
        boolean driving = p.getVehicle() instanceof CarEntity c && c.getControllingPassenger() == p && !(c instanceof MotorVehicle) && !c.isBike() && !c.isScooter();
        List<Kind> bag = new ArrayList<>();
        if (urban) {
            bag.add(Kind.SNATCH);
            bag.add(Kind.CRASH);
            bag.add(Kind.DRUNK);
            if (!driving) bag.add(Kind.ROBBERY);
            if (!driving) bag.add(Kind.SNATCH);
            if (driving && Economy.act(d) >= 1) { bag.add(Kind.RACE); bag.add(Kind.RACE); }
        } else if (driving && zn != Island.Zone.SEA && zn != Island.Zone.CORE) {
            bag.add(Kind.HITCH);
            bag.add(Kind.HITCH);
            bag.add(Kind.CRASH);
        }
        // засада: много грязных денег, а районов у тебя мало
        if (d.dirty >= 2500 && Economy.act(d) >= 1 && (urban || driving)) {
            int owned = Territories.ownedCount(p.getServer());
            if (p.getRandom().nextInt(1 + owned) == 0) { bag.add(Kind.AMBUSH); bag.add(Kind.AMBUSH); }
        }
        if (bag.isEmpty()) return null;
        return bag.get(p.getRandom().nextInt(bag.size()));
    }

    /** Запустить событие (для теста: /fun event …). */
    public static boolean start(ServerPlayerEntity p, Kind k) {
        if (EV.containsKey(p.getUuid())) end(p, EV.get(p.getUuid()), 0);
        Ev e = new Ev();
        e.kind = k;
        boolean ok = switch (k) {
            case SNATCH -> startSnatch(p, e);
            case CRASH -> startCrash(p, e);
            case RACE -> startRace(p, e);
            case ROBBERY -> startRobbery(p, e);
            case DRUNK -> startDrunk(p, e);
            case HITCH -> startHitch(p, e);
            case AMBUSH -> startAmbush(p, e);
        };
        if (ok) EV.put(p.getUuid(), e);
        return ok;
    }

    private static void end(ServerPlayerEntity p, Ev e, int pauseSec) {
        EV.remove(p.getUuid());
        FunUtil.clearHud(p);
        long now = p.getServerWorld().getTime();
        NEXT.put(p.getUuid(), now + 20L * (pauseSec <= 0 ? 30 : pauseSec + p.getRandom().nextInt(Math.max(1, pauseSec))));
        ServerWorld w = p.getServerWorld();
        // уборка: сумка, гонщик уезжает, лишние участники уходят
        if (e.bag != null && w.getEntity(e.bag) instanceof ItemEntity it) it.discard();
        for (UUID u : new UUID[]{e.victim, e.perp}) if (u != null && w.getEntity(u) instanceof CitizenEntity c) {
            c.lockActivity(null);
            c.removeLater(200);
        }
        for (UUID u : e.crew) if (w.getEntity(u) instanceof CitizenEntity c && !c.isDead()) c.removeLater(400);
        for (UUID u : new UUID[]{e.car, e.car2}) if (u != null && w.getEntity(u) instanceof CarEntity c) c.setAmbient(true);
    }

    public static void onDownfall(ServerPlayerEntity p) {
        Ev e = EV.get(p.getUuid());
        if (e != null) {
            if (e.kind == Kind.RACE && e.stage >= 1) FunUtil.title(p, "ГОНКА ПРОИГРАНА", "Ставка $" + e.bet + " сгорела");
            end(p, e, 120);
        }
    }

    public static void onLeave(ServerPlayerEntity p) {
        Ev e = EV.get(p.getUuid());
        if (e != null) end(p, e, 0);
        NEXT.remove(p.getUuid());
    }

    private static void tickEvent(ServerPlayerEntity p, Ev e) {
        e.timer -= 5;
        switch (e.kind) {
            case SNATCH -> tickSnatch(p, e);
            case CRASH -> tickCrash(p, e);
            case RACE -> tickRace(p, e);
            case ROBBERY -> tickRobbery(p, e);
            case DRUNK -> tickDrunk(p, e);
            case HITCH -> tickHitch(p, e);
            case AMBUSH -> tickAmbush(p, e);
        }
    }

    @Nullable
    private static CitizenEntity cit(ServerWorld w, @Nullable UUID u) {
        return u != null && w.getEntity(u) instanceof CitizenEntity c && c.isAlive() ? c : null;
    }

    @Nullable
    private static CarEntity carOf(ServerWorld w, @Nullable UUID u) {
        return u != null && w.getEntity(u) instanceof CarEntity c && !c.isRemoved() ? c : null;
    }

    private static boolean witnesses(ServerPlayerEntity p, double r) {
        return !p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(r),
                c -> c.getRole() == CitizenEntity.Role.CITIZEN && c.isAlive() && c.canSee(p)).isEmpty();
    }

    // ================================================================== 1. вор с сумкой
    private static boolean startSnatch(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = FunUtil.curbAround(p, 14, 30);
        if (at == null) return false;
        CitizenEntity v = FunUtil.citizen(w, at, CitizenEntity.Role.CITIZEN, null);
        v.setStationary(true);
        v.lockActivity(CitizenEntity.Activity.UPSET);
        v.setCustomNameVisible(true);
        Vec3d pa = at.add(1.5, 0, 0.5);
        CitizenEntity thief = FunUtil.citizen(w, pa, CitizenEntity.Role.CITIZEN, "Вор " + Names.random(w.getRandom(), false));
        thief.setCustomNameVisible(true);
        thief.setGlowing(true);
        thief.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.BUNDLE));
        e.victim = v.getUuid();
        e.perp = thief.getUuid();
        e.at = at;
        e.pay = 200 + w.getRandom().nextInt(400);
        e.timer = 20 * 75;
        runAway(w, thief, p, 1.05);
        w.playSound(null, v.getBlockPos(), SoundEvents.ENTITY_VILLAGER_HURT, SoundCategory.NEUTRAL, 1.2f, 1.4f);
        FunUtil.title(p, "ДЕРЖИ ВОРА!", "«Помогите! Сумку вырвал!» — догони вора");
        return true;
    }

    private static void runAway(ServerWorld w, CitizenEntity c, ServerPlayerEntity from, double speed) {
        Vec3d away = c.getPos().subtract(from.getPos()).multiply(1, 0, 1);
        if (away.lengthSquared() < 0.01) away = new Vec3d(1, 0, 0);
        boolean alongX = Math.abs(away.x) > Math.abs(away.z);
        double sx = Math.signum(alongX ? away.x : away.z);
        // по тротуару вдоль улицы, на 40 блоков дальше
        double tx = alongX ? c.getX() + sx * 40 : FunUtil.snap(c.getX()) + 5 * Math.signum(c.getX() - FunUtil.snap(c.getX()) + 0.01);
        double tz = alongX ? FunUtil.snap(c.getZ()) + 5 * Math.signum(c.getZ() - FunUtil.snap(c.getZ()) + 0.01) : c.getZ() + sx * 40;
        c.direct(new Vec3d(tx, Story.topY(w, (int) Math.floor(tx), (int) Math.floor(tz)), tz), speed);
    }

    private static void tickSnatch(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        CitizenEntity v = cit(w, e.victim);
        if (e.stage == 0) {
            CitizenEntity thief = cit(w, e.perp);
            Vec3d dropAt = null;
            if (thief == null) {
                var dead = e.perp == null ? null : w.getEntity(e.perp);
                dropAt = dead != null ? dead.getPos() : p.getPos();
            } else if (thief.squaredDistanceTo(p) < 2.4 * 2.4 || thief.getHealth() < thief.getMaxHealth() - 0.5f
                    || p.getVehicle() != null && thief.squaredDistanceTo(p.getVehicle()) < 3.5 * 3.5) {
                dropAt = thief.getPos();
                thief.setGlowing(false);
                thief.equipStack(net.minecraft.entity.EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                thief.panic(p.getPos());
                thief.removeLater(200);
                FunUtil.hint(p, "«Ай! Забирай, забирай!» — вор бросил сумку", Formatting.YELLOW);
            }
            if (dropAt != null) {
                ItemEntity bag = new ItemEntity(w, dropAt.x, dropAt.y + 0.5, dropAt.z, new ItemStack(Items.BUNDLE));
                bag.setPickupDelayInfinite();
                bag.setNeverDespawn();
                bag.setGlowing(true);
                bag.setCustomName(Text.literal("👜 Сумка").formatted(Formatting.GOLD));
                bag.setCustomNameVisible(true);
                w.spawnEntity(bag);
                e.bag = bag.getUuid();
                e.stage = 1;
                e.timer = 20 * 40;
                return;
            }
            if (thief == null) return;
            FunUtil.target(p, thief.getPos(), "Вор");
            FunUtil.timer(p, e.timer / 20);
            if (thief.directTo == null || thief.getNavigation().isIdle()) runAway(w, thief, p, e.timer < 20 * 55 ? 0.8 : 1.05);
            if (thief.squaredDistanceTo(p) > 90 * 90 || e.timer <= 0) {
                FunUtil.title(p, "ВОР СКРЫЛСЯ", "Сумку уже не вернуть");
                if (v != null) v.lockActivity(CitizenEntity.Activity.UPSET);
                end(p, e, 90);
            }
            return;
        }
        if (e.stage == 1) {
            ItemEntity bag = e.bag != null && w.getEntity(e.bag) instanceof ItemEntity it ? it : null;
            if (bag == null) {
                end(p, e, 60);
                return;
            }
            FunUtil.target(p, bag.getPos(), "Сумка");
            FunUtil.timer(p, e.timer / 20);
            if (p.squaredDistanceTo(bag) < 2.2 * 2.2 && !p.hasVehicle()) {
                bag.discard();
                e.bag = null;
                e.stage = 2;
                e.timer = 20 * 90;
                w.playSound(null, p.getBlockPos(), SoundEvents.ITEM_BUNDLE_INSERT, SoundCategory.PLAYERS, 1f, 1f);
                FunUtil.title(p, "СУМКА У ТЕБЯ", "Верни хозяйке — или оставь себе: просто уходи ($" + e.pay + " наличными)");
            } else if (e.timer <= 0) {
                FunUtil.hint(p, "Сумку подобрал кто-то другой…", Formatting.GRAY);
                end(p, e, 90);
            }
            return;
        }
        // stage 2: вернуть или оставить себе
        if (v == null) {
            keepBag(p, e);
            return;
        }
        FunUtil.target(p, v.getPos(), "Хозяйка сумки");
        FunUtil.timer(p, e.timer / 20);
        if (v.squaredDistanceTo(p) < 3 * 3) {
            int reward = 120 + e.pay / 4 + 40 * Economy.act(Life.get(p));
            v.lockActivity(CitizenEntity.Activity.CHEER);
            v.face(p);
            Life.earn(p, reward, "благодарность за сумку");
            FunUtil.title(p, "СУМКА ВОЗВРАЩЕНА", "«Спасибо огромное! Вот, возьмите» · +$" + reward);
            Achievements.give(p, "bag_return");
            end(p, e, 120);
        } else if (v.squaredDistanceTo(p) > 110 * 110 || e.timer <= 0) keepBag(p, e);
    }

    private static void keepBag(ServerPlayerEntity p, Ev e) {
        Economy.dirty(p, e.pay, "чужая сумка");
        FunUtil.title(p, "СУМКА ТВОЯ", "+$" + e.pay + " наличными. Хозяйка запомнила твоё лицо…");
        if (Wanted.policeNear(p, 48)) Wanted.add(p, 0, "кража", 1);
        end(p, e, 120);
    }

    // ================================================================== 2. авария
    private static boolean startCrash(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        var r = w.getRandom();
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 45 + r.nextInt(40);
            double x = p.getX() + Math.cos(a) * dist, z = p.getZ() + Math.sin(a) * dist;
            boolean alongX = r.nextBoolean();
            Vec3d at = FunUtil.road(w, x, z, alongX);
            Island.Zone zn = Island.zone((int) at.x, (int) at.z);
            if (zn == Island.Zone.SEA || !Island.road((int) Math.floor(at.x), (int) Math.floor(at.z))) continue;
            w.getChunk((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4);
            int[] vars = {0, 1, 3, 4, 7, 8};
            CarEntity c1 = FunUtil.car(w, at, (alongX ? -90 : 0) + r.nextInt(50) - 25, vars[r.nextInt(vars.length)]);
            c1.hitByBullet(32, null);
            c1.toggleLight(CarEntity.HAZARD);
            Vec3d at2 = at.add(alongX ? 3.2 : 1.5, 0, alongX ? 1.5 : 3.2);
            CarEntity c2 = FunUtil.car(w, at2, (alongX ? 0 : 90) + r.nextInt(40) - 20, vars[r.nextInt(vars.length)]);
            c2.hitByBullet(25, null);
            Vec3d lie = at.add(alongX ? 0 : -2.6, 0, alongX ? -2.6 : 0);
            CitizenEntity drv = FunUtil.citizen(w, new Vec3d(lie.x, Story.topY(w, (int) Math.floor(lie.x), (int) Math.floor(lie.z)), lie.z), CitizenEntity.Role.CITIZEN, null);
            drv.setStationary(true);
            drv.lockActivity(CitizenEntity.Activity.SLEEP);
            drv.setCustomName(Text.literal("Пострадавший"));
            drv.setCustomNameVisible(true);
            e.victim = drv.getUuid();
            e.car = c1.getUuid();
            e.car2 = c2.getUuid();
            e.at = at;
            e.timer = 20 * 150;
            e.pay = 150 + r.nextInt(350);
            Life.message(p, "Новоград-радио", "На дороге рядом с тобой авария, водитель без сознания. Помоги — или… кто узнает?");
            return true;
        }
        return false;
    }

    private static void tickCrash(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        CitizenEntity drv = cit(w, e.victim);
        CarEntity car = carOf(w, e.car);
        if (drv == null || car == null) {
            end(p, e, 90);
            return;
        }
        if (w.getTime() % 20 == 2) w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, car.getX(), car.getY() + 1.4, car.getZ(), 1, 0.3, 0.1, 0.3, 0.01);
        if (e.stage == 0) {
            FunUtil.target(p, e.at, "Авария");
            FunUtil.timer(p, e.timer / 20);
            double dd = p.squaredDistanceTo(drv), dc = p.squaredDistanceTo(car);
            if (!e.flag && dd < 14 * 14) {
                e.flag = true;
                FunUtil.hint(p, "Присядь (Shift) у пострадавшего — помочь и вызвать скорую. У машины — обчистить салон", Formatting.YELLOW);
            }
            boolean sneak = p.isSneaking() && !p.hasVehicle();
            if (sneak && dd < 2.6 * 2.6) {
                e.count += 5;
                if (e.count % 20 == 0) FunUtil.hint(p, "Первая помощь… " + "▮".repeat(e.count / 20) + "▯".repeat(Math.max(0, 4 - e.count / 20)), Formatting.GREEN);
                if (e.count >= 80) {
                    int reward = 150 + 50 * Economy.act(Life.get(p));
                    Life.earn(p, reward, "помощь при аварии");
                    FunUtil.title(p, "ПОМОЩЬ ОКАЗАНА", "Скорая уже едет · родные пострадавшего благодарят: +$" + reward);
                    Achievements.give(p, "samaritan");
                    drv.lockActivity(CitizenEntity.Activity.SIT);
                    callAmbulance(w, e.at, drv);
                    e.stage = 1;
                    e.timer = 20 * 30;
                }
            } else if (sneak && dc < 3.2 * 3.2 && dd > 2 * 2) {
                e.count += 5;
                if (e.count % 20 == 0) FunUtil.hint(p, "Шаришь по салону… " + "▮".repeat(e.count / 20) + "▯".repeat(Math.max(0, 4 - e.count / 20)), Formatting.RED);
                if (e.count >= 80) {
                    Economy.dirty(p, e.pay, "добыча из разбитой машины");
                    FunUtil.title(p, "САЛОН ОБЧИЩЕН", "+$" + e.pay + " наличными · пострадавший стонет…");
                    if (witnesses(p, 18) || Wanted.policeNear(p, 40)) Wanted.add(p, 0, "мародёрство", 2);
                    end(p, e, 120);
                }
            } else e.count = Math.max(0, e.count - 5);
            if (e.timer <= 0 || p.squaredDistanceTo(car) > 180 * 180) {
                FunUtil.hint(p, "Пострадавшего увезла скорая", Formatting.GRAY);
                end(p, e, 90);
            }
        } else if (e.timer <= 0) end(p, e, 120);
    }

    /** Белый фургон скорой с аварийкой приезжает и забирает пострадавшего. */
    private static void callAmbulance(ServerWorld w, Vec3d at, CitizenEntity patient) {
        Vec3d from = FunUtil.road(w, at.x + 60, at.z, true);
        if (!Island.road((int) Math.floor(from.x), (int) Math.floor(from.z))) from = FunUtil.road(w, at.x, at.z + 60, false);
        CarEntity van = FunUtil.car(w, from, FunUtil.yawTo(from, at), 4);
        van.paint(VehicleJobs.AMB_COLOR);
        van.toggleLight(CarEntity.HAZARD);
        van.toggleLight(CarEntity.HEADLIGHTS);
        van.setAmbient(true);
        CitizenEntity medic = FunUtil.citizen(w, from, CitizenEntity.Role.CITIZEN, "Врач скорой");
        medic.setAmbient(true);
        medic.startRiding(van, true);
        UUID pid = patient.getUuid();
        van.setRoute(List.of(at.add(3, 0, 3)), 0.5f, () -> {
            if (w.getEntity(pid) instanceof CitizenEntity pt) {
                pt.lockActivity(null);
                pt.startRiding(van, true);
            }
            van.setRoute(FunUtil.wander(w, van.getX(), van.getZ(), 6, w.getRandom()), 0.5f, null);
        });
    }

    // ================================================================== 3. гонка на светофоре
    private static boolean startRace(ServerPlayerEntity p, Ev e) {
        if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p) return false;
        ServerWorld w = p.getServerWorld();
        int gx = FunUtil.snap(p.getX()), gz = FunUtil.snap(p.getZ());
        if (Math.abs(p.getX() - gx) > 6 && Math.abs(p.getZ() - gz) > 6) return false; // не на улице
        float yaw = car.getYaw();
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        int dir = Math.abs(fx) > Math.abs(fz) ? (fx > 0 ? 0 : 2) : (fz > 0 ? 1 : 3);
        // ближайший перекрёсток впереди
        int sx = dir == 0 ? FunUtil.snap(p.getX() + 24) : dir == 2 ? FunUtil.snap(p.getX() - 24) : gx;
        int sz = dir == 1 ? FunUtil.snap(p.getZ() + 24) : dir == 3 ? FunUtil.snap(p.getZ() - 24) : gz;
        List<Vec3d> route = FunUtil.straight(w, sx, sz, dir, 9);
        if (route.size() < 5) return false;
        // соперник рядом, на соседней полосе
        double side = 3.6;
        Vec3d at = new Vec3d(p.getX() + (dir % 2 == 1 ? side * (dir == 1 ? 1 : -1) : 0), car.getY(), p.getZ() + (dir % 2 == 0 ? side * (dir == 0 ? -1 : 1) : 0));
        if (!w.isAir(net.minecraft.util.math.BlockPos.ofFloored(at.add(0, 0.5, 0)))) return false;
        var r = w.getRandom();
        CarEntity rival = FunUtil.car(w, at, yaw, r.nextBoolean() ? 2 : 9);
        int[] bright = {0xF2C032, 0xF07A12, 0x7BC639, 0xD7262C, 0x9B3FD1, 0x28E6FF};
        rival.paint(bright[r.nextInt(bright.length)]);
        LifeData d = Life.get(p);
        rival.setTune(true, Math.min(2, 1 + Economy.act(d) / 2), 0);
        rival.toggleLight(CarEntity.HEADLIGHTS);
        String nm = Names.random(r, false);
        CitizenEntity drv = FunUtil.citizen(w, at, CitizenEntity.Role.CITIZEN, "Гонщик " + nm.split(" ")[0]);
        drv.setCustomNameVisible(true);
        drv.startRiding(rival, true);
        e.car = rival.getUuid();
        e.perp = drv.getUuid();
        e.route = route;
        e.dest = route.get(route.size() - 1);
        e.bet = Math.min(2000, 150 + Economy.act(d) * 150 + (d.money > 5000 ? 300 : 0));
        e.lights = car.lights();
        e.car2 = car.getUuid();
        e.timer = 20 * 14;
        w.playSound(null, rival.getBlockPos(), ModContent.CAR_HORN, SoundCategory.NEUTRAL, 1.4f, 0.8f);
        FunUtil.title(p, "ВЫЗОВ НА ГОНКУ", "«Эй! До " + route.size() + "-го перекрёстка, ставка $" + e.bet + "?» — мигни фарами (L)");
        return true;
    }

    private static void tickRace(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        CarEntity rival = carOf(w, e.car);
        CarEntity mine = p.getVehicle() instanceof CarEntity c && c.getControllingPassenger() == p ? c : null;
        if (rival == null || rival.wrecked()) {
            if (e.stage >= 2) {
                Life.earn(p, e.bet, "ставка возвращена — соперник выбыл");
                FunUtil.title(p, "СОПЕРНИК ВЫБЫЛ", "Ставка возвращена");
            }
            end(p, e, 120);
            return;
        }
        if (e.stage == 0) { // ждём ответа: мигнуть фарами
            if (w.getTime() % 20 == 2) w.spawnParticles(ParticleTypes.SMOKE, rival.getX(), rival.getY() + 0.4, rival.getZ(), 4, 0.6, 0.05, 0.6, 0.02);
            if (mine == null || mine.squaredDistanceTo(rival) > 16 * 16 || e.timer <= 0) {
                FunUtil.hint(p, "Гонщик: «Сдрейфил? Ну и ладно»", Formatting.GRAY);
                rival.setRoute(FunUtil.wander(w, rival.getX(), rival.getZ(), 6, w.getRandom()), 0.6f, null);
                end(p, e, 120);
                return;
            }
            if ((mine.lights() & CarEntity.HEADLIGHTS) != (e.lights & CarEntity.HEADLIGHTS)) {
                if (!Life.pay(p, e.bet, "ставка на гонку")) {
                    FunUtil.hint(p, "Гонщик: «Без денег не катаемся»", Formatting.GRAY);
                    end(p, e, 120);
                    return;
                }
                e.stage = 1;
                e.timer = 20 * 4;
                FunUtil.target(p, e.dest, "Финиш");
                if (Wanted.policeNear(p, 50)) Wanted.add(p, 0, "уличная гонка", 1);
            }
            return;
        }
        if (e.stage == 1) { // отсчёт
            int s = e.timer / 20;
            if (e.timer % 20 == 0 || e.timer % 20 == 15) {
                String[] cd = {"ВПЕРЁД!", "1", "2", "3"};
                if (e.timer % 20 == 0 && s >= 0 && s < 4) {
                    FunUtil.title(p, cd[s], s == 0 ? "Гонка до финиша · ставка $" + e.bet : "");
                    p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1f, s == 0 ? 2f : 1f);
                }
            }
            if (e.timer <= 0) {
                LifeData d = Life.get(p);
                rival.setRoute(e.route, Math.min(0.92f, 0.78f + Economy.act(d) * 0.03f), null);
                e.stage = 2;
                e.timer = 20 * 90;
            }
            return;
        }
        // гонка
        FunUtil.target(p, e.dest, "Финиш");
        FunUtil.timer(p, e.timer / 20);
        if (w.getTime() % 10 == 2)
            for (int i = 0; i < 8; i++) w.spawnParticles(p, ParticleTypes.END_ROD, true, e.dest.x, e.dest.y + i * 1.5, e.dest.z, 2, 0.3, 0.3, 0.3, 0.01);
        boolean meDone = mine != null && mine.getPos().squaredDistanceTo(e.dest.x, mine.getY(), e.dest.z) < 9 * 9;
        boolean himDone = rival.getPos().squaredDistanceTo(e.dest.x, rival.getY(), e.dest.z) < 9 * 9;
        if (meDone) {
            int win = e.bet;
            Life.earn(p, e.bet, "ставка вернулась");
            Economy.dirty(p, win, "выигрыш в уличной гонке");
            FunUtil.title(p, "ПОБЕДА!", "Соперник отдаёт $" + win + " наличными");
            Achievements.give(p, "street_race");
            if (w.getRandom().nextInt(2) == 0) Economy.shock(p.getServer(), "NITR", 0.03, "уличные гонщики скупают тюнинг");
            rival.setRoute(FunUtil.wander(w, rival.getX(), rival.getZ(), 6, w.getRandom()), 0.5f, null);
            end(p, e, 150);
        } else if (himDone || e.timer <= 0) {
            FunUtil.title(p, "ГОНКА ПРОИГРАНА", "Ставка $" + e.bet + " ушла сопернику");
            rival.setRoute(FunUtil.wander(w, rival.getX(), rival.getZ(), 6, w.getRandom()), 0.6f, null);
            end(p, e, 150);
        }
    }

    // ================================================================== 4. ограбление магазина
    private static boolean startRobbery(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        CitizenEntity clerk = null;
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(70, 20, 70), c -> (c.getRole() == CitizenEntity.Role.SHOPKEEPER || c.getRole() == CitizenEntity.Role.TAILOR || c.getRole() == CitizenEntity.Role.BOUTIQUE
                || c.getRole() == CitizenEntity.Role.BARBER) && c.isAlive() && c.getStoryId().isEmpty())) {
            if (now - CLERK_CD.getOrDefault(c.getUuid(), -999999L) < 24000) continue;
            if (c.squaredDistanceTo(p) < 12 * 12) continue;
            clerk = c;
            break;
        }
        if (clerk == null) return false;
        CLERK_CD.put(clerk.getUuid(), now);
        clerk.lockActivity(CitizenEntity.Activity.UPSET);
        var r = w.getRandom();
        for (int i = 0; i < 2; i++) {
            Vec3d at = clerk.getPos().add(r.nextDouble() * 4 - 2, 0, r.nextDouble() * 4 - 2);
            CitizenEntity g = FunUtil.gangster(w, new Vec3d(at.x, clerk.getY(), at.z), i == 0 ? "Грабитель" : "Подельник", true);
            g.setStationary(true);
            g.lockActivity(CitizenEntity.Activity.AIM);
            g.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(ModContent.PISTOL));
            g.face(clerk);
            e.crew.add(g.getUuid());
        }
        e.victim = clerk.getUuid();
        e.at = clerk.getPos();
        e.timer = 20 * 70;
        e.pay = 400 + r.nextInt(500) + 150 * Economy.act(Life.get(p));
        Life.message(p, "Сканер полиции", "Вооружённое ограбление магазина рядом с тобой! Хозяин заплатит тому, кто остановит грабителей.");
        FunUtil.title(p, "ОГРАБЛЕНИЕ МАГАЗИНА", "Останови грабителей — или наведи ствол на продавца и войди в долю");
        return true;
    }

    private static void tickRobbery(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        CitizenEntity clerk = cit(w, e.victim);
        int alive = 0;
        for (UUID u : e.crew) {
            CitizenEntity g = cit(w, u);
            if (g == null) continue;
            alive++;
            if (clerk != null && !g.chasing(p) && g.getActivity() == CitizenEntity.Activity.AIM && w.getTime() % 20 == 2) g.face(clerk);
        }
        FunUtil.target(p, e.at, "Ограбление");
        FunUtil.timer(p, e.timer / 20);
        if (alive == 0) {
            int reward = e.pay / 2 + 150;
            Life.earn(p, reward, "награда от хозяина магазина");
            FunUtil.title(p, "ГРАБИТЕЛИ ОСТАНОВЛЕНЫ", "Хозяин: «Спасибо, сынок!» · +$" + reward);
            Achievements.give(p, "hero_store");
            if (clerk != null) clerk.lockActivity(null);
            end(p, e, 150);
            return;
        }
        // войти в долю: держать ствол на продавце
        if (clerk != null && Weapons.isGun(p.getMainHandStack()) && p.squaredDistanceTo(clerk) < 10 * 10 && !e.flag) {
            Vec3d to = clerk.getEyePos().subtract(p.getEyePos()).normalize();
            if (to.dotProduct(p.getRotationVec(1)) > 0.95 && p.canSee(clerk)) {
                e.count += 5;
                if (e.count == 5) FunUtil.hint(p, "Грабитель: «О, свой! Держи его на мушке, делим пополам!»", Formatting.YELLOW);
                if (e.count >= 30) {
                    e.flag = true;
                    Heists.markRobbed(clerk);
                    Economy.dirty(p, e.pay, "доля с ограбления магазина");
                    FunUtil.title(p, "КАССА НАША", "Твоя доля +$" + e.pay + " · уходим, полиция близко!");
                    Achievements.give(p, "holdup");
                    Wanted.add(p, 0, "ограбление магазина", 3);
                    clerk.lockActivity(null);
                    for (UUID u : e.crew) if (cit(w, u) instanceof CitizenEntity g) {
                        g.lockActivity(null);
                        g.setStationary(false);
                        g.panic(p.getPos());
                        runAway(w, g, p, 1.2);
                    }
                    end(p, e, 180);
                    return;
                }
            } else e.count = 0;
        }
        if (e.timer <= 0 || p.squaredDistanceTo(e.at) > 150 * 150) {
            FunUtil.title(p, "ГРАБИТЕЛИ УШЛИ", "Касса магазина пуста");
            if (clerk != null) Heists.markRobbed(clerk);
            for (UUID u : e.crew) if (cit(w, u) instanceof CitizenEntity g) {
                g.lockActivity(null);
                g.setStationary(false);
                runAway(w, g, p, 1.1);
            }
            end(p, e, 120);
        }
    }

    // ================================================================== 5. пьяный водитель
    private static boolean startDrunk(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        var r = w.getRandom();
        for (int tries = 0; tries < 10; tries++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 50 + r.nextInt(30);
            int gx = FunUtil.snap(p.getX() + Math.cos(a) * dist), gz = FunUtil.snap(p.getZ() + Math.sin(a) * dist);
            if (!Island.urban(Island.zone(gx, gz))) continue;
            List<Vec3d> base = FunUtil.wander(w, gx, gz, 14, r);
            List<Vec3d> route = new ArrayList<>();
            Vec3d prev = new Vec3d(gx + 0.5, Story.topY(w, gx, gz), gz + 0.5);
            int k = 0;
            for (Vec3d pt : base) { // виляет: середина квартала со смещением на встречку и обратно
                Vec3d mid = prev.add(pt).multiply(0.5);
                boolean alongX = Math.abs(pt.x - prev.x) > Math.abs(pt.z - prev.z);
                double off = (k++ % 2 == 0 ? 3.5 : -3.5);
                route.add(mid.add(alongX ? 0 : off, 0, alongX ? off : 0));
                route.add(pt);
                prev = pt;
            }
            Vec3d at = new Vec3d(gx + 0.5, Story.topY(w, gx, gz), gz + 0.5);
            CarEntity car = FunUtil.car(w, at, FunUtil.yawTo(at, route.get(0)), new int[]{0, 1, 7, 8, 9}[r.nextInt(5)]);
            CitizenEntity drv = FunUtil.citizen(w, at, CitizenEntity.Role.CITIZEN, "Пьяный водитель");
            drv.setCustomNameVisible(true);
            drv.startRiding(car, true);
            car.setRoute(route, 0.62f, null);
            car.toggleLight(CarEntity.HEADLIGHTS);
            e.car = car.getUuid();
            e.perp = drv.getUuid();
            e.timer = 20 * 120;
            Life.message(p, "Диспетчер полиции", "Пьяный водитель виляет по встречке рядом с тобой. Останови его — город заплатит.");
            FunUtil.title(p, "ПЬЯНЫЙ ЗА РУЛЁМ", "Останови машину, пока он кого-нибудь не сбил");
            return true;
        }
        return false;
    }

    private static void tickDrunk(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        CarEntity car = carOf(w, e.car);
        boolean stopped = car == null || car.wrecked() || car.health() < 45 || car.getPassengerList().stream().noneMatch(x -> x instanceof CitizenEntity);
        if (stopped) {
            int reward = 200 + 60 * Economy.act(Life.get(p));
            Life.earn(p, reward, "остановил пьяного водителя");
            FunUtil.title(p, "ВОДИТЕЛЬ ОСТАНОВЛЕН", "Город благодарит · +$" + reward);
            Achievements.give(p, "drunk");
            if (car != null) {
                car.setRoute(List.of(), 0, null);
                for (var ps : new ArrayList<>(car.getPassengerList())) if (ps instanceof CitizenEntity c) {
                    c.stopRiding();
                    c.lockActivity(CitizenEntity.Activity.UPSET);
                    c.removeLater(300);
                }
            }
            end(p, e, 150);
            return;
        }
        if (!car.onRoute()) car.setRoute(FunUtil.wander(w, car.getX(), car.getZ(), 8, w.getRandom()), 0.62f, null);
        if (w.getTime() % 20 == 2) w.getChunkManager().addTicket(net.minecraft.server.world.ChunkTicketType.PORTAL, car.getChunkPos(), 2, car.getBlockPos());
        if (w.getTime() % 60 == 2) w.playSound(null, car.getBlockPos(), ModContent.CAR_HORN, SoundCategory.NEUTRAL, 1.2f, 0.7f);
        FunUtil.target(p, car.getPos(), "Пьяный водитель");
        FunUtil.timer(p, e.timer / 20);
        if (e.timer <= 0 || car.squaredDistanceTo(p) > 220 * 220) {
            FunUtil.title(p, "ВОДИТЕЛЬ СКРЫЛСЯ", "Полиция разберётся сама");
            car.setAmbient(true);
            end(p, e, 120);
        }
    }

    // ================================================================== 6. автостопщик
    private static final String[] HITCH_DEST = {"village", "lake_dock", "airfield_hangar", "riv_strip", "desert_mesa", "mtn_road", "port_gate", "beach_bar", "downtown_plaza", "riv_marina", "industry_street", "farm"};
    private static final Map<String, String> HITCH_NAMES = Map.ofEntries(Map.entry("village", "деревня Сосновка"), Map.entry("lake_dock", "озеро Ясное"),
            Map.entry("airfield_hangar", "аэродром"), Map.entry("riv_strip", "Стрип Ривьеры"), Map.entry("desert_mesa", "Пустошь"), Map.entry("mtn_road", "дорога в Седые горы"),
            Map.entry("port_gate", "порт"), Map.entry("beach_bar", "пляжный бар"), Map.entry("downtown_plaza", "деловой центр"), Map.entry("riv_marina", "пристань Ривьеры"),
            Map.entry("industry_street", "промзона"), Map.entry("farm", "ферма у холмов"));

    private static boolean startHitch(ServerPlayerEntity p, Ev e) {
        if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p) return false;
        ServerWorld w = p.getServerWorld();
        Vec3d v = car.getVelocity().multiply(1, 0, 1);
        Vec3d fwd = v.lengthSquared() > 0.01 ? v.normalize() : Vec3d.fromPolar(0, car.getYaw());
        Vec3d side = new Vec3d(-fwd.z, 0, fwd.x);
        Vec3d at = car.getPos().add(fwd.multiply(55)).add(side.multiply(5));
        int ax = (int) Math.floor(at.x), az = (int) Math.floor(at.z);
        if (Island.zone(ax, az) == Island.Zone.SEA) return false;
        at = new Vec3d(at.x, Story.topY(w, ax, az), at.z);
        List<String> far = new ArrayList<>();
        for (String id : HITCH_DEST) {
            Vec3d d = Story.place(w, id);
            if (d != null && d.squaredDistanceTo(at) > 260 * 260 && d.squaredDistanceTo(at) < 1100 * 1100) far.add(id);
        }
        if (far.isEmpty()) return false;
        String dest = far.get(w.getRandom().nextInt(far.size()));
        CitizenEntity h = FunUtil.citizen(w, at, CitizenEntity.Role.CITIZEN, null);
        h.setStationary(true);
        h.lockActivity(CitizenEntity.Activity.WAVE);
        h.setCustomName(Text.literal("👍 " + h.getCustomName().getString()));
        h.setCustomNameVisible(true);
        h.face(p);
        e.victim = h.getUuid();
        e.dest = Story.place(w, dest);
        e.name = HITCH_NAMES.getOrDefault(dest, dest);
        e.flag = w.getRandom().nextInt(5) == 0; // за ним «хвост»
        e.timer = 20 * 45;
        FunUtil.hint(p, "👍 Впереди голосует автостопщик — притормози рядом, если подвезёшь", Formatting.YELLOW);
        return true;
    }

    private static void tickHitch(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        CitizenEntity h = cit(w, e.victim);
        CarEntity car = p.getVehicle() instanceof CarEntity c && c.getControllingPassenger() == p ? c : null;
        if (h == null || e.dest == null) {
            end(p, e, 90);
            return;
        }
        if (e.stage == 0) {
            FunUtil.target(p, h.getPos(), "Автостопщик");
            if (car != null && car.squaredDistanceTo(h) < 7 * 7 && car.slow()) {
                if (car.getPassengerList().size() >= 4) {
                    FunUtil.hint(p, "«Места нет? Ну ладно…»", Formatting.GRAY);
                    end(p, e, 120);
                    return;
                }
                h.lockActivity(null);
                h.setStationary(false);
                h.startRiding(car, true);
                double dist = Math.sqrt(e.dest.squaredDistanceTo(car.getPos()));
                e.stage = 1;
                e.timer = (int) (20 * (40 + dist / 7));
                e.pay = 40 + (int) (dist / 7);
                if (e.flag) {
                    FunUtil.title(p, "«ГОНИ! ЗА МНОЙ ХВОСТ!»", "Довези до места: " + e.name + " · заплачу вдвое, наличными");
                    Wanted.add(p, 0, "укрывательство беглеца", 2);
                } else
                    FunUtil.title(p, "ПОПУТЧИК", "«Подбросишь до места " + e.name + "? Заплачу $" + e.pay + "»");
            } else if (e.timer <= 0 || h.squaredDistanceTo(p) > 140 * 140) {
                end(p, e, 90);
            }
            return;
        }
        FunUtil.target(p, e.dest, e.name);
        FunUtil.timer(p, e.timer / 20);
        if (!h.hasVehicle() || car == null && e.timer % 100 == 0 && p.squaredDistanceTo(h) > 20 * 20) {
            FunUtil.hint(p, "Попутчик ушёл пешком", Formatting.GRAY);
            h.stopRiding();
            end(p, e, 120);
            return;
        }
        if (car != null && car.getPos().squaredDistanceTo(e.dest.x, car.getY(), e.dest.z) < 12 * 12 && car.slow()) {
            h.stopRiding();
            h.removeLater(200);
            if (e.flag) {
                Economy.dirty(p, e.pay * 2, "попутчик с «хвостом»");
                FunUtil.title(p, "ДОВЁЗ", "«Ты меня не видел» · +$" + e.pay * 2 + " наличными");
            } else {
                Life.earn(p, e.pay, "подвёз попутчика");
                FunUtil.title(p, "ПОПУТЧИК ДОВЕЗЁН", "«Спасибо, выручил!» · +$" + e.pay);
            }
            Achievements.give(p, "hitchhiker");
            end(p, e, 150);
        } else if (e.timer <= 0) {
            h.stopRiding();
            h.removeLater(100);
            FunUtil.title(p, "ОПОЗДАЛ", "Попутчик вышел, ругаясь");
            end(p, e, 120);
        }
    }

    // ================================================================== 7. засада банды
    private static boolean startAmbush(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        var r = w.getRandom();
        String gang = Territories.rivalName(p);
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 50 + r.nextInt(25);
            double x = p.getX() + Math.cos(a) * dist, z = p.getZ() + Math.sin(a) * dist;
            boolean alongX = r.nextBoolean();
            Vec3d at = FunUtil.road(w, x, z, alongX);
            if (!Island.road((int) Math.floor(at.x), (int) Math.floor(at.z))) continue;
            CarEntity car = FunUtil.car(w, at, FunUtil.yawTo(at, p.getPos()), r.nextBoolean() ? 7 : 8);
            car.paint(gang.startsWith("Син") ? 0x1F3A68 : 0x2A2C31);
            car.setTune(false, 1, 1);
            for (int i = 0; i < 3; i++) {
                CitizenEntity g = FunUtil.gangster(w, at, gang + " " + Names.random(r, false).split(" ")[0], i < 2 || r.nextBoolean());
                g.startRiding(car, true);
                e.crew.add(g.getUuid());
            }
            e.car = car.getUuid();
            e.timer = 20 * 120;
            e.pay = 300 + r.nextInt(500);
            e.name = gang;
            Life.message(p, "Неизвестный номер", "Слышали, у тебя карманы набиты налом. Делиться надо. Мы уже рядом.");
            FunUtil.title(p, "ЗАСАДА!", "Банда «" + gang + "» охотится за твоими грязными деньгами");
            return true;
        }
        return false;
    }

    private static void tickAmbush(ServerPlayerEntity p, Ev e) {
        ServerWorld w = p.getServerWorld();
        CarEntity car = carOf(w, e.car);
        int alive = 0;
        CitizenEntity any = null;
        for (UUID u : e.crew) {
            CitizenEntity g = cit(w, u);
            if (g == null) continue;
            alive++;
            any = g;
            if (!g.hasVehicle() && !g.chasing(p)) g.attack(p);
        }
        if (alive == 0) {
            Economy.dirty(p, e.pay, "наличные бандитов");
            FunUtil.title(p, "ЗАСАДА ОТБИТА", "У бандитов нашлось $" + e.pay);
            Achievements.give(p, "ambush");
            end(p, e, 300);
            return;
        }
        if (car != null && !car.wrecked() && car.hasNpcDriver()) {
            boolean close = car.squaredDistanceTo(p) < 14 * 14 || car.squaredDistanceTo(p) < 22 * 22 && car.slow();
            if (close) { // выходят и стреляют
                car.setRoute(List.of(), 0, null);
                for (var ps : new ArrayList<>(car.getPassengerList())) if (ps instanceof CitizenEntity g) {
                    g.stopRiding();
                    g.attack(p);
                }
            } else if (w.getTime() % 15 == 2) car.setRoute(List.of(p.getPos()), p.hasVehicle() ? 0.88f : 0.6f, null);
        } else if (car != null) for (var ps : new ArrayList<>(car.getPassengerList())) if (ps instanceof CitizenEntity g) {
            g.stopRiding();
            g.attack(p);
        }
        FunUtil.target(p, (any != null ? any : p).getPos(), "Засада");
        FunUtil.timer(p, e.timer / 20);
        double far = any == null ? 0 : any.squaredDistanceTo(p);
        if (far > 160 * 160 || e.timer <= 0) {
            FunUtil.title(p, "ОТОРВАЛСЯ", "Бандиты потеряли тебя из виду");
            for (UUID u : e.crew) if (cit(w, u) instanceof CitizenEntity g) g.standDown();
            end(p, e, 300);
        }
    }

    /** Название события для подсказок и автотеста. */
    public static String name(Kind k) {
        return switch (k) {
            case SNATCH -> "Вор с сумкой";
            case CRASH -> "Авария";
            case RACE -> "Гонка на светофоре";
            case ROBBERY -> "Ограбление магазина";
            case DRUNK -> "Пьяный водитель";
            case HITCH -> "Автостопщик";
            case AMBUSH -> "Засада банды";
        };
    }

    @SuppressWarnings("unused")
    private static String gangOf(ServerPlayerEntity p) {
        return Territories.rivalName(p);
    }

    static Set<UUID> activePlayers() {
        return EV.keySet();
    }

    static Optional<Kind> kindOf(ServerPlayerEntity p) {
        Ev e = EV.get(p.getUuid());
        return e == null ? Optional.empty() : Optional.of(e.kind);
    }
}
