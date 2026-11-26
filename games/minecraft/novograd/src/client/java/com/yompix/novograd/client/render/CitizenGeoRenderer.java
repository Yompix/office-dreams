package com.yompix.novograd.client.render;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Жители на GeckoLib: модель с локтями и коленями, покадровые анимации (ходьба, бег, разговор, танец…),
 * поверх — объёмные причёски, бороды, моргание и губы из {@link HairFeature}, предмет в руке.
 */
public class CitizenGeoRenderer extends GeoEntityRenderer<CitizenEntity> {
    public static final Identifier[] SKINS = new Identifier[CitizenEntity.SKINS.length];
    private static final Identifier WIDE = Novograd.id("geo/citizen.geo.json"), SLIM = Novograd.id("geo/citizen_slim.geo.json");
    private static final Identifier ANIM = Novograd.id("animations/citizen.animation.json");

    static {
        for (int i = 0; i < SKINS.length; i++) SKINS[i] = Novograd.id("textures/entity/citizen/" + CitizenEntity.SKINS[i] + ".png");
    }

    public CitizenGeoRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Model());
        shadowRadius = 0.45f;
        HairFeature hair = new HairFeature(null);
        addRenderLayer(new GeoRenderLayer<>(this) {
            // 0.8: позы костей запоминаем по ходу модели, а рисуем всё ПОСЛЕ неё и по слоям (пряди → сплошные → стёкла):
            // раньше причёска/лицо/юбка рисовались посреди модели, и каждая смена слоя рвала пачку отрисовки (8–10 на жителя)
            private final org.joml.Matrix4f[] pos = {new org.joml.Matrix4f(), new org.joml.Matrix4f(), new org.joml.Matrix4f()};
            private final org.joml.Matrix3f[] nrm = {new org.joml.Matrix3f(), new org.joml.Matrix3f(), new org.joml.Matrix3f()};
            private final boolean[] has = new boolean[3]; // 0 голова, 1 грудь, 2 пояс
            private final MatrixStack tmp = new MatrixStack();

            @Override
            public void renderForBone(MatrixStack m, CitizenEntity e, GeoBone bone, RenderLayer type, VertexConsumerProvider buffers, VertexConsumer buffer,
                                      float partialTick, int light, int overlay) {
                String n = bone.getName();
                int k = n.equals("head") ? 0 : n.equals("chest") ? 1 : n.equals("waist") ? 2 : -1;
                if (k < 0) return;
                // вдали (дальше «Производительность» → дальняя граница) причёску, лицо и юбку не дорисовываем — их не различить
                float far = com.yompix.novograd.client.NovogradConfig.lodFar();
                var camPos = net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
                if (camPos.squaredDistanceTo(e.getX(), e.getY(), e.getZ()) > far * far) return;
                // из пространства модели GeckoLib (y вверх, пиксели) в пространство ванильной модели
                m.push();
                m.translate(0, 24 / 16f, 0);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
                pos[k].set(m.peek().getPositionMatrix());
                nrm[k].set(m.peek().getNormalMatrix());
                has[k] = true;
                m.pop();
            }

            @Override
            public void render(MatrixStack m, CitizenEntity e, software.bernie.geckolib.cache.object.BakedGeoModel model, RenderLayer type,
                               VertexConsumerProvider buffers, VertexConsumer buffer, float partialTick, int light, int overlay) {
                if (!has[0] && !has[1] && !has[2]) return;
                HairFeature.Compiled c = hair.compiled(e);
                // 0.9.7: форма груди у жительниц (и рельеф у спортивных) — из того же скина; висящее на груди — вперёд
                var gen = com.yompix.novograd.client.style.CitizenLooks.get(e);
                HairFeature.bustPush = gen != null ? com.yompix.novograd.client.style.BodyShape.depth(gen.look()) : 0;
                if (gen != null && has[1]) {
                    var tex = com.yompix.novograd.client.style.SkinComposer.peek(gen.key());
                    if (tex != null) {
                        tmp.peek().getPositionMatrix().set(pos[1]);
                        tmp.peek().getNormalMatrix().set(nrm[1]);
                        com.yompix.novograd.client.style.BodyShape.render(tmp, buffers, tex, light, overlay, gen.look(), e.getId(), e,
                                e.limbAnimator.getPos(partialTick), e.limbAnimator.getSpeed(partialTick), partialTick);
                    }
                }
                if (c != null) {
                    boolean blink = HairFeature.blink(e.getId(), e.age), talk = HairFeature.talking(e), asleep = e.asleep();
                    for (int pass = 0; pass < 3; pass++) {
                        for (int k = 0; k < 3; k++) {
                            if (!has[k] || k > 0 && (pass == 2 || !c.body())) continue;
                            tmp.peek().getPositionMatrix().set(pos[k]);
                            tmp.peek().getNormalMatrix().set(nrm[k]);
                            if (k == 0) {
                                hair.headPass(tmp, buffers, light, c, pass, blink, asleep, talk, e.getId());
                                if (pass == 1) com.yompix.novograd.client.HolidayHats.render(tmp, buffers, light, e.getId());
                            } else hair.bodyPass(tmp, buffers, light, c, pass, k == 1, k == 2);
                        }
                    }
                }
                HairFeature.bustPush = 0;
                has[0] = has[1] = has[2] = false;
            }
        });
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, CitizenEntity e) {
                return bone.getName().equals("right_hand") ? e.getMainHandStack() : null;
            }

            @Override
            protected ModelTransformationMode getTransformTypeForStack(GeoBone bone, ItemStack stack, CitizenEntity e) {
                return ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(MatrixStack m, GeoBone bone, ItemStack stack, CitizenEntity e, VertexConsumerProvider buffers,
                                              float partialTick, int light, int overlay) {
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                m.translate(0, 0.05, -0.05);
                super.renderStackForBone(m, bone, stack, e, buffers, partialTick, light, overlay);
            }
        });
    }

    @Override
    public void render(CitizenEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        if (e.isInvisible()) return; // ушёл к себе в комнату
        // 0.9.6: телосложение и рост из генератора (только картинка — хитбокс прежний); голову не растягиваем
        var look = com.yompix.novograd.client.style.CitizenLooks.get(e);
        if (look != null && (look.width() != 1f || look.height() != 1f)) {
            m.push();
            m.scale(look.width(), look.height(), look.width());
            BODY_W.set(look.width());
            super.render(e, yaw, tickDelta, m, buffers, light);
            BODY_W.set(1f);
            m.pop();
        } else {
            BODY_W.set(1f);
            super.render(e, yaw, tickDelta, m, buffers, light);
        }
        if (e.asleep()) {
            // «Z z z» поднимаются над спящим
            var tr = getTextRenderer();
            float time = (e.age + tickDelta) * 0.03f;
            for (int i = 0; i < 3; i++) {
                float k = (time + i / 3f) % 1f;
                m.push();
                m.translate(0.15 + k * 0.35, 0.55 + k * 0.9, 0);
                m.multiply(dispatcher.getRotation());
                float sc = 0.02f + k * 0.025f;
                m.scale(sc, -sc, sc);
                int alpha = (int) (255 * Math.sin(k * Math.PI));
                tr.draw("Z", -tr.getWidth("Z") / 2f, 0, (Math.max(alpha, 8) << 24) | 0xDDEEFF, false, m.peek().getPositionMatrix(), buffers,
                        net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
                m.pop();
            }
        }
        String say = e.saying();
        if (!say.isEmpty() && dispatcher.getSquaredDistanceToCamera(e) < 24 * 24) {
            // реплика прохожего: короткая строка в «облачке» над головой
            m.push();
            m.translate(0, e.getHeight() + (e.hasCustomName() && e.isCustomNameVisible() ? 0.85f : 0.55f), 0);
            m.multiply(dispatcher.getRotation());
            m.scale(0.022f, -0.022f, 0.022f);
            var tr = getTextRenderer();
            float x = -tr.getWidth(say) / 2f;
            tr.draw(say, x, 0, 0xFFFFFFFF, false, m.peek().getPositionMatrix(), buffers,
                    net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0x99202830, light);
            m.pop();
        }
        if (e.hasQuest()) {
            m.push();
            float bob = MathHelper.sin((e.age + tickDelta) * 0.12f) * 0.08f;
            m.translate(0, e.getHeight() + 0.75f + bob, 0);
            m.multiply(dispatcher.getRotation());
            m.scale(0.06f, -0.06f, 0.06f);
            var tr = getTextRenderer();
            tr.draw("!", -tr.getWidth("!") / 2f, 0, 0xFFFFD000, true, m.peek().getPositionMatrix(), buffers,
                    net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            m.pop();
        }
    }

    /** Сбитый машиной (и любой погибший житель) падает анимацией и лежит — без ванильного заваливания набок. */
    @Override
    protected float getDeathMaxRotation(CitizenEntity e) {
        return e.knockPhase() >= CitizenEntity.KO_DEAD ? 0 : super.getDeathMaxRotation(e);
    }

    /** Красная вспышка — только в момент удара, а не всё время, пока тело лежит. */
    @Override
    public int getPackedOverlay(CitizenEntity e, float u, float partialTick) {
        if (e.knockPhase() >= CitizenEntity.KO_DEAD)
            return net.minecraft.client.render.OverlayTexture.packUv(net.minecraft.client.render.OverlayTexture.getU(u), net.minecraft.client.render.OverlayTexture.getV(e.hurtTime > 0));
        return super.getPackedOverlay(e, u, partialTick);
    }

    /** 1.0: дальних жителей не рисуем вовсе (граница — от «Производительности»), актёров кат-сцены — всегда. */
    @Override
    public boolean shouldRender(CitizenEntity e, net.minecraft.client.render.Frustum frustum, double x, double y, double z) {
        // 0.9.7: герои и массовка чужого сюжета (мультиплеер) — не видны
        var me = net.minecraft.client.MinecraftClient.getInstance().player;
        if (me != null && !e.visibleTo(me.getUuid())) return false;
        float lim = com.yompix.novograd.client.NovogradConfig.citizenDist();
        if (e.squaredDistanceTo(x, y, z) > lim * lim && !com.yompix.novograd.client.story.CutsceneScreen.ACTORS.containsValue(e.getId())) return false;
        return super.shouldRender(e, frustum, x, y, z);
    }

    /** Ширина тела текущего жителя (голову масштабируем обратно, чтобы не «плыла»). */
    static final ThreadLocal<Float> BODY_W = ThreadLocal.withInitial(() -> 1f);

    static class Model extends GeoModel<CitizenEntity> {
        @Override
        public Identifier getModelResource(CitizenEntity e) {
            return CitizenEntity.isSlim(e.getSkin()) ? SLIM : WIDE;
        }

        @Override
        public Identifier getTextureResource(CitizenEntity e) {
            // 0.9.6: внешность из генератора — свой скин (собирается один раз на зерно)
            Identifier gen = com.yompix.novograd.client.style.CitizenLooks.texture(e);
            return gen != null ? gen : SKINS[e.getSkin()];
        }

        @Override
        public Identifier getAnimationResource(CitizenEntity e) {
            return ANIM;
        }

        @Override
        public RenderLayer getRenderType(CitizenEntity e, Identifier texture) {
            return RenderLayer.getEntityTranslucent(texture);
        }

        /** Голова поворачивается туда, куда смотрит житель. */
        @Override
        public void setCustomAnimations(CitizenEntity e, long instanceId, AnimationState<CitizenEntity> state) {
            var head = getAnimationProcessor().getBone("neck");
            var data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            var headBone = getAnimationProcessor().getBone("head");
            if (headBone != null) {
                float inv = 1f / BODY_W.get();
                headBone.setScaleX(inv);
                headBone.setScaleZ(inv);
            }
            if (head != null && (e.asleep() || e.knockPhase() != CitizenEntity.KO_NONE)) { head.setRotX(0); head.setRotY(0); head.setRotZ(0); return; }
            if (head != null && data != null) {
                // ставим, а не прибавляем: иначе поворот копится каждый кадр и голову «колбасит»
                head.setRotX(data.headPitch() * MathHelper.RADIANS_PER_DEGREE);
                head.setRotY(data.netHeadYaw() * MathHelper.RADIANS_PER_DEGREE);
                head.setRotZ(0);
                // 1.0: в кат-сцене — живой взгляд (на говорящего / на собеседника), мягко, с кивками на ударных слогах
                float[] look = com.yompix.novograd.client.story.CutsceneScreen.look(e);
                if (look != null) {
                    head.setRotX(look[1] * MathHelper.RADIANS_PER_DEGREE);
                    head.setRotY(look[0] * MathHelper.RADIANS_PER_DEGREE);
                    head.setRotZ(look[2] * MathHelper.RADIANS_PER_DEGREE);
                }
                else {
                    // 0.8: герой сюжета смотрит на игрока, когда тот рядом (плавно смешиваем с обычным поворотом головы)
                    float[] lp = com.yompix.novograd.client.story.StoryLife.look(e, state.getPartialTick());
                    if (lp != null) {
                        float w = lp[2];
                        head.setRotX(MathHelper.lerp(w, head.getRotX(), lp[1] * MathHelper.RADIANS_PER_DEGREE));
                        head.setRotY(MathHelper.lerp(w, head.getRotY(), lp[0] * MathHelper.RADIANS_PER_DEGREE));
                    }
                }
                // 0.8: своя походка героя: раскачка корпуса на шаге (клип ходьбы сам ставит грудь каждый кадр)
                float sway = com.yompix.novograd.client.story.StoryLife.sway(e);
                var chest = getAnimationProcessor().getBone("chest");
                double mv = e.getVelocity().horizontalLengthSquared() + (e.getX() - e.prevX) * (e.getX() - e.prevX) + (e.getZ() - e.prevZ) * (e.getZ() - e.prevZ);
                if (sway > 0 && chest != null && mv > 2.5e-4) {
                    float ph = e.limbAnimator.getPos(state.getPartialTick()) * 0.6662f;
                    chest.setRotZ(chest.getRotZ() + MathHelper.sin(ph) * sway * MathHelper.RADIANS_PER_DEGREE);
                    chest.setRotY(chest.getRotY() + MathHelper.cos(ph) * sway * 0.6f * MathHelper.RADIANS_PER_DEGREE);
                }
            }
        }
    }
}
