package com.yompix.novograd.gta;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.Life;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * «Пульс города» — точка входа: коллекции (граффити, тайники, фото-точки, уникальные прыжки), режим «Хаос»,
 * уличные экраны «Новоград-24», вертолёт телеканала, ночные прожекторы и сирены, подпольные бои.
 * Свой сетевой канал (pulse_s2c / pulse_c2s), чтобы не трогать общие пакеты LifeNet.
 */
public final class Pulse {
    private Pulse() {}

    public record S2C(String type, NbtCompound data) implements CustomPayload {
        public static final Id<S2C> ID = new Id<>(Novograd.id("pulse_s2c"));
        public static final PacketCodec<RegistryByteBuf, S2C> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, S2C::type, PacketCodecs.NBT_COMPOUND, S2C::data, S2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S(String type, NbtCompound data) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(Novograd.id("pulse_c2s"));
        public static final PacketCodec<RegistryByteBuf, C2S> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, C2S::type, PacketCodecs.NBT_COMPOUND, C2S::data, C2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static boolean inited;

    public static void init() {
        if (inited) return;
        inited = true;
        PayloadTypeRegistry.playS2C().register(S2C.ID, S2C.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S.ID, C2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(C2S.ID, (payload, ctx) -> {
            ServerPlayerEntity p = ctx.player();
            try {
                handle(p, payload.type(), payload.data());
            } catch (RuntimeException e) {
                Novograd.LOG.warn("Пульс: ошибка пакета {}", payload.type(), e);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            try {
                Collectibles.tick(s);
                Chaos.tick(s);
                NewsTv.tick(s);
                NewsHeli.tick(s);
                FightClub.tick(s);
            } catch (RuntimeException e) {
                Novograd.LOG.warn("Пульс: ошибка тика", e);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> {
            Collectibles.onJoin(h.player);
            NewsTv.onJoin(h.player);
            Chaos.sendState(h.player);
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            NewsHeli.onLeave(h.player);
            FightClub.onLeave(h.player);
            Collectibles.onLeave(h.player);
        });
        // бои: урон между бойцами и по игроку на ринге считает FightClub (без розыска и без смерти)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(FightClub::allowDamage);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(literal("pulse")
                .requires(s -> s.getServer().isSingleplayer() || s.hasPermissionLevel(2))
                .then(argument("what", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    String r = debug(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what"));
                    ctx.getSource().sendFeedback(() -> Text.literal(r), false);
                    return 1;
                }))));
    }

    public static void send(ServerPlayerEntity p, String type, NbtCompound n) {
        if (ServerPlayNetworking.canSend(p, S2C.ID)) ServerPlayNetworking.send(p, new S2C(type, n));
    }

    private static void handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "col_state" -> Collectibles.sendState(p, false);
            case "col_buy" -> Collectibles.buyMap(p, d.getString("cat"));
            case "col_repair" -> Collectibles.freeRepair(p);
            case "chaos" -> Chaos.set(p, d.getBoolean("on"));
            case "fight_bet" -> FightClub.bet(p, d.getInt("side"), d.getInt("amount"));
            case "fight_join" -> FightClub.join(p, d.getInt("tier"));
            case "fight_state" -> FightClub.sendState(p);
            default -> Novograd.LOG.debug("Пульс: неизвестный пакет {}", type);
        }
    }

    /** /pulse … — для проверки. */
    public static String debug(ServerPlayerEntity p, String what) {
        String[] a = what.trim().toLowerCase(Locale.ROOT).split("\\s+");
        switch (a[0]) {
            case "col" -> {
                if (a.length >= 3 && a[1].equals("give")) return Collectibles.debugGive(p, a[2], a.length > 3 ? Integer.parseInt(a[3]) : 1);
                if (a.length >= 3 && a[1].equals("tp")) return Collectibles.debugTp(p, a[2], a.length > 3 ? Integer.parseInt(a[3]) : 0);
                if (a.length >= 3 && a[1].equals("view")) return Collectibles.debugView(p, Integer.parseInt(a[2]));
                if (a.length >= 2 && a[1].equals("map")) { for (String c : Collectibles.CATS) Life.get(p).flags.add("colmap:" + c); Collectibles.sendState(p, false); return "Карты куплены"; }
                if (a.length >= 2 && a[1].equals("list")) return Collectibles.debugList(a.length > 2 ? a[2] : "tag");
                return "col give tag|photo|jump|pkg N · col tp tag|photo|jump|pkg|stall i · col map · col list cat";
            }
            case "chaos" -> {
                Chaos.set(p, a.length < 2 || !a[1].equals("off"));
                return "Хаос: " + Chaos.on(p);
            }
            case "heli" -> {
                Wanted.add(p, 3, "проверка вертолёта", 3);
                NewsHeli.spawnFor(p);
                return "Вертолёт «Новоград-24» вылетел";
            }
            case "tv" -> {
                if (a.length > 2 && a[1].equals("view")) return NewsTv.debugView(p, Integer.parseInt(a[2]));
                return NewsTv.debugTp(p, a.length > 1 ? Integer.parseInt(a[1]) : 0);
            }
            case "fight" -> {
                if (a.length > 1 && a[1].equals("tp")) return FightClub.debugTp(p);
                if (a.length > 1 && a[1].equals("join")) { FightClub.join(p, a.length > 2 ? Integer.parseInt(a[2]) : 0); return "На ринг"; }
                FightClub.openScreen(p);
                return "Бои";
            }
            default -> { return "col … | chaos [off] | heli | tv N | fight [tp|join N]"; }
        }
    }
}
