package com.example.enchantforge.trigger;

import com.example.enchantforge.ArmorTriggerSpec;
import com.example.enchantforge.WeaponTriggerSpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class EnchantTriggerTypeRegistry {

    private static final Map<String, Function<ConfigurationSection, EnchantTrigger>> FACTORIES  = new HashMap<>();
    private static final List<WeaponTriggerSpec<?>> WEAPON_SPECS = new ArrayList<>();
    private static final List<ArmorTriggerSpec<?>>  ARMOR_SPECS  = new ArrayList<>();

    static {
        register("on_equip",        section -> new OnEquipTrigger());
        register("on_damage_taken", section -> new OnDamageTakenTrigger());
        register("stat_threshold",  StatThresholdTrigger::fromYaml);
        register("on_right_click",  section -> new OnRightClickTrigger());
        register("on_suit_jump",    section -> new OnSuitJumpTrigger());
        register("on_deal_damage",  section -> new OnDealDamageTrigger(), OnDealDamageTrigger.SPEC);
        register("on_kill_entity",  section -> new OnKillEntityTrigger(), OnKillEntityTrigger.SPEC);
    }

    private EnchantTriggerTypeRegistry() {}

    // ---- Registration ----

    public static void register(String type, Function<ConfigurationSection, EnchantTrigger> factory) {
        if (type == null || type.isBlank() || factory == null)
            throw new IllegalArgumentException("Trigger type and factory must be provided");
        FACTORIES.put(type.toLowerCase(), factory);
    }

    public static <E extends Event> void register(String type,
                                                   Function<ConfigurationSection, EnchantTrigger> factory,
                                                   WeaponTriggerSpec<E> spec) {
        register(type, factory);
        WEAPON_SPECS.add(spec);
    }

    public static <E extends Event> void register(String type,
                                                   Function<ConfigurationSection, EnchantTrigger> factory,
                                                   ArmorTriggerSpec<E> spec) {
        register(type, factory);
        ARMOR_SPECS.add(spec);
    }

    // ---- Spec accessors (used by EnchantEventRouter at startup) ----

    public static List<WeaponTriggerSpec<?>> weaponSpecs() {
        return Collections.unmodifiableList(WEAPON_SPECS);
    }

    public static List<ArmorTriggerSpec<?>> armorSpecs() {
        return Collections.unmodifiableList(ARMOR_SPECS);
    }

    // ---- YAML factory ----

    public static EnchantTrigger fromYaml(ConfigurationSection section) {
        if (section == null) throw new IllegalArgumentException("Missing trigger section");
        String type = section.getString("type", "").toLowerCase();
        Function<ConfigurationSection, EnchantTrigger> factory = FACTORIES.get(type);
        if (factory == null) throw new IllegalArgumentException("Unknown trigger type: " + type);
        return factory.apply(section);
    }
}
