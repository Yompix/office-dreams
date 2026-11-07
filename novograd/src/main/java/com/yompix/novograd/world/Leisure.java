package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.BallEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.entity.CitizenEntity.Activity;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Отдых и «сцены» города рядом с игроком.
 * <ul>
 *   <li>Пляжи Новограда и Ривьеры днём (10–19, без дождя): загорают на песке, сидят под зонтиками, плавают
 *   и возвращаются, играют в мяч, гуляют вдоль воды, берут напитки и мороженое в очереди у ларька.</li>
 *   <li>Ларьки с очередью: хот-доги на площади и на «Стрипе», напитки и мороженое на пляжах.</li>
 *   <li>Собачники (собака идёт рядом, поводок в руке), рабочие на стройках (молоток, носят коробки),
 *   грузчики в порту, патруль полиции вокруг квартала, дворник с метлой, бездомный у урны.</li>
 *   <li>Вечер: очередь в клуб «Неон», танцы на «Стрипе» Ривьеры.</li>
 * </ul>
 * Все — обычные прохожие (ambient: исчезают, когда игрок далеко) и входят в общий бюджет людей Streets.
 */
public final class Leisure {
    private Leisure() {}

    /** Сцена (рабочий, патруль, ларёк…) — ведёт Leisure, а не LifeAi. */
    public static final String TAG = "nvg_leisure";
    /** Хозяин собаки (прохожий LifeAi, ходит с поводком). */
    public static final String DOG = "nvg_dogowner";
    private static final String PET = "nvg_pet", BALL = "nvg_lifeball";

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Leisure::tick);
    }

    /** Слабый компьютер (мало памяти Java) — людей меньше (~60%). */
    public static double scale() {
        return Runtime.getRuntime().maxMemory() < 3_500_000_000L ? 0.6 : 1.0;
    }

    // ------------------------------------------------------------------ общий спавн прохожего
    static CitizenEntity citizen(ServerWorld w, Vec3d at, Random r) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        int skin = CitizenEntity.randomSkin(r);
        c.setup(skin, CitizenEntity.Role.CITIZEN, false, Names.random(r, CitizenEntity.isSlim(skin)));
        c.refreshPositionAndAngles(at.x, at.y, at.z, r.nextFloat() * 360, 0);
        c.randomizeBody(r, false);
        c.setAmbient(true);
        return c;
    }

    // ================================================================== пляж
    /** Свободное место на песке в радиусе r (случайные пробы, загруженные чанки). */
    @Nullable
    public static Vec3d beachSpot(ServerWorld w, Vec3d p, double r, Random rnd) {
        for (int i = 0; i < 14; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
            int x = (int) Math.floor(p.x + Math.cos(a) * d), z = (int) Math.floor(p.z + Math.sin(a) * d);
            if (Island.zone(x, z) != Island.Zone.BEACH) continue;
            int s = Island.surface(x, z);
            BlockPos b = new BlockPos(x, s, z);
            if (!w.isChunkLoaded(b)) continue;
            BlockState g = w.getBlockState(b);
            if (!g.isOf(Blocks.SAND) && !g.isOf(Blocks.SANDSTONE)) continue;
            if (!w.getBlockState(b.up()).isAir() || !w.getBlockState(b.up(2)).isAir()) continue;
            return new Vec3d(x + 0.5, s + 1, z + 0.5);
        }
        return null;
    }

    /** Пойти на пляж (прохожий LifeAi). */
    static boolean goBeach(ServerWorld w, CitizenEntity c, Random r) {
        Vec3d spot = beachSpot(w, c.getPos(), 80, r);
        if (spot == null) return false;
        LifeAi.go(w, c, spot, null, ItemStack.EMPTY, () -> beachActivity(w, c));
        return true;
    }

    /** Что делать на пляже. */
    static void beachActivity(ServerWorld w, CitizenEntity c) {
        Random r = c.getRandom();
        int k = r.nextInt(100);
        if (k < 34) { // загорает
            c.setYaw(r.nextFloat() * 360);
            c.setBodyYaw(c.getYaw());
            c.busy(Activity.SUNBATHE, 900 + r.nextInt(1400), null, ItemStack.EMPTY);
        } else if (k < 52) { // сидит под зонтиком (или просто на песке) — с напитком или телефоном
            Vec3d u = umbrella(w, c.getPos());
            Vec3d at = u != null ? u : c.getPos();
            ItemStack held = r.nextBoolean() ? new ItemStack(ModContent.COLA) : r.nextBoolean() ? new ItemStack(ModContent.PHONE) : ItemStack.EMPTY;
            c.walkRoute(List.of(at), 0.5, null, ItemStack.EMPTY, () -> c.busy(r.nextBoolean() ? Activity.SITCROSS : Activity.SIT, 700 + r.nextInt(900), null, held));
        } else if (k < 72) { // купается
            Vec3d[] sw = water(w, c.getPos(), r);
            if (sw == null) {
                c.busy(Activity.STRETCH, 100, null, ItemStack.EMPTY);
                return;
            }
            c.walkRoute(List.of(sw[0]), 0.6, null, ItemStack.EMPTY, () -> c.busy(Activity.SWIM, 400 + r.nextInt(400), sw[1], ItemStack.EMPTY));
        } else if (k < 84) { // мяч
            if (!volley(w, c, r)) c.busy(Activity.STRETCH, 120, null, ItemStack.EMPTY);
        } else if (k < 93) { // гуляет у воды
            Vec3d[] sw = water(w, c.getPos(), r);
            Vec3d to = sw != null ? sw[0].add(0, 0, r.nextInt(50) - 25) : c.getPos().add(0, 0, r.nextInt(40) - 20);
            Vec3d spot = beachSpot(w, to, 4, r);
            c.walkRoute(List.of(spot != null ? spot : to), 0.45, null, ItemStack.EMPTY, null);
        } else { // за напитком
            Vendor v = nearestVendor(w, c.getPos(), 60);
            if (v == null || !joinQueue(w, v, c)) c.busy(Activity.DRINK, 200, null, new ItemStack(ModContent.COLA));
        }
    }

    /** Зонтик (столб забора с шерстью сверху) рядом: место в тени возле столба. */
    @Nullable
    private static Vec3d umbrella(ServerWorld w, Vec3d p) {
        BlockPos c = BlockPos.ofFloored(p);
        for (BlockPos b : BlockPos.iterate(c.add(-10, 0, -10), c.add(10, 1, 10))) {
            if (!w.getBlockState(b).isOf(Blocks.OAK_FENCE) || !w.getBlockState(b.up(2)).isIn(net.minecraft.registry.tag.BlockTags.WOOL)) continue;
            int dx = w.getRandom().nextBoolean() ? 1 : -1;
            BlockPos s = b.add(dx, 0, w.getRandom().nextInt(3) - 1);
            if (w.getBlockState(s).isAir()) return Vec3d.ofBottomCenter(s);
        }
        return null;
    }

    /** {край воды на песке, точка в воде в 3–7 блоках от берега}. */
    @Nullable
    private static Vec3d[] water(ServerWorld w, Vec3d p, Random r) {
        int x0 = (int) Math.floor(p.x), z = (int) Math.floor(p.z);
        for (int dir = -1; dir <= 1; dir += 2)
            for (int i = 1; i < 50; i++) {
                int x = x0 + dir * i;
                if (Island.zone(x, z) != Island.Zone.SEA) continue;
                BlockPos wpos = new BlockPos(x, Island.G - 1, z);
                if (!w.isChunkLoaded(wpos) || !w.getBlockState(wpos).isOf(Blocks.WATER)) break;
                int out = 3 + r.nextInt(5);
                return new Vec3d[]{new Vec3d(x - dir * 1.5 + 0.5, Island.G + 1, z + 0.5), new Vec3d(x + dir * out + 0.5, Island.G - 1, z + 0.5 + r.nextInt(7) - 3)};
            }
        return null;
    }

    /** Пляжников рядом с игроком меньше нормы — привести ещё одного (в стороне от взгляда, на песке). */
    public static boolean spawnBeach(ServerWorld w, ServerPlayerEntity p) {
        double h = LifeAi.hour(w);
        if (h < 9.5 || h >= 19.5 || w.isRaining()) return false;
        Random r = w.getRandom();
        int want = (int) Math.round((h >= 11 && h < 17 ? 16 : 9) * scale());
        long have = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(60, 20, 60),
                c -> c.isAmbient() && Island.zone(c.getBlockX(), c.getBlockZ()) == Island.Zone.BEACH || Island.zone(c.getBlockX(), c.getBlockZ()) == Island.Zone.SEA && c.isAmbient()).size();
        if (have >= want) return false;
        if (have < want - 5 && r.nextBoolean()) spawnBeachOne(w, p, r); // пляж пустой — наполняется быстрее
        return spawnBeachOne(w, p, r);
    }

    private static boolean spawnBeachOne(ServerWorld w, ServerPlayerEntity p, Random r) {
        for (int i = 0; i < 6; i++) {
            Vec3d at = beachSpot(w, p.getPos(), 55, r);
            if (at == null || at.squaredDistanceTo(p.getPos()) < 14 * 14) continue;
            if (!Sidewalks.unseen(w, at.add(0, 1, 0)) && at.squaredDistanceTo(p.getPos()) < 40 * 40 && i < 5) continue;
            CitizenEntity c = citizen(w, at, r);
            c.lifeManaged = true;
            if (!w.spawnEntity(c)) return false;
            beachActivity(w, c);
            return true;
        }
        return false;
    }

    // ---- пляжный мяч
    private static final class Volley {
        CitizenEntity a, b;
        BallEntity ball;
        Vec3d sa, sb;
        long until, lastHit;
        int cool;
    }

    private static final List<Volley> VOLLEYS = new ArrayList<>();

    private static boolean volley(ServerWorld w, CitizenEntity c, Random r) {
        if (VOLLEYS.size() >= 3) return false;
        CitizenEntity mate = w.getEntitiesByClass(CitizenEntity.class, c.getBoundingBox().expand(14, 3, 14), o -> o != c && o.lifeManaged && o.calm()
                && !o.walkingRoute() && o.lifeHold == 0 && Island.zone(o.getBlockX(), o.getBlockZ()) == Island.Zone.BEACH).stream().findAny().orElse(null);
        if (mate == null) return false;
        Volley v = new Volley();
        v.a = c;
        v.b = mate;
        Vec3d mid = c.getPos().add(mate.getPos()).multiply(0.5);
        v.sa = mid.add(0, 0, -3.5);
        v.sb = mid.add(0, 0, 3.5);
        v.ball = new BallEntity(ModContent.BALL, w);
        v.ball.addCommandTag(BALL);
        v.ball.refreshPositionAndAngles(v.sa.x, v.sa.y + 1.5, v.sa.z, 0, 0);
        if (!w.spawnEntity(v.ball)) return false;
        v.until = w.getTime() + 900 + r.nextInt(900);
        v.lastHit = w.getTime();
        for (CitizenEntity x : new CitizenEntity[]{c, mate}) {
            x.stopRoute();
            x.busy(Activity.IDLE, 1, null, ItemStack.EMPTY);
            x.lifeHold = 100000;
        }
        VOLLEYS.add(v);
        return true;
    }

    private static void volleys(ServerWorld w) {
        long now = w.getTime();
        for (Iterator<Volley> it = VOLLEYS.iterator(); it.hasNext(); ) {
            Volley v = it.next();
            boolean over = now > v.until || v.a.isRemoved() || v.b.isRemoved() || !v.a.calm() || !v.b.calm() || v.ball.isRemoved();
            if (over) {
                v.ball.discard();
                for (CitizenEntity x : new CitizenEntity[]{v.a, v.b}) if (x.lifeHold > 0) x.lifeHold = 0;
                if (!v.a.isRemoved() && v.a.calm()) v.a.busy(Activity.CHEER, 40, null, ItemStack.EMPTY);
                it.remove();
                continue;
            }
            if (v.cool > 0) v.cool--;
            if (now - v.lastHit > 160) { // мяч укатился — подобрать и подать снова
                v.ball.refreshPositionAndAngles(v.a.getX(), v.a.getY() + 1.6, v.a.getZ(), 0, 0);
                v.ball.setVelocity(Vec3d.ZERO);
                v.lastHit = now;
            }
            Vec3d bp = v.ball.getPos();
            boolean sideA = bp.squaredDistanceTo(v.sa) < bp.squaredDistanceTo(v.sb);
            for (int k = 0; k < 2; k++) {
                CitizenEntity x = k == 0 ? v.a : v.b;
                Vec3d home = k == 0 ? v.sa : v.sb, other = k == 0 ? v.sb : v.sa;
                boolean mine = k == 0 == sideA;
                x.getLookControl().lookAt(v.ball, 30, 30);
                double dh = Math.hypot(bp.x - x.getX(), bp.z - x.getZ());
                if (mine && dh < 1.7 && bp.y - x.getY() < 2.6 && v.cool == 0) {
                    Vec3d dir = other.subtract(bp);
                    v.ball.kick(dir, Math.min(0.42, dir.horizontalLength() * 0.055 + 0.08), 0.5 + w.getRandom().nextDouble() * 0.12);
                    x.triggerAnim("action", "highfive");
                    v.cool = 8;
                    v.lastHit = now;
                    if (w.getRandom().nextInt(5) == 0) x.say(w.getRandom().nextBoolean() ? "Лови!" : "Пас!", 30);
                } else if (mine && now % 4 == 0) x.getNavigation().startMovingTo(bp.x, bp.y, bp.z, 1.0);
                else if (!mine && now % 10 == 0 && x.squaredDistanceTo(home) > 2) x.getNavigation().startMovingTo(home.x, home.y, home.z, 0.7);
            }
        }
    }

    // ================================================================== ларьки с очередью
    public static final class Vendor {
        final String place, label;
        final double dx, dz;
        final Item item;
        final boolean drink;
        @Nullable Vec3d pos, dir;
        @Nullable UUID uuid;
        final List<UUID> queue = new ArrayList<>();
        long serveAt;

        Vendor(String place, double dx, double dz, String label, Item item, boolean drink) {
            this.place = place;
            this.dx = dx;
            this.dz = dz;
            this.label = label;
            this.item = item;
            this.drink = drink;
        }

        Vec3d spot(int k) {
            return pos.add(dir.multiply(1.6 + k * 1.1));
        }
    }

    private static final Vendor[] VENDORS = {
            new Vendor("downtown_plaza", -6, -8, "Хот-доги", ModContent.BURGER, false),
            new Vendor("riv_strip", 10, -6, "Хот-доги", ModContent.BURGER, false),
            new Vendor("beach_bar", 6, -10, "Напитки", ModContent.COLA, true),
            new Vendor("beach_lot", 5, 12, "Мороженое", Items.MELON_SLICE, false),
            new Vendor("riv_beach", 10, 12, "Коктейли", ModContent.COLA, true),
            new Vendor("riv_marina", 8, -8, "Мороженое", Items.MELON_SLICE, false),
    };

    @Nullable
    private static Vec3d[] vendorPos(ServerWorld w, Vendor v) {
        Vec3d base = Story.place(w, v.place);
        if (base == null) return null;
        int x = (int) Math.floor(base.x + v.dx), z = (int) Math.floor(base.z + v.dz);
        Island.Zone zn = Island.zone(x, z);
        Vec3d dir = new Vec3d(1, 0, 0);
        if (Island.urban(zn)) { // на тротуар, очередь — вдоль тротуара
            int gx = Island.gridDist(x), gz = Island.gridDist(z);
            if (gx <= gz) {
                int line = (int) (Math.round((x - 24) / 48.0) * 48 + 24);
                x = line + (x >= line ? 6 : -6);
                dir = new Vec3d(0, 0, 1);
            } else {
                int line = (int) (Math.round((z - 24) / 48.0) * 48 + 24);
                z = line + (z >= line ? 6 : -6);
            }
        }
        return new Vec3d[]{new Vec3d(x + 0.5, Island.surface(x, z) + 1, z + 0.5), dir};
    }

    @Nullable
    static Vendor nearestVendor(ServerWorld w, Vec3d p, double r) {
        Vendor best = null;
        double bd = r * r;
        for (Vendor v : VENDORS) {
            if (v.uuid == null || v.pos == null || !(w.getEntity(v.uuid) instanceof CitizenEntity)) continue;
            double d = v.pos.squaredDistanceTo(p);
            if (d < bd && v.queue.size() < 5) {
                bd = d;
                best = v;
            }
        }
        return best;
    }

    static boolean joinQueue(ServerWorld w, Vendor v, CitizenEntity c) {
        if (v.queue.contains(c.getUuid()) || v.queue.size() >= 5) return false;
        v.queue.add(c.getUuid());
        int k = v.queue.size() - 1;
        Vec3d spot = v.spot(k);
        Vec3d at = v.pos;
        LifeAi.go(w, c, spot, null, ItemStack.EMPTY, () -> c.faceTo(at));
        return true;
    }

    static boolean inQueue(CitizenEntity c) {
        for (Vendor v : VENDORS) if (v.queue.contains(c.getUuid())) return true;
        return false;
    }

    private static void vendors(ServerWorld w, List<ServerPlayerEntity> players) {
        double h = LifeAi.hour(w);
        boolean open = h >= 9 && h < 22;
        long now = w.getTime();
        for (Vendor v : VENDORS) {
            CitizenEntity seller = v.uuid == null ? null : w.getEntity(v.uuid) instanceof CitizenEntity c && !c.isRemoved() ? c : null;
            if (v.pos == null) {
                Vec3d[] pd = vendorPos(w, v);
                if (pd == null) continue;
                v.pos = pd[0];
                v.dir = pd[1];
            }
            boolean near = Sidewalks.playerNear(w, v.pos, 70);
            if (seller == null || !open) {
                v.queue.clear();
                if (seller != null && !open && Sidewalks.unseen(seller)) seller.discard();
                if (seller == null && open && near && w.isChunkLoaded(BlockPos.ofFloored(v.pos))) {
                    CitizenEntity c = citizen(w, v.pos, w.getRandom());
                    c.setup(CitizenEntity.randomSkin(w.getRandom()), CitizenEntity.Role.CITIZEN, true, "Ларёк «" + v.label + "»");
                    c.setCustomNameVisible(true);
                    c.addCommandTag(TAG);
                    c.refreshPositionAndAngles(v.pos.x, v.pos.y, v.pos.z, 0, 0);
                    if (w.spawnEntity(c)) {
                        c.setStationary(true);
                        c.faceTo(v.spot(0));
                        c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(v.item));
                        v.uuid = c.getUuid();
                    }
                }
                continue;
            }
            // очередь: ушедших и испуганных убрать; первого — обслужить; остальные подтягиваются
            boolean moved = v.queue.removeIf(u -> !(w.getEntity(u) instanceof CitizenEntity c) || !c.calm() || c.squaredDistanceTo(v.pos) > 40 * 40);
            if (v.queue.isEmpty()) {
                v.serveAt = 0;
                continue;
            }
            CitizenEntity head = (CitizenEntity) w.getEntity(v.queue.get(0));
            if (head.squaredDistanceTo(v.spot(0)) < 2.5 * 2.5 && !head.walkingRoute()) {
                if (v.serveAt == 0) {
                    v.serveAt = now + 50 + w.getRandom().nextInt(30);
                    seller.faceTo(head.getPos());
                    seller.busy(Activity.TALK, 60, null, new ItemStack(v.item));
                    head.faceTo(seller.getPos());
                    head.busy(Activity.TALK, 50, null, ItemStack.EMPTY);
                    head.say(LifeAi.pick(w.getRandom(), ORDER), 50);
                } else if (now >= v.serveAt) {
                    v.queue.remove(0);
                    v.serveAt = 0;
                    seller.say("Пожалуйста! Следующий!", 50);
                    LifeAi.served(w, head, new ItemStack(v.item), v.drink);
                    moved = true;
                }
            }
            if (moved) for (int k = 0; k < v.queue.size(); k++) {
                CitizenEntity c = (CitizenEntity) w.getEntity(v.queue.get(k));
                Vec3d spot = v.spot(k), at = v.pos;
                if (c != null && c.squaredDistanceTo(spot) > 1) c.walkRoute(List.of(spot), 0.45, null, ItemStack.EMPTY, () -> c.faceTo(at));
            }
        }
    }

    private static final String[] ORDER = {"Один, пожалуйста!", "Мне как обычно.", "А можно без лука?", "Два, с собой!", "Что посоветуете?"};

    // ================================================================== вечер: клуб и «Стрип»
    static boolean goParty(ServerWorld w, CitizenEntity c, Random r) {
        for (String pl : new String[]{"club_neon", "riv_strip"}) {
            Vec3d at = Story.place(w, pl);
            if (at == null || at.squaredDistanceTo(c.getPos()) > 160 * 160) continue;
            Vec3d spot = Sidewalks.nearestSidewalk(at.add(r.nextInt(13) - 6, 0, r.nextInt(13) - 6));
            LifeAi.go(w, c, spot, null, ItemStack.EMPTY, () -> dance(c, r));
            return true;
        }
        return false;
    }

    private static void dance(CitizenEntity c, Random r) {
        Activity[] moves = {Activity.DANCE, Activity.DANCE2, Activity.DANCE3, Activity.CHEER, Activity.DRINK};
        Activity a = moves[r.nextInt(moves.length)];
        c.busy(a, 400 + r.nextInt(800), null, a == Activity.DRINK ? new ItemStack(ModContent.COLA) : ItemStack.EMPTY);
    }

    // ================================================================== сцены (ведёт Leisure)
    private enum Kind { WORKER, DOCKER, COP, SWEEPER, HOMELESS, CLUBBER, DANCER }

    private static final class Scene {
        Kind kind;
        String key;
        Vec3d a, b;
        int phase;
    }

    private static final Map<UUID, Scene> SCENES = new HashMap<>();
    private static final Map<UUID, UUID> PETS = new HashMap<>(); // хозяин → собака

    public static void scenesFor(ServerWorld w, ServerPlayerEntity p) {
        scenes(w, p);
    }

    /** Сцены рядом с игроком: стройка, порт, патруль, дворник, бездомный, клуб. Вызывается из Streets раз в 2 с. */
    static void scenes(ServerWorld w, ServerPlayerEntity p) {
        Random r = w.getRandom();
        double h = LifeAi.hour(w);
        boolean rain = w.isRaining(), day = h >= 8 && h < 18;
        Island.Zone here = Island.zone(p.getBlockX(), p.getBlockZ());
        // стройки делового центра
        if (day && !rain && (here == Island.Zone.DOWNTOWN)) {
            long px0 = Math.floorDiv(p.getBlockX() + 24, 48), pz0 = Math.floorDiv(p.getBlockZ() + 24, 48);
            for (long px = px0 - 1; px <= px0 + 1; px++)
                for (long pz = pz0 - 1; pz <= pz0 + 1; pz++) {
                    int[] in = Island.lotInfo((int) px * 48, (int) pz * 48);
                    if (in == null || in[0] != 8) continue;
                    int cx = (int) px * 48, cz = (int) pz * 48;
                    ensure(w, "site:" + px + ":" + pz, 3, Kind.WORKER, new Vec3d(cx - 8.5, Island.G + 1, cz + 7.5), new Vec3d(cx + 3.5, Island.G + 1, cz - 3.5), r);
                }
        }
        // порт: грузчики со склада на причал
        if (h >= 7 && h < 19 && here == Island.Zone.PORT) {
            Vec3d a = Story.place(w, "port_warehouse"), b = Story.place(w, "port_dock");
            if (a != null && b != null && a.squaredDistanceTo(p.getPos()) < 90 * 90) ensure(w, "port", 3, Kind.DOCKER, a, b, r);
        }
        boolean town = here == Island.Zone.DOWNTOWN || here == Island.Zone.RIVIERA || here == Island.Zone.INDUSTRIAL || here == Island.Zone.SUBURB;
        if (town && h >= 8 && h < 23) { // патруль: двое полицейских вокруг квартала
            Vec3d s = Sidewalks.nearestSidewalk(p.getPos().add(r.nextInt(60) - 30, 0, r.nextInt(60) - 30));
            ensure(w, "cops:" + here, 2, Kind.COP, s, s, r);
        }
        if (town && day && !rain && here != Island.Zone.SUBURB) ensure(w, "sweep:" + here, 1, Kind.SWEEPER, Sidewalks.nearestSidewalk(p.getPos().add(r.nextInt(50) - 25, 0, r.nextInt(50) - 25)), null, r);
        if ((here == Island.Zone.DOWNTOWN || here == Island.Zone.INDUSTRIAL) && h >= 6) ensure(w, "bum:" + here, 1, Kind.HOMELESS, urn(p.getPos(), r), null, r);
        // вечер: очередь в «Неон», танцы на «Стрипе»
        if (h >= 20 || h < 3) {
            Vec3d club = Story.place(w, "club_neon");
            if (club != null && club.squaredDistanceTo(p.getPos()) < 80 * 80) ensure(w, "clubq", (int) Math.round(6 * scale()), Kind.CLUBBER, Sidewalks.nearestSidewalk(club), null, r);
            Vec3d strip = Story.place(w, "riv_strip");
            if (strip != null && strip.squaredDistanceTo(p.getPos()) < 90 * 90) ensure(w, "strip", (int) Math.round(8 * scale()), Kind.DANCER, strip, null, r);
        }
        // собачник — иногда, днём в спальных районах и на Ривьере
        if (town && h >= 7 && h < 21 && !rain && PETS.size() < 2 * w.getPlayers().size() && r.nextInt(6) == 0) dogWalker(w, p, r);
    }

    /** Урна на тротуаре рядом (Island.city: компостер в 6 блоках от осевой, каждые 16 блоков, m = 4). */
    private static Vec3d urn(Vec3d p, Random r) {
        int X = (int) (Math.round((p.x - 24) / 48.0) * 48 + 24);
        int z = (int) (Math.floor(p.z / 16) * 16) + 4 + 16 * (r.nextInt(5) - 2);
        if (Island.gridDist(z) < 7) z += 16;
        int x = X + (r.nextBoolean() ? 6 : -6);
        // встать рядом с урной (на полосе 5)
        return new Vec3d(x + (x > X ? -0.5 : 1.5), Island.G + 1, z + 0.5);
    }

    private static final Map<String, List<UUID>> GROUPS = new HashMap<>();

    /** Держать n человек сцены key (если все ушли/исчезли — новые, в стороне от взгляда игрока). */
    private static void ensure(ServerWorld w, String key, int n, Kind kind, Vec3d a, @Nullable Vec3d b, Random r) {
        List<UUID> g = GROUPS.computeIfAbsent(key, k -> new ArrayList<>());
        g.removeIf(u -> !(w.getEntity(u) instanceof CitizenEntity c) || c.isRemoved());
        if (g.size() >= n || !w.isChunkLoaded(BlockPos.ofFloored(a))) return;
        Vec3d at = a.add(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
        if (kind == Kind.CLUBBER) at = a.add(g.size() * 1.0, 0, 0);
        Vec3d foot = new Vec3d(at.x, Island.surface((int) Math.floor(at.x), (int) Math.floor(at.z)) + 1, at.z);
        if (!w.getBlockState(BlockPos.ofFloored(foot)).getCollisionShape(w, BlockPos.ofFloored(foot)).isEmpty()) return;
        if (!Sidewalks.unseen(w, foot.add(0, 1, 0)) && Sidewalks.playerNear(w, foot, 40)) return; // не появляться на глазах
        CitizenEntity c = citizen(w, foot, r);
        if (kind == Kind.COP) {
            c.setup(6, CitizenEntity.Role.POLICE, false, "Патрульный " + Names.random(r, false));
            c.setCustomNameVisible(false);
        }
        if (kind == Kind.WORKER || kind == Kind.DOCKER) c.setup(4, CitizenEntity.Role.CITIZEN, false, Names.random(r, false));
        c.addCommandTag(TAG);
        c.lifeManaged = true;
        if (!w.spawnEntity(c)) return;
        Scene s = new Scene();
        s.kind = kind;
        s.key = key;
        s.a = a;
        s.b = b != null ? b : a;
        SCENES.put(c.getUuid(), s);
        g.add(c.getUuid());
    }

    private static void sceneTick(ServerWorld w) {
        Random r = w.getRandom();
        for (Iterator<Map.Entry<UUID, Scene>> it = SCENES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (!(w.getEntity(e.getKey()) instanceof CitizenEntity c) || c.isRemoved()) {
                it.remove();
                continue;
            }
            Scene s = e.getValue();
            if (!c.calm() || c.walkingRoute() || c.isBusy()) continue;
            s.phase++;
            switch (s.kind) {
                case WORKER -> { // молоток на каркасе ↔ носит коробки со склада
                    if (s.phase % 2 == 0) c.busy(r.nextInt(3) == 0 ? Activity.SWEEP : Activity.HAMMER, 200 + r.nextInt(200), null, new ItemStack(Items.IRON_AXE));
                    else c.walkRoute(List.of((s.phase % 4 == 1 ? s.a : s.b).add(r.nextInt(5) - 2, 0, r.nextInt(5) - 2)), 0.5, Activity.CARRY, new ItemStack(ModContent.PARCEL), null);
                }
                case DOCKER -> c.walkRoute(List.of((s.phase % 2 == 0 ? s.a : s.b).add(r.nextInt(7) - 3, 0, r.nextInt(7) - 3)), 0.5,
                        s.phase % 2 == 0 ? null : Activity.CARRY, s.phase % 2 == 0 ? ItemStack.EMPTY : new ItemStack(Items.CHEST), () -> {});
                case COP -> { // обход квартала, иногда — постоять, поговорить по рации
                    if (s.phase % 3 == 0) c.busy(r.nextBoolean() ? Activity.PHONE : Activity.WATCH, 100, null, new ItemStack(ModContent.PHONE));
                    else {
                        Vec3d to = Sidewalks.nearestSidewalk(c.getPos().add(r.nextInt(80) - 40, 0, r.nextInt(80) - 40));
                        c.walkRoute(Sidewalks.route(c.getPos(), to), 0.5, null, ItemStack.EMPTY, null);
                    }
                }
                case SWEEPER -> {
                    if (s.phase % 2 == 0) c.busy(Activity.SWEEP, 240, null, new ItemStack(Items.BRUSH));
                    else c.walkRoute(Sidewalks.route(c.getPos(), Sidewalks.nearestSidewalk(c.getPos().add(r.nextInt(17) - 8, 0, r.nextInt(17) - 8))), 0.4, null, new ItemStack(Items.BRUSH), null);
                }
                case HOMELESS -> {
                    if (s.phase % 2 == 0) c.busy(r.nextInt(4) == 0 ? Activity.SHRUG : Activity.RUMMAGE, 300, null, ItemStack.EMPTY);
                    else c.walkRoute(Sidewalks.route(c.getPos(), urn(c.getPos(), r)), 0.35, null, ItemStack.EMPTY, null);
                }
                case CLUBBER -> { // стоят в очереди; первый иногда проходит внутрь
                    if (r.nextInt(12) == 0 && GROUPS.getOrDefault(s.key, List.of()).indexOf(c.getUuid()) == 0) {
                        Vec3d club = Story.place(w, "club_neon");
                        if (club != null) {
                            c.walkRoute(List.of(club), 0.5, null, ItemStack.EMPTY, () -> c.removeLater(4));
                            break;
                        }
                    }
                    Activity[] wait = {Activity.PHONE, Activity.SMOKE, Activity.TALK, Activity.WATCH, Activity.DANCE2, Activity.IDLE};
                    Activity a = wait[r.nextInt(wait.length)];
                    c.busy(a, 100 + r.nextInt(100), null, a == Activity.PHONE ? new ItemStack(ModContent.PHONE) : ItemStack.EMPTY);
                }
                case DANCER -> dance(c, r);
            }
        }
    }

    // ---- собачник
    private static void dogWalker(ServerWorld w, ServerPlayerEntity p, Random r) {
        Vec3d at = Sidewalks.nearestSidewalk(p.getPos().add(r.nextInt(70) - 35, 0, r.nextInt(70) - 35));
        if (at.squaredDistanceTo(p.getPos()) < 20 * 20 || !Sidewalks.unseen(w, at.add(0, 1, 0)) || !w.isChunkLoaded(BlockPos.ofFloored(at))) return;
        if (!w.getBlockState(BlockPos.ofFloored(at)).isAir()) return;
        CitizenEntity c = citizen(w, at, r);
        c.addCommandTag(DOG);
        c.lifeManaged = true;
        WolfEntity dog = EntityType.WOLF.create(w);
        if (dog == null || !w.spawnEntity(c)) return;
        dog.refreshPositionAndAngles(at.x + 1, at.y, at.z, 0, 0);
        dog.addCommandTag(PET);
        dog.setPersistent();
        w.spawnEntity(dog);
        PETS.put(c.getUuid(), dog.getUuid());
    }

    private static void pets(ServerWorld w) {
        for (Iterator<Map.Entry<UUID, UUID>> it = PETS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            var owner = w.getEntity(e.getKey());
            var dog = w.getEntity(e.getValue());
            if (!(dog instanceof WolfEntity wolf) || wolf.isRemoved()) {
                if (owner instanceof CitizenEntity c) c.removeCommandTag(DOG);
                it.remove();
                continue;
            }
            if (!(owner instanceof CitizenEntity c) || c.isRemoved() || c.isDead()) {
                wolf.discard();
                it.remove();
                continue;
            }
            if (c.getMainHandStack().isEmpty() && c.walkingRoute()) c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.LEAD));
            double d = wolf.squaredDistanceTo(c);
            if (d > 14 * 14 && Sidewalks.unseen(wolf)) wolf.refreshPositionAndAngles(c.getX() + 1, c.getY(), c.getZ(), c.getYaw(), 0);
            else if (d > 3 * 3) wolf.getNavigation().startMovingTo(c, d > 36 ? 1.3 : 1.0);
        }
    }

    // ================================================================== такт
    private static void tick(MinecraftServer server) {
        long t = server.getTicks();
        ServerWorld w = server.getOverworld();
        if (!VOLLEYS.isEmpty() && t % 2 == 0) volleys(w);
        if (t % 20 != 13) return;
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity p : w.getPlayers()) if (!p.isSpectator()) players.add(p);
        if (players.isEmpty()) return;
        vendors(w, players);
        sceneTick(w);
        pets(w);
        // хвосты прошлых запусков: мячи и собаки без хозяина, сцены без записи
        if (t % 200 == 13) for (ServerPlayerEntity p : players) {
            Box box = p.getBoundingBox().expand(96, 40, 96);
            for (BallEntity b : w.getEntitiesByClass(BallEntity.class, box, b -> b.getCommandTags().contains(BALL)))
                if (VOLLEYS.stream().noneMatch(v -> v.ball == b)) b.discard();
            for (WolfEntity d : w.getEntitiesByClass(WolfEntity.class, box, d -> d.getCommandTags().contains(PET)))
                if (!PETS.containsValue(d.getUuid())) d.discard();
            for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, box, c -> c.getCommandTags().contains(TAG)))
                if (!SCENES.containsKey(c.getUuid()) && !isVendor(c)) c.discard();
        }
    }

    private static boolean isVendor(CitizenEntity c) {
        for (Vendor v : VENDORS) if (c.getUuid().equals(v.uuid)) return true;
        return false;
    }

    /** Для автотеста: сколько людей в сценах, очередях, у мячей, с собаками. */
    public static String debug() {
        int q = 0;
        for (Vendor v : VENDORS) q += v.queue.size();
        return "scenes=" + SCENES.size() + " queue=" + q + " volleys=" + VOLLEYS.size() + " dogs=" + PETS.size();
    }
}
