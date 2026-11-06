package com.yompix.novograd.client.screen.casino;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.CasinoMath;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;

/** Плинко: шарик падает сквозь 12 рядов колышков в лунку с множителем. Путь шарика приходит с сервера. */
public class PlinkoScreen extends CasinoScreen {
    private static final int ROWS = CasinoMath.PLINKO_ROWS;
    private static final long ROW_MS = 140;

    private record Ball(int path, int slot, int risk, int win, int bet, long at, boolean[] done) {}

    private final List<Ball> balls = new ArrayList<>();
    private static int risk = 1;
    private final long[] flash = new long[ROWS + 1];
    private final List<Integer> history = new ArrayList<>();

    public PlinkoScreen() {
        super("Плинко", "plinko", 0xFF00ACC1);
    }

    @Override
    public void onServer(NbtCompound n) {
        stopWait();
        balls.add(new Ball(n.getInt("path"), n.getInt("slot"), n.getInt("risk"), n.getInt("win"), n.getInt("bet"), System.currentTimeMillis(), new boolean[1]));
    }

    @Override
    protected boolean busy() {
        return waiting();
    }

    private static int slotColor(int mult10) {
        if (mult10 >= 100) return 0xFFFF1744;
        if (mult10 >= 30) return 0xFFFF6D00;
        if (mult10 >= 14) return 0xFFFFC400;
        if (mult10 >= 10) return 0xFF76FF03;
        return 0xFF00B8D4;
    }

    @Override
    protected void drawGame(DrawContext ctx, int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        int bw = w - 110;
        ctx.fillGradient(x, y, x + bw, y + h, 0xFF06222B, 0xFF041318);
        ctx.drawBorder(x, y, bw, h, 0xFF00ACC1);
        int gap = Math.min((bw - 16) / (ROWS + 1), (h - 26) / (ROWS + 1));
        int cx = x + bw / 2, top = y + 10;
        // колышки: в ряду r — r + 3 штук
        for (int r = 0; r < ROWS; r++)
            for (int i = 0; i < r + 3; i++) {
                int px = cx + (int) ((i - (r + 2) / 2f) * gap), py = top + r * gap + gap;
                ctx.fill(px - 1, py - 1, px + 2, py + 2, 0xFFCFE8EE);
            }
        // лунки
        int[] mults = CasinoMath.PLINKO[risk];
        int sy = top + ROWS * gap + gap + 4;
        for (int s = 0; s <= ROWS; s++) {
            int sx = cx + (int) ((s - ROWS / 2f) * gap) - gap / 2 + 1;
            float f = Math.max(0, 1 - (now - flash[s]) / 500f);
            int col = slotColor(mults[s]);
            roundRect(ctx, sx, sy - (int) (f * 3), gap - 2, 12, f > 0 ? lighten(col, 0.5f * f) : darken(col, 0.15f));
            String lbl = mults[s] % 10 == 0 ? String.valueOf(mults[s] / 10) : mults[s] / 10 + "." + mults[s] % 10;
            Ui.small(ctx, textRenderer, lbl, sx + (gap - 2) / 2f, sy + 3 - (int) (f * 3), gap < 16 ? 0.5f : 0.6f, 0xFF101010, 1);
        }
        // шарики
        for (int bi = balls.size() - 1; bi >= 0; bi--) {
            Ball b = balls.get(bi);
            float t = (now - b.at()) / (float) ROW_MS;
            if (t >= ROWS + 1) {
                if (!b.done()[0]) {
                    b.done()[0] = true;
                    flash[b.slot()] = now;
                    history.add(0, b.win() * 10 / Math.max(1, b.bet()));
                    if (history.size() > 8) history.remove(history.size() - 1);
                    result(b.win(), b.bet());
                }
                if (t > ROWS + 3) balls.remove(bi);
                continue;
            }
            // позиция: после ряда r шарик смещён на число «вправо» до r
            int r = (int) Math.floor(t);
            float k = t - r;
            float col0 = rights(b.path(), r) - r / 2f, col1 = rights(b.path(), r + 1) - (r + 1) / 2f;
            float bx = cx + (col0 + (col1 - col0) * k) * gap;
            float by = top + (r + k) * gap + gap - 4 - (float) Math.sin(k * Math.PI) * gap * 0.35f;
            disc(ctx, (int) bx, (int) by, 3, 0xFFFF2E88);
            ctx.fill((int) bx - 1, (int) by - 2, (int) bx + 1, (int) by, 0xFFFFC1DA);
        }
        // панель
        int px = x + bw + 6, pw = w - bw - 6;
        ctx.drawText(textRenderer, "Риск:", px, y + 2, 0xFFFFFFFF, false);
        for (int i = 0; i < 3; i++) {
            final int ri = i;
            button(ctx, px, y + 13 + i * 17, pw, 14, CasinoMath.PLINKO_RISK[i], risk == i ? 0xFFFFB300 : 0xFF0E4A57, balls.isEmpty(), () -> risk = ri);
        }
        ctx.drawText(textRenderer, "Последние:", px, y + 68, Ui.DIM, false);
        for (int i = 0; i < history.size(); i++) {
            int m = history.get(i);
            String s = "×" + m / 10 + "." + m % 10;
            Ui.small(ctx, textRenderer, s, px + (i % 2) * (pw / 2), y + 80 + (i / 2) * 9, 0.75f, m >= 10 ? Ui.MONEY : 0xFFE57373, 0);
        }
        button(ctx, px, y + h - 22, pw, 18, "БРОСИТЬ $" + bet(), 0xFF00838F, !waiting() && balls.size() < 6 && money() >= bet(), () -> {
            startWait();
            NbtCompound n = new NbtCompound();
            n.putInt("risk", risk);
            send("drop", n);
            if (mc().player != null) mc().player.playSound(ModContent.PHONE_CLICK, 0.5f, 1.8f);
        });
    }

    /** Сколько раз шарик ушёл вправо на первых n рядах. */
    private static int rights(int path, int n) {
        n = Math.min(n, ROWS);
        return Integer.bitCount(path & ((1 << n) - 1));
    }
}
