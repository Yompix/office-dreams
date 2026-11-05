package com.yompix.novograd.item;

import com.yompix.novograd.life.CityLife;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/** Еда из магазина: кроме сытости даёт энергию и чуть поднимает настроение. */
public class SnackItem extends Item {
    private final boolean drink;
    /** Сколько килограммов «прибавит» еда (салат — минус). */
    private final float kg;

    public SnackItem(Settings settings, boolean drink) {
        this(settings, drink, drink ? 0.25f : 0.6f);
    }

    public SnackItem(Settings settings, boolean drink, float kg) {
        super(settings);
        this.drink = drink;
        this.kg = kg;
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (user instanceof ServerPlayerEntity p) {
            CityLife.ate(p, drink);
            com.yompix.novograd.life.Fitness.ate(p, kg);
        }
        return super.finishUsing(stack, world, user);
    }
}
