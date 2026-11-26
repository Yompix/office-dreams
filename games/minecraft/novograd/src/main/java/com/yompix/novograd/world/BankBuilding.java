package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

/**
 * 0.9.8: «Сити-Банк» — настоящий городской банк на участке «bank_tower» (деловой центр, вход с юга).
 * <ul>
 *   <li>Снаружи: подиум со ступенями, портик из шести колонн, антаблемент и фронтон, стеклянный фасад, двери из меди,
 *       ниша с двумя банкоматами в стене (под навесом портика), вывеска.</li>
 *   <li>Внутри: зал с мраморным полом, стойка кассиров со стеклом и окошками, стойки очереди, кресла ожидания,
 *       стеклянный кабинет кредитного менеджера (ПКМ — «Кредит»), круглая дверь хранилища, камеры, растения, свет.</li>
 *   <li>Люди (кассиры, менеджер, охрана) и вывески появляются, когда рядом игрок.</li>
 *   <li>Старые миры: участок перестраивается при загрузке, только если там стоит нетронутая прежняя башня
 *       (каждый столбец совпадает с тем, что строил генератор 0.9.7); есть правки игроков — не трогаем.</li>
 * </ul>
 * Координаты: lx, lz — от центра участка (−24..23), пол зала — s+1 (подиум), вход — lz = +13.
 */
public final class BankBuilding {
    private BankBuilding() {}

    /** Участок банка (как Island.place("bank_tower") = 480, −317): px, pz. */
    public static final long PX = Math.floorDiv(480 + 24, 48), PZ = Math.floorDiv(-317 + 24, 48);
    static final int HX = 15, HZ = 13;
    /** Включает прежнюю генерацию (для сравнения со старым миром). */
    static final ThreadLocal<Boolean> OLD = ThreadLocal.withInitial(() -> false);

    public static boolean lot(long px, long pz) {
        return px == PX && pz == PZ && !OLD.get();
    }

    private static BlockState st(Block b) {
        return b.getDefaultState();
    }

    private static BlockState stairs(Block b, Direction back, boolean top) {
        return b.getDefaultState().with(StairsBlock.FACING, back).with(StairsBlock.HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
    }

    private static BlockState slab(Block b, boolean top) {
        return b.getDefaultState().with(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    private static BlockState facing(Block b, Direction d) {
        return b.getDefaultState().with(Properties.HORIZONTAL_FACING, d);
    }

    private static void door(Island.Column c, int y, Block b, Direction outward, DoorHinge hinge) {
        BlockState d = b.getDefaultState().with(DoorBlock.FACING, outward).with(DoorBlock.HINGE, hinge).with(DoorBlock.OPEN, false);
        c.set(y, d.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        c.set(y + 1, d.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static final BlockState AIR = st(Blocks.AIR), QUARTZ = st(Blocks.SMOOTH_QUARTZ), QBRICK = st(Blocks.QUARTZ_BRICKS),
            PILLAR = st(Blocks.QUARTZ_PILLAR), GLASS = st(Blocks.LIGHT_GRAY_STAINED_GLASS), TINT = st(Blocks.GRAY_STAINED_GLASS),
            LIGHT = st(Blocks.SEA_LANTERN), DARK = st(Blocks.POLISHED_DEEPSLATE), OAK = st(Blocks.DARK_OAK_PLANKS);

    // ================================================================== генерация столбца
    /** Столбец участка банка. */
    static void column(int lx, int lz, int s, Island.Column c) {
        int ax = Math.abs(lx);
        int F = s + 1; // пол зала (подиум)
        boolean in = ax <= HX && lz >= -HZ && lz <= HZ;
        if (!in) {
            plaza(lx, lz, s, c, F);
            return;
        }
        c.set(s, QUARTZ);
        c.set(F, floor(lx, lz));
        boolean wallX = ax == HX, back = lz == -HZ, front = lz == HZ;
        int top = F + 9; // потолок зала
        if (wallX || back || front) {
            wall(lx, lz, F, top, c, wallX, back, front);
        } else {
            for (int y = F + 1; y < top; y++) c.set(y, AIR);
            interior(lx, lz, F, c);
            c.set(top, ceiling(lx, lz));
        }
        roofAndTower(lx, lz, F, top, c);
    }

    private static BlockState floor(int lx, int lz) {
        if (lz <= -4) return OAK; // за стойкой — паркет
        if (Math.floorMod(lx, 4) == 0 || Math.floorMod(lz, 4) == 1) return st(Blocks.POLISHED_ANDESITE);
        return ((Math.floorDiv(lx, 4) + Math.floorDiv(lz, 4)) & 1) == 0 ? st(Blocks.POLISHED_DIORITE) : QUARTZ;
    }

    private static BlockState ceiling(int lx, int lz) {
        return Math.floorMod(lx, 4) == 2 && Math.floorMod(lz, 4) == 2 ? LIGHT : QUARTZ;
    }

    private static void wall(int lx, int lz, int F, int top, Island.Column c, boolean wallX, boolean back, boolean front) {
        int ax = Math.abs(lx);
        for (int y = F + 1; y <= top; y++) c.set(y, QBRICK);
        if (front) {
            // стеклянный фасад в квадратной раме, двери из меди в центре, ниша банкоматов слева
            for (int y = F + 1; y <= F + 7; y++) c.set(y, ax % 4 == 3 ? PILLAR : GLASS);
            c.set(F + 8, QUARTZ);
            if (lx == -1) door(c, F + 1, Blocks.WAXED_COPPER_DOOR, Direction.SOUTH, DoorHinge.LEFT);
            if (lx == 0) door(c, F + 1, Blocks.WAXED_COPPER_DOOR, Direction.SOUTH, DoorHinge.RIGHT);
            if (lx >= -1 && lx <= 0) c.set(F + 3, st(Blocks.WAXED_COPPER_GRATE));
            if (lx == -13 || lx == -10) {
                c.set(F + 1, facing(ModContent.ATM_WALL, Direction.SOUTH));
                c.set(F + 2, AIR); // верх банкомата (модель выше блока)
                c.set(F + 3, QUARTZ);
            }
            if (lx == -12 || lx == -11 || lx == -14 || lx == -9) for (int y = F + 1; y <= F + 3; y++) c.set(y, st(Blocks.POLISHED_ANDESITE));
            return;
        }
        if (wallX && lz != -HZ && lz != HZ) {
            // боковые окна между пилястрами
            if (Math.floorMod(lz, 4) == 0) for (int y = F + 1; y <= top; y++) c.set(y, PILLAR);
            else for (int y = F + 3; y <= F + 7; y++) c.set(y, TINT);
        }
        if (back) {
            if (Math.floorMod(lx, 4) == 0) for (int y = F + 1; y <= top; y++) c.set(y, PILLAR);
        }
    }

    /** Мебель и всё внутри. */
    private static void interior(int lx, int lz, int F, Island.Column c) {
        int ax = Math.abs(lx);
        // --- хранилище: стена lz = −9, круглая дверь по центру, за ней золото
        if (lz == -9) {
            for (int y = F + 1; y <= F + 8; y++) c.set(y, st(Blocks.POLISHED_BLACKSTONE_BRICKS));
            if (ax <= 2) for (int y = F + 1; y <= F + 5; y++) {
                int dy = y - (F + 3);
                double r = Math.sqrt(lx * lx + dy * dy);
                c.set(y, r > 2.2 ? st(Blocks.POLISHED_BLACKSTONE_BRICKS) : r > 1.4 ? st(Blocks.IRON_BLOCK) : r > 0.5 ? st(Blocks.SMOOTH_STONE) : st(Blocks.CHISELED_POLISHED_BLACKSTONE));
            }
            return;
        }
        if (lz < -9) {
            c.set(F, st(Blocks.POLISHED_BLACKSTONE));
            if (lz == -10 && ax <= 10 && ax % 3 == 0) c.set(F + 1, st(Blocks.GOLD_BLOCK));
            if (lz == -11 && ax <= 10 && ax % 3 == 1) {
                c.set(F + 1, st(Blocks.GOLD_BLOCK));
                c.set(F + 2, st(Blocks.GOLD_BLOCK));
            }
            if (lz == -12 && ax <= 12) c.set(F + 1, st(Blocks.IRON_BARS));
            return;
        }
        // --- стойка кассиров: lz = −3, стекло; окошки на lx −8, −4, 0, 4, 8
        if (lz == -3 && ax <= 11) {
            c.set(F + 1, ax == 11 ? QBRICK : OAK);
            boolean window = ax % 4 == 0;
            c.set(F + 2, window ? slab(Blocks.DARK_OAK_SLAB, false) : st(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE));
            c.set(F + 3, st(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE));
            c.set(F + 4, slab(Blocks.DARK_OAK_SLAB, false));
            if (ax == 11) for (int y = F + 2; y <= F + 4; y++) c.set(y, QBRICK);
            return;
        }
        // столы кассиров за стойкой
        if (lz == -5 && ax <= 10 && ax % 4 == 0) {
            c.set(F + 1, OAK);
            c.set(F + 2, facing(ModContent.COMPUTER, Direction.SOUTH));
            return;
        }
        if (lz == -6 && ax <= 10 && ax % 4 == 0) {
            c.set(F + 1, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, false));
            return;
        }
        if (lz < -3) return;
        // --- стойки очереди (столбики) и ковровая дорожка к окошкам
        if (lz == 0 && ax <= 7 && ax % 2 == 1) {
            c.set(F + 1, st(Blocks.LIGHTNING_ROD));
            return;
        }
        if (lz == -1 && ax <= 8) c.set(F + 1, st(Blocks.RED_CARPET));
        if ((lz == 1 || lz == 2) && ax <= 1) c.set(F + 1, st(Blocks.RED_CARPET));
        // --- зал ожидания: кресла вдоль западной стены, столик
        if (lx == -13 && lz >= 1 && lz <= 9) {
            c.set(F + 1, lz == 5 ? st(Blocks.POTTED_FLOWERING_AZALEA_BUSH) : stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST, false));
            return;
        }
        if (lx == -11 && (lz == 3 || lz == 7)) {
            c.set(F + 1, st(Blocks.SPRUCE_FENCE));
            c.set(F + 2, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
            return;
        }
        // --- кабинет кредитного менеджера (восток): стекло, дверь, стол, кресла
        if (lx >= 8 && lx <= 14 && lz >= 3 && lz <= 11) {
            boolean wallW = lx == 8, wallN = lz == 3, wallS = lz == 11;
            if (wallW || wallN || wallS) {
                if (wallW && lz == 7) {
                    door(c, F + 1, Blocks.WAXED_COPPER_DOOR, Direction.WEST, DoorHinge.LEFT);
                    for (int y = F + 3; y <= F + 4; y++) c.set(y, st(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE));
                    c.set(F + 5, QUARTZ);
                    return;
                }
                c.set(F + 1, QUARTZ);
                for (int y = F + 2; y <= F + 4; y++) c.set(y, st(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE));
                c.set(F + 5, QUARTZ);
                return;
            }
            c.set(F + 1, st(Blocks.GRAY_CARPET));
            if (lx == 12 && lz >= 6 && lz <= 8) {
                c.set(F + 1, OAK);
                if (lz == 7) c.set(F + 2, facing(ModContent.COMPUTER, Direction.EAST));
                if (lz == 6) c.set(F + 2, st(Blocks.POTTED_CACTUS));
            }
            if (lx == 13 && lz == 7) c.set(F + 1, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false)); // кресло менеджера
            if (lx == 10 && (lz == 6 || lz == 8)) c.set(F + 1, stairs(Blocks.DARK_OAK_STAIRS, Direction.WEST, false)); // кресла клиента
            if (lx == 13 && lz == 4) {
                c.set(F + 1, st(Blocks.BOOKSHELF));
                c.set(F + 2, st(Blocks.BOOKSHELF));
            }
            if (lx == 13 && lz == 10) c.set(F + 1, st(Blocks.POTTED_BAMBOO));
            return;
        }
        // --- растения по углам зала, банкомат в зале, огнетушитель
        if ((ax == 13 && (lz == 12 || lz == -2)) || (lx == 7 && lz == 12)) c.set(F + 1, st(Blocks.POTTED_FLOWERING_AZALEA_BUSH));
        if (lx == 13 && lz == 0) c.set(F + 1, facing(ModContent.ATM, Direction.WEST));
        // --- камеры под потолком
        if ((ax == 14 && (lz == 12 || lz == -8)) && true) c.set(F + 8, Blocks.OBSERVER.getDefaultState().with(Properties.FACING, lx > 0 ? Direction.EAST : Direction.WEST));
        // --- люстры над залом ожидания
        if ((lx == -6 || lx == 2) && (lz == 6)) {
            c.set(F + 8, st(Blocks.CHAIN));
            c.set(F + 7, st(Blocks.LANTERN).with(LanternBlock.HANGING, true));
        }
    }

    private static void roofAndTower(int lx, int lz, int F, int top, Island.Column c) {
        int ax = Math.abs(lx);
        // крыша зала и парапет
        c.set(top + 1, ax == HX || lz == -HZ || lz == HZ ? QBRICK : st(Blocks.SMOOTH_STONE));
        if (ax == HX || lz == -HZ) c.set(top + 2, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
        // световой короб на крыше над входом и антенна
        if (lz == HZ - 1 && ax <= 6) c.set(top + 2, ax == 6 ? QBRICK : st(Blocks.GREEN_STAINED_GLASS));
    }

    /** Площадь перед банком: ступени, портик с колоннами, фронтон, фонари и клумбы. */
    private static void plaza(int lx, int lz, int s, Island.Column c, int F) {
        int ax = Math.abs(lx);
        c.set(s, (ax + lz) % 6 == 0 ? st(Blocks.POLISHED_ANDESITE) : st(Blocks.SMOOTH_STONE));
        if (ax <= HX && lz > HZ && lz <= HZ + 3) {
            // портик: пол (подиум), колонны, антаблемент, фронтон
            int top = F + 9;
            boolean steps = lz == HZ + 3;
            c.set(F, steps ? stairs(Blocks.QUARTZ_STAIRS, Direction.NORTH, false) : QUARTZ);
            if (lz == HZ + 2 && (ax == 13 || ax == 9 || ax == 5)) for (int y = F + 1; y <= F + 8; y++) c.set(y, y == F + 8 ? st(Blocks.CHISELED_QUARTZ_BLOCK) : PILLAR);
            if (!steps) {
                c.set(top, lz == HZ + 1 && (ax % 4 == 2) ? LIGHT : QUARTZ);
                c.set(top + 1, lz == HZ + 2 ? st(Blocks.POLISHED_DEEPSLATE) : QUARTZ);
            } else {
                c.set(top, slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
                c.set(top + 1, QUARTZ);
            }
            // фронтон: треугольник над антаблементом
            for (int k = 0; k < 4; k++) {
                int half = HX - 4 * k - 1;
                if (ax <= half) c.set(top + 2 + k, ax >= half - 1 ? stairs(Blocks.QUARTZ_STAIRS, lx > 0 ? Direction.WEST : Direction.EAST, false) : QUARTZ);
            }
            return;
        }
        // клумбы вдоль фасада и фонари
        if (lz == HZ + 5 && ax >= 6 && ax <= 14) {
            c.set(s + 1, ax % 4 == 2 ? st(Blocks.AZALEA_LEAVES).with(Properties.PERSISTENT, true) : st(Blocks.MOSS_BLOCK));
        }
        if (lz == HZ + 5 && (ax == 16 || ax == 4)) {
            c.set(s + 1, ModContent.LAMP_POST.getDefaultState());
            c.set(s + 2, ModContent.LAMP_POST.getDefaultState());
            c.set(s + 3, ModContent.STREET_LAMP.getDefaultState().with(net.minecraft.block.HorizontalFacingBlock.FACING, Direction.SOUTH));
        }
        if (ax == HX + 2 && lz >= -HZ && lz <= HZ && Math.floorMod(lz, 6) == 0) c.set(s + 1, st(Blocks.POTTED_BAMBOO));
    }

    // ================================================================== люди и вывески
    static final String TAG = "nv_bank";

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BankBuilding::tick);
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || hand != net.minecraft.util.Hand.MAIN_HAND || !(player instanceof ServerPlayerEntity p) || !entity.getCommandTags().contains(TAG + ":loan"))
                return net.minecraft.util.ActionResult.PASS;
            com.yompix.novograd.life.LifeNet.send(p, "open_loan", new NbtCompound()); // кредитный менеджер — сразу «Кредит»
            com.yompix.novograd.gta.Loans.send(p);
            return net.minecraft.util.ActionResult.SUCCESS;
        });
    }

    /** Центр участка (мир). */
    static int cx() {
        return (int) PX * 48;
    }

    static int cz() {
        return (int) PZ * 48;
    }

    private static void tick(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        if (w == null || !(w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) return;
        State st = state(server);
        if (Boolean.getBoolean("novograd.nobank")) return; // для снимков «до»
        if (!st.built && !st.skipped) {
            if (st.col >= 0) Upgrade.rebuildStep(w, st);
            else if (server.getTicks() % 20 == 3 || Upgrade.scanning()) Upgrade.tick(w);
            return;
        }
        if (server.getTicks() % 100 != 37 || !st.built) return;
        int x = cx(), z = cz();
        boolean near = false;
        for (ServerPlayerEntity p : w.getPlayers()) near |= p.squaredDistanceTo(x, Island.G, z) < 96 * 96;
        if (!near || !w.isChunkLoaded(BlockPos.ofFloored(x, Island.G, z))) return;
        int F = Island.surface(x, z) + 1;
        Box box = new Box(x - 30, F - 4, z - 30, x + 30, F + 60, z + 30);
        if (w.getEntitiesByClass(DisplayEntity.TextDisplayEntity.class, box, e -> e.getCommandTags().contains(TAG)).isEmpty()) {
            label(w, new Vec3d(x + 0.5, F + 9.85, z + HZ + 4.06), Text.literal("СИТИ-БАНК").formatted(Formatting.DARK_GREEN, Formatting.BOLD), 3.0f, 0x00000000);
            label(w, new Vec3d(x - 11 + 0.5, F + 4.3, z + HZ + 0.9), Text.literal("БАНКОМАТЫ 24/7").formatted(Formatting.GREEN), 0.9f, 0xAA101010);
            label(w, new Vec3d(x + 8.5, F + 5.8, z + 7.5), Text.literal("КРЕДИТЫ").formatted(Formatting.GOLD, Formatting.BOLD), 1.1f, 0xAA101010);
            label(w, new Vec3d(x + 0.5, F + 5.4, z - 3.5), Text.literal("КАССЫ").formatted(Formatting.WHITE), 1.1f, 0xAA101010);
        }
        staff(w, x, z, F, box);
    }

    private static void label(ServerWorld w, Vec3d at, Text text, float scale, int bg) {
        DisplayEntity.TextDisplayEntity d = EntityType.TEXT_DISPLAY.create(w);
        if (d == null) return;
        NbtCompound n = new NbtCompound();
        n.putString("billboard", "fixed");
        n.putString("alignment", "center");
        n.putInt("background", bg);
        n.putInt("line_width", 400);
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
        d.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0); // yaw 0 — текст смотрит на юг (к площади)
        d.addCommandTag(TAG);
        w.spawnEntity(d);
    }

    private static void staff(ServerWorld w, int x, int z, int F, Box box) {
        if (!w.getEntitiesByClass(CitizenEntity.class, box, e -> e.getCommandTags().contains(TAG + ":staff")).isEmpty()) return;
        String[] tellers = {"Кассир Алла", "Кассир Денис", "Кассир Ирина"};
        int[] tx = {-8, 0, 8};
        for (int i = 0; i < 3; i++) spawn(w, x + tx[i] + 0.5, F + 1, z - 4 + 0.5, 0, CitizenEntity.Role.BANKER, tellers[i], "staff");
        CitizenEntity m = spawn(w, x + 13 + 0.5, F + 1, z + 7 + 0.5, 90, CitizenEntity.Role.BANKER, "Кредитный менеджер Олег", "staff");
        if (m != null) m.addCommandTag(TAG + ":loan");
        spawn(w, x - 3 + 0.5, F + 1, z + 11 + 0.5, 180, CitizenEntity.Role.CITIZEN, "Охрана банка", "staff");
    }

    private static CitizenEntity spawn(ServerWorld w, double x, double y, double z, float yaw, CitizenEntity.Role role, String name, String kind) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        c.setup(CitizenEntity.RANDOM_FROM + Math.floorMod(name.hashCode(), 97), role, true, name);
        c.refreshPositionAndAngles(x, y, z, yaw, 0);
        c.setHeadYaw(yaw);
        c.setBodyYaw(yaw);
        c.addCommandTag(TAG + ":" + kind);
        w.spawnEntity(c);
        return c;
    }

    // ================================================================== старые миры: безопасная замена башни
    static final class State extends PersistentState {
        /** Банк стоит (новый мир или перестроили); skipped — в старом мире участок изменён игроками, не трогаем. */
        boolean built, skipped;
        int col = -1;

        @Override
        public NbtCompound writeNbt(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            n.putBoolean("built", built);
            n.putBoolean("skipped", skipped);
            n.putInt("col", col);
            return n;
        }

        static State read(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            State s = new State();
            s.built = n.getBoolean("built");
            s.skipped = n.getBoolean("skipped");
            s.col = n.contains("col") ? n.getInt("col") : -1;
            return s;
        }
    }

    private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::read, null);

    /** Для автотеста: «built», «skipped» или «pending N%». */
    public static String status(MinecraftServer s) {
        State st = state(s);
        return st.built ? "built" : st.skipped ? "skipped" : st.col >= 0 ? "rebuild " + st.col * 100 / (48 * 48) + "%" : "pending";
    }

    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_bank098");
    }

    /** Проверка «участок не трогали» и перестройка — по кусочку за такт, только когда весь участок загружен. */
    static final class Upgrade {
        static final int X0 = cx() - 24, Z0 = cz() - 24, N = 48;
        static int y0() {
            return Island.G - 4;
        }

        static int y1() {
            return Island.G + 140;
        }

        static boolean loaded(ServerWorld w) {
            for (int x = X0; x < X0 + N; x += 16)
                for (int z = Z0; z < Z0 + N; z += 16)
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) return false;
            return w.isChunkLoaded((X0 + N - 1) >> 4, (Z0 + N - 1) >> 4);
        }

        private static int scanned, intact, edited;

        static void tick(ServerWorld w) {
            State st = state(w.getServer());
            if (st.built || st.skipped) return;
            if (!loaded(w)) return;
            // новый мир (или уже новый банк в чанке): центральный столбец совпадает с новой генерацией — готово
            if (matches(w, cx(), cz() - 5, false) && matches(w, cx() - 13, cz() + 13, false)) {
                st.built = true;
                st.markDirty();
                Novograd.LOG.info("[банк] новый «Сити-Банк» уже на месте");
                return;
            }
            if (scanned == 0) {
                intact = 0;
                edited = 0;
            }
            long t0 = System.nanoTime();
            while (scanned < N * N && System.nanoTime() - t0 < 4_000_000) {
                int x = X0 + scanned % N, z = Z0 + scanned / N;
                if (matches(w, x, z, true) || matches(w, x, z, false)) intact++;
                else edited++;
                scanned++;
            }
            if (scanned < N * N) return;
            scanned = 0;
            if (edited > N * N / 50) { // правки игроков (больше 2 % столбцов) — участок не трогаем
                st.skipped = true;
                st.markDirty();
                Novograd.LOG.info("[банк] участок изменён ({} столбцов) — старая башня остаётся", edited);
                return;
            }
            st.col = 0;
            st.markDirty();
            Novograd.LOG.info("[банк] старая башня нетронута ({} из {}) — строим новый «Сити-Банк»", intact, N * N);
        }

        static boolean scanning() {
            return scanned > 0;
        }

        /** Сравнить столбец мира с генерацией (old — как в 0.9.7); блоки — по виду (двери открыты и т. п. не в счёт). */
        static boolean matches(ServerWorld w, int x, int z, boolean old) {
            BlockState[] col = column(x, z, old);
            BlockPos.Mutable m = new BlockPos.Mutable();
            int bad = 0;
            for (int y = y0(); y <= y1(); y++) {
                BlockState want = col[y + 64];
                BlockState have = w.getBlockState(m.set(x, y, z));
                if (have.getBlock() instanceof com.yompix.novograd.block.PropBlock || have.isOf(ModContent.LAMP_POST) || have.isOf(ModContent.STREET_LAMP)) continue; // уличные мелочи (Props) ставит не генератор
                if (want == null ? !have.isAir() && !(have.getBlock() instanceof net.minecraft.block.FluidBlock) : have.getBlock() != want.getBlock()) {
                    if (++bad > 2) return false;
                }
            }
            return true;
        }

        static BlockState[] column(int x, int z, boolean old) {
            BlockState[] col = new BlockState[400];
            OLD.set(old);
            try {
                Island.column(x, z, (y, s) -> {
                    if (y >= -64 && y < 320) col[y + 64] = s.isAir() ? null : s;
                });
            } finally {
                OLD.set(false);
            }
            return col;
        }

        /** Перестройка по столбцам: не дольше 4 мс за такт (без рывков), выгрузился участок — ждём. */
        static void rebuildStep(ServerWorld w, State st) {
            if (!loaded(w)) return;
            BlockPos.Mutable m = new BlockPos.Mutable();
            int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
            long t0 = System.nanoTime();
            for (; st.col < N * N && System.nanoTime() - t0 < 4_000_000; st.col++) {
                int x = X0 + st.col % N, z = Z0 + st.col / N;
                BlockState[] col = column(x, z, false);
                for (int y = y0(); y <= y1(); y++) {
                    BlockState want = col[y + 64] == null ? AIR : col[y + 64];
                    if (w.getBlockState(m.set(x, y, z)) != want) w.setBlockState(m, want, flags);
                }
            }
            st.markDirty();
            if (st.col < N * N) return;
            st.built = true;
            // жильцы старой башни: их квартир больше нет — уйдут сами (Residents), лишних в здании убрать
            Box box = new Box(X0, y0(), Z0, X0 + N, y1(), Z0 + N);
            for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, box, e -> !e.getCommandTags().contains(TAG + ":staff") && e.getY() > Island.G + 12)) c.discard();
            Novograd.LOG.info("[банк] «Сити-Банк» построен");
        }
    }
}
