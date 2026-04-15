package com.umlcollab.server.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.UMLDiagram;
import com.umlcollab.server.models.User;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;

public class ApiServer {
    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);
    private static final Gson gson = new Gson();
    
    private final DatabaseManager dbManager;
    private final HttpServer server;
    private static final int PORT = 8888;

    public ApiServer(DatabaseManager dbManager) throws IOException {
        this.dbManager = dbManager;
        // Bind to all interfaces (0.0.0.0) instead of just localhost
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        
        server.createContext("/api/login", this::handleLogin);
        server.createContext("/api/register", this::handleRegister);
        server.createContext("/api/notebooks", this::handleNotebooks);
        server.createContext("/api/notebook/create", this::handleCreateNotebook);
        server.createContext("/api/notebook/share", this::handleShareNotebook);
        
        server.setExecutor(null);
    }

    public void start() {
        server.start();
        logger.info("API Server started on port " + PORT);
    }

    private void handleLogin(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes());
            if (body.isEmpty()) {
                sendErrorJson(exchange, "Empty request body");
                return;
            }
            JsonObject json = gson.fromJson(body, JsonObject.class);
            
            if (!json.has("email") || !json.has("password")) {
                sendErrorJson(exchange, "Missing email or password");
                return;
            }
            
            String email = json.get("email").getAsString();
            String password = json.get("password").getAsString();
            
            logger.info("Login attempt for email: {}", email);
            
            User user = dbManager.validateUser(email, password);
            
            JsonObject response = new JsonObject();
            if (user != null) {
                logger.info("Login successful for user: {}", user.getUsername());
                response.addProperty("success", true);
                response.addProperty("userId", user.getId());
                response.addProperty("username", user.getUsername());
                response.addProperty("email", user.getEmail());
            } else {
                logger.warn("Login failed for email: {}", email);
                response.addProperty("success", false);
                response.addProperty("message", "Invalid credentials");
            }
            
            sendJson(exchange, response);
        } catch (Exception e) {
            logger.error("Login error: {}", e.getMessage(), e);
            sendErrorJson(exchange, "Server error: " + e.getMessage());
        }
    }

    private void sendErrorJson(com.sun.net.httpserver.HttpExchange exchange, String message) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("success", false);
        response.addProperty("message", message);
        sendJson(exchange, response);
    }

    private void handleRegister(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes());
        JsonObject json = gson.fromJson(body, JsonObject.class);
        
        String username = json.get("username").getAsString();
        String email = json.get("email").getAsString();
        String password = json.get("password").getAsString();
        
        User user = dbManager.registerUser(username, email, password);
        
        JsonObject response = new JsonObject();
        if (user != null) {
            response.addProperty("success", true);
            response.addProperty("userId", user.getId());
            response.addProperty("username", user.getUsername());
        } else {
            response.addProperty("success", false);
            response.addProperty("message", dbManager.getLastError());
        }
        
        sendJson(exchange, response);
    }

    private void handleNotebooks(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String query = exchange.getRequestURI().getQuery();
            int userId = Integer.parseInt(query.split("=")[1]);
            
            List<UMLDiagram> notebooks = dbManager.getAccessibleNotebooks(userId);
            
            JsonArray arr = new JsonArray();
            for (UMLDiagram nb : notebooks) {
                JsonObject obj = new JsonObject();
                obj.addProperty("diagramId", nb.getDiagramId());
                obj.addProperty("diagramName", nb.getDiagramName());
                obj.addProperty("content", nb.getContent());
                obj.addProperty("accessRole", nb.getAccessRole());
                arr.add(obj);
            }
            
            JsonObject response = new JsonObject();
            response.add("notebooks", arr);
            
            sendJson(exchange, response);
        } catch (Exception e) {
            logger.error("Error getting notebooks: {}", e.getMessage());
            sendError(exchange, e.getMessage());
        }
    }

    private void handleCreateNotebook(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes());
            JsonObject json = gson.fromJson(body, JsonObject.class);
            
            int userId = json.get("userId").getAsInt();
            String name = json.get("name").getAsString();
            
            int notebookId = dbManager.createNotebook(name, userId);
            
            JsonObject response = new JsonObject();
            if (notebookId > 0) {
                response.addProperty("success", true);
                response.addProperty("notebookId", notebookId);
            } else {
                response.addProperty("success", false);
                response.addProperty("message", dbManager.getLastError());
            }
            
            sendJson(exchange, response);
        } catch (Exception e) {
            logger.error("Error creating notebook: {}", e.getMessage());
            sendError(exchange, e.getMessage());
        }
    }

    private void handleShareNotebook(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        try {
            String body = new String(exchange.getRequestBody().readAllBytes());
            JsonObject json = gson.fromJson(body, JsonObject.class);
            
            int diagramId = json.get("diagramId").getAsInt();
            int ownerId = json.get("ownerId").getAsInt();
            String email = json.get("email").getAsString();
            String role = json.get("role").getAsString();
            
            User recipient = dbManager.getUserByEmail(email);
            JsonObject response = new JsonObject();
            
            if (recipient == null) {
                response.addProperty("success", false);
                response.addProperty("message", "User not found");
            } else {
                boolean success = dbManager.shareNotebookWithUser(diagramId, ownerId, recipient.getId(), null, role);
                response.addProperty("success", success);
                if (!success) {
                    response.addProperty("message", dbManager.getLastError());
                }
            }
            
            sendJson(exchange, response);
        } catch (Exception e) {
            logger.error("Error sharing notebook: {}", e.getMessage());
            sendError(exchange, e.getMessage());
        }
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, JsonObject json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, json.toString().getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(json.toString().getBytes());
        }
    }

    private void sendError(com.sun.net.httpserver.HttpExchange exchange, String message) throws IOException {
        JsonObject response = new JsonObject();
        response.addProperty("success", false);
        response.addProperty("message", message);
        sendJson(exchange, response);
    }
}