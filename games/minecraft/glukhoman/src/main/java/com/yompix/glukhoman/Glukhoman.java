package com.yompix.glukhoman;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.yompix.glukhoman.block.PropBlock;
import com.yompix.glukhoman.horror.Director;
import com.yompix.glukhoman.horror.Light;
import com.yompix.glukhoman.horror.Noise;
import com.yompix.glukhoman.horror.Sanity;
import com.yompix.glukhoman.net.Net;
import com.yompix.glukhoman.story.Story;
import com.yompix.glukhoman.story.StoryState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class Glukhoman implements ModInitializer {
    public static final String MOD_ID = "glukhoman";
    public static final Logger LOG = LoggerFactory.getLogger("Глухомань");

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        GContent.init();
        Net.init(Glukhoman::onClientMessage);

        ServerLifecycleEvents.SERVER_STARTED.register(Story::serverStarted);
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            Story.tick(s);
            if (!Story.isOurWorld()) return;
            Light.tick(s);
            Sanity.tick(s);
            Director.tick(s);
        });
        ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> {
            if (Boolean.getBoolean("glukhoman.devop")) s.getPlayerManager().addToOperators(h.player.getGameProfile()); // только для автотеста
            if (Story.isOurWorld() && h.player.interactionManager.getGameMode() == GameMode.SURVIVAL) h.player.changeGameMode(GameMode.ADVENTURE);
            Story.join(h.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> Light.off(h.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, p, alive) -> {
            if (!Story.isOurWorld()) return;
            BlockPos cp = Story.checkpoint();
            if (cp != null) p.teleport(p.getServerWorld(), cp.getX() + 0.5, cp.getY(), cp.getZ() + 0.5, p.getYaw(), 0);
            Sanity.set(p, 60);
            p.sendMessage(Text.literal("Ты очнулся. Голова раскалывается."), true);
            Story.sendHud(p);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            BlockState s = world.getBlockState(pos);
            if (world.isClient) return s.getBlock() instanceof PropBlock ? ActionResult.SUCCESS : ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp) || !Story.isOurWorld()) return ActionResult.PASS;
            if (s.getBlock() instanceof DoorBlock || s.getBlock() instanceof TrapdoorBlock || s.getBlock() instanceof FenceGateBlock)
                Noise.emit((ServerWorld) world, Vec3d.ofCenter(pos), 14, sp);
            String key = StoryState.get((ServerWorld) world).interact.get(pos.asLong());
            if (key == null) return ActionResult.PASS;
            if (hand != net.minecraft.util.Hand.MAIN_HAND) return ActionResult.SUCCESS;
            return Story.interact(sp, pos, key) ? ActionResult.SUCCESS : ActionResult.PASS;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) Noise.emit(sw, Vec3d.ofCenter(pos), 14, sp);
        });

        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(literal("glukhoman")
                .then(literal("status").executes(c -> {
                    c.getSource().sendFeedback(() -> Text.literal(Story.status()), false);
                    return 1;
                }))
                .then(literal("chapter").requires(src -> src.hasPermissionLevel(2)).then(argument("n", IntegerArgumentType.integer(1, 5)).executes(c -> {
                    Story.jump(IntegerArgumentType.getInteger(c, "n"));
                    return 1;
                })))
                .then(literal("difficulty").requires(src -> src.hasPermissionLevel(2)).then(argument("d", IntegerArgumentType.integer(0, 2)).executes(c -> {
                    if (c.getSource().getPlayer() != null) Story.chooseDifficulty(c.getSource().getPlayer(), IntegerArgumentType.getInteger(c, "d"), true);
                    return 1;
                })))
                .then(literal("reset").requires(src -> src.hasPermissionLevel(2)).executes(c -> {
                    Story.reset();
                    c.getSource().sendFeedback(() -> Text.literal("Сюжет сброшен. Перезайди в мир — появится выбор сложности."), false);
                    return 1;
                }))
                .then(literal("scare").requires(src -> src.hasPermissionLevel(2)).executes(c -> {
                    if (c.getSource().getPlayer() != null) Director.apparition(c.getSource().getPlayer(), false);
                    return 1;
                }))));

        if (Boolean.getBoolean("glukhoman.servertest")) com.yompix.glukhoman.story.ServerTest.init();
        LOG.info("Глухомань загружена: тайга, посёлок, станция, шахта, три существа");
    }

    static void onClientMessage(ServerPlayerEntity p, NbtCompound m) {
        switch (m.getString("t")) {
            case "light" -> Light.toggle(p);
            case "choice" -> Story.choose(p, m.getString("c"));
            case "difficulty" -> Story.chooseDifficulty(p, m.getInt("d"));
            case "prefs" -> {
                if (m.getBoolean("noScares")) Director.NO_SCARES.add(p.getUuid());
                else Director.NO_SCARES.remove(p.getUuid());
            }
            case "hint" -> {
                BlockPos t = Story.hintTarget(p);
                if (t == null) return;
                NbtCompound r = Net.msg("hint");
                r.putInt("x", t.getX());
                r.putInt("y", t.getY());
                r.putInt("z", t.getZ());
                Net.send(p, r);
            }
            default -> {
            }
        }
    }
}
