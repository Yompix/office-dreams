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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Точка входа «всякого интересного»: уличные события, подработки скорой/пожарных/пиццы, война за районы,
 * парашют и прыжки, автоматы и дартс. Всё регистрируется отсюда одной строкой из LifeEvents.init.
 */
public final class FunHooks {
    private FunHooks() {}

    public static void init() {
        Skydive.register();
        ServerTickEvents.END_SERVER_TICK.register(StreetEvents::tick);
        ServerTickEvents.END_SERVER_TICK.register(VehicleJobs::tick);
        ServerTickEvents.END_SERVER_TICK.register(Territories::tick);
        ServerTickEvents.END_SERVER_TICK.register(Skydive::tick);
        ServerTickEvents.END_SERVER_TICK.register(Arcade::tick);
        ServerTickEvents.END_SERVER_TICK.register(FunHooks::tips);
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> onJoin(h.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            StreetEvents.onLeave(h.player);
            VehicleJobs.onLeave(h.player);
            Territories.onLeave(h.player);
            Skydive.onLeave(h.player);
            Arcade.onLeave(h.player);
        });
        // убитый игроком боец банды в чужом районе — объявление войны
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof CitizenEntity c && c.getRole() == CitizenEntity.Role.GANG && source.getAttacker() instanceof ServerPlayerEntity p)
                Territories.onGangKill(p, c);
        });
        // для проверки: /fun event snatch|crash|race|robbery|drunk|hitch|ambush, /fun war, /fun jump, /fun arcade|darts
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(literal("fun")
                .requires(s -> s.getServer().isSingleplayer() || s.hasPermissionLevel(2))
                .then(argument("what", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    String r = debug(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what"));
                    ctx.getSource().sendFeedback(() -> Text.literal(r), false);
                    return 1;
                }))));
    }

    public static String debug(ServerPlayerEntity p, String what) {
        String[] a = what.trim().toLowerCase(Locale.ROOT).split("\\s+");
        switch (a[0]) {
            case "event" -> {
                if (a.length < 2) return "event snatch|crash|race|robbery|drunk|hitch|ambush";
                StreetEvents.Kind k = StreetEvents.Kind.valueOf(a[1].toUpperCase(Locale.ROOT));
                return StreetEvents.start(p, k) ? "Событие: " + StreetEvents.name(k) : "Здесь не получилось: " + StreetEvents.name(k);
            }
            case "war" -> { return Territories.debugWar(p) ? "Война началась" : "Ты не в районе банды"; }
            case "jump" -> { return Skydive.challenge(p) ? "Прыжок!" : "Нет мишени"; }
            case "arcade", "darts" -> {
                Arcade.open(p, a[0], 50);
                return "Открыто";
            }
            case "act" -> {
                LifeData d = Life.get(p);
                d.chapter = a.length > 1 ? Integer.parseInt(a[1]) : 3;
                Life.sync(p);
                return "Акт " + (d.chapter + 1);
            }
            default -> { return "event … | war | jump | arcade | darts | act N"; }
        }
    }

    /** Пакеты от клиента, которых нет в LifeNet. true — обработан. */
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "chute_open" -> Skydive.open(p);
            case "hose" -> VehicleJobs.hose(p, d.getBoolean("on"));
            case "arcade_done" -> Arcade.done(p, d);
            case "arcade_again" -> Arcade.open(p, "arcade", 0);
            case "turf_please" -> Territories.send(p);
            default -> { return false; }
        }
        return true;
    }

    public static void onDownfall(ServerPlayerEntity p, boolean busted) {
        StreetEvents.onDownfall(p);
        VehicleJobs.onDownfall(p);
        Territories.onDownfall(p);
        Skydive.onDownfall(p);
    }

    private static void onJoin(ServerPlayerEntity p) {
        Territories.send(p);
        sendPlaces(p);
    }

    /** Значки новых мест на карте и радаре (клиент добавляет их к местам сюжета). */
    private static final String[][] PLACES = {
            {"airfield_hangar+16,10", "fun_airfield_skydive", "Парашютный клуб"},
            {"hospital+9,4", "fun_hospital_ambulance", "Скорая помощь (J в фургоне)"},
            {"hospital+-9,4", "fun_fire_station", "Пожарная часть (J в машине)"},
            {"pizza_door+5,5", "fun_pizza_scooter", "Скутер пиццерии (J)"},
            {"bar_door+3,-3", "fun_bar_arcade", "Автомат и дартс"},
            {"riv_strip+6,-6", "fun_club_arcade", "Автомат и дартс (Ривьера)"},
            {"gunshop_door+3,3", "fun_gun_parachute", "Парашют — $300"},
    };

    public static void sendPlaces(ServerPlayerEntity p) {
        NbtList l = new NbtList();
        for (String[] pl : PLACES) {
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), pl[0]);
            if (at == null) continue;
            NbtCompound c = new NbtCompound();
            c.putString("id", pl[1]);
            c.putString("name", pl[2]);
            c.putInt("x", (int) Math.floor(at.x));
            c.putInt("z", (int) Math.floor(at.z));
            l.add(c);
        }
        NbtCompound n = new NbtCompound();
        n.put("list", l);
        LifeNet.send(p, "fun_places", n);
    }

    /** Подсказки в телефон, когда новое становится доступно. */
    private static void tips(MinecraftServer s) {
        if (s.getTicks() % 200 != 77) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            LifeData d = Life.get(p);
            if (p.age > 20 * 60 * 6 && d.flags.add("tips_fun"))
                Life.message(p, "Новоград-гид", "Город живёт: на улицах то вор с сумкой, то авария, то гонщик зовёт погоняться (мигни фарами — L). "
                        + "Подработки на J: скорая (белый фургон у больницы), пожарные (красный грузовик), пицца (любой скутер). 12 уровней — особое умение. "
                        + "Парашют — у оружейника, прыжки на точность — в клубе на аэродроме. В барах — автоматы и дартс.");
            if (Economy.act(d) >= Territories.ACT_NEEDED && d.flags.add("tips_turf_act"))
                Life.message(p, "Свои", "Ты теперь свой в криминальном мире. Ящеры и Синие делят город — на карте (M) видно их районы. Хочешь свой кусок? Зайди в чужой район и убей их бойца.");
        }
    }
}
