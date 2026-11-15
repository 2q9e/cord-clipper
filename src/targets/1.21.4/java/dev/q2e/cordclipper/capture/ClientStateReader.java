package dev.q2e.cordclipper.capture;

import dev.q2e.cordclipper.model.CoordinateSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Reads the current player's already available client state. This does not
 * open a screen, inspect the F3 overlay, or send a request to the game server.
 */
public final class ClientStateReader {
    private ClientStateReader() {
    }

    /**
     * Captures block coordinates and scoreboard context from the active client.
     * If the local player or world is unavailable, no snapshot can be made.
     */
    public static Optional<CoordinateSnapshot> capture(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return Optional.empty();
        }

        ClientPlayerEntity player = client.player;
        ScoreboardDetails scoreboardDetails = readSidebar(client.world.getScoreboard());

        return Optional.of(new CoordinateSnapshot(
                player.getBlockX(),
                player.getBlockY(),
                player.getBlockZ(),
                scoreboardDetails.region(),
                scoreboardDetails.server(),
                client.world.getRegistryKey().getValue().toString(),
                Instant.now()
        ));
    }

    private static ScoreboardDetails readSidebar(Scoreboard scoreboard) {
        if (scoreboard == null) {
            return ScoreboardDetails.EMPTY;
        }

        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (objective == null) {
            return ScoreboardDetails.EMPTY;
        }

        String server = clean(objective.getDisplayName());
        Collection<ScoreboardEntry> entries = scoreboard.getScoreboardEntries(objective);
        List<String> lines = new ArrayList<>(entries.size());
        for (ScoreboardEntry entry : entries) {
            if (entry == null || entry.hidden()) {
                continue;
            }

            Text displayText = entry.display();
            if (displayText == null) {
                Team team = scoreboard.getScoreHolderTeam(entry.owner());
                displayText = team == null ? entry.name() : team.decorateName(entry.name());
            }
            String line = clean(displayText);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        String region = ScoreboardRegionParser.findRegion(lines).orElse("Unknown");
        return new ScoreboardDetails(region, server.isBlank() ? "Unknown server" : server);
    }

    private static String clean(Text text) {
        return text == null ? "" : text.getString().trim();
    }

    private record ScoreboardDetails(String region, String server) {
        private static final ScoreboardDetails EMPTY = new ScoreboardDetails("Unknown", "Unknown server");
    }
}
