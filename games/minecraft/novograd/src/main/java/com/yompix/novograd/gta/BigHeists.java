package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.BodyguardEntity;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.story.Story;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;

/**
 * Большие ограбления с подготовкой, как в GTA V. Три дела: порт («Портовая компания»), Сити-Банк и казино «Корона».
 * Доска планирования — в своём убежище или доме (телефон → «Дела» → «Ограбления»):
 *  1) разведка — сфотографируй охрану камерой телефона;  2) угони машину для отхода и отгони на точку;
 *  3) снаряжение под подход: ТИХО (форма охраны и глушитель) или ГРОМКО (взрывчатка, бронежилет, гранаты);
 *  4) команда: водитель, хакер, стрелок — у каждого три уровня, лучше — дороже доля, но выше шанс.
 * Финал — многоэтапное дело: вход → хранилище (взлом/подрыв) → мешки (тяжёлые — медленнее) → машина → уйти от погони.
 * Тихо: камеры (хакер отключает на время), охрана замечает (присядь — заметят позже; форма — ещё позже), выстрел без
 * глушителя или найденное тело — тревога. Громко: охрана и полиция сразу, заряд на сейф.
 * Добыча — грязными, делится по долям; акции компании падают, новости пишут. Открывается с акта IV (Economy.act ≥ 3).
 * Мелкие налёты на банк (Heists) в это время не начинаются, а после большого дела хранилище «пустое» пару дней.
 */
public final class BigHeists {
    private BigHeists() {}

    public static final int ACT = 3;

    /** id, название, место хранилища, смещение входа, точка отхода, награда от/до, тикер, падение акций, охрана, камеры, кто охраняет. */
    public record Def(String id, String name, String vault, String entry, String getaway, int min, int max, String ticker, double shock,
                      int guards, int cams, String guardName, String news) {}

    public static final Def[] DEFS = {
            new Def("port", "Портовая компания", "port_warehouse", "port_warehouse+-14,0", "port_gate+30,8", 70000, 110000, "PORT", -0.22,
                    5, 3, "Охрана порта", "Ночью ограблен склад «Портовой компании»: из сейфа таможни пропала крупная сумма"),
            new Def("bank", "Сити-Банк", "bank_tower", "bank_tower+0,14", "downtown_plaza+0,30", 140000, 200000, "CITY", -0.3,
                    6, 4, "Охрана банка", "Дерзкое ограбление хранилища Сити-Банка: полиция ищет банду профессионалов"),
            new Def("casino", "Казино «Корона»", "riv_casino", "riv_casino+0,16", "riv_strip+0,40", 220000, 320000, "KRON", -0.38,
                    7, 4, "Охрана казино", "Хранилище казино «Корона» вскрыто: Ривьера в шоке, акции казино рушатся"),
    };

    // ---------------------------------------------------------------- команда
    public static final String[] ROLES = {"drv", "hck", "gun"};
    public static final String[] ROLE_NAMES = {"Водитель", "Хакер", "Стрелок"};
    public static final String[][] CREW = {
            {"Лёха «Педаль»", "Карина Ветрова", "Макс «Нитро» Королёв"},
            {"Студент Паша", "Вика «Ноль»", "Профессор Гуревич"},
            {"Бык", "Сержант Рябов", "Тень"},
    };
    public static final String[][] CREW_INFO = {
            {"седан, уходишь сам", "маслкар, −1 звезда у машины", "суперкар с нитро, −2 звезды"},
            {"сейф 50 с, камеры 45 с, может спалиться", "сейф 38 с, камеры 90 с", "сейф 26 с, камеры выключены"},
            {"пистолет, 30 HP", "пистолет-пулемёт, 40 HP", "карабин, 60 HP, бронежилет"},
    };
    public static final String[][] CREW_SHORT = {{"Лёха", "Карина", "Макс"}, {"Паша", "Вика", "Профессор"}, {"Бык", "Рябов", "Тень"}};
    public static final int[] CUT = {5, 10, 15};
    public static final int GEAR_PRICE = 6000;

    // ---------------------------------------------------------------- состояние подготовки (флаги игрока)
    private static String key(String id, String k) {
        return "bh_" + id + "_" + k;
    }

    static int get(LifeData d, String id, String k) {
        return FunUtil.getInt(d, key(id, k));
    }

    static void set(LifeData d, String id, String k, int v) {
        FunUtil.setInt(d, key(id, k), v);
    }

    @Nullable
    static Def def(String id) {
        for (Def x : DEFS) if (x.id.equals(id)) return x;
        return null;
    }

    static boolean unlocked(LifeData d) {
        return Economy.act(d) >= ACT;
    }

    /** Всё готово к финалу. */
    static boolean ready(LifeData d, Def h) {
        boolean loud = get(d, h.id, "loud") == 1;
        return get(d, h.id, "scout") == 1 && get(d, h.id, "car") == 1 && get(d, h.id, loud ? "gearL" : "gearS") == 1
                && get(d, h.id, "drv") > 0 && get(d, h.id, "hck") > 0 && get(d, h.id, "gun") > 0;
    }

    static long day(ServerWorld w) {
        return w.getTimeOfDay() / 24000;
    }

    static int cooldown(ServerPlayerEntity p, Def h) {
        long last = FunUtil.getLong(Life.get(p), key(h.id, "cd"));
        if (last == Long.MIN_VALUE) return 0;
        return (int) Math.max(0, 3 - (day(p.getServerWorld()) - last));
    }

    /** Доска планирования — в своём убежище, квартире, доме или вилле. */
    public static boolean atBoard(ServerPlayerEntity p) {
        return Activities.nearOwnedSafehouse(p, 24) || Life.get(p).flags.contains("bh_board_anywhere");
    }

    // ---------------------------------------------------------------- вход для звонков и сюжета
    /** Предложить большое дело (звонок/СМС от Седого). id: port, bank, casino. */
    public static void offer(ServerPlayerEntity p, String id) {
        Def h = def(id);
        if (h == null) return;
        LifeData d = Life.get(p);
        set(d, h.id, "offered", 1);
        if (!unlocked(d)) {
            Life.message(p, "Седой", "Есть одна мысль про «" + h.name + "». Но ты пока зелёный — поговорим, когда о тебе заговорит весь город (акт IV).");
            return;
        }
        Life.message(p, "Седой", "«" + h.name + "». Большое дело, большие деньги. Доска планирования — у тебя дома или в убежище: телефон → «Дела» → «Ограбления». "
                + "Разведка, машина, снаряжение, команда — и вперёд.");
    }

    // ---------------------------------------------------------------- подготовка: разведка и угон
    private static final class Prep {
        String heist, kind;
        @Nullable UUID car;
        int stage;
        long until;
    }

    private static final Map<UUID, Prep> PREPS = new HashMap<>();

    static void action(ServerPlayerEntity p, String id, String a, int v) {
        LifeData d = Life.get(p);
        if (a.equals("cancel")) {
            if (RUNS.containsKey(p.getUuid())) fail(p, "Ты отменил дело");
            else if (PREPS.containsKey(p.getUuid())) endPrep(p, "Подготовка отменена");
            return;
        }
        Def h = def(id);
        if (h == null) return;
        if (!unlocked(d)) {
            p.sendMessage(Text.literal("Седой: «Рано тебе такие дела. Акт IV.»").formatted(Formatting.GRAY), true);
            return;
        }
        if (RUNS.containsKey(p.getUuid())) return;
        switch (a) {
            case "approach" -> set(d, h.id, "loud", v == 1 ? 1 : 0);
            case "drv", "hck", "gun" -> set(d, h.id, a, MathHelper.clamp(v, 0, 2) + 1);
            case "gear" -> buyGear(p, h);
            case "scout" -> startPrep(p, h, "scout");
            case "car" -> startPrep(p, h, "car");
            case "start" -> start(p, h);
            default -> {}
        }
        Life.sync(p);
    }

    private static void buyGear(ServerPlayerEntity p, Def h) {
        LifeData d = Life.get(p);
        boolean loud = get(d, h.id, "loud") == 1;
        String k = loud ? "gearL" : "gearS";
        if (get(d, h.id, k) == 1) return;
        if (!Economy.payBlack(p, GEAR_PRICE, "снаряжение для дела")) return;
        set(d, h.id, k, 1);
        if (loud) {
            give(p, new ItemStack(Arsenal.ARMOR));
            give(p, new ItemStack(Arsenal.GRENADE, 3));
            give(p, new ItemStack(Arsenal.MEDKIT, 2));
            Activities.title(p, "СНАРЯЖЕНИЕ КУПЛЕНО", "Взрывчатка для сейфа, бронежилет, гранаты, аптечки");
        } else {
            ItemStack gun = new ItemStack(ModContent.PISTOL);
            gun.set(GunMods.MODS, GunMods.SILENCER);
            give(p, gun);
            give(p, new ItemStack(ModContent.AMMO, 48));
            Activities.title(p, "СНАРЯЖЕНИЕ КУПЛЕНО", "Форма охраны (замечают позже) и пистолет с глушителем");
        }
    }

    private static void give(ServerPlayerEntity p, ItemStack s) {
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    private static void startPrep(ServerPlayerEntity p, Def h, String kind) {
        LifeData d = Life.get(p);
        if (get(d, h.id, kind) == 1) return;
        if (FunUtil.busy(p) && !PREPS.containsKey(p.getUuid())) {
            p.sendMessage(Text.literal("Сначала закончи текущее дело").formatted(Formatting.RED), true);
            return;
        }
        PREPS.remove(p.getUuid());
        ServerWorld w = p.getServerWorld();
        Prep pr = new Prep();
        pr.heist = h.id;
        pr.kind = kind;
        pr.until = w.getTime() + 20 * 60 * 12;
        if (kind.equals("car")) {
            Vec3d at = FunUtil.curbAround(p, 90, 200);
            if (at == null) at = Story.place(w, h.getaway);
            if (at == null) at = p.getPos().add(30, 0, 0);
            int[] vars = {CarEntity.SUPERCAR, 9, 3};
            CarEntity car = FunUtil.car(w, at, 90, vars[p.getRandom().nextInt(vars.length)]);
            pr.car = car.getUuid();
            FunUtil.target(p, at, "Машина для отхода");
            Life.message(p, "Седой", "Нужна чистая тачка, которую не знают копы. Стоит у тротуара — угони и отгони на точку отхода у «" + h.name + "».");
        } else {
            Vec3d at = Story.place(w, h.vault);
            if (at != null) FunUtil.target(p, at, "Разведка: " + h.name);
            Life.message(p, "Седой", "Подойди к «" + h.name + "» и сфотографируй охрану камерой телефона (G → «Камера»). Близко не лезь.");
        }
        PREPS.put(p.getUuid(), pr);
        Activities.title(p, kind.equals("car") ? "ПОДГОТОВКА: МАШИНА" : "ПОДГОТОВКА: РАЗВЕДКА", h.name);
    }

    private static void endPrep(ServerPlayerEntity p, String why) {
        Prep pr = PREPS.remove(p.getUuid());
        FunUtil.clearHud(p);
        Underworld.hud(p, List.of());
        if (pr == null) return;
        if (!why.isEmpty()) p.sendMessage(Text.literal(why).formatted(Formatting.GRAY), true);
    }

    private static void tickPrep(ServerPlayerEntity p, Prep pr) {
        ServerWorld w = p.getServerWorld();
        Def h = def(pr.heist);
        if (h == null || w.getTime() > pr.until) {
            endPrep(p, "Подготовка сорвалась — не успел");
            return;
        }
        if (!pr.kind.equals("car")) return; // разведка завершается снимком (onPhoto)
        var e = pr.car == null ? null : w.getEntity(pr.car);
        if (!(e instanceof CarEntity car) || car.wrecked()) {
            endPrep(p, "Машина для отхода потеряна — найди другую");
            return;
        }
        Vec3d drop = Story.place(w, h.getaway);
        if (drop == null) drop = p.getPos();
        if (pr.stage == 0) {
            FunUtil.target(p, car.getPos(), "Машина для отхода");
            if (p.getVehicle() == car) {
                pr.stage = 1;
                FunUtil.target(p, drop, "Точка отхода");
                p.sendMessage(Text.literal("Отгони машину на точку отхода, не попадись полиции").formatted(Formatting.GOLD), true);
            }
        } else if (p.getVehicle() == car && car.squaredDistanceTo(drop) < 10 * 10 && Wanted.stars(p) == 0) {
            LifeData d = Life.get(p);
            set(d, h.id, "car", 1);
            p.stopRiding();
            car.discard();
            endPrep(p, "");
            Activities.title(p, "МАШИНА ГОТОВА", "Седой спрячет её до дня «Х»");
            Life.sync(p);
        } else if (p.getVehicle() == car && car.squaredDistanceTo(drop) < 10 * 10 && w.getTime() % 40 == 0) {
            p.sendMessage(Text.literal("С хвостом нельзя — сначала оторвись от полиции").formatted(Formatting.RED), true);
        }
    }

    /** Снимок камерой телефона: рядом с целью ограбления — разведка засчитана. */
    static void onPhoto(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (!unlocked(d)) return;
        ServerWorld w = p.getServerWorld();
        for (Def h : DEFS) {
            if (get(d, h.id, "scout") == 1) continue;
            Vec3d at = Story.place(w, h.vault);
            if (at == null) continue;
            double dist = Math.sqrt(p.squaredDistanceTo(at));
            if (dist > 60) continue;
            Vec3d to = at.subtract(p.getEyePos()).normalize();
            if (dist > 8 && to.dotProduct(p.getRotationVec(1)) < 0.6) {
                p.sendMessage(Text.literal("На снимке не то — сними здание «" + h.name + "»").formatted(Formatting.GRAY), true);
                continue;
            }
            set(d, h.id, "scout", 1);
            Prep pr = PREPS.get(p.getUuid());
            if (pr != null && pr.kind.equals("scout") && pr.heist.equals(h.id)) endPrep(p, "");
            Activities.title(p, "РАЗВЕДКА ГОТОВА", h.name + " · охрана " + h.guards + ", камер " + h.cams);
            Life.message(p, "Седой", "Снимки получил. «" + h.name + "»: охранников " + h.guards + ", камер " + h.cams
                    + ". Камеры над входом и у сейфа — хакер их погасит, но не навсегда. Охрана смотрит перед собой: заходи со спины, пригнувшись.");
            Life.sync(p);
            return;
        }
    }

    // ---------------------------------------------------------------- финал
    private static final int GO = 0, INSIDE = 1, VAULT = 2, LOOT = 3, ESCAPE = 4, LOSE = 5;

    private static final class Run {
        Def h;
        int stage, timer, bags, suspicion, camsOff, drv, hck, gun, wave, charge = -1, sent, phase;
        boolean loud, alarm, spawned, hackRisk, gunDead;
        final List<UUID> guards = new ArrayList<>(), cops = new ArrayList<>(), props = new ArrayList<>();
        final List<Vec3d> cams = new ArrayList<>();
        @Nullable UUID gunman, driver, car;
        Vec3d vault, entry, getaway;
        long started;
        String lastHud = "";
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Set<UUID> CREW_IDS = new HashSet<>();
    /** Охрана, ещё не поднявшая тревогу: не бросается на вооружённого игрока сама (CitizenEntity) и не слышит чужую драку (Crime). */
    private static final Set<UUID> CALM = new HashSet<>();

    public static boolean calm(CitizenEntity c) {
        return !CALM.isEmpty() && CALM.contains(c.getUuid());
    }

    public static boolean active(ServerPlayerEntity p) {
        return RUNS.containsKey(p.getUuid()) || PREPS.containsKey(p.getUuid());
    }

    /** Идёт финал большого ограбления. */
    public static boolean inFinale(ServerPlayerEntity p) {
        return RUNS.containsKey(p.getUuid());
    }

    /** Член команды ограбления (BodyguardEntity не исчезает, пока идёт дело). */
    public static boolean isCrew(BodyguardEntity b) {
        return CREW_IDS.contains(b.getUuid());
    }

    private static void start(ServerPlayerEntity p, Def h) {
        LifeData d = Life.get(p);
        ServerWorld w = p.getServerWorld();
        if (!atBoard(p)) {
            p.sendMessage(Text.literal("Финал начинается у доски планирования — в твоём убежище или доме").formatted(Formatting.RED), true);
            return;
        }
        if (!ready(d, h)) {
            p.sendMessage(Text.literal("Подготовка не закончена").formatted(Formatting.RED), true);
            return;
        }
        int cd = cooldown(p, h);
        if (cd > 0) {
            p.sendMessage(Text.literal("Шум ещё не улёгся — подожди " + cd + " дн.").formatted(Formatting.RED), true);
            return;
        }
        if (FunUtil.busy(p) && !PREPS.containsKey(p.getUuid())) {
            p.sendMessage(Text.literal("Сначала закончи текущее дело").formatted(Formatting.RED), true);
            return;
        }
        if (Wanted.stars(p) > 0) {
            p.sendMessage(Text.literal("С розыском на дело не идут — сначала оторвись").formatted(Formatting.RED), true);
            return;
        }
        Vec3d vault = Story.place(w, h.vault), entry = Story.place(w, h.entry), get = Story.place(w, h.getaway);
        if (vault == null || entry == null || get == null) {
            p.sendMessage(Text.literal("В этом мире нет «" + h.name + "»").formatted(Formatting.RED), true);
            return;
        }
        PREPS.remove(p.getUuid());
        Run r = new Run();
        r.h = h;
        r.vault = vault;
        r.entry = entry;
        r.getaway = get;
        r.loud = get(d, h.id, "loud") == 1;
        r.drv = get(d, h.id, "drv") - 1;
        r.hck = get(d, h.id, "hck") - 1;
        r.gun = get(d, h.id, "gun") - 1;
        r.started = w.getTime();
        r.timer = 20 * 60 * 15;
        RUNS.put(p.getUuid(), r);
        // стрелок идёт с тобой
        BodyguardEntity g = crew(p, CREW[2][r.gun], r.gun, 3, p.getPos());
        if (g != null) r.gunman = g.getUuid();
        Activities.title(p, "ОГРАБЛЕНИЕ: " + h.name.toUpperCase(Locale.ROOT), (r.loud ? "ГРОМКО" : "ТИХО") + " · команда в сборе, едем");
        FunUtil.target(p, entry, "Вход: " + h.name);
        Life.message(p, "Седой", r.loud ? "Громко так громко. Заходите быстро, заряд на сейф — и на выход. Полиция будет через минуту."
                : "Тихо. Камеры погасит " + CREW[1][r.hck] + ", но ненадолго. Пригнись (Shift) — охрана заметит позже. Стреляй только с глушителем.");
    }

    @Nullable
    private static BodyguardEntity crew(ServerPlayerEntity p, String name, int tier, int slot, Vec3d at) {
        ServerWorld w = p.getServerWorld();
        BodyguardEntity b = Armory.BODYGUARD.create(w);
        if (b == null) return null;
        b.refreshPositionAndAngles(at.x, at.y, at.z, p.getYaw(), 0);
        b.setup(CitizenEntity.randomSkin(p.getRandom()), CitizenEntity.Role.CITIZEN, false, name);
        b.hire(p.getUuid(), tier, slot);
        b.setCustomNameVisible(true);
        CREW_IDS.add(b.getUuid());
        w.spawnEntity(b);
        if (p.getVehicle() != null && slot == 3) b.startRiding(p.getVehicle(), true);
        return b;
    }

    private static void tickRun(ServerPlayerEntity p, Run r) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        Def h = r.h;
        if (r.stage <= LOOT) {
            r.timer -= 5;
            if (r.timer <= 0) {
                fail(p, "Время вышло — команда разбежалась");
                return;
            }
            if (p.squaredDistanceTo(r.vault) > (r.spawned ? 400 * 400 : 2500 * 2500)) {
                fail(p, "Ты бросил дело");
                return;
            }
        }
        // гости у сейфа: охрана и камеры появляются, когда ты рядом
        if (!r.spawned && p.squaredDistanceTo(r.vault) < 80 * 80) spawnSecurity(p, r);
        if (r.spawned && now % 10 == 0) drawCams(w, r);
        if (r.gunman != null && !r.gunDead && !(w.getEntity(r.gunman) instanceof BodyguardEntity ge && ge.isAlive())) {
            r.gunDead = true;
            p.sendMessage(Text.literal(CREW[2][r.gun] + " погиб — его доля остаётся тебе").formatted(Formatting.RED), false);
        }
        if (!r.alarm && r.spawned && r.stage >= GO && r.stage <= LOOT) stealth(p, r);
        if (r.alarm && r.stage >= INSIDE && r.stage <= LOOT && ++r.wave % 80 == 0) cops(p, r, 2 + (r.h.id.equals("casino") ? 1 : 0));
        if (r.camsOff > 0) r.camsOff -= 5;
        switch (r.stage) {
            case GO -> {
                if (p.squaredDistanceTo(r.entry) < 8 * 8 || p.squaredDistanceTo(r.vault) < 14 * 14) {
                    if (!r.spawned) spawnSecurity(p, r);
                    r.stage = INSIDE;
                    if (r.loud) {
                        alarm(p, r, "Громкий налёт!");
                        Activities.title(p, "ГРОМКО!", "Пробейся к хранилищу");
                    } else {
                        r.camsOff = r.hck == 2 ? -1 : r.hck == 1 ? 20 * 90 : 20 * 45;
                        Activities.title(p, "ТИХО", CREW[1][r.hck] + ": «Камеры выключены» · иди к хранилищу");
                    }
                    FunUtil.target(p, r.vault, "Хранилище");
                }
            }
            case INSIDE -> {
                FunUtil.target(p, r.vault, "Хранилище");
                if (p.squaredDistanceTo(r.vault) < 4 * 4) {
                    r.stage = VAULT;
                    if (r.loud) {
                        r.charge = 20 * 5;
                        com.yompix.novograd.world.Destruction.later(w, r.vault.add(0, 0.5, 0), 1.6f, r.charge, null);
                        Activities.title(p, "ЗАРЯД ЗАЛОЖЕН", "Отойди на пару шагов!");
                        r.timer = Math.max(r.timer, 20 * 60 * 3);
                        r.wave = 1;
                    } else {
                        int[] t = {50, 38, 26};
                        r.charge = 20 * t[r.hck] * (r.alarm ? 3 : 2) / 2;
                        r.hackRisk = r.hck == 0 && p.getRandom().nextInt(4) == 0 || r.hck == 1 && p.getRandom().nextInt(10) == 0;
                        Activities.title(p, "ВЗЛОМ СЕЙФА", CREW[1][r.hck] + " подбирает код · держись рядом");
                    }
                }
            }
            case VAULT -> {
                if (p.squaredDistanceTo(r.vault) > 12 * 12) {
                    if (now % 40 == 0) p.sendMessage(Text.literal("Вернись к сейфу!").formatted(Formatting.RED), true);
                    break;
                }
                r.charge -= 5;
                if (r.loud && r.phase == 0 && r.charge <= 0) {
                    int[] t = {20, 16, 12};
                    r.phase = 1;
                    r.charge = 20 * t[r.hck];
                    Activities.title(p, "ДВЕРЬ ПОДДАЛАСЬ", "Добиваем замки… держись рядом");
                }
                if (!r.loud && r.hackRisk && !r.alarm && r.charge <= 20 * 15) {
                    r.hackRisk = false;
                    alarm(p, r, CREW[1][r.hck] + " спалился — сработала сигнализация!");
                }
                if (now % 30 == 0) w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, r.vault.x, r.vault.y + 1, r.vault.z, 14, 0.5, 0.6, 0.5, 0.2);
                if (r.charge <= 0 && (!r.loud || r.phase == 1)) {
                    r.stage = LOOT;
                    r.charge = 0;
                    w.playSound(null, BlockPos.ofFloored(r.vault), SoundEvents.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.5f, 0.6f);
                    Activities.title(p, "СЕЙФ ОТКРЫТ", "Стой у сейфа — набивай мешки (до 4). Уходи, когда хватит");
                }
            }
            case LOOT -> {
                double dv = p.squaredDistanceTo(r.vault);
                if (dv < 4.5 * 4.5 && r.bags < 4 && (r.charge += 5) >= 60) {
                    r.charge = 0;
                    r.bags++;
                    w.playSound(null, p.getBlockPos(), ModContent.CASH, SoundCategory.PLAYERS, 1f, 0.8f + r.bags * 0.1f);
                    p.sendMessage(Text.literal("Мешок " + r.bags + "/4 — тяжело! Уходи, когда хватит").formatted(Formatting.GOLD), true);
                }
                if (dv > 14 * 14 && r.bags > 0) escape(p, r);
            }
            case ESCAPE -> {
                var ce = r.car == null ? null : w.getEntity(r.car);
                if (ce instanceof CarEntity car && !car.wrecked()) {
                    FunUtil.target(p, car.getPos(), "Машина отхода");
                    if (p.getVehicle() == car) {
                        r.stage = LOSE;
                        int drop = r.drv;
                        if (drop > 0) Wanted.add(p, -drop, "водитель знает дворы", 0);
                        if (r.driver != null && w.getEntity(r.driver) instanceof BodyguardEntity dg) {
                            dg.setStay(false);
                            dg.startRiding(car, true);
                        }
                        FunUtil.target(p, null, "");
                        Activities.title(p, "ГОНИ!", CREW[0][r.drv] + ": «Оторвёмся — и деньги наши»");
                    }
                } else if (p.squaredDistanceTo(r.vault) > 150 * 150) r.stage = LOSE;
            }
            case LOSE -> {
                if (Wanted.stars(p) == 0) payout(p, r);
            }
            default -> {}
        }
        if (r.bags > 0) p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, r.bags >= 3 ? 1 : 0, false, false, true));
        hud(p, r);
    }

    private static void spawnSecurity(ServerPlayerEntity p, Run r) {
        r.spawned = true;
        ServerWorld w = p.getServerWorld();
        var rnd = p.getRandom();
        int n = r.h.guards;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + rnd.nextDouble() * 0.6;
            Vec3d at = spot(w, r.vault, 4 + rnd.nextInt(8), a);
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            c.setup(CitizenEntity.skinIndex("guard"), CitizenEntity.Role.GANG, false, r.h.guardName);
            c.refreshPositionAndAngles(at.x, at.y, at.z, (float) Math.toDegrees(a) + 90 + rnd.nextInt(90), 0);
            c.setHeadYaw(c.getYaw());
            w.spawnEntity(c);
            c.setStationary(true);
            c.equipStack(EquipmentSlot.MAINHAND, new ItemStack(i % 3 == 2 ? ModContent.SMG : ModContent.PISTOL));
            r.guards.add(c.getUuid());
            if (!r.loud) CALM.add(c.getUuid());
        }
        vaultProps(w, r);
        for (int i = 0; i < r.h.cams; i++) {
            double a = i * Math.PI * 2 / r.h.cams + 0.4;
            Vec3d at = i == 0 ? r.entry : spot(w, r.vault, 6 + (i % 2) * 4, a);
            r.cams.add(at.add(0, 2.6, 0));
        }
    }

    /** Сейф, который видно: стальной шкаф 1×2 и надпись (вид без блоков — карта не меняется, после дела исчезает). */
    private static void vaultProps(ServerWorld w, Run r) {
        BlockPos b = BlockPos.ofFloored(r.vault);
        Vec3d dir = r.entry.subtract(r.vault).multiply(1, 0, 1);
        int ox = Math.abs(dir.x) > Math.abs(dir.z) ? (dir.x > 0 ? -1 : 1) : 0, oz = ox == 0 ? (dir.z > 0 ? -1 : 1) : 0; // за сейфом — от входа
        for (int k = 0; k < 2; k++) {
            var bd = net.minecraft.entity.EntityType.BLOCK_DISPLAY.create(w);
            if (bd == null) return;
            NbtCompound n = new NbtCompound();
            NbtCompound bs = new NbtCompound();
            bs.putString("Name", k == 0 ? "minecraft:iron_block" : "minecraft:iron_trapdoor");
            n.put("block_state", bs);
            bd.readNbt(n);
            bd.refreshPositionAndAngles(b.getX() + ox, b.getY() + k, b.getZ() + oz, 0, 0);
            w.spawnEntity(bd);
            r.props.add(bd.getUuid());
        }
        var td = net.minecraft.entity.EntityType.TEXT_DISPLAY.create(w);
        if (td != null) {
            NbtCompound n = new NbtCompound();
            n.putString("text", "{\"text\":\"СЕЙФ\",\"color\":\"gold\",\"bold\":true}");
            n.putString("billboard", "center");
            td.readNbt(n);
            td.refreshPositionAndAngles(b.getX() + ox + 0.5, b.getY() + 2.3, b.getZ() + oz + 0.5, 0, 0);
            w.spawnEntity(td);
            r.props.add(td.getUuid());
        }
    }

    /** Свободная точка на уровне center на расстоянии dist в направлении a (или рядом). */
    static Vec3d spot(ServerWorld w, Vec3d center, double dist, double a) {
        for (int k = 0; k < 20; k++) {
            boolean sky = k < 10; // сначала — под открытым небом (не в чужом подвале)
            double aa = a + k * 0.7, dd = Math.max(2, dist - (k % 10) * 0.6);
            int x = MathHelper.floor(center.x + Math.cos(aa) * dd), z = MathHelper.floor(center.z + Math.sin(aa) * dd);
            for (int dy = 0; dy <= 2; dy++) {
                BlockPos bp = new BlockPos(x, MathHelper.floor(center.y) + dy, z);
                if (w.isAir(bp) && w.isAir(bp.up()) && !w.isAir(bp.down()) && w.getFluidState(bp.down()).isEmpty() && (!sky || w.isSkyVisible(bp)))
                    return new Vec3d(x + 0.5, bp.getY(), z + 0.5);
            }
        }
        return center;
    }

    private static final DustParticleEffect CAM_ON = new DustParticleEffect(new Vector3f(1f, 0.1f, 0.1f), 1.3f),
            CAM_OFF = new DustParticleEffect(new Vector3f(0.3f, 0.3f, 0.35f), 0.9f);

    private static void drawCams(ServerWorld w, Run r) {
        boolean on = !r.loud && !r.alarm && r.camsOff == 0;
        for (Vec3d c : r.cams) w.spawnParticles(on ? CAM_ON : CAM_OFF, c.x, c.y, c.z, 2, 0.08, 0.08, 0.08, 0);
    }

    /** Тихий подход: камеры, взгляды охраны, выстрелы и тела. */
    private static void stealth(ServerPlayerEntity p, Run r) {
        ServerWorld w = p.getServerWorld();
        if (r.loud) return;
        boolean camsOn = r.camsOff == 0 && r.stage >= INSIDE;
        if (camsOn)
            for (Vec3d c : r.cams)
                if (p.squaredDistanceTo(c.x, c.y - 2.6, c.z) < 5.5 * 5.5) {
                    alarm(p, r, "Тебя засекла камера!");
                    return;
                }
        boolean uniform = get(Life.get(p), r.h.id, "gearS") == 1;
        double range = (p.isSneaking() ? 5 : 11) * (uniform ? 0.65 : 1);
        int sees = 0;
        for (UUID id : r.guards) {
            if (!(w.getEntity(id) instanceof CitizenEntity g) || !g.isAlive()) continue;
            if (g.chasing(p)) {
                alarm(p, r, "Охрана подняла шум!");
                return;
            }
            double d = g.distanceTo(p);
            if (d > range || !g.canSee(p)) continue;
            Vec3d look = Vec3d.fromPolar(0, g.getHeadYaw());
            Vec3d to = p.getPos().subtract(g.getPos()).multiply(1, 0, 1).normalize();
            if (d > 2.5 && look.dotProduct(to) < 0.25) continue;
            sees = Math.max(sees, (int) ((range - d) / range * 14) + 4);
            g.face(p);
            g.say(r.suspicion > 50 ? "Эй! Стоять!" : "Хм?..", 30);
        }
        r.suspicion = sees > 0 ? r.suspicion + sees : Math.max(0, r.suspicion - 3);
        if (r.suspicion >= 100) alarm(p, r, "Охрана тебя заметила!");
    }

    /** Убит охранник: без глушителя — выстрел слышали; с глушителем — если тело видел другой охранник. */
    static void onDeath(CitizenEntity c, DamageSource src) {
        for (var e : RUNS.entrySet()) {
            Run r = e.getValue();
            if (!r.guards.contains(c.getUuid()) || r.alarm || r.loud) continue;
            if (!(c.getWorld() instanceof ServerWorld w)) return;
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(e.getKey());
            if (p == null) return;
            if (!GunMods.silenced(p.getMainHandStack()) && Weapons.isGun(p.getMainHandStack())) {
                alarm(p, r, "Выстрел услышали!");
                return;
            }
            for (UUID id : r.guards)
                if (w.getEntity(id) instanceof CitizenEntity g && g != c && g.isAlive() && g.distanceTo(c) < 10 && g.canSee(c)) {
                    alarm(p, r, "Охранник увидел тело!");
                    return;
                }
            p.sendMessage(Text.literal("Тихо снят. Никто не заметил").formatted(Formatting.GREEN), true);
            return;
        }
    }

    private static void alarm(ServerPlayerEntity p, Run r, String why) {
        if (r.alarm) return;
        r.alarm = true;
        r.suspicion = 100;
        r.guards.forEach(CALM::remove);
        ServerWorld w = p.getServerWorld();
        for (UUID id : r.guards)
            if (w.getEntity(id) instanceof CitizenEntity g && g.isAlive()) {
                g.setStationary(false);
                g.attack(p);
            }
        Wanted.add(p, 0, "ограбление: " + r.h.name, r.loud ? 4 : 3);
        w.playSound(null, BlockPos.ofFloored(r.vault), SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 2.5f, 0.5f);
        if (!r.loud) Activities.title(p, "ТРЕВОГА!", why);
        if (r.stage == VAULT && !r.loud) r.charge = r.charge * 3 / 2;
    }

    private static void cops(ServerPlayerEntity p, Run r, int n) {
        ServerWorld w = p.getServerWorld();
        r.cops.removeIf(id -> !(w.getEntity(id) instanceof CitizenEntity c) || !c.isAlive());
        if (r.cops.size() >= 8) return;
        var rnd = p.getRandom();
        for (int i = 0; i < n; i++) {
            Vec3d at = spot(w, r.vault, 16 + rnd.nextInt(6), rnd.nextDouble() * Math.PI * 2);
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            c.setup(CitizenEntity.skinIndex("police"), CitizenEntity.Role.POLICE, false, i == 0 && r.h.id.equals("bank") ? "Спецназ" : "Полицейский");
            c.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
            c.equipStack(EquipmentSlot.MAINHAND, new ItemStack(i % 2 == 0 ? ModContent.SMG : ModContent.PISTOL));
            c.removeWhenIdle();
            w.spawnEntity(c);
            c.attack(p);
            r.cops.add(c.getUuid());
        }
    }

    private static void escape(ServerPlayerEntity p, Run r) {
        ServerWorld w = p.getServerWorld();
        r.stage = ESCAPE;
        Wanted.add(p, 0, "ограбление: " + r.h.name, r.loud ? 4 : r.alarm ? 3 : 1);
        int[] vars = {3, 9, CarEntity.SUPERCAR};
        CarEntity car = FunUtil.car(w, r.getaway, Story.placeYaw(w, r.h.getaway), vars[r.drv]);
        car.setTune(r.drv == 2, r.drv, r.drv);
        r.car = car.getUuid();
        BodyguardEntity drv = crew(p, CREW[0][r.drv], 0, 4, r.getaway.add(2, 0, 0));
        if (drv != null) {
            drv.setStay(true);
            r.driver = drv.getUuid();
        }
        FunUtil.target(p, r.getaway, "Машина отхода");
        Activities.title(p, "НА ВЫХОД!", "Мешков: " + r.bags + " · " + CREW[0][r.drv] + " ждёт в машине");
    }

    private static void payout(ServerPlayerEntity p, Run r) {
        ServerWorld w = p.getServerWorld();
        Def h = r.h;
        LifeData d = Life.get(p);
        int base = h.min + p.getRandom().nextInt(h.max - h.min + 1);
        boolean clean = !r.loud && !r.alarm;
        int take = base * r.bags / 4;
        if (clean) take = take * 115 / 100;
        int cuts = CUT[r.drv] + CUT[r.hck] + (r.gunDead ? 0 : CUT[r.gun]);
        int net = take * (100 - cuts) / 100;
        Economy.dirty(p, net, "ограбление: " + h.name);
        Economy.shock(p.getServer(), h.ticker, h.shock * r.bags / 4.0, h.news.toLowerCase(Locale.ROOT));
        News.post(p.getServer(), h.news, "КРИМИНАЛ");
        Activities.title(p, "ДЕЛО СДЕЛАНО", h.name + " · твоя доля +$" + net + " · команде " + cuts + "%" + (clean ? " · чисто +15%" : ""));
        Life.message(p, "Седой", "Красиво. Добыча $" + take + ", команде " + cuts + "%. Твои $" + net + " — грязные: отмой через бизнес или у Рэя.");
        Achievements.give(p, "bh_" + h.id);
        if (clean) Achievements.give(p, "bh_ghost");
        set(d, h.id, "done", get(d, h.id, "done") + 1);
        if (Arrays.stream(DEFS).allMatch(x -> get(d, x.id, "done") > 0)) Achievements.give(p, "bh_all");
        // подготовка заново (команда запомнилась), хранилище пустое
        for (String k : new String[]{"scout", "car", "gearS", "gearL"}) set(d, h.id, k, 0);
        FunUtil.setLong(d, key(h.id, "cd"), day(w));
        int small = h.id.equals("bank") ? 1 : h.id.equals("casino") ? 2 : -1; // мелкий налёт Heists на это хранилище — через пару дней
        if (small >= 0) {
            String f = "heist_cd:" + small + "=";
            d.flags.removeIf(x -> x.startsWith(f));
            d.flags.add(f + day(w));
        }
        cleanup(p, r, false);
        Life.sync(p);
    }

    private static void fail(ServerPlayerEntity p, String why) {
        Run r = RUNS.get(p.getUuid());
        if (r == null) return;
        Activities.title(p, "ОГРАБЛЕНИЕ ПРОВАЛЕНО", why);
        cleanup(p, r, true);
    }

    private static void cleanup(ServerPlayerEntity p, Run r, boolean failed) {
        RUNS.remove(p.getUuid());
        ServerWorld w = p.getServerWorld();
        for (UUID id : new UUID[]{r.gunman, r.driver}) {
            if (id == null) continue;
            CREW_IDS.remove(id);
            var e = w.getEntity(id);
            if (e != null) {
                e.stopRiding();
                e.discard();
            }
        }
        r.guards.forEach(CALM::remove);
        for (UUID id : r.props) {
            var e = w.getEntity(id);
            if (e != null) e.discard();
        }
        for (UUID id : r.guards) {
            var e = w.getEntity(id);
            if (e instanceof CitizenEntity c && c.isAlive()) c.removeLater(failed ? 200 : 20);
        }
        p.removeStatusEffect(StatusEffects.SLOWNESS);
        FunUtil.clearHud(p);
        Underworld.hud(p, List.of());
    }

    private static void hud(ServerPlayerEntity p, Run r) {
        List<String> l = new ArrayList<>();
        l.add("§6ОГРАБЛЕНИЕ · " + r.h.name + " · " + (r.loud ? "§cГРОМКО" : "§bТИХО"));
        l.add(switch (r.stage) {
            case GO -> "§fЕдь ко входу (голубая метка)";
            case INSIDE -> r.alarm || r.loud ? "§fПробейся к хранилищу" : "§fПроберись к хранилищу незаметно";
            case VAULT -> r.loud ? (r.phase == 0 ? "§fЗаряд: " + (Math.max(0, r.charge) / 20 + 1) + " с — отойди!" : "§fДверь: ещё " + (Math.max(0, r.charge) / 20 + 1) + " с")
                    : "§fВзлом: " + (Math.max(0, r.charge) / 20 + 1) + " с — держись у сейфа";
            case LOOT -> "§fМешки: " + r.bags + "/4 · стой у сейфа, уходи когда хватит";
            case ESCAPE -> "§fБеги к машине отхода · мешков " + r.bags;
            case LOSE -> "§fОторвись от полиции";
            default -> "";
        });
        if (!r.loud && !r.alarm && r.stage <= LOOT) {
            if (r.stage >= INSIDE) l.add(r.camsOff < 0 ? "§aКамеры: выключены" : r.camsOff > 0 ? "§eКамеры: выкл ещё " + (r.camsOff / 20) + " с" : "§cКамеры: РАБОТАЮТ (красные точки)");
            int k = Math.min(10, r.suspicion / 10);
            if (r.suspicion > 0) l.add("§eПодозрение: " + "▮".repeat(k) + "▯".repeat(10 - k));
            else l.add("§7Пригнись (Shift) — заметят позже");
        }
        if (r.alarm && r.stage <= LOOT) l.add("§cТРЕВОГА — полиция едет");
        if (r.stage <= LOOT) l.add("§7Осталось " + (r.timer / 1200) + ":" + String.format("%02d", r.timer / 20 % 60));
        String s = String.join("\n", l);
        if (s.equals(r.lastHud) && ++r.sent % 8 != 0) return;
        r.lastHud = s;
        Underworld.hud(p, l);
    }

    // ---------------------------------------------------------------- тики, отказы, состояние
    static void tick(MinecraftServer s) {
        if (s.getTicks() % 5 != 1) return;
        if (RUNS.isEmpty() && PREPS.isEmpty()) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (p.isDead()) continue;
            Run r = RUNS.get(p.getUuid());
            if (r != null) tickRun(p, r);
            Prep pr = PREPS.get(p.getUuid());
            if (pr != null && s.getTicks() % 10 == 1) tickPrep(p, pr);
        }
    }

    static void onDownfall(ServerPlayerEntity p, boolean busted) {
        if (RUNS.containsKey(p.getUuid())) fail(p, busted ? "Тебя взяли — добыча изъята" : "Ты погиб — команда разбежалась");
        if (PREPS.containsKey(p.getUuid())) endPrep(p, "Подготовка сорвалась");
    }

    static void onLeave(ServerPlayerEntity p) {
        Run r = RUNS.get(p.getUuid());
        if (r != null) cleanup(p, r, true);
        PREPS.remove(p.getUuid());
    }

    static NbtList state(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtList l = new NbtList();
        Prep pr = PREPS.get(p.getUuid());
        Run run = RUNS.get(p.getUuid());
        for (Def h : DEFS) {
            NbtCompound c = new NbtCompound();
            c.putString("id", h.id);
            c.putString("name", h.name);
            c.putInt("min", h.min);
            c.putInt("max", h.max);
            c.putInt("guards", h.guards);
            c.putInt("cams", h.cams);
            for (String k : new String[]{"scout", "car", "gearS", "gearL", "loud", "drv", "hck", "gun", "done"}) c.putInt(k, get(d, h.id, k));
            c.putInt("cd", cooldown(p, h));
            c.putBoolean("ready", ready(d, h));
            c.putString("prep", pr != null && pr.heist.equals(h.id) ? pr.kind : "");
            c.putBoolean("running", run != null && run.h == h);
            l.add(c);
        }
        return l;
    }

    // ---------------------------------------------------------------- для автотеста
    static void debugPrep(ServerPlayerEntity p, String id, boolean loud) {
        LifeData d = Life.get(p);
        Def h = def(id);
        if (h == null) return;
        for (String k : new String[]{"scout", "car", loud ? "gearL" : "gearS"}) set(d, id, k, 1);
        set(d, id, "loud", loud ? 1 : 0);
        for (String k : ROLES) set(d, id, k, 3);
        FunUtil.setLong(d, key(id, "cd"), -10);
        d.flags.add("bh_board_anywhere");
        Life.sync(p);
    }

    static String debugGo(ServerPlayerEntity p, String id, boolean loud) {
        debugPrep(p, id, loud);
        Def h = def(id);
        if (h == null) return "нет такого";
        start(p, h);
        Run r = RUNS.get(p.getUuid());
        return r == null ? "не началось" : "Финал: " + h.name + " вход " + (int) r.entry.x + "," + (int) r.entry.z + " сейф " + (int) r.vault.x + "," + (int) r.vault.y + "," + (int) r.vault.z;
    }

    /** Автотест: перескочить к этапу (0 вход, 2 сейф открыт, 3 мешки, 4 отход). */
    public static void debugStage(ServerPlayerEntity p, int stage) {
        Run r = RUNS.get(p.getUuid());
        if (r == null) return;
        if (stage == LOOT) {
            r.stage = LOOT;
            r.bags = 3;
        } else if (stage == ESCAPE) {
            r.bags = Math.max(r.bags, 2);
            escape(p, r);
        } else if (stage == LOSE) {
            r.stage = LOSE;
            Wanted.clear(p);
        } else r.stage = stage;
    }

    public static Vec3d debugVault(ServerPlayerEntity p) {
        Run r = RUNS.get(p.getUuid());
        return r == null ? null : r.vault;
    }

    public static Vec3d debugEntry(ServerPlayerEntity p) {
        Run r = RUNS.get(p.getUuid());
        return r == null ? null : r.entry;
    }

    static String debug(ServerPlayerEntity p) {
        Run r = RUNS.get(p.getUuid());
        Prep pr = PREPS.get(p.getUuid());
        if (r != null) return "heist " + r.h.id + " stage " + r.stage + " alarm " + r.alarm + " bags " + r.bags + " susp " + r.suspicion + " guards " + r.guards.size();
        if (pr != null) return "prep " + pr.heist + " " + pr.kind + " " + pr.stage;
        return "heist -";
    }
}
