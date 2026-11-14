package com.yompix.glukhoman.item;

import com.yompix.glukhoman.story.Tapes;
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

/** Кассета E-180: 6 сюжетов. ПКМ — список сюжетов: пересмотреть или выбрать, что перезаписать. */
public class CassetteItem extends Item {
    public CassetteItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity sp) Tapes.open(sp);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("E-180 · 6 сюжетов. ПКМ — посмотреть, что на плёнке.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Новый сюжет на полную кассету стирает старый.").formatted(Formatting.DARK_GRAY));
    }
}
