package com.yompix.novograd.client.screen;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.client.ArsenalClient;
import com.yompix.novograd.gta.Arsenal;
import com.yompix.novograd.item.GunItem;
import com.yompix.novograd.item.ThrowableItem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Колесо оружия: держи TAB, веди мышь в сторону оружия, отпусти — оно в руке.
 * Оружие из рюкзака встаёт в текущую ячейку панели. Сверху — «Кулаки» (пустая ячейка).
 */
public class WeaponWheelScreen extends Screen {
    private static final int PINK = 0xFFFF3EA5, CYAN = 0xFF35E0FF, GOLD = 0xFFFFC94A;

    private record Choice(int slot, ItemStack stack, String name, int accent) {}

    private final List<Choice> choices = new ArrayList<>();
    private int hovered = -1;
    private long openedAt;
    private boolean done;

    public WeaponWheelScreen() {
        super(Text.literal("Оружие"));
    }

    public static boolean hasWeapons(PlayerEntity p) {
        for (int i = 0; i < p.getInventory().main.size(); i++) if (Arsenal.isWeapon(p.getInventory().getStack(i))) return true;
        return false;
    }

    @Override
    protected void init() {
        openedAt = System.currentTimeMillis();
        choices.clear();
        var p = client.player;
        if (p == null) return;
        var inv = p.getInventory();
        choices.add(new Choice(-1, ItemStack.EMPTY, "Кулаки", GOLD));
        for (Item it : Arsenal.weapons()) {
            int best = -1;
            if (inv.getStack(inv.selectedSlot).isOf(it)) best = inv.selectedSlot;
            for (int i = 0; i < inv.main.size() && best < 0; i++) if (inv.getStack(i).isOf(it)) best = i;
            if (best < 0) continue;
            ItemStack s = inv.getStack(best);
            int accent = s.getItem() instanceof GunItem ? CYAN : s.getItem() instanceof ThrowableItem ? PINK : GOLD;
            choices.add(new Choice(best, s, s.getName().getString(), accent));
        }
        // текущее оружие — подсвечено сразу
        ItemStack cur = inv.getStack(inv.selectedSlot);
        for (int i = 0; i < choices.size(); i++) if (choices.get(i).slot == inv.selectedSlot || cur.isEmpty() && choices.get(i).slot < 0) hovered = i;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // без размытия: мир виден, только затемнение
    }

    @Override
    public void tick() {
        // отпустил TAB — берём выбранное
        if (!ArsenalClient.held(ArsenalClient.wheelKey()) && System.currentTimeMillis() - openedAt > 60) pick();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == ArsenalClient.wheelKey()) {
            pick();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        pick();
        return true;
    }

    private void pick() {
        if (done) return;
        done = true;
        var p = client.player;
        if (p != null && hovered >= 0 && hovered < choices.size()) {
            Choice c = choices.get(hovered);
            var inv = p.getInventory();
            if (c.slot < 0) {
                for (int i = 0; i < 9; i++)
                    if (inv.getStack(i).isEmpty()) {
                        inv.selectedSlot = i;
                        break;
                    }
            } else if (c.slot < 9) {
                inv.selectedSlot = c.slot;
            } else {
                ArsenalClient.send("wheel", c.slot);
            }
            p.playSound(net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), 0.5f, 1.4f);
        }
        close();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float k = Math.min(1f, (System.currentTimeMillis() - openedAt) / 140f);
        float ease = 1 - (1 - k) * (1 - k);
        int n = Math.max(1, choices.size());
        float cx = width / 2f, cy = height / 2f;
        float r2 = Math.min(110, Math.min(width, height) * 0.36f) * (0.85f + 0.15f * ease), r1 = r2 * 0.4f;
        // выбор направлением мыши
        double dx = mouseX - cx, dy = mouseY - cy;
        if (dx * dx + dy * dy > r1 * r1 * 0.35) {
            double ang = Math.atan2(dx, -dy);
            if (ang < 0) ang += Math.PI * 2;
            double seg = Math.PI * 2 / n;
            hovered = (int) Math.floor(((ang + seg / 2) % (Math.PI * 2)) / seg);
        }
        int dim = (int) (0x88 * ease) << 24;
        ctx.fill(0, 0, width, height, dim);
        ctx.draw();
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        float seg = (float) (Math.PI * 2 / n), gap = n > 1 ? 0.035f : 0f;
        for (int i = 0; i < n; i++) {
            Choice c = choices.get(i);
            boolean on = i == hovered;
            float a0 = i * seg - seg / 2 + gap, a1 = i * seg + seg / 2 - gap;
            float out = on ? r2 + 6 : r2;
            ArsenalClient.ring(m, cx, cy, r1, out, a0, a1, on ? 0xE01A1A19 : 0xC0101018, 12);
            ArsenalClient.ring(m, cx, cy, out - (on ? 4 : 2), out, a0, a1, on ? c.accent : (c.accent & 0x00FFFFFF) | 0x70000000, 12);
        }
        ArsenalClient.ring(m, cx, cy, 0, r1 - 4, 0, (float) (Math.PI * 2), 0xD80A0A12, 40);
        ArsenalClient.ring(m, cx, cy, r1 - 6, r1 - 4, 0, (float) (Math.PI * 2), 0x90FF3EA5, 40);
        // иконки
        var inv = client.player == null ? null : client.player.getInventory();
        for (int i = 0; i < n; i++) {
            Choice c = choices.get(i);
            boolean on = i == hovered;
            float mid = (r1 + r2) / 2f + (on ? 3 : 0);
            float ix = cx + (float) Math.sin(i * seg) * mid, iy = cy - (float) Math.cos(i * seg) * mid;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(ix, iy, 100);
            float sc = on ? 1.7f : 1.35f;
            ctx.getMatrices().scale(sc, sc, 1);
            if (c.slot < 0) ctx.drawCenteredTextWithShadow(textRenderer, "✊", 0, -4, on ? GOLD : 0xFFCCCCCC);
            else ctx.drawItem(c.stack, -8, -8);
            ctx.getMatrices().pop();
            String sub = sub(c, inv);
            if (!sub.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, sub, (int) ix, (int) iy + 13, on ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
        // центр: название и патроны
        if (hovered >= 0 && hovered < n) {
            Choice c = choices.get(hovered);
            String name = c.name;
            float s = textRenderer.getWidth(name) > r1 * 1.6f ? 1f : 1.25f;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy - 8, 100);
            ctx.getMatrices().scale(s, s, 1);
            ctx.drawCenteredTextWithShadow(textRenderer, name, 0, 0, c.accent);
            ctx.getMatrices().pop();
            String info = info(c, inv);
            if (!info.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, info, (int) cx, (int) cy + 6, 0xFFDDDDDD);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, "ОРУЖИЕ", (int) cx, (int) (cy - r2 - 22), PINK);
        ctx.drawCenteredTextWithShadow(textRenderer, "веди мышь · отпусти TAB — взять", (int) cx, (int) (cy + r2 + 14), 0xFF9FE9FF);
    }

    private static String sub(Choice c, net.minecraft.entity.player.PlayerInventory inv) {
        if (c.stack.getItem() instanceof GunItem g) return g.mag(c.stack) + "/" + g.magSize(c.stack);
        if (c.stack.getItem() instanceof ThrowableItem && inv != null) return "×" + inv.count(c.stack.getItem());
        return "";
    }

    private static String info(Choice c, net.minecraft.entity.player.PlayerInventory inv) {
        if (inv == null) return "";
        if (c.stack.getItem() instanceof GunItem g)
            return "магазин " + g.mag(c.stack) + "/" + g.magSize(c.stack) + " · запас " + (inv.player.isCreative() ? "∞" : inv.count(g.ammoItem()));
        if (c.stack.getItem() instanceof ThrowableItem) return "осталось " + inv.count(c.stack.getItem());
        if (c.slot < 0) return "руки свободны";
        return "ближний бой";
    }
}
