package pawg.gravity;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class GravitySystemCPU extends Application {

    private static final int CANVAS_WIDTH = 1600;
    private static final int SIDEBAR_WIDTH = 430;
    private static final int HEIGHT = 880;

    private static final double SPEED_FACTOR = 0.1;
    private static final double G = 1000.0 * (SPEED_FACTOR * SPEED_FACTOR);
    private static final double SUN_MASS = 100000.0;
    private static final double EARTH_ORBIT_R = 140.0;
    private static final double VELOCITY_SCALE = 0.085;
    private static final double MAX_CREATED_BODY_RADIUS = 20.0;
    private static final double CENTER_COLLISION_EPSILON = 0.5;

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
    private double canvasWidth = CANVAS_WIDTH;
    private double canvasHeight = HEIGHT;

    @Override
    public void start(Stage primaryStage) {
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        canvasWidth = Math.max(800.0, screenBounds.getWidth() - SIDEBAR_WIDTH);
        canvasHeight = screenBounds.getHeight();

        Canvas canvas = new Canvas(canvasWidth, canvasHeight);
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
                        String.format("Body #%d", customBodyCount),
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
                createdRadius = radiusForCreatedMass(createdMass);
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
        sidebar.setStyle(String.format("-fx-background-color: #111118; -fx-padding: 15; -fx-min-width: %dpx; -fx-pref-width: %dpx; -fx-border-color: #333344; -fx-border-width: 0 0 0 1;", SIDEBAR_WIDTH, SIDEBAR_WIDTH));

        Label title = new Label("NBodies Panel");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 14px;");

        Button btnReset = new Button("RESET (SPACE)");
        btnReset.setStyle("-fx-background-color: #222; -fx-text-fill: #ff4444; -fx-border-color: #ff4444; -fx-font-weight: bold; -fx-cursor: hand; -fx-max-width: Infinity;");
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

        primaryStage.setTitle("Gravity simulator | 1. Hold = Mass | 2. Click = Velocity V");
        primaryStage.setScene(scene);
        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());
        primaryStage.setResizable(false);
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
                    resolveCollisions();
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
                gc.fillRect(0, 0, canvasWidth, canvasHeight);

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
            Body nearest = findNearestBody(b);
            double acceleration = Math.sqrt(b.ax * b.ax + b.ay * b.ay);
            String nearestText = nearest == null
                    ? "Nearest: -"
                    : String.format("Nearest: %s %.1fpx", nearest.name, distanceBetween(b, nearest));
            Label lbl = new Label(String.format(
                    "%-10s | M:%8.2f | R:%4.1f%nV:%7.2f | A:%7.3f | X:%6.1f Y:%6.1f%n%s",
                    b.name, b.mass, b.radius,
                    b.getSpeed(), acceleration, b.x, b.y,
                    nearestText
            ));
            String hexColor = toHex(b.color);
            lbl.setStyle(String.format("-fx-text-fill: %s; -fx-font-family: monospace; -fx-font-size: 11px; -fx-padding: 2 0 4 0;", hexColor));
            dashboardList.getChildren().add(lbl);
        }
    }

    private Body findNearestBody(Body body) {
        Body nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (Body other : bodies) {
            if (other == body) {
                continue;
            }

            double dx = other.x - body.x;
            double dy = other.y - body.y;
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = other;
            }
        }

        return nearest;
    }

    private double distanceBetween(Body first, Body second) {
        double dx = second.x - first.x;
        double dy = second.y - first.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private double radiusForCreatedMass(double bodyMass) {
        return Math.min(MAX_CREATED_BODY_RADIUS, Math.max(3.0, 4.0 + Math.pow(bodyMass, 1.0 / 3.0) * 1.8));
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

        double cx = canvasWidth / 2.0;
        double cy = canvasHeight / 2.0;

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

    private void resolveCollisions() {
        boolean mergedAny = false;
        for (int i = 0; i < bodies.size(); i++) {
            Body first = bodies.get(i);
            for (int j = i + 1; j < bodies.size(); j++) {
                Body second = bodies.get(j);
                if (centersCollide(first, second)) {
                    first = mergeBodies(first, second);
                    bodies.remove(j);
                    j--;
                    mergedAny = true;
                }
            }
        }

        if (mergedAny) {
            computeAccelerations();
        }
    }

    private boolean centersCollide(Body first, Body second) {
        double dx = second.x - first.x;
        double dy = second.y - first.y;
        return dx * dx + dy * dy <= CENTER_COLLISION_EPSILON * CENTER_COLLISION_EPSILON;
    }

    private Body mergeBodies(Body first, Body second) {
        double mergedMass = first.mass + second.mass;
        if (mergedMass <= 0.0) {
            return first;
        }

        boolean keepFirst = first.mass >= second.mass;
        Body survivor = keepFirst ? first : second;
        Body absorbed = keepFirst ? second : first;

        double mergedX = (first.x * first.mass + second.x * second.mass) / mergedMass;
        double mergedY = (first.y * first.mass + second.y * second.mass) / mergedMass;
        double mergedVx = (first.vx * first.mass + second.vx * second.mass) / mergedMass;
        double mergedVy = (first.vy * first.mass + second.vy * second.mass) / mergedMass;

        survivor.name = survivor.name + "+";
        survivor.x = mergedX;
        survivor.y = mergedY;
        survivor.vx = mergedVx;
        survivor.vy = mergedVy;
        survivor.ax = 0.0;
        survivor.ay = 0.0;
        survivor.mass = mergedMass;
        survivor.radius = radiusForCreatedMass(mergedMass);

        if (survivor != first) {
            bodies.set(bodies.indexOf(first), survivor);
        }

        absorbed.trailX.clear();
        absorbed.trailY.clear();
        return survivor;
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
