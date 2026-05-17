package com.example.enchantforge;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    // playerId -> enchantKey -> expiry time (ms)
    private final Map<UUID, Map<NamespacedKey, Long>> cooldowns = new HashMap<>();
    // playerId -> (enchantKey|itemId) -> expiry time (ms)
    private final Map<UUID, Map<String, Long>> itemCooldowns = new HashMap<>();

    public boolean isOnCooldown(Player player, CustomEnchant enchant) {
        Map<NamespacedKey, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) return false;
        Long expiry = map.get(enchant.getKey());
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public long getRemainingSeconds(Player player, CustomEnchant enchant) {
        Map<NamespacedKey, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) return 0;
        Long expiry = map.get(enchant.getKey());
        if (expiry == null) return 0;
        return Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
    }

    public long getRemainingMs(Player player, CustomEnchant enchant) {
        Map<NamespacedKey, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) return 0;
        Long expiry = map.get(enchant.getKey());
        if (expiry == null) return 0;
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    public void setCooldown(Player player, CustomEnchant enchant) {
        long expiryMs = System.currentTimeMillis() + (enchant.getCooldownTicks() * 50L);
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(enchant.getKey(), expiryMs);
    }

    public boolean isOnCooldown(Player player, CustomEnchant enchant, ItemStack item) {
        String itemId = CooldownVisuals.readItemId(item);
        if (itemId == null || itemId.isBlank()) {
            return isOnCooldown(player, enchant);
        }
        Map<String, Long> map = itemCooldowns.get(player.getUniqueId());
        if (map == null) return false;
        Long expiry = map.get(itemKey(enchant.getKey(), itemId));
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public void setCooldown(Player player, CustomEnchant enchant, ItemStack item) {
        String itemId = CooldownVisuals.ensureItemIdForTracking(player, item, false);
        if (itemId == null || itemId.isBlank()) {
            setCooldown(player, enchant);
            return;
        }
        long expiryMs = System.currentTimeMillis() + (enchant.getCooldownTicks() * 50L);
        itemCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(itemKey(enchant.getKey(), itemId), expiryMs);
    }

    public void clearPlayer(UUID playerId) {
        cooldowns.remove(playerId);
        itemCooldowns.remove(playerId);
    }

    private String itemKey(NamespacedKey enchantKey, String itemId) {
        return enchantKey.getNamespace() + ":" + enchantKey.getKey() + "|" + itemId;
    }
}
