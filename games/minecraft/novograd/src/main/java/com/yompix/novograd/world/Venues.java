package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.block.NeonBlock;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.entity.CitizenEntity.Activity;
import com.yompix.novograd.entity.SeatEntity;
import com.yompix.novograd.life.Life;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CandleBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Кафе и супермаркеты с интерьерами. Строятся генератором острова (участки и пляж), в старых мирах — {@link Retrofit}.
 * <ul>
 *   <li>Кафе: стойка с кофемашиной и витриной, кухня за стойкой (плиты, мойка, холодильник), столики со стульями,
 *   летняя терраса под маркизой, бариста и повар. ПКМ по стойке или по бариста — меню: кофе (скорость), выпечка,
 *   обед дня (регенерация). Еда за столиком (сел на стул — ПКМ пустой рукой) — «приятного аппетита»: ещё здоровье.</li>
 *   <li>Супермаркет «Корзинка»: парковка с тележками, кассы, ряды полок, холодильники, фрукты; ПКМ по полке — купить,
 *   кассира можно ограбить (gta/Heists: держи прицел на продавце).</li>
 *   <li>Прохожие приходят поесть (world/LifeAi): заказ у стойки, столик, еда сидя, уходят.</li>
 * </ul>
 */
public final class Venues {
    private Venues() {}

    public enum Kind { CAFE, MARKET, BEACH, LOBBY }

    /** rot: куда смотрит вход (0 — север, 1 — восток…); style — оформление. */
    public record Site(String id, String name, Kind kind, int cx, int cz, int rot, int style, int r) {}

    /** Башня в деловом центре, где в лобби кофейня (участок 9, −9: напротив площади и станции метро). */
    static final int LOBBY_PX = 9, LOBBY_PZ = -9;

    public static final List<Site> SITES = new ArrayList<>();

    static {
        SITES.add(new Site("cafe_dt", "Кофейня «Бодрость»", Kind.LOBBY, LOBBY_PX * 48, LOBBY_PZ * 48, 2, 0, 17));
        SITES.add(new Site("cafe_suburb", "Кафе «Пончик»", Kind.CAFE, 4 * 48, 0, 0, 1, 17));
        SITES.add(new Site("cafe_riviera", "Кафе «Лазурь»", Kind.CAFE, -26 * 48, 48, 0, 2, 17));
        int bz = -472, bx = (int) Math.floor(Island.coastX(bz)) + 24;
        SITES.add(new Site("cafe_beach", "Пляжное кафе «Чайка»", Kind.BEACH, bx, bz, 1, 3, 11));
        SITES.add(new Site("cafe_mirage", "Дайнер «У Дасти»", Kind.CAFE, 25 * 48, 29 * 48, 0, 4, 17));
        SITES.add(new Site("market_suburb", "Супермаркет «Корзинка»", Kind.MARKET, 6 * 48, 0, 0, 0, 17));
        SITES.add(new Site("market_mirage", "Супермаркет «Мираж-Маркет»", Kind.MARKET, 26 * 48, 28 * 48, 0, 1, 17));
    }

    @Nullable
    public static Site site(String id) {
        for (Site s : SITES) if (s.id().equals(id)) return s;
        return null;
    }

    /** Участок (px, pz) занят кафе или супермаркетом (там нет домиков для жильцов). */
    public static boolean lot(long px, long pz) {
        for (Site s : SITES)
            if (s.kind() == Kind.CAFE || s.kind() == Kind.MARKET)
                if (Math.floorDiv(s.cx() + 24, 48) == px && Math.floorDiv(s.cz() + 24, 48) == pz) return true;
        return false;
    }

    // ------------------------------------------------------------------ поворот: локальные (вход на −z) ↔ мир
    static int[] toLocal(Site s, int x, int z) {
        int dx = x - s.cx(), dz = z - s.cz();
        return switch (s.rot()) {
            case 1 -> new int[]{dz, -dx};
            case 2 -> new int[]{-dx, -dz};
            case 3 -> new int[]{-dz, dx};
            default -> new int[]{dx, dz};
        };
    }

    static Vec3d toWorld(Site s, double lx, double lz, double y) {
        double dx, dz;
        switch (s.rot()) {
            case 1 -> { dx = -lz; dz = lx; }
            case 2 -> { dx = -lx; dz = -lz; }
            case 3 -> { dx = lz; dz = -lx; }
            default -> { dx = lx; dz = lz; }
        }
        return new Vec3d(s.cx() + dx + 0.5, y, s.cz() + dz + 0.5);
    }

    static BlockRotation rotation(Site s) {
        return switch (s.rot()) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    /** Мировой yaw для локального «смотрит на +z» с поворотом. */
    static float yaw(Site s, float localYaw) {
        return localYaw + 90 * s.rot();
    }

    @Nullable
    static Site siteAt(double x, double z, int pad) {
        for (Site s : SITES) {
            if (s.kind() == Kind.LOBBY) {
                if (Math.abs(x - s.cx() - 0.5) <= 17 + pad && Math.abs(z - s.cz() - 0.5) <= 17 + pad) return s;
                continue;
            }
            if (Math.abs(x - s.cx() - 0.5) <= s.r() + pad && Math.abs(z - s.cz() - 0.5) <= s.r() + pad) return s;
        }
        return null;
    }

    // ------------------------------------------------------------------ генерация
    public static List<Retrofit.Region> regions() {
        List<Retrofit.Region> out = new ArrayList<>();
        int G = Island.G;
        for (Site s : SITES) {
            if (s.kind() == Kind.LOBBY) out.add(new Retrofit.Region(s.cx() - 15, s.cz() - 15, s.cx() + 15, s.cz() + 15, G, G + 4));
            else out.add(new Retrofit.Region(s.cx() - s.r(), s.cz() - s.r(), s.cx() + s.r(), s.cz() + s.r(), G - 3, G + 18));
        }
        return out;
    }

    /** Столбец кафе/супермаркета (участки и пляж). true — построено здесь. */
    static boolean column(int x, int z, int s, Island.Column c) {
        for (Site site : SITES) {
            if (site.kind() == Kind.LOBBY) continue;
            if (Math.abs(x - site.cx()) > site.r() || Math.abs(z - site.cz()) > site.r()) continue;
            int[] l = toLocal(site, x, z);
            BlockRotation rot = rotation(site);
            Island.Column rc = rot == BlockRotation.NONE ? c : (y, st) -> c.set(y, st.rotate(rot));
            int g = Island.G;
            // ровная площадка: земля под зданием
            for (int y = g - 3; y < g; y++) rc.set(y, Blocks.DIRT.getDefaultState());
            switch (site.kind()) {
                case CAFE -> cafe(l[0], l[1], g, rc, site.style());
                case MARKET -> market(l[0], l[1], g, rc, site.style());
                case BEACH -> beachCafe(l[0], l[1], g, rc);
                default -> {}
            }
            return true;
        }
        return false;
    }

    private static BlockState st(Block b) {
        return b.getDefaultState();
    }

    private static BlockState facing(Block b, Direction d) {
        return b.getDefaultState().with(Properties.HORIZONTAL_FACING, d);
    }

    private static BlockState chair(Block b, Direction back) {
        return b.getDefaultState().with(StairsBlock.FACING, back).with(StairsBlock.HALF, BlockHalf.BOTTOM);
    }

    private static void door(Island.Column c, int y, Block b, Direction inward, DoorHinge hinge) {
        BlockState d = b.getDefaultState().with(DoorBlock.FACING, inward).with(DoorBlock.HINGE, hinge).with(DoorBlock.OPEN, false);
        c.set(y, d.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(y + 1, d.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Оформление кафе: пол, стены, отделка, маркиза (две полосы), стулья, неон. */
    private record Style(Block floor, Block floor2, Block wall, Block trim, Block roof, Block awn1, Block awn2, Block chair, Block table,
                         Block ground, NeonBlock.Color neon) {}

    private static final Style[] STYLES = {
            new Style(Blocks.DARK_OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BRICKS, Blocks.DARK_OAK_PLANKS, Blocks.GRAY_CONCRETE, Blocks.BROWN_WOOL, Blocks.WHITE_WOOL,
                    Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_FENCE, Blocks.GRASS_BLOCK, NeonBlock.Color.YELLOW),
            new Style(Blocks.OAK_PLANKS, Blocks.BIRCH_PLANKS, Blocks.BRICKS, Blocks.WHITE_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.RED_WOOL, Blocks.WHITE_WOOL,
                    Blocks.OAK_STAIRS, Blocks.OAK_FENCE, Blocks.GRASS_BLOCK, NeonBlock.Color.PINK),
            new Style(Blocks.SMOOTH_QUARTZ, Blocks.CUT_SANDSTONE, Blocks.WHITE_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.CYAN_WOOL, Blocks.WHITE_WOOL,
                    Blocks.BIRCH_STAIRS, Blocks.BIRCH_FENCE, Blocks.SMOOTH_SANDSTONE, NeonBlock.Color.CYAN),
            new Style(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_LOG, Blocks.HAY_BLOCK, Blocks.LIGHT_BLUE_WOOL, Blocks.WHITE_WOOL,
                    Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_FENCE, Blocks.SAND, NeonBlock.Color.CYAN),
            new Style(Blocks.WHITE_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.RED_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.LIGHT_GRAY_CONCRETE, Blocks.RED_WOOL, Blocks.WHITE_WOOL,
                    Blocks.CRIMSON_STAIRS, Blocks.IRON_BARS, Blocks.SMOOTH_SANDSTONE, NeonBlock.Color.RED),
    };

    /** Столики кафе: {x, z} (стулья — по бокам с запада и востока), ряд подхода — z + 2. */
    static final int[][] CAFE_TABLES = {{-9, -8}, {-4, -8}, {4, -8}, {9, -8}, {-9, -3}, {-4, -3}, {4, -3}, {9, -3}, {-9, 2}, {-4, 2}, {4, 2}, {9, 2}};
    static final int[][] CAFE_TERRACE = {{-9, -15}, {-4, -15}, {4, -15}, {9, -15}};
    static final int[][] BEACH_TABLES = {{-6, -5}, {6, -5}, {-6, -1}, {6, -1}};
    static final int[][] BEACH_TERRACE = {{-4, -9}, {4, -9}};

    private static boolean tableAt(int[][] t, int lx, int lz) {
        for (int[] a : t) if (a[0] == lx && a[1] == lz) return true;
        return false;
    }

    /** Стол или стул в этом столбце; true — что-то поставлено. */
    private static boolean furniture(int[][] tables, int lx, int lz, int s, Island.Column c, Style st, int seed) {
        for (int[] t : tables) {
            if (lz != t[1]) continue;
            if (lx == t[0]) {
                c.set(s + 1, st(st.table()));
                int k = Math.floorMod(t[0] * 7 + t[1] * 3 + seed, 4);
                c.set(s + 2, k == 0 ? Blocks.CANDLE.getDefaultState().with(CandleBlock.LIT, true) : k == 1 ? st(Blocks.POTTED_RED_TULIP)
                        : k == 2 ? st(Blocks.WHITE_CARPET) : st(Blocks.POTTED_WHITE_TULIP));
                return true;
            }
            if (lx == t[0] - 1) {
                c.set(s + 1, chair(st.chair(), Direction.WEST));
                return true;
            }
            if (lx == t[0] + 1) {
                c.set(s + 1, chair(st.chair(), Direction.EAST));
                return true;
            }
        }
        return false;
    }

    /** Кафе на участке 35×35: вход с севера (−z), терраса, зал на 24 места, стойка, кухня. */
    static void cafe(int lx, int lz, int s, Island.Column c, int style) {
        Style st = STYLES[style];
        int ax = Math.abs(lx);
        boolean diner = style == 4;
        if (lz < -12) { // терраса и дорожка
            c.set(s, lz >= -15 && ax <= 12 ? st(diner ? Blocks.RED_CONCRETE : st.floor2()) : st(diner ? Blocks.SMOOTH_STONE : Blocks.SMOOTH_STONE));
            if (lz >= -14 && ax <= 12) c.set(s + 4, st(((lx & 1) == 0) ? st.awn1() : st.awn2())); // маркиза
            if (lz == -14 && (ax == 12)) for (int y = s + 1; y <= s + 3; y++) c.set(y, st(st.table()));
            if (furniture(CAFE_TERRACE, lx, lz, s, c, st, style)) return;
            if (lz == -16 && (ax == 7 || ax == 14)) c.set(s + 1, st(Blocks.POTTED_AZALEA_BUSH));
            return;
        }
        if (ax > 13 || lz > 12) { // двор: газон/плитка, деревья, мусорные баки
            c.set(s, st(st.ground()));
            if (diner && ax > 13) {
                c.set(s, ModContent.ASPHALT.getDefaultState());
                if (lz % 4 == 0) c.set(s, st(Blocks.WHITE_CONCRETE));
            }
            if (lz == 15 && (lx == 8 || lx == 9)) {
                c.set(s + 1, st(Blocks.GREEN_TERRACOTTA));
                c.set(s + 2, Blocks.DARK_OAK_TRAPDOOR.getDefaultState());
            }
            if (!diner && ax == 15 && Math.floorMod(lz, 8) == 4) {
                Block log = style == 2 ? Blocks.JUNGLE_LOG : Blocks.OAK_LOG, lv = style == 2 ? Blocks.JUNGLE_LEAVES : Blocks.OAK_LEAVES;
                for (int y = 1; y <= 4; y++) c.set(s + y, st(log));
                c.set(s + 5, lv.getDefaultState().with(LeavesBlock.PERSISTENT, true));
            }
            return;
        }
        boolean wall = ax == 13 || lz == -12 || lz == 12;
        c.set(s, wall ? st(st.trim()) : (diner ? (((lx + lz) & 1) == 0 ? st(st.floor()) : st(st.floor2())) : (lz >= 7 ? st(Blocks.SMOOTH_STONE) : st(st.floor()))));
        c.set(s + 5, wall ? st(st.trim()) : (Math.floorMod(lx, 4) == 2 && Math.floorMod(lz, 4) == 2) ? st(Blocks.SEA_LANTERN) : st(st.roof()));
        if (wall) c.set(s + 6, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
        if (wall) {
            if (lz == -12) { // фасад: витрины, дверь посередине, неон над окнами
                if (lx == 0 || lx == -1) {
                    door(c, s + 1, diner ? Blocks.IRON_DOOR : Blocks.SPRUCE_DOOR, Direction.SOUTH, lx == 0 ? DoorHinge.RIGHT : DoorHinge.LEFT);
                    c.set(s + 3, st(Blocks.GLASS));
                } else for (int y = s + 1; y <= s + 3; y++) c.set(y, ax == 13 || ax == 6 ? st(st.trim()) : st(Blocks.GLASS));
                c.set(s + 4, facing(ModContent.NEON, Direction.NORTH).with(NeonBlock.LIT, true).with(NeonBlock.COLOR, st.neon()));
                return;
            }
            for (int y = s + 1; y <= s + 4; y++) c.set(y, (y == s + 2 || y == s + 3) && lz != 12 && Math.floorMod(lz, 4) == 0 && lz < 6 ? st(Blocks.GLASS) : st(st.wall()));
            return;
        }
        // стойка
        if (lz == 6) {
            if (ax <= 10) {
                c.set(s + 1, facing(ModContent.SHOP_COUNTER, Direction.NORTH));
                if (lx == -2) c.set(s + 2, st(Blocks.BREWING_STAND)); // кофемашина
                else if (lx == 2 || lx == 4) c.set(s + 2, st(Blocks.CAKE));
                else if (lx == 7) c.set(s + 2, Blocks.LANTERN.getDefaultState());
                else if (lx == -6) c.set(s + 2, st(Blocks.POTTED_CACTUS));
            }
            return;
        }
        // кухня
        if (lz >= 7) {
            if (lz == 11) {
                if (lx >= -11 && lx <= -6) c.set(s + 1, switch (lx) {
                    case -11, -10 -> facing(Blocks.SMOKER, Direction.NORTH);
                    case -9 -> facing(Blocks.FURNACE, Direction.NORTH);
                    case -8 -> Blocks.WATER_CAULDRON.getDefaultState().with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3);
                    default -> Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.NORTH);
                });
                if (lx >= 5 && lx <= 7) {
                    c.set(s + 1, st(Blocks.WHITE_CONCRETE));
                    c.set(s + 2, st(Blocks.WHITE_CONCRETE)); // холодильник
                    c.set(s + 3, st(Blocks.LIGHT_GRAY_CONCRETE));
                }
                if (lx == 9 || lx == 10) c.set(s + 1, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
                if (lx >= -11 && lx <= -9) c.set(s + 3, Blocks.IRON_TRAPDOOR.getDefaultState().with(net.minecraft.block.TrapdoorBlock.HALF, BlockHalf.TOP)); // вытяжка
                return;
            }
            if (lz == 9 && ax <= 3) {
                c.set(s + 1, st(Blocks.SMOOTH_STONE));
                if (lx == -1) c.set(s + 2, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.getDefaultState());
                if (lx == 2) c.set(s + 2, st(Blocks.MELON));
                return;
            }
            if (lz == 7 && ax == 12) c.set(s + 1, st(Blocks.POTTED_FERN));
            return;
        }
        // зал
        if (furniture(CAFE_TABLES, lx, lz, s, c, st, style)) return;
        if (ax == 12 && (lz == -11 || lz == 5)) c.set(s + 1, st(Blocks.POTTED_FERN));
        if (diner && ax == 12 && lz == -6) c.set(s + 1, facing(ModContent.SLOT_MACHINE, lx > 0 ? Direction.WEST : Direction.EAST)); // в дайнере Миража — автомат
    }

    /** Пляжное кафе-павильон: деревянный настил, навес из соломы на столбах, барная стойка, столики. */
    static void beachCafe(int lx, int lz, int s, Island.Column c) {
        Style st = STYLES[3];
        int ax = Math.abs(lx);
        if (ax > 11 || lz < -11 || lz > 11) return;
        c.set(s, st(Blocks.SAND));
        if (ax <= 9 && lz >= -10 && lz <= 7) c.set(s, st(st.floor()));
        if (lz < -7) { // терраса
            if (ax <= 9 && lz >= -10) {
                if (furniture(BEACH_TERRACE, lx, lz, s, c, st, 3)) return;
                if (lz == -10 && (ax == 9 || ax == 0)) {
                    c.set(s + 1, st(Blocks.SPRUCE_FENCE));
                    c.set(s + 2, st(Blocks.SPRUCE_FENCE));
                    c.set(s + 3, ax == 0 ? st(Blocks.LANTERN) : st(Blocks.WHITE_WOOL));
                }
            }
            return;
        }
        if (ax > 9 || lz > 7) {
            if (ax == 11 && Math.floorMod(lz, 7) == 0) { // пальмы по бокам
                for (int y = 1; y <= 5; y++) c.set(s + y, st(Blocks.JUNGLE_LOG));
                c.set(s + 6, Blocks.JUNGLE_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
            }
            return;
        }
        boolean edge = ax == 9 || lz == -7 || lz == 7;
        // столбы и крыша
        if (edge && (Math.floorMod(lx, 3) == 0 && (lz == -7 || lz == 7) || ax == 9 && Math.floorMod(lz, 4) == 1))
            for (int y = s + 1; y <= s + 3; y++) c.set(y, st(st.trim()));
        c.set(s + 4, edge ? st(((lx + lz) & 1) == 0 ? st.awn1() : st.awn2()) : st(st.roof()));
        if (!edge && Math.floorMod(lx, 4) == 0 && Math.floorMod(lz, 4) == 0) c.set(s + 3, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
        if (lz == 7 && ax < 9) { // задняя стенка за баром
            for (int y = s + 1; y <= s + 3; y++) c.set(y, st(st.wall()));
            return;
        }
        if (lz == 3 && ax <= 6) { // барная стойка
            c.set(s + 1, facing(ModContent.SHOP_COUNTER, Direction.NORTH));
            if (lx == 0) c.set(s + 2, st(Blocks.BREWING_STAND));
            if (lx == 4) c.set(s + 2, st(Blocks.CAKE));
            return;
        }
        if (lz == 6) {
            if (lx >= -6 && lx <= -4) c.set(s + 1, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
            if (lx == -2) c.set(s + 1, facing(Blocks.SMOKER, Direction.NORTH));
            if (lx >= 3 && lx <= 4) {
                c.set(s + 1, st(Blocks.WHITE_CONCRETE));
                c.set(s + 2, st(Blocks.WHITE_CONCRETE));
            }
            return;
        }
        furniture(BEACH_TABLES, lx, lz, s, c, st, 3);
    }

    /** Супермаркет на участке: парковка, кассы у входа, ряды полок, холодильники у задней стены. */
    static void market(int lx, int lz, int s, Island.Column c, int style) {
        int ax = Math.abs(lx);
        if (lz < -9 || ax > 15 || lz > 13) { // парковка, газон, разгрузка
            if (lz < -9) {
                c.set(s, ModContent.ASPHALT.getDefaultState());
                if (lz >= -16 && lz <= -12 && Math.floorMod(lx, 5) == 0 && ax >= 5) c.set(s, st(Blocks.WHITE_CONCRETE));
                if (lz == -11 && lx >= 10 && lx <= 14) { // тележки у входа
                    c.set(s + 1, lx == 10 || lx == 14 ? st(Blocks.IRON_BARS) : st(Blocks.HOPPER));
                }
                if (lz == -17 && (ax == 15)) {
                    for (int y = 1; y <= 3; y++) c.set(s + y, ModContent.LAMP_POST.getDefaultState());
                    c.set(s + 4, ModContent.STREET_LAMP.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
                }
            } else c.set(s, lz > 13 ? st(Blocks.SMOOTH_STONE) : st(Blocks.GRASS_BLOCK));
            return;
        }
        boolean wall = ax == 15 || lz == -9 || lz == 13;
        c.set(s, wall ? st(Blocks.LIGHT_GRAY_CONCRETE) : (((lx + lz) & 1) == 0 ? st(Blocks.WHITE_CONCRETE) : st(Blocks.SMOOTH_QUARTZ)));
        c.set(s + 7, wall ? st(Blocks.LIGHT_GRAY_CONCRETE) : (Math.floorMod(lx, 4) == 1 && Math.floorMod(lz, 3) == 0) ? st(Blocks.SEA_LANTERN) : st(Blocks.WHITE_CONCRETE));
        Block brand = style == 1 ? Blocks.ORANGE_CONCRETE : Blocks.GREEN_CONCRETE;
        if (wall) {
            if (lz == -9) {
                for (int y = s + 1; y <= s + 4; y++) if (ax > 2) c.set(y, ax % 5 == 0 ? st(Blocks.LIGHT_GRAY_CONCRETE) : st(Blocks.GLASS));
                if (ax <= 2) c.set(s + 4, st(Blocks.GLASS));
                c.set(s + 5, st(brand));
                c.set(s + 6, facing(ModContent.NEON, Direction.NORTH).with(NeonBlock.LIT, true).with(NeonBlock.COLOR, style == 1 ? NeonBlock.Color.YELLOW : NeonBlock.Color.GREEN));
                return;
            }
            for (int y = s + 1; y <= s + 6; y++) c.set(y, y == s + 5 ? st(brand) : st(Blocks.WHITE_CONCRETE));
            return;
        }
        // кассы: ленты (прилавки) вдоль z, кассир — с запада
        for (int cx : new int[]{-11, -6, 6, 11}) {
            if (lx == cx && lz >= -6 && lz <= -4) {
                c.set(s + 1, facing(ModContent.SHOP_COUNTER, Direction.WEST));
                if (lz == -6) c.set(s + 2, st(Blocks.BLACK_CARPET));
                return;
            }
            if (lx == cx + 1 && lz == -6) {
                c.set(s + 1, st(Blocks.IRON_BARS));
                c.set(s + 2, st(Blocks.REDSTONE_LAMP).with(net.minecraft.block.RedstoneLampBlock.LIT, true));
                return;
            }
        }
        if (lz == -7 && (lx == 1 || lx == -1)) { // тележки у входа внутри
            c.set(s + 1, st(Blocks.HOPPER));
            return;
        }
        // холодильники у задней стены
        if (lz == 12) {
            c.set(s + 1, st(Blocks.PACKED_ICE));
            c.set(s + 2, st(Blocks.LIGHT_BLUE_STAINED_GLASS));
            c.set(s + 3, st(Blocks.WHITE_CONCRETE));
            return;
        }
        // ряды полок
        int[] rows = {-13, -9, -5, 5, 9, 13};
        for (int ri = 0; ri < rows.length; ri++) {
            if (lx != rows[ri] || lz < 0 || lz > 9) continue;
            Block[] goods = switch (ri) {
                case 0 -> new Block[]{Blocks.HAY_BLOCK, Blocks.BARREL, Blocks.BEEHIVE};            // хлеб и крупы
                case 1 -> new Block[]{Blocks.WHITE_CONCRETE, Blocks.CHISELED_BOOKSHELF, Blocks.WHITE_TERRACOTTA}; // молочное
                case 2 -> new Block[]{Blocks.LIME_CONCRETE, Blocks.CHISELED_BOOKSHELF, Blocks.RED_TERRACOTTA};   // напитки
                case 3 -> new Block[]{Blocks.ORANGE_TERRACOTTA, Blocks.CHISELED_BOOKSHELF, Blocks.YELLOW_TERRACOTTA}; // сладости
                case 4 -> new Block[]{Blocks.LIGHT_BLUE_CONCRETE, Blocks.BARREL, Blocks.CYAN_TERRACOTTA};      // бытовое
                default -> new Block[]{Blocks.DRIED_KELP_BLOCK, Blocks.CHISELED_BOOKSHELF, Blocks.BROWN_TERRACOTTA}; // консервы
            };
            Direction face = lx < 0 ? Direction.EAST : Direction.WEST;
            for (int k = 0; k < 3; k++) {
                Block b = goods[Math.floorMod(k + lz, 3)];
                BlockState bs = b == Blocks.CHISELED_BOOKSHELF ? facing(b, face) : b == Blocks.BARREL ? b.getDefaultState().with(Properties.FACING, face)
                        : b == Blocks.BEEHIVE ? facing(b, face) : st(b);
                c.set(s + 1 + k, bs);
            }
            c.set(s + 4, Blocks.SMOOTH_STONE_SLAB.getDefaultState());
            return;
        }
        // фрукты и овощи посередине
        if ((ax == 2 || ax == 3) && lz >= 1 && lz <= 8) {
            c.set(s + 1, st(Blocks.SPRUCE_PLANKS));
            c.set(s + 2, (lz & 1) == 0 ? st(Blocks.MELON) : st(Blocks.PUMPKIN));
            return;
        }
        if ((lx == -7 && lz == 4) || (lx == 7 && lz == 7)) c.set(s + 1, st(Blocks.HOPPER)); // тележка в проходе
    }

    /** Кофейня в лобби башни делового центра (вход с юга): стойка с кухней, 8 столиков. true — столбец её. */
    static boolean lobby(long px, long pz, int lx, int lz, int L, Island.Column c, int W) {
        if (px != LOBBY_PX || pz != LOBBY_PZ) return false;
        int ax = Math.abs(lx);
        Style st = STYLES[0];
        if ((lx == 0 || lx == 1) && lz >= -2) {
            c.set(L + 1, st(Blocks.BROWN_CARPET));
            return true;
        }
        if (lz == 0 && lx >= -W + 1 && lx <= -3) { // стойка
            c.set(L + 1, facing(ModContent.SHOP_COUNTER, Direction.SOUTH));
            if (lx == -5) c.set(L + 2, st(Blocks.BREWING_STAND));
            else if (lx == -8) c.set(L + 2, st(Blocks.CAKE));
            else if (lx == -3) c.set(L + 2, Blocks.LANTERN.getDefaultState());
            return true;
        }
        if (lz == -3 && lx >= -W + 1 && lx <= -6) { // кухня у стены ядра
            c.set(L + 1, switch (Math.floorMod(lx, 4)) {
                case 0 -> facing(Blocks.SMOKER, Direction.SOUTH);
                case 1 -> Blocks.WATER_CAULDRON.getDefaultState().with(net.minecraft.block.LeveledCauldronBlock.LEVEL, 3);
                case 2 -> st(Blocks.WHITE_CONCRETE);
                default -> Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP);
            });
            if (Math.floorMod(lx, 4) == 2) c.set(L + 2, st(Blocks.WHITE_CONCRETE));
            return true;
        }
        if (lz >= -2 && lz <= 2 && lx >= -W + 1 && lx <= -3) return true; // проход за стойкой и место у неё (без ресепшена)
        if (lz >= 3) {
            int[][] t = lobbyTables(W);
            if (furniture(t, lx, lz, L, c, st, 0)) return true;
            if (lz == W && (ax == W || lx == -6 || lx == 7)) {
                c.set(L + 1, st(Blocks.POTTED_FERN));
                return true;
            }
            return lz >= 3 && lx < 3; // остальная часть зала — пусто (без ресепшена)
        }
        return false;
    }

    static int[][] lobbyTables(int W) {
        List<int[]> l = new ArrayList<>();
        for (int tz : new int[]{4, 8, 12})
            for (int tx : new int[]{-9, -5, 5, 9})
                if (Math.abs(tx) + 1 <= W - 1 && tz <= W - 2 && !(tx > 0 && tz <= 9 && tx <= 8)) l.add(new int[]{tx, tz});
        return l.toArray(new int[0][]);
    }

    // ------------------------------------------------------------------ меню
    private static final String TAG = "nv_venue";

    static ItemStack food(Item base, String name, int nutrition, float sat, boolean drink, StatusEffectInstance... fx) {
        ItemStack s = new ItemStack(base);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).formatted(Formatting.WHITE));
        FoodComponent.Builder b = new FoodComponent.Builder().nutrition(nutrition).saturationModifier(sat).alwaysEdible();
        if (drink) b.snack();
        for (StatusEffectInstance e : fx) b.statusEffect(e, 1f);
        s.set(DataComponentTypes.FOOD, b.build());
        return s;
    }

    private record Dish(String name, String desc, int price, java.util.function.Supplier<ItemStack> item) {}

    private static final Dish[] CAFE_MENU = {
            new Dish("Кофе «Бодрость»", "скорость на 90 с", 4, () -> food(Items.HONEY_BOTTLE, "Кофе «Бодрость»", 2, 0.3f, true,
                    new StatusEffectInstance(StatusEffects.SPEED, 1800, 0))),
            new Dish("Капучино", "скорость и сноровка на 2 мин", 6, () -> food(Items.HONEY_BOTTLE, "Капучино", 3, 0.4f, true,
                    new StatusEffectInstance(StatusEffects.SPEED, 2400, 0), new StatusEffectInstance(StatusEffects.HASTE, 2400, 0))),
            new Dish("Круассан", "+здоровье", 5, () -> food(Items.BREAD, "Круассан", 5, 0.6f, false, new StatusEffectInstance(StatusEffects.INSTANT_HEALTH, 1, 0))),
            new Dish("Чизкейк", "регенерация 10 с", 7, () -> food(Items.PUMPKIN_PIE, "Чизкейк", 6, 0.6f, false, new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0))),
            new Dish("Бургер", "сытно", 8, () -> new ItemStack(ModContent.BURGER)),
            new Dish("Пицца", "сытно", 9, () -> new ItemStack(ModContent.PIZZA)),
            new Dish("Салат", "легко", 6, () -> new ItemStack(ModContent.SALAD)),
            new Dish("Обед дня", "суп, второе, компот: регенерация и запас здоровья", 15, () -> food(Items.MUSHROOM_STEW, "Обед дня", 10, 1.0f, false,
                    new StatusEffectInstance(StatusEffects.REGENERATION, 300, 1), new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 1))),
    };

    private static final Dish[] MARKET_MENU = {
            new Dish("Хлеб", "", 2, () -> new ItemStack(Items.BREAD)),
            new Dish("Яблоки ×3", "", 3, () -> new ItemStack(Items.APPLE, 3)),
            new Dish("Газировка", "", 2, () -> new ItemStack(ModContent.COLA)),
            new Dish("Салат в коробке", "", 4, () -> new ItemStack(ModContent.SALAD)),
            new Dish("Курица-гриль", "", 7, () -> new ItemStack(Items.COOKED_CHICKEN)),
            new Dish("Энергетик", "скорость II на минуту", 6, () -> food(Items.HONEY_BOTTLE, "Энергетик «Вольт»", 1, 0.1f, true,
                    new StatusEffectInstance(StatusEffects.SPEED, 1200, 1))),
            new Dish("Шоколадки ×4", "", 3, () -> {
                ItemStack s = new ItemStack(Items.COOKIE, 4);
                s.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Шоколадка").formatted(Formatting.WHITE));
                return s;
            }),
            new Dish("Аптечка", "лечит в бою", 45, () -> new ItemStack(com.yompix.novograd.gta.Arsenal.MEDKIT)),
            new Dish("Цветы", "для неё", 5, () -> new ItemStack(Items.POPPY, 3)),
    };

    /** Для автотеста. */
    public static void debugMenu(ServerPlayerEntity p, Site s) {
        openMenu(p, s);
    }

    static void openMenu(ServerPlayerEntity p, Site s) {
        boolean market = s.kind() == Kind.MARKET;
        Dish[] menu = market ? MARKET_MENU : CAFE_MENU;
        int money = Life.get(p).money;
        NbtCompound m = CityNet.menu("venue:" + s.id(), s.name().toUpperCase(),
                market ? "Возьми с полки — оплата картой на кассе" : "Сядь за столик (ПКМ по стулу) — и ешь сидя: «приятного аппетита» лечит", market ? 0xFF2E7D32 : 0xFF8D5524);
        for (Dish d : menu) CityNet.item(m, d.name(), d.desc(), d.price(), money >= d.price());
        CityNet.send(p, "menu", m);
        p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.4f, 1.6f);
    }

    static void pick(ServerPlayerEntity p, String ctx, int i) {
        if (!ctx.startsWith("venue:")) return;
        Site s = site(ctx.substring(6));
        if (s == null || p.squaredDistanceTo(s.cx() + 0.5, p.getY(), s.cz() + 0.5) > 30 * 30) return;
        Dish[] menu = s.kind() == Kind.MARKET ? MARKET_MENU : CAFE_MENU;
        if (i < 0 || i >= menu.length) return;
        Dish d = menu[i];
        if (!Life.pay(p, d.price(), d.name() + " · " + s.name())) return;
        ItemStack it = d.item().get();
        if (!p.getInventory().insertStack(it)) p.dropItem(it, false);
        p.playSoundToPlayer(ModContent.CASH, SoundCategory.PLAYERS, 0.6f, 1.3f);
        if (s.kind() != Kind.MARKET) p.sendMessage(Text.literal("«Ваш заказ! Садитесь, где удобно»").formatted(Formatting.GOLD), true);
        com.yompix.novograd.life.Achievements.give(p, s.kind() == Kind.MARKET ? "market_buy" : "cafe_meal");
    }

    // ------------------------------------------------------------------ такт: персонал, вывески, еда сидя, посетители
    public static void init() {
        com.yompix.novograd.life.Achievements.ALL.putIfAbsent("cafe_meal", new String[]{"Приятного аппетита", "Поесть в кафе"});
        com.yompix.novograd.life.Achievements.ALL.putIfAbsent("market_buy", new String[]{"Список покупок", "Купить что-нибудь в супермаркете"});
        CityNet.init();
        net.minecraft.util.Identifier early = Novograd.id("venues_early");
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, world, hand, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity p) || p.isSpectator()) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            Site s = siteAt(pos.getX(), pos.getZ(), 0);
            if (s == null || pos.getY() > Island.G + 6) return ActionResult.PASS;
            BlockState b = world.getBlockState(pos);
            boolean market = s.kind() == Kind.MARKET;
            if (b.isOf(ModContent.SHOP_COUNTER) && !market
                    || market && (b.isOf(Blocks.CHISELED_BOOKSHELF) || b.isOf(Blocks.BARREL) || b.isOf(Blocks.BEEHIVE) || b.isOf(Blocks.PACKED_ICE)
                    || b.isOf(Blocks.LIGHT_BLUE_STAINED_GLASS) || b.isOf(Blocks.MELON) || b.isOf(Blocks.PUMPKIN) || b.isOf(Blocks.HAY_BLOCK))) {
                openMenu(p, s);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(early, (player, world, hand, entity, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity p) || !(entity instanceof CitizenEntity c)) return ActionResult.PASS;
            for (String t : c.getCommandTags())
                if (t.startsWith(TAG + ":")) {
                    Site s = site(t.substring(TAG.length() + 1));
                    if (s != null && !(s.kind() == Kind.MARKET && c.getRole() == CitizenEntity.Role.CITIZEN)) {
                        openMenu(p, s);
                        return ActionResult.SUCCESS;
                    }
                }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            try {
                tick(s);
            } catch (RuntimeException e) {
                Novograd.LOG.warn("Кафе: ошибка такта", e);
            }
        });
    }

    private static void tick(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        if (!(w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) return;
        long now = w.getTime();
        if (now % 5 == 2) eatSeated(w);
        if (now % 10 == 4) visits(w, now);
        if (now % 60 != 31) return;
        for (Site s : SITES) {
            Vec3d c = new Vec3d(s.cx() + 0.5, Island.G + 1, s.cz() + 0.5);
            boolean near = false;
            for (ServerPlayerEntity p : w.getPlayers()) if (p.squaredDistanceTo(c.x, p.getY(), c.z) < 64 * 64) near = true;
            if (!near || !built(w, s)) continue;
            Box box = new Box(c.x - 20, Island.G - 1, c.z - 20, c.x + 20, Island.G + 14, c.z + 20);
            staff(w, s, box);
            if (w.getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, box, e -> e.getCommandTags().contains(TAG + ":" + s.id())).isEmpty()) labels(w, s);
        }
    }

    /** Здание уже стоит (отметка чанка или новый мир). */
    static boolean built(ServerWorld w, Site s) {
        BlockPos p = s.kind() == Kind.LOBBY ? BlockPos.ofFloored(toWorld(s, 0, 0, Island.G)) : BlockPos.ofFloored(toWorld(s, 0, 6, Island.G + 1));
        if (!w.isChunkLoaded(p)) return false;
        if (s.kind() == Kind.LOBBY) return w.getBlockState(new BlockPos(s.cx() - 5, Island.G + 1, s.cz())).isOf(ModContent.SHOP_COUNTER);
        if (s.kind() == Kind.MARKET) return w.getBlockState(BlockPos.ofFloored(toWorld(s, -11, -5, Island.G + 1))).isOf(ModContent.SHOP_COUNTER);
        if (s.kind() == Kind.BEACH) return w.getBlockState(BlockPos.ofFloored(toWorld(s, 0, 3, Island.G + 1))).isOf(ModContent.SHOP_COUNTER);
        return w.getBlockState(p).isOf(ModContent.SHOP_COUNTER);
    }

    /** Персонал: {локальные x, z, yaw (локальный), роль, имя}. */
    private static List<Object[]> staffOf(Site s) {
        List<Object[]> l = new ArrayList<>();
        switch (s.kind()) {
            case CAFE -> {
                l.add(new Object[]{0.0, 7.0, 180f, CitizenEntity.Role.SHOPKEEPER, s.style() == 4 ? "Официантка Дейзи" : "Бариста Оля"});
                l.add(new Object[]{-9.0, 9.5, 0f, CitizenEntity.Role.SHOPKEEPER, s.style() == 4 ? "Повар Дасти" : "Повар Михалыч"});
            }
            case BEACH -> l.add(new Object[]{0.0, 4.5, 180f, CitizenEntity.Role.SHOPKEEPER, "Бармен Чайка"});
            case LOBBY -> l.add(new Object[]{-6.0, -1.5, 0f, CitizenEntity.Role.SHOPKEEPER, "Бариста Кирилл"});
            case MARKET -> {
                l.add(new Object[]{-12.0, -5.0, -90f, CitizenEntity.Role.SHOPKEEPER, "Кассир Валя"});
                l.add(new Object[]{5.0, -5.0, -90f, CitizenEntity.Role.SHOPKEEPER, "Кассир Тимур"});
                l.add(new Object[]{3.0, -7.5, 180f, CitizenEntity.Role.CITIZEN, "Охранник Гена"});
            }
        }
        return l;
    }

    private static void staff(ServerWorld w, Site s, Box box) {
        List<CitizenEntity> have = w.getEntitiesByClass(CitizenEntity.class, box, e -> e.getCommandTags().contains(TAG + ":" + s.id()));
        List<Object[]> want = staffOf(s);
        if (have.size() >= want.size()) return;
        for (Object[] o : want) {
            String name = (String) o[4];
            if (have.stream().anyMatch(e -> e.getName().getString().equals(name))) continue;
            double lx = (double) o[0], lz = (double) o[1];
            Vec3d at = s.kind() == Kind.LOBBY ? new Vec3d(s.cx() + lx + 0.5, Island.G + 1, s.cz() + lz + 0.5) : toWorld(s, lx, lz, Island.G + 1);
            CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
            float yaw = s.kind() == Kind.LOBBY ? (float) o[2] : yaw(s, (float) o[2]);
            c.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
            c.setHeadYaw(yaw);
            c.setBodyYaw(yaw);
            c.setup(CitizenEntity.RANDOM_FROM + Math.floorMod(name.hashCode(), CitizenEntity.RANDOM_COUNT), (CitizenEntity.Role) o[3], true, name);
            c.addCommandTag(TAG + ":" + s.id());
            c.addCommandTag(TAG);
            w.spawnEntity(c);
        }
    }

    private static void labels(ServerWorld w, Site s) {
        boolean market = s.kind() == Kind.MARKET;
        Formatting col = market ? Formatting.GREEN : s.style() == 4 ? Formatting.RED : Formatting.GOLD;
        Vec3d sign = switch (s.kind()) {
            case LOBBY -> new Vec3d(s.cx() + 1, Island.G + 5.6, s.cz() + 17.5);
            case MARKET -> toWorld(s, 0, -9.6, Island.G + 8.3);
            case BEACH -> toWorld(s, 0, -7.5, Island.G + 5.6);
            default -> toWorld(s, 0, -12.6, Island.G + 6.4);
        };
        label(w, s, sign, Text.literal(s.name().toUpperCase()).formatted(col, Formatting.BOLD), 2.0f);
        if (!market) {
            Vec3d menu = switch (s.kind()) {
                case LOBBY -> new Vec3d(s.cx() - 5.5, Island.G + 3.4, s.cz() + 0.5);
                case BEACH -> toWorld(s, 0, 3, Island.G + 3.4);
                default -> toWorld(s, 0, 6, Island.G + 3.6);
            };
            StringBuilder sb = new StringBuilder("МЕНЮ\n");
            for (Dish d : CAFE_MENU) sb.append(d.name()).append(" — $").append(d.price()).append('\n');
            sb.append("ПКМ по стойке — заказать");
            label(w, s, menu, Text.literal(sb.toString()).formatted(Formatting.WHITE), 0.6f);
        } else {
            String[] cats = {"Хлеб и крупы", "Молочное", "Напитки", "Сладости", "Бытовое", "Консервы"};
            int[] rows = {-13, -9, -5, 5, 9, 13};
            for (int i = 0; i < rows.length; i++) label(w, s, toWorld(s, rows[i], 5, Island.G + 5.3), Text.literal(cats[i]).formatted(Formatting.YELLOW), 0.8f);
            label(w, s, toWorld(s, 0, 4.5, Island.G + 3.8), Text.literal("Фрукты и овощи").formatted(Formatting.GREEN), 0.8f);
            label(w, s, toWorld(s, 0, 11.5, Island.G + 4.6), Text.literal("❄ Заморозка").formatted(Formatting.AQUA), 0.8f);
            label(w, s, toWorld(s, 0, -7.5, Island.G + 3.6), Text.literal("ПКМ по полке — купить").formatted(Formatting.WHITE), 0.6f);
        }
    }

    private static void label(ServerWorld w, Site s, Vec3d at, Text text, float scale) {
        DisplayEntity.TextDisplayEntity d = EntityType.TEXT_DISPLAY.create(w);
        if (d == null) return;
        NbtCompound n = new NbtCompound();
        n.putString("billboard", "center");
        n.putString("alignment", "center");
        n.putInt("background", 0xAA101010);
        n.putInt("line_width", 300);
        NbtCompound br = new NbtCompound();
        br.putInt("block", 15);
        br.putInt("sky", 15);
        n.put("brightness", br);
        NbtCompound tr = new NbtCompound();
        tr.put("translation", Metro.floats(0, 0, 0));
        tr.put("left_rotation", Metro.floats(0, 0, 0, 1));
        tr.put("scale", Metro.floats(scale, scale, scale));
        tr.put("right_rotation", Metro.floats(0, 0, 0, 1));
        n.put("transformation", tr);
        d.readNbt(n);
        d.setText(text);
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        d.addCommandTag(TAG + ":" + s.id());
        w.spawnEntity(d);
    }

    // ------------------------------------------------------------------ еда за столиком
    private static final Map<UUID, Long> BON_APPETIT = new HashMap<>();

    /** Игрок сидит на стуле в кафе и доедает — «приятного аппетита»: регенерация и сытость сверху. */
    private static void eatSeated(ServerWorld w) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (!(p.getVehicle() instanceof SeatEntity)) continue;
            Site s = siteAt(p.getX(), p.getZ(), 0);
            if (s == null || s.kind() == Kind.MARKET) continue;
            if (p.isUsingItem() && p.getActiveItem().contains(DataComponentTypes.FOOD) && p.getItemUseTimeLeft() <= 5) {
                long now = w.getTime();
                if (now - BON_APPETIT.getOrDefault(p.getUuid(), -9999L) < 40) continue;
                BON_APPETIT.put(p.getUuid(), now);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 160, 0));
                p.getHungerManager().add(2, 0.6f);
                p.sendMessage(Text.literal("Приятного аппетита! За столиком вкуснее: +здоровье").formatted(Formatting.GOLD), true);
                com.yompix.novograd.life.Achievements.give(p, "cafe_meal");
            }
        }
    }

    // ------------------------------------------------------------------ прохожие едят в кафе (зовёт LifeAi.eat)
    record Seat(Vec3d at, float yaw, Vec3d approach) {}

    private static final Map<String, List<Seat>> SEATS = new HashMap<>();

    static List<Seat> seats(Site s) {
        return SEATS.computeIfAbsent(s.id(), id -> {
            List<Seat> out = new ArrayList<>();
            int[][] tables = switch (s.kind()) {
                case CAFE -> CAFE_TABLES;
                case BEACH -> BEACH_TABLES;
                case LOBBY -> lobbyTables(lobbyW());
                default -> new int[0][];
            };
            int y = Island.G + 1;
            for (int[] t : tables)
                for (int sd = -1; sd <= 1; sd += 2) {
                    int lx = t[0] + sd, rz = s.kind() == Kind.LOBBY ? t[1] - 2 : t[1] + 2;
                    float yawLocal = sd < 0 ? -90 : 90; // сидит лицом к столу: запад → смотрит на восток (−90)
                    Vec3d at, ap;
                    if (s.kind() == Kind.LOBBY) {
                        at = new Vec3d(s.cx() + lx + 0.5, y, s.cz() + t[1] + 0.5);
                        ap = new Vec3d(s.cx() + lx + 0.5, y, s.cz() + rz + 0.5);
                        out.add(new Seat(at, yawLocal, ap));
                    } else {
                        at = toWorld(s, lx, t[1], y);
                        ap = toWorld(s, lx, rz, y);
                        out.add(new Seat(at, yaw(s, yawLocal), ap));
                    }
                }
            return out;
        });
    }

    static int lobbyW() {
        int[] in = Buildings.info(Island.Zone.DOWNTOWN, LOBBY_PX, LOBBY_PZ);
        return in[1] - 1;
    }

    /** Вход: снаружи, внутри, у стойки (где заказывают). */
    static Vec3d[] entry(Site s) {
        int y = Island.G + 1;
        return switch (s.kind()) {
            case LOBBY -> {
                int half = lobbyW() + 1;
                yield new Vec3d[]{new Vec3d(s.cx() + 1.0, y, s.cz() + half + 2.5), new Vec3d(s.cx() + 1.0, y, s.cz() + half - 2.5),
                        new Vec3d(s.cx() - 5.5, y, s.cz() + 1.5)};
            }
            case BEACH -> new Vec3d[]{toWorld(s, 0, -12, y), toWorld(s, 0, -6, y), toWorld(s, 0, 2, y)};
            default -> new Vec3d[]{toWorld(s, -0.5, -14, y), toWorld(s, -0.5, -10, y), toWorld(s, 0, 4.5, y)};
        };
    }

    /** Центральный проход (точка ряда подхода к столику). */
    static Vec3d aisle(Site s, Seat seat) {
        if (s.kind() == Kind.LOBBY) return new Vec3d(s.cx() + 1.0, seat.approach().y, seat.approach().z);
        int[] l = toLocal(s, (int) Math.floor(seat.approach().x), (int) Math.floor(seat.approach().z));
        return toWorld(s, 0, l[1], seat.approach().y);
    }

    private static final class Visit {
        final Site site;
        final Seat seat;
        int stage;
        long t, since;

        Visit(Site site, Seat seat, long t) {
            this.site = site;
            this.seat = seat;
            this.t = t;
        }
    }

    private static final Map<UUID, Visit> VISITS = new HashMap<>();

    @Nullable
    private static Seat freeSeat(ServerWorld w, Site s, Random r) {
        List<Seat> all = new ArrayList<>(seats(s));
        java.util.Collections.shuffle(all, new java.util.Random(r.nextLong()));
        for (Seat seat : all) {
            boolean taken = VISITS.values().stream().anyMatch(v -> v.seat == seat);
            if (taken) continue;
            BlockPos b = BlockPos.ofFloored(seat.at());
            if (!(w.getBlockState(b).getBlock() instanceof StairsBlock)) continue;
            if (!w.getEntitiesByClass(net.minecraft.entity.Entity.class, new Box(b).expand(0.2, 1, 0.2),
                    e -> e instanceof CitizenEntity || e instanceof SeatEntity).isEmpty()) continue;
            return seat;
        }
        return null;
    }

    /** Голодный прохожий: если рядом кафе со свободным столиком — идёт туда (половина случаев). */
    static boolean goEat(ServerWorld w, CitizenEntity c, LifeAi.Brain b) {
        Random r = c.getRandom();
        if (r.nextInt(5) < 2) return false;
        Site best = null;
        double bd = 72 * 72;
        for (Site s : SITES) {
            if (s.kind() == Kind.MARKET) continue;
            double d = c.squaredDistanceTo(s.cx() + 0.5, c.getY(), s.cz() + 0.5);
            if (d < bd && built(w, s)) {
                bd = d;
                best = s;
            }
        }
        if (best == null) return false;
        final Site site = best;
        if (VISITS.values().stream().filter(v -> v.site == site).count() >= 6) return false;
        Seat seat = freeSeat(w, site, r);
        if (seat == null) return false;
        Vec3d[] e = entry(site);
        Visit v = new Visit(site, seat, Long.MAX_VALUE);
        v.since = w.getTime();
        VISITS.put(c.getUuid(), v);
        b.doing = "cafe";
        c.lifeHold = 100000;
        LifeAi.go(w, c, e[0], null, ItemStack.EMPTY, () -> {
            LifeAi.openDoor(w, e[0], e[1]);
            c.walkRoute(List.of(e[1], e[2]), 0.45, null, ItemStack.EMPTY, () -> {
                c.faceTo(e[2].add(0, 0, 0));
                c.busy(Activity.THINK, 50, null, ItemStack.EMPTY);
                v.t = w.getTime() + 50;
            });
        });
        return true;
    }

    private static void visits(ServerWorld w, long now) {
        if (VISITS.isEmpty()) return;
        for (var it = VISITS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Visit v = en.getValue();
            if (!(w.getEntity(en.getKey()) instanceof CitizenEntity c) || !c.isAlive() || !c.calm()) {
                if (w.getEntity(en.getKey()) instanceof CitizenEntity c2) c2.lifeHold = 0;
                it.remove();
                continue;
            }
            if (v.stage == 0 && v.t == Long.MAX_VALUE && now - v.since > 20 * 100) { // не дошёл за 100 с — передумал
                c.lifeHold = 0;
                LifeAi.brain(c).doing = "";
                it.remove();
                continue;
            }
            if (v.stage == 1 && now - v.since > 20 * 40) { // застрял между столиками — просто садится
                ItemStack food = c.getMainHandStack().isEmpty() ? new ItemStack(ModContent.BURGER) : c.getMainHandStack();
                c.stopRoute();
                LifeAi.sit(c, new LifeAi.BenchSpot(v.seat.at(), v.seat.yaw()), Activity.SIT_EAT, food, 400);
                v.stage = 2;
                v.t = now + 420;
                continue;
            }
            if (now < v.t) continue;
            Random r = c.getRandom();
            if (v.stage == 0) { // заказал — с едой к столику
                v.stage = 1;
                v.t = Long.MAX_VALUE;
                v.since = now;
                ItemStack food = new ItemStack(r.nextInt(3) == 0 ? ModContent.PIZZA : r.nextBoolean() ? ModContent.BURGER : ModContent.SALAD);
                c.walkRoute(List.of(aisle(v.site, v.seat), v.seat.approach()), 0.45, null, food, () -> {
                    LifeAi.sit(c, new LifeAi.BenchSpot(v.seat.at(), v.seat.yaw()), Activity.SIT_EAT, food, 360 + r.nextInt(300));
                    v.stage = 2;
                    v.t = w.getTime() + 380 + r.nextInt(300);
                });
            } else if (v.stage == 2) { // поел — к выходу
                v.stage = 3;
                v.t = Long.MAX_VALUE;
                Vec3d[] e = entry(v.site);
                c.refreshPositionAndAngles(v.seat.approach().x, v.seat.approach().y, v.seat.approach().z, c.getYaw(), 0);
                c.walkRoute(List.of(aisle(v.site, v.seat), e[1], e[0]), 0.5, null, ItemStack.EMPTY, () -> {
                    LifeAi.openDoor(w, e[0], e[1]);
                    c.lifeHold = 0;
                    LifeAi.Brain b = LifeAi.brain(c);
                    b.hunger = 0;
                    b.doing = "";
                    VISITS.remove(c.getUuid());
                });
            }
        }
        // зависшие визиты (не дошёл за 2 минуты) — отпускаем
        if (now % 600 == 4) VISITS.entrySet().removeIf(en -> {
            if (w.getEntity(en.getKey()) instanceof CitizenEntity c && c.age % 2400 == 0) {
                c.lifeHold = 0;
                return true;
            }
            return false;
        });
    }

    /** Автотест: сколько прохожих сейчас в кафе (по стадиям). */
    public static String debug() {
        int[] st = new int[4];
        for (Visit v : VISITS.values()) st[Math.min(3, v.stage)]++;
        return "в кафе: идут " + st[0] + ", к столу " + st[1] + ", едят " + st[2] + ", уходят " + st[3];
    }

    /** Автотест: всех прохожих рядом — в ближайшее кафе. */
    public static int debugFeed(ServerWorld w, Vec3d at) {
        int n = 0;
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, new Box(at, at).expand(60), x -> x.lifeManaged && x.calm() && !VISITS.containsKey(x.getUuid()))) {
            LifeAi.Brain b = LifeAi.brain(c);
            c.stopRoute();
            for (int k = 0; k < 5 && !VISITS.containsKey(c.getUuid()); k++) goEat(w, c, b);
            if (VISITS.containsKey(c.getUuid())) n++;
            if (n >= 6) break;
        }
        return n;
    }
}
