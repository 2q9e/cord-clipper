package dev.q2e.cordclipper.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client-only settings persisted under {@code .minecraft/config}.
 *
 * Webhook URLs are stored so the user only has to enter them once. Callers
 * should display only endpoint labels and must never log URLs.
 */
public final class CordClipperConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("cord_clipper");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "cord-clipper.json";
    // Keep the integer key-code format written by 0.1.0 configs. Minecraft 26.x
    // uses SDL key codes, so target adapters translate at the game boundary.
    private static final int KEY_UNKNOWN = -1;
    private static final int KEY_SPACE = 32;
    private static final int KEY_G = 71;
    private static final int KEY_F3 = 292;
    private static final int KEY_LAST = 348;

    private final Path configPath;
    private final List<WebhookEndpoint> webhooks = new ArrayList<>();
    private int clipKeyCode = KEY_G;

    private CordClipperConfig(Path configPath) {
        this.configPath = configPath;
    }

    public static CordClipperConfig load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        CordClipperConfig config = new CordClipperConfig(configPath);

        if (!Files.isRegularFile(configPath)) {
            return config;
        }

        try (BufferedReader reader = Files.newBufferedReader(configPath)) {
            StoredConfig stored = GSON.fromJson(reader, StoredConfig.class);
            if (stored == null) {
                return config;
            }

            if (isValidKeyCode(stored.clipKeyCode)) {
                config.clipKeyCode = stored.clipKeyCode;
            }

            if (stored.webhooks != null) {
                for (StoredWebhook entry : stored.webhooks) {
                    if (entry == null || entry.url == null || entry.url.isBlank()) {
                        continue;
                    }

                    String id = entry.id == null || entry.id.isBlank()
                            ? UUID.randomUUID().toString()
                            : entry.id;
                    String label = entry.label == null || entry.label.isBlank()
                            ? "Webhook " + (config.webhooks.size() + 1)
                            : entry.label;

                    try {
                        config.webhooks.add(new WebhookEndpoint(id, label, entry.url.trim()));
                    } catch (RuntimeException ignored) {
                        // Skip an invalid entry without exposing its secret URL.
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            LOGGER.warn("Could not read Cord Clipper settings. Defaults will be used.");
        }

        return config;
    }

    public synchronized List<WebhookEndpoint> getWebhooks() {
        return List.copyOf(webhooks);
    }

    /**
     * Adds a destination and saves it immediately. The caller should clear
     * its text field after calling this method and should never render the URL
     * again.
     */
    public synchronized void addWebhook(String label, String url) {
        if (url == null || url.isBlank()) {
            return;
        }

        String safeLabel = label == null || label.isBlank()
                ? "Webhook " + (webhooks.size() + 1)
                : label.trim();
        webhooks.add(new WebhookEndpoint(UUID.randomUUID().toString(), safeLabel, url.trim()));
        save();
    }

    public synchronized boolean removeWebhook(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        boolean removed = webhooks.removeIf(endpoint -> endpoint.id().equals(id));
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized int getClipKeyCode() {
        return clipKeyCode;
    }

    public synchronized void setClipKeyCode(int keyCode) {
        if (!isValidKeyCode(keyCode)) {
            return;
        }

        if (clipKeyCode != keyCode) {
            clipKeyCode = keyCode;
            save();
        }
    }

    private synchronized void save() {
        Path parent = configPath.getParent();
        Path temporaryPath = configPath.resolveSibling(FILE_NAME + ".tmp");
        StoredConfig stored = new StoredConfig();
        stored.clipKeyCode = clipKeyCode;
        stored.webhooks = new ArrayList<>(webhooks.size());
        for (WebhookEndpoint endpoint : webhooks) {
            StoredWebhook entry = new StoredWebhook();
            entry.id = endpoint.id();
            entry.label = endpoint.label();
            entry.url = endpoint.url();
            stored.webhooks.add(entry);
        }

        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(temporaryPath)) {
                GSON.toJson(stored, writer);
            }

            try {
                Files.move(temporaryPath, configPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException ignored) {
            try {
                Files.deleteIfExists(temporaryPath);
            } catch (IOException cleanupIgnored) {
                // Keep failures silent in-game; diagnostics contain no URL.
            }
            LOGGER.warn("Could not save Cord Clipper settings.");
        }
    }

    private static boolean isValidKeyCode(int keyCode) {
        return keyCode == KEY_UNKNOWN
                || (keyCode >= KEY_SPACE
                && keyCode <= KEY_LAST
                && keyCode != KEY_F3);
    }

    private static final class StoredConfig {
        private int clipKeyCode = KEY_G;
        private List<StoredWebhook> webhooks = new ArrayList<>();
    }

    private static final class StoredWebhook {
        private String id;
        private String label;
        private String url;
    }
}
