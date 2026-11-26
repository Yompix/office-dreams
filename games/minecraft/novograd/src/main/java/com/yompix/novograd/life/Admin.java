package com.yompix.novograd.life;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yompix.novograd.Novograd;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.9.6: сервер на много игроков.
 * <ul>
 *   <li>{@code config/novograd-server.json} — настройки сервера: множители заработка и цен, PvP, сложность полиции,
 *       сколько игроков в одном ограблении, грабители на улицах, лимит пакетов от игрока;</li>
 *   <li>{@code /novograd} (уровень прав 3 — оператор): деньги (дать/забрать/задать: карта, наличные, грязные),
 *       сброс игрока, телепорт, кик и бан (ванильные), статистика сервера, перечитать настройки;</li>
 *   <li>ограничение частоты пакетов мода от одного игрока ({@link #allow}).</li>
 * </ul>
 */
public final class Admin {
    private Admin() {}

    /** Настройки сервера (читаются при запуске и по {@code /novograd reload}). */
    public static final class Config {
        public double earnMul = 1.0, priceMul = 1.0;
        public boolean pvp = true;
        public double policeDifficulty = 1.0;
        public int maxHeistPlayers = 4;
        public boolean muggers = true;
        public int packetsPerSecond = 60;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Config cfg = new Config();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("novograd-server.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) cfg = GSON.fromJson(Files.readString(f), Config.class);
            if (cfg == null) cfg = new Config();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(cfg));
        } catch (Exception e) {
            Novograd.LOG.warn("novograd-server.json: {}", e.toString());
            cfg = new Config();
        }
        if (!cfg.muggers) System.setProperty("novograd.nomuggers", "true");
    }

    // ------------------------------------------------------------------ частота пакетов
    private static final Map<UUID, long[]> RATE = new HashMap<>();

    /** Можно ли обработать ещё один пакет от игрока (окно 1 с). Лишние молча отбрасываются. */
    public static boolean allow(ServerPlayerEntity p) {
        long now = System.currentTimeMillis();
        long[] r = RATE.computeIfAbsent(p.getUuid(), k -> new long[]{now, 0});
        if (now - r[0] > 1000) {
            r[0] = now;
            r[1] = 0;
        }
        if (++r[1] > cfg.packetsPerSecond) {
            if (r[1] == cfg.packetsPerSecond + 1) Novograd.LOG.warn("[сервер] {}: слишком много пакетов, лишние отброшены", p.getName().getString());
            return false;
        }
        return true;
    }

    public static void init() {
        load();
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> RATE.remove(h.player.getUuid()));
        // PvP выключен в настройках сервера — игроки не ранят друг друга (полиция, жители, машины — как обычно)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amt) -> cfg.pvp || !(e instanceof ServerPlayerEntity)
                || !(src.getAttacker() instanceof ServerPlayerEntity a) || a == e);
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(CommandManager.literal("novograd").requires(s -> s.hasPermissionLevel(3))
                .then(CommandManager.literal("money")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("op", StringArgumentType.word())
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 100_000_000))
                                                .executes(c -> money(c.getSource(), EntityArgumentType.getPlayer(c, "player"), StringArgumentType.getString(c, "op"),
                                                        IntegerArgumentType.getInteger(c, "amount"), "card"))
                                                .then(CommandManager.argument("wallet", StringArgumentType.word())
                                                        .executes(c -> money(c.getSource(), EntityArgumentType.getPlayer(c, "player"), StringArgumentType.getString(c, "op"),
                                                                IntegerArgumentType.getInteger(c, "amount"), StringArgumentType.getString(c, "wallet"))))))))
                .then(CommandManager.literal("reset").then(CommandManager.argument("player", EntityArgumentType.player()).executes(c -> {
                    ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
                    LifeData fresh = new LifeData();
                    p.setAttached(Life.DATA, fresh);
                    Life.sync(p);
                    Style.broadcast(p);
                    log(c.getSource(), "сброс " + p.getName().getString());
                    return 1;
                })))
                .then(CommandManager.literal("tp").then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("x", IntegerArgumentType.integer()).then(CommandManager.argument("z", IntegerArgumentType.integer()).executes(c -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
                            int x = IntegerArgumentType.getInteger(c, "x"), z = IntegerArgumentType.getInteger(c, "z");
                            var w = p.getServer().getOverworld();
                            int y = w.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
                            p.teleport(w, x + 0.5, y, z + 0.5, p.getYaw(), p.getPitch());
                            log(c.getSource(), "телепорт " + p.getName().getString() + " → " + x + " " + z);
                            return 1;
                        })))))
                .then(CommandManager.literal("kick").then(CommandManager.argument("player", EntityArgumentType.player()).executes(c -> {
                    ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
                    p.networkHandler.disconnect(Text.literal("Отключён администратором"));
                    log(c.getSource(), "кик " + p.getName().getString());
                    return 1;
                })))
                .then(CommandManager.literal("ban").then(CommandManager.argument("player", EntityArgumentType.player()).executes(c -> {
                    ServerPlayerEntity p = EntityArgumentType.getPlayer(c, "player");
                    var s = c.getSource().getServer();
                    s.getCommandManager().executeWithPrefix(s.getCommandSource(), "ban " + p.getName().getString() + " Новоград: бан администратора");
                    log(c.getSource(), "бан " + p.getName().getString());
                    return 1;
                })))
                .then(CommandManager.literal("stats").executes(c -> {
                    var s = c.getSource().getServer();
                    int cit = 0, cars = 0;
                    for (var w : s.getWorlds()) {
                        for (var e : w.iterateEntities()) {
                            if (e instanceof com.yompix.novograd.entity.CitizenEntity) cit++;
                            else if (e instanceof com.yompix.novograd.entity.CarEntity) cars++;
                        }
                    }
                    String msg = String.format(java.util.Locale.ROOT, "игроков %d · MSPT %.1f · жителей %d · машин %d · индекс цен %.3f",
                            s.getCurrentPlayerCount(), s.getAverageNanosPerTick() / 1e6, cit, cars, com.yompix.novograd.gta.Money.cpi());
                    c.getSource().sendFeedback(() -> Text.literal(msg), false);
                    return 1;
                }))
                .then(CommandManager.literal("reload").executes(c -> {
                    load();
                    c.getSource().sendFeedback(() -> Text.literal("novograd-server.json перечитан"), true);
                    return 1;
                }))));
    }

    private static int money(ServerCommandSource src, ServerPlayerEntity p, String op, int amount, String wallet) {
        LifeData d = Life.get(p);
        int cur = switch (wallet) {
            case "cash" -> d.cash;
            case "dirty" -> d.dirty;
            default -> d.money;
        };
        int v = switch (op) {
            case "give" -> cur + amount;
            case "take" -> Math.max(0, cur - amount);
            case "set" -> amount;
            default -> cur;
        };
        switch (wallet) {
            case "cash" -> d.cash = v;
            case "dirty" -> d.dirty = v;
            default -> d.money = v;
        }
        Life.sync(p);
        log(src, "деньги " + p.getName().getString() + " " + op + " " + amount + " " + wallet + " → " + v);
        return 1;
    }

    private static void log(ServerCommandSource src, String what) {
        Novograd.LOG.info("[админ] {}: {}", src.getName(), what);
        src.sendFeedback(() -> Text.literal(what), true);
    }
}
