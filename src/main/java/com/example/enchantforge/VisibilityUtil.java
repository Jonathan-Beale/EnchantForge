package com.example.enchantforge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Full invisibility — body, worn gear, and off-hand hidden from all perspectives:
 *   1. hidePlayer()         — removes the entity from other clients entirely
 *   2. setInvisible(true)   — hides the body model in the player's own F5 view
 *   3. clear inventory slots — the client renders its own armor from inventory state,
 *                              so sendEquipmentChange to self is ignored; clearing the
 *                              actual slots is the only way to hide gear in F5
 *
 * Armor and off-hand are saved and restored in show(). Death and quit handlers ensure
 * items are never lost.
 */
public final class VisibilityUtil implements Listener {

    private static Plugin plugin;
    private static final Set<UUID>              hidden       = Collections.synchronizedSet(new HashSet<>());
    private static final Map<UUID, ItemStack[]> savedArmor   = new HashMap<>();
    private static final Map<UUID, ItemStack>   savedOffHand = new HashMap<>();

    private VisibilityUtil() {}

    public static void init(Plugin p) {
        plugin = p;
        p.getServer().getPluginManager().registerEvents(new VisibilityUtil(), p);
    }

    public static void hide(Player player) {
        hidden.add(player.getUniqueId());
        player.setInvisible(true);

        PlayerInventory inv = player.getInventory();
        savedArmor.put(player.getUniqueId(), new ItemStack[]{
            inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()
        });
        savedOffHand.put(player.getUniqueId(), inv.getItemInOffHand());
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
        inv.setItemInOffHand(null);

        for (Player other : Bukkit.getOnlinePlayers()) {
            other.hidePlayer(plugin, player);
        }
    }

    public static void show(Player player) {
        if (!hidden.remove(player.getUniqueId())) return;
        player.setInvisible(false);

        // Restore equipment BEFORE showPlayer() so the entity re-spawns with correct gear.
        PlayerInventory inv = player.getInventory();
        ItemStack[] armor = savedArmor.remove(player.getUniqueId());
        if (armor != null) {
            inv.setHelmet(armor[0]);
            inv.setChestplate(armor[1]);
            inv.setLeggings(armor[2]);
            inv.setBoots(armor[3]);
        }
        inv.setItemInOffHand(savedOffHand.remove(player.getUniqueId()));

        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
    }

    public static boolean isHidden(UUID id) {
        return hidden.contains(id);
    }

    /** Players joining mid-effect must not see currently hidden players. */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player newPlayer = event.getPlayer();
        for (UUID id : hidden) {
            Player hiddenPlayer = Bukkit.getPlayer(id);
            if (hiddenPlayer != null) {
                newPlayer.hidePlayer(plugin, hiddenPlayer);
            }
        }
    }

    /** Restore gear before death processing so items drop/keep correctly under gamerule. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        show(event.getPlayer());
    }

    /** Restore gear on logout to prevent silent item loss. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        show(event.getPlayer());
    }
}
