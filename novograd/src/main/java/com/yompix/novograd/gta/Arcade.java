package com.yompix.novograd.gta;

import com.yompix.novograd.life.Achievements;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.story.Story;
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
 * Игровые автоматы и дартс в барах (экраны client/screen/ArcadeScreen и DartsScreen):
 *  • автомат «Неон-Погоня» — $5 за игру, побей рекорд — заплатят по очкам (до $250);
 *  • дартс — партия с завсегдатаем бара на ставку (на Ривьере ставки выше). Счёт соперника выбирает сервер.
 */
public final class Arcade {
    private Arcade() {}

    /** Место, что стоит, ставка дартса. */
    private static final Object[][] SPOTS = {
            {"bar_door+3,-3", "arcade", 0}, {"bar_door+-3,-3", "darts", 50},
            {"club_neon+4,3", "arcade", 0}, {"beach_bar+3,3", "darts", 50},
            {"riv_strip+6,-6", "arcade", 0}, {"riv_strip+-6,-6", "darts", 250},
            {"club_door+4,3", "arcade", 0},
    };

    private static final Map<UUID, Integer> AT = new HashMap<>();
    private static final Map<UUID, long[]> PLAYING = new HashMap<>(); // {начало, ставка, счёт соперника}

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 10 != 9) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.hasVehicle()) continue;
            ServerWorld w = p.getServerWorld();
            int inside = -1;
            for (int i = 0; i < SPOTS.length; i++) {
                Vec3d at = Story.place(w, (String) SPOTS[i][0]);
                if (at == null || p.squaredDistanceTo(at) > 40 * 40) continue;
                boolean darts = SPOTS[i][1].equals("darts");
                int bet = (int) SPOTS[i][2];
                ItemEntity it = FunUtil.marker(w, at, "fun_" + SPOTS[i][1] + "_" + i, new ItemStack(darts ? Items.TARGET : Items.REDSTONE_LAMP),
                        Text.literal(darts ? "Дартс — партия на $" + bet : "Автомат «Неон-Погоня» — $5").formatted(darts ? Formatting.GOLD : Formatting.LIGHT_PURPLE), false);
                if (p.squaredDistanceTo(it) < 2 * 2) inside = i;
            }
            Integer was = AT.put(p.getUuid(), inside);
            if (inside < 0 || was != null && was == inside) continue;
            open(p, (String) SPOTS[inside][1], (int) SPOTS[inside][2]);
        }
    }

    public static void open(ServerPlayerEntity p, String game, int bet) {
        LifeData d = Life.get(p);
        NbtCompound n = new NbtCompound();
        n.putString("game", game);
        if (game.equals("arcade")) {
            if (!Life.pay(p, 5, "игровой автомат")) return;
            n.putInt("record", FunUtil.getInt(d, "arcade_best"));
        } else {
            if (d.money < bet) {
                FunUtil.hint(p, "Завсегдатай: «На мелочь не играю. Приходи с $" + bet + "»", Formatting.GRAY);
                return;
            }
            // сила соперника: от 110 до 220 очков за 3 раунда по 3 дротика
            int foe = 110 + p.getRandom().nextInt(111);
            n.putInt("foe", foe);
            n.putInt("bet", bet);
            n.putString("foeName", FunUtil.pick(p.getServerWorld(), "Боря-Глаз", "Дядя Миша", "Светка «Стрела»", "Моряк Петрович", "Жорик"));
            PLAYING.put(p.getUuid(), new long[]{p.getServerWorld().getTime(), bet, foe});
            return2(p, n);
            return;
        }
        PLAYING.put(p.getUuid(), new long[]{p.getServerWorld().getTime(), 0, 0});
        return2(p, n);
    }

    private static void return2(ServerPlayerEntity p, NbtCompound n) {
        LifeNet.send(p, "open_arcade", n);
    }

    /** Итог игры от клиента: сервер проверяет время и ставку. */
    public static void done(ServerPlayerEntity p, NbtCompound n) {
        long[] g = PLAYING.remove(p.getUuid());
        if (g == null) return;
        LifeData d = Life.get(p);
        long secs = (p.getServerWorld().getTime() - g[0]) / 20;
        if (n.getString("game").equals("arcade")) {
            int score = Math.min(n.getInt("score"), (int) (secs * 120 + 300)); // не больше, чем можно набрать за это время
            int best = FunUtil.getInt(d, "arcade_best");
            if (score >= 500) Achievements.give(p, "arcade");
            if (score > best) {
                FunUtil.setInt(d, "arcade_best", score);
                int prize = Math.min(250, score / 10);
                if (prize > 0) Life.earn(p, prize, "рекорд автомата: " + score);
                FunUtil.title(p, "НОВЫЙ РЕКОРД!", score + " очков" + (prize > 0 ? " · автомат выплюнул $" + prize : ""));
            } else FunUtil.hint(p, score + " очков (рекорд " + best + ")", Formatting.LIGHT_PURPLE);
            Life.sync(p);
            return;
        }
        int mine = Math.min(n.getInt("score"), 540);
        int bet = (int) g[1], foe = (int) g[2];
        if (secs < 6 || n.getBoolean("quit")) {
            if (n.getBoolean("quit") && secs >= 3) Life.pay(p, bet, "сдался в дартс");
            return;
        }
        if (mine > foe) {
            Life.earn(p, bet, "выигрыш в дартс");
            Achievements.give(p, "darts");
            FunUtil.title(p, "ПОБЕДА В ДАРТС", mine + " : " + foe + " · +$" + bet);
        } else if (mine < foe) {
            Life.pay(p, bet, "проигрыш в дартс");
            FunUtil.title(p, "ПРОИГРЫШ", mine + " : " + foe + " · −$" + bet);
        } else FunUtil.title(p, "НИЧЬЯ", mine + " : " + foe);
    }

    public static void onLeave(ServerPlayerEntity p) {
        AT.remove(p.getUuid());
        PLAYING.remove(p.getUuid());
    }
}
