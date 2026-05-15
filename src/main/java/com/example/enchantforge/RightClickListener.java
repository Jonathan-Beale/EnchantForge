package com.example.enchantforge;

import com.example.enchantforge.trigger.OnRightClickTrigger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RightClickListener implements Listener {

    private final EnchantmentRegistry registry;
    private final CooldownManager cooldowns;
    private final Plugin plugin;

    /** Players currently under a timed full-invisibility effect from this listener. */
    private final Set<UUID> invisible = new HashSet<>();

    /** Deduplicates right-click events — Bukkit fires once per hand, we only want one per click. */
    private final Set<UUID> firedThisTick = new HashSet<>();

    public RightClickListener(EnchantmentRegistry registry, CooldownManager cooldowns, Plugin plugin) {
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        // Deduplicate: Bukkit fires this event once per hand; only process the first one per click
        if (!firedThisTick.add(player.getUniqueId())) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> firedThisTick.remove(player.getUniqueId()));
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        Map<NamespacedKey, List<Integer>> triggered = new LinkedHashMap<>();

        // Check main hand
        if (mainHand.getType() != Material.AIR) {
            registry.getEnchants(mainHand).forEach((enchant, level) -> {
                if (!(enchant.getTrigger() instanceof OnRightClickTrigger)) return;
                if (cooldowns.isOnCooldown(player, enchant)) return;
                triggered.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
            });
        }

        // Check equipped armor slots
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType() == Material.AIR) continue;
            registry.getEnchants(armor).forEach((enchant, level) -> {
                if (!(enchant.getTrigger() instanceof OnRightClickTrigger)) return;
                if (cooldowns.isOnCooldown(player, enchant)) return;
                triggered.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
            });
        }

        // Check off-hand (gauntlet slot)
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() != Material.AIR) {
            registry.getEnchants(offHand).forEach((enchant, level) -> {
                if (!(enchant.getTrigger() instanceof OnRightClickTrigger)) return;
                if (cooldowns.isOnCooldown(player, enchant)) return;
                triggered.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
            });
        }

        if (triggered.isEmpty()) return;
        event.setCancelled(true);

        triggered.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            enchant.apply(player, effectiveLevel);
            EnchantDebug.log(enchant, player, "right-click triggered lv" + effectiveLevel);

            int durationTicks = enchant.getEndCondition().getEffectDurationTicks();
            if (durationTicks > 0) {
                UUID playerId = player.getUniqueId();
                invisible.add(playerId);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    invisible.remove(playerId);
                    Player p = plugin.getServer().getPlayer(playerId);
                    if (p != null) {
                        enchant.remove(p);
                        EnchantDebug.log(enchant, p, "effect expired (duration elapsed)");
                    }
                }, durationTicks);
            }

            if (enchant.hasCooldown()) {
                cooldowns.setCooldown(player, enchant);
                if (mainHand.getType() != Material.AIR)
                    player.setCooldown(mainHand.getType(), enchant.getCooldownTicks());
                EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
            }
        });
    }

    /** Mobs cannot target shadow-veiled players. */
    @EventHandler
    public void onMobTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        if (invisible.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Entity metadata resets on reconnect, so just purge the tracking entry.
        invisible.remove(event.getPlayer().getUniqueId());
    }
}
