package com.example.enchantforge.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public interface EnchantEffect {

    /** Apply this effect to the player. durationTicks is -1 for indefinite (manually removed). */
    void apply(Player player, int enchantLevel, int durationTicks);

    /** Remove this effect from the player. */
    void remove(Player player);

    static EnchantEffect fromYaml(NamespacedKey key, ConfigurationSection section) {
        String style = section.getString("style", "").toLowerCase();
        return switch (style) {
            case "attribute"  -> AttributeStyleEffect.fromYaml(key, section);
            case "potion"     -> PotionStyleEffect.fromYaml(section);
            case "absorption" -> new AbsorptionEffect(new NamespacedKey(key.getNamespace(), key.getKey() + "_abs"));
            case "hunger"     -> HungerEffect.fromYaml(section);
            case "heal"              -> HealEffect.fromYaml(key, section);
            case "full_invisibility" -> FullInvisibilityEffect.INSTANCE;
            case "wolf_form"         -> WolfFormEffect.INSTANCE;
            case "eye_laser"         -> EyeLaserEffect.fromYaml(section);
            case "thruster"          -> ThrusterEffect.fromYaml(section);
            case "hand_laser"        -> HandLaserEffect.fromYaml(section);
            case "friday_ai"         -> FridayAiEffect.INSTANCE;
            default -> throw new IllegalArgumentException("Unknown effect style: " + style);
        };
    }
}
