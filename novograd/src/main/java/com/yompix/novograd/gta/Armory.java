package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.BodyguardEntity;
import com.yompix.novograd.entity.RocketEntity;
import com.yompix.novograd.item.GunItem;
import com.yompix.novograd.item.RocketLauncherItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Rarity;

import java.util.List;

/**
 * Новые стволы «Калибра» (вдобавок к Arsenal): револьвер «Удав», «Орёл», «Микро», «Сайга», «Карабин», «Пулемёт»,
 * «Гранатомёт» (выстрелы ПГ — отдельные), мачете. Открываются по ходу сюжета (см. locked), обвесы — GunMods,
 * телохранители — Bodyguards.
 */
public final class Armory {
    private Armory() {}

    // ---------- звуки
    public static final SoundEvent REVOLVER_SHOT = sound("revolver_shot");
    public static final SoundEvent EAGLE_SHOT = sound("eagle_shot");
    public static final SoundEvent MICRO_SHOT = sound("micro_shot");
    public static final SoundEvent SAIGA_SHOT = sound("saiga_shot");
    public static final SoundEvent CARBINE_SHOT = sound("carbine_shot");
    public static final SoundEvent LMG_SHOT = sound("lmg_shot");
    public static final SoundEvent RPG_SHOT = sound("rpg_shot");
    public static final SoundEvent SILENCED_SHOT = sound("silenced_shot");
    public static final SoundEvent SILENCED_RIFLE = sound("silenced_rifle");
    public static final SoundEvent REVOLVER_RELOAD = sound("revolver_reload");
    public static final SoundEvent LMG_RELOAD = sound("lmg_reload");
    public static final SoundEvent RPG_RELOAD = sound("rpg_reload");

    // ---------- стволы: урон, темп (тики), очередь, дробинки, разброс, дальность, магазин, перезарядка (тики), отдача, звук, громкость, оптика
    public static final Item REVOLVER = item("revolver", new GunItem(new Item.Settings().maxCount(1),
            new GunItem.Spec(12f, 12, false, 1, 0.012, 56, 6, 58, 5.5f, REVOLVER_SHOT, 1.5f, false)));
    public static final Item EAGLE = item("eagle", new GunItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON),
            new GunItem.Spec(10f, 8, false, 1, 0.018, 60, 7, 40, 6.5f, EAGLE_SHOT, 1.5f, false)));
    public static final Item MICRO = item("micro", new GunItem(new Item.Settings().maxCount(1),
            new GunItem.Spec(2.6f, 2, true, 1, 0.075, 34, 32, 32, 0.55f, MICRO_SHOT, 0.8f, false)));
    public static final Item SAIGA = item("saiga", new GunItem(new Item.Settings().maxCount(1),
            new GunItem.Spec(2.6f, 6, false, 7, 0.15, 20, 8, 52, 4.2f, SAIGA_SHOT, 1.4f, false)));
    public static final Item CARBINE = item("carbine", new GunItem(new Item.Settings().maxCount(1),
            new GunItem.Spec(4.4f, 3, true, 1, 0.026, 72, 30, 42, 0.75f, CARBINE_SHOT, 1.1f, false)));
    public static final Item LMG = item("lmg", new GunItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON),
            new GunItem.Spec(4.6f, 3, true, 1, 0.055, 70, 100, 110, 0.85f, LMG_SHOT, 1.2f, false)));
    public static final Item RPG = item("rpg", new RocketLauncherItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE),
            new GunItem.Spec(0f, 24, false, 1, 0.01, 120, 1, 70, 7f, RPG_SHOT, 2f, false)));
    public static final Item ROCKET = item("rocket", new Item(new Item.Settings().maxCount(8)));
    public static final Item MACHETE = item("machete", new SwordItem(ToolMaterials.IRON,
            new Item.Settings().attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.IRON, 5, -2.2f))));

    public static final EntityType<RocketEntity> ROCKET_ENTITY = Registry.register(Registries.ENTITY_TYPE, Novograd.id("rocket"),
            EntityType.Builder.<RocketEntity>create(RocketEntity::new, SpawnGroup.MISC).dimensions(0.3f, 0.3f).maxTrackingRange(8).trackingTickInterval(1).build("rocket"));
    public static final EntityType<BodyguardEntity> BODYGUARD = Registry.register(Registries.ENTITY_TYPE, Novograd.id("bodyguard"),
            EntityType.Builder.<BodyguardEntity>create(BodyguardEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).maxTrackingRange(10).build("bodyguard"));

    /** Новые виды оружия в порядке колеса (после старых). */
    public static List<Item> weapons() {
        return List.of(MACHETE, REVOLVER, EAGLE, MICRO, SAIGA, CARBINE, LMG, RPG);
    }

    /**
     * Сюжетный допуск у оружейника Боба (act: 0 — акт I … 4 — акт V, 5 — сюжет пройден).
     * @return фраза отказа или null, если продаёт
     */
    public static String locked(Item it, int act) {
        int need = it == EAGLE || it == SAIGA || it == CARBINE ? 1 : it == LMG ? 2 : it == RPG || it == ROCKET ? 4 : 0;
        if (act >= need) return null;
        return it == RPG || it == ROCKET ? "Оружейник Боб: «Гранатомёт? Такое я продаю только тем, кто держит этот город. Пока это не ты.»"
                : "Оружейник Боб: «Такой ствол — только проверенным людям. Приходи, когда о тебе заговорят (акт " + roman(need) + ").»";
    }

    public static String roman(int act) {
        return switch (act) {
            case 0 -> "I";
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            default -> "V";
        };
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(RegistryKey.of(RegistryKeys.ITEM_GROUP, Novograd.id("main"))).register(e -> {
            for (Item i : List.of(REVOLVER, EAGLE, MICRO, SAIGA, CARBINE, LMG, RPG, ROCKET, MACHETE)) e.add(i);
        });
        FabricDefaultAttributeRegistry.register(BODYGUARD, BodyguardEntity.createGuardAttributes());
        GunMods.register();
        Bodyguards.register();
    }

    /** Патроны для ствола: у гранатомёта — выстрелы ПГ, у остальных — общие патроны. */
    public static Item ammoFor(Item gun) {
        return gun == RPG ? ROCKET : ModContent.AMMO;
    }

    private static SoundEvent sound(String name) {
        return Registry.register(Registries.SOUND_EVENT, Novograd.id(name), SoundEvent.of(Novograd.id(name)));
    }

    private static <I extends Item> I item(String name, I item) {
        return Registry.register(Registries.ITEM, Novograd.id(name), item);
    }
}
