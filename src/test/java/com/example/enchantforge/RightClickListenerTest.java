package com.example.enchantforge;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

class RightClickListenerTest {

    private Player mockPlayer;
    private ItemStack mockItem;
    private Plugin mockPlugin;
    private RightClickListener listener;

    @BeforeEach
    void setUp() {
        mockPlayer = mock(Player.class);
        mockItem = mock(ItemStack.class);
        mockPlugin = mock(Plugin.class);

        // Mock item behavior
        when(mockItem.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(mockPlayer.getInventory().getItemInMainHand()).thenReturn(mockItem);

        // Initialize RightClickListener
        listener = new RightClickListener(null, null, mockPlugin);
    }

    @Test
    void testOnRightClick() {
        // Mock event
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_AIR);

        // Trigger event
        listener.onRightClick(event);

        // Verify interactions
        verify(mockPlayer, atLeastOnce()).getInventory();
    }
}