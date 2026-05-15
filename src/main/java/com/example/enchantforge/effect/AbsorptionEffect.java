package com.example.enchantforge.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AbsorptionEffect implements EnchantEffect {

    private final NamespacedKey modifierKey;
    // Stores absorption amount saved during remove() so the following apply() can restore it
    // rather than resetting to full. Consumed immediately — only persists across the
    // removeOnEquip→applyOnEquip boundary within the same armor-change event.
    private final Map<UUID, Double> pendingRestore = new HashMap<>();

    public AbsorptionEffect(NamespacedKey modifierKey) {
        this.modifierKey = modifierKey;
    }

    @Override
    public void apply(Player player, int enchantLevel, int durationTicks) {
        double amount = enchantLevel * 4.0;
        AttributeInstance attr = player.getAttribute(Attribute.MAX_ABSORPTION);
        if (attr != null) {
            attr.removeModifier(modifierKey);
            attr.addModifier(new AttributeModifier(
                    modifierKey, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
        Double saved = pendingRestore.remove(player.getUniqueId());
        // Fresh equip (no saved state) → give full absorption.
        // Armor-cycle re-apply (saved state present) → restore the amount that was there before.
        player.setAbsorptionAmount(saved != null ? Math.min(saved, amount) : amount);
    }

    @Override
    public void remove(Player player) {
        // Save current absorption so the next apply() can restore it instead of resetting to full.
        pendingRestore.put(player.getUniqueId(), player.getAbsorptionAmount());
        AttributeInstance attr = player.getAttribute(Attribute.MAX_ABSORPTION);
        if (attr != null) {
            attr.removeModifier(modifierKey);
            double newMax = attr.getValue();
            if (player.getAbsorptionAmount() > newMax) {
                player.setAbsorptionAmount(newMax);
            }
        }
    }
}
