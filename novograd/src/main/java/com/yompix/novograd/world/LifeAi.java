package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.entity.CitizenEntity.Activity;
import com.yompix.novograd.gta.StreetPerformers;
import com.yompix.novograd.gta.Wanted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * «Мозг» прохожих острова: лёгкий выбор дела по нуждам (голод, силы, общение, деньги, настроение) и характеру
 * (лентяй, спортсмен, тусовщик, трудоголик, осторожный). Утилитарная оценка: время суток + нужды + характер +
 * погода + что рядом. Голоден — к ларьку, в очередь; хочет общения — собирает 2–4 человека поболтать (реплики
 * над головами); устал — на скамейку; спортсмен — пробежка вокруг квартала; вечером тусовщики — в клуб;
 * нагулялся — заходит в подъезд/дом/магазин (дверь открывается) и исчезает внутри.
 * <p>
 * Реакции: смотрит на игрока, здоровается, уступает дорогу на тротуаре; ждёт у «зебры», пока проедет машина;
 * снимает на телефон сбитого; собирается у уличных музыкантов и хлопает; в дождь — зонтик или домой;
 * гудок игрока — отскакивает и ругается; игрок в розыске — показывает пальцем и звонит в полицию.
 * Драки, паника, свидетели — прежняя логика CitizenEntity: пока житель не спокоен, мозг молчит.
 * <p>
 * Производительность: думают только горожане рядом с игроками (≤ 56 блоков), раз в секунду, вперемешку
 * (половина — на чётных проходах, половина — на нечётных); сами решения — арифметика без поиска пути.
 */
public final class LifeAi {
    private LifeAi() {}

    public enum Trait { LAZY, SPORTY, PARTY, WORKAHOLIC, CAUTIOUS }

    static final class Brain {
        float hunger, energy, social, money, mood;
        Trait trait;
        long lastSay, lastReact, pendingPhone;
        String doing = "";
        @Nullable Talk talk;
    }

    private static final Map<UUID, Brain> BRAINS = new HashMap<>();
    private static final List<Talk> TALKS = new ArrayList<>();
    private static final List<Object[]> DOORS = new ArrayList<>(); // {BlockPos, закрыть в тик}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(LifeAi::tick);
    }

    // ------------------------------------------------------------------ кто чей
    /** Обычный прохожий острова, которым управляет мозг (а не сюжет, распорядок жильца или сцена). */
    static boolean candidate(CitizenEntity c) {
        if (!c.isAmbient() || c.getRole() != CitizenEntity.Role.CITIZEN || !c.getStoryId().isEmpty() || c.hasVehicle() || c.isStationary()) return false;
        if (Residents.is(c) || c.getCommandTags().contains(Leisure.TAG) || c.getCommandTags().contains(StreetPerformers.TAG)) return false;
        return Island.zone(c.getBlockX(), c.getBlockZ()) != Island.Zone.CORE;
    }

    static Brain brain(CitizenEntity c) {
        return BRAINS.computeIfAbsent(c.getUuid(), u -> {
            Brain b = new Brain();
            java.util.Random r = new java.util.Random(u.getLeastSignificantBits());
            b.hunger = r.nextFloat() * 0.7f;
            b.energy = 0.4f + r.nextFloat() * 0.6f;
            b.social = r.nextFloat() * 0.8f;
            b.money = r.nextFloat();
            b.mood = 0.3f + r.nextFloat() * 0.5f;
            b.trait = Trait.values()[r.nextInt(Trait.values().length)];
            return b;
        });
    }

    public static double hour(ServerWorld w) {
        return ((w.getTimeOfDay() % 24000) / 1000.0 + 6) % 24;
    }

    // ------------------------------------------------------------------ такт
    private record Ctx(List<ServerPlayerEntity> wanted, List<CitizenEntity> knocked, List<CitizenEntity> performers, long now) {}

    private static void tick(MinecraftServer server) {
        long t = server.getTicks();
        ServerWorld w = server.getOverworld();
        if (!DOORS.isEmpty()) closeDoors(w, w.getTime());
        if (t % 10 != 3) return;
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity p : w.getPlayers()) if (!p.isSpectator()) players.add(p);
        if (players.isEmpty()) return;
        talks(w);
        int slot = (int) ((t / 10) % 2);
        Set<Integer> seen = new HashSet<>();
        List<CitizenEntity> all = new ArrayList<>();
        List<ServerPlayerEntity> wanted = new ArrayList<>();
        List<CitizenEntity> knocked = new ArrayList<>(), performers = new ArrayList<>();
        for (ServerPlayerEntity p : players) {
            if (Wanted.stars(p) > 0) wanted.add(p);
            for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(56, 20, 56), CitizenEntity::isAlive))
                if (seen.add(c.getId())) {
                    all.add(c);
                    if (c.knockPhase() != CitizenEntity.KO_NONE) knocked.add(c);
                    else if (c.getCommandTags().contains(StreetPerformers.TAG) && c.getActivity() != Activity.IDLE) performers.add(c);
                }
        }
        Ctx ctx = new Ctx(wanted, knocked, performers, w.getTime());
        for (CitizenEntity c : all) {
            if ((c.getId() & 1) != slot) continue;
            if (c.getCommandTags().contains(Leisure.TAG)) { // сцену (рабочий, патруль, ларёк) ведёт Leisure — тут только реакции
                if (c.calm()) react(w, c, brain(c), ctx, false);
                continue;
            }
            boolean mine = c.lifeManaged || candidate(c);
            if (mine) {
                c.lifeManaged = true;
                think(w, c, ctx);
            } else if (Residents.is(c) && c.calm()) react(w, c, brain(c), ctx, false);
        }
        if (t % 400 == 3) BRAINS.keySet().removeIf(u -> w.getEntity(u) == null);
    }

    private static void think(ServerWorld w, CitizenEntity c, Ctx ctx) {
        Brain b = brain(c);
        // нужды плывут: голод и желание общаться растут, силы тают (у спортсмена на пробежке — быстрее)
        b.hunger = Math.min(1, b.hunger + 0.006f);
        b.social = Math.min(1, b.social + 0.007f);
        b.energy = Math.max(0, b.energy - (c.getActivity() == Activity.JOG ? 0.02f : 0.003f));
        if (c.getActivity() == Activity.SIT || c.getActivity() == Activity.SITCROSS || c.getActivity() == Activity.SUNBATHE) b.energy = Math.min(1, b.energy + 0.03f);
        b.mood += (0.55f - b.mood) * 0.01f + (w.isRaining() ? -0.004f : 0);
        if (!c.calm()) {
            if (b.talk != null) b.talk.end = 0;
            return;
        }
        if (b.pendingPhone > 0 && ctx.now() >= b.pendingPhone && !c.isBusy()) { // показал на преступника — звонит
            b.pendingPhone = 0;
            c.busy(Activity.PHONE, 140, null, new ItemStack(ModContent.PHONE));
            say(c, b, pick(c.getRandom(), CALL_COPS), 80, ctx.now());
            return;
        }
        if (react(w, c, b, ctx, true)) return;
        if (c.walkingRoute() || c.isBusy() || c.lifeHold > 0 || c.hasPartner() || b.talk != null) return;
        if (b.doing.equals("shopping") && leaveShop(w, c, b)) return; // выбрал — выходит с пакетом
        if (b.doing.equals("queue")) { // стоит в очереди к ларьку
            if (Leisure.inQueue(c)) {
                if (c.getRandom().nextInt(4) == 0) c.busy(c.getRandom().nextBoolean() ? Activity.WATCH : Activity.PHONE, 60, null,
                        c.getActivity() == Activity.PHONE ? new ItemStack(ModContent.PHONE) : ItemStack.EMPTY);
                return;
            }
            b.doing = "";
        }
        if (b.doing.equals("leave") && c.age > 6000 && leave(w, c, b, hour(w))) return;
        decide(w, c, b, ctx.now());
    }

    // ------------------------------------------------------------------ реакции на мир
    private static boolean react(ServerWorld w, CitizenEntity c, Brain b, Ctx ctx, boolean mine) {
        Random r = c.getRandom();
        long now = ctx.now();
        boolean fresh = now - b.lastReact > 500;
        // игрок в розыске рядом и на виду — показать пальцем и позвонить в полицию
        if (fresh && !ctx.wanted().isEmpty()) for (ServerPlayerEntity p : ctx.wanted()) {
            if (c.squaredDistanceTo(p) < 16 * 16 && r.nextInt(3) == 0 && c.canSee(p)) {
                b.lastReact = now;
                c.stopRoute();
                c.face(p);
                c.busy(Activity.POINT, 50, null, ItemStack.EMPTY);
                say(c, b, pick(r, POINT_LINES), 50, now);
                b.pendingPhone = now + 50;
                return true;
            }
        }
        // сбили человека — снимать на телефон
        if (fresh) for (CitizenEntity v : ctx.knocked()) {
            if (v != c && c.squaredDistanceTo(v) < 14 * 14 && r.nextInt(2) == 0) {
                b.lastReact = now;
                c.stopRoute();
                Vec3d away = c.getPos().subtract(v.getPos()).multiply(1, 0, 1);
                Vec3d at = away.lengthSquared() < 9 ? v.getPos().add(away.normalize().multiply(3.5)) : c.getPos();
                c.busy(Activity.PHONE, 160 + r.nextInt(80), at, new ItemStack(ModContent.PHONE));
                c.faceTo(v.getPos());
                say(c, b, pick(r, ACCIDENT_LINES), 70, now);
                return true;
            }
        }
        if (!mine) return false;
        // уличный музыкант — подойти, встать в круг и хлопать
        if (fresh && !ctx.performers().isEmpty() && (b.doing.isEmpty() || b.doing.equals("stroll")) && !c.isBusy())
            for (CitizenEntity pf : ctx.performers()) {
                if (c.squaredDistanceTo(pf) < 22 * 22 && r.nextInt(3) == 0) {
                    b.lastReact = now;
                    double a = r.nextDouble() * Math.PI * 2;
                    Vec3d spot = pf.getPos().add(Math.cos(a) * (3 + r.nextDouble()), 0, Math.sin(a) * (3 + r.nextDouble()));
                    b.doing = "show";
                    c.walkRoute(List.of(spot), 0.6, null, ItemStack.EMPTY, () -> {
                        c.faceTo(pf.getPos());
                        c.busy(r.nextInt(3) == 0 ? Activity.CHEER : Activity.CLAP, 200 + r.nextInt(300), null, ItemStack.EMPTY);
                        b.doing = "";
                    });
                    return true;
                }
            }
        // игрок рядом: уступить дорогу на тротуаре или поздороваться
        ServerPlayerEntity p = (ServerPlayerEntity) w.getClosestPlayer(c, 3.2);
        if (p != null && !p.isSpectator() && !p.hasVehicle()) {
            if (c.walkingRoute() && c.directTo != null) {
                Vec3d dir = c.directTo.subtract(c.getPos()).multiply(1, 0, 1), toP = p.getPos().subtract(c.getPos()).multiply(1, 0, 1);
                if (dir.lengthSquared() > 0.01 && toP.lengthSquared() < 2.6 * 2.6 && dir.normalize().dotProduct(toP.normalize()) > 0.6) {
                    Vec3d side = new Vec3d(-dir.z, 0, dir.x).normalize().multiply(0.28);
                    c.addVelocity(side.x, 0, side.z); // шаг в сторону
                    c.velocityModified = true;
                }
            } else if (!c.isBusy() && now - b.lastSay > 600 && r.nextInt(4) == 0) {
                c.face(p);
                say(c, b, pick(r, w.isRaining() ? RAIN_LINES : HELLO), 60, now);
                c.busy(r.nextInt(3) == 0 ? Activity.WAVE : Activity.IDLE, 40, null, ItemStack.EMPTY);
            }
        }
        return false;
    }

    /** Гудок игрока: прохожие перед машиной отскакивают и ругаются. */
    public static void honk(ServerPlayerEntity p, CarEntity car) {
        if (!(p.getWorld() instanceof ServerWorld w)) return;
        Vec3d fwd = Vec3d.fromPolar(0, car.getYaw()).normalize();
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, car.getBoundingBox().expand(9, 3, 9), x -> x.calm() && !x.isStationary() && x.getStoryId().isEmpty())) {
            Vec3d rel = c.getPos().subtract(car.getPos()).multiply(1, 0, 1);
            if (rel.lengthSquared() < 0.01 || rel.normalize().dotProduct(fwd) < 0.3) continue;
            Vec3d side = new Vec3d(-fwd.z, 0, fwd.x);
            if (side.dotProduct(rel) < 0) side = side.multiply(-1);
            c.stopRoute();
            c.setVelocity(side.x * 0.45, 0.42, side.z * 0.45); // отпрыгнул
            c.velocityModified = true;
            c.faceTo(car.getPos());
            c.busy(c.getRandom().nextBoolean() ? Activity.ARGUE : Activity.UPSET, 50, null, ItemStack.EMPTY);
            Brain b = brain(c);
            say(c, b, pick(c.getRandom(), HONK_LINES), 60, w.getTime());
        }
    }

    // ------------------------------------------------------------------ выбор дела
    private static void decide(ServerWorld w, CitizenEntity c, Brain b, long now) {
        Random r = c.getRandom();
        double h = hour(w);
        boolean rain = w.isRaining(), day = h >= 7 && h < 20, night = h >= 23 || h < 6;
        Vec3d pos = c.getPos();
        boolean beachNear = Leisure.beachSpot(w, pos, 80, r) != null && h >= 10 && h < 19 && !rain;
        float[] s = new float[11];
        s[0] = b.hunger * 1.3f + (h >= 12 && h < 14 ? 0.4f : 0);                                             // есть
        s[1] = h >= 9 && h < 20 ? 0.2f + b.money * 0.5f : 0;                                                    // магазин
        s[2] = b.social * (b.trait == Trait.PARTY ? 1.4f : 1f);                                                 // общение
        s[3] = (1 - b.energy) * (b.trait == Trait.LAZY ? 1.5f : 1f) + (rain ? -0.3f : 0);                        // скамейка
        s[4] = 0.35f + (b.trait == Trait.WORKAHOLIC ? 0.1f : 0);                                                // прогулка
        s[5] = b.trait == Trait.SPORTY && !rain && (h >= 6 && h < 10 || h >= 17 && h < 21) ? 0.95f * b.energy : 0; // пробежка
        s[6] = 0.12f + (c.age > 6000 ? 0.45f : 0) + ((h >= 7 && h < 9.5) || (h >= 18 && h < 21) ? 0.35f : 0) + (rain ? 0.6f : 0) + (night ? 0.8f : 0); // домой/на работу
        s[7] = beachNear ? 0.6f + (h >= 11 && h < 17 ? 0.5f : 0) : 0;                                           // пляж
        s[8] = (h >= 20 || h < 3) ? (b.trait == Trait.PARTY ? 1.1f : 0.3f) : 0;                                 // клуб
        s[9] = 0.3f;                                                                                             // постоять: покурить, часы, потянуться
        s[10] = day && !rain ? 0.08f : 0.02f;                                                                    // ловит такси
        int best = 4;
        float bs = -1;
        for (int i = 0; i < s.length; i++) {
            float v = s[i] + r.nextFloat() * 0.3f;
            if (s[i] > 0 && v > bs) {
                bs = v;
                best = i;
            }
        }
        boolean ok = switch (best) {
            case 0 -> eat(w, c, b);
            case 1 -> shop(w, c, b);
            case 2 -> socialize(w, c, b, now);
            case 3 -> rest(w, c, b);
            case 5 -> jog(c, b);
            case 6 -> leave(w, c, b, h);
            case 7 -> Leisure.goBeach(w, c, r);
            case 8 -> Leisure.goParty(w, c, r);
            case 9 -> idle(w, c, b);
            case 10 -> hail(c, b);
            default -> false;
        };
        if (!ok) stroll(w, c, b);
    }

    private static Activity walkAct(ServerWorld w, CitizenEntity c, Brain b) {
        if (c.getCommandTags().contains(Leisure.DOG)) return Activity.DOGWALK;
        if (w.isRaining()) return b.trait == Trait.CAUTIOUS || c.getRandom().nextBoolean() ? Activity.UMBRELLA : null;
        int k = c.getRandom().nextInt(10);
        return k < 2 || b.trait == Trait.WORKAHOLIC && k < 5 ? Activity.PHONEWALK : null;
    }

    private static ItemStack walkItem(Activity a) {
        return a == Activity.UMBRELLA ? new ItemStack(ModContent.UMBRELLA) : a == Activity.PHONEWALK ? new ItemStack(ModContent.PHONE) : ItemStack.EMPTY;
    }

    static double speed(ServerWorld w, CitizenEntity c) {
        double h = hour(w);
        double s = 0.55 + (c.getId() % 5) * 0.03;
        if (w.isRaining()) s += 0.2; // в дождь — быстрее
        if (h >= 7 && h < 9) s += 0.1; // утренний час пик — спешат
        return s;
    }

    /** Идти по тротуару к цели. */
    static void go(ServerWorld w, CitizenEntity c, Vec3d to, @Nullable Activity act, ItemStack held, @Nullable Runnable arrive) {
        Brain b = brain(c);
        if (act == null) {
            act = walkAct(w, c, b);
            held = walkItem(act);
        }
        c.walkRoute(Sidewalks.route(c.getPos(), to), speed(w, c), act, held, arrive);
    }

    private static boolean stroll(ServerWorld w, CitizenEntity c, Brain b) {
        Random r = c.getRandom();
        for (int i = 0; i < 4; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 30 + r.nextInt(40);
            Vec3d to = c.getPos().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            Island.Zone zn = Island.zone((int) to.x, (int) to.z);
            if (zn == Island.Zone.SEA || zn == Island.Zone.CORE || zn == Island.Zone.COUNTRY) continue;
            to = Sidewalks.nearestSidewalk(to);
            if (Island.zone((int) to.x, (int) to.z) == Island.Zone.SEA) continue;
            b.doing = "stroll";
            go(w, c, to, null, ItemStack.EMPTY, () -> b.doing = "");
            return true;
        }
        return false;
    }

    private static boolean jog(CitizenEntity c, Brain b) {
        if (!(c.getWorld() instanceof ServerWorld w)) return false;
        // круг вокруг квартала: к противоположному углу и обратно
        Vec3d p = c.getPos();
        int ci = Math.floorDiv(c.getBlockX() - 24, 48), cj = Math.floorDiv(c.getBlockZ() - 24, 48);
        Vec3d far = Sidewalks.nearestSidewalk(new Vec3d(24 + 48 * ci + (p.x - 24 - 48 * ci < 24 ? 42 : 6), p.y, 24 + 48 * cj + (p.z - 24 - 48 * cj < 24 ? 42 : 6)));
        if (!Island.urban(Island.zone((int) far.x, (int) far.z))) return false;
        List<Vec3d> path = new ArrayList<>(Sidewalks.route(p, far));
        List<Vec3d> back = Sidewalks.route(far, Sidewalks.nearestSidewalk(p));
        // обратно — другой стороной кольца: через «зеркальный» угол
        path.addAll(back);
        b.doing = "jog";
        c.walkRoute(path, 1.05, Activity.JOG, ItemStack.EMPTY, () -> b.doing = "");
        return true;
    }

    private static boolean idle(ServerWorld w, CitizenEntity c, Brain b) {
        Random r = c.getRandom();
        Activity[] pool = w.isRaining() ? new Activity[]{Activity.PHONE, Activity.WATCH, Activity.SHRUG}
                : new Activity[]{Activity.SMOKE, Activity.WATCH, Activity.STRETCH, Activity.PHONE, Activity.LEAN, Activity.THINK, Activity.DRINK};
        Activity a = pool[r.nextInt(pool.length)];
        if (a == Activity.LEAN && !wallBehind(w, c)) a = Activity.SMOKE;
        ItemStack held = a == Activity.PHONE ? new ItemStack(ModContent.PHONE) : a == Activity.DRINK ? new ItemStack(ModContent.COLA) : ItemStack.EMPTY;
        c.busy(a, 120 + r.nextInt(160), null, held);
        return true;
    }

    /** Стена за спиной (чтобы прислониться): повернуться спиной к ближайшей стене. */
    private static boolean wallBehind(ServerWorld w, CitizenEntity c) {
        BlockPos b = c.getBlockPos();
        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos q = b.offset(d).up();
            if (w.getBlockState(q).isFullCube(w, q)) {
                c.faceTo(Vec3d.ofCenter(b.offset(d.getOpposite())));
                return true;
            }
        }
        return false;
    }

    private static boolean hail(CitizenEntity c, Brain b) {
        if (!(c.getWorld() instanceof ServerWorld w) || !Island.urban(Island.zone(c.getBlockX(), c.getBlockZ()))) return false;
        Vec3d kerb = Sidewalks.nearestSidewalk(c.getPos());
        b.doing = "taxi";
        c.walkRoute(List.of(kerb), speed(w, c), null, ItemStack.EMPTY, () -> {
            c.busy(Activity.HAIL, 120 + c.getRandom().nextInt(100), null, ItemStack.EMPTY);
            b.doing = "leave"; // поймал (или нет) — потом уйдёт
            c.age = Math.max(c.age, 6000);
        });
        return true;
    }

    // ------------------------------------------------------------------ еда: ларёк с очередью
    private static boolean eat(ServerWorld w, CitizenEntity c, Brain b) {
        Leisure.Vendor v = Leisure.nearestVendor(w, c.getPos(), 110);
        if (v != null && Leisure.joinQueue(w, v, c)) {
            b.doing = "queue";
            return true;
        }
        // ларька нет — перекусить своим на скамейке
        if (b.hunger > 0.6f && rest(w, c, b)) {
            b.hunger = 0.1f;
            return true;
        }
        return false;
    }

    /** Купил еду у ларька: отойти и съесть (на скамейке — сидя). Зовёт Leisure. */
    static void served(ServerWorld w, CitizenEntity c, ItemStack food, boolean drink) {
        Brain b = brain(c);
        b.hunger = 0;
        b.money = Math.max(0, b.money - 0.1f);
        b.doing = "";
        Random r = c.getRandom();
        BenchSpot bench = r.nextBoolean() ? bench(w, c.getPos(), 14) : null;
        if (bench != null) {
            go(w, c, bench.at(), null, food, () -> sit(c, bench, Activity.SIT_EAT, food, 300 + r.nextInt(200)));
            return;
        }
        Vec3d to = c.getPos().add(r.nextInt(9) - 4, 0, r.nextInt(9) - 4);
        c.walkRoute(List.of(to), 0.5, null, food, () -> c.busy(drink ? Activity.DRINK : Activity.EAT, 220 + r.nextInt(160), null, food));
    }

    // ------------------------------------------------------------------ магазин: зайти, походить внутри, выйти с пакетом
    private static boolean shop(ServerWorld w, CitizenEntity c, Brain b) {
        Door d = Doors.shop(w, c.getPos(), 150);
        if (d == null) return false;
        b.doing = "shop";
        Random r = c.getRandom();
        go(w, c, d.outside(), null, ItemStack.EMPTY, () -> {
            openDoor(w, d.outside(), d.inside());
            Vec3d browse = d.inside().add(r.nextInt(7) - 3, 0, d.deep() * (2 + r.nextInt(5)));
            c.walkRoute(List.of(d.inside(), browse), 0.45, null, ItemStack.EMPTY, () -> {
                c.busy(Activity.THINK, 160 + r.nextInt(160), null, ItemStack.EMPTY); // выбирает
                c.lifeHold = 0;
                b.money = Math.max(0, b.money - 0.3f);
                b.mood = Math.min(1, b.mood + 0.2f);
                b.doing = "shopping";
                b.pendingPhone = 0;
                SHOPPERS.put(c.getUuid(), d);
            });
        });
        return true;
    }

    private static final Map<UUID, Door> SHOPPERS = new HashMap<>();

    /** Покупатель, закончивший выбирать, выходит с пакетом (проверяется в think через decide → здесь). */
    private static boolean leaveShop(ServerWorld w, CitizenEntity c, Brain b) {
        Door d = SHOPPERS.remove(c.getUuid());
        if (d == null) return false;
        ItemStack bag = new ItemStack(ModContent.SHOPPING_BAG);
        openDoor(w, d.outside(), d.inside());
        c.walkRoute(List.of(d.inside(), d.outside()), 0.55, Activity.BAGS, bag, () -> {
            b.doing = "";
            // с пакетами — домой
            Door home = Doors.home(w, c.getPos(), 120, c.getRandom());
            if (home != null) enter(w, c, home, Activity.BAGS, bag);
            else go(w, c, Sidewalks.nearestSidewalk(c.getPos().add(c.getRandom().nextInt(60) - 30, 0, c.getRandom().nextInt(60) - 30)), Activity.BAGS, bag, null);
        });
        return true;
    }

    // ------------------------------------------------------------------ домой / на работу: войти в дверь и исчезнуть внутри
    private static boolean leave(ServerWorld w, CitizenEntity c, Brain b, double h) {
        Random r = c.getRandom();
        Door d = h >= 7 && h < 10 && r.nextBoolean() ? Doors.office(w, c.getPos(), 120, r) : null;
        if (d == null) d = Doors.home(w, c.getPos(), 120, r);
        if (d == null) d = Doors.office(w, c.getPos(), 120, r);
        if (d == null) return false;
        b.doing = "leave";
        enter(w, c, d, null, ItemStack.EMPTY);
        return true;
    }

    static void enter(ServerWorld w, CitizenEntity c, Door d, @Nullable Activity act, ItemStack held) {
        go(w, c, d.outside(), act, held, () -> {
            openDoor(w, d.outside(), d.inside());
            c.walkRoute(List.of(d.inside()), 0.5, act, held, () -> c.removeLater(6)); // зашёл — и дверь закрылась
        });
    }

    /** Прохожий выходит из двери рядом с игроком (подъезд, офис, дом, магазин — с пакетом). Зовёт Streets. */
    public static boolean spawnFromDoor(ServerWorld w, ServerPlayerEntity p) {
        Random r = w.getRandom();
        List<Door> ds = new ArrayList<>(Doors.all(w, p.getPos(), 56, true));
        ds.addAll(Doors.all(w, p.getPos(), 56, false));
        Door sh = Doors.shop(w, p.getPos(), 56);
        if (sh != null) ds.add(sh);
        ds.removeIf(d -> d.outside().squaredDistanceTo(p.getPos()) < 16 * 16);
        if (ds.isEmpty()) return false;
        Door d = ds.get(r.nextInt(ds.size()));
        BlockPos ip = BlockPos.ofFloored(d.inside());
        if (!w.getBlockState(ip).getCollisionShape(w, ip).isEmpty() || !w.getBlockState(ip.up()).getCollisionShape(w, ip.up()).isEmpty()) return false;
        CitizenEntity c = Leisure.citizen(w, d.inside(), r);
        c.lifeManaged = true;
        if (!w.spawnEntity(c)) return false;
        openDoor(w, d.outside(), d.inside());
        boolean shop = d.kind().equals("shop");
        ItemStack held = shop ? new ItemStack(ModContent.SHOPPING_BAG) : ItemStack.EMPTY;
        Activity act = shop ? Activity.BAGS : null;
        c.walkRoute(List.of(d.outside()), 0.55, act, held, () -> {
            if (!shop) return;
            Vec3d to = Sidewalks.nearestSidewalk(c.getPos().add(r.nextInt(80) - 40, 0, r.nextInt(80) - 40));
            go(w, c, to, Activity.BAGS, held, null);
        });
        return true;
    }

    // ------------------------------------------------------------------ общение: кучка из 2–4 человек
    static final class Talk {
        final List<CitizenEntity> who = new ArrayList<>();
        Vec3d center;
        int turn, pair;
        long next, end;
    }

    private static boolean socialize(ServerWorld w, CitizenEntity c, Brain b, long now) {
        Random r = c.getRandom();
        List<CitizenEntity> mates = w.getEntitiesByClass(CitizenEntity.class, c.getBoundingBox().expand(12, 3, 12), o -> o != c && o.lifeManaged && o.calm()
                && !o.isBusy() && !o.walkingRoute() && o.lifeHold == 0 && !o.hasPartner() && brain(o).talk == null);
        if (mates.isEmpty()) {
            // никого — подождать, пока кто-то подойдёт: позвонить другу
            c.busy(Activity.PHONEWALK, 160, null, new ItemStack(ModContent.PHONE));
            say(c, b, pick(r, PHONE_LINES), 60, now);
            b.social *= 0.6f;
            return true;
        }
        Talk t = new Talk();
        t.who.add(c);
        for (CitizenEntity m : mates) if (t.who.size() < 2 + r.nextInt(3)) t.who.add(m);
        Vec3d center = Vec3d.ZERO;
        for (CitizenEntity m : t.who) center = center.add(m.getPos());
        t.center = center.multiply(1.0 / t.who.size());
        t.pair = r.nextInt(TALK_PAIRS.length);
        t.next = now + 40;
        t.end = now + 400 + r.nextInt(300);
        for (int i = 0; i < t.who.size(); i++) {
            CitizenEntity m = t.who.get(i);
            Brain mb = brain(m);
            mb.talk = t;
            mb.social = 0;
            double a = Math.PI * 2 * i / t.who.size() + r.nextDouble() * 0.4;
            Vec3d spot = t.center.add(Math.cos(a) * 1.2, 0, Math.sin(a) * 1.2);
            m.walkRoute(List.of(spot), 0.5, null, ItemStack.EMPTY, () -> m.faceTo(t.center));
        }
        TALKS.add(t);
        return true;
    }

    private static void talks(ServerWorld w) {
        long now = w.getTime();
        for (Iterator<Talk> it = TALKS.iterator(); it.hasNext(); ) {
            Talk t = it.next();
            t.who.removeIf(m -> m.isRemoved() || !m.calm());
            if (now > t.end || t.who.size() < 2) {
                for (CitizenEntity m : t.who) {
                    brain(m).talk = null;
                    if (m.getActivity() == Activity.TALK || m.getActivity() == Activity.LAUGH) m.busy(Activity.WAVE, 30, null, ItemStack.EMPTY);
                }
                for (CitizenEntity m : w.getEntitiesByClass(CitizenEntity.class, net.minecraft.util.math.Box.of(t.center, 30, 10, 30), x -> brain(x).talk == t)) brain(m).talk = null;
                it.remove();
                continue;
            }
            if (now < t.next) continue;
            Random r = w.getRandom();
            t.next = now + 50 + r.nextInt(30);
            CitizenEntity sp = t.who.get(t.turn % t.who.size());
            String[] pair = TALK_PAIRS[t.pair];
            String line = t.turn < pair.length ? pair[t.turn] : pick(r, FILLERS);
            t.turn++;
            for (CitizenEntity m : t.who) {
                if (m.walkingRoute()) continue;
                m.faceTo(m == sp ? t.center : sp.getPos());
                if (m == sp) m.busy(Activity.TALK, 70, null, ItemStack.EMPTY);
                else m.busy(r.nextInt(3) == 0 ? Activity.LAUGH : r.nextInt(4) == 0 ? Activity.SHRUG : Activity.IDLE, 70, null, ItemStack.EMPTY);
            }
            say(sp, brain(sp), line, 60, now);
        }
    }

    /** Встреча двух прохожих (CitizenEntity.meet): короткий обмен репликами над головами. */
    public static void chat(CitizenEntity a, CitizenEntity b, Activity act) {
        if (!(a.getWorld() instanceof ServerWorld w)) return;
        Random r = a.getRandom();
        String[] pair = switch (act) {
            case ARGUE -> ARGUE_PAIRS[r.nextInt(ARGUE_PAIRS.length)];
            case HUG, HANDSHAKE -> new String[]{"Сколько лет, сколько зим!", "Рад тебя видеть!"};
            case FIGHT -> new String[]{"Ты на кого смотришь?!", "А ну иди сюда!"};
            default -> TALK_PAIRS[r.nextInt(TALK_PAIRS.length)];
        };
        long now = w.getTime();
        say(a, brain(a), pair[0], 60, now);
        if (pair.length > 1) b.say(pair[1], 90);
    }

    // ------------------------------------------------------------------ скамейки (тротуар: ступеньки ели в 6 блоках от осевой)
    record BenchSpot(Vec3d at, float yaw) {}

    /** Свободная скамейка на тротуарах рядом (формула раскладки Island.city, проверка блока и «не занято»). */
    @Nullable
    static BenchSpot bench(ServerWorld w, Vec3d p, int r) {
        BenchSpot best = null;
        double bd = r * r;
        int X = (int) (Math.round((p.x - 24) / 48.0) * 48 + 24), Z = (int) (Math.round((p.z - 24) / 48.0) * 48 + 24);
        for (int side = -1; side <= 1; side += 2)
            for (int k = -2; k <= 2; k++) {
                // вдоль улицы по z (x = X ± 6) и по x (z = Z ± 6)
                int z = (int) (Math.floor(p.z / 16) * 16) + 12 + k * 16, x = (int) (Math.floor(p.x / 16) * 16) + 12 + k * 16;
                for (int dz = 0; dz <= 1; dz++) {
                    best = benchAt(w, new BlockPos(X + side * 6, Island.G + 1, z + dz), p, best, bd);
                    if (best != null) bd = best.at().squaredDistanceTo(p);
                    best = benchAt(w, new BlockPos(x + dz, Island.G + 1, Z + side * 6), p, best, bd);
                    if (best != null) bd = best.at().squaredDistanceTo(p);
                }
            }
        return best;
    }

    @Nullable
    private static BenchSpot benchAt(ServerWorld w, BlockPos b, Vec3d p, @Nullable BenchSpot best, double bd) {
        Vec3d at = Vec3d.ofBottomCenter(b);
        if (at.squaredDistanceTo(p) >= bd || !w.isChunkLoaded(b)) return best;
        BlockState s = w.getBlockState(b);
        if (!(s.getBlock() instanceof StairsBlock) || s.get(StairsBlock.HALF) != BlockHalf.BOTTOM) return best;
        if (!w.getEntitiesByClass(CitizenEntity.class, new net.minecraft.util.math.Box(b).expand(0.3, 1, 0.3), e -> true).isEmpty()) return best;
        Direction f = s.get(StairsBlock.FACING); // спинка с этой стороны — сидим лицом в обратную
        Direction look = f.getOpposite();
        float yaw = (float) Math.toDegrees(Math.atan2(-look.getOffsetX(), look.getOffsetZ()));
        return new BenchSpot(at, yaw);
    }

    static void sit(CitizenEntity c, BenchSpot b, Activity a, ItemStack held, int ticks) {
        c.refreshPositionAndAngles(b.at().x, b.at().y, b.at().z, b.yaw(), 0);
        c.setBodyYaw(b.yaw());
        c.setHeadYaw(b.yaw());
        c.busy(a, ticks, b.at(), held);
    }

    private static boolean rest(ServerWorld w, CitizenEntity c, Brain b) {
        BenchSpot bench = bench(w, c.getPos(), 40);
        if (bench == null) return false;
        Random r = c.getRandom();
        b.doing = "bench";
        Activity a = b.hunger > 0.6f ? Activity.SIT_EAT : r.nextBoolean() ? Activity.SITCROSS : Activity.SIT;
        ItemStack held = a == Activity.SIT_EAT ? new ItemStack(ModContent.BURGER) : r.nextInt(3) == 0 ? new ItemStack(ModContent.PHONE) : ItemStack.EMPTY;
        go(w, c, bench.at(), null, ItemStack.EMPTY, () -> {
            sit(c, bench, a, held, 400 + r.nextInt(500));
            b.doing = "";
        });
        return true;
    }

    // ------------------------------------------------------------------ двери
    /** Вход: снаружи (тротуар у двери), внутри (за дверью), deep — куда «вглубь» по z (+1/−1). */
    public record Door(Vec3d outside, Vec3d inside, int deep, String kind) {}

    static void openDoor(ServerWorld w, Vec3d outside, Vec3d inside) {
        Vec3d mid = outside.add(inside).multiply(0.5);
        BlockPos c = BlockPos.ofFloored(mid);
        for (BlockPos b : BlockPos.iterate(c.add(-2, -1, -2), c.add(2, 1, 2))) {
            BlockState s = w.getBlockState(b);
            if (s.getBlock() instanceof DoorBlock door && !s.get(DoorBlock.OPEN) && s.get(DoorBlock.HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER) {
                door.setOpen(null, w, s, b, true);
                DOORS.add(new Object[]{b.toImmutable(), w.getTime() + 40});
            }
        }
    }

    private static void closeDoors(ServerWorld w, long now) {
        for (Iterator<Object[]> it = DOORS.iterator(); it.hasNext(); ) {
            Object[] d = it.next();
            if (now < (long) d[1]) continue;
            it.remove();
            BlockPos b = (BlockPos) d[0];
            BlockState s = w.getBlockState(b);
            if (s.getBlock() instanceof DoorBlock door && s.get(DoorBlock.OPEN)) door.setOpen(null, w, s, b, false);
        }
    }

    // ------------------------------------------------------------------ реплики
    private static void say(CitizenEntity c, Brain b, String line, int ticks, long now) {
        c.say(line, ticks);
        b.lastSay = now;
    }

    static String pick(Random r, String[] a) {
        return a[r.nextInt(a.length)];
    }

    private static final String[] HELLO = {"Привет!", "Добрый день!", "Хорошая погодка, да?", "Мы знакомы?", "Осторожнее, тут люди ходят!",
            "Классный прикид!", "Ты не местный, да?", "Хорошего дня!", "Опять ты…", "Не подскажешь, который час?"};
    private static final String[] RAIN_LINES = {"Ну вот, опять дождь…", "Где мой зонтик?!", "Бегом под крышу!", "Промок до нитки…"};
    private static final String[] HONK_LINES = {"Эй! Куда прёшь?!", "Смотри на дорогу!", "Совсем с ума сошёл?!", "Ай! Чуть не задавил!", "Я тебя запомнил!"};
    private static final String[] POINT_LINES = {"Это он! Его все ищут!", "Смотрите, преступник!", "Это же тот самый из новостей!"};
    private static final String[] CALL_COPS = {"Алло, полиция? Он здесь!", "Алло! Тут преступник, приезжайте!", "Да-да, прямо на моей улице…"};
    private static final String[] ACCIDENT_LINES = {"Скорую! Человека сбили!", "Снимаю, это в новости надо!", "Ужас… Вы видели?", "Кто-нибудь, помогите ему!"};
    private static final String[] PHONE_LINES = {"Алло, ты где? Я жду.", "Мам, я перезвоню.", "Да, буду через пять минут.", "Ну что, встречаемся?"};
    private static final String[] FILLERS = {"Ха-ха, точно!", "Да ладно?!", "Ну ты даёшь!", "Согласен.", "Вот-вот!", "Серьёзно?", "Не может быть!",
            "А я тебе говорил!", "Ну и дела…", "Короче, ладно."};
    static final String[][] TALK_PAIRS = {
            {"Видел новости? Опять погоня на мосту!", "Да этот город с ума сошёл."},
            {"Пойдём в выходные на пляж?", "Только если без дождя!"},
            {"Говорят, в «Неоне» сегодня новый диджей.", "Я туда больше ни ногой."},
            {"Цены на бензин опять подняли…", "Пересяду на автобус. Или на угнанную!"},
            {"Ты слышал про ограбление банка?", "Тише ты! Тут везде уши."},
            {"Мой шеф опять задержал всех до ночи.", "Увольняйся и иди в казино!"},
            {"На Ривьере открыли новый отель.", "Нам туда только на экскурсию."},
            {"Как дела на работе?", "Да как обычно — аврал."},
            {"Купил себе новые кроссовки!", "В «Шмот-Базаре»? Там сейчас скидки."},
            {"Ты в зал ходишь?", "Хожу… мимо."},
            {"Опять пробки на кольце.", "Я теперь пешком — и полезно, и быстрее."},
            {"Видел, какой закат вчера был?", "Да, с пляжа — просто огонь."},
            {"Голодный как волк.", "Пошли в бургерную?"},
            {"Слышал, Ящеры опять шумят в промзоне.", "Держись оттуда подальше."},
    };
    private static final String[][] ARGUE_PAIRS = {
            {"Ты мне двадцатку должен!", "Отдам! В пятницу!"},
            {"Это моё место на парковке!", "Где написано?!"},
            {"Ты опять опоздал!", "Пробки, я-то тут при чём?"},
    };

    // ------------------------------------------------------------------ для автотеста
    public static Map<String, Integer> stats(ServerWorld w, Vec3d at, double r) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, net.minecraft.util.math.Box.of(at, r * 2, 60, r * 2), CitizenEntity::isAlive)) {
            String k = c.getActivity().name() + (c.walkingRoute() ? "+walk" : "");
            out.merge(k, 1, Integer::sum);
        }
        return out;
    }

    public static String doing(CitizenEntity c) {
        Brain b = BRAINS.get(c.getUuid());
        return b == null ? "-" : b.trait + "/" + b.doing;
    }

}
