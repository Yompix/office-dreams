package com.yompix.novograd;

import com.yompix.novograd.block.FacingBlock;
import com.yompix.novograd.block.LifeBlock;
import com.yompix.novograd.block.TrafficLightBlock;
import com.yompix.novograd.block.TvBlock;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.entity.SeatEntity;
import com.yompix.novograd.item.CarItem;
import com.yompix.novograd.item.PhoneItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.FoodComponent;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

import static com.yompix.novograd.block.FacingBlock.box;

public final class ModContent {
    private ModContent() {}

    private static final List<ItemStack> TAB = new ArrayList<>();

    // ---------- звуки ----------
    public static final SoundEvent SCHOOL_BELL = sound("school_bell");
    public static final SoundEvent PHONE_NOTIFY = sound("phone_notify");
    public static final SoundEvent PHONE_OPEN = sound("phone_open");
    public static final SoundEvent PHONE_CLICK = sound("phone_click");
    public static final SoundEvent CAR_HORN = sound("car_horn");
    public static final SoundEvent GUNSHOT = sound("gunshot");
    public static final SoundEvent SMG_SHOT = sound("smg_shot");
    public static final SoundEvent SIREN = sound("siren");
    public static final SoundEvent CAR_ENGINE = sound("car_engine");
    public static final SoundEvent CAR_START = sound("car_start");
    public static final SoundEvent CASH = sound("cash");
    public static final SoundEvent ANSWER_RIGHT = sound("answer_right");
    public static final SoundEvent ANSWER_WRONG = sound("answer_wrong");
    public static final SoundEvent TV_SWITCH = sound("tv_switch");
    public static final SoundEvent LEVEL_UP = sound("level_up");

    // ---------- компонент: адрес доставки посылки ----------
    public static final ComponentType<BlockPos> DELIVERY_TARGET = Registry.register(Registries.DATA_COMPONENT_TYPE, Novograd.id("delivery_target"),
            ComponentType.<BlockPos>builder().codec(BlockPos.CODEC).packetCodec(BlockPos.PACKET_CODEC).build());

    // ---------- блоки ----------
    public static final Block ASPHALT = block("asphalt", new Block(stone(MapColor.GRAY)));
    public static final Block ASPHALT_LINE = block("asphalt_line", new FacingBlock(stone(MapColor.GRAY)));
    public static final Block ASPHALT_EDGE = block("asphalt_edge", new FacingBlock(stone(MapColor.GRAY)));
    public static final Block CROSSWALK = block("crosswalk", new FacingBlock(stone(MapColor.GRAY)));
    public static final Block SIDEWALK = block("sidewalk", new Block(stone(MapColor.STONE_GRAY)));
    public static final Block CHALKBOARD = block("chalkboard", new LifeBlock(wood().nonOpaque(), LifeBlock.Kind.CHALKBOARD, box(0, 0, 13, 16, 16, 16)));
    public static final Block SCHOOL_DESK = block("school_desk", new LifeBlock(wood().nonOpaque(), LifeBlock.Kind.DESK,
            VoxelShapes.union(box(1, 10, 0, 15, 12, 9), box(2, 0, 1, 4, 10, 3), box(12, 0, 1, 14, 10, 3), box(2, 0, 10, 14, 7, 16))));
    public static final Block TV = block("tv", new TvBlock(metal().nonOpaque().luminance(s -> s.get(TvBlock.CHANNEL) > 0 ? 7 : 0)));
    public static final Block TRAFFIC_LIGHT = block("traffic_light", new TrafficLightBlock(metal().nonOpaque().luminance(s -> 9)));
    public static final Block LAMP_POST = block("lamp_post", new FacingBlock(metal().nonOpaque(), box(6, 0, 6, 10, 16, 10)));
    public static final Block STREET_LAMP = block("street_lamp", new FacingBlock(metal().nonOpaque().luminance(s -> 15),
            VoxelShapes.union(box(6, 0, 6, 10, 16, 10), box(5, 12, 0, 11, 16, 6))));
    public static final Block ATM = block("atm", new LifeBlock(metal().nonOpaque(), LifeBlock.Kind.ATM, box(1, 0, 2, 15, 16, 16)));
    public static final Block JOB_BOARD = block("job_board", new LifeBlock(wood().nonOpaque(), LifeBlock.Kind.JOB_BOARD, box(0, 0, 12, 16, 16, 16)));
    public static final Block MAILBOX = block("mailbox", new LifeBlock(metal().nonOpaque(), LifeBlock.Kind.MAILBOX,
            VoxelShapes.union(box(4, 7, 3, 12, 15, 13), box(7, 0, 7, 9, 7, 9))));
    public static final Block SHOP_COUNTER = block("shop_counter", new LifeBlock(wood(), LifeBlock.Kind.SHOP_COUNTER, VoxelShapes.fullCube()));
    public static final Block COMPUTER = block("computer", new LifeBlock(metal().nonOpaque().luminance(s -> 5), LifeBlock.Kind.COMPUTER,
            VoxelShapes.union(box(2, 0, 9, 14, 1, 13), box(7, 1, 10, 9, 4, 12), box(1, 4, 9, 15, 14, 11))));

    public static final Block SLOT_MACHINE = block("slot_machine", new LifeBlock(metal().nonOpaque().luminance(s -> 8), LifeBlock.Kind.SLOT_MACHINE, box(1, 0, 2, 15, 16, 15)));
    public static final Block TREADMILL = block("treadmill", new LifeBlock(metal().nonOpaque(), LifeBlock.Kind.TREADMILL, box(1, 0, 0, 15, 14, 16)));
    public static final Block BARBELL = block("barbell", new LifeBlock(metal().nonOpaque(), LifeBlock.Kind.BARBELL, box(0, 0, 3, 16, 15, 13)));
    public static final Block SCALES = block("scales", new LifeBlock(metal().nonOpaque(), LifeBlock.Kind.SCALES, box(3, 0, 3, 13, 16, 13)));
    public static final Block DJ_DECK = block("dj_deck", new LifeBlock(metal().nonOpaque().luminance(s -> 10), LifeBlock.Kind.DJ_DECK, box(0, 0, 3, 16, 14, 13)));
    public static final Block PIZZA_OVEN = block("pizza_oven", new LifeBlock(net.minecraft.block.AbstractBlock.Settings.copy(net.minecraft.block.Blocks.BRICKS).nonOpaque().luminance(s -> 12), LifeBlock.Kind.PIZZA_OVEN, box(0, 0, 0, 16, 16, 16)));
    public static final Block ROULETTE = block("roulette", new LifeBlock(wood().nonOpaque().luminance(s -> 4), LifeBlock.Kind.ROULETTE, box(0, 0, 0, 16, 13, 16)));

    public static final Block NEON = block("neon", new com.yompix.novograd.block.NeonBlock(AbstractBlock.Settings.create().strength(0.5f).nonOpaque().noCollision()
            .sounds(BlockSoundGroup.GLASS).luminance(s -> s.get(com.yompix.novograd.block.NeonBlock.LIT) ? 15 : 0), box(0, 5, 13, 16, 11, 16)));
    public static final Block NEON_FLOOR = block("neon_floor", new com.yompix.novograd.block.NeonBlock(AbstractBlock.Settings.create().strength(0.5f).nonOpaque().noCollision()
            .sounds(BlockSoundGroup.GLASS).luminance(s -> s.get(com.yompix.novograd.block.NeonBlock.LIT) ? 14 : 0), box(0, 0, 4, 16, 1, 12)));
    public static final Block CLUE = block("clue", new com.yompix.novograd.block.ClueBlock(AbstractBlock.Settings.create().strength(-1f, 3600000f).noCollision().nonOpaque().luminance(s -> 10).dropsNothing()));

    // ---------- предметы ----------
    public static final Item PHONE = item("phone", new PhoneItem(new Item.Settings().maxCount(1)));
    public static final Item BANKNOTE = item("banknote", new Item(new Item.Settings()));
    public static final Item BANKNOTE_50 = item("banknote_50", new Item(new Item.Settings()));
    public static final Item BANKNOTE_100 = item("banknote_100", new Item(new Item.Settings()));
    public static final Item BANK_CARD = item("bank_card", new Item(new Item.Settings().maxCount(1)));
    public static final Item PARCEL = item("parcel", new Item(new Item.Settings().maxCount(1)));
    public static final Item DIPLOMA = item("diploma", new Item(new Item.Settings().maxCount(1).rarity(Rarity.RARE)));
    public static final Item TEXTBOOK = item("textbook", new Item(new Item.Settings().maxCount(16)));
    public static final Item SUIT = item("suit", new Item(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
    public static final Item BURGER = item("burger", new com.yompix.novograd.item.SnackItem(new Item.Settings().food(new FoodComponent.Builder().nutrition(8).saturationModifier(0.8f).build()), false));
    public static final Item COLA = item("cola", new com.yompix.novograd.item.SnackItem(new Item.Settings().food(new FoodComponent.Builder().nutrition(2).saturationModifier(0.3f).alwaysEdible().snack().build()), true));
    public static final Item PIZZA = item("pizza", new com.yompix.novograd.item.SnackItem(new Item.Settings().food(new FoodComponent.Builder().nutrition(7).saturationModifier(0.7f).build()), false, 0.4f));
    public static final Item SALAD = item("salad", new com.yompix.novograd.item.SnackItem(new Item.Settings().food(new FoodComponent.Builder().nutrition(4).saturationModifier(0.6f).alwaysEdible().build()), false, -0.15f));
    public static final Item PISTOL = item("pistol", new com.yompix.novograd.item.GunItem(new Item.Settings().maxCount(1), 6f, 9, false));
    public static final Item SMG = item("smg", new com.yompix.novograd.item.GunItem(new Item.Settings().maxCount(1), 3.5f, 3, true));
    public static final Item AMMO = item("ammo", new Item(new Item.Settings().maxCount(64)));
    public static final Item BAT = item("bat", new net.minecraft.item.SwordItem(net.minecraft.item.ToolMaterials.WOOD,
            new Item.Settings().attributeModifiers(net.minecraft.item.SwordItem.createAttributeModifiers(net.minecraft.item.ToolMaterials.WOOD, 4, -2.6f))));

    // ---------- существа ----------
    public static final EntityType<CarEntity> CAR = Registry.register(Registries.ENTITY_TYPE, Novograd.id("car"),
            EntityType.Builder.<CarEntity>create(CarEntity::new, SpawnGroup.MISC).dimensions(2.2f, 1.95f).maxTrackingRange(10).trackingTickInterval(1).alwaysUpdateVelocity(true).build("car"));
    public static final EntityType<CitizenEntity> CITIZEN = Registry.register(Registries.ENTITY_TYPE, Novograd.id("citizen"),
            EntityType.Builder.<CitizenEntity>create(CitizenEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).eyeHeight(1.62f).maxTrackingRange(10).build("citizen"));
    public static final EntityType<SeatEntity> SEAT = Registry.register(Registries.ENTITY_TYPE, Novograd.id("seat"),
            EntityType.Builder.<SeatEntity>create(SeatEntity::new, SpawnGroup.MISC).dimensions(0.01f, 0.01f).disableSummon().build("seat"));

    public static final EntityType<com.yompix.novograd.entity.BallEntity> BALL = Registry.register(Registries.ENTITY_TYPE, Novograd.id("ball"),
            EntityType.Builder.<com.yompix.novograd.entity.BallEntity>create(com.yompix.novograd.entity.BallEntity::new, SpawnGroup.MISC).dimensions(0.45f, 0.45f).maxTrackingRange(8).trackingTickInterval(1).build("ball"));
    public static final EntityType<CarEntity> SCOOTER = Registry.register(Registries.ENTITY_TYPE, Novograd.id("scooter"),
            EntityType.Builder.<CarEntity>create(CarEntity::new, SpawnGroup.MISC).dimensions(0.9f, 1.1f).maxTrackingRange(10).trackingTickInterval(1).alwaysUpdateVelocity(true).build("scooter"));
    public static final Item SCOOTER_ITEM = item("scooter", new CarItem(new Item.Settings().maxCount(1), 0, true));

    public static final Item[] CARS = new Item[CarEntity.VARIANTS.length];
    static {
        for (int i = 0; i < CarEntity.VARIANTS.length; i++)
            CARS[i] = item("car_" + CarEntity.VARIANTS[i], new CarItem(new Item.Settings().maxCount(1), i));
    }

    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, Novograd.id("main"), FabricItemGroup.builder()
            .icon(() -> new ItemStack(PHONE))
            .displayName(Text.translatable("itemGroup.novograd"))
            .entries((ctx, entries) -> entries.addAll(TAB))
            .build());

    static {
        Registry.register(Registries.CHUNK_GENERATOR, Novograd.id("island"), com.yompix.novograd.world.IslandChunkGenerator.CODEC);
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(CITIZEN, CitizenEntity.createAttributes());
    }

    private static AbstractBlock.Settings stone(MapColor color) {
        return AbstractBlock.Settings.create().mapColor(color).strength(1.5f, 6f).sounds(BlockSoundGroup.STONE).requiresTool();
    }

    private static AbstractBlock.Settings wood() {
        return AbstractBlock.Settings.create().mapColor(MapColor.OAK_TAN).strength(1.5f).sounds(BlockSoundGroup.WOOD);
    }

    private static AbstractBlock.Settings metal() {
        return AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(2f, 6f).sounds(BlockSoundGroup.METAL);
    }

    private static SoundEvent sound(String name) {
        return Registry.register(Registries.SOUND_EVENT, Novograd.id(name), SoundEvent.of(Novograd.id(name)));
    }

    private static <B extends Block> B block(String name, B block) {
        Registry.register(Registries.BLOCK, Novograd.id(name), block);
        Item item = Registry.register(Registries.ITEM, Novograd.id(name), new BlockItem(block, new Item.Settings()));
        TAB.add(new ItemStack(item));
        return block;
    }

    private static <I extends Item> I item(String name, I item) {
        Registry.register(Registries.ITEM, Novograd.id(name), item);
        TAB.add(new ItemStack(item));
        return item;
    }
}
