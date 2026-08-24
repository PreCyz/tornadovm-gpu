package pawg.gravity;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.*;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;

public class EarthOrbitGPU extends Application {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 800;

    // Earth orbit radius around the Sun.
    private static final float ORBIT_RADIUS = 260.0f;
//    private static final float SUN_RADIUS = 45.0f;
//    private static final float EARTH_RADIUS = 16.0f;

    private final int[] pixelBuffer = new int[WIDTH * HEIGHT];
    // [sunX, sunY, earthX, earthY, earthAngle]
    private final float[] params = new float[5];

    private TornadoExecutionPlan executionPlan;
    private PixelWriter pixelWriter;

    public static void renderOrbitKernel(int[] output, float[] p, int width, int height) {
        float sunX = p[0];
        float sunY = p[1];
        float earthX = p[2];
        float earthY = p[3];
        float earthAngle = p[4];

        float sunRadiusSq = 45.0f * 45.0f;
        float earthRadiusSq = 16.0f * 16.0f;

        for (@Parallel int y = 0; y < height; y++) {
            for (@Parallel int x = 0; x < width; x++) {
                int idx = y * width + x;

                float dxSun = x - sunX;
                float dySun = y - sunY;
                float distSunSq = dxSun * dxSun + dySun * dySun;

                float dxEarth = x - earthX;
                float dyEarth = y - earthY;
                float distEarthSq = dxEarth * dxEarth + dyEarth * dyEarth;

                // 1. Sun disk and glow.
                if (distSunSq <= sunRadiusSq) {
                    output[idx] = 0xFFFFD700; // Golden Sun.
                } else if (distSunSq < sunRadiusSq * 2.5f) {
                    float distSun = TornadoMath.sqrt(distSunSq);
                    float glow = 1.0f - ((distSun - 45.0f) / (45.0f * 1.22f));
                    if (glow < 0.0f) glow = 0.0f;
                    int alpha = (int) (glow * 200.0f);
                    int red = 255;
                    int green = (int) (215 * glow);
                    output[idx] = (alpha << 24) | (red << 16) | (green << 8);
                }
                // 2. Earth disk with simple land/ocean texture and axial rotation.
                else if (distEarthSq <= earthRadiusSq) {
                    // Compute the local pixel position inside Earth.
                    float localX = x - earthX;
                    float localY = y - earthY;

                    // Simple continent rotation effect.
                    float rotX = localX * TornadoMath.cos(earthAngle) - localY * TornadoMath.sin(earthAngle);
                    float rotY = localX * TornadoMath.sin(earthAngle) + localY * TornadoMath.cos(earthAngle);

                    if ((rotX > -8.0f && rotX < 6.0f && rotY > -10.0f && rotY < 8.0f) ||
                            (rotX > 2.0f && rotY < -2.0f)) {
                        output[idx] = 0xFF2E8B57; // Green land.
                    } else {
                        output[idx] = 0xFF1E90FF; // Blue ocean.
                    }
                }
                // 3. Earth atmosphere.
                else if (distEarthSq < earthRadiusSq * 1.5f) {
                    float distEarth = TornadoMath.sqrt(distEarthSq);
                    float atmosphere = 1.0f - ((distEarth - 16.0f) / (16.0f * 0.22f));
                    if (atmosphere < 0.0f) atmosphere = 0.0f;
                    int a = (int) (atmosphere * 150.0f);
                    output[idx] = (a << 24) | (100 << 16) | (200 << 8) | 255;
                }
                // 4. Space background with orbit trace.
                else {
                    float distSun = TornadoMath.sqrt(distSunSq);
                    float orbitDiff = TornadoMath.abs(distSun - 260.0f);

                    if (orbitDiff < 1.0f) {
                        output[idx] = 0x40FFFFFF; // Subtle orbit line.
                    } else {
                        output[idx] = 0xFF020208; // Dark outer space.
                    }
                }
            }
        }
    }

    @Override
    public void start(Stage primaryStage) {
        float sunX = WIDTH / 2.0f;
        float sunY = HEIGHT / 2.0f;

        params[0] = sunX;
        params[1] = sunY;

        TaskGraph taskGraph = new TaskGraph("orbitGraph")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, params)
                .task("renderTask", EarthOrbitGPU::renderOrbitKernel, pixelBuffer, params, WIDTH, HEIGHT)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, pixelBuffer);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        executionPlan = new TornadoExecutionPlan(immutableTaskGraph);

        WritableImage writableImage = new WritableImage(WIDTH, HEIGHT);
        pixelWriter = writableImage.getPixelWriter();
        ImageView imageView = new ImageView(writableImage);
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        imageView.setFitWidth(screenBounds.getWidth());
        imageView.setFitHeight(screenBounds.getHeight());
        imageView.setPreserveRatio(true);

        StackPane root = new StackPane(imageView);
        Scene scene = new Scene(root, screenBounds.getWidth(), screenBounds.getHeight(), Color.BLACK);

        primaryStage.setTitle("Earth orbiting the Sun");
        primaryStage.setScene(scene);
        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());
        primaryStage.setResizable(false);
        primaryStage.show();

        AnimationTimer timer = new AnimationTimer() {
            private float orbitAngle = 0.0f;
            private float earthRotationAngle = 0.0f;

            @Override
            public void handle(long now) {
                // Orbital and axial rotation speeds.
                orbitAngle += 0.015f;
                earthRotationAngle += 0.08f;

                // Compute Earth coordinates along the orbit.
                params[2] = (float) (sunX + ORBIT_RADIUS * Math.cos(orbitAngle));
                params[3] = (float) (sunY + ORBIT_RADIUS * Math.sin(orbitAngle));
                params[4] = earthRotationAngle;

                // Render the frame on the GPU.
                executionPlan.execute();

                // Refresh the canvas.
                pixelWriter.setPixels(0, 0, WIDTH, HEIGHT,
                        javafx.scene.image.PixelFormat.getIntArgbInstance(),
                        pixelBuffer, 0, WIDTH);
            }
        };
        timer.start();
    }

    static void main(String[] args) {
        launch(args);
    }
}
