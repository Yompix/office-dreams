package com.yompix.novograd.gta;

import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.world.IslandChunkGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ShovelItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Город — не стройка (как в GTA): в выживании и приключении ломать и ставить блоки нельзя,
 * двери, сундуки, кнопки, стулья и блоки Новограда работают. В творческом режиме — можно всё.
 * Клиент получает флаг «nogrief» и даже не начинает копать (без «моргающих» блоков).
 */
public final class NoGrief {
    private NoGrief() {}

    private static final Map<UUID, Long> WARNED = new HashMap<>();

    public static boolean active(PlayerEntity p, World w) {
        if (p.isCreative() || p.isSpectator()) return false;
        return w instanceof ServerWorld sw && sw.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator;
    }

    private static void warn(PlayerEntity p) {
        if (!(p instanceof ServerPlayerEntity sp)) return;
        long now = System.currentTimeMillis();
        if (WARNED.getOrDefault(p.getUuid(), 0L) > now) return;
        WARNED.put(p.getUuid(), now + 2000);
        sp.sendMessage(Text.literal("Это город, а не стройка: ломать и ставить блоки нельзя.").formatted(Formatting.GRAY), true);
    }

    /** Блоки, которые игрок «использует», а не строит на них: с ними в руке блок не мешает. */
    private static boolean usable(BlockState s) {
        var b = s.getBlock();
        return b instanceof DoorBlock || b instanceof TrapdoorBlock || b instanceof FenceGateBlock || b instanceof ButtonBlock || b instanceof LeverBlock
                || b instanceof BedBlock || b instanceof AbstractChestBlock || b instanceof BlockWithEntity || b instanceof CraftingTableBlock
                || b instanceof StairsBlock || b instanceof SlabBlock || b instanceof com.yompix.novograd.block.LifeBlock
                || b.getClass().getName().startsWith("com.yompix.novograd");
    }

    private static boolean builds(Item item) {
        return item instanceof BlockItem || item instanceof BucketItem || item instanceof FlintAndSteelItem || item instanceof FireChargeItem
                || item instanceof AxeItem || item instanceof ShovelItem || item instanceof HoeItem;
    }

    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!active(player, world)) return true;
            warn(player);
            return false;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (world.isClient || !active(player, world)) return ActionResult.PASS;
            warn(player);
            return ActionResult.FAIL;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !active(player, world)) return ActionResult.PASS;
            if (!builds(player.getStackInHand(hand).getItem())) return ActionResult.PASS;
            if (!player.isSneaking() && usable(world.getBlockState(hit.getBlockPos()))) return ActionResult.PASS; // открыть дверь/сундук с блоком в руке
            warn(player);
            return ActionResult.FAIL;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getStackInHand(hand);
            if (world.isClient || !(stack.getItem() instanceof BucketItem) || !active(player, world)) return TypedActionResult.pass(stack);
            warn(player);
            return TypedActionResult.fail(stack);
        });
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> sync(h.player)));
    }

    /** Клиенту: включена ли защита (чтобы не начинать копать). */
    public static void sync(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        n.putBoolean("on", p.getServerWorld().getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator);
        LifeNet.send(p, "nogrief", n);
    }
}
