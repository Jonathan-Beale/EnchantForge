package com.example.enchantforge.trigger;

import org.bukkit.configuration.ConfigurationSection;

public abstract class EnchantTrigger {

    public static EnchantTrigger fromYaml(ConfigurationSection section) {
        String type = section.getString("type", "").toLowerCase();
        return switch (type) {
            case "on_equip"        -> new OnEquipTrigger();
            case "on_damage_taken" -> new OnDamageTakenTrigger();
            case "on_kill_entity"  -> new OnKillEntityTrigger();
            case "on_deal_damage"  -> new OnDealDamageTrigger();
            case "on_right_click"  -> new OnRightClickTrigger();
            case "on_suit_jump"    -> new OnSuitJumpTrigger();
            case "stat_threshold"  -> StatThresholdTrigger.fromYaml(section);
            default -> throw new IllegalArgumentException("Unknown trigger type: " + type);
        };
    }
}
