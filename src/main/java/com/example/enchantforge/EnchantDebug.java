package com.example.enchantforge;

import org.bukkit.entity.Player;

import java.util.logging.Logger;

public final class EnchantDebug {

    private static Logger logger;

    private EnchantDebug() {}

    static void init(Logger l) {
        logger = l;
    }

    public static void log(CustomEnchant enchant, Player player, String event) {
        if (!enchant.isDebug() || logger == null) return;
        logger.info("[debug|" + enchant.getKey().getKey() + "] " + player.getName() + " — " + event);
    }

    public static void log(CustomEnchant enchant, String event) {
        if (!enchant.isDebug() || logger == null) return;
        logger.info("[debug|" + enchant.getKey().getKey() + "] " + event);
    }
}
