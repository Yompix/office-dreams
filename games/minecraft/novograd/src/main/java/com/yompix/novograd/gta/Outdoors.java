package com.yompix.novograd.gta;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.WildAnimalEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.world.Holidays;
import com.yompix.novograd.world.Seasons;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * «За городом» — точка входа: навыки SA (Skills), охота (Hunting), рыбалка и турнир (Fishing), времена года (world/Seasons)
 * и праздники (world/Holidays). Предметы, дичь и подарок регистрируются здесь; пакеты от клиента — с префиксом «out_».
 * Проверка: /outdoor skills [id|all N], /outdoor season ..., /outdoor holiday ..., /outdoor hunt, /outdoor fish, /outdoor tour.
 */
public final class Outdoors {
    private Outdoors() {}

    private static final List<ItemStack> TAB = new ArrayList<>();

    // ------------------------------------------------------------------ дичь
    public static final EntityType<WildAnimalEntity> DEER = Registry.register(Registries.ENTITY_TYPE, Novograd.id("deer"),
            EntityType.Builder.<WildAnimalEntity>create(WildAnimalEntity::new, SpawnGroup.CREATURE).dimensions(0.9f, 1.6f).maxTrackingRange(10).build("deer"));
    public static final EntityType<WildAnimalEntity> BOAR = Registry.register(Registries.ENTITY_TYPE, Novograd.id("boar"),
            EntityType.Builder.<WildAnimalEntity>create(WildAnimalEntity::new, SpawnGroup.CREATURE).dimensions(0.9f, 0.95f).maxTrackingRange(10).build("boar"));

    // ------------------------------------------------------------------ охота
    public static final Item LICENSE = item("hunting_license", new Item(new Item.Settings().maxCount(1)));
    public static final Item PELT_DEER = item("pelt_deer", new Item(new Item.Settings().maxCount(16)));
    public static final Item PELT_BOAR = item("pelt_boar", new Item(new Item.Settings().maxCount(16)));
    public static final Item PELT_WOLF = item("pelt_wolf", new Item(new Item.Settings().maxCount(16)));
    public static final Item PELT_FOX = item("pelt_fox", new Item(new Item.Settings().maxCount(16)));
    public static final Item PELT_RABBIT = item("pelt_rabbit", new Item(new Item.Settings().maxCount(16)));
    public static final Item HORN = item("goat_trophy", new Item(new Item.Settings().maxCount(16)));
    public static final Item VENISON = item("venison", new Item(new Item.Settings().food(food(6, 0.6f))));
    public static final Item BOAR_MEAT = item("boar_meat", new Item(new Item.Settings().food(food(6, 0.5f))));

    // ------------------------------------------------------------------ рыба (вес пишет Fishing при поимке)
    public static final Item PERCH = fish("fish_perch");
    public static final Item CRUCIAN = fish("fish_crucian");
    public static final Item PIKE = fish("fish_pike");
    public static final Item CATFISH = fish("fish_catfish");
    public static final Item SALMON = fish("fish_salmon");
    public static final Item MACKEREL = fish("fish_mackerel");
    public static final Item TUNA = fish("fish_tuna");
    public static final Item GOLDFISH = item("fish_gold", new Item(new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.EPIC)));

    private static FoodComponent food(int n, float sat) {
        return new FoodComponent.Builder().nutrition(n).saturationModifier(sat).build();
    }

    private static Item fish(String id) {
        return item(id, new Item(new Item.Settings().maxCount(16).food(food(3, 0.3f))));
    }

    static <I extends Item> I item(String name, I item) {
        Registry.register(Registries.ITEM, Novograd.id(name), item);
        TAB.add(new ItemStack(item));
        return item;
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(DEER, WildAnimalEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BOAR, WildAnimalEntity.createAttributes());
        Holidays.register(); // блок «подарок»
        ItemGroupEvents.modifyEntriesEvent(RegistryKey.of(RegistryKeys.ITEM_GROUP, Novograd.id("main"))).register(e -> {
            for (ItemStack s : TAB) e.add(s.copy());
            e.add(new ItemStack(Holidays.GIFT_ITEM));
        });
        ServerTickEvents.END_SERVER_TICK.register(Skills::tick);
        ServerTickEvents.END_SERVER_TICK.register(Hunting::tick);
        ServerTickEvents.END_SERVER_TICK.register(Fishing::tick);
        ServerTickEvents.END_SERVER_TICK.register(Seasons::tick);
        ServerTickEvents.END_SERVER_TICK.register(Holidays::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> Skills.afterDamage(entity, source, taken));
        ServerLivingEntityEvents.AFTER_DEATH.register(Hunting::afterDeath);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> Holidays.reset());
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> Seasons.sendState(h.player)));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            Skills.onLeave(h.player);
            Holidays.onLeave(h.player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(literal("outdoor")
                .requires(s -> s.getServer().isSingleplayer() || s.hasPermissionLevel(2))
                .then(argument("what", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    String r;
                    try {
                        r = debug(p, com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "what"));
                    } catch (RuntimeException ex) {
                        r = "Ошибка: " + ex.getMessage();
                    }
                    String msg = r;
                    ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                    return 1;
                }))));
    }

    /** Пакеты телефона и лавок. true — наш. */
    public static boolean handle(ServerPlayerEntity p, String type, NbtCompound d) {
        switch (type) {
            case "out_state" -> sendState(p);
            case "out_buy" -> Hunting.buy(p, d.getString("item"));
            case "out_sell" -> Hunting.sellAll(p, d.getString("shop"));
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Всё для приложения «Природа»: календарь, лицензия, рекорды, турнир. */
    public static void sendState(ServerPlayerEntity p) {
        NbtCompound n = new NbtCompound();
        n.put("season", Seasons.state(p.getServerWorld()));
        n.put("hunt", Hunting.state(p));
        n.put("fish", Fishing.state(p));
        n.putInt("money", Life.get(p).money);
        LifeNet.send(p, "out_state", n);
    }

    static String debug(ServerPlayerEntity p, String what) {
        String[] a = what.trim().toLowerCase(Locale.ROOT).split("\\s+");
        return switch (a[0]) {
            case "skills", "skill" -> Skills.debug(p, a);
            case "season" -> Seasons.debug(p, a);
            case "holiday" -> Holidays.debug(p, a);
            case "hunt" -> Hunting.debug(p, a);
            case "fish", "tour" -> Fishing.debug(p, a);
            default -> "outdoor skills|season|holiday|hunt|fish|tour";
        };
    }
}
