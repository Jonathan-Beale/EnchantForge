package com.example.enchantforge;

import com.example.enchantforge.effect.PlayerResourcePool;
import com.example.enchantforge.effect.ResourcePoolRegistry;
import com.example.enchantforge.effect.SuitFlightEffect;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SuitListener implements Listener {

    private static SuitListener instance;
    public static SuitListener getInstance() { return instance; }

    private final Plugin plugin;
    private final PlayerEnchantIndex enchantIndex;

    // ---- AI Interface helmet state ----

    private final Set<UUID> activeSuit = new HashSet<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Double> playerGlowRadius = new HashMap<>();
    private final Map<UUID, Boolean> wasPowerCritical = new HashMap<>();
    private final Map<UUID, Long> lastFallGuard = new HashMap<>();
    private final Map<UUID, Long> lastHostileWarning = new HashMap<>();

    // ---- Thruster boots — creative-style flight ----

    /** Players currently in flight mode (double-jump engaged). */
    private final Set<UUID> flightActive = new HashSet<>();
    /** Timestamp of the last jump press, for double-jump window detection. */
    private final Map<UUID, Long> lastJumpPressMs = new HashMap<>();
    /** Players who received allowFlight(true) from us — revoked when flight ends or on quit. */
    private final Set<UUID> thrusterFlightGranted = new HashSet<>();
    /** Previous-tick jump state, for rising/falling edge detection. */
    private final Map<UUID, Boolean> prevJump = new HashMap<>();
    /** Players currently in sprint-fly mode (elytra pose active); hysteresis prevents isSprinting() flicker. */
    private final Set<UUID> sprintFlyMode = new HashSet<>();

    // ---- Constants ----

    private static final TextColor FRIDAY_COLOR  = TextColor.color(0x00CCFF);

    // -------------------------------------------------------------------------

    public SuitListener(Plugin plugin, PlayerEnchantIndex enchantIndex) {
        this.plugin       = plugin;
        this.enchantIndex = enchantIndex;
        instance = this;
        ResourcePoolRegistry.all().forEach(p -> p.addOnChanged(this::onEnergyChanged));
        // 5-tick poll: boss bar, Friday audio, mob glow pulse
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 5L);
        // 1-tick poll: fall guard + flight physics need per-tick precision
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::fallGuardTick, 1L, 1L);
    }

    // ---- Helpers ----

    private SuitFlightEffect getSuitFlightEffect(Player player) {
        for (CustomEnchant e : enchantIndex.getByTrigger(player.getUniqueId(), "on_suit_jump")) {
            if (e.getEffect() instanceof SuitFlightEffect sfe) return sfe;
        }
        return null;
    }

    private PlayerResourcePool poolFor(Player player) {
        SuitFlightEffect sfe = getSuitFlightEffect(player);
        return sfe != null ? sfe.getPool() : ResourcePoolRegistry.get("energy");
    }

    // ---- Suit on/off (called by FridayAiEffect) ----

    public void activateSuit(Player player, double glowRadius) {
        if (!activeSuit.add(player.getUniqueId())) {
            return;
        }
        playerGlowRadius.put(player.getUniqueId(), glowRadius);
        BossBar bar = Bukkit.createBossBar("⚡  F.R.I.D.A.Y.", BarColor.BLUE, BarStyle.SEGMENTED_20);
        PlayerResourcePool p = poolFor(player);
        bar.setProgress(p.get(player) / p.getEffectiveMax(player.getUniqueId()));
        bar.addPlayer(player);
        bossBars.put(player.getUniqueId(), bar);
        sendHostileIndicatorSchema(player);
        friday(player, FridayLine.ACTIVATED);
    }

    // ---- Plugin channel helpers ----


    private void sendEvent(Player player, String json) {
        try {
            player.sendPluginMessage(plugin, "vibecraft:events",
                    json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    private boolean hasMod(Player player) {
        return player.getListeningPluginChannels().contains("vibecraft:events");
    }

    /** Sends a ui_schema_patch that registers the hostile indicator overlay widget. */
    private void sendHostileIndicatorSchema(Player player) {
        if (!hasMod(player)) return;
        JsonObject overlay = new JsonObject();
        overlay.addProperty("id", "ef_hostile_indicator");
        overlay.addProperty("type", "hostile_indicator");
        overlay.addProperty("plugin", "enchantforge");
        JsonObject pos = new JsonObject();
        pos.addProperty("x", 0);
        pos.addProperty("y", 0);
        overlay.add("position", pos);
        overlay.addProperty("dataBinding", "enchantforge.hostile_direction");

        JsonArray overlays = new JsonArray();
        overlays.add(overlay);

        JsonObject patch = new JsonObject();
        patch.add("overlays", overlays);

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "ui_schema_patch");
        msg.add("patch", patch);
        sendEvent(player, msg.toString());
    }

    public void deactivateSuit(Player player) {
        deactivateSuit(player.getUniqueId());
    }

    private void deactivateSuit(UUID id) {
        activeSuit.remove(id);
        playerGlowRadius.remove(id);
        BossBar bar = bossBars.remove(id);
        if (bar != null) bar.removeAll();
        wasPowerCritical.remove(id);

        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline() && hasMod(p)) {
            // Clear highlighted mobs
            JsonObject clearGlow = new JsonObject();
            clearGlow.addProperty("type", "ef_highlight_entities");
            clearGlow.add("groups", new JsonArray());
            sendEvent(p, clearGlow.toString());
            // Clear hostile direction indicator
            JsonObject clearDir = new JsonObject();
            clearDir.addProperty("type", "binding_update");
            clearDir.addProperty("binding", "enchantforge.hostile_direction");
            clearDir.add("value", com.google.gson.JsonNull.INSTANCE);
            sendEvent(p, clearDir.toString());
        }
        updateAllMobGlow();
    }

    // ---- 5-tick poll ----

    private void tick() {
        for (UUID id : new HashSet<>(activeSuit)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) { deactivateSuit(id); continue; }
            updateBossBar(p);
            checkFallGuard(p);
            checkFridayAudio(p);
            checkHostileMobWarning(p);
        }
        updateAllMobGlow();
    }

    // ---- Mob ESP — per-player via VibeCraftMod ----

    /**
     * Sends each suited+mod player entity highlights: hostiles red, neutrals aqua.
     * Always on (no pulse). Players without the mod receive nothing.
     */
    private void updateAllMobGlow() {
        if (activeSuit.isEmpty()) return;

        for (UUID id : activeSuit) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            double radius = playerGlowRadius.getOrDefault(id, 0.0);
            JsonArray hostile = new JsonArray();
            JsonArray neutral = new JsonArray();

            if (radius > 0) {
                double radiusSq = radius * radius;
                for (LivingEntity mob : p.getWorld().getLivingEntities()) {
                    if (mob instanceof Player || mob instanceof ArmorStand) continue;
                    if (mob.getLocation().distanceSquared(p.getLocation()) > radiusSq) continue;
                    if (mob instanceof Monster) hostile.add(mob.getEntityId());
                    else neutral.add(mob.getEntityId());
                }
            }

            JsonObject msg = new JsonObject();
            msg.addProperty("type", "ef_highlight_entities");
            msg.add("hostile", hostile);
            msg.add("neutral", neutral);
            sendEvent(p, msg.toString());
        }
    }

    // ---- Hostile mob behind-warning ----

    private void checkHostileMobWarning(Player player) {
        double radius = 14.0;
        long cooldownMs = 2500;
        long now = System.currentTimeMillis();
        if (now - lastHostileWarning.getOrDefault(player.getUniqueId(), 0L) < cooldownMs) return;

        Vector lookH = player.getLocation().getDirection().setY(0).normalize();
        for (Monster mob : player.getWorld().getEntitiesByClass(Monster.class)) {
            if (mob.getLocation().distanceSquared(player.getLocation()) > radius * radius) continue;
            Vector toMobH = mob.getLocation().toVector()
                    .subtract(player.getLocation().toVector()).setY(0);
            if (toMobH.lengthSquared() < 0.001) continue;
            toMobH.normalize();
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, lookH.dot(toMobH)))));
            if (angle > 120.0) {
                // cross Y: positive = mob is to the right
                double cross = lookH.getX() * toMobH.getZ() - lookH.getZ() * toMobH.getX();
                String side = cross > 0 ? "right" : "left";

                if (hasMod(player)) {
                    JsonObject value = new JsonObject();
                    value.addProperty("side", side);
                    value.addProperty("timestamp", now);

                    JsonObject msg = new JsonObject();
                    msg.addProperty("type", "binding_update");
                    msg.addProperty("binding", "enchantforge.hostile_direction");
                    msg.add("value", value);
                    sendEvent(player, msg.toString());
                } else {
                    // Fallback for players without the mod
                    String arrow = cross > 0 ? "►" : "◄";
                    player.sendActionBar(
                        Component.text(arrow + "  hostile  " + arrow)
                            .color(TextColor.color(0xFF2222)));
                }
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 0.5f);
                lastHostileWarning.put(player.getUniqueId(), now);
                break;
            }
        }
    }

    // ---- 1-tick poll: fall guard + flight physics ----

    private void fallGuardTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!enchantIndex.getByTrigger(p.getUniqueId(), "on_suit_jump").isEmpty()) {
                checkThrusterFallGuard(p);
                applyFlightTick(p);
            }
        }
    }

    // ---- Creative-style flight ----

    private void startFlight(Player player) {
        if (!flightActive.add(player.getUniqueId())) return;
        player.addScoreboardTag("thruster_flying");
        grantThrusterFlight(player);
        player.setFallDistance(0);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.3f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
        if (activeSuit.contains(player.getUniqueId())) {
            player.sendActionBar(Component.text("[ F.R.I.D.A.Y. ] Flight systems engaged.").color(FRIDAY_COLOR));
        }
        SuitFlightEffect sfe = getSuitFlightEffect(player);
        if (sfe != null) sfe.playEngageVisuals(player);
    }

    private void endFlight(Player player) {
        if (!flightActive.remove(player.getUniqueId())) return;
        sprintFlyMode.remove(player.getUniqueId());
        player.removeScoreboardTag("thruster_flying");
        player.setGliding(false);
        player.setFlying(false);
        revokeThrusterFlight(player);
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 0.8f);
        if (activeSuit.contains(player.getUniqueId())) {
            player.sendActionBar(Component.text("[ F.R.I.D.A.Y. ] Flight disengaged.").color(FRIDAY_COLOR));
        }
    }

    /**
     * Applies custom flight physics every tick for players in flight mode.
     *
     * Controls:
     *   Space       → ascend
     *   Shift       → descend
     *   Neither     → maintain altitude (gravity counteracted)
     *   WASD        → horizontal movement relative to look direction
     *   Sprint + W  → sprint-fly in exact look direction (pitch included), higher speed
     *
     * Uses setAllowFlight(true) as an anti-cheat bypass; if the server somehow
     * enables creative flight (setFlying=true), it is immediately reverted so our
     * physics remain authoritative.
     */
    private void applyFlightTick(Player player) {
        UUID uid = player.getUniqueId();
        if (!flightActive.contains(uid)) return;

        // Prevent vanilla creative-flight from interfering with our physics
        if (player.isFlying()) player.setFlying(false);

        if (player.isOnGround()) {
            endFlight(player);
            return;
        }

        SuitFlightEffect sfe = getSuitFlightEffect(player);
        if (sfe == null) { endFlight(player); return; }
        boolean wasSprintFly = sprintFlyMode.contains(uid);
        SuitFlightEffect.TickResult result = sfe.tickFlight(player, wasSprintFly);
        if (!result.alive()) {
            endFlight(player);
            if (activeSuit.contains(uid)) friday(player, FridayLine.POWER_CRITICAL);
            return;
        }
        boolean isSprintFly = result.sprintFly();
        if (isSprintFly) sprintFlyMode.add(uid); else sprintFlyMode.remove(uid);
        if (isSprintFly != wasSprintFly) player.setGliding(isSprintFly);
        updateBossBar(player);
    }

    // ---- Boss bar / energy ----

    private void onEnergyChanged(Player player) {
        updateBossBar(player);
    }

    private void updateBossBar(Player player) {
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) return;
        PlayerResourcePool p = poolFor(player);
        double pct = p.get(player) / p.getEffectiveMax(player.getUniqueId());
        bar.setProgress(Math.max(0.0, Math.min(1.0, pct)));
        bar.setColor(pct > 0.6 ? BarColor.BLUE : pct > 0.3 ? BarColor.YELLOW : BarColor.RED);
    }

    // ---- Fall guards ----

    private void checkFallGuard(Player player) {
        if (player.isOnGround() || player.isFlying() || player.getFallDistance() < 14) return;
        long now = System.currentTimeMillis();
        if (now - lastFallGuard.getOrDefault(player.getUniqueId(), 0L) < 3000) return;
        if (!poolFor(player).tryConsumeEmergency(player, 18)) return;

        lastFallGuard.put(player.getUniqueId(), now);
        Vector v = player.getVelocity();
        player.setVelocity(new Vector(v.getX() * 0.4, 0.5, v.getZ() * 0.4));
        player.setFallDistance(0);

        Location feet = player.getLocation();
        player.getWorld().spawnParticle(Particle.FLAME, feet, 24, 0.2, 0.1, 0.2, 0.12);
        player.getWorld().spawnParticle(Particle.SMOKE, feet, 16, 0.2, 0.1, 0.2, 0.05);
        player.getWorld().playSound(feet, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.3f);
        friday(player, FridayLine.FALL_PROTECTION);
    }

    private void checkThrusterFallGuard(Player player) {
        if (player.isOnGround() || player.isFlying() || flightActive.contains(player.getUniqueId())) return;

        Vector vel = player.getVelocity();
        if (vel.getY() >= -0.1) return;
        if (player.getFallDistance() < 3.0f) return;

        long now = System.currentTimeMillis();
        if (now - lastFallGuard.getOrDefault(player.getUniqueId(), 0L) < 3000) return;

        double lookAhead = Math.max(4.0, Math.abs(vel.getY()) * 2 + 2.0);
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                player.getLocation().add(0, 0.05, 0),
                new Vector(0, -1, 0), lookAhead);
        if (hit == null) return;

        if (!poolFor(player).tryConsumeEmergency(player, 12)) return;

        lastFallGuard.put(player.getUniqueId(), now);
        player.setVelocity(new Vector(vel.getX() * 0.5, 0.0, vel.getZ() * 0.5));
        player.setFallDistance(0);

        Location feet = player.getLocation();
        player.getWorld().spawnParticle(Particle.FLAME, feet, 22, 0.20, 0.08, 0.20, 0.10);
        player.getWorld().spawnParticle(Particle.SMOKE, feet, 14, 0.20, 0.08, 0.20, 0.04);
        player.getWorld().playSound(feet, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9f, 1.4f);
        player.getWorld().playSound(feet, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.5f);

        if (activeSuit.contains(player.getUniqueId())) {
            friday(player, FridayLine.FALL_PROTECTION);
        }
    }

    private void checkFridayAudio(Player player) {
        PlayerResourcePool p = poolFor(player);
        double pct = p.get(player) / p.getEffectiveMax(player.getUniqueId());
        boolean critical = pct < 0.2;
        boolean wasCrit  = wasPowerCritical.getOrDefault(player.getUniqueId(), false);
        if (critical && !wasCrit)               friday(player, FridayLine.POWER_CRITICAL);
        else if (!critical && wasCrit && pct > 0.95) friday(player, FridayLine.POWER_RESTORED);
        wasPowerCritical.put(player.getUniqueId(), critical);
    }

    // ---- Fall damage failsafe ----

    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (enchantIndex.getByTrigger(player.getUniqueId(), "on_suit_jump").isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean proactiveFired = now - lastFallGuard.getOrDefault(player.getUniqueId(), 0L) < 1000;
        if (proactiveFired) { event.setCancelled(true); return; }

        if (!poolFor(player).tryConsumeEmergency(player, 12)) return;
        lastFallGuard.put(player.getUniqueId(), now);
        event.setCancelled(true);

        Location feet = player.getLocation();
        player.getWorld().spawnParticle(Particle.FLAME, feet, 22, 0.20, 0.08, 0.20, 0.10);
        player.getWorld().spawnParticle(Particle.SMOKE, feet, 14, 0.20, 0.08, 0.20, 0.04);
        player.getWorld().playSound(feet, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9f, 1.4f);
        player.getWorld().playSound(feet, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.5f);

        if (activeSuit.contains(player.getUniqueId())) friday(player, FridayLine.FALL_PROTECTION);
    }

    // ---- Anti-cheat flight permission ----

    private void grantThrusterFlight(Player player) {
        if (!player.getAllowFlight() && thrusterFlightGranted.add(player.getUniqueId())) {
            player.setAllowFlight(true);
        }
    }

    private void revokeThrusterFlight(Player player) {
        if (thrusterFlightGranted.remove(player.getUniqueId()) && player.isOnline()) {
            if (player.isFlying()) player.setFlying(false);
            if (player.getAllowFlight()) player.setAllowFlight(false);
        }
    }

    // ---- Input: double-jump to toggle flight ----
    //
    // State machine:
    //   Any state, both presses mid-air within DOUBLE_JUMP_WINDOW_MS → toggle flight
    //   Landing while flight active → endFlight (handled in applyFlightTick)

    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        Player player  = event.getPlayer();
        UUID   uid     = player.getUniqueId();
        boolean jumping    = event.getInput().isJump();
        boolean wasJumping = prevJump.getOrDefault(uid, false);
        prevJump.put(uid, jumping);

        if (!jumping || wasJumping) return; // only care about rising edge

        long now  = System.currentTimeMillis();
        long last = lastJumpPressMs.getOrDefault(uid, 0L);
        lastJumpPressMs.put(uid, now);

        // Double-jump: second press must be airborne and within the window
        if (player.isOnGround()) return;
        SuitFlightEffect sfe = getSuitFlightEffect(player);
        if (sfe == null) return;
        if (now - last >= sfe.getDoubleJumpWindowMs()) return;

        if (flightActive.contains(uid)) endFlight(player);
        else startFlight(player);
    }

    /** Cancel vanilla glide-state changes that contradict our sprint-fly mode. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (!flightActive.contains(p.getUniqueId())) return;
        boolean wantGliding = sprintFlyMode.contains(p.getUniqueId());
        if (event.isGliding() != wantGliding) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        flightActive.remove(id);
        sprintFlyMode.remove(id);
        event.getPlayer().removeScoreboardTag("thruster_flying");
        deactivateSuit(id);
        prevJump.remove(id);
        lastJumpPressMs.remove(id);
        lastFallGuard.remove(id);
        lastHostileWarning.remove(id);
        revokeThrusterFlight(event.getPlayer());
    }

    // ---- Friday voice ----

    public enum FridayLine {
        ACTIVATED(     "F.R.I.D.A.Y. online. All systems nominal."),
        POWER_CRITICAL("Warning: power levels critical."),
        POWER_RESTORED("Power cells fully charged."),
        FALL_PROTECTION("Emergency landing thrusters engaged."),
        CHARGE_MAX(    "Thrusters at maximum charge. Ready to fire.");

        final String text;
        FridayLine(String t) { text = t; }
    }

    public void friday(Player player, FridayLine line) {
        player.sendActionBar(Component.text("[ F.R.I.D.A.Y. ] " + line.text).color(FRIDAY_COLOR));
        switch (line) {
            case ACTIVATED      -> player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE,    0.8f, 1.5f);
            case POWER_CRITICAL -> {
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.8f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL,   0.7f, 0.5f);
            }
            case POWER_RESTORED  -> player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE,        0.8f, 1.3f);
            case FALL_PROTECTION -> player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,  1.0f, 1.5f);
            case CHARGE_MAX      -> player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ATTACK_TARGET,    0.9f, 1.3f);
        }
    }
}
