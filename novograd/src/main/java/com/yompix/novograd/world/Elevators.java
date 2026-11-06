package com.yompix.novograd.world;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.block.ElevatorFloorBlock;
import com.yompix.novograd.block.FacingBlock;
import com.yompix.novograd.block.SeatBlockHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Лифты в небоскрёбах и отелях. Шахта — столбец, где на каждом этаже лежит {@link ElevatorFloorBlock}
 * (Island ставит их при генерации). В кабине: ПКМ по панели — меню этажей; прыжок — этажом выше, Shift — ниже.
 * Поездка: звонок, затемнение, перенос на нужный этаж лицом к дверям, снова звонок.
 * Заодно: на ступеньки-стулья и диваны можно сесть (ПКМ пустой рукой).
 */
public final class Elevators {
    private Elevators() {}

    // ------------------------------------------------------------------ блоки
    public static final Block PANEL = CityBlocks.ELEVATOR_PANEL;
    public static final Block FLOOR = CityBlocks.ELEVATOR_FLOOR;

    // ------------------------------------------------------------------ сеть
    public record S2C(NbtCompound data) implements CustomPayload {
        public static final Id<S2C> ID = new Id<>(Novograd.id("lift_s2c"));
        public static final PacketCodec<RegistryByteBuf, S2C> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, S2C::data, S2C::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record C2S(NbtCompound data) implements CustomPayload {
        public static final Id<C2S> ID = new Id<>(Novograd.id("lift_c2s"));
        public static final PacketCodec<RegistryByteBuf, C2S> CODEC = PacketCodec.tuple(PacketCodecs.NBT_COMPOUND, C2S::data, C2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private record Ride(ServerWorld world, int x, int z, int floorY, Direction exit, String label, int[] timer) {}

    private static final Map<UUID, Ride> RIDES = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<UUID, Boolean> SNEAK = new HashMap<>();
    private static final Map<UUID, Long> HINT = new HashMap<>();

    public static void init() {
        CityBlocks.init();
        PayloadTypeRegistry.playS2C().register(S2C.ID, S2C.CODEC);
        PayloadTypeRegistry.playC2S().register(C2S.ID, C2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(C2S.ID, (payload, ctx) -> {
            try {
                choose(ctx.player(), payload.data());
            } catch (RuntimeException e) {
                Novograd.LOG.warn("Лифт: ошибка", e);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Elevators::tick);
        // сесть на стул, диван, скамейку (ступеньки нижней половины, сверху пусто) — пустой рукой
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || player.isSneaking() || !player.getStackInHand(hand).isEmpty() || player.hasVehicle() || player.isSpectator())
                return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BlockState s = world.getBlockState(pos);
            if (!(s.getBlock() instanceof StairsBlock) || s.get(StairsBlock.HALF) != BlockHalf.BOTTOM) return ActionResult.PASS;
            if (!world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()) return ActionResult.PASS;
            if (player.squaredDistanceTo(pos.toCenterPos()) > 9) return ActionResult.PASS;
            // ступени лестничных пролётов (под ними или над ними ещё ступени) — не стулья
            if (world.getBlockState(pos.down()).getBlock() instanceof StairsBlock) return ActionResult.PASS;
            for (Direction d : Direction.Type.HORIZONTAL) {
                if (world.getBlockState(pos.offset(d).up()).getBlock() instanceof StairsBlock
                        || world.getBlockState(pos.offset(d).down()).getBlock() instanceof StairsBlock) return ActionResult.PASS;
            }
            SeatBlockHelper.sit((ServerWorld) world, pos, (ServerPlayerEntity) player, 0.45);
            return ActionResult.SUCCESS;
        });
    }

    /** Все этажи лифта в столбце (x, z): y полов кабины снизу вверх. */
    public static List<Integer> floors(World w, int x, int z) {
        List<Integer> out = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int y = w.getBottomY(); y < w.getTopY(); y++) if (w.getBlockState(m.set(x, y, z)).isOf(FLOOR)) out.add(y);
        return out;
    }

    private static String name(World w, int x, int z, List<Integer> fl, int i) {
        if (i == 0) return "Лобби";
        if (w.getBlockState(new BlockPos(x, fl.get(i), z)).get(ElevatorFloorBlock.ROOF)) return "Крыша";
        return "Этаж " + (i + 1);
    }

    /** ПКМ по панели: панель смотрит в кабину, пол кабины — на 2 ниже панели. */
    public static void openMenu(ServerPlayerEntity p, BlockPos panel, Direction face) {
        BlockPos cabin = panel.offset(face);
        World w = p.getWorld();
        List<Integer> fl = floors(w, cabin.getX(), cabin.getZ());
        if (fl.size() < 2) {
            p.sendMessage(Text.literal("Лифт не работает").formatted(Formatting.GRAY), true);
            return;
        }
        int cur = nearest(fl, p.getBlockY() - 1);
        NbtCompound d = new NbtCompound();
        d.putString("type", "menu");
        d.putInt("x", cabin.getX());
        d.putInt("z", cabin.getZ());
        d.putInt("cur", cur);
        NbtList names = new NbtList();
        for (int i = 0; i < fl.size(); i++) names.add(NbtString.of(name(w, cabin.getX(), cabin.getZ(), fl, i)));
        d.put("names", names);
        ServerPlayNetworking.send(p, new S2C(d));
        w.playSound(null, panel, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.BLOCKS, 0.4f, 1.6f);
    }

    private static int nearest(List<Integer> fl, int y) {
        int best = 0;
        for (int i = 1; i < fl.size(); i++) if (Math.abs(fl.get(i) - y) < Math.abs(fl.get(best) - y)) best = i;
        return best;
    }

    /** Игрок выбрал этаж в меню. Сервер всё перепроверяет. */
    private static void choose(ServerPlayerEntity p, NbtCompound d) {
        int x = d.getInt("x"), z = d.getInt("z"), idx = d.getInt("floor");
        if (p.squaredDistanceTo(x + 0.5, p.getY(), z + 0.5) > 3 * 3) return;
        List<Integer> fl = floors(p.getWorld(), x, z);
        if (idx < 0 || idx >= fl.size()) return;
        int cur = nearest(fl, p.getBlockY() - 1);
        if (Math.abs(fl.get(cur) - (p.getBlockY() - 1)) > 2 || cur == idx) return;
        start(p, x, z, fl, idx);
    }

    private static void start(ServerPlayerEntity p, int x, int z, List<Integer> fl, int idx) {
        if (RIDES.containsKey(p.getUuid())) return;
        ServerWorld w = p.getServerWorld();
        int y = fl.get(idx);
        BlockState st = w.getBlockState(new BlockPos(x, y, z));
        Direction exit = st.isOf(FLOOR) ? st.get(FacingBlock.FACING) : Direction.NORTH;
        String label = name(w, x, z, fl, idx);
        int floorsAway = Math.abs(idx - nearest(fl, p.getBlockY() - 1));
        int ticks = 10 + Math.min(10, floorsAway / 3); // дальше — дольше, но не больше секунды
        RIDES.put(p.getUuid(), new Ride(w, x, z, y, exit, label, new int[]{ticks}));
        NbtCompound d = new NbtCompound();
        d.putString("type", "fade");
        d.putString("label", label);
        d.putInt("ticks", ticks);
        ServerPlayNetworking.send(p, new S2C(d));
        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.7f, 1.19f);
        w.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.25f, 1.5f);
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            UUID id = p.getUuid();
            Ride r = RIDES.get(id);
            if (r != null) {
                if (--r.timer()[0] > 0) continue;
                RIDES.remove(id);
                if (p.isRemoved() || p.isDead()) continue;
                p.teleport(r.world(), r.x() + 0.5, r.floorY() + 1, r.z() + 0.5, r.exit().asRotation(), 0);
                r.world().playSound(null, new BlockPos(r.x(), r.floorY() + 1, r.z()), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.8f, 1.5f);
                r.world().playSound(null, new BlockPos(r.x(), r.floorY() + 1, r.z()), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.25f, 1.6f);
                p.sendMessage(Text.literal("🛗 " + r.label()).formatted(Formatting.GOLD), true);
                COOLDOWN.put(id, now + 25);
                SNEAK.put(id, true); // отпусти Shift, прежде чем ехать дальше вниз
                continue;
            }
            if (p.hasVehicle() || p.isSpectator() || now < COOLDOWN.getOrDefault(id, 0L)) {
                SNEAK.put(id, p.isSneaking());
                continue;
            }
            // стоит (или подпрыгнул) на полу кабины?
            int bx = MathHelper.floor(p.getX()), bz = MathHelper.floor(p.getZ()), by = MathHelper.floor(p.getY() + 0.01);
            int pad = Integer.MIN_VALUE;
            for (int dy = 1; dy <= 2; dy++)
                if (p.getWorld().getBlockState(new BlockPos(bx, by - dy, bz)).isOf(FLOOR)) {
                    pad = by - dy;
                    break;
                }
            boolean sneak = p.isSneaking();
            boolean wasSneak = SNEAK.getOrDefault(id, false);
            SNEAK.put(id, sneak);
            if (pad == Integer.MIN_VALUE) continue;
            double above = p.getY() - (pad + 1);
            if (now - HINT.getOrDefault(id, -999L) > 60) {
                HINT.put(id, now);
                p.sendMessage(Text.literal("Лифт: прыжок — вверх · Shift — вниз · ПКМ по панели — выбор этажа").formatted(Formatting.YELLOW), true);
            }
            int dir = 0;
            if (above > 0.45) dir = 1;
            else if (sneak && !wasSneak && above < 0.1) dir = -1;
            if (dir == 0) continue;
            List<Integer> fl = floors(p.getWorld(), bx, bz);
            int cur = fl.indexOf(pad);
            if (cur < 0) continue;
            int to = cur + dir;
            if (to < 0 || to >= fl.size()) {
                COOLDOWN.put(id, now + 15);
                p.sendMessage(Text.literal(dir > 0 ? "Выше некуда — это последний этаж" : "Ниже некуда — это лобби").formatted(Formatting.GRAY), true);
                continue;
            }
            start(p, bx, bz, fl, to);
        }
    }
}
