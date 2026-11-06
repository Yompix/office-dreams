package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.world.Island;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
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
 * Свободная игра как в криминальных боевиках:
 *  • 50 тайников по острову (светящиеся посылки) — деньги и бонусы за каждые 10;
 *  • трюковые прыжки на машине — награда за время в воздухе;
 *  • подработки за рулём (клавиша J): такси — возить пассажиров, патруль на полицейской машине — ловить бандитов;
 *  • коды (/cheat …).
 */
public final class Activities {
    private Activities() {}

    // ------------------------------------------------------------------ тайники
    public static final int PACKAGES = 50;
    private static List<int[]> spots;

    /** Точки тайников: тротуары, пляж, порт, холмы, аэродром — одинаковые в каждом мире. */
    public static List<int[]> packages() {
        if (spots != null) return spots;
        spots = new ArrayList<>();
        Random r = new Random(777);
        int guard = 0;
        while (spots.size() < PACKAGES && guard++ < 20000) {
            int x = r.nextInt(1800) - 900, z = r.nextInt(1800) - 900;
            Island.Zone zn = Island.zone(x, z);
            if (zn == Island.Zone.SEA || zn == Island.Zone.CORE) continue;
            if (Island.urban(zn)) { // на тротуаре между перекрёстками
                x = snap(x) + 5;
                z = snap(z) + 18;
            }
            boolean far = true;
            for (int[] s : spots) if (Math.abs(s[0] - x) + Math.abs(s[1] - z) < 90) far = false;
            if (far) spots.add(new int[]{x, z});
        }
        return spots;
    }

    private static int snap(double v) {
        return (int) (Math.round((v - 24) / 48.0) * 48 + 24);
    }

    private static int found(LifeData d) {
        int n = 0;
        for (int i = 0; i < PACKAGES; i++) if (d.flags.contains("pkg:" + i)) n++;
        return n;
    }

    private static void tickPackages(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        List<int[]> l = packages();
        for (int i = 0; i < l.size(); i++) {
            int[] s = l.get(i);
            double dx = s[0] + 0.5 - p.getX(), dz = s[1] + 0.5 - p.getZ();
            if (dx * dx + dz * dz > 48 * 48 || d.flags.contains("pkg:" + i)) continue;
            String tag = "tainik_" + i;
            int y = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, s[0], s[1]);
            List<ItemEntity> have = w.getEntitiesByClass(ItemEntity.class, new net.minecraft.util.math.Box(s[0] - 3, y - 4, s[1] - 3, s[0] + 4, y + 4, s[1] + 4),
                    e -> e.getCommandTags().contains(tag));
            ItemEntity it;
            if (have.isEmpty()) {
                it = new ItemEntity(w, s[0] + 0.5, y + 0.3, s[1] + 0.5, new ItemStack(Items.CHEST));
                it.setVelocity(0, 0, 0);
                it.setPickupDelayInfinite();
                it.setNeverDespawn();
                it.setGlowing(true);
                it.addCommandTag(tag);
                w.spawnEntity(it);
            } else it = have.get(0);
            if (w.getTime() % 20 == 0) w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, it.getX(), it.getY() + 0.8, it.getZ(), 3, 0.3, 0.4, 0.3, 0);
            if (p.squaredDistanceTo(it) < 2.2 * 2.2) {
                it.discard();
                d.flags.add("pkg:" + i);
                int n = found(d);
                int pay = 250 + (n % 10 == 0 ? 2500 : 0);
                Life.earn(p, pay, "тайник " + n + "/" + PACKAGES);
                w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
                if (n >= PACKAGES) com.yompix.novograd.life.Achievements.give(p, "packages50");
                title(p, "ТАЙНИК " + n + "/" + PACKAGES, n % 10 == 0 ? "Бонус! +$" + pay + " и патроны" : "+$" + pay);
                if (n % 10 == 0) p.getInventory().insertStack(new ItemStack(ModContent.AMMO, 64));
                Life.sync(p);
            }
        }
    }

    // ------------------------------------------------------------------ трюки
    private static final Map<UUID, Integer> AIR = new HashMap<>();

    private static void tickStunts(ServerPlayerEntity p) {
        if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p) {
            AIR.remove(p.getUuid());
            return;
        }
        int air = AIR.getOrDefault(p.getUuid(), 0);
        if (!car.isOnGround() && !car.isTouchingWater()) {
            AIR.put(p.getUuid(), air + 1);
        } else {
            if (air >= 26) {
                int pay = air * 4;
                Life.earn(p, pay, "трюк");
                com.yompix.novograd.life.Achievements.give(p, "stunt");
                title(p, "ТРЮКОВОЙ ПРЫЖОК!", String.format("%.1f с в воздухе · +$%d", air / 20f, pay));
                Life.sync(p);
            }
            AIR.put(p.getUuid(), 0);
        }
    }

    // ------------------------------------------------------------------ подработки за рулём
    private static final class Job {
        String kind; // taxi | vigilante
        int level = 1, timer;
        @Nullable UUID who, car;
        @Nullable Vec3d dest;
        boolean riding;
        int outOfCar;
    }

    private static final Map<UUID, Job> JOBS = new HashMap<>();

    /** Клавиша J в машине: начать / закончить подработку. */
    public static void toggle(ServerPlayerEntity p) {
        Job j = JOBS.get(p.getUuid());
        if (j != null) {
            stop(p, "Подработка окончена.");
            return;
        }
        if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p) {
            p.sendMessage(Text.literal("Подработка: сядь за руль такси (жёлтая машина) или полицейской машины и нажми J").formatted(Formatting.YELLOW), true);
            return;
        }
        j = new Job();
        if (car.isTaxi()) j.kind = "taxi";
        else if (car.isPolice()) j.kind = "vigilante";
        else {
            p.sendMessage(Text.literal("Эта машина не подходит: нужно такси (жёлтое) или полицейская").formatted(Formatting.YELLOW), true);
            return;
        }
        JOBS.put(p.getUuid(), j);
        title(p, j.kind.equals("taxi") ? "ТАКСИСТ" : "ПАТРУЛЬ", j.kind.equals("taxi") ? "Вози пассажиров. J — закончить" : "Лови бандитов. J — закончить");
        next(p, j);
    }

    private static void stop(ServerPlayerEntity p, String why) {
        Job j = JOBS.remove(p.getUuid());
        if (j == null) return;
        ServerWorld w = p.getServerWorld();
        for (UUID u : new UUID[]{j.who, j.car}) {
            var e = u == null ? null : w.getEntity(u);
            if (e instanceof CitizenEntity c && j.kind.equals("taxi")) {
                c.stopRiding();
                c.removeLater(100);
            } else if (e != null && j.kind.equals("vigilante")) {
                for (var ps : e.getPassengerList()) ps.discard();
                e.discard();
            }
        }
        target(p, null, "");
        p.sendMessage(Text.literal(why + " Уровень: " + j.level).formatted(Formatting.GOLD), true);
    }

    /** Точка на тротуаре рядом (для такси). */
    private static Vec3d curb(ServerWorld w, double x, double z) {
        int sx = snap(x) + 5, sz = snap(z) + 16;
        return new Vec3d(sx + 0.5, Story.topY(w, sx, sz), sz + 0.5);
    }

    private static Vec3d around(ServerPlayerEntity p, int min, int max) {
        var r = p.getRandom();
        for (int i = 0; i < 30; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextInt(max - min);
            int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
            if (Island.urban(Island.zone(x, z))) return curb(p.getServerWorld(), x, z);
        }
        return curb(p.getServerWorld(), p.getX() + min, p.getZ());
    }

    private static void next(ServerPlayerEntity p, Job j) {
        ServerWorld w = p.getServerWorld();
        j.riding = false;
        if (j.kind.equals("taxi")) {
            Vec3d at = around(p, 40, 110);
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            c.setup(CitizenEntity.randomSkin(w.getRandom()), CitizenEntity.Role.CITIZEN, true, Names.random(w.getRandom()));
            c.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
            c.setCustomNameVisible(true);
            c.lockActivity(CitizenEntity.Activity.WAVE);
            w.spawnEntity(c);
            j.who = c.getUuid();
            j.dest = null;
            j.timer = 20 * 120;
            target(p, at, "Пассажир");
            p.sendMessage(Text.literal("🚕 Пассажир ждёт — голубая метка на радаре").formatted(Formatting.YELLOW), true);
        } else {
            Vec3d at = around(p, 70, 120);
            CarEntity car = new CarEntity(ModContent.CAR, w);
            car.refreshPositionAndAngles(at.x - 5, at.y, at.z - 16, 0, 0);
            car.setVariant(w.getRandom().nextInt(4));
            car.setTune(j.level > 3, Math.min(2, j.level / 4), 0);
            w.spawnEntity(car);
            for (int i = 0; i < 1 + Math.min(2, j.level / 3); i++) {
                CitizenEntity g = new CitizenEntity(ModContent.CITIZEN, w);
                g.setup(CitizenEntity.RANDOM_FROM + 39 - w.getRandom().nextInt(4) * 2, CitizenEntity.Role.GANG, false, "Бандит");
                g.refreshPositionAndAngles(car.getX(), car.getY(), car.getZ(), 0, 0);
                w.spawnEntity(g);
                g.startRiding(car, true);
            }
            List<Vec3d> route = new ArrayList<>();
            for (int k = 0; k < 8; k++) route.add(around(p, 150, 400));
            car.setRoute(route, Math.min(0.95f, 0.6f + j.level * 0.04f), null);
            j.car = car.getUuid();
            j.timer = 20 * (150 - Math.min(60, j.level * 5));
            p.sendMessage(Text.literal("🚔 Уровень " + j.level + ": бандиты на машине — красная метка. Разбей их машину!").formatted(Formatting.RED), true);
        }
    }

    private static void tickJob(ServerPlayerEntity p) {
        Job j = JOBS.get(p.getUuid());
        if (j == null) return;
        ServerWorld w = p.getServerWorld();
        boolean driving = p.getVehicle() instanceof CarEntity c && c.getControllingPassenger() == p;
        j.outOfCar = driving ? 0 : j.outOfCar + 1;
        if (j.outOfCar > 20 * 12) {
            stop(p, "Ты бросил машину — подработка окончена.");
            return;
        }
        if (--j.timer <= 0) {
            stop(p, "Время вышло!");
            return;
        }
        if (j.timer % 20 == 0) LifeNet.send(p, "act_timer", timer(j.timer / 20));
        if (j.kind.equals("taxi")) {
            if (!(w.getEntity(j.who) instanceof CitizenEntity c)) {
                next(p, j);
                return;
            }
            CarEntity car = driving ? (CarEntity) p.getVehicle() : null;
            if (!j.riding) {
                if (car != null && car.squaredDistanceTo(c) < 7 * 7 && car.slow()) {
                    c.lockActivity(null);
                    c.startRiding(car, true);
                    j.riding = true;
                    j.dest = around(p, 160, 420);
                    double dist = Math.sqrt(j.dest.squaredDistanceTo(p.getPos()));
                    j.timer = (int) (20 * (30 + dist / 6));
                    target(p, j.dest, "Куда везти");
                    p.sendMessage(Text.literal("🚕 «" + pick(w, "До центра, побыстрее!", "Мне на работу, опаздываю!", "Только не гони, ладно?", "В порт, пожалуйста.") + "»").formatted(Formatting.YELLOW), true);
                }
            } else if (car != null && j.dest != null && car.squaredDistanceTo(j.dest) < 9 * 9 && car.slow()) {
                c.stopRiding();
                c.removeLater(140);
                int pay = 40 + j.timer / 20 + j.level * 10;
                Life.earn(p, pay, "такси");
                title(p, "ПАССАЖИР ДОВЕЗЁН", "+$" + pay + " · заказ " + j.level);
                j.level++;
                Life.sync(p);
                next(p, j);
            }
        } else {
            var e = w.getEntity(j.car);
            if (!(e instanceof CarEntity car) || car.wrecked() || car.getPassengerList().isEmpty()) {
                // машину разбили или банда разбежалась — добить пеших не обязательно
                int pay = 150 * j.level;
                Life.earn(p, pay, "патруль");
                title(p, "ПРЕСТУПНИКИ ОСТАНОВЛЕНЫ", "+$" + pay + " · уровень " + j.level);
                if (e != null) for (var ps : e.getPassengerList()) if (ps instanceof CitizenEntity g) {
                    g.stopRiding();
                    g.attack(p);
                }
                j.level++;
                Life.sync(p);
                next(p, j);
                return;
            }
            target(p, car.getPos(), "Бандиты");
            if (car.getPos().squaredDistanceTo(p.getPos()) > 260 * 260) stop(p, "Бандиты скрылись.");
        }
    }

    private static String pick(ServerWorld w, String... s) {
        return s[w.getRandom().nextInt(s.length)];
    }

    private static NbtCompound timer(int sec) {
        NbtCompound n = new NbtCompound();
        n.putInt("sec", sec);
        return n;
    }

    private static void target(ServerPlayerEntity p, @Nullable Vec3d at, String label) {
        NbtCompound n = new NbtCompound();
        if (at != null) {
            n.putDouble("x", at.x);
            n.putDouble("z", at.z);
            n.putString("label", label);
        }
        LifeNet.send(p, "act_target", n);
    }

    public static void title(ServerPlayerEntity p, String title, String sub) {
        NbtCompound n = new NbtCompound();
        n.putString("title", title);
        n.putString("sub", sub);
        LifeNet.send(p, "gta_title", n);
    }

    // ------------------------------------------------------------------ коды
    public static String cheat(ServerPlayerEntity p, String code) {
        LifeData d = Life.get(p);
        ServerWorld w = p.getServerWorld();
        switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "BOGACH" -> { d.money += 50000; Life.sync(p); return "+$50 000"; }
            case "ZDOROV" -> { p.setHealth(p.getMaxHealth()); return "Здоровье полное"; }
            case "ARSENAL" -> {
                p.getInventory().insertStack(new ItemStack(ModContent.PISTOL));
                p.getInventory().insertStack(new ItemStack(ModContent.SMG));
                p.getInventory().insertStack(new ItemStack(ModContent.AMMO, 64));
                p.getInventory().insertStack(new ItemStack(ModContent.AMMO, 64));
                return "Оружие выдано";
            }
            case "TISHINA" -> { Wanted.clear(p); return "Розыск снят"; }
            case "HAOS" -> { Wanted.add(p, 5, "код ХАОС", 5); return "★★★★★"; }
            case "TACHKA" -> {
                CarEntity car = new CarEntity(ModContent.CAR, w);
                car.refreshPositionAndAngles(p.getX() + 3, p.getY(), p.getZ(), p.getYaw(), 0);
                car.setVariant(0);
                car.setTune(true, 2, 2);
                w.spawnEntity(car);
                return "Спорткар с нитро";
            }
            case "NOCH" -> { w.setTimeOfDay(18000); return "Ночь"; }
            case "DEN" -> { w.setTimeOfDay(6000); return "День"; }
            case "LIVEN" -> { w.setWeather(0, 6000, true, false); return "Ливень"; }
            case "SOLNCE" -> { w.setWeather(6000, 0, false, false); return "Солнце"; }
            default -> { return null; }
        }
    }

    // ------------------------------------------------------------------ недвижимость
    /** Название, x, z, цена. Купленное убежище — точка возрождения, там можно подлечиться. */
    private static final Object[][] PROPS = {
            {"Квартира в пригороде", -307, 262, 4000}, {"Домик у пляжа", -596, -600, 9000}, {"Лофт в промзоне", 461, 470, 7000},
            {"Пентхаус в центре", 461, -400, 25000}, {"Ранчо за городом", -390, -812, 12000}, {"Ангар на аэродроме", 470, -860, 30000},
    };
    private static final Map<UUID, Integer> INSIDE = new HashMap<>();

    /** Своё убежище в радиусе r блоков (там бесплатный гардероб, см. life/Style). */
    public static boolean nearOwnedSafehouse(ServerPlayerEntity p, double r) {
        LifeData d = Life.get(p);
        for (int i = 0; i < PROPS.length; i++) {
            if (!d.flags.contains("prop:" + i)) continue;
            double dx = (int) PROPS[i][1] + 0.5 - p.getX(), dz = (int) PROPS[i][2] + 0.5 - p.getZ();
            if (dx * dx + dz * dz <= r * r) return true;
        }
        return false;
    }

    private static void tickProps(ServerPlayerEntity p, LifeData d) {
        ServerWorld w = p.getServerWorld();
        int inside = -1;
        for (int i = 0; i < PROPS.length; i++) {
            int x = (int) PROPS[i][1], z = (int) PROPS[i][2], price = (int) PROPS[i][3];
            double dx = x + 0.5 - p.getX(), dz = z + 0.5 - p.getZ();
            if (dx * dx + dz * dz > 48 * 48) continue;
            boolean own = d.flags.contains("prop:" + i);
            String tag = "prop_" + i;
            int y = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            List<ItemEntity> have = w.getEntitiesByClass(ItemEntity.class, new net.minecraft.util.math.Box(x - 3, y - 4, z - 3, x + 4, y + 4, z + 4), e -> e.getCommandTags().contains(tag));
            ItemEntity it;
            if (have.isEmpty()) {
                it = new ItemEntity(w, x + 0.5, y + 0.3, z + 0.5, new ItemStack(Items.OAK_DOOR));
                it.setVelocity(0, 0, 0);
                it.setPickupDelayInfinite();
                it.setNeverDespawn();
                it.addCommandTag(tag);
                it.setCustomNameVisible(true);
                w.spawnEntity(it);
            } else it = have.get(0);
            it.setCustomName(Text.literal(own ? "🏠 " + PROPS[i][0] + " — твоё убежище" : "🏠 " + PROPS[i][0] + " — продаётся $" + price)
                    .formatted(own ? Formatting.GREEN : Formatting.GOLD));
            if (p.squaredDistanceTo(it) < 2.2 * 2.2) inside = i;
        }
        Integer was = INSIDE.put(p.getUuid(), inside);
        if (inside < 0 || was != null && was == inside) return;
        int price = (int) PROPS[inside][3];
        String name = (String) PROPS[inside][0];
        if (d.flags.contains("prop:" + inside)) {
            p.setHealth(p.getMaxHealth());
            p.setSpawnPoint(w.getRegistryKey(), p.getBlockPos(), p.getYaw(), true, false);
            title(p, "УБЕЖИЩЕ", name + " · здоровье восстановлено, игра сохранена");
        } else if (d.money >= price) {
            d.money -= price;
            d.flags.add("prop:" + inside);
            p.setSpawnPoint(w.getRegistryKey(), p.getBlockPos(), p.getYaw(), true, false);
            title(p, "НЕДВИЖИМОСТЬ КУПЛЕНА", name + " · −$" + price);
            w.playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1f);
            Life.sync(p);
        } else {
            p.sendMessage(Text.literal("🏠 " + name + ": нужно $" + price + ", у тебя $" + d.money).formatted(Formatting.YELLOW), true);
        }
    }

    // ------------------------------------------------------------------ тик
    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator()) continue;
            tickStunts(p);
            tickJob(p);
            if (t % 10 == 0) tickPackages(p, Life.get(p));
            if (t % 10 == 5) tickProps(p, Life.get(p));
        }
    }

    public static void onLeave(ServerPlayerEntity p) {
        stop(p, "");
        AIR.remove(p.getUuid());
    }
}
