package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.lwjgl.glfw.GLFW;

/** Телефон → «Новости»: лента канала «Новоград-24» — твои дела глазами города, биржа и жизнь острова. */
public class NewsScreen extends LifeScreen {
    public static NbtCompound feed = new NbtCompound();
    private int scroll;

    public NewsScreen() {
        super("Новости");
        NovogradClient.send("news_feed");
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = Math.min(440, width - 30), h = Math.min(300, height - 30);
        int x = width / 2 - w / 2, y = height / 2 - h / 2;
        panel(ctx, x, y, w, h, 0xF0101418, 0xF0080A0D);
        title(ctx, "НОВОГРАД-24", x + 10, y + 8, 0xFFFF4040);
        ctx.drawText(textRenderer, "город говорит о тебе", x + w - 10 - textRenderer.getWidth("город говорит о тебе"), y + 11, 0xFF90A4AE, false);
        var list = feed.getList("feed", NbtElement.COMPOUND_TYPE);
        int ry = y + 30;
        if (list.isEmpty()) wrap(ctx, "Пока тихо. Сделай что-нибудь громкое — и город заговорит.", x + 14, ry + 10, w - 28, 0xFFB0BEC5);
        for (int i = scroll; i < list.size() && ry < y + h - 30; i++) {
            NbtCompound c = list.getCompound(i);
            String cat = c.getString("c");
            int col = cat.equals("БИРЖА") ? 0xFF2E7D32 : cat.equals("ГОРОД") ? 0xFF1565C0 : 0xFFD32F2F;
            ctx.fill(x + 10, ry, x + 13, ry + 22, col);
            ctx.drawText(textRenderer, c.getString("t") + "  " + cat, x + 18, ry, 0xFF90A4AE, false);
            int used = wrap(ctx, c.getString("h"), x + 18, ry + 10, w - 34, 0xFFFFFFFF);
            ry += Math.max(26, used + 14);
        }
        center(ctx, "колесо — листать · Esc — закрыть", x + w / 2, y + h - 12, 0xFF607D8B);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        int n = feed.getList("feed", NbtElement.COMPOUND_TYPE).size();
        scroll = Math.max(0, Math.min(Math.max(0, n - 1), scroll + (vy < 0 ? 1 : -1)));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
