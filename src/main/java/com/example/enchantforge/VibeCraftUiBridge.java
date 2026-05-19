package com.example.enchantforge;

import com.example.enchantforge.effect.EnchantEffectTypeRegistry;
import com.example.enchantforge.trigger.EnchantTriggerTypeRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Sends UI events to the VibeCraft mod channel without compile-time dependency
 * on the VibeCraft plugin.
 *
 * Uses schema-driven UI: schemas are built from enchantment data at call time.
 * New enchantments are automatically included without code changes.
 */
public final class VibeCraftUiBridge {

    private final JavaPlugin plugin;
    private static final boolean ENCHANTFORGE_CATALOG_UI_ENABLED = true;

    public VibeCraftUiBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Enchantment Catalog ----

    public void openEnchantCatalog(Player player, EnchantmentRegistry registry) {
        if (!ENCHANTFORGE_CATALOG_UI_ENABLED) {
            sendInfo(player, "EnchantForge catalog UI is temporarily disabled.");
            return;
        }
        JsonObject catalogSchema = CatalogSchemaGenerator.generateCatalogSchema(registry);
        sendSchema(player, catalogSchema);
        sendEvent(player, buildOpenScreenEvent("enchantforge:catalog").toString());
    }

    // ---- Product Guide ----

    public void openProductGuide(Player player, EnchantmentRegistry registry) {
        JsonArray sections = new JsonArray();

        // Triggers
        JsonObject triggers = buildCollapsible("Triggers \u2014 when an enchant activates", false, "0xFF1A2A3A");
        JsonArray tRows = new JsonArray();
        tRows.add(buildHint("While worn \u2014 active whenever the item is equipped; removed on unequip"));
        tRows.add(buildHint("On hit taken \u2014 fires each time you take damage (subject to cooldown)"));
        tRows.add(buildHint("Low health \u2014 fires when health crosses a threshold (e.g. below 3 hearts)"));
        tRows.add(buildHint("  Configurable: any stat, above or below, any value"));
        tRows.add(buildHint("Right-click \u2014 fires when you right-click while holding the item"));
        tRows.add(buildHint("On damage dealt \u2014 fires each time you deal damage with the weapon"));
        tRows.add(buildHint("On kill \u2014 fires when you kill an entity with the weapon"));
        tRows.add(buildHint("Suit jump \u2014 fires when you jump while wearing a full enchanted suit"));
        triggers.add("widgets", tRows);
        sections.add(triggers);
        sections.add(buildSpacer(3));

        // Effects
        JsonObject effects = buildCollapsible("Effects \u2014 what an enchant can do", false, "0xFF1A2A3A");
        JsonArray eRows = new JsonArray();
        eRows.add(buildHint("Stat boost \u2014 modify movement speed, max health, armor, attack damage, or any other attribute. Amount and scaling configurable per level."));
        eRows.add(buildSpacer(2));
        eRows.add(buildHint("Potion effect \u2014 apply any vanilla potion: resistance, speed, strength, regeneration, fire resistance, and more. Amplifier scales with level."));
        eRows.add(buildSpacer(2));
        eRows.add(buildHint("Absorption hearts \u2014 grant extra golden hearts (4 per level). Hearts are preserved on removal and regenerate out of combat."));
        eRows.add(buildSpacer(2));
        eRows.add(buildHint("Hand Laser \u2014 fire a particle beam from your hand. Damages the first entity in its path. Range and damage scale with level. Uses energy."));
        eRows.add(buildSpacer(2));
        eRows.add(buildHint("Thruster \u2014 launch yourself into the air. Thrust scales with level. Uses energy."));
        eRows.add(buildSpacer(2));
        eRows.add(buildHint("Morph Form \u2014 transform the player's appearance and apply a set of effects."));
        effects.add("widgets", eRows);
        sections.add(effects);
        sections.add(buildSpacer(3));

        // Durations
        JsonObject durations = buildCollapsible("Durations \u2014 how long effects last", false, "0xFF1A2A3A");
        JsonArray dRows = new JsonArray();
        dRows.add(buildHint("Permanent \u2014 lasts until the item is unequipped. No expiry."));
        dRows.add(buildHint("Timed \u2014 lasts a fixed number of seconds, then expires automatically."));
        dRows.add(buildHint("Until next hit \u2014 ends the moment you take any damage."));
        dRows.add(buildHint("Until full health or hit \u2014 ends at full health or on the next hit."));
        dRows.add(buildHint("Until absorption gone \u2014 ends when your absorption hearts are consumed."));
        durations.add("widgets", dRows);
        sections.add(durations);
        sections.add(buildSpacer(3));

        // Slots
        JsonObject slots = buildCollapsible("Slots \u2014 what items can carry an enchant", false, "0xFF1A2A3A");
        JsonArray sRows = new JsonArray();
        sRows.add(buildHint("Any armor \u2014 helmet, chestplate, leggings, or boots."));
        sRows.add(buildHint("Specific armor piece \u2014 target one slot only (e.g. boots only)."));
        sRows.add(buildHint("Weapon \u2014 swords, axes, or any specific weapon material."));
        sRows.add(buildHint("Any item \u2014 leave the slot list empty to allow any item."));
        slots.add("widgets", sRows);
        sections.add(slots);
        sections.add(buildSpacer(3));

        // Stacking
        JsonObject stacking = buildCollapsible("Stacking \u2014 multiple copies of the same enchant", false, "0xFF1A2A3A");
        JsonArray stRows = new JsonArray();
        stRows.add(buildHint("Highest \u2014 only the strongest copy counts. Extra pieces add nothing."));
        stRows.add(buildHint("Sum \u2014 all copies add together. Two level-1 pieces act as level 2."));
        stRows.add(buildHint("Exclusive \u2014 only the first piece found activates. Others are ignored."));
        stacking.add("widgets", stRows);
        sections.add(stacking);
        sections.add(buildSpacer(3));

        // Energy
        JsonObject energy = buildCollapsible("Energy \u2014 resource pool for active effects", false, "0xFF1A2A3A");
        JsonArray enRows = new JsonArray();
        enRows.add(buildHint("Hand Laser and Thruster draw from a shared energy pool."));
        enRows.add(buildHint("Pool capacity: 100. Regenerates at 8 per second passively."));
        enRows.add(buildHint("Regen pauses while in combat (5 seconds after the last hit taken)."));
        enRows.add(buildHint("If you activate an effect when empty, the action fails with a sound."));
        energy.add("widgets", enRows);
        sections.add(energy);

        JsonObject schema = buildScreenSchema("enchantforge:product_guide", "EnchantForge Guide", sections);
        sendSchema(player, schema);
        sendEvent(player, buildOpenScreenEvent("enchantforge:product_guide").toString());
    }

    // ---- AI Manual ----

    public void openAIManual(Player player, EnchantmentRegistry registry) {
        JsonArray sections = new JsonArray();

        // § Architecture
        JsonObject arch = buildCollapsible("\u00a7 Architecture", false, "0xFF1A2A1A");
        JsonArray archRows = new JsonArray();
        archRows.add(buildHint("Package: com.example.enchantforge"));
        archRows.add(buildHint("Entry point: EnchantForge.java"));
        archRows.add(buildHint("  onEnable(): wires all managers, listeners, registries"));
        archRows.add(buildHint("  onDisable(): saves cooldowns, stops HTTP server"));
        archRows.add(buildHint("  reload(): clears player state, re-runs loadEnchantments()"));
        archRows.add(buildSpacer(2));
        archRows.add(buildHint("Constructor-injected collaborators:"));
        archRows.add(buildHint("  EnchantmentRegistry, CooldownManager, ActiveEffectTracker,"));
        archRows.add(buildHint("  CombatTracker, PlayerEnchantIndex, PlayerResourcePool,"));
        archRows.add(buildHint("  VibeCraftUiBridge"));
        arch.add("widgets", archRows);
        sections.add(arch);

        // § Key Files
        JsonObject files = buildCollapsible("\u00a7 Key Files", false, "0xFF1A2A1A");
        JsonArray fileRows = new JsonArray();
        String[][] keyFiles = {
            {"EnchantForge.java",                       "Plugin entry; wires all components"},
            {"CustomEnchant.java",                      "Data object loaded from YAML"},
            {"EnchantmentRegistry.java",                "Holds all loaded enchants; reads PDC on ItemStacks"},
            {"CooldownManager.java",                    "Cooldown store; save/load to cooldowns.yml"},
            {"ActiveEffectTracker.java",                "player\u00d7enchant\u2192effectiveLevel for tracked effects"},
            {"CombatTracker.java",                      "player\u2192last-hit timestamp for OOC regen"},
            {"StackBehavior.java",                      "Enum: SUM / HIGHEST / EXCLUSIVE"},
            {"PlayerEnchantIndex.java",                 "Per-player slot\u2192enchant index; rebuilt on equip"},
            {"effect/PlayerResourcePool.java",          "Instance-based lazy-regen resource pool"},
            {"PlayerLifecycleRegistry.java",            "Declarative join/quit handler registry"},
            {"StackingDispatcher.java",                 "Static util: collect-stack-dispatch pattern"},
            {"EnchantEventRouter.java",                 "Registers weapon/armor specs; dispatches events"},
            {"DamageTakenListener.java",                "on_damage_taken, stat_threshold, end-conditions"},
            {"EquipmentEnchantListener.java",           "Armor equip/unequip; passive reapply; OOC regen"},
            {"RightClickListener.java",                 "on_right_click trigger dispatch"},
            {"SuitListener.java",                       "Suit jump triggers; energy HUD binding updates"},
            {"CooldownVisuals.java",                    "Visual item cooldowns via player.setCooldown()"},
            {"ResourcePackManager.java",                "Generates & serves enchantforge_pack.zip"},
            {"VibeCraftUiBridge.java",                  "All outbound plugin-channel messages to mod"},
            {"CatalogSchemaGenerator.java",             "Dynamic schema for the technical catalog screen"},
            {"trigger/EnchantTriggerTypeRegistry.java", "Factory registry for trigger types"},
            {"effect/EnchantEffectTypeRegistry.java",   "Factory registry for effect styles"},
            {"condition/EndCondition.java",             "Interface + fromYaml() switch for duration types"},
        };
        for (String[] row : keyFiles) {
            fileRows.add(buildHint(row[0] + " \u2014 " + row[1]));
        }
        files.add("widgets", fileRows);
        sections.add(files);

        // § Trigger Types (dynamic)
        JsonObject triggers = buildCollapsible("\u00a7 Trigger Types (live registry)", false, "0xFF1A2A1A");
        JsonArray trigRows = new JsonArray();
        for (String id : new TreeSet<>(EnchantTriggerTypeRegistry.registeredIds())) {
            trigRows.add(buildHint(id));
        }
        triggers.add("widgets", trigRows);
        sections.add(triggers);

        // § Effect Types (dynamic)
        JsonObject effects = buildCollapsible("\u00a7 Effect Types (live registry)", false, "0xFF1A2A1A");
        JsonArray effRows = new JsonArray();
        for (String id : new TreeSet<>(EnchantEffectTypeRegistry.registeredIds())) {
            effRows.add(buildHint(id));
        }
        effects.add("widgets", effRows);
        sections.add(effects);

        // § End Conditions (static)
        JsonObject conditions = buildCollapsible("\u00a7 End Conditions", false, "0xFF1A2A1A");
        JsonArray condRows = new JsonArray();
        String[][] conds = {
            {"never",                               "NeverCondition \u2014 permanent; removed on unequip"},
            {"time (ticks:N)",                      "TimeCondition \u2014 scheduler-based expiry"},
            {"until_condition/damaged",             "DamagedCondition \u2014 removed on next hit"},
            {"until_condition/full_health_or_damaged", "FullHealthOrDamagedCondition"},
            {"until_condition/stat_threshold",      "StatThresholdCondition \u2014 stat/comparison/value"},
            {"until_condition/absorption_depleted", "AbsorptionDepletedCondition \u2014 deferred 1-tick check"},
        };
        for (String[] row : conds) {
            condRows.add(buildHint(row[0] + " \u2192 " + row[1]));
        }
        conditions.add("widgets", condRows);
        sections.add(conditions);

        // § Extension Points
        JsonObject ext = buildCollapsible("\u00a7 Extension Points", false, "0xFF1A2A1A");
        JsonArray extRows = new JsonArray();
        extRows.add(buildHint("Add trigger:"));
        extRows.add(buildHint("  1. Implement EnchantTrigger in trigger/"));
        extRows.add(buildHint("  2. Declare WeaponTriggerSpec<E> or ArmorTriggerSpec<E> SPEC"));
        extRows.add(buildHint("  3. register(id, factory, SPEC) in EnchantTriggerTypeRegistry static block"));
        extRows.add(buildSpacer(2));
        extRows.add(buildHint("Add effect:"));
        extRows.add(buildHint("  1. Implement EnchantEffect in effect/"));
        extRows.add(buildHint("  2. Add fromYaml(NamespacedKey, ConfigurationSection) factory"));
        extRows.add(buildHint("  3. register(style, factory) in EnchantEffectTypeRegistry static block"));
        extRows.add(buildSpacer(2));
        extRows.add(buildHint("Add end condition:"));
        extRows.add(buildHint("  1. Implement EndCondition in condition/"));
        extRows.add(buildHint("  2. Add case to EndCondition.fromYaml() switch"));
        extRows.add(buildHint("  3. Add resolution in DamageTakenListener.evaluateEndCondition()"));
        extRows.add(buildSpacer(2));
        extRows.add(buildHint("Add enchant: create enchants/<key>.yml,"));
        extRows.add(buildHint("  add key to saveDefaultEnchants() in EnchantForge.java"));
        ext.add("widgets", extRows);
        sections.add(ext);

        // § Runtime Lifecycle
        JsonObject lifecycle = buildCollapsible("\u00a7 Runtime Lifecycle", false, "0xFF1A2A1A");
        JsonArray lcRows = new JsonArray();
        lcRows.add(buildHint("Load: loadEnchantments() \u2192 CustomEnchant.fromYaml()"));
        lcRows.add(buildHint("      \u2192 EnchantmentRegistry.register()"));
        lcRows.add(buildSpacer(2));
        lcRows.add(buildHint("Equip: EquipmentEnchantListener.applyOnEquip()"));
        lcRows.add(buildHint("       \u2192 EnchantmentRegistry.getEnchants(ItemStack)"));
        lcRows.add(buildSpacer(2));
        lcRows.add(buildHint("Weapon trigger: EnchantEventRouter.dispatchWeapon()"));
        lcRows.add(buildHint("  \u2192 StackingDispatcher.collectFromItem() \u2192 dispatch()"));
        lcRows.add(buildHint("  \u2192 CustomEnchant.apply(player, level, context)"));
        lcRows.add(buildSpacer(2));
        lcRows.add(buildHint("Damage/armor: DamageTakenListener.onDamageTaken()"));
        lcRows.add(buildHint("  \u2192 resolveEndConditions() \u2192 evaluateEndCondition()"));
        lcRows.add(buildHint("  \u2192 ActiveEffectTracker.remove() + CooldownManager.setCooldown()"));
        lcRows.add(buildSpacer(2));
        lcRows.add(buildHint("Reapply (20-tick): EquipmentEnchantListener.reapplyInterruptedPassives()"));
        lcRows.add(buildHint("  \u2192 cooldown expired? \u2192 applyOnEquip()"));
        lifecycle.add("widgets", lcRows);
        sections.add(lifecycle);

        // § Debug System
        JsonObject debug = buildCollapsible("\u00a7 Debug System", false, "0xFF1A2A1A");
        JsonArray dbgRows = new JsonArray();
        dbgRows.add(buildHint("Per-enchant debug: set 'debug: true' in enchant YAML"));
        dbgRows.add(buildHint("EnchantDebug.log(enchant, player, msg) \u2014 no-ops when debug=false"));
        dbgRows.add(buildHint("Cooldown diagnostics: 'debug.cooldownVisuals: true' in config.yml"));
        dbgRows.add(buildHint("Cooldown test: /ctestcooldown while holding an item"));
        debug.add("widgets", dbgRows);
        sections.add(debug);

        // § VibeCraftMod Integration
        JsonObject vibecraft = buildCollapsible("\u00a7 VibeCraftMod Integration", false, "0xFF1A2A1A");
        JsonArray vcRows = new JsonArray();
        vcRows.add(buildHint("Server\u2192client channel: vibecraft:events (plugin messaging)"));
        vcRows.add(buildHint("All outbound messages route through VibeCraftUiBridge.java"));
        vcRows.add(buildSpacer(2));
        vcRows.add(buildHint("Message types sent by EnchantForge:"));
        vcRows.add(buildHint("  ui_schema      \u2014 full screen/overlay definition"));
        vcRows.add(buildHint("  open_screen    \u2014 show a named screen"));
        vcRows.add(buildHint("  close_screen   \u2014 hide a named screen"));
        vcRows.add(buildHint("  binding_update  \u2014 update single overlay data binding"));
        vcRows.add(buildHint("  binding_updates \u2014 batch update overlay bindings"));
        vcRows.add(buildSpacer(2));
        vcRows.add(buildHint("Client\u2192server: vibecraft:input (button clicks, text input)"));
        vcRows.add(buildHint("Mod detection: 40-tick join delay, check getListeningPluginChannels()"));
        vibecraft.add("widgets", vcRows);
        sections.add(vibecraft);

        JsonObject schema = buildScreenSchema("enchantforge:ai_manual", "EnchantForge AI Manual", sections);
        sendSchema(player, schema);
        sendEvent(player, buildOpenScreenEvent("enchantforge:ai_manual").toString());
    }

    // ---- Utilities ----

    public void sendInfo(Player player, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "claude_text");
        JsonArray lines = new JsonArray();
        lines.add(message);
        obj.add("lines", lines);
        sendEvent(player, obj.toString());
    }

    private static JsonObject buildScreenSchema(String screenId, String title, JsonArray contentWidgets) {
        JsonObject schema = new JsonObject();
        schema.addProperty("version", 1);
        schema.addProperty("defaultPlugin", "enchantforge");

        JsonObject panel = new JsonObject();
        panel.addProperty("maxWidth", 700);
        panel.addProperty("widthPercent", 0.82);

        JsonObject header = new JsonObject();
        header.addProperty("type", "text");
        header.addProperty("text", title);
        header.addProperty("color", "0xFF88DDDD");

        JsonObject scroll = new JsonObject();
        scroll.addProperty("type", "scroll_panel");
        scroll.addProperty("id", screenId.replace(":", "_") + "_scroll");
        scroll.addProperty("flex", true);
        scroll.add("widgets", contentWidgets);

        JsonArray widgets = new JsonArray();
        widgets.add(header);
        widgets.add(buildSpacer(4));
        widgets.add(scroll);

        JsonObject screen = new JsonObject();
        screen.addProperty("id", screenId);
        String pluginNamespace = screenId.contains(":") ? screenId.substring(0, screenId.indexOf(':')) : "enchantforge";
        screen.addProperty("plugin", pluginNamespace);
        screen.addProperty("title", title);
        screen.add("panel", panel);
        screen.add("widgets", widgets);

        JsonArray screens = new JsonArray();
        screens.add(screen);
        schema.add("screens", screens);
        return schema;
    }

    private static JsonObject buildCollapsible(String label, boolean open, String headerBg) {
        JsonObject c = new JsonObject();
        c.addProperty("type", "collapsible");
        String id = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        c.addProperty("id", id);
        c.addProperty("label", label);
        c.addProperty("open", open);
        c.addProperty("headerHeight", 16);
        c.addProperty("padding", 3);
        c.addProperty("headerBg", headerBg);
        c.addProperty("headerHoverBg", "0xFF1C2C3A");
        c.addProperty("background", "0xFF0A0A18");
        return c;
    }

    private static JsonObject buildHint(String text) {
        JsonObject h = new JsonObject();
        h.addProperty("type", "hint");
        h.addProperty("text", text);
        h.addProperty("wrap", true);
        return h;
    }

    private static JsonObject buildHintRow(String label, String value) {
        return buildHint(label + ": " + value);
    }

    private static JsonObject buildWrappedHint(String text) {
        JsonObject h = new JsonObject();
        h.addProperty("type", "hint");
        h.addProperty("text", text);
        h.addProperty("wrap", true);
        h.addProperty("lineHeight", 10);
        return h;
    }

    private static JsonObject buildSpacer(int height) {
        JsonObject s = new JsonObject();
        s.addProperty("type", "spacer");
        s.addProperty("height", height);
        return s;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private void sendSchema(Player player, JsonObject schema) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "ui_schema");
        event.add("schema", schema);
        sendEvent(player, event.toString());
    }

    private JsonObject buildOpenScreenEvent(String screenId) {
        JsonObject evt = new JsonObject();
        evt.addProperty("type", "open_screen");
        evt.addProperty("screenId", screenId);
        return evt;
    }

    private void sendEvent(Player player, String json) {
        try {
            player.sendPluginMessage(plugin, "vibecraft:events", json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Ignore channel errors for players without the mod installed.
        }
    }
}
