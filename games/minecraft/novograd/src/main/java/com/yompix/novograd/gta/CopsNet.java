package com.yompix.novograd.gta;

import com.yompix.novograd.Novograd;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Свой канал «полиция»: умная погоня ({@link Pursuit}), маски ({@link Disguise}), тюрьма ({@link Prison}),
 * «Полицейские и воры» ({@link CopsAndRobbers}). Тик сервера раскидан по разным тикам секунды — чтобы не грузить слабый ноутбук.
 */
public final class CopsNet {
    private CopsNet() {}

    public record S2C(String type, NbtCompound data) implements CustomPayload {
        public static final Id<S2C> ID = new Id<>(Novograd.id("cops_s2c"));
        public static final PacketCodec<RegistryByteBuf, S2C> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, S2C::type, PacketCodecs.NBT_COMPOUND, S2C::data, S2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S(String type, NbtCompound data) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(Novograd.id("cops_c2s"));
        public static final PacketCodec<RegistryByteBuf, C2S> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, C2S::type, PacketCodecs.NBT_COMPOUND, C2S::data, C2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(S2C.ID, S2C.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S.ID, C2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(C2S.ID, (payload, ctx) -> {
            try {
                handle(ctx.player(), payload.type(), payload.data());
            } catch (RuntimeException e) {
                Novograd.LOG.warn("[cops] ошибка пакета {}", payload.type(), e);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(CopsNet::tick);
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> {
            Disguise.onJoin(h.player);
            Prison.onJoin(h.player);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> server.execute(() -> CopsAndRobbers.onLeave(h.player)));
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
            d.register(CommandManager.literal("cnr")
                    .then(CommandManager.literal("start").executes(c -> {
                        CopsAndRobbers.start(c.getSource().getServer(), c.getSource().getPlayer());
                        return 1;
                    }))
                    .then(CommandManager.literal("stop").executes(c -> {
                        CopsAndRobbers.stop(c.getSource().getServer(), "остановлено командой");
                        return 1;
                    }))
                    .executes(c -> {
                        if (c.getSource().getPlayer() != null) CopsAndRobbers.menu(c.getSource().getPlayer());
                        return 1;
                    }));
            d.register(CommandManager.literal("jail").requires(s -> s.hasPermissionLevel(2))
                    .then(CommandManager.argument("stars", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5)).executes(c -> {
                        ServerPlayerEntity p = c.getSource().getPlayer();
                        if (p != null) Prison.jail(p, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "stars"), 0);
                        return 1;
                    })));
            d.register(CommandManager.literal("pursuit").requires(s -> s.hasPermissionLevel(2)).executes(c -> {
                ServerPlayerEntity p = c.getSource().getPlayer();
                if (p != null) c.getSource().sendFeedback(() -> Text.literal(Pursuit.debug(p)), false);
                return 1;
            }));
        });
    }

    public static void send(ServerPlayerEntity p, String type, NbtCompound data) {
        if (ServerPlayNetworking.canSend(p, S2C.ID)) ServerPlayNetworking.send(p, new S2C(type, data));
    }

    private static void tick(MinecraftServer server) {
        int t = server.getTicks();
        try {
            Pursuit.tick(server, t);
            if (t % 20 == 5) Disguise.tick(server);
            if (t % 20 == 10) Prison.tick(server);
            if (t % 20 == 15) CopsAndRobbers.tick(server);
        } catch (RuntimeException e) {
            if (t % 200 == 0) Novograd.LOG.warn("[cops] ошибка тика", e);
        }
    }

    private static void handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "mask_toggle" -> Disguise.toggle(p);
            case "menu" -> {
                String id = d.getString("id"), kind = d.getString("kind");
                switch (kind) {
                    case "masks" -> Disguise.click(p, id);
                    case "jail" -> Prison.click(p, id);
                    case "cnr" -> CopsAndRobbers.click(p, id);
                    default -> {}
                }
            }
            case "jail_menu" -> Prison.menu(p);
            case "cnr_menu" -> CopsAndRobbers.menu(p);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ меню (один экран на всё: строки и кнопки)
    /** Собрать меню: kind — кто обработает нажатие, кнопки — пары id/подпись (пустой id — неактивна). */
    public static final class Menu {
        final NbtCompound n = new NbtCompound();
        final net.minecraft.nbt.NbtList lines = new net.minecraft.nbt.NbtList(), buttons = new net.minecraft.nbt.NbtList();

        public Menu(String kind, String title, int accent) {
            n.putString("kind", kind);
            n.putString("title", title);
            n.putInt("accent", accent);
        }

        public Menu line(String s) {
            lines.add(net.minecraft.nbt.NbtString.of(s));
            return this;
        }

        public Menu button(String id, String label, int color, boolean enabled) {
            NbtCompound b = new NbtCompound();
            b.putString("id", id);
            b.putString("label", label);
            b.putInt("color", color);
            b.putBoolean("on", enabled);
            buttons.add(b);
            return this;
        }

        public void open(ServerPlayerEntity p) {
            n.put("lines", lines);
            n.put("buttons", buttons);
            send(p, "menu", n);
        }
    }
}
