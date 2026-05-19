package com.example.enchantforge;

import com.example.enchantforge.effect.AttributeStyleEffect;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class CatalogFacets {

    private final List<String> tags;
    private final List<String> items;
    private final List<String> triggers;
    private final List<String> effectKinds;
    private final List<String> slotGroups;
    private final List<String> cooldownClasses;
    private final List<String> flags;
    private final String searchText;

    private CatalogFacets(List<String> tags, List<String> items, List<String> triggers,
                          List<String> effectKinds, List<String> slotGroups,
                          List<String> cooldownClasses, List<String> flags,
                          String searchText) {
        this.tags = List.copyOf(tags);
        this.items = List.copyOf(items);
        this.triggers = List.copyOf(triggers);
        this.effectKinds = List.copyOf(effectKinds);
        this.slotGroups = List.copyOf(slotGroups);
        this.cooldownClasses = List.copyOf(cooldownClasses);
        this.flags = List.copyOf(flags);
        this.searchText = searchText;
    }

    public static CatalogFacets fromEnchant(CustomEnchant enchant) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        LinkedHashSet<String> items = new LinkedHashSet<>();
        LinkedHashSet<String> triggers = new LinkedHashSet<>();
        LinkedHashSet<String> effectKinds = new LinkedHashSet<>();
        LinkedHashSet<String> slotGroups = new LinkedHashSet<>();
        LinkedHashSet<String> cooldownClasses = new LinkedHashSet<>();
        LinkedHashSet<String> flags = new LinkedHashSet<>();

        String triggerKey = enchant.getTrigger().id();
        triggers.add(triggerKey);

        if (triggerKey.equals("on_equip")) {
            tags.add("passive");
        } else if (triggerKey.equals("on_damage_taken")) {
            tags.add("reactive");
        } else if (triggerKey.equals("on_right_click")) {
            tags.add("active");
        } else if (triggerKey.equals("on_kill_entity")) {
            tags.add("execute");
        } else if (triggerKey.equals("stat_threshold")) {
            tags.add("threshold");
        } else {
            tags.add("conditional");
        }

        if (triggerKey.equals("on_deal_damage") || triggerKey.equals("on_kill_entity")) tags.add("offense");
        if (triggerKey.equals("on_damage_taken") || triggerKey.equals("on_equip")) tags.add("defense");
        if (triggerKey.equals("on_right_click") || triggerKey.equals("on_equip")) tags.add("utility");

        if (enchant.getCooldownTicks() > 0) {
            cooldownClasses.add("cooldown");
            tags.add("cooldown");
        } else {
            cooldownClasses.add("instant");
            tags.add("instant");
        }

        String effectKind = enchant.getEffect().id();
        effectKinds.add(effectKind);
        if (enchant.getEffect() instanceof AttributeStyleEffect attr && attr.getSlotGroup() != null) {
            slotGroups.add(attr.getSlotGroup().toString().toLowerCase(Locale.ROOT));
        }

        items.addAll(normalizeItems(enchant.getApplicableTo()));

        String rawText = (enchant.getKey().getKey() + " " + enchant.getDisplayName() + " "
                + (enchant.getDescription() == null ? "" : enchant.getDescription())).toLowerCase(Locale.ROOT);
        if (rawText.contains("heal") || rawText.contains("vamp") || rawText.contains("vital")
                || rawText.contains("guard") || rawText.contains("bulwark")) {
            tags.add("sustain");
        }
        if (rawText.contains("swift") || rawText.contains("shadow") || rawText.contains("feral")) {
            tags.add("mobility");
        }
        if (enchant.isDebug()) {
            tags.add("debug");
            flags.add("debug");
        }

        CatalogMetadata metadata = enchant.getCatalogMetadata();
        List<String> normalizedTags = metadata.tags().isEmpty() ? List.copyOf(tags) : normalizeValues(metadata.tags());
        List<String> normalizedItems = metadata.items().isEmpty() ? List.copyOf(items) : normalizeItems(metadata.items());

        StringBuilder search = new StringBuilder();
        search.append(enchant.getKey().getKey()).append(' ')
                .append(enchant.getDisplayName()).append(' ')
                .append(triggerKey).append(' ')
                .append(effectKind).append(' ')
                .append(enchant.getStackBehavior().name()).append(' ')
                .append(enchant.getCooldownTicks()).append(" ticks ");
        if (enchant.getDescription() != null) search.append(enchant.getDescription()).append(' ');
        normalizedTags.forEach(tag -> search.append(tag).append(' '));
        normalizedItems.forEach(item -> search.append(item).append(' '));
        triggers.forEach(value -> search.append(value).append(' '));
        effectKinds.forEach(value -> search.append(value).append(' '));
        slotGroups.forEach(value -> search.append(value).append(' '));
        cooldownClasses.forEach(value -> search.append(value).append(' '));

        return new CatalogFacets(
                normalizedTags,
                normalizedItems,
                List.copyOf(triggers),
                List.copyOf(effectKinds),
                List.copyOf(slotGroups),
                List.copyOf(cooldownClasses),
                List.copyOf(flags),
                search.toString().trim().toLowerCase(Locale.ROOT)
        );
    }

    private static List<String> normalizeItems(List<String> rawItems) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (rawItems == null || rawItems.isEmpty()) {
            out.add("any");
            return List.copyOf(out);
        }

        for (String raw : rawItems) {
            if (raw == null || raw.isBlank()) continue;
            String value = raw.toLowerCase(Locale.ROOT).trim();
            if (value.equals("armor")) {
                out.add("armor");
                out.add("helmet");
                out.add("chestplate");
                out.add("leggings");
                out.add("boots");
                continue;
            }
            if (value.contains("helmet")) {
                out.add("helmet");
                out.add("armor");
                continue;
            }
            if (value.contains("chestplate")) {
                out.add("chestplate");
                out.add("armor");
                continue;
            }
            if (value.contains("leggings")) {
                out.add("leggings");
                out.add("armor");
                continue;
            }
            if (value.contains("boots")) {
                out.add("boots");
                out.add("armor");
                continue;
            }
            if (value.contains("sword")) {
                out.add("sword");
                continue;
            }
            if (value.contains("axe")) {
                out.add("axe");
                continue;
            }
            if (value.contains("mace")) {
                out.add("mace");
                continue;
            }
            if (value.contains("bow")) {
                out.add("bow");
                continue;
            }
            if (value.contains("crossbow")) {
                out.add("crossbow");
                continue;
            }
            if (value.contains("trident")) {
                out.add("trident");
                continue;
            }
            if (value.contains("pickaxe")) {
                out.add("pickaxe");
                continue;
            }
            if (value.contains("shovel")) {
                out.add("shovel");
                continue;
            }
            if (value.contains("hoe")) {
                out.add("hoe");
                continue;
            }
            out.add(value);
        }
        return List.copyOf(out);
    }

    private static List<String> normalizeValues(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            normalized.add(value.toLowerCase(Locale.ROOT).trim());
        }
        return List.copyOf(normalized);
    }

    public List<String> tags() { return tags; }
    public List<String> items() { return items; }
    public List<String> triggers() { return triggers; }
    public List<String> effectKinds() { return effectKinds; }
    public List<String> slotGroups() { return slotGroups; }
    public List<String> cooldownClasses() { return cooldownClasses; }
    public List<String> flags() { return flags; }
    public String searchText() { return searchText; }
}