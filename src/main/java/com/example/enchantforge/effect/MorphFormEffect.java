package com.example.enchantforge.effect;

import com.example.enchantforge.VisibilityUtil;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MorphFormEffect implements EnchantEffect, Listener {

    private static Plugin plugin;
    private static final Map<UUID, ActiveMorph> activeMorphs = new HashMap<>();

    private static final String TEAM = "ef_morph_nc";
    private final EntityType morphEntityType;
    private final List<PotionSpec> potionSpecs;
    private final boolean mimicSwing;
    private final VisualMode visualMode;
    private final boolean forceSwimPose;
    private final boolean hidePlayer;

    private MorphFormEffect(EntityType morphEntityType, List<PotionSpec> potionSpecs, boolean mimicSwing,
                            VisualMode visualMode, boolean forceSwimPose, boolean hidePlayer) {
        this.morphEntityType = morphEntityType;
        this.potionSpecs = List.copyOf(potionSpecs);
        this.mimicSwing = mimicSwing;
        this.visualMode = visualMode;
        this.forceSwimPose = forceSwimPose;
        this.hidePlayer = hidePlayer;
    }

    public static void init(Plugin pluginInstance) {
        plugin = pluginInstance;
        plugin.getServer().getPluginManager().registerEvents(
                new MorphFormEffect(EntityType.WOLF, List.of(), true, VisualMode.ENTITY_FOLLOW, false, true),
                plugin
        );
    }

    public static MorphFormEffect fromYaml(ConfigurationSection section) {
        String entityName = section.getString("entity", "WOLF").toUpperCase(Locale.ROOT);
        EntityType type;
        try {
            type = EntityType.valueOf(entityName);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown morph entity type: " + entityName);
        }
        if (!type.isSpawnable() || !type.isAlive()) {
            throw new IllegalArgumentException("Morph entity type must be a spawnable living entity: " + entityName);
        }

        boolean mimicSwing = section.getBoolean("mimicSwing", true);
        VisualMode visualMode = parseVisualMode(section.getString("visualMode", "entity_follow"));
        boolean forceSwimPose = section.getBoolean("forceSwimPose", false);
        boolean hidePlayer = section.getBoolean("hidePlayer", visualMode == VisualMode.ENTITY_FOLLOW);
        List<PotionSpec> specs = parsePotionSpecs(section);
        if (specs.isEmpty()) {
            specs = List.of(
                    new PotionSpec(requireEffectType("speed"), 1),
                    new PotionSpec(requireEffectType("strength"), 1),
                    new PotionSpec(requireEffectType("jump_boost"), 1),
                    new PotionSpec(requireEffectType("night_vision"), 0)
            );
        }

        return new MorphFormEffect(type, specs, mimicSwing, visualMode, forceSwimPose, hidePlayer);
    }

    @Override
    public String id() { return "morph_form"; }

    @Override
    public void apply(Player player, int enchantLevel, int durationTicks) {
        if (plugin == null) {
            throw new IllegalStateException("MorphFormEffect not initialized");
        }

        remove(player);
        if (hidePlayer) {
            VisibilityUtil.hide(player);
        } else {
            VisibilityUtil.show(player);
        }

        int dur = durationTicks > 0 ? durationTicks : 200;
        Set<PotionEffectType> appliedPotionTypes = new HashSet<>();
        for (PotionSpec spec : potionSpecs) {
            appliedPotionTypes.add(spec.type());
            player.addPotionEffect(new PotionEffect(spec.type(), dur, spec.amplifier(), false, false, false));
        }

        LivingEntity morphEntity = null;
        if (visualMode == VisualMode.ENTITY_FOLLOW) {
            Entity spawned = player.getWorld().spawnEntity(player.getLocation(), morphEntityType);
            if (!(spawned instanceof LivingEntity living)) {
                spawned.remove();
                throw new IllegalStateException("Failed to spawn living morph entity for type: " + morphEntityType);
            }

            morphEntity = living;
            morphEntity.setAI(false);
            morphEntity.setGravity(false);
            morphEntity.setCollidable(false);
            morphEntity.setInvulnerable(true);
            morphEntity.setPersistent(false);
        addNoCollideEntries(player, morphEntity);
        }

        UUID id = player.getUniqueId();
        BukkitTask[] ref = {null};
        ref[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player p = plugin.getServer().getPlayer(id);
            ActiveMorph active = activeMorphs.get(id);
            if (p == null || !p.isOnline() || active == null || (active.entity != null && active.entity.isDead())) {
                removeActive(id);
                if (ref[0] != null) ref[0].cancel();
                return;
            }

            if (active.entity != null) {
                Vector delta = p.getLocation().toVector().subtract(active.entity.getLocation().toVector());
                if (delta.lengthSquared() > 0.08 * 0.08) {
                    active.entity.teleport(p.getLocation());
                }
            }

            if (active.forceSwimPose) {
                boolean canForce = canForceSwimPose(p);
                if (canForce && !p.isSwimming()) {
                    p.setSwimming(true);
                    active.forcedSwimming = true;
                } else if (!canForce && active.forcedSwimming && p.isSwimming()) {
                    p.setSwimming(false);
                    active.forcedSwimming = false;
                }
            }
        }, 1L, 1L);

        activeMorphs.put(id, new ActiveMorph(morphEntity, ref[0], appliedPotionTypes, mimicSwing, forceSwimPose, hidePlayer));
    }

    @Override
    public void remove(Player player) {
        removeActive(player.getUniqueId());
        VisibilityUtil.show(player);
    }

    @EventHandler
    public void onPlayerSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        ActiveMorph active = activeMorphs.get(event.getPlayer().getUniqueId());
        if (active == null || !active.mimicSwing || active.entity == null || active.entity.isDead()) return;
        active.entity.swingMainHand();
    }

    private static void removeActive(UUID playerId) {
        ActiveMorph active = activeMorphs.remove(playerId);
        Player player = plugin.getServer().getPlayer(playerId);
        if (active == null) return;

        if (active.task != null) active.task.cancel();

        if (active.entity != null && !active.entity.isDead()) {
            removeNoCollideEntries(player, active.entity);
            active.entity.remove();
        }

        if (player != null) {
            if (active.forcedSwimming && player.isSwimming()) {
                player.setSwimming(false);
            }
            for (PotionEffectType type : active.appliedPotionTypes) {
                player.removePotionEffect(type);
            }
            if (active.hidePlayer) {
                VisibilityUtil.show(player);
            }
        }
    }

    private static void addNoCollideEntries(Player player, LivingEntity entity) {
        ScoreboardTeamUtil.addNeverCollide(TEAM,
                player != null ? player.getName() : null,
                entity.getUniqueId().toString());
    }

    private static void removeNoCollideEntries(Player player, LivingEntity entity) {
        ScoreboardTeamUtil.removeNeverCollide(TEAM,
                player != null ? player.getName() : null,
                entity.getUniqueId().toString());
    }

    private static List<PotionSpec> parsePotionSpecs(ConfigurationSection section) {
        List<Map<?, ?>> rawList = section.getMapList("effects");
        List<PotionSpec> specs = new ArrayList<>();
        for (Map<?, ?> raw : rawList) {
            Object typeValue = raw.get("type");
            if (!(typeValue instanceof String typeName) || typeName.isBlank()) continue;
            int amplifier = 0;
            Object ampValue = raw.get("amplifier");
            if (ampValue instanceof Number number) {
                amplifier = number.intValue();
            }
            specs.add(new PotionSpec(requireEffectType(typeName), amplifier));
        }
        return specs;
    }

    private static PotionEffectType requireEffectType(String name) {
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
        if (type == null) {
            throw new IllegalArgumentException("Unknown potion effect type: " + name);
        }
        return type;
    }

    private static VisualMode parseVisualMode(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return VisualMode.ENTITY_FOLLOW;
        return switch (rawValue.toLowerCase(Locale.ROOT).trim()) {
            case "entity_follow", "entity", "companion" -> VisualMode.ENTITY_FOLLOW;
            case "none" -> VisualMode.NONE;
            default -> throw new IllegalArgumentException("Unknown morph visualMode: " + rawValue);
        };
    }

    private static boolean canForceSwimPose(Player player) {
        return player.getGameMode() != GameMode.SPECTATOR
                && !player.isDead()
                && !player.isSleeping()
                && !player.isInsideVehicle()
                && !player.isFlying()
                && !player.isGliding()
                && !player.isClimbing();
    }

    private record PotionSpec(PotionEffectType type, int amplifier) {}

    private enum VisualMode {
        ENTITY_FOLLOW,
        NONE
    }

    private static final class ActiveMorph {
        private final LivingEntity entity;
        private final BukkitTask task;
        private final Set<PotionEffectType> appliedPotionTypes;
        private final boolean mimicSwing;
        private final boolean forceSwimPose;
        private final boolean hidePlayer;
        private boolean forcedSwimming;

        private ActiveMorph(LivingEntity entity, BukkitTask task, Set<PotionEffectType> appliedPotionTypes,
                            boolean mimicSwing, boolean forceSwimPose, boolean hidePlayer) {
            this.entity = entity;
            this.task = task;
            this.appliedPotionTypes = appliedPotionTypes;
            this.mimicSwing = mimicSwing;
            this.forceSwimPose = forceSwimPose;
            this.hidePlayer = hidePlayer;
            this.forcedSwimming = false;
        }
    }
}