package com.example.enchantforge;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class EnchantCatalogListener implements Listener {

    private final EnchantmentRegistry registry;
    private final VibeCraftUiBridge uiBridge;

    public EnchantCatalogListener(EnchantmentRegistry registry, VibeCraftUiBridge uiBridge) {
        this.registry = registry;
        this.uiBridge = uiBridge;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!EnchantCatalogItem.isCatalog(event.getItem())) return;
        event.setCancelled(true);
        openCatalog(event.getPlayer());
    }

    private void openCatalog(Player player) {
        uiBridge.openEnchantCatalog(player, registry);
    }
}
