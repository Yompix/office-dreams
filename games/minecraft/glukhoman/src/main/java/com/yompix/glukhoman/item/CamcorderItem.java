package com.yompix.glukhoman.item;

import com.yompix.glukhoman.story.Cam;
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

/** Камера «Панасоник». ПКМ — поднять к глазу / опустить. Заряд батареи = прочность; батарейка из инвентаря меняется сама. */
public class CamcorderItem extends Item {
    public CamcorderItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity sp) Cam.toggle(sp);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int left = stack.getMaxDamage() - stack.getDamage();
        tooltip.add(Text.literal("Батарея: " + (left * 100 / stack.getMaxDamage()) + "%").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("ПКМ — к глазу. G — ИК-режим (садит батарею втрое).").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("В камере видно то, чего нет глазами. И наоборот.").formatted(Formatting.DARK_GRAY));
    }
}
