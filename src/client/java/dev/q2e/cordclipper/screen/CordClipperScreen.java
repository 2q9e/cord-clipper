package dev.q2e.cordclipper.screen;

import dev.q2e.cordclipper.config.CordClipperConfig;
import dev.q2e.cordclipper.config.WebhookEndpoint;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Compact settings panel for webhook destinations and the clip key.
 *
 * <p>Webhook URLs are accepted in the text box and retained only in memory
 * until Save is selected. They are never included in labels, previews, or
 * other rendered text.</p>
 */
public final class CordClipperScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_CONTENT_HEIGHT = 20;

    private final Screen parent;
    private final CordClipperConfig config;
    private final Runnable clipAction;
    private final List<WebhookEndpoint> stagedWebhooks = new ArrayList<>();

    private int stagedClipKeyCode;
    private int firstVisibleWebhook;
    private boolean listeningForClipKey;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listTop;
    private int listBottom;
    private int visibleRows;

    private TextFieldWidget webhookUrlField;
    private ButtonWidget clipKeyButton;

    public CordClipperScreen(Screen parent, CordClipperConfig config, Runnable clipAction) {
