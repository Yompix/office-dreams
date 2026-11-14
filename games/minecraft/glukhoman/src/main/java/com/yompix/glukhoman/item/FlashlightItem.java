package com.yompix.glukhoman.item;

import com.yompix.glukhoman.horror.Light;
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

/** Фонарик (луч, батарейки) или керосиновая лампа (свет вокруг, масло). Заряд = прочность. ПКМ или R — вкл/выкл. */
public class FlashlightItem extends Item {
    public final boolean beam;

    public FlashlightItem(Settings settings, boolean beam) {
        super(settings);
        this.beam = beam;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user instanceof ServerPlayerEntity sp) Light.toggle(sp);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int left = stack.getMaxDamage() - stack.getDamage();
        tooltip.add(Text.literal((beam ? "Батарейка: " : "Масло: ") + (left * 100 / stack.getMaxDamage()) + "%").formatted(Formatting.GRAY));
        tooltip.add(Text.literal(beam ? "R или ПКМ — включить. Луч оглушает Хора." : "R или ПКМ — зажечь. Светит вокруг.").formatted(Formatting.DARK_GRAY));
    }
}
