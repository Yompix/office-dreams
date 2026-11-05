package com.yompix.novograd.item;

import com.yompix.novograd.gta.Weapons;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/** Пистолет (ПКМ — выстрел) и автомат (держи ПКМ — очередь). Патроны — «Патроны» из оружейного магазина. */
public class GunItem extends Item {
    private final float damage;
    private final int cooldown;
    private final boolean auto;

    public GunItem(Settings settings, float damage, int cooldown, boolean auto) {
        super(settings);
        this.damage = damage;
        this.cooldown = cooldown;
        this.auto = auto;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (auto) {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        }
        if (!world.isClient) fire((ServerWorld) world, user);
        else user.setPitch(user.getPitch() - 2.2f); // отдача
        user.getItemCooldownManager().set(this, cooldown);
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remaining) {
        if (!auto || (getMaxUseTime(stack, user) - remaining) % cooldown != 0 || !(user instanceof PlayerEntity p)) return;
        if (!world.isClient) fire((ServerWorld) world, p);
        else p.setPitch(p.getPitch() - 0.7f);
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return auto ? 72000 : 0;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return auto ? UseAction.BOW : UseAction.NONE;
    }

    private void fire(ServerWorld w, PlayerEntity p) {
        if (!Weapons.useAmmo(p)) {
            p.sendMessage(Text.literal("Нет патронов! Купи в оружейном «Калибр»").formatted(Formatting.RED), true);
            w.playSound(null, p.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_DISPENSER_FAIL, net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, 1.8f);
            return;
        }
        Weapons.shoot(w, p, p.getEyePos(), p.getRotationVec(1f), damage, auto ? 0.045 : 0.012, 48, auto);
    }
}
