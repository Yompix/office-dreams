package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.gta.Garage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

/** Экран «Нитро-Гаража»: машина сбоку в выбранном цвете и кнопки апгрейдов с ценами. */
public class GarageScreen extends LifeScreen {
    private NbtCompound info;

    public GarageScreen(NbtCompound info) {
        super("Нитро-Гараж");
        this.info = info;
    }

    public void update(NbtCompound n) {
        info = n;
    }

    private void send(String act, int value) {
        NbtCompound n = new NbtCompound();
        n.putString("action", act);
        n.putInt("value", value);
        NovogradClient.send("garage", n);
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = 330, h = 220;
        int x = width / 2 - w / 2, y = height / 2 - h / 2 + (int) ((1 - openAnim()) * 30);
        panel(ctx, x, y, w, h, 0xFF2A2116, 0xFF15110B);
        ctx.fillGradient(x, y, x + w, y + 22, 0xFFFFB300, 0xFFFF8F00);
        center(ctx, "НИТРО-ГАРАЖ · $" + info.getInt("money"), x + w / 2, y + 7, 0xFF1A1200);
        // машина сбоку
        int color = 0xFF000000 | info.getInt("color");
        int cx = x + 14, cy = y + 34;
        ctx.fill(cx, cy + 22, cx + 120, cy + 42, color);
        ctx.fill(cx + 22, cy + 6, cx + 92, cy + 24, color);
        ctx.fill(cx + 28, cy + 10, cx + 54, cy + 22, 0xFF8FB8D8);
        ctx.fill(cx + 58, cy + 10, cx + 86, cy + 22, 0xFF8FB8D8);
        for (int wx : new int[]{cx + 14, cx + 86}) {
            ctx.fill(wx, cy + 36, wx + 20, cy + 54, 0xFF151515);
            ctx.fill(wx + 6, cy + 42, wx + 14, cy + 48, 0xFFB0B0B0);
        }
        if (info.getBoolean("nitro")) {
            float f = (System.currentTimeMillis() % 400) / 400f;
            ctx.fill(cx - 10 - (int) (f * 8), cy + 30, cx, cy + 34, 0xFF4FC3F7);
        }
        int hp = info.getInt("health");
        ctx.drawText(textRenderer, "Состояние: " + hp + "%", cx, cy + 60, hp < 40 ? 0xFFFF6B6B : 0xFF9CCC65, false);
        ctx.drawText(textRenderer, "Двигатель: " + "▮".repeat(info.getInt("engine")) + "▯".repeat(2 - info.getInt("engine")), cx, cy + 72, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, "Броня: " + "▮".repeat(info.getInt("armor")) + "▯".repeat(2 - info.getInt("armor")), cx, cy + 84, 0xFFFFFFFF, false);
        ctx.drawText(textRenderer, "Нитро: " + (info.getBoolean("nitro") ? "есть (N)" : "нет"), cx, cy + 96, 0xFFFFFFFF, false);
        // цвета
        ctx.drawText(textRenderer, "Покраска $" + Garage.PAINT, cx, cy + 112, 0xFFFFD54F, false);
        for (int i = 0; i < Garage.PAINTS.length; i++) {
            int sx = cx + i * 15, sy = cy + 124;
            boolean hov = hover(sx, sy, 12, 12);
            ctx.fill(sx - (hov ? 1 : 0), sy - (hov ? 1 : 0), sx + 12 + (hov ? 1 : 0), sy + 12 + (hov ? 1 : 0), 0xFF000000 | Garage.PAINTS[i]);
            int idx = i;
            area(sx, sy, 12, 12, () -> send("paint", idx));
        }
        // апгрейды
        int bx = x + 176, by = y + 32, bw = 140;
        int eng = info.getInt("engine"), arm = info.getInt("armor");
        button(ctx, bx, by, bw, 20, info.getBoolean("nitro") ? "Нитро ✓" : "Нитро · $" + Garage.NITRO, 0xFF0288D1, !info.getBoolean("nitro"), () -> send("nitro", 0));
        button(ctx, bx, by + 26, bw, 20, eng >= 2 ? "Двигатель MAX ✓" : "Двигатель " + (eng + 1) + " · $" + (eng == 0 ? Garage.ENGINE1 : Garage.ENGINE2), 0xFFE65100, eng < 2, () -> send("engine", 0));
        button(ctx, bx, by + 52, bw, 20, arm >= 2 ? "Броня MAX ✓" : "Броня " + (arm + 1) + " · $" + (arm == 0 ? Garage.ARMOR1 : Garage.ARMOR2), 0xFF546E7A, arm < 2, () -> send("armor", 0));
        button(ctx, bx, by + 78, bw, 20, hp >= 100 ? "Ремонт не нужен" : "Ремонт · $" + Garage.REPAIR, 0xFF2E7D32, hp < 100, () -> send("repair", 0));
        int stars = info.getInt("stars");
        button(ctx, bx, by + 110, bw, 26, stars > 0 ? "Перекрасить от полиции ★" + stars + " · $" + Garage.HIDE : "Розыска нет", 0xFFC62828, stars > 0, () -> send("hide", 0));
        button(ctx, bx, by + 150, bw, 20, "Готово", 0xFF5D4037, true, this::close);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
