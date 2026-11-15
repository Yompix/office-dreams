package com.yompix.glukhoman.client.anim;

import java.util.HashMap;
import java.util.Map;

/**
 * Поза для слоя: смещения костей (поворот в градусах, сдвиг в пикселях модели). Знаки — как у костей GeckoLib в коде:
 * X+ — наклон/взмах вперёд (голова вниз, рука вперёд-вверх, предплечье сгибается), Y+ — поворот вправо (для персонажа),
 * Z — крен. Клипы пишутся в этих знаках, без «переворотов» из JSON.
 */
public final class Pose {
    final Map<String, float[]> m = new HashMap<>();

    public float[] b(String bone) {
        return m.computeIfAbsent(bone, k -> new float[6]);
    }

    public Pose rot(String bone, double x, double y, double z) {
        float[] a = b(bone);
        a[0] += (float) x;
        a[1] += (float) y;
        a[2] += (float) z;
        return this;
    }

    public Pose pos(String bone, double x, double y, double z) {
        float[] a = b(bone);
        a[3] += (float) x;
        a[4] += (float) y;
        a[5] += (float) z;
        return this;
    }

    void clear() {
        m.clear();
    }
}
