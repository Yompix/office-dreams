package com.yompix.novograd.client.city;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.MathHelper;

/** 0.9: телефон → «Прогресс»: проценты разделов и общий процент (данные — gta/Progress). */
public class ProgressScreen extends LifeScreen {
    public static NbtCompound state = new NbtCompound();

    public ProgressScreen() {
        super("Прогресс");
        state = new NbtCompound();
        NovogradClient.send("progress_state");
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = Math.min(420, width - 24), h = Math.min(300, height - 20);
        int x = width / 2 - w / 2, y = height / 2 - h / 2;
        panel(ctx, x, y, w, h, 0xF0101418, 0xF0080808);
        title(ctx, "ПРОГРЕСС", x + 10, y + 8, 0xFFFFC107);
        NbtList l = state.getList("parts", NbtElement.COMPOUND_TYPE);
        if (l.isEmpty()) {
            center(ctx, "…", x + w / 2, y + h / 2, 0xFFB0BEC5);
            return;
        }
        int pct = state.getInt("pct");
        String big = pct + "%";
        bigText(ctx, big, x + w - 14 - (int) (textRenderer.getWidth(big) * 1.2f), y + 6, 2.4f, pct >= 100 ? 0xFF7CFF7C : 0xFFFFFFFF);
        int top = y + 34, row = MathHelper.clamp((h - 56) / l.size(), 13, 20);
        int bx = x + 112, bw = w - 112 - 62;
        for (int i = 0; i < l.size(); i++) {
            NbtCompound c = l.getCompound(i);
            int ry = top + i * row, d = c.getInt("done"), t = Math.max(1, c.getInt("total"));
            float k = d / (float) t;
            ctx.drawText(textRenderer, c.getString("name"), x + 12, ry, 0xFFE0E0E0, false);
            ctx.fill(bx, ry + 2, bx + bw, ry + 7, 0xFF262A33);
            ctx.fill(bx, ry + 2, bx + (int) (bw * k), ry + 7, k >= 1 ? 0xFF43A047 : 0xFFFFB300);
            String s = (int) (k * 100) + "%";
            ctx.drawText(textRenderer, s, x + w - 12 - textRenderer.getWidth(s), ry, k >= 1 ? 0xFF7CFF7C : 0xFFB0BEC5, false);
        }
        center(ctx, "50% — $50 000 · 100% — ключ от города", x + w / 2, y + h - 22, 0xFF90A4AE);
        center(ctx, "Esc — закрыть", x + w / 2, y + h - 11, 0xFF607D8B);
    }
}
