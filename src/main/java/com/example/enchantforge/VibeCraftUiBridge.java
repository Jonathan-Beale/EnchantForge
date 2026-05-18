package com.example.enchantforge;

import com.example.enchantforge.condition.AbsorptionDepletedCondition;
import com.example.enchantforge.condition.DamagedCondition;
import com.example.enchantforge.condition.EndCondition;
import com.example.enchantforge.condition.FullHealthOrDamagedCondition;
import com.example.enchantforge.condition.NeverCondition;
import com.example.enchantforge.condition.TimeCondition;
import com.example.enchantforge.effect.EnchantEffectTypeRegistry;
import com.example.enchantforge.trigger.EnchantTrigger;
import com.example.enchantforge.trigger.EnchantTriggerTypeRegistry;
import com.example.enchantforge.trigger.StatThresholdTrigger;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private static final List<String> CATEGORY_ORDER =
            List.of("offensive", "defensive", "mobility", "survival", "utility", "transform");

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
        Map<String, List<CustomEnchant>> byCategory = new LinkedHashMap<>();
        for (String cat : CATEGORY_ORDER) byCategory.put(cat, new ArrayList<>());

        for (CustomEnchant e : registry.getAll()) {
            String cat = e.getCategory() != null ? e.getCategory().toLowerCase(Locale.ROOT) : "uncategorized";
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(e);
        }
        byCategory.values().forEach(list ->
                list.sort(java.util.Comparator.comparing(CustomEnchant::getDisplayName, String.CASE_INSENSITIVE_ORDER)));

        JsonArray sections = new JsonArray();
        for (var entry : byCategory.entrySet()) {
            List<CustomEnchant> enchants = entry.getValue();
            if (enchants.isEmpty()) continue;
            String catLabel = capitalize(entry.getKey()) + " (" + enchants.size() + ")";
            JsonObject catSection = buildCollapsible(catLabel, true, "0xFF1A2A3A");
            JsonArray catChildren = new JsonArray();
            for (CustomEnchant e : enchants) {
                catChildren.add(buildSpacer(2));
                catChildren.add(buildProductCard(e));
            }
            catSection.add("widgets", catChildren);
            sections.add(catSection);
            sections.add(buildSpacer(3));
        }

        JsonObject schema = buildScreenSchema("enchantforge:product_guide", "EnchantForge Guide", sections);
        sendSchema(player, schema);
        sendEvent(player, buildOpenScreenEvent("enchantforge:product_guide").toString());
    }

    private static JsonObject buildProductCard(CustomEnchant enchant) {
        String levelRange = enchant.getMaxLevel() > 1
                ? "I\u2013" + CustomEnchant.toRoman(enchant.getMaxLevel())
                : "I";
        JsonObject card = buildCollapsible(enchant.getDisplayName() + " " + levelRange, false, "0xFF111122");
        JsonArray rows = new JsonArray();
        rows.add(buildSpacer(2));
        rows.add(buildHintRow("Slot", formatSlot(enchant.getApplicableTo())));
        rows.add(buildHintRow("Activation", formatTrigger(enchant.getTrigger())));
        EndCondition end = enchant.getEndCondition();
        if (!(end instanceof NeverCondition)) {
            rows.add(buildHintRow("Duration", formatDuration(end)));
        }
        if (enchant.hasCooldown()) {
            rows.add(buildHintRow("Cooldown", (enchant.getCooldownTicks() / 20) + "s"));
        }
        String desc = enchant.resolveDescription(enchant.getMaxLevel());
        if (!desc.isBlank()) {
            rows.add(buildSpacer(2));
            rows.add(buildWrappedHint(desc));
        }
        rows.add(buildSpacer(2));
        card.add("widgets", rows);
        return card;
    }

    private static String formatTrigger(EnchantTrigger trigger) {
        if (trigger instanceof StatThresholdTrigger t) {
            String stat = t.getStat().equals("health") ? "health" : t.getStat();
            String cmp = t.getComparison().equals("below") ? "below" : "above";
            String val;
            if (t.getStat().equals("health")) {
                double hearts = t.getValue() / 2.0;
                val = (hearts == Math.floor(hearts) ? String.valueOf((int) hearts) : String.valueOf(hearts)) + " hearts";
            } else {
                val = String.valueOf(t.getValue());
            }
            return "Activates when " + stat + " is " + cmp + " " + val;
        }
        return switch (trigger.id()) {
            case "on_equip"        -> "While equipped";
            case "on_damage_taken" -> "Activates when you take damage";
            case "on_deal_damage"  -> "Activates when you deal damage";
            case "on_kill_entity"  -> "Activates when you kill an entity";
            case "on_right_click"  -> "Right-click to activate";
            case "on_suit_jump"    -> "Activates when you jump (suit)";
            default                -> trigger.id();
        };
    }

    private static String formatDuration(EndCondition end) {
        return switch (end) {
            case NeverCondition c               -> "Permanent";
            case TimeCondition c               -> "Lasts " + (c.getEffectDurationTicks() / 20) + "s";
            case DamagedCondition c            -> "Until next hit";
            case FullHealthOrDamagedCondition c -> "Until full health or next hit";
            case AbsorptionDepletedCondition c -> "Until absorption runs out";
            default                            -> end.getDisplayLabel();
        };
    }

    private static String formatSlot(List<String> applicableTo) {
        if (applicableTo.isEmpty()) return "Any item";
        if (applicableTo.contains("ARMOR")) return "Any armor";
        List<String> readable = new ArrayList<>();
        for (String s : applicableTo) {
            readable.add(switch (s) {
                case "_HELMET"     -> "helmets";
                case "_CHESTPLATE" -> "chestplates";
                case "_LEGGINGS"   -> "leggings";
                case "_BOOTS"      -> "boots";
                case "_SWORD"      -> "swords";
                case "_AXE"        -> "axes";
                default            -> s.toLowerCase(Locale.ROOT).replace("_", " ");
            });
        }
        return String.join(", ", readable);
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
        screen.addProperty("title", title);
        screen.add("widgets", widgets);

        JsonArray screens = new JsonArray();
        screens.add(screen);
        schema.add("screens", screens);
        return schema;
    }

    private static JsonObject buildCollapsible(String label, boolean open, String headerBg) {
        JsonObject c = new JsonObject();
        c.addProperty("type", "collapsible");
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
