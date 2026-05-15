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
        
        // Main panel layout: search bar + enchantment grid
        JsonObject panel = new JsonObject();
        panel.addProperty("type", "panel");
        panel.addProperty("direction", "column");
        panel.addProperty("spacing", 8);
        
        JsonArray widgets = new JsonArray();
        
        // Header
        widgets.add(createHeader());
        
        // Search/filter bar
        widgets.add(createSearchBar());
        
        // Enchantment cards (grid layout)
        widgets.add(createEnchantmentGrid(registry));
        
        panel.add("children", widgets);
        
        JsonArray panelArray = new JsonArray();
        panelArray.add(panel);
        screen.add("panel", panelArray);
        
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

    private static JsonObject createSearchBar() {
        JsonObject search = new JsonObject();
        search.addProperty("type", "input");
        search.addProperty("id", "catalog_search");
        search.addProperty("placeholder", "Search enchantments... (name, tag, trigger)");
        
        JsonObject config = new JsonObject();
        config.addProperty("clearable", true);
        config.addProperty("maxLength", 50);
        search.add("config", config);
        
        return search;
    }

    /**
     * Create a grid of enchantment cards with all YAML properties.
     * Cards are procedurally generated from the registry - no hardcoding needed.
     */
    private static JsonObject createEnchantmentGrid(EnchantmentRegistry registry) {
        JsonObject grid = new JsonObject();
        grid.addProperty("type", "panel");
        grid.addProperty("direction", "column");
        grid.addProperty("spacing", 4);
        
        // Sort enchantments alphabetically
        List<CustomEnchant> sorted = registry.getAll().stream()
                .sorted(Comparator.comparing(CustomEnchant::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        
        JsonArray cards = new JsonArray();
        for (CustomEnchant enchant : sorted) {
            cards.add(createEnchantmentCard(enchant));
        }
        
        grid.add("children", cards);
        return grid;
    }

    /**
     * Create a card displaying a single enchantment with all YAML properties.
     * Each card shows: name, level range, applicable items, trigger, effect, description, tags
     */
    private static JsonObject createEnchantmentCard(CustomEnchant enchant) {
        JsonObject card = new JsonObject();
        card.addProperty("type", "panel");
        card.addProperty("direction", "column");
        card.addProperty("spacing", 3);
        
        // Add data binding key for search filtering on client-side
        String searchKey = enchant.getKey().getKey() + " " + 
                          enchant.getDisplayName() + " " +
                          String.join(" ", tagsFor(enchant));
        card.addProperty("dataBinding", searchKey.toLowerCase(Locale.ROOT));
        
        JsonObject borderStyle = new JsonObject();
        borderStyle.addProperty("border", "0xFF2A2A4E");
        borderStyle.addProperty("bg", "0xFF0F0F23");
        borderStyle.addProperty("padding", 4);
        card.add("style", borderStyle);
        
        JsonArray cardContent = new JsonArray();
        
        // Title row: name + level + tags
        cardContent.add(createCardTitle(enchant));
        
        // Trigger info
        cardContent.add(createInfoRow("Trigger", triggerLabel(enchant.getTrigger())));
        
        // Applicable items
        String itemsStr = enchant.getApplicableTo().size() == 0 ? 
            "any" : String.join(", ", enchant.getApplicableTo().stream().limit(3).toList());
        if (enchant.getApplicableTo().size() > 3) itemsStr += " ...";
        cardContent.add(createInfoRow("Items", itemsStr));
        
        // Effect style
        String effectStyle = enchant.getEffect().getClass().getSimpleName();
        cardContent.add(createInfoRow("Effect", effectStyle));
        
        // Cooldown
        cardContent.add(createInfoRow("Cooldown", enchant.getCooldownTicks() + " ticks"));
        
        // Description
        if (enchant.getDescription() != null && !enchant.getDescription().isBlank()) {
            JsonObject descWidget = new JsonObject();
            descWidget.addProperty("type", "text");
            descWidget.addProperty("text", enchant.getDescription());
            
            JsonObject descColor = new JsonObject();
            descColor.addProperty("text", "0xBBBBBB");
            descWidget.add("colors", descColor);
            
            cardContent.add(descWidget);
        }
        
        // Tags
        List<String> tags = tagsFor(enchant);
        if (!tags.isEmpty()) {
            cardContent.add(createTagRow(tags));
        }
        
        card.add("children", cardContent);
        return card;
    }

    private static JsonObject createCardTitle(CustomEnchant enchant) {
        JsonObject title = new JsonObject();
        title.addProperty("type", "text");
        
        String titleText = enchant.getDisplayName() + " (I-" + 
                          CustomEnchant.toRoman(enchant.getMaxLevel()) + ")";
        title.addProperty("text", titleText);
        
        JsonObject titleColor = new JsonObject();
        titleColor.addProperty("text", "0x55FFFF");
        title.add("colors", titleColor);
        
        return title;
    }

    private static JsonObject createInfoRow(String label, String value) {
        JsonObject row = new JsonObject();
        row.addProperty("type", "text");
        row.addProperty("text", "**" + label + "**: " + value);
        
        JsonObject color = new JsonObject();
        color.addProperty("text", "0xDDDDDD");
        row.add("colors", color);
        
        return row;
    }

    private static JsonObject createTagRow(List<String> tags) {
        JsonObject tagRow = new JsonObject();
        tagRow.addProperty("type", "text");
        tagRow.addProperty("text", "**Tags**: " + String.join(", ", tags));
        
        JsonObject tagColor = new JsonObject();
        tagColor.addProperty("text", "0xAA99FF");
        tagRow.add("colors", tagColor);
        
        return tagRow;
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
