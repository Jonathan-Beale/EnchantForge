package com.example.enchantforge.effect.visual;

import java.util.Locale;

public enum CuePoint {
    ON_FIRE, ON_HIT, ON_MISS, ON_FAIL;

    public String yamlKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
