package dev.q2e.cordclipper.webhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.q2e.cordclipper.config.WebhookEndpoint;
import dev.q2e.cordclipper.model.CoordinateSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends coordinate embeds without blocking Minecraft's client thread.
 *
 * <p>Every invocation submits a distinct request for each configured endpoint.
 * This class deliberately adds no cooldown, debounce, or deduplication.</p>
 */
public final class DiscordWebhookSender {
    private static final Logger LOGGER = LoggerFactory.getLogger("cord_clipper");
    private static final int SUPPRESS_NOTIFICATIONS_FLAG = 1 << 12;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final ScheduledExecutorService RETRY_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Cord Clipper webhook retries");
                thread.setDaemon(true);
                return thread;
            });

    private DiscordWebhookSender() {
    }

    /**
     * Submits one independent webhook message for every configured destination.
     * This method is safe to call from the Minecraft client thread.
     */
    public static void sendAll(List<WebhookEndpoint> endpoints, CoordinateSnapshot snapshot) {
        if (endpoints == null || endpoints.isEmpty() || snapshot == null) {
            return;
        }

        String body = createPayload(snapshot).toString();
        for (WebhookEndpoint endpoint : List.copyOf(endpoints)) {
            if (endpoint != null) {
                send(endpoint, body);
            }
        }
    }

    private static void send(WebhookEndpoint endpoint, String body) {
        URI uri;
        try {
            uri = validatedWebhookUri(endpoint.url());
        } catch (RuntimeException exception) {
            // Avoid logging exception messages because URI parsing errors may contain the secret URL.
            LOGGER.warn("Skipped a configured Discord webhook because its URL is invalid");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            // Exception messages can embed the request URI, so log only its type.
                            LOGGER.warn("Discord webhook delivery failed ({})",
                                    error.getClass().getSimpleName());
                            return;
                        }

                        int status = response.statusCode();
                        if (status >= 200 && status < 300) {
                            return;
                        }

                        if (status == 429) {
                            Long retryAfterMillis = retryAfterMillis(response);
                            if (retryAfterMillis != null) {
                                RETRY_SCHEDULER.schedule(
                                        () -> send(endpoint, body),
                                        retryAfterMillis,
                                        TimeUnit.MILLISECONDS
