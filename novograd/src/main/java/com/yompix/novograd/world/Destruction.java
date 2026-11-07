package com.yompix.novograd.world;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.block.PropBlock;
import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.entity.DebrisEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

/**
 * Живой, ломающийся город: машина сносит фонари, гидранты, урны, скамейки, заборы и стёкла; пули бьют стёкла,
 * гасят фонари, взрывают баллоны и колонки на заправках; у припаркованных машин воет сигнализация.
 * Всё сломанное запоминается ({@link State}) и через несколько минут само встаёт на место — когда рядом никого нет.
 * Работает только по событиям (удар, выстрел, взрыв), без обхода мира по тикам.
 */
public final class Destruction {
    private Destruction() {}

    /**
     * Крючок для ИИ жителей (другие модули): «здесь что-то грохнуло» — место и сила (1 — мелочь, 3 — взрыв).
     * Свидетели уже получают {@link CitizenEntity#panic}; сюда можно повесить свою реакцию (позвонить в полицию, снимать на телефон).
     */
    @Nullable public static BiConsumer<ServerWorld, Vec3d> onCommotion;

    // ------------------------------------------------------------------ что сломано и когда вернуть
    static final class Broken {
        final BlockPos pos;
        final BlockState state;
        long at;

        Broken(BlockPos pos, BlockState state, long at) {
            this.pos = pos;
            this.state = state;
            this.at = at;
        }
    }

    public static final class State extends PersistentState {
        final Map<Long, Broken> broken = new HashMap<>();
        static final Type<State> TYPE = new Type<>(State::new, (n, reg) -> load(n), null);

        static State load(NbtCompound n) {
            State s = new State();
            NbtList l = n.getList("b", NbtElement.COMPOUND_TYPE);
            var blocks = Registries.BLOCK.getReadOnlyWrapper();
            for (int i = 0; i < l.size(); i++) {
                NbtCompound e = l.getCompound(i);
                BlockPos p = BlockPos.fromLong(e.getLong("p"));
                BlockState st = NbtHelper.toBlockState(blocks, e.getCompound("s"));
                if (!st.isAir()) s.broken.put(p.asLong(), new Broken(p, st, e.getLong("t")));
            }
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound n, net.minecraft.registry.RegistryWrapper.WrapperLookup reg) {
            NbtList l = new NbtList();
            for (Broken b : broken.values()) {
                NbtCompound e = new NbtCompound();
                e.putLong("p", b.pos.asLong());
                e.put("s", NbtHelper.fromBlockState(b.state));
                e.putLong("t", b.at);
                l.add(e);
            }
            n.put("b", l);
            return n;
        }
    }

    public static State state(ServerWorld w) {
        return w.getPersistentStateManager().getOrCreate(State.TYPE, "novograd_props");
    }

    /** Сколько предметов сейчас сломано (для автотеста и отладки). */
    public static int brokenCount(ServerWorld w) {
        return state(w).broken.size();
    }

    /** Автотест: что ещё не починено (до 12 штук). */
    public static String debugBroken(ServerWorld w) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Broken b : state(w).broken.values()) {
            if (n++ >= 12) break;
            sb.append(b.pos.toShortString()).append('=').append(Registries.BLOCK.getId(b.state.getBlock()).getPath())
                    .append(" now:").append(Registries.BLOCK.getId(w.getBlockState(b.pos).getBlock()).getPath()).append("; ");
        }
        return sb.toString();
    }

    /** Автотест: всё сломанное пора чинить. */
    public static void expireAll(ServerWorld w) {
        for (Broken b : state(w).broken.values()) b.at = w.getTime() - 6001;
    }

    /** Убрать блок (заменить на replacement) и вернуть исходный через sec секунд. */
    public static void breakBlock(ServerWorld w, BlockPos pos, BlockState replacement, int sec) {
        BlockState old = w.getBlockState(pos);
        if (old.isAir()) return;
        State s = state(w);
        long when = w.getTime() + sec * 20L;
        Broken b = s.broken.get(pos.asLong());
        if (b == null) s.broken.put(pos.asLong(), new Broken(pos.toImmutable(), old, when));
        else b.at = Math.max(b.at, when); // уже был сломан (фонарь погас, потом его снесли) — помним целый
        w.setBlockState(pos, replacement, Block.NOTIFY_ALL);
        s.markDirty();
    }

    private static void restoreTick(ServerWorld w) {
        State s = state(w);
        if (s.broken.isEmpty()) return;
        long now = w.getTime();
        boolean dirty = false;
        for (Iterator<Broken> it = s.broken.values().iterator(); it.hasNext(); ) {
            Broken b = it.next();
            if (now < b.at || !w.isChunkLoaded(b.pos.getX() >> 4, b.pos.getZ() >> 4)) continue;
            // как в GTA: чиним, пока никто не смотрит (но не дольше лишних 5 минут)
            if (now < b.at + 6000 && w.getClosestPlayer(b.pos.getX() + 0.5, b.pos.getY(), b.pos.getZ() + 0.5, 14, false) != null) continue;
            BlockState cur = w.getBlockState(b.pos);
            if (cur.isAir() || cur.isReplaceable() || cur.isOf(Props.LAMP_BROKEN) || cur.isOf(Blocks.FIRE)) {
                Box box = new Box(b.pos);
                if (w.getOtherEntities(null, box, e -> e instanceof LivingEntity || e instanceof CarEntity).isEmpty()) w.setBlockState(b.pos, b.state, Block.NOTIFY_ALL);
                else continue; // кто-то стоит на месте столба — подождём
            }
            it.remove();
            dirty = true;
        }
        if (dirty) s.markDirty();
    }

    // ------------------------------------------------------------------ что ломается
    public enum Hit { NONE, GLASS, FENCE, TRASH, BENCH, HEDGE, HYDRANT, LAMP, MAILBOX, PROP }

    public static boolean onSidewalk(World w, BlockPos p) {
        return w.getBlockState(p.down()).isOf(ModContent.SIDEWALK);
    }

    public static boolean isGlass(BlockState st) {
        Block b = st.getBlock();
        return st.getSoundGroup() == BlockSoundGroup.GLASS && (b instanceof PaneBlock || b instanceof TransparentBlock || b instanceof StainedGlassBlock);
    }

    public static Hit classify(World w, BlockPos p, BlockState st) {
        if (st.isAir()) return Hit.NONE;
        Block b = st.getBlock();
        if (b instanceof PropBlock) return Hit.PROP;
        if (b == ModContent.LAMP_POST || b == ModContent.STREET_LAMP || b == Props.LAMP_BROKEN) return Hit.LAMP;
        if (isGlass(st)) return Hit.GLASS;
        if (b == Blocks.COMPOSTER) return Hit.TRASH;
        if (b == ModContent.MAILBOX) return Hit.MAILBOX;
        if ((b instanceof FenceBlock && st.isIn(BlockTags.WOODEN_FENCES)) || b instanceof FenceGateBlock) return Hit.FENCE;
        if (b == Blocks.RED_NETHER_BRICK_WALL && onSidewalk(w, p)) return Hit.HYDRANT;
        if (b instanceof StairsBlock && onSidewalk(w, p)) return Hit.BENCH;
        if (b instanceof LeavesBlock && onSidewalk(w, p)) return Hit.HEDGE;
        return Hit.NONE;
    }

    /** Минимальная скорость машины (блоков за тик), чтобы снести предмет. */
    private static double minSpeed(Hit h, BlockState st) {
        return switch (h) {
            case LAMP -> 0.34;
            case HYDRANT, FENCE -> 0.28;
            case PROP -> st.getBlock() instanceof PropBlock pb && pb.kind == PropBlock.Kind.VENDING ? 0.42 : 0.2;
            default -> 0.2;
        };
    }

    // ------------------------------------------------------------------ машина: удар по предметам и по припаркованным машинам
    private static final Map<CarEntity, Long> BUMPED = new WeakHashMap<>();

    /**
     * Из CarEntity.tick (сервер): машина едет со скоростью motion. Смотрим несколько точек перед капотом
     * (на скорости — дальше, чтобы клиент водителя не «упёрся» в столб, который сервер уже снёс).
     */
    public static void carTick(CarEntity car, Vec3d motion) {
        if (!(car.getWorld() instanceof ServerWorld w)) return;
        double s = motion.horizontalLength();
        if (s < 0.18 || car.wrecked() || car.isScooter()) return;
        LivingEntity drv = car.getControllingPassenger();
        boolean player = drv instanceof ServerPlayerEntity;
        if (!player && !car.isPolice()) return; // мирный трафик по тротуарам не ездит
        Vec3d dir = new Vec3d(motion.x, 0, motion.z).normalize();
        Vec3d side = new Vec3d(-dir.z, 0, dir.x);
        double reach = 1.4 + Math.min(3.2, s * 3.5);
        long last = Long.MIN_VALUE;
        for (double a = 0.9; a <= reach; a += 0.6)
            for (int l = -1; l <= 1; l++)
                for (int dy = 0; dy <= 1; dy++) {
                    Vec3d pt = car.getPos().add(dir.multiply(a)).add(side.multiply(l * 0.95)).add(0, 0.35 + dy, 0);
                    BlockPos bp = BlockPos.ofFloored(pt);
                    long k = bp.asLong();
                    if (k == last) continue;
                    last = k;
                    BlockState st = w.getBlockState(bp);
                    if (st.isAir()) continue;
                    Hit h = classify(w, bp, st);
                    if (h == Hit.NONE || s < minSpeed(h, st)) continue;
                    smash(w, bp, st, h, dir, s, drv instanceof ServerPlayerEntity sp ? sp : null);
                }
        // задели припаркованную машину — воет сигнализация
        if (player && w.getTime() % 2 == 0) {
            Box front = car.getBoundingBox().offset(dir.multiply(0.9)).expand(0.2, 0, 0.2);
            for (CarEntity o : w.getEntitiesByClass(CarEntity.class, front, o -> o != car && o.getPassengerList().isEmpty() && !o.wrecked())) {
                if (w.getTime() - BUMPED.getOrDefault(o, -100L) < 20) continue;
                BUMPED.put(o, w.getTime());
                alarm(w, o);
                w.playSound(null, o.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.NEUTRAL, 0.7f, 1.2f);
                w.spawnParticles(ParticleTypes.CRIT, o.getX(), o.getY() + 0.8, o.getZ(), 8, 0.6, 0.3, 0.6, 0.3);
            }
        }
    }

    /** Снести предмет: dir — куда летит, speed — с какой силой (блоков за тик). */
    public static void smash(ServerWorld w, BlockPos pos, BlockState st, Hit h, Vec3d dir, double speed, @Nullable ServerPlayerEntity by) {
        Vec3d c = Vec3d.ofCenter(pos);
        Vec3d fly = dir.multiply(Math.min(1.1, speed * 1.2)).add(0, 0.3 + Math.min(0.35, speed * 0.4), 0);
        var r = w.getRandom();
        int loud = 1;
        switch (h) {
            case GLASS -> shatter(w, pos, st, true);
            case FENCE -> {
                breakBlock(w, pos, Blocks.AIR.getDefaultState(), 240);
                debris(w, st, c, fly, 0.8f);
                w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), c.x, c.y, c.z, 14, 0.3, 0.4, 0.3, 0.15);
                w.playSound(null, pos, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.8f, 1.3f);
            }
            case TRASH -> {
                breakBlock(w, pos, Blocks.AIR.getDefaultState(), 200);
                debris(w, st, c, fly, 0.75f);
                litter(w, c, 12);
                w.playSound(null, pos, SoundEvents.BLOCK_COMPOSTER_EMPTY, SoundCategory.BLOCKS, 1f, 0.8f);
                w.playSound(null, pos, SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, SoundCategory.BLOCKS, 0.5f, 1.6f);
            }
            case BENCH -> {
                breakBlock(w, pos, Blocks.AIR.getDefaultState(), 240);
                debris(w, st, c, fly, 0.85f);
                w.playSound(null, pos, SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.BLOCKS, 0.7f, 1.0f);
            }
            case HEDGE -> {
                breakBlock(w, pos, Blocks.AIR.getDefaultState(), 240);
                w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), c.x, c.y, c.z, 24, 0.4, 0.4, 0.4, 0.2);
                w.playSound(null, pos, SoundEvents.BLOCK_AZALEA_LEAVES_BREAK, SoundCategory.BLOCKS, 1f, 0.8f);
            }
            case MAILBOX -> {
                breakBlock(w, pos, Blocks.AIR.getDefaultState(), 240);
                debris(w, st, c, fly, 0.8f);
                litter(w, c, 16);
                w.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.5f, 1.5f);
            }
            case HYDRANT -> {
                breakBlock(w, pos, Blocks.AIR.getDefaultState(), 200);
                debris(w, st, c, fly.add(0, 0.3, 0), 0.55f);
                fountain(w, pos);
                w.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.8f, 0.7f);
                loud = 2;
            }
            case LAMP -> {
                knockLamp(w, pos, dir);
                loud = 2;
            }
            case PROP -> loud = smashProp(w, pos, st, (PropBlock) st.getBlock(), fly, by);
            default -> {}
        }
        commotion(w, c, loud, by);
    }

    private static int smashProp(ServerWorld w, BlockPos pos, BlockState st, PropBlock pb, Vec3d fly, @Nullable ServerPlayerEntity by) {
        Vec3d c = Vec3d.ofCenter(pos);
        switch (pb.kind) {
            case PROPANE -> {
                breakBlock(w, pos, Blocks.AIR.getDefaultState(), 300);
                w.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.4f, 0.6f); // шипит
                w.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 10, 0.2, 0.2, 0.2, 0.08);
                later(w, c, 3.2f, 14, by);
                return 3;
            }
            case VENDING -> {
                breakBlock(w, pos, Blocks.AIR.getDefaultState(), 300);
                debris(w, st, c.add(0, -0.3, 0), fly.multiply(0.5), 1f);
                w.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(ModContent.COLA)), c.x, c.y + 0.4, c.z, 14, 0.4, 0.5, 0.4, 0.25);
                w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GLASS.getDefaultState()), c.x, c.y + 0.5, c.z, 16, 0.4, 0.4, 0.4, 0.2);
                w.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1f, 0.8f);
                w.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.7f, 0.6f);
                if (w.getRandom().nextInt(2) == 0) dropCan(w, c);
                return 2;
            }
            default -> {
                breakBlock(w, pos, Blocks.AIR.getDefaultState(), 240);
                debris(w, st, c, pb.kind == PropBlock.Kind.CONE ? fly.multiply(1.3).add(0, 0.2, 0) : fly, pb.kind == PropBlock.Kind.CONE ? 0.9f : 0.85f);
                if (pb.kind == PropBlock.Kind.NEWS_STAND) litter(w, c, 18);
                w.playSound(null, pos, pb.kind == PropBlock.Kind.CONE ? SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK : SoundEvents.BLOCK_ANVIL_LAND,
                        SoundCategory.BLOCKS, 0.6f, pb.kind == PropBlock.Kind.CONE ? 1.4f : 1.2f);
                return 1;
            }
        }
    }

    private static int debrisAlive;
    private static long debrisTick;

    /** Обломок (не больше ~24 на мир, иначе только частицы). */
    static void debris(ServerWorld w, BlockState st, Vec3d at, Vec3d vel, float scale) {
        if (w.getTime() != debrisTick) {
            debrisTick = w.getTime();
            debrisAlive = 0;
        }
        if (debrisAlive++ > 6) return; // за один тик (взрыв) — не больше 6 летящих
        if (w.getEntitiesByType(Props.DEBRIS, new Box(at, at).expand(48), e -> true).size() > 24) return;
        var r = w.getRandom();
        DebrisEntity.tumble(w, st, at, vel.add((r.nextDouble() - 0.5) * 0.12, 0, (r.nextDouble() - 0.5) * 0.12), scale, 260 + r.nextInt(80));
    }

    private static void litter(ServerWorld w, Vec3d c, int n) {
        w.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.PAPER)), c.x, c.y + 0.3, c.z, n, 0.4, 0.3, 0.4, 0.18);
        w.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.BONE_MEAL)), c.x, c.y + 0.3, c.z, n / 2, 0.4, 0.3, 0.4, 0.15);
    }

    static void dropCan(ServerWorld w, Vec3d c) {
        var it = new net.minecraft.entity.ItemEntity(w, c.x, c.y + 0.3, c.z, new ItemStack(ModContent.COLA));
        it.setVelocity((w.random.nextDouble() - 0.5) * 0.2, 0.25, (w.random.nextDouble() - 0.5) * 0.2);
        it.setToDefaultPickupDelay();
        w.spawnEntity(it);
    }

    /** Стекло вдребезги: звук, осколки; через 5 минут стекольщик вставит новое. */
    public static void shatter(ServerWorld w, BlockPos pos, BlockState st, boolean sound) {
        breakBlock(w, pos, Blocks.AIR.getDefaultState(), 300);
        Vec3d c = Vec3d.ofCenter(pos);
        w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, st), c.x, c.y, c.z, 16, 0.3, 0.4, 0.3, 0.15);
        if (sound) w.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1.1f, 0.85f + w.random.nextFloat() * 0.3f);
    }

    // ------------------------------------------------------------------ фонарь
    private static void knockLamp(ServerWorld w, BlockPos hit, Vec3d dir) {
        BlockPos base = hit;
        while (isPole(w.getBlockState(base.down()))) base = base.down();
        List<BlockPos> col = new ArrayList<>();
        BlockPos p = base;
        while (isPole(w.getBlockState(p)) && col.size() < 7) {
            col.add(p);
            p = p.up();
        }
        if (w.getBlockState(p).isOf(ModContent.TRAFFIC_LIGHT) || col.size() < 2) return; // светофоры не трогаем
        BlockState top = w.getBlockState(col.get(col.size() - 1));
        if (top.isOf(ModContent.LAMP_POST)) top = ModContent.STREET_LAMP.getDefaultState().with(HorizontalFacingBlock.FACING, net.minecraft.util.math.Direction.NORTH);
        for (int i = col.size() - 1; i >= 0; i--) breakBlock(w, col.get(i), Blocks.AIR.getDefaultState(), 240);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        // лампа с поворотом «по ходу падения»: модель поворачиваем рендером, состояние — как стояло
        DebrisEntity.pole(w, ModContent.LAMP_POST.getDefaultState(), top, col.size(), Vec3d.ofBottomCenter(base), yaw, 20 * 45);
        Vec3d b = Vec3d.ofBottomCenter(base);
        w.playSound(null, base, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1f, 0.55f);
        w.playSound(null, base, SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.BLOCKS, 1f, 0.7f);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, b.x, b.y + 0.4, b.z, 14, 0.2, 0.2, 0.2, 0.4);
    }

    private static boolean isPole(BlockState st) {
        return st.isOf(ModContent.LAMP_POST) || st.isOf(ModContent.STREET_LAMP) || st.isOf(Props.LAMP_BROKEN);
    }

    /** Столб лёг на землю (из DebrisEntity): удар, искры, лампа бьётся; кто под ним — получает. */
    public static void poleLanded(ServerWorld w, DebrisEntity d) {
        double rad = Math.toRadians(d.getYaw());
        Vec3d dir = new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
        int len = d.poleLength();
        Vec3d tip = d.getPos().add(dir.multiply(len - 0.5)).add(0, 0.3, 0);
        w.playSound(null, BlockPos.ofFloored(tip), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 1.2f, 0.5f);
        w.playSound(null, BlockPos.ofFloored(tip), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1f, 1.1f);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 20, 0.3, 0.2, 0.3, 0.5);
        w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GLASS.getDefaultState()), tip.x, tip.y, tip.z, 12, 0.3, 0.2, 0.3, 0.15);
        w.spawnParticles(ParticleTypes.POOF, d.getX() + dir.x * len / 2, d.getY() + 0.2, d.getZ() + dir.z * len / 2, 10, len / 3.0, 0.1, len / 3.0, 0.02);
        Box line = new Box(d.getPos(), tip).expand(0.6, 0.6, 0.6);
        for (Entity e : w.getOtherEntities(d, line, e -> e instanceof LivingEntity || e instanceof CarEntity)) {
            if (e instanceof CarEntity car) {
                car.hitByBullet(8, null);
                if (car.getPassengerList().isEmpty()) alarm(w, car);
            } else if (e instanceof LivingEntity le && !(le instanceof ServerPlayerEntity p && p.isCreative())) {
                le.damage(w.getDamageSources().fallingBlock(d), 6);
                if (le instanceof CitizenEntity ce && !ce.storyProtected()) ce.knockDown(d.getPos(), null, 60);
            }
        }
    }

    // ------------------------------------------------------------------ пули
    private static final Map<Long, Integer> PUMP_HITS = new HashMap<>();
    private static final Map<Long, Long> PUMP_TIME = new HashMap<>();

    /** Из Weapons.fire: пуля попала в блок. */
    public static void bulletHit(ServerWorld w, LivingEntity shooter, BlockPos pos, Vec3d at, Vec3d dir) {
        BlockState st = w.getBlockState(pos);
        Hit h = classify(w, pos, st);
        ServerPlayerEntity by = shooter instanceof ServerPlayerEntity sp ? sp : null;
        switch (h) {
            case GLASS -> {
                shatter(w, pos, st, true);
                // большое окно сыплется дальше: соседние стёкла той же плоскости
                for (BlockPos n : new BlockPos[]{pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()})
                    if (w.random.nextInt(3) == 0 && isGlass(w.getBlockState(n))) shatter(w, n, w.getBlockState(n), false);
                commotion(w, at, 1, by);
            }
            case HYDRANT -> smash(w, pos, st, h, dir, 0.4, by);
            case LAMP -> {
                if (st.isOf(ModContent.STREET_LAMP)) { // погасить фонарь выстрелом
                    breakBlock(w, pos, Props.LAMP_BROKEN.getDefaultState().with(HorizontalFacingBlock.FACING, st.get(HorizontalFacingBlock.FACING)), 240);
                    w.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 1f, 1.3f);
                    w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.4);
                    w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GLASS.getDefaultState()), at.x, at.y - 0.2, at.z, 8, 0.2, 0.1, 0.2, 0.1);
                } else ping(w, pos, at);
            }
            case TRASH, MAILBOX -> {
                ping(w, pos, at);
                litter(w, at, 4);
            }
            case PROP -> {
                PropBlock pb = (PropBlock) st.getBlock();
                switch (pb.kind) {
                    case PROPANE -> {
                        breakBlock(w, pos, Blocks.AIR.getDefaultState(), 300);
                        later(w, Vec3d.ofCenter(pos), 3.4f, 2, by);
                    }
                    case CONE -> smash(w, pos, st, h, dir, 0.35, by);
                    case VENDING -> {
                        ping(w, pos, at);
                        if (w.random.nextInt(4) == 0) dropCan(w, at.subtract(dir.multiply(0.6)));
                    }
                    default -> ping(w, pos, at);
                }
            }
            default -> {
                if (st.isOf(ModContent.ATM)) ping(w, pos, at);
                else if (st.isOf(Blocks.RED_CONCRETE) || st.isOf(Blocks.LIGHT_GRAY_CONCRETE)) pumpHit(w, pos, at, by);
            }
        }
    }

    private static void ping(ServerWorld w, BlockPos pos, Vec3d at) {
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 5, 0.05, 0.05, 0.05, 0.25);
        w.playSound(null, pos, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, 0.8f, 1.6f + w.random.nextFloat() * 0.3f);
    }

    /** Колонка на заправке «Нефтяник»: несколько попаданий — и она взрывается вместе с соседними. */
    private static void pumpHit(ServerWorld w, BlockPos pos, Vec3d at, @Nullable ServerPlayerEntity by) {
        int station = -1;
        for (int i = 0; i < 8; i++) {
            Vec3d pump;
            try {
                pump = com.yompix.novograd.gta.Fuel.pumps(i);
            } catch (RuntimeException e) {
                break;
            }
            if (Math.abs(pump.x - at.x) < 10 && Math.abs(pump.z - at.z) < 10 && Math.abs(pump.y - at.y) < 6) {
                station = i;
                break;
            }
        }
        if (station < 0) return;
        BlockPos base = pos;
        while (isPumpPart(w.getBlockState(base.down()))) base = base.down();
        long k = base.asLong();
        if (w.getTime() - PUMP_TIME.getOrDefault(k, -10000L) > 400) PUMP_HITS.remove(k);
        PUMP_TIME.put(k, w.getTime());
        int n = PUMP_HITS.merge(k, 1, Integer::sum);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.3);
        w.playSound(null, pos, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, 0.9f, 1.2f);
        if (n == 2) {
            w.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1.4f, 0.5f); // бензин хлещет
            w.spawnParticles(ParticleTypes.FALLING_LAVA, at.x, at.y, at.z, 6, 0.2, 0.1, 0.2, 0);
            if (by != null) by.sendMessage(Text.literal("⛽ Из колонки хлещет бензин… ещё выстрел — и рванёт!").formatted(Formatting.GOLD), true);
        }
        if (n < 3) return;
        PUMP_HITS.remove(k);
        // колонка — столбик из бетона; рвём её и (с задержкой) соседние на островке
        for (int dy = 0; dy < 4 && isPumpPart(w.getBlockState(base.up(dy))); dy++) breakBlock(w, base.up(dy), Blocks.AIR.getDefaultState(), 360);
        Vec3d c = Vec3d.ofBottomCenter(base).add(0, 1, 0);
        later(w, c, 4.2f, 1, by);
        int delay = 10;
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos q = base.add(dx, 0, dz);
                if (isPumpPart(w.getBlockState(q)) && isPumpPart(w.getBlockState(q.up())) && !isPumpPart(w.getBlockState(q.down()))) {
                    for (int dy = 0; dy < 4 && isPumpPart(w.getBlockState(q.up(dy))); dy++) breakBlock(w, q.up(dy), Blocks.AIR.getDefaultState(), 360);
                    later(w, Vec3d.ofBottomCenter(q).add(0, 1, 0), 3.6f, delay, by);
                    delay += 8 + w.random.nextInt(8);
                }
            }
        Fires.ignite(w, c, 50, null);
        if (by != null && !by.isCreative()) com.yompix.novograd.gta.Wanted.add(by, 1, "взрыв на заправке", 2);
        com.yompix.novograd.gta.News.post(w.getServer(), "Взрыв на заправке «Нефтяник»: огонь видно за километр", "ЭКСТРЕННО");
    }

    private static boolean isPumpPart(BlockState st) {
        return st.isOf(Blocks.RED_CONCRETE) || st.isOf(Blocks.LIGHT_GRAY_CONCRETE);
    }

    /** Из Weapons.fire: пуля попала в машину. */
    public static void carShot(CarEntity car, @Nullable LivingEntity shooter) {
        if (car.getWorld() instanceof ServerWorld w && car.getPassengerList().isEmpty()) alarm(w, car);
    }

    // ------------------------------------------------------------------ взрывы
    private record Pending(ServerWorld w, Vec3d at, float power, long when, @Nullable UUID by) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    /** Отложенный взрыв (цепная реакция: баллон, колонка, соседняя колонка). */
    public static void later(ServerWorld w, Vec3d at, float power, int ticks, @Nullable ServerPlayerEntity by) {
        if (PENDING.size() < 24) PENDING.add(new Pending(w, at, power, w.getTime() + ticks, by == null ? null : by.getUuid()));
    }

    private static void boom(ServerWorld w, Vec3d at, float power, @Nullable ServerPlayerEntity by) {
        w.createExplosion(by, null, null, at.x, at.y, at.z, power, false, World.ExplosionSourceType.NONE,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, com.yompix.novograd.gta.Arsenal.EXPLOSION);
        w.spawnParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 30, 0.8, 0.6, 0.8, 0.12);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.8, at.z, 16, 1, 0.8, 1, 0.05);
        for (CarEntity car : w.getEntitiesByClass(CarEntity.class, new Box(at, at).expand(power + 1), c -> true))
            car.hitByBullet(50f * (float) Math.max(0.2, 1 - Math.sqrt(car.squaredDistanceTo(at)) / (power + 1.5)), by);
        explosion(w, at, power, by);
    }

    /**
     * Любой взрыв в городе (граната, ракета, машина, баллон): стёкла вылетают, мелочь разлетается, баллоны и колонки рядом
     * рвутся следом, загорается огонь, воют сигнализации, люди разбегаются. Банкомат рядом — вскрыт.
     */
    public static void explosion(ServerWorld w, Vec3d at, float power, @Nullable LivingEntity owner) {
        ServerPlayerEntity by = owner instanceof ServerPlayerEntity sp ? sp : null;
        int r = MathHelper.ceil(power * 1.25);
        BlockPos o = BlockPos.ofFloored(at);
        int glass = 0;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -2; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r * r) continue;
                    p.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    BlockState st = w.getBlockState(p);
                    if (st.isAir()) continue;
                    BlockPos bp = p.toImmutable();
                    Hit h = classify(w, bp, st);
                    double d = Math.sqrt(d2);
                    Vec3d dir = Vec3d.ofCenter(bp).subtract(at).normalize();
                    if (h == Hit.GLASS) {
                        shatter(w, bp, st, glass++ < 3);
                    } else if (h == Hit.PROP && st.getBlock() instanceof PropBlock pb && pb.kind == PropBlock.Kind.PROPANE) {
                        breakBlock(w, bp, Blocks.AIR.getDefaultState(), 300);
                        later(w, Vec3d.ofCenter(bp), 3.2f, 6 + w.random.nextInt(10), by);
                    } else if (h != Hit.NONE && d < r * 0.8) {
                        smash(w, bp, st, h, dir.add(0, 0.4, 0).normalize(), 0.9 * (1 - d / r) + 0.35, by);
                    } else if (h == Hit.NONE && st.isOf(ModContent.ATM) && d < r * 0.8) {
                        atmCracked(w, bp, by, true);
                    } else if (h == Hit.NONE && d < r * 0.7 && isPumpPart(st) && isPumpPart(w.getBlockState(bp.up())) && !isPumpPart(w.getBlockState(bp.down()))) {
                        for (int k = 0; k < 4 && isPumpPart(w.getBlockState(bp.up(k))); k++) breakBlock(w, bp.up(k), Blocks.AIR.getDefaultState(), 360);
                        later(w, Vec3d.ofBottomCenter(bp).add(0, 1, 0), 3.8f, 8 + w.random.nextInt(12), by);
                    }
                }
        // огонь на земле и на горючих предметах рядом
        Fires.igniteAround(w, at, power * 0.9, power > 3 ? 3 : 2);
        // сигнализации у машин вокруг
        for (CarEntity car : w.getEntitiesByClass(CarEntity.class, new Box(at, at).expand(power * 3), c -> c.getPassengerList().isEmpty() && !c.wrecked())) alarm(w, car);
        commotion(w, at, 3, by);
    }

    /** Машина взорвалась (CarEntity.wreck): небольшой взрыв по окрестностям и огонь. */
    public static void carExploded(ServerWorld w, CarEntity car) {
        explosion(w, car.getPos().add(0, 0.6, 0), 2.2f, null);
        Fires.ignite(w, car.getPos(), 30, null);
    }

    // ------------------------------------------------------------------ банкомат: взлом
    /** Банкомат вскрыт (взрыв или монтировка): грязные наличные и розыск; сам банкомат вернут через 10 минут. */
    public static void atmCracked(ServerWorld w, BlockPos pos, @Nullable ServerPlayerEntity by, boolean blast) {
        if (!w.getBlockState(pos).isOf(ModContent.ATM)) return;
        breakBlock(w, pos, Blocks.AIR.getDefaultState(), 600);
        Vec3d c = Vec3d.ofCenter(pos);
        w.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(ModContent.BANKNOTE_100)), c.x, c.y + 0.3, c.z, 30, 0.5, 0.5, 0.5, 0.2);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 20, 0.3, 0.4, 0.3, 0.4);
        w.playSound(null, pos, SoundEvents.BLOCK_ANVIL_DESTROY, SoundCategory.BLOCKS, 1f, 0.8f);
        w.playSound(null, pos, ModContent.CASH, SoundCategory.BLOCKS, 1f, 1f);
        if (by == null) return;
        int sum = (blast ? 300 : 150) + w.random.nextInt(blast ? 600 : 300);
        com.yompix.novograd.gta.Economy.dirty(by, sum, blast ? "(банкомат взорван)" : "(банкомат вскрыт)");
        if (!by.isCreative()) com.yompix.novograd.gta.Wanted.add(by, 1, "ограбление банкомата", 2);
        com.yompix.novograd.gta.News.post(w.getServer(), "Неизвестный вскрыл банкомат: полиция ищет свидетелей", "КРИМИНАЛ");
    }

    // ------------------------------------------------------------------ сигнализация
    private static final Map<CarEntity, int[]> ALARMS = new WeakHashMap<>();

    /** Сигнализация припаркованной машины: гудок и аварийка ~12 секунд, прохожие оборачиваются. */
    public static void alarm(ServerWorld w, CarEntity car) {
        if (car.wrecked() || !car.getPassengerList().isEmpty() || car.isRemoved()) return;
        int[] a = ALARMS.get(car);
        if (a != null) {
            a[0] = 240;
            return;
        }
        boolean hazard = car.light(CarEntity.HAZARD);
        if (!hazard) car.toggleLight(CarEntity.HAZARD);
        ALARMS.put(car, new int[]{240, hazard ? 1 : 0});
    }

    public static boolean alarmOn(CarEntity car) {
        return ALARMS.containsKey(car);
    }

    private static void alarmTick() {
        if (ALARMS.isEmpty()) return;
        for (Iterator<Map.Entry<CarEntity, int[]>> it = ALARMS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            CarEntity car = e.getKey();
            int[] a = e.getValue();
            a[0]--;
            boolean stop = a[0] <= 0 || car.isRemoved() || car.wrecked() || !car.getPassengerList().isEmpty();
            if (stop) {
                if (!car.isRemoved() && a[1] == 0 && car.light(CarEntity.HAZARD)) car.toggleLight(CarEntity.HAZARD);
                it.remove();
                continue;
            }
            if (!(car.getWorld() instanceof ServerWorld w)) continue;
            if (a[0] % 8 == 0)
                w.playSound(null, car.getBlockPos(), ModContent.CAR_HORN, SoundCategory.NEUTRAL, 1.3f, (a[0] / 8) % 2 == 0 ? 1.0f : 1.3f);
            if (a[0] % 20 == 0)
                for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, car.getBoundingBox().expand(14), c -> c.isAlive() && !c.hasVehicle()))
                    c.getLookControl().lookAt(car, 30, 30);
        }
    }

    // ------------------------------------------------------------------ гидрант: фонтан
    private record Fountain(ServerWorld w, BlockPos pos, int[] ttl) {}

    private static final List<Fountain> FOUNTAINS = new ArrayList<>();

    public static void fountain(ServerWorld w, BlockPos pos) {
        for (Fountain f : FOUNTAINS) if (f.pos.equals(pos)) {
            f.ttl[0] = 600;
            return;
        }
        if (FOUNTAINS.size() < 8) FOUNTAINS.add(new Fountain(w, pos.toImmutable(), new int[]{600}));
    }

    private static void fountainTick(long t) {
        for (Iterator<Fountain> it = FOUNTAINS.iterator(); it.hasNext(); ) {
            Fountain f = it.next();
            f.ttl[0]--;
            ServerWorld w = f.w;
            if (f.ttl[0] <= 0 || !w.isChunkLoaded(f.pos.getX() >> 4, f.pos.getZ() >> 4)) {
                it.remove();
                continue;
            }
            double x = f.pos.getX() + 0.5, y = f.pos.getY(), z = f.pos.getZ() + 0.5;
            float k = Math.min(1f, f.ttl[0] / 200f); // под конец струя слабеет
            double hgt = 1 + 3.2 * k;
            if (t % 2 == 0) {
                w.spawnParticles(ParticleTypes.FALLING_WATER, x, y + hgt * 0.5, z, 6, 0.12, hgt * 0.35, 0.12, 0);
                w.spawnParticles(ParticleTypes.SPLASH, x, y + hgt, z, 8, 0.35, 0.15, 0.35, 0.3);
                w.spawnParticles(ParticleTypes.SPLASH, x, y + 0.1, z, 5, 1.2, 0.05, 1.2, 0.1);
            }
            if (t % 6 == 0) w.spawnParticles(ParticleTypes.CLOUD, x, y + hgt, z, 1, 0.3, 0.2, 0.3, 0.01);
            if (t % 16 == 0) w.playSound(null, f.pos, SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS, 1.4f, 1.4f);
            if (t % 40 == 0) w.playSound(null, f.pos, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.BLOCKS, 0.8f, 1.2f);
            if (t % 4 == 0) {
                // струя подбрасывает тех, кто встал на гидрант (и машину тоже)
                for (Entity e : w.getOtherEntities(null, new Box(f.pos).expand(0.3, 2.5 * k, 0.3), e -> e instanceof LivingEntity || e instanceof CarEntity)) {
                    if (e instanceof CarEntity car && car.getControllingPassenger() instanceof ServerPlayerEntity) continue; // машину игрока считает клиент
                    e.setVelocity(e.getVelocity().x, Math.max(e.getVelocity().y, 0.32 * k), e.getVelocity().z);
                    e.velocityModified = true;
                    e.extinguish();
                    e.fallDistance = 0;
                }
            }
            if (t % 30 == 0) {
                // мокрые прохожие с визгом разбегаются
                for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, new Box(f.pos).expand(4.5, 3, 4.5), c -> c.isAlive() && !c.hasVehicle())) c.panic(Vec3d.ofCenter(f.pos));
                Fires.douseAt(w, Vec3d.ofCenter(f.pos), 4.5, 60);
            }
        }
    }

    // ------------------------------------------------------------------ свидетели и полиция
    private static final Map<UUID, Long> VANDAL = new HashMap<>();

    /** Шум: прохожие разбегаются (loud ≥ 2) или оборачиваются; полиция видела порчу имущества — звезда. */
    static void commotion(ServerWorld w, Vec3d at, int loud, @Nullable ServerPlayerEntity by) {
        double r = loud >= 3 ? 28 : loud == 2 ? 14 : 8;
        for (CitizenEntity c : w.getEntitiesByClass(CitizenEntity.class, new Box(at, at).expand(r), c -> c.isAlive() && !c.hasVehicle())) {
            if (loud >= 2) c.panic(at);
            else c.getLookControl().lookAt(at.x, at.y, at.z, 30, 30);
        }
        if (onCommotion != null) {
            try {
                onCommotion.accept(w, at);
            } catch (RuntimeException ignored) {}
        }
        if (by != null && !by.isCreative() && loud >= 2) {
            long now = w.getTime();
            if (now - VANDAL.getOrDefault(by.getUuid(), -1000L) > 200 && com.yompix.novograd.gta.Wanted.policeSees(w, by, 24)) {
                VANDAL.put(by.getUuid(), now);
                com.yompix.novograd.gta.Wanted.add(by, 0, "порча городского имущества", 1);
            }
        }
    }

    // ------------------------------------------------------------------ тик
    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        if (!PENDING.isEmpty()) {
            List<Pending> due = new ArrayList<>();
            for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
                Pending pd = it.next();
                if (pd.w.getTime() >= pd.when) {
                    due.add(pd);
                    it.remove();
                }
            }
            for (Pending pd : due) {
                ServerPlayerEntity by = pd.by == null ? null : server.getPlayerManager().getPlayer(pd.by);
                if (pd.w.isChunkLoaded(BlockPos.ofFloored(pd.at))) boom(pd.w, pd.at, pd.power, by);
            }
        }
        alarmTick();
        if (!FOUNTAINS.isEmpty()) fountainTick(t);
        if (t % 20 == 7) for (ServerWorld w : server.getWorlds()) restoreTick(w);
        if (t % 1200 == 0) {
            PUMP_HITS.clear();
            PUMP_TIME.clear();
        }
    }
}
