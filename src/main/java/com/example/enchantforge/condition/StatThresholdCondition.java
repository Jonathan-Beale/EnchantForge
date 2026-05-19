package com.example.enchantforge.condition;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class StatThresholdCondition implements EndCondition {

    private final String stat;
    private final String comparison;
    private final double value;

    public StatThresholdCondition(String stat, String comparison, double value) {
        this.stat = stat.toLowerCase();
        this.comparison = comparison.toLowerCase();
        this.value = value;
    }

    public static StatThresholdCondition fromYaml(ConfigurationSection section) {
        String stat = section.getString("stat");
        String comparison = section.getString("comparison");
        if (stat == null || stat.isBlank())
            throw new IllegalArgumentException("stat_threshold condition missing required field 'stat'");
        if (comparison == null || comparison.isBlank())
            throw new IllegalArgumentException("stat_threshold condition missing required field 'comparison'");
        return new StatThresholdCondition(stat, comparison, section.getDouble("value"));
    }

    public boolean matches(Player player, double resultingHealth) {
        double statValue = stat.equals("health") ? resultingHealth : getAttributeValue(player);
        return switch (comparison) {
            case "below" -> statValue < value;
            case "above" -> statValue > value;
            default -> throw new IllegalStateException("Invalid comparison: " + comparison);
        };
    }

    @Override public boolean requiresTracking() { return true; }
    @Override public int getEffectDurationTicks() { return -1; }
    @Override public String getDisplayLabel() { return "until " + stat + " " + comparison + " " + (int) value; }

    private double getAttributeValue(Player player) {
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(stat));
        if (attr == null) {
            player.getServer().getLogger().warning("Unknown attribute '" + stat + "' in stat_threshold condition");
            return 0;
        }
        AttributeInstance instance = player.getAttribute(attr);
        return instance != null ? instance.getValue() : 0;
    }
}
