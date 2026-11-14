package com.yompix.glukhoman.net;

import com.yompix.glukhoman.Glukhoman;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.BiConsumer;

/**
 * Один пакет в каждую сторону, внутри NBT с полем "t" (тип).
 * S2C: hud (глава/цель), sanity, sub (субтитр), title, fx (тряска/вспышка/затемнение/звук), note, choice, credits.
 * C2S: light (фонарик), choice, difficulty, prefs (меньше скримеров), hint.
 */
public final class Net {
    private Net() {}

    public record S2C(NbtCompound data) implements CustomPayload {
        public static final Id<S2C> ID = new Id<>(Glukhoman.id("s2c"));
        public static final PacketCodec<RegistryByteBuf, S2C> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, S2C::data, S2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S(NbtCompound data) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(Glukhoman.id("c2s"));
        public static final PacketCodec<RegistryByteBuf, C2S> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, C2S::data, C2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void init(BiConsumer<ServerPlayerEntity, NbtCompound> handler) {
        PayloadTypeRegistry.playS2C().register(S2C.ID, S2C.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S.ID, C2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(C2S.ID, (p, ctx) -> ctx.server().execute(() -> handler.accept(ctx.player(), p.data())));
    }

    public static NbtCompound msg(String type) {
        NbtCompound n = new NbtCompound();
        n.putString("t", type);
        return n;
    }

    public static void send(ServerPlayerEntity p, NbtCompound n) {
        if (p instanceof net.fabricmc.fabric.api.entity.FakePlayer) return;
        if (ServerPlayNetworking.canSend(p, S2C.ID)) ServerPlayNetworking.send(p, new S2C(n));
    }

    public static void all(MinecraftServer s, NbtCompound n) {
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) send(p, n);
    }
}
