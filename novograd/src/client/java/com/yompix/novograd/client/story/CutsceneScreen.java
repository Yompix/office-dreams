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
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Кат-сцена: камера плавно летит между точками (как облёт в кино), внизу субтитры под голос рассказчика.
 * Работает на невидимом «операторе», игрок в это время стоит в режиме наблюдателя.
 */
public class CutsceneScreen extends LifeScreen {
    private record Shot(double[] from, double[] to, float sec, String track, String follow, double[] off, float shake) {}

    /** Актёры кат-сцены: имя в сценарии → id сущности на клиенте (сервер присылает «cut_actor»). */
    public static final java.util.Map<String, Integer> ACTORS = new java.util.HashMap<>();
    private double camX = Double.NaN, camY, camZ;
    private float camYaw, camPitch;
    private long lastFrame;

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
            double[] off = s.contains("off") ? arr(s.getList("off", NbtElement.DOUBLE_TYPE)) : new double[]{7, 3, 0, 0, 0};
            Shot sh = new Shot(s.contains("from") ? arr(s.getList("from", NbtElement.DOUBLE_TYPE)) : null,
                    s.contains("to") ? arr(s.getList("to", NbtElement.DOUBLE_TYPE)) : null, s.getFloat("sec"),
                    s.getString("track"), s.getString("follow"), off, s.getFloat("shake"));
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

    @org.jetbrains.annotations.Nullable
    private net.minecraft.entity.Entity actor(String name) {
        Integer id = name.isEmpty() ? null : ACTORS.get(name);
        return id == null || mc().world == null ? null : mc().world.getEntityById(id);
    }

    private void place(float time, float delta) {
        float t = time;
        for (Shot s : shots) {
            if (t <= s.sec() || s == shots.get(shots.size() - 1)) {
                float k = MathHelper.clamp(t / s.sec(), 0, 1);
                k = k * k * (3 - 2 * k); // плавный старт и остановка
                double[] f = s.from(), to = s.to() != null ? s.to() : s.from();
                var fol = actor(s.follow());
                var trk = actor(s.track());
                double x, y, z;
                float yaw, pitch;
                if (fol != null) {
                    // камера «за плечом» / за машиной: держится сзади и сбоку, мягко догоняет
                    Vec3d at = fol.getLerpedPos(delta);
                    float fy = fol instanceof net.minecraft.entity.LivingEntity le ? MathHelper.lerpAngleDegrees(delta, le.prevBodyYaw, le.bodyYaw) : fol.getYaw(delta);
                    double rad = Math.toRadians(fy), back = s.off()[0], up = s.off()[1], side = s.off()[2];
                    double fx = -Math.sin(rad), fz = Math.cos(rad); // вперёд по взгляду
                    x = at.x - fx * back + fz * side;
                    y = at.y + up;
                    z = at.z - fz * back - fx * side;
                    Vec3d look = at.add(0, fol.getHeight() * 0.8, 0);
                    yaw = yawTo(x, z, look);
                    pitch = pitchTo(x, y, z, look);
                } else if (f != null) {
                    x = MathHelper.lerp(k, f[0], to[0]);
                    y = MathHelper.lerp(k, f[1], to[1]);
                    z = MathHelper.lerp(k, f[2], to[2]);
                    yaw = (float) (f[3] + MathHelper.wrapDegrees(to[3] - f[3]) * k);
                    pitch = (float) MathHelper.lerp(k, f[4], to[4]);
                } else return;
                if (trk != null) {
                    Vec3d look = trk.getLerpedPos(delta).add(0, trk.getHeight() * 0.75, 0);
                    yaw = yawTo(x, z, look);
                    pitch = pitchTo(x, y, z, look);
                }
                // сглаживание: камера-оператор не дёргается, а «ведёт» героя
                long now = System.nanoTime();
                float dt = lastFrame == 0 ? 1 : Math.min(1, (now - lastFrame) / 1e9f * (fol != null ? 6f : trk != null ? 10f : 60f));
                lastFrame = now;
                boolean cut = Double.isNaN(camX) || Math.abs(camX - x) + Math.abs(camZ - z) > 30;
                if (cut || (fol == null && trk == null)) {
                    camX = x; camY = y; camZ = z; camYaw = yaw; camPitch = pitch;
                } else {
                    camX += (x - camX) * dt;
                    camY += (y - camY) * dt;
                    camZ += (z - camZ) * dt;
                    camYaw += MathHelper.wrapDegrees(yaw - camYaw) * Math.min(1, dt * 1.5f);
                    camPitch += (pitch - camPitch) * Math.min(1, dt * 1.5f);
                }
                float shake = s.shake();
                double sx = 0, sy = 0;
                if (shake > 0) {
                    double tt = System.currentTimeMillis() / 1000.0;
                    sx = (Math.sin(tt * 23) + Math.sin(tt * 37) * 0.5) * shake * 0.6;
                    sy = (Math.cos(tt * 29) + Math.sin(tt * 41) * 0.5) * shake * 0.4;
                }
                cam.prevX = cam.getX();
                cam.prevY = cam.getY();
                cam.prevZ = cam.getZ();
                cam.lastRenderX = cam.getX();
                cam.lastRenderY = cam.getY();
                cam.lastRenderZ = cam.getZ();
                cam.setPosition(camX, camY - cam.getStandingEyeHeight(), camZ);
                cam.setYaw(camYaw + (float) sx);
                cam.setPitch(camPitch + (float) sy);
                cam.prevYaw = cam.getYaw();
                cam.prevPitch = cam.getPitch();
                cam.setHeadYaw(cam.getYaw());
                cam.prevHeadYaw = cam.getYaw();
                return;
            }
            t -= s.sec();
        }
    }

    private static float yawTo(double x, double z, Vec3d look) {
        return (float) Math.toDegrees(Math.atan2(-(look.x - x), look.z - z));
    }

    private static float pitchTo(double x, double y, double z, Vec3d look) {
        double h = Math.sqrt((look.x - x) * (look.x - x) + (look.z - z) * (look.z - z));
        return (float) -Math.toDegrees(Math.atan2(look.y - y, h));
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
        ACTORS.clear();
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
