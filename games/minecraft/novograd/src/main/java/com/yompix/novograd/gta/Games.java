package com.yompix.novograd.gta;

import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
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
 * 0.9.9: игры в барах, клубах и на площадках — бильярд, боулинг, баскетбол (штрафные), гольф (паттинг).
 * Как дартс (gta/Arcade): подошёл к метке — экран игры (client/games/*), соперник — завсегдатай, его счёт решает
 * сервер заранее; ставка на кону. Клиент присылает свой счёт, сервер проверяет время партии и предел очков.
 * Если рядом ещё игрок — «вызов» (оба играют свою партию, побеждает лучший счёт; ставка с обоих).
 */
public final class Games {
    private Games() {}

    /** Место, игра, ставка. */
    static final Object[][] SPOTS = {
            {"club_neon+-4,3", "billiards", 100}, {"beach_bar+-3,3", "billiards", 50}, {"riv_strip+6,6", "billiards", 300},
            {"club_neon+0,-6", "bowling", 80}, {"riv_strip+-6,6", "bowling", 200},
            {"downtown_plaza+14,8", "hoops", 50}, {"suburb_street+10,-8", "hoops", 30},
            {"hill_top+6,4", "golf", 100}, {"riv_villa+10,10", "golf", 400}};
    static final String[] TITLES = {"Бильярд", "Боулинг", "Баскетбол", "Гольф"};

    public static String title(String game) {
        return switch (game) {
            case "billiards" -> "Бильярд";
            case "bowling" -> "Боулинг";
            case "hoops" -> "Баскетбол";
            default -> "Гольф";
        };
    }

    /** Предел очков (защита от подделки) и сколько секунд минимум длится партия. */
    static int max(String g) {
        return switch (g) {
            case "billiards" -> 15;   // забитых шаров за 10 ударов
            case "bowling" -> 150;    // 5 фреймов
            case "hoops" -> 30;       // 10 бросков: 3 очка за чистый
            default -> 30;            // гольф: удары на 3 лунки (меньше — лучше)
        };
    }

    static int minSec(String g) {
        return switch (g) {
            case "billiards" -> 20;
            case "bowling" -> 25;
            case "hoops" -> 15;
            default -> 20;
        };
    }

    /** Счёт соперника: средний завсегдатай (гольф — меньше лучше). */
    static int foe(String g, java.util.Random r) {
        return switch (g) {
            case "billiards" -> 3 + r.nextInt(5);
            case "bowling" -> 55 + r.nextInt(55);
            case "hoops" -> 9 + r.nextInt(12);
            default -> 7 + r.nextInt(6);
        };
    }

    static boolean lowerWins(String g) {
        return g.equals("golf");
    }

    private static final Map<UUID, Integer> AT = new HashMap<>();
    private static final Map<UUID, Object[]> PLAYING = new HashMap<>(); // {игра, начало, ставка, счёт соперника, имя}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Games::tick);
        for (String g : new String[]{"billiards", "bowling", "hoops", "golf"})
            Achievements.ALL.putIfAbsent("win_" + g, new String[]{"Победа: " + title(g), "Обыграть завсегдатая — " + title(g).toLowerCase()});
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 10 != 7) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.hasVehicle()) continue;
            ServerWorld w = p.getServerWorld();
            int inside = -1;
            for (int i = 0; i < SPOTS.length; i++) {
                Vec3d at = Story.place(w, (String) SPOTS[i][0]);
                if (at == null || p.squaredDistanceTo(at) > 40 * 40) continue;
                String g = (String) SPOTS[i][1];
                ItemStack icon = new ItemStack(switch (g) {
                    case "billiards" -> Items.ENDER_PEARL;
                    case "bowling" -> Items.SNOWBALL;
                    case "hoops" -> Items.SLIME_BALL;
                    default -> Items.IRON_SHOVEL;
                });
                ItemEntity it = FunUtil.marker(w, at, "game_" + g + "_" + i, icon, Text.literal(title(g) + " — партия на $" + SPOTS[i][2]).formatted(Formatting.AQUA), false);
                if (p.squaredDistanceTo(it) < 2 * 2) inside = i;
            }
            Integer was = AT.put(p.getUuid(), inside);
            if (inside < 0 || was != null && was == inside || PLAYING.containsKey(p.getUuid())) continue;
            open(p, (String) SPOTS[inside][1], (int) SPOTS[inside][2]);
        }
    }

    public static void open(ServerPlayerEntity p, String game, int bet) {
        LifeData d = Life.get(p);
        if (d.money + d.cash < bet) {
            FunUtil.hint(p, "Завсегдатай: «Без $" + bet + " не играю»", Formatting.GRAY);
            return;
        }
        int foe = foe(game, new java.util.Random(p.getRandom().nextLong()));
        String name = FunUtil.pick(p.getServerWorld(), "Кий-Саныч", "Лёха «Страйк»", "Марина Борт", "Тренер Палыч", "Дед Гольфист", "Зина «Трёшка»");
        PLAYING.put(p.getUuid(), new Object[]{game, p.getServerWorld().getTime(), bet, foe, name});
        NbtCompound n = new NbtCompound();
        n.putString("game", game);
        n.putInt("bet", bet);
        n.putInt("foe", foe);
        n.putString("foeName", name);
        n.putLong("seed", p.getRandom().nextLong());
        LifeNet.send(p, "open_game", n);
    }

    /** Итог от клиента: время, предел, ставка. */
    public static void done(ServerPlayerEntity p, NbtCompound n) {
        Object[] g = PLAYING.remove(p.getUuid());
        if (g == null) return;
        String game = (String) g[0];
        if (!game.equals(n.getString("game"))) return;
        long secs = (p.getServerWorld().getTime() - (long) g[1]) / 20;
        int bet = (int) g[2], foe = (int) g[3];
        if (n.getBoolean("quit")) {
            if (secs >= 5) Life.pay(p, bet, "сдался: " + title(game).toLowerCase());
            return;
        }
        if (secs < minSec(game)) return; // слишком быстро — не засчитываем
        int mine = Math.max(0, Math.min(max(game), n.getInt("score")));
        boolean win = lowerWins(game) ? mine < foe : mine > foe, draw = mine == foe;
        if (draw) FunUtil.title(p, "НИЧЬЯ", title(game) + " · " + mine + " : " + foe);
        else if (win) {
            Life.earn(p, bet, "выигрыш: " + title(game).toLowerCase());
            Achievements.give(p, "win_" + game);
            FunUtil.title(p, "ПОБЕДА · " + title(game).toUpperCase(), mine + " : " + foe + " · +$" + bet);
            Life.get(p).mood = Math.min(100, Life.get(p).mood + 5);
        } else {
            Life.pay(p, bet, "проигрыш: " + title(game).toLowerCase());
            FunUtil.title(p, "ПРОИГРЫШ", title(game) + " · " + mine + " : " + foe + " · −$" + bet);
        }
    }

    public static void onLeave(ServerPlayerEntity p) {
        AT.remove(p.getUuid());
        PLAYING.remove(p.getUuid());
    }

    /** Для «Ночь»/«Спорт» в телефоне: места игр. */
    public static void fill(NbtCompound out, ServerWorld w) {
        net.minecraft.nbt.NbtList l = new net.minecraft.nbt.NbtList();
        for (Object[] s : SPOTS) {
            Vec3d at = Story.place(w, (String) s[0]);
            if (at == null) continue;
            NbtCompound c = new NbtCompound();
            c.putString("game", (String) s[1]);
            c.putString("title", title((String) s[1]));
            c.putInt("bet", (int) s[2]);
            c.putDouble("x", at.x);
            c.putDouble("z", at.z);
            l.add(c);
        }
        out.put("games", l);
    }
}
