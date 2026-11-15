# Cord Clipper

Cord Clipper is a client-side Fabric mod for Minecraft 1.21.11. It captures your current block coordinates and the region shown on the client-visible scoreboard, then posts a formatted Discord embed to your configured webhook destinations.

## Features

- Open the settings screen with the **Open Cord Clipper** keybind in Minecraft's **Options → Controls**.
- Configure the separate clip key in the Cord Clipper screen, or use its **Clip Now** button.
- Read the local player's position without opening F3 or a screen to reveal coordinates. The mod does not send Minecraft packets to request them.
- Configure multiple Discord webhooks. Each clip sends a separate message to each configured destination.
- Send embeds with mentions disabled. Clipping has no in-game success or error notifications; failures are written to the log.
- Add no Cord Clipper cooldown or deduplication. Discord's own rate limits may delay or reject rapid sends.

## Configure

Open the Cord Clipper screen, enter each Discord webhook URL, and add it. Saved URLs are hidden from the screen afterward. They are stored in Minecraft's local **config** directory as plain text, not encrypted. Keep the config file private, and remove or replace any webhook URL you no longer trust.

## Build and install

Use Java 21 or newer with Fabric Loader and Fabric API for Minecraft 1.21.11.
The mod metadata does not pin a Fabric API version; use a Fabric API build for Minecraft 1.21.11.

Build from the repository root:

~~~powershell
.\gradlew.bat build
