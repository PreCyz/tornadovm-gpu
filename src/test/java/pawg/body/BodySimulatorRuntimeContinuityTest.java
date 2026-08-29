package pawg.body;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodySimulatorRuntimeContinuityTest {

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
    void addingABodyWhileRunningKeepsItDrawableAndReadyForTheNextUpload() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "running", true);
            set(simulator, "planDirty", false);
            invoke(simulator, "addBody");

            assertTrue((boolean) get(simulator, "running"));
            assertFalse((boolean) get(simulator, "planDirty"),
                    "EVERY_EXECUTION host upload must accept the added slot without rebuilding the plan");
            assertEquals(1, (int) get(simulator, "bodyCount"));
            assertEquals(1, ((IntArray) get(simulator, "active")).get(0));
            assertNotNull(((Color[]) get(simulator, "colors"))[0], "a new body must have a visible sphere color");
            assertTrue(((FloatArray) get(simulator, "mass")).get(0) > 0.0f);
            assertEquals(0.0f, array(simulator, "accX").get(0));
            assertEquals(0.0f, array(simulator, "accY").get(0));
            assertEquals(0.0f, array(simulator, "accZ").get(0));
            assertFalse((boolean) get(simulator, "gridGeometryDirty"),
                    "the immediate draw must rebuild the grid for the inserted body's current state");
            assertTrue((int) get(simulator, "gridPointCount") > 0);
            return null;
        });
    }

    @Test
    void collisionKeepsSimulationRunningAndAllowsAColoredReplacementWithoutPlanRebuild() throws Exception {
        onFx(() -> {
            BodySimulator simulator = simulatorWithCanvas();
            set(simulator, "bodyCount", 2);
            set(simulator, "running", true);
            set(simulator, "planDirty", false);
            for (int i = 0; i < 2; i++) {
                array(simulator, "mass").set(i, 10.0f + i);
                ((IntArray) get(simulator, "active")).set(i, 1);
                ((Color[]) get(simulator, "colors"))[i] = i == 0 ? Color.RED : Color.BLUE;
            }
            ((IntArray) get(simulator, "state")).set(0, 2);

            invoke(simulator, "resolveBodyCollisions");

            assertEquals(1, (int) get(simulator, "bodyCount"));
            assertTrue((boolean) get(simulator, "running"));
            assertFalse((boolean) get(simulator, "planDirty"));
            assertTrue((boolean) get(simulator, "gridGeometryDirty"));
            invoke(simulator, "addBody");
            assertEquals(2, (int) get(simulator, "bodyCount"));
            assertNotNull(((Color[]) get(simulator, "colors"))[1], "a reused collision slot must render a sphere");
            return null;
        });
    }

    @Test
    void elapsedClockAccumulatesOnlyWhileResumedAndFormatsWholeSeconds() throws Exception {
        onFx(() -> {
            BodySimulator simulator = new BodySimulator();
            set(simulator, "elapsedTimeLabel", new Label());
            invoke(simulator, "resumeElapsedClock");
            invoke(simulator, "updateElapsedClock", 1_000_000_000L);
            invoke(simulator, "updateElapsedClock", 3_500_000_000L);
            invoke(simulator, "pauseElapsedClock");
            assertEquals(2_500_000_000L, (long) get(simulator, "elapsedAccumulatedNanos"));
            invoke(simulator, "updateElapsedClock", 8_000_000_000L);
            assertEquals(2_500_000_000L, (long) get(simulator, "elapsedAccumulatedNanos"));
            invoke(simulator, "resetElapsedClock");
            assertEquals(0L, (long) get(simulator, "elapsedAccumulatedNanos"));
            assertEquals("00:00:00", BodySimulator.formatElapsedNanos(-1L));
            assertEquals("01:01:01", BodySimulator.formatElapsedNanos(3_661_999_999_999L));
            return null;
        });
    }

    private static BodySimulator simulatorWithCanvas() {
        BodySimulator simulator = new BodySimulator();
        simulator.configureCanvasInteractions(new Canvas(800.0, 600.0));
        try {
            Object[] trails = (Object[]) get(simulator, "trails");
            Object[] fullTracks = (Object[]) get(simulator, "fullTracks");
            for (int i = 0; i < trails.length; i++) {
                trails[i] = new ArrayDeque<>();
                fullTracks[i] = new ArrayList<>();
            }
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
        return simulator;
    }

    private static FloatArray array(BodySimulator simulator, String name) throws Exception {
        return (FloatArray) get(simulator, name);
    }

    private static Object get(BodySimulator simulator, String name) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(simulator);
    }

    private static void set(BodySimulator simulator, String name, Object value) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(simulator, value);
    }

    private static Object invoke(BodySimulator simulator, String name, Object... arguments) throws Exception {
        Class<?>[] types = new Class<?>[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            types[i] = arguments[i] instanceof Long ? long.class : arguments[i].getClass();
        }
        Method method = BodySimulator.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(simulator, arguments);
    }

    private static <T> T onFx(ThrowingSupplier<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.get();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { result.set(action.get()); } catch (Throwable t) { failure.set(t); } finally { done.countDown(); } });
        assertTrue(done.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    @FunctionalInterface private interface ThrowingSupplier<T> { T get() throws Exception; }
}
