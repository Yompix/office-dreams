package com.yompix.novograd.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.story.Director;
import com.yompix.novograd.story.Story;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Только разработка: -Dnovograd.autotest=shotexport:гл:мис:шаг,… — выгрузка места кат-сцены для офлайн-постановки
 * (tools/shot_planner.py): блоки вокруг (воксели: 0 — воздух, иначе 1 + цвет карты; 255 — «прозрачное», сквозь которое
 * видно: стекло, листва, забор), где стоят актёры и куда идут, реплики с длиной озвучки, исходные планы.
 * Файлы — в -Dnovograd.exportdir (по умолчанию /tmp/claude-0/novograd-shots).
 */
public final class ShotExport {
    private ShotExport() {}

    private static final List<int[]> TODO = new ArrayList<>();
    private static int idx = -1, timer;
    private static boolean active;

    static void begin(String mode) {
        String[] parts = mode.split(":", 2);
        for (String k : parts[1].split(",")) {
            String[] a = k.split(":");
            TODO.add(new int[]{Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2])});
        }
        active = true;
    }

    static void tick(MinecraftClient c) {
        if (!active || c.player == null || ++timer < 40) return;
        timer = 0;
        idx++;
        if (idx >= TODO.size()) {
            active = false;
            Novograd.LOG.info("[shotexport] готово: {}", TODO.size());
            c.scheduleStop();
            return;
        }
        int[] k = TODO.get(idx);
        var s = c.getServer();
        if (s == null) return;
        // длины реплик знает клиент (по файлам озвучки)
        JsonObject step = Story.root().getAsJsonArray("chapters").get(k[0]).getAsJsonObject().getAsJsonArray("missions").get(k[1]).getAsJsonObject()
                .getAsJsonArray("steps").get(k[2]).getAsJsonObject();
        JsonArray lines = new JsonArray();
        if (step.has("lines")) for (JsonElement e : step.getAsJsonArray("lines")) {
            JsonObject l = e.getAsJsonObject().deepCopy();
            l.addProperty("len", com.yompix.novograd.client.story.Voice.length(l.has("voice") ? l.get("voice").getAsString() : ""));
            lines.add(l);
        }
        s.execute(() -> {
            try {
                export(CineAutotest.player(s), k, step, lines);
            } catch (Exception e) {
                Novograd.LOG.warn("[shotexport] {}:{}:{} не вышло", k[0], k[1], k[2], e);
            }
        });
    }

    private static JsonArray vec(Vec3d v) {
        JsonArray a = new JsonArray();
        a.add(Math.round(v.x * 100) / 100.0);
        a.add(Math.round(v.y * 100) / 100.0);
        a.add(Math.round(v.z * 100) / 100.0);
        return a;
    }

    private static void export(ServerPlayerEntity p, int[] k, JsonObject step, JsonArray lines) throws Exception {
        ServerWorld w = p.getServerWorld();
        JsonObject out = new JsonObject();
        out.addProperty("key", k[0] + ":" + k[1] + ":" + k[2]);
        out.add("lines", lines);
        out.add("shots", step.getAsJsonArray("shots"));
        out.add("actors", step.has("actors") ? step.getAsJsonArray("actors") : new JsonArray());
        // места: все «place» из планов и таймлайна → координаты (земля) и курс
        JsonObject places = new JsonObject();
        List<String> names = new ArrayList<>();
        for (JsonElement e : step.getAsJsonArray("shots")) {
            JsonObject sh = e.getAsJsonObject();
            for (String f : new String[]{"from", "to"}) if (sh.has(f)) names.add(sh.get(f).getAsString());
        }
        if (step.has("actors")) for (JsonElement e : step.getAsJsonArray("actors")) {
            JsonArray c = e.getAsJsonArray();
            for (int i = 3; i < c.size(); i++) {
                if (c.get(i).isJsonArray()) for (JsonElement q : c.get(i).getAsJsonArray()) names.add(q.getAsString());
                else if (c.get(i).isJsonPrimitive() && c.get(i).getAsJsonPrimitive().isString()) names.add(c.get(i).getAsString());
            }
        }
        for (String n : names) {
            if (places.has(n)) continue;
            Vec3d v;
            try {
                v = Director.pos(w, n);
            } catch (RuntimeException e) {
                v = null;
            }
            if (v == null) continue;
            w.getChunk(BlockPos.ofFloored(v));
            JsonObject o = new JsonObject();
            o.add("pos", vec(v));
            o.addProperty("ground", Story.topY(w, (int) Math.floor(v.x), (int) Math.floor(v.z)));
            o.addProperty("yaw", Story.placeYaw(w, n));
            double[] cam = Director.camera(w, n);
            if (cam != null) o.add("cam", vec(new Vec3d(cam[0], cam[1], cam[2])));
            places.add(n, o);
        }
        out.add("places", places);
        // кластеры точек (сцена может идти в разных концах острова) → коробки вокселей
        List<double[]> pts = new ArrayList<>();
        for (String n : places.keySet()) {
            JsonArray a = places.getAsJsonObject(n).getAsJsonArray("pos");
            pts.add(new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()});
        }
        List<double[]> boxes = new ArrayList<>(); // minx,miny,minz,maxx,maxy,maxz
        for (double[] q : pts) {
            double[] hit = null;
            for (double[] b : boxes)
                if (q[0] > b[0] - 40 && q[0] < b[3] + 40 && q[2] > b[2] - 40 && q[2] < b[5] + 40) hit = b;
            if (hit == null) boxes.add(new double[]{q[0], q[1], q[2], q[0], q[1], q[2]});
            else {
                hit[0] = Math.min(hit[0], q[0]); hit[1] = Math.min(hit[1], q[1]); hit[2] = Math.min(hit[2], q[2]);
                hit[3] = Math.max(hit[3], q[0]); hit[4] = Math.max(hit[4], q[1]); hit[5] = Math.max(hit[5], q[2]);
            }
        }
        JsonArray vox = new JsonArray();
        long cells = 0;
        for (double[] b : boxes) {
            int x0 = (int) Math.floor(b[0]) - 28, z0 = (int) Math.floor(b[2]) - 28, y0 = (int) Math.floor(b[1]) - 8;
            int sx = Math.min(160, (int) Math.ceil(b[3] - b[0]) + 57), sz = Math.min(160, (int) Math.ceil(b[5] - b[2]) + 57);
            int sy = Math.min(72, (int) Math.ceil(b[4] - b[1]) + 40);
            byte[] data = new byte[sx * sy * sz];
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int x = 0; x < sx; x++)
                for (int z = 0; z < sz; z++) {
                    w.getChunk((x0 + x) >> 4, (z0 + z) >> 4);
                    for (int y = 0; y < sy; y++) {
                        m.set(x0 + x, y0 + y, z0 + z);
                        var st = w.getBlockState(m);
                        int v = 0;
                        if (!st.isAir()) {
                            boolean solid = !st.getCollisionShape(w, m).isEmpty();
                            boolean see = !st.isOpaqueFullCube(w, m);
                            if (!solid && !st.getFluidState().isEmpty()) v = 0; // вода — для камеры воздух
                            else if (!solid) v = 0;                            // трава, цветы, факелы
                            else if (see) v = 255;                             // стекло, забор, листва — препятствие, но «сквозное»
                            else v = 1 + Math.min(253, st.getMapColor(w, m).id);
                        }
                        data[(x * sz + z) * sy + y] = (byte) v;
                    }
                }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bo)) {
                gz.write(data);
            }
            JsonObject vb = new JsonObject();
            JsonArray org = new JsonArray();
            org.add(x0);
            org.add(y0);
            org.add(z0);
            JsonArray size = new JsonArray();
            size.add(sx);
            size.add(sy);
            size.add(sz);
            vb.add("origin", org);
            vb.add("size", size);
            vb.addProperty("order", "x,z,y");
            vb.addProperty("gz", Base64.getEncoder().encodeToString(bo.toByteArray()));
            vox.add(vb);
            cells += data.length;
        }
        out.add("voxels", vox);
        String dir = System.getProperty("novograd.exportdir", "/tmp/claude-0/novograd-shots");
        new java.io.File(dir).mkdirs();
        String f = dir + "/scene_" + k[0] + "_" + k[1] + "_" + k[2] + ".json";
        try (FileWriter fw = new FileWriter(f, StandardCharsets.UTF_8)) {
            fw.write(out.toString());
        }
        Novograd.LOG.info("[shotexport] {} → {} (мест {}, коробок {}, вокселей {})", out.get("key").getAsString(), f, places.size(), boxes.size(), cells);
    }
}
