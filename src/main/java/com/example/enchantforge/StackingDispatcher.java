package com.example.enchantforge;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class StackingDispatcher {

    private StackingDispatcher() {}

    public static Map<NamespacedKey, List<Integer>> newMap() {
        return new LinkedHashMap<>();
    }

    /**
     * Adds enchants from an item's PDC that match triggerId and are not on cooldown.
     * Uses item-scoped cooldown (isOnCooldown(player, enchant, item)).
     */
    public static void collectFromItem(
            Map<NamespacedKey, List<Integer>> out,
            Map<CustomEnchant, Integer> enchants,
            String triggerId,
            Player player,
            CooldownManager cooldowns,
            ItemStack item) {
        enchants.forEach((enchant, level) -> {
            if (!triggerId.equals(enchant.getTrigger().id())) return;
            if (cooldowns.isOnCooldown(player, enchant, item)) return;
            out.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
        });
    }

    /**
     * Adds enchants from an equipped-armor index list that are not on cooldown.
     * Uses player-scoped cooldown (isOnCooldown(player, enchant)).
     */
    public static void collectFromIndex(
            Map<NamespacedKey, List<Integer>> out,
            List<PlayerEnchantIndex.SlottedEnchant> equipped,
            Player player,
            CooldownManager cooldowns) {
        for (PlayerEnchantIndex.SlottedEnchant se : equipped) {
            if (cooldowns.isOnCooldown(player, se.enchant())) continue;
            out.computeIfAbsent(se.enchant().getKey(), k -> new ArrayList<>()).add(se.level());
        }
    }

    /**
     * Adds enchants from armor slots via index, with null/AIR guard.
     * Uses item-scoped cooldown (isOnCooldown(player, enchant, armorItem)).
     */
    public static void collectFromArmorSlots(
            Map<NamespacedKey, List<Integer>> out,
            List<PlayerEnchantIndex.SlottedEnchant> equipped,
            Player player,
            CooldownManager cooldowns) {
        for (PlayerEnchantIndex.SlottedEnchant se : equipped) {
            ItemStack armorItem = player.getInventory().getItem(se.slot());
            if (armorItem == null || armorItem.getType().isAir()) continue;
            if (cooldowns.isOnCooldown(player, se.enchant(), armorItem)) continue;
            out.computeIfAbsent(se.enchant().getKey(), k -> new ArrayList<>()).add(se.level());
        }
    }

    /**
     * Resolves stacking for each collected key and dispatches.
     * Calls apply(enchant, effectiveLevel), then onCooldown(enchant) if enchant.hasCooldown().
     */
    public static void dispatch(
            Map<NamespacedKey, List<Integer>> collected,
            EnchantmentRegistry registry,
            BiConsumer<CustomEnchant, Integer> apply,
            Consumer<CustomEnchant> onCooldown) {
        collected.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            apply.accept(enchant, effectiveLevel);
            if (enchant.hasCooldown()) onCooldown.accept(enchant);
        });
    }
}
