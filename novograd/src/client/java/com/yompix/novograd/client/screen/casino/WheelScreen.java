package com.yompix.novograd.client.screen.casino;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.CasinoMath;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/**
 * Колесо фортуны: ставка на число 1/2/5/10/20/40 (платит N к 1). Сектора «×3» и «×7» — колесо крутится ещё раз,
 * выигрыш умножается. Анимацию задаёт серверная последовательность секторов.
 */
public class WheelScreen extends CasinoScreen {
    private static final int N = CasinoMath.WHEEL.length;
    private static final float SEG = 360f / N;
    private int pick = 1, win, lastBet;
    private int[] seq = new int[0];
    private int stage = -1;
    private float rot, fromRot, toRot;
    private long stageAt;
    private long mult = 1;
    private final List<Integer> history = new ArrayList<>();
    private int lastTick = -1;

    public WheelScreen() {
        super("Колесо фортуны", "wheel", 0xFFFFB300);
    }

    @Override
    public void onServer(NbtCompound n) {
        seq = n.getIntArray("seq");
        win = n.getInt("win");
        lastBet = n.getInt("bet");
        stopWait();
        mult = 1;
        startStage(0);
    }

    private void startStage(int s) {
        stage = s;
        stageAt = System.currentTimeMillis();
        rot %= 360;
        fromRot = rot;
        float target = -seq[s] * SEG;
        float turns = s == 0 ? 4 : 2;
        toRot = target + 360f * (float) Math.floor((rot - 360 * turns - target) / 360f);
    }

    private long duration() {
        return stage == 0 ? 4200 : 2800;
    }

    @Override
    protected boolean busy() {
        return waiting() || stage >= 0;
    }

    private static int color(int v) {
        return switch (v) {
            case 1 -> 0xFFF9A825;
            case 2 -> 0xFF1E88E5;
            case 5 -> 0xFF8E24AA;
            case 10 -> 0xFF43A047;
            case 20 -> 0xFFF4511E;
            case 40 -> 0xFFD50000;
            case -3 -> 0xFFB0BEC5;
            default -> 0xFFFFD700;
        };
    }

    private static String label(int v) {
        return v < 0 ? "×" + (-v) : String.valueOf(v);
    }

    @Override
    protected void drawGame(DrawContext ctx, int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        if (stage >= 0) {
            float k = Math.min(1, (now - stageAt) / (float) duration());
            float e = 1 - (float) Math.pow(1 - k, 3.2);
            rot = fromRot + (toRot - fromRot) * e;
            int tick = (int) Math.floor(-rot / SEG);
            if (tick != lastTick && mc().player != null) mc().player.playSound(ModContent.PHONE_CLICK, 0.25f, 1.6f);
            lastTick = tick;
            if (k >= 1 && now - stageAt > duration() + 700) {
                int v = CasinoMath.WHEEL[seq[stage]];
                if (v < 0) {
                    mult *= -v;
                    startStage(stage + 1);
                } else {
                    stage = -1;
                    history.add(0, v);
                    if (history.size() > 12) history.remove(history.size() - 1);
                    result(win, lastBet);
                }
            }
        }
        int R = Math.min(h / 2 - 4, 82);
        int cx = x + R + 10, cy = y + h / 2 + 2;
        // обод
        disc(ctx, cx, cy, R + 7, 0xFF3E2410);
        disc(ctx, cx, cy, R + 4, 0xFFC9A227);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            boolean on = (i + now / 150) % 3 == 0;
            int lx = cx + (int) (Math.cos(a) * (R + 5)), ly = cy + (int) (Math.sin(a) * (R + 5));
            ctx.fill(lx - 1, ly - 1, lx + 2, ly + 2, on ? 0xFFFFF59D : 0xFF7A5A20);
        }
        for (int i = 0; i < N; i++) {
            int v = CasinoMath.WHEEL[i];
            var m = ctx.getMatrices();
            m.push();
            m.translate(cx, cy, 0);
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot + i * SEG));
            int segW = (int) Math.ceil(Math.PI * R / N) + 1;
            ctx.fill(-segW, -R, segW, -R / 3, color(v));
            ctx.fill(-segW, -R, -segW + 1, -R / 3, 0x55000000);
            m.translate(0, -R + 4, 0);
            m.scale(0.6f, 0.6f, 1);
            String s = label(v);
            ctx.drawText(textRenderer, s, -textRenderer.getWidth(s) / 2, 0, v == 1 || v < 0 ? 0xFF1A1A1A : 0xFFFFFFFF, false);
            m.pop();
        }
        disc(ctx, cx, cy, R / 3 + 2, 0xFFC9A227);
        disc(ctx, cx, cy, R / 3, 0xFF2A0F3D);
        Ui.big(ctx, textRenderer, mult > 1 ? "×" + mult : "★", cx, cy - 6, mult > 1 ? 1.4f : 1.6f, Ui.GOLD, 1);
        // стрелка сверху
        for (int i = 0; i < 8; i++) ctx.fill(cx - 8 + i, cy - R - 10 + i, cx + 9 - i, cy - R - 9 + i, 0xFFFFFFFF);
        ctx.fill(cx - 1, cy - R - 12, cx + 2, cy - R - 2, 0xFFFF2E88);
        // правая панель: ставки
        int px = cx + R + 16, pw = x + w - px;
        ctx.drawText(textRenderer, "Ставлю на число:", px, y + 2, 0xFFFFFFFF, false);
        int bw = (pw - 4) / 2;
        for (int i = 0; i < CasinoMath.WHEEL_BETS.length; i++) {
            int v = CasinoMath.WHEEL_BETS[i];
            int bx = px + (i % 2) * (bw + 4), by = y + 14 + (i / 2) * 22;
            boolean on = pick == v;
            roundRect(ctx, bx, by, bw, 19, on ? lighten(color(v), 0.25f) : darken(color(v), 0.45f));
            if (on) ctx.drawBorder(bx - 1, by - 1, bw + 2, 21, 0xFFFFFFFF);
            Ui.big(ctx, textRenderer, String.valueOf(v), bx + 12, by + 5, 1.1f, 0xFFFFFFFF, 1);
            Ui.small(ctx, textRenderer, "платит " + v + ":1", bx + bw - 3, by + 7, 0.65f, 0xFFFFFFFF, 2);
            if (stage < 0) area(bx, by, bw, 19, () -> pick = v);
        }
        Ui.small(ctx, textRenderer, "«×3» и «×7» — ещё оборот, выигрыш умножается", px, y + 82, 0.62f, Ui.DIM, 0);
        // история
        int hx = px;
        for (int v : history) {
            if (hx > x + w - 14) break;
            roundRect(ctx, hx, y + 92, 13, 10, color(v));
            Ui.small(ctx, textRenderer, String.valueOf(v), hx + 6.5f, y + 94, 0.6f, v == 1 ? 0xFF1A1A1A : 0xFFFFFFFF, 1);
            hx += 15;
        }
        button(ctx, px, y + h - 22, pw, 18, stage >= 0 ? "Крутится…" : "КРУТИТЬ $" + bet(), 0xFFB71C1C, !busy() && money() >= bet(), () -> {
            startWait();
            NbtCompound n = new NbtCompound();
            n.putInt("pick", pick);
            send("spin", n);
        });
    }
}
