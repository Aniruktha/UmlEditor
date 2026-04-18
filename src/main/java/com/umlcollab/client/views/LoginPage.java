package com.umlcollab.client.views;

import com.umlcollab.client.ApiClient;
import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.User;
import com.umlcollab.server.services.AuthService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class LoginPage {
    private static final Logger logger = LoggerFactory.getLogger(LoginPage.class);

    private final DatabaseManager dbManager;
    private final ApiClient apiClient;
    private final AuthService authService;
    private final Consumer<User> onLoginSuccess;

    // Constructor for local database mode
    public LoginPage(DatabaseManager dbManager, Consumer<User> onLoginSuccess) {
        this.dbManager = dbManager;
        this.apiClient = null;
        this.authService = dbManager != null ? new AuthService(dbManager) : null;
        this.onLoginSuccess = onLoginSuccess;
    }

    // Constructor for remote server mode (also supports local if dbManager provided)
    public LoginPage(DatabaseManager dbManager, ApiClient apiClient, Consumer<User> onLoginSuccess) {
        this.dbManager = dbManager;
        this.apiClient = apiClient;
        this.authService = dbManager != null ? new AuthService(dbManager) : null;
        this.onLoginSuccess = onLoginSuccess;
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle("Collaborative UML Editor - Login");

        Label lblTitle = new Label("Collaborative UML Editor");
        lblTitle.setId("label-title");
        lblTitle.getStyleClass().add("label-title");

        TextField txtEmail = new TextField();
        txtEmail.setPromptText("Email");
        txtEmail.setId("txtEmail");
        txtEmail.getStyleClass().add("text-field");

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

        VBox card = new VBox(15, lblTitle, txtEmail, txtPassword, btnLogin, btnSignup, lblMessage);
        card.setId("loginCard");
        card.getStyleClass().add("login-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40));

        StackPane root = new StackPane(card);
        root.setId("root");
        root.getStyleClass().add("root");

        Scene scene = new Scene(root, 600, 480);
        
        loadStylesheet(scene);

        stage.setScene(scene);
        stage.setResizable(false);
        stage.centerOnScreen();
        stage.show();

        btnLogin.setOnAction(e -> {
            String email = txtEmail.getText().trim();
            String password = txtPassword.getText();

            if (email.isEmpty() || password.isEmpty()) {
                lblMessage.setText("Email and password are required!");
                lblMessage.setTextFill(Color.RED);
                return;
            }

            lblMessage.setText("Logging in...");
            lblMessage.setTextFill(Color.BLUE);
            btnLogin.setDisable(true);

            Task<User> loginTask = new Task<>() {
                @Override
                protected User call() {
                    if (authService != null) {
                        AuthService.LoginResult result = authService.loginUser(email, password);
                        if (result.isSuccess()) {
                            return result.getUser();
                        }
                        if (apiClient != null) {
                            boolean remoteSuccess = apiClient.login(email, password);
                            if (remoteSuccess) {
                                return new User(apiClient.getUserId(), apiClient.getUsername(), apiClient.getEmail(), null, null);
                            }
                        }
                    } else if (apiClient != null) {
                        boolean success = apiClient.login(email, password);
                        if (success) {
                            return new User(apiClient.getUserId(), apiClient.getUsername(), apiClient.getEmail(), null, null);
                        }
                    }
                    return null;
                }

                @Override
                protected void succeeded() {
                    btnLogin.setDisable(false);
                    User user = getValue();
                    if (user != null) {
                        lblMessage.setText("Login successful!");
                        lblMessage.setTextFill(Color.GREEN);
                        stage.close();
                        if (onLoginSuccess != null) {
                            onLoginSuccess.accept(user);
                        }
                    } else {
                        lblMessage.setText("Invalid credentials");
                        lblMessage.setTextFill(Color.RED);
                    }
                }

                @Override
                protected void failed() {
                    btnLogin.setDisable(false);
                    lblMessage.setText("Login failed: " + getException().getMessage());
                    lblMessage.setTextFill(Color.RED);
                }
            };

            new Thread(loginTask).start();
        });

        btnSignup.setOnAction(e -> {
            stage.close();
            new SigninPage(dbManager, apiClient, onLoginSuccess).show();
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