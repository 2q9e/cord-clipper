package dev.q2e.cordclipper;

import com.mojang.blaze3d.platform.InputConstants;
import dev.q2e.cordclipper.capture.ClientStateReader;
import dev.q2e.cordclipper.config.CordClipperConfig;
import dev.q2e.cordclipper.model.CoordinateSnapshot;
import dev.q2e.cordclipper.screen.CordClipperScreen;
import dev.q2e.cordclipper.webhook.DiscordWebhookSender;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public final class CordClipperClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("cord_clipper");
    private static final int LEGACY_UNKNOWN_KEY_CODE = InputConstants.UNKNOWN.getValue();

    private static CordClipperConfig config;
    private static KeyMapping openScreenKey;
    private static boolean clipKeyWasDown;

    @Override
    public void onInitializeClient() {
        config = CordClipperConfig.load();
        openScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cord_clipper.open",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_O,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(CordClipperClient::onClientTick);
        LOGGER.info("Cord Clipper initialized.");
    }

    private static void onClientTick(Minecraft client) {
        try {
            while (openScreenKey.consumeClick()) {
                if (client.player != null) {
                    client.gui.setScreen(new CordClipperScreen(
                            client.gui.screen(),
                            config,
                            () -> clipCurrentCoordinates(client)
                    ));
                }
            }

            int clipKeyCode = config.getClipKeyCode();
            InputConstants.Key clipKey = clipKeyCode == LEGACY_UNKNOWN_KEY_CODE
                    ? InputConstants.UNKNOWN
                    : InputConstants.Type.KEYSYM.getOrCreate(clipKeyCode);
            boolean clipKeyDown = clipKeyCode != LEGACY_UNKNOWN_KEY_CODE
                    && clipKeyCode != InputConstants.KEY_F3
                    && InputConstants.isKeyDown(client.getWindow(), clipKey.getValue());
            if (clipKeyDown && !clipKeyWasDown
                    && client.gui.screen() == null
                    && client.player != null) {
                clipCurrentCoordinates(client);
            }
            clipKeyWasDown = clipKeyDown;
        } catch (RuntimeException exception) {
            // Clipping and input handling never surface an error in the game UI.
            LOGGER.warn("Could not process Cord Clipper input.");
        }
    }

    private static void clipCurrentCoordinates(Minecraft client) {
        try {
            Optional<CoordinateSnapshot> snapshot = ClientStateReader.capture(client);
            snapshot.ifPresent(coordinates ->
                    DiscordWebhookSender.sendAll(config.getWebhooks(), coordinates)
            );
        } catch (RuntimeException exception) {
            // Keep diagnostics out of the game UI and never log payloads or webhook URLs.
            LOGGER.warn("Could not capture or submit clipped coordinates.");
        }
    }
}
