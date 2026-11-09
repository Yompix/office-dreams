package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Игровой автомат «Неон-Погоня» в барах: неоновая трасса в три полосы, A/D (или стрелки) — перестроиться,
 * уворачивайся от машин, собирай монеты. Скорость растёт. Рекорд оплачивает сервер (gta/Arcade).
 */
public class ArcadeScreen extends LifeScreen {
    private record Thing(int lane, float y, boolean coin, int color) {}

    private final int record;
    private final List<Thing> things = new ArrayList<>();
    private final Random rnd = new Random();
    private int lane = 1, score, coins;
    private float scoreF;
    private float laneX = 1, speed = 60, road, spawnIn = 0.6f, flash;
    private long last = System.nanoTime();
    private boolean over, sent, started;
    private float startIn = 2.2f;

    public ArcadeScreen(NbtCompound n) {
        super("Неон-Погоня");
        record = n.getInt("record");
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        long t = System.nanoTime();
        float dt = Math.min(0.05f, (t - last) / 1e9f);
        last = t;
        update(dt);
        int cw = Math.min(220, width - 40), ch = Math.min(300, height - 40);
        int x0 = width / 2 - cw / 2, y0 = height / 2 - ch / 2 + 6;
        // корпус автомата
        int glow = Ui.alpha(Ui.PINK, 0.25f + 0.1f * MathHelper.sin(seconds() * 4));
        ctx.fill(x0 - 14, y0 - 30, x0 + cw + 14, y0 + ch + 14, 0xFF0F0F0E);
        ctx.fill(x0 - 16, y0 - 32, x0 + cw + 16, y0 - 30, Ui.PINK);
        ctx.fill(x0 - 16, y0 + ch + 14, x0 + cw + 16, y0 + ch + 16, Ui.CYAN);
        ctx.fill(x0 - 6, y0 - 6, x0 + cw + 6, y0 + ch + 6, glow);
        Ui.big(ctx, textRenderer, "НЕОН-ПОГОНЯ", width / 2f, y0 - 25, 1.5f, Ui.GOLD, 1);
        ctx.enableScissor(x0, y0, x0 + cw, y0 + ch);
        ctx.fillGradient(x0, y0, x0 + cw, y0 + ch, 0xFF080808, 0xFF121111);
        // сетка-горизонт и бегущие полосы
        int lw = cw / 3;
        for (int i = 0; i <= 3; i++) ctx.fill(x0 + i * lw - 1, y0, x0 + i * lw + 1, y0 + ch, i == 0 || i == 3 ? Ui.CYAN : 0x603FB8B0);
        for (int k = -1; k < ch / 30 + 2; k++) {
            int yy = y0 + (int) (k * 30 + road % 30);
            for (int i = 1; i < 3; i++) ctx.fill(x0 + i * lw - 1, yy, x0 + i * lw + 1, yy + 14, 0xC0D42A3A);
        }
        // машины и монеты
        for (Thing th : things) {
            int cx = x0 + th.lane * lw + lw / 2, cy = y0 + (int) th.y;
            if (th.coin) {
                int r = 5 + (int) (MathHelper.sin(seconds() * 8 + th.y) * 1.5f);
                ctx.fill(cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2, 0x40FFC428);
                ctx.fill(cx - r, cy - r, cx + r, cy + r, Ui.GOLD);
                ctx.drawText(textRenderer, "$", cx - 2, cy - 4, 0xFF5A3A00, false);
            } else car(ctx, cx, cy, th.color, false);
        }
        // игрок
        int px = x0 + (int) (laneX * lw) + lw / 2, py = y0 + ch - 34;
        car(ctx, px, py, flash > 0 && (int) (flash * 20) % 2 == 0 ? 0xFFFFFFFF : Ui.CYAN, true);
        ctx.disableScissor();
        // счёт
        Ui.big(ctx, textRenderer, String.valueOf(score), x0 + 6, y0 + 6, 1.6f, Ui.WHITE, 0);
        Ui.small(ctx, textRenderer, "рекорд " + Math.max(record, sent ? score : record), x0 + cw - 6, y0 + 8, 0.9f, Ui.DIM, 2);
        Ui.small(ctx, textRenderer, "монеты " + coins, x0 + cw - 6, y0 + 19, 0.9f, Ui.GOLD, 2);
        if (!started) {
            Ui.big(ctx, textRenderer, startIn > 0.4f ? String.valueOf((int) Math.ceil(startIn - 0.4f)) : "ПОЕХАЛИ!", width / 2f, height / 2f - 10, 2.4f, Ui.GOLD, 1);
            Ui.small(ctx, textRenderer, "A/D или ←/→ — перестроиться", width / 2f, height / 2f + 22, 1f, Ui.WHITE, 1);
        }
        if (over) {
            ctx.fill(x0, y0 + ch / 2 - 46, x0 + cw, y0 + ch / 2 + 54, 0xD0000000);
            Ui.big(ctx, textRenderer, "АВАРИЯ!", width / 2f, y0 + ch / 2f - 40, 2f, Ui.RED, 1);
            Ui.big(ctx, textRenderer, score + " очков", width / 2f, y0 + ch / 2f - 14, 1.4f, score > record ? Ui.MONEY : Ui.WHITE, 1);
            if (score > record) Ui.small(ctx, textRenderer, "НОВЫЙ РЕКОРД!", width / 2f, y0 + ch / 2f + 4, 1f, Ui.GOLD, 1);
            button(ctx, width / 2 - 92, y0 + ch / 2 + 20, 88, 18, "Ещё — $5", Ui.PINK & 0xFFFFFF, true, () -> {
                finish();
                NovogradClient.send("arcade_again");
                close();
            });
            button(ctx, width / 2 + 4, y0 + ch / 2 + 20, 88, 18, "Выйти", 0x3A3F55, true, this::close);
        }
    }

    private void car(DrawContext ctx, int cx, int cy, int color, boolean me) {
        ctx.fill(cx - 12, cy - 18, cx + 12, cy + 18, Ui.alpha(color, 0.18f));
        ctx.fill(cx - 9, cy - 15, cx + 9, cy + 15, color);
        ctx.fill(cx - 7, cy - 9, cx + 7, cy - 3, 0xFF10101A); // лобовое
        ctx.fill(cx - 7, cy + 6, cx + 7, cy + 10, 0xFF10101A);
        int lamp = me ? 0xFFFFFFFF : 0xFFFF3030;
        ctx.fill(cx - 8, me ? cy - 15 : cy + 13, cx - 4, me ? cy - 13 : cy + 15, lamp);
        ctx.fill(cx + 4, me ? cy - 15 : cy + 13, cx + 8, me ? cy - 13 : cy + 15, lamp);
    }

    private void update(float dt) {
        if (over) return;
        if (!started) {
            startIn -= dt;
            if (startIn <= 0) started = true;
            return;
        }
        int ch = Math.min(300, height - 40);
        laneX += (lane - laneX) * Math.min(1, dt * 14);
        speed += dt * 5.5f;
        road += speed * dt * 2.2f;
        scoreF += speed * dt * 0.15f;
        score = (int) scoreF + coins * 50;
        flash = Math.max(0, flash - dt);
        spawnIn -= dt;
        if (spawnIn <= 0) {
            spawnIn = Math.max(0.28f, 1.05f - speed / 260f) * (0.7f + rnd.nextFloat() * 0.6f);
            int l = rnd.nextInt(3);
            boolean coin = rnd.nextInt(4) == 0;
            int[] cols = {0xFFD42A3A, 0xFFFFC428, 0xFF8A3A4A, 0xFF62E57E, 0xFFFF7A1A};
            things.add(new Thing(l, -30, coin, cols[rnd.nextInt(cols.length)]));
            // иногда сразу две машины — одна полоса всегда свободна
            if (!coin && speed > 90 && rnd.nextInt(3) == 0) things.add(new Thing((l + 1 + rnd.nextInt(2)) % 3, -30, false, cols[rnd.nextInt(cols.length)]));
        }
        int py = ch - 34;
        for (int i = things.size() - 1; i >= 0; i--) {
            Thing th = things.get(i);
            float ny = th.y + speed * dt * (th.coin ? 2.2f : 1.6f);
            if (ny > ch + 40) {
                things.remove(i);
                continue;
            }
            things.set(i, new Thing(th.lane, ny, th.coin, th.color));
            boolean sameLane = Math.abs(th.lane - laneX) < 0.5f;
            if (sameLane && Math.abs(ny - py) < (th.coin ? 18 : 28)) {
                if (th.coin) {
                    coins++;
                    things.remove(i);
                    if (client != null && client.player != null) client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
                } else {
                    over = true;
                    flash = 1;
                    if (client != null && client.player != null) client.player.playSound(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.4f, 1.3f);
                    finish();
                }
            }
        }
    }

    private void finish() {
        if (sent) return;
        sent = true;
        NbtCompound n = new NbtCompound();
        n.putString("game", "arcade");
        n.putInt("score", score);
        NovogradClient.send("arcade_done", n);
    }

    /** Для автотеста: нажать влево/вправо. */
    public void steer(int d) {
        lane = MathHelper.clamp(lane + d, 0, 2);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!over && (key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_LEFT)) {
            steer(-1);
            return true;
        }
        if (!over && (key == GLFW.GLFW_KEY_D || key == GLFW.GLFW_KEY_RIGHT)) {
            steer(1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void close() {
        if (started) finish();
        super.close();
    }
}
