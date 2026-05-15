package com.example.enchantforge;

import com.example.enchantforge.trigger.EnchantTrigger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Procedurally generates UI schema for the EnchantForge catalog.
 * Schema is dynamically built from enchantment YAML files.
 * New enchantments are automatically included without UI code changes.
 */
public final class CatalogSchemaGenerator {

    private CatalogSchemaGenerator() {}

    /**
     * Generate a complete catalog schema from the registry.
     * The schema defines a searchable catalog screen with dynamically generated
     * enchantment cards showing all YAML properties.
     */
    public static JsonObject generateCatalogSchema(EnchantmentRegistry registry) {
        JsonObject schema = new JsonObject();
        schema.addProperty("version", 1);
        schema.addProperty("defaultPlugin", "enchantforge");
        
        // Define the catalog screen
        JsonArray screens = new JsonArray();
        screens.add(generateCatalogScreen(registry));
        schema.add("screens", screens);
        
        return schema;
    }

    /**
     * Generate the main catalog screen with search and enchantment grid
     */
    private static JsonObject generateCatalogScreen(EnchantmentRegistry registry) {
        JsonObject screen = new JsonObject();
        screen.addProperty("id", "enchantforge:catalog");
        screen.addProperty("plugin", "enchantforge");
        screen.addProperty("title", "EnchantForge Catalog");
        screen.addProperty("priority", 100);
        
        // Build widgets array directly (not nested under panel)
        JsonArray widgets = new JsonArray();
        
        // Header
        widgets.add(createHeader());
        widgets.add(createSpacer(2));
        
        // Search/filter bar
        widgets.add(createSearchBar());
        widgets.add(createSpacer(4));
        
        // Enchantment cards (one per enchant, sorted)
        List<CustomEnchant> sorted = registry.getAll().stream()
                .sorted(Comparator.comparing(CustomEnchant::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) widgets.add(createSpacer(2)); // Spacing between cards
            widgets.add(createEnchantmentCard(sorted.get(i)));
        }
        
        screen.add("widgets", widgets);
        
        return screen;
    }

    private static JsonObject createHeader() {
        JsonObject header = new JsonObject();
        header.addProperty("type", "text");
        header.addProperty("text", "EnchantForge Catalog");
        header.addProperty("style", "heading");
        
        JsonObject color = new JsonObject();
        color.addProperty("bg", "1a1a2e");
        color.addProperty("text", "55FFFF");
        header.add("colors", color);
        
        return header;
    }

    private static JsonObject createSpacer(int height) {
        JsonObject spacer = new JsonObject();
        spacer.addProperty("type", "spacer");
        spacer.addProperty("height", height);
        return spacer;
    }

    private static JsonObject createSearchBar() {
        JsonObject search = new JsonObject();
        search.addProperty("type", "text");
        search.addProperty("text", "🔍 Search: (name, trigger, tag)");
        
        JsonObject color = new JsonObject();
        color.addProperty("text", "0xFFFFFF");
        search.add("colors", color);
        
        return search;
    }

    /**
     * Create a card displaying a single enchantment with all YAML properties.
     * Each card shows: name, level range, applicable items, trigger, effect, description, tags
     */
    private static JsonObject createEnchantmentCard(CustomEnchant enchant) {
        JsonObject card = new JsonObject();
        card.addProperty("type", "text");
        
        // Build formatted text with all enchantment info
        StringBuilder text = new StringBuilder();
        text.append("**").append(enchant.getDisplayName())
            .append(" (I-").append(CustomEnchant.toRoman(enchant.getMaxLevel())).append(")**\n");
        
        // Trigger info
        text.append("*Trigger:* ").append(triggerLabel(enchant.getTrigger())).append("\n");
        
        // Applicable items
        String itemsStr = enchant.getApplicableTo().isEmpty() ? 
            "any" : String.join(", ", enchant.getApplicableTo().stream().limit(3).toList());
        if (enchant.getApplicableTo().size() > 3) itemsStr += " ...";
        text.append("*Items:* ").append(itemsStr).append("\n");
        
        // Effect style
        String effectStyle = enchant.getEffect().getClass().getSimpleName();
        text.append("*Effect:* ").append(effectStyle).append("\n");
        
        // Cooldown
        text.append("*Cooldown:* ").append(enchant.getCooldownTicks()).append(" ticks\n");
        
        // Description
        if (enchant.getDescription() != null && !enchant.getDescription().isBlank()) {
            text.append("*Description:* ").append(enchant.getDescription()).append("\n");
        }
        
        // Tags
        List<String> tags = tagsFor(enchant);
        if (!tags.isEmpty()) {
            text.append("*Tags:* ").append(String.join(", ", tags));
        }
        
        card.addProperty("text", text.toString().trim());
        
        // Color the card
        JsonObject color = new JsonObject();
        color.addProperty("text", "0xDDDDDD");
        color.addProperty("bg", "0xFF0F0F23");
        card.add("colors", color);
        
        return card;
    }

    /**
     * Get tags for an enchantment (offensive, defensive, utility, etc.)
     * These are used for search/filtering on the client side.
     */
    private static List<String> tagsFor(CustomEnchant enchant) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        
        // Trigger-based tags
        String triggerType = enchant.getTrigger().getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (triggerType.contains("equip")) {
            tags.add("passive");
        } else if (triggerType.contains("damagetaken")) {
            tags.add("reactive");
        } else {
            tags.add("conditional");
        }
        
        // Effect-based tags
        if (triggerType.contains("deal") || triggerType.contains("kill")) {
            tags.add("offense");
        }
        if (triggerType.contains("damage") || triggerType.contains("equip")) {
            tags.add("defense");
        }
        if (triggerType.contains("rightclick") || triggerType.contains("equip")) {
            tags.add("utility");
        }
        
        // Description/name-based tags
        String key = enchant.getKey().getKey().toLowerCase(Locale.ROOT);
        String name = enchant.getDisplayName().toLowerCase(Locale.ROOT);
        String text = key + " " + name;
        
        if (text.contains("heal") || text.contains("vamp") || text.contains("vital") || 
            text.contains("guard") || text.contains("bulwark")) {
            tags.add("sustain");
        }
        if (text.contains("swift") || text.contains("shadow") || text.contains("feral")) {
            tags.add("mobility");
        }
        
        return List.copyOf(tags);
    }

    /**
     * Get a human-readable label for the trigger type
     */
    private static String triggerLabel(EnchantTrigger trigger) {
        String className = trigger.getClass().getSimpleName();
        return switch (className) {
            case "OnRightClickTrigger" -> "Right Click";
            case "OnEquipTrigger" -> "On Equip";
            case "OnDealDamageTrigger" -> "On Deal Damage";
            case "OnDamageTakenTrigger" -> "On Damage Taken";
            case "OnKillEntityTrigger" -> "On Kill";
            case "OnSuitJumpTrigger" -> "On Jump (Suit)";
            case "StatThresholdTrigger" -> "Stat Threshold";
            default -> className.replace("Trigger", "");
        };
    }
}
