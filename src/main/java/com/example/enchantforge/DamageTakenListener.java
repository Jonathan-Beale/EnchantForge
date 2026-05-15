package com.example.enchantforge;

import com.example.enchantforge.condition.AbsorptionDepletedCondition;
import com.example.enchantforge.condition.DamagedCondition;
import com.example.enchantforge.condition.FullHealthOrDamagedCondition;
import com.example.enchantforge.condition.StatThresholdCondition;
import com.example.enchantforge.trigger.OnDamageTakenTrigger;
import com.example.enchantforge.trigger.OnEquipTrigger;
import com.example.enchantforge.trigger.StatThresholdTrigger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DamageTakenListener implements Listener {

    private final EnchantmentRegistry registry;
    private final CooldownManager cooldowns;
    private final ActiveEffectTracker tracker;
    private final CombatTracker combatTracker;
    private final Plugin plugin;

    public DamageTakenListener(EnchantmentRegistry registry, CooldownManager cooldowns,
                                ActiveEffectTracker tracker, CombatTracker combatTracker, Plugin plugin) {
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.tracker = tracker;
        this.combatTracker = combatTracker;
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        combatTracker.recordHit(player.getUniqueId());

        double absorptionBefore = player.getAbsorptionAmount();
        double finalDamage = event.getFinalDamage();
        double resultingHealth = Math.max(0, player.getHealth() - finalDamage);

        signalOutOfCombatRefresh(player);

        // Step 1: resolve end conditions for active tracked effects
        resolveEndConditions(player, resultingHealth, true);

        // Step 1b: any hit sets a fresh cooldown for ALL on_equip+DamagedCondition (or
        // FullHealthOrDamagedCondition) enchants, including those on unequipped armor,
        // so mid-combat equipping can't bypass the interrupt.
        for (CustomEnchant enchant : registry.getAll()) {
            if (enchant.getTrigger() instanceof OnEquipTrigger
                    && (enchant.getEndCondition() instanceof DamagedCondition
                        || enchant.getEndCondition() instanceof FullHealthOrDamagedCondition)
                    && enchant.hasCooldown()) {
                applyCooldown(player, enchant);
            }
        }

        // Step 2: collect and apply triggered enchants with stacking
        Map<NamespacedKey, List<Integer>> triggered = new LinkedHashMap<>();
        // Lazy-init set — only allocated when at least one debug enchant is encountered,
        // preventing duplicate skip messages when the same enchant appears on multiple pieces.
        Set<NamespacedKey>[] loggedSkip = new Set[]{null};
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            registry.getEnchants(piece).forEach((enchant, level) -> {
                if (!fires(enchant, player, resultingHealth)) return;
                if (cooldowns.isOnCooldown(player, enchant)) {
                    if (enchant.isDebug()) {
                        if (loggedSkip[0] == null) loggedSkip[0] = new HashSet<>();
                        if (loggedSkip[0].add(enchant.getKey()))
                            EnchantDebug.log(enchant, player, "trigger skipped — on cooldown ("
                                    + cooldowns.getRemainingSeconds(player, enchant) + "s left)");
                    }
                    return;
                }
                if (tracker.isTracked(player, enchant)) {
                    if (enchant.isDebug()) {
                        if (loggedSkip[0] == null) loggedSkip[0] = new HashSet<>();
                        if (loggedSkip[0].add(enchant.getKey()))
                            EnchantDebug.log(enchant, player, "trigger skipped — already active");
                    }
                    return;
                }
                triggered.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
            });
        }

        triggered.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            String stackNote = levels.size() > 1
                    ? " (" + levels.size() + " pieces, " + enchant.getStackBehavior().name().toLowerCase() + "→" + effectiveLevel + ")"
                    : "";
            EnchantDebug.log(enchant, player, "triggered lv" + effectiveLevel + stackNote
                    + " — hp " + String.format("%.1f", player.getHealth())
                    + "→" + String.format("%.1f", resultingHealth));
            enchant.apply(player, effectiveLevel);
            if (enchant.getEndCondition().requiresTracking()) {
                tracker.track(player, enchant, effectiveLevel);
                EnchantDebug.log(enchant, player, "tracking started lv" + effectiveLevel);
            } else if (enchant.hasCooldown()) {
                applyCooldown(player, enchant);
                EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
            }
        });

        // Step 3: absorption-depleted check — deferred one tick because the server applies
        // absorption deduction after all MONITOR handlers complete
        UUID playerId = player.getUniqueId();
        boolean hasAbsorptionTracked = tracker.getActive(playerId).keySet().stream()
                .map(registry::get)
                .anyMatch(e -> e != null && e.getEndCondition() instanceof AbsorptionDepletedCondition);
        // Fire depletion only when we can compute from event data that absorption hit zero.
        // Reading p.getAbsorptionAmount() one tick later is unreliable because the once-per-second
        // armor-change cycle can reset it to full before the deferred task runs.
        if (hasAbsorptionTracked && absorptionBefore > 0 && finalDamage >= absorptionBefore) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player p = plugin.getServer().getPlayer(playerId);
                if (p == null) return;
                resolveAbsorptionDepleted(p);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHealthRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        double resultingHealth = Math.min(
                player.getAttribute(Attribute.MAX_HEALTH).getValue(),
                player.getHealth() + event.getAmount());
        resolveEndConditions(player, resultingHealth, false);
    }

    // -------------------------------------------------------------------------

    private boolean fires(CustomEnchant enchant, Player player, double resultingHealth) {
        return switch (enchant.getTrigger()) {
            case OnDamageTakenTrigger t -> true;
            case StatThresholdTrigger t -> t.matches(player, resultingHealth);
            default -> false;
        };
    }

    private void resolveEndConditions(Player player, double resultingHealth, boolean damageEvent) {
        var activeSnapshot = Map.copyOf(tracker.getActive(player.getUniqueId()));
        for (var entry : activeSnapshot.entrySet()) {
            NamespacedKey key = entry.getKey();
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) continue;
            // 0 = not met; 1 = met, set cooldown; 2 = met, suppress cooldown
            int result = evaluateEndCondition(enchant, player, resultingHealth, damageEvent);
            if (result > 0) {
                EnchantDebug.log(enchant, player, "end condition met ("
                        + enchant.getEndCondition().getDisplayLabel() + ") — removing");
                enchant.remove(player);
                tracker.remove(player.getUniqueId(), key);
                if (result == 1 && enchant.hasCooldown()) {
                    applyCooldown(player, enchant);
                    EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
                }
            }
        }
    }

    /**
     * Evaluates whether an end condition is met.
     * Returns: 0 = not met; 1 = met, start cooldown; 2 = met, suppress cooldown.
     */
    private int evaluateEndCondition(CustomEnchant enchant, Player player,
                                      double resultingHealth, boolean damageEvent) {
        return switch (enchant.getEndCondition()) {
            case DamagedCondition c -> damageEvent ? 1 : 0;
            case FullHealthOrDamagedCondition c -> {
                if (damageEvent) yield 1;
                double maxHp = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                yield resultingHealth >= maxHp - 0.001 ? 2 : 0;
            }
            case StatThresholdCondition c -> c.matches(player, resultingHealth) ? 1 : 0;
            default -> 0;
        };
    }

    /** Sum of level*4 HP that tracked AbsorptionDepletedCondition enchants are expected to provide. */
    private double expectedAbsorption(Player player) {
        double expected = 0;
        for (Map.Entry<NamespacedKey, Integer> entry : tracker.getActive(player.getUniqueId()).entrySet()) {
            CustomEnchant enchant = registry.get(entry.getKey());
            if (enchant != null && enchant.getEndCondition() instanceof AbsorptionDepletedCondition) {
                expected += entry.getValue() * 4.0;
            }
        }
        return expected;
    }

    /**
     * Mirrors the out-of-combat refresh timer to vanilla's item cooldown so the sweep overlay
     * shows time-until-hearts-restored. Reset each hit because the OOC timer resets each hit.
     * Only applied when the piece's absorption is below the level-derived expected amount.
     */
    private void signalOutOfCombatRefresh(Player player) {
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType() == Material.AIR) continue;
            int maxRefreshTicks = 0;
            double expectedAbs = 0;
            for (Map.Entry<CustomEnchant, Integer> e : registry.getEnchants(piece).entrySet()) {
                CustomEnchant enchant = e.getKey();
                if (enchant.hasOutOfCombatRefresh()) {
                    maxRefreshTicks = Math.max(maxRefreshTicks, enchant.getOutOfCombatRefreshTicks());
                    expectedAbs = Math.max(expectedAbs, e.getValue() * 4.0);
                }
            }
            if (maxRefreshTicks > 0 && player.getAbsorptionAmount() < expectedAbs - 0.01) {
                player.setCooldown(piece.getType(), maxRefreshTicks);
            }
        }
    }

    /** Sets our internal cooldown and mirrors it to vanilla's item cooldown system for visual feedback. */
    private void applyCooldown(Player player, CustomEnchant enchant) {
        cooldowns.setCooldown(player, enchant);
        if (!enchant.hasCooldown()) return;
        int ticks = enchant.getCooldownTicks();
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece != null && piece.getType() != Material.AIR
                    && registry.getLevel(piece, enchant) > 0) {
                player.setCooldown(piece.getType(), ticks);
            }
        }
    }

    private void resolveAbsorptionDepleted(Player player) {
        var snapshot = Map.copyOf(tracker.getActive(player.getUniqueId()));
        snapshot.forEach((key, level) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            if (enchant.getEndCondition() instanceof AbsorptionDepletedCondition) {
                EnchantDebug.log(enchant, player, "absorption depleted — removing");
                enchant.remove(player);
                tracker.remove(player.getUniqueId(), key);
                if (enchant.hasCooldown()) {
                    applyCooldown(player, enchant);
                    EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
                }
            }
        });
    }
}
