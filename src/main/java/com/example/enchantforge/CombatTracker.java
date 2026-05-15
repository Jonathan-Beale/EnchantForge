package com.example.enchantforge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatTracker {

    private final Map<UUID, Long> lastHit = new HashMap<>();

    public void recordHit(UUID playerId) {
        lastHit.put(playerId, System.currentTimeMillis());
    }

    public long millisSinceLastHit(UUID playerId) {
        Long last = lastHit.get(playerId);
        return last == null ? Long.MAX_VALUE : System.currentTimeMillis() - last;
    }

    public void clearPlayer(UUID playerId) {
        lastHit.remove(playerId);
    }
}
