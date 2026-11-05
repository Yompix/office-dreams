package com.yompix.novograd.client.story;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import net.minecraft.client.gui.DrawContext;

/** Титры после финала. */
public class CreditsScreen extends LifeScreen {
    private static final String[][] LINES = {
            {"T", "НОВОГРАД"}, {"", "симулятор жизни для Minecraft"}, {"", ""},
            {"H", "Студия"}, {"", "Yompix Games"}, {"", ""},
            {"H", "Идея и продюсер"}, {"", "Yompix"}, {"", ""},
            {"H", "Сценарий, код, графика"}, {"", "Yompix Games и Claude"}, {"", ""},
            {"H", "В ролях"}, {"", "Тёма · Лиза · Глеб · Мама · Папа"}, {"", "Марья Ивановна · Лейтенант Петров · Барон"}, {"", ""},
            {"H", "Озвучка"}, {"", "нейросеть XTTS-v2 (Coqui)"}, {"", ""},
            {"H", "Город"}, {"", "Новоград и все его жители"}, {"", ""}, {"", ""},
            {"T", "Спасибо, что сыграл!"}, {"", "Сюжет пройден — город твой."}};
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
