package com.yompix.novograd.gta;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.block.NeonBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

/**
 * 0.9.10: сцена концерта — собирается из блоков на вечер концерта и убирается после (только в пустом воздухе,
 * ничего чужого не трогает). Подиум с подсвеченной кромкой, фермы и балки, прожекторы и фонари,
 * LED-задник из неона, стеки колонок, диджейский пульт, ступеньки. Сцена смотрит на юг (+Z), как и группа.
 * Поставленные блоки записаны в мир (novograd_stage): если сервер выключили посреди концерта — уберутся при следующем запуске.
 */
public final class ConcertStage {
    private ConcertStage() {}

    static final class State extends PersistentState {
        final Map<Long, String> placed = new HashMap<>(); // позиция → id поставленного блока

        @Override
        public NbtCompound writeNbt(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            NbtList l = new NbtList();
            for (var e : placed.entrySet()) {
                NbtCompound c = new NbtCompound();
                c.putLong("p", e.getKey());
                c.putString("b", e.getValue());
                l.add(c);
            }
            n.put("placed", l);
            return n;
        }

        static State read(NbtCompound n, RegistryWrapper.WrapperLookup reg) {
            State s = new State();
            for (NbtElement e : n.getList("placed", NbtElement.COMPOUND_TYPE)) {
                NbtCompound c = (NbtCompound) e;
                s.placed.put(c.getLong("p"), c.getString("b"));
            }
            return s;
        }
    }

    private static final PersistentState.Type<State> TYPE = new PersistentState.Type<>(State::new, State::read, null);

    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "novograd_stage");
    }

    /** Высота подиума над землёй: группа стоит на at.y + 1. */
    public static final int RISE = 1;

    public static void build(ServerWorld w, Vec3d at) {
        State st = state(w.getServer());
        BlockPos o = BlockPos.ofFloored(at);
        for (long k : st.placed.keySet()) if (BlockPos.fromLong(k).getSquaredDistance(o) < 20 * 20) return; // здесь уже стоит
        BlockState deck = Blocks.POLISHED_BLACKSTONE.getDefaultState(), edge = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        // подиум 11×6, кромка — цветные огни, по краям — ступеньки
        for (int x = -5; x <= 5; x++)
            for (int z = -7; z <= -2; z++) {
                BlockState b = z == -2 ? (x % 2 == 0 ? Blocks.PEARLESCENT_FROGLIGHT.getDefaultState() : Blocks.VERDANT_FROGLIGHT.getDefaultState())
                        : (x == -5 || x == 5) ? edge : deck;
                put(w, st, o.add(x, 0, z), b, true);
            }
        for (int x = -1; x <= 1; x++)
            put(w, st, o.add(x, 0, -1), Blocks.POLISHED_BLACKSTONE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH), true);
        // фермы по углам (4 блока) и балки над сценой
        int top = 5;
        for (int[] c : new int[][]{{-5, -2}, {5, -2}, {-5, -7}, {5, -7}})
            for (int y = 1; y < top; y++) put(w, st, o.add(c[0], y, c[1]), Blocks.IRON_BARS.getDefaultState(), false);
        for (int x = -5; x <= 5; x++) {
            put(w, st, o.add(x, top, -2), Blocks.POLISHED_BLACKSTONE_SLAB.getDefaultState(), false);
            put(w, st, o.add(x, top, -7), Blocks.POLISHED_BLACKSTONE_SLAB.getDefaultState(), false);
        }
        for (int z = -6; z <= -3; z++) {
            put(w, st, o.add(-5, top, z), Blocks.POLISHED_BLACKSTONE_SLAB.getDefaultState(), false);
            put(w, st, o.add(5, top, z), Blocks.POLISHED_BLACKSTONE_SLAB.getDefaultState(), false);
            put(w, st, o.add(0, top, z), Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.Z), false);
        }
        // прожекторы на передней балке и фонари над группой
        for (int x = -4; x <= 4; x += 2)
            put(w, st, o.add(x, top - 1, -2), Blocks.END_ROD.getDefaultState().with(Properties.FACING, Direction.DOWN), false);
        for (int x : new int[]{-3, 3})
            put(w, st, o.add(x, top - 1, -5), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), false);
        // LED-задник: чёрная стена с неоновыми полосами
        NeonBlock.Color[] colors = {NeonBlock.Color.PINK, NeonBlock.Color.CYAN, NeonBlock.Color.PURPLE};
        for (int x = -4; x <= 4; x++)
            for (int y = 1; y <= 4; y++) {
                put(w, st, o.add(x, y, -8), Blocks.BLACK_CONCRETE.getDefaultState(), false);
                if (y >= 2)
                    put(w, st, o.add(x, y, -7), ModContent.NEON.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH)
                            .with(NeonBlock.COLOR, colors[(y + Math.abs(x)) % 3]).with(NeonBlock.LIT, true), false);
            }
        // диджейский пульт в глубине сцены
        for (int x = -1; x <= 0; x++)
            put(w, st, o.add(x, RISE, -6), ModContent.DJ_DECK.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH), false);
        // колонки: на сцене по краям и стеки у сцены
        BlockState spk = ModContent.FURNITURE.get("speaker").getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH);
        for (int x : new int[]{-4, 4}) put(w, st, o.add(x, RISE, -3), spk, false);
        for (int x : new int[]{-7, 7}) {
            put(w, st, o.add(x, 0, -3), Blocks.BLACK_CONCRETE.getDefaultState(), true);
            put(w, st, o.add(x, 1, -3), Blocks.NOTE_BLOCK.getDefaultState(), false);
            put(w, st, o.add(x, 2, -3), spk, false);
        }
        st.markDirty();
    }

    /** Поставить блок только в пустой воздух; подиум (ground=true) — только на твёрдую землю. */
    private static void put(ServerWorld w, State st, BlockPos p, BlockState b, boolean ground) {
        if (!w.getBlockState(p).isAir()) return;
        if (ground && !w.getBlockState(p.down()).isSolidBlock(w, p.down())) return;
        w.setBlockState(p, b, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        st.placed.put(p.asLong(), net.minecraft.registry.Registries.BLOCK.getId(b.getBlock()).toString());
    }

    /** Убрать сцену: только блоки, которые всё ещё наши (игрок не подменил). */
    public static void clear(ServerWorld w) {
        State st = state(w.getServer());
        if (st.placed.isEmpty()) return;
        int removed = 0, waiting = 0;
        BlockPos sample = null;
        var it = st.placed.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            BlockPos p = BlockPos.fromLong(e.getKey());
            if (!w.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) { // уберём, когда чанк загрузится
                waiting++;
                sample = p;
                continue;
            }
            if (net.minecraft.registry.Registries.BLOCK.getId(w.getBlockState(p).getBlock()).toString().equals(e.getValue()))
                w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            it.remove();
            removed++;
        }
        if (removed > 0) st.markDirty();
        if (removed > 0 || Boolean.getBoolean("novograd.workfast"))
            com.yompix.novograd.Novograd.LOG.info("[ночь] сцена убрана: {} блоков, ждут загрузки {} (например {})", removed, waiting, sample);
    }

    public static int size(MinecraftServer s) {
        return state(s).placed.size();
    }
}
