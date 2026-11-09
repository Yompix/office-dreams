package com.yompix.novograd.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayDeque;

/** «Новоград-24»: экстренный выпуск — плашка и бегущая строка сверху экрана. Очередь, если новостей несколько. */
public final class NewsHud {
    private NewsHud() {}

    private static final ArrayDeque<String[]> QUEUE = new ArrayDeque<>();
    private static String[] cur;
    private static long start;
    private static final long SHOW = 9000;

    public static void push(NbtCompound n) {
        QUEUE.add(new String[]{n.getString("h"), n.getString("t"), n.getString("c")});
        while (QUEUE.size() > 4) QUEUE.removeFirst();
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || LifeHud.cinematic(mc)) return;
        long now = System.currentTimeMillis();
        if (cur == null || now - start > SHOW) {
            cur = QUEUE.pollFirst();
            start = now;
            if (cur == null) return;
        }
        float age = (now - start) / 1000f, total = SHOW / 1000f;
        float a = Math.min(1, Math.min(age / 0.35f, (total - age) / 0.5f));
        if (a <= 0) return;
        var tr = mc.textRenderer;
        int W = ctx.getScaledWindowWidth();
        float K = NovogradConfig.hud();
        int h = 13, y = CarRadio.showing() ? 112 : 62; // под названием станции радио
        int al = (int) (a * 255) << 24;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(W / 2f, y, 0);
        ctx.getMatrices().scale(K, K, 1);
        ctx.getMatrices().translate(-W / 2f, -y, 0);
        int bw = Math.min(W - 40, 420), bx = W / 2 - bw / 2;
        String tag = cur[2].equals("БИРЖА") ? "БИРЖА" : cur[2].equals("ГОРОД") ? "НОВОСТИ" : "ЭКСТРЕННО";
        int tagCol = cur[2].equals("БИРЖА") ? 0x2E7D32 : cur[2].equals("ГОРОД") ? 0x1565C0 : 0xD32F2F;
        int tw = tr.getWidth(tag) + 10;
        ctx.fill(bx, y, bx + bw, y + h, (int) (a * 0xD0) << 24 | 0x0B0E16);
        ctx.fill(bx, y, bx + tw, y + h, al | tagCol);
        ctx.drawText(tr, tag, bx + 5, y + 3, al | 0xFFFFFF, false);
        String chan = "НОВОГРАД-24 · " + cur[1];
        int cw = tr.getWidth(chan);
        ctx.fill(bx, y + h, bx + cw + 10, y + h + 10, (int) (a * 0xC0) << 24 | 0x1A1F2E);
        ctx.drawText(tr, chan, bx + 5, y + h + 1, al | 0xFFC83D, false);
        // бегущая строка внутри плашки
        int lx = bx + tw + 6, lw = bw - tw - 10;
        // ножницы — в экранных координатах (масштаб K вокруг (W/2, y) на них не действует)
        ctx.enableScissor((int) (W / 2f + (lx - W / 2f) * K), y, (int) Math.ceil(W / 2f + (lx + lw - W / 2f) * K), y + (int) Math.ceil(h * K));
        int textW = tr.getWidth(cur[0]);
        int off = textW <= lw ? 0 : (int) Math.min(textW - lw + 20, Math.max(0, (age - 1.2f) * 38));
        ctx.drawText(tr, cur[0], lx - off, y + 3, al | 0xFFFFFF, false);
        ctx.disableScissor();
        ctx.getMatrices().pop();
    }
}
