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

    /** +add звёзд (не меньше min). */
    public static void add(ServerPlayerEntity p, int add, String why, int min) {
        W x = w(p);
        int before = x.stars;
        x.stars = Math.min(5, Math.max(min, x.stars + add));
        x.lastSeen = p.getServerWorld().getTime();
        x.seen = true;
        if (x.stars > before) {
            p.sendMessage(Text.literal("★ Розыск: " + "★".repeat(x.stars) + " — " + why).formatted(Formatting.RED), true);
            p.playSoundToPlayer(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 1f, 0.6f);
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
            boolean was = x.seen;
            x.seen = seen;
            if (seen) x.lastSeen = now;
            // скрылся: звёзды гаснут по одной
            long hide = (8 + x.stars * 4) * 20L;
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
            if (x.arrest == 2) p.sendMessage(Text.literal("🚔 «Руки за голову!» — беги, иначе задержат!").formatted(Formatting.RED), true);
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
        p.sendMessage(Text.literal("🚧 Впереди полицейский блокпост!").formatted(Formatting.RED), true);
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
        int fine = Math.min(d.money, 50 + stars(p) * 60);
        d.money -= fine;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            var s = inv.getStack(i);
            if (Weapons.isGun(s) || s.isOf(ModContent.AMMO) || s.isOf(ModContent.BAT)) inv.setStack(i, net.minecraft.item.ItemStack.EMPTY);
        }
        p.stopRiding();
        clear(p);
        Vec3d at = Story.place(p.getServerWorld(), "police_station");
        if (at == null) at = Story.place(p.getServerWorld(), "office_door");
        if (at != null) p.teleport(p.getServerWorld(), at.x, at.y, at.z, p.getYaw(), 0);
        NbtCompound n = new NbtCompound();
        n.putString("title", "ЗАДЕРЖАН");
        n.putString("sub", "Штраф $" + fine + ", оружие изъято");
        LifeNet.send(p, "gta_title", n);
        Life.sync(p);
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
    }
}
