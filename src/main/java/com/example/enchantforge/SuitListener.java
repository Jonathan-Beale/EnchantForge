package com.example.enchantforge;

import com.example.enchantforge.effect.PlayerResourcePool;
import com.example.enchantforge.effect.VelocityImpulseEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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

    /** Players with the Friday AI helmet enchant currently equipped. */
    private final Set<UUID> activeSuit  = new HashSet<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    /**
     * Charge state per player.
     * null  = idle (no double-jump in progress this air session)
     * -1    = already fired this air session
     * >= 0  = charging (value is ticks held so far)
     */
    private final Map<UUID, Integer> chargeState = new HashMap<>();
    private final Map<UUID, Boolean> prevJump    = new HashMap<>();

    /** Friday spam-prevention state. */
    private final Map<UUID, Boolean> wasPowerCritical = new HashMap<>();
    private final Map<UUID, Long>    lastFallGuard     = new HashMap<>();

    /** Players who received setAllowFlight(true) from us — revoked on landing / quit. */
    private final Set<UUID> thrusterFlightGranted = new HashSet<>();

    private static final TextColor FRIDAY_COLOR = TextColor.color(0x00CCFF);

    // -------------------------------------------------------------------------

    public SuitListener(Plugin plugin, PlayerEnchantIndex enchantIndex, PlayerResourcePool energy) {
        this.plugin       = plugin;
        this.enchantIndex = enchantIndex;
        this.energy       = energy;
        instance = this;
        energy.onChanged(this::onEnergyChanged);
        // 5-tick poll: BossBar refresh + Friday audio checks
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 5L);
        // 1-tick poll: fall guard needs per-tick precision — at terminal velocity a player
        // passes through a 3-block detection window in under one tick, so 5-tick polling
        // misses the window entirely on fast falls.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::fallGuardTick, 1L, 1L);
    }

    // ---- Suit on/off (called by FridayAiEffect) ----

    public void activateSuit(Player player) {
        if (!activeSuit.add(player.getUniqueId())) return;
        BossBar bar = Bukkit.createBossBar("⚡  F.R.I.D.A.Y.", BarColor.BLUE, BarStyle.SEGMENTED_20);
        bar.setProgress(energy.get(player) / energy.getMax());
        bar.addPlayer(player);
        bossBars.put(player.getUniqueId(), bar);
        friday(player, FridayLine.ACTIVATED);
    }

    public void deactivateSuit(Player player) {
        deactivateSuit(player.getUniqueId());
    }

    private void deactivateSuit(UUID id) {
        activeSuit.remove(id);
        BossBar bar = bossBars.remove(id);
        if (bar != null) bar.removeAll();
        chargeState.remove(id);
        wasPowerCritical.remove(id);
    }

    // ---- Tick ----

    private void tick() {
        for (UUID id : new HashSet<>(activeSuit)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) { deactivateSuit(id); continue; }
            updateBossBar(p);
            checkFallGuard(p);
            checkFridayAudio(p);
        }
        // Charging visuals fire for all players with thruster boots (suit optional)
        for (Map.Entry<UUID, Integer> entry : new HashMap<>(chargeState).entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) continue;
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) showChargeEffect(p, entry.getValue());
        }
    }

    /** 1-tick scheduler — fall guard + continuous thrust for every boot wearer each tick. */
    private void fallGuardTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!enchantIndex.getByTrigger(p.getUniqueId(), "on_suit_jump").isEmpty()) {
                checkThrusterFallGuard(p);
                applyThrusterTick(p);
            }
        }
    }

    /**
     * Applies one tick of continuous thrust while the player holds space mid-air.
     * Stops thrusting (marks chargeState spent) if energy hits the 5% reserve.
     */
    private void applyThrusterTick(Player player) {
        Integer ct = chargeState.get(player.getUniqueId());
        if (ct == null || ct < 0) return;
        if (player.isOnGround()) return;

        for (PlayerEnchantIndex.SlottedEnchant se : enchantIndex.getByTrigger(player.getUniqueId(), "on_suit_jump")) {
            if (se.enchant().getEffect() instanceof VelocityImpulseEffect vie) {
                if (!vie.tickThrust(player, se.level())) {
                    // Reserve hit — sputter out
                    chargeState.put(player.getUniqueId(), -1);
                    player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 0.8f);
                }
            }
            break;
        }
    }

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

    /**
     * Auto-brake for thruster boot wearers: fires when the player is falling,
     * would take fall damage (fallDistance > 3), and is within 3 blocks of solid
     * ground. Kills downward velocity and resets fall distance so the remaining
     * drop (≤ 3 blocks from the detection point) causes no damage.
     */
    private void checkThrusterFallGuard(Player player) {
        if (player.isOnGround() || player.isFlying()) return;

        Vector vel = player.getVelocity();
        if (vel.getY() >= -0.1) return;          // not falling meaningfully
        if (player.getFallDistance() < 3.0f) return; // vanilla won't deal damage anyway

        // Cooldown: don't re-fire within 3 s of last guard
        long now = System.currentTimeMillis();
        if (now - lastFallGuard.getOrDefault(player.getUniqueId(), 0L) < 3000) return;

        // Look ahead far enough to cover at least 2 ticks of fall at current speed, with a
        // minimum of 4 blocks so slow falls are also caught.  This prevents the detection
        // window being skipped entirely on high-speed falls.
        double lookAhead = Math.max(4.0, Math.abs(vel.getY()) * 2 + 2.0);
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                player.getLocation().add(0, 0.05, 0),
                new Vector(0, -1, 0), lookAhead);
        if (hit == null) return;

        // Consume from emergency reserve — landing protection must always fire if energy exists.
        if (!energy.tryConsumeEmergency(player, 12)) return;

        lastFallGuard.put(player.getUniqueId(), now);

        // Kill downward velocity; let gravity carry them the remaining ≤ 3 blocks safely
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
        boolean critical  = pct < 0.2;
        boolean wasCrit   = wasPowerCritical.getOrDefault(player.getUniqueId(), false);
        if (critical && !wasCrit)          friday(player, FridayLine.POWER_CRITICAL);
        else if (!critical && wasCrit && pct > 0.95) friday(player, FridayLine.POWER_RESTORED);
        wasPowerCritical.put(player.getUniqueId(), critical);
    }

    private void showChargeEffect(Player player, int ct) {
        Location feet = player.getLocation();
        if (ct >= 20) {
            player.getWorld().spawnParticle(Particle.FLAME, feet, 5, 0.25, 0.3, 0.25, 0.06);
            player.getWorld().spawnParticle(Particle.DUST,  feet, 4, 0.2, 0.3, 0.2, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 160, 0), 1.5f));
        } else if (ct >= 8) {
            player.getWorld().spawnParticle(Particle.FLAME, feet, 3, 0.12, 0.2, 0.12, 0.03);
        } else {
            player.getWorld().spawnParticle(Particle.SMOKE, feet, 2, 0.08, 0.1, 0.08, 0.01);
        }
    }

    // ---- Fall damage failsafe ----

    /**
     * Last-resort guard: if the proactive brake in checkThrusterFallGuard missed (e.g. the
     * player fell so fast the raycast window was entered and exited between two ticks), cancel
     * the fall-damage event here and play braking effects so the mechanic always fires.
     *
     * If the proactive brake fired within the last second we assume energy was already consumed
     * (don't double-charge) and just silently cancel the residual damage.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (enchantIndex.getByTrigger(player.getUniqueId(), "on_suit_jump").isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean proactiveFired = now - lastFallGuard.getOrDefault(player.getUniqueId(), 0L) < 1000;

        if (proactiveFired) {
            // Proactive brake already consumed energy — just suppress the residual damage.
            event.setCancelled(true);
            return;
        }

        // Proactive brake missed entirely — consume from emergency reserve as failsafe.
        if (!energy.tryConsumeEmergency(player, 12)) return;

        lastFallGuard.put(player.getUniqueId(), now);
        event.setCancelled(true);

        Location feet = player.getLocation();
        player.getWorld().spawnParticle(Particle.FLAME, feet, 22, 0.20, 0.08, 0.20, 0.10);
        player.getWorld().spawnParticle(Particle.SMOKE, feet, 14, 0.20, 0.08, 0.20, 0.04);
        player.getWorld().playSound(feet, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9f, 1.4f);
        player.getWorld().playSound(feet, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.5f);

        if (activeSuit.contains(player.getUniqueId())) {
            friday(player, FridayLine.FALL_PROTECTION);
        }
    }

    // ---- Anti-cheat flight permission ----

    /**
     * Temporarily allows flight so the server's movement validator doesn't kick the
     * player for the upward velocity produced by thruster thrust (up to 0.65 b/t vs
     * the ~0.42 b/t a normal jump allows). Revoked on landing via revokeThrusterFlight.
     */
    private void grantThrusterFlight(Player player) {
        if (!player.getAllowFlight() && thrusterFlightGranted.add(player.getUniqueId())) {
            player.setAllowFlight(true);
        }
    }

    private void revokeThrusterFlight(Player player) {
        if (thrusterFlightGranted.remove(player.getUniqueId()) && player.isOnline()) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    // ---- Jump input detection ----

    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        Player player  = event.getPlayer();
        boolean jumping    = event.getInput().isJump();
        boolean wasJumping = prevJump.getOrDefault(player.getUniqueId(), false);
        prevJump.put(player.getUniqueId(), jumping);

        if (player.isOnGround()) {
            chargeState.remove(player.getUniqueId()); // reset on landing
            revokeThrusterFlight(player);
            return;
        }

        // Skip all charge/fire logic for players without thruster boots equipped
        if (enchantIndex.getByTrigger(player.getUniqueId(), "on_suit_jump").isEmpty()) return;

        if (jumping && !wasJumping) {
            // Rising edge mid-air: begin continuous thrust if idle this air session
            if (!chargeState.containsKey(player.getUniqueId())) {
                chargeState.put(player.getUniqueId(), 0);
                grantThrusterFlight(player);
                // Ignition sound — per-tick thrust starts on the very next fallGuardTick
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f, 1.3f);
            }
        } else if (jumping) {
            // Held: increment hold-time counter (drives charge visuals and audio cues)
            Integer ct = chargeState.get(player.getUniqueId());
            if (ct != null && ct >= 0) {
                int next = ct + 1;
                chargeState.put(player.getUniqueId(), next);
                if (next == 8)  player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.3f);
                if (next == 20) {
                    player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.8f, 1.5f);
                    if (activeSuit.contains(player.getUniqueId())) friday(player, FridayLine.CHARGE_MAX);
                }
            }
        } else if (!jumping && wasJumping) {
            // Falling edge: cut thrust
            Integer ct = chargeState.remove(player.getUniqueId());
            if (ct != null && ct >= 0) {
                chargeState.put(player.getUniqueId(), -1); // spent; won't re-fire until landing
                player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.45f, 0.75f);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        deactivateSuit(id);
        prevJump.remove(id);
        chargeState.remove(id);
        lastFallGuard.remove(id);
        revokeThrusterFlight(event.getPlayer());
    }

    // ---- Friday voice ----

    public enum FridayLine {
        ACTIVATED(    "F.R.I.D.A.Y. online. All systems nominal."),
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
            case POWER_RESTORED  -> player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE,       0.8f, 1.3f);
            case FALL_PROTECTION -> player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.5f);
            case CHARGE_MAX      -> player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ATTACK_TARGET,   0.9f, 1.3f);
        }
    }
}
