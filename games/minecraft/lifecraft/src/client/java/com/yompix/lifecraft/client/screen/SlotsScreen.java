package com.yompix.lifecraft.client.screen;

import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.client.LifeCraftClient;
import com.yompix.lifecraft.life.Casino;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Игровой автомат: три барабана крутятся и останавливаются по очереди с пружинкой,
 * лампочки по краю бегут огоньками, ручка опускается, при выигрыше сыплются монеты.
 */
public class SlotsScreen extends LifeScreen {
    private static final int[][] STRIPS = {
            {0, 1, 2, 0, 3, 1, 4, 0, 2, 5, 1, 3}, {1, 0, 3, 2, 0, 5, 1, 2, 4, 0, 3, 1}, {2, 0, 1, 4, 0, 3, 2, 1, 0, 5, 3, 1}};
    private final float[] pos = {0, 4, 8};
    private final float[] startPos = new float[3];
    private final float[] targetPos = new float[3];
    private boolean spinning, waiting;
    private long spinAt, resultAt;
    private int betIndex = 1;
    private int lastWin = -1;
    private boolean jackpot;

    public SlotsScreen() {
        super("Автомат");
    }

    private void spin() {
        if (spinning) return;
        NbtCompound n = new NbtCompound();
        n.putInt("bet", Casino.BETS[betIndex]);
        LifeCraftClient.send("slots", n);
        spinning = waiting = true;
        spinAt = System.currentTimeMillis();
        lastWin = -1;
        for (int i = 0; i < 3; i++) startPos[i] = pos[i];
        if (client != null && client.player != null) client.player.playSound(ModContent.PHONE_CLICK, 1f, 0.5f);
    }

    public void result(NbtCompound n) {
        int[] reels = n.getIntArray("reels");
        resultAt = System.currentTimeMillis();
        float elapsed = (resultAt - spinAt) / 1000f;
        for (int i = 0; i < 3; i++) {
            float cur = startPos[i] + elapsed * speed(i);
            int k = (int) Math.ceil(cur) + 8 + i * 4;
            while (STRIPS[i][Math.floorMod(k, 12)] != reels[i]) k++;
            startPos[i] = cur;
            targetPos[i] = k;
        }
        waiting = false;
        lastWin = n.getInt("win");
        jackpot = n.getBoolean("jackpot");
    }

    private static float speed(int i) {
        return 14 + i * 2;
    }

    private float reelPos(int i, long now) {
        if (!spinning) return pos[i];
        if (waiting) return startPos[i] + (now - spinAt) / 1000f * speed(i);
        float dur = 700 + i * 450;
        float t = MathHelper.clamp((now - resultAt) / dur, 0, 1);
        // ease-out с лёгким перелётом — барабан «щёлкает» на место
        float c1 = 1.4f, c3 = c1 + 1;
        float e = 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
        float p = startPos[i] + (targetPos[i] - startPos[i]) * e;
        if (t >= 1) pos[i] = targetPos[i] % 12;
        return p;
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        long now = System.currentTimeMillis();
        float a = openAnim();
        int w = 220, h = 200;
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 40);
        // корпус автомата
        panel(ctx, x, y, w, h, 0xFFB3122E, 0xFF6E0A1C);
        roundRect(ctx, x + 6, y + 6, w - 12, 24, 0xFF2A0710);
        bigText(ctx, "★ ФОРТУНА ★", x + w / 2, y + 11, 1.4f, (now / 300) % 2 == 0 ? 0xFFFFD54F : 0xFFFFF3B0);
        // бегущие огни по краю
        int bulbs = 26;
        for (int i = 0; i < bulbs; i++) {
            float f = i / (float) bulbs;
            int bx, by;
            if (f < 0.25f) { bx = x + (int) (f * 4 * w); by = y + 2; }
            else if (f < 0.5f) { bx = x + w - 3; by = y + (int) ((f - 0.25f) * 4 * h); }
            else if (f < 0.75f) { bx = x + w - (int) ((f - 0.5f) * 4 * w); by = y + h - 3; }
            else { bx = x + 2; by = y + h - (int) ((f - 0.75f) * 4 * h); }
            boolean on = (i + now / (spinning || lastWin > 0 ? 60 : 200)) % 3 == 0;
            ctx.fill(bx - 1, by - 1, bx + 2, by + 2, on ? 0xFFFFF59D : 0xFF7A5A20);
        }
        // барабаны
        int rw = 46, rh = 72, gap = 8;
        int rx0 = x + (w - 3 * rw - 2 * gap) / 2 - 10, ry = y + 40;
        roundRect(ctx, rx0 - 5, ry - 5, 3 * rw + 2 * gap + 10, rh + 10, 0xFFE0B040);
        for (int i = 0; i < 3; i++) {
            int rx = rx0 + i * (rw + gap);
            ctx.fillGradient(rx, ry, rx + rw, ry + rh, 0xFFDADADA, 0xFFFFFFFF);
            ctx.enableScissor(rx, ry, rx + rw, ry + rh);
            float p = reelPos(i, now);
            int base = (int) Math.floor(p);
            float frac = p - base;
            for (int row = -2; row <= 2; row++) {
                int sym = STRIPS[i][Math.floorMod(base + row, 12)];
                int sy = ry + rh / 2 - 14 + (int) ((row - frac) * 28);
                Identifier tex = LifeCraft.id("textures/gui/slot/" + Casino.SYMBOLS[sym] + ".png");
                ctx.getMatrices().push();
                ctx.getMatrices().translate(rx + rw / 2f - 12, sy, 0);
                ctx.getMatrices().scale(1.5f, 1.5f, 1);
                ctx.drawTexture(tex, 0, 0, 0, 0, 16, 16, 16, 16);
                ctx.getMatrices().pop();
            }
            ctx.fillGradient(rx, ry, rx + rw, ry + 14, 0x99000000, 0x00000000);
            ctx.fillGradient(rx, ry + rh - 14, rx + rw, ry + rh, 0x00000000, 0x99000000);
            ctx.disableScissor();
        }
        ctx.fill(rx0 - 3, ry + rh / 2, rx0 + 3 * rw + 2 * gap + 3, ry + rh / 2 + 1, 0x88FF1744);
        // ручка справа: опускается при запуске
        int lx = x + w - 22, ly = ry + 6;
        float pull = spinning ? MathHelper.clamp(1 - Math.abs((now - spinAt) / 250f - 1), 0, 1) : 0;
        int knobY = ly + (int) (pull * 44);
        ctx.fill(lx + 3, ly + 6, lx + 6, knobY + 4, 0xFFB0B0B0);
        ctx.fill(lx, knobY - 4, lx + 9, knobY + 5, 0xFFE53935);
        ctx.fill(lx + 2, knobY - 3, lx + 4, knobY - 1, 0xFFFF8A80);
        area(lx - 2, ly - 6, 14, 60, this::spin);
        if (waiting && now - spinAt > 3000) {
            // сервер отказал (мало денег или ещё школьник) — просто останавливаем барабаны
            spinning = waiting = false;
            for (int i = 0; i < 3; i++) pos[i] = Math.floorMod((int) startPos[i], 12);
        }
        boolean done = spinning && !waiting && now - resultAt > 700 + 2 * 450 + 50;
        if (done) {
            spinning = false;
            if (client != null && client.player != null && lastWin > 0) client.player.playSound(jackpot ? ModContent.LEVEL_UP : ModContent.CASH, 1f, 1f);
        }
        // выигрыш
        int by = ry + rh + 10;
        if (!spinning && lastWin > 0) {
            float pulse = 1 + 0.12f * (float) Math.sin(now / 90.0);
            bigText(ctx, (jackpot ? "ДЖЕКПОТ! " : "ВЫИГРЫШ ") + "$" + lastWin, x + w / 2, by, 1.3f * pulse, 0xFFFFE082);
            for (int c = 0; c < (jackpot ? 30 : 10); c++) {
                long seed = c * 7919L;
                int cx = x + 10 + (int) (seed % (w - 20));
                int cy = y + (int) (((now - resultAt) / 4 + seed * 3) % h);
                ctx.fill(cx, cy, cx + 4, cy + 4, 0xFFFFC107);
                ctx.fill(cx + 1, cy + 1, cx + 3, cy + 3, 0xFFFFE082);
            }
        } else if (!spinning && lastWin == 0) {
            center(ctx, "Не повезло… ещё разок?", x + w / 2, by + 2, 0xFFFFCDD2);
        }
        // ставка и кнопка
        int cy = y + h - 44;
        ctx.drawText(textRenderer, "Ставка:", x + 10, cy + 3, 0xFFFFFFFF, true);
        for (int i = 0; i < Casino.BETS.length; i++) {
            final int bi = i;
            button(ctx, x + 52 + i * 27, cy, 25, 13, String.valueOf(Casino.BETS[i]), i == betIndex ? 0xFFFFB300 : 0xFF5D0B18, !spinning, () -> betIndex = bi);
        }
        int bet = Casino.BETS[betIndex];
        button(ctx, x + 10, cy + 18, w - 20, 18, spinning ? "Крутится…" : "КРУТИТЬ ($" + bet + ")", 0xFF2E7D32,
                !spinning && LifeCraftClient.data.money >= bet, this::spin);
        String bal = "$" + LifeCraftClient.data.money;
        ctx.drawText(textRenderer, bal, x + w - 10 - textRenderer.getWidth(bal), y + h - 56, 0xFF7CFF8A, true);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 10, y + h - 56, 0);
        ctx.getMatrices().scale(0.7f, 0.7f, 1);
        ctx.drawText(textRenderer, "3× ◆ ×100 · 3× 7 ×40 · 3× ★ ×20 · 2 вишни ×2", 0, 0, 0xFFFFCCBC, false);
        ctx.getMatrices().pop();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 32) {
            spin();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
