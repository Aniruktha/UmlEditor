package com.umlcollab.server;

import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.websocket.UMLWebSocketServer;

public class ServerMain {
    public static void main(String[] args) {
        System.out.println("Server started...");

        // Initialize database connection first (required for WebSocket ops)
        DatabaseManager dbManager = new DatabaseManager();
        dbManager.connect(); // Ensures DB is ready – you'll see "Database connected successfully."

        // Initialize WebSocket server with DB manager
        UMLWebSocketServer server = new UMLWebSocketServer(dbManager);
        server.start(); // Launches server threads – you'll see "WebSocket server started on port: 8887"

        // Optional: Graceful shutdown hook (stops server on Ctrl+C/JVM exit)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down server...");
            try {
                server.stop(1000); // 1s timeout for clean disconnects
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            try {
                if (dbManager.getConnection() != null) {
                    dbManager.getConnection().close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        // Keep main thread alive
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }
}