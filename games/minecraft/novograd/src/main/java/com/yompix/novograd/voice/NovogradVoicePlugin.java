package com.yompix.novograd.voice;

import com.yompix.novograd.Novograd;
import com.yompix.novograd.gta.Voice;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.9.10: плагин Simple Voice Chat (точка входа "voicechat" в fabric.mod.json — её вызывает только мод голоса,
 * без него класс не загружается). Группы звонков и раций, тишина в тюрьме. Логика — в gta/Voice.
 */
public final class NovogradVoicePlugin implements VoicechatPlugin {
    private VoicechatServerApi api;
    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return Novograd.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration r) {
        r.registerEvent(VoicechatServerStartedEvent.class, e -> {
            api = e.getVoicechat();
            groups.clear();
            Voice.backend = new Voice.Backend() {
                @Override
                public boolean installed(UUID player) {
                    VoicechatConnection c = api.getConnectionOf(player);
                    return c != null && c.isInstalled();
                }

                @Override
                public void group(UUID player, String key, String name) {
                    set(player, key, name);
                }
            };
            Novograd.LOG.info("[голос] Simple Voice Chat найден — звонки и рации Новограда включены");
        });
        r.registerEvent(MicrophonePacketEvent.class, e -> {
            VoicechatConnection c = e.getSenderConnection();
            if (c != null && Voice.MUTED.contains(c.getPlayer().getUuid())) e.cancel(); // в тюрьме — тишина
        });
    }

    private void set(UUID player, String key, String name) {
        if (api == null) return;
        VoicechatConnection c = api.getConnectionOf(player);
        if (c == null) return;
        if (key.isEmpty()) {
            // убираем только из своих групп: в группу, которую игрок создал сам в окне мода голоса, не лезем
            if (c.isInGroup() && groups.values().stream().anyMatch(x -> x.getId().equals(c.getGroup().getId()))) c.setGroup(null);
            return;
        }
        Group g = groups.get(key);
        if (g == null || api.getGroup(g.getId()) == null) {
            g = api.groupBuilder().setName(name).setPassword(UUID.randomUUID().toString()).setHidden(true)
                    .setPersistent(false).setType(Group.Type.OPEN).build();
            groups.put(key, g);
        }
        if (c.getGroup() == null || !c.getGroup().getId().equals(g.getId())) c.setGroup(g);
    }
}
