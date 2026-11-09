package com.yompix.novograd.client.menu;

import com.yompix.novograd.client.NovogradConfig;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.option.SoundOptionsScreen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * «НАСТРОЙКИ» Новограда: вкладки ЗВУК / ГРАФИКА / ИНТЕРФЕЙС, строки «название — значение» как в криминальных играх.
 * ← → или клик меняют значение, ползунки тянутся мышью. Всё сохраняется сразу (config/novograd.json + options.txt).
 */
public class SettingsScreen extends Screen {
    /** slider != null — ползунок 0..1; step(±1) — переключить; open — открыть подэкран. */
    private record Row(String label, String desc, Supplier<String> value, @Nullable Supplier<Float> slider, @Nullable Consumer<Float> set,
                       @Nullable Consumer<Integer> step, @Nullable Runnable open) {}

    private static final String[] TABS = {"ЗВУК", "ГРАФИКА", "ИНТЕРФЕЙС"};
    private static int tab;

    private final Screen parent;
    private final long opened = System.currentTimeMillis();
    private final List<Row> rows = new ArrayList<>();
    private float[] anim = new float[0];
    private final float[] tabAnim = new float[TABS.length];
    private int selected, dragging = -1, lastMx = -1, lastMy = -1;
    private int px, py, pw, rowY0;
    private static final int ROW_H = 18;

    public SettingsScreen(Screen parent) {
        super(Text.literal("Настройки"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuild();
    }

    private NovogradConfig cfg() {
        return NovogradConfig.get();
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String onOff(boolean b) {
        return b ? "ВКЛ" : "ВЫКЛ";
    }

    private void rebuild() {
        rows.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        var o = mc.options;
        switch (tab) {
            case 0 -> {
                rows.add(new Row("Громкость радио", "Радио в машине, в наушниках (телефон → «Радио») и в главном меню.", () -> pct(cfg().radioVolume),
                        () -> cfg().radioVolume, v -> cfg().radioVolume = v, null, null));
                rows.add(new Row("Голоса героев", "Озвучка разговоров и кат-сцен.", () -> pct(cfg().voiceVolume),
                        () -> cfg().voiceVolume, v -> cfg().voiceVolume = v, null, null));
                rows.add(new Row("Общая громкость", "Громкость всей игры (настройка Minecraft).", () -> pct(o.getSoundVolumeOption(SoundCategory.MASTER).getValue()),
                        () -> o.getSoundVolumeOption(SoundCategory.MASTER).getValue().floatValue(), v -> o.getSoundVolumeOption(SoundCategory.MASTER).setValue((double) v), null, null));
                // музыки Minecraft в Новограде нет (вместо неё радио) — вместо её ползунка выбор станции
                rows.add(new Row("Радиостанция", "Что играет в машине и в наушниках. Колесо мыши в машине тоже листает станции.", () -> {
                            int s = cfg().radioStation;
                            return s < 0 || s >= com.yompix.novograd.client.CarRadio.STATIONS.length ? "ВЫКЛ" : com.yompix.novograd.client.CarRadio.STATIONS[s][0];
                        }, null, null, d -> {
                            int n = com.yompix.novograd.client.CarRadio.STATIONS.length;
                            cfg().radioStation = Math.floorMod(cfg().radioStation + 1 + d, n + 1) - 1; // -1 = выкл
                        }, null));
                rows.add(new Row("Все звуки Minecraft", "Погода, существа, блоки и остальное.", () -> "›", null, null, null,
                        () -> mc.setScreen(new SoundOptionsScreen(this, o))));
            }
            case 1 -> {
                String[] g = {"ВЫКЛ", "ЛЁГКИЙ", "КРАСИВЫЙ"};
                rows.add(new Row("Фильтр города", "Цвет и свет кадра. «Лёгкий» — для слабых ноутбуков. Клавиша K в игре.", () -> g[MathHelper.clamp(cfg().graphics, 0, 2)],
                        null, null, d -> cfg().graphics = Math.floorMod(cfg().graphics + d, 3), null));
                rows.add(new Row("Деревья на ветру", "Листва и трава колышутся.", () -> onOff(cfg().windyFoliage), null, null,
                        d -> cfg().windyFoliage = !cfg().windyFoliage, null));
                rows.add(new Row("Красивая вода", "Блики, волны и цвет воды.", () -> onOff(cfg().prettyWater), null, null,
                        d -> cfg().prettyWater = !cfg().prettyWater, null));
                rows.add(new Row("Дальность прорисовки", "Сколько чанков видно. Меньше — быстрее.", () -> o.getViewDistance().getValue() + " чанков",
                        null, null, d -> o.getViewDistance().setValue(MathHelper.clamp(o.getViewDistance().getValue() + d * 2, 2, 32)), null));
                rows.add(new Row("Графика Minecraft", "Все настройки видео: разрешение, сглаживание, частицы…", () -> "›", null, null, null,
                        () -> mc.setScreen(new VideoOptionsScreen(this, mc, o))));
            }
            default -> {
                rows.add(new Row("Субтитры", "Текст реплик в разговорах и кат-сценах.", () -> onOff(cfg().subtitles), null, null,
                        d -> cfg().subtitles = !cfg().subtitles, null));
                rows.add(new Row("Размер HUD", "Деньги, звёзды розыска, цель задания на экране.", () -> pct(cfg().hudScale),
                        () -> (cfg().hudScale - 0.75f) / 0.75f, v -> cfg().hudScale = Math.round((0.75f + v * 0.75f) * 20) / 20f, null, null));
                rows.add(new Row("Управление", "Клавиши Minecraft и Новограда.", () -> "›", null, null, null,
                        () -> mc.setScreen(new ControlsOptionsScreen(this, o))));
                rows.add(new Row("Все настройки Minecraft", "Язык, чат, доступность, пакеты ресурсов.", () -> "›", null, null, null,
                        () -> mc.setScreen(new OptionsScreen(this, o))));
            }
        }
        anim = new float[rows.size()];
        selected = MathHelper.clamp(selected, 0, Math.max(0, rows.size() - 1));
    }

    private void changed() {
        NovogradConfig.save();
        client.options.write();
    }

    private void step(Row r, int d) {
        if (r.step() != null) r.step().accept(d);
        else if (r.slider() != null && r.set() != null) r.set().accept(MathHelper.clamp(r.slider().get() + d * 0.05f, 0, 1));
        else if (r.open() != null && d > 0) r.open().run();
        else return;
        sound(1.4f);
        changed();
    }

    private void sound(float pitch) {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.5f));
    }

    // ------------------------------------------------------------------ отрисовка
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float a = Ui.easeOut((System.currentTimeMillis() - opened) / 300f);
        backdrop(ctx, width, height, a);
        int lx = 24;
        int top = Math.max(10, height / 2 - 108);
        // заголовок
        float hw = Math.min(150, width * 0.32f);
        Ui.tex(ctx, Ui.SETTINGS, lx - 2 + (1 - a) * -30, top, hw, hw * Ui.SETTINGS_SIZE[1] / Ui.SETTINGS_SIZE[0], Ui.SETTINGS_SIZE[0], Ui.SETTINGS_SIZE[1], a);
        int hy = top + (int) (hw * Ui.SETTINGS_SIZE[1] / Ui.SETTINGS_SIZE[0]) + 6;
        // вкладки
        int tx = lx;
        for (int i = 0; i < TABS.length; i++) {
            int tw = textRenderer.getWidth(TABS[i]) + 20;
            boolean hov = mouseX >= tx && mouseX < tx + tw && mouseY >= hy && mouseY < hy + 16;
            tabAnim[i] = Ui.approach(tabAnim[i], i == tab ? 1 : hov ? 0.4f : 0, 0.3f);
            Ui.slant(ctx, tx, hy, tw, 16, 5, Ui.alpha(0xC0161615, a));
            if (tabAnim[i] > 0.02f) Ui.slant(ctx, tx, hy, (int) (tw * tabAnim[i]), 16, 5, Ui.alpha(i == tab ? Ui.PINK : 0x80D42A3A, a));
            ctx.drawText(textRenderer, TABS[i], tx + 10, hy + 4, Ui.alpha(i == tab ? 0xFFFFFFFF : 0xFFB9B7B1, a), i == tab);
            tx += tw + 4;
        }
        // строки
        pw = Math.min(width - lx * 2, 340);
        px = lx;
        py = hy + 22;
        rowY0 = py + 4;
        int ph = rows.size() * ROW_H + 8;
        Ui.panel(ctx, px, py, pw, ph, Ui.alpha(Ui.CYAN, a));
        boolean moved = mouseX != lastMx || mouseY != lastMy;
        lastMx = mouseX;
        lastMy = mouseY;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int ry = rowY0 + i * ROW_H;
            if (moved && mouseX >= px && mouseX < px + pw && mouseY >= ry && mouseY < ry + ROW_H - 1) selected = i;
            anim[i] = Ui.approach(anim[i], i == selected ? 1 : 0, 0.35f);
            if (anim[i] > 0.02f) {
                Ui.slant(ctx, px + 4, ry, (int) ((pw - 8) * Ui.easeOut(anim[i])), ROW_H - 2, 4, Ui.alpha(0xE0D42A3A, a * anim[i]));
            } else if (i % 2 == 1) ctx.fill(px + 4, ry, px + pw - 4, ry + ROW_H - 2, 0x14FFFFFF);
            int ty = ry + (ROW_H - 2 - 8) / 2;
            boolean sel = i == selected;
            ctx.drawText(textRenderer, r.label(), px + 12 + (int) (anim[i] * 4), ty, sel ? 0xFFFFFFFF : 0xFFDDDBD4, sel);
            int right = px + pw - 12;
            if (r.slider() != null) {
                int bw = 90, bx = right - bw - 34;
                float v = r.slider().get();
                ctx.fill(bx, ty + 2, bx + bw, ty + 6, sel ? 0x60000000 : 0x40FFFFFF);
                ctx.fill(bx, ty + 2, bx + (int) (bw * v), ty + 6, sel ? 0xFFFFFFFF : Ui.CYAN);
                int hx = bx + (int) (bw * v);
                ctx.fill(hx - 2, ty - 1, hx + 2, ty + 9, sel ? 0xFF060606 : 0xFFFFFFFF);
                String val = r.value().get();
                ctx.drawText(textRenderer, val, right - textRenderer.getWidth(val), ty, 0xFFFFFFFF, sel);
            } else {
                String val = r.value().get();
                boolean arrows = r.step() != null;
                int vw = textRenderer.getWidth(val);
                int vx = right - vw - (arrows ? 10 : 0);
                ctx.drawText(textRenderer, val, vx, ty, r.open() != null ? 0xFFFFFFFF : (sel ? 0xFFFFFFFF : Ui.GOLD), sel);
                if (arrows && sel) {
                    ctx.drawText(textRenderer, "‹", vx - 9, ty, 0xFFFFFFFF, false);
                    ctx.drawText(textRenderer, "›", right - 4, ty, 0xFFFFFFFF, false);
                }
            }
        }
        // описание выбранной строки
        if (!rows.isEmpty()) {
            Row r = rows.get(selected);
            int dy = py + ph + 6;
            ctx.fill(px, dy, px + pw, dy + 18, Ui.alpha(0xC0040404, a));
            ctx.fill(px, dy, px + 2, dy + 18, Ui.GOLD);
            ctx.drawText(textRenderer, r.desc(), px + 8, dy + 5, Ui.alpha(0xFFE9E7E0, a), false);
        }
        // нижняя полоса с подсказками
        ctx.fill(0, height - 18, width, height, 0xE0040404);
        ctx.fill(0, height - 19, width, height - 18, Ui.alpha(Ui.PINK, 0.8f));
        String hint = "↑↓ выбор   ← → изменить   Tab вкладка   Esc назад";
        ctx.drawText(textRenderer, hint, 10, height - 13, 0xB0FFFFFF, false);
        String back = "‹ НАЗАД";
        int bw = textRenderer.getWidth(back) + 16;
        boolean bh = mouseX >= width - bw - 8 && mouseY >= height - 18;
        Ui.slant(ctx, width - bw - 8, height - 16, bw, 14, 4, bh ? Ui.PINK : 0x60FFFFFF);
        ctx.drawText(textRenderer, back, width - bw, height - 13, 0xFFFFFFFF, false);
        Ui.grain(ctx, width, height, 0.25f);
        super.render(ctx, mouseX, mouseY, delta);
    }

    /** Фон: в игре — затемнённый и подкрашенный кадр мира, в главном меню — ночной город. */
    public static void backdrop(DrawContext ctx, int width, int height, float a) {
        if (MinecraftClient.getInstance().world == null) LifeMenuScreen.drawBackground(ctx, width, height);
        ctx.fillGradient(0, 0, width, height, (int) (a * 150) << 24 | 0x0A0418, (int) (a * 215) << 24 | 0x05020C);
        for (int x = 0; x < width * 0.7f; x += 4) ctx.fill(x, 0, x + 4, height, (int) (a * 120 * (1 - x / (width * 0.7f))) << 24);
        Ui.vignette(ctx, width, height, a * 0.8f);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    // ------------------------------------------------------------------ управление
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (my >= height - 18 && mx >= width - 80) {
            close();
            return true;
        }
        // вкладки
        int top = Math.max(10, height / 2 - 108);
        float hw = Math.min(150, width * 0.32f);
        int hy = top + (int) (hw * Ui.SETTINGS_SIZE[1] / Ui.SETTINGS_SIZE[0]) + 6, tx = 24;
        for (int i = 0; i < TABS.length; i++) {
            int tw = textRenderer.getWidth(TABS[i]) + 20;
            if (mx >= tx && mx < tx + tw && my >= hy && my < hy + 16) {
                setTab(i);
                return true;
            }
            tx += tw + 4;
        }
        for (int i = 0; i < rows.size(); i++) {
            int ry = rowY0 + i * ROW_H;
            if (mx >= px && mx < px + pw && my >= ry && my < ry + ROW_H - 1) {
                selected = i;
                Row r = rows.get(i);
                if (r.slider() != null) {
                    dragging = i;
                    drag(mx);
                } else if (r.open() != null) {
                    sound(1f);
                    r.open().run();
                } else step(r, 1);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void drag(double mx) {
        if (dragging < 0 || dragging >= rows.size()) return;
        Row r = rows.get(dragging);
        int right = px + pw - 12, bw = 90, bx = right - bw - 34;
        if (r.set() != null) r.set().accept((float) MathHelper.clamp((mx - bx) / bw, 0, 1));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging >= 0) {
            drag(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging >= 0) {
            dragging = -1;
            sound(1.2f);
            changed();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void setTab(int t) {
        tab = Math.floorMod(t, TABS.length);
        selected = 0;
        sound(0.9f);
        rebuild();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_Q) {
            setTab(tab + (key == GLFW.GLFW_KEY_Q || (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1));
            return true;
        }
        if (!rows.isEmpty()) {
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
                selected = (selected + 1) % rows.size();
                sound(2f);
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
                selected = (selected - 1 + rows.size()) % rows.size();
                sound(2f);
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
                step(rows.get(selected), -1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D || key == GLFW.GLFW_KEY_ENTER) {
                step(rows.get(selected), 1);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void close() {
        changed();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return client.world != null && client.isInSingleplayer();
    }
}
