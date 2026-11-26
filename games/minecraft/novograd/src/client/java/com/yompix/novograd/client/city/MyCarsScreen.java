package com.yompix.novograd.client.city;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.story.ClientStory;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 0.9.6: телефон → «Мои машины»: где каждая (улица / гараж / штрафстоянка / разбита), расстояние, метка на карте,
 * «подогнать» (за деньги, через 20 с), страховка, «передать права» игроку (с ценой — сделку подтверждает покупатель).
 */
public class MyCarsScreen extends LifeScreen {
    public static NbtCompound state = new NbtCompound();
    private int sel = -1, price;
    private int giveTo = -1;

    public MyCarsScreen() {
        super("Мои машины");
        NovogradClient.send("mycars", act("list"));
    }

    private static NbtCompound act(String a) {
        NbtCompound n = new NbtCompound();
        n.putString("act", a);
        return n;
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float an = openAnim();
        int top = 8 + (int) ((1 - an) * -16);
        title(ctx, "МОИ МАШИНЫ", 12, top, 0xFF00897B);
        var list = state.getList("list", NbtElement.COMPOUND_TYPE);
        int y = top + 30, rowH = 16, w = width - 24;
        if (list.isEmpty()) center(ctx, "Машин нет", width / 2, y + 20, 0xFFB9B7B1);
        var pl = mc().player;
        for (int i = 0; i < list.size() && y < height - 70; i++) {
            NbtCompound c = list.getCompound(i);
            String where = c.getString("where");
            boolean on = i == sel;
            Ui.slant(ctx, 12, y, w, rowH - 2, 3, on ? 0xE000897B : 0x90161615);
            ctx.drawText(textRenderer, c.getString("name"), 18, y + 3, Ui.WHITE, on);
            String st = switch (where) {
                case "impound" -> "штрафстоянка $" + c.getInt("fee");
                case "garage" -> "в гараже";
                case "wrecked" -> "разбита";
                default -> c.contains("x") && pl != null ? Math.round(Math.hypot(c.getDouble("x") - pl.getX(), c.getDouble("z") - pl.getZ())) + " м" : "";
            };
            int sc = where.equals("impound") ? Ui.GOLD : where.equals("wrecked") ? Ui.RED : 0xFFB9B7B1;
            Ui.small(ctx, textRenderer, st + (c.getBoolean("insured") ? "  · застрахована" : ""), 12 + w - 6, y + 4, 0.8f, sc, 2);
            final int k = i;
            area(12, y, w, rowH - 2, () -> {
                sel = k;
                giveTo = -1;
            });
            y += rowH;
        }
        // действия
        if (sel >= 0 && sel < list.size()) {
            NbtCompound c = list.getCompound(sel);
            boolean street = c.getString("where").equals("street");
            int by = height - 64, bw = (w - 9) / 4;
            button(ctx, 12, by, bw, 16, "МЕТКА", 0xFFAB47BC, c.contains("x"), () -> {
                ClientStory.waypoint = new Vec3d(c.getDouble("x"), pl == null ? 64 : pl.getY(), c.getDouble("z"));
                close();
            });
            int callPrice = state.getInt("callBase") + (pl == null ? 0 : (int) Math.min(400, Math.hypot(c.getDouble("x") - pl.getX(), c.getDouble("z") - pl.getZ()) / 10));
            button(ctx, 15 + bw, by, bw, 16, "ПОДОГНАТЬ $" + callPrice, 0xFF00897B, street, () -> {
                NbtCompound n = act("call");
                n.putUuid("car", c.getUuid("car"));
                NovogradClient.send("mycars", n);
            });
            button(ctx, 18 + 2 * bw, by, bw, 16, c.getBoolean("insured") ? "СТРАХОВКА: ДА" : "СТРАХОВКА $" + state.getInt("insure") + "/ДЕНЬ", 0xFF3F7FC0, street, () -> {
                NbtCompound n = act("insure");
                n.putUuid("car", c.getUuid("car"));
                NovogradClient.send("mycars", n);
            });
            button(ctx, 21 + 3 * bw, by, bw, 16, "ПЕРЕДАТЬ", Ui.GOLD, street, () -> giveTo = 0);
            if (giveTo >= 0 && street) {
                // кому и за сколько
                var players = mc().getNetworkHandler() == null ? java.util.List.<String>of()
                        : mc().getNetworkHandler().getPlayerList().stream().map(e -> e.getProfile().getName()).filter(n -> pl == null || !n.equals(pl.getName().getString())).sorted().toList();
                int py = by + 20;
                if (players.isEmpty()) Ui.small(ctx, textRenderer, "Нет других игроков в сети", 12, py + 3, 0.8f, 0xFFB9B7B1, 0);
                else {
                    giveTo = MathHelper.clamp(giveTo, 0, players.size() - 1);
                    String who = players.get(giveTo);
                    button(ctx, 12, py, 14, 16, "‹", Ui.GOLD, true, () -> giveTo = Math.max(0, giveTo - 1));
                    ctx.drawText(textRenderer, who, 30, py + 4, Ui.WHITE, true);
                    button(ctx, 30 + textRenderer.getWidth(who) + 4, py, 14, 16, "›", Ui.GOLD, true, () -> giveTo++);
                    int px = width / 2;
                    button(ctx, px, py, 20, 16, "−", 0xFF6E6D69, price > 0, () -> price = Math.max(0, price - 500));
                    center(ctx, price == 0 ? "даром" : "$" + price, px + 44, py + 4, Ui.MONEY);
                    button(ctx, px + 68, py, 20, 16, "+", 0xFF6E6D69, true, () -> price += 500);
                    button(ctx, width - 12 - 90, py, 90, 16, "ОТПРАВИТЬ", Ui.GOLD, true, () -> {
                        NbtCompound n = act("give");
                        n.putUuid("car", c.getUuid("car"));
                        n.putString("to", who);
                        n.putInt("price", price);
                        NovogradClient.send("mycars", n);
                        giveTo = -1;
                    });
                }
            }
        }
        ctx.fill(0, height - 18, width, height, 0xE0040404);
        String back = "‹ НАЗАД";
        int bw = textRenderer.getWidth(back) + 16;
        Ui.slant(ctx, width - bw - 8, height - 16, bw, 14, 4, hover(width - bw - 8, height - 17, bw, 17) ? Ui.PINK : 0x60FFFFFF);
        ctx.drawText(textRenderer, back, width - bw, height - 13, 0xFFFFFFFF, false);
        area(width - bw - 8, height - 17, bw, 17, this::close);
    }
}
