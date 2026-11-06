package com.yompix.novograd.client.render;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.render.car.CarModels;
import com.yompix.novograd.entity.CarBody;
import com.yompix.novograd.entity.CarEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Машины из кубиков: у каждого типа кузова свой силуэт (CarModels) — седан, хэтчбек, спорткупе, люкс, фургон, такси,
 * полиция, внедорожник, пикап, маслкар, лимузин, грузовик. Колёса с дисками крутятся от пути, передние поворачивают;
 * кузов на подвеске кренится, клюёт носом при торможении и приседает при разгоне; повреждения видны: вмятины,
 * трещины и выбитые стёкла, разбитая фара, отвисший бампер.
 */
public class CarRenderer extends EntityRenderer<CarEntity> {
    /** Слой оставлен для регистрации в NovogradClient; сами кузова собираются в CarModels. */
    public static final EntityModelLayer LAYER = new EntityModelLayer(Novograd.id("car"), "main");
    private static final Identifier BASE = Novograd.id("textures/entity/car/base.png");
    private static final int FULL = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    private static final int OV = OverlayTexture.DEFAULT_UV;

    public CarRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 1.4f;
    }

    public static TexturedModelData modelData() {
        return TexturedModelData.of(new ModelData(), 64, 64);
    }

    @Override
    public void render(CarEntity car, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        CarBody type = car.body();
        CarModels.CarModel model = CarModels.get(type);
        ModelPart root = model.root;
        VertexConsumer solid = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(BASE));
        int hp = car.health();
        boolean wrecked = car.wrecked();

        m.push();
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, car.prevYaw, car.getYaw());
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));

        // ---- колёса: на земле, без крена кузова
        float wheelRot = MathHelper.lerp(tickDelta, car.prevWheelRot, car.wheelRot) * 5f / model.wheelR;
        float steer = MathHelper.lerp(tickDelta, car.prevVisualSteer, car.visualSteer) * 0.5f;
        int tyre = wrecked ? 0xFF0E0E0E : 0xFF1B1B1D;
        int rim = wrecked ? 0xFF3A3A3A : rimColor(type, car);
        for (int i = 0; i < model.wheels.length; i++) {
            float[] w = model.wheels[i];
            m.push();
            m.translate(w[0] / 16f, model.wheelR / 16f, w[1] / 16f);
            if (i < 2) m.multiply(RotationAxis.POSITIVE_Y.rotation(steer));
            m.multiply(RotationAxis.POSITIVE_X.rotation(wheelRot));
            wheel(model.wheel, m, solid, light, tyre, rim);
            m.pop();
        }

        // ---- кузов на подвеске
        float roll = MathHelper.lerp(tickDelta, car.prevRoll, car.roll);
        float pitch = MathHelper.lerp(tickDelta, car.prevPitchLean, car.pitchLean);
        float susp = MathHelper.lerp(tickDelta, car.prevSuspY, car.suspY);
        float wobble = car.getWobble() > 0 ? MathHelper.sin((car.getWobble() - tickDelta) * 1.3f) * car.getWobble() * 0.4f : 0;
        float idle = car.getControllingPassenger() != null || car.hasNpcDriver() ? MathHelper.sin((car.age + tickDelta) * 2.1f) * 0.12f : 0; // мотор потряхивает
        m.translate(0, (0.3f + susp * 0.35f + idle) / 16f, 0);
        m.translate(0, 0.5f, 0);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll + wobble));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        m.translate(0, -0.5f, 0);

        int color = 0xFF000000 | car.color();
        // грязь и вмятины: чем меньше здоровья, тем тусклее краска
        float dull = wrecked ? 0.25f : 1 - (100 - Math.max(0, hp)) / 100f * 0.22f;
        color = LifeShade.shade(color, dull);
        long time = car.getWorld().getTimeOfDay() % 24000;
        boolean night = time > 12300 && time < 23700;
        boolean driven = car.getControllingPassenger() != null || car.hasNpcDriver();

        int under = 0xFF232427, trim = wrecked ? 0xFF111111 : 0xFF1C1D20;
        p(root, "under", m, solid, light, under);
        p(root, "paint", m, solid, light, color);
        p(root, "mirror_glass", m, solid, light, 0xFF8FA4B8);
        int hood = type == CarBody.POLICE && car.color() == CarEntity.COLORS[CarEntity.POLICE] ? LifeShade.shade(0xFF16243F, dull) : LifeShade.shade(color, 0.95f);
        p(root, "hood", m, solid, light, hood);
        p(root, "roof", m, solid, light, type == CarBody.LIMO || type == CarBody.LUXURY ? LifeShade.shade(color, 0.85f) : color);
        p(root, "trim", m, solid, light, trim);
        p(root, "arch", m, solid, light, 0xFF101012);
        p(root, "chrome", m, solid, light, wrecked ? 0xFF404040 : 0xFFD2D6DC);
        p(root, "exhaust_tip", m, solid, light, 0xFF0A0A0A);
        p(root, "grille", m, solid, light, 0xFF111214);
        p(root, "plate", m, solid, light, 0xFFF2F2F0);
        p(root, "plate_txt", m, solid, light, 0xFF1A2550);
        int seat = type == CarBody.TAXI ? 0xFF4B3A2A : type == CarBody.LUXURY || type == CarBody.LIMO ? 0xFFC9B48A : type == CarBody.SPORTS ? 0xFF8A1E1E : 0xFF33363D;
        p(root, "seat", m, solid, light, seat);
        p(root, "seam", m, solid, light, LifeShade.shade(seat, 0.72f));
        p(root, "dash", m, solid, light, 0xFF26282D);
        p(root, "floor", m, solid, light, 0xFF1C1D20);
        p(root, "inner", m, solid, light, type == CarBody.TAXI ? 0xFF5A4A3A : LifeShade.shade(seat, 1.15f));
        p(root, "headliner", m, solid, light, 0xFFD4CFC4);
        p(root, "gauges", m, solid, driven ? FULL : light, driven ? 0xFF6FE3FF : 0xFF203038);
        ModelPart wheelPart = root.getChild("steering");
        wheelPart.roll = -steer * 2.4f;
        wheelPart.render(m, solid, light, OV, 0xFF141414);
        // полоски, ливрея, служебная окраска
        boolean lightPaint = brightness(car.color()) > 140;
        p(root, "stripe", m, solid, light, type == CarBody.POLICE ? 0xFFE8B400 : type == CarBody.TRUCK ? 0xFFD7262C : lightPaint ? 0xFF15161A : 0xFFF2F2F2);
        p(root, "livery", m, solid, light, LifeShade.shade(0xFF16243F, dull));
        p(root, "accent", m, solid, light, type == CarBody.TRUCK ? LifeShade.shade(0xFFEDEDE8, dull) : type == CarBody.VAN ? 0xFFE0662A : color);
        p(root, "pushbar", m, solid, light, 0xFF141414);
        p(root, "check", m, solid, light, 0xFF141414);
        // вмятины и царапины
        if (hp < 80 || wrecked) p(root, "dent1", m, solid, light, dent(color, 0.62f));
        if (hp < 55 || wrecked) p(root, "dent2", m, solid, light, dent(color, 0.55f));
        if (hp < 32 || wrecked) p(root, "dent3", m, solid, light, dent(color, 0.5f));
        // передний бампер повисает на одном креплении
        ModelPart bumper = root.getChild("bumper_f");
        bumper.roll = hp < 45 && !wrecked ? 0.16f : wrecked ? 0.3f : 0;
        bumper.pitch = hp < 45 ? 0.08f : 0;
        bumper.render(m, solid, light, OV, type == CarBody.MUSCLE || type == CarBody.PICKUP || type == CarBody.TRUCK ? 0xFFD2D6DC : type == CarBody.SPORTS ? color : trim);

        // ---- свет: фары по выключателю, габариты у заведённой, стопы ярче при торможении, задний ход, поворотники
        boolean head = car.light(CarEntity.HEADLIGHTS) && !wrecked;
        boolean leftBroken = hp < 35 || wrecked;
        p(root, "head", m, solid, head ? FULL : light, head ? 0xFFFFF6C8 : 0xFFD8D4C0);
        if (leftBroken) p(root, "head_l", m, solid, light, 0xFF2A2A28);
        else p(root, "head_l", m, solid, head ? FULL : light, head ? 0xFFFFF6C8 : 0xFFD8D4C0);
        float br = car.brakeGlow;
        boolean tailOn = (br > 0.1f || driven || head) && !wrecked;
        int tail = br > 0.1f ? 0xFFFF1A1A : driven || head ? 0xFFC42020 : 0xFF8E1A1A;
        p(root, "tail", m, solid, tailOn ? FULL : light, tailOn ? tail : 0xFF8E1A1A);
        if (hp < 20 || wrecked) p(root, "tail_l", m, solid, light, 0xFF301010);
        else p(root, "tail_l", m, solid, tailOn ? FULL : light, tailOn ? tail : 0xFF8E1A1A);
        boolean rev = car.reverseGlow > 0.1f && !wrecked;
        p(root, "rev", m, solid, rev ? FULL : light, rev ? 0xFFFFFFFF : 0xFF9A9A9A);
        boolean blink = (car.getWorld().getTime() / 8) % 2 == 0;
        boolean hz = car.light(CarEntity.HAZARD);
        boolean l = blink && (hz || car.light(CarEntity.LEFT)) && !wrecked, rr = blink && (hz || car.light(CarEntity.RIGHT)) && !wrecked;
        p(root, "sig_l", m, solid, l ? FULL : light, l ? 0xFFFFA000 : 0xFF8A5A10);
        p(root, "sig_r", m, solid, rr ? FULL : light, rr ? 0xFFFFA000 : 0xFF8A5A10);
        if (type == CarBody.TAXI) {
            p(root, "bar_base", m, solid, light, 0xFF141414);
            p(root, "taxi_sign", m, solid, driven ? FULL : light, driven ? 0xFFFFE14D : 0xFFC9A632);
        }
        if (type == CarBody.POLICE) {
            boolean phase = (car.getWorld().getTime() / 5) % 2 == 0;
            boolean siren = driven || car.light(CarEntity.HAZARD);
            p(root, "bar_base", m, solid, light, 0xFF141414);
            p(root, "bar_red", m, solid, siren && phase ? FULL : light, siren && phase ? 0xFFFF2020 : 0xFF601010);
            p(root, "bar_blue", m, solid, siren && !phase ? FULL : light, siren && !phase ? 0xFF2A6AFF : 0xFF10204A);
        }
        if (model.spare != null) {
            m.push();
            m.translate(model.spare[0] / 16f, model.spare[1] / 16f, model.spare[2] / 16f);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
            wheel(model.wheel, m, solid, light, tyre, 0xFF2A2C30);
            m.pop();
        }

        // ---- стёкла: тонировка по типу; трещины на побитой, выбитые боковые на разбитой
        if (!wrecked) {
            VertexConsumer glass = buffers.getBuffer(RenderLayer.getEntityTranslucent(BASE));
            int tint = switch (type) {
                case LIMO, LUXURY -> 0xB0141A22;
                case POLICE, SUV -> 0x90202A36;
                case SPORTS -> 0x88202630;
                default -> 0x6A5A6A84;
            };
            int front = hp < 45 ? 0xA8C8D0D6 : type == CarBody.LIMO || type == CarBody.LUXURY ? 0x7A2A3442 : 0x5A6A8AA8;
            root.getChild("glass").render(m, glass, light, OV, front);
            if (hp >= 22) root.getChild("glass_side").render(m, glass, light, OV, tint);
        }
        if (head) beams(m, buffers, night ? 120 : 35, model, leftBroken);
        m.pop();
        super.render(car, yaw, tickDelta, m, buffers, light);
    }

    private static int rimColor(CarBody t, CarEntity car) {
        return switch (t) {
            case POLICE, VAN, TRUCK -> 0xFF2C2E33;          // стальные диски
            case MUSCLE, LUXURY, LIMO -> 0xFFE2E5EA;        // хром
            case SPORTS -> brightness(car.color()) > 140 ? 0xFF2A2A2E : 0xFFB8BCC4;
            case SUV, PICKUP -> 0xFF6E7278;
            default -> 0xFFB4B9C1;
        };
    }

    /** Вмятина: краска темнеет и сереет (царапины до грунта). */
    private static int dent(int argb, float k) {
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        r = (int) (r * (1 - k) * 0.6f + 52 * k);
        g = (int) (g * (1 - k) * 0.6f + 50 * k);
        b = (int) (b * (1 - k) * 0.6f + 48 * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int brightness(int rgb) {
        return (((rgb >> 16) & 255) * 3 + ((rgb >> 8) & 255) * 6 + (rgb & 255)) / 10;
    }

    private static void wheel(CarModels.Wheel w, MatrixStack m, VertexConsumer vc, int light, int tyre, int rim) {
        w.tyre.render(m, vc, light, OV, tyre);
        w.rim.render(m, vc, light, OV, rim);
        w.disc.render(m, vc, light, OV, LifeShade.shade(rim, 0.45f));
        w.spokes.render(m, vc, light, OV, rim);
        w.cap.render(m, vc, light, OV, 0xFFE8EAEE);
    }

    private static void p(ModelPart root, String name, MatrixStack m, VertexConsumer vc, int light, int color) {
        root.getChild(name).render(m, vc, light, OV, color);
    }

    /** Ночью от фар вперёд расходятся полупрозрачные лучи. */
    private static void beams(MatrixStack m, VertexConsumerProvider buffers, int alpha, CarModels.CarModel model, boolean leftBroken) {
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getLightning());
        var mat = m.peek().getPositionMatrix();
        float hx = model.headX / 16f, y = model.headY / 16f, z = model.headZ / 16f;
        for (float side : new float[]{-hx, hx}) {
            if (side < 0 && leftBroken) continue;
            float len = 7f, spread = 1.4f;
            vc.vertex(mat, side - 0.12f, y + 0.08f, z).color(255, 250, 210, alpha);
            vc.vertex(mat, side + 0.12f, y + 0.08f, z).color(255, 250, 210, alpha);
            vc.vertex(mat, side + spread, y - 0.6f, z + len).color(255, 250, 210, 0);
            vc.vertex(mat, side - spread, y - 0.6f, z + len).color(255, 250, 210, 0);
        }
    }

    @Override
    public Identifier getTexture(CarEntity entity) {
        return BASE;
    }
}
