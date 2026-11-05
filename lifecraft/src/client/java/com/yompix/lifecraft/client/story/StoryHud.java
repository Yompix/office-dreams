package com.yompix.lifecraft.client.story;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
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
    private static long bannerAt = -1;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        long now = System.currentTimeMillis();
        var o = ClientStory.objective;
        if (o.contains("hint") && !o.getString("hint").isEmpty() && ClientStory.titleAt < 0) {
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
            java.util.List<String[]> lines = new java.util.ArrayList<>();
            lines.add(new String[]{mission, "FFC107"});
            lines.add(new String[]{"▶ " + hint, "FFFFFF"});
            if (!whereLine.isEmpty()) lines.add(new String[]{whereLine, "7FE3FF"});
            if (!how.isEmpty()) lines.add(new String[]{how, "B0BEC5"});
            if (!extra.isEmpty()) lines.add(new String[]{extra, "9FE6FF"});
            int bw = 0;
            for (String[] l : lines) bw = Math.max(bw, tr.getWidth(l[0]));
            bw += 16;
            int x = w / 2 - bw / 2, y = 4, bh = lines.size() * 10 + 6;
            ctx.fill(x, y, x + bw, y + bh, 0xA0000000);
            ctx.fill(x, y, x + bw, y + 1, 0xFFFFC107);
            for (int i = 0; i < lines.size(); i++) {
                String[] l = lines.get(i);
                ctx.drawText(tr, l[0], w / 2 - tr.getWidth(l[0]) / 2, y + 4 + i * 10, 0xFF000000 | Integer.parseInt(l[1], 16), true);
            }
            // стрелка-компас: куда повернуть
            if (t != null && mc.player != null) {
                double dx = t.x - mc.player.getX(), dz = t.z - mc.player.getZ();
                float want = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float rel = MathHelper.wrapDegrees(want - mc.player.getYaw());
                int ax = w / 2, ay = y + bh + 16;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(ax, ay, 0);
                ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rel));
                int col = Math.abs(rel) < 20 ? 0xFF66FF88 : 0xFFFFC107;
                for (int i = 0; i < 7; i++) ctx.fill(-i, -9 + i * 2, i + 1, -7 + i * 2, col);
                ctx.fill(-2, 4, 3, 10, col);
                ctx.getMatrices().pop();
                if (Math.abs(rel) > 100) {
                    String back = "развернись";
                    ctx.drawText(tr, back, ax - tr.getWidth(back) / 2, ay + 13, 0xFFFFC107, true);
                }
            }
            // баннер «НОВАЯ ЦЕЛЬ»
            float bt = (now - bannerAt) / 1000f;
            if (bannerAt > 0 && bt < 3.2f) {
                float a = bt < 0.3f ? bt / 0.3f : bt > 2.6f ? (3.2f - bt) / 0.6f : 1;
                int al = (int) (a * 255) << 24;
                ctx.fill(0, h / 3 - 18, w, h / 3 + 26, (int) (a * 150) << 24);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f, h / 3f - 12, 0);
                ctx.getMatrices().scale(1.6f, 1.6f, 1);
                ctx.drawText(tr, "НОВАЯ ЦЕЛЬ", -tr.getWidth("НОВАЯ ЦЕЛЬ") / 2, 0, al | 0xFFC107, true);
                ctx.getMatrices().pop();
                ctx.drawText(tr, hint, w / 2 - tr.getWidth(hint) / 2, h / 3 + 4, al | 0xFFFFFF, true);
                if (!whereLine.isEmpty()) ctx.drawText(tr, whereLine, w / 2 - tr.getWidth(whereLine) / 2, h / 3 + 15, al | 0x7FE3FF, true);
            }
        }
        // заставка главы / «прошло три года»
        if (ClientStory.titleAt > 0) {
            float t = (now - ClientStory.titleAt) / 1000f;
            if (t > 7f) ClientStory.titleAt = -1;
            else {
                float alpha = t < 1 ? t : t > 6 ? 7 - t : 1;
                ctx.fill(0, 0, w, h, (int) (alpha * 230) << 24);
                int a = (int) (alpha * 255) << 24;
                float scale = 3f + Math.min(t, 1) * 0.4f;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f, h / 2f - 22, 0);
                ctx.getMatrices().scale(scale, scale, 1);
                ctx.drawText(tr, ClientStory.title, -tr.getWidth(ClientStory.title) / 2, 0, a | 0xFFFFFF, true);
                ctx.getMatrices().pop();
                float sub = MathHelper.clamp(t - 0.8f, 0, 1) * alpha;
                if (sub > 0.05f) {
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(w / 2f, h / 2f + 14, 0);
                    ctx.getMatrices().scale(1.6f, 1.6f, 1);
                    ctx.drawText(tr, ClientStory.subtitle, -tr.getWidth(ClientStory.subtitle) / 2, 0, (int) (sub * 255) << 24 | 0xFFC107, true);
                    ctx.getMatrices().pop();
                }
                int line = (int) (Math.min(t, 1.2f) / 1.2f * 120);
                ctx.fill(w / 2 - line, h / 2 + 6, w / 2 + line, h / 2 + 7, a | 0xFFC107);
            }
        }
        // «МИССИЯ ВЫПОЛНЕНА» в стиле GTA
        if (ClientStory.missionDoneAt > 0) {
            float t = (now - ClientStory.missionDoneAt) / 1000f;
            if (t > 4.5f) ClientStory.missionDoneAt = -1;
            else {
                float alpha = t < 0.3f ? t / 0.3f : t > 3.8f ? (4.5f - t) / 0.7f : 1;
                int a = (int) (alpha * 255) << 24;
                float s = 2.6f + (t < 0.3f ? (0.3f - t) * 3 : 0);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f, h * 0.32f, 0);
                ctx.getMatrices().scale(s, s, 1);
                String txt = "МИССИЯ ВЫПОЛНЕНА";
                ctx.drawText(tr, txt, -tr.getWidth(txt) / 2, 0, a | 0xFFC107, true);
                ctx.getMatrices().pop();
                String sub = ClientStory.missionDone + "   +$15";
                ctx.drawText(tr, sub, w / 2 - tr.getWidth(sub) / 2, (int) (h * 0.32f) + 30, a | 0xFFFFFF, true);
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
        // награда: плашка выезжает справа
        if (ClientStory.achAt > 0) {
            float t = (now - ClientStory.achAt) / 1000f;
            if (t > 5f) ClientStory.achAt = -1;
            else {
                float k = t < 0.4f ? t / 0.4f : t > 4.4f ? (5f - t) / 0.6f : 1;
                int bw = Math.max(tr.getWidth(ClientStory.achDesc), tr.getWidth(ClientStory.achName)) + 34;
                int bx = w - (int) (bw * k) - 4, by = h / 2 - 50;
                ctx.fill(bx, by, bx + bw, by + 28, 0xE0201A0A);
                ctx.fill(bx, by, bx + 2, by + 28, 0xFFFFC107);
                ctx.drawText(tr, "★", bx + 8, by + 10, 0xFFFFC107, false);
                ctx.drawText(tr, "Награда: " + ClientStory.achName, bx + 22, by + 5, 0xFFFFC107, true);
                ctx.drawText(tr, ClientStory.achDesc, bx + 22, by + 16, 0xFFDDDDDD, false);
            }
        }
        if (ClientStory.countdownAt > 0) {
            float t = (now - ClientStory.countdownAt) / 1000f;
            if (t > 1f) ClientStory.countdownAt = -1;
            else {
                float s = 5 - t * 2;
                int a = (int) ((1 - t) * 255) << 24;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(w / 2f, h / 2f - 20, 0);
                ctx.getMatrices().scale(s, s, 1);
                ctx.drawText(tr, ClientStory.countdown, -tr.getWidth(ClientStory.countdown) / 2, 0, a | 0xFFFFFF, true);
                ctx.getMatrices().pop();
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
