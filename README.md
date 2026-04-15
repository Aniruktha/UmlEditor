# Collaborative UML Editor

A real-time collaborative UML diagram editor built with JavaFX and Java 17. The application enables multiple users to create, edit, and share UML diagrams simultaneously through a client-server architecture.

![Java](https://img.shields.io/badge/Java-17-blue)
![JavaFX](https://img.shields.io/badge/JavaFX-23.0.2-blue)
![Maven](https://img.shields.io/badge/Maven-3.9+-blue)

## Overview

This project is a full-featured desktop UML diagram editor with real-time collaboration capabilities. It follows a client-server architecture where the server manages WebSocket connections for real-time synchronization while the client provides a rich JavaFX-based graphical interface for diagram creation.

## Features

### Drawing Tools
- **Shape Library**: Rectangle, Square, Oval, Hexagon, Parallelogram, Triangle, Document
- **Text Labels**: Editable text shapes with double-click editing
- **Connectors**: Arrow lines with draggable start/end handles
- **Stickman Figure**: Actor representation for use case diagrams

### Collaboration
- **Real-time Sync**: Multiple users can edit the same diagram simultaneously via WebSocket
- **Room-based Editing**: Users join diagram-specific rooms for synchronized editing
- **Live Updates**: Changes are broadcast to all connected clients instantly

### User Management
- **Secure Authentication**: BCrypt password hashing with SHA-256 backward compatibility
- **User Registration**: Create new accounts with username, email, and password
- **Login System**: Secure login with credential validation

### Diagram Management
- **Create Notebooks**: Create new UML diagrams with custom names
- **Save/Load**: Persist diagrams to MySQL database with manual save
- **Export**: Export diagrams as PNG images

### Collaboration & Sharing
- **Role-Based Access Control**: OWNER, EDITOR, VIEWER roles
- **Project-Based Sharing**: Share diagrams through projects with team members
- **Access Management**: Owners can grant EDITOR or VIEWER access to others

### Editor Features
- **Undo/Redo**: Full undo/redo support using Command Pattern
- **Zoom Controls**: Zoom in/out (25% - 400%) with Ctrl+scroll
- **Grid Toggle**: Show/hide alignment grid
- **Page View**: Toggle between clean canvas and page-style view
- **Drag & Drop**: Drag shapes from sidebar onto canvas
- **Delete**: Delete selected shapes with Delete key

## Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Java | 17 |
| UI Framework | JavaFX | 23.0.2 |
| Build Tool | Maven | 3.9+ |
| Database | MySQL | 8.0+ |
| WebSocket | Java-WebSocket | 1.5.6 |
| Connection Pool | HikariCP | 5.1.0 |
| Password Hashing | jBCrypt | 0.4 |
| JSON Processing | Gson | 2.10.1 |
| Logging | SLF4J | 2.0.9 |

### Why This Stack?

- **Java 17**: Long-term support, excellent performance, mature ecosystem
- **JavaFX**: Native Java desktop UI framework with rich component library
- **MySQL**: Reliable relational database for structured data (users, diagrams, projects)
- **WebSocket**: Bi-directional, low-latency communication for real-time sync
- **HikariCP**: High-performance JDBC connection pooling (10 max connections)
- **BCrypt**: Industry-standard password hashing with automatic salt generation (12 rounds)
- **SLF4J**: Standardized logging API with simple console implementation

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (JavaFX)                          │
│  ┌──────────┐  ┌──────────────┐  ┌────────────────────────┐   │
│  │LoginPage │  │MyNotebooks   │  │   NotebooksPage        │   │
│  │          │  │   Page       │  │ (Canvas Editor)        │   │
│  └────┬─────┘  └──────┬───────┘  └───────────┬────────────┘   │
│       │              │                      │                  │
│       └──────────────┴──────────────────────┴─────────────────┤
│                          │                                      │
│                   DatabaseManager (MySQL + HikariCP)            │
└──────────────────────────┬──────────────────────────────────────┘
                           │ JDBC + HikariCP
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      SERVER (Standalone)                        │
│  ┌──────────────────┐  ┌──────────────────────────────────┐    │
│  │  ServerMain     │  │  UMLWebSocketServer (Port 8887) │    │
│  │  - DB Init      │  │  - Real-time collaboration       │    │
│  │  - WS Start     │  │  - Room management              │    │
│  └────────┬─────────┘  └────────────┬─────────────────────┘    │
│           │                       │                            │
│           ▼                       ▼                            │
│  ┌─────────────────────────────────────────────────────────────┐
│  │              DatabaseManager (MySQL + HikariCP)               │
│  │  - User management (Auth, BCrypt hashing)                     │
│  │  - Notebook CRUD                                               │
│  │  - Project-based sharing                                       │
│  │  - Role-based access (OWNER/EDITOR/VIEWER)                   │
│  └─────────────────────────────────────────────────────────────┘
└─────────────────────────────────────────────────────────────────┘
```

## Project Structure

```
UmlEditor/
├── pom.xml                           # Maven configuration
├── README.md                         # This file
├── setup-env.ps1                     # Environment setup script
└── src/
    └── main/
        ├── java/com/umlcollab/
        │   ├── client/
        │   │   ├── ClientApp.java            # Main client application
        │   │   └── views/
        │   │       ├── LoginPage.java        # User login UI
        │   │       ├── SigninPage.java       # User registration UI
        │   │       ├── MyNotebooksPage.java  # Notebook dashboard
        │   │       └── NotebooksPage.java    # Canvas editor (main UI)
        │   ├── server/
        │   │   ├── ServerMain.java           # Server entry point
        │   │   ├── db/
        │   │   │   └── DatabaseManager.java # DB operations + HikariCP
        │   │   ├── services/
        │   │   │   └── AuthService.java     # Authentication logic
        │   │   ├── websocket/
        │   │   │   └── UMLWebSocketServer.java # Real-time sync server
        │   │   └── models/
        │   │       ├── User.java             # User data model
        │   │       └── UMLDiagram.java      # Diagram data model
        └── resources/
            └── styles/
                └── style.css                 # UI stylesheet
```

## Prerequisites

1. **Java Development Kit (JDK) 17** or higher
2. **Apache Maven 3.9+**
3. **MySQL 8.0+** running locally
4. **Operating System**: Windows, macOS, or Linux

## Database Setup

Create the MySQL database and tables:

```sql
CREATE DATABASE uml_editor;
USE uml_editor;

CREATE TABLE Users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE UML_Diagrams (
    diagram_id INT AUTO_INCREMENT PRIMARY KEY,
    diagram_name VARCHAR(100) NOT NULL,
    project_id INT,
    owner_id INT,
    mongo_id VARCHAR(50),
    content MEDIUMBLOB,
    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE Projects (
    project_id INT AUTO_INCREMENT PRIMARY KEY,
    project_name VARCHAR(100) NOT NULL,
    owner_id INT
);

CREATE TABLE Project_Members (
    project_id INT,
    user_id INT,
    role VARCHAR(20),
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_id)
);

CREATE TABLE Diagram_History (
    history_id INT AUTO_INCREMENT PRIMARY KEY,
    diagram_id INT,
    modified_by INT,
    change_summary TEXT,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Configuration

Set the database password environment variable:

**Windows (PowerShell):**
```powershell
$env:DB_PASSWORD = "YourPassword"
```

**Windows (CMD):**
```cmd
set DB_PASSWORD=YourPassword
```

**Linux/macOS:**
```bash
export DB_PASSWORD="YourPassword"
```

Or edit `setup-env.ps1` to set your password:
```powershell
$env:DB_PASSWORD = "YourPassword"
```

## Running the Application

### Step 1: Start the Server

Open a terminal and run:

```powershell
cd UmlEditor
mvn compile exec:java -Dexec.executionId=run-server
```

Expected output:
```
INFO - Starting UML Editor Server...
INFO - Database connected successfully with connection pooling
INFO - WebSocket server started on port 8887
INFO - API server started on port 8888
INFO - Server started successfully
```

### Step 2: Run the Client

Open a **new terminal** and run:

```powershell
cd UmlEditor
mvn javafx:run
```

The login page will appear. You can:
- **Login** with existing credentials
- **Sign Up** to create a new account

## Usage Guide

### Creating a Diagram
1. Login to the application
2. Click "Create New Notebook"
3. Enter a name for your diagram
4. The canvas editor will open

### Drawing on Canvas
1. **Drag shapes** from the left sidebar onto the canvas
2. **Double-click** on shapes to edit text
3. **Drag** shapes to reposition them
4. **Draw arrows** by dragging the handles on arrow lines

### Saving & Exporting
- Click **"Save Notebook"** or click the notification bar to save
- Use **File > Save as PNG** to export your diagram as an image

### Sharing Diagrams
1. Click **"Share Notebook"** on the dashboard
2. Select the notebook to share
3. Enter the recipient's email
4. Choose role (EDITOR or VIEWER)

### Real-time Collaboration
1. Open the same notebook in multiple client windows
2. Changes made in one window appear instantly in others
3. The server broadcasts all edits to connected clients

### Keyboard Shortcuts
- **Ctrl+Z**: Undo
- **Ctrl+Y**: Redo
- **Delete/Backspace**: Delete selected shape
- **Ctrl + Scroll**: Zoom in/out

## Security Features

- **Password Hashing**: BCrypt with automatic salt (12 rounds)
- **Backward Compatibility**: SHA-256 to BCrypt auto-migration on login
- **Connection Pooling**: HikariCP prevents connection leaks (max 10 connections)
- **Prepared Statements**: SQL injection prevention
- **SLF4J Logging**: Audit trail for debugging

## Key Design Patterns

1. **Command Pattern**: Undo/redo functionality with Command interface
2. **Connection Pooling**: HikariCP for efficient database connections
3. **WebSocket Room Management**: ConcurrentHashMap for diagram-specific rooms
4. **Result Wrappers**: AuthService.RegistrationResult & LoginResult for type-safe results

## Known Limitations

- Canvas scaling has a maximum limit of 400%
- WebSocket authentication is disabled for demo purposes (any user can join/edit)

## Future Enhancements

- JWT-based WebSocket authentication
- Incremental save (delta synchronization)
- Version history with rollback
- Additional diagram types (class, sequence, activity)
- Auto-save intervals
- User avatars and presence indicators

## License

This project is for educational purposes.

## Acknowledgments

Built with:
- [JavaFX](https://openjdk.org/projects/openjfx/)
- [HikariCP](https://github.com/brettwooldridge/HikariCP)
- [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket)
- [jBCrypt](http://www.mindrot.org/projects/jBCrypt/)

---
