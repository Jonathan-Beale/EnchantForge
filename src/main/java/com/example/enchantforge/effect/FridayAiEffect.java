package com.example.enchantforge.effect;

import com.example.enchantforge.SuitListener;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class FridayAiEffect implements EnchantEffect {

    private final double glowRadius;

    public FridayAiEffect(double glowRadius) {
        this.glowRadius = glowRadius;
    }

    public static FridayAiEffect fromYaml(ConfigurationSection section) {
        double radius = section.getDouble("glowRadius", 0.0);
        return new FridayAiEffect(radius);
    }

    @Override
    public String id() { return "friday_ai"; }

    @Override
    public void apply(Player player, int level, int durationTicks) {
        SuitListener suit = SuitListener.getInstance();
        if (suit != null) suit.activateSuit(player, glowRadius);
    }

    @Override
    public void remove(Player player) {
        SuitListener suit = SuitListener.getInstance();
        if (suit != null) suit.deactivateSuit(player);
    }
}
