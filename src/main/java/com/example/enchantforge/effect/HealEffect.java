package com.example.enchantforge.effect;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

public class HealEffect implements EnchantEffect {

    private final double percentPerLevel;
    private final double overhealPerLevel;
    private final NamespacedKey overhealKey;  // null when overheal is disabled

    private HealEffect(double percentPerLevel, double overhealPerLevel, NamespacedKey overhealKey) {
        this.percentPerLevel = percentPerLevel;
        this.overhealPerLevel = overhealPerLevel;
        this.overhealKey = overhealKey;
    }

    public static HealEffect fromYaml(NamespacedKey enchantKey, ConfigurationSection section) {
        double percent = section.getDouble("percentPerLevel", 0.15);
        double overheal = section.getDouble("overhealPerLevel", 0.0);
        NamespacedKey overhealKey = overheal > 0
                ? new NamespacedKey(enchantKey.getNamespace(), enchantKey.getKey() + "_overheal")
                : null;
        return new HealEffect(percent, overheal, overhealKey);
    }

    @Override
    public String id() { return "heal"; }

    public void healForDamage(Player player, int level, double damage) {
        double healAmount = percentPerLevel * level * damage;
        double maxHp = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        double deficit = maxHp - player.getHealth();

        if (overhealKey == null || healAmount <= deficit) {
            player.setHealth(Math.min(player.getHealth() + healAmount, maxHp));
            return;
        }

        // Heal to full; convert overflow into temporary absorption
        player.setHealth(maxHp);
        double overflow = healAmount - deficit;
        double vampiricCap = overhealPerLevel * level;

        AttributeInstance attr = player.getAttribute(Attribute.MAX_ABSORPTION);
        if (attr != null) {
            attr.removeModifier(overhealKey);
            attr.addModifier(new AttributeModifier(
                    overhealKey, vampiricCap, AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY));
            double maxAbs = attr.getValue();
            player.setAbsorptionAmount(Math.min(player.getAbsorptionAmount() + overflow, maxAbs));
        }
    }

    @Override
    public void apply(Player player, int enchantLevel, int durationTicks) {
        // Context-free fallback — actual heal goes through healForDamage()
    }

    @Override
    public void apply(Player player, int enchantLevel, int durationTicks, EnchantEffectContext context) {
        if (context == null || !context.hasDealtDamage()) return;
        healForDamage(player, enchantLevel, context.dealtDamage());
    }

    @Override
    public void remove(Player player) {
        // Instant effect; nothing to remove
    }
}
