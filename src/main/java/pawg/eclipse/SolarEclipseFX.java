package pawg.eclipse;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import pawg.nbody.TornadoDeviceSelector;
import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;

import java.util.Optional;

public class SolarEclipseFX extends Application {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    // Input parameters.
    private static double ECLIPSE_DURATION_SECONDS = 12.0; // Animation duration.
    private static double MAX_COVERAGE_PERCENT = 100.0;     // Eclipse coverage percent (0.0 - 100.0%).

    private final int[] pixelBuffer = new int[WIDTH * HEIGHT];
    private final float[] params = new float[5]; // [sunX, sunY, moonX, moonY, radius]

    private TornadoExecutionPlan executionPlan;
    private PixelWriter pixelWriter;

    // Stores the simulation start time.
    private long startTime;

    public static void renderEclipseKernel(int[] output, float[] p, int width, int height) {
        float sunX = p[0];
        float sunY = p[1];
        float moonX = p[2];
        float moonY = p[3];
        float r = p[4];

        for (@Parallel int y = 0; y < height; y++) {
            for (@Parallel int x = 0; x < width; x++) {
                int idx = y * width + x;

                float dxSun = x - sunX;
                float dySun = y - sunY;
                float distSunSq = dxSun * dxSun + dySun * dySun;

                float dxMoon = x - moonX;
                float dyMoon = y - moonY;
                float distMoonSq = dxMoon * dxMoon + dyMoon * dyMoon;

                float rSq = r * r;

                // 1. Moon disk.
                if (distMoonSq <= rSq) {
                    output[idx] = 0xFF000000;
                }
                // 2. Sun disk.
                else if (distSunSq <= rSq) {
                    output[idx] = 0xFFFFFFA0;
                }
                // 3. Solar corona and sky background.
                else {
                    float distSun = TornadoMath.sqrt(distSunSq);

                    if (distSun < r * 2.5f) {
                        float coronaFactor = 1.0f - ((distSun - r) / (r * 1.5f));
                        coronaFactor = coronaFactor * coronaFactor;

                        float distMoon = TornadoMath.sqrt(distMoonSq);
                        if (distMoon < r * 1.5f) {
                            float mask = (distMoon - (r * 0.8f)) / (r * 0.7f);
                            if (mask < 0.0f) mask = 0.0f;
                            coronaFactor *= mask;
                        }

                        int intensity = (int) (coronaFactor * 255.0f);
                        if (intensity > 255) intensity = 255;
                        if (intensity < 0) intensity = 0;

                        int red = (int) (intensity * 1.0f);
                        int green = (int) (intensity * 0.9f);
                        int blue = (int) (intensity * 0.7f);

                        if (red > 255) red = 255;
                        if (green > 255) green = 255;
                        if (blue > 255) blue = 255;

                        output[idx] = (0xFF << 24) | (red << 16) | (green << 8) | blue;
                    } else {
                        output[idx] = 0xFF050510;
                    }
                }
            }
        }
    }

    @Override
    public void start(Stage primaryStage) {
        var selectedTornadoDevice = TornadoDeviceSelector.selectDevice(primaryStage);

        float radius = 90.0f;
        float sunX = WIDTH / 2.0f;
        float sunY = HEIGHT / 2.0f;

        double coverageClamped = Math.clamp(MAX_COVERAGE_PERCENT, 0.0, 100.0);
        double coverageFraction = coverageClamped / 100.0;

        float moonOffsetY = (float) ((1.0 - coverageFraction) * (2.0 * radius));
        float moonY = sunY + moonOffsetY;

        params[0] = sunX;
        params[1] = sunY;
        params[4] = radius;

        TaskGraph taskGraph = new TaskGraph("eclipseGraph")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, params)
                .task("renderTask", SolarEclipseFX::renderEclipseKernel, pixelBuffer, params, WIDTH, HEIGHT)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, pixelBuffer);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        executionPlan = selectedTornadoDevice.map(it -> TornadoDeviceSelector.applyDevice(new TornadoExecutionPlan(immutableTaskGraph), it))
                .orElseGet(() -> new TornadoExecutionPlan(immutableTaskGraph));

        WritableImage writableImage = new WritableImage(WIDTH, HEIGHT);
        pixelWriter = writableImage.getPixelWriter();
        ImageView imageView = new ImageView(writableImage);

        // Reset handling on mouse click.
        imageView.setOnMouseClicked(_ -> startTime = System.nanoTime()); // Reset the start time.

        StackPane root = new StackPane(imageView);
        Scene scene = new Scene(root, WIDTH, HEIGHT, Color.BLACK);

        primaryStage.setTitle(String.format(
                "Solar Eclipse (Coverage: %.1f%%, Total time: %.1f sec) [Click to reset]",
                coverageClamped, ECLIPSE_DURATION_SECONDS));
        primaryStage.setScene(scene);
        primaryStage.show();

        // Initialize the start time.
        startTime = System.nanoTime();

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double elapsedSeconds = (now - startTime) / 1e9;
                double progress = elapsedSeconds / ECLIPSE_DURATION_SECONDS;

                if (progress > 1.0) progress = 1.0;

                float startX = -radius * 2.5f;
                float endX = WIDTH + radius * 2.5f;

                params[2] = (float) (startX + progress * (endX - startX)); // X trajectory.
                params[3] = moonY;                                       // Fixed Y.

                executionPlan.execute();

                pixelWriter.setPixels(0, 0, WIDTH, HEIGHT,
                        javafx.scene.image.PixelFormat.getIntArgbInstance(),
                        pixelBuffer, 0, WIDTH);
            }
        };
        timer.start();
    }

    public static void main(String[] args) {
        if (args != null && args.length > 1) {
            ECLIPSE_DURATION_SECONDS = Optional.ofNullable(args[1]).map(Double::parseDouble).orElse(ECLIPSE_DURATION_SECONDS);
        }
        if (args != null && args.length > 2) {
            MAX_COVERAGE_PERCENT = Optional.ofNullable(args[2]).map(Double::parseDouble).orElse(MAX_COVERAGE_PERCENT);
        }
        launch(args);
    }
}
