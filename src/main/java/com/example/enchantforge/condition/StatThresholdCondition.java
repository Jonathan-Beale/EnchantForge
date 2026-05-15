package com.example.enchantforge.condition;

import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.attribute.AttributeInstance;
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
        return new StatThresholdCondition(
                section.getString("stat"),
                section.getString("comparison"),
                section.getDouble("value")
        );
    }

    public boolean matches(Player player, double resultingHealth) {
        double statValue = stat.equals("health") ? resultingHealth : getAttributeValue(player);
        return comparison.equals("above") ? statValue > value : statValue < value;
    }

    @Override public boolean requiresTracking() { return true; }
    @Override public int getEffectDurationTicks() { return -1; }
    @Override public String getDisplayLabel() { return "until " + stat + " " + comparison + " " + (int) value; }

    private double getAttributeValue(Player player) {
        try {
            Attribute attr = (Attribute) Attribute.class.getField(stat.toUpperCase()).get(null);
            AttributeInstance instance = player.getAttribute(attr);
            return instance != null ? instance.getValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
