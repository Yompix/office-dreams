package com.yompix.novograd.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * 0.9.10: Simple Voice Chat в Новограде. Один раз при первом запуске с модом голоса: «говорить» (push-to-talk) — V,
 * а его клавиши, которые совпадают с клавишами Новограда (G — телефон, M — карта, N — нитро, H — гудок…), снимаются:
 * всё это есть в окне настроек голоса (телефон → «Рация» → «Настройки голоса»). Жест игрока переезжает с V на U.
 * Классы мода голоса не трогаем напрямую — только по имени (мод может отсутствовать).
 */
public final class VoiceKeys {
    private VoiceKeys() {}

    private static final Set<String> SVC = Set.of("key.push_to_talk", "key.voice_chat", "key.voice_chat_settings", "key.voice_chat_group",
            "key.mute_microphone", "key.disable_voice_chat", "key.hide_icons", "key.whisper", "key.voice_chat_toggle_recording",
            "key.voice_chat_adjust_volumes");

    public static boolean installed() {
        return FabricLoader.getInstance().isModLoaded("voicechat");
    }

    public static void setupOnce(MinecraftClient mc) {
        if (!installed() || mc.options == null || NovogradConfig.get().voiceKeys) return;
        KeyBinding ptt = null;
        Set<String> ours = new HashSet<>();
        for (KeyBinding k : mc.options.allKeys) {
            if (k.getTranslationKey().equals("key.push_to_talk")) ptt = k;
            else if (!SVC.contains(k.getTranslationKey()) && !k.isUnbound()) ours.add(k.getBoundKeyTranslationKey());
        }
        if (ptt == null) return; // мод голоса ещё не зарегистрировал клавиши
        InputUtil.Key v = InputUtil.Type.KEYSYM.createFromCode(org.lwjgl.glfw.GLFW.GLFW_KEY_V);
        // V освобождаем от жеста Новограда (на случай старых настроек), отдаём голосу
        for (KeyBinding k : mc.options.allKeys)
            if (k.getTranslationKey().equals("key.novograd.emote") && k.getBoundKeyTranslationKey().equals(v.getTranslationKey()))
                k.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(org.lwjgl.glfw.GLFW.GLFW_KEY_U));
        ours.remove(v.getTranslationKey());
        ptt.setBoundKey(v);
        for (KeyBinding k : mc.options.allKeys)
            if (SVC.contains(k.getTranslationKey()) && k != ptt && (ours.contains(k.getBoundKeyTranslationKey()) || k.getBoundKeyTranslationKey().equals(v.getTranslationKey())))
                k.setBoundKey(InputUtil.UNKNOWN_KEY);
        KeyBinding.updateKeysByCode();
        mc.options.write();
        NovogradConfig.get().voiceKeys = true;
        NovogradConfig.save();
        com.yompix.novograd.Novograd.LOG.info("[голос] клавиши: говорить — V, совпадения с Новоградом сняты");
    }

    /** Клавиша «говорить» для подсказок. */
    public static String talkKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null)
            for (KeyBinding k : mc.options.allKeys)
                if (k.getTranslationKey().equals("key.push_to_talk")) return k.getBoundKeyLocalizedText().getString();
        return "V";
    }

    /** Окно мода голоса (микрофон, громкость, режим «нажми и говори» / «по голосу»). */
    public static void openSettings() {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (String cls : new String[]{"de.maxhenkel.voicechat.gui.VoiceChatScreen", "de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen"})
            try {
                mc.setScreen((net.minecraft.client.gui.screen.Screen) Class.forName(cls).getConstructor().newInstance());
                return;
            } catch (Throwable ignored) {
            }
        if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal("Нет мода Simple Voice Chat"), true);
    }
}
