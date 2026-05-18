package com.example.enchantforge;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ActiveEffectTracker {

    // playerId -> enchantKey -> effective level
    private final Map<UUID, Map<NamespacedKey, Integer>> active = new HashMap<>();

    public void track(Player player, CustomEnchant enchant, int level) {
        active.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashMap<>())
                .put(enchant.getKey(), level);
    }

    public boolean isTracked(Player player, CustomEnchant enchant) {
        Map<NamespacedKey, Integer> map = active.get(player.getUniqueId());
        return map != null && map.containsKey(enchant.getKey());
    }

    public boolean hasActive(UUID playerId) {
        Map<NamespacedKey, Integer> map = active.get(playerId);
        return map != null && !map.isEmpty();
    }

    public Map<NamespacedKey, Integer> getActive(UUID playerId) {
        return active.getOrDefault(playerId, Collections.emptyMap());
    }

    public void remove(UUID playerId, NamespacedKey key) {
        Map<NamespacedKey, Integer> map = active.get(playerId);
        if (map != null) {
            map.remove(key);
            if (map.isEmpty()) active.remove(playerId);
        }
    }

    public void clearPlayer(UUID playerId) {
        active.remove(playerId);
    }
}
