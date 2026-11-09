package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
import com.yompix.novograd.world.IslandRoads;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;

/**
 * Умная погоня как в GTA: патрульные машины ведут игрока (подрезают, бьют в заднее крыло — PIT, зажимают с двух сторон),
 * с 3★ — шипы на дороге и перекрытые мосты на Ривьеру, с 4★ — блокпосты на трассах и вертолёт, 5★ — армия:
 * броневики с пулемётчиком и солдаты с автоматами. Потеряли из виду — ищут в круге вокруг последней точки
 * (круг на радаре), вне круга звёзды гаснут быстрее.
 * Всё раз в секунду и только рядом с игроками в розыске; шипы проверяются каждый тик, но их на карте 1–2.
 */
public final class Pursuit {
    private Pursuit() {}

    static final class P {
        @Nullable Vec3d lastKnown, prevPos;
        Vec3d vel = Vec3d.ZERO;
        long lastSeenAt, lastPit, lastSpike, lastBridge, lastHighway, lastArmy, zeroSince;
        int boxTicks, searchSent;
        boolean armyTold;
        final int seed = new Random().nextInt(1000);
    }

    private static final Map<UUID, P> ST = new HashMap<>();

    private static P st(ServerPlayerEntity p) {
        return ST.computeIfAbsent(p.getUuid(), u -> new P());
    }

    // ------------------------------------------------------------------ API для Wanted, CitizenEntity, HeliEntity
    /** Радиус зоны поиска (блоки) — растёт со звёздами. */
    public static double radius(int stars) {
        return 40 + stars * 14;
    }

    /** Полиция потеряла игрока из виду (больше 2 с). */
    public static boolean lost(ServerPlayerEntity p) {
        P s = ST.get(p.getUuid());
        return s != null && s.lastKnown != null && Wanted.stars(p) > 0 && !Wanted.seen(p) && p.getServerWorld().getTime() - s.lastSeenAt > 40;
    }

    /** Звёзды не начисляются: идёт раунд «Полицейских и воров» или игрок сидит в тюрьме. */
    public static boolean immune(ServerPlayerEntity p) {
        return CopsAndRobbers.immune(p) || Prison.immune(p);
    }

    /** Вертолёт с прожектором видит игрока на открытом месте (не под крышей и не в тоннеле), если кружит рядом. */
    public static boolean extraSeen(ServerPlayerEntity p, int stars) {
        ServerWorld w = p.getServerWorld();
        // экипаж патрульной машины видит дальше пешего копа (по прямой, без стен)
        if (Wanted.policeSees(w, p.getVehicle() != null ? p.getVehicle() : p, 60)) return true;
        if (stars < 3) return false;
        if (!w.isSkyVisible(p.getBlockPos().up())) return false;
        for (var h : w.getEntitiesByClass(com.yompix.novograd.vehicle.HeliEntity.class, p.getBoundingBox().expand(40, 60, 40), h -> h.isPolice() && !h.wrecked()))
            if (Math.hypot(h.getX() - p.getX(), h.getZ() - p.getZ()) < 30) return true;
        return false;
    }

    /** Сколько тиков вне поля зрения гаснет звезда: вне зоны поиска быстрее, внутри — дольше. */
    public static long hideTicks(ServerPlayerEntity p, int stars, long base) {
        P s = ST.get(p.getUuid());
        if (s == null || s.lastKnown == null) return base;
        double d = Math.hypot(p.getX() - s.lastKnown.x, p.getZ() - s.lastKnown.z);
        return d > radius(stars) ? base * 6 / 10 : base * 6 / 5;
    }

    /** Куда идти пешему копу, когда цель потеряна (null — цель видна, обычный бой). */
    @Nullable
    public static Vec3d searchPoint(CitizenEntity c, LivingEntity t) {
        if (!(t instanceof ServerPlayerEntity p) || !lost(p)) return null;
        if (c.distanceTo(p) < 12 && c.canSee(p)) return null; // наткнулся сам — бой (звёзды вспыхнут в Wanted.tick)
        return searchPoint(p, st(p), c.getId(), false);
    }

    /** Куда смотрит вертолёт: на игрока или на зону поиска. */
    public static Vec3d heliFocus(ServerPlayerEntity p) {
        if (!lost(p)) return p.getPos();
        P s = st(p);
        return s.lastKnown == null ? p.getPos() : s.lastKnown;
    }

    /** Прожектор вертолёта шарит по зоне поиска. */
    private static final DustParticleEffect BEAM = new DustParticleEffect(new Vector3f(1f, 0.97f, 0.8f), 1.6f);

    public static void sweep(ServerWorld w, Entity heli, int age) {
        if (age % 3 != 0) return;
        ServerPlayerEntity p = null;
        double best = 1e9;
        for (ServerPlayerEntity o : w.getPlayers()) {
            double d = o.squaredDistanceTo(heli);
            if (d < best && Wanted.stars(o) > 0) {
                best = d;
                p = o;
            }
        }
        if (p == null) return;
        P s = st(p);
        if (s.lastKnown == null) return;
        double r = radius(Wanted.stars(p)) * 0.5;
        double a = age * 0.035, k = 0.4 + 0.6 * Math.abs(Math.sin(age * 0.011));
        Vec3d to = s.lastKnown.add(Math.cos(a) * r * k, 0, Math.sin(a) * r * k);
        to = new Vec3d(to.x, Story.topY(w, (int) Math.floor(to.x), (int) Math.floor(to.z)), to.z);
        Vec3d from = heli.getPos().add(0, -0.2, 0);
        Vec3d d = to.subtract(from);
        double len = d.length();
        Vec3d n = d.normalize();
        for (double t = 1.5; t < len; t += 1.6) {
            Vec3d q = from.add(n.multiply(t));
            double sp = t / len * 0.6;
            w.spawnParticles(BEAM, q.x, q.y, q.z, 1, sp, sp, sp, 0);
        }
        for (int i = 0; i < 10; i++) {
            double b = i / 10.0 * Math.PI * 2;
            w.spawnParticles(BEAM, to.x + Math.cos(b) * 2.4, to.y + 0.1, to.z + Math.sin(b) * 2.4, 1, 0, 0, 0, 0);
        }
    }

    public static String debug(ServerPlayerEntity p) {
        P s = st(p);
        return "stars " + Wanted.stars(p) + " seen " + Wanted.seen(p) + " lost " + lost(p) + " last " + s.lastKnown + " vel " + s.vel
                + " strips " + STRIPS.size() + " army " + ARMY.size() + " blocks " + BLOCKS.size();
    }

    /** Машина полиции или спецназа/армии (броневики тоже ведёт Pursuit). */
    static boolean copCar(CarEntity c) {
        return c.isPolice() || c.getVariant() == CarEntity.SWAT;
    }

    // ------------------------------------------------------------------ тик
    public static void tick(MinecraftServer server, int t) {
        if (!STRIPS.isEmpty()) spikeTick(server.getOverworld());
        if (t % 5 == 2) contactTick(server);
        if (t % 20 == 2) second(server);
        if (t % 200 == 77) cleanup(server);
    }

    private static void second(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            P s = st(p);
            ServerWorld w = p.getServerWorld();
            long now = w.getTime();
            Vec3d pos = p.getPos();
            s.vel = s.prevPos != null && s.prevPos.squaredDistanceTo(pos) < 60 * 60 ? pos.subtract(s.prevPos).multiply(1 / 20.0) : Vec3d.ZERO;
            s.vel = new Vec3d(s.vel.x, 0, s.vel.z);
            s.prevPos = pos;
            int stars = Wanted.stars(p);
            if (stars == 0) {
                if (s.searchSent > 0) {
                    s.searchSent = 0;
                    NbtCompound n = new NbtCompound();
                    n.putBoolean("on", false);
                    CopsNet.send(p, "search", n);
                }
                s.lastKnown = null;
                if (s.zeroSince == 0) s.zeroSince = now;
                if (now - s.zeroSince > 20 * 30) dismissArmy(w, p);
                s.armyTold = false;
                continue;
            }
            s.zeroSince = 0;
            if (Wanted.seen(p) || s.lastKnown == null) {
                s.lastKnown = pos;
                s.lastSeenAt = now;
            }
            boolean lost = lost(p);
            // зона поиска на радаре
            if (lost || s.searchSent > 0) {
                NbtCompound n = new NbtCompound();
                n.putBoolean("on", lost);
                n.putDouble("x", s.lastKnown.x);
                n.putDouble("z", s.lastKnown.z);
                n.putDouble("r", radius(stars));
                CopsNet.send(p, "search", n);
                s.searchSent = lost ? 1 : 0;
            }
            // машины погони
            int i = 0;
            for (CarEntity car : w.getEntitiesByClass(CarEntity.class, p.getBoundingBox().expand(140, 40, 140),
                    c -> copCar(c) && !c.wrecked() && c.hasNpcDriver() && c.chaseTarget() == p))
                steer(w, car, p, s, lost, now, i++);
            boolean inCar = p.getVehicle() instanceof CarEntity cv && !(cv instanceof com.yompix.novograd.vehicle.MotorVehicle) && cv.getControllingPassenger() == p;
            double ps = s.vel.horizontalLength();
            if (stars >= 3 && inCar && !lost && ps > 0.3 && now - s.lastSpike > 20 * 30 && now - s.lastSeenAt < 100) {
                if (spikes(w, p, s)) s.lastSpike = now;
                else s.lastSpike = now - 20 * 25; // не нашли дорогу — попробуем через 5 с
            }
            if (stars >= 3 && now - s.lastBridge > 20 * 80) {
                if (bridge(w, p, stars)) s.lastBridge = now;
            }
            if (stars >= 4 && inCar && ps > 0.35 && now - s.lastHighway > 20 * 35 && !Island.urban(Island.zone(p.getBlockX(), p.getBlockZ()))) {
                if (blockAhead(w, p, s, 75, stars >= 5)) s.lastHighway = now;
                else s.lastHighway = now - 20 * 28;
            }
            if (stars >= 5) army(w, p, s, now);
        }
    }

    // ------------------------------------------------------------------ погоня на машинах
    private static void steer(ServerWorld w, CarEntity car, ServerPlayerEntity p, P s, boolean lost, long now, int idx) {
        car.pursuitDriven = true;
        Entity tgt = p.getVehicle() != null ? p.getVehicle() : p;
        Vec3d tp = tgt.getPos();
        double d = Math.sqrt(car.squaredDistanceTo(tgt));
        Vec3d aim;
        float spd;
        if (!lost) {
            double ps = s.vel.horizontalLength();
            if (tgt instanceof CarEntity && ps > 0.2) {
                Vec3d f = s.vel.normalize(), side = new Vec3d(-f.z, 0, f.x);
                double sg = Math.signum(car.getPos().subtract(tp).dotProduct(side));
                if (sg == 0) sg = idx % 2 == 0 ? 1 : -1;
                if (idx % 2 == 1) sg = -sg; // вторая машина — с другого бока (зажать)
                if (d > 22) aim = tp.add(s.vel.multiply(Math.min(28, d * 0.6))); // упреждение: перехват по курсу
                else aim = tp.add(f.multiply(-0.8)).add(side.multiply(sg * 1.6)).add(s.vel.multiply(7)); // в заднее крыло — PIT
                spd = (float) Math.min(d > 22 ? 1.35 : 1.2, ps + (d > 22 ? 0.45 : 0.3));
                // «резинка» как в GTA: патруль не отстаёт от машины на прямой (если смотрит в её сторону)
                double cy = Math.toRadians(car.getYaw());
                Vec3d cf = new Vec3d(-Math.sin(cy), 0, Math.cos(cy));
                if (d > 4 && d < 30 && cf.dotProduct(aim.subtract(car.getPos()).normalize()) > 0.8)
                    car.speed = Math.max(car.speed, (float) Math.min(spd, ps + (d > 10 ? 0.35 : 0.25)));
                if (d > 28 && ps > 0.5) { // отстали на трассе — нитро
                    if (!car.hasNitro()) car.setTune(true, car.engine(), car.armor());
                    car.fireNitro();
                }
            } else {
                aim = tp;
                spd = d < 14 ? 0.35f : 0.85f;
            }
        } else {
            aim = searchPoint(p, s, car.getId(), true);
            spd = car.squaredDistanceTo(aim) < 64 ? 0.2f : 0.5f;
        }
        if (car.squaredDistanceTo(aim) > 55 * 55) {
            if ((now / 20 + car.getId()) % 3 != 0 && car.onRoute()) return; // длинный путь по дорогам — пересчёт раз в 3 с
            List<int[]> r = IslandRoads.route(car.getX(), car.getZ(), aim.x, aim.z);
            List<Vec3d> pts = new ArrayList<>();
            for (int[] q : r) if (q[0] != 0 || q[1] != 0) pts.add(new Vec3d(q[0] + 0.5, car.getY(), q[1] + 0.5));
            while (pts.size() > 1 && pts.get(0).squaredDistanceTo(car.getPos()) < 16) pts.remove(0);
            pts.add(aim);
            car.setRoute(pts, Math.max(spd, 0.8f), null);
        } else if (!lost && tgt instanceof CarEntity && s.vel.horizontalLengthSquared() > 0.04)
            car.setRoute(List.of(aim, aim.add(s.vel.normalize().multiply(30))), spd, null); // точка за целью — не тормозим «у финиша», а таранем
        else car.setRoute(List.of(aim), spd, null);
    }

    /** Точка поиска вокруг последнего места: у каждого копа своя, меняется раз в 10 с; машинам — на дороге. */
    private static Vec3d searchPoint(ServerPlayerEntity p, P s, int id, boolean road) {
        Vec3d c = s.lastKnown == null ? p.getPos() : s.lastKnown;
        long slot = p.getServerWorld().getTime() / 200;
        Random r = new Random(slot * 31L + id * 7919L + s.seed);
        double a = r.nextDouble() * Math.PI * 2, dd = radius(Wanted.stars(p)) * (0.15 + 0.6 * r.nextDouble());
        double x = c.x + Math.cos(a) * dd, z = c.z + Math.sin(a) * dd;
        if (road && Island.urban(Island.zone((int) x, (int) z))) {
            double sx = FunUtil.snap(x), sz = FunUtil.snap(z);
            if (Math.abs(sx - x) < Math.abs(sz - z)) x = sx + 2.5;
            else z = sz + 2.5;
        }
        return new Vec3d(x, c.y, z);
    }

    // ------------------------------------------------------------------ PIT, зажим, пулемёт броневика (каждые 5 тиков)
    private static void contactTick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (Wanted.stars(p) == 0) continue;
            ServerWorld w = p.getServerWorld();
            long now = w.getTime();
            P s = st(p);
            turret(w, p, now);
            if (!(p.getVehicle() instanceof CarEntity pc) || pc instanceof com.yompix.novograd.vehicle.MotorVehicle || pc.getControllingPassenger() != p) {
                s.boxTicks = 0;
                continue;
            }
            double ps = s.vel.horizontalLength();
            double yr = Math.toRadians(pc.getYaw());
            Vec3d fwd = new Vec3d(-Math.sin(yr), 0, Math.cos(yr)), side = new Vec3d(-fwd.z, 0, fwd.x);
            boolean left = false, right = false;
            List<CarEntity> near = w.getEntitiesByClass(CarEntity.class, pc.getBoundingBox().expand(6, 2, 6), c -> c != pc && copCar(c) && !c.wrecked() && c.hasNpcDriver());
            for (CarEntity c : near) {
                Vec3d rel = c.getPos().subtract(pc.getPos());
                double lat = rel.dotProduct(side), along = rel.dotProduct(fwd);
                if (Math.abs(along) < 3.8 && Math.abs(lat) < 4.4 && Math.abs(lat) > 1.0) {
                    if (lat > 0) left = true;
                    else right = true;
                }
                // PIT: коп на скорости бьёт нас в бок/заднее крыло
                double cr = Math.toRadians(c.getYaw());
                Vec3d cf = new Vec3d(-Math.sin(cr), 0, Math.cos(cr));
                double dist = rel.length();
                float cs = Math.max(c.speed, LAST_SPEED.getOrDefault(c, 0f)); // скорость до удара (после удара она падает)
                LAST_SPEED.put(c, c.speed);
                if (dist < 4.3 && cs > 0.3f && ps > 0.22 && now - s.lastPit > 90 && along < 1.5
                        && cf.dotProduct(rel.multiply(-1).normalize()) > 0.45) {
                    s.lastPit = now;
                    float kick = (float) (Math.signum(lat == 0 ? 1 : lat) * (35 + 25 * Math.min(1, ps)));
                    NbtCompound n = new NbtCompound();
                    n.putFloat("kick", kick);
                    n.putFloat("mul", 0.5f);
                    CopsNet.send(p, "pit", n);
                    c.speed *= 0.6f;
                    if (Boolean.getBoolean("novograd.debugcops")) com.yompix.novograd.Novograd.LOG.info("[cops] PIT kick {} ps {} cop {}", kick, ps, c.speed);
                    w.playSound(null, pc.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.NEUTRAL, 1.3f, 0.6f);
                    w.playSound(null, pc.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.NEUTRAL, 0.9f, 0.8f);
                    p.sendMessage(Text.literal("PIT-манёвр — полиция развернула твою машину!").formatted(Formatting.RED), true);
                }
            }
            if (left && right) {
                s.boxTicks++;
                if (ps > 0.08) {
                    NbtCompound n = new NbtCompound();
                    n.putFloat("mul", 0.86f);
                    CopsNet.send(p, "box", n);
                }
                if (s.boxTicks == 1 && Boolean.getBoolean("novograd.debugcops")) com.yompix.novograd.Novograd.LOG.info("[cops] BOX ps {}", ps);
                if (s.boxTicks == 1) p.sendMessage(Text.literal("Тебя зажали с двух сторон — вырывайся!").formatted(Formatting.RED), true);
                if (s.boxTicks >= 10 && ps < 0.12) {
                    // встал — копы выходят задерживать
                    for (CarEntity c : near)
                        for (Entity e : new ArrayList<>(c.getPassengerList()))
                            if (e instanceof CitizenEntity cop) {
                                cop.stopRiding();
                                cop.attack(p);
                            }
                    s.boxTicks = 0;
                }
            } else s.boxTicks = 0;
        }
    }

    private static final Map<CarEntity, Float> LAST_SPEED = new WeakHashMap<>();

    // ------------------------------------------------------------------ шипы
    private record Strip(Box box, Vec3d a, Vec3d b, long until) {}

    private static final List<Strip> STRIPS = new ArrayList<>();
    private static final String SPIKE_TAG = "nvg_spike", BLOCK_TAG = "nvg_block";

    private static boolean asphalt(ServerWorld w, int x, int y, int z) {
        BlockState b = w.getBlockState(new BlockPos(x, y - 1, z));
        return b.isOf(ModContent.ASPHALT) || b.isOf(ModContent.ASPHALT_LINE) || b.isOf(ModContent.CROSSWALK) || b.isOf(ModContent.ASPHALT_EDGE)
                || b.isOf(net.minecraft.block.Blocks.YELLOW_CONCRETE) || b.isOf(net.minecraft.block.Blocks.WHITE_CONCRETE); // разметка трассы
    }

    /** Поперёк дороги впереди: {центр, нижняя граница, верхняя граница, y, alongX(1/0)} или null. */
    @Nullable
    private static int[] roadAhead(ServerWorld w, Vec3d from, Vec3d dir, int dist) {
        boolean alongX = Math.abs(dir.x) > Math.abs(dir.z);
        for (int dd = dist; dd <= dist + 25; dd += 5) {
            int x = (int) Math.floor(from.x + dir.x * dd), z = (int) Math.floor(from.z + dir.z * dd);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) return null;
            int y = Story.topY(w, x, z);
            if (!asphalt(w, x, y, z)) continue;
            int lo = 0, hi = 0;
            while (lo > -16 && asphalt(w, alongX ? x : x + lo - 1, y, alongX ? z + lo - 1 : z) && Story.topY(w, alongX ? x : x + lo - 1, alongX ? z + lo - 1 : z) == y) lo--;
            while (hi < 16 && asphalt(w, alongX ? x : x + hi + 1, y, alongX ? z + hi + 1 : z) && Story.topY(w, alongX ? x : x + hi + 1, alongX ? z + hi + 1 : z) == y) hi++;
            if (hi - lo < 3) continue;
            return new int[]{alongX ? x : z, (alongX ? z : x) + lo, (alongX ? z : x) + hi, y, alongX ? 1 : 0};
        }
        return null;
    }

    private static Vec3d heading(ServerPlayerEntity p, P s) {
        if (s.vel.horizontalLengthSquared() > 0.01) return s.vel.normalize();
        double yr = Math.toRadians(p.getVehicle() != null ? p.getVehicle().getYaw() : p.getYaw());
        return new Vec3d(-Math.sin(yr), 0, Math.cos(yr));
    }

    private static boolean spikes(ServerWorld w, ServerPlayerEntity p, P s) {
        int dist = 45 + (int) Math.min(25, s.vel.horizontalLength() * 25);
        int[] r = roadAhead(w, p.getPos(), heading(p, s), dist);
        if (r == null) return false;
        boolean alongX = r[4] == 1;
        int along = r[0], lo = r[1], hi = r[2], y = r[3];
        int len = hi - lo + 1;
        // основа — чёрная лента, сверху «гребёнка» (решётка) — видно издалека
        if (alongX) {
            display(w, along + 0.1, y + 0.01, lo, "minecraft:black_concrete", 0.8f, 0.08f, len, SPIKE_TAG);
            display(w, along - 0.15, y + 0.04, lo, "minecraft:iron_bars[north=true,south=true]", 1f, 0.4f, len, SPIKE_TAG);
            display(w, along + 0.15, y + 0.04, lo, "minecraft:iron_bars[north=true,south=true]", 1f, 0.32f, len, SPIKE_TAG);
        } else {
            display(w, lo, y + 0.01, along + 0.1, "minecraft:black_concrete", len, 0.08f, 0.8f, SPIKE_TAG);
            display(w, lo, y + 0.04, along - 0.15, "minecraft:iron_bars[east=true,west=true]", len, 0.4f, 1f, SPIKE_TAG);
            display(w, lo, y + 0.04, along + 0.15, "minecraft:iron_bars[east=true,west=true]", len, 0.32f, 1f, SPIKE_TAG);
        }
        Vec3d a = alongX ? new Vec3d(along + 0.5, y, lo) : new Vec3d(lo, y, along + 0.5);
        Vec3d b = alongX ? new Vec3d(along + 0.5, y, hi + 1) : new Vec3d(hi + 1, y, along + 0.5);
        Box box = new Box(a, b).expand(alongX ? 0.9 : 0, 1.2, alongX ? 0 : 0.9).stretch(0, 1, 0);
        STRIPS.add(new Strip(box, a, b, w.getTime() + 20 * 50));
        // коп на обочине, который бросил шипы
        int cx = alongX ? along - (int) Math.signum(heading(p, s).x) * 3 : hi + 2, cz = alongX ? hi + 2 : along - (int) Math.signum(heading(p, s).z) * 3;
        CitizenEntity cop = cop(w, cx + 0.5, Story.topY(w, cx, cz), cz + 0.5, "Полицейский", false);
        cop.attack(p);
        p.sendMessage(Text.literal("⚠ Полиция бросила шипы на дорогу впереди!").formatted(Formatting.GOLD), true);
        return true;
    }

    private static void spikeTick(ServerWorld w) {
        long now = w.getTime();
        for (Iterator<Strip> it = STRIPS.iterator(); it.hasNext(); ) {
            Strip st = it.next();
            if (now > st.until) {
                for (Entity e : w.getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class, st.box.expand(2), e -> e.getCommandTags().contains(SPIKE_TAG))) e.discard();
                it.remove();
                continue;
            }
            if (!w.isChunkLoaded(((int) st.a.x) >> 4, ((int) st.a.z) >> 4)) continue;
            for (CarEntity car : w.getEntitiesByClass(CarEntity.class, st.box, c -> !copCar(c) && !c.flat() && !c.wrecked())) {
                car.setFlat(true);
                w.playSound(null, car.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.NEUTRAL, 0.5f, 1.9f);
                w.playSound(null, car.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.NEUTRAL, 1.2f, 0.7f);
                w.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, car.getX(), car.getY() + 0.3, car.getZ(), 12, 1, 0.1, 1, 0.02);
                if (car.getControllingPassenger() instanceof ServerPlayerEntity sp)
                    sp.sendMessage(Text.literal("Шины пробиты! Скорость и руль хуже — почини машину в «Нитро-Гараже» или смени её").formatted(Formatting.RED), false);
            }
        }
    }

    /** Блок-дисплей (без своих блоков в моде): sx/sy/sz — размер, якорь — угол. */
    static void display(ServerWorld w, double x, double y, double z, String state, float sx, float sy, float sz, String tag) {
        DisplayEntity.BlockDisplayEntity d = EntityType.BLOCK_DISPLAY.create(w);
        if (d == null) return;
        NbtCompound n = new NbtCompound();
        NbtCompound bs = new NbtCompound();
        String name = state, props = "";
        int br = state.indexOf('[');
        if (br > 0) {
            name = state.substring(0, br);
            props = state.substring(br + 1, state.length() - 1);
        }
        bs.putString("Name", name);
        if (!props.isEmpty()) {
            NbtCompound pr = new NbtCompound();
            for (String kv : props.split(",")) {
                String[] q = kv.split("=");
                pr.putString(q[0], q[1]);
            }
            bs.put("Properties", pr);
        }
        n.put("block_state", bs);
        NbtCompound tr = new NbtCompound();
        tr.put("translation", floats(0, 0, 0));
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("scale", floats(sx, sy, sz));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        n.put("transformation", tr);
        n.putFloat("view_range", 1.2f);
        d.readNbt(n);
        d.refreshPositionAndAngles(x, y, z, 0, 0);
        d.addCommandTag(tag);
        w.spawnEntity(d);
    }

    private static NbtList floats(float... v) {
        NbtList l = new NbtList();
        for (float f : v) l.add(NbtFloat.of(f));
        return l;
    }

    // ------------------------------------------------------------------ блокпосты: мосты на Ривьеру и трассы
    private static final List<long[]> BLOCKS = new ArrayList<>(); // {uuidMost, uuidLeast, until}
    private static int[] bridgeSpan; // {восточный край x, западный край x}

    private static int[] span() {
        if (bridgeSpan == null) {
            int east = -620, west = -900;
            for (int x = -560; x > -1000; x--) if (Island.zone(x, -744) == Island.Zone.SEA) {
                east = x;
                break;
            }
            for (int x = east; x > -1000; x--) if (Island.zone(x, -744) != Island.Zone.SEA) {
                west = x + 1;
                break;
            }
            bridgeSpan = new int[]{east, west};
        }
        return bridgeSpan;
    }

    /** Мост на Ривьеру перекрыт с той стороны, откуда едет игрок (стоит на мосту — не трогаем). */
    private static boolean bridge(ServerWorld w, ServerPlayerEntity p, int stars) {
        int[] sp = span();
        for (int z0 : new int[]{-744, 744}) {
            if (Math.abs(p.getZ() - z0) > 220) continue;
            int bx;
            if (p.getX() > sp[0] + 4) bx = sp[0] - 6; // едет с Новограда — блок на въезде на мост
            else if (p.getX() < sp[1] - 4) bx = sp[1] + 6; // с Ривьеры
            else continue;
            double d = Math.hypot(p.getX() - bx, p.getZ() - z0);
            if (d > 150 || d < 35 || !w.isChunkLoaded(bx >> 4, z0 >> 4)) continue;
            int y = Story.topY(w, bx, z0);
            if (!asphalt(w, bx, y, z0)) continue;
            int dir = p.getX() > bx ? 1 : -1; // в какую сторону от блока игрок
            for (int dz : new int[]{-4, 0, 4}) {
                CarEntity c = new CarEntity(ModContent.CAR, w);
                c.refreshPositionAndAngles(bx + 0.5, y, z0 + dz + 0.5, 0, 0); // поперёк трассы
                c.setVariant(dz == 0 && stars >= 4 ? CarEntity.SWAT : CarEntity.POLICE);
                c.toggleLight(CarEntity.HAZARD);
                c.setAmbient(true);
                w.spawnEntity(c);
                BLOCKS.add(new long[]{c.getUuid().getMostSignificantBits(), c.getUuid().getLeastSignificantBits(), w.getTime() + 20 * 120});
            }
            for (int k = 0; k < (stars >= 4 ? 3 : 2); k++) {
                CitizenEntity cop = cop(w, bx - dir * 3 + 0.5, y, z0 - 3 + k * 3 + 0.5, stars >= 4 ? "Спецназ" : "Полицейский", stars >= 4);
                cop.attack(p);
            }
            p.sendMessage(Text.literal("" + (z0 < 0 ? "Северный" : "Южный") + " мост перекрыт полицией!").formatted(Formatting.RED), true);
            Dispatch.wanted(p, "перекрыт " + (z0 < 0 ? "северный" : "южный") + " мост", stars);
            return true;
        }
        return false;
    }

    /** Блокпост на трассе впереди: машины поперёк, щель посередине, копы целятся. */
    private static boolean blockAhead(ServerWorld w, ServerPlayerEntity p, P s, int dist, boolean swat) {
        Vec3d dir = heading(p, s);
        int[] r = roadAhead(w, p.getPos(), dir, dist);
        if (r == null) return false;
        boolean alongX = r[4] == 1;
        int along = r[0], lo = r[1], hi = r[2], y = r[3];
        int width = hi - lo + 1, gap = lo + width / 2 + (w.getRandom().nextBoolean() ? 1 : -2);
        for (int c = lo + 1; c <= hi - 1; c += 4) {
            if (Math.abs(c - gap) < 2) continue; // щель — можно прорваться, как в GTA
            CarEntity car = new CarEntity(ModContent.CAR, w);
            car.refreshPositionAndAngles(alongX ? along + 0.5 : c + 0.5, y, alongX ? c + 0.5 : along + 0.5, alongX ? 0 : 90, 0);
            car.setVariant(swat && w.getRandom().nextBoolean() ? CarEntity.SWAT : CarEntity.POLICE);
            car.toggleLight(CarEntity.HAZARD);
            car.setAmbient(true);
            w.spawnEntity(car);
            BLOCKS.add(new long[]{car.getUuid().getMostSignificantBits(), car.getUuid().getLeastSignificantBits(), w.getTime() + 20 * 90});
        }
        double back = alongX ? Math.signum(dir.x) : Math.signum(dir.z);
        for (int k = 0; k < 2; k++) {
            double cx = alongX ? along + 0.5 + back * 3 : lo + 1 + k * (width - 3) + 0.5, cz = alongX ? lo + 1 + k * (width - 3) + 0.5 : along + 0.5 + back * 3;
            cop(w, cx, y, cz, swat ? "Спецназ" : "Полицейский", swat).attack(p);
        }
        p.sendMessage(Text.literal("Блокпост на трассе впереди!").formatted(Formatting.RED), true);
        return true;
    }

    // ------------------------------------------------------------------ армия (5★)
    private static final Map<UUID, UUID> ARMY = new HashMap<>(); // броневик → пулемётчик
    private static final Map<UUID, Integer> BURST = new HashMap<>();

    private static void army(ServerWorld w, ServerPlayerEntity p, P s, long now) {
        int have = 0;
        for (UUID id : new ArrayList<>(ARMY.keySet())) {
            Entity e = w.getEntity(id);
            if (!(e instanceof CarEntity truck) || truck.wrecked()) {
                ARMY.remove(id);
                continue;
            }
            if (truck.squaredDistanceTo(p) < 170 * 170) have++;
            // доехал — солдаты выходят (водитель и пулемётчик остаются)
            if (truck.squaredDistanceTo(p) < 14 * 14 && (truck.slow() || !p.hasVehicle())) {
                UUID gun = ARMY.get(id);
                boolean first = true;
                for (Entity c : new ArrayList<>(truck.getPassengerList())) {
                    if (!(c instanceof CitizenEntity cit)) continue;
                    if (first || c.getUuid().equals(gun)) {
                        first = false;
                        continue;
                    }
                    cit.stopRiding();
                    cit.attack(p);
                }
            }
            if (truck.chaseTarget() == null && Wanted.stars(p) >= 5) truck.chase(p);
        }
        if (have >= 2 || now - s.lastArmy < 20 * 25) return;
        var r = w.getRandom();
        for (int tries = 0; tries < 14; tries++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 55 + r.nextInt(25);
            int bx = (int) (p.getX() + Math.cos(a) * dist), bz = (int) (p.getZ() + Math.sin(a) * dist);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            int y = Story.topY(w, bx, bz);
            if (!asphalt(w, bx, y, bz) || !w.isAir(new BlockPos(bx, y, bz)) || !w.isAir(new BlockPos(bx, y + 1, bz))) continue;
            CarEntity truck = new CarEntity(ModContent.CAR, w);
            truck.refreshPositionAndAngles(bx + 0.5, y, bz + 0.5, FunUtil.yawTo(new Vec3d(bx, y, bz), p.getPos()), 0);
            truck.setVariant(CarEntity.SWAT);
            truck.paint(0x4A5530); // армейский хаки
            truck.setTune(false, 2, 2);
            w.spawnEntity(truck);
            CitizenEntity driver = cop(w, bx + 0.5, y, bz + 0.5, "Солдат", true);
            driver.startRiding(truck, true);
            CitizenEntity gunner = cop(w, bx + 0.5, y, bz + 0.5, "Пулемётчик", true);
            gunner.startRiding(truck, true);
            for (int i = 0; i < 2; i++) {
                CitizenEntity sol = cop(w, bx + 0.5, y, bz + 0.5, "Солдат", true);
                sol.startRiding(truck, true);
                sol.attack(p);
            }
            truck.chase(p);
            ARMY.put(truck.getUuid(), gunner.getUuid());
            s.lastArmy = now;
            if (!s.armyTold) {
                s.armyTold = true;
                Activities.title(p, "АРМИЯ", "Против тебя выслали броневики с пулемётом");
                Dispatch.wanted(p, "в город введена армия", 5);
            }
            return;
        }
    }

    /** Пулемёт броневика: очередь из 3–4 выстрелов, когда цель видна. */
    private static void turret(ServerWorld w, ServerPlayerEntity p, long now) {
        if (ARMY.isEmpty() || p.isCreative()) return;
        for (Map.Entry<UUID, UUID> e : ARMY.entrySet()) {
            if (!(w.getEntity(e.getKey()) instanceof CarEntity truck) || truck.squaredDistanceTo(p) > 48 * 48) continue;
            if (!(w.getEntity(e.getValue()) instanceof CitizenEntity gun) || !gun.isAlive() || gun.getVehicle() != truck) continue;
            int b = BURST.getOrDefault(e.getKey(), 0);
            Vec3d from = truck.getPos().add(0, 2.7, 0);
            Vec3d to = p.getEyePos().add(0, -0.4, 0);
            boolean clear = w.raycast(new net.minecraft.world.RaycastContext(from, to, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE, truck)).getType() == net.minecraft.util.hit.HitResult.Type.MISS;
            if (b <= -8 && clear) b = 4; // пауза между очередями ~2 с
            if (b > 0) {
                Weapons.shoot(w, gun, from, to.subtract(from), 4f, 0.05 + truck.distanceTo(p) * 0.002, 60, true);
                w.playSound(null, truck.getBlockPos(), ModContent.SMG_SHOT, SoundCategory.HOSTILE, 1.6f, 0.7f);
            }
            BURST.put(e.getKey(), b - 1 < -8 ? -8 : b - 1);
        }
    }

    private static void dismissArmy(ServerWorld w, ServerPlayerEntity p) {
        for (UUID id : new ArrayList<>(ARMY.keySet())) {
            Entity e = w.getEntity(id);
            if (e == null || e.squaredDistanceTo(p) > 200 * 200) continue;
            boolean watched = false;
            for (ServerPlayerEntity o : w.getPlayers()) if (o.squaredDistanceTo(e) < 50 * 50) watched = true;
            if (watched) continue;
            for (Entity c : e.getPassengerList()) c.discard();
            e.discard();
            ARMY.remove(id);
        }
    }

    // ------------------------------------------------------------------ уборка
    private static void cleanup(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        long now = w.getTime();
        for (Iterator<long[]> it = BLOCKS.iterator(); it.hasNext(); ) {
            long[] b = it.next();
            if (now < b[2]) continue;
            Entity e = w.getEntity(new UUID(b[0], b[1]));
            if (e == null) {
                it.remove();
                continue;
            }
            boolean watched = false;
            for (ServerPlayerEntity o : w.getPlayers()) if (o.squaredDistanceTo(e) < 40 * 40) watched = true;
            if (!watched && e.getPassengerList().isEmpty()) {
                e.discard();
                it.remove();
            }
        }
        // шипы, оставшиеся от прошлого запуска (сервер перезапущен — список пуст)
        for (ServerPlayerEntity o : w.getPlayers())
            for (Entity e : w.getEntitiesByClass(DisplayEntity.BlockDisplayEntity.class, o.getBoundingBox().expand(96), e -> e.getCommandTags().contains(SPIKE_TAG))) {
                boolean live = false;
                for (Strip st : STRIPS) if (st.box.expand(2).contains(e.getPos())) live = true;
                if (!live) e.discard();
            }
        ST.keySet().removeIf(u -> server.getPlayerManager().getPlayer(u) == null);
    }

    // ------------------------------------------------------------------ люди
    /** Полицейский/спецназовец/солдат подкрепления (исчезает, когда розыск снят). */
    static CitizenEntity cop(ServerWorld w, double x, double y, double z, String name, boolean heavy) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        c.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.POLICE, false, name);
        c.refreshPositionAndAngles(x, y, z, 0, 0);
        if (heavy) {
            c.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            c.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            c.equipStack(EquipmentSlot.MAINHAND, new ItemStack(name.equals("Спецназ") ? ModContent.SMG : Arsenal.RIFLE));
        } else c.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModContent.PISTOL));
        c.removeWhenIdle();
        w.spawnEntity(c);
        return c;
    }

    static double clamp(double v, double a, double b) {
        return MathHelper.clamp(v, a, b);
    }
}
