package com.example.enchantforge;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    // playerId -> enchantKey -> expiry time (ms)
    private final Map<UUID, Map<NamespacedKey, Long>> cooldowns = new HashMap<>();

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

    public void clearPlayer(UUID playerId) {
        cooldowns.remove(playerId);
    }
}
