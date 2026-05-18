package com.example.enchantforge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class EnchantCommand implements CommandExecutor, TabCompleter {

    private final EnchantForge plugin;

    public EnchantCommand(EnchantForge plugin) {
        this.plugin = plugin;
    }

    private EnchantmentRegistry registry() { return plugin.getRegistry(); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /cenchant <enchantment|reload> [level]", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("enchantforge.cenchant")) {
                sender.sendMessage(Component.text("You don't have permission to reload.", NamedTextColor.RED));
                return true;
            }
            plugin.reload(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("catalog")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use this command.");
                return true;
            }
            plugin.getUiBridge().openEnchantCatalog(player, registry());
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        CustomEnchant enchant = findEnchant(args[0]);
        if (enchant == null) {
            player.sendMessage(Component.text("Unknown enchantment: " + args[0], NamedTextColor.RED));
            return true;
        }

        int level = 1;
        if (args.length > 1) {
            try {
                level = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text(
                        "Usage: /cenchant " + args[0] + " [1-" + enchant.getMaxLevel() + "]", NamedTextColor.RED));
                return true;
            }
            if (level < 1 || level > enchant.getMaxLevel()) {
                player.sendMessage(Component.text(
                        "Level must be between 1 and " + enchant.getMaxLevel() + ".", NamedTextColor.RED));
                return true;
            }
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!enchant.canApplyTo(item.getType())) {
            player.sendMessage(Component.text(
                    "This enchantment cannot be applied to that item.", NamedTextColor.RED));
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(enchant.getKey(), PersistentDataType.INTEGER, level);

        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        removeEnchantLore(lore, enchant);
        lore.addAll(enchant.buildLore(level));

        meta.lore(lore);
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        item.setItemMeta(meta);

        player.sendMessage(Component.text(
                "Applied " + enchant.getDisplayName() + " " + CustomEnchant.toRoman(level) + "!",
                NamedTextColor.GREEN));

        // Update index and apply effect if the item is currently equipped in an armor slot
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack slotItem = player.getInventory().getItem(slot);
            if (slotItem != null && slotItem.equals(item)) {
                plugin.getEnchantIndex().updateSlot(player, slot, item, plugin.getRegistry());
                enchant.apply(player, level);
                break;
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> options = new ArrayList<>();
            options.add("reload");
            options.add("catalog");
            registry().getAll().stream()
                    .map(e -> e.getKey().getKey())
                    .filter(k -> k.startsWith(partial))
                    .forEach(options::add);
            return options.stream().filter(o -> o.startsWith(partial)).toList();
        }
        if (args.length == 2) {
            CustomEnchant enchant = findEnchant(args[0]);
            if (enchant != null) {
                List<String> levels = new ArrayList<>();
                for (int i = 1; i <= enchant.getMaxLevel(); i++) levels.add(String.valueOf(i));
                return levels;
            }
        }
        return List.of();
    }

    private CustomEnchant findEnchant(String key) {
        return registry().getAll().stream()
                .filter(e -> e.getKey().getKey().equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
    }

    private void removeEnchantLore(List<Component> lore, CustomEnchant enchant) {
        for (int i = 0; i < lore.size(); i++) {
            String plain = PlainTextComponentSerializer.plainText().serialize(lore.get(i));
            if (plain.startsWith(enchant.getDisplayName())) {
                int end = Math.min(i + enchant.loreLineCount(), lore.size());
                lore.subList(i, end).clear();
                return;
            }
        }
    }
}
