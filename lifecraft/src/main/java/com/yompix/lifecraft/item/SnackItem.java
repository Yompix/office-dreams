package com.yompix.lifecraft.item;

import com.yompix.lifecraft.life.CityLife;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/** Еда из магазина: кроме сытости даёт энергию и чуть поднимает настроение. */
public class SnackItem extends Item {
    private final boolean drink;

    public SnackItem(Settings settings, boolean drink) {
        super(settings);
        this.drink = drink;
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (user instanceof ServerPlayerEntity p) CityLife.ate(p, drink);
        return super.finishUsing(stack, world, user);
    }
}
