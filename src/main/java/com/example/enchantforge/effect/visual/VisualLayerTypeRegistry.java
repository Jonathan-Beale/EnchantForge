package com.example.enchantforge.effect.visual;

import com.example.enchantforge.effect.visual.layers.BeamLayer;
import com.example.enchantforge.effect.visual.layers.BurstLayer;
import com.example.enchantforge.effect.visual.layers.ParticleLayer;
import com.example.enchantforge.effect.visual.layers.SoundLayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public final class VisualLayerTypeRegistry {

    private static final Map<String, Function<Map<?, ?>, VisualLayer>> FACTORIES = new HashMap<>();

    static {
        register("particle", ParticleLayer::fromMap);
        register("burst",    BurstLayer::fromMap);
        register("sound",    SoundLayer::fromMap);
        register("beam",     BeamLayer::fromMap);
    }

    private VisualLayerTypeRegistry() {}

    public static void register(String type, Function<Map<?, ?>, VisualLayer> factory) {
        FACTORIES.put(type.toLowerCase(Locale.ROOT), factory);
    }

    public static VisualLayer fromMap(String type, Map<?, ?> map) {
        if (type == null) return null;
        Function<Map<?, ?>, VisualLayer> factory = FACTORIES.get(type.toLowerCase(Locale.ROOT));
        return factory != null ? factory.apply(map) : null;
    }
}
