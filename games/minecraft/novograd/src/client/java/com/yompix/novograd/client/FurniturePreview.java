package com.yompix.novograd.client;

import com.yompix.novograd.world.HomeLife;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * 0.9.10: предпросмотр мебели — полупрозрачная модель там, куда встанет предмет, с тем же поворотом,
 * что поставит сервер (HomeLife.facing). Z/X — повернуть на четверть.
 */
public final class FurniturePreview {
    private FurniturePreview() {}

    static int rot;
    private static boolean hinted;

    public static boolean holding(MinecraftClient mc) {
        if (mc.player == null || mc.player.hasVehicle()) return false;
        ItemStack st = mc.player.getMainHandStack();
        return HomeLife.isFurniture(st) && st.getItem() instanceof BlockItem bi && bi.getBlock().getDefaultState().contains(HorizontalFacingBlock.FACING);
    }

    public static void rotate(int d) {
        rot = Math.floorMod(rot + d, 4);
        NbtCompound n = new NbtCompound();
        n.putInt("r", rot);
        NovogradClient.send("home_rot", n);
    }

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!holding(mc) || mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!hinted) {
            hinted = true;
            NbtCompound n = new NbtCompound();
            n.putInt("r", rot); // сервер мог забыть поворот после перезахода
            NovogradClient.send("home_rot", n);
            mc.player.sendMessage(Text.literal("Z / X — повернуть · ПКМ — поставить").formatted(Formatting.GOLD), true);
        }
        ItemStack st = mc.player.getMainHandStack();
        BlockItem bi = (BlockItem) st.getItem();
        ItemPlacementContext pc = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, st, hit);
        BlockPos at = pc.getBlockPos();
        boolean ok = pc.canPlace() && mc.world.getBlockState(at).isReplaceable();
        BlockState state = bi.getBlock().getDefaultState().with(HorizontalFacingBlock.FACING, HomeLife.facing(mc.player.getHorizontalFacing(), rot));
        Vec3d cam = ctx.camera().getPos();
        MatrixStack m = ctx.matrixStack();
        m.push();
        m.translate(at.getX() - cam.x, at.getY() - cam.y, at.getZ() - cam.z);
        // лёгкое «дыхание», чтобы призрак не путали с настоящей мебелью
        float pulse = 0.55f + 0.12f * (float) Math.sin((mc.world.getTime() + ctx.tickCounter().getTickDelta(false)) * 0.25);
        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = new Ghost(imm.getBuffer(TexturedRenderLayers.getEntityTranslucentCull()), (int) (pulse * 255));
        float r = ok ? 0.75f : 1f, g = ok ? 1f : 0.45f, b = ok ? 0.8f : 0.45f;
        mc.getBlockRenderManager().getModelRenderer().render(m.peek(), vc, state, mc.getBlockRenderManager().getModel(state), r, g, b,
                WorldRenderer.getLightmapCoordinates(mc.world, at), OverlayTexture.DEFAULT_UV);
        imm.draw(TexturedRenderLayers.getEntityTranslucentCull());
        m.pop();
    }

    /** Обёртка: та же геометрия, но полупрозрачная. */
    private record Ghost(VertexConsumer d, int alpha) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            d.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            d.color(r, g, b, Math.min(a, alpha));
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            d.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            d.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            d.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            d.normal(x, y, z);
            return this;
        }
    }
}
