package com.yompix.novograd.world;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /novograd town — построить город здесь; money / edu — для админа и тестов; info — всё о себе. */
public final class LifeCommands {
    private LifeCommands() {}

    public static void init() {
        // коды как в старых криминальных играх: /cheat BOGACH, ZDOROV, ARSENAL, TISHINA, HAOS, TACHKA, NOCH, DEN, LIVEN, SOLNCE
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(literal("cheat")
                .requires(s -> s.getServer().isSingleplayer() || s.hasPermissionLevel(2))
                .then(argument("code", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    String r = com.yompix.novograd.gta.Cheats.apply(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "code"));
                    ctx.getSource().sendFeedback(() -> Text.literal(r == null ? "Код не сработал" : "✔ Код принят: " + r), false);
                    return r == null ? 0 : 1;
                }))));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(literal("novograd")
                .then(literal("info").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    LifeData d = Life.get(p);
                    ctx.getSource().sendFeedback(() -> Text.literal("$" + d.money + (d.dirty > 0 ? "  грязные $" + d.dirty : "") + "  " + com.yompix.novograd.life.Jobs.job(p).title + "  заработано всего $" + d.earned), false);
                    return 1;
                }))
                .then(literal("town").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    BlockPos at = p.getBlockPos();
                    ctx.getSource().sendFeedback(() -> Text.literal("Строю город вокруг тебя (160×160)…"), true);
                    TownBuilder.build(p.getServerWorld(), at, true);
                    p.requestTeleport(at.getX() + 0.5, at.getY(), at.getZ() + 9.5);
                    ctx.getSource().sendFeedback(() -> Text.literal("✅ Город готов!"), true);
                    return 1;
                }))
                .then(literal("money").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("player", EntityArgumentType.player()).then(argument("amount", IntegerArgumentType.integer(0)).executes(ctx -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                            Life.get(p).money = IntegerArgumentType.getInteger(ctx, "amount");
                            Life.sync(p);
                            ctx.getSource().sendFeedback(() -> Text.literal("Деньги " + p.getName().getString() + ": $" + Life.get(p).money), true);
                            return 1;
                        }))))
                .then(literal("edu").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("player", EntityArgumentType.player()).then(argument("level", IntegerArgumentType.integer(1, 16)).executes(ctx -> {
                            ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                            LifeData d = Life.get(p);
                            d.edu = IntegerArgumentType.getInteger(ctx, "level");
                            d.lessons = 0;
                            Life.sync(p);
                            ctx.getSource().sendFeedback(() -> Text.literal("Образование " + p.getName().getString() + ": " + d.eduName()), true);
                            return 1;
                        }))))));
        // /novograd shops — для теста: поставить перед собой «Шмот-Базар», бутик «Бархат», цирюльню и зал (стиль SA)
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(literal("novograd")
                .then(literal("shops").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    net.minecraft.util.math.Direction f = p.getHorizontalFacing(), side = f.rotateYClockwise();
                    BlockPos base = p.getBlockPos().offset(f, 22);
                    java.util.List<ShopBuilder.Spot> spots = new java.util.ArrayList<>();
                    ShopBuilder.Kind[] kinds = ShopBuilder.Kind.values();
                    for (int i = 0; i < kinds.length; i++)
                        spots.add(new ShopBuilder.Spot(kinds[i], base.offset(side, (i - 1) * 24 - 12), f.getOpposite()));
                    ShopBuilder.placeAll(p.getServerWorld(), spots);
                    ctx.getSource().sendFeedback(() -> Text.literal("Магазины стиля построены перед тобой"), true);
                    return 1;
                }))));
    }
}
