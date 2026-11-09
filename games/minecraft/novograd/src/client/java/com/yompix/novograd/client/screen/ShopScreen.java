package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.life.Shop;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;

/** Магазин: вкладки по категориям, иконки товаров, цена и кнопка «Купить». С телефона — доставка +20%. */
public class ShopScreen extends LifeScreen {
    private final boolean delivery;
    private boolean guns;
    private String tab = "Еда";
    private int scroll;
    private int boughtIndex = -1;
    private long boughtAt;

    public ShopScreen(boolean delivery, boolean guns) {
        this(delivery);
        this.guns = guns;
        if (guns) tab = "Оружие";
    }

    public ShopScreen(boolean delivery) {
        super("Магазин");
        this.delivery = delivery;
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        int w = Math.min(300, width - 20), h = Math.min(220, height - 20);
        int x = width / 2 - w / 2, y = (int) (height / 2 - h / 2 + (1 - a) * 30);
        panel(ctx, x, y, w, h, 0xFFFDFBF7, 0xFFF1ECE2);
        ctx.fill(x, y, x + w, y + 22, 0xFFE05A47);
        ctx.drawText(textRenderer, (guns ? "Оружие «Калибр» · 18+" : delivery ? "Магазин · доставка +20%" : "Супермаркет") + (NovogradClient.data.today.equals("fair") ? " · ЯРМАРКА −30%" : ""), x + 8, y + 7, 0xFFFFFFFF, true);
        String money = "$" + NovogradClient.data.money;
        ctx.drawText(textRenderer, money, x + w - 8 - textRenderer.getWidth(money), y + 7, 0xFFFFF4B0, true);
        List<String> tabs = new ArrayList<>();
        for (Shop.Entry e : Shop.CATALOG) if (!tabs.contains(e.category()) && Shop.gunCategory(e.category()) == guns) tabs.add(e.category());
        int tx = x + 6;
        for (String t : tabs) {
            int tw = textRenderer.getWidth(t) + 10;
            boolean on = t.equals(tab);
            roundRect(ctx, tx, y + 26, tw, 14, on ? 0xFFE05A47 : 0xFFE6DED0);
            ctx.drawText(textRenderer, t, tx + 5, y + 29, on ? 0xFFFFFFFF : 0xFF5A4A3A, false);
            area(tx, y + 26, tw, 14, () -> {
                tab = t;
                scroll = 0;
            });
            tx += tw + 4;
        }
        if (guns) // «Калибр» → Мастерская: обвесы и окраска
            button(ctx, x + w - 84, y + 26, 78, 14, "Мастерская", 0xFFD42A3A, true, () -> client.setScreen(new GunTuneScreen()));
        ctx.enableScissor(x, y + 44, x + w, y + h - 4);
        int ry = y + 46 - scroll;
        boolean adult = NovogradClient.data.adult();
        for (int i = 0; i < Shop.CATALOG.size(); i++) {
            Shop.Entry e = Shop.CATALOG.get(i);
            if (!e.category().equals(tab)) continue;
            final int idx = i;
            boolean flash = idx == boughtIndex && System.currentTimeMillis() - boughtAt < 400;
            roundRect(ctx, x + 6, ry, w - 12, 22, flash ? 0xFFC8F5D0 : 0xFFFFFFFF);
            ctx.drawItem(new ItemStack(e.item().get(), e.count()), x + 10, ry + 3);
            ctx.drawText(textRenderer, e.name(), x + 32, ry + 7, 0xFF333333, false);
            int price = Shop.price(e, delivery, NovogradClient.data.today);
            boolean locked = false;
            boolean afford = NovogradClient.data.money >= price;
            String label = locked ? "18+" : "$" + price;
            button(ctx, x + w - 66, ry + 4, 56, 14, label, afford ? 0xFF2FA84F : 0xFFB0A090, !locked && afford, () -> {
                NbtCompound n = new NbtCompound();
                n.putInt("item", idx);
                n.putBoolean("delivery", delivery);
                NovogradClient.send("shop_buy", n);
                boughtIndex = idx;
                boughtAt = System.currentTimeMillis();
            });
            ry += 25;
        }
        ctx.disableScissor();
        if (!adult && tab.equals("Транспорт")) center(ctx, "", x + w / 2, y + h - 14, 0xFFB04030);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll = Math.max(0, scroll - (int) (v * 14));
        return true;
    }
}
