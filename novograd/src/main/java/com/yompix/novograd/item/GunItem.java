package com.yompix.novograd.item;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.gta.Arsenal;
import com.yompix.novograd.gta.Weapons;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/**
 * Огнестрел: пистолет, автомат, дробовик, «АК», снайперка. ПКМ — выстрел (у автоматического — держи ПКМ).
 * В оружии свой магазин; пустой — перезарядка сама, или R пешком. Патроны — «Патроны» из «Калибра».
 * Работает и из машины (водителем или пассажиром).
 */
public class GunItem extends Item {
    /**
     * @param pellets дробинок за выстрел; spread — разброс; range — дальность; magazine — патронов в магазине;
     *                reload — тиков перезарядки; recoil — отдача (градусы); scope — прицел с приближением (присесть)
     */
    public record Spec(float damage, int cooldown, boolean auto, int pellets, double spread, double range, int magazine, int reload,
                       float recoil, SoundEvent sound, float volume, boolean scope) {}

    private final Spec spec;

    public GunItem(Settings settings, Spec spec) {
        super(settings);
        this.spec = spec;
    }

    /** Старый вид: пистолет и автомат. */
    public GunItem(Settings settings, float damage, int cooldown, boolean auto) {
        this(settings, new Spec(damage, cooldown, auto, 1, auto ? 0.045 : 0.012, 48, auto ? 30 : 12, auto ? 40 : 30, auto ? 0.7f : 2.2f,
                auto ? ModContent.SMG_SHOT : ModContent.GUNSHOT, auto ? 0.9f : 1.2f, false));
    }

    public Spec spec() {
        return spec;
    }

    public int mag(ItemStack stack) {
        return Math.min(spec.magazine, stack.getOrDefault(Arsenal.MAG, spec.magazine));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (spec.auto) {
            user.setCurrentHand(hand);
            return TypedActionResult.consume(stack);
        }
        boolean canFire = mag(stack) > 0;
        user.getItemCooldownManager().set(this, spec.cooldown);
        if (!world.isClient) fire((ServerWorld) world, user, stack);
        else if (canFire) user.setPitch(user.getPitch() - spec.recoil * (spec.scope && user.isSneaking() ? 0.5f : 1f)); // отдача
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remaining) {
        if (!spec.auto || (getMaxUseTime(stack, user) - remaining) % spec.cooldown != 0 || !(user instanceof PlayerEntity p)) return;
        if (p.getItemCooldownManager().isCoolingDown(this)) return; // перезарядка
        if (!world.isClient) fire((ServerWorld) world, p, stack);
        else if (mag(stack) > 0) p.setPitch(p.getPitch() - spec.recoil);
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return spec.auto ? 72000 : 0;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return spec.auto ? UseAction.BOW : UseAction.NONE;
    }

    private void fire(ServerWorld w, PlayerEntity p, ItemStack stack) {
        int mag = mag(stack);
        if (mag <= 0) {
            if (!reload(w, p, stack, false)) {
                p.sendMessage(Text.literal("Нет патронов! Купи в оружейном «Калибр»").formatted(Formatting.RED), true);
                w.playSound(null, p.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.6f, 1.8f);
                if (p.isUsingItem()) p.stopUsingItem();
            }
            return;
        }
        if (!p.isCreative()) stack.set(Arsenal.MAG, mag - 1);
        double spread = spec.scope && p.isSneaking() ? 0.002 : spec.spread;
        Weapons.fire(w, p, p.getEyePos(), p.getRotationVec(1f), spec.damage, spread, spec.range, spec.pellets, spec.sound, spec.volume);
        if (mag - 1 <= 0 && !p.isCreative()) reload(w, p, stack, false); // последний патрон — сразу перезаряжаем
        else if (spec.magazine > 1) p.sendMessage(Text.literal("▮ " + (p.isCreative() ? mag : mag - 1) + " / " + spec.magazine + "   запас " + reserve(p))
                .formatted(Formatting.GOLD), true);
    }

    private static int reserve(PlayerEntity p) {
        return p.isCreative() ? 999 : p.getInventory().count(ModContent.AMMO);
    }

    /** Перезарядка: берём патроны из инвентаря, оружие «занято» reload тиков. */
    public boolean reload(ServerWorld w, PlayerEntity p, ItemStack stack, boolean manual) {
        int mag = mag(stack);
        if (mag >= spec.magazine || p.getItemCooldownManager().isCoolingDown(this) && manual) return false;
        int got = Arsenal.takeAmmo(p, spec.magazine - mag);
        if (got <= 0) {
            if (manual) p.sendMessage(Text.literal("Нет патронов для перезарядки").formatted(Formatting.RED), true);
            return false;
        }
        stack.set(Arsenal.MAG, mag + got);
        p.getItemCooldownManager().set(this, spec.reload);
        if (p.isUsingItem()) p.stopUsingItem();
        w.playSound(null, p.getBlockPos(), Arsenal.RELOAD, SoundCategory.PLAYERS, 0.9f, 1f);
        p.sendMessage(Text.literal("⟳ Перезарядка… " + (mag + got) + " / " + spec.magazine).formatted(Formatting.YELLOW), true);
        return true;
    }

    /** Патрон ушёл из магазина — не «перевыхватываем» оружие на каждом выстреле. */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return spec.magazine > 1 && mag(stack) < spec.magazine;
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13f * mag(stack) / spec.magazine);
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xFFD54F;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Магазин: " + mag(stack) + " / " + spec.magazine).formatted(Formatting.GOLD));
        String dmg = spec.pellets > 1 ? spec.pellets + "×" + fmt(spec.damage) : fmt(spec.damage);
        tooltip.add(Text.literal("Урон " + dmg + " · дальность " + (int) spec.range + " м" + (spec.auto ? " · очередь" : "")).formatted(Formatting.GRAY));
        if (spec.scope) tooltip.add(Text.literal("Присядь — прицел с приближением").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("R — перезарядка (пешком)").formatted(Formatting.DARK_GRAY));
    }

    private static String fmt(float f) {
        return f == (int) f ? String.valueOf((int) f) : String.valueOf(f);
    }
}
