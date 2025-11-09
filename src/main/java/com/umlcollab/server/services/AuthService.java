package com.umlcollab.server.services;

import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.User;

/**
 * Handles user authentication logic.
 */
public class AuthService {

    private DatabaseManager dbManager;

    public AuthService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Registers a new user.
     * @param username username
     * @param email user's email
     * @param password user's password
     * @return true if registration successful, false if username exists
     */
    public boolean registerUser(String username, String email, String password) {
        // Check if user already exists
        if (dbManager.getUserByUsername(username) != null) {
            return false; // username already taken
        }

        // Create new user object and save to DB
        User newUser = new User(username, email, password);
        return dbManager.saveUser(newUser);
    }

    /**
     * Logs in a user.
     * @param username username
     * @param password password
     * @return true if credentials match, false otherwise
     */
    public boolean loginUser(String username, String password) {
        User user = dbManager.getUserByUsername(username);
        if (user == null) return false;

        // For now, plain-text comparison; hashing can be added later
        return user.getPassword().equals(password);
    }
}
