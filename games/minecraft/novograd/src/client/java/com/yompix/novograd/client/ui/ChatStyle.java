package com.yompix.novograd.client.ui;

import com.yompix.novograd.client.NovogradConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

/**
 * 1.0, чат (владелец: «и сам чат сделай так, чтоб видно было»):
 * <ul>
 *   <li>читаемость — тёмная полупрозрачная подложка, шрифт чуть крупнее, плотнее строки: ставится ОДИН раз ванильными
 *       настройками (Настройки чата Minecraft, их можно вернуть); без миксинов в отрисовку — совместимо с Sodium,
 *       ImmediatelyFast и любыми модами чата;</li>
 *   <li>игроки — имя своим цветом («Имя: текст»), команды, LAN и сервер работают как раньше;</li>
 *   <li>сообщения мода (простой текст сервера) — короче 100 знаков, серой меткой «•», тот же текст подряд в течение
 *       30 с — не повторяется (уходит в журнал Quiet.LOG).</li>
 * </ul>
 */
public final class ChatStyle {
    private ChatStyle() {}

    static final int MAX = 100;
    private static final Map<String, Long> RECENT = new HashMap<>();
    private static final int[] NAME_COLORS = {0xFF6BD3FF, 0xFFFFC85A, 0xFF8BE36B, 0xFFFF7A8A, 0xFFC59BFF, 0xFF5AE0C8, 0xFFFFA25A};

    public static void init() {
        // когда в чате последний раз была строка (Quiet уводит строку статуса и цель вправо от чата, пока он виден);
        // отмечаем в ALLOW_* — свои строки мод перехватывает там же, и событие GAME/CHAT после этого не приходит
        ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> {
            if (!overlay) Quiet.chatAt = System.currentTimeMillis();
            return true;
        });
        ClientReceiveMessageEvents.ALLOW_CHAT.register((msg, signed, sender, params, ts) -> {
            Quiet.chatAt = System.currentTimeMillis();
            return true;
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> {
            if (overlay || !com.yompix.novograd.story.Stage.ON) return true;
            if (!(msg.getContent() instanceof PlainTextContent)) return true; // ванильные (переводимые) — как есть
            String s = msg.getString().trim();
            long now = System.currentTimeMillis();
            Long last = RECENT.get(s);
            RECENT.put(s, now);
            if (RECENT.size() > 200) RECENT.entrySet().removeIf(e -> now - e.getValue() > 60_000);
            if (last != null && now - last < 30_000) {
                Quiet.LOG.addLast("чат, повтор: " + s);
                return false;
            }
            if (s.length() <= MAX && s.startsWith("•")) return true;
            String shortS = shorten(s, MAX - 2);
            if (shortS.length() < s.length()) Quiet.LOG.addLast("чат, полностью: " + s);
            var style = msg.getStyle().getColor() != null ? msg.getStyle() : msg.getSiblings().isEmpty() ? msg.getStyle() : msg.getSiblings().get(0).getStyle();
            MutableText t = Text.literal("• ").formatted(Formatting.DARK_GRAY).append(Text.literal(shortS).setStyle(style.getColor() == null ? style.withColor(Formatting.GRAY) : style));
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(t);
            return false;
        });
        ClientReceiveMessageEvents.ALLOW_CHAT.register((msg, signed, sender, params, ts) -> {
            if (sender == null || !com.yompix.novograd.story.Stage.ON) return true;
            String name = sender.getName();
            int col = NAME_COLORS[Math.floorMod(name.hashCode(), NAME_COLORS.length)];
            Text body = signed != null ? signed.getContent() : msg;
            String raw = body.getString();
            // без «<Имя>» второй раз: в одиночной игре и на части серверов содержимое уже «<Имя> текст»
            String pre = "<" + name + ">";
            MutableText bodyT = raw.startsWith(pre) ? Text.literal(raw.substring(pre.length()).trim()) : body.copy();
            MutableText t = Text.literal(name).styled(s -> s.withColor(col & 0xFFFFFF).withBold(true))
                    .append(Text.literal(": ").formatted(Formatting.GRAY))
                    .append(bodyT.formatted(Formatting.WHITE));
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(t);
            MinecraftClient.getInstance().getNarratorManager().narrateChatMessage(t);
            return false;
        });
    }

    /** Один раз: ванильные настройки чата — темнее подложка, крупнее и плотнее текст. */
    public static void applyOnce(MinecraftClient mc) {
        if (!com.yompix.novograd.story.Stage.ON || NovogradConfig.get().chatStyled || mc.options == null) return;
        NovogradConfig.get().chatStyled = true;
        mc.options.getTextBackgroundOpacity().setValue(0.72);
        mc.options.getChatOpacity().setValue(1.0);
        mc.options.getChatScale().setValue(1.1);
        mc.options.getChatLineSpacing().setValue(0.15);
        mc.options.getChatWidth().setValue(0.8);
        mc.options.write();
        NovogradConfig.save();
    }

    /** Коротко: первая фраза, если влезает; иначе по словам до max с «…». */
    public static String shorten(String s, int max) {
        if (s.length() <= max) return s;
        int dot = -1;
        for (int i = 20; i < Math.min(s.length(), max); i++)
            if ((s.charAt(i) == '.' || s.charAt(i) == '!' || s.charAt(i) == '?') && (i + 1 >= s.length() || s.charAt(i + 1) == ' ')) dot = i;
        if (dot > 0) return s.substring(0, dot + 1);
        int sp = s.lastIndexOf(' ', max - 1);
        return (sp > max / 2 ? s.substring(0, sp) : s.substring(0, max - 1)) + "…";
    }
}
