package com.yompix.novograd.client;

import com.yompix.novograd.client.screen.OutdoorScreen;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.gta.Outdoors;
import com.yompix.novograd.gta.Skills;
import com.yompix.novograd.world.Holidays;
import com.yompix.novograd.world.Island;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Клиент «За городом»: тосты «навык повышен» как в SA, таблица турнира рыбаков, время года (трава и листва — SeasonColorsMixin,
 * снег вместо дождя — SeasonSnowMixin, лёгкий снежок зимой), гирлянды на крышах в праздники, дичь на GeckoLib, лавки и приложения.
 */
public final class OutdoorClient {
    private OutdoorClient() {}

    /** 0 лето, 1 осень, 2 зима, 3 весна — читают миксины цвета травы и осадков. */
    public static volatile int SEASON;
    public static String holiday = "NONE";
    public static NbtCompound season = new NbtCompound(), state = new NbtCompound(), tour = new NbtCompound();
    private static long tourAt;

    private static String toastTitle = "", toastSub = "";
    private static int toastColor, toastPct = -1;
    private static long toastAt;

    public static void init() {
        EntityRendererRegistry.register(Outdoors.DEER, com.yompix.novograd.client.render.WildGeoRenderer::new);
        EntityRendererRegistry.register(Outdoors.BOAR, com.yompix.novograd.client.render.WildGeoRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(Holidays.GIFT, RenderLayer.getCutout());
        Skills.CLIENT = () -> NovogradClient.data;
        HudRenderCallback.EVENT.register((ctx, t) -> hud(ctx));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(OutdoorClient::lights);
        ClientTickEvents.END_CLIENT_TICK.register(OutdoorClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            setSeason(0, c);
            holiday = "NONE";
            tour = new NbtCompound();
        });
        OutdoorAutotest.init();
    }

    /** Пакеты сервера с префиксом out_; true — наш. */
    public static boolean receive(MinecraftClient mc, String type, NbtCompound n) {
        switch (type) {
            case "out_skill" -> {
                Skills.Skill s = Skills.Skill.of(n.getString("id"));
                if (s == null) return true;
                int pct = n.getInt("pct");
                toast("НАВЫК " + s.genitive.toUpperCase(Locale.ROOT) + " ПОВЫШЕН", Skills.rank(s, pct) + " · " + pct + "%", s.color, pct);
                if (mc.player != null) mc.player.playSound(com.yompix.novograd.ModContent.LEVEL_UP, 0.5f, 1.3f);
            }
            case "out_season" -> {
                int was = SEASON;
                season = n;
                String h = n.getString("holiday");
                int idx = switch (n.getString("season")) {
                    case "AUTUMN" -> 1;
                    case "WINTER" -> 2;
                    case "SPRING" -> 3;
                    default -> 0;
                };
                boolean firstPacket = !seasonKnown;
                seasonKnown = true;
                setSeason(idx, mc);
                if (!firstPacket && was != idx) toast("НАСТУПИЛА " + n.getString("title").toUpperCase(Locale.ROOT), "день " + n.getLong("day") + " · Новоград", 0xFF8EC5FF, -1);
                if (!h.equals(holiday) && !h.equals("NONE")) toast(n.getString("holidayTitle").toUpperCase(Locale.ROOT), "праздник в Новограде", h.equals("HALLOWEEN") ? 0xFFFF8A1E : 0xFFFF3B4A, -1);
                holiday = h;
                lightsAt = -1000;
            }
            case "out_state" -> state = n;
            case "out_open" -> NovogradClient.show(mc, new OutdoorScreen(n.getString("shop")));
            case "out_tour" -> {
                tour = n;
                tourAt = System.currentTimeMillis();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static boolean seasonKnown;

    private static void setSeason(int idx, MinecraftClient mc) {
        if (SEASON == idx) return;
        SEASON = idx;
        if (mc.worldRenderer != null && mc.world != null) mc.worldRenderer.reload(); // перекрасить траву и листву
    }

    public static void toast(String title, String sub, int color, int pct) {
        toastTitle = title;
        toastSub = sub;
        toastColor = color;
        toastPct = pct;
        toastAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ HUD
    private static void hud(DrawContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth();
        long age = System.currentTimeMillis() - toastAt;
        if (age < 4500 && !toastTitle.isEmpty()) {
            float a = MathHelper.clamp(Math.min(age / 250f, (4500 - age) / 600f), 0, 1);
            float slide = 1 - Ui.easeOut(MathHelper.clamp(age / 300f, 0, 1));
            int y = 86 - (int) (slide * 20); // ниже ленты новостей и компаса
            var tr = c.textRenderer;
            int tw = (int) (tr.getWidth(toastTitle) * 1.6f);
            int bw = Math.max(tw + 30, 170);
            ctx.fill(w / 2 - bw / 2, y - 4, w / 2 + bw / 2, y + 34, Ui.alpha(0xD0070510, a));
            ctx.fill(w / 2 - bw / 2, y - 4, w / 2 + bw / 2, y - 2, Ui.alpha(toastColor, a));
            Ui.big(ctx, tr, toastTitle, w / 2f, y + 2, 1.6f, Ui.alpha(0xFFFFFFFF, a), 1);
            Ui.small(ctx, tr, toastSub, w / 2f, y + 18, 0.85f, Ui.alpha(toastColor, a), 1);
            if (toastPct >= 0) {
                int bx = w / 2 - bw / 2 + 12, bb = bw - 24;
                ctx.fill(bx, y + 28, bx + bb, y + 31, Ui.alpha(0x60FFFFFF, a));
                float grow = Ui.easeOut(MathHelper.clamp((age - 300) / 900f, 0, 1));
                float from = Math.max(0, toastPct - 5) / 100f, to = toastPct / 100f;
                ctx.fill(bx, y + 28, bx + (int) (bb * (from + (to - from) * grow)), y + 31, Ui.alpha(toastColor, a));
            }
        }
        // турнир рыбаков: таблица справа, пока рядом с озером
        if (tour.getBoolean("on") && System.currentTimeMillis() - tourAt < 12000) {
            NbtList rows = tour.getList("rows", NbtElement.COMPOUND_TYPE);
            int x = w - 128, y = 60;
            ctx.fill(x - 4, y - 4, x + 122, y + 14 + Math.min(6, rows.size()) * 10, 0xB0070510);
            ctx.fill(x - 4, y - 4, x + 122, y - 2, 0xFF26C6DA);
            Ui.small(ctx, c.textRenderer, "🎣 ТУРНИР НА ЯСНОМ", x, y, 0.8f, 0xFF26C6DA, 0);
            for (int i = 0; i < Math.min(6, rows.size()); i++) {
                NbtCompound r = rows.getCompound(i);
                int col = r.getBoolean("me") ? Ui.GOLD : 0xFFE0E0E8;
                Ui.small(ctx, c.textRenderer, (i + 1) + ". " + r.getString("name"), x, y + 11 + i * 10, 0.75f, col, 0);
                Ui.small(ctx, c.textRenderer, kg(r.getInt("g")), x + 118, y + 11 + i * 10, 0.75f, col, 2);
            }
        }
    }

    public static String kg(int g) {
        return String.format(Locale.ROOT, "%.1f кг", g / 1000f).replace('.', ',');
    }

    // ------------------------------------------------------------------ снежок
    private static void tick(MinecraftClient c) {
        if (c.world == null || c.player == null || c.isPaused()) return;
        if (SEASON == 2 && !c.world.isRaining() && Island.urban(Island.zone((int) c.player.getX(), (int) c.player.getZ()))
                || SEASON == 2 && !c.world.isRaining() && c.player.getZ() < -1000) {
            var r = c.world.random;
            for (int i = 0; i < 3; i++) {
                double x = c.player.getX() + (r.nextDouble() - 0.5) * 36, z = c.player.getZ() + (r.nextDouble() - 0.5) * 36;
                double y = c.player.getY() + 6 + r.nextDouble() * 10;
                if (c.world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) > y) continue; // под крышей не падает
                c.world.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, (r.nextDouble() - 0.5) * 0.02, -0.06, (r.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    // ------------------------------------------------------------------ гирлянды
    private static float[] pts = new float[0];
    private static int[] cols = new int[0];
    private static int npts;
    private static long lightsAt = -1000;
    private static final int[] NY = {0xFF3030, 0x30FF50, 0x3080FF, 0xFFD030, 0xFF40D0, 0xFFFFFF}, HW = {0xFF7A10, 0xA040FF, 0xFF7A10, 0x50FF40},
            CITY = {0xFFFFFF, 0x3060FF, 0xFF3030};

    private static void rebuild(MinecraftClient c) {
        int[] pal = switch (holiday) {
            case "NEW_YEAR" -> NY;
            case "HALLOWEEN" -> HW;
            case "CITY_DAY" -> CITY;
            default -> null;
        };
        npts = 0;
        if (pal == null) return;
        int px = (int) c.player.getX(), pz = (int) c.player.getZ();
        if (!Island.urban(Island.zone(px, pz)) && Island.zone(px, pz) != Island.Zone.BEACH) return;
        float[] p = new float[3 * 1200];
        int[] cl = new int[1200];
        int n = 0, G = Island.G;
        var w = c.world;
        int R = 52, S = 2;
        for (int x = (px & ~1) - R; x <= px + R && n < 1200; x += S)
            for (int z = (pz & ~1) - R; z <= pz + R && n < 1200; z += S) {
                if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                int h = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                if (h - G < 5) continue;
                // фасад у тротуара: огоньки на уровне витрин и второго этажа; невысокий дом — ещё и по кромке крыши
                int[][] nb = {{S, 0}, {0, S}, {-S, 0}, {0, -S}};
                for (int[] d : nb) {
                    if (!w.isChunkLoaded((x + d[0]) >> 4, (z + d[1]) >> 4) || n >= 1196) continue;
                    int hn = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x + d[0], z + d[1]);
                    if (h - hn < 4) continue;
                    float fx = x + 0.5f + d[0] * 0.3f, fz = z + 0.5f + d[1] * 0.3f;
                    int c0 = Math.floorMod(x * 7 + z * 13 + d[0], pal.length);
                    if (hn <= G + 2) {
                        float[] ys = h - G >= 10 ? new float[]{G + 4.6f, G + 8.6f} : new float[]{h + 0.1f};
                        for (int k = 0; k < ys.length; k++) {
                            p[n * 3] = fx;
                            p[n * 3 + 1] = ys[k];
                            p[n * 3 + 2] = fz;
                            cl[n++] = pal[(c0 + k) % pal.length];
                        }
                    }
                    if (h - G < 20) {
                        p[n * 3] = fx;
                        p[n * 3 + 1] = h + 0.12f;
                        p[n * 3 + 2] = fz;
                        cl[n++] = pal[(c0 + 2) % pal.length];
                    }
                }
            }
        pts = p;
        cols = cl;
        npts = n;
    }

    private static void lights(WorldRenderContext ctx) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.world == null || c.player == null || ctx.consumers() == null || holiday.equals("NONE")) return;
        long now = c.world.getTime();
        if (now - lightsAt > 60 || now < lightsAt) {
            lightsAt = now;
            rebuild(c);
        }
        if (npts == 0) return;
        Vec3d cam = ctx.camera().getPos();
        MatrixStack m = ctx.matrixStack() != null ? ctx.matrixStack() : new MatrixStack();
        Matrix4f mat = m.peek().getPositionMatrix();
        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayer.getLightning());
        float t = (System.currentTimeMillis() % 100000) / 1000f;
        long tod = c.world.getTimeOfDay() % 24000;
        float night = tod > 12500 && tod < 23500 ? 1f : 0.45f;
        // ориентация «звёздочки» к камере: два вектора экрана
        float yaw = (float) Math.toRadians(ctx.camera().getYaw()), pitch = (float) Math.toRadians(ctx.camera().getPitch());
        float rx = (float) -Math.cos(yaw), rz = (float) -Math.sin(yaw);
        float ux = (float) (-Math.sin(yaw) * Math.sin(pitch)), uy = (float) Math.cos(pitch), uz = (float) (Math.cos(yaw) * Math.sin(pitch));
        for (int i = 0; i < npts; i++) {
            float x = (float) (pts[i * 3] - cam.x), y = (float) (pts[i * 3 + 1] - cam.y), z = (float) (pts[i * 3 + 2] - cam.z);
            float d2 = x * x + y * y + z * z;
            if (d2 > 60 * 60) continue;
            float blink = 0.55f + 0.45f * MathHelper.sin(t * 3f + i * 1.7f);
            int col = cols[i];
            int r = col >> 16 & 255, g = col >> 8 & 255, b = col & 255;
            int a = (int) (255 * blink * night);
            float s = 0.11f + (float) Math.sqrt(d2) * 0.004f;
            quad(vc, mat, x, y, z, rx * s, rz * s, ux * s, uy * s, uz * s, r, g, b, a);
            float h = s * 2.6f;
            quad(vc, mat, x, y, z, rx * h, rz * h, ux * h, uy * h, uz * h, r, g, b, a / 4);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float rx, float rz, float ux, float uy, float uz, int r, int g, int b, int a) {
        vc.vertex(mat, x - rx - ux, y - uy, z - rz - uz).color(r, g, b, a);
        vc.vertex(mat, x + rx - ux, y - uy, z + rz - uz).color(r, g, b, a);
        vc.vertex(mat, x + rx + ux, y + uy, z + rz + uz).color(r, g, b, a);
        vc.vertex(mat, x - rx + ux, y + uy, z - rz + uz).color(r, g, b, a);
        // обратная сторона (если слой отсекает задние грани)
        vc.vertex(mat, x - rx + ux, y + uy, z - rz + uz).color(r, g, b, a);
        vc.vertex(mat, x + rx + ux, y + uy, z + rz + uz).color(r, g, b, a);
        vc.vertex(mat, x + rx - ux, y - uy, z + rz - uz).color(r, g, b, a);
        vc.vertex(mat, x - rx - ux, y - uy, z - rz - uz).color(r, g, b, a);
    }

    /** Есть ли праздничные костюмы у прохожих (для HolidayHats). */
    public static boolean costumes() {
        return holiday.equals("NEW_YEAR") || holiday.equals("HALLOWEEN");
    }
}
