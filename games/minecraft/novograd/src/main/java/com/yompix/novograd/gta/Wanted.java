package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Звёзды розыска 0–5. Чем больше звёзд — тем больше полиции: пешие патрульные, потом машины с мигалками,
 * с трёх звёзд полиция стреляет. Скрылся из виду — звёзды гаснут. Поймали — «ЗАДЕРЖАН», погиб — больница.
 */
public final class Wanted {
    private Wanted() {}

    static final class W {
        int stars;
        long lastSeen, arrest;
        int spawnTick;
        long lastBlock;
        boolean seen;
    }

    private static final Map<UUID, W> ALL = new HashMap<>();

    private static W w(ServerPlayerEntity p) {
        return ALL.computeIfAbsent(p.getUuid(), u -> new W());
    }

    public static int stars(ServerPlayerEntity p) {
        W x = ALL.get(p.getUuid());
        return x == null ? 0 : x.stars;
    }

    /** Видит ли полиция игрока прямо сейчас (обновляется раз в секунду). */
    public static boolean seen(ServerPlayerEntity p) {
        W x = ALL.get(p.getUuid());
        return x != null && x.stars > 0 && x.seen;
    }

    /** Когда полиция последний раз видела игрока (время мира). */
    public static long lastSeen(ServerPlayerEntity p) {
        W x = ALL.get(p.getUuid());
        return x == null ? 0 : x.lastSeen;
    }

    /** Снять n звёзд (смена образа — gta/Disguise). 0 — розыск снят. */
    public static void reduce(ServerPlayerEntity p, int n) {
        W x = ALL.get(p.getUuid());
        if (x == null || x.stars == 0 || n <= 0) return;
        x.stars = Math.max(0, x.stars - n);
        x.lastSeen = p.getServerWorld().getTime();
        if (x.stars == 0) {
            p.sendMessage(Text.literal("✔ Полиция тебя потеряла").formatted(Formatting.GREEN), true);
            clear(p);
        } else sync(p, x);
    }

    /** +add звёзд (не меньше min). */
    public static void add(ServerPlayerEntity p, int add, String why, int min) {
        if (Pursuit.immune(p)) return; // «Полицейские и воры», тюрьма
        W x = w(p);
        int before = x.stars;
        x.stars = Math.min(5, Math.max(min, x.stars + add));
        x.lastSeen = p.getServerWorld().getTime();
        x.seen = true;
        if (x.stars >= 5) com.yompix.novograd.life.Achievements.give(p, "stars5");
        if (x.stars > before) {
            p.sendMessage(Text.literal("★ Розыск: " + "★".repeat(x.stars) + " — " + why).formatted(Formatting.RED), true);
            p.playSoundToPlayer(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1f, 0.6f);
            Dispatch.wanted(p, why, x.stars); // сводка в полицейскую рацию
        }
        sync(p, x);
    }

    public static void clear(ServerPlayerEntity p) {
        W x = w(p);
        x.stars = 0;
        x.arrest = 0;
        sync(p, x);
        for (CitizenEntity c : p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(96), c -> c.chasing(p)))
            c.standDown();
    }

    private static void sync(ServerPlayerEntity p, W x) {
        NbtCompound n = new NbtCompound();
        n.putInt("stars", x.stars);
        n.putBoolean("seen", x.seen);
        LifeNet.send(p, "wanted", n);
    }

    public static boolean policeNear(ServerPlayerEntity p, double r) {
        return !p.getServerWorld().getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(r),
                c -> c.getRole() == CitizenEntity.Role.POLICE && c.isAlive()).isEmpty();
    }

    // ------------------------------------------------------------------ сбил человека машиной (CarEntity.runOver)
    /**
     * Видит ли полиция место: полицейский-житель в пределах r с прямой видимостью на цель
     * или патрульная машина с экипажем, от которой цель не закрыта стенами.
     */
    public static boolean policeSees(ServerWorld w, net.minecraft.entity.Entity target, double r) {
        var box = target.getBoundingBox().expand(r);
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, box, c -> c.getRole() == CitizenEntity.Role.POLICE && c.isAlive() && c != target)) {
            if (c.hasVehicle() ? clearLine(w, c.getEyePos(), target, c.getVehicle()) : c.canSee(target)) return true;
        }
        for (CarEntity car : w.getEntitiesByClass(CarEntity.class, box, car -> car.isPolice() && !car.wrecked() && car.hasNpcDriver() && car != target.getVehicle()))
            if (clearLine(w, car.getPos().add(0, 1.3, 0), target, car)) return true;
        return false;
    }

    private static boolean clearLine(ServerWorld w, Vec3d from, net.minecraft.entity.Entity target, net.minecraft.entity.Entity self) {
        Vec3d to = target.getPos().add(0, target.getHeight() * 0.7, 0);
        return w.raycast(new net.minecraft.world.RaycastContext(from, to, net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, self)).getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    private static final Map<UUID, long[]> STREAK = new HashMap<>();
    private static long lastHitNews = -100000;

    /**
     * Наезд на человека. Как в GTA: звезда только если это видела полиция (коп рядом или патрульная машина
     * в ~40 блоках и цель у них на виду). Сбил полицейского — розыск всегда, насмерть — больше звёзд.
     */
    public static void runOver(ServerPlayerEntity p, CarEntity car, net.minecraft.entity.LivingEntity victim, boolean killed) {
        ServerWorld w = p.getServerWorld();
        boolean cop = victim instanceof CitizenEntity c && c.getRole() == CitizenEntity.Role.POLICE;
        boolean gang = victim instanceof CitizenEntity c && c.getRole() == CitizenEntity.Role.GANG;
        boolean seen = !p.isCreative() && (cop || policeSees(w, victim, 40) || policeSees(w, car, 40));
        if (seen) {
            if (cop) add(p, killed ? 1 : 0, killed ? "сбил полицейского насмерть" : "наезд на полицейского", killed ? 4 : 2);
            else add(p, killed ? 1 : 0, killed ? "сбил человека насмерть" : "наезд на пешехода", killed ? 2 : 1);
        }
        // свидетели: прохожие разбегаются, полиция (если видела) — за тобой, друзья бандита — в бой
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, victim.getBoundingBox().expand(18), c -> c != victim && c.isAlive() && !c.hasVehicle())) {
            if (c.getRole() == CitizenEntity.Role.POLICE) {
                if (seen) c.attack(p);
            } else if (c.getRole() == CitizenEntity.Role.GANG && gang) c.attack(p);
            else if (!c.knockedDown()) c.panic(victim.getPos());
        }
        // новость о ДТП — изредка, не за каждого
        long now = w.getTime();
        if (killed && !cop && now - lastHitNews > 20 * 120 && w.getRandom().nextInt(3) == 0) {
            lastHitNews = now;
            String[] h = seen
                    ? new String[]{"ДТП в Новограде: водитель сбил пешехода, полиция начала погоню", "Наезд на пешехода на глазах у патруля — водитель скрывается"}
                    : new String[]{"Водитель сбил пешехода и скрылся с места ДТП. Полиция ищет свидетелей", "Смертельный наезд в Новограде: виновник уехал, номера никто не запомнил"};
            News.post(p.getServer(), h[w.getRandom().nextInt(h.length)], "ЭКСТРЕННО");
        } else if (cop && killed && now - lastHitNews > 20 * 60) {
            lastHitNews = now;
            News.post(p.getServer(), "Сбит полицейский: в городе объявлен план «Перехват»", "ЭКСТРЕННО");
        }
    }

    /** Серия за пару секунд: «Боулинг» — пятерых за раз. */
    public static void runOverStreak(ServerPlayerEntity p, int victims, int kills) {
        long now = p.getServerWorld().getTime();
        long[] s = STREAK.computeIfAbsent(p.getUuid(), u -> new long[2]);
        if (now - s[1] > 60) s[0] = 0;
        s[0] += victims;
        s[1] = now;
        if (s[0] >= 5) com.yompix.novograd.life.Achievements.give(p, "bowling");
    }

    /** Раз в секунду. */
    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            W x = ALL.get(p.getUuid());
            if (x == null || x.stars == 0) continue;
            ServerWorld w = p.getServerWorld();
            long now = w.getTime();
            var cops = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(48), c -> c.getRole() == CitizenEntity.Role.POLICE && c.isAlive());
            boolean seen = false;
            for (CitizenEntity c : cops) {
                double d = c.distanceTo(p);
                if (d < 30 && c.canSee(p)) seen = true;
                if (!c.chasing(p) && d < 48) c.attack(p);
            }
            if (!seen) seen = Pursuit.extraSeen(p, x.stars); // вертолёт с прожектором видит открытое место
            boolean was = x.seen;
            x.seen = seen;
            if (seen) x.lastSeen = now;
            // скрылся: звёзды гаснут по одной
            long hide = Pursuit.hideTicks(p, x.stars, (8 + x.stars * 4) * 20L); // вне зоны поиска — быстрее
            if (!seen && now - x.lastSeen > hide) {
                x.stars--;
                x.lastSeen = now;
                if (x.stars == 0) {
                    p.sendMessage(Text.literal("✔ Ты ушёл от полиции").formatted(Formatting.GREEN), true);
                    clear(p);
                    continue;
                }
            }
            if (was != seen || now % 40 == 0) sync(p, x);
            reinforce(p, x, cops.size());
            // вертолёт полиции с 4★: кружит, светит прожектором, стрелок бьёт очередями
            if (x.stars >= 4 && now % 100 == 0 && com.yompix.novograd.vehicle.HeliEntity.countPolice(w, p.getPos(), 200) < (x.stars >= 5 ? 2 : 1))
                com.yompix.novograd.vehicle.HeliEntity.spawnPolice(w, p.getPos(), p);
            if (x.stars >= 3 && p.getVehicle() instanceof CarEntity cv && (!(cv instanceof com.yompix.novograd.vehicle.MotorVehicle)) && now - x.lastBlock > 20 * 25) {
                if (roadblock(p)) x.lastBlock = now;
            }
            // задержание: полицейский вплотную, а ты стоишь (или машина стоит)
            boolean slow = p.getVelocity().horizontalLengthSquared() < 0.01 && (!(p.getVehicle() instanceof CarEntity car) || car.slow());
            boolean grabbed = cops.stream().anyMatch(c -> c.distanceTo(p) < 2.2 && !c.hasVehicle());
            x.arrest = grabbed && slow ? x.arrest + 1 : 0;
            if (x.arrest == 2) p.sendMessage(Text.literal("«Руки за голову!» — беги, иначе задержат!").formatted(Formatting.RED), true);
            if (x.arrest >= 4) busted(p);
        }
    }

    /** Подкрепление: пешие патрульные и машины с мигалками там, где игрок их не видит. */
    private static void reinforce(ServerPlayerEntity p, W x, int have) {
        ServerWorld w = p.getServerWorld();
        int want = Math.min(8, x.stars * 2);
        if (have >= want || ++x.spawnTick % 3 != 0) return;
        var r = w.getRandom();
        boolean car = x.stars >= 2 && r.nextInt(3) == 0;
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 26 + r.nextInt(14);
            int bx = (int) (p.getX() + Math.cos(a) * dist), bz = (int) (p.getZ() + Math.sin(a) * dist);
            int y = Story.topY(w, bx, bz);
            BlockPos at = new BlockPos(bx, y, bz);
            var below = w.getBlockState(at.down());
            boolean road = below.isOf(ModContent.ASPHALT) || below.isOf(ModContent.ASPHALT_LINE) || below.isOf(ModContent.SIDEWALK) || below.isOf(ModContent.CROSSWALK);
            if (!road || !w.isAir(at) || !w.isAir(at.up())) continue;
            if (Boolean.getBoolean("novograd.debuggta")) com.yompix.novograd.Novograd.LOG.info("[gta] reinforce {} car {}", at, car);
            if (car && below.isOf(ModContent.ASPHALT) || car && below.isOf(ModContent.ASPHALT_LINE)) {
                CarEntity c = new CarEntity(ModContent.CAR, w);
                c.refreshPositionAndAngles(bx + 0.5, y, bz + 0.5, (float) Math.toDegrees(Math.atan2(-(p.getX() - bx), p.getZ() - bz)), 0);
                c.setVariant(CarEntity.POLICE);
                w.spawnEntity(c);
                for (int i = 0; i < 2; i++) {
                    CitizenEntity cop = cop(w, bx + 0.5, y, bz + 0.5);
                    cop.startRiding(c, true);
                    cop.attack(p);
                }
                c.chase(p);
            } else {
                CitizenEntity cop = cop(w, bx + 0.5, y, bz + 0.5);
                cop.attack(p);
            }
            return;
        }
    }

    /** Блокпост на 3★ и выше: две патрульные машины поперёк улицы впереди, копы целятся. */
    private static boolean roadblock(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        var v = p.getVehicle().getVelocity();
        if (v.horizontalLengthSquared() < 0.04) return false;
        boolean alongX = Math.abs(v.x) > Math.abs(v.z);
        double sx = Math.signum(alongX ? v.x : v.z);
        int street = (int) (Math.round(((alongX ? p.getZ() : p.getX()) - 24) / 48.0) * 48 + 24);
        int ahead = (int) ((alongX ? p.getX() : p.getZ()) + sx * 55);
        int y = Story.topY(w, alongX ? ahead : street, alongX ? street : ahead);
        if (!w.getBlockState(new BlockPos(alongX ? ahead : street, y - 1, alongX ? street : ahead)).isOf(ModContent.ASPHALT)
                && !w.getBlockState(new BlockPos(alongX ? ahead : street, y - 1, alongX ? street : ahead)).isOf(ModContent.ASPHALT_LINE)) return false;
        for (int side : new int[]{-2, 2}) {
            double cx = alongX ? ahead + 0.5 : street + side + 0.5, cz = alongX ? street + side + 0.5 : ahead + 0.5;
            CarEntity c = new CarEntity(ModContent.CAR, w);
            c.refreshPositionAndAngles(cx, y, cz, alongX ? 0 : 90, 0); // поперёк дороги
            c.setVariant(CarEntity.POLICE);
            c.toggleLight(CarEntity.HAZARD);
            c.setAmbient(true);
            w.spawnEntity(c);
            double bx = alongX ? cx - sx * 2.5 : cx, bz = alongX ? cz : cz - sx * 2.5;
            CitizenEntity cop = cop(w, bx, y, bz);
            cop.attack(p);
        }
        p.sendMessage(Text.literal("Впереди полицейский блокпост!").formatted(Formatting.RED), true);
        return true;
    }

    private static CitizenEntity cop(ServerWorld w, double x, double y, double z) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        boolean swat = false;
        for (ServerPlayerEntity pl : w.getPlayers()) if (stars(pl) >= 5 && pl.squaredDistanceTo(x, y, z) < 80 * 80) swat = true;
        c.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.POLICE, false, swat ? "Спецназ" : "Полицейский");
        if (swat) {
            // спецназ: шлем, бронежилет, автомат
            c.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(net.minecraft.item.Items.NETHERITE_HELMET));
            c.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, new net.minecraft.item.ItemStack(net.minecraft.item.Items.NETHERITE_CHESTPLATE));
            c.refreshPositionAndAngles(x, y, z, 0, 0);
            c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(ModContent.SMG));
            c.removeWhenIdle();
            w.spawnEntity(c);
            return c;
        }
        c.refreshPositionAndAngles(x, y, z, 0, 0);
        c.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(ModContent.PISTOL));
        c.removeWhenIdle();
        w.spawnEntity(c);
        return c;
    }

    /** Задержан: штраф, оружие изъято, участок. */
    public static void busted(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        int starsAtBust = stars(p);
        int fine = Math.min(d.money, 50 + stars(p) * 60);
        d.money -= fine;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            var s = inv.getStack(i);
            if (Weapons.isGun(s) || s.isOf(ModContent.AMMO) || s.isOf(ModContent.BAT)) inv.setStack(i, net.minecraft.item.ItemStack.EMPTY);
        }
        p.stopRiding();
        clear(p);
        // не по сюжету — в тюрьму (gta/Prison): срок, взятка, адвокат или побег
        boolean jailed = !d.flags.contains("scripted_bust") && Prison.jail(p, starsAtBust, fine);
        if (!jailed) {
            Vec3d at = Story.place(p.getServerWorld(), "police_station");
            if (at == null) at = Story.place(p.getServerWorld(), "office_door");
            if (at != null) p.teleport(p.getServerWorld(), at.x, at.y, at.z, p.getYaw(), 0);
            NbtCompound n = new NbtCompound();
            n.putString("title", "ЗАДЕРЖАН");
            n.putString("sub", "Штраф $" + fine + ", оружие изъято");
            LifeNet.send(p, "gta_title", n);
        }
        Life.sync(p);
        if (!Life.get(p).flags.contains("scripted_bust")) com.yompix.novograd.story.Story.failMission(p, "Тебя задержали");
        Heists.onDownfall(p, true);
    }

    /** Погиб: больница, счёт за лечение. */
    public static void respawned(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        int bill = Math.min(d.money, 100);
        d.money -= bill;
        clear(p);
        Vec3d at = Story.place(p.getServerWorld(), "hospital");
        if (at == null) at = Story.place(p.getServerWorld(), "home_door");
        if (at != null) p.teleport(p.getServerWorld(), at.x, at.y, at.z, p.getYaw(), 0);
        NbtCompound n = new NbtCompound();
        n.putString("title", "ТЫ ПОГИБ");
        n.putString("sub", "Врачи спасли тебя. Счёт за лечение: $" + bill);
        LifeNet.send(p, "gta_title", n);
        Life.sync(p);
        com.yompix.novograd.story.Story.failMission(p, "Ты погиб");
        Heists.onDownfall(p, false);
    }
}
