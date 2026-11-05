package com.yompix.novograd.client.story;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.screen.LifeScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Кат-сцена: камера плавно летит между точками (как облёт в кино), внизу субтитры под голос рассказчика.
 * Работает на невидимом «операторе», игрок в это время стоит в режиме наблюдателя.
 */
public class CutsceneScreen extends LifeScreen {
    private record Shot(double[] from, double[] to, float sec) {}

    private record Sub(String name, int color, String text, String voice) {}

    private final List<Shot> shots = new ArrayList<>();
    private final List<Sub> subs = new ArrayList<>();
    private final float total;
    private ArmorStandEntity cam;
    private boolean hudWas, done;
    private int subIndex = -1;
    private long subAt, startAt = System.currentTimeMillis();

    private float elapsed() {
        return (System.currentTimeMillis() - startAt) / 1000f;
    }

    public CutsceneScreen(NbtCompound n) {
        super("Кат-сцена");
        float t = 0;
        for (NbtElement e : n.getList("shots", NbtElement.COMPOUND_TYPE)) {
            NbtCompound s = (NbtCompound) e;
            Shot sh = new Shot(arr(s.getList("from", NbtElement.DOUBLE_TYPE)), arr(s.getList("to", NbtElement.DOUBLE_TYPE)), s.getFloat("sec"));
            shots.add(sh);
            t += sh.sec();
        }
        for (NbtElement e : n.getList("lines", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            subs.add(new Sub(c.getString("name"), c.getInt("color"), c.getString("text"), c.getString("voice")));
        }
        total = Math.max(t, 2);
    }

    private static double[] arr(NbtList l) {
        double[] v = new double[5];
        for (int i = 0; i < Math.min(5, l.size()); i++) v[i] = l.getDouble(i);
        return v;
    }

    @Override
    protected void init() {
        if (cam == null && mc().world != null) {
            subIndex = -1;
            startAt = System.currentTimeMillis();
            cam = new ArmorStandEntity(EntityType.ARMOR_STAND, mc().world);
            cam.setInvisible(true);
            hudWas = mc().options.hudHidden;
            mc().options.hudHidden = true;
            place(0, 0);
            mc().setCameraEntity(cam);
        }
    }

    private void place(float time, float delta) {
        float t = time;
        for (Shot s : shots) {
            if (t <= s.sec() || s == shots.get(shots.size() - 1)) {
                float k = MathHelper.clamp(t / s.sec(), 0, 1);
                k = k * k * (3 - 2 * k); // плавный старт и остановка
                double x = MathHelper.lerp(k, s.from()[0], s.to()[0]), y = MathHelper.lerp(k, s.from()[1], s.to()[1]), z = MathHelper.lerp(k, s.from()[2], s.to()[2]);
                float yaw = (float) (s.from()[3] + MathHelper.wrapDegrees(s.to()[3] - s.from()[3]) * k);
                float pitch = (float) MathHelper.lerp(k, s.from()[4], s.to()[4]);
                cam.prevX = cam.getX();
                cam.prevY = cam.getY();
                cam.prevZ = cam.getZ();
                cam.lastRenderX = cam.getX();
                cam.lastRenderY = cam.getY();
                cam.lastRenderZ = cam.getZ();
                cam.prevYaw = cam.getYaw();
                cam.prevPitch = cam.getPitch();
                cam.setPosition(x, y - cam.getStandingEyeHeight(), z);
                cam.setYaw(yaw);
                cam.setPitch(pitch);
                cam.setHeadYaw(yaw);
                cam.prevHeadYaw = cam.prevYaw;
                return;
            }
            t -= s.sec();
        }
    }

    @Override
    public void tick() {
        if (cam == null) return;
        float time = elapsed();
        place(Math.min(time, total), 0);
        // субтитры идут по очереди: следующая — когда голос закончился
        if (subIndex < 0 || (!Voice.playing() && System.currentTimeMillis() - subAt > 1200)) {
            if (subIndex + 1 < subs.size()) {
                subIndex++;
                subAt = System.currentTimeMillis();
                Voice.play(subs.get(subIndex).voice());
            } else if (time >= total) {
                end();
            }
        }
    }

    private void end() {
        if (done) return;
        done = true;
        Voice.stop();
        mc().options.hudHidden = hudWas;
        if (mc().player != null) mc().setCameraEntity(mc().player);
        NovogradClient.send("cutscene_done");
        super.close();
    }

    @Override
    public void close() {
    }

    /** Если окно закрыла чужая загрузка — вернуть камеру игроку и показать кат-сцену позже. */
    @Override
    public void removed() {
        if (done || cam == null) return;
        mc().options.hudHidden = hudWas;
        if (mc().player != null) mc().setCameraEntity(mc().player);
        Voice.stop();
        cam = null;
        com.yompix.novograd.client.NovogradClient.requeue(this);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ESCAPE) {
            end();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void background(DrawContext ctx) {
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        if (cam != null) place(Math.min(elapsed(), total), delta);
        int bar = (int) (height * 0.12f);
        ctx.fill(0, 0, width, bar, 0xFF000000);
        ctx.fill(0, height - bar, width, height, 0xFF000000);
        float fade = Math.min(1, elapsed() / 1.2f);
        if (fade < 1) ctx.fill(0, 0, width, height, (int) ((1 - fade) * 255) << 24);
        if (subIndex >= 0 && subIndex < subs.size()) {
            Sub s = subs.get(subIndex);
            var lines = textRenderer.wrapLines(Text.literal(s.text()), Math.min(width - 40, 380));
            int y = height - bar + (bar - lines.size() * 11) / 2;
            for (int i = 0; i < lines.size(); i++) {
                int w = textRenderer.getWidth(lines.get(i));
                ctx.drawText(textRenderer, lines.get(i), width / 2 - w / 2, y + i * 11, 0xFFF0F0F0, true);
            }
        }
        String skip = "Пробел — пропустить";
        ctx.drawText(textRenderer, skip, width - textRenderer.getWidth(skip) - 6, 6, 0x66FFFFFF, false);
    }
}
