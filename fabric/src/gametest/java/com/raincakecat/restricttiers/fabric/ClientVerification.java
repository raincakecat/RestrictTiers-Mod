package com.raincakecat.restricttiers.fabric;

import com.raincakecat.restricttiers.core.*;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import java.net.*;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("UnstableApiUsage")
public final class ClientVerification implements FabricClientGameTest {
    private static final String JSON = """
        {"uuid":"123456781234123412341234567890ab","username":"180","nameUnavailable":false,
         "activeCount":1,"historyCount":1,"active":[{"server":"Example community","reason":"Active restriction reason",
         "categories":["Cheating"],"restrictedAt":"2026-10-01T00:00:00Z","expiresAt":null,"archivedAt":null,
         "unverified":true,"sourceUrl":"https://discord.com/channels/12345678901234567/12345678901234568/12345678901234569"}],
         "history":[{"server":"Example community","reason":"Past restriction reason","categories":[],"restrictedAt":null,
         "expiresAt":null,"archivedAt":null,"unverified":false,"sourceUrl":null}]}
        """;
    @Override public void runTest(ClientGameTestContext context) {
        AtomicInteger requests = new AtomicInteger(), forwarded = new AtomicInteger();
        HttpServer http;
        try {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/api/player/", exchange -> {
                requests.incrementAndGet();
                try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                byte[] bytes = JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
            }); http.start();
            Object mod = FabricLoader.getInstance().getEntrypoints("client", net.fabricmc.api.ClientModInitializer.class).stream()
                .filter(RestrictTiersClient.class::isInstance).findFirst().orElseThrow();
            var api = RestrictTiersClient.class.getDeclaredField("api"); api.setAccessible(true);
            api.set(mod, new ApiClient(URI.create("http://127.0.0.1:" + http.getAddress().getPort()), HttpClient.newHttpClient()));
        } catch (Exception e) { throw new AssertionError(e); }
        try {
          try (var singleplayer = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && ClientCommandManager.getActiveDispatcher() != null);
            require(requests.get() == 0, "Connecting must not query");
            singleplayer.getServer().runOnServer(server -> {
                server.getCommandManager().getDispatcher().register(CommandManager.literal("check")
                    .then(CommandManager.argument("identifier", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(c -> { forwarded.incrementAndGet(); return 1; })));
                server.getPlayerManager().getPlayerList().forEach(server.getPlayerManager()::sendCommandTree);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                var dispatcher = ClientCommandManager.getActiveDispatcher();
                var parsed = dispatcher.parse("check ", (FabricClientCommandSource) client.getNetworkHandler().getCommandSource());
                dispatcher.getCompletionSuggestions(parsed).join();
            });
            require(requests.get() == 0, "Autocomplete must not query");
            context.runOnClient(client -> client.getNetworkHandler().sendChatCommand("check bad.name"));
            require(requests.get() == 0, "Invalid input must not query");
            context.runOnClient(client -> client.getNetworkHandler().sendChatCommand("check 180"));
            context.waitFor(client -> chat(client).stream().anyMatch(t -> t.getString().contains("Active: 1 · Past: 1")));
            require(requests.get() == 1 && forwarded.get() == 0, "Client command must not forward or duplicate");
            String navigation = context.computeOnClient(client -> {
                require(chat(client).stream().anyMatch(t -> t.getStyle().getClickEvent() instanceof ClickEvent.OpenUrl), "Clickable profile link");
                return chat(client).stream().map(t -> t.getStyle().getClickEvent()).filter(ClickEvent.RunCommand.class::isInstance)
                    .map(ClickEvent.RunCommand.class::cast).findFirst().orElseThrow().command();
            });
            context.runOnClient(client -> client.getNetworkHandler().sendChatCommand(navigation.substring(1)));
            context.waitFor(client -> chat(client).stream().anyMatch(t -> t.getString().contains("Past ·")));
            require(requests.get() == 1, "Details must not query");
            context.takeScreenshot("restricttiers-details");
            context.runOnClient(client -> client.getNetworkHandler().sendChatCommand("restrictcheck 180"));
            context.waitTicks(2);
            require(requests.get() == 1, "Alias shares cooldown");
            context.runOnClient(client -> chat(client).forEach(t -> {
                if (t.getString().contains("Expires:")) require(t.getString().contains("Not recorded"), "Null expiry must not imply permanent");
            }));
            System.out.println("RT_CLIENT_PASS: startup, commands, alias, autocomplete, private local chat, clickable links, cached details, server-command collision");
          }
          if (Boolean.getBoolean("restricttiers.verify.paper")) paper(context, requests);
        } finally { http.stop(0); }
    }
    private static void paper(ClientGameTestContext context, AtomicInteger clientRequests) {
        context.runOnClient(client -> {
            client.inGameHud.getChatHud().clear(true);
            net.minecraft.client.gui.screen.multiplayer.ConnectScreen.connect(new net.minecraft.client.gui.screen.TitleScreen(), client,
                net.minecraft.client.network.ServerAddress.parse("127.0.0.1:25581"),
                new net.minecraft.client.network.ServerInfo("Local verification", "127.0.0.1:25581", net.minecraft.client.network.ServerInfo.ServerType.OTHER), false, null);
        });
        context.waitFor(client -> client.player != null && client.getNetworkHandler() != null, 1200);
        context.waitFor(client -> client.getNetworkHandler().getCommandDispatcher().getRoot().getChild("restricttiers:check") != null, 600);
        require(serverCount() == 0, "Connecting to Paper must not query");
        int before = clientRequests.get();
        context.runOnClient(client -> client.getNetworkHandler().sendChatCommand("check 180"));
        context.waitFor(client -> chat(client).stream().anyMatch(t -> t.getString().contains("Active: 1 · Past: 1")));
        require(clientRequests.get() == before + 1 && serverCount() == 0, "Mod and plugin together must issue one client lookup only");
        context.runOnClient(client -> { client.inGameHud.getChatHud().clear(true); client.getNetworkHandler().sendChatCommand("restricttiers:check 180"); });
        context.waitFor(client -> chat(client).stream().anyMatch(t -> t.getString().contains("Active: 1 · Past: 1")));
        require(serverCount() == 1 && clientRequests.get() == before + 1, "Plugin works over real multiplayer command dispatch");
        String details = context.computeOnClient(client -> chat(client).stream().map(t -> t.getStyle().getClickEvent())
            .filter(ClickEvent.RunCommand.class::isInstance).map(ClickEvent.RunCommand.class::cast).findFirst().orElseThrow().command());
        context.runOnClient(client -> client.getNetworkHandler().sendChatCommand(details.substring(1)));
        context.waitFor(client -> chat(client).stream().anyMatch(t -> t.getString().contains("Past ·")));
        require(serverCount() == 1, "Plugin details over multiplayer must not query");
        context.takeScreenshot("restricttiers-paper-details");
        context.runOnClient(client -> client.disconnect(new net.minecraft.client.gui.screen.TitleScreen(), false));
        control("stop");
        System.out.println("RT_MULTIPLAYER_PASS: real Paper plugin commands, mod and plugin together, no duplicate lookup, cached plugin details");
    }
    private static int serverCount() { return Integer.parseInt(control("count")); }
    private static String control(String path) {
        try { return HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:25583/" + path)).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).body(); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    @SuppressWarnings("unchecked") private static List<Text> chat(MinecraftClient client) {
        try {
            var field = ChatHud.class.getDeclaredField("messages"); field.setAccessible(true);
            return ((List<ChatHudLine>) field.get(client.inGameHud.getChatHud())).stream().map(ChatHudLine::content).toList();
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private static void require(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
}
