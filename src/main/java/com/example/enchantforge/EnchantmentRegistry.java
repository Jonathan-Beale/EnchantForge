package com.example.enchantforge;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class EnchantmentRegistry {

    private final Map<NamespacedKey, CustomEnchant> enchants = new LinkedHashMap<>();

    public void register(CustomEnchant enchant) {
        enchants.put(enchant.getKey(), enchant);
    }

    public void clear() {
        enchants.clear();
    }

    public CustomEnchant get(NamespacedKey key) {
        return enchants.get(key);
    }

    public Collection<CustomEnchant> getAll() {
        return Collections.unmodifiableCollection(enchants.values());
    }

    /** Returns all custom enchantments present on the item and their levels. */
    public Map<CustomEnchant, Integer> getEnchants(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return Map.of();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Map.of();
        var pdc = meta.getPersistentDataContainer();
        Map<CustomEnchant, Integer> result = new LinkedHashMap<>();
        for (CustomEnchant enchant : enchants.values()) {
            Integer level = pdc.get(enchant.getKey(), PersistentDataType.INTEGER);
            if (level != null) result.put(enchant, level);
        }
        return result;
    }

    /** Returns the level of a specific enchantment on the item, or 0 if absent. */
    public int getLevel(ItemStack item, CustomEnchant enchant) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer level = meta.getPersistentDataContainer().get(enchant.getKey(), PersistentDataType.INTEGER);
        return level != null ? level : 0;
    }
}
