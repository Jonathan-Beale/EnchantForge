package com.example.enchantforge.trigger;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class EnchantTriggerTypeRegistry {

    private static final Map<String, Function<ConfigurationSection, EnchantTrigger>> FACTORIES = new HashMap<>();

    static {
        register("on_equip", section -> new OnEquipTrigger());
        register("on_damage_taken", section -> new OnDamageTakenTrigger());
        register("on_kill_entity", section -> new OnKillEntityTrigger());
        register("on_deal_damage", section -> new OnDealDamageTrigger());
        register("on_right_click", section -> new OnRightClickTrigger());
        register("on_suit_jump", section -> new OnSuitJumpTrigger());
        register("stat_threshold", StatThresholdTrigger::fromYaml);
    }

    private EnchantTriggerTypeRegistry() {}

    public static void register(String type, Function<ConfigurationSection, EnchantTrigger> factory) {
        if (type == null || type.isBlank() || factory == null) {
            throw new IllegalArgumentException("Trigger type and factory must be provided");
        }
        FACTORIES.put(type.toLowerCase(), factory);
    }

    public static EnchantTrigger fromYaml(ConfigurationSection section) {
        if (section == null) throw new IllegalArgumentException("Missing trigger section");
        String type = section.getString("type", "").toLowerCase();
        Function<ConfigurationSection, EnchantTrigger> factory = FACTORIES.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown trigger type: " + type);
        }
        return factory.apply(section);
    }
}