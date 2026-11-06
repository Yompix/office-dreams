package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.PulseClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.lwjgl.glfw.GLFW;

/** Букмекер «Подвала»: ставки на бой NPC (грязными наличными) и запись на ринг самому. */
public class FightScreen extends LifeScreen {
    private static final int[] BETS = {100, 500, 1000, 5000};
    private long lastPoll;

    public FightScreen() {
        super("Подвал");
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        NbtCompound f = PulseClient.fight;
        if (System.currentTimeMillis() - lastPoll > 1000) {
            lastPoll = System.currentTimeMillis();
            PulseClient.send("fight_state");
        }
        int w = Math.min(420, width - 30), h = Math.min(260, height - 24);
        int x = width / 2 - w / 2, y = height / 2 - h / 2;
        panel(ctx, x, y, w, h, 0xF01A0F0F, 0xF00A0606);
        title(ctx, "«ПОДВАЛ» · БУКМЕКЕР", x + 10, y + 8, 0xFFFF5252);
        String cash = "наличные $" + f.getInt("dirty") + " · карта $" + f.getInt("money");
        ctx.drawText(textRenderer, cash, x + w - 10 - textRenderer.getWidth(cash), y + 11, 0xFFFFE082, false);
        int cy = y + 32;
        String phase = f.getString("phase");
        boolean betting = phase.equals("BETS") && !f.getBoolean("playerBout");
        if (phase.equals("IDLE") || f.getString("a").isEmpty()) {
            wrap(ctx, "Сейчас ринг пуст. Следующий бой объявят через несколько секунд.", x + 14, cy, w - 28, 0xFFB0BEC5);
        } else {
            String state = switch (phase) {
                case "BETS" -> f.getBoolean("playerBout") ? "Сейчас начнётся бой с игроком" : "Ставки принимаются · " + f.getInt("timer") + " с";
                case "FIGHT" -> "Идёт бой — ставки закрыты";
                default -> "Бой окончен";
            };
            ctx.drawText(textRenderer, state, x + 14, cy, betting ? 0xFF7CFF7C : 0xFFFFC107, false);
            int colW = (w - 40) / 2;
            for (int side = 1; side <= 2; side++) {
                int bx = x + 14 + (side - 1) * (colW + 12), by = cy + 14;
                ctx.fill(bx, by, bx + colW, by + 72, side == 1 ? 0x60B71C1C : 0x601565C0);
                String name = f.getString(side == 1 ? "a" : "b");
                ctx.drawText(textRenderer, name, bx + 6, by + 5, 0xFFFFFFFF, true);
                double odds = f.getDouble(side == 1 ? "o1" : "o2");
                float pow = f.getFloat(side == 1 ? "pa" : "pb");
                ctx.drawText(textRenderer, "коэффициент ×" + String.format("%.1f", odds), bx + 6, by + 17, 0xFFFFE082, false);
                ctx.drawText(textRenderer, "сила " + "▮".repeat(Math.max(1, Math.round(pow * 5))), bx + 6, by + 28, 0xFFB0BEC5, false);
                for (int i = 0; i < BETS.length; i++) {
                    final int s = side, amount = BETS[i];
                    int bw = (colW - 15) / 2;
                    button(ctx, bx + 5 + (i % 2) * (bw + 5), by + 40 + (i / 2) * 16, bw, 14, "$" + amount, side == 1 ? 0xFFC62828 : 0xFF1E88E5,
                            betting && f.getInt("mySide") == 0, () -> {
                                NbtCompound n = new NbtCompound();
                                n.putInt("side", s);
                                n.putInt("amount", amount);
                                PulseClient.send("fight_bet", n);
                            });
                }
            }
            if (f.getInt("mySide") > 0)
                ctx.drawText(textRenderer, "Твоя ставка: $" + f.getInt("myBet") + " на " + f.getString(f.getInt("mySide") == 1 ? "a" : "b"),
                        x + 14, cy + 90, 0xFFFFE082, false);
        }
        // запись на ринг
        int ry = y + h - 74;
        ctx.drawText(textRenderer, "Выйти на ринг самому — только кулаки, 3 раунда, нокаут или решение судей:", x + 14, ry, 0xFFE0E0E0, false);
        NbtList tiers = f.getList("tiers", NbtElement.COMPOUND_TYPE);
        int tw = (w - 40) / 3;
        for (int i = 0; i < tiers.size(); i++) {
            NbtCompound t = tiers.getCompound(i);
            final int k = i;
            boolean ok = !phase.equals("FIGHT") && !phase.equals("RESULT") && (i < 2 || f.getInt("wins") >= 2);
            button(ctx, x + 14 + i * (tw + 6), ry + 14, tw, 18, t.getString("name") + " · $" + t.getInt("prize"), 0xFF8E24AA, ok, () -> {
                NbtCompound n = new NbtCompound();
                n.putInt("tier", k);
                PulseClient.send("fight_join", n);
                close();
            });
        }
        ctx.drawText(textRenderer, "Побед на ринге: " + f.getInt("wins") + (f.getInt("wins") < 2 ? " (к чемпиону — после двух)" : ""), x + 14, ry + 38, 0xFF9E9E9E, false);
        center(ctx, "Ставки и призы — наличными (грязными) · Esc — закрыть", x + w / 2, y + h - 12, 0xFF607D8B);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
