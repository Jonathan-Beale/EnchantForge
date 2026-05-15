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
    /**
     * Generate the main catalog screen with scrollable, collapsible enchantment cards.
     */
    private static JsonObject generateCatalogScreen(EnchantmentRegistry registry) {
        JsonObject screen = new JsonObject();
        screen.addProperty("id", "enchantforge:catalog");
        screen.addProperty("plugin", "enchantforge");
        screen.addProperty("title", "EnchantForge Catalog");
        screen.addProperty("priority", 100);

        JsonArray widgets = new JsonArray();

        // Header + hint
        widgets.add(createHeader());
        widgets.add(createSpacer(2));
        widgets.add(createSearchHint());
        widgets.add(createSpacer(4));

        // Scrollable list of collapsible cards
        List<CustomEnchant> sorted = registry.getAll().stream()
                .sorted(Comparator.comparing(CustomEnchant::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        JsonArray cardWidgets = new JsonArray();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) cardWidgets.add(createSpacer(2));
            cardWidgets.add(createEnchantmentCard(sorted.get(i)));
        }

        JsonObject scrollPanel = new JsonObject();
        scrollPanel.addProperty("type", "scroll_panel");
        scrollPanel.addProperty("id", "catalog_list");
        scrollPanel.addProperty("height", 180);  // Will use remaining space
        scrollPanel.add("widgets", cardWidgets);
        widgets.add(scrollPanel);

        screen.add("widgets", widgets);
        return screen;
    }

    private static JsonObject createHeader() {
        JsonObject header = new JsonObject();
        header.addProperty("type", "text");
        header.addProperty("text", "EnchantForge Catalog");
        header.addProperty("color", "0xFF88DDDD");  // Softer teal
        return header;
    }

    private static JsonObject createSearchHint() {
        JsonObject hint = new JsonObject();
        hint.addProperty("type", "hint");
        hint.addProperty("text", "Click an enchantment to expand details");
        return hint;
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
     * Create a collapsible card for an enchantment.
     * Header shows: name + level + tags (always visible).
     * Body shows: trigger, items, effect, cooldown, description (expanded on click).
     */
    private static JsonObject createEnchantmentCard(CustomEnchant enchant) {
        JsonObject card = new JsonObject();
        card.addProperty("type", "collapsible");
        card.addProperty("id", "enchant_" + enchant.getKey().getKey());
        card.addProperty("open", false);
        card.addProperty("headerHeight", 18);
        card.addProperty("padding", 3);

        // Header label: name + level range + tags inline
        List<String> tags = tagsFor(enchant);
        String tagStr = tags.isEmpty() ? "" : "  [" + String.join(", ", tags) + "]";
        card.addProperty("label", enchant.getDisplayName() +
                " (I-" + CustomEnchant.toRoman(enchant.getMaxLevel()) + ")" + tagStr);

        card.addProperty("headerColor", "0xFFCCCCCC");
        card.addProperty("headerBg", "0xFF111122");
        card.addProperty("headerHoverBg", "0xFF1C1C33");
        card.addProperty("background", "0xFF0A0A18");

        // Children = detail rows shown only when expanded
        JsonArray children = new JsonArray();

        children.add(createSpacer(2));
        children.add(hintRow("Trigger", triggerLabel(enchant.getTrigger())));

        String itemsStr = enchant.getApplicableTo().isEmpty()
                ? "any"
                : String.join(", ", enchant.getApplicableTo().stream().limit(3).toList());
        if (enchant.getApplicableTo().size() > 3) itemsStr += ", ...";
        children.add(hintRow("Items", itemsStr));
        children.add(hintRow("Effect", enchant.getEffect().getClass().getSimpleName()));
        children.add(hintRow("Cooldown", enchant.getCooldownTicks() + " ticks"));

        if (enchant.getDescription() != null && !enchant.getDescription().isBlank()) {
            JsonObject desc = new JsonObject();
            desc.addProperty("type", "hint");
            desc.addProperty("text", enchant.getDescription());
            children.add(desc);
        }

        children.add(createSpacer(2));

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
