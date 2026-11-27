package com.yompix.novograd.life;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.gta.FunUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
 * 0.9.9: знакомства и отношения, как в San Andreas — без интима (объятия, танцы, прогулки, подарки).
 * <ul>
 *   <li>Знакомство: Shift+ПКМ пустой рукой по прохожему. Шанс — от формы, славы, стиля, настроения. Удалось —
 *       номер в телефоне («Встречи»).</li>
 *   <li>Свидание: «Встречи» → место (кафе, клуб, пляж-бар, концерт, прогулка по Стрипу). Человек ждёт на месте
 *       (метка на карте), дальше идёт рядом; три минуты вместе — симпатия растёт (больше — если поели, потанцевали,
 *       сыграли в бильярд/боулинг, подарили цветы или шоколад: ПКМ по нему с подарком). Ушёл, полиция, драка — минус.</li>
 *   <li>Уровни: знакомые → встречаетесь (30) → пара (60) → вместе (90). С 60 — помощь по профессии раз в день
 *       (врач — подлечит, механик — починит машину, полицейский — снимет звезду, повар — накормит).</li>
 *   <li>Ревность: два близких человека (45+) — после свидания с одним второй может узнать (СМС, −15).</li>
 *   <li>Без внимания симпатия медленно падает (−1 раз в 2 дня).</li>
 * </ul>
 * Данные — {@code LifeData.city.dating} (список, до 6), всё на сервере.
 */
public final class Dating {
    private Dating() {}

    public static final String[] JOBS = {"врач", "механик", "полицейский", "повар", "бармен", "студент"};
    public static final String[] PLACES = {"cafe_dt", "club_neon", "beach_bar", "riv_strip", "downtown_plaza"};
    public static final String[] PLACE_NAMES = {"Кафе в центре", "Клуб «Неон»", "Пляжный бар", "Прогулка по Стрипу", "Деловая площадь"};
    private static final String[] YES = {"«Давай! Вот мой номер»", "«Ты забавный(ая). Пиши»", "«Ладно, запиши номер»"};
    private static final String[] NO = {"«Не сегодня»", "«У меня есть кто-то»", "«Извини, я спешу»", "«Мы знакомы? Нет»"};

    static NbtList list(LifeData d) {
        if (!d.city.contains("dating")) d.city.put("dating", new NbtList());
        return d.city.getList("dating", 10);
    }

    public static String level(int rel) {
        return rel >= 90 ? "вместе" : rel >= 60 ? "пара" : rel >= 30 ? "встречаетесь" : "знакомые";
    }

    // ------------------------------------------------------------------ знакомство
    private static final Map<UUID, Long> TRIED = new HashMap<>();

    /** Shift+ПКМ по прохожему. true — заняли нажатие. */
    public static boolean meet(ServerPlayerEntity p, CitizenEntity c) {
        if (c.getRole() != CitizenEntity.Role.CITIZEN || !c.getStoryId().isEmpty() || c.getLookSeed() == 0 || DATE.containsKey(p.getUuid()) && DATE.get(p.getUuid()).npc.equals(c.getUuid()))
            return false;
        long now = p.getServerWorld().getTime();
        if (now - TRIED.getOrDefault(c.getUuid(), -99999L) < 20 * 300) {
            p.sendMessage(Text.literal(c.getName().getString() + ": «Мы уже говорили»").formatted(Formatting.GRAY), true);
            return true;
        }
        TRIED.put(c.getUuid(), now);
        LifeData d = Life.get(p);
        NbtList l = list(d);
        for (int i = 0; i < l.size(); i++)
            if (l.getCompound(i).getLong("seed") == c.getLookSeed()) {
                p.sendMessage(Text.literal(c.getName().getString() + ": «Привет ещё раз!»").formatted(Formatting.LIGHT_PURPLE), true);
                return true;
            }
        if (l.size() >= 6) {
            p.sendMessage(Text.literal("В «Встречах» уже 6 номеров").formatted(Formatting.GRAY), true);
            return true;
        }
        double chance = 0.3 + Math.min(0.15, d.fitness / 400.0) + Math.min(0.15, d.city.getInt("fame") / 2000.0) + (d.look.custom ? 0.05 : 0) + (d.mood - 50) / 500.0;
        if (com.yompix.novograd.gta.Wanted.stars(p) > 0) chance -= 0.25;
        boolean ok = p.getRandom().nextDouble() < chance || Boolean.getBoolean("novograd.datefast");
        c.getLookControl().lookAt(p);
        c.busy(ok ? CitizenEntity.Activity.HANDSHAKE : CitizenEntity.Activity.SHRUG, 40, null, ItemStack.EMPTY);
        if (!ok) {
            p.sendMessage(Text.literal(c.getName().getString() + ": " + NO[p.getRandom().nextInt(NO.length)]).formatted(Formatting.GRAY), false);
            return true;
        }
        NbtCompound e = new NbtCompound();
        e.putString("name", c.getName().getString());
        e.putLong("seed", c.getLookSeed());
        e.putInt("skin", c.getSkin());
        e.putString("job", JOBS[Math.floorMod((int) c.getLookSeed(), JOBS.length)]);
        e.putInt("rel", 10);
        e.putLong("last", Life.day(p));
        e.putString("likes", p.getRandom().nextBoolean() ? "цветы" : "шоколад");
        l.add(e);
        d.city.put("dating", l);
        p.sendMessage(Text.literal(c.getName().getString() + ": " + YES[p.getRandom().nextInt(YES.length)]).formatted(Formatting.LIGHT_PURPLE), false);
        Life.message(p, "Встречи", "Новый номер: " + e.getString("name") + " (" + e.getString("job") + "). Позови на свидание в «Встречах».");
        Achievements.give(p, "first_date_contact");
        return true;
    }

    // ------------------------------------------------------------------ свидание
    static final class Date {
        int idx;
        UUID npc;
        String place;
        Vec3d at;
        long start = -1, created;
        int bonus;
        boolean met;
    }

    private static final Map<UUID, Date> DATE = new HashMap<>();

    public static void init() {
        if (!Boolean.getBoolean("novograd.no099")) ServerTickEvents.END_SERVER_TICK.register(Dating::tick); // замер «до/после» (MSPT)
        Achievements.ALL.putIfAbsent("first_date_contact", new String[]{"Номерок", "Познакомиться на улице"});
        Achievements.ALL.putIfAbsent("first_date", new String[]{"Первое свидание", "Сходить на свидание"});
        Achievements.ALL.putIfAbsent("couple", new String[]{"Пара", "Отношения до «пары»"});
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity p) || !(entity instanceof CitizenEntity c))
                return net.minecraft.util.ActionResult.PASS;
            Date dt = DATE.get(p.getUuid());
            if (dt != null && dt.npc.equals(c.getUuid())) return gift(p, c, dt) ? net.minecraft.util.ActionResult.SUCCESS : net.minecraft.util.ActionResult.PASS;
            if (p.isSneaking() && p.getMainHandStack().isEmpty() && meet(p, c)) return net.minecraft.util.ActionResult.SUCCESS;
            return net.minecraft.util.ActionResult.PASS;
        });
    }

    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        switch (type) {
            case "date_state" -> send(p);
            case "date_ask" -> ask(p, n.getInt("i"), n.getInt("place"));
            case "date_call" -> call(p, n.getInt("i"));
            case "date_perk" -> perk(p, n.getInt("i"));
            case "date_forget" -> {
                NbtList l = list(Life.get(p));
                int i = n.getInt("i");
                if (i >= 0 && i < l.size()) l.remove(i);
                send(p);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public static void send(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtCompound n = new NbtCompound();
        n.put("list", list(d).copy());
        Date dt = DATE.get(p.getUuid());
        n.putInt("active", dt == null ? -1 : dt.idx);
        n.putLong("day", Life.day(p));
        LifeNet.send(p, "dating", n);
    }

    private static void call(ServerPlayerEntity p, int i) {
        NbtList l = list(Life.get(p));
        if (i < 0 || i >= l.size()) return;
        NbtCompound e = l.getCompound(i);
        long day = Life.day(p);
        if (e.getLong("called") == day) {
            p.sendMessage(Text.literal(e.getString("name") + ": «Мы же сегодня говорили»").formatted(Formatting.GRAY), true);
            return;
        }
        e.putLong("called", day);
        e.putLong("last", day);
        add(p, e, 2);
        String[] talk = {"«Как день? У меня завал»", "«Видел(а) новости? Город с ума сошёл»", "«Скучаю. Встретимся?»", "«Только о тебе и думала»"};
        Life.message(p, e.getString("name"), talk[Math.min(3, e.getInt("rel") / 30)]);
        send(p);
    }

    private static void ask(ServerPlayerEntity p, int i, int place) {
        LifeData d = Life.get(p);
        NbtList l = list(d);
        if (i < 0 || i >= l.size() || place < 0 || place >= PLACES.length || DATE.containsKey(p.getUuid())) return;
        NbtCompound e = l.getCompound(i);
        ServerWorld w = p.getServerWorld();
        Vec3d at = com.yompix.novograd.story.Story.place(w, PLACES[place]);
        if (at == null) return;
        int rel = e.getInt("rel");
        if (p.getRandom().nextInt(100) > 55 + rel && !Boolean.getBoolean("novograd.datefast")) {
            Life.message(p, e.getString("name"), "«Сегодня не могу, давай в другой раз»");
            return;
        }
        Date dt = new Date();
        dt.idx = i;
        dt.place = PLACES[place];
        dt.at = at;
        dt.created = w.getTime();
        DATE.put(p.getUuid(), dt);
        Life.message(p, e.getString("name"), "«Давай! Жду: " + PLACE_NAMES[place] + "»");
        FunUtil.target(p, at, "♥ " + e.getString("name"));
        send(p);
    }

    /** Спавн «на месте», когда игрок близко (чтобы не стоял пустой). */
    private static CitizenEntity spawn(ServerWorld w, NbtCompound e, Vec3d at) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        c.setup(e.getInt("skin"), CitizenEntity.Role.CITIZEN, false, e.getString("name"));
        c.forceLook(e.getLong("seed"));
        c.setCustomNameVisible(true);
        c.refreshPositionAndAngles(at.x + 1.5, at.y, at.z + 1.5, 0, 0);
        c.addCommandTag("nv_date");
        w.spawnEntity(c);
        return c;
    }

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 10 != 4) return;
        for (var it = DATE.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(en.getKey());
            Date dt = en.getValue();
            ServerWorld w = s.getOverworld();
            CitizenEntity npc = dt.npc == null ? null : (w.getEntity(dt.npc) instanceof CitizenEntity c ? c : null);
            if (p == null) {
                if (npc != null) npc.discard();
                it.remove();
                continue;
            }
            NbtList l = list(Life.get(p));
            if (dt.idx >= l.size()) {
                if (npc != null) npc.discard();
                it.remove();
                continue;
            }
            NbtCompound e = l.getCompound(dt.idx);
            long now = w.getTime();
            // не пришёл за 6 минут — обиделись
            if (!dt.met && now - dt.created > 20 * 360) {
                Life.message(p, e.getString("name"), "«Я ждал(а) полчаса… Ну и ладно»");
                add(p, e, -8);
                end(p, dt, npc, it);
                continue;
            }
            if (npc == null && p.squaredDistanceTo(dt.at) < 60 * 60) {
                npc = spawn(w, e, dt.at);
                dt.npc = npc.getUuid();
            }
            if (npc == null) continue;
            double dist = npc.squaredDistanceTo(p);
            if (!dt.met && dist < 5 * 5) {
                dt.met = true;
                dt.start = now;
                npc.busy(CitizenEntity.Activity.WAVE, 30, null, ItemStack.EMPTY);
                FunUtil.title(p, "СВИДАНИЕ", e.getString("name") + " · " + level(e.getInt("rel")));
                Achievements.give(p, "first_date");
            }
            if (!dt.met) continue;
            // идёт рядом
            if (dist > 3.5 * 3.5 && dist < 40 * 40) npc.getNavigation().startMovingTo(p, dist > 12 * 12 ? 1.2 : 0.9);
            else if (dist <= 3.5 * 3.5) {
                npc.getNavigation().stop();
                npc.getLookControl().lookAt(p);
                // танцует с тобой, обнимает в конце
                String em = com.yompix.novograd.life.Hands.current(p) != null ? "hands" : "";
                if (!em.isEmpty() && now % 100 == 4) dt.bonus += 2; // ели/пили вместе
            }
            if (com.yompix.novograd.gta.Wanted.stars(p) > 0 || dist > 40 * 40) {
                Life.message(p, e.getString("name"), dist > 40 * 40 ? "«Ты просто ушёл(ла)?!»" : "«Полиция?! Я ухожу»");
                add(p, e, -10);
                end(p, dt, npc, it);
                continue;
            }
            if (now - dt.start > (Boolean.getBoolean("novograd.datefast") ? 20 * 12 : 20 * 180)) {
                npc.busy(CitizenEntity.Activity.HUG, 60, null, ItemStack.EMPTY);
                int gain = 8 + Math.min(12, dt.bonus);
                add(p, e, gain);
                e.putLong("last", Life.day(p));
                FunUtil.title(p, "СВИДАНИЕ УДАЛОСЬ", e.getString("name") + " · симпатия +" + gain + " · " + level(e.getInt("rel")));
                Life.get(p).mood = Math.min(100, Life.get(p).mood + 15);
                jealousy(p, dt.idx);
                com.yompix.novograd.gta.Social.post(s, "@" + e.getString("name").toLowerCase().replace(' ', '_'), "провели вечер с " + p.getName().getString() + " ✨", p, 30 + gain * 4, 4);
                npc.removeLater(80);
                it.remove();
                send(p);
            }
        }
    }

    private static void end(ServerPlayerEntity p, Date dt, CitizenEntity npc, java.util.Iterator<?> it) {
        if (npc != null) npc.removeLater(60);
        it.remove();
        send(p);
    }

    /** Подарок: ПКМ по человеку на свидании с цветами/шоколадом/украшением в руке. */
    private static boolean gift(ServerPlayerEntity p, CitizenEntity c, Date dt) {
        ItemStack st = p.getMainHandStack();
        NbtCompound e = list(Life.get(p)).getCompound(dt.idx);
        int v = st.isOf(Items.POPPY) || st.isOf(Items.DANDELION) || st.isOf(Items.CORNFLOWER) ? (e.getString("likes").equals("цветы") ? 8 : 4)
                : st.isOf(Items.COOKIE) ? (e.getString("likes").equals("шоколад") ? 8 : 4) : st.isOf(Items.GOLD_NUGGET) || st.isOf(Items.DIAMOND) ? 10 : 0;
        if (v == 0 || dt.bonus >= 20) return false;
        st.decrement(1);
        dt.bonus += v;
        c.busy(CitizenEntity.Activity.LAUGH, 40, null, ItemStack.EMPTY);
        p.sendMessage(Text.literal(e.getString("name") + ": «Это мне? Спасибо!»").formatted(Formatting.LIGHT_PURPLE), true);
        return true;
    }

    static void add(ServerPlayerEntity p, NbtCompound e, int v) {
        int before = e.getInt("rel");
        int rel = Math.max(0, Math.min(100, before + v));
        e.putInt("rel", rel);
        if (before < 60 && rel >= 60) {
            Achievements.give(p, "couple");
            Life.message(p, e.getString("name"), "«Мы ведь пара, да?» · помощь: " + perkName(e.getString("job")));
        }
        Life.sync(p);
    }

    private static void jealousy(ServerPlayerEntity p, int idx) {
        NbtList l = list(Life.get(p));
        for (int i = 0; i < l.size(); i++) {
            if (i == idx) continue;
            NbtCompound o = l.getCompound(i);
            if (o.getInt("rel") >= 45 && p.getRandom().nextInt(100) < 30) {
                Life.message(p, o.getString("name"), "«Видела тебя с " + l.getCompound(idx).getString("name") + ". Объяснишь?»");
                add(p, o, -15);
            }
        }
    }

    public static String perkName(String job) {
        return switch (job) {
            case "врач" -> "подлечит";
            case "механик" -> "починит машину";
            case "полицейский" -> "снимет звезду";
            case "повар" -> "накормит";
            case "бармен" -> "напоит и поднимет настроение";
            default -> "поднимет настроение";
        };
    }

    /** Помощь пары (раз в день, с 60). */
    private static void perk(ServerPlayerEntity p, int i) {
        NbtList l = list(Life.get(p));
        if (i < 0 || i >= l.size()) return;
        NbtCompound e = l.getCompound(i);
        long day = Life.day(p);
        if (e.getInt("rel") < 60 || e.getLong("perk") == day) return;
        e.putLong("perk", day);
        switch (e.getString("job")) {
            case "врач" -> p.setHealth(p.getMaxHealth());
            case "механик" -> {
                if (p.getVehicle() instanceof com.yompix.novograd.entity.CarEntity car) car.repair();
                else {
                    e.putLong("perk", 0);
                    p.sendMessage(Text.literal("Сядь в машину — починю по видеосвязи").formatted(Formatting.GRAY), true);
                    return;
                }
            }
            case "полицейский" -> {
                if (com.yompix.novograd.gta.Wanted.stars(p) > 0) com.yompix.novograd.gta.Wanted.reduce(p, 1);
            }
            case "повар" -> p.getHungerManager().add(20, 1f);
            default -> Life.get(p).mood = Math.min(100, Life.get(p).mood + 25);
        }
        Life.message(p, e.getString("name"), "«Держись, я рядом» · " + perkName(e.getString("job")));
        send(p);
    }

    /** Утро: без внимания — симпатия медленно падает. Вызывает Money.daily. */
    public static void morning(ServerPlayerEntity p, LifeData d, long day) {
        NbtList l = list(d);
        for (int i = 0; i < l.size(); i++) {
            NbtCompound e = l.getCompound(i);
            if (day - e.getLong("last") >= 2 && day % 2 == 0 && e.getInt("rel") > 5) e.putInt("rel", e.getInt("rel") - 1);
        }
    }

    public static void onLeave(ServerPlayerEntity p) {
        Date dt = DATE.remove(p.getUuid());
        if (dt != null && dt.npc != null && p.getServer() != null && p.getServer().getOverworld().getEntity(dt.npc) instanceof CitizenEntity c) c.discard();
    }

    static {
        Novograd.LOG.debug("Dating loaded");
    }
}
