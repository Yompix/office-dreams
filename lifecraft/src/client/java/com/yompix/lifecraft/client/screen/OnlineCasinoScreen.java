package com.yompix.lifecraft.client.screen;

import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.client.LifeCraftClient;
import com.yompix.lifecraft.life.Casino;
import com.yompix.lifecraft.life.OnlineCasino;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Онлайн-казино «Фортуна.онлайн» в телефоне: блэкджек с раздачей карт, «Краш» с растущим графиком, монетка.
 * Отсюда же — автоматы и рулетка.
 */
public class OnlineCasinoScreen extends LifeScreen {
    private String tab = "bj";
    private int betIndex = 1;

    // блэкджек
    private int[] player = new int[0], dealer = new int[0];
    private boolean bjDone = true;
    private int ps, ds, bjBet;
    private long dealAt;

    // краш
    private long crashStart = -1, crashEnd = -1;
    private double crashedAt, cashedAt;
    private int crashWin;
    private final List<Double> history = new ArrayList<>();

    // монетка
    private long coinAt = -1;
    private boolean coinHeads, coinPick;
    private int coinWin = -1;

    public OnlineCasinoScreen() {
        super("Онлайн-казино");
    }

    public void bj(NbtCompound n) {
        player = n.getIntArray("player");
        dealer = n.getIntArray("dealer");
        bjDone = n.getBoolean("done");
        ps = n.getInt("ps");
        ds = n.getInt("ds");
        bjBet = n.getInt("bet");
        dealAt = System.currentTimeMillis();
    }

    public void crash(String type, NbtCompound n) {
        switch (type) {
            case "crash_start" -> {
                crashStart = mc().world.getTime();
                crashEnd = -1;
                cashedAt = 0;
                crashWin = 0;
            }
            case "crash_cashout" -> {
                cashedAt = n.getDouble("at");
                crashWin = n.getInt("win");
            }
            case "crash_end" -> {
                crashEnd = mc().world.getTime();
                crashedAt = n.getDouble("point");
                history.add(0, crashedAt);
                if (history.size() > 8) history.remove(history.size() - 1);
                if (cashedAt == 0 && mc().player != null) mc().player.playSound(ModContent.ANSWER_WRONG, 0.7f, 0.8f);
            }
            default -> {}
        }
    }

    public void coin(NbtCompound n) {
        coinHeads = n.getBoolean("heads");
        coinWin = n.getInt("win");
        coinAt = System.currentTimeMillis();
    }

    private int bet() {
        return Casino.BETS[betIndex];
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int w = Math.min(330, width - 16), h = Math.min(220, height - 16);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 30);
        panel(ctx, x, y, w, h, 0xFF2A0F3D, 0xFF12061C);
        ctx.fill(x, y, x + w, y + 20, 0xFF6A1B9A);
        ctx.drawText(textRenderer, "ФОРТУНА.онлайн  18+", x + 8, y + 6, 0xFFFFD54F, true);
        String bal = "$" + LifeCraftClient.data.money;
        ctx.drawText(textRenderer, bal, x + w - 8 - textRenderer.getWidth(bal), y + 6, 0xFF7CFF8A, true);
        String[][] tabs = {{"bj", "Блэкджек"}, {"crash", "Краш"}, {"coin", "Монетка"}, {"slots", "Автоматы"}, {"roulette", "Рулетка"}};
        int tx = x + 6;
        for (String[] t : tabs) {
            int tw = textRenderer.getWidth(t[1]) + 10;
            boolean on = t[0].equals(tab);
            roundRect(ctx, tx, y + 24, tw, 14, on ? 0xFFFFB300 : 0xFF3D1A55);
            ctx.drawText(textRenderer, t[1], tx + 5, y + 27, on ? 0xFF1A0A24 : 0xFFE0D0F0, false);
            area(tx, y + 24, tw, 14, () -> {
                switch (t[0]) {
                    case "slots" -> mc().setScreen(new SlotsScreen());
                    case "roulette" -> mc().setScreen(new RouletteScreen());
                    default -> tab = t[0];
                }
            });
            tx += tw + 4;
        }
        int cy = y + 44;
        switch (tab) {
            case "bj" -> blackjack(ctx, x, cy, w, h - 66);
            case "crash" -> crashGame(ctx, x, cy, w, h - 66);
            default -> coinGame(ctx, x, cy, w, h - 66);
        }
        // ставка
        int by = y + h - 18;
        ctx.drawText(textRenderer, "Ставка:", x + 8, by + 3, 0xFFFFFFFF, false);
        boolean locked = (tab.equals("bj") && !bjDone) || (tab.equals("crash") && crashRunning());
        for (int i = 0; i < Casino.BETS.length; i++) {
            final int bi = i;
            button(ctx, x + 50 + i * 30, by, 28, 13, String.valueOf(Casino.BETS[i]), i == betIndex ? 0xFFFFB300 : 0xFF3D1A55, !locked, () -> betIndex = bi);
        }
    }

    // ---------------------------------------------------------------- блэкджек
    private void blackjack(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x + 6, y, x + w - 6, y + h, 0xFF0F5A2E);
        ctx.drawBorder(x + 6, y, w - 12, h, 0xFFC9A227);
        ctx.drawText(textRenderer, "Дилер" + (ds >= 0 && bjDone ? ": " + ds : ""), x + 12, y + 4, 0xFFE0FFE0, false);
        cards(ctx, dealer, x + 12, y + 14, !bjDone);
        ctx.drawText(textRenderer, "Ты" + (player.length > 0 ? ": " + ps : ""), x + 12, y + 62, 0xFFE0FFE0, false);
        cards(ctx, player, x + 12, y + 72, false);
        int bx = x + w - 92;
        if (bjDone) {
            if (player.length > 0) {
                String res = ps > 21 ? "Перебор!" : ds > 21 || ps > ds ? (ps == 21 && player.length == 2 ? "БЛЭКДЖЕК!" : "Победа!") : ps == ds ? "Ничья" : "Дилер выиграл";
                center(ctx, res, bx + 40, y + 50, res.contains("!") && !res.startsWith("Пере") ? 0xFFFFE082 : 0xFFFFCDD2);
            }
            button(ctx, bx, y + 66, 80, 16, "Раздать $" + bet(), 0xFF2E7D32, LifeCraftClient.data.money >= bet(), () -> bjAction("deal"));
        } else {
            button(ctx, bx, y + 14, 80, 15, "Ещё карту", 0xFF1565C0, true, () -> bjAction("hit"));
            button(ctx, bx, y + 33, 80, 15, "Хватит", 0xFFB71C1C, true, () -> bjAction("stand"));
            button(ctx, bx, y + 52, 80, 15, "Удвоить", 0xFF6A1B9A, player.length == 2 && LifeCraftClient.data.money >= bjBet, () -> bjAction("double"));
        }
    }

    private void bjAction(String action) {
        NbtCompound n = new NbtCompound();
        n.putString("action", action);
        n.putInt("bet", bet());
        LifeCraftClient.send("bj", n);
    }

    private void cards(DrawContext ctx, int[] cards, int x, int y, boolean hidden) {
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "В", "Д", "К"};
        String[] suits = {"♠", "♥", "♦", "♣"};
        int n = cards.length + (hidden ? 1 : 0);
        for (int i = 0; i < n; i++) {
            float k = MathHelper.clamp((System.currentTimeMillis() - dealAt) / 200f - i * 0.6f, 0, 1);
            int cx = (int) (x + i * 26 - (1 - k) * 60), cy = y;
            if (i >= cards.length) {
                roundRect(ctx, cx, cy, 22, 32, 0xFF8E1B3A);
                ctx.drawBorder(cx + 2, cy + 2, 18, 28, 0xFFFFD54F);
                continue;
            }
            int c = cards[i];
            boolean red = c / 13 == 1 || c / 13 == 2;
            roundRect(ctx, cx, cy, 22, 32, 0xFFFAFAFA);
            ctx.drawText(textRenderer, ranks[c % 13], cx + 3, cy + 3, red ? 0xFFD32F2F : 0xFF111111, false);
            bigText(ctx, suits[c / 13], cx + 11, cy + 15, 1.4f, red ? 0xFFD32F2F : 0xFF111111);
        }
    }

    // ---------------------------------------------------------------- краш
    private boolean crashRunning() {
        return crashStart >= 0 && crashEnd < 0;
    }

    private void crashGame(DrawContext ctx, int x, int y, int w, int h) {
        int gx = x + 8, gy = y, gw = w - 110, gh = h - 12;
        ctx.fill(gx, gy, gx + gw, gy + gh, 0xFF0B0614);
        for (int i = 1; i < 4; i++) ctx.fill(gx, gy + gh * i / 4, gx + gw, gy + gh * i / 4 + 1, 0x22FFFFFF);
        long now = mc().world.getTime();
        double m = crashStart < 0 ? 1 : OnlineCasino.mult((crashEnd >= 0 ? crashEnd : now) - crashStart);
        if (crashEnd >= 0) m = crashedAt;
        // кривая: растёт экспонентой, ракета на конце
        long ticks = crashStart < 0 ? 0 : (crashEnd >= 0 ? crashEnd : now) - crashStart;
        double maxM = Math.max(2, m * 1.2);
        int px = gx, py = gy + gh;
        for (int i = 1; i <= 60 && ticks > 0; i++) {
            long t = ticks * i / 60;
            double mv = OnlineCasino.mult(t);
            int nx = gx + (int) (gw * (double) i / 60 * Math.min(1, ticks / 400.0 + 0.3));
            int ny = gy + gh - (int) (gh * (mv - 1) / (maxM - 1));
            ctx.fill(Math.min(px, nx), Math.min(py, ny), Math.max(px, nx) + 2, Math.max(py, ny) + 2, crashEnd >= 0 && cashedAt == 0 ? 0xFFE53935 : 0xFF2EE6D6);
            px = nx;
            py = ny;
        }
        if (crashRunning()) ctx.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFD54F);
        int color = crashEnd >= 0 ? (cashedAt > 0 ? 0xFF7CFF8A : 0xFFE53935) : 0xFFFFFFFF;
        bigText(ctx, String.format("×%.2f", m), gx + gw / 2, gy + gh / 2 - 10, 2.2f, color);
        if (crashEnd >= 0) center(ctx, cashedAt > 0 ? "Забрал на ×" + String.format("%.2f", cashedAt) + " · +$" + crashWin : "КРАШ!", gx + gw / 2, gy + gh / 2 + 14, color);
        else if (cashedAt > 0) center(ctx, "Забрал на ×" + String.format("%.2f", cashedAt) + " · +$" + crashWin, gx + gw / 2, gy + gh / 2 + 14, 0xFF7CFF8A);
        int bx = x + w - 96;
        if (!crashRunning()) button(ctx, bx, y + 10, 88, 18, "Старт $" + bet(), 0xFF2E7D32, LifeCraftClient.data.money >= bet(), () -> {
            NbtCompound n = new NbtCompound();
            n.putInt("bet", bet());
            LifeCraftClient.send("crash_start", n);
        });
        else button(ctx, bx, y + 10, 88, 18, cashedAt > 0 ? "Забрано" : "ЗАБРАТЬ", 0xFFFFB300, cashedAt == 0, () -> LifeCraftClient.send("crash_cashout"));
        ctx.drawText(textRenderer, "Последние:", bx, y + 36, 0xFFAAAAAA, false);
        for (int i = 0; i < history.size(); i++) {
            double hv = history.get(i);
            ctx.drawText(textRenderer, String.format("×%.2f", hv), bx + (i % 2) * 44, y + 48 + (i / 2) * 11, hv >= 2 ? 0xFF7CFF8A : 0xFFE57373, false);
        }
        ctx.drawText(textRenderer, "Забери до краша!", gx, y + h - 9, 0xFF9E86B0, false);
    }

    // ---------------------------------------------------------------- монетка
    private void coinGame(DrawContext ctx, int x, int y, int w, int h) {
        int cx = x + w / 2, cy = y + 50;
        long t = coinAt < 0 ? 99999 : System.currentTimeMillis() - coinAt;
        float spin = t < 1200 ? t / 1200f : 1;
        float flip = (float) Math.cos(spin * Math.PI * 9);
        boolean showHeads = t < 1200 ? flip > 0 : coinHeads;
        int rw = (int) (26 * Math.max(0.12f, Math.abs(t < 1200 ? flip : 1)));
        int lift = t < 1200 ? (int) (Math.sin(spin * Math.PI) * 26) : 0;
        roundRect(ctx, cx - rw, cy - 26 - lift, rw * 2, 52, showHeads ? 0xFFFFC107 : 0xFFB0BEC5);
        if (rw > 10) bigText(ctx, showHeads ? "О" : "Р", cx, cy - 10 - lift, 2.4f, 0xFF5D4037);
        if (t >= 1200 && coinWin >= 0) center(ctx, (coinHeads ? "Орёл" : "Решка") + (coinWin > 0 ? " — выигрыш $" + coinWin + "!" : " — мимо"), cx, cy + 34, coinWin > 0 ? 0xFFFFE082 : 0xFFFFCDD2);
        boolean ready = t >= 1200;
        button(ctx, cx - 92, y + h - 18, 88, 18, "Орёл $" + bet(), 0xFFFFA000, ready && LifeCraftClient.data.money >= bet(), () -> flip(true));
        button(ctx, cx + 4, y + h - 18, 88, 18, "Решка $" + bet(), 0xFF607D8B, ready && LifeCraftClient.data.money >= bet(), () -> flip(false));
    }

    private void flip(boolean heads) {
        coinPick = heads;
        NbtCompound n = new NbtCompound();
        n.putInt("bet", bet());
        n.putBoolean("heads", heads);
        LifeCraftClient.send("coin", n);
        coinAt = System.currentTimeMillis() + 100000; // ждём ответа сервера
        coinWin = -1;
    }
}
