package com.yompix.glukhoman;

import com.yompix.glukhoman.block.LampBlock;
import com.yompix.glukhoman.block.PropBlock;
import com.yompix.glukhoman.entity.AnnaEntity;
import com.yompix.glukhoman.entity.ChoirEntity;
import com.yompix.glukhoman.entity.ListenerEntity;
import com.yompix.glukhoman.entity.PebbleEntity;
import com.yompix.glukhoman.entity.PoleEntity;
import com.yompix.glukhoman.item.FlashlightItem;
import com.yompix.glukhoman.item.PebbleItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Всё, что регистрирует мод: блоки, предметы, существа, звуки. */
public final class GContent {
    private GContent() {}

    private static final List<ItemStack> TAB = new ArrayList<>();
    public static final Map<String, SoundEvent> SOUNDS = new HashMap<>();

    // ---------- блоки (интерактивность — по координатам в StoryState, сами блоки только для вида) ----------
    public static final Block RADIO = block("radio", new PropBlock(metal().luminance(s -> 3), box(2, 0, 4, 14, 9, 12)));
    public static final Block TAPE_PLAYER = block("tape_player", new PropBlock(metal(), box(2, 0, 3, 14, 6, 13)));
    public static final Block FUSE_BOX = block("fuse_box", new PropBlock(metal(), box(2, 1, 0, 14, 15, 4)));
    public static final Block GENERATOR = block("generator", new PropBlock(metal(), box(0, 0, 2, 16, 13, 14)));
    public static final Block AMPLIFIER = block("amplifier", new PropBlock(metal().luminance(s -> 4), box(1, 0, 1, 15, 16, 15)));
    public static final Block TRANSMITTER = block("transmitter", new PropBlock(metal().luminance(s -> 6), box(0, 0, 0, 16, 16, 16)));
    public static final Block NOTE = block("note", new PropBlock(AbstractBlock.Settings.create().strength(-1f, 3600000f).noCollision().nonOpaque()
            .sounds(BlockSoundGroup.WOOL).dropsNothing(), box(3, 0, 3, 13, 0.5, 13)));
    public static final Block CHALK_MARK = block("chalk_mark", new PropBlock(AbstractBlock.Settings.create().strength(-1f, 3600000f).noCollision().nonOpaque()
            .luminance(s -> 2).dropsNothing(), box(0, 0, 0, 16, 16, 1)));
    public static final Block LOCK = block("lock", new PropBlock(metal(), box(5, 4, 0, 11, 12, 4)));
    public static final Block CRATE = block("crate", new PropBlock(AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(-1f, 3600000f)
            .sounds(BlockSoundGroup.WOOD).nonOpaque(), box(1, 0, 1, 15, 12, 15)));
    public static final Block LAMP = block("lamp", new LampBlock(AbstractBlock.Settings.create().strength(-1f, 3600000f).sounds(BlockSoundGroup.GLASS)
            .nonOpaque().noCollision().luminance(s -> s.get(LampBlock.LIT) ? 13 : 0)));
    public static final Block STREET_LAMP = block("street_lamp", new LampBlock(AbstractBlock.Settings.create().strength(-1f, 3600000f).sounds(BlockSoundGroup.METAL)
            .nonOpaque().luminance(s -> s.get(LampBlock.LIT) ? 15 : 0)));

    // ---------- обстановка: история без слов ----------
    public static final Block POSTER = block("poster", new com.yompix.glukhoman.block.DecorBlock(decor(), box(1, 2, 0, 15, 15, 0.5)));
    public static final Block SCRATCHES = block("scratches", new com.yompix.glukhoman.block.DecorBlock(decor(), box(0, 0, 0, 16, 16, 0.5)));
    public static final Block DOLL = block("doll", new com.yompix.glukhoman.block.DecorBlock(decor(), box(5, 0, 6, 11, 9, 10)));
    public static final Block PHOTO = block("photo", new com.yompix.glukhoman.block.DecorBlock(decor(), box(3, 4, 0, 13, 13, 1)));

    // ---------- предметы ----------
    public static final Item FLASHLIGHT = item("flashlight", new FlashlightItem(new Item.Settings().maxCount(1).maxDamage(300), true));
    public static final Item BATTERY = item("battery", new Item(new Item.Settings().maxCount(16)));
    public static final Item OIL_LAMP = item("oil_lamp", new FlashlightItem(new Item.Settings().maxCount(1).maxDamage(420), false));
    public static final Item OIL = item("oil", new Item(new Item.Settings().maxCount(16)));
    public static final Item PEBBLE = item("pebble", new PebbleItem(new Item.Settings().maxCount(16)));
    public static final Item FUSE = item("fuse", new Item(new Item.Settings().maxCount(8)));
    public static final Item GATE_KEY = item("gate_key", new Item(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
    public static final Item MINE_KEY = item("mine_key", new Item(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
    public static final Item CANISTER = item("canister", new Item(new Item.Settings().maxCount(1)));
    public static final Item TUNING_FORK = item("tuning_fork", new Item(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));

    // ---------- существа ----------
    public static final EntityType<PoleEntity> POLE = entity("pole", EntityType.Builder.create(PoleEntity::new, SpawnGroup.MONSTER)
            .dimensions(0.7f, 3.6f).maxTrackingRange(12));
    public static final EntityType<ListenerEntity> LISTENER = entity("listener", EntityType.Builder.create(ListenerEntity::new, SpawnGroup.MONSTER)
            .dimensions(1.1f, 0.9f).maxTrackingRange(10));
    public static final EntityType<ChoirEntity> CHOIR = entity("choir", EntityType.Builder.create(ChoirEntity::new, SpawnGroup.MONSTER)
            .dimensions(1.8f, 3.2f).maxTrackingRange(12));
    public static final EntityType<com.yompix.glukhoman.entity.DoppelEntity> DOPPEL = entity("doppel", EntityType.Builder.create(com.yompix.glukhoman.entity.DoppelEntity::new, SpawnGroup.MONSTER)
            .dimensions(0.6f, 1.8f).maxTrackingRange(12));
    public static final EntityType<AnnaEntity> ANNA = entity("anna", EntityType.Builder.create(AnnaEntity::new, SpawnGroup.MISC)
            .dimensions(0.6f, 1.8f).maxTrackingRange(10));
    public static final EntityType<PebbleEntity> PEBBLE_ENTITY = entity("pebble", EntityType.Builder.<PebbleEntity>create(PebbleEntity::new, SpawnGroup.MISC)
            .dimensions(0.25f, 0.25f).maxTrackingRange(6).trackingTickInterval(10));

    /** Звуки: имя = путь в sounds.json. Голоса добавляются из glukhoman_story.json (voice.<id>). */
    public static final String[] SOUND_NAMES = {
            "amb.forest", "amb.village", "amb.mine", "amb.station", "amb.bunker", "amb.dread", "amb.relief", "amb.chase",
            "heartbeat", "breath", "knock", "steps_far", "steps_near", "whisper", "radio_static", "radio_click", "stinger", "stinger_soft",
            "pole_appear", "pole_vanish", "pole_step", "listener_click", "listener_scream", "listener_hurt", "choir_roar", "choir_hum", "choir_hurt",
            "flashlight_click", "battery_dead", "fuse", "generator", "power_on", "lamp_buzz", "door_creak", "lift", "tape_click", "title_hit",
            "pebble", "branch_snap", "distant_scream", "wind_gust", "jumpscare", "breath_close", "scratch", "musicbox", "crash_buzz", "slowmo"};

    public static SoundEvent sound(String name) {
        return SOUNDS.get(name);
    }

    public static void init() {
        for (String n : SOUND_NAMES) registerSound(n);
        for (String id : com.yompix.glukhoman.story.Texts.get().lineIds()) registerSound("voice." + id);
        FabricDefaultAttributeRegistry.register(POLE, PoleEntity.attributes());
        FabricDefaultAttributeRegistry.register(LISTENER, ListenerEntity.attributes());
        FabricDefaultAttributeRegistry.register(CHOIR, ChoirEntity.attributes());
        FabricDefaultAttributeRegistry.register(ANNA, AnnaEntity.attributes());
        FabricDefaultAttributeRegistry.register(DOPPEL, com.yompix.glukhoman.entity.DoppelEntity.attributes());
        Registry.register(Registries.CHUNK_GENERATOR, Glukhoman.id("taiga"), com.yompix.glukhoman.world.TaigaChunkGenerator.CODEC);
        Registry.register(Registries.ITEM_GROUP, Glukhoman.id("main"), FabricItemGroup.builder()
                .icon(() -> new ItemStack(FLASHLIGHT)).displayName(Text.translatable("itemGroup.glukhoman"))
                .entries((ctx, entries) -> TAB.forEach(entries::add)).build());
    }

    private static void registerSound(String n) {
        var id = Glukhoman.id(n);
        SOUNDS.put(n, Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id)));
    }

    private static AbstractBlock.Settings decor() {
        return AbstractBlock.Settings.create().strength(-1f, 3600000f).noCollision().nonOpaque().sounds(BlockSoundGroup.WOOL).dropsNothing();
    }

    private static AbstractBlock.Settings metal() {
        return AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(-1f, 3600000f).sounds(BlockSoundGroup.METAL).nonOpaque();
    }

    public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return VoxelShapes.cuboid(x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, z2 / 16);
    }

    private static Block block(String name, Block b) {
        Registry.register(Registries.BLOCK, Glukhoman.id(name), b);
        Item it = Registry.register(Registries.ITEM, Glukhoman.id(name), new BlockItem(b, new Item.Settings()));
        TAB.add(new ItemStack(it));
        return b;
    }

    private static Item item(String name, Item it) {
        Registry.register(Registries.ITEM, Glukhoman.id(name), it);
        TAB.add(new ItemStack(it));
        return it;
    }

    private static <T extends net.minecraft.entity.Entity> EntityType<T> entity(String name, EntityType.Builder<T> b) {
        return Registry.register(Registries.ENTITY_TYPE, Glukhoman.id(name), b.build());
    }
}
