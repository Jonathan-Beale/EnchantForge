package com.example.enchantforge;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RightClickListenerTest {

    private Player mockPlayer;
    private Plugin mockPlugin;
    private RightClickListener listener;

    @BeforeEach
    void setUp() {
        mockPlayer = mock(Player.class);
        mockPlugin = mock(Plugin.class);
        FileConfiguration mockConfig = mock(FileConfiguration.class);
        when(mockConfig.getBoolean(any(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(mockPlugin.getConfig()).thenReturn(mockConfig);
        Server mockServer = mock(Server.class);
        BukkitScheduler mockScheduler = mock(BukkitScheduler.class);
        when(mockPlugin.getServer()).thenReturn(mockServer);
        when(mockServer.getScheduler()).thenReturn(mockScheduler);

        PlayerInventory mockInventory = mock(PlayerInventory.class);
        ItemStack mockMainHand = mock(ItemStack.class);
        ItemStack mockOffHand = mock(ItemStack.class);

        when(mockMainHand.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(mockOffHand.getType()).thenReturn(Material.AIR);
        when(mockPlayer.getInventory()).thenReturn(mockInventory);
        when(mockInventory.getItemInMainHand()).thenReturn(mockMainHand);
        when(mockInventory.getItemInOffHand()).thenReturn(mockOffHand);
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        EnchantmentRegistry mockRegistry = mock(EnchantmentRegistry.class);
        when(mockRegistry.getEnchants(any())).thenReturn(Collections.emptyMap());

        PlayerEnchantIndex mockEnchantIndex = mock(PlayerEnchantIndex.class);
        when(mockEnchantIndex.getByTrigger(any(), any())).thenReturn(Collections.emptyList());

        listener = new RightClickListener(mockRegistry, new CooldownManager(), mockPlugin, mockEnchantIndex);
    }

    @Test
    void testOnRightClickCallsGetInventory() {
        org.bukkit.event.player.PlayerInteractEvent event =
                mock(org.bukkit.event.player.PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(mockPlayer);
        when(event.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_AIR);
        when(event.getHand()).thenReturn(org.bukkit.inventory.EquipmentSlot.HAND);

        listener.onRightClick(event);

        verify(mockPlayer, atLeastOnce()).getInventory();
    }
}
