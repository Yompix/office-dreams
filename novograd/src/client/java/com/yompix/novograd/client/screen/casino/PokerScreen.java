package com.yompix.novograd.client.screen.casino;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.CasinoMath;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;

/** «Джекпот-покер» (Jacks or Better 8/5): раздача, держи карты, добор. Таблица выплат слева. */
public class PokerScreen extends CasinoScreen {
    private int[] hand = {-1, -1, -1, -1, -1};
    private int hold, res = -1, now = -1, lastBet;
    private boolean drawn = true;
    private long dealtAt;
    private int changed;

    public PokerScreen() {
        super("Джекпот-покер", "poker", 0xFF1E88E5);
    }

    @Override
    public void onServer(NbtCompound n) {
        int[] nh = n.getIntArray("hand");
        if (nh.length != 5) return;
        changed = 0;
        for (int i = 0; i < 5; i++) if (nh[i] != hand[i]) changed |= 1 << i;
        hand = nh;
        drawn = n.getBoolean("drawn");
        lastBet = n.getInt("bet");
        stopWait();
        dealtAt = System.currentTimeMillis();
        if (drawn) {
            res = n.getInt("res");
            result(n.getInt("win"), lastBet);
        } else {
            hold = 0;
            res = -1;
            now = n.getInt("now");
            clearBanner();
        }
        if (mc().player != null) mc().player.playSound(ModContent.PHONE_CLICK, 0.6f, 1.4f);
    }

    /** «Совет»: отметить карты по упрощённой стратегии. */
    public void advise() {
        if (!drawn) hold = CasinoMath.pokerAdvice(hand);
    }

    public void draw() {
        if (drawn) return;
        startWait();
        NbtCompound n = new NbtCompound();
        n.putInt("hold", hold);
        send("draw", n);
    }

    @Override
    protected boolean busy() {
        return !drawn || waiting();
    }

    @Override
    protected void drawGame(DrawContext ctx, int x, int y, int w, int h) {
        // таблица выплат
        int tw = 108;
        ctx.fill(x, y, x + tw, y + h, 0xE0101A3A);
        ctx.drawBorder(x, y, tw, h, 0xFF3F6FD8);
        int ry = y + 4;
        for (int i = 9; i >= 1; i--) {
            boolean hit = res == i || (!drawn && now == i);
            if (hit) ctx.fill(x + 1, ry - 1, x + tw - 1, ry + 9, (System.currentTimeMillis() / 250) % 2 == 0 ? 0xFFFFB300 : 0xFF8A5A00);
            Ui.small(ctx, textRenderer, CasinoMath.POKER_HANDS[i], x + 4, ry + 1, 0.75f, hit ? 0xFF000000 : 0xFFFFE9A0, 0);
            Ui.small(ctx, textRenderer, "×" + CasinoMath.POKER_PAY[i], x + tw - 4, ry + 1, 0.75f, hit ? 0xFF000000 : 0xFFFFFFFF, 2);
            ry += Math.min(12, (h - 8) / 9);
        }
        // стол
        int gx = x + tw + 6, gw = w - tw - 6;
        ctx.fillGradient(gx, y, gx + gw, y + h, 0xFF0D2A66, 0xFF071433);
        ctx.drawBorder(gx, y, gw, h, 0xFFC9A227);
        int cw = Math.min(40, (gw - 24) / 5), ch = (int) (cw * 1.45f);
        int cx0 = gx + (gw - 5 * cw - 4 * 5) / 2, cy = y + 16;
        for (int i = 0; i < 5; i++) {
            int cx = cx0 + i * (cw + 5);
            boolean flipping = (changed & (1 << i)) != 0;
            float k = MathHelper.clamp((System.currentTimeMillis() - dealtAt - i * 90) / 180f, 0, 1);
            boolean held = (hold & (1 << i)) != 0;
            int lift = held && !drawn ? -4 : 0;
            if (flipping && k < 1) {
                int sw = (int) (cw * Math.abs(1 - 2 * k));
                card(ctx, k < 0.5f ? -1 : hand[i], cx + (cw - sw) / 2, cy + lift, Math.max(2, sw), ch, k < 0.5f);
            } else card(ctx, hand[i], cx, cy + lift, cw, ch, hand[i] < 0);
            if (!drawn && hand[i] >= 0) {
                if (held) {
                    Ui.slant(ctx, cx, cy + ch + 3, cw, 10, 3, 0xFFFFB300);
                    Ui.small(ctx, textRenderer, "ДЕРЖУ", cx + cw / 2f, cy + ch + 5, 0.7f, 0xFF121211, 1);
                }
                final int bit = 1 << i;
                area(cx, cy - 4, cw, ch + 18, () -> hold ^= bit);
            }
        }
        String status;
        if (drawn && res >= 0) status = res > 0 ? CasinoMath.POKER_HANDS[res] + "!" : "Ничего — попробуй ещё";
        else if (!drawn) status = "Нажми на карты, которые оставить" + (now > 0 ? " · уже: " + CasinoMath.POKER_HANDS[now] : "");
        else status = "Пара валетов и выше — уже выигрыш";
        center(ctx, status, gx + gw / 2, cy + ch + 18, res > 0 ? Ui.GOLD : 0xFFD0D8FF);
        int by = y + h - 20;
        if (drawn) {
            button(ctx, gx + gw / 2 - 50, by, 100, 16, "РАЗДАТЬ $" + bet(), 0xFF2E7D32, !waiting() && money() >= bet(), () -> {
                lastBet = bet();
                startWait();
                send("deal");
            });
        } else {
            button(ctx, gx + gw / 2 - 104, by, 66, 16, "Совет", 0xFF5B2A86, true, this::advise);
            button(ctx, gx + gw / 2 - 34, by, 66, 16, "Сброс", 0xFF455A64, true, () -> hold = 0);
            button(ctx, gx + gw / 2 + 36, by, 70, 16, "ДОБОР", 0xFFE65100, !waiting(), this::draw);
        }
    }
}
