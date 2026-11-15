package dev.q2e.cordclipper.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.q2e.cordclipper.CordClipperClient;
import dev.q2e.cordclipper.config.CordClipperConfig;
import dev.q2e.cordclipper.config.WebhookEndpoint;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Settings screen for webhooks and the silent clipping key. */
public final class CordClipperScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_CONTENT_HEIGHT = 20;
    private static final int GLFW_UNKNOWN = -1;

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
        webhookUrlField.setHint(Component.literal("Paste Discord webhook URL"));
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
            stagedWebhooks.add(new WebhookEndpoint(UUID.randomUUID().toString(), nextWebhookLabel(), url));
            clampScrollOffset();
            firstVisibleWebhook = Math.max(0, stagedWebhooks.size() - visibleRows);
        }

        // Clear the secret immediately and never put a saved URL in rendered text.
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
            // Config failures stay out of the game UI and the stored URLs are never logged here.
        }
    }

    private void closeToParent() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private Component clipKeyMessage() {
        if (listeningForClipKey) {
            return Component.literal("Press a key…");
        }
        if (stagedClipKeyCode == GLFW_UNKNOWN) {
            return Component.translatable("screen.cord_clipper.clip_key", "Unbound");
        }

        int sdlKeyCode = CordClipperClient.glfwToSdlKey(stagedClipKeyCode);
        String displayName = sdlKeyCode == GLFW_UNKNOWN
                ? "Unbound"
                : InputConstants.Type.KEYBOARD.getOrCreate(sdlKeyCode).getDisplayName().getString();
        return Component.translatable("screen.cord_clipper.clip_key", displayName);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (listeningForClipKey) {
            listeningForClipKey = false;
            if (input.key() == InputConstants.KEY_ESCAPE) {
                stagedClipKeyCode = GLFW_UNKNOWN;
            } else if (input.key() != InputConstants.KEY_F3) {
                int glfwKeyCode = CordClipperClient.sdlToGlfwKey(input.key());
                if (glfwKeyCode != GLFW_UNKNOWN) {
                    stagedClipKeyCode = glfwKeyCode;
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
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT
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

    public void extractBackground(GuiGraphicsExtractor graphics) {
        extractMenuBackground(graphics);
    }

    /** 26.4's screen extraction signature. */
    public void extractRenderState(GuiGraphicsExtractor graphics) {
        extractScreenContent(graphics, 0, 0, 0.0F);
    }

    /** 26.3's screen extraction signature. Kept as an overload for that API line. */
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractScreenContent(graphics, mouseX, mouseY, delta);
    }

    /** 26.3 passes cursor and frame data to the screen's background extraction callback. */
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractMenuBackground(graphics);
    }

    private void extractScreenContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD01C1510);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, 0xFF000000);
        graphics.centeredText(font, Component.translatable("screen.cord_clipper.title"), width / 2,
                panelY + 15, 0xFFFFFFFF);

        Component webhookHeading = Component.translatable("screen.cord_clipper.webhooks");
        graphics.text(font, webhookHeading, panelX + 14, panelY + 128, 0xFFE0D5C9);
        graphics.text(font, "(" + stagedWebhooks.size() + ")",
                panelX + 14 + font.width(webhookHeading) + 5, panelY + 128, 0xFFAAA092);

        extractWebhookRows(graphics);
        extractWidgets(graphics, mouseX, mouseY, delta);
    }

    private void extractWidgets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        try {
            for (Method method : Renderable.class.getMethods()) {
                if (!method.getName().equals("extractRenderState")) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == 0 || parameterTypes[0] != GuiGraphicsExtractor.class) {
                    continue;
                }

                for (Object child : children()) {
                    if (!(child instanceof Renderable)) {
                        continue;
                    }
                    if (parameterTypes.length == 1) {
                        method.invoke(child, graphics);
                    } else if (parameterTypes.length == 4) {
                        method.invoke(child, graphics, mouseX, mouseY, delta);
                    }
                }
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A rendering mismatch should never leak a message into the game UI.
        }
    }

    private void extractWebhookRows(GuiGraphicsExtractor graphics) {
        if (stagedWebhooks.isEmpty()) {
            graphics.text(font, "No webhooks configured", panelX + 18, listTop + 5, 0xFFAAA092);
            return;
        }

        for (int row = 0; row < visibleRows; row++) {
            int endpointIndex = firstVisibleWebhook + row;
            if (endpointIndex >= stagedWebhooks.size()) {
                break;
            }

            int y = listTop + row * ROW_HEIGHT;
            int removeX = panelX + panelWidth - 82;
            graphics.fill(panelX + 12, y, panelX + panelWidth - 12, y + ROW_CONTENT_HEIGHT, 0x80291F19);
            graphics.outline(panelX + 12, y, panelWidth - 24, ROW_CONTENT_HEIGHT, 0xFF000000);
            graphics.text(font, "Webhook " + (endpointIndex + 1), panelX + 20, y + 6, 0xFFFFFFFF);
            graphics.fill(removeX, y + 1, removeX + 66, y + ROW_CONTENT_HEIGHT - 1, 0x7020100D);
            graphics.outline(removeX, y + 1, 66, ROW_CONTENT_HEIGHT - 2, 0xFF000000);
            graphics.centeredText(font, Component.translatable("screen.cord_clipper.remove"),
                    removeX + 33, y + 6, 0xFFFFD8D0);
        }
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
