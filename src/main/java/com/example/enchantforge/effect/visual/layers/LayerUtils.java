package com.example.enchantforge.effect.visual.layers;

import org.bukkit.Color;
import java.util.Map;

final class LayerUtils {
    private LayerUtils() {}

    static int intVal(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) try { return Integer.parseInt(String.valueOf(v)); } catch (NumberFormatException ignored) {}
        return def;
    }

    static double doubleVal(Map<?, ?> map, String key, double def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) try { return Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException ignored) {}
        return def;
    }

    static Color parseColor(Object raw) {
        if (raw == null) return null;
        String[] parts = String.valueOf(raw).split(",");
        if (parts.length != 3) return null;
        try {
            return Color.fromRGB(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
