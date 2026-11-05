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

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        long now = System.currentTimeMillis();
        var o = ClientStory.objective;
        if (o.contains("hint") && !o.getString("hint").isEmpty() && ClientStory.titleAt < 0) {
            String mission = o.getString("mission"), hint = o.getString("hint"), extra = o.getString("extra");
            Vec3d t = ClientStory.target();
            if (t != null && mc.player != null) {
                int dist = (int) Math.sqrt(t.squaredDistanceTo(mc.player.getPos()));
                hint += "  · " + dist + " м";
            }
            int bw = Math.max(tr.getWidth(hint), tr.getWidth(mission)) + 16;
            int x = w / 2 - bw / 2, y = 4;
            ctx.fill(x, y, x + bw, y + (extra.isEmpty() ? 26 : 36), 0x99000000);
            ctx.fill(x, y, x + bw, y + 1, 0xFFFFC107);
            ctx.drawText(tr, mission, w / 2 - tr.getWidth(mission) / 2, y + 4, 0xFFFFC107, true);
            ctx.drawText(tr, hint, w / 2 - tr.getWidth(hint) / 2, y + 15, 0xFFFFFFFF, true);
            if (!extra.isEmpty()) ctx.drawText(tr, extra, w / 2 - tr.getWidth(extra) / 2, y + 25, 0xFF9FE6FF, true);
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
