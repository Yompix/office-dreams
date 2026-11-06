package com.yompix.novograd.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Аптечка: ПКМ — сразу +5 сердец и немного регенерации. */
public class MedkitItem extends Item {
    public MedkitItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getHealth() >= user.getMaxHealth()) {
            if (world.isClient) user.sendMessage(Text.literal("Ты и так здоров").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(stack);
        }
        if (!world.isClient) {
            user.heal(10f);
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
            world.playSound(null, user.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), SoundCategory.PLAYERS, 1f, 1.4f);
            world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.3f, 2f);
            user.sendMessage(Text.literal("✚ Аптечка: +5 ❤").formatted(Formatting.GREEN), true);
        }
        user.getItemCooldownManager().set(this, 30);
        stack.decrementUnlessCreative(1, user);
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("ПКМ — лечит 5 сердец").formatted(Formatting.GREEN));
    }
}
