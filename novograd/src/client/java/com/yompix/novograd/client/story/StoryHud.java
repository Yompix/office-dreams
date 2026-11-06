package com.yompix.novograd.client.story;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Цель миссии сверху экрана, заставки глав, «МИССИЯ ВЫПОЛНЕНА», обратный отсчёт и световой столб над целью. */
public final class StoryHud {
    private StoryHud() {}

    private static String lastHint = "";
    private static String gtaTitle = "", gtaSub = "";
    private static long gtaAt = -1;

    /** «ЗАДЕРЖАН» / «ТЫ ПОГИБ» — большая надпись на весь экран. */
    public static void gtaTitle(String t, String sub) {
        gtaTitle = t;
        gtaSub = sub;
        gtaAt = System.currentTimeMillis();
    }

    private static long bannerAt = -1;

    private static float scale() {
        return Math.max(0.5f, Math.min(2f, com.yompix.novograd.client.NovogradConfig.get().hudScale));
    }

    /** Строки, разбитые по ширине. */
    private static java.util.List<String> wrapStr(TextRenderer tr, String s, int w) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (var l : tr.getTextHandler().wrapLines(s, w, net.minecraft.text.Style.EMPTY)) out.add(l.getString());
        return out;
    }

    /** Компас-лента сверху по центру: стороны света и метка цели. */
    private static void compass(DrawContext ctx, TextRenderer tr, int w, Vec3d t, MinecraftClient mc) {
        int cw = 150, cx = w / 2, y = 4;
        float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
        ctx.fillGradient(cx - cw / 2, y, cx + cw / 2, y + 13, 0x900B0812, 0x600B0812);
        ctx.fill(cx - cw / 2, y + 13, cx + cw / 2, y + 14, 0x60FFFFFF);
        ctx.enableScissor(cx - cw / 2, y, cx + cw / 2, y + 30);
        String[] names = {"Ю", "ЮЗ", "З", "СЗ", "С", "СВ", "В", "ЮВ"};
        float pxPerDeg = cw / 140f;
        for (int i = 0; i < 16; i++) {
            float deg = i * 22.5f;
            float rel = MathHelper.wrapDegrees(deg - yaw);
            if (Math.abs(rel) > 75) continue;
            int x = (int) (cx + rel * pxPerDeg);
            if (i % 2 == 0) {
                String n = names[i / 2];
                boolean main = n.length() == 1;
                ctx.drawText(tr, n, x - tr.getWidth(n) / 2, y + 3, main ? (n.equals("С") ? 0xFFFF5A6E : 0xFFFFFFFF) : 0xFFA9A3BC, true);
            } else ctx.fill(x, y + 8, x + 1, y + 12, 0x80FFFFFF);
        }
        ctx.disableScissor();
        ctx.fill(cx, y + 12, cx + 1, y + 16, 0xFFFFFFFF);
        if (t != null) {
            double dx = t.x - mc.player.getX(), dz = t.z - mc.player.getZ();
            float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = MathHelper.wrapDegrees(want - mc.player.getYaw());
            boolean edge = Math.abs(rel) > 68;
            int x = (int) (cx + MathHelper.clamp(rel, -68, 68) * pxPerDeg);
            int col = Math.abs(rel) < 12 ? 0xFF66FF88 : Ui.GOLD;
            // ромб-метка
            for (int i = 0; i < 4; i++) ctx.fill(x - i, y + 15 + i, x + i + 1, y + 16 + i, col);
            for (int i = 0; i < 3; i++) ctx.fill(x - 2 + i, y + 19 + i, x + 3 - i, y + 20 + i, col);
            String dist = (int) Math.sqrt(t.squaredDistanceTo(mc.player.getPos())) + " м";
            Ui.small(ctx, tr, dist, x, y + 24, 0.75f, 0xFFFFFFFF, 1);
            if (edge) Ui.big(ctx, tr, rel < 0 ? "‹" : "›", rel < 0 ? cx - cw / 2 - 6 : cx + cw / 2 + 6, y + 2, 1.3f, col, 1);
            if (Math.abs(rel) > 100) Ui.small(ctx, tr, "развернись", cx, y + 33, 0.8f, Ui.GOLD, 1);
        }
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        long now = System.currentTimeMillis();
        boolean hud = mc.player != null && !mc.options.hudHidden && !mc.getDebugHud().shouldShowDebugHud() && !com.yompix.novograd.client.LifeHud.cinematic(mc);
        if (hud && com.yompix.novograd.client.NovogradClient.wantedStars >= 1 && (now / 400) % 2 == 0) {
            ctx.fill(0, 0, w, 2, 0x88FF2A2A);
            ctx.fill(0, 2, w, 4, 0x882A6AFF);
        }
        if (gtaAt > 0) {
            float gt = (now - gtaAt) / 1000f;
            if (gt > 5) gtaAt = -1;
            else {
                // «ЗАДЕРЖАН» / «ТЫ ПОГИБ»: тёмный кадр, полоса через экран, крупные буквы с обводкой
                float a = gt < 0.4f ? gt / 0.4f : gt > 4.2f ? (5 - gt) / 0.8f : 1;
                boolean dead = gtaTitle.startsWith("ТЫ");
                ctx.fill(0, 0, w, h, (int) (a * 140) << 24 | (dead ? 0x200000 : 0x000818));
                int band = (int) (34 * Ui.easeOut(gt / 0.35f));
                ctx.fillGradient(0, h / 2 - 20 - band / 2 - 4, w, h / 2 - 20 + band / 2 + 18, (int) (a * 200) << 24, (int) (a * 200) << 24);
                float s = 3.4f + Math.max(0, 0.3f - gt) * 4;
                Ui.big(ctx, tr, gtaTitle, w / 2f, h / 2f - 22, s, Ui.alpha(dead ? 0xFFE53935 : 0xFF4FA8FF, a), 1);
                Ui.big(ctx, tr, gtaSub, w / 2f, h / 2f + 14, 1f, Ui.alpha(0xFFFFFFFF, a), 1);
            }
        }
        var o = ClientStory.objective;
        if (hud && o.contains("hint") && !o.getString("hint").isEmpty() && ClientStory.titleAt < 0) {
            String mission = o.getString("mission"), hint = o.getString("hint"), extra = o.getString("extra");
            String where = o.getString("where"), how = o.getString("how");
            // новая цель — заметный баннер и звук
            if (!hint.equals(lastHint)) {
                lastHint = hint;
                bannerAt = now;
                if (mc.player != null) mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.7f, 1.2f);
            }
            Vec3d t = ClientStory.target();
            String dist = "";
            if (t != null && mc.player != null) dist = (int) Math.sqrt(t.squaredDistanceTo(mc.player.getPos())) + " м";
            String whereLine = where.isEmpty() ? (dist.isEmpty() ? "" : "📍 " + dist) : "📍 " + where + (dist.isEmpty() ? "" : " · " + dist);
            float sc = scale();
            var m = ctx.getMatrices();
            // компас сверху
            if (mc.player != null) {
                m.push();
                m.translate(w / 2f, 0, 0);
                m.scale(sc, sc, 1);
                m.translate(-w / 2f, 0, 0);
                compass(ctx, tr, w, t, mc);
                m.pop();
            }
            // цель — внизу по центру над хотбаром, как субтитры
            int maxW = Math.max(170, Math.min(300, (int) ((w - 2 * 156) / sc)));
            java.util.List<String[]> lines = new java.util.ArrayList<>();
            for (String l : wrapStr(tr, hint, maxW)) lines.add(new String[]{l, "FFFFFF"});
            if (!whereLine.isEmpty()) for (String l : wrapStr(tr, whereLine, maxW)) lines.add(new String[]{l, "7FE3FF"});
            if (!how.isEmpty()) for (String l : wrapStr(tr, how, maxW)) lines.add(new String[]{l, "C4BEDA"});
            if (!extra.isEmpty()) for (String l : wrapStr(tr, extra, maxW)) lines.add(new String[]{l, "9FE6FF"});
            int lh = 10, total = lines.size() * lh + 12;
            m.push();
            m.translate(w / 2f, h - 74, 0);
            m.scale(sc, sc, 1);
            int top = -total;
            int bw = 0;
            for (String[] l : lines) bw = Math.max(bw, tr.getWidth(l[0]));
            bw = Math.max(bw, tr.getWidth(mission.toUpperCase()) + 16) + 24;
            ctx.fillGradient(-bw / 2, top - 2, bw / 2, 4, 0x00000000, 0x90000000);
            // ярлык миссии
            String mt = mission.toUpperCase();
            int mw = tr.getWidth(mt) + 14;
            Ui.slant(ctx, -mw / 2, top - 4, mw, 11, 3, 0xE00B0812);
            ctx.fill(-mw / 2 + 2, top + 6, mw / 2 + 1, top + 7, Ui.GOLD);
            ctx.drawText(tr, mt, -tr.getWidth(mt) / 2 + 1, top - 2, Ui.GOLD, false);
            for (int i = 0; i < lines.size(); i++) {
                String[] l = lines.get(i);
                Ui.outlined(ctx, tr, l[0], -tr.getWidth(l[0]) / 2, top + 11 + i * lh, 0xFF000000 | Integer.parseInt(l[1], 16));
            }
            m.pop();
            // баннер «НОВАЯ ЦЕЛЬ»: косая розовая плашка проезжает через экран
            float bt = (now - bannerAt) / 1000f;
            if (bannerAt > 0 && bt < 3.2f) {
                float a = bt < 0.3f ? bt / 0.3f : bt > 2.6f ? (3.2f - bt) / 0.6f : 1;
                int by = h / 3 - 16;
                int slide = (int) ((1 - Ui.easeOut(bt / 0.4f)) * w);
                int hw = Math.max(tr.getWidth(hint), (int) (tr.getWidth("НОВАЯ ЦЕЛЬ") * 2f)) + 40;
                Ui.slant(ctx, w / 2 - hw / 2 - slide, by, hw, 22, 8, Ui.alpha(0xE8FF2E88, a));
                Ui.slant(ctx, w / 2 - hw / 2 - slide, by + 22, hw, 24, 8, Ui.alpha(0xD00B0812, a));
                Ui.big(ctx, tr, "НОВАЯ ЦЕЛЬ", w / 2f - slide + 4, by + 4, 2f, Ui.alpha(0xFFFFFFFF, a), 1);
                ctx.drawText(tr, hint, w / 2 - tr.getWidth(hint) / 2 - slide, by + 26, Ui.alpha(0xFFFFFFFF, a), true);
                if (!whereLine.isEmpty()) ctx.drawText(tr, whereLine, w / 2 - tr.getWidth(whereLine) / 2 - slide, by + 36, Ui.alpha(0xFF7FE3FF, a), true);
            }
        }
        // заставка главы / «прошло три года»
        if (ClientStory.titleAt > 0) {
            float t = (now - ClientStory.titleAt) / 1000f;
            if (t > 7f) ClientStory.titleAt = -1;
            else {
                float alpha = t < 1 ? t : t > 6 ? 7 - t : 1;
                ctx.fill(0, 0, w, h, (int) (alpha * 220) << 24 | 0x05020C);
                Ui.letterbox(ctx, w, h, alpha);
                float scale = 3f + Math.min(t, 1) * 0.4f;
                Ui.big(ctx, tr, ClientStory.title.toUpperCase(), w / 2f, h / 2f - 24, scale, Ui.alpha(0xFFFFFFFF, alpha), 1);
                float sub = MathHelper.clamp(t - 0.8f, 0, 1) * alpha;
                if (sub > 0.05f) {
                    int sw = (int) (tr.getWidth(ClientStory.subtitle) * 1.4f) + 30;
                    int slide = (int) ((1 - Ui.easeOut(MathHelper.clamp(t - 0.8f, 0, 1) / 0.6f)) * 60);
                    Ui.slant(ctx, w / 2 - sw / 2 + slide, h / 2 + 10, sw, 18, 6, Ui.alpha(0xE0FFC428, sub));
                    Ui.small(ctx, tr, ClientStory.subtitle, w / 2f + slide + 3, h / 2f + 14, 1.4f, Ui.alpha(0xFF07050C, sub), 1);
                }
                Ui.grain(ctx, w, h, 0.3f * alpha);
            }
        }
        // «МИССИЯ ПРОЙДЕНА»: золотые буквы, под ними награда и название
        if (ClientStory.missionDoneAt > 0) {
            float t = (now - ClientStory.missionDoneAt) / 1000f;
            if (t > 4.5f) ClientStory.missionDoneAt = -1;
            else {
                float alpha = t < 0.3f ? t / 0.3f : t > 3.8f ? (4.5f - t) / 0.7f : 1;
                float pop = 1 + (t < 0.25f ? (0.25f - t) * 2.4f : 0);
                float tw = Math.min(w * 0.62f, 300) * pop, th = tw * Ui.PASSED_SIZE[1] / Ui.PASSED_SIZE[0];
                float cy = h * 0.3f;
                ctx.fillGradient(0, (int) (cy - th * 0.7f), w, (int) (cy + th * 0.5f + 34), 0, 0);
                ctx.fill(0, (int) (cy - th * 0.65f), w, (int) (cy + th * 0.5f + 32), (int) (alpha * 110) << 24);
                Ui.tex(ctx, Ui.PASSED, w / 2f - tw / 2, cy - th / 2, tw, th, Ui.PASSED_SIZE[0], Ui.PASSED_SIZE[1], alpha);
                float ra = MathHelper.clamp((t - 0.35f) / 0.3f, 0, 1) * alpha;
                Ui.big(ctx, tr, "+$15", w / 2f, cy + th / 2 + 4, 2f, Ui.alpha(Ui.MONEY, ra), 1);
                Ui.big(ctx, tr, ClientStory.missionDone, w / 2f, cy + th / 2 + 22, 1f, Ui.alpha(0xFFFFFFFF, ra), 1);
            }
        }
        // мигалка полиции при штрафе
        if (ClientStory.sirenAt > 0) {
            float t = (now - ClientStory.sirenAt) / 1000f;
            if (t > 2.4f) ClientStory.sirenAt = -1;
            else {
                int a = (int) (70 * (1 - t / 2.4f)) << 24;
                boolean red = (now / 180) % 2 == 0;
                ctx.fillGradient(0, 0, w / 2, h, a | (red ? 0xFF2020 : 0x2040FF), 0);
                ctx.fillGradient(w / 2, 0, w, h, a | (red ? 0x2040FF : 0xFF2020), 0);
            }
        }
        // награда: косая плашка выезжает справа
        if (ClientStory.achAt > 0) {
            float t = (now - ClientStory.achAt) / 1000f;
            if (t > 5f) ClientStory.achAt = -1;
            else {
                float k = t < 0.4f ? Ui.easeOut(t / 0.4f) : t > 4.4f ? (5f - t) / 0.6f : 1;
                int bw = Math.max(tr.getWidth(ClientStory.achDesc), tr.getWidth(ClientStory.achName) + 50) + 40;
                int bx = w - (int) (bw * k), by = h / 2 - 50;
                Ui.slant(ctx, bx, by, bw + 10, 30, 8, 0xE80B0812);
                ctx.fill(bx + 8, by + 29, bx + bw + 10, by + 30, Ui.GOLD);
                Ui.tint(ctx, Ui.STAR, bx + 10, by + 7, 16, 16, 32, 32, 1f, 0.84f, 0.3f, 1);
                ctx.drawText(tr, "НАГРАДА · " + ClientStory.achName, bx + 32, by + 6, Ui.GOLD, true);
                ctx.drawText(tr, ClientStory.achDesc, bx + 32, by + 17, 0xFFDDDDDD, false);
            }
        }
        if (ClientStory.countdownAt > 0) {
            float t = (now - ClientStory.countdownAt) / 1000f;
            if (t > 1f) ClientStory.countdownAt = -1;
            else {
                float s = 5 - t * 2;
                Ui.big(ctx, tr, ClientStory.countdown, w / 2f, h / 2f - 20, s, Ui.alpha(ClientStory.countdown.matches("\\d+") ? 0xFFFFFFFF : Ui.MONEY, 1 - t), 1);
            }
        }
    }

    /** Золотой столб света над целью, видно издалека. */
    public static void renderWorld(WorldRenderContext ctx) {
        Vec3d t = ClientStory.target();
        if (t == null || ctx.consumers() == null || ClientStory.titleAt > 0) return;
        Vec3d cam = ctx.camera().getPos();
        MatrixStack m = ctx.matrixStack() != null ? ctx.matrixStack() : new MatrixStack();
        m.push();
        m.translate(t.x - cam.x, t.y - cam.y, t.z - cam.z);
        Matrix4f mat = m.peek().getPositionMatrix();
        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLightning());
        float pulse = 0.75f + 0.25f * MathHelper.sin((System.currentTimeMillis() % 100000) / 300f);
        int alpha = (int) (110 * pulse);
        float r = 0.35f, top = 60;
        float[][] quads = {{-r, 0, r, 0}, {0, -r, 0, r}};
        for (float[] q : quads) {
            vc.vertex(mat, q[0], 0, q[1]).color(255, 200, 40, alpha);
            vc.vertex(mat, q[2], 0, q[3]).color(255, 200, 40, alpha);
            vc.vertex(mat, q[2], top, q[3]).color(255, 220, 80, 0);
            vc.vertex(mat, q[0], top, q[1]).color(255, 220, 80, 0);
        }
        // кольцо на земле
        int seg = 24;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float r0 = 1.2f, r1 = 1.5f;
            vc.vertex(mat, (float) Math.cos(a0) * r0, 0.05f, (float) Math.sin(a0) * r0).color(255, 200, 40, alpha);
            vc.vertex(mat, (float) Math.cos(a1) * r0, 0.05f, (float) Math.sin(a1) * r0).color(255, 200, 40, alpha);
            vc.vertex(mat, (float) Math.cos(a1) * r1, 0.05f, (float) Math.sin(a1) * r1).color(255, 200, 40, alpha);
            vc.vertex(mat, (float) Math.cos(a0) * r1, 0.05f, (float) Math.sin(a0) * r1).color(255, 200, 40, alpha);
        }
        m.pop();
    }
}
