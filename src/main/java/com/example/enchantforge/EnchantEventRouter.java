package com.example.enchantforge;

import com.example.enchantforge.effect.EnchantEffectContext;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single dispatch hub for weapon-based and armor-based enchant triggers.
 *
 * Weapon triggers ({@link WeaponTriggerSpec}) dispatch against enchants on the held weapon's PDC.
 * Armor triggers ({@link ArmorTriggerSpec}) dispatch against enchants in the equipped-armor index.
 *
 * Each trigger type registers a spec at startup via {@link com.example.enchantforge.trigger.EnchantTriggerTypeRegistry}.
 * Adding a new trigger on an existing event type is purely declarative (spec + registry entry).
 * Adding a trigger on a brand-new event type additionally needs a one-line {@code @EventHandler} stub here.
 */
public class EnchantEventRouter implements Listener {

    private final EnchantmentRegistry registry;
    private final CooldownManager cooldowns;
    private final PlayerEnchantIndex enchantIndex;

    private final Map<Class<? extends Event>, List<WeaponTriggerSpec<?>>> weaponHandlers = new HashMap<>();
    private final Map<Class<? extends Event>, List<ArmorTriggerSpec<?>>>  armorHandlers  = new HashMap<>();

    public EnchantEventRouter(EnchantmentRegistry registry, CooldownManager cooldowns,
                               PlayerEnchantIndex enchantIndex) {
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.enchantIndex = enchantIndex;
    }

    public <E extends Event> void register(WeaponTriggerSpec<E> spec) {
        weaponHandlers.computeIfAbsent(spec.eventClass(), k -> new ArrayList<>()).add(spec);
    }

    public <E extends Event> void register(ArmorTriggerSpec<E> spec) {
        armorHandlers.computeIfAbsent(spec.eventClass(), k -> new ArrayList<>()).add(spec);
    }

    // ---- Bukkit event stubs — one per event class, no logic ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) { dispatch(event); }

    @EventHandler
    public void onEntityKilled(EntityDeathEvent event) { dispatch(event); }

    // ---- Shared dispatch ----

    @SuppressWarnings("unchecked")
    private <E extends Event> void dispatch(E event) {
        Class<? extends Event> cls = event.getClass();

        List<WeaponTriggerSpec<?>> wSpecs = weaponHandlers.get(cls);
        if (wSpecs != null) {
            for (WeaponTriggerSpec<?> raw : wSpecs)
                dispatchWeapon((WeaponTriggerSpec<E>) raw, event);
        }

        List<ArmorTriggerSpec<?>> aSpecs = armorHandlers.get(cls);
        if (aSpecs != null) {
            for (ArmorTriggerSpec<?> raw : aSpecs)
                dispatchArmor((ArmorTriggerSpec<E>) raw, event);
        }
    }

    private <E extends Event> void dispatchWeapon(WeaponTriggerSpec<E> spec, E event) {
        if (!spec.guard().test(event)) return;
        Player player = spec.playerExtractor().apply(event);
        if (player == null) return;
        ItemStack weapon = spec.weaponExtractor().apply(event);
        if (weapon == null || weapon.getType() == Material.AIR) return;

        Map<NamespacedKey, List<Integer>> triggered = new LinkedHashMap<>();
        registry.getEnchants(weapon).forEach((enchant, level) -> {
            if (!spec.triggerId().equals(enchant.getTrigger().id())) return;
            if (cooldowns.isOnCooldown(player, enchant, weapon)) return;
            triggered.computeIfAbsent(enchant.getKey(), k -> new ArrayList<>()).add(level);
        });
        if (triggered.isEmpty()) return;

        EnchantEffectContext ctx = spec.contextBuilder() != null
                ? spec.contextBuilder().apply(event) : null;
        triggered.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            if (ctx != null) enchant.apply(player, effectiveLevel, ctx);
            else enchant.apply(player, effectiveLevel);
            String msg = spec.triggerId() + " triggered lv" + effectiveLevel;
            if (ctx != null && ctx.hasDealtDamage())
                msg += " (+" + String.format("%.1f", ctx.dealtDamage()) + " dmg)";
            EnchantDebug.log(enchant, player, msg);
            if (enchant.hasCooldown()) {
                cooldowns.setCooldown(player, enchant, weapon);
                CooldownVisuals.applyMainHandCooldown(player, enchant.getCooldownTicks());
                EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
            }
        });
    }

    private <E extends Event> void dispatchArmor(ArmorTriggerSpec<E> spec, E event) {
        if (!spec.guard().test(event)) return;
        Player player = spec.playerExtractor().apply(event);
        if (player == null) return;

        List<PlayerEnchantIndex.SlottedEnchant> equipped =
                enchantIndex.getByTrigger(player.getUniqueId(), spec.triggerId());
        if (equipped.isEmpty()) return;

        Map<NamespacedKey, List<Integer>> triggered = new LinkedHashMap<>();
        for (PlayerEnchantIndex.SlottedEnchant se : equipped) {
            if (cooldowns.isOnCooldown(player, se.enchant())) continue;
            triggered.computeIfAbsent(se.enchant().getKey(), k -> new ArrayList<>()).add(se.level());
        }
        if (triggered.isEmpty()) return;

        EnchantEffectContext ctx = spec.contextBuilder() != null
                ? spec.contextBuilder().apply(event) : null;
        triggered.forEach((key, levels) -> {
            CustomEnchant enchant = registry.get(key);
            if (enchant == null) return;
            int effectiveLevel = enchant.getStackBehavior().compute(levels);
            if (ctx != null) enchant.apply(player, effectiveLevel, ctx);
            else enchant.apply(player, effectiveLevel);
            EnchantDebug.log(enchant, player, spec.triggerId() + " triggered lv" + effectiveLevel);
            if (enchant.hasCooldown()) {
                cooldowns.setCooldown(player, enchant);
                EnchantDebug.log(enchant, player, "cooldown started (" + (enchant.getCooldownTicks() / 20) + "s)");
            }
        });
    }
}
