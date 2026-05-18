package com.example.enchantforge.effect;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Shared energy pool used by Thruster and Repulsor enchants.
 * Regenerates passively; visual display is handled by SuitListener (BossBar).
 */
public final class EnergyManager {
    private EnergyManager() {}

    public static final double MAX = 100.0;
    // 0.4/tick × 20 ticks/sec = 8/sec → full recharge from 0 in ~12.5 seconds
    private static final double REGEN_PER_TICK = 0.4;
    private static final long MS_PER_TICK = 50L;

    private static final Map<UUID, Double> energy = new ConcurrentHashMap<>();
    // Tracks the wall-clock time (ms) at which each player's energy value was last written.
    // Regen is computed lazily on read rather than on a per-tick timer.
    private static final Map<UUID, Long> lastRegenAt = new ConcurrentHashMap<>();
    private static Consumer<Player> onEnergyChanged = p -> {};

    /** Register a callback invoked whenever a player's energy is consumed. */
    public static void registerCallback(Consumer<Player> callback) {
        onEnergyChanged = callback;
    }

    /** Returns true and deducts energy on success. */
    public static boolean tryConsume(Player player, double cost) {
        UUID id = player.getUniqueId();
        double e = computeRegen(id);
        if (e < cost) return false;
        energy.put(id, e - cost);
        onEnergyChanged.accept(player);
        return true;
    }

    public static double get(Player player) {
        return computeRegen(player.getUniqueId());
    }

    public static void cleanup(UUID id) {
        energy.remove(id);
        lastRegenAt.remove(id);
    }

    /**
     * Applies accumulated regen since the last write, updates stored state, and returns the
     * current energy. Called on every read so the per-tick timer is no longer needed.
     */
    private static double computeRegen(UUID id) {
        double current = energy.getOrDefault(id, MAX);
        if (current >= MAX) return MAX;
        long now = System.currentTimeMillis();
        long last = lastRegenAt.getOrDefault(id, now);
        long elapsedTicks = (now - last) / MS_PER_TICK;
        if (elapsedTicks <= 0) return current;
        double regened = Math.min(MAX, current + elapsedTicks * REGEN_PER_TICK);
        energy.put(id, regened);
        lastRegenAt.put(id, last + elapsedTicks * MS_PER_TICK);
        return regened;
    }
}
