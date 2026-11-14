package com.yompix.glukhoman.client.screen;

import com.yompix.glukhoman.client.GlukhomanClient;
import com.yompix.glukhoman.net.Net;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

/** Кассеты: что на плёнке. ▶ — пересмотреть, ✕ — следующая запись пойдёт поверх (стирание). Сданные в ящик — только номер. */
public class TapeScreen extends Screen {
    final NbtList tapes;

    public TapeScreen(NbtList tapes) {
        super(Text.literal("Кассеты"));
        this.tapes = tapes;
    }

    int rowY(int i) {
        return 34 + i * 13;
    }

    @Override
    protected void init() {
        int row = 0;
        for (NbtElement e : tapes) {
            NbtCompound t = (NbtCompound) e;
            row++;
            if (t.getBoolean("dep")) continue;
            NbtList sl = t.getList("slots", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < sl.size(); i++) {
                final int n = t.getInt("n"), k = i;
                int y = rowY(row);
                addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> act(n, k, "play")).dimensions(width / 2 + 150, y - 2, 18, 12).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> act(n, k, "over")).dimensions(width / 2 + 170, y - 2, 18, 12).build());
                row++;
            }
        }
    }

    void act(int n, int slot, String a) {
        NbtCompound m = Net.msg("tape");
        m.putInt("n", n);
        m.putInt("slot", slot);
        m.putString("act", a);
        GlukhomanClient.send(m);
        close();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xD0080A0C);
        ctx.drawCenteredTextWithShadow(textRenderer, "КАССЕТЫ  ·  ▶ пересмотреть  ·  ✕ писать поверх (сюжет сотрётся)", width / 2, 14, 0xFFD8D0C0);
        int row = 0;
        for (NbtElement e : tapes) {
            NbtCompound t = (NbtCompound) e;
            ctx.drawTextWithShadow(textRenderer, "E-180 №" + t.getInt("n") + (t.getBoolean("dep") ? "  — в ящике. Её забрали." : ""), width / 2 - 190, rowY(row), t.getBoolean("dep") ? 0xFF806868 : 0xFFE8D9A8);
            row++;
            if (t.getBoolean("dep")) continue;
            NbtList sl = t.getList("slots", NbtElement.COMPOUND_TYPE);
            if (sl.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer, "   (чистая)", width / 2 - 190, rowY(row), 0xFF808080);
                row++;
            }
            for (int i = 0; i < sl.size(); i++) {
                NbtCompound s = sl.getCompound(i);
                boolean ptr = sl.size() >= 6 && t.getInt("ptr") == i;
                String line = (i + 1) + ". " + s.getString("title") + "  — " + s.getString("who");
                if (textRenderer.getWidth(line) > 330) line = textRenderer.trimToWidth(line, 326) + "…";
                ctx.drawTextWithShadow(textRenderer, line, width / 2 - 184, rowY(row), ptr ? 0xFFD07070 : 0xFFC8C8C8);
                row++;
            }
        }
        super.render(ctx, mx, my, delta);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
    }
}
