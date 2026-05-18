package com.example.enchantforge;

import com.example.enchantforge.effect.HandLaserEffect;
import com.example.enchantforge.effect.MorphFormEffect;
import com.example.enchantforge.effect.PlayerResourcePool;
import com.example.enchantforge.effect.ThrusterEffect;
import com.example.enchantforge.effect.WolfFormEffect;
import com.example.enchantforge.trigger.EnchantTriggerTypeRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class EnchantForge extends JavaPlugin {

    private EnchantmentRegistry registry;
    private CooldownManager cooldowns;
    private ActiveEffectTracker tracker;
    private CombatTracker combatTracker;
    private PlayerEnchantIndex enchantIndex;
    private EquipmentEnchantListener equipmentListener;
    private ResourcePackManager resourcePackManager;
    private VibeCraftUiBridge uiBridge;
    private EnchantForgeModInputListener modInputListener;
    private PlayerResourcePool energy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("ui/main.json", false);
        EnchantDebug.init(getLogger());
        CooldownVisuals.init(this);
        VisibilityUtil.init(this);
        WolfFormEffect.init(this);
        MorphFormEffect.init(this);

        registry = new EnchantmentRegistry();
        cooldowns = new CooldownManager();
        tracker = new ActiveEffectTracker();
        combatTracker = new CombatTracker();
        enchantIndex = new PlayerEnchantIndex();
        energy = new PlayerResourcePool(100.0, 0.4);
        ThrusterEffect.init(energy);
        HandLaserEffect.init(energy);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "vibecraft:events");
        uiBridge = new VibeCraftUiBridge(this);
        modInputListener = new EnchantForgeModInputListener(registry, uiBridge);
        getServer().getMessenger().registerIncomingPluginChannel(this, "vibecraft:input", modInputListener);

        resourcePackManager = new ResourcePackManager(this);
        resourcePackManager.generatePack();

        saveDefaultEnchants();
        loadEnchantments();
        cooldowns.load(new File(getDataFolder(), "cooldowns.yml"));

        equipmentListener = new EquipmentEnchantListener(registry, cooldowns, tracker, combatTracker, enchantIndex);
        equipmentListener.startReapplyTicker(this);
        getServer().getPluginManager().registerEvents(equipmentListener, this);
        getServer().getPluginManager().registerEvents(
                new DamageTakenListener(registry, cooldowns, tracker, combatTracker, this, enchantIndex), this);

        PlayerLifecycleRegistry lifecycle = new PlayerLifecycleRegistry();
        lifecycle.onJoin(resourcePackManager::sendPackTo);
        lifecycle.onJoin(p -> {
            String modUrl = getConfig().getString("vibecraft-mod.url", "");
            if (modUrl == null || modUrl.isBlank()) return;
            getServer().getScheduler().runTaskLater(this, () -> {
                if (!p.isOnline()) return;
                if (p.getListeningPluginChannels().contains("vibecraft:events")) return;
                sendModRecommendation(p, modUrl);
            }, 40L);
        });
        lifecycle.onQuit(p -> {
            java.util.UUID id = p.getUniqueId();
            cooldowns.clearPlayer(id);
            tracker.clearPlayer(id);
            combatTracker.clearPlayer(id);
            enchantIndex.clearPlayer(id);
            energy.cleanup(id);
        });
        getServer().getPluginManager().registerEvents(lifecycle, this);

        getServer().getPluginManager().registerEvents(
            new EnchantCatalogListener(registry, uiBridge), this);
        EnchantEventRouter enchantRouter = new EnchantEventRouter(registry, cooldowns, enchantIndex);
        EnchantTriggerTypeRegistry.weaponSpecs().forEach(enchantRouter::register);
        EnchantTriggerTypeRegistry.armorSpecs().forEach(enchantRouter::register);
        getServer().getPluginManager().registerEvents(enchantRouter, this);
        getServer().getPluginManager().registerEvents(
                new RightClickListener(registry, cooldowns, this, enchantIndex), this);
        getServer().getPluginManager().registerEvents(new SuitListener(this, enchantIndex, energy), this);

        EnchantCommand cmd = new EnchantCommand(this);
        getCommand("cenchant").setExecutor(cmd);
        getCommand("cenchant").setTabCompleter(cmd);

        getCommand("ctestcooldown").setExecutor(new CooldownTestCommand());

        getLogger().info("Loaded " + registry.getAll().size() + " enchantment(s).");
        getLogger().info("EnchantForge enabled!");
    }

    @Override
    public void onDisable() {
        cooldowns.save(new File(getDataFolder(), "cooldowns.yml"));
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "vibecraft:events");
        getServer().getMessenger().unregisterIncomingPluginChannel(this, "vibecraft:input");
        if (resourcePackManager != null) resourcePackManager.stopHttpServer();
        getLogger().info("EnchantForge disabled!");
    }

    public void reload(CommandSender sender) {
        cooldowns.save(new File(getDataFolder(), "cooldowns.yml"));
        reloadConfig();
        resourcePackManager.reloadSettings();

        for (Player player : getServer().getOnlinePlayers()) {
            for (CustomEnchant enchant : registry.getAll()) enchant.remove(player);
            tracker.clearPlayer(player.getUniqueId());
            cooldowns.clearPlayer(player.getUniqueId());
            enchantIndex.clearPlayer(player.getUniqueId());
            energy.cleanup(player.getUniqueId());
        }

        registry.clear();
        loadEnchantments();
        cooldowns.load(new File(getDataFolder(), "cooldowns.yml"));

        for (Player player : getServer().getOnlinePlayers()) {
            equipmentListener.refreshPlayer(player);
        }

        sender.sendMessage("Reloaded " + registry.getAll().size() + " enchantment(s).");
        getLogger().info("Config reloaded by " + sender.getName() + " — "
                + registry.getAll().size() + " enchantment(s).");
    }

    public EnchantmentRegistry getRegistry()   { return registry; }
    public VibeCraftUiBridge getUiBridge()     { return uiBridge; }
    public PlayerEnchantIndex getEnchantIndex() { return enchantIndex; }

    // -------------------------------------------------------------------------

    private void saveDefaultEnchants() {
        new File(getDataFolder(), "enchants").mkdirs();
        for (String name : new String[]{"berserker.yml", "vitality.yml", "bulwark.yml",
                                        "iron_skin.yml", "swift_steps.yml", "reactive_guard.yml", "last_stand.yml",
                                        "steadfast.yml", "soulfeast.yml", "vampiric.yml",
                                        "shadow_veil.yml", "feral_form.yml", "eye_laser.yml",
                                        "thruster_boots.yml", "hand_laser.yml",
                                        "ai_interface.yml"}) {
            try {
                saveResource("enchants/" + name, false);
            } catch (IllegalArgumentException ignored) {
                // Not bundled in this jar — skip silently
            }
        }
    }

    private void loadEnchantments() {
        File dir = new File(getDataFolder(), "enchants");
        if (!dir.exists()) {
            dir.mkdirs();
            return;
        }
        File[] files = dir.listFiles(f -> f.getName().endsWith(".yml") || f.getName().endsWith(".yaml"));
        if (files == null) return;

        for (File file : files) {
            String defaultKey = file.getName().replaceAll("\\.(yml|yaml)$", "");
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String keyStr = yaml.getString("key", defaultKey);
                NamespacedKey key = new NamespacedKey(this, keyStr);
                registry.register(CustomEnchant.fromYaml(key, yaml));
            } catch (Exception e) {
                getLogger().severe("Failed to load enchant from " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private void sendModRecommendation(Player player, String url) {
        Component msg = Component.text()
            .append(Component.text("[EnchantForge] ").color(NamedTextColor.GOLD))
            .append(Component.text("This server uses ").color(NamedTextColor.YELLOW))
            .append(Component.text("VibeCraftMod")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD))
            .append(Component.text(" for an enhanced HUD and enchantment UI. ").color(NamedTextColor.YELLOW))
            .append(Component.text("[Get it here]")
                .color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(
                    Component.text("Click to open download page").color(NamedTextColor.GRAY))))
            .build();
        player.sendMessage(msg);
    }
}
