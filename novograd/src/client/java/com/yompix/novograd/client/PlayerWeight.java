package com.yompix.novograd.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

/** Вес игроков на клиенте: свой — из данных жизни, друзей — из списка игроков (раз в 2 с). */
public final class PlayerWeight {
    private PlayerWeight() {}

    public static float kg(PlayerEntity p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && p.getUuid().equals(mc.player.getUuid())) return NovogradClient.data.weight;
        var list = com.yompix.novograd.client.story.ClientStory.players;
        if (list != null) for (var e : list.getList("list", 10)) {
            var c = (net.minecraft.nbt.NbtCompound) e;
            if (c.getString("name").equals(p.getName().getString()) && c.contains("kg")) return c.getFloat("kg");
        }
        return 62;
    }

    /** 62 кг — обычная ширина; 100 кг — на треть шире; 50 — чуть уже. */
    public static float width(PlayerEntity p) {
        return 1f; // в Новограде веса нет — все обычного телосложения (ширина от силы — в PlayerStyleScaleMixin)
    }
}
