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
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(literal("novograd")
                .then(literal("info").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    LifeData d = Life.get(p);
                    ctx.getSource().sendFeedback(() -> Text.literal("💰 $" + d.money + "  🎓 " + d.eduName() + " (" + d.lessons + "/" + LifeData.lessonsNeeded(d.edu)
                            + ")  💼 " + com.yompix.novograd.life.Jobs.job(p).title + "  заработано всего $" + d.earned), false);
                    return 1;
                }))
                .then(literal("town").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    BlockPos at = p.getBlockPos();
                    ctx.getSource().sendFeedback(() -> Text.literal("🏗 Строю город вокруг тебя (160×160)…"), true);
                    TownBuilder.build(p.getServerWorld(), at, true);
                    p.requestTeleport(at.getX() + 0.5, at.getY(), at.getZ() + 9.5);
                    ctx.getSource().sendFeedback(() -> Text.literal("✅ Город готов! Ты у школы."), true);
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
    }
}
