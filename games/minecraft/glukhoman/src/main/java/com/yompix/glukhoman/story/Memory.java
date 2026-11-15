package com.yompix.glukhoman.story;

import com.yompix.glukhoman.horror.Sanity;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Память (1.0, механика 11): ниже 20 % — ник в TAB серый, в чате ты «???»; на нуле — петля: улица к деревне замыкается
 * (каждый круг — снова у колонки, на столбе «41 км»). Выход — тебя снял другой игрок, или (один) сними себя 5 секунд.
 */
public final class Memory {
    private Memory() {}

    static final Map<UUID, Boolean> GREY = new HashMap<>();
    static final Map<UUID, Integer> SELF = new HashMap<>();

    public static boolean forgotten(ServerPlayerEntity p) {
        return Story.isOurWorld() && p != Story.testPlayer && Sanity.get(p) < 20;
    }

    /** Ник в TAB (из ServerPlayerEntityMixin): серый, а на нуле — «???». */
    public static Text listName(ServerPlayerEntity p) {
        if (!forgotten(p)) return null;
        return Text.literal(Sanity.get(p) <= 1 ? "???" : p.getName().getString()).formatted(Formatting.DARK_GRAY);
    }

    public static void init() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((msg, sender, params) -> {
            if (!forgotten(sender)) return true;
            var server = sender.getServer();
            if (server != null) server.getPlayerManager().broadcast(Text.literal("<???> ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(msg.getContent().getString()).formatted(Formatting.GRAY)), false);
            return false;
        });
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0 || !Story.isOurWorld()) return;
        StoryState st = Story.st();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            boolean g = forgotten(p);
            if (GREY.getOrDefault(p.getUuid(), false) != g) {
                GREY.put(p.getUuid(), g);
                server.getPlayerManager().sendToAll(new PlayerListS2CPacket(EnumSet.of(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME), List.of(p)));
                if (g) Story.msg(p, "Тебя забывают. В списке ты серый, в чате — «???». Пусть тебя снимут.");
            }
            if (p == Story.testPlayer || Sanity.get(p) > 1 || Night.running || Story.sceneRunning() || st.chapter < 2 || st.chapter >= 8) continue;
            // петля: дальше колонки — снова у колонки
            if (p.getZ() > 70 && p.getZ() < 170 && Math.abs(p.getX()) < 40) {
                p.teleport(p.getServerWorld(), p.getX(), p.getY(), p.getZ() - 34, p.getYaw(), p.getPitch());
                st.inc("loops");
                Story.msg(p, "Та же улица. На столбе вместо «Еловый» — «41 км». Домов на один меньше.");
            }
            if (Cam.up(p)) {   // сними себя: в кадре за спиной — он, но ты есть
                int n = SELF.merge(p.getUuid(), 1, Integer::sum);
                if (n >= 5) {
                    SELF.remove(p.getUuid());
                    Sanity.set(p, 30);
                    Story.msg(p, "Ты снял себя. За спиной в кадре кто-то стоит. Но ты — есть.");
                }
            } else SELF.remove(p.getUuid());
        }
    }
}
