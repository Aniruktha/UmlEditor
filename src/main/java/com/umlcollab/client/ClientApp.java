package com.umlcollab.client;

import com.umlcollab.client.views.LoginPage;
import com.umlcollab.client.views.MyNotebooksPage;
import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.User;
import javafx.application.Application;
import javafx.stage.Stage;

public class ClientApp extends Application {

    private DatabaseManager dbManager;

    @Override
    public void start(Stage primaryStage) {
        // ✅ Initialize the database connection
        dbManager = new DatabaseManager();
        dbManager.connect();

        // ✅ Show login page and redirect to MyNotebooksPage after login success
        LoginPage loginPage = new LoginPage(dbManager, this::openMyNotebooksPage);
        loginPage.show();
    }

    /** Opens the My Notebooks page after successful login */
    private void openMyNotebooksPage(User loggedInUser) {
        MyNotebooksPage notebooksPage = new MyNotebooksPage(dbManager, loggedInUser);
        notebooksPage.showFullscreen();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
