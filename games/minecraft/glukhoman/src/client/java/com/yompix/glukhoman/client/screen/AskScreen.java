package com.yompix.glukhoman.client.screen;

import com.yompix.glukhoman.client.GlukhomanClient;
import com.yompix.glukhoman.net.Net;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.List;

/** Решение (ящик: сдать или оставить кассету). Сервер присылает вопрос и варианты, ответ уходит номером. */
public class AskScreen extends Screen {
    final String id, question;
    final List<String> opts;

    public AskScreen(String id, String question, List<String> opts) {
        super(Text.literal("Решение"));
        this.id = id;
        this.question = question;
        this.opts = opts;
    }

    @Override
    protected void init() {
        int y = height / 2 + 6;
        for (int i = 0; i < opts.size(); i++) {
            final int k = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(opts.get(i)), b -> pick(k)).dimensions(width / 2 - 170, y + i * 24, 340, 20).build());
        }
    }

    void pick(int i) {
        NbtCompound m = Net.msg("answer");
        m.putString("id", id);
        m.putInt("i", i);
        GlukhomanClient.send(m);
        close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        var lines = textRenderer.wrapLines(Text.literal(question), Math.min(400, width - 40));
        int y = height / 2 - 14 - lines.size() * 11;
        for (var l : lines) {
            ctx.drawCenteredTextWithShadow(textRenderer, l, width / 2, y, 0xFFD8D0C0);
            y += 11;
        }
        super.render(ctx, mx, my, delta);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }
}
