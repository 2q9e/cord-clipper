package dev.q2e.cordclipper.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A point-in-time snapshot of values already available to the Minecraft client.
 */
public record CoordinateSnapshot(
        int x,
        int y,
        int z,
        String region,
