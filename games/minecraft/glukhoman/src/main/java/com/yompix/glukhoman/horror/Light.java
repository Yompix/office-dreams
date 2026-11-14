package com.yompix.glukhoman.horror;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.entity.ChoirEntity;
import com.yompix.glukhoman.item.FlashlightItem;
import com.yompix.glukhoman.net.Net;
import com.yompix.glukhoman.story.StoryState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LightBlock;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Свет без модов: невидимые блоки света (minecraft:light) там, куда бьёт луч фонарика, или вокруг керосинки.
 * Видно всем игрокам. Заряд тратится раз в секунду; кончился — берём батарейку/масло из инвентаря.
 */
public final class Light {
    private Light() {}

    private static final Set<UUID> ON = new HashSet<>();
    private static final Map<UUID, BlockPos[]> PLACED = new HashMap<>();

    public static boolean isOn(ServerPlayerEntity p) {
        return ON.contains(p.getUuid()) && held(p) != null;
    }

    static ItemStack held(ServerPlayerEntity p) {
        if (p.getMainHandStack().getItem() instanceof FlashlightItem) return p.getMainHandStack();
        if (p.getOffHandStack().getItem() instanceof FlashlightItem) return p.getOffHandStack();
        return null;
    }

    public static void toggle(ServerPlayerEntity p) {
        ItemStack s = held(p);
        if (s == null) {
            p.sendMessage(Text.literal("В руках нет фонарика или лампы"), true);
            return;
        }
        if (ON.remove(p.getUuid())) {
            clear(p);
        } else {
            if (charge(s) <= 0 && !refill(p, s)) {
                p.sendMessage(Text.literal(((FlashlightItem) s.getItem()).beam ? "Нет батареек" : "Нет масла"), true);
                p.getServerWorld().playSound(null, p.getBlockPos(), GContent.sound("battery_dead"), SoundCategory.PLAYERS, 0.7f, 1f);
                return;
            }
            ON.add(p.getUuid());
        }
        p.getServerWorld().playSound(null, p.getBlockPos(), GContent.sound("flashlight_click"), SoundCategory.PLAYERS, 0.6f, 1f);
        NbtCompound m = Net.msg("light");
        m.putBoolean("on", ON.contains(p.getUuid()));
        Net.send(p, m);
    }

    static int charge(ItemStack s) {
        return s.getMaxDamage() - s.getDamage();
    }

    static boolean refill(ServerPlayerEntity p, ItemStack s) {
        Item need = ((FlashlightItem) s.getItem()).beam ? GContent.BATTERY : GContent.OIL;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack x = inv.getStack(i);
            if (x.isOf(need)) {
                x.decrement(1);
                s.setDamage(0);
                p.getServerWorld().playSound(null, p.getBlockPos(), GContent.sound("flashlight_click"), SoundCategory.PLAYERS, 0.5f, 0.6f);
                p.sendMessage(Text.literal(need == GContent.BATTERY ? "Новая батарейка" : "Долил масла"), true);
                return true;
            }
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        long t = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!ON.contains(p.getUuid())) continue;
            ItemStack s = held(p);
            if (s == null || p.isSpectator()) {
                clear(p);
                continue;
            }
            FlashlightItem fl = (FlashlightItem) s.getItem();
            if (t % 20 == 0 && !p.isCreative()) {
                int diff = StoryState.get(p.getServerWorld()).difficulty;
                int cost = diff == 2 && t % 40 == 0 ? 2 : 1;
                s.setDamage(Math.min(s.getMaxDamage(), s.getDamage() + cost));
                if (charge(s) <= 0 && !refill(p, s)) {
                    ON.remove(p.getUuid());
                    clear(p);
                    p.sendMessage(Text.literal(fl.beam ? "Батарейка села" : "Масло кончилось"), true);
                    p.getServerWorld().playSound(null, p.getBlockPos(), GContent.sound("battery_dead"), SoundCategory.PLAYERS, 0.8f, 1f);
                    NbtCompound m = Net.msg("light");
                    m.putBoolean("on", false);
                    Net.send(p, m);
                    continue;
                }
            }
            if (t % 3 != 0) continue;
            ServerWorld w = p.getServerWorld();
            BlockPos a, b2 = null;
            if (fl.beam) {
                Vec3d eye = p.getEyePos(), dir = p.getRotationVec(1f);
                BlockHitResult hit = w.raycast(new RaycastContext(eye, eye.add(dir.multiply(26)), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
                Vec3d end = hit.getType() == HitResult.Type.MISS ? eye.add(dir.multiply(26)) : hit.getPos().subtract(dir.multiply(0.6));
                a = BlockPos.ofFloored(end);
                b2 = BlockPos.ofFloored(eye.add(dir.multiply(Math.min(6, end.distanceTo(eye) * 0.5))));
                stun(p, eye, dir);
            } else {
                a = BlockPos.ofFloored(p.getEyePos());
            }
            place(p, w, a, fl.beam ? 15 : 14, b2, fl.beam ? 10 : 0);
        }
    }

    /** Луч оглушает Хора, если бьёт в него. */
    static void stun(ServerPlayerEntity p, Vec3d eye, Vec3d dir) {
        for (com.yompix.glukhoman.entity.PoleEntity c : p.getServerWorld().getEntitiesByClass(com.yompix.glukhoman.entity.PoleEntity.class, new Box(p.getBlockPos()).expand(24), e -> true)) {
            Vec3d to = c.getPos().add(0, 2.6, 0).subtract(eye);
            if (to.length() < 24 && to.normalize().dotProduct(dir) > 0.96 && c.canSee(p)) c.lit(p);
        }
        for (ChoirEntity c : p.getServerWorld().getEntitiesByClass(ChoirEntity.class, new Box(p.getBlockPos()).expand(20), e -> true)) {
            Vec3d to = c.getPos().add(0, 1.6, 0).subtract(eye);
            if (to.length() < 20 && to.normalize().dotProduct(dir) > 0.94 && c.canSee(p)) c.stun(p);
        }
    }

    static void place(ServerPlayerEntity p, ServerWorld w, BlockPos a, int level, BlockPos b, int levelB) {
        BlockPos[] old = PLACED.get(p.getUuid());
        BlockPos[] now = {a, b};
        if (old != null) for (BlockPos o : old) if (o != null && !o.equals(a) && !o.equals(b) && w.getBlockState(o).isOf(Blocks.LIGHT)) w.removeBlock(o, false);
        if (w.getBlockState(a).isAir()) w.setBlockState(a, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, level), 3);
        if (b != null && levelB > 0 && w.getBlockState(b).isAir()) w.setBlockState(b, Blocks.LIGHT.getDefaultState().with(LightBlock.LEVEL_15, levelB), 3);
        PLACED.put(p.getUuid(), now);
    }

    public static void clear(ServerPlayerEntity p) {
        BlockPos[] old = PLACED.remove(p.getUuid());
        if (old == null) return;
        for (BlockPos o : old) if (o != null && p.getServerWorld().getBlockState(o).isOf(Blocks.LIGHT)) p.getServerWorld().removeBlock(o, false);
    }

    public static void off(ServerPlayerEntity p) {
        ON.remove(p.getUuid());
        clear(p);
    }
}
