package com.yompix.novograd.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.entity.CitizenEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Объёмные причёски, бороды, усы, кепки, шапки, каски и очки поверх головы жителя.
 * Какая причёска у какого скина — в citizen_looks.json (его пишет tools/gen_assets.py).
 */
public class HairFeature extends FeatureRenderer<CitizenEntity, PlayerEntityModel<CitizenEntity>> {
    private static final Identifier HAIR = Novograd.id("textures/entity/citizen/hair.png");
    private static final Identifier SOLID = Novograd.id("textures/entity/car/base.png");
    private static JsonArray looks;
    private final Map<String, ModelPart> parts = new HashMap<>();

    public HairFeature(FeatureRendererContext<CitizenEntity, PlayerEntityModel<CitizenEntity>> ctx) {
        super(ctx);
        // координаты в пикселях головы: лицо смотрит в −z, макушка — y = −8
        part("top", b().cuboid(-4.5f, -9.1f, -4.5f, 9, 1.6f, 9));
        part("fringe", b().cuboid(-4.5f, -8.7f, -4.75f, 9, 1.3f, 1));
        part("back_short", b().cuboid(-4.5f, -8f, 3.55f, 9, 4.5f, 1.2f).cuboid(-4.65f, -8f, -2f, 0.9f, 2.6f, 6).cuboid(3.75f, -8f, -2f, 0.9f, 2.6f, 6));
        part("buzz", b().cuboid(-4.3f, -8.45f, -4.3f, 8.6f, 0.75f, 8.6f));
        part("quiff", b().cuboid(-3.5f, -10f, -4.9f, 7, 1.8f, 3.6f).cuboid(-3f, -9.6f, -1.5f, 6, 1.2f, 3));
        part("long", b().cuboid(-4.75f, -8f, -4f, 1, 9.5f, 8.2f).cuboid(3.75f, -8f, -4f, 1, 9.5f, 8.2f).cuboid(-4.6f, -8f, 3.55f, 9.2f, 11, 1.3f));
        part("bob", b().cuboid(-4.75f, -8f, -3.6f, 1, 6.6f, 7.8f).cuboid(3.75f, -8f, -3.6f, 1, 6.6f, 7.8f).cuboid(-4.6f, -8f, 3.55f, 9.2f, 6.8f, 1.3f));
        part("ponytail", b().cuboid(-4.5f, -8f, 3.55f, 9, 3.5f, 1.2f).cuboid(-1.1f, -6.6f, 4.5f, 2.2f, 2.2f, 1.4f).cuboid(-1f, -5.4f, 5.5f, 2, 7.5f, 2).cuboid(-0.7f, 2f, 5.8f, 1.4f, 2, 1.4f));
        ModelPartBuilder curly = b();
        for (int x = -4; x <= 2; x += 2)
            for (int z = -4; z <= 2; z += 2) curly.cuboid(x - 0.3f, -9.7f + ((x + z) & 2) * 0.25f, z - 0.3f, 2.6f, 2.2f, 2.6f);
        for (int x = -4; x <= 2; x += 2) curly.cuboid(x - 0.3f, -8f, 3.4f, 2.6f, 4, 1.6f);
        curly.cuboid(-4.9f, -8.2f, -3f, 1.3f, 3, 6).cuboid(3.6f, -8.2f, -3f, 1.3f, 3, 6);
        part("curly", curly);
        part("bald_ring", b().cuboid(-4.4f, -5.2f, -1f, 0.7f, 2, 5).cuboid(3.7f, -5.2f, -1f, 0.7f, 2, 5).cuboid(-4.4f, -5.2f, 3.6f, 8.8f, 2, 0.8f));
        // борода — вокруг рта, сам рот открыт (видно губы и как он говорит)
        part("beard", b().cuboid(-4.3f, -3.3f, -4.7f, 2.0f, 3.5f, 1.1f).cuboid(2.3f, -3.3f, -4.7f, 2.0f, 3.5f, 1.1f)
                .cuboid(-2.3f, -0.85f, -4.75f, 4.6f, 1.05f, 1.15f)
                .cuboid(-4.35f, -3.6f, -4f, 0.8f, 3.4f, 4.5f).cuboid(3.55f, -3.6f, -4f, 0.8f, 3.4f, 4.5f)
                .cuboid(-2.6f, 0.2f, -4.9f, 5.2f, 1.2f, 1.4f));
        part("mustache", b().cuboid(-2.7f, -3.5f, -4.85f, 5.4f, 1, 0.9f).cuboid(-3.2f, -3f, -4.8f, 1, 1.2f, 0.8f).cuboid(2.2f, -3f, -4.8f, 1, 1.2f, 0.8f));
        part("cap", b().cuboid(-4.6f, -9.3f, -4.6f, 9.2f, 2.6f, 9.2f).cuboid(-4.6f, -7.2f, -7.6f, 9.2f, 0.6f, 3.2f).cuboid(-0.6f, -9.8f, -0.6f, 1.2f, 0.5f, 1.2f));
        part("helmet", b().cuboid(-4.9f, -9.8f, -4.9f, 9.8f, 3.2f, 9.8f).cuboid(-5.5f, -7f, -5.5f, 11, 0.7f, 11).cuboid(-0.8f, -10.4f, -4.9f, 1.6f, 0.8f, 9.8f));
        part("beanie", b().cuboid(-4.7f, -9.7f, -4.7f, 9.4f, 3.4f, 9.4f).cuboid(-4.95f, -6.7f, -4.95f, 9.9f, 1.3f, 9.9f).cuboid(-1.1f, -11f, -1.1f, 2.2f, 1.5f, 2.2f));
        part("glasses_frame", b().cuboid(-4.4f, -4.7f, -4.65f, 8.8f, 0.6f, 0.4f).cuboid(-4.55f, -4.7f, -4.4f, 0.4f, 0.6f, 4.2f).cuboid(4.15f, -4.7f, -4.4f, 0.4f, 0.6f, 4.2f)
                .cuboid(-3.9f, -4.3f, -4.62f, 0.4f, 1.8f, 0.35f).cuboid(-0.85f, -4.3f, -4.62f, 0.4f, 1.8f, 0.35f).cuboid(0.45f, -4.3f, -4.62f, 0.4f, 1.8f, 0.35f).cuboid(3.5f, -4.3f, -4.62f, 0.4f, 1.8f, 0.35f));
        part("bow", b().cuboid(-2.6f, -10.2f, 1.8f, 2.2f, 1.6f, 1).cuboid(0.4f, -10.2f, 1.8f, 2.2f, 1.6f, 1).cuboid(-0.5f, -9.9f, 1.7f, 1, 1, 1.2f));
        part("bow_pony", b().cuboid(-2.4f, -7.3f, 5.6f, 2, 1.5f, 0.8f).cuboid(0.4f, -7.3f, 5.6f, 2, 1.5f, 0.8f).cuboid(-0.5f, -7f, 5.5f, 1, 1, 1));
        part("long_girl", b().cuboid(-4.8f, -8.2f, -4.2f, 1.1f, 11, 8.6f).cuboid(3.7f, -8.2f, -4.2f, 1.1f, 11, 8.6f).cuboid(-4.7f, -8.2f, 3.5f, 9.4f, 12.5f, 1.4f)
                .cuboid(-4.2f, 2.5f, 3.3f, 8.4f, 2.5f, 1.2f));
        // лицо: веки моргают, губы шевелятся при разговоре
        part("skirt", b().cuboid(-4.4f, 9.5f, -2.4f, 8.8f, 2, 4.8f).cuboid(-4.9f, 11.5f, -2.9f, 9.8f, 2, 5.8f).cuboid(-5.3f, 13.5f, -3.3f, 10.6f, 1.6f, 6.6f));
        part("lids", b().cuboid(-3.02f, -4.02f, -4.06f, 2.04f, 1.04f, 0.05f).cuboid(0.98f, -4.02f, -4.06f, 2.04f, 1.04f, 0.05f));
        part("mouth_open", b().cuboid(-1.1f, -2.05f, -4.07f, 2.2f, 1.3f, 0.05f));
        part("mouth_wide", b().cuboid(-1.4f, -2.2f, -4.07f, 2.8f, 1.8f, 0.05f));
        part("glasses_lens", b().cuboid(-3.6f, -4.2f, -4.6f, 2.8f, 1.6f, 0.3f).cuboid(0.8f, -4.2f, -4.6f, 2.8f, 1.6f, 0.3f));
        // стиль SA (одежда и цирюльня игрока): ирокез, афро, эспаньолка, федора, бандана, кепка козырьком назад
        part("mohawk", b().cuboid(-1.1f, -11.2f, -4.6f, 2.2f, 3.2f, 9.2f).cuboid(-0.8f, -12f, -3.5f, 1.6f, 1, 6));
        part("afro", b().cuboid(-5.6f, -11.6f, -5.2f, 11.2f, 5.2f, 10.6f).cuboid(-5.2f, -7f, -3f, 1.2f, 3f, 8.2f).cuboid(4f, -7f, -3f, 1.2f, 3f, 8.2f)
                .cuboid(-5f, -7f, 4.2f, 10f, 3.6f, 1.2f).cuboid(-4.8f, -12.3f, -4.4f, 9.6f, 0.8f, 9f));
        part("goatee", b().cuboid(-1.3f, -0.8f, -4.75f, 2.6f, 1.6f, 1f).cuboid(-2.4f, -3.4f, -4.8f, 4.8f, 0.8f, 0.8f));
        part("fedora", b().cuboid(-4.7f, -10.8f, -4.7f, 9.4f, 4.2f, 9.4f).cuboid(-6.8f, -7f, -6.8f, 13.6f, 0.6f, 13.6f));
        part("fedora_band", b().cuboid(-4.8f, -7.9f, -4.8f, 9.6f, 0.9f, 9.6f));
        part("bandana", b().cuboid(-4.65f, -8.9f, -4.65f, 9.3f, 3.1f, 9.3f).cuboid(-1.2f, -7f, 4.4f, 2.4f, 2.4f, 1.2f).cuboid(-0.6f, -5f, 4.9f, 1.2f, 3f, 0.6f));
        // 0.8: «подписи» героев сюжета — у прохожих этого нет (tools/gen_story_looks.py, life/Looks)
        part("beret", b().cuboid(-4.3f, -9.75f, -5.0f, 10.0f, 1.5f, 9.8f).cuboid(-0.6f, -10.4f, -0.6f, 1.2f, 0.7f, 1.2f));
        part("cap_back", b().cuboid(-4.6f, -9.3f, -4.6f, 9.2f, 2.6f, 9.2f).cuboid(-4.6f, -7.2f, 4.4f, 9.2f, 0.6f, 3.2f));
        part("goggles_strap", b().cuboid(-4.65f, -7.5f, -4.65f, 9.3f, 0.8f, 9.3f));
        part("goggles_lens", b().cuboid(-3.6f, -8.1f, -5.05f, 3.0f, 1.9f, 0.7f).cuboid(0.6f, -8.1f, -5.05f, 3.0f, 1.9f, 0.7f));
        part("earrings", b().cuboid(-4.7f, -2.7f, -0.6f, 0.6f, 1.5f, 0.6f).cuboid(4.1f, -2.7f, -0.6f, 0.6f, 1.5f, 0.6f));
        // корпус (пространство тела: шея y = 0, пояс y = 12, грудь в −z)
        part("scarf", b().cuboid(-4.45f, -0.5f, -2.45f, 8.9f, 2.1f, 4.9f).cuboid(0.8f, 1.4f, -2.7f, 2.2f, 7.5f, 0.6f).cuboid(-2.6f, 1.4f, 2.2f, 2.0f, 5.0f, 0.6f));
        part("chain", b().cuboid(-2.7f, 0.2f, -2.3f, 0.5f, 3.2f, 0.3f).cuboid(2.2f, 0.2f, -2.3f, 0.5f, 3.2f, 0.3f).cuboid(-2.2f, 3.0f, -2.3f, 4.4f, 0.5f, 0.3f)
                .cuboid(-0.55f, 3.4f, -2.4f, 1.1f, 1.2f, 0.4f));
        part("coat_collar", b().cuboid(-4.55f, -0.7f, -2.55f, 9.1f, 1.8f, 5.1f));
        part("coat_tails", b().cuboid(-4.5f, 10.0f, 2.05f, 9.0f, 10.5f, 0.7f).cuboid(-4.75f, 10.0f, -2.35f, 0.7f, 10.5f, 4.7f).cuboid(4.05f, 10.0f, -2.35f, 0.7f, 10.5f, 4.7f)
                .cuboid(-4.75f, 10.0f, -2.6f, 2.4f, 10.5f, 0.6f).cuboid(2.35f, 10.0f, -2.6f, 2.4f, 10.5f, 0.6f));
        parts096();
    }

    // ===================================================================== 0.9.6: редактор внешности
    /** Длина прядей по варианту 0 короче / 1 / 2 длиннее. */
    private static float len(int v, float a, float b, float c) {
        return v == 0 ? a : v == 1 ? b : c;
    }

    private void parts096() {
        part("crew", b().cuboid(-4.35f, -8.6f, -4.35f, 8.7f, 1.0f, 8.7f));
        part("side_fringe", b().cuboid(-4.5f, -8.8f, -4.75f, 6.2f, 1.7f, 1).cuboid(1.7f, -9.1f, -4.7f, 2.8f, 0.7f, 1));
        part("undercut", b().cuboid(-3.6f, -9.9f, -4.7f, 7.2f, 2.3f, 8.6f).cuboid(-3.2f, -10.3f, -5.0f, 6.4f, 0.9f, 3.2f));
        part("pixie_fringe", b().cuboid(-4.5f, -8.9f, -4.75f, 9, 1.6f, 1).cuboid(-4.5f, -7.3f, -4.75f, 3.5f, 0.9f, 1).cuboid(0.5f, -7.3f, -4.75f, 2.5f, 0.6f, 1));
        ModelPartBuilder cs = b();
        for (int x = -4; x <= 2; x += 2)
            for (int z = -4; z <= 2; z += 2) cs.cuboid(x - 0.2f, -9.1f + ((x + z) & 2) * 0.15f, z - 0.2f, 2.4f, 1.5f, 2.4f);
        cs.cuboid(-4.5f, -8f, 3.5f, 9, 3, 1.2f);
        part("curly_short", cs);
        ModelPartBuilder cr = b();
        for (float x : new float[]{-3.6f, -1.6f, 0.4f, 2.4f}) cr.cuboid(x, -8.6f, -4.3f, 1.2f, 0.55f, 8.6f).cuboid(x, -8f, 4.05f, 1.2f, 5f, 0.5f);
        part("cornrows", cr);
        part("man_bun", b().cuboid(-1.6f, -10.7f, 0.4f, 3.2f, 2.5f, 3.2f).cuboid(-1.2f, -10.9f, 0.8f, 2.4f, 0.4f, 2.4f));
        part("bun", b().cuboid(-2f, -8.4f, 4.3f, 4f, 3.6f, 2.6f).cuboid(-1.5f, -7.9f, 6.7f, 3f, 2.6f, 0.8f));
        part("pony_tie", b().cuboid(-1.1f, -9.9f, 2.6f, 2.2f, 1.6f, 2.2f));
        for (int v = 0; v < 3; v++) {
            float L = len(v, 5, 8, 11);
            part("long_" + v, b().cuboid(-4.75f, -8f, -4f, 1, L + 1.5f, 8.2f).cuboid(3.75f, -8f, -4f, 1, L + 1.5f, 8.2f).cuboid(-4.6f, -8f, 3.55f, 9.2f, L + 3, 1.3f));
            part("long_girl_" + v, b().cuboid(-4.8f, -8.2f, -4.2f, 1.1f, L + 3, 8.6f).cuboid(3.7f, -8.2f, -4.2f, 1.1f, L + 3, 8.6f)
                    .cuboid(-4.7f, -8.2f, 3.5f, 9.4f, L + 4.5f, 1.4f).cuboid(-4.2f, L - 5.5f, 3.3f, 8.4f, 2.5f, 1.2f));
            ModelPartBuilder wv = b();
            for (int k = 0; k < (int) ((L + 4) / 2.5f); k++) {
                float y = -8.2f + k * 2.5f, o = (k % 2) * 0.35f;
                wv.cuboid(-4.85f - o, y, -4.2f, 1.1f + o, 2.6f, 8.6f).cuboid(3.75f, y, -4.2f, 1.1f + o, 2.6f, 8.6f).cuboid(-4.7f - o, y, 3.5f, 9.4f + 2 * o, 2.6f, 1.4f + o);
            }
            part("wavy_" + v, wv);
            float bl = len(v, 4.8f, 6.6f, 8.4f);
            part("bob_" + v, b().cuboid(-4.75f, -8f, -3.6f, 1, bl, 7.8f).cuboid(3.75f, -8f, -3.6f, 1, bl, 7.8f).cuboid(-4.6f, -8f, 3.55f, 9.2f, bl + 0.2f, 1.3f));
            float pl = len(v, 4.5f, 7.5f, 10.5f);
            part("ponytail_" + v, b().cuboid(-4.5f, -8f, 3.55f, 9, 3.5f, 1.2f).cuboid(-1.1f, -6.6f, 4.5f, 2.2f, 2.2f, 1.4f).cuboid(-1f, -5.4f, 5.5f, 2, pl, 2)
                    .cuboid(-0.7f, -5.4f + pl, 5.8f, 1.4f, 2, 1.4f));
            part("high_pony_" + v, b().cuboid(-1f, -9.6f, 4.4f, 2f, 2f, 2f).cuboid(-0.9f, -8f, 5.6f, 1.8f, pl + 1, 1.8f).cuboid(-0.6f, -7f + pl, 5.8f, 1.2f, 2, 1.2f));
            ModelPartBuilder br = b().cuboid(-4.5f, -8f, 3.55f, 9, 4.5f, 1.2f);
            int seg = (int) len(v, 4, 6, 8);
            for (int sd = -1; sd <= 1; sd += 2)
                for (int k = 0; k < seg; k++) {
                    float w = k % 2 == 0 ? 1.7f : 1.3f;
                    br.cuboid(sd * 2.6f - w / 2, -4.2f + k * 1.6f, 3.9f + (1.7f - w) / 2, w, 1.7f, w);
                }
            part("braids_" + v, br);
            ModelPartBuilder dr = b().cuboid(-4.5f, -9.0f, -4.5f, 9, 1.4f, 9);
            float dl = len(v, 6, 9, 12);
            for (float x = -4.2f; x <= 3.4f; x += 1.5f) dr.cuboid(x, -8f, 3.7f, 1, dl, 1);
            for (float z = -2.5f; z <= 3f; z += 1.6f) dr.cuboid(-4.8f, -8f, z, 1, dl - 1.5f, 1).cuboid(3.8f, -8f, z, 1, dl - 1.5f, 1);
            dr.cuboid(-3.8f, -8.6f, -4.8f, 1, 3, 1).cuboid(2.8f, -8.6f, -4.8f, 1, 3, 1);
            part("dreads_" + v, dr);
        }
        part("streaks", b().cuboid(-2.6f, -9.2f, -4.6f, 0.8f, 0.2f, 9.2f).cuboid(1.4f, -9.2f, -4.6f, 0.8f, 0.2f, 9.2f)
                .cuboid(-2.6f, -8.75f, -4.85f, 0.8f, 1.3f, 0.15f).cuboid(1.4f, -8.75f, -4.85f, 0.8f, 1.3f, 0.15f));
        part("streaks_long", b().cuboid(-4.9f, -8f, -1.5f, 0.15f, 9f, 0.9f).cuboid(4.75f, -8f, 0.5f, 0.15f, 9f, 0.9f)
                .cuboid(-2.0f, -8f, 4.85f, 0.9f, 10f, 0.15f).cuboid(1.5f, -8f, 4.85f, 0.9f, 10f, 0.15f));
        // головные уборы
        part("sunhat", b().cuboid(-4.6f, -10.4f, -4.6f, 9.2f, 3.6f, 9.2f).cuboid(-7.8f, -7.1f, -7.8f, 15.6f, 0.5f, 15.6f));
        part("sunhat_band", b().cuboid(-4.7f, -7.9f, -4.7f, 9.4f, 0.8f, 9.4f));
        part("bucket", b().cuboid(-4.7f, -10.2f, -4.7f, 9.4f, 3.5f, 9.4f).cuboid(-6.0f, -7.0f, -6.0f, 12f, 0.5f, 12f));
        part("headband", b().cuboid(-4.6f, -7.7f, -4.6f, 9.2f, 1.2f, 9.2f));
        part("ushanka", b().cuboid(-4.9f, -10.5f, -4.9f, 9.8f, 3.5f, 9.8f).cuboid(-5.4f, -7.5f, -3f, 0.7f, 4.8f, 5.8f).cuboid(4.7f, -7.5f, -3f, 0.7f, 4.8f, 5.8f)
                .cuboid(-5f, -8.6f, -5.4f, 10f, 2f, 0.7f));
        // очки и серьги
        part("cateye_tips", b().cuboid(-4.6f, -5.2f, -4.7f, 1.2f, 0.6f, 0.4f).cuboid(3.4f, -5.2f, -4.7f, 1.2f, 0.6f, 0.4f));
        part("sport_frame", b().cuboid(-4.5f, -5.0f, -4.85f, 9.0f, 0.5f, 0.45f).cuboid(-4.6f, -5.0f, -4.4f, 0.4f, 0.5f, 4.2f).cuboid(4.2f, -5.0f, -4.4f, 0.4f, 0.5f, 4.2f));
        part("sport_lens", b().cuboid(-4.3f, -4.6f, -4.8f, 8.6f, 1.8f, 0.3f));
        part("hoops", b().cuboid(-4.8f, -2.5f, -0.9f, 0.4f, 0.3f, 1.5f).cuboid(-4.8f, -0.9f, -0.9f, 0.4f, 0.3f, 1.5f).cuboid(-4.8f, -2.5f, -0.9f, 0.4f, 1.9f, 0.3f)
                .cuboid(-4.8f, -2.5f, 0.3f, 0.4f, 1.9f, 0.3f)
                .cuboid(4.4f, -2.5f, -0.9f, 0.4f, 0.3f, 1.5f).cuboid(4.4f, -0.9f, -0.9f, 0.4f, 0.3f, 1.5f).cuboid(4.4f, -2.5f, -0.9f, 0.4f, 1.9f, 0.3f)
                .cuboid(4.4f, -2.5f, 0.3f, 0.4f, 1.9f, 0.3f));
        // шея и сумки (пространство тела)
        part("pendant", b().cuboid(-2.2f, 0.2f, -2.3f, 0.35f, 2.6f, 0.25f).cuboid(1.85f, 0.2f, -2.3f, 0.35f, 2.6f, 0.25f).cuboid(-1.85f, 2.6f, -2.3f, 3.7f, 0.35f, 0.25f)
                .cuboid(-0.4f, 2.9f, -2.42f, 0.8f, 0.9f, 0.35f));
        part("nbandana", b().cuboid(-4.4f, -0.4f, -2.4f, 8.8f, 1.2f, 4.8f).cuboid(-3f, 0.8f, -2.5f, 6f, 1.2f, 0.4f).cuboid(-2.1f, 2f, -2.5f, 4.2f, 1.1f, 0.4f)
                .cuboid(-1.1f, 3.1f, -2.5f, 2.2f, 1f, 0.4f));
        part("backpack", b().cuboid(-3.4f, 1.0f, 2.05f, 6.8f, 8.5f, 2.8f).cuboid(-2.6f, 5.5f, 4.85f, 5.2f, 3f, 0.6f)
                .cuboid(-3f, 0f, -2.32f, 1f, 7.5f, 0.3f).cuboid(2f, 0f, -2.32f, 1f, 7.5f, 0.3f).cuboid(-3f, -0.25f, -2.3f, 1f, 0.3f, 4.4f).cuboid(2f, -0.25f, -2.3f, 1f, 0.3f, 4.4f));
        ModelPartBuilder ms = b();
        for (int i = 0; i < 8; i++) ms.cuboid(-3.6f + i * 0.95f, -0.2f + i * 1.35f, -2.35f, 1.1f, 1.5f, 0.3f).cuboid(-3.6f + i * 0.95f, -0.2f + i * 1.35f, 2.05f, 1.1f, 1.5f, 0.3f);
        ms.cuboid(3.3f, 9.2f, -2.2f, 1.6f, 3.6f, 4.4f);
        part("messenger", ms);
        part("handbag", b().cuboid(2.2f, 9.6f, -3.3f, 3.4f, 3.0f, 1.2f).cuboid(2.4f, 0f, -2.35f, 0.35f, 9.8f, 0.25f).cuboid(2.4f, -0.25f, -2.3f, 0.35f, 0.3f, 4.4f)
                .cuboid(2.4f, 0f, 2.05f, 0.35f, 4f, 0.25f));
        part("fanny", b().cuboid(-3.2f, 9.6f, -3.05f, 6.4f, 2.4f, 1.0f).cuboid(-4.15f, 10.3f, -2.15f, 8.3f, 0.6f, 4.3f));
        part("tote", b().cuboid(-4.3f, 4.5f, 2.05f, 4.4f, 6f, 1.0f).cuboid(-3.4f, 0f, 2.05f, 0.4f, 4.6f, 0.3f).cuboid(-1.4f, 0f, 2.05f, 0.4f, 4.6f, 0.3f));
        // юбки по длине: короткая есть («skirt»), до колена и в пол
        part("skirt_mid", b().cuboid(-4.4f, 9.5f, -2.4f, 8.8f, 3f, 4.8f).cuboid(-4.75f, 12.5f, -2.75f, 9.5f, 3f, 5.5f).cuboid(-5.05f, 15.5f, -3.05f, 10.1f, 3f, 6.1f));
        part("skirt_long", b().cuboid(-4.4f, 9.5f, -2.4f, 8.8f, 3f, 4.8f).cuboid(-4.75f, 12.5f, -2.75f, 9.5f, 3f, 5.5f).cuboid(-5.05f, 15.5f, -3.05f, 10.1f, 3.5f, 6.1f)
                .cuboid(-5.35f, 19f, -3.35f, 10.7f, 3.6f, 6.7f));
    }

    private static ModelPartBuilder b() {
        return ModelPartBuilder.create().uv(0, 0);
    }

    private void part(String name, ModelPartBuilder builder) {
        ModelData data = new ModelData();
        data.getRoot().addChild("p", builder, ModelTransform.NONE);
        parts.put(name, TexturedModelData.of(data, 64, 64).createModel().getChild("p"));
    }

    static JsonArray looks() {
        if (looks == null) {
            try (var in = HairFeature.class.getResourceAsStream("/assets/novograd/citizen_looks.json")) {
                looks = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            } catch (Exception e) {
                looks = new JsonArray();
            }
        }
        return looks;
    }

    private static int color(JsonObject o, String key, float shade) {
        int c = Integer.parseInt(o.get(key).getAsString(), 16);
        int r = Math.min(255, (int) (((c >> 16) & 255) * shade + 18)), g = Math.min(255, (int) (((c >> 8) & 255) * shade + 18)), b = Math.min(255, (int) ((c & 255) * shade + 18));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // ===================================================================== 0.8: образ «скомпилирован» один раз
    /**
     * Описание образа (JSON) превращено в списки частей и цветов по слоям: волосы (текстура прядей), сплошные
     * (шапки, очки, «подписи» героев, веки, рот) и стёкла. Раньше на каждого жителя в каждом кадре — разбор JSON и
     * hex-строк и 6–10 смен слоя (каждая смена — отдельная отрисовка); теперь три слоя по очереди.
     */
    static final class Compiled {
        final java.util.List<ModelPart> hairP = new java.util.ArrayList<>(), solidP = new java.util.ArrayList<>();
        final it.unimi.dsi.fastutil.ints.IntArrayList hairC = new it.unimi.dsi.fastutil.ints.IntArrayList(), solidC = new it.unimi.dsi.fastutil.ints.IntArrayList();
        final java.util.List<ModelPart> skirtP = new java.util.ArrayList<>(), waistP = new java.util.ArrayList<>(), chestP = new java.util.ArrayList<>();
        final it.unimi.dsi.fastutil.ints.IntArrayList skirtC = new it.unimi.dsi.fastutil.ints.IntArrayList(), waistC = new it.unimi.dsi.fastutil.ints.IntArrayList(),
                chestC = new it.unimi.dsi.fastutil.ints.IntArrayList();
        /** 0.9.7: цепь/кулон/бандана висят спереди — при форме груди их выносим вперёд (не прячутся внутри). */
        final java.util.BitSet chestFront = new java.util.BitSet();
        int lid, lens;
        boolean face, glasses;
        String lensPart = "glasses_lens";

        boolean body() {
            return !skirtP.isEmpty() || !waistP.isEmpty() || !chestP.isEmpty();
        }
    }

    private final java.util.IdentityHashMap<JsonObject, Compiled> compiled = new java.util.IdentityHashMap<>();
    private final java.util.HashMap<String, Compiled> byText = new java.util.HashMap<>();

    Compiled compile(JsonObject look) {
        Compiled c = compiled.get(look);
        if (c != null) return c;
        // образ игрока (StyleClient.headJson) — новый объект каждый кадр: ищем по содержимому
        String key = look.toString();
        c = byText.get(key);
        if (c != null) return c;
        if (compiled.size() > 512) compiled.clear();
        if (byText.size() > 256) byText.clear();
        final Compiled cc = new Compiled();
        c = cc;
        byText.put(key, c);
        int hc = color(look, "hair", 1.0f);
        boolean hat = look.has("cap") || look.has("beanie") || look.has("helmet") || look.has("fedora") || look.has("bandana") || look.has("cap_back")
                || look.has("sunhat") || look.has("bucket") || look.has("ushanka");
        String style = look.get("style").getAsString();
        int v = look.has("len") ? Math.max(0, Math.min(2, look.get("len").getAsInt())) : -1; // −1 — старый образ (citizen_looks.json)
        boolean fringe = !look.has("nofringe");
        int hl = look.has("hl") ? color(look, "hl", 1.0f) : 0;
        java.util.function.BiConsumer<String, Integer> hair = (n, col) -> { cc.hairP.add(parts.get(n)); cc.hairC.add((int) col); };
        java.util.function.BiConsumer<String, Integer> solid = (n, col) -> { cc.solidP.add(parts.get(n)); cc.solidC.add((int) col); };
        java.util.function.Consumer<String> top = n -> {
            if (hat) return;
            hair.accept("top", hc);
            if (fringe) hair.accept(n, hc);
        };
        boolean longish = false;
        switch (style) {
            case "buzz" -> hair.accept("buzz", hc);
            case "crew" -> hair.accept("crew", hc);
            case "bald" -> hair.accept("bald_ring", hc);
            case "cornrows" -> {
                hair.accept("buzz", hc);
                if (!hat) hair.accept("cornrows", hc);
            }
            case "curly" -> hair.accept(!hat ? "curly" : "back_short", hc);
            case "curly_short" -> hair.accept(!hat ? "curly_short" : "back_short", hc);
            case "afro" -> hair.accept(!hat ? "afro" : "curly", hc);
            case "mohawk" -> {
                hair.accept("buzz", hc);
                if (!hat) hair.accept("mohawk", hc);
            }
            case "undercut" -> {
                hair.accept("buzz", hc);
                if (!hat) hair.accept("undercut", hc);
            }
            case "man_bun" -> {
                if (!hat) {
                    hair.accept("top", hc);
                    hair.accept("man_bun", hc);
                }
                hair.accept("back_short", hc);
            }
            case "side_part" -> {
                top.accept("side_fringe");
                hair.accept("back_short", hc);
            }
            case "pixie" -> {
                top.accept("pixie_fringe");
                hair.accept("back_short", hc);
            }
            case "dreads" -> {
                hair.accept("dreads_" + Math.max(0, v), hc);
                longish = true;
            }
            case "wavy" -> {
                top.accept("fringe");
                hair.accept("wavy_" + (v < 0 ? 1 : v), hc);
                longish = true;
            }
            case "high_pony" -> {
                if (!hat) hair.accept("top", hc);
                hair.accept("back_short", hc);
                hair.accept("high_pony_" + (v < 0 ? 1 : v), hc);
            }
            case "braids" -> {
                top.accept("fringe");
                hair.accept("braids_" + (v < 0 ? 1 : v), hc);
            }
            case "bun" -> {
                top.accept("fringe");
                hair.accept("back_short", hc);
                hair.accept("bun", hc);
            }
            default -> {
                if (!hat) {
                    hair.accept("top", hc);
                    if (fringe || v < 0) hair.accept("fringe", hc);
                }
                switch (style) {
                    case "long" -> {
                        String base = look.has("girl") ? "long_girl" : "long";
                        hair.accept(v < 0 ? base : base + "_" + v, hc);
                        longish = true;
                    }
                    case "bob" -> {
                        hair.accept(v < 0 ? "bob" : "bob_" + v, hc);
                        longish = true;
                    }
                    case "ponytail" -> hair.accept(v < 0 ? "ponytail" : "ponytail_" + v, hc);
                    case "slick", "quiff" -> {
                        hair.accept("back_short", hc);
                        if (!hat) hair.accept("quiff", hc);
                    }
                    default -> hair.accept("back_short", hc);
                }
            }
        }
        if (hl != 0 && !style.equals("bald") && !style.equals("buzz") && !style.equals("crew")) {
            if (!hat) hair.accept("streaks", hl);
            if (longish) hair.accept("streaks_long", hl);
        }
        if (look.has("beard")) hair.accept("beard", color(look, "beard", 0.95f));
        if (look.has("goatee")) hair.accept("goatee", color(look, "goatee", 0.95f));
        if (look.has("mustache")) hair.accept("mustache", color(look, "mustache", 0.95f));
        if (look.has("bow")) solid.accept(style.equals("ponytail") ? "bow_pony" : "bow", color(look, "bow", 1f));
        if (look.has("helmet")) solid.accept("helmet", color(look, "helmet", 1f));
        else if (look.has("cap")) solid.accept("cap", color(look, "cap", 1f));
        else if (look.has("beanie")) solid.accept("beanie", color(look, "beanie", 1f));
        else if (look.has("bandana")) solid.accept("bandana", color(look, "bandana", 1f));
        else if (look.has("fedora")) {
            solid.accept("fedora", color(look, "fedora", 1f));
            solid.accept("fedora_band", look.has("band") ? color(look, "band", 1f) : 0xFF2A1A12);
        } else if (look.has("sunhat")) {
            solid.accept("sunhat", color(look, "sunhat", 1f));
            solid.accept("sunhat_band", 0xFF2A1A12);
        } else if (look.has("bucket")) solid.accept("bucket", color(look, "bucket", 1f));
        else if (look.has("ushanka")) solid.accept("ushanka", color(look, "ushanka", 1f));
        if (look.has("headband")) solid.accept("headband", color(look, "headband", 1f));
        if (look.has("beret") && !look.has("cap") && !look.has("fedora")) solid.accept("beret", color(look, "beret", 1f));
        if (look.has("cap_back")) solid.accept("cap_back", color(look, "cap_back", 1f));
        if (look.has("goggles")) {
            solid.accept("goggles_strap", color(look, "goggles", 0.8f));
            solid.accept("goggles_lens", 0xFF8FD3FF);
        }
        if (look.has("earrings")) solid.accept(look.has("hoops") ? "hoops" : "earrings", color(look, "earrings", 1f));
        if (look.has("glasses")) {
            String g = look.get("glasses").getAsString();
            int fc = look.has("gcol") ? color(look, "gcol", 1f) : g.equals("gold") || g.equals("aviator") ? 0xFFD4AF37 : 0xFF1A1A1A;
            if (g.equals("sport")) {
                solid.accept("sport_frame", fc);
                c.lensPart = "sport_lens";
            } else {
                solid.accept("glasses_frame", fc);
                if (g.equals("cateye")) solid.accept("cateye_tips", fc);
            }
            c.glasses = true;
            c.lens = g.equals("shades") || g.equals("sport") ? 0xF0101010 : g.equals("aviator") ? 0xE0302010 : 0x70A8D8FF;
        }
        c.face = look.has("skin");
        if (c.face) c.lid = color(look, "skin", 0.82f);
        if (look.has("skirt")) {
            int sl = look.has("skirtLen") ? look.get("skirtLen").getAsInt() : 0;
            c.skirtP.add(parts.get(sl == 2 ? "skirt_long" : sl == 1 ? "skirt_mid" : "skirt"));
            c.skirtC.add(color(look, "skirt", 0.95f));
        }
        if (look.has("coat")) {
            c.waistP.add(parts.get("coat_tails"));
            c.waistC.add(color(look, "coat", 0.92f));
            c.chestP.add(parts.get("coat_collar"));
            c.chestC.add(color(look, "coat", 0.85f));
        }
        if (look.has("scarf")) { c.chestP.add(parts.get("scarf")); c.chestC.add(color(look, "scarf", 1f)); }
        if (look.has("chain")) { c.chestFront.set(c.chestP.size()); c.chestP.add(parts.get("chain")); c.chestC.add(color(look, "chain", 1f)); }
        if (look.has("pendant")) { c.chestFront.set(c.chestP.size()); c.chestP.add(parts.get("pendant")); c.chestC.add(color(look, "pendant", 1f)); }
        if (look.has("nbandana")) { c.chestFront.set(c.chestP.size()); c.chestP.add(parts.get("nbandana")); c.chestC.add(color(look, "nbandana", 1f)); }
        if (look.has("bag")) {
            String bg = look.get("bag").getAsString();
            if (parts.containsKey(bg)) {
                c.chestP.add(parts.get(bg));
                c.chestC.add(color(look, "bagc", 0.95f));
            }
        }
        compiled.put(look, c);
        return c;
    }

    @org.jetbrains.annotations.Nullable
    Compiled compiled(CitizenEntity e) {
        // 0.9.6: житель с внешностью из генератора
        var gen = com.yompix.novograd.client.style.CitizenLooks.get(e);
        if (gen != null) return compile(gen.head());
        JsonArray all = looks();
        int skin = e.getSkin();
        return skin >= all.size() ? null : compile(all.get(skin).getAsJsonObject());
    }

    /** Слой 0 — пряди, 1 — сплошные (+веки, рот, праздничная шапка), 2 — стёкла очков. Голова: лицо в −z, макушка y = −8. */
    void headPass(MatrixStack m, VertexConsumerProvider buffers, int light, Compiled c, int pass, boolean blink, boolean asleep, boolean talking, int id) {
        int ov = OverlayTexture.DEFAULT_UV;
        if (pass == 0) {
            if (c.hairP.isEmpty()) return;
            VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(HAIR));
            for (int i = 0; i < c.hairP.size(); i++) c.hairP.get(i).render(m, vc, light, ov, c.hairC.getInt(i));
        } else if (pass == 1) {
            VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SOLID));
            for (int i = 0; i < c.solidP.size(); i++) c.solidP.get(i).render(m, vc, light, ov, c.solidC.getInt(i));
            if (c.face) {
                // спящий — глаза закрыты (и в очках тоже)
                if (asleep || blink && !c.glasses) parts.get("lids").render(m, vc, light, ov, c.lid);
                int lip = com.yompix.novograd.client.story.CutsceneScreen.mouth(id); // 1.0: рот по огибающей озвучки
                if (lip >= 0) {
                    if (lip >= 7) parts.get("mouth_wide").render(m, vc, light, ov, 0xFF4A1414);
                    else if (lip >= 3) parts.get("mouth_open").render(m, vc, light, ov, 0xFF5A1A1A);
                } else if (talking) {
                    int phase = (int) ((System.currentTimeMillis() / 110 + id) % 4);
                    if (phase == 1) parts.get("mouth_open").render(m, vc, light, ov, 0xFF5A1A1A);
                    else if (phase == 2) parts.get("mouth_wide").render(m, vc, light, ov, 0xFF4A1414);
                }
            }
        } else if (c.glasses) {
            parts.get(c.lensPart).render(m, buffers.getBuffer(RenderLayer.getEntityTranslucent(SOLID)), light, ov, c.lens);
        }
    }

    /** Корпус (пространство тела: шея y = 0, пояс y = 12): юбка — слой 0, плащ/шарф/цепь — слой 1. */
    void bodyPass(MatrixStack m, VertexConsumerProvider buffers, int light, Compiled c, int pass, boolean chest, boolean waist) {
        int ov = OverlayTexture.DEFAULT_UV;
        if (pass == 0 && waist && !c.skirtP.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(HAIR));
            for (int i = 0; i < c.skirtP.size(); i++) c.skirtP.get(i).render(m, vc, light, ov, c.skirtC.getInt(i));
        } else if (pass == 1 && (waist && !c.waistP.isEmpty() || chest && !c.chestP.isEmpty())) {
            VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(SOLID));
            if (waist) for (int i = 0; i < c.waistP.size(); i++) c.waistP.get(i).render(m, vc, light, ov, c.waistC.getInt(i));
            if (chest) for (int i = 0; i < c.chestP.size(); i++) {
                boolean push = bustPush > 0 && c.chestFront.get(i);
                if (push) {
                    m.push();
                    m.translate(0, 0, -bustPush / 16f);
                }
                c.chestP.get(i).render(m, vc, light, ov, c.chestC.getInt(i));
                if (push) m.pop();
            }
        }
    }

    /** 0.9.7: насколько вынести вперёд висящее на груди (px), ставит {@link com.yompix.novograd.client.style.BodyShape}. */
    public static float bustPush;

    public static boolean talking(CitizenEntity e) {
        return com.yompix.novograd.client.story.ClientStory.isTalking(e.getId()) || e.getActivity() == CitizenEntity.Activity.TALK;
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider buffers, int light, CitizenEntity e, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (e.isInvisible()) return;
        Compiled c = compiled(e);
        if (c == null) return;
        boolean blink = blink(e.getId(), e.age), talk = talking(e);
        for (int pass = 0; pass < 3; pass++) {
            m.push();
            getContextModel().head.rotate(m);
            headPass(m, buffers, light, c, pass, blink, e.asleep(), talk, e.getId());
            if (pass == 1) com.yompix.novograd.client.HolidayHats.render(m, buffers, light, e.getId());
            m.pop();
            if (c.body() && pass < 2) {
                m.push();
                getContextModel().body.rotate(m);
                bodyPass(m, buffers, light, c, pass, true, true);
                m.pop();
            }
        }
    }

    /**
     * Причёска, борода, головной убор и очки по описанию look (как в citizen_looks.json) — в пространстве головы.
     * Нужна и жителям, и игроку (см. client/style/PlayerLookFeature).
     */
    public void drawLook(MatrixStack m, VertexConsumerProvider buffers, int light, JsonObject look, boolean blink, boolean asleep, boolean talking, int id) {
        Compiled c = compile(look);
        for (int pass = 0; pass < 3; pass++) headPass(m, buffers, light, c, pass, blink, asleep, talking, id);
    }

    /** 0.9.6: юбка, плащ, шея и сумка игрока — в пространстве корпуса. */
    public void drawBody(MatrixStack m, VertexConsumerProvider buffers, int light, JsonObject look) {
        Compiled c = compile(look);
        if (!c.body()) return;
        for (int pass = 0; pass < 2; pass++) bodyPass(m, buffers, light, c, pass, true, true);
    }


    /**
     * 0.8: живое моргание — в каждом окне 6 с одно-два моргания в разные моменты (по хешу окна, без состояния),
     * иногда двойное; веки закрыты 2–3 тика. Раньше — ровно раз в 4.5 с, как метроном.
     */
    public static boolean blink(int id, int age) {
        int t = age + Math.floorMod(id * 977, 97);
        int base = Math.floorDiv(t, 120) * 120;
        int h = (int) (((id * 31L + (base / 120) * 7919L) * 2654435761L) >>> 33);
        int a = Math.floorMod(h, 60), b = 62 + Math.floorMod(h >>> 7, 50), lt = t - base;
        boolean two = Math.floorMod(h >>> 11, 3) != 0, dbl = Math.floorMod(h >>> 13, 5) == 0;
        return lt >= a && lt < a + 2 || two && lt >= b && lt < b + 3 || dbl && lt >= a + 5 && lt < a + 7;
    }

}
