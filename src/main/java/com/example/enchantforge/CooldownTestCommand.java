package com.example.enchantforge;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static org.bukkit.ChatColor.RED;

public class CooldownTestCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        if (player.getInventory().getItemInMainHand() == null || player.getInventory().getItemInMainHand().getType().isAir()) {
            sender.sendMessage(RED + "Hold an item in your main hand first.");
            return true;
        }

        CooldownVisuals.applyMainHandCooldown(player, 100);
        sender.sendMessage("Applied cooldown to held item for 5 seconds.");
        return true;
    }
}
