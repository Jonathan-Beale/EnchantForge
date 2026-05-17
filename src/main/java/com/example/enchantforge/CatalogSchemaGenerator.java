package com.example.enchantforge;

import com.example.enchantforge.trigger.EnchantTrigger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

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

        List<CustomEnchant> sorted = registry.getAll().stream()
            .filter(enchant -> !enchant.getCatalogMetadata().hidden())
            .sorted(Comparator.comparing(CustomEnchant::getDisplayName, String.CASE_INSENSITIVE_ORDER))
            .sorted(Comparator.comparingInt(enchant -> enchant.getCatalogMetadata().order()))
            .toList();

        JsonArray widgets = new JsonArray();

        // Header + controls
        widgets.add(createHeader());
        widgets.add(createSpacer(2));
        widgets.add(createSearchBar());
        widgets.add(createSpacer(3));

        Set<String> allTags = collectCatalogFacetValues(sorted, facets -> facets.tags());
        Set<String> allItems = collectCatalogFacetValues(sorted, facets -> facets.items());
        widgets.add(createTagFilterDropdown(allTags));
        widgets.add(createItemFilterDropdown(allItems));
        widgets.add(createSpacer(4));

        JsonArray cardWidgets = new JsonArray();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) cardWidgets.add(createSpacer(2));
            cardWidgets.add(createEnchantmentCard(sorted.get(i)));
        }

        JsonObject scrollPanel = new JsonObject();
        scrollPanel.addProperty("type", "scroll_panel");
        scrollPanel.addProperty("id", "catalog_list");
        scrollPanel.addProperty("flex", true);
        scrollPanel.addProperty("searchState", "catalog_search");
        scrollPanel.addProperty("emptyText", "No enchantments match this search/filter.");
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

    private static JsonObject createSpacer(int height) {
        JsonObject spacer = new JsonObject();
        spacer.addProperty("type", "spacer");
        spacer.addProperty("height", height);
        return spacer;
    }

    private static JsonObject createSearchBar() {
        JsonObject search = new JsonObject();
        search.addProperty("type", "search_box");
        search.addProperty("id", "catalog_search");
        search.addProperty("placeholder", "Search name, trigger, item, effect, or tag...");
        search.addProperty("prefix", "");
        search.addProperty("height", 18);
        search.addProperty("background", "0xFF101422");
        search.addProperty("focusBackground", "0xFF121A2E");
        search.addProperty("outline", "0x664A6A94");
        search.addProperty("focusOutline", "0xFF6CA4D4");
        search.addProperty("textColor", "0xFFE8EEF7");
        search.addProperty("placeholderColor", "0xFF7C8AA8");
        search.addProperty("iconColor", "0xFF86A8D1");
        return search;
    }

    private static JsonObject createTagFilterDropdown(Set<String> tags) {
        JsonObject dropdown = new JsonObject();
        dropdown.addProperty("type", "multi_select_dropdown");
        dropdown.addProperty("id", "catalog_tag_filter");
        dropdown.addProperty("stateId", "catalog_search");
        dropdown.addProperty("label", "Tags");
        dropdown.addProperty("rowGroup", "catalog_filters");
        dropdown.addProperty("widthPercent", 0.49);
        dropdown.addProperty("align", "left");
        dropdown.addProperty("height", 18);
        dropdown.addProperty("optionHeight", 14);
        dropdown.addProperty("maxVisible", 9);
        dropdown.addProperty("buttonBg", "0xFF101828");
        dropdown.addProperty("buttonHover", "0xFF16253A");
        dropdown.addProperty("buttonOutline", "0x663A5A80");
        dropdown.addProperty("buttonHoverOutline", "0xFF4A86C8");
        dropdown.addProperty("optionBg", "0xFF101828");
        dropdown.addProperty("optionHover", "0xFF1A2D46");
        dropdown.addProperty("optionActive", "0xFF21456A");
        dropdown.addProperty("optionOutline", "0x553A5A80");

        JsonArray tokenDefs = new JsonArray();
        for (String tag : tags) {
            JsonObject tok = new JsonObject();
            tok.addProperty("label", tag);
            tok.addProperty("token", "tag:" + tag);
            tokenDefs.add(tok);
        }
        dropdown.add("options", tokenDefs);
        return dropdown;
    }

    private static JsonObject createItemFilterDropdown(Set<String> items) {
        JsonObject dropdown = new JsonObject();
        dropdown.addProperty("type", "multi_select_dropdown");
        dropdown.addProperty("id", "catalog_item_filter");
        dropdown.addProperty("stateId", "catalog_search");
        dropdown.addProperty("label", "Items");
        dropdown.addProperty("rowGroup", "catalog_filters");
        dropdown.addProperty("widthPercent", 0.49);
        dropdown.addProperty("align", "right");
        dropdown.addProperty("height", 18);
        dropdown.addProperty("optionHeight", 14);
        dropdown.addProperty("maxVisible", 9);
        dropdown.addProperty("buttonBg", "0xFF101828");
        dropdown.addProperty("buttonHover", "0xFF16253A");
        dropdown.addProperty("buttonOutline", "0x663A5A80");
        dropdown.addProperty("buttonHoverOutline", "0xFF4A86C8");
        dropdown.addProperty("optionBg", "0xFF101828");
        dropdown.addProperty("optionHover", "0xFF1A2D46");
        dropdown.addProperty("optionActive", "0xFF21456A");
        dropdown.addProperty("optionOutline", "0x553A5A80");

        JsonArray optionDefs = new JsonArray();
        for (String item : items) {
            JsonObject opt = new JsonObject();
            opt.addProperty("label", item);
            opt.addProperty("token", "item:" + item.toLowerCase(Locale.ROOT));
            optionDefs.add(opt);
        }
        dropdown.add("options", optionDefs);
        return dropdown;
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

        CatalogFacets facets = CatalogFacets.fromEnchant(enchant);

        // Header label: name + level range
        card.addProperty("label", enchant.getDisplayName() +
            " (I-" + CustomEnchant.toRoman(enchant.getMaxLevel()) + ")");

        JsonArray tagArr = new JsonArray();
        for (String tag : facets.tags()) {
            tagArr.add(tag);
        }
        card.add("tags", tagArr);

        JsonArray itemArr = new JsonArray();
        for (String item : facets.items()) {
            itemArr.add(item);
        }
        card.add("items", itemArr);
        JsonObject catalog = new JsonObject();
        JsonObject facetObj = new JsonObject();
        facetObj.add("tags", toArray(facets.tags()));
        facetObj.add("items", toArray(facets.items()));
        facetObj.add("triggers", toArray(facets.triggers()));
        facetObj.add("effectKinds", toArray(facets.effectKinds()));
        facetObj.add("slotGroups", toArray(facets.slotGroups()));
        facetObj.add("cooldownClasses", toArray(facets.cooldownClasses()));
        facetObj.add("flags", toArray(facets.flags()));
        catalog.add("facets", facetObj);
        catalog.addProperty("searchText", facets.searchText());
        card.add("catalog", catalog);

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
        children.add(hintRow("Duration", enchant.getDurationLabel()));
        children.add(hintRow("Cooldown", enchant.getCooldownLabel()));

        if (enchant.getDescription() != null && !enchant.getDescription().isBlank()) {
            JsonObject desc = new JsonObject();
            desc.addProperty("type", "hint");
            desc.addProperty("wrap", true);
            desc.addProperty("lineHeight", 10);
            desc.addProperty("text", enchant.resolveDescription(1));
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

    private static Set<String> collectCatalogFacetValues(List<CustomEnchant> enchants,
                                                         java.util.function.Function<CatalogFacets, List<String>> extractor) {
        Set<String> values = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (CustomEnchant enchant : enchants) {
            values.addAll(extractor.apply(CatalogFacets.fromEnchant(enchant)));
        }
        return values;
    }

    private static JsonArray toArray(List<String> values) {
        JsonArray arr = new JsonArray();
        for (String value : values) {
            arr.add(value);
        }
        return arr;
    }

    /**
     * Get a human-readable label for the trigger type
     */
    private static String triggerLabel(EnchantTrigger trigger) {
        return switch (trigger.id()) {
            case "on_right_click" -> "Right Click";
            case "on_equip" -> "On Equip";
            case "on_deal_damage" -> "On Deal Damage";
            case "on_damage_taken" -> "On Damage Taken";
            case "on_kill_entity" -> "On Kill";
            case "on_suit_jump" -> "On Jump (Suit)";
            case "stat_threshold" -> "Stat Threshold";
            default -> trigger.id();
        };
    }
}
