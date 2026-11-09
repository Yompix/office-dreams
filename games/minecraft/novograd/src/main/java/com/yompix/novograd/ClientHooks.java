package com.yompix.novograd;

import com.yompix.novograd.entity.CarEntity;

public interface ClientHooks {
    default void openPhone() {}

    default void carTick(CarEntity car) {}

    /** Игрок на этом клиенте (null на сервере). */
    default net.minecraft.entity.Entity player() {
        return null;
    }
}
