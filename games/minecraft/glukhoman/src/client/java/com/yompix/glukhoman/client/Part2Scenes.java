package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.client.anim.Gestures;
import com.yompix.glukhoman.entity.TrainEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static com.yompix.glukhoman.client.Timeline.v;

/** Кат-сцены части второй (0.6) на режиссёре Timeline + сцены для роликов глав II и III. */
final class Part2Scenes {
    private Part2Scenes() {}

    static final int NX = 422, NZ = 4;   // нарядная на 41-м км (Part2Map: CAMP_X + 26, 4)

    /** Надпись в мире только у этого клиента (табличка «40» в последнем окне и т. п.). */
    static Entity label(String text, Vec3d at, float yaw) {
        return label(text, at, yaw, 1f);
    }

    static Entity label(String text, Vec3d at, float yaw, float scale) {
        var w = net.minecraft.client.MinecraftClient.getInstance().world;
        DisplayEntity.TextDisplayEntity t = EntityType.TEXT_DISPLAY.create(w);
        if (t == null) return null;
        var nbt = new net.minecraft.nbt.NbtCompound();
        t.writeNbt(nbt);
        nbt.putString("text", Text.Serialization.toJsonString(Text.literal(text), w.getRegistryManager()));
        nbt.putString("billboard", "fixed");
        nbt.putInt("background", 0xC0E8E0C8);
        if (scale != 1f) {
            var tr = new net.minecraft.nbt.NbtCompound();
            tr.put("left_rotation", floats(0, 0, 0, 1));
            tr.put("right_rotation", floats(0, 0, 0, 1));
            tr.put("translation", floats(0, 0, 0));
            tr.put("scale", floats(scale, scale, scale));
            nbt.put("transformation", tr);
        }
        t.readNbt(nbt);
        t.setId(Cine.nextId--);
        t.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
        w.addEntity(t);
        Cine.PUPPETS.add(t);
        return t;
    }

    static net.minecraft.nbt.NbtList floats(float... f) {
        var l = new net.minecraft.nbt.NbtList();
        for (float x : f) l.add(net.minecraft.nbt.NbtFloat.of(x));
        return l;
    }

    // ======================= глава II: Гена приехал (24 с) =======================
    static Timeline gena(boolean ok) {
        String p = ok ? "c2_gena_ok_" : "c2_gena_";
        String[] ru = ok ? new String[]{"Лёха! Живой. Молодец, кассеты сдаёшь — Москва довольна.", "Сдавай и дальше. Каждое утро. Это главное, понял? Главное.",
                "Захар тебе ничего не рассказывал? Вот и не слушай. Старый он."}
                : new String[]{"Лёха! Живой. Ну и рожа у тебя… Ничего, ничего.", "Ты чего не сдал? Без батарей сидеть будешь. Мне Москва без плёнки — ни копейки.",
                "Захар тебе ничего не рассказывал? Вот и не слушай. Старый он."};
        return new Timeline(24, tl -> {
            var g = tl.actor("gena", "Гена", v(22, 66, 19.5), 90);
            var me = tl.actor("@me", "Сторож", v(1.6, 66, 19.3), -90);
            var z = tl.actor("zakhar", "Захар Ильич", v(-7.6, 66, 22.5), 180);
            tl.at(0, () -> {
                tl.hideReal();
                Cine.key(tl.w, 2, 68, 20, 10);
            });
            g.walk(0.3, 7.0, v(14, 66, 19.6), v(5.0, 66, 19.6));
            g.look = () -> me.head();
            me.look = () -> g.head();
            me.face = () -> g.pos;
            z.look = () -> g.head();   // Захар смотрит из окна дежурки — Гена его не видит
            tl.at(7.0, () -> g.face = () -> me.pos);
            tl.play(7.2, g, Gestures.WATCH);
            tl.line(7.6, g, p + "1", ru[0]);
            tl.line(12.2, g, p + "2", ru[1]);
            tl.play(13.4, g, ok ? Gestures.GLANCE : Gestures.REACH);
            tl.at(16.6, () -> {   // ощущает взгляд — резко к окну дежурки
                g.an.startle(v(-8, 67.4, 21));
                g.look = () -> v(-8, 67.4, 21);
            });
            tl.at(18.4, () -> g.look = () -> me.head());
            tl.line(18.6, g, p + "3", ru[2]);
            tl.dolly(0, 7.0, () -> g.head().add(0, -0.3, 0), v(15, 67.3, 15.4), v(8.5, 67.3, 15.6)).fov(56, 50).shake(0.5);
            tl.ots(7.0, 12.0, me, g, 1.8, 1.5, 0.15).fov(50, 46).shake(0.45);
            tl.close(12.0, 16.6, g, 20, 1.5, 1.1, 0.02).fov(48, 44).shake(0.35);
            tl.dolly(16.6, 19.6, () -> z.head(), v(-6.3, 67.6, 18.3), v(-6.6, 67.6, 18.6)).fov(40, 34).shake(0.3);
            tl.dolly(19.6, 24, () -> g.head().add(me.head()).multiply(0.5), v(3.2, 67.4, 15.6), v(3.0, 67.4, 15.8)).fov(54, 52).shake(0.45);
        });
    }

    // ======================= ночь 3: «Проход поезда» (16 с, одним планом в видоискателе) =======================
    static Timeline pass() {
        return new Timeline(16, tl -> {
            final Entity[] tr = new Entity[1];
            final Entity[] forty = new Entity[1];
            tl.at(0, () -> {
                Cine.vf = true;
                tr[0] = Cine.puppet(tl.w, GContent.TRAIN, 60, TrainEntity.Y, 16.5, 90);
                Viewfinder.ghostRails(tl.w, true);
                for (int x = -8; x <= 20; x += 7) Cine.key(tl.w, x, 68, 18, 11);   // фонари перрона: лица в окнах читаются
                forty[0] = label("40", v(60, 68.0, 17.35), 0);
            });
            tl.each.add(t -> {
                StoryScenes.nightFx(2, 34);
                double tx = 44 - 4.2 * t;
                Cine.place(tr[0], tx, TrainEntity.Y, 16.5, 90);
                Cine.act(tr[0], "idle", t);
                if (forty[0] != null) Cine.place(forty[0], tx + 27.5, 67.9, 17.42, 0);   // последнее окно — пустое, табличка «40»
                Cine.glitch = (float) (0.1 + (t > 13 ? 0.35 : 0));
                Cine.motor(t);
            });
            tl.shot(0, 16, (u, t) -> {
                double tx = 44 - 4.2 * t;
                Vec3d p = v(2.2, 67.55, 18.7);
                double lookX = MathHelper.lerp(Cine.ease(Cine.seg(t, 0, 16)), 6, tx + 27);
                return new Vec3d[]{p, v(Math.max(-8, Math.min(20, lookX)), 67.6, 16.5)};
            }).fov(42, 34).shake(0.45);
        });
    }

    // ======================= глава III: «Нарядная» (22 с) =======================
    static Timeline naryad() {
        return new Timeline(22, tl -> {
            var me = tl.actor("@me", "Сторож", v(-60, 40, -60), 180);   // камера — его глаза; сам в кадре не нужен
            var wt = tl.creature(GContent.WATCHER, Gestures.WATCHER, "", v(NX + 4.5, 66, NZ + 8.5), 180);
            wt.pos = v(-60, 40, -60);
            tl.at(0, () -> {
                Cine.vf = true;
                String[] lab = {"ПЕЛАГЕЯ Т. — 38", "ГЛОТОВ В. — 37", "ЗИНАИДА Г. — 36", "ВИКТОРИЯ С. — 35", "— 40 —"};
                for (int i = 0; i < lab.length; i++) label(lab[i], v(NX + 1.6 + i * 1.55, 67.75, NZ + 2.02), 0, 0.42f);
                var mc = net.minecraft.client.MinecraftClient.getInstance();
                String nick = mc.player != null ? mc.player.getName().getString().toUpperCase() : "СТОРОЖ";
                label(nick + " — 1", v(NX + 6.4, 67.15, NZ + 2.02), 0, 0.5f);
                Cine.key(tl.w, NX + 4, 67, NZ + 4, 7);
            });
            tl.each.add(t -> {
                StoryScenes.nightFx(1.5, 18);
                Cine.cone = 0.55f;
                Vec3d from = v(MathHelper.lerp(Cine.ease(Cine.seg(t, 0, 14)), NX + 1.5, NX + 8.0), 67.5, NZ + 4.2);
                Cine.beam(tl.w, from, v(0, -0.15, -1).normalize(), true);   // фонарь выхватывает наклейки
            });
            tl.line(5.0, me, "c3_naryad_1", "Это… мои кассеты. Все, что я сдавал. «Пелагея Т. — 38». «Глотов В. — 37».");
            tl.line(15.6, me, "c3_naryad_2", "А эта… свежая. Моё имя. Место — один. Я её не сдавал.");
            tl.sound(14.0, "relay", v(NX + 6, 67, NZ + 2), 0.6f);
            tl.at(18.6, () -> {   // в дверях — он. Просто стоит.
                wt.pos = v(NX + 4.5, 66, NZ + 8.6);
                wt.face = Timeline::camPos;
                wt.look = Timeline::camPos;
                wt.an.base("w_observe", 6);
            });
            tl.sound(18.6, "breath_close", null, 0.4f);
            tl.door(18.2, new net.minecraft.util.math.BlockPos(NX + 4, 66, NZ + 7), true, true);   // дверь сама открылась
            tl.dolly(0, 14, () -> v(MathHelper.lerp(Cine.ease(Cine.seg(tl.t, 0, 14)), NX + 1.5, NX + 8.5), 67.6, NZ + 1.6),
                    v(NX + 1.6, 67.6, NZ + 4.4), v(NX + 8.2, 67.5, NZ + 4.4)).fov(48, 44).shake(0.5);
            tl.dolly(14, 18.6, () -> v(NX + 6.5, 67.4, NZ + 2.0), v(NX + 6.2, 67.5, NZ + 4.0), v(NX + 6.3, 67.4, NZ + 3.3)).fov(40, 26).shake(0.4);
            tl.dolly(18.6, 22, () -> v(NX + 4.5, 68.0, NZ + 8.6), v(NX + 5.0, 67.5, NZ + 3.6), v(NX + 5.0, 67.5, NZ + 3.4)).fov(50, 50).shake(0.6);
        });
    }

    // ======================= глава III: кассета Саввы, 1991 (16 с) =======================
    static Timeline savva() {
        return new Timeline(16, tl -> {
            var zi = tl.actor("zina", "Зина", v(4, 66, 19.4), 0);
            var pe = tl.actor("petrovich", "Петрович", v(4, 66, 19.4), 0);
            var za = tl.actor("zakhar", "Захар Ильич", v(4, 66, 19.4), 0);
            var me = tl.actor("@me", "", v(-60, 40, -60), 0);
            Vec3d cam = v(4, 67.62, 17.2);
            for (var a : new Timeline.Actor[]{zi, pe, za}) {
                a.look = () -> cam;
                a.face = () -> cam;
            }
            tl.at(0, () -> {
                Cine.vf = true;
                Cine.replay = true;
                Cine.key(tl.w, 4, 68, 18, 13);   // свет на лица — «камера с подсветкой»
                Cine.key(tl.w, 4, 67, 21, 9);
            });
            tl.each.add(t -> {
                Cine.tintR = 1.12f;
                Cine.tintG = 1.0f;
                Cine.tintB = 0.82f;   // старая плёнка
                Cine.glitch = (float) (0.15 + 0.6 * Math.max(0, Math.sin(t * 3.1)) * (t % 3.4 < 0.15 ? 1 : 0));
                zi.pos = t < 4.4 ? v(4, 66, 19.4) : v(-60, 40, -60);
                pe.pos = t >= 4.4 && t < 7.8 ? v(4, 66, 19.4) : v(-60, 40, -60);
                za.pos = t >= 7.8 && t < 11.2 ? v(4, 66, 19.4) : v(-60, 40, -60);
                if (t > 11.2) Cine.tintR = Cine.tintG = Cine.tintB = (float) (1.0 - 0.5 * Cine.seg(t, 11.2, 16));
            });
            tl.line(0.4, me, "c3_savva_1", "Кассета Саввы. Девяносто первый. Все жители. По одному.");
            tl.line(2.6, zi, "c3_savva_2", "Я тебя запомнил.");
            tl.line(6.0, pe, "c3_savva_3", "Я тебя запомнил.");
            tl.line(9.4, za, "c3_savva_4", "Я тебя запомнил.");
            tl.line(12.0, me, "c3_savva_5", "А его самого — ни кадра. Только тень. Длиннее… длиннее…");
            tl.shot(0, 11.2, (u, t) -> new Vec3d[]{cam, v(4, 67.55, 19.4)}).fov(38, 38).shake(0.3);
            tl.shot(11.2, 16, (u, t) -> new Vec3d[]{v(4, 67.4, 17.2), v(4, 65.2 + 0.0 * t, 22 + (t - 11.2) * 1.6)}).fov(60, 60).shake(0.2);
        });
    }

    // ======================= ролики: лучшие моменты глав II и III =======================
    static Timeline demoCh2() {
        return new Timeline(34, tl -> {
            var wt = tl.creature(GContent.WATCHER, Gestures.WATCHER, "", v(124.5, 66.06, 16.5), 90);
            var n1 = tl.actor("nameless", "", v(-12, 65, 37), 90);
            var n2 = tl.actor("nameless", "", v(-14, 65, 39.5), 90);
            var zina = tl.actor("zina", "", v(-60, 40, -60), 180);
            final Entity[] tr = new Entity[1];
            final boolean[] irWas = {false};
            tl.at(0, () -> {
                irWas[0] = Viewfinder.ir;
                Viewfinder.ir = true;
                Cine.vf = true;
                tr[0] = Cine.puppet(tl.w, GContent.TRAIN, -60, TrainEntity.Y, 16.5, 90);
                wt.an.base("w_observe", 14);
            });
            tl.cleanup.add(() -> Viewfinder.ir = irWas[0]);
            tl.each.add(t -> {
                if (t < 14) {
                    Cine.fog(6f, 70f, 0.32f, 0.3f, 0.32f);
                    Cine.vf = true;
                    Viewfinder.ir = true;
                } else {
                    StoryScenes.nightFx(2, 30);
                    Viewfinder.ir = false;
                    Cine.vf = t >= 22;
                }
                if (tr[0] != null) Cine.place(tr[0], t >= 22 ? 8 : -200, TrainEntity.Y, 16.5, 90);
                zina.pos = t >= 22 ? v(4.4, 66.4, 16.7) : v(-60, 40, -60);
            });
            for (var n : new Timeline.Actor[]{n1, n2}) {
                n.look = Timeline::camPos;
                n.face = Timeline::camPos;
            }
            wt.look = Timeline::camPos;
            tl.line(4.6, wt, "c2_name_1", "Гринюк Семён Петрович. Тридцать восемь.");
            tl.sound(4.5, "loudspeaker", v(14, 70, 20), 1.0f);
            tl.line(9.8, wt, "c2_name_false", "Глотов Виктор Павлович. Тридцать семь.");
            tl.sound(9.7, "loudspeaker", v(14, 70, 20), 1.0f);
            tl.sound(15.0, "wind_gust", v(-10, 70, 38), 0.6f);
            tl.at(22.4, () -> zina.e.setInvisible(false));
            tl.at(22.6, () -> ((com.yompix.glukhoman.entity.ResidentEntity) zina.e).act("slowwave"));
            tl.line(25.4, zina, "n3_ghost", "Зина?.. Она же дома. Спит. Почему она там сидит?");
            tl.dolly(0, 14, () -> v(MathHelper.lerp(Cine.ease(Cine.seg(tl.t, 0, 13)), 76, 86), 66.2, 16.5), v(74.5, 67.4, 18.6), v(84.5, 67.4, 18.6)).fov(46, 40).shake(0.5);
            tl.dolly(14, 22, () -> v(-13, 66.5, 38), v(-4, 67.0, 36.5), v(-5.5, 66.9, 37.3)).fov(52, 44).shake(0.6);
            tl.dolly(22, 34, () -> v(4.4, 67.6, 16.7), v(4.6, 67.6, 19.4), v(4.5, 67.6, 18.9)).fov(46, 26).shake(0.35);
        });
    }

    static Timeline demoCh3() {
        return new Timeline(34, tl -> {
            var wt = tl.creature(GContent.WATCHER, Gestures.WATCHER, "", v(-60, 40, -60), -90);
            final Entity[] cart = new Entity[1];
            tl.at(0, () -> {
                cart[0] = Cine.puppet(tl.w, GContent.HANDCAR, 300, 66.12, 16.5, -90);
                Cine.key(tl.w, CAMP_W + 31, 74, 33, 12);   // фонарь на вышке
                Cine.key(tl.w, CAMP_W + 12, 67, 21, 9);    // у барака
                Cine.key(tl.w, CAMP_W + 30, 67, 14, 9);    // у нарядной
            });
            tl.each.add(t -> {
                StoryScenes.nightFx(1.2, 16);
                if (t >= 22) {   // 41-й км: туман светлее — силуэты барака, вышки и его самого читаются
                    Cine.fog(4f, 60f, 0.15f, 0.16f, 0.19f);
                    Cine.crush = 0.2f;
                }
                Cine.cone = 0.35f;
                Cine.flash = t >= 22 && t < 22.25 ? 0.5f : 0f;
                double x = t < 8 ? 300 + 4.0 * t : t < 13 ? 332 : t < 22 ? 332 + 5.2 * (t - 13) : 389 + Math.min(6, (t - 22) * 2);
                if (cart[0] != null) Cine.place(cart[0], x, 66.12, 16.5, -90);
                if (cart[0] instanceof com.yompix.glukhoman.entity.HandcarEntity hc) hc.pump(t < 8 || (t >= 13 && t < 22) ? 4f : 0f);
                if (t >= 8 && t < 13) wt.pos = v(332 - 22 + (t - 9) * 1.2, 66.06, 16.5);
                else if (t >= 26) wt.pos = v(CAMP_W + 16, 66, 18.5);
                else wt.pos = v(-60, 40, -60);
            });
            wt.face = Timeline::camPos;
            wt.look = Timeline::camPos;
            for (double k : new double[]{1.5, 4.5, 7.0}) tl.sound(k, "joints", v(308 + k * 4, 67, 16.5), 0.8f);
            tl.sound(9.2, "joints", v(316, 67, 16.5), 1.1f);   // стук сзади — рельсов там нет
            tl.line(9.4, wt, "c3_behind", "Стук. Сзади. Там же нет рельсов…");
            tl.line(14.0, wt, "c3_58", "Пятьдесят восьмой. Не останавливаться. Не останавливаться.");
            tl.line(23.5, wt, "c3_camp", "Сорок первый километр. Барак. Вышка. Нарядная…");
            tl.at(26.0, () -> wt.an.base("w_unfold", 2.4));
            tl.shot(0, 8, (u, t) -> new Vec3d[]{v(300 + 4.0 * t, 67.55, 16.5), v(330 + 4.0 * t, 67.3, 16.5)}).fov(70, 70).shake(0.9);
            tl.shot(8, 13, (u, t) -> {   // остановился — обернулся на стук
                double k = 1 - Math.pow(1 - Cine.seg(t, 8.9, 9.35), 3);
                return new Vec3d[]{v(332, 67.55, 16.5), v(362, 67.3, 16.5).lerp(v(302, 67.6, 16.5), k)};
            }).fov(70, 70).shake(0.7);
            tl.shot(13, 22, (u, t) -> new Vec3d[]{v(332 + 5.2 * (t - 13), 67.55, 16.5), v(362 + 5.2 * (t - 13), 67.3, 16.5)}).fov(72, 72).shake(1.0);
            tl.shot(22, 34, (u, t) -> new Vec3d[]{v(CAMP_W + 2 + Math.min(4, (t - 22)), 67.6, 15.5),
                    v(CAMP_W + 14, 67.4, 25).lerp(v(CAMP_W + 16, 68.2, 18.5), Cine.ease(Cine.seg(t, 25.5, 28)))}).fov(66, 54).shake(0.6);
        });
    }

    static final int CAMP_W = 396;
}
