package com.yompix.glukhoman.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Настройки игрока (config/glukhoman.json). Меняются кнопкой «Глухомань» в меню паузы. */
public class GConfig {
    public boolean fewerScares = false;   // меньше скримеров
    public boolean noFlashing = false;    // без вспышек и резкого мигания
    public boolean subtitles = true;
    public boolean heartbeat = true;
    public boolean hints = true;          // H — направление к цели
    public int brightness = 0;
    public int screenFx = 2;            // 0 — без пост-эффектов, 1 — слабо, 2 — полностью
    public boolean fakeCrash = true;
    public boolean vhs = true;           // лёгкий плёночный фильтр и без камеры (в видоискателе VHS всегда)
    public boolean noBlood = false;      // убрать кровь с экрана скримера
    public boolean goreReduced = false;  // 18+: «меньше жестокости» — останки заменены кровью
    public boolean liveAnim = true;
    public int chapterReached = 0;
    public boolean kitUi = true;
    // настройки (этап 7) — все реально применяются (см. FearFx, HandCam, Timeline, Cine.hand, Hud)
    public int vhsStrength = 100;         // сила плёночного фильтра, %
    public boolean fxGlitch = true;       // помехи/срывы кадра
    public boolean fxBlur = true;         // размытие и лучи в кат-сценах
    public boolean fxTear = true;         // «разрыв» кадра
    public int cineShake = 100;           // дрожь камеры в кат-сценах, %
    public boolean noShake = false;       // доступность: без тряски вообще (руки и кат-сцены)
    public boolean hudObjective = true;   // цель на экране          // свои пауза и экран провала (выкл. — ванильные)        // до какой главы дошёл (для экрана «Главы»)
    // субтитры (этап 4)
    public String subLang = "auto";       // auto — как язык игры; ru_ru / uk_ua / en_us
    public int subSize = 100;             // %, 75–200
    public int subPos = 0;                // 0 — внизу, 1 — выше
    public int subBg = 55;                // фон под строкой, %
    public int subStyle = 0;              // 0 — «плёнка» (тёплый), 1 — «чистый» (белый)
    public boolean subContrast = false;   // высокий контраст      // новая анимация персонажей (взгляд, моргание, рот, жесты); выкл. — как в 0.4.2
    public int handSway = 70;            // «Покачивание камеры» в руках, % (0 — неподвижная)
    public boolean ageAck = false;       // предупреждение 18+ уже показано    // ложный «вылет игры» в финале            // 0 — темно (как задумано), 1 — обычно, 2 — светлее

    private static GConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("glukhoman.json");
    }

    public static GConfig get() {
        if (INSTANCE == null) {
            try {
                INSTANCE = Files.exists(path()) ? GSON.fromJson(Files.readString(path()), GConfig.class) : new GConfig();
            } catch (Exception e) {
                INSTANCE = new GConfig();
            }
            if (INSTANCE == null) INSTANCE = new GConfig();
        }
        return INSTANCE;
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (Exception ignored) {
        }
    }
}
