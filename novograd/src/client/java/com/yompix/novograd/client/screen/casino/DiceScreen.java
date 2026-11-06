package com.yompix.novograd.client.screen.casino;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.CasinoMath;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/** Кости: два кубика, ставка на сумму (меньше/ровно/больше 7, дубль, 2 или 12). */
public class DiceScreen extends CasinoScreen {
    private static final long ROLL = 1100;
    private int pick = 0, a = 3, b = 4, win, lastBet;
    private long rollAt = -1;
    private boolean shown = true;
    private final List<int[]> history = new ArrayList<>();

    public DiceScreen() {
        super("Кости", "dice", 0xFFD81B60);
    }

    @Override
    public void onServer(NbtCompound n) {
        a = n.getInt("a");
        b = n.getInt("b");
        win = n.getInt("win");
        lastBet = n.getInt("bet");
        stopWait();
        shown = false;
        rollAt = System.currentTimeMillis();
        if (mc().player != null) mc().player.playSound(ModContent.PHONE_CLICK, 0.8f, 0.6f);
    }

    @Override
    protected boolean busy() {
        return waiting() || rollAt >= 0 && System.currentTimeMillis() - rollAt < ROLL;
    }

    @Override
    protected void drawGame(DrawContext ctx, int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        // сукно
        int fw = w - 120;
        ctx.fillGradient(x, y, x + fw, y + h, 0xFF5A0F2E, 0xFF2A0716);
        ctx.drawBorder(x, y, fw, h, 0xFFC9A227);
        ring(ctx, x + fw / 2, y + h / 2 - 6, Math.min(fw, h) / 2 - 8, 0x40FFD54F);
        long t = rollAt < 0 ? ROLL : now - rollAt;
        boolean rolling = waiting() || t < ROLL;
        if (!rolling && !shown) {
            shown = true;
            history.add(0, new int[]{a, b});
            if (history.size() > 10) history.remove(history.size() - 1);
            result(win, lastBet);
        }
        float k = rolling ? Math.min(1, t / (float) ROLL) : 1;
        int ds = 30;
        for (int i = 0; i < 2; i++) {
            int face = rolling ? 1 + (int) ((now / 70 + i * 3) % 6) : (i == 0 ? a : b);
            float bounce = rolling ? (float) Math.abs(Math.sin(k * Math.PI * 3)) * (1 - k) * 26 : 0;
            float rot = rolling ? (1 - k) * 540 * (i == 0 ? 1 : -1) : 0;
            int cx = x + fw / 2 + (i == 0 ? -24 : 24) + (int) ((1 - k) * (i == 0 ? -40 : 40));
            int cy = y + h / 2 - 10 - (int) bounce;
            die(ctx, cx, cy, ds, face, rot);
        }
        if (!rolling && rollAt >= 0) center(ctx, "Сумма: " + (a + b) + (a == b ? " · дубль" : ""), x + fw / 2, y + h - 30, win > 0 ? Ui.GOLD : 0xFFFFCDD2);
        // история
        int hx = x + 6;
        for (int[] r : history) {
            int s = r[0] + r[1];
            roundRect(ctx, hx, y + h - 14, 16, 10, s == 7 ? 0xFF8E24AA : s < 7 ? 0xFF1565C0 : 0xFFC62828);
            Ui.small(ctx, textRenderer, String.valueOf(s), hx + 8, y + h - 12, 0.7f, 0xFFFFFFFF, 1);
            hx += 18;
            if (hx > x + fw - 18) break;
        }
        // ставки
        int px = x + fw + 6, pw = w - fw - 6;
        ctx.drawText(textRenderer, "На что ставим:", px, y + 2, 0xFFFFFFFF, false);
        for (int i = 0; i < CasinoMath.DICE_BETS.length; i++) {
            final int pi = i;
            String label = CasinoMath.DICE_NAMES[i] + "  ×" + String.format(java.util.Locale.ROOT, "%.1f", CasinoMath.DICE_PAY[i] / 100.0);
            button(ctx, px, y + 14 + i * 18, pw, 15, label, pi == pick ? 0xFFFFB300 : 0xFF6A1B4A, !rolling, () -> pick = pi);
        }
        button(ctx, px, y + h - 20, pw, 18, rolling ? "Бросаем…" : "БРОСИТЬ $" + bet(), 0xFF2E7D32, !rolling && money() >= bet(), () -> {
            startWait();
            NbtCompound n = new NbtCompound();
            n.putInt("pick", pick);
            send("roll", n);
        });
    }

    private void die(DrawContext ctx, int cx, int cy, int s, int face, float rot) {
        var m = ctx.getMatrices();
        m.push();
        m.translate(cx, cy, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot));
        ctx.fill(-s / 2 + 3, -s / 2 + 3, s / 2 + 3, s / 2 + 3, 0x70000000);
        roundRect(ctx, -s / 2, -s / 2, s, s, 0xFFF5F5F5);
        ctx.fill(-s / 2, s / 2 - 3, s / 2, s / 2, 0xFFCFCFCF);
        int q = s / 4;
        int[][] pips = switch (face) {
            case 1 -> new int[][]{{0, 0}};
            case 2 -> new int[][]{{-1, -1}, {1, 1}};
            case 3 -> new int[][]{{-1, -1}, {0, 0}, {1, 1}};
            case 4 -> new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
            case 5 -> new int[][]{{-1, -1}, {1, -1}, {0, 0}, {-1, 1}, {1, 1}};
            default -> new int[][]{{-1, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {1, 1}};
        };
        for (int[] p : pips) {
            int px = p[0] * q, py = p[1] * q;
            ctx.fill(px - 2, py - 2, px + 3, py + 3, face == 1 ? 0xFFD32F2F : 0xFF1A1A1A);
        }
        m.pop();
    }
}
