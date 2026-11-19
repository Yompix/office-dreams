package com.yompix.novograd.perf;

/**
 * Настройка «Производительность» (0 — низкая, 1 — средняя, 2 — высокая): клиент присылает её серверу (пакет perf_level).
 * На сервере от неё зависят только невидимые игроку вещи: как часто думают дальние жители и как быстро в фоне
 * догенерируется остров. Механика игры не меняется.
 */
public final class PerfLevel {
    private PerfLevel() {}

    public static volatile int level = 1;

    /** Жители дальше этого (блоков) от всех игроков думают реже (раз в 4 тика). */
    public static double aiNear() {
        return level <= 0 ? 40 : level == 1 ? 56 : 80;
    }

    /** Дальше этого — почти «спят» (раз в 20 тиков), их всё равно не видно. */
    public static double aiFar() {
        return level <= 0 ? 96 : level == 1 ? 112 : 160;
    }

    /** Фоновая догенерация острова: раз в сколько тиков один участок (0 — не догенерировать в фоне). */
    public static int pregenEvery(boolean weak) {
        if (level <= 0) return 0;
        return level == 1 ? (weak ? 16 : 8) : (weak ? 8 : 4);
    }
}
