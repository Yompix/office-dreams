package com.yompix.novograd.client.city;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;

/** 0.9.6: тебе предлагают машину — принять (деньги спишутся с карты) или отказаться. */
public class CarOfferScreen extends LifeScreen {
    private final NbtCompound offer;

    public CarOfferScreen(NbtCompound offer) {
        super("Машина");
        this.offer = offer;
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        int cx = width / 2, cy = height / 2;
        int price = offer.getInt("price");
        Ui.panel(ctx, cx - 130, cy - 50, 260, 100, Ui.GOLD);
        Ui.big(ctx, textRenderer, offer.getString("car").toUpperCase(), cx, cy - 40, 1.4f, Ui.WHITE, 1);
        center(ctx, "от " + offer.getString("from") + " · " + (price > 0 ? "$" + price : "даром"), cx, cy - 18, Ui.GOLD);
        button(ctx, cx - 120, cy + 20, 115, 18, "ПРИНЯТЬ", Ui.MONEY, NovogradClient.data.money >= price, () -> answer("accept"));
        button(ctx, cx + 5, cy + 20, 115, 18, "ОТКАЗАТЬСЯ", 0xFF6E6D69, true, () -> answer("decline"));
    }

    private void answer(String a) {
        NbtCompound n = new NbtCompound();
        n.putString("act", a);
        NovogradClient.send("mycars", n);
        close();
    }
}
