package com.umlcollab.client;

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

    @Override
    public void start(Stage primaryStage) {
        logger.info("Starting UML Editor Client...");
        
        try {
            dbManager = new DatabaseManager();
            dbManager.connect();
            logger.info("Database connected successfully");
        } catch (Exception e) {
            logger.error("Failed to connect to database: {}", e.getMessage());
            showErrorAndExit("Failed to connect to database. Please ensure the database is running and DB_PASSWORD environment variable is set.");
            return;
        }

        LoginPage loginPage = new LoginPage(dbManager, this::openMyNotebooksPage);
        loginPage.show();
    }

    private void openMyNotebooksPage(User loggedInUser) {
        logger.info("User logged in: {}", loggedInUser.getUsername());
        MyNotebooksPage notebooksPage = new MyNotebooksPage(dbManager, loggedInUser);
        notebooksPage.showFullscreen();
    }

    private void showErrorAndExit(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Connection Error");
        alert.setHeaderText("Unable to connect to server");
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