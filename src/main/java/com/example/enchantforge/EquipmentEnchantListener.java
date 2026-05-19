package com.example.enchantforge;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.example.enchantforge.condition.FullHealthOrDamagedCondition;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EquipmentEnchantListener implements Listener {

    private final EnchantmentRegistry registry;
    private final CooldownManager cooldowns;
    private final ActiveEffectTracker tracker;
    private final CombatTracker combatTracker;
    private final PlayerEnchantIndex enchantIndex;

    public EquipmentEnchantListener(EnchantmentRegistry registry, CooldownManager cooldowns,
                                    ActiveEffectTracker tracker, CombatTracker combatTracker,
                                    PlayerEnchantIndex enchantIndex) {
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.tracker = tracker;
        this.combatTracker = combatTracker;
        this.enchantIndex = enchantIndex;
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        double savedAbsorption = player.getAbsorptionAmount();

        removeOnEquip(player);

        EquipmentSlot slot = PlayerEnchantIndex.fromSlotType(event.getSlotType());
        enchantIndex.updateSlot(player, slot, event.getNewItem(), registry);

        applyOnEquip(player);

        // Prevent the remove→reapply cycle from inflating absorption back to full.
        if (savedAbsorption > 0 && player.getAbsorptionAmount() > savedAbsorption) {
            AttributeInstance maxAbsAttr = player.getAttribute(Attribute.MAX_ABSORPTION);
            double newMax = maxAbsAttr != null ? maxAbsAttr.getValue() : 0.0;
            player.setAbsorptionAmount(Math.min(savedAbsorption, newMax));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        enchantIndex.rebuild(player, registry);
        applyOnEquip(player);
    }

    public void refreshPlayer(Player player) {
        removeOnEquip(player);
        enchantIndex.rebuild(player, registry);
        applyOnEquip(player);
    }

    /** Checks every second whether any online player's interrupted passive cooldown has expired. */
    public void startReapplyTicker(Plugin plugin) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                // Only re-apply interrupted enchants — not a full applyOnEquip, which would reset
                // NeverCondition absorption HP causing near-invulnerability.
                reapplyInterruptedPassives(player);
                refreshOutOfCombat(player);
            }
        }, 20L, 20L);
    }

    private void reapplyInterruptedPassives(Player player) {
        List<PlayerEnchantIndex.SlottedEnchant> onEquip = enchantIndex.getByTrigger(player.getUniqueId(), "on_equip");
        if (onEquip.isEmpty()) return;
        Map<NamespacedKey, List<Integer>> needed = new LinkedHashMap<>();
        double maxHp = safeMaxHealth(player);
        for (PlayerEnchantIndex.SlottedEnchant se : onEquip) {
            CustomEnchant enchant = se.enchant();
            if (enchant.getEndCondition().requiresTracking()
                    && !tracker.isTracked(player, enchant)
                    && !cooldowns.isOnCooldown(player, enchant)
                    && !(enchant.getEndCondition() instanceof FullHealthOrDamagedCondition
                            && player.getHealth() >= maxHp - 0.001)) {
                needed.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(se.level());
            }
        }
        needed.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            EnchantDebug.log(enchant, player, "passive restored lv" + effectiveLevel + " (cooldown expired)");
            enchant.apply(player, effectiveLevel);
            tracker.track(player, enchant, effectiveLevel);
            EnchantDebug.log(enchant, player, "tracking started lv" + effectiveLevel);
        });
    }

    // -------------------------------------------------------------------------

    private void refreshOutOfCombat(Player player) {
        List<PlayerEnchantIndex.SlottedEnchant> onEquip = enchantIndex.getByTrigger(player.getUniqueId(), "on_equip");
        if (onEquip.isEmpty()) return;
        Map<NamespacedKey, List<Integer>> candidates = new LinkedHashMap<>();
        for (PlayerEnchantIndex.SlottedEnchant se : onEquip) {
            CustomEnchant enchant = se.enchant();
            if (enchant.hasOutOfCombatRefresh()
                    && combatTracker.millisSinceLastHit(player.getUniqueId())
                        >= enchant.getOutOfCombatRefreshTicks() * 50L) {
                candidates.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(se.level());
            }
        }
        if (candidates.isEmpty()) return;

        AttributeInstance maxAbsAttr = player.getAttribute(Attribute.MAX_ABSORPTION);
        double maxAbs = maxAbsAttr != null ? maxAbsAttr.getValue() : 16.0;

        candidates.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            double expected = Math.min(effectiveLevel * 4.0, maxAbs);
            double current = player.getAbsorptionAmount();
            if (current >= expected - 0.01) return;
            double regenPerTick = enchant.getOutOfCombatRegenPerTick();
            double next = regenPerTick > 0 ? Math.min(expected, current + regenPerTick) : expected;
            player.setAbsorptionAmount(next);
            if (next >= expected - 0.01)
                EnchantDebug.log(enchant, player, "absorption fully restored (out-of-combat regen complete)");
        });
    }

    private void applyOnEquip(Player player) {
        Map<NamespacedKey, List<Integer>> collected = new LinkedHashMap<>();
        for (PlayerEnchantIndex.SlottedEnchant se : enchantIndex.getByTrigger(player.getUniqueId(), "on_equip")) {
            collected.computeIfAbsent(se.enchant().getKey(), k -> new ArrayList<>()).add(se.level());
        }
        collected.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            if (enchant.getEndCondition().requiresTracking() && cooldowns.isOnCooldown(player, enchant)) {
                EnchantDebug.log(enchant, player, "on_equip skipped — cooldown ("
                        + cooldowns.getRemainingSeconds(player, enchant) + "s left)");
                return;
            }
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            String stackNote = levels.size() > 1
                    ? " (" + levels.size() + " pieces, " + enchant.getStackBehavior().name().toLowerCase() + "→" + effectiveLevel + ")"
                    : "";
            EnchantDebug.log(enchant, player, "on_equip applied lv" + effectiveLevel + stackNote);
            enchant.apply(player, effectiveLevel);
            if (enchant.getEndCondition().requiresTracking()) {
                tracker.track(player, enchant, effectiveLevel);
                EnchantDebug.log(enchant, player, "tracking started lv" + effectiveLevel);
            }
        });
    }

    private void removeOnEquip(Player player) {
        Set<NamespacedKey> seen = new HashSet<>();
        for (PlayerEnchantIndex.SlottedEnchant se : enchantIndex.getByTrigger(player.getUniqueId(), "on_equip")) {
            if (!seen.add(se.enchant().getKey())) continue;
            CustomEnchant enchant = se.enchant();
            if (tracker.isTracked(player, enchant))
                EnchantDebug.log(enchant, player, "on_equip removed (armor change)");
            enchant.remove(player);
            tracker.remove(player.getUniqueId(), enchant.getKey());
        }
    }

    private static double safeMaxHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }
}
