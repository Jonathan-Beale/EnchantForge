package com.example.enchantforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Handles plugin-scoped mod input for EnchantForge UI interactions.
 */
public final class EnchantForgeModInputListener implements PluginMessageListener {

    private final EnchantmentRegistry registry;
    private final VibeCraftUiBridge uiBridge;

    public EnchantForgeModInputListener(EnchantmentRegistry registry, VibeCraftUiBridge uiBridge) {
        this.registry = registry;
        this.uiBridge = uiBridge;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        try {
            String json = new String(message, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String targetPlugin = obj.has("plugin") ? obj.get("plugin").getAsString() : "vibecraft";
            if (!"enchantforge".equalsIgnoreCase(targetPlugin)) {
                return;
            }

            if (!obj.has("type")) return;
            String type = obj.get("type").getAsString();

            if ("request_history".equals(type)) {
                // Schema-driven UI: just open the catalog, filtering happens client-side
                uiBridge.openEnchantCatalog(player, registry);
                return;
            }

            if ("message".equals(type) && obj.has("message")) {
                handleMessage(player, obj.get("message").getAsString().trim());
            }
        } catch (Exception ignored) {
        }
    }

    private void handleMessage(Player player, String message) {
        if (message.isBlank()) return;
        String lower = message.toLowerCase(Locale.ROOT);

        if ("show all".equals(lower) || "all".equals(lower)) {
            // Show full catalog (client-side filtering)
            uiBridge.openEnchantCatalog(player, registry);
            return;
        }
        if (lower.startsWith("apply ")) {
            applyEnchant(player, message.substring("apply ".length()).trim());
            return;
        }

        // For other messages, just send info
        uiBridge.sendInfo(player, "Unknown command: " + message);
    }

    private void applyEnchant(Player player, String args) {
        if (args.isBlank()) {
            uiBridge.sendInfo(player, "Usage: apply <id> [level]");
            return;
        }
        String[] parts = args.split("\\s+");
        String enchantId = parts[0];
        String level = parts.length > 1 ? parts[1] : "1";

        boolean ok = player.performCommand("cenchant " + enchantId + " " + level);
        if (!ok) {
            uiBridge.sendInfo(player, "Could not apply enchant. Try: apply " + enchantId + " " + level);
        }

        // Keep the catalog visible after command execution (client-side filtering preserved)
        uiBridge.openEnchantCatalog(player, registry);
    }
}
