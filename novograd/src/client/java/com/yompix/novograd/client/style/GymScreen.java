package com.yompix.novograd.client.style;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.StyleCatalog;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Зал «Железо» в стиле SA, без веса и «жира»: два упражнения.
 * <ul>
 *   <li><b>Штанга</b> (сила): метка бегает по шкале — жми ПРОБЕЛ, когда она в зелёной зоне. Каждый повтор — зона уже,
 *   метка быстрее; три промаха — подход окончен.</li>
 *   <li><b>Беговая дорожка</b> (выносливость): чередуй ← и → (или A и D), чтобы держать темп в зелёной полосе 20 секунд.</li>
 * </ul>
 * Прирост за игровой день ограничен; сервер проверяет время подхода и лимит.
 */
public class GymScreen extends LifeScreen {
    private enum State { MENU, WAIT, BENCH, RUN, RESULT }

    private State state = State.MENU;
    private String ex = "";
    private String deny = "";
    private long denyAt, startAt, resultAt, lastHitAt, lastMissAt;
    // штанга
    private int reps, misses;
    private float phase, lift;
    private boolean finished;
    // дорожка
    private float speed, inBand, belt;
    private int lastKey;
    private long lastFrame;
    private int gain, value;
    private static final float RUN_TIME = 20f;

    public GymScreen() {
        super(StyleCatalog.GYM_NAME);
    }

    void server(String type, NbtCompound n) {
        if (type.equals("gym_start")) {
            deny = n.getString("deny");
            if (!deny.isEmpty()) {
                denyAt = System.currentTimeMillis();
                state = State.MENU;
                return;
            }
            startAt = System.currentTimeMillis();
            lastFrame = startAt;
            reps = misses = 0;
            phase = 0;
            lift = 0;
            speed = inBand = belt = 0;
            finished = false;
            state = ex.equals("bench") ? State.BENCH : State.RUN;
        } else {
            gain = n.getInt("gain");
            value = n.getInt("value");
            resultAt = System.currentTimeMillis();
            sentAt = 0;
            state = State.RESULT;
        }
    }

    private void start(String which) {
        ex = which;
        state = State.WAIT;
        NbtCompound n = new NbtCompound();
        n.putString("ex", which);
        StyleClient.send("gym_start", n);
    }

    private long sentAt;

    private void finish(int score) {
        if (finished) return;
        finished = true;
        sentAt = System.currentTimeMillis();
        NbtCompound n = new NbtCompound();
        n.putString("ex", ex);
        n.putInt("score", score);
        StyleClient.send("gym", n);
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int top = 10 + (int) ((1 - a) * -20);
        title(ctx, StyleCatalog.GYM_NAME, 12, top, Ui.GOLD);
        Ui.small(ctx, textRenderer, "тренер Борис: «Без веса и диет — только сила и дыхалка»", 14, top + 28, 0.8f, 0xFFD9D7D0, 0);
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        switch (state) {
            case MENU, WAIT -> menu(ctx, top + 42);
            case BENCH -> bench(ctx, top + 42, dt);
            case RUN -> run(ctx, top + 42, dt);
            case RESULT -> result(ctx, top + 42);
        }
        ctx.fill(0, height - 18, width, height, 0xE0040404);
        ctx.fill(0, height - 19, width, height - 18, Ui.alpha(Ui.GOLD, 0.8f));
        String hint = switch (state) {
            case BENCH -> "ПРОБЕЛ / клик — жать, когда метка в зелёной зоне";
            case RUN -> "← → (или A D) по очереди — держи темп в зелёной полосе";
            default -> "Esc — выйти";
        };
        Ui.small(ctx, textRenderer, hint, 10, height - 13, 0.85f, 0xC0FFFFFF, 0);
    }

    private void menu(DrawContext ctx, int y) {
        LifeData d = NovogradClient.data;
        boolean today = d.gymDay == (mc().world == null ? -1 : mc().world.getTimeOfDay() / 24000L + 1);
        int cw = Math.min(200, (width - 40) / 2), ch = Math.min(150, height - y - 30);
        int x1 = width / 2 - cw - 6, x2 = width / 2 + 6;
        card(ctx, x1, y, cw, ch, "ШТАНГА", "Сила", d.strength, today ? d.gymStrToday : 0, Ui.RED,
                "Жми, когда метка в зелёной зоне. Чем больше повторов — тем больше силы.", "bench");
        card(ctx, x2, y, cw, ch, "БЕГОВАЯ ДОРОЖКА", "Выносливость", d.stamina, today ? d.gymStaToday : 0, Ui.CYAN,
                "Чередуй ← и →, держи темп в зелёной полосе 20 секунд.", "run");
        if (!deny.isEmpty() && System.currentTimeMillis() - denyAt < 4000) {
            int w = textRenderer.getWidth(deny) + 20;
            Ui.slant(ctx, width / 2 - w / 2, y + ch + 4, w, 14, 4, 0xE0301018);
            center(ctx, deny, width / 2, y + ch + 7, Ui.GOLD);
        }
    }

    private void card(DrawContext ctx, int x, int y, int w, int h, String title, String stat, int value, int today, int color, String desc, String id) {
        Ui.panel(ctx, x, y, w, h, color);
        Ui.big(ctx, textRenderer, title, x + 8, y + 8, 1.3f, Ui.WHITE, 0);
        // значок упражнения
        int ix = x + w - 34, iy = y + 8;
        if (id.equals("bench")) {
            ctx.fill(ix, iy + 8, ix + 26, iy + 10, 0xFFC0C0C0);
            ctx.fill(ix + 1, iy + 3, ix + 5, iy + 15, color);
            ctx.fill(ix + 21, iy + 3, ix + 25, iy + 15, color);
        } else {
            ctx.fill(ix, iy + 13, ix + 26, iy + 16, 0xFF404048);
            for (int i = 0; i < 4; i++) ctx.fill(ix + 2 + i * 6, iy + 14, ix + 4 + i * 6, iy + 15, color);
            ctx.fill(ix + 22, iy + 2, ix + 24, iy + 13, 0xFF808088);
        }
        ctx.drawText(textRenderer, stat.toUpperCase(), x + 8, y + 30, color, true);
        String v = value + " / " + StyleCatalog.STAT_MAX;
        ctx.drawText(textRenderer, v, x + w - 8 - textRenderer.getWidth(v), y + 30, Ui.WHITE, true);
        Ui.bar(ctx, x + 8, y + 41, w - 16, 5, value / (float) StyleCatalog.STAT_MAX, color);
        Ui.small(ctx, textRenderer, "сегодня +" + today + " из " + StyleCatalog.GYM_DAILY, x + 8, y + 50, 0.75f, today >= StyleCatalog.GYM_DAILY ? Ui.GOLD : 0xFFB9B7B1, 0);
        wrap(ctx, desc, x + 8, y + 62, w - 16, 0xFFDDDBD4);
        boolean waiting = state == State.WAIT && ex.equals(id);
        button(ctx, x + 8, y + h - 26, w - 16, 18, waiting ? "…" : "НАЧАТЬ ПОДХОД", color, state == State.MENU, () -> start(id));
    }

    // ------------------------------------------------------------------ штанга
    private void bench(DrawContext ctx, int y, float dt) {
        float t = (System.currentTimeMillis() - startAt) / 1000f;
        float spd = 1.6f + reps * 0.22f;
        if (!finished) phase += dt * spd;
        float pos = 0.5f + 0.5f * MathHelper.sin(phase * 2.2f);        // 0..1 по шкале
        float half = Math.max(0.05f, 0.17f - reps * 0.009f);
        float center = 0.5f + 0.18f * MathHelper.sin(reps * 1.7f);
        lift = Ui.approach(lift, 0, 0.08f);
        int pw = Math.min(460, width - 40), ph = Math.min(220, height - y - 26), px = width / 2 - pw / 2;
        ctx.fill(px, y, px + pw, y + ph, 0xF0070707);
        Ui.panel(ctx, px, y, pw, ph, Ui.RED);
        // счёт
        Ui.big(ctx, textRenderer, "ПОВТОРЫ  " + reps + " / 15", px + 12, y + 10, 1.5f, Ui.WHITE, 0);
        StringBuilder sb = new StringBuilder("ПРОМАХИ  ");
        for (int i = 0; i < 3; i++) sb.append(i < misses ? "✖ " : "○ ");
        Ui.big(ctx, textRenderer, sb.toString(), px + 12, y + 30, 1.1f, misses > 0 ? Ui.RED : 0xFFB9B7B1, 0);
        Ui.small(ctx, textRenderer, "время " + (int) t + " с", px + 12, y + 46, 0.8f, 0xFFB9B7B1, 0);
        // шкала справа
        int gh = ph - 30, gx = px + pw - 46, gy = y + 14;
        ctx.fill(gx - 2, gy - 2, gx + 22, gy + gh + 2, 0xFF000000);
        ctx.fill(gx, gy, gx + 20, gy + gh, 0xFF181817);
        int zt = gy + (int) ((1 - (center + half)) * gh), zb = gy + (int) ((1 - (center - half)) * gh);
        ctx.fill(gx, zt, gx + 20, zb, 0xE03CD070);
        ctx.fill(gx, zt, gx + 20, zt + 1, 0xFFB8FFC8);
        int my = gy + (int) ((1 - pos) * gh);
        ctx.fill(gx - 7, my - 1, gx + 27, my + 2, Ui.WHITE);
        ctx.fill(gx - 9, my - 3, gx - 5, my + 4, Ui.GOLD);
        Ui.small(ctx, textRenderer, "ЖМИ!", gx + 10, gy + gh + 4, 0.8f, Ui.GOLD, 1);
        // скамья, атлет и штанга
        int cx = px + pw / 2 - 30, floor = y + ph - 22;
        ctx.fill(cx - 70, floor, cx + 70, floor + 3, 0xFF393836);                 // пол
        ctx.fill(cx - 50, floor - 22, cx + 40, floor - 16, 0xFF272726);           // скамья
        ctx.fill(cx - 44, floor - 16, cx - 38, floor, 0xFF494846);
        ctx.fill(cx + 28, floor - 16, cx + 34, floor, 0xFF494846);
        ctx.fill(cx - 46, floor - 30, cx + 16, floor - 22, Ui.PINK);              // торс лёжа
        ctx.fill(cx + 16, floor - 32, cx + 28, floor - 22, 0xFFE0B48F);           // голова
        ctx.fill(cx - 66, floor - 26, cx - 46, floor - 20, 0xFF2F4A7A);           // ноги
        ctx.fill(cx - 70, floor - 20, cx - 64, floor, 0xFF2F4A7A);
        int barY = floor - 52 - (int) (lift * 26);
        ctx.fill(cx - 22, barY + 2, cx - 18, floor - 28, 0xFFE0B48F);             // руки
        ctx.fill(cx + 2, barY + 2, cx + 6, floor - 28, 0xFFE0B48F);
        ctx.fill(cx - 64, barY, cx + 52, barY + 3, 0xFFD0D0D8);                   // гриф
        for (int s : new int[]{-1, 1}) {
            int bx = s < 0 ? cx - 62 : cx + 42;
            ctx.fill(bx, barY - 13, bx + 8, barY + 16, 0xFF202028);
            ctx.fill(bx + (s < 0 ? -7 : 8), barY - 10, bx + (s < 0 ? 0 : 15), barY + 13, Ui.RED);
        }
        float hitFlash = (System.currentTimeMillis() - lastHitAt) / 300f, missFlash = (System.currentTimeMillis() - lastMissAt) / 400f;
        if (hitFlash < 1) Ui.big(ctx, textRenderer, "+1", cx, barY - 30 - hitFlash * 10, 1.8f, Ui.alpha(Ui.MONEY, 1 - hitFlash), 1);
        if (missFlash < 1) Ui.big(ctx, textRenderer, "МИМО", cx, barY - 30, 1.5f, Ui.alpha(Ui.RED, 1 - missFlash), 1);
        if (finished) {
            center(ctx, "Отдышись…", px + pw / 2, y + ph + 4, Ui.GOLD);
            if (t >= 6.5f && state == State.BENCH && !sentBench) {
                sentBench = true;
                sendNow(reps);
            }
        }
        waitCheck();
    }

    private boolean sentBench;

    private void sendNow(int score) {
        finished = false;
        finish(score);
    }

    private void benchPress() {
        if (finished) return;
        float pos = 0.5f + 0.5f * MathHelper.sin(phase * 2.2f);
        float half = Math.max(0.05f, 0.17f - reps * 0.009f);
        float center = 0.5f + 0.18f * MathHelper.sin(reps * 1.7f);
        if (Math.abs(pos - center) <= half) {
            reps++;
            lift = 1;
            lastHitAt = System.currentTimeMillis();
            if (client != null && client.player != null) client.player.playSound(SoundEvents.BLOCK_ANVIL_PLACE, 0.25f, 1.6f + reps * 0.03f);
        } else {
            misses++;
            lastMissAt = System.currentTimeMillis();
            if (client != null && client.player != null) client.player.playSound(SoundEvents.ENTITY_PLAYER_HURT, 0.4f, 0.8f);
        }
        if (misses >= 3 || reps >= 15) {
            finished = true;
            sentBench = false;
            if ((System.currentTimeMillis() - startAt) / 1000f >= 6.5f) {
                sentBench = true;
                sendNow(reps);
            }
        }
    }

    // ------------------------------------------------------------------ беговая дорожка
    private void run(DrawContext ctx, int y, float dt) {
        float t = (System.currentTimeMillis() - startAt) / 1000f;
        float bandC = 0.5f + 0.2f * MathHelper.sin(t * 0.45f), bandH = 0.11f;
        if (!finished) {
            speed = Math.max(0, speed - dt * 0.32f);
            if (Math.abs(speed - bandC) <= bandH) inBand += dt;
            belt += speed * dt * 120;
            if (t >= RUN_TIME) {
                finish(Math.round(100 * inBand / RUN_TIME));
            }
        }
        int pw = Math.min(460, width - 40), ph = Math.min(220, height - y - 26), px = width / 2 - pw / 2;
        ctx.fill(px, y, px + pw, y + ph, 0xF0070707);
        Ui.panel(ctx, px, y, pw, ph, Ui.CYAN);
        Ui.big(ctx, textRenderer, String.format("%.0f", Math.max(0, RUN_TIME - t)) + " С", px + 12, y + 10, 1.5f, Ui.WHITE, 0);
        int pct = Math.round(100 * inBand / Math.max(0.1f, Math.min(t, RUN_TIME)));
        Ui.big(ctx, textRenderer, "В ТЕМПЕ " + pct + "%", px + pw - 12, y + 10, 1.3f, pct >= 60 ? Ui.MONEY : Ui.GOLD, 2);
        int w = pw - 24, x = px + 12, gy = y + 42;
        // шкала темпа
        Ui.small(ctx, textRenderer, "ТЕМП", x, gy - 9, 0.8f, 0xFFB9B7B1, 0);
        ctx.fill(x - 2, gy - 2, x + w + 2, gy + 16, 0xFF000000);
        ctx.fill(x, gy, x + w, gy + 14, 0xFF181817);
        ctx.fill(x + (int) ((bandC - bandH) * w), gy, x + (int) ((bandC + bandH) * w), gy + 14, 0xE03CD070);
        int sx = x + (int) (MathHelper.clamp(speed, 0, 1) * w);
        ctx.fill(sx - 1, gy - 5, sx + 2, gy + 19, Ui.WHITE);
        // дорожка и бегун
        int lh = 14, ly = y + ph - 34;
        int lx0 = px + pw / 2 - 90, lw = 180;
        ctx.fill(lx0 + lw - 10, ly - 70, lx0 + lw - 4, ly, 0xFF606070);              // стойка с пультом
        ctx.fill(lx0 + lw - 24, ly - 74, lx0 + lw + 2, ly - 66, 0xFF404050);
        ctx.fill(lx0 + lw - 20, ly - 72, lx0 + lw - 6, ly - 68, Ui.alpha(Ui.CYAN, 0.8f));
        ctx.fill(lx0, ly, lx0 + lw, ly + lh, 0xFF202028);
        for (int i = -1; i < lw / 14 + 2; i++) {
            int lx = lx0 + (int) (i * 14 - (belt % 14));
            if (lx < lx0 || lx + 3 > lx0 + lw) continue;
            ctx.fill(lx, ly + 2, lx + 3, ly + lh - 2, 0xFF3A3A48);
        }
        ctx.fill(lx0, ly + lh, lx0 + lw, ly + lh + 3, Ui.CYAN);
        float step = MathHelper.sin(belt * 0.08f);
        int rx = lx0 + lw / 2, ry = ly;
        int bob = (int) (Math.abs(step) * 2);
        ctx.fill(rx - 5, ry - 58 - bob, rx + 5, ry - 48 - bob, 0xFFE0B48F);          // голова
        ctx.fill(rx - 7, ry - 48 - bob, rx + 7, ry - 26 - bob, Ui.PINK);             // торс
        ctx.fill(rx - 3 + (int) (step * 9), ry - 46 - bob, rx + 2 + (int) (step * 9), ry - 32 - bob, 0xFFE0B48F); // руки
        ctx.fill(rx - 3 - (int) (step * 9), ry - 46 - bob, rx + 2 - (int) (step * 9), ry - 32 - bob, 0xFFC09070);
        ctx.fill(rx - 5 + (int) (step * 8), ry - 26, rx + (int) (step * 8), ry, 0xFF2F4A7A); // ноги
        ctx.fill(rx - (int) (step * 8), ry - 26, rx + 5 - (int) (step * 8), ry, 0xFF233A62);
        if (finished) center(ctx, "Финиш! Считаем…", width / 2, y + ph + 4, Ui.GOLD);
        waitCheck();
    }

    /** Ответ сервера не пришёл за 6 с — вернуться в меню, а не висеть. */
    private void waitCheck() {
        if (finished && sentAt > 0 && System.currentTimeMillis() - sentAt > 6000) {
            sentAt = 0;
            deny = "Тренер не засчитал подход — попробуй ещё раз";
            denyAt = System.currentTimeMillis();
            state = State.MENU;
        }
    }

    private void runKey(int dir) {
        if (finished) return;
        if (dir == lastKey) speed = Math.max(0, speed - 0.04f); // сбился с шага
        else speed = Math.min(1, speed + 0.075f);
        lastKey = dir;
    }

    // ------------------------------------------------------------------ итог
    private void result(DrawContext ctx, int y) {
        boolean str = ex.equals("bench");
        int w = Math.min(260, width - 40), x = width / 2 - w / 2, h = 110;
        ctx.fill(x, y, x + w, y + h, 0xF0070707);
        Ui.panel(ctx, x, y, w, h, str ? Ui.RED : Ui.CYAN);
        Ui.big(ctx, textRenderer, "ПОДХОД ЗАКОНЧЕН", width / 2f, y + 10, 1.4f, Ui.WHITE, 1);
        String g = gain > 0 ? (str ? "СИЛА +" : "ВЫНОСЛИВОСТЬ +") + gain : "Без прироста";
        Ui.big(ctx, textRenderer, g, width / 2f, y + 32, 1.6f, gain > 0 ? Ui.MONEY : Ui.GOLD, 1);
        center(ctx, (str ? "Сила " : "Выносливость ") + value + " / " + StyleCatalog.STAT_MAX, width / 2, y + 54, 0xFFDDDBD4);
        if (gain == 0) center(ctx, str ? "Нужно хотя бы 3 повтора — или лимит на сегодня" : "Держи темп хотя бы 30% времени — или лимит на сегодня", width / 2, y + 66, 0xFFB9B7B1);
        button(ctx, x + 8, y + h - 26, w / 2 - 12, 18, "ЕЩЁ ПОДХОД", Ui.GOLD, true, () -> state = State.MENU);
        button(ctx, x + w / 2 + 4, y + h - 26, w / 2 - 12, 18, "ВЫЙТИ", 0xFF6E6D69, true, this::close);
    }

    // ------------------------------------------------------------------ управление
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (state == State.BENCH && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER)) {
            benchPress();
            return true;
        }
        if (state == State.RUN) {
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
                runKey(-1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
                runKey(1);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (state == State.BENCH && button == 0) {
            benchPress();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Для автотеста: нажать «жать»/шаг. */
    public void autotest(int i) {
        if (state == State.BENCH) {
            float pos = 0.5f + 0.5f * MathHelper.sin(phase * 2.2f);
            float half = Math.max(0.05f, 0.17f - reps * 0.009f);
            float center = 0.5f + 0.18f * MathHelper.sin(reps * 1.7f);
            if (Math.abs(pos - center) <= half * 0.7f || i % 23 == 0) benchPress();
        }
        if (state == State.RUN) {
            float t = (System.currentTimeMillis() - startAt) / 1000f;
            if (speed < 0.5f + 0.2f * MathHelper.sin(t * 0.45f)) runKey(lastKey == 1 ? -1 : 1);
        }
    }

    public boolean idle() {
        return state == State.MENU;
    }

    public void autostart(String which) {
        if (state == State.MENU) start(which);
    }
}
