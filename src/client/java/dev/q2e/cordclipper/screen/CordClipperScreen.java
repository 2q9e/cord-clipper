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
        super(Text.translatable("screen.cord_clipper.title"));
        this.parent = parent;
        this.config = config;
        this.clipAction = clipAction;
        this.stagedWebhooks.addAll(config.getWebhooks());
        this.stagedClipKeyCode = config.getClipKeyCode();
    }

    @Override
    protected void init() {
        clearChildren();

        panelWidth = Math.min(460, Math.max(300, width - 24));
        panelHeight = Math.min(360, Math.max(220, height - 24));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int contentX = panelX + 14;
        int contentWidth = panelWidth - 28;

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.cord_clipper.clip_now"),
                button -> clipNow()
        ).dimensions(contentX, panelY + 38, contentWidth, 20).build());

        clipKeyButton = addDrawableChild(ButtonWidget.builder(
                clipKeyMessage(),
                button -> {
                    listeningForClipKey = true;
                    clipKeyButton.setMessage(Text.literal("Press a key…"));
                }
        ).dimensions(contentX, panelY + 64, contentWidth, 20).build());

        int addButtonWidth = 78;
        int fieldWidth = contentWidth - addButtonWidth - 8;
        webhookUrlField = new TextFieldWidget(
                textRenderer,
                contentX,
                panelY + 96,
                fieldWidth,
                20,
                Text.literal("Paste Discord webhook URL")
        );
        webhookUrlField.setMaxLength(512);
        webhookUrlField.setPlaceholder(Text.literal("Paste Discord webhook URL"));
        addDrawableChild(webhookUrlField);

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.cord_clipper.add_webhook"),
                button -> addWebhook()
        ).dimensions(contentX + fieldWidth + 8, panelY + 96, addButtonWidth, 20).build());

        listTop = panelY + 145;
        int bottomButtonY = panelY + panelHeight - 32;
        listBottom = bottomButtonY - 9;
        visibleRows = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        clampScrollOffset();

        int gap = 8;
        int buttonWidth = (contentWidth - gap) / 2;
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.cord_clipper.save"),
                button -> {
                    persistStagedSettings();
                    closeToParent();
                }
        ).dimensions(contentX, bottomButtonY, buttonWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("screen.cord_clipper.cancel"),
                button -> closeToParent()
        ).dimensions(contentX + buttonWidth + gap, bottomButtonY, buttonWidth, 20).build());
    }

    private void clipNow() {
        persistStagedSettings();
        if (clipAction != null) {
            clipAction.run();
        }
    }

    private void addWebhook() {
        String url = webhookUrlField.getText().trim();
        if (!url.isBlank()) {
            String label = nextWebhookLabel();
            stagedWebhooks.add(new WebhookEndpoint(UUID.randomUUID().toString(), label, url));
            clampScrollOffset();
            firstVisibleWebhook = Math.max(0, stagedWebhooks.size() - visibleRows);
        }

        // Do not leave a saved webhook URL on screen after it is added.
        webhookUrlField.setText("");
    }

    private String nextWebhookLabel() {
        Set<String> labels = new HashSet<>();
        for (WebhookEndpoint endpoint : stagedWebhooks) {
            labels.add(endpoint.label());
        }

        int number = 1;
        while (labels.contains("Webhook " + number)) {
            number++;
        }
