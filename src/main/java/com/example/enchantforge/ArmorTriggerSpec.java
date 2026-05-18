package com.example.enchantforge;

import com.example.enchantforge.effect.EnchantEffectContext;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Declarative specification for an armor-based enchant trigger.
 * Parallel to {@link WeaponTriggerSpec}, but dispatch sources enchants from the equipped-armor
 * index ({@link PlayerEnchantIndex}) rather than a held weapon's PDC.
 *
 * @param triggerId       matches {@link com.example.enchantforge.trigger.EnchantTrigger#id()}
 * @param eventClass      the Bukkit event this trigger fires on
 * @param guard           pre-check; if false the event is skipped entirely for this trigger
 * @param playerExtractor extracts the player from the event
 * @param contextBuilder  builds an {@link EnchantEffectContext} from the event, or null if none
 */
public record ArmorTriggerSpec<E extends Event>(
        String triggerId,
        Class<E> eventClass,
        Predicate<E> guard,
        Function<E, Player> playerExtractor,
        Function<E, EnchantEffectContext> contextBuilder
) {}
