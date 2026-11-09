package com.yompix.novograd.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * «Режим кино» из меню паузы: чистый кадр без интерфейса, в машине — кино-камера с ракурсами.
 * Подсказывает, чем снимать: Flashback (запись и монтаж), Freecam (свободная камера), Camera Utils (зум) — если они стоят.
 */
public final class FilmMode {
    private FilmMode() {}

    public static boolean active;
    private static boolean hudWas;

    public static void toggle(MinecraftClient mc) {
        active = !active;
        if (active) {
            hudWas = mc.options.hudHidden;
            mc.options.hudHidden = true;
            if (mc.player != null && mc.player.getVehicle() instanceof com.yompix.novograd.entity.CarEntity && !CinemaCam.active()) CinemaCam.toggle(mc);
            StringBuilder tip = new StringBuilder("Режим кино: интерфейс скрыт. Выход — пауза → «Режим кино» или F1.");
            if (FabricLoader.getInstance().isModLoaded("flashback")) tip.append(" Запись видео — кнопка Flashback в меню паузы.");
            if (FabricLoader.getInstance().isModLoaded("freecam")) tip.append(" F4 — свободная камера.");
            tip.append(" Своё кино с кадрами и актёрами — F7 «Киностудия».");
            if (FabricLoader.getInstance().isModLoaded("bbs")) tip.append(" BBS (актёры, камера, анимации) — его клавиша в «Управление» → BBS.");
            if (FabricLoader.getInstance().isModLoaded("replaymod")) tip.append(" Replay Mod: запись повторов — меню паузы → Replay Viewer.");
            if (FabricLoader.getInstance().isModLoaded("camerautils")) tip.append(" Зум — Camera Utils (клавиша в настройках управления).");
            if (mc.player != null) mc.player.sendMessage(Text.literal(tip.toString()).formatted(Formatting.LIGHT_PURPLE), false);
        } else {
            mc.options.hudHidden = hudWas;
            if (CinemaCam.active()) CinemaCam.toggle(mc);
        }
    }

    /** Нажали F1 вручную в режиме кино — считаем, что режим выключен. */
    public static void tick(MinecraftClient mc) {
        if (active && !mc.options.hudHidden) {
            active = false;
            if (CinemaCam.active()) CinemaCam.toggle(mc);
        }
    }
}
