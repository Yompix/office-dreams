package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.WildAnimalEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.world.Island;
import com.yompix.novograd.world.IslandChunkGenerator;
import com.yompix.novograd.world.Seasons;
import com.yompix.novograd.world.TownState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Охота за городом: в лесах, горах и Пустоши бродит дичь (олень и кабан — свои модели, волки, лисы, зайцы, горные козлы).
 * Лицензия и охотничья винтовка — в домике «Берлога» у деревни Сосновка; там же скупщик берёт шкуры и мясо за чистые.
 * Заповедник у озера Ясное, Ривьера и город — охота запрещена; весной закрыт сезон на оленя; без лицензии — браконьерство:
 * увидит полиция или егерь — звезда. Скупщик берёт «левую» добычу вполцены грязными.
 */
public final class Hunting {
    private Hunting() {}

    public static final String TAG = "nv_wild";
    /** Где домик: подбирается при первом приближении (ровная поляна у дороги к Сосновке), хранится в TownState. */
    private static final int LODGE_X = 1196, LODGE_Z = -36;
    private static final Set<UUID> SPAWNED = new HashSet<>();

    /** Цены скупщика (чистыми). */
    public static final Map<Item, Integer> PRICES = new LinkedHashMap<>();

    static {
        PRICES.put(Outdoors.PELT_DEER, 140);
        PRICES.put(Outdoors.VENISON, 45);
        PRICES.put(Outdoors.PELT_BOAR, 110);
        PRICES.put(Outdoors.BOAR_MEAT, 55);
        PRICES.put(Outdoors.PELT_WOLF, 180);
        PRICES.put(Outdoors.PELT_FOX, 130);
        PRICES.put(Outdoors.PELT_RABBIT, 30);
        PRICES.put(Outdoors.HORN, 120);
    }

    public static final int LICENSE_PRICE = 400, RIFLE_PRICE = 2500, AMMO_PRICE = 80, ROD_PRICE = 35;

    // ------------------------------------------------------------------ где можно
    public enum Region { CITY, FOREST, MOUNTAINS, DESERT, RESERVE }

    public static Region region(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        Island.Zone zn = Island.zone(ix, iz);
        if (Math.hypot((x - 1320) / 1.3, z - 760) < 300) return Region.RESERVE; // заповедник «Ясное»
        if (zn != Island.Zone.COUNTRY) return Region.CITY;
        if (x < -840) return Region.RESERVE; // природа Ривьеры — охраняется
        if (z < -1050) return Region.MOUNTAINS;
        if (z > 1050) return Region.DESERT;
        return Region.FOREST;
    }

    public static String regionName(Region r) {
        return switch (r) {
            case CITY -> "город";
            case FOREST -> "леса и поля — охота по лицензии";
            case MOUNTAINS -> "Седые горы — охота по лицензии";
            case DESERT -> "Пустошь — охота по лицензии";
            case RESERVE -> "заповедник — охота запрещена";
        };
    }

    public static boolean licensed(LifeData d) {
        return d.flags.contains("hunt_license");
    }

    // ------------------------------------------------------------------ тик
    static void tick(MinecraftServer s) {
        long t = s.getTicks();
        if (t % 20 != 7) return;
        ServerWorld w = s.getOverworld();
        if (!(w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) return;
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || p.getServerWorld() != w) continue;
            shops(w, p);
            if (t % 40 == 7 && !p.isDead()) spawnAround(w, p);
        }
        if (t % 200 == 7) cleanup(w);
    }

    /** Метки лавок: домик охотника, рыбные лавки (порт, озеро, марина Ривьеры). */
    private static void shops(ServerWorld w, ServerPlayerEntity p) {
        Vec3d lodge = lodge(w, p);
        if (lodge != null && p.squaredDistanceTo(lodge) < 48 * 48) {
            if (p.age % 40 < 20)
                FunUtil.marker(w, lodge, "nv_lodge", new ItemStack(Outdoors.PELT_DEER), Text.literal("🦌 Охотничий домик «Берлога»").formatted(Formatting.GOLD), true);
            DriveHooks.walkIn(p, "out_lodge", lodge, 1.8, () -> open(p, "hunt"));
        }
        for (String[] f : Fishing.SHOPS) {
            Vec3d at = com.yompix.novograd.story.Story.place(w, f[0]);
            if (at == null || p.squaredDistanceTo(at) > 48 * 48) continue;
            if (p.age % 40 < 20)
                FunUtil.marker(w, at, "nv_fish_" + f[0], new ItemStack(Outdoors.PIKE), Text.literal("🎣 " + f[1]).formatted(Formatting.AQUA), true);
            DriveHooks.walkIn(p, "out_fish_" + f[0], at, 1.8, () -> open(p, "fish"));
        }
    }

    static void open(ServerPlayerEntity p, String shop) {
        Outdoors.sendState(p);
        NbtCompound n = new NbtCompound();
        n.putString("shop", shop);
        LifeNet.send(p, "out_open", n);
    }

    /** Домик охотника: строится, когда игрок впервые подъезжает ближе 120 блоков. */
    public static Vec3d lodge(ServerWorld w, ServerPlayerEntity near) {
        TownState ts = TownState.get(w);
        String v = ts.storyPlaces.get("out_lodge");
        if (v != null) {
            String[] a = v.split(",");
            return new Vec3d(Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]));
        }
        if (near == null || near.squaredDistanceTo(LODGE_X, near.getY(), LODGE_Z) > 120 * 120) return null;
        // ровная поляна без дороги рядом с точкой
        int bx = LODGE_X, bz = LODGE_Z;
        search:
        for (int r = 0; r <= 40; r += 4)
            for (int dx = -r; dx <= r; dx += 4)
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    if (flat(LODGE_X + dx, LODGE_Z + dz)) {
                        bx = LODGE_X + dx;
                        bz = LODGE_Z + dz;
                        break search;
                    }
                }
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) w.getChunk((bx >> 4) + cx, (bz >> 4) + cz);
        int y = buildLodge(w, bx, bz);
        Vec3d door = new Vec3d(bx + 0.5, y + 1, bz + 5.5);
        ts.storyPlaces.put("out_lodge", door.x + "," + door.y + "," + door.z);
        ts.markDirty();
        return door;
    }

    private static boolean flat(int x, int z) {
        int s0 = Island.surface(x, z);
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -4; dz <= 8; dz++) {
                int xx = x + dx, zz = z + dz;
                if (Island.zone(xx, zz) != Island.Zone.COUNTRY || Island.road(xx, zz) || Math.abs(Island.surface(xx, zz) - s0) > 1) return false;
            }
        return true;
    }

    /** Бревенчатый домик 7×6 с навесом, бочками и рогами над дверью; дверь — на юг (+z). */
    private static int buildLodge(ServerWorld w, int cx, int cz) {
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
        BlockState log = Blocks.SPRUCE_LOG.getDefaultState(), plank = Blocks.SPRUCE_PLANKS.getDefaultState(), air = Blocks.AIR.getDefaultState();
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 6; dz++) {
                for (int dy = 1; dy <= 9; dy++) w.setBlockState(new BlockPos(cx + dx, y + dy, cz + dz), air, 2);
                w.setBlockState(new BlockPos(cx + dx, y, cz + dz), dz <= 3 && Math.abs(dx) <= 3 ? plank : Blocks.COARSE_DIRT.getDefaultState(), 2);
            }
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                if (!edge) continue;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                for (int dy = 1; dy <= 3; dy++) {
                    boolean door = dz == 3 && dx == 0 && dy <= 2;
                    boolean window = !corner && dy == 2 && (Math.abs(dx) == 3 && dz == 0 || dz == -3 && dx == 0);
                    BlockState st = door ? air : window ? Blocks.GLASS_PANE.getDefaultState() : corner ? log : plank;
                    w.setBlockState(new BlockPos(cx + dx, y + dy, cz + dz), st, 2);
                }
            }
        // крыша двускатная из ступенек: от карниза (|dx| = 4) к коньку, внутри — потолок из досок
        for (int dz = -4; dz <= 5; dz++)
            for (int dx = -4; dx <= 4; dx++) {
                int ax = Math.abs(dx), level = 4 - ax; // 0 — карниз
                BlockPos at = new BlockPos(cx + dx, y + 4 + level, cz + dz);
                BlockState st = dx == 0 ? Blocks.DARK_OAK_SLAB.getDefaultState()
                        : Blocks.DARK_OAK_STAIRS.getDefaultState().with(net.minecraft.block.StairsBlock.FACING,
                        dx < 0 ? net.minecraft.util.math.Direction.EAST : net.minecraft.util.math.Direction.WEST);
                w.setBlockState(at, st, 2);
                // фронтоны: доски под скатом на торцах
                if (dz == -3 || dz == 3) for (int yy = y + 4; yy < y + 4 + level; yy++) w.setBlockState(new BlockPos(cx + dx, yy, cz + dz), plank, 2);
            }
        // навес над крыльцом на столбах, бочки, сено, рога
        w.setBlockState(new BlockPos(cx - 3, y + 1, cz + 5), Blocks.SPRUCE_FENCE.getDefaultState(), 3);
        w.setBlockState(new BlockPos(cx + 3, y + 1, cz + 5), Blocks.SPRUCE_FENCE.getDefaultState(), 3);
        for (int yy = 2; yy <= 4; yy++) {
            w.setBlockState(new BlockPos(cx - 3, y + yy, cz + 5), Blocks.SPRUCE_FENCE.getDefaultState(), 3);
            w.setBlockState(new BlockPos(cx + 3, y + yy, cz + 5), Blocks.SPRUCE_FENCE.getDefaultState(), 3);
        }
        w.setBlockState(new BlockPos(cx - 2, y + 1, cz + 4), Blocks.BARREL.getDefaultState(), 2);
        w.setBlockState(new BlockPos(cx + 2, y + 1, cz + 4), Blocks.HAY_BLOCK.getDefaultState(), 2);
        w.setBlockState(new BlockPos(cx - 2, y + 1, cz - 2), Blocks.BARREL.getDefaultState(), 2);
        w.setBlockState(new BlockPos(cx + 2, y + 1, cz - 2), Blocks.CRAFTING_TABLE.getDefaultState(), 2);
        w.setBlockState(new BlockPos(cx, y + 1, cz - 2), Blocks.CAMPFIRE.getDefaultState().with(net.minecraft.block.CampfireBlock.LIT, false), 2);
        w.setBlockState(new BlockPos(cx - 2, y + 2, cz + 4), Blocks.LANTERN.getDefaultState(), 2);
        w.setBlockState(new BlockPos(cx - 1, y + 3, cz + 4), Blocks.SKELETON_WALL_SKULL.getDefaultState().with(net.minecraft.block.WallSkullBlock.FACING, net.minecraft.util.math.Direction.SOUTH), 2);
        w.setBlockState(new BlockPos(cx + 1, y + 3, cz + 4), Blocks.SKELETON_WALL_SKULL.getDefaultState().with(net.minecraft.block.WallSkullBlock.FACING, net.minecraft.util.math.Direction.SOUTH), 2);
        return y;
    }

    // ------------------------------------------------------------------ дичь
    private static void spawnAround(ServerWorld w, ServerPlayerEntity p) {
        Region here = region(p.getX(), p.getZ());
        if (here == Region.CITY) return;
        var rnd = w.getRandom();
        int cap = Runtime.getRuntime().maxMemory() < 3_500L * 1048576 ? 4 : 7;
        Box box = p.getBoundingBox().expand(96, 48, 96);
        int have = w.getEntitiesByClass(LivingEntity.class, box, e -> e.getCommandTags().contains(TAG) && e.isAlive()).size();
        if (have >= cap) return;
        double ang = rnd.nextDouble() * Math.PI * 2, dist = 36 + rnd.nextDouble() * 34;
        int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist), z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
        Region r = region(x, z);
        if (r == Region.CITY || Island.road(x, z) || !w.isChunkLoaded(x >> 4, z >> 4)) return;
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockState ground = w.getBlockState(new BlockPos(x, y - 1, z));
        if (!ground.getFluidState().isEmpty() || ground.isIn(net.minecraft.registry.tag.BlockTags.LEAVES) || ground.isAir()) return;
        boolean night = w.isNight();
        String kind = pick(rnd, r, night);
        int group = switch (kind) {
            case "deer" -> 1 + rnd.nextInt(3);
            case "boar", "wolf", "goat" -> 1 + rnd.nextInt(2);
            default -> 1;
        };
        for (int i = 0; i < group; i++) {
            Entity e = create(w, kind);
            if (e == null) return;
            double ox = x + 0.5 + (i == 0 ? 0 : rnd.nextDouble() * 4 - 2), oz = z + 0.5 + (i == 0 ? 0 : rnd.nextDouble() * 4 - 2);
            int oy = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(ox), (int) Math.floor(oz));
            e.refreshPositionAndAngles(ox, oy, oz, rnd.nextFloat() * 360, 0);
            e.addCommandTag(TAG);
            if (e instanceof MobEntity m) m.initialize(w, w.getLocalDifficulty(m.getBlockPos()), SpawnReason.EVENT, null);
            w.spawnEntity(e);
            SPAWNED.add(e.getUuid());
        }
    }

    private static String pick(net.minecraft.util.math.random.Random rnd, Region r, boolean night) {
        String[] k;
        int[] wt;
        switch (r) {
            case MOUNTAINS -> {
                k = new String[]{"goat", "deer", "wolf", "fox"};
                wt = new int[]{30, 30, night ? 30 : 15, 15};
            }
            case DESERT -> {
                k = new String[]{"rabbit", "fox", "wolf", "boar"};
                wt = new int[]{45, 30, night ? 20 : 8, 10};
            }
            default -> {
                k = new String[]{"deer", "boar", "fox", "rabbit", "wolf"};
                wt = new int[]{34, night ? 30 : 22, 14, 22, night ? 14 : 5};
            }
        }
        int sum = 0;
        for (int v : wt) sum += v;
        int roll = rnd.nextInt(sum);
        for (int i = 0; i < k.length; i++) {
            roll -= wt[i];
            if (roll < 0) return k[i];
        }
        return k[0];
    }

    static Entity create(ServerWorld w, String kind) {
        return switch (kind) {
            case "deer" -> Outdoors.DEER.create(w);
            case "boar" -> Outdoors.BOAR.create(w);
            case "wolf" -> EntityType.WOLF.create(w);
            case "fox" -> EntityType.FOX.create(w);
            case "rabbit" -> EntityType.RABBIT.create(w);
            case "goat" -> EntityType.GOAT.create(w);
            default -> null;
        };
    }

    /** Дичь, рядом с которой давно никого (140+ блоков), исчезает; приручённых не трогаем. */
    private static void cleanup(ServerWorld w) {
        var it = SPAWNED.iterator();
        while (it.hasNext()) {
            Entity e = w.getEntity(it.next());
            if (e == null || !e.isAlive()) {
                it.remove();
                continue;
            }
            if (e instanceof TameableEntity t && t.isTamed()) {
                e.removeCommandTag(TAG);
                it.remove();
                continue;
            }
            if (w.getClosestPlayer(e, 140) == null) {
                e.discard();
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ добыча и закон
    static void afterDeath(LivingEntity e, DamageSource src) {
        if (!e.getCommandTags().contains(TAG) && !(e instanceof WildAnimalEntity)) return;
        if (!(src.getAttacker() instanceof ServerPlayerEntity p) || !(e.getWorld() instanceof ServerWorld w)) return;
        var rnd = w.getRandom();
        java.util.List<ItemStack> loot = new java.util.ArrayList<>();
        String what;
        boolean deer = false;
        if (e instanceof WildAnimalEntity a) {
            if (a.kind() == WildAnimalEntity.Kind.DEER) {
                deer = true;
                what = "олень";
                loot.add(new ItemStack(Outdoors.PELT_DEER));
                loot.add(new ItemStack(Outdoors.VENISON, 1 + rnd.nextInt(2)));
            } else {
                what = "кабан";
                loot.add(new ItemStack(Outdoors.PELT_BOAR));
                loot.add(new ItemStack(Outdoors.BOAR_MEAT, 1 + rnd.nextInt(2)));
            }
        } else if (e.getType() == EntityType.WOLF) {
            what = "волк";
            loot.add(new ItemStack(Outdoors.PELT_WOLF));
        } else if (e.getType() == EntityType.FOX) {
            what = "лиса";
            loot.add(new ItemStack(Outdoors.PELT_FOX));
        } else if (e.getType() == EntityType.RABBIT) {
            what = "заяц";
            loot.add(new ItemStack(Outdoors.PELT_RABBIT));
        } else if (e.getType() == EntityType.GOAT) {
            what = "горный козёл";
            loot.add(new ItemStack(Outdoors.HORN));
        } else return;
        for (ItemStack s : loot) e.dropStack(s);
        LifeData d = Life.get(p);
        d.skills.merge("x_hunted", 1, Integer::sum);
        // закон: лицензия, заповедник, сезон
        Region r = region(e.getX(), e.getZ());
        String why = !licensed(d) ? "охота без лицензии" : r == Region.RESERVE ? "охота в заповеднике"
                : r == Region.CITY ? "охота в городе" : deer && Seasons.season(w) == Seasons.Season.SPRING ? "олень весной — сезон закрыт" : "";
        if (why.isEmpty()) {
            p.sendMessage(Text.literal("🦌 Добыча: " + what + ". Шкуры и мясо возьмёт скупщик в «Берлоге»").formatted(Formatting.GREEN), true);
            return;
        }
        d.skills.merge("x_poached", 1, Integer::sum);
        if (p.isCreative()) return;
        boolean cop = Wanted.policeSees(w, p, 50);
        boolean ranger = !cop && rnd.nextFloat() < 0.3f;
        if (cop || ranger) {
            Wanted.add(p, 0, "браконьерство: " + why, 1);
            p.sendMessage(Text.literal(cop ? "🚓 Полиция видела браконьерство — " + why : "🌲 Егерь заметил браконьера и вызвал полицию — " + why).formatted(Formatting.RED), false);
        } else
            p.sendMessage(Text.literal("⚠ Браконьерство (" + why + "). В этот раз никто не видел").formatted(Formatting.GOLD), true);
    }

    // ------------------------------------------------------------------ лавка
    static void buy(ServerPlayerEntity p, String item) {
        boolean lodge = nearShop(p, "hunt"), fish = nearShop(p, "fish");
        LifeData d = Life.get(p);
        switch (item) {
            case "license" -> {
                if (!lodge || licensed(d)) return;
                if (!Life.pay(p, LICENSE_PRICE, "охотничья лицензия")) return;
                d.flags.add("hunt_license");
                give(p, new ItemStack(Outdoors.LICENSE));
                Life.message(p, "Охотнадзор", "Лицензия выдана. Охота — в лесах, горах и Пустоши. Заповедник у озера Ясное и Ривьера — под охраной. Весной на оленя не охотятся.");
            }
            case "rifle" -> {
                if (!lodge) return;
                if (!licensed(d)) {
                    p.sendMessage(Text.literal("Охотник: «Ствол — только с лицензией»").formatted(Formatting.RED), true);
                    return;
                }
                if (!Life.pay(p, RIFLE_PRICE, "охотничья винтовка с оптикой")) return;
                give(p, new ItemStack(Arsenal.SNIPER));
                give(p, new ItemStack(ModContent.AMMO, 20));
            }
            case "ammo" -> {
                if (!lodge || !Life.pay(p, AMMO_PRICE, "патроны ×20")) return;
                give(p, new ItemStack(ModContent.AMMO, 20));
            }
            case "rod" -> {
                if (!lodge && !fish || !Life.pay(p, ROD_PRICE, "удочка")) return;
                give(p, new ItemStack(Items.FISHING_ROD));
            }
            default -> {
                return;
            }
        }
        p.getServerWorld().playSound(null, p.getBlockPos(), ModContent.CASH, SoundCategory.PLAYERS, 0.7f, 1f);
        Outdoors.sendState(p);
    }

    static void give(ServerPlayerEntity p, ItemStack s) {
        if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
    }

    static boolean nearShop(ServerPlayerEntity p, String shop) {
        ServerWorld w = p.getServerWorld();
        if (shop.equals("hunt")) {
            Vec3d l = lodge(w, null);
            return l != null && p.squaredDistanceTo(l) < 10 * 10;
        }
        for (String[] f : Fishing.SHOPS) {
            Vec3d at = com.yompix.novograd.story.Story.place(w, f[0]);
            if (at != null && p.squaredDistanceTo(at) < 10 * 10) return true;
        }
        return false;
    }

    /** Продать всё: у охотника — шкуры и мясо (без лицензии — вполцены грязными), у рыбаков — рыбу по весу. */
    static void sellAll(ServerPlayerEntity p, String shop) {
        if (!nearShop(p, shop)) return;
        LifeData d = Life.get(p);
        int total = 0, count = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            int v = shop.equals("hunt") ? PRICES.getOrDefault(s.getItem(), 0) * s.getCount() : Fishing.price(s);
            if (v <= 0) continue;
            total += v;
            count += s.getCount();
            inv.setStack(i, ItemStack.EMPTY);
        }
        if (total <= 0) {
            p.sendMessage(Text.literal(shop.equals("hunt") ? "Скупщик: «Принеси шкуры, мясо или рога»" : "Рыбак: «Где рыба-то?»").formatted(Formatting.GRAY), true);
            return;
        }
        if (shop.equals("hunt") && !licensed(d)) {
            int dirty = total / 2;
            d.dirty += dirty;
            p.sendMessage(Text.literal("Скупщик: «Без лицензии — только вполцены, наличкой». +$" + dirty + " грязными").formatted(Formatting.GOLD), false);
            Life.sound(p, ModContent.CASH, 0.9f);
            Life.sync(p);
        } else {
            Life.earn(p, total, (shop.equals("hunt") ? "добыча" : "улов") + " ×" + count);
            if (!shop.equals("hunt")) d.skills.merge("x_fish_sold", total, Integer::sum);
        }
        Outdoors.sendState(p);
    }

    static NbtCompound state(ServerPlayerEntity p) {
        LifeData d = Life.get(p);
        NbtCompound n = new NbtCompound();
        n.putBoolean("license", licensed(d));
        n.putInt("hunted", d.skills.getOrDefault("x_hunted", 0));
        n.putInt("poached", d.skills.getOrDefault("x_poached", 0));
        n.putString("region", regionName(region(p.getX(), p.getZ())));
        n.putBoolean("lodgeNear", nearShop(p, "hunt"));
        n.putBoolean("fishNear", nearShop(p, "fish"));
        NbtList prices = new NbtList();
        for (var e : PRICES.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putString("item", Registries.ITEM.getId(e.getKey()).toString());
            c.putInt("price", e.getValue());
            prices.add(c);
        }
        n.put("prices", prices);
        Vec3d l = lodge(p.getServerWorld(), null);
        if (l != null) {
            n.putInt("lx", (int) l.x);
            n.putInt("lz", (int) l.z);
        } else {
            n.putInt("lx", LODGE_X);
            n.putInt("lz", LODGE_Z);
        }
        return n;
    }

    static String debug(ServerPlayerEntity p, String[] a) {
        ServerWorld w = p.getServerWorld();
        if (a.length > 1 && a[1].equals("license")) {
            Life.get(p).flags.add("hunt_license");
            return "Лицензия выдана";
        }
        if (a.length > 1 && a[1].equals("lodge")) {
            Vec3d l = lodge(w, p);
            return l == null ? "Подъедь ближе к Сосновке" : "Домик: " + (int) l.x + " " + (int) l.y + " " + (int) l.z;
        }
        if (a.length > 1) {
            Entity e = create(w, a[1]);
            if (e == null) return "deer|boar|wolf|fox|rabbit|goat|license|lodge";
            Vec3d at = p.getPos().add(p.getRotationVec(1).multiply(6, 0, 6));
            e.refreshPositionAndAngles(at.x, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z)), at.z, p.getYaw() + 180, 0);
            e.addCommandTag(TAG);
            w.spawnEntity(e);
            SPAWNED.add(e.getUuid());
            return "Дичь: " + a[1];
        }
        return "Регион: " + regionName(region(p.getX(), p.getZ())) + ", дичи рядом " +
                w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(96), e -> e.getCommandTags().contains(TAG)).size();
    }
}
