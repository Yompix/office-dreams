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
