package com.example.enchantforge;

import com.example.enchantforge.condition.EndCondition;
import com.example.enchantforge.condition.NeverCondition;
import com.example.enchantforge.effect.EnchantEffect;
import com.example.enchantforge.trigger.EnchantTrigger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class CustomEnchant {

    private static final List<String> ARMOR_SUFFIXES = List.of("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS");

    private final NamespacedKey key;
    private final String displayName;
    private final int maxLevel;
    private final EnchantTrigger trigger;
    private final EnchantEffect effect;
    private final EndCondition endCondition;
    private final int cooldownTicks;
    private final int outOfCombatRefreshTicks;
    private final double outOfCombatRegenPerTick;
    private final StackBehavior stackBehavior;
    private final List<String> applicableTo;
    private final String descriptionTemplate;
    private final double displayAmountPerLevel;
    private final boolean debug;

    private CustomEnchant(NamespacedKey key, String displayName, int maxLevel,
                          EnchantTrigger trigger, EnchantEffect effect, EndCondition endCondition,
                          int cooldownTicks, int outOfCombatRefreshTicks, double outOfCombatRegenPerTick,
                          StackBehavior stackBehavior,
                          List<String> applicableTo, String descriptionTemplate,
                          double displayAmountPerLevel, boolean debug) {
        this.key = key;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.trigger = trigger;
        this.effect = effect;
        this.endCondition = endCondition;
        this.cooldownTicks = cooldownTicks;
        this.outOfCombatRefreshTicks = outOfCombatRefreshTicks;
        this.outOfCombatRegenPerTick = outOfCombatRegenPerTick;
        this.stackBehavior = stackBehavior;
        this.applicableTo = applicableTo;
        this.descriptionTemplate = descriptionTemplate;
        this.displayAmountPerLevel = displayAmountPerLevel;
        this.debug = debug;
    }

    public static CustomEnchant fromYaml(NamespacedKey key, ConfigurationSection section) {
        String displayName = section.getString("displayName", key.getKey());
        int maxLevel = section.getInt("maxLevel", 1);
        EnchantTrigger trigger = EnchantTrigger.fromYaml(
                section.getConfigurationSection("trigger"));
        EnchantEffect effect = EnchantEffect.fromYaml(key,
                section.getConfigurationSection("effect"));
        EndCondition endCondition = section.contains("duration")
                ? EndCondition.fromYaml(section.getConfigurationSection("duration"))
                : NeverCondition.INSTANCE;
        int cooldownTicks = section.getInt("cooldown", 0);
        int outOfCombatRefreshTicks = section.getInt("outOfCombatRefreshTicks", 0);
        double outOfCombatRegenPerTick = section.getDouble("outOfCombatRegenPerTick", 0.0);
        StackBehavior stackBehavior = section.contains("stackBehavior")
                ? StackBehavior.fromString(section.getString("stackBehavior"))
                : StackBehavior.HIGHEST;
        double displayAmountPerLevel = section.getDouble("displayAmountPerLevel", 1.0);
        String descriptionTemplate = section.getString("description", "");
        List<String> applicableTo = section.getStringList("applicableTo");
        boolean debug = section.getBoolean("debug", false);

        return new CustomEnchant(key, displayName, maxLevel, trigger, effect, endCondition,
                cooldownTicks, outOfCombatRefreshTicks, outOfCombatRegenPerTick, stackBehavior,
                applicableTo, descriptionTemplate, displayAmountPerLevel, debug);
    }

    // -------------------------------------------------------------------------

    public NamespacedKey getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public int getMaxLevel() { return maxLevel; }
    public EnchantTrigger getTrigger() { return trigger; }
    public EnchantEffect getEffect() { return effect; }
    public EndCondition getEndCondition() { return endCondition; }
    public StackBehavior getStackBehavior() { return stackBehavior; }
    public int getCooldownTicks() { return cooldownTicks; }
    public boolean hasCooldown() { return cooldownTicks > 0; }
    public boolean isDebug() { return debug; }
    public int getOutOfCombatRefreshTicks() { return outOfCombatRefreshTicks; }
    public boolean hasOutOfCombatRefresh() { return outOfCombatRefreshTicks > 0; }
    public double getOutOfCombatRegenPerTick() { return outOfCombatRegenPerTick; }
    public List<String> getApplicableTo() { return applicableTo; }
    public String getDescription() { return descriptionTemplate; }

    public boolean canApplyTo(Material material) {
        String name = material.name();
        for (String pattern : applicableTo) {
            if (pattern.equalsIgnoreCase("ARMOR")) {
                if (ARMOR_SUFFIXES.stream().anyMatch(name::endsWith)) return true;
            } else if (name.endsWith(pattern)) {
                return true;
            }
        }
        return false;
    }

    public void apply(Player player, int level) {
        effect.apply(player, level, endCondition.getEffectDurationTicks());
    }

    public void remove(Player player) {
        effect.remove(player);
    }

    // -------------------------------------------------------------------------
    // Lore

    public List<Component> buildLore(int level) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(displayName + " " + toRoman(level))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (!descriptionTemplate.isEmpty()) {
            lines.add(buildDescriptionLine(level));
        }
        return lines;
    }

    public int loreLineCount() {
        return descriptionTemplate.isEmpty() ? 1 : 2;
    }

    private Component buildDescriptionLine(int level) {
        double amount = displayAmountPerLevel * level;
        String amountStr = (amount == Math.floor(amount))
                ? String.valueOf((int) amount)
                : String.format("%.2f", amount);
        String cooldownStr = cooldownTicks <= 0 ? "none" : (cooldownTicks / 20) + "s";
        String refreshStr = outOfCombatRefreshTicks <= 0 ? "never" : (outOfCombatRefreshTicks / 20) + "s";
        String line = descriptionTemplate
                .replace("{amount}", amountStr)
                .replace("{level}", String.valueOf(level))
                .replace("{duration}", endCondition.getDisplayLabel())
                .replace("{cooldown}", cooldownStr)
                .replace("{refresh}", refreshStr);
        return Component.text(" " + line)
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
