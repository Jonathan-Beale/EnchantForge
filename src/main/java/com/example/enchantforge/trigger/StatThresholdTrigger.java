package com.example.enchantforge.trigger;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class StatThresholdTrigger extends EnchantTrigger {

    private final String stat;
    private final String comparison; // "below" or "above"
    private final double value;

    public StatThresholdTrigger(String stat, String comparison, double value) {
        this.stat = stat.toLowerCase();
        this.comparison = comparison.toLowerCase();
        this.value = value;
    }

    public static StatThresholdTrigger fromYaml(ConfigurationSection section) {
        String stat = section.getString("stat");
        String comparison = section.getString("comparison");
        if (stat == null || stat.isBlank())
            throw new IllegalArgumentException("stat_threshold trigger missing required field 'stat'");
        if (comparison == null || comparison.isBlank())
            throw new IllegalArgumentException("stat_threshold trigger missing required field 'comparison'");
        return new StatThresholdTrigger(stat, comparison, section.getDouble("value"));
    }

    /** Returns the current or projected stat value for this player. */
    public double getStatValue(Player player, double resultingHealth) {
        if (stat.equals("health")) return resultingHealth;
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(stat));
        if (attr == null) {
            Plugin p = player.getServer().getPluginManager().getPlugin("EnchantForge");
            if (p != null) p.getLogger().warning("Unknown attribute '" + stat + "' in stat_threshold trigger");
            return 0;
        }
        AttributeInstance instance = player.getAttribute(attr);
        return instance != null ? instance.getValue() : 0;
    }

    public boolean matches(Player player, double resultingHealth) {
        double statValue = getStatValue(player, resultingHealth);
        return switch (comparison) {
            case "below" -> statValue < value;
            case "above" -> statValue > value;
            default -> throw new IllegalStateException("Invalid comparison: " + comparison);
        };
    }

    @Override
    public String id() {
        return "stat_threshold";
    }

    public String getStat() { return stat; }
    public String getComparison() { return comparison; }
    public double getValue() { return value; }
}
