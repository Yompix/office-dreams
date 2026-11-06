package com.yompix.novograd.client;

import com.yompix.novograd.client.screen.RealEstateScreen;
import com.yompix.novograd.client.story.ClientStory;
import com.yompix.novograd.world.Housing;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.HashSet;
import java.util.Set;

/** Недвижимость на клиенте: каталог риелтора (телефон → «Дом») и значки жилья/гаражей на радаре и карте. */
public final class HousingClient {
    private HousingClient() {}

    /** Последний каталог от сервера (list, money, dirty, safe, here, cars). */
    public static NbtCompound data = new NbtCompound();
    public static long version;
    private static NbtList pins = new NbtList();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(Housing.S2C.ID, (payload, ctx) -> receive(ctx.client(), payload.data()));
        // «places» с сервера заменяет весь список — значки жилья подмешиваем заново
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.player != null && c.player.age % 20 == 0) inject();
        });
        HousingAutotest.init();
    }

    private static void receive(MinecraftClient c, NbtCompound n) {
        switch (n.getString("type")) {
            case "list" -> {
                data = n;
                version++;
            }
            case "pins" -> {
                pins = n.getList("pins", NbtElement.COMPOUND_TYPE);
                inject();
            }
            case "open" -> c.setScreen(new RealEstateScreen());
            default -> {}
        }
    }

    private static void inject() {
        NbtCompound pl = ClientStory.places;
        Set<String> keep = new HashSet<>();
        for (NbtElement e : pins) {
            NbtCompound p = (NbtCompound) e;
            String id = p.getString("id");
            keep.add(id);
            NbtCompound v = new NbtCompound();
            v.putInt("x", p.getInt("x"));
            v.putInt("z", p.getInt("z"));
            v.putString("name", p.getString("name"));
            pl.put(id, v);
        }
        for (String k : new HashSet<>(pl.getKeys())) if (k.startsWith("realty_") && !keep.contains(k)) pl.remove(k);
    }

    public static void send(String type, NbtCompound n) {
        n.putString("type", type);
        ClientPlayNetworking.send(new Housing.C2S(n));
    }

    public static void send(String type) {
        send(type, new NbtCompound());
    }
}
