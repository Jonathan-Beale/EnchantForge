package com.example.enchantforge;

import com.example.enchantforge.trigger.OnKillEntityTrigger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

        Map<NamespacedKey, List<Integer>> triggered = new LinkedHashMap<>();
        registry.getEnchants(weapon).forEach((enchant, level) -> {
            if (!(enchant.getTrigger() instanceof OnKillEntityTrigger)) return;
            if (cooldowns.isOnCooldown(killer, enchant)) return;
            triggered.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
        });

        triggered.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            enchant.apply(killer, effectiveLevel);
            EnchantDebug.log(enchant, killer, "kill-triggered lv" + effectiveLevel);
            if (enchant.hasCooldown()) {
                cooldowns.setCooldown(killer, enchant);
                killer.setCooldown(weapon.getType(), enchant.getCooldownTicks());
                EnchantDebug.log(enchant, killer, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
            }
        });
    }
}
