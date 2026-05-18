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
    private final PlayerEnchantIndex enchantIndex;

    public PlayerSessionListener(CooldownManager cooldowns, ActiveEffectTracker tracker,
                                 CombatTracker combatTracker, ResourcePackManager resourcePackManager,
                                 PlayerEnchantIndex enchantIndex) {
        this.cooldowns = cooldowns;
        this.tracker = tracker;
        this.combatTracker = combatTracker;
        this.resourcePackManager = resourcePackManager;
        this.enchantIndex = enchantIndex;
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
        enchantIndex.clearPlayer(id);
        EnergyManager.cleanup(id);
    }
}
