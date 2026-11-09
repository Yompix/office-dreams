package com.yompix.novograd.world;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.gta.Activities;
import com.yompix.novograd.gta.DriveHooks;
import com.yompix.novograd.gta.FunUtil;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.time.LocalDate;
import java.util.*;

/**
 * Праздники острова. Новый год: ёлка на площади делового центра, подарки по городу, фейерверк в полночь, гирлянды на домах
 * и прохожие в шапках Деда Мороза (гирлянды и шапки рисует клиент). Хэллоуин: тыквы на тротуарах, костюмы прохожих,
 * «Дом с привидениями» на холмах и ночное задание «Ночь страха». День города: салют в 21:00, концерт на площади, флажки-огни.
 * Когда праздник: по настоящей дате (20 дек–10 янв, 25 окт–2 ноя) или по календарю острова (середина зимы, конец осени,
 * середина лета). Всё, что ставится в мир, записано и убирается после праздника. /outdoor holiday newyear|halloween|cityday|off|auto|real|calendar
 */
public final class Holidays {
    private Holidays() {}

    public enum Holiday {
        NONE("—"), NEW_YEAR("Новый год"), HALLOWEEN("Хэллоуин"), CITY_DAY("День города");

        public final String title;

        Holiday(String title) {
            this.title = title;
        }
    }

    // ------------------------------------------------------------------ подарок
    public static Block GIFT;
    public static Item GIFT_ITEM;

    public static class GiftBlock extends Block {
        private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 10, 13);

        public GiftBlock(Settings s) {
            super(s);
        }

        @Override
        protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
            return SHAPE;
        }

        @Override
        protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
            if (world.isClient) return ActionResult.SUCCESS;
            openGift((ServerPlayerEntity) player, pos);
            return ActionResult.CONSUME;
        }
    }

    public static void register() {
        GIFT = Registry.register(Registries.BLOCK, Novograd.id("gift"), new GiftBlock(AbstractBlock.Settings.create().strength(-1f, 3600000f)
                .sounds(BlockSoundGroup.WOOL).nonOpaque().dropsNothing()));
        GIFT_ITEM = Registry.register(Registries.ITEM, Novograd.id("gift"), new BlockItem(GIFT, new Item.Settings()));
    }

    // ------------------------------------------------------------------ когда праздник
    public static String mode(ServerWorld w) {
        return TownState.get(w).storyPlaces.getOrDefault("holiday_mode", "auto");
    }

    public static Holiday realDate() {
        LocalDate d = LocalDate.now();
        int m = d.getMonthValue(), day = d.getDayOfMonth();
        if (m == 12 && day >= 20 || m == 1 && day <= 10) return Holiday.NEW_YEAR;
        if (m == 10 && day >= 25 || m == 11 && day <= 2) return Holiday.HALLOWEEN;
        return Holiday.NONE;
    }

    public static Holiday calendar(ServerWorld w) {
        if (Seasons.real(w)) return Holiday.NONE;
        Seasons.Season s = Seasons.season(w);
        int len = Seasons.len(w), in = Seasons.dayIn(w);
        if (TownState.get(w).storyPlaces.containsKey("season_force")) return Holiday.NONE;
        if (s == Seasons.Season.WINTER && (in == len / 2 || len >= 4 && in == len / 2 - 1)) return Holiday.NEW_YEAR;
        if (s == Seasons.Season.AUTUMN && (in == len - 1 || len >= 6 && in == len - 2)) return Holiday.HALLOWEEN;
        if (s == Seasons.Season.SUMMER && in == len / 2) return Holiday.CITY_DAY;
        return Holiday.NONE;
    }

    public static Holiday current(ServerWorld w) {
        return switch (mode(w)) {
            case "off" -> Holiday.NONE;
            case "newyear" -> Holiday.NEW_YEAR;
            case "halloween" -> Holiday.HALLOWEEN;
            case "cityday" -> Holiday.CITY_DAY;
            case "real" -> realDate();
            case "calendar" -> calendar(w);
            default -> {
                Holiday r = realDate();
                yield r != Holiday.NONE ? r : calendar(w);
            }
        };
    }

    /** «Ближайший праздник» для телефона. */
    public static String nextText(ServerWorld w) {
        Holiday h = current(w);
        if (h != Holiday.NONE) return h.title + " — сегодня!";
        if (Seasons.real(w)) return "по настоящему календарю: Хэллоуин 25 окт–2 ноя, Новый год 20 дек–10 янв";
        int len = Seasons.len(w);
        long day = Seasons.day(w), cycle = len * 4L;
        long pos = (day - 1) % cycle;
        long[][] at = {{len + len - 1, 2}, {2L * len + len / 2 - (len >= 4 ? 1 : 0), 1}, {len / 2, 3}};
        long best = Long.MAX_VALUE;
        int which = 0;
        for (long[] a : at) {
            long d = Math.floorMod(a[0] - pos, cycle);
            if (d == 0) d = cycle;
            if (d < best) {
                best = d;
                which = (int) a[1];
            }
        }
        return Holiday.values()[which].title + " — через " + best + " дн.";
    }

    // ------------------------------------------------------------------ состояние
    private static final Set<Long> GIFTS = new HashSet<>();
    private static final Map<UUID, Fear> FEAR = new HashMap<>();
    private static List<Long> placed;
    /** Украшения прошлого праздника — убираются по мере загрузки чанков. */
    private static final List<Long> OLD = new ArrayList<>();

    private static List<Long> placed(ServerWorld w) {
        if (placed == null) {
            placed = new ArrayList<>();
            String v = TownState.get(w).storyPlaces.getOrDefault("hol_placed", "");
            for (String s : v.split(",")) if (!s.isEmpty()) placed.add(Long.parseLong(s));
            for (String s : TownState.get(w).storyPlaces.getOrDefault("hol_gifts", "").split(",")) if (!s.isEmpty()) GIFTS.add(Long.parseLong(s));
            for (String s : TownState.get(w).storyPlaces.getOrDefault("hol_old", "").split(",")) if (!s.isEmpty()) OLD.add(Long.parseLong(s));
        }
        return placed;
    }

    private static void save(ServerWorld w) {
        StringBuilder sb = new StringBuilder();
        for (long l : placed(w)) sb.append(l).append(',');
        StringBuilder g = new StringBuilder();
        for (long l : GIFTS) g.append(l).append(',');
        StringBuilder o = new StringBuilder();
        for (long l : OLD) o.append(l).append(',');
        TownState ts = TownState.get(w);
        ts.storyPlaces.put("hol_old", o.toString());
        ts.storyPlaces.put("hol_placed", sb.toString());
        ts.storyPlaces.put("hol_gifts", g.toString());
        ts.markDirty();
    }

    /** Поставить блок только в пустоту и запомнить, чтобы убрать после праздника. */
    private static boolean put(ServerWorld w, BlockPos pos, BlockState st) {
        BlockState was = w.getBlockState(pos);
        if (!was.isAir() && !was.isReplaceable()) return false;
        w.setBlockState(pos, st, 2);
        placed(w).add(pos.asLong());
        return true;
    }

    public static void tick(MinecraftServer s) {
        long t = s.getTicks();
        ServerWorld w = s.getOverworld();
        if (t % 10 != 5 || !(w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) return;
        Holiday h = current(w);
        TownState ts = TownState.get(w);
        String built = ts.storyPlaces.getOrDefault("hol_active", "NONE");
        if (!built.equals(h.name())) {
            // праздник сменился: убрать прошлые украшения (по мере загрузки чанков)
            ts.storyPlaces.put("hol_active", h.name());
            ts.storyPlaces.remove("hol_tree");
            ts.storyPlaces.remove("hol_ruin");
            OLD.addAll(placed(w));
            placed(w).clear();
            GIFTS.clear();
            save(w);
            new ArrayList<>(FEAR.keySet()).forEach(u -> {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(u);
                if (p != null) endFear(p, false, "Праздник закончился");
            });
            if (h == Holiday.NONE) for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) Seasons.sendState(p);
            else for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList())
                Life.message(p, "Новоград-24", switch (h) {
                    case NEW_YEAR -> "С Новым годом! На площади делового центра — ёлка, по городу спрятаны подарки, в полночь — салют.";
                    case HALLOWEEN -> "Хэллоуин! Тыквы на улицах, а на холмах ночью оживает «Дом с привидениями» — смельчакам награда.";
                    case CITY_DAY -> "День города! Концерт на площади делового центра, ярмарка и салют в 21:00.";
                    default -> "";
                });
        }
        cleanup(w);
        if (h == Holiday.NONE) return;
        long tod = w.getTimeOfDay() % 24000;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (p.getServerWorld() != w || p.isSpectator()) continue;
            switch (h) {
                case NEW_YEAR -> {
                    tree(w, p);
                    if (t % 100 == 5) gifts(w, p);
                    if (tod >= 18000 && tod < 19400 && t % 20 == 5) fireworks(w, p, 2);
                }
                case HALLOWEEN -> {
                    if (t % 100 == 5) pumpkins(w, p);
                    ruin(w, p);
                    fear(w, p);
                }
                case CITY_DAY -> {
                    concert(w, p, t);
                    if (tod >= 15000 && tod < 16400 && t % 20 == 5) fireworks(w, p, 3);
                }
                default -> {}
            }
        }
        if (t % 200 == 5) save(w);
    }

    private static void cleanup(ServerWorld w) {
        placed(w);
        if (OLD.isEmpty()) return;
        int done = 0;
        var it = OLD.iterator();
        while (it.hasNext() && done < 400) {
            BlockPos pos = BlockPos.fromLong(it.next());
            if (!w.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (!w.getBlockState(pos).isAir()) w.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
            it.remove();
            done++;
        }
        if (done > 0) save(w);
    }

    // ------------------------------------------------------------------ Новый год
    private static long lastTreeTry = -1000;

    private static void tree(ServerWorld w, ServerPlayerEntity p) {
        TownState ts = TownState.get(w);
        if (ts.storyPlaces.containsKey("hol_tree")) return;
        double[] v = Island.place("downtown_plaza");
        if (v == null || p.squaredDistanceTo(v[0], v[1], v[2]) > 160 * 160) return;
        long now = w.getTime();
        if (now - lastTreeTry < 200 && now >= lastTreeTry) return;
        lastTreeTry = now;
        int px = (int) Math.floor(v[0]), pz = (int) Math.floor(v[2]);
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) w.getChunk((px >> 4) + dx, (pz >> 4) + dz);
        // открытое место на площади: круг радиусом 5 без крыш, дорог и столбов
        int cx = Integer.MIN_VALUE, cz = 0;
        search:
        for (int r = 0; r <= 60; r += 2)
            for (int dx = -r; dx <= r; dx += 2)
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    if (open(w, px + dx, pz + dz)) {
                        cx = px + dx;
                        cz = pz + dz;
                        break search;
                    }
                }
        if (cx == Integer.MIN_VALUE) return; // на площади тесно — попробуем позже (на крыши ёлку не ставим)
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        BlockState leaves = Blocks.SPRUCE_LEAVES.getDefaultState().with(net.minecraft.block.LeavesBlock.PERSISTENT, true);
        BlockState[] balls = {Blocks.RED_WOOL.getDefaultState(), Blocks.YELLOW_WOOL.getDefaultState(), Blocks.LIGHT_BLUE_WOOL.getDefaultState(),
                Blocks.GLOWSTONE.getDefaultState(), Blocks.SEA_LANTERN.getDefaultState()};
        int H = 17;
        for (int dy = 0; dy < H; dy++) put(w, new BlockPos(cx, y + dy, cz), Blocks.SPRUCE_LOG.getDefaultState());
        var rnd = w.getRandom();
        for (int dy = 2; dy <= H; dy++) {
            double r = (H + 1 - dy) * 0.42 + (dy % 3 == 2 ? 0.8 : 0);
            int ir = (int) Math.ceil(r);
            for (int dx = -ir; dx <= ir; dx++)
                for (int dz = -ir; dz <= ir; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > r || dx == 0 && dz == 0 && dy < H) continue;
                    boolean shell = d > r - 1.1;
                    BlockState st = shell && rnd.nextFloat() < 0.13f ? balls[rnd.nextInt(balls.length)] : leaves;
                    put(w, new BlockPos(cx + dx, y + dy, cz + dz), st);
                }
        }
        put(w, new BlockPos(cx, y + H + 1, cz), Blocks.GOLD_BLOCK.getDefaultState());
        put(w, new BlockPos(cx, y + H + 2, cz), Blocks.GLOWSTONE.getDefaultState());
        // подарки под ёлкой
        int[][] g = {{3, 0}, {-3, 1}, {1, 3}, {-2, -3}, {4, -2}};
        for (int[] o : g) {
            BlockPos gp = new BlockPos(cx + o[0] + (o[0] > 0 ? 3 : -3), w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx + o[0] + (o[0] > 0 ? 3 : -3), cz + o[1]), cz + o[1]);
            if (put(w, gp, GIFT.getDefaultState())) GIFTS.add(gp.asLong());
        }
        ts.storyPlaces.put("hol_tree", cx + "," + y + "," + cz);
        save(w);
    }

    private static boolean open(ServerWorld w, int x, int z) {
        if (Island.road(x, z)) return false;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10) continue;
                if (w.getTopY(Heightmap.Type.MOTION_BLOCKING, x + dx, z + dz) > Island.G + 2) return false;
            }
        return true;
    }

    /** Держим вокруг игрока до 5 спрятанных подарков в городе (во дворах, у стен, на тротуарах). */
    private static void gifts(ServerWorld w, ServerPlayerEntity p) {
        if (!Island.urban(Island.zone((int) p.getX(), (int) p.getZ()))) return;
        int near = 0;
        for (long l : GIFTS) {
            BlockPos b = BlockPos.fromLong(l);
            if (b.getSquaredDistance(p.getPos()) < 90 * 90) near++;
        }
        if (near >= 5) return;
        var r = w.getRandom();
        for (int tries = 0; tries < 6; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = 20 + r.nextDouble() * 45;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4) || Island.road(x, z) || !Island.urban(Island.zone(x, z))) continue;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y > Island.G + 3) continue; // не на крышах
            BlockPos pos = new BlockPos(x, y, z);
            if (!w.getBlockState(pos.down()).isSolidBlock(w, pos.down())) continue;
            if (put(w, pos, GIFT.getDefaultState())) {
                GIFTS.add(pos.asLong());
                return;
            }
        }
    }

    static void openGift(ServerPlayerEntity p, BlockPos pos) {
        ServerWorld w = p.getServerWorld();
        w.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        placed(w).remove((Long) pos.asLong());
        GIFTS.remove(pos.asLong());
        LifeData d = Life.get(p);
        int n = d.skills.merge("x_gifts", 1, Integer::sum);
        var r = w.getRandom();
        w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.1);
        w.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 1.6f);
        int kind = r.nextInt(10);
        if (kind < 6) Life.earn(p, 50 + r.nextInt(10) * 25, "подарок №" + n);
        else if (kind < 8) {
            ItemStack fw = rocket(r, 2);
            fw.setCount(4);
            if (!p.getInventory().insertStack(fw)) p.dropItem(fw, false);
            p.sendMessage(Text.literal("Подарок №" + n + ": фейерверки!").formatted(Formatting.GOLD), true);
        } else {
            ItemStack cake = new ItemStack(Items.PUMPKIN_PIE, 3);
            if (!p.getInventory().insertStack(cake)) p.dropItem(cake, false);
            Life.earn(p, 300, "подарок №" + n + " — праздничная премия");
        }
        save(w);
    }

    private static ItemStack rocket(net.minecraft.util.math.random.Random r, int flight) {
        int[] palette = {0xFF3B3B, 0xFFD23B, 0x3BFF6A, 0x3BC8FF, 0xC83BFF, 0xFFFFFF, 0xFF8A1E};
        List<FireworkExplosionComponent> ex = new ArrayList<>();
        int n = 1 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            FireworkExplosionComponent.Type type = FireworkExplosionComponent.Type.values()[r.nextInt(FireworkExplosionComponent.Type.values().length)];
            ex.add(new FireworkExplosionComponent(type, IntList.of(palette[r.nextInt(palette.length)], palette[r.nextInt(palette.length)]),
                    IntList.of(palette[r.nextInt(palette.length)]), r.nextBoolean(), r.nextBoolean()));
        }
        ItemStack s = new ItemStack(Items.FIREWORK_ROCKET);
        s.set(DataComponentTypes.FIREWORKS, new FireworksComponent(flight, ex));
        return s;
    }

    private static void fireworks(ServerWorld w, ServerPlayerEntity p, int perSecond) {
        if (!Island.urban(Island.zone((int) p.getX(), (int) p.getZ())) && Island.zone((int) p.getX(), (int) p.getZ()) != Island.Zone.BEACH) return;
        var r = w.getRandom();
        for (int i = 0; i < perSecond; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 25 + r.nextDouble() * 40;
            double x = p.getX() + Math.cos(a) * d, z = p.getZ() + Math.sin(a) * d;
            if (!w.isChunkLoaded((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4)) continue;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            w.spawnEntity(new FireworkRocketEntity(w, x, y + 1, z, rocket(r, 1 + r.nextInt(2))));
        }
    }

    // ------------------------------------------------------------------ Хэллоуин
    private static void pumpkins(ServerWorld w, ServerPlayerEntity p) {
        if (!Island.urban(Island.zone((int) p.getX(), (int) p.getZ()))) return;
        int near = 0;
        for (long l : placed(w)) {
            BlockPos b = BlockPos.fromLong(l);
            if (b.getSquaredDistance(p.getPos()) < 60 * 60 && w.getBlockState(b).isOf(Blocks.JACK_O_LANTERN)) near++;
        }
        if (near >= 14) return;
        var r = w.getRandom();
        for (int tries = 0; tries < 8; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = 12 + r.nextDouble() * 40;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
            if (!w.isChunkLoaded(x >> 4, z >> 4) || Island.road(x, z) || !Island.urban(Island.zone(x, z))) continue;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y > Island.G + 3) continue;
            BlockPos pos = new BlockPos(x, y, z);
            if (!w.getBlockState(pos.down()).isSolidBlock(w, pos.down())) continue;
            put(w, pos, Blocks.JACK_O_LANTERN.getDefaultState().with(net.minecraft.block.CarvedPumpkinBlock.FACING,
                    net.minecraft.util.math.Direction.fromHorizontal(r.nextInt(4))));
        }
    }

    /** «Дом с привидениями» на холмах: развалины из мшистого камня, паутина, тыквы, фонари душ. */
    private static void ruin(ServerWorld w, ServerPlayerEntity p) {
        Vec3d door = ruinDoor(w);
        if (door != null) {
            if (p.squaredDistanceTo(door) < 64 * 64) {
                long tod = w.getTimeOfDay() % 24000;
                boolean night = tod >= 13000 && tod < 23000;
                if (p.age % 40 < 20)
                    FunUtil.marker(w, door, "nv_fear", new ItemStack(Items.JACK_O_LANTERN),
                            Text.literal(night ? "«Ночь страха» — войди, если смелый" : "Дом с привидениями · приходи ночью").formatted(Formatting.GOLD), true);
                if (p.age % 10 == 0)
                    w.spawnParticles(ParticleTypes.SOUL, door.x + (w.getRandom().nextDouble() - 0.5) * 8, door.y + 1 + w.getRandom().nextDouble() * 3, door.z - 4 + (w.getRandom().nextDouble() - 0.5) * 8, 1, 0, 0.05, 0, 0.01);
                DriveHooks.walkIn(p, "out_fear", door, 1.8, () -> {
                    if (night) startFear(p, door);
                    else p.sendMessage(Text.literal("Днём тут тихо. Возвращайся после заката.").formatted(Formatting.GRAY), true);
                });
            }
            return;
        }
        double[] v = Island.place("hill_top");
        if (v == null || p.squaredDistanceTo(v[0], v[1], v[2]) > 140 * 140) return;
        int cx = (int) Math.floor(v[0]) + 14, cz = (int) Math.floor(v[2]);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.getChunk((cx >> 4) + dx, (cz >> 4) + dz);
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        var r = w.getRandom();
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                int gy = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx + dx, cz + dz);
                if (!edge) {
                    if (r.nextFloat() < 0.12f) put(w, new BlockPos(cx + dx, gy, cz + dz), Blocks.COBWEB.getDefaultState());
                    continue;
                }
                if (dz == 4 && Math.abs(dx) <= 1) continue; // проём двери
                int h = 2 + r.nextInt(4);
                for (int dy = 0; dy < h; dy++)
                    put(w, new BlockPos(cx + dx, gy + dy, cz + dz), (r.nextFloat() < 0.5f ? Blocks.MOSSY_COBBLESTONE : Blocks.CRACKED_STONE_BRICKS).getDefaultState());
            }
        put(w, new BlockPos(cx - 2, y + 5, cz + 4), Blocks.SOUL_LANTERN.getDefaultState());
        put(w, new BlockPos(cx + 2, y + 5, cz + 4), Blocks.SOUL_LANTERN.getDefaultState());
        put(w, new BlockPos(cx - 2, y, cz + 6), Blocks.JACK_O_LANTERN.getDefaultState());
        put(w, new BlockPos(cx + 2, y, cz + 6), Blocks.JACK_O_LANTERN.getDefaultState());
        Vec3d d = new Vec3d(cx + 0.5, y, cz + 6.5);
        TownState.get(w).storyPlaces.put("hol_ruin", d.x + "," + d.y + "," + d.z);
        save(w);
    }

    private static Vec3d ruinDoor(ServerWorld w) {
        String v = TownState.get(w).storyPlaces.get("hol_ruin");
        if (v == null) return null;
        String[] a = v.split(",");
        return new Vec3d(Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]));
    }

    private static final class Fear {
        Vec3d at;
        int wave, ticks;
        final Set<UUID> mobs = new HashSet<>();
    }

    private static void startFear(ServerPlayerEntity p, Vec3d at) {
        if (FEAR.containsKey(p.getUuid())) return;
        Fear f = new Fear();
        f.at = at;
        FEAR.put(p.getUuid(), f);
        Activities.title(p, "НОЧЬ СТРАХА", "Продержись: три волны мертвецов");
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.5f, 0.7f);
        NbtCompound n = new NbtCompound();
        n.putDouble("x", at.x);
        n.putDouble("z", at.z);
        n.putString("label", "Дом с привидениями");
        LifeNet.send(p, "act_target", n);
    }

    private static void fear(ServerWorld w, ServerPlayerEntity p) {
        Fear f = FEAR.get(p.getUuid());
        if (f == null) return;
        f.ticks += 10;
        if (p.isDead() || p.squaredDistanceTo(f.at) > 110 * 110) {
            endFear(p, false, p.isDead() ? "Мертвецы победили" : "Сбежал из дома с привидениями");
            return;
        }
        int left = 300 - f.ticks / 20;
        if (f.ticks % 20 == 0) {
            NbtCompound n = new NbtCompound();
            n.putInt("sec", left);
            LifeNet.send(p, "act_timer", n);
        }
        if (left <= 0) {
            endFear(p, false, "Рассвет застал тебя в доме");
            return;
        }
        f.mobs.removeIf(u -> !(w.getEntity(u) instanceof ZombieEntity z) || !z.isAlive());
        if (!f.mobs.isEmpty()) {
            for (UUID u : f.mobs) if (w.getEntity(u) instanceof ZombieEntity z && z.getTarget() == null) z.setTarget(p);
            return;
        }
        if (f.wave >= 3) {
            endFear(p, true, "");
            return;
        }
        f.wave++;
        int count = 2 + f.wave * 2;
        var r = w.getRandom();
        for (int i = 0; i < count; i++) {
            ZombieEntity z = EntityType.ZOMBIE.create(w);
            if (z == null) continue;
            double a = r.nextDouble() * Math.PI * 2, d = 9 + r.nextDouble() * 8;
            double x = f.at.x + Math.cos(a) * d, zz = f.at.z + Math.sin(a) * d;
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(zz));
            z.refreshPositionAndAngles(x, y, zz, r.nextFloat() * 360, 0);
            z.equipStack(EquipmentSlot.HEAD, new ItemStack(r.nextBoolean() ? Items.CARVED_PUMPKIN : Items.JACK_O_LANTERN));
            z.setEquipmentDropChance(EquipmentSlot.HEAD, 0);
            z.setCustomName(Text.literal(f.wave == 3 && i == 0 ? "Главарь мертвецов" : "Мертвец").formatted(Formatting.DARK_RED));
            if (f.wave == 3 && i == 0) {
                var hp = z.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
                if (hp != null) hp.setBaseValue(60);
                z.setHealth(60);
                z.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            }
            z.addCommandTag("nv_fear");
            z.setPersistent();
            z.setTarget(p);
            w.spawnEntity(z);
            w.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, zz, 12, 0.3, 0.6, 0.3, 0.02);
            f.mobs.add(z.getUuid());
        }
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_AMBIENT, SoundCategory.HOSTILE, 1f, 0.6f);
        p.sendMessage(Text.literal("Волна " + f.wave + " из 3: " + count + " мертвецов").formatted(Formatting.GOLD), true);
    }

    static void endFear(ServerPlayerEntity p, boolean win, String why) {
        Fear f = FEAR.remove(p.getUuid());
        if (f == null) return;
        ServerWorld w = p.getServerWorld();
        for (UUID u : f.mobs) if (w.getEntity(u) != null) w.getEntity(u).discard();
        LifeNet.send(p, "act_target", new NbtCompound());
        NbtCompound t = new NbtCompound();
        t.putInt("sec", -1);
        LifeNet.send(p, "act_timer", t);
        if (win) {
            Activities.title(p, "НОЧЬ СТРАХА ПРОЙДЕНА", "+$2500");
            Life.earn(p, 2500, "«Ночь страха»");
            Life.get(p).skills.merge("x_fear", 1, Integer::sum);
        } else Activities.title(p, "НОЧЬ СТРАХА ПРОВАЛЕНА", why);
    }

    /** Новый мир в той же игре — состояние читается заново. */
    public static void reset() {
        placed = null;
        OLD.clear();
        GIFTS.clear();
        FEAR.clear();
    }

    public static void onLeave(ServerPlayerEntity p) {
        Fear f = FEAR.remove(p.getUuid());
        if (f != null) for (UUID u : f.mobs) if (p.getServerWorld().getEntity(u) != null) p.getServerWorld().getEntity(u).discard();
    }

    // ------------------------------------------------------------------ День города
    private static void concert(ServerWorld w, ServerPlayerEntity p, long t) {
        double[] v = Island.place("downtown_plaza");
        if (v == null) return;
        Vec3d stage = new Vec3d(v[0], v[1], v[2]);
        if (p.squaredDistanceTo(stage) > 70 * 70) return;
        if (p.age % 40 < 20)
            FunUtil.marker(w, stage.add(0, 2.5, 0), "nv_cityday", new ItemStack(Items.FIREWORK_ROCKET),
                    Text.literal("День города · концерт и ярмарка").formatted(Formatting.LIGHT_PURPLE), true);
        if (t % 20 == 5) w.spawnParticles(ParticleTypes.NOTE, stage.x, stage.y + 2.5, stage.z, 4, 2.5, 1, 2.5, 1);
        if (t % 200 == 5)
            w.playSound(null, stage.x, stage.y + 1, stage.z, w.getRandom().nextBoolean() ? com.yompix.novograd.gta.StreetPerformers.GUITAR
                    : com.yompix.novograd.gta.StreetPerformers.BEAT, SoundCategory.RECORDS, 2.2f, 1f);
        DriveHooks.walkIn(p, "out_cityday", stage, 3, () -> {
            LifeData d = Life.get(p);
            String key = "cityday_" + Seasons.day(w);
            if (d.flags.add(key)) {
                d.mood = Math.min(100, d.mood + 15);
                Life.earn(p, 300, "День города: премия от мэрии");
            } else p.sendMessage(Text.literal("С праздником, Новоград!").formatted(Formatting.LIGHT_PURPLE), true);
        });
    }

    public static String debug(ServerPlayerEntity p, String[] a) {
        ServerWorld w = p.getServerWorld();
        if (a.length > 1) {
            String m = switch (a[1]) {
                case "ny", "newyear", "новый" -> "newyear";
                case "hw", "halloween" -> "halloween";
                case "city", "cityday" -> "cityday";
                case "off", "auto", "real", "calendar" -> a[1];
                case "fear" -> {
                    Vec3d d = ruinDoor(w);
                    if (d != null) startFear(p, d);
                    yield null;
                }
                case "fireworks" -> {
                    fireworks(w, p, 6);
                    yield null;
                }
                default -> "";
            };
            if (m == null) return "Ок";
            if (m.isEmpty()) return "holiday newyear|halloween|cityday|off|auto|real|calendar|fear|fireworks";
            TownState.get(w).storyPlaces.put("holiday_mode", m);
            TownState.get(w).markDirty();
        }
        return "Праздник: " + current(w).title + " (режим " + mode(w) + "), подарков в городе " + GIFTS.size() + ", украшений " + placed(w).size();
    }
}
