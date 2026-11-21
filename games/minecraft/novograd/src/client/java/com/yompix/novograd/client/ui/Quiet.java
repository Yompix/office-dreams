package com.yompix.novograd.client.ui;

import com.yompix.novograd.client.NovogradConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.0 «тихий экран», как в GTA (владелец: «слишком много текста на экране, полэкрана занимают»):
 * <ul>
 *   <li>{@link #subtitle} — субтитры: не больше двух коротких строк в нижней трети, мелкий шрифт (Настройки → «Размер
 *       субтитров»), полупрозрачная полоса; длинная реплика режется на куски по 2 строки, куски идут по очереди в такт
 *       голосу; имя говорящего — цветом в начале строки, а не отдельной крупной надписью;</li>
 *   <li>{@link #hint} — подсказка: маленькая рамка слева сверху, одна за раз, коротко; «один раз» — показанные ключи
 *       запоминаются для этого мира (Настройки → «Подсказки» выключают все);</li>
 *   <li>{@link #notify} — уведомления (награды, навыки, деньги): компактная очередь справа, не больше двух сразу.</li>
 * </ul>
 */
public final class Quiet {
    private Quiet() {}

    // ================================================================== субтитры
    public static float subScale() {
        return MathHelper.clamp(NovogradConfig.get().subtitleScale, 0.6f, 1.4f);
    }

    /** Реплика → куски по ≤2 строки (ширина — в пикселях экрана с учётом масштаба). */
    public static List<List<String>> chunks(TextRenderer tr, String prefix, String text, int maxW) {
        float s = subScale();
        int w = (int) (maxW / s);
        List<String> lines = new ArrayList<>();
        for (var l : tr.getTextHandler().wrapLines(prefix + text, w, net.minecraft.text.Style.EMPTY)) lines.add(l.getString());
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += 2) out.add(lines.subList(i, Math.min(lines.size(), i + 2)));
        if (out.isEmpty()) out.add(List.of(""));
        return out;
    }

    /** Какой кусок показывать: доля прошедшего времени реплики, куски — пропорционально числу букв. */
    public static int chunkAt(List<List<String>> ch, double progress) {
        if (ch.size() <= 1) return 0;
        int total = 0;
        for (var c : ch) total += String.join(" ", c).length();
        double want = MathHelper.clamp(progress, 0, 1) * total, acc = 0;
        for (int i = 0; i < ch.size(); i++) {
            acc += String.join(" ", ch.get(i)).length();
            if (want < acc) return i;
        }
        return ch.size() - 1;
    }

    /**
     * Нарисовать кусок субтитров; baseY — нижняя граница текста. Имя (prefix «ИМЯ: ») в первой строке первого куска —
     * цветом героя.
     */
    public static void subtitle(DrawContext ctx, TextRenderer tr, List<String> lines, String prefix, int nameColor, int textColor, int cx, int baseY, float alpha) {
        if (lines.isEmpty() || alpha <= 0.01f) return;
        float s = subScale();
        int lh = 10;
        int bw = 0;
        for (String l : lines) bw = Math.max(bw, tr.getWidth(l));
        int h = lines.size() * lh;
        var m = ctx.getMatrices();
        m.push();
        m.translate(cx, baseY - h * s, 0);
        m.scale(s, s, 1);
        ctx.fill(-bw / 2 - 6, -3, bw / 2 + 6, h + 1, Ui.alpha(0x8C000000, alpha));
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int x = -tr.getWidth(l) / 2, y = i * lh;
            if (!prefix.isEmpty() && l.startsWith(prefix)) {
                ctx.drawText(tr, prefix, x, y, Ui.alpha(nameColor | 0xFF000000, alpha), true);
                ctx.drawText(tr, l.substring(prefix.length()), x + tr.getWidth(prefix), y, Ui.alpha(textColor, alpha), true);
            } else ctx.drawText(tr, l, x, y, Ui.alpha(textColor, alpha), true);
        }
        m.pop();
    }

    // ================================================================== подсказки и уведомления: одна за раз
    /**
     * Владелец: «всё постоянно и быстро появляется». Правила: на экране одно всплывающее сообщение (подсказка ИЛИ
     * уведомление); между не срочными — не меньше {@link #GAP} мс; держится 0.06 с на букву, не меньше 2.5 с; повтор
     * того же текста подряд не показывается; одинаковые уведомления складываются («×3»); плавный вход и выход.
     */
    static final long GAP = 8000;
    /** Строка над хотбаром: y = высота − STATUS_Y; цель миссии (StoryHud) — над ней. */
    public static final int STATUS_Y = 70;
    /** Когда в чат пришла последняя строка (ChatStyle). */
    public static volatile long chatAt = -1;

    /**
     * Полоса по ширине для строки статуса и цели: пока чат виден (10 с после строки или открыт) — справа от него, чтобы
     * не писать поверх чата; иначе — весь экран. [левый край, правый край].
     */
    public static int[] lane(MinecraftClient mc, int W) {
        boolean chat = mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen || chatAt > 0 && System.currentTimeMillis() - chatAt < 10_500;
        if (chat && mc.options != null) {
            var ch = mc.inGameHud.getChatHud();
            int right = (int) Math.ceil((ch.getWidth() + 12) * ch.getChatScale()) + 6;
            if (W - right >= 150) return new int[]{right, W - 6};
        }
        return new int[]{6, W - 6};
    }
    private record Pop(String title, String sub, int color, boolean hint, int count) {}

    private static final ArrayDeque<Pop> QUEUE = new ArrayDeque<>();
    private static Pop cur;
    private static long curAt = -1, curFor, lastStart = -1;
    private static String lastText = "";
    /** Журнал всего, что сказано коротко или пропущено (для проверки и будущего «журнала» в телефоне). */
    public static final ArrayDeque<String> LOG = new ArrayDeque<>();

    static long readFor(String text) {
        return Math.max(2500, text.length() * 60L);
    }

    private static void log(String s) {
        LOG.addLast(s);
        while (LOG.size() > 60) LOG.pollFirst();
    }

    private static void offer(Pop p) {
        // не длиннее 100 знаков (владелец: «уведомления до 100 слов, а то долго всё читать»); полностью — в журнал
        if ((p.title() + " " + p.sub()).length() > ChatStyle.MAX) {
            log("полностью: " + p.title() + " " + p.sub());
            String t = ChatStyle.shorten(p.title(), ChatStyle.MAX);
            p = new Pop(t, ChatStyle.shorten(p.sub(), Math.max(0, ChatStyle.MAX - t.length())), p.color(), p.hint(), p.count());
        }
        String key = p.title() + "|" + p.sub();
        if (cur != null && (cur.title() + "|" + cur.sub()).equals(key)) return; // уже на экране
        for (var it = QUEUE.iterator(); it.hasNext(); ) {
            Pop q = it.next();
            if (q.title().equals(p.title()) && q.hint() == p.hint()) { // то же уведомление ещё ждёт — складываем
                it.remove();
                QUEUE.addLast(new Pop(p.title(), p.sub(), p.color(), p.hint(), q.count() + 1));
                return;
            }
        }
        if (key.equals(lastText) && System.currentTimeMillis() - lastStart < 60_000) {
            log("повтор: " + key);
            return;
        }
        QUEUE.addLast(p);
        while (QUEUE.size() > 4) log("сброшено: " + QUEUE.pollFirst().title());
    }

    /** Подсказка один раз за мир (key — что это за подсказка). Пустой key — каждый раз (но не подряд). */
    public static void hint(String key, String text) {
        if (!NovogradConfig.get().hints || text == null || text.isEmpty()) return;
        if (!key.isEmpty()) {
            String k = world() + "|" + key;
            if (NovogradConfig.get().seenHints.contains(k)) return;
            NovogradConfig.get().seenHints.add(k);
            if (NovogradConfig.get().seenHints.size() > 2000) NovogradConfig.get().seenHints.clear();
            NovogradConfig.save();
        }
        offer(new Pop(text, "", Ui.GOLD, true, 1));
    }

    /** Подсказка «пока стоишь здесь» (фото-точка…): видна, пока её зовут каждый кадр, и только если нет другого окна. */
    public static void context(String text) {
        if (!NovogradConfig.get().hints) return;
        ctxText = text;
        ctxAt = System.currentTimeMillis();
    }

    private static String ctxText = "";
    private static long ctxAt = -1;

    private static String world() {
        var mc = MinecraftClient.getInstance();
        if (mc.getServer() != null) return mc.getServer().getSaveProperties().getLevelName();
        return mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "?";
    }

    public static void notify(String title, String sub, int color) {
        offer(new Pop(title, sub == null ? "" : sub, color, false, 1));
    }

    // ================================================================== строка над хотбаром (бывший action bar)
    private static String stText = "", stPending;
    private static int stColor = 0xFFFFFFFF, stPendColor;
    private static long stAt = -1, stFor, stStart = -1;
    private static int money;
    private static long moneyAt = -1;

    /** Всё, что сервер и клиент пишут «над хотбаром». */
    public static void status(net.minecraft.text.Text t) {
        String s = t.getString().trim();
        if (s.isEmpty()) return;
        if (s.length() > ChatStyle.MAX) {
            log("полностью: " + s);
            s = ChatStyle.shorten(s, ChatStyle.MAX);
        }
        int col = color(t);
        long now = System.currentTimeMillis();
        boolean urgent = col == 0xFFFF5555 || col == 0xFFAA0000 || s.startsWith("⚠");
        // деньги «+$N» — один счётчик
        var mm = java.util.regex.Pattern.compile("^\\+\\s?\\$(\\d+)").matcher(s);
        if (mm.find() && s.length() < 24) {
            money = now - moneyAt < 4000 ? money + Integer.parseInt(mm.group(1)) : Integer.parseInt(mm.group(1));
            moneyAt = now;
            s = "+$" + money;
            col = 0xFF6BD36B;
            if (stText.startsWith("+$")) { // обновить на месте, без нового «всплытия»
                stText = s;
                stAt = now;
                stFor = readFor(s);
                return;
            }
        }
        if (s.equals(stText) && now - stAt < stFor + 400) { // тот же текст — просто держим дольше
            stAt = now;
            return;
        }
        if (urgent || stStart < 0 || now - stStart >= GAP && now - stAt >= stFor) {
            show(s, col, now);
        } else {
            if (stPending != null) log("заменено: " + stPending);
            stPending = s;
            stPendColor = col;
        }
    }

    private static void show(String s, int col, long now) {
        stText = s;
        stColor = col;
        stAt = now;
        stStart = now;
        stFor = readFor(s);
    }

    private static int color(net.minecraft.text.Text t) {
        var c = t.getStyle().getColor();
        if (c == null) for (var sib : t.getSiblings()) if (sib.getStyle().getColor() != null) {
            c = sib.getStyle().getColor();
            break;
        }
        return c == null ? 0xFFFFFFFF : 0xFF000000 | c.getRgb();
    }

    /** HUD: подсказка и уведомления. */
    public static void render(DrawContext ctx) {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || com.yompix.novograd.client.LifeHud.cinematic(mc)) return;
        var tr = mc.textRenderer;
        long now = System.currentTimeMillis();
        int W = ctx.getScaledWindowWidth();
        // ---- одно всплывающее окно: подсказка (слева сверху) или уведомление (справа)
        if (cur != null && now - curAt > curFor) cur = null;
        if (cur == null && !QUEUE.isEmpty() && (lastStart < 0 || now - lastStart >= GAP)) {
            cur = QUEUE.poll();
            curAt = now;
            lastStart = now;
            String full = cur.title() + " " + cur.sub();
            curFor = readFor(full);
            lastText = cur.title() + "|" + cur.sub();
            log((cur.hint() ? "подсказка: " : "уведомление: ") + full.trim());
        }
        if (cur != null) {
            long age = now - curAt;
            float a = MathHelper.clamp(Math.min(age / 300f, (curFor - age) / 500f), 0, 1);
            if (cur.hint()) box(ctx, tr, cur.title(), a);
            else note(ctx, tr, W, cur, a, age);
        } else if (now - ctxAt < 500 && !ctxText.isEmpty()) box(ctx, tr, ctxText, 1);
        // ---- строка над хотбаром
        if (stPending != null && now - stStart >= GAP && now - stAt >= stFor) {
            show(stPending, stPendColor, now);
            stPending = null;
        }
        long sa = now - stAt;
        if (!stText.isEmpty() && sa < stFor + 500) {
            float a = MathHelper.clamp(Math.min((now - stStart) / 250f, (stFor + 500 - sa) / 500f), 0, 1);
            int H = ctx.getScaledWindowHeight();
            // выше названия предмета в руке (ваниль рисует его на h−59) — как ванильная строка (h−68); не шире полосы
            // (рядом с видимым чатом — справа от него), длинная — в две строки
            int[] ln = lane(mc, W);
            int maxW = ln[1] - ln[0] - 10, cx = (ln[0] + ln[1]) / 2;
            var wl = tr.getTextHandler().wrapLines(stText, maxW, net.minecraft.text.Style.EMPTY);
            int n = Math.min(2, wl.size()), y = H - STATUS_Y, bw = 0;
            String[] ls = new String[n];
            for (int i = 0; i < n; i++) {
                ls[i] = wl.get(i).getString();
                if (i == 1 && wl.size() > 2) ls[i] = tr.trimToWidth(ls[i], maxW - tr.getWidth("…")) + "…";
                bw = Math.max(bw, tr.getWidth(ls[i]));
            }
            ctx.fill(cx - bw / 2 - 5, y - 3, cx + bw / 2 + 5, y + 10 * n, Ui.alpha(0x70000000, a));
            for (int i = 0; i < n; i++) ctx.drawText(tr, ls[i], cx - tr.getWidth(ls[i]) / 2, y + i * 10, Ui.alpha(stColor, a), true);
        }
    }

    private static void box(DrawContext ctx, TextRenderer tr, String show, float a) {
        int maxW = 170;
        var lines = tr.getTextHandler().wrapLines(show, maxW, net.minecraft.text.Style.EMPTY);
        int n = Math.min(3, lines.size()), bw = 0;
        for (int i = 0; i < n; i++) bw = Math.max(bw, tr.getWidth(lines.get(i).getString()));
        var m = ctx.getMatrices();
        m.push();
        m.translate(6, 6, 0);
        m.scale(0.85f, 0.85f, 1);
        ctx.fill(0, 0, bw + 10, n * 10 + 6, Ui.alpha(0xB0000000, a));
        ctx.fill(0, 0, 2, n * 10 + 6, Ui.alpha(Ui.GOLD, a));
        for (int i = 0; i < n; i++) ctx.drawText(tr, lines.get(i).getString(), 6, 4 + i * 10, Ui.alpha(0xFFE8E8E8, a), false);
        m.pop();
    }

    private static void note(DrawContext ctx, TextRenderer tr, int W, Pop nt, float k, long age) {
        String t = nt.title() + (nt.count() > 1 ? "  ×" + nt.count() : ""), s = nt.sub();
        // не шире 260 и не шире экрана (масштаб интерфейса 4 на 720p — экран 320 пикселей): длинное — обрезать «…»
        int maxW = Math.min(260, W - 12) - 14;
        if (tr.getWidth(t) > maxW) t = tr.trimToWidth(t, maxW - tr.getWidth("…")) + "…";
        if (tr.getWidth(s) * 0.85f > maxW) s = tr.trimToWidth(s, (int) (maxW / 0.85f) - tr.getWidth("…")) + "…";
        int bw = Math.max(tr.getWidth(t), (int) (tr.getWidth(s) * 0.85f)) + 14;
        // под правым блоком HUD (деньги, время, оружие), а не поверх него
        int x = W - 6 - (int) (bw * Ui.easeOut(Math.min(1, age / 300f))), y = Math.max(44, com.yompix.novograd.client.LifeHud.rightBottom + 6);
        int h = s.isEmpty() ? 13 : 22;
        ctx.fill(x, y, x + bw, y + h, Ui.alpha(0xC0000000, k));
        ctx.fill(x, y, x + 2, y + h, Ui.alpha(nt.color() | 0xFF000000, k));
        ctx.drawText(tr, t, x + 7, y + 3, Ui.alpha(nt.color() | 0xFF000000, k), false);
        if (!s.isEmpty()) Ui.small(ctx, tr, s, x + 7, y + 13, 0.85f, Ui.alpha(0xFFCFCFCF, k), 0);
    }
}
