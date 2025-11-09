//package com.umlcollab.client.views;
//
//import com.umlcollab.server.db.DatabaseManager;
//import com.umlcollab.server.models.User;
//import javafx.geometry.Pos;
//import javafx.scene.Scene;
//import javafx.scene.control.*;
//import javafx.scene.layout.*;
//import javafx.scene.paint.Color;
//import javafx.stage.Stage;
//import java.util.Objects;
//
//public class SigninPage {
//
//    private final DatabaseManager dbManager;
//
//    public SigninPage(DatabaseManager dbManager) {
//        this.dbManager = dbManager;
//    }
//
//    public void show() {
//        Stage stage = new Stage();
//        stage.setTitle("Collaborative UML Editor - Sign Up");
//
//        Label lblTitle = new Label("Create Account");
//        lblTitle.getStyleClass().add("label-signup-title");
//
//        TextField txtUsername = new TextField();
//        txtUsername.setPromptText("Username");
//        txtUsername.getStyleClass().add("text-field");
//
//        TextField txtEmail = new TextField();
//        txtEmail.setPromptText("Email");
//        txtEmail.getStyleClass().add("text-field");
//
//        PasswordField txtPassword = new PasswordField();
//        txtPassword.setPromptText("Password");
//        txtPassword.getStyleClass().add("password-field");
//
//        Button btnRegister = new Button("Register");
//        btnRegister.getStyleClass().add("button-register");
//
//        Label lblMessage = new Label();
//        lblMessage.getStyleClass().add("label-signup-message");
//
//        VBox card = new VBox(15, lblTitle, txtUsername, txtEmail, txtPassword, btnRegister, lblMessage);
//        card.setAlignment(Pos.CENTER);
//        card.getStyleClass().add("signup-card");
//
//        StackPane root = new StackPane(card);
//
//        Scene scene = new Scene(root, 600, 500);
//
//        try {
//            String cssPath;
//            if (getClass().getResource("/styles/style.css") != null) {
//                cssPath = getClass().getResource("/styles/style.css").toExternalForm();
//            } else if (getClass().getClassLoader().getResource("styles/style.css") != null) {
//                cssPath = getClass().getClassLoader().getResource("styles/style.css").toExternalForm();
//            } else {
//                throw new RuntimeException("style.css not found");
//            }
//
//            scene.getStylesheets().add(cssPath);
//            System.out.println("✅ Loaded CSS from: " + cssPath);
//        } catch (Exception ex) {
//            System.err.println("❌ Could not load CSS file: " + ex.getMessage());
//        }
//
//
//        stage.setScene(scene);
//        stage.centerOnScreen();
//        stage.show();
//
//        btnRegister.setOnAction(e -> {
//            String username = txtUsername.getText().trim();
//            String email = txtEmail.getText().trim();
//            String password = txtPassword.getText().trim();
//
//            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
//                lblMessage.setText("All fields are required!");
//                lblMessage.setTextFill(Color.RED);
//                return;
//            }
//
//            User existing = dbManager.getUserByUsername(username);
//            if (existing != null) {
//                lblMessage.setText("Username already exists!");
//                lblMessage.setTextFill(Color.RED);
//                return;
//            }
//
//            String hashed = dbManager.hashPassword(password);
//            User newUser = new User(username, email, hashed);
//
//            if (dbManager.saveUser(newUser)) {
//                lblMessage.setText("✅ Registration successful!");
//                lblMessage.setTextFill(Color.GREEN);
//                stage.close();
//                new LoginPage(dbManager).show();
//            } else {
//                lblMessage.setText("Database error — check connection!");
//                lblMessage.setTextFill(Color.RED);
//            }
//        });
//    }
//}








package com.umlcollab.client.views;

import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class SigninPage {

    private final DatabaseManager dbManager;

    public SigninPage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Collaborative UML Editor - Sign Up");

        Label lblTitle = new Label("Create Account");
        lblTitle.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        TextField txtUsername = new TextField();
        txtUsername.setPromptText("Username");
        txtUsername.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #dfe6e9; "
                + "-fx-padding: 10; -fx-font-size: 14; -fx-background-color: #f5f6fa;");

        TextField txtEmail = new TextField();
        txtEmail.setPromptText("Email");
        txtEmail.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #dfe6e9; "
                + "-fx-padding: 10; -fx-font-size: 14; -fx-background-color: #f5f6fa;");

        PasswordField txtPassword = new PasswordField();
        txtPassword.setPromptText("Password");
        txtPassword.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #dfe6e9; "
                + "-fx-padding: 10; -fx-font-size: 14; -fx-background-color: #f5f6fa;");

        Button btnRegister = new Button("Register");
        btnRegister.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; "
                + "-fx-background-radius: 8; -fx-padding: 10 25; -fx-font-size: 14;");
        btnRegister.setOnMouseEntered(e -> btnRegister.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10 25;"));
        btnRegister.setOnMouseExited(e -> btnRegister.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10 25;"));

        Label lblMessage = new Label();
        lblMessage.setStyle("-fx-font-size: 13px;");

        VBox card = new VBox(15, lblTitle, txtUsername, txtEmail, txtPassword, btnRegister, lblMessage);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 15, 0, 0, 6);");

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #74b9ff, #a29bfe);");

        Scene scene = new Scene(root, 600, 500);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        btnRegister.setOnAction(e -> {
            String username = txtUsername.getText().trim();
            String email = txtEmail.getText().trim();
            String password = txtPassword.getText().trim();

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                lblMessage.setText("All fields are required!");
                lblMessage.setTextFill(Color.RED);
                return;
            }

            User existing = dbManager.getUserByUsername(username);
            if (existing != null) {
                lblMessage.setText("Username already exists!");
                lblMessage.setTextFill(Color.RED);
                return;
            }

            String hashed = dbManager.hashPassword(password);
            User newUser = new User(username, email, hashed);

            if (dbManager.saveUser(newUser)) {
                lblMessage.setText("Registration successful!");
                lblMessage.setTextFill(Color.GREEN);
                stage.close();
                new LoginPage(dbManager, user -> {
                    // After successful login, show their notebooks page
                    new com.umlcollab.client.views.MyNotebooksPage(dbManager, user).showFullscreen();
                }).show();
            } else {
                lblMessage.setText("Database error — check connection!");
                lblMessage.setTextFill(Color.RED);
            }
        });
    }
}
