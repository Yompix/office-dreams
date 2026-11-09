package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.MathHelper;

/** Смена кассира: покупатель выкладывает товары на ленту, надо выбрать верную сумму чека. */
public class CashierScreen extends LifeScreen {
    private final String[][][] carts;
    private final int[][] options;
    private final int[] picked;
    private int index;
    private long customerAt = System.currentTimeMillis();
    private boolean sent;

    public CashierScreen(NbtCompound n) {
        super("Касса");
        NbtList cs = n.getList("customers", NbtElement.COMPOUND_TYPE);
        carts = new String[cs.size()][][];
        options = new int[cs.size()][];
        picked = new int[cs.size()];
        for (int i = 0; i < cs.size(); i++) {
            NbtList items = cs.getCompound(i).getList("items", NbtElement.STRING_TYPE);
            carts[i] = new String[items.size()][];
            for (int j = 0; j < items.size(); j++) carts[i][j] = items.getString(j).split("\t");
            options[i] = cs.getCompound(i).getIntArray("options");
        }
    }

    private void pick(int o) {
        picked[index] = o;
        index++;
        customerAt = System.currentTimeMillis();
        if (index >= carts.length && !sent) {
            sent = true;
            NbtCompound n = new NbtCompound();
            n.putIntArray("answers", picked);
            NovogradClient.send("work_answer", n);
        }
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int w = Math.min(280, width - 20), h = Math.min(200, height - 20);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 30);
        panel(ctx, x, y, w, h, 0xFFECEFF3, 0xFFD5DAE1);
        ctx.fill(x, y, x + w, y + 20, 0xFFE05A47);
        ctx.drawText(textRenderer, "Касса №1 · покупатель " + Math.min(index + 1, carts.length) + "/" + carts.length, x + 8, y + 6, 0xFFFFFFFF, true);
        if (index >= carts.length) {
            center(ctx, "Смена окончена! Зарплата придёт на карту.", x + w / 2, y + h / 2 - 10, 0xFF333333);
            button(ctx, x + w / 2 - 40, y + h / 2 + 8, 80, 16, "Готово", 0xFF2FA84F, true, this::close);
            return;
        }
        // лента: товары подъезжают слева
        float t = MathHelper.clamp((System.currentTimeMillis() - customerAt) / 500f, 0, 1);
        ctx.fill(x + 8, y + 28, x + w - 8, y + 30 + carts[index].length * 14 + 6, 0xFF2B2F36);
        for (int i = 0; i < carts[index].length; i++) {
            float it = MathHelper.clamp(t * 1.6f - i * 0.2f, 0, 1);
            int ix = (int) (x + 14 - (1 - it) * 120);
            String[] item = carts[index][i];
            ctx.drawText(textRenderer, item[0], ix, y + 33 + i * 14, 0xFFFFFFFF, false);
            String price = "$" + item[1];
            ctx.drawText(textRenderer, price, x + w - 16 - textRenderer.getWidth(price) - (int) ((1 - it) * 120), y + 33 + i * 14, 0xFF7CFF8A, false);
        }
        int oy = y + h - 50;
        center(ctx, "Сколько с покупателя?", x + w / 2, oy - 14, 0xFF333333);
        int bw = (w - 40) / 4;
        for (int i = 0; i < 4; i++) {
            final int o = i;
            button(ctx, x + 8 + i * (bw + 8), oy, bw, 22, "$" + options[index][i], 0xFF3A7BD5, t >= 1, () -> pick(o));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return index >= carts.length;
    }
}
