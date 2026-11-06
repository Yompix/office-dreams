package com.yompix.novograd.client.screen;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Дартс в баре: три раунда по три дротика против завсегдатая. Прицел гуляет (бар всё-таки),
 * Shift — задержать дыхание (ненадолго), ЛКМ — бросок. Больше очков, чем у соперника, — забираешь ставку (gta/Arcade).
 */
public class DartsScreen extends LifeScreen {
    private static final Identifier BOARD = Novograd.id("dynamic/dartboard");
    private static final int TR = 64;
    private static NativeImageBackedTexture boardTex;
    private static final int[] SECT = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};

    private final int foe, bet;
    private final String foeName;
    private final int[] foeRounds = new int[3];
    private final int[] myRounds = new int[3];
    private final List<float[]> darts = new ArrayList<>(); // {nx, ny, очки}
    private int round, dart, total;
    private float breath = 1, foeShowIn = -1;
    private int foeShown;
    private boolean holding, done, sent;
    private String lastHit = "";
    private float lastHitT;
    private final long seed = System.nanoTime();

    public DartsScreen(NbtCompound n) {
        super("Дартс");
        foe = n.getInt("foe");
        bet = n.getInt("bet");
        foeName = n.getString("foeName").isEmpty() ? "Завсегдатай" : n.getString("foeName");
        Random r = new Random(seed);
        int a = (int) (foe * (0.26f + r.nextFloat() * 0.12f)), b = (int) (foe * (0.28f + r.nextFloat() * 0.12f));
        foeRounds[0] = a;
        foeRounds[1] = b;
        foeRounds[2] = foe - a - b;
        bake();
    }

    private static void bake() {
        if (boardTex != null) return;
        int s = TR * 2 + 1;
        NativeImage img = new NativeImage(s, s, false);
        for (int y = 0; y < s; y++)
            for (int x = 0; x < s; x++) {
                double dx = (x - TR) / (double) TR, dy = (y - TR) / (double) TR;
                img.setColor(x, y, abgr(colorAt(dx, dy)));
            }
        boardTex = new NativeImageBackedTexture(img);
        mc().getTextureManager().registerTexture(BOARD, boardTex);
    }

    private static int abgr(int argb) {
        return (argb & 0xFF000000) | (argb & 0xFF) << 16 | (argb & 0xFF00) | (argb >> 16 & 0xFF);
    }

    private static int sector(double dx, double dy) {
        double ang = Math.toDegrees(Math.atan2(dx, -dy));
        return (int) Math.floor(((ang + 9 + 720) % 360) / 18);
    }

    private static int colorAt(double dx, double dy) {
        double r = Math.hypot(dx, dy);
        if (r > 1) return 0;
        if (r > 0.86) return 0xFF141414; // кольцо с цифрами
        boolean even = sector(dx, dy) % 2 == 0;
        r /= 0.86;
        if (r < 0.05) return 0xFFD32F2F;
        if (r < 0.11) return 0xFF2E7D32;
        if (r > 0.94 || r > 0.56 && r < 0.62) return even ? 0xFFD32F2F : 0xFF2E7D32;
        if (Math.abs(r - 0.62) < 0.008 || Math.abs(r - 0.56) < 0.008 || Math.abs(r - 0.94) < 0.008) return 0xFFB0B0B0; // проволока
        return even ? 0xFF1C1C1C : 0xFFEFE3C2;
    }

    /** Очки за попадание в точку (в долях радиуса игрового поля). */
    private static int points(double dx, double dy) {
        double r = Math.hypot(dx, dy) / 0.86;
        if (r > 1) return 0;
        if (r < 0.05) return 50;
        if (r < 0.11) return 25;
        int v = SECT[sector(dx, dy)];
        if (r > 0.94) return v * 2;
        if (r > 0.56 && r < 0.62) return v * 3;
        return v;
    }

    private int R() {
        return (int) Math.min(height * 0.36f, 100);
    }

    /** Прицел: мышь плюс «гуляние» руки; Shift — задержать дыхание. */
    private float[] aim() {
        float t = seconds();
        float amp = R() * (holding && breath > 0 ? 0.035f : 0.13f) * (1 + round * 0.15f);
        float ox = MathHelper.sin(t * 1.9f) * amp + MathHelper.sin(t * 4.3f + 1) * amp * 0.35f;
        float oy = MathHelper.cos(t * 2.4f) * amp * 0.8f + MathHelper.sin(t * 5.1f) * amp * 0.3f;
        return new float[]{mouseX + ox, mouseY + oy};
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int R = R(), cx = width / 2 - 40, cy = height / 2 + 8;
        float dt = 1 / 60f;
        holding = org.lwjgl.glfw.GLFW.glfwGetKey(mc().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
        breath = MathHelper.clamp(breath + (holding ? -dt / 1.6f : dt / 3f), 0, 1);
        // стена бара
        ctx.fillGradient(0, 0, width, height, 0xF0201410, 0xF0100A08);
        title(ctx, "Дартс", 12, 10, Ui.GOLD);
        // доска
        ctx.fill(cx - R - 8, cy - R - 8, cx + R + 8, cy + R + 8, 0xFF3A2516);
        ctx.drawTexture(BOARD, cx - R, cy - R, 0, 0, R * 2, R * 2, R * 2, R * 2);
        for (int i = 0; i < 20; i++) {
            double a = Math.toRadians(i * 18);
            int nx = (int) (cx + Math.sin(a) * R * 0.93), ny = (int) (cy - Math.cos(a) * R * 0.93);
            String s = String.valueOf(SECT[i]);
            ctx.drawText(textRenderer, s, nx - textRenderer.getWidth(s) / 2, ny - 4, 0xFFE0E0E0, false);
        }
        // дротики
        for (float[] d : darts) {
            int dx = (int) (cx + d[0] * R), dy = (int) (cy + d[1] * R);
            ctx.fill(dx - 1, dy - 1, dx + 2, dy + 2, 0xFFFFC428);
            ctx.fill(dx + 1, dy - 7, dx + 2, dy - 1, 0xFF9E9E9E);
            ctx.fill(dx - 1, dy - 10, dx + 4, dy - 7, Ui.PINK);
        }
        // прицел
        if (!done) {
            float[] a = aim();
            int ax = (int) a[0], ay = (int) a[1];
            int col = holding && breath > 0 ? Ui.CYAN : 0xFFFFFFFF;
            ctx.fill(ax - 7, ay, ax - 2, ay + 1, col);
            ctx.fill(ax + 3, ay, ax + 8, ay + 1, col);
            ctx.fill(ax, ay - 7, ax + 1, ay - 2, col);
            ctx.fill(ax, ay + 3, ax + 1, ay + 8, col);
            Ui.bar(ctx, cx - 40, cy + R + 14, 80, 4, breath, Ui.CYAN);
            Ui.small(ctx, textRenderer, "Shift — задержать дыхание · ЛКМ — бросок", cx, cy + R + 22, 0.85f, Ui.DIM, 1);
        }
        if (seconds() - lastHitT < 1.2f && !lastHit.isEmpty())
            Ui.big(ctx, textRenderer, lastHit, cx, cy - R - 26, 1.6f, Ui.GOLD, 1);
        // табло
        int px = width - 150, py = 40;
        Ui.panel(ctx, px, py, 138, 150, Ui.GOLD);
        Ui.small(ctx, textRenderer, "Ставка $" + bet, px + 69, py + 8, 1f, Ui.MONEY, 1);
        ctx.drawText(textRenderer, "Раунд", px + 8, py + 26, 0xFFA9A3BC, false);
        ctx.drawText(textRenderer, "Ты", px + 60, py + 26, 0xFFFFFFFF, false);
        String fn = foeName.length() > 9 ? foeName.substring(0, 9) + "." : foeName;
        ctx.drawText(textRenderer, fn, px + 88, py + 26, 0xFFFFC428, false);
        int foeSum = 0;
        for (int i = 0; i < 3; i++) {
            int yy = py + 42 + i * 16;
            ctx.drawText(textRenderer, String.valueOf(i + 1), px + 16, yy, i == round && !done ? 0xFFFFC428 : 0xFFA9A3BC, false);
            String mine = i < round || done ? String.valueOf(myRounds[i]) : i == round ? myRounds[i] + "…" : "—";
            ctx.drawText(textRenderer, mine, px + 60, yy, 0xFFFFFFFF, false);
            boolean shown = i < foeShown;
            if (shown) foeSum += foeRounds[i];
            ctx.drawText(textRenderer, shown ? String.valueOf(foeRounds[i]) : "—", px + 92, yy, 0xFFFFC428, false);
        }
        ctx.fill(px + 8, py + 92, px + 130, py + 93, 0x60FFFFFF);
        ctx.drawText(textRenderer, "Итого", px + 8, py + 98, 0xFFA9A3BC, false);
        ctx.drawText(textRenderer, String.valueOf(total), px + 60, py + 98, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, String.valueOf(foeSum), px + 92, py + 98, 0xFFFFC428, true);
        ctx.drawText(textRenderer, "Дротик " + Math.min(3, dart + 1) + "/3", px + 8, py + 116, 0xFFA9A3BC, false);
        if (foeShowIn > 0) {
            foeShowIn -= dt;
            Ui.small(ctx, textRenderer, foeName + " бросает…", px + 69, py + 132, 0.9f, Ui.GOLD, 1);
            if (foeShowIn <= 0) {
                foeShown++;
                if (client != null && client.player != null) client.player.playSound(SoundEvents.BLOCK_WOOD_HIT, 0.8f, 1.2f);
                if (foeShown >= 3) done = true;
            }
        }
        if (done && foeShowIn <= 0) {
            int ox = width / 2 - 110, oy = height / 2 - 40;
            ctx.fill(ox, oy, ox + 220, oy + 80, 0xE0000000);
            boolean win = total > foe, draw = total == foe;
            Ui.big(ctx, textRenderer, win ? "ПОБЕДА!" : draw ? "НИЧЬЯ" : "ПРОИГРЫШ", width / 2f, oy + 8, 2f, win ? Ui.MONEY : draw ? Ui.WHITE : Ui.RED, 1);
            Ui.small(ctx, textRenderer, total + " : " + foe + (win ? "  ·  +$" + bet : draw ? "" : "  ·  −$" + bet), width / 2f, oy + 34, 1f, Ui.WHITE, 1);
            button(ctx, width / 2 - 50, oy + 54, 100, 18, "Готово", 0xFFC428, true, this::close);
        } else Ui.small(ctx, textRenderer, "Esc — сдаться (ставка проиграна)", 12, height - 14, 0.8f, Ui.DIM, 0);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0 || done || foeShowIn > 0) return false;
        throwDart();
        return true;
    }

    /** Бросок туда, где сейчас прицел (автотест тоже зовёт). */
    public void throwDart() {
        int R = R(), cx = width / 2 - 40, cy = height / 2 + 8;
        if (dart == 0 && !darts.isEmpty()) darts.clear();
        float[] a = aim();
        float nx = (a[0] - cx) / R + (float) ((Math.random() - 0.5) * 0.03), ny = (a[1] - cy) / R + (float) ((Math.random() - 0.5) * 0.03);
        int pts = points(nx, ny);
        darts.add(new float[]{nx, ny, pts});
        myRounds[round] += pts;
        total += pts;
        lastHit = pts == 0 ? "МИМО" : pts == 50 ? "ЯБЛОЧКО! 50" : "+" + pts;
        lastHitT = seconds();
        if (client != null && client.player != null) client.player.playSound(SoundEvents.BLOCK_WOOD_HIT, 1f, pts >= 40 ? 1.6f : 1f);
        dart++;
        if (dart >= 3) {
            dart = 0;
            round++;
            foeShowIn = 1.1f;
            if (round >= 3) round = 2;
        }
    }

    @Override
    public void close() {
        if (!sent) {
            sent = true;
            NbtCompound n = new NbtCompound();
            n.putString("game", "darts");
            n.putInt("score", total);
            n.putBoolean("quit", !(done && foeShowIn <= 0));
            NovogradClient.send("arcade_done", n);
        }
        super.close();
    }
}
