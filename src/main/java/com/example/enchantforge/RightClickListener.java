package com.example.enchantforge;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
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
    private final PlayerEnchantIndex enchantIndex;

    /** Players currently under a timed full-invisibility effect from this listener. */
    private final Set<UUID> invisible = new HashSet<>();

    /** Deduplicates right-click events — Bukkit fires once per hand, we only want one per click. */
    private final Set<UUID> firedThisTick = new HashSet<>();

    public RightClickListener(EnchantmentRegistry registry, CooldownManager cooldowns, Plugin plugin,
                              PlayerEnchantIndex enchantIndex) {
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.plugin = plugin;
        this.enchantIndex = enchantIndex;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (isCooldownVisualDebugEnabled()) {
            plugin.getLogger().info("[cooldown-visuals] interact action=" + action
                + " hand=" + event.getHand()
                + " player=" + player.getName());
        }
        // Deduplicate: Bukkit fires this event once per hand; only process the first one per click
        if (!firedThisTick.add(player.getUniqueId())) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> firedThisTick.remove(player.getUniqueId()));
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        Map<NamespacedKey, List<Integer>> triggered = new LinkedHashMap<>();

        // Check main hand
        if (mainHand.getType() != Material.AIR) {
            registry.getEnchants(mainHand).forEach((enchant, level) -> {
                if (!"on_right_click".equals(enchant.getTrigger().id())) return;
                if (cooldowns.isOnCooldown(player, enchant, mainHand)) return;
                triggered.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
            });
        }

        // Check equipped armor slots via index
        for (PlayerEnchantIndex.SlottedEnchant se : enchantIndex.getByTrigger(player.getUniqueId(), "on_right_click")) {
            ItemStack armorItem = player.getInventory().getItem(se.slot());
            if (armorItem == null || armorItem.getType().isAir()) continue;
            if (cooldowns.isOnCooldown(player, se.enchant(), armorItem)) continue;
            triggered.computeIfAbsent(se.enchant().getKey(), k -> new ArrayList<>()).add(se.level());
        }

        // Check off-hand (gauntlet slot)
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand.getType() != Material.AIR) {
            registry.getEnchants(offHand).forEach((enchant, level) -> {
                if (!"on_right_click".equals(enchant.getTrigger().id())) return;
                if (cooldowns.isOnCooldown(player, enchant, offHand)) return;
                triggered.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
            });
        }

        if (triggered.isEmpty()) {
            if (isCooldownVisualDebugEnabled()) {
                logRightClickCandidates(player, mainHand, offHand);
                plugin.getLogger().info("[cooldown-visuals] no right-click enchant triggered for player=" + player.getName());
            }
            return;
        }
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
                CooldownVisualSource visualSource = findCooldownVisualSource(player, enchant, mainHand, offHand);
                if (visualSource != null && visualSource.item() != null && visualSource.item().getType() != Material.AIR) {
                    cooldowns.setCooldown(player, enchant, visualSource.item());
                    if (visualSource.mainHand()) {
                        CooldownVisuals.applyMainHandCooldown(player, enchant.getCooldownTicks());
                    } else {
                        CooldownVisuals.applyItemCooldown(player, visualSource.item(), enchant.getCooldownTicks());
                    }
                    if (isCooldownVisualDebugEnabled()) {
                        plugin.getLogger().info("[cooldown-visuals] right-click source=" + visualSource.item().getType()
                                + " slot=" + visualSource.slot()
                                + " enchant=" + enchant.getKey().getKey()
                                + " player=" + player.getName()
                                + " ticks=" + enchant.getCooldownTicks());
                    }
                } else if (isCooldownVisualDebugEnabled()) {
                    plugin.getLogger().info("[cooldown-visuals] right-click no source enchant="
                            + enchant.getKey().getKey() + " player=" + player.getName());
                } else {
                    cooldowns.setCooldown(player, enchant);
                }
                EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
            }
        });
    }

    private CooldownVisualSource findCooldownVisualSource(Player player, CustomEnchant enchant, ItemStack mainHand, ItemStack offHand) {
        if (mainHand != null && mainHand.getType() != Material.AIR && registry.getLevel(mainHand, enchant) > 0) {
            return new CooldownVisualSource(player.getInventory().getItemInMainHand(), true, "main");
        }
        if (offHand != null && offHand.getType() != Material.AIR && registry.getLevel(offHand, enchant) > 0) {
            return new CooldownVisualSource(player.getInventory().getItemInOffHand(), false, "off");
        }
        for (PlayerEnchantIndex.SlottedEnchant se : enchantIndex.getByTrigger(player.getUniqueId(), "on_right_click")) {
            if (se.enchant().getKey().equals(enchant.getKey())) {
                return new CooldownVisualSource(player.getInventory().getItem(se.slot()), false, "armor");
            }
        }
        return null;
    }

    private void logRightClickCandidates(Player player, ItemStack mainHand, ItemStack offHand) {
        List<String> candidates = new ArrayList<>();
        collectRightClickCandidates(player, candidates, "main", registry.getEnchants(mainHand));
        collectRightClickCandidates(player, candidates, "off", registry.getEnchants(offHand));
        for (PlayerEnchantIndex.SlottedEnchant se : enchantIndex.getByTrigger(player.getUniqueId(), "on_right_click")) {
            boolean onCooldown = cooldowns.isOnCooldown(player, se.enchant());
            candidates.add("candidate slot=armor enchant=" + se.enchant().getKey().getKey()
                    + " level=" + se.level() + " onCooldown=" + onCooldown);
        }
        if (candidates.isEmpty()) {
            plugin.getLogger().info("[cooldown-visuals] no on_right_click candidates found for player=" + player.getName());
            return;
        }
        for (String line : candidates) {
            plugin.getLogger().info("[cooldown-visuals] " + line);
        }
    }

    private void collectRightClickCandidates(Player player, List<String> out, String slot, Map<CustomEnchant, Integer> enchants) {
        enchants.forEach((enchant, level) -> {
            if (!"on_right_click".equals(enchant.getTrigger().id())) return;
                boolean onCooldown = cooldowns.isOnCooldown(player, enchant);
            out.add("candidate slot=" + slot
                    + " enchant=" + enchant.getKey().getKey()
                    + " level=" + level
                    + " onCooldown=" + onCooldown);
        });
    }

    private boolean isCooldownVisualDebugEnabled() {
        return plugin.getConfig().getBoolean("debug.cooldownVisuals", false);
    }

    private record CooldownVisualSource(ItemStack item, boolean mainHand, String slot) {}

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
