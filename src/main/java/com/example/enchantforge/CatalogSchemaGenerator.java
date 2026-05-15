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
        header.addProperty("color", "0xFF55FFFF");
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
        search.addProperty("type", "hint");
        search.addProperty("text", "Search: (name, trigger, tag)");
        return search;
    }

    /**
     * Create an enchantment card as a panel widget containing labelled child rows.
     * Panel renders a background + border and recursively lays out children.
     */
    private static JsonObject createEnchantmentCard(CustomEnchant enchant) {
        JsonObject card = new JsonObject();
        card.addProperty("type", "panel");
        card.addProperty("padding", 4);
        card.addProperty("background", 0xFF0F0F23);
        card.addProperty("border", 0xFF2A2A4E);

        JsonArray children = new JsonArray();

        // Title
        JsonObject title = new JsonObject();
        title.addProperty("type", "text");
        title.addProperty("text", enchant.getDisplayName() +
                " (I-" + CustomEnchant.toRoman(enchant.getMaxLevel()) + ")");
        title.addProperty("color", "0xFF55FFFF");
        children.add(title);

        // Trigger
        children.add(hintRow("Trigger", triggerLabel(enchant.getTrigger())));

        // Items
        String itemsStr = enchant.getApplicableTo().isEmpty()
                ? "any"
                : String.join(", ", enchant.getApplicableTo().stream().limit(3).toList());
        if (enchant.getApplicableTo().size() > 3) itemsStr += ", ...";
        children.add(hintRow("Items", itemsStr));

        // Effect
        children.add(hintRow("Effect", enchant.getEffect().getClass().getSimpleName()));

        // Cooldown
        children.add(hintRow("Cooldown", enchant.getCooldownTicks() + " ticks"));

        // Description
        if (enchant.getDescription() != null && !enchant.getDescription().isBlank()) {
            JsonObject desc = new JsonObject();
            desc.addProperty("type", "hint");
            desc.addProperty("text", enchant.getDescription());
            children.add(desc);
        }

        // Tags
        List<String> tags = tagsFor(enchant);
        if (!tags.isEmpty()) {
            children.add(hintRow("Tags", String.join(", ", tags)));
        }

        card.add("widgets", children);
        return card;
    }

    private static JsonObject hintRow(String label, String value) {
        JsonObject row = new JsonObject();
        row.addProperty("type", "hint");
        row.addProperty("text", label + ": " + value);
        return row;
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
