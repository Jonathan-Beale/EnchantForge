package com.example.enchantforge.effect;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class HungerEffect implements EnchantEffect {

    private final double foodPerLevel;
    private final double saturationPerLevel;

    private HungerEffect(double foodPerLevel, double saturationPerLevel) {
        this.foodPerLevel = foodPerLevel;
        this.saturationPerLevel = saturationPerLevel;
    }

    public static HungerEffect fromYaml(ConfigurationSection section) {
        double food       = section.getDouble("amountPerLevel", 2.0);
        double saturation = section.getDouble("saturationPerLevel", 1.0);
        return new HungerEffect(food, saturation);
    }

    @Override
    public void apply(Player player, int enchantLevel, int durationTicks) {
        int foodAdd  = (int) Math.round(foodPerLevel * enchantLevel);
        float satAdd = (float) (saturationPerLevel * enchantLevel);
        int newFood  = Math.min(20, player.getFoodLevel() + foodAdd);
        // Saturation cannot exceed the current food level
        float newSat = Math.min(newFood, player.getSaturation() + satAdd);
        player.setFoodLevel(newFood);
        player.setSaturation(newSat);
    }

    @Override
    public void remove(Player player) {
        // Instant effect; nothing to roll back
    }
}
