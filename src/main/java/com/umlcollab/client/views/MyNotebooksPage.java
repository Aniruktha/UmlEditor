package com.umlcollab.client.views;

import com.umlcollab.client.ApiClient;
import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.UMLDiagram;
import com.umlcollab.server.models.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MyNotebooksPage {

    private final DatabaseManager dbManager;
    private final ApiClient apiClient;
    private final User user;

    public MyNotebooksPage(DatabaseManager dbManager, User user) {
        this.dbManager = dbManager;
        this.apiClient = null;
        this.user = user;
    }

    public MyNotebooksPage(ApiClient apiClient, User user) {
        this.dbManager = null;
        this.apiClient = apiClient;
        this.user = user;
    }

    public void showFullscreen() {
        Stage stage = new Stage();
        stage.setTitle("My Notebooks - " + user.getUsername());

        Label lblTitle = new Label("Welcome, " + user.getUsername() + "!");
        lblTitle.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");

        Label lblSub = new Label("Manage your UML Notebooks below");
        lblSub.setStyle("-fx-font-size: 16px; -fx-text-fill: #2d3436;");

        Button btnCreateNotebook = new Button("+ Create New Notebook");
        btnCreateNotebook.setStyle("-fx-background-color: #00b894; -fx-text-fill: white; "
                + "-fx-font-weight: bold; -fx-padding: 10 25; -fx-background-radius: 8;");

        Button btnViewNotebook = new Button("Open Existing Notebook");
        btnViewNotebook.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white; "
                + "-fx-font-weight: bold; -fx-padding: 10 25; -fx-background-radius: 8;");

        Button btnShareNotebook = new Button("Share Notebook");
        btnShareNotebook.setStyle("-fx-background-color: #fdcb6e; -fx-text-fill: #2d3436; "
                + "-fx-font-weight: bold; -fx-padding: 10 25; -fx-background-radius: 8;");

        // Event handlers
        btnCreateNotebook.setOnAction(e -> openNewNotebook());
        btnViewNotebook.setOnAction(e -> openExistingNotebook());
        btnShareNotebook.setOnAction(e -> shareNotebook());

        VBox vbox = new VBox(20, lblTitle, lblSub, btnCreateNotebook, btnViewNotebook, btnShareNotebook);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(40));
        vbox.setStyle("-fx-background-color: linear-gradient(to bottom right, #74b9ff, #a29bfe);");

        Scene scene = new Scene(vbox, 1000, 700);
        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();
    }

    private void openNewNotebook() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Notebook");
        dialog.setHeaderText("Create a new notebook");
        dialog.setContentText("Notebook name:");

        Optional<String> result = dialog.showAndWait();

        if (result.isEmpty()) {
            return;
        }

        String notebookName = result.get().trim();

        if (notebookName.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Notebook name cannot be empty.", ButtonType.OK).showAndWait();
            return;
        }

        int notebookId = -1;
        if (apiClient != null) {
            notebookId = apiClient.createNotebook(notebookName);
        } else {
            try {
                notebookId = dbManager.createNotebook(notebookName, user.getId());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        if (notebookId == -1) {
            new Alert(Alert.AlertType.ERROR, "Failed to create notebook. Please try again.", ButtonType.OK).showAndWait();
            return;
        }

        try {
            NotebooksPage notebooksPage = new NotebooksPage();
            if (apiClient != null) {
                notebooksPage.setApiClient(apiClient);
            } else {
                notebooksPage.setDatabaseManager(dbManager);
            }
            notebooksPage.setNotebookId(notebookId);
            notebooksPage.setNotebookName(notebookName);
            notebooksPage.setAccessRole("OWNER");
            notebooksPage.setInitialContent(null);
            notebooksPage.setUserId(user.getId());
            notebooksPage.start(new Stage());
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Unable to open the notebook editor.").showAndWait();
        }
    }

    private void openExistingNotebook() {
        List<UMLDiagram> notebooks;
        
        if (apiClient != null) {
            notebooks = apiClient.getNotebooks();
        } else {
            try {
                notebooks = dbManager.getAccessibleNotebooks(user.getId());
            } catch (SQLException e) {
                e.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Failed to load notebooks.").showAndWait();
                return;
            }
        }

        if (notebooks.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No notebooks available yet. Create one or request a share!", ButtonType.OK).showAndWait();
            return;
        }

        Map<String, UMLDiagram> options = new LinkedHashMap<>();
        for (UMLDiagram diagram : notebooks) {
            String name = diagram.getDiagramName();
            if (name == null || name.isBlank()) {
                name = "Untitled Notebook";
            }
            String role = diagram.getAccessRole() != null ? diagram.getAccessRole().toUpperCase() : "OWNER";
            String display = String.format("%s (ID: %d, Role: %s)", name, diagram.getDiagramId(), role);
            options.put(display, diagram);
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(options.keySet().iterator().next(), options.keySet());
        dialog.setTitle("Open Notebook");
        dialog.setHeaderText("Select a notebook to open");
        dialog.setContentText("Available notebooks:");

        Optional<String> selection = dialog.showAndWait();

        if (selection.isEmpty()) {
            return;
        }

        UMLDiagram chosen = options.get(selection.get());
        if (chosen == null) {
            new Alert(Alert.AlertType.ERROR, "Unable to open the selected notebook.", ButtonType.OK).showAndWait();
            return;
        }

        try {
            NotebooksPage notebooksPage = new NotebooksPage();
            if (apiClient != null) {
                notebooksPage.setApiClient(apiClient);
            } else {
                notebooksPage.setDatabaseManager(dbManager);
            }
            notebooksPage.setNotebookId(chosen.getDiagramId());
            notebooksPage.setNotebookName(chosen.getDiagramName());
            notebooksPage.setAccessRole(chosen.getAccessRole() != null ? chosen.getAccessRole() : "OWNER");
            notebooksPage.setInitialContent(chosen.getContent());
            notebooksPage.setUserId(user.getId());
            notebooksPage.start(new Stage());
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Unable to open the notebook editor.").showAndWait();
        }
    }

    private void shareNotebook() {
        List<UMLDiagram> ownedNotebooks;
        
        if (apiClient != null) {
            ownedNotebooks = apiClient.getNotebooks().stream()
                .filter(n -> "OWNER".equalsIgnoreCase(n.getAccessRole()))
                .toList();
        } else {
            try {
                ownedNotebooks = dbManager.getNotebooksByOwner(user.getId());
            } catch (SQLException e) {
                e.printStackTrace();
                new Alert(Alert.AlertType.ERROR, "Failed to load notebooks.").showAndWait();
                return;
            }
        }

        if (ownedNotebooks.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "You must create a notebook before sharing.", ButtonType.OK).showAndWait();
            return;
        }

        Map<String, UMLDiagram> options = new LinkedHashMap<>();
        for (UMLDiagram diagram : ownedNotebooks) {
            String name = diagram.getDiagramName();
            if (name == null || name.isBlank()) {
                name = "Untitled Notebook";
            }
            String display = String.format("%s (ID: %d)", name, diagram.getDiagramId());
            options.put(display, diagram);
        }

        ChoiceDialog<String> notebookDialog = new ChoiceDialog<>(options.keySet().iterator().next(), options.keySet());
        notebookDialog.setTitle("Share Notebook");
        notebookDialog.setHeaderText("Select a notebook to share");
        notebookDialog.setContentText("Notebook:");

        Optional<String> notebookSelection = notebookDialog.showAndWait();
        if (notebookSelection.isEmpty()) {
            return;
        }

        UMLDiagram selectedNotebook = options.get(notebookSelection.get());
        if (selectedNotebook == null) {
            new Alert(Alert.AlertType.ERROR, "Could not determine the notebook to share.", ButtonType.OK).showAndWait();
            return;
        }

        TextInputDialog emailDialog = new TextInputDialog();
        emailDialog.setTitle("Share Notebook");
        emailDialog.setHeaderText("Invite a collaborator");
        emailDialog.setContentText("Recipient email:");

        Optional<String> emailInput = emailDialog.showAndWait();
        if (emailInput.isEmpty()) {
            return;
        }

        String email = emailInput.get().trim();
        if (email.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Email cannot be blank.", ButtonType.OK).showAndWait();
            return;
        }

        if (apiClient != null) {
            ChoiceDialog<String> roleDialog = new ChoiceDialog<>("EDITOR", List.of("EDITOR", "VIEWER"));
            roleDialog.setTitle("Share Role");
            roleDialog.setHeaderText("Select access role for " + email);
            roleDialog.setContentText("Role:");
            Optional<String> roleSelection = roleDialog.showAndWait();
            if (roleSelection.isEmpty()) {
                return;
            }
            String role = roleSelection.get();
            
            boolean success = apiClient.shareNotebook(selectedNotebook.getDiagramId(), email, role);
            if (success) {
                new Alert(Alert.AlertType.INFORMATION, String.format(
                        "Shared '%s' with %s as %s. They can now access it via 'Open Existing Notebook'.",
                        selectedNotebook.getDiagramName(), email, role
                ), ButtonType.OK).showAndWait();
            } else {
                new Alert(Alert.AlertType.ERROR, "Failed to share the notebook.").showAndWait();
            }
        } else {
            User recipient;
            try {
                recipient = dbManager.getUserByEmail(email);
            } catch (SQLException e) {
                new Alert(Alert.AlertType.ERROR, "Failed to find user.").showAndWait();
                return;
            }
            
            if (recipient == null) {
                new Alert(Alert.AlertType.ERROR, "No user found with that email.", ButtonType.OK).showAndWait();
                return;
            }

            if (recipient.getId() == user.getId()) {
                new Alert(Alert.AlertType.INFORMATION, "You already own this notebook.", ButtonType.OK).showAndWait();
                return;
            }

            ChoiceDialog<String> roleDialog = new ChoiceDialog<>("EDITOR", List.of("EDITOR", "VIEWER"));
            roleDialog.setTitle("Share Role");
            roleDialog.setHeaderText("Select access role for " + email);
            roleDialog.setContentText("Role:");
            Optional<String> roleSelection = roleDialog.showAndWait();
            if (roleSelection.isEmpty()) {
                return;
            }
            String role = roleSelection.get();

            boolean success;
            try {
                success = dbManager.shareNotebookWithUser(
                        selectedNotebook.getDiagramId(), user.getId(), recipient.getId(), null, role
                );
            } catch (SQLException e) {
                e.printStackTrace();
                success = false;
            }

            if (success) {
                new Alert(Alert.AlertType.INFORMATION, String.format(
                        "Shared '%s' with %s as %s. They can now access it via 'Open Existing Notebook'.",
                        selectedNotebook.getDiagramName(), email, role
                ), ButtonType.OK).showAndWait();
            } else {
                String reason = dbManager.getLastError();
                if (reason == null || reason.isBlank()) {
                    reason = "Failed to share the notebook. Please try again.";
                }
                new Alert(Alert.AlertType.ERROR, reason, ButtonType.OK).showAndWait();
            }
        }
    }
}