package com.yompix.novograd.gta;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.10: мини-игры против живого игрока — бильярд, боулинг, баскетбол, гольф.
 * Вызов — из телефона («Спорт» → «Вызов игроку») или вживую (Shift+ПКМ по игроку пустой рукой: игра ближайшей площадки).
 * Принять — в телефоне или кнопкой в чате (/duel accept). Ставки обоих — в залог при старте; одинаковый расклад (seed) у обоих,
 * счёт соперника виден по ходу. Победитель получает банк, ничья — возврат. Сдался/вышел — проиграл (после 5 с).
 * Сервер проверяет счёт (предел и минимальная длительность — как у Games), ставка не больше $1000, вызов живёт 60 с.
 */
public final class Duels {
    private Duels() {}

    static final int[] BETS = {0, 50, 100, 500, 1000};
    static final String[] GAMES = {"billiards", "bowling", "hoops", "golf"};

    static final class Ask {
        UUID from, to;
        String game;
        int bet;
        long at;
    }

    static final class Match {
        UUID a, b;
        String game;
        int bet;
        long start;
        Integer sa, sb;
        boolean qa, qb;
    }

    private static final Map<UUID, Ask> ASKS = new HashMap<>(); // кому → вызов
    private static final Map<UUID, Match> MATCH = new HashMap<>(); // оба игрока → матч
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Duels::tick);
        Achievements.ALL.putIfAbsent("duel_win", new String[]{"Один на один", "Обыграть живого игрока в мини-игру"});
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(
                net.minecraft.server.command.CommandManager.literal("duel")
                        .then(net.minecraft.server.command.CommandManager.literal("accept").executes(c -> {
                            accept(c.getSource().getPlayerOrThrow());
                            return 1;
                        }))
                        .then(net.minecraft.server.command.CommandManager.literal("decline").executes(c -> {
                            decline(c.getSource().getPlayerOrThrow());
                            return 1;
                        }))));
        // вживую: Shift+ПКМ по игроку пустой рукой
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity p) || !(entity instanceof ServerPlayerEntity t)
                    || !p.isSneaking() || !p.getMainHandStack().isEmpty()) return net.minecraft.util.ActionResult.PASS;
            ask(p, t.getName().getString(), nearestGame(p), 50);
            return net.minecraft.util.ActionResult.SUCCESS;
        });
    }

    /** Игра ближайшей площадки (до 40 блоков) или бильярд. */
    static String nearestGame(ServerPlayerEntity p) {
        String best = "billiards";
        double bd = 40 * 40;
        for (Object[] s : Games.SPOTS) {
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), (String) s[0]);
            if (at != null && p.squaredDistanceTo(at) < bd) {
                bd = p.squaredDistanceTo(at);
                best = (String) s[1];
            }
        }
        return best;
    }

    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound n) {
        switch (type) {
            case "duel_ask" -> ask(p, n.getString("to"), n.getString("game"), n.getInt("bet"));
            case "duel_accept" -> accept(p);
            case "duel_decline" -> decline(p);
            case "duel_live" -> live(p, n.getInt("score"));
            default -> {
                return false;
            }
        }
        return true;
    }

    public static boolean busy(UUID id) {
        return MATCH.containsKey(id);
    }

    static boolean money(ServerPlayerEntity p, int bet) {
        LifeData d = Life.get(p);
        return d.money + d.cash >= bet;
    }

    public static void ask(ServerPlayerEntity p, String toName, String game, int bet) {
        MinecraftServer s = p.getServer();
        ServerPlayerEntity t = s.getPlayerManager().getPlayer(toName);
        if (t == null || t == p || !java.util.Arrays.asList(GAMES).contains(game) || java.util.Arrays.stream(BETS).noneMatch(b -> b == bet)) return;
        long now = s.getTicks();
        if (now - COOLDOWN.getOrDefault(p.getUuid(), -1000L) < 200) {
            FunUtil.hint(p, "Подожди немного перед новым вызовом", Formatting.GRAY);
            return;
        }
        if (busy(p.getUuid()) || busy(t.getUuid()) || Games.playing(p) || Games.playing(t)) {
            FunUtil.hint(p, t.getName().getString() + " сейчас занят(а)", Formatting.GRAY);
            return;
        }
        if (!money(p, bet)) {
            FunUtil.hint(p, "Для ставки нужно $" + bet, Formatting.RED);
            return;
        }
        COOLDOWN.put(p.getUuid(), now);
        Ask a = new Ask();
        a.from = p.getUuid();
        a.to = t.getUuid();
        a.game = game;
        a.bet = bet;
        a.at = now;
        ASKS.put(t.getUuid(), a);
        String what = Games.title(game) + (bet > 0 ? " на $" + bet : " на интерес");
        FunUtil.hint(p, "Вызов отправлен: " + t.getName().getString() + " · " + what, Formatting.AQUA);
        t.sendMessage(Text.literal("⚔ " + p.getName().getString() + " вызывает: " + what + "  ").formatted(Formatting.AQUA)
                .append(Text.literal("[ПРИНЯТЬ]").setStyle(Style.EMPTY.withColor(Formatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Или телефон → «Спорт»")))))
                .append(Text.literal("  "))
                .append(Text.literal("[НЕТ]").setStyle(Style.EMPTY.withColor(Formatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duel decline")))), false);
        Life.sound(t, com.yompix.novograd.ModContent.PHONE_NOTIFY, 1f);
        Sports.send(t);
        Novograd.LOG.info("[дуэль] вызов {} → {}: {} ${}", p.getName().getString(), t.getName().getString(), game, bet);
    }

    static void decline(ServerPlayerEntity p) {
        Ask a = ASKS.remove(p.getUuid());
        if (a == null) return;
        ServerPlayerEntity f = p.getServer().getPlayerManager().getPlayer(a.from);
        if (f != null) FunUtil.hint(f, p.getName().getString() + " отказался(ась)", Formatting.GRAY);
    }

    public static void accept(ServerPlayerEntity p) {
        Ask a = ASKS.remove(p.getUuid());
        if (a == null) {
            FunUtil.hint(p, "Вызовов нет", Formatting.GRAY);
            return;
        }
        ServerPlayerEntity f = p.getServer().getPlayerManager().getPlayer(a.from);
        if (f == null || busy(f.getUuid()) || busy(p.getUuid()) || Games.playing(f) || Games.playing(p)) {
            FunUtil.hint(p, "Соперник уже не может", Formatting.GRAY);
            return;
        }
        if (!money(f, a.bet) || !money(p, a.bet)) {
            FunUtil.hint(p, "Не хватает денег на ставку", Formatting.RED);
            return;
        }
        // залог ставок
        if (a.bet > 0 && (!Life.payRaw(f, a.bet, "залог: " + Games.title(a.game).toLowerCase()) || !Life.payRaw(p, a.bet, "залог: " + Games.title(a.game).toLowerCase()))) return;
        Match m = new Match();
        m.a = f.getUuid();
        m.b = p.getUuid();
        m.game = a.game;
        m.bet = a.bet;
        m.start = p.getServerWorld().getTime();
        MATCH.put(m.a, m);
        MATCH.put(m.b, m);
        long seed = p.getRandom().nextLong();
        open(f, m, p, seed);
        open(p, m, f, seed);
        Novograd.LOG.info("[дуэль] старт {} vs {}: {} ${}", f.getName().getString(), p.getName().getString(), m.game, m.bet);
    }

    private static void open(ServerPlayerEntity p, Match m, ServerPlayerEntity foe, long seed) {
        NbtCompound n = new NbtCompound();
        n.putString("game", m.game);
        n.putInt("bet", m.bet);
        n.putInt("foe", 0);
        n.putString("foeName", foe.getName().getString());
        n.putBoolean("duel", true);
        n.putLong("seed", seed);
        LifeNet.send(p, "open_game", n);
    }

    private static ServerPlayerEntity other(MinecraftServer s, Match m, UUID me) {
        return s.getPlayerManager().getPlayer(me.equals(m.a) ? m.b : m.a);
    }

    /** Счёт по ходу партии — сопернику (только для показа; итог проверяется в done). */
    public static void live(ServerPlayerEntity p, int score) {
        Match m = MATCH.get(p.getUuid());
        if (m == null) return;
        ServerPlayerEntity o = other(p.getServer(), m, p.getUuid());
        if (o == null) return;
        NbtCompound n = new NbtCompound();
        n.putInt("score", Math.max(0, Math.min(Games.max(m.game), score)));
        LifeNet.send(o, "duel_live", n);
    }

    /** Итог от клиента (из Games.done). true — это была дуэль. */
    public static boolean done(ServerPlayerEntity p, NbtCompound n) {
        Match m = MATCH.get(p.getUuid());
        if (m == null || !m.game.equals(n.getString("game"))) return m != null;
        long secs = (p.getServerWorld().getTime() - m.start) / 20;
        boolean quit = n.getBoolean("quit") || secs < Games.minSec(m.game);
        int score = Math.max(0, Math.min(Games.max(m.game), n.getInt("score")));
        boolean isA = p.getUuid().equals(m.a);
        if (isA) {
            m.sa = score;
            m.qa = quit;
        } else {
            m.sb = score;
            m.qb = quit;
        }
        if (quit) {
            resolve(p.getServer(), m);
            return true;
        }
        if (m.sa == null || m.sb == null) {
            ServerPlayerEntity o = other(p.getServer(), m, p.getUuid());
            FunUtil.hint(p, "Ждём " + (o == null ? "соперника" : o.getName().getString()) + "…", Formatting.AQUA);
            return true;
        }
        resolve(p.getServer(), m);
        return true;
    }

    /** Подвести итог: сдавшийся проигрывает, иначе — по счёту (в гольфе меньше — лучше). */
    private static void resolve(MinecraftServer s, Match m) {
        resolve(s, m, null);
    }

    /** leaving — игрок, который сейчас выходит (его уже может не быть в списке сервера). */
    private static void resolve(MinecraftServer s, Match m, ServerPlayerEntity leaving) {
        MATCH.remove(m.a);
        MATCH.remove(m.b);
        ServerPlayerEntity pa = find(s, m.a, leaving), pb = find(s, m.b, leaving);
        int sa = m.sa == null ? 0 : m.sa, sb = m.sb == null ? 0 : m.sb;
        int win; // 1 — a, -1 — b, 0 — ничья
        if (m.qa && m.qb) win = 0;
        else if (m.qa || m.sa == null && m.sb != null) win = -1;
        else if (m.qb || m.sb == null) win = 1;
        else win = Games.lowerWins(m.game) ? Integer.compare(sb, sa) : Integer.compare(sa, sb);
        // банк: победителю обе ставки, ничья — каждому своя
        if (m.bet > 0) {
            if (win == 0) {
                give(pa, m.bet, "ничья: ставка назад");
                give(pb, m.bet, "ничья: ставка назад");
            } else give(win > 0 ? pa : pb, m.bet * 2, "выигрыш в дуэли");
        }
        String score = sa + " : " + sb;
        result(pa, pb, win, score, m, true);
        result(pb, pa, -win, sb + " : " + sa, m, false);
        Novograd.LOG.info("[дуэль] итог {}: {} ({}{})", Games.title(m.game), score, win > 0 ? "победил первый" : win < 0 ? "победил второй" : "ничья",
                m.qa || m.qb ? ", кто-то сдался" : "");
    }

    private static void result(ServerPlayerEntity p, ServerPlayerEntity o, int win, String score, Match m, boolean first) {
        if (p == null) return;
        String on = o == null ? "соперник" : o.getName().getString();
        if (win > 0) {
            FunUtil.title(p, "ПОБЕДА · " + Games.title(m.game).toUpperCase(), "против " + on + " · " + score + (m.bet > 0 ? " · +$" + m.bet : ""));
            Achievements.give(p, "duel_win");
            Life.get(p).mood = Math.min(100, Life.get(p).mood + 6);
            Social.post(p.getServer(), "@" + p.getName().getString(), "обыграл(а) " + on + " — " + Games.title(m.game).toLowerCase() + " " + score + " 🏆", p, 10 + p.getRandom().nextInt(30), 3);
        } else if (win < 0) FunUtil.title(p, "ПРОИГРЫШ", on + " сильнее · " + score);
        else FunUtil.title(p, "НИЧЬЯ", "с " + on + " · " + score);
        NbtCompound n = new NbtCompound();
        n.putInt("win", win);
        n.putString("score", score);
        LifeNet.send(p, "duel_result", n);
    }

    private static ServerPlayerEntity find(MinecraftServer s, UUID id, ServerPlayerEntity leaving) {
        if (leaving != null && leaving.getUuid().equals(id)) return leaving;
        return s.getPlayerManager().getPlayer(id);
    }

    private static void give(ServerPlayerEntity p, int amount, String why) {
        if (p == null) return; // не бывает: выход игрока подводит итог, пока он ещё здесь
        Life.get(p).money += amount;
        p.sendMessage(Text.literal("+$" + amount + "  " + why).formatted(Formatting.GREEN), true);
        Life.sync(p);
    }

    private static void tick(MinecraftServer s) {
        if (s.getTicks() % 20 != 9) return;
        long now = s.getTicks();
        for (Iterator<Map.Entry<UUID, Ask>> it = ASKS.entrySet().iterator(); it.hasNext(); ) {
            Ask a = it.next().getValue();
            if (now - a.at > 20 * 60) it.remove();
        }
        // матч не закончили за 5 минут — у кого нет счёта, тот сдался
        for (Match m : new java.util.HashSet<>(MATCH.values())) {
            if (s.getOverworld().getTime() - m.start < 20 * 300) continue;
            if (m.sa == null) m.qa = true;
            if (m.sb == null) m.qb = true;
            resolve(s, m);
        }
    }

    public static void onLeave(ServerPlayerEntity p) {
        ASKS.remove(p.getUuid());
        Match m = MATCH.get(p.getUuid());
        if (m == null) return;
        if (p.getUuid().equals(m.a)) {
            if (m.sa == null) m.qa = true;
        } else if (m.sb == null) m.qb = true;
        resolve(p.getServer(), m, p);
    }

    /** Для «Спорта» в телефоне: входящий вызов и кто в сети. */
    public static void fill(ServerPlayerEntity p, NbtCompound out) {
        Ask a = ASKS.get(p.getUuid());
        if (a != null) {
            ServerPlayerEntity f = p.getServer().getPlayerManager().getPlayer(a.from);
            NbtCompound c = new NbtCompound();
            c.putString("from", f == null ? "?" : f.getName().getString());
            c.putString("game", Games.title(a.game));
            c.putInt("bet", a.bet);
            out.put("ask", c);
        }
        NbtList l = new NbtList();
        for (ServerPlayerEntity o : p.getServer().getPlayerManager().getPlayerList())
            if (o != p) l.add(net.minecraft.nbt.NbtString.of(o.getName().getString()));
        out.put("players", l);
        out.putBoolean("inDuel", busy(p.getUuid()));
    }

    /** Для автотеста: матч без вызова (второй игрок может быть FakePlayer), ставки — в залог. */
    public static void testStart(ServerPlayerEntity a, ServerPlayerEntity b, String game, int bet) {
        Life.payRaw(a, bet, "залог");
        Life.payRaw(b, bet, "залог");
        Match m = new Match();
        m.a = a.getUuid();
        m.b = b.getUuid();
        m.game = game;
        m.bet = bet;
        m.start = a.getServerWorld().getTime();
        MATCH.put(m.a, m);
        MATCH.put(m.b, m);
        open(a, m, b, 42);
    }

    /** Для автотеста: число идущих матчей. */
    public static int matches() {
        return MATCH.size() / 2;
    }
}
