package com.example.enchantforge;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CooldownVisualsTest {

    private Player mockPlayer;
    private ItemStack mockItem;
    private JavaPlugin mockPlugin;

    @BeforeEach
    void setUp() {
        mockPlayer = mock(Player.class);
        mockItem = mock(ItemStack.class);
        mockPlugin = mock(JavaPlugin.class);

        // Mock item behavior
        when(mockItem.getType()).thenReturn(Material.DIAMOND_SWORD);
        ItemMeta mockMeta = mock(ItemMeta.class);
        when(mockItem.getItemMeta()).thenReturn(mockMeta);
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        // Initialize CooldownVisuals
        CooldownVisuals.init(mockPlugin);
    }

    @Test
    void testApplyItemCooldown() {
        // Apply cooldown
        CooldownVisuals.applyItemCooldown(mockPlayer, mockItem, 600);

        // With Paper per-item API available, cooldown should be applied to the stack itself.
        verify(mockPlayer, atLeastOnce()).setCooldown(eq(mockItem), anyInt());
        verify(mockPlayer, never()).setCooldown(eq(Material.DIAMOND_SWORD), anyInt());
    }

    @Test
    void testRefreshHeldItemVisual() {
        // Apply cooldown
        CooldownVisuals.applyItemCooldown(mockPlayer, mockItem, 600);

        // Refresh visuals
        CooldownVisuals.refreshHeldItemVisual(mockPlayer);

        // Per-item API path should not use material fallback visual updates.
        verify(mockPlayer, never()).setCooldown(eq(Material.DIAMOND_SWORD), anyInt());
    }

    @Test
    void testExpireCooldown() throws InterruptedException {
        // Apply cooldown
        CooldownVisuals.applyItemCooldown(mockPlayer, mockItem, 20); // 1 second

        // Wait for cooldown to expire
        Thread.sleep(1100);

        // Refresh visuals
        CooldownVisuals.refreshHeldItemVisual(mockPlayer);

        // Per-item API path should not involve material fallback.
        verify(mockPlayer, never()).setCooldown(eq(Material.DIAMOND_SWORD), anyInt());
    }

    @Test
    void testSetCooldownCalledOnPlayer() {
        // Apply cooldown
        CooldownVisuals.applyItemCooldown(mockPlayer, mockItem, 600);

        // Verify setCooldown is called with correct arguments
        verify(mockPlayer, atLeastOnce()).setCooldown(eq(Material.DIAMOND_SWORD), anyInt());
    }
}