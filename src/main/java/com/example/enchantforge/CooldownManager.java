package com.example.enchantforge;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class CooldownManager {

    // playerId -> enchantKey -> expiry time (ms)
    private final Map<UUID, Map<NamespacedKey, Long>> cooldowns = new HashMap<>();
    // playerId -> (enchantKey|itemId) -> expiry time (ms)
    private final Map<UUID, Map<String, Long>> itemCooldowns = new HashMap<>();

    public boolean isOnCooldown(Player player, CustomEnchant enchant) {
        Map<NamespacedKey, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) return false;
        Long expiry = map.get(enchant.getKey());
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            map.remove(enchant.getKey());
            if (map.isEmpty()) cooldowns.remove(player.getUniqueId());
            return false;
        }
        return true;
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
        String key = itemKey(enchant.getKey(), itemId);
        Long expiry = map.get(key);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            map.remove(key);
            if (map.isEmpty()) itemCooldowns.remove(player.getUniqueId());
            return false;
        }
        return true;
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

    // ---- Persistence ----

    public void save(File file) {
        long now = System.currentTimeMillis();
        YamlConfiguration yaml = new YamlConfiguration();

        cooldowns.forEach((uuid, enchantMap) -> enchantMap.forEach((key, expiry) -> {
            if (expiry > now) {
                yaml.set("player-cooldowns." + uuid + "." + key.toString(), expiry);
            }
        }));

        itemCooldowns.forEach((uuid, keyMap) -> keyMap.forEach((compoundKey, expiry) -> {
            if (expiry > now) {
                // compound key may contain dots — use raw path setter via explicit nesting
                yaml.set("item-cooldowns." + uuid + "." + sanitizeKey(compoundKey), expiry);
            }
        }));

        try {
            yaml.save(file);
        } catch (IOException e) {
            Logger.getLogger("EnchantForge").warning("Failed to save cooldowns: " + e.getMessage());
        }
    }

    public void load(File file) {
        if (!file.exists()) return;
        long now = System.currentTimeMillis();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        var playerSection = yaml.getConfigurationSection("player-cooldowns");
        if (playerSection != null) {
            for (String uuidStr : playerSection.getKeys(false)) {
                UUID uuid;
                try { uuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { continue; }
                var enchantSection = playerSection.getConfigurationSection(uuidStr);
                if (enchantSection == null) continue;
                for (String keyStr : enchantSection.getKeys(false)) {
                    long expiry = enchantSection.getLong(keyStr);
                    if (expiry <= now) continue;
                    NamespacedKey key = parseNamespacedKey(keyStr);
                    if (key == null) continue;
                    cooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(key, expiry);
                }
            }
        }

        var itemSection = yaml.getConfigurationSection("item-cooldowns");
        if (itemSection != null) {
            for (String uuidStr : itemSection.getKeys(false)) {
                UUID uuid;
                try { uuid = UUID.fromString(uuidStr); } catch (IllegalArgumentException e) { continue; }
                var entrySection = itemSection.getConfigurationSection(uuidStr);
                if (entrySection == null) continue;
                for (String sanitized : entrySection.getKeys(false)) {
                    long expiry = entrySection.getLong(sanitized);
                    if (expiry <= now) continue;
                    String compoundKey = desanitizeKey(sanitized);
                    itemCooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(compoundKey, expiry);
                }
            }
        }
    }

    // ---- Helpers ----

    private String itemKey(NamespacedKey enchantKey, String itemId) {
        return enchantKey.getNamespace() + ":" + enchantKey.getKey() + "|" + itemId;
    }

    /** YAML keys cannot contain dots; replace them with a safe placeholder. */
    private static String sanitizeKey(String key) {
        return key.replace(".", "\u00B7");
    }

    private static String desanitizeKey(String key) {
        return key.replace("\u00B7", ".");
    }

    private static NamespacedKey parseNamespacedKey(String s) {
        int idx = s.indexOf(':');
        if (idx < 1 || idx >= s.length() - 1) return null;
        return new NamespacedKey(s.substring(0, idx), s.substring(idx + 1));
    }
}
