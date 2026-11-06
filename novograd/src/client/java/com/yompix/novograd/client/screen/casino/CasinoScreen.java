package com.yompix.novograd.client.screen.casino;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.Casino;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

/**
 * Основа экранов новых игр казино: тёмное неоновое окно, баланс, «◂ Лобби», ряд ставок, плашка результата.
 * Ответы сервера приходят в {@link #onServer}; исход решает сервер, экран только показывает.
 */
public abstract class CasinoScreen extends LifeScreen {
    /** Ставка запоминается между играми. */
    protected static int betIndex = 1;
    protected final String game;
    protected final int accent;
    protected int x, y, w, h;
    /** Плашка результата: текст, цвет, когда показана. */
    private String banner = "";
    private int bannerColor;
    private long bannerAt;

    /** Ждём ответа сервера (если он отказал молча — через 3 с снова можно играть). */
    private long waitAt = -1;

    protected void startWait() {
        waitAt = System.currentTimeMillis();
    }

    protected void stopWait() {
        waitAt = -1;
    }

    protected boolean waiting() {
        return waitAt >= 0 && System.currentTimeMillis() - waitAt < 3000;
    }

    protected CasinoScreen(String title, String game, int accent) {
        super(title);
        this.game = game;
        this.accent = accent;
    }

    public String game() {
        return game;
    }

    protected static int bet() {
        return Casino.BETS[betIndex];
    }

    protected static int money() {
        return NovogradClient.data.money;
    }

    /** Запрос серверу: «cg» {g, a, bet, …}. */
    protected void send(String action, NbtCompound n) {
        n.putString("g", game);
        n.putString("a", action);
        if (!n.contains("bet")) n.putInt("bet", bet());
        NovogradClient.send("cg", n);
    }

    protected void send(String action) {
        send(action, new NbtCompound());
    }

    public abstract void onServer(NbtCompound n);

    /** Пока идёт раунд/анимация — ставку менять нельзя. */
    protected abstract boolean busy();

    protected abstract void drawGame(DrawContext ctx, int x, int y, int w, int h);

    protected void result(long win, int bet) {
        if (win > bet) banner(win >= bet * 10 ? "КРУПНЫЙ ВЫИГРЫШ! +$" + win : "Выигрыш +$" + win, win >= bet * 10 ? Ui.GOLD : Ui.MONEY);
        else if (win == bet && win > 0) banner("Ставка вернулась · $" + win, Ui.CYAN);
        else if (win > 0) banner("Возврат $" + win, Ui.DIM);
        else banner("Мимо · −$" + bet, 0xFFFF7A85);
        if (mc().player != null) {
            if (win > bet) mc().player.playSound(win >= bet * 10 ? ModContent.LEVEL_UP : ModContent.ANSWER_RIGHT, 0.6f, 1.1f);
            else if (win < bet) mc().player.playSound(ModContent.ANSWER_WRONG, 0.4f, 0.9f);
        }
    }

    protected void banner(String text, int color) {
        banner = text;
        bannerColor = color;
        bannerAt = System.currentTimeMillis();
    }

    protected void clearBanner() {
        banner = "";
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        w = Math.min(390, width - 12);
        h = Math.min(228, height - 10);
        x = width / 2 - w / 2;
        y = (int) (height / 2 - h / 2 + (1 - a) * 30);
        panel(ctx, x, y, w, h, darken(accent, 0.75f), 0xFF0A0610);
        // шапка
        ctx.fill(x, y + 3, x + w, y + 19, darken(accent, 0.55f));
        Ui.slant(ctx, x + 4, y + 5, textRenderer.getWidth(getTitle().getString()) + 14, 12, 4, accent);
        ctx.drawText(textRenderer, getTitle().getString(), x + 10, y + 7, 0xFF0A0610, false);
        String bal = "$" + money();
        ctx.drawText(textRenderer, bal, x + w - 8 - textRenderer.getWidth(bal), y + 7, Ui.MONEY, true);
        button(ctx, x + w - 62 - textRenderer.getWidth(bal), y + 5, 50, 12, "◂ Лобби", 0xFF5B2A86, !busy(), () -> mc().setScreen(new CasinoLobbyScreen()));
        drawGame(ctx, x + 6, y + 23, w - 12, h - 44);
        // ставка
        int by = y + h - 18;
        ctx.drawText(textRenderer, "Ставка:", x + 8, by + 3, 0xFFFFFFFF, false);
        for (int i = 0; i < Casino.BETS.length; i++) {
            final int bi = i;
            button(ctx, x + 50 + i * 31, by, 29, 13, String.valueOf(Casino.BETS[i]), i == betIndex ? 0xFFFFB300 : 0xFF3D1A55, !busy(), () -> betIndex = bi);
        }
        // результат
        if (!banner.isEmpty()) {
            long t = System.currentTimeMillis() - bannerAt;
            float k = Math.min(1, t / 180f);
            float fade = t > 4500 ? Math.max(0, 1 - (t - 4500) / 600f) : 1;
            if (fade > 0) {
                int bw = textRenderer.getWidth(banner) + 20, bx = x + w - bw - 8, byy = by - 1;
                Ui.slant(ctx, bx, byy, (int) (bw * Ui.easeOut(k)), 15, 4, Ui.alpha(0xF0120D1A, fade));
                ctx.fill(bx, byy + 14, bx + (int) (bw * Ui.easeOut(k)), byy + 15, Ui.alpha(bannerColor, fade));
                if (k >= 1) ctx.drawText(textRenderer, banner, bx + 10, byy + 4, Ui.alpha(bannerColor, fade), true);
            }
        }
    }

    // ---------------------------------------------------------------- общая отрисовка
    protected void card(DrawContext ctx, int c, int cx, int cy, int cw, int ch, boolean faceDown) {
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "В", "Д", "К"};
        String[] suits = {"♠", "♥", "♦", "♣"};
        ctx.fill(cx + 2, cy + 2, cx + cw + 2, cy + ch + 2, 0x60000000);
        if (faceDown || c < 0) {
            roundRect(ctx, cx, cy, cw, ch, 0xFF8E1B3A);
            ctx.drawBorder(cx + 2, cy + 2, cw - 4, ch - 4, 0xFFFFD54F);
            bigText(ctx, "★", cx + cw / 2, cy + ch / 2 - 6, 1.4f, 0xFFFFD54F);
            return;
        }
        boolean red = c / 13 == 1 || c / 13 == 2;
        int col = red ? 0xFFD32F2F : 0xFF111111;
        roundRect(ctx, cx, cy, cw, ch, 0xFFFAFAFA);
        ctx.drawText(textRenderer, ranks[c % 13], cx + 3, cy + 3, col, false);
        bigText(ctx, suits[c / 13], cx + cw / 2, cy + ch / 2 - 4, 2f, col);
    }

    protected void ring(DrawContext ctx, int cx, int cy, int r, int color) {
        int steps = Math.max(24, r * 3);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps;
            int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
            ctx.fill(px, py, px + 2, py + 2, color);
        }
    }

    protected void disc(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    protected static String mult(double m) {
        return String.format(java.util.Locale.ROOT, "×%.2f", m);
    }
}
