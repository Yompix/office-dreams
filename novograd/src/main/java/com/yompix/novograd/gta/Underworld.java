package com.yompix.novograd.gta;

import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Преступный мир Новограда — «Тёмные дела» (телефон → «Дела»):
 *  • большие ограбления с подготовкой, как в GTA V (BigHeists): казино «Корона», порт, Сити-Банк;
 *  • заказные убийства по телефону (Contracts);
 *  • контрабанда на катере и самолёте (Smuggling).
 * Всё регистрируется отсюда одной строкой из LifeEvents.init; пакеты клиента — с префиксом «uw_».
 * Для звонков сюжета и других систем — простые входы: BigHeists.offer(p, id), Contracts.offer(p), Smuggling.offer(p).
 */
public final class Underworld {
    private Underworld() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Underworld::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof CitizenEntity c) {
                Contracts.onDeath(c, source);
                BigHeists.onDeath(c, source);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            BigHeists.onLeave(h.player);
            Contracts.onLeave(h.player);
            Smuggling.onLeave(h.player);
        });
        // для проверки: /uw heist casino|port|bank [prep], /uw contract, /uw smuggle, /uw state
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(literal("uw")
                .requires(s -> s.getServer().isSingleplayer() || s.hasPermissionLevel(2))
                .then(argument("what", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    String r = debug(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what"));
                    ctx.getSource().sendFeedback(() -> Text.literal(r), false);
                    return 1;
                }))));
    }

    private static void tick(MinecraftServer s) {
        BigHeists.tick(s);
        Contracts.tick(s);
        Smuggling.tick(s);
    }

    /** Пакеты телефона «Дела». true — обработан. */
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "uw_state" -> {}
            case "uw_bh" -> BigHeists.action(p, d.getString("h"), d.getString("a"), d.getInt("v"));
            case "uw_ct" -> Contracts.action(p, d.getString("a"), d.getInt("v"));
            case "uw_sm" -> Smuggling.action(p, d.getString("a"), d.getInt("v"));
            default -> { return false; }
        }
        sendState(p);
        return true;
    }

    /** Всё для экрана «Дела»: ограбления, заказы, контрабанда. */
    public static void sendState(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        LifeData d = Life.get(p);
        n.putInt("act", Economy.act(d));
        n.putBoolean("board", BigHeists.atBoard(p));
        n.put("heists", BigHeists.state(p));
        n.put("contracts", Contracts.state(p));
        n.put("smuggle", Smuggling.state(p));
        LifeNet.send(p, "uw_state", n);
    }

    /** Подсказки на экране во время дела (слева): строки, первая — заголовок. Пусто — убрать. */
    public static void hud(ServerPlayerEntity p, List<String> lines) {
        NbtCompound n = new NbtCompound();
        NbtList l = new NbtList();
        for (String s : lines) l.add(NbtString.of(s));
        n.put("lines", l);
        LifeNet.send(p, "uw_hud", n);
    }

    public static boolean busy(ServerPlayerEntity p) {
        return BigHeists.active(p) || Contracts.active(p) || Smuggling.active(p);
    }

    public static void onDownfall(ServerPlayerEntity p, boolean busted) {
        BigHeists.onDownfall(p, busted);
        Contracts.onDownfall(p, busted);
        Smuggling.onDownfall(p, busted);
    }

    /** Снимок камерой телефона (разведка ограбления). */
    public static void onPhoto(ServerPlayerEntity p) {
        BigHeists.onPhoto(p);
    }

    public static String debug(ServerPlayerEntity p, String what) {
        String[] a = what.trim().toLowerCase(Locale.ROOT).split("\\s+");
        LifeData d = Life.get(p);
        switch (a[0]) {
            case "heist" -> {
                String id = a.length > 1 ? a[1] : "port";
                if (a.length > 2 && a[2].equals("prep")) {
                    BigHeists.debugPrep(p, id, a.length > 3 && a[3].equals("loud"));
                    return "Подготовка готова: " + id;
                }
                if (a.length > 2 && a[2].equals("go")) return BigHeists.debugGo(p, id, a.length > 3 && a[3].equals("loud"));
                BigHeists.offer(p, id);
                return "Предложено: " + id;
            }
            case "contract" -> {
                Contracts.offer(p);
                if (a.length > 1) return Contracts.debugTake(p, Integer.parseInt(a[1]));
                return "Заказы предложены";
            }
            case "smuggle" -> {
                Smuggling.offer(p);
                if (a.length > 1) return Smuggling.debugTake(p, Integer.parseInt(a[1]));
                return "Контрабанда предложена";
            }
            case "cancel" -> {
                onDownfall(p, false);
                return "Отменено";
            }
            case "state" -> {
                sendState(p);
                return "act " + Economy.act(d) + " dirty " + d.dirty + " " + BigHeists.debug(p) + " | " + Contracts.debug(p) + " | " + Smuggling.debug(p);
            }
            default -> { return "heist casino|port|bank [prep|go [loud]] | contract [i] | smuggle [i] | cancel | state"; }
        }
    }
}
