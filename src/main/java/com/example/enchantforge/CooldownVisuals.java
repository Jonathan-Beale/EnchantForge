package com.example.enchantforge;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.meta.components.UseCooldownComponent;

import java.util.UUID;

// All methods called on the main server thread — no synchronization needed.
public final class CooldownVisuals {

    private static JavaPlugin plugin;
    private static NamespacedKey itemIdKey;
    private static NamespacedKey cooldownGroupPrefixKey;

    private CooldownVisuals() {}

    public static void init(JavaPlugin pluginInstance) {
        if (plugin != null) return;
        plugin = pluginInstance;
        itemIdKey = new NamespacedKey(plugin, "cooldown_item_id");
        cooldownGroupPrefixKey = new NamespacedKey(plugin, "cooldown_group");
    }

    public static void applyItemCooldown(Player player, ItemStack item, int ticks) {
        if (player == null || item == null || item.getType() == Material.AIR || ticks <= 0) return;
        ensurePerItemCooldownGroup(player, item, false);
        player.setCooldown(item, ticks);
        debug(player, "apply item cooldown material=" + item.getType() + " ticks=" + ticks);
    }

    public static void applyMainHandCooldown(Player player, int ticks) {
        if (player == null || ticks <= 0) return;
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null || mainHand.getType() == Material.AIR) return;
        ensurePerItemCooldownGroup(player, mainHand, true);
        player.setCooldown(mainHand, ticks);
        debug(player, "apply main-hand cooldown material=" + mainHand.getType() + " ticks=" + ticks);
    }

    private static void ensurePerItemCooldownGroup(Player player, ItemStack item, boolean persistToMainHand) {
        if (item == null || item.getType() == Material.AIR || plugin == null) return;

        String itemId = ensureItemId(player, item, persistToMainHand);
        if (itemId == null || itemId.isBlank()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        UseCooldownComponent cooldown = meta.getUseCooldown();
        if (cooldown == null) {
            debug(player, "use cooldown component unavailable for material=" + item.getType());
            return;
        }

        NamespacedKey expectedGroup = new NamespacedKey(cooldownGroupPrefixKey.getNamespace(),
                cooldownGroupPrefixKey.getKey() + "_" + itemId.replace("-", ""));
        if (expectedGroup.equals(cooldown.getCooldownGroup())) return;

        cooldown.setCooldownGroup(expectedGroup);
        meta.setUseCooldown(cooldown);
        item.setItemMeta(meta);
        if (persistToMainHand && player != null) {
            player.getInventory().setItemInMainHand(item);
        }
        debug(player, "assigned per-item cooldown group material=" + item.getType() + " group=" + expectedGroup);
    }

    public static String readItemId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || itemIdKey == null) return null;
        return meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    public static String ensureItemIdForTracking(Player player, ItemStack item, boolean persistToMainHand) {
        if (item == null || item.getType() == Material.AIR) return null;
        return ensureItemId(player, item, persistToMainHand);
    }

    private static String ensureItemId(Player player, ItemStack item, boolean persistToMainHand) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || itemIdKey == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String current = pdc.get(itemIdKey, PersistentDataType.STRING);
        if (current != null && !current.isBlank()) return current;

        String generated = UUID.randomUUID().toString();
        pdc.set(itemIdKey, PersistentDataType.STRING, generated);
        item.setItemMeta(meta);
        if (persistToMainHand && player != null) {
            player.getInventory().setItemInMainHand(item);
        }
        debug(player, "generated item id material=" + item.getType() + " id=" + generated);
        return generated;
    }

    private static void debug(Player player, String message) {
        if (plugin == null) return;
        if (!plugin.getConfig().getBoolean("debug.cooldownVisuals", false)) return;
        String prefix = player == null ? "" : ("player=" + player.getName() + " ");
        plugin.getLogger().info("[cooldown-visuals] " + prefix + message);
    }
}
