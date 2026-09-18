package com.quinto33.bridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class TurboWarpClientBridge implements ModInitializer {
    private static MinecraftServer currentServer;
    private HttpServer webServer;

    @Override
    public void onInitialize() {
        System.out.println("[TurboWarp Bridge] Initializing Mod...");

        // 1. Capture the running server instance when a player joins the world
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            currentServer = server;
        });

        // 2. Start the embedded HTTP server to listen for commands from TurboWarp
        try {
            webServer = HttpServer.create(new InetSocketAddress(8080), 0);
            
            // Handshake context for the "is game online?" TurboWarp boolean block
            webServer.createContext("/handshake", exchange -> {
                // Add Cross-Origin Resource Sharing (CORS) headers so the browser doesn't block it
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                
                String response = "Handshake secured!";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                exchange.close();
            });

            // Command receiving context for executing actions like /say and /summon
            webServer.createContext("/command", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                    
                    // Handle CORS preflight requests from browsers
                    if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(204, -1);
                        exchange.close();
                        return;
                    }

                    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        try (InputStream is = exchange.getRequestBody()) {
                            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                            
                            // FIXED: Extract the command string step-by-step to avoid array splitting syntax errors
                            if (body.contains("\"cmd\":\"")) {
                                String[] firstSplit = body.split("\"cmd\":\"");
                                if (firstSplit.length > 1) {
                                    String[] secondSplit = firstSplit[1].split("\"");
                                    String cmd = secondSplit[0]; // Isolate the clean command string (e.g., "/say Hello!")
                                    
                                    // Direct the execution onto Minecraft's primary server thread loop safely
                                    if (currentServer != null) {
                                        currentServer.execute(() -> {
                                            currentServer.getCommandManager().executeWithPrefix(
                                                currentServer.getCommandSource(), cmd
                                            );
                                        });
                                    }
                                }
                            }
                        }
                    }
                    exchange.sendResponseHeaders(200, 0);
                    exchange.close();
                }
            });

            webServer.setExecutor(null); 
            webServer.start();
            System.out.println("[TurboWarp Bridge] Server successfully active on port 8080!");
        } catch (Exception e) {
            System.err.println("[TurboWarp Bridge] Failed to start HTTP server!");
            e.printStackTrace();
        }

        // 3. Optional: Hook into block breaking events to print activity logs
        PlayerBlockBreakEvents.BREAK.register((world, player, pos, state, blockEntity) -> {
            String brokenBlockName = state.getBlock().toString(); 
            System.out.println("[TurboWarp Bridge] Player broke block: " + brokenBlockName);
            return true;
        });
    }
}
