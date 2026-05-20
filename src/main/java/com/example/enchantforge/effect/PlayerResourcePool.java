package com.example.enchantforge.effect;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PlayerResourcePool {

    private static final long MS_PER_TICK = 50L;

    private final double max;
    private final double regenPerTick;
    /** Bottom 5% is reserved for emergency triggers (fall guard, landing). */
    private final double reserveFloor;
    private final Map<UUID, Double> pool = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegenAt = new ConcurrentHashMap<>();
    private Consumer<Player> onChanged = p -> {};

    public PlayerResourcePool(double max, double regenPerTick) {
        this.max = max;
        this.regenPerTick = regenPerTick;
        this.reserveFloor = max * 0.05;
    }

    public double getMax() { return max; }

    public void onChanged(Consumer<Player> callback) {
        this.onChanged = callback;
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
        if (current - cost < reserveFloor) return false;
        pool.put(id, current - cost);
        onChanged.accept(player);
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
        onChanged.accept(player);
        return true;
    }

    public void cleanup(UUID id) {
        pool.remove(id);
        lastRegenAt.remove(id);
    }

    private double computeRegen(UUID id) {
        double current = pool.getOrDefault(id, max);
        if (current >= max) return max;
        long now = System.currentTimeMillis();
        // computeIfAbsent seeds the timer on first call below max so elapsedTicks
        // can grow on subsequent calls; without this, getOrDefault(id, now) always
        // returns the current instant, keeping elapsedTicks at 0 forever.
        long last = lastRegenAt.computeIfAbsent(id, k -> now);
        long elapsedTicks = (now - last) / MS_PER_TICK;
        if (elapsedTicks <= 0) return current;
        double regened = Math.min(max, current + elapsedTicks * regenPerTick);
        pool.put(id, regened);
        lastRegenAt.put(id, last + elapsedTicks * MS_PER_TICK);
        return regened;
    }
}
