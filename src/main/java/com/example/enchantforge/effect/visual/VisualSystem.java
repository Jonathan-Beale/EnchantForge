package com.example.enchantforge.effect.visual;

import com.example.enchantforge.effect.EnchantEffectContext;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class VisualSystem {

    public static final VisualSystem NONE = new VisualSystem(Map.of());

    private final Map<CuePoint, List<VisualLayer>> bindings;

    private VisualSystem(Map<CuePoint, List<VisualLayer>> bindings) {
        this.bindings = bindings;
    }

    public void play(CuePoint cue, Player player, EnchantEffectContext context) {
        List<VisualLayer> layers = bindings.get(cue);
        if (layers == null) return;
        VisualLayerContext ctx = new VisualLayerContext(player, context);
        for (VisualLayer layer : layers) layer.play(ctx);
    }

    public void play(CuePoint cue, Player player, EnchantEffectContext context, Location hitLocation) {
        List<VisualLayer> layers = bindings.get(cue);
        if (layers == null) return;
        VisualLayerContext ctx = new VisualLayerContext(player, context).withHitLocation(hitLocation);
        for (VisualLayer layer : layers) layer.play(ctx);
    }

    public static VisualSystem fromYaml(ConfigurationSection effectSection) {
        if (effectSection == null) return NONE;
        ConfigurationSection vs = effectSection.getConfigurationSection("visuals");
        if (vs == null) return NONE;

        Map<CuePoint, List<VisualLayer>> bindings = new EnumMap<>(CuePoint.class);
        for (CuePoint cue : CuePoint.values()) {
            List<?> rawList = vs.getList(cue.yamlKey());
            if (rawList == null || rawList.isEmpty()) continue;
            List<VisualLayer> layers = new ArrayList<>();
            for (Object entry : rawList) {
                if (!(entry instanceof Map<?, ?> map)) continue;
                Object typeObj = map.get("type");
                if (typeObj == null) continue;
                VisualLayer layer = VisualLayerTypeRegistry.fromMap(String.valueOf(typeObj), map);
                if (layer != null) layers.add(layer);
            }
            if (!layers.isEmpty()) bindings.put(cue, List.copyOf(layers));
        }
        return bindings.isEmpty() ? NONE : new VisualSystem(bindings);
    }
}
