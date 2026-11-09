package com.yompix.novograd.client;

import com.yompix.novograd.entity.CarEntity;
import com.yompix.novograd.entity.DebrisEntity;
import com.yompix.novograd.world.Props;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Клиент «живой улицы»: обломки и падающие столбы, искры, когда машина трётся о стену. */
public final class PropsClient {
    private PropsClient() {}

    public static void init() {
        EntityRendererRegistry.register(Props.DEBRIS, DebrisRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(), Props.VENDING, Props.PAYPHONE, Props.NEWS_STAND, Props.HOTDOG_CART,
                Props.ICECREAM_CART, Props.PROPANE, Props.CONE, Props.LAMP_BROKEN);
        ClientTickEvents.END_CLIENT_TICK.register(PropsClient::tick);
        PropsAutotest.init();
    }

    private static int scrapeSound;

    /** Машина игрока трётся о стену на скорости: искры у борта и скрежет (физику машины игрока считает клиент). */
    private static void tick(MinecraftClient c) {
        if (c.player == null || c.world == null || c.isPaused()) return;
        if (!(c.player.getVehicle() instanceof CarEntity car) || car.getControllingPassenger() != c.player) return;
        if (scrapeSound > 0) scrapeSound--;
        float sp = Math.abs(car.speed);
        if (!car.horizontalCollision || sp < 0.06f) return;
        double rad = Math.toRadians(car.getYaw());
        Vec3d fwd = new Vec3d(-Math.sin(rad), 0, Math.cos(rad)), side = new Vec3d(-Math.cos(rad), 0, -Math.sin(rad));
        // с какой стороны стена: пробуем точки у бортов и у носа
        Vec3d base = car.getPos().add(0, 0.5, 0);
        Vec3d hit = null;
        for (Vec3d probe : new Vec3d[]{side.multiply(1.3), side.multiply(-1.3), fwd.multiply(2.3).add(side.multiply(0.7)), fwd.multiply(2.3).add(side.multiply(-0.7)), fwd.multiply(2.4)}) {
            BlockPos bp = BlockPos.ofFloored(base.add(probe));
            if (!c.world.getBlockState(bp).getCollisionShape(c.world, bp).isEmpty()) {
                hit = base.add(probe.multiply(0.8));
                break;
            }
        }
        if (hit == null) return;
        var r = c.world.random;
        int n = 3 + (int) (sp * 8);
        for (int i = 0; i < n; i++) {
            Vec3d v = fwd.multiply(-0.15 - r.nextDouble() * 0.25).add((r.nextDouble() - 0.5) * 0.2, 0.08 + r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.2);
            c.world.addParticle(i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y + r.nextDouble() * 0.4, hit.z, v.x, v.y, v.z);
        }
        if (scrapeSound == 0) {
            scrapeSound = 6;
            c.world.playSound(c.player, BlockPos.ofFloored(hit), SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.NEUTRAL, 0.6f + sp, 0.8f + sp * 0.4f);
        }
    }

    /** Обломок: блок, кувыркающийся в воздухе, или столб, заваливающийся набок. */
    static final class DebrisRenderer extends EntityRenderer<DebrisEntity> {
        private final BlockRenderManager blocks;

        DebrisRenderer(EntityRendererFactory.Context ctx) {
            super(ctx);
            blocks = ctx.getBlockRenderManager();
            shadowRadius = 0.3f;
        }

        @Override
        public void render(DebrisEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light) {
            m.push();
            int len = e.poleLength();
            if (len > 0) {
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-e.getYaw()));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(e.poleAngle(tickDelta)));
                for (int i = 0; i < len; i++) {
                    BlockState st = i == len - 1 ? e.top() : e.state();
                    m.push();
                    m.translate(-0.5, i, -0.5);
                    blocks.renderBlockAsEntity(st, m, vcp, light, OverlayTexture.DEFAULT_UV);
                    m.pop();
                }
            } else {
                float s = e.scale();
                float left = e.life() - e.age - tickDelta;
                if (left < 20) s *= Math.max(0, left / 20f); // исчезает, сжимаясь
                float rot = MathHelper.lerp(tickDelta, e.prevRot, e.rot);
                m.translate(0, 0.5 * s, 0);
                m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(e.getYaw()));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rot));
                m.scale(s, s, s);
                m.translate(-0.5, -0.5, -0.5);
                blocks.renderBlockAsEntity(e.state(), m, vcp, light, OverlayTexture.DEFAULT_UV);
            }
            m.pop();
            super.render(e, yaw, tickDelta, m, vcp, light);
        }

        @Override
        public boolean shouldRender(DebrisEntity e, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
            if (e.poleLength() > 0) return e.squaredDistanceTo(x, y, z) < 96 * 96; // столб длинный — его бокс у основания
            return super.shouldRender(e, frustum, x, y, z);
        }

        @Override
        public Identifier getTexture(DebrisEntity e) {
            return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
        }
    }
}
