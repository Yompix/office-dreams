package com.yompix.glukhoman.client.anim;

import java.util.Set;

/**
 * Жест/движение для слоя аниматора. Время — секунды от начала клипа. Кости из {@code own} клип ведёт сам (база под ними
 * гасится весом слоя — так рука «показывает дорогу», даже если ноги идут); остальные кости — добавкой к базе.
 *
 * @param len       длина, с (0 — пока не снимут)
 * @param fadeIn    плавный вход, с
 * @param fadeOut   плавный выход, с
 * @param priority  кто главнее на общих костях (больше — главнее)
 * @param look      насколько при этом жесте голова продолжает следить за целью (0…1)
 * @param own       кости, которые клип ведёт целиком
 * @param body      сама поза во времени
 * @param sounds    звуки по времени: {секунда, имя}
 */
public record Clip(String name, double len, double fadeIn, double fadeOut, int priority, double look, Set<String> own, Body body, Object[][] sounds) {
    public interface Body {
        void at(double t, Pose p, Animator a);
    }

    public static Clip of(String name, double len, double fadeIn, double fadeOut, int priority, double look, Set<String> own, Body body, Object[]... sounds) {
        return new Clip(name, len, fadeIn, fadeOut, priority, look, own, body, sounds);
    }
}
