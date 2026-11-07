package com.yompix.novograd.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.gta.FunUtil;
import com.yompix.novograd.gta.Wanted;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Звонки героев, как в GTA: между миссиями, в свободной игре, раз в 8–15 минут звонит или пишет кто-то из своих.
 * Шутки, сплетни, реакции на твои дела (розыск, налёты, авария, пентхаус, спортзал, казино), приглашения
 * (гонка с Ники, выпивка у Розы), просьбы о помощи — короткие миссии call_* (скрытые побочные, их пишет tools/story.py).
 * Звонки помнят, что уже было (сыгранные не повторяются, у повторяемых — пауза), а за помощь растёт дружба,
 * которая даёт бонусы: Ники — скидку на тюнинг, Брик — бойцов на сутки, Роза — выпивку и здоровье в «Неоне»,
 * Питерсон — раз в день снимает одну звезду. Сценарии — novograd_story.json → calls. Окно — client/story/CallClient.
 */
public final class Calls {
    private Calls() {}

    /** Окно реакции: событие считается «свежим» 20 минут (в тиках мира). */
    private static final long EVENT_WINDOW = 20L * 60 * 20;
    private static final int RING_SEC = 25, MAX_FRIEND = 10;
    /** Персонажи с бонусом дружбы: с какого уровня он открывается. */
    public static final int PERK_LEVEL = 3;
    private static final Map<String, Integer> START = Map.of("gleb", 4, "nika", 2, "rosa", 1, "petrov", 1, "tema", 1);
    private static final Random R = new Random();

    // ---------------------------------------------------------------- то, что живёт только пока игрок в игре
    static final class Run {
        int roam, need = -1;               // секунд свободной игры накоплено / нужно до следующего звонка
        String ringing = "", active = "";  // звонит / идёт разговор
        int ringSec, activeSec;
        String prefer = "";                // «перезвоню» — этот герой позвонит следующим
        final Map<String, Integer> snooze = new HashMap<>(); // сброшенный/пропущенный звонок — не раньше, чем через N секунд
        // наблюдение за делами игрока
        boolean primed;
        int stars, carHp = -1, homes, strength, casinoWon;
        UUID car;
        boolean penthouse, hadHeat, dead;
        int sec;
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    static Run run(ServerPlayerEntity p) {
        return RUNS.computeIfAbsent(p.getUuid(), u -> new Run());
    }

    // ---------------------------------------------------------------- сохранение: дружба, сыгранные звонки, события, бонусы
    static final class State extends PersistentState {
        final Map<UUID, NbtCompound> all = new HashMap<>();
        static final Type<State> TYPE = new Type<>(State::new, (n, reg) -> load(n), null);

        static State load(NbtCompound n) {
            State s = new State();
            for (String k : n.getKeys()) {
                try {
                    s.all.put(UUID.fromString(k), n.getCompound(k));
                } catch (IllegalArgumentException ignored) {}
            }
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup reg) {
            all.forEach((u, c) -> n.put(u.toString(), c));
            return n;
        }
    }

    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(State.TYPE, "novograd_calls");
    }

    /** Данные игрока: fr — дружба, done — когда звонок был (время мира), ev — события, perk — день бонуса, dec — сбросы подряд. */
    static NbtCompound data(ServerPlayerEntity p) {
        State st = state(p.getServer());
        NbtCompound c = st.all.computeIfAbsent(p.getUuid(), u -> new NbtCompound());
        for (String k : new String[]{"fr", "done", "ev", "perk", "dec", "help"}) if (!c.contains(k)) c.put(k, new NbtCompound());
        if (!c.getBoolean("init")) {
            c.putBoolean("init", true);
            NbtCompound fr = c.getCompound("fr");
            START.forEach(fr::putInt);
            LifeData d = Life.get(p);
            if (d.flags.contains("a1_rosa_ok")) fr.putInt("rosa", fr.getInt("rosa") + 1);
            if (d.flags.contains("a3_yes")) fr.putInt("petrov", fr.getInt("petrov") + 1);
            if (d.flags.contains("end_cop")) fr.putInt("petrov", fr.getInt("petrov") + 1);
            if (d.flags.contains("end_racer")) fr.putInt("nika", fr.getInt("nika") + 1);
        }
        st.markDirty();
        return c;
    }

    public static int friend(ServerPlayerEntity p, String who) {
        return data(p).getCompound("fr").getInt(who);
    }

    static void addFriend(ServerPlayerEntity p, String who, int n) {
        if (n == 0 || who.isEmpty()) return;
        NbtCompound fr = data(p).getCompound("fr");
        int before = fr.getInt(who), after = Math.max(0, Math.min(MAX_FRIEND, before + n));
        fr.putInt(who, after);
        state(p.getServer()).markDirty();
        if (after > before)
            p.sendMessage(Text.literal("♥ " + name(who) + ": дружба " + after + "/" + MAX_FRIEND + (before < PERK_LEVEL && after >= PERK_LEVEL && perkText(who) != null
                    ? " — открыт бонус: " + perkText(who) : "")).formatted(Formatting.LIGHT_PURPLE), false);
    }

    // ---------------------------------------------------------------- сценарии
    static JsonArray calls() {
        JsonObject r = Story.root();
        return r.has("calls") ? r.getAsJsonArray("calls") : new JsonArray();
    }

    @Nullable
    static JsonObject call(String id) {
        for (JsonElement e : calls()) if (Story.str(e.getAsJsonObject(), "id", "").equals(id)) return e.getAsJsonObject();
        return null;
    }

    static String name(String who) {
        JsonObject ch = Story.root().getAsJsonObject("characters").getAsJsonObject(who);
        return ch == null ? who : Story.str(ch, "name", who);
    }

    static int color(String who) {
        JsonObject ch = Story.root().getAsJsonObject("characters").getAsJsonObject(who);
        try {
            return ch == null ? 0xFFFFFF : Integer.parseInt(Story.str(ch, "color", "ffffff"), 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }

    /** Пройдена ли миссия [глава, миссия] основного сюжета. */
    static boolean passed(LifeData d, JsonArray cm) {
        int c = cm.get(0).getAsInt(), m = cm.get(1).getAsInt();
        if (!d.storyStarted) return false;
        return d.storyDone || d.chapter > c || d.chapter == c && d.mission > m;
    }

    /** Акт VI открыт, но первая миссия ещё не начата — Питерсон напоминает про участок. */
    static boolean copIdle(LifeData d) {
        if (!d.flags.contains("end_cop") && !d.flags.contains("oath")) return false;
        JsonObject ch = Story.chapter(d);
        return ch != null && Story.str(ch, "id", "").equals("a6") && d.mission == 0 && Story.idle(d);
    }

    static boolean flag(LifeData d, String f) {
        return f.equals("cop_idle") ? copIdle(d) : d.flags.contains(f);
    }

    /** Герой уже знаком игроку (у него есть хотя бы один звонок с пройденным «from»). */
    static boolean known(LifeData d, String who) {
        for (JsonElement e : calls()) {
            JsonObject c = e.getAsJsonObject();
            if (!Story.str(c, "who", "").equals(who)) continue;
            JsonObject w = c.getAsJsonObject("when");
            if (w.has("from") && passed(d, w.getAsJsonArray("from"))) return true;
        }
        return false;
    }

    /** Подходит ли звонок сейчас. ignoreEvent — игрок сам набрал номер (реакции на события не нужны). */
    static boolean fits(ServerPlayerEntity p, LifeData d, NbtCompound st, JsonObject c, long now) {
        JsonObject w = c.getAsJsonObject("when");
        String id = Story.str(c, "id", ""), who = Story.str(c, "who", "");
        NbtCompound done = st.getCompound("done");
        if (done.contains(id)) {
            int rep = Story.num(c, "repeat", 0);
            if (rep <= 0 || now - done.getLong(id) < rep * 1200L) return false;
        }
        if (w.has("from") && !passed(d, w.getAsJsonArray("from"))) return false;
        if (w.has("until") && passed(d, w.getAsJsonArray("until"))) return false;
        if (w.has("need")) for (JsonElement f : w.getAsJsonArray("need")) if (!flag(d, f.getAsString())) return false;
        if (w.has("no")) for (JsonElement f : w.getAsJsonArray("no")) if (flag(d, f.getAsString())) return false;
        if (w.has("friend") && st.getCompound("fr").getInt(who) < w.get("friend").getAsInt()) return false;
        if (w.has("after") && !done.contains(w.get("after").getAsString())) return false;
        if (w.has("event")) {
            long at = st.getCompound("ev").getLong(w.get("event").getAsString());
            if (at <= 0 || now - at > EVENT_WINDOW || done.contains(id) && done.getLong(id) >= at) return false;
        }
        // предложение с миссией — только если такая миссия есть
        if (c.has("offer")) for (JsonElement a : c.getAsJsonObject("offer").getAsJsonArray("do")) {
            JsonArray arr = a.getAsJsonArray();
            if (arr.get(0).getAsString().equals("side") && SideMissions.get(arr.get(1).getAsString()) == null) return false;
        }
        return true;
    }

    /** Выбрать звонок: свежие события важнее всего, потом новые сюжетные, потом повторяемые. */
    @Nullable
    static JsonObject pick(ServerPlayerEntity p, @Nullable String onlyWho) {
        LifeData d = Life.get(p);
        NbtCompound st = data(p);
        Run r = run(p);
        long now = p.getServerWorld().getTime();
        JsonObject best = null;
        int bestScore = Integer.MIN_VALUE;
        String last = st.getString("last");
        for (JsonElement e : calls()) {
            JsonObject c = e.getAsJsonObject();
            String id = Story.str(c, "id", ""), who = Story.str(c, "who", "");
            if (onlyWho != null && !who.equals(onlyWho)) continue;
            if (onlyWho == null && r.snooze.getOrDefault(id, 0) > r.sec) continue;
            if (!fits(p, d, st, c, now)) continue;
            int score = Story.num(c, "prio", 0) * 10 + R.nextInt(25);
            if (c.getAsJsonObject("when").has("event")) score += 1000;
            if (!st.getCompound("done").contains(id)) score += 60;
            if (who.equals(r.prefer)) score += 500;
            if (who.equals(last)) score -= 40;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** Звонить сейчас можно: свободная игра, не миссия, не кат-сцена, не погоня, не розыск. */
    static boolean free(ServerPlayerEntity p, LifeData d) {
        if (!p.isAlive() || p.isSpectator() || d.prevMode >= 0 || Director.running(p) || !d.storyStarted) return false;
        if (!d.storyDone && d.chapter == 0 && d.mission == 0) return false; // ещё идёт пролог
        return FunUtil.freeRoam(p) && !FunUtil.busy(p) && Wanted.stars(p) == 0;
    }

    private static int nextGap(ServerPlayerEntity p) {
        if (Boolean.getBoolean("novograd.calls.fast")) return 8;
        return 480 + R.nextInt(421); // 8–15 минут свободной игры
    }

    // ---------------------------------------------------------------- тик
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Calls::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> RUNS.remove(h.player.getUuid()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            if (!alive) event(newP, "wasted");
        });
    }

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 0) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            try {
                second(p);
            } catch (RuntimeException e) {
                com.yompix.novograd.Novograd.LOG.warn("Звонки: ошибка у {}", p.getName().getString(), e);
            }
        }
    }

    private static void second(ServerPlayerEntity p) {
        Run r = run(p);
        r.sec++;
        LifeData d = Life.get(p);
        watch(p, d, r);
        rosaBar(p, d);
        if (!r.ringing.isEmpty()) {
            if (++r.ringSec > RING_SEC) missed(p, r);
            return;
        }
        if (!r.active.isEmpty()) {
            if (++r.activeSec > 120) r.active = ""; // клиент пропал посреди разговора
            return;
        }
        if (!free(p, d)) return;
        r.roam++;
        if (r.need < 0) r.need = Boolean.getBoolean("novograd.calls.fast") ? 6 : 180 + R.nextInt(180); // первый звонок за сессию — через 3–6 минут
        boolean react = r.roam >= 90 && freshEvent(p);
        if (r.roam < r.need && !react) return;
        JsonObject c = pick(p, null);
        r.roam = 0;
        r.need = nextGap(p);
        if (c != null) ring(p, c);
    }

    /** Есть событие новее последнего звонка — повод позвонить пораньше. */
    private static boolean freshEvent(ServerPlayerEntity p) {
        NbtCompound st = data(p), ev = st.getCompound("ev");
        long last = st.getLong("lastAt"), now = p.getServerWorld().getTime();
        for (String k : ev.getKeys()) if (ev.getLong(k) > last && now - ev.getLong(k) < EVENT_WINDOW) return true;
        return false;
    }

    // ---------------------------------------------------------------- события
    /** Что-то случилось — может стать поводом для звонка (crash, escaped, busted, wasted, bank, store, hero…). */
    public static void event(ServerPlayerEntity p, String kind) {
        if (p.getServer() == null) return;
        data(p).getCompound("ev").putLong(kind, p.getServerWorld().getTime());
        state(p.getServer()).markDirty();
    }

    /** Большие титры активностей (gta/News.onTitle): налёты, бизнес, жильё, геройство, гонки. */
    public static void onTitle(ServerPlayerEntity p, String title) {
        String t = title == null ? "" : title.toUpperCase(Locale.ROOT);
        String ev = switch (t) {
            case "ОГРАБЛЕНИЕ УДАЛОСЬ" -> "bank";
            case "КАССА ВЗЯТА", "КАССА НАША", "САЛОН ОБЧИЩЕН", "СУМКА ТВОЯ" -> "store";
            case "ИНКАССАТОР ВЗЯТ" -> "armored";
            case "БОЙНЯ ПРОЙДЕНА", "ВОЙНА ЗА РАЙОН" -> "massacre";
            case "БИЗНЕС КУПЛЕН" -> "business";
            case "НЕДВИЖИМОСТЬ КУПЛЕНА" -> "home";
            case "ГРАБИТЕЛИ ОСТАНОВЛЕНЫ", "СУМКА ВОЗВРАЩЕНА", "ПОМОЩЬ ОКАЗАНА", "ПРЕСТУПНИКИ ОСТАНОВЛЕНЫ" -> "hero";
            case "ПОБЕДА!" -> "race";
            case "ТРЮКОВОЙ ПРЫЖОК!" -> "stunt";
            default -> t.contains("ОГРАБЛЕНИЕ") && t.contains("УДАЛ") || t.contains("НАЛЁТ") && t.contains("УДАЛ") ? "bank" : null;
        };
        if (ev != null) event(p, ev);
    }

    /** Раз в секунду: розыск, авария, жильё, спортзал, казино. */
    private static void watch(ServerPlayerEntity p, LifeData d, Run r) {
        int stars = Wanted.stars(p);
        CarEntity car = p.getVehicle() instanceof CarEntity c && c.getControllingPassenger() == p ? c : null;
        if (!r.primed || r.sec % 10 == 0) {
            int homes = 0;
            boolean pent = false;
            for (var l : com.yompix.novograd.world.Housing.homes(p)) {
                homes++;
                pent |= l.kind == com.yompix.novograd.world.Housing.Kind.PENTHOUSE;
            }
            if (r.primed && homes > r.homes) event(p, pent && !r.penthouse ? "penthouse" : "home");
            r.homes = homes;
            r.penthouse = pent;
        }
        if (!r.primed) {
            r.primed = true;
            r.stars = stars;
            r.strength = d.strength + d.stamina;
            r.casinoWon = d.casinoWon;
            return;
        }
        if (stars >= 3 && r.stars < 3) event(p, "stars3");
        if (stars >= 4 && r.stars < 4) event(p, "stars4");
        if (stars >= 2) r.hadHeat = true;
        if (stars == 0 && r.stars > 0) {
            Vec3d ps = Story.place(p.getServerWorld(), "police_station");
            if (ps != null && p.squaredDistanceTo(ps) < 16 * 16) event(p, "busted");
            else if (p.isAlive() && r.hadHeat) event(p, "escaped");
            r.hadHeat = false;
        }
        r.stars = stars;
        if (car != null) {
            int hp = car.health();
            if (car.getUuid().equals(r.car) && r.carHp - hp >= 30) event(p, "crash");
            r.car = car.getUuid();
            r.carHp = hp;
        } else r.car = null;
        int str = d.strength + d.stamina;
        if (str > r.strength) event(p, "gym");
        r.strength = str;
        if (d.casinoWon - r.casinoWon >= 2000) event(p, "casino");
        r.casinoWon = d.casinoWon;
    }

    /** Бонус Розы: раз в игровой день в «Неоне» — выпивка за счёт заведения и полное здоровье. */
    private static void rosaBar(ServerPlayerEntity p, LifeData d) {
        if (p.age % 3 != 0 || !p.isAlive() || p.isSpectator() || friend(p, "rosa") < PERK_LEVEL || !known(d, "rosa")) return;
        Vec3d bar = Story.place(p.getServerWorld(), "club_neon");
        if (bar == null || p.squaredDistanceTo(bar) > 12 * 12) return;
        long day = Life.day(p);
        NbtCompound perk = data(p).getCompound("perk");
        if (perk.contains("rosa") && perk.getLong("rosa") >= day) return;
        perk.putLong("rosa", day);
        state(p.getServer()).markDirty();
        Actions.run(p, d, heal());
        Life.message(p, name("rosa"), friend(p, "rosa") >= 6 ? "Твой столик ждёт. Двойной фирменный — за счёт заведения." : "Садись, налью за счёт заведения. Выглядишь уставшим.");
    }

    private static JsonArray heal() {
        JsonArray a = new JsonArray();
        a.add("heal");
        return a;
    }

    // ---------------------------------------------------------------- звонок
    static void ring(ServerPlayerEntity p, JsonObject c) {
        Run r = run(p);
        String id = Story.str(c, "id", ""), who = Story.str(c, "who", "");
        NbtCompound st = data(p);
        st.putString("last", who);
        st.putLong("lastAt", p.getServerWorld().getTime());
        if (who.equals(r.prefer)) r.prefer = "";
        if (Story.str(c, "kind", "call").equals("sms")) {
            for (JsonElement e : c.getAsJsonArray("lines")) Life.message(p, name(who), Story.str(e.getAsJsonObject(), "text", ""));
            done(p, c);
            return;
        }
        r.ringing = id;
        r.ringSec = 0;
        LifeNet.send(p, "call_ring", packet(c));
    }

    static NbtCompound packet(JsonObject c) {
        String who = Story.str(c, "who", "");
        NbtCompound n = new NbtCompound();
        n.putString("id", Story.str(c, "id", ""));
        n.putString("who", who);
        n.putString("name", name(who));
        n.putInt("color", color(who));
        n.put("lines", lines(c.getAsJsonArray("lines")));
        if (c.has("offer")) {
            JsonObject o = c.getAsJsonObject("offer");
            NbtCompound on = new NbtCompound();
            on.putString("q", Story.str(o, "q", ""));
            on.putString("yes", Story.str(o, "yes", "Да"));
            on.putString("no", Story.str(o, "no", "Нет"));
            if (o.has("yes_lines")) on.put("yes_lines", lines(o.getAsJsonArray("yes_lines")));
            if (o.has("no_lines")) on.put("no_lines", lines(o.getAsJsonArray("no_lines")));
            n.put("offer", on);
        }
        return n;
    }

    static NbtList lines(JsonArray arr) {
        NbtList out = new NbtList();
        for (JsonElement e : arr) {
            JsonObject l = e.getAsJsonObject();
            String who = Story.str(l, "who", "narrator");
            NbtCompound ln = new NbtCompound();
            ln.putString("who", who);
            ln.putString("name", name(who));
            ln.putInt("color", color(who));
            ln.putString("text", Story.str(l, "text", ""));
            ln.putString("voice", Story.str(l, "voice", ""));
            out.add(ln);
        }
        return out;
    }

    private static void missed(ServerPlayerEntity p, Run r) {
        JsonObject c = call(r.ringing);
        r.snooze.put(r.ringing, r.sec + 600);
        r.ringing = "";
        LifeNet.send(p, "call_stop", new NbtCompound());
        if (c != null) Life.message(p, name(Story.str(c, "who", "")), "Пропущенный вызов. Перезвони, когда сможешь.");
        r.prefer = c == null ? "" : Story.str(c, "who", "");
        r.need = Math.min(r.need, 240); // перезвонят через несколько минут
    }

    static void done(ServerPlayerEntity p, JsonObject c) {
        data(p).getCompound("done").putLong(Story.str(c, "id", ""), p.getServerWorld().getTime());
        state(p.getServer()).markDirty();
    }

    // ---------------------------------------------------------------- пакеты от клиента
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        Run r = run(p);
        switch (type) {
            case "call_answer" -> {
                String id = n.getString("id");
                if (!id.equals(r.ringing)) return true;
                JsonObject c = call(id);
                r.ringing = "";
                if (c == null) return true;
                String who = Story.str(c, "who", "");
                NbtCompound dec = data(p).getCompound("dec");
                if (n.getBoolean("yes")) {
                    r.active = id;
                    r.activeSec = 0;
                    dec.putInt(who, 0);
                    addFriend(p, who, data(p).getCompound("done").contains(id) ? 0 : Story.num(c, "friend", 1));
                } else {
                    r.snooze.put(id, r.sec + 900);
                    int k = dec.getInt(who) + 1;
                    if (k >= 3) { // сбросил три раза подряд — обиделись
                        k = 0;
                        addFriend(p, who, -1);
                        Life.message(p, name(who), "Ну и не бери трубку. Я запомнил(а).");
                    }
                    dec.putInt(who, k);
                    state(p.getServer()).markDirty();
                }
            }
            case "call_end" -> {
                String id = n.getString("id");
                if (!id.equals(r.active)) return true;
                r.active = "";
                JsonObject c = call(id);
                if (c == null) return true;
                done(p, c);
                if (c.has("offer") && n.getInt("choice") == 0) offerYes(p, c);
            }
            case "call_busy" -> { // экран занят (разговор, кат-сцена) — позвонят чуть позже
                if (n.getString("id").equals(r.ringing)) {
                    r.ringing = "";
                    r.roam = Math.max(0, r.need - 45);
                }
            }
            case "call_contacts" -> sendContacts(p);
            case "call_dial" -> dial(p, n.getString("who"));
            case "call_perk" -> perk(p, n.getString("who"));
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void offerYes(ServerPlayerEntity p, JsonObject c) {
        String who = Story.str(c, "who", "");
        LifeData d = Life.get(p);
        for (JsonElement e : c.getAsJsonObject("offer").getAsJsonArray("do")) {
            JsonArray a = e.getAsJsonArray();
            switch (a.get(0).getAsString()) {
                case "bigheist" -> { // наводка на большое дело (gta/BigHeists, акт IV+), раньше — налёт попроще (запасная миссия)
                    if (com.yompix.novograd.gta.Economy.act(d) >= 3) {
                        com.yompix.novograd.gta.BigHeists.offer(p, a.get(1).getAsString());
                        Life.message(p, name(who), "Седой тебе напишет. Я сказал, что ты свой.");
                    } else if (a.size() > 2) startSide(p, d, who, a.get(2).getAsString());
                }
                case "contract" -> com.yompix.novograd.gta.Contracts.offer(p); // заказы с незнакомого номера
                case "smuggle" -> com.yompix.novograd.gta.Smuggling.offer(p); // партии от Рыбака
                case "side", "heist" -> startSide(p, d, who, a.get(1).getAsString());
                case "oath" -> {
                    d.flags.add("oath");
                    addFriend(p, "petrov", 1);
                    Story.resume(p);
                    Life.message(p, name("petrov"), "Жду тебя в участке. Значок уже на столе.");
                }
                case "money" -> Life.earn(p, a.get(1).getAsInt(), name(who));
                case "flag" -> d.flags.add(a.get(1).getAsString());
                case "friend" -> addFriend(p, a.get(1).getAsString(), a.get(2).getAsInt());
                default -> {}
            }
        }
        Life.sync(p);
    }

    private static void startSide(ServerPlayerEntity p, LifeData d, String who, String id) {
        if (!d.side.isEmpty() || !FunUtil.freeRoam(p) || FunUtil.busy(p)) {
            Life.message(p, name(who), "Сначала закончи то, чем занят. Я подожду.");
            run(p).prefer = who;
        } else SideMissions.start(p, id);
    }

    /** Просьба героя выполнена (SideMissions, скрытая миссия call_*). */
    static void onHelpDone(ServerPlayerEntity p, String mission, String who) {
        if (who.isEmpty()) return;
        addFriend(p, who, 2);
        NbtCompound st = data(p);
        st.getCompound("help").putInt(who, st.getCompound("help").getInt(who) + 1);
        state(p.getServer()).markDirty();
    }

    /** Игрок сам набрал номер из «Контактов». */
    private static void dial(ServerPlayerEntity p, String who) {
        LifeData d = Life.get(p);
        Run r = run(p);
        if (!known(d, who) || !r.ringing.isEmpty() || !r.active.isEmpty()) return;
        if (!free(p, d)) {
            toast(p, name(who) + " сейчас не может говорить");
            return;
        }
        JsonObject c = pick(p, who);
        if (c == null) {
            toast(p, name(who) + " не берёт трубку. Перезвонит позже.");
            r.prefer = who;
            r.need = Math.min(r.need < 0 ? 300 : r.need, r.roam + 120);
            return;
        }
        String id = Story.str(c, "id", "");
        if (Story.str(c, "kind", "call").equals("sms")) {
            ring(p, c);
            return;
        }
        // исходящий: сразу разговор, без звонка
        data(p).putString("last", who);
        r.active = id;
        r.activeSec = 0;
        addFriend(p, who, data(p).getCompound("done").contains(id) ? 0 : Story.num(c, "friend", 1));
        NbtCompound n = packet(c);
        n.putBoolean("outgoing", true);
        LifeNet.send(p, "call_ring", n);
    }

    static void toast(ServerPlayerEntity p, String s) {
        p.sendMessage(Text.literal("📞 " + s).formatted(Formatting.GRAY), true);
    }

    // ---------------------------------------------------------------- бонусы дружбы
    @Nullable
    static String perkText(String who) {
        return switch (who) {
            case "nika" -> "тюнинг на 25% дешевле (с 6♥ — 40%)";
            case "kirpich" -> "бойцы «Ящеров» на сутки, раз в день";
            case "rosa" -> "выпивка и здоровье в «Неоне», раз в день";
            case "petrov" -> "−1★ розыска, раз в день";
            default -> null;
        };
    }

    /** Цена тюнинга со скидкой Ники (gta/Garage). */
    public static int tunePrice(ServerPlayerEntity p, int price) {
        int f = friend(p, "nika");
        if (f < PERK_LEVEL || !known(Life.get(p), "nika")) return price;
        return Math.round(price * (f >= 6 ? 0.6f : 0.75f));
    }

    private static boolean perkToday(ServerPlayerEntity p, String who) {
        NbtCompound perk = data(p).getCompound("perk");
        return perk.contains(who) && perk.getLong(who) >= Life.day(p);
    }

    private static void perk(ServerPlayerEntity p, String who) {
        LifeData d = Life.get(p);
        if (!known(d, who) || friend(p, who) < PERK_LEVEL || perkToday(p, who)) return;
        switch (who) {
            case "petrov" -> {
                int s = Wanted.stars(p);
                if (s <= 0) {
                    toast(p, "Питерсон: «Тебя никто не ищет. Пока.»");
                    return;
                }
                Wanted.clear(p);
                if (s > 1) Wanted.add(p, 0, "прежние нарушения", s - 1);
                Life.message(p, name(who), "Один рапорт потерялся. Случайно. Больше так не делай.");
            }
            case "kirpich" -> {
                if (com.yompix.novograd.gta.Economy.act(d) < 3) return;
                int n = com.yompix.novograd.gta.Bodyguards.loan(p, friend(p, who) >= 6 ? 3 : 2);
                if (n <= 0) {
                    toast(p, "Брик: «Мест нет — у тебя и так полная охрана»");
                    return;
                }
                Life.message(p, name(who), "Ребята выехали. Сутки они твои. Потом сами уйдут — не держи.");
            }
            default -> {
                return;
            }
        }
        data(p).getCompound("perk").putLong(who, Life.day(p));
        state(p.getServer()).markDirty();
        sendContacts(p);
    }

    private static final String[] TITLES = {"Знакомый", "Знакомый", "Приятель", "Приятель", "Друг", "Друг", "Хороший друг", "Хороший друг", "Близкий друг", "Близкий друг", "Как родной"};

    static void sendContacts(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtList list = new NbtList();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonElement e : calls()) seen.add(Story.str(e.getAsJsonObject(), "who", ""));
        for (String who : seen) {
            if (!known(d, who)) continue;
            int f = friend(p, who);
            NbtCompound c = new NbtCompound();
            c.putString("who", who);
            c.putString("name", name(who));
            c.putInt("color", color(who));
            c.putInt("fr", f);
            c.putString("title", TITLES[Math.min(f, TITLES.length - 1)]);
            String perk = perkText(who);
            if (perk != null) {
                c.putString("perk", perk);
                c.putBoolean("perkOn", f >= PERK_LEVEL);
                boolean button = who.equals("petrov") || who.equals("kirpich") && com.yompix.novograd.gta.Economy.act(d) >= 3;
                if (button) {
                    c.putString("perkBtn", who.equals("petrov") ? "Замять дело (−1★)" : "Прислать бойцов");
                    c.putBoolean("perkReady", f >= PERK_LEVEL && !perkToday(p, who));
                }
            }
            NbtCompound help = data(p).getCompound("help");
            c.putInt("help", help.getInt(who));
            list.add(c);
        }
        NbtCompound n = new NbtCompound();
        n.put("list", list);
        LifeNet.send(p, "call_contacts", n);
    }

    // ---------------------------------------------------------------- для автотеста
    /** Позвонить прямо сейчас (id звонка или пусто — выбрать самому). */
    public static String debugRing(ServerPlayerEntity p, String id) {
        Run r = run(p);
        r.ringing = "";
        r.active = "";
        JsonObject c = id.isEmpty() ? pick(p, null) : call(id);
        if (c != null) ring(p, c);
        return c == null ? "" : Story.str(c, "id", "");
    }

    public static void debugFriend(ServerPlayerEntity p, String who, int n) {
        data(p).getCompound("fr").putInt(who, n);
    }

    public static String debugState(ServerPlayerEntity p) {
        Run r = run(p);
        NbtCompound st = data(p);
        return "ringing=" + r.ringing + " active=" + r.active + " roam=" + r.roam + "/" + r.need + " fr=" + st.getCompound("fr") + " done=" + st.getCompound("done").getKeys()
                + " ev=" + st.getCompound("ev").getKeys() + " free=" + free(p, Life.get(p));
    }
}
