package com.raincakecat.restricttiers.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.raincakecat.restricttiers.core.*;
import com.raincakecat.restricttiers.core.Model.Line;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public final class RestrictTiersClient implements ClientModInitializer {
    private final ApiClient api = new ApiClient();
    private Session session = new Session();
    @Override public void onInitializeClient() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> session = new Session());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> session = new Session());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            for (String name : List.of("check", "restrictcheck")) {
                dispatcher.register(literal(name).executes(context -> { feedback(List.of(Line.text("Usage: /" + name + " <IGN or UUID>"))); return 1; })
                    .then(argument("identifier", StringArgumentType.word()).suggests((context, builder) -> {
                        var handler = MinecraftClient.getInstance().getNetworkHandler();
                        if (handler != null) handler.getPlayerList().stream().map(entry -> entry.getProfile().name())
                            .filter(player -> player.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                        return builder.buildFuture();
                    }).executes(context -> check(StringArgumentType.getString(context, "identifier")))));
            }
            dispatcher.register(literal("restricttiersresults").then(argument("token", StringArgumentType.word())
                .then(argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                    if (session.snapshot() == null || !session.token().equals(StringArgumentType.getString(context, "token"))) feedback(List.of(Line.text("This result is no longer available. Run a new check.")));
                    else feedback(Results.page(session.snapshot(), IntegerArgumentType.getInteger(context, "page"), "/restricttiersresults", session.token()));
                    return 1;
                }))));
        });
    }
    private int check(String raw) {
        String identifier;
        try { identifier = ApiClient.identifier(raw); }
        catch (IllegalArgumentException e) { feedback(List.of(Line.text(e.getMessage()))); return 1; }
        MinecraftClient client = MinecraftClient.getInstance();
        var connection = client.getNetworkHandler();
        if (connection == null || client.player == null) return 1;
        Session current = session;
        String blocked = current.begin(System.currentTimeMillis());
        if (blocked != null) { feedback(List.of(Line.text(blocked))); return 1; }
        feedback(List.of(Line.text("Checking RestrictTiers…")));
        api.lookup(identifier).thenAccept(outcome -> client.execute(() -> {
            if (current != session || client.getNetworkHandler() != connection || client.player == null) return;
            current.complete(outcome, System.currentTimeMillis());
            feedback(outcome.success() ? Results.summary(outcome.player(), "/restricttiersresults", current.token()) : List.of(Line.text(outcome.error())));
        }));
        return 1;
    }
    public static Text render(Line line) {
        MutableText text = Text.literal(line.text()).formatted(line.url() != null || line.command() != null ? Formatting.AQUA : Formatting.GRAY);
        if (line.url() != null) text.styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(line.url()))).withHoverEvent(new HoverEvent.ShowText(Text.literal("Open in browser"))));
        else if (line.command() != null) text.styled(style -> style.withClickEvent(new ClickEvent.RunCommand(line.command())).withHoverEvent(new HoverEvent.ShowText(Text.literal("Show cached results"))));
        return text;
    }
    private static void feedback(List<Line> lines) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) lines.forEach(line -> player.sendMessage(render(line), false));
    }
}
