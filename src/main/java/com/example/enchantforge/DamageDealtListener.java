package com.example.enchantforge;

import com.example.enchantforge.effect.EnchantEffectContext;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

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

        Map<org.bukkit.NamespacedKey, List<Integer>> triggered = TriggerDispatchService.newTriggeredMap();
        TriggerDispatchService.collectTriggered(
                triggered,
                registry.getEnchants(weapon),
                "on_deal_damage",
                player,
            cooldowns,
            weapon
        );

        TriggerDispatchService.executeTriggered(
                triggered,
                registry,
                player,
                (enchant, effectiveLevel) -> {
            EnchantEffectContext context = EnchantEffectContext.fromDealDamage(finalDamage);
            enchant.apply(player, effectiveLevel, context);
            EnchantDebug.log(enchant, player, "deal-damage-triggered lv" + effectiveLevel
                    + " (+" + String.format("%.1f", finalDamage) + " dmg)");
                },
                enchant -> {
                    cooldowns.setCooldown(player, enchant, weapon);
                    CooldownVisuals.applyMainHandCooldown(player, enchant.getCooldownTicks());
                    EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
                },
                "deal-damage-triggered"
        );
    }
}
