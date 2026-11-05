package com.yompix.lifecraft;

import com.yompix.lifecraft.entity.CarEntity;

public interface ClientHooks {
    default void openPhone() {}

    default void carTick(CarEntity car) {}
}
