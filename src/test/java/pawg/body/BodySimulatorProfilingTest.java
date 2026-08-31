package pawg.body;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.TornadoExecutionPlan;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BodySimulatorProfilingTest {

    private static final int BODY_COUNT = Integer.getInteger("bodyprofile.bodies", 8);
    private static final int DURATION_SECONDS = Integer.getInteger("bodyprofile.seconds", 12);

    @BeforeAll
    static void startJavaFx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyStarted) {
            started.countDown();
        }
        assertTrue(started.await(10, TimeUnit.SECONDS));
    }

    @Test
    void profileMergeHeavyGpuWorkerRun() throws Exception {
        assumeTrue(Boolean.getBoolean("bodyprofile.run"),
                "Set -Dbodyprofile.run=true to run the profiling scenario");
        assumeTrue(Boolean.getBoolean("bodygpu.test.gpu"),
                "Set -Dbodygpu.test.gpu=true to run the profiling scenario on a TornadoVM device");

        BodySimulator[] simulatorRef = new BodySimulator[1];
        runOnFx(() -> {
            BodySimulator simulator = new BodySimulator();
            configureForProfiling(simulator);
            invoke(simulator, "startSimulation");
            simulatorRef[0] = simulator;
        });
        BodySimulator simulator = simulatorRef[0];
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(DURATION_SECONDS);
        while (System.nanoTime() < deadline && (boolean) field(simulator, "running")) {
            runOnFx(() -> invoke(simulator, "resolveBodyCollisionsFromSnapshot"));
            Thread.sleep(100L);
        }
        runOnFx(simulator::stop);
    }

    private static void configureForProfiling(BodySimulator simulator) throws Exception {
        set(simulator, "canvas", new Canvas(1024.0, 720.0));
        Object[] trails = (Object[]) field(simulator, "trails");
        Object[] fullTracks = (Object[]) field(simulator, "fullTracks");
        for (int i = 0; i < trails.length; i++) {
            trails[i] = new ArrayDeque<>();
            fullTracks[i] = new ArrayList<>();
        }
        FloatArray params = (FloatArray) field(simulator, "params");
        params.set(0, 100.0f);
        params.set(1, 0.015f);
        params.set(2, 25.0f);
        params.set(3, 0.5f);
        set(simulator, "selectedDevice", TornadoExecutionPlan.getDevice(0, 0));
        set(simulator, "bodyCount", BODY_COUNT);
        ((IntArray) field(simulator, "state")).set(0, BODY_COUNT);
        String[] names = (String[]) field(simulator, "names");
        Color[] colors = (Color[]) field(simulator, "colors");
        for (int i = 0; i < BODY_COUNT; i++) {
            names[i] = "Profile Body " + (i + 1);
            colors[i] = Color.hsb((i * 47.0) % 360.0, 0.80, 1.0);
            invoke(simulator, "setBody", i,
                    (i % 4) * 0.12f, (i / 4) * 0.12f, 0.0f,
                    0.2f - i * 0.03f, 0.4f + i * 0.02f, 0.0f,
                    10.0f + i);
            ((IntArray) field(simulator, "active")).set(i, 1);
        }
    }

    private static void runOnFx(ThrowingRunnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure[0] = throwable;
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static Object field(BodySimulator simulator, String name) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(simulator);
    }

    private static void set(BodySimulator simulator, String name, Object value) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(simulator, value);
    }

    private static void invoke(BodySimulator simulator, String name, Object... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            Object argument = arguments[i];
            if (argument instanceof Integer) {
                types[i] = int.class;
            } else if (argument instanceof Float) {
                types[i] = float.class;
            } else {
                types[i] = argument.getClass();
            }
        }
        Method method = BodySimulator.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(simulator, arguments);
    }
}
