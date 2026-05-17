package com.example.enchantforge.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

public class AttributeStyleEffect implements EnchantEffect {

    private final NamespacedKey key;
    private final Attribute attribute;
    private final AttributeModifier.Operation operation;
    private final double amountPerLevel;
    private final EquipmentSlotGroup slotGroup;

    private AttributeStyleEffect(NamespacedKey key, Attribute attribute,
                                  AttributeModifier.Operation operation,
                                  double amountPerLevel, EquipmentSlotGroup slotGroup) {
        this.key = key;
        this.attribute = attribute;
        this.operation = operation;
        this.amountPerLevel = amountPerLevel;
        this.slotGroup = slotGroup;
    }

    public static AttributeStyleEffect fromYaml(NamespacedKey key, ConfigurationSection section) {
        Attribute attribute = resolveAttribute(section.getString("attribute"));
        AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(
                section.getString("operation", "ADD_NUMBER").toUpperCase());
        double amountPerLevel = section.getDouble("amountPerLevel");
        EquipmentSlotGroup slotGroup = section.contains("slotGroup")
                ? resolveSlotGroup(section.getString("slotGroup"))
                : EquipmentSlotGroup.ANY;
        return new AttributeStyleEffect(key, attribute, operation, amountPerLevel, slotGroup);
    }

    @Override
    public String id() { return "attribute"; }

    @Override
    public void apply(Player player, int enchantLevel, int durationTicks) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) return;
        attr.removeModifier(key);
        attr.addModifier(new AttributeModifier(key, amountPerLevel * enchantLevel, operation, slotGroup));
    }

    @Override
    public void remove(Player player) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr != null) attr.removeModifier(key);
    }

    public double getAmountPerLevel() { return amountPerLevel; }

    public EquipmentSlotGroup getSlotGroup() { return slotGroup; }

    private static Attribute resolveAttribute(String name) {
        try {
            return (Attribute) Attribute.class.getField(name.toUpperCase()).get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException("Unknown attribute: " + name);
        }
    }

    private static EquipmentSlotGroup resolveSlotGroup(String name) {
        EquipmentSlotGroup group = EquipmentSlotGroup.getByName(name.toLowerCase());
        return group != null ? group : EquipmentSlotGroup.ANY;
    }
}
