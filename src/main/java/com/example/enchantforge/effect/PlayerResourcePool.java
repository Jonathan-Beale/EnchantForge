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
    private final Map<UUID, Double> pool = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRegenAt = new ConcurrentHashMap<>();
    private Consumer<Player> onChanged = p -> {};

    public PlayerResourcePool(double max, double regenPerTick) {
        this.max = max;
        this.regenPerTick = regenPerTick;
    }

    public double getMax() { return max; }

    public void onChanged(Consumer<Player> callback) {
        this.onChanged = callback;
    }

    public double get(Player player) {
        return computeRegen(player.getUniqueId());
    }

    public boolean tryConsume(Player player, double cost) {
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
        long last = lastRegenAt.getOrDefault(id, now);
        long elapsedTicks = (now - last) / MS_PER_TICK;
        if (elapsedTicks <= 0) return current;
        double regened = Math.min(max, current + elapsedTicks * regenPerTick);
        pool.put(id, regened);
        lastRegenAt.put(id, last + elapsedTicks * MS_PER_TICK);
        return regened;
    }
}
