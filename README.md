# UML Editor

A collaborative UML diagram editor built with JavaFX and Java 17. The application enables real-time multi-user editing through a client-server architecture.

**Core Features:**
- User authentication with secure password hashing (SHA-256)
- Create, save, and manage multiple UML diagrams (notebooks)
- Rich drawing tools: shapes (rectangles, ovals, hexagons, etc.), text labels, and arrow connectors
- Real-time collaboration via WebSocket server (port 8887) for synchronized editing
- Undo/redo functionality with command pattern
- Export diagrams as PNG images
- Sharing system with role-based access (OWNER, EDITOR, VIEWER)
- Project-based organization for team collaboration

**Architecture:**
- The client (JavaFX GUI) connects to a MySQL database for persistent storage and a WebSocket server for real-time synchronization.
- The application uses Maven for dependency management and supports both standalone and collaborative modes.
