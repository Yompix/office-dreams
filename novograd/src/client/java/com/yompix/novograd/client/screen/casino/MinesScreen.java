package com.yompix.novograd.client.screen.casino;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.CasinoMath;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

/** Мины: поле 5×5, открывай клетки и забирай растущий множитель, пока не попалась мина. Мины знает только сервер. */
public class MinesScreen extends CasinoScreen {
    private static int mineIndex = 1;
    private boolean playing;
    private byte[] open = new byte[25], grid = null;
    private int opened, mines = 3, bet0, boomCell = -1;
    private final long[] openedAt = new long[25];

    public MinesScreen() {
        super("Мины", "mines", 0xFF7CB342);
        send("state");
    }

    @Override
    public void onServer(NbtCompound n) {
        stopWait();
        String st = n.getString("state");
        if (n.contains("open")) {
            byte[] o = n.getByteArray("open");
            long now = System.currentTimeMillis();
            for (int i = 0; i < 25 && i < o.length; i++) if (o[i] != 0 && open[i] == 0) openedAt[i] = now;
            open = o;
            opened = n.getInt("opened");
            mines = n.getInt("mines");
            bet0 = n.getInt("bet");
        }
        grid = n.contains("grid") ? n.getByteArray("grid") : null;
        switch (st) {
            case "play" -> {
                if (!playing) {
                    boomCell = -1;
                    clearBanner();
                }
                playing = true;
                if (mc().player != null && opened > 0) mc().player.playSound(ModContent.ANSWER_RIGHT, 0.4f, 1f + opened * 0.05f);
            }
            case "boom" -> {
                playing = false;
                boomCell = n.getInt("cell");
                result(0, bet0);
                if (mc().player != null) mc().player.playSound(ModContent.GUNSHOT, 0.5f, 0.6f);
            }
            case "cashed" -> {
                playing = false;
                result(n.getInt("win"), bet0);
            }
            default -> playing = false;
        }
    }

    @Override
    protected boolean busy() {
        return playing || waiting();
    }

    @Override
    protected void drawGame(DrawContext ctx, int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        int cell = Math.min((h - 4) / 5, 34), gw = cell * 5;
        int gx = x + 4, gy = y + (h - gw) / 2;
        ctx.fill(gx - 3, gy - 3, gx + gw + 3, gy + gw + 3, 0xFF1B2A12);
        for (int i = 0; i < 25; i++) {
            int cx = gx + (i % 5) * cell, cy = gy + (i / 5) * cell;
            boolean isOpen = open[i] != 0;
            boolean mine = grid != null && grid[i] != 0;
            int c;
            if (isOpen && !mine) c = 0xFF2E7D32;
            else if (mine) c = i == boomCell ? 0xFFD50000 : 0xFF5D1F1F;
            else c = hover(cx, cy, cell - 2, cell - 2) && playing ? 0xFF4E6A3A : 0xFF33452A;
            float pop = isOpen ? Math.max(0, 1 - (now - openedAt[i]) / 250f) : 0;
            int in = (int) (pop * 4);
            roundRect(ctx, cx + in, cy + in, cell - 2 - in * 2, cell - 2 - in * 2, c);
            if (!isOpen && !mine) ctx.fill(cx + 1, cy + 1, cx + cell - 3, cy + 3, 0x30FFFFFF);
            if (isOpen && !mine) Ui.big(ctx, textRenderer, "◆", cx + cell / 2f - 1, cy + cell / 2f - 7, 1.4f, 0xFF7CFFB0, 1);
            if (mine) Ui.big(ctx, textRenderer, "✹", cx + cell / 2f - 1, cy + cell / 2f - 7, 1.4f, i == boomCell ? 0xFFFFFF00 : 0xFFFF8A80, 1);
            if (playing && !isOpen && !waiting()) {
                final int ci = i;
                area(cx, cy, cell - 2, cell - 2, () -> {
                    startWait();
                    NbtCompound n = new NbtCompound();
                    n.putInt("cell", ci);
                    send("open", n);
                });
            }
        }
        // панель
        int px = gx + gw + 12, pw = x + w - px;
        if (!playing) {
            ctx.drawText(textRenderer, "Сколько мин:", px, y + 2, 0xFFFFFFFF, false);
            int bw = (pw - 6) / 4;
            for (int i = 0; i < CasinoMath.MINES_COUNTS.length; i++) {
                final int mi = i;
                button(ctx, px + i * (bw + 2), y + 13, bw, 15, String.valueOf(CasinoMath.MINES_COUNTS[i]), mineIndex == i ? 0xFFFFB300 : 0xFF33452A, true, () -> mineIndex = mi);
            }
            int m = CasinoMath.MINES_COUNTS[mineIndex];
            ctx.drawText(textRenderer, "Множители:", px, y + 36, Ui.DIM, false);
            for (int k = 1; k <= 6 && k <= 25 - m; k++) {
                Ui.small(ctx, textRenderer, k + " кл. → " + mult(CasinoMath.minesMult(m, k)), px + ((k - 1) % 2) * (pw / 2), y + 48 + ((k - 1) / 2) * 10, 0.75f, 0xFFD8F5C0, 0);
            }
            button(ctx, px, y + h - 22, pw, 18, "ИГРАТЬ $" + bet(), 0xFF2E7D32, !waiting() && money() >= bet(), () -> {
                startWait();
                NbtCompound n = new NbtCompound();
                n.putInt("mines", CasinoMath.MINES_COUNTS[mineIndex]);
                send("start", n);
            });
        } else {
            double cur = CasinoMath.minesMult(mines, opened), nxt = CasinoMath.minesMult(mines, opened + 1);
            ctx.drawText(textRenderer, "Мин: " + mines + " · ставка $" + bet0, px, y + 2, 0xFFFFFFFF, false);
            Ui.big(ctx, textRenderer, mult(cur), px, y + 16, 2f, Ui.GOLD, 0);
            ctx.drawText(textRenderer, "Следующая: " + mult(nxt), px, y + 38, Ui.DIM, false);
            ctx.drawText(textRenderer, "Открыто: " + opened + " из " + (25 - mines), px, y + 50, Ui.DIM, false);
            long cash = CasinoMath.minesPay(bet0, cur);
            button(ctx, px, y + h - 22, pw, 18, opened == 0 ? "Открой клетку" : "ЗАБРАТЬ $" + cash, 0xFFFFB300, opened > 0 && !waiting(), () -> {
                startWait();
                send("cash");
            });
        }
    }
}
