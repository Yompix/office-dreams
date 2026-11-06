package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CarBody;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * «Аукцион угнанных машин» в разборке (chop_shop): пригони чужую машину в ворота — покупатели «из своих» торгуются за неё.
 *  • Цена — по редкости и состоянию; сегодня в цене один тип (видно в телефоне → «Авто» → «Аукцион»).
 *  • Деньги — грязными наличными. Риск: кто-то стучит в полицию — розыск и наряд к разборке (редкие машины — чаще).
 *  • Своих машин, такси с пассажиром и машин миссий не берут. Один лот в 2 минуты.
 */
public final class Auction {
    private Auction() {}

    public static final String GATE = "chop_shop+0,8";
    static final String[] BUYERS = {"Хромой Сэм", "Армянин Гриша", "Близнецы Ли", "Мадам Роза", "Дядя Витя", "Курьер из порта"};
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<UUID, int[]> RESULT = new HashMap<>(); // игрок → {цена, тик выплаты, стук}

    /** Базовая цена по типу кузова: обычные дёшево, редкие дорого. */
    public static int base(int variant) {
        return switch (CarBody.of(variant)) {
            case SUPERCAR -> 9000;
            case CLASSIC -> 5200;
            case LOWRIDER -> 4200;
            case LIMO -> 3800;
            case SPORTS -> 3000;
            case MUSCLE -> 2800;
            case LUXURY -> 2600;
            case SWAT -> 4800;
            case POLICE -> 2400;
            case BUGGY -> 2000;
            case SUV -> 1700;
            case AMBULANCE, FIRETRUCK -> 1600;
            case PICKUP -> 1300;
            case TRUCK -> 1500;
            case VAN -> 1000;
            case TAXI -> 700;
            case COMPACT -> 600;
            default -> 900;
        };
    }

    /** Тип машины, за который сегодня дают +50%. */
    public static int hot(ServerWorld w) {
        int[] pool = {2, 3, 7, 9, 10, CarEntity.CLASSIC, CarEntity.LOWRIDER, CarEntity.BUGGY, 8};
        return pool[(int) Math.floorMod(w.getTimeOfDay() / 24000L * 7919L, pool.length)];
    }

    static int offer(ServerWorld w, CarEntity car) {
        double k = 0.35 + 0.65 * car.health() / 100.0;
        if (car.getVariant() == hot(w)) k *= 1.5;
        if (car.tune() != 0) k *= 1.15;
        return (int) (base(car.getVariant()) * k / 10) * 10;
    }

    static boolean sellable(ServerPlayerEntity p, CarEntity car) {
        return !(car instanceof com.yompix.novograd.vehicle.MotorVehicle) && !car.isScooter() && !car.wrecked()
                && !p.getUuid().equals(Impound.ownerOf(car)) && car.getCommandTags().stream().noneMatch(t -> t.equals("nv_betrace") || t.equals("nv_rent") || t.startsWith("nv_owned:"));
    }

    static void tickPlayer(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, GATE);
        if (at == null || p.squaredDistanceTo(at) > 60 * 60) return;
        Vec3d m = at;
        if (p.age % 40 < 10)
            FunUtil.marker(w, m, "nv_auction", new ItemStack(Items.NAME_TAG), Text.literal("⚒ Аукцион угнанных машин").formatted(Formatting.RED), true);
        DriveHooks.walkIn(p, "auction", m, 4.5, () -> {
            NbtCompound n = DriveHooks.state(p);
            n.putString("open", "auction");
            putLot(p, n);
            LifeNet.send(p, "drv_open", n);
        });
        int[] res = RESULT.get(p.getUuid());
        if (res != null && w.getTime() >= res[1]) payout(p, res);
    }

    static void putLot(ServerPlayerEntity p, NbtCompound n) {
        ServerWorld w = p.getServerWorld();
        n.putString("hot", Impound.name(hot(w)));
        if (p.getVehicle() instanceof CarEntity car && car.getControllingPassenger() == p) {
            n.putString("car", Impound.name(car.getVariant()));
            n.putInt("hp", car.health());
            n.putBoolean("ok", sellable(p, car));
            n.putInt("est", offer(w, car));
            n.putInt("risk", risk(car));
        }
        long cd = COOLDOWN.getOrDefault(p.getUuid(), 0L) - w.getTime();
        if (cd > 0) n.putInt("cd", (int) (cd / 20));
    }

    static int risk(CarEntity car) {
        int base = base(car.getVariant());
        return Math.min(60, 12 + base / 400 + (car.isPolice() || car.body() == CarBody.SWAT ? 25 : 0));
    }

    /** Выставить машину, в которой сидишь, на торги. Клиенту уходят ставки покупателей (анимация), деньги — после. */
    public static void sell(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d at = Story.place(w, GATE);
        if (at == null || p.squaredDistanceTo(at) > 14 * 14 || RESULT.containsKey(p.getUuid())) return;
        if (!(p.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != p) {
            p.sendMessage(Text.literal("Загони машину в ворота разборки").formatted(Formatting.RED), true);
            return;
        }
        if (!sellable(p, car)) {
            p.sendMessage(Text.literal("Скупщик: «Эту не возьмём. Свою продавай в салоне.»").formatted(Formatting.GOLD), false);
            return;
        }
        long now = w.getTime();
        if (now < COOLDOWN.getOrDefault(p.getUuid(), 0L)) {
            p.sendMessage(Text.literal("Скупщик: «Не так часто, засветишь нас. Через пару минут.»").formatted(Formatting.GOLD), true);
            return;
        }
        int est = offer(w, car);
        var r = w.getRandom();
        // торги: 3–6 ставок от покупателей, финальная — от 85% до 135% оценки
        NbtList bids = new NbtList();
        int price = (int) (est * (0.55 + r.nextFloat() * 0.15));
        int n = 3 + r.nextInt(4);
        int target = (int) (est * (0.85 + r.nextFloat() * 0.5));
        for (int i = 0; i < n; i++) {
            price = i == n - 1 ? Math.max(price + 50, target) : price + (target - price) / (n - i) + r.nextInt(80);
            price = price / 10 * 10;
            NbtCompound b = new NbtCompound();
            b.putString("who", BUYERS[r.nextInt(BUYERS.length)]);
            b.putInt("v", price);
            bids.add(b);
        }
        boolean snitch = r.nextInt(100) < risk(car);
        NbtCompound out = new NbtCompound();
        out.putString("car", Impound.name(car.getVariant()));
        out.put("bids", bids);
        out.putInt("final", price);
        LifeNet.send(p, "drv_auction", out);
        p.stopRiding();
        for (var e : new java.util.ArrayList<>(car.getPassengerList())) if (!(e instanceof ServerPlayerEntity)) e.discard();
        car.discard();
        COOLDOWN.put(p.getUuid(), now + 20 * 120);
        RESULT.put(p.getUuid(), new int[]{price, (int) (now + 20 * (2 + n * 1.6)), snitch ? 1 : 0});
        if (r.nextInt(2) == 0) Economy.shock(p.getServer(), "NITR", -0.01, "");
    }

    private static void payout(ServerPlayerEntity p, int[] res) {
        RESULT.remove(p.getUuid());
        Economy.dirty(p, res[0], "аукцион угнанных машин");
        FunUtil.title(p, "ПРОДАНО!", "+$" + res[0] + " наличными" + (res[2] == 1 ? " · кто-то настучал копам!" : ""));
        LifeData d = Life.get(p);
        FunUtil.setInt(d, "auction_sold", FunUtil.getInt(d, "auction_sold") + 1);
        if (res[2] == 1) raid(p);
    }

    /** Наводка: розыск и патруль у разборки. */
    private static void raid(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Wanted.add(p, 2, "наводка: торговля угнанными машинами", 2);
        Vec3d at = Story.place(w, "chop_shop+18,14");
        if (at == null) return;
        for (int i = 0; i < 2; i++) {
            Vec3d s = at.add(i * 2.5, 0, 0);
            CitizenEntity cop = FunUtil.citizen(w, new Vec3d(s.x, Story.topY(w, (int) Math.floor(s.x), (int) Math.floor(s.z)), s.z), CitizenEntity.Role.POLICE, null);
            cop.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(com.yompix.novograd.ModContent.PISTOL));
            cop.removeWhenIdle();
            cop.attack(p);
        }
        if (w.getRandom().nextInt(2) == 0) News.post(p.getServer(), "Полиция нагрянула в разборку на промзоне: ищут угнанные машины", "КРИМИНАЛ");
    }

    static void tick(MinecraftServer s) {
    }

    public static void onLeave(ServerPlayerEntity p) {
        int[] res = RESULT.remove(p.getUuid());
        if (res != null) Economy.dirty(p, res[0], "аукцион");
    }
}
