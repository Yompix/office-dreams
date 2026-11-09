package com.yompix.novograd.client.cops;

import com.yompix.novograd.client.screen.LifeScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/** Одно окно для ларька масок, камеры в колонии и «Полицейских и воров»: строки текста и кнопки от сервера. */
public class CopsMenuScreen extends LifeScreen {
    private NbtCompound n;

    public CopsMenuScreen(NbtCompound n) {
        super(n.getString("title"));
        this.n = n;
    }

    public String kind() {
        return n.getString("kind");
    }

    public void update(NbtCompound n) {
        this.n = n;
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int accent = n.getInt("accent");
        NbtList lines = n.getList("lines", NbtElement.STRING_TYPE), buttons = n.getList("buttons", NbtElement.COMPOUND_TYPE);
        int w = Math.min(320, width - 20);
        int textH = 0;
        for (int i = 0; i < lines.size(); i++) textH += textRenderer.wrapLines(net.minecraft.text.Text.literal(lines.getString(i)), w - 20).size() * 10 + 2;
        int h = 34 + textH + 6 + buttons.size() * 18 + 8;
        int x = (width - w) / 2, y = Math.max(6, (height - h) / 2);
        float a = openAnim();
        y += (int) ((1 - a) * 20);
        panel(ctx, x, y, w, h, 0xFF181817, 0xFF0A0A0A);
        title(ctx, n.getString("title"), x + 10, y + 10, accent | 0xFF000000);
        int yy = y + 34;
        for (int i = 0; i < lines.size(); i++) yy += wrap(ctx, lines.getString(i), x + 10, yy, w - 20, 0xFFD8D6D0) + 2;
        yy += 6;
        for (int i = 0; i < buttons.size(); i++) {
            NbtCompound b = buttons.getCompound(i);
            String id = b.getString("id");
            button(ctx, x + 10, yy, w - 20, 15, b.getString("label"), b.getInt("color"), b.getBoolean("on") && !id.isEmpty(), () -> {
                if (id.equals("close")) {
                    close();
                    return;
                }
                NbtCompound q = new NbtCompound();
                q.putString("kind", kind());
                q.putString("id", id);
                CopsClient.send("menu", q);
            });
            yy += 18;
        }
    }
}
