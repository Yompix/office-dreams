package com.yompix.lifecraft.item;

import com.yompix.lifecraft.LifeCraft;
import com.yompix.lifecraft.ModContent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

public class PhoneItem extends Item {
    public PhoneItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) LifeCraft.CLIENT.openPhone();
        else world.playSound(null, user.getBlockPos(), ModContent.PHONE_OPEN, SoundCategory.PLAYERS, 0.5f, 1f);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("ПКМ или клавиша G — открыть").formatted(Formatting.GRAY));
    }
}
