package com.yompix.novograd.client.games;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 0.9.9: игры в барах и на площадках — бильярд, боулинг, баскетбол, гольф. Вид сверху/сбоку, простая физика (шары,
 * кегли, мяч), управление мышью: потянул и отпустил. Соперник — завсегдатай, его счёт известен заранее (сервер);
 * в конце свой счёт уходит на сервер (gta/Games.done), он проверяет и платит. Esc — сдаться.
 */
public final class MiniGames {
    private MiniGames() {}

    public static void open(NbtCompound n) {
        Base s = switch (n.getString("game")) {
            case "billiards" -> new Billiards(n);
            case "bowling" -> new Bowling(n);
            case "hoops" -> new Hoops(n);
            default -> new Golf(n);
        };
        MinecraftClient.getInstance().setScreen(s);
    }

    // ====================================================================== общая рамка
    abstract static class Base extends LifeScreen {
        final String game, foeName;
        final int bet, foe;
        final Random rnd;
        int score;
        boolean finished, sent;
        long last = System.nanoTime();
        float doneT;
        int fx, fy, fw, fh; // поле

        Base(NbtCompound n, String title) {
            super(title);
            game = n.getString("game");
            foeName = n.getString("foeName");
            bet = n.getInt("bet");
            foe = n.getInt("foe");
            rnd = new Random(n.getLong("seed"));
        }

        boolean lower() {
            return false;
        }

        abstract String progress();

        abstract void step(float dt);

        abstract void field(DrawContext ctx);

        @Override
        protected void draw(DrawContext ctx, float delta) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            fw = Math.min(width - 40, 520);
            fh = Math.min(height - 70, 300);
            fx = width / 2 - fw / 2;
            fy = 40;
            // шапка: игра, соперник, счёт
            title(ctx, getTitle().getString().toUpperCase(), 12, 8, 0xFF3FB8B0);
            Ui.small(ctx, textRenderer, foeName + ": " + foe + (lower() ? " ударов" : "") + "   ·   ставка $" + bet, width - 12, 10, 0.85f, Ui.GOLD, 2);
            Ui.big(ctx, textRenderer, String.valueOf(score), width - 12, 20, 1.4f, Ui.WHITE, 2);
            Ui.small(ctx, textRenderer, progress(), 12, 26, 0.85f, 0xFFB0BEC5, 0);
            for (int i = 0; i < 4 && !finished; i++) step(dt / 4);
            field(ctx);
            if (finished) {
                doneT += dt;
                boolean win = lower() ? score < foe : score > foe;
                boolean draw = score == foe;
                ctx.fill(fx, fy + fh / 2 - 22, fx + fw, fy + fh / 2 + 22, 0xC0000000);
                Ui.big(ctx, textRenderer, draw ? "НИЧЬЯ" : win ? "ПОБЕДА" : "ПРОИГРЫШ", width / 2, fy + fh / 2 - 12, 2f, draw ? Ui.GOLD : win ? Ui.MONEY : Ui.RED, 1);
                if (!sent && doneT > 0.4f) finish(false);
                if (doneT > 2.6f) close();
            }
            Ui.small(ctx, textRenderer, hint(), width / 2, height - 14, 0.85f, 0xFF90A4AE, 1);
        }

        abstract String hint();

        void finish(boolean quit) {
            if (sent) return;
            sent = true;
            NbtCompound n = new NbtCompound();
            n.putString("game", game);
            n.putInt("score", score);
            n.putBoolean("quit", quit);
            NovogradClient.send("game_done", n);
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!finished) finish(true);
                close();
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }

        @Override
        public void removed() {
            if (!sent) finish(true);
        }

        void sound(net.minecraft.sound.SoundEvent e, float vol, float pitch) {
            if (client != null && client.player != null) client.player.playSound(e, vol, pitch);
        }

        static void disc(DrawContext ctx, float cx, float cy, float r, int color) {
            int ir = Math.max(1, Math.round(r));
            for (int dy = -ir; dy <= ir; dy++) {
                int hw = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
                ctx.fill(Math.round(cx) - hw, Math.round(cy) + dy, Math.round(cx) + hw + 1, Math.round(cy) + dy + 1, color);
            }
        }
    }

    // ====================================================================== шары (общая физика)
    static final class Ball {
        float x, y, vx, vy, r;
        int color;
        boolean gone;
        float x0, y0;

        Ball(float x, float y, float r, int color) {
            this.x = x0 = x;
            this.y = y0 = y;
            this.r = r;
            this.color = color;
        }

        boolean moving() {
            return !gone && vx * vx + vy * vy > 0.04f;
        }
    }

    /** Упругие столкновения равных шаров. */
    static boolean collide(List<Ball> bs) {
        boolean hit = false;
        for (int i = 0; i < bs.size(); i++)
            for (int j = i + 1; j < bs.size(); j++) {
                Ball a = bs.get(i), b = bs.get(j);
                if (a.gone || b.gone) continue;
                float dx = b.x - a.x, dy = b.y - a.y, d = (float) Math.sqrt(dx * dx + dy * dy), min = a.r + b.r;
                if (d <= 0 || d >= min) continue;
                float nx = dx / d, ny = dy / d;
                float over = (min - d) / 2;
                a.x -= nx * over;
                a.y -= ny * over;
                b.x += nx * over;
                b.y += ny * over;
                float rel = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny;
                if (rel <= 0) continue;
                a.vx -= rel * nx;
                a.vy -= rel * ny;
                b.vx += rel * nx;
                b.vy += rel * ny;
                hit = true;
            }
        return hit;
    }

    // ====================================================================== бильярд
    static final class Billiards extends Base {
        final List<Ball> balls = new ArrayList<>();
        Ball cue;
        int shots = 10;
        boolean aiming;
        float ax, ay;
        static final float W = 400, H = 200;

        Billiards(NbtCompound n) {
            super(n, "Бильярд");
            cue = new Ball(100, 100, 6, 0xFFF5F5F5);
            balls.add(cue);
            int[] cols = {0xFFFFC107, 0xFF1E88E5, 0xFFE53935, 0xFF8E24AA, 0xFFFB8C00, 0xFF43A047, 0xFF6D4C41, 0xFF212121, 0xFFD81B60, 0xFF00ACC1};
            int k = 0;
            for (int row = 0; row < 4; row++)
                for (int i = 0; i <= row; i++) balls.add(new Ball(280 + row * 10.5f, 100 + (i - row / 2f) * 12.2f, 6, cols[k++ % cols.length]));
        }

        float sx(float x) {
            return fx + x * fw / W;
        }

        float sy(float y) {
            return fy + y * fh / H;
        }

        boolean still() {
            for (Ball b : balls) if (b.moving()) return false;
            return true;
        }

        @Override
        String progress() {
            return "Ударов: " + shots + " · забито " + score;
        }

        @Override
        String hint() {
            return "Зажми ЛКМ у белого шара, потяни назад и отпусти — удар. Сильнее тянешь — сильнее удар";
        }

        @Override
        void step(float dt) {
            for (Ball b : balls) {
                if (b.gone) continue;
                b.x += b.vx * dt;
                b.y += b.vy * dt;
                float f = (float) Math.pow(0.55, dt);
                b.vx *= f;
                b.vy *= f;
                if (Math.abs(b.vx) < 2 && Math.abs(b.vy) < 2) b.vx = b.vy = 0;
                // лузы: углы и середины длинных бортов
                float[][] pk = {{0, 0}, {W / 2, 0}, {W, 0}, {0, H}, {W / 2, H}, {W, H}};
                for (float[] p : pk)
                    if (Math.hypot(b.x - p[0], b.y - p[1]) < 13) {
                        b.gone = true;
                        sound(SoundEvents.BLOCK_WOOD_HIT, 0.6f, 0.7f);
                        if (b == cue) {
                            score = Math.max(0, score - 1);
                            b.gone = false;
                            b.x = 100;
                            b.y = 100;
                            b.vx = b.vy = 0;
                        } else score++;
                    }
                if (b.x < b.r) { b.x = b.r; b.vx = -b.vx * 0.85f; }
                if (b.x > W - b.r) { b.x = W - b.r; b.vx = -b.vx * 0.85f; }
                if (b.y < b.r) { b.y = b.r; b.vy = -b.vy * 0.85f; }
                if (b.y > H - b.r) { b.y = H - b.r; b.vy = -b.vy * 0.85f; }
            }
            if (collide(balls)) sound(SoundEvents.BLOCK_STONE_HIT, 0.3f, 1.8f);
            boolean left = false;
            for (Ball b : balls) left |= b != cue && !b.gone;
            if (still() && (shots == 0 || !left)) finished = true;
        }

        @Override
        void field(DrawContext ctx) {
            ctx.fill(fx - 10, fy - 10, fx + fw + 10, fy + fh + 10, 0xFF4E2C14);
            ctx.fill(fx, fy, fx + fw, fy + fh, 0xFF1B6B3A);
            float k = fw / W;
            for (float[] p : new float[][]{{0, 0}, {W / 2, 0}, {W, 0}, {0, H}, {W / 2, H}, {W, H}}) disc(ctx, sx(p[0]), sy(p[1]), 11 * k, 0xFF0A0A0A);
            for (Ball b : balls) if (!b.gone) {
                disc(ctx, sx(b.x) + 1, sy(b.y) + 1, b.r * k, 0x60000000);
                disc(ctx, sx(b.x), sy(b.y), b.r * k, b.color);
            }
            if (aiming && still()) {
                float dx = sx(cue.x) - mouseX, dy = sy(cue.y) - mouseY;
                for (int i = 1; i < 14; i++) ctx.fill((int) (sx(cue.x) + dx * i * 0.25f), (int) (sy(cue.y) + dy * i * 0.25f), (int) (sx(cue.x) + dx * i * 0.25f) + 2,
                        (int) (sy(cue.y) + dy * i * 0.25f) + 2, 0x90FFFFFF);
                ctx.fill(mouseX - 1, mouseY - 1, mouseX + 2, mouseY + 2, 0xFFD7A15E);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (!finished && still() && shots > 0 && Math.hypot(mx - sx(cue.x), my - sy(cue.y)) < 40) aiming = true;
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int b) {
            if (aiming && still() && shots > 0) {
                float k = W / fw;
                float dx = (float) (sx(cue.x) - mx) * k, dy = (float) (sy(cue.y) - my) * k;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len > 3) {
                    float p = Math.min(len, 120) * 5.2f;
                    cue.vx = dx / len * p;
                    cue.vy = dy / len * p;
                    shots--;
                    sound(SoundEvents.BLOCK_WOOD_HIT, 0.5f, 1.4f);
                }
            }
            aiming = false;
            return true;
        }
    }

    // ====================================================================== боулинг
    static final class Bowling extends Base {
        final List<Ball> pins = new ArrayList<>();
        Ball ball;
        int frame = 1, roll = 1, firstRoll;
        boolean rolling, aiming;
        float wait, hook;
        static final float W = 150, L = 520;

        Bowling(NbtCompound n) {
            super(n, "Боулинг");
            rack();
        }

        void rack() {
            pins.clear();
            for (int row = 0; row < 4; row++)
                for (int i = 0; i <= row; i++) pins.add(new Ball(W / 2 + (i - row / 2f) * 15, 70 - row * 13, 4.5f, 0xFFF5F5F5));
            newBall();
        }

        void newBall() {
            ball = new Ball(W / 2, L - 30, 8, 0xFF1A237E);
            rolling = false;
        }

        int down() {
            int n = 0;
            for (Ball p : pins) if (p.gone || Math.hypot(p.x - p.x0, p.y - p.y0) > 4) n++;
            return n;
        }

        float sx(float x) {
            return fx + fw / 2f - W * k() / 2 + x * k();
        }

        float sy(float y) {
            return fy + y * fh / L;
        }

        /** Ширина дорожки на экране — шире, чем по пропорции (иначе узкая полоска). */
        float k() {
            return Math.max(fh / L, Math.min(fw * 0.3f / W, fh / L * 1.8f));
        }

        @Override
        String progress() {
            return "Фрейм " + Math.min(frame, 5) + "/5 · бросок " + roll;
        }

        @Override
        String hint() {
            return "Зажми ЛКМ на шаре, потяни вниз и в сторону, отпусти. Боковое движение — подкрутка";
        }

        @Override
        void step(float dt) {
            if (!rolling) return;
            List<Ball> all = new ArrayList<>(pins);
            all.add(ball);
            for (Ball b : all) {
                if (b.gone) continue;
                if (b == ball) b.vx += hook * dt;
                b.x += b.vx * dt;
                b.y += b.vy * dt;
                float f = (float) Math.pow(b == ball ? 0.92 : 0.25, dt);
                b.vx *= f;
                b.vy *= f;
                if (b.x < -4 || b.x > W + 4) b.gone = true; // в жёлоб
                if (b.y < -10) b.gone = true;
            }
            if (collide(all)) sound(SoundEvents.BLOCK_BAMBOO_WOOD_HIT, 0.5f, 1f);
            wait += dt;
            boolean moving = ball.moving() && !ball.gone;
            for (Ball p : pins) moving |= p.moving();
            if ((!moving && wait > 0.6f) || wait > 6) {
                int n = down();
                int got = n - firstRoll;
                if (roll == 1) {
                    if (n == 10) { score += 20; next(); } // страйк: 10 + бонус 10
                    else { firstRoll = n; roll = 2; for (Ball p : pins) if (Math.hypot(p.x - p.x0, p.y - p.y0) > 4) p.gone = true; newBall(); }
                    if (roll == 2) score += got;
                } else {
                    score += got + (n == 10 ? 5 : 0); // спэр: бонус 5
                    next();
                }
            }
        }

        void next() {
            frame++;
            roll = 1;
            firstRoll = 0;
            if (frame > 5) {
                finished = true;
                return;
            }
            rack();
        }

        @Override
        void field(DrawContext ctx) {
            int lx = (int) sx(0), rx = (int) sx(W);
            ctx.fill(lx - 8, fy, rx + 8, fy + fh, 0xFF263238);
            ctx.fill(lx, fy, rx, fy + fh, 0xFFD7B47A);
            for (int i = 1; i < 8; i++) ctx.fill(lx + (rx - lx) * i / 8, fy, lx + (rx - lx) * i / 8 + 1, fy + fh, 0x30000000);
            for (Ball p : pins) if (!p.gone) {
                disc(ctx, sx(p.x), sy(p.y), p.r * k() + 1, 0xFFB71C1C);
                disc(ctx, sx(p.x), sy(p.y), p.r * k(), p.color);
            }
            if (!ball.gone) disc(ctx, sx(ball.x), sy(ball.y), ball.r * k(), ball.color);
            if (aiming) ctx.fill((int) sx(ball.x), (int) sy(ball.y), mouseX + 1, mouseY + 1, 0x00000000);
            if (aiming) {
                float dx = sx(ball.x) - mouseX, dy = sy(ball.y) - mouseY;
                for (int i = 1; i < 10; i++) ctx.fill((int) (sx(ball.x) + dx * i * 0.3f), (int) (sy(ball.y) + dy * i * 0.3f), (int) (sx(ball.x) + dx * i * 0.3f) + 2,
                        (int) (sy(ball.y) + dy * i * 0.3f) + 2, 0x90FFFFFF);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (!rolling && !finished) aiming = true;
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
            if (aiming && !rolling) ball.x = MathHelper.clamp(ball.x, 10, W - 10);
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int b) {
            if (aiming && !rolling) {
                float dx = (float) (sx(ball.x) - mx) / k(), dy = (float) (sy(ball.y) - my) * L / fh;
                if (dy < -20) {
                    float p = Math.min(-dy, 300) * 2.2f;
                    ball.vy = -p;
                    ball.vx = dx * 0.25f;
                    hook = -dx * 0.6f;
                    rolling = true;
                    wait = 0;
                    sound(SoundEvents.BLOCK_STONE_PLACE, 0.6f, 0.6f);
                }
            }
            aiming = false;
            return true;
        }
    }

    // ====================================================================== баскетбол: штрафные
    static final class Hoops extends Base {
        int shots = 10;
        float meter, meterV = 1.4f, bx, by, vx, vy, t;
        boolean charging, flying, scored, rim;
        static final float HX = 0.82f, HY = 0.32f, G = 1.6f;

        Hoops(NbtCompound n) {
            super(n, "Баскетбол");
        }

        @Override
        String progress() {
            return "Бросков: " + shots + " · чисто — 3, от дужки — 2";
        }

        @Override
        String hint() {
            return "Зажми ЛКМ — сила качается; отпусти в зелёной зоне";
        }

        @Override
        void step(float dt) {
            if (charging) {
                meter += meterV * dt;
                if (meter > 1 || meter < 0) {
                    meterV = -meterV;
                    meter = MathHelper.clamp(meter, 0, 1);
                }
            }
            if (!flying) return;
            t += dt;
            bx += vx * dt;
            by += vy * dt;
            vy += G * dt;
            // кольцо: центр HX, HY, ширина 0.05; дужки по краям
            if (vy > 0 && Math.abs(by - HY) < 0.012f) {
                float off = Math.abs(bx - HX);
                if (off < 0.018f && !scored) { scored = true; score += 3; sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f); }
                else if (off < 0.034f && !scored) { scored = true; rim = true; score += 2; sound(SoundEvents.BLOCK_CHAIN_HIT, 0.6f, 1.5f); }
            }
            if (by > 1.05f || bx > 1.05f || t > 4) {
                flying = false;
                if (shots == 0) finished = true;
            }
        }

        @Override
        void field(DrawContext ctx) {
            ctx.fill(fx, fy, fx + fw, fy + fh, 0xFF1B2838);
            ctx.fill(fx, fy + (int) (fh * 0.9f), fx + fw, fy + fh, 0xFFB5651D);
            // щит и кольцо
            int hx = fx + (int) (fw * HX), hy = fy + (int) (fh * HY);
            ctx.fill(hx + (int) (fw * 0.03f), hy - (int) (fh * 0.16f), hx + (int) (fw * 0.035f), hy + (int) (fh * 0.04f), 0xFFECEFF1);
            ctx.fill(hx - (int) (fw * 0.03f), hy, hx + (int) (fw * 0.03f), hy + 2, 0xFFFF5722);
            for (int i = 0; i < 5; i++) ctx.fill(hx - (int) (fw * 0.025f) + i * (int) (fw * 0.012f), hy + 2, hx - (int) (fw * 0.025f) + i * (int) (fw * 0.012f) + 1,
                    hy + (int) (fh * 0.06f), 0x90FFFFFF);
            // игрок и мяч
            int px = fx + (int) (fw * 0.12f), py = fy + (int) (fh * 0.9f);
            ctx.fill(px - 4, py - 34, px + 4, py, 0xFF90A4AE);
            float x = flying ? bx : 0.14f, y = flying ? by : 0.62f;
            disc(ctx, fx + fw * x, fy + fh * y, 6, 0xFFFF6F00);
            // шкала силы: зелёная зона — где попадёшь
            int mx0 = fx + 20, my0 = fy + fh - 30, mw = 160;
            ctx.fill(mx0, my0, mx0 + mw, my0 + 8, 0x60FFFFFF);
            float lo = best() - 0.05f, hi = best() + 0.05f;
            ctx.fill(mx0 + (int) (mw * lo), my0, mx0 + (int) (mw * hi), my0 + 8, 0xC043A047);
            ctx.fill(mx0 + (int) (mw * meter) - 1, my0 - 2, mx0 + (int) (mw * meter) + 2, my0 + 10, 0xFFFFFFFF);
        }

        /** Сила, при которой мяч ровно в кольцо (подбор простым перебором). */
        float best() {
            float bestP = 0.5f, err = 9;
            for (float p = 0.2f; p <= 1f; p += 0.01f) {
                float vx0 = (0.55f + p * 0.65f) * 0.62f, vy0 = -(0.55f + p * 0.65f) * 0.79f;
                // время до x кольца
                float tt = (HX - 0.14f) / vx0;
                float yy = 0.62f + vy0 * tt + G * tt * tt / 2;
                if (Math.abs(yy - HY) < err && vy0 + G * tt > 0) {
                    err = Math.abs(yy - HY);
                    bestP = p;
                }
            }
            return bestP;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (!flying && shots > 0 && !finished) charging = true;
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int b) {
            if (charging) {
                charging = false;
                float s = 0.55f + meter * 0.65f;
                vx = s * 0.62f;
                vy = -s * 0.79f;
                bx = 0.14f;
                by = 0.62f;
                t = 0;
                flying = true;
                scored = rim = false;
                shots--;
                sound(SoundEvents.ENTITY_SNOWBALL_THROW, 0.6f, 0.8f);
            }
            return true;
        }
    }

    // ====================================================================== гольф: паттинг на трёх лунках
    static final class Golf extends Base {
        int hole = 1, strokes;
        Ball ball;
        float holeX, holeY, slopeX, slopeY;
        boolean aiming;
        static final float W = 300, H = 180;

        Golf(NbtCompound n) {
            super(n, "Гольф");
            setup();
        }

        @Override
        boolean lower() {
            return true;
        }

        void setup() {
            ball = new Ball(40, 90 + (rnd.nextFloat() - 0.5f) * 60, 4, 0xFFFFFFFF);
            holeX = 230 + rnd.nextFloat() * 50;
            holeY = 40 + rnd.nextFloat() * 100;
            slopeX = (rnd.nextFloat() - 0.5f) * 30;
            slopeY = (rnd.nextFloat() - 0.5f) * 40;
            strokes = 0;
        }

        float sx(float x) {
            return fx + x * fw / W;
        }

        float sy(float y) {
            return fy + y * fh / H;
        }

        @Override
        String progress() {
            return "Лунка " + Math.min(hole, 3) + "/3 · ударов на лунке " + strokes + " · всего " + score;
        }

        @Override
        String hint() {
            return "Потяни от мяча назад и отпусти. Стрелки на траве — уклон";
        }

        @Override
        void step(float dt) {
            if (!ball.moving()) return;
            ball.vx += slopeX * dt;
            ball.vy += slopeY * dt;
            ball.x += ball.vx * dt;
            ball.y += ball.vy * dt;
            float f = (float) Math.pow(0.45, dt);
            ball.vx *= f;
            ball.vy *= f;
            if (ball.x < 4 || ball.x > W - 4) ball.vx = -ball.vx * 0.6f;
            if (ball.y < 4 || ball.y > H - 4) ball.vy = -ball.vy * 0.6f;
            ball.x = MathHelper.clamp(ball.x, 4, W - 4);
            ball.y = MathHelper.clamp(ball.y, 4, H - 4);
            float v = (float) Math.hypot(ball.vx, ball.vy);
            if (Math.hypot(ball.x - holeX, ball.y - holeY) < 6 && v < 90) {
                sound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.6f, 1.4f);
                hole++;
                if (hole > 3) finished = true;
                else setup();
            }
            if (!ball.moving()) {
                ball.vx = ball.vy = 0;
                if (strokes >= 8) { // больше 8 — «подобрал мяч», +2 штрафных
                    score += 2;
                    hole++;
                    if (hole > 3) finished = true;
                    else setup();
                }
            }
        }

        @Override
        void field(DrawContext ctx) {
            ctx.fill(fx - 6, fy - 6, fx + fw + 6, fy + fh + 6, 0xFF2E5E2A);
            ctx.fill(fx, fy, fx + fw, fy + fh, 0xFF4CAF50);
            // уклон — стрелки
            for (int i = 0; i < 6; i++)
                for (int j = 0; j < 4; j++) {
                    float ax = sx(25 + i * 50), ay = sy(22 + j * 45);
                    float l = 6;
                    float n = (float) Math.hypot(slopeX, slopeY) + 1e-3f;
                    ctx.fill((int) ax, (int) ay, (int) (ax + slopeX / n * l) + 1, (int) (ay + slopeY / n * l) + 1, 0x4FFFFFFF);
                }
            disc(ctx, sx(holeX), sy(holeY), 6 * fw / W, 0xFF101010);
            ctx.fill((int) sx(holeX), (int) sy(holeY) - 26, (int) sx(holeX) + 1, (int) sy(holeY), 0xFFECEFF1);
            ctx.fill((int) sx(holeX) + 1, (int) sy(holeY) - 26, (int) sx(holeX) + 12, (int) sy(holeY) - 19, 0xFFE53935);
            disc(ctx, sx(ball.x), sy(ball.y), ball.r * fw / W, ball.color);
            if (aiming) {
                float dx = sx(ball.x) - mouseX, dy = sy(ball.y) - mouseY;
                for (int i = 1; i < 10; i++) ctx.fill((int) (sx(ball.x) + dx * i * 0.25f), (int) (sy(ball.y) + dy * i * 0.25f), (int) (sx(ball.x) + dx * i * 0.25f) + 2,
                        (int) (sy(ball.y) + dy * i * 0.25f) + 2, 0x90FFFFFF);
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int b) {
            if (!finished && !ball.moving()) aiming = true;
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int b) {
            if (aiming && !ball.moving()) {
                float k = W / fw;
                float dx = (float) (sx(ball.x) - mx) * k, dy = (float) (sy(ball.y) - my) * k;
                float len = (float) Math.hypot(dx, dy);
                if (len > 2) {
                    float p = Math.min(len, 120) * 3.4f;
                    ball.vx = dx / len * p;
                    ball.vy = dy / len * p;
                    strokes++;
                    score++;
                    sound(SoundEvents.BLOCK_WOOD_HIT, 0.5f, 1.8f);
                }
            }
            aiming = false;
            return true;
        }
    }
}
