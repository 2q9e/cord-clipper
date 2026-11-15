package dev.q2e.cordclipper.config;

import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
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

    private final Path configPath;
    private final List<WebhookEndpoint> webhooks = new ArrayList<>();
    private int clipKeyCode = GLFW.GLFW_KEY_G;

    private CordClipperConfig(Path configPath) {
        this.configPath = configPath;
    }
