# Cord Clipper

Cord Clipper is a client-side Fabric mod for Minecraft 1.21.11. It captures your current block coordinates and the region shown on the client-visible scoreboard, then posts a formatted Discord embed to your configured webhook destinations.

## Features

- Open the settings screen with the **Open Cord Clipper** keybind in Minecraft's **Options → Controls**.
- Configure the separate clip key in the Cord Clipper screen, or use its **Clip Now** button.
- Read the local player's position without opening F3 or a screen to reveal coordinates. The mod does not send Minecraft packets to request them.
- Configure multiple Discord webhooks. Each clip sends a separate message to each configured destination.
- Send embeds with mentions disabled. Clipping has no in-game success or error notifications; failures are written to the log.
- Add no Cord Clipper cooldown or deduplication. Discord's own rate limits may delay or reject rapid sends.

