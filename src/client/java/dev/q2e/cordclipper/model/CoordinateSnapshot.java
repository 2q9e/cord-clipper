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
        String server,
        String dimension,
        Instant capturedAt
) {
    public CoordinateSnapshot {
        region = nonBlankOr(region, "Unknown");
        server = nonBlankOr(server, "Unknown server");
        dimension = nonBlankOr(dimension, "Unknown");
        capturedAt = Objects.requireNonNullElseGet(capturedAt, Instant::now);
    }

    private static String nonBlankOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
