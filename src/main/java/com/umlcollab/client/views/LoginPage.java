//package com.umlcollab.client.views;
//
//import com.umlcollab.server.db.DatabaseManager;
//import com.umlcollab.server.models.User;
//import javafx.geometry.Insets;
//import javafx.geometry.Pos;
//import javafx.scene.Scene;
//import javafx.scene.control.*;
//import javafx.scene.layout.*;
//import javafx.scene.paint.Color;
//import javafx.stage.Stage;
//
//public class LoginPage {
//
//    private final DatabaseManager dbManager;
//
//    public LoginPage(DatabaseManager dbManager) {
//        this.dbManager = dbManager;
//    }
//
//    public void show() {
//        Stage stage = new Stage();
//        stage.setTitle("Collaborative UML Editor - Login");
//
//        // Title
//        Label lblTitle = new Label("Collaborative UML Editor");
//        lblTitle.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
//
//        // Input fields
//        TextField txtUsername = new TextField();
//        txtUsername.setPromptText("Username");
//        txtUsername.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #dfe6e9; "
//                + "-fx-padding: 10; -fx-font-size: 14; -fx-background-color: #f5f6fa;");
//
//        PasswordField txtPassword = new PasswordField();
//        txtPassword.setPromptText("Password");
//        txtPassword.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #dfe6e9; "
//                + "-fx-padding: 10; -fx-font-size: 14; -fx-background-color: #f5f6fa;");
//
//        // Buttons
//        Button btnLogin = new Button("Login");
//        btnLogin.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white; -fx-font-weight: bold; "
//                + "-fx-background-radius: 8; -fx-padding: 10 25; -fx-font-size: 14;");
//        btnLogin.setOnMouseEntered(e -> btnLogin.setStyle("-fx-background-color: #74b9ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10 25;"));
//        btnLogin.setOnMouseExited(e -> btnLogin.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10 25;"));
//
//        Button btnSignup = new Button("Sign Up");
//        btnSignup.setStyle("-fx-background-color: #00b894; -fx-text-fill: white; -fx-font-weight: bold; "
//                + "-fx-background-radius: 8; -fx-padding: 10 25; -fx-font-size: 14;");
//        btnSignup.setOnMouseEntered(e -> btnSignup.setStyle("-fx-background-color: #55efc4; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10 25;"));
//        btnSignup.setOnMouseExited(e -> btnSignup.setStyle("-fx-background-color: #00b894; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 10 25;"));
//
//        Label lblMessage = new Label();
//        lblMessage.setStyle("-fx-font-size: 13px;");
//
//        // Layout
//        VBox card = new VBox(15, lblTitle, txtUsername, txtPassword, btnLogin, btnSignup, lblMessage);
//        card.setAlignment(Pos.CENTER);
//        card.setPadding(new Insets(40));
//        card.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 15, 0, 0, 6);");
//
//        StackPane root = new StackPane(card);
//        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #74b9ff, #a29bfe);");
//
//        Scene scene = new Scene(root, 600, 480);
//        stage.setScene(scene);
//        stage.setResizable(false);
//        stage.centerOnScreen();
//        stage.show();
//
//        // Login Event
//        btnLogin.setOnAction(e -> {
//            String username = txtUsername.getText().trim();
//            String password = txtPassword.getText().trim();
//
//            if (username.isEmpty() || password.isEmpty()) {
//                lblMessage.setText("Enter username and password!");
//                lblMessage.setTextFill(Color.RED);
//                return;
//            }
//
//            User user = dbManager.getUserByUsername(username);
//            if (user != null && user.getPassword().equals(dbManager.hashPassword(password))) {
//                lblMessage.setText("✅ Login successful!");
//                lblMessage.setTextFill(Color.GREEN);
//                stage.close();
//                // TODO: Open main UML editor
//            } else {
//                lblMessage.setText("Invalid username or password.");
//                lblMessage.setTextFill(Color.RED);
//            }
//        });
//
//        // Signup Redirect
//        btnSignup.setOnAction(e -> {
//            stage.close();
//            new SigninPage(dbManager).show();
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
import java.util.function.Consumer;

public class LoginPage {

    private final DatabaseManager dbManager;
    private final Consumer<User> onLoginSuccess;

    public LoginPage(DatabaseManager dbManager, Consumer<User> onLoginSuccess) {
        this.dbManager = dbManager;
        this.onLoginSuccess = onLoginSuccess;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Collaborative UML Editor - Login");

        Label lblTitle = new Label("Collaborative UML Editor");
        lblTitle.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        TextField txtUsername = new TextField();
        txtUsername.setPromptText("Username");
        txtUsername.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #dfe6e9; "
                + "-fx-padding: 10; -fx-font-size: 14; -fx-background-color: #f5f6fa;");

        PasswordField txtPassword = new PasswordField();
        txtPassword.setPromptText("Password");
        txtPassword.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #dfe6e9; "
                + "-fx-padding: 10; -fx-font-size: 14; -fx-background-color: #f5f6fa;");

        Button btnLogin = new Button("Login");
        btnLogin.setStyle("-fx-background-color: #0984e3; -fx-text-fill: white; -fx-font-weight: bold; "
                + "-fx-background-radius: 8; -fx-padding: 10 25; -fx-font-size: 14;");

        Button btnSignup = new Button("Sign Up");
        btnSignup.setStyle("-fx-background-color: #00b894; -fx-text-fill: white; -fx-font-weight: bold; "
                + "-fx-background-radius: 8; -fx-padding: 10 25; -fx-font-size: 14;");

        Label lblMessage = new Label();
        lblMessage.setStyle("-fx-font-size: 13px;");

        VBox card = new VBox(15, lblTitle, txtUsername, txtPassword, btnLogin, btnSignup, lblMessage);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40));
        card.setStyle("-fx-background-color: white; -fx-background-radius: 15; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 15, 0, 0, 6);");

        StackPane root = new StackPane(card);
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #74b9ff, #a29bfe);");

        Scene scene = new Scene(root, 600, 480);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.centerOnScreen();
        stage.show();

        // Login Button
        btnLogin.setOnAction(e -> {
            String username = txtUsername.getText().trim();
            String password = txtPassword.getText().trim();

            if (username.isEmpty() || password.isEmpty()) {
                lblMessage.setText("Enter username and password!");
                lblMessage.setTextFill(Color.RED);
                return;
            }

            User user = dbManager.getUserByUsername(username);
            if (user != null && user.getPassword().equals(dbManager.hashPassword(password))) {
                lblMessage.setText("Login successful!");
                lblMessage.setTextFill(Color.GREEN);
                stage.close();

                if (onLoginSuccess != null) onLoginSuccess.accept(user);
            } else {
                lblMessage.setText("Invalid username or password.");
                lblMessage.setTextFill(Color.RED);
            }
        });

        // Sign-up Button
        btnSignup.setOnAction(e -> {
            stage.close();
            new SigninPage(dbManager).show();
        });
    }
}
