package pawg.gravity;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
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
    private static final int SIDEBAR_WIDTH = 250;
    private static final int HEIGHT = 880;
    private static final int MAX_BODIES = 1024;

    private static final float SPEED_FACTOR = 0.1f;
    private static final float G = 1000.0f * (SPEED_FACTOR * SPEED_FACTOR);
    private static final float DT = 0.0012f;
    private static final float SUN_MASS = 100000.0f;
    private static final float EARTH_ORBIT_R = 140.0f;
    private static final float VELOCITY_SCALE = 0.085f;

    private final FloatArray posX = new FloatArray(MAX_BODIES);
    private final FloatArray posY = new FloatArray(MAX_BODIES);
    private final FloatArray velX = new FloatArray(MAX_BODIES);
    private final FloatArray velY = new FloatArray(MAX_BODIES);
    private final FloatArray accX = new FloatArray(MAX_BODIES);
    private final FloatArray accY = new FloatArray(MAX_BODIES);
    private final FloatArray mass = new FloatArray(MAX_BODIES);
    private final FloatArray radius = new FloatArray(MAX_BODIES);
    private final IntArray activeState = new IntArray(MAX_BODIES);

    private final FloatArray physParams = new FloatArray(2);

    private final String[] bodyNames = new String[MAX_BODIES];
    private final Color[] bodyColors = new Color[MAX_BODIES];
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

    private final VBox dashboardList = new VBox(6);

    @Override
    public void start(Stage primaryStage) {
        Canvas canvas = new Canvas(CANVAS_WIDTH, HEIGHT);
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

                customBodyCount++;
                addBody(
                        String.format("Obiekt #%d", customBodyCount),
                        clickX, clickY,
                        dx * VELOCITY_SCALE, dy * VELOCITY_SCALE,
                        createdMass, createdRadius, Color.RED
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
                createdRadius = (float) Math.max(3.0, 4.0 + Math.pow(createdMass, 1.0 / 3.0) * 1.8);
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
        sidebar.setStyle(String.format("-fx-background-color: #111118; -fx-padding: 15; -fx-min-width: %dpx; -fx-border-color: #333344; -fx-border-width: 0 0 0 1;", SIDEBAR_WIDTH));

        Label title = new Label("GPU DASHBOARD (TornadoVM)");
        title.setStyle("-fx-text-fill: #00ff88; -fx-font-weight: bold; -fx-font-size: 13px;");

        Button btnReset = new Button("RESET (SPACE)");
        // The sidebar is SIDEBAR_WIDTH (250 px), with 15 px of left padding.
        // A 215 px button width leaves exactly 5 px before the right edge.
        btnReset.setStyle("-fx-background-color: #222; -fx-text-fill: #ff4444; -fx-border-color: #ff4444; -fx-font-weight: bold; -fx-cursor: hand; -fx-min-width: 215px; -fx-max-width: 215px;");
        btnReset.setFocusTraversable(false);
        btnReset.setOnAction(_ -> resetSystem());

        sidebar.getChildren().addAll(title, btnReset, dashboardList);

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setRight(sidebar);

        Scene scene = new Scene(root, CANVAS_WIDTH + SIDEBAR_WIDTH, HEIGHT, Color.BLACK);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE) {
                resetSystem();
            }
        });

        primaryStage.setTitle("N-Body Gravity Simulator");
        primaryStage.setScene(scene);
        primaryStage.show();

        AnimationTimer timer = new AnimationTimer() {
            private int frameCounter = 0;

            @Override
            public void handle(long now) {
                int subSteps = 8;
                for (int step = 0; step < subSteps; step++) {
                    executionPlan.execute();
                }

                frameCounter++;
                if (frameCounter % 2 == 0) {
                    for (int i = 0; i < bodyCount; i++) {
                        trailX.get(i).add(posX.get(i));
                        trailY.get(i).add(posY.get(i));
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
                gc.fillRect(0, 0, CANVAS_WIDTH, HEIGHT);

                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.BOTTOM);
                gc.setFont(Font.font("SansSerif", 11));

                for (int i = 0; i < bodyCount; i++) {
                    gc.setStroke(bodyColors[i].deriveColor(0, 1, 1, 0.3));
                    gc.setLineWidth(1.0);
                    List<Float> tx = trailX.get(i);
                    List<Float> ty = trailY.get(i);
                    for (int k = 0; k < tx.size() - 1; k++) {
                        gc.strokeLine(tx.get(k), ty.get(k), tx.get(k+1), ty.get(k+1));
                    }

                    gc.setFill(bodyColors[i]);
                    gc.fillOval(posX.get(i) - radius.get(i), posY.get(i) - radius.get(i), radius.get(i) * 2, radius.get(i) * 2);

                    gc.setFill(bodyColors[i].deriveColor(0, 0.7, 1.2, 0.9));
                    gc.fillText(bodyNames[i], posX.get(i), posY.get(i) - radius.get(i) - 4);
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

                    gc.setStroke(Color.RED);
                    gc.setLineWidth(2.0);
                    gc.strokeLine(clickX, clickY, vectorEndX, vectorEndY);

                    gc.setTextAlign(TextAlignment.LEFT);
                    gc.setTextBaseline(VPos.CENTER);
                    gc.setFill(Color.WHITE);
                    gc.fillText(String.format("Masa: %.1f M_Earth | V: %.2f px/s", createdMass, vectorLength * VELOCITY_SCALE), vectorEndX + 10, vectorEndY);
                }
            }
        };
        timer.start();
    }

    private void initTornadoPlanOnce() {
        TaskGraph taskGraph = new TaskGraph("nbody")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, posX, posY, velX, velY, accX, accY, mass, activeState, physParams)
                .task("computeForces", PhysicsKernels::computeForces, posX, posY, accX, accY, mass, activeState, physParams)
                .task("integrateMotion", PhysicsKernels::integrateMotion, posX, posY, velX, velY, accX, accY, activeState, physParams)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, posX, posY, velX, velY);

        executionPlan = new TornadoExecutionPlan(taskGraph.snapshot());
    }

    private void updateDashboard() {
        dashboardList.getChildren().clear();
        for (int i = 0; i < bodyCount; i++) {
            float speed = (float) Math.sqrt(velX.get(i) * velX.get(i) + velY.get(i) * velY.get(i));
            Label lbl = new Label(String.format("%-10s | M: %-6.2f | V: %.2f", bodyNames[i], mass.get(i), speed));
            String hexColor = toHex(bodyColors[i]);
            lbl.setStyle(String.format("-fx-text-fill: %s; -fx-font-family: monospace; -fx-font-size: 11px;", hexColor));
            dashboardList.getChildren().add(lbl);
        }
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }

    private void resetSystem() {
        bodyCount = 0;
        customBodyCount = 0;
        creationState = CreationState.IDLE;

        for (int i = 0; i < MAX_BODIES; i++) {
            activeState.set(i, 0);
            trailX.get(i).clear();
            trailY.get(i).clear();
        }

        float cx = CANVAS_WIDTH / 2.0f;
        float cy = HEIGHT / 2.0f;

        // Sun
        addBody("Sun", cx, cy, 0, 0, SUN_MASS, 16, Color.GOLD);

        // Planets
        addKeplerPlanet("Mercury", cx, cy, 55.0f,  0.055f, 3.0f, Color.GRAY);
        addKeplerPlanet("Venus",   cx, cy, 95.0f,  0.815f, 4.5f, Color.BEIGE);
        addKeplerPlanet("Earth",   cx, cy, EARTH_ORBIT_R, 1.000f, 5.0f, Color.DODGERBLUE);
        addKeplerPlanet("Mars",    cx, cy, 185.0f, 0.107f, 4.0f, Color.INDIANRED);
        addKeplerPlanet("Jupiter", cx, cy, 245.0f, 317.8f, 11.0f, Color.PERU);
        addKeplerPlanet("Saturn",  cx, cy, 305.0f, 95.2f,  9.0f, Color.BURLYWOOD);
        addKeplerPlanet("Uranus",  cx, cy, 365.0f, 14.5f,  7.0f, Color.LIGHTBLUE);
        addKeplerPlanet("Neptune", cx, cy, 420.0f, 17.1f,  7.0f, Color.ROYALBLUE);

        // Momentum compensation for the Sun.
        float totalPx = 0, totalPy = 0;
        for (int i = 1; i < bodyCount; i++) {
            totalPx += mass.get(i) * velX.get(i);
            totalPy += mass.get(i) * velY.get(i);
        }
        velX.set(0, -totalPx / mass.get(0));
        velY.set(0, -totalPy / mass.get(0));
    }

    private void addKeplerPlanet(String name, float cx, float cy, float orbitR, float m, float size, Color color) {
        float v = (float) Math.sqrt(G * (SUN_MASS + m) / orbitR);
        addBody(name, cx + orbitR, cy, 0, v, m, size, color);
    }

    private void addBody(String name, float x, float y, float vx, float vy, float m, float r, Color color) {
        if (bodyCount >= MAX_BODIES) return;

        int i = bodyCount;
        bodyNames[i] = name;
        posX.set(i, x); posY.set(i, y);
        velX.set(i, vx); velY.set(i, vy);
        accX.set(i, 0); accY.set(i, 0);
        mass.set(i, m); radius.set(i, r);
        bodyColors[i] = color;
        activeState.set(i, 1);

        bodyCount++;
    }

    static void main(String[] args) {
        launch(args);
    }
}
