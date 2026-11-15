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
