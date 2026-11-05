package com.yompix.lifecraft.item;

import com.yompix.lifecraft.ModContent;
import com.yompix.lifecraft.entity.CarEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/** Машина в инвентаре: ПКМ по земле — поставить. Ударь свою машину, присев, — заберёшь обратно. */
public class CarItem extends Item {
    public final int variant;

    public final boolean scooter;

    public CarItem(Settings settings, int variant) {
        this(settings, variant, false);
    }

    public CarItem(Settings settings, int variant, boolean scooter) {
        super(settings);
        this.variant = variant;
        this.scooter = scooter;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        Vec3d at = ctx.getHitPos();
        if (world instanceof ServerWorld sw) {
            CarEntity car = new CarEntity(scooter ? ModContent.SCOOTER : ModContent.CAR, sw);
            car.refreshPositionAndAngles(at.x, ctx.getBlockPos().getY() + 1, at.z, ctx.getPlayer() != null ? ctx.getPlayer().getYaw() : 0, 0);
            car.setVariant(variant);
            if (ctx.getPlayer() != null) car.setOwner(ctx.getPlayer().getUuid());
            if (!sw.isSpaceEmpty(car, car.getBoundingBox())) return ActionResult.FAIL;
            sw.spawnEntity(car);
            sw.playSound(null, car.getBlockPos(), ModContent.CAR_START, SoundCategory.NEUTRAL, 0.8f, 1f);
            if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) ctx.getStack().decrement(1);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("ПКМ по дороге — поставить машину").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("W/S — газ/тормоз, A/D — руль, H — гудок").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Присядь и ударь свою машину — забрать").formatted(Formatting.GRAY));
    }
}
