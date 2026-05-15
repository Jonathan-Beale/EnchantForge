package com.example.enchantforge.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PotionStyleEffect implements EnchantEffect {

    private final PotionEffectType potionType;

    private PotionStyleEffect(PotionEffectType potionType) {
        this.potionType = potionType;
    }

    public static PotionStyleEffect fromYaml(ConfigurationSection section) {
        String name = section.getString("potion", "").toLowerCase();
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name));
        if (type == null) throw new IllegalArgumentException("Unknown potion effect: " + name);
        return new PotionStyleEffect(type);
    }

    @Override
    public void apply(Player player, int enchantLevel, int durationTicks) {
        int ticks = durationTicks < 0 ? Integer.MAX_VALUE : durationTicks;
        player.addPotionEffect(new PotionEffect(
                potionType,
                ticks,
                enchantLevel - 1, // amplifier is 0-indexed
                true, true, true
        ));
    }

    @Override
    public void remove(Player player) {
        player.removePotionEffect(potionType);
    }
}
