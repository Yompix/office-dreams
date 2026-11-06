package com.yompix.novograd.client.screen;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.life.Casino;
import com.yompix.novograd.life.OnlineCasino;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.CasinoMath;
import net.minecraft.client.MinecraftClient;
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

    // краш: состояние живёт между открытиями телефона (раунд идёт на сервере, пока не выведен или не крашнулся)
    private static int crashPhase; // 0 — нет раунда, 1 — летит, 2 — закончен
    private static long syncE, syncAt, animAt;
    private static double cashedAt, crashPoint = -1, animFromE;
    private static int crashWin, crashBet, autoIndex;
    private static int[] crashHist = new int[0];

    // монетка
    private long coinAt = -1;
    private boolean coinHeads, coinPick;
    private int coinWin = -1;

    public OnlineCasinoScreen() {
        this("bj");
    }

    public OnlineCasinoScreen(String tab) {
        super("Онлайн-казино");
        this.tab = tab;
        NbtCompound n = new NbtCompound();
        n.putString("g", "crash");
        n.putString("a", "state");
        NovogradClient.send("cg", n); // идёт ли раунд «Краша» и последние результаты
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

    /** Пакеты «Краша». Вызывается всегда, даже если экран закрыт: раунд продолжается и без телефона. */
    public static void crashPacket(String type, NbtCompound n) {
        long now = System.currentTimeMillis();
        if (n.contains("hist")) crashHist = n.getIntArray("hist");
        switch (type) {
            case "crash_start" -> {
                crashPhase = 1;
                syncE = n.getLong("e");
                syncAt = now;
                cashedAt = 0;
                crashWin = 0;
                crashPoint = -1;
                crashBet = n.getInt("bet");
            }
            case "crash_sync" -> {
                if (crashPhase == 1) {
                    syncE = n.getLong("e");
                    syncAt = now;
                }
            }
            case "crash_state" -> {
                if (n.getBoolean("running")) {
                    crashPhase = 1;
                    syncE = n.getLong("e");
                    syncAt = now;
                    crashBet = n.getInt("bet");
                    cashedAt = 0;
                } else if (crashPhase == 1) crashPhase = 0;
            }
            case "crash_cashout" -> {
                cashedAt = n.getDouble("at");
                crashWin = n.getInt("win");
                crashPoint = n.getDouble("point");
                animFromE = n.getLong("e");
                animAt = now;
                crashPhase = 2;
                if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.playSound(ModContent.CASH, 0.8f, 1.2f);
            }
            case "crash_end" -> {
                crashPoint = n.getDouble("point");
                cashedAt = 0;
                crashWin = 0;
                crashPhase = 2;
                animAt = 0;
                if (MinecraftClient.getInstance().player != null) MinecraftClient.getInstance().player.playSound(ModContent.ANSWER_WRONG, 0.7f, 0.8f);
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
        int w = Math.min(384, width - 12), h = Math.min(228, height - 10);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 30);
        panel(ctx, x, y, w, h, 0xFF2A0F3D, 0xFF12061C);
        ctx.fill(x, y, x + w, y + 20, 0xFF6A1B9A);
        ctx.drawText(textRenderer, "ФОРТУНА.онлайн  18+", x + 8, y + 6, 0xFFFFD54F, true);
        String bal = "$" + NovogradClient.data.money;
        ctx.drawText(textRenderer, bal, x + w - 8 - textRenderer.getWidth(bal), y + 6, 0xFF7CFF8A, true);
        String[][] tabs = {{"bj", "Блэкджек"}, {"crash", "Краш"}, {"coin", "Монетка"}, {"slots", "Автоматы"}, {"roulette", "Рулетка"}, {"lobby", "Все игры ▸"}};
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
                    case "lobby" -> mc().setScreen(new com.yompix.novograd.client.screen.casino.CasinoLobbyScreen());
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
            button(ctx, bx, y + 66, 80, 16, "Раздать $" + bet(), 0xFF2E7D32, NovogradClient.data.money >= bet(), () -> bjAction("deal"));
        } else {
            button(ctx, bx, y + 14, 80, 15, "Ещё карту", 0xFF1565C0, true, () -> bjAction("hit"));
            button(ctx, bx, y + 33, 80, 15, "Хватит", 0xFFB71C1C, true, () -> bjAction("stand"));
            button(ctx, bx, y + 52, 80, 15, "Удвоить", 0xFF6A1B9A, player.length == 2 && NovogradClient.data.money >= bjBet, () -> bjAction("double"));
        }
    }

    private void bjAction(String action) {
        NbtCompound n = new NbtCompound();
        n.putString("action", action);
        n.putInt("bet", bet());
        NovogradClient.send("bj", n);
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
        return crashPhase == 1;
    }

    /** Сколько тиков раунда показывать: не дальше чем на 6 тиков (0,3 с) впереди последней сверки с сервером. */
    private static double shownTicks(long now) {
        if (crashPhase == 1) return syncE + Math.min(6, (now - syncAt) / 50.0);
        double end = Math.log(Math.max(1, crashPoint)) / CasinoMath.CRASH_RATE;
        if (cashedAt > 0) { // вывел — докручиваем до точки краша за ~1 с
            double k = Math.min(1, (now - animAt) / 1000.0);
            return animFromE + (end - animFromE) * k;
        }
        return end;
    }

    private void crashGame(DrawContext ctx, int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        int gx = x + 8, gy = y + 14, gw = w - 112, gh = h - 26;
        // история: последние 10 точек краша (красные < ×2, зелёные ≥ ×2, золотые ≥ ×10)
        int hx = gx;
        for (int v : crashHist) {
            String s = String.format(java.util.Locale.ROOT, "%.2f", v / 100.0);
            int tw = textRenderer.getWidth(s) * 3 / 4 + 6;
            if (hx + tw > gx + gw) break;
            int c = v >= 1000 ? 0xFFFFB300 : v >= 200 ? 0xFF2E9E4F : 0xFFC62828;
            roundRect(ctx, hx, y, tw, 11, c);
            Ui.small(ctx, textRenderer, s, hx + tw / 2f, y + 2, 0.75f, 0xFFFFFFFF, 1);
            hx += tw + 2;
        }
        ctx.fill(gx, gy, gx + gw, gy + gh, 0xFF0B0614);
        for (int i = 1; i < 4; i++) ctx.fill(gx, gy + gh * i / 4, gx + gw, gy + gh * i / 4 + 1, 0x22FFFFFF);
        double e = crashPhase == 0 ? 0 : shownTicks(now);
        double m = crashPhase == 0 ? 1 : OnlineCasino.mult((long) Math.floor(e));
        boolean over = crashPhase == 2 && (cashedAt == 0 || now - animAt >= 1000);
        if (over) m = crashPoint; // ровно то, что сказал сервер
        // кривая
        double tMax = Math.max(e * 1.15, 180), maxM = Math.max(2, m * 1.2);
        int px = gx, py = gy + gh;
        boolean lost = crashPhase == 2 && cashedAt == 0;
        for (int i = 1; i <= 60 && e > 0; i++) {
            double t = e * i / 60;
            double mv = Math.min(m, CasinoMath.crashMult(t));
            int nx = gx + (int) (gw * t / tMax);
            int ny = gy + gh - (int) ((gh - 4) * (mv - 1) / (maxM - 1));
            ctx.fill(Math.min(px, nx), Math.min(py, ny), Math.max(px, nx) + 2, Math.max(py, ny) + 2, lost ? 0xFFE53935 : 0xFF2EE6D6);
            px = nx;
            py = ny;
        }
        if (crashRunning()) ctx.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFD54F);
        if (lost) bigText(ctx, "✹", px, py - 6, 1.5f, 0xFFFF8A00);
        int color = crashPhase == 2 ? (cashedAt > 0 ? 0xFF7CFF8A : 0xFFE53935) : 0xFFFFFFFF;
        bigText(ctx, String.format(java.util.Locale.ROOT, "×%.2f", m), gx + gw / 2, gy + gh / 2 - 12, 2.2f, color);
        if (crashPhase == 2 && cashedAt > 0)
            center(ctx, "Забрал на ×" + String.format(java.util.Locale.ROOT, "%.2f", cashedAt) + " · +$" + crashWin + (over ? " · краш был на ×" + String.format(java.util.Locale.ROOT, "%.2f", crashPoint) : ""), gx + gw / 2, gy + gh / 2 + 12, 0xFF7CFF8A);
        else if (crashPhase == 2) center(ctx, crashPoint <= 1.0 ? "КРАШ СРАЗУ!" : "КРАШ!", gx + gw / 2, gy + gh / 2 + 12, color);
        else if (crashPhase == 1 && CasinoMath.CRASH_AUTO[autoIndex] > 0)
            center(ctx, "Автовывод на ×" + String.format(java.util.Locale.ROOT, "%.2f", CasinoMath.CRASH_AUTO[autoIndex] / 100.0), gx + gw / 2, gy + gh / 2 + 12, 0xFF9E86B0);
        // управление
        int bx = x + w - 98;
        if (!crashRunning()) button(ctx, bx, y + 2, 90, 18, "СТАРТ $" + bet(), 0xFF2E7D32, NovogradClient.data.money >= bet(), () -> {
            NbtCompound n = new NbtCompound();
            n.putInt("bet", bet());
            n.putInt("auto", CasinoMath.CRASH_AUTO[autoIndex]);
            NovogradClient.send("crash_start", n);
        });
        else button(ctx, bx, y + 2, 90, 18, "ЗАБРАТЬ $" + (int) Math.floor(crashBet * m), 0xFFFFB300, true, () -> NovogradClient.send("crash_cashout"));
        ctx.drawText(textRenderer, "Автовывод:", bx, y + 26, 0xFFAAAAAA, false);
        for (int i = 0; i < CasinoMath.CRASH_AUTO.length; i++) {
            final int ai = i;
            int v = CasinoMath.CRASH_AUTO[i];
            String l = v == 0 ? "Выкл" : "×" + (v % 100 == 0 ? String.valueOf(v / 100) : String.format(java.util.Locale.ROOT, "%.1f", v / 100.0));
            button(ctx, bx + (i % 3) * 30, y + 37 + (i / 3) * 16, 28, 13, l, i == autoIndex ? 0xFFFFB300 : 0xFF3D1A55, !crashRunning(), () -> autoIndex = ai);
        }
        Ui.small(ctx, textRenderer, "3% раундов — краш сразу", bx, y + 74, 0.7f, 0xFF9E86B0, 0);
        Ui.small(ctx, textRenderer, "Казино ~5% · макс. ×100", bx, y + 83, 0.7f, 0xFF9E86B0, 0);
        ctx.drawText(textRenderer, "Забери до краша!", gx, gy + gh + 2, 0xFF9E86B0, false);
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
        button(ctx, cx - 92, y + h - 18, 88, 18, "Орёл $" + bet(), 0xFFFFA000, ready && NovogradClient.data.money >= bet(), () -> flip(true));
        button(ctx, cx + 4, y + h - 18, 88, 18, "Решка $" + bet(), 0xFF607D8B, ready && NovogradClient.data.money >= bet(), () -> flip(false));
    }

    private void flip(boolean heads) {
        coinPick = heads;
        NbtCompound n = new NbtCompound();
        n.putInt("bet", bet());
        n.putBoolean("heads", heads);
        NovogradClient.send("coin", n);
        coinAt = System.currentTimeMillis() + 100000; // ждём ответа сервера
        coinWin = -1;
    }
}
