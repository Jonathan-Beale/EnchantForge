package com.example.enchantforge;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CooldownVisualsTest {

    private Player mockPlayer;
    private ItemStack mockItem;
    private JavaPlugin mockPlugin;

    @BeforeEach
    void setUp() throws Exception {
        // Reset CooldownVisuals static state so init() always runs fresh with our mock
        Field pluginField = CooldownVisuals.class.getDeclaredField("plugin");
        pluginField.setAccessible(true);
        pluginField.set(null, null);

        mockPlayer = mock(Player.class);
        mockItem = mock(ItemStack.class);
        mockPlugin = mock(JavaPlugin.class);

        when(mockPlugin.getName()).thenReturn("enchantforge");
        FileConfiguration mockConfig = mock(FileConfiguration.class);
        when(mockConfig.getBoolean(any(), anyBoolean())).thenAnswer(inv -> inv.getArgument(1));
        when(mockPlugin.getConfig()).thenReturn(mockConfig);
        when(mockItem.getType()).thenReturn(Material.DIAMOND_SWORD);

        ItemMeta mockMeta = mock(ItemMeta.class);
        PersistentDataContainer mockPdc = mock(PersistentDataContainer.class);
        when(mockMeta.getPersistentDataContainer()).thenReturn(mockPdc);
        when(mockPdc.get(any(), eq(PersistentDataType.STRING))).thenReturn(UUID.randomUUID().toString());
        when(mockItem.getItemMeta()).thenReturn(mockMeta);

        UseCooldownComponent mockCooldown = mock(UseCooldownComponent.class);
        when(mockMeta.getUseCooldown()).thenReturn(mockCooldown);
        when(mockPlugin.getServer()).thenReturn(mock(org.bukkit.Server.class));
        when(mockPlayer.getUniqueId()).thenReturn(UUID.randomUUID());

        CooldownVisuals.init(mockPlugin);
    }

    @Test
    void applyItemCooldownCallsPerItemApi() {
        // After §17 refactor the per-item API is called directly — no material fallback
        CooldownVisuals.applyItemCooldown(mockPlayer, mockItem, 600);
        verify(mockPlayer, atLeastOnce()).setCooldown(eq(mockItem), anyInt());
        verify(mockPlayer, never()).setCooldown(eq(Material.DIAMOND_SWORD), anyInt());
    }
}
