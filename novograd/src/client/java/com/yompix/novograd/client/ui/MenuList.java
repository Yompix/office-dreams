package com.yompix.novograd.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Список пунктов меню слева (главное меню, пауза, настройки): мышь, стрелки и Enter, анимация наведения. */
public class MenuList {
    public record Item(Supplier<String> label, String sub, Runnable action, boolean enabled) {}

    public final List<Item> items = new ArrayList<>();
    private float[] anim = new float[0];
    public int selected = 0;
    private int x, y, w, lastMx = -1, lastMy = -1;
    private final long born = System.currentTimeMillis();
    public int rowH = 18, rowGap = 3, subH = 25;
    public float scale = 1.5f;

    public MenuList add(String label, String sub, Runnable action) {
        return add(() -> label, sub, action, true);
    }

    public MenuList add(Supplier<String> label, String sub, Runnable action, boolean enabled) {
        items.add(new Item(label, sub, action, enabled));
        anim = new float[items.size()];
        return this;
    }

    public void clear() {
        items.clear();
        anim = new float[0];
    }

    private int h(Item it) {
        return it.sub() == null || it.sub().isEmpty() ? rowH : subH;
    }

    public int height() {
        int s = 0;
        for (Item it : items) s += h(it) + rowGap;
        return s;
    }

    public void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int mx, int my) {
        this.x = x;
        this.y = y;
        this.w = w;
        float t = (System.currentTimeMillis() - born) / 1000f;
        boolean moved = mx != lastMx || my != lastMy;
        lastMx = mx;
        lastMy = my;
        int yy = y;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int hh = h(it);
            if (moved && mx >= x - 10 && mx < x + w && my >= yy && my < yy + hh) selected = i;
            anim[i] = Ui.approach(anim[i], i == selected ? 1 : 0, 0.35f);
            float appear = Ui.easeOut((t - 0.1f - i * 0.05f) / 0.3f);
            Ui.menuItem(ctx, tr, x, yy, w, hh, it.label().get(), it.sub(), anim[i], appear, it.enabled(), scale);
            yy += hh + rowGap;
        }
    }

    public boolean click(double mx, double my) {
        int yy = y;
        for (Item it : items) {
            int hh = h(it);
            if (mx >= x - 10 && mx < x + w && my >= yy && my < yy + hh) {
                activate(it);
                return true;
            }
            yy += hh + rowGap;
        }
        return false;
    }

    private void activate(Item it) {
        if (!it.enabled()) return;
        Ui.mc().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.15f, 0.6f));
        it.action().run();
    }

    public boolean key(int key) {
        if (items.isEmpty()) return false;
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
            selected = (selected + 1) % items.size();
            tick();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
            selected = (selected - 1 + items.size()) % items.size();
            tick();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            activate(items.get(Math.max(0, Math.min(selected, items.size() - 1))));
            return true;
        }
        return false;
    }

    private void tick() {
        Ui.mc().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 2f, 0.25f));
    }
}
