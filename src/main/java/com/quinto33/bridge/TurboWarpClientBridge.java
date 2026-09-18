package com.scratchbridge.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class TurboWarpClientBridge implements ClientModInitializer {
    private static HttpServer server;

    @Override
    public void onInitializeClient() {
        System.out.println("🚀 TurboWarp Client Bridge Mod Initialization started!");
        
        // Listen for when you log into a world or a multiplayer server
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.player != null) {
                String currentUsername = client.getSession().getUsername();
                System.out.println("Welcome " + currentUsername + "! Bridge is tracking your player name variable.");
            }
        });

        startBridgeServer();
    }

    private void startBridgeServer() {
        try {
            // Spin up our local port server on channel 8080
            server = HttpServer.create(new InetSocketAddress(8080), 0);
            
            // 📡 Route 1: Handshake (/handshake) -> Pings the pointy block 'is game online?' to TRUE
            server.createContext("/handshake", exchange -> {
                String response = "{\"status\": \"connected\", \"message\": \"Fabric Client Online\"}";
                sendCORSResponse(exchange, response);
            });

            // 🚀 Route 2: Core Command Router (/command) -> Handles movements, actions, WorldEdit, etc.
            server.createContext("/command", exchange -> {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    
                    // Pull the command value cleanly out of TurboWarp's JSON payload
                    if (body.contains("\"cmd\":\"")) {
                        String cmd = body.split("\"cmd\":\"")[1].split("\"")[0];
                        
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null && client.player != null) {
                            // Route the text directly back onto Minecraft's main gameplay thread safely
                            client.execute(() -> {
                                if (cmd.startsWith("/")) {
                                    // Handles single slash and WorldEdit double slash commands automatically
                                    client.player.networkHandler.sendCommand(cmd.substring(1));
                                } else {
                                    // Sends normal chat messages
                                    client.player.networkHandler.sendChatMessage(cmd);
                                }
                            });
                        }
                    }
                    sendCORSResponse(exchange, "{\"success\": true}");
                } else {
                    exchange.sendResponseHeaders(405, -1); // Reject non-POST requests securely
                }
            });

            server.setExecutor(null);
            server.start();
            System.out.println("✅ TurboWarp Client Bridge is listening on port 8080!");
        } catch (IOException e) {
            System.out.println("❌ Bridge failed to secure port 8080. Is another program using it?");
        }
    }

    private void sendCORSResponse(HttpExchange exchange, String response) throws IOException {
        // Essential web-safety overrides to make sure TurboWarp does not get blocked by cross-origin rules
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
