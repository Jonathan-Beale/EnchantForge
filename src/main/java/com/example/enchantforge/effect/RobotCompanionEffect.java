package com.example.enchantforge.effect;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class RobotCompanionEffect implements EnchantEffect {

    public static RobotCompanionEffect fromYaml(ConfigurationSection section) {
        return new RobotCompanionEffect();
    }

    @Override
    public String id() { return "robot_companion"; }

    @Override
    public void apply(Player player, int level, int durationTicks) {
        RobotCompanionManager.summon(player);
    }

    @Override
    public void remove(Player player) {
        RobotCompanionManager.dismiss(player);
    }
}
