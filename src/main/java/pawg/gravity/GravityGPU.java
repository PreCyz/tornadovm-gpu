package pawg.gravity;

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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.util.ArrayList;
import java.util.List;

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
    private static final float VELOCITY_SCALE = 0.085f;
    private static final float MAX_CREATED_BODY_RADIUS = 20.0f;
    private static final float CENTER_COLLISION_EPSILON = 0.5f;
    private static final int GPU_SUB_STEPS = 12;
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
    private final FloatArray velX = new FloatArray(MAX_BODIES);
    private final FloatArray velY = new FloatArray(MAX_BODIES);
    private final FloatArray nextPosX = new FloatArray(MAX_BODIES);
    private final FloatArray nextPosY = new FloatArray(MAX_BODIES);
    private final FloatArray nextVelX = new FloatArray(MAX_BODIES);
    private final FloatArray nextVelY = new FloatArray(MAX_BODIES);
    private final FloatArray accX = new FloatArray(MAX_BODIES);
    private final FloatArray accY = new FloatArray(MAX_BODIES);
    private final FloatArray nextAccX = new FloatArray(MAX_BODIES);
    private final FloatArray nextAccY = new FloatArray(MAX_BODIES);
    private final FloatArray mass = new FloatArray(MAX_BODIES);
    private final FloatArray radius = new FloatArray(MAX_BODIES);
    private final FloatArray dashboardSpeed = new FloatArray(MAX_BODIES);
    private final FloatArray dashboardAcceleration = new FloatArray(MAX_BODIES);
    private final FloatArray dashboardNearestDistance = new FloatArray(MAX_BODIES);
    private final IntArray activeState = new IntArray(MAX_BODIES);
    private final IntArray collisionTarget = new IntArray(MAX_BODIES);
    private final IntArray dashboardNearestIndex = new IntArray(MAX_BODIES);

    private final FloatArray physParams = new FloatArray(2);
    private final IntArray simulationState = new IntArray(1);

    private final String[] bodyNames = new String[MAX_BODIES];
    private final Color[] bodyColors = new Color[MAX_BODIES];
    private final boolean[] editableMass = new boolean[MAX_BODIES];
    private final float[] orbitSemiMajorAu = new float[MAX_BODIES];
    private final float[] orbitEccentricity = new float[MAX_BODIES];
    private final HBox[] dashboardRows = new HBox[MAX_BODIES];
    private final Label[] dashboardLabels = new Label[MAX_BODIES];
    private final TextField[] massFields = new TextField[MAX_BODIES];
    private final TextField[] velocityFields = new TextField[MAX_BODIES];
    private final List<List<Float>> trailX = new ArrayList<>();
    private final List<List<Float>> trailY = new ArrayList<>();

    private int bodyCount = 0;

    private TornadoExecutionPlan executionPlan;

    private enum CreationState { IDLE, SIZING_MASS, SELECTING_VECTOR }
    private CreationState creationState = CreationState.IDLE;

    private float clickX, clickY;
    private float createdMass = 1.0f;
    private float createdRadius = 5.0f;
    private float vectorEndX, vectorEndY;
    private int customBodyCount = 0;
    private boolean showHabitableZone = false;
    private boolean showAsteroidBelt = false;
    private boolean alignPlanetsOnReset = false;
    private boolean showOrbitGuides = false;

    private final VBox dashboardList = new VBox(6);
    private Label elapsedTimeLabel;
    private double canvasWidth = CANVAS_WIDTH;
    private double canvasHeight = HEIGHT;
    private long simulationStartNanos = -1L;

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

        for (int i = 0; i < MAX_BODIES; i++) {
            trailX.add(new ArrayList<>());
            trailY.add(new ArrayList<>());
        }

        physParams.set(0, G);
        physParams.set(1, DT);

        resetSystem();
        initTornadoPlanOnce();

        canvas.setOnMousePressed(event -> {
            if (creationState == CreationState.IDLE && bodyCount < MAX_BODIES) {
                clickX = (float) event.getX();
                clickY = (float) event.getY();
                createdMass = 1.0f;
                createdRadius = 5.0f;
                creationState = CreationState.SIZING_MASS;
            } else if (creationState == CreationState.SELECTING_VECTOR) {
                float dx = (float) event.getX() - clickX;
                float dy = (float) event.getY() - clickY;
                float velocityScale = physicsUnitsPerScreenPixelAtEarthOrbit() * VELOCITY_SCALE;

                customBodyCount++;
                addBody(
                        String.format("Body #%d", customBodyCount),
                        physicsXForScreen(clickX, clickY), physicsYForScreen(clickX, clickY),
                        dx * velocityScale, dy * velocityScale,
                        createdMass, createdRadius, Color.RED, true
                );

                creationState = CreationState.IDLE;
            }
        });

        canvas.setOnMouseDragged(event -> {
            if (creationState == CreationState.SIZING_MASS) {
                float dx = (float) event.getX() - clickX;
                float dy = (float) event.getY() - clickY;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);

                createdMass = (float) Math.max(0.1, 1.0 + Math.pow(dist / 4.0, 1.8));
                createdRadius = radiusForCreatedMass(createdMass);
            }
        });

        canvas.setOnMouseReleased(event -> {
            if (creationState == CreationState.SIZING_MASS) {
                vectorEndX = (float) event.getX();
                vectorEndY = (float) event.getY();
                creationState = CreationState.SELECTING_VECTOR;
            }
        });

        canvas.setOnMouseMoved(event -> {
            if (creationState == CreationState.SELECTING_VECTOR) {
                vectorEndX = (float) event.getX();
                vectorEndY = (float) event.getY();
            }
        });

        VBox sidebar = new VBox(10);
        sidebar.setStyle(String.format("-fx-background-color: #111118; -fx-padding: 15; -fx-min-width: %dpx; -fx-pref-width: %dpx; -fx-border-color: #333344; -fx-border-width: 0 0 0 1;", SIDEBAR_WIDTH, SIDEBAR_WIDTH));

        Label title = new Label("NBody DASHBOARD");
        title.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-font-size: 13px;");

        Button btnReset = new Button("RESET (SPACE)");
        btnReset.setStyle("-fx-background-color: #222; -fx-text-fill: #ff4444; -fx-border-color: #ff4444; -fx-font-weight: bold; -fx-cursor: hand;");
        btnReset.setFocusTraversable(false);
        btnReset.setOnAction(_ -> resetSystem());

        elapsedTimeLabel = new Label("Time: 0s");
        elapsedTimeLabel.setStyle("-fx-text-fill: #00ff88; -fx-font-family: monospace; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 0 0 0;");

        HBox resetRow = new HBox(12, btnReset, elapsedTimeLabel);
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

        GridPane optionsGrid = new GridPane();
        optionsGrid.setHgap(12);
        optionsGrid.setVgap(4);
        optionsGrid.add(alignPlanetsCheckbox, 0, 0);
        optionsGrid.add(orbitGuidesCheckbox, 1, 0);

        CheckBox habitableZoneCheckbox = new CheckBox("Show golden belt");
        habitableZoneCheckbox.setSelected(false);
        habitableZoneCheckbox.setFocusTraversable(false);
        habitableZoneCheckbox.setStyle("-fx-text-fill: #ffd76a; -fx-font-family: monospace; -fx-font-size: 11px;");
        habitableZoneCheckbox.selectedProperty().addListener((_, _, selected) -> showHabitableZone = selected);

        CheckBox asteroidBeltCheckbox = new CheckBox("Show asteroid belt");
        asteroidBeltCheckbox.setSelected(false);
        asteroidBeltCheckbox.setFocusTraversable(false);
        asteroidBeltCheckbox.setStyle("-fx-text-fill: #b67a42; -fx-font-family: monospace; -fx-font-size: 11px;");
        asteroidBeltCheckbox.selectedProperty().addListener((_, _, selected) -> showAsteroidBelt = selected);

        Label legend = new Label("""
                M = mass [M_Earth]
                R = body radius [px]
                V = speed [km/s eq]
                A = acceleration [m/s² eq]
                X/Y = physical position [AU]
                Nearest = closest body and distance [AU]""");
        legend.setStyle("-fx-text-fill: #b8b8c8; -fx-font-family: monospace; -fx-font-size: 11px; -fx-padding: 0 0 6 0;");

        sidebar.getChildren().addAll(title, resetRow, optionsGrid, habitableZoneCheckbox, asteroidBeltCheckbox, legend, dashboardList);

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setRight(sidebar);

        Scene scene = new Scene(root, screenBounds.getWidth(), screenBounds.getHeight(), Color.BLACK);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE) {
                resetSystem();
            }
        });

        primaryStage.setTitle("N-Body Gravity Simulator");
        primaryStage.setScene(scene);
        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());
        primaryStage.setResizable(false);
        primaryStage.show();

        AnimationTimer timer = new AnimationTimer() {
            private int frameCounter = 0;

            @Override
            public void handle(long now) {
                if (simulationStartNanos < 0) {
                    simulationStartNanos = now;
                }
                elapsedTimeLabel.setText("Time: " + formatElapsedTime((now - simulationStartNanos) / 1_000_000_000L));

                updateSimulationState();
                executionPlan.execute();
                resolveCollisions();

                frameCounter++;
                if (frameCounter % 2 == 0) {
                    for (int i = 0; i < bodyCount; i++) {
                        trailX.get(i).add(screenXForPhysics(posX.get(i), posY.get(i)));
                        trailY.get(i).add(screenYForPhysics(posX.get(i), posY.get(i)));
                        if (trailX.get(i).size() > 180) {
                            trailX.get(i).removeFirst();
                            trailY.get(i).removeFirst();
                        }
                    }
                }

                if (frameCounter % 5 == 0) {
                    updateDashboard();
                }

                gc.setFill(Color.rgb(3, 3, 10, 0.35));
                gc.fillRect(0, 0, canvasWidth, canvasHeight);
                drawSolarBelts(gc, frameCounter);
                drawOrbitGuides(gc);

                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.BOTTOM);
                gc.setFont(Font.font("SansSerif", 11));

                for (int i = 0; i < bodyCount; i++) {
                    float screenX = screenXForPhysics(posX.get(i), posY.get(i));
                    float screenY = screenYForPhysics(posX.get(i), posY.get(i));
                    gc.setStroke(bodyColors[i].deriveColor(0, 1, 1, 0.3));
                    gc.setLineWidth(1.0);
                    List<Float> tx = trailX.get(i);
                    List<Float> ty = trailY.get(i);
                    for (int k = 0; k < tx.size() - 1; k++) {
                        gc.strokeLine(tx.get(k), ty.get(k), tx.get(k+1), ty.get(k+1));
                    }

                    drawPlanetRings(gc, i);

                    gc.setFill(bodyColors[i]);
                    gc.fillOval(screenX - radius.get(i), screenY - radius.get(i), radius.get(i) * 2, radius.get(i) * 2);

                    gc.setFill(bodyColors[i].deriveColor(0, 0.7, 1.2, 0.9));
                    gc.fillText(bodyNames[i], screenX, screenY - radius.get(i) - 4);
                }

                if (creationState == CreationState.SIZING_MASS) {
                    gc.setFill(Color.RED.deriveColor(0, 1, 1, 0.7));
                    gc.fillOval(clickX - createdRadius, clickY - createdRadius, createdRadius * 2, createdRadius * 2);

                    gc.setStroke(Color.WHITE);
                    gc.setLineWidth(1.0);
                    gc.strokeOval(clickX - createdRadius, clickY - createdRadius, createdRadius * 2, createdRadius * 2);

                    gc.setTextAlign(TextAlignment.LEFT);
                    gc.setTextBaseline(VPos.CENTER);
                    gc.setFill(Color.WHITE);
                    gc.fillText(String.format("Masa: %.1f M_Earth", createdMass), clickX + createdRadius + 10, clickY);
                } else if (creationState == CreationState.SELECTING_VECTOR) {
                    gc.setFill(Color.RED);
                    gc.fillOval(clickX - createdRadius, clickY - createdRadius, createdRadius * 2, createdRadius * 2);

                    float dx = vectorEndX - clickX;
                    float dy = vectorEndY - clickY;
                    float vectorLength = (float) Math.sqrt(dx * dx + dy * dy);
                    float previewSpeed = speedToKilometersPerSecond(vectorLength * physicsUnitsPerScreenPixelAtEarthOrbit() * VELOCITY_SCALE);

                    gc.setStroke(Color.RED);
                    gc.setLineWidth(2.0);
                    gc.strokeLine(clickX, clickY, vectorEndX, vectorEndY);

                    gc.setTextAlign(TextAlignment.LEFT);
                    gc.setTextBaseline(VPos.CENTER);
                    gc.setFill(Color.WHITE);
                    gc.fillText(String.format("Masa: %.1f M_Earth | V: %.2f km/s", createdMass, previewSpeed), vectorEndX + 10, vectorEndY);
                }
            }
        };
        timer.start();
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
            gc.setStroke(orbitColor.deriveColor(0, 0.8, 1.3, 0.28));
            drawEllipticalOrbitGuide(gc, sunIndex, i);
        }

        gc.setLineDashes();
    }

    private void drawEllipticalOrbitGuide(GraphicsContext gc, int sunIndex, int bodyIndex) {
        final int segments = 144;
        float semiMajorAxis = physicalRadiusForAu(orbitSemiMajorAu[bodyIndex]);
        float eccentricity = orbitEccentricity[bodyIndex];
        float semiLatusRectum = semiMajorAxis * (1.0f - eccentricity * eccentricity);
        float sunPhysicsX = posX.get(sunIndex);
        float sunPhysicsY = posY.get(sunIndex);

        double previousX = 0.0;
        double previousY = 0.0;
        for (int segment = 0; segment <= segments; segment++) {
            double trueAnomaly = Math.PI * 2.0 * segment / segments;
            double radiusFromFocus = semiLatusRectum / (1.0 + eccentricity * Math.cos(trueAnomaly));
            float physicsX = (float) (sunPhysicsX + radiusFromFocus * Math.cos(trueAnomaly));
            float physicsY = (float) (sunPhysicsY + radiusFromFocus * Math.sin(trueAnomaly));
            double screenX = screenXForPhysics(physicsX, physicsY);
            double screenY = screenYForPhysics(physicsX, physicsY);

            if (segment > 0) {
                gc.strokeLine(previousX, previousY, screenX, screenY);
            }
            previousX = screenX;
            previousY = screenY;
        }
    }

    private void drawSolarBelts(GraphicsContext gc, int frameCounter) {
        if (!showHabitableZone && !showAsteroidBelt) {
            return;
        }

        int sunIndex = findSunIndex();
        if (sunIndex < 0) {
            return;
        }

        float sunX = screenXForPhysics(posX.get(sunIndex), posY.get(sunIndex));
        float sunY = screenYForPhysics(posX.get(sunIndex), posY.get(sunIndex));
        float sunMass = mass.get(sunIndex);
        if (showHabitableZone) {
            drawHabitableZone(gc, sunX, sunY, sunMass);
        }
        if (showAsteroidBelt) {
            drawAsteroidBelt(gc, sunX, sunY, frameCounter);
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
        double zoneRadius = (innerRadius + outerRadius) / 2.0;
        double zoneWidth = outerRadius - innerRadius;
        double diameter = zoneRadius * 2.0;

        gc.setStroke(Color.rgb(255, 190, 55, 0.025));
        gc.setLineWidth(zoneWidth * 0.65);
        gc.strokeOval(sunX - zoneRadius, sunY - zoneRadius, diameter, diameter);

        gc.setStroke(Color.rgb(255, 215, 90, 0.07));
        gc.setLineWidth(zoneWidth * 0.25);
        gc.strokeOval(sunX - zoneRadius, sunY - zoneRadius, diameter, diameter);

        gc.setStroke(Color.rgb(255, 235, 160, 0.16));
        gc.setLineWidth(0.8);
        gc.strokeOval(sunX - innerRadius, sunY - innerRadius, innerRadius * 2.0, innerRadius * 2.0);
        gc.strokeOval(sunX - outerRadius, sunY - outerRadius, outerRadius * 2.0, outerRadius * 2.0);
    }

    private double habitableZoneScale(float sunMass) {
        double massRatio = Math.max(0.01, sunMass / SUN_MASS);
        double luminosityRatio = Math.pow(massRatio, 3.5);
        double radiusScale = Math.sqrt(luminosityRatio);
        return Math.max(HABITABLE_ZONE_MIN_SCALE, Math.min(HABITABLE_ZONE_MAX_SCALE, radiusScale));
    }

    private float screenXForPhysics(float physicsX, float physicsY) {
        float physicsDistance = (float) Math.sqrt(physicsX * physicsX + physicsY * physicsY);
        if (physicsDistance <= 0.000001f) {
            return (float) (canvasWidth / 2.0);
        }

        float screenDistance = screenRadiusForPhysicsDistance(physicsDistance);
        return (float) (canvasWidth / 2.0 + (physicsX / physicsDistance) * screenDistance);
    }

    private float screenYForPhysics(float physicsX, float physicsY) {
        float physicsDistance = (float) Math.sqrt(physicsX * physicsX + physicsY * physicsY);
        if (physicsDistance <= 0.000001f) {
            return (float) (canvasHeight / 2.0);
        }

        float screenDistance = screenRadiusForPhysicsDistance(physicsDistance);
        return (float) (canvasHeight / 2.0 + (physicsY / physicsDistance) * screenDistance);
    }

    private float physicsXForScreen(float screenX, float screenY) {
        float dx = (float) (screenX - canvasWidth / 2.0);
        float dy = (float) (screenY - canvasHeight / 2.0);
        float screenDistance = (float) Math.sqrt(dx * dx + dy * dy);
        if (screenDistance <= 0.000001f) {
            return 0.0f;
        }

        float physicsDistance = physicsRadiusForScreenDistance(screenDistance);
        return dx / screenDistance * physicsDistance;
    }

    private float physicsYForScreen(float screenX, float screenY) {
        float dx = (float) (screenX - canvasWidth / 2.0);
        float dy = (float) (screenY - canvasHeight / 2.0);
        float screenDistance = (float) Math.sqrt(dx * dx + dy * dy);
        if (screenDistance <= 0.000001f) {
            return 0.0f;
        }

        float physicsDistance = physicsRadiusForScreenDistance(screenDistance);
        return dy / screenDistance * physicsDistance;
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
        double normalized = (screenDistance - MIN_PLANET_ORBIT_RADIUS) / (maxOrbitRadius - MIN_PLANET_ORBIT_RADIUS);
        normalized = Math.max(0.0, Math.min(1.0, normalized));
        double minLog = Math.log(MERCURY_AU);
        double maxLog = Math.log(NEPTUNE_AU);
        return physicalRadiusForAu((float) Math.exp(minLog + normalized * (maxLog - minLog)));
    }

    private float physicsUnitsPerScreenPixelAtEarthOrbit() {
        return physicalRadiusForAu(EARTH_AU) / orbitRadiusForAu(EARTH_AU);
    }

    private float physicsDistanceToAu(float physicsDistance) {
        return physicsDistance / PHYSICS_UNITS_PER_AU;
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

    private void drawPlanetRings(GraphicsContext gc, int bodyIndex) {
        String name = bodyNames[bodyIndex];
        if (name == null) {
            return;
        }

        if (name.startsWith("Saturn")) {
            drawSaturnRings(gc, screenXForPhysics(posX.get(bodyIndex), posY.get(bodyIndex)),
                    screenYForPhysics(posX.get(bodyIndex), posY.get(bodyIndex)), radius.get(bodyIndex));
        } else if (name.startsWith("Uranus")) {
            drawUranusVerticalRing(gc, screenXForPhysics(posX.get(bodyIndex), posY.get(bodyIndex)),
                    screenYForPhysics(posX.get(bodyIndex), posY.get(bodyIndex)), radius.get(bodyIndex));
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
        if (GPU_SUB_STEPS % 2 != 0) {
            throw new IllegalStateException("GPU_SUB_STEPS must be even so final Verlet buffers are posX/posY/velX/velY.");
        }

        TaskGraph taskGraph = new TaskGraph("nbody")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, posX, posY, velX, velY, mass, activeState, physParams, simulationState)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, nextPosX, nextPosY, nextVelX, nextVelY, accX, accY, nextAccX, nextAccY)
                .task("clearCollisionTargets", PhysicsKernels::clearCollisionTargets, collisionTarget, simulationState)
                .task("computeInitialAccelerations", PhysicsKernels::computeAccelerations,
                        posX, posY, accX, accY, mass, activeState, physParams, simulationState);

        for (int step = 0; step < GPU_SUB_STEPS; step++) {
            boolean evenStep = step % 2 == 0;
            FloatArray sourcePosX = evenStep ? posX : nextPosX;
            FloatArray sourcePosY = evenStep ? posY : nextPosY;
            FloatArray sourceVelX = evenStep ? velX : nextVelX;
            FloatArray sourceVelY = evenStep ? velY : nextVelY;
            FloatArray sourceAccX = evenStep ? accX : nextAccX;
            FloatArray sourceAccY = evenStep ? accY : nextAccY;
            FloatArray targetPosX = evenStep ? nextPosX : posX;
            FloatArray targetPosY = evenStep ? nextPosY : posY;
            FloatArray targetVelX = evenStep ? nextVelX : velX;
            FloatArray targetVelY = evenStep ? nextVelY : velY;
            FloatArray targetAccX = evenStep ? nextAccX : accX;
            FloatArray targetAccY = evenStep ? nextAccY : accY;

            taskGraph
                    .task("integrateVerletPosition" + step, PhysicsKernels::integrateVerletPosition,
                            sourcePosX, sourcePosY, sourceVelX, sourceVelY, sourceAccX, sourceAccY,
                            targetPosX, targetPosY, activeState, physParams, simulationState)
                    .task("computeTargetAccelerations" + step, PhysicsKernels::computeAccelerations,
                            targetPosX, targetPosY, targetAccX, targetAccY,
                            mass, activeState, physParams, simulationState)
                    .task("integrateVerletVelocity" + step, PhysicsKernels::integrateVerletVelocity,
                            sourceVelX, sourceVelY, sourceAccX, sourceAccY,
                            targetVelX, targetVelY, targetAccX, targetAccY,
                            activeState, physParams, simulationState)
                    .task("detectCollisions" + step, PhysicsKernels::detectCollisions,
                            targetPosX, targetPosY, activeState, collisionTarget, CENTER_COLLISION_EPSILON, simulationState);
        }

        taskGraph
                .task("computeDashboardMetrics", PhysicsKernels::computeDashboardMetrics,
                        posX, posY, velX, velY, accX, accY, activeState,
                        dashboardSpeed, dashboardAcceleration, dashboardNearestIndex, dashboardNearestDistance, simulationState)
                .transferToHost(DataTransferMode.EVERY_EXECUTION,
                        posX, posY, velX, velY, collisionTarget,
                        dashboardSpeed, dashboardAcceleration, dashboardNearestIndex, dashboardNearestDistance);

        executionPlan = new TornadoExecutionPlan(taskGraph.snapshot());
    }

    private void updateSimulationState() {
        simulationState.set(0, bodyCount);
    }

    private void resolveCollisions() {
        boolean mergedAny = false;
        for (int i = 0; i < bodyCount; i++) {
            int j = collisionTarget.get(i);
            if (j >= 0 && j < bodyCount && i < j && activeState.get(i) == 1 && activeState.get(j) == 1) {
                mergeBodies(i, j);
                mergedAny = true;
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
        float mergedVx = (velX.get(firstIndex) * firstMass + velX.get(secondIndex) * secondMass) / mergedMass;
        float mergedVy = (velY.get(firstIndex) * firstMass + velY.get(secondIndex) * secondMass) / mergedMass;

        boolean keepFirst = firstMass >= secondMass;
        int survivor = keepFirst ? firstIndex : secondIndex;
        int absorbed = keepFirst ? secondIndex : firstIndex;

        posX.set(survivor, mergedX);
        posY.set(survivor, mergedY);
        velX.set(survivor, mergedVx);
        velY.set(survivor, mergedVy);
        accX.set(survivor, 0.0f);
        accY.set(survivor, 0.0f);
        nextAccX.set(survivor, 0.0f);
        nextAccY.set(survivor, 0.0f);
        mass.set(survivor, mergedMass);
        radius.set(survivor, radiusForCreatedMass(mergedMass));
        dashboardSpeed.set(survivor, (float) Math.sqrt(mergedVx * mergedVx + mergedVy * mergedVy));
        dashboardAcceleration.set(survivor, 0.0f);
        dashboardNearestDistance.set(survivor, 0.0f);
        dashboardNearestIndex.set(survivor, -1);
        bodyNames[survivor] = bodyNames[survivor] + "+";
        editableMass[survivor] = editableMass[survivor] || editableMass[absorbed];
        activeState.set(absorbed, 0);
        trailX.get(absorbed).clear();
        trailY.get(absorbed).clear();
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
        velX.set(target, velX.get(source));
        velY.set(target, velY.get(source));
        accX.set(target, accX.get(source));
        accY.set(target, accY.get(source));
        nextAccX.set(target, nextAccX.get(source));
        nextAccY.set(target, nextAccY.get(source));
        mass.set(target, mass.get(source));
        radius.set(target, radius.get(source));
        dashboardSpeed.set(target, dashboardSpeed.get(source));
        dashboardAcceleration.set(target, dashboardAcceleration.get(source));
        dashboardNearestDistance.set(target, dashboardNearestDistance.get(source));
        dashboardNearestIndex.set(target, dashboardNearestIndex.get(source));
        bodyColors[target] = bodyColors[source];
        editableMass[target] = editableMass[source];
        orbitSemiMajorAu[target] = orbitSemiMajorAu[source];
        orbitEccentricity[target] = orbitEccentricity[source];
        activeState.set(target, 1);
        collisionTarget.set(target, -1);

        trailX.get(target).clear();
        trailX.get(target).addAll(trailX.get(source));
        trailY.get(target).clear();
        trailY.get(target).addAll(trailY.get(source));
        dashboardRows[target] = null;
        dashboardLabels[target] = null;
        massFields[target] = null;
        velocityFields[target] = null;
    }

    private void clearBodySlot(int i) {
        bodyNames[i] = null;
        posX.set(i, 0.0f);
        posY.set(i, 0.0f);
        velX.set(i, 0.0f);
        velY.set(i, 0.0f);
        accX.set(i, 0.0f);
        accY.set(i, 0.0f);
        nextAccX.set(i, 0.0f);
        nextAccY.set(i, 0.0f);
        mass.set(i, 0.0f);
        radius.set(i, 0.0f);
        dashboardSpeed.set(i, 0.0f);
        dashboardAcceleration.set(i, 0.0f);
        dashboardNearestDistance.set(i, 0.0f);
        dashboardNearestIndex.set(i, -1);
        bodyColors[i] = null;
        editableMass[i] = false;
        orbitSemiMajorAu[i] = 0.0f;
        orbitEccentricity[i] = 0.0f;
        activeState.set(i, 0);
        collisionTarget.set(i, -1);
        trailX.get(i).clear();
        trailY.get(i).clear();
        dashboardRows[i] = null;
        dashboardLabels[i] = null;
        massFields[i] = null;
        velocityFields[i] = null;
    }

    private void updateDashboard() {
        while (dashboardList.getChildren().size() > bodyCount) {
            dashboardList.getChildren().removeLast();
        }

        for (int i = 0; i < bodyCount; i++) {
            float simulationSpeed = dashboardSpeed.get(i);
            float acceleration = dashboardAcceleration.get(i);
            int nearestIndex = dashboardNearestIndex.get(i);
            String nearestText = nearestIndex < 0
                    ? "Nearest: -"
                    : String.format("Nearest: %s %.3fAU", bodyNames[nearestIndex], physicsDistanceToAu(dashboardNearestDistance.get(i)));
            HBox row = dashboardRows[i];
            Label label = dashboardLabels[i];
            float speed = speedToKilometersPerSecond(simulationSpeed);
            float accelerationMetersPerSecondSquared = accelerationToMetersPerSecondSquared(acceleration);
            float xAu = posX.get(i) / PHYSICS_UNITS_PER_AU;
            float yAu = posY.get(i) / PHYSICS_UNITS_PER_AU;

            if (dashboardRowNeedsRebuild(i)) {
                row = createDashboardRow(i);
                dashboardRows[i] = row;
                label = dashboardLabels[i];
            }

            if (editableMass[i]) {
                label.setText(String.format(
                        "%-10s | R:%4.1f%nA:%7.4f | X:%7.3f Y:%7.3f%n%s",
                        bodyNames[i], radius.get(i),
                        accelerationMetersPerSecondSquared, xAu, yAu,
                        nearestText
                ));
                TextField massField = massFields[i];
                if (!massField.isFocused()) {
                    massField.setText(String.format("%.2f", mass.get(i)));
                }
                TextField velocityField = velocityFields[i];
                if (!velocityField.isFocused()) {
                    velocityField.setText(String.format("%.2f", speed));
                }
            } else {
                label.setText(String.format(
                        "%-10s | M:%8.2f | R:%4.1f%nV:%7.2f | A:%7.4f | X:%7.3f Y:%7.3f%n%s",
                        bodyNames[i], mass.get(i), radius.get(i),
                        speed, accelerationMetersPerSecondSquared, xAu, yAu,
                        nearestText
                ));
            }

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
                || (editableMass[i] && (massFields[i] == null || velocityFields[i] == null))
                || (!editableMass[i] && (massFields[i] != null || velocityFields[i] != null));
    }

    private HBox createDashboardRow(int i) {
        String hexColor = toHex(bodyColors[i]);
        Label label = new Label();
        label.setStyle(String.format("-fx-text-fill: %s; -fx-font-family: monospace; -fx-font-size: 11px;", hexColor));
        dashboardLabels[i] = label;

        HBox row = new HBox(6);
        row.setStyle("-fx-alignment: center-left;");

        if (editableMass[i]) {
            TextField massField = new TextField(String.format("%.2f", mass.get(i)));
            massField.setTooltip(new Tooltip("Mass"));
            massField.setPrefWidth(72);
            massField.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-background-color: #1d1d28; -fx-text-fill: #ffffff; -fx-border-color: #444455;");
            massField.setOnAction(_ -> applyMassField(i));
            massField.focusedProperty().addListener((_, _, focused) -> {
                if (!focused) {
                    applyMassField(i);
                }
            });
            massFields[i] = massField;
            Label massPrefix = new Label("M:");
            massPrefix.setStyle("-fx-text-fill: #ffffff; -fx-font-family: monospace; -fx-font-size: 11px;");

            TextField velocityField = new TextField(String.format("%.2f", speedToKilometersPerSecond((float) Math.sqrt(velX.get(i) * velX.get(i) + velY.get(i) * velY.get(i)))));
            velocityField.setTooltip(new Tooltip("Speed [km/s equivalent]"));
            velocityField.setPrefWidth(72);
            velocityField.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-background-color: #1d1d28; -fx-text-fill: #ffffff; -fx-border-color: #444455;");
            velocityField.setOnAction(_ -> applyVelocityField(i));
            velocityField.focusedProperty().addListener((_, _, focused) -> {
                if (!focused) {
                    applyVelocityField(i);
                }
            });
            velocityFields[i] = velocityField;
            Label velocityPrefix = new Label("V:");
            velocityPrefix.setStyle("-fx-text-fill: #ffffff; -fx-font-family: monospace; -fx-font-size: 11px;");

            row.getChildren().addAll(label, massPrefix, massField, velocityPrefix, velocityField);
        } else {
            massFields[i] = null;
            velocityFields[i] = null;
            row.getChildren().add(label);
        }

        return row;
    }

    private void applyMassField(int i) {
        TextField massField = massFields[i];
        if (massField == null) {
            return;
        }

        try {
            float newMass = Math.max(0.1f, Float.parseFloat(massField.getText().trim().replace(',', '.')));
            mass.set(i, newMass);
            radius.set(i, radiusForCreatedMass(newMass));
            massField.setText(String.format("%.2f", newMass));
        } catch (NumberFormatException e) {
            massField.setText(String.format("%.2f", mass.get(i)));
        }
    }

    private void applyVelocityField(int i) {
        TextField velocityField = velocityFields[i];
        if (velocityField == null) {
            return;
        }

        try {
            float newSpeed = Math.max(0.0f, Float.parseFloat(velocityField.getText().trim().replace(',', '.')));
            float newSimulationSpeed = kilometersPerSecondToSimulationSpeed(newSpeed);
            float currentVx = velX.get(i);
            float currentVy = velY.get(i);
            float currentSpeed = (float) Math.sqrt(currentVx * currentVx + currentVy * currentVy);

            if (currentSpeed > 0.000001f) {
                float scale = newSimulationSpeed / currentSpeed;
                velX.set(i, currentVx * scale);
                velY.set(i, currentVy * scale);
            } else {
                velX.set(i, newSimulationSpeed);
                velY.set(i, 0.0f);
            }

            velocityField.setText(String.format("%.2f", newSpeed));
        } catch (NumberFormatException e) {
            float currentSpeed = (float) Math.sqrt(velX.get(i) * velX.get(i) + velY.get(i) * velY.get(i));
            velocityField.setText(String.format("%.2f", speedToKilometersPerSecond(currentSpeed)));
        }
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
        if (elapsedTimeLabel != null) {
            elapsedTimeLabel.setText("Time: 0s");
        }

        bodyCount = 0;
        customBodyCount = 0;
        creationState = CreationState.IDLE;

        for (int i = 0; i < MAX_BODIES; i++) {
            activeState.set(i, 0);
            collisionTarget.set(i, -1);
            dashboardSpeed.set(i, 0.0f);
            dashboardAcceleration.set(i, 0.0f);
            dashboardNearestDistance.set(i, 0.0f);
            dashboardNearestIndex.set(i, -1);
            accX.set(i, 0.0f);
            accY.set(i, 0.0f);
            nextAccX.set(i, 0.0f);
            nextAccY.set(i, 0.0f);
            editableMass[i] = false;
            orbitSemiMajorAu[i] = 0.0f;
            orbitEccentricity[i] = 0.0f;
            dashboardRows[i] = null;
            dashboardLabels[i] = null;
            massFields[i] = null;
            velocityFields[i] = null;
            trailX.get(i).clear();
            trailY.get(i).clear();
        }

        float cx = 0.0f;
        float cy = 0.0f;

        // Sun
        addBody("Sun", cx, cy, 0, 0, SUN_MASS, 16, Color.GOLD, false);

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
        float totalPx = 0, totalPy = 0;
        for (int i = 1; i < bodyCount; i++) {
            totalPx += mass.get(i) * velX.get(i);
            totalPy += mass.get(i) * velY.get(i);
        }
        velX.set(0, -totalPx / mass.get(0));
        velY.set(0, -totalPy / mass.get(0));
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
        float boundedEccentricity = Math.max(0.0f, Math.min(0.95f, eccentricity));
        float semiLatusRectum = semiMajorAxis * (1.0f - boundedEccentricity * boundedEccentricity);
        float radiusFromFocus = (float) (semiLatusRectum / (1.0 + boundedEccentricity * Math.cos(trueAnomaly)));
        float mu = G * (SUN_MASS + m);
        float specificAngularMomentum = (float) Math.sqrt(mu * semiLatusRectum);

        float x = (float) (cx + radiusFromFocus * Math.cos(trueAnomaly));
        float y = (float) (cy + radiusFromFocus * Math.sin(trueAnomaly));
        float vx = (float) (-mu / specificAngularMomentum * Math.sin(trueAnomaly));
        float vy = (float) (mu / specificAngularMomentum * (boundedEccentricity + Math.cos(trueAnomaly)));
        int planetIndex = bodyCount;
        addBody(name, x, y, vx, vy, m, size, color, false);
        if (bodyCount > planetIndex) {
            orbitSemiMajorAu[planetIndex] = semiMajorAxisAu;
            orbitEccentricity[planetIndex] = boundedEccentricity;
        }
    }

    private void addBody(String name, float x, float y, float vx, float vy, float m, float r, Color color, boolean canEditMass) {
        if (bodyCount >= MAX_BODIES) return;

        int i = bodyCount;
        bodyNames[i] = name;
        posX.set(i, x); posY.set(i, y);
        velX.set(i, vx); velY.set(i, vy);
        accX.set(i, 0); accY.set(i, 0);
        nextAccX.set(i, 0); nextAccY.set(i, 0);
        mass.set(i, m); radius.set(i, r);
        dashboardSpeed.set(i, (float) Math.sqrt(vx * vx + vy * vy));
        dashboardAcceleration.set(i, 0.0f);
        dashboardNearestDistance.set(i, 0.0f);
        dashboardNearestIndex.set(i, -1);
        bodyColors[i] = color;
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
