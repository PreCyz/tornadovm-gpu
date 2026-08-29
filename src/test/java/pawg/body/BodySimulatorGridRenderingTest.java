package pawg.body;

import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BodySimulatorGridRenderingTest {

    private static final double LATERAL_BEND_LIMIT = 1.0;

    @BeforeAll
    static void startJavaFx() throws Exception {
        if (!Platform.isFxApplicationThread()) {
            CountDownLatch started = new CountDownLatch(1);
            try {
                Platform.startup(started::countDown);
            } catch (IllegalStateException alreadyStarted) {
                started.countDown();
            }
            assertTrue(started.await(10, TimeUnit.SECONDS), "JavaFX toolkit did not start");
        }
    }

    @Test
    void strongGravityKeepsLateralGridDeformationSmoothlyBounded() throws Exception {
        BodySimulator simulator = newSimulatorWithBody(0.0f, 0.0f, 0.0f, 1_000_000.0f);

        double[] samples = {0.001, 0.01, 0.1, 0.5, 1.0, 2.0};
        for (double x : samples) {
            double[] bent = bentGridPoint(simulator, x, 0.0);
            double displacement = Math.hypot(bent[0] - x, bent[1]);

            assertTrue(Double.isFinite(displacement), "deformation must remain finite at x=" + x);
            assertTrue(displacement <= LATERAL_BEND_LIMIT + 1.0e-6,
                    "lateral deformation exceeded one grid-unit limit at x=" + x);
        }

        double[] justBefore = bentGridPoint(simulator, 0.5000, 0.0);
        double[] justAfter = bentGridPoint(simulator, 0.5001, 0.0);
        assertTrue(Math.hypot(justAfter[0] - justBefore[0], justAfter[1] - justBefore[1]) < 0.001,
                "nearby grid samples should not jump discontinuously");
    }

    @Test
    void weakGravityRemainsApproximatelyLinearInMass() throws Exception {
        BodySimulator simulator = newSimulatorWithBody(0.0f, 0.0f, 0.0f, 1.0f);
        double x = 10.0;
        double shiftForOneMassUnit = Math.abs(bentGridPoint(simulator, x, 0.0)[0] - x);

        mass(simulator).set(0, 2.0f);
        double shiftForTwoMassUnits = Math.abs(bentGridPoint(simulator, x, 0.0)[0] - x);

        assertTrue(shiftForOneMassUnit > 0.0, "weak gravity should still deform the grid");
        assertEquals(2.0, shiftForTwoMassUnits / shiftForOneMassUnit, 0.01,
                "the bounded response should preserve the weak-field linear regime");
    }

    @Test
    void projectedGridGuardDrawsNormalSegmentsAndRejectsPathologicalOnes() throws Exception {
        WritableImage normal = renderGridSegment(-0.0875f, 0.0875f);
        WritableImage pathological = renderGridSegment(-5.0f, 5.0f);

        assertTrue(countVisiblePixels(normal) > 0, "an adjacent grid sample must be drawn");
        assertEquals(0, countVisiblePixels(pathological),
                "a projected chord much longer than the grid sampling distance must be rejected");
    }

    @Test
    void unchangedPausedGridReusesCachedGeometry() throws Exception {
        BodySimulator simulator = newSimulatorWithBody(0.0f, 0.0f, 0.0f, 100.0f);
        invokeGridRebuild(simulator, 800.0, 600.0);

        float[] firstX = (float[]) field(simulator, "gridPointX");
        int firstPointCount = (int) field(simulator, "gridPointCount");
        int firstLineCount = (int) field(simulator, "gridLineCount");
        assertFalse((boolean) field(simulator, "gridGeometryDirty"));

        invokeGridRebuild(simulator, 800.0, 600.0);

        assertSame(firstX, field(simulator, "gridPointX"), "cache hit must reuse the backing geometry");
        assertEquals(firstPointCount, field(simulator, "gridPointCount"));
        assertEquals(firstLineCount, field(simulator, "gridLineCount"));
    }

    @Test
    void adaptiveSamplingMaintainsPixelDensityAtMinimumZoom() throws Exception {
        BodySimulator simulator = newSimulatorWithBody(0.0f, 0.0f, 0.0f, 100.0f);
        setField(simulator, "viewScale", 16.0);

        invokeGridRebuild(simulator, 800.0, 600.0);

        double sampleStep = (double) field(simulator, "cachedGridSampleStep");
        assertTrue(sampleStep * 16.0 >= 4.0 - 1.0e-9,
                "adaptive samples must stay at least four pixels apart");
        assertTrue((int) field(simulator, "gridPointCount") > 0);
        assertTrue((int) field(simulator, "gridLineCount") > 0);
        assertFiniteGridPoints(simulator);
    }

    @Test
    void scaleChangeInvalidatesTheGridCacheKey() throws Exception {
        BodySimulator simulator = newSimulatorWithBody(0.0f, 0.0f, 0.0f, 100.0f);
        invokeGridRebuild(simulator, 800.0, 600.0);
        int initialPointCount = (int) field(simulator, "gridPointCount");

        setField(simulator, "viewScale", 16.0);
        invokeGridRebuild(simulator, 800.0, 600.0);

        assertEquals(16.0, (double) field(simulator, "cachedGridViewScale"));
        assertTrue(initialPointCount != (int) field(simulator, "gridPointCount"),
                "a zoom change must rebuild geometry with adaptive density");
        assertFiniteGridPoints(simulator);
    }

    @Test
    void physicalGridBendDoesNotChangeWhenOnlyTheViewZoomChanges() throws Exception {
        BodySimulator simulator = newSimulatorWithBody(0.0f, 0.0f, 0.0f, 500.0f);
        double[] before = bentGridPoint(simulator, 3.0, 1.0);
        setField(simulator, "viewScale", 16.0);
        double[] zoomedOut = bentGridPoint(simulator, 3.0, 1.0);
        setField(simulator, "viewScale", 180.0);
        double[] zoomedIn = bentGridPoint(simulator, 3.0, 1.0);

        assertArrayEquals(before, zoomedOut, 1.0e-6,
                "grid deformation is a physical world-space value, not a zoom-dependent value");
        assertArrayEquals(before, zoomedIn, 1.0e-6);
    }

    private static WritableImage renderGridSegment(float fromX, float toX) throws Exception {
        return onFxThread(() -> {
            BodySimulator simulator = new BodySimulator();
            Canvas canvas = new Canvas(240.0, 120.0);
            setField(simulator, "canvas", canvas);
            GraphicsContext graphics = canvas.getGraphicsContext2D();
            graphics.setStroke(Color.WHITE);
            graphics.setLineWidth(1.0);

            simulator.strokeProjectedGridLine(graphics,
                    (double) fromX, 0.0, 0.0,
                    (double) toX, 0.0, 0.0, 0.35);

            SnapshotParameters snapshotParameters = new SnapshotParameters();
            snapshotParameters.setFill(Color.TRANSPARENT);
            return canvas.snapshot(snapshotParameters, null);
        });
    }

    private static int countVisiblePixels(WritableImage image) {
        int visible = 0;
        for (int y = 0; y < (int) image.getHeight(); y++) {
            for (int x = 0; x < (int) image.getWidth(); x++) {
                if (image.getPixelReader().getColor(x, y).getOpacity() > 0.0) {
                    visible++;
                }
            }
        }
        return visible;
    }

    private static BodySimulator newSimulatorWithBody(float x, float y, float z, float bodyMass) throws Exception {
        BodySimulator simulator = onFxThread(BodySimulator::new);
        setField(simulator, "bodyCount", 1);
        position(simulator, "posX").set(0, x);
        position(simulator, "posY").set(0, y);
        position(simulator, "posZ").set(0, z);
        mass(simulator).set(0, bodyMass);
        return simulator;
    }

    private static double[] bentGridPoint(BodySimulator simulator, double x, double y) throws Exception {
        Method method = BodySimulator.class.getDeclaredMethod("bentGridPoint", double.class, double.class);
        method.setAccessible(true);
        Object point = method.invoke(simulator, x, y);
        Method pointX = point.getClass().getDeclaredMethod("x");
        Method pointY = point.getClass().getDeclaredMethod("y");
        pointX.setAccessible(true);
        pointY.setAccessible(true);
        return new double[]{((Number) pointX.invoke(point)).doubleValue(), ((Number) pointY.invoke(point)).doubleValue()};
    }

    private static FloatArray mass(BodySimulator simulator) throws Exception {
        return position(simulator, "mass");
    }

    private static FloatArray position(BodySimulator simulator, String name) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return (FloatArray) field.get(simulator);
    }

    private static void invokeGridRebuild(BodySimulator simulator, double width, double height) throws Exception {
        Method rebuild = BodySimulator.class.getDeclaredMethod(
                "rebuildGridGeometryIfNeeded", double.class, double.class);
        rebuild.setAccessible(true);
        rebuild.invoke(simulator, width, height);
    }

    private static void assertFiniteGridPoints(BodySimulator simulator) throws Exception {
        int count = (int) field(simulator, "gridPointCount");
        float[] x = (float[]) field(simulator, "gridPointX");
        float[] y = (float[]) field(simulator, "gridPointY");
        float[] z = (float[]) field(simulator, "gridPointZ");
        for (int i = 0; i < count; i++) {
            assertTrue(Float.isFinite(x[i]) && Float.isFinite(y[i]) && Float.isFinite(z[i]),
                    "grid point " + i + " must be finite");
        }
    }

    private static Object field(BodySimulator simulator, String name) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(simulator);
    }

    private static void setField(BodySimulator simulator, String name, Object value) throws Exception {
        Field field = BodySimulator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(simulator, value);
    }

    private static <T> T onFxThread(ThrowingSupplier<T> supplier) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }

        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(supplier.get());
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS), "JavaFX operation timed out");
        if (failure.get() != null) {
            throw new AssertionError("JavaFX operation failed", failure.get());
        }
        return result.get();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
