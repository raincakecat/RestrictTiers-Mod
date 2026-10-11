# RestrictTiers client mod

Fabric client mod for Minecraft **1.21.11**. Run `/check <IGN or UUID>` or `/restrictcheck <IGN or UUID>` to get private restriction counts, a clickable website profile, and cached parsed details. No automatic checks or permission requirement.

## License

**All Rights Reserved.** You may install and run official, unmodified JARs on your clients and servers. Reusing, modifying, or redistributing the original code or distributing builds requires prior written permission, subject to the GitHub and third-party exceptions in [LICENSE](LICENSE). This is source-visible proprietary software. Third-party components retain their own licenses.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) **0.19.5 or newer** for Minecraft **1.21.11**.
2. Put `RestrictTiers-Fabric-1.21.11-1.0.0.jar` and [Fabric API for 1.21.11](https://modrinth.com/mod/fabric-api/versions?g=1.21.11) in your instance's `mods` folder. Tested with Fabric API **0.141.6+1.21.11** and Java **21**.
3. Join a world or server and run `/check <IGN or UUID>`. Open chat with **T** to click **Open player profile** or **View details**.

No server plugin is required. If the [RestrictTiers plugin](https://github.com/raincakecat/RestrictTiers-Plugin) is also installed, these client commands execute locally without invoking the plugin twice. A namespaced server command such as `/restricttiers:check` explicitly invokes the plugin instead.

## How lookups work

The mod calls `https://restricttiers.raincakecat.com/api/player/<identifier>` only after an explicit check. The website resolves an IGN to a verified UUID and reads its restriction database. The mod displays the returned public fields only in the command runner's local chat; it contains no database or admin credentials.

Autocomplete uses the server-provided online player list without querying the database. Active records precede past records; details and page navigation reuse the fetched snapshot. See [RESULTS.md](RESULTS.md) for cooldowns, limits, and errors.

## Build and test

Set `JAVA_HOME` to a Java **21 JDK**, then run:

```powershell
.\gradlew.bat build
.\gradlew.bat :fabric:runClientGameTest
```

On Linux/macOS use `sh ./gradlew` instead of `.\gradlew.bat`. The wrapper downloads Gradle **9.6.0** with a verified distribution checksum. The client game test launches an isolated Minecraft instance with fictional records and a local HTTP fixture.

Installable output: `fabric/build/libs/RestrictTiers-Fabric-1.21.11-1.0.0.jar`. Do not install the sources JAR or the common library alone.

This repository contains its own `common` module and builds independently of the plugin repository. Keep changes to shared lookup code consistent between repositories. See [TEST-REPORT.md](TEST-REPORT.md) for runtime verification, and [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) for dependency licenses.
