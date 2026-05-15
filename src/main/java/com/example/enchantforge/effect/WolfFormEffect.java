package com.example.enchantforge.effect;

import com.example.enchantforge.VisibilityUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WolfFormEffect implements EnchantEffect, Listener {

    public static final WolfFormEffect INSTANCE = new WolfFormEffect();
    private static Plugin plugin;

    private static final Map<UUID, Wolf>       wolves = new HashMap<>();
    private static final Map<UUID, BukkitTask> tasks  = new HashMap<>();

    private WolfFormEffect() {}

    public static void init(Plugin p) {
        plugin = p;
        plugin.getServer().getPluginManager().registerEvents(INSTANCE, p);
    }

    @Override
    public void apply(Player player, int enchantLevel, int durationTicks) {
        UUID id = player.getUniqueId();
        remove(player);

        VisibilityUtil.hide(player);

        int dur = durationTicks > 0 ? durationTicks : 200;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,        dur, 1, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,     dur, 1, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,   dur, 1, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, dur, 0, false, false, false));

        Wolf wolf = player.getWorld().spawn(player.getLocation(), Wolf.class, w -> {
            w.setAI(false);
            w.setGravity(false);
            w.setCollidable(false);
            w.setInvulnerable(true);
            w.setPersistent(false);
        });
        // Re-apply after full spawn in case post-spawn initialisation resets these flags
        wolf.setGravity(false);
        wolf.setCollidable(false);
        wolves.put(id, wolf);

        // Belt-and-suspenders: NEVER collision rule so the wolf cannot push the player
        // even if setCollidable is bypassed by the server's entity-separation logic
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team noCollide = board.getTeam("ef_wolf_nc");
        if (noCollide == null) {
            noCollide = board.registerNewTeam("ef_wolf_nc");
            noCollide.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        noCollide.addEntry(player.getName());

        BukkitTask[] ref = {null};
        ref[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player p = plugin.getServer().getPlayer(id);
            Wolf w = wolves.get(id);
            if (p == null || !p.isOnline() || w == null || w.isDead()) {
                Wolf stale = wolves.remove(id);
                if (stale != null && !stale.isDead()) stale.remove();
                tasks.remove(id);
                if (ref[0] != null) ref[0].cancel();
                return;
            }
            w.teleport(p.getLocation());
        }, 1L, 1L);
        tasks.put(id, ref[0]);
    }

    @Override
    public void remove(Player player) {
        UUID id = player.getUniqueId();

        BukkitTask task = tasks.remove(id);
        if (task != null) task.cancel();

        Wolf wolf = wolves.remove(id);
        if (wolf != null && !wolf.isDead()) wolf.remove();

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team noCollide = board.getTeam("ef_wolf_nc");
        if (noCollide != null) noCollide.removeEntry(player.getName());

        VisibilityUtil.show(player);

        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    // -------------------------------------------------------------------------

    /** Mirror the player's arm swing to the wolf so it looks like a bite attack. */
    @EventHandler
    public void onPlayerSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Wolf wolf = wolves.get(event.getPlayer().getUniqueId());
        if (wolf == null || wolf.isDead()) return;
        wolf.swingMainHand();
    }
}
