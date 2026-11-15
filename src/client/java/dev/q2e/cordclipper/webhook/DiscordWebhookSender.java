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
                                );
                            } else {
                                LOGGER.warn("Discord rate limited a webhook without a usable retry time");
                            }
                            return;
                        }

                        LOGGER.warn("Discord webhook delivery failed with HTTP status {}", status);
                    });
        } catch (RuntimeException exception) {
            // Scheduling failures are logged without exposing the configured URL.
            LOGGER.warn("Could not schedule Discord webhook delivery ({})",
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * Accept only HTTPS Discord webhook endpoints. This prevents a locally
     * edited config from turning the coordinate sender into an arbitrary HTTP client.
     */
    private static URI validatedWebhookUri(String rawUrl) {
        URI uri = URI.create(rawUrl);
        String host = uri.getHost();
        String path = uri.getRawPath();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawFragment() != null || !isDiscordHost(host)
                || path == null
                || !path.matches("/api(?:/v[0-9]+)?/webhooks/[^/]+/[^/]+/?")) {
            throw new IllegalArgumentException("Not a Discord webhook URL");
        }

        String query = uri.getRawQuery();
        String preservedQuery = query == null ? "" : java.util.Arrays.stream(query.split("&"))
                .filter(part -> !part.toLowerCase(Locale.ROOT).startsWith("wait="))
                .filter(part -> !part.isBlank())
                .collect(java.util.stream.Collectors.joining("&"));
        String requestQuery = preservedQuery.isBlank() ? "wait=true" : preservedQuery + "&wait=true";
        String requestUrl = uri.getScheme() + "://" + uri.getRawAuthority()
                + uri.getRawPath() + "?" + requestQuery;
        return URI.create(requestUrl);
    }

    private static boolean isDiscordHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals("discord.com")
                || normalizedHost.endsWith(".discord.com")
                || normalizedHost.equals("discordapp.com")
                || normalizedHost.endsWith(".discordapp.com");
    }

    private static Long retryAfterMillis(HttpResponse<String> response) {
        String header = response.headers().firstValue("Retry-After").orElse(null);
        Long fromHeader = parseRetryAfter(header);
        if (fromHeader != null) {
            return fromHeader;
        }

        Long fromResetHeader = parseRetryAfter(
                response.headers().firstValue("X-RateLimit-Reset-After").orElse(null)
        );
        if (fromResetHeader != null) {
            return fromResetHeader;
        }

        try {
            JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (body.has("retry_after") && body.get("retry_after").isJsonPrimitive()) {
                double seconds = body.get("retry_after").getAsDouble();
                if (Double.isFinite(seconds) && seconds >= 0) {
                    return Math.max(1L, (long) Math.ceil(seconds * 1000.0));
                }
            }
        } catch (RuntimeException ignored) {
            // A 429 still gets retried if Discord omits a parseable delay.
        }

        return null;
    }

    private static Long parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            double seconds = Double.parseDouble(value.trim());
            if (Double.isFinite(seconds) && seconds >= 0) {
                return Math.max(1L, (long) Math.ceil(seconds * 1000.0));
            }
        } catch (NumberFormatException ignored) {
            // Retry-After may also be an HTTP date.
        }

        try {
            Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Math.max(1L, Duration.between(Instant.now(), retryAt).toMillis());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static JsonObject createPayload(CoordinateSnapshot snapshot) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Coordinates clipped");
        embed.addProperty("color", 0x8B5CF6);
        embed.addProperty("timestamp", snapshot.capturedAt().toString());

        JsonArray fields = new JsonArray();
        addField(fields, "Coordinates (X, Y, Z)",
                "(" + snapshot.x() + ", " + snapshot.y() + ", " + snapshot.z() + ")", false);
        addField(fields, "Server", safeField(snapshot.server()), true);
        addField(fields, "Region", safeField(snapshot.region()), true);
        addField(fields, "Dimension", safeField(snapshot.dimension()), true);
        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Cord Clipper");
        embed.add("footer", footer);

        JsonObject payload = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        // Disable parsing for user, role, @everyone and @here mentions.
        JsonObject allowedMentions = new JsonObject();
        allowedMentions.add("parse", new JsonArray());
        allowedMentions.add("users", new JsonArray());
        allowedMentions.add("roles", new JsonArray());
        allowedMentions.addProperty("replied_user", false);
        payload.add("allowed_mentions", allowedMentions);

        // Discord's SUPPRESS_NOTIFICATIONS message flag is bit 12.
        payload.addProperty("flags", SUPPRESS_NOTIFICATIONS_FLAG);
        return payload;
    }

    private static void addField(JsonArray fields, String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
