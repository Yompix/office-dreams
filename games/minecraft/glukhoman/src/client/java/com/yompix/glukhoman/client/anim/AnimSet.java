package com.yompix.glukhoman.client.anim;

import java.util.List;

/**
 * Набор анимаций персонажа: базовые клипы GeckoLib (ходьба, стойка) и жесты-слои.
 *
 * @param walk   имя клипа ходьбы в human.animation.json (без «animation.human.»)
 * @param idle   стойка
 * @param idles  случайные жесты на месте
 * @param tired  жест после долгой ходьбы (одышка, кашель)
 * @param talk   руки и плечи во время речи (слой, пока говорит)
 * @param settle «досадка» веса при остановке
 * @param breath частота дыхания в покое, Гц
 */
public record AnimSet(String walk, String idle, List<Clip> idles, Clip tired, Clip talk, Clip settle, double breath) {
}
