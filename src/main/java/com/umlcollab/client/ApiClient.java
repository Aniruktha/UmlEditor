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
import java.time.Duration;
import java.util.List;

public class ApiClient {
    private static final Gson gson = new Gson();
    private final String serverAddress;
    
    private int userId;
    private String username;
    private String email;

    public ApiClient(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    private HttpClient createClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public boolean testConnection() {
        try {
            HttpClient client = createClient();
            JsonObject json = new JsonObject();
            json.addProperty("email", "test@test.com");
            json.addProperty("password", "test");
            
            String url = "http://" + serverAddress + "/api/login";
            System.out.println(">>> Testing connection to: " + url);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(">>> Test connection response: " + response.statusCode());
            // If we get ANY response (200, 400, 401, etc), server is running!
            return response.statusCode() > 0;
        } catch (Exception e) {
            System.err.println(">>> Connection test FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean login(String email, String password) {
        try {
            HttpClient client = createClient();
            JsonObject json = new JsonObject();
            json.addProperty("email", email);
            json.addProperty("password", password);

            System.out.println("Attempting login to: http://" + serverAddress + "/api/login");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/login"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            System.out.println("Login response status: " + response.statusCode());
            
            if (response.statusCode() != 200) {
                System.err.println("Server error: " + response.body());
                return false;
            }
            
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);

            if (result.has("success") && result.get("success").getAsBoolean()) {
                this.userId = result.get("userId").getAsInt();
                this.username = result.get("username").getAsString();
                this.email = result.get("email").getAsString();
                return true;
            } else {
                String msg = result.has("message") ? result.get("message").getAsString() : "Unknown error";
                System.err.println("Login failed: " + msg);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Login error: " + e.getClass().getName() + " - " + e.getMessage());
            return false;
        }
    }

    public boolean register(String username, String email, String password) {
        try {
            HttpClient client = createClient();
            JsonObject json = new JsonObject();
            json.addProperty("username", username);
            json.addProperty("email", email);
            json.addProperty("password", password);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/register"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                System.err.println("Server error: " + response.body());
                return false;
            }
            
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);

            if (result.has("success") && result.get("success").getAsBoolean()) {
                this.userId = result.get("userId").getAsInt();
                this.username = result.get("username").getAsString();
                this.email = email;
                return true;
            } else {
                String msg = result.has("message") ? result.get("message").getAsString() : "Unknown error";
                System.err.println("Registration failed: " + msg);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Registration error: " + e.getClass().getName() + " - " + e.getMessage());
            return false;
        }
    }

    public List<UMLDiagram> getNotebooks() {
        try {
            HttpClient client = createClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/notebooks?userId=" + userId))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);
            JsonArray arr = result.getAsJsonArray("notebooks");

            return gson.fromJson(arr, new com.google.gson.reflect.TypeToken<List<UMLDiagram>>(){}.getType());
        } catch (Exception e) {
            System.err.println("Error getting notebooks: " + e.getMessage());
            return List.of();
        }
    }

    public int createNotebook(String name) {
        try {
            HttpClient client = createClient();
            JsonObject json = new JsonObject();
            json.addProperty("userId", userId);
            json.addProperty("name", name);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/notebook/create"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);

            if (result.has("success") && result.get("success").getAsBoolean()) {
                return result.get("notebookId").getAsInt();
            }
            return -1;
        } catch (Exception e) {
            System.err.println("Error creating notebook: " + e.getMessage());
            return -1;
        }
    }

    public boolean shareNotebook(int diagramId, String email, String role) {
        try {
            HttpClient client = createClient();
            JsonObject json = new JsonObject();
            json.addProperty("diagramId", diagramId);
            json.addProperty("ownerId", userId);
            json.addProperty("email", email);
            json.addProperty("role", role);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/notebook/share"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);

            return result.has("success") && result.get("success").getAsBoolean();
        } catch (Exception e) {
            System.err.println("Error sharing notebook: " + e.getMessage());
            return false;
        }
    }

    public boolean updateNotebookContent(int diagramId, String content) {
        try {
            HttpClient client = createClient();
            JsonObject json = new JsonObject();
            json.addProperty("diagramId", diagramId);
            json.addProperty("content", content);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + serverAddress + "/api/notebook/update"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject result = gson.fromJson(response.body(), JsonObject.class);

            return result.has("success") && result.get("success").getAsBoolean();
        } catch (Exception e) {
            System.err.println("Error updating notebook: " + e.getMessage());
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