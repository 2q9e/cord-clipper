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
        return "Webhook " + number;
    }

    private void persistStagedSettings() {
        try {
            config.replaceSettings(stagedWebhooks, stagedClipKeyCode);
        } catch (RuntimeException ignored) {
            // All failures stay out of the game UI. Config persistence handles
            // diagnostics in logs, and the player is never shown an error here.
        }
    }

    private void closeToParent() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private Text clipKeyMessage() {
        if (listeningForClipKey) {
            return Text.literal("Press a key…");
        }
        if (stagedClipKeyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return Text.translatable("screen.cord_clipper.clip_key", "Unbound");
        }
        return Text.translatable("screen.cord_clipper.clip_key", keyName(stagedClipKeyCode));
    }

    private static String keyName(int keyCode) {
        return InputUtil.Type.KEYSYM.createFromCode(keyCode).getLocalizedText().getString();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listeningForClipKey) {
            listeningForClipKey = false;
            int keyCode = input.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                stagedClipKeyCode = GLFW.GLFW_KEY_UNKNOWN;
            } else if (keyCode != GLFW.GLFW_KEY_F3 && keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                stagedClipKeyCode = keyCode;
            }
            if (clipKeyButton != null) {
                clipKeyButton.setMessage(clipKeyMessage());
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_1
                && mouseX >= panelX + 8 && mouseX <= panelX + panelWidth - 8
                && mouseY >= listTop && mouseY < listBottom) {
            int visibleIndex = (int) ((mouseY - listTop) / ROW_HEIGHT);
            int endpointIndex = firstVisibleWebhook + visibleIndex;
            if (visibleIndex < visibleRows && endpointIndex < stagedWebhooks.size()
                    && mouseX >= panelX + panelWidth - 82) {
                stagedWebhooks.remove(endpointIndex);
                clampScrollOffset();
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= panelX && mouseX <= panelX + panelWidth
                && mouseY >= listTop && mouseY < listBottom
                && stagedWebhooks.size() > visibleRows) {
            firstVisibleWebhook -= (int) Math.signum(verticalAmount);
            clampScrollOffset();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void clampScrollOffset() {
        int max = Math.max(0, stagedWebhooks.size() - visibleRows);
        firstVisibleWebhook = Math.max(0, Math.min(firstVisibleWebhook, max));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD01C1510);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF000000);

        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("screen.cord_clipper.title"),
                width / 2,
                panelY + 15,
                0xFFFFFFFF
        );

        Text webhookHeading = Text.translatable("screen.cord_clipper.webhooks");
        context.drawTextWithShadow(textRenderer, webhookHeading, panelX + 14, panelY + 128, 0xFFE0D5C9);
        context.drawTextWithShadow(
                textRenderer,
                Text.literal("(" + stagedWebhooks.size() + ")"),
                panelX + 14 + textRenderer.getWidth(webhookHeading) + 5,
                panelY + 128,
                0xFFAAA092
        );

        renderWebhookRows(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderWebhookRows(DrawContext context, int mouseX, int mouseY) {
        if (stagedWebhooks.isEmpty()) {
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("No webhooks configured"),
                    panelX + 18,
                    listTop + 5,
                    0xFFAAA092
            );
            return;
        }

        for (int row = 0; row < visibleRows; row++) {
            int endpointIndex = firstVisibleWebhook + row;
            if (endpointIndex >= stagedWebhooks.size()) {
                break;
            }

            int y = listTop + row * ROW_HEIGHT;
            boolean hovered = mouseX >= panelX + 12 && mouseX <= panelX + panelWidth - 12
                    && mouseY >= y && mouseY < y + ROW_CONTENT_HEIGHT;
            int rowColor = hovered ? 0xA03B2B20 : 0x80291F19;
            context.fill(panelX + 12, y, panelX + panelWidth - 12, y + ROW_CONTENT_HEIGHT, rowColor);
            drawBorder(context, panelX + 12, y, panelWidth - 24, ROW_CONTENT_HEIGHT, 0xFF000000);

            // Only the generic endpoint label is displayed; the URL is never rendered.
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal("Webhook " + (endpointIndex + 1)),
                    panelX + 20,
                    y + 6,
                    0xFFFFFFFF
            );

            int removeX = panelX + panelWidth - 82;
            context.fill(removeX, y + 1, removeX + 66, y + ROW_CONTENT_HEIGHT - 1, 0x7020100D);
            drawBorder(context, removeX, y + 1, 66, ROW_CONTENT_HEIGHT - 2, 0xFF000000);
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.translatable("screen.cord_clipper.remove"),
                    removeX + 33,
                    y + 6,
                    0xFFFFD8D0
            );
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
