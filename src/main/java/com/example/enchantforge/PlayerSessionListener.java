package com.example.enchantforge;

import com.example.enchantforge.effect.EnergyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerSessionListener implements Listener {

    private final CooldownManager cooldowns;
    private final ActiveEffectTracker tracker;
    private final CombatTracker combatTracker;
    private final ResourcePackManager resourcePackManager;

    public PlayerSessionListener(CooldownManager cooldowns, ActiveEffectTracker tracker,
                                 CombatTracker combatTracker, ResourcePackManager resourcePackManager) {
        this.cooldowns = cooldowns;
        this.tracker = tracker;
        this.combatTracker = combatTracker;
        this.resourcePackManager = resourcePackManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        resourcePackManager.sendPackTo(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var id = event.getPlayer().getUniqueId();
        cooldowns.clearPlayer(id);
        tracker.clearPlayer(id);
        combatTracker.clearPlayer(id);
        EnergyManager.cleanup(id);
    }
}
