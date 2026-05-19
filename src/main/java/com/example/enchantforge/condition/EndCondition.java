package com.example.enchantforge.condition;

import org.bukkit.configuration.ConfigurationSection;

public interface EndCondition {

    /** Whether active effects with this condition must be tracked and removed by event. */
    boolean requiresTracking();

    /** Ticks to pass to the effect's apply method. -1 means apply indefinitely (manually removed). */
    int getEffectDurationTicks();

    /** Human-readable label for lore display (e.g. "30s", "∞", "until hit"). */
    String getDisplayLabel();

    static EndCondition fromYaml(ConfigurationSection section) {
        String type = section.getString("type", "").toLowerCase();
        return switch (type) {
            case "never"           -> NeverCondition.INSTANCE;
            case "time"            -> new TimeCondition(section.getInt("ticks"));
            case "until_condition" -> parseUntilCondition(section.getString("condition", "").toLowerCase(), section);
            default -> throw new IllegalArgumentException("Unknown duration type: " + type);
        };
    }

    private static EndCondition parseUntilCondition(String condition, ConfigurationSection section) {
        return switch (condition) {
            case "damaged"                  -> DamagedCondition.INSTANCE;
            case "full_health_or_damaged"  -> FullHealthOrDamagedCondition.INSTANCE;
            case "stat_threshold"      -> StatThresholdCondition.fromYaml(section);
            case "absorption_depleted" -> AbsorptionDepletedCondition.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown until_condition: " + condition);
        };
    }
}
