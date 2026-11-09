package com.yompix.novograd.world;

import com.yompix.novograd.Novograd;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Сеть для кафе, супермаркетов, метро и «Миража»: одно меню на всё (заголовок, пункты с ценой, выбор → сервер),
 * подсказки поездки в метро и затемнение. Сервер всё перепроверяет (расстояние, деньги).
 */
public final class CityNet {
    private CityNet() {}

    public record S2C(NbtCompound data) implements CustomPayload {
        public static final Id<S2C> ID = new Id<>(Novograd.id("city_s2c"));
        public static final PacketCodec<RegistryByteBuf, S2C> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, S2C::data, S2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S(NbtCompound data) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(Novograd.id("city_c2s"));
        public static final PacketCodec<RegistryByteBuf, C2S> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, C2S::data, C2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static boolean ready;

    static void init() {
        if (ready) return;
        ready = true;
        PayloadTypeRegistry.playS2C().register(S2C.ID, S2C.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S.ID, C2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(C2S.ID, (payload, ctx) -> {
            ServerPlayerEntity p = ctx.player();
            NbtCompound d = payload.data();
            try {
                switch (d.getString("type")) {
                    case "pick" -> {
                        String c = d.getString("ctx");
                        if (c.startsWith("metro")) Metro.pick(p, c, d.getInt("i"));
                        else Venues.pick(p, c, d.getInt("i"));
                    }
                    case "board" -> Metro.board(p);
                    case "hijack" -> Metro.hijack(p);
                    case "skip" -> Metro.skipMenu(p);
                    default -> {}
                }
            } catch (RuntimeException e) {
                Novograd.LOG.warn("CityNet: ошибка", e);
            }
        });
    }

    public static void send(ServerPlayerEntity p, String type, NbtCompound n) {
        n.putString("type", type);
        if (ServerPlayNetworking.canSend(p, S2C.ID)) ServerPlayNetworking.send(p, new S2C(n));
    }

    /** Меню: ctx — что выбираем (сервер узнает по нему, к чему относится выбор). */
    public static NbtCompound menu(String ctx, String title, String sub, int color) {
        NbtCompound n = new NbtCompound();
        n.putString("ctx", ctx);
        n.putString("title", title);
        n.putString("sub", sub);
        n.putInt("color", color);
        n.put("items", new NbtList());
        return n;
    }

    public static void item(NbtCompound menu, String label, String desc, int price, boolean ok) {
        NbtCompound e = new NbtCompound();
        e.putString("label", label);
        e.putString("desc", desc);
        e.putInt("price", price);
        e.putBoolean("ok", ok);
        menu.getList("items", 10).add(e);
    }
}
