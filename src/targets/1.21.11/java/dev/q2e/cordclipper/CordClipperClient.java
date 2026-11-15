package dev.q2e.cordclipper;

import dev.q2e.cordclipper.capture.ClientStateReader;
import dev.q2e.cordclipper.config.CordClipperConfig;
import dev.q2e.cordclipper.model.CoordinateSnapshot;
import dev.q2e.cordclipper.screen.CordClipperScreen;
import dev.q2e.cordclipper.webhook.DiscordWebhookSender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class CordClipperClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("cord_clipper");
    private static final KeyBinding.Category KEY_CATEGORY =
            KeyBinding.Category.create(Identifier.of("cord_clipper", "main"));

    private static CordClipperConfig config;
    private static KeyBinding openScreenKey;
    private static boolean clipKeyWasDown;

    @Override
    public void onInitializeClient() {
        config = CordClipperConfig.load();
        openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cord_clipper.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(CordClipperClient::onClientTick);
        LOGGER.info("Cord Clipper initialized.");
    }

    private static void onClientTick(MinecraftClient client) {
        while (openScreenKey.wasPressed()) {
            if (client.player != null) {
                client.setScreen(new CordClipperScreen(
                        client.currentScreen,
                        config,
                        () -> clipCurrentCoordinates(client)
                ));
            }
        }

        int clipKeyCode = config.getClipKeyCode();
        boolean clipKeyDown = clipKeyCode != GLFW.GLFW_KEY_UNKNOWN
                && clipKeyCode != GLFW.GLFW_KEY_F3
                && InputUtil.isKeyPressed(client.getWindow(), clipKeyCode);
        if (clipKeyDown && !clipKeyWasDown
                && client.currentScreen == null
                && client.player != null) {
            clipCurrentCoordinates(client);
        }
        clipKeyWasDown = clipKeyDown;
    }

    private static void clipCurrentCoordinates(MinecraftClient client) {
        try {
            Optional<CoordinateSnapshot> snapshot = ClientStateReader.capture(client);
            snapshot.ifPresent(coordinates ->
                    DiscordWebhookSender.sendAll(config.getWebhooks(), coordinates)
            );
        } catch (RuntimeException exception) {
            // Clipping is intentionally silent. Keep diagnostics out of the
            // game UI and avoid logging any payload or webhook credential.
            LOGGER.warn("Could not capture or submit clipped coordinates.");
        }
    }
}
