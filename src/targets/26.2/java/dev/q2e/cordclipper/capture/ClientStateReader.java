package dev.q2e.cordclipper.capture;

import dev.q2e.cordclipper.model.CoordinateSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reads state that is already present on the client. It does not open the F3
 * overlay, request data from the server, or issue game commands.
 */
public final class ClientStateReader {
    private ClientStateReader() {
    }

    /**
     * Captures block coordinates and scoreboard context from the active client.
     * If the local player or level is unavailable, no snapshot can be made.
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
                PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                displayText = team == null
                        ? entry.ownerName()
                        : PlayerTeam.formatNameForTeam(team, entry.ownerName());
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
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team != null) {
            DisplaySlot teamSlot = team.getColor().map(TeamColor::displaySlot).orElse(null);
            if (teamSlot != null) {
                Objective teamObjective = scoreboard.getDisplayObjective(teamSlot);
                if (teamObjective != null) {
                    return teamObjective;
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
