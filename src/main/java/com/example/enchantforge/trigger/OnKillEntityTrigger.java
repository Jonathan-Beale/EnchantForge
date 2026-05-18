package com.example.enchantforge.trigger;

import com.example.enchantforge.WeaponTriggerSpec;
import org.bukkit.event.entity.EntityDeathEvent;

public final class OnKillEntityTrigger extends EnchantTrigger {

    public static final WeaponTriggerSpec<EntityDeathEvent> SPEC = new WeaponTriggerSpec<>(
            "on_kill_entity",
            EntityDeathEvent.class,
            e -> e.getEntity().getKiller() != null,
            e -> e.getEntity().getKiller(),
            e -> e.getEntity().getKiller().getInventory().getItemInMainHand(),
            null
    );

    @Override
    public String id() { return "on_kill_entity"; }
}
