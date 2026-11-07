package com.yompix.novograd.client.story;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import net.minecraft.client.gui.DrawContext;

/** Титры после финала. */
public class CreditsScreen extends LifeScreen {
    private static final String[][] LINES = {
            {"T", "НОВОГРАД"}, {"", "Тёмные улицы"}, {"", ""},
            {"H", "Создатель и сценарист"}, {"B", "Yompix"}, {"", ""},
            {"H", "Студия"}, {"", "Yompix Games"}, {"", ""},
            {"H", "Идея, сюжет, персонажи, мир"}, {"", "Yompix"}, {"", ""},
            {"H", "Программирование и графика"}, {"", "Claude (Anthropic) по сценарию Yompix"}, {"", ""},
            {"H", "В ролях"}, {"", "Джейк · Ники «Нитро» · Сторм · Брик"}, {"", "Лейтенант Питерсон · Роза · Винс · Марко «Синий»"},
            {"", "Шеф Харрис · Лена Вуд · Виктор Кроу · Софи · Рэй · Шакал"}, {"", ""},
            {"H", "Голоса"}, {"", "нейросеть XTTS-v2 (Coqui)"}, {"", ""},
            {"H", "Радио Новограда"}, {"", "НЕОН 88 FM · БИТ УЛИЦ · НОЧНОЙ ПОРТ · ТРАССА 128 · РОК-ВОЛНА"},
            {"", "ДЖАЗ «ПОЛНОЧЬ» · ПАЛЬМА FM · КЛАССИКА · КАНТРИ-РОУД · ТЕХНО-ПОДВАЛ"},
            {"", "Музыка: Kevin MacLeod (incompetech.com)"}, {"", "Licensed under Creative Commons: By Attribution 4.0"}, {"", ""},
            {"H", "Город"}, {"", "Новоград, Ривьера и все их жители"}, {"", ""}, {"", ""},
            {"T", "Спасибо, что сыграл!"}, {"", "Сюжет пройден — Новоград твой."}, {"", "© Yompix Games"}};
    private boolean sent;

    public CreditsScreen() {
        super("Титры");
    }

    @Override
    protected void background(DrawContext ctx) {
        ctx.fill(0, 0, width, height, 0xF0000000);
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float y = height - seconds() * 22;
        for (String[] l : LINES) {
            if (l[0].equals("T")) {
                bigText(ctx, l[1], width / 2, (int) y, 2.4f, 0xFFFFD54F);
                y += 30;
            } else if (l[0].equals("B")) {
                bigText(ctx, l[1], width / 2, (int) y, 1.8f, 0xFFFF4FD8);
                y += 24;
            } else if (l[0].equals("H")) {
                center(ctx, l[1], width / 2, (int) y, 0xFF8FC3FF);
                y += 12;
            } else {
                center(ctx, l[1], width / 2, (int) y, 0xFFE0E0E0);
                y += 12;
            }
        }
        if (y < -20) close();
        if (seconds() > 3) ctx.drawText(textRenderer, "Esc — пропустить", 6, height - 12, 0x66FFFFFF, false);
    }

    @Override
    public void close() {
        if (!sent) {
            sent = true;
            NovogradClient.send("credits_done");
        }
        super.close();
    }
}
