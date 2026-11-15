package dev.q2e.cordclipper.capture;

import dev.q2e.cordclipper.model.CoordinateSnapshot;
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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads values already available in the local client state. It does not open
 * the debug screen or ask the game server for coordinates or scoreboard data.
 */
public final class ClientStateReader {
    private static final Pattern REGION_AT_END_OF_PING_LINE = Pattern.compile(
            "(?i)(?:\\bping\\b|\\b\\d+(?:\\.\\d+)?\\s*ms\\b).*?\\(([a-z]{2,5})\\)\\s*$"
    );

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
        ScoreboardDetails scoreboardDetails = readSidebar(client.level.getScoreboard());

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

        String region = findRegion(lines).orElse("Unknown");
        return new ScoreboardDetails(region, server.isBlank() ? "Unknown server" : server);
    }

    private static Optional<String> findRegion(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = REGION_AT_END_OF_PING_LINE.matcher(line);
            if (matcher.find()) {
                return Optional.of(matcher.group(1).toUpperCase(Locale.ROOT));
            }
        }
        return Optional.empty();
    }

    private static String clean(Component component) {
        return component == null ? "" : component.getString().trim();
    }

    private record ScoreboardDetails(String region, String server) {
        private static final ScoreboardDetails EMPTY = new ScoreboardDetails("Unknown", "Unknown server");
    }
}
