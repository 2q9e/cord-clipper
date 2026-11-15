package dev.q2e.cordclipper.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.q2e.cordclipper.config.CordClipperConfig;
import dev.q2e.cordclipper.config.WebhookEndpoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Compact settings panel for webhook destinations and the clip key.
 * Webhook URLs are never shown after they have been added.
 */
public final class CordClipperScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_CONTENT_HEIGHT = 20;
    private static final int UNKNOWN_KEY_CODE = InputConstants.UNKNOWN.getValue();

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

    private EditBox webhookUrlField;
    private Button clipKeyButton;

    public CordClipperScreen(Screen parent, CordClipperConfig config, Runnable clipAction) {
        super(Component.translatable("screen.cord_clipper.title"));
        this.parent = parent;
        this.config = config;
        this.clipAction = clipAction;
        this.stagedWebhooks.addAll(config.getWebhooks());
        this.stagedClipKeyCode = config.getClipKeyCode();
    }

    @Override
    protected void init() {
        clearWidgets();

        panelWidth = Math.min(460, Math.max(300, width - 24));
        panelHeight = Math.min(360, Math.max(220, height - 24));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int contentX = panelX + 14;
        int contentWidth = panelWidth - 28;

        addRenderableWidget(Button.builder(
                Component.translatable("screen.cord_clipper.clip_now"),
                button -> clipNow()
        ).bounds(contentX, panelY + 38, contentWidth, 20).build());

        clipKeyButton = addRenderableWidget(Button.builder(
                clipKeyMessage(),
                button -> {
                    listeningForClipKey = true;
                    clipKeyButton.setMessage(Component.literal("Press a key…"));
                }
        ).bounds(contentX, panelY + 64, contentWidth, 20).build());

        int addButtonWidth = 78;
        int fieldWidth = contentWidth - addButtonWidth - 8;
        webhookUrlField = new EditBox(
                font,
                contentX,
                panelY + 96,
                fieldWidth,
                20,
                Component.literal("Paste Discord webhook URL")
        );
        webhookUrlField.setMaxLength(512);
        webhookUrlField.setHint(Component.translatable("screen.cord_clipper.url_hint"));
        addRenderableWidget(webhookUrlField);

        addRenderableWidget(Button.builder(
                Component.translatable("screen.cord_clipper.add_webhook"),
                button -> addWebhook()
        ).bounds(contentX + fieldWidth + 8, panelY + 96, addButtonWidth, 20).build());

        listTop = panelY + 145;
        int bottomButtonY = panelY + panelHeight - 32;
        listBottom = bottomButtonY - 9;
        visibleRows = Math.max(0, (listBottom - listTop) / ROW_HEIGHT);
        clampScrollOffset();

        int gap = 8;
        int buttonWidth = (contentWidth - gap) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("screen.cord_clipper.save"),
                button -> {
                    persistStagedSettings();
                    closeToParent();
                }
        ).bounds(contentX, bottomButtonY, buttonWidth, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.cord_clipper.cancel"),
                button -> closeToParent()
        ).bounds(contentX + buttonWidth + gap, bottomButtonY, buttonWidth, 20).build());
    }

    private void clipNow() {
        persistStagedSettings();
        if (clipAction != null) {
            clipAction.run();
        }
    }

    private void addWebhook() {
        String url = webhookUrlField.getValue().trim();
        if (!url.isBlank()) {
            String label = nextWebhookLabel();
            stagedWebhooks.add(new WebhookEndpoint(UUID.randomUUID().toString(), label, url));
            clampScrollOffset();
            firstVisibleWebhook = Math.max(0, stagedWebhooks.size() - visibleRows);
        }

        // Clear the secret immediately so it is no longer visible in the field.
        webhookUrlField.setValue("");
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
            for (WebhookEndpoint current : List.copyOf(config.getWebhooks())) {
                config.removeWebhook(current.id());
            }
            for (WebhookEndpoint staged : stagedWebhooks) {
                config.addWebhook(staged.label(), staged.url());
            }
            config.setClipKeyCode(stagedClipKeyCode);
        } catch (RuntimeException ignored) {
            // Persistence errors stay out of the game UI.
        }
    }

    private void closeToParent() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private Component clipKeyMessage() {
        if (listeningForClipKey) {
            return Component.translatable("screen.cord_clipper.set_key");
        }
        if (stagedClipKeyCode == UNKNOWN_KEY_CODE) {
            return Component.translatable("screen.cord_clipper.clip_key", "Unbound");
        }
        return Component.translatable("screen.cord_clipper.clip_key", keyName(stagedClipKeyCode));
    }

    private static String keyName(int keyCode) {
        return fromLegacyKeyCode(keyCode).getDisplayName().getString();
    }

    private static InputConstants.Key fromLegacyKeyCode(int keyCode) {
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (listeningForClipKey) {
            listeningForClipKey = false;
            int keyCode = input.key();
            if (keyCode == InputConstants.KEY_ESCAPE) {
                stagedClipKeyCode = UNKNOWN_KEY_CODE;
            } else if (keyCode != InputConstants.KEY_F3 && keyCode != UNKNOWN_KEY_CODE) {
                InputConstants.Key selectedKey = fromLegacyKeyCode(keyCode);
                if (selectedKey.getType() == InputConstants.Type.KEYSYM) {
                    stagedClipKeyCode = selectedKey.getValue();
                }
            }
            if (clipKeyButton != null) {
                clipKeyButton.setMessage(clipKeyMessage());
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == 0
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD01C1510);
        drawBorder(context, panelX, panelY, panelWidth, panelHeight, 0xFF000000);

        Component title = Component.translatable("screen.cord_clipper.title");
        context.drawString(font, title, width / 2 - font.width(title) / 2, panelY + 15, 0xFFFFFFFF, true);

        Component webhookHeading = Component.translatable("screen.cord_clipper.webhooks");
        context.drawString(font, webhookHeading, panelX + 14, panelY + 128, 0xFFE0D5C9, true);
        context.drawString(
                font,
                Component.literal("(" + stagedWebhooks.size() + ")"),
                panelX + 14 + font.width(webhookHeading) + 5,
                panelY + 128,
                0xFFAAA092,
                true
        );

        renderWebhookRows(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderWebhookRows(GuiGraphics context, int mouseX, int mouseY) {
        if (stagedWebhooks.isEmpty()) {
            context.drawString(
                    font,
                    Component.literal("No webhooks configured"),
                    panelX + 18,
                    listTop + 5,
                    0xFFAAA092,
                    true
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

            // Only a generic label is rendered; the destination URL stays hidden.
            context.drawString(
                    font,
                    Component.literal("Webhook " + (endpointIndex + 1)),
                    panelX + 20,
                    y + 6,
                    0xFFFFFFFF,
                    true
            );

            int removeX = panelX + panelWidth - 82;
            context.fill(removeX, y + 1, removeX + 66, y + ROW_CONTENT_HEIGHT - 1, 0x7020100D);
            drawBorder(context, removeX, y + 1, 66, ROW_CONTENT_HEIGHT - 2, 0xFF000000);
            context.drawCenteredString(
                    font,
                    Component.translatable("screen.cord_clipper.remove"),
                    removeX + 33,
                    y + 6,
                    0xFFFFD8D0
            );
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void drawBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
