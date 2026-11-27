package com.yompix.novograd.client.guide;

import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/**
 * 0.9.6: телефон → «Гид» — что можно делать в Новограде: разделы слева, в каждом — значок и одна строка на дело.
 * Минимум текста: название и где/как (клавиша, приложение).
 */
public class GuideScreen extends LifeScreen {
    /** Раздел: название, цвет, значок (символ) и строки «что — где». */
    record Cat(String name, int color, String icon, String[][] items) {}

    static final Cat[] CATS = {
            new Cat("Сюжет", 0xFFFFB300, "★", new String[][]{{"Миссии", "жёлтая «М» на карте"}, {"Задания", "телефон → Задания"},
                    {"Звонки героев", "Y — ответить, N — сбросить"}, {"Тёмные дела", "телефон → Дела: ограбления, заказы"}}),
            new Cat("Работа", 0xFF5A6ACF, "$", new String[][]{{"Подработки", "телефон → Работа"}, {"Такси, патруль", "в машине — J"},
                    {"Скорая, пожарные, пицца", "служебная машина — J"}, {"Диджей в «Неоне»", "вечером, пульт в клубе"},
                    {"Профессии и звания", "телефон → Карьера"}, {"Служба: полиция, 03, 01", "телефон → Служба"}, {"Свой бизнес", "телефон → Бизнес"}}),
            new Cat("Деньги", 0xFF2E7D32, "₽", new String[][]{{"Наличные и карта", "банкомат: снять / положить"},
                    {"Грязные деньги", "только наличными, отмыть — бизнес или Рэй"}, {"Тайник дома", "телефон → Дом"},
                    {"Счета и цены", "телефон → Финансы"}, {"Биржа", "телефон → Финансы"}}),
            new Cat("Бизнес", 0xFFC99A2E, "⌂", new String[][]{{"Купить бизнес", "телефон → Империя"}, {"Выручка", "сейф бизнеса, раз в день"},
                    {"Управляющий, охрана", "телефон → Империя"}, {"Рейды банд", "метка на карте, 3 минуты"}}),
            new Cat("Империя", 0xFFD42A3A, "♛", new String[][]{{"Районы", "отбей район у банды"}, {"Дома и виллы", "телефон → Дом"},
                    {"Цели", "миллион, Стрип, все районы"}}),
            new Cat("Сеть", 0xFF3FB8B0, "⌁", new String[][]{{"Светофоры", "телефон → Сеть"}, {"Камеры, банкоматы", "телефон → Сеть"},
                    {"Свет района", "блэкаут"}}),
            new Cat("Гонки", 0xFFE05A47, "⚑", new String[][]{{"Уличные гонки", "метки на карте"}, {"Рекорды трасс", "телефон → Прогресс"},
                    {"Тюнинг", "мастерская: нитро, двигатель, броня"}, {"Мои машины", "телефон → Авто"}}),
            new Cat("Спортзал", 0xFFFFC428, "♥", new String[][]{{"Сила, выносливость", "Зал «Железо»"}, {"Вес", "еда — плюс, спорт — минус"},
                    {"Тело", "видно на герое: худое — плотное"}}),
            new Cat("Внешность", 0xFFB96890, "☺", new String[][]{{"Редактор", "телефон → Внешность"}, {"Одежда", "Шмот-Базар, «Бархат»"},
                    {"Стрижки", "Цирюльня"}, {"Образы", "сохрани до 6 образов"}}),
            new Cat("Розыск", 0xFF3F7FC0, "!", new String[][]{{"Свидетели", "звонят в полицию, если видели"}, {"Уйти", "скройся из вида — круг поиска"},
                    {"Маски", "K — надеть, меньше звёзд"}, {"Тюрьма", "срок, взятка, адвокат, побег"}}),
            new Cat("События", 0xFF8E24AA, "✦", new String[][]{{"Блэкаут, шторм", "город сам, раз в полчаса"}, {"Ночь музыки", "парк"},
                    {"Праздники", "Новый год, Хэллоуин, День города"}}),
            new Cat("Онлайн", 0xFF6A5F80, "☻", new String[][]{{"С друзьями", "пауза → Открыть для друзей"}, {"Копы и воры", "телефон → Копы и воры"},
                    {"Передать машину", "телефон → Авто"}}),
            new Cat("Голос", 0xFF26A69A, "♪", new String[][]{{"Говорить", "зажми V"}, {"Рядом", "слышат в 48 блоках"},
                    {"Звонок, рация", "телефон → Рация"}, {"Микрофон, громкость", "Рация → Настройки голоса"}}),
    };

    private int cat, scroll;

    public GuideScreen() {
        super("Гид");
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int top = 8 + (int) ((1 - a) * -16);
        title(ctx, "ЧТО МОЖНО ДЕЛАТЬ", 12, top, Ui.GOLD);
        int lw = Math.min(110, width / 3), y0 = top + 30, rowH = 14;
        int visible = Math.max(1, (height - y0 - 24) / rowH);
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, CATS.length - visible));
        for (int i = scroll; i < CATS.length && i < scroll + visible; i++) {
            Cat c = CATS[i];
            int y = y0 + (i - scroll) * rowH;
            boolean on = i == cat;
            Ui.slant(ctx, 8, y, lw, rowH - 2, 3, on ? c.color() : 0xA0161615);
            ctx.drawText(textRenderer, c.icon(), 14, y + 2, on ? Ui.WHITE : c.color(), false);
            ctx.drawText(textRenderer, c.name(), 28, y + 2, on ? Ui.WHITE : 0xFFDDDBD4, on);
            final int k = i;
            area(8, y, lw, rowH - 2, () -> cat = k);
        }
        Cat c = CATS[cat];
        int px = 16 + lw, pw = width - px - 10;
        Ui.panel(ctx, px, y0 - 4, pw, height - y0 - 20, Ui.alpha(c.color(), a));
        Ui.big(ctx, textRenderer, c.icon() + "  " + c.name().toUpperCase(), px + 8, y0 + 2, 1.3f, c.color(), 0);
        int y = y0 + 22;
        for (String[] it : c.items()) {
            if (y > height - 36) break;
            ctx.fill(px + 8, y + 3, px + 12, y + 7, c.color());
            ctx.drawText(textRenderer, it[0], px + 17, y, Ui.WHITE, true);
            Ui.small(ctx, textRenderer, it[1], px + 17, y + 10, 0.8f, 0xFFB9B7B1, 0);
            y += 24;
        }
        ctx.fill(0, height - 18, width, height, 0xE0040404);
        String back = "‹ НАЗАД";
        int bw = textRenderer.getWidth(back) + 16;
        Ui.slant(ctx, width - bw - 8, height - 16, bw, 14, 4, hover(width - bw - 8, height - 17, bw, 17) ? Ui.PINK : 0x60FFFFFF);
        ctx.drawText(textRenderer, back, width - bw, height - 13, 0xFFFFFFFF, false);
        area(width - bw - 8, height - 17, bw, 17, this::close);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll -= (int) Math.signum(v);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
            cat = Math.min(CATS.length - 1, cat + 1);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_W) {
            cat = Math.max(0, cat - 1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
