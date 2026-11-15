# Cord Clipper

Cord Clipper is a client-side Fabric mod that captures your current block coordinates and the region shown on the client-visible scoreboard, then posts a formatted Discord embed to your configured webhook destinations.

## Minecraft targets

Each target builds its own JAR.

| Minecraft target | Java |
| --- | --- |
| 1.21.4 | 21 |
| 1.21.11 | 21 |
| 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | 25 |
| 26.4-snapshot-8 | 25 |

Support for **26.4-snapshot-8 is preview-only**. It does not promise support for future 26.4 snapshots.

## Features

- Open the settings screen with the **Open Cord Clipper** keybind in Minecraft's **Options → Controls**.
- Configure the separate clip key in the Cord Clipper screen, or use its **Clip Now** button.
- Read the local player's position without opening F3 or a screen to reveal coordinates. The mod does not send Minecraft packets to request them.
- Configure multiple Discord webhooks. Each clip sends a separate message to each configured destination.
- Send embeds with mentions disabled. Clipping has no in-game success or error notifications; failures are written to the log.
- Add no Cord Clipper cooldown or deduplication. Discord's own rate limits may delay or reject rapid sends.

## Configure

Open the Cord Clipper screen, enter each Discord webhook URL, and add it. Saved URLs are hidden from the screen afterward. They are stored in Minecraft's local **config** directory as plain text, not encrypted. Keep the config file private, and remove or replace any webhook URL you no longer trust.

## Build

Use Java 21 for the 1.21.4 and 1.21.11 targets, and Java 25 for 26.x targets. The all-target build includes Java 25 targets, so run it with Java 25.

Build every target from the repository root:

~~~powershell
.\gradlew.bat build -x test
~~~

Each target produces its own JAR in that project's **build/libs/** directory. To build one target, use its Gradle project path, for example:

~~~powershell
.\gradlew.bat :mc1214:build -x test
.\gradlew.bat :mc261:build -x test
~~~

## Fabric API compatibility

The runtime manifest uses **fabric-api: "*"**, so it does not pin the mod to one Fabric API build. The compile matrix checks the client source against every published Fabric API Maven coordinate matching the configured targets, including the separate 1.21.11 **_unobfuscated** variants.

Run the matrix from PowerShell:

~~~powershell
.\scripts\verify-fabric-api-matrix.ps1
~~~

The matrix compiles each matching coordinate; it does not launch Minecraft. Copy the JAR for the desired Minecraft target into that instance's **mods** folder and start its Fabric profile.
