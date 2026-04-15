package com.umlcollab.client.views;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.umlcollab.client.ApiClient;
import com.umlcollab.server.db.DatabaseManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class NotebooksPage extends Application {

    private Stage primaryStage;
    private Pane canvas;
    private Pane gridPane;
    private StackPane centerArea;
    private double canvasZoom = 1.0;
    private Label zoomLabel;
    private double mouseAnchorX, mouseAnchorY;

    private Node selectedNode = null;
    private final Glow selectionGlow = new Glow(0.8);

    private String notebookName;
    private Integer notebookId;
    private DatabaseManager dbManager;
    private ApiClient apiClient;
    private String initialContent;
    private boolean dirty = false;
    private boolean loadingState = false;
    private HBox saveNotificationBar;
    private Label saveNotificationLabel;
    private String accessRole = "OWNER";
    private boolean readOnly = false;
    private MenuItem newFileMenuItem;
    private MenuItem saveNotebookMenuItem;
    private MenuItem saveAsPngMenuItem;
    private VBox leftSidebarRef;
    private TabPane rightSidebarRef;

    // WebSocket for collaboration
    private WebSocketClient wsClient;
    private final Gson gson = new Gson();
    private int userId;

    private static String getWebSocketServerAddress() {
        String serverAddr = System.getenv("UML_SERVER_ADDRESS");
        if (serverAddr == null || serverAddr.trim().isEmpty()) {
            serverAddr = "localhost";
        }
        String wsAddr = serverAddr + ":8887";
        if (!wsAddr.startsWith("ws://") && !wsAddr.startsWith("wss://")) {
            wsAddr = "ws://" + wsAddr;
        }
        return wsAddr;
    }

    // --- UNDO/REDO ---
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private final BooleanProperty undoEmpty = new SimpleBooleanProperty(true);
    private final BooleanProperty redoEmpty = new SimpleBooleanProperty(true);

    private enum ShapeType {
        RECTANGLE, SQUARE, OVAL, HEXAGON, PARALLELOGRAM, TRIANGLE, DOCUMENT, STICKMAN, LINE, TEXT
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F3F3F3;");

        root.setTop(createTopUI());
        leftSidebarRef = createLeftSidebar();
        root.setLeft(leftSidebarRef);
        rightSidebarRef = createRightSidebar();
        root.setRight(rightSidebarRef);

        canvas = new Pane();
        canvas.setPrefSize(816, 1056);
        canvas.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        gridPane = createGridPane();
        canvas.getChildren().add(gridPane);

        centerArea = new StackPane(canvas);
        centerArea.setPadding(new Insets(20));

        togglePageView(true);
        setupCanvasDragAndDrop();

        ScrollPane scrollPane = new ScrollPane(centerArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        scrollPane.setOnScroll(event -> {
            if (event.isControlDown()) {
                double zoomFactor = event.getDeltaY() > 0 ? 1.1 : 0.9;
                setZoom(canvasZoom * zoomFactor);
                event.consume();
            }
        });

        root.setCenter(scrollPane);

        Scene scene = new Scene(root, 1600, 900);

        scene.setOnKeyPressed(event -> {
            if (readOnly) {
                return;
            }
            if (new KeyCodeCombination(KeyCode.DELETE).match(event) || new KeyCodeCombination(KeyCode.BACK_SPACE).match(event)) {
                deleteSelectedNode();
            } else if (new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN).match(event)) {
                undo();
            } else if (new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN).match(event)) {
                redo();
            }
        });

        StringBuilder titleBuilder = new StringBuilder("Diagram Editor");
        if (notebookName != null && !notebookName.isBlank()) {
            titleBuilder.append(" - ").append(notebookName);
            if (notebookId != null) {
                titleBuilder.append(" (ID: ").append(notebookId).append(')');
            }
        } else if (notebookId != null) {
            titleBuilder.append(" - Notebook #").append(notebookId);
        }
        stage.setTitle(titleBuilder.toString());
        stage.setScene(scene);
        stage.setMaximized(true);

        loadNotebookContent();
        updateSaveNotification();
        applyAccessRestrictions();

        // Connect WebSocket for collaboration if editable
        if (!readOnly && notebookId != null) {
            connectWebSocket();
        }

        stage.setOnCloseRequest(event -> {
            if (dirty) {
                saveNotebookContent();
            }
            if (wsClient != null) {
                wsClient.close();
            }
        });

        stage.show();
    }

    // --- WebSocket Integration ---
    private void connectWebSocket() {
        String serverAddress = getWebSocketServerAddress();
        System.out.println(">>> Connecting to WebSocket server: " + serverAddress);
        try {
            wsClient = new WebSocketClient(URI.create(serverAddress)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    Platform.runLater(() -> {
                        System.out.println("WebSocket connected for diagram " + notebookId);
                        // Join room with userId
                        JsonObject joinMsg = new JsonObject();
                        joinMsg.addProperty("type", "join");
                        joinMsg.addProperty("diagramId", notebookId);
                        joinMsg.addProperty("userId", userId);
                        send(gson.toJson(joinMsg));
                    });
                }

                @Override
                public void onMessage(String message) {
                    Platform.runLater(() -> {
                        try {
                            JsonObject json = gson.fromJson(message, JsonObject.class);
                            if (json == null || !json.has("type")) {
                                System.err.println("Invalid message: missing type field");
                                return;
                            }
                            String type = json.get("type").getAsString();
                            if ("initialState".equals(type)) {
                                if (!json.has("content")) {
                                    System.err.println("Missing content in initialState");
                                    return;
                                }
                                String content = json.get("content").getAsString();
                                loadFromJson(content);
                            } else if ("edit".equals(type)) {
                                if (!json.has("delta")) {
                                    System.err.println("Missing delta in edit message");
                                    return;
                                }
                                JsonObject delta = json.getAsJsonObject("delta");
                                applyDelta(delta);
                            } else if ("error".equals(type)) {
                                String errMsg = json.has("message") ? json.get("message").getAsString() : "Unknown server error";
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setContentText("Server Error: " + errMsg);
                                alert.show();
                            }
                        } catch (Exception e) {
                            System.err.println("Error processing WebSocket message: " + e.getMessage());
                        }
                    });
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Platform.runLater(() -> System.out.println("WebSocket closed: " + reason));
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                    System.err.println(">>> WebSocket ERROR: " + ex.getClass().getName() + " - " + ex.getMessage());
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setContentText("Cannot connect to server:\n" + ex.getMessage() + "\n\nMake sure server is running on " + serverAddress);
                        alert.show();
                    });
                }
            };
            wsClient.connect();
        } catch (Exception e) {
            System.err.println("Failed to connect WebSocket: " + e.getMessage());
        }
    }

    private void sendEdit(String action, ShapeState state) {
        if (wsClient == null || !wsClient.isOpen()) return;

        JsonObject delta = new JsonObject();
        delta.addProperty("action", action);
        if (state != null) {
            // Serialize state to JSON for delta
            delta.addProperty("type", state.type);
            delta.addProperty("layoutX", state.layoutX);
            delta.addProperty("layoutY", state.layoutY);
            if (state.text != null) delta.addProperty("text", state.text);
            // Add more fields as needed (e.g., startX for lines)
        }

        JsonObject editMsg = new JsonObject();
        editMsg.addProperty("type", "edit");
        editMsg.addProperty("diagramId", notebookId);
        editMsg.add("delta", delta);
        // Send full content for simplicity (merge on server/client)
        editMsg.addProperty("newContent", new String(captureCurrentStateBytes()));

        wsClient.send(gson.toJson(editMsg));
    }

    private String captureCurrentStateBytes() {
        List<ShapeState> states = captureCurrentState();
        return gson.toJson(states);
    }

    private void loadFromJson(String jsonContent) {
        if (jsonContent == null || jsonContent.isEmpty()) {
            setDirty(false);
            return;
        }
        
        try {
            Type listType = new TypeToken<List<ShapeState>>(){}.getType();
            List<ShapeState> states = gson.fromJson(jsonContent, listType);
            if (states != null) {
                loadingState = true;
                clearCanvas();
                for (ShapeState state : states) {
                    addShapeFromState(state);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            loadingState = false;
            setDirty(false);
        }
    }

    private void applyDelta(JsonObject delta) {
        try {
            if (delta == null || !delta.has("action")) {
                return;
            }
            String action = delta.get("action").getAsString();
            if ("add".equals(action)) {
                ShapeState state = new ShapeState();
                if (delta.has("type")) state.type = delta.get("type").getAsString();
                if (delta.has("layoutX")) state.layoutX = delta.get("layoutX").getAsDouble();
                if (delta.has("layoutY")) state.layoutY = delta.get("layoutY").getAsDouble();
                if (delta.has("text")) state.text = delta.get("text").getAsString();
                addShapeFromState(state);
            }
            setDirty(true);
        } catch (Exception e) {
            System.err.println("Error applying delta: " + e.getMessage());
        }
    }

    // --- Command Pattern for Undo/Redo ---
    private interface Command {
        void execute();
        void undo();
    }

    private void executeCommand(Command command) {
        if (readOnly) {
            return;
        }
        command.execute();
        undoStack.push(command);
        redoStack.clear();
        updateUndoRedoStatus();
        setDirty(true);
        // Send delta on change
        if (notebookId != null) {
            sendEdit("edit", null); // Full state broadcast
        }
    }

    private void undo() {
        if (readOnly) {
            return;
        }
        if (!undoStack.isEmpty()) {
            Command command = undoStack.pop();
            command.undo();
            redoStack.push(command);
            updateUndoRedoStatus();
            setDirty(true);
        }
    }

    private void redo() {
        if (readOnly) {
            return;
        }
        if (!redoStack.isEmpty()) {
            Command command = redoStack.pop();
            command.execute();
            undoStack.push(command);
            updateUndoRedoStatus();
            setDirty(true);
        }
    }

    private void updateUndoRedoStatus() {
        undoEmpty.set(undoStack.isEmpty());
        redoEmpty.set(redoStack.isEmpty());
    }

    // --- UI Creation ---

    private VBox createTopUI() {
        MenuBar menuBar = createMenuBar();
        saveNotificationLabel = new Label();
        saveNotificationBar = new HBox(saveNotificationLabel);
        saveNotificationBar.setAlignment(Pos.CENTER);
        saveNotificationBar.setStyle("-fx-background-color: #FFDDC1; -fx-padding: 5;");
        saveNotificationBar.setCursor(Cursor.HAND);
        saveNotificationBar.setOnMouseClicked(e -> {
            if (!readOnly) {
                saveNotebookContent();
            }
        });
        Button zoomInBtn = new Button("+");
        zoomInBtn.setOnAction(e -> setZoom(canvasZoom * 1.2));
        Button zoomOutBtn = new Button("-");
        zoomOutBtn.setOnAction(e -> setZoom(canvasZoom * 0.8));
        zoomLabel = new Label("100%");
        Button zoomResetBtn = new Button(null, zoomLabel);
        zoomResetBtn.setOnAction(e -> setZoom(1.0));
        zoomResetBtn.setStyle("-fx-background-color: transparent;");
        ToolBar toolBar = new ToolBar(new Separator(), zoomOutBtn, zoomResetBtn, zoomInBtn, new Separator());
        toolBar.setStyle("-fx-background-color: #FFFFFF;");
        toolBar.setPadding(new Insets(5));
        return new VBox(menuBar, saveNotificationBar, toolBar);
    }

    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem newFile = new MenuItem("New");
        newFile.setOnAction(e -> clearCanvas());
        MenuItem saveNotebookItem = new MenuItem("Save Notebook");
        saveNotebookItem.setOnAction(e -> saveNotebookContent());
        MenuItem saveFile = new MenuItem("Save as PNG...");
        saveFile.setOnAction(e -> saveAsPng());
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> primaryStage.close());
        fileMenu.getItems().addAll(newFile, saveNotebookItem, new SeparatorMenuItem(), saveFile, new SeparatorMenuItem(), exit);
        this.newFileMenuItem = newFile;
        this.saveNotebookMenuItem = saveNotebookItem;
        this.saveAsPngMenuItem = saveFile;

        Menu editMenu = new Menu("Edit");
        MenuItem undoItem = new MenuItem("Undo");
        undoItem.setOnAction(e -> undo());
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undoItem.disableProperty().bind(undoEmpty);

        MenuItem redoItem = new MenuItem("Redo");
        redoItem.setOnAction(e -> redo());
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        redoItem.disableProperty().bind(redoEmpty);

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> deleteSelectedNode());
        deleteItem.setAccelerator(new KeyCodeCombination(KeyCode.DELETE));
        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(), deleteItem);

        menuBar.getMenus().addAll(fileMenu, editMenu);
        return menuBar;
    }

    // --- Core Functionality ---

    private void deleteSelectedNode() {
        if (readOnly) {
            return;
        }
        if (selectedNode != null) {
            Node nodeToDelete = selectedNode;
            Command deleteCommand = new Command() {
                @Override
                public void execute() {
                    canvas.getChildren().remove(nodeToDelete);
                    deselectNode();
                }
                @Override
                public void undo() {
                    canvas.getChildren().add(nodeToDelete);
                    selectNode(nodeToDelete);
                }
            };
            executeCommand(deleteCommand);
        }
    }

    private void makeDraggable(final Node node) {
        node.setCursor(readOnly ? Cursor.DEFAULT : Cursor.HAND);

        final double[] initialPos = new double[2];

        node.setOnMousePressed(event -> {
            if (!event.isPrimaryButtonDown()) return;
            selectNode(node);
            if (readOnly) {
                event.consume();
                return;
            }
            mouseAnchorX = event.getSceneX();
            mouseAnchorY = event.getSceneY();

            initialPos[0] = node.getLayoutX();
            initialPos[1] = node.getLayoutY();

            node.setCursor(Cursor.MOVE);
            event.consume();
        });

        node.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;
            if (readOnly) {
                event.consume();
                return;
            }
            double deltaX = event.getSceneX() - mouseAnchorX;
            double deltaY = event.getSceneY() - mouseAnchorY;
            node.relocate(node.getLayoutX() + deltaX / canvasZoom, node.getLayoutY() + deltaY / canvasZoom);
            mouseAnchorX = event.getSceneX();
            mouseAnchorY = event.getSceneY();
            event.consume();
        });

        node.setOnMouseReleased(event -> {
            if (readOnly) {
                node.setCursor(Cursor.DEFAULT);
                event.consume();
                return;
            }
            node.setCursor(Cursor.HAND);

            if (node.getLayoutX() != initialPos[0] || node.getLayoutY() != initialPos[1]) {
                final double finalX = node.getLayoutX();
                final double finalY = node.getLayoutY();

                Command moveCommand = new Command() {
                    @Override
                    public void execute() { node.relocate(finalX, finalY); }
                    @Override
                    public void undo() { node.relocate(initialPos[0], initialPos[1]); }
                };
                executeCommand(moveCommand);
            }
        });
    }

    // --- Shape Creation and Management ---

    private VBox createLeftSidebar() {
        VBox leftSidebar = new VBox(10);
        leftSidebar.setPadding(new Insets(10));
        leftSidebar.setPrefWidth(240);
        leftSidebar.setStyle("-fx-background-color: #FAFAFA; -fx-border-color: #E0E0E0; -fx-border-width: 0 1 0 0;");
        TextField searchField = new TextField();
        searchField.setPromptText("Type / to search");
        Label generalLabel = new Label("General");
        generalLabel.setStyle("-fx-font-weight: bold;");
        TilePane shapesPane = new TilePane(10, 10);
        shapesPane.setPrefColumns(2);
        shapesPane.getChildren().add(createDraggableShape(ShapeType.RECTANGLE, new Rectangle(60, 40, Color.TRANSPARENT) {{ setStroke(Color.BLACK); setStrokeWidth(2); }}));
        shapesPane.getChildren().add(createDraggableShape(ShapeType.SQUARE, new Rectangle(50, 50, Color.TRANSPARENT) {{ setStroke(Color.BLACK); setStrokeWidth(2); }}));
        shapesPane.getChildren().add(createDraggableShape(ShapeType.OVAL, new Ellipse(35, 25) {{ setStroke(Color.BLACK); setFill(Color.TRANSPARENT); setStrokeWidth(2); }}));
        shapesPane.getChildren().add(createDraggableShape(ShapeType.TRIANGLE, createTrianglePolygon(50, 45)));
        shapesPane.getChildren().add(createDraggableShape(ShapeType.HEXAGON, createHexagonPolygon(50, 45)));
        shapesPane.getChildren().add(createDraggableShape(ShapeType.PARALLELOGRAM, createParallelogramPolygon(60, 40)));
        shapesPane.getChildren().add(createDraggableShape(ShapeType.DOCUMENT, createDocumentPath(50, 55)));
        shapesPane.getChildren().add(createDraggableShape(ShapeType.TEXT, new Label("Text")));
        shapesPane.getChildren().add(createDraggableShape(ShapeType.STICKMAN, createStickmanIcon()));
        shapesPane.getChildren().add(createDraggableShape(ShapeType.LINE, new Line(0, 0, 50, 50) {{ setStroke(Color.BLACK); setStrokeWidth(2); }}));
        Button moreShapesBtn = new Button("+ More Shapes");
        moreShapesBtn.setPrefWidth(Double.MAX_VALUE);
        leftSidebar.getChildren().addAll(searchField, generalLabel, new ScrollPane(shapesPane), moreShapesBtn);
        return leftSidebar;
    }

    private void createAndAddShape(ShapeType type, double x, double y) {
        if (readOnly) {
            return;
        }
        Node shapeNode = createShapeNode(type, null);
        if (shapeNode == null) {
            return;
        }

        if (type != ShapeType.LINE) {
            double width = shapeNode.getBoundsInLocal().getWidth();
            double height = shapeNode.getBoundsInLocal().getHeight();
            shapeNode.setLayoutX(x - width / 2);
            shapeNode.setLayoutY(y - height / 2);
        } else {
            shapeNode.setLayoutX(x);
            shapeNode.setLayoutY(y);
        }

        if (type == ShapeType.TEXT && shapeNode instanceof TextArea) {
            registerTextAreaListeners((TextArea) shapeNode);
        }

        shapeNode.setUserData(type.name());

        if (type == ShapeType.LINE && shapeNode instanceof ArrowLine) {
            ((ArrowLine) shapeNode).setHandles(0, 0, 100, 0);
        }

        Node finalShapeNode = shapeNode;
        ShapeState state = captureStateForNode(finalShapeNode); // Capture for delta
        Command createCommand = new Command() {
            @Override public void execute() {
                canvas.getChildren().add(finalShapeNode);
                makeDraggable(finalShapeNode);
                selectNode(finalShapeNode);
            }
            @Override public void undo() {
                canvas.getChildren().remove(finalShapeNode);
                deselectNode();
            }
        };
        executeCommand(createCommand);
        sendEdit("add", state);
    }

    private ShapeState captureStateForNode(Node node) {
        ShapeState state = new ShapeState();
        state.type = (String) node.getUserData();
        state.layoutX = node.getLayoutX();
        state.layoutY = node.getLayoutY();
        if (node instanceof EditableTextShape) {
            state.text = ((EditableTextShape) node).getTextValue();
        } else if (node instanceof TextArea) {
            state.text = ((TextArea) node).getText();
            state.prefWidth = ((TextArea) node).getPrefWidth();
            state.prefHeight = ((TextArea) node).getPrefHeight();
        } else if (node instanceof ArrowLine) {
            ArrowLine line = (ArrowLine) node;
            state.startX = line.getStartHandleX();
            state.startY = line.getStartHandleY();
            state.endX = line.getEndHandleX();
            state.endY = line.getEndHandleY();
        }
        return state;
    }

    private Node createShapeNode(ShapeType type, String textOverride) {
        double standardWidth = 140;
        double standardHeight = 80;
        String text = textOverride != null ? textOverride : defaultTextForType(type);

        switch (type) {
            case RECTANGLE:
                return new EditableTextShape(new Rectangle(standardWidth, standardHeight, Color.WHITE) {{
                    setStroke(Color.BLACK);
                    setStrokeWidth(2);
                }}, text);
            case SQUARE:
                return new EditableTextShape(new Rectangle(100, 100, Color.WHITE) {{
                    setStroke(Color.BLACK);
                    setStrokeWidth(2);
                }}, text);
            case OVAL:
                return new EditableTextShape(new Ellipse(standardWidth / 2, standardHeight / 2) {{
                    setStroke(Color.BLACK);
                    setFill(Color.WHITE);
                    setStrokeWidth(2);
                }}, text);
            case HEXAGON:
                return new EditableTextShape(createHexagonPolygon(standardWidth, standardHeight), text);
            case PARALLELOGRAM:
                return new EditableTextShape(createParallelogramPolygon(standardWidth, standardHeight), text);
            case TRIANGLE:
                return new EditableTextShape(createTrianglePolygon(120, 100), text);
            case DOCUMENT:
                return new EditableTextShape(createDocumentPath(standardWidth, standardHeight + 10), text);
            case TEXT:
                TextArea area = new TextArea(text);
                area.setPrefSize(100, 60);
                area.setEditable(!readOnly);
                area.setCursor(readOnly ? Cursor.DEFAULT : Cursor.TEXT);
                return area;
            case STICKMAN:
                return createFullStickman();
            case LINE:
                return new ArrowLine(0, 0, 100, 0);
        }
        return null;
    }

    private String defaultTextForType(ShapeType type) {
        switch (type) {
            case RECTANGLE: return "Rectangle";
            case SQUARE: return "Square";
            case OVAL: return "Oval";
            case HEXAGON: return "Hexagon";
            case PARALLELOGRAM: return "Parallelogram";
            case TRIANGLE: return "Triangle";
            case DOCUMENT: return "Document";
            case TEXT: return "Text";
            default: return "";
        }
    }

    private void registerTextAreaListeners(TextArea textArea) {
        textArea.setEditable(!readOnly);
        if (readOnly) {
            return;
        }
        textArea.textProperty().addListener((obs, oldVal, newVal) -> setDirty(true));
    }

    private class EditableTextShape extends StackPane {
        private final Text text = new Text();
        public EditableTextShape(Node visualNode, String defaultText) {
            super(visualNode, new Group(new Text()));
            text.setText(defaultText);
            this.setAlignment(Pos.CENTER);
            text.wrappingWidthProperty().bind(this.widthProperty().subtract(25));
            getChildren().set(1, text);
            this.setOnMouseClicked(event -> {
                if (event.getButton().equals(MouseButton.PRIMARY) && event.getClickCount() == 2) {
                    startEditing();
                }
                event.consume();
            });
        }
        private void startEditing() {
            TextField textField = new TextField(text.getText());
            textField.setPrefWidth(this.getWidth() - 20);
            textField.setAlignment(Pos.CENTER);
            getChildren().set(1, textField);
            textField.requestFocus();
            Runnable commitEdit = () -> {
                text.setText(textField.getText());
                getChildren().set(1, text);
                NotebooksPage.this.setDirty(true);
            };
            textField.focusedProperty().addListener((obs, was, isNow) -> { if (!isNow) commitEdit.run(); });
            textField.setOnAction(e -> commitEdit.run());
        }

        public String getTextValue() {
            return text.getText();
        }

        public void setTextValue(String value) {
            text.setText(value != null ? value : "");
        }
    }

    private class ArrowLine extends Group {
        private final Line line = new Line();
        private final Line hitbox = new Line();
        private final Polygon arrowhead = new Polygon();
        private final Circle startHandle = new Circle(5, Color.DODGERBLUE);
        private final Circle endHandle = new Circle(5, Color.DODGERBLUE);

        ArrowLine(double startX, double startY, double endX, double endY) {
            line.setStrokeWidth(2);
            hitbox.setStrokeWidth(10);
            hitbox.setStroke(Color.TRANSPARENT);
            arrowhead.getPoints().addAll(0.0, 0.0, -10.0, 5.0, -10.0, -5.0);
            arrowhead.setFill(Color.BLACK);
            this.setLayoutX(startX);
            this.setLayoutY(startY);
            startHandle.setCenterX(0);
            startHandle.setCenterY(0);
            endHandle.setCenterX(endX - startX);
            endHandle.setCenterY(endY - startY);
            getChildren().addAll(hitbox, line, arrowhead, startHandle, endHandle);
            makeHandleDraggable(startHandle);
            makeHandleDraggable(endHandle);
            line.startXProperty().bind(startHandle.centerXProperty());
            line.startYProperty().bind(startHandle.centerYProperty());
            line.endXProperty().bind(endHandle.centerXProperty());
            line.endYProperty().bind(endHandle.centerYProperty());
            hitbox.startXProperty().bind(startHandle.centerXProperty());
            hitbox.startYProperty().bind(startHandle.centerYProperty());
            hitbox.endXProperty().bind(endHandle.centerXProperty());
            hitbox.endYProperty().bind(endHandle.centerYProperty());
            startHandle.centerXProperty().addListener((obs, ov, nv) -> updateArrow());
            startHandle.centerYProperty().addListener((obs, ov, nv) -> updateArrow());
            endHandle.centerXProperty().addListener((obs, ov, nv) -> updateArrow());
            endHandle.centerYProperty().addListener((obs, ov, nv) -> updateArrow());
            updateArrow();
        }

        private void updateArrow() {
            double sx = line.getStartX(), sy = line.getStartY(), ex = line.getEndX(), ey = line.getEndY();
            arrowhead.setLayoutX(ex);
            arrowhead.setLayoutY(ey);
            arrowhead.setRotate(Math.toDegrees(Math.atan2(ey - sy, ex - sx)));
        }

        private void makeHandleDraggable(Circle handle) {
            handle.setCursor(Cursor.CROSSHAIR);
            final double[] initialPos = new double[2];
            handle.setOnMousePressed(event -> {
                selectNode(ArrowLine.this);
                initialPos[0] = handle.getCenterX();
                initialPos[1] = handle.getCenterY();
                event.consume();
            });
            handle.setOnMouseDragged(event -> {
                Point2D parentCoords = getParent().sceneToLocal(event.getSceneX(), event.getSceneY());
                handle.setCenterX(parentCoords.getX() - getLayoutX());
                handle.setCenterY(parentCoords.getY() - getLayoutY());
                event.consume();
            });
            handle.setOnMouseReleased(event -> {
                if (handle.getCenterX() != initialPos[0] || handle.getCenterY() != initialPos[1]) {
                    final double finalX = handle.getCenterX();
                    final double finalY = handle.getCenterY();
                    Command moveHandleCommand = new Command() {
                        @Override public void execute() { handle.setCenterX(finalX); handle.setCenterY(finalY); }
                        @Override public void undo() { handle.setCenterX(initialPos[0]); handle.setCenterY(initialPos[1]); }
                    };
                    executeCommand(moveHandleCommand);
                }
            });
        }

        public double getStartHandleX() {
            return startHandle.getCenterX();
        }

        public double getStartHandleY() {
            return startHandle.getCenterY();
        }

        public double getEndHandleX() {
            return endHandle.getCenterX();
        }

        public double getEndHandleY() {
            return endHandle.getCenterY();
        }

        public void setHandles(double startX, double startY, double endX, double endY) {
            startHandle.setCenterX(startX);
            startHandle.setCenterY(startY);
            endHandle.setCenterX(endX);
            endHandle.setCenterY(endY);
            updateArrow();
        }
    }

    // --- Shape Drawing Helpers ---
    private Polygon createHexagonPolygon(double width, double height) { double side = width / 4; Polygon hexagon = new Polygon(side, 0, side * 3, 0, width, height / 2, side * 3, height, side, height, 0, height / 2); hexagon.setStroke(Color.BLACK); hexagon.setFill(Color.WHITE); hexagon.setStrokeWidth(2); return hexagon; }
    private Polygon createParallelogramPolygon(double width, double height) { double skew = width * 0.25; Polygon parallelogram = new Polygon(skew, 0, width, 0, width - skew, height, 0, height); parallelogram.setStroke(Color.BLACK); parallelogram.setFill(Color.WHITE); parallelogram.setStrokeWidth(2); return parallelogram; }
    private Polygon createTrianglePolygon(double width, double height) { Polygon triangle = new Polygon(width / 2, 0, width, height, 0, height); triangle.setStroke(Color.BLACK); triangle.setFill(Color.WHITE); triangle.setStrokeWidth(2); return triangle; }
    private Path createDocumentPath(double width, double height) { Path path = new Path(); path.getElements().addAll(new MoveTo(0, 0), new LineTo(width, 0), new LineTo(width, height - 15), new CubicCurveTo(width * 0.75, height - 30, width * 0.25, height, 0, height - 15), new ClosePath()); path.setStroke(Color.BLACK); path.setFill(Color.WHITE); path.setStrokeWidth(2); return path; }
    private Node createStickmanIcon() { Group stickman = new Group(); Circle head = new Circle(5, Color.TRANSPARENT) {{ setStroke(Color.BLACK); setStrokeWidth(2); setCenterX(15); setCenterY(5); }}; Line body = new Line(15, 10, 15, 25) {{ setStrokeWidth(2); }}; Line arms = new Line(10, 15, 20, 15) {{ setStrokeWidth(2); }}; Line leg1 = new Line(15, 25, 10, 35) {{ setStrokeWidth(2); }}; Line leg2 = new Line(15, 25, 20, 35) {{ setStrokeWidth(2); }}; stickman.getChildren().addAll(head, body, arms, leg1, leg2); return new Group(stickman); }
    private Node createFullStickman() { Group stickman = new Group(); Circle head = new Circle(15, Color.WHITE) {{ setStroke(Color.BLACK); setStrokeWidth(2); setCenterX(50); setCenterY(15); }}; Line body = new Line(50, 30, 50, 70) {{ setStrokeWidth(2); }}; Line arms = new Line(30, 45, 70, 45) {{ setStrokeWidth(2); }}; Line leg1 = new Line(50, 70, 30, 100) {{ setStrokeWidth(2); }}; Line leg2 = new Line(50, 70, 70, 100) {{ setStrokeWidth(2); }}; stickman.getChildren().addAll(head, body, arms, leg1, leg2); Bounds bounds = stickman.getBoundsInLocal(); Rectangle hitbox = new Rectangle(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight()); hitbox.setFill(Color.TRANSPARENT); return new Group(hitbox, stickman); }

    // --- Unchanged Helper and Setup Methods ---
    private void clearCanvas() {
        deselectNode();
        canvas.getChildren().removeIf(node -> node != gridPane);
        undoStack.clear();
        redoStack.clear();
        updateUndoRedoStatus();
        setDirty(true);
    }
    private void saveAsPng() { FileChooser fc = new FileChooser(); fc.setTitle("Save Diagram"); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png")); File f = fc.showSaveDialog(primaryStage); if (f != null) { try { if (selectedNode != null) selectedNode.setEffect(null); WritableImage img = canvas.snapshot(new SnapshotParameters(), null); ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", f); if (selectedNode != null) selectedNode.setEffect(selectionGlow); } catch (IOException ex) { new Alert(Alert.AlertType.ERROR, "Error saving: " + ex.getMessage()).show(); } } }
    private void selectNode(Node node) { if (selectedNode == node) return; deselectNode(); selectedNode = node; selectedNode.setEffect(selectionGlow); }
    private void deselectNode() { if (selectedNode != null) { selectedNode.setEffect(null); selectedNode = null; } }
    private void setZoom(double newZoom) { newZoom = Math.max(0.25, Math.min(newZoom, 4.0)); canvasZoom = newZoom; centerArea.setScaleX(canvasZoom); centerArea.setScaleY(canvasZoom); zoomLabel.setText(Math.round(canvasZoom * 100) + "%"); }
    private void togglePageView(boolean enabled) { if (enabled) { centerArea.setStyle("-fx-background-color: #D3D3D3;"); canvas.setStyle("-fx-background-color: white;"); canvas.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.3))); } else { centerArea.setStyle("-fx-background-color: white;"); canvas.setStyle("-fx-background-color: white;"); canvas.setEffect(null); } }
    private TabPane createRightSidebar() { 
        TabPane rightSidebar = new TabPane(); 
        rightSidebar.setPrefWidth(250); 
        
        // Diagram Tab
        Tab diagramTab = new Tab("Diagram"); 
        diagramTab.setClosable(false); 
        CheckBox gridCheckBox = new CheckBox("Grid"); 
        gridCheckBox.setSelected(true); 
        gridCheckBox.setOnAction(e -> gridPane.setVisible(gridCheckBox.isSelected())); 
        CheckBox pageViewCheckBox = new CheckBox("Page View"); 
        pageViewCheckBox.setSelected(true); 
        pageViewCheckBox.setOnAction(e -> togglePageView(pageViewCheckBox.isSelected())); 
        
        VBox diagramControls = new VBox(15); 
        diagramControls.setPadding(new Insets(15)); 
        diagramControls.getChildren().addAll( 
            new Label("View"){{setStyle("-fx-font-weight: bold;");}}, 
            gridCheckBox, 
            pageViewCheckBox, 
            new CheckBox("Connection Arrows"){{setSelected(true);}}, 
            new Separator(), 
            new Label("Paper Size"){{setStyle("-fx-font-weight: bold;");}}, 
            new ComboBox<String>() {{ getItems().addAll("US-Letter (8.5\" x 11\")"); setValue("US-Letter (8.5\" x 11\")"); }}
        ); 
        diagramTab.setContent(diagramControls); 
        
        rightSidebar.getTabs().addAll(diagramTab); 
        return rightSidebar; 
    }
    private Node createDraggableShape(final ShapeType type, Node node) {
        StackPane container = new StackPane(node);
        container.setAlignment(Pos.CENTER);
        container.setStyle("-fx-border-color: #CCCCCC; -fx-border-width: 1; -fx-background-color: white;");
        container.setPrefSize(80, 60);
        container.setCursor(Cursor.HAND);
        container.setOnDragDetected(event -> {
            if (readOnly) {
                event.consume();
                return;
            }
            Dragboard db = container.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            content.putString(type.name());
            db.setContent(content);
            db.setDragView(container.snapshot(new SnapshotParameters(), null));
            event.consume();
        });
        return container;
    }
    private void setupCanvasDragAndDrop() {
        canvas.setOnDragOver(event -> {
            if (readOnly) {
                event.consume();
                return;
            }
            if (event.getGestureSource() != canvas && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        canvas.setOnDragDropped(event -> {
            if (readOnly) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            Dragboard db = event.getDragboard();
            if (db.hasString()) {
                ShapeType type = ShapeType.valueOf(db.getString());
                Point2D localCoords = canvas.sceneToLocal(event.getSceneX(), event.getSceneY());
                createAndAddShape(type, localCoords.getX(), localCoords.getY());
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });

        canvas.setOnMousePressed(event -> {
            if (event.getTarget() == canvas) {
                deselectNode();
            }
        });
    }
    private Pane createGridPane() { Pane grid = new Pane(); double gridSize = 20.0; for (double i = 0; i < canvas.getPrefWidth() + gridSize; i += gridSize) { Line line = new Line(i, 0, i, canvas.getPrefHeight() + gridSize); line.setStroke(Color.web("#EAEAEA")); grid.getChildren().add(line); } for (double i = 0; i < canvas.getPrefHeight() + gridSize; i += gridSize) { Line line = new Line(0, i, canvas.getPrefWidth() + gridSize, i); line.setStroke(Color.web("#EAEAEA")); grid.getChildren().add(line); } grid.setMouseTransparent(true); return grid; }

    private boolean saveNotebookContent() {
        if (readOnly) {
            new Alert(Alert.AlertType.INFORMATION, "This notebook is view-only. Changes cannot be saved.", ButtonType.OK).showAndWait();
            return false;
        }
        if (dbManager == null || notebookId == null) {
            new Alert(Alert.AlertType.WARNING, "Cannot save notebook: missing database connection or notebook id.", ButtonType.OK).showAndWait();
            return false;
        }

        try {
            List<ShapeState> states = captureCurrentState();
            String jsonContent = gson.toJson(states);

            boolean success = dbManager.updateNotebookContent(notebookId, jsonContent);
            if (success) {
                setDirty(false);
                initialContent = jsonContent;
            } else {
                new Alert(Alert.AlertType.ERROR, "Failed to save notebook. Please try again.", ButtonType.OK).showAndWait();
            }
            return success;
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR, "Error saving notebook: " + e.getMessage(), ButtonType.OK).showAndWait();
            return false;
        }
    }

    private List<ShapeState> captureCurrentState() {
        List<ShapeState> states = new ArrayList<>();
        for (Node node : canvas.getChildren()) {
            if (node == gridPane) {
                continue;
            }
            Object data = node.getUserData();
            if (!(data instanceof String)) {
                continue;
            }

            ShapeState state = new ShapeState();
            state.type = (String) data;
            state.layoutX = node.getLayoutX();
            state.layoutY = node.getLayoutY();

            if (node instanceof EditableTextShape) {
                state.text = ((EditableTextShape) node).getTextValue();
            }

            if (node instanceof TextArea) {
                TextArea area = (TextArea) node;
                state.text = area.getText();
                state.prefWidth = area.getPrefWidth();
                state.prefHeight = area.getPrefHeight();
            }

            if (node instanceof ArrowLine) {
                ArrowLine line = (ArrowLine) node;
                state.startX = line.getStartHandleX();
                state.startY = line.getStartHandleY();
                state.endX = line.getEndHandleX();
                state.endY = line.getEndHandleY();
            }

            states.add(state);
        }
        return states;
    }

    private void loadNotebookContent() {
        if (initialContent == null || initialContent.isEmpty()) {
            setDirty(false);
            return;
        }

        try {
            Type listType = new TypeToken<List<ShapeState>>(){}.getType();
            List<ShapeState> states = gson.fromJson(initialContent, listType);
            if (states != null) {
                loadingState = true;
                for (ShapeState state : states) {
                    addShapeFromState(state);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Failed to load notebook content. Starting with a blank canvas.", ButtonType.OK).showAndWait();
        } finally {
            loadingState = false;
            setDirty(false);
            initialContent = null;
        }
    }

    private void addShapeFromState(ShapeState state) {
        if (state == null || state.type == null) {
            return;
        }

        ShapeType type;
        try {
            type = ShapeType.valueOf(state.type);
        } catch (IllegalArgumentException ex) {
            return;
        }

        Node node = createShapeNode(type, state.text);
        if (node == null) {
            return;
        }

        if (type == ShapeType.LINE && node instanceof ArrowLine) {
            ArrowLine line = (ArrowLine) node;
            line.setHandles(state.startX, state.startY, state.endX, state.endY);
        }

        if (type == ShapeType.TEXT && node instanceof TextArea) {
            TextArea area = (TextArea) node;
            if (state.prefWidth > 0) {
                area.setPrefWidth(state.prefWidth);
            }
            if (state.prefHeight > 0) {
                area.setPrefHeight(state.prefHeight);
            }
            registerTextAreaListeners(area);
        }

        node.setLayoutX(state.layoutX);
        node.setLayoutY(state.layoutY);
        node.setUserData(type.name());

        canvas.getChildren().add(node);
        makeDraggable(node);
    }

    private void setDirty(boolean dirtyFlag) {
        if ((loadingState || readOnly) && dirtyFlag) {
            return;
        }
        this.dirty = dirtyFlag;
        updateSaveNotification();
    }

    private void updateSaveNotification() {
        if (saveNotificationLabel == null || saveNotificationBar == null) {
            return;
        }
        if (readOnly) {
            saveNotificationLabel.setText("View-only mode. Editing disabled.");
            saveNotificationBar.setStyle("-fx-background-color: #ECEFF1; -fx-padding: 5;");
            saveNotificationBar.setCursor(Cursor.DEFAULT);
            saveNotificationBar.setDisable(true);
            return;
        }
        if (dirty) {
            saveNotificationLabel.setText("Unsaved changes. Click here to save.");
            saveNotificationBar.setStyle("-fx-background-color: #FFDDC1; -fx-padding: 5;");
            saveNotificationBar.setCursor(Cursor.HAND);
            saveNotificationBar.setDisable(false);
        } else {
            saveNotificationLabel.setText("All changes saved.");
            saveNotificationBar.setStyle("-fx-background-color: #C8E6C9; -fx-padding: 5;");
            saveNotificationBar.setCursor(Cursor.HAND);
            saveNotificationBar.setDisable(false);
        }
    }

    private void applyAccessRestrictions() {
        readOnly = "VIEWER".equalsIgnoreCase(accessRole);

        if (leftSidebarRef != null) {
            leftSidebarRef.setDisable(readOnly);
            leftSidebarRef.setOpacity(readOnly ? 0.6 : 1.0);
        }

        if (rightSidebarRef != null) {
            rightSidebarRef.setDisable(readOnly);
            rightSidebarRef.setOpacity(readOnly ? 0.8 : 1.0);
        }

        if (newFileMenuItem != null) {
            newFileMenuItem.setDisable(readOnly);
        }

        if (saveNotebookMenuItem != null) {
            saveNotebookMenuItem.setDisable(readOnly);
        }

        if (readOnly) {
            undoStack.clear();
            redoStack.clear();
            updateUndoRedoStatus();
        }

        updateSaveNotification();
    }

    private static class ShapeState implements Serializable {
        private static final long serialVersionUID = 1L;
        String type;
        double layoutX;
        double layoutY;
        String text;
        double prefWidth;
        double prefHeight;
        double startX;
        double startY;
        double endX;
        double endY;
    }

    public void setDatabaseManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void setAccessRole(String role) {
        this.accessRole = (role == null || role.isBlank()) ? "OWNER" : role.toUpperCase();
        this.readOnly = "VIEWER".equalsIgnoreCase(this.accessRole);
        if (canvas != null) {
            applyAccessRestrictions();
        } else {
            updateSaveNotification();
        }
    }

    public void setNotebookName(String notebookName) {
        this.notebookName = notebookName;
    }

    public void setNotebookId(Integer notebookId) {
        this.notebookId = notebookId;
    }

    public void setInitialContent(String initialContent) {
        this.initialContent = initialContent != null ? initialContent : null;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public static void main(String[] args) {
        launch(args);
    }
}