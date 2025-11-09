package com.umlcollab.server.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.UMLDiagram;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UMLWebSocketServer extends org.java_websocket.server.WebSocketServer {
    private final DatabaseManager dbManager;
    private final Gson gson = new Gson();

    // Track connections per diagram: diagramId -> Set of WebSockets (thread-safe)
    private final ConcurrentHashMap<Integer, Set<WebSocket>> diagramConnections = new ConcurrentHashMap<>();

    // Preferred constructor: Requires DB for full functionality
    public UMLWebSocketServer(DatabaseManager dbManager) {
        super(new InetSocketAddress(8887)); // Port 8887
        this.dbManager = dbManager != null ? dbManager : new DatabaseManager(); // Fallback if null
    }

    // Fallback no-arg for basic testing (no DB)
    public UMLWebSocketServer() {
        this(null); // Uses fallback DB
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New connection: " + conn.getRemoteSocketAddress());
        // Optional: Extract userId from handshake for auth
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Closed connection: " + conn.getRemoteSocketAddress() + " (code: " + code + ", reason: " + reason + ")");
        // Remove from all diagram groups
        diagramConnections.values().forEach(clients -> clients.remove(conn));
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Message received from " + conn.getRemoteSocketAddress() + ": " + message);
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);
            String type = json.get("type").getAsString();

            switch (type) {
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
            e.printStackTrace();
            sendError(conn, "Invalid message format: " + e.getMessage());
        }
    }

    private void handleJoin(WebSocket conn, JsonObject json) throws SQLException {
        int diagramId = json.get("diagramId").getAsInt();
        int userId = json.has("userId") ? json.get("userId").getAsInt() : -1; // For auth

        // TODO: Auth check - verify user has access via dbManager.getAccessibleNotebooks(userId)
        // For now, allow all

        // Add to group
        Set<WebSocket> clients = diagramConnections.computeIfAbsent(diagramId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        clients.add(conn);

        System.out.println("Client joined diagram " + diagramId + " (total clients: " + clients.size() + ")");

        // Send initial state from DB (fallback to empty if no DB)
        JsonObject response = new JsonObject();
        response.addProperty("type", "initialState");
        response.addProperty("diagramId", diagramId);
        String initialState = "[]"; // Default empty
        if (dbManager != null) {
            UMLDiagram diagram = dbManager.getDiagramById(diagramId);
            if (diagram != null && diagram.getContent() != null) {
                initialState = new String(diagram.getContent()); // Assume UTF-8 serialized
            }
        }
        response.addProperty("content", initialState);
        conn.send(gson.toJson(response));
    }

    private void handleEdit(WebSocket conn, JsonObject json) throws SQLException {
        int diagramId = json.get("diagramId").getAsInt();
        JsonObject delta = json.getAsJsonObject("delta");

        // Verify conn is in this diagram's group
        Set<WebSocket> clients = diagramConnections.get(diagramId);
        if (clients == null || !clients.contains(conn)) {
            sendError(conn, "Not joined to this diagram");
            return;
        }

        // Save full content (from client; TODO: merge delta)
        String newContentStr = json.get("newContent").getAsString();
        byte[] newContent = newContentStr.getBytes();
        boolean saved = (dbManager != null) ? dbManager.updateNotebookContent(diagramId, newContent) : true; // Skip save if no DB

        if (!saved) {
            sendError(conn, "Failed to save edit: " + dbManager.getLastError());
            return;
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

        System.out.println("Edit broadcast to diagram " + diagramId + " (delta: " + delta + ")");
    }

    private void sendError(WebSocket conn, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("type", "error");
        error.addProperty("message", message);
        conn.send(gson.toJson(error));
        System.err.println("Error sent to " + conn.getRemoteSocketAddress() + ": " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
        if (conn != null) {
            System.err.println("Error on connection: " + conn.getRemoteSocketAddress());
        }
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
    }

    @Override
    public void start() {
        super.start(); // CRITICAL: Launches acceptor/processor threads
    }

    // Utility for testing
    public Set<WebSocket> getClientsForDiagram(int diagramId) {
        return diagramConnections.getOrDefault(diagramId, Collections.emptySet());
    }
}