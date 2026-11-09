package com.yompix.novograd.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * Парашют: лежит в инвентаре. Прыгнул с крыши, вертолёта или самолёта — в свободном падении жми Пробел (или ПКМ парашютом):
 * купол раскроется, дальше W/S — скорость, A/D — поворот. Одноразовый. Продаётся в «Калибре», лежит на крышах небоскрёбов
 * и в кабине вертолёта/самолёта; прыжки на точность — в парашютном клубе на аэродроме (см. gta/Skydive).
 */
public class ParachuteItem extends Item {
    public ParachuteItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp && com.yompix.novograd.gta.Skydive.open(sp)) return TypedActionResult.success(stack);
        if (world.isClient && user.isOnGround()) user.sendMessage(Text.literal("Парашют раскрывают в падении: прыгни с высоты и жми Пробел").formatted(Formatting.GRAY), true);
        return TypedActionResult.pass(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("В падении — Пробел: раскрыть купол").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("W/S — скорость, A/D — поворот").formatted(Formatting.GRAY));
    }
}
