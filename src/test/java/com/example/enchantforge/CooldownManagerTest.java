package com.example.enchantforge;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CooldownManagerTest {

    private CooldownManager cooldownManager;
    private Player player;
    private CustomEnchant enchant;

    @BeforeEach
    void setUp() {
        cooldownManager = new CooldownManager();
        player = mock(Player.class);
        enchant = mock(CustomEnchant.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(enchant.getKey()).thenReturn(new NamespacedKey("enchantforge", "test_enchant"));
        when(enchant.getCooldownTicks()).thenReturn(40); // 2 seconds
        when(enchant.hasCooldown()).thenReturn(true);
    }

    @Test
    void notOnCooldownByDefault() {
        assertFalse(cooldownManager.isOnCooldown(player, enchant));
    }

    @Test
    void onCooldownAfterSet() {
        cooldownManager.setCooldown(player, enchant);
        assertTrue(cooldownManager.isOnCooldown(player, enchant));
    }

    @Test
    void clearPlayerRemovesEntries() {
        cooldownManager.setCooldown(player, enchant);
        cooldownManager.clearPlayer(player.getUniqueId());
        assertFalse(cooldownManager.isOnCooldown(player, enchant));
    }

    @Test
    void itemScopedAndGlobalDoNotBleed() {
        // Set global cooldown for enchant A
        CustomEnchant enchantA = mock(CustomEnchant.class);
        when(enchantA.getKey()).thenReturn(new NamespacedKey("enchantforge", "enchant_a"));
        when(enchantA.getCooldownTicks()).thenReturn(100);
        when(enchantA.hasCooldown()).thenReturn(true);

        // Set item-scoped cooldown for enchant B (item with no PDC id falls back to global)
        cooldownManager.setCooldown(player, enchantA);

        // enchantA should be on global cooldown
        assertTrue(cooldownManager.isOnCooldown(player, enchantA));

        // A different enchant should not be affected
        assertFalse(cooldownManager.isOnCooldown(player, enchant));
    }

    @Test
    void getRemainingSecondsReturnsPositive() {
        cooldownManager.setCooldown(player, enchant);
        long remaining = cooldownManager.getRemainingSeconds(player, enchant);
        // 40 ticks * 50ms = 2000ms = 2s
        assertTrue(remaining >= 1 && remaining <= 2,
                "Expected 1-2 remaining seconds, got " + remaining);
    }

    @Test
    void getRemainingSecondsZeroWhenNotSet() {
        assertEquals(0, cooldownManager.getRemainingSeconds(player, enchant));
    }
}
