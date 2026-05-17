package com.example.enchantforge.trigger;

import org.bukkit.configuration.ConfigurationSection;

public abstract class EnchantTrigger {

    public abstract String id();

    public static EnchantTrigger fromYaml(ConfigurationSection section) {
        return EnchantTriggerTypeRegistry.fromYaml(section);
    }
}
