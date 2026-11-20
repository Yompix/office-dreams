package com.yompix.novograd.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yompix.novograd.ModContent;
import com.yompix.novograd.entity.CitizenEntity;
import com.yompix.novograd.life.Life;
import com.yompix.novograd.life.LifeData;
import com.yompix.novograd.life.LifeNet;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * «Сцена заранее» (1.0 «Кино»): следующая кат-сцена готовится, пока игрок ПОДХОДИТ к метке, а не в момент старта.
 *
 * <p>Что было в 0.6.6: всё начиналось на самом триггере — сервер в этот тик грузил (а то и генерировал) участки у
 * точек камеры, ставил актёров, переносил игрока-«зрителя» к первому кадру, клиент получал участки и строил их сетку,
 * и только потом проявлял кадр (пауза «Загрузка сцены…» до 8 с).
 *
 * <p>Теперь: игрок ближе {@link #NEAR} блоков к месту, где сцена начнётся (жёлтая метка миссии или цель шага «доехать»
 * перед сценой), или шаг перед сценой — титр/диалог без места:
 * <ul>
 *   <li>участки у всех точек камеры и актёров держатся загруженными (свой тикет — генерация и загрузка идут в фоне
 *       заранее, а не в тик старта);</li>
 *   <li>массовка нулевой секунды (бандиты, полиция, прохожие сцены) ставится заранее — невидимой, неуязвимой, на месте;
 *       Director на старте её «принимает», а не создаёт (клиент уже знает сущность, модель и скин);</li>
 *   <li>клиенту — «cut_prep»: озвучка реплик и скины прогреваются (чтение файлов, длина, текстуры) до старта.</li>
 * </ul>
 * Ушёл далеко или прошло 90 с — всё снимается. -Dnovograd.stage=false — как в 0.6.6 (для замеров «до/после»).
 */
public final class Stage {
    private Stage() {}

    public static final boolean ON = !"false".equals(System.getProperty("novograd.stage"));
    /** С какого расстояния до места старта готовим сцену. */
    static final double NEAR = 72;
    static final String TAG = "nvg_stage";
    static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("novograd_stage", Comparator.comparingLong(ChunkPos::toLong), 20 * 15);

    private static final class Staged {
        JsonObject step;
        int key;
        final List<ChunkPos> chunks = new ArrayList<>();
        final Map<String, UUID> extras = new HashMap<>();
        long since;
        boolean prepSent;
    }

    private static final Map<UUID, Staged> STAGED = new HashMap<>();

    /** Раз в полсекунды из Story.tick (до проверки метки). */
    public static void tick(ServerPlayerEntity p) {
        if (!ON || p.age % 10 != 3) return;
        ServerWorld w = p.getServerWorld();
        Staged st = STAGED.get(p.getUuid());
        if (Director.running(p)) return;
        Object[] next = Story.active(p) ? upcoming(p, Life.get(p)) : null;
        JsonObject step = next == null ? null : (JsonObject) next[0];
        Vec3d at = next == null ? null : (Vec3d) next[1];
        boolean near = step != null && (at == null || p.squaredDistanceTo(at) < NEAR * NEAR);
        if (st != null && (!near || st.step != step || w.getTime() - st.since > 20 * 90)) {
            release(p);
            st = null;
        }
        if (!near) return;
        if (st == null) {
            st = new Staged();
            st.step = step;
            st.key = Story.stepKey(Life.get(p));
            st.since = w.getTime();
            STAGED.put(p.getUuid(), st);
            collect(w, step, st);
            spawnExtras(p, w, step, st);
            com.yompix.novograd.Novograd.LOG.info("[stage] готовим сцену {} заранее: участков {}, массовки {}, до старта {} блоков",
                    Story.stepKey(Life.get(p)), st.chunks.size(), st.extras.size(), at == null ? 0 : (int) Math.sqrt(p.squaredDistanceTo(at)));
        }
        // тикеты с запасом по времени: обновляем, пока игрок рядом
        for (ChunkPos c : st.chunks) w.getChunkManager().addTicket(TICKET, c, 2, c);
        if (!st.prepSent) {
            st.prepSent = true;
            prep(p, step, st);
        }
    }

    /**
     * Ближайшая кат-сцена впереди и место, где она начнётся (null — сразу, без места).
     * Свободная игра у метки: первая сцена миссии (перед ней могут быть титр/диалог). Посреди миссии: шаг «доехать» →
     * сцена; или текущий шаг — титр/диалог, а за ним сцена.
     */
    @Nullable
    static Object[] upcoming(ServerPlayerEntity p, LifeData d) {
        JsonObject m = Story.mission(d);
        if (m == null || !d.side.isEmpty()) return null;
        JsonArray steps = m.getAsJsonArray("steps");
        if (Story.idle(d)) {
            JsonObject cs = ahead(steps, 0);
            return cs == null ? null : new Object[]{cs, Story.missionStart(p, d)};
        }
        if (d.step >= steps.size()) return null;
        JsonObject cur = steps.get(d.step).getAsJsonObject();
        String type = Story.str(cur, "type", "");
        if (type.equals("cutscene")) return null;
        if (type.equals("goto")) {
            JsonObject cs = ahead(steps, d.step + 1);
            Vec3d at = Story.place(p.getServerWorld(), Story.str(cur, "place", ""));
            return cs == null || at == null ? null : new Object[]{cs, at};
        }
        if (type.equals("title") || type.equals("dialog")) {
            JsonObject cs = ahead(steps, d.step + 1);
            return cs == null ? null : new Object[]{cs, null};
        }
        return null;
    }

    /** Сцена на шаге i или сразу за титром/диалогом (не дальше двух шагов). */
    @Nullable
    private static JsonObject ahead(JsonArray steps, int i) {
        for (int k = i; k < steps.size() && k < i + 3; k++) {
            JsonObject s = steps.get(k).getAsJsonObject();
            String type = Story.str(s, "type", "");
            if (type.equals("cutscene")) return s.has("shots") ? s : null;
            if (!type.equals("title") && !type.equals("dialog")) return null;
        }
        return null;
    }

    /** Все точки сцены: камера (начало/конец планов, ключи) и места актёров. */
    static List<Vec3d> points(ServerWorld w, JsonObject step) {
        List<Vec3d> r = new ArrayList<>();
        for (JsonElement e : step.getAsJsonArray("shots")) {
            JsonObject s = e.getAsJsonObject();
            for (String k : new String[]{"from", "to"})
                if (s.has(k)) {
                    Vec3d v = Director.pos(w, s.get(k).getAsString());
                    if (v != null) r.add(v);
                }
            if (s.has("keys")) for (JsonElement ke : s.getAsJsonArray("keys")) {
                JsonArray k = ke.getAsJsonArray();
                r.add(new Vec3d(k.get(1).getAsDouble(), k.get(2).getAsDouble(), k.get(3).getAsDouble()));
            }
        }
        if (step.has("actors")) for (JsonElement e : step.getAsJsonArray("actors")) {
            JsonArray c = e.getAsJsonArray();
            if (c.size() < 3) continue;
            String kind = c.get(1).getAsString();
            if (kind.equals("spawn") || kind.equals("car") || kind.equals("walk") || kind.equals("run")) {
                if (c.size() > 3 && c.get(3).isJsonPrimitive()) {
                    Vec3d v = Director.pos(w, c.get(3).getAsString());
                    if (v != null) r.add(v);
                }
            } else if (kind.equals("drive") && c.size() > 3 && c.get(3).isJsonArray()) {
                for (JsonElement pe : c.get(3).getAsJsonArray()) {
                    Vec3d v = Director.pos(w, pe.getAsString());
                    if (v != null) r.add(v);
                }
            }
        }
        return r;
    }

    private static void collect(ServerWorld w, JsonObject step, Staged st) {
        Set<Long> seen = new HashSet<>();
        for (Vec3d v : points(w, step)) {
            ChunkPos c = new ChunkPos(net.minecraft.util.math.BlockPos.ofFloored(v));
            if (seen.add(c.toLong())) st.chunks.add(c);
        }
    }

    /** Массовка нулевой секунды — заранее, невидимой (герои сюжета не трогаем: они живут в мире сами). */
    private static void spawnExtras(ServerPlayerEntity p, ServerWorld w, JsonObject step, Staged st) {
        if (!step.has("actors")) return;
        for (JsonElement e : step.getAsJsonArray("actors")) {
            JsonArray c = e.getAsJsonArray();
            if (c.size() < 4 || c.get(0).getAsDouble() > 0.01 || !c.get(1).getAsString().equals("spawn")) continue;
            String id = c.get(2).getAsString();
            if (Director.isCharacterId(id)) continue;
            Vec3d at = Director.pos(w, c.get(3).getAsString());
            if (at == null) continue;
            // только если участок уже загружен — стадия не должна сама дёргать генерацию в тик сервера
            if (!w.isChunkLoaded(net.minecraft.util.math.ChunkSectionPos.getSectionCoord(at.x), net.minecraft.util.math.ChunkSectionPos.getSectionCoord(at.z))) continue;
            CitizenEntity ce = Director.makeExtra(w, c, at);
            if (ce == null) continue;
            ce.setInvisible(true);
            ce.setInvulnerable(true);
            ce.setSilent(true);
            ce.addCommandTag(TAG);
            w.spawnEntity(ce);
            ce.setStationary(true);
            st.extras.put(id, ce.getUuid());
        }
    }

    /** Клиенту: что прогреть (озвучка реплик, скины актёров). */
    private static void prep(ServerPlayerEntity p, JsonObject step, Staged st) {
        NbtCompound n = new NbtCompound();
        NbtList voices = new NbtList();
        if (step.has("lines")) for (JsonElement e : step.getAsJsonArray("lines")) {
            JsonObject l = e.getAsJsonObject();
            if (l.has("voice")) voices.add(NbtString.of(l.get("voice").getAsString()));
        }
        n.put("voices", voices);
        NbtList ids = new NbtList();
        for (UUID u : st.extras.values()) {
            Entity e = p.getServerWorld().getEntity(u);
            if (e instanceof CitizenEntity c) ids.add(net.minecraft.nbt.NbtInt.of(c.getSkin()));
        }
        n.put("skins", ids);
        LifeNet.send(p, "cut_prep", n);
    }

    /** Director на старте: заранее поставленная массовка с этим id (становится видимой) или null. */
    @Nullable
    static CitizenEntity take(ServerPlayerEntity p, JsonObject step, String id) {
        Staged st = STAGED.get(p.getUuid());
        if (st == null || st.step != step) return null;
        UUID u = st.extras.remove(id);
        if (u == null || !(p.getServerWorld().getEntity(u) instanceof CitizenEntity c)) return null;
        c.setInvisible(false);
        c.setInvulnerable(false);
        c.setSilent(false);
        c.removeCommandTag(TAG);
        return c;
    }

    /** Сцена началась: тикеты больше не нужны (дальше держит Director), лишнюю массовку убрать. */
    static void started(ServerPlayerEntity p) {
        release(p);
    }

    public static void release(ServerPlayerEntity p) {
        Staged st = STAGED.remove(p.getUuid());
        if (st == null) return;
        ServerWorld w = p.getServerWorld();
        for (UUID u : st.extras.values()) {
            Entity e = w.getEntity(u);
            if (e != null) e.discard();
        }
        for (ChunkPos c : st.chunks) w.getChunkManager().removeTicket(TICKET, c, 2, c);
    }

    /** Осиротевшая заготовка (сервер выключили, пока она ждала) — убрать при загрузке. */
    public static void init() {
        // вышел, пока сцена ждала, — снять тикеты и невидимую массовку сразу (на сервере с друзьями она иначе стоит до
        // выгрузки участка)
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> release(h.player));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> STAGED.clear());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, w) -> {
            if (!e.getCommandTags().contains(TAG)) return;
            for (Staged st : STAGED.values()) if (st.extras.containsValue(e.getUuid())) return;
            w.getServer().execute(e::discard);
        });
    }
}
