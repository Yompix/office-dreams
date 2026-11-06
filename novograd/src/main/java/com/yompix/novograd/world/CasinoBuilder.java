package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.block.NeonBlock;
import com.yompix.novograd.entity.CitizenEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Игровой зал казино «Золотая корона» на Ривьере. Остров генерирует только коробку здания
 * ({@link Island} — resort), поэтому, когда игрок впервые подходит к точке «riv_casino» ближе 60 блоков,
 * первый этаж ближайшего здания превращается в зал в два этажа высотой: ряды автоматов, рулетки, карточные столы,
 * бар, касса, люстры и неон, крупье и дилеры (у них — «игровой стол» с выбором игры).
 * Строится только внутри стен здания (улицы и тротуары не трогаем) и один раз — отметка в своём PersistentState.
 */
public final class CasinoBuilder {
    private CasinoBuilder() {}

    public static final String PLACE = "riv_casino";

    /** Отметка «зал уже построен» (и где), хранится в мире. */
    public static final class State extends PersistentState {
        public boolean built;
        public BlockPos hall;

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            nbt.putBoolean("built", built);
            if (hall != null) nbt.putLong("hall", hall.asLong());
            return nbt;
        }

        static State read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            State s = new State();
            s.built = nbt.getBoolean("built");
            if (nbt.contains("hall")) s.hall = BlockPos.fromLong(nbt.getLong("hall"));
            return s;
        }
    }

    private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::read, DataFixTypes.LEVEL);

    public static State state(ServerWorld w) {
        return w.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_casino_hall");
    }

    private static boolean failedOnce;

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            if (w.getTime() % 40 != 21 || w.getRegistryKey() != World.OVERWORLD) return;
            if (!(w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) return;
            double[] pl = Island.place(PLACE);
            if (pl == null) return;
            State st = state(w);
            if (st.built || failedOnce) return;
            for (ServerPlayerEntity p : w.getPlayers()) {
                if (p.squaredDistanceTo(pl[0], p.getY(), pl[2]) > 60 * 60) continue;
                try {
                    if (build(w, (int) Math.floor(pl[0]), (int) Math.floor(pl[2]))) return;
                } catch (Exception ex) {
                    failedOnce = true;
                    Novograd.LOG.warn("Зал казино на Ривьере не построен", ex);
                }
                return;
            }
        });
    }

    /** Найти здание у точки и обставить. false — чанки ещё не загружены (попробуем позже). */
    public static boolean build(ServerWorld w, int px, int pz) {
        // ближайшие центры участков (сетка 48, центр участка — кратен 48)
        int[][] cand = new int[4][];
        int bx = Math.floorDiv(px, 48) * 48, bz = Math.floorDiv(pz, 48) * 48, k = 0;
        for (int dx = 0; dx <= 48; dx += 48) for (int dz = 0; dz <= 48; dz += 48) cand[k++] = new int[]{bx + dx, bz + dz};
        java.util.Arrays.sort(cand, java.util.Comparator.comparingDouble(c -> Math.hypot(c[0] - px, c[1] - pz)));
        for (int[] c : cand) {
            if (Island.zone(c[0], c[1]) != Island.Zone.RIVIERA) continue;
            int s = Island.surface(c[0], c[1]);
            if (!loaded(w, c[0], c[1], 20)) return false;
            int half = wallDist(w, c[0], s + 1, c[1]);
            if (half < 8) continue;
            furnish(w, c[0], s, c[1], half);
            State st = state(w);
            st.built = true;
            st.hall = new BlockPos(c[0], s + 1, c[1]);
            st.markDirty();
            Novograd.LOG.info("Зал казино «Золотая корона» построен у {} {} (полуширина {})", c[0], c[1], half);
            return true;
        }
        failedOnce = true;
        Novograd.LOG.warn("Зал казино: рядом с {} {} не нашлось здания Ривьеры", px, pz);
        return true;
    }

    private static boolean loaded(ServerWorld w, int x, int z, int r) {
        for (int cx = (x - r) >> 4; cx <= (x + r) >> 4; cx++)
            for (int cz = (z - r) >> 4; cz <= (z + r) >> 4; cz++)
                if (!w.isChunkLoaded(ChunkPos.toLong(cx, cz))) return false;
        return true;
    }

    /** Расстояние от центра до стены (одинаковое во все стороны) или −1, если это не коробка здания. */
    private static int wallDist(ServerWorld w, int cx, int y, int cz) {
        int[] d = new int[4];
        Direction[] dirs = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
        for (int i = 0; i < 4; i++) {
            d[i] = -1;
            if (i == 2) continue; // юг — там дверной проём
            for (int t = 1; t <= 20; t++) {
                BlockPos p = new BlockPos(cx + dirs[i].getOffsetX() * t, y, cz + dirs[i].getOffsetZ() * t);
                BlockState b = w.getBlockState(p);
                if (!b.isAir() && !b.isOf(Blocks.SEA_LANTERN)) {
                    d[i] = t;
                    break;
                }
            }
        }
        int m = Math.max(Math.max(d[0], d[1]), d[3]);
        int n = Math.min(Math.min(d[0], d[1]), d[3]);
        return n > 0 && m - n <= 1 ? n : -1;
    }

    private static void set(ServerWorld w, int x, int y, int z, BlockState s) {
        w.setBlockState(new BlockPos(x, y, z), s, 2 | 16);
    }

    private static BlockState facing(net.minecraft.block.Block b, Direction d) {
        return b.getDefaultState().with(HorizontalFacingBlock.FACING, d);
    }

    /**
     * Обстановка. Координаты — от центра здания (cx, cz), s — уровень пола, внутренняя полуширина I = half − 1.
     * Вход — с юга (+z), посередине.
     */
    private static void furnish(ServerWorld w, int cx, int s, int cz, int half) {
        int I = half - 1;
        // зал в два этажа: убираем перекрытие и плавающие светильники, пол — ковёр с узором
        for (int lx = -I; lx <= I; lx++)
            for (int lz = -I; lz <= I; lz++) {
                for (int y = s + 1; y <= s + 7; y++) set(w, cx + lx, y, cz + lz, Blocks.AIR.getDefaultState());
                boolean edge = Math.abs(lx) == I || Math.abs(lz) == I;
                boolean aisle = Math.abs(lx) <= 1 && lz > 0;
                BlockState floor = edge ? Blocks.BLACK_CONCRETE.getDefaultState()
                        : aisle ? Blocks.YELLOW_WOOL.getDefaultState()
                        : (Math.floorMod(lx, 4) == 0 && Math.floorMod(lz, 4) == 0) ? Blocks.ORANGE_WOOL.getDefaultState()
                        : Blocks.RED_WOOL.getDefaultState();
                set(w, cx + lx, s, cz + lz, floor);
                set(w, cx + lx, s + 8, cz + lz, Blocks.BLACK_CONCRETE.getDefaultState()); // потолок
            }
        // автоматы вдоль западной и восточной стен
        for (int lz = -I + 2; lz <= I - 3; lz += 2) {
            set(w, cx - I, s + 1, cz + lz, facing(ModContent.SLOT_MACHINE, Direction.EAST));
            set(w, cx + I, s + 1, cz + lz, facing(ModContent.SLOT_MACHINE, Direction.WEST));
        }
        // остров автоматов «спина к спине» в северной части
        for (int lx = -I + 4; lx <= I - 4; lx += 2) {
            if (Math.abs(lx) <= 5) continue; // посередине — бар
            set(w, cx + lx, s + 1, cz - I + 4, facing(ModContent.SLOT_MACHINE, Direction.SOUTH));
            set(w, cx + lx, s + 1, cz - I + 3, facing(ModContent.SLOT_MACHINE, Direction.NORTH));
        }
        // бар у северной стены
        for (int lx = -4; lx <= 4; lx++) {
            set(w, cx + lx, s + 1, cz - I + 3, Blocks.POLISHED_BLACKSTONE.getDefaultState());
            set(w, cx + lx, s + 2, cz - I + 3, Blocks.SMOOTH_QUARTZ_SLAB.getDefaultState());
            set(w, cx + lx, s + 1, cz - I, lx % 2 == 0 ? Blocks.BARREL.getDefaultState() : Blocks.BOOKSHELF.getDefaultState());
            set(w, cx + lx, s + 2, cz - I, Blocks.BOOKSHELF.getDefaultState());
            set(w, cx + lx, s + 3, cz - I, facing(ModContent.NEON, Direction.SOUTH).with(NeonBlock.COLOR, NeonBlock.Color.CYAN));
        }
        citizen(w, cx, s + 1, cz - I + 2, 7, CitizenEntity.Role.CITIZEN, "Бармен", 0);
        // рулетки: две, крупье стоят с северной стороны
        for (int lx : new int[]{-6, 6}) {
            set(w, cx + lx, s + 1, cz - 3, facing(ModContent.ROULETTE, Direction.SOUTH));
            set(w, cx + lx - 1, s + 1, cz - 3, Blocks.GREEN_WOOL.getDefaultState());
            set(w, cx + lx + 1, s + 1, cz - 3, Blocks.GREEN_WOOL.getDefaultState());
            set(w, cx + lx - 1, s + 2, cz - 3, Blocks.GREEN_CARPET.getDefaultState());
            set(w, cx + lx + 1, s + 2, cz - 3, Blocks.GREEN_CARPET.getDefaultState());
            citizen(w, cx + lx, s + 1, cz - 4, 5, CitizenEntity.Role.DEALER, "Крупье", 0);
        }
        // карточные столы (блэкджек, покер): сукно 3×2, дилер с северной стороны, табуреты с юга
        for (int lx : new int[]{-6, 6}) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = 0; dz <= 1; dz++) {
                    set(w, cx + lx + dx, s + 1, cz + 3 + dz, Blocks.DARK_OAK_PLANKS.getDefaultState());
                    set(w, cx + lx + dx, s + 2, cz + 3 + dz, Blocks.GREEN_CARPET.getDefaultState());
                }
            for (int dx = -1; dx <= 1; dx++)
                set(w, cx + lx + dx, s + 1, cz + 5, Blocks.DARK_OAK_SLAB.getDefaultState());
            citizen(w, cx + lx, s + 1, cz + 2, 6, CitizenEntity.Role.DEALER, lx < 0 ? "Дилер · блэкджек" : "Дилер · покер", 0);
        }
        // касса у входа и администратор
        for (int lz = I - 3; lz <= I - 1; lz++) set(w, cx + I - 3, s + 1, cz + lz, Blocks.POLISHED_BLACKSTONE.getDefaultState());
        set(w, cx + I - 3, s + 2, cz + I - 2, Blocks.GOLD_BLOCK.getDefaultState());
        citizen(w, cx + I - 2, s + 1, cz + I - 2, 8, CitizenEntity.Role.BANKER, "Касса казино", 90);
        citizen(w, cx - 3, s + 1, cz + I - 3, 6, CitizenEntity.Role.CROUPIER, "Администратор «Короны»", 180);
        citizen(w, cx + 3, s + 1, cz + I - 1, 4, CitizenEntity.Role.CITIZEN, "Охрана", 180);
        // колонны с золотом по углам зала
        for (int sx : new int[]{-1, 1})
            for (int sz : new int[]{-1, 1}) {
                int x = cx + sx * (I - 3), z = cz + sz * (I - 6);
                if (Math.abs(z - cz) < 2) continue;
                for (int y = s + 1; y <= s + 7; y++) set(w, x, y, z, y == s + 7 || y == s + 1 ? Blocks.GOLD_BLOCK.getDefaultState() : Blocks.QUARTZ_PILLAR.getDefaultState());
            }
        // люстры: светящийся шар на цепи и четыре фонаря вокруг
        for (int lx : new int[]{-6, 0, 6})
            for (int lz : new int[]{-6, 0, 6}) {
                if (lz == -6 && lx == 0) continue; // над баром
                int x = cx + lx, z = cz + lz;
                set(w, x, s + 7, z, Blocks.CHAIN.getDefaultState());
                set(w, x, s + 6, z, Blocks.GLOWSTONE.getDefaultState());
                for (Direction d : Direction.Type.HORIZONTAL) {
                    set(w, x + d.getOffsetX(), s + 7, z + d.getOffsetZ(), Blocks.CHAIN.getDefaultState());
                    set(w, x + d.getOffsetX(), s + 6, z + d.getOffsetZ(), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
                }
            }
        // неон по стенам под потолком второго яруса, цвета по кругу
        NeonBlock.Color[] colors = {NeonBlock.Color.PINK, NeonBlock.Color.PURPLE, NeonBlock.Color.CYAN, NeonBlock.Color.YELLOW};
        for (int t = -I; t <= I; t++) {
            NeonBlock.Color c = colors[Math.floorMod(t + I, colors.length * 3) / 3];
            set(w, cx + t, s + 6, cz - I, facing(ModContent.NEON, Direction.SOUTH).with(NeonBlock.COLOR, c));
            set(w, cx - I, s + 6, cz + t, facing(ModContent.NEON, Direction.EAST).with(NeonBlock.COLOR, c));
            set(w, cx + I, s + 6, cz + t, facing(ModContent.NEON, Direction.WEST).with(NeonBlock.COLOR, c));
            if (Math.abs(t) > 3) set(w, cx + t, s + 6, cz + I, facing(ModContent.NEON, Direction.NORTH).with(NeonBlock.COLOR, c));
        }
        label(w, cx, s + 9, cz + half + 1, "👑 КАЗИНО «ЗОЛОТАЯ КОРОНА»", Formatting.GOLD);
        label(w, cx, s + 4, cz, "Поговори с крупье или дилером — выбери игру", Formatting.LIGHT_PURPLE);
    }

    private static void citizen(ServerWorld w, int x, int y, int z, int skin, CitizenEntity.Role role, String name, float yaw) {
        CitizenEntity c = new CitizenEntity(ModContent.CITIZEN, w);
        c.refreshPositionAndAngles(x + 0.5, y, z + 0.5, yaw, 0);
        c.setHeadYaw(yaw);
        c.setBodyYaw(yaw);
        c.setup(skin, role, true, name);
        w.spawnEntity(c);
    }

    private static void label(ServerWorld w, int x, int y, int z, String text, Formatting color) {
        DisplayEntity.TextDisplayEntity d = EntityType.TEXT_DISPLAY.create(w);
        if (d == null) return;
        NbtCompound n = new NbtCompound();
        n.putString("text", Text.Serialization.toJsonString(Text.literal(text).formatted(color, Formatting.BOLD), w.getRegistryManager()));
        n.putString("billboard", "center");
        n.putString("alignment", "center");
        n.putInt("background", 0x99000000);
        n.putByte("shadow", (byte) 1);
        NbtCompound tr = new NbtCompound();
        tr.put("translation", floats(0, 0, 0));
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("scale", floats(1.6f, 1.6f, 1.6f));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        n.put("transformation", tr);
        NbtCompound br = new NbtCompound();
        br.putInt("block", 15);
        br.putInt("sky", 15);
        n.put("brightness", br);
        d.readNbt(n);
        d.refreshPositionAndAngles(x + 0.5, y, z + 0.5, 0, 0);
        w.spawnEntity(d);
    }

    private static NbtList floats(float... v) {
        NbtList l = new NbtList();
        for (float f : v) l.add(NbtFloat.of(f));
        return l;
    }
}
