package com.umlcollab.server;

import com.umlcollab.server.api.ApiServer;
import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.websocket.UMLWebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) {
        logger.info("Starting UML Editor Server...");

        DatabaseManager dbManager = new DatabaseManager();
        try {
            dbManager.connect();
        } catch (Exception e) {
            logger.error("Failed to connect to database: {}", e.getMessage());
            System.exit(1);
        }

        UMLWebSocketServer wsServer = new UMLWebSocketServer(dbManager);
        wsServer.start();
        logger.info("WebSocket server started on port 8887");

        try {
            ApiServer apiServer = new ApiServer(dbManager);
            apiServer.start();
            logger.info("API server started on port 8888");
        } catch (Exception e) {
            logger.error("Failed to start API server: {}", e.getMessage());
            System.exit(1);
        }

        logger.info("Server started successfully");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down server...");
            try {
                wsServer.stop(5000);
                logger.info("WebSocket server stopped");
            } catch (InterruptedException e) {
                logger.error("Error stopping WebSocket server", e);
            }
            try {
                dbManager.close();
                logger.info("Database connection closed");
            } catch (Exception e) {
                logger.error("Error closing database", e);
            }
            logger.info("Server shutdown complete");
        }));

        synchronized (ServerMain.class) {
            try {
                ServerMain.class.wait();
            } catch (InterruptedException e) {
                logger.info("Server thread interrupted");
            }
        }
    }
}