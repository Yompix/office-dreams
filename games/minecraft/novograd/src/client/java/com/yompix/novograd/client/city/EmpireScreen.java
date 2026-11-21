package com.yompix.novograd.client.city;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/** 0.9: телефон → «Империя»: бизнесы (найм, рейды), районы, жильё, доход, цели (данные — gta/Empire). */
public class EmpireScreen extends LifeScreen {
    public static NbtCompound state = new NbtCompound();

    public EmpireScreen() {
        super("Империя");
        state = new NbtCompound();
        NovogradClient.send("empire_state");
    }

    private void hire(int i, String who) {
        NbtCompound n = new NbtCompound();
        n.putInt("i", i);
        n.putString("who", who);
        NovogradClient.send("empire_hire", n);
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int w = Math.min(480, width - 20), h = Math.min(320, height - 16);
        int x = width / 2 - w / 2, y = height / 2 - h / 2;
        panel(ctx, x, y, w, h, 0xF014100A, 0xF0080808);
        title(ctx, "ИМПЕРИЯ", x + 10, y + 8, 0xFFFFB300);
        if (!state.contains("biz")) {
            center(ctx, "…", x + w / 2, y + h / 2, 0xFFB0BEC5);
            return;
        }
        String head = "$" + state.getInt("daily") + "/день · районы " + state.getInt("turf") + "/" + state.getInt("turfAll") + " · дома " + state.getInt("homes");
        ctx.drawText(textRenderer, head, x + w - 10 - textRenderer.getWidth(head), y + 10, 0xFFFFE082, false);
        NbtList biz = state.getList("biz", NbtElement.COMPOUND_TYPE);
        int ry = y + 30, row = Math.max(16, Math.min(22, (h - 120) / Math.max(1, biz.size())));
        for (int i = 0; i < biz.size(); i++) {
            NbtCompound c = biz.getCompound(i);
            boolean own = c.getBoolean("own");
            int col = c.getBoolean("raid") ? 0xFFFF5252 : own ? 0xFFFFFFFF : 0xFF8A8F99;
            ctx.drawText(textRenderer, (c.getBoolean("raid") ? "! " : "") + c.getString("name"), x + 12, ry + 4, col, false);
            String inc = own ? "+$" + c.getInt("income") : "$" + c.getInt("price");
            ctx.drawText(textRenderer, inc, x + 170, ry + 4, own ? 0xFF7CFF7C : 0xFF9E9E9E, false);
            if (own) {
                final int k = i;
                if (c.getBoolean("mgr")) ctx.drawText(textRenderer, "управл. ✔", x + w - 190, ry + 4, 0xFF7CFF7C, false);
                else button(ctx, x + w - 192, ry, 88, row - 3, "Управл. $" + c.getInt("mgrCost"), 0xFF2E7D32, true, () -> hire(k, "mgr"));
                if (c.getBoolean("grd")) ctx.drawText(textRenderer, "охрана ✔", x + w - 94, ry + 4, 0xFF7CFF7C, false);
                else button(ctx, x + w - 98, ry, 88, row - 3, "Охрана $" + c.getInt("grdCost"), 0xFF1565C0, true, () -> hire(k, "grd"));
            }
            ry += row;
        }
        ry += 6;
        ctx.drawText(textRenderer, "ЦЕЛИ", x + 12, ry, 0xFFFFB300, true);
        ry += 12;
        NbtList g = state.getList("goals", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < g.size(); i++) {
            NbtCompound c = g.getCompound(i);
            boolean done = c.getBoolean("done");
            int cx = x + 12 + (i % 2) * (w / 2), cy = ry + (i / 2) * 12;
            ctx.drawText(textRenderer, (done ? "✔ " : "○ ") + c.getString("name") + " · $" + c.getInt("prize"), cx, cy, done ? 0xFF7CFF7C : 0xFFB0BEC5, false);
        }
        center(ctx, "Esc — закрыть", x + w / 2, y + h - 11, 0xFF607D8B);
    }
}
