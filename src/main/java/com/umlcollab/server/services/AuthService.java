package com.umlcollab.server.services;

import com.umlcollab.server.db.DatabaseManager;
import com.umlcollab.server.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles user authentication logic.
 * This service provides a clean separation between UI and authentication logic.
 */
public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    
    private final DatabaseManager dbManager;

    public AuthService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Registers a new user with secure password hashing.
     * @param username username
     * @param email user's email
     * @param password user's password (will be hashed)
     * @return RegistrationResult with success status and message
     */
    public RegistrationResult registerUser(String username, String email, String password) {
        // Validate inputs
        if (username == null || username.isBlank()) {
            return RegistrationResult.failure("Username cannot be empty");
        }
        if (email == null || email.isBlank()) {
            return RegistrationResult.failure("Email cannot be empty");
        }
        if (password == null || password.isBlank()) {
            return RegistrationResult.failure("Password cannot be empty");
        }
        
        // Check username length
        if (username.length() < 3 || username.length() > 50) {
            return RegistrationResult.failure("Username must be between 3 and 50 characters");
        }
        
        // Check password strength
        if (password.length() < 6) {
            return RegistrationResult.failure("Password must be at least 6 characters");
        }
        
        // Check if user already exists
        if (dbManager.getUserByUsername(username) != null) {
            return RegistrationResult.failure("Username already exists");
        }
        
        // Check if email already exists
        try {
            if (dbManager.getUserByEmail(email) != null) {
                return RegistrationResult.failure("Email already registered");
            }
        } catch (Exception e) {
            logger.warn("Error checking email: {}", e.getMessage());
        }

        // Hash password with BCrypt
        String hashedPassword = dbManager.hashPassword(password);
        
        // Create and save new user
        User newUser = new User(username, email, hashedPassword);
        boolean saved = dbManager.saveUser(newUser);
        
        if (saved) {
            logger.info("New user registered: {}", username);
            return RegistrationResult.success("Registration successful");
        } else {
            return RegistrationResult.failure("Database error - please try again");
        }
    }

    /**
     * Logs in a user with secure password verification.
     * @param username username
     * @param password password
     * @return LoginResult with user object on success or error message on failure
     */
    public LoginResult loginUser(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return LoginResult.failure("Username and password are required");
        }
        
        User user = dbManager.getUserByUsername(username);
        if (user == null) {
            return LoginResult.failure("Invalid username or password");
        }
        
        // Use BCrypt to verify password
        boolean verified = dbManager.verifyPassword(password, user.getPassword());
        
        if (verified) {
            // Check if password needs migration from SHA-256 to BCrypt
            String storedHash = user.getPassword();
            if (storedHash != null && storedHash.length() != 60) {
                // Old SHA-256 hash - migrate to BCrypt
                String newHash = dbManager.hashPassword(password);
                dbManager.updatePasswordHash(user.getId(), newHash);
                logger.info("Migrated user {} password from SHA-256 to BCrypt", username);
            }
            
            logger.info("User logged in: {}", username);
            return LoginResult.success(user);
        } else {
            return LoginResult.failure("Invalid username or password");
        }
    }
    
    /**
     * Result wrapper for registration operations
     */
    public static class RegistrationResult {
        private final boolean success;
        private final String message;
        
        private RegistrationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
        
        public static RegistrationResult success(String message) {
            return new RegistrationResult(true, message);
        }
        
        public static RegistrationResult failure(String message) {
            return new RegistrationResult(false, message);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
    
    /**
     * Result wrapper for login operations
     */
    public static class LoginResult {
        private final boolean success;
        private final User user;
        private final String message;
        
        private LoginResult(boolean success, User user, String message) {
            this.success = success;
            this.user = user;
            this.message = message;
        }
        
        public static LoginResult success(User user) {
            return new LoginResult(true, user, null);
        }
        
        public static LoginResult failure(String message) {
            return new LoginResult(false, null, message);
        }
        
        public boolean isSuccess() { return success; }
        public User getUser() { return user; }
        public String getMessage() { return message; }
    }
}