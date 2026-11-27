package com.yompix.novograd.client;

import com.yompix.novograd.ModContent;
import com.yompix.novograd.Novograd;
import com.yompix.novograd.life.HandKind;
import com.yompix.novograd.life.Hands;
import com.yompix.novograd.life.Life;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

import static com.yompix.novograd.client.ShowAutotest.*;

/**
 * 0.9.8, только разработка: {@code -Dnovograd.autotest=handshow} — витрина «Рук»: банкомат (карта, PIN, деньги),
 * автомат (банка и чипсы), ларёк, касса, дверь, колонка и ремонт, сейф, сигарета и бутылка, перезарядки пяти стволов,
 * бита/нож/кулак — от третьего лица (камера сбоку) и от первого. Снимки — screenshots/show/hand_*.png, ролик —
 * -Dnovograd.rec=файл.mp4. В конце в логе: «[hands] done/cancel» (сколько действий кончилось и прервалось).
 */
public final class HandShow {
    private HandShow() {}

    private static BlockPos atm, atmWall, vend, cart, door;

    static void handshow() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            base = studio(p);
            var w = p.getServerWorld();
            BlockPos b = BlockPos.ofFloored(base);
            atm = b.add(0, 0, -3);
            atmWall = b.add(-7, 0, -3);
            vend = b.add(4, 0, -3);
            cart = b.add(8, 0, -3);
            door = b.add(-4, 0, -3);
            w.setBlockState(atm, ModContent.ATM.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            for (int dx = -1; dx <= 1; dx++) for (int y = 0; y < 3; y++) w.setBlockState(atmWall.add(dx, y, -1 + (dx == 0 ? 0 : 1)), Blocks.POLISHED_ANDESITE.getDefaultState());
            w.setBlockState(atmWall, ModContent.ATM_WALL.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            w.setBlockState(atmWall.up(2), Blocks.POLISHED_ANDESITE.getDefaultState());
            w.setBlockState(vend, com.yompix.novograd.world.Props.VENDING.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            w.setBlockState(cart, com.yompix.novograd.world.Props.HOTDOG_CART.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            w.setBlockState(door, Blocks.OAK_DOOR.getDefaultState());
            w.setBlockState(door.up(), Blocks.OAK_DOOR.getDefaultState().with(net.minecraft.block.DoorBlock.HALF, net.minecraft.block.enums.DoubleBlockHalf.UPPER));
            var d = Life.get(p);
            d.money = 5000;
            d.cash = 300;
            d.storyStarted = true;
            d.storyDone = true;
            d.look = com.yompix.novograd.life.LookGen.generate(31, true, com.yompix.novograd.life.LookGen.CASUAL, false);
            d.look.hair = "ponytail";
            d.look.hat = "";
            d.look.glasses = "";
            d.look.bag = "";
            d.look.sanitize();
            Life.sync(p);
            com.yompix.novograd.life.Style.broadcast(p);
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
        });
        run(100, c -> {
            if (c.currentScreen != null) c.setScreen(null);
            c.options.hudHidden = true;
        });
        run(40, ShowAutotest::rec);
        // ---------------- банкомат: третье лицо, потом первое
        station(() -> atm, 0.5, 2.3, "atm");
        click(10, () -> atm);
        shotAt(14, "hand_atm_card");
        shotAt(14, "hand_atm_pin");
        run(26, c -> { if (c.currentScreen != null) shot(c, "hand_atm_menu"); });
        run(6, c -> {
            if (c.currentScreen instanceof com.yompix.novograd.client.screen.AtmScreen a) a.pressWithdraw(250);
        });
        shotAt(24, "hand_atm_cash");
        shotAt(18, "hand_atm_count");
        run(30, c -> {});
        firstPerson(() -> atm, "atm_fp");
        click(8, () -> atm);
        shotAt(18, "hand_fp_atm_card");
        shotAt(14, "hand_fp_atm_pin");
        run(20, c -> { if (c.currentScreen != null) c.currentScreen.close(); });
        shotAt(10, "hand_fp_atm_takecard");
        run(30, c -> thirdPerson(c));
        // настенный банкомат
        station(() -> atmWall, 0.6, -2.3, "atm_wall");
        click(10, () -> atmWall);
        shotAt(30, "hand_atm_wall");
        run(30, c -> { if (c.currentScreen != null) c.currentScreen.close(); });
        run(30, c -> {});
        // ---------------- автомат: банка; потом чипсы (Shift)
        station(() -> vend, 0.5, 2.3, "vend");
        click(10, () -> vend);
        shotAt(16, "hand_vend_coin");
        shotAt(30, "hand_vend_pick");
        shotAt(30, "hand_vend_open");
        shotAt(30, "hand_vend_drink");
        run(50, c -> {});
        firstPerson(() -> vend, "vend_fp");
        run(2, c -> c.player.setSneaking(true));
        server(1, s -> pl(s).setSneaking(true));
        click(4, () -> vend);
        server(2, s -> pl(s).setSneaking(false));
        run(1, c -> c.player.setSneaking(false));
        shotAt(48, "hand_fp_vend_tray");
        shotAt(40, "hand_fp_chips");
        run(70, c -> thirdPerson(c));
        // ---------------- ларёк
        station(() -> cart, 0.6, 2.4, "stall");
        click(10, () -> cart);
        shotAt(24, "hand_stall_pay");
        shotAt(26, "hand_stall_take");
        shotAt(24, "hand_stall_eat");
        run(60, c -> {});
        // ---------------- дверь, касса, колонка, ремонт, сейф, сигарета, бутылка (на месте)
        station(() -> door, 0.6, 2.2, "door");
        click(8, () -> door);
        shotAt(5, "hand_door");
        run(20, c -> {});
        for (Object[] k : new Object[][]{{HandKind.CHECKOUT, "card", "hand_checkout", 30}, {HandKind.CHECKOUT, "cash", "hand_checkout_cash", 30},
                {HandKind.FUEL, "", "hand_fuel", 60}, {HandKind.REPAIR, "", "hand_repair", 60}, {HandKind.SAFE, "1", "hand_safe", 30},
                {HandKind.SMOKE, "", "hand_smoke", 64}, {HandKind.DRINK, "beer", "hand_drink", 30}}) {
            HandKind kind = (HandKind) k[0];
            server(4, s -> {
                ServerPlayerEntity p = pl(s);
                p.teleport(p.getServerWorld(), base.x + 0.5, base.y, base.z + 3.5, 180, 10);
                Hands.start(p, kind, (String) k[1], null, null);
            });
            run(3, c -> aim(c, new Vec3d(base.x + 0.5, base.y, base.z + 3.5), -2.4, 1.1));
            shotAt((Integer) k[3], (String) k[2]);
            run(kind.len - (Integer) k[3] + 6, c -> {});
        }
        // ---------------- оружие: перезарядки (третье лицо), бита, нож, кулак
        for (String gun : new String[]{"pistol", "smg", "rifle", "shotgun", "sniper"}) {
            server(4, s -> {
                ServerPlayerEntity p = pl(s);
                p.getInventory().clear();
                var item = net.minecraft.registry.Registries.ITEM.get(Novograd.id(gun));
                ItemStack st = new ItemStack(item);
                st.set(com.yompix.novograd.gta.Arsenal.MAG, 0);
                p.getInventory().setStack(0, st);
                p.getInventory().setStack(1, new ItemStack(ModContent.AMMO, 64));
                p.getInventory().selectedSlot = 0;
                p.teleport(p.getServerWorld(), base.x + 0.5, base.y, base.z + 3.5, 160, 0);
            });
            run(14, c -> aim(c, new Vec3d(base.x + 0.5, base.y, base.z + 3.5), -2.2, 1.0));
            server(2, s -> {
                ServerPlayerEntity p = pl(s);
                if (p.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem g) g.reload(p.getServerWorld(), p, p.getMainHandStack(), true);
            });
            shotAt(gun.equals("shotgun") ? 24 : 14, "hand_reload_" + gun);
            run(gun.equals("shotgun") ? 70 : 40, c -> {});
        }
        for (String m : new String[]{"bat", "knife", ""}) {
            server(4, s -> {
                ServerPlayerEntity p = pl(s);
                p.getInventory().clear();
                if (!m.isEmpty()) p.getInventory().setStack(0, new ItemStack(net.minecraft.registry.Registries.ITEM.get(Novograd.id(m))));
            });
            run(8, c -> c.player.swingHand(Hand.MAIN_HAND));
            shotAt(3, "hand_melee_" + (m.isEmpty() ? "fist" : m));
            run(12, c -> c.player.swingHand(Hand.MAIN_HAND));
            run(14, c -> {});
        }
        // первое лицо: пистолет и дробовик
        for (String gun : new String[]{"pistol", "shotgun"}) {
            server(4, s -> {
                ServerPlayerEntity p = pl(s);
                var st = new ItemStack(net.minecraft.registry.Registries.ITEM.get(Novograd.id(gun)));
                st.set(com.yompix.novograd.gta.Arsenal.MAG, 0);
                p.getInventory().setStack(0, st);
                p.getInventory().setStack(1, new ItemStack(ModContent.AMMO, 64));
            });
            run(4, c -> {
                CAM = null;
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.options.hudHidden = false;
            });
            server(10, s -> {
                ServerPlayerEntity p = pl(s);
                if (p.getMainHandStack().getItem() instanceof com.yompix.novograd.item.GunItem g) g.reload(p.getServerWorld(), p, p.getMainHandStack(), true);
            });
            shotAt(12, "hand_fp_reload_" + gun);
            run(60, c -> {});
        }
        run(10, c -> {
            CAM = null;
            Novograd.LOG.info("[hands] done {} cancel {}", Hands.doneCount, Hands.cancelCount);
        });
        finish();
    }

    // ================================================================== походка: игрок и жители (до/после — -Dnovograd.oldgait)
    static void gaitshow() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        java.util.List<UUID> crowd = new java.util.ArrayList<>();
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            base = studio(p);
            var w = p.getServerWorld();
            BlockPos b = BlockPos.ofFloored(base);
            // лестница: 4 ступени вверх по +x на дорожке z = b.z+2
            for (int i = 0; i < 4; i++)
                for (int j = 0; j <= i; j++) w.setBlockState(b.add(8 + i, j, 2), j == i ? Blocks.SMOOTH_QUARTZ_STAIRS.getDefaultState()
                        .with(net.minecraft.block.StairsBlock.FACING, Direction.EAST) : Blocks.SMOOTH_QUARTZ.getDefaultState());
            for (int i = 0; i < 6; i++) w.setBlockState(b.add(12 + i, 3, 2), Blocks.SMOOTH_QUARTZ.getDefaultState());
            var d = Life.get(p);
            d.look = com.yompix.novograd.life.LookGen.generate(31, true, com.yompix.novograd.life.LookGen.CASUAL, false);
            d.look.hair = "ponytail";
            d.look.hat = "";
            d.look.sanitize();
            Life.sync(p);
            com.yompix.novograd.life.Style.broadcast(p);
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            p.getAbilities().flying = false;
            p.sendAbilitiesUpdate();
            // толпа: 8 жителей стоят (дыхание, вес, взгляд)
            for (int i = 0; i < 8; i++) {
                var c = com.yompix.novograd.gta.FunUtil.citizen(w, base.add(-6 + (i % 4) * 1.6, 0, -3 - (i / 4) * 1.6), com.yompix.novograd.entity.CitizenEntity.Role.CITIZEN, null);
                c.setYaw(180);
                c.setHeadYaw(180);
                c.setBodyYaw(180);
                c.setStationary(true);
                crowd.add(c.getUuid());
                SPAWNED.add(c.getUuid());
            }
        });
        run(100, c -> {
            if (c.currentScreen != null) c.setScreen(null);
            c.options.hudHidden = true;
        });
        run(20, ShowAutotest::rec);
        // ---- игрок: шаг, бег, стоп, лестница (камера сбоку, едет рядом)
        server(2, s -> pl(s).teleport(pl(s).getServerWorld(), base.x - 9, base.y, base.z + 2.5, -90, 0));
        run(4, c -> {
            c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            perTick = () -> {
                var pl = MinecraftClient.getInstance().player;
                if (pl != null && CAM != null && CAM.length > 5) {
                    CAM[0] = (float) pl.getX() + 0.6f;
                    CAM[1] = (float) pl.getY() + 1.1f;
                    CAM[2] = (float) pl.getZ() + 4.2f;
                }
            };
            CAM = new float[]{0, 0, 0, 175, 4, 1};
        });
        label(1, "gait walk");
        run(10, c -> { c.player.setYaw(-90); key(c, c.options.forwardKey, true); });
        run(30, c -> shot(c, "gait_walk"));
        run(5, c -> c.player.setSprinting(true));
        run(14, c -> { c.player.setSprinting(true); shot(c, "gait_run"); });
        run(6, c -> key(c, c.options.forwardKey, false));
        run(4, c -> shot(c, "gait_stop"));
        run(30, c -> {});
        // лестница: от подножия вверх
        server(2, s -> pl(s).teleport(pl(s).getServerWorld(), base.x + 5, base.y, base.z + 2.5, -90, 0));
        run(4, c -> CAM = new float[]{0, 0, 0, 175, 6, 1});
        run(10, c -> { c.player.setYaw(-90); key(c, c.options.forwardKey, true); });
        run(12, c -> shot(c, "gait_stairs"));
        run(26, c -> key(c, c.options.forwardKey, false));
        run(20, c -> {});
        // ---- толпа стоит; потом идёт
        run(4, c -> CAM = new float[]{(float) base.x - 3.6f, (float) base.y + 1.7f, (float) base.z + 1.2f, 180, 10});
        server(2, s -> pl(s).teleport(pl(s).getServerWorld(), base.x - 3.6, base.y, base.z + 8, 180, 0));
        run(80, c -> shot(c, "gait_crowd_idle"));
        server(2, s -> {
            var w = s.getOverworld();
            for (UUID u : crowd)
                if (w.getEntity(u) instanceof com.yompix.novograd.entity.CitizenEntity ce)
                {
                    ce.setStationary(false);
                    ce.getNavigation().startMovingTo(ce.getX() + 10, ce.getY(), ce.getZ() + 1, 0.9);
                }
        });
        run(30, c -> shot(c, "gait_crowd_walk"));
        run(40, c -> {});
        run(5, c -> {
            CAM = null;
            Novograd.LOG.info("[gait] citizen layer: {} calls, {} ns/call", com.yompix.novograd.client.anim.CitizenLife.calls,
                    com.yompix.novograd.client.anim.CitizenLife.calls == 0 ? 0 : com.yompix.novograd.client.anim.CitizenLife.nanos / com.yompix.novograd.client.anim.CitizenLife.calls);
        });
        finish();
    }

    // ================================================================== 0.9.9 «Город»: приложения, игры, свидание, концерт, гонка, квартира
    static void cityshow() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            var d = Life.get(p);
            d.money = 20000;
            d.cash = 500;
            d.storyStarted = true;
            d.storyDone = true;
            Life.sync(p);
        });
        run(100, c -> { if (c.currentScreen != null) c.setScreen(null); });
        run(5, ShowAutotest::rec);
        // ---- соцсеть: громкое дело и своё фото
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            com.yompix.novograd.gta.Social.onTitle(p, "ОГРАБЛЕНИЕ УДАЛОСЬ", "Сити-Банк");
            com.yompix.novograd.gta.Social.onTitle(p, "ОТОРВАЛСЯ", "погоня");
            com.yompix.novograd.gta.Social.onPhoto(p);
        });
        // ---- приложения
        for (String app : new String[]{"night", "dating", "sport", "social", "cozy"}) {
            run(10, c -> c.setScreen(switch (app) {
                case "night" -> new com.yompix.novograd.client.city.CityApps.Night();
                case "dating" -> new com.yompix.novograd.client.city.CityApps.Dating();
                case "sport" -> new com.yompix.novograd.client.city.CityApps.Sport();
                case "social" -> new com.yompix.novograd.client.city.CityApps.Social();
                default -> new com.yompix.novograd.client.city.CityApps.Home();
            }));
            run(25, c -> shot(c, "city_app_" + app));
            run(2, c -> c.setScreen(null));
        }
        // ---- игры: открыть, снимок, сдаться
        for (String g : new String[]{"billiards", "bowling", "hoops", "golf"}) {
            server(4, s -> com.yompix.novograd.gta.Games.open(pl(s), g, 50));
            run(30, c -> shot(c, "city_game_" + g));
            run(2, c -> { if (c.currentScreen != null) c.currentScreen.keyPressed(GLFW_ESC, 0, 0); });
        }
        // ---- знакомство и свидание (ускоренное: -Dnovograd.datefast)
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), "cafe_dt");
            p.teleport(p.getServerWorld(), at.x + 4, at.y, at.z + 4, 0, 10);
            var c = com.yompix.novograd.gta.FunUtil.citizen(p.getServerWorld(), p.getPos().add(1.5, 0, 0), com.yompix.novograd.entity.CitizenEntity.Role.CITIZEN, null);
            c.forceLook(0x5EEDL * 7919);
            SPAWNED.add(c.getUuid());
            p.setSneaking(true);
            boolean got = com.yompix.novograd.life.Dating.meet(p, c);
            p.setSneaking(false);
            Novograd.LOG.info("[city] знакомство: {} номеров в «Встречах»", Life.get(p).city.getList("dating", 10).size());
        });
        server(20, s -> {
            var n = new net.minecraft.nbt.NbtCompound();
            n.putInt("i", 0);
            n.putInt("place", 0);
            com.yompix.novograd.life.Dating.handle(pl(s), "date_ask", n);
        });
        run(60, c -> {
            CAM = null;
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            c.options.hudHidden = true;
        });
        run(20, c -> shot(c, "city_date"));
        run(300, c -> Novograd.LOG.info("[city] свидание: симпатия {}", com.yompix.novograd.client.NovogradClient.data.city.getList("dating", 10).isEmpty() ? -1
                : com.yompix.novograd.client.NovogradClient.data.city.getList("dating", 10).getCompound(0).getInt("rel")));
        // ---- концерт
        cmd(5, "time set 13600");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), com.yompix.novograd.gta.Nightlife.todayVenue(p.getServerWorld()));
            p.teleport(p.getServerWorld(), at.x, at.y, at.z + 9, 180, 5);
        });
        run(80, c -> {
            CAM = null;
            c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        });
        run(40, c -> shot(c, "city_concert"));
        server(2, s -> Novograd.LOG.info("[city] концерт: на сцене и в толпе {}", com.yompix.novograd.gta.Nightlife.spawned()));
        cmd(5, "time set 6000");
        // ---- гонка соло: машина у старта, заезд «Кольцо центра»
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), "dt_1");
            p.teleport(p.getServerWorld(), at.x, at.y, at.z, 0, 0);
            var car = com.yompix.novograd.gta.FunUtil.car(p.getServerWorld(), at, 0, 1);
            SPAWNED.add(car.getUuid());
            p.startRiding(car, true);
            var n = new net.minecraft.nbt.NbtCompound();
            n.putInt("route", 0);
            n.putInt("fee", 0);
            com.yompix.novograd.gta.Sports.handle(p, "sport_create", n);
            com.yompix.novograd.gta.Sports.handle(p, "sport_start", n);
        });
        run(100, c -> {
            c.options.hudHidden = false;
            shot(c, "city_race_hud");
        });
        server(5, s -> {
            com.yompix.novograd.gta.Sports.handle(pl(s), "sport_leave", new net.minecraft.nbt.NbtCompound());
            pl(s).stopRiding();
        });
        // ---- квартира: дать жильё, мебель, вечеринка, питомец
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            var l = com.yompix.novograd.world.Housing.listings().get(0);
            com.yompix.novograd.world.Housing.state(s).owner(p.getUuid()).homes.put(l.id, new com.yompix.novograd.world.Housing.Holding());
            p.teleport(p.getServerWorld(), l.inside.x, l.inside.y, l.inside.z, 0, 30);
            Novograd.LOG.info("[city] дом: {} ({})", l.name, l.id);
            var n = new net.minecraft.nbt.NbtCompound();
            n.putInt("i", 0);
            com.yompix.novograd.world.HomeLife.handle(p, "home_buy", n);
            n.putInt("i", 7);
            com.yompix.novograd.world.HomeLife.handle(p, "home_buy", n);
            for (int i = 0; i < p.getInventory().size(); i++)
                if (com.yompix.novograd.world.HomeLife.isFurniture(p.getInventory().getStack(i))) {
                    p.getInventory().selectedSlot = Math.min(8, i);
                    if (i > 8) {
                        var st = p.getInventory().getStack(i);
                        p.getInventory().setStack(i, p.getInventory().getStack(0));
                        p.getInventory().setStack(0, st);
                        p.getInventory().selectedSlot = 0;
                    }
                    break;
                }
        });
        run(20, c -> {
            var hit = c.crosshairTarget;
            if (hit instanceof net.minecraft.util.hit.BlockHitResult bh) c.interactionManager.interactBlock(c.player, net.minecraft.util.Hand.MAIN_HAND, bh);
        });
        server(10, s -> {
            var n = new net.minecraft.nbt.NbtCompound();
            com.yompix.novograd.world.HomeLife.handle(pl(s), "home_party", n);
            n.putString("kind", "dog");
            com.yompix.novograd.world.HomeLife.handle(pl(s), "home_pet", n);
            n.putString("kind", "cat");
            com.yompix.novograd.world.HomeLife.handle(pl(s), "home_pet", n);
        });
        run(60, c -> {
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            c.options.hudHidden = true;
        });
        run(10, c -> shot(c, "city_home_party"));
        run(5, c -> c.setScreen(new com.yompix.novograd.client.city.CityApps.Home()));
        run(25, c -> shot(c, "city_app_cozy_home"));
        run(2, c -> c.setScreen(null));
        server(5, s -> {
            var d = Life.get(pl(s));
            Novograd.LOG.info("[city] итог: слава {}, подписчики {}, питомцы {}/{}, «Встречи» {}, мебели поставлено {}", d.city.getInt("fame"), d.city.getInt("followers"),
                    d.city.getString("dogName"), d.city.getString("catName"), d.city.getList("dating", 10).size(), com.yompix.novograd.world.HomeLife.placed(pl(s)));
        });
        finish();
    }

    private static final int GLFW_ESC = 256;

    // ================================================================== 0.9.10 «Работа»: смены, криминал, служба, бизнес
    private static net.fabricmc.fabric.api.entity.FakePlayer bot;

    static void workshow() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            var d = Life.get(p);
            d.money = 50000;
            d.storyStarted = true;
            d.storyDone = true;
            Life.sync(p);
        });
        run(100, c -> {
            if (c.player != null && c.player.isDead()) c.player.requestRespawn(); // прошлый прогон мог оставить игрока мёртвым
            if (c.currentScreen != null) c.setScreen(null);
        });
        run(40, c -> { if (c.currentScreen != null) c.setScreen(null); });
        server(2, s -> { pl(s).setHealth(20); pl(s).getHungerManager().setFoodLevel(20); });
        run(5, ShowAutotest::rec);
        // ---- курьер: три адреса
        server(5, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_start", nbt("i", 4)));
        for (int k = 0; k < 3; k++) server(30, s -> {
            var p = pl(s);
            Vec3d t = workTarget(p);
            if (t != null) p.teleport(p.getServerWorld(), t.x, safeY(p, t.x, t.z), t.z, 0, 0);
        });
        run(30, c -> shot(c, "work_courier"));
        server(5, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_stop", new net.minecraft.nbt.NbtCompound()));
        // ---- механик: к сломанной машине, присесть — чинит руками
        server(5, s -> {
            pl(s).stopRiding();
            com.yompix.novograd.gta.Careers.handle(pl(s), "career_start", nbt("i", 3));
        });
        server(30, s -> {
            var p = pl(s);
            Vec3d t = workTarget(p);
            if (t != null) p.teleport(p.getServerWorld(), t.x + 2, safeY(p, t.x + 2, t.z + 0.5), t.z + 0.5, 90, 20);
        });
        server(40, s -> { // машина могла встать в ещё не загруженном месте — подойти вплотную
            var p = pl(s);
            var car = nearestCar(p, -1, 12);
            Novograd.LOG.info("[work] механик: машина рядом {}", car != null);
            if (car != null) p.teleport(p.getServerWorld(), car.getX() + 2, car.getY(), car.getZ(), 90, 20); // рядом с машиной, не на крыше
        });
        run(60, c -> { // ремонт начинается сам, когда подошёл пешком (Shift прерывает руки — не жмём)
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            c.options.hudHidden = true;
            shot(c, "work_mechanic");
        });
        run(160, c -> c.options.hudHidden = false);
        server(5, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_stop", new net.minecraft.nbt.NbtCompound()));
        // ---- журналист: к сюжету, снимок
        server(5, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_start", nbt("i", 6)));
        server(20, s -> {
            var p = pl(s);
            Vec3d t = workTarget(p);
            if (t != null) p.teleport(p.getServerWorld(), t.x + 5, safeY(p, t.x + 5, t.z + 5), t.z + 5, 0, 0);
            com.yompix.novograd.gta.Careers.onPhoto(p);
        });
        server(5, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_stop", new net.minecraft.nbt.NbtCompound()));
        // ---- охранник (ускоренно: -Dnovograd.workfast)
        server(5, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_start", nbt("i", 5)));
        server(20, s -> {
            var p = pl(s);
            Vec3d t = workTarget(p);
            if (t != null) p.teleport(p.getServerWorld(), t.x + 2, safeY(p, t.x + 2, t.z + 2), t.z + 2, 0, 0);
        });
        run(260, c -> shot(c, "work_guard"));
        server(5, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_stop", new net.minecraft.nbt.NbtCompound()));
        // ---- рыбак: 5 рыб у причала
        server(5, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_start", nbt("i", 2)));
        server(10, s -> {
            var p = pl(s);
            Vec3d t = workTarget(p);
            p.getInventory().insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.COD, 5));
            if (t != null) p.teleport(p.getServerWorld(), t.x, safeY(p, t.x, t.z), t.z, 0, 0);
        });
        server(30, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_stop", new net.minecraft.nbt.NbtCompound()));
        // ---- криминал: угон на заказ, банда, налёт вдвоём
        server(5, s -> {
            var p = pl(s);
            com.yompix.novograd.gta.CrimeCareer.handle(p, "crime_order", new net.minecraft.nbt.NbtCompound());
            int i = Life.get(p).city.getInt("order");
            Vec3d chop = com.yompix.novograd.story.Story.place(p.getServerWorld(), "chop_shop");
            int[] variants = {12, 9, 10, 13, 14, 7, 8, 16};
            var car = com.yompix.novograd.gta.FunUtil.car(p.getServerWorld(), chop, 0, variants[i]);
            p.teleport(p.getServerWorld(), chop.x, safeY(p, chop.x, chop.z), chop.z, 0, 0);
            p.startRiding(car, true);
            com.yompix.novograd.gta.CrimeCareer.handle(p, "crime_gang", nbtS("name", "Тестовые"));
        });
        server(60, s -> {
            var p = pl(s);
            p.stopRiding();
            var w = p.getServerWorld();
            bot = net.fabricmc.fabric.api.entity.FakePlayer.get(w, new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("workbot".getBytes()), "Partner"));
            bot.refreshPositionAndAngles(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
            bot.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(com.yompix.novograd.ModContent.PISTOL));
            int before = Life.get(p).dirty;
            boolean seen = w.getPlayers().contains(bot);
            com.yompix.novograd.gta.CrimeCareer.robbery(p, 500);
            Novograd.LOG.info("[work] налёт вдвоём: бот в мире {}, доля игрока {}, у бота {}", seen, Life.get(p).dirty - before, Life.get(bot).dirty);
            com.yompix.novograd.gta.Wanted.clear(p);
        });
        // ---- служба: скорая лечит, полиция задерживает (бот)
        server(10, s -> com.yompix.novograd.gta.Duty.handle(pl(s), "duty_on", nbt("role", 2)));
        server(5, s -> {
            if (bot == null) return;
            bot.setHealth(4);
            bot.refreshPositionAndAngles(pl(s).getX() + 1, pl(s).getY(), pl(s).getZ(), 0, 0);
        });
        server(80, s -> Novograd.LOG.info("[work] скорая: здоровье бота {}", bot == null ? -1 : bot.getHealth()));
        run(5, c -> c.setScreen(new com.yompix.novograd.client.city.WorkApps.Duty()));
        run(20, c -> shot(c, "work_app_duty"));
        run(2, c -> c.setScreen(null));
        // ---- бизнес: кафе в центре, запас, персонал, день
        server(5, s -> {
            var p = pl(s);
            com.yompix.novograd.gta.Duty.handle(p, "duty_off", new net.minecraft.nbt.NbtCompound());
            var n = nbt("type", 0);
            n.putString("place", "cafe_dt");
            n.putString("name", "Тест-кофейня");
            com.yompix.novograd.gta.Business.handle(p, "biz_open", n);
            com.yompix.novograd.gta.Business.handle(p, "biz_buy", nbt("units", 200));
            com.yompix.novograd.gta.Business.handle(p, "biz_staff", nbtD(0, 1));
            com.yompix.novograd.gta.Business.handle(p, "biz_staff", nbtD(1, 1));
        });
        cmd(10, "time add 24000");
        run(140, c -> {});
        // ---- дальнобойщик: фура → в кабину → к разгрузке
        server(5, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_start", nbt("i", 0)));
        server(5, s -> {
            var p = pl(s);
            Vec3d wh = com.yompix.novograd.story.Story.place(p.getServerWorld(), "port_warehouse");
            p.teleport(p.getServerWorld(), wh.x, safeY(p, wh.x, wh.z), wh.z, 0, 0);
        });
        server(40, s -> {
            var p = pl(s);
            Vec3d tr = workTarget(p);
            if (tr != null) p.teleport(p.getServerWorld(), tr.x, safeY(p, tr.x, tr.z), tr.z, 0, 0);
            var car = nearestCar(p, 11, 30);
            if (car != null) {
                p.teleport(p.getServerWorld(), car.getX(), safeY(p, car.getX(), car.getZ()), car.getZ(), 0, 0);
                p.startRiding(car, true);
            }
        });
        server(30, s -> { // к разгрузке: сначала игрок (грузится чанк), потом фура
            var p = pl(s);
            Vec3d t = workTarget(p);
            var car = p.getVehicle();
            Novograd.LOG.info("[work] фура: в кабине {}, разгрузка {}", car != null, t);
            if (t != null && car != null) {
                p.stopRiding();
                p.teleport(p.getServerWorld(), t.x, safeY(p, t.x, t.z), t.z, 0, 0);
                car.teleport(p.getServerWorld(), t.x + 1, safeY(p, t.x + 1, t.z), t.z, java.util.Set.of(), 0, 0);
            }
        });
        server(40, s -> {
            var p = pl(s);
            var car = nearestCar(p, 11, 20);
            if (car != null) p.startRiding(car, true);
        });
        run(60, c -> shot(c, "work_trucker"));
        server(5, s -> com.yompix.novograd.gta.Careers.handle(pl(s), "career_stop", new net.minecraft.nbt.NbtCompound()));
        for (String app : new String[]{"career", "crime", "biz"}) {
            run(5, c -> c.setScreen(switch (app) {
                case "career" -> new com.yompix.novograd.client.city.WorkApps.Career();
                case "crime" -> new com.yompix.novograd.client.city.WorkApps.Crime();
                default -> new com.yompix.novograd.client.city.WorkApps.Biz();
            }));
            run(25, c -> shot(c, "work_app_" + app));
            run(2, c -> c.setScreen(null));
        }
        server(5, s -> {
            var d = Life.get(pl(s));
            StringBuilder sb = new StringBuilder();
            for (String id : com.yompix.novograd.gta.Careers.IDS) sb.append(id).append('=').append(d.city.getInt("car_" + id)).append(' ');
            Novograd.LOG.info("[work] опыт профессий: {}; авторитет {}; заказ {}", sb, d.city.getInt("crimeXp"), d.city.contains("order") ? "не сдан" : "сдан");
            if (bot != null) bot.discard();
        });
        finish();
    }

    // ================================================================== 0.9.10: мебель, сцена, свидание вместе, Фотограм, дуэль
    private static float[] STAGE_CAM;

    static void propshow() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            var d = Life.get(p);
            d.money = 50000;
            d.storyStarted = true;
            d.storyDone = true;
            p.setHealth(20);
            p.getAbilities().invulnerable = true; // банды и прохожие не мешают снимать
            p.sendAbilitiesUpdate();
            Life.sync(p);
        });
        run(100, c -> {
            if (c.player != null && c.player.isDead()) c.player.requestRespawn();
            if (c.currentScreen != null) c.setScreen(null);
        });
        run(5, ShowAutotest::rec);
        // ---- мебель: купить, предпросмотр с поворотом, поставить (сервер — как ПКМ игрока), сесть
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            var l = com.yompix.novograd.world.Housing.listings().get(1);
            com.yompix.novograd.world.Housing.state(s).owner(p.getUuid()).homes.put(l.id, new com.yompix.novograd.world.Housing.Holding());
            p.teleport(p.getServerWorld(), l.inside.x, l.inside.y, l.inside.z, 0, 50);
            for (var e : p.getServerWorld().getOtherEntities(p, p.getBoundingBox().expand(14), e -> !(e instanceof net.minecraft.entity.player.PlayerEntity))) e.discard();
            p.getInventory().clear();
            for (int i : new int[]{0, 1, 3, 12, 16, 14}) com.yompix.novograd.world.HomeLife.handle(p, "home_buy", nbt("i", i));
            Novograd.LOG.info("[prop] дом {} ({}), в руках {}", l.name, l.inside, p.getInventory().getStack(0).getName().getString());
        });
        run(40, c -> {
            c.player.getInventory().selectedSlot = 0;
            c.options.setPerspective(Perspective.FIRST_PERSON);
            com.yompix.novograd.client.FurniturePreview.rotate(1);
        });
        run(30, c -> shot(c, "prop_preview"));
        int[][] spots = {{-2, 2}, {0, 2}, {2, 2}, {-2, 4}, {0, 4}, {2, 4}};
        for (int k = 0; k < spots.length; k++) {
            int kk = k;
            server(4, s -> {
                ServerPlayerEntity p = pl(s);
                var w = p.getServerWorld();
                var l = com.yompix.novograd.world.Housing.listings().get(1);
                net.minecraft.util.math.BlockPos base = net.minecraft.util.math.BlockPos.ofFloored(l.inside);
                net.minecraft.util.math.BlockPos at = base.add(spots[kk][0], 0, spots[kk][1]);
                p.teleport(w, l.inside.x, l.inside.y, l.inside.z, 0, 30); // лицом на юг, к предметам
                if (w.getBlockState(at).getBlock() instanceof com.yompix.novograd.block.FurnitureBlock) w.removeBlock(at, false); // прошлый прогон
                p.getInventory().selectedSlot = kk;
                var hit = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(at.down()).add(0, 0.5, 0), net.minecraft.util.math.Direction.UP, at.down(), false);
                var r = p.interactionManager.interactBlock(p, w, p.getMainHandStack(), net.minecraft.util.Hand.MAIN_HAND, hit);
                Novograd.LOG.info("[prop] ставлю {} в {}: {}", p.getMainHandStack().isEmpty() ? "-" : p.getMainHandStack().getName().getString(), at, r);
            });
        }
        server(10, s -> {
            ServerPlayerEntity p = pl(s);
            var l = com.yompix.novograd.world.Housing.listings().get(1);
            StringBuilder sb = new StringBuilder();
            for (var pos : net.minecraft.util.math.BlockPos.iterate(net.minecraft.util.math.BlockPos.ofFloored(l.inside).add(-6, -1, -4), net.minecraft.util.math.BlockPos.ofFloored(l.inside).add(6, 1, 6))) {
                var st = p.getServerWorld().getBlockState(pos);
                if (st.getBlock() instanceof com.yompix.novograd.block.FurnitureBlock)
                    sb.append(net.minecraft.registry.Registries.BLOCK.getId(st.getBlock()).getPath()).append('=').append(st.get(net.minecraft.block.HorizontalFacingBlock.FACING)).append(' ');
            }
            Novograd.LOG.info("[prop] мебель поставлена: {} (всего {})", sb, com.yompix.novograd.world.HomeLife.placed(p));
            p.teleport(p.getServerWorld(), l.inside.x - 0.5, l.inside.y + 1.2, l.inside.z + 7.5, 180, 30);
        });
        run(30, c -> {
            c.options.hudHidden = true;
            shot(c, "prop_furniture");
            c.options.hudHidden = false;
        });
        server(5, s -> { // сесть на диван (через блок, как ПКМ)
            ServerPlayerEntity p = pl(s);
            var l = com.yompix.novograd.world.Housing.listings().get(1);
            for (var pos : net.minecraft.util.math.BlockPos.iterate(net.minecraft.util.math.BlockPos.ofFloored(l.inside).add(-6, -1, -4), net.minecraft.util.math.BlockPos.ofFloored(l.inside).add(6, 1, 6)))
                if (p.getServerWorld().getBlockState(pos).getBlock() instanceof com.yompix.novograd.block.FurnitureBlock f && f.seat >= 0) {
                    p.getInventory().selectedSlot = 8;
                    var hit = new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos.toImmutable(), false);
                    p.interactionManager.interactBlock(p, p.getServerWorld(), p.getMainHandStack(), net.minecraft.util.Hand.MAIN_HAND, hit);
                    break;
                }
            Novograd.LOG.info("[prop] сидит на мебели: {}", p.hasVehicle());
        });
        run(30, c -> {
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            c.options.hudHidden = true;
            shot(c, "prop_sofa");
            c.options.hudHidden = false;
        });
        server(5, s -> pl(s).stopRiding());
        // ---- свидание: партнёр садится за столик и ест вместе с тобой
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), "cafe_dt");
            p.teleport(p.getServerWorld(), at.x + 4, safeY(p, at.x + 4, at.z + 4), at.z + 4, 0, 10);
            var c = com.yompix.novograd.gta.FunUtil.citizen(p.getServerWorld(), p.getPos().add(1.5, 0, 0), com.yompix.novograd.entity.CitizenEntity.Role.CITIZEN, null);
            c.forceLook(0x5EEDL * 7919);
            SPAWNED.add(c.getUuid());
            p.setSneaking(true);
            com.yompix.novograd.life.Dating.meet(p, c);
            p.setSneaking(false);
            var n = nbt("i", 0);
            n.putInt("place", 0);
            com.yompix.novograd.life.Dating.handle(p, "date_ask", n);
            p.teleport(p.getServerWorld(), at.x + 1.5, safeY(p, at.x + 1.5, at.z + 2.5), at.z + 2.5, 0, 10);
        });
        server(40, s -> Novograd.LOG.info("[prop] свидание: {}", com.yompix.novograd.life.Dating.together(pl(s))));
        server(5, s -> { // к партнёру; два стула рядом (для теста ставим свои, если у кафе нет) — садимся на один
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            var c = com.yompix.novograd.life.Dating.partner(p);
            if (c != null) p.teleport(w, c.getX() + 1, c.getY(), c.getZ(), 90, 10);
        });
        server(30, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            var date = java.util.Collections.singletonList(com.yompix.novograd.life.Dating.partner(p));
            net.minecraft.util.math.BlockPos a = p.getBlockPos().add(0, 0, 1), b = a.add(2, 0, 0);
            for (var pos : new net.minecraft.util.math.BlockPos[]{a, b})
                if (w.getBlockState(pos).isAir()) w.setBlockState(pos, com.yompix.novograd.ModContent.FURNITURE.get("chair").getDefaultState());
            w.setBlockState(a.add(1, 0, 0), com.yompix.novograd.ModContent.FURNITURE.get("coffee_table").getDefaultState());
            com.yompix.novograd.block.SeatBlockHelper.sit(w, a, p, 0.5);
            Novograd.LOG.info("[prop] свидание: партнёр {}, сидит {}", date.size(), p.hasVehicle());
        });
        server(60, s -> Novograd.LOG.info("[prop] свидание, вместе: «{}»", com.yompix.novograd.life.Dating.together(pl(s))));
        run(5, c -> {
            CAM = null;
            c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            c.options.hudHidden = true;
        });
        run(80, c -> {
            shot(c, "prop_date_cafe");
            c.options.hudHidden = false;
        });
        server(5, s -> pl(s).stopRiding());
        // второе свидание — клуб: танец и бильярд с партнёром
        server(300, s -> {
            ServerPlayerEntity p = pl(s);
            var n = nbt("i", 0);
            n.putInt("place", 1);
            com.yompix.novograd.life.Dating.handle(p, "date_ask", n);
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), "club_neon");
            p.teleport(p.getServerWorld(), at.x + 1, safeY(p, at.x + 1, at.z + 3), at.z + 3, 180, 5);
        });
        server(40, s -> { // к партнёру вплотную
            ServerPlayerEntity p = pl(s);
            var c = com.yompix.novograd.life.Dating.partner(p);
            if (c != null) p.teleport(p.getServerWorld(), c.getX() + 1.5, c.getY(), c.getZ(), 90, 10);
        });
        server(60, s -> Novograd.LOG.info("[prop] клуб, вместе: «{}»", com.yompix.novograd.life.Dating.together(pl(s))));
        run(5, c -> {
            c.setScreen(null);
            c.options.hudHidden = true;
        });
        run(70, c -> {
            shot(c, "prop_date_dance");
            c.options.hudHidden = false;
        });
        server(5, s -> com.yompix.novograd.gta.Games.open(pl(s), "billiards", 100));
        run(30, c -> shot(c, "prop_date_billiards"));
        run(2, c -> { if (c.currentScreen != null) c.currentScreen.keyPressed(GLFW_ESC, 0, 0); });
        // ---- сцена концерта: обе площадки
        cmd(5, "time set 13600");
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), com.yompix.novograd.gta.Nightlife.todayVenue(p.getServerWorld()));
            p.teleport(p.getServerWorld(), at.x, safeY(p, at.x, at.z + 12), at.z + 12, 180, 5);
        });
        run(100, c -> {
            c.options.setPerspective(Perspective.FIRST_PERSON);
            c.options.hudHidden = true;
        });
        server(2, s -> {
            Vec3d at = com.yompix.novograd.story.Story.place(s.getOverworld(), com.yompix.novograd.gta.Nightlife.todayVenue(s.getOverworld()));
            STAGE_CAM = new float[]{(float) at.x + 0.5f, (float) at.y + 5.5f, (float) at.z + 13, 180, 14};
        });
        run(10, c -> CAM = STAGE_CAM);
        run(20, c -> shot(c, "prop_stage_a"));
        run(2, c -> CAM = null);
        server(2, s -> Novograd.LOG.info("[prop] сцена A ({}, концерт {}, время {}): блоков {}, на сцене и в толпе {}", com.yompix.novograd.gta.Nightlife.todayVenue(s.getOverworld()),
                com.yompix.novograd.gta.Nightlife.concertNow(s.getOverworld()), s.getOverworld().getTimeOfDay(),
                com.yompix.novograd.gta.ConcertStage.size(s), com.yompix.novograd.gta.Nightlife.spawned()));
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            var w = p.getServerWorld();
            String other = com.yompix.novograd.gta.Nightlife.todayVenue(w).equals("riv_strip") ? "downtown_plaza" : "riv_strip";
            Vec3d at = com.yompix.novograd.story.Story.place(w, other);
            p.teleport(w, at.x, safeY(p, at.x, at.z + 12), at.z + 12, 180, 5);
            w.getChunk((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4);
        });
        server(60, s -> {
            var w = s.getOverworld();
            String other = com.yompix.novograd.gta.Nightlife.todayVenue(w).equals("riv_strip") ? "downtown_plaza" : "riv_strip";
            int before = com.yompix.novograd.gta.ConcertStage.size(s);
            com.yompix.novograd.gta.ConcertStage.clear(w);
            Novograd.LOG.info("[prop] сцена A убрана: {} → {}", before, com.yompix.novograd.gta.ConcertStage.size(s));
            com.yompix.novograd.gta.ConcertStage.build(w, com.yompix.novograd.story.Story.place(w, other));
            Novograd.LOG.info("[prop] сцена B ({}): блоков {}", other, com.yompix.novograd.gta.ConcertStage.size(s));
        });
        server(2, s -> {
            var w = s.getOverworld();
            String other = com.yompix.novograd.gta.Nightlife.todayVenue(w).equals("riv_strip") ? "downtown_plaza" : "riv_strip";
            Vec3d at = com.yompix.novograd.story.Story.place(w, other);
            STAGE_CAM = new float[]{(float) at.x + 0.5f, (float) at.y + 5.5f, (float) at.z + 13, 180, 14};
        });
        run(10, c -> CAM = STAGE_CAM);
        run(30, c -> shot(c, "prop_stage_b"));
        run(2, c -> CAM = null);
        cmd(5, "time set 6000");
        server(60, s -> Novograd.LOG.info("[prop] после концерта блоков сцены: {}", com.yompix.novograd.gta.ConcertStage.size(s)));
        run(5, c -> c.options.hudHidden = false);
        // ---- Фотограм: снимок камерой → картинка в посте
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            Vec3d at = com.yompix.novograd.story.Story.place(p.getServerWorld(), "downtown_plaza");
            p.teleport(p.getServerWorld(), at.x, safeY(p, at.x, at.z + 20) + 1, at.z + 20, 180, -5);
            Life.get(p).city.putLong("photoAt", 0);
        });
        run(60, c -> com.yompix.novograd.client.NovogradClient.takePhoto());
        server(40, s -> {
            var feed = com.yompix.novograd.gta.Social.class;
            Novograd.LOG.info("[prop] фотограм: картинка у поста {}", com.yompix.novograd.gta.Social.latestHasImage(s, "@" + pl(s).getName().getString()));
        });
        run(5, c -> c.setScreen(new com.yompix.novograd.client.city.CityApps.Social()));
        run(40, c -> shot(c, "prop_fotogram"));
        run(5, c -> c.setScreen(null));
        // ---- дуэль: бильярд против второго игрока (FakePlayer), ставка $100
        int[] money = new int[2];
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            bot = net.fabricmc.fabric.api.entity.FakePlayer.get(p.getServerWorld(), new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("propbot".getBytes()), "Rival"));
            Life.get(bot).money = 1000;
            money[0] = Life.get(p).money + Life.get(p).cash;
            com.yompix.novograd.gta.Duels.testStart(p, bot, "billiards", 100);
        });
        run(40, c -> shot(c, "prop_duel"));
        server(2, s -> com.yompix.novograd.gta.Duels.live(bot, 3));
        run(30, c -> shot(c, "prop_duel_live"));
        server(440, s -> {
            var n = nbtS("game", "billiards");
            n.putInt("score", 3);
            com.yompix.novograd.gta.Duels.done(bot, n);
            n.putInt("score", 6);
            com.yompix.novograd.gta.Duels.done(pl(s), n);
            money[1] = Life.get(pl(s)).money + Life.get(pl(s)).cash;
            Novograd.LOG.info("[prop] дуэль: деньги {} → {} (ставка $100, банк $200), у соперника ${}, матчей {}", money[0], money[1], Life.get(bot).money,
                    com.yompix.novograd.gta.Duels.matches());
        });
        run(30, c -> shot(c, "prop_duel_result"));
        run(2, c -> { if (c.currentScreen != null) c.currentScreen.keyPressed(GLFW_ESC, 0, 0); });
        run(20, c -> c.setScreen(new com.yompix.novograd.client.city.WorkApps.VoiceApp()));
        run(30, c -> shot(c, "prop_voice_app"));
        run(5, c -> c.setScreen(null));
        server(5, s -> {
            Novograd.LOG.info("[prop] голос: мод {}, на сервере {}", net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("voicechat"), com.yompix.novograd.gta.Voice.backend != null);
            if (bot != null) bot.discard();
        });
        finish();
    }

    /** Высота земли (телепорт в автотесте — не в стену). */
    private static double safeY(ServerPlayerEntity p, double x, double z) {
        p.getServerWorld().getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
        return com.yompix.novograd.story.Story.topY(p.getServerWorld(), (int) Math.floor(x), (int) Math.floor(z)) + 1;
    }

    private static net.minecraft.nbt.NbtCompound nbt(String k, int v) {
        var n = new net.minecraft.nbt.NbtCompound();
        n.putInt(k, v);
        return n;
    }

    private static net.minecraft.nbt.NbtCompound nbtS(String k, String v) {
        var n = new net.minecraft.nbt.NbtCompound();
        n.putString(k, v);
        return n;
    }

    private static net.minecraft.nbt.NbtCompound nbtD(int i, int d) {
        var n = nbt("i", i);
        n.putInt("d", d);
        return n;
    }

    private static Vec3d workTarget(ServerPlayerEntity p) {
        return com.yompix.novograd.gta.Careers.target(p);
    }

    private static com.yompix.novograd.entity.CarEntity nearestCar(ServerPlayerEntity p, int variant, double r) {
        com.yompix.novograd.entity.CarEntity best = null;
        for (var c : p.getServerWorld().getEntitiesByClass(com.yompix.novograd.entity.CarEntity.class, p.getBoundingBox().expand(r), c -> variant < 0 || c.getVariant() == variant))
            if (best == null || c.squaredDistanceTo(p) < best.squaredDistanceTo(p)) best = c;
        return best;
    }

    // ================================================================== «Сити-Банк»: снимки снаружи и внутри
    static void bankshow() {
        cmd(5, "time set 6000");
        cmd(5, "weather clear");
        cmd(5, "gamerule doDaylightCycle false");
        int X = 480, Z = -336;
        server(5, s -> {
            ServerPlayerEntity p = pl(s);
            p.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            p.teleport(s.getOverworld(), X + 0.5, com.yompix.novograd.world.Island.G + 30, Z + 40, 180, 10);
        });
        run(100, c -> {
            if (c.currentScreen != null) c.setScreen(null);
            c.options.hudHidden = true;
            c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        });
        // ждём загрузку участка и (в старом мире) перестройку
        for (int i = 0; i < 90; i++) server(40, s -> Novograd.LOG.info("[bank] {}", com.yompix.novograd.world.BankBuilding.status(s)));
        String tag = Boolean.getBoolean("novograd.nobank") ? "before" : "after";
        int F = com.yompix.novograd.world.Island.G + 2;
        float[][] views = {
                {X + 0.5f, F + 4, Z + 38, 180, -8}, {X + 24, F + 7, Z + 34, 145, -6}, {X + 0.5f, F + 14, Z + 75, 180, -18},
                {X + 0.5f, F + 5, Z + 11.5f, 180, 12}, {X + 9.5f, F + 3.2f, Z + 10.5f, 210, 18}, {X - 11.5f, F + 2.6f, Z + 18.5f, 180, 8},
                {X - 4, F + 4, Z + 4, 90, 18}, {X + 0.5f, F + 3, Z - 1.5f, 180, 0}};
        String[] names = {"front", "corner", "tower", "lobby", "office", "atm_bay", "waiting", "vault"};
        for (int i = 0; i < views.length; i++) {
            float[] v = views[i];
            String n = names[i];
            server(2, s -> pl(s).teleport(s.getOverworld(), v[0], v[1] - 1.6, v[2] + 3, v[3], v[4]));
            run(30, c -> CAM = new float[]{v[0], v[1], v[2], v[3], v[4]});
            run(30, c -> shot(c, "bank_" + tag + "_" + n));
        }
        run(5, c -> CAM = null);
        finish();
    }

    /** Подойти к месту у блока и поставить камеру сбоку (side — вправо/влево, dist — от игрока). */
    private static void station(java.util.function.Supplier<BlockPos> at, double dist, double side, String name) {
        server(4, s -> {
            BlockPos pos = at.get();
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.4, 180, 10);
        });
        run(4, c -> aim(c, new Vec3d(at.get().getX() + 0.5, at.get().getY(), at.get().getZ() + 1.1), dist, side));
        label(1, "станция " + name);
    }

    /** Камера показа (ставит ShowCameraMixin в виде от 3-го лица): x, y, z, yaw, pitch; null — обычная. */
    public static float[] CAM;

    /** Камера сбоку и чуть спереди от точки, смотрит на неё. */
    private static void aim(MinecraftClient c, Vec3d at, double dist, double side) {
        Vec3d eye = at.add(side, 1.45, dist);
        Vec3d d = at.add(0, 1.0, 0).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        CAM = new float[]{(float) eye.x, (float) eye.y, (float) eye.z, yaw, pitch};
        thirdPerson(c);
    }

    private static void thirdPerson(MinecraftClient c) {
        c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        c.options.hudHidden = true;
    }

    private static void firstPerson(java.util.function.Supplier<BlockPos> at, String name) {
        server(4, s -> {
            BlockPos pos = at.get();
            ServerPlayerEntity p = pl(s);
            p.teleport(p.getServerWorld(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.0, 180, 25);
        });
        run(4, c -> {
            CAM = null;
            c.options.setPerspective(Perspective.FIRST_PERSON);
            c.options.hudHidden = false;
        });
        label(1, "первое лицо " + name);
    }

    private static void click(int delay, java.util.function.Supplier<BlockPos> at) {
        run(delay, c -> {
            BlockPos p = at.get();
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(p).add(0, 0.2, 0.5), Direction.SOUTH, p, false);
            c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, hit);
        });
    }

    private static void shotAt(int delay, String name) {
        run(delay, c -> shot(c, name));
    }
}
