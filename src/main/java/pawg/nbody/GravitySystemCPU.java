package pawg.nbody;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
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
    private static final double SUN_MASS = 332946.0;
    private static final double PHYSICS_UNITS_PER_AU = 140.0;
    private static final double ASTRONOMICAL_UNIT_KM = 149_597_870.7;
    private static final double EARTH_ORBITAL_SPEED_KM_PER_SECOND = 29.78;
    private static final double VELOCITY_SCALE = 0.085;
    private static final double MAX_CREATED_BODY_RADIUS = 20.0;
    private static final double CENTER_COLLISION_EPSILON = 0.5;
    private static final double MIN_PLANET_ORBIT_RADIUS = 55.0;
    private static final double ORBIT_EDGE_PADDING = 50.0;
    private static final double MERCURY_AU = 0.387;
    private static final double VENUS_AU = 0.723;
    private static final double EARTH_AU = 1.000;
    private static final double MARS_AU = 1.524;
    private static final double JUPITER_AU = 5.203;
    private static final double SATURN_AU = 9.537;
    private static final double URANUS_AU = 19.191;
    private static final double NEPTUNE_AU = 30.070;
    private static final double MERCURY_ECCENTRICITY = 0.2056;
    private static final double VENUS_ECCENTRICITY = 0.0068;
    private static final double EARTH_ECCENTRICITY = 0.0167;
    private static final double MARS_ECCENTRICITY = 0.0934;
    private static final double JUPITER_ECCENTRICITY = 0.0489;
    private static final double SATURN_ECCENTRICITY = 0.0565;
    private static final double URANUS_ECCENTRICITY = 0.0472;
    private static final double NEPTUNE_ECCENTRICITY = 0.0086;
    private static final double[] STABLE_ORBIT_PHASES = {
            0.10, 1.65, 3.15, 4.85,
            0.75, 2.85, 4.95, 5.65
    };

    private enum CreationState { IDLE, SIZING_MASS, SELECTING_VECTOR }
    private CreationState creationState = CreationState.IDLE;

    private double bodyX, bodyY;
    private double createdMass = 1.0;
    private double createdRadius = 5.0;
    private double vectorEndX, vectorEndY;
    private int customBodyCount = 0;
    private boolean alignPlanetsOnReset = false;

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

        public void addTrailPoint(double screenX, double screenY) {
            trailX.add(screenX);
            trailY.add(screenY);
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

                double velocityScale = physicsUnitsPerScreenPixelAtEarthOrbit() * VELOCITY_SCALE;
                double vx = dx * velocityScale;
                double vy = dy * velocityScale;

                customBodyCount++;
                Body customBody = new Body(
                        String.format("Body #%d", customBodyCount),
                        physicsXForScreen(bodyX, bodyY), physicsYForScreen(bodyX, bodyY),
                        vx, vy, createdMass, createdRadius, Color.RED
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
        btnReset.setStyle("-fx-background-color: #222; -fx-text-fill: #ff4444; -fx-border-color: #ff4444; -fx-font-weight: bold; -fx-cursor: hand;");
        btnReset.setFocusTraversable(false);
        btnReset.setOnAction(_ -> resetSystem());

        CheckBox alignPlanetsCheckbox = new CheckBox("Align planets on reset");
        alignPlanetsCheckbox.setSelected(false);
        alignPlanetsCheckbox.setFocusTraversable(false);
        alignPlanetsCheckbox.setStyle("-fx-text-fill: #b8b8c8; -fx-font-family: monospace; -fx-font-size: 11px;");
        alignPlanetsCheckbox.selectedProperty().addListener((_, _, selected) -> alignPlanetsOnReset = selected);

        GridPane optionsGrid = new GridPane();
        optionsGrid.setHgap(12);
        optionsGrid.setVgap(4);
        optionsGrid.add(alignPlanetsCheckbox, 0, 0);

        Label legend = new Label("""
                M = mass [M_Earth]
                R = body radius [px]
                V = speed [km/s eq]
                A = acceleration [m/s² eq]
                X/Y = physical position [AU]
                Nearest = closest body and distance [AU]""");
        legend.setStyle("-fx-text-fill: #b8b8c8; -fx-font-family: monospace; -fx-font-size: 11px; -fx-padding: 0 0 6 0;");

        sidebar.getChildren().addAll(title, btnReset, optionsGrid, legend, dashboardList);

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
                        b.addTrailPoint(screenXForPhysics(b.x, b.y), screenYForPhysics(b.x, b.y));
                    }
                }

                if (frameCounter % 5 == 0) {
                    updateDashboard();
                }

                gc.setFill(Color.rgb(3, 3, 10, 0.35));
                gc.fillRect(0, 0, canvasWidth, canvasHeight);

                for (Body b : bodies) {
                    double screenX = screenXForPhysics(b.x, b.y);
                    double screenY = screenYForPhysics(b.x, b.y);
                    gc.setStroke(b.color.deriveColor(0, 1, 1, 0.3));
                    gc.setLineWidth(1.0);
                    for (int k = 0; k < b.trailX.size() - 1; k++) {
                        gc.strokeLine(b.trailX.get(k), b.trailY.get(k), b.trailX.get(k+1), b.trailY.get(k+1));
                    }

                    gc.setFill(b.color);
                    gc.fillOval(screenX - b.radius, screenY - b.radius, b.radius * 2, b.radius * 2);
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
                    double previewSpeed = speedToKilometersPerSecond(vectorLength * physicsUnitsPerScreenPixelAtEarthOrbit() * VELOCITY_SCALE);

                    gc.setStroke(Color.RED);
                    gc.setLineWidth(2.0);
                    gc.strokeLine(bodyX, bodyY, vectorEndX, vectorEndY);

                    gc.setFill(Color.WHITE);
                    gc.fillText(String.format("Masa: %.1f M_Earth | V: %.2f km/s", createdMass, previewSpeed), vectorEndX + 10, vectorEndY);
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
            double speed = speedToKilometersPerSecond(b.getSpeed());
            double accelerationMetersPerSecondSquared = accelerationToMetersPerSecondSquared(acceleration);
            double xAu = physicsDistanceToAu(b.x);
            double yAu = physicsDistanceToAu(b.y);
            String nearestText = nearest == null
                    ? "Nearest: -"
                    : String.format("Nearest: %s %.3fAU", nearest.name, physicsDistanceToAu(distanceBetween(b, nearest)));
            Label lbl = new Label(String.format(
                    "%-10s | M:%8.2f | R:%4.1f%nV:%7.2f | A:%7.3f | X:%6.1f Y:%6.1f%n%s",
                    b.name, b.mass, b.radius,
                    speed, accelerationMetersPerSecondSquared, xAu, yAu,
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

    private double screenXForPhysics(double physicsX, double physicsY) {
        double physicsDistance = Math.sqrt(physicsX * physicsX + physicsY * physicsY);
        if (physicsDistance <= 0.000001) {
            return canvasWidth / 2.0;
        }

        double screenDistance = screenRadiusForPhysicsDistance(physicsDistance);
        return canvasWidth / 2.0 + (physicsX / physicsDistance) * screenDistance;
    }

    private double screenYForPhysics(double physicsX, double physicsY) {
        double physicsDistance = Math.sqrt(physicsX * physicsX + physicsY * physicsY);
        if (physicsDistance <= 0.000001) {
            return canvasHeight / 2.0;
        }

        double screenDistance = screenRadiusForPhysicsDistance(physicsDistance);
        return canvasHeight / 2.0 + (physicsY / physicsDistance) * screenDistance;
    }

    private double physicsXForScreen(double screenX, double screenY) {
        double dx = screenX - canvasWidth / 2.0;
        double dy = screenY - canvasHeight / 2.0;
        double screenDistance = Math.sqrt(dx * dx + dy * dy);
        if (screenDistance <= 0.000001) {
            return 0.0;
        }

        double physicsDistance = physicsRadiusForScreenDistance(screenDistance);
        return dx / screenDistance * physicsDistance;
    }

    private double physicsYForScreen(double screenX, double screenY) {
        double dx = screenX - canvasWidth / 2.0;
        double dy = screenY - canvasHeight / 2.0;
        double screenDistance = Math.sqrt(dx * dx + dy * dy);
        if (screenDistance <= 0.000001) {
            return 0.0;
        }

        double physicsDistance = physicsRadiusForScreenDistance(screenDistance);
        return dy / screenDistance * physicsDistance;
    }

    private double screenRadiusForPhysicsDistance(double physicsDistance) {
        double au = physicsDistanceToAu(physicsDistance);
        if (au <= MERCURY_AU) {
            return MIN_PLANET_ORBIT_RADIUS * au / MERCURY_AU;
        }

        return orbitRadiusForAu(au);
    }

    private double physicsRadiusForScreenDistance(double screenDistance) {
        if (screenDistance <= MIN_PLANET_ORBIT_RADIUS) {
            return physicalRadiusForAu(MERCURY_AU) * screenDistance / MIN_PLANET_ORBIT_RADIUS;
        }

        double maxOrbitRadius = Math.max(MIN_PLANET_ORBIT_RADIUS + 1.0, Math.min(canvasWidth, canvasHeight) / 2.0 - ORBIT_EDGE_PADDING);
        double normalized = (screenDistance - MIN_PLANET_ORBIT_RADIUS) / (maxOrbitRadius - MIN_PLANET_ORBIT_RADIUS);
        normalized = Math.clamp(normalized, 0.0, 1.0);
        double minLog = Math.log(MERCURY_AU);
        double maxLog = Math.log(NEPTUNE_AU);
        return physicalRadiusForAu(Math.exp(minLog + normalized * (maxLog - minLog)));
    }

    private double physicsUnitsPerScreenPixelAtEarthOrbit() {
        return physicalRadiusForAu(EARTH_AU) / orbitRadiusForAu(EARTH_AU);
    }

    private double physicsDistanceToAu(double physicsDistance) {
        return physicsDistance / PHYSICS_UNITS_PER_AU;
    }

    private double speedToKilometersPerSecond(double simulationSpeed) {
        double earthSimulationSpeed = Math.sqrt(G * (SUN_MASS + 1.0) / physicalRadiusForAu(EARTH_AU));
        return simulationSpeed / earthSimulationSpeed * EARTH_ORBITAL_SPEED_KM_PER_SECOND;
    }

    private double accelerationToMetersPerSecondSquared(double simulationAcceleration) {
        double realSecondsPerSimulationSecond = realSecondsPerSimulationSecond();
        double kilometersPerSimulationUnit = ASTRONOMICAL_UNIT_KM / PHYSICS_UNITS_PER_AU;
        double kilometersPerSecondSquared = simulationAcceleration * kilometersPerSimulationUnit
                / (realSecondsPerSimulationSecond * realSecondsPerSimulationSecond);
        return kilometersPerSecondSquared * 1000.0;
    }

    private double realSecondsPerSimulationSecond() {
        double earthSimulationSpeed = Math.sqrt(G * (SUN_MASS + 1.0) / physicalRadiusForAu(EARTH_AU));
        return (EARTH_ORBITAL_SPEED_KM_PER_SECOND / earthSimulationSpeed)
                / (ASTRONOMICAL_UNIT_KM / PHYSICS_UNITS_PER_AU);
    }

    private double radiusForCreatedMass(double bodyMass) {
        return Math.clamp(4.0 + Math.pow(bodyMass, 1.0 / 3.0) * 1.8, 3.0, MAX_CREATED_BODY_RADIUS);
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

        double cx = 0.0;
        double cy = 0.0;

        Body sun = new Body("Sun", cx, cy, 0, 0, SUN_MASS, 16, Color.GOLD);
        bodies.add(sun);

        double[] orbitAngles = resetOrbitAngles();

        // Planets use real mass ratios in Earth masses, real eccentricities, and Keplerian start velocities.
        addKeplerPlanet("Mercury", cx, cy, MERCURY_AU, MERCURY_ECCENTRICITY, 0.055, 3.0, Color.GRAY, orbitAngles[0]);
        addKeplerPlanet("Venus",   cx, cy, VENUS_AU,   VENUS_ECCENTRICITY,   0.815, 4.5, Color.BEIGE, orbitAngles[1]);
        addKeplerPlanet("Earth",   cx, cy, EARTH_AU,   EARTH_ECCENTRICITY,   1.000, 5.0, Color.DODGERBLUE, orbitAngles[2]);
        addKeplerPlanet("Mars",    cx, cy, MARS_AU,    MARS_ECCENTRICITY,    0.107, 4.0, Color.INDIANRED, orbitAngles[3]);
        addKeplerPlanet("Jupiter", cx, cy, JUPITER_AU, JUPITER_ECCENTRICITY, 317.8, 11.0, Color.PERU, orbitAngles[4]);
        addKeplerPlanet("Saturn",  cx, cy, SATURN_AU,  SATURN_ECCENTRICITY,  95.2,  9.0, Color.BURLYWOOD, orbitAngles[5]);
        addKeplerPlanet("Uranus",  cx, cy, URANUS_AU,  URANUS_ECCENTRICITY,  14.5,  7.0, Color.LIGHTBLUE, orbitAngles[6]);
        addKeplerPlanet("Neptune", cx, cy, NEPTUNE_AU, NEPTUNE_ECCENTRICITY, 17.1,  7.0, Color.ROYALBLUE, orbitAngles[7]);

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

    private double[] resetOrbitAngles() {
        double[] angles = new double[8];
        if (alignPlanetsOnReset) {
            return angles;
        }

        double baseAngle = Math.random() * Math.PI * 2.0;
        double jitterRange = Math.PI / 45.0;
        for (int i = 0; i < angles.length; i++) {
            double jitter = (Math.random() * 2.0 - 1.0) * jitterRange;
            angles[i] = baseAngle + STABLE_ORBIT_PHASES[i] + jitter;
        }

        return angles;
    }

    private double orbitRadiusForAu(double semiMajorAxisAu) {
        double minLog = Math.log(MERCURY_AU);
        double maxLog = Math.log(NEPTUNE_AU);
        double orbitLog = Math.log(Math.max(MERCURY_AU, semiMajorAxisAu));
        double normalized = (orbitLog - minLog) / (maxLog - minLog);
        double maxOrbitRadius = Math.max(MIN_PLANET_ORBIT_RADIUS + 1.0, Math.min(canvasWidth, canvasHeight) / 2.0 - ORBIT_EDGE_PADDING);
        return MIN_PLANET_ORBIT_RADIUS + normalized * (maxOrbitRadius - MIN_PLANET_ORBIT_RADIUS);
    }

    private double physicalRadiusForAu(double semiMajorAxisAu) {
        return semiMajorAxisAu * PHYSICS_UNITS_PER_AU;
    }

    private void addKeplerPlanet(String name, double cx, double cy, double semiMajorAxisAu, double eccentricity, double mass, double size, Color color, double trueAnomaly) {
        double semiMajorAxis = physicalRadiusForAu(semiMajorAxisAu);
        double boundedEccentricity = Math.clamp(eccentricity, 0.0, 0.95);
        double semiLatusRectum = semiMajorAxis * (1.0 - boundedEccentricity * boundedEccentricity);
        double radiusFromFocus = semiLatusRectum / (1.0 + boundedEccentricity * Math.cos(trueAnomaly));
        double mu = G * (SUN_MASS + mass);
        double specificAngularMomentum = Math.sqrt(mu * semiLatusRectum);

        double x = cx + radiusFromFocus * Math.cos(trueAnomaly);
        double y = cy + radiusFromFocus * Math.sin(trueAnomaly);
        double vx = -mu / specificAngularMomentum * Math.sin(trueAnomaly);
        double vy = mu / specificAngularMomentum * (boundedEccentricity + Math.cos(trueAnomaly));
        bodies.add(new Body(name, x, y, vx, vy, mass, size, color));
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
