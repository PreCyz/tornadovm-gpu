package pawg.gameoflife;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import pawg.nbody.TornadoDeviceSelector;
import uk.ac.manchester.tornado.api.TaskGraph;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.enums.DataTransferMode;

import java.nio.IntBuffer;
import java.util.Random;

public class GameOfLifeInteractive extends Application {

    public static final Random RANDOM = new Random();
    private static final int WIDTH = 1200;
    private static final int HEIGHT = 880;
    private static final int SIZE = WIDTH * HEIGHT;

    private final int[] gridA = new int[SIZE];
    private final int[] gridB = new int[SIZE];

    private final int[] pixelBuffer = new int[SIZE];
    private static final int COLOR_ALIVE = 0xFF00FF88;
    private static final int COLOR_DEAD = 0xFF111115;

    private TornadoExecutionPlan executionPlan;

    @Override
    public void start(Stage primaryStage) {
        var selectedTornadoDevice = TornadoDeviceSelector.selectDevice(primaryStage);

        for (int i = 0; i < SIZE; i++) {
            gridA[i] = RANDOM.nextFloat() < 0.1 ? 1 : 0;
        }

        // Use DataTransferMode.EVERY_EXECUTION for gridA so mouse changes can be uploaded dynamically while the app runs.
        TaskGraph taskGraph = new TaskGraph("golInteractive")
                // Both arrays (gridA and gridB) must be allocated on the GPU.
                // gridA is transferred on each execution and kept up to date with mouse edits.
                .transferToDevice(DataTransferMode.EVERY_EXECUTION, gridA)
                // gridB must be allocated in VRAM once at startup.
                .transferToDevice(DataTransferMode.FIRST_EXECUTION, gridB)
                // Execute simulation steps in VRAM.
                .task("step1", GameOfLifeKernel::computeNextGeneration, gridA, gridB, WIDTH, HEIGHT)
                .task("step2", GameOfLifeKernel::computeNextGeneration, gridB, gridA, WIDTH, HEIGHT)
                .task("render", GameOfLifeKernel::renderCells, gridA, pixelBuffer, SIZE, COLOR_ALIVE, COLOR_DEAD)
                .transferToHost(DataTransferMode.EVERY_EXECUTION, gridA, pixelBuffer);

        executionPlan = TornadoDeviceSelector.applyDevice(new TornadoExecutionPlan(taskGraph.snapshot()), selectedTornadoDevice);

        createFxView(primaryStage);
    }

    private void createFxView(Stage primaryStage) {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        PixelWriter pixelWriter = gc.getPixelWriter();
        WritablePixelFormat<IntBuffer> format = WritablePixelFormat.getIntArgbInstance();

        // Mouse handling.
        canvas.setOnMouseClicked(this::handleMouseEvent);
        canvas.setOnMouseDragged(this::handleMouseEvent);

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        primaryStage.setTitle("Game of Life - TornadoVM + GPU + JavaFX (Interactive)");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Simulation and rendering loop.
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // If the user draws with the mouse, the next execute() call automatically
                // synchronizes the modified gridA with VRAM because it uses EVERY_EXECUTION.

                executionPlan.execute();

                pixelWriter.setPixels(0, 0, WIDTH, HEIGHT, format, pixelBuffer, 0, WIDTH);
            }
        };

        timer.start();
    }

    // Helper method that handles mouse clicks and dragging.
    private void handleMouseEvent(MouseEvent event) {
        int mouseX = (int) event.getX();
        int mouseY = (int) event.getY();

        // Check whether the coordinates are inside the board.
        if (mouseX >= 0 && mouseX < WIDTH && mouseY >= 0 && mouseY < HEIGHT) {
            // Use a small brush around the cursor for convenience.
            int brushSize = 3;
            for (int dy = -brushSize; dy <= brushSize; dy++) {
                for (int dx = -brushSize; dx <= brushSize; dx++) {
                    int nx = mouseX + dx;
                    int ny = mouseY + dy;
                    if (nx >= 0 && nx < WIDTH && ny >= 0 && ny < HEIGHT) {
                        int index = ny * WIDTH + nx;
                        gridA[index] = 1; // Mouse input makes cells alive.
                    }
                }
            }
        }
    }

    @Override
    public void stop() {
        if (executionPlan != null) {
            executionPlan.freeDeviceMemory();
        }
    }

    static void main(String[] args) {
        launch(args);
    }
}
