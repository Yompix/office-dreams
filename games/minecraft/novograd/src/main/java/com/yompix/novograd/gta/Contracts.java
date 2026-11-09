package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.BodyguardEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.Names;
import com.yompix.novograd.story.Story;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Заказные убийства по телефону: с неизвестного номера приходит СМС, в телефоне → «Дела» → «Заказы» — три заказа:
 * кто цель (имя, кто такой и за что), где бывает днём и ночью, срок, оплата и бонусы:
 * за способ (с глушителем, из машины, «несчастный случай» — сбить машиной или взрыв), без свидетелей, без розыска.
 * Цель стоит в своём месте по расписанию, у некоторых — охрана. Платят грязными. Открывается с акта II.
 */
public final class Contracts {
    private Contracts() {}

    public static final int ACT = 1;

    /** Кто цель: должность, за что, где днём, где ночью, охрана, плата от/до. */
    private record Profile(String title, String why, String[] day, String[] night, int guards, int min, int max) {}

    private static final Profile[] PROFILES = {
            new Profile("Бухгалтер Ящеров", "Ведёт чёрную кассу банды и слишком много знает", new String[]{"industry_street", "chop_shop"}, new String[]{"club_neon", "gang_yard"}, 1, 8000, 12000),
            new Profile("Продажный судья", "Берёт с обеих сторон. Заказчик проиграл из-за него дело", new String[]{"downtown_plaza", "bank_tower"}, new String[]{"riv_hotel", "riv_villa"}, 2, 14000, 20000),
            new Profile("Свидетель обвинения", "Завтра даёт показания. До суда дойти не должен", new String[]{"suburb_street", "beach_bar"}, new String[]{"suburb_street", "hill_top"}, 0, 9000, 13000),
            new Profile("Ростовщик", "Ставит счётчик всему району. Должники скинулись", new String[]{"dt_alley", "industry_street"}, new String[]{"club_neon", "casino_dt"}, 1, 7000, 10000),
            new Profile("Застройщик-мошенник", "Продал одну квартиру сорок раз", new String[]{"downtown_plaza", "riv_strip"}, new String[]{"riv_hotel", "casino_dt"}, 2, 12000, 16000),
            new Profile("Шантажист-фотограф", "Торгует чужими тайнами. Нужен он, а не плёнка", new String[]{"riv_beach", "beach_bar"}, new String[]{"riv_strip", "club_neon"}, 0, 8000, 11000),
            new Profile("Сбежавший кассир «Короны»", "Унёс выручку казино. «Корона» не прощает", new String[]{"riv_marina", "lake_dock"}, new String[]{"village", "desert_mesa"}, 0, 10000, 14000),
            new Profile("Капитан контрабандистов", "Сдал груз береговой охране. Свои не прощают", new String[]{"port_dock", "riv_docks"}, new String[]{"port_warehouse", "riv_marina"}, 2, 12000, 17000),
            new Profile("Нечистый инспектор порта", "Требует долю с каждого контейнера", new String[]{"port_warehouse", "port_dock"}, new String[]{"beach_bar", "club_neon"}, 1, 9000, 12000),
            new Profile("Букмекер-кидала", "Не выплатил крупный выигрыш", new String[]{"casino_dt", "riv_strip"}, new String[]{"casino_dt", "club_neon"}, 1, 8000, 12000),
            new Profile("Угонщик-конкурент", "Перебивает заказы у «Нитро-Гаража»", new String[]{"chop_shop", "industry_street"}, new String[]{"hill_top", "gang_yard"}, 1, 7000, 10000),
            new Profile("Курьер «Синих»", "Возит товар через чужой район", new String[]{"hill_top", "airfield_hangar"}, new String[]{"gang_yard", "dt_alley"}, 2, 9000, 13000),
            new Profile("Охотник за головами", "Охотится за тобой. Опереди его", new String[]{"desert_mesa", "village"}, new String[]{"hill_top", "lake_dock"}, 1, 11000, 15000),
            new Profile("Двойной агент", "Работает и на банду, и на полицию", new String[]{"riv_docks", "downtown_plaza"}, new String[]{"riv_beach", "beach_bar"}, 1, 12000, 16000),
    };

    private static final Map<String, String> PLACE_NAMES = Map.ofEntries(
            Map.entry("industry_street", "промзона"), Map.entry("chop_shop", "разборка"), Map.entry("club_neon", "клуб «Неон»"),
            Map.entry("gang_yard", "двор Ящеров"), Map.entry("downtown_plaza", "площадь делового центра"), Map.entry("bank_tower", "у Сити-Банка"),
            Map.entry("riv_hotel", "отель «Ривьера»"), Map.entry("riv_villa", "виллы Ривьеры"), Map.entry("suburb_street", "улица в пригороде"),
            Map.entry("beach_bar", "пляжный бар"), Map.entry("hill_top", "смотровая на холме"), Map.entry("dt_alley", "переулок в центре"),
            Map.entry("casino_dt", "казино в центре"), Map.entry("riv_strip", "«Стрип» Ривьеры"), Map.entry("riv_beach", "пляж Ривьеры"),
            Map.entry("riv_marina", "марина Ривьеры"), Map.entry("lake_dock", "причал на озере Ясном"), Map.entry("village", "деревня Сосновка"),
            Map.entry("desert_mesa", "плато в Пустоши"), Map.entry("port_dock", "причал порта"), Map.entry("riv_docks", "доки Ривьеры"),
            Map.entry("port_warehouse", "склады порта"), Map.entry("airfield_hangar", "ангар аэродрома"));

    public static final String[] METHODS = {"любым способом", "с глушителем", "из машины", "«несчастный случай»"};
    public static final String[] METHOD_HINT = {"", "оружие с глушителем, пешком", "стреляй из машины или сбей", "сбей машиной или взорви"};
    private static final int[] METHOD_BONUS = {0, 40, 50, 60};
    private static final String[] CLIENTS = {"Неизвестный номер", "Скрытый номер", "Аноним", "«Друг семьи»", "Номер не определён"};

    private static final class Contract {
        String name, client;
        int profile, method, pay, minutes;
        String day, night;
        boolean female;
    }

    private static final class Active {
        Contract c;
        long deadline, checkAt = -1;
        @Nullable UUID target;
        final List<UUID> guards = new ArrayList<>();
        String spawnedAt = "";
        boolean methodOk, killed, spooked;
        int witnesses;
        Vec3d diedAt;
    }

    private static final Map<UUID, List<Contract>> OFFERS = new HashMap<>();
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> NEXT_OFFER = new HashMap<>();

    public static boolean active(ServerPlayerEntity p) {
        return ACTIVE.containsKey(p.getUuid());
    }

    static String placeName(String id) {
        return PLACE_NAMES.getOrDefault(id, id);
    }

    private static Contract make(Random r) {
        Contract c = new Contract();
        c.profile = r.nextInt(PROFILES.length);
        Profile pr = PROFILES[c.profile];
        c.female = r.nextInt(3) == 0;
        c.name = Names.full(r, c.female);
        c.day = pr.day[r.nextInt(pr.day.length)];
        c.night = pr.night[r.nextInt(pr.night.length)];
        c.method = r.nextInt(5) == 0 ? 0 : 1 + r.nextInt(3);
        c.pay = (pr.min + r.nextInt(pr.max - pr.min + 1)) / 500 * 500;
        c.minutes = 10 + r.nextInt(7);
        c.client = CLIENTS[r.nextInt(CLIENTS.length)];
        return c;
    }

    // ---------------------------------------------------------------- вход для звонков
    /** Новые заказы (три) и СМС с неизвестного номера. */
    public static void offer(ServerPlayerEntity p) {
        List<Contract> l = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        Random r = p.getRandom();
        while (l.size() < 3) {
            Contract c = make(r);
            if (used.add(c.profile)) l.add(c);
        }
        OFFERS.put(p.getUuid(), l);
        Contract c = l.get(0);
        Life.message(p, c.client, "Есть работа. " + PROFILES[c.profile].title + " " + c.name + ". $" + c.pay + ". Подробности — телефон → «Дела» → «Заказы». Сообщение удалится.");
    }

    static void action(ServerPlayerEntity p, String a, int v) {
        LifeData d = Life.get(p);
        if (Economy.act(d) < ACT) {
            p.sendMessage(Text.literal("Тебе пока не звонят с такими предложениями (акт II)").formatted(Formatting.GRAY), true);
            return;
        }
        switch (a) {
            case "take" -> take(p, v);
            case "cancel" -> {
                if (ACTIVE.containsKey(p.getUuid())) fail(p, "Ты отказался от заказа. Заказчик запомнит");
            }
            case "refresh" -> {
                if (!ACTIVE.containsKey(p.getUuid())) offer(p);
            }
            default -> {}
        }
    }

    private static void take(ServerPlayerEntity p, int i) {
        List<Contract> l = OFFERS.get(p.getUuid());
        if (l == null || i < 0 || i >= l.size() || ACTIVE.containsKey(p.getUuid())) return;
        if (FunUtil.busy(p) || !FunUtil.freeRoam(p)) {
            p.sendMessage(Text.literal("Сначала закончи текущее дело").formatted(Formatting.RED), true);
            return;
        }
        Contract c = l.remove(i);
        Active a = new Active();
        a.c = c;
        a.deadline = p.getServerWorld().getTime() + 20L * 60 * c.minutes;
        ACTIVE.put(p.getUuid(), a);
        Profile pr = PROFILES[c.profile];
        Activities.title(p, "ЗАКАЗ ПРИНЯТ", pr.title + " " + c.name + " · " + c.minutes + " мин");
        Life.message(p, c.client, c.name + ". " + pr.why + ". Днём (06–18) бывает: " + placeName(c.day) + ", вечером и ночью: " + placeName(c.night)
                + (pr.guards > 0 ? ". С охраной." : ". Ходит один.") + " Способ: " + METHODS[c.method]
                + (c.method > 0 ? " (+" + METHOD_BONUS[c.method] + "%)" : "") + ". Без свидетелей и без полиции — доплачу.");
    }

    static boolean isDay(ServerWorld w) {
        return w.getTimeOfDay() % 24000 < 12000;
    }

    // ---------------------------------------------------------------- ход заказа
    static void tick(MinecraftServer s) {
        long t = s.getTicks();
        if (t % 10 != 4) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            Active a = ACTIVE.get(p.getUuid());
            if (a != null) {
                if (!p.isDead()) tickActive(p, a);
                continue;
            }
            if (t % 200 == 4) autoOffer(p);
        }
    }

    /** Время от времени — СМС с заказом (свободная игра, акт II+). */
    private static void autoOffer(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        if (Economy.act(d) < ACT || !FunUtil.freeRoam(p) || FunUtil.busy(p) || p.age < 20 * 60 * 5) return;
        long now = p.getServerWorld().getTime();
        long next = NEXT_OFFER.computeIfAbsent(p.getUuid(), u -> now + 20 * 60 * (d.flags.contains("ct_intro") ? 12 : 3));
        if (now < next) return;
        NEXT_OFFER.put(p.getUuid(), now + 20 * 60 * (15 + p.getRandom().nextInt(10)));
        d.flags.add("ct_intro");
        offer(p);
    }

    private static void tickActive(ServerPlayerEntity p, Active a) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        Contract c = a.c;
        if (a.killed) {
            if (now >= a.checkAt) pay(p, a);
            return;
        }
        if (now > a.deadline) {
            fail(p, "Время вышло — заказчик нашёл другого");
            return;
        }
        String placeId = isDay(w) ? c.day : c.night;
        CitizenEntity t = a.target != null && w.getEntity(a.target) instanceof CitizenEntity ce && ce.isAlive() ? ce : null;
        Vec3d place = Story.place(w, placeId);
        if (place == null) {
            fail(p, "Цель уехала с острова");
            return;
        }
        // цель переехала по расписанию (если ты не рядом) или ты далеко — убираем, появится на месте
        if (t != null && (!a.spawnedAt.equals(placeId) && p.squaredDistanceTo(t) > 70 * 70 || p.squaredDistanceTo(t) > 200 * 200)) {
            despawn(w, a);
            t = null;
        }
        if (t == null && p.squaredDistanceTo(place) < 110 * 110) t = spawn(p, a, place, placeId);
        Vec3d mark = t != null ? t.getPos() : place;
        FunUtil.target(p, mark, "Цель: " + c.name);
        // цель замечает вооружённого игрока рядом — бежит; ранена — бежит
        if (t != null && !a.spooked && (t.getHealth() < t.getMaxHealth()
                || Weapons.isGun(p.getMainHandStack()) && t.distanceTo(p) < 9 && t.canSee(p) && !p.isSneaking())) {
            a.spooked = true;
            t.lockActivity(null);
            t.setStationary(false);
            t.say("Помогите!", 40);
            t.panic(p.getPos());
            for (UUID id : a.guards) if (w.getEntity(id) instanceof CitizenEntity g && g.isAlive()) g.attack(p);
        }
        if (a.spooked && t != null && now % 60 == 4) t.panic(p.getPos());
        long left = (a.deadline - now) / 20;
        List<String> hud = new ArrayList<>();
        hud.add("§cЗАКАЗ · " + PROFILES[c.profile].title);
        hud.add("§f" + c.name + " · " + placeName(placeId));
        hud.add("§eСпособ: " + METHODS[c.method] + (c.method > 0 ? " (+" + METHOD_BONUS[c.method] + "%)" : ""));
        hud.add("§7Осталось " + left / 60 + ":" + String.format("%02d", left % 60));
        Underworld.hud(p, hud);
    }

    private static CitizenEntity spawn(ServerPlayerEntity p, Active a, Vec3d place, String placeId) {
        ServerWorld w = p.getServerWorld();
        Random r = p.getRandom();
        Vec3d at = BigHeists.spot(w, place, 3 + r.nextInt(4), r.nextDouble() * Math.PI * 2);
        CitizenEntity t = new CitizenEntity(ModContent.CITIZEN, w);
        int skin = CitizenEntity.RANDOM_FROM + 2 * r.nextInt(CitizenEntity.RANDOM_COUNT / 2) + (a.c.female ? 0 : 1);
        t.setup(skin, CitizenEntity.Role.CITIZEN, false, a.c.name);
        t.setCustomNameVisible(true);
        t.refreshPositionAndAngles(at.x, at.y, at.z, r.nextFloat() * 360, 0);
        w.spawnEntity(t);
        t.setStationary(true);
        CitizenEntity.Activity[] acts = {CitizenEntity.Activity.PHONE, CitizenEntity.Activity.SMOKE, CitizenEntity.Activity.LEAN, CitizenEntity.Activity.THINK, CitizenEntity.Activity.DRINK};
        t.lockActivity(acts[r.nextInt(acts.length)]);
        a.target = t.getUuid();
        a.spawnedAt = placeId;
        a.spooked = false;
        a.guards.clear();
        int n = PROFILES[a.c.profile].guards;
        for (int i = 0; i < n; i++) {
            Vec3d ga = BigHeists.spot(w, at, 2 + i, r.nextDouble() * Math.PI * 2);
            CitizenEntity g = new CitizenEntity(ModContent.CITIZEN, w);
            g.setup(CitizenEntity.skinIndex("guard"), CitizenEntity.Role.GANG, false, "Охрана: " + a.c.name.split(" ")[0]);
            g.refreshPositionAndAngles(ga.x, ga.y, ga.z, r.nextFloat() * 360, 0);
            w.spawnEntity(g);
            g.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModContent.PISTOL));
            g.setStationary(true);
            a.guards.add(g.getUuid());
        }
        return t;
    }

    private static void despawn(ServerWorld w, Active a) {
        if (a.target != null) {
            var e = w.getEntity(a.target);
            if (e != null && e.isAlive()) e.discard();
        }
        for (UUID id : a.guards) {
            var e = w.getEntity(id);
            if (e instanceof CitizenEntity g && g.isAlive()) g.removeLater(a.killed ? 400 : 1);
        }
        a.guards.clear();
        a.target = null;
    }

    /** Смерть цели: чья рука, каким способом, кто видел. */
    static void onDeath(CitizenEntity c, DamageSource src) {
        if (ACTIVE.isEmpty() || !(c.getWorld() instanceof ServerWorld w)) return;
        for (var e : ACTIVE.entrySet()) {
            Active a = e.getValue();
            if (a.killed || !c.getUuid().equals(a.target)) continue;
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(e.getKey());
            if (p == null) return;
            boolean mine = src.getAttacker() == p || p.squaredDistanceTo(c) < 40 * 40;
            if (!mine) {
                fail(p, "Цель погибла не от твоей руки — заказчик не платит");
                return;
            }
            boolean inCar = p.hasVehicle();
            boolean accident = src.isOf(DamageTypes.FLY_INTO_WALL) || src.isIn(DamageTypeTags.IS_EXPLOSION) || src.isIn(DamageTypeTags.IS_FALL) || src.isIn(DamageTypeTags.IS_FIRE);
            a.methodOk = switch (a.c.method) {
                case 1 -> src.getAttacker() == p && !inCar && GunMods.silenced(p.getMainHandStack());
                case 2 -> inCar;
                case 3 -> accident;
                default -> true;
            };
            a.witnesses = w.getEntitiesByClass(CitizenEntity.class, c.getBoundingBox().expand(22), o -> o != c && o.isAlive()
                    && !(o instanceof BodyguardEntity) && !a.guards.contains(o.getUuid()) && o.canSee(c)).size();
            a.killed = true;
            a.diedAt = c.getPos();
            a.checkAt = w.getTime() + 60; // через 3 секунды — есть ли розыск
            p.sendMessage(Text.literal("Цель устранена" + (a.methodOk && a.c.method > 0 ? " — как просили" : "")).formatted(Formatting.GOLD), true);
            return;
        }
    }

    private static void pay(ServerPlayerEntity p, Active a) {
        ServerWorld w = p.getServerWorld();
        Contract c = a.c;
        Profile pr = PROFILES[c.profile];
        int base = c.pay, total = base;
        List<String> extra = new ArrayList<>();
        if (c.method > 0 && a.methodOk) {
            total += base * METHOD_BONUS[c.method] / 100;
            extra.add("способ +" + METHOD_BONUS[c.method] + "%");
        }
        if (a.witnesses == 0) {
            total += base / 5;
            extra.add("без свидетелей +20%");
        }
        boolean clean = Wanted.stars(p) == 0;
        if (clean) {
            total += base / 4;
            extra.add("без розыска +25%");
        }
        total = total / 100 * 100;
        Economy.dirty(p, total, "заказ: " + c.name);
        Activities.title(p, "ЗАКАЗ ВЫПОЛНЕН", "+$" + total + (extra.isEmpty() ? "" : " · " + String.join(", ", extra)));
        Life.message(p, c.client, "Получил новости. $" + total + " — наличными, как договаривались." + (a.witnesses > 0 ? " Свидетелей было " + a.witnesses + " — в другой раз аккуратнее." : ""));
        String where = placeName(a.spawnedAt);
        News.post(p.getServer(), (a.c.method == 3 && a.methodOk ? "Несчастный случай: " : "Убийство: ") + pr.title.toLowerCase(Locale.ROOT) + " " + c.name
                + " погиб(ла) — " + where + (a.witnesses > 0 ? ", полиция опрашивает свидетелей" : ", свидетелей нет"), "КРИМИНАЛ");
        LifeData d = Life.get(p);
        int n = FunUtil.getInt(d, "ct_done") + 1;
        FunUtil.setInt(d, "ct_done", n);
        Achievements.give(p, "contract1");
        if (n >= 10) Achievements.give(p, "contract10");
        if (a.witnesses == 0 && clean && a.methodOk && c.method > 0) Achievements.give(p, "contract_clean");
        if (pr.title.startsWith("Продажный") || pr.title.startsWith("Застройщик")) Economy.shock(p.getServer(), "CITY", -0.03, "громкое убийство в деловых кругах");
        end(p, a);
        Life.sync(p);
    }

    private static void fail(ServerPlayerEntity p, String why) {
        Active a = ACTIVE.get(p.getUuid());
        if (a == null) return;
        Activities.title(p, "ЗАКАЗ ПРОВАЛЕН", why);
        end(p, a);
    }

    private static void end(ServerPlayerEntity p, Active a) {
        ACTIVE.remove(p.getUuid());
        despawn(p.getServerWorld(), a);
        FunUtil.clearHud(p);
        Underworld.hud(p, List.of());
    }

    static void onDownfall(ServerPlayerEntity p, boolean busted) {
        Active a = ACTIVE.get(p.getUuid());
        if (a == null) return;
        if (a.killed) return; // дело сделано — заплатят, даже если потом взяли
        fail(p, busted ? "Тебя взяли" : "Ты погиб");
    }

    static void onLeave(ServerPlayerEntity p) {
        Active a = ACTIVE.remove(p.getUuid());
        if (a != null) despawn(p.getServerWorld(), a);
    }

    static NbtCompound state(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        NbtList l = new NbtList();
        for (Contract c : OFFERS.getOrDefault(p.getUuid(), List.of())) l.add(nbt(c));
        n.put("offers", l);
        Active a = ACTIVE.get(p.getUuid());
        if (a != null) {
            NbtCompound c = nbt(a.c);
            c.putInt("left", (int) Math.max(0, (a.deadline - p.getServerWorld().getTime()) / 20));
            c.putString("now", placeName(isDay(p.getServerWorld()) ? a.c.day : a.c.night));
            n.put("active", c);
        }
        n.putInt("done", FunUtil.getInt(Life.get(p), "ct_done"));
        return n;
    }

    private static NbtCompound nbt(Contract c) {
        Profile pr = PROFILES[c.profile];
        NbtCompound o = new NbtCompound();
        o.putString("name", c.name);
        o.putString("title", pr.title);
        o.putString("why", pr.why);
        o.putString("day", placeName(c.day));
        o.putString("night", placeName(c.night));
        o.putString("method", METHODS[c.method]);
        o.putString("hint", METHOD_HINT[c.method]);
        o.putInt("bonus", METHOD_BONUS[c.method]);
        o.putInt("pay", c.pay);
        o.putInt("min", c.minutes);
        o.putInt("guards", pr.guards);
        o.putBoolean("female", c.female);
        o.putString("client", c.client);
        return o;
    }

    // ---------------------------------------------------------------- автотест
    static String debugTake(ServerPlayerEntity p, int i) {
        take(p, i);
        Active a = ACTIVE.get(p.getUuid());
        return a == null ? "не взят" : "Заказ: " + a.c.name + " · днём " + a.c.day + " · ночью " + a.c.night + " · " + METHODS[a.c.method];
    }

    /** Автотест: где сейчас цель (или место по расписанию). */
    @Nullable
    public static Vec3d debugTarget(ServerPlayerEntity p) {
        Active a = ACTIVE.get(p.getUuid());
        if (a == null) return null;
        ServerWorld w = p.getServerWorld();
        if (a.target != null && w.getEntity(a.target) != null) return w.getEntity(a.target).getPos();
        return Story.place(w, isDay(w) ? a.c.day : a.c.night);
    }

    static String debug(ServerPlayerEntity p) {
        Active a = ACTIVE.get(p.getUuid());
        return a == null ? "contract -" : "contract " + a.c.name + " killed " + a.killed + " target " + (a.target != null) + " guards " + a.guards.size();
    }
}
