package com.quinto33.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TurboWarpClientBridge implements ClientModInitializer {
    private static final int PORT = 8080;
    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            "\\\"cmd\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\""
    );

    private volatile String lastBrokenBlock;
    private volatile String lastKilledMob;
    private HttpServer webServer;

    @Override
    public void onInitializeClient() {
        System.out.println("[TurboWarp Bridge] Initializing client mod...");

        startWebServer();

        PlayerBlockBreakEvents.BREAK.register((world, player, pos, state, blockEntity) -> {
            lastBrokenBlock = Registries.BLOCK.getId(state.getBlock()).toString();
            System.out.println("[TurboWarp Bridge] Player broke block: " + lastBrokenBlock);
            return true;
        });
    }

    private void startWebServer() {
        try {
            webServer = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);

            webServer.createContext("/handshake", this::handleHandshake);
            webServer.createContext("/command", this::handleCommand);
            webServer.createContext("/playerdata", this::handlePlayerData);

            webServer.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "turbowarp-bridge-http");
                thread.setDaemon(true);
                return thread;
            }));

            webServer.start();
            System.out.println("[TurboWarp Bridge] HTTP server active on http://127.0.0.1:" + PORT);
        } catch (IOException exception) {
            System.err.println("[TurboWarp Bridge] Could not start HTTP server on port " + PORT + ".");
            exception.printStackTrace();
        }
    }

    private void handleHandshake(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        if (handleOptions(exchange)) {
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        sendText(exchange, 200, "Handshake secured!");
    }

    private void handlePlayerData(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        if (handleOptions(exchange)) {
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        String name = "unknown";
        double x = 0;
        double y = 0;
        double z = 0;

        if (client.player != null) {
            name = client.player.getName().getString();
            x = client.player.getX();
            y = client.player.getY();
            z = client.player.getZ();
        }

        String payload = "{"
                + "\"name\":\"" + escapeJson(name) + "\","
                + "\"x\":" + x + ","
                + "\"y\":" + y + ","
                + "\"z\":" + z + ","
                + "\"blockBroken\":" + nullableJsonString(lastBrokenBlock) + ","
                + "\"killedMob\":" + nullableJsonString(lastKilledMob)
                + "}";

        sendJson(exchange, 200, payload);
    }

    private void handleCommand(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        if (handleOptions(exchange)) {
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method Not Allowed");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Matcher matcher = COMMAND_PATTERN.matcher(body);

        if (!matcher.find()) {
            sendText(exchange, 400, "Expected JSON containing a cmd string");
            return;
        }

        String command = unescapeJsonString(matcher.group(1)).trim();

        if (command.isEmpty()) {
            sendText(exchange, 400, "Command must not be empty");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        client.execute(() -> {
            if (client.player == null || client.getNetworkHandler() == null) {
                return;
            }

            if (command.startsWith("/")) {
                client.player.networkHandler.sendChatCommand(command.substring(1));
            } else {
                client.player.networkHandler.sendChatMessage(command);
            }
        });

        sendText(exchange, 202, "Command queued");
    }

    private static boolean handleOptions(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }

        return false;
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] response = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        sendText(exchange, status, json);
    }

    private static String nullableJsonString(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescapeJsonString(String value) {
        return value
                .replace("\\\\", "\\")
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r");
    }
}
