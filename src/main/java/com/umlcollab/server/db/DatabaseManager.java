package com.umlcollab.server.db;

import com.umlcollab.server.models.UMLDiagram;
import com.umlcollab.server.models.User;

import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager {
    private Connection connection;
    private String lastError;

    public void connect() {
        String url = "jdbc:mysql://localhost:3306/uml_editor"; // match your DB name
        String user = "root";
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("DB_PASSWORD env var must be set!");  // Fail fast, prompt user to set it
        }
        //String password="Aniruktha@123";
        try {
            connection = DriverManager.getConnection(url, user, password);
            System.out.println("Database connected successfully.");
        } catch (SQLException e) {
            System.err.println("DEBUG: SQL Error Details: " + e.getMessage());  // Extra detail
            e.printStackTrace();
            System.err.println("Database connection failed!");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public String getLastError() {
        return lastError;
    }

    private void setLastError(String message) {
        this.lastError = message;
        if (message != null) {
            System.err.println(message);
        }
    }

    public boolean saveUser(User user) {
        String query = "INSERT INTO Users (username, email, password) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, user.getPassword());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public User getUserByUsername(String username) {
        String sql = "SELECT * FROM Users WHERE username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
            e.printStackTrace();
        }
        return null;
    }

    public User getUserByEmail(String email) throws SQLException {
        if (email == null || email.isBlank()) {
            return null;
        }

        if (!ensureConnection()) {
            return null;
        }

        String sql = "SELECT * FROM Users WHERE email = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
            e.printStackTrace();
        }

        return null;
    }

    public String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int createNotebook(String diagramName, int ownerId) throws SQLException {
        if (diagramName == null || diagramName.isBlank()) {
            throw new IllegalArgumentException("diagramName cannot be null or blank");
        }

        if (!ensureConnection()) {
            setLastError("Unable to create notebook because database connection is null.");
            return -1;
        }

        // Create personal notebook with project_id = NULL
        String sql = "INSERT INTO UML_Diagrams (diagram_name, owner_id, last_modified) VALUES (?, ?, NOW())";

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, diagramName);
            stmt.setInt(2, ownerId);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                System.err.println("Creating notebook failed, no rows affected.");
                return -1;
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newDiagramId = generatedKeys.getInt(1);
                    // Optional: Log creation
                    logDiagramHistory(newDiagramId, ownerId, "Created new diagram");
                    return newDiagramId;
                }
            }

            return -1;
        } catch (SQLException e) {
            e.printStackTrace();
            setLastError("SQL error creating notebook: " + e.getMessage());
            return -1;
        }
    }

    public List<UMLDiagram> getNotebooksByOwner(int ownerId) throws SQLException {
        List<UMLDiagram> diagrams = new ArrayList<>();

        if (!ensureConnection()) {
            setLastError("Unable to fetch notebooks because database connection is null.");
            return diagrams;
        }

        String sql = "SELECT diagram_id, diagram_name, project_id, owner_id, mongo_id, content, last_modified " +
                "FROM UML_Diagrams WHERE owner_id = ? ORDER BY last_modified DESC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, ownerId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UMLDiagram diagram = mapDiagram(rs);
                    diagram.setAccessRole("OWNER");
                    diagrams.add(diagram);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            setLastError("SQL error fetching notebooks: " + e.getMessage());
        }

        return diagrams;
    }

    public boolean updateNotebookContent(int diagramId, byte[] content) throws SQLException {
        if (!ensureConnection()) {
            setLastError("Unable to save notebook because database connection is null.");
            return false;
        }

        String sql = "UPDATE UML_Diagrams SET content = ?, last_modified = NOW() WHERE diagram_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (content != null) {
                stmt.setBytes(1, content);
            } else {
                stmt.setNull(1, Types.BLOB);
            }
            stmt.setInt(2, diagramId);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                // Optional: Log update (pass userId from caller)
                // logDiagramHistory(diagramId, currentUserId, "Updated content");
            }
            return updated > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            setLastError("SQL error updating notebook: " + e.getMessage());
            return false;
        }
    }

    public List<UMLDiagram> getAccessibleNotebooks(int userId) throws SQLException {
        List<UMLDiagram> diagrams = new ArrayList<>();

        if (!ensureConnection()) {
            setLastError("Unable to fetch accessible notebooks: no DB connection");
            return diagrams;
        }

        // SQL: Owned diagrams + those in projects where user is member
        // Use CASE for role: OWNER if direct owner, else member's role
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

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int paramIndex = 1;
            stmt.setInt(paramIndex++, userId); // For owner check
            stmt.setInt(paramIndex++, userId); // For pm.user_id in JOIN
            stmt.setInt(paramIndex++, userId); // For owned WHERE
            stmt.setInt(paramIndex, userId);   // For shared WHERE

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UMLDiagram diagram = mapDiagram(rs);
                    diagram.setAccessRole(rs.getString("access_role"));
                    diagrams.add(diagram);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            setLastError("SQL error fetching accessible notebooks: " + e.getMessage());
        }

        return diagrams;
    }

    public boolean shareNotebookWithUser(int diagramId, int ownerId, int recipientUserId, Integer projectId, String role) throws SQLException {
        if (role == null || (!role.equalsIgnoreCase("EDITOR") && !role.equalsIgnoreCase("VIEWER"))) {
            setLastError("Role must be EDITOR or VIEWER");
            return false;
        }

        if (!ensureConnection()) {
            setLastError("Unable to share notebook because database connection is null.");
            return false;
        }

        // Validate ownership
        UMLDiagram diagram = getDiagramById(diagramId);
        if (diagram == null || diagram.getOwnerId() != ownerId) {
            setLastError("User " + ownerId + " is not the owner of diagram " + diagramId);
            return false;
        }

        // Ensure diagram has a project (create if needed)
        int effectiveProjectId;
        if (projectId != null) {
            effectiveProjectId = projectId;
        } else {
            effectiveProjectId = createProjectForDiagramIfNeeded(diagramId, ownerId, diagram.getDiagramName() + " Project");
            if (effectiveProjectId == -1) {
                return false;
            }
        }

        // Add recipient as member to project
        boolean added = addMemberToProject(effectiveProjectId, recipientUserId, role);

        if (added) {
            // Log sharing
            logDiagramHistory(diagramId, ownerId, "Shared with user " + recipientUserId + " as " + role);
            setLastError(null);
            return true;
        }

        return false;
    }

    private int createProjectForDiagramIfNeeded(int diagramId, int ownerId, String projectName) throws SQLException {
        if (!ensureConnection()) {
            setLastError("Unable to create project: no DB connection");
            return -1;
        }

        // Check if diagram already has project
        String checkSql = "SELECT project_id FROM UML_Diagrams WHERE diagram_id = ?";
        Integer existingProjectId = null;
        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setInt(1, diagramId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    existingProjectId = rs.getObject("project_id", Integer.class);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            setLastError("SQL error checking project: " + e.getMessage());
            return -1;
        }

        if (existingProjectId != null) {
            return existingProjectId; // Already has one
        }

        // Create new project
        String createProjectSql = "INSERT INTO Projects (project_name, owner_id) VALUES (?, ?)";
        try (PreparedStatement projectStmt = connection.prepareStatement(createProjectSql, Statement.RETURN_GENERATED_KEYS)) {
            projectStmt.setString(1, projectName != null ? projectName : "Shared UML Project");
            projectStmt.setInt(2, ownerId);
            projectStmt.executeUpdate();

            try (ResultSet keys = projectStmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int newProjectId = keys.getInt(1);

                    // Update diagram to link to this project
                    String updateDiagramSql = "UPDATE UML_Diagrams SET project_id = ? WHERE diagram_id = ?";
                    try (PreparedStatement updateStmt = connection.prepareStatement(updateDiagramSql)) {
                        updateStmt.setInt(1, newProjectId);
                        updateStmt.setInt(2, diagramId);
                        updateStmt.executeUpdate();
                    }

                    // Add owner as member (OWNER role)
                    addMemberToProject(newProjectId, ownerId, "OWNER");

                    setLastError(null);
                    return newProjectId;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            setLastError("SQL error creating project: " + e.getMessage());
            return -1;
        }
        return -1;
    }

    private boolean addMemberToProject(int projectId, int userId, String role) {
        String sql = "INSERT INTO Project_Members (project_id, user_id, role) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE role = VALUES(role), joined_at = NOW()";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, projectId);
            stmt.setInt(2, userId);
            stmt.setString(3, role.toUpperCase());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            setLastError("SQL error adding member: " + e.getMessage());
            return false;
        }
    }

    private void logDiagramHistory(int diagramId, int modifiedBy, String changeSummary) {
        String sql = "INSERT INTO Diagram_History (diagram_id, modified_by, change_summary) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, diagramId);
            stmt.setInt(2, modifiedBy);
            stmt.setString(3, changeSummary);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace(); // Non-fatal
        }
    }

    // Deprecated: Use shareNotebookWithUser instead for non-duplicating shares
    @Deprecated
    public Integer duplicateNotebookForUser(int sourceDiagramId, int recipientUserId) throws SQLException {
        // Legacy duplication logic (creates new row - avoid for collab)
        UMLDiagram src = getDiagramById(sourceDiagramId);
        if (src == null) {
            if (lastError == null) setLastError("Source notebook not found");
            return null;
        }
        if (!ensureConnection()) {
            setLastError("Unable to duplicate notebook: no DB connection");
            return null;
        }
        String insert = "INSERT INTO UML_Diagrams (diagram_name, owner_id, content, last_modified) VALUES (?, ?, ?, NOW())";
        try (PreparedStatement stmt = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, src.getDiagramName());
            stmt.setInt(2, recipientUserId);
            if (src.getContent() != null) stmt.setBytes(3, src.getContent()); else stmt.setNull(3, Types.BLOB);
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
            e.printStackTrace();
            setLastError("SQL error duplicating notebook: " + e.getMessage());
            return null;
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
        diagram.setContent(rs.getBytes("content"));

        Timestamp ts = rs.getTimestamp("last_modified");
        if (ts != null) {
            diagram.setLastModified(ts.toLocalDateTime());
        }

        return diagram;
    }

    public UMLDiagram getDiagramById(int diagramId) throws SQLException {
        if (!ensureConnection()) {
            setLastError("Unable to fetch diagram: no DB connection");
            return null;
        }
        String sql = "SELECT diagram_id, diagram_name, project_id, owner_id, mongo_id, content, last_modified FROM UML_Diagrams WHERE diagram_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, diagramId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapDiagram(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            setLastError("SQL error fetching diagram: " + e.getMessage());
        }
        setLastError("Diagram " + diagramId + " not found");
        return null;
    }

    private boolean ensureConnection() throws SQLException {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection != null && !connection.isClosed();
    }

    // Optional: Get history for a diagram
    public List<Map<String, Object>> getDiagramHistory(int diagramId) {
        List<Map<String, Object>> history = new ArrayList<>();
        String sql = "SELECT * FROM Diagram_History WHERE diagram_id = ? ORDER BY modified_at DESC";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
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
            e.printStackTrace();
        }
        return history;
    }
}