package com.yompix.novograd.vehicle;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CarEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

/** Транспорт в инвентаре: ПКМ по земле (катер — по воде) — поставить. Присядь и ударь свой 3 раза — заберёшь обратно. */
public class VehicleItem extends Item {
    public enum Kind { BIKE, BOAT, HELI, PLANE }

    public final Kind kind;

    public VehicleItem(Settings settings, Kind kind) {
        super(settings);
        this.kind = kind;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        BlockHitResult hit = raycast(world, user, kind == Kind.BOAT ? RaycastContext.FluidHandling.SOURCE_ONLY : RaycastContext.FluidHandling.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) return TypedActionResult.pass(stack);
        if (!(world instanceof ServerWorld sw)) return TypedActionResult.success(stack);
        BlockPos bp = hit.getBlockPos();
        CarEntity v = switch (kind) {
            case BIKE -> new CarEntity(Vehicles.BIKE, sw);
            case BOAT -> new SpeedboatEntity(Vehicles.SPEEDBOAT, sw);
            case HELI -> new HeliEntity(Vehicles.HELI, sw);
            case PLANE -> new PlaneEntity(Vehicles.PLANE, sw);
        };
        double y;
        if (kind == Kind.BOAT) {
            if (!sw.getFluidState(bp).isIn(FluidTags.WATER)) {
                user.sendMessage(Text.literal("Катер ставится только на воду"), true);
                return TypedActionResult.fail(stack);
            }
            y = bp.getY() + sw.getFluidState(bp).getHeight(sw, bp) - SpeedboatEntity.DRAFT;
        } else {
            y = hit.getSide() == net.minecraft.util.math.Direction.UP ? hit.getPos().y : bp.getY() + 1;
        }
        v.refreshPositionAndAngles(hit.getPos().x, y, hit.getPos().z, user.getYaw(), 0);
        v.setOwner(user.getUuid());
        if (!sw.isSpaceEmpty(v, v.getBoundingBox().contract(0.05))) {
            user.sendMessage(Text.literal("Здесь не помещается"), true);
            return TypedActionResult.fail(stack);
        }
        sw.spawnEntity(v);
        sw.playSound(null, v.getBlockPos(), ModContent.CAR_START, SoundCategory.NEUTRAL, 0.8f, 1f);
        if (!user.isCreative()) stack.decrement(1);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        switch (kind) {
            case BIKE -> {
                tooltip.add(Text.literal("ПКМ по дороге — поставить мотоцикл").formatted(Formatting.GRAY));
                tooltip.add(Text.literal("W/S — газ/тормоз, A/D — руль, H — гудок").formatted(Formatting.GRAY));
            }
            case BOAT -> {
                tooltip.add(Text.literal("ПКМ по воде — спустить катер").formatted(Formatting.GRAY));
                tooltip.add(Text.literal("W/S — газ/реверс, A/D — руль").formatted(Formatting.GRAY));
            }
            case HELI -> {
                tooltip.add(Text.literal("ПКМ по площадке — поставить вертолёт").formatted(Formatting.GRAY));
                tooltip.add(Text.literal("Пробел — вверх, Shift — вниз").formatted(Formatting.GRAY));
                tooltip.add(Text.literal("W/S — вперёд/назад, A/D — разворот").formatted(Formatting.GRAY));
                tooltip.add(Text.literal("Выйти — Shift после посадки").formatted(Formatting.GRAY));
            }
            case PLANE -> {
                tooltip.add(Text.literal("ПКМ по полосе — поставить самолёт").formatted(Formatting.GRAY));
                tooltip.add(Text.literal("W/S — газ, A/D — поворот").formatted(Formatting.GRAY));
                tooltip.add(Text.literal("Пробел — нос вверх, Shift — нос вниз").formatted(Formatting.GRAY));
                tooltip.add(Text.literal("Взлёт: разгонись по полосе и жми Пробел").formatted(Formatting.GRAY));
            }
        }
        tooltip.add(Text.literal("Присядь и ударь свой транспорт — забрать").formatted(Formatting.GRAY));
    }
}
