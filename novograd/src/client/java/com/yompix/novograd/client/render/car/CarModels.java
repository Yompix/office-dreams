package com.yompix.novograd.client.render.car;

import com.yompix.novograd.entity.CarBody;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Кузова машин из кубиков, собранные по размерам: у каждого типа свой силуэт (наклонное лобовое, капот, багажник,
 * арки колёс, стойки), фары и стопы на своих местах и свои детали (мигалка, шашечки, кузов пикапа, будка грузовика...).
 * Координаты в пикселях (16 = блок): y вверх от земли, перед машины в +z, +x — левый борт.
 */
public final class CarModels {
    private CarModels() {}

    /** Детали, которые красятся отдельно (по ним CarRenderer выбирает цвет). */
    public static final String[] GROUPS = {"under", "paint", "hood", "roof", "trim", "chrome", "grille", "glass", "glass_side",
            "head", "head_l", "tail", "tail_l", "rev", "sig_l", "sig_r", "plate", "plate_txt",
            "seat", "seam", "dash", "gauges", "floor", "inner", "headliner",
            "taxi_sign", "check", "bar_base", "bar_red", "bar_blue", "livery", "pushbar",
            "stripe", "accent", "dent1", "dent2", "dent3", "arch", "exhaust_tip", "mirror_glass"};

    private static final Map<CarBody, CarModel> CACHE = new EnumMap<>(CarBody.class);

    public static CarModel get(CarBody b) {
        return CACHE.computeIfAbsent(b, CarModels::build);
    }

    /** Готовая модель: части кузова, колёса и где у неё фары/выхлоп. */
    public static final class CarModel {
        public final ModelPart root;
        public final Wheel wheel;
        /** {x, z} центров колёс в пикселях; первые два — передние (поворачивают). */
        public final float[][] wheels;
        public final float wheelR;
        /** Для лучей фар: x фары, y, z переда. */
        public final float headX, headY, headZ;
        /** Запаска на задней двери (внедорожник): {x, y, z} или null. */
        public final float[] spare;

        CarModel(ModelPart root, Wheel wheel, float[][] wheels, float wheelR, float headX, float headY, float headZ, float[] spare) {
            this.root = root;
            this.wheel = wheel;
            this.wheels = wheels;
            this.wheelR = wheelR;
            this.headX = headX;
            this.headY = headY;
            this.headZ = headZ;
            this.spare = spare;
        }
    }

    /** Колесо вокруг своей оси (ось — x): шина, обод, тёмный диск, спицы, колпачок. */
    public static final class Wheel {
        public final ModelPart tyre, rim, disc, spokes, cap;

        Wheel(float r, float w, boolean steelies) {
            tyre = disc(r, w, 0);
            rim = disc(r * 0.64f, w + 0.3f, 0);
            disc = disc(r * 0.5f, w + 0.36f, 0);
            ModelData d = new ModelData();
            ModelPartData root = d.getRoot();
            int n = steelies ? 4 : 3;
            for (int i = 0; i < n; i++) {
                float a = (float) (i * Math.PI / n);
                root.addChild("s" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-(w + 0.5f) / 2, -r * 0.09f, -r * 0.6f, w + 0.5f, r * 0.18f, r * 1.2f),
                        ModelTransform.of(0, 0, 0, a, 0, 0));
            }
            spokes = TexturedModelData.of(d, 256, 256).createModel();
            cap = disc(r * 0.16f, w + 0.62f, 0);
        }

        /** Почти круглый «диск»: четыре бруска под 45° дают шестнадцатиугольник. */
        private static ModelPart disc(float r, float w, float unused) {
            ModelData d = new ModelData();
            ModelPartData root = d.getRoot();
            float k = r * 0.42f;
            for (int i = 0; i < 4; i++) {
                root.addChild("b" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-w / 2, -r, -k, w, r * 2, k * 2),
                        ModelTransform.of(0, 0, 0, (float) (i * Math.PI / 4), 0, 0));
            }
            return TexturedModelData.of(d, 256, 256).createModel();
        }
    }

    // ------------------------------------------------------------------ сборщик
    private record Rot(float px, float py, float pz, float pitch, float yaw, float roll, float[] c) {}

    private static final class B {
        final Map<String, List<float[]>> boxes = new LinkedHashMap<>();
        final Map<String, List<Rot>> rots = new LinkedHashMap<>();
        final Map<String, Rot> pivots = new LinkedHashMap<>();
        final Map<String, List<float[]>> pivotBoxes = new LinkedHashMap<>();

        B() {
            for (String g : GROUPS) {
                boxes.put(g, new ArrayList<>());
                rots.put(g, new ArrayList<>());
            }
        }

        void box(String g, float x1, float y1, float z1, float x2, float y2, float z2) {
            boxes.get(g).add(new float[]{Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)});
        }

        /** Пара деталей, зеркальных по бортам. */
        void sym(String g, float x1, float y1, float z1, float x2, float y2, float z2) {
            box(g, x1, y1, z1, x2, y2, z2);
            box(g, -x1, y1, z1, -x2, y2, z2);
        }

        void rot(String g, float px, float py, float pz, float pitch, float yaw, float roll, float x1, float y1, float z1, float x2, float y2, float z2) {
            rots.get(g).add(new Rot(px, py, pz, pitch, yaw, roll, new float[]{x1, y1, z1, x2, y2, z2}));
        }

        /** Наклонная пластина от точки A (yA, zA) до B (yB, zB) по ширине x1..x2, толщиной t (капот, лобовое, багажник). */
        void slab(String g, float x1, float x2, float yA, float zA, float yB, float zB, float t) {
            float dy = yB - yA, dz = zB - zA;
            float len = (float) Math.sqrt(dy * dy + dz * dz);
            if (len < 0.01f) return;
            float pitch = (float) Math.atan2(-dy, dz);
            rot(g, 0, yA, zA, pitch, 0, 0, x1, -t / 2, 0, x2, t / 2, len);
        }

        void symSlab(String g, float x1, float x2, float yA, float zA, float yB, float zB, float t) {
            slab(g, x1, x2, yA, zA, yB, zB, t);
            slab(g, -x2, -x1, yA, zA, yB, zB, t);
        }

        /** Заполнить ступеньками треугольник под линией A→B до высоты base (боковые стёкла у наклонных стоек). */
        void tri(String g, float x1, float x2, float base, float yA, float zA, float yB, float zB, int n) {
            for (int i = 0; i < n; i++) {
                float za = zA + (zB - zA) * i / n, zb = zA + (zB - zA) * (i + 1) / n;
                float y = yA + (yB - yA) * (i + 0.5f) / n;
                if (y > base + 0.05f) box(g, x1, base, za, x2, y, zb);
            }
        }

        /** Отдельная подвижная деталь (бампер, руль) со своей точкой поворота. */
        void pivot(String name, float px, float py, float pz, float pitch, float... c) {
            pivots.put(name, new Rot(px, py, pz, pitch, 0, 0, null));
            List<float[]> l = new ArrayList<>();
            for (int i = 0; i + 5 < c.length; i += 6) l.add(new float[]{c[i], c[i + 1], c[i + 2], c[i + 3], c[i + 4], c[i + 5]});
            pivotBoxes.put(name, l);
        }

        ModelPart build() {
            ModelData data = new ModelData();
            ModelPartData root = data.getRoot();
            for (String g : GROUPS) {
                ModelPartBuilder pb = ModelPartBuilder.create().uv(0, 0);
                for (float[] c : boxes.get(g)) pb.cuboid(c[0], c[1], c[2], c[3] - c[0], c[4] - c[1], c[5] - c[2]);
                ModelPartData gd = root.addChild(g, pb, ModelTransform.NONE);
                int i = 0;
                for (Rot r : rots.get(g)) {
                    float[] c = r.c;
                    gd.addChild("r" + i++, ModelPartBuilder.create().uv(0, 0).cuboid(c[0], c[1], c[2], c[3] - c[0], c[4] - c[1], c[5] - c[2]),
                            ModelTransform.of(r.px, r.py, r.pz, r.pitch, r.yaw, r.roll));
                }
            }
            for (var e : pivots.entrySet()) {
                ModelPartBuilder pb = ModelPartBuilder.create().uv(0, 0);
                for (float[] c : pivotBoxes.get(e.getKey())) pb.cuboid(c[0], c[1], c[2], c[3] - c[0], c[4] - c[1], c[5] - c[2]);
                Rot r = e.getValue();
                root.addChild(e.getKey(), pb, ModelTransform.of(r.px, r.py, r.pz, r.pitch, 0, 0));
            }
            return TexturedModelData.of(data, 256, 256).createModel();
        }
    }

    // ------------------------------------------------------------------ размеры кузова
    /** Профиль: где кончается капот, где стоит лобовое, крыша, заднее стекло, багажник; колёса. */
    private static final class S {
        float W = 15, zF = 29.5f, zR = -29, sill = 3.5f, belt = 13.5f, hoodF = 12, trunkR = 13.5f;
        float wsB = 11, roofF = 4, roofR = -14, rwB = -20, roof = 29;
        float inset = 2.4f;        // насколько кабина уже кузова (завал боковин)
        float r = 5, ww = 4.4f, wF = 18.5f, wR = -18.5f;
        float pillarB = -6.5f;     // средняя стойка
        boolean bed, panelVan, trunk = true, steelies;
        float seatDrop;
    }

    private static S spec(CarBody t) {
        S s = new S();
        switch (t) {
            case HATCH -> {
                s.W = 14.5f; s.zF = 23; s.zR = -22; s.belt = 13; s.hoodF = 11.5f; s.trunkR = 13;
                s.wsB = 9; s.roofF = 2.5f; s.roofR = -18; s.rwB = -21; s.r = 4.7f; s.wF = 15; s.wR = -15; s.trunk = false;
            }
            case SPORTS -> {
                s.W = 16; s.zF = 30; s.zR = -28; s.sill = 3; s.belt = 12; s.hoodF = 9.5f; s.trunkR = 12.5f;
                s.wsB = 10.5f; s.roofF = -0.5f; s.roofR = -11; s.rwB = -22; s.roof = 27.5f; s.inset = 3; s.r = 5.2f; s.ww = 5; s.wF = 18.5f; s.wR = -17.5f;
                s.seatDrop = 2;
            }
            case LUXURY -> {
                s.W = 15.5f; s.zF = 32; s.zR = -31; s.belt = 14; s.hoodF = 12.5f; s.trunkR = 14;
                s.wsB = 11; s.roofF = 4; s.roofR = -15; s.rwB = -22; s.roof = 29.5f; s.r = 5.3f; s.wF = 20.5f; s.wR = -19.5f;
            }
            case VAN -> {
                s.W = 16; s.zF = 26; s.zR = -33; s.sill = 4.5f; s.belt = 15; s.hoodF = 13.5f; s.trunkR = 15;
                s.wsB = 15; s.roofF = 9; s.roofR = -33; s.rwB = -33; s.roof = 36; s.inset = 0.8f; s.r = 5.5f; s.wF = 17; s.wR = -24;
                s.panelVan = true; s.trunk = false; s.steelies = true;
            }
            case POLICE -> {
                s.W = 15.5f; s.belt = 13.5f; s.hoodF = 12; s.r = 5.2f;
                s.steelies = true;
            }
            case SUV -> {
                s.W = 16; s.zF = 27; s.zR = -26; s.sill = 5.5f; s.belt = 17; s.hoodF = 15.5f; s.trunkR = 17;
                s.wsB = 11; s.roofF = 5; s.roofR = -23; s.rwB = -25; s.roof = 33; s.inset = 1.4f; s.r = 6.5f; s.ww = 5; s.wF = 17; s.wR = -16.5f;
                s.trunk = false;
            }
            case PICKUP -> {
                s.W = 16; s.zF = 30; s.zR = -46; s.sill = 5; s.belt = 16; s.hoodF = 15; s.trunkR = 16;
                s.wsB = 13; s.roofF = 8; s.roofR = -18; s.rwB = -19; s.roof = 31.5f; s.inset = 1.4f; s.r = 6; s.ww = 4.8f; s.wF = 20; s.wR = -34;
                s.bed = true; s.trunk = false;
            }
            case MUSCLE -> {
                s.W = 16; s.zF = 31; s.zR = -29; s.belt = 14; s.hoodF = 13.5f; s.trunkR = 14;
                s.wsB = 9; s.roofF = 2; s.roofR = -11; s.rwB = -19; s.roof = 28; s.inset = 2.6f; s.r = 5.4f; s.ww = 5.2f; s.wF = 19.5f; s.wR = -18;
                s.seatDrop = 1;
            }
            case LIMO -> {
                s.W = 15.5f; s.zF = 31; s.zR = -52; s.belt = 14; s.hoodF = 12.5f; s.trunkR = 14;
                s.wsB = 11; s.roofF = 4; s.roofR = -37; s.rwB = -44; s.roof = 29.5f; s.r = 5.3f; s.wF = 20; s.wR = -41;
            }
            case TRUCK -> {
                s.W = 16.5f; s.zF = 34; s.zR = -9; s.sill = 6; s.belt = 18; s.hoodF = 17; s.trunkR = 18;
                s.wsB = 25; s.roofF = 20; s.roofR = -9; s.rwB = -9; s.roof = 38; s.inset = 0.8f; s.r = 6.5f; s.ww = 5; s.wF = 25; s.wR = -40;
                s.panelVan = true; s.trunk = false; s.steelies = true;
            }
            default -> {}
        }
        return s;
    }

    /** Высота верха боковины в точке z (по капоту, линии окон и багажнику). */
    private static float top(S s, float z) {
        if (z >= s.wsB) return lerp(s.belt, s.hoodF, (z - s.wsB) / Math.max(0.01f, s.zF - s.wsB));
        if (z >= s.rwB) return s.belt;
        return lerp(s.belt, s.trunkR, (s.rwB - z) / Math.max(0.01f, s.rwB - s.zR));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0, Math.min(1, t));
    }

    /** Низ боковины в точке z: над колесом — круглая арка. */
    private static float bottom(S s, float z) {
        float ar = s.r + 1.3f;
        for (float wz : new float[]{s.wF, s.wR}) {
            float d = Math.abs(z - wz);
            if (d < ar) return Math.max(s.sill, s.r + (float) Math.sqrt(ar * ar - d * d));
        }
        return s.sill;
    }

    // ------------------------------------------------------------------ сборка
    private static CarModel build(CarBody t) {
        S s = spec(t);
        B b = new B();
        float W = s.W, iw = W - s.inset; // полуширина кабины
        float cabRear = s.bed ? s.rwB : s.zR;

        // днище и пороги
        b.box("under", -W + 1.5f, 2.2f, s.zR + 2, W - 1.5f, s.sill + 0.6f, s.zF - 2);
        // боковины по профилю: шаг 1 пиксель, арки круглые
        for (float z = s.zR; z < s.zF - 0.01f; z += 1) {
            float z2 = Math.min(s.zF, z + 1), zm = (z + z2) / 2;
            float top = top(s, zm), bot = bottom(s, zm);
            if (top - bot > 0.05f) b.sym("paint", W - 2.6f, bot, z, W, top, z2);
            if (bot > s.sill + 0.2f) b.sym("arch", W - 2.4f, bot - 0.35f, z, W - 0.15f, bot, z2); // тёмная подкладка арки
        }
        // колёсные ниши изнутри — чтобы не видеть салон сквозь арку
        for (float wz : new float[]{s.wF, s.wR}) b.sym("arch", W - 6.5f, s.sill, wz - s.r - 1.4f, W - 6, s.r * 2 + 1.4f, wz + s.r + 1.4f);
        // перед: морда и подкапотное
        b.box("paint", -W + 2.6f, s.sill, s.zF - 2, W - 2.6f, s.hoodF - 0.4f, s.zF);
        b.box("under", -W + 2.6f, s.sill, s.wsB, W - 2.6f, Math.min(s.hoodF, s.belt) - 0.6f, s.zF - 2);
        b.slab("hood", -W + 0.15f, W - 0.15f, s.hoodF, s.zF, s.belt, s.wsB, 1.1f);
        // зад
        if (s.bed) {
            b.box("under", -W + 2.6f, s.sill, s.zR + 1.2f, W - 2.6f, 8.5f, s.rwB);
            b.box("trim", -W + 2.6f, 8.5f, s.zR + 1.2f, W - 2.6f, 9.1f, s.rwB - 1.5f);           // пол кузова
            for (float x = -W + 5; x < W - 4; x += 4) b.box("under", x, 9.1f, s.zR + 1.4f, x + 0.8f, 9.4f, s.rwB - 1.7f); // рёбра пола
            b.box("paint", -W + 2.6f, s.sill, s.zR, W - 2.6f, s.belt, s.zR + 1.2f);              // задний борт
            b.box("trim", -W + 2.6f, s.belt - 0.6f, s.zR - 0.1f, W - 2.6f, s.belt + 0.2f, s.zR + 1.3f);
            b.sym("trim", W - 2.7f, s.belt, s.zR, W + 0.1f, s.belt + 0.7f, s.rwB);                 // накладки бортов
            b.box("paint", -iw, s.sill, s.rwB - 1.5f, iw, s.roof, s.rwB);                          // задняя стенка кабины
            b.box("chrome", -5, s.belt - 3.5f, s.zR - 0.3f, -2, s.belt - 2.8f, s.zR);              // ручка борта
        } else {
            b.box("paint", -W + 2.6f, s.sill, s.zR, W - 2.6f, s.trunkR - 0.4f, s.rwB);
            if (s.rwB - s.zR > 1.5f) b.slab("hood", -W + 0.15f, W - 0.15f, s.belt, s.rwB, s.trunkR, s.zR, 1.1f);
            else b.box("hood", -W + 0.15f, s.trunkR - 0.6f, s.zR, W - 0.15f, s.trunkR + 0.4f, s.zR + 2);
        }

        // кабина: лобовое, крыша, заднее стекло, стойки
        float gl = iw - 0.6f;
        b.slab("glass", -gl, gl, s.belt, s.wsB, s.roof, s.roofF, 0.35f);
        b.symSlab("paint", iw - 1.4f, iw, s.belt, s.wsB, s.roof, s.roofF, 1.3f);              // передние стойки
        b.box("roof", -iw, s.roof, s.roofR, iw, s.roof + 1.4f, s.roofF);
        b.box("headliner", -iw + 0.6f, s.roof - 0.4f, s.roofR + 0.5f, iw - 0.6f, s.roof, s.roofF - 0.3f);
        if (!s.bed && s.rwB != s.roofR || s.bed) {
            float rz = s.bed ? s.rwB : s.rwB;
            b.slab("glass", -gl, gl, s.roof, s.roofR, s.belt, rz, 0.35f);
            b.symSlab("paint", iw - (s.trunk ? 3.4f : 1.8f), iw, s.roof, s.roofR, s.belt, rz, 1.3f);  // задние стойки
        } else {
            b.box("glass", -gl, s.belt + 2, s.zR - 0.2f, gl, s.roof - 1.5f, s.zR + 0.2f);
            b.sym("paint", iw - 1.8f, s.belt, s.zR - 0.3f, iw, s.roof, s.zR + 1);
            b.box("paint", -iw, s.roof - 1.5f, s.zR - 0.3f, iw, s.roof, s.zR + 1);
            b.box("paint", -iw, s.belt, s.zR - 0.3f, iw, s.belt + 2, s.zR + 1);
        }
        // боковые стёкла: прямоугольник под крышей и ступеньки у наклонных стоек
        float sx1 = iw - 0.45f, sx2 = iw - 0.15f;
        float rearGlassEnd = s.panelVan ? s.pillarB : s.roofR;
        b.sym("glass_side", sx1, s.belt, rearGlassEnd, sx2, s.roof, s.roofF);
        b.tri("glass_side", sx1, sx2, s.belt, s.belt, s.wsB, s.roof, s.roofF, 8);
        b.tri("glass_side", -sx2, -sx1, s.belt, s.belt, s.wsB, s.roof, s.roofF, 8);
        if (!s.panelVan) {
            // у седанов за задней дверью глухая стойка, у хэтчбеков и внедорожников — стекло
            String g = s.trunk ? "paint" : "glass_side";
            float a1 = s.trunk ? iw - 0.8f : sx1, a2 = s.trunk ? iw : sx2;
            b.tri(g, a1, a2, s.belt, s.belt, s.rwB, s.roof, s.roofR, 8);
            b.tri(g, -a2, -a1, s.belt, s.belt, s.rwB, s.roof, s.roofR, 8);
        } else {
            // фургон: глухие борта за средней стойкой
            b.sym("paint", iw - 0.8f, s.belt, s.zR, iw, s.roof, rearGlassEnd);
        }
        // если кабина уже кузова — заливаем «плечо» над боковиной
        b.sym("trim", iw - 0.7f, s.belt - 0.2f, s.rwB, W - 2.5f, s.belt + 0.35f, s.wsB);            // уплотнитель окон
        // средняя стойка и рамки дверей
        b.sym("trim", iw - 0.8f, s.belt, s.pillarB - 1.4f, iw + 0.05f, s.roof, s.pillarB);
        if (t == CarBody.LIMO) for (float z : new float[]{-22, -30}) b.sym("trim", iw - 0.8f, s.belt, z - 1.4f, iw + 0.05f, s.roof, z);
        // швы дверей и ручки
        float doorF = s.wF - s.r - 1.6f;
        float[] seams = s.panelVan ? new float[]{doorF, s.pillarB - 1.6f, s.pillarB - 20} : t == CarBody.SPORTS || t == CarBody.MUSCLE
                ? new float[]{doorF, -9} : t == CarBody.LIMO ? new float[]{doorF, s.pillarB - 0.7f, -36} : new float[]{doorF, s.pillarB - 0.7f, -17};
        for (float z : seams) b.sym("trim", W, s.sill + 1.2f, z - 0.15f, W + 0.06f, s.belt - 0.3f, z + 0.15f);
        float[] handles = t == CarBody.SPORTS || t == CarBody.MUSCLE ? new float[]{-6} : new float[]{-4, -14};
        for (float z : handles) b.sym("chrome", W, s.belt - 2.6f, z - 1.2f, W + 0.35f, s.belt - 1.9f, z + 1.2f);
        // зеркала
        b.sym("paint", W - 0.5f, s.belt + 0.6f, s.wsB - 2.6f, W + 2.2f, s.belt + 3, s.wsB - 0.8f);
        b.sym("mirror_glass", W - 0.3f, s.belt + 0.9f, s.wsB - 2.7f, W + 2, s.belt + 2.7f, s.wsB - 2.55f);

        // бамперы: передний — отдельной деталью (на разбитой машине повисает)
        float bz = s.zF;
        b.pivot("bumper_f", W, s.sill, bz, 0,
                -2 * W - 0.2f, -1.2f, -0.6f, 0.2f, 2.8f, 1.6f,           // относительно точки поворота у левого края
                -2 * W + 2, 0.4f, 1.6f, -2, 1.4f, 1.9f);
        b.box("trim", -W - 0.2f, s.sill - 1.2f, s.zR - 1.6f, W + 0.2f, s.sill + 2.8f, s.zR + 0.6f);
        if (t == CarBody.MUSCLE || t == CarBody.PICKUP || t == CarBody.TRUCK) {
            b.box("chrome", -W - 0.3f, s.sill + 0.3f, s.zR - 1.8f, W + 0.3f, s.sill + 2.2f, s.zR - 1.2f);
        }
        // решётка радиатора
        float gy1 = s.sill + 2.2f, gy2 = s.hoodF - 1.6f;
        switch (t) {
            case LUXURY, LIMO -> {
                b.box("chrome", -5, gy1 - 0.4f, bz, 5, gy2 + 0.4f, bz + 0.5f);
                b.box("grille", -4.2f, gy1, bz + 0.1f, 4.2f, gy2, bz + 0.6f);
                for (float x = -3.4f; x < 3.6f; x += 1.4f) b.box("chrome", x, gy1, bz + 0.6f, x + 0.4f, gy2, bz + 0.8f);
                b.box("chrome", -0.4f, s.hoodF + 0.4f, bz - 1.5f, 0.4f, s.hoodF + 1.8f, bz - 0.6f);   // фигурка на капоте
            }
            case MUSCLE -> {
                b.box("grille", -W + 3, gy1, bz, W - 3, gy2, bz + 0.4f);
                b.box("chrome", -W + 2.6f, gy2, bz, W - 2.6f, gy2 + 0.4f, bz + 0.5f);
            }
            case SUV, PICKUP, TRUCK, VAN -> {
                b.box("grille", -7, gy1, bz, 7, gy2, bz + 0.5f);
                for (float y = gy1 + 0.8f; y < gy2 - 0.4f; y += 1.6f) b.box(t == CarBody.VAN ? "trim" : "chrome", -7, y, bz + 0.5f, 7, y + 0.5f, bz + 0.7f);
            }
            case SPORTS -> {
                b.box("grille", -6, s.sill + 1, bz + 0.6f, 6, s.sill + 3.2f, bz + 1.7f);   // нижний воздухозаборник
                b.box("trim", -W, s.sill - 1.5f, bz + 0.4f, W, s.sill - 1, bz + 2.6f);       // сплиттер
            }
            default -> b.box("grille", -5.5f, gy1, bz, 5.5f, gy2, bz + 0.4f);
        }
        // фары
        float hy2 = s.hoodF - 1.0f, hy1 = hy2 - (t == CarBody.SPORTS ? 1.6f : t == CarBody.SUV || t == CarBody.TRUCK ? 3.2f : 2.6f);
        float hx2 = W - 1.0f, hx1 = t == CarBody.MUSCLE ? W - 3.6f : W - 5.6f;
        b.box("head", hx1, hy1, bz, hx2, hy2, bz + 0.5f);
        b.box("head_l", -hx2, hy1, bz, -hx1, hy2, bz + 0.5f);
        if (t == CarBody.MUSCLE) {
            b.box("head", hx1 - 3.4f, hy1, bz, hx1 - 0.6f, hy2, bz + 0.5f);
            b.box("head_l", -hx1 + 0.6f, hy1, bz, -hx1 + 3.4f, hy2, bz + 0.5f);
        }
        b.box("sig_l", W - 1.6f, hy1 - 1.4f, bz, W - 0.2f, hy1 - 0.4f, bz + 0.55f);
        b.box("sig_r", -W + 0.2f, hy1 - 1.4f, bz, -W + 1.6f, hy1 - 0.4f, bz + 0.55f);
        b.sym("sig_l", W, s.belt - 1.6f, s.wF + s.r + 2, W + 0.1f, s.belt - 1.0f, s.wF + s.r + 3.4f); // боковые повторители
        // номер спереди
        b.box("plate", -3.6f, s.sill - 0.6f, bz + 1.6f, 3.6f, s.sill + 1.4f, bz + 1.9f);
        b.box("plate_txt", -3, s.sill - 0.1f, bz + 1.9f, 3, s.sill + 0.9f, bz + 2);

        // задние огни, номер, выхлоп
        float rz = s.zR, ty2 = s.trunkR - 1.0f, ty1 = ty2 - 2.4f;
        boolean tallTail = !s.trunk && !s.bed && t != CarBody.HATCH || s.bed;
        if (t == CarBody.TRUCK) {
            // огни внизу будки
            rz = -56;
            b.box("tail", W - 4, 9, rz - 0.4f, W - 0.5f, 11, rz);
            b.box("tail_l", -W + 0.5f, 9, rz - 0.4f, -W + 4, 11, rz);
            b.sym("rev", W - 6, 9, rz - 0.4f, W - 4.4f, 11, rz);
        } else if (tallTail) {
            float y1 = s.belt - 4, y2 = s.bed ? s.belt - 0.6f : s.belt + 4;
            b.box("tail", W - 2.6f, y1, rz - 0.4f, W - 0.4f, y2, rz);
            b.box("tail_l", -W + 0.4f, y1, rz - 0.4f, -W + 2.6f, y2, rz);
            b.sym("rev", W - 2.6f, y1 - 1.6f, rz - 0.4f, W - 0.4f, y1 - 0.4f, rz);
            b.sym("sig_l", W - 2.6f, y2 + 0.2f, rz - 0.4f, W - 0.4f, y2 + 1.2f, rz);
        } else {
            float tx1 = t == CarBody.SPORTS || t == CarBody.MUSCLE ? W - 6.5f : W - 5;
            b.box("tail", tx1, ty1, rz - 0.4f, W - 0.4f, ty2, rz);
            b.box("tail_l", -W + 0.4f, ty1, rz - 0.4f, -tx1, ty2, rz);
            b.sym("rev", tx1 - 2.2f, ty1 + 0.5f, rz - 0.4f, tx1 - 0.4f, ty2 - 0.5f, rz);
            if (t == CarBody.LUXURY || t == CarBody.LIMO) b.box("tail", -tx1, ty2 - 0.6f, rz - 0.4f, tx1, ty2 - 0.2f, rz); // светящаяся полоса через всю корму
        }
        // поворотники сзади — отдельно
        if (t != CarBody.TRUCK && !tallTail) {
            b.box("sig_l", W - 1.8f, ty1 - 1.2f, rz - 0.45f, W - 0.4f, ty1 - 0.3f, rz);
            b.box("sig_r", -W + 0.4f, ty1 - 1.2f, rz - 0.45f, -W + 1.8f, ty1 - 0.3f, rz);
        } else if (tallTail && t != CarBody.TRUCK) {
            b.box("sig_r", -W + 0.4f, s.belt + 0.2f + (s.bed ? -0.6f : 4), rz - 0.45f, -W + 2.6f, s.belt + 1.2f + (s.bed ? -0.6f : 4), rz);
        }
        float plY = t == CarBody.TRUCK ? 8 : s.sill + 3.2f;
        if (t != CarBody.TRUCK) {
            b.box("plate", -3.6f, plY, rz - 0.3f, 3.6f, plY + 2, rz);
            b.box("plate_txt", -3, plY + 0.5f, rz - 0.4f, 3, plY + 1.5f, rz - 0.3f);
        }
        float ex = W - 5;
        boolean dual = t == CarBody.SPORTS || t == CarBody.MUSCLE || t == CarBody.LUXURY;
        float exZ = t == CarBody.TRUCK ? -11 : s.zR;
        b.box("trim", ex - 2.6f, s.sill - 0.8f, exZ - 2.2f, ex, s.sill + 0.9f, exZ + 1);
        b.box("exhaust_tip", ex - 2.1f, s.sill - 0.4f, exZ - 2.3f, ex - 0.5f, s.sill + 0.5f, exZ - 2.2f);
        if (dual) {
            b.box("trim", -ex, s.sill - 0.8f, exZ - 2.2f, -ex + 2.6f, s.sill + 0.9f, exZ + 1);
            b.box("exhaust_tip", -ex + 0.5f, s.sill - 0.4f, exZ - 2.3f, -ex + 2.1f, s.sill + 0.5f, exZ - 2.2f);
        }

        interior(b, s, t);
        extras(b, s, t, iw);
        dents(b, s);

        float[][] wheels = t == CarBody.TRUCK
                ? new float[][]{{W - 2.4f, s.wF}, {-W + 2.4f, s.wF}, {W - 2.4f, s.wR}, {-W + 2.4f, s.wR}, {W - 2.4f, s.wR + 13}, {-W + 2.4f, s.wR + 13}}
                : new float[][]{{W - 2.3f, s.wF}, {-W + 2.3f, s.wF}, {W - 2.3f, s.wR}, {-W + 2.3f, s.wR}};
        float[] spare = t == CarBody.SUV ? new float[]{0, s.belt + 0.5f, s.zR - 2.4f} : null;
        return new CarModel(b.build(), new Wheel(s.r, s.ww, s.steelies), wheels, s.r, W - 3.3f, (hy1 + hy2) / 2, bz + 0.6f, spare);
    }

    /** Салон: пол, торпедо с приборами, руль, кресла, обивка дверей. Кресла стоят там, где сидят игроки (CarEntity.SEATS). */
    private static void interior(B b, S s, CarBody t) {
        float d = s.seatDrop, iw = s.W - s.inset - 0.8f;
        float rear = Math.max(s.rwB + 1, s.bed ? s.rwB : -21), front = Math.min(s.wsB - 0.5f, 9.5f);
        b.box("floor", -iw, 7 - d, rear, iw, 7.6f - d, front);
        // обивка дверей
        b.sym("inner", s.W - 2.6f - 0.8f, 7.6f - d, rear, s.W - 2.6f, s.belt - 0.2f, front);
        b.sym("inner", s.W - 2.6f - 1.6f, s.belt - 1.6f, -16, s.W - 2.6f, s.belt - 0.6f, -10);
        b.sym("inner", s.W - 2.6f - 1.6f, s.belt - 1.6f, -4, s.W - 2.6f, s.belt - 0.6f, 3);
        // торпедо под лобовым
        float dz = Math.min(front, 9);
        // торпедо упирается в лобовое стекло, не протыкая его
        float dashEnd = s.wsB - (3.0f - d * 0.5f) * (s.wsB - s.roofF) / (s.roof - s.belt);
        b.box("dash", -iw, s.belt - 2.5f - d * 0.5f, dz - 3.5f, iw, s.belt + 2.5f - d * 0.5f, Math.max(dz - 2, dashEnd));
        b.box("dash", -iw, s.belt + 2.3f - d * 0.5f, dz - 5.5f, iw, s.belt + 3.4f - d * 0.5f, dz - 3.5f);
        b.box("dash", -2.5f, 7.6f - d, 3, 2.5f, s.belt - 1.5f, 6);
        float gy = s.belt + 0.2f - d * 0.5f;
        b.box("gauges", 4.8f, gy, dz - 3.7f, 7, gy + 2.2f, dz - 3.5f);
        b.box("gauges", 7.8f, gy, dz - 3.7f, 10, gy + 2.2f, dz - 3.5f);
        b.box("gauges", -1.8f, gy - 1, dz - 3.7f, 1.8f, gy + 1.6f, dz - 3.5f);
        // передние ковши и задний диван
        for (float x : new float[]{-11, 3}) {
            b.box("seat", x, 7.6f - d, -4, x + 8, 10.2f - d, 4);
            b.box("seat", x - 0.2f, 8 - d, -4, x + 0.8f, 11.2f - d, 4);
            b.box("seat", x + 7.2f, 8 - d, -4, x + 8.2f, 11.2f - d, 4);
            b.box("seat", x, 10.2f - d, -6, x + 8, 23.2f - d, -4);
            b.box("seat", x + 1.5f, 24.2f - d, -5.8f, x + 6.5f, 27.7f - d, -4.2f);
            b.box("seam", x + 2, 10.25f - d, -3.5f, x + 6, 10.35f - d, 3.5f);
            b.box("seam", x + 2, 12 - d, -3.95f, x + 6, 22 - d, -3.85f);
        }
        if (t != CarBody.SPORTS) {
            b.box("seat", -12, 7.6f, -17, 12, 10.2f, -9);
            b.box("seat", -12, 10.2f, -19, 12, 22.2f, -17);
            b.box("seat", -10, 22.5f, -18.8f, -5, 25.5f, -17.4f);
            b.box("seat", 5, 22.5f, -18.8f, 10, 25.5f, -17.4f);
            b.box("seam", -11, 10.25f, -16.5f, 11, 10.35f, -9.5f);
        }
        b.box("seat", -2, 7.6f - d, -3, 2, 11 - d, 3); // тоннель с подлокотником
        if (t == CarBody.LIMO) {
            // салон лимузина: диван против хода и бар
            b.box("seat", -12, 7.6f, -36, 12, 10.2f, -29);
            b.box("seat", -12, 10.2f, -38, 12, 21, -36);
            b.box("dash", -12, 7.6f, -24, 12, 12, -21);
            b.box("gauges", -8, 12, -22.8f, 8, 12.3f, -22.2f);
        }
        b.box("trim", -0.3f, 11 - d, 3.2f, 0.3f, 13.4f - d, 3.8f);           // рычаг КПП
        b.box("trim", -0.7f, 13.2f - d, 2.9f, 0.7f, 14.4f - d, 4.1f);
        b.box("trim", -2, s.roof - 2.5f, Math.min(s.roofF, 8) - 0.6f, 2, s.roof - 1, Math.min(s.roofF, 8)); // зеркало в салоне
        // руль: обод из 16 сегментов, три спицы, ступица; поворачивается вместе с колёсами
        float[] c = new float[(3 + 16 + 1 + 1) * 6];
        int k = 0;
        float[][] fixed = {{-1.1f, -1.1f, -0.45f, 1.1f, 1.1f, 0.25f}, {-3.0f, -0.35f, -0.2f, -1.05f, 0.35f, 0.2f},
                {1.05f, -0.35f, -0.2f, 3.0f, 0.35f, 0.2f}, {-0.35f, -3.0f, -0.2f, 0.35f, -1.05f, 0.2f}, {-0.45f, -0.45f, 0.3f, 0.45f, 0.45f, 1.6f}};
        for (float[] f : fixed) for (float v : f) c[k++] = v;
        for (int i = 0; i < 16; i++) {
            float a = (float) (i * Math.PI * 2 / 16);
            float cx = (float) Math.cos(a) * 3.1f, cy = (float) Math.sin(a) * 3.1f;
            c[k++] = cx - 0.45f;
            c[k++] = cy - 0.45f;
            c[k++] = -0.3f;
            c[k++] = cx + 0.45f;
            c[k++] = cy + 0.45f;
            c[k++] = 0.3f;
        }
        float[] cc = java.util.Arrays.copyOf(c, k);
        b.pivot("steering", 7, s.belt + 4.1f - d, 4, -0.55f, cc);
    }

    /** Свои детали каждого кузова. */
    private static void extras(B b, S s, CarBody t, float iw) {
        float W = s.W;
        switch (t) {
            case TAXI -> {
                b.box("bar_base", -5.5f, s.roof + 1.4f, -7.5f, 5.5f, s.roof + 1.9f, -1.5f);
                b.box("taxi_sign", -5, s.roof + 1.9f, -7, 5, s.roof + 4.6f, -2);
                // шашечки по бортам
                for (int i = 0; i < 12; i++) {
                    float z = -16 + i * 2.4f;
                    b.sym("check", W, s.belt - 3.0f + (i % 2) * 0.9f, z, W + 0.07f, s.belt - 2.1f + (i % 2) * 0.9f, z + 1.2f);
                }
            }
            case POLICE -> {
                b.box("bar_base", -9.5f, s.roof + 1.4f, -6, 9.5f, s.roof + 2.2f, -1.5f);
                b.box("bar_red", 0.4f, s.roof + 2.2f, -5.5f, 9, s.roof + 4, -2);
                b.box("bar_blue", -9, s.roof + 2.2f, -5.5f, -0.4f, s.roof + 4, -2);
                // ливрея: тёмные капот, багажник и полоса по дверям
                b.sym("livery", W, s.sill + 1.2f, -20, W + 0.06f, s.belt - 3.6f, 12);
                b.sym("stripe", W, s.belt - 3.6f, -20, W + 0.07f, s.belt - 2.8f, 12);
                // кенгурятник
                float z1 = s.zF + 1.6f, z2 = s.zF + 2.6f;
                b.sym("pushbar", 5.5f, s.sill - 1, z1, 7.5f, s.hoodF + 1.5f, z2);
                b.box("pushbar", -7.5f, s.hoodF - 1.6f, z1 - 0.3f, 7.5f, s.hoodF - 0.4f, z2);
                b.box("pushbar", -7.5f, s.sill + 2.2f, z1 - 0.3f, 7.5f, s.sill + 3.2f, z2);
                // прожектор у стойки и антенна
                b.box("chrome", W - 1.5f, s.belt + 3.5f, s.wsB - 2.5f, W + 0.5f, s.belt + 4.8f, s.wsB - 0.6f);
                b.box("trim", -3, s.roof + 1.4f, -14, -2.6f, s.roof + 7, -13.6f);
            }
            case SPORTS -> {
                // антикрыло и воздухозаборники
                b.sym("trim", 8, s.trunkR, s.zR + 1.5f, 9, s.trunkR + 3.2f, s.zR + 3);
                b.box("trim", -W + 1, s.trunkR + 3.2f, s.zR + 0.5f, W - 1, s.trunkR + 3.9f, s.zR + 4.6f);
                b.sym("grille", W, s.sill + 2.2f, -12, W + 0.08f, s.belt - 2.4f, -7);
                b.box("grille", -4, s.hoodF + 1.1f, s.zF - 9, 4, s.hoodF + 1.4f, s.zF - 4);
            }
            case MUSCLE -> {
                // воздухозаборник на капоте, две полосы через весь кузов, спойлер
                b.box("hood", -4, s.belt + 0.4f, s.wsB + 3, 4, s.belt + 2.2f, s.wsB + 10);
                b.box("grille", -3.2f, s.belt + 0.8f, s.wsB + 10, 3.2f, s.belt + 1.9f, s.wsB + 10.2f);
                for (float x : new float[]{-3.4f, 1.4f}) {
                    b.slab("stripe", x, x + 2, s.hoodF + 0.6f, s.zF, s.belt + 0.6f, s.wsB, 0.1f);
                    b.box("stripe", x, s.roof + 1.42f, s.roofR, x + 2, s.roof + 1.5f, s.roofF);
                    b.slab("stripe", x, x + 2, s.roof + 0.25f, s.roofR, s.belt + 0.25f, s.rwB, 0.1f);
                    b.slab("stripe", x, x + 2, s.belt + 0.6f, s.rwB, s.trunkR + 0.6f, s.zR, 0.1f);
                }
                b.box("trim", -W + 1, s.trunkR + 0.4f, s.zR + 0.3f, W - 1, s.trunkR + 1.6f, s.zR + 1.4f);
            }
            case SUV -> {
                // рейлинги на крыше, расширители арок, подножки, защита снизу
                b.sym("trim", iw - 2, s.roof + 1.4f, s.roofR + 1, iw - 1, s.roof + 2.6f, s.roofF - 1);
                for (float z : new float[]{s.roofF - 3, (s.roofF + s.roofR) / 2, s.roofR + 3}) b.box("trim", -iw + 1, s.roof + 2.2f, z - 0.5f, iw - 1, s.roof + 2.8f, z + 0.5f);
                for (float wz : new float[]{s.wF, s.wR}) {
                    float ar = s.r + 1.3f;
                    for (float z = wz - ar - 1; z < wz + ar + 1; z += 1) {
                        float dd = Math.abs(z + 0.5f - wz), y = dd < ar + 1 ? s.r + (float) Math.sqrt(Math.max(0, (ar + 1) * (ar + 1) - dd * dd)) : 0;
                        if (y > s.sill) b.sym("trim", W, Math.max(s.sill, y - 1.4f), z, W + 0.6f, y, z + 1);
                    }
                }
                b.sym("trim", W - 1, s.sill - 0.6f, s.wR + s.r + 2, W + 1.4f, s.sill + 0.2f, s.wF - s.r - 2);
                b.box("chrome", -7, s.sill - 1.6f, s.zF - 2, 7, s.sill - 0.8f, s.zF + 1.4f);
            }
            case PICKUP -> {
                // дуга безопасности над кузовом и фаркоп
                b.sym("trim", iw - 1.2f, s.belt, s.rwB - 3.2f, iw, s.roof - 2, s.rwB - 2.2f);
                b.box("trim", -iw, s.roof - 3, s.rwB - 3.2f, iw, s.roof - 2, s.rwB - 2.2f);
                b.box("chrome", -1, s.sill - 0.6f, s.zR - 3.4f, 1, s.sill + 0.6f, s.zR - 1.6f);
            }
            case VAN -> {
                // полоса службы доставки, лестница на крыше, шов сдвижной двери и задних створок
                b.sym("accent", W, s.belt - 1.6f, s.zR + 1, W + 0.06f, s.belt + 3.2f, s.pillarB - 2.5f);
                for (float x : new float[]{-6, 6}) b.box("chrome", x - 0.4f, s.roof + 1.4f, s.zR + 2, x + 0.4f, s.roof + 2, s.pillarB);
                for (float z = s.zR + 3; z < s.pillarB; z += 4) b.box("chrome", -6, s.roof + 1.5f, z, 6, s.roof + 1.9f, z + 0.5f);
                b.box("trim", -0.15f, s.sill + 1, s.zR - 0.06f, 0.15f, s.roof - 1, s.zR);
                b.box("chrome", -2.2f, s.belt - 1.5f, s.zR - 0.4f, 2.2f, s.belt - 1, s.zR);
            }
            case LUXURY, LIMO -> {
                // хромированные молдинги по бортам и вокруг окон
                b.sym("chrome", W, s.sill + 2.2f, s.zR + 3, W + 0.08f, s.sill + 2.6f, s.zF - 3);
                b.sym("chrome", iw - 0.7f, s.belt + 0.3f, s.rwB, iw - 0.05f, s.belt + 0.55f, s.wsB);
            }
            case HATCH -> b.box("paint", -iw + 0.3f, s.roof + 0.4f, s.roofR - 1.6f, iw - 0.3f, s.roof + 1.4f, s.roofR); // спойлер над дверью
            case TRUCK -> {
                // будка, рама, сдвоенные задние колёса, вертикальная выхлопная труба
                float bx = W + 0.8f;
                b.box("accent", -bx, 14, -56, bx, 44, -10.6f);
                for (float z = -54; z < -11; z += 4) b.sym("accent", bx, 14.5f, z, bx + 0.25f, 43.5f, z + 0.6f);
                b.box("trim", -bx, 43.5f, -56.2f, bx, 44.4f, -10.4f);
                b.box("stripe", -bx - 0.3f, 24, -54, -bx, 30, -14);
                b.box("stripe", bx, 24, -54, bx + 0.3f, 30, -14);
                b.box("accent", -bx + 0.6f, 15.6f, -56.4f, bx - 0.6f, 42.4f, -56);  // задние створки
                b.box("trim", -bx, 15, -56.3f, bx, 15.6f, -56.1f);
                b.box("stripe", -bx + 0.6f, 24, -56.5f, bx - 0.6f, 30, -56.35f);  // полоса и сзади
                b.box("chrome", -0.2f, 15, -56.6f, 0.2f, 43, -56.3f);
                b.box("under", -8, 7, -56, 8, 10, s.wsB);                           // рама
                b.box("trim", -W - 0.3f, 5, -57, W + 0.3f, 8, -55.6f);             // задний бампер
                b.box("trim", W - 4, 14, -11, W - 2.6f, 42, -9.6f);
                b.box("chrome", W - 4.3f, 42, -11.3f, W - 2.3f, 43, -9.3f);
                for (float wz : new float[]{s.wR, s.wR + 13}) b.sym("arch", W - 4, s.r * 2 + 0.4f, wz - s.r - 1, W + 0.4f, s.r * 2 + 1, wz + s.r + 1); // брызговики
            }
            default -> {}
        }
    }

    /** Вмятины и царапины: тёмные пятна на панелях, появляются по мере повреждения. */
    private static void dents(B b, S s) {
        float W = s.W;
        // только на дверях между колёсами — над аркой пятно висело бы в воздухе
        float[][] d1 = {{1, s.belt - 4, 5}, {-1, s.sill + 3, -7}, {1, s.sill + 2.5f, -9}};
        float[][] d2 = {{-1, s.belt - 3.5f, 4}, {1, s.belt - 5.5f, -4}, {-1, s.belt - 3, -9}, {1, s.sill + 4, 3}};
        float[][] d3 = {{1, s.belt - 3, -10}, {-1, s.sill + 2.5f, 1}, {-1, s.belt - 5, -3}, {1, s.belt - 2.5f, 0}};
        float zMin = s.wR + s.r + 3.8f, zMax = s.wF - s.r - 8.8f;
        for (float[][] arr : new float[][][]{d1, d2, d3}) for (float[] p : arr) p[2] = Math.max(zMin, Math.min(zMax, p[2]));
        String[] g = {"dent1", "dent2", "dent3"};
        float[][][] all = {d1, d2, d3};
        for (int i = 0; i < 3; i++)
            for (float[] p : all[i]) {
                float x = p[0] > 0 ? W : -W - 0.08f;
                b.box(g[i], x, p[1] - 0.6f, p[2] - 2.4f, x + 0.08f, p[1] + 2.0f, p[2] + 2.4f);
                b.box(g[i], x - 0.01f, p[1] - 0.1f, p[2] - 1.4f, x + 0.09f, p[1] + 1.3f, p[2] + 1.0f); // глубже в центре
                b.box(g[i], x, p[1] + 0.6f, p[2] + 2.4f, x + 0.08f, p[1] + 0.9f, p[2] + 6.4f); // царапина
            }
        // помятый капот и крыша
        b.box("dent2", -6, top(s, s.zF - 4) + 0.6f, s.zF - 7, -2, top(s, s.zF - 4) + 0.7f, s.zF - 3);
        b.box("dent3", 2, s.roof + 1.41f, s.roofR + 4, 7, s.roof + 1.5f, s.roofR + 9);
    }
}
