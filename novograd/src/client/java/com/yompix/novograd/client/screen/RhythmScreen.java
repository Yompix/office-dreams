package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ритм-игра: сверху падают стрелки, жми ту же стрелку (или WASD), когда она в рамке.
 * Беговая дорожка — быстрый темп, штанга — медленные тяжёлые «повторы», пульт диджея — под музыку.
 */
public class RhythmScreen extends LifeScreen {
    private static final String[] ARROWS = {"←", "↓", "↑", "→"};
    private static final int[] COLORS = {0xFFFF5C8A, 0xFF4FC3F7, 0xFF7CFF8A, 0xFFFFD54F};
    private static final float FALL = 1.6f; // сек от верха до рамки

    private record Note(int lane, float at) {}

    private final String mode;
    private final List<Note> notes = new ArrayList<>();
    private final float length;
    private int hit, miss, combo, best;
    private final List<Note> done = new ArrayList<>();
    private String flash = "";
    private float flashAt = -1;
    private boolean sent;
    private SoundInstance music;

    public RhythmScreen(String mode) {
        super(switch (mode) { case "dj" -> "Пульт диджея"; case "lift" -> "Штанга"; default -> "Беговая дорожка"; });
        this.mode = mode;
        Random r = new Random();
        float step = switch (mode) { case "dj" -> 0.42f; case "lift" -> 0.9f; default -> 0.36f; };
        length = mode.equals("dj") ? 24 : 15;
        for (float t = 1.5f; t < length - 0.5f; t += step * (r.nextInt(4) == 0 ? 2 : 1)) {
            notes.add(new Note(r.nextInt(4), t));
            if (mode.equals("dj") && r.nextInt(6) == 0) notes.add(new Note(r.nextInt(4), t));
        }
    }

    @Override
    protected void init() {
        super.init();
        if (music == null && mode.equals("dj")) {
            music = PositionedSoundInstance.master(SoundEvents.MUSIC_DISC_CHIRP.value(), 1f, 0.6f);
            MinecraftClient.getInstance().getSoundManager().play(music);
        }
    }

    private float now() {
        return seconds();
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        var tr = textRenderer;
        float t = now();
        int lanes = 4, laneW = 46, w = lanes * laneW, x0 = width / 2 - w / 2, top = 40, judge = height - 70;
        ctx.fill(x0 - 8, top - 26, x0 + w + 8, judge + 40, 0xC0101018);
        ctx.drawCenteredTextWithShadow(tr, getTitle(), width / 2, top - 20, 0xFFFFFF);
        for (int i = 0; i < lanes; i++) {
            int lx = x0 + i * laneW;
            ctx.fill(lx + 2, top, lx + laneW - 2, judge + 20, 0x30FFFFFF);
            ctx.fill(lx + 2, judge - 2, lx + laneW - 2, judge + 18, 0x50000000 | (COLORS[i] & 0xFFFFFF));
            ctx.drawCenteredTextWithShadow(tr, ARROWS[i], lx + laneW / 2, judge + 4, 0xFFFFFF);
        }
        for (Note n : notes) {
            if (done.contains(n)) continue;
            float k = 1 - (n.at - t) / FALL;
            if (k < 0) continue;
            if (k > 1.25f) { // проехала мимо рамки
                done.add(n);
                miss++;
                combo = 0;
                flash("Мимо!");
                continue;
            }
            int y = (int) (top + k * (judge - top));
            int lx = x0 + n.lane * laneW;
            ctx.fill(lx + 6, y, lx + laneW - 6, y + 16, COLORS[n.lane]);
            ctx.drawCenteredTextWithShadow(tr, ARROWS[n.lane], lx + laneW / 2, y + 4, 0x202020);
        }
        int total = Math.max(1, notes.size());
        int score = Math.round(100f * hit / total);
        ctx.drawTextWithShadow(tr, "Попаданий: " + hit + "/" + total, x0 + w + 16, top, 0xFFFFFF);
        ctx.drawTextWithShadow(tr, "Комбо: " + combo + " (лучшее " + best + ")", x0 + w + 16, top + 12, 0xFFD54F);
        String goal = switch (mode) { case "dj" -> "Танцпол: " + "♪".repeat(Math.min(10, combo / 3 + 1)); case "lift" -> "Подходы: " + hit; default -> "Дистанция: " + hit * 50 + " м"; };
        ctx.drawTextWithShadow(tr, goal, x0 + w + 16, top + 24, 0x7CFF8A);
        ctx.drawTextWithShadow(tr, "← ↓ ↑ → или A S W D", x0 - 130, top, 0xAAAAAA);
        int left = (int) Math.max(0, length - t);
        ctx.drawTextWithShadow(tr, "Осталось: " + left + " с", x0 - 130, top + 12, 0xAAAAAA);
        if (flashAt >= 0 && t - flashAt < 0.4f) ctx.drawCenteredTextWithShadow(tr, flash, width / 2, judge - 24, flash.equals("Мимо!") ? 0xFF5555 : 0x7CFF8A);
        if (t >= length + FALL * 0.3f) finish(score);
    }

    private void flash(String s) {
        flash = s;
        flashAt = now();
    }

    private void press(int lane) {
        float t = now();
        Note bestNote = null;
        float bestD = 0.22f;
        for (Note n : notes) {
            if (done.contains(n) || n.lane != lane) continue;
            float d = Math.abs(n.at - t);
            if (d < bestD) { bestD = d; bestNote = n; }
        }
        if (bestNote != null) {
            done.add(bestNote);
            hit++;
            combo++;
            best = Math.max(best, combo);
            flash(bestD < 0.08f ? "Идеально!" : "Хорошо");
            if (client != null && client.player != null) client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.4f, 1.2f + lane * 0.1f);
        } else {
            combo = 0;
            flash("Мимо!");
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        int lane = switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> 0;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> 1;
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> 2;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> 3;
            default -> -1;
        };
        if (lane >= 0) {
            press(lane);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void finish(int score) {
        if (sent) return;
        sent = true;
        NbtCompound n = new NbtCompound();
        n.putString("mode", mode);
        n.putInt("score", score);
        NovogradClient.send("rhythm_done", n);
        close();
    }

    @Override
    public void removed() {
        if (music != null) MinecraftClient.getInstance().getSoundManager().stop(music);
        // вышел раньше — засчитываем то, что успел
        if (!sent) {
            sent = true;
            NbtCompound n = new NbtCompound();
            n.putString("mode", mode);
            n.putInt("score", Math.round(100f * hit / Math.max(1, notes.size())));
            NovogradClient.send("rhythm_done", n);
        }
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
