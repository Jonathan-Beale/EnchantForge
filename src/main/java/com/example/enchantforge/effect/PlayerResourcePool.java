package com.example.enchantforge.effect;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PlayerResourcePool {

    private static final long MS_PER_TICK = 50L;

    private final double baseMax;
    private final double baseRegenPerTick;
    private final Map<UUID, Double> pool = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegenAt = new ConcurrentHashMap<>();
    private final Map<UUID, Double> bonusMax = new ConcurrentHashMap<>();
    private final Map<UUID, Double> bonusRegen = new ConcurrentHashMap<>();
    private final java.util.List<Consumer<Player>> onChangedCallbacks = new java.util.ArrayList<>();

    public PlayerResourcePool(double baseMax, double baseRegenPerTick) {
        this.baseMax = baseMax;
        this.baseRegenPerTick = baseRegenPerTick;
    }

    /** Base (config) max, without any per-player modifiers. */
    public double getBaseMax() { return baseMax; }

    /** Effective max for this player, including equipped enchant bonuses. */
    public double getEffectiveMax(UUID id) {
        return baseMax + bonusMax.getOrDefault(id, 0.0);
    }

    /** Add a modifier from an equipped enchant. Each call to addModifier must be paired with removeModifier. */
    public void addModifier(UUID id, double maxBonus, double regenBonus) {
        bonusMax.merge(id, maxBonus, Double::sum);
        bonusRegen.merge(id, regenBonus, Double::sum);
    }

    /** Remove a modifier when the enchant is unequipped. */
    public void removeModifier(UUID id, double maxBonus, double regenBonus) {
        bonusMax.computeIfPresent(id, (k, v) -> {
            double result = v - maxBonus;
            return result == 0.0 ? null : result;
        });
        bonusRegen.computeIfPresent(id, (k, v) -> {
            double result = v - regenBonus;
            return result == 0.0 ? null : result;
        });
        // Cap stored value to new effective max in case it shrank
        pool.computeIfPresent(id, (k, v) -> Math.min(v, getEffectiveMax(id)));
    }

    public void addOnChanged(Consumer<Player> callback) {
        onChangedCallbacks.add(callback);
    }

    public double get(Player player) {
        return computeRegen(player.getUniqueId());
    }

    /**
     * Consumes energy for normal abilities. Fails if the remaining energy would
     * drop below the 5% emergency reserve (kept for fall guard / landing).
     */
    public boolean tryConsume(Player player, double cost) {
        UUID id = player.getUniqueId();
        double current = computeRegen(id);
        double floor = getEffectiveMax(id) * 0.05;
        if (current - cost < floor) return false;
        pool.put(id, current - cost);
        fireOnChanged(player);
        return true;
    }

    /**
     * Consumes energy for emergency triggers (fall guard, landing brakes).
     * Bypasses the reserve floor so the system can always protect the player.
     */
    public boolean tryConsumeEmergency(Player player, double cost) {
        UUID id = player.getUniqueId();
        double current = computeRegen(id);
        if (current < cost) return false;
        pool.put(id, current - cost);
        fireOnChanged(player);
        return true;
    }

    public void cleanup(UUID id) {
        pool.remove(id);
        lastRegenAt.remove(id);
        bonusMax.remove(id);
        bonusRegen.remove(id);
    }

    private void fireOnChanged(Player player) {
        for (Consumer<Player> cb : onChangedCallbacks) cb.accept(player);
    }

    private double computeRegen(UUID id) {
        double effMax = getEffectiveMax(id);
        double current = pool.getOrDefault(id, effMax);
        if (current >= effMax) return effMax;
        long now = System.currentTimeMillis();
        // computeIfAbsent seeds the timer on first call below max so elapsedTicks
        // can grow on subsequent calls; without this, getOrDefault(id, now) always
        // returns the current instant, keeping elapsedTicks at 0 forever.
        long last = lastRegenAt.computeIfAbsent(id, k -> now);
        long elapsedTicks = (now - last) / MS_PER_TICK;
        if (elapsedTicks <= 0) return current;
        double effRegen = baseRegenPerTick + bonusRegen.getOrDefault(id, 0.0);
        double regened = Math.min(effMax, current + elapsedTicks * effRegen);
        pool.put(id, regened);
        lastRegenAt.put(id, last + elapsedTicks * MS_PER_TICK);
        return regened;
    }
}
