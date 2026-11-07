package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.story.Story;
import com.yompix.novograd.vehicle.HeliEntity;
import com.yompix.novograd.vehicle.MotorVehicle;
import com.yompix.novograd.vehicle.PlaneEntity;
import com.yompix.novograd.vehicle.SpeedboatEntity;
import com.yompix.novograd.vehicle.Vehicles;
import com.yompix.novograd.world.Island;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Контрабанда: забери груз в порту (катер) или на аэродроме (самолёт) и доставь покупателю — на воде у причалов
 * или сбросом с самолёта над точкой. Чем ценнее груз, тем вероятнее береговая охрана: полицейские катера со стрелками
 * и вертолёт с прожектором. Оплата грязными, без хвоста — +20%. Открывается с акта II.
 */
public final class Smuggling {
    private Smuggling() {}

    public static final int ACT = 1;

    /** Груз: название, цена от/до, риск 0–2. */
    private record Cargo(String name, int min, int max, int risk) {}

    private static final Cargo[] CARGO = {
            new Cargo("Сигареты", 6000, 9000, 0), new Cargo("Алкоголь", 7000, 10000, 0), new Cargo("Электроника", 10000, 14000, 1),
            new Cargo("Часы", 13000, 18000, 1), new Cargo("Лекарства", 11000, 15000, 1), new Cargo("Картины", 16000, 22000, 2),
            new Cargo("Оружие", 20000, 28000, 2),
    };
    public static final String[] RISK = {"низкий", "средний", "высокий"};

    /** Покупатели у воды (катер) и точки сброса (самолёт): место, название, кто ждёт. */
    private static final String[][] SEA = {
            {"riv_marina", "Марина Ривьеры", "яхтсмен «Капитан Флинт»"}, {"riv_docks", "Доки Ривьеры", "грузчики Самвела"},
            {"riv_beach", "Пляж Ривьеры", "катер «Скат»"}, {"beach_bar", "Пляжный бар", "бармен Гоша"}, {"@-700,-460", "Маяк", "смотритель маяка"},
    };
    private static final String[][] AIR = {
            {"desert_mesa", "Плато в Пустоши", "люди Пустоши"}, {"village_farm", "Ферма у Сосновки", "фермер Кузьмич"},
            {"mtn_road", "Горная дорога", "лесорубы"}, {"outer_se", "Юго-восток острова", "байкеры"}, {"farm", "Ферма на севере", "агроном Лида"},
    };

    private static final class Job {
        int cargo, buyer, pay, minutes;
        boolean plane;
    }

    private static final class Run {
        Job j;
        int stage;
        long deadline, guardAt;
        boolean guards, chased, fast;
        @Nullable UUID vehicle;
        final List<UUID> police = new ArrayList<>();
        Vec3d drop;
        @Nullable Vec3d lastPos;
    }

    private static final Map<UUID, List<Job>> OFFERS = new HashMap<>();
    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_OFFER = new HashMap<>();
    /** Катера береговой охраны: id → за кем гонятся. */
    private static final Map<UUID, UUID> BOATS = new HashMap<>();

    public static boolean active(ServerPlayerEntity p) {
        return RUNS.containsKey(p.getUuid());
    }

    private static String[] buyer(Job j) {
        return (j.plane ? AIR : SEA)[j.buyer];
    }

    private static Job make(Random r) {
        Job j = new Job();
        j.cargo = r.nextInt(CARGO.length);
        j.plane = r.nextInt(3) == 0;
        j.buyer = r.nextInt((j.plane ? AIR : SEA).length);
        Cargo c = CARGO[j.cargo];
        j.pay = (c.min + r.nextInt(c.max - c.min + 1)) / 500 * 500 + (j.plane ? 2000 : 0);
        j.minutes = 9 + r.nextInt(4);
        return j;
    }

    // ---------------------------------------------------------------- вход для звонков
    /** Новые партии (три) и СМС от «Рыбака». */
    public static void offer(ServerPlayerEntity p) {
        List<Job> l = new ArrayList<>();
        Random r = p.getRandom();
        for (int i = 0; i < 3; i++) l.add(make(r));
        if (l.stream().noneMatch(j -> j.plane)) l.get(2).plane = true;
        if (l.stream().allMatch(j -> j.plane)) l.get(0).plane = false;
        for (Job j : l) j.buyer = Math.min(j.buyer, (j.plane ? AIR : SEA).length - 1);
        OFFERS.put(p.getUuid(), l);
        Job j = l.get(0);
        Life.message(p, "Рыбак", "Есть товар: " + CARGO[j.cargo].name.toLowerCase(Locale.ROOT) + ", $" + j.pay + " за доставку. Остальное — телефон → «Дела» → «Контрабанда».");
    }

    static void action(ServerPlayerEntity p, String a, int v) {
        if (Economy.act(Life.get(p)) < ACT) {
            p.sendMessage(Text.literal("Рыбак с незнакомыми не работает (акт II)").formatted(Formatting.GRAY), true);
            return;
        }
        switch (a) {
            case "take" -> take(p, v);
            case "cancel" -> {
                if (RUNS.containsKey(p.getUuid())) fail(p, "Ты бросил груз. Рыбак недоволен");
            }
            case "refresh" -> {
                if (!RUNS.containsKey(p.getUuid())) offer(p);
            }
            default -> {}
        }
    }

    private static void take(ServerPlayerEntity p, int i) {
        List<Job> l = OFFERS.get(p.getUuid());
        if (l == null || i < 0 || i >= l.size() || RUNS.containsKey(p.getUuid())) return;
        if (FunUtil.busy(p) || !FunUtil.freeRoam(p)) {
            p.sendMessage(Text.literal("Сначала закончи текущее дело").formatted(Formatting.RED), true);
            return;
        }
        Job j = l.remove(i);
        Run r = new Run();
        r.j = j;
        r.deadline = p.getServerWorld().getTime() + 20L * 60 * j.minutes;
        r.guards = p.getRandom().nextInt(10) < new int[]{3, 6, 10}[CARGO[j.cargo].risk];
        RUNS.put(p.getUuid(), r);
        Activities.title(p, "КОНТРАБАНДА", CARGO[j.cargo].name + " · забери " + (j.plane ? "самолёт на аэродроме" : "катер в порту"));
        Life.message(p, "Рыбак", (j.plane ? "Самолёт с грузом ждёт на аэродроме. Сбросишь над меткой — пролети низко, ниже 40 метров."
                : "Катер с грузом у причала в порту. Покупатель ждёт на воде.") + " Береговая охрана в последнее время злая — риск " + RISK[CARGO[j.cargo].risk] + ".");
    }

    // ---------------------------------------------------------------- ход
    static void tick(MinecraftServer s) {
        if (!BOATS.isEmpty()) steerBoats(s);
        long t = s.getTicks();
        if (t % 10 != 6) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            Run r = RUNS.get(p.getUuid());
            if (r != null) {
                if (!p.isDead()) tickRun(p, r);
                continue;
            }
            if (t % 200 == 6) autoOffer(p);
        }
    }

    private static void autoOffer(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (Economy.act(d) < ACT || !FunUtil.freeRoam(p) || FunUtil.busy(p) || p.age < 20 * 60 * 8) return;
        long now = p.getServerWorld().getTime();
        long next = NEXT_OFFER.computeIfAbsent(p.getUuid(), u -> now + 20 * 60 * (d.flags.contains("sm_intro") ? 15 : 6));
        if (now < next) return;
        NEXT_OFFER.put(p.getUuid(), now + 20 * 60 * (18 + p.getRandom().nextInt(10)));
        d.flags.add("sm_intro");
        offer(p);
    }

    private static void tickRun(ServerPlayerEntity p, Run r) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        Job j = r.j;
        if (now > r.deadline) {
            fail(p, "Покупатель не дождался");
            return;
        }
        Entity ve = r.vehicle == null ? null : w.getEntity(r.vehicle);
        MotorVehicle v = ve instanceof MotorVehicle mv ? mv : null;
        if (v != null) r.lastPos = v.getPos();
        if (r.stage > 0 && (v != null && v.wrecked() || v == null && (r.lastPos == null || p.squaredDistanceTo(r.lastPos) < 48 * 48))) {
            fail(p, "Груз потерян вместе с " + (j.plane ? "самолётом" : "катером"));
            return;
        }
        List<String> hud = new ArrayList<>();
        hud.add("§bКОНТРАБАНДА · " + CARGO[j.cargo].name);
        switch (r.stage) {
            case 0 -> {
                Vec3d pick = Story.place(w, j.plane ? "airfield" : "port_dock");
                if (pick == null) {
                    fail(p, "Точка погрузки недоступна");
                    return;
                }
                FunUtil.target(p, pick, j.plane ? "Самолёт с грузом" : "Катер с грузом");
                hud.add("§fЗабери " + (j.plane ? "самолёт на аэродроме" : "катер у причала порта"));
                if (p.squaredDistanceTo(pick) < 60 * 60) {
                    MotorVehicle nv = j.plane ? plane(w, pick) : boat(w, pick, 0x111316);
                    if (nv == null) break;
                    r.vehicle = nv.getUuid();
                    r.stage = 1;
                    p.sendMessage(Text.literal("Груз погружен — садись!").formatted(Formatting.GOLD), true);
                }
            }
            case 1 -> {
                FunUtil.target(p, v.getPos(), j.plane ? "Самолёт с грузом" : "Катер с грузом");
                hud.add("§fСадись в " + (j.plane ? "самолёт" : "катер") + " с грузом");
                if (p.getVehicle() == v) {
                    r.stage = 2;
                    Vec3d at = Story.place(w, buyer(j)[0]);
                    if (at == null) at = p.getPos();
                    r.drop = j.plane ? at : seaNear(at);
                    r.guardAt = now + (r.fast ? 60 : 20 * (20 + p.getRandom().nextInt(25)));
                    Activities.title(p, "ВЕЗИ ГРУЗ", buyer(j)[1] + " · ждёт " + buyer(j)[2]);
                }
            }
            case 2 -> {
                FunUtil.target(p, r.drop, buyer(j)[1]);
                double dx = v.getX() - r.drop.x, dz = v.getZ() - r.drop.z, d2 = dx * dx + dz * dz;
                int dist = (int) Math.sqrt(d2);
                hud.add("§f" + (j.plane ? "Сбрось груз: " : "Доставь: ") + buyer(j)[1] + " · " + dist + " м");
                if (j.plane) {
                    int ground = w.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(v.getX()), MathHelper.floor(v.getZ()));
                    int alt = (int) (v.getY() - ground);
                    if (d2 < 120 * 120) hud.add(alt < 40 ? "§aВысота " + alt + " м — сбрасывай!" : "§eВысота " + alt + " м — снижайся ниже 40");
                    if (d2 < 40 * 40 && alt < 40 && p.getVehicle() == v) {
                        w.spawnParticles(ParticleTypes.CLOUD, v.getX(), v.getY() - 1, v.getZ(), 30, 0.6, 0.3, 0.6, 0.05);
                        deliver(p, r);
                        return;
                    }
                } else if (d2 < 14 * 14 && p.getVehicle() == v) {
                    deliver(p, r);
                    return;
                }
                if (r.guards && !r.chased && now >= r.guardAt && p.getVehicle() == v) coastGuard(p, r, v);
                if (r.chased) hud.add(Wanted.stars(p) > 0 ? "§cБереговая охрана на хвосте!" : "§aОторвался от охраны");
            }
            default -> {}
        }
        long left = (r.deadline - now) / 20;
        hud.add("§7Осталось " + left / 60 + ":" + String.format("%02d", left % 60) + " · риск " + RISK[CARGO[j.cargo].risk]);
        Underworld.hud(p, hud);
    }

    /** Береговая охрана: розыск, вертолёт с прожектором, на воде — катера со стрелками. */
    private static void coastGuard(ServerPlayerEntity p, Run r, MotorVehicle v) {
        ServerWorld w = p.getServerWorld();
        r.chased = true;
        int risk = CARGO[r.j.cargo].risk;
        Wanted.add(p, 0, "контрабанда", 2 + risk);
        Activities.title(p, "БЕРЕГОВАЯ ОХРАНА!", "Уходи от погони — груз должен дойти");
        if (risk >= 1 || r.j.plane) HeliEntity.spawnPolice(w, p.getPos(), p);
        if (!r.j.plane) {
            for (int i = 0; i < 1 + risk; i++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2;
                Vec3d near = seaNear(p.getPos().add(Math.cos(a) * 45, 0, Math.sin(a) * 45));
                SpeedboatEntity b = boat(w, near, 0x1E5AA8);
                if (b == null) continue;
                CitizenEntity drv = cop(w, b, false), gun = cop(w, b, true);
                drv.startRiding(b, true);
                gun.startRiding(b, true);
                b.setCustomName(Text.literal("Береговая охрана"));
                BOATS.put(b.getUuid(), p.getUuid());
                r.police.add(b.getUuid());
            }
        }
        Economy.shock(p.getServer(), "PORT", -0.02, "береговая охрана гоняется за контрабандистами");
    }

    private static CitizenEntity cop(ServerWorld w, Entity at, boolean gunner) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        c.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.POLICE, false, gunner ? "Стрелок охраны" : "Рулевой");
        c.refreshPositionAndAngles(at.getX(), at.getY(), at.getZ(), 0, 0);
        if (gunner) c.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModContent.SMG));
        w.spawnEntity(c);
        return c;
    }

    /** Катера охраны идут на перехват и стреляют, пока у цели есть звёзды. */
    private static void steerBoats(MinecraftServer s) {
        ServerWorld w = s.getOverworld();
        Iterator<Map.Entry<UUID, UUID>> it = BOATS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Entity be = w.getEntity(e.getKey());
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(e.getValue());
            if (!(be instanceof SpeedboatEntity b) || b.wrecked() || p == null || p.squaredDistanceTo(b) > 220 * 220 || Wanted.stars(p) == 0) {
                if (be instanceof SpeedboatEntity b2 && (p == null || Wanted.stars(p) == 0 || p.squaredDistanceTo(b2) > 220 * 220)) {
                    for (Entity pa : b2.getPassengerList()) pa.discard();
                    b2.discard();
                }
                it.remove();
                continue;
            }
            double dx = p.getX() - b.getX(), dz = p.getZ() - b.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
            b.setYaw(b.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(want - b.getYaw()), -5, 5));
            b.speed = dist > 10 ? 0.95f : 0.3f;
            if (b.age % 25 == 0 && dist < 34) {
                for (Entity pa : b.getPassengerList())
                    if (pa instanceof CitizenEntity c && c.getMainHandStack().isOf(ModContent.SMG) && Wanted.stars(p) >= 2) {
                        Vec3d eye = c.getEyePos();
                        Weapons.shoot(w, c, eye, p.getEyePos().subtract(eye), 2.5f, 0.09 + dist * 0.004, 40, true);
                    }
            }
        }
    }

    private static void deliver(ServerPlayerEntity p, Run r) {
        Job j = r.j;
        boolean clean = Wanted.stars(p) == 0;
        int pay = j.pay + (clean ? j.pay / 5 : 0);
        Economy.dirty(p, pay, "контрабанда: " + CARGO[j.cargo].name.toLowerCase(Locale.ROOT));
        Activities.title(p, "ГРУЗ ДОСТАВЛЕН", CARGO[j.cargo].name + " · +$" + pay + (clean ? " · без хвоста +20%" : ""));
        Life.message(p, "Рыбак", buyer(j)[2] + " доволен. $" + pay + " — грязными." + (r.chased ? " Охрана тебя видела — пару дней не светись." : ""));
        p.getServerWorld().playSound(null, p.getBlockPos(), ModContent.CASH, SoundCategory.PLAYERS, 1f, 1f);
        if (r.chased) News.post(p.getServer(), "Береговая охрана упустила контрабандистов: " + buyer(j)[1].toLowerCase(Locale.ROOT) + ", груз — " + CARGO[j.cargo].name.toLowerCase(Locale.ROOT), "КРИМИНАЛ");
        LifeData d = Life.get(p);
        int n = FunUtil.getInt(d, "sm_done") + 1;
        FunUtil.setInt(d, "sm_done", n);
        Achievements.give(p, "smuggle1");
        if (n >= 10) Achievements.give(p, "smuggle10");
        if (j.plane) Achievements.give(p, "smuggle_air");
        end(p, r);
        Life.sync(p);
    }

    private static void fail(ServerPlayerEntity p, String why) {
        Run r = RUNS.get(p.getUuid());
        if (r == null) return;
        Activities.title(p, "КОНТРАБАНДА СОРВАЛАСЬ", why);
        end(p, r);
    }

    private static void end(ServerPlayerEntity p, Run r) {
        RUNS.remove(p.getUuid());
        FunUtil.clearHud(p);
        Underworld.hud(p, List.of());
    }

    static void onDownfall(ServerPlayerEntity p, boolean busted) {
        if (RUNS.containsKey(p.getUuid())) fail(p, busted ? "Тебя взяли с грузом" : "Ты погиб — груз пропал");
    }

    static void onLeave(ServerPlayerEntity p) {
        RUNS.remove(p.getUuid());
    }

    // ---------------------------------------------------------------- транспорт и вода
    /** Ближайшая открытая вода (море) к точке: остров считает море сам, без загрузки чанков. */
    static Vec3d seaNear(Vec3d at) {
        int cx = MathHelper.floor(at.x), cz = MathHelper.floor(at.z);
        for (int r = 0; r <= 120; r += 3)
            for (int i = 0; i < Math.max(1, r * 2); i++) {
                double a = i * Math.PI * 2 / Math.max(1, r * 2);
                int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
                if (open(x, z)) return new Vec3d(x + 0.5, Island.G, z + 0.5);
            }
        return at;
    }

    private static boolean open(int x, int z) {
        for (int dx = -4; dx <= 4; dx += 4)
            for (int dz = -4; dz <= 4; dz += 4)
                if (Island.zone(x + dx, z + dz) != Island.Zone.SEA) return false;
        return true;
    }

    @Nullable
    private static SpeedboatEntity boat(ServerWorld w, Vec3d near, int color) {
        // свободная вода 5×5 под открытым небом (не под причалом), ближе всего к точке
        int cx = MathHelper.floor(near.x), cz = MathHelper.floor(near.z);
        BlockPos found = null;
        for (int r = 0; r <= 64 && found == null; r += 2)
            for (int i = 0; i < Math.max(1, r * 2) && found == null; i++) {
                double a = i * Math.PI * 2 / Math.max(1, r * 2);
                int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
                if (Island.zone(x, z) != Island.Zone.SEA || !w.isChunkLoaded(x >> 4, z >> 4)) continue;
                BlockPos wp = new BlockPos(x, Island.G - 1, z);
                if (openWater(w, wp)) found = wp;
            }
        if (found == null) {
            Vec3d s = seaNear(near);
            found = new BlockPos(MathHelper.floor(s.x), Island.G - 1, MathHelper.floor(s.z));
            w.getChunk(found.getX() >> 4, found.getZ() >> 4);
            if (!w.getFluidState(found).isIn(FluidTags.WATER)) return null;
        }
        double surf = found.getY() + w.getFluidState(found).getHeight(w, found);
        SpeedboatEntity b = new SpeedboatEntity(Vehicles.SPEEDBOAT, w);
        b.refreshPositionAndAngles(found.getX() + 0.5, surf - SpeedboatEntity.DRAFT, found.getZ() + 0.5, w.random.nextInt(360), 0);
        int v = 0;
        for (int i = 0; i < SpeedboatEntity.COLORS.length; i++) if (SpeedboatEntity.COLORS[i] == color) v = i;
        b.setVariant(v);
        w.spawnEntity(b);
        return b;
    }

    private static boolean openWater(ServerWorld w, BlockPos at) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos q = at.add(dx, 0, dz);
                if (!w.getFluidState(q).isIn(FluidTags.WATER) || !w.isAir(q.up()) || !w.isAir(q.up(2)) || !w.isSkyVisible(q.up())) return false;
            }
        return true;
    }

    @Nullable
    private static PlaneEntity plane(ServerWorld w, Vec3d airfield) {
        int x = MathHelper.floor(airfield.x) - 30, z = MathHelper.floor(airfield.z); // на полосе, носом на запад
        w.getChunk(x >> 4, z >> 4);
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        PlaneEntity pl = new PlaneEntity(Vehicles.PLANE, w);
        pl.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 270, 0);
        pl.setVariant(4);
        if (!w.isSpaceEmpty(pl, pl.getBoundingBox())) pl.refreshPositionAndAngles(x + 0.5, y + 1, z + 12.5, 270, 0);
        w.spawnEntity(pl);
        return pl;
    }

    // ---------------------------------------------------------------- телефон
    static NbtCompound state(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        NbtList l = new NbtList();
        for (Job j : OFFERS.getOrDefault(p.getUuid(), List.of())) l.add(nbt(j));
        n.put("offers", l);
        Run r = RUNS.get(p.getUuid());
        if (r != null) {
            NbtCompound c = nbt(r.j);
            c.putInt("left", (int) Math.max(0, (r.deadline - p.getServerWorld().getTime()) / 20));
            c.putInt("stage", r.stage);
            n.put("active", c);
        }
        n.putInt("done", FunUtil.getInt(Life.get(p), "sm_done"));
        return n;
    }

    private static NbtCompound nbt(Job j) {
        NbtCompound o = new NbtCompound();
        Cargo c = CARGO[j.cargo];
        o.putString("cargo", c.name);
        o.putString("risk", RISK[c.risk]);
        o.putInt("riskN", c.risk);
        o.putBoolean("plane", j.plane);
        o.putString("from", j.plane ? "Аэродром — самолёт" : "Порт — катер");
        o.putString("to", buyer(j)[1]);
        o.putString("who", buyer(j)[2]);
        o.putInt("pay", j.pay);
        o.putInt("min", j.minutes);
        return o;
    }

    // ---------------------------------------------------------------- автотест
    static String debugTake(ServerPlayerEntity p, int i) {
        take(p, i);
        Run r = RUNS.get(p.getUuid());
        return r == null ? "не взято" : "Контрабанда: " + CARGO[r.j.cargo].name + " → " + buyer(r.j)[1] + (r.j.plane ? " (самолёт)" : " (катер)") + (r.guards ? " с охраной" : "");
    }

    /** Автотест: взять партию катером (или самолётом) и сразу вызвать охрану. */
    public static String debugBoat(ServerPlayerEntity p, boolean plane, boolean guards) {
        offer(p);
        List<Job> l = OFFERS.get(p.getUuid());
        for (int i = 0; i < l.size(); i++)
            if (l.get(i).plane == plane) {
                take(p, i);
                break;
            }
        Run r = RUNS.get(p.getUuid());
        if (r == null) return "не взято";
        r.guards = guards;
        r.fast = true;
        return debugTake(p, -1);
    }

    @Nullable
    public static Entity debugVehicle(ServerPlayerEntity p) {
        Run r = RUNS.get(p.getUuid());
        return r == null || r.vehicle == null ? null : p.getServerWorld().getEntity(r.vehicle);
    }

    @Nullable
    public static Vec3d debugDrop(ServerPlayerEntity p) {
        Run r = RUNS.get(p.getUuid());
        return r == null ? null : r.drop;
    }

    static String debug(ServerPlayerEntity p) {
        Run r = RUNS.get(p.getUuid());
        return r == null ? "smuggle -" : "smuggle stage " + r.stage + " chased " + r.chased + " police " + r.police.size();
    }
}
