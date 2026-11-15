package dev.q2e.cordclipper.capture;

import dev.q2e.cordclipper.model.CoordinateSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Reads state already present on the client. It never opens the debug screen,
 * queries the server, or sends a Minecraft packet or command.
 */
public final class ClientStateReader {
    private ClientStateReader() {
    }

    /** Returns an empty result until both the local player and client level exist. */
    public static Optional<CoordinateSnapshot> capture(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return Optional.empty();
        }

        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        ScoreboardDetails scoreboardDetails = readSidebar(level.getScoreboard());
        String dimension = level.dimension().identifier().toString();

        return Optional.of(new CoordinateSnapshot(
                player.getBlockX(),
                player.getBlockY(),
                player.getBlockZ(),
                scoreboardDetails.region(),
                scoreboardDetails.server(),
                dimension,
                Instant.now()
        ));
    }

    private static ScoreboardDetails readSidebar(Scoreboard scoreboard) {
        if (scoreboard == null) {
            return ScoreboardDetails.EMPTY;
        }

        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return ScoreboardDetails.EMPTY;
        }

        String server = clean(objective.getDisplayName());
        Collection<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective);
        List<String> lines = new ArrayList<>(entries.size());
        for (PlayerScoreEntry entry : entries) {
            if (entry == null || entry.isHidden()) {
                continue;
            }

            Component display = entry.display();
            if (display == null) {
                Component ownerName = entry.ownerName();
                if (ownerName == null) {
                    ownerName = Component.literal(entry.owner() == null ? "" : entry.owner());
                }
                PlayerTeam team = entry.owner() == null ? null : scoreboard.getPlayersTeam(entry.owner());
                display = team == null ? ownerName : team.getFormattedName(ownerName);
            }

            String line = clean(display);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        String region = ScoreboardRegionParser.findRegion(lines).orElse("Unknown");
        return new ScoreboardDetails(region, server.isBlank() ? "Unknown server" : server);
    }

    private static String clean(Component component) {
        return component == null ? "" : component.getString().trim();
    }

    private record ScoreboardDetails(String region, String server) {
        private static final ScoreboardDetails EMPTY = new ScoreboardDetails("Unknown", "Unknown server");
    }
}
