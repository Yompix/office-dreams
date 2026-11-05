package com.yompix.novograd.client.render;

final class LifeShade {
    private LifeShade() {}

    static int shade(int argb, float k) {
        int a = argb >>> 24, r = (int) (((argb >> 16) & 255) * k), g = (int) (((argb >> 8) & 255) * k), b = (int) ((argb & 255) * k);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
