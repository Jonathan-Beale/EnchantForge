package com.example.enchantforge;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.meta.components.UseCooldownComponent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CooldownVisuals implements Listener {

    private static final CooldownVisuals INSTANCE = new CooldownVisuals();
    private static Method setCooldownItemMethod;
    private static boolean lookedUp;
    private static boolean warnedPerItemOnly;
    private static JavaPlugin plugin;
    private static NamespacedKey itemIdKey;
    private static NamespacedKey cooldownGroupPrefixKey;

    // player -> active tracked cooldown entries for material fallback visuals.
    private static final Map<UUID, List<TrackedCooldown>> TRACKED = new HashMap<>();

    private CooldownVisuals() {}

    public static void init(JavaPlugin pluginInstance) {
        if (plugin != null) return;
        plugin = pluginInstance;
        itemIdKey = new NamespacedKey(plugin, "cooldown_item_id");
        cooldownGroupPrefixKey = new NamespacedKey(plugin, "cooldown_group");
        plugin.getServer().getPluginManager().registerEvents(INSTANCE, plugin);
    }

    public static void applyItemCooldown(Player player, ItemStack item, int ticks) {
        if (player == null || item == null || item.getType().isAir() || ticks <= 0) return;
        debug(player, "apply item cooldown material=" + item.getType() + " ticks=" + ticks);
        if (applyPerItemApi(player, item, ticks, false)) return;
        if (!isMaterialFallbackEnabled()) {
            warnPerItemOnlyMode(player, item);
            return;
        }
        trackMaterialFallback(player, item, ticks, false);
        refreshHeldItemVisual(player);
    }

    public static void applyMainHandCooldown(Player player, int ticks) {
        if (player == null || ticks <= 0) return;
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null || mainHand.getType().isAir()) return;
        debug(player, "apply main-hand cooldown material=" + mainHand.getType() + " ticks=" + ticks);
        if (applyPerItemApi(player, mainHand, ticks, true)) return;
        if (!isMaterialFallbackEnabled()) {
            warnPerItemOnlyMode(player, mainHand);
            return;
        }
        trackMaterialFallback(player, mainHand, ticks, true);
        refreshHeldItemVisual(player);
    }

    private static boolean applyPerItemApi(Player player, ItemStack item, int ticks, boolean persistToMainHand) {
        Method method = resolveSetCooldownItem();
        if (method == null) return false;
        ensurePerItemCooldownGroup(player, item, persistToMainHand);
        try {
            method.invoke(player, item, ticks);
            debug(player, "invoked per-item API material=" + item.getType() + " ticks=" + ticks);
            return true;
        } catch (ReflectiveOperationException ignored) {
            debug(player, "per-item API invocation failed; falling back to material tracking");
            return false;
        }
    }

    private static synchronized void trackMaterialFallback(Player player, ItemStack item, int ticks, boolean persistToMainHand) {
        if (plugin == null || itemIdKey == null) {
            debug(player, "plugin or itemIdKey is null; skipping material fallback tracking");
            return;
        }
        String itemId = ensureItemId(player, item, persistToMainHand);
        if (itemId == null || itemId.isBlank()) {
            debug(player, "itemId is null or blank; skipping material fallback tracking");
            return;
        }

        long endMs = System.currentTimeMillis() + (ticks * 50L);
        List<TrackedCooldown> entries = TRACKED.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>());
        for (int i = 0; i < entries.size(); i++) {
            TrackedCooldown entry = entries.get(i);
            if (entry.material == item.getType() && entry.itemId.equals(itemId)) {
                if (endMs > entry.endMs) {
                    entries.set(i, new TrackedCooldown(entry.material, entry.itemId, endMs));
                    debug(player, "updated tracked cooldown material=" + item.getType() + " id=" + itemId + " endMs=" + endMs);
                }
                return;
            }
        }
        entries.add(new TrackedCooldown(item.getType(), itemId, endMs));
        debug(player, "added tracked cooldown material=" + item.getType() + " id=" + itemId + " endMs=" + endMs);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (plugin == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshHeldItemVisual(event.getPlayer()));
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (plugin == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshHeldItemVisual(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TRACKED.remove(event.getPlayer().getUniqueId());
    }

    protected static void refreshHeldItemVisual(Player player) {
        if (resolveSetCooldownItem() != null) return;
        if (!isMaterialFallbackEnabled()) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            debug(player, "held item is null or air; skipping cooldown update");
            return;
        }

        Material heldType = held.getType();
        String heldId = readItemId(held);
        long now = System.currentTimeMillis();

        List<TrackedCooldown> entries = TRACKED.get(player.getUniqueId());
        if (entries == null || entries.isEmpty()) {
            player.setCooldown(heldType, 0);
            debug(player, "no tracked entries; cleared material cooldown material=" + heldType);
            return;
        }

        long bestEndMs = 0L;
        Iterator<TrackedCooldown> iterator = entries.iterator();
        while (iterator.hasNext()) {
            TrackedCooldown entry = iterator.next();
            if (entry.endMs <= now) {
                debug(player, "removing expired cooldown entry material=" + entry.material + " id=" + entry.itemId);
                iterator.remove();
                continue;
            }
            if (entry.material == heldType && heldId != null && heldId.equals(entry.itemId)) {
                bestEndMs = Math.max(bestEndMs, entry.endMs);
                debug(player, "found matching cooldown entry material=" + entry.material + " id=" + entry.itemId + " endMs=" + entry.endMs);
            }
        }

        if (entries.isEmpty()) {
            TRACKED.remove(player.getUniqueId());
        }

        if (bestEndMs <= now) {
            player.setCooldown(heldType, 0);
            debug(player, "no matching active entry; cleared material cooldown material=" + heldType + " heldId=" + heldId);
            return;
        }

        int remainingTicks = (int) Math.max(1, (bestEndMs - now + 49L) / 50L);
        player.setCooldown(heldType, remainingTicks);
        debug(player, "applied material cooldown material=" + heldType + " heldId=" + heldId + " remainingTicks=" + remainingTicks);
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
            // Ensure the generated ID lands on the actual inventory stack used for held-item checks.
            player.getInventory().setItemInMainHand(item);
        }
        debug(player, "generated item id material=" + item.getType() + " id=" + generated + " persistedMainHand=" + persistToMainHand);
        return generated;
    }

    private static void ensurePerItemCooldownGroup(Player player, ItemStack item, boolean persistToMainHand) {
        if (item == null || item.getType().isAir() || plugin == null) return;

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
        NamespacedKey currentGroup = cooldown.getCooldownGroup();
        if (expectedGroup.equals(currentGroup)) return;

        cooldown.setCooldownGroup(expectedGroup);
        meta.setUseCooldown(cooldown);
        item.setItemMeta(meta);
        if (persistToMainHand && player != null) {
            player.getInventory().setItemInMainHand(item);
        }
        debug(player, "assigned per-item cooldown group material=" + item.getType() + " group=" + expectedGroup);
    }

    public static String readItemId(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || itemIdKey == null) return null;
        return meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    public static String ensureItemIdForTracking(Player player, ItemStack item, boolean persistToMainHand) {
        if (item == null || item.getType().isAir()) return null;
        return ensureItemId(player, item, persistToMainHand);
    }

    public static boolean isTracked(Player player, Material material, String itemId) {
        List<TrackedCooldown> entries = TRACKED.get(player.getUniqueId());
        if (entries == null) return false;
        return entries.stream().anyMatch(entry -> entry.material == material && entry.itemId.equals(itemId));
    }

    private static Method resolveSetCooldownItem() {
        if (lookedUp) return setCooldownItemMethod;
        lookedUp = true;
        try {
            setCooldownItemMethod = Player.class.getMethod("setCooldown", ItemStack.class, int.class);
            debug(null, "per-item cooldown API available");
            return setCooldownItemMethod;
        } catch (NoSuchMethodException ignored) {
            setCooldownItemMethod = null;
            debug(null, "per-item cooldown API unavailable; using material fallback only");
            return null;
        }
    }

    private static boolean isMaterialFallbackEnabled() {
        return plugin != null && plugin.getConfig().getBoolean("cooldownVisuals.materialFallbackEnabled", false);
    }

    private static void warnPerItemOnlyMode(Player player, ItemStack item) {
        if (plugin == null || warnedPerItemOnly) return;
        warnedPerItemOnly = true;
        String playerName = player == null ? "unknown" : player.getName();
        plugin.getLogger().warning("[cooldown-visuals] Per-item cooldown API unavailable for "
                + item.getType() + " (player=" + playerName + "). Material fallback is disabled"
                + " to avoid global cooldowns. Enable cooldownVisuals.materialFallbackEnabled if desired.");
    }

    private static void debug(Player player, String message) {
        if (plugin == null) return;
        if (!plugin.getConfig().getBoolean("debug.cooldownVisuals", false)) return;
        String prefix = player == null ? "" : ("player=" + player.getName() + " ");
        plugin.getLogger().info("[cooldown-visuals] " + prefix + message);
    }

    private record TrackedCooldown(Material material, String itemId, long endMs) {}
}