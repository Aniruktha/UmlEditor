package com.umlcollab.server.db;

import com.umlcollab.server.models.UMLDiagram;
import com.umlcollab.server.models.User;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    
    private HikariDataSource dataSource;
    private String lastError;

    public void connect() {
        String url = "jdbc:mysql://localhost:3306/uml_editor";
        String user = "root";
        String password = System.getenv("DB_PASSWORD");
        
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("DB_PASSWORD env var must be set!");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        // Connection pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(20000);
        config.setMaxLifetime(1800000);
        
        // Performance optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        try {
            dataSource = new HikariDataSource(config);
            logger.info("Database connected successfully with connection pooling");
        } catch (Exception e) {
            logger.error("Database connection failed: {}", e.getMessage());
            throw new RuntimeException("Failed to connect to database", e);
        }
    }

    public Connection getConnection() {
        if (dataSource == null) {
            throw new IllegalStateException("Database not initialized. Call connect() first.");
        }
        try {
            Connection conn = dataSource.getConnection();
            if (conn.isClosed()) {
                throw new IllegalStateException("Connection is closed");
            }
            return conn;
        } catch (SQLException e) {
            logger.error("Failed to get connection from pool", e);
            throw new RuntimeException("Failed to get database connection", e);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }

    public String getLastError() {
        return lastError;
    }

    private void setLastError(String message) {
        this.lastError = message;
        if (message != null) {
            logger.warn(message);
        }
    }

    public boolean saveUser(User user) {
        String query = "INSERT INTO Users (username, email, password) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPassword());
            stmt.executeUpdate();
            logger.info("User created: {}", user.getUsername());
            return true;
        } catch (SQLException e) {
            logger.error("Failed to save user: {}", e.getMessage());
            return false;
        }
    }

    public User getUserByUsername(String username) {
        String sql = "SELECT * FROM Users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int id = rs.getInt("user_id");
                String email = rs.getString("email");
                String password = rs.getString("password");
                Timestamp ts = rs.getTimestamp("created_at");
                LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : null;
                return new User(id, username, email, password, createdAt);
            }
        } catch (SQLException e) {
            logger.error("Error fetching user by username: {}", e.getMessage());
        }
        return null;
    }

    public User getUserByEmail(String email) throws SQLException {
        if (email == null || email.isBlank()) {
            return null;
        }

        String sql = "SELECT * FROM Users WHERE email = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email.trim());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int id = rs.getInt("user_id");
                String username = rs.getString("username");
                String password = rs.getString("password");
                Timestamp ts = rs.getTimestamp("created_at");
                LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : null;
                return new User(id, username, email.trim(), password, createdAt);
            }
        } catch (SQLException e) {
            logger.error("Error fetching user by email: {}", e.getMessage());
        }
        return null;
    }

    public String hashPassword(String password) {
        // BCrypt generates a 60-character hash with salt
        return org.mindrot.jbcrypt.BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    public boolean verifyPassword(String password, String hashedPassword) {
        if (hashedPassword == null || password == null) {
            return false;
        }
        
        try {
            // Check if it's a BCrypt hash (starts with $2a$, $2b$, or $2y$)
            if (hashedPassword.length() == 60 && hashedPassword.startsWith("$2")) {
                return BCrypt.checkpw(password, hashedPassword);
            }
            
            // Fallback: Check if it's an old SHA-256 hash (for backward compatibility)
            String sha256Hash = hashPasswordSHA256(password);
            if (hashedPassword.equals(sha256Hash)) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            logger.error("Error verifying password", e);
            return false;
        }
    }
    
    public boolean updatePasswordHash(int userId, String newHash) {
        String sql = "UPDATE Users SET password = ? WHERE user_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newHash);
            stmt.setInt(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating password hash", e);
            return false;
        }
    }
    
    // Legacy SHA-256 hashing for backward compatibility
    private String hashPasswordSHA256(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            logger.error("Error creating SHA-256 hash", e);
            return null;
        }
    }

    public int createNotebook(String diagramName, int ownerId) throws SQLException {
        if (diagramName == null || diagramName.isBlank()) {
            throw new IllegalArgumentException("diagramName cannot be null or blank");
        }

        String sql = "INSERT INTO UML_Diagrams (diagram_name, owner_id, last_modified) VALUES (?, ?, NOW())";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, diagramName);
            stmt.setInt(2, ownerId);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                logger.error("Creating notebook failed, no rows affected.");
                return -1;
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newDiagramId = generatedKeys.getInt(1);
                    logDiagramHistory(newDiagramId, ownerId, "Created new diagram");
                    logger.info("Created notebook: {} with ID: {}", diagramName, newDiagramId);
                    return newDiagramId;
                }
            }
            return -1;
        } catch (SQLException e) {
            logger.error("SQL error creating notebook: {}", e.getMessage());
            throw e;
        }
    }

    public List<UMLDiagram> getNotebooksByOwner(int ownerId) throws SQLException {
        List<UMLDiagram> diagrams = new ArrayList<>();

        String sql = "SELECT diagram_id, diagram_name, project_id, owner_id, mongo_id, content, last_modified " +
                "FROM UML_Diagrams WHERE owner_id = ? ORDER BY last_modified DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, ownerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UMLDiagram diagram = mapDiagram(rs);
                    diagram.setAccessRole("OWNER");
                    diagrams.add(diagram);
                }
            }
        } catch (SQLException e) {
            logger.error("SQL error fetching notebooks: {}", e.getMessage());
            throw e;
        }
        return diagrams;
    }

    public boolean updateNotebookContent(int diagramId, String content) throws SQLException {
        String sql = "UPDATE UML_Diagrams SET content = ?, last_modified = NOW() WHERE diagram_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (content != null && !content.isEmpty()) {
                stmt.setString(1, content);
            } else {
                stmt.setNull(1, Types.LONGVARCHAR);
            }
            stmt.setInt(2, diagramId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("SQL error updating notebook: {}", e.getMessage());
            throw e;
        }
    }

    public List<UMLDiagram> getAccessibleNotebooks(int userId) throws SQLException {
        List<UMLDiagram> diagrams = new ArrayList<>();

        String sql = """
            SELECT DISTINCT d.diagram_id, d.diagram_name, d.project_id, d.owner_id, d.mongo_id, d.content, d.last_modified,
                   CASE 
                       WHEN d.owner_id = ? THEN 'OWNER'
                       ELSE COALESCE(pm.role, 'VIEWER')
                   END as access_role
            FROM UML_Diagrams d
            LEFT JOIN Project_Members pm ON d.project_id = pm.project_id AND pm.user_id = ?
            WHERE d.owner_id = ? 
               OR (d.project_id IS NOT NULL AND pm.user_id = ?)
            ORDER BY d.last_modified DESC
            """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            stmt.setInt(paramIndex++, userId);
            stmt.setInt(paramIndex++, userId);
            stmt.setInt(paramIndex++, userId);
            stmt.setInt(paramIndex, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UMLDiagram diagram = mapDiagram(rs);
                    diagram.setAccessRole(rs.getString("access_role"));
                    diagrams.add(diagram);
                }
            }
        } catch (SQLException e) {
            logger.error("SQL error fetching accessible notebooks: {}", e.getMessage());
            throw e;
        }
        return diagrams;
    }

    public boolean shareNotebookWithUser(int diagramId, int ownerId, int recipientUserId, Integer projectId, String role) throws SQLException {
        if (role == null || (!role.equalsIgnoreCase("EDITOR") && !role.equalsIgnoreCase("VIEWER"))) {
            setLastError("Role must be EDITOR or VIEWER");
            return false;
        }

        UMLDiagram diagram = getDiagramById(diagramId);
        if (diagram == null || diagram.getOwnerId() != ownerId) {
            setLastError("User " + ownerId + " is not the owner of diagram " + diagramId);
            return false;
        }

        int effectiveProjectId;
        if (projectId != null) {
            effectiveProjectId = projectId;
        } else {
            effectiveProjectId = createProjectForDiagramIfNeeded(diagramId, ownerId, diagram.getDiagramName() + " Project");
            if (effectiveProjectId == -1) {
                return false;
            }
        }

        boolean added = addMemberToProject(effectiveProjectId, recipientUserId, role);

        if (added) {
            logDiagramHistory(diagramId, ownerId, "Shared with user " + recipientUserId + " as " + role);
            setLastError(null);
            logger.info("Shared notebook {} with user {} as {}", diagramId, recipientUserId, role);
            return true;
        }
        return false;
    }

    private int createProjectForDiagramIfNeeded(int diagramId, int ownerId, String projectName) throws SQLException {
        String checkSql = "SELECT project_id FROM UML_Diagrams WHERE diagram_id = ?";
        Integer existingProjectId = null;
        
        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, diagramId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    existingProjectId = rs.getObject("project_id", Integer.class);
                }
            }
        }

        if (existingProjectId != null) {
            return existingProjectId;
        }

        String createProjectSql = "INSERT INTO Projects (project_name, owner_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement projectStmt = conn.prepareStatement(createProjectSql, Statement.RETURN_GENERATED_KEYS)) {
            projectStmt.setString(1, projectName != null ? projectName : "Shared UML Project");
            projectStmt.setInt(2, ownerId);
            projectStmt.executeUpdate();

            try (ResultSet keys = projectStmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int newProjectId = keys.getInt(1);

                    String updateDiagramSql = "UPDATE UML_Diagrams SET project_id = ? WHERE diagram_id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateDiagramSql)) {
                        updateStmt.setInt(1, newProjectId);
                        updateStmt.setInt(2, diagramId);
                        updateStmt.executeUpdate();
                    }

                    addMemberToProject(newProjectId, ownerId, "OWNER");
                    return newProjectId;
                }
            }
        } catch (SQLException e) {
            logger.error("SQL error creating project: {}", e.getMessage());
            throw e;
        }
        return -1;
    }

    private boolean addMemberToProject(int projectId, int userId, String role) {
        String sql = "INSERT INTO Project_Members (project_id, user_id, role) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE role = VALUES(role), joined_at = NOW()";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, projectId);
            stmt.setInt(2, userId);
            stmt.setString(3, role.toUpperCase());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("SQL error adding member: {}", e.getMessage());
            return false;
        }
    }

    private void logDiagramHistory(int diagramId, int modifiedBy, String changeSummary) {
        String sql = "INSERT INTO Diagram_History (diagram_id, modified_by, change_summary) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, diagramId);
            stmt.setInt(2, modifiedBy);
            stmt.setString(3, changeSummary);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to log diagram history (non-fatal): {}", e.getMessage());
        }
    }

    @Deprecated
    public Integer duplicateNotebookForUser(int sourceDiagramId, int recipientUserId) throws SQLException {
        UMLDiagram src = getDiagramById(sourceDiagramId);
        if (src == null) {
            setLastError("Source notebook not found");
            return null;
        }
        
        String insert = "INSERT INTO UML_Diagrams (diagram_name, owner_id, content, last_modified) VALUES (?, ?, ?, NOW())";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, src.getDiagramName());
            stmt.setInt(2, recipientUserId);
            if (src.getContent() != null) stmt.setString(3, src.getContent()); else stmt.setNull(3, Types.LONGVARCHAR);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                setLastError("Duplicate insert affected 0 rows");
                return null;
            }
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    setLastError(null);
                    return keys.getInt(1);
                }
            }
            setLastError("Could not retrieve new diagram id");
            return null;
        } catch (SQLException e) {
            logger.error("SQL error duplicating notebook: {}", e.getMessage());
            throw e;
        }
    }

    private UMLDiagram mapDiagram(ResultSet rs) throws SQLException {
        UMLDiagram diagram = new UMLDiagram();
        diagram.setDiagramId(rs.getInt("diagram_id"));
        diagram.setDiagramName(rs.getString("diagram_name"));

        Integer projectIdObj = rs.getObject("project_id", Integer.class);
        if (projectIdObj != null) {
            diagram.setProjectId(projectIdObj);
        }

        Integer ownerObj = rs.getObject("owner_id", Integer.class);
        if (ownerObj != null) {
            diagram.setOwnerId(ownerObj);
        }

        diagram.setMongoId(rs.getString("mongo_id"));
        diagram.setContent(rs.getString("content"));

        Timestamp ts = rs.getTimestamp("last_modified");
        if (ts != null) {
            diagram.setLastModified(ts.toLocalDateTime());
        }

        return diagram;
    }

    public UMLDiagram getDiagramById(int diagramId) throws SQLException {
        String sql = "SELECT diagram_id, diagram_name, project_id, owner_id, mongo_id, content, last_modified FROM UML_Diagrams WHERE diagram_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, diagramId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapDiagram(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("SQL error fetching diagram: {}", e.getMessage());
            throw e;
        }
        setLastError("Diagram " + diagramId + " not found");
        return null;
    }

    public List<Map<String, Object>> getDiagramHistory(int diagramId) {
        List<Map<String, Object>> history = new ArrayList<>();
        String sql = "SELECT * FROM Diagram_History WHERE diagram_id = ? ORDER BY modified_at DESC";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, diagramId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("history_id", rs.getInt("history_id"));
                    entry.put("modified_by", rs.getInt("modified_by"));
                    entry.put("change_summary", rs.getString("change_summary"));
                    entry.put("modified_at", rs.getTimestamp("modified_at").toLocalDateTime());
                    history.add(entry);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching diagram history: {}", e.getMessage());
        }
        return history;
    }
}