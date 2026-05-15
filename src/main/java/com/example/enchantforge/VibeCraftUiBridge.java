package com.example.enchantforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;

/**
 * Sends UI events to the VibeCraft mod channel without compile-time dependency
 * on the VibeCraft plugin.
 * 
 * Uses schema-driven UI: CatalogSchemaGenerator creates a dynamic schema from
 * enchantment YAMLs. New enchantments are automatically included without code changes.
 */
public final class VibeCraftUiBridge {

    private final JavaPlugin plugin;
    private static final boolean ENCHANTFORGE_CATALOG_UI_ENABLED = true;

    public VibeCraftUiBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Open the enchantment catalog for a player.
     * Sends a schema event to VibeCraft mod with dynamically generated UI from YAML files.
     * Client-side filtering is handled by the VibeCraftMod (search input databinding).
     */
    public void openEnchantCatalog(Player player, EnchantmentRegistry registry) {
        if (!ENCHANTFORGE_CATALOG_UI_ENABLED) {
            sendInfo(player, "EnchantForge catalog UI is temporarily disabled.");
            return;
        }

        // Generate schema from enchantment registry (all YAMLs automatically included)
        JsonObject catalogSchema = CatalogSchemaGenerator.generateCatalogSchema(registry);
        
        // Send schema to VibeCraft mod for rendering
        sendSchema(player, catalogSchema);
        
        // Open the catalog screen
        sendEvent(player, buildOpenScreenEvent("enchantforge:catalog").toString());
    }

    public void sendInfo(Player player, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "claude_text");
        JsonArray lines = new JsonArray();
        lines.add(message);
        obj.add("lines", lines);
        sendEvent(player, obj.toString());
    }

    /**
     * Send the catalog schema to the VibeCraft mod.
     * The mod will render the dynamic UI based on the schema.
     */
    /**
     * Send the catalog schema to the VibeCraft mod.
     * The mod will render the dynamic UI based on the schema.
     */
    private void sendSchema(Player player, JsonObject schema) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "schema");
        event.add("schema", schema);
        sendEvent(player, event.toString());
    }

    private JsonObject buildOpenScreenEvent(String screenId) {
        JsonObject evt = new JsonObject();
        evt.addProperty("type", "open_screen");
        evt.addProperty("screenId", screenId);
        return evt;
    }

    private void sendEvent(Player player, String json) {
        try {
            player.sendPluginMessage(plugin, "vibecraft:events", json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Ignore client-side channel errors for players without the mod.
        }
    }
}
