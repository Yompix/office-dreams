package com.yompix.novograd.client.screen;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.story.ClientStory;
import com.yompix.novograd.life.Taxi;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Карта города (клавиша M): вид сверху с подписями зданий, друзьями, целью сюжета и твоей меткой.
 * Колесо — масштаб, перетаскивание — сдвиг, ПКМ — поставить метку. Справа — сюжет и задания.
 */
public class MapScreen extends LifeScreen {
    private static final Identifier TEX = Novograd.id("dynamic/city_map");
    private static final int SIZE = 256;
    private static NativeImageBackedTexture texture;
    private static int texX = Integer.MIN_VALUE, texZ;
    private double cx, cz;
    private float zoom = 1.6f;
    private boolean dragging;
    private double lastMx, lastMy;
    private String tab = "story";

    public MapScreen() {
        super("Карта");
        Vec3d p = mc().player.getPos();
        NbtCompound pl = ClientStory.places;
        cx = pl.contains("ox") ? pl.getInt("ox") : p.x;
        cz = pl.contains("oz") ? pl.getInt("oz") : p.z;
        if (Math.abs(p.x - cx) > 100 || Math.abs(p.z - cz) > 100) {
            cx = p.x;
            cz = p.z;
        }
        bake((int) cx - SIZE / 2, (int) cz - SIZE / 2);
    }

    /** Рисуем карту в текстуру один раз (цвета карты Майнкрафта, с тенью по высоте). */
    private static void bake(int x0, int z0) {
        if (texture != null && Math.abs(x0 - texX) < 24 && Math.abs(z0 - texZ) < 24) return;
        World w = mc().world;
        NativeImage img = new NativeImage(SIZE, SIZE, false);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dz = 0; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                int x = x0 + dx, z = z0 + dz;
                int top = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
                pos.set(x, top, z);
                BlockState s = w.getBlockState(pos);
                MapColor col = s.getMapColor(w, pos);
                int north = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1) - 1;
                MapColor.Brightness br = top > north ? MapColor.Brightness.HIGH : top < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                int abgr = col == MapColor.CLEAR ? 0xFF1E2A1E : col.getRenderColor(br);
                img.setColor(dx, dz, abgr | 0xFF000000);
            }
        }
        if (texture == null) {
            texture = new NativeImageBackedTexture(img);
            mc().getTextureManager().registerTexture(TEX, texture);
        } else {
            texture.setImage(img);
            texture.upload();
        }
        texX = x0;
        texZ = z0;
    }

    private int mapX, mapY, mapW, mapH;

    private int sx(double wx) {
        return (int) (mapX + mapW / 2f + (wx - cx) * zoom);
    }

    private int sy(double wz) {
        return (int) (mapY + mapH / 2f + (wz - cz) * zoom);
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        ctx.fill(0, 0, width, height, 0xF0101420);
        int panelW = Math.min(170, width / 3);
        mapX = 8;
        mapY = 22;
        mapW = width - panelW - 24;
        mapH = height - 30;
        ctx.drawText(textRenderer, "КАРТА НОВОГРАДА", 10, 8, 0xFFFFC107, true);
        String help = "колесо — масштаб · тянуть — сдвиг · ПКМ — метка · M/Esc — закрыть";
        ctx.drawText(textRenderer, help, mapX + mapW - textRenderer.getWidth(help), 8, 0x88FFFFFF, false);
        ctx.fill(mapX - 1, mapY - 1, mapX + mapW + 1, mapY + mapH + 1, 0xFF3A3F55);
        ctx.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
        ctx.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF1E2A1E);
        // текстура карты
        int left = sx(texX), top = sy(texZ), size = (int) (SIZE * zoom);
        ctx.drawTexture(TEX, left, top, 0, 0, size, size, size, size);
        // подписи мест
        NbtCompound pl = ClientStory.places;
        for (String id : pl.getKeys()) {
            if (!(pl.get(id) instanceof NbtCompound p)) continue;
            int x = sx(p.getInt("x") + 0.5), y = sy(p.getInt("z") + 0.5);
            ctx.fill(x - 2, y - 2, x + 3, y + 3, 0xFFFFFFFF);
            ctx.fill(x - 1, y - 1, x + 2, y + 2, id.contains("casino") ? 0xFFFF2FB4 : id.contains("school") ? 0xFFFFC107 : 0xFF42A5F5);
            String name = p.getString("name");
            int tw = textRenderer.getWidth(name);
            ctx.fill(x - tw / 2 - 2, y + 4, x + tw / 2 + 2, y + 14, 0xAA000000);
            ctx.drawText(textRenderer, name, x - tw / 2, y + 5, 0xFFFFFFFF, false);
        }
        long now = System.currentTimeMillis();
        // цель сюжета
        Vec3d target = ClientStory.target();
        if (target != null) marker(ctx, target, 0xFFFFC107, "Цель", now);
        // задание работы / такси
        var d = NovogradClient.data;
        if (d.taskPos != null && !d.taskKind.isEmpty()) marker(ctx, Vec3d.ofCenter(d.taskPos), 0xFF4CAF50, "Задание", now);
        if (ClientStory.waypoint != null) marker(ctx, ClientStory.waypoint, 0xFFAB47BC, "Метка", now);
        // игроки
        for (PlayerEntity p : mc().world.getPlayers()) {
            int x = sx(p.getX()), y = sy(p.getZ());
            boolean me = p == mc().player;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x, y, 0);
            ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.getYaw() + 180));
            int c = me ? 0xFFFFFFFF : 0xFF7E57C2;
            for (int i = 0; i < 5; i++) ctx.fill(-i, -5 + i * 2, i + 1, -3 + i * 2, c);
            ctx.fill(-1, -5, 2, 4, me ? 0xFF1E88E5 : 0xFFD1C4E9);
            ctx.getMatrices().pop();
            if (!me) {
                String n = p.getName().getString();
                ctx.drawText(textRenderer, n, x - textRenderer.getWidth(n) / 2, y - 14, 0xFFD1C4E9, true);
            }
        }
        // друзья далеко (их нет в прогруженном мире) — по данным сервера
        for (var el : ClientStory.players.getList("list", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            NbtCompound f = (NbtCompound) el;
            String n = f.getString("name");
            if (n.equals(mc().player.getGameProfile().getName())) continue;
            if (mc().world.getPlayers().stream().anyMatch(pp -> pp.getGameProfile().getName().equals(n))) continue;
            int x = sx(f.getDouble("x")), y = sy(f.getDouble("z"));
            ctx.fill(x - 3, y - 3, x + 4, y + 4, 0xFF7E57C2);
            ctx.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
            ctx.drawText(textRenderer, n, x - textRenderer.getWidth(n) / 2, y - 13, 0xFFD1C4E9, true);
        }
        ctx.disableScissor();
        panel(ctx, width - panelW - 8, mapY, panelW, mapH);
    }

    private void marker(DrawContext ctx, Vec3d at, int color, String label, long now) {
        int x = sx(at.x), y = sy(at.z);
        int r = 4 + (int) ((now / 120) % 4);
        ctx.fill(x - r, y - r, x + r + 1, y + r + 1, (color & 0x00FFFFFF) | 0x44000000);
        ctx.fill(x - 3, y - 3, x + 4, y + 4, color);
        ctx.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
        ctx.drawText(textRenderer, label, x + 6, y - 4, color, true);
    }

    /** Правая панель: сюжет, задания и такси до выбранной точки. */
    private void panel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, 0xE0181C2C);
        ctx.fill(x, y, x + w, y + 1, 0xFFFF2FB4);
        String[][] tabs = {{"story", "Сюжет"}, {"tasks", "Задания"}};
        int tx = x + 4;
        for (String[] t : tabs) {
            int tw = textRenderer.getWidth(t[1]) + 10;
            roundRect(ctx, tx, y + 4, tw, 13, t[0].equals(tab) ? 0xFFFFB300 : 0xFF2E3450);
            ctx.drawText(textRenderer, t[1], tx + 5, y + 7, t[0].equals(tab) ? 0xFF111111 : 0xFFDDDDDD, false);
            area(tx, y + 4, tw, 13, () -> tab = t[0]);
            tx += tw + 4;
        }
        int yy = y + 24;
        var d = NovogradClient.data;
        var o = ClientStory.objective;
        if (tab.equals("story")) {
            if (d.storyDone) yy += wrap(ctx, "Сюжет пройден! Город твой.", x + 6, yy, w - 12, 0xFF7CFF8A);
            else if (o.contains("mission")) {
                ctx.drawText(textRenderer, o.getString("chapter"), x + 6, yy, 0xFFFFC107, false);
                yy += 12;
                ctx.drawText(textRenderer, o.getString("mission"), x + 6, yy, 0xFFFFFFFF, true);
                yy += 14;
                yy += wrap(ctx, o.getString("hint"), x + 6, yy, w - 12, 0xFFC8D0E8) + 4;
                if (o.contains("extra")) yy += wrap(ctx, o.getString("extra"), x + 6, yy, w - 12, 0xFF9FE6FF) + 4;
                if (ClientStory.target() != null) button(ctx, x + 6, yy, w - 12, 14, "Показать цель", 0xFF5A4300, true, () -> {
                    cx = ClientStory.target().x;
                    cz = ClientStory.target().z;
                });
                yy += 20;
            } else yy += wrap(ctx, "Сюжет идёт в мире «Новоград: Город».", x + 6, yy, w - 12, 0xFFAAAAAA);
            // главы
            String[] chapters = {"1. Новенький", "2. Друзья и деньги", "3. Выпускной", "4. Взрослая жизнь", "5. Барон"};
            for (int i = 0; i < chapters.length; i++) {
                boolean done = d.storyDone || i < d.chapter, cur = !d.storyDone && i == d.chapter;
                ctx.drawText(textRenderer, (done ? "✔ " : cur ? "▶ " : "· ") + chapters[i], x + 6, yy, done ? 0xFF7CFF8A : cur ? 0xFFFFC107 : 0xFF777777, false);
                yy += 11;
            }
        } else {
            if (d.taskPos != null && !d.taskKind.isEmpty()) {
                ctx.drawText(textRenderer, "Текущее задание:", x + 6, yy, 0xFF7CFF8A, false);
                yy += 11;
                yy += wrap(ctx, taskName(d.taskKind) + (d.taskReward > 0 ? " · $" + d.taskReward : ""), x + 6, yy, w - 12, 0xFFFFFFFF) + 6;
            } else {
                yy += wrap(ctx, "Нет активных заданий. Жители с «!» над головой просят о помощи, а вакансии есть в телефоне.", x + 6, yy, w - 12, 0xFFAAAAAA) + 6;
            }
            ctx.drawText(textRenderer, "Работа: " + com.yompix.novograd.life.Jobs.Job.of(d.job).title, x + 6, yy, 0xFFC8D0E8, false);
            yy += 11;
            ctx.drawText(textRenderer, "Энергия " + d.energy + "% · настроение " + d.mood + "%", x + 6, yy, 0xFFC8D0E8, false);
            yy += 16;
        }
        // такси до метки
        Vec3d wp = ClientStory.waypoint;
        int by = y + h - 40;
        if (wp != null) {
            int price = Taxi.price(mc().player.getPos(), wp);
            button(ctx, x + 6, by, w - 12, 15, "Такси к метке · $" + price, 0xFFF7C600, d.money >= price, () -> {
                NbtCompound n = new NbtCompound();
                n.putString("dest", (int) Math.floor(wp.x) + "," + (int) Math.floor(wp.z));
                NovogradClient.send("taxi_call", n);
                close();
            });
            button(ctx, x + 6, by + 19, w - 12, 15, "Убрать метку", 0xFF5E35B1, true, () -> ClientStory.waypoint = null);
        } else {
            wrap(ctx, "ПКМ по карте — поставить метку, туда можно вызвать такси.", x + 6, by + 6, w - 12, 0xFF888888);
        }
    }

    private static String taskName(String kind) {
        if (kind.equals("courier")) return "Доставить посылку";
        if (kind.equals("taxi")) return "Довезти клиента";
        if (kind.equals("taxi_wait")) return "Ждать такси у дороги";
        if (kind.equals("taxi_ride")) return "Едем на такси";
        if (kind.startsWith("pickup:")) return "Забрать клиента";
        if (kind.startsWith("quest")) return "Помочь жителю";
        return kind;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        float old = zoom;
        zoom = MathHelper.clamp(zoom * (v > 0 ? 1.2f : 1 / 1.2f), 0.6f, 6f);
        // приближаем к курсору
        double wx = cx + (mx - mapX - mapW / 2f) / old, wz = cz + (my - mapY - mapH / 2f) / old;
        cx = wx - (mx - mapX - mapW / 2f) / zoom;
        cz = wz - (my - mapY - mapH / 2f) / zoom;
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        boolean inMap = mx >= mapX && mx < mapX + mapW && my >= mapY && my < mapY + mapH;
        if (inMap && button == 1) {
            double wx = cx + (mx - mapX - mapW / 2f) / zoom, wz = cz + (my - mapY - mapH / 2f) / zoom;
            ClientStory.waypoint = new Vec3d(wx, mc().player.getY(), wz);
            click();
            return true;
        }
        if (inMap && button == 0) {
            dragging = true;
            lastMx = mx;
            lastMy = my;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            cx -= (mx - lastMx) / zoom;
            cz -= (my - lastMy) / zoom;
            lastMx = mx;
            lastMy = my;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_M) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    protected void background(DrawContext ctx) {
    }
}
