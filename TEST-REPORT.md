# Mod verification

Verified on 2026-10-11 with Minecraft **1.21.11**, Fabric Loader **0.19.5**, Fabric API **0.141.6+1.21.11**, and Java **21**.

- Ten shared unit tests pass: complete usernames/UUIDs, identity mismatch, name-service outages, HTTP errors, no automatic retry, cooldown, snapshot isolation, stale links, clickable URLs, active/past order, and multiple cached pages.
- The actual Minecraft client game test passes: startup and autocomplete cause no lookup, invalid input is rejected, commands produce local private results, links work, details cause no request, and the alias shares the cooldown.
- A real multiplayer test against Paper **1.21.11 build 132** with both the mod and plugin installed passes: `/check` makes one client lookup with no duplicate server lookup; the plugin's namespaced command and cached detail navigation work over the network.

Run `gradlew build` and `gradlew :fabric:runClientGameTest` to reproduce the standalone tests. Fictional records and localhost HTTP fixtures are used; Minecraft runtime assets and test worlds are not tracked.

To reproduce the paired multiplayer test, build the [plugin repository](https://github.com/raincakecat/RestrictTiers-Plugin), start its isolated Paper test setup in client-verification mode, wait for `RT_CLIENT_SERVER_READY`, and run `gradlew :fabric:runClientGameTest -PpaperVerification` here. The plugin repository's test report provides server instructions.
