package com.umlcollab.client.views;

import com.umlcollab.client.ApiClient;
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

public class SigninPage {

    private final DatabaseManager dbManager;
    private final ApiClient apiClient;
    private final AuthService authService;
    private final Consumer<User> onLoginSuccess;

    public SigninPage(DatabaseManager dbManager, ApiClient apiClient, Consumer<User> onLoginSuccess) {
        this.dbManager = dbManager;
        this.apiClient = apiClient;
        this.authService = dbManager != null ? new AuthService(dbManager) : null;
        this.onLoginSuccess = onLoginSuccess;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Collaborative UML Editor - Sign Up");

        Label lblTitle = new Label("Create Account");
        lblTitle.setId("lblTitle");
        lblTitle.getStyleClass().add("label-signup-title");

        TextField txtUsername = new TextField();
        txtUsername.setPromptText("Username");
        txtUsername.setId("txtUsername");
        txtUsername.getStyleClass().add("text-field");

        TextField txtEmail = new TextField();
        txtEmail.setPromptText("Email");
        txtEmail.setId("txtEmail");
        txtEmail.getStyleClass().add("text-field");

        PasswordField txtPassword = new PasswordField();
        txtPassword.setPromptText("Password");
        txtPassword.setId("txtPassword");
        txtPassword.getStyleClass().add("password-field");

        Button btnRegister = new Button("Register");
        btnRegister.setId("btnRegister");
        btnRegister.getStyleClass().add("button-register");
        
        Button btnBack = new Button("Back to Login");
        btnBack.setId("btnBack");
        btnBack.getStyleClass().add("button-signup");
        
        Label lblMessage = new Label();
        lblMessage.setId("lblMessage");
        lblMessage.getStyleClass().add("label-signup-message");

        VBox card = new VBox(15, lblTitle, txtUsername, txtEmail, txtPassword, btnRegister, btnBack, lblMessage);
        card.setId("signupCard");
        card.getStyleClass().add("signup-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40));

        StackPane root = new StackPane(card);
        root.setId("root");
        root.getStyleClass().add("root");

        Scene scene = new Scene(root, 600, 500);
        
        loadStylesheet(scene);

        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();

        btnRegister.setOnAction(e -> {
            String username = txtUsername.getText().trim();
            String email = txtEmail.getText().trim();
            String password = txtPassword.getText();

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                lblMessage.setText("All fields are required!");
                lblMessage.setTextFill(Color.RED);
                return;
            }
            
            if (username.length() < 3 || username.length() > 50) {
                lblMessage.setText("Username must be 3-50 characters!");
                lblMessage.setTextFill(Color.RED);
                return;
            }
            
            if (!isValidEmail(email)) {
                lblMessage.setText("Please enter a valid email!");
                lblMessage.setTextFill(Color.RED);
                return;
            }
            
            if (password.length() < 6) {
                lblMessage.setText("Password must be at least 6 characters!");
                lblMessage.setTextFill(Color.RED);
                return;
            }

            boolean success = false;
            User user = null;

            if (apiClient != null) {
                success = apiClient.register(username, email, password);
                if (success) {
                    success = apiClient.login(email, password);
                    if (success) {
                        user = new User(apiClient.getUserId(), apiClient.getUsername(), apiClient.getEmail(), null, null);
                    }
                }
            } else if (authService != null) {
                AuthService.RegistrationResult result = authService.registerUser(username, email, password);
                if (result.isSuccess()) {
                    AuthService.LoginResult loginResult = authService.loginUser(email, password);
                    success = loginResult.isSuccess();
                    user = loginResult.getUser();
                } else {
                    lblMessage.setText(result.getMessage());
                    lblMessage.setTextFill(Color.RED);
                    return;
                }
            }

            if (success && user != null) {
                stage.close();
                if (onLoginSuccess != null) {
                    onLoginSuccess.accept(user);
                }
            }
        });
        
        btnBack.setOnAction(e -> {
            stage.close();
            new LoginPage(dbManager, apiClient, onLoginSuccess).show();
        });
    }
    
    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
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