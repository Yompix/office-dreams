package com.yompix.novograd.client.screen;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.life.Casino;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/** Европейская рулетка: колесо крутится, шарик бежит навстречу и падает в лунку, которую выбрал сервер. */
public class RouletteScreen extends LifeScreen {
    private static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    private static final String[][] KINDS = {{"red", "Красное ×2"}, {"black", "Чёрное ×2"}, {"even", "Чёт ×2"}, {"odd", "Нечет ×2"},
            {"low", "1–18 ×2"}, {"high", "19–36 ×2"}, {"dozen1", "1–12 ×3"}, {"dozen2", "13–24 ×3"}, {"dozen3", "25–36 ×3"}, {"number", "Число ×36"}};
    private static final long SPIN_MS = 4200;

    private String kind = "red";
    private int number = 7, betIndex = 1;
    private float wheelRot, wheelSpeed = 0.3f;
    private float ballRel, ballStart, ballTarget;
    private boolean spinning, waiting;
    private long spinAt, resultAt, lastFrame = System.currentTimeMillis();
    private int resultNumber = -1, win = -1;
    private final List<Integer> history = new ArrayList<>();

    public RouletteScreen() {
        super("Рулетка");
    }

    private void spin() {
        if (spinning) return;
        NbtCompound n = new NbtCompound();
        n.putInt("bet", Casino.BETS[betIndex]);
        n.putString("kind", kind);
        n.putInt("number", number);
        NovogradClient.send("roulette", n);
        spinning = waiting = true;
        spinAt = System.currentTimeMillis();
        wheelSpeed = 9f;
        win = -1;
    }

    public void result(NbtCompound n) {
        resultNumber = n.getInt("number");
        win = n.getInt("win");
        waiting = false;
        resultAt = System.currentTimeMillis();
        int idx = 0;
        for (int i = 0; i < WHEEL.length; i++) if (WHEEL[i] == resultNumber) idx = i;
        ballStart = ballRel;
        // шарик делает 3–4 оборота против колеса и останавливается в лунке
        float pocket = idx * 360f / WHEEL.length;
        float target = pocket - 360 * 4;
        while (target > ballStart - 720) target -= 360;
        ballTarget = target;
    }

    private static int color(int n) {
        return n == 0 ? 0xFF1B8E3A : Casino.isRed(n) ? 0xFFC62828 : 0xFF1A1A1A;
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (waiting && now - spinAt > 3000) spinning = waiting = false;
        // колесо плавно тормозит
        wheelSpeed = MathHelper.lerp(dt * (spinning ? 0.35f : 1.2f), wheelSpeed, spinning ? 0.6f : 0.25f);
        wheelRot = (wheelRot + wheelSpeed * dt * 60) % 360;
        float ballRadiusK = 1;
        if (spinning && !waiting) {
            float t = MathHelper.clamp((now - resultAt) / (float) SPIN_MS, 0, 1);
            float e = 1 - (float) Math.pow(1 - t, 3);
            ballRel = ballStart + (ballTarget - ballStart) * e;
            ballRadiusK = t < 0.7f ? 1 : 1 - (t - 0.7f) / 0.3f * 0.22f;
            if (t >= 1) {
                spinning = false;
                history.add(0, resultNumber);
                if (history.size() > 10) history.remove(history.size() - 1);
                if (client != null && client.player != null) client.player.playSound(win > 0 ? ModContent.CASH : ModContent.ANSWER_WRONG, 0.8f, 1f);
            }
        } else if (waiting) {
            ballRel -= dt * 600;
        }
        float a = openAnim();
        int w = Math.min(340, width - 16), h = Math.min(210, height - 16);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 40);
        panel(ctx, x, y, w, h, 0xFF0F5A2E, 0xFF0A3D1F);
        ctx.drawBorder(x + 3, y + 3, w - 6, h - 6, 0xFFC9A227);
        // колесо
        int cx = x + 82, cy = y + h / 2, R = Math.min(70, h / 2 - 12);
        drawWheel(ctx, cx, cy, R);
        // шарик
        float ballAbs = wheelRot + ballRel;
        // по бортику, а под конец скатывается в лунку
        float br = R + 1 - (1 - ballRadiusK) / 0.22f * 9;
        if (!spinning) br = resultNumber >= 0 ? R - 8 : R + 1;
        double rad = Math.toRadians(ballAbs - 90);
        int bx = cx + (int) (Math.cos(rad) * br), by = cy + (int) (Math.sin(rad) * br);
        ctx.fill(bx - 2, by - 2, bx + 3, by + 3, 0xFFFFFFFF);
        ctx.fill(bx - 1, by - 1, bx + 1, by + 1, 0xFFDDDDDD);
        // панель ставок
        int px = x + 165, pw = w - 175;
        if (!spinning && resultNumber >= 0) {
            roundRect(ctx, px, y + 8, 34, 22, color(resultNumber));
            center(ctx, String.valueOf(resultNumber), px + 17, y + 15, 0xFFFFFFFF);
            String msg = win > 0 ? "Выигрыш $" + win + "!" : "Мимо…";
            ctx.drawText(textRenderer, msg, px + 40, y + 15, win > 0 ? 0xFFFFE082 : 0xFFFFCDD2, true);
        } else {
            ctx.drawText(textRenderer, spinning ? "Ставки сделаны!" : "Делайте ставки", px, y + 15, 0xFFFFF3B0, true);
        }
        int kx = px, ky = y + 36, kw = (pw - 4) / 2;
        for (int i = 0; i < KINDS.length; i++) {
            String[] k = KINDS[i];
            int c = switch (k[0]) {
                case "red" -> 0xFFC62828;
                case "black" -> 0xFF222222;
                default -> 0xFF2E6B45;
            };
            if (k[0].equals(kind)) c = 0xFFFFB300;
            button(ctx, kx + (i % 2) * (kw + 4), ky + (i / 2) * 17, kw, 14, k[1], c, !spinning, () -> kind = k[0]);
        }
        int ny = ky + 5 * 17 + 2;
        if (kind.equals("number")) {
            button(ctx, px, ny, 16, 14, "−", 0xFF2E6B45, !spinning, () -> number = (number + 36) % 37);
            roundRect(ctx, px + 20, ny, 30, 14, color(number));
            center(ctx, String.valueOf(number), px + 35, ny + 3, 0xFFFFFFFF);
            button(ctx, px + 54, ny, 16, 14, "+", 0xFF2E6B45, !spinning, () -> number = (number + 1) % 37);
        }
        int sy = ny + 18;
        for (int i = 0; i < Casino.BETS.length; i++) {
            final int bi = i;
            button(ctx, px + i * (pw / 6), sy, pw / 6 - 2, 13, String.valueOf(Casino.BETS[i]), i == betIndex ? 0xFFFFB300 : 0xFF145C33, !spinning, () -> betIndex = bi);
        }
        int bet = Casino.BETS[betIndex];
        button(ctx, px, sy + 17, pw, 16, spinning ? "Крутится…" : "КРУТИТЬ ($" + bet + ")", 0xFFB71C1C, !spinning && NovogradClient.data.money >= bet, this::spin);
        // последние числа
        int hx = px;
        for (int n : history) {
            if (hx > x + w - 14) break;
            roundRect(ctx, hx, y + h - 16, 12, 10, color(n));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(hx + 6, y + h - 14, 0);
            ctx.getMatrices().scale(0.6f, 0.6f, 1);
            ctx.drawText(textRenderer, String.valueOf(n), -textRenderer.getWidth(String.valueOf(n)) / 2, 0, 0xFFFFFFFF, false);
            ctx.getMatrices().pop();
            hx += 14;
        }
        String bal = "$" + NovogradClient.data.money;
        ctx.drawText(textRenderer, bal, x + w - 8 - textRenderer.getWidth(bal), y + 8, 0xFF7CFF8A, true);
    }

    private void drawWheel(DrawContext ctx, int cx, int cy, int R) {
        // обод и тень
        for (int r = R + 6; r >= R + 2; r--) ring(ctx, cx, cy, r, r == R + 6 ? 0xFF3E2410 : 0xFF6B3E1C);
        int n = WHEEL.length;
        for (int i = 0; i < n; i++) {
            float ang = wheelRot + i * 360f / n;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy, 0);
            ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(ang));
            int segW = (int) Math.ceil(Math.PI * 2 * R / n / 2) + 1;
            ctx.fill(-segW, -R, segW, -R + 16, color(WHEEL[i]));
            ctx.fill(-segW, -R + 16, segW, -R + 18, 0xFFC9A227);
            ctx.getMatrices().translate(0, -R + 3, 0);
            ctx.getMatrices().scale(0.55f, 0.55f, 1);
            String s = String.valueOf(WHEEL[i]);
            ctx.drawText(textRenderer, s, -textRenderer.getWidth(s) / 2, 0, 0xFFFFFFFF, false);
            ctx.getMatrices().pop();
        }
        // центр колеса с крестовиной
        for (int r = R - 18; r > 0; r -= 2) ring(ctx, cx, cy, r, r > R - 24 ? 0xFF8B5A2B : 0xFF5C3A1A);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(wheelRot));
        ctx.fill(-2, -R + 22, 2, R - 22, 0xFFD4AF37);
        ctx.fill(-R + 22, -2, R - 22, 2, 0xFFD4AF37);
        ctx.fill(-5, -5, 5, 5, 0xFFF0D060);
        ctx.getMatrices().pop();
    }

    private static void ring(DrawContext ctx, int cx, int cy, int r, int color) {
        int steps = Math.max(24, r * 3);
        for (int i = 0; i < steps; i++) {
            double a = i * Math.PI * 2 / steps;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }
}
