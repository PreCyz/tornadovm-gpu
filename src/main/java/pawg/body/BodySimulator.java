package pawg.body;

import javafx.animation.*;
import javafx.application.Application;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.controlsfx.control.PopOver;
import pawg.nbody.TornadoDeviceChoice;
import pawg.nbody.TornadoDeviceSelector;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.util.*;

public class BodySimulator extends Application {

    private static final int MAX_BODIES = 256;
    private static final int TRAIL_CAPACITY = 260;
    private static final int SIDEBAR_WIDTH = 520;
    private static final double SIDEBAR_PADDING = 14.0;
    private static final double DEVICE_COMBO_RIGHT_GAP = 15.0;
    private static final double HOVER_POPOVER_GAP = 8.0;
    private static final double DASHBOARD_DRAWER_ANIMATION_MILLIS = 240.0;
    private static final float G = 100.0f;
    private static final float DT = 0.015f;
    private static final float SOFTENING = 25.0f;
    private static final float CENTER_COLLISION_EPSILON = 0.5f;
    private static final double INITIAL_VIEW_SCALE = 45.0;
    private static final double MIN_VIEW_SCALE = 16.0;
    private static final double MAX_VIEW_SCALE = 180.0;
    private static final double GRID_STEP = 0.5;
    private static final double SPACE_BEND_SCALE = 0.025;
    private static final double SPACE_BEND_LIMIT = 70.0;
    private static final double GRID_LATERAL_BEND_LIMIT = GRID_STEP * 2.0;
    private static final double GRID_SEGMENT_MAX_LENGTH_FACTOR = 6.0;
    private static final double GRID_SEGMENT_MIN_LIMIT_PIXELS = 48.0;
    private static final double GRID_MIN_LINE_SPACING_PIXELS = 10.0;
    private static final double GRID_MIN_SAMPLE_SPACING_PIXELS = 4.0;
    private static final double GRID_CLIP_MARGIN_PIXELS = 24.0;
    private static final double GRID_MAX_WORLD_SPAN_FACTOR = 24.0;
    private static final int GRID_MAX_LINES_PER_AXIS = 240;
    private static final int GRID_MAX_SAMPLES_PER_LINE = 600;
    private static final int PHOTON_MIN_STEPS = 900;
    private static final int PHOTON_MAX_STEPS = 60_000;
    private static final double PHOTON_STEP_DISTANCE = 0.08;
    private static final double PHOTON_MIN_STEP_DISTANCE = 0.012;
    private static final double PHOTON_LIGHT_SPEED = 160.0;
    private static final double SI_LIGHT_SPEED = 299_792_458.0;
    private static final double SI_GRAVITATIONAL_CONSTANT = 6.67430e-11;
    private static final double SI_SECONDS_PER_SIMULATION_SECOND = 1.0;
    private static final double METERS_PER_DISTANCE_UNIT =
            SI_LIGHT_SPEED * SI_SECONDS_PER_SIMULATION_SECOND / PHOTON_LIGHT_SPEED;
    private static final double KILOGRAMS_PER_MASS_UNIT =
            G * METERS_PER_DISTANCE_UNIT * METERS_PER_DISTANCE_UNIT * METERS_PER_DISTANCE_UNIT
                    / (SI_GRAVITATIONAL_CONSTANT
                    * SI_SECONDS_PER_SIMULATION_SECOND * SI_SECONDS_PER_SIMULATION_SECOND);
    private static final double SI_SOLAR_MASS_KILOGRAMS = 1.98847e30;
    private static final double BLACK_HOLE_MASS_THRESHOLD = 2_000.0;
    private static final double BLACK_HOLE_CAPTURE_RADIUS_MULTIPLIER = 1.0;
    private static final double PHOTON_BODY_RADIUS = 0.35;
    private static final double PHOTON_MAX_DEFLECTION = Math.PI * 0.95;
    private static final double PHOTON_IMPACT_PARAMETER = 2.4;
    private static final double PHOTON_EXIT_MARGIN_PIXELS = 120.0;
    private static final double GRID_OVERSCAN_WORLD_RATIO = 0.45;
    private static final int SCHWARZSCHILD_GUIDE_SEGMENTS = 192;
    private static final double CAMERA_PITCH_MIN = -Math.PI * 0.48;
    private static final double CAMERA_PITCH_MAX = Math.PI * 0.48;
    private static final double CAMERA_DRAG_SENSITIVITY = 0.006;
    private static final double CAMERA_PAN_VIEWPORT_FRACTION = 0.10;
    private static final double CAMERA_FAST_PAN_MULTIPLIER = 4.0;
    private static final double PLANE_INTERSECTION_EPSILON = 1.0e-5;
    private static final int ORBIT_GUIDE_SEGMENTS = 160;
    private static final double ROTATION_INDICATOR_BODY_RADIUS = 3.25;
    private static final int FULL_TRACK_RENDER_POINT_LIMIT = 2_000;
    private static final boolean CPU_PHYSICS = Boolean.getBoolean("pawg.body.cpu");
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
    private final String[] initialNames = new String[MAX_BODIES];
    private final Color[] initialColors = new Color[MAX_BODIES];
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
    private final int[] indicatorBodyOrder = new int[MAX_BODIES];
    private final double[][] indicatorViewPositions = new double[MAX_BODIES][3];
    private final double[] clippedLine = new double[4];
    private double photonImpactParameter = PHOTON_IMPACT_PARAMETER;

    private final VBox dashboard = new VBox(8);
    private final VBox editorList = new VBox(6);
    private final DoubleProperty dashboardDrawerProgress = new SimpleDoubleProperty(this, "dashboardDrawerProgress");
    private PopOver guidePopover;
    private PopOver unitDescriptionPopover;
    private Label unitCalibrationHeader;
    private Label unitCalibrationExplanation;
    private PopOver unitCalibrationPopover;
    /**
     * The dynamically refreshed dashboard rows.  Keeping this separate from
     * the calibration header preserves that label's hover handlers while the
     * simulation timer refreshes body telemetry.
     */
    private VBox dashboardDynamicContent;
    private Canvas canvas;
    private Button dashboardHideButton;
    private Button dashboardRestoreButton;
    private Timeline dashboardDrawerTimeline;
    private AnimationTimer simulationTimer;
    private Label elapsedTimeLabel;
    private boolean elapsedClockRunning;
    private long elapsedAccumulatedNanos;
    private long elapsedSegmentStartNanos = -1L;
    private long lastAnimationNow = -1L;
    private long displayedElapsedSecond = -1L;
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
    private boolean showSchwarzschildRadii;
    private int draggedBodyIndex = -1;
    private double viewScale = INITIAL_VIEW_SCALE;
    private boolean photonAnimating;
    private int visiblePhotonPoints;
    private boolean suppressEditorApply;
    private double cameraYaw;
    private double cameraPitch;
    private double cameraRoll;
    private double viewCenterX;
    private double viewCenterY;
    private double viewCenterZ;
    private double dragStartX;
    private double dragStartY;
    private double dragStartYaw;
    private double dragStartPitch;
    private double dragStartRoll;
    private double draggedBodyViewDepth;
    private boolean rotatingCamera;
    private boolean rotatingRoll;
    private int mergedBodySequence;
    private float[] gridPointX = new float[0];
    private float[] gridPointY = new float[0];
    private float[] gridPointZ = new float[0];
    private int[] gridLineStarts = new int[0];
    private int[] gridLineLengths = new int[0];
    private int gridPointCount;
    private int gridLineCount;
    private boolean gridGeometryDirty = true;
    private double cachedGridWidth = Double.NaN;
    private double cachedGridHeight = Double.NaN;
    private double cachedGridViewScale = Double.NaN;
    private double cachedGridViewCenterX = Double.NaN;
    private double cachedGridViewCenterY = Double.NaN;
    private double cachedGridViewCenterZ = Double.NaN;
    private double cachedGridCameraYaw = Double.NaN;
    private double cachedGridCameraPitch = Double.NaN;
    private double cachedGridCameraRoll = Double.NaN;
    private double cachedGridSampleStep = GRID_STEP * 0.35;

    private record Point3(float x, float y, float z) {
    }

    private record GridProjection(
            double centerX, double centerY, double centerZ,
            double cosYaw, double sinYaw,
            double cosPitch, double sinPitch,
            double cosRoll, double sinRoll) {
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
        configureCanvasInteractions(canvas);

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
        photonButton.setOnAction(_ -> askPhotonOffsetAndShoot(stage));

        Button hideDashboardButton = createDashboardHideButton();

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

        CheckBox schwarzschildRadiusBox = new CheckBox("Schwarzschild radius");
        schwarzschildRadiusBox.setTooltip(new Tooltip("Show the event horizon of each black-hole body"));
        applyControlCheckboxStyle(schwarzschildRadiusBox);
        schwarzschildRadiusBox.selectedProperty().addListener((_, _, selected) -> {
            showSchwarzschildRadii = selected;
            draw();
        });

        HBox deviceRow = new HBox(deviceCombo);
        HBox buttonRow = new HBox(8.0, addButton, startButton, resetButton, photonButton);
        HBox checkboxRow = new HBox(12.0, trailsBox, fullTrackBox, orbitBox, schwarzschildRadiusBox);
        VBox controlsPane = new VBox(7.0, deviceRow, buttonRow, checkboxRow);

        editorList.setStyle("-fx-background-color: #10131c;");
        dashboard.setStyle("-fx-background-color: #10131c;");

        Label title = new Label("Body Simulator");
        title.setStyle("-fx-text-fill: #00e59b; -fx-font-size: 15px; -fx-font-weight: bold;");
        elapsedTimeLabel = new Label("Elapsed 00:00:00");
        elapsedTimeLabel.setAccessibleText("Elapsed simulation time");
        elapsedTimeLabel.setStyle("-fx-text-fill: #c4cbe0; -fx-font-family: monospace; -fx-font-size: 12px;");
        HBox titleRow = new HBox(12.0, title, elapsedTimeLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        SidebarHoverTriggers hoverTriggers = createSidebarHoverTriggers();
        Label guide = hoverTriggers.guide();
        Label unitDescription = hoverTriggers.unitDescription();

        ScrollPane editorScroll = new ScrollPane(editorList);
        editorScroll.setFitToWidth(true);
        editorScroll.setStyle("-fx-background: #10131c; -fx-background-color: #10131c; -fx-control-inner-background: #10131c;");
        VBox.setVgrow(editorScroll, Priority.ALWAYS);

        ScrollPane dashboardScroll = new ScrollPane(dashboard);
        dashboardScroll.setFitToWidth(true);
        dashboardScroll.setStyle("-fx-background: #10131c; -fx-background-color: #10131c; -fx-control-inner-background: #10131c;");
        VBox.setVgrow(dashboardScroll, Priority.ALWAYS);

        VBox side = new VBox(10, titleRow, controlsPane, guide, unitDescription, sectionLabel("Initial bodies"), editorScroll, sectionLabel("Dashboard"), dashboardScroll);
        side.setPadding(new Insets(14));
        side.setPrefWidth(SIDEBAR_WIDTH);
        side.setMinWidth(SIDEBAR_WIDTH);
        side.setMaxWidth(SIDEBAR_WIDTH);
        side.setStyle("-fx-background-color: #10131c; -fx-border-color: #2b3142; -fx-border-width: 0 0 0 1;");

        DrawerLayout drawerLayout = createDrawerLayout(canvas, side, hideDashboardButton);
        StackPane root = drawerLayout.root();
        Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight(), Color.BLACK);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleCameraKeyPressed);
        stage.setTitle("GPU Body Simulator");
        BodyStageIcons.addBodyIcon(stage);
        stage.setScene(scene);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.show();

        snapshotInitialState();
        draw();
        updateDashboard();

        simulationTimer = new AnimationTimer() {
            private int frame;

            @Override
            public void handle(long now) {
                updateElapsedClock(now);
                if (running && bodyCount > 0) {
                    stepSimulation();
                    resolveBodyCollisions();
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
        };
        simulationTimer.start();
    }

    /**
     * Installs only JavaFX input routing.  It deliberately does not discover a
     * TornadoVM device or build an execution plan, so interaction tests can use
     * an isolated canvas.
     */
    void configureCanvasInteractions(Canvas simulationCanvas) {
        canvas = simulationCanvas;
        canvas.setOnMousePressed(event -> {
            canvas.requestFocus();
            if (event.getButton() == MouseButton.SECONDARY) {
                int removedBodyIndex = bodyAt(event.getX(), event.getY());
                if (removedBodyIndex >= 0) {
                    removeBody(removedBodyIndex);
                    event.consume();
                    return;
                }
            }
            draggedBodyIndex = bodyAt(event.getX(), event.getY());
            rotatingCamera = draggedBodyIndex < 0;
            rotatingRoll = rotatingCamera && event.getButton() == MouseButton.SECONDARY;
            if (draggedBodyIndex >= 0) {
                draggedBodyViewDepth = worldToView(
                        posX.get(draggedBodyIndex), posY.get(draggedBodyIndex), posZ.get(draggedBodyIndex))[2];
            }
            dragStartX = event.getX();
            dragStartY = event.getY();
            dragStartYaw = cameraYaw;
            dragStartPitch = cameraPitch;
            dragStartRoll = cameraRoll;
        });
        canvas.setOnMouseDragged(event -> {
            if (rotatingCamera) {
                double dx = event.getX() - dragStartX;
                double dy = event.getY() - dragStartY;
                if (rotatingRoll || event.isShiftDown()) {
                    cameraRoll = dragStartRoll + dx * CAMERA_DRAG_SENSITIVITY;
                } else {
                    cameraYaw = dragStartYaw + dx * CAMERA_DRAG_SENSITIVITY;
                    cameraPitch = Math.clamp(dragStartPitch - dy * CAMERA_DRAG_SENSITIVITY,
                            CAMERA_PITCH_MIN, CAMERA_PITCH_MAX);
                }
                invalidateGridGeometry();
                draw();
            } else {
                dragBodyTo(event.getX(), event.getY());
            }
        });
        canvas.setOnMouseReleased(_ -> {
            draggedBodyIndex = -1;
            rotatingCamera = false;
            rotatingRoll = false;
        });
        canvas.setOnScroll(event -> {
            zoom(event.getDeltaY(), event.getX(), event.getY());
            event.consume();
        });
    }

    private ComboBox<TornadoDeviceChoice> createDeviceCombo(Stage stage) {
        ComboBox<TornadoDeviceChoice> deviceCombo = new ComboBox<>();
        List<TornadoDeviceChoice> devices = TornadoDeviceSelector.deviceChoices();
        selectedDeviceChoice = TornadoDeviceSelector.initialDeviceChoice(devices);
        selectedDevice = TornadoDeviceSelector.resolveDevice(stage, selectedDeviceChoice);
        deviceCombo.getItems().setAll(devices);
        deviceCombo.setValue(selectedDeviceChoice);
        deviceCombo.setTooltip(new Tooltip("GPU device"));
        double comboWidth = SIDEBAR_WIDTH - SIDEBAR_PADDING * 2.0 - DEVICE_COMBO_RIGHT_GAP;
        deviceCombo.setPrefWidth(comboWidth);
        deviceCombo.setMinWidth(comboWidth);
        deviceCombo.setMaxWidth(comboWidth);
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
            {
                hoverProperty().addListener((_, _, _) -> updateCellStyle());
            }

            @Override
            protected void updateItem(TornadoDeviceChoice item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setTextFill(Color.WHITE);
                updateCellStyle();
            }

            private void updateCellStyle() {
                setStyle(isEmpty()
                        ? ""
                        : isHover()
                                ? "-fx-background-color: #365f85;"
                                : "-fx-background-color: #1b2533;");
            }
        };
    }

    private void changeDevice(Stage stage, TornadoDeviceChoice deviceChoice) {
        selectedDeviceChoice = deviceChoice;
        selectedDevice = TornadoDeviceSelector.resolveDevice(stage, deviceChoice);
        invalidateExecutionPlan();
        resetToInitialState();
    }

    private void startSimulation() {
        if (!running) {
            snapshotInitialState();
        }
        running = true;
        resumeElapsedClock();
        invalidateExecutionPlan();
        clearTrails();
        clearFullTracks();
        clearPhotonPath();
    }

    private void addBody() {
        if (bodyCount >= MAX_BODIES) {
            return;
        }
        int i = bodyCount++;
        names[i] = "Body " + bodyCount;
        colors[i] = Color.hsb((i * 47.0) % 360.0, 0.80, 1.0);
        setBody(i, (i % 6 - 2) * 2.0f, (i / 6f) * 2.0f, 0.0f, 0.0f, 0.65f + i * 0.03f, 0.0f, 10.0f);
        accX.set(i, 0.0f);
        accY.set(i, 0.0f);
        accZ.set(i, 0.0f);
        nextAccX.set(i, 0.0f);
        nextAccY.set(i, 0.0f);
        nextAccZ.set(i, 0.0f);
        active.set(i, 1);
        state.set(0, bodyCount);
        trails[i].clear();
        fullTracks[i].clear();
        snapshotInitialState();
        rebuildEditors();
        invalidateGridGeometry();
        draw();
        updateDashboard();
    }

    /**
     * Removes a canvas-selected body and persists the compacted host state as the
     * next reset snapshot before the old device execution plan is discarded.
     */
    private void removeBody(int removedIndex) {
        if (removedIndex < 0 || removedIndex >= bodyCount) {
            return;
        }
        draggedBodyIndex = -1;
        removeBodySlot(removedIndex);
        state.set(0, bodyCount);
        snapshotInitialState();
        clearTrails();
        clearFullTracks();
        clearPhotonPath();
        invalidateExecutionPlan();
        rebuildEditors();
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
            initialNames[i] = names[i];
            initialColors[i] = colors[i];
        }
    }

    private void resetToInitialState() {
        running = false;
        resetElapsedClock();
        draggedBodyIndex = -1;
        viewScale = INITIAL_VIEW_SCALE;
        cameraYaw = 0.0;
        cameraPitch = 0.0;
        cameraRoll = 0.0;
        recenterView();
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
            names[i] = initialNames[i];
            colors[i] = initialColors[i];
            accX.set(i, 0.0f);
            accY.set(i, 0.0f);
            accZ.set(i, 0.0f);
            active.set(i, i < bodyCount ? 1 : 0);
        }
        clearTrails();
        clearFullTracks();
        clearPhotonPath();
        invalidateExecutionPlan();
        rebuildEditors();
        draw();
        updateDashboard();
    }

    private void resolveBodyCollisions() {
        boolean mergedAny = false;
        boolean mergedThisPass;
        do {
            mergedThisPass = false;
            collisionSearch:
            for (int i = 0; i < bodyCount; i++) {
                if (active.get(i) == 0 || mass.get(i) <= 0.0f) {
                    continue;
                }
                for (int j = i + 1; j < bodyCount; j++) {
                    if (active.get(j) == 0 || mass.get(j) <= 0.0f) {
                        continue;
                    }
                    float dx = posX.get(j) - posX.get(i);
                    float dy = posY.get(j) - posY.get(i);
                    float dz = posZ.get(j) - posZ.get(i);
                    if (dx * dx + dy * dy + dz * dz
                            <= CENTER_COLLISION_EPSILON * CENTER_COLLISION_EPSILON) {
                        mergeBodies(i, j);
                        mergedAny = true;
                        mergedThisPass = true;
                        break collisionSearch;
                    }
                }
            }
        } while (mergedThisPass);

        if (mergedAny) {
            state.set(0, bodyCount);
            invalidateGridGeometry();
            clearTrails();
            clearFullTracks();
            clearPhotonPath();
            rebuildEditors();
        }
    }

    private void mergeBodies(int survivor, int absorbed) {
        float survivorMass = mass.get(survivor);
        float absorbedMass = mass.get(absorbed);
        float mergedMass = survivorMass + absorbedMass;
        if (mergedMass <= 0.0f) {
            removeBodySlot(absorbed);
            return;
        }

        BodyCollision.State merged = BodyCollision.merge(
                new BodyCollision.State(
                        posX.get(survivor), posY.get(survivor), posZ.get(survivor),
                        velX.get(survivor), velY.get(survivor), velZ.get(survivor), survivorMass),
                new BodyCollision.State(
                        posX.get(absorbed), posY.get(absorbed), posZ.get(absorbed),
                        velX.get(absorbed), velY.get(absorbed), velZ.get(absorbed), absorbedMass));
        Color mergedColor = colors[survivor].interpolate(colors[absorbed], absorbedMass / mergedMass);

        posX.set(survivor, merged.x());
        posY.set(survivor, merged.y());
        posZ.set(survivor, merged.z());
        velX.set(survivor, merged.vx());
        velY.set(survivor, merged.vy());
        velZ.set(survivor, merged.vz());
        mass.set(survivor, merged.mass());
        accX.set(survivor, 0.0f);
        accY.set(survivor, 0.0f);
        accZ.set(survivor, 0.0f);
        nextAccX.set(survivor, 0.0f);
        nextAccY.set(survivor, 0.0f);
        nextAccZ.set(survivor, 0.0f);
        active.set(survivor, 1);
        names[survivor] = "Merged Body " + (++mergedBodySequence);
        colors[survivor] = mergedColor;
        removeBodySlot(absorbed);
    }

    private void removeBodySlot(int removedIndex) {
        for (int source = removedIndex + 1; source < bodyCount; source++) {
            copyBodySlot(source, source - 1);
        }
        clearBodySlot(bodyCount - 1);
        bodyCount--;
    }

    private void copyBodySlot(int source, int target) {
        posX.set(target, posX.get(source));
        posY.set(target, posY.get(source));
        posZ.set(target, posZ.get(source));
        velX.set(target, velX.get(source));
        velY.set(target, velY.get(source));
        velZ.set(target, velZ.get(source));
        accX.set(target, accX.get(source));
        accY.set(target, accY.get(source));
        accZ.set(target, accZ.get(source));
        nextAccX.set(target, nextAccX.get(source));
        nextAccY.set(target, nextAccY.get(source));
        nextAccZ.set(target, nextAccZ.get(source));
        mass.set(target, mass.get(source));
        active.set(target, active.get(source));
        names[target] = names[source];
        colors[target] = colors[source];
    }

    private void clearBodySlot(int index) {
        setBody(index, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        accX.set(index, 0.0f);
        accY.set(index, 0.0f);
        accZ.set(index, 0.0f);
        nextAccX.set(index, 0.0f);
        nextAccY.set(index, 0.0f);
        nextAccZ.set(index, 0.0f);
        active.set(index, 0);
        names[index] = null;
        colors[index] = null;
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
                invalidateExecutionPlan();
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

    Button createDashboardHideButton() {
        dashboardHideButton = createDashboardEdgeButton("▶", "Hide dashboard");
        dashboardHideButton.setOnAction(_ -> setDashboardDrawerHidden(true));
        return dashboardHideButton;
    }

    record DrawerLayout(StackPane root, StackPane simulationArea, VBox sidebar,
                        Button hideButton, Button restoreButton) {
    }

    /**
     * Builds the production drawer arrangement without discovering a TornadoVM
     * device.  Package tests can therefore exercise the same bindings and
     * controls as {@link #start(Stage)} in a normal JavaFX stage.
     */
    DrawerLayout createDrawerLayout(Canvas simulationCanvas, VBox side, Button hideButton) {
        canvas = simulationCanvas;
        dashboardHideButton = hideButton;
        StackPane simulationArea = new StackPane(simulationCanvas);
        StackPane.setAlignment(simulationArea, Pos.TOP_LEFT);
        simulationArea.setMinSize(0.0, 0.0);

        StackPane root = new StackPane(simulationArea, side);
        DoubleBinding visibleSidebarWidth = dashboardDrawerProgress.subtract(1.0).negate().multiply(SIDEBAR_WIDTH);
        simulationArea.prefWidthProperty().bind(root.widthProperty().subtract(visibleSidebarWidth));
        simulationArea.maxWidthProperty().bind(root.widthProperty().subtract(visibleSidebarWidth));
        simulationArea.prefHeightProperty().bind(root.heightProperty());
        simulationArea.maxHeightProperty().bind(root.heightProperty());
        simulationCanvas.widthProperty().bind(simulationArea.widthProperty());
        simulationCanvas.heightProperty().bind(simulationArea.heightProperty());
        simulationCanvas.widthProperty().addListener((_, _, _) -> invalidateGridGeometry());
        simulationCanvas.heightProperty().addListener((_, _, _) -> invalidateGridGeometry());

        StackPane.setAlignment(side, Pos.TOP_RIGHT);
        side.setMinWidth(SIDEBAR_WIDTH);
        side.setMaxWidth(SIDEBAR_WIDTH);
        side.translateXProperty().bind(dashboardDrawerProgress.multiply(SIDEBAR_WIDTH));
        Rectangle rootClip = new Rectangle();
        rootClip.widthProperty().bind(root.widthProperty());
        rootClip.heightProperty().bind(root.heightProperty());
        root.setClip(rootClip);

        dashboardRestoreButton = createDashboardEdgeButton("◀", "Show dashboard");
        dashboardRestoreButton.setOnAction(_ -> setDashboardDrawerHidden(false));
        setDashboardEdgeButtonActive(dashboardHideButton, true);
        setDashboardEdgeButtonActive(dashboardRestoreButton, false);
        root.getChildren().addAll(dashboardHideButton, dashboardRestoreButton);
        return new DrawerLayout(root, simulationArea, side, dashboardHideButton, dashboardRestoreButton);
    }

    private Button createDashboardEdgeButton(String arrow, String description) {
        Button button = new Button(arrow);
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        applyGravityButtonStyle(button, BLUE_BUTTON_STYLE);
        button.setFocusTraversable(true);
        button.setStyle(button.getStyle() + " -fx-min-width: 28px; -fx-min-height: 42px;");
        StackPane.setAlignment(button, Pos.CENTER_RIGHT);
        StackPane.setMargin(button, new Insets(0.0, 4.0, 0.0, 0.0));
        return button;
    }

    /**
     * Drives the sidebar as a drawer.  Progress is shared by the drawer
     * translation and simulation-area width, so they cannot drift apart during
     * a transition or a window resize.
     */
    private void setDashboardDrawerHidden(boolean hidden) {
        double targetProgress = hidden ? 1.0 : 0.0;
        if (dashboardHideButton == null || dashboardRestoreButton == null) {
            return;
        }
        if (dashboardDrawerTimeline != null) {
            dashboardDrawerTimeline.stop();
            dashboardDrawerTimeline = null;
        }
        if (hidden) {
            hidePopover(guidePopover);
            hidePopover(unitDescriptionPopover);
            hidePopover(unitCalibrationPopover);
        }
        updateDashboardEdgeButtons(null);
        if (Math.abs(dashboardDrawerProgress.get() - targetProgress) < 1.0e-6) {
            dashboardDrawerProgress.set(targetProgress);
            updateDashboardEdgeButtons(hidden);
            return;
        }

        KeyValue drawerTarget = new KeyValue(dashboardDrawerProgress, targetProgress, Interpolator.EASE_BOTH);
        dashboardDrawerTimeline = new Timeline(new KeyFrame(
                Duration.millis(DASHBOARD_DRAWER_ANIMATION_MILLIS), drawerTarget));
        dashboardDrawerTimeline.setOnFinished(_ -> {
            dashboardDrawerProgress.set(targetProgress);
            dashboardDrawerTimeline = null;
            updateDashboardEdgeButtons(hidden);
        });
        dashboardDrawerTimeline.play();
    }

    private void updateDashboardEdgeButtons(Boolean dashboardHidden) {
        setDashboardEdgeButtonActive(dashboardHideButton, Boolean.FALSE.equals(dashboardHidden));
        setDashboardEdgeButtonActive(dashboardRestoreButton, Boolean.TRUE.equals(dashboardHidden));
    }

    private void setDashboardEdgeButtonActive(Button button, boolean active) {
        button.setVisible(active);
        button.setManaged(active);
        button.setDisable(!active);
        button.setMouseTransparent(!active);
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

    private record HoverPopoverTrigger(Label trigger, PopOver popover) {
    }

    record SidebarHoverTriggers(Label guide, Label unitDescription) {
    }

    SidebarHoverTriggers createSidebarHoverTriggers() {
        HoverPopoverTrigger guideTrigger = createHoverPopoverLabel("Guide", "Add bodies, edit initial position/velocity/mass, then start GPU simulation. "
                + "Drag empty space to rotate, Shift-drag or right-drag to roll, scroll to zoom, "
                + "use arrow keys to pan (Shift for faster movement), and Home to recenter. "
                + "Right-click a body to remove it from the simulation.");
        guidePopover = guideTrigger.popover();

        HoverPopoverTrigger unitDescriptionTrigger = createHoverPopoverLabel("Unit description", String.format(
                "Units: distance = simulation space units (du), speed = du/simulation second, mass = simulation mass units (mu). Grid uses Phi = -G*m/r. Photon bending uses weak-field deflection proportional to 2Gm/(c^2*r^2), G = %.1f, c = %.1f du/s. Bodies with mass >= %.0f mu act as black holes and can trap photons.",
                G, PHOTON_LIGHT_SPEED, BLACK_HOLE_MASS_THRESHOLD));
        unitDescriptionPopover = unitDescriptionTrigger.popover();
        return new SidebarHoverTriggers(guideTrigger.trigger(), unitDescriptionTrigger.trigger());
    }

    private HoverPopoverTrigger createHoverPopoverLabel(String text, String explanation) {
        Label trigger = new Label(text);
        trigger.setStyle("-fx-text-fill: #9ecfff; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
        PopOver popover = createHoverPopover(createPopoverContent(explanation));
        installHoverPopover(trigger, popover);
        return new HoverPopoverTrigger(trigger, popover);
    }

    private Label createPopoverContent(String text) {
        Label content = new Label(text);
        content.setWrapText(true);
        content.setMaxWidth(SIDEBAR_WIDTH - SIDEBAR_PADDING * 4.0);
        content.setPadding(new Insets(9));
        content.setStyle("-fx-text-fill: #f2f5fb; -fx-font-size: 11px; -fx-background-color: #151a26; -fx-background-radius: 5;");
        content.setMouseTransparent(true);
        return content;
    }

    private PopOver createHoverPopover(Label content) {
        PopOver popover = new PopOver(content);
        popover.setAnimated(false);
        popover.setAutoHide(false);
        popover.setDetachable(false);
        return popover;
    }

    private void installHoverPopover(Label trigger, PopOver popover) {
        popover.setOnShown(_ -> keepPopoverClearOfTrigger(trigger, popover));
        trigger.setOnMouseEntered(_ -> {
            if (!popover.isShowing()) {
                placePopoverOnRoomierSide(trigger, popover);
                // A negative offset creates a gap between the arrow and owner.
                // ControlsFX otherwise uses a positive overlap by default.
                popover.show(trigger, -HOVER_POPOVER_GAP);
            }
        });
        trigger.setOnMouseExited(_ -> popover.hide());
    }

    private void placePopoverOnRoomierSide(Label trigger, PopOver popover) {
        Bounds triggerBounds = trigger.localToScreen(trigger.getBoundsInLocal());
        if (triggerBounds == null) {
            return;
        }
        Rectangle2D screenBounds = Screen.getScreensForRectangle(
                        triggerBounds.getMinX(), triggerBounds.getMinY(),
                        triggerBounds.getWidth(), triggerBounds.getHeight())
                .stream()
                .findFirst()
                .map(Screen::getVisualBounds)
                .orElseGet(() -> Screen.getPrimary().getVisualBounds());
        double leftSpace = triggerBounds.getMinX() - screenBounds.getMinX();
        double rightSpace = screenBounds.getMaxX() - triggerBounds.getMaxX();
        popover.setArrowLocation(leftSpace >= rightSpace
                ? PopOver.ArrowLocation.RIGHT_TOP
                : PopOver.ArrowLocation.LEFT_TOP);
    }

    private void keepPopoverClearOfTrigger(Label trigger, PopOver popover) {
        Bounds triggerBounds = trigger.localToScreen(trigger.getBoundsInLocal());
        if (triggerBounds == null || popover.getWidth() <= 0.0) {
            return;
        }
        if (popover.getArrowLocation() == PopOver.ArrowLocation.RIGHT_TOP) {
            double maximumX = triggerBounds.getMinX() - HOVER_POPOVER_GAP - popover.getWidth();
            popover.setX(Math.min(popover.getX(), maximumX));
        } else {
            double minimumX = triggerBounds.getMaxX() + HOVER_POPOVER_GAP;
            popover.setX(Math.max(popover.getX(), minimumX));
        }
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
        closeExecutionPlan();
        TaskGraph graph = new TaskGraph("body-simulator")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION,
                        posX, posY, posZ, velX, velY, velZ, accX, accY, accZ,
                        nextAccX, nextAccY, nextAccZ, mass, active, state)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, params)
                .task("current-acceleration", BodyPhysicsKernels::computeAcceleration,
                        posX, posY, posZ, accX, accY, accZ, mass, active, params, state)
                .task("position-update", BodyPhysicsKernels::updatePositions,
                        posX, posY, posZ, velX, velY, velZ, accX, accY, accZ, active, params, state)
                .task("next-acceleration-velocity-update", BodyPhysicsKernels::computeNextAccelerationAndUpdateVelocity,
                        posX, posY, posZ, velX, velY, velZ, accX, accY, accZ,
                        nextAccX, nextAccY, nextAccZ, mass, active, params, state)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, posX, posY, posZ, velX, velY, velZ, accX, accY, accZ);
        TornadoExecutionPlan nextPlan = new TornadoExecutionPlan(graph.snapshot());
        try {
            executionPlan = TornadoDeviceSelector.applyDevice(nextPlan, selectedDevice);
        } catch (RuntimeException | Error failure) {
            closePlanQuietly(nextPlan);
            throw failure;
        }
        planDirty = false;
    }

    private void stepSimulation() {
        if (CPU_PHYSICS) {
            BodyPhysicsKernels.simulateOnCpu(
                    posX, posY, posZ, velX, velY, velZ, accX, accY, accZ,
                    nextAccX, nextAccY, nextAccZ, mass, active, params, state);
        } else {
            rebuildPlanIfNeeded();
            executionPlan.execute();
        }
        invalidateGridGeometry();
    }

    private void invalidateExecutionPlan() {
        planDirty = true;
        invalidateGridGeometry();
        closeExecutionPlan();
    }

    private void invalidateGridGeometry() {
        gridGeometryDirty = true;
    }

    private void closeExecutionPlan() {
        TornadoExecutionPlan currentPlan = executionPlan;
        executionPlan = null;
        closePlanQuietly(currentPlan);
    }

    private static void closePlanQuietly(TornadoExecutionPlan plan) {
        if (plan == null) {
            return;
        }
        try {
            plan.close();
        } catch (Exception _) {
            // Closing is best effort; TornadoVM can reclaim stale device memory.
        }
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

    private void resumeElapsedClock() {
        if (!elapsedClockRunning) {
            elapsedClockRunning = true;
            elapsedSegmentStartNanos = -1L;
        }
    }

    private void pauseElapsedClock() {
        if (!elapsedClockRunning) {
            return;
        }
        if (elapsedSegmentStartNanos >= 0L && lastAnimationNow >= elapsedSegmentStartNanos) {
            elapsedAccumulatedNanos += lastAnimationNow - elapsedSegmentStartNanos;
        }
        elapsedClockRunning = false;
        elapsedSegmentStartNanos = -1L;
        updateElapsedLabel(elapsedAccumulatedNanos);
    }

    private void resetElapsedClock() {
        elapsedClockRunning = false;
        elapsedAccumulatedNanos = 0L;
        elapsedSegmentStartNanos = -1L;
        displayedElapsedSecond = -1L;
        updateElapsedLabel(0L);
    }

    private void updateElapsedClock(long now) {
        lastAnimationNow = now;
        long elapsedNanos = elapsedAccumulatedNanos;
        if (elapsedClockRunning) {
            if (elapsedSegmentStartNanos < 0L) {
                elapsedSegmentStartNanos = now;
            }
            elapsedNanos += Math.max(0L, now - elapsedSegmentStartNanos);
        }
        updateElapsedLabel(elapsedNanos);
    }

    private void updateElapsedLabel(long elapsedNanos) {
        long elapsedSecond = Math.max(0L, elapsedNanos) / 1_000_000_000L;
        if (elapsedTimeLabel != null && elapsedSecond != displayedElapsedSecond) {
            elapsedTimeLabel.setText("Elapsed " + formatElapsedNanos(elapsedNanos));
            displayedElapsedSecond = elapsedSecond;
        }
    }

    static String formatElapsedNanos(long elapsedNanos) {
        long totalSeconds = Math.max(0L, elapsedNanos) / 1_000_000_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = totalSeconds / 60L % 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private void draw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        gc.setFill(Color.rgb(4, 6, 12));
        gc.fillRect(0, 0, width, height);
        drawGravityGrid(gc, width, height);

        if (showSchwarzschildRadii) {
            drawSchwarzschildRadii(gc);
        }

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
            double[] projected = projectPoint(posX.get(i), posY.get(i), posZ.get(i));
            double sx = projected[0];
            double sy = projected[1];
            double r = bodyRadius(i);
            gc.setFill(bodySpherePaint(i, sx, sy, r));
            gc.fillOval(sx - r, sy - r, r * 2.0, r * 2.0);
            gc.setStroke(colors[i].deriveColor(0, 0.75, 0.75, 0.9));
            gc.strokeOval(sx - r, sy - r, r * 2.0, r * 2.0);
            gc.setFill(Color.rgb(220, 228, 242));
            gc.fillText(names[i], sx + r + 4.0, sy - r - 2.0);
        }
        drawRotationIndicator(gc);
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
                    strokeProjectedLine(gc, previous, point);
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
                    strokeProjectedLine(gc, previous, point);
                }
                previous = point;
            }
            Point3 last = track.getLast();
            if (previous != null && previous != last) {
                strokeProjectedLine(gc, previous, last);
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
        double[] projectedHead = projectPoint(head.x, head.y, head.z);
        gc.fillOval(projectedHead[0] - 4.0, projectedHead[1] - 4.0, 8.0, 8.0);
        gc.setLineWidth(1.0);
    }

    private void strokePhotonSegments(GraphicsContext gc, int visiblePoints) {
        Point3 previous = null;
        for (int i = 0; i < visiblePoints; i++) {
            Point3 point = photonPath.get(i);
            if (previous != null) {
                strokeProjectedLine(gc, previous, point);
            }
            previous = point;
        }
    }

    private Point3 curvedSpaceRenderPoint(double x, double y) {
        Point3 bent = bentGridPoint(x, y);
        return new Point3(bent.x, bent.y, bent.z);
    }

    private void drawGravityGrid(GraphicsContext gc, double width, double height) {
        gc.setStroke(Color.rgb(27, 38, 58));
        rebuildGridGeometryIfNeeded(width, height);
        GridProjection projection = gridProjection();
        for (int line = 0; line < gridLineCount; line++) {
            int start = gridLineStarts[line];
            int end = start + gridLineLengths[line];
            for (int point = start + 1; point < end; point++) {
                strokeProjectedGridLine(gc,
                        gridPointX[point - 1], gridPointY[point - 1], gridPointZ[point - 1],
                        gridPointX[point], gridPointY[point], gridPointZ[point],
                        cachedGridSampleStep, projection);
            }
        }
    }

    private void rebuildGridGeometryIfNeeded(double width, double height) {
        if (!gridGeometryDirty
                && Double.compare(cachedGridWidth, width) == 0
                && Double.compare(cachedGridHeight, height) == 0
                && Double.compare(cachedGridViewScale, viewScale) == 0
                && Double.compare(cachedGridViewCenterX, viewCenterX) == 0
                && Double.compare(cachedGridViewCenterY, viewCenterY) == 0
                && Double.compare(cachedGridViewCenterZ, viewCenterZ) == 0
                && Double.compare(cachedGridCameraYaw, cameraYaw) == 0
                && Double.compare(cachedGridCameraPitch, cameraPitch) == 0
                && Double.compare(cachedGridCameraRoll, cameraRoll) == 0) {
            return;
        }

        double worldWidth = width / viewScale;
        double worldHeight = height / viewScale;
        double[][] corners = canvasCorners(width, height);
        double worldLeft = Double.POSITIVE_INFINITY;
        double worldRight = Double.NEGATIVE_INFINITY;
        double worldTop = Double.NEGATIVE_INFINITY;
        double worldBottom = Double.POSITIVE_INFINITY;
        for (double[] corner : corners) {
            worldLeft = Math.min(worldLeft, corner[0]);
            worldRight = Math.max(worldRight, corner[0]);
            worldTop = Math.max(worldTop, corner[1]);
            worldBottom = Math.min(worldBottom, corner[1]);
        }

        double overscan = Math.max(GRID_STEP * 8.0,
                Math.max(worldRight - worldLeft, worldTop - worldBottom) * GRID_OVERSCAN_WORLD_RATIO);
        overscan = Math.max(overscan, SPACE_BEND_LIMIT);
        worldLeft -= overscan;
        worldRight += overscan;
        worldBottom -= overscan;
        worldTop += overscan;

        double maximumSpan = Math.max(GRID_STEP * 2.0,
                Math.max(Math.max(worldWidth, worldHeight) * GRID_MAX_WORLD_SPAN_FACTOR,
                        SPACE_BEND_LIMIT * 4.0));
        double centerX = (worldLeft + worldRight) * 0.5;
        double centerY = (worldBottom + worldTop) * 0.5;
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY)) {
            centerX = viewCenterX;
            centerY = viewCenterY;
        }
        double spanX = Math.clamp(worldRight - worldLeft, GRID_STEP * 2.0, maximumSpan);
        double spanY = Math.clamp(worldTop - worldBottom, GRID_STEP * 2.0, maximumSpan);
        worldLeft = centerX - spanX * 0.5;
        worldRight = centerX + spanX * 0.5;
        worldBottom = centerY - spanY * 0.5;
        worldTop = centerY + spanY * 0.5;

        double lineStep = Math.max(GRID_STEP, GRID_MIN_LINE_SPACING_PIXELS / viewScale);
        double sampleStep = Math.max(GRID_STEP * 0.35, GRID_MIN_SAMPLE_SPACING_PIXELS / viewScale);
        lineStep = Math.max(lineStep, Math.max(spanX, spanY) / (GRID_MAX_LINES_PER_AXIS - 1.0));
        sampleStep = Math.max(sampleStep, Math.max(spanX, spanY) / (GRID_MAX_SAMPLES_PER_LINE - 1.0));
        float[] bent = new float[3];
        gridPointCount = 0;
        gridLineCount = 0;

        double firstXLine = Math.floor(worldLeft / lineStep) * lineStep;
        double firstYLine = Math.floor(worldBottom / lineStep) * lineStep;
        int verticalLineCount = boundedSampleCount(firstXLine, worldRight, lineStep, GRID_MAX_LINES_PER_AXIS);
        int horizontalLineCount = boundedSampleCount(firstYLine, worldTop, lineStep, GRID_MAX_LINES_PER_AXIS);
        int verticalSampleCount = boundedSampleCount(worldBottom, worldTop, sampleStep, GRID_MAX_SAMPLES_PER_LINE);
        int horizontalSampleCount = boundedSampleCount(worldLeft, worldRight, sampleStep, GRID_MAX_SAMPLES_PER_LINE);

        for (int line = 0; line < verticalLineCount; line++) {
            double x = firstXLine + line * lineStep;
            int lineStart = gridPointCount;
            for (int sample = 0; sample < verticalSampleCount; sample++) {
                double y = interpolatedSample(worldBottom, worldTop, sampleStep, sample, verticalSampleCount);
                bentGridPointInto(x, y, bent);
                appendGridPoint(bent);
            }
            appendGridLine(lineStart, gridPointCount - lineStart);
        }

        for (int line = 0; line < horizontalLineCount; line++) {
            double y = firstYLine + line * lineStep;
            int lineStart = gridPointCount;
            for (int sample = 0; sample < horizontalSampleCount; sample++) {
                double x = interpolatedSample(worldLeft, worldRight, sampleStep, sample, horizontalSampleCount);
                bentGridPointInto(x, y, bent);
                appendGridPoint(bent);
            }
            appendGridLine(lineStart, gridPointCount - lineStart);
        }

        cachedGridWidth = width;
        cachedGridHeight = height;
        cachedGridViewScale = viewScale;
        cachedGridViewCenterX = viewCenterX;
        cachedGridViewCenterY = viewCenterY;
        cachedGridViewCenterZ = viewCenterZ;
        cachedGridCameraYaw = cameraYaw;
        cachedGridCameraPitch = cameraPitch;
        cachedGridCameraRoll = cameraRoll;
        cachedGridSampleStep = sampleStep;
        gridGeometryDirty = false;
    }

    private static int boundedSampleCount(double minimum, double maximum, double step, int limit) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || !Double.isFinite(step) || step <= 0.0) {
            return 0;
        }
        return Math.clamp((int) Math.ceil(Math.max(0.0, maximum - minimum) / step) + 1, 2, limit);
    }

    private static double interpolatedSample(double minimum, double maximum, double step,
                                             int sample, int sampleCount) {
        return sample == sampleCount - 1 ? maximum : Math.min(maximum, minimum + sample * step);
    }

    private void appendGridPoint(float[] point) {
        ensureGridPointCapacity(gridPointCount + 1);
        gridPointX[gridPointCount] = point[0];
        gridPointY[gridPointCount] = point[1];
        gridPointZ[gridPointCount] = point[2];
        gridPointCount++;
    }

    private void appendGridLine(int start, int length) {
        if (length < 2) {
            return;
        }
        ensureGridLineCapacity(gridLineCount + 1);
        gridLineStarts[gridLineCount] = start;
        gridLineLengths[gridLineCount] = length;
        gridLineCount++;
    }

    private void ensureGridPointCapacity(int required) {
        if (required <= gridPointX.length) {
            return;
        }
        int capacity = Math.max(required, Math.max(4_096, gridPointX.length * 2));
        gridPointX = Arrays.copyOf(gridPointX, capacity);
        gridPointY = Arrays.copyOf(gridPointY, capacity);
        gridPointZ = Arrays.copyOf(gridPointZ, capacity);
    }

    private void ensureGridLineCapacity(int required) {
        if (required <= gridLineStarts.length) {
            return;
        }
        int capacity = Math.max(required, Math.max(256, gridLineStarts.length * 2));
        gridLineStarts = Arrays.copyOf(gridLineStarts, capacity);
        gridLineLengths = Arrays.copyOf(gridLineLengths, capacity);
    }

    private Point3 bentGridPoint(double x, double y) {
        float[] bent = new float[3];
        bentGridPointInto(x, y, bent);
        return new Point3(bent[0], bent[1], bent[2]);
    }

    private void bentGridPointInto(double x, double y, float[] result) {
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
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz + SOFTENING);
            potential += -G * mass.get(i) / distance;
            double slope = G * mass.get(i) / (distance * distance * distance);
            shiftX -= dx * slope * 0.00020;
            shiftY -= dy * slope * 0.00020;
        }

        double lateralShift = Math.hypot(shiftX, shiftY);
        if (lateralShift > 0.0) {
            double boundedShift = GRID_LATERAL_BEND_LIMIT
                    * Math.tanh(lateralShift / GRID_LATERAL_BEND_LIMIT);
            double bendScale = boundedShift / lateralShift;
            shiftX *= bendScale;
            shiftY *= bendScale;
        }

        double visualDepth = Math.max(-SPACE_BEND_LIMIT, potential * SPACE_BEND_SCALE);
        result[0] = (float) (x + shiftX);
        result[1] = (float) (y + shiftY);
        result[2] = (float) visualDepth;
    }

    private void askPhotonOffsetAndShoot(Stage stage) {
        TextInputDialog dialog = new TextInputDialog(format((float) photonImpactParameter));
        dialog.setTitle("Photon offset");
        dialog.setHeaderText("Set initial photon offset");
        dialog.setContentText("Distance from Body 1 center:");
        if (stage != null && stage.getScene() != null) {
            dialog.initOwner(stage);
        }

        dialog.showAndWait().ifPresent(value -> {
            try {
                photonImpactParameter = Math.max(0.0, Float.parseFloat(value.trim().replace(',', '.')));
                shootPhoton();
            } catch (NumberFormatException _) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Photon offset");
                alert.setHeaderText("Invalid photon offset");
                alert.setContentText("Enter a non-negative number.");
                if (stage != null && stage.getScene() != null) {
                    alert.initOwner(stage);
                }
                alert.showAndWait();
            }
        });
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
        double[] direction = photonDirectionToward(targetX, targetY);
        double vx = direction[0];
        double vy = direction[1];
        double normalX = -vy;
        double normalY = vx;
        double startDistance = photonStartDistance(targetX, targetY);
        double x = targetX - vx * startDistance + normalX * photonImpactParameter;
        double y = targetY - vy * startDistance + normalY * photonImpactParameter;
        double[] deflectionUsed = new double[MAX_BODIES];
        double[] deflectionLimit = new double[MAX_BODIES];
        for (int i = 0; i < bodyCount; i++) {
            double impactParameter = photonLineDistance(x, y, vx, vy, posX.get(i), posY.get(i));
            deflectionLimit[i] = photonDeflectionLimit(i, impactParameter);
        }

        boolean hasBeenVisible = false;
        int visibleSamples = 0;
        double[] deflectionContribution = new double[MAX_BODIES];
        for (int step = 0; step < PHOTON_MAX_STEPS; step++) {
            animatedPhotonPath.add(curvedSpaceRenderPoint(x, y));
            boolean visible = isPhotonInsideCanvas(x, y, 0.0);
            hasBeenVisible |= visible;
            if (visible) {
                visibleSamples++;
            }
            if (step >= PHOTON_MIN_STEPS && hasBeenVisible && !isPhotonInsideCanvas(x, y, PHOTON_EXIT_MARGIN_PIXELS)) {
                break;
            }
            if (hasBeenVisible && visibleSamples > 2 && isPhotonCaptured(x, y, vx, vy)) {
                break;
            }

            double ax = 0.0;
            double ay = 0.0;
            for (int i = 0; i < bodyCount; i++) {
                deflectionContribution[i] = 0.0;
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
                double availableDeflection = Math.max(0.0, deflectionLimit[i] - deflectionUsed[i]);
                if (availableDeflection > 0.0) {
                    double contribution = Math.min(deflection, availableDeflection / PHOTON_STEP_DISTANCE);
                    ax += contribution * transverseX / transverseLength;
                    ay += contribution * transverseY / transverseLength;
                    deflectionContribution[i] = contribution;
                }
            }

            double curvature = Math.sqrt(ax * ax + ay * ay);
            double stepDistance = Math.max(PHOTON_MIN_STEP_DISTANCE, PHOTON_STEP_DISTANCE / Math.max(1.0, curvature * 4.0));
            for (int i = 0; i < bodyCount; i++) {
                deflectionUsed[i] = Math.min(deflectionLimit[i],
                        deflectionUsed[i] + deflectionContribution[i] * stepDistance);
            }
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
        if (!photonPath.isEmpty()) {
            resumeElapsedClock();
        }
    }

    private boolean isPhotonCaptured(double photonX, double photonY, double photonVx, double photonVy) {
        for (int i = 0; i < bodyCount; i++) {
            double bodyMass = mass.get(i);
            double dx = photonX - posX.get(i);
            double dy = photonY - posY.get(i);
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance <= photonBodyRadius(i)) {
                return true;
            }
            if (bodyMass >= BLACK_HOLE_MASS_THRESHOLD
                    && distance <= blackHoleCaptureRadius(bodyMass)
                    && photonLineDistance(photonX, photonY, photonVx, photonVy, posX.get(i), posY.get(i))
                    <= photonCriticalImpactParameter(bodyMass)) {
                return true;
            }
        }
        return false;
    }

    private double photonDeflectionLimit(int bodyIndex, double impactParameter) {
        double bodyMass = mass.get(bodyIndex);
        double absoluteImpact = Math.max(photonBodyRadius(bodyIndex), Math.abs(impactParameter));
        double weakFieldDeflection = 4.0 * G * bodyMass
                / (PHOTON_LIGHT_SPEED * PHOTON_LIGHT_SPEED * absoluteImpact);
        return Math.min(PHOTON_MAX_DEFLECTION, weakFieldDeflection);
    }

    private double photonCriticalImpactParameter(double bodyMass) {
        double gravitationalRadius = G * bodyMass / (PHOTON_LIGHT_SPEED * PHOTON_LIGHT_SPEED);
        return 3.0 * Math.sqrt(3.0) * gravitationalRadius;
    }

    private double photonBodyRadius(int bodyIndex) {
        return PHOTON_BODY_RADIUS;
    }

    private double photonLineDistance(double photonX, double photonY, double photonVx, double photonVy,
                                      double bodyX, double bodyY) {
        return Math.abs((bodyX - photonX) * photonVy - (bodyY - photonY) * photonVx);
    }

    private double blackHoleCaptureRadius(double bodyMass) {
        return Math.max(0.18, schwarzschildRadius(bodyMass) * BLACK_HOLE_CAPTURE_RADIUS_MULTIPLIER);
    }

    private double schwarzschildRadius(double bodyMass) {
        return 2.0 * G * bodyMass / (PHOTON_LIGHT_SPEED * PHOTON_LIGHT_SPEED);
    }

    private double visiblePhotonCaptureRadius(double bodyMass) {
        double visibleMinSpan = Math.min(canvas.getWidth(), canvas.getHeight()) / viewScale;
        return Math.clamp(visibleMinSpan * 0.18, 0.18, blackHoleCaptureRadius(bodyMass));
    }

    private boolean isPhotonInsideCanvas(double photonX, double photonY, double marginPixels) {
        double[] projected = projectPoint(photonX, photonY, 0.0);
        double sx = projected[0];
        double sy = projected[1];
        return sx >= -marginPixels
                && sx <= canvas.getWidth() + marginPixels
                && sy >= -marginPixels
                && sy <= canvas.getHeight() + marginPixels;
    }

    private double[] photonDirectionToward(double targetX, double targetY) {
        double[] corner = farthestCanvasCornerFrom(targetX, targetY);
        double vx = targetX - corner[0];
        double vy = targetY - corner[1];
        double invLen = 1.0 / Math.max(0.000001, Math.sqrt(vx * vx + vy * vy));
        return new double[]{vx * invLen, vy * invLen};
    }

    private double photonStartDistance(double targetX, double targetY) {
        double maxDistance = 0.0;
        double[][] corners = canvasCorners();
        for (double[] corner : corners) {
            double dx = corner[0] - targetX;
            double dy = corner[1] - targetY;
            maxDistance = Math.max(maxDistance, Math.sqrt(dx * dx + dy * dy));
        }
        return maxDistance + PHOTON_EXIT_MARGIN_PIXELS / viewScale;
    }

    private double[] farthestCanvasCornerFrom(double x, double y) {
        double[][] corners = canvasCorners();
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

    private double[][] canvasCorners() {
        return canvasCorners(canvas.getWidth(), canvas.getHeight());
    }

    private double[][] canvasCorners(double width, double height) {
        return new double[][]{
                screenToWorldOnPlane(0.0, 0.0, 0.0, width, height),
                screenToWorldOnPlane(width, 0.0, 0.0, width, height),
                screenToWorldOnPlane(0.0, height, 0.0, width, height),
                screenToWorldOnPlane(width, height, 0.0, width, height)
        };
    }

    private void drawSchwarzschildRadii(GraphicsContext gc) {
        gc.save();
        gc.setLineWidth(1.5);
        gc.setLineDashes(10.0, 7.0);
        for (int i = 0; i < bodyCount; i++) {
            if (mass.get(i) < BLACK_HOLE_MASS_THRESHOLD) {
                continue;
            }
            double centerX = posX.get(i);
            double centerY = posY.get(i);
            double centerZ = posZ.get(i);
            double radius = schwarzschildRadius(mass.get(i));
            Color guideColor = colors[i].interpolate(Color.WHITE, 0.55).deriveColor(0.0, 1.0, 1.0, 0.85);
            gc.setStroke(guideColor);
            double labelX = Double.NaN;
            double labelY = Double.NaN;
            gc.beginPath();
            for (int segment = 0; segment <= SCHWARZSCHILD_GUIDE_SEGMENTS; segment++) {
                double angle = Math.PI * 2.0 * segment / SCHWARZSCHILD_GUIDE_SEGMENTS;
                double[] projected = projectPoint(
                        centerX + Math.cos(angle) * radius,
                        centerY + Math.sin(angle) * radius,
                        centerZ);
                if (segment == 0) {
                    gc.moveTo(projected[0], projected[1]);
                } else {
                    gc.lineTo(projected[0], projected[1]);
                }
                boolean labelFits = projected[0] >= 8.0 && projected[0] <= canvas.getWidth() - 100.0
                        && projected[1] >= 18.0 && projected[1] <= canvas.getHeight() - 8.0;
                if (labelFits && (Double.isNaN(labelY) || projected[1] < labelY)) {
                    labelX = projected[0];
                    labelY = projected[1];
                }
            }
            gc.closePath();
            gc.stroke();
            if (!Double.isNaN(labelY)) {
                gc.setLineDashes();
                gc.setFont(Font.font("Monospaced", 11));
                gc.setTextAlign(TextAlignment.LEFT);
                gc.setTextBaseline(VPos.BOTTOM);
                gc.setFill(Color.rgb(4, 6, 12, 0.92));
                gc.fillText("Event horizon", labelX + 7.0, labelY - 3.0);
                gc.setFill(guideColor);
                gc.fillText("Event horizon", labelX + 6.0, labelY - 4.0);
                gc.setLineDashes(10.0, 7.0);
            }
        }
        gc.restore();
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
            gc.setStroke(colors[i].deriveColor(0, 0.8, 1.2, 0.32));
            drawStableOrbitGuide(gc, i, center);
        }
    }

    private void drawStableOrbitGuide(GraphicsContext gc, int bodyIndex, int centerIndex) {
        double relativeX = posX.get(bodyIndex) - posX.get(centerIndex);
        double relativeY = posY.get(bodyIndex) - posY.get(centerIndex);
        double relativeZ = posZ.get(bodyIndex) - posZ.get(centerIndex);
        double radius = Math.sqrt(relativeX * relativeX + relativeY * relativeY + relativeZ * relativeZ);
        if (radius < 0.000001) {
            return;
        }

        double e1X = relativeX / radius;
        double e1Y = relativeY / radius;
        double e1Z = relativeZ / radius;
        double velocityX = velX.get(bodyIndex) - velX.get(centerIndex);
        double velocityY = velY.get(bodyIndex) - velY.get(centerIndex);
        double velocityZ = velZ.get(bodyIndex) - velZ.get(centerIndex);
        double normalX = relativeY * velocityZ - relativeZ * velocityY;
        double normalY = relativeZ * velocityX - relativeX * velocityZ;
        double normalZ = relativeX * velocityY - relativeY * velocityX;
        double normalLength = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (normalLength < 0.000001) {
            normalX = 0.0;
            normalY = 0.0;
            normalZ = 1.0;
            if (Math.abs(e1Z) > 0.95) {
                normalX = 0.0;
                normalY = 1.0;
                normalZ = 0.0;
            }
        } else {
            normalX /= normalLength;
            normalY /= normalLength;
            normalZ /= normalLength;
        }

        double e2X = normalY * e1Z - normalZ * e1Y;
        double e2Y = normalZ * e1X - normalX * e1Z;
        double e2Z = normalX * e1Y - normalY * e1X;
        double e2Length = Math.sqrt(e2X * e2X + e2Y * e2Y + e2Z * e2Z);
        if (e2Length < 0.000001) {
            return;
        }
        e2X /= e2Length;
        e2Y /= e2Length;
        e2Z /= e2Length;

        double centerX = posX.get(centerIndex);
        double centerY = posY.get(centerIndex);
        double centerZ = posZ.get(centerIndex);
        double[] rotated = new double[3];
        double[] previous = new double[2];
        double[] projected = new double[2];
        for (int segment = 0; segment <= ORBIT_GUIDE_SEGMENTS; segment++) {
            double angle = Math.PI * 2.0 * segment / ORBIT_GUIDE_SEGMENTS;
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            projectPointInto(
                    centerX + radius * (e1X * cosine + e2X * sine),
                    centerY + radius * (e1Y * cosine + e2Y * sine),
                    centerZ + radius * (e1Z * cosine + e2Z * sine),
                    rotated, projected);
            if (segment > 0) {
                strokeClippedLine(gc, previous[0], previous[1], projected[0], projected[1], 0.0);
            }
            double[] swap = previous;
            previous = projected;
            projected = swap;
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
        ensureUnitCalibrationPopover();
        unitCalibrationExplanation.setText(unitCalibrationText());
        dashboardDynamicContent.getChildren().clear();

        if (bodyCount == 0) {
            Label empty = new Label("Empty space");
            empty.setStyle("-fx-text-fill: #96a2bc;");
            dashboardDynamicContent.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < bodyCount; i++) {
            float speed = (float) Math.sqrt(velX.get(i) * velX.get(i) + velY.get(i) * velY.get(i) + velZ.get(i) * velZ.get(i));
            float accel = (float) Math.sqrt(accX.get(i) * accX.get(i) + accY.get(i) * accY.get(i) + accZ.get(i) * accZ.get(i));
            String nearest = nearestBodyText(i);
            Label label = new Label(String.format(
                    "%s  M %.6g mu (%.6g M☉)  P(%.3f, %.3f, %.3f) du  V(%.3f, %.3f, %.3f) du/s  |V| %.3f du/s  |A| %.5f du/s2  %s",
                    names[i], mass.get(i), simulationMassUnitsToSolarMasses(mass.get(i)),
                    posX.get(i), posY.get(i), posZ.get(i),
                    velX.get(i), velY.get(i), velZ.get(i), speed, accel, nearest));
            label.setWrapText(true);
            label.setStyle("-fx-text-fill: " + toHex(colors[i]) + "; -fx-font-family: monospace; -fx-font-size: 11px;");
            dashboardDynamicContent.getChildren().add(label);
        }
        if (!photonPath.isEmpty()) {
            Label photonHeader = new Label("Photon");
            photonHeader.setStyle("-fx-text-fill: #fff596; -fx-font-size: 12px; -fx-font-weight: bold;");
            dashboardDynamicContent.getChildren().add(photonHeader);

            Label photonDetails = new Label(String.format(
                    "Speed %.3f du/s  Offset %.3f du  Path points %d",
                    PHOTON_LIGHT_SPEED, photonImpactParameter, photonPath.size()));
            photonDetails.setWrapText(true);
            photonDetails.setStyle("-fx-text-fill: #fffbd0; -fx-font-family: monospace; -fx-font-size: 11px;");
            dashboardDynamicContent.getChildren().add(photonDetails);

            for (int i = 0; i < bodyCount; i++) {
                Label radius = new Label(String.format(
                        "%s  Schwarzschild radius %.6f du",
                        names[i], schwarzschildRadius(mass.get(i))));
                radius.setWrapText(true);
                radius.setStyle("-fx-text-fill: #fffbd0; -fx-font-family: monospace; -fx-font-size: 11px;");
                dashboardDynamicContent.getChildren().add(radius);
            }
        }
    }

    private void ensureUnitCalibrationPopover() {
        if (unitCalibrationHeader != null) {
            return;
        }
        unitCalibrationHeader = new Label("Unit calibration");
        unitCalibrationHeader.setStyle("-fx-text-fill: #9ecfff; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
        unitCalibrationExplanation = createPopoverContent(unitCalibrationText());
        unitCalibrationPopover = createHoverPopover(unitCalibrationExplanation);
        installHoverPopover(unitCalibrationHeader, unitCalibrationPopover);
        dashboardDynamicContent = new VBox(8);
        dashboard.getChildren().setAll(unitCalibrationHeader, dashboardDynamicContent);
    }

    private String unitCalibrationText() {
        return String.format(
                "Assuming 1 simulation second = %.0f SI second and photon speed = c: "
                        + "1 du = %.4e m (%.3f km), 1 mu = %.4e kg = %.6g solar masses (M☉), "
                        + "and 1 solar mass (M☉) = %.6g mu. "
                        + "This calibration follows from G = %.1f du3/(mu*s2); changing the simulation-time calibration changes both SI conversions.",
                SI_SECONDS_PER_SIMULATION_SECOND,
                METERS_PER_DISTANCE_UNIT, METERS_PER_DISTANCE_UNIT / 1_000.0,
                KILOGRAMS_PER_MASS_UNIT, simulationMassUnitsToSolarMasses(1.0),
                solarMassesToSimulationMassUnits(1.0),
                G);
    }

    static double simulationMassUnitsToSolarMasses(double simulationMassUnits) {
        return simulationMassUnits * KILOGRAMS_PER_MASS_UNIT / SI_SOLAR_MASS_KILOGRAMS;
    }

    static double solarMassesToSimulationMassUnits(double solarMasses) {
        return solarMasses * SI_SOLAR_MASS_KILOGRAMS / KILOGRAMS_PER_MASS_UNIT;
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
        double nextScale = Math.clamp(viewScale * zoomFactor, MIN_VIEW_SCALE, MAX_VIEW_SCALE);
        if (Math.abs(nextScale - oldScale) < 0.0001) {
            return;
        }
        double[] worldBeforeZoom = screenToWorldAtViewDepth(pivotScreenX, pivotScreenY, 0.0, oldScale);
        viewScale = nextScale;
        double[] worldAfterZoom = screenToWorldAtViewDepth(pivotScreenX, pivotScreenY, 0.0, viewScale);
        viewCenterX += worldBeforeZoom[0] - worldAfterZoom[0];
        viewCenterY += worldBeforeZoom[1] - worldAfterZoom[1];
        viewCenterZ += worldBeforeZoom[2] - worldAfterZoom[2];
        invalidateGridGeometry();
        draw();
        updateDashboard();
    }

    private void handleCameraKeyPressed(KeyEvent event) {
        if (canvas.getScene() != null && canvas.getScene().getFocusOwner() instanceof Control) {
            return;
        }

        KeyCode code = event.getCode();
        if (code == KeyCode.HOME) {
            recenterView();
            draw();
            event.consume();
            return;
        }
        if (code != KeyCode.LEFT && code != KeyCode.RIGHT && code != KeyCode.UP && code != KeyCode.DOWN) {
            return;
        }

        double acceleration = event.isShiftDown() ? CAMERA_FAST_PAN_MULTIPLIER : 1.0;
        double horizontalStep = canvas.getWidth() / viewScale * CAMERA_PAN_VIEWPORT_FRACTION * acceleration;
        double verticalStep = canvas.getHeight() / viewScale * CAMERA_PAN_VIEWPORT_FRACTION * acceleration;
        double viewX = code == KeyCode.LEFT ? -horizontalStep : code == KeyCode.RIGHT ? horizontalStep : 0.0;
        double viewY = code == KeyCode.DOWN ? -verticalStep : code == KeyCode.UP ? verticalStep : 0.0;
        double[] worldDelta = rotateViewToWorld(viewX, viewY, 0.0, cameraYaw, cameraPitch, cameraRoll);
        viewCenterX += worldDelta[0];
        viewCenterY += worldDelta[1];
        viewCenterZ += worldDelta[2];
        invalidateGridGeometry();
        draw();
        event.consume();
    }

    private void recenterView() {
        viewCenterX = 0.0;
        viewCenterY = 0.0;
        viewCenterZ = 0.0;
        invalidateGridGeometry();
    }

    private int bodyAt(double screenX, double screenY) {
        for (int i = bodyCount - 1; i >= 0; i--) {
            double[] projected = projectPoint(posX.get(i), posY.get(i), posZ.get(i));
            double dx = screenX - projected[0];
            double dy = screenY - projected[1];
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
        pauseElapsedClock();
        int i = draggedBodyIndex;
        double[] world = screenToWorldAtViewDepth(screenX, screenY, draggedBodyViewDepth, viewScale);
        posX.set(i, (float) world[0]);
        posY.set(i, (float) world[1]);
        posZ.set(i, (float) world[2]);
        snapshotInitialState();
        updatePositionFields(i);
        clearTrails();
        clearFullTracks();
        clearPhotonPath();
        invalidateExecutionPlan();
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
        return Math.clamp((3.0 + Math.cbrt(Math.max(0.0f, mass.get(i)))) * 2.0, 8.0, 32.0);
    }

    private void strokeProjectedLine(GraphicsContext gc, Point3 from, Point3 to) {
        double[] fromScreen = projectPoint(from.x, from.y, from.z);
        double[] toScreen = projectPoint(to.x, to.y, to.z);
        strokeClippedLine(gc, fromScreen[0], fromScreen[1], toScreen[0], toScreen[1], 0.0);
    }

    void strokeProjectedGridLine(GraphicsContext gc,
                                 double fromX, double fromY, double fromZ,
                                 double toX, double toY, double toZ, double sampleStep) {
        strokeProjectedGridLine(gc, fromX, fromY, fromZ, toX, toY, toZ, sampleStep, gridProjection());
    }

    private void strokeProjectedGridLine(GraphicsContext gc,
                                         double fromX, double fromY, double fromZ,
                                         double toX, double toY, double toZ, double sampleStep,
                                         GridProjection projection) {
        double cosYaw = projection.cosYaw;
        double sinYaw = projection.sinYaw;
        double cosPitch = projection.cosPitch;
        double sinPitch = projection.sinPitch;
        double cosRoll = projection.cosRoll;
        double sinRoll = projection.sinRoll;

        double relativeFromX = fromX - projection.centerX;
        double relativeFromY = fromY - projection.centerY;
        double relativeFromZ = fromZ - projection.centerZ;
        double fromYawX = relativeFromX * cosYaw + relativeFromZ * sinYaw;
        double fromYawZ = -relativeFromX * sinYaw + relativeFromZ * cosYaw;
        double fromPitchedY = relativeFromY * cosPitch - fromYawZ * sinPitch;
        double fromRolledX = fromYawX * cosRoll - fromPitchedY * sinRoll;
        double fromRolledY = fromYawX * sinRoll + fromPitchedY * cosRoll;
        double fromScreenX = canvas.getWidth() * 0.5 + fromRolledX * viewScale;
        double fromScreenY = canvas.getHeight() * 0.5 - fromRolledY * viewScale;

        double relativeToX = toX - projection.centerX;
        double relativeToY = toY - projection.centerY;
        double relativeToZ = toZ - projection.centerZ;
        double toYawX = relativeToX * cosYaw + relativeToZ * sinYaw;
        double toYawZ = -relativeToX * sinYaw + relativeToZ * cosYaw;
        double toPitchedY = relativeToY * cosPitch - toYawZ * sinPitch;
        double toRolledX = toYawX * cosRoll - toPitchedY * sinRoll;
        double toRolledY = toYawX * sinRoll + toPitchedY * cosRoll;
        double toScreenX = canvas.getWidth() * 0.5 + toRolledX * viewScale;
        double toScreenY = canvas.getHeight() * 0.5 - toRolledY * viewScale;

        double segmentLength = Math.hypot(toScreenX - fromScreenX, toScreenY - fromScreenY);
        double sampleLength = sampleStep * viewScale;
        double maximumLength = Math.max(GRID_SEGMENT_MIN_LIMIT_PIXELS, sampleLength * GRID_SEGMENT_MAX_LENGTH_FACTOR);
        if (Double.isFinite(segmentLength) && segmentLength <= maximumLength) {
            strokeClippedLine(gc, fromScreenX, fromScreenY, toScreenX, toScreenY, GRID_CLIP_MARGIN_PIXELS);
        }
    }

    private void strokeClippedLine(GraphicsContext gc, double fromX, double fromY,
                                   double toX, double toY, double margin) {
        if (clipLineToRectangleInto(fromX, fromY, toX, toY,
                -margin, -margin, canvas.getWidth() + margin, canvas.getHeight() + margin, clippedLine)) {
            gc.strokeLine(clippedLine[0], clippedLine[1], clippedLine[2], clippedLine[3]);
        }
    }

    static double[] clipLineToRectangle(double fromX, double fromY, double toX, double toY,
                                        double minimumX, double minimumY, double maximumX, double maximumY) {
        double[] result = new double[4];
        return clipLineToRectangleInto(fromX, fromY, toX, toY,
                minimumX, minimumY, maximumX, maximumY, result) ? result : null;
    }

    private static boolean clipLineToRectangleInto(double fromX, double fromY, double toX, double toY,
                                                   double minimumX, double minimumY,
                                                   double maximumX, double maximumY, double[] result) {
        if (!Double.isFinite(fromX) || !Double.isFinite(fromY)
                || !Double.isFinite(toX) || !Double.isFinite(toY)) {
            return false;
        }
        double deltaX = toX - fromX;
        double deltaY = toY - fromY;
        double lower = 0.0;
        double upper = 1.0;
        for (int boundary = 0; boundary < 4; boundary++) {
            double direction;
            double distance;
            if (boundary == 0) {
                direction = -deltaX;
                distance = fromX - minimumX;
            } else if (boundary == 1) {
                direction = deltaX;
                distance = maximumX - fromX;
            } else if (boundary == 2) {
                direction = -deltaY;
                distance = fromY - minimumY;
            } else {
                direction = deltaY;
                distance = maximumY - fromY;
            }
            if (Math.abs(direction) < 1.0e-12) {
                if (distance < 0.0) {
                    return false;
                }
                continue;
            }
            double ratio = distance / direction;
            if (direction < 0.0) {
                lower = Math.max(lower, ratio);
            } else {
                upper = Math.min(upper, ratio);
            }
            if (lower > upper) {
                return false;
            }
        }
        result[0] = fromX + lower * deltaX;
        result[1] = fromY + lower * deltaY;
        result[2] = fromX + upper * deltaX;
        result[3] = fromY + upper * deltaY;
        return true;
    }

    private GridProjection gridProjection() {
        return new GridProjection(
                viewCenterX, viewCenterY, viewCenterZ,
                Math.cos(cameraYaw), Math.sin(cameraYaw),
                Math.cos(cameraPitch), Math.sin(cameraPitch),
                Math.cos(cameraRoll), Math.sin(cameraRoll));
    }

    private double[] projectPoint(double x, double y, double z) {
        return projectWorldToScreen(
                x, y, z,
                viewCenterX, viewCenterY, viewCenterZ,
                cameraYaw, cameraPitch, cameraRoll,
                viewScale, canvas.getWidth(), canvas.getHeight());
    }

    private void projectPointInto(double x, double y, double z,
                                  double[] rotated, double[] projected) {
        rotateWorldToViewInto(
                x - viewCenterX, y - viewCenterY, z - viewCenterZ,
                cameraYaw, cameraPitch, cameraRoll, rotated);
        projected[0] = canvas.getWidth() * 0.5 + rotated[0] * viewScale;
        projected[1] = canvas.getHeight() * 0.5 - rotated[1] * viewScale;
    }

    static double[] projectWorldToScreen(double x, double y, double z,
                                         double centerX, double centerY, double centerZ,
                                         double yaw, double pitch, double roll,
                                         double scale, double width, double height) {
        double[] rotated = rotateWorldToView(x - centerX, y - centerY, z - centerZ, yaw, pitch, roll);
        return new double[]{width * 0.5 + rotated[0] * scale, height * 0.5 - rotated[1] * scale};
    }

    static double[] rotateWorldToView(double x, double y, double z,
                                      double yaw, double pitch, double roll) {
        double[] result = new double[3];
        rotateWorldToViewInto(x, y, z, yaw, pitch, roll, result);
        return result;
    }

    private static void rotateWorldToViewInto(double x, double y, double z,
                                              double yaw, double pitch, double roll,
                                              double[] result) {
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);
        double yawX = x * cosYaw + z * sinYaw;
        double yawZ = -x * sinYaw + z * cosYaw;
        double pitchedY = y * cosPitch - yawZ * sinPitch;
        double viewZ = y * sinPitch + yawZ * cosPitch;
        double rolledX = yawX * cosRoll - pitchedY * sinRoll;
        double rolledY = yawX * sinRoll + pitchedY * cosRoll;
        result[0] = rolledX;
        result[1] = rolledY;
        result[2] = viewZ;
    }

    static double[] rotateViewToWorld(double x, double y, double z,
                                      double yaw, double pitch, double roll) {
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);
        double unrolledX = x * cosRoll + y * sinRoll;
        double unrolledY = -x * sinRoll + y * cosRoll;
        double unpitchedY = unrolledY * cosPitch + z * sinPitch;
        double unpitchedZ = -unrolledY * sinPitch + z * cosPitch;
        double worldX = unrolledX * cosYaw - unpitchedZ * sinYaw;
        double worldZ = unrolledX * sinYaw + unpitchedZ * cosYaw;
        return new double[]{worldX, unpitchedY, worldZ};
    }

    private double[] worldToView(double x, double y, double z) {
        return rotateWorldToView(
                x - viewCenterX, y - viewCenterY, z - viewCenterZ,
                cameraYaw, cameraPitch, cameraRoll);
    }

    private void worldToViewInto(double x, double y, double z, double[] result) {
        rotateWorldToViewInto(
                x - viewCenterX, y - viewCenterY, z - viewCenterZ,
                cameraYaw, cameraPitch, cameraRoll, result);
    }

    private double[] screenToWorldAtViewDepth(double screenX, double screenY,
                                               double viewDepth, double scale) {
        return unprojectScreenAtViewDepth(
                screenX, screenY, viewDepth,
                viewCenterX, viewCenterY, viewCenterZ,
                cameraYaw, cameraPitch, cameraRoll,
                scale, canvas.getWidth(), canvas.getHeight());
    }

    static double[] unprojectScreenAtViewDepth(double screenX, double screenY, double viewDepth,
                                               double centerX, double centerY, double centerZ,
                                               double yaw, double pitch, double roll,
                                               double scale, double width, double height) {
        double viewX = (screenX - width * 0.5) / scale;
        double viewY = (height * 0.5 - screenY) / scale;
        double[] relativeWorld = rotateViewToWorld(viewX, viewY, viewDepth, yaw, pitch, roll);
        return new double[]{centerX + relativeWorld[0], centerY + relativeWorld[1], centerZ + relativeWorld[2]};
    }

    private double[] screenToWorldOnPlane(double screenX, double screenY, double planeZ,
                                          double width, double height) {
        double viewX = (screenX - width * 0.5) / viewScale;
        double viewY = (height * 0.5 - screenY) / viewScale;
        double[] atZeroDepth = rotateViewToWorld(
                viewX, viewY, 0.0, cameraYaw, cameraPitch, cameraRoll);
        double[] viewDepthDirection = rotateViewToWorld(
                0.0, 0.0, 1.0, cameraYaw, cameraPitch, cameraRoll);
        double requiredDepth = 0.0;
        if (Math.abs(viewDepthDirection[2]) >= PLANE_INTERSECTION_EPSILON) {
            requiredDepth = (planeZ - viewCenterZ - atZeroDepth[2]) / viewDepthDirection[2];
        }
        double maximumDepth = Math.max(
                Math.max(width, height) / viewScale * GRID_MAX_WORLD_SPAN_FACTOR,
                SPACE_BEND_LIMIT * 4.0);
        requiredDepth = Math.clamp(requiredDepth, -maximumDepth, maximumDepth);
        double[] relativeWorld = rotateViewToWorld(
                viewX, viewY, requiredDepth, cameraYaw, cameraPitch, cameraRoll);
        return new double[]{viewCenterX + relativeWorld[0], viewCenterY + relativeWorld[1], planeZ};
    }

    private void drawRotationIndicator(GraphicsContext gc) {
        double centerX = canvas.getWidth() - 92.0;
        double centerY = 76.0;
        double axisLength = 34.0;

        gc.setLineDashes();
        gc.setFill(Color.rgb(5, 8, 18, 0.62));
        gc.fillRoundRect(centerX - 78.0, centerY - 58.0, 156.0, 116.0, 8.0, 8.0);
        gc.setStroke(Color.rgb(140, 155, 180, 0.36));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(centerX - 78.0, centerY - 58.0, 156.0, 116.0, 8.0, 8.0);

        drawRotationIndicatorBodies(gc, centerX, centerY);
        drawRotationAxis(gc, centerX, centerY, axisLength,
                rotateWorldToView(1.0, 0.0, 0.0, cameraYaw, cameraPitch, cameraRoll),
                Color.CORNFLOWERBLUE, "X");
        drawRotationAxis(gc, centerX, centerY, axisLength,
                rotateWorldToView(0.0, 1.0, 0.0, cameraYaw, cameraPitch, cameraRoll),
                Color.LIGHTGREEN, "Y");
        drawRotationAxis(gc, centerX, centerY, axisLength,
                rotateWorldToView(0.0, 0.0, 1.0, cameraYaw, cameraPitch, cameraRoll),
                Color.SALMON, "Z");

        gc.setFill(Color.rgb(220, 225, 235, 0.9));
        gc.setFont(Font.font("Monospaced", 11));
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.setTextBaseline(VPos.BOTTOM);
        gc.fillText(String.format("X %.1f  Y %.1f  Z %.1f deg",
                        Math.toDegrees(cameraPitch), Math.toDegrees(cameraYaw), Math.toDegrees(cameraRoll)),
                centerX + 72.0, centerY - 42.0);
        gc.setLineWidth(1.0);
    }

    private void drawRotationAxis(GraphicsContext gc, double centerX, double centerY, double length,
                                  double[] axis, Color color, String label) {
        double endX = centerX + axis[0] * length;
        double endY = centerY - axis[1] * length;
        gc.setStroke(color);
        gc.setFill(color);
        gc.setLineWidth(1.6);
        gc.strokeLine(centerX, centerY, endX, endY);
        gc.fillOval(endX - 2.5, endY - 2.5, 5.0, 5.0);
        gc.fillText(label, endX + 4.0, endY + 4.0);
    }

    private void drawRotationIndicatorBodies(GraphicsContext gc, double centerX, double centerY) {
        if (bodyCount == 0) {
            return;
        }
        double worldRadius = Math.max(0.000001,
                Math.hypot(canvas.getWidth(), canvas.getHeight()) * 0.5 / viewScale);
        double pixelsPerWorldUnit = Math.min(66.0, 40.0) / worldRadius;
        for (int i = 0; i < bodyCount; i++) {
            indicatorBodyOrder[i] = i;
            worldToViewInto(posX.get(i), posY.get(i), posZ.get(i), indicatorViewPositions[i]);
        }
        for (int i = 1; i < bodyCount; i++) {
            int bodyIndex = indicatorBodyOrder[i];
            int insertion = i;
            while (insertion > 0
                    && compareIndicatorDepth(bodyIndex, indicatorBodyOrder[insertion - 1], indicatorViewPositions) < 0) {
                indicatorBodyOrder[insertion] = indicatorBodyOrder[insertion - 1];
                insertion--;
            }
            indicatorBodyOrder[insertion] = bodyIndex;
        }

        for (int orderIndex = 0; orderIndex < bodyCount; orderIndex++) {
            int bodyIndex = indicatorBodyOrder[orderIndex];
            double[] position = indicatorViewPositions[bodyIndex];
            double normalizedDepth = Math.clamp(position[2] / worldRadius, -1.0, 1.0);
            double depthCue = (normalizedDepth + 1.0) * 0.5;
            double radius = ROTATION_INDICATOR_BODY_RADIUS * (0.72 + depthCue * 0.38);
            double dotX = Math.clamp(centerX + position[0] * pixelsPerWorldUnit,
                    centerX - 68.0 + radius, centerX + 68.0 - radius);
            double dotY = Math.clamp(centerY - position[1] * pixelsPerWorldUnit,
                    centerY - 40.0 + radius, centerY + 46.0 - radius);
            Color bodyColor = colors[bodyIndex] == null ? Color.WHITE : colors[bodyIndex];
            gc.setFill(bodyColor.deriveColor(0.0, 1.0, 1.0, 0.48 + depthCue * 0.48));
            gc.fillOval(dotX - radius, dotY - radius, radius * 2.0, radius * 2.0);
            gc.setStroke(Color.rgb(235, 240, 250, 0.35 + depthCue * 0.45));
            gc.setLineWidth(0.65);
            gc.strokeOval(dotX - radius, dotY - radius, radius * 2.0, radius * 2.0);
        }
    }

    private static int compareIndicatorDepth(int left, int right, double[][] positions) {
        int depthOrder = Double.compare(positions[left][2], positions[right][2]);
        return depthOrder != 0 ? depthOrder : Integer.compare(left, right);
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

    @Override
    public void stop() {
        if (simulationTimer != null) {
            simulationTimer.stop();
            simulationTimer = null;
        }
        if (dashboardDrawerTimeline != null) {
            dashboardDrawerTimeline.stop();
            dashboardDrawerTimeline = null;
        }
        hidePopover(guidePopover);
        hidePopover(unitDescriptionPopover);
        hidePopover(unitCalibrationPopover);
        closeExecutionPlan();
    }

    private static void hidePopover(PopOver popover) {
        if (popover != null) {
            popover.hide();
        }
    }

    static void main(String[] args) {
        launch(args);
    }
}
