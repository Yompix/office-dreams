package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.story.Story;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * «Полицейские и воры» по сети (LAN, e4mc, свой сервер), 2–8 игроков. Телефон → «Копы и воры» или /cnr.
 * Игроки делятся случайно: половина (минимум один) — полиция: патрульная машина, пистолет, раз в 20 с радар
 * показывает воров. Воры: быстрая машина и бита; цель — довезти добычу до точки сбора (жёлтая метка) или продержаться
 * до конца раунда. Арест — коп рядом со стоящим вором 3 секунды, или вор погиб. 3 раунда, очки и награды.
 * На время игры полиция Новограда участников не трогает (звёзды не начисляются).
 */
public final class CopsAndRobbers {
    private CopsAndRobbers() {}

    public static final int ROUND_SEC = 300, BREAK_SEC = 15, ROUNDS = 3;
    private static final String[] STARTS = {"downtown_plaza", "industry_street", "suburb_street", "club_neon", "dt_alley"};
    private static final String[][] EXITS = {{"port_dock", "Порт"}, {"airfield", "Аэродром"}, {"beach_bar", "Пляжный бар"}, {"farm", "Ферма"},
            {"hw_south", "Южная трасса"}, {"riv_docks", "Причал Ривьеры"}, {"chop_shop", "Разборка"}};

    private static boolean running;
    private static int round;
    private static long roundEnd, breakUntil, nextPing;
    private static final Set<UUID> COPS = new LinkedHashSet<>(), ROBBERS = new LinkedHashSet<>(), CAUGHT = new HashSet<>(), ESCAPED = new HashSet<>();
    private static final Map<UUID, Integer> SCORE = new HashMap<>(), ARREST = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();
    private static final List<UUID> CARS = new ArrayList<>();
    @Nullable private static Vec3d exit;
    private static String exitName = "";
    /** Для автотеста на одной машине: -Dnovograd.cnrsolo=true — можно начать одному (будешь вором, за тобой — патруль). */
    private static final boolean SOLO = Boolean.getBoolean("novograd.cnrsolo");

    public static boolean running() {
        return running;
    }

    public static boolean immune(ServerPlayerEntity p) {
        return running && (COPS.contains(p.getUuid()) || ROBBERS.contains(p.getUuid()));
    }

    // ------------------------------------------------------------------ меню
    public static void menu(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        int n = s.getPlayerManager().getCurrentPlayerCount();
        CopsNet.Menu m = new CopsNet.Menu("cnr", "🚓 Полицейские и воры", 0xFF4FA3FF);
        if (running) {
            m.line("Идёт игра: раунд " + round + " из " + ROUNDS + ". Полиция: " + names(COPS) + ".")
                    .line("Воры: " + names(ROBBERS) + ".")
                    .line("Счёт: " + scoreLine());
            m.button("stop", "Остановить игру", 0xFFE53935, true);
        } else {
            m.line("Онлайн-режим для 2–8 игроков. Команды делятся случайно.")
                    .line("Полиция: патрульная машина, пистолет, радар раз в 20 с показывает воров. Арест — постой рядом с вором 3 с.")
                    .line("Воры: быстрая машина, бита. Довези добычу до точки сбора или продержись " + ROUND_SEC / 60 + " минут.")
                    .line(ROUNDS + " раунда, награды: победа $300, арест $150, побег $250. Сейчас в игре: " + n + ".");
            m.button("start", n >= 2 || SOLO ? "Начать игру" : "Нужно минимум 2 игрока", 0xFF2FA84F, n >= 2 || SOLO);
        }
        m.button("close", "Закрыть", 0xFF8E8E93, true);
        m.open(p);
    }

    public static void click(ServerPlayerEntity p, String id) {
        switch (id) {
            case "start" -> start(p.getServer(), p);
            case "stop" -> stop(p.getServer(), "игру остановил " + p.getName().getString());
            default -> {}
        }
    }

    private static String names(Set<UUID> ids) {
        List<String> l = new ArrayList<>();
        for (UUID u : ids) l.add(NAMES.getOrDefault(u, "?"));
        return l.isEmpty() ? "—" : String.join(", ", l);
    }

    private static String scoreLine() {
        List<Map.Entry<UUID, Integer>> l = new ArrayList<>(SCORE.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        for (var e : l) sb.append(sb.isEmpty() ? "" : ", ").append(NAMES.getOrDefault(e.getKey(), "?")).append(" ").append(e.getValue());
        return sb.isEmpty() ? "—" : sb.toString();
    }

    // ------------------------------------------------------------------ запуск и раунды
    public static void start(MinecraftServer server, @Nullable ServerPlayerEntity who) {
        if (running) return;
        List<ServerPlayerEntity> pl = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) if (!p.isSpectator() && !Prison.inside(p)) pl.add(p);
        if (pl.size() > 8) pl = pl.subList(0, 8);
        if (pl.size() < 2 && !SOLO) {
            if (who != null) who.sendMessage(Text.literal("Для «Полицейских и воров» нужно минимум 2 игрока (пауза → «Открыть для друзей»)").formatted(Formatting.RED), false);
            return;
        }
        running = true;
        round = 0;
        SCORE.clear();
        NAMES.clear();
        for (ServerPlayerEntity p : pl) {
            SCORE.put(p.getUuid(), 0);
            NAMES.put(p.getUuid(), p.getName().getString());
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList())
            p.sendMessage(Text.literal("🚓 «Полицейские и воры»: " + pl.size() + " игрока(ов), " + ROUNDS + " раунда. Поехали!").formatted(Formatting.AQUA), false);
        startRound(server);
    }

    private static void startRound(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        round++;
        COPS.clear();
        ROBBERS.clear();
        CAUGHT.clear();
        ESCAPED.clear();
        ARREST.clear();
        List<ServerPlayerEntity> pl = new ArrayList<>();
        for (UUID u : SCORE.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p != null) pl.add(p);
        }
        Collections.shuffle(pl, new Random(w.getTime()));
        int cops = SOLO && pl.size() == 1 ? 0 : Math.max(1, pl.size() / 2);
        for (int i = 0; i < pl.size(); i++) (i < cops ? COPS : ROBBERS).add(pl.get(i).getUuid());
        // точки: старт воров в городе, точка сбора — подальше
        var r = w.getRandom();
        String start = STARTS[r.nextInt(STARTS.length)];
        Vec3d rs = Story.place(w, start);
        if (rs == null) rs = pl.get(0).getPos();
        String[] best = EXITS[0];
        double bd = -1;
        for (int k = 0; k < 6; k++) {
            String[] e = EXITS[r.nextInt(EXITS.length)];
            Vec3d v = Story.place(w, e[0]);
            if (v == null) continue;
            double d = v.distanceTo(rs);
            if (d > 350 && d < 1300 && (bd < 0 || r.nextBoolean())) {
                best = e;
                bd = d;
            }
        }
        exit = Story.place(w, best[0]);
        if (exit == null) exit = rs.add(400, 0, 0);
        exitName = best[1];
        Vec3d cs = Story.place(w, "police_station");
        if (cs == null) cs = rs.add(-120, 0, 60);
        int i = 0;
        for (ServerPlayerEntity p : pl) {
            boolean cop = COPS.contains(p.getUuid());
            Vec3d base = cop ? cs : rs;
            Vec3d at = base.add((i % 4) * 4 - 6, 0, (i / 4) * 5);
            i++;
            p.stopRiding();
            Wanted.clear(p);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.getChunk(((int) Math.floor(at.x) >> 4) + dx, ((int) Math.floor(at.z) >> 4) + dz); // иначе высота неизвестна — упадёт в пустоту
            p.teleport(w, at.x, Story.topY(w, (int) Math.floor(at.x), (int) Math.floor(at.z)), at.z, p.getYaw(), 0);
            p.setHealth(p.getMaxHealth());
            CarEntity car = FunUtil.car(w, new Vec3d(at.x + 2.5, Story.topY(w, (int) Math.floor(at.x + 2.5), (int) Math.floor(at.z)), at.z), 0,
                    cop ? CarEntity.POLICE : (r.nextBoolean() ? CarEntity.SUPERCAR : 9));
            car.setOwner(p.getUuid());
            CARS.add(car.getUuid());
            if (cop) {
                give(p, new ItemStack(ModContent.PISTOL));
                give(p, new ItemStack(ModContent.AMMO, 48));
                Activities.title(p, "ТЫ — ПОЛИЦЕЙСКИЙ", "Раунд " + round + "/" + ROUNDS + " · поймай воров: стой рядом с вором 3 секунды");
                FunUtil.target(p, null, "");
            } else {
                give(p, new ItemStack(ModContent.BAT));
                Activities.title(p, "ТЫ — ВОР", "Раунд " + round + "/" + ROUNDS + " · довези добычу: " + exitName + " — или продержись " + ROUND_SEC / 60 + " мин");
                FunUtil.target(p, exit, "Точка сбора · " + exitName);
            }
            FunUtil.timer(p, ROUND_SEC);
            p.getServerWorld().playSound(null, p.getBlockPos(), ModContent.SIREN, SoundCategory.PLAYERS, 1f, 1f);
        }
        if (SOLO && COPS.isEmpty() && !pl.isEmpty()) {
            // одиночный тест: патрульная машина-бот гонится за вором
            ServerPlayerEntity p = pl.get(0);
            CarEntity bot = FunUtil.car(w, p.getPos().add(-18, 0, 0), 0, CarEntity.POLICE);
            for (int k = 0; k < 2; k++) {
                var cop = Pursuit.cop(w, bot.getX(), bot.getY(), bot.getZ(), "Полицейский", false);
                cop.startRiding(bot, true);
            }
            bot.chase(p);
            CARS.add(bot.getUuid());
        }
        long now = w.getTime();
        roundEnd = now + ROUND_SEC * 20L;
        breakUntil = 0;
        nextPing = now + 20 * 20;
        hud(server);
    }

    private static void give(ServerPlayerEntity p, ItemStack s) {
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    public static void stop(MinecraftServer server, String why) {
        if (!running) return;
        running = false;
        cleanupCars(server.getOverworld());
        NbtCompound off = new NbtCompound();
        off.putBoolean("on", false);
        for (UUID u : SCORE.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p == null) continue;
            CopsNet.send(p, "cnr", off);
            FunUtil.clearHud(p);
            p.sendMessage(Text.literal("🚓 «Полицейские и воры» окончены: " + why + ". Счёт: " + scoreLine()).formatted(Formatting.AQUA), false);
        }
        COPS.clear();
        ROBBERS.clear();
    }

    public static void onLeave(ServerPlayerEntity p) {
        if (!running) return;
        COPS.remove(p.getUuid());
        ROBBERS.remove(p.getUuid());
    }

    private static void cleanupCars(ServerWorld w) {
        for (UUID id : CARS) {
            Entity e = w.getEntity(id);
            if (e != null && !(e.getFirstPassenger() instanceof ServerPlayerEntity)) {
                for (Entity c : e.getPassengerList()) c.discard();
                e.discard();
            }
        }
        CARS.clear();
    }

    // ------------------------------------------------------------------ тик (раз в секунду)
    public static void tick(MinecraftServer server) {
        if (!running) return;
        ServerWorld w = server.getOverworld();
        long now = w.getTime();
        int online = 0;
        for (UUID u : SCORE.keySet()) if (server.getPlayerManager().getPlayer(u) != null) online++;
        if (online < 2 && !SOLO) {
            stop(server, "игроков меньше двух");
            return;
        }
        if (breakUntil > 0) {
            if (now >= breakUntil) {
                if (round >= ROUNDS) finish(server);
                else startRound(server);
            } else hud(server);
            return;
        }
        // аресты: коп вплотную к стоящему вору 3 секунды; погибший вор — тоже пойман
        for (UUID ru : ROBBERS) {
            if (CAUGHT.contains(ru) || ESCAPED.contains(ru)) continue;
            ServerPlayerEntity rob = server.getPlayerManager().getPlayer(ru);
            if (rob == null) continue;
            if (rob.isDead()) {
                caught(server, rob, null);
                continue;
            }
            Entity rv = rob.getVehicle() != null ? rob.getVehicle() : rob;
            boolean still = rv.getVelocity().horizontalLengthSquared() < 0.02 && (!(rv instanceof CarEntity c) || c.slow());
            ServerPlayerEntity by = null;
            for (UUID cu : COPS) {
                ServerPlayerEntity cop = server.getPlayerManager().getPlayer(cu);
                if (cop != null && !cop.isDead() && cop.squaredDistanceTo(rob) < (rob.hasVehicle() ? 4.5 * 4.5 : 3.2 * 3.2)) by = cop;
            }
            int a = by != null && still ? ARREST.getOrDefault(ru, 0) + 1 : 0;
            ARREST.put(ru, a);
            if (a == 1) {
                rob.sendMessage(Text.literal("🚔 Полиция рядом — двигайся, иначе арест!").formatted(Formatting.RED), true);
                by.sendMessage(Text.literal("Задерживаешь " + rob.getName().getString() + "…").formatted(Formatting.AQUA), true);
            }
            if (a >= 3) caught(server, rob, by);
            else if (exit != null && rob.getPos().squaredDistanceTo(exit.x, rob.getY(), exit.z) < 9 * 9) {
                ESCAPED.add(ru);
                SCORE.merge(ru, 2, Integer::sum);
                Life.earn(rob, 250, "добыча довезена");
                Activities.title(rob, "ДОБЫЧА ДОВЕЗЕНА", "Ты ушёл от полиции! +2 очка");
                FunUtil.clearHud(rob);
                broadcast(server, "💰 " + rob.getName().getString() + " довёз добычу до точки «" + exitName + "»!");
                rob.stopRiding();
            }
        }
        // радар копам: где воры (раз в 20 с, на 6 с)
        if (now >= nextPing) {
            nextPing = now + 20 * 20;
            NbtCompound n = new NbtCompound();
            NbtList l = new NbtList();
            for (UUID ru : ROBBERS) {
                ServerPlayerEntity rob = server.getPlayerManager().getPlayer(ru);
                if (rob == null || CAUGHT.contains(ru) || ESCAPED.contains(ru)) continue;
                NbtCompound q = new NbtCompound();
                q.putDouble("x", rob.getX());
                q.putDouble("z", rob.getZ());
                q.putString("n", rob.getName().getString());
                l.add(q);
            }
            n.put("list", l);
            for (UUID cu : COPS) {
                ServerPlayerEntity cop = server.getPlayerManager().getPlayer(cu);
                if (cop != null) {
                    CopsNet.send(cop, "cnr_ping", n);
                    cop.sendMessage(Text.literal("📡 Радар: воры замечены — смотри на радар").formatted(Formatting.AQUA), true);
                }
            }
        }
        int left = 0;
        for (UUID ru : ROBBERS) if (!CAUGHT.contains(ru) && !ESCAPED.contains(ru) && server.getPlayerManager().getPlayer(ru) != null) left++;
        boolean anyEscaped = !ESCAPED.isEmpty();
        if (left == 0) endRound(server, anyEscaped ? "ВОРЫ" : "ПОЛИЦИЯ");
        else if (now >= roundEnd) endRound(server, "ВОРЫ");
        else hud(server);
    }

    private static void caught(MinecraftServer server, ServerPlayerEntity rob, @Nullable ServerPlayerEntity by) {
        CAUGHT.add(rob.getUuid());
        if (by != null) {
            SCORE.merge(by.getUuid(), 1, Integer::sum);
            Life.earn(by, 150, "арест");
        }
        rob.stopRiding();
        FunUtil.clearHud(rob);
        Activities.title(rob, "ПОЙМАН", by != null ? "Тебя арестовал " + by.getName().getString() : "Вор выбыл из раунда");
        broadcast(server, "🚔 " + rob.getName().getString() + (by != null ? " арестован (" + by.getName().getString() + ")" : " выбыл"));
    }

    private static void endRound(MinecraftServer server, String winner) {
        boolean robbers = winner.equals("ВОРЫ");
        for (UUID u : SCORE.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p == null) continue;
            boolean cop = COPS.contains(u);
            boolean won = cop != robbers;
            if (!cop && robbers && !CAUGHT.contains(u) && !ESCAPED.contains(u)) SCORE.merge(u, 1, Integer::sum); // продержался
            if (won) {
                SCORE.merge(u, 1, Integer::sum);
                Life.earn(p, 300, "победа в раунде");
            }
            FunUtil.clearHud(p);
            Activities.title(p, "РАУНД " + round + ": ПОБЕДИЛИ " + winner, (round < ROUNDS ? "Следующий раунд через " + BREAK_SEC + " с · " : "") + "Счёт: " + scoreLine());
        }
        cleanupCars(server.getOverworld());
        breakUntil = server.getOverworld().getTime() + BREAK_SEC * 20L;
        hud(server);
    }

    private static void finish(MinecraftServer server) {
        UUID best = null;
        for (var e : SCORE.entrySet()) if (best == null || e.getValue() > SCORE.get(best)) best = e.getKey();
        for (UUID u : SCORE.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p == null) continue;
            Activities.title(p, "ИГРА ОКОНЧЕНА", "Лучший: " + NAMES.getOrDefault(best, "?") + " · " + scoreLine());
            if (u.equals(best)) Life.earn(p, 500, "лучший игрок «Полицейских и воров»");
        }
        stop(server, "все " + ROUNDS + " раунда сыграны");
    }

    private static void broadcast(MinecraftServer server, String s) {
        for (UUID u : SCORE.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p != null) p.sendMessage(Text.literal(s).formatted(Formatting.YELLOW), false);
        }
    }

    /** Табло справа сверху: роль, раунд, время, кто остался, счёт. */
    private static void hud(MinecraftServer server) {
        long now = server.getOverworld().getTime();
        int robLeft = 0;
        for (UUID ru : ROBBERS) if (!CAUGHT.contains(ru) && !ESCAPED.contains(ru)) robLeft++;
        NbtList score = new NbtList();
        List<Map.Entry<UUID, Integer>> l = new ArrayList<>(SCORE.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        for (var e : l) {
            NbtCompound q = new NbtCompound();
            q.putString("n", NAMES.getOrDefault(e.getKey(), "?"));
            q.putInt("s", e.getValue());
            q.putString("r", COPS.contains(e.getKey()) ? "cop" : CAUGHT.contains(e.getKey()) ? "caught" : ESCAPED.contains(e.getKey()) ? "escaped" : "rob");
            score.add(q);
        }
        for (UUID u : SCORE.keySet()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(u);
            if (p == null) continue;
            NbtCompound n = new NbtCompound();
            n.putBoolean("on", true);
            n.putString("role", COPS.contains(u) ? "cop" : "rob");
            n.putInt("round", round);
            n.putInt("rounds", ROUNDS);
            n.putInt("left", (int) Math.max(0, ((breakUntil > 0 ? breakUntil : roundEnd) - now) / 20));
            n.putBoolean("break", breakUntil > 0);
            n.putInt("cops", COPS.size());
            n.putInt("robLeft", robLeft);
            n.putInt("robAll", ROBBERS.size());
            n.putString("exit", exitName);
            n.put("score", score);
            CopsNet.send(p, "cnr", n);
            // метка точки сбора у вора (другие системы могли перебить голубую метку)
            if (breakUntil == 0 && ROBBERS.contains(u) && !CAUGHT.contains(u) && !ESCAPED.contains(u) && exit != null && now % 200 < 20)
                FunUtil.target(p, exit, "Точка сбора · " + exitName);
        }
    }
}
