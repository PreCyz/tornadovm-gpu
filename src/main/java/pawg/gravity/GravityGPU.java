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
    private static final float SUN_MASS = 100000.0f;
    private static final float EARTH_ORBIT_R = 140.0f;
    private static final float VELOCITY_SCALE = 0.085f;
    private static final float MAX_CREATED_BODY_RADIUS = 20.0f;
    private static final float CENTER_COLLISION_EPSILON = 0.5f;
    private static final int GPU_SUB_STEPS = 8;

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
    private final FloatArray mass = new FloatArray(MAX_BODIES);
    private final FloatArray radius = new FloatArray(MAX_BODIES);
    private final IntArray activeState = new IntArray(MAX_BODIES);
    private final IntArray collisionTarget = new IntArray(MAX_BODIES);

    private final FloatArray physParams = new FloatArray(2);
    private final IntArray simulationState = new IntArray(1);

    private final String[] bodyNames = new String[MAX_BODIES];
    private final Color[] bodyColors = new Color[MAX_BODIES];
    private final boolean[] editableMass = new boolean[MAX_BODIES];
    private final HBox[] dashboardRows = new HBox[MAX_BODIES];
    private final Label[] dashboardLabels = new Label[MAX_BODIES];
    private final TextField[] massFields = new TextField[MAX_BODIES];
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
    private double canvasWidth = CANVAS_WIDTH;
    private double canvasHeight = HEIGHT;

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

                customBodyCount++;
                addBody(
                        String.format("Body #%d", customBodyCount),
                        clickX, clickY,
                        dx * VELOCITY_SCALE, dy * VELOCITY_SCALE,
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

        Label legend = new Label("""
                M = mass
                R = body radius
                V = speed
                A = acceleration
                X/Y = position
                Nearest = closest body and distance""");
        legend.setStyle("-fx-text-fill: #b8b8c8; -fx-font-family: monospace; -fx-font-size: 11px; -fx-padding: 0 0 6 0;");

        sidebar.getChildren().addAll(title, btnReset, legend, dashboardList);

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
                updateSimulationState();
                executionPlan.execute();
                resolveCollisions();

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
                gc.fillRect(0, 0, canvasWidth, canvasHeight);

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

                    drawPlanetRings(gc, i);

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

    private void drawPlanetRings(GraphicsContext gc, int bodyIndex) {
        String name = bodyNames[bodyIndex];
        if (name == null) {
            return;
        }

        if (name.startsWith("Saturn")) {
            drawSaturnRings(gc, posX.get(bodyIndex), posY.get(bodyIndex), radius.get(bodyIndex));
        } else if (name.startsWith("Uranus")) {
            drawUranusVerticalRing(gc, posX.get(bodyIndex), posY.get(bodyIndex), radius.get(bodyIndex));
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
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, posX, posY, velX, velY, mass, activeState, physParams, simulationState)
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, nextPosX, nextPosY, nextVelX, nextVelY)
                .task("clearCollisionTargets", PhysicsKernels::clearCollisionTargets, collisionTarget, simulationState);

        for (int step = 0; step < GPU_SUB_STEPS; step++) {
            boolean evenStep = step % 2 == 0;
            FloatArray sourcePosX = evenStep ? posX : nextPosX;
            FloatArray sourcePosY = evenStep ? posY : nextPosY;
            FloatArray sourceVelX = evenStep ? velX : nextVelX;
            FloatArray sourceVelY = evenStep ? velY : nextVelY;
            FloatArray targetPosX = evenStep ? nextPosX : posX;
            FloatArray targetPosY = evenStep ? nextPosY : posY;
            FloatArray targetVelX = evenStep ? nextVelX : velX;
            FloatArray targetVelY = evenStep ? nextVelY : velY;

            taskGraph
                    .task("integrateStep" + step, PhysicsKernels::integrateStep,
                            sourcePosX, sourcePosY, sourceVelX, sourceVelY,
                            targetPosX, targetPosY, targetVelX, targetVelY,
                            accX, accY, mass, activeState, physParams, simulationState)
                    .task("detectCollisions" + step, PhysicsKernels::detectCollisions,
                            targetPosX, targetPosY, activeState, collisionTarget, CENTER_COLLISION_EPSILON, simulationState);
        }

        taskGraph.transferToHost(DataTransferMode.EVERY_EXECUTION, posX, posY, velX, velY, collisionTarget);

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
        mass.set(survivor, mergedMass);
        radius.set(survivor, radiusForCreatedMass(mergedMass));
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
        mass.set(target, mass.get(source));
        radius.set(target, radius.get(source));
        bodyColors[target] = bodyColors[source];
        editableMass[target] = editableMass[source];
        activeState.set(target, 1);
        collisionTarget.set(target, -1);

        trailX.get(target).clear();
        trailX.get(target).addAll(trailX.get(source));
        trailY.get(target).clear();
        trailY.get(target).addAll(trailY.get(source));
        dashboardRows[target] = null;
        dashboardLabels[target] = null;
        massFields[target] = null;
    }

    private void clearBodySlot(int i) {
        bodyNames[i] = null;
        posX.set(i, 0.0f);
        posY.set(i, 0.0f);
        velX.set(i, 0.0f);
        velY.set(i, 0.0f);
        accX.set(i, 0.0f);
        accY.set(i, 0.0f);
        mass.set(i, 0.0f);
        radius.set(i, 0.0f);
        bodyColors[i] = null;
        editableMass[i] = false;
        activeState.set(i, 0);
        collisionTarget.set(i, -1);
        trailX.get(i).clear();
        trailY.get(i).clear();
        dashboardRows[i] = null;
        dashboardLabels[i] = null;
        massFields[i] = null;
    }

    private void updateDashboard() {
        while (dashboardList.getChildren().size() > bodyCount) {
            dashboardList.getChildren().removeLast();
        }

        for (int i = 0; i < bodyCount; i++) {
            float speed = (float) Math.sqrt(velX.get(i) * velX.get(i) + velY.get(i) * velY.get(i));
            float acceleration = (float) Math.sqrt(accX.get(i) * accX.get(i) + accY.get(i) * accY.get(i));
            int nearestIndex = findNearestBodyIndex(i);
            String nearestText = nearestIndex < 0
                    ? "Nearest: -"
                    : String.format("Nearest: %s %.1fpx", bodyNames[nearestIndex], distanceBetween(i, nearestIndex));
            HBox row = dashboardRows[i];
            Label label = dashboardLabels[i];

            if (row == null) {
                row = createDashboardRow(i);
                dashboardRows[i] = row;
                label = dashboardLabels[i];
            }

            if (editableMass[i]) {
                label.setText(String.format(
                        "%-10s | R:%4.1f%nV:%7.2f | A:%7.3f | X:%6.1f Y:%6.1f%n%s",
                        bodyNames[i], radius.get(i),
                        speed, acceleration, posX.get(i), posY.get(i),
                        nearestText
                ));
                TextField massField = massFields[i];
                if (!massField.isFocused()) {
                    massField.setText(String.format("%.2f", mass.get(i)));
                }
            } else {
                label.setText(String.format(
                        "%-10s | M:%8.2f | R:%4.1f%nV:%7.2f | A:%7.3f | X:%6.1f Y:%6.1f%n%s",
                        bodyNames[i], mass.get(i), radius.get(i),
                        speed, acceleration, posX.get(i), posY.get(i),
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
            row.getChildren().addAll(label, massPrefix, massField);
        } else {
            row.getChildren().add(label);
        }

        return row;
    }

    private int findNearestBodyIndex(int bodyIndex) {
        int nearestIndex = -1;
        float nearestDistanceSq = Float.MAX_VALUE;
        float bodyX = posX.get(bodyIndex);
        float bodyY = posY.get(bodyIndex);

        for (int otherIndex = 0; otherIndex < bodyCount; otherIndex++) {
            if (otherIndex == bodyIndex) {
                continue;
            }

            float dx = posX.get(otherIndex) - bodyX;
            float dy = posY.get(otherIndex) - bodyY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearestIndex = otherIndex;
            }
        }

        return nearestIndex;
    }

    private float distanceBetween(int firstIndex, int secondIndex) {
        float dx = posX.get(secondIndex) - posX.get(firstIndex);
        float dy = posY.get(secondIndex) - posY.get(firstIndex);
        return (float) Math.sqrt(dx * dx + dy * dy);
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
        bodyCount = 0;
        customBodyCount = 0;
        creationState = CreationState.IDLE;

        for (int i = 0; i < MAX_BODIES; i++) {
            activeState.set(i, 0);
            collisionTarget.set(i, -1);
            editableMass[i] = false;
            dashboardRows[i] = null;
            dashboardLabels[i] = null;
            massFields[i] = null;
            trailX.get(i).clear();
            trailY.get(i).clear();
        }

        float cx = (float) (canvasWidth / 2.0);
        float cy = (float) (canvasHeight / 2.0);

        // Sun
        addBody("Sun", cx, cy, 0, 0, SUN_MASS, 16, Color.GOLD, false);

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
        addBody(name, cx + orbitR, cy, 0, v, m, size, color, false);
    }

    private void addBody(String name, float x, float y, float vx, float vy, float m, float r, Color color, boolean canEditMass) {
        if (bodyCount >= MAX_BODIES) return;

        int i = bodyCount;
        bodyNames[i] = name;
        posX.set(i, x); posY.set(i, y);
        velX.set(i, vx); velY.set(i, vy);
        accX.set(i, 0); accY.set(i, 0);
        mass.set(i, m); radius.set(i, r);
        bodyColors[i] = color;
        editableMass[i] = canEditMass;
        activeState.set(i, 1);

        bodyCount++;
    }

    static void main(String[] args) {
        launch(args);
    }
}
