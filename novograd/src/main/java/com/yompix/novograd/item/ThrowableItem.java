package com.yompix.novograd.item;

import com.yompix.novograd.entity.GrenadeEntity;
import com.yompix.novograd.gta.Arsenal;
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

/** Граната (взрыв через 2,5 с, дома целы) и коктейль Молотова (лужа огня). ПКМ — бросок, можно из машины. */
public class ThrowableItem extends Item {
    private final boolean molotov;

    public ThrowableItem(Settings settings, boolean molotov) {
        super(settings);
        this.molotov = molotov;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.7f, molotov ? 0.6f : 0.4f);
        if (!world.isClient) {
            GrenadeEntity g = new GrenadeEntity(molotov ? Arsenal.MOLOTOV_ENTITY : Arsenal.GRENADE_ENTITY, user, world);
            g.setItem(stack.copyWithCount(1));
            g.setVelocity(user, user.getPitch() - 8, user.getYaw(), 0f, molotov ? 1.1f : 1.25f, 1.5f);
            world.spawnEntity(g);
        }
        user.getItemCooldownManager().set(this, 20);
        stack.decrementUnlessCreative(1, user);
        user.swingHand(hand);
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal(molotov ? "Разбивается и поджигает всё вокруг" : "Взрыв через 2,5 секунды").formatted(Formatting.GRAY));
    }
}
