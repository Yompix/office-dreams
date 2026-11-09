package com.yompix.novograd.client.screen;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.NovogradClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Банкомат «Банк Новограда»: вставляем карту, на экране баланс и суммы, при выдаче
 * купюры по одной выезжают из лотка, при внесении — заезжают в приёмник.
 */
public class AtmScreen extends LifeScreen {
    private enum Stage { CARD, MENU, CUSTOM, PROCESS, DISPENSE, DEPOSIT, ERROR }

    private Stage stage = Stage.CARD;
    private long stageAt = System.currentTimeMillis();
    private int balance, cash;
    private String custom = "";
    private String error = "";
    /** Купюры, которые выезжают/заезжают: номинал. */
    private final List<Integer> bills = new ArrayList<>();
    private int sum;
    private boolean answered;
    private int lastSoundBill = -1;

    public AtmScreen(int balance, int cash) {
        super("Банкомат");
        this.balance = balance;
        this.cash = cash;
    }

    private void go(Stage s) {
        stage = s;
        stageAt = System.currentTimeMillis();
    }

    private float since() {
        return (System.currentTimeMillis() - stageAt) / 1000f;
    }

    private void sound(SoundEvent e, float vol, float pitch) {
        if (client != null && client.player != null) client.player.playSound(e, vol, pitch);
    }

    // ------------------------------------------------------------------ отрисовка
    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = 300, h = 236;
        int x = width / 2 - w / 2, y = height / 2 - h / 2 + (int) ((1 - openAnim()) * 30);
        // корпус банкомата
        roundRect(ctx, x - 6, y - 6, w + 12, h + 12, 0xFF1B1E24);
        roundRect(ctx, x, y, w, h, 0xFF3A404C);
        ctx.fillGradient(x, y, x + w, y + 26, 0xFF0D47A1, 0xFF1565C0);
        center(ctx, "БАНК НОВОГРАДА", x + w / 2, y + 9, 0xFFFFFFFF);
        // экран
        int sx = x + 18, sy = y + 34, sw = w - 36, sh = 120;
        ctx.fill(sx - 3, sy - 3, sx + sw + 3, sy + sh + 3, 0xFF0B0D12);
        ctx.fillGradient(sx, sy, sx + sw, sy + sh, 0xFF0F3B5F, 0xFF07243D);
        // строки развёртки — «живой» экран
        int scan = (int) ((System.currentTimeMillis() / 18) % sh);
        ctx.fill(sx, sy + scan, sx + sw, sy + scan + 1, 0x18FFFFFF);
        screen(ctx, sx, sy, sw, sh);
        // прорезь карты и лоток для денег
        int slotY = y + h - 66;
        ctx.fill(x + w - 92, slotY, x + w - 22, slotY + 6, 0xFF0A0A0A);
        ctx.fill(x + w - 92, slotY + 6, x + w - 22, slotY + 7, 0xFF66FF88);
        ctx.drawText(textRenderer, "карта", x + w - 72, slotY + 10, 0xFF9AA3B2, false);
        int trayX = x + 40, trayY = y + h - 36, trayW = w - 80;
        ctx.fill(trayX, trayY, trayX + trayW, trayY + 8, 0xFF0A0A0A);
        ctx.fill(trayX, trayY + 8, trayX + trayW, trayY + 9, stage == Stage.DISPENSE ? 0xFF66FF88 : 0xFF555555);
        center(ctx, "выдача / приём наличных", x + w / 2, trayY + 13, 0xFF9AA3B2);
        card(ctx, x + w - 92, slotY);
        moneyAnim(ctx, trayX, trayY, trayW);
    }

    /** Карта въезжает в прорезь. */
    private void card(DrawContext ctx, int slotX, int slotY) {
        if (stage != Stage.CARD) return;
        float k = MathHelper.clamp(since() / 0.9f, 0, 1);
        float e = k * k;
        int cw = 70, ch = 42;
        int cy = slotY + 10 + (int) ((1 - e) * 46) - (int) (e * ch);
        ctx.enableScissor(0, slotY + 6, width, height);
        roundRect(ctx, slotX, cy, cw, ch, 0xFFFFC107);
        ctx.fill(slotX + 6, cy + 12, slotX + 20, cy + 22, 0xFFB08800);
        ctx.drawText(textRenderer, "НОВОГРАД БАНК", slotX + 4, cy + 30, 0xFF5D4000, false);
        ctx.disableScissor();
        if (k >= 1 && since() > 1.3f) {
            sound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.6f, 1.6f);
            go(Stage.MENU);
        }
    }

    /** Купюры выезжают из лотка (выдача) или заезжают в него (внесение). */
    private void moneyAnim(DrawContext ctx, int trayX, int trayY, int trayW) {
        if (stage != Stage.DISPENSE && stage != Stage.DEPOSIT) return;
        float t = since();
        boolean out = stage == Stage.DISPENSE;
        int n = Math.min(bills.size(), 12);
        for (int i = 0; i < n; i++) {
            float k = MathHelper.clamp((t - i * 0.18f) / 0.45f, 0, 1);
            if (k <= 0) continue;
            if (k > 0 && lastSoundBill < i) {
                lastSoundBill = i;
                sound(SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8f, 1.4f + (i % 3) * 0.1f);
            }
            float e = 1 - (1 - k) * (1 - k);
            int v = bills.get(i);
            int bw = 62, bh = 26;
            int bx = trayX + trayW / 2 - bw / 2 + (i % 5 - 2) * 7;
            // выдача: из щели вниз; внесение: сверху в щель
            int by = out ? trayY + 4 + (int) (e * 34) + i * 2 : trayY + 40 - (int) (e * 40);
            ctx.enableScissor(0, trayY + 4, width, height);
            int paper = v == 100 ? 0xFFE8944A : v == 50 ? 0xFF6F8FE0 : 0xFF5FB86B;
            roundRect(ctx, bx, by, bw, bh, paper);
            ctx.fill(bx + 3, by + 3, bx + bw - 3, by + 4, 0x40FFFFFF);
            ctx.drawText(textRenderer, "$" + v, bx + bw / 2 - textRenderer.getWidth("$" + v) / 2, by + 9, 0xFFFFFFFF, true);
            ctx.disableScissor();
        }
        float total = n * 0.18f + 0.6f;
        if (t > total && answered) {
            if (t < total + 0.1f) sound(ModContent.CASH, 0.8f, 1f);
            if (t > total + 1.6f) {
                bills.clear();
                lastSoundBill = -1;
                go(Stage.MENU);
            }
        }
    }

    /** Содержимое экрана по стадиям. */
    private void screen(DrawContext ctx, int sx, int sy, int sw, int sh) {
        int cx = sx + sw / 2;
        switch (stage) {
            case CARD -> {
                center(ctx, "Вставьте карту", cx, sy + 40, 0xFFFFFFFF);
                if ((System.currentTimeMillis() / 400) % 2 == 0) center(ctx, "▼", cx, sy + 60, 0xFF66FF88);
            }
            case MENU -> {
                center(ctx, "Баланс", cx, sy + 6, 0xFF9FD3FF);
                bigText(ctx, "$" + balance, cx, sy + 17, 2f, 0xFFFFFFFF);
                int[] sums = {50, 100, 250, 500, 1000};
                int bw = 58, gap = 6, row = sy + 46;
                int left = cx - (bw * 3 + gap * 2) / 2;
                for (int i = 0; i < sums.length; i++) {
                    int s = sums[i];
                    int bx = left + (i % 3) * (bw + gap), by = row + (i / 3) * 22;
                    button(ctx, bx, by, bw, 18, "$" + s, 0xFF1E88E5, balance >= s, () -> withdraw(s));
                }
                button(ctx, left + 2 * (bw + gap), row + 22, bw, 18, "Другая", 0xFF5C6BC0, balance >= 10, () -> {
                    custom = "";
                    go(Stage.CUSTOM);
                });
                button(ctx, sx + 6, sy + sh - 22, sw / 2 - 9, 18, "Внести $" + cash, 0xFF2E7D32, cash > 0, this::deposit);
                button(ctx, sx + sw / 2 + 3, sy + sh - 22, sw / 2 - 9, 18, "Забрать карту", 0xFF6D4C41, true, this::close);
            }
            case CUSTOM -> {
                center(ctx, "Введите сумму (кратно $10)", cx, sy + 10, 0xFF9FD3FF);
                String shown = "$" + (custom.isEmpty() ? "0" : custom) + ((System.currentTimeMillis() / 450) % 2 == 0 ? "_" : " ");
                bigText(ctx, shown, cx, sy + 30, 2f, 0xFFFFFFFF);
                center(ctx, "цифры на клавиатуре · Enter — снять", cx, sy + 58, 0xFF7FA7C9);
                int val = custom.isEmpty() ? 0 : Integer.parseInt(custom);
                boolean ok = val >= 10 && val % 10 == 0 && val <= balance;
                button(ctx, sx + 6, sy + sh - 22, sw / 2 - 9, 18, "Снять", 0xFF1E88E5, ok, () -> withdraw(val));
                button(ctx, sx + sw / 2 + 3, sy + sh - 22, sw / 2 - 9, 18, "Назад", 0xFF546E7A, true, () -> go(Stage.MENU));
            }
            case PROCESS -> {
                center(ctx, "Обработка операции…", cx, sy + 36, 0xFFFFFFFF);
                // крутилка
                float a = since() * 6;
                for (int i = 0; i < 8; i++) {
                    double ang = a + i * Math.PI / 4;
                    int px = cx + (int) (Math.cos(ang) * 14), py = sy + 72 + (int) (Math.sin(ang) * 14);
                    int alpha = 60 + i * 24;
                    ctx.fill(px - 2, py - 2, px + 2, py + 2, alpha << 24 | 0x9FD3FF);
                }
                if (since() > 1.2f && answered) {
                    if (!error.isEmpty()) go(Stage.ERROR);
                    else {
                        sound(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 0.6f, 1.2f);
                        go(Stage.DISPENSE);
                    }
                }
            }
            case DISPENSE -> {
                center(ctx, "Выдано $" + sum, cx, sy + 30, 0xFF66FF88);
                center(ctx, "Заберите наличные ↓", cx, sy + 52, (System.currentTimeMillis() / 350) % 2 == 0 ? 0xFFFFFFFF : 0xFFFFD54F);
                center(ctx, "Остаток: $" + balance, cx, sy + 80, 0xFF9FD3FF);
            }
            case DEPOSIT -> {
                center(ctx, "Пересчёт купюр…", cx, sy + 30, 0xFFFFFFFF);
                center(ctx, "Зачислено $" + sum, cx, sy + 52, since() > 1 ? 0xFF66FF88 : 0x00000000);
                center(ctx, "Баланс: $" + balance, cx, sy + 80, 0xFF9FD3FF);
            }
            case ERROR -> {
                center(ctx, "Операция отклонена", cx, sy + 36, 0xFFFF6B6B);
                center(ctx, error, cx, sy + 56, 0xFFFFFFFF);
                if (since() > 2) {
                    error = "";
                    go(Stage.MENU);
                }
            }
        }
    }

    // ------------------------------------------------------------------ действия
    /** Для автотеста. */
    public void pressWithdraw(int amount) {
        withdraw(amount);
    }

    private void withdraw(int amount) {
        sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.4f);
        sum = amount;
        answered = false;
        error = "";
        NbtCompound n = new NbtCompound();
        n.putString("action", "withdraw");
        n.putInt("amount", amount);
        NovogradClient.send("atm", n);
        go(Stage.PROCESS);
    }

    private void deposit() {
        sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.4f);
        answered = false;
        NbtCompound n = new NbtCompound();
        n.putString("action", "deposit");
        NovogradClient.send("atm", n);
        // наличные: раскладываем на купюры для анимации
        bills.clear();
        int left = cash;
        for (int v : new int[]{100, 50, 10}) while (left >= v && bills.size() < 12) { bills.add(v); left -= v; }
        sum = cash;
        go(Stage.DEPOSIT);
    }

    /** Ответ сервера. */
    public void result(NbtCompound n) {
        answered = true;
        balance = n.getInt("balance");
        cash = n.getInt("cash");
        if (!n.getBoolean("ok")) {
            error = n.getString("msg");
            return;
        }
        if (n.getString("action").equals("withdraw")) {
            sum = n.getInt("sum");
            bills.clear();
            int left = sum;
            for (int v : new int[]{100, 50, 10}) while (left >= v && bills.size() < 12) { bills.add(v); left -= v; }
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (stage == Stage.CUSTOM) {
            if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9 && custom.length() < 5) {
                custom += (char) ('0' + key - GLFW.GLFW_KEY_0);
                sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.8f);
                return true;
            }
            if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9 && custom.length() < 5) {
                custom += (char) ('0' + key - GLFW.GLFW_KEY_KP_0);
                sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3f, 1.8f);
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !custom.isEmpty()) {
                custom = custom.substring(0, custom.length() - 1);
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && !custom.isEmpty()) {
                int v = Integer.parseInt(custom);
                if (v >= 10 && v % 10 == 0 && v <= balance) withdraw(v);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
