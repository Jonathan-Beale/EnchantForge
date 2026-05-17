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

public final class TriggerDispatchService {

    private TriggerDispatchService() {}

    public static void collectTriggered(Map<NamespacedKey, List<Integer>> out,
                                        Map<CustomEnchant, Integer> enchants,
                                        String triggerId,
                                        Player player,
                                        CooldownManager cooldowns) {
        enchants.forEach((enchant, level) -> {
            if (!triggerId.equals(enchant.getTrigger().id())) return;
            if (cooldowns.isOnCooldown(player, enchant)) return;
            out.computeIfAbsent(enchant.getKey(), ignored -> new ArrayList<>()).add(level);
        });
    }

    public static void collectTriggered(Map<NamespacedKey, List<Integer>> out,
                                        Map<CustomEnchant, Integer> enchants,
                                        String triggerId,
                                        Player player,
                                        CooldownManager cooldowns,
                                        ItemStack sourceItem) {
        enchants.forEach((enchant, level) -> {
            if (!triggerId.equals(enchant.getTrigger().id())) return;
            if (cooldowns.isOnCooldown(player, enchant, sourceItem)) return;
            out.computeIfAbsent(enchant.getKey(), ignored -> new ArrayList<>()).add(level);
        });
    }

    public static Map<NamespacedKey, List<Integer>> newTriggeredMap() {
        return new LinkedHashMap<>();
    }

    public static void executeTriggered(Map<NamespacedKey, List<Integer>> triggered,
                                        EnchantmentRegistry registry,
                                        Player player,
                                        BiConsumer<CustomEnchant, Integer> applyAction,
                                        Consumer<CustomEnchant> cooldownAction,
                                        String debugLabel) {
        triggered.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            applyAction.accept(enchant, effectiveLevel);
            EnchantDebug.log(enchant, player, debugLabel + " lv" + effectiveLevel);
            if (enchant.hasCooldown()) {
                cooldownAction.accept(enchant);
            }
        });
    }
}