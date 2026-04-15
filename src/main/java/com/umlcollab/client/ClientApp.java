package com.umlcollab.client;

import com.umlcollab.client.config.ServerConfig;
import com.umlcollab.client.views.LoginPage;
import com.umlcollab.client.views.MyNotebooksPage;
import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.User;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(ClientApp.class);

    private DatabaseManager dbManager;
    private ApiClient apiClient;

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting UML Editor Client...");
        
        // Get configuration
        String serverAddr = ServerConfig.getApiAddress();
        boolean useRemote = ServerConfig.useRemoteApi();
        
        System.out.println("Config - server: " + serverAddr + ", useRemote: " + useRemote);
        
        // First, always try to set up local database
        boolean dbConnected = false;
        try {
            System.out.println("Attempting local database connection...");
            String dbPass = System.getenv("DB_PASSWORD");
            System.out.println("DB_PASSWORD set: " + (dbPass != null && !dbPass.isEmpty()));
            
            if (dbPass != null && !dbPass.isEmpty()) {
                dbManager = new DatabaseManager();
                dbManager.connect();
                dbConnected = true;
                System.out.println("Local database connected!");
            } else {
                System.out.println("DB_PASSWORD not set, skipping local DB");
            }
        } catch (Exception e) {
            System.out.println("Local DB failed: " + e.getMessage());
        }
        
        // If remote is configured and local failed, try remote
        if (!dbConnected && useRemote) {
            System.out.println("Trying remote server: " + serverAddr);
            try {
                apiClient = new ApiClient(serverAddr);
                if (apiClient.testConnection()) {
                    System.out.println("Connected to remote server!");
                } else {
                    System.out.println("Remote server not available");
                    apiClient = null;
                }
            } catch (Exception e) {
                System.out.println("Remote connection failed: " + e.getMessage());
                apiClient = null;
            }
        }

        System.out.println("Final - dbManager: " + (dbManager != null) + ", apiClient: " + (apiClient != null));

        LoginPage loginPage = new LoginPage(dbManager, apiClient, this::openMyNotebooksPage);
        loginPage.show();
    }

    private void openMyNotebooksPage(User loggedInUser) {
        logger.info("User logged in: {}", loggedInUser.getUsername());
        
        if (apiClient != null) {
            MyNotebooksPage notebooksPage = new MyNotebooksPage(apiClient, loggedInUser);
            notebooksPage.showFullscreen();
        } else {
            MyNotebooksPage notebooksPage = new MyNotebooksPage(dbManager, loggedInUser);
            notebooksPage.showFullscreen();
        }
    }

    private void showErrorAndExit(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Connection Error");
        alert.setHeaderText("Unable to connect");
        alert.setContentText(message);
        alert.setOnCloseRequest(e -> Platform.exit());
        alert.show();
    }

    @Override
    public void stop() throws Exception {
        logger.info("Client shutting down...");
        if (dbManager != null) {
            dbManager.close();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}