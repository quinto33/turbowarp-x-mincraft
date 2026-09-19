package com.quinto33.bridge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client-side TurboWarp bridge for Fabric 1.21.1.
 *
 * TurboWarp sends JSON requests to localhost:8080. Commands are sent through
 * the connected player's normal chat/command connection, so the server still
 * applies its normal permissions and command validation.
 */
public final class TurboWarpClientBridge implements ClientModInitializer {
    private static final int PORT = 8080;
    private static final Pattern COMMAND_PATTERN = Pattern.compile("\\\"cmd\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"");
    private HttpServer webServer;

    @Override
    public void onInitializeClient() {
        System.out.println("[TurboWarp Bridge] Initializing client mod...");
        startWebServer();

        PlayerBlockBreakEvents.BREAK.register((world, player, pos, state, blockEntity) -> {
            System.out.println("[TurboWarp Bridge] Player broke block: " + state.getBlock().getName().getString());
            return true;
        });
    }

    private void startWebServer() {
        try {
            webServer = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            webServer.createContext("/handshake", this::handleHandshake);
            webServer.createContext("/command", this::handleCommand);
            webServer.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r, "turbowarp-bridge-http");
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
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
    }

    private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
        byte[] response = text.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private static String unescapeJsonString(String value) {
        return value.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r");
    }
}
