package com.yompix.novograd.client.menu;

import com.yompix.novograd.client.LifeHud;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.PhoneScreen;
import com.yompix.novograd.client.story.ClientStory;
import com.yompix.novograd.client.ui.MenuList;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.life.Jobs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;

/** Пауза в стиле «Тёмных улиц»: слева список, справа карточка — что сейчас по сюжету, деньги, день. */
public class LifePauseScreen extends Screen {
    private final long opened = System.currentTimeMillis();
    private final MenuList menu = new MenuList();

    public LifePauseScreen() {
        super(Text.literal("Пауза"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        int sel = menu.selected;
        menu.clear();
        menu.rowH = 15;
        menu.rowGap = 2;
        menu.scale = 1.35f;
        menu.add("Продолжить", "", () -> mc.setScreen(null));
        menu.add("Телефон", "", () -> mc.setScreen(new PhoneScreen(null)));
        menu.add("Сюжет", "", () -> mc.setScreen(new PhoneScreen("story")));
        menu.add("Награды", "", () -> mc.setScreen(new PhoneScreen("trophy")));
        menu.add(() -> com.yompix.novograd.client.FilmMode.active ? "Режим кино ✓" : "Режим кино", "", () -> {
            com.yompix.novograd.client.FilmMode.toggle(mc);
            mc.setScreen(null);
        }, true);
        menu.add(() -> com.yompix.novograd.client.studio.Studio.active() ? "Киностудия ✓" : "Киностудия (F7)", "", () -> {
            mc.setScreen(null);
            com.yompix.novograd.client.studio.Studio.toggle(mc);
        }, true);
        menu.add("Настройки игры", "", () -> mc.setScreen(new SettingsScreen(this)));
        menu.add("Настройки Minecraft", "", () -> mc.setScreen(new OptionsScreen(this, mc.options)));
        friends(mc);
        menu.add("Сохранить и выйти", "", this::quit);
        menu.selected = Math.min(sel, menu.items.size() - 1);
    }

    /** «Открыть для друзей» (стандартное «Открыть для сети»): с модом e4mc — играть вдвоём через интернет. */
    private void friends(MinecraftClient mc) {
        var server = mc.getServer();
        if (server == null) {
            menu.add(() -> "Ты в гостях у друга", "", () -> {}, false);
            return;
        }
        if (server.isRemote()) {
            menu.add("Мир открыт для друзей ✓", "", () -> {
                if (mc.player != null) mc.player.sendMessage(Text.literal("Мир уже открыт. Адрес для друга — в чате (с модом e4mc) или порт " + server.getServerPort()), false);
                mc.setScreen(null);
            });
            return;
        }
        menu.add("Открыть для друзей", "", () -> mc.setScreen(new net.minecraft.client.gui.screen.OpenToLanScreen(this)));
    }

    private void quit() {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean sp = mc.isInSingleplayer();
        if (mc.world != null) mc.world.disconnect();
        if (sp) mc.disconnect(new MessageScreen(Text.literal("Сохраняем твою жизнь…")));
        else mc.disconnect();
        mc.setScreen(new LifeMenuScreen());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float a = Ui.easeOut((System.currentTimeMillis() - opened) / 280f);
        SettingsScreen.backdrop(ctx, width, height, a);
        int lx = 24, top = Math.max(8, height / 2 - 110);
        // шапка: маленький логотип и «ПАУЗА»
        float lw = Math.min(96, width * 0.22f);
        int lh = Ui.logo(ctx, lx - 2 + (1 - a) * -24, top, lw, a);
        float pw = Math.min(118, width * 0.27f);
        float ph = pw * Ui.PAUSE_SIZE[1] / Ui.PAUSE_SIZE[0];
        Ui.tex(ctx, Ui.PAUSE, lx - 2 + (1 - a) * -40, top + lh + 2, pw, ph, Ui.PAUSE_SIZE[0], Ui.PAUSE_SIZE[1], a);
        int my = top + lh + (int) ph + 10;
        menu.render(ctx, textRenderer, lx + 10, my, 170, mouseX, mouseY);
        card(ctx, a);
        // нижняя полоса
        ctx.fill(0, height - 18, width, height, 0xE0050309);
        ctx.fill(0, height - 19, width, height - 18, Ui.alpha(Ui.PINK, 0.8f));
        ctx.drawText(textRenderer, "↑↓ выбор   Enter открыть   Esc вернуться в игру", 10, height - 13, 0xB0FFFFFF, false);
        Ui.grain(ctx, width, height, 0.25f);
        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Карточка справа: задание, деньги, день, учёба и работа. */
    private void card(DrawContext ctx, float a) {
        int cw = Math.min(190, width - 250);
        if (cw < 120) return;
        int cx = width - cw - 20 + (int) ((1 - a) * 40), cy = Math.max(14, height / 2 - 100);
        var d = NovogradClient.data;
        var o = ClientStory.objective;
        // деньги — крупно, зелёным
        Ui.slant(ctx, cx, cy, cw, 26, 6, 0xE00B0812);
        ctx.fill(cx, cy + 26, cx + cw, cy + 27, Ui.MONEY);
        Ui.big(ctx, textRenderer, "$" + LifeHud.money(d.money), cx + cw - 10, cy + 6, 2f, Ui.MONEY, 2);
        ctx.drawText(textRenderer, "НАЛИЧНЫЕ", cx + 10, cy + 10, 0xFF9C96B0, false);
        int y = cy + 34;
        int ch = 96;
        Ui.panel(ctx, cx, y, cw, ch, Ui.GOLD);
        if (o.contains("mission")) {
            Ui.small(ctx, textRenderer, o.getString("chapter").toUpperCase(), cx + 8, y + 7, 0.8f, Ui.GOLD, 0);
            Ui.big(ctx, textRenderer, trim(o.getString("mission"), (int) ((cw - 16) / 1.3f)), cx + 8, y + 17, 1.3f, 0xFFFFFFFF, 0);
            var lines = textRenderer.wrapLines(Text.literal("▶ " + o.getString("hint")), cw - 16);
            for (int i = 0; i < lines.size() && i < 4; i++) ctx.drawText(textRenderer, lines.get(i), cx + 8, y + 34 + i * 10, 0xFFDCD8E8, false);
        } else {
            Ui.small(ctx, textRenderer, "СВОБОДНАЯ ИГРА", cx + 8, y + 7, 0.8f, Ui.GOLD, 0);
            Ui.big(ctx, textRenderer, "Новоград твой", cx + 8, y + 17, 1.3f, 0xFFFFFFFF, 0);
            ctx.drawText(textRenderer, "Работай, катайся, ищи задания в телефоне.", cx + 8, y + 34, 0xFFDCD8E8, false);
        }
        int sy = y + ch + 6;
        Ui.panel(ctx, cx, sy, cw, 44, Ui.CYAN);
        var w = MinecraftClient.getInstance().world;
        String day = w == null ? "" : "ДЕНЬ " + (w.getTimeOfDay() / 24000 + 1) + " · " + LifeHud.clock(w.getTimeOfDay() % 24000);
        ctx.drawText(textRenderer, day, cx + 8, sy + 7, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "Работа: " + Jobs.Job.of(d.job).title, cx + 8, sy + 19, 0xFFB8B2CC, false);
        ctx.drawText(textRenderer, "Розыск", cx + 8, sy + 30, 0xFF8A8598, false);
        int stars = NovogradClient.wantedStars;
        for (int i = 0; i < 5; i++)
            Ui.tex(ctx, i < stars ? Ui.STAR : Ui.STAR_EMPTY, cx + 50 + i * 11, sy + 29, 10, 10, 32, 32, a);
    }

    private String trim(String s, int w) {
        if (textRenderer.getWidth(s) <= w) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && menu.click(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (menu.key(key)) return true;
        return super.keyPressed(key, scan, mods);
    }
}
