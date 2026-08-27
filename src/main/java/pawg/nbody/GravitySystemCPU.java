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

public class GravitySystemCPU extends Application {

    private static final int CANVAS_WIDTH = 1250;
    private static final int SIDEBAR_WIDTH = 430;
    private static final int MAX_BODIES = 1024;
    private static final int TRAIL_CAPACITY = 180;
    private static final int SUB_STEPS = 12;

    private static final int DASHBOARD_METRIC_STRIDE = 7;
    private static final int DASHBOARD_DISTANCE_FROM_SUN_AU = 0;
    private static final int DASHBOARD_VELOCITY_X_KILOMETERS_PER_SECOND = 1;
    private static final int DASHBOARD_VELOCITY_Y_KILOMETERS_PER_SECOND = 2;
    private static final int DASHBOARD_VELOCITY_Z_KILOMETERS_PER_SECOND = 3;
    private static final int DASHBOARD_ACCELERATION_X_METERS_PER_SECOND_SQUARED = 4;
    private static final int DASHBOARD_ACCELERATION_Y_METERS_PER_SECOND_SQUARED = 5;
    private static final int DASHBOARD_ACCELERATION_Z_METERS_PER_SECOND_SQUARED = 6;

    private static final float SPEED_FACTOR = 0.1f;
    private static final float G = 1000.0f * SPEED_FACTOR * SPEED_FACTOR;
    private static final float DT = 0.0012f;
    private static final float SUN_MASS = 332_946.0f;
    private static final float PHYSICS_UNITS_PER_AU = 140.0f;
    private static final float ASTRONOMICAL_UNIT_KM = 149_597_870.7f;
    private static final float EARTH_ORBITAL_SPEED_KM_PER_SECOND = 29.78f;
    private static final float MIN_PLANET_ORBIT_RADIUS = 55.0f;
    private static final float ORBIT_EDGE_PADDING = 50.0f;
    private static final float MAX_CREATED_BODY_RADIUS = 10.0f;
    private static final float WEAK_SUN_GRAVITY_THRESHOLD_METERS_PER_SECOND_SQUARED = 0.00001f;
    private static final float MIN_VISIBLE_WEAK_SUN_GRAVITY_SIMULATION_ACCELERATION = 0.00002f;
    private static final float CENTER_COLLISION_EPSILON = 0.5f;
    private static final float DEPTH_SCALE_DENOMINATOR = PHYSICS_UNITS_PER_AU * 55.0f;

    private static final float MERCURY_AU = 0.387f;
    private static final float VENUS_AU = 0.723f;
    private static final float EARTH_AU = 1.0f;
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
    private static final float[] STABLE_ORBIT_PHASES = {
            0.10f, 1.65f, 3.15f, 4.85f,
            0.75f, 2.85f, 4.95f, 5.65f
    };

    private static final Color AXIS_X_COLOR = Color.rgb(255, 90, 90);
    private static final Color AXIS_Y_COLOR = Color.rgb(90, 255, 120);
    private static final Color AXIS_Z_COLOR = Color.rgb(110, 170, 255);

    private final float[] posX = new float[MAX_BODIES], posY = new float[MAX_BODIES], posZ = new float[MAX_BODIES];
    private final float[] velX = new float[MAX_BODIES], velY = new float[MAX_BODIES], velZ = new float[MAX_BODIES];
    private final float[] accX = new float[MAX_BODIES], accY = new float[MAX_BODIES], accZ = new float[MAX_BODIES];
    private final float[] oldAccX = new float[MAX_BODIES], oldAccY = new float[MAX_BODIES], oldAccZ = new float[MAX_BODIES];
    private final float[] mass = new float[MAX_BODIES], radius = new float[MAX_BODIES];
    private final float[] dashboardSpeed = new float[MAX_BODIES], dashboardAcceleration = new float[MAX_BODIES];
    private final float[] dashboardNearestDistance = new float[MAX_BODIES];
    private final float[] dashboardMetrics = new float[MAX_BODIES * DASHBOARD_METRIC_STRIDE];
    private final float[] projectedScreenX = new float[MAX_BODIES], projectedScreenY = new float[MAX_BODIES], projectedDepthScale = new float[MAX_BODIES];
    private final float[] trailX = new float[MAX_BODIES * TRAIL_CAPACITY], trailY = new float[MAX_BODIES * TRAIL_CAPACITY], trailZ = new float[MAX_BODIES * TRAIL_CAPACITY];
    private final float[] projectedTrailX = new float[MAX_BODIES * TRAIL_CAPACITY], projectedTrailY = new float[MAX_BODIES * TRAIL_CAPACITY];
    private final int[] activeState = new int[MAX_BODIES], trailStart = new int[MAX_BODIES], trailSize = new int[MAX_BODIES], dashboardNearestIndex = new int[MAX_BODIES];
    private final String[] bodyNames = new String[MAX_BODIES];
    private final Color[] bodyColors = new Color[MAX_BODIES], bodyTrailColors = new Color[MAX_BODIES], bodyLabelColors = new Color[MAX_BODIES], bodyOrbitColors = new Color[MAX_BODIES];
    private final boolean[] editableMass = new boolean[MAX_BODIES];
    private final HBox[] dashboardRows = new HBox[MAX_BODIES];
    private final Label[] dashboardLabels = new Label[MAX_BODIES];
    private final TextField[] positionXFields = new TextField[MAX_BODIES], positionYFields = new TextField[MAX_BODIES], positionZFields = new TextField[MAX_BODIES];
    private final TextField[] massFields = new TextField[MAX_BODIES], velocityXFields = new TextField[MAX_BODIES], velocityYFields = new TextField[MAX_BODIES], velocityZFields = new TextField[MAX_BODIES];
    private final VBox dashboardList = new VBox(6);

    private Label elapsedTimeLabel;
    private int bodyCount, customBodyCount, frameCounter;
    private long simulationStartNanos = -1L;
    private boolean alignPlanetsOnReset, showOrbitGuides, showTrails, showWeakSunGravity, showHabitableZone, showAsteroidBelt;
    private float cameraYaw, cameraPitch, dragStartYaw, dragStartPitch;
    private double dragStartX, dragStartY, canvasWidth = CANVAS_WIDTH, canvasHeight = 880;

    @Override
    public void start(Stage primaryStage) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        canvasWidth = Math.max(800.0, screenBounds.getWidth() - SIDEBAR_WIDTH);
        canvasHeight = screenBounds.getHeight();
        Canvas canvas = new Canvas(canvasWidth, canvasHeight);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        resetSystem();

        canvas.setOnMousePressed(event -> {
            dragStartX = event.getX();
            dragStartY = event.getY();
            dragStartYaw = cameraYaw;
            dragStartPitch = cameraPitch;
        });
        canvas.setOnMouseDragged(event -> {
            cameraYaw = dragStartYaw + (float) ((event.getX() - dragStartX) * 0.006);
            cameraPitch = Math.clamp(dragStartPitch - (float) ((event.getY() - dragStartY) * 0.006), -1.35f, 1.35f);
            projectBodies();
            if (showTrails) projectTrails();
        });

        BorderPane root = new BorderPane(canvas);
        root.setRight(createSidebar());
        Scene scene = new Scene(root, screenBounds.getWidth(), screenBounds.getHeight(), Color.BLACK);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE) resetSystem();
        });

        primaryStage.setTitle("CPU NBody simulator");
        NBodyStageIcons.addJupiterIcon(primaryStage);
        primaryStage.setScene(scene);
        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());
        primaryStage.setResizable(false);
        primaryStage.show();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (simulationStartNanos < 0L) simulationStartNanos = now;
                frameCounter++;
                for (int step = 0; step < SUB_STEPS; step++) {
                    physicsStepVerlet(DT);
                    resolveCollisions();
                }
                if (showTrails && frameCounter % 2 == 0) {
                    for (int i = 0; i < bodyCount; i++) {
                        if (activeState[i] != 0) appendTrailPoint(i);
                    }
                }
                projectBodies();
                if (showTrails) projectTrails();
                computeDashboardMetrics();
                if (frameCounter % 5 == 0) {
                    updateElapsedTime(now);
                    updateDashboard();
                }
                drawScene(gc);
            }
        }.start();
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setStyle(String.format("-fx-background-color: #111118; -fx-padding: 15; -fx-min-width: %dpx; -fx-pref-width: %dpx; -fx-border-color: #333344; -fx-border-width: 0 0 0 1;", SIDEBAR_WIDTH, SIDEBAR_WIDTH));
        Label title = new Label("Dashboard");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 14px;");
        Button btnReset = new Button("RESET (SPACE)");
        btnReset.setStyle("-fx-background-color: #222; -fx-text-fill: #ff4444; -fx-border-color: #ff4444; -fx-font-weight: bold; -fx-cursor: hand;");
        btnReset.setFocusTraversable(false);
        btnReset.setOnAction(_ -> resetSystem());
        Button btnAddBody = new Button("+");
        btnAddBody.setStyle("-fx-background-color: #222; -fx-text-fill: #ff7777; -fx-border-color: #ff7777; -fx-font-weight: bold; -fx-cursor: hand;");
        btnAddBody.setFocusTraversable(false);
        btnAddBody.setOnAction(_ -> addEditableBody());
        elapsedTimeLabel = new Label("Time: 0s");
        elapsedTimeLabel.setStyle("-fx-text-fill: #d8d8e8; -fx-font-family: monospace; -fx-font-size: 11px;");

        CheckBox align = optionCheckbox("Align planets on reset", alignPlanetsOnReset);
        align.selectedProperty().addListener((_, _, selected) -> alignPlanetsOnReset = selected);
        CheckBox orbits = optionCheckbox("Show orbits", showOrbitGuides);
        orbits.selectedProperty().addListener((_, _, selected) -> showOrbitGuides = selected);
        CheckBox trails = optionCheckbox("Show trails", showTrails);
        trails.selectedProperty().addListener((_, _, selected) -> {
            showTrails = selected;
            clearAllTrails();
        });
        CheckBox weak = optionCheckbox("Show weak Sun gravity", showWeakSunGravity);
        weak.selectedProperty().addListener((_, _, selected) -> showWeakSunGravity = selected);
        CheckBox habitable = optionCheckbox("Show habitable zone", showHabitableZone);
        habitable.selectedProperty().addListener((_, _, selected) -> showHabitableZone = selected);
        CheckBox asteroid = optionCheckbox("Show asteroid belt", showAsteroidBelt);
        asteroid.selectedProperty().addListener((_, _, selected) -> showAsteroidBelt = selected);

        GridPane options = new GridPane();
        options.setHgap(12);
        options.setVgap(4);
        options.add(align, 0, 0);
        options.add(habitable, 1, 0);
        options.add(trails, 0, 1);
        options.add(weak, 1, 1);
        options.add(orbits, 0, 2);
        options.add(asteroid, 1, 2);

        Label legend = new Label("""
                Drag canvas = rotate 3D view
                X/Y/Z = physical position [AU]
                Vx/Vy/Vz = velocity [km/s eq]
                Ax/Ay/Az = acceleration [m/s² eq]
                Blue ring = weak Sun gravity boundary
                Nearest = closest body and distance [AU]""");
        legend.setStyle("-fx-text-fill: #b8b8c8; -fx-font-family: monospace; -fx-font-size: 11px; -fx-padding: 0 0 6 0;");
        sidebar.getChildren().addAll(title, new HBox(12, btnReset, btnAddBody, elapsedTimeLabel), options, legend, dashboardList);
        return sidebar;
    }

    private CheckBox optionCheckbox(String text, boolean selected) {
        CheckBox checkBox = new CheckBox(text);
        checkBox.setSelected(selected);
        checkBox.setFocusTraversable(false);
        checkBox.setStyle("-fx-text-fill: #b8b8c8; -fx-font-family: monospace; -fx-font-size: 11px;");
        return checkBox;
    }

    private void drawScene(GraphicsContext gc) {
        gc.setFill(Color.rgb(3, 3, 10, 0.35));
        gc.fillRect(0, 0, canvasWidth, canvasHeight);
        if (bodyCount > 0) {
            float sunX = projectedScreenX[0], sunY = projectedScreenY[0];
            if (showHabitableZone) drawHabitableZone(gc, sunX, sunY, mass[0]);
            if (showAsteroidBelt) drawAsteroidBelt(gc, sunX, sunY, frameCounter);
            if (showWeakSunGravity) drawWeakSunGravityBoundary(gc, 0);
        }
        if (showOrbitGuides) drawOrbitGuides(gc);
        if (showTrails) {
            for (int i = 0; i < bodyCount; i++) {
                if (activeState[i] != 0) drawTrail(gc, i);
            }
        }
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.BOTTOM);
        gc.setFont(Font.font("SansSerif", 11));
        int sunIndex = bodyCount > 0 && activeState[0] != 0 ? 0 : -1;
        float sunScreenX = sunIndex >= 0 ? projectedScreenX[sunIndex] : (float) (canvasWidth * 0.5);
        float sunScreenY = sunIndex >= 0 ? projectedScreenY[sunIndex] : (float) (canvasHeight * 0.5);
        for (int i = bodyCount - 1; i >= 0; i--) {
            if (activeState[i] == 0) continue;
            float renderRadius = Math.max(2.0f, radius[i] * projectedDepthScale[i]);
            drawPlanetRings(gc, i, projectedScreenX[i], projectedScreenY[i], projectedDepthScale[i]);
            drawSphere(gc, projectedScreenX[i], projectedScreenY[i], renderRadius, bodyColors[i],
                    sunScreenX - projectedScreenX[i], sunScreenY - projectedScreenY[i]);
            gc.setFill(bodyLabelColors[i]);
            gc.fillText(bodyNames[i], projectedScreenX[i], projectedScreenY[i] - renderRadius - 4);
        }
        drawAxisIndicator(gc);
    }

    private void drawTrail(GraphicsContext gc, int bodyIndex) {
        int size = trailSize[bodyIndex];
        if (size < 2) return;
        int start = trailStart[bodyIndex];
        gc.setStroke(bodyTrailColors[bodyIndex]);
        gc.setLineWidth(1.0);
        gc.setLineDashes(null);
        for (int k = 0; k < size - 1; k++) {
            int a = trailIndex(bodyIndex, (start + k) % TRAIL_CAPACITY);
            int b = trailIndex(bodyIndex, (start + k + 1) % TRAIL_CAPACITY);
            gc.strokeLine(projectedTrailX[a], projectedTrailY[a], projectedTrailX[b], projectedTrailY[b]);
        }
    }

    private void drawSphere(GraphicsContext gc, float centerX, float centerY, float sphereRadius, Color baseColor, float lightDx, float lightDy) {
        SpherePaint paint = spherePaint(baseColor, lightDx, lightDy);
        gc.setFill(paint.gradient());
        gc.fillOval(centerX - sphereRadius, centerY - sphereRadius, sphereRadius * 2.0, sphereRadius * 2.0);
        gc.setStroke(paint.rim());
        gc.setLineWidth(0.8);
        gc.strokeOval(centerX - sphereRadius, centerY - sphereRadius, sphereRadius * 2.0, sphereRadius * 2.0);
    }

    private SpherePaint spherePaint(Color baseColor, float lightDx, float lightDy) {
        double lightLength = Math.sqrt(lightDx * lightDx + lightDy * lightDy);
        double focusAngle = lightLength <= 0.0001 ? -135.0 : Math.toDegrees(Math.atan2(lightDy, lightDx));
        double focusDistance = lightLength <= 0.0001 ? 0.28 : 0.42;
        RadialGradient gradient = new RadialGradient(focusAngle, focusDistance, 0.5, 0.5, 0.82, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, baseColor.interpolate(Color.WHITE, 0.75).deriveColor(0, 1, 1, 0.95)),
                new Stop(0.45, baseColor),
                new Stop(1.0, baseColor.interpolate(Color.BLACK, 0.55)));
        return new SpherePaint(gradient, baseColor.interpolate(Color.BLACK, 0.25));
    }

    private void drawAxisIndicator(GraphicsContext gc) {
        double width = 145.0, height = 105.0;
        double x = canvasWidth - width - 18.0, y = 34.0;
        double cx = x + width * 0.5, cy = y + height * 0.56, length = 34.0;
        ScreenPoint xAxis = projectDirection(1.0f, 0.0f, 0.0f, length);
        ScreenPoint yAxis = projectDirection(0.0f, 1.0f, 0.0f, length);
        ScreenPoint zAxis = projectDirection(0.0f, 0.0f, 1.0f, length);

        gc.setFont(Font.font("Monospaced", 14));
        gc.setTextBaseline(VPos.BOTTOM);
        gc.setTextAlign(TextAlignment.RIGHT);
        double right = x + width - 4.0, textY = y - 3.0;
        String zText = axisValueText("Z", zAxis), yText = axisValueText("Y", yAxis), xText = axisValueText("X", xAxis);
        double zWidth = textWidth(zText, 14.0), yWidth = textWidth(yText, 14.0);
        gc.setFill(AXIS_Z_COLOR);
        gc.fillText(zText, right, textY);
        gc.setFill(AXIS_Y_COLOR);
        gc.fillText(yText, right - zWidth - 8.0, textY);
        gc.setFill(AXIS_X_COLOR);
        gc.fillText(xText, right - zWidth - yWidth - 16.0, textY);
        gc.setTextAlign(TextAlignment.LEFT);

        gc.setFill(Color.rgb(12, 12, 22, 0.70));
        gc.fillRoundRect(x, y, width, height, 10, 10);
        gc.setStroke(Color.rgb(120, 120, 150, 0.55));
        gc.strokeRoundRect(x, y, width, height, 10, 10);
        drawAxis(gc, cx, cy, xAxis, AXIS_X_COLOR);
        drawAxis(gc, cx, cy, yAxis, AXIS_Y_COLOR);
        drawAxis(gc, cx, cy, zAxis, AXIS_Z_COLOR);
    }

    private String axisValueText(String label, ScreenPoint point) {
        return String.format("%s %.1f,%.1f,%.1f", label, point.x(), point.y(), point.depthScale());
    }

    private double textWidth(String text, double fontSize) {
        return text.length() * fontSize * 0.61;
    }

    private void drawAxis(GraphicsContext gc, double cx, double cy, ScreenPoint point, Color color) {
        double endX = cx + point.x(), endY = cy + point.y();
        gc.setStroke(color.deriveColor(0, 1, 1, 0.85));
        gc.setLineWidth(Math.max(1.5, 2.8 * point.depthScale()));
        gc.strokeLine(cx, cy, endX, endY);
        double angle = Math.atan2(endY - cy, endX - cx), arrow = 6.0;
        gc.strokeLine(endX, endY, endX - Math.cos(angle - 0.55) * arrow, endY - Math.sin(angle - 0.55) * arrow);
        gc.strokeLine(endX, endY, endX - Math.cos(angle + 0.55) * arrow, endY - Math.sin(angle + 0.55) * arrow);
    }

    private ScreenPoint projectDirection(float x, float y, float z, double length) {
        float cosYaw = (float) Math.cos(cameraYaw), sinYaw = (float) Math.sin(cameraYaw);
        float cosPitch = (float) Math.cos(cameraPitch), sinPitch = (float) Math.sin(cameraPitch);
        float yawX = x * cosYaw + z * sinYaw;
        float yawZ = -x * sinYaw + z * cosYaw;
        float viewY = y * cosPitch - yawZ * sinPitch;
        float viewZ = y * sinPitch + yawZ * cosPitch;
        float scale = Math.clamp(1.0f + viewZ * 0.25f, 0.65f, 1.35f);
        return new ScreenPoint((float) (yawX * length * scale), (float) (-viewY * length * scale), scale);
    }

    private void drawOrbitGuides(GraphicsContext gc) {
        gc.setLineWidth(1.0);
        gc.setLineDashes(8, 7);
        for (int i = 1; i < bodyCount; i++) {
            if (activeState[i] != 0 && mass[i] > 0.0f) {
                gc.setStroke(bodyOrbitColors[i]);
                drawOsculatingOrbitGuide(gc, i);
            }
        }
        gc.setLineDashes(null);
    }

    private void drawOsculatingOrbitGuide(GraphicsContext gc, int i) {
        float rx = posX[i] - posX[0], ry = posY[i] - posY[0], rz = posZ[i] - posZ[0];
        float vx = velX[i] - velX[0], vy = velY[i] - velY[0], vz = velZ[i] - velZ[0];
        float r = length(rx, ry, rz), mu = G * (mass[0] + mass[i]);
        if (r <= 0.0001f || mu <= 0.0f) return;
        float hX = ry * vz - rz * vy, hY = rz * vx - rx * vz, hZ = rx * vy - ry * vx;
        float h = length(hX, hY, hZ);
        if (h <= 0.0001f) return;
        float speedSq = vx * vx + vy * vy + vz * vz;
        float energy = speedSq * 0.5f - mu / r;
        if (energy >= 0.0f) return;
        float semiMajor = -mu / (2.0f * energy);
        float eccentricity = (float) Math.sqrt(Math.max(0.0, 1.0 - (h * h) / (semiMajor * mu)));
        if (eccentricity >= 0.98f) return;
        float p = h * h / mu;
        float eX = ((vy * hZ - vz * hY) / mu) - rx / r;
        float eY = ((vz * hX - vx * hZ) / mu) - ry / r;
        float eZ = ((vx * hY - vy * hX) / mu) - rz / r;
        float eLen = length(eX, eY, eZ);
        if (eLen <= 0.0001f) {
            eX = rx / r; eY = ry / r; eZ = rz / r; eLen = 1.0f;
        }
        eX /= eLen; eY /= eLen; eZ /= eLen;
        float qX = hY * eZ - hZ * eY, qY = hZ * eX - hX * eZ, qZ = hX * eY - hY * eX;
        float qLen = length(qX, qY, qZ);
        if (qLen <= 0.0001f) return;
        qX /= qLen; qY /= qLen; qZ /= qLen;
        gc.beginPath();
        for (int sample = 0; sample <= 220; sample++) {
            float anomaly = (float) (sample * Math.PI * 2.0 / 220.0);
            float orbitR = p / (1.0f + eccentricity * (float) Math.cos(anomaly));
            float localX = orbitR * (float) Math.cos(anomaly), localY = orbitR * (float) Math.sin(anomaly);
            ScreenPoint point = projectPhysics(posX[0] + eX * localX + qX * localY, posY[0] + eY * localX + qY * localY, posZ[0] + eZ * localX + qZ * localY);
            if (sample == 0) gc.moveTo(point.x(), point.y()); else gc.lineTo(point.x(), point.y());
        }
        gc.stroke();
    }

    private void drawHabitableZone(GraphicsContext gc, float sunX, float sunY, float sunMass) {
        double luminosityScale = habitableZoneScale(sunMass);
        double inner = screenRadiusForPhysicsDistance(physicalRadiusForAu((float) (HABITABLE_ZONE_INNER_AU * luminosityScale)));
        double outer = screenRadiusForPhysicsDistance(physicalRadiusForAu((float) (HABITABLE_ZONE_OUTER_AU * luminosityScale)));
        if (outer <= inner) {
            return;
        }
        double middle = (inner + outer) * 0.5;
        double innerStop = Math.clamp(inner / outer, 0.0, 1.0);
        double middleStop = Math.clamp(middle / outer, innerStop, 1.0);
        gc.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(255, 205, 80, 0.0)),
                new Stop(Math.max(0.0, innerStop - 0.015), Color.rgb(255, 205, 80, 0.0)),
                new Stop(innerStop, Color.rgb(255, 210, 85, 0.025)),
                new Stop(middleStop, Color.rgb(255, 220, 95, 0.12)),
                new Stop(1.0, Color.rgb(255, 205, 80, 0.0))));
        gc.fillOval(sunX - outer, sunY - outer, outer * 2.0, outer * 2.0);
    }

    private double habitableZoneScale(float sunMass) {
        double massRatio = Math.max(0.01, sunMass / SUN_MASS);
        double luminosityRatio = Math.pow(massRatio, 3.5);
        double radiusScale = Math.sqrt(luminosityRatio);
        return Math.clamp(radiusScale, HABITABLE_ZONE_MIN_SCALE, HABITABLE_ZONE_MAX_SCALE);
    }

    private void drawAsteroidBelt(GraphicsContext gc, float sunX, float sunY, int frameCounter) {
        double inner = screenRadiusForPhysicsDistance(physicalRadiusForAu(2.1f));
        double outer = screenRadiusForPhysicsDistance(physicalRadiusForAu(3.3f));
        gc.setStroke(Color.rgb(170, 150, 120, 0.20));
        gc.setLineWidth(1.0);
        gc.setLineDashes(1, 8);
        double wobble = frameCounter * 0.002;
        for (int i = 0; i < 80; i++) {
            double angle = i * Math.PI * 2.0 / 80.0 + wobble;
            double r = inner + (outer - inner) * ((i * 37) % 100) / 100.0;
            double x = sunX + Math.cos(angle) * r, y = sunY + Math.sin(angle) * r;
            gc.strokeLine(x, y, x + 0.1, y + 0.1);
        }
        gc.setLineDashes(null);
    }

    private void drawWeakSunGravityBoundary(GraphicsContext gc, int sunIndex) {
        float thresholdSimulationAcceleration = Math.max(
                simulationAccelerationForMetersPerSecondSquared(WEAK_SUN_GRAVITY_THRESHOLD_METERS_PER_SECOND_SQUARED),
                MIN_VISIBLE_WEAK_SUN_GRAVITY_SIMULATION_ACCELERATION);
        if (thresholdSimulationAcceleration <= 0.0f || mass[sunIndex] <= 0.0f) {
            return;
        }

        float boundaryRadius = (float) Math.sqrt(G * mass[sunIndex] / thresholdSimulationAcceleration);
        float sunPhysicsX = posX[sunIndex];
        float sunPhysicsY = posY[sunIndex];
        float sunPhysicsZ = posZ[sunIndex];
        float sunScreenX = projectedScreenX[sunIndex];
        float sunScreenY = projectedScreenY[sunIndex];
        ScreenPoint projectedSunPoint = projectPhysics(sunPhysicsX, sunPhysicsY, sunPhysicsZ);
        float visibleScreenRadius = visibleWeakGravityScreenRadius(sunScreenX, sunScreenY);
        float boundaryScreenRadius = screenRadiusForPhysicsDistance(boundaryRadius);
        if (boundaryScreenRadius > visibleScreenRadius) {
            boundaryRadius = physicsRadiusForScreenDistance(visibleScreenRadius);
        }

        gc.setStroke(Color.rgb(100, 190, 255, 0.34));
        gc.setLineWidth(1.1);
        gc.setLineDashes(10.0, 8.0);
        double previousX = 0.0;
        double previousY = 0.0;
        final int segments = 180;
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

    private float visibleWeakGravityScreenRadius(float sunScreenX, float sunScreenY) {
        float padding = 38.0f;
        float horizontalRadius = Math.max(60.0f, Math.min(sunScreenX - padding, (float) canvasWidth - sunScreenX - padding));
        float verticalRadius = Math.max(60.0f, Math.min(sunScreenY - padding, (float) canvasHeight - sunScreenY - padding));
        float maximumCenteredRadius = Math.min(horizontalRadius, verticalRadius);
        float fallbackRadius = (float) Math.min(canvasWidth, canvasHeight) * 0.43f;
        return Math.clamp(Math.min(maximumCenteredRadius, fallbackRadius), 60.0f, fallbackRadius);
    }

    private void drawPlanetRings(GraphicsContext gc, int bodyIndex, float screenX, float screenY, float depthScale) {
        String name = bodyNames[bodyIndex];
        if (name == null) {
            return;
        }

        if (name.startsWith("Saturn")) {
            drawSaturnRings(gc, screenX, screenY, radius[bodyIndex] * depthScale);
        } else if (name.startsWith("Uranus")) {
            drawUranusVerticalRing(gc, screenX, screenY, radius[bodyIndex] * depthScale);
        }
    }

    private void drawSaturnRings(GraphicsContext gc, float x, float y, float bodyRadius) {
        double outerWidth = bodyRadius * 4.8;
        double outerHeight = bodyRadius * 1.45;
        double middleWidth = bodyRadius * 4.0;
        double middleHeight = bodyRadius * 1.15;
        double innerWidth = bodyRadius * 3.2;
        double innerHeight = bodyRadius * 0.9;

        gc.setLineDashes();
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

        gc.setLineDashes();
        gc.setLineWidth(1.4);
        gc.setStroke(Color.rgb(158, 221, 232, 0.7));
        gc.strokeOval(x - ringWidth / 2.0, y - ringHeight / 2.0, ringWidth, ringHeight);

        gc.setLineWidth(0.8);
        gc.setStroke(Color.rgb(210, 245, 250, 0.45));
        gc.strokeOval(x - ringWidth * 0.72 / 2.0, y - ringHeight * 0.86 / 2.0, ringWidth * 0.72, ringHeight * 0.86);
    }

    private void updateDashboard() {
        dashboardList.getChildren().clear();
        for (int i = 0; i < bodyCount; i++) {
            if (activeState[i] == 0) continue;
            if (dashboardRows[i] == null) createDashboardRow(i);
            updateDashboardRow(i);
            dashboardList.getChildren().add(dashboardRows[i]);
        }
    }

    private void createDashboardRow(int i) {
        HBox row = new HBox(6);
        Label label = new Label();
        label.setStyle(String.format("-fx-text-fill: %s; -fx-font-family: monospace; -fx-font-size: 11px; -fx-padding: 2 0 4 0;", toHex(bodyColors[i])));
        Tooltip tooltip = new Tooltip();
        Tooltip.install(label, tooltip);
        label.setUserData(tooltip);
        HBox.setHgrow(label, Priority.ALWAYS);
        row.getChildren().add(label);
        dashboardLabels[i] = label;
        if (editableMass[i]) {
            GridPane grid = new GridPane();
            grid.setHgap(3);
            grid.setVgap(2);
            positionXFields[i] = editorField(); positionYFields[i] = editorField(); positionZFields[i] = editorField(); massFields[i] = editorField();
            velocityXFields[i] = editorField(); velocityYFields[i] = editorField(); velocityZFields[i] = editorField();
            addEditorField(grid, "X", positionXFields[i], 0, 0);
            addEditorField(grid, "Y", positionYFields[i], 1, 0);
            addEditorField(grid, "Z", positionZFields[i], 2, 0);
            addEditorField(grid, "M", massFields[i], 0, 1);
            addEditorField(grid, "Vx", velocityXFields[i], 1, 1);
            addEditorField(grid, "Vy", velocityYFields[i], 2, 1);
            addEditorField(grid, "Vz", velocityZFields[i], 3, 1);
            int bodyIndex = i;
            positionXFields[i].setOnAction(_ -> applyPositionFields(bodyIndex));
            positionYFields[i].setOnAction(_ -> applyPositionFields(bodyIndex));
            positionZFields[i].setOnAction(_ -> applyPositionFields(bodyIndex));
            massFields[i].setOnAction(_ -> applyMassField(bodyIndex));
            velocityXFields[i].setOnAction(_ -> applyVelocityFields(bodyIndex));
            velocityYFields[i].setOnAction(_ -> applyVelocityFields(bodyIndex));
            velocityZFields[i].setOnAction(_ -> applyVelocityFields(bodyIndex));
            row.getChildren().add(grid);
        }
        dashboardRows[i] = row;
    }

    private TextField editorField() {
        TextField field = new TextField();
        field.setPrefColumnCount(4);
        field.setStyle("-fx-font-family: monospace; -fx-font-size: 10px; -fx-padding: 1 3 1 3;");
        return field;
    }

    private void addEditorField(GridPane grid, String labelText, TextField field, int column, int row) {
        Label label = new Label(labelText + ":");
        label.setStyle("-fx-text-fill: #c8c8d8; -fx-font-family: monospace; -fx-font-size: 10px;");
        grid.add(label, column * 2, row);
        grid.add(field, column * 2 + 1, row);
    }

    private void updateDashboardRow(int i) {
        Label label = dashboardLabels[i];
        String nearestText = nearestText(i);
        if (editableMass[i]) {
            label.setText(String.format("%s | R: %.0f | A: %.3f%n%s", bodyNames[i], radius[i], dashboardAcceleration[i], nearestText));
            updateDirectionFields(i);
        } else {
            label.setText(String.format("%-10s | M:%8.2f | R:%4.1f | Sun:%7.3fAU",
                    bodyNames[i], mass[i], radius[i], dashboardMetrics[i * DASHBOARD_METRIC_STRIDE + DASHBOARD_DISTANCE_FROM_SUN_AU]));
        }
        ((Tooltip) label.getUserData()).setText(bodyDetailsText(i, nearestText));
    }

    private void updateDirectionFields(int i) {
        setField(positionXFields[i], "%.3f", physicsDistanceToAu(posX[i]));
        setField(positionYFields[i], "%.3f", physicsDistanceToAu(posY[i]));
        setField(positionZFields[i], "%.3f", physicsDistanceToAu(posZ[i]));
        setField(massFields[i], "%.3f", mass[i]);
        setField(velocityXFields[i], "%.3f", velX[i] * velocityConversion());
        setField(velocityYFields[i], "%.3f", velY[i] * velocityConversion());
        setField(velocityZFields[i], "%.3f", velZ[i] * velocityConversion());
    }

    private void setField(TextField field, String format, float value) {
        if (field != null && !field.isFocused()) field.setText(String.format(format, value));
    }

    private String bodyDetailsText(int i, String nearestText) {
        int base = i * DASHBOARD_METRIC_STRIDE;
        return String.format("""
                        %s | R: %.1f | A: %.6f
                        X: %.3f AU | Y: %.3f AU | Z: %.3f AU
                        %s
                        M: %.3f M_Earth | Sun: %.3f AU
                        Vx: %.3f | Vy: %.3f | Vz: %.3f km/s
                        Ax: %.6f | Ay: %.6f | Az: %.6f m/s2""",
                bodyNames[i], radius[i], dashboardAcceleration[i],
                physicsDistanceToAu(posX[i]), physicsDistanceToAu(posY[i]), physicsDistanceToAu(posZ[i]),
                nearestText, mass[i], dashboardMetrics[base + DASHBOARD_DISTANCE_FROM_SUN_AU],
                dashboardMetrics[base + DASHBOARD_VELOCITY_X_KILOMETERS_PER_SECOND],
                dashboardMetrics[base + DASHBOARD_VELOCITY_Y_KILOMETERS_PER_SECOND],
                dashboardMetrics[base + DASHBOARD_VELOCITY_Z_KILOMETERS_PER_SECOND],
                dashboardMetrics[base + DASHBOARD_ACCELERATION_X_METERS_PER_SECOND_SQUARED],
                dashboardMetrics[base + DASHBOARD_ACCELERATION_Y_METERS_PER_SECOND_SQUARED],
                dashboardMetrics[base + DASHBOARD_ACCELERATION_Z_METERS_PER_SECOND_SQUARED]);
    }

    private String nearestText(int i) {
        int nearest = dashboardNearestIndex[i];
        return nearest < 0 ? "Nearest: -" : String.format("Nearest: %s %.3fAU", bodyNames[nearest], physicsDistanceToAu(dashboardNearestDistance[i]));
    }

    private void applyPositionFields(int i) {
        Float x = parseField(positionXFields[i]), y = parseField(positionYFields[i]), z = parseField(positionZFields[i]);
        if (x == null || y == null || z == null) return;
        posX[i] = physicalRadiusForAu(x);
        posY[i] = physicalRadiusForAu(y);
        posZ[i] = physicalRadiusForAu(z);
        clearTrail(i);
        computeAccelerations();
        projectBodies();
        updateDashboard();
    }

    private void applyMassField(int i) {
        Float value = parseField(massFields[i]);
        if (value == null) return;
        mass[i] = Math.max(0.0f, value);
        radius[i] = radiusForCreatedMass(mass[i]);
        computeAccelerations();
        updateDashboard();
    }

    private void applyVelocityFields(int i) {
        Float vx = parseField(velocityXFields[i]), vy = parseField(velocityYFields[i]), vz = parseField(velocityZFields[i]);
        if (vx == null || vy == null || vz == null) return;
        velX[i] = vx / velocityConversion();
        velY[i] = vy / velocityConversion();
        velZ[i] = vz / velocityConversion();
        updateDashboard();
    }

    private Float parseField(TextField field) {
        try {
            return Float.parseFloat(field.getText().trim().replace(',', '.'));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void updateElapsedTime(long now) {
        if (elapsedTimeLabel != null && simulationStartNanos >= 0L) elapsedTimeLabel.setText("Time: " + ((now - simulationStartNanos) / 1_000_000_000L) + "s");
    }

    private void resetSystem() {
        clearArrays();
        customBodyCount = 0;
        simulationStartNanos = -1L;
        frameCounter = 0;
        addBody("Sun", 0, 0, 0, 0, 0, 0, SUN_MASS, 16, Color.GOLD, false);
        float[] angles = resetOrbitAngles();
        addKeplerPlanet("Mercury", MERCURY_AU, MERCURY_ECCENTRICITY, 0.055f, 3.0f, Color.GRAY, angles[0]);
        addKeplerPlanet("Venus", VENUS_AU, VENUS_ECCENTRICITY, 0.815f, 4.5f, Color.BEIGE, angles[1]);
        addKeplerPlanet("Earth", EARTH_AU, EARTH_ECCENTRICITY, 1.0f, 5.0f, Color.DODGERBLUE, angles[2]);
        addKeplerPlanet("Mars", MARS_AU, MARS_ECCENTRICITY, 0.107f, 4.0f, Color.INDIANRED, angles[3]);
        addKeplerPlanet("Jupiter", JUPITER_AU, JUPITER_ECCENTRICITY, 317.8f, 11.0f, Color.PERU, angles[4]);
        addKeplerPlanet("Saturn", SATURN_AU, SATURN_ECCENTRICITY, 95.2f, 9.0f, Color.BURLYWOOD, angles[5]);
        addKeplerPlanet("Uranus", URANUS_AU, URANUS_ECCENTRICITY, 14.5f, 7.0f, Color.LIGHTBLUE, angles[6]);
        addKeplerPlanet("Neptune", NEPTUNE_AU, NEPTUNE_ECCENTRICITY, 17.1f, 7.0f, Color.ROYALBLUE, angles[7]);
        float px = 0, py = 0, pz = 0;
        for (int i = 1; i < bodyCount; i++) {
            px += mass[i] * velX[i]; py += mass[i] * velY[i]; pz += mass[i] * velZ[i];
        }
        velX[0] = -px / mass[0]; velY[0] = -py / mass[0]; velZ[0] = -pz / mass[0];
        computeAccelerations();
        projectBodies();
        computeDashboardMetrics();
        updateDashboard();
    }

    private void clearArrays() {
        bodyCount = 0;
        for (int i = 0; i < MAX_BODIES; i++) {
            clearBodySlot(i);
            dashboardRows[i] = null; dashboardLabels[i] = null;
            positionXFields[i] = positionYFields[i] = positionZFields[i] = massFields[i] = velocityXFields[i] = velocityYFields[i] = velocityZFields[i] = null;
        }
    }

    private void clearBodySlot(int i) {
        posX[i] = posY[i] = posZ[i] = velX[i] = velY[i] = velZ[i] = accX[i] = accY[i] = accZ[i] = oldAccX[i] = oldAccY[i] = oldAccZ[i] = 0;
        mass[i] = radius[i] = 0;
        activeState[i] = 0;
        bodyNames[i] = null;
        bodyColors[i] = bodyTrailColors[i] = bodyLabelColors[i] = bodyOrbitColors[i] = Color.WHITE;
        editableMass[i] = false;
        clearTrail(i);
    }

    private void addEditableBody() {
        if (bodyCount >= MAX_BODIES) return;
        float[] spawn = rightEdgeSpawnPosition();
        addBody(String.format("Body #%d", ++customBodyCount), spawn[0], spawn[1], spawn[2], 0, 0, 0, 0, 3, Color.RED, true);
        computeAccelerations();
        projectBodies();
        computeDashboardMetrics();
        updateDashboard();
    }

    private float[] rightEdgeSpawnPosition() {
        float physicsDistance = physicsRadiusForScreenDistance((float) Math.max(1.0, canvasWidth * 0.5 - MAX_CREATED_BODY_RADIUS - 4.0));
        return new float[]{physicsDistance * (float) Math.cos(cameraYaw), 0.0f, physicsDistance * (float) Math.sin(cameraYaw)};
    }

    private float[] resetOrbitAngles() {
        float[] angles = new float[8];
        if (alignPlanetsOnReset) return angles;
        float base = (float) (Math.random() * Math.PI * 2.0);
        float jitterRange = (float) (Math.PI / 45.0);
        for (int i = 0; i < angles.length; i++) angles[i] = base + STABLE_ORBIT_PHASES[i] + (float) ((Math.random() * 2.0 - 1.0) * jitterRange);
        return angles;
    }

    private void addKeplerPlanet(String name, float au, float eccentricity, float planetMass, float size, Color color, float trueAnomaly) {
        float semiMajor = physicalRadiusForAu(au);
        float e = Math.clamp(eccentricity, 0, 0.95f);
        float p = semiMajor * (1.0f - e * e);
        float r = p / (1.0f + e * (float) Math.cos(trueAnomaly));
        float mu = G * (SUN_MASS + planetMass);
        float h = (float) Math.sqrt(mu * p);
        float x = r * (float) Math.cos(trueAnomaly), y = r * (float) Math.sin(trueAnomaly);
        float vx = -mu / h * (float) Math.sin(trueAnomaly);
        float vy = mu / h * (e + (float) Math.cos(trueAnomaly));
        addBody(name, x, y, 0, vx, vy, 0, planetMass, size, color, false);
    }

    private void addBody(String name, float x, float y, float z, float vx, float vy, float vz, float bodyMass, float bodyRadius, Color color, boolean editable) {
        if (bodyCount >= MAX_BODIES) return;
        int i = bodyCount++;
        bodyNames[i] = name;
        posX[i] = x; posY[i] = y; posZ[i] = z;
        velX[i] = vx; velY[i] = vy; velZ[i] = vz;
        mass[i] = bodyMass; radius[i] = bodyRadius;
        bodyColors[i] = color;
        bodyTrailColors[i] = color.deriveColor(0, 1, 1, 0.30);
        bodyLabelColors[i] = color.interpolate(Color.WHITE, 0.25);
        bodyOrbitColors[i] = color.deriveColor(0, 1, 1, 0.45);
        editableMass[i] = editable;
        activeState[i] = 1;
        clearTrail(i);
    }

    private void physicsStepVerlet(float dt) {
        float halfDtSq = 0.5f * dt * dt;
        for (int i = 0; i < bodyCount; i++) {
            if (activeState[i] == 0) continue;
            oldAccX[i] = accX[i]; oldAccY[i] = accY[i]; oldAccZ[i] = accZ[i];
            posX[i] += velX[i] * dt + accX[i] * halfDtSq;
            posY[i] += velY[i] * dt + accY[i] * halfDtSq;
            posZ[i] += velZ[i] * dt + accZ[i] * halfDtSq;
        }
        computeAccelerations();
        float halfDt = 0.5f * dt;
        for (int i = 0; i < bodyCount; i++) {
            if (activeState[i] == 0) continue;
            velX[i] += (oldAccX[i] + accX[i]) * halfDt;
            velY[i] += (oldAccY[i] + accY[i]) * halfDt;
            velZ[i] += (oldAccZ[i] + accZ[i]) * halfDt;
        }
    }

    private void computeAccelerations() {
        for (int i = 0; i < bodyCount; i++) accX[i] = accY[i] = accZ[i] = 0;
        for (int i = 0; i < bodyCount; i++) {
            if (activeState[i] == 0 || mass[i] <= 0) continue;
            float fx = 0, fy = 0, fz = 0;
            for (int j = 0; j < bodyCount; j++) {
                if (i == j || activeState[j] == 0 || mass[j] <= 0) continue;
                float dx = posX[j] - posX[i], dy = posY[j] - posY[i], dz = posZ[j] - posZ[i];
                float distSq = dx * dx + dy * dy + dz * dz + 35.0f;
                float dist = (float) Math.sqrt(distSq);
                float force = G * mass[i] * mass[j] / distSq;
                fx += force * dx / dist; fy += force * dy / dist; fz += force * dz / dist;
            }
            accX[i] = fx / mass[i]; accY[i] = fy / mass[i]; accZ[i] = fz / mass[i];
        }
    }

    private void resolveCollisions() {
        boolean merged = false;
        for (int i = 0; i < bodyCount; i++) {
            if (activeState[i] == 0 || mass[i] <= 0) continue;
            for (int j = i + 1; j < bodyCount; j++) {
                if (activeState[j] == 0 || mass[j] <= 0) continue;
                float dx = posX[j] - posX[i], dy = posY[j] - posY[i], dz = posZ[j] - posZ[i];
                if (dx * dx + dy * dy + dz * dz <= CENTER_COLLISION_EPSILON * CENTER_COLLISION_EPSILON) {
                    mergeBodies(i, j);
                    activeState[j] = 0;
                    clearTrail(j);
                    merged = true;
                }
            }
        }
        if (merged) {
            compactBodies();
            computeAccelerations();
        }
    }

    private void mergeBodies(int survivor, int absorbed) {
        float mergedMass = mass[survivor] + mass[absorbed];
        if (mergedMass <= 0) return;
        posX[survivor] = (posX[survivor] * mass[survivor] + posX[absorbed] * mass[absorbed]) / mergedMass;
        posY[survivor] = (posY[survivor] * mass[survivor] + posY[absorbed] * mass[absorbed]) / mergedMass;
        posZ[survivor] = (posZ[survivor] * mass[survivor] + posZ[absorbed] * mass[absorbed]) / mergedMass;
        velX[survivor] = (velX[survivor] * mass[survivor] + velX[absorbed] * mass[absorbed]) / mergedMass;
        velY[survivor] = (velY[survivor] * mass[survivor] + velY[absorbed] * mass[absorbed]) / mergedMass;
        velZ[survivor] = (velZ[survivor] * mass[survivor] + velZ[absorbed] * mass[absorbed]) / mergedMass;
        mass[survivor] = mergedMass;
        radius[survivor] = radiusForCreatedMass(mergedMass);
        bodyNames[survivor] = bodyNames[survivor] + "+";
        editableMass[survivor] = editableMass[survivor] || editableMass[absorbed];
    }

    private void compactBodies() {
        int write = 0;
        for (int read = 0; read < bodyCount; read++) {
            if (activeState[read] == 0) continue;
            if (write != read) copyBody(read, write);
            write++;
        }
        for (int i = write; i < bodyCount; i++) {
            clearBodySlot(i);
            dashboardRows[i] = null;
            dashboardLabels[i] = null;
        }
        bodyCount = write;
    }

    private void copyBody(int s, int t) {
        posX[t] = posX[s]; posY[t] = posY[s]; posZ[t] = posZ[s];
        velX[t] = velX[s]; velY[t] = velY[s]; velZ[t] = velZ[s];
        accX[t] = accX[s]; accY[t] = accY[s]; accZ[t] = accZ[s];
        mass[t] = mass[s]; radius[t] = radius[s]; activeState[t] = activeState[s];
        bodyNames[t] = bodyNames[s]; bodyColors[t] = bodyColors[s]; bodyTrailColors[t] = bodyTrailColors[s]; bodyLabelColors[t] = bodyLabelColors[s]; bodyOrbitColors[t] = bodyOrbitColors[s];
        editableMass[t] = editableMass[s];
        copyTrail(s, t);
        dashboardRows[t] = null; dashboardLabels[t] = null;
    }

    private void computeDashboardMetrics() {
        float vConv = velocityConversion(), aConv = accelerationConversion();
        for (int i = 0; i < bodyCount; i++) {
            dashboardNearestIndex[i] = -1;
            dashboardNearestDistance[i] = 0;
            int base = i * DASHBOARD_METRIC_STRIDE;
            for (int k = 0; k < DASHBOARD_METRIC_STRIDE; k++) dashboardMetrics[base + k] = 0;
            if (activeState[i] == 0) continue;
            dashboardSpeed[i] = length(velX[i], velY[i], velZ[i]) * vConv;
            dashboardAcceleration[i] = length(accX[i], accY[i], accZ[i]) * aConv;
            dashboardMetrics[base + DASHBOARD_VELOCITY_X_KILOMETERS_PER_SECOND] = velX[i] * vConv;
            dashboardMetrics[base + DASHBOARD_VELOCITY_Y_KILOMETERS_PER_SECOND] = velY[i] * vConv;
            dashboardMetrics[base + DASHBOARD_VELOCITY_Z_KILOMETERS_PER_SECOND] = velZ[i] * vConv;
            dashboardMetrics[base + DASHBOARD_ACCELERATION_X_METERS_PER_SECOND_SQUARED] = accX[i] * aConv;
            dashboardMetrics[base + DASHBOARD_ACCELERATION_Y_METERS_PER_SECOND_SQUARED] = accY[i] * aConv;
            dashboardMetrics[base + DASHBOARD_ACCELERATION_Z_METERS_PER_SECOND_SQUARED] = accZ[i] * aConv;
            dashboardMetrics[base + DASHBOARD_DISTANCE_FROM_SUN_AU] = i == 0 ? 0 : length(posX[i] - posX[0], posY[i] - posY[0], posZ[i] - posZ[0]) / PHYSICS_UNITS_PER_AU;
            float closestSq = Float.MAX_VALUE;
            for (int j = 0; j < bodyCount; j++) {
                if (i == j || activeState[j] == 0) continue;
                float dx = posX[j] - posX[i], dy = posY[j] - posY[i], dz = posZ[j] - posZ[i];
                float distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < closestSq) {
                    closestSq = distSq;
                    dashboardNearestIndex[i] = j;
                }
            }
            if (dashboardNearestIndex[i] >= 0) dashboardNearestDistance[i] = (float) Math.sqrt(closestSq);
        }
    }

    private void projectBodies() {
        for (int i = 0; i < bodyCount; i++) {
            ScreenPoint p = projectPhysics(posX[i], posY[i], posZ[i]);
            projectedScreenX[i] = p.x();
            projectedScreenY[i] = p.y();
            projectedDepthScale[i] = p.depthScale();
        }
    }

    private ScreenPoint projectPhysics(float physicsX, float physicsY, float physicsZ) {
        float cosYaw = (float) Math.cos(cameraYaw), sinYaw = (float) Math.sin(cameraYaw);
        float cosPitch = (float) Math.cos(cameraPitch), sinPitch = (float) Math.sin(cameraPitch);
        float yawX = physicsX * cosYaw + physicsZ * sinYaw;
        float yawZ = -physicsX * sinYaw + physicsZ * cosYaw;
        float viewX = yawX;
        float viewY = physicsY * cosPitch - yawZ * sinPitch;
        float viewZ = physicsY * sinPitch + yawZ * cosPitch;
        float physicsDistance = length(physicsX, physicsY, physicsZ);
        float scale = Math.clamp(1.0f + viewZ / DEPTH_SCALE_DENOMINATOR, 0.55f, 1.45f);
        if (physicsDistance <= 0.000001f) return new ScreenPoint((float) canvasWidth * 0.5f, (float) canvasHeight * 0.5f, scale);
        float projectedDistance = length(viewX, viewY, 0);
        if (projectedDistance <= 0.000001f) return new ScreenPoint((float) canvasWidth * 0.5f, (float) canvasHeight * 0.5f, scale);
        float screenDistance = screenRadiusForPhysicsDistance(physicsDistance);
        return new ScreenPoint((float) canvasWidth * 0.5f + viewX / projectedDistance * screenDistance * scale,
                (float) canvasHeight * 0.5f + viewY / projectedDistance * screenDistance * scale, scale);
    }

    private void appendTrailPoint(int bodyIndex) {
        int start = trailStart[bodyIndex], size = trailSize[bodyIndex];
        int slot = size < TRAIL_CAPACITY ? (start + size) % TRAIL_CAPACITY : start;
        if (size < TRAIL_CAPACITY) trailSize[bodyIndex] = size + 1; else trailStart[bodyIndex] = (start + 1) % TRAIL_CAPACITY;
        int index = trailIndex(bodyIndex, slot);
        trailX[index] = posX[bodyIndex]; trailY[index] = posY[bodyIndex]; trailZ[index] = posZ[bodyIndex];
        ScreenPoint p = projectPhysics(trailX[index], trailY[index], trailZ[index]);
        projectedTrailX[index] = p.x(); projectedTrailY[index] = p.y();
    }

    private void projectTrails() {
        for (int body = 0; body < bodyCount; body++) {
            for (int k = 0; k < trailSize[body]; k++) {
                int index = trailIndex(body, (trailStart[body] + k) % TRAIL_CAPACITY);
                ScreenPoint p = projectPhysics(trailX[index], trailY[index], trailZ[index]);
                projectedTrailX[index] = p.x(); projectedTrailY[index] = p.y();
            }
        }
    }

    private int trailIndex(int bodyIndex, int trailSlot) {
        return bodyIndex * TRAIL_CAPACITY + trailSlot;
    }

    private void clearAllTrails() {
        for (int i = 0; i < MAX_BODIES; i++) clearTrail(i);
    }

    private void clearTrail(int bodyIndex) {
        trailStart[bodyIndex] = 0;
        trailSize[bodyIndex] = 0;
    }

    private void copyTrail(int source, int target) {
        trailStart[target] = trailStart[source];
        trailSize[target] = trailSize[source];
        int sBase = source * TRAIL_CAPACITY, tBase = target * TRAIL_CAPACITY;
        for (int slot = 0; slot < TRAIL_CAPACITY; slot++) {
            trailX[tBase + slot] = trailX[sBase + slot]; trailY[tBase + slot] = trailY[sBase + slot]; trailZ[tBase + slot] = trailZ[sBase + slot];
            projectedTrailX[tBase + slot] = projectedTrailX[sBase + slot]; projectedTrailY[tBase + slot] = projectedTrailY[sBase + slot];
        }
    }

    private float screenRadiusForPhysicsDistance(float physicsDistance) {
        float au = physicsDistanceToAu(physicsDistance);
        return au <= MERCURY_AU ? MIN_PLANET_ORBIT_RADIUS * au / MERCURY_AU : orbitRadiusForAu(au);
    }

    private float physicsRadiusForScreenDistance(float screenDistance) {
        if (screenDistance <= MIN_PLANET_ORBIT_RADIUS) return physicalRadiusForAu(MERCURY_AU) * screenDistance / MIN_PLANET_ORBIT_RADIUS;
        float maxOrbitRadius = Math.max(MIN_PLANET_ORBIT_RADIUS + 1.0f, (float) Math.min(canvasWidth, canvasHeight) * 0.5f - ORBIT_EDGE_PADDING);
        float normalized = Math.clamp((screenDistance - MIN_PLANET_ORBIT_RADIUS) / (maxOrbitRadius - MIN_PLANET_ORBIT_RADIUS), 0, 1);
        float minLog = (float) Math.log(MERCURY_AU), maxLog = (float) Math.log(NEPTUNE_AU);
        return physicalRadiusForAu((float) Math.exp(minLog + normalized * (maxLog - minLog)));
    }

    private float orbitRadiusForAu(float au) {
        float minLog = (float) Math.log(MERCURY_AU), maxLog = (float) Math.log(NEPTUNE_AU);
        float normalized = ((float) Math.log(Math.max(MERCURY_AU, au)) - minLog) / (maxLog - minLog);
        float maxOrbitRadius = Math.max(MIN_PLANET_ORBIT_RADIUS + 1.0f, (float) Math.min(canvasWidth, canvasHeight) * 0.5f - ORBIT_EDGE_PADDING);
        return MIN_PLANET_ORBIT_RADIUS + normalized * (maxOrbitRadius - MIN_PLANET_ORBIT_RADIUS);
    }

    private float physicalRadiusForAu(float au) {
        return au * PHYSICS_UNITS_PER_AU;
    }

    private float physicsDistanceToAu(float physicsDistance) {
        return physicsDistance / PHYSICS_UNITS_PER_AU;
    }

    private float velocityConversion() {
        return EARTH_ORBITAL_SPEED_KM_PER_SECOND / (float) Math.sqrt(G * (SUN_MASS + 1.0f) / physicalRadiusForAu(EARTH_AU));
    }

    private float accelerationConversion() {
        float realSeconds = realSecondsPerSimulationSecond();
        return (ASTRONOMICAL_UNIT_KM / PHYSICS_UNITS_PER_AU) / (realSeconds * realSeconds) * 1000.0f;
    }

    private float simulationAccelerationForMetersPerSecondSquared(float metersPerSecondSquared) {
        float realSeconds = realSecondsPerSimulationSecond();
        float kilometersPerSimulationUnit = ASTRONOMICAL_UNIT_KM / PHYSICS_UNITS_PER_AU;
        return metersPerSecondSquared / 1000.0f * (realSeconds * realSeconds) / kilometersPerSimulationUnit;
    }

    private float realSecondsPerSimulationSecond() {
        float earthSpeed = (float) Math.sqrt(G * (SUN_MASS + 1.0f) / physicalRadiusForAu(EARTH_AU));
        return (EARTH_ORBITAL_SPEED_KM_PER_SECOND / earthSpeed) / (ASTRONOMICAL_UNIT_KM / PHYSICS_UNITS_PER_AU);
    }

    private float radiusForCreatedMass(float bodyMass) {
        return Math.clamp(3.0f + (float) Math.pow(Math.max(0.0f, bodyMass), 1.0 / 3.0) * 1.5f, 3.0f, MAX_CREATED_BODY_RADIUS);
    }

    private float length(float x, float y, float z) {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X", (int) Math.round(color.getRed() * 255), (int) Math.round(color.getGreen() * 255), (int) Math.round(color.getBlue() * 255));
    }

    static void main(String[] args) {
        launch(args);
    }

    private record ScreenPoint(float x, float y, float depthScale) {
    }

    private record SpherePaint(RadialGradient gradient, Color rim) {
    }
}
