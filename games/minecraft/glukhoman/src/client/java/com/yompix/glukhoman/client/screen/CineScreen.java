package com.yompix.glukhoman.client.screen;

import com.yompix.glukhoman.client.Cine;
import com.yompix.glukhoman.client.GlukhomanClient;
import com.yompix.glukhoman.net.Net;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Поверх кат-сцены: киношные полосы, подпись, субтитры, «Esc — пропустить». Ввод игрока заблокирован. */
public class CineScreen extends Screen {
    boolean skipped;

    public CineScreen() {
        super(Text.literal("Кат-сцена"));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        int bar = (int) (height * 0.12f * Cine.letter);
        ctx.fill(0, 0, width, bar, 0xFF000000);
        ctx.fill(0, height - bar, width, height, 0xFF000000);
        if (!Cine.caption.isEmpty()) {
            boolean title = Cine.caption.equals("ГЛУХОМАНЬ");
            if (title) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(width / 2f, height / 2f - 10, 0);
                ctx.getMatrices().scale(4f, 4f, 1f);
                ctx.drawCenteredTextWithShadow(textRenderer, Cine.caption, 0, 0, 0xFFE6E0D0);
                ctx.getMatrices().pop();
            } else ctx.drawCenteredTextWithShadow(textRenderer, Cine.caption, width / 2, height - bar - 30, 0xFFE8D9A8);
        }
        int y = height - bar + 6;
        for (int i = Math.max(0, GlukhomanClient.SUBS.size() - 2); i < GlukhomanClient.SUBS.size(); i++) {
            String[] s = GlukhomanClient.SUBS.get(i);
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal((s[0].isEmpty() ? "" : s[0] + ": ") + s[1]), width - 60);
            for (OrderedText l : lines) {
                ctx.drawCenteredTextWithShadow(textRenderer, l, width / 2, y, 0xFFE0E0E0);
                y += 10;
            }
        }
        com.yompix.glukhoman.client.Jumpscare.render(ctx, width, height);
        com.yompix.glukhoman.client.Jumpscare.renderSplat(ctx, width, height);
        ctx.drawTextWithShadow(textRenderer, skipped ? "пропуск…" : "Esc — пропустить", width - 96, 6, 0x60A0A0A0);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && !skipped) {
            skipped = true;
            GlukhomanClient.send(Net.msg("skip"));
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (!Cine.active) super.close();
    }
}
