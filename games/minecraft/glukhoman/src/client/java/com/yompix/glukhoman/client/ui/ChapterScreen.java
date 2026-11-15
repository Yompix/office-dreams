package com.yompix.glukhoman.client.ui;

import com.yompix.glukhoman.client.GConfig;
import com.yompix.glukhoman.story.Texts;
import net.minecraft.client.gui.screen.Screen;

/**
 * «Главы» (этап 5): список глав с отметкой, до какой дошли (по карточкам глав — `GConfig.chapterReached`).
 * Пройденные — «Продолжить» последнюю смену; запуск с произвольной главы требует серверной команды сюжета
 * (перемотка StoryState) — не сделан, такие пункты неактивны с пометкой.
 */
public class ChapterScreen extends KitScreen {
    public ChapterScreen(Screen parent) {
        super("Главы", parent, false);
        sub = "Часть первая";
    }

    @Override
    protected int rows() {
        return 9;
    }

    @Override
    protected void build() {
        int reached = GConfig.get().chapterReached;
        for (int n = 1; n <= 6; n++) {
            String t = Texts.get().chapterTitle(n);
            if (t.isEmpty()) continue;
            final int ch = n;
            if (n == 1) item(t, b -> com.yompix.glukhoman.client.menu.WorldStart.newShift());
            else if ((n == 2 || n == 4 || n == 5) && n <= reached) {   // новая смена с начала главы (прошлое «пройдено» на сервере)
                item(t, b -> {
                    com.yompix.glukhoman.client.menu.WorldStart.pendingChapter = ch;
                    com.yompix.glukhoman.client.menu.WorldStart.newShift();
                });
            } else if (n == 3 || n == 6) item(t, b -> {}).off(n <= reached ? "конец части" : "ещё не дошли");
            else item(t, b -> {}).off("ещё не дошли");
        }
        gap();
        var cont = item("Продолжить последнюю смену", b -> com.yompix.glukhoman.client.menu.WorldStart.continueLatest());
        if (!com.yompix.glukhoman.client.menu.WorldStart.hasSave(client)) cont.off("нет сохранения");
        item("Назад", b -> close());
    }
}
