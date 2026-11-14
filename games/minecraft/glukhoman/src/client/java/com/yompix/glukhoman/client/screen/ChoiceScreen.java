package com.yompix.glukhoman.client.screen;

import com.yompix.glukhoman.client.GlukhomanClient;
import com.yompix.glukhoman.net.Net;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/** Финальный выбор у передатчика. */
public class ChoiceScreen extends Screen {
    public ChoiceScreen() {
        super(Text.literal("Выбор"));
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Передать тишину"), b -> pick("A")).dimensions(width / 2 - 155, height / 2 + 20, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Ответить на голос"), b -> pick("B")).dimensions(width / 2 + 5, height / 2 + 20, 150, 20).build());
    }

    void pick(String c) {
        NbtCompound m = Net.msg("choice");
        m.putString("c", c);
        GlukhomanClient.send(m);
        close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xC0000000);
        ctx.drawCenteredTextWithShadow(textRenderer, "Камертон дрожит в руке. Из динамиков — голос Анны. И твой.", width / 2, height / 2 - 30, 0xFFD8B0B0);
        ctx.drawCenteredTextWithShadow(textRenderer, "«Останься с нами. Ответь мне».", width / 2, height / 2 - 14, 0xFFB0B0B0);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
