package pawg.gravity;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class GravitySystemCPU extends Application {

    private static final int CANVAS_WIDTH = 1600;
    private static final int HEIGHT = 880;

    private static final double SPEED_FACTOR = 0.1;
    private static final double G = 1000.0 * (SPEED_FACTOR * SPEED_FACTOR);
    private static final double SUN_MASS = 100000.0;
    private static final double EARTH_ORBIT_R = 140.0;
    private static final double VELOCITY_SCALE = 0.085;

    private enum CreationState { IDLE, SIZING_MASS, SELECTING_VECTOR }
    private CreationState creationState = CreationState.IDLE;

    private double bodyX, bodyY;
    private double createdMass = 1.0;
    private double createdRadius = 5.0;
    private double vectorEndX, vectorEndY;
    private int customBodyCount = 0;

    public static class Body {
        String name;
        double x, y;
        double vx, vy;
        double ax, ay;
        double mass;
        double radius;
        Color color;
        List<Double> trailX = new ArrayList<>();
        List<Double> trailY = new ArrayList<>();

        public Body(String name, double x, double y, double vx, double vy, double mass, double radius, Color color) {
            this.name = name;
            this.x = x; this.y = y;
            this.vx = vx; this.vy = vy;
            this.ax = 0; this.ay = 0;
            this.mass = mass; this.radius = radius;
            this.color = color;
        }

        public double getSpeed() {
            return Math.sqrt(vx * vx + vy * vy);
        }

        public void addTrailPoint() {
            trailX.add(x);
            trailY.add(y);
            if (trailX.size() > 220) {
                trailX.removeFirst();
                trailY.removeFirst();
            }
        }
    }

    private final List<Body> bodies = new ArrayList<>();
    private final VBox dashboardList = new VBox(6);

    @Override
    public void start(Stage primaryStage) {
        Canvas canvas = new Canvas(CANVAS_WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        resetSystem();

        // --- Precise two-stage mouse interaction handling ---

        canvas.setOnMousePressed(event -> {
            if (creationState == CreationState.IDLE) {
                // Stage 1: choose position and mass by dragging.
                bodyX = event.getX();
                bodyY = event.getY();
                createdMass = 1.0;
                createdRadius = 5.0;
                creationState = CreationState.SIZING_MASS;
            } else if (creationState == CreationState.SELECTING_VECTOR) {
                // Stage 2: finish and confirm the velocity vector with a click.
                double dx = event.getX() - bodyX;
                double dy = event.getY() - bodyY;

                double vx = dx * VELOCITY_SCALE;
                double vy = dy * VELOCITY_SCALE;

                customBodyCount++;
                Body customBody = new Body(
                        String.format("Obiekt #%d", customBodyCount),
                        bodyX, bodyY, vx, vy, createdMass, createdRadius, Color.RED
                );
                bodies.add(customBody);

                computeAccelerations();
                creationState = CreationState.IDLE;
            }
        });

        canvas.setOnMouseDragged(event -> {
            if (creationState == CreationState.SIZING_MASS) {
                double dx = event.getX() - bodyX;
                double dy = event.getY() - bodyY;
                double dist = Math.sqrt(dx * dx + dy * dy);

                createdMass = Math.max(0.1, 1.0 + Math.pow(dist / 4.0, 1.8));
                createdRadius = Math.max(3.0, 4.0 + Math.pow(createdMass, 1.0 / 3.0) * 1.8);
            }
        });

        canvas.setOnMouseReleased(event -> {
            if (creationState == CreationState.SIZING_MASS) {
                // Move from stage 1 to stage 2 for vector selection.
                vectorEndX = event.getX();
                vectorEndY = event.getY();
                creationState = CreationState.SELECTING_VECTOR;
            }
        });

        canvas.setOnMouseMoved(event -> {
            if (creationState == CreationState.SELECTING_VECTOR) {
                // Track the mouse for vector preview.
                vectorEndX = event.getX();
                vectorEndY = event.getY();
            }
        });

        // UI Panel
        VBox sidebar = new VBox(10);
        sidebar.setStyle("-fx-background-color: #111118; -fx-padding: 15; -fx-min-width: 250px; -fx-border-color: #333344; -fx-border-width: 0 0 0 1;");

        Label title = new Label("PANEL OBIEKTÓW");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 14px;");

        Button btnReset = new Button("RESET (SPACE)");
        btnReset.setStyle("-fx-background-color: #222; -fx-text-fill: #ff4444; -fx-border-color: #ff4444; -fx-font-weight: bold; -fx-cursor: hand; -fx-max-width: Infinity;");
        btnReset.setFocusTraversable(false);
        btnReset.setOnAction(_ -> resetSystem());

        sidebar.getChildren().addAll(title, btnReset, dashboardList);

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setRight(sidebar);

        Scene scene = new Scene(root, CANVAS_WIDTH + 250, HEIGHT, Color.BLACK);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE) {
                resetSystem();
            }
        });

        primaryStage.setTitle("Symulator Grawitacyjny | 1. Przytrzymaj = Masa | 2. Kliknij = Wektor V");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Rendering and physics loop.
        AnimationTimer timer = new AnimationTimer() {
            private int frameCounter = 0;

            @Override
            public void handle(long now) {
                double dt = 0.0012;
                int subSteps = 12;

                for (int step = 0; step < subSteps; step++) {
                    physicsStepVerlet(dt);
                }

                frameCounter++;
                if (frameCounter % 2 == 0) {
                    for (Body b : bodies) {
                        b.addTrailPoint();
                    }
                }

                if (frameCounter % 5 == 0) {
                    updateDashboard();
                }

                gc.setFill(Color.rgb(3, 3, 10, 0.35));
                gc.fillRect(0, 0, CANVAS_WIDTH, HEIGHT);

                for (Body b : bodies) {
                    gc.setStroke(b.color.deriveColor(0, 1, 1, 0.3));
                    gc.setLineWidth(1.0);
                    for (int k = 0; k < b.trailX.size() - 1; k++) {
                        gc.strokeLine(b.trailX.get(k), b.trailY.get(k), b.trailX.get(k+1), b.trailY.get(k+1));
                    }

                    gc.setFill(b.color);
                    gc.fillOval(b.x - b.radius, b.y - b.radius, b.radius * 2, b.radius * 2);
                }

                // Body creation visualization.
                if (creationState == CreationState.SIZING_MASS) {
                    gc.setFill(Color.RED.deriveColor(0, 1, 1, 0.7));
                    gc.fillOval(bodyX - createdRadius, bodyY - createdRadius, createdRadius * 2, createdRadius * 2);

                    gc.setStroke(Color.WHITE);
                    gc.setLineWidth(1.0);
                    gc.strokeOval(bodyX - createdRadius, bodyY - createdRadius, createdRadius * 2, createdRadius * 2);

                    gc.setFill(Color.WHITE);
                    gc.fillText(String.format("Masa: %.1f M_Earth", createdMass), bodyX + createdRadius + 10, bodyY);
                } else if (creationState == CreationState.SELECTING_VECTOR) {
                    gc.setFill(Color.RED);
                    gc.fillOval(bodyX - createdRadius, bodyY - createdRadius, createdRadius * 2, createdRadius * 2);

                    double dx = vectorEndX - bodyX;
                    double dy = vectorEndY - bodyY;
                    double vectorLength = Math.sqrt(dx * dx + dy * dy);

                    gc.setStroke(Color.RED);
                    gc.setLineWidth(2.0);
                    gc.strokeLine(bodyX, bodyY, vectorEndX, vectorEndY);

                    gc.setFill(Color.WHITE);
                    gc.fillText(String.format("Masa: %.1f M_Earth | V: %.2f px/s", createdMass, vectorLength * VELOCITY_SCALE), vectorEndX + 10, vectorEndY);
                }
            }
        };
        timer.start();
    }

    private void updateDashboard() {
        dashboardList.getChildren().clear();
        for (Body b : bodies) {
            Label lbl = new Label(String.format("%-10s | M: %-6.1f | V: %.2f", b.name, b.mass, b.getSpeed()));
            String hexColor = toHex(b.color);
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
        bodies.clear();
        creationState = CreationState.IDLE;
        customBodyCount = 0;

        double cx = CANVAS_WIDTH / 2.0;
        double cy = HEIGHT / 2.0;

        Body sun = new Body("Sun", cx, cy, 0, 0, SUN_MASS, 16, Color.GOLD);
        bodies.add(sun);

        addKeplerPlanet("Mercury", cx, cy, 55.0,  0.055, 3.0, Color.GRAY);
        addKeplerPlanet("Venus",   cx, cy, 95.0,  0.815, 4.5, Color.BEIGE);
        addKeplerPlanet("Earth",   cx, cy, EARTH_ORBIT_R, 1.000, 5.0, Color.DODGERBLUE);
        addKeplerPlanet("Mars",    cx, cy, 185.0, 0.107, 4.0, Color.INDIANRED);
        addKeplerPlanet("Jupiter", cx, cy, 245.0, 317.8, 11.0, Color.PERU);
        addKeplerPlanet("Saturn",  cx, cy, 305.0, 95.2,  9.0, Color.BURLYWOOD);
        addKeplerPlanet("Uranus",  cx, cy, 365.0, 14.5,  7.0, Color.LIGHTBLUE);
        addKeplerPlanet("Neptune", cx, cy, 420.0, 17.1,  7.0, Color.ROYALBLUE);

        double totalPx = 0, totalPy = 0;
        for (int i = 1; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            totalPx += b.mass * b.vx;
            totalPy += b.mass * b.vy;
        }
        sun.vx = -totalPx / sun.mass;
        sun.vy = -totalPy / sun.mass;

        computeAccelerations();
    }

    private void addKeplerPlanet(String name, double cx, double cy, double orbitR, double mass, double size, Color color) {
        double v = Math.sqrt(G * (SUN_MASS + mass) / orbitR);
        bodies.add(new Body(name, cx + orbitR, cy, 0, v, mass, size, color));
    }

    private void physicsStepVerlet(double dt) {
        for (Body b : bodies) {
            b.x += b.vx * dt + 0.5 * b.ax * dt * dt;
            b.y += b.vy * dt + 0.5 * b.ay * dt * dt;
        }

        double[][] oldA = new double[bodies.size()][2];
        for (int i = 0; i < bodies.size(); i++) {
            oldA[i][0] = bodies.get(i).ax;
            oldA[i][1] = bodies.get(i).ay;
        }

        computeAccelerations();

        for (int i = 0; i < bodies.size(); i++) {
            Body b = bodies.get(i);
            b.vx += 0.5 * (oldA[i][0] + b.ax) * dt;
            b.vy += 0.5 * (oldA[i][1] + b.ay) * dt;
        }
    }

    private void computeAccelerations() {
        for (Body b : bodies) {
            b.ax = 0;
            b.ay = 0;
        }

        for (int i = 0; i < bodies.size(); i++) {
            Body b1 = bodies.get(i);
            for (int j = i + 1; j < bodies.size(); j++) {
                Body b2 = bodies.get(j);

                double dx = b2.x - b1.x;
                double dy = b2.y - b1.y;
                double distSq = dx * dx + dy * dy + 35.0;
                double dist = Math.sqrt(distSq);

                double force = (G * b1.mass * b2.mass) / distSq;

                double fx = force * (dx / dist);
                double fy = force * (dy / dist);

                b1.ax += fx / b1.mass;
                b1.ay += fy / b1.mass;

                b2.ax -= fx / b2.mass;
                b2.ay -= fy / b2.mass;
            }
        }
    }

    static void main(String[] args) {
        launch(args);
    }
}
