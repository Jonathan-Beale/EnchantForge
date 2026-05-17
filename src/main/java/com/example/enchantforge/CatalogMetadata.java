package com.example.enchantforge;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public final class CatalogMetadata {

    private static final CatalogMetadata EMPTY = new CatalogMetadata(List.of(), List.of(), false, 0);

    private final List<String> tags;
    private final List<String> items;
    private final boolean hidden;
    private final int order;

    private CatalogMetadata(List<String> tags, List<String> items, boolean hidden, int order) {
        this.tags = List.copyOf(tags);
        this.items = List.copyOf(items);
        this.hidden = hidden;
        this.order = order;
    }

    public static CatalogMetadata empty() {
        return EMPTY;
    }

    public static CatalogMetadata fromYaml(ConfigurationSection section) {
        if (section == null) return EMPTY;
        List<String> tags = section.getStringList("tags");
        List<String> items = section.getStringList("items");
        boolean hidden = section.getBoolean("hidden", false);
        int order = section.getInt("order", 0);
        return new CatalogMetadata(tags, items, hidden, order);
    }

    public List<String> tags() {
        return tags;
    }

    public List<String> items() {
        return items;
    }

    public boolean hidden() {
        return hidden;
    }

    public int order() {
        return order;
    }
}