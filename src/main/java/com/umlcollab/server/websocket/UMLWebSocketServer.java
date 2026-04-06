package com.umlcollab.server.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.UMLDiagram;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UMLWebSocketServer extends org.java_websocket.server.WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger(UMLWebSocketServer.class);
    
    private final DatabaseManager dbManager;
    private final Gson gson = new Gson();

    // Track connections per diagram: diagramId -> Set of WebSockets (thread-safe)
    private final ConcurrentHashMap<Integer, Set<WebSocket>> diagramConnections = new ConcurrentHashMap<>();
    
    // Track authenticated users per connection
    private final ConcurrentHashMap<WebSocket, Integer> authenticatedUsers = new ConcurrentHashMap<>();

    public UMLWebSocketServer(DatabaseManager dbManager) {
        super(new InetSocketAddress(8887));
        this.dbManager = dbManager;
        logger.info("WebSocket server initialized on port 8887");
    }

    public UMLWebSocketServer() {
        this(null);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        logger.info("New connection from: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("Connection closed: {} (code: {}, reason: {})", conn.getRemoteSocketAddress(), code, reason);
        
        // Remove from all diagram groups
        for (Set<WebSocket> clients : diagramConnections.values()) {
            clients.remove(conn);
        }
        
        // Remove authenticated user
        authenticatedUsers.remove(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.debug("Message received from {}: {}", conn.getRemoteSocketAddress(), message);
        
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();

            switch (type) {
                case "auth":
                    handleAuth(conn, json);
                    break;
                case "join":
                    handleJoin(conn, json);
                    break;
                case "edit":
                    handleEdit(conn, json);
                    break;
                default:
                    sendError(conn, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage(), e);
            sendError(conn, "Invalid message format: " + e.getMessage());
        }
    }

    private void handleAuth(WebSocket conn, JsonObject json) {
        // Simple authentication - in production, use JWT or session-based auth
        if (!json.has("userId") || !json.has("token")) {
            sendError(conn, "Missing authentication credentials");
            return;
        }
        
        int userId = json.get("userId").getAsInt();
        String token = json.get("token").getAsString();
        
        // In production, validate token against user
        // For now, we just store the userId
        authenticatedUsers.put(conn, userId);
        logger.info("User {} authenticated", userId);
        
        JsonObject response = new JsonObject();
        response.addProperty("type", "authResponse");
        response.addProperty("success", true);
        conn.send(gson.toJson(response));
    }

    private void handleJoin(WebSocket conn, JsonObject json) throws SQLException {
        if (!json.has("diagramId")) {
            sendError(conn, "Missing diagramId");
            return;
        }
        
        int diagramId = json.get("diagramId").getAsInt();
        
        // For this demo, we allow unauthenticated joins
        // In production, uncomment the following to enforce authentication:
        // Integer userId = authenticatedUsers.get(conn);
        // if (userId == null) {
        //     sendError(conn, "Not authenticated - please authenticate first");
        //     return;
        // }

        // Add to group
        Set<WebSocket> clients = diagramConnections.computeIfAbsent(diagramId, k -> 
            Collections.newSetFromMap(new ConcurrentHashMap<>()));
        clients.add(conn);

        logger.info("Client joined diagram {} (total clients: {})", diagramId, clients.size());

        // Send initial state from DB
        JsonObject response = new JsonObject();
        response.addProperty("type", "initialState");
        response.addProperty("diagramId", diagramId);
        
        String initialState = "[]";
        if (dbManager != null) {
            UMLDiagram diagram = dbManager.getDiagramById(diagramId);
            if (diagram != null && diagram.getContent() != null) {
                initialState = diagram.getContent();
            }
        }
        response.addProperty("content", initialState);
        conn.send(gson.toJson(response));
    }

    private void handleEdit(WebSocket conn, JsonObject json) throws SQLException {
        if (!json.has("diagramId") || !json.has("delta")) {
            sendError(conn, "Missing diagramId or delta");
            return;
        }
        
        int diagramId = json.get("diagramId").getAsInt();
        
        // For this demo, we allow unauthenticated edits
        // In production, uncomment the following to enforce authentication:
        // Integer userId = authenticatedUsers.get(conn);
        // if (userId == null) {
        //     sendError(conn, "Not authenticated - please authenticate first");
        //     return;
        // }
        
        // Verify conn is in this diagram's group
        Set<WebSocket> clients = diagramConnections.get(diagramId);
        if (clients == null || !clients.contains(conn)) {
            sendError(conn, "Not joined to this diagram");
            return;
        }

        JsonObject delta = json.getAsJsonObject("delta");
        
        // Save content (in production, consider delta merge)
        if (json.has("newContent")) {
            String newContentStr = json.get("newContent").getAsString();
            
            if (dbManager != null) {
                boolean saved = dbManager.updateNotebookContent(diagramId, newContentStr);
                if (!saved) {
                    sendError(conn, "Failed to save edit");
                    return;
                }
            }
        }

        // Broadcast delta to others (exclude sender)
        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("type", "edit");
        broadcast.addProperty("diagramId", diagramId);
        broadcast.add("delta", delta);

        for (WebSocket client : clients) {
            if (!client.equals(conn)) {
                client.send(gson.toJson(broadcast));
            }
        }

        // Ack sender
        JsonObject ack = new JsonObject();
        ack.addProperty("type", "editAck");
        ack.addProperty("diagramId", diagramId);
        ack.addProperty("success", true);
        conn.send(gson.toJson(ack));

        logger.debug("Edit broadcast to diagram {}", diagramId);
    }

    private void sendError(WebSocket conn, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("type", "error");
        error.addProperty("message", message);
        conn.send(gson.toJson(error));
        logger.warn("Error sent to {}: {}", conn.getRemoteSocketAddress(), message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.error("Error on connection {}: {}", conn != null ? conn.getRemoteSocketAddress() : "unknown", ex.getMessage(), ex);
    }

    @Override
    public void onStart() {
        logger.info("WebSocket server started on port: {}", getPort());
    }

    public Set<WebSocket> getClientsForDiagram(int diagramId) {
        return diagramConnections.getOrDefault(diagramId, Collections.emptySet());
    }
}