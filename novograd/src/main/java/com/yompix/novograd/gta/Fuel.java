package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarBody;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.world.Island;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Бензин и заправки «Нефтяник».
 *  • Машина игрока жжёт бензин: полный бак — 15–20 минут езды (на холостых — больше часа). Служебные, трафик, скутер — без бензина.
 *  • Пустой бак — мотор глохнет, машина катится и встаёт (CarEntity.drive).
 *  • Заправка: заедь под навес к колонкам и остановись — бак наполняется, деньги списываются с карты (не хватает — наличными).
 *  • На каждой заправке за кассой кассир — его можно ограбить, как продавца в магазине (Heists: целься в продавца).
 *  • Погода: в дождь шины держат хуже (дольше тормозить, легче занос); вне асфальта — медленнее, багги наоборот быстрее.
 */
public final class Fuel {
    private Fuel() {}

    /** Как в world/Island.STATIONS: {ось (0 — трасса вдоль z на x=c, 1 — вдоль x на z=c), c, центр вдоль трассы, сторона}. */
    static final int[][] STATIONS = {{1, 744, -150, 1}, {0, 744, -400, 1}, {1, 24, 1100, -1}, {0, 24, 1300, -1}};
    /** Цена: $1 за 20 единиц (полный бак — $50). */
    public static final int UNITS_PER_DOLLAR = 20;

    /** Точка на заправке i: a — вдоль трассы от центра, b — от оси трассы (колонки — b=14, касса — b=22, a=−5). */
    public static Vec3d at(int i, double a, double b) {
        int[] st = STATIONS[i];
        double x = st[0] == 0 ? st[1] + b * st[3] : st[2] + a;
        double z = st[0] == 0 ? st[2] + a : st[1] + b * st[3];
        return new Vec3d(x + 0.5, Island.G + 1, z + 0.5);
    }

    public static Vec3d pumps(int i) {
        return at(i, 0, 14);
    }

    public static String name(int i) {
        return switch (i) {
            case 0 -> "«Нефтяник» у кольца (юг)";
            case 1 -> "«Нефтяник» на восточной трассе";
            case 2 -> "«Нефтяник» у Пустоши";
            default -> "«Нефтяник» на южной трассе";
        };
    }

    /** Ближайшая заправка к точке: номер. */
    public static int nearest(Vec3d p) {
        int best = 0;
        for (int i = 1; i < STATIONS.length; i++) if (pumps(i).squaredDistanceTo(p) < pumps(best).squaredDistanceTo(p)) best = i;
        return best;
    }

    // ------------------------------------------------------------------ расход (сервер, раз в секунду из CarEntity.tick)
    private static final Map<CarEntity, Float> ACC = new WeakHashMap<>();

    public static void carTick(CarEntity car) {
        if (!(car.getWorld() instanceof ServerWorld)) return;
        if (!(car.getControllingPassenger() instanceof ServerPlayerEntity p)) return;
        Impound.driven(car, p);
        if (!car.usesFuel() || p.isCreative() || car.wrecked()) return;
        double s = car.hitSpeed();
        float k = car.isBike() ? 0.6f : switch (car.body()) {
            case COMPACT -> 0.75f;
            case BUGGY -> 0.9f;
            case SUPERCAR, MUSCLE, TRUCK, LIMO -> 1.3f;
            case SUV, PICKUP, CLASSIC, LOWRIDER -> 1.12f;
            default -> 1f;
        };
        float use = (float) (0.22 + 0.82 * Math.min(2, s / 0.6)) * k * (car.nitroOn() ? 2 : 1);
        float acc = ACC.getOrDefault(car, 0f) + use;
        int whole = (int) acc;
        ACC.put(car, acc - whole);
        if (whole <= 0) return;
        int before = car.fuel();
        car.setFuel(before - whole);
        int now = car.fuel();
        int max = CarEntity.FUEL_MAX;
        if (before > max / 5 && now <= max / 5) hint(p, "⛽ Бензина меньше 20% — заправка: " + name(nearest(p.getPos())) + " (значок ⛽ на радаре)", Formatting.GOLD);
        else if (before > max / 15 && now <= max / 15) {
            hint(p, "⛽ Бак почти пуст!", Formatting.RED);
            p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.MASTER, 0.8f, 0.6f);
        } else if (before > 0 && now <= 0) {
            Activities.title(p, "БЕНЗИН КОНЧИЛСЯ", "Толкай до заправки или вызови такси · ближайшая: " + name(nearest(p.getPos())));
            car.getWorld().playSound(null, car.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.NEUTRAL, 0.6f, 0.6f);
        }
    }

    private static void hint(ServerPlayerEntity p, String s, Formatting f) {
        p.sendMessage(Text.literal(s).formatted(f), true);
    }

    // ------------------------------------------------------------------ дорога и погода (общие: клиент водителя и сервер)
    /** Множитель максималки по покрытию под колёсами: асфальт 1, песок/трава/снег — по кузову. */
    public static float surfaceFactor(CarEntity car) {
        if (!car.isOnGround()) return 1;
        BlockState b = car.getWorld().getBlockState(BlockPos.ofFloored(car.getX(), car.getY() - 0.2, car.getZ()));
        boolean soft = b.isIn(BlockTags.SAND) || b.isIn(BlockTags.DIRT) || b.isIn(BlockTags.TERRACOTTA) || b.isOf(Blocks.SNOW_BLOCK)
                || b.isOf(Blocks.SNOW) || b.isOf(Blocks.GRAVEL) || b.isOf(Blocks.POWDER_SNOW) || b.isOf(Blocks.SANDSTONE) || b.isOf(Blocks.RED_SANDSTONE);
        return soft ? car.body().offroad : 1;
    }

    /** Сцепление в дождь: 1 — сухо, меньше — скользко (на улице под открытым небом). */
    public static float wetGrip(CarEntity car) {
        var w = car.getWorld();
        if (!w.isRaining()) return 1;
        BlockPos up = BlockPos.ofFloored(car.getX(), car.getY() + 2, car.getZ());
        if (!w.isSkyVisible(up)) return 1;
        float k = w.isThundering() ? 0.55f : 0.65f;
        return car.body() == CarBody.BUGGY || car.body() == CarBody.SUV ? k + 0.12f : k;
    }

    // ------------------------------------------------------------------ заправка и кассир (сервер, каждые 10 тиков на игрока)
    private static final Map<java.util.UUID, Integer> PAID = new java.util.HashMap<>();

    static void tick(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d pos = p.getPos();
        int i = nearest(pos);
        Vec3d pump = pumps(i);
        double d2 = pump.squaredDistanceTo(pos.x, pump.y, pos.z);
        if (d2 < 60 * 60 && p.age % 40 < 10) clerk(w, i);
        CarEntity car = p.getVehicle() instanceof CarEntity c && c.getControllingPassenger() == p ? c : null;
        boolean at = car != null && car.usesFuel() && d2 < 8.5 * 8.5 && Math.abs(pos.y - pump.y) < 4;
        if (!at) {
            Integer paid = PAID.remove(p.getUuid());
            if (paid != null && paid > 0) hint(p, "⛽ Заправлено на $" + paid + ". Счастливого пути!", Formatting.GREEN);
            return;
        }
        int max = CarEntity.FUEL_MAX;
        if (car.fuel() >= max - 5) {
            if (!PAID.containsKey(p.getUuid())) {
                hint(p, "⛽ Бак полный", Formatting.GRAY);
                PAID.put(p.getUuid(), 0);
            }
            return;
        }
        if (!car.slow()) {
            if (p.age % 20 < 10) hint(p, "⛽ " + name(i) + ": остановись у колонки — заправим", Formatting.YELLOW);
            return;
        }
        int add = Math.min(max - car.fuel(), 60);
        int cost = Math.max(1, (add + UNITS_PER_DOLLAR - 1) / UNITS_PER_DOLLAR);
        LifeData d = Life.get(p);
        if (d.money >= cost) d.money -= cost;
        else if (d.dirty >= cost) d.dirty -= cost;
        else {
            hint(p, "⛽ Нечем платить за бензин", Formatting.RED);
            return;
        }
        d.spent += cost;
        car.setFuel(car.fuel() + add);
        int paid = PAID.merge(p.getUuid(), cost, Integer::sum);
        w.playSound(null, car.getBlockPos(), SoundEvents.ITEM_BUCKET_FILL, SoundCategory.NEUTRAL, 0.5f, 1.4f);
        hint(p, "⛽ Заправка… " + car.fuel() * 100 / max + "% · $" + paid, Formatting.AQUA);
        if (p.age % 40 < 10) Life.sync(p);
        if (car.fuel() >= max - 5) {
            Life.sync(p);
            if (w.getRandom().nextInt(3) == 0) Economy.shock(p.getServer(), "NITR", 0.002, "");
        }
    }

    /** Кассир за стойкой магазина заправки (появляется, когда игрок рядом; в старых мирах тоже). */
    private static void clerk(ServerWorld w, int i) {
        Vec3d c = at(i, -5, 23.2);
        if (!w.isChunkLoaded(BlockPos.ofFloored(c))) return;
        String tag = "nv_fuel_clerk" + i;
        var box = new net.minecraft.util.math.Box(c, c).expand(12, 6, 12);
        if (!w.getEntitiesByClass(CitizenEntity.class, box, e -> e.getCommandTags().contains(tag)).isEmpty()) return;
        int y = Island.surface((int) Math.floor(c.x), (int) Math.floor(c.z)) + 1;
        if (!w.getBlockState(BlockPos.ofFloored(c.x, y, c.z)).isAir()) return; // магазина тут нет (другая генерация)
        CitizenEntity clerk = new CitizenEntity(ModContent.CITIZEN, w);
        String[] names = {"Кассир Галя", "Кассир Вован", "Кассир Зоя", "Кассир Тимур"};
        clerk.setup(CitizenEntity.RANDOM_FROM + 7 + i * 3, CitizenEntity.Role.SHOPKEEPER, true, names[i % names.length]);
        Vec3d pump = pumps(i);
        float yaw = (float) Math.toDegrees(Math.atan2(-(pump.x - c.x), pump.z - c.z));
        clerk.refreshPositionAndAngles(c.x, y, c.z, yaw, 0);
        clerk.addCommandTag(tag);
        w.spawnEntity(clerk);
    }

    /** Для автотеста и отладки. */
    @Nullable
    public static CarEntity car(ServerPlayerEntity p) {
        return p.getVehicle() instanceof CarEntity c ? c : null;
    }
}
