package com.yompix.glukhoman.client.screen;

import com.yompix.glukhoman.client.GConfig;
import com.yompix.glukhoman.client.GlukhomanClient;
import com.yompix.glukhoman.client.ui.Kit;
import com.yompix.glukhoman.client.ui.KitScreen;
import com.yompix.glukhoman.client.ui.StoryLang;
import com.yompix.glukhoman.client.ui.Subtitles;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.sound.SoundCategory;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Настройки (этап 7): вкладки «Эффекты», «Звук», «Управление», «Доступность», «Игра». Здесь только то, что реально
 * применяется (каждый пункт читается в коде каждый кадр — меняется сразу, без перезапуска); сохраняется при каждом
 * изменении (GConfig / options.txt). Две колонки на широком экране, одна — на узком; низ — «Готово».
 */
public class SettingsScreen extends KitScreen {
    public static int tab;
    static final String[] TABS = {"Эффекты", "Звук", "Управление", "Доступность", "Игра"};
    int col, row, x0, colW2, top, rowsPerCol;

    public SettingsScreen(Screen parent) {
        super("Настройки", parent, parent != null && net.minecraft.client.MinecraftClient.getInstance().world != null);
    }

    @Override
    protected int rows() {
        return 9;
    }

    @Override
    protected void build() {
        GConfig c = GConfig.get();
        var o = client.options;
        // вкладки
        int tw = Math.min(96, (width - 40) / TABS.length);
        int tx = width / 2 - tw * TABS.length / 2;
        top = height < 300 ? 26 : Math.max(50, height / 2 - 120);
        showTitle = height >= 300;   // на маленьком экране (интерфейс ×4 на 1080p) заголовок не помещается — вкладки сразу сверху
        for (int i = 0; i < TABS.length; i++) {
            final int k = i;
            var it = new Kit.Item(tx + i * tw, top, tw, (i == tab ? "[" : "") + TABS[i] + (i == tab ? "]" : ""), order++, b -> {
                tab = k;
                clearAndInit();
            });
            addDrawableChild(it);
        }
        boolean two = width >= 300;
        colW2 = two ? Math.min(210, (width - 48) / 2) : Math.min(260, width - 40);
        x0 = two ? width / 2 - colW2 - 8 : width / 2 - colW2 / 2;
        if (height < 300) top -= 8;
        rowsPerCol = two ? 6 : 12;
        col = row = 0;
        switch (tab) {
            case 0 -> {
                cycle("Эффекты экрана", new String[]{"выкл", "слабо", "полностью"}, () -> c.screenFx, v -> c.screenFx = v);
                slider("Сила плёнки (VHS)", 0, 100, () -> c.vhsStrength, v -> c.vhsStrength = v, "%");
                toggle("Плёнка и глазами", () -> c.vhs, v -> c.vhs = v);
                toggle("Помехи и срывы кадра", () -> c.fxGlitch, v -> c.fxGlitch = v);
                toggle("Размытие и лучи в сценах", () -> c.fxBlur, v -> c.fxBlur = v);
                toggle("Разрыв кадра", () -> c.fxTear, v -> c.fxTear = v);
                cycle("Яркость", new String[]{"темно (как задумано)", "обычно", "светлее"}, () -> c.brightness, v -> c.brightness = v);
                slider("Покачивание камеры", 0, 100, () -> c.handSway, v -> c.handSway = v, "%");
                slider("Дрожь камеры в сценах", 0, 100, () -> c.cineShake, v -> c.cineShake = v, "%");
                toggle("Живая анимация персонажей", () -> c.liveAnim, v -> c.liveAnim = v);
                cycle("Масштаб интерфейса", guiScales(), () -> o.getGuiScale().getValue(), v -> {
                    o.getGuiScale().setValue(v);
                    o.write();
                    client.onResolutionChanged();
                });
            }
            case 1 -> {
                vol("Общая громкость", SoundCategory.MASTER);
                vol("Окружение (ветер, гул)", SoundCategory.AMBIENT);
                vol("Голоса и рация", SoundCategory.VOICE);
                vol("Существа", SoundCategory.HOSTILE);
                vol("Жители и поезд", SoundCategory.NEUTRAL);
                vol("Шаги и блоки", SoundCategory.BLOCKS);
                vol("Музыка", SoundCategory.MUSIC);
                toggle("Сердцебиение", () -> c.heartbeat, v -> c.heartbeat = v);
            }
            case 2 -> {
                optSlider("Чувствительность мыши", o.getMouseSensitivity());
                boolOpt("Инверсия мыши", o.getInvertYMouse());
                item2("Назначить клавиши…", () -> client.setScreen(new net.minecraft.client.gui.screen.option.KeybindsScreen(this, o)));
                item2("Все настройки игры…", () -> client.setScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(this, o)));
            }
            case 3 -> {
                toggle("Субтитры", () -> c.subtitles, v -> c.subtitles = v);
                slider("Размер субтитров", 75, 200, () -> c.subSize, v -> c.subSize = v, "%");
                slider("Фон под субтитрами", 0, 100, () -> c.subBg, v -> c.subBg = v, "%");
                cycle("Положение", new String[]{"внизу", "выше"}, () -> c.subPos, v -> c.subPos = v);
                cycle("Стиль", new String[]{"плёнка", "чистый"}, () -> c.subStyle, v -> c.subStyle = v);
                toggle("Высокий контраст текста", () -> c.subContrast, v -> c.subContrast = v);
                cycle("Язык субтитров", StoryLang.NAMES, () -> java.util.Arrays.asList(StoryLang.LANGS).indexOf(c.subLang) < 0 ? 0
                        : java.util.Arrays.asList(StoryLang.LANGS).indexOf(c.subLang), v -> c.subLang = StoryLang.LANGS[v]);
                toggle("Без тряски", () -> c.noShake, v -> c.noShake = v);
                toggle("Без вспышек и мигания", () -> c.noFlashing, v -> c.noFlashing = v);
                toggle("Меньше скримеров", () -> c.fewerScares, v -> c.fewerScares = v);
            }
            default -> {
                toggle("Подсказка направления (H)", () -> c.hints, v -> c.hints = v);
                toggle("Цель на экране", () -> c.hudObjective, v -> c.hudObjective = v);
                toggle("Свои пауза и экран провала", () -> c.kitUi, v -> c.kitUi = v);
                toggle("Ложный вылет игры в финале", () -> c.fakeCrash, v -> c.fakeCrash = v);
                toggle("Без крови на экране", () -> c.noBlood, v -> c.noBlood = v);
                toggle("Жестокость: меньше", () -> c.goreReduced, v -> {
                    c.goreReduced = v;
                    GlukhomanClient.applyGore(client);
                });
            }
        }
        addDrawableChild(new Kit.Item(width / 2 - 60, Math.min(height - 30, top + 34 + rowsPerCol * 26 + 6), 120, "Готово", order++, b -> close()));
    }

    // ---------- раскладка ----------
    int[] next(int h) {
        if (row >= rowsPerCol) {
            row = 0;
            col++;
        }
        int x = x0 + col * (colW2 + 16), y = top + 34 + row * 26;
        row++;
        return new int[]{x, y};
    }

    void toggle(String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        int[] p = next(26);
        var it = new Kit.Item(p[0], p[1], colW2, name + ": " + (get.get() ? "да" : "нет"), order++, b -> {
            set.accept(!get.get());
            b.setMessage(net.minecraft.text.Text.literal(name + ": " + (get.get() ? "да" : "нет")));
            GConfig.get().save();
            GlukhomanClient.sendPrefs();
        });
        it.center = false;
        addDrawableChild(it);
    }

    void cycle(String name, String[] vals, IntSupplier get, Consumer<Integer> set) {
        int[] p = next(26);
        var it = new Kit.Item(p[0], p[1], colW2, name + ": " + vals[Math.max(0, Math.min(vals.length - 1, get.getAsInt()))], order++, b -> {
            int v = (get.getAsInt() + 1) % vals.length;
            set.accept(v);
            b.setMessage(net.minecraft.text.Text.literal(name + ": " + vals[v]));
            GConfig.get().save();
        });
        it.center = false;
        addDrawableChild(it);
    }

    void item2(String name, Runnable r) {
        int[] p = next(26);
        var it = new Kit.Item(p[0], p[1], colW2, name, order++, b -> r.run());
        it.center = false;
        addDrawableChild(it);
    }

    void slider(String name, int min, int max, IntSupplier get, Consumer<Integer> set, String unit) {
        int[] p = next(26);
        addDrawableChild(new Kit.Slider(p[0], p[1] + 8, colW2, name + ": " + get.getAsInt() + unit, (get.getAsInt() - min) / (double) (max - min)) {
            @Override
            protected void updateMessage() {
                setMessage(net.minecraft.text.Text.literal(name + ": " + (int) Math.round(min + value * (max - min)) + unit));
            }

            @Override
            protected void applyValue() {
                set.accept((int) Math.round(min + value * (max - min)));
                GConfig.get().save();
            }
        });
    }

    void vol(String name, SoundCategory cat) {
        optSlider(name, client.options.getSoundVolumeOption(cat));
    }

    void optSlider(String name, SimpleOption<Double> opt) {
        int[] p = next(26);
        addDrawableChild(new Kit.Slider(p[0], p[1] + 8, colW2, name + ": " + Math.round(opt.getValue() * 100) + "%", opt.getValue()) {
            @Override
            protected void updateMessage() {
                setMessage(net.minecraft.text.Text.literal(name + ": " + Math.round(value * 100) + "%"));
            }

            @Override
            protected void applyValue() {
                opt.setValue(value);
                client.options.write();
            }
        });
    }

    void boolOpt(String name, SimpleOption<Boolean> opt) {
        toggle(name, opt::getValue, v -> {
            opt.setValue(v);
            client.options.write();
        });
    }

    String[] guiScales() {
        int max = client.getWindow().calculateScaleFactor(0, client.forcesUnicodeFont());
        String[] r = new String[max + 1];
        r[0] = "авто";
        for (int i = 1; i <= max; i++) r[i] = Integer.toString(i);
        return r;
    }

    @Override
    protected int colY0() {
        return top + 16;
    }

    @Override
    protected void extra(DrawContext ctx, int mx, int my, double t) {
        if (tab == 3) {   // живой пример субтитров
            if (Subtitles.visible(Subtitles.now()).isEmpty())
                Subtitles.push("zakhar", StoryLang.who("zakhar", "Захар Ильич"), StoryLang.text("talk_zakhar", "Табло горит без тока. Не спрашивай меня, сторож."), 2.5);
            Subtitles.render(ctx, textRenderer, width, height, 8);
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
    }
}
