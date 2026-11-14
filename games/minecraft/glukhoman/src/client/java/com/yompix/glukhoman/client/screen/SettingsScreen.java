package com.yompix.glukhoman.client.screen;

import com.yompix.glukhoman.client.GConfig;
import com.yompix.glukhoman.client.GlukhomanClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Настройки Глухомани: доступность и яркость. Сложность меняет хозяин мира командой /glukhoman difficulty. */
public class SettingsScreen extends Screen {
    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Глухомань — настройки"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        GConfig c = GConfig.get();
        int y = height / 4;
        toggle(y, "Меньше скримеров", () -> c.fewerScares, v -> c.fewerScares = v);
        toggle(y + 24, "Без вспышек и мигания", () -> c.noFlashing, v -> c.noFlashing = v);
        toggle(y + 48, "Субтитры", () -> c.subtitles, v -> c.subtitles = v);
        toggle(y + 72, "Сердцебиение", () -> c.heartbeat, v -> c.heartbeat = v);
        toggle(y + 96, "Подсказка направления (H)", () -> c.hints, v -> c.hints = v);
        String[] br = {"Темно (как задумано)", "Обычно", "Светлее"};
        addDrawableChild(ButtonWidget.builder(Text.literal("Яркость: " + br[c.brightness]), b -> {
            c.brightness = (c.brightness + 1) % 3;
            b.setMessage(Text.literal("Яркость: " + br[c.brightness]));
            c.save();
        }).dimensions(width / 2 - 110, y + 120, 220, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), b -> close()).dimensions(width / 2 - 60, y + 156, 120, 20).build());
    }

    void toggle(int y, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(Text.literal(name + ": " + (get.get() ? "да" : "нет")), b -> {
            set.accept(!get.get());
            b.setMessage(Text.literal(name + ": " + (get.get() ? "да" : "нет")));
            GConfig.get().save();
            GlukhomanClient.sendPrefs();
        }).dimensions(width / 2 - 110, y, 220, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 4 - 24, 0xFFE0D8C8);
        String[] d = {"Жутко", "Страшно", "Кошмар"};
        ctx.drawCenteredTextWithShadow(textRenderer, "Сложность мира: " + d[Math.max(0, Math.min(2, GlukhomanClient.difficulty))] + "  (R — фонарик, H — подсказка)",
                width / 2, height / 4 - 12, 0xFF909090);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
