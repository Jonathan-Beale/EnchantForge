package com.example.enchantforge;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PlayerEnchantIndex {

    public record SlottedEnchant(EquipmentSlot slot, CustomEnchant enchant, int level) {}

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    // playerId → triggerId → list of (slot, enchant, level)
    private final Map<UUID, Map<String, List<SlottedEnchant>>> index = new HashMap<>();

    public void rebuild(Player player, EnchantmentRegistry registry) {
        Map<String, List<SlottedEnchant>> byTrigger = new HashMap<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            addSlotToMap(byTrigger, slot, player.getInventory().getItem(slot), registry);
        }
        index.put(player.getUniqueId(), byTrigger);
    }

    /** Surgically updates one slot without touching the other three. */
    public void updateSlot(Player player, EquipmentSlot slot, ItemStack newItem, EnchantmentRegistry registry) {
        Map<String, List<SlottedEnchant>> byTrigger =
            index.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        byTrigger.values().forEach(list -> list.removeIf(se -> se.slot() == slot));
        byTrigger.entrySet().removeIf(e -> e.getValue().isEmpty());
        addSlotToMap(byTrigger, slot, newItem, registry);
    }

    public List<SlottedEnchant> getByTrigger(UUID playerId, String triggerId) {
        Map<String, List<SlottedEnchant>> byTrigger = index.get(playerId);
        if (byTrigger == null) return List.of();
        List<SlottedEnchant> result = byTrigger.get(triggerId);
        return result != null ? Collections.unmodifiableList(result) : List.of();
    }

    public void clearPlayer(UUID playerId) {
        index.remove(playerId);
    }

    private void addSlotToMap(Map<String, List<SlottedEnchant>> byTrigger, EquipmentSlot slot,
                               ItemStack item, EnchantmentRegistry registry) {
        if (item == null || item.getType().isAir()) return;
        registry.getEnchants(item).forEach((enchant, level) ->
            byTrigger.computeIfAbsent(enchant.getTrigger().id(), k -> new ArrayList<>())
                     .add(new SlottedEnchant(slot, enchant, level)));
    }

    public static EquipmentSlot fromSlotType(PlayerArmorChangeEvent.SlotType slotType) {
        return switch (slotType) {
            case FEET  -> EquipmentSlot.FEET;
            case LEGS  -> EquipmentSlot.LEGS;
            case CHEST -> EquipmentSlot.CHEST;
            case HEAD  -> EquipmentSlot.HEAD;
        };
    }
}
