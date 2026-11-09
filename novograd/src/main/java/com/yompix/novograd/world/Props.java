package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.block.FacingBlock;
import com.yompix.novograd.block.PropBlock;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.entity.DebrisEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeNet;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.yompix.novograd.block.FacingBlock.box;

/**
 * Интерактивная улица: автоматы с газировкой, таксофоны, газетные киоски, тележки с хот-догами и мороженым,
 * баллоны с пропаном, конусы — ПКМ, чтобы пользоваться. Ещё: мойка (умыться), холодильник дома (перекусить),
 * урна (порыться), банкомат с монтировкой/битой (Shift+ПКМ — вскрыть).
 * <p>
 * Фонари, гидранты, урны, скамейки и киоски на тротуарах расставляются при первой загрузке чанка
 * (и в старых сохранениях), по 1–2 чанка за тик. Ломаются и чинятся — {@link Destruction}, огонь — {@link Fires}.
 */
public final class Props {
    private Props() {}

    // ------------------------------------------------------------------ блоки и обломки
    private static AbstractBlock.Settings metal() {
        return AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(-1f, 6f).sounds(BlockSoundGroup.METAL).nonOpaque().dropsNothing();
    }

    public static final Block VENDING = block("vending_machine", new PropBlock(metal().mapColor(MapColor.RED).luminance(s -> 6), PropBlock.Kind.VENDING, box(1, 0, 1, 15, 16, 15)));
    public static final Block PAYPHONE = block("payphone", new PropBlock(metal(), PropBlock.Kind.PAYPHONE, box(4, 0, 4, 12, 16, 12)));
    public static final Block NEWS_STAND = block("news_stand", new PropBlock(metal().mapColor(MapColor.GREEN), PropBlock.Kind.NEWS_STAND, box(1, 0, 3, 15, 14, 13)));
    public static final Block HOTDOG_CART = block("hotdog_cart", new PropBlock(metal(), PropBlock.Kind.HOTDOG, box(0, 0, 3, 16, 15, 13)));
    public static final Block ICECREAM_CART = block("icecream_cart", new PropBlock(metal().mapColor(MapColor.WHITE), PropBlock.Kind.ICECREAM, box(0, 0, 3, 16, 15, 13)));
    public static final Block PROPANE = block("propane_tank", new PropBlock(metal().mapColor(MapColor.WHITE), PropBlock.Kind.PROPANE, box(4, 0, 4, 12, 15, 12)));
    public static final Block CONE = block("traffic_cone", new PropBlock(metal().mapColor(MapColor.ORANGE).sounds(BlockSoundGroup.WOOL), PropBlock.Kind.CONE, box(3, 0, 3, 13, 11, 13)));
    /** Фонарь с разбитой лампой (выстрел): не светит, через пару минут его чинят. */
    public static final Block LAMP_BROKEN = block("street_lamp_broken", new FacingBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
            .strength(2f, 6f).sounds(BlockSoundGroup.METAL).nonOpaque().dropsNothing(), VoxelShapes.union(box(6, 0, 6, 10, 16, 10), box(5, 12, 0, 11, 16, 6))));

    public static final EntityType<DebrisEntity> DEBRIS = Registry.register(Registries.ENTITY_TYPE, Novograd.id("debris"),
            EntityType.Builder.<DebrisEntity>create(DebrisEntity::new, SpawnGroup.MISC).dimensions(0.6f, 0.6f).maxTrackingRange(8).trackingTickInterval(2).build("debris"));

    private static final List<Block> TAB = List.of(VENDING, PAYPHONE, NEWS_STAND, HOTDOG_CART, ICECREAM_CART, PROPANE, CONE);

    private static Block block(String name, Block b) {
        Registry.register(Registries.BLOCK, Novograd.id(name), b);
        Registry.register(Registries.ITEM, Novograd.id(name), new BlockItem(b, new Item.Settings()));
        return b;
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(RegistryKey.of(RegistryKeys.ITEM_GROUP, Novograd.id("main"))).register(e -> TAB.forEach(e::add));
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            long t0 = System.nanoTime();
            try {
                Destruction.tick(s);
                Fires.tick(s);
                decorateTick(s);
            } catch (RuntimeException ex) {
                Novograd.LOG.warn("Props tick", ex);
            }
            long dt = System.nanoTime() - t0;
            tickNanos += dt;
            tickMax = Math.max(tickMax, dt);
            ticks++;
        });
        ServerChunkEvents.CHUNK_LOAD.register((w, chunk) -> {
            if (w.getRegistryKey() == World.OVERWORLD) QUEUE.add(chunk.getPos().toLong());
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity p) || p.isSpectator()) return ActionResult.PASS;
            return useVanilla((ServerWorld) world, hit.getBlockPos(), p, p.getMainHandStack());
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity p && world.getBlockState(pos).getBlock() instanceof PropBlock pb && pb.kind == PropBlock.Kind.VENDING)
                kickVending((ServerWorld) world, pos, p);
            return ActionResult.PASS;
        });
    }

    // ------------------------------------------------------------------ ПКМ по уличным предметам
    private static final Map<UUID, Long> COOL = new HashMap<>();

    private static boolean cooldown(ServerPlayerEntity p, String what, int ticks) {
        long now = p.getServerWorld().getTime();
        UUID k = UUID.nameUUIDFromBytes((p.getUuid() + what).getBytes());
        if (now - COOL.getOrDefault(k, -100000L) < ticks) return true;
        COOL.put(k, now);
        return false;
    }

    public static void use(PropBlock.Kind kind, BlockState state, ServerWorld w, BlockPos pos, ServerPlayerEntity p) {
        switch (kind) {
            case VENDING -> vending(w, pos, p);
            case PAYPHONE -> payphone(w, pos, p);
            case NEWS_STAND -> newspaper(w, pos, p);
            case HOTDOG -> food(w, pos, p, 6, 7, 4f, "Хот-дог", FunnyLines.HOTDOG);
            case ICECREAM -> food(w, pos, p, 4, 3, 2f, "Пломбир «Новоградский»", FunnyLines.ICECREAM);
            case PROPANE -> p.sendMessage(Text.literal("⚠ Пропан. Огнеопасно! Не стрелять и не курить").formatted(Formatting.GOLD), true);
            case CONE -> p.sendMessage(Text.literal("Дорожный конус. Объезжай… или нет").formatted(Formatting.GOLD), true);
        }
    }

    private static final class FunnyLines {
        static final String[] HOTDOG = {"«С горчицей и луком, как ты любишь!»", "«Сосиски утренние, клянусь мамой»", "«Кетчуп за счёт заведения»",
                "«Видел вчера погоню? Полгорода в дыму»", "«Не капни на рубашку, ха!»"};
        static final String[] ICECREAM = {"«Холодное, как сердце мэра»", "«Пломбир, сам бы ел»", "«В жару — лучшее лекарство»", "«Вафельный стаканчик хрустит!»"};
        static final String[] VENDING_FAIL = {"Автомат зажевал деньги. Пни его!", "Банка застряла… Пни автомат!"};
    }

    private static void vending(ServerWorld w, BlockPos pos, ServerPlayerEntity p) {
        if (cooldown(p, "vend", 20)) return;
        boolean snack = p.isSneaking();
        if (!Life.pay(p, snack ? 4 : 3, snack ? "чипсы «Хруст»" : "газировка «Новоград-Кола»")) return;
        w.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 0.8f, 0.7f); // бам — банка упала
        if (w.random.nextInt(14) == 0) {
            p.sendMessage(Text.literal(FunnyLines.VENDING_FAIL[w.random.nextInt(FunnyLines.VENDING_FAIL.length)]).formatted(Formatting.YELLOW), true);
            JAMMED.put(pos.asLong(), w.getTime());
            return;
        }
        if (snack) {
            p.getHungerManager().add(5, 0.5f);
            p.heal(1);
            w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.8f, 1.2f);
            p.sendMessage(Text.literal("Чипсы «Хруст»: +сытость").formatted(Formatting.GREEN), true);
        } else {
            p.getHungerManager().add(2, 0.3f);
            p.heal(4);
            w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 1.1f);
            p.sendMessage(Text.literal("«Новоград-Кола»: +здоровье   (Shift+ПКМ — чипсы)").formatted(Formatting.GREEN), true);
        }
    }

    private static final Map<Long, Long> JAMMED = new HashMap<>();

    /** Пнуть автомат: зажевал деньги — отдаст банку; иначе изредка тоже. */
    private static void kickVending(ServerWorld w, BlockPos pos, ServerPlayerEntity p) {
        if (cooldown(p, "kick", 12)) return;
        w.playSound(null, pos, SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.BLOCKS, 0.6f, 1.3f);
        boolean jam = w.getTime() - JAMMED.getOrDefault(pos.asLong(), -100000L) < 1200;
        if (jam || w.random.nextInt(12) == 0) {
            JAMMED.remove(pos.asLong());
            Destruction.dropCan(w, Vec3d.ofCenter(pos).add(Vec3d.of(w.getBlockState(pos).get(HorizontalFacingBlock.FACING).getVector()).multiply(0.7)));
            p.sendMessage(Text.literal("Бам! Автомат выплюнул банку").formatted(Formatting.GREEN), true);
        }
    }

    private static void payphone(ServerWorld w, BlockPos pos, ServerPlayerEntity p) {
        w.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.BLOCKS, 0.5f, 1.6f);
        if (p.isSneaking()) {
            anonymousTip(w, p);
            return;
        }
        p.sendMessage(Text.literal("Таксофон: «Служба такси Новограда, куда едем?»   (Shift+ПКМ — анонимный звонок в полицию)").formatted(Formatting.YELLOW), false);
        NbtCompound n = new NbtCompound();
        n.putString("app", "taxi");
        LifeNet.send(p, "open_phone", n);
    }

    /** Анонимный звонок: полиция едет разбираться с бандой поблизости. */
    private static void anonymousTip(ServerWorld w, ServerPlayerEntity p) {
        if (cooldown(p, "tip", 20 * 120)) {
            p.sendMessage(Text.literal("Диспетчер: «Опять вы? Наряд уже выехал»").formatted(Formatting.GRAY), true);
            return;
        }
        List<CitizenEntity> gang = w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(110, 30, 110),
                c -> c.isAlive() && c.getRole() == CitizenEntity.Role.GANG && !c.hasVehicle());
        String area = com.yompix.novograd.gta.Territories.turfName(Math.max(0, com.yompix.novograd.gta.Territories.at(p.getX(), p.getZ())));
        if (gang.isEmpty()) {
            p.sendMessage(Text.literal("Ты: «Тут бандиты…» Диспетчер: «Принято. Наряд проверит район»").formatted(Formatting.BLUE), false);
            com.yompix.novograd.gta.News.post(w.getServer(), "Анонимный звонок: в районе «" + area + "» видели вооружённых людей", "КРИМИНАЛ");
            return;
        }
        CitizenEntity g = gang.get(0);
        for (CitizenEntity c : gang) if (c.squaredDistanceTo(p) < g.squaredDistanceTo(p)) g = c;
        // наряд: патрульная машина у ближайшего тротуара в 30–40 блоках и двое полицейских
        Vec3d at = com.yompix.novograd.gta.FunUtil.curbAround(p, 25, 45);
        if (at == null) at = g.getPos().add(20, 0, 0);
        CarEntity car = com.yompix.novograd.gta.FunUtil.car(w, at, com.yompix.novograd.gta.FunUtil.yawTo(at, g.getPos()), CarEntity.POLICE);
        car.toggleLight(CarEntity.HAZARD);
        TIP_CARS.put(car.getUuid(), w.getTime() + 20 * 120);
        w.playSound(null, car.getBlockPos(), ModContent.SIREN, SoundCategory.NEUTRAL, 1.6f, 1f);
        final CitizenEntity target = g;
        for (int i = 0; i < 2; i++) {
            CitizenEntity cop = com.yompix.novograd.gta.FunUtil.citizen(w, at.add(i * 1.5 - 0.75, 0, 1.5), CitizenEntity.Role.POLICE, null);
            cop.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new ItemStack(ModContent.PISTOL));
            cop.attack(target);
            cop.removeLater(20 * 100);
        }
        p.sendMessage(Text.literal("Ты (шёпотом): «Тут бандиты…» Диспетчер: «Наряд выезжает». Полиция разберётся с бандой").formatted(Formatting.BLUE), false);
        com.yompix.novograd.gta.News.post(w.getServer(), "Полиция выехала по анонимному звонку: перестрелка в районе «" + area + "»", "КРИМИНАЛ");
    }

    private static final Map<UUID, Long> TIP_CARS = new HashMap<>();

    private static void newspaper(ServerWorld w, BlockPos pos, ServerPlayerEntity p) {
        if (cooldown(p, "news", 30)) return;
        if (!Life.pay(p, 2, "газета «Вестник Новограда»")) return;
        w.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 1f, 1f);
        p.sendMessage(Text.literal("«ВЕСТНИК НОВОГРАДА» — свежий номер").formatted(Formatting.GOLD, Formatting.BOLD), false);
        var news = com.yompix.novograd.gta.News.latest(4);
        if (news.isEmpty()) {
            String[] fallback = {"Мэр обещает новые фонари на всех улицах", "В порту ждут рекордный урожай контейнеров", "Синоптики: ночью туман, водителям — осторожность"};
            for (String s : fallback) p.sendMessage(Text.literal(" • " + s).formatted(Formatting.WHITE), false);
        } else for (String[] n : news)
            p.sendMessage(Text.literal(" • [" + n[1] + "] ").formatted(Formatting.GRAY).append(Text.literal(n[0]).formatted(Formatting.WHITE)), false);
        int broken = Destruction.brokenCount(w);
        if (broken > 0) p.sendMessage(Text.literal(" • Коммунальщики: «Чиним " + broken + " сломанных фонарей, гидрантов и витрин»").formatted(Formatting.DARK_AQUA), false);
    }

    private static void food(ServerWorld w, BlockPos pos, ServerPlayerEntity p, int price, int hunger, float heal, String what, String[] lines) {
        if (cooldown(p, "food", 30)) return;
        if (!Life.pay(p, price, what)) return;
        p.getHungerManager().add(hunger, 0.6f);
        p.heal(heal);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.9f, 1f);
        p.sendMessage(Text.literal(what + " — продавец: ").formatted(Formatting.YELLOW).append(Text.literal(lines[w.random.nextInt(lines.length)]).formatted(Formatting.WHITE)), false);
        Life.get(p).mood = Math.min(100, Life.get(p).mood + 1);
    }

    // ------------------------------------------------------------------ обычные блоки города: мойка, холодильник, урна, банкомат
    private static final Map<Long, Long> TRASH = new HashMap<>();
    private static final Map<UUID, int[]> PRY = new HashMap<>();

    private static ActionResult useVanilla(ServerWorld w, BlockPos pos, ServerPlayerEntity p, ItemStack hand) {
        BlockState st = w.getBlockState(pos);
        if (st.isOf(ModContent.ATM) && p.isSneaking() && hand.getItem() instanceof SwordItem) {
            pryAtm(w, pos, p);
            return ActionResult.SUCCESS;
        }
        if (st.isOf(ModContent.TV) && !p.isSneaking()) {
            int ch = st.get(com.yompix.novograd.block.TvBlock.CHANNEL);
            if (ch == 0 || ch == 5) { // сейчас включится «Новости 24» — бегущая строка в чат
                var news = com.yompix.novograd.gta.News.latest(2);
                for (String[] n : news) p.sendMessage(Text.literal("Новости 24 [" + n[1] + "]: ").formatted(Formatting.AQUA).append(Text.literal(n[0]).formatted(Formatting.WHITE)), false);
                if (news.isEmpty()) p.sendMessage(Text.literal("Новости 24: «В Новограде спокойно… пока»").formatted(Formatting.AQUA), false);
            }
            return ActionResult.PASS; // канал переключит сам телевизор
        }
        if (!hand.isEmpty()) return ActionResult.PASS;
        if (st.isOf(Blocks.WATER_CAULDRON) || st.isOf(Blocks.CAULDRON)) {
            if (cooldown(p, "sink", 20)) return ActionResult.SUCCESS;
            Vec3d c = Vec3d.ofCenter(pos);
            w.spawnParticles(ParticleTypes.SPLASH, c.x, c.y + 0.5, c.z, 14, 0.25, 0.1, 0.25, 0.1);
            w.spawnParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 0.9, c.z, 4, 0.1, 0.05, 0.1, 0);
            w.playSound(null, pos, SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS, 1f, 1.5f);
            p.extinguish();
            Life.get(p).mood = Math.min(100, Life.get(p).mood + 1);
            p.sendMessage(Text.literal("Умылся холодной водой — бодрит").formatted(Formatting.AQUA), true);
            return ActionResult.SUCCESS;
        }
        if (st.isOf(Blocks.SMOOTH_QUARTZ) && fridge(w, pos)) {
            if (cooldown(p, "fridge", 20 * 45)) {
                p.sendMessage(Text.literal("В холодильнике пусто… загляни попозже").formatted(Formatting.GRAY), true);
                return ActionResult.SUCCESS;
            }
            String[] eat = {"бутерброд с колбасой", "холодный борщ", "пельмени", "йогурт", "котлету с пюре", "кусок пиццы", "сырок"};
            p.getHungerManager().add(6, 0.6f);
            p.heal(3);
            w.playSound(null, pos, SoundEvents.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.7f, 1.4f);
            w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, 0.8f, 1f);
            p.sendMessage(Text.literal("Холодильник: съел " + eat[w.random.nextInt(eat.length)]).formatted(Formatting.GREEN), true);
            return ActionResult.SUCCESS;
        }
        if (st.isOf(Blocks.COMPOSTER) && Destruction.onSidewalk(w, pos)) {
            rummage(w, pos, p);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /** Холодильник в квартирах (world/Buildings): два блока гладкого кварца на полу рядом с плитой или мойкой. */
    private static boolean fridge(ServerWorld w, BlockPos pos) {
        BlockPos base = w.getBlockState(pos.down()).isOf(Blocks.SMOOTH_QUARTZ) ? pos.down() : pos;
        if (!w.getBlockState(base.up()).isOf(Blocks.SMOOTH_QUARTZ) || w.getBlockState(base.up(2)).isOf(Blocks.SMOOTH_QUARTZ) || w.getBlockState(base.down()).isOf(Blocks.SMOOTH_QUARTZ))
            return false;
        for (Direction d : Direction.Type.HORIZONTAL)
            for (int k = 1; k <= 2; k++) {
                BlockState n = w.getBlockState(base.offset(d, k));
                if (n.isOf(Blocks.SMOKER) || n.isOf(Blocks.WATER_CAULDRON)) return true;
            }
        return false;
    }

    private static void rummage(ServerWorld w, BlockPos pos, ServerPlayerEntity p) {
        long now = w.getTime();
        w.playSound(null, pos, SoundEvents.BLOCK_COMPOSTER_FILL, SoundCategory.BLOCKS, 1f, 0.8f);
        if (now - TRASH.getOrDefault(pos.asLong(), -100000L) < 20 * 300) {
            p.sendMessage(Text.literal("Тут уже порылись. Только огрызки").formatted(Formatting.GRAY), true);
            return;
        }
        TRASH.put(pos.asLong(), now);
        if (TRASH.size() > 512) TRASH.clear();
        int r = w.random.nextInt(20);
        ItemStack loot = r < 4 ? new ItemStack(ModContent.BANKNOTE) : r < 7 ? new ItemStack(ModContent.COLA) : r < 9 ? new ItemStack(ModContent.AMMO, 3)
                : r == 9 ? new ItemStack(ModContent.BANKNOTE_50) : ItemStack.EMPTY;
        String what = r < 4 ? "мятые $10" : r < 7 ? "нераспечатанную газировку" : r < 9 ? "три патрона (кто их выбросил?!)" : r == 9 ? "$50! Повезло" : null;
        if (what == null) {
            p.sendMessage(Text.literal("Порылся в урне: " + new String[]{"старые газеты", "банановая кожура", "сломанный зонтик", "ничего полезного"}[w.random.nextInt(4)]).formatted(Formatting.GRAY), true);
            return;
        }
        if (!p.getInventory().insertStack(loot)) p.dropItem(loot, false);
        p.sendMessage(Text.literal("Порылся в урне и нашёл " + what).formatted(Formatting.GREEN), true);
    }

    /** Монтировка (бита, нож, мачете) + Shift+ПКМ по банкомату: несколько рывков — и он вскрыт. */
    private static void pryAtm(ServerWorld w, BlockPos pos, ServerPlayerEntity p) {
        int[] st = PRY.computeIfAbsent(p.getUuid(), u -> new int[3]);
        long now = w.getTime();
        int key = (int) pos.asLong();
        if (st[1] != key || now - st[2] > 60) st[0] = 0;
        if (now - st[2] < 6) return;
        st[1] = key;
        st[2] = (int) now;
        st[0]++;
        Vec3d c = Vec3d.ofCenter(pos);
        w.playSound(null, pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.5f, 1.4f + st[0] * 0.08f);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.2, c.z, 6, 0.3, 0.3, 0.3, 0.3);
        if (st[0] == 1) {
            p.sendMessage(Text.literal("Вскрываешь банкомат… (Shift+ПКМ ещё)").formatted(Formatting.GOLD), true);
            if (!p.isCreative() && com.yompix.novograd.gta.Wanted.policeSees(w, p, 30)) com.yompix.novograd.gta.Wanted.add(p, 0, "взлом банкомата", 1);
            for (CitizenEntity cz : w.getEntitiesByClass(CitizenEntity.class, p.getBoundingBox().expand(12), cz -> cz.isAlive() && !cz.hasVehicle())) cz.panic(c);
        } else if (st[0] < 6) p.sendMessage(Text.literal("Вскрываешь банкомат… " + "▮".repeat(st[0]) + "▯".repeat(6 - st[0])).formatted(Formatting.GOLD), true);
        else {
            st[0] = 0;
            Destruction.atmCracked(w, pos, p, false);
        }
    }

    // ------------------------------------------------------------------ расстановка предметов по тротуарам (новые и старые миры)
    static final class Decor extends PersistentState {
        final LongOpenHashSet done = new LongOpenHashSet();
        static final Type<Decor> TYPE = new Type<>(Decor::new, (n, reg) -> {
            Decor d = new Decor();
            for (long l : n.getLongArray("c")) d.done.add(l);
            return d;
        }, null);

        @Override
        public NbtCompound writeNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup reg) {
            n.putLongArray("c", done.toLongArray());
            return n;
        }
    }

    /** Версия расстановки: увеличь, чтобы заново пройтись по уже обставленным чанкам (добавятся только новые места). */
    private static final int DECOR_VERSION = 1;
    private static final LongLinkedOpenHashSet QUEUE = new LongLinkedOpenHashSet();

    private static void decorateTick(MinecraftServer s) {
        ServerWorld w = s.getOverworld();
        long now = w.getTime();
        if (now % 100 == 3 && !TIP_CARS.isEmpty()) // патрульные машины анонимного вызова уезжают
            TIP_CARS.entrySet().removeIf(e -> {
                if (now < e.getValue()) return false;
                if (w.getEntity(e.getKey()) instanceof CarEntity car && car.getPassengerList().isEmpty()) car.discard();
                return true;
            });
        if (QUEUE.isEmpty() || Boolean.getBoolean("novograd.noprops")) return;
        Decor d = w.getPersistentStateManager().getOrCreate(Decor.TYPE, "novograd_decor" + DECOR_VERSION);
        long start = System.nanoTime();
        int n = 0, tries = Math.min(QUEUE.size(), 48);
        while (tries-- > 0 && n < 1 && System.nanoTime() - start < 1_500_000) { // один чанк за тик — без рывков на слабом ноутбуке
            long key = QUEUE.removeFirstLong();
            if (d.done.contains(key)) continue;
            ChunkPos cp = new ChunkPos(key);
            if (!w.isChunkLoaded(cp.x, cp.z)) continue; // выгрузился — вернётся в очередь при загрузке
            // соседи тоже должны быть загружены (не цепляем генерацию) — иначе в конец очереди, попробуем позже
            if (!neighborsLoaded(w, cp)) {
                QUEUE.add(key);
                continue;
            }
            decorate(w, cp);
            d.done.add(key);
            d.markDirty();
            n++;
        }
        if (QUEUE.size() > 20000) QUEUE.clear();
    }

    private static long tickNanos, tickMax, ticks;

    /** Все 8 соседних чанков загружены: тогда чтение блоков у края не грузит чанк с диска (сервер не встаёт). */
    private static boolean neighborsLoaded(ServerWorld w, ChunkPos cp) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if ((dx != 0 || dz != 0) && !w.isChunkLoaded(cp.x + dx, cp.z + dz)) return false;
        return true;
    }

    /** Автотест: сколько в среднем и максимум занимает тик живой улицы (мс), со сбросом. */
    public static String perf() {
        String r = String.format("avg %.3f ms, max %.2f ms over %d ticks", ticks == 0 ? 0 : tickNanos / 1e6 / ticks, tickMax / 1e6, ticks);
        tickNanos = tickMax = ticks = 0;
        return r;
    }

    /** Сколько чанков ждут расстановки (автотест). */
    public static int queued() {
        return QUEUE.size();
    }

    private static double hash(int x, int z, int salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * Тротуар вдоль улиц города: road 5 — у бордюра (фонари, гидранты, конусы), road 6 — у домов (урны, скамейки,
     * автоматы, таксофоны, киоски, тележки, баллоны в промзоне). Подальше от перекрёстков и зебр.
     */
    static void decorate(ServerWorld w, ChunkPos cp) {
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int x = cp.getStartX() + lx, z = cp.getStartZ() + lz;
                int dx = Island.gridDist(x), dz = Island.gridDist(z);
                int road = Math.min(dx, dz);
                if ((road != 5 && road != 6) || Math.max(dx, dz) < 10) continue;
                Island.Zone zn = Island.zone(x, z);
                if (!Island.urban(zn)) continue;
                boolean alongZ = dx < dz; // улица идёт вдоль z (мы у вертикальной линии сетки)
                int t = alongZ ? z : x;
                int m = Math.floorMod(t, 16);
                // в сторону проезжей части
                Direction toRoad = alongZ ? (Math.floorMod(x - 24, 48) < 24 ? Direction.WEST : Direction.EAST)
                        : (Math.floorMod(z - 24, 48) < 24 ? Direction.NORTH : Direction.SOUTH);
                int top = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                BlockPos ground = new BlockPos(x, top, z);
                if (!w.getBlockState(ground).isOf(ModContent.SIDEWALK)) continue;
                BlockPos at = ground.up();
                double hsh = hash(x, z, 77);
                if (road == 5) {
                    if (m == 8) place(w, at, lamp(toRoad), 4);
                    else if (m == 2 && Math.floorMod(t, 48) == 2) place(w, at, Blocks.RED_NETHER_BRICK_WALL.getDefaultState(), 1); // гидрант
                    else if (m == 13 && zn == Island.Zone.INDUSTRIAL && hsh < 0.25) place(w, at, CONE.getDefaultState(), 1);
                } else {
                    if (nearDoor(w, at)) continue;
                    if (m == 4) place(w, at, Blocks.COMPOSTER.getDefaultState(), 1); // урна
                    else if ((m == 11 || m == 12) && zn != Island.Zone.INDUSTRIAL)
                        place(w, at, Blocks.SPRUCE_STAIRS.getDefaultState().with(StairsBlock.FACING, toRoad.getOpposite()).with(StairsBlock.HALF, BlockHalf.BOTTOM), 2); // скамейка
                    else if (m == 14) {
                        long seg = Math.floorDiv(t, 16);
                        double sh = hash((int) seg, alongZ ? x : z, 13);
                        Block b = zn == Island.Zone.INDUSTRIAL ? (sh < 0.35 ? PROPANE : null)
                                : sh < 0.22 ? VENDING : sh < 0.36 ? PAYPHONE : sh < 0.5 ? NEWS_STAND
                                : sh < 0.6 ? (zn == Island.Zone.RIVIERA ? ICECREAM_CART : HOTDOG_CART) : null;
                        if (b != null) place(w, at, b.getDefaultState().with(HorizontalFacingBlock.FACING, toRoad), b == VENDING || b == PAYPHONE ? 3 : 2);
                    }
                }
            }
    }

    private static BlockState[] lamp(Direction toRoad) {
        BlockState pole = ModContent.LAMP_POST.getDefaultState();
        return new BlockState[]{pole, pole, pole, ModContent.STREET_LAMP.getDefaultState().with(HorizontalFacingBlock.FACING, toRoad)};
    }

    private static void place(ServerWorld w, BlockPos at, BlockState st, int clear) {
        place(w, at, new BlockState[]{st}, clear);
    }

    /** Ставим столбик блоков, если над тротуаром свободно на clear блоков (и не мешаем ничему). */
    private static void place(ServerWorld w, BlockPos at, BlockState[] col, int clear) {
        for (int i = 0; i < Math.max(clear, col.length); i++) if (!w.getBlockState(at.up(i)).isAir()) return;
        for (int i = 0; i < col.length; i++) w.setBlockState(at.up(i), col[i], Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
    }

    private static boolean nearDoor(ServerWorld w, BlockPos at) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                BlockState st = w.getBlockState(at.add(dx, 0, dz));
                if (st.getBlock() instanceof DoorBlock || st.isOf(Blocks.IRON_BARS) || st.isOf(ModContent.TRAFFIC_LIGHT)) return true;
            }
        return false;
    }
}
