package com.example.enchantforge;

import com.example.enchantforge.effect.HealEffect;
import com.example.enchantforge.trigger.OnDealDamageTrigger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DamageDealtListener implements Listener {

    private final EnchantmentRegistry registry;
    private final CooldownManager cooldowns;

    public DamageDealtListener(EnchantmentRegistry registry, CooldownManager cooldowns) {
        this.registry = registry;
        this.cooldowns = cooldowns;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR) return;

        double finalDamage = event.getFinalDamage();
        if (finalDamage <= 0) return;

        Map<NamespacedKey, List<Integer>> triggered = new LinkedHashMap<>();
        registry.getEnchants(weapon).forEach((enchant, level) -> {
            if (!(enchant.getTrigger() instanceof OnDealDamageTrigger)) return;
            if (cooldowns.isOnCooldown(player, enchant)) return;
            triggered.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
        });

        triggered.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            if (enchant.getEffect() instanceof HealEffect healEffect) {
                healEffect.healForDamage(player, effectiveLevel, finalDamage);
            } else {
                enchant.apply(player, effectiveLevel);
            }
            EnchantDebug.log(enchant, player, "deal-damage-triggered lv" + effectiveLevel
                    + " (+" + String.format("%.1f", finalDamage) + " dmg)");
            if (enchant.hasCooldown()) {
                cooldowns.setCooldown(player, enchant);
                player.setCooldown(weapon.getType(), enchant.getCooldownTicks());
                EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
            }
        });
    }
}
