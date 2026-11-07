package com.yompix.novograd.client.screen;

import com.yompix.novograd.client.NovogradClient;
import com.yompix.novograd.client.ui.Ui;
import com.yompix.novograd.gta.Skills;
import com.yompix.novograd.gta.Skills.Skill;
import com.yompix.novograd.life.LifeData;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/** Телефон → «Навыки»: полоски навыков как в SA, звание, что даёт и как качать. */
public class SkillsScreen extends LifeScreen {
    public SkillsScreen() {
        super("Навыки");
    }

    @Override
    protected void draw(DrawContext ctx, float delta) {
        float a = openAnim();
        LifeData d = NovogradClient.data;
        int top = 10 + (int) ((1 - a) * -20);
        title(ctx, "Навыки", 12, top, Ui.CYAN);
        Ui.small(ctx, textRenderer, "растут от практики · зал «Железо» качает силу и выносливость", 14, top + 28, 0.8f, 0xFFD8D2EA, 0);
        Skill[] all = Skill.values();
        int cols = width >= 520 ? 2 : 1;
        int gx = 10, gy = top + 40, gw = (width - 20 - (cols - 1) * 8) / cols;
        int rows = (all.length + cols - 1) / cols;
        int rh = Math.max(26, Math.min(36, (height - gy - 26) / rows));
        for (int i = 0; i < all.length; i++) {
            Skill s = all[i];
            int col = i / rows, row = i % rows;
            int x = gx + col * (gw + 8), y = gy + row * rh;
            float appear = Ui.easeOut(Math.min(1, (seconds() - i * 0.03f) / 0.3f));
            int pts = Skills.points(d, s), pct = pts / 10;
            ctx.fill(x, y, x + gw, y + rh - 4, Ui.alpha(0xB00B0812, appear));
            ctx.fill(x, y, x + 2, y + rh - 4, Ui.alpha(s.color, appear));
            Ui.small(ctx, textRenderer, s.title.toUpperCase(Locale.ROOT), x + 7, y + 3, 0.85f, Ui.alpha(0xFFFFFFFF, appear), 0);
            String r = Skills.rank(s, pct) + "  " + pct + "%";
            Ui.small(ctx, textRenderer, r, x + gw - 6, y + 3, 0.8f, Ui.alpha(s.color, appear), 2);
            Ui.bar(ctx, x + 7, y + 12, gw - 14, 4, pts / (float) Skills.MAX * appear, s.color);
            for (int k = 1; k < 4; k++) ctx.fill(x + 7 + (gw - 14) * k / 4, y + 12, x + 8 + (gw - 14) * k / 4, y + 16, 0x80000000);
            if (rh >= 30) Ui.small(ctx, textRenderer, effect(s, d, pts / (float) Skills.MAX), x + 7, y + 19, 0.68f, Ui.alpha(0xFFB8B2CC, appear), 0);
            if (hover(x, y, gw, rh - 4)) {
                ctx.fill(x, y + rh - 5, x + gw, y + rh - 4, s.color);
                tip = how(s);
            }
        }
        // низ
        ctx.fill(0, height - 18, width, height, 0xE0050309);
        ctx.fill(0, height - 19, width, height - 18, Ui.alpha(Ui.CYAN, 0.8f));
        Ui.small(ctx, textRenderer, tip.isEmpty() ? "Наведи на навык — как его качать" : tip, 10, height - 13, 0.8f, 0xD0FFFFFF, 0);
        tip = "";
        String back = "‹ НАЗАД";
        int bw = textRenderer.getWidth(back) + 16;
        Ui.slant(ctx, width - bw - 8, height - 16, bw, 14, 4, hover(width - bw - 8, height - 17, bw, 17) ? Ui.CYAN : 0x60FFFFFF);
        ctx.drawText(textRenderer, back, width - bw, height - 13, 0xFFFFFFFF, false);
        area(width - bw - 8, height - 17, bw, 17, this::close);
    }

    private String tip = "";

    private static String effect(Skill s, LifeData d, float k) {
        return switch (s) {
            case DRIVE -> "Сцепление +" + Math.round(35 * k) + "% · руль +" + Math.round(12 * k) + "% · занос до " + Math.round(65 - 20 * k) + "°";
            case BIKE -> "Руль мотоцикла +" + Math.round(18 * k) + "%";
            case FLY -> "Вертолёт и самолёт послушнее на " + Math.round(30 * k) + "%";
            case PISTOL, SMG, SHOTGUN, RIFLE -> "Разброс −" + Math.round(45 * k) + "% · отдача −" + Math.round(50 * k) + "%"
                    + (s == Skill.SHOTGUN ? "" : " · перезарядка −" + Math.round(30 * k) + "%") + (s == Skill.PISTOL && k >= 1 ? " · на бегу без промаха" : "");
            case STAMINA -> "Бег +" + Math.round(d.stamina * 0.12) + "% · голод от бега −" + Math.round(60 * k) + "% · падение мягче";
            case STRENGTH -> "Удар кулаком и битой +" + String.format(Locale.ROOT, "%.1f", d.strength * 0.03);
            case SWIM -> "В воде быстрее на " + Math.round(55 * k) + "% · воздуха ×" + String.format(Locale.ROOT, "%.1f", 1 + 3 * k);
            case STEALTH -> "Свидетели тихой стрельбы −" + Math.round(50 * k) + "% · звери подпускают ближе";
        };
    }

    private static String how(Skill s) {
        return switch (s) {
            case DRIVE -> "Езди без аварий: каждые полминуты чистой езды — быстрее рост";
            case BIKE -> "Катайся на мотоцикле, прыгай с трамплинов";
            case FLY -> "Летай на вертолёте и самолёте (аэродром, OHDUDE / JUMPJET)";
            case PISTOL -> "Стреляй из пистолета: попадания и победы дают больше всего";
            case SMG -> "Стреляй из ПП и «Микро»";
            case SHOTGUN -> "Стреляй из дробовика и «Сайги»";
            case RIFLE -> "Стреляй из «АК», карабина, пулемёта и снайперки — охота тоже считается";
            case STAMINA -> "Бегай (спринт), плавай; беговая дорожка в зале «Железо»";
            case STRENGTH -> "Дерись врукопашную и битой; штанга в зале «Железо»";
            case SWIM -> "Плавай, ныряй под воду";
            case STEALTH -> "Крадись (Shift) рядом с людьми, стреляй с глушителем без свидетелей";
        };
    }
}
