package com.yompix.novograd;

import com.yompix.novograd.life.LifeNet;
import com.yompix.novograd.life.LifeEvents;
import com.yompix.novograd.world.LifeCommands;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Novograd implements ModInitializer {
    public static final String MOD_ID = "novograd";
    public static final Logger LOG = LoggerFactory.getLogger("Новоград");

    /** Мостик к клиентскому коду: общий код не может напрямую открыть экран. */
    public static ClientHooks CLIENT = new ClientHooks() {};

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModContent.init();
        LifeNet.init();
        LifeEvents.init();
        LifeCommands.init();
        LOG.info("Новоград загружен: школа, работа, деньги, машины и город");
    }
}
