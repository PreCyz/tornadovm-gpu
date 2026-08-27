package pawg.heatdistribution;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import pawg.nbody.TornadoDeviceSelector;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.annotations.Parallel;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

public class HeatDistributionFX extends Application {

    private static final int DIM = 512;
    private static final float ALPHA = 0.15f; // Stable diffusion coefficient.
    private static final int STEPS_PER_FRAME = 20; // Number of steps computed per frame.
    private static final int BRUSH_RADIUS = 12;

    private final float[] gridA = new float[DIM * DIM];
    private final float[] gridB = new float[DIM * DIM];

    private TornadoExecutionPlan planAtoB;
    private TornadoExecutionPlan planBtoA;

    private PixelWriter pixelWriter;
    private final int[] pixelBuffer = new int[DIM * DIM];

    private boolean isMouseDown = false;
    private double mouseX = -1;
    private double mouseY = -1;

    public static void computeHeatStep(float[] current, float[] next, int dim, float alpha) {
        for (@Parallel int i = 1; i < dim - 1; i++) {
            for (@Parallel int j = 1; j < dim - 1; j++) {
                int idx = i * dim + j;
                int top = (i - 1) * dim + j;
                int bottom = (i + 1) * dim + j;
                int left = i * dim + (j - 1);
                int right = i * dim + (j + 1);

                next[idx] = current[idx] + alpha * (
                        current[top] + current[bottom] +
                                current[left] + current[right] -
                                4.0f * current[idx]
                );
            }
        }
    }

    public static void renderHeatPixels(float[] grid, int[] output, int size) {
        for (@Parallel int i = 0; i < size; i++) {
            float temp = grid[i];
            if (temp < 0.0f) {
                temp = 0.0f;
            } else if (temp > 100.0f) {
                temp = 100.0f;
            }

            float norm = temp / 100.0f;
            float red = norm * 2.0f - 0.5f;
            float green = norm * 3.0f - 2.0f;
            float blue = 1.0f - norm * 2.0f;

            if (red < 0.0f) {
                red = 0.0f;
            } else if (red > 1.0f) {
                red = 1.0f;
            }

            if (green < 0.0f) {
                green = 0.0f;
            } else if (green > 1.0f) {
                green = 1.0f;
            }

            if (blue < 0.0f) {
                blue = 0.0f;
            } else if (blue > 1.0f) {
                blue = 1.0f;
            }

            int r = (int) (red * 255.0f);
            int g = (int) (green * 255.0f);
            int b = (int) (blue * 255.0f);
            output[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
    }

    @Override
    public void start(Stage primaryStage) {
        var selectedTornadoDevice = TornadoDeviceSelector.selectDevice(primaryStage);

        initHeatSources();

        // Create two separate execution plans for buffer swapping (A->B and B->A).
        TaskGraph tgAtoB = new TaskGraph("tgAtoB")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, gridA)
                .task("taskAtoB", HeatDistributionFX::computeHeatStep, gridA, gridB, DIM, ALPHA)
                .task("renderB", HeatDistributionFX::renderHeatPixels, gridB, pixelBuffer, DIM * DIM)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, gridB, pixelBuffer);
        planAtoB = TornadoDeviceSelector.applyDevice(new TornadoExecutionPlan(tgAtoB.snapshot()), selectedTornadoDevice);

        TaskGraph tgBtoA = new TaskGraph("tgBtoA")
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, gridB)
                .task("taskBtoA", HeatDistributionFX::computeHeatStep, gridB, gridA, DIM, ALPHA)
                .task("renderA", HeatDistributionFX::renderHeatPixels, gridA, pixelBuffer, DIM * DIM)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, gridA, pixelBuffer);
        planBtoA = TornadoDeviceSelector.applyDevice(new TornadoExecutionPlan(tgBtoA.snapshot()), selectedTornadoDevice);

        WritableImage writableImage = new WritableImage(DIM, DIM);
        pixelWriter = writableImage.getPixelWriter();
        ImageView imageView = new ImageView(writableImage);

        imageView.setFitWidth(800);
        imageView.setFitHeight(800);
        imageView.setPreserveRatio(true);

        imageView.setOnMousePressed(this::handleMouseEvent);
        imageView.setOnMouseDragged(this::handleMouseEvent);
        imageView.setOnMouseReleased(_ -> isMouseDown = false);

        StackPane root = new StackPane(imageView);
        Scene scene = new Scene(root, 800, 800, Color.BLACK);

        primaryStage.setTitle("Heat distribution");
        primaryStage.setScene(scene);
        primaryStage.show();

        AnimationTimer timer = new AnimationTimer() {
            private boolean useAtoB = true;

            @Override
            public void handle(long now) {
                if (isMouseDown) {
                    injectHeatAtMouse(imageView);
                }

                // Execute several simulation substeps per frame for a smooth effect.
                for (int step = 0; step < STEPS_PER_FRAME; step++) {
                    if (useAtoB) {
                        planAtoB.execute();
                    } else {
                        planBtoA.execute();
                    }
                    useAtoB = !useAtoB;
                }

                updateImageView();
            }
        };
        timer.start();
    }

    private void handleMouseEvent(MouseEvent event) {
        isMouseDown = true;
        mouseX = event.getX();
        mouseY = event.getY();
    }

    private void injectHeatAtMouse(ImageView imageView) {
        double scaleX = DIM / imageView.getBoundsInLocal().getWidth();
        double scaleY = DIM / imageView.getBoundsInLocal().getHeight();

        int gridX = (int) (mouseX * scaleX);
        int gridY = (int) (mouseY * scaleY);

        for (int i = -BRUSH_RADIUS; i <= BRUSH_RADIUS; i++) {
            for (int j = -BRUSH_RADIUS; j <= BRUSH_RADIUS; j++) {
                int py = gridY + i;
                int px = gridX + j;

                if (px > 0 && px < DIM - 1 && py > 0 && py < DIM - 1) {
                    if (i * i + j * j <= BRUSH_RADIUS * BRUSH_RADIUS) {
                        gridA[py * DIM + px] = 100.0f;
                        gridB[py * DIM + px] = 100.0f;
                    }
                }
            }
        }
    }

    private void initHeatSources() {
        int cx = DIM / 2;
        int cy = DIM / 2;
        int r = 25;
        for (int i = cx - r; i <= cx + r; i++) {
            for (int j = cy - r; j <= cy + r; j++) {
                if ((i - cx) * (i - cx) + (j - cy) * (j - cy) <= r * r) {
                    gridA[i * DIM + j] = 100.0f;
                    gridB[i * DIM + j] = 100.0f;
                }
            }
        }
    }

    private void updateImageView() {
        pixelWriter.setPixels(0, 0, DIM, DIM,
                javafx.scene.image.PixelFormat.getIntArgbInstance(),
                pixelBuffer, 0, DIM);
    }

    static void main(String[] args) {
        launch(args);
    }
}
