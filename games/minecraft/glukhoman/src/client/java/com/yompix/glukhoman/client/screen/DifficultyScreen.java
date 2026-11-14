package com.yompix.glukhoman.client.screen;

import com.yompix.glukhoman.client.GlukhomanClient;
import com.yompix.glukhoman.net.Net;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/** Первый запуск мира: заставка и выбор сложности (выбирает тот, кто зашёл первым). */
public class DifficultyScreen extends Screen {
    static final String[][] D = {
            {"Жутко", "Мягче: Обходчик только вырывает плёнку, поезд стоит у перрона меньше."},
            {"Страшно", "Как задумано. Рекомендуем."},
            {"Кошмар", "Обходчик учится быстрее, ошибки дорого стоят."}};

    public DifficultyScreen() {
        super(Text.literal("Глухомань"));
    }

    @Override
    protected void init() {
        int y = height / 2 + 4;
        for (int i = 0; i < 3; i++) {
            final int d = i;
            addDrawableChild(new com.yompix.glukhoman.client.ui.Kit.Item(width / 2 - 75, y + i * 18, 150, D[i][0], i, b -> {   // стиль интерфейса мода (этап 5)
                NbtCompound m = Net.msg("difficulty");
                m.putInt("d", d);
                GlukhomanClient.send(m);
                close();
            }));
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xF0050506);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f, height / 2f - 70, 0);
        ctx.getMatrices().scale(4f, 4f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, "ГЛУХОМАНЬ", 0, 0, 0xFFD8D0C0);
        ctx.getMatrices().scale(0.4f, 0.4f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, "ПОРОЖНЯК · часть первая", 0, 26, 0xFFB04040);
        ctx.getMatrices().pop();
        ctx.drawCenteredTextWithShadow(textRenderer, "Октябрь 1996-го. Мёртвая узкоколейка «Ветка 41». Тебя наняли сторожить и снимать её каждую ночь.", width / 2, height / 2 - 30, 0xFF9A9A9A);
        ctx.drawCenteredTextWithShadow(textRenderer, "ПКМ с камерой — к глазу. В камере видно то, чего нет глазами. Играй в наушниках:", width / 2, height / 2 - 14, 0xFF9A9A9A);
        ctx.drawCenteredTextWithShadow(textRenderer, "Yompix Games · не связано с Mojang", width / 2, height - 14, 0xFF555555);
        super.render(ctx, mx, my, delta);
        for (var el : children())   // описание сложности — под пунктами, для того, на который навели
            if (el instanceof com.yompix.glukhoman.client.ui.Kit.Item it && (it.isHovered() || it.isFocused()))
                for (int i = 0; i < 3; i++) if (it.getMessage().getString().equals(D[i][0]))
                    ctx.drawCenteredTextWithShadow(textRenderer, D[i][1], width / 2, height / 2 + 64, 0xFFA8A090);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
