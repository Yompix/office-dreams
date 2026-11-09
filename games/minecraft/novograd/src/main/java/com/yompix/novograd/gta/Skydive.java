package com.yompix.novograd.gta;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.item.ParachuteItem;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.vehicle.HeliEntity;
import com.yompix.novograd.vehicle.PlaneEntity;
import com.yompix.novograd.world.Island;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;

/**
 * Парашют и прыжки, как в GTA SA:
 *  • парашют (предмет) — у оружейника «Калибр», на крышах небоскрёбов делового центра, в кабине вертолёта и самолёта;
 *  • в свободном падении — Пробел: купол раскрывается, W/S — скорость, A/D — поворот (физику считает клиент, см. FunClient);
 *  • парашютный клуб на аэродроме: прыжок на точность за $150 — тебя поднимают над мишенью, чем ближе к центру, тем больше приз.
 */
public final class Skydive {
    private Skydive() {}

    public static Item PARACHUTE;

    public static void register() {
        PARACHUTE = Registry.register(Registries.ITEM, Novograd.id("parachute"), new ParachuteItem(new Item.Settings().maxCount(1)));
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ITEM_GROUP, Novograd.id("main")))
                .register(e -> e.add(PARACHUTE));
    }

    private static final class Chute {
        boolean open;
        @Nullable Vec3d target;
        String targetName = "";
        int timer;
        long openedAt;
    }

    private static final Map<UUID, Chute> STATE = new HashMap<>();

    /** Прыжок на точность идёт — у радара одна голубая метка. */
    public static boolean busy(ServerPlayerEntity p) {
        Chute c = STATE.get(p.getUuid());
        return c != null && c.target != null;
    }

    public static boolean has(ServerPlayerEntity p) {
        return p.getInventory().count(PARACHUTE) > 0;
    }

    /** Раскрыть купол (клиент: Пробел в падении; или ПКМ парашютом). */
    public static boolean open(ServerPlayerEntity p) {
        Chute c = STATE.computeIfAbsent(p.getUuid(), u -> new Chute());
        if (c.open || p.isOnGround() || p.hasVehicle() || p.isTouchingWater() || !has(p)) return false;
        if (p.getVelocity().y > 0.1) return false;
        c.open = true;
        c.openedAt = p.getServerWorld().getTime();
        if (!p.isCreative()) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(PARACHUTE)) {
                inv.getStack(i).decrement(1);
                break;
            }
        }
        p.fallDistance = 0;
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_ELYTRA.value(), SoundCategory.PLAYERS, 1.2f, 0.7f);
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1f, 0.6f);
        sync(p, true);
        FunUtil.hint(p, "Купол раскрыт · W — быстрее, S — тормоз, A/D — поворот", Formatting.AQUA);
        return true;
    }

    private static void sync(ServerPlayerEntity p, boolean on) {
        NbtCompound n = new NbtCompound();
        n.putInt("id", p.getId());
        n.putBoolean("on", on);
        for (ServerPlayerEntity o : p.getServerWorld().getPlayers()) if (o.squaredDistanceTo(p) < 320 * 320) LifeNet.send(o, "chute", n);
    }

    private static void land(ServerPlayerEntity p, Chute c) {
        c.open = false;
        p.fallDistance = 0;
        sync(p, false);
        ServerWorld w = p.getServerWorld();
        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 1f, 0.8f);
        Achievements.give(p, "skydive");
        if (c.target == null) return;
        double d = Math.hypot(p.getX() - c.target.x, p.getZ() - c.target.z);
        int pay = d < 3 ? 1000 : d < 8 ? 500 : d < 20 ? 200 : 0;
        if (pay > 0) {
            Life.earn(p, pay, "прыжок на точность");
            FunUtil.title(p, d < 3 ? "В ЯБЛОЧКО!" : "ХОРОШЕЕ ПРИЗЕМЛЕНИЕ", String.format("%.1f м от центра · +$%d", d, pay));
            if (d < 3) {
                Achievements.give(p, "bullseye");
                if (Island.zone((int) c.target.x, (int) c.target.z) == Island.Zone.RIVIERA)
                    Economy.shock(p.getServer(), "RIVH", 0.02, "туристы в восторге от парашютистов над пляжем");
            }
        } else FunUtil.title(p, "МИМО МИШЕНИ", String.format("%.0f м от центра · попробуй ещё", d));
        c.target = null;
        FunUtil.clearHud(p);
    }

    // ------------------------------------------------------------------ клуб и мишени
    private static final String[][] TARGETS = {{"airfield", "Аэродром"}, {"beach_bar", "Пляжный бар"}, {"downtown_plaza", "Площадь делового центра"},
            {"hill_top", "Вершина холма"}, {"riv_beach", "Пляж Ривьеры"}, {"lake_dock", "Озеро Ясное"}, {"port_dock", "Причал порта"}, {"village", "Сосновка"}};
    private static final String CLUB = "airfield_hangar+16,10";
    private static final Map<UUID, Boolean> AT_CLUB = new HashMap<>(), AT_SHOP = new HashMap<>();

    /** Прыжок на точность: поднимаем над мишенью (против ветра), выдаём парашют. */
    public static boolean challenge(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        var r = w.getRandom();
        String[] t = TARGETS[r.nextInt(TARGETS.length)];
        Vec3d at = Story.place(w, t[0]);
        if (at == null) return false;
        double a = r.nextDouble() * Math.PI * 2;
        double sx = at.x + Math.cos(a) * 110, sz = at.z + Math.sin(a) * 110;
        int ground = Math.max(Island.surface((int) sx, (int) sz), Island.G);
        w.getChunk((int) Math.floor(sx) >> 4, (int) Math.floor(sz) >> 4);
        Chute c = STATE.computeIfAbsent(p.getUuid(), u -> new Chute());
        c.target = at;
        c.targetName = t[1];
        c.timer = 20 * 150;
        c.openedAt = 0;
        if (!has(p)) p.getInventory().insertStack(new ItemStack(PARACHUTE));
        p.stopRiding();
        p.teleport(w, sx, Math.min(300, ground + 210), sz, FunUtil.yawTo(new Vec3d(sx, 0, sz), at), 35);
        p.fallDistance = 0;
        FunUtil.title(p, "ПРЫЖОК НА ТОЧНОСТЬ", "Мишень: " + t[1] + " · Пробел — раскрыть купол");
        FunUtil.target(p, at, "Мишень");
        return true;
    }

    private static void tickClub(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        // клуб на аэродроме
        Vec3d club = Story.place(w, CLUB);
        if (club != null && p.squaredDistanceTo(club) < 48 * 48) {
            ItemEntity it = FunUtil.marker(w, club, "chute_club", new ItemStack(PARACHUTE),
                    Text.literal("Парашютный клуб: прыжок на точность — $150").formatted(Formatting.AQUA), true);
            boolean inside = p.squaredDistanceTo(it) < 2.2 * 2.2;
            Boolean was = AT_CLUB.put(p.getUuid(), inside);
            if (inside && (was == null || !was)) {
                if (FunUtil.busy(p) && !busy(p)) FunUtil.hint(p, "Сначала закончи текущее дело", Formatting.YELLOW);
                else if (!FunUtil.freeRoam(p)) FunUtil.hint(p, "Клуб примет тебя после миссии", Formatting.GRAY);
                else if (Life.pay(p, 150, "прыжок с парашютом")) challenge(p);
            }
        }
        // парашют у оружейника
        Vec3d shop = Story.place(w, "gunshop_door+3,3");
        if (shop != null && p.squaredDistanceTo(shop) < 40 * 40) {
            ItemEntity it = FunUtil.marker(w, shop, "chute_shop", new ItemStack(PARACHUTE), Text.literal("Парашют — $300").formatted(Formatting.GOLD), false);
            boolean inside = p.squaredDistanceTo(it) < 2 * 2;
            Boolean was = AT_SHOP.put(p.getUuid(), inside);
            if (inside && (was == null || !was)) {
                if (has(p)) FunUtil.hint(p, "Парашют у тебя уже есть", Formatting.GRAY);
                else if (Economy.payBlack(p, 300, "парашют")) {
                    p.getInventory().insertStack(new ItemStack(PARACHUTE));
                    FunUtil.hint(p, "Парашют куплен. Прыгай с крыши или вертолёта, в падении — Пробел", Formatting.AQUA);
                    if (d.flags.add("tips_chute")) Life.message(p, "Оружейник", "Парашют в деле не подведёт. Крыши небоскрёбов, вертолёт — а в клубе на аэродроме платят за меткое приземление.");
                }
            }
        }
        roofs(p, d);
    }

    /** На крышах высоких башен делового центра лежат парашюты (раз в день). */
    private static void roofs(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        Island.Zone zn = Island.zone(p.getBlockX(), p.getBlockZ());
        if (zn != Island.Zone.DOWNTOWN && zn != Island.Zone.RIVIERA) return;
        long day = w.getTimeOfDay() / 24000;
        int cx = (int) Math.round(p.getX() / 48.0) * 48, cz = (int) Math.round(p.getZ() / 48.0) * 48;
        for (int dx = -48; dx <= 48; dx += 48)
            for (int dz = -48; dz <= 48; dz += 48) {
                int x = cx + dx, z = cz + dz;
                if (Island.zone(x, z) != zn) continue;
                String key = "chute_roof:" + x + "," + z;
                if (FunUtil.getLong(d, key) == day) continue;
                int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (top - Island.surface(x, z) < 28) continue;
                Vec3d at = new Vec3d(x + 0.5, top, z + 0.5);
                if (p.squaredDistanceTo(at) > 40 * 40) continue;
                ItemEntity it = FunUtil.marker(w, at, "chute_roof", new ItemStack(PARACHUTE), Text.literal("Парашют").formatted(Formatting.AQUA), true);
                if (p.squaredDistanceTo(it) < 1.8 * 1.8) {
                    it.discard();
                    FunUtil.setLong(d, key, day);
                    p.getInventory().insertStack(new ItemStack(PARACHUTE));
                    w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
                    FunUtil.title(p, "ПАРАШЮТ", "Прыгай с крыши — в падении жми Пробел");
                }
            }
    }

    // ------------------------------------------------------------------ тик
    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            Chute c = STATE.get(p.getUuid());
            if (c != null && c.open) {
                p.fallDistance = 0;
                if ((p.isOnGround() || p.isTouchingWater() || p.hasVehicle() || p.isDead()) && p.getServerWorld().getTime() - c.openedAt > 5) land(p, c);
            }
            if (c != null && c.target != null && t % 10 == 4) {
                c.timer -= 10;
                FunUtil.timer(p, c.timer / 20);
                rings(p, c.target);
                if (c.timer <= 0) {
                    FunUtil.title(p, "ПРЫЖОК НЕ ЗАСЧИТАН", "Время вышло");
                    c.target = null;
                    FunUtil.clearHud(p);
                } else if (p.isOnGround() && !c.open && c.openedAt == 0 && c.timer < 20 * 146) { // приземлился, так и не раскрыв купол
                    FunUtil.title(p, "ПРЫЖОК НЕ ЗАСЧИТАН", "Купол нужно раскрыть в воздухе — Пробел");
                    c.target = null;
                    FunUtil.clearHud(p);
                }
            }
            if (t % 10 == 8) {
                tickClub(p, Life.get(p));
                // в кабине вертолёта и самолёта всегда есть парашют
                if ((p.getVehicle() instanceof HeliEntity || p.getVehicle() instanceof PlaneEntity) && !has(p)) {
                    p.getInventory().insertStack(new ItemStack(PARACHUTE));
                    FunUtil.hint(p, "В кабине нашёлся парашют — можно прыгать (в падении Пробел)", Formatting.AQUA);
                }
            }
        }
    }

    /** Мишень — три цветных кольца, видные издалека. */
    private static void rings(ServerPlayerEntity p, Vec3d at) {
        ServerWorld w = p.getServerWorld();
        double y = Story.topY(w, (int) Math.floor(at.x), (int) Math.floor(at.z)) + 0.2;
        float[][] col = {{1f, 0.15f, 0.15f}, {1f, 1f, 1f}, {1f, 0.8f, 0.1f}};
        double[] rad = {3, 8, 20};
        for (int k = 0; k < 3; k++) {
            int n = (int) (rad[k] * 3);
            DustParticleEffect dust = new DustParticleEffect(new Vector3f(col[k][0], col[k][1], col[k][2]), k == 2 ? 3f : 2f);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                w.spawnParticles(p, dust, true, at.x + Math.cos(a) * rad[k], y, at.z + Math.sin(a) * rad[k], 1, 0, 0, 0, 0);
            }
        }
        w.spawnParticles(p, net.minecraft.particle.ParticleTypes.END_ROD, true, at.x, y + 2, at.z, 6, 0.1, 2, 0.1, 0.01);
    }

    public static void onLeave(ServerPlayerEntity p) {
        STATE.remove(p.getUuid());
        AT_CLUB.remove(p.getUuid());
        AT_SHOP.remove(p.getUuid());
    }

    public static void onDownfall(ServerPlayerEntity p) {
        Chute c = STATE.get(p.getUuid());
        if (c != null) {
            if (c.open) sync(p, false);
            c.open = false;
            c.target = null;
        }
    }

    public static boolean isOpen(ServerPlayerEntity p) {
        Chute c = STATE.get(p.getUuid());
        return c != null && c.open;
    }
}
