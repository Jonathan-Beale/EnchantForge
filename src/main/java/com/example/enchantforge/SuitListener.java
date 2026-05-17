package com.example.enchantforge;

import com.example.enchantforge.effect.EnergyManager;
import com.example.enchantforge.effect.ThrusterEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SuitListener implements Listener {

    private static SuitListener instance;
    public static SuitListener getInstance() { return instance; }

    private final EnchantmentRegistry registry;
    private final Plugin plugin;

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

    private static final TextColor FRIDAY_COLOR = TextColor.color(0x00CCFF);

    // -------------------------------------------------------------------------

    public SuitListener(EnchantmentRegistry registry, Plugin plugin) {
        this.registry = registry;
        this.plugin   = plugin;
        instance = this;
        EnergyManager.registerCallback(this::onEnergyChanged);
        // 5-tick poll: BossBar refresh + fall guard + Friday audio checks
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 5L);
    }

    // ---- Suit on/off (called by FridayAiEffect) ----

    public void activateSuit(Player player) {
        if (!activeSuit.add(player.getUniqueId())) return;
        BossBar bar = Bukkit.createBossBar("⚡  F.R.I.D.A.Y.", BarColor.BLUE, BarStyle.SEGMENTED_20);
        bar.setProgress(EnergyManager.get(player) / EnergyManager.MAX);
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

    private void onEnergyChanged(Player player) {
        updateBossBar(player);
    }

    private void updateBossBar(Player player) {
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) return;
        double pct = EnergyManager.get(player) / EnergyManager.MAX;
        bar.setProgress(Math.max(0.0, Math.min(1.0, pct)));
        bar.setColor(pct > 0.6 ? BarColor.BLUE : pct > 0.3 ? BarColor.YELLOW : BarColor.RED);
    }

    private void checkFallGuard(Player player) {
        if (player.isOnGround() || player.isFlying() || player.getFallDistance() < 14) return;
        long now = System.currentTimeMillis();
        if (now - lastFallGuard.getOrDefault(player.getUniqueId(), 0L) < 3000) return;
        if (!EnergyManager.tryConsume(player, 18)) return;

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

    private void checkFridayAudio(Player player) {
        double pct = EnergyManager.get(player) / EnergyManager.MAX;
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

    // ---- Jump input detection ----

    @EventHandler
    public void onPlayerInput(PlayerInputEvent event) {
        Player player  = event.getPlayer();
        boolean jumping    = event.getInput().isJump();
        boolean wasJumping = prevJump.getOrDefault(player.getUniqueId(), false);
        prevJump.put(player.getUniqueId(), jumping);

        if (player.isOnGround()) {
            chargeState.remove(player.getUniqueId()); // reset on landing
            return;
        }

        if (jumping && !wasJumping) {
            // Rising edge mid-air: start charge if idle this air session
            if (!chargeState.containsKey(player.getUniqueId())) {
                chargeState.put(player.getUniqueId(), 0);
            }
        } else if (jumping) {
            // Held: increment charge
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
            // Falling edge: fire
            Integer ct = chargeState.remove(player.getUniqueId());
            if (ct != null && ct >= 0) {
                chargeState.put(player.getUniqueId(), -1); // spent; won't re-fire until landing
                fireThruster(player, ct);
            }
        }
    }

    private void fireThruster(Player player, int chargeTicks) {
        ItemStack boots = player.getInventory().getBoots();
        if (boots == null || boots.getType() == Material.AIR) return;
        for (Map.Entry<CustomEnchant, Integer> e : registry.getEnchants(boots).entrySet()) {
            if (!"on_suit_jump".equals(e.getKey().getTrigger().id())) continue;
            if (!(e.getKey().getEffect()  instanceof ThrusterEffect t))  continue;
            t.fire(player, e.getValue(), chargeTicks);
            break;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        deactivateSuit(id);
        prevJump.remove(id);
        chargeState.remove(id);
        lastFallGuard.remove(id);
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
