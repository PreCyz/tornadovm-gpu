package pawg.body;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import pawg.nbody.TornadoDeviceChoice;
import pawg.nbody.TornadoDeviceSelector;
import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.util.*;

public class BodySimulator extends Application {

    private static final int MAX_BODIES = 256;
    private static final int TRAIL_CAPACITY = 260;
    private static final int SIDEBAR_WIDTH = 520;
    private static final float G = 100.0f;
    private static final float DT = 0.015f;
    private static final float SOFTENING = 25.0f;
    private static final double INITIAL_VIEW_SCALE = 45.0;
    private static final double MIN_VIEW_SCALE = 16.0;
    private static final double MAX_VIEW_SCALE = 180.0;
    private static final double GRID_STEP = 0.5;
    private static final double SPACE_BEND_SCALE = 0.025;
    private static final double SPACE_BEND_LIMIT = 70.0;
    private static final int PHOTON_MIN_STEPS = 900;
    private static final int PHOTON_MAX_STEPS = 60_000;
    private static final double PHOTON_STEP_DISTANCE = 0.08;
    private static final double PHOTON_MIN_STEP_DISTANCE = 0.012;
    private static final double PHOTON_LIGHT_SPEED = 160.0;
    private static final double BLACK_HOLE_MASS_THRESHOLD = 2_000.0;
    private static final double BLACK_HOLE_CAPTURE_RADIUS_MULTIPLIER = 1.0;
    private static final double PHOTON_IMPACT_PARAMETER = 2.4;
    private static final double PHOTON_EXIT_MARGIN_PIXELS = 120.0;
    private static final int FULL_TRACK_RENDER_POINT_LIMIT = 2_000;
    private static final String GREEN_BUTTON_STYLE = "-fx-background-color: #1d2b24; -fx-text-fill: #00ff88; -fx-border-color: #00aa66; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String RED_BUTTON_STYLE = "-fx-background-color: #222; -fx-text-fill: #ff4444; -fx-border-color: #ff4444; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String BLUE_BUTTON_STYLE = "-fx-background-color: #1b2533; -fx-text-fill: #9ecfff; -fx-border-color: #497aa5; -fx-font-weight: bold; -fx-cursor: hand;";

    private final FloatArray posX = new FloatArray(MAX_BODIES);
    private final FloatArray posY = new FloatArray(MAX_BODIES);
    private final FloatArray posZ = new FloatArray(MAX_BODIES);
    private final FloatArray velX = new FloatArray(MAX_BODIES);
    private final FloatArray velY = new FloatArray(MAX_BODIES);
    private final FloatArray velZ = new FloatArray(MAX_BODIES);
    private final FloatArray accX = new FloatArray(MAX_BODIES);
    private final FloatArray accY = new FloatArray(MAX_BODIES);
    private final FloatArray accZ = new FloatArray(MAX_BODIES);
    private final FloatArray nextAccX = new FloatArray(MAX_BODIES);
    private final FloatArray nextAccY = new FloatArray(MAX_BODIES);
    private final FloatArray nextAccZ = new FloatArray(MAX_BODIES);
    private final FloatArray mass = new FloatArray(MAX_BODIES);
    private final IntArray active = new IntArray(MAX_BODIES);
    private final FloatArray params = new FloatArray(3);
    private final IntArray state = new IntArray(1);

    private final float[] initialPosX = new float[MAX_BODIES];
    private final float[] initialPosY = new float[MAX_BODIES];
    private final float[] initialPosZ = new float[MAX_BODIES];
    private final float[] initialVelX = new float[MAX_BODIES];
    private final float[] initialVelY = new float[MAX_BODIES];
    private final float[] initialVelZ = new float[MAX_BODIES];
    private final float[] initialMass = new float[MAX_BODIES];
    private final String[] names = new String[MAX_BODIES];
    private final Color[] colors = new Color[MAX_BODIES];
    private final TextField[] positionXFields = new TextField[MAX_BODIES];
    private final TextField[] positionYFields = new TextField[MAX_BODIES];
    private final TextField[] positionZFields = new TextField[MAX_BODIES];
    private final TextField[] velocityXFields = new TextField[MAX_BODIES];
    private final TextField[] velocityYFields = new TextField[MAX_BODIES];
    private final TextField[] velocityZFields = new TextField[MAX_BODIES];
    private final TextField[] massFields = new TextField[MAX_BODIES];
    @SuppressWarnings("unchecked")
    private final Deque<Point3>[] trails = new ArrayDeque[MAX_BODIES];
    @SuppressWarnings("unchecked")
    private final List<Point3>[] fullTracks = new ArrayList[MAX_BODIES];
    private final List<Point3> photonPath = new ArrayList<>();
    private final List<Point3> animatedPhotonPath = new ArrayList<>();

    private final VBox dashboard = new VBox(8);
    private final VBox editorList = new VBox(6);
    private Canvas canvas;
    private TornadoExecutionPlan executionPlan;
    private TornadoDevice selectedDevice;
    private TornadoDeviceChoice selectedDeviceChoice;
    private int bodyCount;
    private int initialBodyCount;
    private boolean running;
    private boolean planDirty = true;
    private boolean showTrails;
    private boolean showFullTracks;
    private boolean showOrbits;
    private int draggedBodyIndex = -1;
    private double viewScale = INITIAL_VIEW_SCALE;
    private boolean photonAnimating;
    private int visiblePhotonPoints;
    private boolean suppressEditorApply;

    private record Point3(float x, float y, float z) {
    }

    @Override
    public void start(Stage stage) {
        params.set(0, G);
        params.set(1, DT);
        params.set(2, SOFTENING);
        for (int i = 0; i < MAX_BODIES; i++) {
            trails[i] = new ArrayDeque<>(TRAIL_CAPACITY);
            fullTracks[i] = new ArrayList<>();
            colors[i] = Color.hsb((i * 47.0) % 360.0, 0.80, 1.0);
        }

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        canvas = new Canvas(Math.max(760.0, bounds.getWidth() - SIDEBAR_WIDTH), bounds.getHeight());
        canvas.setOnMousePressed(event -> draggedBodyIndex = bodyAt(event.getX(), event.getY()));
        canvas.setOnMouseDragged(event -> dragBodyTo(event.getX(), event.getY()));
        canvas.setOnMouseReleased(_ -> draggedBodyIndex = -1);
        canvas.setOnScroll(event -> {
            zoom(event.getDeltaY(), event.getX(), event.getY());
            event.consume();
        });

        Button addButton = new Button("+");
        addButton.setTooltip(new Tooltip("Add body"));
        applyGravityButtonStyle(addButton, GREEN_BUTTON_STYLE);
        addButton.setStyle(addButton.getStyle() + " -fx-min-width: 34px;");
        addButton.setOnAction(_ -> addBody());

        Button startButton = new Button("Start");
        applyGravityButtonStyle(startButton, GREEN_BUTTON_STYLE);
        startButton.setOnAction(_ -> startSimulation());

        Button resetButton = new Button("Reset");
        applyGravityButtonStyle(resetButton, RED_BUTTON_STYLE);
        resetButton.setOnAction(_ -> resetToInitialState());

        Button photonButton = new Button("Photon");
        photonButton.setTooltip(new Tooltip("Shoot a photon toward Body 1"));
        applyGravityButtonStyle(photonButton, BLUE_BUTTON_STYLE);
        photonButton.setOnAction(_ -> shootPhoton());

        ComboBox<TornadoDeviceChoice> deviceCombo = createDeviceCombo(stage);

        CheckBox trailsBox = new CheckBox("Trails");
        applyControlCheckboxStyle(trailsBox);
        trailsBox.selectedProperty().addListener((_, _, selected) -> {
            showTrails = selected;
            clearTrails();
        });

        CheckBox fullTrackBox = new CheckBox("Full track");
        applyControlCheckboxStyle(fullTrackBox);
        fullTrackBox.selectedProperty().addListener((_, _, selected) -> showFullTracks = selected);

        CheckBox orbitBox = new CheckBox("Stable orbits");
        applyControlCheckboxStyle(orbitBox);
        orbitBox.selectedProperty().addListener((_, _, selected) -> showOrbits = selected);

        FlowPane controlsPane = new FlowPane(8.0, 7.0);
        controlsPane.setPrefWrapLength(SIDEBAR_WIDTH - 42.0);
        controlsPane.getChildren().addAll(
                addButton,
                startButton,
                resetButton,
                photonButton,
                deviceCombo,
                trailsBox,
                fullTrackBox,
                orbitBox);

        editorList.setStyle("-fx-background-color: #10131c;");
        dashboard.setStyle("-fx-background-color: #10131c;");

        Label title = new Label("Body Simulator");
        title.setStyle("-fx-text-fill: #00e59b; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label help = new Label("Add bodies, edit initial position/velocity/mass, then start GPU simulation.");
        help.setWrapText(true);
        help.setStyle("-fx-text-fill: #b8c2d6;");
        Label units = new Label(String.format(
                "Units: distance = simulation space units (du), speed = du/simulation second, mass = simulation mass units (mu). Grid uses Phi = -G*m/r. Photon bending uses weak-field deflection proportional to 2Gm/(c^2*r^2), G = %.1f, c = %.1f du/s. Bodies with mass >= %.0f mu act as black holes and can trap photons.",
                G, PHOTON_LIGHT_SPEED, BLACK_HOLE_MASS_THRESHOLD));
        units.setWrapText(true);
        units.setStyle("-fx-text-fill: #d8deef; -fx-font-size: 11px;");

        ScrollPane editorScroll = new ScrollPane(editorList);
        editorScroll.setFitToWidth(true);
        editorScroll.setStyle("-fx-background: #10131c; -fx-background-color: #10131c; -fx-control-inner-background: #10131c;");
        VBox.setVgrow(editorScroll, Priority.ALWAYS);

        ScrollPane dashboardScroll = new ScrollPane(dashboard);
        dashboardScroll.setFitToWidth(true);
        dashboardScroll.setStyle("-fx-background: #10131c; -fx-background-color: #10131c; -fx-control-inner-background: #10131c;");
        VBox.setVgrow(dashboardScroll, Priority.ALWAYS);

        VBox side = new VBox(10, title, controlsPane, help, units, sectionLabel("Initial bodies"), editorScroll, sectionLabel("Dashboard"), dashboardScroll);
        side.setPadding(new Insets(14));
        side.setPrefWidth(SIDEBAR_WIDTH);
        side.setStyle("-fx-background-color: #10131c; -fx-border-color: #2b3142; -fx-border-width: 0 0 0 1;");

        BorderPane root = new BorderPane(canvas, null, side, null, null);
        Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight(), Color.BLACK);
        stage.setTitle("GPU Body Simulator");
        stage.setScene(scene);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.show();

        snapshotInitialState();
        draw();
        updateDashboard();

        new AnimationTimer() {
            private int frame;

            @Override
            public void handle(long now) {
                if (running && bodyCount > 0) {
                    rebuildPlanIfNeeded();
                    TornadoExecutionResult result = executionPlan.execute();
                    result.transferToHost(posX, posY, posZ, velX, velY, velZ, accX, accY, accZ);
                    if (showTrails && frame % 2 == 0) {
                        appendTrails();
                    }
                    if (frame % 2 == 0) {
                        appendFullTracks();
                    }
                    frame++;
                }
                advancePhotonAnimation();
                draw();
                updateDashboard();
            }
        }.start();
    }

    private ComboBox<TornadoDeviceChoice> createDeviceCombo(Stage stage) {
        ComboBox<TornadoDeviceChoice> deviceCombo = new ComboBox<>();
        List<TornadoDeviceChoice> devices = TornadoDeviceSelector.deviceChoices();
        selectedDeviceChoice = TornadoDeviceSelector.initialDeviceChoice(devices);
        selectedDevice = TornadoDeviceSelector.resolveDevice(stage, selectedDeviceChoice);
        deviceCombo.getItems().setAll(devices);
        deviceCombo.setValue(selectedDeviceChoice);
        deviceCombo.setTooltip(new Tooltip("GPU device"));
        deviceCombo.setPrefWidth(250.0);
        deviceCombo.setMinWidth(250.0);
        deviceCombo.setMaxWidth(250.0);
        deviceCombo.setStyle("-fx-background-color: #1b2533; -fx-border-color: #497aa5; -fx-mark-color: #9ecfff; -fx-text-fill: white;");
        deviceCombo.setButtonCell(tornadoDeviceListCell());
        deviceCombo.setCellFactory(_ -> tornadoDeviceListCell());
        deviceCombo.valueProperty().addListener((_, previousDevice, chosenDevice) -> {
            if (chosenDevice == null || chosenDevice.equals(previousDevice)) {
                return;
            }
            changeDevice(stage, chosenDevice);
        });
        return deviceCombo;
    }

    private ListCell<TornadoDeviceChoice> tornadoDeviceListCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(TornadoDeviceChoice item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setTextFill(Color.WHITE);
                setStyle("-fx-background-color: #1b2533;");
            }
        };
    }

    private void changeDevice(Stage stage, TornadoDeviceChoice deviceChoice) {
        selectedDeviceChoice = deviceChoice;
        selectedDevice = TornadoDeviceSelector.resolveDevice(stage, deviceChoice);
        executionPlan = null;
        planDirty = true;
        resetToInitialState();
    }

    private void startSimulation() {
        if (!running) {
            snapshotInitialState();
        }
        running = true;
        planDirty = true;
        clearTrails();
        clearFullTracks();
        clearPhotonPath();
    }

    private void addBody() {
        if (bodyCount >= MAX_BODIES) {
            return;
        }
        running = false;
        int i = bodyCount++;
        names[i] = "Body " + bodyCount;
        setBody(i, (i % 6 - 2) * 2.0f, (i / 6) * 2.0f, 0.0f, 0.0f, 0.65f + i * 0.03f, 0.0f, 10.0f);
        active.set(i, 1);
        state.set(0, bodyCount);
        snapshotInitialState();
        rebuildEditors();
        planDirty = true;
        draw();
        updateDashboard();
    }

    private void setBody(int i, float x, float y, float z, float vx, float vy, float vz, float bodyMass) {
        posX.set(i, x);
        posY.set(i, y);
        posZ.set(i, z);
        velX.set(i, vx);
        velY.set(i, vy);
        velZ.set(i, vz);
        mass.set(i, Math.max(0.0f, bodyMass));
    }

    private void snapshotInitialState() {
        initialBodyCount = bodyCount;
        for (int i = 0; i < MAX_BODIES; i++) {
            initialPosX[i] = posX.get(i);
            initialPosY[i] = posY.get(i);
            initialPosZ[i] = posZ.get(i);
            initialVelX[i] = velX.get(i);
            initialVelY[i] = velY.get(i);
            initialVelZ[i] = velZ.get(i);
            initialMass[i] = mass.get(i);
        }
    }

    private void resetToInitialState() {
        running = false;
        draggedBodyIndex = -1;
        viewScale = INITIAL_VIEW_SCALE;
        bodyCount = initialBodyCount;
        state.set(0, bodyCount);
        for (int i = 0; i < MAX_BODIES; i++) {
            posX.set(i, initialPosX[i]);
            posY.set(i, initialPosY[i]);
            posZ.set(i, initialPosZ[i]);
            velX.set(i, initialVelX[i]);
            velY.set(i, initialVelY[i]);
            velZ.set(i, initialVelZ[i]);
            mass.set(i, initialMass[i]);
            accX.set(i, 0.0f);
            accY.set(i, 0.0f);
            accZ.set(i, 0.0f);
            active.set(i, i < bodyCount ? 1 : 0);
        }
        clearTrails();
        clearFullTracks();
        clearPhotonPath();
        planDirty = true;
        rebuildEditors();
        draw();
        updateDashboard();
    }

    private void rebuildEditors() {
        suppressEditorApply = true;
        try {
            editorList.getChildren().clear();
            for (int i = 0; i < bodyCount; i++) {
                editorList.getChildren().add(createEditor(i));
            }
        } finally {
            suppressEditorApply = false;
        }
    }

    private VBox createEditor(int i) {
        Label label = new Label(names[i]);
        label.setStyle("-fx-text-fill: " + toHex(colors[i]) + "; -fx-font-weight: bold;");

        TextField x = field(posX.get(i));
        TextField y = field(posY.get(i));
        TextField z = field(posZ.get(i));
        TextField vx = field(velX.get(i));
        TextField vy = field(velY.get(i));
        TextField vz = field(velZ.get(i));
        TextField m = field(mass.get(i));
        positionXFields[i] = x;
        positionYFields[i] = y;
        positionZFields[i] = z;
        velocityXFields[i] = vx;
        velocityYFields[i] = vy;
        velocityZFields[i] = vz;
        massFields[i] = m;

        Runnable apply = () -> {
            if (suppressEditorApply) {
                return;
            }
            if (running) {
                updateEditorFields(i);
                return;
            }
            try {
                setBody(i, parse(x), parse(y), parse(z), parse(vx), parse(vy), parse(vz), parse(m));
                snapshotInitialState();
                planDirty = true;
                clearTrails();
                clearFullTracks();
                clearPhotonPath();
            } catch (NumberFormatException ignored) {
                x.setText(format(posX.get(i)));
                y.setText(format(posY.get(i)));
                z.setText(format(posZ.get(i)));
                vx.setText(format(velX.get(i)));
                vy.setText(format(velY.get(i)));
                vz.setText(format(velZ.get(i)));
                m.setText(format(mass.get(i)));
            }
        };
        for (TextField field : new TextField[]{x, y, z, vx, vy, vz, m}) {
            field.setOnAction(_ -> apply.run());
            field.focusedProperty().addListener((_, _, focused) -> {
                if (!focused) {
                    apply.run();
                }
            });
        }

        GridPane grid = new GridPane();
        grid.setHgap(5);
        grid.setVgap(4);
        addGridField(grid, "X", x, 0, 0);
        addGridField(grid, "Y", y, 2, 0);
        addGridField(grid, "Z", z, 4, 0);
        addGridField(grid, "Vx", vx, 0, 1);
        addGridField(grid, "Vy", vy, 2, 1);
        addGridField(grid, "Vz", vz, 4, 1);
        addGridField(grid, "Mass", m, 0, 2);

        VBox editor = new VBox(4, label, grid);
        editor.setPadding(new Insets(8));
        editor.setStyle("-fx-background-color: #171b27; -fx-border-color: #30384c; -fx-background-radius: 6; -fx-border-radius: 6;");
        return editor;
    }

    private TextField field(float value) {
        TextField field = new TextField(format(value));
        field.setPrefWidth(64);
        field.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        return field;
    }

    private void applyGravityButtonStyle(Button button, String style) {
        button.setStyle(style);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setFocusTraversable(false);
    }

    private void applyControlCheckboxStyle(CheckBox checkBox) {
        checkBox.setStyle("-fx-text-fill: white;");
        checkBox.setMinWidth(Region.USE_PREF_SIZE);
        checkBox.setFocusTraversable(false);
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #d8deef; -fx-font-size: 12px; -fx-font-weight: bold;");
        return label;
    }

    private void addGridField(GridPane grid, String labelText, TextField field, int column, int row) {
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #c4cbe0; -fx-font-size: 11px;");
        grid.add(label, column, row);
        grid.add(field, column + 1, row);
    }

    private void rebuildPlanIfNeeded() {
        if (!planDirty && executionPlan != null) {
            return;
        }
        TaskGraph graph = new TaskGraph("body-simulator")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, posX, posY, posZ, velX, velY, velZ, mass, active, params, state)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, accX, accY, accZ, nextAccX, nextAccY, nextAccZ)
                .task("simulate", BodyPhysicsKernels::simulate,
                        posX, posY, posZ, velX, velY, velZ, accX, accY, accZ, nextAccX, nextAccY, nextAccZ,
                        mass, active, params, state)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, posX, posY, posZ, velX, velY, velZ, accX, accY, accZ);
        executionPlan = TornadoDeviceSelector.applyDevice(new TornadoExecutionPlan(graph.snapshot()), selectedDevice);
        planDirty = false;
    }

    private void appendTrails() {
        for (int i = 0; i < bodyCount; i++) {
            Deque<Point3> trail = trails[i];
            if (trail.size() >= TRAIL_CAPACITY) {
                trail.removeFirst();
            }
            trail.addLast(new Point3(posX.get(i), posY.get(i), posZ.get(i)));
        }
    }

    private void appendFullTracks() {
        for (int i = 0; i < bodyCount; i++) {
            fullTracks[i].add(new Point3(posX.get(i), posY.get(i), posZ.get(i)));
        }
    }

    private void clearTrails() {
        for (Deque<Point3> trail : trails) {
            if (trail != null) {
                trail.clear();
            }
        }
    }

    private void clearFullTracks() {
        for (List<Point3> track : fullTracks) {
            if (track != null) {
                track.clear();
            }
        }
    }

    private void clearPhotonPath() {
        photonPath.clear();
        animatedPhotonPath.clear();
        visiblePhotonPoints = 0;
        photonAnimating = false;
    }

    private void advancePhotonAnimation() {
        if (!photonAnimating) {
            return;
        }
        visiblePhotonPoints = Math.min(photonPath.size(), visiblePhotonPoints + 6);
        photonAnimating = visiblePhotonPoints < photonPath.size();
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        gc.setFill(Color.rgb(4, 6, 12));
        gc.fillRect(0, 0, width, height);
        drawGravityGrid(gc, width, height);

        if (showOrbits) {
            drawStableOrbitGuides(gc);
        }
        if (showTrails) {
            drawTrails(gc);
        }
        if (showFullTracks) {
            drawFullTracks(gc);
        }
        drawPhotonPath(gc);
        for (int i = 0; i < bodyCount; i++) {
            double sx = screenX(posX.get(i));
            double sy = screenY(posY.get(i));
            double r = bodyRadius(i);
            gc.setFill(bodySpherePaint(i, sx, sy, r));
            gc.fillOval(sx - r, sy - r, r * 2.0, r * 2.0);
            gc.setStroke(colors[i].deriveColor(0, 0.75, 0.75, 0.9));
            gc.strokeOval(sx - r, sy - r, r * 2.0, r * 2.0);
            gc.setFill(Color.rgb(220, 228, 242));
            gc.fillText(names[i], sx + r + 4.0, sy - r - 2.0);
        }
    }

    private RadialGradient bodySpherePaint(int i, double sx, double sy, double radius) {
        Color color = colors[i];
        return new RadialGradient(
                0.0,
                0.0,
                sx - radius * 0.35,
                sy - radius * 0.35,
                radius * 1.35,
                false,
                javafx.scene.paint.CycleMethod.NO_CYCLE,
                new Stop(0.0, color.interpolate(Color.WHITE, 0.55)),
                new Stop(0.45, color),
                new Stop(1.0, color.deriveColor(0, 1.0, 0.32, 1.0)));
    }

    private void drawTrails(GraphicsContext gc) {
        for (int i = 0; i < bodyCount; i++) {
            Deque<Point3> trail = trails[i];
            if (trail.size() < 2) {
                continue;
            }
            gc.setStroke(colors[i].deriveColor(0, 1, 1, 0.45));
            Point3 previous = null;
            for (Point3 point : trail) {
                if (previous != null) {
                    gc.strokeLine(screenX(previous.x), screenY(previous.y), screenX(point.x), screenY(point.y));
                }
                previous = point;
            }
        }
    }

    private void drawFullTracks(GraphicsContext gc) {
        for (int i = 0; i < bodyCount; i++) {
            List<Point3> track = fullTracks[i];
            if (track.size() < 2) {
                continue;
            }
            gc.setStroke(colors[i].deriveColor(0, 0.8, 1.15, 0.65));
            Point3 previous = null;
            int stride = Math.max(1, track.size() / FULL_TRACK_RENDER_POINT_LIMIT);
            for (int pointIndex = 0; pointIndex < track.size(); pointIndex += stride) {
                Point3 point = track.get(pointIndex);
                if (previous != null) {
                    gc.strokeLine(screenX(previous.x), screenY(previous.y), screenX(point.x), screenY(point.y));
                }
                previous = point;
            }
            Point3 last = track.getLast();
            if (previous != null && previous != last) {
                gc.strokeLine(screenX(previous.x), screenY(previous.y), screenX(last.x), screenY(last.y));
            }
        }
    }

    private void drawPhotonPath(GraphicsContext gc) {
        int visiblePoints = Math.min(visiblePhotonPoints, photonPath.size());
        if (visiblePoints < 2) {
            return;
        }
        gc.setLineWidth(4.0);
        gc.setStroke(Color.rgb(4, 6, 12, 0.82));
        strokePhotonSegments(gc, visiblePoints);
        gc.setLineWidth(2.0);
        gc.setStroke(Color.rgb(255, 245, 120));
        strokePhotonSegments(gc, visiblePoints);

        Point3 head = photonPath.get(visiblePoints - 1);
        gc.setFill(Color.rgb(255, 255, 190));
        gc.fillOval(screenX(head.x) - 4.0, screenY(head.y) - 4.0, 8.0, 8.0);
        gc.setLineWidth(1.0);
    }

    private void strokePhotonSegments(GraphicsContext gc, int visiblePoints) {
        Point3 previous = null;
        for (int i = 0; i < visiblePoints; i++) {
            Point3 point = photonPath.get(i);
            if (previous != null) {
                gc.strokeLine(screenX(previous.x), screenY(previous.y), screenX(point.x), screenY(point.y));
            }
            previous = point;
        }
    }

    private void drawGravityGrid(GraphicsContext gc, double width, double height) {
        gc.setStroke(Color.rgb(27, 38, 58));
        double worldLeft = -width * 0.5 / viewScale;
        double worldRight = width * 0.5 / viewScale;
        double worldTop = height * 0.5 / viewScale;
        double worldBottom = -height * 0.5 / viewScale;

        for (double x = Math.floor(worldLeft); x <= worldRight; x += GRID_STEP) {
            Point3 previous = null;
            for (double y = worldBottom; y <= worldTop; y += GRID_STEP * 0.35) {
                Point3 current = bentGridPoint(x, y);
                if (previous != null) {
                    gc.strokeLine(screenX(previous.x), screenY(previous.y) - previous.z, screenX(current.x), screenY(current.y) - current.z);
                }
                previous = current;
            }
        }

        for (double y = Math.floor(worldBottom); y <= worldTop; y += GRID_STEP) {
            Point3 previous = null;
            for (double x = worldLeft; x <= worldRight; x += GRID_STEP * 0.35) {
                Point3 current = bentGridPoint(x, y);
                if (previous != null) {
                    gc.strokeLine(screenX(previous.x), screenY(previous.y) - previous.z, screenX(current.x), screenY(current.y) - current.z);
                }
                previous = current;
            }
        }
    }

    private Point3 bentGridPoint(double x, double y) {
        double potential = 0.0;
        double shiftX = 0.0;
        double shiftY = 0.0;
        for (int i = 0; i < bodyCount; i++) {
            if (mass.get(i) <= 0.0f) {
                continue;
            }
            double dx = x - posX.get(i);
            double dy = y - posY.get(i);
            double dz = posZ.get(i);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz + SOFTENING / (viewScale * viewScale));
            potential += -G * mass.get(i) / distance;
            double slope = G * mass.get(i) / (distance * distance * distance);
            shiftX -= dx * slope * 0.00020;
            shiftY -= dy * slope * 0.00020;
        }

        double visualDepth = Math.max(-SPACE_BEND_LIMIT, potential * SPACE_BEND_SCALE);
        return new Point3((float) (x + shiftX), (float) (y + shiftY), (float) visualDepth);
    }

    private void shootPhoton() {
        photonPath.clear();
        animatedPhotonPath.clear();
        visiblePhotonPoints = 0;
        photonAnimating = false;
        if (bodyCount == 0) {
            return;
        }

        double targetX = posX.get(0);
        double targetY = posY.get(0);
        double[] start = farthestCanvasCornerFrom(targetX, targetY);
        double x = start[0];
        double y = start[1];
        double vx = targetX - x;
        double vy = targetY - y;
        double invLen = 1.0 / Math.max(0.000001, Math.sqrt(vx * vx + vy * vy));
        vx *= invLen;
        vy *= invLen;
        double visibleMinSpan = Math.min(canvas.getWidth(), canvas.getHeight()) / viewScale;
        double impactOffset = Math.max(
                PHOTON_IMPACT_PARAMETER,
                Math.min(blackHoleCaptureRadius(mass.get(0)) * 1.6, visibleMinSpan * 0.35));
        double aimX = targetX - vy * impactOffset;
        double aimY = targetY + vx * impactOffset;
        vx = aimX - x;
        vy = aimY - y;
        invLen = 1.0 / Math.max(0.000001, Math.sqrt(vx * vx + vy * vy));
        vx *= invLen;
        vy *= invLen;

        boolean hasBeenVisible = false;
        int visibleSamples = 0;
        for (int step = 0; step < PHOTON_MAX_STEPS; step++) {
            animatedPhotonPath.add(new Point3((float) x, (float) y, 0.0f));
            boolean visible = isPhotonInsideCanvas(x, y, 0.0);
            hasBeenVisible |= visible;
            if (visible) {
                visibleSamples++;
            }
            if (step >= PHOTON_MIN_STEPS && hasBeenVisible && !isPhotonInsideCanvas(x, y, PHOTON_EXIT_MARGIN_PIXELS)) {
                break;
            }
            if (hasBeenVisible && visibleSamples > 2 && isPhotonCapturedByBlackHole(x, y)) {
                break;
            }

            double ax = 0.0;
            double ay = 0.0;
            for (int i = 0; i < bodyCount; i++) {
                if (mass.get(i) <= 0.0f) {
                    continue;
                }
                double dx = posX.get(i) - x;
                double dy = posY.get(i) - y;
                double distSq = dx * dx + dy * dy + SOFTENING / (viewScale * viewScale);
                double dot = dx * vx + dy * vy;
                double transverseX = dx - dot * vx;
                double transverseY = dy - dot * vy;
                double transverseLength = Math.max(0.000001, Math.sqrt(transverseX * transverseX + transverseY * transverseY));
                double deflection = 2.0 * G * mass.get(i) / (PHOTON_LIGHT_SPEED * PHOTON_LIGHT_SPEED * distSq);
                ax += deflection * transverseX / transverseLength;
                ay += deflection * transverseY / transverseLength;
            }

            double curvature = Math.sqrt(ax * ax + ay * ay);
            double stepDistance = Math.max(PHOTON_MIN_STEP_DISTANCE, PHOTON_STEP_DISTANCE / Math.max(1.0, curvature * 4.0));
            vx += ax * stepDistance;
            vy += ay * stepDistance;
            double speed = Math.max(0.000001, Math.sqrt(vx * vx + vy * vy));
            vx /= speed;
            vy /= speed;
            x += vx * stepDistance;
            y += vy * stepDistance;
        }
        photonPath.addAll(animatedPhotonPath);
        visiblePhotonPoints = Math.min(12, photonPath.size());
        photonAnimating = photonPath.size() > visiblePhotonPoints;
    }

    private boolean isPhotonCapturedByBlackHole(double photonX, double photonY) {
        for (int i = 0; i < bodyCount; i++) {
            double bodyMass = mass.get(i);
            if (bodyMass < BLACK_HOLE_MASS_THRESHOLD) {
                continue;
            }
            double dx = photonX - posX.get(i);
            double dy = photonY - posY.get(i);
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance <= visiblePhotonCaptureRadius(bodyMass)) {
                return true;
            }
        }
        return false;
    }

    private double blackHoleCaptureRadius(double bodyMass) {
        double schwarzschildRadius = 2.0 * G * bodyMass / (PHOTON_LIGHT_SPEED * PHOTON_LIGHT_SPEED);
        return Math.max(0.18, schwarzschildRadius * BLACK_HOLE_CAPTURE_RADIUS_MULTIPLIER);
    }

    private double visiblePhotonCaptureRadius(double bodyMass) {
        double visibleMinSpan = Math.min(canvas.getWidth(), canvas.getHeight()) / viewScale;
        return Math.min(blackHoleCaptureRadius(bodyMass), Math.max(0.18, visibleMinSpan * 0.18));
    }

    private boolean isPhotonInsideCanvas(double photonX, double photonY, double marginPixels) {
        double sx = screenX((float) photonX);
        double sy = screenY((float) photonY);
        return sx >= -marginPixels
                && sx <= canvas.getWidth() + marginPixels
                && sy >= -marginPixels
                && sy <= canvas.getHeight() + marginPixels;
    }

    private double[] farthestCanvasCornerFrom(double x, double y) {
        double left = -canvas.getWidth() * 0.5 / viewScale;
        double right = canvas.getWidth() * 0.5 / viewScale;
        double top = canvas.getHeight() * 0.5 / viewScale;
        double bottom = -canvas.getHeight() * 0.5 / viewScale;
        double[][] corners = {
                {left, top},
                {right, top},
                {left, bottom},
                {right, bottom}
        };
        double[] farthest = corners[0];
        double farthestDistance = -1.0;
        for (double[] corner : corners) {
            double dx = corner[0] - x;
            double dy = corner[1] - y;
            double distance = dx * dx + dy * dy;
            if (distance > farthestDistance) {
                farthestDistance = distance;
                farthest = corner;
            }
        }
        return farthest;
    }

    private void drawStableOrbitGuides(GraphicsContext gc) {
        int center = dominantBodyIndex();
        if (center < 0) {
            return;
        }
        for (int i = 0; i < bodyCount; i++) {
            if (i == center || !isStableOrbitCandidate(i, center)) {
                continue;
            }
            float dx = posX.get(i) - posX.get(center);
            float dy = posY.get(i) - posY.get(center);
            double radius = Math.sqrt(dx * dx + dy * dy) * viewScale;
            gc.setStroke(colors[i].deriveColor(0, 0.8, 1.2, 0.32));
            gc.strokeOval(screenX(posX.get(center)) - radius, screenY(posY.get(center)) - radius, radius * 2.0, radius * 2.0);
        }
    }

    private boolean isStableOrbitCandidate(int bodyIndex, int centerIndex) {
        float dx = posX.get(bodyIndex) - posX.get(centerIndex);
        float dy = posY.get(bodyIndex) - posY.get(centerIndex);
        float dz = posZ.get(bodyIndex) - posZ.get(centerIndex);
        float radius = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (radius < 0.0001f || mass.get(centerIndex) <= 0.0f) {
            return false;
        }
        float relativeVx = velX.get(bodyIndex) - velX.get(centerIndex);
        float relativeVy = velY.get(bodyIndex) - velY.get(centerIndex);
        float relativeVz = velZ.get(bodyIndex) - velZ.get(centerIndex);
        float speed = (float) Math.sqrt(relativeVx * relativeVx + relativeVy * relativeVy + relativeVz * relativeVz);
        float circularSpeed = (float) Math.sqrt(G * mass.get(centerIndex) / radius);
        return circularSpeed > 0.0001f && Math.abs(speed - circularSpeed) / circularSpeed < 0.35f;
    }

    private int dominantBodyIndex() {
        int best = -1;
        float bestMass = 0.0f;
        for (int i = 0; i < bodyCount; i++) {
            if (mass.get(i) > bestMass) {
                bestMass = mass.get(i);
                best = i;
            }
        }
        return best;
    }

    private void updateDashboard() {
        dashboard.getChildren().clear();
        if (bodyCount == 0) {
            Label empty = new Label("Empty space");
            empty.setStyle("-fx-text-fill: #96a2bc;");
            dashboard.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < bodyCount; i++) {
            float speed = (float) Math.sqrt(velX.get(i) * velX.get(i) + velY.get(i) * velY.get(i) + velZ.get(i) * velZ.get(i));
            float accel = (float) Math.sqrt(accX.get(i) * accX.get(i) + accY.get(i) * accY.get(i) + accZ.get(i) * accZ.get(i));
            String nearest = nearestBodyText(i);
            Label label = new Label(String.format(
                    "%s  M %.3f mu  P(%.3f, %.3f, %.3f) du  V(%.3f, %.3f, %.3f) du/s  |V| %.3f du/s  |A| %.5f du/s2  %s",
                    names[i], mass.get(i), posX.get(i), posY.get(i), posZ.get(i),
                    velX.get(i), velY.get(i), velZ.get(i), speed, accel, nearest));
            label.setWrapText(true);
            label.setStyle("-fx-text-fill: " + toHex(colors[i]) + "; -fx-font-family: monospace; -fx-font-size: 11px;");
            dashboard.getChildren().add(label);
        }
    }

    private String nearestBodyText(int bodyIndex) {
        if (bodyCount < 2) {
            return "Nearest: -";
        }

        int nearestIndex = -1;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < bodyCount; i++) {
            if (i == bodyIndex) {
                continue;
            }
            double dx = posX.get(i) - posX.get(bodyIndex);
            double dy = posY.get(i) - posY.get(bodyIndex);
            double dz = posZ.get(i) - posZ.get(bodyIndex);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
        }

        return nearestIndex < 0 ? "Nearest: -" : String.format("Nearest: %s %.3f du", names[nearestIndex], nearestDistance);
    }

    private void zoom(double deltaY, double pivotScreenX, double pivotScreenY) {
        double oldScale = viewScale;
        double zoomFactor = deltaY > 0.0 ? 1.12 : 1.0 / 1.12;
        viewScale = Math.clamp(viewScale * zoomFactor, MIN_VIEW_SCALE, MAX_VIEW_SCALE);
        if (Math.abs(viewScale - oldScale) < 0.0001) {
            return;
        }
        draw();
        updateDashboard();
    }

    private int bodyAt(double screenX, double screenY) {
        for (int i = bodyCount - 1; i >= 0; i--) {
            double dx = screenX - screenX(posX.get(i));
            double dy = screenY - screenY(posY.get(i));
            if (Math.sqrt(dx * dx + dy * dy) <= bodyRadius(i) + 4.0) {
                return i;
            }
        }
        return -1;
    }

    private void dragBodyTo(double screenX, double screenY) {
        if (draggedBodyIndex < 0) {
            return;
        }

        running = false;
        int i = draggedBodyIndex;
        posX.set(i, physicsX(screenX));
        posY.set(i, physicsY(screenY));
        snapshotInitialState();
        updatePositionFields(i);
        clearTrails();
        clearFullTracks();
        clearPhotonPath();
        planDirty = true;
        draw();
        updateDashboard();
    }

    private void updatePositionFields(int i) {
        setFieldText(positionXFields[i], posX.get(i));
        setFieldText(positionYFields[i], posY.get(i));
        setFieldText(positionZFields[i], posZ.get(i));
    }

    private void updateEditorFields(int i) {
        updatePositionFields(i);
        setFieldText(velocityXFields[i], velX.get(i));
        setFieldText(velocityYFields[i], velY.get(i));
        setFieldText(velocityZFields[i], velZ.get(i));
        setFieldText(massFields[i], mass.get(i));
    }

    private void setFieldText(TextField field, float value) {
        if (field != null && !field.isFocused()) {
            field.setText(format(value));
        }
    }

    private double bodyRadius(int i) {
        return Math.max(8.0, Math.min(32.0, (3.0 + Math.cbrt(Math.max(0.0f, mass.get(i)))) * 2.0));
    }

    private double screenX(float physicsX) {
        return canvas.getWidth() * 0.5 + physicsX * viewScale;
    }

    private double screenY(float physicsY) {
        return canvas.getHeight() * 0.5 - physicsY * viewScale;
    }

    private float physicsX(double screenX) {
        return (float) ((screenX - canvas.getWidth() * 0.5) / viewScale);
    }

    private float physicsY(double screenY) {
        return (float) ((canvas.getHeight() * 0.5 - screenY) / viewScale);
    }

    private float parse(TextField field) {
        return Float.parseFloat(field.getText().trim().replace(',', '.'));
    }

    private String format(float value) {
        return String.format("%.3f", value);
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X", (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    static void main(String[] args) {
        launch(args);
    }
}
