package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.9: соревнования между игроками (телефон → «Спорт»).
 * <ul>
 *   <li><b>Уличная гонка</b> — «Кольцо центра» (5 точек) или «Большое кольцо» (трасса): хозяин создаёт заезд со
 *       взносом, остальные (в сети) получают приглашение и присоединяются; через 60 с (или «Старт») — отсчёт; кто в
 *       машине у старта — едет. Точки по порядку (метка на радаре), итог — по времени. Банк взносов: 1-му 70 %,
 *       2-му 30 %. Один — гонка на время: золото/серебро/бронза.</li>
 *   <li><b>Дрифт</b> — 90 секунд у старта: очки за занос (угол между носом и ходом машины 15–75° на скорости),
 *       множитель за непрерывный занос; удар — серия сгорает. Лучший счёт забирает банк.</li>
 * </ul>
 * Всё считает сервер (позиция и курс машины), клиенту — табло «sport_hud».
 */
public final class Sports {
    private Sports() {}

    public static final String[][] ROUTES = {
            {"race_dt", "Кольцо центра", "dt_1", "dt_2", "dt_3", "dt_4", "dt_5", "dt_1"},
            {"race_ring", "Большое кольцо", "race_start", "race_1", "race_2", "race_3", "race_4", "race_5", "race_finish"},
            {"drift", "Дрифт у старта", "race_start"}};
    static final int DRIFT_SEC = 90, OPEN_SEC = 60, MAX_SEC = 420;

    static final class Event {
        final int id;
        final UUID host;
        final int route, fee;
        final Map<UUID, Racer> racers = new LinkedHashMap<>();
        int stage; // 0 набор, 1 отсчёт, 2 едут, 3 итоги
        long t0;

        Event(int id, UUID host, int route, int fee, long now) {
            this.id = id;
            this.host = host;
            this.route = route;
            this.fee = fee;
            this.t0 = now;
        }

        boolean drift() {
            return ROUTES[route][0].equals("drift");
        }
    }

    static final class Racer {
        String name;
        int cp = 1;
        long finish = -1;
        double drift, chain;
        float lastYaw = Float.NaN;
        Vec3d last;
    }

    private static final List<Event> EVENTS = new ArrayList<>();
    private static int nextId = 1;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Sports::tick);
        Achievements.ALL.putIfAbsent("race_win", new String[]{"Король улиц", "Выиграть гонку у игроков"});
        Achievements.ALL.putIfAbsent("race_gold", new String[]{"Золотой круг", "Золото в гонке на время"});
        Achievements.ALL.putIfAbsent("drift_king", new String[]{"Король дрифта", "10 000 очков дрифта"});
    }

    static Event of(UUID p) {
        for (Event e : EVENTS) if (e.racers.containsKey(p)) return e;
        return null;
    }

    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        switch (type) {
            case "sport_state" -> send(p);
            case "sport_create" -> create(p, n.getInt("route"), n.getInt("fee"));
            case "sport_join" -> join(p, n.getInt("id"));
            case "sport_leave" -> leave(p);
            case "sport_start" -> {
                Event e = of(p.getUuid());
                if (e != null && e.host.equals(p.getUuid()) && e.stage == 0) countdown(p.getServer(), e);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void create(ServerPlayerEntity p, int route, int fee) {
        if (route < 0 || route >= ROUTES.length || of(p.getUuid()) != null) return;
        fee = Math.max(0, Math.min(5000, fee / 10 * 10));
        if (fee > 0 && !Life.pay(p, fee, "взнос: " + ROUTES[route][1])) return;
        Event e = new Event(nextId++, p.getUuid(), route, fee, p.getServerWorld().getTime());
        Racer r = new Racer();
        r.name = p.getName().getString();
        e.racers.put(p.getUuid(), r);
        EVENTS.add(e);
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList())
            if (o != p) Life.message(o, "Спорт", p.getName().getString() + " зовёт: " + ROUTES[route][1] + (fee > 0 ? ", взнос $" + fee : "") + ". Телефон → «Спорт».");
        Vec3d start = Story.place(p.getServerWorld(), ROUTES[route][2]);
        FunUtil.target(p, start, "СТАРТ");
        send(p);
    }

    private static void join(ServerPlayerEntity p, int id) {
        if (of(p.getUuid()) != null) return;
        for (Event e : EVENTS)
            if (e.id == id && e.stage == 0 && e.racers.size() < 8) {
                if (e.fee > 0 && !Life.pay(p, e.fee, "взнос: " + ROUTES[e.route][1])) return;
                Racer r = new Racer();
                r.name = p.getName().getString();
                e.racers.put(p.getUuid(), r);
                FunUtil.target(p, Story.place(p.getServerWorld(), ROUTES[e.route][2]), "СТАРТ");
                broadcast(p.getServer(), e, p.getName().getString() + " в заезде");
                send(p);
                return;
            }
    }

    private static void leave(ServerPlayerEntity p) {
        Event e = of(p.getUuid());
        if (e == null) return;
        e.racers.remove(p.getUuid());
        if (e.stage == 0 && e.fee > 0) Life.earn(p, e.fee, "взнос вернули"); // до старта — без потерь
        FunUtil.target(p, null, "");
        hud(p, null, null);
        if (e.racers.isEmpty()) EVENTS.remove(e);
        send(p);
    }

    private static void broadcast(MinecraftServer s, Event e, String msg) {
        for (UUID u : e.racers.keySet()) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(u);
            if (p != null) FunUtil.hint(p, "🏁 " + msg, net.minecraft.util.Formatting.AQUA);
        }
    }

    private static void countdown(MinecraftServer s, Event e) {
        ServerWorld w = s.getOverworld();
        Vec3d start = Story.place(w, ROUTES[e.route][2]);
        // в машине у старта — едет; кто не успел — деньги назад
        for (var it = e.racers.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(en.getKey());
            if (p == null || !(p.getVehicle() instanceof CarEntity) || start == null || p.squaredDistanceTo(start) > 40 * 40) {
                if (p != null) {
                    FunUtil.hint(p, "🏁 Не у старта в машине — заезд без тебя (взнос вернули)", net.minecraft.util.Formatting.GRAY);
                    if (e.fee > 0) Life.earn(p, e.fee, "взнос вернули");
                }
                it.remove();
            }
        }
        if (e.racers.isEmpty()) {
            EVENTS.remove(e);
            return;
        }
        e.stage = 1;
        e.t0 = w.getTime();
        for (UUID u : e.racers.keySet()) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(u);
            if (p != null) FunUtil.timer(p, 3);
        }
    }

    private static void tick(MinecraftServer s) {
        if (EVENTS.isEmpty()) return;
        ServerWorld w = s.getOverworld();
        long now = w.getTime();
        for (Event e : new ArrayList<>(EVENTS)) {
            String[] r = ROUTES[e.route];
            switch (e.stage) {
                case 0 -> {
                    if (now - e.t0 > OPEN_SEC * 20L) countdown(s, e);
                }
                case 1 -> {
                    if (now - e.t0 >= 60) {
                        e.stage = 2;
                        e.t0 = now;
                        for (UUID u : e.racers.keySet()) {
                            ServerPlayerEntity p = s.getPlayerManager().getPlayer(u);
                            if (p == null) continue;
                            FunUtil.title(p, e.drift() ? "ДРИФТ!" : "СТАРТ!", e.drift() ? DRIFT_SEC + " секунд — в занос!" : r[1]);
                            if (!e.drift()) FunUtil.target(p, Story.place(w, r[3]), "ТОЧКА 1");
                        }
                    }
                }
                case 2 -> run(s, w, e, now);
                default -> EVENTS.remove(e);
            }
        }
    }

    private static void run(MinecraftServer s, ServerWorld w, Event e, long now) {
        String[] r = ROUTES[e.route];
        int points = r.length - 2;
        long t = now - e.t0;
        boolean allDone = true;
        for (var en : e.racers.entrySet()) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(en.getKey());
            Racer x = en.getValue();
            if (p == null || x.finish >= 0) continue;
            allDone = false;
            if (e.drift()) {
                drift(p, x);
                if (t % 10 == 0) hud(p, e, x);
                continue;
            }
            Vec3d cp = Story.place(w, r[2 + x.cp]);
            if (cp != null && p.squaredDistanceTo(cp.x, p.getY(), cp.z) < 16 * 16) {
                x.cp++;
                p.playSoundToPlayer(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), net.minecraft.sound.SoundCategory.MASTER, 0.8f, 1.6f);
                if (x.cp >= points + 1) {
                    x.finish = t;
                    FunUtil.target(p, null, "");
                    FunUtil.title(p, "ФИНИШ", fmt(t));
                } else FunUtil.target(p, Story.place(w, r[2 + x.cp]), x.cp == points ? "ФИНИШ" : "ТОЧКА " + x.cp);
            }
            if (t % 20 == 0) hud(p, e, x);
        }
        boolean timeUp = e.drift() ? t >= DRIFT_SEC * 20L : t >= MAX_SEC * 20L;
        if (allDone || timeUp) results(s, e, t);
    }

    /** Очки дрифта за тик: угол заноса × скорость × серия. */
    static void drift(ServerPlayerEntity p, Racer x) {
        if (!(p.getVehicle() instanceof CarEntity car)) {
            x.chain = 0;
            return;
        }
        Vec3d pos = car.getPos();
        if (x.last != null) {
            Vec3d mv = pos.subtract(x.last);
            double v = mv.horizontalLength();
            if (v > 0.25) {
                float heading = (float) Math.toDegrees(Math.atan2(-mv.x, mv.z));
                float ang = Math.abs(MathHelper.wrapDegrees(heading - car.getYaw()));
                if (ang > 90) ang = 180 - ang; // задом — тоже занос, но не «очки за разворот»
                if (ang >= 15 && ang <= 75) {
                    x.chain = Math.min(5, x.chain + 0.02);
                    x.drift += (ang / 30.0) * v * 20 * (1 + x.chain);
                } else x.chain = Math.max(0, x.chain - 0.1);
            } else x.chain = 0;
            if (car.wrecked()) x.chain = 0;
        }
        x.last = pos;
    }

    private static void results(MinecraftServer s, Event e, long t) {
        e.stage = 3;
        List<Map.Entry<UUID, Racer>> order = new ArrayList<>(e.racers.entrySet());
        if (e.drift()) order.sort((a, b) -> Double.compare(b.getValue().drift, a.getValue().drift));
        else order.sort((a, b) -> Long.compare(a.getValue().finish < 0 ? Long.MAX_VALUE : a.getValue().finish, b.getValue().finish < 0 ? Long.MAX_VALUE : b.getValue().finish));
        int pool = e.fee * e.racers.size();
        StringBuilder board = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            Racer x = order.get(i).getValue();
            board.append(i + 1).append(". ").append(x.name).append(" — ").append(e.drift() ? (int) x.drift + " очк." : x.finish < 0 ? "не доехал" : fmt(x.finish)).append("   ");
        }
        for (int i = 0; i < order.size(); i++) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(order.get(i).getKey());
            if (p == null) continue;
            Racer x = order.get(i).getValue();
            FunUtil.target(p, null, "");
            hud(p, null, null);
            boolean finished = e.drift() || x.finish >= 0;
            if (order.size() > 1) {
                int prize = i == 0 ? pool * 70 / 100 : i == 1 ? pool * 30 / 100 : 0;
                if (prize > 0 && finished) Life.earn(p, prize, (i + 1) + " место");
                if (i == 0 && finished) {
                    Achievements.give(p, "race_win");
                    Social.post(s, "@gonshchik_99", p.getName().getString() + " выиграл(а) «" + ROUTES[e.route][1] + "» 🏁", p, 60 + pool / 20, 20);
                }
            } else if (finished) {
                // один — на время / на очки
                int medal = medal(e, x);
                int prize = new int[]{0, 60, 150, 300}[medal];
                if (prize > 0) Life.earn(p, prize, new String[]{"", "бронза", "серебро", "золото"}[medal]);
                if (medal == 3) Achievements.give(p, e.drift() ? "drift_king" : "race_gold");
            }
            if (e.drift() && x.drift >= 10000) Achievements.give(p, "drift_king");
            Life.message(p, "Спорт", board.toString().trim());
        }
        EVENTS.remove(e);
    }

    /** Медаль соло: 0 нет, 1 бронза, 2 серебро, 3 золото. Время — от длины трассы (≈ 1,05 блока/тик — золото). */
    static int medal(Event e, Racer x) {
        if (e.drift()) return x.drift >= 9000 ? 3 : x.drift >= 5000 ? 2 : x.drift >= 2000 ? 1 : 0;
        double len = e.route == 0 ? 1500 : 3600;
        double gold = len / 1.05, silver = len / 0.85, bronze = len / 0.65;
        return x.finish <= gold ? 3 : x.finish <= silver ? 2 : x.finish <= bronze ? 1 : 0;
    }

    static String fmt(long ticks) {
        long ms = ticks * 50;
        return String.format("%d:%02d.%d", ms / 60000, ms / 1000 % 60, ms / 100 % 10);
    }

    /** Табло на экране: точка/время или очки дрифта и серия. */
    private static void hud(ServerPlayerEntity p, Event e, Racer x) {
        NbtCompound n = new NbtCompound();
        if (e != null) {
            n.putString("name", ROUTES[e.route][1]);
            n.putBoolean("drift", e.drift());
            n.putLong("t", p.getServerWorld().getTime() - e.t0);
            n.putInt("cp", x.cp - 1);
            n.putInt("cps", ROUTES[e.route].length - 2);
            n.putInt("score", (int) x.drift);
            n.putFloat("chain", (float) x.chain);
            int place = 1;
            for (Racer o : e.racers.values()) if (o != x && (e.drift() ? o.drift > x.drift : o.cp > x.cp)) place++;
            n.putInt("place", place);
            n.putInt("of", e.racers.size());
            n.putInt("limit", e.drift() ? DRIFT_SEC : MAX_SEC);
        }
        LifeNet.send(p, "sport_hud", n);
    }

    public static void send(ServerPlayerEntity p) {
        NbtCompound out = new NbtCompound();
        NbtList l = new NbtList();
        for (Event e : EVENTS) {
            NbtCompound c = new NbtCompound();
            c.putInt("id", e.id);
            c.putString("name", ROUTES[e.route][1]);
            c.putInt("fee", e.fee);
            c.putInt("n", e.racers.size());
            c.putInt("stage", e.stage);
            c.putBoolean("mine", e.racers.containsKey(p.getUuid()));
            c.putBoolean("host", e.host.equals(p.getUuid()));
            StringBuilder names = new StringBuilder();
            for (Racer r : e.racers.values()) names.append(names.length() > 0 ? ", " : "").append(r.name);
            c.putString("who", names.toString());
            l.add(c);
        }
        out.put("events", l);
        NbtList routes = new NbtList();
        for (String[] r : ROUTES) routes.add(net.minecraft.nbt.NbtString.of(r[1]));
        out.put("routes", routes);
        Games.fill(out, p.getServerWorld());
        LifeNet.send(p, "sport", out);
    }

    public static void onLeave(ServerPlayerEntity p) {
        Event e = of(p.getUuid());
        if (e != null) {
            e.racers.remove(p.getUuid());
            if (e.racers.isEmpty()) EVENTS.remove(e);
        }
    }
}
