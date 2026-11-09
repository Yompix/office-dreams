package com.yompix.novograd.client.screen.casino;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.screen.OnlineCasinoScreen;
import com.yompix.novograd.client.screen.RouletteScreen;
import com.yompix.novograd.client.screen.SlotsScreen;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Supplier;

/**
 * Лобби казино: плитки всех игр. Открывается из телефона («Фортуна.онлайн») и у крупье/дилера в залах («игровой стол»).
 */
public class CasinoLobbyScreen extends LifeScreen {
    private record Game(String name, String sub, String icon, int color, Supplier<Screen> open) {}

    private static final Game[] GAMES = {
            new Game("Краш", "Забери до взрыва", "↗", 0xFFE53935, () -> new OnlineCasinoScreen("crash")),
            new Game("Блэкджек", "21 против дилера", "♠", 0xFF2E7D32, () -> new OnlineCasinoScreen("bj")),
            new Game("Покер", "Джекпот-покер", "♦", 0xFF1565C0, PokerScreen::new),
            new Game("Колесо", "Колесо фортуны", "✪", 0xFFFFB300, WheelScreen::new),
            new Game("Кости", "Два кубика", "⚄", 0xFFD81B60, DiceScreen::new),
            new Game("Бега", "Тараканьи бега", "»", 0xFF8D6E63, RaceScreen::new),
            new Game("Плинко", "Шарик и колышки", "●", 0xFF00ACC1, PlinkoScreen::new),
            new Game("Мины", "Не наступи", "✹", 0xFF7CB342, MinesScreen::new),
            new Game("Автоматы", "Три барабана", "7", 0xFFC62828, SlotsScreen::new),
            new Game("Рулетка", "Европейская", "◎", 0xFF6D4C41, RouletteScreen::new),
            new Game("Монетка", "Орёл или решка", "◐", 0xFF90A4AE, () -> new OnlineCasinoScreen("coin")),
    };

    public CasinoLobbyScreen() {
        super("Казино");
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int w = Math.min(390, width - 12), h = Math.min(228, height - 10);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 30);
        panel(ctx, x, y, w, h, 0xFF2A0F3D, 0xFF080808);
        ctx.fill(x, y + 3, x + w, y + 21, 0xFF3A1257);
        long t = System.currentTimeMillis();
        Ui.big(ctx, textRenderer, "ФОРТУНА", x + 10, y + 6, 1.5f, (t / 400) % 2 == 0 ? Ui.GOLD : 0xFFFFF0A0, 0);
        ctx.drawText(textRenderer, "казино · игровые деньги", x + 82, y + 9, Ui.DIM, false);
        String bal = "$" + NovogradClient.data.money;
        ctx.drawText(textRenderer, bal, x + w - 8 - textRenderer.getWidth(bal), y + 9, Ui.MONEY, true);
        // бегущие огни под шапкой
        for (int i = 0; i < w / 6; i++) {
            boolean on = (i + t / 120) % 4 == 0;
            ctx.fill(x + 3 + i * 6, y + 22, x + 5 + i * 6, y + 24, on ? Ui.PINK : 0xFF3A1A40);
        }
        int cols = 4, gap = 5;
        int tw = (w - 12 - gap * (cols - 1)) / cols, th = Math.min(46, (h - 34 - gap * 2) / 3);
        for (int i = 0; i < GAMES.length; i++) {
            Game g = GAMES[i];
            int tx = x + 6 + (i % cols) * (tw + gap), ty = y + 28 + (i / cols) * (th + gap);
            float appear = Ui.easeOut(Math.min(1, (System.currentTimeMillis() - openedAt - i * 35) / 220f));
            if (appear <= 0) continue;
            ty += (int) ((1 - appear) * 10);
            boolean hov = hover(tx, ty, tw, th);
            ctx.fill(tx + 2, ty + 2, tx + tw + 2, ty + th + 2, 0x60000000);
            ctx.fillGradient(tx, ty, tx + tw, ty + th, mix(0xF00E0D0D, g.color(), hov ? 0.45f : 0.22f), 0xF0070707);
            ctx.fill(tx, ty, tx + tw, ty + 2, g.color());
            ctx.drawBorder(tx, ty, tw, th, hov ? lighten(g.color(), 0.3f) : Ui.alpha(g.color(), 0.5f));
            Ui.big(ctx, textRenderer, g.icon(), tx + tw - 13, ty + th / 2f - 8, 1.8f, Ui.alpha(lighten(g.color(), 0.3f), 0.9f), 1);
            Ui.big(ctx, textRenderer, g.name(), tx + 6, ty + 7, 1.15f, 0xFFFFFFFF, 0);
            Ui.small(ctx, textRenderer, g.sub(), tx + 6, ty + th - 11, 0.75f, Ui.DIM, 0);
            area(tx, ty, tw, th, () -> mc().setScreen(g.open().get()));
        }
        Ui.small(ctx, textRenderer, "Все исходы решает сервер · в среднем заведение оставляет себе 2–7%", x + w / 2f, y + h - 9, 0.7f, 0xFF84837F, 1);
    }
}
