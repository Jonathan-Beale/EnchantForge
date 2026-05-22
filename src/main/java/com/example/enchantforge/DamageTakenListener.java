package com.example.enchantforge;

import com.example.enchantforge.condition.AbsorptionDepletedCondition;
import com.example.enchantforge.condition.DamagedCondition;
import com.example.enchantforge.condition.FullHealthOrDamagedCondition;
import com.example.enchantforge.condition.StatThresholdCondition;
import com.example.enchantforge.trigger.StatThresholdTrigger;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class DamageTakenListener implements Listener {

    private final EnchantmentRegistry registry;
    private final CooldownManager cooldowns;
    private final ActiveEffectTracker tracker;
    private final CombatTracker combatTracker;
    private final Plugin plugin;
    private final PlayerEnchantIndex enchantIndex;

    public DamageTakenListener(EnchantmentRegistry registry, CooldownManager cooldowns,
                                ActiveEffectTracker tracker, CombatTracker combatTracker,
                                Plugin plugin, PlayerEnchantIndex enchantIndex) {
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.tracker = tracker;
        this.combatTracker = combatTracker;
        this.plugin = plugin;
        this.enchantIndex = enchantIndex;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        combatTracker.recordHit(player.getUniqueId());

        double absorptionBefore = player.getAbsorptionAmount();
        double finalDamage = event.getFinalDamage();
        double resultingHealth = Math.max(0, player.getHealth() - finalDamage);

        // Step 1: resolve end conditions for active tracked effects
        resolveEndConditions(player, resultingHealth, true);

        UUID pid = player.getUniqueId();

        // Step 1b: set a fresh cooldown for all on_equip+DamagedCondition enchants currently
        // equipped, so mid-combat equip-swap can't bypass the interrupt.
        List<PlayerEnchantIndex.SlottedEnchant> onEquipList = enchantIndex.getByTrigger(pid, "on_equip");
        if (!onEquipList.isEmpty()) {
            Set<NamespacedKey> cooledDown = new HashSet<>();
            for (PlayerEnchantIndex.SlottedEnchant se : onEquipList) {
                CustomEnchant enchant = se.enchant();
                // TODO: replace instanceof chain with registry dispatch — violates YAML-first design principle
                if ((enchant.getEndCondition() instanceof DamagedCondition
                        || enchant.getEndCondition() instanceof FullHealthOrDamagedCondition)
                        && enchant.hasCooldown()
                        && cooledDown.add(enchant.getKey())) {
                    applyCooldown(player, enchant);
                }
            }
        }

        // Step 2: collect and apply triggered enchants with stacking
        List<PlayerEnchantIndex.SlottedEnchant> dmgList  = enchantIndex.getByTrigger(pid, "on_damage_taken");
        List<PlayerEnchantIndex.SlottedEnchant> statList = enchantIndex.getByTrigger(pid, "stat_threshold");
        if (!dmgList.isEmpty() || !statList.isEmpty()) {
        Map<NamespacedKey, List<Integer>> triggered = new LinkedHashMap<>();
        // Lazy-init set — only allocated when at least one debug enchant is encountered,
        // preventing duplicate skip messages when the same enchant appears on multiple pieces.
        AtomicReference<Set<NamespacedKey>> loggedSkip = new AtomicReference<>();
        for (List<PlayerEnchantIndex.SlottedEnchant> list : new List[]{dmgList, statList}) {
            for (PlayerEnchantIndex.SlottedEnchant se : list) {
                CustomEnchant enchant = se.enchant();
                if (!fires(enchant, player, resultingHealth)) continue;
                if (cooldowns.isOnCooldown(player, enchant)) {
                    if (enchant.isDebug()) {
                        if (loggedSkip.get() == null) loggedSkip.set(new HashSet<>());
                        if (loggedSkip.get().add(enchant.getKey()))
                            EnchantDebug.log(enchant, player, "trigger skipped — on cooldown ("
                                    + cooldowns.getRemainingSeconds(player, enchant) + "s left)");
                    }
                    continue;
                }
                if (tracker.isTracked(player, enchant)) {
                    if (enchant.isDebug()) {
                        if (loggedSkip.get() == null) loggedSkip.set(new HashSet<>());
                        if (loggedSkip.get().add(enchant.getKey()))
                            EnchantDebug.log(enchant, player, "trigger skipped — already active");
                    }
                    continue;
                }
                triggered.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(se.level());
            }
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
        } // end if (!dmgList.isEmpty() || !statList.isEmpty())

        // Step 3: absorption-depleted check — deferred one tick because the server applies
        // absorption deduction after all MONITOR handlers complete
        boolean hasAbsorptionTracked = tracker.getActive(pid).keySet().stream()
                .map(registry::get)
                .anyMatch(e -> e != null && e.getEndCondition() instanceof AbsorptionDepletedCondition);
        // Fire depletion only when we can compute from event data that absorption hit zero.
        // Reading p.getAbsorptionAmount() one tick later is unreliable because the once-per-second
        // armor-change cycle can reset it to full before the deferred task runs.
        if (hasAbsorptionTracked && absorptionBefore > 0 && finalDamage >= absorptionBefore) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player p = plugin.getServer().getPlayer(pid);
                if (p == null) return;
                resolveAbsorptionDepleted(p);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHealthRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!tracker.hasActive(player.getUniqueId())) return;
        double resultingHealth = Math.min(safeMaxHealth(player), player.getHealth() + event.getAmount());
        resolveEndConditions(player, resultingHealth, false);
    }

    // -------------------------------------------------------------------------

    private boolean fires(CustomEnchant enchant, Player player, double resultingHealth) {
        String triggerId = enchant.getTrigger().id();
        if ("on_damage_taken".equals(triggerId)) return true;
        // TODO: replace instanceof chain with registry dispatch — violates YAML-first design principle
        if ("stat_threshold".equals(triggerId) && enchant.getTrigger() instanceof StatThresholdTrigger t) {
            return t.matches(player, resultingHealth);
        }
        return false;
    }

    private void resolveEndConditions(Player player, double resultingHealth, boolean damageEvent) {
        if (!tracker.hasActive(player.getUniqueId())) return;
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
        // TODO: replace instanceof chain with registry dispatch — violates YAML-first design principle
        return switch (enchant.getEndCondition()) {
            case DamagedCondition c -> damageEvent ? 1 : 0;
            case FullHealthOrDamagedCondition c -> {
                if (damageEvent) yield 1;
                yield resultingHealth >= safeMaxHealth(player) - 0.001 ? 2 : 0;
            }
            case StatThresholdCondition c -> c.matches(player, resultingHealth) ? 1 : 0;
            default -> 0;
        };
    }

    private static double safeMaxHealth(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
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

    /** Sets only our internal per-enchant cooldown. */
    private void applyCooldown(Player player, CustomEnchant enchant) {
        cooldowns.setCooldown(player, enchant);
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
