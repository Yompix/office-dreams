package com.yompix.novograd.client.city;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.ui.Ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 0.9.10: картинки «Фотограма». Свой пост — уменьшенный кадр камеры телефона (96×54, RGB565 ≈ 10 КБ, уходит на сервер
 * вместе с постом); посты горожан — стильная карточка: цвет и знак по теме (погоня, ограбление, концерт, победа,
 * свидание), силуэт города и подпись-хэштег.
 */
public final class FeedImages {
    private FeedImages() {}

    static final int W = 96, H = 54;
    private static final Map<Integer, Identifier> TEX = new LinkedHashMap<>(16, 0.75f, true);

    /** Снимок с экрана (интерфейс уже спрятан) → на сервер к посту. */
    public static void capture(MinecraftClient mc) {
        try (NativeImage full = ScreenshotRecorder.takeScreenshot(mc.getFramebuffer())) {
            int fw = full.getWidth(), fh = full.getHeight();
            // кадр 16:9 из середины экрана
            int cw = Math.min(fw, fh * 16 / 9), ch = cw * 9 / 16, x0 = (fw - cw) / 2, y0 = (fh - ch) / 2;
            byte[] out = new byte[W * H * 2];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    // среднее по клетке 2×2 из её области — меньше «лесенки»
                    int r = 0, g = 0, b = 0, n = 0;
                    for (int sy = 0; sy < 2; sy++)
                        for (int sx = 0; sx < 2; sx++) {
                            int px = x0 + (int) ((x + 0.25 + sx * 0.5) * cw / W), py = y0 + (int) ((y + 0.25 + sy * 0.5) * ch / H);
                            int c = full.getColor(Math.min(fw - 1, px), Math.min(fh - 1, py)); // ABGR
                            r += c & 255;
                            g += (c >> 8) & 255;
                            b += (c >> 16) & 255;
                            n++;
                        }
                    r /= n;
                    g /= n;
                    b /= n;
                    int v = (r >> 3) << 11 | (g >> 2) << 5 | (b >> 3);
                    out[(y * W + x) * 2] = (byte) (v >> 8);
                    out[(y * W + x) * 2 + 1] = (byte) v;
                }
            NbtCompound n = new NbtCompound();
            n.putByteArray("img", out);
            NovogradClient.send("photo_img", n);
        } catch (Throwable t) {
            com.yompix.novograd.Novograd.LOG.warn("Фотограм: снимок не получился", t);
        }
    }

    /** Текстура из RGB565 (кэш по содержимому, до 24 штук). */
    static Identifier texture(byte[] img) {
        if (img.length != W * H * 2) return null;
        int key = java.util.Arrays.hashCode(img);
        Identifier id = TEX.get(key);
        if (id != null) return id;
        NativeImage ni = new NativeImage(W, H, false);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int v = (img[(y * W + x) * 2] & 255) << 8 | img[(y * W + x) * 2 + 1] & 255;
                int r = (v >> 11 & 31) * 255 / 31, g = (v >> 5 & 63) * 255 / 63, b = (v & 31) * 255 / 31;
                ni.setColor(x, y, 0xFF000000 | b << 16 | g << 8 | r); // ABGR
            }
        id = Identifier.of("novograd", "fotogram/" + Integer.toHexString(key));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(ni));
        TEX.put(key, id);
        if (TEX.size() > 24) {
            var it = TEX.entrySet().iterator();
            Identifier old = it.next().getValue();
            it.remove();
            MinecraftClient.getInstance().getTextureManager().destroyTexture(old);
        }
        return id;
    }

    /** Картинка поста: снимок или карточка по теме. */
    static void draw(DrawContext ctx, NbtCompound post, int x, int y, int w, int h) {
        byte[] img = post.getByteArray("i");
        Identifier tex = img.length > 0 ? texture(img) : null;
        if (tex != null) {
            ctx.drawTexture(tex, x, y, w, h, 0, 0, W, H, W, H);
            ctx.fill(x, y + h - 1, x + w, y + h, 0x60FFFFFF);
            return;
        }
        String t = post.getString("t").toLowerCase();
        int top, bottom;
        String glyph, tag;
        if (t.contains("погон") || t.contains("мигалк") || t.contains("полици")) { top = 0xFF1E3A8A; bottom = 0xFFB71C1C; glyph = "★"; tag = "#погоня"; }
        else if (t.contains("ограблен") || t.contains("касс")) { top = 0xFF3E0D12; bottom = 0xFF111111; glyph = "$"; tag = "#ограбление"; }
        else if (t.contains("концерт") || t.contains("ночью") || t.contains("клуб")) { top = 0xFF4A148C; bottom = 0xFFEC407A; glyph = "♪"; tag = "#ночь"; }
        else if (t.contains("перв") || t.contains("победа") || t.contains("рекорд") || t.contains("обыграл")) { top = 0xFFFF8F00; bottom = 0xFF5D4037; glyph = "⚑"; tag = "#победа"; }
        else if (t.contains("вечер с") || t.contains("свидан")) { top = 0xFFF06292; bottom = 0xFF6A1B4D; glyph = "♥"; tag = "#вдвоём"; }
        else if (t.contains("арест") || t.contains("сидеть")) { top = 0xFF37474F; bottom = 0xFF101010; glyph = "▦"; tag = "#новоград"; }
        else { top = 0xFF0277BD; bottom = 0xFFFF8A65; glyph = "◎"; tag = "#город"; }
        ctx.fillGradient(x, y, x + w, y + h, top, bottom);
        // силуэт города: дома разной высоты (по хэшу поста — у каждого свой)
        java.util.Random r = new java.util.Random(post.getString("t").hashCode());
        int bx = x;
        while (bx < x + w) {
            int bw = 5 + r.nextInt(9), bh = 6 + r.nextInt(h / 2);
            ctx.fill(bx, y + h - bh, Math.min(x + w, bx + bw), y + h, 0xD0101018);
            for (int wy = y + h - bh + 3; wy < y + h - 2; wy += 4) // окна
                for (int wx = bx + 2; wx < Math.min(x + w, bx + bw) - 1; wx += 3)
                    if (r.nextInt(3) == 0) ctx.fill(wx, wy, wx + 1, wy + 1, 0xC0FFE082);
            bx += bw + 1;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Ui.big(ctx, mc.textRenderer, glyph, x + w / 2, y + h / 2 - 14, 2f, 0xF0FFFFFF, 1);
        Ui.small(ctx, mc.textRenderer, tag, x + 3, y + 3, 0.7f, 0xE0FFFFFF, 0);
    }
}
