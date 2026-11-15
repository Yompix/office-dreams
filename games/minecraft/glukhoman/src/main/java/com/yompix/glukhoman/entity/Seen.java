package com.yompix.glukhoman.entity;

/** Кто что видит: 1 — только в видоискателе камеры, 2 — только глазами (в камере его нет). Решает клиент каждого игрока. */
public interface Seen {
    int CAMERA_ONLY = 1, EYE_ONLY = 2;

    int seen();
}
