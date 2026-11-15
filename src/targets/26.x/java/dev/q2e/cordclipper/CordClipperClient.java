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

/** Client-only entry point for the non-remapped 26.x source set. */
public final class CordClipperClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("cord_clipper");
    private static final int GLFW_UNKNOWN = -1;
    private static final int GLFW_F3 = 292;

    private static CordClipperConfig config;
    private static KeyMapping openScreenKey;
    private static boolean clipKeyWasDown;

    @Override
    public void onInitializeClient() {
        config = CordClipperConfig.load();
        openScreenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cord_clipper.open",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_O,
                KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(CordClipperClient::onClientTick);
        LOGGER.info("Cord Clipper initialized.");
    }

    private static void onClientTick(Minecraft client) {
        if (client == null) {
            return;
        }

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

            int sdlKeyCode = glfwToSdlKey(config.getClipKeyCode());
            boolean clipKeyDown = sdlKeyCode != GLFW_UNKNOWN
                    && config.getClipKeyCode() != GLFW_F3
                    && InputConstants.isKeyDown(sdlKeyCode);
            if (clipKeyDown && !clipKeyWasDown
                    && client.gui.screen() == null
                    && client.player != null) {
                clipCurrentCoordinates(client);
            }
            clipKeyWasDown = clipKeyDown;
        } catch (RuntimeException exception) {
            clipKeyWasDown = false;
            LOGGER.warn("Could not process Cord Clipper input ({})", exception.getClass().getSimpleName());
        }
    }

    private static void clipCurrentCoordinates(Minecraft client) {
        try {
            Optional<CoordinateSnapshot> snapshot = ClientStateReader.capture(client);
            snapshot.ifPresent(coordinates ->
                    DiscordWebhookSender.sendAll(config.getWebhooks(), coordinates)
            );
        } catch (RuntimeException exception) {
            // Clipping is silent in-game. Log only a fixed message, never payloads or webhook credentials.
            LOGGER.warn("Could not capture or submit clipped coordinates.");
        }
    }

    /** Converts the persisted GLFW key code into the SDL scancode used by Minecraft 26.x. */
    public static int glfwToSdlKey(int keyCode) {
        if (keyCode >= 65 && keyCode <= 90) {
            return keyCode - 61; // A-Z -> SDL scancodes 4-29.
        }
        if (keyCode == 48) {
            return 39;
        }
        if (keyCode >= 49 && keyCode <= 57) {
            return keyCode - 19; // 1-9 -> SDL scancodes 30-38.
        }
        if (keyCode >= 290 && keyCode <= 301) {
            return keyCode - 232; // F1-F12.
        }
        if (keyCode >= 302 && keyCode <= 313) {
            return keyCode - 198; // F13-F24.
        }
        if (keyCode >= 321 && keyCode <= 329) {
            return keyCode - 232; // Keypad 1-9.
        }

        return switch (keyCode) {
            case 32 -> 44;  // Space
            case 39 -> 52;  // Apostrophe
            case 44 -> 54;  // Comma
            case 45 -> 45;  // Minus
            case 46 -> 55;  // Period
            case 47 -> 56;  // Slash
            case 59 -> 51;  // Semicolon
            case 61 -> 46;  // Equal
            case 91 -> 47;  // Left bracket
            case 92 -> 49;  // Backslash
            case 93 -> 48;  // Right bracket
            case 96 -> 53;  // Grave accent
            case 161 -> 135; // World 1 / international 1
            case 162 -> 136; // World 2 / international 2
            case 256 -> InputConstants.KEY_ESCAPE;
            case 257 -> InputConstants.KEY_RETURN;
            case 258 -> InputConstants.KEY_TAB;
            case 259 -> InputConstants.KEY_BACKSPACE;
            case 260 -> InputConstants.KEY_INSERT;
            case 261 -> InputConstants.KEY_DELETE;
            case 262 -> InputConstants.KEY_RIGHT;
            case 263 -> InputConstants.KEY_LEFT;
            case 264 -> InputConstants.KEY_DOWN;
            case 265 -> InputConstants.KEY_UP;
            case 266 -> InputConstants.KEY_PAGEUP;
            case 267 -> InputConstants.KEY_PAGEDOWN;
            case 268 -> InputConstants.KEY_HOME;
            case 269 -> InputConstants.KEY_END;
            case 280 -> InputConstants.KEY_CAPSLOCK;
            case 281 -> InputConstants.KEY_SCROLLLOCK;
            case 282 -> InputConstants.KEY_NUMLOCK;
            case 283 -> InputConstants.KEY_PRINTSCREEN;
            case 284 -> InputConstants.KEY_PAUSE;
            case 320 -> InputConstants.KEY_NUMPAD0;
            case 330 -> 99; // Keypad decimal point
            case 331 -> 84; // Keypad divide
            case 332 -> InputConstants.KEY_MULTIPLY;
            case 333 -> 86; // Keypad subtract
            case 334 -> InputConstants.KEY_ADD;
            case 335 -> InputConstants.KEY_NUMPADENTER;
            case 336 -> InputConstants.KEY_NUMPADEQUALS;
            case 340 -> InputConstants.KEY_LSHIFT;
            case 341 -> InputConstants.KEY_LCONTROL;
            case 342 -> InputConstants.KEY_LALT;
            case 343 -> InputConstants.KEY_LGUI;
            case 344 -> InputConstants.KEY_RSHIFT;
            case 345 -> InputConstants.KEY_RCONTROL;
            case 346 -> InputConstants.KEY_RALT;
            case 347 -> InputConstants.KEY_RGUI;
            case 348 -> 118; // Application / menu
            default -> GLFW_UNKNOWN;
        };
    }

    /** Converts SDL scancodes captured in the GUI back to the format shared settings persist. */
    public static int sdlToGlfwKey(int keyCode) {
        if (keyCode >= 4 && keyCode <= 29) {
            return keyCode + 61; // SDL scancodes A-Z -> GLFW key codes.
        }
        if (keyCode >= 30 && keyCode <= 38) {
            return keyCode + 19; // SDL scancodes 1-9.
        }
        if (keyCode == 39) {
            return 48;
        }
        if (keyCode >= 58 && keyCode <= 69) {
            return keyCode + 232; // F1-F12.
        }
        if (keyCode >= 104 && keyCode <= 115) {
            return keyCode + 198; // F13-F24.
        }
        if (keyCode >= 89 && keyCode <= 97) {
            return keyCode + 232; // Keypad 1-9.
        }
        if (keyCode == InputConstants.KEY_NUMPAD0) {
            return 320;
        }

        return switch (keyCode) {
            case 44 -> 32;
            case 52 -> 39;
            case 54 -> 44;
            case 45 -> 45;
            case 55 -> 46;
            case 56 -> 47;
            case 51 -> 59;
            case 46 -> 61;
            case 47 -> 91;
            case 49 -> 92;
            case 48 -> 93;
            case 53 -> 96;
            case 135 -> 161;
            case 136 -> 162;
            case InputConstants.KEY_ESCAPE -> 256;
            case InputConstants.KEY_RETURN -> 257;
            case InputConstants.KEY_TAB -> 258;
            case InputConstants.KEY_BACKSPACE -> 259;
            case InputConstants.KEY_INSERT -> 260;
            case InputConstants.KEY_DELETE -> 261;
            case InputConstants.KEY_RIGHT -> 262;
            case InputConstants.KEY_LEFT -> 263;
            case InputConstants.KEY_DOWN -> 264;
            case InputConstants.KEY_UP -> 265;
            case InputConstants.KEY_PAGEUP -> 266;
            case InputConstants.KEY_PAGEDOWN -> 267;
            case InputConstants.KEY_HOME -> 268;
            case InputConstants.KEY_END -> 269;
            case InputConstants.KEY_CAPSLOCK -> 280;
            case InputConstants.KEY_SCROLLLOCK -> 281;
            case InputConstants.KEY_NUMLOCK -> 282;
            case InputConstants.KEY_PRINTSCREEN -> 283;
            case InputConstants.KEY_PAUSE -> 284;
            case 84 -> 331;
            case 86 -> 333;
            case InputConstants.KEY_MULTIPLY -> 332;
            case InputConstants.KEY_ADD -> 334;
            case InputConstants.KEY_NUMPADENTER -> 335;
            case InputConstants.KEY_NUMPADEQUALS -> 336;
            case 99 -> 330; // Keypad decimal point
            case InputConstants.KEY_LSHIFT -> 340;
            case InputConstants.KEY_LCONTROL -> 341;
            case InputConstants.KEY_LALT -> 342;
            case InputConstants.KEY_LGUI -> 343;
            case InputConstants.KEY_RSHIFT -> 344;
            case InputConstants.KEY_RCONTROL -> 345;
            case InputConstants.KEY_RALT -> 346;
            case InputConstants.KEY_RGUI -> 347;
            case 118 -> 348;
            default -> GLFW_UNKNOWN;
        };
    }
}
