package dev.q2e.cordclipper.capture;

import dev.q2e.cordclipper.model.CoordinateSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reads values already available in the local client state. It does not open
 * the debug screen or ask the game server for coordinates or scoreboard data.
 */
public final class ClientStateReader {
    private ClientStateReader() {
    }

    /**
     * Captures block coordinates and sidebar context from the active client.
     * No snapshot is available while the local player or level is absent.
     */
    public static Optional<CoordinateSnapshot> capture(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return Optional.empty();
        }

        LocalPlayer player = client.player;
        ScoreboardDetails scoreboardDetails = readSidebar(client.level.getScoreboard(), player);

        return Optional.of(new CoordinateSnapshot(
                player.getBlockX(),
                player.getBlockY(),
                player.getBlockZ(),
                scoreboardDetails.region(),
                scoreboardDetails.server(),
                client.level.dimension().identifier().toString(),
                Instant.now()
        ));
    }

    private static ScoreboardDetails readSidebar(Scoreboard scoreboard, LocalPlayer player) {
        if (scoreboard == null) {
            return ScoreboardDetails.EMPTY;
        }

        Objective objective = currentSidebarObjective(scoreboard, player);
        if (objective == null) {
            return ScoreboardDetails.EMPTY;
        }

        String server = clean(objective.getDisplayName());
        List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> entry != null && !entry.isHidden() && entry.owner() != null)
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed()
                        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
                .limit(15)
                .toList();
        List<String> lines = new ArrayList<>(entries.size());
        for (PlayerScoreEntry entry : entries) {
            Component displayText = entry.display();
            if (displayText == null) {
                Component ownerName = Component.literal(entry.owner());
                Team team = scoreboard.getPlayersTeam(entry.owner());
                displayText = team == null ? ownerName : team.getFormattedName(ownerName);
            }

            String line = clean(displayText);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        String region = ScoreboardRegionParser.findRegion(lines).orElse("Unknown");
        return new ScoreboardDetails(region, server.isBlank() ? "Unknown server" : server);
    }

    private static Objective currentSidebarObjective(Scoreboard scoreboard, LocalPlayer player) {
        Team team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team != null) {
            ChatFormatting color = team.getColor();
            if (color != null) {
                DisplaySlot teamSlot = DisplaySlot.teamColorToSlot(color);
                if (teamSlot != null) {
                    Objective teamObjective = scoreboard.getDisplayObjective(teamSlot);
                    if (teamObjective != null) {
                        return teamObjective;
                    }
                }
            }
        }
        return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    private static String clean(Component component) {
        return component == null ? "" : component.getString().trim();
    }

    private record ScoreboardDetails(String region, String server) {
        private static final ScoreboardDetails EMPTY = new ScoreboardDetails("Unknown", "Unknown server");
    }
}
