package com.example.enchantforge;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PlayerLifecycleRegistry implements Listener {

    private final List<Consumer<Player>> joinHandlers = new ArrayList<>();
    private final List<Consumer<Player>> quitHandlers = new ArrayList<>();

    public PlayerLifecycleRegistry onJoin(Consumer<Player> handler) {
        joinHandlers.add(handler);
        return this;
    }

    public PlayerLifecycleRegistry onQuit(Consumer<Player> handler) {
        quitHandlers.add(handler);
        return this;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        joinHandlers.forEach(h -> h.accept(event.getPlayer()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        quitHandlers.forEach(h -> h.accept(event.getPlayer()));
    }
}
