package com.yompix.novograd.client.city;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * 0.9: телефон → «Сеть»: узлы городской сети рядом и мини-игра взлома — бегунок ходит по шине, «Пробел» или клик,
 * когда он в зелёном окне; три попадания подряд — узел взломан, промах или 12 с — сбой (шум в сети).
 */
public class GridScreen extends LifeScreen {
    public static NbtCompound nodes = new NbtCompound();
    /** Для автотеста: всегда попадать. */
    public static boolean autoWin;

    private String hacking;
    private int diff, hits;
    private float pos, speed, winA, winW;
    private long startNs, lastNs, flashNs;
    private boolean flashOk;
    private final java.util.Random r = new java.util.Random();

    public GridScreen() {
        super("Сеть");
        nodes = new NbtCompound();
        NovogradClient.send("grid_scan");
    }

    /** Для автотеста: начать взлом узла, чей id начинается с prefix (когда узлы пришли). */
    public boolean autoHack(String prefix) {
        for (var e : nodes.getList("nodes", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            if (c.getString("id").startsWith(prefix) && c.getInt("diff") > 0) {
                begin(c.getString("id"), c.getInt("diff"));
                return true;
            }
        }
        return false;
    }

    private void begin(String id, int d) {
        hacking = id;
        diff = d;
        hits = 0;
        int lvl = Math.max(1, nodes.getInt("level"));
        speed = 0.55f + d * 0.12f;
        winW = MathHelper.clamp(0.16f + lvl * 0.025f - d * 0.02f, 0.08f, 0.3f);
        newWindow();
        startNs = lastNs = System.nanoTime();
    }

    private void newWindow() {
        winA = 0.1f + r.nextFloat() * (0.8f - winW);
    }

    private void press() {
        if (hacking == null) return;
        boolean in = autoWin || pos >= winA && pos <= winA + winW;
        flashOk = in;
        flashNs = System.nanoTime();
        if (!in) {
            NovogradClient.send("grid_fail");
            hacking = null;
            return;
        }
        if (++hits >= 3) {
            NbtCompound n = new NbtCompound();
            n.putString("id", hacking);
            NovogradClient.send("grid_hack", n);
            hacking = null;
            close();
            return;
        }
        speed *= 1.18f;
        newWindow();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE && hacking != null) {
            press();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        if (hacking != null) {
            press();
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = Math.min(360, width - 24), h = Math.min(240, height - 20);
        int x = width / 2 - w / 2, y = height / 2 - h / 2;
        panel(ctx, x, y, w, h, 0xF00A1418, 0xF0040808);
        title(ctx, "СЕТЬ", x + 10, y + 8, 0xFF26C6DA);
        String lv = "уровень " + Math.max(1, nodes.getInt("level"));
        ctx.drawText(textRenderer, lv, x + w - 10 - textRenderer.getWidth(lv), y + 10, 0xFF80DEEA, false);
        if (hacking != null) {
            long now = System.nanoTime();
            float dt = (now - lastNs) / 1e9f;
            lastNs = now;
            pos += speed * dt;
            if (pos > 1) { pos = 2 - pos; speed = -speed; }
            if (pos < 0) { pos = -pos; speed = -speed; }
            if (autoWin && pos >= winA && pos <= winA + winW) press();
            if (hacking == null) return;
            if ((now - startNs) / 1e9f > 12) {
                NovogradClient.send("grid_fail");
                hacking = null;
                return;
            }
            int bx = x + 20, bw = w - 40, by = y + h / 2 - 6;
            ctx.fill(bx, by, bx + bw, by + 12, 0xFF0E2A30);
            ctx.fill(bx + (int) (bw * winA), by, bx + (int) (bw * (winA + winW)), by + 12, 0xFF2E7D32);
            int cx = bx + (int) (bw * pos);
            ctx.fill(cx - 1, by - 4, cx + 2, by + 16, 0xFF26C6DA);
            for (int i = 0; i < 3; i++) ctx.fill(x + w / 2 - 20 + i * 15, by + 24, x + w / 2 - 10 + i * 15, by + 30, i < hits ? 0xFF26C6DA : 0xFF263238);
            center(ctx, "Пробел — в зелёное", x + w / 2, by - 18, 0xFFB2EBF2);
            return;
        }
        NbtList l = nodes.getList("nodes", NbtElement.COMPOUND_TYPE);
        if (l.isEmpty()) {
            center(ctx, nodes.contains("level") ? "Рядом нет узлов" : "…", x + w / 2, y + h / 2, 0xFFB0BEC5);
            return;
        }
        int ry = y + 30;
        for (int i = 0; i < l.size(); i++) {
            NbtCompound c = l.getCompound(i);
            int d = c.getInt("diff");
            final String id = c.getString("id");
            button(ctx, x + 14, ry, w - 28, 18, c.getString("name") + (d == 0 ? " · сегодня уже" : ""), 0xFF00838F, d > 0, () -> begin(id, d));
            ry += 22;
        }
        center(ctx, "Esc — закрыть", x + w / 2, y + h - 11, 0xFF607D8B);
    }
}
