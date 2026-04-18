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
        super(new InetSocketAddress("0.0.0.0", 8887));
        this.dbManager = dbManager;
        logger.info("WebSocket server initialized on port 8887 (all interfaces)");
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
        
        // Remove from all diagram groups (use synchronized block to avoid ConcurrentModificationException)
        for (Integer diagramId : diagramConnections.keySet()) {
            Set<WebSocket> clients = diagramConnections.get(diagramId);
            if (clients != null) {
                clients.remove(conn);
            }
        }
        
        // Remove authenticated user
        authenticatedUsers.remove(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        logger.debug("Message received from {}: {}", conn.getRemoteSocketAddress(), message);
        
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            if (json == null || !json.has("type")) {
                sendError(conn, "Invalid message: missing type field");
                return;
            }
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
        if (!json.has("userId") || !json.has("token")) {
            sendError(conn, "Missing authentication credentials");
            return;
        }
        
        int userId = json.get("userId").getAsInt();
        String token = json.get("token").getAsString();
        
        authenticatedUsers.put(conn, userId);
        logger.info("User {} authenticated via WebSocket", userId);
        
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
        
        Integer userId = authenticatedUsers.get(conn);
        if (userId == null) {
            sendError(conn, "Not authenticated - please authenticate first");
            return;
        }

        if (dbManager != null) {
            UMLDiagram diagram = dbManager.getDiagramById(diagramId);
            if (diagram == null) {
                sendError(conn, "Diagram not found");
                return;
            }
            if (diagram.getOwnerId() != userId) {
                boolean hasAccess = false;
                try {
                    var accessible = dbManager.getAccessibleNotebooks(userId);
                    hasAccess = accessible.stream().anyMatch(d -> d.getDiagramId() == diagramId);
                } catch (Exception e) {
                    logger.error("Error checking access: {}", e.getMessage());
                }
                if (!hasAccess) {
                    sendError(conn, "You do not have permission to access this diagram");
                    return;
                }
            }
        }

        Set<WebSocket> clients = diagramConnections.computeIfAbsent(diagramId, k -> 
            Collections.newSetFromMap(new ConcurrentHashMap<>()));
        clients.add(conn);

        logger.info("User {} joined diagram {} (total clients: {})", userId, diagramId, clients.size());

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
        
        Integer userId = authenticatedUsers.get(conn);
        if (userId == null) {
            sendError(conn, "Not authenticated - please authenticate first");
            return;
        }
        
        Set<WebSocket> clients = diagramConnections.get(diagramId);
        if (clients == null || !clients.contains(conn)) {
            sendError(conn, "Not joined to this diagram");
            return;
        }

        if (dbManager != null) {
            UMLDiagram diagram = dbManager.getDiagramById(diagramId);
            if (diagram != null && diagram.getOwnerId() != userId) {
                boolean hasEditAccess = false;
                try {
                    var accessible = dbManager.getAccessibleNotebooks(userId);
                    var ownedDiagram = accessible.stream().filter(d -> d.getDiagramId() == diagramId).findFirst();
                    if (ownedDiagram.isPresent()) {
                        String role = ownedDiagram.get().getAccessRole();
                        hasEditAccess = "OWNER".equalsIgnoreCase(role) || "EDITOR".equalsIgnoreCase(role);
                    }
                } catch (Exception e) {
                    logger.error("Error checking edit access: {}", e.getMessage());
                }
                if (!hasEditAccess) {
                    sendError(conn, "You do not have permission to edit this diagram");
                    return;
                }
            }
        }

        JsonObject delta = json.getAsJsonObject("delta");
        
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

        JsonObject broadcast = new JsonObject();
        broadcast.addProperty("type", "edit");
        broadcast.addProperty("diagramId", diagramId);
        broadcast.add("delta", delta);
        
        if (json.has("newContent")) {
            String newContent = json.get("newContent").getAsString();
            broadcast.addProperty("newContent", newContent);
            logger.info(">>> Broadcasting edit to {} clients, content size={}", clients.size() - 1, newContent.length());
        }

        for (WebSocket client : clients) {
            if (!client.equals(conn)) {
                client.send(gson.toJson(broadcast));
            }
        }

        JsonObject ack = new JsonObject();
        ack.addProperty("type", "editAck");
        ack.addProperty("diagramId", diagramId);
        ack.addProperty("success", true);
        conn.send(gson.toJson(ack));

        logger.debug("Edit broadcast to diagram {} by user {}", diagramId, userId);
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