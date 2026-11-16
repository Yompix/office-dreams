package com.yompix.shaderlite;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Кнопка настроек в Mod Menu (если Mod Menu установлен; без него мод работает, настройки — в файле). */
public final class ModMenuIntegration implements ModMenuApi {
    @Override public ConfigScreenFactory<?> getModConfigScreenFactory() { return ConfigScreen::new; }
}
