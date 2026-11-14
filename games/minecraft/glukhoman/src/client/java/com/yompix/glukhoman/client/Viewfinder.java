package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Видоискатель на клиенте: камера у глаза (узкий кадр, полный VHS, REC, батарея), ИК-режим (ночное видение + зелень),
 * «рельсы там, где их сняли» — ночью в кадре на голых шпалах снова лежат рельсы (только на этом клиенте).
 */
public final class Viewfinder {
    private Viewfinder() {}

    public static boolean up, ir;
    static boolean nv;
    static final Map<BlockPos, BlockState> GHOST = new LinkedHashMap<>();

    /** Смотрим через камеру (сам игрок или кат-сцена «в видоискателе»). */
    public static boolean view() {
        return Cine.active ? Cine.vf : up;
    }

    public static float battery() {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null) return 0;
        ItemStack s = c.player.getMainHandStack();
        if (!s.isOf(GContent.CAMCORDER)) return 0;
        return 1f - s.getDamage() / (float) s.getMaxDamage();
    }

    static boolean night(MinecraftClient c) {
        long t = c.world.getTimeOfDay() % 24000;
        return t > 12600 && t < 23400;
    }

    static void tick(MinecraftClient c) {
        if (c.player == null || c.world == null) return;
        boolean wantNv = up && ir && !Cine.active;
        if (wantNv) {
            c.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false));
            nv = true;
        } else if (nv) {
            c.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            nv = false;
        }
        if (!Cine.active) ghostRails(c.world, view() && night(c) && GlukhomanClient.chapter > 0);
    }

    /** Рельсы, которых нет: x 63…150 по оси пути. */
    public static void ghostRails(ClientWorld w, boolean on) {
        if (on == !GHOST.isEmpty()) return;
        if (on) {
            BlockState rail = Blocks.RAIL.getDefaultState().with(RailBlock.SHAPE, RailShape.EAST_WEST);
            for (int x = 63; x <= 150; x++) {
                BlockPos p = new BlockPos(x, 66, 16);
                BlockState s = w.getBlockState(p);
                if (!(s.isAir() || s.isOf(GContent.FOOTPRINTS) || s.getBlock() instanceof net.minecraft.block.TrapdoorBlock)) continue;
                if (!w.getBlockState(p.down()).isSolidBlock(w, p.down())) continue;
                GHOST.put(p, s);
                w.setBlockState(p, rail, 0x10 | 3);
            }
        } else {
            for (var e : GHOST.entrySet()) if (w.getBlockState(e.getKey()).isOf(Blocks.RAIL)) w.setBlockState(e.getKey(), e.getValue(), 0x10 | 3);
            GHOST.clear();
        }
    }

    static void reset() {
        up = ir = false;
        GHOST.clear();
        nv = false;
    }
}
