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
import pawg.nbody.TornadoDeviceSelector;
import uk.ac.manchester.tornado.api.*;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;
import uk.ac.manchester.tornado.api.math.TornadoMath;

public class SolarSystemGPU extends Application {

    private static final int WIDTH = 880;
    private static final int HEIGHT = 880;

    private static final float MERCURY_ORBIT = 55.0f;
    private static final float VENUS_ORBIT   = 90.0f;
    private static final float EARTH_ORBIT   = 130.0f;
    private static final float MARS_ORBIT    = 175.0f;
    private static final float JUPITER_ORBIT = 235.0f;
    private static final float SATURN_ORBIT  = 300.0f;
    private static final float URANUS_ORBIT  = 360.0f;
    private static final float NEPTUNE_ORBIT = 415.0f;

    private final int[] pixelBuffer = new int[WIDTH * HEIGHT];

    // GPU parameter buffer:
    // [0]=sunX, [1]=sunY,
    // [2]=mercX, [3]=mercY, [4]=venusX, [5]=venusY,
    // [6]=earthX, [7]=earthY, [8]=earthRot,
    // [9]=marsX, [10]=marsY,
    // [11]=jupX, [12]=jupY,
    // [13]=satX, [14]=satY,
    // [15]=uranX, [16]=uranY,
    // [17]=nepX, [18]=nepY
    private final float[] params = new float[19];

    private TornadoExecutionPlan executionPlan;
    private PixelWriter pixelWriter;

    public static void renderSystemKernel(int[] output, float[] p, int width, int height) {
        float sunX     = p[0];
        float sunY     = p[1];
        float mercX    = p[2];  float mercY    = p[3];
        float venusX   = p[4];  float venusY   = p[5];
        float earthX   = p[6];  float earthY   = p[7];  float earthRot = p[8];
        float marsX    = p[9];  float marsY    = p[10];
        float jupX     = p[11]; float jupY     = p[12];
        float satX     = p[13]; float satY     = p[14];
        float uranX    = p[15]; float uranY    = p[16];
        float nepX     = p[17]; float nepY     = p[18];

        // Body radii reduced to keep the full system readable.
        float sunR   = 22.0f;
        float mercR  = 3.0f;
        float venusR = 5.0f;
        float earthR = 6.0f;
        float marsR  = 4.0f;
        float jupR   = 13.0f;
        float satR   = 10.0f;
        float uranR  = 8.0f;
        float nepR   = 8.0f;

        float sunRSq   = sunR * sunR;
        float mercRSq  = mercR * mercR;
        float venusRSq = venusR * venusR;
        float earthRSq = earthR * earthR;
        float marsRSq  = marsR * marsR;
        float jupRSq   = jupR * jupR;
        float satRSq   = satR * satR;
        float uranRSq  = uranR * uranR;
        float nepRSq   = nepR * nepR;

        for (@Parallel int y = 0; y < height; y++) {
            for (@Parallel int x = 0; x < width; x++) {
                int idx = y * width + x;

                float dxSun = x - sunX; float dySun = y - sunY;
                float distSunSq = dxSun * dxSun + dySun * dySun;

                float distMercSq  = (x - mercX) * (x - mercX) + (y - mercY) * (y - mercY);
                float distVenusSq = (x - venusX) * (x - venusX) + (y - venusY) * (y - venusY);
                float distEarthSq = (x - earthX) * (x - earthX) + (y - earthY) * (y - earthY);
                float distMarsSq  = (x - marsX) * (x - marsX) + (y - marsY) * (y - marsY);
                float distJupSq   = (x - jupX) * (x - jupX) + (y - jupY) * (y - jupY);
                float distSatSq   = (x - satX) * (x - satX) + (y - satY) * (y - satY);
                float distUranSq  = (x - uranX) * (x - uranX) + (y - uranY) * (y - uranY);
                float distNepSq   = (x - nepX) * (x - nepX) + (y - nepY) * (y - nepY);

                // 1. Sun and glow
                if (distSunSq <= sunRSq) {
                    output[idx] = 0xFFFFD700;
                } else if (distSunSq < sunRSq * 2.0f) {
                    float distSun = TornadoMath.sqrt(distSunSq);
                    float glow = 1.0f - ((distSun - sunR) / sunR);
                    int alpha = (int) (glow * 180.0f);
                    output[idx] = (alpha << 24) | (255 << 16) | ((int) (200 * glow) << 8);
                }
                // 2. Mercury (gray)
                else if (distMercSq <= mercRSq) {
                    output[idx] = 0xFFA0A0A0;
                }
                // 3. Venus (yellow-beige)
                else if (distVenusSq <= venusRSq) {
                    output[idx] = 0xFFE3BB76;
                }
                // 4. Earth (blue with rotating land)
                else if (distEarthSq <= earthRSq) {
                    float localX = x - earthX;
                    float localY = y - earthY;
                    float rotX = localX * TornadoMath.cos(earthRot) - localY * TornadoMath.sin(earthRot);
                    float rotY = localX * TornadoMath.sin(earthRot) + localY * TornadoMath.cos(earthRot);

                    if ((rotX > -3.0f && rotX < 2.0f &&
                            rotY > -4.0f && rotY < 3.0f)) {
                        output[idx] = 0xFF2E8B57;
                    } else {
                        output[idx] = 0xFF1E90FF;
                    }
                }
                // 5. Mars (red/rust)
                else if (distMarsSq <= marsRSq) {
                    output[idx] = 0xFFC1440E;
                }
                // 6. Jupiter (atmospheric bands)
                else if (distJupSq <= jupRSq) {
                    float localY = y - jupY;
                    float band = TornadoMath.sin(localY * 0.6f);
                    if (band > 0.2f) {
                        output[idx] = 0xFFC87D55;
                    } else {
                        output[idx] = 0xFFE0C9A6;
                    }
                }
                // 7. Saturn and rings
                else if (distSatSq <= satRSq) {
                    output[idx] = 0xFFE2C58F;
                } else if (distSatSq >= satRSq * 1.6f && distSatSq <= satRSq * 4.5f) {
                    float distSat = TornadoMath.sqrt(distSatSq);
                    if (distSat > 14.0f && distSat < 19.0f) {
                        output[idx] = 0xCCD4B27C; // A/B ring
                    } else if (distSat >= 19.0f && distSat <= 21.0f) {
                        output[idx] = 0x10000000; // Cassini division
                    } else if (distSat > 21.0f && distSat < 23.0f) {
                        output[idx] = 0xAAAE9664; // Outer ring
                    } else {
                        output[idx] = 0xFF020208;
                    }
                }
                // 8. Uranus (light blue/turquoise)
                else if (distUranSq <= uranRSq) {
                    output[idx] = 0xFF4FD0E7;
                } else if (((x - uranX) * (x - uranX)) / (4.0f * 4.0f) +
                        ((y - uranY) * (y - uranY)) / (20.0f * 20.0f) <= 1.0f &&
                        ((x - uranX) * (x - uranX)) / (2.2f * 2.2f) +
                                ((y - uranY) * (y - uranY)) / (17.0f * 17.0f) >= 1.0f) {
                    output[idx] = 0xAA9EDDE8;
                }
                // 9. Neptune (dark blue)
                else if (distNepSq <= nepRSq) {
                    output[idx] = 0xFF274687;
                }
                // 10. Space background and orbit lines
                else {
                    float distSun = TornadoMath.sqrt(distSunSq);

                    float diffMerc  = TornadoMath.abs(distSun - 55.0f);
                    float diffVenus = TornadoMath.abs(distSun - 90.0f);
                    float diffEarth = TornadoMath.abs(distSun - 130.0f);
                    float diffMars  = TornadoMath.abs(distSun - 175.0f);
                    float diffJup   = TornadoMath.abs(distSun - 235.0f);
                    float diffSat   = TornadoMath.abs(distSun - 300.0f);
                    float diffUran  = TornadoMath.abs(distSun - 360.0f);
                    float diffNep   = TornadoMath.abs(distSun - 415.0f);
                    float orbitLineWidth = 0.7f;

                    if (diffMerc < orbitLineWidth || diffVenus < orbitLineWidth || diffEarth < orbitLineWidth ||
                            diffMars < orbitLineWidth || diffJup < orbitLineWidth   || diffSat < orbitLineWidth   ||
                            diffUran < orbitLineWidth || diffNep < orbitLineWidth) {
                        output[idx] = 0x20FFFFFF; // Subtle orbit lines
                    } else {
                        output[idx] = 0xFF020208; // Black space
                    }
                }
            }
        }
    }

    @Override
    public void start(Stage primaryStage) {
        var selectedTornadoDevice = TornadoDeviceSelector.selectDevice(primaryStage);

        float sunX = WIDTH / 2.0f;
        float sunY = HEIGHT / 2.0f;

        params[0] = sunX;
        params[1] = sunY;

        TaskGraph taskGraph = new TaskGraph("solarSystemGraph")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, params)
                .task("renderTask", SolarSystemGPU::renderSystemKernel, pixelBuffer, params, WIDTH, HEIGHT)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, pixelBuffer);

        ImmutableTaskGraph immutableTaskGraph = taskGraph.snapshot();
        executionPlan = selectedTornadoDevice.map(it -> TornadoDeviceSelector.applyDevice(new TornadoExecutionPlan(immutableTaskGraph), it))
                .orElseGet(() -> new TornadoExecutionPlan(immutableTaskGraph));

        WritableImage writableImage = new WritableImage(WIDTH, HEIGHT);
        pixelWriter = writableImage.getPixelWriter();
        ImageView imageView = new ImageView(writableImage);
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        imageView.setFitWidth(screenBounds.getWidth());
        imageView.setFitHeight(screenBounds.getHeight());
        imageView.setPreserveRatio(true);

        StackPane root = new StackPane(imageView);
        Scene scene = new Scene(root, screenBounds.getWidth(), screenBounds.getHeight(), Color.BLACK);

        primaryStage.setTitle("Solar System");
        primaryStage.setScene(scene);
        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());
        primaryStage.setResizable(false);
        primaryStage.show();

        AnimationTimer timer = new AnimationTimer() {
            private float mercAngle  = 0.0f;
            private float venusAngle = 0.0f;
            private float earthAngle = 0.0f;
            private float earthRotation = 0.0f;
            private float marsAngle  = 0.0f;
            private float jupAngle   = 0.0f;
            private float satAngle   = 0.0f;
            private float uranAngle  = 0.0f;
            private float nepAngle   = 0.0f;

            @Override
            public void handle(long now) {
                // Orbital speeds approximated from Kepler-like scaling.
                mercAngle  += 0.040f;
                venusAngle += 0.024f;
                earthAngle += 0.015f;
                earthRotation += 0.080f;
                marsAngle  += 0.010f;
                jupAngle   += 0.005f;
                satAngle   += 0.003f;
                uranAngle  += 0.0018f;
                nepAngle   += 0.0010f;

                // Planet coordinates
                params[2]  = (float) (sunX + MERCURY_ORBIT * Math.cos(mercAngle));
                params[3]  = (float) (sunY + MERCURY_ORBIT * Math.sin(mercAngle));

                params[4]  = (float) (sunX + VENUS_ORBIT * Math.cos(venusAngle));
                params[5]  = (float) (sunY + VENUS_ORBIT * Math.sin(venusAngle));

                params[6]  = (float) (sunX + EARTH_ORBIT * Math.cos(earthAngle));
                params[7]  = (float) (sunY + EARTH_ORBIT * Math.sin(earthAngle));
                params[8]  = earthRotation;

                params[9]  = (float) (sunX + MARS_ORBIT * Math.cos(marsAngle));
                params[10] = (float) (sunY + MARS_ORBIT * Math.sin(marsAngle));

                params[11] = (float) (sunX + JUPITER_ORBIT * Math.cos(jupAngle));
                params[12] = (float) (sunY + JUPITER_ORBIT * Math.sin(jupAngle));

                params[13] = (float) (sunX + SATURN_ORBIT * Math.cos(satAngle));
                params[14] = (float) (sunY + SATURN_ORBIT * Math.sin(satAngle));

                params[15] = (float) (sunX + URANUS_ORBIT * Math.cos(uranAngle));
                params[16] = (float) (sunY + URANUS_ORBIT * Math.sin(uranAngle));

                params[17] = (float) (sunX + NEPTUNE_ORBIT * Math.cos(nepAngle));
                params[18] = (float) (sunY + NEPTUNE_ORBIT * Math.sin(nepAngle));

                // Render the frame on the GPU.
                executionPlan.execute();

                // Refresh the JavaFX frame.
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
