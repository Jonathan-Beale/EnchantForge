package com.example.enchantforge.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;

public interface EnchantEffect {

    default String id() {
        return getClass().getSimpleName().replace("Effect", "").toLowerCase(Locale.ROOT);
    }

    /** Apply this effect to the player. durationTicks is -1 for indefinite (manually removed). */
    void apply(Player player, int enchantLevel, int durationTicks);

    /**
     * Apply this effect with optional trigger context. Effects that do not need context can
     * keep implementing the legacy apply overload.
     */
    default void apply(Player player, int enchantLevel, int durationTicks, EnchantEffectContext context) {
        apply(player, enchantLevel, durationTicks);
    }

    /** Remove this effect from the player. */
    void remove(Player player);

    static EnchantEffect fromYaml(NamespacedKey key, ConfigurationSection section) {
        return EnchantEffectTypeRegistry.fromYaml(key, section);
    }
}
