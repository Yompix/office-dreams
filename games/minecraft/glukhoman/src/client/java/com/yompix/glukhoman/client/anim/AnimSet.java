package com.yompix.glukhoman.client.anim;

import java.util.List;
import java.util.function.Function;

/**
 * Набор анимаций персонажа: базовые клипы GeckoLib (ходьба, стойка) и жесты-слои.
 *
 * @param walk      имя клипа ходьбы (без «animation.&lt;набор&gt;.»)
 * @param idle      стойка
 * @param idles     случайные жесты на месте
 * @param tired     жест после долгой ходьбы
 * @param talk      руки во время речи
 * @param settle    «досадка» веса при остановке
 * @param breath    частота дыхания, Гц (0 — не дышит)
 * @param lookLimit насколько голова поворачивается от корпуса, °
 * @param lookZeta  демпфирование взгляда: 1 — без перелёта, меньше — перелетает и медленно возвращается
 * @param lookSpeed скорость взгляда (ω пружины)
 * @param tilt      крен головы при взгляде вбок, ° (у людей 0)
 * @param blinks    моргает ли
 * @param picker    свой выбор базового клипа (Обходчик, лунатики); null — ходьба/стойка
 */
public record AnimSet(String walk, String idle, List<Clip> idles, Clip tired, Clip talk, Clip settle, double breath,
                      double lookLimit, double lookZeta, double lookSpeed, double tilt, boolean blinks, Function<Animator, String> picker) {
    public AnimSet(String walk, String idle, List<Clip> idles, Clip tired, Clip talk, Clip settle, double breath) {
        this(walk, idle, idles, tired, talk, settle, breath, 80, 1, 7, 0, true, null);
    }

    public AnimSet withLook(double limit, double zeta, double speed, double tilt) {
        return new AnimSet(walk, idle, idles, tired, talk, settle, breath, limit, zeta, speed, tilt, blinks, picker);
    }

    public AnimSet noBlink() {
        return new AnimSet(walk, idle, idles, tired, talk, settle, breath, lookLimit, lookZeta, lookSpeed, tilt, false, picker);
    }

    public AnimSet pick(Function<Animator, String> p) {
        return new AnimSet(walk, idle, idles, tired, talk, settle, breath, lookLimit, lookZeta, lookSpeed, tilt, blinks, p);
    }
}
