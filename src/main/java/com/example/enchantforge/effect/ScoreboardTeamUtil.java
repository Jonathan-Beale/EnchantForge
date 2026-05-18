package com.example.enchantforge.effect;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class ScoreboardTeamUtil {

    private ScoreboardTeamUtil() {}

    public static void addNeverCollide(String teamName, String... entries) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        for (String entry : entries) {
            if (entry != null) team.addEntry(entry);
        }
    }

    public static void removeNeverCollide(String teamName, String... entries) {
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
        if (team == null) return;
        for (String entry : entries) {
            if (entry != null) team.removeEntry(entry);
        }
    }
}
