package com.yompix.novograd.client.anim;

import com.yompix.novograd.life.HandKind;
import net.minecraft.util.math.MathHelper;

/**
 * 0.9.8 «Руки»: раскадровка каждого действия — где правая и левая кисть (в «пространстве тела»: x вправо, y вверх от
 * ступней, z вперёд; блоки), что в руке, присел ли, куда смотрит. Одна раскадровка — и для вида от третьего лица
 * (руки модели тянутся к точке, {@link HandsClient#applyThird}), и от первого (кисти и предметы в кадре,
 * {@link HandsClient#renderFirst}). Точки банкомата совпадают с моделью блока (прорезь, клавиатура, лоток).
 */
public final class HandsPose {
    private HandsPose() {}

    /** Кадр: цели кистей, вес (0 — обычная поза), предметы, присед, наклон предмета, взгляд. */
    public static final class Frame {
        public final float[] r = new float[3], l = new float[3];
        public float wr, wl;
        public String propR = "", propL = "";
        public float tiltR, tiltL, rollR;
        public float crouch;
        /** Куда смотреть (точка тела) и с каким весом; NaN — не трогать. */
        public final float[] look = new float[3];
        public float lookW;
        /** Пальцы «жмут» (клавиши, курок колонки) — для мелкой дрожи кисти. */
        public float press;

        void clear() {
            wr = wl = 0;
            propR = propL = "";
            tiltR = tiltL = rollR = 0;
            crouch = 0;
            lookW = 0;
            press = 0;
        }
    }

    // ------------------------------------------------------------------ точки (тело; банкомат — по модели блока)
    static final float[] R_REST = {0.34f, 0.80f, 0.06f}, L_REST = {-0.34f, 0.80f, 0.06f};
    static final float[] R_POCKET = {0.27f, 0.86f, 0.10f}, L_POCKET = {-0.27f, 0.86f, 0.10f};
    static final float[] CHEST = {0.05f, 1.14f, 0.26f}, CHEST_L = {-0.06f, 1.12f, 0.26f};
    static final float[] MOUTH = {0.03f, 1.50f, 0.16f};
    // банкомат (встал в 0,9 блока от центра): прорезь карты справа, клавиатура на полке, лоток слева, чек под картой
    static final float[] ATM_SLOT = {0.20f, 1.225f, 0.61f}, ATM_PAD = {0.0f, 1.085f, 0.52f}, ATM_DISP = {-0.14f, 1.18f, 0.61f}, ATM_SCREEN = {0f, 1.47f, 0.65f};
    // автомат с напитками (1,75 блока): монетоприёмник справа, кнопки на витрине, лоток внизу
    static final float[] VEND_COIN = {0.27f, 0.80f, 0.50f}, VEND_BTN = {0.02f, 1.18f, 0.50f}, VEND_TRAY = {0.0f, 0.20f, 0.45f};

    static float s(float a, float b, float t) {
        float x = MathHelper.clamp((t - a) / (b - a), 0, 1);
        return x * x * (3 - 2 * x);
    }

    static float bump(float t, float a, float b) {
        if (t <= a || t >= b) return 0;
        return MathHelper.sin((t - a) / (b - a) * MathHelper.PI);
    }

    static void set(float[] o, float[] a) {
        System.arraycopy(a, 0, o, 0, 3);
    }

    static void set(float[] o, float x, float y, float z) {
        o[0] = x;
        o[1] = y;
        o[2] = z;
    }

    /** o = o + (b − o)·k */
    static void to(float[] o, float[] b, float k) {
        for (int i = 0; i < 3; i++) o[i] += (b[i] - o[i]) * k;
    }

    static float[] p(float x, float y, float z) {
        return new float[]{x, y, z};
    }

    static float[] add(float[] a, float x, float y, float z) {
        return new float[]{a[0] + x, a[1] + y, a[2] + z};
    }

    /** Глоток/укус: рука к губам и обратно (окно a..b), наклон предмета. */
    static float sip(Frame f, float t, float a, float b, float[] hold, float tilt) {
        float k = s(a, a + (b - a) * 0.3f, t) * (1 - s(b - (b - a) * 0.3f, b, t));
        set(f.r, hold);
        to(f.r, MOUTH, k);
        f.tiltR = -tilt * k;
        return k;
    }

    /** Кадр действия k на тике t (с долями). */
    public static void sample(HandKind k, String v, float t, Frame f) {
        f.clear();
        float in = s(0, 4, t), out = 1 - s(k.len - 5, k.len, t);
        float w = in * out;
        set(f.r, R_REST);
        set(f.l, L_REST);
        switch (k) {
            case ATM_IN -> {
                // 7–12 карта из кармана, 12–17 в прорезь, 17–23 к клавиатуре (левая прикрывает), 23–44 PIN
                to(f.r, R_POCKET, s(6, 10, t));
                to(f.r, add(ATM_SLOT, 0.01f, -0.02f, -0.1f), s(11, 15, t));
                to(f.r, ATM_SLOT, s(14.5f, 17, t));
                if (t >= 10 && t < 17) f.propR = "card";
                float[] pad = add(ATM_PAD, 0, 0.05f, 0);
                to(f.r, pad, s(18, 23, t));
                to(f.l, p(-0.09f, 1.15f, 0.40f), s(19, 24, t) * (1 - s(43, 46, t)));
                f.wl = s(19, 24, t) * (1 - s(43, 46, t));
                if (t >= 23) {
                    // клавиши: 27, 31, 35, 39 — цифры, 43 — «Ввод»
                    float[][] keys = {{0.03f, 0.01f}, {-0.03f, -0.02f}, {0.0f, 0.03f}, {0.03f, -0.01f}, {0.05f, -0.04f}};
                    float px = 0, pz = 0, press = 0;
                    for (int i = 0; i < 5; i++) {
                        float at = 27 + i * 4;
                        float k2 = s(at - 3, at - 1, t) * (1 - s(at + 1, at + 3, t));
                        if (Math.abs(t - at) < 3) {
                            px = keys[i][0];
                            pz = keys[i][1];
                        }
                        press = Math.max(press, bump(t, at - 1.2f, at + 1.2f));
                    }
                    f.r[0] += px;
                    f.r[2] += pz;
                    f.r[1] -= 0.045f * press;
                    f.press = press;
                }
                f.wr = w;
                set(f.look, ATM_SCREEN);
                to(f.look, ATM_PAD, s(20, 24, t) * 0.6f);
                f.lookW = w;
            }
            case ATM_CASH -> {
                set(f.r, add(ATM_PAD, 0.05f, 0.08f, -0.05f));
                to(f.r, ATM_DISP, s(16, 21, t));
                if (t >= 22 && t < 45) f.propR = "cash";
                to(f.r, CHEST, s(23, 30, t));
                // пересчитать: левая подходит, пальцы перебирают
                float count = s(28, 32, t) * (1 - s(40, 43, t));
                set(f.l, L_REST);
                to(f.l, CHEST_L, count);
                f.r[0] += 0.015f * MathHelper.sin(t * 2.4f) * count;
                f.wl = count;
                to(f.r, R_POCKET, s(40, 45, t));
                to(f.r, add(ATM_SLOT, 0, -0.01f, -0.04f), s(45, 50, t));
                if (t >= 50 && t < 57) f.propR = "card";
                to(f.r, R_POCKET, s(51, 56, t));
                f.wr = w;
                set(f.look, ATM_SCREEN);
                to(f.look, ATM_DISP, s(10, 18, t) * (1 - s(24, 28, t)));
                to(f.look, CHEST, s(26, 30, t) * (1 - s(42, 46, t)));
                f.lookW = w;
            }
            case ATM_DEPOSIT -> {
                to(f.r, R_POCKET, s(4, 9, t));
                if (t >= 8 && t < 18) f.propR = "cash";
                to(f.r, add(ATM_DISP, 0, 0, -0.06f), s(10, 14, t));
                to(f.r, ATM_DISP, s(14, 18, t));
                to(f.r, add(ATM_PAD, 0.05f, 0.08f, -0.08f), s(19, 24, t));
                to(f.r, add(ATM_SLOT, 0, -0.01f, -0.04f), s(38, 43, t));
                if (t >= 44 && t < 51) f.propR = "card";
                to(f.r, R_POCKET, s(45, 50, t));
                f.wr = w;
                set(f.look, ATM_SCREEN);
                f.lookW = w;
            }
            case ATM_CARD -> {
                set(f.r, add(ATM_PAD, 0.05f, 0.06f, -0.05f));
                to(f.r, add(ATM_SLOT, 0, -0.01f, -0.04f), s(1, 6, t));
                if (t >= 6 && t < 15) f.propR = "card";
                to(f.r, R_POCKET, s(8, 14, t));
                f.wr = w;
                set(f.look, ATM_SLOT);
                f.lookW = w * 0.7f;
            }
            case VEND -> {
                boolean snack = "snack".equals(v);
                to(f.r, R_POCKET, s(6, 10, t));
                if (t >= 10 && t < 15) f.propR = "coin";
                to(f.r, VEND_COIN, s(10, 15, t));
                to(f.r, add(VEND_BTN, 0, 0, -0.04f), s(17, 23, t));
                f.r[2] += 0.04f * bump(t, 24, 28);
                to(f.r, R_REST, s(28, 34, t));
                f.crouch = s(38, 47, t) * (1 - s(56, 64, t));
                to(f.r, VEND_TRAY, s(42, 50, t));
                if (t >= 52) f.propR = snack ? "chips" : "can";
                float[] hold = snack ? p(0.08f, 1.08f, 0.26f) : p(0.13f, 1.10f, 0.25f);
                to(f.r, hold, s(54, 64, t));
                // открыть: левая к банке (кольцо) / разорвать пачку
                float open = s(64, 69, t) * (1 - s(74, 78, t));
                set(f.l, L_REST);
                to(f.l, snack ? CHEST_L : add(hold, -0.08f, 0.12f, 0), open);
                f.wl = open;
                if (snack) {
                    // левая держит пачку у груди, правая — в пачку и ко рту
                    float holdBag = s(70, 76, t) * (1 - s(138, 146, t));
                    if (holdBag > 0) {
                        f.propL = "chips";
                        f.propR = "";
                        to(f.l, CHEST_L, holdBag);
                        f.wl = Math.max(f.wl, holdBag);
                        float[] bag = add(CHEST_L, 0.04f, 0.06f, 0);
                        set(f.r, bag);
                        for (float a : new float[]{80, 98, 116}) sip(f, t, a, a + 14, bag, 0);
                        if (t < 80) to(f.r, hold, 1 - s(74, 80, t));
                    }
                } else {
                    for (float a : new float[]{78, 96, 114}) if (t >= a - 1 && t <= a + 18) sip(f, t, a, a + 18, hold, 55);
                    if (t > 132) set(f.r, hold);
                    if (t > 142) f.propR = "";
                }
                to(f.r, R_REST, s(140, 148, t));
                f.wr = w;
                set(f.look, VEND_BTN);
                to(f.look, VEND_TRAY, s(30, 44, t) * (1 - s(54, 62, t)));
                f.lookW = w * (1 - s(60, 70, t));
            }
            case STALL -> {
                boolean drink = "drink".equals(v) || "coffee".equals(v);
                String item = "coffee".equals(v) ? "coffee" : drink ? "coffee" : "icecream".equals(v) ? "icecream" : "hotdog";
                // заказ: показать на меню
                float point = s(7, 11, t) * (1 - s(17, 21, t));
                to(f.r, p(0.16f, 1.38f, 0.48f), point);
                // платёж: из кармана — продавцу
                to(f.r, R_POCKET, s(20, 23, t));
                if (t >= 22 && t < 29) f.propR = "cash";
                to(f.r, p(0.06f, 1.12f, 0.55f), s(23, 27, t));
                to(f.r, R_REST, s(29, 34, t));
                // получить: обе руки вперёд
                float take = s(42, 48, t) * (1 - s(54, 60, t));
                to(f.r, p(0.04f, 1.08f, 0.5f), take);
                set(f.l, L_REST);
                to(f.l, p(-0.06f, 1.06f, 0.48f), take);
                f.wl = take;
                if (t >= 49) f.propR = item;
                float[] hold = drink ? p(0.12f, 1.12f, 0.24f) : p(0.08f, 1.14f, 0.26f);
                to(f.r, hold, s(52, 60, t));
                for (float a : new float[]{66, 84, 102}) if (t >= a - 1 && t <= a + 16) sip(f, t, a, a + 16, hold, drink ? 40 : 15);
                if (t > 120) f.propR = "";
                to(f.r, R_REST, s(116, 122, t));
                f.wr = w;
                set(f.look, p(0, 1.45f, 0.8f));
                to(f.look, hold, s(56, 62, t) * 0.5f);
                f.lookW = w * 0.8f;
            }
            case CHECKOUT -> {
                boolean cash = "cash".equals(v);
                float put = s(4, 9, t) * (1 - s(12, 16, t));
                float[] counter = p(0.0f, 0.98f, 0.45f);
                to(f.r, add(counter, 0.1f, 0, 0), put);
                set(f.l, L_REST);
                to(f.l, add(counter, -0.1f, 0, 0), put);
                f.wl = put;
                if (t < 12) f.propR = t >= 2 ? "goods" : "";
                to(f.r, R_POCKET, s(20, 24, t));
                if (t >= 23 && t < 38) f.propR = cash ? "cash" : "card";
                to(f.r, cash ? p(0.04f, 1.08f, 0.52f) : p(0.20f, 1.02f, 0.42f), s(25, 29, t));
                if (cash && t >= 32) f.propR = t >= 38 ? f.propR : "";
                to(f.r, R_POCKET, s(34, 38, t));
                to(f.r, p(0.10f, 0.98f, 0.42f), s(42, 46, t));
                if (t >= 46) f.propR = "bag";
                to(f.r, R_REST, s(48, 56, t));
                f.wr = w;
                set(f.look, counter);
                f.lookW = w * 0.6f;
            }
            case DOOR -> {
                float reach = s(0, 4, t) * (1 - s(9, 12, t));
                to(f.r, p(0.26f, 1.0f, 0.40f + 0.12f * s(4, 9, t)), reach);
                f.wr = reach;
            }
            case FUEL -> {
                float[] tank = p(0.0f, 0.88f, 0.5f), pump = p(0.38f, 1.12f, -0.22f);
                to(f.r, pump, s(8, 13, t));
                if (t >= 13 && t < 199) f.propR = "nozzle";
                to(f.r, p(0.14f, 1.0f, 0.32f), s(16, 24, t));
                // левая открывает лючок бака
                float flap = s(20, 25, t) * (1 - s(28, 32, t));
                set(f.l, L_REST);
                to(f.l, add(tank, -0.08f, 0.02f, 0.02f), flap);
                to(f.r, tank, s(28, 34, t));
                float lean = s(34, 42, t) * (1 - s(178, 184, t));
                to(f.l, p(-0.18f, 1.18f, 0.42f), lean); // ладонь на крыше
                f.wl = Math.max(flap, lean);
                f.press = lean * (0.5f + 0.5f * MathHelper.sin(t * 0.7f));
                to(f.r, add(tank, 0.05f, 0.08f, -0.18f), s(182, 188, t));
                to(f.r, pump, s(189, 196, t));
                to(f.r, R_REST, s(199, 206, t));
                f.tiltR = -25;
                f.wr = w;
                set(f.look, tank);
                f.lookW = w * 0.7f;
            }
            case REPAIR -> {
                f.crouch = s(0, 10, t) * (1 - s(152, 166, t));
                float[] bolt = p(0.04f, 0.55f, 0.52f);
                to(f.r, bolt, s(6, 14, t));
                if (t >= 8 && t < 158) f.propR = "wrench";
                float cyc = (t - 14) % 22 / 22f;
                if (t > 14 && t < 150) {
                    float a = MathHelper.sin(cyc * MathHelper.TAU);
                    f.r[0] += 0.08f * a;
                    f.r[1] += 0.05f * Math.abs(a);
                    f.rollR = 40 * a;
                }
                set(f.l, L_REST);
                to(f.l, p(-0.18f, 0.6f, 0.5f), s(6, 14, t) * (1 - s(150, 158, t)));
                f.wl = s(6, 14, t) * (1 - s(150, 158, t));
                to(f.r, R_REST, s(152, 162, t));
                f.wr = w;
                set(f.look, bolt);
                f.lookW = w;
            }
            case SAFE -> {
                f.crouch = 0.65f * s(0, 8, t) * (1 - s(58, 64, t));
                float[] dial = p(0.04f, 0.78f, 0.46f), handle = p(-0.12f, 0.72f, 0.46f);
                to(f.r, dial, s(4, 11, t));
                if (t > 11 && t < 44) {
                    float a = (t - 11) * 0.35f * (t < 24 ? 1 : t < 34 ? -1 : 1);
                    f.r[0] += 0.03f * MathHelper.cos(a);
                    f.r[1] += 0.03f * MathHelper.sin(a);
                }
                set(f.l, L_REST);
                to(f.l, handle, s(6, 12, t));
                to(f.l, add(handle, -0.1f, 0, -0.22f), s(44, 54, t));
                f.wl = s(6, 12, t) * (1 - s(58, 64, t));
                to(f.r, R_REST, s(44, 52, t));
                f.wr = w;
                set(f.look, dial);
                f.lookW = w;
            }
            case CAR_IN -> {
                // водитель слева: левая тянется к двери и захлопывает
                float reach = s(0, 4, t) * (1 - s(14, 18, t));
                set(f.l, p(-0.55f, 1.05f, 0.1f));
                to(f.l, p(-0.35f, 1.0f, 0.15f), s(9, 14, t));
                f.wl = reach;
            }
            case BELT -> {
                float k1 = s(0, 4, t) * (1 - s(12, 16, t));
                set(f.r, p(-0.22f, 1.36f, 0.08f));
                to(f.r, p(0.24f, 0.96f, 0.14f), s(4, 11, t));
                f.wr = k1;
            }
            case CAR_OUT -> {
                float k1 = s(0, 3, t) * (1 - s(10, 14, t));
                set(f.r, p(0.42f, 1.0f, -0.2f));
                to(f.r, p(0.30f, 1.02f, -0.45f), s(5, 9, t));
                f.wr = k1;
            }
            case SMOKE -> {
                float[] relax = p(0.24f, 1.02f, 0.16f);
                to(f.r, R_POCKET, s(1, 5, t));
                if (t >= 5 && t < 308) f.propR = "cigarette";
                to(f.r, MOUTH, s(6, 11, t));
                // прикурить: левая с зажигалкой к лицу
                float light = s(10, 14, t) * (1 - s(22, 28, t));
                set(f.l, L_REST);
                to(f.l, p(0.0f, 1.48f, 0.24f), light);
                if (light > 0.2f) f.propL = "lighter";
                f.wl = light;
                to(f.r, relax, s(26, 34, t));
                for (float a : new float[]{60, 150, 240}) if (t >= a - 1 && t <= a + 22) sip(f, t, a, a + 22, relax, 0);
                if (t > 282) set(f.r, relax);
                to(f.r, p(0.42f, 0.95f, 0.42f), s(296, 304, t));
                to(f.r, R_REST, s(308, 320, t));
                f.wr = w;
            }
            case DRINK -> {
                String item = "coffee".equals(v) ? "coffee" : "beer".equals(v) ? "beer" : "soda";
                float[] hold = p(0.20f, 1.06f, 0.18f);
                to(f.r, hold, s(2, 8, t));
                if (t >= 4 && t < 126) f.propR = item;
                for (float a : new float[]{24, 64, 98}) if (t >= a - 1 && t <= a + 22) sip(f, t, a, a + 22, hold, "coffee".equals(item) ? 35 : 70);
                if (t > 120) set(f.r, hold);
                to(f.r, R_REST, s(120, 128, t));
                f.wr = w;
            }
        }
    }
}
