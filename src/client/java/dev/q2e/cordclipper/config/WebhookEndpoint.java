package dev.q2e.cordclipper.config;

import java.util.Objects;

/**
 * A locally stored Discord destination. The URL is sensitive and must never be
 * written to logs or rendered back into the settings screen.
 */
public record WebhookEndpoint(String id, String label, String url) {
    public WebhookEndpoint {
        id = Objects.requireNonNull(id, "id");
        label = label == null || label.isBlank() ? "Webhook" : label;
        url = Objects.requireNonNull(url, "url");
    }
}
