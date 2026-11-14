package com.yompix.glukhoman.client;

import com.yompix.glukhoman.GContent;
import com.yompix.glukhoman.client.anim.Gestures;
import com.yompix.glukhoman.entity.WatcherEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import static com.yompix.glukhoman.client.Timeline.v;

/**
 * Прототипы новой системы (этап 1–3), только просмотр/запись — в сюжет не встроены:
 * <ul>
 *   <li>{@code proto_npc} (~21 с, день, перрон) — Захар Ильич на новом аниматоре: взгляд, моргание, дыхание, хромота,
 *       остановка, кашель, одёрнуть форму, речь с ртом, показать дорогу.</li>
 *   <li>{@code proto_cut} (~40 с, ночь, дежурка) — «Сдача смены»: общий/средний/крупный/из-за плеча, передача камеры
 *       из рук в руки, три персонажа; приёмы ужаса: стук за кадром и реакция на него, фоновое событие во время
 *       разговора (табло само загорается, хрипит рация), присутствие без скримера (фигура в первом кадре
 *       и в окне — никто не кричит, просто исчезает, пока мигает свет).</li>
 * </ul>
 */
final class ProtoScenes {
    private ProtoScenes() {}

    // ======================= Захар на перроне =======================
    static Timeline npc() {
        return new Timeline(21, tl -> {
            var z = tl.actor("zakhar", "Захар Ильич", v(2.0, 66, 19.2), -120);
            var me = tl.actor("@me", "Сторож", v(7.4, 66, 16.9), 60);
            Vec3d road = v(40, 67.5, 15.5);   // переезд на востоке
            z.look = () -> me.head();
            me.look = () -> z.head();
            me.walk(0.3, 4.6, v(5.4, 66, 17.0), v(3.9, 66, 17.7));   // подходит справа — Захар провожает глазами и головой
            me.face = () -> z.pos;
            z.face = () -> me.pos;
            tl.at(0, () -> z.an.lookSpeed = 5);
            tl.play(5.2, z, Gestures.UNIFORM);
            tl.line(8.0, z, "proto_n1", "Новенький? Ну пошли, покажу.");
            z.walk(9.2, 12.6, v(0.6, 66, 19.3), v(-2.0, 66, 19.0));
            me.walk(9.8, 13.0, v(2.4, 66, 18.0), v(0.5, 66, 18.0));
            tl.at(9.2, () -> z.an.lookAt = null);
            tl.at(12.6, () -> z.an.fatigue = 0.7);   // старик запыхался — дышит чаще, кашляет
            tl.play(13.6, z, Gestures.COUGH);
            tl.line(16.6, z, "proto_n2", "Дорога в посёлок — вон туда, за переезд. Засветло ходи.");
            tl.at(17.4, () -> {
                z.an.pointAt = road;
                z.look = () -> road;
            });
            tl.play(17.4, z, Gestures.point(1.6));
            tl.at(19.2, () -> z.look = () -> me.head());

            tl.dolly(0, 5, z::head, v(5.3, 67.25, 20.55), v(5.0, 67.2, 20.4)).fov(50, 46).shake(0.5).name("средний: взгляд");
            tl.dolly(5, 9.2, z::head, v(2.9, 67.15, 16.8), v(2.7, 67.1, 17.0)).fov(46, 42).shake(0.5).name("средний спереди");
            tl.dolly(9.2, 13.5, z::head, v(3.0, 67.0, 16.5), v(-1.4, 67.0, 16.7)).fov(50, 50).shake(0.7).name("проводка сбоку");
            tl.close(13.5, 16.5, z, -35, 2.1, 1.7, 0.05).fov(45, 45).shake(0.4).name("крупно: кашель");
            tl.dolly(16.5, 21, () -> z.head().add(0.6, -0.2, 0), v(1.0, 67.3, 20.6), v(0.7, 67.25, 20.5)).fov(55, 52).shake(0.5).name("показывает дорогу");
        });
    }

    // ======================= «Сдача смены» в дежурке =======================
    static Timeline handover() {
        BlockPos door = new BlockPos(-6, 66, 21), lamp = new BlockPos(-6, 68, 25), board = new BlockPos(-9, 68, 24);
        Vec3d wall = v(-10.2, 67.2, 25.0), radio = v(-7.5, 67.2, 28.0);
        return new Timeline(40, tl -> {
            var z = tl.actor("zakhar", "Захар Ильич", v(-7.6, 66, 24.5), 90);
            var vika = tl.actor("vika", "Вика", v(-7.4, 66, 27.2), 0);
            var me = tl.actor("@me", "Сторож", v(3.0, 66, 19.4), 90);

            // фигура: в первом кадре — у кромки леса (её никто не замечает), потом — в окне на путях
            final Entity[] fig = new Entity[1];
            tl.at(0, () -> {
                double gx = -21, gz = 12.5;
                fig[0] = Cine.puppet(tl.w, GContent.WATCHER, gx, Cine.ground(tl.w, gx, gz), gz, Cine.yawTo(v(gx, 66, gz), v(-6, 66, 21)));
                Cine.key(tl.w, -5, 68, 23, 11);
                Cine.key(tl.w, -7, 68, 27, 8);
            });
            tl.each.add(t -> {
                Cine.fog(3f, 36f, 0.15f, 0.16f, 0.19f);   // светлый туман — тёмная фигура читается силуэтом
                Cine.crush = 0.25f;
                Cine.tintR = 0.95f;
                Cine.tintB = 1.05f;
                if (fig[0] != null) Cine.act(fig[0], "still", t);
            });
            tl.at(29.5, () -> {   // переставить фигуру на пути — напротив окна
                double gx = -8.9, gz = 16.4;   // на путях напротив окна дежурки (левее переплёта рамы — иначе прячется за ним)
                int gy = Cine.ground(tl.w, gx, gz);
                Cine.place(fig[0], gx, gy, gz, Cine.yawTo(v(gx, 66, gz), v(-7.5, 66, 21)));
                Cine.key(tl.w, (int) Math.floor(gx), gy + 2, (int) Math.floor(gz) + 2, 12);   // чуть света — силуэт читается в тумане
            });

            // 0–5.8: общий план снаружи; сторож идёт по перрону к двери
            me.walk(0.4, 6.9, v(-4.6, 66, 19.5), v(-5.5, 66, 20.4), v(-5.5, 66, 21.7), v(-5.4, 66, 23.2), v(-5.2, 66, 24.4));
            me.look = () -> z.head();
            me.face = () -> z.pos;
            // Вика возится с рацией (фон), на скрип двери отвлекается
            tl.at(0, () -> vika.e.act("rummage"));
            tl.at(5.9, () -> vika.e.act(""));
            tl.at(16.0, () -> vika.e.act("rummage"));
            tl.at(24.6, () -> vika.e.act(""));
            tl.sound(0.2, "wind_gust", v(0, 70, 14), 0.6f);
            tl.door(5.3, door, true, true);
            // Захар у табло; на скрип — сначала глаза, потом голова, потом корпус
            z.look = () -> v(-9.5, 67.8, 24.5);
            tl.at(5.6, () -> z.look = () -> me.head());
            tl.at(6.3, () -> z.face = () -> me.pos);
            vika.look = () -> radio;
            vika.face = () -> radio;
            tl.at(6.0, () -> vika.look = () -> me.head());
            tl.at(8.2, () -> vika.look = () -> radio);
            tl.line(7.0, z, "proto_c1", "Пришёл, сторож. Дверь прикрой — дует.");
            tl.door(9.4, door, false, true);

            // 10–15: из-за плеча; одёрнул форму, говорит; фоном — табло загорается само
            tl.play(10.0, z, Gestures.UNIFORM);
            tl.line(11.6, z, "proto_c2", "Смену сдаю. Табло само знает, где поезд. Ты только смотри.");
            tl.lamp(13.0, board, true);
            tl.sound(13.0, "relay", Vec3d.ofCenter(board), 0.9f);
            tl.at(13.15, () -> z.look = () -> Vec3d.ofCenter(board));
            tl.at(14.0, () -> z.look = () -> me.head());

            // 15–20: крупно; кашель, главное
            tl.play(15.0, z, Gestures.COUGH);
            tl.line(17.4, z, "proto_c3", "Камеру держи при себе. Чего глазами не видно — плёнка видит.");

            // 20–24.5: передача камеры из рук в руки; фоном хрипит рация (Вика крутит ручку)
            tl.at(19.4, () -> z.an.held = new ItemStack(GContent.CAMCORDER));
            tl.play(19.6, z, Gestures.GIVE);
            tl.sound(19.8, "radio_static", radio, 0.35f);
            tl.play(20.9, me, Gestures.TAKE);
            tl.at(21.7, () -> {
                z.an.held = ItemStack.EMPTY;
                me.an.held = new ItemStack(GContent.CAMCORDER);
            });
            tl.stop(22.2, z, "give");
            tl.line(22.9, z, "proto_c4", "Ну всё. Теперь ты здесь дежурный, а я…");

            // 24.6: стук в глухую стену за кадром — все вздрагивают и оборачиваются; рация смолкла
            for (double k : new double[]{24.6, 25.0, 25.45}) tl.sound(k, "knock", wall, 1.0f);
            tl.at(24.65, () -> {
                z.an.say("");
                z.an.startle(wall);
                z.look = () -> wall;
                vika.an.startle(wall);
                vika.look = () -> wall;
                me.an.startle(wall);
                me.look = () -> wall;
                me.face = () -> wall;
            });
            tl.lamp(25.7, lamp, false);
            tl.lamp(25.85, lamp, true);
            tl.lamp(26.2, lamp, false);
            tl.lamp(26.5, lamp, true);
            tl.sound(25.7, "lamp_buzz", Vec3d.ofCenter(lamp), 0.6f);
            tl.line(27.8, z, "proto_c5", "Не открывай. Это не к нам.");

            // 30–35.6: Вика идёт к окну, наклоняется — на путях стоит фигура; свет мигает — её нет
            vika.walk(29.9, 32.2, v(-6.6, 66, 26.2), v(-6.9, 66, 23.6), v(-8.55, 66, 22.45));   // встаёт сбоку от окна и выглядывает
            tl.at(30.0, () -> vika.look = () -> v(-8.0, 67.5, 16.4));
            tl.at(31.7, () -> vika.face = () -> v(-8.0, 66, 16.4));
            tl.play(31.9, vika, Gestures.LEAN);
            tl.line(32.6, vika, "proto_c6", "Захар Ильич… там на путях кто-то стоит.");
            tl.lamp(34.3, lamp, false);
            tl.at(34.6, () -> Cine.place(fig[0], -60, 40, -60, 0));
            tl.lamp(34.95, lamp, true);
            tl.sound(34.3, "lamp_buzz", Vec3d.ofCenter(lamp), 0.5f);

            // 35.6–40: Захар не оборачивается; сторож поднимает камеру
            tl.at(35.4, () -> {
                z.look = () -> me.head();
                z.face = () -> me.pos;
                me.look = () -> v(-8.0, 67.5, 16.4);
            });
            tl.line(35.9, z, "proto_c7", "Не смотри на него. Снимай.");
            tl.play(37.4, me, Gestures.RAISE);
            tl.stop(37.0, vika, "lean");
            tl.sound(38.4, "cam_motor", null, 0.3f);

            // ---- планы ----
            tl.dolly(0, 5.8, () -> v(-6, 67.3, 21), v(7.0, 66.9, 15.2), v(5.2, 66.8, 15.9)).fov(62, 58).shake(0.8).name("общий: станция ночью");
            tl.dolly(5.8, 10.0, () -> v(-5.6, 67.1, 22.2), v(-4.3, 67.55, 27.6), v(-4.35, 67.5, 27.2)).fov(68, 64).shake(0.5).name("средний: дверь");
            tl.ots(10.0, 15.0, me, z, 1.8, 1.55, 0.15).fov(50, 47).shake(0.5).name("из-за плеча сторожа");
            tl.close(15.0, 20.0, z, 22, 1.45, 1.05, 0.02).fov(48, 46).shake(0.35).name("крупно: Захар");
            tl.dolly(20.0, 24.5, () -> z.head().add(me.head()).multiply(0.5).add(0, -0.25, 0), v(-6.35, 67.4, 27.1), v(-6.25, 67.35, 26.8))
                    .fov(58, 56).shake(0.45).name("двое: передача камеры");
            tl.dolly(24.5, 27.6, () -> v(-8.0, 67.0, 24.6), v(-4.25, 68.1, 28.35), v(-4.3, 68.0, 28.2)).fov(66, 66).shake(0.9).name("общий: стук");
            tl.close(27.6, 30.0, z, -38, 1.25, 1.05, 0.0).fov(46, 44).shake(0.3).name("крупно: шёпот");
            tl.dolly(30.0, 35.6, () -> v(-8.0, 67.35, 16.4), v(-6.85, 67.7, 24.6), v(-6.9, 67.66, 24.4)).fov(46, 26).shake(0.35).name("из-за плеча Вики: окно");
            tl.ots(35.6, 40.0, z, me, -1.9, 1.3, 0.1).fov(50, 46).shake(0.45).name("из-за плеча Захара");
        });
    }
}
