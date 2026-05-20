package com.example.enchantforge;

import com.example.enchantforge.effect.PlayerResourcePool;
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
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
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
    private final PlayerResourcePool energy;

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

    // ---- Constants ----

    private static final TextColor FRIDAY_COLOR  = TextColor.color(0x00CCFF);
    private static final long   DOUBLE_JUMP_WINDOW_MS  = 400L;
    private static final double FLIGHT_ENERGY_PER_TICK = 40.0;
    private static final double FLIGHT_SPEED            = 0.25;
    private static final double SPRINT_FLIGHT_SPEED     = 0.6;
    private static final double FLIGHT_VERTICAL_SPEED   = 0.25;
    private static final double GRAVITY_COUNTERACT      = 0.08;

    // -------------------------------------------------------------------------

    public SuitListener(Plugin plugin, PlayerEnchantIndex enchantIndex, PlayerResourcePool energy) {
        this.plugin       = plugin;
        this.enchantIndex = enchantIndex;
        this.energy       = energy;
        instance = this;
        energy.onChanged(this::onEnergyChanged);
        ensureGlowTeam();
        // 5-tick poll: boss bar, Friday audio, mob glow pulse
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 5L);
        // 1-tick poll: fall guard + flight physics need per-tick precision
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::fallGuardTick, 1L, 1L);
    }

    // ---- Suit on/off (called by FridayAiEffect) ----

    public void activateSuit(Player player, double glowRadius) {
        if (!activeSuit.add(player.getUniqueId())) return;
        playerGlowRadius.put(player.getUniqueId(), glowRadius);
        BossBar bar = Bukkit.createBossBar("⚡  F.R.I.D.A.Y.", BarColor.BLUE, BarStyle.SEGMENTED_20);
        bar.setProgress(energy.get(player) / energy.getMax());
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
            clearGlow.add("entities", new JsonArray());
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

    // ---- Mob ESP — per-player via VibeCraftMod (hostile-only, pulsed) ----

    /**
     * Sends each suited+mod player the set of hostile entity IDs to highlight.
     * 4 s ON / 16 s OFF pulse (20 s cycle).  Players without the mod receive nothing.
     */
    private void updateAllMobGlow() {
        boolean pulseOn = !activeSuit.isEmpty() && (System.currentTimeMillis() % 20000L) < 4000L;

        for (UUID id : activeSuit) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline() || !hasMod(p)) continue;

            JsonArray entities = new JsonArray();
            if (pulseOn) {
                double radius = playerGlowRadius.getOrDefault(id, 0.0);
                if (radius > 0) {
                    double radiusSq = radius * radius;
                    for (Monster mob : p.getWorld().getEntitiesByClass(Monster.class)) {
                        if (mob.getLocation().distanceSquared(p.getLocation()) <= radiusSq) {
                            entities.add(mob.getEntityId());
                        }
                    }
                }
            }

            JsonObject msg = new JsonObject();
            msg.addProperty("type", "ef_highlight_entities");
            msg.add("entities", entities);
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
        grantThrusterFlight(player);
        player.setFallDistance(0);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.3f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
        if (activeSuit.contains(player.getUniqueId())) {
            player.sendActionBar(Component.text("[ F.R.I.D.A.Y. ] Flight systems engaged.").color(FRIDAY_COLOR));
        }
    }

    private void endFlight(Player player) {
        if (!flightActive.remove(player.getUniqueId())) return;
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

        // Drain energy; cut flight if the reserve is hit
        if (!energy.tryConsume(player, FLIGHT_ENERGY_PER_TICK)) {
            endFlight(player);
            if (activeSuit.contains(uid)) friday(player, FridayLine.POWER_CRITICAL);
            return;
        }

        var input   = player.getCurrentInput();
        double yawRad   = Math.toRadians(player.getLocation().getYaw());
        double pitchRad = Math.toRadians(player.getLocation().getPitch());

        double vx, vy, vz;

        if (player.isSprinting() && input.isForward()) {
            // Sprint-fly: move in exact look direction (pitch included)
            double s = SPRINT_FLIGHT_SPEED;
            vx = -Math.sin(yawRad) * Math.cos(pitchRad) * s;
            vy = -Math.sin(pitchRad) * s;
            vz =  Math.cos(yawRad)  * Math.cos(pitchRad) * s;
        } else {
            // Standard WASD horizontal movement
            double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);   // forward unit vector
            double rx =  Math.cos(yawRad), rz = Math.sin(yawRad);   // right unit vector
            double hx = 0, hz = 0;
            if (input.isForward())  { hx += fx; hz += fz; }
            if (input.isBackward()) { hx -= fx; hz -= fz; }
            if (input.isRight())    { hx += rx; hz += rz; }
            if (input.isLeft())     { hx -= rx; hz -= rz; }
            double len = Math.sqrt(hx * hx + hz * hz);
            if (len > 0.001) { hx = hx / len * FLIGHT_SPEED; hz = hz / len * FLIGHT_SPEED; }
            vx = hx;
            vz = hz;

            // Vertical: space = up, sneak = down, neither = hold altitude
            if (input.isJump())        vy =  FLIGHT_VERTICAL_SPEED;
            else if (input.isSneak())  vy = -FLIGHT_VERTICAL_SPEED;
            else                       vy =  GRAVITY_COUNTERACT;
        }

        player.setVelocity(new Vector(vx, vy, vz));
        player.setFallDistance(0);

        // Subtle exhaust trail every 3 ticks
        if (plugin.getServer().getCurrentTick() % 3 == 0) {
            Location feet = player.getLocation();
            player.getWorld().spawnParticle(Particle.FLAME, feet, 2, 0.10, 0.04, 0.10, 0.05);
            player.getWorld().spawnParticle(Particle.SMOKE, feet, 1, 0.12, 0.04, 0.12, 0.03);
        }
    }

    // ---- Boss bar / energy ----

    private void onEnergyChanged(Player player) {
        updateBossBar(player);
    }

    private void updateBossBar(Player player) {
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) return;
        double pct = energy.get(player) / energy.getMax();
        bar.setProgress(Math.max(0.0, Math.min(1.0, pct)));
        bar.setColor(pct > 0.6 ? BarColor.BLUE : pct > 0.3 ? BarColor.YELLOW : BarColor.RED);
    }

    // ---- Fall guards ----

    private void checkFallGuard(Player player) {
        if (player.isOnGround() || player.isFlying() || player.getFallDistance() < 14) return;
        long now = System.currentTimeMillis();
        if (now - lastFallGuard.getOrDefault(player.getUniqueId(), 0L) < 3000) return;
        if (!energy.tryConsumeEmergency(player, 18)) return;

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

        if (!energy.tryConsumeEmergency(player, 12)) return;

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
        double pct = energy.get(player) / energy.getMax();
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

        if (!energy.tryConsumeEmergency(player, 12)) return;
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
        if (now - last >= DOUBLE_JUMP_WINDOW_MS) return;
        if (enchantIndex.getByTrigger(uid, "on_suit_jump").isEmpty()) return;

        if (flightActive.contains(uid)) endFlight(player);
        else startFlight(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        flightActive.remove(id);
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
