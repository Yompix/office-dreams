package com.yompix.novograd.item;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.gta.Arsenal;
import com.yompix.novograd.gta.Weapons;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Огнестрел: пистолет, ПП, дробовик, «АК», снайперка. Управление как в шутерах: ЛКМ — огонь (у автоматического — держи),
 * ПКМ — прицел (ADS). Клиент шлёт «спуск» пакетом, сервер сам проверяет темп, патроны и попадания.
 * В оружии свой магазин; пустой — перезарядка сама, или R пешком. Перезарядка с пустого магазина дольше (надо передёрнуть затвор),
 * дробовик заряжается по патрону. Работает и из машины (водителем или пассажиром).
 * Модель — GeckoLib (кости: затвор, магазин, спуск, помпа…), анимации ведёт клиент (client/gun/GunAnim).
 */
public class GunItem extends Item implements GeoItem {
    /**
     * @param pellets дробинок за выстрел; spread — разброс от бедра; range — дальность; magazine — патронов в магазине;
     *                reload — тиков перезарядки с пустого магазина; recoil — подброс камеры (градусы); scope — оптика
     */
    public record Spec(float damage, int cooldown, boolean auto, int pellets, double spread, double range, int magazine, int reload,
                       float recoil, SoundEvent sound, float volume, boolean scope) {}

    private final Spec spec;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /** Клиент ставит сюда фабрику GeoRenderProvider (на сервере не нужна). */
    public static java.util.function.Function<GunItem, Object> renderProvider = g -> null;

    /** Когда игрок стрелял последний раз (тик мира) — сервер держит темп стрельбы. */
    private static final Map<UUID, Long> LAST_SHOT = new ConcurrentHashMap<>();

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

    /** pistol / smg / shotgun / rifle / sniper / revolver / eagle / micro / saiga / carbine / lmg / rpg */
    public String gunId() {
        return Registries.ITEM.getId(this).getPath();
    }

    /**
     * «Основа» для анимаций и эффектов: новые стволы двигаются как похожие старые
     * (Орёл — как пистолет, Микро — как ПП, Сайга/Карабин/Пулемёт — как «АК»); у револьвера и гранатомёта — свои.
     */
    public String animBase() {
        return base(gunId());
    }

    public static String base(String id) {
        return switch (id) {
            case "eagle" -> "pistol";
            case "micro" -> "smg";
            case "saiga", "carbine", "lmg" -> "rifle";
            default -> id;
        };
    }

    /** Дробовик заряжается по патрону (остальные — магазином). */
    public boolean shellByShell() {
        return gunId().equals("shotgun");
    }

    /** Номер вида для пакетов эффектов: 0 пистолет, 1 ПП, 2 дробовик, 3 «АК», 4 снайперка. */
    public int kindIndex() {
        return switch (gunId()) {
            case "smg", "micro" -> 1;
            case "shotgun", "saiga", "rpg" -> 2;
            case "rifle", "carbine", "lmg" -> 3;
            case "sniper" -> 4;
            default -> 0;
        };
    }

    /** Ёмкость магазина с обвесом (удлинённый магазин +50%). */
    public int magSize(ItemStack stack) {
        return com.yompix.novograd.gta.GunMods.magSize(this, stack);
    }

    public int mag(ItemStack stack) {
        int size = magSize(stack);
        return Math.min(size, stack.getOrDefault(Arsenal.MAG, size));
    }

    /** Чем заряжается: выстрелы ПГ у гранатомёта, иначе обычные патроны. */
    public Item ammoItem() {
        return com.yompix.novograd.gta.Armory.ammoFor(this);
    }

    /** ПКМ — прицел, а не выстрел: сам предмет ничего не делает (стреляет пакет «fire» по ЛКМ). */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    @Override
    public int getMaxUseTime(ItemStack stack, net.minecraft.entity.LivingEntity user) {
        return 0;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    /** Спуск от клиента (ЛКМ). Сервер держит темп: раньше cooldown−1 тиков после прошлого выстрела — не стреляем. */
    public void trigger(ServerPlayerEntity p, ItemStack stack, boolean aiming) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        Long last = LAST_SHOT.get(p.getUuid());
        if (last != null && now - last < Math.max(1, spec.cooldown - 1) && now >= last) return;
        if (p.getItemCooldownManager().isCoolingDown(this)) return; // перезарядка
        LAST_SHOT.put(p.getUuid(), now);
        fire(w, p, stack, aiming);
    }

    public static void forget(UUID id) {
        LAST_SHOT.remove(id);
    }

    private void fire(ServerWorld w, PlayerEntity p, ItemStack stack, boolean aiming) {
        int mag = mag(stack);
        if (mag <= 0) {
            if (!reload(w, p, stack, false)) {
                p.sendMessage(Text.literal(ammoItem() == ModContent.AMMO ? "Нет патронов! Купи в оружейном «Калибр»" : "Нет выстрелов ПГ! Купи в оружейном «Калибр»").formatted(Formatting.RED), true);
                w.playSound(null, p.getBlockPos(), Arsenal.GUN_DRY, SoundCategory.PLAYERS, 0.8f, 1f);
            }
            return;
        }
        if (!p.isCreative()) stack.set(Arsenal.MAG, mag - 1);
        double spread = spec.spread;
        if (aiming) spread *= spec.scope ? 0.03 : 0.35;      // в прицеле кучнее (в оптике — почти точно)
        else if (p.getVelocity().horizontalLengthSquared() > 0.01) spread *= 1.3; // на бегу от бедра — шире
        if (p.isSneaking()) spread *= 0.8;
        if (com.yompix.novograd.gta.GunMods.has(stack, com.yompix.novograd.gta.GunMods.GRIP)) spread *= 0.75; // рукоятка — кучнее
        discharge(w, p, stack, spread);
        if (mag - 1 <= 0 && !p.isCreative()) reload(w, p, stack, false); // последний патрон — сразу перезаряжаем
    }

    /** Сам выстрел (у гранатомёта — ракета, см. RocketLauncherItem). */
    protected void discharge(ServerWorld w, PlayerEntity p, ItemStack stack, double spread) {
        Weapons.fire(w, p, p.getEyePos(), p.getRotationVec(1f), spec.damage, spread, spec.range, spec.pellets, spec.sound, spec.volume, kindIndex());
    }

    /** Тиков на перезарядку: с пустого дольше (затвор), дробовик — по 7 тиков на патрон (+ помпа, если пуст). */
    public int reloadTicks(int magBefore, int got) {
        boolean empty = magBefore <= 0;
        if (shellByShell()) return 8 + 7 * got + (empty ? 10 : 0);
        return empty ? spec.reload : Math.round(spec.reload * 0.78f);
    }

    /** Перезарядка: берём патроны из инвентаря, оружие «занято» на время анимации. */
    public boolean reload(ServerWorld w, PlayerEntity p, ItemStack stack, boolean manual) {
        int mag = mag(stack), size = magSize(stack);
        if (mag >= size || p.getItemCooldownManager().isCoolingDown(this) && manual) return false;
        int got = Arsenal.takeAmmo(p, size - mag, ammoItem());
        if (got <= 0) {
            if (manual) p.sendMessage(Text.literal(ammoItem() == ModContent.AMMO ? "Нет патронов для перезарядки" : "Нет выстрелов ПГ — купи в «Калибре»").formatted(Formatting.RED), true);
            return false;
        }
        stack.set(Arsenal.MAG, mag + got);
        p.getItemCooldownManager().set(this, shellByShell() ? reloadTicks(mag, got) : Math.max(6, Math.round(reloadTicks(mag, got) * com.yompix.novograd.gta.Skills.reloadMul(p, kindIndex())))); // навык: быстрее
        // остальные слышат звук перезарядки целиком; у самого стрелка звуки идут по кадрам анимации (клиент)
        w.playSound(p, p.getBlockPos(), Arsenal.reloadSound(this), SoundCategory.PLAYERS, 0.9f, 1f);
        return true;
    }

    /** Патрон ушёл из магазина — не «перевыхватываем» оружие на каждом выстреле. */
    @Override
    public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public boolean isItemBarVisible(ItemStack stack) {
        return spec.magazine > 1 && mag(stack) < magSize(stack);
    }

    @Override
    public int getItemBarStep(ItemStack stack) {
        return Math.round(13f * mag(stack) / magSize(stack));
    }

    @Override
    public int getItemBarColor(ItemStack stack) {
        return 0xFFD54F;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Магазин: " + mag(stack) + " / " + magSize(stack)).formatted(Formatting.GOLD));
        String dmg = spec.damage <= 0 ? "взрыв" : spec.pellets > 1 ? spec.pellets + "×" + fmt(spec.damage) : fmt(spec.damage);
        tooltip.add(Text.literal("Урон " + dmg + " · дальность " + (int) spec.range + " м" + (spec.auto ? " · очередь" : "")).formatted(Formatting.GRAY));
        String mods = com.yompix.novograd.gta.GunMods.describe(stack);
        if (!mods.isEmpty()) tooltip.add(Text.literal("Мастерская: " + mods).formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal("ЛКМ — огонь, ПКМ — " + (spec.scope ? "оптика" : "прицел")).formatted(Formatting.AQUA));
        tooltip.add(Text.literal("R — перезарядка (пешком), Y — осмотреть").formatted(Formatting.DARK_GRAY));
    }

    private static String fmt(float f) {
        return f == (int) f ? String.valueOf((int) f) : String.valueOf(f);
    }

    // ---------- GeckoLib ----------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        if (renderProvider.apply(this) instanceof GeoRenderProvider p) consumer.accept(p);
    }
}
