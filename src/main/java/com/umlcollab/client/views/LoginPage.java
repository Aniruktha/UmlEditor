package com.umlcollab.client.views;

import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.User;
import com.umlcollab.server.services.AuthService;
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
    private final AuthService authService;
    private final Consumer<User> onLoginSuccess;

    public LoginPage(DatabaseManager dbManager, Consumer<User> onLoginSuccess) {
        this.dbManager = dbManager;
        this.authService = new AuthService(dbManager);
        this.onLoginSuccess = onLoginSuccess;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Collaborative UML Editor - Login");

        Label lblTitle = new Label("Collaborative UML Editor");
        lblTitle.setId("label-title");
        lblTitle.getStyleClass().add("label-title");

        TextField txtUsername = new TextField();
        txtUsername.setPromptText("Username");
        txtUsername.setId("txtUsername");
        txtUsername.getStyleClass().add("text-field");

        PasswordField txtPassword = new PasswordField();
        txtPassword.setPromptText("Password");
        txtPassword.setId("txtPassword");
        txtPassword.getStyleClass().add("password-field");

        Button btnLogin = new Button("Login");
        btnLogin.setId("btnLogin");
        btnLogin.getStyleClass().add("button-login");

        Button btnSignup = new Button("Sign Up");
        btnSignup.setId("btnSignup");
        btnSignup.getStyleClass().add("button-signup");

        Label lblMessage = new Label();
        lblMessage.setId("lblMessage");
        lblMessage.getStyleClass().add("label-message");

        VBox card = new VBox(15, lblTitle, txtUsername, txtPassword, btnLogin, btnSignup, lblMessage);
        card.setId("loginCard");
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40));

        StackPane root = new StackPane(card);
        root.setId("root");
        root.getStyleClass().add("root");

        Scene scene = new Scene(root, 600, 480);
        
        // Load CSS stylesheet
        loadStylesheet(scene);

        stage.setScene(scene);
        stage.setResizable(false);
        stage.centerOnScreen();
        stage.show();

        // Login Button
        btnLogin.setOnAction(e -> {
            String username = txtUsername.getText().trim();
            String password = txtPassword.getText();

            // Validation
            if (username.isEmpty() || password.isEmpty()) {
                lblMessage.setText("Username and password are required!");
                lblMessage.setTextFill(Color.RED);
                return;
            }

            // Use AuthService for login
            AuthService.LoginResult result = authService.loginUser(username, password);
            
            if (result.isSuccess()) {
                lblMessage.setText("Login successful!");
                lblMessage.setTextFill(Color.GREEN);
                stage.close();

                if (onLoginSuccess != null) {
                    onLoginSuccess.accept(result.getUser());
                }
            } else {
                lblMessage.setText(result.getMessage());
                lblMessage.setTextFill(Color.RED);
            }
        });

        // Sign-up Button
        btnSignup.setOnAction(e -> {
            stage.close();
            new SigninPage(dbManager, onLoginSuccess).show();
        });
    }

    private void loadStylesheet(Scene scene) {
        String cssPath = getClass().getResource("/styles/style.css") != null 
            ? getClass().getResource("/styles/style.css").toExternalForm()
            : getClass().getClassLoader().getResource("styles/style.css") != null
                ? getClass().getClassLoader().getResource("styles/style.css").toExternalForm()
                : null;
        
        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }
    }
}