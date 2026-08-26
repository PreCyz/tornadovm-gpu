package pawg.nbody;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

public class GravityGPU extends Application {

    private static final int CANVAS_WIDTH = 1250;
    private static final int SIDEBAR_WIDTH = 430;
    private static final int HEIGHT = 880;
    private static final int MAX_BODIES = 1024;

    private static final float SPEED_FACTOR = 0.1f;
    private static final float G = 1000.0f * (SPEED_FACTOR * SPEED_FACTOR);
    private static final float DT = 0.0012f;
    private static final float SUN_MASS = 332946.0f;
    private static final float PHYSICS_UNITS_PER_AU = 140.0f;
    private static final float ASTRONOMICAL_UNIT_KM = 149_597_870.7f;
    private static final float EARTH_ORBITAL_SPEED_KM_PER_SECOND = 29.78f;
    private static final float MAX_CREATED_BODY_RADIUS = 10.0f;
    private static final float WEAK_SUN_GRAVITY_THRESHOLD_METERS_PER_SECOND_SQUARED = 0.00001f;
    private static final float CENTER_COLLISION_EPSILON = 0.5f;
    private static final int GPU_SUB_STEPS = 12;
    private static final boolean FRAME_TIMING_ENABLED = Boolean.getBoolean("gravitygpu.timing");
    private static final double FRAME_TIMING_SLOW_MS = Double.parseDouble(System.getProperty("gravitygpu.timing.slow.ms", "24.0"));
    private static final int FRAME_TIMING_SUMMARY_FRAMES = Integer.getInteger("gravitygpu.timing.summary.frames", 300);
    private static final double AXIS_VALUE_CHARACTER_WIDTH = 7.2;
    private static final int DASHBOARD_METRIC_STRIDE = 7;
    private static final int DASHBOARD_DISTANCE_FROM_SUN_AU = 0;
    private static final int DASHBOARD_VELOCITY_X_KILOMETERS_PER_SECOND = 1;
    private static final int DASHBOARD_VELOCITY_Y_KILOMETERS_PER_SECOND = 2;
    private static final int DASHBOARD_VELOCITY_Z_KILOMETERS_PER_SECOND = 3;
    private static final int DASHBOARD_ACCELERATION_X_METERS_PER_SECOND_SQUARED = 4;
    private static final int DASHBOARD_ACCELERATION_Y_METERS_PER_SECOND_SQUARED = 5;
    private static final int DASHBOARD_ACCELERATION_Z_METERS_PER_SECOND_SQUARED = 6;
    private static final int TRAIL_CAPACITY = 180;
    private static final Color AXIS_X_COLOR = Color.rgb(255, 90, 90);
    private static final Color AXIS_Y_COLOR = Color.rgb(95, 235, 135);
    private static final Color AXIS_Z_COLOR = Color.rgb(95, 170, 255);
    private static final float MIN_PLANET_ORBIT_RADIUS = 55.0f;
    private static final float ORBIT_EDGE_PADDING = 50.0f;
    private static final float MERCURY_AU = 0.387f;
    private static final float VENUS_AU = 0.723f;
    private static final float EARTH_AU = 1.000f;
    private static final float MARS_AU = 1.524f;
    private static final float JUPITER_AU = 5.203f;
    private static final float SATURN_AU = 9.537f;
    private static final float URANUS_AU = 19.191f;
    private static final float NEPTUNE_AU = 30.070f;
    private static final float MERCURY_ECCENTRICITY = 0.2056f;
    private static final float VENUS_ECCENTRICITY = 0.0068f;
    private static final float EARTH_ECCENTRICITY = 0.0167f;
    private static final float MARS_ECCENTRICITY = 0.0934f;
    private static final float JUPITER_ECCENTRICITY = 0.0489f;
    private static final float SATURN_ECCENTRICITY = 0.0565f;
    private static final float URANUS_ECCENTRICITY = 0.0472f;
    private static final float NEPTUNE_ECCENTRICITY = 0.0086f;
    private static final float HABITABLE_ZONE_INNER_AU = 0.95f;
    private static final float HABITABLE_ZONE_OUTER_AU = 1.37f;
    private static final float HABITABLE_ZONE_MIN_SCALE = 0.35f;
    private static final float HABITABLE_ZONE_MAX_SCALE = 3.0f;
    private static final float ASTEROID_BELT_INNER_AU = 2.1f;
    private static final float ASTEROID_BELT_OUTER_AU = 3.3f;
    private static final int ASTEROID_COUNT = 180;
    private static final float[] STABLE_ORBIT_PHASES = {
            0.10f, 1.65f, 3.15f, 4.85f,
            0.75f, 2.85f, 4.95f, 5.65f
    };

    private final FloatArray posX = new FloatArray(MAX_BODIES);
    private final FloatArray posY = new FloatArray(MAX_BODIES);
    private final FloatArray posZ = new FloatArray(MAX_BODIES);
    private final FloatArray velX = new FloatArray(MAX_BODIES);
    private final FloatArray velY = new FloatArray(MAX_BODIES);
    private final FloatArray velZ = new FloatArray(MAX_BODIES);
    private final FloatArray nextPosX = new FloatArray(MAX_BODIES);
    private final FloatArray nextPosY = new FloatArray(MAX_BODIES);
    private final FloatArray nextPosZ = new FloatArray(MAX_BODIES);
    private final FloatArray nextVelX = new FloatArray(MAX_BODIES);
    private final FloatArray nextVelY = new FloatArray(MAX_BODIES);
    private final FloatArray nextVelZ = new FloatArray(MAX_BODIES);
    private final FloatArray accX = new FloatArray(MAX_BODIES);
    private final FloatArray accY = new FloatArray(MAX_BODIES);
    private final FloatArray accZ = new FloatArray(MAX_BODIES);
    private final FloatArray nextAccX = new FloatArray(MAX_BODIES);
    private final FloatArray nextAccY = new FloatArray(MAX_BODIES);
    private final FloatArray nextAccZ = new FloatArray(MAX_BODIES);
    private final FloatArray mass = new FloatArray(MAX_BODIES);
    private final FloatArray radius = new FloatArray(MAX_BODIES);
    private final FloatArray dashboardSpeed = new FloatArray(MAX_BODIES);
    private final FloatArray dashboardAcceleration = new FloatArray(MAX_BODIES);
    private final FloatArray dashboardNearestDistance = new FloatArray(MAX_BODIES);
    private final FloatArray dashboardMetrics = new FloatArray(MAX_BODIES * DASHBOARD_METRIC_STRIDE);
    private final FloatArray trailX = new FloatArray(MAX_BODIES * TRAIL_CAPACITY);
    private final FloatArray trailY = new FloatArray(MAX_BODIES * TRAIL_CAPACITY);
    private final FloatArray trailZ = new FloatArray(MAX_BODIES * TRAIL_CAPACITY);
    private final FloatArray projectedTrailX = new FloatArray(MAX_BODIES * TRAIL_CAPACITY);
    private final FloatArray projectedTrailY = new FloatArray(MAX_BODIES * TRAIL_CAPACITY);
    private final IntArray trailStart = new IntArray(MAX_BODIES);
    private final IntArray trailSize = new IntArray(MAX_BODIES);
    private final FloatArray projectedScreenX = new FloatArray(MAX_BODIES);
    private final FloatArray projectedScreenY = new FloatArray(MAX_BODIES);
    private final FloatArray projectedDepthScale = new FloatArray(MAX_BODIES);
    private final IntArray activeState = new IntArray(MAX_BODIES);
    private final IntArray dashboardNearestIndex = new IntArray(MAX_BODIES);

    private final FloatArray physParams = new FloatArray(2);
    private final FloatArray dashboardParams = new FloatArray(3);
    private final FloatArray renderParams = new FloatArray(10);
    private final IntArray simulationState = new IntArray(1);

    private final String[] bodyNames = new String[MAX_BODIES];
    private final Color[] bodyColors = new Color[MAX_BODIES];
    private final Color[] bodyTrailColors = new Color[MAX_BODIES];
    private final Color[] bodyLabelColors = new Color[MAX_BODIES];
    private final Color[] bodyOrbitColors = new Color[MAX_BODIES];
    private final boolean[] editableMass = new boolean[MAX_BODIES];
    private final float[] orbitSemiMajorAu = new float[MAX_BODIES];
    private final float[] orbitEccentricity = new float[MAX_BODIES];
    private final HBox[] dashboardRows = new HBox[MAX_BODIES];
    private final Label[] dashboardLabels = new Label[MAX_BODIES];
    private final TextField[] positionXFields = new TextField[MAX_BODIES];
    private final TextField[] positionYFields = new TextField[MAX_BODIES];
    private final TextField[] positionZFields = new TextField[MAX_BODIES];
    private final TextField[] massFields = new TextField[MAX_BODIES];
    private final TextField[] velocityXFields = new TextField[MAX_BODIES];
    private final TextField[] velocityYFields = new TextField[MAX_BODIES];
    private final TextField[] velocityZFields = new TextField[MAX_BODIES];

    private int bodyCount = 0;

    private TornadoExecutionPlan executionPlan;
    private TornadoExecutionPlan trailProjectionPlan;

    private int customBodyCount = 0;
    private boolean showHabitableZone = false;
    private boolean showAsteroidBelt = false;
    private boolean showWeakSunGravity = false;
    private boolean alignPlanetsOnReset = false;
    private boolean showOrbitGuides = false;
    private boolean showTrails = false;
    private float cameraYaw = 0.0f;
    private float cameraPitch = 0.0f;
    private float dragStartX;
    private float dragStartY;
    private float dragStartYaw;
    private float dragStartPitch;
    private boolean trailsNeedProjection = false;
    private float projectedTrailYaw = Float.NaN;
    private float projectedTrailPitch = Float.NaN;
    private float cachedAxisYaw = Float.NaN;
    private float cachedAxisPitch = Float.NaN;
    private String cachedXAxisText = "";
    private String cachedYAxisText = "";
    private String cachedZAxisText = "";
    private RadialGradient cachedHabitableZoneGradient;
    private double cachedHabitableZoneInnerStop = Double.NaN;
    private double cachedHabitableZoneMiddleStop = Double.NaN;

    private final VBox dashboardList = new VBox(6);
    private Label elapsedTimeLabel;
    private double canvasWidth = CANVAS_WIDTH;
    private double canvasHeight = HEIGHT;
    private long simulationStartNanos = -1L;
    private long displayedElapsedSeconds = -1L;
    private int projectedBodyCount = 0;

    private record ScreenPoint(float x, float y, float depthScale) {
    }

    private record SpherePaint(RadialGradient gradient, Color rim) {
    }

    private static final class FrameTiming {
        private int frames;
        private long totalFrameNanos;
        private long totalSimulationNanos;
        private long totalDrawNanos;
        private long maxFrameNanos;
        private long maxSimulationNanos;
        private long maxDrawNanos;

        void record(int frameNumber, long uiNanos, long trailAppendNanos, long simulationNanos,
                    long collisionNanos, long projectionNanos, long trailProjectionNanos,
                    long dashboardNanos, long drawNanos, long frameNanos) {
            if (!FRAME_TIMING_ENABLED) {
                return;
            }

            frames++;
            totalFrameNanos += frameNanos;
            totalSimulationNanos += simulationNanos;
            totalDrawNanos += drawNanos;
            maxFrameNanos = Math.max(maxFrameNanos, frameNanos);
            maxSimulationNanos = Math.max(maxSimulationNanos, simulationNanos);
            maxDrawNanos = Math.max(maxDrawNanos, drawNanos);

            if (toMillis(frameNanos) >= FRAME_TIMING_SLOW_MS) {
                System.out.printf(
                        "GravityGPU slow frame %d total=%.3fms ui=%.3fms trailAppend=%.3fms sim=%.3fms collision=%.3fms projection=%.3fms trailProjection=%.3fms dashboard=%.3fms draw=%.3fms%n",
                        frameNumber,
                        toMillis(frameNanos),
                        toMillis(uiNanos),
                        toMillis(trailAppendNanos),
                        toMillis(simulationNanos),
                        toMillis(collisionNanos),
                        toMillis(projectionNanos),
                        toMillis(trailProjectionNanos),
                        toMillis(dashboardNanos),
                        toMillis(drawNanos)
                );
            }

            if (frames >= FRAME_TIMING_SUMMARY_FRAMES) {
                System.out.printf(
                        "GravityGPU timing summary frames=%d avgTotal=%.3fms maxTotal=%.3fms avgSim=%.3fms maxSim=%.3fms avgDraw=%.3fms maxDraw=%.3fms%n",
                        frames,
                        toMillis(totalFrameNanos) / frames,
                        toMillis(maxFrameNanos),
                        toMillis(totalSimulationNanos) / frames,
                        toMillis(maxSimulationNanos),
                        toMillis(totalDrawNanos) / frames,
                        toMillis(maxDrawNanos)
                );
                reset();
            }
        }

        private void reset() {
            frames = 0;
            totalFrameNanos = 0L;
            totalSimulationNanos = 0L;
            totalDrawNanos = 0L;
            maxFrameNanos = 0L;
            maxSimulationNanos = 0L;
            maxDrawNanos = 0L;
        }

        private static double toMillis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }

    private static String formatElapsedTime(long elapsedSeconds) {
        long days = elapsedSeconds / 86_400;
        long hours = (elapsedSeconds % 86_400) / 3_600;
        long minutes = (elapsedSeconds % 3_600) / 60;
        long seconds = elapsedSeconds % 60;

        if (days > 0) {
            return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);
        }
        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds);
        }
        return seconds + "s";
    }

    @Override
    public void start(Stage primaryStage) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        canvasWidth = Math.max(800.0, screenBounds.getWidth() - SIDEBAR_WIDTH);
        canvasHeight = screenBounds.getHeight();

        Canvas canvas = new Canvas(canvasWidth, canvasHeight);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        physParams.set(0, G);
        physParams.set(1, DT);
        dashboardParams.set(0, PHYSICS_UNITS_PER_AU);
        dashboardParams.set(1, speedToKilometersPerSecond(1.0f));
        dashboardParams.set(2, accelerationToMetersPerSecondSquared(1.0f));

        resetSystem();
        initTornadoPlanOnce();
        warmUpTornadoPlans();

        canvas.setOnMousePressed(event -> {
            dragStartX = (float) event.getX();
            dragStartY = (float) event.getY();
            dragStartYaw = cameraYaw;
            dragStartPitch = cameraPitch;
        });

        canvas.setOnMouseDragged(event -> {
            float dx = (float) event.getX() - dragStartX;
            float dy = (float) event.getY() - dragStartY;
            cameraYaw = dragStartYaw + dx * 0.006f;
            cameraPitch = Math.clamp(dragStartPitch - dy * 0.006f, -1.35f, 1.35f);
        });

        VBox sidebar = new VBox(10);
        sidebar.setStyle(String.format("-fx-background-color: #111118; -fx-padding: 15; -fx-min-width: %dpx; -fx-pref-width: %dpx; -fx-border-color: #333344; -fx-border-width: 0 0 0 1;", SIDEBAR_WIDTH, SIDEBAR_WIDTH));

        Label title = new Label("Dashboard");
        title.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-font-size: 13px;");

        Button btnReset = new Button("RESET (SPACE)");
        btnReset.setStyle("-fx-background-color: #222; -fx-text-fill: #ff4444; -fx-border-color: #ff4444; -fx-font-weight: bold; -fx-cursor: hand;");
        btnReset.setFocusTraversable(false);
        btnReset.setOnAction(_ -> resetSystem());

        Button btnAddBody = new Button("+");
        btnAddBody.setTooltip(new Tooltip("Add editable zero-mass body"));
        btnAddBody.setStyle("-fx-background-color: #1d2b24; -fx-text-fill: #00ff88; -fx-border-color: #00aa66; -fx-font-weight: bold; -fx-cursor: hand; -fx-min-width: 34px;");
        btnAddBody.setFocusTraversable(false);
        btnAddBody.setOnAction(_ -> addEditableBody());

        elapsedTimeLabel = new Label("Time: 0s");
        elapsedTimeLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-family: monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 0 0 0;");

        HBox resetRow = new HBox(12, btnReset, btnAddBody, elapsedTimeLabel);
        resetRow.setStyle("-fx-alignment: center-left;");

        CheckBox alignPlanetsCheckbox = new CheckBox("Align planets on reset");
        alignPlanetsCheckbox.setSelected(false);
        alignPlanetsCheckbox.setFocusTraversable(false);
        alignPlanetsCheckbox.setStyle("-fx-text-fill: #b8b8c8; -fx-font-family: monospace; -fx-font-size: 11px;");
        alignPlanetsCheckbox.selectedProperty().addListener((_, _, selected) -> alignPlanetsOnReset = selected);

        CheckBox orbitGuidesCheckbox = new CheckBox("Show orbits");
        orbitGuidesCheckbox.setSelected(false);
        orbitGuidesCheckbox.setFocusTraversable(false);
        orbitGuidesCheckbox.setStyle("-fx-text-fill: #9ecfff; -fx-font-family: monospace; -fx-font-size: 11px;");
        orbitGuidesCheckbox.selectedProperty().addListener((_, _, selected) -> showOrbitGuides = selected);

        CheckBox trailsCheckbox = new CheckBox("Show trails");
        trailsCheckbox.setSelected(false);
        trailsCheckbox.setFocusTraversable(false);
        trailsCheckbox.setStyle("-fx-text-fill: #b8e0ff; -fx-font-family: monospace; -fx-font-size: 11px;");
        trailsCheckbox.selectedProperty().addListener((_, _, selected) -> {
            showTrails = selected;
            if (!selected) {
                clearAllTrails();
            }
        });

        CheckBox habitableZoneCheckbox = new CheckBox("Show habitable zone");
        habitableZoneCheckbox.setSelected(false);
        habitableZoneCheckbox.setFocusTraversable(false);
        habitableZoneCheckbox.setStyle("-fx-text-fill: #ffd76a; -fx-font-family: monospace; -fx-font-size: 11px;");
        habitableZoneCheckbox.selectedProperty().addListener((_, _, selected) -> showHabitableZone = selected);

        CheckBox asteroidBeltCheckbox = new CheckBox("Show asteroid belt");
        asteroidBeltCheckbox.setSelected(false);
        asteroidBeltCheckbox.setFocusTraversable(false);
        asteroidBeltCheckbox.setStyle("-fx-text-fill: #b67a42; -fx-font-family: monospace; -fx-font-size: 11px;");
        asteroidBeltCheckbox.selectedProperty().addListener((_, _, selected) -> showAsteroidBelt = selected);

        CheckBox weakSunGravityCheckbox = new CheckBox("Show weak Sun gravity");
        weakSunGravityCheckbox.setSelected(false);
        weakSunGravityCheckbox.setFocusTraversable(false);
        weakSunGravityCheckbox.setStyle("-fx-text-fill: #7fcfff; -fx-font-family: monospace; -fx-font-size: 11px;");
        weakSunGravityCheckbox.selectedProperty().addListener((_, _, selected) -> showWeakSunGravity = selected);

        GridPane optionsGrid = new GridPane();
        optionsGrid.setHgap(14);
        optionsGrid.setVgap(6);
        ColumnConstraints firstColumn = new ColumnConstraints();
        firstColumn.setPercentWidth(50.0);
        ColumnConstraints secondColumn = new ColumnConstraints();
        secondColumn.setPercentWidth(50.0);
        optionsGrid.getColumnConstraints().addAll(firstColumn, secondColumn);
        optionsGrid.add(alignPlanetsCheckbox, 0, 0);
        optionsGrid.add(habitableZoneCheckbox, 1, 0);
        optionsGrid.add(trailsCheckbox, 0, 1);
        optionsGrid.add(weakSunGravityCheckbox, 1, 1);
        optionsGrid.add(orbitGuidesCheckbox, 0, 2);
        optionsGrid.add(asteroidBeltCheckbox, 1, 2);

        Label legend = new Label("""
                M = mass [M_Earth]
                R = body radius [px]
                V = speed [km/s eq]
                A = acceleration [m/s² eq]
                X/Y/Z = physical position [AU]
                Drag canvas = rotate 3D view
                Blue ring = weak Sun gravity boundary
                Nearest = closest body and distance [AU]""");
        legend.setStyle("-fx-text-fill: #b8b8c8; -fx-font-family: monospace; -fx-font-size: 11px; -fx-padding: 0 0 6 0;");

        sidebar.getChildren().addAll(title, resetRow, optionsGrid, legend, dashboardList);

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setRight(sidebar);

        Scene scene = new Scene(root, screenBounds.getWidth(), screenBounds.getHeight(), Color.BLACK);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE) {
                resetSystem();
            }
        });

        primaryStage.setTitle("GPU N-Body Simulator");
        NBodyStageIcons.addJupiterIcon(primaryStage);
        primaryStage.setScene(scene);
        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());
        primaryStage.setResizable(false);
        primaryStage.show();

        AnimationTimer timer = new AnimationTimer() {
            private int frameCounter = 0;
            private final FrameTiming frameTiming = new FrameTiming();

            @Override
            public void handle(long now) {
                long frameStartNanos = System.nanoTime();
                long stageStartNanos = frameStartNanos;
                if (simulationStartNanos < 0) {
                    simulationStartNanos = now;
                }
                updateElapsedTime(now);
                long uiNanos = System.nanoTime() - stageStartNanos;

                frameCounter++;
                stageStartNanos = System.nanoTime();
                if (showTrails && frameCounter % 2 == 0) {
                    for (int i = 0; i < bodyCount; i++) {
                        appendTrailPoint(i, posX.get(i), posY.get(i), posZ.get(i));
                    }
                }
                long trailAppendNanos = System.nanoTime() - stageStartNanos;

                stageStartNanos = System.nanoTime();
                updateSimulationState();
                updateRenderParams();
                executionPlan.execute();
                long simulationNanos = System.nanoTime() - stageStartNanos;

                stageStartNanos = System.nanoTime();
                detectAndResolveCollisionsOnCpu();
                long collisionNanos = System.nanoTime() - stageStartNanos;

                stageStartNanos = System.nanoTime();
                projectBodiesOnCpu();
                long projectionNanos = System.nanoTime() - stageStartNanos;

                stageStartNanos = System.nanoTime();
                if (shouldProjectTrails()) {
                    trailProjectionPlan.execute();
                    trailsNeedProjection = false;
                    projectedTrailYaw = cameraYaw;
                    projectedTrailPitch = cameraPitch;
                }
                long trailProjectionNanos = System.nanoTime() - stageStartNanos;

                stageStartNanos = System.nanoTime();
                if (GravityGpuFramePolicy.shouldUpdateDashboard(frameCounter)) {
                    computeDashboardMetricsOnCpu();
                    updateDashboard();
                }
                long dashboardNanos = System.nanoTime() - stageStartNanos;

                stageStartNanos = System.nanoTime();
                gc.setFill(Color.rgb(3, 3, 10, 0.35));
                gc.fillRect(0, 0, canvasWidth, canvasHeight);
                drawSolarBelts(gc, frameCounter);
                drawOrbitGuides(gc);

                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.BOTTOM);
                gc.setFont(Font.font("SansSerif", 11));
                int sunIndex = findSunIndex();
                float sunScreenX = sunIndex >= 0 ? projectedScreenX.get(sunIndex) : (float) (canvasWidth * 0.5);
                float sunScreenY = sunIndex >= 0 ? projectedScreenY.get(sunIndex) : (float) (canvasHeight * 0.5);

                for (int i = 0; i < projectedBodyCount; i++) {
                    float screenX = projectedScreenX.get(i);
                    float screenY = projectedScreenY.get(i);
                    float depthScale = projectedDepthScale.get(i);
                    float renderRadius = Math.max(1.5f, radius.get(i) * depthScale);
                    if (showTrails) {
                        gc.setStroke(bodyTrailColors[i]);
                        gc.setLineWidth(1.0);
                        int size = trailSize.get(i);
                        int start = trailStart.get(i);
                        for (int k = 0; k < size - 1; k++) {
                            int firstSlot = (start + k) % TRAIL_CAPACITY;
                            int secondSlot = (start + k + 1) % TRAIL_CAPACITY;
                            int firstIndex = trailIndex(i, firstSlot);
                            int secondIndex = trailIndex(i, secondSlot);
                            gc.strokeLine(projectedTrailX.get(firstIndex), projectedTrailY.get(firstIndex),
                                    projectedTrailX.get(secondIndex), projectedTrailY.get(secondIndex));
                        }
                    }

                    drawPlanetRings(gc, i, screenX, screenY, depthScale);

                    drawSphere(gc, screenX, screenY, renderRadius, bodyColors[i], sunScreenX - screenX, sunScreenY - screenY);

                    gc.setFill(bodyLabelColors[i]);
                    gc.fillText(bodyNames[i], screenX, screenY - renderRadius - 4);
                }

                drawAxisIndicator(gc);
                long drawNanos = System.nanoTime() - stageStartNanos;
                frameTiming.record(
                        frameCounter,
                        uiNanos,
                        trailAppendNanos,
                        simulationNanos,
                        collisionNanos,
                        projectionNanos,
                        trailProjectionNanos,
                        dashboardNanos,
                        drawNanos,
                        System.nanoTime() - frameStartNanos);
            }
        };
        timer.start();
    }

    private void drawSphere(GraphicsContext gc, float centerX, float centerY, float sphereRadius, Color baseColor, float lightDx, float lightDy) {
        SpherePaint paint = createSpherePaint(baseColor, lightDx, lightDy);
        double diameter = sphereRadius * 2.0;
        gc.setFill(paint.gradient());
        gc.fillOval(centerX - sphereRadius, centerY - sphereRadius, diameter, diameter);
        gc.setStroke(paint.rim());
        gc.setLineWidth(Math.max(0.6, sphereRadius * 0.08));
        gc.strokeOval(centerX - sphereRadius, centerY - sphereRadius, diameter, diameter);
    }

    private SpherePaint createSpherePaint(Color baseColor, float lightDx, float lightDy) {
        Color highlight = baseColor.deriveColor(0, 0.55, 1.65, 1.0);
        Color midtone = baseColor.deriveColor(0, 1.0, 1.05, 1.0);
        Color shadow = baseColor.deriveColor(0, 1.15, 0.38, 1.0);
        Color rim = baseColor.deriveColor(0, 0.8, 1.25, 0.42);
        double lightLength = Math.sqrt(lightDx * lightDx + lightDy * lightDy);
        double focusAngle = lightLength <= 0.0001 ? -135.0 : Math.toDegrees(Math.atan2(lightDy, lightDx));
        double focusDistance = lightLength <= 0.0001 ? 0.28 : 0.42;

        return new SpherePaint(new RadialGradient(
                focusAngle, focusDistance,
                0.5, 0.5,
                0.74,
                true,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, highlight),
                new Stop(0.42, midtone),
                new Stop(1.0, shadow)
        ), rim);
    }

    private void drawAxisIndicator(GraphicsContext gc) {
        double centerX = canvasWidth - 98.0;
        double centerY = 78.0;
        double axisLength = 34.0;

        gc.setLineDashes();
        gc.setFill(Color.rgb(5, 8, 18, 0.55));
        gc.fillRoundRect(centerX - 78.0, centerY - 58.0, 156.0, 116.0, 8.0, 8.0);
        gc.setStroke(Color.rgb(140, 155, 180, 0.36));
        gc.setLineWidth(0.8);
        gc.strokeRoundRect(centerX - 78.0, centerY - 58.0, 156.0, 116.0, 8.0, 8.0);

        ScreenPoint xAxis = projectUnitAxis(1.0f, 0.0f, 0.0f);
        ScreenPoint yAxis = projectUnitAxis(0.0f, 1.0f, 0.0f);
        ScreenPoint zAxis = projectUnitAxis(0.0f, 0.0f, 1.0f);
        gc.setTextBaseline(VPos.BOTTOM);
        gc.setFont(Font.font("Monospaced", 11));
        drawAxisValues(gc, centerX + 78.0, centerY - 62.0, xAxis, yAxis, zAxis);

        drawAxisLine(gc, centerX, centerY, axisLength, xAxis, AXIS_X_COLOR, "X");
        drawAxisLine(gc, centerX, centerY, axisLength, yAxis, AXIS_Y_COLOR, "Y");
        drawAxisLine(gc, centerX, centerY, axisLength, zAxis, AXIS_Z_COLOR, "Z");

        gc.setFill(Color.rgb(220, 225, 235, 0.9));
        gc.fillOval(centerX - 2.5, centerY - 2.5, 5.0, 5.0);
    }

    private void drawAxisValues(GraphicsContext gc, double rightX, double y, ScreenPoint xAxis, ScreenPoint yAxis, ScreenPoint zAxis) {
        updateAxisValueTextCache(xAxis, yAxis, zAxis);
        double spaceWidth = axisTextWidth(" ");

        gc.setTextAlign(TextAlignment.RIGHT);
        double x = rightX;
        x = drawAxisValueRight(gc, x, y, cachedZAxisText, AXIS_Z_COLOR) - spaceWidth;
        x = drawAxisValueRight(gc, x, y, cachedYAxisText, AXIS_Y_COLOR) - spaceWidth;
        drawAxisValueRight(gc, x, y, cachedXAxisText, AXIS_X_COLOR);
    }

    private void updateAxisValueTextCache(ScreenPoint xAxis, ScreenPoint yAxis, ScreenPoint zAxis) {
        if (Float.isNaN(cachedAxisYaw)
                || Float.isNaN(cachedAxisPitch)
                || Math.abs(cameraYaw - cachedAxisYaw) > 0.0001f
                || Math.abs(cameraPitch - cachedAxisPitch) > 0.0001f) {
            cachedXAxisText = axisValueText("X", xAxis);
            cachedYAxisText = axisValueText("Y", yAxis);
            cachedZAxisText = axisValueText("Z", zAxis);
            cachedAxisYaw = cameraYaw;
            cachedAxisPitch = cameraPitch;
        }
    }

    private String axisValueText(String label, ScreenPoint axisPoint) {
        return String.format("%s %.1f,%.1f,%.1f", label, axisPoint.x(), axisPoint.y(), axisPoint.depthScale());
    }

    private double drawAxisValueRight(GraphicsContext gc, double rightX, double y, String text, Color color) {
        gc.setFill(color);
        gc.fillText(text, rightX, y);
        return rightX - axisTextWidth(text);
    }

    private double axisTextWidth(String text) {
        return text.length() * AXIS_VALUE_CHARACTER_WIDTH;
    }

    private void drawAxisLine(GraphicsContext gc, double centerX, double centerY, double axisLength,
                              ScreenPoint axisPoint, Color color, String label) {
        double endX = centerX + axisPoint.x() * axisLength;
        double endY = centerY + axisPoint.y() * axisLength;
        double arrowScale = Math.max(0.7, axisPoint.depthScale());
        double arrowSize = 5.0 * arrowScale;

        gc.setStroke(color);
        gc.setLineWidth(2.0 * arrowScale);
        gc.strokeLine(centerX, centerY, endX, endY);

        double angle = Math.atan2(endY - centerY, endX - centerX);
        double leftX = endX - Math.cos(angle - Math.PI / 6.0) * arrowSize;
        double leftY = endY - Math.sin(angle - Math.PI / 6.0) * arrowSize;
        double rightX = endX - Math.cos(angle + Math.PI / 6.0) * arrowSize;
        double rightY = endY - Math.sin(angle + Math.PI / 6.0) * arrowSize;
        gc.strokeLine(endX, endY, leftX, leftY);
        gc.strokeLine(endX, endY, rightX, rightY);

        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFill(color);
        gc.fillText(label, endX + Math.cos(angle) * 10.0, endY + Math.sin(angle) * 10.0);
    }

    private ScreenPoint projectUnitAxis(float axisX, float axisY, float axisZ) {
        float cosYaw = (float) Math.cos(cameraYaw);
        float sinYaw = (float) Math.sin(cameraYaw);
        float cosPitch = (float) Math.cos(cameraPitch);
        float sinPitch = (float) Math.sin(cameraPitch);

        float yawX = axisX * cosYaw + axisZ * sinYaw;
        float yawZ = -axisX * sinYaw + axisZ * cosYaw;
        float viewX = yawX;
        float viewY = axisY * cosPitch - yawZ * sinPitch;
        float viewZ = axisY * sinPitch + yawZ * cosPitch;
        float depth = 1.0f + viewZ * 0.22f;
        return new ScreenPoint(viewX, viewY, depth);
    }

    private void drawOrbitGuides(GraphicsContext gc) {
        if (!showOrbitGuides) {
            return;
        }

        int sunIndex = findSunIndex();
        if (sunIndex < 0) {
            return;
        }

        gc.setLineWidth(0.9);
        gc.setLineDashes(8.0, 7.0);

        for (int i = 0; i < bodyCount; i++) {
            if (i == sunIndex || editableMass[i] || bodyNames[i] == null) {
                continue;
            }

            if (orbitSemiMajorAu[i] <= 0.0f) {
                continue;
            }

            Color orbitColor = bodyColors[i] == null ? Color.WHITE : bodyColors[i];
            gc.setStroke(bodyOrbitColors[i] == null ? orbitColor.deriveColor(0, 0.8, 1.3, 0.28) : bodyOrbitColors[i]);
            drawOsculatingOrbitGuide(gc, sunIndex, i);
        }

        gc.setLineDashes();
    }

    private void drawOsculatingOrbitGuide(GraphicsContext gc, int sunIndex, int bodyIndex) {
        final int segments = 192;
        double rx = posX.get(bodyIndex) - posX.get(sunIndex);
        double ry = posY.get(bodyIndex) - posY.get(sunIndex);
        double rz = posZ.get(bodyIndex) - posZ.get(sunIndex);
        double vx = velX.get(bodyIndex) - velX.get(sunIndex);
        double vy = velY.get(bodyIndex) - velY.get(sunIndex);
        double vz = velZ.get(bodyIndex) - velZ.get(sunIndex);
        double mu = G * (mass.get(sunIndex) + mass.get(bodyIndex));

        double rMagnitude = Math.sqrt(rx * rx + ry * ry + rz * rz);
        double vMagnitudeSquared = vx * vx + vy * vy + vz * vz;
        if (rMagnitude <= 0.000001 || mu <= 0.0) {
            return;
        }

        double hx = ry * vz - rz * vy;
        double hy = rz * vx - rx * vz;
        double hz = rx * vy - ry * vx;
        double hMagnitude = Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (hMagnitude <= 0.000001) {
            return;
        }

        double energy = vMagnitudeSquared * 0.5 - mu / rMagnitude;
        if (energy >= 0.0) {
            return;
        }

        double vxhX = vy * hz - vz * hy;
        double vxhY = vz * hx - vx * hz;
        double vxhZ = vx * hy - vy * hx;
        double eccentricityX = vxhX / mu - rx / rMagnitude;
        double eccentricityY = vxhY / mu - ry / rMagnitude;
        double eccentricityZ = vxhZ / mu - rz / rMagnitude;
        double eccentricity = Math.sqrt(eccentricityX * eccentricityX + eccentricityY * eccentricityY + eccentricityZ * eccentricityZ);
        if (eccentricity >= 0.98) {
            return;
        }

        double semiLatusRectum = hMagnitude * hMagnitude / mu;
        if (semiLatusRectum <= 0.0) {
            return;
        }

        double eHatX;
        double eHatY;
        double eHatZ;
        if (eccentricity > 0.0001) {
            eHatX = eccentricityX / eccentricity;
            eHatY = eccentricityY / eccentricity;
            eHatZ = eccentricityZ / eccentricity;
        } else {
            eHatX = rx / rMagnitude;
            eHatY = ry / rMagnitude;
            eHatZ = rz / rMagnitude;
        }

        double hHatX = hx / hMagnitude;
        double hHatY = hy / hMagnitude;
        double hHatZ = hz / hMagnitude;
        double qHatX = hHatY * eHatZ - hHatZ * eHatY;
        double qHatY = hHatZ * eHatX - hHatX * eHatZ;
        double qHatZ = hHatX * eHatY - hHatY * eHatX;
        double qMagnitude = Math.sqrt(qHatX * qHatX + qHatY * qHatY + qHatZ * qHatZ);
        if (qMagnitude <= 0.000001) {
            return;
        }
        qHatX /= qMagnitude;
        qHatY /= qMagnitude;
        qHatZ /= qMagnitude;

        gc.beginPath();
        for (int segment = 0; segment <= segments; segment++) {
            double trueAnomaly = Math.PI * 2.0 * segment / segments;
            double cosAnomaly = Math.cos(trueAnomaly);
            double sinAnomaly = Math.sin(trueAnomaly);
            double denominator = 1.0 + eccentricity * cosAnomaly;
            if (denominator <= 0.000001) {
                continue;
            }

            double radiusFromFocus = semiLatusRectum / denominator;
            float physicsX = (float) (posX.get(sunIndex) + radiusFromFocus * (cosAnomaly * eHatX + sinAnomaly * qHatX));
            float physicsY = (float) (posY.get(sunIndex) + radiusFromFocus * (cosAnomaly * eHatY + sinAnomaly * qHatY));
            float physicsZ = (float) (posZ.get(sunIndex) + radiusFromFocus * (cosAnomaly * eHatZ + sinAnomaly * qHatZ));
            ScreenPoint point = projectPhysics(physicsX, physicsY, physicsZ);

            if (segment == 0) {
                gc.moveTo(point.x(), point.y());
            } else {
                gc.lineTo(point.x(), point.y());
            }
        }
        gc.stroke();
    }

    private void drawSolarBelts(GraphicsContext gc, int frameCounter) {
        if (!showHabitableZone && !showAsteroidBelt && !showWeakSunGravity) {
            return;
        }

        int sunIndex = findSunIndex();
        if (sunIndex < 0) {
            return;
        }

        float sunX = projectedScreenX.get(sunIndex);
        float sunY = projectedScreenY.get(sunIndex);
        float sunMass = mass.get(sunIndex);
        if (showHabitableZone) {
            drawHabitableZone(gc, sunX, sunY, sunMass);
        }
        if (showAsteroidBelt) {
            drawAsteroidBelt(gc, sunX, sunY, frameCounter);
        }
        if (showWeakSunGravity) {
            drawWeakSunGravityBoundary(gc, sunIndex);
        }
    }

    private int findSunIndex() {
        for (int i = 0; i < bodyCount; i++) {
            String name = bodyNames[i];
            if (name != null && name.startsWith("Sun")) {
                return i;
            }
        }

        return bodyCount > 0 ? 0 : -1;
    }

    private void drawHabitableZone(GraphicsContext gc, float sunX, float sunY, float sunMass) {
        double luminosityScale = habitableZoneScale(sunMass);
        double innerRadius = screenRadiusForPhysicsDistance(physicalRadiusForAu((float) (HABITABLE_ZONE_INNER_AU * luminosityScale)));
        double outerRadius = screenRadiusForPhysicsDistance(physicalRadiusForAu((float) (HABITABLE_ZONE_OUTER_AU * luminosityScale)));
        if (outerRadius <= innerRadius) {
            return;
        }

        double innerStop = Math.clamp(innerRadius / outerRadius, 0.0, 1.0);
        double middleStop = Math.clamp(((innerRadius + outerRadius) * 0.5) / outerRadius, innerStop, 1.0);

        double diameter = outerRadius * 2.0;
        gc.setFill(habitableZoneGradient(innerStop, middleStop));
        gc.fillOval(sunX - outerRadius, sunY - outerRadius, diameter, diameter);
    }

    private RadialGradient habitableZoneGradient(double innerStop, double middleStop) {
        if (cachedHabitableZoneGradient == null
                || Math.abs(innerStop - cachedHabitableZoneInnerStop) > 0.0001
                || Math.abs(middleStop - cachedHabitableZoneMiddleStop) > 0.0001) {
            cachedHabitableZoneGradient = new RadialGradient(
                    0.0, 0.0,
                    0.5, 0.5,
                    0.5,
                    true,
                    CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.rgb(255, 205, 80, 0.0)),
                    new Stop(Math.max(0.0, innerStop - 0.015), Color.rgb(255, 205, 80, 0.0)),
                    new Stop(innerStop, Color.rgb(255, 210, 85, 0.025)),
                    new Stop(middleStop, Color.rgb(255, 220, 95, 0.12)),
                    new Stop(1.0, Color.rgb(255, 205, 80, 0.0))
            );
            cachedHabitableZoneInnerStop = innerStop;
            cachedHabitableZoneMiddleStop = middleStop;
        }
        return cachedHabitableZoneGradient;
    }

    private double habitableZoneScale(float sunMass) {
        double massRatio = Math.max(0.01, sunMass / SUN_MASS);
        double luminosityRatio = Math.pow(massRatio, 3.5);
        double radiusScale = Math.sqrt(luminosityRatio);
        return Math.clamp(radiusScale, HABITABLE_ZONE_MIN_SCALE, HABITABLE_ZONE_MAX_SCALE);
    }

    private void drawWeakSunGravityBoundary(GraphicsContext gc, int sunIndex) {
        float thresholdSimulationAcceleration = simulationAccelerationForMetersPerSecondSquared(WEAK_SUN_GRAVITY_THRESHOLD_METERS_PER_SECOND_SQUARED);
        if (thresholdSimulationAcceleration <= 0.0f) {
            return;
        }

        float sunMass = Math.max(0.0f, mass.get(sunIndex));
        if (sunMass <= 0.0f) {
            return;
        }

        float boundaryRadius = (float) Math.sqrt(G * sunMass / thresholdSimulationAcceleration);
        float sunPhysicsX = posX.get(sunIndex);
        float sunPhysicsY = posY.get(sunIndex);
        float sunPhysicsZ = posZ.get(sunIndex);
        float sunScreenX = projectedScreenX.get(sunIndex);
        float sunScreenY = projectedScreenY.get(sunIndex);
        ScreenPoint projectedSunPoint = projectPhysics(sunPhysicsX, sunPhysicsY, sunPhysicsZ);
        final int segments = 180;

        gc.setLineDashes(10.0, 8.0);
        gc.setLineWidth(1.1);
        gc.setStroke(Color.rgb(100, 190, 255, 0.34));
        double previousX = 0.0;
        double previousY = 0.0;
        for (int segment = 0; segment <= segments; segment++) {
            double angle = Math.PI * 2.0 * segment / segments;
            ScreenPoint point = projectPhysics(
                    (float) (sunPhysicsX + boundaryRadius * Math.cos(angle)),
                    (float) (sunPhysicsY + boundaryRadius * Math.sin(angle)),
                    sunPhysicsZ
            );
            double centeredX = sunScreenX + point.x() - projectedSunPoint.x();
            double centeredY = sunScreenY + point.y() - projectedSunPoint.y();
            if (segment > 0) {
                gc.strokeLine(previousX, previousY, centeredX, centeredY);
            }
            previousX = centeredX;
            previousY = centeredY;
        }
        gc.setLineDashes();

        ScreenPoint labelPoint = projectPhysics(sunPhysicsX + boundaryRadius, sunPhysicsY, sunPhysicsZ);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFill(Color.rgb(130, 210, 255, 0.82));
        gc.fillText(String.format("< %.5f m/s2", WEAK_SUN_GRAVITY_THRESHOLD_METERS_PER_SECOND_SQUARED),
                sunScreenX + labelPoint.x() - projectedSunPoint.x() + 8.0,
                sunScreenY + labelPoint.y() - projectedSunPoint.y());
    }

    private ScreenPoint projectPhysics(float physicsX, float physicsY, float physicsZ) {
        float cosYaw = (float) Math.cos(cameraYaw);
        float sinYaw = (float) Math.sin(cameraYaw);
        float cosPitch = (float) Math.cos(cameraPitch);
        float sinPitch = (float) Math.sin(cameraPitch);

        float yawX = physicsX * cosYaw + physicsZ * sinYaw;
        float yawZ = -physicsX * sinYaw + physicsZ * cosYaw;
        float viewX = yawX;
        float viewY = physicsY * cosPitch - yawZ * sinPitch;
        float viewZ = physicsY * sinPitch + yawZ * cosPitch;
        float physicsDistance = (float) Math.sqrt(physicsX * physicsX + physicsY * physicsY + physicsZ * physicsZ);

        if (physicsDistance <= 0.000001f) {
            return new ScreenPoint((float) (canvasWidth / 2.0), (float) (canvasHeight / 2.0), 1.0f);
        }

        float projectedDistance = (float) Math.sqrt(viewX * viewX + viewY * viewY);
        if (projectedDistance <= 0.000001f) {
            return new ScreenPoint((float) (canvasWidth / 2.0), (float) (canvasHeight / 2.0), depthScale(viewZ));
        }

        float screenDistance = screenRadiusForPhysicsDistance(physicsDistance);
        float scale = depthScale(viewZ);
        return new ScreenPoint(
                (float) (canvasWidth / 2.0 + (viewX / projectedDistance) * screenDistance * scale),
                (float) (canvasHeight / 2.0 + (viewY / projectedDistance) * screenDistance * scale),
                scale
        );
    }

    private float depthScale(float viewZ) {
        return Math.clamp(1.0f + viewZ / (PHYSICS_UNITS_PER_AU * 55.0f), 0.55f, 1.45f);
    }

    private float screenRadiusForPhysicsDistance(float physicsDistance) {
        float au = physicsDistanceToAu(physicsDistance);
        if (au <= MERCURY_AU) {
            return MIN_PLANET_ORBIT_RADIUS * au / MERCURY_AU;
        }

        return orbitRadiusForAu(au);
    }

    private float physicsRadiusForScreenDistance(float screenDistance) {
        if (screenDistance <= MIN_PLANET_ORBIT_RADIUS) {
            return physicalRadiusForAu(MERCURY_AU) * screenDistance / MIN_PLANET_ORBIT_RADIUS;
        }

        double maxOrbitRadius = Math.max(MIN_PLANET_ORBIT_RADIUS + 1.0, Math.min(canvasWidth, canvasHeight) / 2.0 - ORBIT_EDGE_PADDING);
        double normalized = Math.max(0.0, (screenDistance - MIN_PLANET_ORBIT_RADIUS) / (maxOrbitRadius - MIN_PLANET_ORBIT_RADIUS));
        double minLog = Math.log(MERCURY_AU);
        double maxLog = Math.log(NEPTUNE_AU);
        return physicalRadiusForAu((float) Math.exp(minLog + normalized * (maxLog - minLog)));
    }

    private float physicsDistanceToAu(float physicsDistance) {
        return physicsDistance / PHYSICS_UNITS_PER_AU;
    }

    private int trailIndex(int bodyIndex, int trailSlot) {
        return bodyIndex * TRAIL_CAPACITY + trailSlot;
    }

    private boolean shouldProjectTrails() {
        return GravityGpuFramePolicy.shouldProjectTrails(
                showTrails,
                hasTrailPoints(),
                trailsNeedProjection,
                !Float.isNaN(projectedTrailYaw) && !Float.isNaN(projectedTrailPitch),
                cameraYaw,
                cameraPitch,
                projectedTrailYaw,
                projectedTrailPitch);
    }

    private boolean hasTrailPoints() {
        for (int i = 0; i < bodyCount; i++) {
            if (trailSize.get(i) > 0) {
                return true;
            }
        }
        return false;
    }

    private void appendTrailPoint(int bodyIndex, float x, float y, float z) {
        int size = trailSize.get(bodyIndex);
        int start = trailStart.get(bodyIndex);
        int writeSlot;
        if (size < TRAIL_CAPACITY) {
            writeSlot = size;
            trailSize.set(bodyIndex, size + 1);
        } else {
            writeSlot = start;
            trailStart.set(bodyIndex, (start + 1) % TRAIL_CAPACITY);
        }

        int index = trailIndex(bodyIndex, writeSlot);
        trailX.set(index, x);
        trailY.set(index, y);
        trailZ.set(index, z);
        ScreenPoint projectedPoint = projectPhysics(x, y, z);
        projectedTrailX.set(index, projectedPoint.x());
        projectedTrailY.set(index, projectedPoint.y());
        if (Float.isNaN(projectedTrailYaw) || Float.isNaN(projectedTrailPitch)) {
            projectedTrailYaw = cameraYaw;
            projectedTrailPitch = cameraPitch;
        }
    }

    private void clearTrail(int bodyIndex) {
        trailStart.set(bodyIndex, 0);
        trailSize.set(bodyIndex, 0);
        int baseIndex = bodyIndex * TRAIL_CAPACITY;
        for (int slot = 0; slot < TRAIL_CAPACITY; slot++) {
            int index = baseIndex + slot;
            trailX.set(index, 0.0f);
            trailY.set(index, 0.0f);
            trailZ.set(index, 0.0f);
            projectedTrailX.set(index, 0.0f);
            projectedTrailY.set(index, 0.0f);
        }
    }

    private void clearAllTrails() {
        for (int i = 0; i < MAX_BODIES; i++) {
            clearTrail(i);
        }
        projectedTrailYaw = Float.NaN;
        projectedTrailPitch = Float.NaN;
        trailsNeedProjection = false;
    }

    private void copyTrail(int source, int target) {
        trailStart.set(target, trailStart.get(source));
        trailSize.set(target, trailSize.get(source));
        int sourceBaseIndex = source * TRAIL_CAPACITY;
        int targetBaseIndex = target * TRAIL_CAPACITY;
        for (int slot = 0; slot < TRAIL_CAPACITY; slot++) {
            int sourceIndex = sourceBaseIndex + slot;
            int targetIndex = targetBaseIndex + slot;
            trailX.set(targetIndex, trailX.get(sourceIndex));
            trailY.set(targetIndex, trailY.get(sourceIndex));
            trailZ.set(targetIndex, trailZ.get(sourceIndex));
            projectedTrailX.set(targetIndex, projectedTrailX.get(sourceIndex));
            projectedTrailY.set(targetIndex, projectedTrailY.get(sourceIndex));
        }
    }

    private float dashboardMetric(int bodyIndex, int metricOffset) {
        return dashboardMetrics.get(bodyIndex * DASHBOARD_METRIC_STRIDE + metricOffset);
    }

    private void setDashboardMetric(int bodyIndex, int metricOffset, float value) {
        dashboardMetrics.set(bodyIndex * DASHBOARD_METRIC_STRIDE + metricOffset, value);
    }

    private void clearDashboardMetrics(int bodyIndex) {
        int baseIndex = bodyIndex * DASHBOARD_METRIC_STRIDE;
        for (int metricOffset = 0; metricOffset < DASHBOARD_METRIC_STRIDE; metricOffset++) {
            dashboardMetrics.set(baseIndex + metricOffset, 0.0f);
        }
    }

    private void computeDashboardMetricsOnCpu() {
        float velocityConversion = speedToKilometersPerSecond(1.0f);
        float accelerationConversion = accelerationToMetersPerSecondSquared(1.0f);
        float sunX = bodyCount > 0 ? posX.get(0) : 0.0f;
        float sunY = bodyCount > 0 ? posY.get(0) : 0.0f;
        float sunZ = bodyCount > 0 ? posZ.get(0) : 0.0f;

        for (int i = 0; i < bodyCount; i++) {
            dashboardNearestIndex.set(i, -1);
            dashboardNearestDistance.set(i, 0.0f);
            dashboardSpeed.set(i, 0.0f);
            dashboardAcceleration.set(i, 0.0f);
            clearDashboardMetrics(i);

            if (activeState.get(i) == 0) {
                continue;
            }

            float vxi = velX.get(i);
            float vyi = velY.get(i);
            float vzi = velZ.get(i);
            float axi = accX.get(i);
            float ayi = accY.get(i);
            float azi = accZ.get(i);

            dashboardSpeed.set(i, vectorLength(vxi, vyi, vzi) * velocityConversion);
            dashboardAcceleration.set(i, vectorLength(axi, ayi, azi) * accelerationConversion);
            setDashboardMetric(i, DASHBOARD_VELOCITY_X_KILOMETERS_PER_SECOND, vxi * velocityConversion);
            setDashboardMetric(i, DASHBOARD_VELOCITY_Y_KILOMETERS_PER_SECOND, vyi * velocityConversion);
            setDashboardMetric(i, DASHBOARD_VELOCITY_Z_KILOMETERS_PER_SECOND, vzi * velocityConversion);
            setDashboardMetric(i, DASHBOARD_ACCELERATION_X_METERS_PER_SECOND_SQUARED, axi * accelerationConversion);
            setDashboardMetric(i, DASHBOARD_ACCELERATION_Y_METERS_PER_SECOND_SQUARED, ayi * accelerationConversion);
            setDashboardMetric(i, DASHBOARD_ACCELERATION_Z_METERS_PER_SECOND_SQUARED, azi * accelerationConversion);

            float pxi = posX.get(i);
            float pyi = posY.get(i);
            float pzi = posZ.get(i);
            if (i != 0) {
                setDashboardMetric(i, DASHBOARD_DISTANCE_FROM_SUN_AU,
                        vectorLength(pxi - sunX, pyi - sunY, pzi - sunZ) / PHYSICS_UNITS_PER_AU);
            }

            int closestIndex = -1;
            float closestDistanceSq = Float.MAX_VALUE;
            for (int j = 0; j < bodyCount; j++) {
                if (i == j || activeState.get(j) == 0) {
                    continue;
                }

                float dx = posX.get(j) - pxi;
                float dy = posY.get(j) - pyi;
                float dz = posZ.get(j) - pzi;
                float distanceSq = dx * dx + dy * dy + dz * dz;
                if (distanceSq < closestDistanceSq) {
                    closestDistanceSq = distanceSq;
                    closestIndex = j;
                }
            }

            if (closestIndex >= 0) {
                dashboardNearestIndex.set(i, closestIndex);
                dashboardNearestDistance.set(i, (float) Math.sqrt(closestDistanceSq));
            }
        }
    }

    private void projectBodiesOnCpu() {
        projectedBodyCount = bodyCount;
        for (int i = 0; i < bodyCount; i++) {
            projectedScreenX.set(i, (float) (canvasWidth * 0.5));
            projectedScreenY.set(i, (float) (canvasHeight * 0.5));
            projectedDepthScale.set(i, 1.0f);
            if (activeState.get(i) == 0) {
                continue;
            }

            ScreenPoint point = projectPhysics(posX.get(i), posY.get(i), posZ.get(i));
            projectedScreenX.set(i, point.x());
            projectedScreenY.set(i, point.y());
            projectedDepthScale.set(i, point.depthScale());
        }
    }

    private float vectorLength(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private float speedToKilometersPerSecond(float simulationSpeed) {
        float earthSimulationSpeed = (float) Math.sqrt(G * (SUN_MASS + 1.0f) / physicalRadiusForAu(EARTH_AU));
        return simulationSpeed / earthSimulationSpeed * EARTH_ORBITAL_SPEED_KM_PER_SECOND;
    }

    private float kilometersPerSecondToSimulationSpeed(float kilometersPerSecond) {
        float earthSimulationSpeed = (float) Math.sqrt(G * (SUN_MASS + 1.0f) / physicalRadiusForAu(EARTH_AU));
        return kilometersPerSecond / EARTH_ORBITAL_SPEED_KM_PER_SECOND * earthSimulationSpeed;
    }

    private float accelerationToMetersPerSecondSquared(float simulationAcceleration) {
        float realSecondsPerSimulationSecond = realSecondsPerSimulationSecond();
        float kilometersPerSimulationUnit = ASTRONOMICAL_UNIT_KM / PHYSICS_UNITS_PER_AU;
        float kilometersPerSecondSquared = simulationAcceleration * kilometersPerSimulationUnit
                / (realSecondsPerSimulationSecond * realSecondsPerSimulationSecond);
        return kilometersPerSecondSquared * 1000.0f;
    }

    private float simulationAccelerationForMetersPerSecondSquared(float metersPerSecondSquared) {
        float realSecondsPerSimulationSecond = realSecondsPerSimulationSecond();
        float kilometersPerSimulationUnit = ASTRONOMICAL_UNIT_KM / PHYSICS_UNITS_PER_AU;
        return metersPerSecondSquared / 1000.0f
                * (realSecondsPerSimulationSecond * realSecondsPerSimulationSecond)
                / kilometersPerSimulationUnit;
    }

    private float realSecondsPerSimulationSecond() {
        float earthSimulationSpeed = (float) Math.sqrt(G * (SUN_MASS + 1.0f) / physicalRadiusForAu(EARTH_AU));
        return earthSimulationSpeed * (ASTRONOMICAL_UNIT_KM / PHYSICS_UNITS_PER_AU) / EARTH_ORBITAL_SPEED_KM_PER_SECOND;
    }

    private void drawAsteroidBelt(GraphicsContext gc, float sunX, float sunY, int frameCounter) {
        double rotation = frameCounter * 0.0008;
        double beltInnerRadius = orbitRadiusForAu(ASTEROID_BELT_INNER_AU);
        double beltOuterRadius = orbitRadiusForAu(ASTEROID_BELT_OUTER_AU);
        double beltWidth = beltOuterRadius - beltInnerRadius;

        for (int i = 0; i < ASTEROID_COUNT; i++) {
            double angle = i * 2.399963229728653 + rotation;
            double radialNoise = ((i * 37) % 100) / 100.0;
            double orbitNoise = Math.sin(i * 12.9898) * 0.5 + 0.5;
            double asteroidRadius = beltInnerRadius + beltWidth * radialNoise + orbitNoise * 2.5;
            double x = sunX + Math.cos(angle) * asteroidRadius;
            double y = sunY + Math.sin(angle) * asteroidRadius;
            double size = 0.8 + ((i * 17) % 9) * 0.12;

            gc.setFill(Color.rgb(143, 91, 43, 0.55 + (((i * 13) % 30) / 100.0)));
            gc.fillOval(x - size / 2.0, y - size / 2.0, size, size);
        }
    }

    private void drawPlanetRings(GraphicsContext gc, int bodyIndex, float screenX, float screenY, float depthScale) {
        String name = bodyNames[bodyIndex];
        if (name == null) {
            return;
        }

        if (name.startsWith("Saturn")) {
            drawSaturnRings(gc, screenX, screenY, radius.get(bodyIndex) * depthScale);
        } else if (name.startsWith("Uranus")) {
            drawUranusVerticalRing(gc, screenX, screenY, radius.get(bodyIndex) * depthScale);
        }
    }

    private void drawSaturnRings(GraphicsContext gc, float x, float y, float bodyRadius) {
        double outerWidth = bodyRadius * 4.8;
        double outerHeight = bodyRadius * 1.45;
        double middleWidth = bodyRadius * 4.0;
        double middleHeight = bodyRadius * 1.15;
        double innerWidth = bodyRadius * 3.2;
        double innerHeight = bodyRadius * 0.9;

        gc.setLineWidth(1.5);
        gc.setStroke(Color.rgb(212, 178, 124, 0.75));
        gc.strokeOval(x - outerWidth / 2.0, y - outerHeight / 2.0, outerWidth, outerHeight);

        gc.setLineWidth(1.0);
        gc.setStroke(Color.rgb(169, 126, 74, 0.65));
        gc.strokeOval(x - middleWidth / 2.0, y - middleHeight / 2.0, middleWidth, middleHeight);

        gc.setStroke(Color.rgb(74, 59, 37, 0.45));
        gc.strokeOval(x - innerWidth / 2.0, y - innerHeight / 2.0, innerWidth, innerHeight);
    }

    private void drawUranusVerticalRing(GraphicsContext gc, float x, float y, float bodyRadius) {
        double ringWidth = bodyRadius * 1.15;
        double ringHeight = bodyRadius * 5.0;

        gc.setLineWidth(1.4);
        gc.setStroke(Color.rgb(158, 221, 232, 0.7));
        gc.strokeOval(x - ringWidth / 2.0, y - ringHeight / 2.0, ringWidth, ringHeight);

        gc.setLineWidth(0.8);
        gc.setStroke(Color.rgb(210, 245, 250, 0.45));
        gc.strokeOval(x - ringWidth * 0.72 / 2.0, y - ringHeight * 0.86 / 2.0, ringWidth * 0.72, ringHeight * 0.86);
    }

    private void initTornadoPlanOnce() {
        TaskGraph taskGraph = new TaskGraph("nbody")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION,
                        posX, posY, posZ, velX, velY, velZ, mass, activeState, physParams, simulationState)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION,
                        accX, accY, accZ, nextAccX, nextAccY, nextAccZ)
                .task("simulateFrame", PhysicsKernels::simulateVerletFrame,
                        posX, posY, posZ, velX, velY, velZ,
                        accX, accY, accZ, nextAccX, nextAccY, nextAccZ,
                        mass, activeState, physParams, simulationState, GPU_SUB_STEPS)
                .transferToHost(DataTransferMode.EVERY_EXECUTION,
                        posX, posY, posZ, velX, velY, velZ, accX, accY, accZ);

        executionPlan = new TornadoExecutionPlan(taskGraph.snapshot());

        TaskGraph trailTaskGraph = new TaskGraph("trailProjection")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION,
                        trailX, trailY, trailZ, trailSize, activeState, renderParams, simulationState)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, projectedTrailX, projectedTrailY)
                .task("projectTrails", PhysicsKernels::projectTrails,
                        trailX, trailY, trailZ, trailSize, activeState, renderParams,
                        projectedTrailX, projectedTrailY, simulationState)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, projectedTrailX, projectedTrailY);

        trailProjectionPlan = new TornadoExecutionPlan(trailTaskGraph.snapshot());
    }

    private void warmUpTornadoPlans() {
        updateSimulationState();
        updateRenderParams();
        executionPlan.execute();
        projectBodiesOnCpu();
        trailProjectionPlan.execute();
        resetSystem();
        updateSimulationState();
        updateRenderParams();
        projectBodiesOnCpu();
        computeDashboardMetricsOnCpu();
        projectedTrailYaw = Float.NaN;
        projectedTrailPitch = Float.NaN;
        trailsNeedProjection = false;
    }

    private void updateElapsedTime(long now) {
        if (simulationStartNanos < 0 || elapsedTimeLabel == null) {
            return;
        }

        long elapsedSeconds = (now - simulationStartNanos) / 1_000_000_000L;
        if (elapsedSeconds != displayedElapsedSeconds) {
            displayedElapsedSeconds = elapsedSeconds;
            elapsedTimeLabel.setText("Time: " + formatElapsedTime(elapsedSeconds));
        }
    }

    private void updateSimulationState() {
        simulationState.set(0, bodyCount);
    }

    private void updateRenderParams() {
        renderParams.set(0, cameraYaw);
        renderParams.set(1, cameraPitch);
        renderParams.set(2, (float) canvasWidth);
        renderParams.set(3, (float) canvasHeight);
        renderParams.set(4, PHYSICS_UNITS_PER_AU);
        renderParams.set(5, MERCURY_AU);
        renderParams.set(6, NEPTUNE_AU);
        renderParams.set(7, MIN_PLANET_ORBIT_RADIUS);
        renderParams.set(8, ORBIT_EDGE_PADDING);
        renderParams.set(9, PHYSICS_UNITS_PER_AU * 55.0f);
    }

    private void detectAndResolveCollisionsOnCpu() {
        if (!GravityGpuFramePolicy.shouldCheckCollisions(bodyCount, customBodyCount)) {
            return;
        }

        boolean mergedAny = false;
        for (int i = 0; i < bodyCount; i++) {
            if (activeState.get(i) == 0 || mass.get(i) <= 0.0f) {
                continue;
            }

            for (int j = i + 1; j < bodyCount; j++) {
                if (activeState.get(j) == 0 || mass.get(j) <= 0.0f) {
                    continue;
                }

                float dx = posX.get(j) - posX.get(i);
                float dy = posY.get(j) - posY.get(i);
                float dz = posZ.get(j) - posZ.get(i);
                if (dx * dx + dy * dy + dz * dz <= CENTER_COLLISION_EPSILON * CENTER_COLLISION_EPSILON) {
                    mergeBodies(i, j);
                    mergedAny = true;
                    break;
                }
            }
        }

        if (mergedAny) {
            compactBodies();
        }
    }

    private void mergeBodies(int firstIndex, int secondIndex) {
        float firstMass = mass.get(firstIndex);
        float secondMass = mass.get(secondIndex);
        float mergedMass = firstMass + secondMass;
        if (mergedMass <= 0.0f) {
            activeState.set(secondIndex, 0);
            return;
        }

        float mergedX = (posX.get(firstIndex) * firstMass + posX.get(secondIndex) * secondMass) / mergedMass;
        float mergedY = (posY.get(firstIndex) * firstMass + posY.get(secondIndex) * secondMass) / mergedMass;
        float mergedZ = (posZ.get(firstIndex) * firstMass + posZ.get(secondIndex) * secondMass) / mergedMass;
        float mergedVx = (velX.get(firstIndex) * firstMass + velX.get(secondIndex) * secondMass) / mergedMass;
        float mergedVy = (velY.get(firstIndex) * firstMass + velY.get(secondIndex) * secondMass) / mergedMass;
        float mergedVz = (velZ.get(firstIndex) * firstMass + velZ.get(secondIndex) * secondMass) / mergedMass;

        boolean keepFirst = firstMass >= secondMass;
        int survivor = keepFirst ? firstIndex : secondIndex;
        int absorbed = keepFirst ? secondIndex : firstIndex;

        posX.set(survivor, mergedX);
        posY.set(survivor, mergedY);
        posZ.set(survivor, mergedZ);
        velX.set(survivor, mergedVx);
        velY.set(survivor, mergedVy);
        velZ.set(survivor, mergedVz);
        accX.set(survivor, 0.0f);
        accY.set(survivor, 0.0f);
        accZ.set(survivor, 0.0f);
        nextAccX.set(survivor, 0.0f);
        nextAccY.set(survivor, 0.0f);
        nextAccZ.set(survivor, 0.0f);
        mass.set(survivor, mergedMass);
        radius.set(survivor, radiusForCreatedMass(mergedMass));
        dashboardSpeed.set(survivor, (float) Math.sqrt(mergedVx * mergedVx + mergedVy * mergedVy + mergedVz * mergedVz));
        dashboardAcceleration.set(survivor, 0.0f);
        dashboardNearestDistance.set(survivor, 0.0f);
        clearDashboardMetrics(survivor);
        dashboardNearestIndex.set(survivor, -1);
        bodyNames[survivor] = bodyNames[survivor] + "+";
        editableMass[survivor] = editableMass[survivor] || editableMass[absorbed];
        activeState.set(absorbed, 0);
        clearTrail(absorbed);
    }

    private void compactBodies() {
        int writeIndex = 0;
        for (int readIndex = 0; readIndex < bodyCount; readIndex++) {
            if (activeState.get(readIndex) == 0) {
                continue;
            }

            if (writeIndex != readIndex) {
                copyBody(readIndex, writeIndex);
            }
            writeIndex++;
        }

        for (int i = writeIndex; i < bodyCount; i++) {
            clearBodySlot(i);
        }
        bodyCount = writeIndex;
    }

    private void copyBody(int source, int target) {
        bodyNames[target] = bodyNames[source];
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
        radius.set(target, radius.get(source));
        dashboardSpeed.set(target, dashboardSpeed.get(source));
        dashboardAcceleration.set(target, dashboardAcceleration.get(source));
        dashboardNearestDistance.set(target, dashboardNearestDistance.get(source));
        for (int metricOffset = 0; metricOffset < DASHBOARD_METRIC_STRIDE; metricOffset++) {
            setDashboardMetric(target, metricOffset, dashboardMetric(source, metricOffset));
        }
        dashboardNearestIndex.set(target, dashboardNearestIndex.get(source));
        projectedScreenX.set(target, projectedScreenX.get(source));
        projectedScreenY.set(target, projectedScreenY.get(source));
        projectedDepthScale.set(target, projectedDepthScale.get(source));
        bodyColors[target] = bodyColors[source];
        bodyTrailColors[target] = bodyTrailColors[source];
        bodyLabelColors[target] = bodyLabelColors[source];
        bodyOrbitColors[target] = bodyOrbitColors[source];
        editableMass[target] = editableMass[source];
        orbitSemiMajorAu[target] = orbitSemiMajorAu[source];
        orbitEccentricity[target] = orbitEccentricity[source];
        activeState.set(target, 1);

        copyTrail(source, target);
        dashboardRows[target] = null;
        dashboardLabels[target] = null;
        positionXFields[target] = null;
        positionYFields[target] = null;
        positionZFields[target] = null;
        massFields[target] = null;
        velocityXFields[target] = null;
        velocityYFields[target] = null;
        velocityZFields[target] = null;
    }

    private void clearBodySlot(int i) {
        bodyNames[i] = null;
        posX.set(i, 0.0f);
        posY.set(i, 0.0f);
        posZ.set(i, 0.0f);
        velX.set(i, 0.0f);
        velY.set(i, 0.0f);
        velZ.set(i, 0.0f);
        nextPosX.set(i, 0.0f);
        nextPosY.set(i, 0.0f);
        nextPosZ.set(i, 0.0f);
        nextVelX.set(i, 0.0f);
        nextVelY.set(i, 0.0f);
        nextVelZ.set(i, 0.0f);
        accX.set(i, 0.0f);
        accY.set(i, 0.0f);
        accZ.set(i, 0.0f);
        nextAccX.set(i, 0.0f);
        nextAccY.set(i, 0.0f);
        nextAccZ.set(i, 0.0f);
        mass.set(i, 0.0f);
        radius.set(i, 0.0f);
        dashboardSpeed.set(i, 0.0f);
        dashboardAcceleration.set(i, 0.0f);
        dashboardNearestDistance.set(i, 0.0f);
        clearDashboardMetrics(i);
        dashboardNearestIndex.set(i, -1);
        projectedScreenX.set(i, 0.0f);
        projectedScreenY.set(i, 0.0f);
        projectedDepthScale.set(i, 1.0f);
        bodyColors[i] = null;
        bodyTrailColors[i] = null;
        bodyLabelColors[i] = null;
        bodyOrbitColors[i] = null;
        editableMass[i] = false;
        orbitSemiMajorAu[i] = 0.0f;
        orbitEccentricity[i] = 0.0f;
        activeState.set(i, 0);
        clearTrail(i);
        dashboardRows[i] = null;
        dashboardLabels[i] = null;
        positionXFields[i] = null;
        positionYFields[i] = null;
        positionZFields[i] = null;
        massFields[i] = null;
        velocityXFields[i] = null;
        velocityYFields[i] = null;
        velocityZFields[i] = null;
    }

    private void updateDashboard() {
        while (dashboardList.getChildren().size() > bodyCount) {
            dashboardList.getChildren().removeLast();
        }

        for (int i = 0; i < bodyCount; i++) {
            int nearestIndex = dashboardNearestIndex.get(i);
            String nearestText = nearestIndex < 0
                    ? "Nearest: -"
                    : String.format("Nearest: %s %.3fAU", bodyNames[nearestIndex], physicsDistanceToAu(dashboardNearestDistance.get(i)));
            HBox row = dashboardRows[i];
            Label label = dashboardLabels[i];

            if (dashboardRowNeedsRebuild(i)) {
                row = createDashboardRow(i);
                dashboardRows[i] = row;
                label = dashboardLabels[i];
            }

            if (editableMass[i]) {
                label.setText(bodyNames[i]);
                updateEditableFields(i);
            } else {
                label.setText(String.format(
                        "%-10s | M:%8.2f | R:%4.1f | Sun:%7.3fAU",
                        bodyNames[i], mass.get(i), radius.get(i), dashboardMetric(i, DASHBOARD_DISTANCE_FROM_SUN_AU)
                ));
            }
            updateBodyTooltip(label, i, nearestText);

            if (dashboardList.getChildren().size() <= i) {
                dashboardList.getChildren().add(row);
            } else if (dashboardList.getChildren().get(i) != row) {
                dashboardList.getChildren().set(i, row);
            }
        }
    }

    private boolean dashboardRowNeedsRebuild(int i) {
        return dashboardRows[i] == null
                || dashboardLabels[i] == null
                || (editableMass[i] && (positionXFields[i] == null || positionYFields[i] == null || positionZFields[i] == null
                || massFields[i] == null || velocityXFields[i] == null || velocityYFields[i] == null || velocityZFields[i] == null))
                || (!editableMass[i] && (positionXFields[i] != null || positionYFields[i] != null || positionZFields[i] != null
                || massFields[i] != null || velocityXFields[i] != null || velocityYFields[i] != null || velocityZFields[i] != null));
    }

    private HBox createDashboardRow(int i) {
        String hexColor = toHex(bodyColors[i]);
        Label label = new Label();
        label.setStyle(String.format("-fx-text-fill: %s; -fx-font-family: monospace; -fx-font-size: 11px;", hexColor));
        dashboardLabels[i] = label;

        HBox row = new HBox(6);
        row.setStyle("-fx-alignment: center-left;");

        if (editableMass[i]) {
            positionXFields[i] = createEditableField("X [AU]", () -> applyPositionFields(i), 58);
            positionYFields[i] = createEditableField("Y [AU]", () -> applyPositionFields(i), 58);
            positionZFields[i] = createEditableField("Z [AU]", () -> applyPositionFields(i), 58);
            massFields[i] = createEditableField("Mass [M_Earth]", () -> applyMassField(i), 58);
            velocityXFields[i] = createEditableField("Vx [km/s equivalent]", () -> applyVelocityFields(i), 58);
            velocityYFields[i] = createEditableField("Vy [km/s equivalent]", () -> applyVelocityFields(i), 58);
            velocityZFields[i] = createEditableField("Vz [km/s equivalent]", () -> applyVelocityFields(i), 58);
            updateEditableFields(i);

            GridPane editorGrid = new GridPane();
            editorGrid.setHgap(4);
            editorGrid.setVgap(3);
            addEditorField(editorGrid, "X", positionXFields[i], 0, 0);
            addEditorField(editorGrid, "Y", positionYFields[i], 2, 0);
            addEditorField(editorGrid, "Z", positionZFields[i], 4, 0);
            addEditorField(editorGrid, "M", massFields[i], 0, 1);
            addEditorField(editorGrid, "Vx", velocityXFields[i], 2, 1);
            addEditorField(editorGrid, "Vy", velocityYFields[i], 4, 1);
            addEditorField(editorGrid, "Vz", velocityZFields[i], 0, 2);
            VBox editableLayout = new VBox(3, label, editorGrid);
            row.getChildren().add(editableLayout);
        } else {
            positionXFields[i] = null;
            positionYFields[i] = null;
            positionZFields[i] = null;
            massFields[i] = null;
            velocityXFields[i] = null;
            velocityYFields[i] = null;
            velocityZFields[i] = null;
            row.getChildren().add(label);
        }

        return row;
    }

    private TextField createEditableField(String tooltip, Runnable applyAction, double width) {
        TextField field = new TextField();
        field.setTooltip(new Tooltip(tooltip));
        field.setPrefWidth(width);
        field.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-background-color: #1d1d28; -fx-text-fill: #ffffff; -fx-border-color: #444455;");
        field.setOnAction(_ -> applyAction.run());
        field.focusedProperty().addListener((_, _, focused) -> {
            if (!focused) {
                applyAction.run();
            }
        });
        return field;
    }

    private void addEditorField(GridPane grid, String labelText, TextField field, int column, int row) {
        Label label = new Label(labelText + ":");
        label.setStyle("-fx-text-fill: #ffffff; -fx-font-family: monospace; -fx-font-size: 11px;");
        grid.add(label, column, row);
        grid.add(field, column + 1, row);
    }

    private void applyPositionFields(int i) {
        if (!hasEditablePositionFields(i)) {
            return;
        }
        try {
            float newX = parseField(positionXFields[i]) * PHYSICS_UNITS_PER_AU;
            float newY = parseField(positionYFields[i]) * PHYSICS_UNITS_PER_AU;
            float newZ = parseField(positionZFields[i]) * PHYSICS_UNITS_PER_AU;
            posX.set(i, newX);
            posY.set(i, newY);
            posZ.set(i, newZ);
            clearTrail(i);
            updateEditableFields(i);
        } catch (NumberFormatException e) {
            updateEditableFields(i);
        }
    }

    private void applyMassField(int i) {
        TextField massField = massFields[i];
        if (massField == null) {
            return;
        }

        try {
            float newMass = Math.max(0.0f, parseField(massField));
            mass.set(i, newMass);
            radius.set(i, radiusForCreatedMass(newMass));
            updateEditableFields(i);
        } catch (NumberFormatException e) {
            updateEditableFields(i);
        }
    }

    private void applyVelocityFields(int i) {
        if (!hasEditableVelocityFields(i)) {
            return;
        }
        try {
            velX.set(i, kilometersPerSecondToSimulationSpeed(parseField(velocityXFields[i])));
            velY.set(i, kilometersPerSecondToSimulationSpeed(parseField(velocityYFields[i])));
            velZ.set(i, kilometersPerSecondToSimulationSpeed(parseField(velocityZFields[i])));
            updateEditableFields(i);
        } catch (NumberFormatException e) {
            updateEditableFields(i);
        }
    }

    private float parseField(TextField field) {
        if (field == null) {
            throw new NumberFormatException("Missing editable field");
        }
        return Float.parseFloat(field.getText().trim().replace(',', '.'));
    }

    private boolean hasEditablePositionFields(int i) {
        return isActiveEditableBodyIndex(i)
                && positionXFields[i] != null
                && positionYFields[i] != null
                && positionZFields[i] != null;
    }

    private boolean hasEditableVelocityFields(int i) {
        return isActiveEditableBodyIndex(i)
                && velocityXFields[i] != null
                && velocityYFields[i] != null
                && velocityZFields[i] != null;
    }

    private boolean isActiveEditableBodyIndex(int i) {
        return i >= 0 && i < bodyCount && activeState.get(i) == 1 && editableMass[i];
    }

    private void updateEditableFields(int i) {
        setFieldTextIfIdle(positionXFields[i], "%.3f", posX.get(i) / PHYSICS_UNITS_PER_AU);
        setFieldTextIfIdle(positionYFields[i], "%.3f", posY.get(i) / PHYSICS_UNITS_PER_AU);
        setFieldTextIfIdle(positionZFields[i], "%.3f", posZ.get(i) / PHYSICS_UNITS_PER_AU);
        setFieldTextIfIdle(massFields[i], "%.2f", mass.get(i));
        setFieldTextIfIdle(velocityXFields[i], "%.2f", dashboardMetric(i, DASHBOARD_VELOCITY_X_KILOMETERS_PER_SECOND));
        setFieldTextIfIdle(velocityYFields[i], "%.2f", dashboardMetric(i, DASHBOARD_VELOCITY_Y_KILOMETERS_PER_SECOND));
        setFieldTextIfIdle(velocityZFields[i], "%.2f", dashboardMetric(i, DASHBOARD_VELOCITY_Z_KILOMETERS_PER_SECOND));
    }

    private void setFieldTextIfIdle(TextField field, String format, float value) {
        if (field != null && !field.isFocused()) {
            field.setText(String.format(format, value));
        }
    }

    private void updateBodyTooltip(Label label, int i, String nearestText) {
        Tooltip tooltip = label.getTooltip();
        if (tooltip == null) {
            tooltip = new Tooltip();
            tooltip.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
            label.setTooltip(tooltip);
        }
        tooltip.setText(bodyDetailsText(i, nearestText));
    }

    private String bodyDetailsText(int i, String nearestText) {
        float xAu = posX.get(i) / PHYSICS_UNITS_PER_AU;
        float yAu = posY.get(i) / PHYSICS_UNITS_PER_AU;
        float zAu = posZ.get(i) / PHYSICS_UNITS_PER_AU;
        return String.format(
                "%s | R: %.1f | A: %.6f%nX: %.3f AU | Y: %.3f AU | Z: %.3f AU%n%s%nM: %.3f M_Earth | Sun: %.3f AU%nVx: %.3f | Vy: %.3f | Vz: %.3f km/s%nAx: %.6f | Ay: %.6f | Az: %.6f m/s2",
                bodyNames[i], radius.get(i), dashboardAcceleration.get(i),
                xAu, yAu, zAu,
                nearestText,
                mass.get(i), dashboardMetric(i, DASHBOARD_DISTANCE_FROM_SUN_AU),
                dashboardMetric(i, DASHBOARD_VELOCITY_X_KILOMETERS_PER_SECOND),
                dashboardMetric(i, DASHBOARD_VELOCITY_Y_KILOMETERS_PER_SECOND),
                dashboardMetric(i, DASHBOARD_VELOCITY_Z_KILOMETERS_PER_SECOND),
                dashboardMetric(i, DASHBOARD_ACCELERATION_X_METERS_PER_SECOND_SQUARED),
                dashboardMetric(i, DASHBOARD_ACCELERATION_Y_METERS_PER_SECOND_SQUARED),
                dashboardMetric(i, DASHBOARD_ACCELERATION_Z_METERS_PER_SECOND_SQUARED)
        );
    }

    private float radiusForCreatedMass(float bodyMass) {
        return Math.min(MAX_CREATED_BODY_RADIUS, (float) Math.max(3.0, 4.0 + Math.pow(bodyMass, 1.0 / 3.0) * 1.8));
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }

    private void resetSystem() {
        simulationStartNanos = -1L;
        displayedElapsedSeconds = -1L;
        if (elapsedTimeLabel != null) {
            elapsedTimeLabel.setText("Time: 0s");
        }

        bodyCount = 0;
        customBodyCount = 0;
        if (alignPlanetsOnReset) {
            cameraYaw = 0.0f;
            cameraPitch = 0.0f;
        }

        for (int i = 0; i < MAX_BODIES; i++) {
            activeState.set(i, 0);
            dashboardSpeed.set(i, 0.0f);
            dashboardAcceleration.set(i, 0.0f);
            dashboardNearestDistance.set(i, 0.0f);
            clearDashboardMetrics(i);
            dashboardNearestIndex.set(i, -1);
            projectedScreenX.set(i, 0.0f);
            projectedScreenY.set(i, 0.0f);
            projectedDepthScale.set(i, 1.0f);
            nextPosX.set(i, 0.0f);
            nextPosY.set(i, 0.0f);
            nextPosZ.set(i, 0.0f);
            nextVelX.set(i, 0.0f);
            nextVelY.set(i, 0.0f);
            nextVelZ.set(i, 0.0f);
            posZ.set(i, 0.0f);
            velZ.set(i, 0.0f);
            accX.set(i, 0.0f);
            accY.set(i, 0.0f);
            accZ.set(i, 0.0f);
            nextAccX.set(i, 0.0f);
            nextAccY.set(i, 0.0f);
            nextAccZ.set(i, 0.0f);
            editableMass[i] = false;
            orbitSemiMajorAu[i] = 0.0f;
            orbitEccentricity[i] = 0.0f;
            dashboardRows[i] = null;
            dashboardLabels[i] = null;
            positionXFields[i] = null;
            positionYFields[i] = null;
            positionZFields[i] = null;
            massFields[i] = null;
            velocityXFields[i] = null;
            velocityYFields[i] = null;
            velocityZFields[i] = null;
            clearTrail(i);
        }

        float cx = 0.0f;
        float cy = 0.0f;

        // Sun
        addBody("Sun", cx, cy, 0.0f, 0, 0, 0.0f, SUN_MASS, 16, Color.GOLD, false);

        float[] orbitAngles = resetOrbitAngles();

        // Planets use real mass ratios in Earth masses, real eccentricities, and AU-scaled orbital mechanics.
        addKeplerPlanet("Mercury", cx, cy, MERCURY_AU, MERCURY_ECCENTRICITY, 0.055f, 3.0f, Color.GRAY, orbitAngles[0]);
        addKeplerPlanet("Venus",   cx, cy, VENUS_AU,   VENUS_ECCENTRICITY,   0.815f, 4.5f, Color.BEIGE, orbitAngles[1]);
        addKeplerPlanet("Earth",   cx, cy, EARTH_AU,   EARTH_ECCENTRICITY,   1.000f, 5.0f, Color.DODGERBLUE, orbitAngles[2]);
        addKeplerPlanet("Mars",    cx, cy, MARS_AU,    MARS_ECCENTRICITY,    0.107f, 4.0f, Color.INDIANRED, orbitAngles[3]);
        addKeplerPlanet("Jupiter", cx, cy, JUPITER_AU, JUPITER_ECCENTRICITY, 317.8f, 11.0f, Color.PERU, orbitAngles[4]);
        addKeplerPlanet("Saturn",  cx, cy, SATURN_AU,  SATURN_ECCENTRICITY,  95.2f,  9.0f, Color.BURLYWOOD, orbitAngles[5]);
        addKeplerPlanet("Uranus",  cx, cy, URANUS_AU,  URANUS_ECCENTRICITY,  14.5f,  7.0f, Color.LIGHTBLUE, orbitAngles[6]);
        addKeplerPlanet("Neptune", cx, cy, NEPTUNE_AU, NEPTUNE_ECCENTRICITY, 17.1f,  7.0f, Color.ROYALBLUE, orbitAngles[7]);

        // Momentum compensation for the Sun.
        float totalPx = 0, totalPy = 0, totalPz = 0;
        for (int i = 1; i < bodyCount; i++) {
            totalPx += mass.get(i) * velX.get(i);
            totalPy += mass.get(i) * velY.get(i);
            totalPz += mass.get(i) * velZ.get(i);
        }
        velX.set(0, -totalPx / mass.get(0));
        velY.set(0, -totalPy / mass.get(0));
        velZ.set(0, -totalPz / mass.get(0));
    }

    private float[] resetOrbitAngles() {
        float[] angles = new float[8];
        if (alignPlanetsOnReset) {
            return angles;
        }

        double baseAngle = Math.random() * Math.PI * 2.0;
        double jitterRange = Math.PI / 45.0;
        for (int i = 0; i < angles.length; i++) {
            double jitter = (Math.random() * 2.0 - 1.0) * jitterRange;
            angles[i] = (float) (baseAngle + STABLE_ORBIT_PHASES[i] + jitter);
        }

        return angles;
    }

    private float orbitRadiusForAu(float semiMajorAxisAu) {
        double minLog = Math.log(MERCURY_AU);
        double maxLog = Math.log(NEPTUNE_AU);
        double orbitLog = Math.log(Math.max(MERCURY_AU, semiMajorAxisAu));
        double normalized = (orbitLog - minLog) / (maxLog - minLog);
        double maxOrbitRadius = Math.max(MIN_PLANET_ORBIT_RADIUS + 1.0, Math.min(canvasWidth, canvasHeight) / 2.0 - ORBIT_EDGE_PADDING);
        return (float) (MIN_PLANET_ORBIT_RADIUS + normalized * (maxOrbitRadius - MIN_PLANET_ORBIT_RADIUS));
    }

    private float physicalRadiusForAu(float semiMajorAxisAu) {
        return semiMajorAxisAu * PHYSICS_UNITS_PER_AU;
    }

    private void addKeplerPlanet(String name, float cx, float cy, float semiMajorAxisAu, float eccentricity, float m, float size, Color color, float trueAnomaly) {
        float semiMajorAxis = physicalRadiusForAu(semiMajorAxisAu);
        float boundedEccentricity = Math.clamp(eccentricity, 0.0f, 0.95f);
        float semiLatusRectum = semiMajorAxis * (1.0f - boundedEccentricity * boundedEccentricity);
        float radiusFromFocus = (float) (semiLatusRectum / (1.0 + boundedEccentricity * Math.cos(trueAnomaly)));
        float mu = G * (SUN_MASS + m);
        float specificAngularMomentum = (float) Math.sqrt(mu * semiLatusRectum);

        float x = (float) (cx + radiusFromFocus * Math.cos(trueAnomaly));
        float y = (float) (cy + radiusFromFocus * Math.sin(trueAnomaly));
        float vx = (float) (-mu / specificAngularMomentum * Math.sin(trueAnomaly));
        float vy = (float) (mu / specificAngularMomentum * (boundedEccentricity + Math.cos(trueAnomaly)));
        int planetIndex = bodyCount;
        addBody(name, x, y, 0.0f, vx, vy, 0.0f, m, size, color, false);
        if (bodyCount > planetIndex) {
            orbitSemiMajorAu[planetIndex] = semiMajorAxisAu;
            orbitEccentricity[planetIndex] = boundedEccentricity;
        }
    }

    private void addEditableBody() {
        if (bodyCount >= MAX_BODIES) {
            return;
        }

        customBodyCount++;
        float[] spawnPosition = rightEdgeSpawnPosition();
        float x = spawnPosition[0];
        float y = spawnPosition[1];
        float z = spawnPosition[2];
        addBody(String.format("Body #%d", customBodyCount), x, y, z, 0.0f, 0.0f, 0.0f, 0.0f, 3.0f, Color.RED, true);
        computeDashboardMetricsOnCpu();
        updateDashboard();
    }

    private float[] rightEdgeSpawnPosition() {
        float edgePadding = MAX_CREATED_BODY_RADIUS + 4.0f;
        float screenDistance = (float) Math.max(MIN_PLANET_ORBIT_RADIUS, canvasWidth / 2.0 - edgePadding);
        float physicsDistance = physicsRadiusForScreenDistance(screenDistance);

        float cosYaw = (float) Math.cos(cameraYaw);
        float sinYaw = (float) Math.sin(cameraYaw);
        return new float[] {
                physicsDistance * cosYaw,
                0.0f,
                physicsDistance * sinYaw
        };
    }

    private void addBody(String name, float x, float y, float z, float vx, float vy, float vz, float m, float r, Color color, boolean canEditMass) {
        if (bodyCount >= MAX_BODIES) return;

        int i = bodyCount;
        bodyNames[i] = name;
        posX.set(i, x); posY.set(i, y); posZ.set(i, z);
        velX.set(i, vx); velY.set(i, vy); velZ.set(i, vz);
        accX.set(i, 0); accY.set(i, 0); accZ.set(i, 0);
        nextAccX.set(i, 0); nextAccY.set(i, 0); nextAccZ.set(i, 0);
        mass.set(i, m); radius.set(i, r);
        dashboardSpeed.set(i, (float) Math.sqrt(vx * vx + vy * vy + vz * vz));
        dashboardAcceleration.set(i, 0.0f);
        dashboardNearestDistance.set(i, 0.0f);
        clearDashboardMetrics(i);
        dashboardNearestIndex.set(i, -1);
        bodyColors[i] = color;
        bodyTrailColors[i] = color.deriveColor(0, 1, 1, 0.3);
        bodyLabelColors[i] = color.deriveColor(0, 0.7, 1.2, 0.9);
        bodyOrbitColors[i] = color.deriveColor(0, 0.8, 1.3, 0.28);
        editableMass[i] = canEditMass;
        orbitSemiMajorAu[i] = 0.0f;
        orbitEccentricity[i] = 0.0f;
        activeState.set(i, 1);

        bodyCount++;
    }

    static void main(String[] args) {
        launch(args);
    }
}
