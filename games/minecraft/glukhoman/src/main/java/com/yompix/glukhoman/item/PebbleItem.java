package com.yompix.glukhoman.item;

import com.yompix.glukhoman.entity.PebbleEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Камешек: бросить (ПКМ) — стукнет там, куда упадёт, и Слухач пойдёт на звук. */
public class PebbleItem extends Item {
    public PebbleItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            PebbleEntity p = new PebbleEntity(world, user);
            p.setItem(stack.copyWithCount(1));
            p.setVelocity(user, user.getPitch(), user.getYaw(), 0f, 1.2f, 1f);
            world.spawnEntity(p);
        }
        user.getItemCooldownManager().set(this, 15);
        if (!user.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.success(stack, world.isClient);
    }
}
