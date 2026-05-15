package com.example.enchantforge.trigger;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public final class StatThresholdTrigger extends EnchantTrigger {

    private final String stat;
    private final String comparison; // "below" or "above"
    private final double value;

    public StatThresholdTrigger(String stat, String comparison, double value) {
        this.stat = stat.toLowerCase();
        this.comparison = comparison.toLowerCase();
        this.value = value;
    }

    public static StatThresholdTrigger fromYaml(org.bukkit.configuration.ConfigurationSection section) {
        return new StatThresholdTrigger(
                section.getString("stat"),
                section.getString("comparison"),
                section.getDouble("value")
        );
    }

    /** Returns the current or projected stat value for this player. */
    public double getStatValue(Player player, double resultingHealth) {
        if (stat.equals("health")) return resultingHealth;
        try {
            Attribute attr = (Attribute) Attribute.class.getField(stat.toUpperCase()).get(null);
            AttributeInstance instance = player.getAttribute(attr);
            return instance != null ? instance.getValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean matches(Player player, double resultingHealth) {
        double statValue = getStatValue(player, resultingHealth);
        return comparison.equals("below") ? statValue < value : statValue > value;
    }

    public String getStat() { return stat; }
    public String getComparison() { return comparison; }
    public double getValue() { return value; }
}
