package com.example.enchantforge;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class EntityKillListener implements Listener {

    private final EnchantmentRegistry registry;
    private final CooldownManager cooldowns;

    public EntityKillListener(EnchantmentRegistry registry, CooldownManager cooldowns) {
        this.registry = registry;
        this.cooldowns = cooldowns;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR) return;

        Map<org.bukkit.NamespacedKey, List<Integer>> triggered = TriggerDispatchService.newTriggeredMap();
        TriggerDispatchService.collectTriggered(
                triggered,
                registry.getEnchants(weapon),
                "on_kill_entity",
                killer,
            cooldowns,
            weapon
        );

        TriggerDispatchService.executeTriggered(
                triggered,
                registry,
                killer,
                (enchant, effectiveLevel) -> enchant.apply(killer, effectiveLevel),
                enchant -> {
                    cooldowns.setCooldown(killer, enchant, weapon);
                    CooldownVisuals.applyMainHandCooldown(killer, enchant.getCooldownTicks());
                    EnchantDebug.log(enchant, killer, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
                },
                "kill-triggered"
        );
    }
}
