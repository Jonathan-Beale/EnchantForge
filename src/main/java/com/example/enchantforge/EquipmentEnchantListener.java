package com.example.enchantforge;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.example.enchantforge.condition.FullHealthOrDamagedCondition;
import org.bukkit.attribute.Attribute;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EquipmentEnchantListener implements Listener {

    private final EnchantmentRegistry registry;
    private final CooldownManager cooldowns;
    private final ActiveEffectTracker tracker;
    private final CombatTracker combatTracker;

    public EquipmentEnchantListener(EnchantmentRegistry registry, CooldownManager cooldowns,
                                    ActiveEffectTracker tracker, CombatTracker combatTracker) {
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.tracker = tracker;
        this.combatTracker = combatTracker;
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        double savedAbsorption = player.getAbsorptionAmount();
        removeOnEquip(player);

        ItemStack[] armor = player.getInventory().getArmorContents();
        int idx = slotIndex(event.getSlotType());
        if (idx >= 0) armor[idx] = event.getNewItem();

        applyOnEquip(player, armor);

        // Prevent the remove→reapply cycle from inflating absorption back to full.
        // If the player had absorption before the armor change, cap back to that amount.
        if (savedAbsorption > 0 && player.getAbsorptionAmount() > savedAbsorption) {
            AttributeInstance maxAbsAttr = player.getAttribute(Attribute.MAX_ABSORPTION);
            double newMax = maxAbsAttr != null ? maxAbsAttr.getValue() : 0.0;
            player.setAbsorptionAmount(Math.min(savedAbsorption, newMax));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyOnEquip(event.getPlayer(), event.getPlayer().getInventory().getArmorContents());
    }

    public void refreshPlayer(Player player) {
        removeOnEquip(player);
        applyOnEquip(player, player.getInventory().getArmorContents());
    }

    /** Checks every second whether any online player's interrupted passive cooldown has expired. */
    public void startReapplyTicker(Plugin plugin) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                // Only re-apply the specific interrupted enchants — NOT applyOnEquip, which would
                // also call addPotionEffect for NeverCondition enchants (Steadfast) and reset
                // their absorption HP, causing near-invulnerability.
                reapplyInterruptedPassives(player);
                refreshOutOfCombat(player);
            }
        }, 20L, 20L);
    }

    private void reapplyInterruptedPassives(Player player) {
        Map<NamespacedKey, List<Integer>> needed = new LinkedHashMap<>();
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            registry.getEnchants(piece).forEach((enchant, level) -> {
                if ("on_equip".equals(enchant.getTrigger().id())
                        && enchant.getEndCondition().requiresTracking()
                        && !tracker.isTracked(player, enchant)
                        && !cooldowns.isOnCooldown(player, enchant)
                        && !(enchant.getEndCondition() instanceof FullHealthOrDamagedCondition
                                && player.getHealth() >= player.getAttribute(Attribute.MAX_HEALTH).getValue() - 0.001)) {
                    needed.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
                }
            });
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
        Map<NamespacedKey, List<Integer>> candidates = new LinkedHashMap<>();
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            registry.getEnchants(piece).forEach((enchant, level) -> {
                if ("on_equip".equals(enchant.getTrigger().id())
                        && enchant.hasOutOfCombatRefresh()
                        && combatTracker.millisSinceLastHit(player.getUniqueId())
                            >= enchant.getOutOfCombatRefreshTicks() * 50L) {
                    candidates.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
                }
            });
        }
        if (candidates.isEmpty()) return;

        AttributeInstance maxAbsAttr = player.getAttribute(Attribute.MAX_ABSORPTION);
        double maxAbs = maxAbsAttr != null ? maxAbsAttr.getValue() : 16.0;

        candidates.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            // Absorption gives (amplifier+1)*4 HP = effectiveLevel*4 HP
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

    private void applyOnEquip(Player player, ItemStack[] armor) {
        Map<NamespacedKey, List<Integer>> collected = new LinkedHashMap<>();
        for (ItemStack piece : armor) {
            registry.getEnchants(piece).forEach((enchant, level) -> {
                if ("on_equip".equals(enchant.getTrigger().id())) {
                    collected.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
                }
            });
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
        for (CustomEnchant enchant : registry.getAll()) {
            if ("on_equip".equals(enchant.getTrigger().id())) {
                if (tracker.isTracked(player, enchant))
                    EnchantDebug.log(enchant, player, "on_equip removed (armor change)");
                enchant.remove(player);
                tracker.remove(player.getUniqueId(), enchant.getKey());
            }
        }
    }

    private int slotIndex(PlayerArmorChangeEvent.SlotType slot) {
        return switch (slot) {
            case FEET  -> 0;
            case LEGS  -> 1;
            case CHEST -> 2;
            case HEAD  -> 3;
        };
    }
}
