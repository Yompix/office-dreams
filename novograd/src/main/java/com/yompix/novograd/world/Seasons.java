package com.yompix.novograd.world;

import com.yompix.novograd.life.LifeNet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.time.LocalDate;

/**
 * Календарь острова: день → время года (лето, осень, зима, весна по N игровых дней, N настраивается: /outdoor season len N).
 * Или по настоящему календарю (/outdoor season real). Клиент красит траву и листву (осень — жёлто-рыжая, зима — в инее),
 * зимой вместо дождя идёт снег; погода: осенью чаще дождь, зимой — снегопады. Праздники — world/Holidays.
 */
public final class Seasons {
    private Seasons() {}

    public enum Season {
        SUMMER("Лето", 0.15f), AUTUMN("Осень", 0.45f), WINTER("Зима", 0.5f), SPRING("Весна", 0.3f);

        public final String title;
        final float rain;

        Season(String title, float rain) {
            this.title = title;
            this.rain = rain;
        }
    }

    public static final int DEFAULT_LEN = 5;
    private static long lastDay = -1;
    private static String lastSent = "";

    public static long day(ServerWorld w) {
        return w.getTimeOfDay() / 24000L + 1;
    }

    public static int len(ServerWorld w) {
        try {
            return Math.max(1, Integer.parseInt(TownState.get(w).storyPlaces.getOrDefault("season_len", String.valueOf(DEFAULT_LEN))));
        } catch (NumberFormatException e) {
            return DEFAULT_LEN;
        }
    }

    public static boolean real(ServerWorld w) {
        return "real".equals(TownState.get(w).storyPlaces.get("season_mode"));
    }

    public static Season season(ServerWorld w) {
        String f = TownState.get(w).storyPlaces.get("season_force");
        if (f != null) for (Season s : Season.values()) if (s.name().equalsIgnoreCase(f)) return s;
        if (real(w)) {
            int m = LocalDate.now().getMonthValue();
            return m == 12 || m <= 2 ? Season.WINTER : m <= 5 ? Season.SPRING : m <= 8 ? Season.SUMMER : Season.AUTUMN;
        }
        return Season.values()[(int) (((day(w) - 1) / len(w)) % 4)];
    }

    /** День внутри времени года, с нуля. */
    public static int dayIn(ServerWorld w) {
        if (real(w)) {
            LocalDate d = LocalDate.now();
            int m = d.getMonthValue();
            int start = switch (m) { case 12, 1, 2 -> 12; case 3, 4, 5 -> 3; case 6, 7, 8 -> 6; default -> 9; };
            int months = Math.floorMod(m - start, 12);
            return months * 30 + d.getDayOfMonth() - 1;
        }
        return (int) ((day(w) - 1) % len(w));
    }

    public static void tick(MinecraftServer s) {
        if (s.getTicks() % 100 != 37) return;
        ServerWorld w = s.getOverworld();
        if (!(w.getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) return;
        long day = day(w);
        if (day != lastDay) {
            if (lastDay > 0) weather(w);
            lastDay = day;
        }
        NbtCompound st = state(w);
        String key = st.getString("season") + st.getString("holiday");
        if (!key.equals(lastSent)) {
            lastSent = key;
            for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) sendState(p);
        }
    }

    /** Новый день: погода по времени года (если «погоду» не закрепили читом или командой). */
    private static void weather(ServerWorld w) {
        if ("off".equals(TownState.get(w).storyPlaces.get("season_weather")) || w.isRaining()) return;
        Season s = season(w);
        if (w.getRandom().nextFloat() < s.rain) {
            int len = 5000 + w.getRandom().nextInt(7000);
            w.setWeather(0, len, true, s == Season.AUTUMN && w.getRandom().nextFloat() < 0.2f);
        }
    }

    public static NbtCompound state(ServerWorld w) {
        NbtCompound n = new NbtCompound();
        Season s = season(w);
        n.putString("season", s.name());
        n.putString("title", s.title);
        n.putLong("day", day(w));
        n.putInt("dayIn", dayIn(w));
        n.putInt("len", real(w) ? 91 : len(w));
        n.putBoolean("real", real(w));
        Holidays.Holiday h = Holidays.current(w);
        n.putString("holiday", h.name());
        n.putString("holidayTitle", h.title);
        n.putString("holidayMode", Holidays.mode(w));
        n.putString("next", Holidays.nextText(w));
        return n;
    }

    public static void sendState(ServerPlayerEntity p) {
        if (!(p.getServerWorld().getChunkManager().getChunkGenerator() instanceof IslandChunkGenerator)) return;
        LifeNet.send(p, "out_season", state(p.getServerWorld()));
    }

    public static String debug(ServerPlayerEntity p, String[] a) {
        ServerWorld w = p.getServerWorld();
        var map = TownState.get(w).storyPlaces;
        if (a.length > 1) {
            switch (a[1]) {
                case "len" -> map.put("season_len", String.valueOf(Math.max(1, Integer.parseInt(a[2]))));
                case "real" -> map.put("season_mode", "real");
                case "game" -> {
                    map.remove("season_mode");
                    map.remove("season_force");
                }
                case "auto" -> map.remove("season_force");
                case "weather" -> map.put("season_weather", a.length > 2 && a[2].equals("off") ? "off" : "on");
                case "summer", "autumn", "winter", "spring", "лето", "осень", "зима", "весна" -> map.put("season_force", switch (a[1]) {
                    case "лето" -> "summer";
                    case "осень" -> "autumn";
                    case "зима" -> "winter";
                    case "весна" -> "spring";
                    default -> a[1];
                });
                default -> {
                    return "season summer|autumn|winter|spring|auto|len N|real|game|weather on|off";
                }
            }
            TownState.get(w).markDirty();
            lastSent = "";
        }
        return "Сейчас: " + season(w).title + ", день " + (dayIn(w) + 1) + " из " + (real(w) ? "~91" : len(w)) + (real(w) ? " (по календарю)" : "");
    }
}
