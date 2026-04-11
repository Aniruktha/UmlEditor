package com.umlcollab.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.umlcollab.server.models.UMLDiagram;
import com.umlcollab.server.models.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class ApiClient {
    private static final Gson gson = new Gson();
    private final String serverAddress;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
    private int userId;
    private String username;
    private String email;

    public ApiClient(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public boolean login(String email, String password) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("email", email);
            json.addProperty("password", password);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);

            if (result.get("success").getAsBoolean()) {
                this.userId = result.get("userId").getAsInt();
                this.username = result.get("username").getAsString();
                this.email = result.get("email").getAsString();
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean register(String username, String email, String password) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("username", username);
            json.addProperty("email", email);
            json.addProperty("password", password);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);

            if (result.get("success").getAsBoolean()) {
                this.userId = result.get("userId").getAsInt();
                this.username = result.get("username").getAsString();
                this.email = email;
                return true;
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<UMLDiagram> getNotebooks() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/notebooks?userId=" + userId))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);
            JsonArray arr = result.getAsJsonArray("notebooks");

            return gson.fromJson(arr, new com.google.gson.reflect.TypeToken<List<UMLDiagram>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public int createNotebook(String name) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("userId", userId);
            json.addProperty("name", name);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/notebook/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);

            if (result.get("success").getAsBoolean()) {
                return result.get("notebookId").getAsInt();
            }
            return -1;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public boolean shareNotebook(int diagramId, String email, String role) {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("diagramId", diagramId);
            json.addProperty("ownerId", userId);
            json.addProperty("email", email);
            json.addProperty("role", role);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/notebook/share"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);

            return result.get("success").getAsBoolean();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getServerAddress() {
        return serverAddress;
    }
}