package com.example.enchantforge;

import com.example.enchantforge.effect.EnergyManager;
import com.example.enchantforge.effect.MorphFormEffect;
import com.example.enchantforge.effect.WolfFormEffect;
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
    private EquipmentEnchantListener equipmentListener;
    private ResourcePackManager resourcePackManager;
    private VibeCraftUiBridge uiBridge;
    private EnchantForgeModInputListener modInputListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("ui/main.json", false);
        EnchantDebug.init(getLogger());
        CooldownVisuals.init(this);
        VisibilityUtil.init(this);
        WolfFormEffect.init(this);
        MorphFormEffect.init(this);
        EnergyManager.init(this);

        registry = new EnchantmentRegistry();
        cooldowns = new CooldownManager();
        tracker = new ActiveEffectTracker();
        combatTracker = new CombatTracker();

        getServer().getMessenger().registerOutgoingPluginChannel(this, "vibecraft:events");
        uiBridge = new VibeCraftUiBridge(this);
        modInputListener = new EnchantForgeModInputListener(registry, uiBridge);
        getServer().getMessenger().registerIncomingPluginChannel(this, "vibecraft:input", modInputListener);

        resourcePackManager = new ResourcePackManager(this);
        resourcePackManager.generatePack();

        saveDefaultEnchants();
        loadEnchantments();

        equipmentListener = new EquipmentEnchantListener(registry, cooldowns, tracker, combatTracker);
        equipmentListener.startReapplyTicker(this);
        getServer().getPluginManager().registerEvents(equipmentListener, this);
        getServer().getPluginManager().registerEvents(
                new DamageTakenListener(registry, cooldowns, tracker, combatTracker, this), this);
        getServer().getPluginManager().registerEvents(
                new PlayerSessionListener(cooldowns, tracker, combatTracker, resourcePackManager), this);
        getServer().getPluginManager().registerEvents(
            new EnchantCatalogListener(registry, uiBridge), this);
        getServer().getPluginManager().registerEvents(
                new EntityKillListener(registry, cooldowns), this);
        getServer().getPluginManager().registerEvents(
                new DamageDealtListener(registry, cooldowns), this);
        getServer().getPluginManager().registerEvents(
                new RightClickListener(registry, cooldowns, this), this);
        getServer().getPluginManager().registerEvents(new SuitListener(registry, this), this);

        EnchantCommand cmd = new EnchantCommand(this);
        getCommand("cenchant").setExecutor(cmd);
        getCommand("cenchant").setTabCompleter(cmd);

        getCommand("ctestcooldown").setExecutor(new CooldownTestCommand());

        getLogger().info("Loaded " + registry.getAll().size() + " enchantment(s).");
        getLogger().info("EnchantForge enabled!");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "vibecraft:events");
        getServer().getMessenger().unregisterIncomingPluginChannel(this, "vibecraft:input");
        if (resourcePackManager != null) resourcePackManager.stopHttpServer();
        getLogger().info("EnchantForge disabled!");
    }

    public void reload(CommandSender sender) {
        reloadConfig();
        resourcePackManager.reloadSettings();

        for (Player player : getServer().getOnlinePlayers()) {
            for (CustomEnchant enchant : registry.getAll()) enchant.remove(player);
            tracker.clearPlayer(player.getUniqueId());
            cooldowns.clearPlayer(player.getUniqueId());
        }

        registry.clear();
        loadEnchantments();

        for (Player player : getServer().getOnlinePlayers()) {
            equipmentListener.refreshPlayer(player);
        }

        sender.sendMessage("Reloaded " + registry.getAll().size() + " enchantment(s).");
        getLogger().info("Config reloaded by " + sender.getName() + " — "
                + registry.getAll().size() + " enchantment(s).");
    }

    public EnchantmentRegistry getRegistry() { return registry; }
    public VibeCraftUiBridge getUiBridge()     { return uiBridge; }

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
                getLogger().warning("Failed to load " + file.getName() + ": " + e.getMessage());
            }
        }
    }
}
