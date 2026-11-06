package com.yompix.novograd.client.screen.casino;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.CasinoMath;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

/** Тараканьи бега: 5 бегунов с коэффициентами, ставка на одного, забег по дорожкам. Порядок финиша — с сервера. */
public class RaceScreen extends CasinoScreen {
    private static final int[] COLORS = {0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047, 0xFFAB47BC};
    private int[] names = new int[0], odds = new int[0];
    private int[] order;
    private float[] finish = new float[5], freq = new float[5], phase = new float[5];
    private NbtCompound next;
    private int pick = 0, racePick, win, lastBet;
    private long raceAt = -1;
    private boolean announced = true;

    public RaceScreen() {
        super("Тараканьи бега", "race", 0xFFA1887F);
        send("card");
    }

    @Override
    public void onServer(NbtCompound n) {
        stopWait();
        if (n.contains("order")) {
            names = n.getIntArray("names");
            odds = n.getIntArray("odds");
            order = n.getIntArray("order");
            raceNames = names;
            pick = n.getInt("pick");
            racePick = pick;
            win = n.getInt("win");
            lastBet = n.getInt("bet");
            next = n.getCompound("next");
            java.util.Random r = new java.util.Random();
            for (int place = 0; place < 5; place++) {
                int lane = order[place];
                finish[lane] = 7000 + place * 320 + r.nextInt(200);
                freq[lane] = 0.0015f + r.nextFloat() * 0.002f;
                phase[lane] = r.nextFloat() * 6.28f;
            }
            raceAt = System.currentTimeMillis();
            announced = false;
            if (mc().player != null) mc().player.playSound(ModContent.SCHOOL_BELL, 0.4f, 1.6f);
        } else if (raceAt < 0 || announced) {
            names = n.getIntArray("names");
            odds = n.getIntArray("odds");
        }
    }

    private boolean racing() {
        return raceAt >= 0 && !announced;
    }

    @Override
    protected boolean busy() {
        return waiting() || racing();
    }

    private float progress(int lane, long t) {
        if (order == null) return 0;
        float k = Math.min(1, t / finish[lane]);
        float wob = (float) Math.sin(t * freq[lane] + phase[lane]) * 0.07f * k * (1 - k) * 4;
        return Math.max(0, Math.min(1, k * k * (3 - 2 * k) * 0.25f + k * 0.75f + wob));
    }

    @Override
    protected void drawGame(DrawContext ctx, int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        long t = raceAt < 0 ? 0 : now - raceAt;
        if (racing() && t > 7000 + 4 * 320 + 900) {
            announced = true;
            result(win, lastBet);
            if (next != null && next.contains("names")) {
                names = next.getIntArray("names");
                odds = next.getIntArray("odds");
            }
        }
        boolean showRace = order != null && (racing() || now - raceAt < 9000 + 4000);
        int lw = w - 112, laneH = Math.min(24, (h - 8) / 5);
        // трек
        ctx.fillGradient(x, y, x + lw, y + laneH * 5 + 4, 0xFF4E342E, 0xFF2E1E1A);
        int fx = x + lw - 18;
        for (int i = 0; i < (laneH * 5) / 4; i++) ctx.fill(fx, y + 2 + i * 4, fx + 3, y + 4 + i * 4, i % 2 == 0 ? 0xFFFFFFFF : 0xFF111111);
        int[] ln = showRace ? lastNames() : names;
        for (int lane = 0; lane < 5 && lane < ln.length; lane++) {
            int ly = y + 2 + lane * laneH;
            ctx.fill(x, ly + laneH - 1, x + lw, ly + laneH, 0x40FFFFFF);
            ctx.fill(x + 2, ly + 3, x + 6, ly + laneH - 3, COLORS[lane]);
            float p = showRace ? (racing() ? progress(lane, t) : 1) : 0;
            int rx = x + 10 + (int) ((fx - x - 22) * p);
            roach(ctx, rx, ly + laneH / 2, COLORS[lane], racing() && p < 1 ? now : 0, showRace ? lane == racePick : lane == pick);
            Ui.small(ctx, textRenderer, CasinoMath.RUNNERS[ln[lane]], x + 10, ly + 1, 0.6f, 0xFFE0D0C0, 0);
            if (showRace && !racing()) {
                int place = 0;
                for (int k = 0; k < 5; k++) if (order[k] == lane) place = k;
                Ui.small(ctx, textRenderer, (place + 1) + "-й", fx - 4, ly + laneH / 2f - 3, 0.75f, place == 0 ? Ui.GOLD : 0xFFFFFFFF, 2);
            }
        }
        if (racing() && t < 900) Ui.big(ctx, textRenderer, t < 300 ? "3" : t < 600 ? "2" : "1", x + lw / 2f, y + h / 2f - 20, 2.5f, Ui.GOLD, 1);
        if (showRace && !racing()) {
            String s = "Победил " + CasinoMath.RUNNERS[names.length > 0 ? lastNames()[order[0]] : 0] + "!";
            center(ctx, s, x + lw / 2, y + laneH * 5 + 10, order[0] == racePick ? Ui.GOLD : 0xFFFFCDD2);
        }
        // ставки
        int px = x + lw + 6, pw = w - lw - 6;
        ctx.drawText(textRenderer, "Ставлю на:", px, y + 2, 0xFFFFFFFF, false);
        for (int lane = 0; lane < 5 && lane < names.length; lane++) {
            final int l = lane;
            int by = y + 14 + lane * 19;
            boolean on = pick == lane;
            roundRect(ctx, px, by, pw, 16, on ? 0xFFFFB300 : 0xFF3A2620);
            ctx.fill(px + 2, by + 3, px + 5, by + 13, COLORS[lane]);
            Ui.small(ctx, textRenderer, CasinoMath.RUNNERS[names[lane]], px + 8, by + 4, 0.7f, on ? 0xFF1A0A24 : 0xFFFFFFFF, 0);
            Ui.small(ctx, textRenderer, "×" + odds[lane] / 10 + "." + odds[lane] % 10, px + pw - 3, by + 4, 0.75f, on ? 0xFF1A0A24 : Ui.GOLD, 2);
            if (!busy()) area(px, by, pw, 16, () -> pick = l);
        }
        button(ctx, px, y + h - 22, pw, 18, racing() ? "Бегут!" : "СТАРТ $" + bet(), 0xFF2E7D32, !busy() && names.length == 5 && money() >= bet(), () -> {
            startWait();
            NbtCompound n = new NbtCompound();
            n.putInt("pick", pick);
            send("run", n);
        });
    }

    /** Имена прошлого забега (после финиша карточка уже следующая). */
    private int[] raceNames;

    private int[] lastNames() {
        return raceNames != null ? raceNames : names;
    }

    private void roach(DrawContext ctx, int cx, int cy, int color, long anim, boolean mine) {
        int leg = anim == 0 ? 0 : (int) ((anim / 60) % 2) * 2 - 1;
        for (int i = -1; i <= 1; i++) {
            ctx.fill(cx - 2 + i * 3, cy - 5 - leg * (i == 0 ? -1 : 1), cx - 1 + i * 3, cy - 2, 0xFF2B1A12);
            ctx.fill(cx - 2 + i * 3, cy + 2, cx - 1 + i * 3, cy + 5 + leg * (i == 0 ? -1 : 1), 0xFF2B1A12);
        }
        roundRect(ctx, cx - 7, cy - 3, 12, 7, 0xFF5D3A1A);
        ctx.fill(cx - 6, cy - 2, cx + 3, cy, 0xFF8D5A2B);
        ctx.fill(cx + 4, cy - 2, cx + 7, cy + 3, 0xFF3E2410);
        ctx.fill(cx + 7, cy - 4, cx + 11, cy - 3, 0xFF2B1A12);
        ctx.fill(cx + 7, cy + 4, cx + 11, cy + 5, 0xFF2B1A12);
        ctx.fill(cx - 4, cy - 1, cx, cy + 2, color);
        if (mine) ctx.drawBorder(cx - 9, cy - 6, 22, 13, 0xFFFFFFFF);
    }
}
